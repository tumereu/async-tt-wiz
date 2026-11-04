package org.asyncttwiz.server.activity

import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.generated.tables.Account.ACCOUNT
import org.asyncttwiz.server.generated.tables.AccountSession.ACCOUNT_SESSION as SESSION
import org.asyncttwiz.server.generated.tables.ActivityCursor.ACTIVITY_CURSOR as CURSOR
import org.asyncttwiz.server.generated.tables.AccountActivity.ACCOUNT_ACTIVITY as EVENT
import org.asyncttwiz.server.generated.tables.ActivitySummary.ACTIVITY_SUMMARY as SUMMARY
import org.asyncttwiz.server.generated.tables.ActivityInstallation.ACTIVITY_INSTALLATION as INSTALL
import org.asyncttwiz.server.generated.tables.ActivityPush.ACTIVITY_PUSH as PUSH
import org.asyncttwiz.server.generated.tables.ActivityPresence.ACTIVITY_PRESENCE as PRESENCE
import org.asyncttwiz.server.generated.tables.EngineSeat.ENGINE_SEAT
import org.asyncttwiz.server.generated.tables.Game.GAME
import org.asyncttwiz.server.generated.tables.PlayerPreparation.PLAYER_PREPARATION
import org.asyncttwiz.server.generated.tables.GamePlay.GAME_PLAY
import org.asyncttwiz.server.generated.tables.Invitation.INVITATION
import org.asyncttwiz.server.generated.tables.GameplayGroup.GAMEPLAY_GROUP
import org.asyncttwiz.server.generated.tables.GameplayMutation.GAMEPLAY_MUTATION
import org.asyncttwiz.server.generated.tables.Pod.POD
import org.asyncttwiz.server.generated.tables.PodMember.POD_MEMBER
import org.asyncttwiz.server.generated.tables.PodInvitation.POD_INVITATION
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

class ActivityFailure(val status: Int, val code: String) : RuntimeException(code)

/** Account-local cursor, never a game command or shared observation. */
class ActivityService(private val runtime: ServiceRuntime, private val clock: Clock = Clock.systemUTC()) {
    private fun now() = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
    private fun <T> transaction(work: (DSLContext) -> T): T = runtime.transaction { connection ->
        // A refusal rolls everything back; the commit that follows then has nothing to do.
        try { Result.success(work(DSL.using(connection, SQLDialect.POSTGRES))) }
        catch (failure: ActivityFailure) { connection.rollback(); Result.failure<T>(failure) }
    }.getOrThrow()
    private fun account(principal: AccountService.Principal) = UUID.fromString(principal.account.accountId)

    /** Records one heartbeat. Returns the player's new presence when it moved between the buckets the
     *  opponent's table shows (same statements as before: all of the player's rows replace the one-row lookup). */
    fun heartbeat(principal: AccountService.Principal, input: GamePresence): PresenceChange? {
        if (!DeckValidation.uuid(input.gameId) || !DeckValidation.uuid(input.presenceId)) throw ActivityFailure(400, "invalid_request")
        return transaction { sql ->
            val owner = account(principal); val game = UUID.fromString(input.gameId); val time = now()
            val row = sql.selectFrom(GAME).where(GAME.ID.eq(game)).forShare().fetchOne()
            if (row == null || owner !in listOf(row.inviterId, row.recipientId)) throw ActivityFailure(404, "game_unavailable")
            val presence = UUID.fromString(input.presenceId)
            val rows = OpponentPresences.rows(sql, game, owner)
            val old = rows.firstOrNull { it.presenceId == presence }
            val active = input.didSomething || old == null || old.left || !old.heartbeat.plusSeconds(15).isAfter(time)
            val inputAt = if (active) time else old!!.input
            sql.insertInto(PRESENCE).set(PRESENCE.GAME_ID, game).set(PRESENCE.ACCOUNT_ID, owner)
                .set(PRESENCE.SESSION_ID, principal.sessionId).set(PRESENCE.PRESENCE_ID, presence)
                .set(PRESENCE.HEARTBEAT_AT, time).set(PRESENCE.INPUT_AT, inputAt)
                .set(PRESENCE.LEFT_GAME, input.left).onConflict(PRESENCE.GAME_ID, PRESENCE.ACCOUNT_ID, PRESENCE.PRESENCE_ID)
                .doUpdate().set(PRESENCE.SESSION_ID, principal.sessionId).set(PRESENCE.HEARTBEAT_AT, time)
                .set(PRESENCE.INPUT_AT, inputAt).set(PRESENCE.LEFT_GAME, input.left).execute()
            val after = rows.filter { it.presenceId != presence } + PresenceRow(presence, time, inputAt, input.left)
            if (!input.left) {
                sql.update(SUMMARY).set(SUMMARY.SEEN_THROUGH, SUMMARY.ACTIVITY_ID).set(SUMMARY.NEW_MESSAGE, false).where(SUMMARY.ACCOUNT_ID.eq(owner))
                    .and(SUMMARY.GAME_ID.eq(game)).and(SUMMARY.KIND.eq("GAME")).execute()
                sql.deleteFrom(PUSH).where(PUSH.GAME_ID.eq(game)).and(PUSH.KIND.eq("GAME"))
                    .and(PUSH.GENERATION.`in`(sql.select(INSTALL.GENERATION).from(INSTALL).where(INSTALL.ACCOUNT_ID.eq(owner)))).execute()
            }
            if (OpponentPresences.bucket(rows, time) == OpponentPresences.bucket(after, time)) null
            else PresenceChange(input.gameId, owner.toString(), OpponentPresences.of(after, time))
        }
    }

    fun register(principal: AccountService.Principal, input: RegisterInstallation): ActivityRegistration {
        if (!ActivityWire.valid(input)) throw ActivityFailure(400, "invalid_request")
        return transaction { sql ->
            val id = UUID.fromString(input.installationId)
            val old = sql.selectFrom(INSTALL).where(INSTALL.INSTALLATION_ID.eq(id)).forUpdate().fetchOne()
            val binding = UUID.fromString(input.binding)
            val same = old?.sessionId == principal.sessionId && old.binding == binding && old.accountId == account(principal)
            val generation = if (same) old!!.generation!! else UUID.randomUUID()
            val invalid = old?.invalidTokenHash
            val token = input.pushToken?.takeUnless { tokenHash(it) == invalid }
            // Remove queued old-account hints before transferring this installation's ownership.
            if (!same && old != null) sql.deleteFrom(INSTALL).where(INSTALL.INSTALLATION_ID.eq(id)).execute()
            sql.insertInto(INSTALL).set(INSTALL.INSTALLATION_ID, id).set(INSTALL.ACCOUNT_ID, account(principal))
                .set(INSTALL.SESSION_ID, principal.sessionId).set(INSTALL.BINDING, binding).set(INSTALL.GENERATION, generation)
                .set(INSTALL.PUSH_TOKEN, token).set(INSTALL.INVALID_TOKEN_HASH, invalid)
                .onConflict(INSTALL.INSTALLATION_ID).doUpdate().set(INSTALL.PUSH_TOKEN, token).execute()
            ActivityRegistration(generation.toString())
        }
    }

