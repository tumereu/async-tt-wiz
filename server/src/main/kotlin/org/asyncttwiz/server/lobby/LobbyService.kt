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
            val users = sql.select(ACCOUNT.ID, ACCOUNT.USERNAME).from(ACCOUNT)
                .where(ACCOUNT.USERNAME_KEY.startsWith(key)).and(ACCOUNT.ID.ne(owner(principal)))
                .orderBy(ACCOUNT.USERNAME_KEY, ACCOUNT.ID).limit(50).fetch().map {
                    AccountIdentity(it[ACCOUNT.ID].toString(), it[ACCOUNT.USERNAME]!!)
                }
            Result(200, json.encodeToString(UserSearch(users)))
        }
    }

    fun contacts(principal: AccountService.Principal, after: String?): Result {
        if (after != null && !DeckValidation.uuid(after)) return error(400, "invalid_request")
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val rows = sql.select(ACCOUNT.ID, ACCOUNT.USERNAME).from(CONTACT).join(ACCOUNT)
                .on(ACCOUNT.ID.eq(CONTACT.CONTACT_ID)).where(CONTACT.ACCOUNT_ID.eq(owner(principal)))
                .and(if (after == null) DSL.trueCondition() else ACCOUNT.ID.gt(UUID.fromString(after)))
                .orderBy(ACCOUNT.ID).limit(101).fetch()
            val contacts = rows.take(100).map { AccountIdentity(it[ACCOUNT.ID].toString(), it[ACCOUNT.USERNAME]!!) }
            Result(200, json.encodeToString(ContactList(contacts, if (rows.size > 100) contacts.last().accountId else null)))
        }
    }

    fun add(principal: AccountService.Principal, input: AddContact): Result =
        business(principal, input.requestId, "addContact", DeckJson.canonical(input)) { sql, actor ->
            if (!DeckValidation.uuid(input.accountId) || input.accountId == actor.toString())
                return@business error(400, "invalid_contact")
            val id = UUID.fromString(input.accountId)
            val name = sql.select(ACCOUNT.USERNAME).from(ACCOUNT).where(ACCOUNT.ID.eq(id)).fetchOne(ACCOUNT.USERNAME)
                ?: return@business error(404, "not_found")
            sql.insertInto(CONTACT).set(CONTACT.ACCOUNT_ID, actor).set(CONTACT.CONTACT_ID, id).onConflictDoNothing().execute()
            Result(200, json.encodeToString(AccountIdentity(input.accountId, name)))
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
            Result(201, json.encodeToString(summary(sql, game)))
        }

    fun summary(sql: DSLContext, id: UUID): GameSummary {
        val game = sql.selectFrom(GAME).where(GAME.ID.eq(id)).fetchSingle()
        val invitation = sql.selectFrom(INVITATION).where(INVITATION.ID.eq(game.currentInvitation)).fetchSingle()
        fun identity(id: UUID) = AccountIdentity(id.toString(), sql.select(ACCOUNT.USERNAME).from(ACCOUNT)
            .where(ACCOUNT.ID.eq(id)).fetchSingle(ACCOUNT.USERNAME)!!)
        return GameSummary(id.toString(), game.gameName!!, game.createdAt!!.toInstant().toString(),
            identity(game.inviterId!!), identity(game.recipientId!!), game.preparationRound.toString(),
            invitation.id.toString(), InvitationState.valueOf(invitation.state!!), GameLifecycle.valueOf(game.lifecycle!!),
            game.revision.toString(), game.currentPlay?.toString())
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
