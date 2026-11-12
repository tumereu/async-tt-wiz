package org.asyncttwiz.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import org.asyncttwiz.protocol.*
import kotlinx.collections.immutable.toPersistentMap
import org.asyncttwiz.appui.toRadians

enum class HistoryMode { LIVE, PLAYBACK }
/** What the timeline transport is doing, for the table's centre text and corner flash. */
enum class TimelinePlaybackStatus { PLAYING, PAUSED, REWINDING }
data class HistoryPosition(val cursorId: String, val mode: HistoryMode = HistoryMode.LIVE,
    val playing: Boolean = false, val displayVersion: String)




data class HistoryAnimatedItem(val item: TableItem, val bounds: TableBounds, val alpha: Float)

/** Upright face dimensions never depend on the rotating footprint. */
data class TableCardTransform(val width: Float, val height: Float, val rotation: Float) {
    fun bounds(center: TablePoint): TableBounds {
        val radians = toRadians(rotation.toDouble())
        val c = kotlin.math.abs(kotlin.math.cos(radians)).toFloat()
        val s = kotlin.math.abs(kotlin.math.sin(radians)).toFloat()
        val w = width * c + height * s
        val h = height * c + width * s
        return TableBounds(center.x - w / 2, center.y - h / 2, w, h)
    }
    /** A rigid rotating face can be wider than either endpoint. Keep its entire AABB in the cell. */
    fun boundsInside(center: TablePoint, footprint: TableBounds): TableBounds {
        val b = bounds(center)
        return b.copy(x = b.x.coerceIn(footprint.x, footprint.right - b.width),
            y = b.y.coerceIn(footprint.y, footprint.bottom - b.height))
    }
}

fun TableItem.faceTransform(bounds: TableBounds = this.bounds) = cardTransform ?: TableCardTransform(
    if (tapped) bounds.height else bounds.width, if (tapped) bounds.width else bounds.height,
    if (tapped) 90f else 0f)

/** Receipts, count labels, ownership and disclosure metadata are not motion targets. A dock pile's members are:
 * they have no drawing of their own, so a move between two piles changes nothing else in the scene. */
// ZON-079: a card of the hand row from another zone animates as itself, apart from its pile's or panel's drawing
val TableItem.animationId get() = if (kind == TableItemKind.CARD && handZone == null) "card/$cardId" else id
val TableScene.animationTargets get() = (items + stackProjections).associate { it.animationId to
    listOf(it.bounds, it.tapped, it.covered, it.faceId, it.cards.takeIf { _ -> it.dockPile }) }
private val TableItem.dockPile get() = kind == TableItemKind.PILE && zone in DOCK_PILE_ZONES

const val CARD_ROTATION_DURATION_MILLIS = 90
const val CARD_FLIP_DURATION_MILLIS = 350
const val CARD_FADE_DURATION_MILLIS = 350
const val LIBRARY_SHUFFLE_DURATION_MILLIS = 280
const val HAND_REFLOW_DURATION_MILLIS = 220
private val DOCK_PILE_ZONES = setOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE)

