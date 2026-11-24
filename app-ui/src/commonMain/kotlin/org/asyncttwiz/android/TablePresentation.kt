package org.asyncttwiz.android

import org.asyncttwiz.protocol.Faults
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ArtworkReference
import org.asyncttwiz.appui.NativeColors
import org.asyncttwiz.appui.toRadians
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color


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
/** One affine transform for the entire world. Camera offsets are world units from its home center. [bottomInset] (screen
 * dp): how far past the usual bounds the world may be panned up, so its bottom can sit above an open Automatum panel
 * (D2: the panel's height). [leftInset] (screen dp, WEB-016): how much of the viewport's left the desktop table's seat boxes
 * cover: the opening view ([landscapeHome]) puts column 0 right of it and [fit] fits the table into the rest, while the
 * world still pans under it freely (only how far right it may be panned grows by as much, so those views are in bounds).
 * [clipViewport] (TBL-238, WEB-015): where the world is seen, drawn and touched, around [viewport] (which places, opens, fits
 * and limits it): the table's whole width and the margins next to it (in landscape with large hand cards also the room under
 * them). [clearViewport] (TBL-238): the part of it clear of the hands' large cards, which what lies centred or docked on the
 * battlefields keeps to; null: all of it. [panViewport] (TBL-238): a taller room (only its top and bottom count) the
 * battlefields may also be panned in besides [viewport] (in landscape under the large hand cards); null: none. [minZoom]
 * (TBL-010): the least zoom (the desktop table's lower, [DesktopTable.MIN_ZOOM]). */
data class TableWorld(val viewport: TableBounds, val bounds: TableBounds, val panBounds: TableBounds = bounds,
    val clipViewport: TableBounds? = null, val bottomInset: Float = 0f, val leftInset: Float = 0f,
    val clearViewport: TableBounds? = null, val panViewport: TableBounds? = null,
    val minZoom: Float = TablePresentation.MIN_ZOOM) {
    val visibleViewport get() = clipViewport ?: viewport
    /** TBL-238: where the battlefields are seen clear of the hands' large cards ([clearViewport], else [visibleViewport]). */
    val clearOfHands get() = clearViewport ?: visibleViewport
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
        fun offset(value: Float, start: Float, end: Float, home: Float, extent: Float, past: Float = 0f): Float {
            val a = start - home + extent / (2 * c.zoom)
            val b = end - home - extent / (2 * c.zoom)
            // [past]: the end may also come that far inside the viewport (the bottom above an open panel)
            return value.coerceIn(minOf(a, b), maxOf(a, b, b + past))
        }
        // WEB-016: content as large as the room right of [leftInset] covers that room (its left end may come as far in as the
        // inset); smaller content stays inside the viewport. Without an inset this is [offset]'s sorting
        val a = panBounds.x - home.x + viewport.width / (2 * c.zoom)
        val b = panBounds.right - home.x - viewport.width / (2 * c.zoom)
        val least = a - leftInset / c.zoom
        val x = if (least <= b) c.x.coerceIn(least, b) else c.x.coerceIn(b, a)
        val room = panViewport ?: return c.copy(x = x,
            y = offset(c.y, panBounds.y, panBounds.bottom, home.y, viewport.height, bottomInset / c.zoom))
        // TBL-238: anywhere the viewport's limits allow, and also anywhere the taller room's would: small content may then lie
        // under the hands' large cards; content taller than both keeps the viewport's limits (it covers the room anyway)
        fun align(world: Float, screen: Float) = world - home.y - (screen - viewport.center.y) / c.zoom
        val top = align(panBounds.y, viewport.y); val bottom = align(panBounds.bottom, viewport.bottom)
        val roomTop = align(panBounds.y, room.y); val roomBottom = align(panBounds.bottom, room.bottom)
        return c.copy(x = x, y = c.y.coerceIn(minOf(top, bottom, roomTop, roomBottom),
            maxOf(top, bottom, roomTop, roomBottom, bottom + bottomInset / c.zoom)))
    }
    /** This world with [inset] dp more room to pan up (D2: an open panel's height). */
    fun withBottomInset(inset: Float) = if (inset == bottomInset) this else copy(bottomInset = inset.coerceAtLeast(0f))
    /** This world, extended upward just enough to hold [camera] where it is (D2: the table stays put after a panel
     * closes, until a pan settles it into the usual bounds). */
    fun holding(camera: TableCamera): TableWorld {
        val c = normalize(camera)
        if (c.y <= clamp(c).y) return this
        val b = panBounds.bottom - home.y - viewport.height / (2 * c.zoom)
        return copy(bottomInset = (c.y - b) * c.zoom)
    }
    fun normalize(camera: TableCamera) = TableCamera(camera.x.takeIf { it.isFinite() } ?: 0f,
        camera.y.takeIf { it.isFinite() } ?: 0f,
        camera.zoom.takeIf { it.isFinite() }?.coerceIn(minZoom, TablePresentation.MAX_ZOOM) ?: 1f)
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
    /** [band]'s rows across the whole width the battlefields are seen in ([visibleViewport], TBL-238), cut to it. */
    fun horizontalBand(band: TableBounds) = visibleViewport.let { v ->
        TableBounds(v.x, band.y, v.width, band.height).intersection(v) ?: TableBounds(v.x, v.y, v.width, 0f) }
    /** TBL-121: the opening view in landscape: zoomed out (to at most 1x, at least the smallest zoom) until the
     * battlefields' rows fit the height, centred on them, with column 0 still at the left edge as at 1x (WEB-016: on the
     * desktop table [leftInset] from it, right of the seat boxes). */
    fun landscapeHome(): TableCamera {
        val zoom = (viewport.height / bounds.height).coerceIn(minZoom, 1f)
        return TableCamera((home.x - leftInset) / zoom - home.x, bounds.center.y - home.y, zoom)
    }
    /** TBL-012: the whole table fitted into the viewport (WEB-016: into its part right of [leftInset]), centred there. */
    fun fit(): TableCamera {
        val room = (viewport.width - leftInset).coerceAtLeast(1f)
        val zoom = minOf(room / bounds.width, viewport.height / bounds.height)
            .coerceIn(minZoom, TablePresentation.MAX_ZOOM)
        return clamp(TableCamera(bounds.center.x - home.x - leftInset / (2 * zoom), bounds.center.y - home.y, zoom))
    }
}
data class TablePlacement(val column: Float, val row: Float, val pileIndex: Int = 0, val pileId: String? = null) {
    constructor(column: Int, row: Int, pileIndex: Int = 0, pileId: String? = null) : this(column.toFloat(), row.toFloat(), pileIndex, pileId)
}

/** A battlefield card centred in its [footprint] (tapped cards rotate in place), [dy] lower (an attached card peeking out
 * above its host is higher, TBL-053). */
fun battlefieldCardBounds(footprint: TableBounds, width: Float, height: Float, dy: Float = 0f): TableBounds =
    TableBounds(footprint.center.x - width / 2, footprint.center.y - height / 2 + dy, width, height)

/** TBL-003: ten percent above/below every face, plus room for the attached cards peeking out above their hosts ([lifts]
 * peek steps, at least one). */
fun battlefieldRowHeight(cardHeight: Float, scale: Float, lifts: Int = 1) =
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
    val expandedGroup: String? = null,
    /** STK-024: the sources paying the open payment: they lie in a pile of their own, to the right of their stack; the
     * viewer's own. */
    val paying: Set<String> = emptySet(),
    /** WEB-015 (owner 2026-11-16): the viewer's hand card the mouse is on on the desktop table: it is drawn over the others
     * where it lies in the fan; the viewer's own. */
    val hovered: String? = null)
/** This layout state without its camera: equal while only the camera changes (a pan or zoom step), so what composition
 * reads of it through this recomposes nothing then (web frame rate, 2026-11-15). */
fun State<TableLayoutState>.withoutCamera(): State<TableLayoutState> =
    androidx.compose.runtime.derivedStateOf(androidx.compose.runtime.structuralEqualityPolicy()) {
        value.let { if (it.camera == TableCamera()) it else it.copy(camera = TableCamera()) } }

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
    /** STK-006 (owner playtest 2026-11-15): a Stack entry that goes nowhere when it leaves the Stack (an ability, a trigger, a
     * copy of a spell): it rises a little and fades out there, as from a pile, instead of flying anywhere. */
    val vanishes: Boolean = false,
    val typeIcon: String = "", val artwork: ArtworkReference? = null,
    val inTransit: Boolean = false, val cardTransform: TableCardTransform? = null,
    /** CMB-001 (owner playtest 2026-11-11): a combat copy fading out where it was as the band closes: clipped to the table
     * rather than the band, covering nothing beneath it, taking no touch. */
    val leaving: Boolean = false,
    val flipScale: Float = 1f, val copies: Int = 1, val faceTitle: String = "",
    val countBadge: TableBounds? = null, val typeLine: String = "", val keepTopRevealed: Boolean = false,
    val faceId: String? = null, val changedPowerToughness: Boolean = false, val frontTypeLine: String = typeLine,
    val damage: String = "",
    val touchBounds: TableBounds? = null,
    /** HAND only (TBL-238): in landscape with hand cards taller than the bar, the part of the hand row in its bar: only it is
     * drawn as the row and takes touches as the row; above it the battlefields are seen and touched between the cards. */
    val handBand: TableBounds? = null,
    /** A player's Temp pile: a LIBRARY-zone pile that is never that player's Library. */
    val temp: Boolean = false,
    /** The Temp pile's number 1..4 (0 for every other item); an empty pile may not exist yet (placeholder). */
    val tempNumber: Int = 0,
    /** A face-up Battlefield card (or its combat copy) that entered the Battlefield this turn (rules v29). */
    val enteredThisTurn: Boolean = false,
    /** A public face-down Battlefield card's title stand-in ("?"): the same for every viewer, never its front. */
    val coveredTitle: String = "",
    /** LIFE only (TRN-053, TRN-072): the free room by the player's piles their round chips lie in ([PlayerChips.layout]). */
    val chipsArea: TableBounds? = null,
    /** LIFE only (TRN-053, I-D25): the player's emblems' rules texts, oldest first. */
    val emblemTexts: List<String> = emptyList(),
    /** LIFE only (TRN-053): where the emblems' round chips are ([PlayerChips.layout]); the last a "+N" chip when there are
     * more emblems than chips ([EmblemChips.shown]). */
    val emblemChips: List<TableBounds> = emptyList(),
    /** LIFE only (TRN-072, TRN-069): the player's round state chips ("Monarch", "Ring 2/4", the dungeon's room first) where they lie. */
    val stateChips: List<PlacedStateChip> = emptyList(),
    /** Automatum on (I1): the colour of a computed P/T (up, down, mixed); null for the manual corner's colours. */
    val cornerColor: Int? = null,
    /** Automatum on (I1): an until-end-of-turn effect changes the computed P/T (⌛ as TBL-023). */
    val cornerHourglass: Boolean = false,
    /** Automatum on (I1): its states, changed types and colours, keywords gained or lost, as one chip (TBL-113). */
    val automatumMarks: List<String> = emptyList(),
    /** TBL-133: the first of [automatumMarks] is a state's word. */
    val automatumState: Boolean = false,
    /** TBL-135 (owner, 2026-11-11): its counters but the one its corner shows, as bubbles on its art ([CounterBubbles]). */
    val counterBubbles: List<org.asyncttwiz.automatum.ShownCounter> = emptyList(),
    /** TBL-136 (owner, 2026-11-12): a continuous effect other than counters applies to it: a violet sparkle in its top-left
     * corner ([org.asyncttwiz.automatum.ComputedBoard.withEffects]). */
    val effectsMarker: Boolean = false,
    /** ZON-079: a raised hand card (drawn above its hand row, not clipped by it at the top). */
    val raised: Boolean = false,
    /** STK-046: a Stack entry that is a triggered or activated ability, drawn as an ability token, not as its source card. */
    val stackAbility: StackAbilityShown? = null,
    /** D7 (owner 2026-11-19): a copy of a spell or ability on the Stack, drawn with the copy marker (never a permanent). */
    val stackCopy: Boolean = false,
    /** TBL-053: a battlefield card's group ([BattlefieldGroup.key]); null off the battlefield. */
    val group: String? = null,
    /** ZON-092: a pile holding a card with counters (a suspended card in exile), marked on the pile. */
    val countersInside: Boolean = false,
    /** CMB-047: a stack's member staged as an attacker or placed as a blocker while they are declared, drawn at its stack's
     * right end with the staged look; null otherwise. */
    val staged: CombatRole? = null,
    /** TBL-054 (owner, 2026-11-19): a battlefield stack's top card ([copies] > 1) with this many of its counted members tapped:
     * its count reads untapped/total ("2/3"), upright at [countCorner]'s bottom-left (its stack's bottom card). */
    val stackTapped: Int = 0,
    val countCorner: TableBounds? = null,
    /** ZON-001 (owner, 2026-11-11): the other player's hand in portrait, a small fan of its cards in their row: drawn as its
     * count only (its cards are their own items, turned). */
    val handFan: Boolean = false,
    /** ZON-079 (owner, 2026-11-12): a card of the viewer's hand row from another zone: the zone of its pile, shown as a badge;
     * null for every other item. */
    val handZone: PlayZone? = null,
    /** TBL-137 (owner notes 2026-11-14): an uncrewed Vehicle's "Crew N", drawn before its corner's P/T where it fits; null for
     * anything else (a crewed Vehicle is a creature and shows its P/T alone). */
    val crew: String? = null,
    /** ZON-079: on the viewer's hand, the x of the thin divider in the gap before its cards from other zones; null without one. */
    val handDivider: Float? = null,
    /** WEB-015 (owner playtest 2026-11-15): a card of the viewer's fanned hand on the desktop table: the part of the fan at
     * rest that is its own (from its left edge to the next card's, down to the window's bottom), which the mouse finds it by
     * even while it, or the card beside it, has risen out of the fan; null for every other item. */
    val handSlot: TableBounds? = null,
    /** TBL-139 (owner 2026-11-16): a hand or battlefield card drawn as its whole printed picture ([artwork] is the whole card)
     * with only the app's overlays it needs, by the Settings of the layout. */
    val fullPicture: Boolean = false,
    /** TBL-233 (owner, 2026-11-21): a battlefield card's card types now ("Artifact", "Creature"), drawn as small icons at its bottom
     * left ([CardTypeIcons]); empty off the battlefield or while Automatum gives it no values. */
    val cardTypes: List<String> = emptyList(),
    /** TBL-234 (owner, 2026-11-21): its subtypes a card the viewer can see refers to, drawn as small tags ([SubtypeTags]). */
    val subtypeTags: List<String> = emptyList(),
) {
    /** The card title's colour on the table: blue while it entered this turn, else the default text colour. */
    val titleColor: Int get() = if (enteredThisTurn) EnteredThisTurnTitleColor else NativeColors.DKGRAY
}