    fun unregister(principal: AccountService.Principal, installation: String) {
        if (!DeckValidation.uuid(installation)) throw ActivityFailure(400, "invalid_request")
        transaction { sql -> sql.deleteFrom(INSTALL).where(INSTALL.INSTALLATION_ID.eq(UUID.fromString(installation)))
            .and(INSTALL.ACCOUNT_ID.eq(account(principal))).and(INSTALL.SESSION_ID.eq(principal.sessionId)).execute() }
    }

    /** Exact attempt/group lookup under the ordinary game lock. No replacement action or observation. */
    fun target(principal: AccountService.Principal, kind: ActivityKind, gameId: String, identity: String): ActivityTarget {
        if (!DeckValidation.uuid(gameId) || !DeckValidation.uuid(identity)) throw ActivityFailure(400, "invalid_request")
        return transaction { sql ->
            val owner = account(principal); val game = UUID.fromString(gameId); val id = UUID.fromString(identity)
            if (kind.name.startsWith("POD_")) {
                val exists = sql.selectFrom(POD).where(POD.ID.eq(game)).forShare().fetchOne()
                val member = sql.fetchExists(POD_MEMBER, POD_MEMBER.POD_ID.eq(game).and(POD_MEMBER.ACCOUNT_ID.eq(owner)))
                val invited = sql.fetchExists(POD_INVITATION, POD_INVITATION.POD_ID.eq(game).and(POD_INVITATION.ACCOUNT_ID.eq(owner))
                    .and(POD_INVITATION.STATE.eq("PENDING")))
                return@transaction ActivityTarget(gameId, if (exists != null &&
                    (member || kind == ActivityKind.POD_INVITATION && invited)) ActivityDestination.POD else ActivityDestination.UNAVAILABLE)
            }
            val row = sql.selectFrom(GAME).where(GAME.ID.eq(game)).forUpdate().fetchOne()
            val unavailable = ActivityTarget(gameId, ActivityDestination.UNAVAILABLE)
            if (row == null || owner !in listOf(row.inviterId, row.recipientId) || row.lifecycle !in listOf("LOBBY", "ACTIVE"))
                return@transaction unavailable
            val invitation = sql.selectFrom(INVITATION).where(INVITATION.ID.eq(row.currentInvitation)).fetchSingle()
            if (owner == row.recipientId && invitation.state !in listOf("PENDING", "ACCEPTED")) return@transaction unavailable
            if (kind == ActivityKind.INVITATION && row.recipientId == owner && row.currentInvitation == id &&
                row.lifecycle == "LOBBY" && invitation.state == "PENDING")
                return@transaction ActivityTarget(gameId, ActivityDestination.INVITATION, invitationId = identity)
            if (kind == ActivityKind.GAME && row.lifecycle == "ACTIVE" && (owner == row.inviterId || invitation.state == "ACCEPTED")) {
                val group = sql.selectFrom(GAMEPLAY_GROUP).where(GAMEPLAY_GROUP.ID.eq(id))
                    .and(GAMEPLAY_GROUP.GAME_ID.eq(game)).and(GAMEPLAY_GROUP.PLAY_ID.eq(row.currentPlay)).fetchOne()
                if (group != null) {
                    val first = sql.selectFrom(GAMEPLAY_MUTATION).where(GAMEPLAY_MUTATION.ID.eq(group.firstNodeId))
                        .and(GAMEPLAY_MUTATION.PLAY_ID.eq(row.currentPlay)).fetchSingle()
                    return@transaction ActivityTarget(gameId, ActivityDestination.GROUP, playId = row.currentPlay.toString(),
                        groupId = identity, boundaryNodeId = first.parentId.toString())
                }
            }
            ActivityTarget(gameId, ActivityDestination.CURRENT, playId = row.currentPlay?.toString())
        }
    }

    /** Bounded maintenance also runs without FCM or an account reopening the app. */
    fun pruneExpired() = transaction { sql ->
        // Keep each player's newest row in a game: it is their "last seen" for the opponent.
        val newer = PRESENCE.`as`("newer")
        sql.deleteFrom(PRESENCE).where(PRESENCE.HEARTBEAT_AT.lt(now().minusDays(1))).and(DSL.exists(DSL.selectOne().from(newer)
            .where(newer.GAME_ID.eq(PRESENCE.GAME_ID)).and(newer.ACCOUNT_ID.eq(PRESENCE.ACCOUNT_ID))
            .and(newer.HEARTBEAT_AT.gt(PRESENCE.HEARTBEAT_AT).or(newer.HEARTBEAT_AT.eq(PRESENCE.HEARTBEAT_AT)
                .and(newer.PRESENCE_ID.gt(PRESENCE.PRESENCE_ID)))))).execute()
        val owners = sql.selectDistinct(EVENT.ACCOUNT_ID).from(EVENT).where(EVENT.CREATED_AT.lt(now().minusDays(30)))
            .orderBy(EVENT.ACCOUNT_ID).limit(100).fetch(EVENT.ACCOUNT_ID)
        for (owner in owners) {
            val state = sql.selectFrom(CURSOR).where(CURSOR.ACCOUNT_ID.eq(owner)).forUpdate().fetchSingle()
            prune(sql, owner!!, state.retentionFloor!!)
        }
    }

    private fun prune(sql: DSLContext, owner: UUID, previous: Long): Long {
        val expired = sql.select(DSL.max(EVENT.ACTIVITY_ID)).from(EVENT).where(EVENT.ACCOUNT_ID.eq(owner))
            .and(EVENT.CREATED_AT.lt(now().minusDays(30))).fetchOne(0, Long::class.java)
        val floor = maxOf(previous, expired ?: 0)
        if (floor != previous) {
            sql.deleteFrom(EVENT).where(EVENT.ACCOUNT_ID.eq(owner)).and(EVENT.ACTIVITY_ID.le(floor)).execute()
            sql.update(CURSOR).set(CURSOR.RETENTION_FLOOR, floor).where(CURSOR.ACCOUNT_ID.eq(owner)).execute()
        }
        return floor
    }

