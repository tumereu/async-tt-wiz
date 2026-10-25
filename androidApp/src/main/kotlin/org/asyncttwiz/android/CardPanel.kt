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
import org.asyncttwiz.storage.ArtworkReference
import kotlin.math.ceil
import kotlin.math.floor

private const val PANEL_HEADER = 28f
private const val PANEL_PITCH = 52f
data class CardPanelRow(val entry: ContentsEntry, val copies: Int, val bounds: TableBounds)
data class CardPanelTypeHeader(val title: String, val bounds: TableBounds)
data class CardPanelGeometry(val display: ContentsDisplay, val bounds: TableBounds,
    val viewport: TableViewport, val rows: List<CardPanelRow>, val columns: Int = 1,
    val localZone: PlayZone? = null, val typeHeaders: List<CardPanelTypeHeader> = emptyList()) {
    val title get() = localZone?.let(::moveZoneLabel) ?: display.title
    val closeBounds get() = TableBounds(bounds.right - 24, bounds.y + 2, 24f, 24f)
}

/** Concealed copies are never grouped using hidden names (or collapsed into one back). */
internal fun panelGroups(entries: List<ContentsEntry>): List<List<ContentsEntry>> =
    entries.groupBy { if (it.faceVisible) it.name else it.cardId }.values.toList()

internal fun panelColumnCount(groupCounts: List<Int>, height: Float, maxColumns: Int,
    typeHeaders: Boolean = false): Int {
    fun required(columns: Int) = groupCounts.sumOf { ceil(it.toDouble() / columns).toInt() } * PANEL_PITCH +
        if (typeHeaders) groupCounts.size * 20f else 0f
    return (1..maxColumns).firstOrNull { required(it) <= (height - PANEL_HEADER).coerceAtLeast(0f) } ?: maxColumns
}

internal fun localPanelDisplay(play: PlayState, viewer: String, ui: TableLayoutState): ContentsDisplay? {
    val pile = play.board.containers.singleOrNull { it.id == ui.localPileId && it.zone in listOf(PlayZone.GRAVEYARD, PlayZone.EXILE) }
        ?: return null
    if (pile.cards.isEmpty()) return null
    return ContentsDisplay("local:${pile.id}", viewer, pile.id, ContentsSelection.ALL, pile.cards.size, false, pile.cards)
}

/** Layout depends only on the current materialized board and personal viewport, never history. */
internal fun cardPanelGeometry(play: PlayState, viewer: String, ui: TableLayoutState, area: TableBounds): List<CardPanelGeometry> {
    val local = localPanelDisplay(play, viewer, ui)
    // Keep old multi-panel data usable; new opens are limited game-wide by the protocol.
    val displays = play.board.contentsDisplays.sortedBy { if (it.author == viewer) 1 else 0 } + listOfNotNull(local)
    var x = area.x
    return displays.map { display ->
        val localZone = if (display == local) play.board.containers.single { it.id == display.containerId }.zone else null
        val entries = if (localZone != null) contentsEntries(play, PileContents(display.containerId, "", display.cardIds), viewer)
            else contentsEntries(play, display, viewer)
        val grouped = if (localZone != null) panelGroups(entries)
            else if (display.search && display.author == viewer) entries.groupBy {
                it.name to (it.cardId in display.revealedIds)
            }.values.toList() else entries.map { listOf(it) }
        val sections = if (localZone != null) grouped.groupBy { it.first().category }.toSortedMap().values.toList() else listOf(grouped)
        val columns = panelColumnCount(sections.map { it.size }, area.height, if (displays.size == 1) 3 else 2, localZone != null)
        val width = maxOf(64f, columns * 32f + 8f)
        val bounds = TableBounds(x, area.y, width, area.height)
        x = bounds.right + 4f
        val portBounds = TableBounds(bounds.x, bounds.y + PANEL_HEADER, width, (bounds.height - PANEL_HEADER).coerceAtLeast(0f))
        val content = sections.sumOf { ceil(it.size.toDouble() / columns).toInt() } * PANEL_PITCH +
            if (localZone != null) sections.size * 20f else 0f
        val portId = "cardPanel/${display.id}"
        val scroll = (ui.scrollY[portId] ?: 0f).coerceIn(0f, (content - portBounds.height).coerceAtLeast(0f))
        val port = TableViewport(portId, portBounds, width, 0f, contentHeight = content, scrollY = scroll)
        val rows = mutableListOf<CardPanelRow>()
        val headers = mutableListOf<CardPanelTypeHeader>()
        var y = portBounds.y - scroll
        for (section in sections) {
            if (localZone != null && section.isNotEmpty()) {
                val b = TableBounds(bounds.x + 2f, y, width - 4f, 20f)
                if (b.intersection(portBounds) != null) headers += CardPanelTypeHeader(section.first().first().category, b)
                y += 20f
            }
            section.forEachIndexed { n, group ->
                val b = TableBounds(bounds.x + (width - columns * 32f) / 2 + n % columns * 32f + 2,
                    y + n / columns * PANEL_PITCH + 2, 28f, 40f)
                if (b.intersection(portBounds) != null) rows += CardPanelRow(group.first(), group.size, b)
            }
            y += ceil(section.size.toDouble() / columns).toInt() * PANEL_PITCH
        }
        CardPanelGeometry(display, bounds, port, rows, columns, localZone, headers)
    }
}

