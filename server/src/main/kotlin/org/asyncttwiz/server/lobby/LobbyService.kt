package org.asyncttwiz.server.lobby

import java.util.UUID
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.StoredData
import org.asyncttwiz.server.accounts.AccountFields
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.accounts.InvalidAccountField
import org.asyncttwiz.server.generated.tables.Account.ACCOUNT
import org.asyncttwiz.server.generated.tables.AccountRequest.ACCOUNT_REQUEST
import org.asyncttwiz.server.generated.tables.Contact.CONTACT
import org.asyncttwiz.server.generated.tables.Game.GAME
import org.asyncttwiz.server.generated.tables.GameChange.GAME_CHANGE
import org.asyncttwiz.server.generated.tables.GameRemoved.GAME_REMOVED
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

    fun summary(sql: DSLContext, id: UUID): GameSummary = summary(sql, sql.selectFrom(GAME).where(GAME.ID.eq(id)).fetchSingle())

    /** [viewer]: whose list it is; a play whose state is the engine's says whose move it is per viewer (I-D56 §7). */
    private fun summary(sql: DSLContext, game: org.asyncttwiz.server.generated.tables.records.GameRecord, viewer: UUID? = null): GameSummary {
        if (game.lifecycle != "ACTIVE") return summary(sql, game, null as String?)
        return summary(sql, game, org.asyncttwiz.server.game.PlayStore.read(sql, game.id!!)?.board?.effectiveAttentionHolder, viewer)
    }

    /** Execution already owns the locked game row and the reduced board. Do not reload either. */
    internal fun summary(sql: DSLContext, game: org.asyncttwiz.server.generated.tables.records.GameRecord,
        attentionHolder: String?, viewer: UUID? = null): GameSummary {
        val inviter = ACCOUNT.`as`("inviter")
        val recipient = ACCOUNT.`as`("recipient")
        // I-D56 §7, TRN-058 (I-D57): for a viewer of a play whose state is the engine's, whose move it is comes from the
        // engine seats (read in the same round trip): the viewer when the game waits for them, else the other player when
        // it waits for them (the record notifications use); the last-activity
        // time is the viewer's own (their next stop's, else their local head's event's: nothing past it)
        val play = game.currentPlay?.takeIf { viewer != null && game.lifecycle == "ACTIVE" }
        // Only for an active play: other summaries don't touch the engine tables (a database at an older schema has none)
        val seats = if (play == null) DSL.inline(null, String::class.java)
            else DSL.field("(SELECT string_agg(s.account_id::text || '|' || s.waited::text || '|' || " +
                "coalesce(to_char(coalesce(s.next_stop_at, (SELECT e.created_at FROM engine_event e WHERE e.play_id = s.play_id " +
                "AND e.id = s.local_head)) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"'), ''), ',' ORDER BY s.account_id) " +
                "FROM engine_seat s WHERE s.play_id = {0})", String::class.java, DSL.`val`(play, UUID::class.java))
        val identities = sql.select(INVITATION.STATE, inviter.USERNAME, recipient.USERNAME,
            inviter.AVATAR_SCRYFALL_ID, inviter.AVATAR_FACE, recipient.AVATAR_SCRYFALL_ID, recipient.AVATAR_FACE, seats).from(INVITATION)
            .join(inviter).on(inviter.ID.eq(game.inviterId)).join(recipient).on(recipient.ID.eq(game.recipientId))
            .where(INVITATION.ID.eq(game.currentInvitation)).fetchSingle()
        val engine = identities.value8()?.split(",")?.map { it.split("|") }
        val holder = if (engine == null) attentionHolder
            else engine.firstOrNull { it[0] == viewer.toString() && it[1] == "true" }?.get(0) ?: engine.firstOrNull { it[1] == "true" }?.get(0)
        val activity = engine?.firstOrNull { it[0] == viewer.toString() }?.getOrNull(2)?.takeIf { it.isNotEmpty() }
            ?.let { java.time.Instant.parse(it).toString() }
        return GameSummary(game.id.toString(), game.gameName!!, game.createdAt!!.toInstant().toString(),
            AccountIdentity(game.inviterId.toString(), identities.value2()!!,
                identities[inviter.AVATAR_SCRYFALL_ID]?.let { Avatar(it.toString(), identities[inviter.AVATAR_FACE] ?: 0) }),
            AccountIdentity(game.recipientId.toString(), identities.value3()!!,
                identities[recipient.AVATAR_SCRYFALL_ID]?.let { Avatar(it.toString(), identities[recipient.AVATAR_FACE] ?: 0) }), game.preparationRound.toString(),
            game.currentInvitation.toString(), InvitationState.valueOf(identities.value1()!!), GameLifecycle.valueOf(game.lifecycle!!),
            game.revision.toString(), game.currentPlay?.toString(),
            holder,
            activity ?: game.updatedAt!!.toInstant().toString(), game.podId?.toString(), game.podName, game.podInvitationHeld == true)
    }

    /**
     * The games (or [invitations]) list, a page of 100 after [after]. With [since] (a token from an earlier read,
     * switch-review #11): only the games changed since then, and the ids of changed games no longer on this list. Every
     * read answers a token to read changes since next time (its first page's). The summaries are read in a constant
     * number of queries per page ([listener]: tests count them).
     */
    fun list(principal: AccountService.Principal, after: String?, invitations: Boolean, since: String? = null,
        listener: org.jooq.ExecuteListener? = null): Result {
        if (after != null && !DeckValidation.uuid(after)) return error(400, "invalid_request")
        val changedSince = since?.let { it.toLongOrNull()?.takeIf { value -> value >= 0 } ?: return error(400, "invalid_request") }
        return runtime.transaction { connection ->
            val base = DSL.using(connection, SQLDialect.POSTGRES)
            val sql = if (listener == null) base else DSL.using(base.configuration().derive(listener))
            val actor = owner(principal)
            // The token first: its snapshot is older than the rows' read, so a change committed after it has a marker at
            // or after it (V61) and the next read since it can't miss one
            val token = if (after == null) sql.fetchValue(DSL.field("pg_snapshot_xmin(pg_current_snapshot())::text", String::class.java)) else null
            // Held pod challenges are listed too; the recipient sees them before their deck is in.
            val listed = (if (invitations) GAME.RECIPIENT_ID.eq(actor).and(INVITATION.STATE.eq("PENDING"))
                else GAME.INVITER_ID.eq(actor).or(GAME.RECIPIENT_ID.eq(actor).and(INVITATION.STATE.eq("ACCEPTED"))))
                .and(GAME.LIFECYCLE.`in`("LOBBY", "ACTIVE"))
            val access = if (changedSince == null) listed
                else GAME.INVITER_ID.eq(actor).or(GAME.RECIPIENT_ID.eq(actor)).and(DSL.exists(DSL.selectOne().from(GAME_CHANGE)
                    .where(GAME_CHANGE.GAME_ID.eq(GAME.ID)).and(GAME_CHANGE.CHANGE_XID.ge(changedSince))))
            val onList = DSL.field(listed).`as`("on_list")
            // The locked rows themselves, not just their ids: each summary then needs no second read of its game.
            val rows = sql.select(*GAME.fields(), onList).from(GAME).join(INVITATION).on(INVITATION.ID.eq(GAME.CURRENT_INVITATION))
                .where(access)
                .and(if (after == null) DSL.trueCondition() else GAME.ID.gt(UUID.fromString(after)))
                .orderBy(GAME.ID).limit(101).forShare().fetch()
            val page = rows.take(100)
            val shown = page.filter { it.get(onList) == true }.map { it.into(GAME) }
            // Games the cleanup deleted since then (game history §4) are gone with their markers: their tombstones say so
            val deleted = if (changedSince == null || after != null) emptyList() else sql.select(GAME_REMOVED.GAME_ID).from(GAME_REMOVED)
                .where(GAME_REMOVED.INVITER_ID.eq(actor).or(GAME_REMOVED.RECIPIENT_ID.eq(actor)))
                .and(GAME_REMOVED.CHANGE_XID.ge(changedSince)).fetch(GAME_REMOVED.GAME_ID).map { it.toString() }
            val removed = page.filter { it.get(onList) != true }.map { it.get(GAME.ID).toString() } + deleted
            // A game whose stored play can't be read is left out of a whole list; opening it fails on its own (SYS-052)
            val games = summaries(sql, shown, actor, checkPlays = changedSince == null)
            Result(200, json.encodeToString(GameList(games, if (rows.size > 100) rows[99].get(GAME.ID).toString() else null,
                token, removed)))
        }
    }

    /**
     * [summary] of each of [games] for [viewer]'s list, in two queries whatever their number (names, avatars, invitation
     * state and the engine seats in one; with [checkPlays] the active plays' rows in the other, each decoded so a game
     * whose play can't be read is left out, SYS-052). A play whose state isn't the engine's (no seats) reads whose move it
     * is from its play row.
     */
    private fun summaries(sql: DSLContext, games: List<org.asyncttwiz.server.generated.tables.records.GameRecord>, viewer: UUID,
        checkPlays: Boolean): List<GameSummary> {
        if (games.isEmpty()) return emptyList()
        val inviter = ACCOUNT.`as`("inviter")
        val recipient = ACCOUNT.`as`("recipient")
        val seats = DSL.field("(CASE WHEN {1} = 'ACTIVE' THEN (SELECT string_agg(s.account_id::text || '|' || s.waited::text || '|' || " +
            "coalesce(to_char(coalesce(s.next_stop_at, (SELECT e.created_at FROM engine_event e WHERE e.play_id = s.play_id " +
            "AND e.id = s.local_head)) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"'), ''), ',' ORDER BY s.account_id) " +
            "FROM engine_seat s WHERE s.play_id = {0}) END)", String::class.java, GAME.CURRENT_PLAY, GAME.LIFECYCLE)
        val ids = games.map { it.id }
        val identities = sql.select(GAME.ID, INVITATION.STATE, inviter.USERNAME, recipient.USERNAME, inviter.AVATAR_SCRYFALL_ID,
            inviter.AVATAR_FACE, recipient.AVATAR_SCRYFALL_ID, recipient.AVATAR_FACE, seats).from(GAME)
            .join(INVITATION).on(INVITATION.ID.eq(GAME.CURRENT_INVITATION))
            .join(inviter).on(inviter.ID.eq(GAME.INVITER_ID)).join(recipient).on(recipient.ID.eq(GAME.RECIPIENT_ID))
            .where(GAME.ID.`in`(ids)).fetch().associateBy { it.get(GAME.ID) }
        val active = games.filter { it.lifecycle == "ACTIVE" }.map { it.id }
        val plays = if (!checkPlays || active.isEmpty()) emptyMap() else sql.select(GAME.ID, org.asyncttwiz.server.generated.tables.GamePlay.GAME_PLAY.PAYLOAD)
            .from(GAME).join(org.asyncttwiz.server.generated.tables.GamePlay.GAME_PLAY)
            .on(org.asyncttwiz.server.generated.tables.GamePlay.GAME_PLAY.ID.eq(GAME.CURRENT_PLAY)
                .and(org.asyncttwiz.server.generated.tables.GamePlay.GAME_PLAY.GAME_ID.eq(GAME.ID)))
            .where(GAME.ID.`in`(active)).fetch().associate { it.value1() to it.value2() }
        return games.mapNotNull { game -> StoredData.orSkip("game", game.id) {
            val row = identities.getValue(game.id)
            val engine = row.value9()?.split(",")?.map { it.split("|") }
            val play = plays[game.id]?.let { payload -> StoredData.decode("game play", game.id!!) { GameWire.stored(PlayState.serializer(), payload) } }
            val holder = if (engine != null)
                engine.firstOrNull { it[0] == viewer.toString() && it[1] == "true" }?.get(0) ?: engine.firstOrNull { it[1] == "true" }?.get(0)
            else if (game.lifecycle != "ACTIVE") null
            else (play ?: org.asyncttwiz.server.game.PlayStore.read(sql, game.id!!))?.board?.effectiveAttentionHolder
            val activity = engine?.firstOrNull { it[0] == viewer.toString() }?.getOrNull(2)?.takeIf { it.isNotEmpty() }
                ?.let { java.time.Instant.parse(it).toString() }
            GameSummary(game.id.toString(), game.gameName!!, game.createdAt!!.toInstant().toString(),
                AccountIdentity(game.inviterId.toString(), row.value3()!!,
                    row.value5()?.let { Avatar(it.toString(), row.value6() ?: 0) }),
                AccountIdentity(game.recipientId.toString(), row.value4()!!,
                    row.value7()?.let { Avatar(it.toString(), row.value8() ?: 0) }), game.preparationRound.toString(),
                game.currentInvitation.toString(), InvitationState.valueOf(row.value2()!!), GameLifecycle.valueOf(game.lifecycle!!),
                game.revision.toString(), game.currentPlay?.toString(),
                holder,
                activity ?: game.updatedAt!!.toInstant().toString(), game.podId?.toString(), game.podName, game.podInvitationHeld == true)
        } }
    }

    fun head(principal: AccountService.Principal, id: String): Result {
        if (!DeckValidation.uuid(id)) return error(404, "not_found")
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val actor = owner(principal)
            val visible = sql.select(*GAME.fields()).from(GAME).join(INVITATION).on(INVITATION.ID.eq(GAME.CURRENT_INVITATION))
                .where(GAME.ID.eq(UUID.fromString(id))).and(GAME.LIFECYCLE.`in`("LOBBY", "ACTIVE"))
                .and(GAME.INVITER_ID.eq(actor).or(GAME.RECIPIENT_ID.eq(actor).and(INVITATION.STATE.eq("ACCEPTED"))))
                .forShare().fetchOneInto(GAME)
            if (visible != null) Result(200, json.encodeToString(summary(sql, visible, actor))) else error(404, "not_found")
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
