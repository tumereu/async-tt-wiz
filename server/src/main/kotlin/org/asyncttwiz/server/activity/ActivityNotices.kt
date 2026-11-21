package org.asyncttwiz.server.activity

import java.time.OffsetDateTime
import java.util.UUID
import org.asyncttwiz.protocol.ActivityHint
import org.asyncttwiz.protocol.ActivityKind
import org.asyncttwiz.server.generated.tables.Account.ACCOUNT
import org.asyncttwiz.server.generated.tables.AccountSession.ACCOUNT_SESSION as SESSION
import org.asyncttwiz.server.generated.tables.ActivityInstallation.ACTIVITY_INSTALLATION as INSTALL
import org.asyncttwiz.server.generated.tables.ActivityNotice.ACTIVITY_NOTICE as NOTICE
import org.asyncttwiz.server.generated.tables.ActivityNoticeBrowser.ACTIVITY_NOTICE_BROWSER as BROWSER
import org.asyncttwiz.server.generated.tables.ActivityPresence.ACTIVITY_PRESENCE as PRESENCE
import org.asyncttwiz.server.generated.tables.ActivityPush.ACTIVITY_PUSH as PUSH
import org.asyncttwiz.server.generated.tables.ActivitySummary.ACTIVITY_SUMMARY as SUMMARY
import org.asyncttwiz.server.generated.tables.EngineSeat.ENGINE_SEAT
import org.asyncttwiz.server.generated.tables.Game.GAME
import org.asyncttwiz.server.generated.tables.Invitation.INVITATION
import org.asyncttwiz.server.generated.tables.Pod.POD
import org.asyncttwiz.server.generated.tables.PodInvitation.POD_INVITATION
import org.asyncttwiz.server.generated.tables.PodMember.POD_MEMBER
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory

/**
 * LOB-078 (owner, 2026-11-19): a game's or pod's notification clears on every device of its player once nothing waits for
 * them, however they dealt with it. The server decides: [open] tells which of an account's notification keys still wait;
 * [shown] remembers the keys whose notice may be showing on the account's devices; [sweep] finds those that no longer wait
 * and queues a clear for each of the account's installations: an Android one gets the usual FCM hint, whose feed refresh
 * then removes the notice ([org.asyncttwiz.protocol.ActivityPage.openNotices], also read on every poll without FCM); a
 * browser that was sent the notice gets a Web Push clear naming its tag.
 */
object ActivityNotices {
    private val log = LoggerFactory.getLogger(ActivityNotices::class.java)

    /** A clear's push kind ([PUSH] row) per key prefix; its game_id and identity hold the key's id. */
    private val clearKinds = mapOf("game" to "CLEAR_GAME", "invite" to "CLEAR_INVITE", "pod" to "CLEAR_POD", "pod-invite" to "CLEAR_POD_INVITE")
    const val CLEAR_PREFIX = "CLEAR_"

    /** The apps' notification key of a notice of [kind] about [resource] with [identity] (ActivityHint.notificationKey); a
     * pod challenge ([pod]: its pod) is its pod's one notification (DRF-135, DRF-122). */
    fun key(kind: ActivityKind, resource: String, identity: String, pod: String? = null): String = when (kind) {
        ActivityKind.GAME, ActivityKind.GAME_REMOVAL -> "game:$resource"
        ActivityKind.INVITATION -> if (pod != null) "pod:$pod" else "invite:$identity"
        ActivityKind.POD_INVITATION -> "pod-invite:$resource"
        else -> "pod:$resource"
    }

    /** [hint]'s notification key ([key]). */
    fun key(hint: ActivityHint): String = key(hint.kind, hint.gameId, hint.identity, hint.podId)

    /** LOB-243 (owner, 2026-11-20): the pod notices that tell something rather than wait for the player: seen once the player
     * has opened the pod on any device ([ActivityService.podSeen]; a destroyed pod's on the Pods list). */
    val POD_NEWS = listOf(ActivityKind.POD_DECKS_READY, ActivityKind.POD_DRAFT_CANCELLED, ActivityKind.POD_DESTROYED,
        ActivityKind.POD_AUTO_START_OFF, ActivityKind.POD_REMOVAL).map { it.name }