internal fun cardPanels(scene: TableScene, play: PlayState, viewer: String, panels: List<CardPanelGeometry>): TableScene {
    if (panels.isEmpty()) return scene
    val cards = play.board.cards.associateBy { it.id }
    val definitions = play.definitions.associateBy { it.digest }
    val containers = play.board.containers.associateBy { it.id }
    val items = panels.flatMap { panel -> panel.rows.mapNotNull { row ->
        val card = cards[row.entry.cardId] ?: return@mapNotNull null
        val definition = definitions[card.definition]?.definition
        val face = definition?.faces?.firstOrNull { it.faceId == card.faceId }
        val art = if (!row.entry.faceVisible || face == null || face.scryfallId == null && face.imageUri == null) null else
            ArtworkReference(face.scryfallId, definition.faces.indexOf(face), card.definition, customUrl = face.imageUri).forTable()
        TableItem("card/${card.id}", row.bounds,
            "${panel.title} panel: ${row.entry.name}${if (row.copies > 1) ", ×${row.copies}" else ""}", if (row.entry.faceVisible) row.entry.name else "",
            owner = card.owner, zone = containers[card.containerId]?.zone, kind = TableItemKind.CARD,
            viewport = panel.viewport.id, cardId = card.id, covered = !row.entry.faceVisible,
            frozen = panel.localZone == null && panel.display.author != viewer, artwork = art,
            panelRevealed = panel.localZone == null && panel.display.author == viewer && !panel.display.reveal &&
                card.id in panel.display.revealedIds,
            corner = if (row.entry.faceVisible) card.corner else "", copies = row.copies, token = card.token,
            typeIcon = if (row.entry.faceVisible) TablePresentation.cardTypeIcon(face?.typeLine.orEmpty()) else "")
    } }
    val ids = panels.flatMap { it.display.cardIds }.toSet()
    return scene.copy(items = scene.items.filterNot { it.cardId in ids } + items,
        viewports = panels.map { it.viewport } + scene.viewports, cardPanels = panels)
}

internal fun TableScene.panelReorder(point: TablePoint, scope: TableScope?): PlayAction.ReorderContents? {
    val id = scope?.contextId ?: return null
    val panel = cardPanels.firstOrNull { id in it.display.cardIds && it.viewport.bounds.contains(point) &&
        it.localZone == null && !it.display.search } ?: return null
    val gridX = panel.bounds.x + (panel.bounds.width - panel.columns * 32f) / 2 + 2
    val row = floor((point.y - panel.viewport.bounds.y + panel.viewport.scrollY - 2) / PANEL_PITCH).toInt().coerceAtLeast(0)
    val column = floor((point.x - gridX + 18) / 32f).toInt().coerceIn(0, panel.columns)
    val index = (row * panel.columns + column).coerceIn(0, panel.display.cardIds.size)
    val before = panel.display.cardIds.asSequence().drop(index).firstOrNull { it != id }
    return PlayAction.ReorderContents(panel.display.id, id, before)
}

