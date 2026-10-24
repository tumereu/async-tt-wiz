package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ArtworkReference
import kotlin.math.max

/** An accepted prediction must not briefly fall back to an older asynchronous history view. */
internal class TableEndpointPresentation {
    private var presented: PlayState? = null
    fun live(live: PlayState?, indexed: PlayState?, awaitingIndex: Boolean): PlayState? {
        val matchesPresented = live != null && live.id == presented?.id &&
            (live.board == presented?.board || live.currentNodeId == presented?.currentNodeId)
        return if (awaitingIndex && !matchesPresented) indexed else live
    }
    fun presented(state: PlayState?) { if (state != null) presented = state }
}

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
/** One affine transform for the entire world. Camera offsets are world units from its home center. */
data class TableWorld(val viewport: TableBounds, val bounds: TableBounds) {
    private val home get() = TablePoint(viewport.width / 2, viewport.height / 2)
    fun screen(p: TablePoint, camera: TableCamera) = TablePoint(
        viewport.center.x + (p.x - home.x - camera.x) * camera.zoom,
        viewport.center.y + (p.y - home.y - camera.y) * camera.zoom)
    fun screen(b: TableBounds, camera: TableCamera): TableBounds = screen(TablePoint(b.x, b.y), camera).let {
        TableBounds(it.x, it.y, b.width * camera.zoom, b.height * camera.zoom)
    }
    fun clamp(camera: TableCamera): TableCamera {
        val c = normalize(camera)
        // Sort the two edge alignments: large content covers the viewport; small content stays inside it.
        fun offset(value: Float, start: Float, end: Float, home: Float, extent: Float): Float {
            val a = start - home + extent / (2 * c.zoom)
            val b = end - home - extent / (2 * c.zoom)
            return value.coerceIn(minOf(a, b), maxOf(a, b))
        }
        return c.copy(x = offset(c.x, bounds.x, bounds.right, home.x, viewport.width),
            y = offset(c.y, bounds.y, bounds.bottom, home.y, viewport.height))
    }
    fun normalize(camera: TableCamera) = TableCamera(camera.x.takeIf { it.isFinite() } ?: 0f,
        camera.y.takeIf { it.isFinite() } ?: 0f,
        camera.zoom.takeIf { it.isFinite() }?.coerceIn(TablePresentation.MIN_ZOOM, TablePresentation.MAX_ZOOM) ?: 1f)
    /** Bounded, progressively stronger resistance, measured in screen dp regardless of zoom. */
    fun resist(camera: TableCamera): TableCamera {
        val c = normalize(camera)
        val edge = clamp(c)
        fun rubber(value: Float, bound: Float): Float {
            val delta = value - bound
            val limit = 96f / c.zoom
            return bound + delta / (2f + kotlin.math.abs(delta) / limit)
        }
        return c.copy(x = rubber(c.x, edge.x), y = rubber(c.y, edge.y))
    }
    /** Resume a grabbed spring at the same displayed position, without applying resistance twice. */
    fun unresist(camera: TableCamera): TableCamera {
        val c = normalize(camera)
        val edge = clamp(c)
        fun raw(value: Float, bound: Float): Float {
            val delta = value - bound
            return bound + 2f * delta / (1f - kotlin.math.abs(delta) * c.zoom / 96f).coerceAtLeast(.001f)
        }
        return c.copy(x = raw(c.x, edge.x), y = raw(c.y, edge.y))
    }
    fun horizontalBand(band: TableBounds) = TableBounds(viewport.x, band.y, viewport.width, band.height)
        .intersection(viewport) ?: TableBounds(viewport.x, viewport.y, viewport.width, 0f)
    fun fit(): TableCamera = clamp(TableCamera(bounds.center.x - home.x, bounds.center.y - home.y,
        minOf(viewport.width / bounds.width, viewport.height / bounds.height).coerceIn(TablePresentation.MIN_ZOOM, TablePresentation.MAX_ZOOM)))
}
data class TablePlacement(val column: Float, val row: Float, val pileIndex: Int = 0, val pileId: String? = null) {
    constructor(column: Int, row: Int, pileIndex: Int = 0, pileId: String? = null) : this(column.toFloat(), row.toFloat(), pileIndex, pileId)
}