    /** The key a clear push ([PUSH] kind [kind], its id [id]) clears; null for any other push. */
    fun clearedKey(kind: String, id: UUID): String? = clearKinds.entries.firstOrNull { it.value == kind }?.let { "${it.key}:$id" }

    /** The kind of notice whose browser switch (WEB-051) a clear push of [kind] follows. */
    fun clearedKind(kind: String): ActivityKind = when (kind) {
        "CLEAR_INVITE" -> ActivityKind.INVITATION
        "CLEAR_POD_INVITE" -> ActivityKind.POD_INVITATION
        "CLEAR_POD" -> ActivityKind.POD_YOUR_PICK
        else -> ActivityKind.GAME
    }

    /**
     * The notification keys of [owner] that still wait for them, so that a notice of them may stay:
     * a game with an event they haven't seen on any device, an unanswered waiting reminder, or their move (or an engine
     * stop waiting for them) in a game in play; a cleanup warning they haven't opened the game since; a pending invitation
     * to them (a pod challenge's key is its pod's); a pod whose latest notice still waits (their pick: packs in their queue;
     * their build: not submitted; the other pod notices tell something: until the player opened the pod on any device, or
     * for a destroyed pod the Pods list, [ActivityService.podSeen]); a pending pod invitation.
     */
    fun open(sql: DSLContext, owner: UUID): Set<String> {
        val seat = DSL.exists(DSL.selectOne().from(ENGINE_SEAT).where(ENGINE_SEAT.PLAY_ID.eq(GAME.CURRENT_PLAY))
            .and(ENGINE_SEAT.ACCOUNT_ID.eq(owner)).and(ENGINE_SEAT.WAITED.isTrue))
        val gameOpen = SUMMARY.KIND.eq("GAME").and(SUMMARY.ACTIVITY_ID.gt(SUMMARY.SEEN_THROUGH)
            .or(SUMMARY.REMINDER_ACTIVITY_ID.isNotNull.andNot(ActivityService.answered()))
            .or(GAME.LIFECYCLE.eq("ACTIVE").and(SUMMARY.YOUR_MOVE.isTrue.or(seat))))
        val removalOpen = SUMMARY.KIND.eq("GAME_REMOVAL").and(GAME.LIFECYCLE.`in`("LOBBY", "ACTIVE"))
            .andNotExists(DSL.selectOne().from(PRESENCE).where(PRESENCE.GAME_ID.eq(SUMMARY.GAME_ID))
                .and(PRESENCE.ACCOUNT_ID.eq(owner)).and(PRESENCE.HEARTBEAT_AT.gt(SUMMARY.LAST_EVENT_AT)))
        val games = DSL.select(DSL.inline("game:").concat(SUMMARY.GAME_ID.cast(String::class.java))).from(SUMMARY)
            .join(GAME).on(GAME.ID.eq(SUMMARY.GAME_ID)).where(SUMMARY.ACCOUNT_ID.eq(owner)).and(gameOpen.or(removalOpen))
        // DRF-135, DRF-122: a pod challenge is its pod's one notification
        val inviteKey = DSL.`when`(GAME.POD_ID.isNull, DSL.inline("invite:").concat(INVITATION.ID.cast(String::class.java)))
            .otherwise(DSL.inline("pod:").concat(GAME.POD_ID.cast(String::class.java)))
        val invitations = DSL.select(inviteKey).from(GAME)
            .join(INVITATION).on(INVITATION.ID.eq(GAME.CURRENT_INVITATION))
            .where(GAME.RECIPIENT_ID.eq(owner)).and(GAME.LIFECYCLE.eq("LOBBY")).and(INVITATION.STATE.eq("PENDING"))
        // A pod's notification shows its latest notice: whether that one still waits (the other notices tell something)
        val podNotice = SUMMARY.KIND.like("POD\\_%").and(SUMMARY.KIND.notIn("POD_INVITATION", "POD_MESSAGE"))
        val newer = SUMMARY.`as`("newer")
        val unsubmitted = DSL.exists(DSL.selectOne().from(POD_MEMBER).where(POD_MEMBER.POD_ID.eq(SUMMARY.GAME_ID))
            .and(POD_MEMBER.ACCOUNT_ID.eq(owner)).and(POD_MEMBER.SUBMITTED_DECK.isNull))
        val pods = DSL.select(DSL.inline("pod:").concat(SUMMARY.GAME_ID.cast(String::class.java))).from(SUMMARY)
            .where(SUMMARY.ACCOUNT_ID.eq(owner)).and(podNotice)
            .andNotExists(DSL.selectOne().from(newer).where(newer.ACCOUNT_ID.eq(owner)).and(newer.GAME_ID.eq(SUMMARY.GAME_ID))
                .and(newer.KIND.like("POD\\_%")).and(newer.KIND.notIn("POD_INVITATION", "POD_MESSAGE"))
                .and(newer.ACTIVITY_ID.gt(SUMMARY.ACTIVITY_ID)))
            .and(ActivityService.podVisible(DSL.`val`(owner), SUMMARY.KIND, SUMMARY.GAME_ID, SUMMARY.IDENTITY))
            .and(SUMMARY.KIND.ne(ActivityKind.POD_DRAFT_COMPLETE.name).or(unsubmitted))
            // LOB-243: news waits until the pod was opened on any device
            .and(SUMMARY.KIND.notIn(POD_NEWS).or(SUMMARY.ACTIVITY_ID.gt(SUMMARY.SEEN_THROUGH)))
        val podInvitations = DSL.select(DSL.inline("pod-invite:").concat(POD_INVITATION.POD_ID.cast(String::class.java)))
            .from(POD_INVITATION).join(POD).on(POD.ID.eq(POD_INVITATION.POD_ID))
            .where(POD_INVITATION.ACCOUNT_ID.eq(owner)).and(POD_INVITATION.STATE.eq("PENDING")).and(POD.PHASE.eq("SETUP"))
        // One statement for all four (the feed's cost, RequestCostTest)
        return sql.fetch(games.unionAll(invitations).unionAll(pods).unionAll(podInvitations)).map { it.value1()!! }.toSet()
    }

