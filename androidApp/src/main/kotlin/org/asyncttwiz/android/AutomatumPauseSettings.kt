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

/** The play settings new games start with (LOB-113), device-local and account-scoped like the other table and playback
 * preferences (the server keeps them per account; this is the device's copy for when it is signed out). */
internal class PausePreferences(context: Context, private val accountId: String) {
    private val preferences = context.applicationContext.getSharedPreferences("automatum-pauses", Context.MODE_PRIVATE)
    private val autotapKey = "autotap/$accountId"
    private val assumeKey = "engine-assume-resolves/$accountId"

    /** I-D56 §7, §13 (LOB-113): the response mode, "Assume resolves" and Autotap a new game on engine state starts with. */
    var engineDefaults: org.asyncttwiz.protocol.game.PlaySettings
        get() = org.asyncttwiz.protocol.game.PlaySettings(
            mode = when (preferences.getString("engine-mode/$accountId", null)) {
                "full" -> org.asyncttwiz.protocol.game.ResponseMode.FullControl
                "normal" -> org.asyncttwiz.protocol.game.ResponseMode.Normal
                else -> org.asyncttwiz.protocol.game.ResponseMode.AutoSkip
            },
            assume = org.asyncttwiz.protocol.game.AssumeResolves.all.firstOrNull { it.key() == preferences.getString(assumeKey, null) }
                ?: org.asyncttwiz.protocol.game.PlaySettings.NEW_GAMES.assume,
            autotap = preferences.getBoolean(autotapKey, true))
        set(value) {
            preferences.edit().putString("engine-mode/$accountId", when (value.mode) {
                org.asyncttwiz.protocol.game.ResponseMode.FullControl -> "full"
                org.asyncttwiz.protocol.game.ResponseMode.Normal -> "normal"
                org.asyncttwiz.protocol.game.ResponseMode.AutoSkip -> "auto-skip"
            }).putString(assumeKey, value.assume.key()).putBoolean(autotapKey, value.autotap).apply()
        }

}

private fun org.asyncttwiz.protocol.game.AssumeResolves.key() = when (this) {
    org.asyncttwiz.protocol.game.AssumeResolves.Never -> "never"
    org.asyncttwiz.protocol.game.AssumeResolves.Normal -> "normal"
    org.asyncttwiz.protocol.game.AssumeResolves.WhenNoMana -> "no-mana"
    org.asyncttwiz.protocol.game.AssumeResolves.Always -> "always"
}

/** Settings → Automatum (LOB-113): the play settings new games start with, kept on the server per account. */
@Composable
internal fun AutomatumPauseSettings(accountId: String, account: AccountSettingsState? = null) {
    val context = LocalContext.current
    val preferences = remember(context, accountId) { PausePreferences(context, accountId) }
    Column(Modifier.fillMaxWidth().testTag("automatum-settings")) { EngineDefaultSettings(preferences, account) }
}

/**
 * I-D56 §7, §13 (LOB-113): the response mode, "Assume resolves" and "Autotap mana sources" (I-D47) new games
 * start with (each game's own are changed from its quick actions).
 */
@Composable
private fun EngineDefaultSettings(preferences: PausePreferences, account: AccountSettingsState?) {
    var local by remember(preferences) { mutableStateOf(preferences.engineDefaults) }
    // LOB-113 (I-D56 step D): kept on the server per account (the device keeps a copy for when it is signed out)
    val defaults = account?.settings?.playDefaults ?: local
    fun set(next: org.asyncttwiz.protocol.game.PlaySettings) {
        preferences.engineDefaults = next; local = next
        account?.setPlayDefaults(next)
    }
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
    // HIS-096: where the other player's passes are assumed at once: Never, Normal, When they have no mana (the default), Always
    Text("Assume resolves in new games", Modifier.padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    for (choice in org.asyncttwiz.protocol.game.AssumeResolves.all) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("assume-resolves-${choice.short().lowercase().replace(' ', '-')}")
            .selectable(defaults.assume == choice, role = Role.RadioButton, onClick = { set(defaults.assuming(choice)) }),
            verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = defaults.assume == choice, onClick = null)
            SettingsLabel(choice.label(), choice.meaning(), Modifier.weight(1f).padding(start = 12.dp, top = 4.dp, bottom = 4.dp))
        }
    }
    // I-D47 (also a quick-actions toggle, I-D46 (c)): casting taps the mana sources by itself (mana already in your pool first)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("autotap")
        .toggleable(defaults.autotap, role = Role.Switch, onValueChange = { set(defaults.copy(autotap = it)) }),
        verticalAlignment = Alignment.CenterVertically) {
        SettingsLabel("Autotap mana sources", "Casting pays by itself, with mana in your pool first; off, you choose what to tap",
            Modifier.weight(1f).padding(end = 12.dp, top = 4.dp, bottom = 4.dp))
        Switch(checked = defaults.autotap, onCheckedChange = null)
    }
}
