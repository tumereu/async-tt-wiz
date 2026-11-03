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
internal const val SHAKE_MILLIS = 160

/** I-D46 (b): the shake's sideways offsets in dp, evenly spaced before it settles back at 0. */
internal val SHAKE_OFFSETS = listOf(3f, -2f, 1f)

/** I-D46 (b): a small horizontal shake (dp offsets), for a card, pile or control Automatum doesn't allow; the one spec
 * every shake uses (the table's items and the controls, [shaken]). */
internal fun shakeKeyframes(): KeyframesSpec<Float> = keyframes {
    durationMillis = SHAKE_MILLIS
    for ((n, at) in SHAKE_OFFSETS.withIndex()) at at (n + 1) * SHAKE_MILLIS / (SHAKE_OFFSETS.size + 1)
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
        if (context.viewer == null) return false
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
                menuIcon(FULL_CONTROL), own.fullControl, enabled) { setFullControl(!own.fullControl) },
            AutomatumToggle(SKIP, "Skip if\nno choice", "Skip when I have no choice: Automatum passes for you when you could only pass",
                menuIcon(SKIP), own.skipWhenNoChoice, enabled) {
                submit(PlayAction.Mtg(MtgAction.SetSkipWhenNoChoice(!own.skipWhenNoChoice)))
            },
            AutomatumToggle(AUTOTAP, "Autotap\nmana", "Autotap mana sources: casting pays by itself, with mana in your pool first",
                menuIcon(AUTOTAP), autotap, true) { setAutotap(!autotap) })
    }
}

/** The quick-actions row with Automatum on (I-D46 (c)): equal-width icon + text toggles, the words at a readable size
 * (UI review round 2 #8); an on toggle is filled violet with a check, an off one white with an empty ring; hold shows
 * what each does. */
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
            AutomatumViolet, fill = if (toggle.on) AutomatumViolet else Color.White, role = Role.Switch,
            tooltip = toggle.tooltip) {
            val ink = (if (toggle.on) Color.White else MenuInk).let { it.copy(alpha = if (toggle.enabled) 1f else .45f) }
            Column(Modifier.fillMaxSize().padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(toggle.icon, null, Modifier.size(16.dp), tint = ink)
                    // The state at a glance: a check when on, an empty ring when off
                    Icon(if (toggle.on) androidx.compose.material.icons.Icons.Filled.CheckCircle
                        else androidx.compose.material.icons.Icons.Outlined.RadioButtonUnchecked, null, Modifier.size(13.dp), tint = ink)
                }
                Text(toggle.label, color = ink, fontSize = 11.sp, lineHeight = 12.sp, letterSpacing = 0.sp, maxLines = 2,
                    textAlign = TextAlign.Center, fontWeight = if (toggle.on) FontWeight.Bold else FontWeight.Medium,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
