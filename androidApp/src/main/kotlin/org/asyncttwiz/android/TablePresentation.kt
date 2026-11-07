package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ArtworkReference


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
    /** TBL-121: the opening view in landscape: zoomed out (to at most 1x, at least the smallest zoom) until the
     * battlefields' rows fit the height, centred on them, with column 0 still at the left edge as at 1x. */
    fun landscapeHome(): TableCamera {
        val zoom = (viewport.height / bounds.height).coerceIn(TablePresentation.MIN_ZOOM, 1f)
        return TableCamera(home.x / zoom - home.x, bounds.center.y - home.y, zoom)
    }
    fun fit(): TableCamera = clamp(TableCamera(bounds.center.x - home.x, bounds.center.y - home.y,
        minOf(viewport.width / bounds.width, viewport.height / bounds.height).coerceIn(TablePresentation.MIN_ZOOM, TablePresentation.MAX_ZOOM)))
}
data class TablePlacement(val column: Float, val row: Float, val pileIndex: Int = 0, val pileId: String? = null) {
    constructor(column: Int, row: Int, pileIndex: Int = 0, pileId: String? = null) : this(column.toFloat(), row.toFloat(), pileIndex, pileId)
}

/** A battlefield card centred in its [footprint] (tapped cards rotate in place), [dy] lower (an attached card peeking out
 * above its host is higher, TBL-053). */
internal fun battlefieldCardBounds(footprint: TableBounds, width: Float, height: Float, dy: Float = 0f): TableBounds =
    TableBounds(footprint.center.x - width / 2, footprint.center.y - height / 2 + dy, width, height)

/** TBL-003: ten percent above/below every face, plus room for the attached cards peeking out above their hosts ([lifts]
 * peek steps, at least one). */
internal fun battlefieldRowHeight(cardHeight: Float, scale: Float, lifts: Int = 1) =
    cardHeight * 1.2f + maxOf(1, lifts) * BattlefieldLayout.PEEK * scale
/** [localPileTargets]: the local pile is open to pick targets from (Automatum, I-D6): its cards one by one, not grouped. */
data class TableLayoutState(val camera: TableCamera = TableCamera(), val scroll: Map<String, Float> = emptyMap(),
    val scrollY: Map<String, Float> = emptyMap(), val localPileId: String? = null, val localPileTargets: Boolean = false,
    /** ZON-079 (I-D50): the viewer's hand card raised by a tap with Automatum on; the viewer's own. */
    val raised: String? = null,
    /** ZON-081 (I-D50): a hand pulled past its end (dp, by hand viewport id), springing back on release. */
    val overscroll: Map<String, Float> = emptyMap(),
    /** TBL-122: the battlefield group the viewer opened with a tap ([BattlefieldGroup.key]), its members side by side;
     * the viewer's own. */
    val expandedGroup: String? = null)
/** [DUNGEON]: a player's dungeon in the command zone (TRN-069), a small card after their piles (landscape: in the free row). */
enum class TableItemKind { CARD, PILE, LIFE, HAND, DUNGEON }
data class TableItem(
    val id: String, val bounds: TableBounds, val label: String, val value: String,
    val owner: String? = null, val zone: PlayZone? = null, val cards: List<String> = emptyList(),
    val kind: TableItemKind = TableItemKind.PILE, val viewport: String = "", val cardId: String? = null,
    val covered: Boolean = false, val tapped: Boolean = false, val corner: String = "",
    val visible: List<TableBounds> = listOf(bounds), val footprint: TableBounds = bounds,
    val peekOwners: List<String> = emptyList(),
    val sourceId: String? = cardId, val projection: Boolean = false, val frozen: Boolean = false,
    val token: Boolean = false,
    val typeIcon: String = "", val artwork: ArtworkReference? = null,
    val inTransit: Boolean = false, val cardTransform: TableCardTransform? = null,
    val flipScale: Float = 1f, val copies: Int = 1, val faceTitle: String = "",
    val countBadge: TableBounds? = null, val typeLine: String = "", val keepTopRevealed: Boolean = false,
    val faceId: String? = null, val changedPowerToughness: Boolean = false, val frontTypeLine: String = typeLine,
    val damage: String = "", val eotCorner: String = "",
    val touchBounds: TableBounds? = null,
    /** A player's Temp pile: a LIBRARY-zone pile that is never that player's Library. */
    val temp: Boolean = false,
    /** The Temp pile's number 1..4 (0 for every other item); an empty pile may not exist yet (placeholder). */
    val tempNumber: Int = 0,
    /** A face-up Battlefield card (or its combat copy) that entered the Battlefield this turn (rules v29). */
    val enteredThisTurn: Boolean = false,
    /** A public face-down Battlefield card's title stand-in ("?"): the same for every viewer, never its front. */
    val coveredTitle: String = "",
    /** LIFE only: the texts drawn as chips beside the square: the room of the player's dungeon (TRN-069). */
    val emblems: List<String> = emptyList(),
    /** LIFE only (TRN-053): the free room right of the player's piles the text chips stack in; null: by the square. */
    val emblemArea: TableBounds? = null,
    /** LIFE only (TRN-053, I-D25): the player's emblems' rules texts, oldest first. */
    val emblemTexts: List<String> = emptyList(),
    /** LIFE only (TRN-053): where the emblems' round chips are ([EmblemChips.round]); the last a "+N" chip when there are
     * more than [EmblemChips.SHOWN] emblems. */
    val emblemChips: List<TableBounds> = emptyList(),
    /** Automatum on (I1): the colour of a computed P/T (up, down, mixed); null for the manual corner's colours. */
    val cornerColor: Int? = null,
    /** Automatum on (I1): an until-end-of-turn effect changes the computed P/T (⌛ as TBL-023). */
    val cornerHourglass: Boolean = false,
    /** Automatum on (I1): changed types and colours, keywords gained or lost and typed counters, as one chip. */
    val automatumMarks: List<String> = emptyList(),
    /** ZON-079: a raised hand card (drawn above its hand row, not clipped by it at the top). */
    val raised: Boolean = false,
    /** STK-046: a Stack entry that is a triggered or activated ability, drawn as an ability token, not as its source card. */
    val stackAbility: StackAbilityShown? = null,
    /** TBL-053: a battlefield card's group ([BattlefieldGroup.key]); null off the battlefield. */
    val group: String? = null
) {
    /** The card title's colour on the table: blue while it entered this turn, else the default text colour. */
    val titleColor: Int get() = if (enteredThisTurn) EnteredThisTurnTitleColor else android.graphics.Color.DKGRAY
}

