package org.asyncttwiz.android

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/** Device-local, account-scoped presentation preference; never part of game state. */
class HistoryPlaybackPreferences(context: Context, accountId: String) {
    private val preferences = context.applicationContext.getSharedPreferences("history-playback", Context.MODE_PRIVATE)
    internal val key = "pause/$accountId"
    private val rewindKey = "rewind-pause/$accountId"
    private val seenPrefix = "last-seen/$accountId/"
    private val triggerPrefix = "auto-pause/$accountId/"
    private val autoRewindLiveKey = "auto-rewind-live/$accountId"
    private val autoRewindOpenKey = "auto-rewind-open/$accountId"

    init {
        // Older releases stored no distinction between the original default and an explicit 1.5s.
        // Migrate that old default once; all non-default stored delays remain untouched.
        val migrated = "pause-default-v2/$accountId"
        if (!preferences.getBoolean(migrated, false)) {
            val edit = preferences.edit().putBoolean(migrated, true)
            if (preferences.getLong(key, 1_500L) == 1_500L) edit.remove(key)
            edit.apply()
        }
        // An explicit old opt-out applies to both halves. Otherwise take the new defaults.
        if (preferences.contains(triggerPrefix + "STACK")) {
            val edit = preferences.edit().remove(triggerPrefix + "STACK")
            if (!preferences.getBoolean(triggerPrefix + "STACK", true)) {
                for (trigger in listOf(PlaybackAutoPause.STACK_ADDED, PlaybackAutoPause.STACK_REMOVED)) {
                    if (!preferences.contains(triggerPrefix + trigger.name)) edit.putBoolean(triggerPrefix + trigger.name, false)
                }
            }
            edit.apply()
        }
    }

    // Separate from the accepted replica and auth cleanup: debug Logout/account switching must
    // retain presentation bookmarks. IDs are still strictly scoped to the signed-in account.
    fun lastSeen(gameId: String): String? = preferences.getString(seenPrefix + gameId, null)
    fun shown(gameId: String, nodeId: String) {
        preferences.edit().putString(seenPrefix + gameId, nodeId).apply()
    }
    var pauseMillis: Long
        get() = preferences.getLong(key, DEFAULT_PAUSE_MILLIS).coerceIn(MIN_PAUSE_MILLIS, MAX_PAUSE_MILLIS)
        set(value) { preferences.edit().putLong(key, value.coerceIn(MIN_PAUSE_MILLIS, MAX_PAUSE_MILLIS)).apply() }

    /** Pause between rewound groups while rolling back to a branch point. */
    var rewindPauseMillis: Long
        get() = preferences.getLong(rewindKey, DEFAULT_REWIND_PAUSE_MILLIS).coerceIn(MIN_REWIND_PAUSE_MILLIS, MAX_REWIND_PAUSE_MILLIS)
        set(value) { preferences.edit().putLong(rewindKey, value.coerceIn(MIN_REWIND_PAUSE_MILLIS, MAX_REWIND_PAUSE_MILLIS)).apply() }

    val autoPause: Set<PlaybackAutoPause> get() = PlaybackAutoPause.entries.filter {
        preferences.getBoolean(triggerPrefix + it.name, it.defaultEnabled)
    }.toSet()

    fun setAutoPause(trigger: PlaybackAutoPause, enabled: Boolean) {
        preferences.edit().putBoolean(triggerPrefix + trigger.name, enabled).apply()
    }

    /** An opponent's branch found while playing live starts the rewind by itself (default: wait for Rewind). */
    var autoRewindLive: Boolean
        get() = preferences.getBoolean(autoRewindLiveKey, false)
        set(value) { preferences.edit().putBoolean(autoRewindLiveKey, value).apply() }
    /** The same when opening (or returning to) a game. */
    var autoRewindOpen: Boolean
        get() = preferences.getBoolean(autoRewindOpenKey, false)
        set(value) { preferences.edit().putBoolean(autoRewindOpenKey, value).apply() }

    fun observe(onChange: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == null || changed == key || changed == rewindKey || changed.startsWith(triggerPrefix) ||
                changed == autoRewindLiveKey || changed == autoRewindOpenKey) onChange()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        const val DEFAULT_PAUSE_MILLIS = 1_000L
        const val MIN_PAUSE_MILLIS = 100L
        const val MAX_PAUSE_MILLIS = 10_000L
        const val DEFAULT_REWIND_PAUSE_MILLIS = 100L
        const val MIN_REWIND_PAUSE_MILLIS = 20L
        const val MAX_REWIND_PAUSE_MILLIS = 2_000L
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

@Composable
internal fun HistoryPlaybackPreferences.rewindPauseState(): State<Long> {
    val state = remember(this) { mutableLongStateOf(rewindPauseMillis) }
    DisposableEffect(this) {
        val stop = observe { state.longValue = rewindPauseMillis }
        state.longValue = rewindPauseMillis
        onDispose { stop() }
    }
    return state
}

@Composable
internal fun HistoryPlaybackPreferences.autoPauseState(): State<Set<PlaybackAutoPause>> {
    val state = remember(this) { mutableStateOf(autoPause) }
    DisposableEffect(this) {
        val stop = observe { state.value = autoPause }
        state.value = autoPause
        onDispose { stop() }
    }
    return state
}

@Composable
internal fun HistoryPlaybackPreferences.autoRewindLiveState(): State<Boolean> = observedState { autoRewindLive }

@Composable
internal fun HistoryPlaybackPreferences.autoRewindOpenState(): State<Boolean> = observedState { autoRewindOpen }

@Composable
private fun <T> HistoryPlaybackPreferences.observedState(read: HistoryPlaybackPreferences.() -> T): State<T> {
    val state = remember(this) { mutableStateOf(read()) }
    DisposableEffect(this) {
        val stop = observe { state.value = read() }
        state.value = read()
        onDispose { stop() }
    }
    return state
}
