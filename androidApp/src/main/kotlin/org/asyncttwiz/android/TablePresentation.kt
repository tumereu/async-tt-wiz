package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.math.max

/** All coordinates are dp. A personal camera never writes placement or container order. */
data class TablePoint(val x: Float, val y: Float)
data class TableBounds(val x: Float, val y: Float, val width: Float, val height: Float) {
    val right get() = x + width
    val bottom get() = y + height
    val center get() = TablePoint(x + width / 2, y + height / 2)
    fun contains(p: TablePoint) = p.x >= x && p.x < right && p.y >= y && p.y < bottom
    fun intersection(b: TableBounds): TableBounds? {
        val l = maxOf(x, b.x); val t = maxOf(y, b.y)
        val r = minOf(right, b.right); val d = minOf(bottom, b.bottom)
        return if (r > l && d > t) TableBounds(l, t, r - l, d - t) else null
    }
    /** Exact visible portions, not a bounding box spanning an obscured card. */
    fun subtract(b: TableBounds): List<TableBounds> {
        val i = intersection(b) ?: return listOf(this)
        return listOf(TableBounds(x, y, width, i.y - y), TableBounds(x, i.bottom, width, bottom - i.bottom),
            TableBounds(x, i.y, i.x - x, i.height), TableBounds(i.right, i.y, right - i.right, i.height))
            .filter { it.width > 0 && it.height > 0 }
    }
}

data class TableCamera(val x: Float = 0f, val y: Float = 0f, val zoom: Float = 1f)
/** Adapter input only: I13 can supply accepted/predicted logical placements without a second card model. */
data class TablePlacement(val column: Float, val row: Float, val pileIndex: Int = 0, val pileId: String? = null) {
    constructor(column: Int, row: Int, pileIndex: Int = 0, pileId: String? = null) : this(column.toFloat(), row.toFloat(), pileIndex, pileId)
}
data class TableLayoutState(val camera: TableCamera = TableCamera(), val scroll: Map<String, Float> = emptyMap(),
    val scrollY: Map<String, Float> = emptyMap())
enum class TableItemKind { CARD, PILE, LIFE, COUNTER, HAND }
data class TableItem(
    val id: String, val bounds: TableBounds, val label: String, val value: String,
    val owner: String? = null, val zone: PlayZone? = null, val cards: List<String> = emptyList(),
    val kind: TableItemKind = TableItemKind.PILE, val viewport: String = "", val cardId: String? = null,
    val covered: Boolean = false, val tapped: Boolean = false, val corner: String = "",
    val visible: List<TableBounds> = listOf(bounds), val footprint: TableBounds = bounds,
    val counter: CounterColor? = null, val peekOwners: List<String> = emptyList(),
    val sourceId: String? = cardId, val projection: Boolean = false, val frozen: Boolean = false,
    val token: Boolean = false, val note: String = "", val counters: Map<CounterColor, String> = emptyMap()
)
data class TableField(val bounds: TableBounds, val owner: String, val containerId: String = "")
data class TableViewport(val id: String, val bounds: TableBounds, val contentWidth: Float, val scroll: Float,
    val hand: Boolean = false, val contentHeight: Float = bounds.height, val scrollY: Float = 0f) {
    val maxScroll get() = (contentWidth - bounds.width).coerceAtLeast(0f)
    val maxScrollY get() = (contentHeight - bounds.height).coerceAtLeast(0f)
}
data class TableScene(val items: List<TableItem>, val fields: List<TableField>,
    val viewports: List<TableViewport> = emptyList(), val cardWidth: Float = 48f, val cardHeight: Float = 68f,
    val footprint: Float = 84f, val camera: TableCamera = TableCamera(),
    val sources: Map<String, TableMoveSource> = emptyMap(), val combatDrops: List<CombatDropRegion> = emptyList(),
    val combatDividers: List<Pair<TablePoint, TablePoint>> = emptyList()) {
    fun hit(point: TablePoint): TableItem? = items.asReversed().firstOrNull { item -> item.visible.any { it.contains(point) } }?.let {
        if (it.id.startsWith("stackDrawing/")) items.first { pile -> pile.id == it.viewport } else it
    }
    fun rectangle(bounds: TableBounds): Set<String> = items.filter { it.kind == TableItemKind.CARD &&
        it.visible.any { part -> part.intersection(bounds) != null } }.mapNotNull { it.cardId }.toSet()
    fun viewport(point: TablePoint) = viewports.firstOrNull { it.bounds.contains(point) }
}

