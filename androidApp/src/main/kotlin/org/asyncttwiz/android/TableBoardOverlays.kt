package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.*

/** What lies over the board below the chrome: each player's mana pool, the review dim, the playback flash, change and
 * event feedback, and a pending result. Inline, so it stays part of TableSurface's own recomposition scope. */
@Composable
internal inline fun TableBoardOverlays(game: GameSummary, play: TableBoard, accountId: String, runtime: TableRuntimeHooks,
    scene: TableScene, rendered: State<TableRenderFrame>) {
    ManaPoolRows(play, scene)
    if (runtime.reviewing) scene.world?.visibleViewport?.let { board ->
        // Purely visual siblings: no pointer handlers, so review gestures and chrome stay usable.
        Box(Modifier.offset(board.x.dp, board.y.dp).size(board.width.dp, board.height.dp)
            .background(Color.Black.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            TimelineCentreText(runtime.timelinePlayback ?: TimelinePlaybackStatus.PAUSED)
        }
    }
    // HIS-112: a play shown in the middle of the table takes the place of the wait's words and the Stack line
    val showing = LocalEngineRoutes.current?.showing() == true
    // TRN-066 (owner, 2026-11-09): the wait, in the band under the other player's hand; purely visual (no pointer handlers),
    // so the board stays usable
    if (!runtime.reviewing && !showing) runtime.waitingLine?.let { line -> WaitBand(scene, accountId) {
        Text(line, Modifier.semantics { contentDescription = line }, color = Color.White,
            style = WaitBandText, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    } }
    // STK-057 (owner playtest 2026-11-07, 2026-11-09): what the other player's top Stack entry is while you hold priority over it,
    // in the wait's place (they never show together) and look; purely visual. Live and in catch-up playback, not under the
    // catch-up's centre text or in timeline mode
    if (!runtime.reviewing && runtime.waitingLine == null && runtime.centreAction == null && !showing) {
        val names = remember(game) { gameNames(game) }
        val pieces = remember(IdentityKey(play.board), accountId, names) { StackLine.pieces(play, accountId, names) }
        if (pieces != null) WaitBand(scene, accountId) {
            val stackLine = pieces.plain()
            val (main, detail) = remember(pieces) { StackLine.split(pieces) }
            // STK-057 (owner, 2026-11-09): who did what a little bigger, what it does small under it, up to 8 lines
            Column(Modifier.testTag("stack-line").clearAndSetSemantics { contentDescription = stackLine },
                horizontalAlignment = Alignment.CenterHorizontally) {
                CardText(main.tintedOnDark(accountId), color = Color.White, style = WaitBandText, maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (detail.isNotEmpty()) CardText(detail.tintedOnDark(accountId), color = Color.White, style = WaitBandDetailText,
                    maxLines = 8, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
    // HIS-094: the catch-up's centre text, a tap resumes playback
    if (!runtime.reviewing) runtime.centreAction?.let { action -> scene.world?.visibleViewport?.let { board ->
        Box(Modifier.offset(board.x.dp, board.y.dp).size(board.width.dp, board.height.dp), contentAlignment = Alignment.Center) {
            TableCentreButton(action)
        }
    } }
    // Outside the review condition: the end of playback (back to live) still flashes the pause.
    scene.world?.visibleViewport?.let { board ->
        PlaybackStatusFlash(runtime.timelinePlayback, Modifier.offset(board.x.dp, board.y.dp).size(board.width.dp, board.height.dp))
        // HIS-021, HIS-112, HIS-113: a held finger pausing what the view is waiting on
        ViewPausedIcon(LocalEngineRoutes.current?.viewPaused() == true, Modifier.offset(board.x.dp, board.y.dp).size(board.width.dp, board.height.dp))
    }
    TableChangeOverlay(runtime.changes, accountId, scene.appearance,
        item = { id -> rendered.value.items[id] })
    ResultActions.pendingPresentation(game, play, accountId)?.let { result ->
        val board = scene.world?.visibleViewport
        Box(if (board == null) Modifier.fillMaxSize() else
            Modifier.offset(board.x.dp, board.y.dp).size(board.width.dp, board.height.dp),
            contentAlignment = Alignment.Center) {
            ResultTablePanel(result, runtime.resultControls, Modifier.padding(8.dp))
        }
    }
}

/** TRN-066, STK-057 (owner, 2026-11-09): the band's main line, a little bigger than before. */
internal val WaitBandText = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium,
    textAlign = androidx.compose.ui.text.style.TextAlign.Center)

/** STK-057 (owner, 2026-11-09): the ability's or effect's text under the band's main line, small. */
internal val WaitBandDetailText = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, lineHeight = 14.sp,
    textAlign = androidx.compose.ui.text.style.TextAlign.Center)

/** How dark the band is in its middle, fading out toward both sides. */
private val WaitBandShade = Color.Black.copy(alpha = .62f)

/**
 * TRN-066, STK-057 (owner, 2026-11-09): the one place the wait and the other player's top Stack entry say what goes on: a
 * semi-transparent dark band right under the other player's hand, edge to edge, fading out toward both sides, its words kept
 * in its dark middle. Purely visual: it has no pointer handlers, so touches go through to the table under it.
 */
@Composable
internal fun WaitBand(scene: TableScene, viewer: String, content: @Composable () -> Unit) {
    val hand = scene.items.firstOrNull { it.kind == TableItemKind.HAND && it.owner != viewer }?.bounds ?: return
    Box(Modifier.offset(0.dp, hand.bottom.dp).fillMaxWidth()
        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(0f to Color.Transparent, .2f to WaitBandShade,
            .8f to WaitBandShade, 1f to Color.Transparent))
        .testTag("wait-band").padding(horizontal = 48.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center) { content() }
}

/** The game's players' names by account id. */
internal fun gameNames(game: GameSummary): Map<String, String> =
    mapOf(game.inviter.accountId to game.inviter.username, game.recipient.accountId to game.recipient.username)

/** The order a mana pool lists its mana in: the colours in WUBRG order, then colourless. */
private val POOL_ORDER = listOf("W", "U", "B", "R", "G", "C")

/** The most mana a pool shows symbol by symbol; a bigger pool shows each kind once with its count ("×4"). */
internal const val POOL_SYMBOLS = 8

/** Where the mana pool row of [field] sits (the line between its land row and the next row, TBL-004; the viewer's
 * field, the last of [fields], has its land row at the bottom), or null when that line is out of view. */
internal fun manaPoolLine(fields: List<TableField>, field: TableField): Float? {
    val line = if (field === fields.last()) field.gridBounds.bottom - field.rowHeight else field.gridBounds.y + field.rowHeight
    return line.takeIf { it >= field.bounds.y && it <= field.bounds.bottom }
}

/**
 * Owner playtest 2026-11-03 #5 (MTG Arena): each player's mana pool as a compact row of mana symbols (drawn as in card
 * text, [CardSymbol]) on the line between their land row and the row next to it, at the left of their battlefield;
 * nothing while the pool is empty. Purely visual: no pointer handlers.
 */
@Composable
internal fun ManaPoolRows(play: TableBoard, scene: TableScene) {
    for (field in scene.fields) {
        val pool = play.board.players.firstOrNull { it.accountId == field.owner }?.manaPool.orEmpty()
        if (pool.isEmpty()) continue
        val line = manaPoolLine(scene.fields, field) ?: continue
        val kinds = pool.map { it.color }.sortedBy { POOL_ORDER.indexOf(it).let { i -> if (i < 0) POOL_ORDER.size else i } }
        val counted = kinds.groupingBy { it }.eachCount()
        val description = "Mana pool: " + counted.entries.joinToString { (kind, n) -> "$n {$kind}" }
        val shape = RoundedCornerShape(10.dp)
        Row(Modifier.offset((field.bounds.x + 6f).dp, (line - 10f).dp).height(20.dp)
            .background(Color.White.copy(alpha = .9f), shape).border(1.dp, Color(0xffb7bcbf), shape)
            .padding(horizontal = 3.dp, vertical = 2.dp).semantics(mergeDescendants = true) { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.CenterVertically) {
            if (kinds.size <= POOL_SYMBOLS) for (kind in kinds) Box(Modifier.size(16.dp)) { CardSymbol(kind) }
            else for ((kind, n) in counted) {
                Box(Modifier.size(16.dp)) { CardSymbol(kind) }
                Text("×$n", Modifier.padding(start = 1.dp, end = 3.dp), color = MenuInk, fontSize = 11.sp, lineHeight = 12.sp,
                    fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * HIS-094: the large text in the board's centre that a tap acts on ("Bob has made moves · Tap to watch"), with a play
 * icon, white over the board.
 */
@Composable
internal fun TableCentreButton(action: TableCentreAction) {
    val shape = RoundedCornerShape(14.dp)
    androidx.compose.material3.Surface(onClick = action.run, shape = shape, color = Color.White.copy(alpha = .94f), shadowElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xffb7bcbf)),
        modifier = Modifier.padding(horizontal = 24.dp).testTag("centre-action").semantics { contentDescription = action.text }) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.PlayArrow, null, Modifier.size(28.dp),
                tint = Color(0xff5b896c))
            Text(action.text, color = MenuInk, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
