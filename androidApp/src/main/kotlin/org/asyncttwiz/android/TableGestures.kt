package org.asyncttwiz.android

import org.asyncttwiz.protocol.PlayZone
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

data class TableGesturePreview(val pressedId: String? = null, val scope: TableScope? = null,
    val point: TablePoint? = null, val rectangle: TableBounds? = null,
    val destination: TableDestination? = null, val delta: Int? = null,
    val marking: Boolean = false, val markIndex: Int? = null)

/** The ONLY pointer arbiter on the table, including docks/hands. Scene, preview and gesture distances
 * are dp; hosts convert raw pointer pixels with [pointFromPixels], including pan deltas/centroids. */
class TableGestureCoordinator(
    private val emit: (TableIntent) -> Unit,
    private val slopDp: Float = 8f, private val doubleMillis: Long = 280, private val holdMillis: Long = CardPeekHoldMillis,
    private val density: Float = 1f, private val doubleTapEnabled: Boolean = true,
    private val singleTapIsLocal: (TableItem) -> Boolean = { it.zone != PlayZone.LIBRARY }
) {
    init { require(density.isFinite() && density > 0f) }
    private val markStepDp = 28f
    private val valueStepDp = 24f
    fun pointFromPixels(x: Float, y: Float) = TablePoint(x / density, y / density)
    private enum class Phase { PRESS, DRAG, RECTANGLE, SCROLL, HOLD, TRANSFORM, MARK, PAN }
    private data class Capture(val item: TableItem?, val start: TablePoint, val time: Long,
        val selection: TableSelection, val scope: TableScope?, val viewport: TableViewport?, var phase: Phase = Phase.PRESS,
        var point: TablePoint = start, var double: Boolean = false, val sources: Map<String, TableMoveSource> = emptyMap(),
        var destination: TableDestination? = null, val bubble: StackBubbleGeometry? = null, var replacingLocal: Boolean = false,
        val suppressTapUi: Boolean = false, val camera: TableCamera = TableCamera())
    private data class Tap(val capture: Capture, val deadline: Long, val shown: Boolean)
    private var capture: Capture? = null
    private var pending: Tap? = null
    private var scene = TableScene(emptyList(), emptyList())
    private var selection = TableSelection()
    private var writable = false
    var preview = TableGesturePreview(); private set
    var capturedActions: TableIntent.Actions? = null; private set
    var actionItem: TableItem? = null; private set
    val busy get() = capture != null || pending != null
    fun replacesTap(point: TablePoint, time: Long): Boolean = pending?.let {
        it.shown && time <= it.deadline && scene.hit(point)?.id == it.capture.item?.id &&
            distance(point, it.capture.start) <= slopDp * 2
    } == true

    fun update(scene: TableScene, selection: TableSelection, writable: Boolean) {
        this.scene = scene; this.selection = selection; this.writable = writable
        // Removed cards cannot become the card that has slid into their old bounds.
        val c = capture
        if (c?.scope != null && c.scope.cardIds.any { id -> scene.items.none { it.cardId == id || id in it.cards } }) cancel()
        val p = pending
        if (p?.capture?.item != null && scene.items.none { it.id == p.capture.item.id }) pending = null
    }
    private fun order() = scene.items.filter { it.kind == TableItemKind.CARD }.mapNotNull { it.cardId }
    fun down(point: TablePoint, time: Long, dismissedRingCard: String? = null) {
        if (capture != null) cancel()
        tick(time)
        val item = scene.hit(point)
        if (scene.stackBubbles != null && item?.id?.startsWith("stackBubble/") != true)
            emit(TableIntent.StackBubbles(null))
        val id = item?.cardId ?: if (item?.kind == TableItemKind.PILE && item.zone != PlayZone.STACK) {
            item.cards.firstOrNull() // the displayed real card, not an invented logical top in unordered bins
        } else null
        val scope = id?.let { selection.scope(it, order()) }
        val next = Capture(item, point, time, selection, scope, scene.viewport(point),
            sources = scene.sources.filterKeys { it in scope?.cardIds.orEmpty() }, bubble = scene.stackBubbles,
            phase = if (item?.id == "stackBubble/mark") Phase.MARK else Phase.PRESS,
            suppressTapUi = dismissedRingCard != null && item?.cardId == dismissedRingCard, camera = scene.camera)
        val old = pending
        if (old != null) {
            if (((item != null && (item.kind == TableItemKind.CARD || item.zone == PlayZone.LIBRARY) && item.id == old.capture.item?.id) ||
                    (item == null && old.capture.item == null && scene.worldInput(point))) &&
                distance(point, old.capture.start) <= slopDp * 2 && time <= old.deadline) {
                next.double = true
                next.replacingLocal = old.shown && item != null
                // The first press's stable scope wins even if sync/selection changed between taps.
                capture = next.copy(scope = old.capture.scope, selection = old.capture.selection, sources = old.capture.sources)
                pending = null
            } else { pending = null; if (!old.shown) single(old.capture); capture = next }
        } else capture = next
        preview = TableGesturePreview(pressedId = item?.id)
        if (next.phase == Phase.MARK) preview = preview.copy(marking = true, markIndex = next.bubble?.selected)
    }
    fun tick(time: Long) {
        val p = pending
        if (p != null && time >= p.deadline) { pending = null; if (!p.shown) single(p.capture) }
        val c = capture ?: return
        if (c.phase == Phase.PRESS && !(c.double && c.item == null) && time - c.time >= holdMillis) {
            c.phase = Phase.HOLD
            // A pile control is not an individual card and cannot implicitly View a Library.
            c.item?.cardId?.let { emit(TableIntent.Peek(it)) }
            preview = TableGesturePreview()
        }
    }
    fun move(point: TablePoint, time: Long) {
        val c = capture ?: return
        val changed = c.point != point
        c.point = point
        if (c.phase == Phase.HOLD || c.phase == Phase.TRANSFORM) return
        val dx = point.x - c.start.x; val dy = point.y - c.start.y
        if (c.phase == Phase.PRESS && hypot(dx, dy) > slopDp) {
            // Crossing slop wins over hold even when a delayed input frame arrives after its deadline.
            pending = null
            c.phase = when {
                c.double && c.item == null && scene.worldInput(c.start) -> Phase.PAN
                c.viewport?.maxScroll?.let { it > 0 } == true && abs(dx) > abs(dy) &&
                    c.item?.kind != TableItemKind.CARD && !scene.worldInput(c.start) -> Phase.SCROLL
                c.item?.kind == TableItemKind.HAND || c.item == null -> Phase.RECTANGLE
                c.scope != null || c.item.kind == TableItemKind.LIFE || c.item.kind == TableItemKind.COUNTER -> Phase.DRAG
                else -> Phase.HOLD // non-draggable control consumes movement, not a release-time click
            }
        }
        when (c.phase) {
            Phase.PAN -> navigate(c.camera.copy(x = c.camera.x - dx / c.camera.zoom, y = c.camera.y - dy / c.camera.zoom))
            Phase.MARK -> {
                val bubble = c.bubble ?: return
                val index = ((bubble.selected ?: -1) + (dy / markStepDp).roundToInt()).coerceIn(-1, bubble.optionCount - 1)
                preview = TableGesturePreview(c.item?.id, point = point, marking = true, markIndex = index.takeIf { it >= 0 })
            }
            Phase.SCROLL -> c.viewport?.let { p -> emit(TableIntent.Scroll(p.id, (p.scroll - dx).coerceIn(0f, p.maxScroll))) }
            Phase.RECTANGLE -> preview = TableGesturePreview(rectangle = rectangle(c.start, point))
            Phase.DRAG -> if (c.item?.kind in listOf(TableItemKind.LIFE, TableItemKind.COUNTER)) {
                preview = TableGesturePreview(c.item?.id, point = point, delta = (-dy / valueStepDp).roundToInt())
            } else {
                if (changed) c.destination = destination(point, c.scope)
                preview = TableGesturePreview(c.item?.id, c.scope, point, destination = c.destination)
            }
            else -> tick(time)
        }
    }
    fun up(point: TablePoint, time: Long) {
        val c = capture ?: return
        move(point, time)
        capture = null
        if (c.replacingLocal && c.phase == Phase.PRESS) emit(TableIntent.DismissTapUi)
        when (c.phase) {
            Phase.PRESS -> if (c.double && c.item == null && scene.worldInput(c.start)) {
                scene.world?.fit()?.let(::navigate)
            } else if (c.double && c.item?.zone == PlayZone.LIBRARY) {
                val hand = c.item.drawHandId
                val top = c.scope?.contextId
                if (writable && hand != null && top != null) emit(TableIntent.Draw(c.item.id, hand, top))
            } else if (c.double && c.item?.kind == TableItemKind.CARD) {
                if (c.item.zone == PlayZone.STACK) single(c, explicit = true)
                else if (writable && c.scope != null && targetsExist(c.scope)) {
                    emit(TableIntent.Selection(c.selection.orientation(c.scope.contextId)))
                    emit(TableIntent.Rotate(rotatable(c.scope), !c.item.tapped, c.sources))
                }
            } else if (doubleTapEnabled && ((c.selection.mode == TableMode.NORMAL && c.item?.kind == TableItemKind.CARD) ||
                (c.selection.mode != TableMode.PING && c.item?.zone == PlayZone.LIBRARY))) {
                // Shared single-tap routes (Library View, link picking) retain the decision window.
                val local = singleTapIsLocal(requireNotNull(c.item))
                if (local) single(c)
                pending = Tap(c, time + doubleMillis, local)
            } else {
                single(c)
                // Empty taps remain immediate; retain only recognition metadata for navigation.
                if (doubleTapEnabled && c.item == null && scene.worldInput(c.start)) pending = Tap(c, time + doubleMillis, true)
            }
            Phase.MARK -> if (writable && c.bubble != null && preview.markIndex != c.bubble.selected &&
                c.bubble.entryId in scene.items.flatMap { it.cards + listOfNotNull(it.cardId) }) {
                emit(TableIntent.Submit(org.asyncttwiz.protocol.PlayAction.Stack(
                    org.asyncttwiz.protocol.StackAction.MarkText(c.bubble.entryId, preview.markIndex)), null))
            }
            Phase.DRAG -> {
                if (writable && c.item?.kind in listOf(TableItemKind.LIFE, TableItemKind.COUNTER)) {
                    val bound = requireNotNull(c.item)
                    val delta = (-((point.y - c.start.y) / valueStepDp)).roundToInt()
                    // A counter row can disappear during sync. The captured semantic field still exists.
                    if (delta != 0 && (bound.value.isEmpty() || Regex("[+-]?[0-9]+").matches(bound.value)))
                        emit(TableIntent.PlayerDelta(requireNotNull(bound.owner), bound.counter, delta))
                } else if (writable && c.scope != null && targetsExist(c.scope)) {
                    c.destination?.let { d ->
                        emit(TableIntent.Selection(c.selection.afterMove(c.scope.contextId)))
                        emit(TableIntent.Move(c.scope, d, c.sources))
                    }
                }
            }
            Phase.RECTANGLE -> emit(TableIntent.Selection(c.selection.copy(ids = scene.rectangle(rectangle(c.start, point)))))
            Phase.HOLD -> if (c.item?.cardId != null) emit(TableIntent.Peek(null))
            else -> Unit
        }
        preview = TableGesturePreview()
    }
    /** A second pointer cancels the single-pointer operation; lifting one never resumes a drag. */
    fun transform(pan: TablePoint, scale: Float, centroid: TablePoint) {
        if (!scale.isFinite() || scale <= 0f) return
        pending = null
        if (capture?.phase == Phase.HOLD) emit(TableIntent.Peek(null))
        capture?.phase = Phase.TRANSFORM
        preview = TableGesturePreview()
        val hand = scene.viewports.firstOrNull { it.hand && it.bounds.contains(centroid) }
        if (hand != null && scale in .98f..1.02f) {
            val next = (hand.scroll - pan.x).coerceIn(0f, hand.maxScroll)
            scene = scene.copy(viewports = scene.viewports.map { if (it.id == hand.id) it.copy(scroll = next) else it })
            emit(TableIntent.Scroll(hand.id, next))
            return
        }
        val sidebar = scene.viewports.firstOrNull { it.id == "sidebar" && it.bounds.contains(centroid) }
        if (sidebar != null && sidebar.maxScrollY > 0 && scale in .98f..1.02f) {
            val next = (sidebar.scrollY - pan.y).coerceIn(0f, sidebar.maxScrollY)
            scene = scene.copy(viewports = scene.viewports.map { if (it.id == sidebar.id) it.copy(scrollY = next) else it })
            emit(TableIntent.Scroll(sidebar.id, next, vertical = true))
            return
        }
        val old = scene.camera
        if (!scene.worldInput(centroid)) return
        val zoom = (old.zoom * scale).coerceIn(TablePresentation.MIN_ZOOM, TablePresentation.MAX_ZOOM)
        val center = requireNotNull(scene.world).viewport.center
        val anchorX = centroid.x - center.x
        val anchorY = centroid.y - center.y
        // centroid is the CURRENT centroid; keep the previous centroid's world point under it.
        val next = TableCamera(old.x + (anchorX - pan.x) / old.zoom - anchorX / zoom,
            old.y + (anchorY - pan.y) / old.zoom - anchorY / zoom, zoom)
        navigate(next)
    }
    private fun navigate(camera: TableCamera) {
        val next = scene.world?.clamp(camera) ?: camera
        scene = scene.copy(camera = next) // coalesce rapid pointer frames without losing personal navigation deltas
        emit(TableIntent.Camera(next))
    }
    fun cancel() {
        if (capture?.phase == Phase.HOLD) emit(TableIntent.Peek(null))
        capture = null; pending = null; preview = TableGesturePreview()
    }
    fun actions(item: TableItem) {
        val id = item.cardId ?: item.cards.firstOrNull().takeIf { item.zone == PlayZone.LIBRARY }
        val scope = id?.let { selection.scope(it, order()) }
        single(Capture(item, item.bounds.center, 0, selection, scope, null,
            sources = scene.sources.filterKeys { it in scope?.cardIds.orEmpty() }), explicit = true)
    }
    fun inspect(item: TableItem) { item.cardId?.let { emit(TableIntent.Inspect(it)) } }
    fun rotate(item: TableItem, tapped: Boolean) {
        if (item.zone == PlayZone.STACK) return
        val id = item.cardId ?: return
        val scope = rotatable(selection.scope(id, order()))
        if (writable) emit(TableIntent.Rotate(scope, tapped, scene.sources.filterKeys { it in scope.cardIds }))
    }
    fun accessibleMove(item: TableItem, destination: TableDestination) {
        val id = item.cardId ?: return
        val scope = selection.scope(id, order())
        if (writable && targetsExist(scope)) emit(TableIntent.Move(scope, destination, scene.sources.filterKeys { it in scope.cardIds }))
    }
    fun select(item: TableItem) { item.cardId?.let { emit(TableIntent.Selection(selection.toggle(it))) } }
    private fun rotatable(scope: TableScope) = scope.copy(cardIds = scope.cardIds.filter { id ->
        scene.items.none { it.cardId == id && it.zone == PlayZone.STACK }
    })
    private fun targetsExist(scope: TableScope) = scope.cardIds.all { id -> scene.items.any { it.cardId == id || id in it.cards } }
    private fun single(c: Capture, explicit: Boolean = false) {
        val item = c.item
        if (item?.id?.startsWith("stackBubble/") == true) {
            if (writable && c.bubble != null) when (item.id) {
                "stackBubble/battlefield" -> emit(TableIntent.StackDestination(c.bubble.entryId, PlayZone.BATTLEFIELD))
                "stackBubble/graveyard" -> emit(TableIntent.StackDestination(c.bubble.entryId, PlayZone.GRAVEYARD))
            }
            return
        }
        if (item != null && scene.items.none { it.id == item.id }) return
        if (!explicit && c.selection.mode == TableMode.PING) {
            if (writable) emit(TableIntent.Ping(item?.id, c.start))
        } else if (!explicit && c.selection.mode == TableMode.SELECT && item?.cardId != null) {
            emit(TableIntent.Selection(c.selection.toggle(item.cardId)))
        } else if (!explicit && c.suppressTapUi) {
            // Closing this card's ring is the single-tap action, but still seeds a normal double tap.
            return
        } else if (!explicit && item?.kind == TableItemKind.CARD && item.zone == PlayZone.STACK) {
            emit(TableIntent.StackBubbles(item.cardId))
        } else if (item == null || item.kind == TableItemKind.HAND) {
            emit(TableIntent.Selection(c.selection.copy(ids = emptySet())))
        } else if (c.scope == null || targetsExist(c.scope)) {
            val event = TableIntent.Actions(item.id, if (item.kind == TableItemKind.CARD) c.scope else null)
            // Private binding for the Actions host. The personal event need not disclose Library order/IDs.
             capturedActions = event.copy(sources = c.sources,
                 topId = if (item.zone == PlayZone.LIBRARY) c.scope?.contextId else null)
             actionItem = item
            emit(event)
        }
    }
    private fun destination(p: TablePoint, scope: TableScope?): TableDestination? {
        scene.items.firstOrNull { it.kind == TableItemKind.HAND && it.bounds.contains(p) }?.let { hand ->
            val cards = scene.items.filter { it.kind == TableItemKind.CARD && it.zone == PlayZone.HAND && it.viewport == hand.id && it.cardId !in scope?.cardIds.orEmpty() }
                .sortedBy { it.bounds.x }
            val index = cards.indexOfFirst { p.x < it.bounds.center.x }.let { if (it < 0) cards.size else it }
            return TableDestination.Container(hand.id, PlayZone.HAND, handGap = org.asyncttwiz.protocol.IdentityGap(
                cards.getOrNull(index - 1)?.cardId, cards.getOrNull(index)?.cardId))
        }
        // Stack drawings are faces of the container, not battlefield pile targets. Resolve the
        // entire visible pile before its top card (and independently of the count badge routing).
        scene.items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE &&
            it.visible.any { bounds -> bounds.contains(p) } }?.let {
            if (scope == null || scope.cardIds.any { id -> scene.sources[id]?.containerId.let { source ->
                    scene.items.any { item -> item.id == source && item.zone == PlayZone.LIBRARY } } }) return null
            return TableDestination.Container(it.id, PlayZone.STACK)
        }
        val hit = scene.items.asReversed().firstOrNull { i -> i.cardId !in (scope?.cardIds ?: emptyList()) && i.visible.any { it.contains(p) } }
        if (hit == null || hit.zone == PlayZone.BATTLEFIELD) {
            if (scene.worldInput(p)) scene.combatDrops.asReversed().firstOrNull { it.bounds.contains(p) }?.let { return TableDestination.Combat(it.destination) }
        }
        if (hit != null) {
            // A face inside a Hand is still a drop onto that Hand, not an ordinary battlefield pile.
            if (hit.kind == TableItemKind.CARD && hit.zone == PlayZone.HAND)
                return TableDestination.Container(hit.viewport, PlayZone.HAND)
            if (hit.kind == TableItemKind.PILE || hit.kind == TableItemKind.HAND)
                return hit.zone?.let { TableDestination.Container(hit.id, it) }
            if (hit.kind == TableItemKind.CARD && hit.zone == PlayZone.BATTLEFIELD && !hit.projection) {
                val members = scene.items.filter { it.viewport == hit.viewport && it.footprint == hit.footprint }.mapNotNull { it.cardId }
                if ((members + scope?.cardIds.orEmpty()).distinct().size > 3) return null
                return TableDestination.CardPile(requireNotNull(hit.cardId), scene.sources[hit.cardId])
            }
            if (hit.kind != TableItemKind.CARD) return null
        }
        if (!scene.worldInput(p)) return null
        val f = scene.fields.firstOrNull { it.bounds.contains(p) } ?: return null
        val c = scene.camera
        val x = (p.x - f.gridBounds.x) / c.zoom
        val y = if (f == scene.fields.last()) f.gridBounds.bottom - p.y else p.y - f.gridBounds.y
        return TableDestination.Grid(f.containerId, floor(x / TablePresentation.GRID).toInt().coerceAtLeast(0), floor(y / f.rowHeight).toInt().coerceAtLeast(0))
    }
    private fun distance(a: TablePoint, b: TablePoint) = hypot(a.x - b.x, a.y - b.y)
    private fun rectangle(a: TablePoint, b: TablePoint) = TableBounds(minOf(a.x, b.x), minOf(a.y, b.y), abs(a.x - b.x), abs(a.y - b.y))
}
