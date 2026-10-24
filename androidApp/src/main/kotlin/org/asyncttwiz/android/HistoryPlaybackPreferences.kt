package org.asyncttwiz.android

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/** Device-local, account-scoped presentation preference; never part of game state. */
class HistoryPlaybackPreferences(context: Context, accountId: String) {
    private val preferences = context.applicationContext.getSharedPreferences("history-playback", Context.MODE_PRIVATE)
    internal val key = "pause/$accountId"
    var pauseMillis: Long
        get() = preferences.getLong(key, DEFAULT_PAUSE_MILLIS).coerceIn(MIN_PAUSE_MILLIS, MAX_PAUSE_MILLIS)
        set(value) { preferences.edit().putLong(key, value.coerceIn(MIN_PAUSE_MILLIS, MAX_PAUSE_MILLIS)).apply() }

    fun observe(onChange: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed -> if (changed == key) onChange() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        const val DEFAULT_PAUSE_MILLIS = 1_500L
        const val MIN_PAUSE_MILLIS = 100L
        const val MAX_PAUSE_MILLIS = 10_000L
    }
}

@Composable
internal fun rememberHistoryPlaybackPreferences(accountId: String): HistoryPlaybackPreferences {
    val context = LocalContext.current
    return remember(context, accountId) { HistoryPlaybackPreferences(context, accountId) }
}

@Composable
internal fun HistoryPlaybackPreferences.pauseState(): State<Long> {
    val state = remember(this) { mutableLongStateOf(pauseMillis) }
    DisposableEffect(this) {
        val stop = observe { state.longValue = pauseMillis }
        state.longValue = pauseMillis
        onDispose { stop() }
    }
    return state
}