/** Lazily built, memoized drawings of public pile members, asked for only by cards that changed pile. Derived
 * from the scene's own board, so it takes no part in scene equality. [built] counts drawings made (tests). */
class PileCardDrawings(private val build: (String) -> TableItem?) {
    private val made = HashMap<String, TableItem?>()
    var built = 0; private set
    operator fun get(cardId: String): TableItem? = if (cardId in made) made[cardId]
        else build(cardId).also { made[cardId] = it; if (it != null) built++ }
    override fun equals(other: Any?) = other is PileCardDrawings
    override fun hashCode() = 0
    companion object { val NONE get() = PileCardDrawings { null } }
}

/** Shown in the title position of a face-down Battlefield card; it says nothing about the hidden front. */
const val FACE_DOWN_TITLE = "?"

/** Clearly blue, distinct from the own (teal) / opponent (red) table colours. */
const val EnteredThisTurnTitleColor = 0xff1a56db.toInt()
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
const val WORLD_PAN_REGION = "world/camera"
data class TableScene(val items: List<TableItem>, val fields: List<TableField>,
    val viewports: List<TableViewport> = emptyList(), val cardWidth: Float = 28f, val cardHeight: Float = 40f,
    val footprint: Float = 56f, val camera: TableCamera = TableCamera(),
    val sources: Map<String, TableMoveSource> = emptyMap(), val combatDrops: List<CombatDropRegion> = emptyList(),
    val combatDividers: List<Pair<TablePoint, TablePoint>> = emptyList(),
    val stackRegion: TableBounds? = null,
    /** TRN-073: the game's day or night, while it is one of them. */
    val dayNightChip: PlacedStateChip? = null,
    val world: TableWorld? = null, val stackProjections: List<TableItem> = emptyList(),
    val cardOwners: Map<String, String> = emptyMap(),
    val cardPanels: List<CardPanelGeometry> = emptyList(),
    val appearance: CardAppearance = CardAppearance(),
    val combatLabels: List<CombatLaneLabel> = emptyList(),
    val combatLaneDividers: List<Pair<TablePoint, TablePoint>> = emptyList(),
    /** CMB-007: the open combat band's lanes on the screen (not clipped to the table), in the middle of the band between
     * the battlefields; null while the band is collapsed. */
    val combatPanel: TableBounds? = null,
    /** CMB-028, CMB-034: the lanes' places for the next attacker or blocker, on the screen ([CombatSlot]). */
    val combatSlots: List<CombatSlot> = emptyList(),
    /** CMB-050 (owner playtest 2026-11-10): the combat damage dealt in the combat going on, until it ends (null: none). */
    val combatDealt: org.asyncttwiz.automatum.CombatDamageDealt? = null,
    /** CMB-050 (owner playtest 2026-11-11): the screen y of the open band's edge facing the attacking player's battlefield,
     * where the life they gained with the combat damage shows; null while the band is collapsed. */
    val combatGainEdge: Float? = null,
    /** CMB-031: the gap between the band's card rows, on the screen; null while it is collapsed. */
    val combatArrowGap: TableBounds? = null,
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
    val expandedGroup: String? = null,
    /** WEB-015: laid out for the desktop layout's table, whose cards are larger: the glows behind them grow with them
     * (TBL-117, [glowScale]). */
    val desktop: Boolean = false,
    /** WEB-016 (owner playtest 2026-11-15, owner notes 2026-11-16): on the desktop table each player's seat header as a box
     * floating over the battlefields, by account id: yours at the bottom left, the other player's at the top left; empty
     * elsewhere. */
    val desktopPlates: Map<String, TableBounds> = emptyMap(),
    /** WEB-015 (owner playtest 2026-11-15): what floats over the battlefields on the desktop table with a background of its
     * own (the seat headers, the action area, the timeline strip), with its colour: the battlefield cards under it are
     * hidden there and the mouse doesn't reach them through it. */
    val floats: List<Pair<TableBounds, Color>> = emptyList()) {
    /** TBL-117 (owner, desktop playtest 2026-11-14): how much wider than the phone's the glows of [item] (a card) are: on
     * the desktop table as much as the card is larger than the phone's 28 dp wide card, so they stand out from it as far
     * for its size as on the phone; 1 on the phone. */
    fun glowScale(item: TableItem): Float =
        if (!desktop) 1f else (minOf(item.bounds.width, item.bounds.height) / 28f).coerceAtLeast(1f)
    /** The battlefields' part of the screen that centred things lie in (the review dim, a play shown, the centre texts): on
     * the desktop table above what floats over their bottom (WEB-015), elsewhere all they show clear of the hands' large cards
     * (TBL-238). */
    val boardArea: TableBounds? get() = world?.let { if (desktop) it.viewport else it.clearOfHands }
    /** TBL-053: the group of the battlefield card [cardId], null for a card in no group. */
    fun groupOf(cardId: String?): BattlefieldGroup? = cardId?.let { id -> battlefieldGroups.firstOrNull { id in it.members } }
    fun worldInput(point: TablePoint) = world?.visibleViewport?.contains(point) == true &&
        stackRegion?.contains(point) != true && items.none { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE && it.stackBadge.contains(point) } &&
        cardPanels.none { it.bounds.contains(point) } && floats.none { it.first.contains(point) }
    fun hit(point: TablePoint): TableItem? {
        items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE && it.stackBadge.contains(point) }?.let { return it }
        // WEB-015: in the desktop table's fanned hand each card is found by its own part of the fan at rest, risen or not
        items.lastOrNull { !it.leaving && it.handSlot?.contains(point) == true }?.let { return it }
        val stack = items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE &&
            (it.visible.any { bounds -> bounds.contains(point) } || it.touchBounds?.contains(point) == true) }
        if (stack != null) {
            if (stack.stackBadge.contains(point)) return stack
            // Visible lower peeks retain their identity for hold-to-inspect, not top-card shortcuts.
            return items.lastOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.CARD &&
                it.viewport == stack.viewport && it.visible.any { bounds -> bounds.contains(point) } } ?: stack
        }
        // TBL-238: a landscape hand row with large cards is touched only in its bar: above it a touch between the cards reaches
        // the battlefield under them
        return items.asReversed().firstOrNull { item -> !item.leaving && item.ownsTouch(point) &&
            (item.visible.any { it.contains(point) } || item.touchBounds?.contains(point) == true) }
    }
    /** TBL-238: the hand row whose own room holds [point] (the row in its bar, [TableItem.handBand]), or whose card is drawn
     * there; null elsewhere. */
    fun handAt(point: TablePoint): TableItem? = items.firstOrNull { hand -> hand.kind == TableItemKind.HAND && hand.bounds.contains(point) &&
        (hand.ownsTouch(point) || items.any { it.kind == TableItemKind.CARD && it.viewport == hand.viewport && !it.leaving &&
            it.visible.any { part -> part.contains(point) } }) }
    fun rectangle(bounds: TableBounds): Set<String> = items.filter { it.kind == TableItemKind.CARD &&
        it.visible.any { part -> part.intersection(bounds) != null } }.mapNotNull { it.cardId }.toSet()
    fun viewport(point: TablePoint) = viewports.firstOrNull { it.bounds.contains(point) }
    /** Resolve once on first down, before a centroid can cross into another region. */
    fun panRegion(point: TablePoint): String? {
        cardPanels.firstOrNull { it.bounds.contains(point) }?.let { return it.viewport.id }
        if (stackRegion?.contains(point) == true || items.any { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE && it.stackBadge.contains(point) })
            return items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }?.viewport
        handAt(point)?.let { return it.viewport }
        if (worldInput(point)) return WORLD_PAN_REGION
        return viewports.firstOrNull { it.bounds.contains(point) && (it.maxScroll > 0 || it.maxScrollY > 0) }?.id
            ?: viewport(point)?.id
    }
    val stackTopId get() = items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }
        ?.cards?.firstOrNull() ?: items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.CARD }?.cards?.firstOrNull()
    fun canDrag(item: TableItem): Boolean = !item.frozen && (item.zone != PlayZone.STACK ||
        item.cardId != null && item.cardId == stackTopId)
}

