package org.asyncttwiz.android

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import org.asyncttwiz.protocol.GameplayField
import org.asyncttwiz.protocol.GameplayMutation
import org.asyncttwiz.protocol.PlayState

internal enum class StatusBubbleMode { NONE, BUBBLE, DOT }

/** A one-off hide of [text] as set by the status event [key]; [at] is the event the viewer was on when hiding. */
internal data class StatusBubbleHide(val text: String, val key: String, val at: String)

/** This viewer's own choice for one player's status bubble in one game. Never sent to the server. */
internal data class StatusBubbleChoice(val muted: Boolean = false, val hidden: StatusBubbleHide? = null)

internal object StatusBubbleRules {
    private const val START = "start:"
    private const val MAX_STEPS = 1_000_000

    private fun changesStatus(node: GameplayMutation, player: String) =
        node.transition.fields.any { it.field == GameplayField.STATUS && it.targetId == player }

    /** The latest event on the path to [cursor] that changed [player]'s status, or where that path starts. */
    fun key(node: (String) -> GameplayMutation?, cursor: String, player: String): String {
        var id = cursor
        repeat(MAX_STEPS) {
            val mutation = node(id) ?: return START + id
            if (changesStatus(mutation, player)) return id
            id = mutation.parentId
        }
        return START + id
    }

    fun hide(node: (String) -> GameplayMutation?, cursor: String, player: String, status: String) =
        StatusBubbleHide(status, key(node, cursor, player), cursor)

    /** Still hidden only while the same status event is in effect. Walks back only as far as the hide point
     * or the latest status change, so ordinary forward play stays cheap. */
    fun hidden(node: (String) -> GameplayMutation?, cursor: String, player: String, status: String,
        hide: StatusBubbleHide?): Boolean {
        if (hide == null || status.isEmpty() || hide.text != status) return false
        var id = cursor
        repeat(MAX_STEPS) {
            if (id == hide.at) return true
            val mutation = node(id) ?: return START + id == hide.key
            if (changesStatus(mutation, player)) return id == hide.key
            id = mutation.parentId
        }
        return false
    }

    fun mode(status: String, choice: StatusBubbleChoice, hidden: Boolean): StatusBubbleMode = when {
        status.isEmpty() -> StatusBubbleMode.NONE
        hidden -> StatusBubbleMode.NONE
        choice.muted -> StatusBubbleMode.DOT
        else -> StatusBubbleMode.BUBBLE
    }
}

/** Device-local, per account + game + player; survives restarts like the card ring page. */
internal class StatusBubbleStore(context: Context, account: String, game: String) {
    private val preferences = context.applicationContext.getSharedPreferences("status-bubbles", Context.MODE_PRIVATE)
    private val prefix = "${account.length}:$account:$game/"
    private val choices = mutableMapOf<String, StatusBubbleChoice>()
    private val changes = mutableIntStateOf(0)
    /** Read during composition so writes redraw the table and the panel. */
    val version: Int get() = changes.intValue

    fun choice(player: String): StatusBubbleChoice {
        if (version < 0) error("unreachable") // Observed: callers in composition see later writes.
        return choices.getOrPut(player) {
        val key = "$prefix${player.length}:$player"
        val text = preferences.getString("$key/hidden-text", null)
        val event = preferences.getString("$key/hidden-key", null)
        val at = preferences.getString("$key/hidden-at", null)
        StatusBubbleChoice(preferences.getBoolean("$key/muted", false),
            if (text != null && event != null && at != null) StatusBubbleHide(text, event, at) else null)
        }
    }

    fun write(player: String, choice: StatusBubbleChoice) {
        val key = "$prefix${player.length}:$player"
        val edit = preferences.edit()
        if (choice.muted) edit.putBoolean("$key/muted", true) else edit.remove("$key/muted")
        val hide = choice.hidden
        if (hide == null) edit.remove("$key/hidden-text").remove("$key/hidden-key").remove("$key/hidden-at")
        else edit.putString("$key/hidden-text", hide.text).putString("$key/hidden-key", hide.key).putString("$key/hidden-at", hide.at)
        edit.apply()
        choices[player] = choice
        changes.intValue++
    }
}

/** Personal hide/mute of status bubbles: no game event, no effect on notifications or quick actions. */
@Stable
internal class StatusBubbles(private val store: StatusBubbleStore, private val node: (String) -> GameplayMutation?) {
    val version: Int get() = store.version

    fun modes(play: PlayState): Map<String, StatusBubbleMode> = play.board.players.associate { player ->
        val choice = store.choice(player.accountId)
        player.accountId to StatusBubbleRules.mode(player.status, choice,
            StatusBubbleRules.hidden(node, play.currentNodeId, player.accountId, player.status, choice.hidden))
    }

    fun muted(player: String): Boolean = store.choice(player).muted

    /** Hides whatever is shown for [player] (bubble or dot) until they set a new status; [hidden] false shows it again. */
    fun hide(play: PlayState, player: String, hidden: Boolean) {
        val status = play.board.players.single { it.accountId == player }.status
        store.write(player, store.choice(player).copy(
            hidden = if (hidden) StatusBubbleRules.hide(node, play.currentNodeId, player, status) else null))
    }

    /** Muting and unmuting both start fresh: unmute always restores the bubble. */
    fun mute(player: String, muted: Boolean) = store.write(player, StatusBubbleChoice(muted = muted))
}

internal val LocalStatusBubbles = staticCompositionLocalOf<StatusBubbles?> { null }
