package org.asyncttwiz.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.KeyframesSpec
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.*

/** I-D46 (b): how long a refusal's shake runs (owner, playtest 2026-11-03: short and subtle). */
const val SHAKE_MILLIS = 160

/** I-D46 (b): the shake's sideways offsets in dp, evenly spaced before it settles back at 0. */
val SHAKE_OFFSETS = listOf(3f, -2f, 1f)

/** I-D46 (b): a small horizontal shake (dp offsets), for a card, pile or control Automatum doesn't allow; the one spec
 * every shake uses (the table's items and the controls, [shaken]). */
fun shakeKeyframes(): KeyframesSpec<Float> = keyframes {
    durationMillis = SHAKE_MILLIS
    for ((n, at) in SHAKE_OFFSETS.withIndex()) at at (n + 1) * SHAKE_MILLIS / (SHAKE_OFFSETS.size + 1)
}

/** TBL-118, HIS-075: told of each refused control's shake ([shaken]); the game table's makes the event text flash while
 * the other player's choice is open ([TableToolkitController.refusedTap]). */
val LocalRefusedTap = staticCompositionLocalOf<() -> Unit> { {} }

/** Shakes the element each time [shakes] goes up (I-D46: a refused control says why on hold, never by a message). */
@Composable
fun Modifier.shaken(shakes: Int): Modifier {
    val offset = remember { Animatable(0f) }
    val refused by rememberUpdatedState(LocalRefusedTap.current)
    val start = remember { shakes }
    LaunchedEffect(shakes) {
        if (shakes > 0) {
            if (shakes != start) refused()
            offset.animateTo(0f, shakeKeyframes())
        }
    }
    return graphicsLayer { translationX = offset.value * density }
}


object AutomatumToggles {
    const val CONTROL = "Control"
    const val SKIP = "Skip if no choice"
    const val AUTOTAP = "Autotap mana"

    /**
     * With Automatum on the quick-actions row is the viewer's Automatum toggles (I-D46 (c)), except while their opening
     * mulligan is open (TRN-027) and while Automatum's combat offers them its own quick actions (CMB-028, CMB-029,
     * CMB-034).
     */
    fun shown(play: TableBoard, context: QuickContext): Boolean {
        if (context.viewer == null) return false
        if (play.board.players.none { it.accountId == context.viewer }) return false
        if (QuickActions.openingMulligan(play, context.viewer)) return false
        val scope = TableActionScope(play.gameId, play.playId)
        return automatumCombatQuickActions(play, context, scope).isNullOrEmpty()
    }

}