/** Reserve the same footprint for either orientation and all three top-left peeks. */
internal fun battlefieldCardBounds(footprint: TableBounds, width: Float, height: Float, pileIndex: Int, zoom: Float): TableBounds {
    val peek = pileIndex.coerceIn(0, 2) * 4f
    return TableBounds(footprint.x + (8f - peek) * zoom,
        footprint.y + (footprint.height - height) / 2 + (4f - peek) * zoom, width, height)
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
    val typeIcon: String = "", val artwork: ArtworkReference? = null, val drawHandId: String? = null,
    val inTransit: Boolean = false
)
data class TableField(val bounds: TableBounds, val owner: String, val containerId: String = "",
    val gridBounds: TableBounds = bounds, val rowHeight: Float = TablePresentation.GRID,
    val holdsAttention: Boolean = false) {
    @Suppress("UNUSED_PARAMETER")
    fun landsBounds(atBottom: Boolean, camera: TableCamera = TableCamera()) = gridBounds.copy(
        y = if (atBottom) gridBounds.bottom - rowHeight else gridBounds.y, height = rowHeight)
}
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
    val stackBubbles: StackBubbleGeometry? = null, val stackRegion: TableBounds? = null,
    val world: TableWorld? = null, val stackProjections: List<TableItem> = emptyList()) {
    fun worldInput(point: TablePoint) = world?.viewport?.contains(point) == true &&
        stackRegion?.contains(point) != true && stackBubbles?.controls?.any { it.bounds.contains(point) } != true
    fun hit(point: TablePoint): TableItem? {
        stackBubbles?.controls?.firstOrNull { it.bounds.contains(point) }?.let { return it }
        val stack = items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE &&
            it.visible.any { bounds -> bounds.contains(point) } }
        if (stack != null) {
            if (stack.stackBadge.contains(point)) return stack
            // Visible lower peeks retain their identity for hold-to-inspect, not top-card shortcuts.
            return items.lastOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.CARD &&
                it.viewport == stack.viewport && it.visible.any { bounds -> bounds.contains(point) } } ?: stack
        }
        return items.asReversed().firstOrNull { item -> item.visible.any { it.contains(point) } }
    }
    fun rectangle(bounds: TableBounds): Set<String> = items.filter { it.kind == TableItemKind.CARD &&
        it.visible.any { part -> part.intersection(bounds) != null } }.mapNotNull { it.cardId }.toSet()
    fun viewport(point: TablePoint) = viewports.firstOrNull { it.bounds.contains(point) }
    val stackTopId get() = items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }
        ?.cards?.firstOrNull() ?: items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.CARD }?.cards?.firstOrNull()
    fun canDrag(item: TableItem): Boolean = !item.frozen && (item.zone != PlayZone.STACK ||
        item.cardId != null && item.cardId == stackTopId)
}

internal val TableItem.stackBadge get() = TableBounds(bounds.x, bounds.y, 20f, 20f)
internal val TableItem.visibleAnchor get() = if (visible.any { it.contains(bounds.center) }) bounds.center
    else visible.maxByOrNull { it.width * it.height }?.center

object TablePresentation {
    const val GRID = 56f
    const val MIN_ZOOM = .65f
    const val MAX_ZOOM = 2f
    const val HEADER = 48f
    const val TOOLBAR = 264f
    fun sidebarWidth(width: Float) = minOf(326f, width * .366f)

    fun stackBubbles(scene: TableScene, play: PlayState, entryId: String?, width: Float, height: Float): StackBubbleGeometry? {
        if (entryId != CombatStackRules.stack(play.board).cards.firstOrNull()) return null
        val card = scene.items.find { entryId != null && it.cardId == entryId && it.zone == PlayZone.STACK && it.visible.isNotEmpty() }
            ?: return null
        val pile = scene.items.single { it.id == card.viewport }
        return StackBubbleGeometry.create(requireNotNull(entryId), pile.bounds, width, height,
            play.board.stackTextMarks[entryId], if (card.covered) 0 else StackText.options(play, entryId).size)
    }