/** Lazily built, memoized drawings of public pile members, asked for only by cards that changed pile. Derived
 * from the scene's own board, so it takes no part in scene equality. [built] counts drawings made (tests). */
class PileCardDrawings(private val build: (String) -> TableItem?) {
    private val made = HashMap<String, TableItem?>()
    internal var built = 0; private set
    operator fun get(cardId: String): TableItem? = if (cardId in made) made[cardId]
        else build(cardId).also { made[cardId] = it; if (it != null) built++ }
    override fun equals(other: Any?) = other is PileCardDrawings
    override fun hashCode() = 0
    companion object { val NONE get() = PileCardDrawings { null } }
}

/** Shown in the title position of a face-down Battlefield card; it says nothing about the hidden front. */
internal const val FACE_DOWN_TITLE = "?"

/** Clearly blue, distinct from the own (teal) / opponent (red) table colours and the until-end-of-turn teal. */
internal const val EnteredThisTurnTitleColor = 0xff1a56db.toInt()
/** [gridBounds] spans the whole pannable width (the battlefield feels infinite); [originX] is the screen x of column 0. */
data class TableField(val bounds: TableBounds, val owner: String, val containerId: String = "",
    val gridBounds: TableBounds = bounds, val rowHeight: Float = TablePresentation.GRID,
    val holdsAttention: Boolean = false, val originX: Float = gridBounds.x) {
    @Suppress("UNUSED_PARAMETER")
    fun landsBounds(atBottom: Boolean, camera: TableCamera = TableCamera()) = gridBounds.copy(
        y = if (atBottom) gridBounds.bottom - rowHeight else gridBounds.y, height = rowHeight)
}
/** [bar]: a player's row in a bar (landscape, TBL-009), drawn on the bar's own colour rather than a white surface. */
data class TableViewport(val id: String, val bounds: TableBounds, val contentWidth: Float, val scroll: Float,
    val hand: Boolean = false, val contentHeight: Float = bounds.height, val scrollY: Float = 0f, val bar: Boolean = false) {
    val maxScroll get() = (contentWidth - bounds.width).coerceAtLeast(0f)
    val maxScrollY get() = (contentHeight - bounds.height).coerceAtLeast(0f)
}
internal const val WORLD_PAN_REGION = "world/camera"
data class TableScene(val items: List<TableItem>, val fields: List<TableField>,
    val viewports: List<TableViewport> = emptyList(), val cardWidth: Float = 28f, val cardHeight: Float = 40f,
    val footprint: Float = 56f, val camera: TableCamera = TableCamera(),
    val sources: Map<String, TableMoveSource> = emptyMap(), val combatDrops: List<CombatDropRegion> = emptyList(),
    val combatDividers: List<Pair<TablePoint, TablePoint>> = emptyList(),
    val stackRegion: TableBounds? = null,
    val world: TableWorld? = null, val stackProjections: List<TableItem> = emptyList(),
    val cardOwners: Map<String, String> = emptyMap(),
    internal val cardPanels: List<CardPanelGeometry> = emptyList(),
    val appearance: CardAppearance = CardAppearance(),
    val combatLabels: List<CombatLaneLabel> = emptyList(),
    val combatLaneDividers: List<Pair<TablePoint, TablePoint>> = emptyList(),
    /** CMB-007: the open combat band's lanes on the screen (not clipped to the table), in the middle of the band between
     * the battlefields; null while the band is collapsed. */
    val combatPanel: TableBounds? = null,
    /** CMB-001, CMB-007 (owner playtest 2026-11-06): the band between the battlefields on the screen, clipped to the
     * table: always there, collapsed to its chip row or open with the lanes; null when it is out of sight. */
    val combatBand: TableBounds? = null,
    /** The size of the combat band's cards on the screen: the battlefield's at the same zoom (CMB-042). */
    val combatCardWidth: Float = cardWidth, val combatCardHeight: Float = cardHeight,
    /** The undrawn face-up (or covered, when face down) drawing of a Graveyard/Exile member: the look of a
     * card flying from or to a public pile. Library and Temp pile members fly face down. */
    val pileCards: PileCardDrawings = PileCardDrawings.NONE,
    /** TBL-053: the groups on both battlefields (stacks, cards with attachments, cards alone), in arrival order. */
    val battlefieldGroups: List<BattlefieldGroup> = emptyList(),
    /** TBL-122: the group the viewer opened ([TableLayoutState.expandedGroup]), while it is on the table. */
    val expandedGroup: String? = null) {
    /** TBL-053: the group of the battlefield card [cardId], null for a card in no group. */
    fun groupOf(cardId: String?): BattlefieldGroup? = cardId?.let { id -> battlefieldGroups.firstOrNull { id in it.members } }
    fun worldInput(point: TablePoint) = world?.visibleViewport?.contains(point) == true &&
        stackRegion?.contains(point) != true && items.none { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE && it.stackBadge.contains(point) } &&
        cardPanels.none { it.bounds.contains(point) }
    fun hit(point: TablePoint): TableItem? {
        items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE && it.stackBadge.contains(point) }?.let { return it }
        val stack = items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE &&
            (it.visible.any { bounds -> bounds.contains(point) } || it.touchBounds?.contains(point) == true) }
        if (stack != null) {
            if (stack.stackBadge.contains(point)) return stack
            // Visible lower peeks retain their identity for hold-to-inspect, not top-card shortcuts.
            return items.lastOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.CARD &&
                it.viewport == stack.viewport && it.visible.any { bounds -> bounds.contains(point) } } ?: stack
        }
        return items.asReversed().firstOrNull { item -> item.visible.any { it.contains(point) } || item.touchBounds?.contains(point) == true }
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
/** The smaller special-pile bubble straddles the card's top-left corner in either layout. */
internal val TableItem.pileCountBubble get() = TableBounds(bounds.x - 8f, bounds.y - 8f, 16f, 16f)
internal val TableScene.columnPitch get() = TablePresentation.COLUMN_PITCH * cardWidth / 28f
internal val TableItem.visibleAnchor get() = if (visible.any { it.contains(bounds.center) }) bounds.center
    else visible.maxByOrNull { it.width * it.height }?.center

