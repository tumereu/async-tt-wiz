package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.*
import kotlin.math.ceil
import kotlin.math.floor

private const val PANEL_HEADER = 28f
data class CardPanelRow(val entry: ContentsEntry, val copies: Int, val bounds: TableBounds,
    val cardIds: List<String> = listOf(entry.cardId))
data class CardPanelTypeHeader(val title: String, val bounds: TableBounds)
data class CardPanelGeometry(val display: ContentsDisplay, val bounds: TableBounds,
    val viewport: TableViewport, val rows: List<CardPanelRow>, val columns: Int = 1,
    val localZone: PlayZone? = null, val typeHeaders: List<CardPanelTypeHeader> = emptyList(), val cardScale: Float = 1f) {
    val title get() = localZone?.let(::moveZoneLabel) ?: display.title
    val closeBounds get() = TableBounds(bounds.right - 24f, bounds.y + 2, 24f, 24f)
}

/** Concealed copies are never grouped using hidden names (or collapsed into one back); copies with different counters
 * ([countersOf], ZON-091) keep their own places. */
fun panelGroups(entries: List<ContentsEntry>, countersOf: (String) -> List<TypedCounter> = { emptyList() }): List<List<ContentsEntry>> =
    entries.groupBy { (if (it.faceVisible) it.name else it.cardId) to countersOf(it.cardId) }.values.toList()

fun panelColumnCount(maxColumns: Int): Int = maxColumns.coerceIn(1, 3)

/** Partial rows start at the left, including each type section's last row. */
@Suppress("UNUSED_PARAMETER")
fun panelColumn(index: Int, count: Int, columns: Int): Int {
    return index % columns
}

fun TableScene.localPanelOutside(point: TablePoint): Boolean =
    cardPanels.any { it.localZone != null && !it.bounds.contains(point) }

fun localPanelDisplay(play: TableBoard, viewer: String, ui: TableLayoutState): ContentsDisplay? {
    val pile = play.board.containers.singleOrNull { it.id == ui.localPileId && it.zone in listOf(PlayZone.GRAVEYARD, PlayZone.EXILE) }
        ?: return null
    if (pile.cards.isEmpty()) return null
    return ContentsDisplay("local:${pile.id}", viewer, pile.id, ContentsSelection.ALL, pile.cards.size, false, pile.cards)
}

/** Layout depends only on the current materialized board and personal viewport, never history. */
fun cardPanelGeometry(play: TableBoard, viewer: String, ui: TableLayoutState, area: TableBounds,
    scale: Float = 1f): List<CardPanelGeometry> {
    // I-D52: only the viewer's own look into a Graveyard or Exile (ZON-016); no shared search, look or reveal panels
    val local = localPanelDisplay(play, viewer, ui)
    val displays = listOfNotNull(local)
    var x = area.x
    val columnPitch = 28f * scale + 4f
    val rowPitch = 40f * scale + 12f
    val availablePerPanel = (area.width - (displays.size - 1).coerceAtLeast(0) * 4f) / displays.size.coerceAtLeast(1)
    return displays.map { display ->
        val localZone = if (display == local) play.board.containers.firstOrNull { it.id == display.containerId }?.zone
            ?: null.also { Faults.report("CardPanel", "panel of a missing container", mapOf("container" to display.containerId)) } else null
        val entries = if (localZone != null) contentsEntries(play, PileContents(display.containerId, "", display.cardIds), viewer)
            else contentsEntries(play, display, viewer)
        // Picking targets: each card is its own object, so none are grouped
        val grouped = if (localZone != null && !ui.localPileTargets) panelGroups(entries) { id ->
                play.board.cards.firstOrNull { it.id == id }?.typedCounters?.filter { it.count != 0 }.orEmpty() +
                    // ZON-094: a plotted copy doesn't lie with an unplotted one
                    play.values?.computed?.exileStates?.get(id).orEmpty().map { org.asyncttwiz.protocol.TypedCounter(it, 1, 0) } }
            else if (localZone != null) entries.map { listOf(it) }
            else if (display.search && display.author == viewer) entries.groupBy {
                it.name to (it.cardId in display.revealedIds)
            }.values.toList() else entries.map { listOf(it) }
        val typed = localZone != null || display.search && display.author == viewer
        val sections = if (typed) grouped.groupBy { it.first().category }.entries
            .sortedBy { contentsCategoryRank(it.key) }.map { it.value } else listOf(grouped)
        val columns = minOf(panelColumnCount(if (displays.size == 1) 3 else 2),
            ((availablePerPanel - 8f) / columnPitch).toInt().coerceAtLeast(1))
        val width = maxOf(64f, columns * columnPitch + 8f)
        val bounds = TableBounds(x, area.y, width, area.height)
        x = bounds.right + 4f
        val top = PANEL_HEADER
        val portBounds = TableBounds(bounds.x, bounds.y + top, width, (bounds.height - top).coerceAtLeast(0f))
        val content = sections.sumOf { ceil(it.size.toDouble() / columns).toInt() } * rowPitch +
            if (typed) sections.size * 20f else 0f
        val portId = "cardPanel/${display.id}"
        val scroll = (ui.scrollY[portId] ?: 0f).coerceIn(0f, (content - portBounds.height).coerceAtLeast(0f))
        val port = TableViewport(portId, portBounds, width, 0f, contentHeight = content, scrollY = scroll)
        val rows = mutableListOf<CardPanelRow>()
        val headers = mutableListOf<CardPanelTypeHeader>()
        var y = portBounds.y - scroll
        for (section in sections) {
            if (typed && section.isNotEmpty()) {
                val b = TableBounds(bounds.x + 2f, y, width - 4f, 20f)
                if (b.intersection(portBounds) != null) headers += CardPanelTypeHeader(
                    "${section.first().first().category} (${section.sumOf { it.size }})", b)
                y += 20f
            }
            section.forEachIndexed { n, group ->
                val b = TableBounds(bounds.x + (width - columns * columnPitch) / 2 + panelColumn(n, section.size, columns) * columnPitch + 2,
                    y + n / columns * rowPitch + 2, 28f * scale, 40f * scale)
                if (b.intersection(portBounds) != null) rows += CardPanelRow(group.first(), group.size, b, group.map { it.cardId })
            }
            y += ceil(section.size.toDouble() / columns).toInt() * rowPitch
        }
        CardPanelGeometry(display, bounds, port, rows, columns, localZone, headers, scale)
    }
}