    fun page(principal: AccountService.Principal, after: String?): ActivityPage = transaction { sql ->
        val owner = account(principal)
        sql.insertInto(CURSOR).set(CURSOR.ACCOUNT_ID, owner).onConflictDoNothing().execute()
        val state = sql.selectFrom(CURSOR).where(CURSOR.ACCOUNT_ID.eq(owner)).forUpdate().fetchSingle()
        // Actual contiguous prefix, not a fictitious minimum based on surviving rows.
        val floor = prune(sql, owner, state.retentionFloor!!)
        val high = state.highWater!!
        val resetParts = after?.takeIf { it.startsWith("r:") }?.split(':')
        val normal = if (after == null || resetParts != null) null else ActivityWire.ordinal(after)
            ?: throw ActivityFailure(400, "invalid_cursor")
        if (normal != null && normal > high) throw ActivityFailure(400, "invalid_cursor")
        var snapshot = high
        var lower = 0L
        if (resetParts != null) {
            if (resetParts.size != 3) throw ActivityFailure(400, "invalid_cursor")
            snapshot = ActivityWire.ordinal(resetParts[1]) ?: throw ActivityFailure(400, "invalid_cursor")
            lower = ActivityWire.ordinal(resetParts[2]) ?: throw ActivityFailure(400, "invalid_cursor")
            if (snapshot > high || lower > snapshot) throw ActivityFailure(400, "invalid_cursor")
            if (snapshot < floor) { snapshot = high; lower = 0 }
        }
        val reset = after == null || resetParts != null || normal!! < floor
        val source = if (reset) SUMMARY else EVENT
        val activity = if (reset) SUMMARY.ACTIVITY_ID else EVENT.ACTIVITY_ID
        val kind = if (reset) SUMMARY.KIND else EVENT.KIND
        val identity = if (reset) SUMMARY.IDENTITY else EVENT.IDENTITY
        val author = if (reset) SUMMARY.AUTHOR_ID else EVENT.AUTHOR_ID
        val game = if (reset) SUMMARY.GAME_ID else EVENT.GAME_ID
        val ownerField = if (reset) SUMMARY.ACCOUNT_ID else EVENT.ACCOUNT_ID
        val rows = sql.select(activity, kind, game, identity, author).from(source)
            .leftJoin(GAME).on(GAME.ID.eq(game)).leftJoin(INVITATION).on(INVITATION.ID.eq(GAME.CURRENT_INVITATION))
            .where(ownerField.eq(owner)).and(activity.gt(if (reset) lower else normal!!))
            .and(activity.le(if (reset) snapshot else high)).and(visible(owner, kind, identity).or(podVisible(DSL.`val`(owner), kind, game, identity)))
            .orderBy(activity).limit(101).fetch()
        val kinds = rows.take(100).map { ActivityKind.valueOf(it[kind]!!) }
        // Pod names in one query, not one per entry.
        val pods = rows.take(100).filterIndexed { index, _ -> kinds[index].name.startsWith("POD_") }.mapNotNull { it[game] }.toSet()
        val podNames = if (pods.isEmpty()) emptyMap() else sql.select(POD.ID, POD.NAME, POD.SOURCE_NAME).from(POD).where(POD.ID.`in`(pods)).fetch()
            .associate { it[POD.ID]!! to (it[POD.NAME] to it[POD.SOURCE_NAME]) }
        val entries = rows.take(100).mapIndexed { index, row ->
            val type = kinds[index]
            val pod = if (type.name.startsWith("POD_")) podNames[row[game]] else null
            // I-D57: a pod's notice names the set or cube it drafts
            ActivityHint(row[activity].toString(), type, row[game].toString(), row[identity].toString(), row[author]?.toString(),
                resourceName = pod?.first, sourceName = pod?.second)
        }
        val more = rows.size > 100
        val next = if (more) {
            if (reset) "r:$snapshot:${entries.last().activityId}" else entries.last().activityId
        } else (if (reset) snapshot else high).toString()
        // Sync cursor advances immediately. Notification eligibility is re-evaluated independently
        // on every poll, including empty deltas, so an actor's later heartbeat can reset the timer.
        val ready = sql.selectFrom(SUMMARY).where(SUMMARY.ACCOUNT_ID.eq(owner)).and(SUMMARY.KIND.eq("GAME"))
            .and(SUMMARY.ACTIVITY_ID.gt(SUMMARY.SEEN_THROUGH)).and(SUMMARY.ACTIVITY_ID.le(high))
            .orderBy(SUMMARY.ACTIVITY_ID.desc()).fetch().asSequence().mapNotNull { row ->
                // LOB-111: the game and, in the same query, the outcome of the result suggestion whose acceptance is this
                // activity (the play it ended records that request); null for any other activity.
                val joined = sql.select(GAME.fields().toList() + outcome + suggestionAuthor + suggestionByRules + ENGINE_SEAT.RESUME_AT + ownDeckName)
                    .from(GAME).leftJoin(GAME_PLAY)
                    .on(GAME_PLAY.GAME_ID.eq(GAME.ID)).and(GAME_PLAY.ENDED_REQUEST_ID.eq(row.identity))
                    .leftJoin(ENGINE_SEAT).on(ENGINE_SEAT.PLAY_ID.eq(GAME.CURRENT_PLAY)).and(ENGINE_SEAT.ACCOUNT_ID.eq(owner))
                    .leftJoin(PLAYER_PREPARATION).on(PLAYER_PREPARATION.GAME_ID.eq(GAME.ID)).and(PLAYER_PREPARATION.ACCOUNT_ID.eq(owner))
                    .where(GAME.ID.eq(row.gameId)).fetchOne() ?: return@mapNotNull null
                val gameRow = joined.into(GAME)
                val byRules = joined[suggestionByRules] == "true"
                // I-D53: the rules' result is told from the recipient's side (its author may be either player)
                val gameOver = joined[outcome]?.let { o -> SuggestedResult.entries.firstOrNull { it.name == o } }?.let { o ->
                    if (!byRules) o else ResultSuggestion("", joined[suggestionAuthor].orEmpty(), o).outcomeFor(owner.toString()) }
                if (gameRow.lifecycle !in listOf("LOBBY", "ACTIVE")) return@mapNotNull null
                val authorId = row.authorId ?: return@mapNotNull null
                // CR 104.3a (I-D52): the accepter accepted their own loss: they conceded
                val conceded = gameOver != null && !byRules && joined[suggestionAuthor] == authorId.toString()
                val recipient = ActivityTiming.leases(sql, row.gameId!!, owner)
                // A push already sent for this activity decided it was due; a later actor input must not hide it.
                val pushed = row.pushedThrough!! >= row.activityId!! && !ActivityTiming.present(recipient, now())
                if (!pushed && !ActivityTiming.due(ActivityTiming.leases(sql, row.gameId!!, authorId),
                        recipient, row.lastEventAt!!, row.yourMove!!, now())) return@mapNotNull null
                val opponent = sql.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(authorId)).fetchSingle()
                ActivityHint(row.activityId.toString(), ActivityKind.GAME, row.gameId.toString(), row.identity.toString(),
                    authorId.toString(), GameNoticeContent(gameRow.gameName!!, opponent.username!!,
                        row.lastEventAt.toString(), row.yourMove!!, row.newMessage!!, gameOver = gameOver,
                        opponentReady = row.opponentReady!!, conceded = conceded, byRules = byRules && gameOver != null,
                        branched = row.branched!! && gameOver == null, resumeAt = resumePoint(joined[ENGINE_SEAT.RESUME_AT]),
                        deckName = joined[ownDeckName]))
            }.take(50).toList()
        // A sent reminder stays listed (client watermarks dedupe it) until the recipient moves.
        val reminders = sql.select(SUMMARY.fields().toList() + GAME.GAME_NAME + ACCOUNT.USERNAME + ENGINE_SEAT.RESUME_AT + ownDeckName).from(SUMMARY)
            .join(GAME).on(GAME.ID.eq(SUMMARY.GAME_ID)).join(ACCOUNT).on(ACCOUNT.ID.eq(SUMMARY.AUTHOR_ID))
            .leftJoin(ENGINE_SEAT).on(ENGINE_SEAT.PLAY_ID.eq(GAME.CURRENT_PLAY)).and(ENGINE_SEAT.ACCOUNT_ID.eq(owner))
            .leftJoin(PLAYER_PREPARATION).on(PLAYER_PREPARATION.GAME_ID.eq(GAME.ID)).and(PLAYER_PREPARATION.ACCOUNT_ID.eq(owner))
            .where(SUMMARY.ACCOUNT_ID.eq(owner)).and(SUMMARY.KIND.eq("GAME")).and(SUMMARY.REMINDER_ACTIVITY_ID.le(high))
            .and(GAME.LIFECYCLE.eq("ACTIVE")).andNot(answered()).orderBy(SUMMARY.REMINDER_ACTIVITY_ID.desc()).limit(50).fetch().map { row ->
                ActivityHint(row[SUMMARY.REMINDER_ACTIVITY_ID].toString(), ActivityKind.GAME, row[SUMMARY.GAME_ID].toString(),
                    row[SUMMARY.IDENTITY].toString(), row[SUMMARY.AUTHOR_ID].toString(), GameNoticeContent(row[GAME.GAME_NAME]!!,
                        row[ACCOUNT.USERNAME]!!, row[SUMMARY.REMINDER_MOVE_AT].toString(), row[SUMMARY.YOUR_MOVE]!!, reminder = true,
                        resumeAt = resumePoint(row[ENGINE_SEAT.RESUME_AT]), deckName = row[ownDeckName]))
            }
        val notices = (ready + reminders).sortedByDescending { ActivityWire.ordinal(it.activityId) }.take(50)
        ActivityPage(entries, next, floor.toString(), reset, more, entries.filter { it.kind != ActivityKind.GAME } + notices)
    }

    /** I-D57: the recipient's own deck name in the game (their private pod deck name, else the deck they selected), with
     * [PLAYER_PREPARATION] joined for them. */
    private val ownDeckName = DSL.coalesce(PLAYER_PREPARATION.POD_DECK_NAME,
        DSL.field("({0})::jsonb ->> 'name'", String::class.java, PLAYER_PREPARATION.DECK)).`as`("own_deck_name")

    /** I-D57: the stored point the recipient would resume at (their local head's turn and step). */
    private fun resumePoint(encoded: String?): ResumePoint? = encoded?.let {
        runCatching { GameWire.json.decodeFromString(ResumePoint.serializer(), it) }.getOrNull() }

    /** LOB-111: a suggestion's outcome as recorded in the play its acceptance ended. */
    private val outcome = DSL.field("({0})::jsonb -> 'board' -> 'resultSuggestion' ->> 'outcome'", String::class.java, GAME_PLAY.PAYLOAD)
    /** Who suggested it: the accepter themselves when they conceded (CR 104.3a). */
    private val suggestionAuthor = DSL.field("({0})::jsonb -> 'board' -> 'resultSuggestion' ->> 'author'", String::class.java, GAME_PLAY.PAYLOAD)
    /** I-D53: whether it was the rules' result. */
    private val suggestionByRules = DSL.field("({0})::jsonb -> 'board' -> 'resultSuggestion' ->> 'byRules'", String::class.java, GAME_PLAY.PAYLOAD)

    /** The app's own fallback when push and the feed poll missed a game or a pod: games in play that wait for the account
     * and whose other player is idle by the rule that makes a push due ([ActivityTiming.due]). On engine state (I-D57) the
     * game waits for the account by its seat ([engineWaiting]), also when the account itself played on and left with
     * nothing waiting for the other player; otherwise the opponent made the latest event (the other player's row holds
     * the account's own latest event) and the move is the account's. Read only. */
    fun waiting(principal: AccountService.Principal): WaitingGames = transaction { sql ->
        val owner = account(principal); val time = now()
        val own = SUMMARY.`as`("own")
        val engine = DSL.exists(DSL.selectOne().from(ENGINE_SEAT).where(ENGINE_SEAT.PLAY_ID.eq(GAME.CURRENT_PLAY)))
        val rows = sql.select(SUMMARY.fields().toList() + GAME.GAME_NAME + ACCOUNT.USERNAME).from(SUMMARY)
            .join(GAME).on(GAME.ID.eq(SUMMARY.GAME_ID)).join(INVITATION).on(INVITATION.ID.eq(GAME.CURRENT_INVITATION))
            .join(ACCOUNT).on(ACCOUNT.ID.eq(SUMMARY.AUTHOR_ID))
            .where(SUMMARY.ACCOUNT_ID.eq(owner)).and(SUMMARY.KIND.eq("GAME")).and(SUMMARY.YOUR_MOVE.isTrue)
            .and(GAME.LIFECYCLE.eq("ACTIVE")).and(visible(owner, SUMMARY.KIND, SUMMARY.IDENTITY))
            .andNotExists(DSL.selectOne().from(own).where(own.GAME_ID.eq(SUMMARY.GAME_ID)).and(own.KIND.eq("GAME"))
                .and(own.ACCOUNT_ID.ne(owner)).and(own.LAST_EVENT_AT.gt(SUMMARY.LAST_EVENT_AT))).andNot(engine)
            .orderBy(SUMMARY.LAST_EVENT_AT.desc()).limit(50).fetch()
        val games = rows.filter { row ->
            ActivityTiming.due(ActivityTiming.leases(sql, row[SUMMARY.GAME_ID]!!, row[SUMMARY.AUTHOR_ID]!!),
                ActivityTiming.leases(sql, row[SUMMARY.GAME_ID]!!, owner), row[SUMMARY.LAST_EVENT_AT]!!, true, time)
        }.map { row ->
            ActivityHint(row[SUMMARY.ACTIVITY_ID].toString(), ActivityKind.GAME, row[SUMMARY.GAME_ID].toString(),
                row[SUMMARY.IDENTITY].toString(), row[SUMMARY.AUTHOR_ID].toString(), GameNoticeContent(row[GAME.GAME_NAME]!!,
                    row[ACCOUNT.USERNAME]!!, row[SUMMARY.LAST_EVENT_AT].toString(), true, row[SUMMARY.NEW_MESSAGE]!!, reminder = true))
        }
        WaitingGames((games + engineWaiting(sql, owner, time)).sortedByDescending { OffsetDateTime.parse(it.notice!!.lastEventAt) }
            .take(50) + waitingPods(sql, owner))
    }

    /** I-D57: the engine games that wait for [owner] by their seat ([org.asyncttwiz.protocol.game.NextStop.waitsFor]), timed
     * from their next stop's event with the other player idle by the push rule; the notice names the opponent, the point
     * the account would resume at and its own deck. */
    private fun engineWaiting(sql: DSLContext, owner: UUID, time: OffsetDateTime): List<ActivityHint> {
        val other = DSL.`when`(GAME.INVITER_ID.eq(owner), GAME.RECIPIENT_ID).otherwise(GAME.INVITER_ID)
        val rows = sql.select(GAME.ID, GAME.GAME_NAME, ACCOUNT.ID, ACCOUNT.USERNAME, ENGINE_SEAT.NEXT_STOP, ENGINE_SEAT.NEXT_STOP_AT,
            ENGINE_SEAT.RESUME_AT, ownDeckName, SUMMARY.ACTIVITY_ID, SUMMARY.NEW_MESSAGE).from(ENGINE_SEAT)
            .join(GAME).on(GAME.CURRENT_PLAY.eq(ENGINE_SEAT.PLAY_ID)).join(INVITATION).on(INVITATION.ID.eq(GAME.CURRENT_INVITATION))
            .join(ACCOUNT).on(ACCOUNT.ID.eq(other))
            .leftJoin(PLAYER_PREPARATION).on(PLAYER_PREPARATION.GAME_ID.eq(GAME.ID)).and(PLAYER_PREPARATION.ACCOUNT_ID.eq(owner))
            .leftJoin(SUMMARY).on(SUMMARY.ACCOUNT_ID.eq(owner)).and(SUMMARY.GAME_ID.eq(GAME.ID)).and(SUMMARY.KIND.eq("GAME"))
            .where(ENGINE_SEAT.ACCOUNT_ID.eq(owner)).and(ENGINE_SEAT.WAITED.isTrue).and(ENGINE_SEAT.NEXT_STOP_AT.isNotNull)
            .and(GAME.LIFECYCLE.eq("ACTIVE")).and(GAME.INVITER_ID.eq(owner).or(INVITATION.STATE.eq("ACCEPTED")))
            .orderBy(ENGINE_SEAT.NEXT_STOP_AT.desc()).limit(50).fetch()
        return rows.filter { row ->
            ActivityTiming.due(ActivityTiming.leases(sql, row[GAME.ID]!!, row[ACCOUNT.ID]!!),
                ActivityTiming.leases(sql, row[GAME.ID]!!, owner), row[ENGINE_SEAT.NEXT_STOP_AT]!!, true, time)
        }.map { row ->
            ActivityHint((row[SUMMARY.ACTIVITY_ID] ?: 0L).toString(), ActivityKind.GAME, row[GAME.ID].toString(),
                (row[ENGINE_SEAT.NEXT_STOP] ?: row[GAME.ID]).toString(), row[ACCOUNT.ID].toString(), GameNoticeContent(row[GAME.GAME_NAME]!!,
                    row[ACCOUNT.USERNAME]!!, row[ENGINE_SEAT.NEXT_STOP_AT].toString(), true, row[SUMMARY.NEW_MESSAGE] == true, reminder = true,
                    resumeAt = resumePoint(row[ENGINE_SEAT.RESUME_AT]), deckName = row[ownDeckName]))
        }
    }

    /** Pods waiting for the account (DRF-136): its pick, with the hint of the pack that arrived at its empty queue,
     * or its deck build after the draft, until it submits. Older apps only read the game hints. */
    private fun waitingPods(sql: DSLContext, owner: UUID): List<ActivityHint> {
        val unsubmitted = DSL.exists(DSL.selectOne().from(POD_MEMBER).where(POD_MEMBER.POD_ID.eq(SUMMARY.GAME_ID))
            .and(POD_MEMBER.ACCOUNT_ID.eq(owner)).and(POD_MEMBER.SUBMITTED_DECK.isNull))
        return sql.select(SUMMARY.ACTIVITY_ID, SUMMARY.KIND, SUMMARY.GAME_ID, SUMMARY.IDENTITY, POD.NAME, POD.SOURCE_NAME).from(SUMMARY)
            .join(POD).on(POD.ID.eq(SUMMARY.GAME_ID))
            .where(SUMMARY.ACCOUNT_ID.eq(owner)).and(podVisible(DSL.`val`(owner), SUMMARY.KIND, SUMMARY.GAME_ID, SUMMARY.IDENTITY))
            .and(SUMMARY.KIND.eq(ActivityKind.POD_YOUR_PICK.name).or(SUMMARY.KIND.eq(ActivityKind.POD_DRAFT_COMPLETE.name).and(unsubmitted)))
            .orderBy(SUMMARY.LAST_EVENT_AT.desc(), SUMMARY.GAME_ID).limit(50).fetch().map { row ->
                ActivityHint(row[SUMMARY.ACTIVITY_ID].toString(), ActivityKind.valueOf(row[SUMMARY.KIND]!!), row[SUMMARY.GAME_ID].toString(),
                    row[SUMMARY.IDENTITY].toString(), resourceName = row[POD.NAME], sourceName = row[POD.SOURCE_NAME])
            }
    }

    companion object {
        internal fun tokenHash(token: String) = java.security.MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
        internal fun visible(owner: UUID, kind: org.jooq.Field<String>, identity: org.jooq.Field<UUID>) =
            GAME.LIFECYCLE.`in`("LOBBY", "ACTIVE").and(
                kind.eq("INVITATION").and(GAME.RECIPIENT_ID.eq(owner)).and(INVITATION.STATE.eq("PENDING"))
                    .and(GAME.CURRENT_INVITATION.eq(identity)).or(
                        kind.eq("GAME").and(GAME.INVITER_ID.eq(owner).or(GAME.RECIPIENT_ID.eq(owner)
                             .and(INVITATION.STATE.eq("ACCEPTED"))))))

        internal fun podVisible(owner: org.jooq.Field<UUID>, kind: org.jooq.Field<String>, resource: org.jooq.Field<UUID>,
            identity: org.jooq.Field<UUID>): org.jooq.Condition {
            val member = DSL.exists(DSL.selectOne().from(POD_MEMBER).join(POD).on(POD.ID.eq(POD_MEMBER.POD_ID))
                .where(POD.ID.eq(resource)).and(POD_MEMBER.ACCOUNT_ID.eq(owner)).and(POD.DRAFT_ATTEMPT_ID.eq(identity)).and(
                    kind.eq("POD_YOUR_PICK").and(POD.PHASE.eq("DRAFTING")).and(DSL.condition(
                        "jsonb_array_length({0}->'queues'->cast({1} as text)) > 0", POD.DRAFT_STATE, owner).or(DSL.condition(
                        // Grid snapshots from before per-member opening have no queues: a turn in a dealt grid is a pick.
                        "({0}->'queues') is null and exists (select 1 from jsonb_array_elements({0}->'grids') as board(value) " +
                            "where board.value->'turns'->>cast(board.value->>'selection' as integer) = cast({1} as text))",
                        POD.DRAFT_STATE, owner)))
                        .or(kind.`in`("POD_DRAFT_COMPLETE", "POD_DECKS_READY").and(POD.PHASE.eq("DRAFTED")))))
            val invited = DSL.exists(DSL.selectOne().from(POD_INVITATION).join(POD).on(POD.ID.eq(POD_INVITATION.POD_ID))
                .where(POD.ID.eq(resource)).and(POD_INVITATION.ACCOUNT_ID.eq(owner)).and(POD_INVITATION.STATE.eq("PENDING"))
                .and(POD.PHASE.eq("SETUP")))
            // Auto-start notices concern only the creator, while the pod is still in setup and not re-armed.
            val autoStartOff = DSL.exists(DSL.selectOne().from(POD).where(POD.ID.eq(resource)).and(POD.CREATOR_ID.eq(owner))
                .and(POD.PHASE.eq("SETUP")).and(POD.AUTO_START.isFalse))
            return kind.`in`("POD_DRAFT_CANCELLED", "POD_DESTROYED").or(member).or(kind.eq("POD_INVITATION").and(invited))
                .or(kind.eq("POD_AUTO_START_OFF").and(autoStartOff))
        }

        /** Call inside the accepting transaction. Gameplay integration supplies the real group ID.
         * Only an opponent's activity is recorded; publication never waits for a delivery window.
         * [move]: a gameplay move by [author] (not chat or a play start). It (re)arms [owner]'s
         * waiting reminder; the same column on the author's own row is then that author's latest move, which
         * is how [answered] sees a stretch end without another statement per move. [opponentReady] (LOB-114): after this
         * activity [author] is Ready while [owner] is still in the opening "Mulliganing" status. */
        fun record(sql: DSLContext, owner: UUID, kind: ActivityKind, game: UUID, identity: UUID,
            author: UUID? = null, time: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
            yourMove: Boolean = false, newMessage: Boolean = false, move: Boolean = false, opponentReady: Boolean = false,
            branched: Boolean = false) =
            recordAll(sql, owner, kind, game, listOf(identity to author), time, yourMove, newMessage, move, opponentReady, branched)

        /** [record] for each of [events] (identity, author) in order, at one [time], in a few statements however many
         * there are (a 1000-member Automatum batch, I-D14): consecutive activity ids, every event row, and the summary
         * and pending pushes as the last of the one-by-one records leaves them. */
        fun recordAll(sql: DSLContext, owner: UUID, kind: ActivityKind, game: UUID, events: List<Pair<UUID, UUID?>>,
            time: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
            yourMove: Boolean = false, newMessage: Boolean = false, move: Boolean = false, opponentReady: Boolean = false,
            branched: Boolean = false) {
            val recorded = events.filter { (_, author) -> author != owner }
            if (recorded.isEmpty()) return
            val last = nextActivityIds(sql, owner, recorded.size)
            val ids = (last - recorded.size + 1..last).toList()
            var insert = sql.insertInto(EVENT, EVENT.ACCOUNT_ID, EVENT.ACTIVITY_ID, EVENT.KIND, EVENT.GAME_ID, EVENT.IDENTITY,
                EVENT.AUTHOR_ID, EVENT.CREATED_AT)
            for ((i, event) in recorded.withIndex()) insert = insert.values(owner, ids[i], kind.name, game, event.first, event.second, time)
            insert.execute()
            val (identity, author) = recorded.last()
            val id = ids.last()
            // Never seen here: only a later heartbeat of the recipient in this game marks it seen. A recent
            // heartbeat proves presence only up to that heartbeat; the app may have closed since.
            val armed = move && kind == ActivityKind.GAME
            sql.insertInto(SUMMARY).set(SUMMARY.ACCOUNT_ID, owner).set(SUMMARY.GAME_ID, game).set(SUMMARY.KIND, kind.name)
                .set(SUMMARY.ACTIVITY_ID, id).set(SUMMARY.IDENTITY, identity).set(SUMMARY.AUTHOR_ID, author)
                .set(SUMMARY.LAST_EVENT_AT, time).set(SUMMARY.YOUR_MOVE, yourMove)
                .set(SUMMARY.NEW_MESSAGE, newMessage).set(SUMMARY.OPPONENT_READY, opponentReady).set(SUMMARY.BRANCHED, branched)
                .set(SUMMARY.REMINDER_MOVE_AT, if (armed) time else null)
                .onConflict(SUMMARY.ACCOUNT_ID, SUMMARY.GAME_ID, SUMMARY.KIND).doUpdate()
                .set(SUMMARY.ACTIVITY_ID, id).set(SUMMARY.IDENTITY, identity).set(SUMMARY.AUTHOR_ID, author)
                .set(SUMMARY.LAST_EVENT_AT, time).set(SUMMARY.YOUR_MOVE, yourMove).set(SUMMARY.OPPONENT_READY, opponentReady)
                .set(SUMMARY.BRANCHED, branched)
                .set(SUMMARY.NEW_MESSAGE, DSL.field("{0} or {1}", Boolean::class.java, SUMMARY.NEW_MESSAGE, DSL.excluded(SUMMARY.NEW_MESSAGE)))
                .set(SUMMARY.REMINDER_MOVE_AT, if (armed) DSL.excluded(SUMMARY.REMINDER_MOVE_AT) else SUMMARY.REMINDER_MOVE_AT)
                // Re-arming keeps a sent reminder within one stretch; a stretch the recipient answered starts anew.
                // (ON CONFLICT expressions see the existing row, so this compares against the previous move.)
                .set(SUMMARY.REMINDER_ACTIVITY_ID, if (armed) DSL.`when`(answered(), DSL.inline(null, SUMMARY.REMINDER_ACTIVITY_ID))
                    .otherwise(SUMMARY.REMINDER_ACTIVITY_ID) else SUMMARY.REMINDER_ACTIVITY_ID).execute()
            queuePush(sql, owner, kind, game, ids.zip(recorded.map { it.first }), time)
        }

        /** The last of [count] new consecutive activity ids of [owner]. */
        private fun nextActivityIds(sql: DSLContext, owner: UUID, count: Int): Long = sql.insertInto(CURSOR).set(CURSOR.ACCOUNT_ID, owner)
            .set(CURSOR.HIGH_WATER, count.toLong()).onConflict(CURSOR.ACCOUNT_ID).doUpdate().set(CURSOR.HIGH_WATER, CURSOR.HIGH_WATER.plus(count.toLong()))
            .returning(CURSOR.HIGH_WATER).fetchSingle().highWater!!

        /** A new play starts without either player's waiting reminder from an earlier play. */
        fun resetReminders(sql: DSLContext, game: UUID) {
            sql.update(SUMMARY).setNull(SUMMARY.REMINDER_MOVE_AT).setNull(SUMMARY.REMINDER_ACTIVITY_ID)
                .where(SUMMARY.GAME_ID.eq(game)).and(SUMMARY.KIND.eq("GAME")).execute()
        }

        /**
         * [owner]'s move in [game] ended without the other player [other] getting a new one to be told of (on engine state:
         * a confirmation, playback past their stop, an undo request; switchreview #3): [owner]'s row no longer says it's
         * their move, and their waiting stretch is answered. When [other] has the move, this is [owner]'s move towards them
         * (it re-arms [other]'s reminder as [record] does); when nobody has one, neither player's reminder stays open.
         */
        fun moveEnded(sql: DSLContext, owner: UUID, game: UUID, other: UUID, otherMoves: Boolean, time: OffsetDateTime) {
            sql.update(SUMMARY).set(SUMMARY.YOUR_MOVE, false).where(SUMMARY.ACCOUNT_ID.eq(owner)).and(SUMMARY.GAME_ID.eq(game))
                .and(SUMMARY.KIND.eq("GAME")).execute()
            if (!otherMoves) return resetReminders(sql, game)
            // (UPDATE expressions see the row as it was, so this compares against [owner]'s previous move, as [record] does)
            val rearmed = sql.update(SUMMARY).set(SUMMARY.REMINDER_MOVE_AT, time)
                .set(SUMMARY.REMINDER_ACTIVITY_ID, DSL.`when`(answered(), DSL.inline(null, SUMMARY.REMINDER_ACTIVITY_ID))
                    .otherwise(SUMMARY.REMINDER_ACTIVITY_ID))
                .where(SUMMARY.ACCOUNT_ID.eq(other)).and(SUMMARY.GAME_ID.eq(game)).and(SUMMARY.KIND.eq("GAME")).execute()
            // Without a row of [other]'s there is no stretch of theirs to keep: [owner]'s own stretch just closes
            if (rearmed == 0) sql.update(SUMMARY).setNull(SUMMARY.REMINDER_MOVE_AT).setNull(SUMMARY.REMINDER_ACTIVITY_ID)
                .where(SUMMARY.ACCOUNT_ID.eq(owner)).and(SUMMARY.GAME_ID.eq(game)).and(SUMMARY.KIND.eq("GAME")).execute()
        }

        /**
         * I-D57: the game no longer waits for [owner] though they did nothing (the other player went on instead: assuming
         * after all, cancelling an undo request). Their "Your move" notice not yet seen is withdrawn with its pending push
         * (one that also tells a new message or a branch stays, no longer as their move), and their waiting stretch closes.
         */
        fun waitWithdrawn(sql: DSLContext, owner: UUID, game: UUID) {
            val row = SUMMARY.ACCOUNT_ID.eq(owner).and(SUMMARY.GAME_ID.eq(game)).and(SUMMARY.KIND.eq("GAME"))
            val kept = SUMMARY.NEW_MESSAGE.isTrue.or(SUMMARY.BRANCHED.isTrue)
            val withdrawn = sql.update(SUMMARY).set(SUMMARY.YOUR_MOVE, false).set(SUMMARY.OPPONENT_READY, false)
                .set(SUMMARY.SEEN_THROUGH, DSL.`when`(kept, SUMMARY.SEEN_THROUGH).otherwise(SUMMARY.ACTIVITY_ID))
                .setNull(SUMMARY.REMINDER_MOVE_AT).setNull(SUMMARY.REMINDER_ACTIVITY_ID)
                .where(row).returning(SUMMARY.NEW_MESSAGE, SUMMARY.BRANCHED).fetchOne() ?: return
            if (withdrawn.newMessage == true || withdrawn.branched == true) return
            sql.deleteFrom(PUSH).where(PUSH.GAME_ID.eq(game)).and(PUSH.KIND.eq("GAME"))
                .and(PUSH.GENERATION.`in`(sql.select(INSTALL.GENERATION).from(INSTALL).where(INSTALL.ACCOUNT_ID.eq(owner)))).execute()
        }

        const val REMINDER_SECONDS = 300L

        /** The row's recipient moved after the opponent's latest move in its stretch: the other player's row of
         * the same game holds the recipient's own latest move time (two-player games). */
        internal fun answered(): org.jooq.Condition {
            val other = SUMMARY.`as`("answer")
            return DSL.exists(DSL.selectOne().from(other).where(other.GAME_ID.eq(SUMMARY.GAME_ID)).and(other.KIND.eq("GAME"))
                .and(other.ACCOUNT_ID.ne(SUMMARY.ACCOUNT_ID)).and(other.REMINDER_MOVE_AT.gt(SUMMARY.REMINDER_MOVE_AT)))
        }

        /** Sends the one waiting reminder of each unanswered stretch: the opponent moved, the recipient has not
         * moved since, and the opponent's last move or input is [REMINDER_SECONDS] old. A recipient in the game at
         * that mark (opening without moving doesn't count as an answer) delays it until that long after leaving. */
        fun remindDue(sql: DSLContext, now: OffsetDateTime) {
            val rows = sql.select(SUMMARY.ACCOUNT_ID, SUMMARY.GAME_ID, SUMMARY.IDENTITY, SUMMARY.REMINDER_MOVE_AT,
                GAME.INVITER_ID, GAME.RECIPIENT_ID).from(SUMMARY).join(GAME).on(GAME.ID.eq(SUMMARY.GAME_ID))
                .where(SUMMARY.KIND.eq("GAME")).and(SUMMARY.REMINDER_MOVE_AT.le(now.minusSeconds(REMINDER_SECONDS)))
                .and(SUMMARY.REMINDER_ACTIVITY_ID.isNull).and(GAME.LIFECYCLE.eq("ACTIVE")).andNot(answered())
                .orderBy(SUMMARY.REMINDER_MOVE_AT).limit(100).fetch()
            for (row in rows) {
                val owner = row[SUMMARY.ACCOUNT_ID]!!; val game = row[SUMMARY.GAME_ID]!!
                val opponent = if (owner == row[GAME.INVITER_ID]) row[GAME.RECIPIENT_ID]!! else row[GAME.INVITER_ID]!!
                fun latest(field: org.jooq.TableField<*, OffsetDateTime?>, account: UUID) = sql.select(DSL.max(field)).from(PRESENCE)
                    .where(PRESENCE.GAME_ID.eq(game)).and(PRESENCE.ACCOUNT_ID.eq(account)).fetchOne(0, OffsetDateTime::class.java)?.toInstant()
                val moved = row[SUMMARY.REMINDER_MOVE_AT]!!.toInstant()
                val mark = maxOf(moved, latest(PRESENCE.INPUT_AT, opponent) ?: moved).plusSeconds(REMINDER_SECONDS)
                if (mark.isAfter(now.toInstant())) continue
                if (ActivityTiming.present(ActivityTiming.leases(sql, game, owner), now)) continue
                val seen = latest(PRESENCE.HEARTBEAT_AT, owner)
                if (seen != null && seen.isAfter(mark) && seen.plusSeconds(REMINDER_SECONDS).isAfter(now.toInstant())) continue
                // Same lock order as record(): cursor, then summary, then push. The summary claim is fenced on the
                // observed stretch, so a concurrent move by either player wins; a skipped ID is only a gap.
                val id = nextActivityIds(sql, owner, 1)
                if (sql.update(SUMMARY).set(SUMMARY.REMINDER_ACTIVITY_ID, id).where(SUMMARY.ACCOUNT_ID.eq(owner))
                    .and(SUMMARY.GAME_ID.eq(game)).and(SUMMARY.KIND.eq("GAME")).and(SUMMARY.REMINDER_ACTIVITY_ID.isNull)
                    .and(SUMMARY.REMINDER_MOVE_AT.eq(row[SUMMARY.REMINDER_MOVE_AT])).andNot(answered()).execute() == 1)
                    queuePush(sql, owner, ActivityKind.GAME, game, listOf(id to row[SUMMARY.IDENTITY]!!), now)
            }
        }

        /** The pending push of each of [owner]'s installations as queuing [events] (activity id, identity) one by one
         * leaves it. */
        private fun queuePush(sql: DSLContext, owner: UUID, kind: ActivityKind, game: UUID, events: List<Pair<Long, UUID>>, time: OffsetDateTime) {
            val installs = sql.select(INSTALL.GENERATION).from(INSTALL).join(SESSION).on(SESSION.ID.eq(INSTALL.SESSION_ID))
                .join(ACCOUNT).on(ACCOUNT.ID.eq(INSTALL.ACCOUNT_ID)).where(INSTALL.ACCOUNT_ID.eq(owner))
                .and(INSTALL.PUSH_TOKEN.isNotNull).and(SESSION.REVOKED_AT.isNull).and(SESSION.AUTH_EPOCH.eq(ACCOUNT.AUTH_EPOCH))
                .fetch(INSTALL.GENERATION)
            for (generation in installs) {
                val pending = sql.selectFrom(PUSH).where(PUSH.GENERATION.eq(generation)).and(PUSH.GAME_ID.eq(game))
                    .and(PUSH.KIND.eq(kind.name)).forUpdate().fetchOne()
                // What each one-by-one upsert would have found and left: (identity, first, due, attempts)
                var row = pending?.let { Triple(it.identity!!, it.firstAt!!, it.dueAt!!) to it.attempts!! }
                for ((_, identity) in events) {
                    val same = row?.first?.first == identity
                    val first = if (same) row!!.first.second else time
                    val retrying = same && row!!.second > 0
                    val due = if (retrying) maxOf(time, row!!.first.third) else time
                    row = Triple(identity, first, due) to if (retrying) row!!.second else 0
                }
                val (id, identity) = events.last()
                val (first, due) = row!!.first.second to row.first.third
                sql.insertInto(PUSH).set(PUSH.GENERATION, generation).set(PUSH.GAME_ID, game).set(PUSH.KIND, kind.name)
                    .set(PUSH.ACTIVITY_ID, id).set(PUSH.IDENTITY, identity).set(PUSH.FIRST_AT, first).set(PUSH.DUE_AT, due)
                    .set(PUSH.EXPIRES_AT, time.plusHours(24)).onConflict(PUSH.GENERATION, PUSH.GAME_ID, PUSH.KIND).doUpdate()
                    .set(PUSH.ACTIVITY_ID, id).set(PUSH.IDENTITY, identity).set(PUSH.FIRST_AT, first).set(PUSH.DUE_AT, due)
                    .set(PUSH.EXPIRES_AT, time.plusHours(24)).set(PUSH.ATTEMPTS, row.second).execute()
            }
        }
    }
}