object TablePresentation {
    const val GRID = 56f
    /** ZON-079: how far a raised hand card rises, in its own heights. */
    const val RAISE = .25f
    const val COLUMN_PITCH = 42f
    /** The open band's margin above and below its lanes (dp at zoom 1). */
    private const val COMBAT_BAND_PAD = 4f
    /** CMB-001: the id of the empty combat area the collapsed band's chips belong to while the viewer declares
     * attackers with none staged (drawn only, never shared; a creature dropped on a chip's lane is staged there). */
    const val OPENED_COMBAT_AREA = "opened"
    const val MIN_ZOOM = .65f
    const val MAX_ZOOM = 2f
    const val HEADER = 48f
    // Reserve the maximum chrome width so the opponent's fixed Hand does not move in review: three 48 dp buttons (step,
    // Return to current or Table actions, chat) with their gaps and the bar's end padding, and a gap before them.
    const val TOOLBAR = 168f
    private const val PILE_START = 76f
    /** The counters' room may be squeezed this far before it scrolls. */
    private const val MIN_SHRUNK_COUNTER_GRID = 60f
    internal fun portraitDockWidth(pileWidth: Float, piles: Int) = PILE_START + (piles - 1) * (pileWidth + 12f) + pileWidth + 8f

    /** TBL-009 (owner, 2026-11-07): in landscape each player's life square, piles and emblems sit in a bar row (the
     * opponent's in the top bar, yours in the bottom bar), so the battlefield takes the whole width. */
    internal object Landscape {
        /** The turn header's room in the top bar (TableChromeHeader), before the opponent's row; narrower on narrow
         * screens. */
        fun title(width: Float) = (width - 764f).coerceIn(80f, 128f)
        /** A life square's side, as in portrait: the top bar's whole height. */
        const val LIFE = 48f
        /** The piles' most height in a bar row (their counts above them still inside the bar). */
        const val PILE_HEIGHT = 32f
        /** Each pile's own 48 dp touch target, side by side, the first [PILE_GAP] after the life square. */
        const val PILE_PITCH = 48f
        const val PILE_GAP = 12f
        /** The emblem chips' most room after the piles: three chips and a "+N" (TRN-053). */
        val EMBLEMS = EmblemChips.rowWidth(EmblemChips.SHOWN + 1)
        /** Your row's left end in the bottom bar. */
        const val OWN_X = 10f
        /** The opponent's row's left end in the top bar: after the navigation button and the turn header. */
        fun opponentX(width: Float) = 48f + 4f + title(width) + 4f
        /** The narrowest the opponent's hand gets in the top bar (a card and its count) before their emblems' room gives
         * way, down to [MIN_EMBLEMS]. */
        const val MIN_OPPONENT_HAND = 64f
        /** The emblems' least room: a chip and a "+N" at their smallest. */
        val MIN_EMBLEMS = EmblemChips.rowWidth(2, EmblemChips.MIN_SIZE)
        /** The x of the room after [places] piles (from the row's [x]). */
        fun pilesRight(x: Float, places: Int) = x + LIFE + PILE_GAP + places * PILE_PITCH
        /** The opponent's emblems' room after [places] piles, fitted to the top bar's free width. */
        fun emblemRoom(width: Float, places: Int) = (width - TOOLBAR - 10f - MIN_OPPONENT_HAND - 10f -
            pilesRight(opponentX(width), places) - 4f).coerceIn(MIN_EMBLEMS, EMBLEMS)
        /** The right end of a player's row: life square, [places] piles (the dungeon's the fourth, TRN-069) and the
         * emblems' room. Yours ([own], in the bottom bar left of the controls, which take its right half) always keeps
         * room for the dungeon, so its end (where the timeline panel's strip starts) never moves. */
        fun rowRight(width: Float, own: Boolean, places: Int = 3) =
            if (own) minOf(pilesRight(OWN_X, 4) + 4f + EMBLEMS, width / 2 - 4f)
            else pilesRight(opponentX(width), places) + 4f + emblemRoom(width, places)
    }

