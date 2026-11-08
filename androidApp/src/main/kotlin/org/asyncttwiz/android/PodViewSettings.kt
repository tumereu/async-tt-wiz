package org.asyncttwiz.android

import android.content.Context
import android.content.res.Configuration
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal val PodPickColumnRange = 1..15
internal val PodBuildColumnRange = 2..10
internal val PodBuildSideBySideRange = 1..3

/** Personal per-orientation Build count setting (columns, or cards side by side within a column);
 * shared by the view settings panel and the Build view. */
@Stable
class PodBuildColumns internal constructor(
    private val preferences: SharedPreferences, private val key: String,
    internal val range: IntRange = PodBuildColumnRange, default: Int = 7,
) {
    var count by mutableIntStateOf(preferences.getInt(key, default).coerceIn(range))
        private set
    fun choose(value: Int) { count = value.coerceIn(range); preferences.edit().putInt(key, count).apply() }
}

@Composable
internal fun rememberPodBuildColumns(): PodBuildColumns {
    val context = LocalContext.current.applicationContext
    val orientation = LocalConfiguration.current.orientation
    val preferences = remember(context) { context.getSharedPreferences("pod-build", Context.MODE_PRIVATE) }
    return remember(preferences, orientation) { PodBuildColumns(preferences, "columns-$orientation") }
}

/** How many cards sit side by side inside each Build column; 1 by default in both orientations. */
@Composable
internal fun rememberPodBuildSideBySide(): PodBuildColumns {
    val context = LocalContext.current.applicationContext
    val orientation = LocalConfiguration.current.orientation
    val preferences = remember(context) { context.getSharedPreferences("pod-build", Context.MODE_PRIVATE) }
    return remember(preferences, orientation) {
        PodBuildColumns(preferences, "side-by-side-$orientation", PodBuildSideBySideRange, 1)
    }
}

/**
 * The draft history's own cards per row (DRF-165), per device and orientation: 1 up to [packSize], by default 8 in
 * portrait and 15 in landscape, capped at the pack size.
 */
@Composable
internal fun rememberPodHistoryColumns(packSize: Int): PodBuildColumns {
    val context = LocalContext.current.applicationContext
    val orientation = LocalConfiguration.current.orientation
    val preferences = remember(context) { context.getSharedPreferences("pod-pick", Context.MODE_PRIVATE) }
    val size = packSize.coerceAtLeast(1)
    return remember(preferences, orientation, size) {
        PodBuildColumns(preferences, "history-columns-$orientation", 1..size,
            (if (orientation == Configuration.ORIENTATION_LANDSCAPE) 15 else 8).coerceAtMost(size))
    }
}

internal val DeckViewColumnRange = 1..10

/** Personal per-orientation tile columns of a visual deck view. Game preparation ([DeckViewScreen.PREPARATION]),
 * saved-deck viewing ([DeckViewScreen.SAVED]) and saved-cube viewing ([DeckViewScreen.CUBE]) keep separate counts,
 * as the draft's pick and Build views do. Decks start at 3 columns; cubes (360–540 cards) at 5 in portrait, 8 in landscape. */
internal enum class DeckViewScreen(internal val key: String, private val portrait: Int, private val landscape: Int) {
    PREPARATION("preparation", 3, 3), SAVED("saved", 3, 3), CUBE("cube", 5, 8),
    /** The card list editor's Cards tab (decks, cubes), Prepare cube and the editor's search results. */
    EDITOR("editor", 3, 3), CUBE_EDITOR("cube-editor", 5, 8), CUBE_PREPARATION("cube-preparation", 5, 8), SEARCH("search", 3, 5);
    fun defaultColumns(orientation: Int) = if (orientation == Configuration.ORIENTATION_LANDSCAPE) landscape else portrait
}

@Composable
internal fun rememberDeckViewColumns(screen: DeckViewScreen): PodBuildColumns {
    val context = LocalContext.current.applicationContext
    val orientation = LocalConfiguration.current.orientation
    val preferences = remember(context) { context.getSharedPreferences("deck-view", Context.MODE_PRIVATE) }
    return remember(preferences, orientation, screen) {
        PodBuildColumns(preferences, deckViewColumnsKey(screen, orientation), DeckViewColumnRange, screen.defaultColumns(orientation))
    }
}

internal fun deckViewColumnsKey(screen: DeckViewScreen, orientation: Int) = "${screen.key}-columns-$orientation"

/** The Build view's columns control, inline: fewer, the count, more. */
@Composable
internal fun DeckViewColumnsControl(columns: PodBuildColumns) =
    PodColumnControl("${columns.count}", "Columns", columns.count, columns.range, columns::choose)

/** Header cog opening a small floating panel with every pod view's layout setting; values apply live. */
@Composable
internal fun PodViewSettingsButton(pick: PodPickColumns?, build: PodBuildColumns,
    sideBySide: PodBuildColumns = rememberPodBuildSideBySide(), history: PodBuildColumns? = null) {
    var open by remember { mutableStateOf(false) }
    Box {
        HoldTip("View settings") { IconButton({ open = true }, Modifier.size(48.dp)) { EntryIcon("View settings", "settings") } }
        DropdownMenu(open, { open = false }, Modifier.testTag("pod-view-settings")) {
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (pick != null) PodViewSettingRow("Draft · cards per row") {
                    val value = pick.preferred.coerceIn(PodPickColumnRange)
                    PodColumnControl("$value", "Cards per row", value, PodPickColumnRange, pick::choose)
                }
                if (history != null) PodViewSettingRow("History · cards per row") {
                    PodColumnControl("${history.count}", "History cards per row", history.count, history.range, history::choose)
                }
                PodViewSettingRow("Build · columns") {
                    PodColumnControl("${build.count}", "Columns", build.count, PodBuildColumnRange, build::choose)
                }
                PodViewSettingRow("Build · cards side by side") {
                    PodColumnControl("${sideBySide.count}", "Cards side by side", sideBySide.count,
                        PodBuildSideBySideRange, sideBySide::choose)
                }
            }
        }
    }
}

@Composable
private fun PodViewSettingRow(label: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.padding(end = 12.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
        Spacer(Modifier.weight(1f))
        control()
    }
}