object TablePresentation {
    const val MIN_ZOOM = .65f
    const val MAX_ZOOM = 2f
    fun scene(game: GameSummary, play: PlayState, viewer: String, width: Float, height: Float, landscape: Boolean,
        ui: TableLayoutState = TableLayoutState(), placements: Map<String, TablePlacement> = emptyMap(), toolbarWidth: Float = 144f): TableScene {
        require(play.gameId == game.id && play.id == game.playId)
        require(width > 0 && height > 0 && width.isFinite() && height.isFinite())
        val placements = logicalPlacements(play) + placements
        val own = play.board.players.single { it.accountId == viewer }
        val opponent = play.board.players.single { it.accountId != viewer }
        fun name(id: String?) = if (game.inviter.accountId == id) game.inviter.username else game.recipient.username
        val zoom = ui.camera.zoom.takeIf { it.isFinite() }?.coerceIn(MIN_ZOOM, MAX_ZOOM) ?: 1f
        val logicalRight = play.board.containers.filter { it.zone == PlayZone.BATTLEFIELD }.maxOfOrNull { c ->
            c.cards.mapIndexed { n, id -> (placements[id]?.column ?: n.toFloat()) + 1f }.maxOrNull() ?: 4f
        } ?: 4f
        val boardWidthAtZoom = (width - if (landscape) minOf(236f, width * .36f) else 0f) / zoom
        val camera = TableCamera(ui.camera.x.takeIf { it.isFinite() }?.coerceIn(-84f, max(84f, logicalRight * 84f - boardWidthAtZoom + 84f)) ?: 0f,
            ui.camera.y.takeIf { it.isFinite() }?.coerceIn(-84f, max(252f, (placements.values.maxOfOrNull { it.row } ?: 0f) * 84f + 84f)) ?: 0f, zoom)
        val cw = 48f * camera.zoom; val ch = 68f * camera.zoom
        val combat = CombatStackPresentation.combat(play.board, cw, ch)
        val combatHeight = combat.height
        val envelope = 84f * camera.zoom // contains 90° card and the complete three-card diagonal peek
        val gap = 8f * camera.zoom
        val items = mutableListOf<TableItem>(); val ports = mutableListOf<TableViewport>()
        val byId = play.board.cards.associateBy { it.id }
        val definitions = play.definitions.associate { it.digest to it.definition }
        val sidebar = if (landscape) minOf(236f, width * .36f) else 0f
        val railHeight = ch + 8f
        val bottomControls = if (landscape) 0f else 48f
        val counterRows = listOf(opponent, own).count { p -> p.counters.values.any(::nonzero) }
        val dockHeight = 2 * (ch + 12f) + combatHeight + if (landscape) 108f else counterRows * 48f
        val middle = height / 2
        val dockTop = if (landscape) railHeight else middle - dockHeight / 2
        val boardWidth = width - sidebar
        val sidebarBounds = TableBounds(0f, railHeight, sidebar, (height - 2 * railHeight).coerceAtLeast(0f))
        val sidebarRow = ch + 12f + 48f
        val sidebarContent = (2 * sidebarRow + ch + 12f).coerceAtLeast(sidebarBounds.height)
        val sidebarScroll = if (landscape) (ui.scrollY["sidebar"] ?: 0f).coerceIn(0f, (sidebarContent - sidebarBounds.height).coerceAtLeast(0f)) else 0f
        val fields = listOf(
            TableField(TableBounds(sidebar, railHeight, boardWidth,
                ((if (landscape) middle - combatHeight / 2 else dockTop) - railHeight).coerceAtLeast(0f)), opponent.accountId),
            TableField(TableBounds(sidebar, if (landscape) middle + combatHeight / 2 else dockTop + dockHeight, boardWidth,
                (height - bottomControls - railHeight - (if (landscape) middle + combatHeight / 2 else dockTop + dockHeight)).coerceAtLeast(0f)), own.accountId)
        ).map { f -> f.copy(containerId = play.board.containers.single { it.accountId == f.owner && it.zone == PlayZone.BATTLEFIELD }.id) }

        fun port(id: String, bounds: TableBounds, content: Float, hand: Boolean = false): TableViewport {
            val scroll = (ui.scroll[id] ?: 0f).coerceIn(0f, (content - bounds.width).coerceAtLeast(0f))
            return TableViewport(id, bounds, content, scroll, hand).also(ports::add)
        }
        fun card(card: PlayCard, b: TableBounds, footprint: TableBounds, region: String?, zone: PlayZone, viewport: String,
            viewId: String = card.id, projection: Boolean = false, frozen: Boolean = false) {
            // Region, not metadata owner, controls automatic hidden-face inspection/disclosure.
            val hidden = card.covered
            val face = definitions[card.definition]?.faces?.firstOrNull { it.faceId == card.faceId }
            val title = if (hidden) "Covered card" else face?.name ?: "Card (definition unavailable)"
            val label = "$title, owner ${name(card.owner)}, ${if (card.tapped) "tapped" else "untapped"}"
            items += TableItem("card/$viewId", b, if (projection) "${if (frozen) "Retained effect, read-only" else "Projection"}, $label" else label,
                if (hidden) "" else title, card.owner, zone,
                kind = TableItemKind.CARD, viewport = viewport, cardId = viewId, covered = card.covered,
                tapped = card.tapped, corner = if (hidden) "" else card.corner, footprint = footprint,
                sourceId = if (frozen) null else card.id, projection = projection, frozen = frozen,
                token = card.token, note = if (hidden) "" else card.note, counters = if (hidden) emptyMap() else card.counters)
        }
        for ((index, player) in listOf(opponent, own).withIndex()) {
            val ownSide = player.accountId == viewer
            val hand = play.board.containers.single { it.accountId == player.accountId && it.zone == PlayZone.HAND }
            // Landscape reserves control space in the very same top/bottom rows, not another field overlay.
            val handBounds = TableBounds(sidebar + if (landscape) 48f else 0f,
                if (index == 0) 0f else height - bottomControls - railHeight,
                (boardWidth - if (index == 0) toolbarWidth + if (landscape) 48f else 0f
                    else if (landscape) 288f else 0f).coerceAtLeast(0f), railHeight)
            val handContent = if (hand.cards.isEmpty()) 0f else (hand.cards.size - 1) * (cw + gap / 2) + cw
            val hp = port(hand.id, handBounds, handContent, true)
            val start = handBounds.x + ((handBounds.width - handContent) / 2).coerceAtLeast(0f) - hp.scroll
            items += TableItem(hand.id, handBounds, "${name(player.accountId)} Hand, ${hand.cards.size} cards",
                hand.cards.size.toString(), player.accountId, PlayZone.HAND, hand.cards, TableItemKind.HAND, hp.id)
            hand.cards.forEachIndexed { n, id -> byId[id]?.let { c ->
                val w = if (c.tapped) ch else cw; val h = if (c.tapped) cw else ch
                val b = TableBounds(start + n * (cw + gap / 2), handBounds.y + (railHeight - h) / 2, w, h)
                card(c, b, b, player.accountId, PlayZone.HAND, hp.id)
            } }

            val field = fields[index]
            val container = play.board.containers.single { it.id == field.containerId }
            // Baseline has no placements yet. Canonical container order is a rendering fallback only.
            // Placement adapter replaces this calculation when I13 adds accepted logical coordinates.
            val logicalWidth = max(4f, container.cards.mapIndexed { n, id ->
                (placements[id]?.column ?: n.toFloat()) + 1f }.maxOrNull() ?: 4f) * 84f
            val cameraX = camera.x
            val cameraY = camera.y
            container.cards.sortedWith(compareByDescending<String> { placements[it]?.pileIndex ?: 0 }.thenBy { container.cards.indexOf(it) })
                .forEach { id -> byId[id]?.let { c ->
                    val p = placements[id] ?: TablePlacement(container.cards.indexOf(id), 0)
                    val px = field.bounds.x + (p.column * 84f - cameraX) * camera.zoom
                    val logicalY = (p.row * 84f - cameraY) * camera.zoom
                    val py = if (ownSide) field.bounds.bottom - envelope - logicalY else field.bounds.y + logicalY
                    val peek = p.pileIndex.coerceIn(0, 2) * 4f * camera.zoom
                    val w = if (c.tapped) ch else cw; val h = if (c.tapped) cw else ch
                    val b = TableBounds(px + peek, py + peek, w, h)
                    card(c, b, TableBounds(px, py, envelope, envelope), player.accountId, PlayZone.BATTLEFIELD, container.id)
                } }
            port(container.id, field.bounds, logicalWidth * camera.zoom)

            val rowY = if (landscape) {
                railHeight + (if (index == 0) 0f else sidebarRow + ch + 12f) - sidebarScroll
            } else dockTop + index * (ch + 12f + combatHeight) + if (index == 1 && opponent.counters.values.any(::nonzero)) 48f else 0f
            val dockBounds = TableBounds(0f, rowY, if (landscape) sidebar else width - envelope, ch + 12f)
            val dockId = "dock/${player.accountId}"
            val dp = port(dockId, dockBounds, 48f + 3 * envelope)
            items += TableItem("life/${player.accountId}", TableBounds(0f - dp.scroll, rowY + (ch - 40f) / 2, 40f, 40f),
                "${name(player.accountId)}, life ${player.life}", player.life, player.accountId, kind = TableItemKind.LIFE, viewport = dockId)
            for ((column, zone) in listOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE).withIndex()) {
                val c = play.board.containers.single { it.accountId == player.accountId && it.zone == zone }
                val peek = minOf(2, (c.cards.size - 1).coerceAtLeast(0)) * 4f * camera.zoom
                val b = TableBounds(48f + column * envelope - dp.scroll, rowY, cw + peek, ch + peek)
                val title = zone.name.lowercase().replaceFirstChar(Char::uppercase)
                items += TableItem(c.id, b, "${name(player.accountId)} $title, ${c.cards.size} cards",
                    c.cards.size.toString(), byId[c.cards.firstOrNull()]?.owner ?: player.accountId, zone, c.cards, viewport = dockId,
                    covered = zone == PlayZone.LIBRARY || c.cards.lastOrNull()?.let { byId[it]?.covered } != false,
                    footprint = TableBounds(b.x, b.y, envelope, envelope), peekOwners = c.cards.take(3).mapNotNull { byId[it]?.owner })
            }
            val showCounters = landscape || player.counters.values.any(::nonzero)
            if (showCounters) {
                // Portrait counters are a separate full-strip rail; no overlapping tiny tap regions.
                val y = rowY + ch + 12f
                val cp = port("counters/${player.accountId}", TableBounds(0f, y, if (landscape) sidebar else width, 48f), max(if (landscape) sidebar else width, 288f))
                for ((n, color) in CounterColor.entries.withIndex()) {
                    val v = player.counters[color].orEmpty()
                    val b = TableBounds(cp.bounds.x + n * cp.contentWidth / 6 - cp.scroll, y, cp.contentWidth / 6, 48f)
                    items += TableItem("counter/${player.accountId}/$color", b,
                        "${name(player.accountId)}, ${color.name.lowercase()}, ${v.ifEmpty { "no value" }}", v,
                        player.accountId, kind = TableItemKind.COUNTER, viewport = cp.id, counter = color)
                }
            }
        }
        val stack = play.board.containers.single { it.zone == PlayZone.STACK }
        val stackGeometry = CombatStackPresentation.stack(play.board, cw, ch)
        val stackPeek = minOf(2, (stack.cards.size - 1).coerceAtLeast(0)) * cw * .12f
        val sb = TableBounds(if (landscape) (sidebar - cw) / 2 else width - envelope,
            (if (landscape) railHeight + sidebarRow else dockTop) - sidebarScroll, cw + stackPeek, ch + stackPeek)
        val sp = port(stack.id, TableBounds(sb.x, sb.y, envelope, envelope), envelope)
        items += TableItem(stack.id, sb, "Stack, ${stack.cards.size} entries", stack.cards.size.toString(),
            zone = PlayZone.STACK, cards = stack.cards, viewport = sp.id, footprint = sp.bounds,
            peekOwners = stackGeometry.visible.map { it.card.owner })
        // Actual mixed Stack top + two peeks, with a single pile/badge hit identity and all IDs retained.
        for (drawing in stackGeometry.visible.asReversed()) {
            val b = drawing.bounds.copy(x = sb.x + drawing.bounds.x, y = sb.y + drawing.bounds.y)
            val before = items.size
            card(drawing.card, b, sp.bounds, null, PlayZone.STACK, sp.id, drawing.viewId, drawing.projection, drawing.frozen)
            items[before] = items[before].copy(id = "stackDrawing/${drawing.viewId}", cards = stack.cards)
        }
        val combatY = if (landscape) middle - combatHeight / 2 else dockTop + ch + 12f +
            if (opponent.counters.values.any(::nonzero)) 48f else 0f
        val combatBounds = TableBounds(sidebar, combatY, boardWidth, combatHeight)
        val combatPort = port("combat", combatBounds, combat.width)
        fun translated(b: TableBounds) = b.copy(x = b.x + sidebar - combatPort.scroll, y = b.y + combatY)
        for (drawing in combat.cards) card(drawing.card, translated(drawing.bounds), translated(drawing.bounds),
            null, PlayZone.BATTLEFIELD, combatPort.id, drawing.viewId, projection = true)
        val drops = combat.destinations.mapNotNull { region -> translated(region.bounds).intersection(combatBounds)?.let { region.copy(bounds = it) } }
        val dividers = combat.dividers.map { x -> Pair(TablePoint(sidebar + x - combatPort.scroll, combatY),
            TablePoint(sidebar + x - combatPort.scroll, combatY + combatHeight)) }
        // Clip once, then subtract later opaque drawings. Drawing, targeting and accessibility share this result.
        val clippedPorts = if (landscape) ports.map { p ->
            if (p.id.startsWith("dock/") || p.id.startsWith("counters/") || p.id == stack.id)
                p.copy(bounds = p.bounds.intersection(sidebarBounds) ?: p.bounds.copy(height = 0f)) else p
        } + TableViewport("sidebar", sidebarBounds, sidebar, 0f, contentHeight = sidebarContent, scrollY = sidebarScroll) else ports
        val realSources = play.board.cards.associate { c -> c.id to TableMoveSource(c.containerId, placements[c.id],
            play.board.containers.any { it.id == c.containerId && it.zone == PlayZone.LIBRARY && it.cards.firstOrNull() == c.id }) }
        val sources = realSources + items.filter { it.projection && it.sourceId != null }.associate { item ->
            item.cardId!! to realSources.getValue(item.sourceId!!).copy(sourceId = item.sourceId) }
        return clip(TableScene(items, fields, clippedPorts, cw, ch, envelope, camera, sources, drops, dividers), width, height)
    }
    /** Reused after animated bounds interpolation: rendered positions, not destination positions, own targeting. */
    fun clip(scene: TableScene, width: Float, height: Float): TableScene {
        val screen = TableBounds(0f, 0f, width, height)
        val clipped = scene.items.map { i -> i.copy(visible = listOfNotNull(i.bounds.intersection(scene.viewports.single { it.id == i.viewport }.bounds)?.intersection(screen))) }
        val visible = clipped.mapIndexed { n, i ->
            val portions = if (i.kind == TableItemKind.HAND) i.visible else clipped.drop(n + 1)
                .filter { it.kind != TableItemKind.HAND && !(i.zone == PlayZone.STACK && i.kind == TableItemKind.PILE && it.viewport == i.viewport) }.flatMap { it.visible }
                .fold(i.visible) { regions, cover -> regions.flatMap { it.subtract(cover) } }
            i.copy(visible = portions)
        }
        return scene.copy(items = visible)
    }
    fun nonzero(value: String): Boolean = value.trim().let { s -> s.isNotEmpty() &&
        (!Regex("[+-]?[0-9]+").matches(s) || s.removePrefix("+").removePrefix("-").any { it != '0' }) }

    fun logicalPlacements(play: PlayState): Map<String, TablePlacement> = play.board.cards.mapNotNull { card ->
        card.placement?.let { p ->
            val members = play.board.containers.single { it.id == card.containerId }.cards.filter { id ->
                play.board.cards.single { it.id == id }.placement == p }
            card.id to TablePlacement(p.position.x, p.position.y, members.indexOf(card.id), p.pileId)
        }
    }.toMap()

    /** Automatic hold never grants hidden-side access outside the card's current own region. */
    fun inspection(play: PlayState, viewer: String, cardId: String): CardDefinition? {
        val card = play.board.cards.firstOrNull { it.id == cardId } ?: return null
        val region = play.board.containers.firstOrNull { it.id == card.containerId }?.accountId
        if (card.covered && region != viewer) return CardDefinition("Covered card", listOf(DefinitionFace("covered", "Covered card")))
        val definition = play.definitions.firstOrNull { it.digest == card.definition }?.definition
            ?: return CardDefinition("Card unavailable", listOf(DefinitionFace("unavailable", "Downloaded definition unavailable")))
        if (region == viewer) return definition
        val face = definition.faces.firstOrNull { it.faceId == card.faceId }
            ?: return CardDefinition("Card unavailable", listOf(DefinitionFace("unavailable", "Downloaded face unavailable")))
        return definition.copy(name = face.name, faces = listOf(face))
    }
}
