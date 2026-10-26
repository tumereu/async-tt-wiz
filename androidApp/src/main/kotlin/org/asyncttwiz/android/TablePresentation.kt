package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ArtworkReference
import kotlin.math.max

/** An accepted prediction must not briefly fall back to an older asynchronous history view. */
internal class TableEndpointPresentation {
    private var presented: PlayState? = null
    fun live(live: PlayState?, indexed: PlayState?, awaitingIndex: Boolean, panelReveal: String? = null): PlayState? {
        if (panelReveal != null) return indexed?.takeIf { it.currentNodeId == panelReveal } ?: presented
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
data class TableWorld(val viewport: TableBounds, val bounds: TableBounds, val panBounds: TableBounds = bounds,
    val clipViewport: TableBounds? = null) {
    val visibleViewport get() = clipViewport ?: viewport
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
        return c.copy(x = offset(c.x, panBounds.x, panBounds.right, home.x, viewport.width),
            y = offset(c.y, panBounds.y, panBounds.bottom, home.y, viewport.height))
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
        .intersection(visibleViewport) ?: TableBounds(viewport.x, viewport.y, viewport.width, 0f)
    fun fit(): TableCamera = clamp(TableCamera(bounds.center.x - home.x, bounds.center.y - home.y,
        minOf(viewport.width / bounds.width, viewport.height / bounds.height).coerceIn(TablePresentation.MIN_ZOOM, TablePresentation.MAX_ZOOM)))
}
data class TablePlacement(val column: Float, val row: Float, val pileIndex: Int = 0, val pileId: String? = null) {
    constructor(column: Int, row: Int, pileIndex: Int = 0, pileId: String? = null) : this(column.toFloat(), row.toFloat(), pileIndex, pileId)
}

/** Rotate in place; lower pile members peek left/up without changing the column pitch. */
internal fun battlefieldCardBounds(footprint: TableBounds, width: Float, height: Float, pileIndex: Int, zoom: Float): TableBounds {
    val peek = pileIndex.coerceIn(0, 2) * 4f
    return TableBounds(footprint.center.x - width / 2 - peek * zoom,
        footprint.center.y - height / 2 + (4f - peek) * zoom, width, height)
}

/** Ten percent above/below every face, plus the +/-4dp offsets of a three-card pile. */
internal fun battlefieldRowHeight(cardHeight: Float, scale: Float) = cardHeight * 1.2f + 8f * scale
data class TableLayoutState(val camera: TableCamera = TableCamera(), val scroll: Map<String, Float> = emptyMap(),
    val scrollY: Map<String, Float> = emptyMap(), val localPileId: String? = null)
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
    val inTransit: Boolean = false, val cardTransform: TableCardTransform? = null,
    internal val valueDragSide: ValueDragSide = ValueDragSide.BOTTOM,
    val panelRevealed: Boolean = false, val flipScale: Float = 1f, val copies: Int = 1, val faceTitle: String = "",
    val countBadge: TableBounds? = null, val typeLine: String = "", val keepTopRevealed: Boolean = false,
    val faceId: String? = null, val frontTypeLine: String = typeLine
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
internal const val WORLD_PAN_REGION = "world/camera"
data class TableScene(val items: List<TableItem>, val fields: List<TableField>,
    val viewports: List<TableViewport> = emptyList(), val cardWidth: Float = 28f, val cardHeight: Float = 40f,
    val footprint: Float = 56f, val camera: TableCamera = TableCamera(),
    val sources: Map<String, TableMoveSource> = emptyMap(), val combatDrops: List<CombatDropRegion> = emptyList(),
    val combatDividers: List<Pair<TablePoint, TablePoint>> = emptyList(),
    val stackBubbles: StackBubbleGeometry? = null, val stackRegion: TableBounds? = null,
    val world: TableWorld? = null, val stackProjections: List<TableItem> = emptyList(),
    val combatBubbles: StackBubbleGeometry? = null, val cardOwners: Map<String, String> = emptyMap(),
    internal val cardPanels: List<CardPanelGeometry> = emptyList(),
    val appearance: CardAppearance = CardAppearance()) {
    fun worldInput(point: TablePoint) = world?.visibleViewport?.contains(point) == true &&
        stackRegion?.contains(point) != true && items.none { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE && it.stackBadge.contains(point) } &&
        cardPanels.none { it.bounds.contains(point) } && stackBubbles?.controls?.any { it.bounds.contains(point) } != true
    fun hit(point: TablePoint): TableItem? {
        combatBubbles?.controls?.firstOrNull { it.bounds.contains(point) }?.let { return it }
        stackBubbles?.controls?.firstOrNull { it.bounds.contains(point) }?.let { return it }
        items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE && it.stackBadge.contains(point) }?.let { return it }
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
    /** Resolve once on first down, before a centroid can cross into another region. */
    fun panRegion(point: TablePoint): String? {
        cardPanels.firstOrNull { it.bounds.contains(point) }?.let { return it.viewport.id }
        if (stackRegion?.contains(point) == true || items.any { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE && it.stackBadge.contains(point) })
            return items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }?.viewport
        items.firstOrNull { it.kind == TableItemKind.HAND && it.bounds.contains(point) }?.let { return it.viewport }
        if (worldInput(point)) return WORLD_PAN_REGION
        return viewports.firstOrNull { it.bounds.contains(point) && (it.maxScroll > 0 || it.maxScrollY > 0) }?.id
            ?: viewport(point)?.id
    }
    val stackTopId get() = items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }
        ?.cards?.firstOrNull() ?: items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.CARD }?.cards?.firstOrNull()
    fun canDrag(item: TableItem): Boolean = !item.frozen && (item.zone != PlayZone.STACK ||
        item.cardId != null && item.cardId == stackTopId)
}

