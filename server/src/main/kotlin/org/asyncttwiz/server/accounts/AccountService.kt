package org.asyncttwiz.server.accounts

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import org.asyncttwiz.protocol.AccountIdentity
import org.asyncttwiz.protocol.SessionCreated
import org.asyncttwiz.protocol.Avatar
import org.asyncttwiz.protocol.DeckValidation
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.generated.tables.Account.ACCOUNT
import org.asyncttwiz.server.generated.tables.AccountSession.ACCOUNT_SESSION
import org.jooq.SQLDialect
import org.jooq.impl.DSL

/** No raw connection/SQL objects escape the server adapter; every write uses the writer fence. */
class AccountService(private val runtime: ServiceRuntime, private val passwords: PasswordHashing = PasswordHashing()) {
    class Principal(val sessionId: UUID, val account: AccountIdentity) {
        override fun toString() = "AccountPrincipal[redacted]"
    }
    private val random = SecureRandom()

    fun register(username: String, email: String, password: String): AccountIdentity? {
        val name = AccountFields.username(username)
        val address = AccountFields.email(email)
        val phc = passwords.hash(AccountFields.password(password))
        val id = UUID.randomUUID()
        val inserted = runtime.transaction { connection ->
            DSL.using(connection, SQLDialect.POSTGRES).insertInto(ACCOUNT)
                .set(ACCOUNT.ID, id).set(ACCOUNT.USERNAME, name.display).set(ACCOUNT.USERNAME_KEY, name.key)
                .set(ACCOUNT.EMAIL_DELIVERY, address.delivery).set(ACCOUNT.EMAIL_KEY, address.key)
                .set(ACCOUNT.PASSWORD_PHC, phc).onConflictDoNothing().execute() == 1
        }
        return if (inserted) AccountIdentity(id.toString(), name.display) else null
    }

    fun login(email: String, password: String): SessionCreated? {
        val address = AccountFields.email(email)
        AccountFields.password(password)
        val row = runtime.transaction { connection ->
            DSL.using(connection, SQLDialect.POSTGRES).selectFrom(ACCOUNT)
                .where(ACCOUNT.EMAIL_KEY.eq(address.key)).fetchOne()
        }
        if (!passwords.verify(password, row?.get(ACCOUNT.PASSWORD_PHC))) return null
        checkNotNull(row)
        val updatedPhc = if (passwords.needsRehash(row[ACCOUNT.PASSWORD_PHC]!!)) passwords.hash(password) else null
        val bytes = ByteArray(32).also(random::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val verifier = hash(token)
        runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            if (updatedPhc != null) sql.update(ACCOUNT).set(ACCOUNT.PASSWORD_PHC, updatedPhc)
                .where(ACCOUNT.ID.eq(row[ACCOUNT.ID])).and(ACCOUNT.PASSWORD_PHC.eq(row[ACCOUNT.PASSWORD_PHC])).execute()
            sql.insertInto(ACCOUNT_SESSION).set(ACCOUNT_SESSION.ID, UUID.randomUUID())
                .set(ACCOUNT_SESSION.ACCOUNT_ID, row[ACCOUNT.ID]).set(ACCOUNT_SESSION.AUTH_EPOCH, row[ACCOUNT.AUTH_EPOCH])
                .set(ACCOUNT_SESSION.TOKEN_HASH, verifier).execute()
        }
        return SessionCreated(token, identity(row))
    }

    fun authenticate(token: String): Principal? {
        if (!validTokenShape(token)) return null
        return runtime.transaction { connection ->
            DSL.using(connection, SQLDialect.POSTGRES).select(ACCOUNT_SESSION.ID, *ACCOUNT.fields())
                .from(ACCOUNT_SESSION).join(ACCOUNT).on(ACCOUNT.ID.eq(ACCOUNT_SESSION.ACCOUNT_ID))
                .where(ACCOUNT_SESSION.TOKEN_HASH.eq(hash(token))).and(ACCOUNT_SESSION.REVOKED_AT.isNull)
                .and(ACCOUNT_SESSION.AUTH_EPOCH.eq(ACCOUNT.AUTH_EPOCH)).fetchOne()?.let {
                    Principal(it[ACCOUNT_SESSION.ID]!!, identity(it))
                }
        }
    }

