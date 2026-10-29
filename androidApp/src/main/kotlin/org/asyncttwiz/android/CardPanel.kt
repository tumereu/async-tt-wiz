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
private const val QUEUE_LINE = 11f
data class CardPanelRow(val entry: ContentsEntry, val copies: Int, val bounds: TableBounds,
    val cardIds: List<String> = listOf(entry.cardId))
data class CardPanelTypeHeader(val title: String, val bounds: TableBounds)
data class CardPanelGeometry(val display: ContentsDisplay, val bounds: TableBounds,
    val viewport: TableViewport, val rows: List<CardPanelRow>, val columns: Int = 1,
    val localZone: PlayZone? = null, val typeHeaders: List<CardPanelTypeHeader> = emptyList(), val cardScale: Float = 1f,
    /** Rules v30: what waits for this Search's close ([searchQueueLines]), shown under the header. */
    val queueLines: List<String> = emptyList()) {
    val title get() = localZone?.let(::moveZoneLabel) ?: display.title
    val closeBounds get() = (if (display.reveal && localZone == null) 40f else 24f).let { TableBounds(bounds.right - it, bounds.y + 2, it, 24f) }
}

internal fun searchQueueCount(count: Int, edge: String): String? = if (count > 0) "$count on $edge" else null

/** Rules v30: what waits for a Search's close, e.g. "After shuffle:", "2 on top", "1 on bottom"; empty when nothing does. */
internal fun searchQueueLines(display: ContentsDisplay): List<String> {
    val counts = listOfNotNull(searchQueueCount(SearchQueue.topCount(display), "top"),
        searchQueueCount(SearchQueue.bottomCount(display), "bottom"))
    return if (counts.isEmpty()) emptyList() else listOf("After shuffle:") + counts
}

/** The same as one line: "After shuffle: 2 on top · 1 on bottom", or null. */
internal fun searchQueueText(display: ContentsDisplay): String? = searchQueueLines(display).takeIf { it.isNotEmpty() }
    ?.let { "${it.first()} ${it.drop(1).joinToString(" · ")}" }

/** Concealed copies are never grouped using hidden names (or collapsed into one back). */
internal fun panelGroups(entries: List<ContentsEntry>): List<List<ContentsEntry>> =
    entries.groupBy { if (it.faceVisible) it.name else it.cardId }.values.toList()

internal fun panelColumnCount(maxColumns: Int): Int = maxColumns.coerceIn(1, 3)

/** Partial rows start at the left, including each type section's last row. */
@Suppress("UNUSED_PARAMETER")
internal fun panelColumn(index: Int, count: Int, columns: Int): Int {
    return index % columns
}

internal fun TableScene.localPanelOutside(point: TablePoint): Boolean =
    cardPanels.any { it.localZone != null && !it.bounds.contains(point) }

internal fun localPanelDisplay(play: PlayState, viewer: String, ui: TableLayoutState): ContentsDisplay? {
    val pile = play.board.containers.singleOrNull { it.id == ui.localPileId && it.zone in listOf(PlayZone.GRAVEYARD, PlayZone.EXILE) }
        ?: return null
    if (pile.cards.isEmpty()) return null
    return ContentsDisplay("local:${pile.id}", viewer, pile.id, ContentsSelection.ALL, pile.cards.size, false, pile.cards)
}