    /** [owner]'s devices may now show the notices of [keys] (a feed page, the waiting check or a push told them). */
    fun shown(sql: DSLContext, owner: UUID, keys: Collection<String>, time: OffsetDateTime) {
        if (keys.isEmpty()) return
        var insert = sql.insertInto(NOTICE, NOTICE.ACCOUNT_ID, NOTICE.NOTICE_KEY, NOTICE.SHOWN_AT)
        for (key in keys.distinct()) insert = insert.values(owner, key, time)
        insert.onConflict(NOTICE.ACCOUNT_ID, NOTICE.NOTICE_KEY).doUpdate().set(NOTICE.SHOWN_AT, time).execute()
    }

    /** The browser [generation] was sent a notice of [key] (WEB-052): it is sent the clear too. */
    fun shownInBrowser(sql: DSLContext, generation: UUID, key: String) {
        sql.insertInto(BROWSER).set(BROWSER.GENERATION, generation).set(BROWSER.NOTICE_KEY, key).onConflictDoNothing().execute()
    }

    /**
     * Every remembered notice that no longer waits ([open]) is forgotten and cleared on its account's devices: a clear push
     * to each Android installation with a push token and to each browser that was sent it; a pending push of it is dropped.
     * Notices remembered for over [KEEP_DAYS] days are forgotten (no device keeps them showing that long unseen).
     */
    fun sweep(sql: DSLContext, time: OffsetDateTime) {
        sql.deleteFrom(NOTICE).where(NOTICE.SHOWN_AT.lt(time.minusDays(KEEP_DAYS))).execute()
        val remembered = sql.select(NOTICE.ACCOUNT_ID, NOTICE.NOTICE_KEY).from(NOTICE).orderBy(NOTICE.ACCOUNT_ID).limit(2000).fetch()
            .groupBy({ it.value1()!! }, { it.value2()!! })
        for ((owner, keys) in remembered) {
            val open = open(sql, owner)
            val settled = keys.filter { it !in open }
            if (settled.isEmpty()) continue
            sql.deleteFrom(NOTICE).where(NOTICE.ACCOUNT_ID.eq(owner)).and(NOTICE.NOTICE_KEY.`in`(settled)).execute()
            for (key in settled) clear(sql, owner, key, time)
        }
    }

