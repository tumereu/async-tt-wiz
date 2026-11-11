package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The card records for the screens (docs/specs/web-client.md section 3.6): while they install, every control whose action
 * reads them stays visible but greyed with [line], and content that needs them shows [CardRecordsPlaceholder].
 */
sealed interface CardRecords {
    data object Ready : CardRecords
    /** Installing, [percent] of the way. */
    data class Updating(val percent: Int) : CardRecords
    /** The download failed, also when tried once more by itself: Retry. */
    data object Failed : CardRecords
}

/** The line a greyed control and a placeholder say; null when the records are ready. */
fun CardRecords.line(): String? = when (this) {
    CardRecords.Ready -> null
    is CardRecords.Updating -> "Card records updating · $percent%"
    CardRecords.Failed -> "Card records couldn't be downloaded"
}

/** The screens' card records (Ready where nothing installs them: previews, most tests). */
val LocalCardRecords = compositionLocalOf<CardRecords> { CardRecords.Ready }
/** Retry for [CardRecords.Failed]. */
val LocalCardRecordsRetry = compositionLocalOf<(() -> Unit)?> { null }

/** Whether controls that read card records can be used. */
@Composable
fun cardRecordsReady(): Boolean = LocalCardRecords.current == CardRecords.Ready

/** The line under greyed controls ("Card records updating · 43%"), with Retry after a failure; nothing when ready. */
@Composable
fun CardRecordsNote(modifier: Modifier = Modifier) {
    val records = LocalCardRecords.current
    val line = records.line() ?: return
    val retry = LocalCardRecordsRetry.current
    Row(modifier.testTag("card-records-note"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(line, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (records == CardRecords.Failed && retry != null) TextButton(onClick = retry) { ButtonLabel("refresh", "Retry") }
    }
}

/** In place of content that needs card records (a deck's cards, a game's table) while they install. */
@Composable
fun CardRecordsPlaceholder(modifier: Modifier = Modifier) {
    val records = LocalCardRecords.current
    val retry = LocalCardRecordsRetry.current
    Box(modifier.testTag("card-records-placeholder"), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(16.dp).widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EntryIcon(null, if (records == CardRecords.Failed) "error" else "download")
                Text(records.line() ?: "", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                if (records == CardRecords.Failed && retry != null) TextButton(onClick = retry) { ButtonLabel("refresh", "Retry") }
            }
        }
    }
}

/** [content] once card records are ready, else [CardRecordsPlaceholder] in its place. */
@Composable
fun NeedsCardRecords(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (cardRecordsReady()) content() else CardRecordsPlaceholder(modifier)
}

/** A screen whose content needs card records (a saved deck's or cube's cards) while they install: its header and the
 * placeholder; Back leaves it ([back]). */
@Composable
fun CardRecordsScreen(title: String, navigation: (() -> Unit)?, back: () -> Unit) {
    org.asyncttwiz.appui.BackHandler(onBack = back)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, navigation)
        CardRecordsPlaceholder(Modifier.weight(1f).fillMaxWidth().padding(16.dp))
    }
}
