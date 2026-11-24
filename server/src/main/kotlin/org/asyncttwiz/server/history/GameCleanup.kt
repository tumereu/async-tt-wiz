package org.asyncttwiz.server.history

import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.asyncttwiz.protocol.ActivityKind
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.activity.ActivityService
import org.asyncttwiz.server.database.OwnershipFailure
import org.asyncttwiz.server.generated.tables.Game.GAME
import org.asyncttwiz.server.generated.tables.GameChat.GAME_CHAT
import org.asyncttwiz.server.generated.tables.GameInbox.GAME_INBOX
import org.asyncttwiz.server.generated.tables.GameOutbox.GAME_OUTBOX
import org.asyncttwiz.server.generated.tables.GamePlay.GAME_PLAY
import org.asyncttwiz.server.generated.tables.Pod.POD
import org.asyncttwiz.server.generated.tables.PodChat.POD_CHAT
import org.asyncttwiz.server.generated.tables.PodMember.POD_MEMBER
import org.asyncttwiz.server.generated.tables.RequestIdentity.REQUEST_IDENTITY
import org.asyncttwiz.server.lobby.LobbyService
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

/**
 * The automatic cleanup (game history spec §4), run every hour: deletes old games and pods with everything that belongs
 * to them, never the history records; and nightly the old answered game requests.
 *
 * - A finished game (back in its lobby after a match) whose last match ended [FINISHED] ago or more, with no activity since,
 *   is deleted (§4.1).
 * - Any other game (an invitation, a lobby, a match in play; a game already cancelled or deleted, whose rows were kept) with
 *   no activity for [INACTIVE] or more is deleted (§4.2); a match in play leaves an Unfinished record (§1.3).
 * - A pod with no activity in the pod or its games for [INACTIVE] or more is deleted (§4.3); games started from it go by
 *   the game rules.
 * - [WARNING] before that, at day 11 of 14, both players of a game in play and every member of a pod get one warning
 *   (§4.5); any activity resets the clock and allows another warning later.
 * - LOB-261 (owner, 2026-11-19): nightly ([runRequests]), a game request answered [REQUESTS] ago or more is deleted with its
 *   outbox notice and its request identity: a device resends a request only until it learns the answer, within one run of the app.
 *
 * Activity (§4.4) is a game's `activity_at` (each change by a player: [org.asyncttwiz.server.game.GameService]) and a pod's
 * `updated_at` (each change by a member saves the pod), its chat's newest message or its games' activity, whichever is latest.
 */
class GameCleanup(private val runtime: ServiceRuntime, private val clock: Clock = Clock.systemUTC()) : AutoCloseable {
    private val scheduler = Executors.newSingleThreadScheduledExecutor { Thread(it, "game-cleanup").apply { isDaemon = true } }
    private val log = org.slf4j.LoggerFactory.getLogger(GameCleanup::class.java)

    /** What one run did: the games and pods warned and deleted. */
    data class Run(val warnedGames: Int = 0, val warnedPods: Int = 0, val deletedGames: Int = 0, val deletedPods: Int = 0,
        val unfinished: Int = 0)

    /** Every hour, the first ten minutes after the start (a restart never deletes at once); the answered game requests
     * nightly at [NIGHTLY_AT] (LOB-261). */
    fun start() {
        scheduler.scheduleWithFixedDelay({
            try { if (runtime.admitting()) run().takeIf { it != Run() }?.let { log.info("Cleanup {}", it) } }
            catch (_: OwnershipFailure) { /* Ordinary ownership recovery: the next run catches up. */ }
            catch (failure: Exception) { log.error("Cleanup failed failureType={}", failure.javaClass.simpleName) }
        }, 10, 60, TimeUnit.MINUTES)
        val now = clock.instant().atOffset(ZoneOffset.UTC)
        val tonight = now.with(NIGHTLY_AT).let { if (it.isAfter(now)) it else it.plusDays(1) }
        scheduler.scheduleAtFixedRate({
            try { if (runtime.admitting()) log.info("Nightly cleanup deletedRequests={}", runRequests()) }
            catch (_: OwnershipFailure) { /* Ordinary ownership recovery: the next night catches up. */ }
            catch (failure: Exception) { log.error("Nightly cleanup failed failureType={}", failure.javaClass.simpleName) }
        }, Duration.between(now, tonight).toMinutes(), Duration.ofDays(1).toMinutes(), TimeUnit.MINUTES)
    }