/** Layout depends only on the current materialized board and personal viewport, never history. */
internal fun cardPanelGeometry(play: PlayState, viewer: String, ui: TableLayoutState, area: TableBounds,
    scale: Float = 1f): List<CardPanelGeometry> {
    val local = localPanelDisplay(play, viewer, ui)
    // Keep old multi-panel data usable; new opens are limited game-wide by the protocol.
    val displays = play.board.contentsDisplays.sortedBy { if (it.author == viewer) 1 else 0 } + listOfNotNull(local)
    var x = area.x
    val columnPitch = 28f * scale + 4f
    val rowPitch = 40f * scale + 12f
    val availablePerPanel = (area.width - (displays.size - 1).coerceAtLeast(0) * 4f) / displays.size.coerceAtLeast(1)
    return displays.map { display ->
        val localZone = if (display == local) play.board.containers.single { it.id == display.containerId }.zone else null
        val entries = if (localZone != null) contentsEntries(play, PileContents(display.containerId, "", display.cardIds), viewer)
            else contentsEntries(play, display, viewer)
        val grouped = if (localZone != null) panelGroups(entries)
            else if (display.search && display.author == viewer) entries.groupBy {
                it.name to (it.cardId in display.revealedIds)
            }.values.toList() else entries.map { listOf(it) }
        val typed = localZone != null || display.search && display.author == viewer
        val sections = if (typed) grouped.groupBy { it.first().category }.toSortedMap().values.toList() else listOf(grouped)
        val columns = minOf(panelColumnCount(if (displays.size == 1) 3 else 2),
            ((availablePerPanel - 8f) / columnPitch).toInt().coerceAtLeast(1))
        val width = maxOf(64f, columns * columnPitch + 8f)
        val bounds = TableBounds(x, area.y, width, area.height)
        x = bounds.right + 4f
        val queueLines = if (localZone == null) searchQueueLines(display) else emptyList()
        val top = PANEL_HEADER + if (queueLines.isEmpty()) 0f else queueLines.size * QUEUE_LINE + 4f
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
        CardPanelGeometry(display, bounds, port, rows, columns, localZone, headers, scale, queueLines)
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
            face.tableArtwork(definition.faces.indexOf(face), card.definition)?.forTable()
        TableItem("card/${card.id}", row.bounds,
            "${panel.title} panel: ${row.entry.name}${if (row.copies > 1) ", ×${row.copies}" else ""}", if (row.entry.faceVisible) row.entry.name else "",
            owner = card.owner, zone = containers[card.containerId]?.zone, kind = TableItemKind.CARD,
            viewport = panel.viewport.id, cardId = card.id, covered = !row.entry.faceVisible,
            frozen = panel.localZone == null && panel.display.author != viewer, artwork = art,
            panelRevealed = panel.localZone == null && panel.display.author == viewer && !panel.display.reveal &&
                card.id in panel.display.revealedIds,
            corner = if (row.entry.faceVisible) effectiveCorner(card) else "", note = if (row.entry.faceVisible) card.note else "",
            damage = if (row.entry.faceVisible) card.damage else "", eotCorner = if (row.entry.faceVisible) card.eotCorner else "",
            eotNote = if (row.entry.faceVisible) card.eotNote else "",
            copies = row.copies, token = card.token, counters = if (row.entry.faceVisible) card.counters.filterValues(TablePresentation::nonzero) else emptyMap(),
            typeIcon = if (row.entry.faceVisible) TablePresentation.cardTypeIcon(face?.typeLine.orEmpty()) else "",
            changedPowerToughness = row.entry.faceVisible && changedPowerToughness(card.corner, face))
    } }
    val ids = panels.flatMap { it.display.cardIds }.toSet()
    return scene.copy(items = scene.items.filterNot { it.cardId in ids } + items,
        viewports = panels.map { it.viewport } + scene.viewports, cardPanels = panels)
}

internal fun TableScene.panelReorder(point: TablePoint, scope: TableScope?): PlayAction.ReorderContents? {
    val id = scope?.contextId ?: return null
    val panel = cardPanels.firstOrNull { id in it.display.cardIds && it.viewport.bounds.contains(point) &&
        it.localZone == null && !it.display.search } ?: return null
    val pitch = 28f * panel.cardScale + 4f
    val gridX = panel.bounds.x + (panel.bounds.width - panel.columns * pitch) / 2 + 2
    val row = floor((point.y - panel.viewport.bounds.y + panel.viewport.scrollY - 2) / (40f * panel.cardScale + 12f)).toInt().coerceAtLeast(0)
    val rowCount = (panel.display.cardIds.size - row * panel.columns).coerceIn(0, panel.columns)
    val column = floor((point.x - gridX + pitch / 2 + 2) / pitch).toInt().coerceIn(0, rowCount)
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
                if (display.author == viewer && toolkit != null && display.cardIds.size > 1) {
                    val all = toolkit.selectionState.value.ids.containsAll(display.cardIds)
                    IconButton({ toolkit.selectAll(display.cardIds, !all) }, modifier = Modifier.width(20.dp).height(24.dp)) {
                        Icon(menuIcon("Select all"), "${if (all) "Deselect" else "Select"} all in ${panel.title} panel", Modifier.size(14.dp),
                            tint = if (all) LocalContentColor.current else LocalContentColor.current.copy(alpha = .6f))
                    }
                }
                // Either player may close any panel (rules v29); the opponent's button keeps the opponent colour.
                val whose = if (display.author == viewer) "" else "opponent's "
                IconButton({
                    if (panel.localZone != null) toolkit?.closeLocalPile() else toolkit?.closeCardPanel(display)
                }, enabled = toolkit != null && (panel.localZone != null || writable), modifier = Modifier.width(panel.closeBounds.width.dp).height(24.dp)) {
                    if (display.reveal && panel.localZone == null) Text("Done", fontSize = 10.sp,
                        modifier = Modifier.semantics { contentDescription = "Done ${whose}Reveal panel" })
                    else Icon(menuIcon("Close"), "Close ${whose}${panel.title} panel", Modifier.size(16.dp))
                }
            }
        }
        if (panel.queueLines.isNotEmpty()) Column(Modifier.offset(panel.bounds.x.dp, (panel.bounds.y + PANEL_HEADER).dp)
            .width(panel.bounds.width.dp).padding(horizontal = 3.dp)
            .semantics(mergeDescendants = true) { contentDescription = searchQueueText(display).orEmpty() }) {
            for (line in panel.queueLines) Text(line, fontSize = 9.sp, lineHeight = QUEUE_LINE.sp, maxLines = 1,
                softWrap = false, color = if (display.author == viewer) OwnTableColor else OpponentTableColor)
        }
    }
}

