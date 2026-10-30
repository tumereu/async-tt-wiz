package org.asyncttwiz.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.asyncttwiz.protocol.ActivityHint
import org.asyncttwiz.protocol.ActivityKind

/** The swiped-away game notice: its latest event's identity and time ([GameNotificationText.whenMillis]). */
data class WaitingDismissal(val identity: String, val whenMillis: Long) {
    fun encode() = "$whenMillis|$identity"
    companion object {
        fun decode(value: String?): WaitingDismissal? = value?.split('|', limit = 2)?.takeIf { it.size == 2 }
            ?.let { (time, identity) -> time.toLongOrNull()?.let { WaitingDismissal(identity, it) } }
    }
}

/** State-based safety net run by the app itself, independent of push and of the activity feed. */
object WaitingChecks {
    /** Minutes after the app went to the background; then once a day until the app is opened again. */
    val OFFSETS_MINUTES = listOf(15L, 30L, 60L, 120L, 240L, 480L, 960L)
    const val DAILY_MINUTES = 24 * 60L
    fun offsetMinutes(check: Int): Long = OFFSETS_MINUTES.getOrNull(check)
        ?: (OFFSETS_MINUTES.last() + DAILY_MINUTES * (check - OFFSETS_MINUTES.size + 1))

    /** The waiting games to notify now: none already showing (from any source) and none whose notice the
     * player swiped away while the game still waits on the same latest event. */
    fun select(waiting: List<ActivityHint>, showing: Set<String>, dismissed: (String) -> WaitingDismissal?): List<ActivityHint> =
        waiting.filter { hint ->
            val notice = hint.notice
            hint.kind == ActivityKind.GAME && notice != null && notice.yourMove && hint.gameId !in showing &&
                dismissed(hint.gameId)?.let { it.identity == hint.identity && gameNotificationText(notice).whenMillis <= it.whenMillis } != true
        }
}

class WaitingCheckWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        inputData.getString("binding")?.let { AndroidActivityDelivery.get(applicationContext).checkWaiting(it) }
        Result.success() // A failed check waits for the next scheduled one.
    }
}

/** Delete intent of a game notification: the player swiped it away (taps and app cancels never send it). */
class ActivityDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val binding = intent.getStringExtra("binding") ?: return
        val game = intent.getStringExtra("game") ?: return
        val identity = intent.getStringExtra("identity") ?: return
        AndroidActivityDelivery.get(context).dismissed(binding, game, WaitingDismissal(identity, intent.getLongExtra("when", Long.MAX_VALUE)))
    }
}
