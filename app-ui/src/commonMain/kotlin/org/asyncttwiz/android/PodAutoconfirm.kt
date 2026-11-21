package org.asyncttwiz.android

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.appui.AppPlatform

/**
 * DRF-293, DRF-294, LOB-264 (owner, 2026-11-21): draft autoconfirm, personal UI kept on the device. Each draft attempt has
 * its own Auto toggle, the tip at the right end of the pick's Confirm (Booster, Glimpse, Custom booster, Arena and Grid); a draft whose toggle was never touched starts as Settings' "Autoconfirm
 * draft picks" says (off by default).
 */
class PodAutoconfirmPreferences(platform: AppPlatform) {
    private val preferences = platform.preferences("pod-autoconfirm")

    /** LOB-264: whether new drafts start with Auto on. */
    var startOn: Boolean
        get() = preferences.getBoolean(START_ON, false)
        set(value) {
            AppLog.info("settings", "Autoconfirm draft picks: new drafts start with Auto ${if (value) "on" else "off"}")
            preferences.edit().putBoolean(START_ON, value).apply()
        }

    /** DRF-293: draft attempt [draft]'s Auto toggle. */
    fun of(draft: String): Boolean = preferences.getBoolean(key(draft), startOn)

    fun set(draft: String, on: Boolean) {
        AppLog.info("pod", "Autoconfirm ${if (on) "on" else "off"} for draft $draft")
        preferences.edit().putBoolean(key(draft), on).apply()
    }

    fun observe(onChange: () -> Unit): () -> Unit = preferences.observe { onChange() }

    private fun key(draft: String) = "draft-$draft"
    private companion object { const val START_ON = "start-on" }
}

/** DRF-293: draft attempt [draft]'s Auto toggle and its setter, following changes made elsewhere on the device. */
@Composable
fun rememberPodAutoconfirm(draft: String): Pair<Boolean, (Boolean) -> Unit> {
    val platform = AppPlatform.current
    val preferences = remember(platform) { PodAutoconfirmPreferences(platform) }
    val state = remember(preferences, draft) { mutableStateOf(preferences.of(draft)) }
    DisposableEffect(preferences, draft) {
        val stop = preferences.observe { state.value = preferences.of(draft) }
        state.value = preferences.of(draft)
        onDispose { stop() }
    }
    return state.value to { on: Boolean -> preferences.set(draft, on); state.value = on }
}

/** LOB-264: the Settings switch, in the Games section. */
@Composable
fun PodAutoconfirmSetting() {
    val platform = AppPlatform.current
    val preferences = remember(platform) { PodAutoconfirmPreferences(platform) }
    val state = remember(preferences) { mutableStateOf(preferences.startOn) }
    DisposableEffect(preferences) {
        val stop = preferences.observe { state.value = preferences.startOn }
        state.value = preferences.startOn
        onDispose { stop() }
    }
    val on = state.value
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pod-autoconfirm-setting")
        .toggleable(on, role = Role.Switch, onValueChange = { preferences.startOn = it }),
        verticalAlignment = Alignment.CenterVertically) {
        SettingsLabel("Autoconfirm draft picks", "New drafts start with Auto on beside Confirm: a pick confirms itself once its cards are marked",
            Modifier.weight(1f).padding(end = 12.dp, top = 4.dp, bottom = 4.dp))
        Switch(checked = on, onCheckedChange = null)
    }
}

/** DRF-294: Confirm's base colour while Auto is on, and its text and icon on it. */
val PodAutoconfirmYellow = Color(0xFFF5C518)
val PodAutoconfirmInk = Color(0xFF3A2C00)
/** DRF-294: the shine's one sweep across Confirm, then back. */
const val PodAutoconfirmShineMillis = 1_400

/** DRF-294: Confirm's colours: yellow while Auto is on, also while it can't be pressed (with Auto on it mostly can't: the pick
 * goes by itself), its label dimmed then; else the theme's. */
