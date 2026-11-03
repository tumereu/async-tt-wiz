package org.asyncttwiz.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.KeyframesSpec
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WaterDrop
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

/** I-D46 (b): how long a refusal's shake runs. */
internal const val SHAKE_MILLIS = 300

/** I-D46 (b): a small horizontal shake (dp offsets), for a card, pile or control Automatum doesn't allow. */
internal fun shakeKeyframes(): KeyframesSpec<Float> = keyframes {
    durationMillis = SHAKE_MILLIS
    for ((n, at) in listOf(4f, -4f, 3f, -2f, 1f).withIndex()) at at (n + 1) * SHAKE_MILLIS / 6
}

/** Shakes the element each time [shakes] goes up (I-D46: a refused control says why on hold, never by a message). */
@Composable
internal fun Modifier.shaken(shakes: Int): Modifier {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(shakes) { if (shakes > 0) offset.animateTo(0f, shakeKeyframes()) }
    return graphicsLayer { translationX = offset.value * density }
}

/** One of the viewer's Automatum toggles in the quick-actions row (I-D46 (c)). */
internal data class AutomatumToggle(val name: String, val label: String, val tooltip: String, val icon: ImageVector,
    val on: Boolean, val enabled: Boolean, val toggle: () -> Unit)

internal object AutomatumToggles {
    const val FULL_CONTROL = "Full control"
    const val SKIP = "Skip if no choice"
    const val AUTOTAP = "Autotap mana"

    /**
     * With Automatum on the quick-actions row is the viewer's Automatum toggles (I-D46 (c)), except while their opening
     * mulligan is open (TRN-027) and while Automatum's combat offers them its own quick actions (CMB-028, CMB-029,
     * CMB-034).
     */
    fun shown(play: PlayState, context: QuickContext): Boolean {
        if (!play.board.automatum || context.viewer == null) return false
        if (play.board.players.none { it.accountId == context.viewer }) return false
        if (QuickActions.openingMulligan(play, context.viewer)) return false
        val scope = TableActionScope(play.gameId, play.id)
        return automatumCombatQuickActions(play, context, scope).isNullOrEmpty()
    }

    /** The three toggles: Full control and Skip when I have no choice as the game holds them for [viewer] (SYS-071,
     * SYS-063), Autotap mana sources from this device (I-D47, [PauseSettings.autotap], which casting reads). */
    fun of(play: PlayState, viewer: String, enabled: Boolean, autotap: Boolean, setFullControl: (Boolean) -> Unit,
        submit: (PlayAction) -> Boolean, setAutotap: (Boolean) -> Unit): List<AutomatumToggle> {
        val own = play.board.players.single { it.accountId == viewer }
        return listOf(
            AutomatumToggle(FULL_CONTROL, "Full\ncontrol", "Full control: also pause for your own spells and in every step in which you could act",
                Icons.Outlined.Tune, own.fullControl, enabled) { setFullControl(!own.fullControl) },
            AutomatumToggle(SKIP, "Skip if\nno choice", "Skip when I have no choice: Automatum passes for you when you could only pass",
                Icons.Outlined.FastForward, own.skipWhenNoChoice, enabled) {
                submit(PlayAction.Mtg(MtgAction.SetSkipWhenNoChoice(!own.skipWhenNoChoice)))
            },
            AutomatumToggle(AUTOTAP, "Autotap\nmana", "Autotap mana sources: casting pays by itself, with mana in your pool first",
                Icons.Outlined.WaterDrop, autotap, true) { setAutotap(!autotap) })
    }
}

/** The quick-actions row with Automatum on (I-D46 (c)): equal-width icon + text toggles; on ones are tinted violet,
 * hold shows what each does. */
@Composable
internal fun RowScope.AutomatumToggleCells(play: PlayState, viewer: String, enabled: Boolean, submit: (PlayAction) -> Boolean) {
    val (pauseState, pausePreferences) = rememberPauseSettings(viewer)
    val toggles = AutomatumToggles.of(play, viewer, enabled, pauseState.value.autotap,
        // SYS-071: the device's setting, which goes into the game after a change (FullControlSync)
        setFullControl = { on -> pausePreferences.settings = pauseState.value.copy(fullControl = on,
            fullControlChanged = System.currentTimeMillis()) },
        // I-D47: the cast's own "Autotap mana sources" setting (Settings → Automatum)
        submit = submit, setAutotap = { on -> pausePreferences.settings = pauseState.value.copy(autotap = on) })
    for (toggle in toggles) key(toggle.name) {
        val control = MenuControl(toggle.name, toggle.enabled, selected = toggle.on, color = AutomatumViolet) { toggle.toggle() }
        RingPress(control, Modifier.weight(1f).height(48.dp).semantics { stateDescription = if (toggle.on) "On" else "Off" },
            AutomatumViolet, fill = if (toggle.on) AutomatumViolet.copy(alpha = .14f) else Color.White, role = Role.Switch,
            tooltip = toggle.tooltip) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                val ink = if (toggle.on) AutomatumViolet else MenuInk.copy(alpha = .55f)
                Icon(toggle.icon, null, Modifier.size(18.dp), tint = ink.copy(alpha = if (toggle.enabled) ink.alpha else .3f))
                Text(toggle.label, Modifier.padding(horizontal = 2.dp), fontSize = 8.sp, lineHeight = 9.sp, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Clip, letterSpacing = 0.sp, fontWeight = FontWeight.Medium,
                    color = MenuInk.copy(alpha = if (toggle.enabled) 1f else .38f))
            }
        }
    }
}
