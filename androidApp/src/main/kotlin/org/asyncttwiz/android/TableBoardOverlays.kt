package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.*

/** What lies over the board below the chrome: each player's mana pool, the review dim, the playback flash, change and
 * event feedback, and a pending result. Inline, so it stays part of TableSurface's own recomposition scope. */
@Composable
internal inline fun TableBoardOverlays(game: GameSummary, play: PlayState, accountId: String, runtime: TableRuntimeHooks,
    scene: TableScene, rendered: State<TableRenderFrame>) {
    ManaPoolRows(play, scene)
    if (runtime.reviewing) scene.world?.visibleViewport?.let { board ->
        // Purely visual siblings: no pointer handlers, so review gestures and chrome stay usable.
        Box(Modifier.offset(board.x.dp, board.y.dp).size(board.width.dp, board.height.dp)
            .background(Color.Black.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            TimelineCentreText(runtime.opponentBranched, runtime.timelinePlayback ?: TimelinePlaybackStatus.PAUSED, runtime.oldBranch)
        }
    }
    // Outside the review condition: the end of playback (back to live) still flashes the pause.
    scene.world?.visibleViewport?.let { board ->
        PlaybackStatusFlash(runtime.timelinePlayback, Modifier.offset(board.x.dp, board.y.dp).size(board.width.dp, board.height.dp))
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
internal fun ManaPoolRows(play: PlayState, scene: TableScene) {
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