val TableItem.stackBadge get() = countBadge ?: TableBounds(bounds.x, bounds.y, 20f, 20f)
/** TBL-238: whether a touch at [point] may be this item's: a hand row with a [TableItem.handBand] only there. */
fun TableItem.ownsTouch(point: TablePoint) = handBand?.contains(point) ?: true
/** The smaller special-pile bubble straddles the card's top-left corner in either layout. */
val TableItem.pileCountBubble get() = pileCountBubble(1f)
/** [pileCountBubble] [scale] times as large (a desktop seat header's, [DesktopTable.PILE_BADGE]). */
fun TableItem.pileCountBubble(scale: Float) = TableBounds(bounds.x - 8f * scale, bounds.y - 8f * scale, 16f * scale, 16f * scale)
val TableScene.columnPitch get() = TablePresentation.COLUMN_PITCH * cardWidth / 28f
val TableItem.visibleAnchor get() = if (visible.any { it.contains(bounds.center) }) bounds.center
    else visible.maxByOrNull { it.width * it.height }?.center

object TablePresentation {
    const val GRID = 56f
    /** ZON-079: how far a raised hand card rises, in its own heights. */
    const val RAISE = .25f
    /** ZON-079 (owner, 2026-11-12): the gap before the hand row's cards from other zones, its thin divider in the middle. */
    const val HAND_ROW_DIVIDER = 14f
    const val COLUMN_PITCH = 42f
    /** The open band's margin above and below its lanes (dp at zoom 1). */
    private const val COMBAT_BAND_PAD = 4f
    /** CMB-001: the id of the empty combat area the collapsed band's chips belong to while the viewer declares
     * attackers with none staged (drawn only, never shared; a creature dropped on a chip's lane is staged there). */
    const val OPENED_COMBAT_AREA = "opened"
    /** TBL-010: the least zoom (the desktop table's: [DesktopTable.MIN_ZOOM]). */
    const val MIN_ZOOM = .65f
    const val MAX_ZOOM = 2f
    const val HEADER = 48f
    /** TBL-238: the margin the battlefields are laid out inside below the header and above the bottom bar (landscape); they
     * are still seen in it. */
    private const val BOARD_MARGIN = 4f
    // Reserve the maximum chrome width so the opponent's fixed Hand does not move in review: three 48 dp buttons (step,
    // Return to current or Table actions, chat) with their gaps and the bar's end padding, and a gap before them.
    const val TOOLBAR = 168f
    private const val PILE_START = 76f
    /** The counters' room may be squeezed this far before it scrolls. */
    private const val MIN_SHRUNK_COUNTER_GRID = 60f
    fun portraitDockWidth(pileWidth: Float, piles: Int) = PILE_START + (piles - 1) * (pileWidth + 12f) + pileWidth + 8f
    /** (owner, 2026-11-11) your life and pile row in portrait: its least height, the life square's and the piles' tops. */
    private const val OWN_ROW = 56f
    private const val OWN_LIFE_TOP = 4f
    private const val OWN_PILE_TOP = 8f
    /** ZON-001 (owner, 2026-11-11): the other player's hand's fan in portrait: its cards this much of a pile card, turned
     * at most [FAN_STEP] degrees apart and [FAN_ANGLE] either side, after [FAN_GAP] from the piles, its count in the last
     * [FAN_COUNT]. */
    const val FAN_CARD = .75f
    const val FAN_STEP = 9f
    const val FAN_ANGLE = 18f
    const val FAN_GAP = 8f
    const val FAN_COUNT = 22f
    /** ZON-001: the fan's whole width, its count's room included, for cards of [cardWidth]x[cardHeight]. */
    fun handFanWidth(cardWidth: Float, cardHeight: Float): Float {
        val turn = toRadians(FAN_ANGLE.toDouble())
        val spread = 2 * cardHeight * kotlin.math.sin(turn)
        val outer = cardWidth * kotlin.math.cos(turn) + cardHeight * kotlin.math.sin(turn)
        return (spread + outer).toFloat() + FAN_COUNT
    }
    /** ZON-001: each of [count] fanned cards' centre and turn (degrees, clockwise), left to right, about [center]: the cards
     * turn about a point a card's height below their middle, as cards held in a hand. */
    fun handFan(count: Int, center: TablePoint, cardWidth: Float, cardHeight: Float): List<Pair<TablePoint, Float>> {
        val step = if (count <= 1) 0f else minOf(FAN_STEP, 2 * FAN_ANGLE / (count - 1))
        return (0 until count).map { n ->
            val turn = (n - (count - 1) / 2f) * step
            val radians = toRadians(turn.toDouble())
            TablePoint(center.x + (cardHeight * kotlin.math.sin(radians)).toFloat(),
                center.y + (cardHeight * (1 - kotlin.math.cos(radians))).toFloat()) to turn
        }
    }

    /** TBL-009 (owner, 2026-11-07): in landscape each player's life square, piles and emblems sit in a bar row (the
     * opponent's in the top bar, yours in the bottom bar), so the battlefield takes the whole width. */
    object Landscape {
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
        /** The opponent's emblems' room after [places] piles, fitted to the top bar's free width; [wanted]: what their row
         * of state chips would like (TRN-072), when more than the emblems' room. */
        fun emblemRoom(width: Float, places: Int, wanted: Float = EMBLEMS) = (width - TOOLBAR - 10f - MIN_OPPONENT_HAND - 10f -
            pilesRight(opponentX(width), places) - 4f).coerceIn(MIN_EMBLEMS, maxOf(EMBLEMS, wanted))
        /** The right end of a player's row: life square, [places] piles (the dungeon's the fourth, TRN-069) and the
         * emblems' room. Yours ([own], in the bottom bar before your hand) has the emblems' room only while you have
         * emblems or a dungeon room chip ([emblems]), fitted as the opponent's is ([ownEmblemRoom]). */
        fun rowRight(width: Float, own: Boolean, places: Int = 3, emblems: Boolean = false, wanted: Float = EMBLEMS) =
            if (own) pilesRight(OWN_X, places) + if (emblems) 4f + ownEmblemRoom(width, places, wanted) else 0f
            else pilesRight(opponentX(width), places) + 4f + emblemRoom(width, places, wanted)
        /** Your emblems' room after [places] piles: it gives way to your hand down to [MIN_OWN_HAND] before the controls,
         * down to [MIN_EMBLEMS]. */
        fun ownEmblemRoom(width: Float, places: Int, wanted: Float = EMBLEMS) = (width - controls(width) - 8f - MIN_OWN_HAND -
            pilesRight(OWN_X, places) - 4f).coerceIn(MIN_EMBLEMS, maxOf(EMBLEMS, wanted))
        /** TBL-009 (owner, 2026-11-08): the bottom bar's right end, one width whatever shows there (Undo, the quick actions
         * and the main button; in timeline mode its strip and Previous / Auto / Next), so your hand left of it never changes
         * width: [CONTROLS], at most half the bar. */
        fun controls(width: Float) = minOf(CONTROLS, width / 2)
        const val CONTROLS = 416f
        /** The narrowest your hand gets in the bottom bar (a card and its count) before your emblems' room gives way. */
        const val MIN_OWN_HAND = 64f
    }