@Composable
internal fun CardPanelHeaders(scene: TableScene, viewer: String, writable: Boolean, toolkit: TableToolkitController?) {
    for (panel in scene.cardPanels) key(panel.display.id) {
        val display = panel.display
        Row(Modifier.offset(panel.bounds.x.dp, panel.bounds.y.dp).size(panel.bounds.width.dp, PANEL_HEADER.dp)
            .semantics { contentDescription = "${if (display.author == viewer) "Your" else "Opponent"} ${panel.title} panel, ${display.cardIds.size} cards" }
            .padding(start = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            CompositionLocalProvider(LocalContentColor provides if (display.author == viewer) OwnTableColor else OpponentTableColor) {
                Icon(if (display.search && panel.localZone == null && !display.reveal) Icons.Outlined.Search else menuIcon(panel.title),
                    null, Modifier.size(14.dp))
                Text(display.cardIds.size.toString(), Modifier.weight(1f), fontSize = 10.sp, maxLines = 1)
                if (display.author == viewer) IconButton({
                    if (panel.localZone != null) toolkit?.closeLocalPile() else toolkit?.closeCardPanel(display)
                }, enabled = toolkit != null && (panel.localZone != null || writable), modifier = Modifier.size(24.dp)) {
                    Icon(menuIcon("Close"), "Close ${panel.title} panel", Modifier.size(16.dp))
                } else Spacer(Modifier.width(3.dp))
            }
        }
    }
}

internal data class PanelCardAction(val label: String, val zone: PlayZone, val reveal: Boolean = false,
    val bottom: Boolean = false, val tapped: Boolean = false)
internal val panelCardActions = listOf(PanelCardAction("Hand", PlayZone.HAND), PanelCardAction("Reveal+Hand", PlayZone.HAND, reveal = true),
    PanelCardAction("Battlefield", PlayZone.BATTLEFIELD), PanelCardAction("Battlefield tapped", PlayZone.BATTLEFIELD, tapped = true),
    PanelCardAction("Graveyard", PlayZone.GRAVEYARD), PanelCardAction("Exile", PlayZone.EXILE),
    PanelCardAction("Top", PlayZone.LIBRARY), PanelCardAction("Reveal+Top", PlayZone.LIBRARY, reveal = true),
    PanelCardAction("Bottom", PlayZone.LIBRARY, bottom = true))
internal fun panelCardActionEnabled(action: PanelCardAction, display: ContentsDisplay, id: String, localZone: PlayZone?): Boolean =
    action.zone != localZone && (!action.reveal || localZone == null && !display.reveal && id !in display.revealedIds)

internal fun panelCardActionLabel(action: PanelCardAction) = when (action.label) {
    // Two lines keep the reveal variants distinguishable from plain Hand/Top without truncation.
    "Reveal+Hand" -> "Reveal\nhand"
    "Reveal+Top" -> "Reveal\ntop"
    "Battlefield tapped" -> "Tapped"
    else -> action.label
}

/** Fixed anchors are scene geometry, not the tapped card's row. No additional popup chrome. */
@Composable
internal fun CardPanelPopup(toolkit: TableToolkitController, play: PlayState, scene: TableScene) {
    val route = toolkit.panel?.split('/') ?: return
    val panel = scene.cardPanels.singleOrNull { it.display.id == route.getOrNull(1) && it.display.author == toolkit.viewer } ?: return
    val display = panel.display
    val rest = route.first() == "panelRest"
    val id = if (rest) null else route.getOrNull(2)?.takeIf { it in display.cardIds } ?: return
    val cell = CardRingGeometry.cell.toFloat()
    val width = if (rest) 5 * cell + 8f else 3 * cell + 4f
    val height = if (rest) cell else 3 * cell + 4f
    val x = if (rest) panel.closeBounds.x else scene.cardPanels.maxOf { it.bounds.right } + 4f
    val y = if (rest) panel.closeBounds.y else panel.viewport.bounds.y + 2f
    TableInlinePopup(toolkit.panel.orEmpty(), id ?: display.id, panel.bounds, true, OwnTableColor, toolkit::dismiss,
        width.dp, height.dp, description = if (rest) "Panel rest choices" else "Panel card actions",
        placement = TableBounds(x, y, width, height)) {
        if (rest) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for ((label, destination) in listOf("Top" to ContentsRest.TOP, "Bottom" to ContentsRest.BOTTOM,
                "Shuffle" to ContentsRest.SHUFFLE, "Graveyard" to ContentsRest.GRAVEYARD, "Exile" to ContentsRest.EXILE)) {
                InlinePopupCell(MenuControl(label, toolkit.enabled()) { toolkit.resolveCardPanel(display, destination) })
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (row in panelCardActions.chunked(3)) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (action in row) InlinePopupCell(MenuControl(action.label,
                    toolkit.enabled() && panelCardActionEnabled(action, display, id!!, panel.localZone)) {
                    if (panel.localZone != null) toolkit.localPanelAction(id!!, action.zone, action.bottom, action.tapped)
                    else toolkit.cardPanelAction(display, id!!, action.zone, action.reveal, action.bottom, action.tapped)
                }, label = panelCardActionLabel(action))
            }
        }
    }
}
