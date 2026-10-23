package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ArtworkReference
import kotlin.math.max

/** All coordinates are dp. Personal navigation never changes shared placement or container order. */
data class TablePoint(val x: Float, val y: Float)
data class TableBounds(val x: Float, val y: Float, val width: Float, val height: Float) {
    val right get() = x + width
    val bottom get() = y + height
    val center get() = TablePoint(x + width / 2, y + height / 2)
    fun contains(p: TablePoint) = p.x >= x && p.x < right && p.y >= y && p.y < bottom
    fun intersection(b: TableBounds): TableBounds? {
        if (width <= 0 || height <= 0 || b.width <= 0 || b.height <= 0) return null
        // Preserve exact extents for contained drawings (subtracting fractional dock anchors loses precision).
        if (x >= b.x && y >= b.y && right <= b.right && bottom <= b.bottom) return this
        if (b.x >= x && b.y >= y && b.right <= right && b.bottom <= bottom) return b
        val l = maxOf(x, b.x); val t = maxOf(y, b.y)
        val r = minOf(right, b.right); val d = minOf(bottom, b.bottom)
        return if (r > l && d > t) TableBounds(l, t, r - l, d - t) else null
    }
    /** Exact visible fragments, not a bounding box spanning an obscured card. */
    fun subtract(b: TableBounds): List<TableBounds> {
        val i = intersection(b) ?: return listOf(this)
        return listOf(TableBounds(x, y, width, i.y - y), TableBounds(x, i.bottom, width, bottom - i.bottom),
            TableBounds(x, i.y, i.x - x, i.height), TableBounds(i.right, i.y, right - i.right, i.height))
            .filter { it.width > 0 && it.height > 0 }
    }
}

data class TableCamera(val x: Float = 0f, val y: Float = 0f, val zoom: Float = 1f)
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
    val token: Boolean = false, val note: String = "", val counters: Map<CounterColor, String> = emptyMap(),
    val typeIcon: String = "", val artwork: ArtworkReference? = null
)
data class TableField(val bounds: TableBounds, val owner: String, val containerId: String = "",
    val gridBounds: TableBounds = bounds)
data class TableViewport(val id: String, val bounds: TableBounds, val contentWidth: Float, val scroll: Float,
    val hand: Boolean = false, val contentHeight: Float = bounds.height, val scrollY: Float = 0f) {
    val maxScroll get() = (contentWidth - bounds.width).coerceAtLeast(0f)
    val maxScrollY get() = (contentHeight - bounds.height).coerceAtLeast(0f)
}
data class TableScene(val items: List<TableItem>, val fields: List<TableField>,
    val viewports: List<TableViewport> = emptyList(), val cardWidth: Float = 28f, val cardHeight: Float = 40f,
    val footprint: Float = 56f, val camera: TableCamera = TableCamera(),
    val sources: Map<String, TableMoveSource> = emptyMap(), val combatDrops: List<CombatDropRegion> = emptyList(),
    val combatDividers: List<Pair<TablePoint, TablePoint>> = emptyList(),
    val stackBubbles: StackBubbleGeometry? = null) {
    fun hit(point: TablePoint): TableItem? = stackBubbles?.controls?.firstOrNull { it.bounds.contains(point) }
        ?: items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE &&
            TableBounds(it.bounds.x, it.bounds.y, 20f, 20f).contains(point) && it.visible.isNotEmpty() }
        ?: items.asReversed().firstOrNull { item -> item.visible.any { it.contains(point) } }
    fun rectangle(bounds: TableBounds): Set<String> = items.filter { it.kind == TableItemKind.CARD &&
        it.visible.any { part -> part.intersection(bounds) != null } }.mapNotNull { it.cardId }.toSet()
    fun viewport(point: TablePoint) = viewports.firstOrNull { it.bounds.contains(point) }
}

object TablePresentation {
    const val GRID = 56f
    const val MIN_ZOOM = .65f
    const val MAX_ZOOM = 2f
    const val HEADER = 48f
    const val TOOLBAR = 264f
    fun sidebarWidth(width: Float) = minOf(326f, width * .366f)

