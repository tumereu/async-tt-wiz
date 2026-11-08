package org.asyncttwiz.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent

/** The record of the cue ([LocalShownTexts]: nothing in the app hears it; the e2e phones do). */
internal const val YOUR_MOVE_CUE = "Your move cue"

/**
 * TRN-075 (owner, 2026-11-08): when the move passes to you from the other player, one cue, once per passing: [see] is told
 * each board shown and says when it comes. It comes when you may act again after you couldn't ([GameDevice.Status.YourMove],
 * or a stop of playback at a decision of yours, not one your own pause made, and no play or combat holding your view), and
 * the other player's real input came since (or playback stops at their assumption about you). Your own chain of steps
 * brings none (nothing of theirs came), and neither does their answer within [JUST_ACTED_MILLIS] of your own action: you
 * have just acted and are still looking.
 */
internal class YourMoveCue {
    private var acting: Boolean? = null
    private var tip: String? = null
    private var theirInput = false
    private var leftAt = Long.MIN_VALUE / 2

    /** The board shown now: [status] with the [line] of events shown ending at [shownTip], [held] while something holds the
     * view; [now] in milliseconds. Whether the cue comes now. */
    fun see(status: GameDevice.Status, line: List<GameEvent>, shownTip: String, held: Boolean, me: String, now: Long): Boolean {
        val stopped = (status as? GameDevice.Status.Stopped)?.takeIf { !it.paused } != null
        val can = !held && (status == GameDevice.Status.YourMove || stopped)
        val last = tip
        if (last != null && last != shownTip) {
            // Taken back (an undo, a branch): what came before no longer counts
            if (line.none { it.id == last }) theirInput = false
            else for (i in line.indices.reversed()) {
                val event = line[i]
                if (event.id == last) break
                if (event.author != me && event.assumed == null && event.engine?.steps.orEmpty().any { it.input != null }) theirInput = true
            }
        }
        tip = shownTip
        val was = acting
        acting = can
        if (was == true && !can) leftAt = now
        if (!can) return false
        val cue = was == false && (theirInput || stopped) && now - leftAt >= JUST_ACTED_MILLIS
        theirInput = false
        return cue
    }

    companion object {
        /** How soon after your own action the other player's answer counts as you having just acted. */
        const val JUST_ACTED_MILLIS = 1_500L
    }
}

/** TRN-075: the cue's one pulse of the main button in your colour; bumping [cue] starts it (0: none yet). */
@Composable
internal fun MoveCuePulse(cue: Int, modifier: Modifier = Modifier) {
    val level = remember { Animatable(0f) }
    LaunchedEffect(cue) {
        if (cue == 0) return@LaunchedEffect
        level.snapTo(MOVE_CUE_STRENGTH)
        level.animateTo(0f, tween(MOVE_CUE_MILLIS))
    }
    Canvas(modifier) { if (level.value > 0f) drawRect(OwnTableColor.copy(alpha = level.value)) }
}

private const val MOVE_CUE_STRENGTH = .65f
private const val MOVE_CUE_MILLIS = 700