    fun available() = !scheduler.isShutdown

    override fun close() {
        scheduler.shutdownNow()
        try { scheduler.awaitTermination(5, TimeUnit.SECONDS) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
    }

    private fun <T> transaction(work: (DSLContext) -> T): T = runtime.transaction { connection -> work(DSL.using(connection, SQLDialect.POSTGRES)) }

    /** One run at the clock's time: the warnings first, then the deletions, in batches until nothing is left. */
    fun run(): Run {
        val now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
        var result = Run()
        while (true) { val n = transaction { warnGames(it, now) }; if (n == 0) break; result = result.copy(warnedGames = result.warnedGames + n) }
        while (true) { val n = transaction { warnPods(it, now) }; if (n == 0) break; result = result.copy(warnedPods = result.warnedPods + n) }
        while (true) {
            val (deleted, unfinished) = transaction { deleteGames(it, now) }
            if (deleted == 0) break
            result = result.copy(deletedGames = result.deletedGames + deleted, unfinished = result.unfinished + unfinished)
        }
        while (true) { val n = transaction { deletePods(it, now) }; if (n == 0) break; result = result.copy(deletedPods = result.deletedPods + n) }
        return result
    }

    /** LOB-261: the nightly run at the clock's time: the answered game requests [REQUESTS] old or more deleted, in batches
     * until nothing is left. Returns how many were deleted. */
    fun runRequests(): Int {
        val now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
        var deleted = 0
        while (true) { val n = transaction { deleteRequests(it, now) }; if (n == 0) break; deleted += n }
        return deleted
    }

    /** Not warned since its latest activity (a warning, then activity, then 11 days more: warned again). */
    private fun unwarned(warned: org.jooq.Field<OffsetDateTime?>, activity: org.jooq.Field<OffsetDateTime?>) =
        warned.isNull.or(warned.lt(activity))

    /** §4.5: games in play at day 11 of 14 (not yet due), not warned since their latest activity. */
    private fun warnGames(sql: DSLContext, now: OffsetDateTime): Int {
        val games = sql.selectFrom(GAME).where(GAME.LIFECYCLE.eq("ACTIVE"))
            .and(GAME.ACTIVITY_AT.le(now.minus(WARNING_AT))).and(GAME.ACTIVITY_AT.gt(now.minus(INACTIVE)))
            .and(unwarned(GAME.REMOVAL_WARNED_AT, GAME.ACTIVITY_AT))
            .orderBy(GAME.ID).limit(BATCH).forUpdate().skipLocked().fetch()
        for (game in games) {
            for (player in listOf(game.inviterId, game.recipientId))
                ActivityService.record(sql, player, ActivityKind.GAME_REMOVAL, game.id, UUID.randomUUID(), time = now)
            sql.update(GAME).set(GAME.REMOVAL_WARNED_AT, now).where(GAME.ID.eq(game.id)).execute()
        }
        return games.size
    }

    /** A pod's activity: its own (every change by a member saves it), its chat's (LOB-133) or its games', whichever is latest. */
    private val podActivity: org.jooq.Field<OffsetDateTime?> = DSL.greatest(POD.UPDATED_AT,
        DSL.field(DSL.select(DSL.max(GAME.ACTIVITY_AT)).from(GAME).where(GAME.POD_ID.eq(POD.ID))),
        DSL.field(DSL.select(DSL.max(POD_CHAT.CREATED_AT)).from(POD_CHAT).where(POD_CHAT.POD_ID.eq(POD.ID))))

    /** §4.5: pods at day 11 of 14, not warned since their latest activity. */
    private fun warnPods(sql: DSLContext, now: OffsetDateTime): Int {
        val pods = sql.select(POD.ID).from(POD).where(podActivity.le(now.minus(WARNING_AT))).and(podActivity.gt(now.minus(INACTIVE)))
            .and(unwarned(POD.REMOVAL_WARNED_AT, podActivity))
            .orderBy(POD.ID).limit(BATCH).forUpdate().of(POD).skipLocked().fetch(POD.ID)
        for (pod in pods) {
            for (member in sql.select(POD_MEMBER.ACCOUNT_ID).from(POD_MEMBER).where(POD_MEMBER.POD_ID.eq(pod)).fetch(POD_MEMBER.ACCOUNT_ID))
                ActivityService.record(sql, member!!, ActivityKind.POD_REMOVAL, pod!!, UUID.randomUUID(), time = now)
            sql.update(POD).set(POD.REMOVAL_WARNED_AT, now).where(POD.ID.eq(pod)).execute()
        }
        return pods.size
    }

    /** §4.1, 4.2: the games due now, deleted; a match in play first leaves its Unfinished record. Returns how many were
     * deleted and how many of them were in play. */
    private fun deleteGames(sql: DSLContext, now: OffsetDateTime): Pair<Int, Int> {
        val finished = GAME.LIFECYCLE.eq("LOBBY").and(GAME.LAST_MATCH_END_AT.le(now.minus(FINISHED)))
            .and(GAME.ACTIVITY_AT.le(GAME.LAST_MATCH_END_AT))
        val inactive = GAME.ACTIVITY_AT.le(now.minus(INACTIVE))
        val games = sql.selectFrom(GAME).where(finished.or(inactive)).orderBy(GAME.ID).limit(BATCH).forUpdate().skipLocked().fetch()
        if (games.isEmpty()) return 0 to 0
        val lobby = LobbyService(runtime)
        val active = games.filter { it.lifecycle == "ACTIVE" }
        for (game in active) MatchRecords.unfinished(sql, lobby.summary(sql, game.id), now)
        GameRemoval.delete(sql, games.map { it.id })
        return games.size to active.size
    }

    /** §4.3: the pods due now, deleted with their members, invitations, draft, decks and notes (cascade) and notices. */
    private fun deletePods(sql: DSLContext, now: OffsetDateTime): Int {
        val pods = sql.select(POD.ID).from(POD).where(podActivity.le(now.minus(INACTIVE)))
            .orderBy(POD.ID).limit(BATCH).forUpdate().of(POD).skipLocked().fetch(POD.ID).map { it!! }
        if (pods.isNotEmpty()) GameRemoval.deletePods(sql, pods)
        return pods.size
    }

    /**
     * LOB-261: a batch of the game requests answered [REQUESTS] ago or more, deleted, each with its outbox notice and its
     * request identity, so a status read of one finds it unknown, as one never sent. Never a request still waiting for its
     * answer, one whose answer still waits to be sent (its outbox notice unpublished), or one other rows name: a chat
     * message's (its id is its request's), a play's start or end. Returns how many were deleted.
     */
    private fun deleteRequests(sql: DSLContext, now: OffsetDateTime): Int {
        val requests = sql.select(GAME_INBOX.REQUEST_ID).from(GAME_INBOX)
            .where(GAME_INBOX.ANSWERED_AT.le(now.minus(REQUESTS)))
            .andNotExists(sql.selectOne().from(GAME_OUTBOX).where(GAME_OUTBOX.REQUEST_ID.eq(GAME_INBOX.REQUEST_ID))
                .and(GAME_OUTBOX.PUBLISHED.eq(false)))
            .andNotExists(sql.selectOne().from(GAME_CHAT).where(GAME_CHAT.ID.eq(GAME_INBOX.REQUEST_ID)))
            .andNotExists(sql.selectOne().from(GAME_PLAY).where(GAME_PLAY.START_REQUEST_ID.eq(GAME_INBOX.REQUEST_ID)
                .or(GAME_PLAY.ENDED_REQUEST_ID.eq(GAME_INBOX.REQUEST_ID))))
            .orderBy(GAME_INBOX.ANSWERED_AT).limit(REQUEST_BATCH).forUpdate().of(GAME_INBOX).skipLocked()
            .fetch(GAME_INBOX.REQUEST_ID).map { it!! }
        if (requests.isEmpty()) return 0
        sql.deleteFrom(GAME_OUTBOX).where(GAME_OUTBOX.REQUEST_ID.`in`(requests)).execute()
        sql.deleteFrom(GAME_INBOX).where(GAME_INBOX.REQUEST_ID.`in`(requests)).execute()
        sql.deleteFrom(REQUEST_IDENTITY).where(REQUEST_IDENTITY.ID.`in`(requests)).and(REQUEST_IDENTITY.KIND.eq("gameCommand")).execute()
        return requests.size
    }

    companion object {
        /** LOB-261: an answered game request is deleted this long after its answer. */
        val REQUESTS: Duration = Duration.ofDays(30)
        /** LOB-261: when the nightly run deletes them, in UTC (03:00 in Finland in winter, 04:00 in summer). */
        val NIGHTLY_AT: java.time.LocalTime = java.time.LocalTime.of(1, 0)
        private const val REQUEST_BATCH = 1000
        /** §4.1: a finished game goes this long after its last match ended, without activity since. */
        val FINISHED: Duration = Duration.ofDays(7)
        /** §4.2, 4.3: any other game, and a pod, goes after this long without activity. */
        val INACTIVE: Duration = Duration.ofDays(14)
        /** §4.5: the warning comes at day 11 of 14 (3 days before). */
        val WARNING_AT: Duration = Duration.ofDays(11)
        private const val BATCH = 100

    }
}

/** Deleting games and pods with every row of theirs (as V63 did for games), the history records excepted. */
internal object GameRemoval {
    /** [games] (locked by the caller) with everything that names them or their plays. A games list read "changed since"
     * learns of each from its tombstone in game_removed. */
    fun delete(sql: DSLContext, games: List<UUID>) {
        val ids = DSL.`val`(games.toTypedArray())
        fun statement(text: String) = sql.execute(text.replace("ANY(?)", "ANY(?::uuid[])"), ids)
        // None of the game/play FKs cascade. The current-play cycle is broken first; the invitation cycle is DEFERRABLE
        // INITIALLY DEFERRED and closes when its game is deleted.
        statement("UPDATE game SET current_play = NULL WHERE id = ANY(?)")
        for (table in listOf("activity_push", "activity_summary", "account_activity", "activity_presence", "game_chat_read_cursor", "game_chat"))
            statement("DELETE FROM $table WHERE game_id = ANY(?)")
        for (table in listOf("engine_async_round", "engine_seat", "engine_checkpoint", "engine_event", "engine_play"))
            statement("DELETE FROM $table WHERE play_id IN (SELECT id FROM game_play WHERE game_id = ANY(?))")
        for (table in listOf("game_play", "game_outbox", "game_revision", "game_stream", "game_inbox", "player_preparation",
                "game_definition", "invitation"))
            statement("DELETE FROM $table WHERE game_id = ANY(?)")
        statement("INSERT INTO game_removed (game_id, inviter_id, recipient_id, change_xid) " +
            "SELECT id, inviter_id, recipient_id, pg_current_xact_id()::text::bigint FROM game WHERE id = ANY(?) " +
            "ON CONFLICT (game_id) DO NOTHING")
        statement("DELETE FROM game WHERE id = ANY(?)")
    }

    /** [pods] (locked by the caller): the pod rows (members, invitations, draft marks and deck notes cascade) and their
     * notices. Games started from them stay (DRF-042); draft decks kept in the members' deck lists stay too. */
    fun deletePods(sql: DSLContext, pods: List<UUID>) {
        val ids = DSL.`val`(pods.toTypedArray())
        for (table in listOf("activity_push", "activity_summary", "account_activity"))
            sql.execute("DELETE FROM $table WHERE game_id = ANY(?::uuid[]) AND left(kind, 4) = 'POD_'", ids)
        sql.execute("DELETE FROM pod WHERE id = ANY(?::uuid[])", ids)
    }
}
