package org.asyncttwiz.android

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong

/**
 * I-D41 (LOB-113): how Automatum's pauses behave for this player. [responseMillis]: how long a pause runs (the progress
 * bar, and the longest the player's device waits for the other player); [proceedAtOnce]: "When the opponent can react",
 * Proceed at once (true) or Wait (false); [fullControl]: pauses where the player usually wouldn't respond too (SYS-071),
 * copied into each game Automatum plays, since the other player's device follows it.
 */
internal data class PauseSettings(val responseMillis: Long = DEFAULT_RESPONSE_MILLIS, val proceedAtOnce: Boolean = false,
    val fullControl: Boolean = false) {
    companion object {
        const val DEFAULT_RESPONSE_MILLIS = 2_000L
        const val MIN_RESPONSE_MILLIS = 1_000L
        const val MAX_RESPONSE_MILLIS = 3_000L
        /** The slider's step. */
        const val RESPONSE_STEP_MILLIS = 500L
    }
}

/** Device-local and account-scoped, like the other table and playback preferences. */
internal class PausePreferences(context: Context, accountId: String) {
    private val preferences = context.applicationContext.getSharedPreferences("automatum-pauses", Context.MODE_PRIVATE)
    private val responseKey = "response/$accountId"
    private val proceedKey = "proceed/$accountId"
    private val fullControlKey = "full-control/$accountId"
    private val keys = setOf(responseKey, proceedKey, fullControlKey)

    var settings: PauseSettings
        get() = PauseSettings(preferences.getLong(responseKey, PauseSettings.DEFAULT_RESPONSE_MILLIS)
            .coerceIn(PauseSettings.MIN_RESPONSE_MILLIS, PauseSettings.MAX_RESPONSE_MILLIS),
            preferences.getBoolean(proceedKey, false), preferences.getBoolean(fullControlKey, false))
        set(value) {
            preferences.edit().putLong(responseKey, value.responseMillis.coerceIn(PauseSettings.MIN_RESPONSE_MILLIS,
                PauseSettings.MAX_RESPONSE_MILLIS)).putBoolean(proceedKey, value.proceedAtOnce)
                .putBoolean(fullControlKey, value.fullControl).apply()
        }

    fun observe(onChange: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed -> if (changed == null || changed in keys) onChange() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

@Composable
internal fun rememberPauseSettings(accountId: String): Pair<State<PauseSettings>, PausePreferences> {
    val context = LocalContext.current
    val preferences = remember(context, accountId) { PausePreferences(context, accountId) }
    val state = remember(preferences) { mutableStateOf(preferences.settings) }
    DisposableEffect(preferences) {
        val stop = preferences.observe { state.value = preferences.settings }
        state.value = preferences.settings
        onDispose { stop() }
    }
    return state to preferences
}

/** Settings → Automatum (LOB-113). */
@Composable
internal fun AutomatumPauseSettings(accountId: String) {
    val (state, preferences) = rememberPauseSettings(accountId)
    val settings by state
    Column(Modifier.fillMaxWidth().testTag("automatum-settings")) {
        SettingsLabel("Response time", "${settings.responseMillis / 1000.0} s to react when the other player does something")
        Slider(value = settings.responseMillis.toFloat(), onValueChange = {
                val step = PauseSettings.RESPONSE_STEP_MILLIS
                preferences.settings = settings.copy(responseMillis = (it / step).roundToLong() * step)
            }, valueRange = PauseSettings.MIN_RESPONSE_MILLIS.toFloat()..PauseSettings.MAX_RESPONSE_MILLIS.toFloat(),
            steps = ((PauseSettings.MAX_RESPONSE_MILLIS - PauseSettings.MIN_RESPONSE_MILLIS) / PauseSettings.RESPONSE_STEP_MILLIS - 1).toInt(),
            modifier = Modifier.testTag("response-time"))
        Text("When the opponent can react", Modifier.padding(vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for ((label, atOnce) in listOf("Wait" to false, "Proceed at once" to true)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(if (atOnce) "proceed-at-once" else "proceed-wait")
                .selectable(settings.proceedAtOnce == atOnce, role = Role.RadioButton,
                    onClick = { preferences.settings = settings.copy(proceedAtOnce = atOnce) }),
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = settings.proceedAtOnce == atOnce, onClick = null)
                Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("full-control")
            .toggleable(settings.fullControl, role = Role.Switch, onValueChange = { preferences.settings = settings.copy(fullControl = it) }),
            verticalAlignment = Alignment.CenterVertically) {
            SettingsLabel("Full control", "Also pause for your own spells and in every step in which you could act",
                Modifier.weight(1f).padding(end = 12.dp, top = 4.dp, bottom = 4.dp))
            Switch(checked = settings.fullControl, onCheckedChange = null)
        }
    }
}