    private fun clear(sql: DSLContext, owner: UUID, key: String, time: OffsetDateTime) {
        val prefix = key.substringBeforeLast(':')
        val id = runCatching { UUID.fromString(key.substringAfterLast(':')) }.getOrNull() ?: return
        val kind = clearKinds[prefix] ?: return
        val mine = INSTALL.ACCOUNT_ID.eq(owner)
        // The notice's own pending push would tell what no longer waits
        val own = when (prefix) {
            "game" -> PUSH.GAME_ID.eq(id).and(PUSH.KIND.`in`("GAME", "GAME_REMOVAL"))
            "invite" -> PUSH.IDENTITY.eq(id).and(PUSH.KIND.eq("INVITATION"))
            "pod-invite" -> PUSH.GAME_ID.eq(id).and(PUSH.KIND.eq("POD_INVITATION"))
            else -> PUSH.GAME_ID.eq(id).and(PUSH.KIND.like("POD\\_%")).and(PUSH.KIND.ne("POD_INVITATION"))
        }
        sql.deleteFrom(PUSH).where(own).and(PUSH.GENERATION.`in`(DSL.select(INSTALL.GENERATION).from(INSTALL).where(mine))).execute()
        val browsers = sql.select(BROWSER.GENERATION).from(BROWSER).join(INSTALL).on(INSTALL.GENERATION.eq(BROWSER.GENERATION))
            .where(mine).and(BROWSER.NOTICE_KEY.eq(key)).fetch(BROWSER.GENERATION).toSet()
        sql.deleteFrom(BROWSER).where(BROWSER.NOTICE_KEY.eq(key)).and(BROWSER.GENERATION.`in`(browsers)).execute()
        val targets = sql.select(INSTALL.GENERATION).from(INSTALL).join(SESSION).on(SESSION.ID.eq(INSTALL.SESSION_ID))
            .join(ACCOUNT).on(ACCOUNT.ID.eq(INSTALL.ACCOUNT_ID)).where(mine).and(SESSION.REVOKED_AT.isNull)
            .and(SESSION.AUTH_EPOCH.eq(ACCOUNT.AUTH_EPOCH))
            .and(INSTALL.PUSH_TOKEN.isNotNull.or(INSTALL.WEB_PUSH.isNotNull.and(INSTALL.GENERATION.`in`(browsers))))
            .fetch(INSTALL.GENERATION)
        for (generation in targets) sql.insertInto(PUSH).set(PUSH.GENERATION, generation).set(PUSH.GAME_ID, id).set(PUSH.KIND, kind)
            .set(PUSH.ACTIVITY_ID, 0L).set(PUSH.IDENTITY, id).set(PUSH.FIRST_AT, time).set(PUSH.DUE_AT, time)
            .set(PUSH.EXPIRES_AT, time.plusHours(24)).onConflict(PUSH.GENERATION, PUSH.GAME_ID, PUSH.KIND).doUpdate()
            .set(PUSH.DUE_AT, time).set(PUSH.ATTEMPTS, 0).set(PUSH.EXPIRES_AT, time.plusHours(24)).execute()
        log.info("Notice {} of account {} settled: nothing waits for it; clearing it on {} device(s)", key, owner, targets.size)
    }

    const val KEEP_DAYS = 30L
}