    fun scene(game: GameSummary, play: TableBoard, viewer: String, width: Float, height: Float, landscape: Boolean,
        ui: TableLayoutState = TableLayoutState(), placements: Map<String, TablePlacement> = emptyMap(),
        toolbarWidth: Float = TOOLBAR, reviewing: Boolean = false,
        appearance: CardAppearance = CardAppearance(),
        logicalPositions: Map<String, TablePlacement> = logicalPlacements(play),
        computed: org.asyncttwiz.automatum.ComputedBoard = org.asyncttwiz.automatum.ComputedBoard.NONE,
        landscapeHome: Boolean = false,
        /** Timeline mode: its panel is taller than play mode's bottom bar in portrait ([tableBottomHeight]). */
        timeline: Boolean = false,
        /** CMB-028 (owner, 2026-11-08): the viewer declares attackers (Automatum says): the open band's lanes keep room for
         * the next one. */
        attackerSlots: Boolean = false,
        /** ZON-079 (owner, 2026-11-12): the cards from other zones the viewer's hand row shows at its right end ([HandRow.cards]). */
        handRow: List<HandRowCard> = emptyList(),
        /** WEB-015 to WEB-019: the desktop table's frame (with [landscape] true); null in the phone layout. */
        desktop: DesktopFrame? = null): TableScene {
        require(play.gameId == game.id && play.playId == game.playId)
        require(width > 0 && height > 0 && width.isFinite() && height.isFinite())
        val positions = logicalPositions + placements
        // The table is drawn for two players: a board without the viewer or with other than one other player is a fault,
        // reported, drawn with a bare seat for the one missing and only the first of several others (no 3+ player table yet)
        val others = play.board.players.filter { it.accountId != viewer }
        fun seatFault(what: String) = Faults.report("TablePresentation", what, mapOf("game" to game.id, "node" to play.nodeId,
            "viewer" to viewer, "players" to play.board.players.joinToString { it.accountId }))
        if (others.size != 1) seatFault("table drawn for ${others.size} other players")
        val own = play.board.players.firstOrNull { it.accountId == viewer }
            ?: PlayPlayer(viewer).also { seatFault("table drawn without the viewer on the board") }
        val opponent = others.firstOrNull()
            ?: PlayPlayer(if (game.inviter.accountId == viewer) game.recipient.accountId else game.inviter.accountId)
        val players = listOf(opponent, own)
        val byId = play.board.cards.associateBy { it.id }
        val definitions = play.definitions.associate { it.digest to it.definition }
        val castFromHand = castSpellSources(play.board)
        fun name(id: String?) = if (game.inviter.accountId == id) game.inviter.username else game.recipient.username
        // Every player has each zone's container; a board without one draws it empty
        fun container(player: String?, zone: PlayZone) = play.board.containers.firstOrNull { it.accountId == player && it.zone == zone && !it.temp }
            ?: PlayContainer("missing/$player/${zone.name}", zone, player).also {
                Faults.report("TablePresentation", "board without a zone's container", mapOf("game" to game.id, "player" to player.toString(), "zone" to zone.name))
            }
        fun face(card: PlayCard?) = card?.let { definitions[it.definition]?.faces?.firstOrNull { f -> f.faceId == it.faceId } }
        fun art(card: PlayCard?, hidden: Boolean): ArtworkReference? {
            val faces = card?.let { definitions[it.definition]?.faces } ?: return null
            val f = faces.firstOrNull { it.faceId == card.faceId } ?: return null
            if (hidden || (f.scryfallId == null && f.imageUri == null)) return null
            return f.tableArtwork(faces.indexOf(f))?.forTable()
        }
        val hudWidth = 28f; val hudHeight = 40f; val hudEnvelope = GRID
        val battlefieldScale = appearance.battlefield
        val columnPitch = COLUMN_PITCH * battlefieldScale
        val handWidth = hudWidth * appearance.size(CardArea.HAND)
        val handHeight = hudHeight * appearance.size(CardArea.HAND)
        // WEB-015: on the desktop table the battlefields end at the dock (the window's right edge with it folded away) and reach
        // the window's bottom (owner playtest 2026-11-15: the timeline strip's top in timeline mode), your seat header, hand and
        // the action area floating over their bottom ([DesktopTable.FLOAT] tall); the other player's seat header lies on theirs
        val tableRight = if (desktop == null) width else (width - desktop.dockWidth).coerceAtLeast(1f)
        val handRight = if (desktop == null) width else (width - DesktopTable.ACTIONS).coerceAtLeast(1f)
        val desktopBand = desktop?.let { DesktopTable.band(timeline) } ?: 0f
        val floorY = height - if (desktop != null && timeline) TableTimelineHeight else 0f
        // TBL-009: in landscape the piles fit their bar row; WEB-016, WEB-022: on the desktop table their seat header
        val pileScale = appearance.size(CardArea.PILES).let { if (landscape) minOf(it,
            (if (desktop != null) DesktopTable.PILE_HEIGHT else Landscape.PILE_HEIGHT) / hudHeight) else it }
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
        val controlsHeight = if (desktop != null) desktopBand else tableBottomHeight(landscape, timeline)
        val dockRow = maxOf(64f, 68f + pileHeight - hudHeight)
        // (owner, 2026-11-11) in portrait your life and pile row has no margin above the life square and no room under it
        val ownDockRow = if (landscape) dockRow else maxOf(OWN_ROW, OWN_PILE_TOP + pileHeight + 4f)
        // ZON-001 (owner, 2026-11-11): in portrait the other player's hand is a small fan in their life and pile row
        val fanCardWidth = pileWidth * FAN_CARD; val fanCardHeight = pileHeight * FAN_CARD
        val fanWidth = handFanWidth(fanCardWidth, fanCardHeight)
        val dockSpacing = if (landscape) Landscape.PILE_PITCH else pileWidth + 12f
        val gridWidth = minOf(132f, width * .34f)
        // TRN-069, TRN-053, TRN-072: what each player's life and pile row holds besides: their dungeon and their round chips
        val seatRows = players.associate { player ->
            val dungeon = play.engine?.let { org.asyncttwiz.automatum.EngineProjection.dungeon(it.state, player.accountId) }
            player.accountId to SeatRow(dungeon, EmblemChips.texts(play.board, player.accountId),
                listOfNotNull(dungeon?.room?.takeIf { it.isNotEmpty() }?.let(::roomChip)) +
                    // ZON-136 (owner playtest 2026-11-19, N17): the other player's cards they may use from other zones, one chip
                    listOfNotNull(computed.othersPlayable[player.accountId]?.takeIf { player.accountId != viewer && it.isNotEmpty() }
                        ?.let { OtherPlayableChip.chip(name(player.accountId), it.size) }) +
                    play.engine?.let { org.asyncttwiz.automatum.PlayerStates.of(it.state, player.accountId) }.orEmpty())
        }
        // WEB-016 (owner playtest 2026-11-15, owner notes 2026-11-16): on the desktop table each seat header is a box floating
        // over the battlefields, as wide as its contents (its life square, piles, the other player's hand's fan, chips): the
        // other player's at the top left, yours at the bottom left, each as tall as the action area
        val plates = if (desktop == null) emptyMap() else players.associate { player ->
            val row = seatRows.getValue(player.accountId)
            val right = DesktopTable.rowRight(tableRight, row.places, row.emblems, row.stateRow,
                fan = if (player.accountId == viewer) 0f else fanWidth, start = DesktopTable.PLATE_PAD) + DesktopTable.PLATE_PAD
            player.accountId to TableBounds(0f, if (player.accountId == viewer) floorY - DesktopTable.FLOAT else 0f, right, DesktopTable.FLOAT)
        }
        // WEB-016, TBL-121, TBL-012: the battlefields' column 0 lies just right of both boxes in the opening view and when the
        // table is fitted; the boxes take no height from the rows (their battlefields reach the top row and the bottom floats)
        val boxesRight = plates.values.maxOfOrNull { it.right } ?: 0f
        val boardTop = if (desktop != null) 4f else HEADER + BOARD_MARGIN + if (landscape) (handHeight - hudHeight).coerceAtLeast(0f) else 0f
        // TBL-009 (owner, 2026-11-08): in landscape your hand sits in the bottom bar (taller cards reach above it)
        val ownHandHeight = maxOf(HEADER, railHeight)
        // WEB-015, TBL-012, TBL-121: on the desktop table the battlefields are laid out (fitted, opened on) above what floats over
        // their bottom, and drawn down to the window's bottom under it
        val boardBottom = if (desktop != null) height - desktopBand - 4f
            else if (landscape) minOf(height - controlsHeight, height - 4f - ownHandHeight) - BOARD_MARGIN else height - controlsHeight - 4f
        val ownDockY = height - controlsHeight - ownDockRow
        // ZON-001: the other player's hand has no row of its own in portrait: their battlefield starts under their row
        val worldTop = if (landscape) boardTop else HEADER + dockRow
        val worldBottom = if (landscape) boardBottom else ownDockY - railHeight
        // TBL-009: the battlefields take the table's whole width in both orientations
        val worldViewport = TableBounds(6f, worldTop, (tableRight - 12f).coerceAtLeast(1f),
            (worldBottom - worldTop).coerceAtLeast(1f))
        // TBL-238 (owner playtest 2026-11-22): where the battlefields are seen (drawn, panned into, touched): the table's whole
        // width, with no margin at its sides, and in landscape up to both bars; the battlefields are still laid out, opened on,
        // fitted and pan-limited in [worldViewport], so nothing lies anywhere else. WEB-015: on the desktop table from the top
        // row down to the window's bottom (the timeline strip)
        val clearViewport = when {
            desktop != null -> TableBounds(0f, 0f, tableRight, floorY.coerceAtLeast(worldViewport.bottom))
            landscape -> TableBounds(0f, worldTop - BOARD_MARGIN, tableRight, worldViewport.height + 2 * BOARD_MARGIN)
            else -> worldViewport.copy(x = 0f, width = tableRight)
        }
        // TBL-238 (owner 2026-11-24): in landscape with hand cards taller than the bar the battlefields are seen under them too,
        // from the top bar down to the bottom bar, the hands' cards drawn over them; only a hand row's part in its bar stays the
        // row ([TableItem.handBand]). They are laid out, opened on and fitted where they were ([worldViewport]); what is centred
        // or docked on them keeps to [clearViewport], and they may also be panned under the hand cards, up to [BOARD_MARGIN] from
        // the bars ([TableWorld.panViewport])
        val largeHands = landscape && desktop == null && handHeight > hudHeight
        val barTop = height - controlsHeight
        val shownViewport = if (!largeHands) clearViewport
            else TableBounds(0f, HEADER, tableRight, (barTop - HEADER).coerceAtLeast(clearViewport.height))
        val panViewport = if (!largeHands) null else TableBounds(worldViewport.x, HEADER + BOARD_MARGIN, worldViewport.width,
            (barTop - HEADER - 2 * BOARD_MARGIN).coerceAtLeast(worldViewport.height))
        // WEB-016: how much of the battlefields' left the seat boxes take, and the battlefields' width right of them (at 1×)
        val boxInset = (boxesRight - worldViewport.x).coerceIn(0f, (worldViewport.width - 1f).coerceAtLeast(0f))
        val boardWidth = worldViewport.width - boxInset
        // TBL-009: in landscape the Stack (over the battlefields' right end, STK-007) fits between the hands
        val stackScale = appearance.size(CardArea.STACK).let { size -> if (!landscape) size
            else minOf(size, ((worldViewport.height - 24f) / hudHeight).coerceAtLeast(.5f)) }
        val stackWidth = hudWidth * stackScale
        val stackPadding = 12f
        val stackHeight = hudHeight * stackScale
        // WEB-016: on the desktop table under the other player's seat box, as under their seat header before
        val panelArea = if (desktop == null) worldViewport.copy(x = 0f, width = tableRight)
            else TableBounds(0f, DesktopTable.FLOAT + 4f, tableRight, (worldViewport.bottom - DesktopTable.FLOAT - 4f).coerceAtLeast(1f))
        val panels = cardPanelGeometry(play, viewer, ui, panelArea, appearance.size(CardArea.PANELS))
        // TBL-004, TBL-053 (Arena style): each battlefield lays itself out from the game state: its groups (stacks, cards with
        // what is attached to them) in its land row and upper band; the group the viewer opened lies with its members side by side
        val groups = BattlefieldLayout.groups(BattlefieldLayout.cards(play.board, definitions, computed), ui.paying)
        val expanded = ui.expandedGroup?.takeIf { key -> groups.any { it.key == key && it.members.size > 1 } }
        // TBL-004: the upper band's right end at 1×: the battlefield's own width on the screen left of the Stack pinned at its
        // right edge (STK-007), so the non-creatures lying from there leftwards aren't under it in the opening view (WEB-016: on
        // the desktop table from right of the seat boxes)
        val rowWidth = ((boardWidth - stackWidth - 2 * stackPadding) / battlefieldScale - 32f)
            .coerceAtLeast(BattlefieldLayout.SLOT)
        val spots = players.associate { player -> container(player.accountId, PlayZone.BATTLEFIELD).id.let { id ->
            id to BattlefieldLayout.place(groups.filter { it.field == id }, rowWidth, expanded) } }
        val logicalRight = spots.values.flatMap { it.values }.maxOfOrNull { it.x + BattlefieldLayout.SLOT } ?: (4 * BattlefieldLayout.SLOT)
        // Rows are world content, not fractions of the remaining screen. Small viewports pan. TBL-003: three rows each, the land
        // row by the player's own edge, the upper band's two by the middle (the combat band pushes the battlefields apart, CMB-007)
        val rowHeight = battlefieldRowHeight(hudHeight * battlefieldScale, battlefieldScale, BattlefieldLayout.lifts(groups))
        val fieldHeights = players.map { BattlefieldRow.COUNT * rowHeight }
        // CMB-042 (owner playtest 2026-11-06): the band is world content, zooming with the battlefields: its cards are
        // exactly the battlefield's
        val combatCard = hudWidth * battlefieldScale
        val combat = CombatStackPresentation.combat(bandBoard, combatCard,
            combatCard * hudHeight / hudWidth, play.definitions, mapOf(game.inviter.accountId to game.inviter.username, game.recipient.accountId to game.recipient.username), viewer,
            avatars = listOf(game.inviter, game.recipient)
                .mapNotNull { player -> player.avatar?.let { player.accountId to it } }.toMap(), open = open,
            attackerSlots = open && attackerSlots, head = "${play.gameId}@${play.nodeId}")
        // CMB-007: the open band pushes the battlefields apart to hold its lanes; collapsed it keeps the chip row's height.
        // Owner playtest 2026-11-11: it grows away from the viewer's battlefield (the lower one), which stays where it is with
        // the collapsed band, so only the other player's moves (up) as it opens and back as it closes
        val bandHeight = if (open) combat.height + 2 * COMBAT_BAND_PAD else CombatStackPresentation.CHIP_ROW
        val bandY = worldViewport.height / 2 + CombatStackPresentation.CHIP_ROW / 2 - bandHeight
        val logicalFields = listOf(TableBounds(0f, bandY - fieldHeights[0], worldViewport.width, fieldHeights[0]),
            TableBounds(0f, bandY + bandHeight, worldViewport.width, fieldHeights[1]))
        // TBL-121: an untouched camera in landscape opens on the battlefields' whole height
        val opening = landscape && landscapeHome && ui.camera == TableCamera()
        // WEB-016: the lanes centred in the battlefields' width right of the desktop table's seat boxes
        val combatX = (boardWidth - combat.width) / 2
        val worldLeft = minOf(0f, combatX)
        val worldRight = maxOf(boardWidth, (logicalRight + 32f) * battlefieldScale, combatX + combat.width)
        val contentBounds = TableBounds(worldLeft, logicalFields.first().y,
            worldRight - worldLeft,
            logicalFields.last().bottom - logicalFields.first().y)
        // Empty columns are navigable without making Fit table zoom out to include unused space.
        val world = TableWorld(worldViewport, contentBounds,
            contentBounds.copy(x = worldLeft - 3 * columnPitch, width = contentBounds.width + 6 * columnPitch),
            clipViewport = shownViewport, leftInset = boxInset, clearViewport = clearViewport.takeIf { largeHands },
            panViewport = panViewport, minZoom = if (desktop != null) DesktopTable.MIN_ZOOM else MIN_ZOOM)
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
        // TBL-238: and the whole width they are seen in
        val fieldLeft = minOf(pan.x, shownViewport.x)
        val fieldRight = maxOf(pan.right, shownViewport.right)
        val fields = players.mapIndexed { index, player ->
            val logical = world.screen(logicalFields[index], camera)
            val grid = TableBounds(fieldLeft, logical.y, fieldRight - fieldLeft, logical.height)
            val bounds = grid.intersection(shownViewport) ?: grid.copy(width = 0f, height = 0f)
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
            viewId: String = card.id, projection: Boolean = false, frozen: Boolean = false, draw: Boolean = true,
            /** ZON-079: a card of the viewer's hand row from another zone, which Automatum says they may see (I-D27). */
            known: Boolean = false): TableItem {
            // A face-down entry (STK-005) is a card back for both players, like a face-down card (ZON-073).
            val hidden = !known && (projection && CombatStackRules.faceDownEntry(play.board, viewId) ||
                if (!frozen) !CardVisibility.isVisibleFaceUp(play.board, card, viewer)
                else !CardVisibility.isVisibleFaceUp(card, zone, region, viewer))
            val f = face(card)
            // A face-down Battlefield card hides its face, but its values are public (rules v23).
            val faceDown = hidden && publicFaceDown(play.board, card)
            val values = !hidden || faceDown
            val title = if (hidden) "Covered card" else f?.shownName
                ?: Faults.placeholder("TablePresentation", "card without its definition", "Card (definition unavailable)", mapOf("card" to card.id))
            val tapped = zone == PlayZone.BATTLEFIELD && !projection && card.tapped
            // Combat copies follow their live source card; a face-down card's "?" title carries the mark too.
            val coveredTitle = if (zone == PlayZone.BATTLEFIELD && faceDown && !frozen) FACE_DOWN_TITLE else ""
            val entered = zone == PlayZone.BATTLEFIELD && (!hidden || coveredTitle.isNotEmpty()) && !frozen &&
                EnteredTurn.marked(play.board, card)
            // Automatum on (I1): a Battlefield card (and its combat copies) shows the engine's P/T and marks.
            val auto = if (values && zone == PlayZone.BATTLEFIELD && !frozen) computed.cards[card.id] else null
            // TBL-137 (owner notes 2026-11-14): an uncrewed Vehicle's corner is the P/T it has once crewed, after its "Crew N"
            val autoCorner = auto?.corner ?: auto?.takeIf { it.crew != null }?.let { a ->
                if (a.basePower != null && a.baseToughness != null) "${a.basePower}/${a.baseToughness}" else null }
            // STK-046: an ability on the Stack names itself as an ability, with its text
            val ability = if (zone == PlayZone.STACK && projection && !hidden) StackAbilities.of(play, viewId, viewer) else null
            // TBL-239 (owner, 2026-11-22): a card of the viewer's hand (and hand row) shows the battlefield's bottom row, its types and
            // the P/T (loyalty, defense) Automatum works out; a card on the Stack its types
            val framed = if (values && !frozen && ability == null && (zone == PlayZone.HAND || zone == PlayZone.STACK))
                computed.offBattlefield[card.id] else null
            val handFramed = framed?.takeIf { zone == PlayZone.HAND }
            // TBL-139 (owner 2026-11-16): hand and battlefield cards as their whole printed picture, as the layout's Settings say
            val artwork = art(card, hidden)
            val fullPicture = artwork != null && artwork.customUrl == null &&
                (zone == PlayZone.HAND && appearance.handFullPicture || zone == PlayZone.BATTLEFIELD && appearance.battlefieldFullPicture)
            val label = if (ability != null) "${ability.shownKind}, ${ability.line}, controller ${name(CombatStackRules.controller(play.board, viewId) ?: card.owner)}"
                // TBL-133: a card's states read aloud too, as the chip shows them
                else "$title, owner ${name(card.owner)}, ${if (tapped) "tapped" else "untapped"}" +
                    (if (entered) ", entered this turn" else "") + auto?.states.orEmpty().joinToString("") { ", $it" } +
                    // TBL-024: its damage badge read aloud too
                    (if (values && zone == PlayZone.BATTLEFIELD && card.damage.isNotEmpty()) ", ${card.damage} damage" else "") +
                    // TBL-136: its effects marker read aloud too
                    (if (auto != null && card.id in computed.withEffects) ", changed by effects" else "") +
                    // TBL-236, TBL-235: its card types and subtype tags read aloud too, before its counters
                    ((auto?.cardTypes ?: framed?.cardTypes)?.takeIf { it.isNotEmpty() }?.let { ", ${it.joinToString(" ")}" } ?: "") +
                    // TBL-239: a hand card's P/T (loyalty, defense) read aloud after its types
                    (handFramed?.let(::cornerWords) ?: "") +
                    auto?.tags.orEmpty().joinToString("") { ", $it tag" } +
                    // TBL-021, TBL-135: its counters read aloud in words, the corner's primary one too
                    auto?.counters.orEmpty().joinToString("") { ", ${it.words}" }
            return TableItem("card/$viewId", b, if (projection) "${if (zone == PlayZone.STACK && ability == null && CombatStackRules.copyEntry(play.board, viewId)) "Copy"
                    else if (frozen) "Retained effect, read-only" else "Projection"}, $label" else label,
                 if (hidden) "" else title, if (projection && zone == PlayZone.STACK) CombatStackRules.controller(play.board, viewId) else card.owner,
                 zone, kind = TableItemKind.CARD, viewport = viewport,
                cardId = viewId, covered = hidden, tapped = tapped, corner = if (!values) "" else autoCorner ?: if (handFramed != null) handFramed.corner.orEmpty() else card.corner,
                damage = if (!values) "" else card.damage,
                footprint = footprint, sourceId = if (frozen) null else card.id, projection = projection, frozen = frozen,
                token = card.token,
                   typeIcon = if (hidden) "" else cardTypeIcon(f?.typeLine.orEmpty()),
                    artwork = if (fullPicture) artwork?.fullPicture() else artwork, fullPicture = fullPicture,
                    typeLine = if (hidden) "" else f?.typeLine.orEmpty(), faceId = card.faceId,
                    changedPowerToughness = if (autoCorner != null || handFramed != null) false
                        else if (faceDown) changedFaceDownPowerToughness(card.corner) else !hidden && changedPowerToughness(card.corner, f),
                    cornerColor = if (autoCorner != null) auto?.let { computedCornerColor(it.trend) }
                        else handFramed?.takeIf { it.corner != null }?.let { computedCornerColor(it.trend) },
                    cornerHourglass = autoCorner != null && auto?.untilEndOfTurn == true,
                    crew = auto?.crew?.let { "Crew $it" }, automatumMarks = auto?.marks.orEmpty(), automatumState = auto?.states?.isNotEmpty() == true,
                    counterBubbles = auto?.bubbles.orEmpty(), effectsMarker = auto != null && card.id in computed.withEffects,
                    cardTypes = (auto?.cardTypes ?: framed?.cardTypes).orEmpty(), subtypeTags = auto?.tags.orEmpty(),
                    frontTypeLine = if (hidden) "" else definitions[card.definition]?.faces?.firstOrNull()?.typeLine.orEmpty(),
                    enteredThisTurn = entered, coveredTitle = coveredTitle, stackAbility = ability,
                    stackCopy = zone == PlayZone.STACK && projection && !hidden && CombatStackRules.copyEntry(play.board, viewId)).also { if (draw) items += it }
        }
        // Opponent block on top, own block at the bottom; in landscape (TBL-009) the opponent's in the top bar, yours in the
        // bottom bar, left of the controls
        // WEB-016: on the desktop table each seat box's life square and piles lie in its lower part, the avatar and name over
        // them: the other player's box at the top left (owner notes 2026-11-16), yours at the bottom left (owner playtest
        // 2026-11-15)
        val dockRowY = if (desktop != null) listOf(DesktopTable.FLOAT - DesktopTable.SEAT, floorY - DesktopTable.SEAT)
            else if (landscape) listOf(0f, height - controlsHeight) else listOf(HEADER, ownDockY)
        val dockRowHeights = if (desktop != null) listOf(DesktopTable.SEAT, DesktopTable.SEAT)
            else if (landscape) listOf(HEADER, controlsHeight) else listOf(dockRow, ownDockRow)
        for ((index, player) in players.withIndex()) {
            val ownSide = player.accountId == viewer
            // TRN-069: the player's dungeon in the command zone takes a fourth place after the piles; TRN-053, TRN-072, TRN-069
            // (owner gallery review 2026-11-10): the room of the player's dungeon, what the rules keep for the player and their
            // emblems, as round chips in one row
            val seatRow = seatRows.getValue(player.accountId)
            val dungeon = seatRow.dungeon
            val places = seatRow.places
            val emblemTexts = seatRow.emblemTexts
            val stateChips = seatRow.stateChips
            val stateRow = seatRow.stateRow
            val emblems = seatRow.emblems
            val hand = container(player.accountId, PlayZone.HAND).let { hand ->
                // UI review round 2 #9 (decided for the owner): with Automatum on a spell cast from a hand leaves it at once:
                // its card isn't drawn in the hand while its entry is on the Stack (the card stays there, STK-001)
                hand.copy(cards = hand.cards.filter { it !in castFromHand })
            }
            // ZON-001 (owner, 2026-11-11): in portrait the other player's hand is a fan in their row, placed with it below;
            // WEB-016 (owner, desktop playtest 2026-11-14: "Opponent hand should be a fan like in mobile?") on the desktop table
            // the same fan in their seat header, after their piles
            val fan = !ownSide && (!landscape || desktop != null)
            val desktopFan = fan && desktop != null
            // WEB-016 (owner playtest 2026-11-15): on the desktop table your seat header floats at the bottom left, as narrow as its
            // life square, piles and chips let it be
            val plateRight = plates[player.accountId]?.right ?: 0f
            // WEB-015: on the desktop table your hand is a fan at the bottom centre, between your seat header and the action area
            val normalHandBounds = if (desktop != null) TableBounds(0f, 0f, 0f, 0f) // the fans' places are worked out below
            else if (!ownSide) {
                // TBL-009: in landscape in the top bar, after the opponent's life square, piles and emblems
                if (landscape) Landscape.rowRight(width, own = false, places, wanted = stateRow).let { left -> TableBounds(left + 10f, 2f,
                    (width - toolbarWidth - 10f - left - 10f).coerceAtLeast(handWidth), maxOf(HEADER, railHeight)) }
                else TableBounds(0f, 0f, 0f, 0f) // the fan's own place is worked out with the row's below
            } else if (landscape) {
                // TBL-009 (owner, 2026-11-08): in the bottom bar, after your life square, piles and emblems, up to the controls
                val left = Landscape.rowRight(width, own = true, places, emblems, stateRow) + 6f
                TableBounds(left, height - 4f - ownHandHeight, (width - Landscape.controls(width) - left).coerceAtLeast(handWidth), ownHandHeight)
            } else TableBounds(8f, ownDockY - railHeight, (width - 16f).coerceAtLeast(28f), railHeight)
            val handBounds = normalHandBounds
            // TBL-238: with large hand cards in landscape only the row's part in its bar is the row; its cards stand over the
            // battlefields above (the other player's below) it
            val handBand = if (!largeHands) null
                else if (ownSide) handBounds.copy(y = barTop, height = (handBounds.bottom - barTop).coerceAtLeast(0f))
                else handBounds.copy(height = (HEADER - handBounds.y).coerceAtLeast(0f))
            val handLabel = "${name(player.accountId)} Hand, ${hand.cards.size} cards"
            if (desktop != null && ownSide) {
                // WEB-015 (owner playtest 2026-11-15: "in a fan shape with hovered card rendering always over others"): your hand
                // as a fan at the bottom centre of the battlefields, between your seat header and the action area, its cards
                // partly below the window's bottom. Owner 2026-11-16: the card the mouse is on is only drawn over the others, as a
                // draft card is, not moved or turned; a raised one (ZON-079) pops out of the fan along its own turn, keeping it,
                // until it is whole in view. ZON-079: your cards from other zones at its right end, after a gap with a divider
                val extras = if (reviewing) emptyList()
                    else handRow.filter { it.cardId in byId && it.cardId !in hand.cards && it.cardId !in castFromHand }
                val ids = hand.cards + extras.map { it.cardId }
                val count = ids.size
                // Owner 2026-11-16 (the fan was cut at its left): the room an end card's turn and pop-out take beside the fan
                val edge = (handHeight * kotlin.math.sin(toRadians(DesktopTable.FAN_MAX_TURN.toDouble()))).toFloat()
                val left = plateRight + 8f + edge
                val right = (handRight - 8f - FAN_COUNT - edge).coerceAtLeast(left + handWidth)
                val gap = if (extras.isNotEmpty() && hand.cards.isNotEmpty()) handWidth * (1f - DesktopTable.FAN_PITCH) + HAND_ROW_DIVIDER else 0f
                val pitch = if (count <= 1) 0f
                    else minOf(handWidth * DesktopTable.FAN_PITCH, (right - left - handWidth - gap) / (count - 1)).coerceAtLeast(4f)
                val span = if (count == 0) 0f else (count - 1) * pitch + handWidth + gap
                val start = (left + right) / 2 - span / 2
                val shown = minOf(DesktopTable.FLOAT, handHeight)
                val restTop = floorY - shown
                val turnStep = if (count <= 1) 0f else minOf(DesktopTable.FAN_TURN, 2 * DesktopTable.FAN_MAX_TURN / (count - 1))
                fun cardX(n: Int) = start + n * pitch + if (n >= hand.cards.size) gap else 0f
                // Each card turned about a point below the fan (its middle dropped as it turns); raised, moved out along its turn
                // until its lowest corner is [DesktopTable.FAN_LIFT] above the window's bottom
                val faces = List(count) { n -> TableCardTransform(handWidth, handHeight, (n - (count - 1) / 2f) * turnStep) }
                val rests = List(count) { n ->
                    val radians = toRadians(faces[n].rotation.toDouble())
                    val drop = (DesktopTable.FAN_PIVOT * handHeight * (1 - kotlin.math.cos(radians))).toFloat()
                    TablePoint(cardX(n) + handWidth / 2, restTop + handHeight / 2 + drop)
                }
                fun raisedCentre(n: Int): TablePoint {
                    val rest = rests[n]
                    val radians = toRadians(faces[n].rotation.toDouble())
                    val lift = ((faces[n].bounds(rest).bottom - (floorY - DesktopTable.FAN_LIFT)) / kotlin.math.cos(radians).toFloat())
                        .coerceAtLeast(0f)
                    return TablePoint(rest.x + lift * kotlin.math.sin(radians).toFloat(), rest.y - lift * kotlin.math.cos(radians).toFloat())
                }
                val fanBounds = TableBounds(start, restTop, span + FAN_COUNT, shown)
                // Its port holds every card at rest and popped out, so nothing of a card is cut off
                val reach = (0 until count).flatMap { n -> listOf(faces[n].bounds(rests[n]), faces[n].bounds(raisedCentre(n))) }
                val portLeft = (reach.minOfOrNull { it.x } ?: fanBounds.x).coerceAtMost(fanBounds.x) - 2f
                val portRight = (reach.maxOfOrNull { it.right } ?: fanBounds.right).coerceAtLeast(fanBounds.right) + 2f
                val top = minOf(reach.minOfOrNull { it.y } ?: restTop, restTop) - 4f
                val hp = port(hand.id, TableBounds(portLeft, top, portRight - portLeft, floorY - top), portRight - portLeft, bar = true)
                items += TableItem(hand.id, fanBounds, handLabel, hand.cards.size.toString(),
                    player.accountId, PlayZone.HAND, hand.cards, TableItemKind.HAND, hp.id, handFan = true,
                    handDivider = if (gap > 0f) (cardX(hand.cards.size - 1) + handWidth + cardX(hand.cards.size)) / 2 else null)
                // Drawn left to right, then the raised card, then the hovered one over everything
                val order = ids.indices.sortedBy { n -> when (ids[n]) { ui.hovered -> 2; ui.raised -> 1; else -> 0 } }
                for (n in order) {
                    val id = ids[n]
                    val c = byId[id] ?: continue
                    val extra = extras.getOrNull(n - hand.cards.size)
                    val raised = !reviewing && id == ui.raised
                    val x = cardX(n)
                    val face = faces[n]
                    val b = face.bounds(if (raised) raisedCentre(n) else rests[n])
                    val slotRight = if (n + 1 < count) cardX(n + 1) else x + handWidth
                    val slot = TableBounds(x, restTop, slotRight - x, floorY - restTop)
                    val item = card(c, b, b, player.accountId, PlayZone.HAND, hp.id, known = extra != null)
                    items[items.lastIndex] = if (extra == null) item.copy(raised = raised, cardTransform = face, handSlot = slot)
                        else item.copy(id = "handRow/${extra.cardId}", raised = raised, handZone = extra.zone, owner = player.accountId,
                            label = "${item.label}, from ${HandRow.zoneWord(extra.zone).lowercase()}", cardTransform = face, handSlot = slot)
                }
            } else if (!fan) {
            // ZON-079 (owner, 2026-11-12): your cards from other zones at the right end, after a thin divider
            val extras = if (!ownSide || reviewing) emptyList()
                else handRow.filter { it.cardId in byId && it.cardId !in hand.cards && it.cardId !in castFromHand }
            val handCount = hand.cards.size + extras.size
            val dividerGap = if (extras.isNotEmpty() && hand.cards.isNotEmpty()) HAND_ROW_DIVIDER - 4f else 0f
            val handContent = if (handCount == 0) 0f else (handCount - 1) * (handWidth + 4f) + handWidth + dividerGap
            val hp = port(hand.id, handBounds.copy(width = (handBounds.width - 24f).coerceAtLeast(1f)), handContent, true)
            // ZON-081: a hand pulled past its end (Automatum on) is drawn that far off, springing back on release
            val pulled = ui.overscroll[hp.id] ?: 0f
            val start = hp.bounds.x + ((hp.bounds.width - handContent) / 2).coerceAtLeast(0f) - hp.scroll - pulled
            items += TableItem(hand.id, handBounds, handLabel, hand.cards.size.toString(),
                player.accountId, PlayZone.HAND, hand.cards, TableItemKind.HAND, hp.id,
                handDivider = if (dividerGap > 0f) start + hand.cards.size * (handWidth + 4f) - 4f + HAND_ROW_DIVIDER / 2 else null,
                handBand = handBand)
            hand.cards.forEachIndexed { n, id -> byId[id]?.let { c ->
                // ZON-079: the raised card sits a quarter of its height higher
                val raised = ownSide && !reviewing && id == ui.raised
                val b = TableBounds(start + n * (handWidth + 4f), handBounds.y + (handBounds.height - handHeight) / 2 -
                    if (raised) handHeight * RAISE else 0f, handWidth, handHeight)
                card(c, b, b, player.accountId, PlayZone.HAND, hp.id)
                if (raised) items[items.lastIndex] = items.last().copy(raised = true)
            } }
            extras.forEachIndexed { n, extra -> byId[extra.cardId]?.let { c ->
                val raised = extra.cardId == ui.raised
                val x = start + (hand.cards.size + n) * (handWidth + 4f) + dividerGap
                val b = TableBounds(x, handBounds.y + (handBounds.height - handHeight) / 2 - if (raised) handHeight * RAISE else 0f,
                    handWidth, handHeight)
                val item = card(c, b, b, player.accountId, PlayZone.HAND, hp.id, known = true)
                items[items.lastIndex] = item.copy(id = "handRow/${extra.cardId}", raised = raised, handZone = extra.zone,
                    owner = player.accountId,
                    label = "${item.label}, from ${HandRow.zoneWord(extra.zone).lowercase()}")
            } }
            }
            val field = fields[index]
            val battlefield = container(player.accountId, PlayZone.BATTLEFIELD)
            // TBL-004, TBL-053: each card where its group puts it, drawn behind to front; an attached card peeks out above
            // its host (on both sides, so its name shows), wherever the host lies
            // TBL-054: each stack's bottom card where it lies, for its count badge (TBL-054, owner 2026-11-19)
            val stackBottoms = HashMap<String, TableBounds>()
            for ((id, spot) in spots[battlefield.id].orEmpty().entries.sortedBy { it.value.order }) byId[id]?.let { c ->
                val x = field.originX + spot.x * fieldScale + 16f * fieldScale
                val rowHeight = field.rowHeight
                val dy = spot.row * rowHeight
                val rowY = if (ownSide) field.gridBounds.bottom - rowHeight - dy else field.gridBounds.y + dy
                val y = rowY + (rowHeight - envelope) / 2
                val footprint = TableBounds(x, y, columnPitch * zoom, envelope)
                val lift = (spot.lifts / 2f - spot.lift) * BattlefieldLayout.PEEK * fieldScale
                val centred = battlefieldCardBounds(footprint, if (c.tapped) ch else cw, if (c.tapped) cw else ch, lift)
                // TBL-143 (owner, 2026-11-22): every card lies on its row's bottom line, tapped or not, so all tapped cards share
                // one line: its bottom at the bottom of the square both a tapped and an untapped card fit in. TBL-177 (owner,
                // 2026-11-19): in a stack with both tapped and untapped members each lies with its bottom-left corner (a tapped
                // one's as it lies turned) at the bottom-left of that square, so the corners line up
                val square = maxOf(cw, ch)
                val bottom = footprint.center.y + square / 2 + lift - centred.height
                val b = if (!spot.aligned) centred.copy(y = bottom) else centred.copy(x = footprint.center.x - square / 2, y = bottom)
                if (spot.staged == null) stackBottoms.getOrPut(spot.group) { b }
                card(c, b, footprint, player.accountId, PlayZone.BATTLEFIELD, battlefield.id)
                items[items.lastIndex] = items.last().copy(group = spot.group, copies = maxOf(1, spot.copies), staged = spot.staged,
                    stackTapped = if (spot.copies > 1) spot.tappedCopies else 0,
                    countCorner = if (spot.copies > 1 && spot.tappedCopies > 0) stackBottoms[spot.group] else null,
                    label = if (spot.copies > 1) items.last().label + ", stack of ${spot.copies}" +
                        (if (spot.tappedCopies > 0) ", ${spot.copies - spot.tappedCopies} untapped" else "") else items.last().label)
            }
            port(battlefield.id, field.bounds, (logicalRight + 32f) * fieldScale)
            // Mirror the player blocks: counters face the outside, piles face the Stack.
            val rowY = dockRowY[index]
            val rowHeight = dockRowHeights[index]
            val spacing = dockSpacing
            // ZON-001: the other player's row holds their hand's fan after the piles
            val pilesDock = portraitDockWidth(pileWidth, 3)
            val fanRoom = if (fan) fanWidth + FAN_GAP else 0f
            val portraitDock = pilesDock + fanRoom
            val portraitCounters = (width - portraitDock).coerceAtLeast(0f)
            val shrink = portraitCounters >= MIN_SHRUNK_COUNTER_GRID
            // TBL-009: in landscape the row starts after the turn header in the top bar, or at the bottom bar's left end
            // WEB-016: on the desktop table at the seat box's left, under the avatar and name
            val dockX = if (desktop != null) DesktopTable.PLATE_PAD
                else if (!landscape) 0f else if (ownSide) Landscape.OWN_X else Landscape.opponentX(width)
            // TRN-053, TRN-072: chips always have their room in the row: where the piles don't leave it they scroll under it
            val dockWidth = if (desktop != null) DesktopTable.rowRight(tableRight, places, emblems, stateRow,
                    fan = if (desktopFan) fanWidth else 0f, start = dockX) - dockX
                else if (landscape) Landscape.rowRight(width, ownSide, places, emblems, stateRow) - dockX
                else (if (portraitCounters >= gridWidth) width - gridWidth
                else if (shrink) portraitDock
                else maxOf(width - gridWidth, minOf(portraitDock, width))).let { full ->
                    if (emblems) minOf(full, width - EmblemChips.MIN_AREA_WIDTH - 16f).coerceAtLeast(0f) else full }
            // ZON-001: the fan keeps its place at the row's end; the piles scroll left of it when there isn't room for both
            val rowRight = dockX + dockWidth
            // WEB-016: a desktop seat header's life square and piles are larger than the landscape bar row's
            fun barPilesRight(x: Float, places: Int) = if (desktop != null) DesktopTable.pilesRight(x, places) else Landscape.pilesRight(x, places)
            // WEB-016: in a desktop seat header the life square and piles, then the other player's fan, then their chips
            val dock = if (desktopFan) TableBounds(dockX, rowY, barPilesRight(dockX, places) - dockX, rowHeight)
                else TableBounds(dockX, rowY, (dockWidth - fanRoom).coerceAtLeast(0f), rowHeight)
            val dp = port("dock/${player.accountId}", dock, if (landscape) dock.width else pilesDock +
                (places - 3) * spacing, bar = landscape)
            val pilePitch = if (desktop != null) DesktopTable.PILE_PITCH else Landscape.PILE_PITCH
            val lifeSide = if (desktop != null) DesktopTable.LIFE else Landscape.LIFE
            // Each pile's left edge: in landscape centred in its own touch target after the life square
            fun pileX(n: Int) = if (landscape) barPilesRight(dockX, n) + (pilePitch - pileWidth) / 2
                else PILE_START + n * spacing - dp.scroll
            val pileY = if (landscape) rowY + (rowHeight - pileHeight) / 2 else rowY + if (ownSide) OWN_PILE_TOP else 12f
            // TRN-053: in portrait the emblem chips sit in the empty room right of the piles (and the other player's hand's
            // fan, ZON-001), clear of the hands; in landscape right after the piles in the bar row
            val pilesRight = PILE_START + (places - 1) * spacing + pileWidth - dp.scroll
            val fanX = if (desktopFan) dock.right + FAN_GAP else minOf(PILE_START + (places - 1) * spacing + pileWidth, dock.right) + FAN_GAP
            val chipsArea = if (landscape) (if (desktopFan) fanX + fanWidth + 4f else barPilesRight(dockX, places) + 4f).let { x ->
                    val room = (rowRight - x).coerceAtLeast(0f)
                    TableBounds(x, rowY + 4f, room, rowHeight - 8f) }
                else (if (fan) fanX + fanWidth + 8f else minOf(pilesRight, dock.right) + 8f).let { x ->
                    // Your trimmed row gives its chips its whole height
                    if (ownSide) TableBounds(x, rowY, width - x - 8f, rowHeight)
                    else TableBounds(x, rowY + 4f, width - x - 8f, rowHeight - 8f) }
            val life = if (landscape) TableBounds(dockX, rowY + (rowHeight - lifeSide) / 2, lifeSide, lifeSide)
                else TableBounds(10f - dp.scroll, rowY + if (ownSide) OWN_LIFE_TOP else 8f, 48f, 48f)
            if (fan) {
                // ZON-001 (owner, 2026-11-11): a small fan of their hand's cards (backs) after their piles, its count at its
                // right; the cards are the hand's own items, so holding one inspects it and cards fly from it as from the row
                val fanBounds = TableBounds(fanX, rowY + 4f, fanWidth, rowHeight - 8f)
                // Its own part of the row (white as the row), outside the piles' scrolling; WEB-016: on the desktop table
                // between the piles and the chips, on the seat header's colour
                val fanLeft = fanX - FAN_GAP
                val fanRight = if (desktopFan) fanX + fanWidth else rowRight
                val fp = port("dock/${player.accountId}/hand", TableBounds(fanLeft, rowY, fanRight - fanLeft, rowHeight),
                    fanRight - fanLeft, bar = desktopFan)
                items += TableItem(hand.id, fanBounds, handLabel, hand.cards.size.toString(),
                    player.accountId, PlayZone.HAND, hand.cards, TableItemKind.HAND, fp.id, handFan = true)
                val faces = handFan(hand.cards.size, TablePoint(fanX + (fanWidth - FAN_COUNT) / 2, pileY + pileHeight / 2),
                    fanCardWidth, fanCardHeight)
                hand.cards.forEachIndexed { n, id -> byId[id]?.let { c ->
                    val (center, turn) = faces[n]
                    val face = TableCardTransform(fanCardWidth, fanCardHeight, turn)
                    val b = face.bounds(center)
                    card(c, b, b, player.accountId, PlayZone.HAND, fp.id)
                    items[items.lastIndex] = items.last().copy(cardTransform = face)
                } }
            }
            val chips = PlayerChips.layout(stateChips, emblemTexts.size, chipsArea, fromRight = !landscape).let { row ->
                // ZON-136: the playable chip carries its list, only what the viewer may know (OffZoneCards.playableBy)
                val others = computed.othersPlayable[player.accountId].orEmpty()
                if (others.isEmpty() || player.accountId == viewer) row else row.copy(states = row.states.map { placed ->
                    if (placed.chip != OtherPlayableChip.chip(name(player.accountId), others.size)) placed else placed.copy(playable = others.map { entry ->
                        val card = entry.cardId?.let(byId::get)
                        OtherPlayableChip.Shown(entry.cardId?.let { face(card)?.name } ?: OtherPlayableChip.hiddenName(entry.zone),
                            OtherPlayableChip.zoneWord(entry.zone), if (entry.cardId == null) null else art(card, false), card?.id,
                            OtherPlayableChip.pile(entry.zone))
                    })
                })
            }
            items += TableItem("life/${player.accountId}", life,
                "${name(player.accountId)}, life ${player.life}",
                player.life, player.accountId, kind = TableItemKind.LIFE, viewport = dp.id,
                chipsArea = chipsArea, emblemTexts = emblemTexts, emblemChips = chips.emblems, stateChips = chips.states)
            /** A small pile's 48 dp touch target in landscape, around it. */
            fun touch(b: TableBounds) = if (landscape) TableBounds(b.center.x - maxOf(48f, b.width) / 2,
                b.center.y - maxOf(48f, b.height) / 2, maxOf(48f, b.width), maxOf(48f, b.height)) else null
            for ((n, c) in listOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE).map { container(player.accountId, it) }.withIndex()) {
                val zone = c.zone
                val top = byId[c.cards.firstOrNull()]
                val hidden = zone == PlayZone.LIBRARY && !c.keepTopRevealed || top == null || !CardVisibility.isVisibleFaceUp(play.board, top, viewer)
                val b = TableBounds(pileX(n), pileY, pileWidth, pileHeight)
                val title = zone.name.lowercase()
                // ZON-092: a card in it carries counters (time counters on a suspended card, CR 702.62a)
                val countersInside = zone == PlayZone.EXILE && c.cards.any { id -> byId[id]?.typedCounters?.any { it.count != 0 } == true }
                items += TableItem(c.id, b, "${name(player.accountId)} $title, ${c.cards.size} cards${if (c.keepTopRevealed) ", keep top card revealed" else ""}" +
                    if (countersInside) ", cards with counters" else "", c.cards.size.toString(),
                    top?.owner ?: player.accountId, zone, c.cards, viewport = dp.id, covered = hidden,
                    footprint = b, peekOwners = listOfNotNull(top?.owner),
                    touchBounds = touch(b),
                    token = top?.token == true,
                    keepTopRevealed = c.keepTopRevealed, countersInside = countersInside,
                    faceTitle = if (hidden || top == null) "" else face(top)?.shownName
                        ?: Faults.placeholder("TablePresentation", "pile's top card without its definition", "", mapOf("card" to top.id)),
                    corner = if (hidden) "" else top?.corner.orEmpty(),
                    typeIcon = if (hidden) "" else cardTypeIcon(face(top)?.typeLine.orEmpty()), artwork = art(top, hidden),
                    changedPowerToughness = !hidden && changedPowerToughness(top?.corner.orEmpty(), face(top)))
            }
            // The player counters' room stays, empty (I-D52: no counters by hand); in portrait right of the piles (landscape
            // has none: the bar row holds only the life square, piles and emblems)
            if (!landscape) port("counters/${player.accountId}",
                TableBounds(rowRight, rowY, (width - rowRight).coerceAtLeast(0f), rowHeight),
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
        val stackHalfHeight = stackHeight / 2 + stackPadding
        val portraitStackY = if (worldViewport.height >= 2 * stackHalfHeight)
            (height / 2).coerceIn(worldViewport.y + stackHalfHeight, worldViewport.bottom - stackHalfHeight)
            else worldViewport.center.y
        // STK-007: pinned at the battlefields' right edge, vertically mid-screen (in landscape too, TBL-009); WEB-017 (owner
        // playtest 2026-11-15: "Stack is better as a thing on the field, not in the chat pane at all"): on the desktop table
        // too, left of the chat dock while it is open, so nothing covers it
        val stackCenter = TablePoint(tableRight - stackWidth / 2 - stackPadding, portraitStackY)
        val stackRegion = TableBounds(stackCenter.x - stackWidth / 2 - stackPadding,
            stackCenter.y - stackHeight / 2 - stackPadding, stackWidth + 2 * stackPadding, stackHeight + 2 * stackPadding)
        val sb = TableBounds(stackRegion.center.x - stackWidth / 2, stackRegion.center.y - stackHeight / 2, stackWidth, stackHeight)
        val sp = port(stack.id, sb, stackWidth)
        // STK-006 (owner playtest 2026-11-15): the entries that go nowhere when they leave (abilities, triggers, copies)
        fun vanishes(entry: String) = play.board.stackEffects.firstOrNull { it.id == entry }?.item?.kind
            .let { it != null && it !is MtgStackKind.Spell }
        items += TableItem(stack.id, sb, "Stack, ${stack.cards.size} entries", stack.cards.size.toString(), zone = PlayZone.STACK,
            cards = stack.cards, viewport = sp.id, footprint = sb, peekOwners = stackGeometry.visible.mapNotNull { CombatStackRules.controller(play.board, it.viewId) },
            countBadge = TableBounds(stackRegion.x - 8.5f, stackRegion.y - 8.5f, 20f, 20f))
        for (drawing in stackGeometry.visible.asReversed()) {
            val b = drawing.bounds.copy(x = sb.x + drawing.bounds.x, y = sb.y + drawing.bounds.y)
            card(drawing.card, b, sb, null, PlayZone.STACK, sp.id, drawing.viewId, drawing.projection, drawing.frozen)
            items[items.lastIndex] = items.last().copy(id = "stackDrawing/${drawing.viewId}", cards = stack.cards,
                vanishes = vanishes(drawing.viewId))
        }
        // Animation metadata for ALL entries, including a real spell buried below the peeks.
        val stackProjections = stack.cards.mapNotNull { entryId ->
            val effect = play.board.stackEffects.firstOrNull { it.id == entryId }
            items.firstOrNull { it.cardId == entryId && it.zone == PlayZone.STACK } ?: card(CombatStackRules.inspection(play.board, entryId)
                ?: return@mapNotNull null.also {
                    Faults.report("TablePresentation", "Stack entry without a card to animate", mapOf("game" to game.id, "entry" to entryId))
                },
                TableBounds(sb.x, sb.y, stackWidth, stackHeight), sb, null, PlayZone.STACK, sp.id, entryId,
                projection = effect != null, frozen = effect?.frozen != null, draw = false).copy(id = "stackDrawing/$entryId",
                    vanishes = vanishes(entryId))
        }
        val combatY = bandY + (bandHeight - combat.height) / 2
        // CMB-007: the band on the screen (the table's whole width), and the lanes it holds in its middle
        // TBL-238: as wide as the fields, so it reaches the sides of the width they are seen in
        val band = world.screen(TableBounds(world.panBounds.x, bandY, world.panBounds.width, bandHeight), camera)
            .let { TableBounds(fieldLeft, it.y, fieldRight - fieldLeft, it.height) }
        val lanesBounds = if (combat.lanes.isEmpty()) null else world.screen(TableBounds(combatX, combatY, combat.width, combat.height), camera)
        val combatPanel = lanesBounds?.takeIf { open }
        val combatBounds = lanesBounds ?: world.screen(TableBounds(combatX, combatY, 0f, 0f), camera)
        val cp = port("combat", combatBounds.intersection(shownViewport) ?: combatBounds.copy(height = 0f), combatBounds.width)
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
            // Combat destinations come with lanes; without them the region keeps its bounds
            val lanes = lanesBounds ?: return@map region.copy(bounds = bounds).also {
                Faults.report("TablePresentation", "combat destination without lanes", mapOf("game" to game.id, "role" to region.destination.role.toString()))
            }
            val top = lanes.y
            val bottom = lanes.bottom
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
        // CMB-050 (owner playtest 2026-11-10, 2026-11-11): what the combat's damage did, kept on the band until the combat
        // ends: each target's total on its chip, and the attacking player's life gained with it on the band's edge facing their
        // battlefield (the chips' edge faces the defender's: mirrored, the defender's battlefield is the lower one)
        val dealt = if (open) play.engine?.state?.let(org.asyncttwiz.automatum.CombatFlow::dealt) else null
        val gainEdge = if (open) (if (combat.mirrored) band.y else band.bottom) else null
        val realSources = play.board.cards.associate { c -> c.id to TableMoveSource(c.containerId, positions[c.id],
            play.board.containers.any { it.id == c.containerId && it.zone == PlayZone.LIBRARY && it.cards.firstOrNull() == c.id }) }
        val sources = realSources + items.filter { it.projection && it.sourceId != null }.mapNotNull { item ->
            val cardId = item.cardId
            val source = item.sourceId?.let { realSources[it] }
            if (cardId == null || source == null) null.also {
                Faults.report("TablePresentation", "projection without its real card", mapOf("game" to game.id, "item" to item.id,
                    "card" to cardId.toString(), "source" to item.sourceId.toString()))
            } else cardId to source.copy(sourceId = item.sourceId)
        }.toMap()
        return clip(cardPanels(TableScene(items, fields, ports, cw, ch, envelope, camera, sources, drops, dividers,
             stackRegion = stackRegion, world = world, appearance = appearance, desktop = desktop != null,
            desktopPlates = plates,
            // WEB-015, WEB-016, WEB-018, WEB-021: each seat header in its player's colour (the other player's at the top left,
            // yours at the bottom left), the action area (and in timeline mode the strip) in the bars' colour, floating over the
            // battlefields
            floats = if (desktop == null) emptyList() else plates.map { (owner, plate) -> plate to seatHeaderColor(owner, viewer, reviewing) } +
                listOfNotNull(
                TableBounds(width - DesktopTable.ACTIONS, height - desktopBand, DesktopTable.ACTIONS, desktopBand) to
                    tableBarColor(play.board, viewer, reviewing),
                TableBounds(0f, floorY, width - DesktopTable.ACTIONS, height - floorY).takeIf { it.height > 0f }?.let {
                    it to tableBarColor(play.board, viewer, reviewing) }),
            // TRN-073: above the Stack
            dayNightChip = play.engine?.let { org.asyncttwiz.automatum.PlayerStates.dayOrNight(it.state) }?.let { PlayerChips.aboveStack(it,
                stackRegion) },
            combatLabels = labels, combatLaneDividers = laneDividers, combatPanel = combatPanel,
            combatSlots = combat.slots.map { it.copy(bounds = translated(it.bounds)) },
            combatDealt = dealt, combatGainEdge = gainEdge,
            combatArrowGap = combat.arrowGap?.takeIf { open }?.let(::translated),
            combatBand = band.intersection(shownViewport), combatCardWidth = combatCard * zoom, combatCardHeight = combatCard * hudHeight / hudWidth * zoom,
            stackProjections = stackProjections, cardOwners = play.board.cards.associate { it.id to it.owner } +
                items.mapNotNull { item -> item.cardId?.let { id -> item.owner?.let { id to it } } }.toMap(),
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
            // CMB-001: a combat copy leaving a closing band fades out where it was, clipped to the table, not the band
            val regions = listOfNotNull((if (i.inTransit || i.leaving) {
                if (i.zone == PlayZone.BATTLEFIELD) scene.world?.visibleViewport ?: screen else screen
            } else port)?.let(i.bounds::intersection)?.intersection(screen))
            val panelClipped = if (i.viewport.startsWith("cardPanel/") || i.inTransit) regions else
                scene.cardPanels.fold(regions) { parts, panel -> parts.flatMap { it.subtract(panel.bounds) } }
            // WEB-015: what floats over the desktop table's battlefields hides the battlefield cards under it
            val floated = if (i.zone == PlayZone.BATTLEFIELD && scene.floats.isNotEmpty())
                scene.floats.fold(panelClipped) { parts, float -> parts.flatMap { it.subtract(float.first) } } else panelClipped
            i.copy(visible = if (i.zone == PlayZone.BATTLEFIELD && scene.stackRegion != null)
                floated.flatMap { it.subtract(scene.stackRegion) } else floated,
                touchBounds = i.touchBounds?.intersection(viewport?.bounds ?: screen)?.intersection(screen))
        }
        val occlusion = TableOcclusionIndex(clipped)
        val visible = clipped.mapIndexed { n, i ->
            var portions = i.visible
            if (portions.isNotEmpty() && i.kind != TableItemKind.HAND) for (next in occlusion.candidates(n)) {
                val cover = clipped[next]
                // A leaving combat copy (CMB-001) fades out: what lies beneath it shows through
                // ZON-001: a fanned hand's turned cards overlap only at their corners: each is drawn over the one before
                if (cover.kind == TableItemKind.HAND || cover.leaving ||
                    i.cardTransform != null && i.zone == PlayZone.HAND && cover.zone == PlayZone.HAND && cover.viewport == i.viewport ||
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
    fun cardTypeIcon(type: String) = when {
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
    /** Menu toggles must not mistake a legacy default cover for an explicit public face-down edit. Null (reported) when the
     * board doesn't hold the card or its container. */
    fun cardForActions(play: TableBoard, cardId: String): PlayCard? {
        val card = play.board.cards.firstOrNull { it.id == cardId }
        val zone = card?.let { play.board.containers.firstOrNull { it.id == card.containerId }?.zone }
        if (card == null || zone == null) {
            Faults.report("TablePresentation", if (card == null) "card for actions not on the board" else "card for actions in no container",
                mapOf("game" to play.gameId, "node" to play.nodeId, "card" to cardId))
            return null
        }
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
fun castSpellSources(board: BoardState): Set<String> =
    board.stackEffects.filter { it.item?.kind == MtgStackKind.Spell }.mapTo(HashSet()) { it.sourceId }

/** CMB-001 (owner playtest 2026-11-06): whether [viewer] declares attackers on [board]: the collapsed band then shows a
 * chip for every target they may attack. */
fun combatChipStep(board: BoardState, viewer: String): Boolean =
    board.turn.accountId == viewer && board.priority?.holder == null && TurnPosition.of(board.turn) == TurnPosition.DECLARE_ATTACKERS

/** The combat phase of [board]: its target chips fade in once per combat, never again as its steps go on (CMB-001, owner
 * playtest 2026-11-16: they faded in anew at the damage). */
fun combatBandKey(board: BoardState): String = "${board.turn.number}/${board.turn.phase}"

/** TRN-069, TRN-053, TRN-072: what a player's life and pile row holds besides their life square and piles: their [dungeon]
 * (a fourth place after the piles) and their round chips (their emblems' [emblemTexts] and their [stateChips], the dungeon's
 * room among them). */
private class SeatRow(val dungeon: org.asyncttwiz.automatum.EngineProjection.ShownDungeon?, val emblemTexts: List<String>,
    val stateChips: List<org.asyncttwiz.automatum.StateChip>) {
    val places get() = if (dungeon != null) 4 else 3
    /** The room the chips would like in one row. */
    val stateRow get() = PlayerChips.wanted(stateChips.size + EmblemChips.chipCount(emblemTexts.size))
    val emblems get() = emblemTexts.isNotEmpty() || stateChips.isNotEmpty()
}