    fun scene(game: GameSummary, play: PlayState, viewer: String, width: Float, height: Float, landscape: Boolean,
        ui: TableLayoutState = TableLayoutState(), placements: Map<String, TablePlacement> = emptyMap(),
        toolbarWidth: Float = TOOLBAR, reviewing: Boolean = false): TableScene {
        require(play.gameId == game.id && play.id == game.playId)
        require(width > 0 && height > 0 && width.isFinite() && height.isFinite())
        val positions = logicalPlacements(play) + placements
        val own = play.board.players.single { it.accountId == viewer }
        val opponent = play.board.players.single { it.accountId != viewer }
        val players = listOf(opponent, own)
        val byId = play.board.cards.associateBy { it.id }
        val definitions = play.definitions.associate { it.digest to it.definition }
        fun name(id: String?) = if (game.inviter.accountId == id) game.inviter.username else game.recipient.username
        fun container(player: String?, zone: PlayZone) = play.board.containers.single { it.accountId == player && it.zone == zone }
        fun face(card: PlayCard?) = card?.let { definitions[it.definition]?.faces?.firstOrNull { f -> f.faceId == it.faceId } }
        fun art(card: PlayCard?, hidden: Boolean): ArtworkReference? {
            val f = face(card) ?: return null
            if (hidden || (f.scryfallId == null && f.imageUri == null)) return null
            return ArtworkReference(f.scryfallId, definitions[card!!.definition]!!.faces.indexOf(f), card.definition, "small", f.imageUri)
        }
        val sidebar = if (landscape) sidebarWidth(width) else 0f
        val zoom = ui.camera.zoom.takeIf { it.isFinite() }?.coerceIn(MIN_ZOOM, MAX_ZOOM) ?: 1f
        val logicalRight = play.board.containers.filter { it.zone == PlayZone.BATTLEFIELD }.maxOfOrNull { c ->
            c.cards.mapIndexed { n, id -> (positions[id]?.column ?: n.toFloat()) + 1f }.maxOrNull() ?: 4f
        } ?: 4f
        val camera = TableCamera(
            ui.camera.x.takeIf { it.isFinite() }?.coerceIn(-GRID, max(GRID, logicalRight * GRID - (width - sidebar) / zoom + GRID)) ?: 0f,
            ui.camera.y.takeIf { it.isFinite() }?.coerceIn(-GRID, max(3 * GRID, (positions.values.maxOfOrNull { it.row } ?: 0f) * GRID + GRID)) ?: 0f, zoom)
        val cw = 28f * zoom; val ch = 40f * zoom; val envelope = GRID * zoom
        val combat = CombatStackPresentation.combat(play.board, cw, ch)
        val railHeight = if (landscape) max(48f, ch + 8f) else max(52f, ch + 12f)
        val controlsHeight = if (landscape) 0f else if (reviewing) 116f else 56f
        val dockRow = max(68f, ch + 16f)
        val counterHeight = 48f
        // Absent values still have six accessible blank controls, in both orientations.
        val dockHeight = 2 * (dockRow + counterHeight) + combat.height
        val fieldHeight = ((height - HEADER - 2 * railHeight - controlsHeight - dockHeight - 6f) / 2).coerceAtLeast(32f)
        val boardTop = HEADER + 4f
        val boardBottom = height - railHeight - if (landscape) 8f else 4f
        val fieldBottom = boardBottom - if (landscape && reviewing) 54f else 0f
        val middle = (boardTop + fieldBottom) / 2
        val dockTop = HEADER + railHeight + 6f + fieldHeight
        val fields = players.mapIndexed { index, player ->
            val y = if (landscape) if (index == 0) boardTop else middle + combat.height / 2
                else if (index == 0) HEADER + railHeight + 6f else dockTop + dockHeight
            val h = if (landscape) (fieldBottom - boardTop - combat.height) / 2 else fieldHeight
            val bounds = TableBounds(sidebar + 6f, y, width - sidebar - 12f, h.coerceAtLeast(1f))
            // Short viewports crop the outer edge rather than shrinking the three logical rows.
            val gridHeight = max(3 * GRID, bounds.height)
            val grid = bounds.copy(y = if (index == 0) bounds.bottom - gridHeight else bounds.y, height = gridHeight)
            TableField(bounds, player.accountId, container(player.accountId, PlayZone.BATTLEFIELD).id, grid)
        }
        val sidebarBounds = TableBounds(0f, boardTop, sidebar, (boardBottom - boardTop).coerceAtLeast(0f))
        val sidebarRow = dockRow + counterHeight
        val sidebarContent = max(2 * sidebarRow + envelope, sidebarBounds.height)
        val sidebarScroll = if (landscape) (ui.scrollY["sidebar"] ?: 0f).coerceIn(0f, (sidebarContent - sidebarBounds.height).coerceAtLeast(0f)) else 0f
        val items = mutableListOf<TableItem>()
        val ports = mutableListOf<TableViewport>()
        fun port(id: String, bounds: TableBounds, content: Float, hand: Boolean = false): TableViewport {
            val offset = (ui.scroll[id] ?: 0f).coerceIn(0f, (content - bounds.width).coerceAtLeast(0f))
            return TableViewport(id, bounds, content, offset, hand).also(ports::add)
        }
        fun card(card: PlayCard, b: TableBounds, footprint: TableBounds, region: String?, zone: PlayZone, viewport: String,
            viewId: String = card.id, projection: Boolean = false, frozen: Boolean = false) {
            val hidden = !CardVisibility.isVisibleFaceUp(card, zone, region, viewer)
            val f = face(card)
            val title = if (hidden) "Covered card" else f?.name ?: "Card (definition unavailable)"
            val label = "$title, owner ${name(card.owner)}, ${if (card.tapped) "tapped" else "untapped"}"
            items += TableItem("card/$viewId", b, if (projection) "${if (frozen) "Retained effect, read-only" else "Projection"}, $label" else label,
                if (hidden) "" else title, card.owner, zone, kind = TableItemKind.CARD, viewport = viewport,
                cardId = viewId, covered = hidden, tapped = card.tapped, corner = if (hidden) "" else card.corner,
                footprint = footprint, sourceId = if (frozen) null else card.id, projection = projection, frozen = frozen,
                token = card.token, note = if (hidden) "" else card.note, counters = if (hidden) emptyMap() else card.counters,
                typeIcon = if (hidden) "" else cardTypeIcon(f?.typeLine.orEmpty()), artwork = art(card, hidden))
        }
        for ((index, player) in players.withIndex()) {
            val ownSide = player.accountId == viewer
            val hand = container(player.accountId, PlayZone.HAND)
            val handBounds = if (!ownSide) {
                if (landscape) TableBounds(sidebar + 10f, 0f, (width - sidebar - toolbarWidth - 10f).coerceAtLeast(28f), HEADER)
                else TableBounds(0f, HEADER, width, railHeight)
            } else TableBounds(if (landscape && !reviewing) 64f else 8f, height - controlsHeight - railHeight - if (landscape) 2f else 0f,
                (if (landscape && reviewing) width / 2 else width - if (landscape) 356f else 16f).coerceAtLeast(28f), railHeight)
            val handContent = if (hand.cards.isEmpty()) 0f else (hand.cards.size - 1) * (cw + 4f * zoom) + cw
            val hp = port(hand.id, handBounds.copy(width = (handBounds.width - 24f).coerceAtLeast(1f)), handContent, true)
            val start = hp.bounds.x + ((hp.bounds.width - handContent) / 2).coerceAtLeast(0f) - hp.scroll
            items += TableItem(hand.id, handBounds, "${name(player.accountId)} Hand, ${hand.cards.size} cards", hand.cards.size.toString(),
                player.accountId, PlayZone.HAND, hand.cards, TableItemKind.HAND, hp.id)
            hand.cards.forEachIndexed { n, id -> byId[id]?.let { c ->
                val w = if (c.tapped) ch else cw; val h = if (c.tapped) cw else ch
                val b = TableBounds(start + n * (cw + 4f * zoom), handBounds.y + (handBounds.height - h) / 2, w, h)
                card(c, b, b, player.accountId, PlayZone.HAND, hp.id)
            } }
            val field = fields[index]
            val battlefield = container(player.accountId, PlayZone.BATTLEFIELD)
            battlefield.cards.sortedWith(compareByDescending<String> { positions[it]?.pileIndex ?: 0 }.thenBy { battlefield.cards.indexOf(it) })
                .forEach { id -> byId[id]?.let { c ->
                    val p = positions[id] ?: TablePlacement(battlefield.cards.indexOf(id), 0)
                    val x = field.gridBounds.x + (p.column * GRID - camera.x) * zoom + 16f * zoom
                    val dy = (p.row * GRID - camera.y) * zoom
                    val y = if (ownSide) field.gridBounds.bottom - envelope - dy else field.gridBounds.y + dy
                    val peek = p.pileIndex.coerceIn(0, 2) * 4f * zoom
                    val b = TableBounds(x + peek, y + peek + 4f * zoom, if (c.tapped) ch else cw, if (c.tapped) cw else ch)
                    card(c, b, TableBounds(x, y, envelope, envelope), player.accountId, PlayZone.BATTLEFIELD, battlefield.id)
                } }
            val logicalWidth = max(4f, battlefield.cards.mapIndexed { n, id -> (positions[id]?.column ?: n.toFloat()) + 1f }.maxOrNull() ?: 4f) * GRID
            port(battlefield.id, field.bounds, logicalWidth * zoom)
            val rowY = if (landscape) boardTop + (if (index == 0) 0f else sidebarContent - sidebarRow) - sidebarScroll
                else dockTop + index * (dockRow + counterHeight + combat.height)
            val dock = TableBounds(0f, rowY, if (landscape) sidebar else width * .8f, dockRow)
            val spacing = max(envelope, (dock.width - 64f) / 3)
            val dp = port("dock/${player.accountId}", dock, 76f + 2 * spacing + envelope)
            items += TableItem("life/${player.accountId}", TableBounds(10f - dp.scroll, rowY + 8f, 48f, 48f),
                "${name(player.accountId)}, life ${player.life}", player.life, player.accountId, kind = TableItemKind.LIFE, viewport = dp.id)
            for ((n, zone) in listOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE).withIndex()) {
                val c = container(player.accountId, zone)
                val top = byId[c.cards.firstOrNull()]
                val hidden = top == null || !CardVisibility.isVisibleFaceUp(top, zone, player.accountId, viewer)
                val b = TableBounds(76f + n * spacing - dp.scroll, rowY + 4f, cw + 12f * zoom, ch + 12f * zoom)
                items += TableItem(c.id, b, "${name(player.accountId)} ${zone.name.lowercase()}, ${c.cards.size} cards", c.cards.size.toString(),
                    top?.owner ?: player.accountId, zone, c.cards, viewport = dp.id, covered = hidden,
                    footprint = TableBounds(b.x, b.y, envelope, envelope), peekOwners = c.cards.take(3).mapNotNull { byId[it]?.owner },
                    note = if (hidden) "" else face(top)?.name.orEmpty(), corner = if (hidden) "" else top?.corner.orEmpty(),
                    typeIcon = if (hidden) "" else cardTypeIcon(face(top)?.typeLine.orEmpty()), artwork = art(top, hidden))
            }
            run {
                val cp = port("counters/${player.accountId}", TableBounds(0f, rowY + dockRow, if (landscape) sidebar else width, counterHeight),
                    max(if (landscape) sidebar else width, 288f))
                for ((n, color) in CounterColor.entries.withIndex()) {
                    val v = player.counters[color].orEmpty()
                    val b = TableBounds(n * cp.contentWidth / 6 - cp.scroll, cp.bounds.y, cp.contentWidth / 6, counterHeight)
                    items += TableItem("counter/${player.accountId}/$color", b, "${name(player.accountId)}, ${color.name.lowercase()}, ${v.ifEmpty { "no value" }}",
                        v, player.accountId, kind = TableItemKind.COUNTER, viewport = cp.id, counter = color)
                }
            }
        }
        val stack = container(null, PlayZone.STACK)
        val stackGeometry = CombatStackPresentation.stack(play.board, cw, ch)
        val sb = TableBounds(if (landscape) (sidebar - envelope) / 2 else width * .83f,
            if (landscape) boardTop + sidebarRow + (sidebarContent - 2 * sidebarRow - envelope) / 2 - sidebarScroll else dockTop + 4f,
            envelope, envelope)
        val sp = port(stack.id, sb, envelope)
        items += TableItem(stack.id, sb, "Stack, ${stack.cards.size} entries", stack.cards.size.toString(), zone = PlayZone.STACK,
            cards = stack.cards, viewport = sp.id, footprint = sb, peekOwners = stackGeometry.visible.map { it.card.owner })
        for (drawing in stackGeometry.visible.asReversed()) {
            val b = drawing.bounds.copy(x = sb.x + drawing.bounds.x, y = sb.y + drawing.bounds.y)
            card(drawing.card, b, sb, null, PlayZone.STACK, sp.id, drawing.viewId, drawing.projection, drawing.frozen)
            items[items.lastIndex] = items.last().copy(id = "stackDrawing/${drawing.viewId}", cards = stack.cards)
        }
        val combatY = if (landscape) middle - combat.height / 2 else dockTop + dockRow + counterHeight
        val combatBounds = TableBounds(sidebar, combatY, width - sidebar, combat.height)
        val cp = port("combat", combatBounds, combat.width)
        fun translated(b: TableBounds) = b.copy(x = b.x + sidebar - cp.scroll, y = b.y + combatY)
        for (drawing in combat.cards) card(drawing.card, translated(drawing.bounds), translated(drawing.bounds), null,
            PlayZone.BATTLEFIELD, cp.id, drawing.viewId, projection = true)
        val drops = combat.destinations.mapNotNull { region -> translated(region.bounds).intersection(combatBounds)?.let { region.copy(bounds = it) } }
        val dividers = combat.dividers.map { x -> TablePoint(sidebar + x - cp.scroll, combatY) to TablePoint(sidebar + x - cp.scroll, combatY + combat.height) }
        val clippedPorts = if (landscape) ports.map { p ->
            if (p.id.startsWith("dock/") || p.id.startsWith("counters/") || p.id == stack.id)
                p.copy(bounds = p.bounds.intersection(sidebarBounds) ?: p.bounds.copy(height = 0f)) else p
        } + TableViewport("sidebar", sidebarBounds, sidebar, 0f, contentHeight = sidebarContent, scrollY = sidebarScroll) else ports
        val realSources = play.board.cards.associate { c -> c.id to TableMoveSource(c.containerId, positions[c.id],
            play.board.containers.any { it.id == c.containerId && it.zone == PlayZone.LIBRARY && it.cards.firstOrNull() == c.id }) }
        val sources = realSources + items.filter { it.projection && it.sourceId != null }.associate { item ->
            item.cardId!! to realSources.getValue(item.sourceId!!).copy(sourceId = item.sourceId) }
        return clip(TableScene(items, fields, clippedPorts, cw, ch, envelope, camera, sources, drops, dividers), width, height)
    }

    /** Used after animation too: rendered bounds own drawing, targeting and accessibility. */
    fun clip(scene: TableScene, width: Float, height: Float): TableScene {
        val screen = TableBounds(0f, 0f, width, height)
        val clipped = scene.items.map { i ->
            val viewport = scene.viewports.firstOrNull { it.id == i.viewport }
            i.copy(visible = listOfNotNull(viewport?.bounds?.let(i.bounds::intersection)?.intersection(screen)))
        }
        val visible = clipped.mapIndexed { n, i ->
            val portions = if (i.kind == TableItemKind.HAND) i.visible else clipped.drop(n + 1)
                .filter { it.kind != TableItemKind.HAND && !(i.zone == PlayZone.STACK && i.kind == TableItemKind.PILE && it.viewport == i.viewport) }
                .flatMap { it.visible }.fold(i.visible) { regions, cover -> regions.flatMap { it.subtract(cover) } }
            i.copy(visible = portions)
        }
        return scene.copy(items = visible)
    }
    fun nonzero(value: String): Boolean = value.trim().let { s -> s.isNotEmpty() &&
        (!Regex("[+-]?[0-9]+").matches(s) || s.removePrefix("+").removePrefix("-").any { it != '0' }) }
    private fun cardTypeIcon(type: String) = when {
        "Land" in type -> "♧"; "Creature" in type -> "♙"; "Instant" in type -> "ϟ"
        "Sorcery" in type -> "◇"; "Artifact" in type -> "⚙"; "Enchantment" in type -> "☆"; else -> "◇"
    }
    fun logicalPlacements(play: PlayState): Map<String, TablePlacement> = play.board.cards.mapNotNull { card ->
        card.placement?.let { p ->
            val members = play.board.containers.single { it.id == card.containerId }.cards.filter { id ->
                play.board.cards.single { it.id == id }.placement == p }
            card.id to TablePlacement(p.position.x, p.position.y, members.indexOf(card.id), p.pileId)
        }
    }.toMap()
    /** Menu toggles must not mistake a legacy default cover for an explicit public face-down edit. */
    fun cardForActions(play: PlayState, cardId: String): PlayCard {
        val card = play.board.cards.single { it.id == cardId }
        val zone = play.board.containers.single { it.id == card.containerId }.zone
        return if (zone == PlayZone.LIBRARY || zone == PlayZone.HAND) card else card.copy(covered = card.isFaceDown)
    }

    fun inspection(play: PlayState, viewer: String, cardId: String): CardDefinition? {
        val card = play.board.cards.firstOrNull { it.id == cardId } ?: return null
        val region = play.board.containers.firstOrNull { it.id == card.containerId }?.accountId
        if (region != viewer && !CardVisibility.isVisibleFaceUp(play.board, card, viewer))
            return CardDefinition("Covered card", listOf(DefinitionFace("covered", "Covered card")))
        val definition = play.definitions.firstOrNull { it.digest == card.definition }?.definition
            ?: return CardDefinition("Card unavailable", listOf(DefinitionFace("unavailable", "Downloaded definition unavailable")))
        if (region == viewer) return definition
        val face = definition.faces.firstOrNull { it.faceId == card.faceId }
            ?: return CardDefinition("Card unavailable", listOf(DefinitionFace("unavailable", "Downloaded face unavailable")))
        return definition.copy(name = face.name, faces = listOf(face))
    }
}
