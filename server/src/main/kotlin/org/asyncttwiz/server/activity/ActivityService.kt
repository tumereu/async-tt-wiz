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
import org.asyncttwiz.server.generated.tables.Game.GAME
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
        val savepoint = connection.setSavepoint()
        try { Result.success(work(DSL.using(connection, SQLDialect.POSTGRES))) }
        catch (failure: ActivityFailure) { connection.rollback(savepoint); Result.failure<T>(failure) }
    }.getOrThrow()
    private fun account(principal: AccountService.Principal) = UUID.fromString(principal.account.accountId)

    fun heartbeat(principal: AccountService.Principal, input: GamePresence) {
        if (!DeckValidation.uuid(input.gameId) || !DeckValidation.uuid(input.presenceId)) throw ActivityFailure(400, "invalid_request")
        transaction { sql ->
            val owner = account(principal); val game = UUID.fromString(input.gameId); val time = now()
            val row = sql.selectFrom(GAME).where(GAME.ID.eq(game)).forShare().fetchOne()
            if (row == null || owner !in listOf(row.inviterId, row.recipientId)) throw ActivityFailure(404, "game_unavailable")
            val presence = UUID.fromString(input.presenceId)
            val old = sql.selectFrom(PRESENCE).where(PRESENCE.GAME_ID.eq(game)).and(PRESENCE.ACCOUNT_ID.eq(owner))
                .and(PRESENCE.PRESENCE_ID.eq(presence)).fetchOne()
            val active = input.didSomething || old == null || old.leftGame == true || !old.heartbeatAt!!.plusSeconds(15).isAfter(time)
            sql.insertInto(PRESENCE).set(PRESENCE.GAME_ID, game).set(PRESENCE.ACCOUNT_ID, owner)
                .set(PRESENCE.SESSION_ID, principal.sessionId).set(PRESENCE.PRESENCE_ID, presence)
                .set(PRESENCE.HEARTBEAT_AT, time).set(PRESENCE.INPUT_AT, if (active) time else old!!.inputAt)
                .set(PRESENCE.LEFT_GAME, input.left).onConflict(PRESENCE.GAME_ID, PRESENCE.ACCOUNT_ID, PRESENCE.PRESENCE_ID)
                .doUpdate().set(PRESENCE.SESSION_ID, principal.sessionId).set(PRESENCE.HEARTBEAT_AT, time)
                .set(PRESENCE.INPUT_AT, if (active) time else old!!.inputAt).set(PRESENCE.LEFT_GAME, input.left).execute()
            if (!input.left) {
                sql.update(SUMMARY).set(SUMMARY.SEEN_THROUGH, SUMMARY.ACTIVITY_ID).set(SUMMARY.NEW_MESSAGE, false).where(SUMMARY.ACCOUNT_ID.eq(owner))
                    .and(SUMMARY.GAME_ID.eq(game)).and(SUMMARY.KIND.eq("GAME")).execute()
                sql.deleteFrom(PUSH).where(PUSH.GAME_ID.eq(game)).and(PUSH.KIND.eq("GAME"))
                    .and(PUSH.GENERATION.`in`(sql.select(INSTALL.GENERATION).from(INSTALL).where(INSTALL.ACCOUNT_ID.eq(owner)))).execute()
            }
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
        sql.deleteFrom(PRESENCE).where(PRESENCE.HEARTBEAT_AT.lt(now().minusDays(1))).execute()
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
        val entries = rows.take(100).map { row ->
            val type = ActivityKind.valueOf(row[kind]!!)
            val name = if (type.name.startsWith("POD_")) sql.select(POD.NAME).from(POD).where(POD.ID.eq(row[game])).fetchOne(POD.NAME) else null
            ActivityHint(row[activity].toString(), type, row[game].toString(), row[identity].toString(), row[author]?.toString(), resourceName = name)
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
                val gameRow = sql.selectFrom(GAME).where(GAME.ID.eq(row.gameId)).fetchOne() ?: return@mapNotNull null
                if (gameRow.lifecycle !in listOf("LOBBY", "ACTIVE")) return@mapNotNull null
                val authorId = row.authorId ?: return@mapNotNull null
                val recipient = ActivityTiming.leases(sql, row.gameId!!, owner)
                // A push already sent for this activity decided it was due; a later actor input must not hide it.
                val pushed = row.pushedThrough!! >= row.activityId!! && !ActivityTiming.present(recipient, now())
                if (!pushed && !ActivityTiming.due(ActivityTiming.leases(sql, row.gameId!!, authorId),
                        recipient, row.lastEventAt!!, row.yourMove!!, now())) return@mapNotNull null
                val opponent = sql.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(authorId)).fetchSingle()
                ActivityHint(row.activityId.toString(), ActivityKind.GAME, row.gameId.toString(), row.identity.toString(),
                    authorId.toString(), GameNoticeContent(gameRow.gameName!!, opponent.username!!,
                        row.lastEventAt.toString(), row.yourMove!!, row.newMessage!!))
            }.take(50).toList()
        // A sent reminder stays listed (client watermarks dedupe it) until the recipient moves.
        val reminders = sql.select(SUMMARY.fields().toList() + GAME.GAME_NAME + ACCOUNT.USERNAME).from(SUMMARY)
            .join(GAME).on(GAME.ID.eq(SUMMARY.GAME_ID)).join(ACCOUNT).on(ACCOUNT.ID.eq(SUMMARY.AUTHOR_ID))
            .where(SUMMARY.ACCOUNT_ID.eq(owner)).and(SUMMARY.KIND.eq("GAME")).and(SUMMARY.REMINDER_ACTIVITY_ID.le(high))
            .and(GAME.LIFECYCLE.eq("ACTIVE")).orderBy(SUMMARY.REMINDER_ACTIVITY_ID.desc()).limit(50).fetch().map { row ->
                ActivityHint(row[SUMMARY.REMINDER_ACTIVITY_ID].toString(), ActivityKind.GAME, row[SUMMARY.GAME_ID].toString(),
                    row[SUMMARY.IDENTITY].toString(), row[SUMMARY.AUTHOR_ID].toString(), GameNoticeContent(row[GAME.GAME_NAME]!!,
                        row[ACCOUNT.USERNAME]!!, row[SUMMARY.REMINDER_MOVE_AT].toString(), row[SUMMARY.YOUR_MOVE]!!, reminder = true))
            }
        val notices = (ready + reminders).sortedByDescending { ActivityWire.ordinal(it.activityId) }.take(50)
        ActivityPage(entries, next, floor.toString(), reset, more, entries.filter { it.kind != ActivityKind.GAME } + notices)
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
                        "exists (select 1 from jsonb_array_elements({0}->'grids') as board(value) " +
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
         * waiting reminder and ends the author's own unanswered stretch. */
        fun record(sql: DSLContext, owner: UUID, kind: ActivityKind, game: UUID, identity: UUID,
            author: UUID? = null, time: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
            yourMove: Boolean = false, newMessage: Boolean = false, move: Boolean = false) {
            if (author == owner) return
            val id = nextActivityId(sql, owner)
            sql.insertInto(EVENT).set(EVENT.ACCOUNT_ID, owner).set(EVENT.ACTIVITY_ID, id).set(EVENT.KIND, kind.name)
                .set(EVENT.GAME_ID, game).set(EVENT.IDENTITY, identity).set(EVENT.AUTHOR_ID, author).set(EVENT.CREATED_AT, time).execute()
            // Keep presence suppression atomic without another action/undo SQL statement.
            val present = if (kind == ActivityKind.GAME) ActivityTiming.present(game, owner, time) else DSL.falseCondition()
            val armed = move && kind == ActivityKind.GAME
            sql.insertInto(SUMMARY).set(SUMMARY.ACCOUNT_ID, owner).set(SUMMARY.GAME_ID, game).set(SUMMARY.KIND, kind.name)
                .set(SUMMARY.ACTIVITY_ID, id).set(SUMMARY.IDENTITY, identity).set(SUMMARY.AUTHOR_ID, author)
                .set(SUMMARY.LAST_EVENT_AT, time).set(SUMMARY.YOUR_MOVE, yourMove)
                .set(SUMMARY.SEEN_THROUGH, DSL.`when`(present, id).otherwise(0L))
                .set(SUMMARY.NEW_MESSAGE, DSL.`when`(present, false).otherwise(newMessage))
                .set(SUMMARY.REMINDER_MOVE_AT, if (armed) time else null)
                .onConflict(SUMMARY.ACCOUNT_ID, SUMMARY.GAME_ID, SUMMARY.KIND).doUpdate()
                .set(SUMMARY.ACTIVITY_ID, id).set(SUMMARY.IDENTITY, identity).set(SUMMARY.AUTHOR_ID, author)
                .set(SUMMARY.LAST_EVENT_AT, time).set(SUMMARY.YOUR_MOVE, yourMove)
                .set(SUMMARY.SEEN_THROUGH, DSL.greatest(SUMMARY.SEEN_THROUGH, DSL.excluded(SUMMARY.SEEN_THROUGH)))
                .set(SUMMARY.NEW_MESSAGE, DSL.`when`(DSL.excluded(SUMMARY.SEEN_THROUGH).eq(id), false)
                    .otherwise(DSL.field("{0} or {1}", Boolean::class.java, SUMMARY.NEW_MESSAGE, DSL.excluded(SUMMARY.NEW_MESSAGE))))
                .set(SUMMARY.REMINDER_MOVE_AT, if (armed) DSL.excluded(SUMMARY.REMINDER_MOVE_AT) else SUMMARY.REMINDER_MOVE_AT).execute()
            if (armed && author != null) sql.update(SUMMARY).setNull(SUMMARY.REMINDER_MOVE_AT).setNull(SUMMARY.REMINDER_ACTIVITY_ID)
                .where(SUMMARY.ACCOUNT_ID.eq(author)).and(SUMMARY.GAME_ID.eq(game)).and(SUMMARY.KIND.eq("GAME")).execute()
            queuePush(sql, owner, kind, game, id, identity, time)
        }

        private fun nextActivityId(sql: DSLContext, owner: UUID): Long = sql.insertInto(CURSOR).set(CURSOR.ACCOUNT_ID, owner)
            .set(CURSOR.HIGH_WATER, 1L).onConflict(CURSOR.ACCOUNT_ID).doUpdate().set(CURSOR.HIGH_WATER, CURSOR.HIGH_WATER.plus(1L))
            .returning(CURSOR.HIGH_WATER).fetchSingle().highWater!!

        /** A new play starts without either player's waiting reminder from an earlier play. */
        fun resetReminders(sql: DSLContext, game: UUID) {
            sql.update(SUMMARY).setNull(SUMMARY.REMINDER_MOVE_AT).setNull(SUMMARY.REMINDER_ACTIVITY_ID)
                .where(SUMMARY.GAME_ID.eq(game)).and(SUMMARY.KIND.eq("GAME")).execute()
        }

        const val REMINDER_SECONDS = 300L

        /** Sends the one waiting reminder of each unanswered stretch: the opponent moved, the recipient has not
         * moved since, and the opponent's last move or input is [REMINDER_SECONDS] old. A recipient in the game at
         * that mark (opening without moving doesn't count as an answer) delays it until that long after leaving. */
        fun remindDue(sql: DSLContext, now: OffsetDateTime) {
            val rows = sql.select(SUMMARY.ACCOUNT_ID, SUMMARY.GAME_ID, SUMMARY.IDENTITY, SUMMARY.REMINDER_MOVE_AT,
                GAME.INVITER_ID, GAME.RECIPIENT_ID).from(SUMMARY).join(GAME).on(GAME.ID.eq(SUMMARY.GAME_ID))
                .where(SUMMARY.KIND.eq("GAME")).and(SUMMARY.REMINDER_MOVE_AT.le(now.minusSeconds(REMINDER_SECONDS)))
                .and(SUMMARY.REMINDER_ACTIVITY_ID.isNull).and(GAME.LIFECYCLE.eq("ACTIVE"))
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
                val id = nextActivityId(sql, owner)
                if (sql.update(SUMMARY).set(SUMMARY.REMINDER_ACTIVITY_ID, id).where(SUMMARY.ACCOUNT_ID.eq(owner))
                    .and(SUMMARY.GAME_ID.eq(game)).and(SUMMARY.KIND.eq("GAME")).and(SUMMARY.REMINDER_ACTIVITY_ID.isNull)
                    .and(SUMMARY.REMINDER_MOVE_AT.eq(row[SUMMARY.REMINDER_MOVE_AT])).execute() == 1)
                    queuePush(sql, owner, ActivityKind.GAME, game, id, row[SUMMARY.IDENTITY]!!, now)
            }
        }

        private fun queuePush(sql: DSLContext, owner: UUID, kind: ActivityKind, game: UUID, id: Long, identity: UUID, time: OffsetDateTime) {
            val installs = sql.select(INSTALL.GENERATION).from(INSTALL).join(SESSION).on(SESSION.ID.eq(INSTALL.SESSION_ID))
                .join(ACCOUNT).on(ACCOUNT.ID.eq(INSTALL.ACCOUNT_ID)).where(INSTALL.ACCOUNT_ID.eq(owner))
                .and(INSTALL.PUSH_TOKEN.isNotNull).and(SESSION.REVOKED_AT.isNull).and(SESSION.AUTH_EPOCH.eq(ACCOUNT.AUTH_EPOCH))
                .fetch(INSTALL.GENERATION)
            for (generation in installs) {
                val pending = sql.selectFrom(PUSH).where(PUSH.GENERATION.eq(generation)).and(PUSH.GAME_ID.eq(game))
                    .and(PUSH.KIND.eq(kind.name)).forUpdate().fetchOne()
                val first = if (pending?.identity == identity) pending.firstAt!! else time
                val due = time // Game timing is evaluated against live presence by the scheduler.
                val retrying = pending?.identity == identity && pending.attempts!! > 0
                val nextDue = if (retrying) maxOf(due, pending!!.dueAt!!) else due
                sql.insertInto(PUSH).set(PUSH.GENERATION, generation).set(PUSH.GAME_ID, game).set(PUSH.KIND, kind.name)
                    .set(PUSH.ACTIVITY_ID, id).set(PUSH.IDENTITY, identity).set(PUSH.FIRST_AT, first).set(PUSH.DUE_AT, due)
                    .set(PUSH.EXPIRES_AT, time.plusHours(24)).onConflict(PUSH.GENERATION, PUSH.GAME_ID, PUSH.KIND).doUpdate()
                    .set(PUSH.ACTIVITY_ID, id).set(PUSH.IDENTITY, identity).set(PUSH.FIRST_AT, first).set(PUSH.DUE_AT, nextDue)
                    .set(PUSH.EXPIRES_AT, time.plusHours(24)).set(PUSH.ATTEMPTS, if (retrying) pending!!.attempts else 0).execute()
            }
        }
    }
}
