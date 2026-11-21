package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The game log (HIS-239): the timeline's events as lines of text, oldest at the top and the newest at the bottom, where it
 * opens; each line its event's icon in its author's colour (HIS-005) and its text (HIS-091) with the players' names and
 * the cards named in their players' colours (HIS-240); where the turn or step changed, the timeline's label ("T3 ·
 * Combat") in the colour of the player whose turn it is (HIS-170). A tap on a line opens timeline mode at that event
 * ([onPick], HIS-241). Scrolling to the top asks for older events ([onTop], HIS-172) while [loadingOlder] shows a spinner.
 */
@Composable
fun GameLogPanel(lines: List<GameLogLine>, viewer: String, onDismiss: () -> Unit, onPick: (String) -> Unit,
    loadingOlder: Boolean = false, onTop: () -> Unit = {}) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        MenuWindowDimming()
        BoxWithConstraints(Modifier.fillMaxSize().tableTapRelay().pointerInput(onDismiss) {
            detectTapGestures(onTap = { onDismiss() })
        }.windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(8.dp).heightIn(max = minOf(820.dp, maxHeight - 16.dp))
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .semantics { paneTitle = "Game log" }.testTag("game-log"),
                color = Color.White, contentColor = MenuInk, shape = RoundedCornerShape(10.dp), shadowElevation = 12.dp) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Game log", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        ManualActionSquare("Close", "", bordered = false, onClick = onDismiss)
                    }
                    Spacer(Modifier.height(4.dp))
                    // Newest at the bottom, where it opens and stays as new events come (a reversed list)
                    val newestFirst = remember(IdentityKey(lines)) { lines.asReversed() }
                    val list = rememberLazyListState()
                    val top by remember(list) { derivedStateOf {
                        list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 1 } ?: false } }
                    val ask by rememberUpdatedState(onTop)
                    LaunchedEffect(top, lines.size) { if (top) ask() }
                    LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth(), state = list, reverseLayout = true) {
                        items(newestFirst, key = { it.event.id }) { line -> GameLogRow(line, viewer, onPick) }
                        if (loadingOlder) item(key = "older") {
                            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(20.dp).testTag("game-log-older"), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One line of the game log, after the turn and step label of a change before it. */
@Composable
private fun GameLogRow(line: GameLogLine, viewer: String, onPick: (String) -> Unit) {
    val event = line.event
    Column(Modifier.fillMaxWidth()) {
        event.separator?.let { s ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 1.dp).testTag("game-log-separator"),
                verticalAlignment = Alignment.CenterVertically) {
                val colour = historyAuthorColor(s.active, viewer)
                Text(s.label, color = colour, fontSize = 12.sp, fontWeight = if (s.newTurn) FontWeight.Bold else FontWeight.Medium)
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f).height(if (s.newTurn) 1.5.dp else 1.dp).background(SeparatorInk.copy(alpha = if (s.newTurn) .5f else .25f)))
            }
        }
        val text = remember(line.pieces) { line.pieces.plain() }
        // HIS-239 (owner 2026-11-21, E11): tight rows, about 24 dp each, so a screen holds more of the game
        Row(Modifier.fillMaxWidth().heightIn(min = 24.dp).clickable { onPick(event.id) }.padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag("game-log-line").clearAndSetSemantics { contentDescription = text },
            verticalAlignment = Alignment.CenterVertically) {
            CompositionLocalProvider(LocalContentColor provides historyAuthorColor(event.author, viewer)) { HistoryEventIcon(event.icon) }
            Spacer(Modifier.width(8.dp))
            val tinted = remember(line.pieces, viewer) { line.pieces.tinted(viewer) }
            CardText(tinted, style = MaterialTheme.typography.bodyMedium, color = MenuInk)
        }
    }
}