/** The scene consumer samples these bounds for drawing AND hit testing/accessibility. No mutation waypoint queue. */
class HistorySceneAnimation(initial: TableScene) {
    var cardMoveSpeedDpPerMs: Float = DEFAULT_CARD_MOVE_SPEED
    private var sceneMoveSpeed = DEFAULT_CARD_MOVE_SPEED
    private var destination = initial
    private var start = initial.items.associate { it.animationId to HistoryAnimatedItem(it, it.bounds, 1f) }
    private var end = start
    /** CMB-001 (owner playtest 2026-11-11): the battlefields where they were when the scene was retargeted and where they go,
     * by owner: they move with their cards, so the combat band between them opens and closes with the cards' motion. */
    private var startFields = initial.fields.associate { it.owner to it.gridBounds }
    private var endFields = startFields
    var durationMillis: Int = MAX_CARD_MOVE_DURATION_MILLIS; private set
    fun remainingDurationMillis(progress: Float) =
        (durationMillis * (1f - progress.coerceIn(0f, 1f))).toInt().coerceAtLeast(1)
    var rotatingCardIds: Set<String> = emptySet(); private set
    private var flippingCardIds: Set<String> = emptySet()
    private var stackFlightIds: Set<String> = emptySet()
    private var shufflingPileIds: Set<String> = emptySet()
    private var lastShuffleCursor: String? = null
    private var latestScene = initial
    private var latestItems = initial.items.associateBy { it.animationId }
    private fun rotationOnly(a: HistoryAnimatedItem, b: HistoryAnimatedItem): Boolean =
        a.item.kind == TableItemKind.CARD && a.item.faceTransform(a.bounds).rotation != b.item.faceTransform(b.bounds).rotation &&
            a.item.viewport == b.item.viewport && (a.item.footprint == b.item.footprint ||
            a.bounds.center == b.bounds.center || a.item.zone == PlayZone.HAND && b.item.zone == PlayZone.HAND &&
            a.item.footprint.x == b.item.footprint.x && a.item.footprint.center.y == b.item.footprint.center.y) &&
            a.item.faceTransform(a.bounds).let { from -> b.item.faceTransform(b.bounds).let { to -> from.width == to.width && from.height == to.height } }
    private fun flipping(a: HistoryAnimatedItem, b: HistoryAnimatedItem) = b.item.cardId in flippingCardIds ||
        a.item.covered != b.item.covered && a.item.viewport.startsWith("cardPanel/") && b.item.viewport == a.item.viewport
    // Retargeted flights already carry destination metadata, but have not joined the hand yet.
    private fun handReflow(a: HistoryAnimatedItem, b: HistoryAnimatedItem) =
        a.item.kind == TableItemKind.CARD && b.item.kind == TableItemKind.CARD &&
            !a.item.inTransit &&
            a.item.zone == PlayZone.HAND && b.item.zone == PlayZone.HAND && a.item.viewport == b.item.viewport
    private fun handReflowDuration() = kotlin.math.round(HAND_REFLOW_DURATION_MILLIS * DEFAULT_CARD_MOVE_SPEED / sceneMoveSpeed)
        .toInt().coerceAtLeast(1)
    private fun moveDuration(a: HistoryAnimatedItem, b: HistoryAnimatedItem): Int = when {
        a.bounds == b.bounds -> 0
        rotationOnly(a, b) -> CARD_ROTATION_DURATION_MILLIS
        handReflow(a, b) -> handReflowDuration()
        b.item.kind != TableItemKind.CARD -> MAX_CARD_MOVE_DURATION_MILLIS
        a.bounds.center == b.bounds.center -> MAX_CARD_MOVE_DURATION_MILLIS
        else -> cardMoveDurationMillis(kotlin.math.hypot(b.bounds.center.x - a.bounds.center.x,
            b.bounds.center.y - a.bounds.center.y), sceneMoveSpeed)
    }
    fun updateMetadata(target: TableScene) {
        if (target.animationTargets == destination.animationTargets) destination = target
    }
    /**
     * CMB-001, CMB-007 (owner playtest 2026-11-11): [presentation]'s battlefields at [progress]: each slides from where it was
     * to its place as its cards do (the same move curve over the same distance), so the combat band between them opens and
     * closes with them instead of jumping.
     */
    fun fields(progress: Float, presentation: TableScene = destination): List<TableField> {
        if (progress >= 1f) return presentation.fields
        val elapsed = progress.coerceIn(0f, 1f) * durationMillis
        val sceneProgress = (elapsed / MAX_CARD_MOVE_DURATION_MILLIS).coerceIn(0f, 1f)
        val viewport = presentation.world?.viewport
        return presentation.fields.map { field ->
            val to = field.gridBounds
            // Only its vertical place moves (its x and width follow the camera and the cards, which jump)
            val from = startFields[field.owner]?.let { to.copy(y = it.y) } ?: return@map field
            if (from.y == to.y) return@map field
            val t = cardMoveProgress(from, to, sceneProgress, MAX_CARD_MOVE_DURATION_MILLIS, sceneMoveSpeed)
            val grid = to.copy(y = from.y + (to.y - from.y) * t)
            field.copy(gridBounds = grid, bounds = viewport?.let { grid.intersection(it) ?: grid.copy(width = 0f, height = 0f) } ?: grid)
        }
    }
    fun frame(progress: Float, presentation: TableScene = destination): List<HistoryAnimatedItem> {
        val sceneProgress = progress.coerceIn(0f, 1f)
        val elapsed = sceneProgress * durationMillis
        fun channel(duration: Int) = (elapsed / duration).coerceIn(0f, 1f)
        if (presentation !== latestScene) {
            latestScene = presentation
            latestItems = presentation.items.associateBy { it.animationId }
        }
        return (end.keys + start.keys).map { id ->
            val a = start[id] ?: end.getValue(id).copy(alpha = 0f)
            val b = end[id] ?: start.getValue(id).copy(alpha = 0f)
            // Most of a large board is stationary during a tap. Reuse its immutable drawings
            // instead of allocating transforms and copied items for every card on every frame.
            if (a.bounds == b.bounds && a.alpha == b.alpha && a.item.tapped == b.item.tapped &&
                id !in shufflingPileIds && a.item.cardId !in flippingCardIds && a.item.covered == b.item.covered && a.item.faceTransform(a.bounds) == b.item.faceTransform(b.bounds)) {
                val shown = latestItems[id] ?: b.item
                return@map if (shown === b.item) b else HistoryAnimatedItem(shown, b.bounds, b.alpha)
            }
            val rotationProgress = channel(CARD_ROTATION_DURATION_MILLIS)
            val flipProgress = channel(CARD_FLIP_DURATION_MILLIS)
            val reflow = handReflow(a, b)
            val t = if (rotationOnly(a, b)) rotationProgress else if (reflow) {
                val t = channel(handReflowDuration())
                1f - (1f - t) * (1f - t)
            } else if (b.item.kind == TableItemKind.CARD)
                cardMoveProgress(a.bounds, b.bounds, channel(MAX_CARD_MOVE_DURATION_MILLIS), MAX_CARD_MOVE_DURATION_MILLIS, sceneMoveSpeed)
                else channel(MAX_CARD_MOVE_DURATION_MILLIS)
            fun mix(x: Float, y: Float) = x + (y - x) * t
            val flipping = flipping(a, b)
            val metadata = if (flipping && flipProgress < .5f) a.item else latestItems[id] ?: b.item
            // Real spells share the destination card's identity. While flying they still leave
            // the screen-fixed Stack, not the clipped Battlefield viewport they are entering.
            val shown = if (id in stackFlightIds && t < 1f) metadata.copy(zone = PlayZone.STACK, viewport = a.item.viewport)
                else metadata
            // A flying face cannot expose a Library top, including at the exact departure frame.
            val item = if (t < 1f && shown.kind == TableItemKind.CARD && a.bounds != b.bounds &&
                listOf(a.item, b.item).any { it.zone == PlayZone.LIBRARY && !it.viewport.startsWith("cardPanel/") })
                shown.copy(covered = true, artwork = null, value = "", corner = "",
                    typeIcon = "", label = "Covered card") else shown
            val face = if (item.kind == TableItemKind.CARD) {
                val from = a.item.faceTransform(a.bounds); val to = b.item.faceTransform(b.bounds)
                // ZON-001: a card of a fanned hand turns with the hand's reflow, as it slides
                TableCardTransform(mix(from.width, to.width), mix(from.height, to.height),
                    from.rotation + (to.rotation - from.rotation) * if (reflow) t else rotationProgress)
            } else null
            val bounds = when {
                t == 0f -> a.bounds
                t == 1f && (face == null || face.rotation == b.item.faceTransform(b.bounds).rotation) -> b.bounds
                face != null -> {
                    val center = TablePoint(mix(a.bounds.center.x, b.bounds.center.x), mix(a.bounds.center.y, b.bounds.center.y))
                    if (a.item.zone == PlayZone.BATTLEFIELD && b.item.zone == PlayZone.BATTLEFIELD &&
                        !item.projection && a.item.footprint == b.item.footprint &&
                        a.item.faceTransform(a.bounds).rotation != b.item.faceTransform(b.bounds).rotation &&
                        a.bounds.intersection(item.footprint) == a.bounds && b.bounds.intersection(item.footprint) == b.bounds &&
                        face.bounds(center).let { it.width <= item.footprint.width && it.height <= item.footprint.height })
                        face.boundsInside(center, item.footprint)
                    else face.bounds(center)
                }
                else -> TableBounds(mix(a.bounds.x, b.bounds.x), mix(a.bounds.y, b.bounds.y),
                    mix(a.bounds.width, b.bounds.width), mix(a.bounds.height, b.bounds.height))
            }
            val alphaProgress = channel(CARD_FADE_DURATION_MILLIS)
            val shuffle = channel(LIBRARY_SHUFFLE_DURATION_MILLIS)
            val shuffledBounds = if (id in shufflingPileIds) bounds.copy(x = bounds.x +
                (kotlin.math.sin(shuffle * 6f * kotlin.math.PI) * 6f * (1f - shuffle)).toFloat()) else bounds
            // CMB-001 (owner playtest 2026-11-11): a combat copy gone from the band fades out where it was, over whatever
            // moves beneath it as the band closes
            val leaving = end[id] == null && item.viewport == "combat"
            HistoryAnimatedItem(item.copy(cardTransform = face, leaving = leaving,
                flipScale = if (flipping) kotlin.math.abs(1f - 2f * flipProgress).coerceAtLeast(.02f) else 1f,
                inTransit = item.kind == TableItemKind.CARD && (t < 1f && a.bounds != b.bounds && !reflow ||
                    !reflow && rotationProgress < 1f && a.item.faceTransform(a.bounds).rotation != b.item.faceTransform(b.bounds).rotation)), shuffledBounds,
                a.alpha + (b.alpha - a.alpha) * alphaProgress)
        }.sortedBy { it.item.inTransit }
    }
    /** False means confirmation/metadata only: the caller must leave the running clock alone. */
    fun retarget(target: TableScene, currentProgress: Float, faceChanges: Set<String> = emptySet(),
        shuffles: Set<String> = emptySet(), shuffleCursor: String? = null): Boolean {
        val freshShuffle = shuffles.isNotEmpty() && shuffleCursor != null && shuffleCursor != lastShuffleCursor
        if (target.animationTargets == destination.animationTargets && !freshShuffle) {
            if (shuffleCursor != null) lastShuffleCursor = shuffleCursor
            destination = target
            return false
        }
        startFields = fields(currentProgress).associate { it.owner to it.gridBounds }
        endFields = target.fields.associate { it.owner to it.gridBounds }
        start = frame(currentProgress).filter { it.alpha > 0f }.associateBy { it.item.animationId }
        // A preference update applies to the next scene, not to an already-sized running clock.
        sceneMoveSpeed = cardMoveSpeedDpPerMs
        shufflingPileIds = if (freshShuffle) target.items.filter { it.id in shuffles && it.zone == PlayZone.LIBRARY && it.kind == TableItemKind.PILE }
            .map { it.animationId }.toSet() else emptySet()
        if (shuffleCursor != null) lastShuffleCursor = shuffleCursor
        end = target.items.associate { it.animationId to HistoryAnimatedItem(it, it.bounds, 1f) }
        flippingCardIds = faceChanges.filter { id ->
            val a = start["card/$id"]?.item; val b = end["card/$id"]?.item
            a != null && b != null && (a.covered != b.covered || a.faceId != b.faceId)
        }.toSet()
        for (projection in destination.stackProjections) if (!target.stackContains(projection.cardId) && projection.animationId !in start) {
            start = start + (projection.animationId to HistoryAnimatedItem(projection, projection.bounds, 1f))
        }
        for (projection in target.stackProjections) if (!destination.stackContains(projection.cardId) && projection.animationId !in end) {
            end = end + (projection.animationId to HistoryAnimatedItem(projection, projection.bounds, 0f))
        }
        // Hidden library instances have no individual scene item. Seed every newly drawn card
        // from its former pile, using stable instance identity (also covers multi-card draws).
        val arrivals = end.values.filter { it.item.kind == TableItemKind.CARD && it.item.animationId !in start }
        for (arrival in arrivals) {
            val item = arrival.item
            if (item.zone == org.asyncttwiz.protocol.PlayZone.STACK && item.projection &&
                !destination.stackContains(item.cardId)) {
                destination.realCardBounds(item.sourceId)?.let { bounds ->
                    start = start + (item.animationId to arrival.copy(bounds = bounds, alpha = 1f))
                }
                // STK-006, STK-045 (owner playtest 2026-11-04): a spell's card no longer drawn where it was cast from (its
                // hand) takes off as its entry; it isn't also left fading out there
                val real = "card/${item.sourceId}"
                if (real !in end && start[real]?.item?.projection == false) start = start - real
                continue
            }
            val pile = destination.items.firstOrNull { it.kind == TableItemKind.PILE && arrival.item.cardId in it.cards } ?: continue
            val bounds = pile.bounds
            start = start + (arrival.item.animationId to arrival.copy(bounds = bounds,
                item = arrival.item.copy(zone = pile.zone, viewport = pile.viewport)))
        }
        // TBL-040 (owner playtest 2026-11-04): a token made as an entry leaves the Stack (the ability or spell that made
        // it resolving) flies out of that entry to its place
        val resolving = start.values.firstOrNull { it.item.kind == TableItemKind.CARD && it.item.projection &&
            it.item.zone == org.asyncttwiz.protocol.PlayZone.STACK && !target.stackContains(it.item.cardId) && it.item.animationId !in end }
        val madeTokens = if (resolving == null) emptyList() else end.values.filter { it.item.kind == TableItemKind.CARD &&
            it.item.token && !it.item.projection && it.item.zone == org.asyncttwiz.protocol.PlayZone.BATTLEFIELD &&
            it.item.animationId !in start && destination.items.none { old -> old.kind == TableItemKind.CARD && old.cardId == it.item.cardId } }
        if (resolving != null) {
            for (token in madeTokens) start = start + (token.item.animationId to token.copy(bounds = resolving.bounds,
                item = token.item.copy(viewport = resolving.item.viewport)))
            if (madeTokens.isNotEmpty()) start = start - resolving.item.animationId
        }
        // A removed occurrence flies home independently of the real card, which stays drawn in place.
        // Membership (not the top-three drawings) distinguishes removal from a newly covered peek.
        for (departure in start.values.filter { it.item.kind == TableItemKind.CARD && it.item.projection &&
            it.item.zone == org.asyncttwiz.protocol.PlayZone.STACK && !target.stackContains(it.item.cardId) &&
            it.item.animationId !in end }) {
            val source = departure.item.sourceId
            if (destination.sources[source] != target.sources[source]) continue
            target.realCardBounds(source)?.let { bounds ->
                // It lands at the real card's size (a landscape pile is smaller than a card, TBL-009), not the face it
                // had mid-flight
                end = end + (departure.item.animationId to departure.copy(bounds = bounds, alpha = 0f,
                    item = departure.item.copy(cardTransform = null)))
            }
        }
        // Hidden pile arrivals have no individual destination drawing; fly back to the pile.
        for (departure in start.values.filter { it.item.kind == TableItemKind.CARD && !it.item.projection && it.item.animationId !in end }) {
            target.realCardBounds(departure.item.cardId)?.let { bounds ->
                val pile = target.items.firstOrNull { it.kind == TableItemKind.PILE && departure.item.cardId in it.cards }
                end = end + (departure.item.animationId to departure.copy(bounds = bounds, alpha = 0f,
                    item = departure.item.copy(zone = pile?.zone ?: departure.item.zone,
                        viewport = pile?.viewport ?: departure.item.viewport)))
            }
        }
        // Dock pile members (Library, Graveyard, Exile, Temp piles) have no drawing of their own, so a card moving
        // from one pile to another flies from pile to pile: with the face its pile shows (Graveyard/Exile), else
        // face down. A Library endpoint keeps the flight covered (see frame), exactly like draws.
        val formerPiles = destination.pileMembers()
        for ((cardId, to) in target.pileMembers()) {
            val from = formerPiles[cardId] ?: continue
            val key = "card/$cardId"
            if (from.id == to.id || key in start || key in end) continue
            fun drawing(scene: TableScene, pile: TableItem) = scene.pileCards[cardId]?.copy(bounds = pile.bounds,
                footprint = pile.bounds, viewport = pile.viewport) ?: TableItem(key, pile.bounds, "Covered card", "",
                pile.owner, pile.zone, kind = TableItemKind.CARD, viewport = pile.viewport, cardId = cardId,
                covered = true, footprint = pile.bounds)
            start = start + (key to HistoryAnimatedItem(drawing(destination, from), from.bounds, 1f))
            end = end + (key to HistoryAnimatedItem(drawing(target, to), to.bounds, 0f))
        }
        val flights = stackRemovalFlights(destination, target)
        val realFlights = HashSet<String>()
        for (flight in flights) {
            val entry = flight.entry
            val departure = start[entry.animationId] ?: HistoryAnimatedItem(entry, entry.bounds, 1f)
            start = start + (entry.animationId to departure)
            if (entry.projection) {
                // The real card is already at its accepted destination; do not also fly it from
                // Hand/Battlefield while its Stack projection is resolving to that same place.
                val realId = "card/${entry.sourceId}"
                val real = end[realId]
                if (real != null && realId !in start) {
                    // STK-006 (owner playtest 2026-11-04): a spell's card that wasn't drawn while its entry was on the Stack
                    // (STK-045) flies itself from the entry's place to where it goes; the entry is gone with it
                    start = start - entry.animationId + (realId to real.copy(bounds = departure.bounds, alpha = 1f,
                        item = real.item.copy(viewport = departure.item.viewport)))
                    end = end - entry.animationId
                    realFlights += realId
                    continue
                }
                real?.let { start = start + (realId to it) }
                end = end + (entry.animationId to departure.copy(bounds = flight.destination.bounds, alpha = 0f))
            }
        }
        stackFlightIds = flights.map { it.entry.animationId }.toSet() + realFlights + madeTokens.map { it.item.animationId }
        // Rotation changes its endpoint AABB/anchor too; that alone is not a move. Any other
        // transition (including a move alongside a tap) keeps the longer scene duration.
        rotatingCardIds = end.values.mapNotNull { b ->
            val a = start[b.item.animationId] ?: return@mapNotNull null
            b.item.cardId?.takeIf { b.item.kind == TableItemKind.CARD &&
                a.item.faceTransform(a.bounds).rotation != b.item.faceTransform(b.bounds).rotation }
        }.toSet()
        durationMillis = (start.keys + end.keys).maxOfOrNull { id ->
            val a = start[id] ?: end.getValue(id).copy(alpha = 0f)
            val b = end[id] ?: start.getValue(id).copy(alpha = 0f)
            maxOf(moveDuration(a, b),
                if (a.item.faceTransform(a.bounds).rotation != b.item.faceTransform(b.bounds).rotation) CARD_ROTATION_DURATION_MILLIS else 0,
                if (flipping(a, b)) CARD_FLIP_DURATION_MILLIS else 0,
                if (a.alpha != b.alpha) CARD_FADE_DURATION_MILLIS else 0)
        }?.coerceAtLeast(if (shufflingPileIds.isEmpty()) 1 else LIBRARY_SHUFFLE_DURATION_MILLIS) ?: 1
        // CMB-001: a battlefield moving (the band opening or closing) takes as long as a card moving as far
        val fieldMoves = endFields.mapNotNull { (owner, to) -> startFields[owner]?.takeIf { it.y != to.y }?.let { from ->
            cardMoveDurationMillis(kotlin.math.abs(to.y - from.y), sceneMoveSpeed)
        } }
        durationMillis = maxOf(durationMillis, fieldMoves.maxOrNull() ?: 0)
        destination = target
        return true
    }
    fun hitTest(x: Float, y: Float, progress: Float): TableItem? = frame(progress).asReversed().firstOrNull {
        it.alpha > 0f && x >= it.bounds.x && x <= it.bounds.x + it.bounds.width &&
            y >= it.bounds.y && y <= it.bounds.y + it.bounds.height
    }?.item

    /** Card ID -> the dock pile (Library, Graveyard, Exile or Temp pile) holding it. */
    private fun TableScene.pileMembers(): Map<String, TableItem> = buildMap {
        for (item in items) if (item.dockPile) for (id in item.cards) put(id, item)
    }
    private fun TableScene.stackContains(id: String?) = items.any {
        it.zone == org.asyncttwiz.protocol.PlayZone.STACK && it.kind == TableItemKind.PILE && id in it.cards
    }
    private fun TableScene.realCardBounds(source: String?): TableBounds? {
        items.firstOrNull { it.kind == TableItemKind.CARD && !it.projection && it.cardId == source }?.let { return it.bounds }
        val pile = items.firstOrNull { (it.kind == TableItemKind.PILE || it.copies > 1) && source in it.cards } ?: return null
        return pile.bounds
    }
}


