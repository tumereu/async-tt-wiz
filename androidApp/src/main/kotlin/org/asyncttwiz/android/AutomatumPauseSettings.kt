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
 * copied into each game Automatum plays, since the other player's device follows it; [fullControlChanged]: when it was
 * last changed on this device (0: never; a value set before this was kept counts as 1), so it goes into a game only after
 * a change ([FullControlSync], #5). [autotap]: "Autotap mana sources" (I-D47, on by default): a cast is paid with
 * Automatum's suggested tapping (mana in the pool first) and the cast panel has no mana selection; this device's own,
 * for the quick actions to bind too ([PausePreferences.settings]).
 */
internal data class PauseSettings(val responseMillis: Long = DEFAULT_RESPONSE_MILLIS, val proceedAtOnce: Boolean = false,
    val fullControl: Boolean = false, val fullControlChanged: Long = if (fullControl) 1L else 0L, val autotap: Boolean = true) {
    companion object {
        const val DEFAULT_RESPONSE_MILLIS = 2_000L
        const val MIN_RESPONSE_MILLIS = 1_000L
        const val MAX_RESPONSE_MILLIS = 3_000L
        /** The slider's step. */
        const val RESPONSE_STEP_MILLIS = 500L
    }
}

/**
 * SYS-071 (pausereview #5): which change of this device's Full control ([PauseSettings.fullControlChanged]) each game
 * last took. A device sends its value into a game only after a change on it the game hasn't taken; otherwise it follows
 * the game, so two devices of one player with different settings don't change it back and forth.
 */
internal interface FullControlSync {
    /** The change [gameId] last took from this device (0: none). */
    fun synced(gameId: String): Long
    fun synced(gameId: String, changed: Long)

    /** Kept while the table is open (tests). */
    class Memory : FullControlSync {
        private val games = mutableMapOf<String, Long>()
        override fun synced(gameId: String) = games[gameId] ?: 0L
        override fun synced(gameId: String, changed: Long) { games[gameId] = changed }
    }
}

/** Device-local and account-scoped, like the other table and playback preferences. */
internal class PausePreferences(context: Context, private val accountId: String) : FullControlSync {
    private val preferences = context.applicationContext.getSharedPreferences("automatum-pauses", Context.MODE_PRIVATE)
    private val responseKey = "response/$accountId"
    private val proceedKey = "proceed/$accountId"
    private val fullControlKey = "full-control/$accountId"
    private val fullControlChangedKey = "full-control-changed/$accountId"
    private val autotapKey = "autotap/$accountId"
    private val keys = setOf(responseKey, proceedKey, fullControlKey, fullControlChangedKey, autotapKey)

    var settings: PauseSettings
        get() = preferences.getBoolean(fullControlKey, false).let { fullControl ->
            PauseSettings(preferences.getLong(responseKey, PauseSettings.DEFAULT_RESPONSE_MILLIS)
                .coerceIn(PauseSettings.MIN_RESPONSE_MILLIS, PauseSettings.MAX_RESPONSE_MILLIS),
                preferences.getBoolean(proceedKey, false), fullControl,
                preferences.getLong(fullControlChangedKey, if (fullControl) 1L else 0L), preferences.getBoolean(autotapKey, true))
        }
        set(value) {
            preferences.edit().putLong(responseKey, value.responseMillis.coerceIn(PauseSettings.MIN_RESPONSE_MILLIS,
                PauseSettings.MAX_RESPONSE_MILLIS)).putBoolean(proceedKey, value.proceedAtOnce)
                .putBoolean(fullControlKey, value.fullControl).putLong(fullControlChangedKey, value.fullControlChanged)
                .putBoolean(autotapKey, value.autotap).apply()
        }

    /** I-D56 §7, §13 (LOB-113): the response mode and "Always assume resolves" a new game on engine state starts with. */
    var engineDefaults: org.asyncttwiz.protocol.game.PlaySettings
        get() = org.asyncttwiz.protocol.game.PlaySettings(
            mode = when (preferences.getString("engine-mode/$accountId", null)) {
                "full" -> org.asyncttwiz.protocol.game.ResponseMode.FullControl
                "normal" -> org.asyncttwiz.protocol.game.ResponseMode.Normal
                else -> org.asyncttwiz.protocol.game.ResponseMode.AutoSkip
            },
            alwaysAssumeResolves = preferences.getBoolean("engine-assume/$accountId", false),
            autotap = preferences.getBoolean(autotapKey, true))
        set(value) {
            preferences.edit().putString("engine-mode/$accountId", when (value.mode) {
                org.asyncttwiz.protocol.game.ResponseMode.FullControl -> "full"
                org.asyncttwiz.protocol.game.ResponseMode.Normal -> "normal"
                org.asyncttwiz.protocol.game.ResponseMode.AutoSkip -> "auto-skip"
            }).putBoolean("engine-assume/$accountId", value.alwaysAssumeResolves).putBoolean(autotapKey, value.autotap).apply()
        }

    /** Whether this device gave game [gameId] the defaults ([engineDefaults]) once. */
    fun engineDefaultsGiven(gameId: String): Boolean = preferences.getBoolean("engine-defaults/$accountId/$gameId", false)
    fun engineDefaultsGiven(gameId: String, given: Boolean) { preferences.edit().putBoolean("engine-defaults/$accountId/$gameId", given).apply() }

    override fun synced(gameId: String): Long = preferences.getLong("full-control-synced/$accountId/$gameId", 0L)
    override fun synced(gameId: String, changed: Long) {
        preferences.edit().putLong("full-control-synced/$accountId/$gameId", changed).apply()
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
            .toggleable(settings.fullControl, role = Role.Switch, onValueChange = {
                preferences.settings = settings.copy(fullControl = it, fullControlChanged = System.currentTimeMillis())
            }),
            verticalAlignment = Alignment.CenterVertically) {
            SettingsLabel("Full control", "Also pause for your own spells and in every step in which you could act",
                Modifier.weight(1f).padding(end = 12.dp, top = 4.dp, bottom = 4.dp))
            Switch(checked = settings.fullControl, onCheckedChange = null)
        }
        // I-D47 (also a quick-actions toggle, I-D46 (c)): casting taps the mana sources by itself (mana already in your pool first)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("autotap")
            .toggleable(settings.autotap, role = Role.Switch, onValueChange = { preferences.settings = settings.copy(autotap = it) }),
            verticalAlignment = Alignment.CenterVertically) {
            SettingsLabel("Autotap mana sources", "Casting pays by itself, with mana in your pool first; off, you choose what to tap",
                Modifier.weight(1f).padding(end = 12.dp, top = 4.dp, bottom = 4.dp))
            Switch(checked = settings.autotap, onCheckedChange = null)
        }
        if (BuildConfig.DEBUG) EngineDefaultSettings(preferences)
    }
}

