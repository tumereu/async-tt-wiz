package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Pure delivery policy used by both polling and push. All callbacks share the owner's lock. */
data class ActivityOwner(val accountId: String, val binding: String)
data class ActivityWatermark(val activity: Long, val identity: String, val yourMove: Boolean? = null)
interface ActivityDeliveryStore {
    fun cursor(): String?
    fun watermark(key: String): ActivityWatermark?
    /** Must durably write cursor AND replacement identity before the OS call. */
    fun save(cursor: String, marks: Map<String, ActivityWatermark>): Boolean
}
data class ActivityNotice(val key: String, val hint: ActivityHint, val silent: Boolean)

/** Runtime stores persist pending windows with the fetch cursor. Immediate sinks remain supported. */
interface ActivityWindowStore : ActivityDeliveryStore {
    fun pending(): Map<String, PendingActivity>
    fun saveWindow(cursor: String, marks: Map<String, ActivityWatermark>, pending: Map<String, PendingActivity>): Boolean
}

class ActivityDelivery(private val store: ActivityDeliveryStore, private val post: (ActivityNotice) -> Unit) {
    private var now: () -> Long = System::currentTimeMillis
    constructor(store: ActivityDeliveryStore, now: () -> Long, post: (ActivityNotice) -> Unit) : this(store, post) {
        this.now = now
    }
    private val lock = Any()
    private var owner: ActivityOwner? = null
    private var screen = ActivityScreen.NONE
    private var foreground = false
    fun bind(next: ActivityOwner?) = synchronized(lock) { owner = next }
    fun display(game: String?, foreground: Boolean) = display(ActivityScreen(game = game), foreground)
    fun display(screen: ActivityScreen, foreground: Boolean) = synchronized(lock) {
        this.screen = screen; this.foreground = foreground
        val game = screen.game
        // Opening cancels the old notice, not its dedup watermark. A later new turn
        // must be allowed to alert even if the last dismissed notice said Your move.
        if (owner != null && foreground && game != null) {
            val key = "game:$game"; val previous = store.watermark(key); val cursor = store.cursor()
            if (previous?.yourMove == true && cursor != null) {
                val marks = mapOf(key to previous.copy(yourMove = false))
                (store as? ActivityWindowStore)?.let { it.saveWindow(cursor, marks, it.pending() - key) } ?: store.save(cursor, marks)
            }
        }
        Unit
    }
    fun cursor(expected: ActivityOwner): String? = synchronized(lock) { if (expected == owner) store.cursor() else null }
    fun current(expected: ActivityOwner) = synchronized(lock) { owner == expected }
    fun nextDue(expected: ActivityOwner): Long? = synchronized(lock) {
        if (owner != expected) null else (store as? ActivityWindowStore)?.pending()?.values?.minOfOrNull { it.dueAt }
    }
    fun drain(expected: ActivityOwner, allowed: (ActivityKind) -> Boolean): Boolean = synchronized(lock) {
        val windows = store as? ActivityWindowStore ?: return true
        applyWindow(expected, ActivityPage(emptyList(), store.cursor() ?: "0", "0", false, false), allowed, windows)
    }
    fun apply(expected: ActivityOwner, page: ActivityPage, allowed: (ActivityKind) -> Boolean): Boolean = synchronized(lock) {
        if (owner != expected) return false
        if (page.readyNotices != null) return applyReady(page, allowed)
        (store as? ActivityWindowStore)?.let { return applyWindow(expected, page, allowed, it) }
        val marks = linkedMapOf<String, ActivityWatermark>()
        val notices = mutableListOf<ActivityNotice>()
        // One replacement per game/attempt, not an alert per primitive or per polled entry.
        for (hint in page.entries) {
            val id = ActivityWire.ordinal(hint.activityId) ?: return false
            val key = hint.notificationKey()
            val previous = marks[key] ?: store.watermark(key)
            if (previous != null && previous.activity >= id) continue
            marks[key] = ActivityWatermark(id, hint.identity)
            notices.removeAll { it.key == key }
            if (!page.summaryReset && allowed(hint.kind) && !displaying(hint)) {
                notices += ActivityNotice(key, hint, !hint.kind.isPodActivity() && store.watermark(key)?.identity == hint.identity)
            }
        }
        if (!store.save(page.nextCursor, marks)) return false
        // Logout cannot pass the shared lock between watermark and display.
        notices.forEach(post)
        true
    }

    /** The resumed app is showing this notification's game, pod or pod list. */
    private fun displaying(hint: ActivityHint) = foreground && screen.shows(hint.notificationKey())