internal val TableItem.stackBadge get() = countBadge ?: TableBounds(bounds.x, bounds.y, 20f, 20f)
internal val TableScene.columnPitch get() = TablePresentation.COLUMN_PITCH * cardWidth / 28f
internal val TableItem.visibleAnchor get() = if (visible.any { it.contains(bounds.center) }) bounds.center
    else visible.maxByOrNull { it.width * it.height }?.center

object TablePresentation {
    const val GRID = 56f
    const val COLUMN_PITCH = 42f
    const val MIN_ZOOM = .65f
    const val MAX_ZOOM = 2f
    const val HEADER = 48f
    // Reserve the maximum chrome width so the opponent's fixed Hand does not move in review.
    const val TOOLBAR = 212f
    fun sidebarWidth(width: Float) = minOf(326f, width * .366f)

    fun stackBubbles(scene: TableScene, play: PlayState, entryId: String?, width: Float, height: Float): StackBubbleGeometry? {
        if (entryId != CombatStackRules.stack(play.board).cards.firstOrNull()) return null
        val card = scene.items.find { entryId != null && it.cardId == entryId && it.zone == PlayZone.STACK && it.visible.isNotEmpty() }
            ?: return null
        val pile = scene.items.single { it.id == card.viewport }
        return StackBubbleGeometry.create(requireNotNull(entryId), pile.bounds, width, height,
            play.board.stackTextMarks[entryId]?.minOrNull(), if (card.covered) 0 else StackText.options(play, entryId).size)
            .copy(multipleMarks = play.board.stackTextMarks[entryId].orEmpty().size > 1)
    }