    fun scene(game: GameSummary, play: PlayState, viewer: String, width: Float, height: Float, landscape: Boolean,
        ui: TableLayoutState = TableLayoutState(), placements: Map<String, TablePlacement> = emptyMap(),
        toolbarWidth: Float = TOOLBAR, reviewing: Boolean = false, combatBandHeight: Float? = null): TableScene {
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
        val hudWidth = 28f; val hudHeight = 40f; val hudEnvelope = GRID
        val logicalRight = play.board.containers.filter { it.zone == PlayZone.BATTLEFIELD }.maxOfOrNull { c ->
            c.cards.mapIndexed { n, id -> (positions[id]?.column ?: n.toFloat()) + 1f }.maxOrNull() ?: 4f
        } ?: 4f
        val combat = CombatStackPresentation.combat(play.board, hudWidth, hudHeight)
        val railHeight = if (landscape) 48f else 52f
        val controlsHeight = if (landscape) 0f else if (reviewing) 116f else 56f
        val dockRow = 68f
        val counterHeight = 48f
        val counterHeights = players.map { if (landscape || it.counters.values.any(::nonzero)) counterHeight else 0f }
        val middleHeight = max(combat.height, hudEnvelope + 8f)
        val boardTop = HEADER + 4f
        val boardBottom = height - railHeight - if (landscape) 8f else 4f
        val ownDockY = height - controlsHeight - dockRow - counterHeights[1]
        val worldTop = if (landscape) boardTop else HEADER + dockRow + counterHeights[0] + railHeight
        val worldBottom = if (landscape) boardBottom - if (reviewing) 54f else 0f else ownDockY - railHeight
        val worldViewport = TableBounds(sidebar + 6f, worldTop, (width - sidebar - 12f).coerceAtLeast(1f),
            (worldBottom - worldTop).coerceAtLeast(1f))
        // Reserve room for a normal open combat lane at home without changing row spacing when it opens.
        val normalCombatBand = (hudHeight + 2 * hudWidth * .04f) * 2 + 4f
        val rowHeight = ((worldViewport.height - normalCombatBand) / 6).coerceAtLeast(12f)
        val fieldHeights = players.map { player ->
            val rows = container(player.accountId, PlayZone.BATTLEFIELD).cards.maxOfOrNull { positions[it]?.row ?: 0f } ?: 0f
            max(3f, rows + 1f) * rowHeight
        }
        val bandHeight = combatBandHeight ?: max(12f, combat.height + 4f)
        val bandY = worldViewport.height / 2 - bandHeight / 2
        val logicalFields = listOf(TableBounds(0f, bandY - fieldHeights[0], worldViewport.width, fieldHeights[0]),
            TableBounds(0f, bandY + bandHeight, worldViewport.width, fieldHeights[1]))
        val world = TableWorld(worldViewport, TableBounds(0f, logicalFields.first().y,
            maxOf(worldViewport.width, logicalRight * GRID + 32f, combat.width),
            logicalFields.last().bottom - logicalFields.first().y))
        // Gestures own resistance/settling. Do not hard-clamp away their overscroll animation here.
        val camera = world.normalize(ui.camera)
        val zoom = camera.zoom
        val cw = hudWidth * zoom; val ch = hudHeight * zoom; val envelope = GRID * zoom
        val fields = players.mapIndexed { index, player ->
            val grid = world.screen(logicalFields[index].copy(width = world.bounds.width), camera)
            val bounds = grid.intersection(worldViewport) ?: grid.copy(width = 0f, height = 0f)
            TableField(bounds, player.accountId, container(player.accountId, PlayZone.BATTLEFIELD).id,
                gridBounds = grid, rowHeight = rowHeight * zoom, holdsAttention = player.accountId == play.board.effectiveAttentionHolder)
        }
        val sidebarBounds = TableBounds(0f, boardTop, sidebar, (boardBottom - boardTop).coerceAtLeast(0f))
        val sidebarRow = dockRow + counterHeight
        val sidebarContent = max(2 * sidebarRow + middleHeight, sidebarBounds.height)
        val sidebarScroll = if (landscape) (ui.scrollY["sidebar"] ?: 0f).coerceIn(0f, (sidebarContent - sidebarBounds.height).coerceAtLeast(0f)) else 0f
        val items = mutableListOf<TableItem>()
        val ports = mutableListOf<TableViewport>()
        fun port(id: String, bounds: TableBounds, content: Float, hand: Boolean = false): TableViewport {
            val offset = (ui.scroll[id] ?: 0f).coerceIn(0f, (content - bounds.width).coerceAtLeast(0f))
            return TableViewport(id, bounds, content, offset, hand).also(ports::add)
        }
        fun card(card: PlayCard, b: TableBounds, footprint: TableBounds, region: String?, zone: PlayZone, viewport: String,
            viewId: String = card.id, projection: Boolean = false, frozen: Boolean = false, draw: Boolean = true): TableItem {
            val hidden = if (!frozen) !CardVisibility.isVisibleFaceUp(play.board, card, viewer)
                else !CardVisibility.isVisibleFaceUp(card, zone, region, viewer)
            val f = face(card)
            val title = if (hidden) "Covered card" else f?.name ?: "Card (definition unavailable)"
            val tapped = zone != PlayZone.STACK && card.tapped
            val label = "$title, owner ${name(card.owner)}, ${if (tapped) "tapped" else "untapped"}"
            return TableItem("card/$viewId", b, if (projection) "${if (frozen) "Retained effect, read-only" else "Projection"}, $label" else label,
                if (hidden) "" else title, card.owner, zone, kind = TableItemKind.CARD, viewport = viewport,
                cardId = viewId, covered = hidden, tapped = tapped, corner = if (hidden) "" else card.corner,
                footprint = footprint, sourceId = if (frozen) null else card.id, projection = projection, frozen = frozen,
                token = card.token, note = if (hidden) "" else card.note, counters = if (hidden) emptyMap() else card.counters,
                typeIcon = if (hidden) "" else cardTypeIcon(f?.typeLine.orEmpty()), artwork = art(card, hidden)).also { if (draw) items += it }
        }
        for ((index, player) in players.withIndex()) {
            val ownSide = player.accountId == viewer
            val hand = container(player.accountId, PlayZone.HAND)
            val handBounds = if (!ownSide) {
                if (landscape) TableBounds(sidebar + 10f, 0f, (width - sidebar - toolbarWidth - 10f).coerceAtLeast(28f), HEADER)
                else TableBounds(0f, worldTop - railHeight, width, railHeight)
            } else TableBounds(if (landscape && !reviewing) 64f else 8f, (if (landscape) height - controlsHeight else ownDockY) - railHeight - if (landscape) 2f else 0f,
                (if (landscape && reviewing) width / 2 else width - if (landscape) 356f else 16f).coerceAtLeast(28f), railHeight)
            val handContent = if (hand.cards.isEmpty()) 0f else (hand.cards.size - 1) * (hudWidth + 4f) + hudWidth
            val hp = port(hand.id, handBounds.copy(width = (handBounds.width - 24f).coerceAtLeast(1f)), handContent, true)
            val start = hp.bounds.x + ((hp.bounds.width - handContent) / 2).coerceAtLeast(0f) - hp.scroll
            items += TableItem(hand.id, handBounds, "${name(player.accountId)} Hand, ${hand.cards.size} cards", hand.cards.size.toString(),
                player.accountId, PlayZone.HAND, hand.cards, TableItemKind.HAND, hp.id)
            hand.cards.forEachIndexed { n, id -> byId[id]?.let { c ->
                val w = if (c.tapped) hudHeight else hudWidth; val h = if (c.tapped) hudWidth else hudHeight
                val b = TableBounds(start + n * (hudWidth + 4f), handBounds.y + (handBounds.height - h) / 2, w, h)
                card(c, b, b, player.accountId, PlayZone.HAND, hp.id)
            } }
            val field = fields[index]
            val battlefield = container(player.accountId, PlayZone.BATTLEFIELD)
            battlefield.cards.sortedWith(compareByDescending<String> { positions[it]?.pileIndex ?: 0 }.thenBy { battlefield.cards.indexOf(it) })
                .forEach { id -> byId[id]?.let { c ->
                    val p = positions[id] ?: TablePlacement(battlefield.cards.indexOf(id), 0)
                    val x = field.gridBounds.x + p.column * GRID * zoom + 16f * zoom
                    val rowHeight = field.rowHeight
                    val dy = p.row * rowHeight
                    val rowY = if (ownSide) field.gridBounds.bottom - rowHeight - dy else field.gridBounds.y + dy
                    val y = rowY + (rowHeight - envelope) / 2
                    val cardHeight = if (c.tapped) cw else ch
                    val footprint = TableBounds(x, y, envelope, envelope)
                    val b = battlefieldCardBounds(footprint, if (c.tapped) ch else cw, cardHeight, p.pileIndex, zoom)
                    card(c, b, footprint, player.accountId, PlayZone.BATTLEFIELD, battlefield.id)
                } }
            val logicalWidth = max(4f, battlefield.cards.mapIndexed { n, id -> (positions[id]?.column ?: n.toFloat()) + 1f }.maxOrNull() ?: 4f) * GRID
            port(battlefield.id, field.bounds, logicalWidth * zoom)
            val rowY = if (landscape) boardTop + (if (index == 0) 0f else sidebarContent - sidebarRow) - sidebarScroll
                else if (index == 0) HEADER else ownDockY
            val dock = TableBounds(0f, rowY, if (landscape) sidebar else width, dockRow)
            val spacing = max(hudEnvelope, (dock.width - 64f) / 3)
            val dp = port("dock/${player.accountId}", dock, 76f + 2 * spacing + hudEnvelope)
            items += TableItem("life/${player.accountId}", TableBounds(10f - dp.scroll, rowY + 8f, 48f, 48f),
                "${name(player.accountId)}, life ${player.life}", player.life, player.accountId, kind = TableItemKind.LIFE, viewport = dp.id)
            for ((n, zone) in listOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE).withIndex()) {
                val c = container(player.accountId, zone)
                val top = byId[c.cards.firstOrNull()]
                val hidden = top == null || !CardVisibility.isVisibleFaceUp(top, zone, player.accountId, viewer)
                val b = TableBounds(76f + n * spacing - dp.scroll, rowY + 4f, hudWidth + 12f, hudHeight + 12f)
                items += TableItem(c.id, b, "${name(player.accountId)} ${zone.name.lowercase()}, ${c.cards.size} cards", c.cards.size.toString(),
                    top?.owner ?: player.accountId, zone, c.cards, viewport = dp.id, covered = hidden,
                    footprint = TableBounds(b.x, b.y, hudEnvelope, hudEnvelope), peekOwners = c.cards.take(3).mapNotNull { byId[it]?.owner },
                    token = top?.token == true,
                    drawHandId = hand.id.takeIf { ownSide && zone == PlayZone.LIBRARY },
                    note = if (hidden) "" else face(top)?.name.orEmpty(), corner = if (hidden) "" else top?.corner.orEmpty(),
                    typeIcon = if (hidden) "" else cardTypeIcon(face(top)?.typeLine.orEmpty()), artwork = art(top, hidden))
            }
            if (counterHeights[index] > 0f) {
                val cp = port("counters/${player.accountId}", TableBounds(0f, rowY + dockRow, dock.width, counterHeight),
                    max(dock.width, 288f))
                for ((n, color) in CounterColor.entries.withIndex()) {
                    val v = player.counters[color].orEmpty()
                    val b = TableBounds(n * cp.contentWidth / 6 - cp.scroll, cp.bounds.y, cp.contentWidth / 6, counterHeight)
                    items += TableItem("counter/${player.accountId}/$color", b, "${name(player.accountId)}, ${color.name.lowercase()}, ${v.ifEmpty { "no value" }}",
                        v, player.accountId, kind = TableItemKind.COUNTER, viewport = cp.id, counter = color)
                }
            }
        }
        val stack = container(null, PlayZone.STACK)
        val stackGeometry = CombatStackPresentation.stack(play.board, hudWidth, hudHeight)
        val stackPadding = 4f
        val stackRegion = if (landscape) TableBounds(6f, boardTop + sidebarRow - sidebarScroll,
            sidebar - 6f, sidebarContent - 2 * sidebarRow)
            else TableBounds(width - hudEnvelope - 2 * stackPadding,
                height / 2 - hudEnvelope / 2 - stackPadding, hudEnvelope + 2 * stackPadding, hudEnvelope + 2 * stackPadding)
        val sb = TableBounds(stackRegion.center.x - hudEnvelope / 2, stackRegion.center.y - hudEnvelope / 2, hudEnvelope, hudEnvelope)
        val sp = port(stack.id, sb, hudEnvelope)
        items += TableItem(stack.id, sb, "Stack, ${stack.cards.size} entries", stack.cards.size.toString(), zone = PlayZone.STACK,
            cards = stack.cards, viewport = sp.id, footprint = sb, peekOwners = stackGeometry.visible.map { it.card.owner })
        for (drawing in stackGeometry.visible.asReversed()) {
            val b = drawing.bounds.copy(x = sb.x + drawing.bounds.x, y = sb.y + drawing.bounds.y)
            card(drawing.card, b, sb, null, PlayZone.STACK, sp.id, drawing.viewId, drawing.projection, drawing.frozen)
            items[items.lastIndex] = items.last().copy(id = "stackDrawing/${drawing.viewId}", cards = stack.cards)
        }
        // Animation metadata for ALL projections: a buried removal still sends a ghost home.
        val stackProjections = play.board.stackEffects.map { effect ->
            items.firstOrNull { it.cardId == effect.id } ?: card(CombatStackRules.inspection(play.board, effect.id)!!,
                TableBounds(sb.x, sb.y, hudWidth, hudHeight), sb, null, PlayZone.STACK, sp.id, effect.id,
                projection = true, frozen = effect.frozen != null, draw = false).copy(id = "stackDrawing/${effect.id}")
        }
        val combatY = worldViewport.height / 2 - combat.height / 2
        val combatBounds = world.screen(TableBounds(0f, maxOf(combatY, bandY), world.bounds.width, minOf(combat.height, bandHeight)), camera)
        val cp = port("combat", combatBounds.intersection(worldViewport) ?: combatBounds.copy(height = 0f), combat.width * zoom)
        fun translated(b: TableBounds) = world.screen(b.copy(y = b.y + combatY), camera)
        for (drawing in combat.cards) card(drawing.card, translated(drawing.bounds), translated(drawing.bounds), null,
            PlayZone.BATTLEFIELD, cp.id, drawing.viewId, projection = true)
        val drops = combat.destinations.mapNotNull { region -> translated(region.bounds).intersection(cp.bounds)?.let { region.copy(bounds = it) } }
        val dividers = combat.dividers.map { x -> world.screen(TablePoint(x, combatY), camera) to world.screen(TablePoint(x, combatY + combat.height), camera) }
        val clippedPorts = if (landscape) ports.map { p ->
            if (p.id.startsWith("dock/") || p.id.startsWith("counters/") || p.id == stack.id)
                p.copy(bounds = p.bounds.intersection(sidebarBounds) ?: p.bounds.copy(height = 0f)) else p
        } + TableViewport("sidebar", sidebarBounds, sidebar, 0f, contentHeight = sidebarContent, scrollY = sidebarScroll) else ports
        val realSources = play.board.cards.associate { c -> c.id to TableMoveSource(c.containerId, positions[c.id],
            play.board.containers.any { it.id == c.containerId && it.zone == PlayZone.LIBRARY && it.cards.firstOrNull() == c.id }) }
        val sources = realSources + items.filter { it.projection && it.sourceId != null }.associate { item ->
            item.cardId!! to realSources.getValue(item.sourceId!!).copy(sourceId = item.sourceId) }
        return clip(TableScene(items, fields, clippedPorts, cw, ch, envelope, camera, sources, drops, dividers,
            stackRegion = if (landscape) stackRegion.intersection(sidebarBounds) else stackRegion, world = world,
            stackProjections = stackProjections), width, height)
    }

    /** Used after animation too: rendered bounds own drawing, targeting and accessibility. */
    fun clip(scene: TableScene, width: Float, height: Float): TableScene {
        val screen = TableBounds(0f, 0f, width, height)
        val ordered = scene.items.filter { it.zone == PlayZone.BATTLEFIELD } + scene.items.filter { it.zone != PlayZone.BATTLEFIELD }
        val clipped = ordered.map { i ->
            val viewport = scene.viewports.firstOrNull { it.id == i.viewport }
            val regions = listOfNotNull((if (i.inTransit) {
                if (i.zone == PlayZone.BATTLEFIELD) scene.world?.viewport ?: screen else screen
            } else viewport?.bounds)?.let(i.bounds::intersection)?.intersection(screen))
            i.copy(visible = if (i.zone == PlayZone.BATTLEFIELD && scene.stackRegion != null)
                regions.flatMap { it.subtract(scene.stackRegion) } else regions)
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