/**
 * I-D56 §7, §13 (LOB-113): the response mode and "Always assume resolves" new games on engine state start with (each
 * game's own are changed from its quick actions); in debug builds also the switch that plays engine games on engine
 * state ([EnginePath], step C's test switch until the switch-over).
 */
@Composable
private fun EngineDefaultSettings(preferences: PausePreferences) {
    var defaults by remember(preferences) { mutableStateOf(preferences.engineDefaults) }
    fun set(next: org.asyncttwiz.protocol.game.PlaySettings) { preferences.engineDefaults = next; defaults = next }
    Text("Response mode in new games", Modifier.padding(vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    for (mode in listOf(org.asyncttwiz.protocol.game.ResponseMode.FullControl, org.asyncttwiz.protocol.game.ResponseMode.Normal,
            org.asyncttwiz.protocol.game.ResponseMode.AutoSkip)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("response-mode-${mode.label()}")
            .selectable(defaults.mode == mode, role = Role.RadioButton, onClick = { set(defaults.copy(mode = mode)) }),
            verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = defaults.mode == mode, onClick = null)
            SettingsLabel(mode.label(), mode.meaning(), Modifier.weight(1f).padding(start = 12.dp, top = 4.dp, bottom = 4.dp))
        }
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("always-assume-resolves")
        .toggleable(defaults.alwaysAssumeResolves, role = Role.Switch, onValueChange = { set(defaults.copy(alwaysAssumeResolves = it)) }),
        verticalAlignment = Alignment.CenterVertically) {
        SettingsLabel("Always assume resolves", "Go on at the other player's pauses even while they are online; they can still respond later",
            Modifier.weight(1f).padding(end = 12.dp, top = 4.dp, bottom = 4.dp))
        Switch(checked = defaults.alwaysAssumeResolves, onCheckedChange = null)
    }
    if (BuildConfig.DEBUG) {
        val context = LocalContext.current
        var on by remember { mutableStateOf(EnginePath.enabled) }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("engine-path")
            .toggleable(on, role = Role.Switch, onValueChange = { EnginePath.set(context, it); on = it }),
            verticalAlignment = Alignment.CenterVertically) {
            SettingsLabel("Engine games (test)", "Play games the server runs on the engine with the new async play (debug builds only)",
                Modifier.weight(1f).padding(end = 12.dp, top = 4.dp, bottom = 4.dp))
            Switch(checked = on, onCheckedChange = null)
        }
    }
}