    fun changeUsername(principal: Principal, username: String): AccountIdentity? {
        val name = AccountFields.username(username)
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            // Keep expected uniqueness conflicts inside the transaction fence. A concurrent
            // registration/rename can still win after a preflight availability check.
            val savepoint = connection.setSavepoint()
            try {
                sql.update(ACCOUNT).set(ACCOUNT.USERNAME, name.display).set(ACCOUNT.USERNAME_KEY, name.key)
                    .where(ACCOUNT.ID.eq(UUID.fromString(principal.account.accountId))).execute()
                connection.releaseSavepoint(savepoint)
                identity(sql.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(UUID.fromString(principal.account.accountId))).fetchSingle())
            } catch (failure: org.jooq.exception.DataAccessException) {
                if (generateSequence<Throwable>(failure) { it.cause }.any { it is java.sql.SQLException && it.sqlState == "23505" }) {
                    connection.rollback(savepoint)
                    connection.releaseSavepoint(savepoint)
                    null
                } else throw failure
            }
        }
    }

    fun changeAvatar(principal: Principal, avatar: Avatar?): AccountIdentity {
        if (avatar != null && (!DeckValidation.uuid(avatar.scryfallId) || avatar.face !in 0..15))
            throw InvalidAccountField("avatar")
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            sql.update(ACCOUNT).set(ACCOUNT.AVATAR_SCRYFALL_ID, avatar?.let { UUID.fromString(it.scryfallId) })
                .set(ACCOUNT.AVATAR_FACE, avatar?.face ?: 0)
                .where(ACCOUNT.ID.eq(UUID.fromString(principal.account.accountId))).execute()
            identity(sql.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(UUID.fromString(principal.account.accountId))).fetchSingle())
        }
    }

    companion object {
        fun identity(row: org.jooq.Record): AccountIdentity = AccountIdentity(row[ACCOUNT.ID].toString(),
            row[ACCOUNT.USERNAME]!!, row[ACCOUNT.AVATAR_SCRYFALL_ID]?.let { Avatar(it.toString(), row[ACCOUNT.AVATAR_FACE] ?: 0) })
    }

    fun revoke(principal: Principal) {
        runtime.transaction { connection ->
            DSL.using(connection, SQLDialect.POSTGRES).update(ACCOUNT_SESSION)
                .set(ACCOUNT_SESSION.REVOKED_AT, java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC))
                .where(ACCOUNT_SESSION.ID.eq(principal.sessionId)).and(ACCOUNT_SESSION.REVOKED_AT.isNull).execute()
            val installs = org.asyncttwiz.server.generated.tables.ActivityInstallation.ACTIVITY_INSTALLATION
            DSL.using(connection, SQLDialect.POSTGRES).deleteFrom(installs).where(installs.SESSION_ID.eq(principal.sessionId)).execute()
        }
    }

    fun validTokenShape(token: String): Boolean = token.matches(Regex("[A-Za-z0-9_-]{43}")) &&
        Base64.getUrlEncoder().withoutPadding().encodeToString(Base64.getUrlDecoder().decode(token)) == token

    fun revokeToken(token: String) {
        runtime.transaction { connection ->
            DSL.using(connection, SQLDialect.POSTGRES).update(ACCOUNT_SESSION)
                .set(ACCOUNT_SESSION.REVOKED_AT, java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC))
                .where(ACCOUNT_SESSION.TOKEN_HASH.eq(hash(token))).and(ACCOUNT_SESSION.REVOKED_AT.isNull).execute()
            val installs = org.asyncttwiz.server.generated.tables.ActivityInstallation.ACTIVITY_INSTALLATION
            DSL.using(connection, SQLDialect.POSTGRES).deleteFrom(installs).where(installs.SESSION_ID.`in`(
                DSL.select(ACCOUNT_SESSION.ID).from(ACCOUNT_SESSION).where(ACCOUNT_SESSION.TOKEN_HASH.eq(hash(token))))).execute()
        }
    }

    private fun hash(token: String) = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.US_ASCII))
}