    fun scene(game: GameSummary, play: TableBoard, viewer: String, width: Float, height: Float, landscape: Boolean,
        ui: TableLayoutState = TableLayoutState(), placements: Map<String, TablePlacement> = emptyMap(),
        toolbarWidth: Float = TOOLBAR, reviewing: Boolean = false,
        appearance: CardAppearance = CardAppearance(),
        logicalPositions: Map<String, TablePlacement> = logicalPlacements(play),
        computed: org.asyncttwiz.automatum.ComputedBoard = org.asyncttwiz.automatum.ComputedBoard.NONE,
        landscapeHome: Boolean = false,
        /** Timeline mode: its panel is taller than play mode's bottom bar in portrait ([tableBottomHeight]). */
        timeline: Boolean = false): TableScene {
        require(play.gameId == game.id && play.playId == game.playId)
        require(width > 0 && height > 0 && width.isFinite() && height.isFinite())
        val positions = logicalPositions + placements
        val own = play.board.players.single { it.accountId == viewer }
        val opponent = play.board.players.single { it.accountId != viewer }
        val players = listOf(opponent, own)
        val byId = play.board.cards.associateBy { it.id }
        val definitions = play.definitions.associate { it.digest to it.definition }
        val castFromHand = castSpellSources(play.board)
        fun name(id: String?) = if (game.inviter.accountId == id) game.inviter.username else game.recipient.username
        fun container(player: String?, zone: PlayZone) = play.board.containers.single { it.accountId == player && it.zone == zone && !it.temp }
        fun face(card: PlayCard?) = card?.let { definitions[it.definition]?.faces?.firstOrNull { f -> f.faceId == it.faceId } }
        fun art(card: PlayCard?, hidden: Boolean): ArtworkReference? {
            val f = face(card) ?: return null
            if (hidden || (f.scryfallId == null && f.imageUri == null)) return null
            return f.tableArtwork(definitions[card!!.definition]!!.faces.indexOf(f), card.definition)?.forTable()
        }
        val hudWidth = 28f; val hudHeight = 40f; val hudEnvelope = GRID
        val battlefieldScale = cardScale(appearance.cardSize)
        val columnPitch = COLUMN_PITCH * battlefieldScale
        val handWidth = hudWidth * appearance.size(CardArea.HAND)
        val handHeight = hudHeight * appearance.size(CardArea.HAND)
        // TBL-009: in landscape the piles fit their bar row
        val pileScale = appearance.size(CardArea.PILES).let { if (landscape) minOf(it, Landscape.PILE_HEIGHT / hudHeight) else it }
        val pileWidth = hudWidth * pileScale
        val pileHeight = hudHeight * pileScale
        // CMB-001, CMB-007 (owner playtest 2026-11-06): the combat band between the battlefields is always there, collapsed
        // to its chip row; while the viewer declares attackers the chips of every target show in it; it opens by itself
        // once an attacker is in the area (in playback too: only when someone attacked)
        val open = play.board.combat?.pairs?.isNotEmpty() == true
        val declaring = !open && !reviewing && combatChipStep(play.board, viewer)
        val bandBoard = when {
            open -> play.board
            declaring -> play.board.copy(combat = play.board.combat ?: CombatArea(OPENED_COMBAT_AREA))
            else -> play.board.copy(combat = null)
        }
        val railHeight = (if (landscape) 48f else 52f) + handHeight - hudHeight
        // HIS-001, HIS-007: play mode's bottom bar has only the controls; timeline mode's panel adds its strip in portrait
        val controlsHeight = tableBottomHeight(landscape, timeline)
        val dockRow = maxOf(64f, 68f + pileHeight - hudHeight)
        val dockSpacing = if (landscape) Landscape.PILE_PITCH else pileWidth + 12f
        val gridWidth = minOf(132f, width * .34f)
        val boardTop = HEADER + 4f + if (landscape) (handHeight - hudHeight).coerceAtLeast(0f) else 0f
        val boardBottom = height - controlsHeight - if (landscape) railHeight + 8f else 4f
        val ownDockY = height - controlsHeight - dockRow
        val worldTop = if (landscape) boardTop else HEADER + dockRow + railHeight
        val worldBottom = if (landscape) boardBottom else ownDockY - railHeight
        // TBL-009: the battlefields take the table's whole width in both orientations
        val worldViewport = TableBounds(6f, worldTop, (width - 12f).coerceAtLeast(1f),
            (worldBottom - worldTop).coerceAtLeast(1f))
        // TBL-009: in landscape the Stack (over the battlefields' right end, STK-007) fits between the hands
        val stackScale = appearance.size(CardArea.STACK).let { size -> if (!landscape) size
            else minOf(size, ((worldViewport.height - 24f) / hudHeight).coerceAtLeast(.5f)) }
        val stackWidth = hudWidth * stackScale
        val stackHeight = hudHeight * stackScale
        val panelArea = worldViewport.copy(x = 0f, width = width)
        val panels = cardPanelGeometry(play, viewer, ui, panelArea, appearance.size(CardArea.PANELS))
        // TBL-004, TBL-053 (Arena style): each battlefield lays itself out from the game state: its groups (stacks, cards with
        // what is attached to them) in its back and creature rows; the group the viewer opened lies with its members side by side
        val groups = BattlefieldLayout.groups(BattlefieldLayout.cards(play.board, definitions, computed))
        val expanded = ui.expandedGroup?.takeIf { key -> groups.any { it.key == key && it.members.size > 1 } }
        // The back row's right end at 1×: the battlefield's own width on the screen (others lie from there leftwards)
        val rowWidth = (worldViewport.width / battlefieldScale - 32f).coerceAtLeast(BattlefieldLayout.SLOT)
        val spots = players.associate { player -> container(player.accountId, PlayZone.BATTLEFIELD).id.let { id ->
            id to BattlefieldLayout.place(groups.filter { it.field == id }, rowWidth, expanded) } }
        val logicalRight = spots.values.flatMap { it.values }.maxOfOrNull { it.x + BattlefieldLayout.SLOT } ?: (4 * BattlefieldLayout.SLOT)
        // Rows are world content, not fractions of the remaining screen. Small viewports pan. TBL-003: two rows each, the back
        // row by the player's own edge, the creature row by the middle (the combat band pushes the battlefields apart, CMB-007)
        val rowHeight = battlefieldRowHeight(hudHeight * battlefieldScale, battlefieldScale, BattlefieldLayout.lifts(groups))
        val fieldHeights = players.map { 2 * rowHeight }
        // CMB-042 (owner playtest 2026-11-06): the band is world content, zooming with the battlefields: its cards are
        // exactly the battlefield's
        val combatCard = hudWidth * battlefieldScale
        val combat = CombatStackPresentation.combat(bandBoard, combatCard,
            combatCard * hudHeight / hudWidth, play.definitions, mapOf(game.inviter.accountId to game.inviter.username, game.recipient.accountId to game.recipient.username), viewer,
            avatars = listOf(game.inviter, game.recipient)
                .mapNotNull { player -> player.avatar?.let { player.accountId to it } }.toMap(), open = open)
        // CMB-007: the open band pushes the battlefields apart to hold its lanes; collapsed it keeps the chip row's height
        val bandHeight = if (open) combat.height + 2 * COMBAT_BAND_PAD else CombatStackPresentation.CHIP_ROW
        val bandY = worldViewport.height / 2 - bandHeight / 2
        val logicalFields = listOf(TableBounds(0f, bandY - fieldHeights[0], worldViewport.width, fieldHeights[0]),
            TableBounds(0f, bandY + bandHeight, worldViewport.width, fieldHeights[1]))
        // TBL-121: an untouched camera in landscape opens on the battlefields' whole height
        val opening = landscape && landscapeHome && ui.camera == TableCamera()
        val combatX = (worldViewport.width - combat.width) / 2
        val worldLeft = minOf(0f, combatX)
        val worldRight = maxOf(worldViewport.width, (logicalRight + 32f) * battlefieldScale, combatX + combat.width)
        val contentBounds = TableBounds(worldLeft, logicalFields.first().y,
            worldRight - worldLeft,
            logicalFields.last().bottom - logicalFields.first().y)
        // Empty columns are navigable without making Fit table zoom out to include unused space.
        val world = TableWorld(worldViewport, contentBounds,
            contentBounds.copy(x = worldLeft - 3 * columnPitch, width = contentBounds.width + 6 * columnPitch))
        // Gestures own resistance/settling. Do not hard-clamp away their overscroll animation here.
        // TBL-121: [landscapeHome] (the table screen): an untouched camera in landscape opens on the whole height
        val camera = if (opening) world.landscapeHome() else world.normalize(ui.camera)
        val zoom = camera.zoom
        val fieldScale = battlefieldScale * zoom
        val cw = hudWidth * fieldScale; val ch = hudHeight * fieldScale; val envelope = GRID * fieldScale
        // The battlefield feels infinite; panning is limited only by distance from placed cards. Each field
        // spans the whole pan area (and the viewport, covering overscroll and zoomed-out views) on both sides,
        // so no field edge, drop gap or tint edge is ever visible. Column 0 stays the world origin.
        val pan = world.screen(world.panBounds, camera)
        val fieldLeft = minOf(pan.x, worldViewport.x)
        val fieldRight = maxOf(pan.right, worldViewport.right)
        val fields = players.mapIndexed { index, player ->
            val logical = world.screen(logicalFields[index], camera)
            val grid = TableBounds(fieldLeft, logical.y, fieldRight - fieldLeft, logical.height)
            val bounds = grid.intersection(worldViewport) ?: grid.copy(width = 0f, height = 0f)
            TableField(bounds, player.accountId, container(player.accountId, PlayZone.BATTLEFIELD).id,
                gridBounds = grid, rowHeight = rowHeight * zoom, holdsAttention = player.accountId == play.board.moveHolder,
                originX = logical.x)
        }
        val items = mutableListOf<TableItem>()
        val ports = mutableListOf<TableViewport>()
        fun port(id: String, bounds: TableBounds, content: Float, hand: Boolean = false, bar: Boolean = false): TableViewport {
            val offset = (ui.scroll[id] ?: 0f).coerceIn(0f, (content - bounds.width).coerceAtLeast(0f))
            return TableViewport(id, bounds, content, offset, hand, bar = bar).also(ports::add)
        }
        fun card(card: PlayCard, b: TableBounds, footprint: TableBounds, region: String?, zone: PlayZone, viewport: String,
            viewId: String = card.id, projection: Boolean = false, frozen: Boolean = false, draw: Boolean = true): TableItem {
            // A face-down entry (STK-005) is a card back for both players, like a face-down card (ZON-073).
            val hidden = projection && CombatStackRules.faceDownEntry(play.board, viewId) ||
                if (!frozen) !CardVisibility.isVisibleFaceUp(play.board, card, viewer)
                else !CardVisibility.isVisibleFaceUp(card, zone, region, viewer)
            val f = face(card)
            // A face-down Battlefield card hides its face, but its values are public (rules v23).
            val faceDown = hidden && publicFaceDown(play.board, card)
            val values = !hidden || faceDown
            val title = if (hidden) "Covered card" else f?.name ?: "Card (definition unavailable)"
            val tapped = zone == PlayZone.BATTLEFIELD && !projection && card.tapped
            // Combat copies follow their live source card; a face-down card's "?" title carries the mark too.
            val coveredTitle = if (zone == PlayZone.BATTLEFIELD && faceDown && !frozen) FACE_DOWN_TITLE else ""
            val entered = zone == PlayZone.BATTLEFIELD && (!hidden || coveredTitle.isNotEmpty()) && !frozen &&
                EnteredTurn.marked(play.board, card)
            // Automatum on (I1): a Battlefield card (and its combat copies) shows the engine's P/T and marks.
            val auto = if (values && zone == PlayZone.BATTLEFIELD && !frozen) computed.cards[card.id] else null
            val autoCorner = auto?.corner
            // STK-046: an ability on the Stack names itself as an ability, with its text
            val ability = if (zone == PlayZone.STACK && projection && !hidden) StackAbilities.of(play, viewId, viewer) else null
            val label = if (ability != null) "${ability.kind}, ${ability.line}, controller ${name(CombatStackRules.controller(play.board, viewId) ?: card.owner)}"
                else "$title, owner ${name(card.owner)}, ${if (tapped) "tapped" else "untapped"}" +
                if (entered) ", entered this turn" else ""
            return TableItem("card/$viewId", b, if (projection) "${if (frozen) "Retained effect, read-only" else "Projection"}, $label" else label,
                 if (hidden) "" else title, if (projection && zone == PlayZone.STACK) CombatStackRules.controller(play.board, viewId) else card.owner,
                 zone, kind = TableItemKind.CARD, viewport = viewport,
                cardId = viewId, covered = hidden, tapped = tapped, corner = if (!values) "" else autoCorner ?: effectiveCorner(card),
                damage = if (!values) "" else card.damage, eotCorner = if (!values || autoCorner != null) "" else card.eotCorner,
                footprint = footprint, sourceId = if (frozen) null else card.id, projection = projection, frozen = frozen,
                token = card.token,
                   typeIcon = if (hidden) "" else cardTypeIcon(f?.typeLine.orEmpty()), artwork = art(card, hidden),
                    typeLine = if (hidden) "" else f?.typeLine.orEmpty(), faceId = card.faceId,
                    changedPowerToughness = if (autoCorner != null) false
                        else if (faceDown) changedFaceDownPowerToughness(card.corner) else !hidden && changedPowerToughness(card.corner, f),
                    cornerColor = if (autoCorner != null) computedCornerColor(auto.trend) else null,
                    cornerHourglass = autoCorner != null && auto.untilEndOfTurn, automatumMarks = auto?.marks.orEmpty(),
                    frontTypeLine = if (hidden) "" else definitions[card.definition]?.faces?.firstOrNull()?.typeLine.orEmpty(),
                    enteredThisTurn = entered, coveredTitle = coveredTitle, stackAbility = ability).also { if (draw) items += it }
        }
        // Opponent block on top, own block at the bottom; in landscape (TBL-009) the opponent's in the top bar, yours in the
        // bottom bar, left of the controls
        val dockRowY = if (landscape) listOf(0f, height - controlsHeight) else listOf(HEADER, ownDockY)
        val dockRowHeights = if (landscape) listOf(HEADER, controlsHeight) else listOf(dockRow, dockRow)
        for ((index, player) in players.withIndex()) {
            val ownSide = player.accountId == viewer
            // TRN-069: the player's dungeon in the command zone takes a fourth place after the piles
            val dungeon = play.engine?.let { org.asyncttwiz.automatum.EngineProjection.dungeon(it.state, player.accountId) }
            val places = if (dungeon != null) 4 else 3
            val hand = container(player.accountId, PlayZone.HAND).let { hand ->
                // UI review round 2 #9 (decided for the owner): with Automatum on a spell cast from a hand leaves it at once:
                // its card isn't drawn in the hand while its entry is on the Stack (the card stays there, STK-001)
                hand.copy(cards = hand.cards.filter { it !in castFromHand })
            }
            val normalHandBounds = if (!ownSide) {
                // TBL-009: in landscape in the top bar, after the opponent's life square, piles and emblems
                if (landscape) Landscape.rowRight(width, own = false, places).let { left -> TableBounds(left + 10f, 2f,
                    (width - toolbarWidth - 10f - left - 10f).coerceAtLeast(handWidth), maxOf(HEADER, railHeight)) }
                else TableBounds(8f, worldTop - railHeight, width - 16f, railHeight)
            } else TableBounds(if (landscape) 10f else 8f, (if (landscape) height - controlsHeight else ownDockY) - railHeight - if (landscape) 2f else 0f,
                 (width - (if (landscape) 20f else 16f)).coerceAtLeast(28f), railHeight)
            val handBounds = normalHandBounds
            val handContent = if (hand.cards.isEmpty()) 0f else (hand.cards.size - 1) * (handWidth + 4f) + handWidth
            val hp = port(hand.id, handBounds.copy(width = (handBounds.width - 24f).coerceAtLeast(1f)), handContent, true)
            // ZON-081: a hand pulled past its end (Automatum on) is drawn that far off, springing back on release
            val pulled = ui.overscroll[hp.id] ?: 0f
            val start = hp.bounds.x + ((hp.bounds.width - handContent) / 2).coerceAtLeast(0f) - hp.scroll - pulled
            items += TableItem(hand.id, handBounds, "${name(player.accountId)} Hand, ${hand.cards.size} cards", hand.cards.size.toString(),
                player.accountId, PlayZone.HAND, hand.cards, TableItemKind.HAND, hp.id)
            hand.cards.forEachIndexed { n, id -> byId[id]?.let { c ->
                // ZON-079: the raised card sits a quarter of its height higher
                val raised = ownSide && !reviewing && id == ui.raised
                val b = TableBounds(start + n * (handWidth + 4f), handBounds.y + (handBounds.height - handHeight) / 2 -
                    if (raised) handHeight * RAISE else 0f, handWidth, handHeight)
                card(c, b, b, player.accountId, PlayZone.HAND, hp.id)
                if (raised) items[items.lastIndex] = items.last().copy(raised = true)
            } }
            val field = fields[index]
            val battlefield = container(player.accountId, PlayZone.BATTLEFIELD)
            // TBL-004, TBL-053: each card where its group puts it, drawn behind to front; an attached card peeks out above
            // its host (on both sides, so its name shows), wherever the host lies
            for ((id, spot) in spots[battlefield.id].orEmpty().entries.sortedBy { it.value.order }) byId[id]?.let { c ->
                val x = field.originX + spot.x * fieldScale + 16f * fieldScale
                val rowHeight = field.rowHeight
                val dy = spot.row * rowHeight
                val rowY = if (ownSide) field.gridBounds.bottom - rowHeight - dy else field.gridBounds.y + dy
                val y = rowY + (rowHeight - envelope) / 2
                val footprint = TableBounds(x, y, columnPitch * zoom, envelope)
                val lift = (spot.lifts / 2f - spot.lift) * BattlefieldLayout.PEEK * fieldScale
                val b = battlefieldCardBounds(footprint, if (c.tapped) ch else cw, if (c.tapped) cw else ch, lift)
                card(c, b, footprint, player.accountId, PlayZone.BATTLEFIELD, battlefield.id)
                items[items.lastIndex] = items.last().copy(group = spot.group, copies = maxOf(1, spot.copies),
                    label = if (spot.copies > 1) items.last().label + ", stack of ${spot.copies}" else items.last().label)
            }
            port(battlefield.id, field.bounds, (logicalRight + 32f) * fieldScale)
            // Mirror the player blocks: counters face the outside, piles face the Stack.
            val rowY = dockRowY[index]
            val rowHeight = dockRowHeights[index]
            val spacing = dockSpacing
            val portraitDock = portraitDockWidth(pileWidth, 3)
            val portraitCounters = (width - portraitDock).coerceAtLeast(0f)
            val shrink = portraitCounters >= MIN_SHRUNK_COUNTER_GRID
            // TBL-009: in landscape the row starts after the turn header in the top bar, or at the bottom bar's left end
            val dockX = if (!landscape) 0f else if (ownSide) Landscape.OWN_X else Landscape.opponentX(width)
            val dockWidth = if (landscape) Landscape.rowRight(width, ownSide, places) - dockX
                else if (portraitCounters >= gridWidth) width - gridWidth
                else if (shrink) portraitDock
                else maxOf(width - gridWidth, minOf(portraitDock, width))
            val dock = TableBounds(dockX, rowY, dockWidth, rowHeight)
            val dp = port("dock/${player.accountId}", dock, if (landscape) dockWidth else portraitDock +
                (places - 3) * spacing, bar = landscape)
            // Each pile's left edge: in landscape centred in its own touch target after the life square
            fun pileX(n: Int) = if (landscape) Landscape.pilesRight(dockX, n) + (Landscape.PILE_PITCH - pileWidth) / 2
                else PILE_START + n * spacing - dp.scroll
            val pileY = if (landscape) rowY + (rowHeight - pileHeight) / 2 else rowY + 12f
            // TRN-053 (owner gallery review 2026-11-06): the emblems as round chips, after the room chip (TRN-069)
            val rooms = listOfNotNull(dungeon?.room?.takeIf { it.isNotEmpty() }?.let { "Room: $it" })
            val emblemTexts = EmblemChips.texts(play.board, player.accountId)
            // TRN-053: in portrait the emblem chips sit in the empty room right of the piles, clear of the hands; in landscape
            // right after the piles in the bar row
            val pilesRight = PILE_START + (places - 1) * spacing + pileWidth - dp.scroll
            val emblemArea = if (landscape) (Landscape.pilesRight(dockX, places) + 4f).let { x ->
                    val room = (dock.right - x).coerceAtLeast(0f)
                    TableBounds(x, rowY + 4f, if (rooms.isEmpty()) minOf(room, EmblemChips.rowWidth(EmblemChips.chipCount(emblemTexts.size)))
                        else room, rowHeight - 8f) }
                else TableBounds(pilesRight + 8f, rowY + 4f, width - pilesRight - 16f, dockRow - 8f)
                .takeIf { it.width >= EmblemChips.MIN_AREA_WIDTH }
            val life = if (landscape) TableBounds(dockX, rowY + (rowHeight - Landscape.LIFE) / 2, Landscape.LIFE, Landscape.LIFE)
                else TableBounds(10f - dp.scroll, rowY + 8f, 48f, 48f)
            val emblemChips = EmblemChips.round(emblemTexts.size, life, emblemArea, player.accountId != viewer,
                EmblemChips.textLines(rooms, life, width))
            val textArea = emblemArea?.let { a -> if (emblemChips.isEmpty()) a
                else a.copy(width = a.width - EmblemChips.rowWidth(emblemChips.size) - 6f) }
            items += TableItem("life/${player.accountId}", life,
                "${name(player.accountId)}, life ${player.life}",
                player.life, player.accountId, kind = TableItemKind.LIFE, viewport = dp.id,
                emblems = rooms, emblemArea = textArea, emblemTexts = emblemTexts, emblemChips = emblemChips)
            /** A small pile's 48 dp touch target in landscape, around it. */
            fun touch(b: TableBounds) = if (landscape) TableBounds(b.center.x - maxOf(48f, b.width) / 2,
                b.center.y - maxOf(48f, b.height) / 2, maxOf(48f, b.width), maxOf(48f, b.height)) else null
            for ((n, c) in listOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE).map { container(player.accountId, it) }.withIndex()) {
                val zone = c.zone
                val top = byId[c.cards.firstOrNull()]
                val hidden = zone == PlayZone.LIBRARY && !c.keepTopRevealed || top == null || !CardVisibility.isVisibleFaceUp(play.board, top, viewer)
                val b = TableBounds(pileX(n), pileY, pileWidth, pileHeight)
                val title = zone.name.lowercase()
                items += TableItem(c.id, b, "${name(player.accountId)} $title, ${c.cards.size} cards${if (c.keepTopRevealed) ", keep top card revealed" else ""}", c.cards.size.toString(),
                    top?.owner ?: player.accountId, zone, c.cards, viewport = dp.id, covered = hidden,
                    footprint = b, peekOwners = listOfNotNull(top?.owner),
                    touchBounds = touch(b),
                    token = top?.token == true,
                    keepTopRevealed = c.keepTopRevealed,
                    faceTitle = if (hidden) "" else face(top)?.name.orEmpty(),
                    corner = if (hidden) "" else top?.corner.orEmpty(),
                    typeIcon = if (hidden) "" else cardTypeIcon(face(top)?.typeLine.orEmpty()), artwork = art(top, hidden),
                    changedPowerToughness = !hidden && changedPowerToughness(top?.corner.orEmpty(), face(top)))
            }
            // The player counters' room stays, empty (I-D52: no counters by hand); in portrait right of the piles (landscape
            // has none: the bar row holds only the life square, piles and emblems)
            if (!landscape) port("counters/${player.accountId}",
                TableBounds(dock.right, rowY, (width - dock.right).coerceAtLeast(0f), dockRow),
                if (shrink) minOf(gridWidth, portraitCounters) else gridWidth)
            dungeon?.let { d ->
                val card = byId[d.cardId]
                val b = TableBounds(pileX(3), pileY, pileWidth, pileHeight)
                items += TableItem("dungeon/${player.accountId}", b, "${name(player.accountId)} dungeon ${d.name}, room ${d.room}",
                    d.room, player.accountId, kind = TableItemKind.DUNGEON, viewport = dp.id, cardId = d.cardId, frozen = true,
                    sourceId = null, footprint = b, faceTitle = d.name, artwork = art(card, false), faceId = card?.faceId,
                    typeLine = face(card)?.typeLine.orEmpty(), touchBounds = touch(b))
            }
        }
        val stack = container(null, PlayZone.STACK)
        val stackGeometry = CombatStackPresentation.stack(play.board, stackWidth, stackHeight)
        // Centre between the actual pile rows, not the asymmetric pile + counter blocks.
        val stackPadding = 12f
        val stackHalfHeight = stackHeight / 2 + stackPadding
        val portraitStackY = if (worldViewport.height >= 2 * stackHalfHeight)
            (height / 2).coerceIn(worldViewport.y + stackHalfHeight, worldViewport.bottom - stackHalfHeight)
            else worldViewport.center.y
        // STK-007: pinned at the right edge, vertically mid-screen (in landscape too, TBL-009)
        val stackCenter = TablePoint(width - stackWidth / 2 - stackPadding, portraitStackY)
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
        // CMB-007: the band on the screen (the table's whole width), and the lanes it holds in its middle
        val band = world.screen(TableBounds(world.panBounds.x, bandY, world.panBounds.width, bandHeight), camera)
        val lanesBounds = if (combat.lanes.isEmpty()) null else world.screen(TableBounds(combatX, combatY, combat.width, combat.height), camera)
        val combatPanel = lanesBounds?.takeIf { open }
        val combatBounds = lanesBounds ?: world.screen(TableBounds(combatX, combatY, 0f, 0f), camera)
        val cp = port("combat", combatBounds.intersection(worldViewport) ?: combatBounds.copy(height = 0f), combatBounds.width)
        fun translated(b: TableBounds) = world.screen(b.copy(x = b.x + combatX, y = b.y + combatY), camera)
        for (drawing in combat.cards) card(drawing.card, translated(drawing.bounds), translated(drawing.bounds), null,
            PlayZone.BATTLEFIELD, cp.id, drawing.viewId, projection = true)
        val drops = combat.destinations.map { region ->
            val bounds = translated(region.bounds)
            // Collapsed, each lane is one whole attacker drop over the chip row; the outermost reach the band's ends
            if (!open) return@map region.copy(bounds = bounds.copy(
                x = if (region.extendLeft) band.x else bounds.x,
                width = (if (region.extendRight) band.right else bounds.right) - (if (region.extendLeft) band.x else bounds.x)),
                extendLeft = false, extendRight = false, landing = world.screen(TablePoint(region.landing.x + combatX, region.landing.y + combatY), camera))
            val top = lanesBounds!!.y
            val bottom = lanesBounds.bottom
            // The two card rows fill the panel except the lane label row (on top, or at the bottom when mirrored).
            val rowTop = if (combat.mirrored) top else world.screen(TablePoint(0f, combatY + combat.rowsTop), camera).y
            val rowBottom = if (combat.mirrored) world.screen(TablePoint(0f, combatY + combat.rowsBottom), camera).y else bottom
            val half = ((rowBottom - rowTop) / 2).coerceAtLeast(0f)
            val upper = if (combat.mirrored) CombatRole.ATTACKER else CombatRole.BLOCKER
            val row = bounds.copy(y = rowTop + if (region.destination.role == upper) 0f else half, height = half)
            // Screen-dp tolerance, capped just short of neighboring slot midpoints at small zoom.
            val neighbors = listOfNotNull(region.destination.pairGap.afterId, region.destination.pairGap.beforeId)
            val tolerance = minOf(12f, combat.destinations.filter { it.destination.pairId in neighbors &&
                it.destination.insertion == CombatInsertion.APPEND }.minOfOrNull { it.bounds.width * zoom / 2 - .01f } ?: 12f)
            val placed = if (region.destination.pairId == null && !region.exact) row.copy(x = row.center.x - tolerance, width = 2 * tolerance) else row
            // The band takes combat drops: the outermost ones reach its ends
            val left = if (region.extendLeft) band.x else maxOf(placed.x, lanesBounds.x)
            val right = if (region.extendRight) band.right else minOf(placed.right, lanesBounds.right)
            region.copy(bounds = placed.copy(x = left, width = (right - left).coerceAtLeast(0f)), extendLeft = false, extendRight = false,
                landing = world.screen(TablePoint(region.landing.x + combatX, region.landing.y + combatY), camera))
        }
        // Card-slot separators span only the card rows; lane separators cross the whole area, label row included.
        val dividers = combat.slotDividers.map { x -> world.screen(TablePoint(x + combatX, combatY + combat.rowsTop), camera) to
             world.screen(TablePoint(x + combatX, combatY + combat.rowsBottom), camera) }
        val laneDividers = combat.laneDividers.map { x -> world.screen(TablePoint(x + combatX, combatY), camera) to
            world.screen(TablePoint(x + combatX, combatY + combat.height), camera) }
        val labels = combat.lanes.map { lane -> lane.copy(bounds = translated(lane.bounds), fontSize = lane.fontSize * zoom,
            inset = lane.inset * zoom, chip = translated(lane.chip),
            artwork = if (lane.cardId == null) lane.artwork else play.board.cards.find { it.id == lane.cardId }
                ?.takeIf { !it.isFaceDown && CardVisibility.isVisibleFaceUp(play.board, it, viewer) }?.let { art(it, false) }) }
        val realSources = play.board.cards.associate { c -> c.id to TableMoveSource(c.containerId, positions[c.id],
            play.board.containers.any { it.id == c.containerId && it.zone == PlayZone.LIBRARY && it.cards.firstOrNull() == c.id }) }
        val sources = realSources + items.filter { it.projection && it.sourceId != null }.associate { item ->
            item.cardId!! to realSources.getValue(item.sourceId!!).copy(sourceId = item.sourceId) }
        return clip(cardPanels(TableScene(items, fields, ports, cw, ch, envelope, camera, sources, drops, dividers,
             stackRegion = stackRegion, world = world, appearance = appearance,
            combatLabels = labels, combatLaneDividers = laneDividers, combatPanel = combatPanel,
            combatBand = band.intersection(worldViewport), combatCardWidth = combatCard * zoom, combatCardHeight = combatCard * hudHeight / hudWidth * zoom,
            stackProjections = stackProjections, cardOwners = play.board.cards.associate { it.id to it.owner } +
                items.filter { it.cardId != null && it.owner != null }.associate { it.cardId!! to it.owner!! },
            pileCards = PileCardDrawings { id ->
                // Built only on demand: for a card that changed pile, never for every pile member per update.
                val member = byId[id] ?: return@PileCardDrawings null
                val pile = play.board.containers.firstOrNull { it.id == member.containerId && it.zone in PublicPiles.zones }
                    ?: return@PileCardDrawings null
                val at = TableBounds(0f, 0f, pileWidth, pileHeight)
                card(member, at, at, pile.accountId, pile.zone, "", draw = false)
            }, battlefieldGroups = groups, expandedGroup = expanded), play, viewer, panels), width, height)
    }

    /** Used after animation too: rendered bounds own drawing, targeting and accessibility. */
    fun clip(scene: TableScene, width: Float, height: Float): TableScene {
        val screen = TableBounds(0f, 0f, width, height)
        val ordered = scene.items.filter { it.zone == PlayZone.BATTLEFIELD && !it.viewport.startsWith("cardPanel/") } +
            scene.items.filter { it.zone != PlayZone.BATTLEFIELD && !it.viewport.startsWith("cardPanel/") } + scene.items.filter { it.viewport.startsWith("cardPanel/") }
        val viewports = scene.viewports.associateBy { it.id }
        val clipped = ordered.map { i ->
            val viewport = viewports[i.viewport]
            // ZON-079: a raised hand card shows whole above its row
            val port = viewport?.bounds?.let { b -> if (i.raised && i.bounds.y < b.y) b.copy(y = i.bounds.y, height = b.bottom - i.bounds.y) else b }
            val regions = listOfNotNull((if (i.inTransit) {
                if (i.zone == PlayZone.BATTLEFIELD) scene.world?.visibleViewport ?: screen else screen
            } else port)?.let(i.bounds::intersection)?.intersection(screen))
            val panelClipped = if (i.viewport.startsWith("cardPanel/") || i.inTransit) regions else
                scene.cardPanels.fold(regions) { parts, panel -> parts.flatMap { it.subtract(panel.bounds) } }
            i.copy(visible = if (i.zone == PlayZone.BATTLEFIELD && scene.stackRegion != null)
                panelClipped.flatMap { it.subtract(scene.stackRegion) } else panelClipped,
                touchBounds = i.touchBounds?.intersection(viewport?.bounds ?: screen)?.intersection(screen))
        }
        val occlusion = TableOcclusionIndex(clipped)
        val visible = clipped.mapIndexed { n, i ->
            var portions = i.visible
            if (portions.isNotEmpty() && i.kind != TableItemKind.HAND) for (next in occlusion.candidates(n)) {
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
    fun logicalPlacements(play: TableBoard): Map<String, TablePlacement> {
        val byId = play.board.cards.associateBy { it.id }
        return buildMap {
            for (container in play.board.containers) {
                val depths = mutableMapOf<PlayPlacement, Int>()
                for (id in container.cards) {
                    // Stack membership can name effects rather than real cards.
                    val p = byId[id]?.placement ?: continue
                    val depth = depths[p] ?: 0
                    put(id, TablePlacement(p.position.x, p.position.y, depth, p.pileId))
                    depths[p] = depth + 1
                }
            }
        }
    }
    /** Menu toggles must not mistake a legacy default cover for an explicit public face-down edit. */
    fun cardForActions(play: TableBoard, cardId: String): PlayCard {
        val card = play.board.cards.single { it.id == cardId }
        val zone = play.board.containers.single { it.id == card.containerId }.zone
        return if (zone == PlayZone.LIBRARY || zone == PlayZone.HAND) card else card.copy(covered = card.isFaceDown)
    }

    fun inspection(play: TableBoard, viewer: String, cardId: String): CardDefinition? {
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

/** The cards whose spell Automatum has on the Stack (UI review round 2 #9: not drawn in their hand meanwhile). */
internal fun castSpellSources(board: BoardState): Set<String> =
    board.stackEffects.filter { it.item?.kind == MtgStackKind.Spell }.mapTo(HashSet()) { it.sourceId }

/** CMB-001 (owner playtest 2026-11-06): whether [viewer] declares attackers on [board]: the collapsed band then shows a
 * chip for every target they may attack. */
internal fun combatChipStep(board: BoardState, viewer: String): Boolean =
    board.turn.accountId == viewer && board.priority?.holder == null && TurnPosition.of(board.turn) == TurnPosition.DECLARE_ATTACKERS

/** The declare attackers step of [board] (its chips fade in once per step, CMB-001). */
internal fun combatBandKey(board: BoardState): String = "${board.turn.number}/${board.turn.phase}/${board.turn.step}"