    private fun applyReady(page: ActivityPage, allowed: (ActivityKind) -> Boolean): Boolean {
        val marks = linkedMapOf<String, ActivityWatermark>()
        val notices = linkedMapOf<String, ActivityNotice>()
        val hints = if (page.summaryReset) page.entries + page.readyNotices.orEmpty() else page.readyNotices.orEmpty()
        for (hint in hints.sortedBy { ActivityWire.ordinal(it.activityId) }) {
            val id = ActivityWire.ordinal(hint.activityId) ?: return false
            val key = hint.notificationKey()
            val previous = marks[key] ?: store.watermark(key)
            if (id <= (previous?.activity ?: -1)) continue
            val shown = displaying(hint)
            marks[key] = ActivityWatermark(id, hint.identity, if (shown) false else hint.notice?.yourMove)
            if (!page.summaryReset && allowed(hint.kind) && !shown) {
                val silent = hint.kind == ActivityKind.GAME && previous != null &&
                    !(hint.notice?.yourMove == true && previous.yourMove != true)
                notices[key] = ActivityNotice(key, hint, silent)
            }
        }
        val saved = (store as? ActivityWindowStore)?.saveWindow(page.nextCursor, marks, emptyMap()) ?: store.save(page.nextCursor, marks)
        if (saved) notices.values.forEach(post)
        return saved
    }

    private fun applyWindow(expected: ActivityOwner, page: ActivityPage, allowed: (ActivityKind) -> Boolean,
        windows: ActivityWindowStore): Boolean {
        if (owner != expected) return false
        val time = now()
        val pending = windows.pending().toMutableMap()
        val marks = linkedMapOf<String, ActivityWatermark>()
        val notices = mutableListOf<ActivityNotice>()
        fun suppressed(hint: ActivityHint) = !allowed(hint.kind) || displaying(hint)
        fun consume(key: String, hint: ActivityHint, notify: Boolean) {
            val old = marks[key] ?: store.watermark(key)
            marks[key] = ActivityWatermark(requireNotNull(ActivityWire.ordinal(hint.activityId)), hint.identity)
            if (notify) notices += ActivityNotice(key, hint, !hint.kind.isPodActivity() && old?.identity == hint.identity)
            pending.remove(key)
        }
        if (page.summaryReset) pending.toMap().forEach { (key, item) -> consume(key, item.hint, false) }
        for (hint in page.entries) {
            val id = ActivityWire.ordinal(hint.activityId) ?: return false
            val key = hint.notificationKey()
            val previous = marks[key] ?: store.watermark(key)
            val queued = pending[key]
            if (id <= maxOf(previous?.activity ?: -1, queued?.hint?.activityId?.toLong() ?: -1)) continue
            if (page.summaryReset || suppressed(hint)) consume(key, hint, false)
            else if (hint.kind != ActivityKind.GAME) consume(key, hint, true)
            else {
                val first = queued?.takeIf { it.hint.identity == hint.identity && it.hint.authorId == hint.authorId }?.firstAt ?: time
                pending[key] = PendingActivity(hint, first, minOf(time + 2_000, first + 10_000))
            }
        }
        for ((key, item) in pending.toMap()) {
            val suppress = suppressed(item.hint)
            if (suppress || item.dueAt <= time) consume(key, item.hint, !suppress)
        }
        // Cursor and unposted windows survive restart; consumed marks precede every OS call.
        if (!windows.saveWindow(page.nextCursor, marks, pending)) return false
        notices.forEach(post)
        return true
    }
}

/** Bounded production worker driver; network adapters and owner lock stay outside UI code. */
class ActivityRefresh(private val current: () -> Boolean, private val cursor: () -> String?,
    private val apply: (ActivityPage) -> Boolean) {
    /** True requests a continuation. A late account result never reaches local storage. */
    suspend fun run(register: suspend () -> Unit, fetch: suspend (String?) -> ActivityPage): Boolean {
        if (!current()) return false
        register()
        repeat(10) {
            if (!current()) return false
            val page = fetch(cursor())
            if (!current() || !apply(page)) return false
            if (!page.hasMore) return false
        }
        return true
    }
}

object ActivityPolling {
    const val PERIOD_MINUTES = 15L
    const val NETWORK_REQUIRED = true
    const val MAX_RETRIES = 11
    const val MAX_SECONDS = 6 * 60 * 60L
    fun retrySeconds(attempt: Int, retryAfter: Long = 0): Long? {
        if (attempt >= MAX_RETRIES) return null
        val exponential = (30L shl attempt.coerceIn(0, 10)).coerceAtMost(MAX_SECONDS)
        // A Retry-After beyond the bounded retry window defers to ordinary periodic/foreground refresh.
        if (retryAfter > MAX_SECONDS) return null
        return maxOf(exponential, retryAfter.coerceAtLeast(0))
    }
}
