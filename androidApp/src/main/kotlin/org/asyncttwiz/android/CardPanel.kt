package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
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

data class CardPanelRow(val entry: ContentsEntry, val copies: Int, val bounds: TableBounds)
data class CardPanelGeometry(val display: ContentsDisplay, val bounds: TableBounds,
    val viewport: TableViewport, val rows: List<CardPanelRow>)

/** Only current board entities and visible rows are consulted; no retained history is walked. */
internal fun cardPanels(scene: TableScene, play: PlayState, viewer: String, ui: TableLayoutState): TableScene {
    val world = scene.world?.visibleViewport ?: return scene
    val displays = play.board.contentsDisplays.sortedBy { if (it.author == viewer) 1 else 0 }
    if (displays.isEmpty()) return scene
    val panels = mutableListOf<CardPanelGeometry>()
    val items = mutableListOf<TableItem>()
    val cards = play.board.cards.associateBy { it.id }
    val definitions = play.definitions.associateBy { it.digest }
    val height = (world.height / displays.size).coerceAtLeast(1f)
    for ((index, display) in displays.withIndex()) {
        val b = TableBounds((world.x - 6f).coerceAtLeast(0f), world.y + index * height, 124f, height)
        val entries = contentsEntries(play, display, viewer)
        // Private opponent copies must never be grouped by their concealed names.
        val groups = if (display.search && display.author == viewer) entries.groupBy { it.name }.values.toList()
            else entries.map { listOf(it) }
        val rowHeight = scene.cardHeight + 12f
        val portBounds = TableBounds(b.x, b.y + 36f, b.width, (b.height - 36f).coerceAtLeast(0f))
        val content = groups.size * rowHeight
        val scroll = (ui.scrollY["cardPanel/${display.id}"] ?: 0f).coerceIn(0f, (content - portBounds.height).coerceAtLeast(0f))
        val port = TableViewport("cardPanel/${display.id}", portBounds, b.width, 0f, contentHeight = content, scrollY = scroll)
        val rows = groups.mapIndexedNotNull { n, group ->
            val bounds = TableBounds(b.x + 8f, portBounds.y + n * rowHeight - scroll + 6f, scene.cardWidth, scene.cardHeight)
            if (bounds.intersection(portBounds) == null) null else CardPanelRow(group.first(), group.size, bounds)
        }
        panels += CardPanelGeometry(display, b, port, rows)
        for (row in rows) {
            val card = cards[row.entry.cardId] ?: continue
            val definition = definitions[card.definition]?.definition
            val face = definition?.faces?.firstOrNull { it.faceId == card.faceId }
            val art = if (!row.entry.faceVisible || face == null || face.scryfallId == null && face.imageUri == null) null else
                ArtworkReference(face.scryfallId, definition.faces.indexOf(face), card.definition, customUrl = face.imageUri).forTable()
            items += TableItem("card/${card.id}", row.bounds,
                "${display.title} panel: ${row.entry.name}${if (row.copies > 1) ", ×${row.copies}" else ""}", row.entry.name,
                owner = card.owner, zone = play.board.containers.single { it.id == card.containerId }.zone,
                kind = TableItemKind.CARD, viewport = port.id, cardId = card.id, covered = !row.entry.faceVisible,
                frozen = display.author != viewer, artwork = art, known = card.knownToAll && display.author != viewer,
                corner = if (row.entry.faceVisible) card.corner else "")
        }
    }
    // Panel cards replace any ordinary drawing of these references, never duplicate card identities.
    val ids = displays.flatMap { it.cardIds }.toSet()
    return scene.copy(items = scene.items.filterNot { it.cardId in ids } + items,
        viewports = panels.map { it.viewport } + scene.viewports, cardPanels = panels)
}

internal fun TableScene.panelReorder(point: TablePoint, scope: TableScope?): PlayAction.ReorderContents? {
    val id = scope?.contextId ?: return null
    val panel = cardPanels.firstOrNull { id in it.display.cardIds && it.viewport.bounds.contains(point) && !it.display.search } ?: return null
    val before = panel.rows.firstOrNull { it.entry.cardId != id && point.y < it.bounds.center.y }?.entry?.cardId
    return PlayAction.ReorderContents(panel.display.id, id, before)
}

@Composable
internal fun CardPanelHeaders(scene: TableScene, viewer: String, writable: Boolean, toolkit: TableToolkitController?) {
    for (panel in scene.cardPanels) key(panel.display.id) {
        val display = panel.display
        Row(Modifier.offset(panel.bounds.x.dp, panel.bounds.y.dp).size(panel.bounds.width.dp, 36.dp)
            .semantics { contentDescription = "${if (display.author == viewer) "Your" else "Opponent"} ${display.title} panel, ${display.cardIds.size} cards" },
            verticalAlignment = Alignment.CenterVertically) {
            CompositionLocalProvider(LocalContentColor provides if (display.author == viewer) OwnTableColor else OpponentTableColor) {
                MenuIcon(display.title, Modifier.size(16.dp))
                Text(display.title, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Text(display.cardIds.size.toString(), fontSize = 11.sp)
                if (display.author == viewer) IconButton({ toolkit?.closeCardPanel(display) }, enabled = writable && toolkit != null,
                    modifier = Modifier.size(32.dp)) { Icon(menuIcon("Close"), "Close ${display.title} panel") }
                else Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
internal fun CardPanelPopup(toolkit: TableToolkitController, play: PlayState) {
    val route = toolkit.panel?.split('/') ?: return
    val display = play.board.contentsDisplays.singleOrNull { it.id == route.getOrNull(1) && it.author == toolkit.viewer } ?: return
    if (route.first() == "panelRest") InlinePopupPanel(toolkit::dismiss, "Rest · ${display.cardIds.size}", width = 272.dp, height = 360.dp) {
        for ((label, rest) in listOf("Top (panel order)" to ContentsRest.TOP, "Bottom (panel order)" to ContentsRest.BOTTOM,
            "Bottom (random)" to ContentsRest.BOTTOM_RANDOM, "Graveyard" to ContentsRest.GRAVEYARD, "Shuffle into library" to ContentsRest.SHUFFLE)) {
            TextButton({ toolkit.resolveCardPanel(display, rest) }, enabled = toolkit.enabled(), modifier = Modifier.fillMaxWidth()) {
                MenuIcon(label); Spacer(Modifier.width(8.dp)); Text(label)
            }
        }
    } else if (route.first() == "panelCard") {
        val id = route.getOrNull(2)?.takeIf { it in display.cardIds } ?: return
        InlinePopupPanel(toolkit::dismiss, "${display.title} card", width = 224.dp, height = 240.dp) {
            val actions = listOf(Triple("Hand", PlayZone.HAND, false), Triple("Reveal+Hand", PlayZone.HAND, true),
                Triple("Battlefield", PlayZone.BATTLEFIELD, false), Triple("Battlefield tapped", PlayZone.BATTLEFIELD, false),
                Triple("Graveyard", PlayZone.GRAVEYARD, false), Triple("Exile", PlayZone.EXILE, false),
                Triple("Top", PlayZone.LIBRARY, false), Triple("Reveal+Top", PlayZone.LIBRARY, true), Triple("Bottom", PlayZone.LIBRARY, false))
            for (row in actions.chunked(3)) Row {
                for ((label, zone, reveal) in row) InlinePopupCell(MenuControl(label, toolkit.enabled()) {
                    toolkit.cardPanelAction(display, id, zone, reveal, bottom = label == "Bottom", tapped = label == "Battlefield tapped")
                })
            }
        }
    }
}