@Composable
fun podConfirmColors(auto: Boolean): ButtonColors = if (!auto) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(
    containerColor = PodAutoconfirmYellow, contentColor = PodAutoconfirmInk,
    disabledContainerColor = PodAutoconfirmYellow, disabledContentColor = PodAutoconfirmInk.copy(alpha = .6f))

/**
 * DRF-294: while [on], a soft white band of light slanting across the button sweeps from near its left end to near its right
 * and back, again and again, over its content; the button is clipped to [shape].
 */
@Composable
fun Modifier.podAutoconfirmShine(on: Boolean, shape: Shape = ButtonDefaults.shape): Modifier {
    if (!on) return this
    val sweep by rememberInfiniteTransition(label = "autoconfirm shine").animateFloat(0f, 1f,
        infiniteRepeatable(tween(PodAutoconfirmShineMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "shine")
    return clip(shape).drawWithContent {
        drawContent()
        val band = maxOf(size.height * 1.6f, size.width * .18f)
        val x = size.width * (.08f + .84f * sweep)
        drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = .7f), Color.Transparent),
            start = Offset(x - band / 2, 0f), end = Offset(x + band / 2, size.height)))
    }
}

/** DRF-293: the width of Auto, the tip at Confirm's right end. */
val PodAutoTipWidth = 52.dp

/**
 * DRF-293, DRF-294 (owner, 2026-11-21): the pick's Confirm with Auto as its right-hand tip, drawn as one rounded shape with a
 * thin divider: Confirm takes the rest of [modifier]'s width, Auto ([PodAutoTipWidth] wide) shows a bolt over a small "Auto".
 * While [auto] is on both are yellow and Confirm has its shine; off, Auto is tonal and Confirm looks as any button. Auto can
 * always be pressed, also while Confirm can't ([enabled]).
 */
@Composable
fun PodConfirmAuto(auto: Boolean, onAuto: (Boolean) -> Unit, enabled: Boolean, onConfirm: () -> Unit, confirmTag: String,
    modifier: Modifier = Modifier, label: String = "Confirm") {
    val colors = podConfirmColors(auto)
    val shape = ButtonDefaults.shape
    // At least a button's height, else as tall as its content asks: the two parts fill that height, never what the parent offers
    Row(modifier.heightIn(min = ButtonDefaults.MinHeight).height(IntrinsicSize.Min).clip(shape).testTag("pod-confirm-auto"),
        verticalAlignment = Alignment.CenterVertically) {
        val confirmContent = if (enabled) colors.contentColor else colors.disabledContentColor
        Box(Modifier.weight(1f).fillMaxHeight().background(if (enabled) colors.containerColor else colors.disabledContainerColor)
            .podAutoconfirmShine(auto, RectangleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onConfirm).testTag(confirmTag),
            contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides confirmContent) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) { ButtonLabel("ready", label) }
                }
            }
        }
        // The thin divider between the two
        Box(Modifier.width(1.dp).fillMaxHeight().background(if (auto) PodAutoconfirmInk.copy(alpha = .35f) else MaterialTheme.colorScheme.surface))
        Column(Modifier.width(PodAutoTipWidth).fillMaxHeight()
            .background(if (auto) PodAutoconfirmYellow else MaterialTheme.colorScheme.secondaryContainer)
            .toggleable(auto, role = Role.Switch, onValueChange = onAuto).testTag("pod-pick-autoconfirm")
            .semantics { contentDescription = "Auto" },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val content = if (auto) PodAutoconfirmInk else MaterialTheme.colorScheme.onSecondaryContainer
            Icon(if (auto) Icons.Filled.Bolt else Icons.Outlined.Bolt, null, Modifier.size(16.dp), tint = content)
            Text("Auto", color = content, fontSize = 10.sp, lineHeight = 11.sp, maxLines = 1)
        }
    }
}
