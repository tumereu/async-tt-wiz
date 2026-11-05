package org.asyncttwiz.android

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.asyncttwiz.protocol.*
import java.util.concurrent.Executor

/** The short text of the group whose last shown event is [cursorId]. [serial] tells repeated texts apart. */
internal data class EventTextChip(val cursorId: String, val text: String, val serial: Long, val shownAt: Long)

internal const val EVENT_TEXT_MIN_MILLIS = 2_000L
internal fun eventTextLifetimeMillis(pauseMillis: Long) = maxOf(EVENT_TEXT_MIN_MILLIS, pauseMillis)
/** Only the final disappearance animates; showing and replacing are instant. */
internal const val EVENT_TEXT_FADE_OUT_MILLIS = 450


/** Personal presentation only: what the shown board's latest group was. Timeline mode shows every
 * group it shows (also the viewer's own); live mode only an opponent's new group. A chip always
 * belongs to the board currently shown: moving elsewhere drops it at once, before the next is ready. */
internal class EventTextFeed(worker: Executor, private val post: (() -> Unit) -> Unit,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    var chip by mutableStateOf<EventTextChip?>(null); private set
    var enabled = true
        set(value) { field = value; if (!value) { chip = null; latest = ++epoch } }
    private val work = LatestReview(worker)
    private var shownCursor: String? = null
    private var shownMode: HistoryMode? = null
    private var epoch = 0L
    @Volatile private var latest = 0L


    /** On engine state (HIS-075, HIS-076): [text] of the event shown at [cursor] (the other player's newest); [always]:
     * shown with Event text off too (§29.4: what an undo took back). */
    fun post(cursor: String, text: String, always: Boolean = false) {
        val generation = ++epoch; latest = generation
        shownCursor = cursor
        if ((enabled || always) && text.isNotBlank()) chip = EventTextChip(cursor, text, generation, clock())
    }

    /** On engine state: the board moved to [cursor] with nothing to tell (the chip of another board goes at once). */
    fun moved(cursor: String) { if (chip?.cursorId != cursor) { chip = null; epoch++; latest = epoch } ; shownCursor = cursor }

    /** The chip's own clock: it fades after its lifetime whether or not playback is running. */
    fun remainingMillis(pauseMillis: Long, now: Long = clock()): Long =
        chip?.let { it.shownAt + eventTextLifetimeMillis(pauseMillis) - now }?.coerceAtLeast(0) ?: 0
    fun expire(serial: Long) { if (chip?.serial == serial) chip = null }

    fun clear() { epoch++; latest = epoch; chip = null; shownCursor = null; shownMode = null; work.clear() }
}

/** Device-local, account-scoped like the other playback preferences. */
internal class EventTextPreferences(context: Context, accountId: String) {
    private val preferences = context.applicationContext.getSharedPreferences("history-playback", Context.MODE_PRIVATE)
    private val key = "event-text/$accountId"
    var enabled: Boolean
        get() = preferences.getBoolean(key, true)
        set(value) { preferences.edit().putBoolean(key, value).apply() }
    fun observe(onChange: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed -> if (changed == null || changed == key) onChange() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

@Composable
internal fun rememberEventTextEnabled(accountId: String): Pair<State<Boolean>, EventTextPreferences> {
    val context = LocalContext.current
    val preferences = remember(context, accountId) { EventTextPreferences(context, accountId) }
    val state = remember(preferences) { mutableStateOf(preferences.enabled) }
    DisposableEffect(preferences) {
        val stop = preferences.observe { state.value = preferences.enabled }
        state.value = preferences.enabled
        onDispose { stop() }
    }
    return state to preferences
}

@Composable
internal fun EventTextSetting(accountId: String) {
    val (state, preferences) = rememberEventTextEnabled(accountId)
    val enabled by state
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("event-text-setting")
        .toggleable(enabled, role = Role.Switch, onValueChange = { preferences.enabled = it }),
        verticalAlignment = Alignment.CenterVertically) {
        SettingsLabel("Event text", "Briefly show what just happened at the top of the table",
            Modifier.weight(1f).padding(end = 12.dp, top = 4.dp, bottom = 4.dp))
        Switch(checked = enabled, onCheckedChange = null)
    }
}

/** How long the event text's warning flash takes to fade back (TBL-118: once, short). */
internal const val EVENT_TEXT_FLASH_MILLIS = 450

/** The event text's own colour. */
private val EventTextFill = Color(0xf0253d44)

/**
 * Overlays the table's top row. Purely visual (no pointer handlers), so taps reach the controls below. [status]: the
 * other player's open choice in the wait's words (HIS-075, TRN-066), shown instead of the event chips, also with Event
 * text off, without fading while it lasts; each rise of [flashes] (a refusal meanwhile, TBL-118) flashes it once in the
 * warning colour.
 */
@Composable
internal fun EventTextOverlay(feed: EventTextFeed, pauseMillis: Long, modifier: Modifier = Modifier,
    status: String? = null, flashes: Int = 0) {
    val chip = feed.chip
    LaunchedEffect(chip?.serial, pauseMillis) {
        if (chip == null) return@LaunchedEffect
        delay(feed.remainingMillis(pauseMillis))
        feed.expire(chip.serial)
    }
    val flash = remember { Animatable(0f) }
    val firstFlashes = remember { flashes }
    val record = LocalShownTexts.current
    LaunchedEffect(flashes) {
        if (flashes != firstFlashes && status != null) {
            // The flash shows the status again, as a warning
            record(status)
            flash.snapTo(1f); flash.animateTo(0f, tween(EVENT_TEXT_FLASH_MILLIS))
        }
    }
    val warning = MaterialTheme.colorScheme.error
    val shownChip = status?.let { EventTextChip(STATUS_CURSOR, it, -1, 0) } ?: chip
    RecordShown(shownChip?.text, shownChip)
    Box(modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
        InstantShowFadeOut(shownChip) { shown ->
            val fill = if (shown.cursorId == STATUS_CURSOR) androidx.compose.ui.graphics.lerp(EventTextFill, warning, flash.value)
                else EventTextFill
            Box(Modifier.padding(horizontal = 8.dp, vertical = 3.dp).widthIn(max = 420.dp)
                .background(fill, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp)
                .testTag("event-text")
                .clearAndSetSemantics { contentDescription = shown.text; liveRegion = LiveRegionMode.Polite },
                contentAlignment = Alignment.Center) {
                Text(shown.text, color = Color.White, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}

/** The cursor of [EventTextOverlay]'s status (no event's). */
private const val STATUS_CURSOR = "status"

/** Shows [value] at once and swaps a new value in at once; only its going away (to null) fades out.
 * Purely visual: no pointer handlers. */
@Composable
internal fun <T : Any> InstantShowFadeOut(value: T?, modifier: Modifier = Modifier,
    fadeMillis: Int = EVENT_TEXT_FADE_OUT_MILLIS, content: @Composable (T) -> Unit) {
    var leaving by remember { mutableStateOf<T?>(null) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(value) {
        if (value != null) { leaving = value; alpha.snapTo(1f) }
        else if (leaving != null) { alpha.animateTo(0f, tween(fadeMillis)); leaving = null }
    }
    val shown = value ?: leaving ?: return
    Box(modifier.graphicsLayer { this.alpha = if (value != null) 1f else alpha.value }) { content(shown) }
}
