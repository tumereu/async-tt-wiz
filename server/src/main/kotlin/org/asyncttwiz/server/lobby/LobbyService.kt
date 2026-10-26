package org.asyncttwiz.server.lobby

import java.util.UUID
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountFields
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.accounts.InvalidAccountField
import org.asyncttwiz.server.generated.tables.Account.ACCOUNT
import org.asyncttwiz.server.generated.tables.AccountRequest.ACCOUNT_REQUEST
import org.asyncttwiz.server.generated.tables.Contact.CONTACT
import org.asyncttwiz.server.generated.tables.Game.GAME
import org.asyncttwiz.server.generated.tables.Invitation.INVITATION
import org.asyncttwiz.server.generated.tables.RequestIdentity.REQUEST_IDENTITY
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

/** Account HTTP exception creates the first game+invite atomically. No existing-game writes here. */
class LobbyService(private val runtime: ServiceRuntime) {
    data class Result(val status: Int, val body: String)
    private val json = DeckJson.format
    private fun error(status: Int, code: String) = Result(status, json.encodeToString(LobbyError(code)))
    private fun owner(principal: AccountService.Principal) = UUID.fromString(principal.account.accountId)

    fun search(principal: AccountService.Principal, query: String): Result {
        val key = try { AccountFields.username(query).key } catch (_: InvalidAccountField) {
            return error(400, "invalid_search")
        }
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            // Literal prefix, not SQL wildcards; same pinned Unicode key as registration.
            val users = sql.selectFrom(ACCOUNT)
                .where(ACCOUNT.USERNAME_KEY.startsWith(key)).and(ACCOUNT.ID.ne(owner(principal)))
                .orderBy(ACCOUNT.USERNAME_KEY, ACCOUNT.ID).limit(50).fetch().map {
                    AccountService.identity(it)
                }
            Result(200, json.encodeToString(UserSearch(users)))
        }
    }

    fun contacts(principal: AccountService.Principal, after: String?): Result {
        if (after != null && !DeckValidation.uuid(after)) return error(400, "invalid_request")
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val rows = sql.select(*ACCOUNT.fields()).from(CONTACT).join(ACCOUNT)
                .on(ACCOUNT.ID.eq(CONTACT.CONTACT_ID)).where(CONTACT.ACCOUNT_ID.eq(owner(principal)))
                .and(if (after == null) DSL.trueCondition() else ACCOUNT.ID.gt(UUID.fromString(after)))
                .orderBy(ACCOUNT.ID).limit(101).fetch()
            val contacts = rows.take(100).map { AccountService.identity(it) }
            Result(200, json.encodeToString(ContactList(contacts, if (rows.size > 100) contacts.last().accountId else null)))
        }
    }

    /** Unilateral and idempotent; games and invitations are independent of saved contacts. */
    fun remove(principal: AccountService.Principal, id: String): Result {
        if (!DeckValidation.uuid(id)) return error(400, "invalid_contact")
        return runtime.transaction { connection ->
            DSL.using(connection, SQLDialect.POSTGRES).deleteFrom(CONTACT)
                .where(CONTACT.ACCOUNT_ID.eq(owner(principal)))
                .and(CONTACT.CONTACT_ID.eq(UUID.fromString(id))).execute()
            Result(200, "{}")
        }
    }

    fun add(principal: AccountService.Principal, input: AddContact): Result =
        business(principal, input.requestId, "addContact", DeckJson.canonical(input)) { sql, actor ->
            if (!DeckValidation.uuid(input.accountId) || input.accountId == actor.toString())
                return@business error(400, "invalid_contact")
            val id = UUID.fromString(input.accountId)
            val identity = sql.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(id)).fetchOne()?.let(AccountService::identity)
                ?: return@business error(404, "not_found")
            sql.insertInto(CONTACT).set(CONTACT.ACCOUNT_ID, actor).set(CONTACT.CONTACT_ID, id).onConflictDoNothing().execute()
            Result(200, json.encodeToString(identity))
        }

    fun create(principal: AccountService.Principal, input: CreateInvitation): Result =
        business(principal, input.requestId, "createInvitation", DeckJson.canonical(input)) { sql, actor ->
            if (!LobbyValidation.name(input.gameName)) return@business error(400, "invalid_name")
            if (!DeckValidation.uuid(input.recipient) || !sql.fetchExists(sql.selectOne().from(CONTACT)
                    .where(CONTACT.ACCOUNT_ID.eq(actor)).and(CONTACT.CONTACT_ID.eq(UUID.fromString(input.recipient)))))
                return@business error(400, "contact_required")
            val game = UUID.randomUUID()
            val invitation = UUID.randomUUID()
            sql.insertInto(GAME).set(GAME.ID, game).set(GAME.GAME_NAME, input.gameName)
                .set(GAME.INVITER_ID, actor).set(GAME.RECIPIENT_ID, UUID.fromString(input.recipient))
                .set(GAME.PREPARATION_ROUND, UUID.randomUUID()).set(GAME.CURRENT_INVITATION, invitation).execute()
            sql.insertInto(INVITATION).set(INVITATION.ID, invitation).set(INVITATION.GAME_ID, game).execute()
            org.asyncttwiz.server.activity.ActivityService.record(sql, UUID.fromString(input.recipient),
                ActivityKind.INVITATION, game, invitation, actor)
            Result(201, json.encodeToString(summary(sql, game)))
        }

    fun summary(sql: DSLContext, id: UUID): GameSummary {
        val game = sql.selectFrom(GAME).where(GAME.ID.eq(id)).fetchSingle()
        return summary(sql, game, if (game.lifecycle == "ACTIVE")
            org.asyncttwiz.server.game.PlayStore.read(sql, id)?.board?.effectiveAttentionHolder else null)
    }

    /** Execution already owns the locked game row and the reduced board. Do not reload either. */
    internal fun summary(sql: DSLContext, game: org.asyncttwiz.server.generated.tables.records.GameRecord,
        attentionHolder: String?): GameSummary {
        val inviter = ACCOUNT.`as`("inviter")
        val recipient = ACCOUNT.`as`("recipient")
        val identities = sql.select(INVITATION.STATE, inviter.USERNAME, recipient.USERNAME,
            inviter.AVATAR_SCRYFALL_ID, inviter.AVATAR_FACE, recipient.AVATAR_SCRYFALL_ID, recipient.AVATAR_FACE).from(INVITATION)
            .join(inviter).on(inviter.ID.eq(game.inviterId)).join(recipient).on(recipient.ID.eq(game.recipientId))
            .where(INVITATION.ID.eq(game.currentInvitation)).fetchSingle()
        return GameSummary(game.id.toString(), game.gameName!!, game.createdAt!!.toInstant().toString(),
            AccountIdentity(game.inviterId.toString(), identities.value2()!!,
                identities[inviter.AVATAR_SCRYFALL_ID]?.let { Avatar(it.toString(), identities[inviter.AVATAR_FACE] ?: 0) }),
            AccountIdentity(game.recipientId.toString(), identities.value3()!!,
                identities[recipient.AVATAR_SCRYFALL_ID]?.let { Avatar(it.toString(), identities[recipient.AVATAR_FACE] ?: 0) }), game.preparationRound.toString(),
            game.currentInvitation.toString(), InvitationState.valueOf(identities.value1()!!), GameLifecycle.valueOf(game.lifecycle!!),
            game.revision.toString(), game.currentPlay?.toString(),
            attentionHolder,
            game.updatedAt!!.toInstant().toString(), game.podId?.toString(), game.podName)
    }

    fun list(principal: AccountService.Principal, after: String?, invitations: Boolean): Result {
        if (after != null && !DeckValidation.uuid(after)) return error(400, "invalid_request")
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val actor = owner(principal)
            val access = if (invitations) GAME.RECIPIENT_ID.eq(actor).and(INVITATION.STATE.eq("PENDING"))
                else GAME.INVITER_ID.eq(actor).or(GAME.RECIPIENT_ID.eq(actor).and(INVITATION.STATE.eq("ACCEPTED")))
            val ids = sql.select(GAME.ID).from(GAME).join(INVITATION).on(INVITATION.ID.eq(GAME.CURRENT_INVITATION))
                .where(access).and(GAME.LIFECYCLE.`in`("LOBBY", "ACTIVE"))
                .and(if (after == null) DSL.trueCondition() else GAME.ID.gt(UUID.fromString(after)))
                .orderBy(GAME.ID).limit(101).forShare().fetch(GAME.ID)
            val games = ids.take(100).map { summary(sql, it!!) }
            Result(200, json.encodeToString(GameList(games, if (ids.size > 100) games.last().id else null)))
        }
    }

    fun head(principal: AccountService.Principal, id: String): Result {
        if (!DeckValidation.uuid(id)) return error(404, "not_found")
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val actor = owner(principal)
            val visible = sql.select(GAME.ID).from(GAME).join(INVITATION).on(INVITATION.ID.eq(GAME.CURRENT_INVITATION))
                .where(GAME.ID.eq(UUID.fromString(id))).and(GAME.LIFECYCLE.`in`("LOBBY", "ACTIVE"))
                .and(GAME.INVITER_ID.eq(actor).or(GAME.RECIPIENT_ID.eq(actor).and(INVITATION.STATE.eq("ACCEPTED"))))
                .forShare().fetchOne() != null
            if (visible) Result(200, json.encodeToString(summary(sql, UUID.fromString(id)))) else error(404, "not_found")
        }
    }

    /** Same global identity/atomic account outcome as decks, within the current writer epoch. */
    private fun business(principal: AccountService.Principal, requestId: String, kind: String, canonical: String,
        action: (DSLContext, UUID) -> Result): Result {
        if (!DeckValidation.uuid(requestId)) return error(400, "invalid_request")
        val actor = owner(principal); val id = UUID.fromString(requestId); val digest = DeckJson.intent(kind, canonical)
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val inserted = sql.insertInto(REQUEST_IDENTITY).set(REQUEST_IDENTITY.ID, id).set(REQUEST_IDENTITY.ACCOUNT_ID, actor)
                .set(REQUEST_IDENTITY.KIND, kind).set(REQUEST_IDENTITY.SCOPE, "account").set(REQUEST_IDENTITY.INTENT_DIGEST, digest)
                .onConflictDoNothing().execute() == 1
            if (!inserted) {
                val original = sql.selectFrom(REQUEST_IDENTITY).where(REQUEST_IDENTITY.ID.eq(id)).fetchSingle()
                if (original.accountId != actor || original.kind != kind || original.scope != "account" || original.intentDigest != digest)
                    return@transaction error(409, "request_conflict")
                val outcome = sql.selectFrom(ACCOUNT_REQUEST).where(ACCOUNT_REQUEST.REQUEST_ID.eq(id)).fetchSingle()
                return@transaction Result(outcome.status!!, outcome.response!!)
            }
            val result = action(sql, actor)
            sql.insertInto(ACCOUNT_REQUEST).set(ACCOUNT_REQUEST.REQUEST_ID, id).set(ACCOUNT_REQUEST.STATUS, result.status)
                .set(ACCOUNT_REQUEST.RESPONSE, result.body).execute()
            result
        }
    }
}