    fun scene(game: GameSummary, play: PlayState, viewer: String, width: Float, height: Float, landscape: Boolean,
        ui: TableLayoutState = TableLayoutState(), placements: Map<String, TablePlacement> = emptyMap(),
        toolbarWidth: Float = TOOLBAR, reviewing: Boolean = false, combatBandHeight: Float? = null,
        appearance: CardAppearance = CardAppearance()): TableScene {
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
            return ArtworkReference(f.scryfallId, definitions[card!!.definition]!!.faces.indexOf(f), card.definition,
                customUrl = f.imageUri).forTable()
        }
        val sidebar = if (landscape) sidebarWidth(width) else 0f
        val hudWidth = 28f; val hudHeight = 40f; val hudEnvelope = GRID
        val battlefieldScale = cardScale(appearance.cardSize)
        val columnPitch = COLUMN_PITCH * battlefieldScale
        val handWidth = hudWidth * appearance.size(CardArea.HAND)
        val handHeight = hudHeight * appearance.size(CardArea.HAND)
        val pileWidth = hudWidth * appearance.size(CardArea.PILES)
        val pileHeight = hudHeight * appearance.size(CardArea.PILES)
        val pileEnvelope = maxOf(hudEnvelope, pileWidth + 24f)
        val stackWidth = hudWidth * appearance.size(CardArea.STACK)
        val stackHeight = hudHeight * appearance.size(CardArea.STACK)
        val logicalLeft = play.board.containers.filter { it.zone == PlayZone.BATTLEFIELD }.minOfOrNull { c ->
            c.cards.mapIndexed { n, id -> positions[id]?.column ?: n.toFloat() }.minOrNull() ?: 0f
        } ?: 0f
        val logicalRight = play.board.containers.filter { it.zone == PlayZone.BATTLEFIELD }.maxOfOrNull { c ->
            c.cards.mapIndexed { n, id -> (positions[id]?.column ?: n.toFloat()) + 1f }.maxOrNull() ?: 4f
        } ?: 4f
        val combat = CombatStackPresentation.combat(play.board, hudWidth * battlefieldScale, hudHeight * battlefieldScale)
        val railHeight = (if (landscape) 48f else 52f) + handHeight - hudHeight
        // Both modes reserve the very same timeline and controls; review never reframes the camera.
        val controlsHeight = tableBottomHeight(landscape)
        val dockRow = maxOf(64f, 68f + pileHeight - hudHeight)
        val counterHeight = 48f
        val counterHeights = players.map { if (landscape || it.counters.values.any(::nonzero)) counterHeight else 0f }
        val middleHeight = max(combat.height, stackHeight + 24f)
        val boardTop = HEADER + 4f + if (landscape) (handHeight - hudHeight).coerceAtLeast(0f) else 0f
        val boardBottom = height - controlsHeight - if (landscape) railHeight + 8f else 4f
        val ownDockY = height - controlsHeight - dockRow - counterHeights[1]
        val worldTop = if (landscape) boardTop else HEADER + dockRow + counterHeights[0] + railHeight
        val worldBottom = if (landscape) boardBottom else ownDockY - railHeight
        val worldViewport = TableBounds(sidebar + 6f, worldTop, (width - sidebar - 12f).coerceAtLeast(1f),
            (worldBottom - worldTop).coerceAtLeast(1f))
        val panelArea = worldViewport.copy(x = sidebar, width = width - sidebar)
        val panels = cardPanelGeometry(play, viewer, ui, panelArea, appearance.size(CardArea.PANELS))
        // Rows are world content, not fractions of the remaining screen. Small viewports pan.
        val rowHeight = battlefieldRowHeight(hudHeight * battlefieldScale, battlefieldScale)
        val fieldHeights = players.map { player ->
            val rows = container(player.accountId, PlayZone.BATTLEFIELD).cards.maxOfOrNull { positions[it]?.row ?: 0f } ?: 0f
            max(3f, rows + 1f) * rowHeight
        }
        val bandHeight = combatBandHeight ?: max(12f, combat.height + 4f)
        val bandY = worldViewport.height / 2 - bandHeight / 2
        val logicalFields = listOf(TableBounds(0f, bandY - fieldHeights[0], worldViewport.width, fieldHeights[0]),
            TableBounds(0f, bandY + bandHeight, worldViewport.width, fieldHeights[1]))
        val combatX = (worldViewport.width - combat.width) / 2
        val worldLeft = minOf(0f, logicalLeft * columnPitch, combatX)
        val worldRight = maxOf(worldViewport.width, logicalRight * columnPitch + 32f * battlefieldScale, combatX + combat.width)
        val contentBounds = TableBounds(worldLeft, logicalFields.first().y,
            worldRight - worldLeft,
            logicalFields.last().bottom - logicalFields.first().y)
        // Empty columns are navigable without making Fit table zoom out to include unused space.
        val world = TableWorld(worldViewport, contentBounds,
            contentBounds.copy(x = worldLeft - 3 * columnPitch, width = contentBounds.width + 6 * columnPitch))
        // Gestures own resistance/settling. Do not hard-clamp away their overscroll animation here.
        val camera = world.normalize(ui.camera)
        val zoom = camera.zoom
        val fieldScale = battlefieldScale * zoom
        val cw = hudWidth * fieldScale; val ch = hudHeight * fieldScale; val envelope = GRID * fieldScale
        val fields = players.mapIndexed { index, player ->
            val grid = world.screen(logicalFields[index].copy(width = world.bounds.right), camera)
            val bounds = grid.intersection(worldViewport) ?: grid.copy(width = 0f, height = 0f)
            TableField(bounds, player.accountId, container(player.accountId, PlayZone.BATTLEFIELD).id,
                gridBounds = grid, rowHeight = rowHeight * zoom, holdsAttention = player.accountId == play.board.effectiveAttentionHolder)
        }
        val sidebarBounds = TableBounds(0f, boardTop, sidebar, (height - controlsHeight - boardTop).coerceAtLeast(0f))
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
            val tapped = zone == PlayZone.BATTLEFIELD && !projection && card.tapped
            val label = "$title, owner ${name(card.owner)}, ${if (tapped) "tapped" else "untapped"}"
            return TableItem("card/$viewId", b, if (projection) "${if (frozen) "Retained effect, read-only" else "Projection"}, $label" else label,
                 if (hidden) "" else title, if (projection && zone == PlayZone.STACK) CombatStackRules.controller(play.board, viewId) else card.owner,
                 zone, kind = TableItemKind.CARD, viewport = viewport,
                cardId = viewId, covered = hidden, tapped = tapped, corner = if (hidden) "" else card.corner,
                footprint = footprint, sourceId = if (frozen) null else card.id, projection = projection, frozen = frozen,
                token = card.token, note = if (hidden) "" else if (projection)
                    CombatStackRules.projectionNote(play.board, viewId).orEmpty() else card.note,
                counters = if (hidden) emptyMap() else card.counters,
                   typeIcon = if (hidden) "" else cardTypeIcon(f?.typeLine.orEmpty()), artwork = art(card, hidden),
                    typeLine = if (hidden) "" else f?.typeLine.orEmpty(), faceId = card.faceId,
                    frontTypeLine = if (hidden) "" else definitions[card.definition]?.faces?.firstOrNull()?.typeLine.orEmpty()).also { if (draw) items += it }
        }
        for ((index, player) in players.withIndex()) {
            val ownSide = player.accountId == viewer
            val hand = container(player.accountId, PlayZone.HAND)
            val normalHandBounds = if (!ownSide) {
                if (landscape) TableBounds(sidebar + 10f, 0f, (width - sidebar - toolbarWidth - 10f).coerceAtLeast(handWidth), maxOf(HEADER, railHeight))
                else TableBounds(0f, worldTop - railHeight, width, railHeight)
            } else TableBounds(if (landscape) sidebar + 10f else 8f, (if (landscape) height - controlsHeight else ownDockY) - railHeight - if (landscape) 2f else 0f,
                 (width - if (landscape) sidebar + 20f else 16f).coerceAtLeast(28f), railHeight)
            val handBounds = normalHandBounds
            val handContent = if (hand.cards.isEmpty()) 0f else (hand.cards.size - 1) * (handWidth + 4f) + handWidth
            val hp = port(hand.id, handBounds.copy(width = (handBounds.width - 24f).coerceAtLeast(1f)), handContent, true)
            val start = hp.bounds.x + ((hp.bounds.width - handContent) / 2).coerceAtLeast(0f) - hp.scroll
            items += TableItem(hand.id, handBounds, "${name(player.accountId)} Hand, ${hand.cards.size} cards", hand.cards.size.toString(),
                player.accountId, PlayZone.HAND, hand.cards, TableItemKind.HAND, hp.id)
            hand.cards.forEachIndexed { n, id -> byId[id]?.let { c ->
                val b = TableBounds(start + n * (handWidth + 4f), handBounds.y + (handBounds.height - handHeight) / 2, handWidth, handHeight)
                card(c, b, b, player.accountId, PlayZone.HAND, hp.id)
            } }
            val field = fields[index]
            val battlefield = container(player.accountId, PlayZone.BATTLEFIELD)
            battlefield.cards.sortedWith(compareByDescending<String> { positions[it]?.pileIndex ?: 0 }.thenBy { battlefield.cards.indexOf(it) })
                .forEach { id -> byId[id]?.let { c ->
                    val p = positions[id] ?: TablePlacement(battlefield.cards.indexOf(id), 0)
                    val x = field.gridBounds.x + p.column * columnPitch * zoom + 16f * fieldScale
                    val rowHeight = field.rowHeight
                    val dy = p.row * rowHeight
                    val rowY = if (ownSide) field.gridBounds.bottom - rowHeight - dy else field.gridBounds.y + dy
                    val y = rowY + (rowHeight - envelope) / 2
                    val cardHeight = if (c.tapped) cw else ch
                    val footprint = TableBounds(x, y, columnPitch * zoom, envelope)
                    val b = battlefieldCardBounds(footprint, if (c.tapped) ch else cw, cardHeight, p.pileIndex, fieldScale)
                    card(c, b, footprint, player.accountId, PlayZone.BATTLEFIELD, battlefield.id)
                } }
            val logicalWidth = max(4f, battlefield.cards.mapIndexed { n, id -> (positions[id]?.column ?: n.toFloat()) + 1f }.maxOrNull() ?: 4f) * columnPitch
            port(battlefield.id, field.bounds, logicalWidth * zoom)
            val rowY = if (landscape) boardTop + (if (index == 0) 0f else sidebarContent - sidebarRow) - sidebarScroll
                else if (index == 0) HEADER else ownDockY
            val dock = TableBounds(0f, rowY, if (landscape) sidebar else width, dockRow)
            val spacing = max(pileEnvelope, (dock.width - 64f) / 3)
            val dp = port("dock/${player.accountId}", dock, 76f + 2 * spacing + pileEnvelope)
            items += TableItem("life/${player.accountId}", TableBounds(10f - dp.scroll, rowY + 8f, 48f, 48f),
                "${name(player.accountId)}, life ${player.life}", player.life, player.accountId, kind = TableItemKind.LIFE, viewport = dp.id,
                valueDragSide = if (ownSide) ValueDragSide.BOTTOM else ValueDragSide.TOP)
            for ((n, zone) in listOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE).withIndex()) {
                val c = container(player.accountId, zone)
                val top = byId[c.cards.firstOrNull()]
                val hidden = zone == PlayZone.LIBRARY && !c.keepTopRevealed || top == null || !CardVisibility.isVisibleFaceUp(play.board, top, viewer)
                val b = TableBounds(84f + n * spacing - dp.scroll, rowY + 12f, pileWidth, pileHeight)
                items += TableItem(c.id, b, "${name(player.accountId)} ${zone.name.lowercase()}, ${c.cards.size} cards${if (c.keepTopRevealed) ", keep top card revealed" else ""}", c.cards.size.toString(),
                    top?.owner ?: player.accountId, zone, c.cards, viewport = dp.id, covered = hidden,
                    footprint = b, peekOwners = listOfNotNull(top?.owner),
                    token = top?.token == true,
                    keepTopRevealed = c.keepTopRevealed,
                    drawHandId = hand.id.takeIf { zone == PlayZone.LIBRARY },
                    note = if (hidden) "" else top?.note.orEmpty(), faceTitle = if (hidden) "" else face(top)?.name.orEmpty(),
                    counters = if (hidden) emptyMap() else top?.counters.orEmpty(),
                    corner = if (hidden) "" else top?.corner.orEmpty(),
                    typeIcon = if (hidden) "" else cardTypeIcon(face(top)?.typeLine.orEmpty()), artwork = art(top, hidden))
            }
            if (counterHeights[index] > 0f) {
                val cp = port("counters/${player.accountId}", TableBounds(0f, rowY + dockRow, dock.width, counterHeight),
                    max(dock.width, 288f))
                for ((n, color) in CounterColor.entries.withIndex()) {
                    val v = player.counters[color].orEmpty()
                    val b = TableBounds(n * cp.contentWidth / 6 - cp.scroll, cp.bounds.y, cp.contentWidth / 6, counterHeight)
                    items += TableItem("counter/${player.accountId}/$color", b, "${name(player.accountId)}, ${color.name.lowercase()}, ${v.ifEmpty { "no value" }}",
                        v, player.accountId, kind = TableItemKind.COUNTER, viewport = cp.id, counter = color,
                        valueDragSide = if (ownSide) ValueDragSide.BOTTOM else ValueDragSide.TOP)
                }
            }
        }
        val stack = container(null, PlayZone.STACK)
        val stackGeometry = CombatStackPresentation.stack(play.board, stackWidth, stackHeight)
        // The same inset on every side; landscape centres the padded Stack in the sidebar lane.
        val stackPadding = 12f
        val stackHalfHeight = stackHeight / 2 + stackPadding
        val portraitStackY = if (worldViewport.height >= 2 * stackHalfHeight)
            (height / 2).coerceIn(worldViewport.y + stackHalfHeight, worldViewport.bottom - stackHalfHeight)
            else worldViewport.center.y
        val stackCenter = if (landscape) TablePoint((16f + sidebar) / 2,
            boardTop + sidebarContent / 2 - sidebarScroll) else TablePoint(width - stackWidth / 2 - stackPadding, portraitStackY)
        val stackRegion = TableBounds(stackCenter.x - stackWidth / 2 - stackPadding,
            stackCenter.y - stackHeight / 2 - stackPadding, stackWidth + 2 * stackPadding, stackHeight + 2 * stackPadding)
        val sb = TableBounds(stackRegion.center.x - stackWidth / 2, stackRegion.center.y - stackHeight / 2, stackWidth, stackHeight)
        val sp = port(stack.id, sb, stackWidth)
        items += TableItem(stack.id, sb, "Stack, ${stack.cards.size} entries", stack.cards.size.toString(), zone = PlayZone.STACK,
            cards = stack.cards, viewport = sp.id, footprint = sb, peekOwners = stackGeometry.visible.mapNotNull { CombatStackRules.controller(play.board, it.viewId) },
            countBadge = TableBounds(stackRegion.x - 8.5f, stackRegion.y - 8.5f, 20f, 20f))
        for (drawing in stackGeometry.visible.asReversed()) {
            val b = drawing.bounds.copy(x = sb.x + drawing.bounds.x, y = sb.y + drawing.bounds.y)
            card(drawing.card, b, sb, null, PlayZone.STACK, sp.id, drawing.viewId, drawing.projection, drawing.frozen)
            items[items.lastIndex] = items.last().copy(id = "stackDrawing/${drawing.viewId}", cards = stack.cards)
        }
        // Animation metadata for ALL entries, including a real spell buried below the peeks.
        val stackProjections = stack.cards.map { entryId ->
            val effect = play.board.stackEffects.firstOrNull { it.id == entryId }
            items.firstOrNull { it.cardId == entryId && it.zone == PlayZone.STACK } ?: card(CombatStackRules.inspection(play.board, entryId)!!,
                TableBounds(sb.x, sb.y, stackWidth, stackHeight), sb, null, PlayZone.STACK, sp.id, entryId,
                projection = effect != null, frozen = effect?.frozen != null, draw = false).copy(id = "stackDrawing/$entryId")
        }
        val combatY = worldViewport.height / 2 - combat.height / 2
        val combatBounds = world.screen(TableBounds(world.bounds.x, maxOf(combatY, bandY), world.bounds.width, minOf(combat.height, bandHeight)), camera)
        val cp = port("combat", combatBounds.intersection(worldViewport) ?: combatBounds.copy(height = 0f), combat.width * zoom)
        fun translated(b: TableBounds) = world.screen(b.copy(x = b.x + combatX, y = b.y + combatY), camera)
        for (drawing in combat.cards) card(drawing.card, translated(drawing.bounds), translated(drawing.bounds), null,
            PlayZone.BATTLEFIELD, cp.id, drawing.viewId, projection = true)
        val drops = combat.destinations.map { region ->
            val bounds = translated(region.bounds)
            val top = world.screen(TablePoint(0f, bandY), camera).y
            val half = bandHeight * zoom / 2
            val row = bounds.copy(y = top + if (region.destination.role == CombatRole.BLOCKER) 0f else half, height = half)
            // Screen-dp tolerance, capped just short of neighboring slot midpoints at small zoom.
            val neighbors = listOfNotNull(region.destination.pairGap.afterId, region.destination.pairGap.beforeId)
            val tolerance = minOf(12f, combat.destinations.filter { it.destination.pairId in neighbors &&
                it.destination.insertion == CombatInsertion.APPEND }.minOfOrNull { it.bounds.width * zoom / 2 - .01f } ?: 12f)
            region.copy(bounds = if (region.destination.pairId == null && !region.extendLeft && !region.extendRight)
                row.copy(x = row.center.x - tolerance, width = 2 * tolerance) else row,
                landing = world.screen(TablePoint(region.landing.x + combatX, region.landing.y + combatY), camera))
        }
        val dividers = combat.dividers.map { x -> world.screen(TablePoint(x + combatX, combatY), camera) to
            world.screen(TablePoint(x + combatX, combatY + combat.height), camera) }
        val clippedPorts = if (landscape) ports.map { p ->
            if (p.id.startsWith("dock/") || p.id.startsWith("counters/") || p.id == stack.id)
                p.copy(bounds = p.bounds.intersection(sidebarBounds) ?: p.bounds.copy(height = 0f)) else p
        } + TableViewport("sidebar", sidebarBounds, sidebar, 0f, contentHeight = sidebarContent, scrollY = sidebarScroll) else ports
        val realSources = play.board.cards.associate { c -> c.id to TableMoveSource(c.containerId, positions[c.id],
            play.board.containers.any { it.id == c.containerId && it.zone == PlayZone.LIBRARY && it.cards.firstOrNull() == c.id }) }
        val sources = realSources + items.filter { it.projection && it.sourceId != null }.associate { item ->
            item.cardId!! to realSources.getValue(item.sourceId!!).copy(sourceId = item.sourceId) }
        return clip(cardPanels(TableScene(items, fields, clippedPorts, cw, ch, envelope, camera, sources, drops, dividers,
            stackRegion = if (landscape) stackRegion.intersection(sidebarBounds) else stackRegion, world = world, appearance = appearance,
            stackProjections = stackProjections, cardOwners = play.board.cards.associate { it.id to it.owner } +
                items.filter { it.cardId != null && it.owner != null }.associate { it.cardId!! to it.owner!! }), play, viewer, panels), width, height)
    }

    /** Used after animation too: rendered bounds own drawing, targeting and accessibility. */
    fun clip(scene: TableScene, width: Float, height: Float): TableScene {
        val screen = TableBounds(0f, 0f, width, height)
        val ordered = scene.items.filter { it.zone == PlayZone.BATTLEFIELD && !it.viewport.startsWith("cardPanel/") } +
            scene.items.filter { it.zone != PlayZone.BATTLEFIELD && !it.viewport.startsWith("cardPanel/") } + scene.items.filter { it.viewport.startsWith("cardPanel/") }
        val viewports = scene.viewports.associateBy { it.id }
        val clipped = ordered.map { i ->
            val viewport = viewports[i.viewport]
            val regions = listOfNotNull((if (i.inTransit) {
                if (i.zone == PlayZone.BATTLEFIELD) scene.world?.visibleViewport ?: screen else screen
            } else viewport?.bounds)?.let(i.bounds::intersection)?.intersection(screen))
            val panelClipped = if (i.viewport.startsWith("cardPanel/") || i.inTransit) regions else
                scene.cardPanels.fold(regions) { parts, panel -> parts.flatMap { it.subtract(panel.bounds) } }
            i.copy(visible = if (i.zone == PlayZone.BATTLEFIELD && scene.stackRegion != null)
                panelClipped.flatMap { it.subtract(scene.stackRegion) } else panelClipped)
        }
        val visible = clipped.mapIndexed { n, i ->
            var portions = i.visible
            if (i.kind != TableItemKind.HAND) for (next in n + 1 until clipped.size) {
                val cover = clipped[next]
                if (cover.kind == TableItemKind.HAND ||
                    i.zone == PlayZone.STACK && i.kind == TableItemKind.PILE && cover.viewport == i.viewport ||
                    i.bounds.intersection(cover.bounds) == null) continue
                for (part in cover.visible) if (portions.any { it.intersection(part) != null })
                    portions = portions.flatMap { it.subtract(part) }
                if (portions.isEmpty()) break
            }
            i.copy(visible = portions)
        }
        return scene.copy(items = visible)
    }
    fun nonzero(value: String): Boolean = value.trim().let { s -> s.isNotEmpty() &&
        (!Regex("[+-]?[0-9]+").matches(s) || s.removePrefix("+").removePrefix("-").any { it != '0' }) }
    internal fun cardTypeIcon(type: String) = when {
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