fun cardPanels(scene: TableScene, play: TableBoard, viewer: String, panels: List<CardPanelGeometry>): TableScene {
    if (panels.isEmpty()) return scene
    val cards = play.board.cards.associateBy { it.id }
    val definitions = play.definitions.associateBy { it.digest }
    val containers = play.board.containers.associateBy { it.id }
    val exileStates = play.values?.computed?.exileStates.orEmpty()
    val items = panels.flatMap { panel -> panel.rows.mapNotNull { row ->
        val card = cards[row.entry.cardId] ?: return@mapNotNull null
        val definition = definitions[card.definition]?.definition
        val face = definition?.faces?.firstOrNull { it.faceId == card.faceId }
        val art = if (!row.entry.faceVisible || face == null || face.scryfallId == null && face.imageUri == null) null else
            face.tableArtwork(definition.faces.indexOf(face))?.forTable()
        // ZON-091: counters on a card in exile (a suspended card's time counters) show as on the battlefield: bubbles (TBL-135)
        val counters = org.asyncttwiz.automatum.ComputedBoard.shownCounters(card.typedCounters)
        TableItem("card/${card.id}", row.bounds,
            "${panel.title} panel: ${row.entry.name}${if (row.copies > 1) ", ×${row.copies}" else ""}" +
                counters.joinToString("") { ", ${it.words}" }, if (row.entry.faceVisible) row.entry.name else "",
            owner = card.owner, zone = containers[card.containerId]?.zone, kind = TableItemKind.CARD,
            viewport = panel.viewport.id, cardId = card.id, covered = !row.entry.faceVisible,
            artwork = art,
            corner = if (row.entry.faceVisible) effectiveCorner(card) else "",
            damage = if (row.entry.faceVisible) card.damage else "",
            copies = row.copies, token = card.token,
            // ZON-094: the state a card has in exile first, as on the battlefield (TBL-133)
            automatumMarks = exileStates[card.id].orEmpty(), counterBubbles = counters,
            typeIcon = if (row.entry.faceVisible) TablePresentation.cardTypeIcon(face?.typeLine.orEmpty()) else "",
            changedPowerToughness = row.entry.faceVisible && changedPowerToughness(card.corner, face))
    } }
    val ids = panels.flatMap { it.display.cardIds }.toSet()
    return scene.copy(items = scene.items.filterNot { it.cardId in ids } + items,
        viewports = panels.map { it.viewport } + scene.viewports, cardPanels = panels)
}

@Composable
fun CardPanelHeaders(scene: TableScene, viewer: String, toolkit: TableToolkitController?) {
    for (panel in scene.cardPanels) key(panel.display.id) {
        val display = panel.display
        Row(Modifier.offset(panel.bounds.x.dp, panel.bounds.y.dp).size(panel.bounds.width.dp, PANEL_HEADER.dp)
            .semantics { contentDescription = "${if (display.author == viewer) "Your" else "Opponent"} ${panel.title} panel, ${display.cardIds.size} cards" }
            .padding(start = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            CompositionLocalProvider(LocalContentColor provides if (display.author == viewer) OwnTableColor else OpponentTableColor) {
                Icon(if (display.search && panel.localZone == null && !display.reveal) Icons.Outlined.Search else menuIcon(panel.title),
                    null, Modifier.size(14.dp))
                Text(display.cardIds.size.toString(), Modifier.weight(1f), fontSize = 10.sp, maxLines = 1)
                IconButton({ toolkit?.closeLocalPile() }, enabled = toolkit != null,
                    modifier = Modifier.width(panel.closeBounds.width.dp).height(24.dp)) {
                    Icon(menuIcon("Close"), "Close ${panel.title} panel", Modifier.size(16.dp))
                }
            }
        }
    }
}