/** One cell of the unified panel card menu. [name] is the accessible name (and icon key), [label] the cell text. */
internal data class PanelCardAction(val name: String, val label: String = name, val zone: PlayZone? = null,
    val reveal: Boolean = false, val bottom: Boolean = false, val tapped: Boolean = false, val shuffle: Boolean = false,
    val temp: Int? = null)
/** The same 5-column layout for every left card panel (search, look, reveal, Graveyard, Exile): 18 cells, 4 rows. */
internal val panelCardActions = listOf(PanelCardAction("Inspect"), PanelCardAction("Inspect other side", "Other side"),
    PanelCardAction("Ping / Unping", "Ping"),
    PanelCardAction("Hand", zone = PlayZone.HAND), PanelCardAction("Reveal+Hand", "Reveal\nhand", PlayZone.HAND, reveal = true),
    PanelCardAction("Battlefield", zone = PlayZone.BATTLEFIELD), PanelCardAction("Battlefield tapped", "Tapped", PlayZone.BATTLEFIELD, tapped = true),
    PanelCardAction("Stack", zone = PlayZone.STACK), PanelCardAction("Graveyard", zone = PlayZone.GRAVEYARD),
    PanelCardAction("Exile", zone = PlayZone.EXILE),
    PanelCardAction("Library top", zone = PlayZone.LIBRARY), PanelCardAction("Reveal+Top", "Reveal\ntop", PlayZone.LIBRARY, reveal = true),
    PanelCardAction("Library bottom", zone = PlayZone.LIBRARY, bottom = true),
    PanelCardAction("Shuffle into library", zone = PlayZone.LIBRARY, shuffle = true)) +
    TempPiles.NUMBERS.map { PanelCardAction("Temp $it", temp = it) }

/** The tapped card alone, or (when it is selected) every selected card of THIS panel. */
internal fun panelCardTargets(display: ContentsDisplay, id: String, selected: Set<String>): List<String> =
    if (id in selected) display.cardIds.filter { it in selected } else listOf(id)

/** Zone moves only; Inspect, Other side, Ping and Temp cells have their own gates. */
internal fun panelCardActionEnabled(action: PanelCardAction, display: ContentsDisplay, ids: List<String>, localZone: PlayZone?,
    board: BoardState? = null): Boolean {
    val zone = action.zone ?: return true
    val container = board?.containers?.singleOrNull { it.id == display.containerId }
    val own = localZone ?: container?.zone?.takeUnless { container.temp }
    if (zone != PlayZone.LIBRARY && zone == own) return false
    // Search/Look panels keep Library top/bottom (reordering); shuffling into its own Library is meaningless.
    if (action.shuffle && own == PlayZone.LIBRARY) return false
    if (action.reveal && (localZone != null || display.reveal || ids.all { it in display.revealedIds })) return false
    if (board == null) return true
    if (PublicPiles.movable(board, ids, zone).isEmpty()) return false
    return zone != PlayZone.STACK || libraryDestinations(board, sourceIds = ids).any { (_, d) ->
        board.containers.any { it.id == d.containerId && it.zone == PlayZone.STACK } }
}

internal fun panelCardActionLabel(action: PanelCardAction) = action.label

