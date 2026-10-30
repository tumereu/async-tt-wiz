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

/** The shown group's text, from the board shown at its last event: each earlier event of the group is
 * stepped back once from there (no replay). The texts are the timeline/tree-dialog texts, so they never
 * name what [viewer] could not see. A group longer than [limit] reads as its last event. */
internal fun eventGroupText(nodes: Map<String, GameplayMutation>, shown: PlayState, viewer: String?,
    usernames: Map<String, String>, limit: Int = 512): HistoryText? {
    val last = nodes[shown.currentNodeId] ?: return null
    val group = last.bookkeeping?.toGroup
    val ids = ArrayList<String>()
    val texts = HashMap<String, HistoryText>()
    var after: PlayState? = shown
    var node = last
    while (true) {
        if (ids.size >= limit) return texts.getValue(last.id)
        val before = after?.let { GameplayHistory.unapply(it, node) }
        ids += node.id
        texts[node.id] = historyText(node, before, viewer, usernames)
        if (group == null || node.id == group.firstNodeId) break
        node = nodes[node.parentId]?.takeIf { it.bookkeeping?.toGroup?.id == group.id } ?: break
        after = before
    }
    ids.reverse()
    return historyGroupText(ids, last.id, texts)
}

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

    /** Called once a board is actually displayed; repeated calls for the same board are ignored. */
    fun displayed(cursor: String?, state: PlayState?, mode: HistoryMode, nodes: Map<String, GameplayMutation>,
        viewer: String?, usernames: Map<String, String>, isAncestor: (String, String) -> Boolean) {
        val previous = shownCursor; val previousMode = shownMode
        shownCursor = cursor; shownMode = mode
        if (cursor == previous) return
        val generation = ++epoch; latest = generation
        if (chip?.cursorId != cursor) chip = null
        if (!enabled || cursor == null || state == null || state.currentNodeId != cursor) return
        val node = nodes[cursor] ?: return // The initial state has nothing to tell.
        if (mode == HistoryMode.LIVE && (previousMode != HistoryMode.LIVE || previous == null ||
                node.author == viewer || !isAncestor(previous, cursor))) return
        work.submit("event-text") {
            if (latest != generation) return@submit
            val text = eventGroupText(nodes, state, viewer, usernames)?.description?.takeIf { it.isNotBlank() } ?: return@submit
            post { if (generation == epoch && shownCursor == cursor) chip = EventTextChip(cursor, text, generation, clock()) }
        }
    }

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
        Column(Modifier.weight(1f)) {
            Text("Event text", style = MaterialTheme.typography.titleSmall)
            Text("Briefly show what just happened at the top of the table", style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = enabled, onCheckedChange = null)
    }
}

/** Overlays the table's top row. Purely visual (no pointer handlers), so taps reach the controls below. */
@Composable
internal fun EventTextOverlay(feed: EventTextFeed, pauseMillis: Long, modifier: Modifier = Modifier) {
    val chip = feed.chip
    LaunchedEffect(chip?.serial, pauseMillis) {
        if (chip == null) return@LaunchedEffect
        delay(feed.remainingMillis(pauseMillis))
        feed.expire(chip.serial)
    }
    Box(modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
        InstantShowFadeOut(chip) { shown ->
            Box(Modifier.padding(horizontal = 8.dp, vertical = 3.dp).widthIn(max = 420.dp)
                .background(Color(0xf0253d44), RoundedCornerShape(8.dp))
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
