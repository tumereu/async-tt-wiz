package org.asyncttwiz.server.accounts

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.generated.tables.Account.ACCOUNT
import org.asyncttwiz.server.generated.tables.AccountSession.ACCOUNT_SESSION
import org.asyncttwiz.server.generated.tables.PasswordResetToken.PASSWORD_RESET_TOKEN as TOKEN
import org.asyncttwiz.server.generated.tables.ResetMail.RESET_MAIL as MAIL
import org.jooq.SQLDialect
import org.jooq.impl.DSL

/** Durable token verification and encrypted outbox. Network I/O never holds a DB transaction. */
class PasswordResetService @JvmOverloads constructor(private val runtime: ServiceRuntime, private val settings: ResetMailSettings,
                           private val passwords: PasswordHashing = PasswordHashing(),
                           private val clock: Clock = Clock.systemUTC()) {
    private val random = SecureRandom()
    private fun now() = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.US_ASCII))
    private fun validShape(value: String) = value.matches(Regex("[A-Za-z0-9_-]{43}")) &&
        Base64.getUrlEncoder().withoutPadding().encodeToString(Base64.getUrlDecoder().decode(value)) == value
    private fun fresh() = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

    fun request(email: String) {
        val address = AccountFields.email(email)
        // Do the same random/encryption work for unknown addresses; the response is always generic.
        val token = fresh()
        val id = UUID.randomUUID()
        val time = now()
        runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val account = sql.select(ACCOUNT.ID, ACCOUNT.EMAIL_DELIVERY).from(ACCOUNT).where(ACCOUNT.EMAIL_KEY.eq(address.key))
                .forUpdate().fetchOne()
            val delivery = account?.get(ACCOUNT.EMAIL_DELIVERY)?.let(AccountFields::email) ?: address
            val transport = delivery.delivery.substringBefore('@') + "@" + delivery.key.substringAfter('@')
            val payload = encrypt(id, transport + "\n" + token)
            if (account != null) {
                sql.insertInto(TOKEN).set(TOKEN.ID, id).set(TOKEN.ACCOUNT_ID, account[ACCOUNT.ID])
                    .set(TOKEN.TOKEN_HASH, hash(token)).set(TOKEN.CREATED_AT, time).set(TOKEN.EXPIRES_AT, time.plusMinutes(30)).execute()
                sql.insertInto(MAIL).set(MAIL.TOKEN_ID, id).set(MAIL.ENCRYPTED_PAYLOAD, payload)
                    .set(MAIL.STATUS, "pending").set(MAIL.NEXT_ATTEMPT_AT, time).execute()
            }
        }
    }

    fun usable(token: String): Boolean {
        if (!validShape(token)) return false
        return runtime.transaction { connection ->
            DSL.using(connection, SQLDialect.POSTGRES).fetchExists(TOKEN,
                TOKEN.TOKEN_HASH.eq(hash(token)).and(TOKEN.CONSUMED_AT.isNull).and(TOKEN.EXPIRES_AT.gt(now())))
        }
    }

    fun confirm(token: String, password: String): Boolean {
        AccountFields.password(password)
        if (!usable(token)) return false
        val phc = passwords.hash(password)
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val accountId = sql.select(TOKEN.ACCOUNT_ID).from(TOKEN).where(TOKEN.TOKEN_HASH.eq(hash(token)))
                .fetchOne(TOKEN.ACCOUNT_ID) ?: return@transaction false
            // Serialize competing tokens for the same account, then recheck after acquiring the lock.
            sql.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.ID.eq(accountId)).forUpdate().fetchSingle()
            val time = now()
            if (!sql.fetchExists(TOKEN, TOKEN.TOKEN_HASH.eq(hash(token)).and(TOKEN.CONSUMED_AT.isNull)
                    .and(TOKEN.EXPIRES_AT.gt(time)))) return@transaction false
            sql.update(ACCOUNT).set(ACCOUNT.PASSWORD_PHC, phc).set(ACCOUNT.AUTH_EPOCH, ACCOUNT.AUTH_EPOCH.plus(1L))
                .where(ACCOUNT.ID.eq(accountId)).execute()
            sql.update(ACCOUNT_SESSION).set(ACCOUNT_SESSION.REVOKED_AT, time)
                .where(ACCOUNT_SESSION.ACCOUNT_ID.eq(accountId)).and(ACCOUNT_SESSION.REVOKED_AT.isNull).execute()
            sql.update(TOKEN).set(TOKEN.CONSUMED_AT, time).where(TOKEN.ACCOUNT_ID.eq(accountId))
                .and(TOKEN.CONSUMED_AT.isNull).execute()
            sql.update(MAIL).set(MAIL.ENCRYPTED_PAYLOAD, null as ByteArray?).set(MAIL.STATUS, "cancelled")
                .where(MAIL.STATUS.eq("pending")).and(MAIL.TOKEN_ID.`in`(sql.select(TOKEN.ID).from(TOKEN)
                    .where(TOKEN.ACCOUNT_ID.eq(accountId)))).execute()
            true
        }
    }

    private class Delivery(val id: UUID, val recipient: String, val token: String) {
        override fun toString() = "ResetDelivery[redacted]"
    }

    /** One bounded batch per poll. A pre-send persisted backoff also recovers an interrupted delivery. */
    fun deliverDue(mailer: ResetMailer) {
        repeat(8) {
            val delivery = runtime.transaction { connection ->
                val sql = DSL.using(connection, SQLDialect.POSTGRES)
                val time = now()
                sql.update(MAIL).set(MAIL.ENCRYPTED_PAYLOAD, null as ByteArray?).set(MAIL.STATUS, "expired")
                    .where(MAIL.STATUS.eq("pending")).and(MAIL.TOKEN_ID.`in`(sql.select(TOKEN.ID).from(TOKEN)
                        .where(TOKEN.EXPIRES_AT.le(time)))).execute()
                val row = sql.select(MAIL.TOKEN_ID, MAIL.ENCRYPTED_PAYLOAD, MAIL.ATTEMPTS)
                    .from(MAIL).where(MAIL.STATUS.eq("pending")).and(MAIL.NEXT_ATTEMPT_AT.le(time))
                    .orderBy(MAIL.NEXT_ATTEMPT_AT, MAIL.TOKEN_ID).limit(1).forUpdate().fetchOne()
                    ?: return@transaction null
                val id = row[MAIL.TOKEN_ID]!!
                val attempt = row[MAIL.ATTEMPTS]!! + 1
                val minutes = when (attempt) { 1 -> 1L; 2 -> 5L; else -> 15L }
                sql.update(MAIL).set(MAIL.ATTEMPTS, attempt).set(MAIL.NEXT_ATTEMPT_AT, time.plusMinutes(minutes))
                    .where(MAIL.TOKEN_ID.eq(id)).execute()
                val payload = decrypt(id, row[MAIL.ENCRYPTED_PAYLOAD]!!).split('\n')
                check(payload.size == 2)
                Delivery(id, payload[0], payload[1])
            } ?: return
            val result = mailer.send(delivery.recipient, settings.link(delivery.token))
            if (result != MailResult.TEMPORARY) runtime.transaction { connection ->
                DSL.using(connection, SQLDialect.POSTGRES).update(MAIL)
                    .set(MAIL.STATUS, if (result == MailResult.DELIVERED) "delivered" else "failed")
                    .set(MAIL.ENCRYPTED_PAYLOAD, null as ByteArray?).where(MAIL.TOKEN_ID.eq(delivery.id))
                    .and(MAIL.STATUS.eq("pending")).execute()
            }
        }
    }

    /** Recovery validates decryptability before readiness; a wrong/missing key must not silently lose mail. */
    fun validatePending() = runtime.transaction { connection ->
        val sql = DSL.using(connection, SQLDialect.POSTGRES)
        sql.select(MAIL.TOKEN_ID, MAIL.ENCRYPTED_PAYLOAD).from(MAIL).where(MAIL.STATUS.eq("pending"))
            .fetchLazy().use { rows -> rows.forEach { decrypt(it[MAIL.TOKEN_ID]!!, it[MAIL.ENCRYPTED_PAYLOAD]!!) } }
    }

    private fun encrypt(id: UUID, value: String): ByteArray {
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(settings.key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(id.toString().toByteArray(Charsets.US_ASCII))
        return nonce + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    }
    private fun decrypt(id: UUID, value: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(settings.key, "AES"), GCMParameterSpec(128, value.copyOfRange(0, 12)))
        cipher.updateAAD(id.toString().toByteArray(Charsets.US_ASCII))
        return cipher.doFinal(value.copyOfRange(12, value.size)).toString(Charsets.UTF_8)
    }
}