/** Fixed anchors are scene geometry, not the tapped card's row. No additional popup chrome. */
@Composable
internal fun CardPanelPopup(toolkit: TableToolkitController, play: PlayState, scene: TableScene) {
    val route = toolkit.panel?.split('/') ?: return
    val rest = route.first() == "panelRest"
    // Card actions stay author-only; the rest chooser also serves the opponent closing a forgotten panel.
    val panel = scene.cardPanels.singleOrNull { it.display.id == route.getOrNull(1) &&
        (rest || it.display.author == toolkit.viewer) } ?: return
    val display = panel.display
    val id = if (rest) null else route.getOrNull(2)?.takeIf { it in display.cardIds } ?: return
    val cell = CardRingGeometry.cell.toFloat()
    val columns = CardRingPages.columns
    val width = if (rest) 5 * cell + 8f else columns * cell + (columns - 1) * 2f
    val height = if (rest) cell else ceil(panelCardActions.size / columns.toFloat()) * (cell + 2f) - 2f
    val x = if (rest) panel.closeBounds.x else scene.cardPanels.maxOf { it.bounds.right } + 4f
    val y = if (rest) panel.closeBounds.y else panel.viewport.bounds.y + 2f
    val annotations = LocalTableMenuAnnotations.current
    TableInlinePopup(toolkit.panel.orEmpty(), id ?: display.id, panel.bounds, true, OwnTableColor, toolkit::dismiss,
        width.dp, height.dp, description = if (rest) "Panel rest choices" else "Panel card actions",
        placement = TableBounds(x, y, width, height)) {
        if (rest) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for ((label, destination) in listOf("Top" to ContentsRest.TOP, "Bottom" to ContentsRest.BOTTOM,
                "Shuffle" to ContentsRest.SHUFFLE, "Graveyard" to ContentsRest.GRAVEYARD, "Exile" to ContentsRest.EXILE)) {
                InlinePopupCell(MenuControl(label, toolkit.enabled()) { toolkit.resolveCardPanel(display, destination) })
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val controls = panelCardControls(toolkit, play, panel, id!!, annotations)
            for (row in panelCardActions.zip(controls).chunked(columns)) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for ((action, control) in row) InlinePopupCell(control, label = panelCardActionLabel(action))
            }
        }
    }
}

/** Controls in [panelCardActions] order. Inspect and Other side act on the tapped card; every other cell on
 * [panelCardTargets] as ONE invocation; two or more cards onto a Library top/bottom go in random order. */
internal fun panelCardControls(toolkit: TableToolkitController, play: PlayState, panel: CardPanelGeometry, id: String,
    annotations: TableMenuAnnotations?): List<MenuControl> {
    val display = panel.display
    val enabled = toolkit.enabled()
    val ids = panelCardTargets(display, id, toolkit.selectionState.value.ids)
    val card = play.board.cards.singleOrNull { it.id == id }
    val faces = card?.let { c -> play.definitions.singleOrNull { it.digest == c.definition }?.definition?.faces }.orEmpty()
    // A face visible in the panel offers its other face; a face-down entry offers its hidden front (logged).
    val visible = toolkit.popupItem?.takeIf { it.cardId == id }?.covered == false
    val other = if (!visible) faces.singleOrNull { it.faceId == card?.faceId } else faces.firstOrNull { it.faceId != card?.faceId }
    val temps = tempPileTools(toolkit, play, ids, enabled)
    return panelCardActions.map { action ->
        when {
            action.name == "Inspect" -> MenuControl(action.name) { toolkit.inspect(id) }
            action.name == "Inspect other side" -> MenuControl(action.name, enabled && card != null && other != null) {
                if (card != null && other != null) toolkit.submit(PlayAction.InspectFace(card.id, other.faceId, card.containerId))
            }
            action.name == "Ping / Unping" -> MenuControl(action.name, enabled && annotations != null) {
                if (annotations != null && annotations.submit(ids.flatMap { target ->
                        tablePingIntent(play, annotations.reference, PingTarget(PingTargetKind.CARD, target)).actions() })) toolkit.dismiss()
            }
            action.temp != null -> temps[action.temp - 1]
            else -> MenuControl(action.name, enabled && panelCardActionEnabled(action, display, ids, panel.localZone, play.board)) {
                val zone = requireNotNull(action.zone)
                if (panel.localZone != null) toolkit.localPanelAction(ids, zone, action.bottom, action.tapped, action.shuffle)
                else toolkit.cardPanelAction(display, ids, zone, action.reveal, action.bottom, action.tapped, action.shuffle)
            }
        }
    }
}
