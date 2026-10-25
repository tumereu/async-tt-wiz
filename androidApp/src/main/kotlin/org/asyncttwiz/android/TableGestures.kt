package org.asyncttwiz.android

import org.asyncttwiz.protocol.PlayZone
import kotlin.math.abs
import kotlin.math.exp
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
    private val slopDp: Float = 8f, private val doubleMillis: Long = 400, private val holdMillis: Long = CardPeekHoldMillis,
    private val density: Float = 1f, private val doubleTapEnabled: Boolean = true,
    private val onPeekScroll: (Float) -> Unit = {}, // Incremental content scroll in dp; positive reveals lower text.
    private val diagnostic: (String) -> Unit = {}
) {
    init { require(density.isFinite() && density > 0f) }
    private val markStepDp = 28f
    fun pointFromPixels(x: Float, y: Float) = TablePoint(x / density, y / density)
    private enum class Phase { PRESS, DRAG, RECTANGLE, SCROLL, HOLD, TRANSFORM, MARK, PAN }
    private data class Capture(val item: TableItem?, val start: TablePoint, val time: Long,
        val selection: TableSelection, val scope: TableScope?, val viewport: TableViewport?, var phase: Phase = Phase.PRESS,
        var point: TablePoint = start, var double: Boolean = false, val sources: Map<String, TableMoveSource> = emptyMap(),
        var destination: TableDestination? = null, val bubble: StackBubbleGeometry? = null, var replacingLocal: Boolean = false,
        val suppressTapUi: Boolean = false, val camera: TableCamera = TableCamera(), val panRegion: String? = null)
    private data class Tap(val capture: Capture, val deadline: Long, val shown: Boolean)
    private var capture: Capture? = null
    private var transformRegion: String? = null
    private var transforming = false
    private var pending: Tap? = null
    private var recentTap: Tap? = null // One bounded near-miss observation, even after recognition expires.
    private var pendingCleared: String? = null
    private var sceneSnapshot = TableScene(emptyList(), emptyList())
    private var sceneSource: (() -> TableScene)? = null
    private var lastSample: TableScene? = null
    private var scene: TableScene
        get() {
            val sample = sceneSource?.invoke()
            if (sample != null && sample !== lastSample) { sceneSnapshot = sample; lastSample = sample }
            return sceneSnapshot
        }
        set(value) { sceneSnapshot = value }
    private var selection = TableSelection()
    private var writable = false
    private var navigationCamera: TableCamera? = null // unresisted pointer position, never compounded rubber
    private data class SpringReturn(val from: TableCamera, val to: TableCamera, var start: Long? = null)
    private var spring: SpringReturn? = null
    var preview = TableGesturePreview(); private set
    var capturedActions: TableIntent.Actions? = null; private set
    var actionItem: TableItem? = null; private set
    val busy get() = capture != null || pending != null || spring != null
    private fun peekId(item: TableItem?) = item?.cardId ?: item?.cards?.firstOrNull()?.takeIf { !item.covered }
    val peeking get() = capture?.let { it.phase == Phase.HOLD && peekId(it.item) != null } == true
    fun replacesTap(point: TablePoint, time: Long): Boolean = pending?.let {
        it.shown && time <= it.deadline && scene.hit(point)?.id == it.capture.item?.id &&
            distance(point, it.capture.start) <= slopDp * 4
    } == true

    fun update(scene: TableScene, selection: TableSelection, writable: Boolean, sceneSource: (() -> TableScene)? = null) {
        this.scene = scene; this.sceneSource = sceneSource; lastSample = null
        this.selection = selection; this.writable = writable
        // Removed cards cannot become the card that has slid into their old bounds.
        val c = capture
        if (c?.scope != null && c.phase !in listOf(Phase.PAN, Phase.SCROLL, Phase.TRANSFORM) &&
            c.scope.cardIds.any { id -> scene.items.none { it.cardId == id || id in it.cards } }) {
            if (c.double) diagnostic("nearMiss:targetRemoved")
            if (c.phase == Phase.HOLD) emit(TableIntent.Peek(null))
            // Invalidate the card operation, not the first-down region. A later second pointer
            // must not acquire the region underneath its new centroid after a remote removal.
            c.phase = Phase.TRANSFORM
            pendingCleared = "targetRemoved"; pending = null; preview = TableGesturePreview()
        }
        val p = pending
        if (p?.capture?.item != null && scene.items.none { it.id == p.capture.item.id }) {
            pendingCleared = "targetRemoved"; pending = null
        }
    }
    private fun order() = scene.items.filter { it.kind == TableItemKind.CARD }.mapNotNull { it.cardId }
    fun down(point: TablePoint, time: Long, dismissedRingCard: String? = null) {
        if (capture != null) cancel()
        tick(time)
        spring = null // a new gesture interrupts the return at its displayed position
        transforming = false; transformRegion = null
        navigationCamera = scene.world?.unresist(scene.camera) ?: scene.camera
        val item = scene.hit(point)
        if (scene.stackBubbles != null && item?.id?.startsWith("stackBubble/") != true)
            emit(TableIntent.StackBubbles(null))
        if (scene.combatBubbles != null && item?.id?.startsWith("combatBubble/") != true)
            emit(TableIntent.CombatBubbles(null))
        val id = item?.cardId ?: if (item?.kind == TableItemKind.PILE && item.zone != PlayZone.STACK) {
            item.cards.firstOrNull() // the displayed real card, not an invented logical top in unordered bins
        } else null
        val scope = id?.let { if (item?.zone == PlayZone.STACK) TableScope(it, listOf(it)) else
            selection.scope(it, order()).let { scope -> scope.copy(cardIds = scope.cardIds.filter { selected ->
                scene.items.none { it.cardId == selected && it.zone == PlayZone.STACK }
            }) } }
        val region = scene.panRegion(point)
        val next = Capture(item, point, time, selection, scope, scene.viewports.firstOrNull { it.id == region },
            sources = scene.sources.filterKeys { it in scope?.cardIds.orEmpty() },
            bubble = if (item?.id?.startsWith("combatBubble/") == true) scene.combatBubbles else scene.stackBubbles,
            phase = if (item?.id == "stackBubble/mark") Phase.MARK else Phase.PRESS,
            suppressTapUi = dismissedRingCard != null && (item?.cardId == dismissedRingCard || item?.id == dismissedRingCard),
            camera = navigationCamera ?: scene.camera, panRegion = region)
        val old = pending
        val recent = recentTap
        if (recent?.capture?.item?.kind == TableItemKind.CARD && item?.id == recent.capture.item.id &&
            time - (recent.deadline - doubleMillis) in 0..600 &&
            (old == null || distance(point, recent.capture.start) > slopDp * 4 || time > recent.deadline)) {
            diagnostic("nearMiss:" + when {
                distance(point, recent.capture.start) > slopDp * 4 -> "distance"
                time > recent.deadline -> "deadline"
                else -> pendingCleared ?: "pendingCleared"
            })
        }
        if (old != null) {
            if (((item?.zone == PlayZone.STACK && old.capture.item?.zone == PlayZone.STACK) ||
                    (item != null && (item.kind == TableItemKind.CARD || item.zone == PlayZone.LIBRARY) && item.id == old.capture.item?.id) ||
                    (item == null && old.capture.item == null && scene.worldInput(point))) &&
                distance(point, old.capture.start) <= slopDp * 4 && time <= old.deadline) {
                next.double = true
                next.replacingLocal = old.shown && item != null
                // The first press's stable scope wins even if sync/selection changed between taps.
                capture = next.copy(scope = old.capture.scope, selection = old.capture.selection, sources = old.capture.sources)
                pending = null
                recentTap = null
            } else { pending = null; if (!old.shown) single(old.capture); capture = next }
        } else capture = next
        preview = TableGesturePreview(pressedId = item?.id)
        if (next.phase == Phase.MARK) preview = preview.copy(marking = true, markIndex = next.bubble?.selected)
    }
    fun tick(time: Long) {
        spring?.let { s ->
            val start = s.start ?: time.also { s.start = it }
            val seconds = ((time - start).coerceAtLeast(0) / 1000f)
            // Critically damped spring, zero release velocity. No overshoot past the opposite edge.
            val remaining = (1f + 16f * seconds) * exp(-16f * seconds)
            if (seconds >= .65f) { spring = null; publishCamera(s.to) }
            else publishCamera(s.to.copy(x = s.to.x + (s.from.x - s.to.x) * remaining,
                y = s.to.y + (s.from.y - s.to.y) * remaining))
        }
        val p = pending
        if (p != null && time >= p.deadline) { pending = null; if (!p.shown) single(p.capture) }
        val c = capture ?: return
        if (c.phase == Phase.PRESS && !(c.double && c.item == null) && time - c.time >= holdMillis) {
            if (c.double && c.item?.kind == TableItemKind.CARD) diagnostic("nearMiss:becameHold")
            c.phase = Phase.HOLD
            // Covered pile controls never implicitly View a Library.
            peekId(c.item)?.let { emit(TableIntent.Peek(it)) }
            preview = TableGesturePreview()
        }
    }
    fun move(point: TablePoint, time: Long) {
        val c = capture ?: return
        val changed = c.point != point
        val scrollDelta = c.point.y - point.y
        c.point = point
        if (c.phase == Phase.HOLD) {
            if (peeking && scrollDelta != 0f) onPeekScroll(scrollDelta)
            return
        }
        if (c.phase == Phase.TRANSFORM) return
        val dx = point.x - c.start.x; val dy = point.y - c.start.y
        if (c.phase == Phase.PRESS && hypot(dx, dy) > slopDp) {
            if (c.double && c.item?.kind == TableItemKind.CARD) diagnostic("nearMiss:becameDrag")
            // Crossing slop wins over hold even when a delayed input frame arrives after its deadline.
            pending = null
            c.phase = when {
                c.double && c.item == null && c.panRegion == WORLD_PAN_REGION -> Phase.PAN
                c.viewport?.id?.startsWith("cardPanel/") == true && abs(dy) > abs(dx) &&
                    c.item == null -> Phase.SCROLL
                c.item?.kind in listOf(TableItemKind.LIFE, TableItemKind.COUNTER) -> Phase.DRAG
                c.viewport?.maxScrollY?.let { it > 0 } == true && abs(dy) > abs(dx) && c.item == null -> Phase.SCROLL
                c.viewport?.maxScroll?.let { it > 0 } == true && abs(dx) > abs(dy) &&
                    c.item?.kind != TableItemKind.CARD -> Phase.SCROLL
                c.item?.kind == TableItemKind.HAND || c.item == null -> Phase.RECTANGLE
                c.scope != null && scene.canDrag(c.item) || c.item.kind == TableItemKind.LIFE || c.item.kind == TableItemKind.COUNTER -> Phase.DRAG
                else -> Phase.HOLD // non-draggable control consumes movement, not a release-time click
            }
        }
        when (c.phase) {
            Phase.PAN -> navigate(c.camera.copy(x = c.camera.x - dx / c.camera.zoom, y = c.camera.y - dy / c.camera.zoom), elastic = true)
            Phase.MARK -> {
                val bubble = c.bubble ?: return
                val index = ((bubble.selected ?: -1) + (dy / markStepDp).roundToInt()).coerceIn(-1, bubble.optionCount - 1)
                preview = TableGesturePreview(c.item?.id, point = point, marking = true, markIndex = index.takeIf { it >= 0 })
            }
            Phase.SCROLL -> c.viewport?.let { p -> if (p.maxScrollY > 0 && (p.id.startsWith("cardPanel/") || abs(dy) > abs(dx)))
                emit(TableIntent.Scroll(p.id, (p.scrollY - dy).coerceIn(0f, p.maxScrollY), vertical = true))
                else emit(TableIntent.Scroll(p.id, (p.scroll - dx).coerceIn(0f, p.maxScroll))) }
            Phase.RECTANGLE -> preview = TableGesturePreview(rectangle = rectangle(c.start, point))
            Phase.DRAG -> if (c.item?.kind in listOf(TableItemKind.LIFE, TableItemKind.COUNTER)) {
                preview = TableGesturePreview(c.item?.id, point = point,
                    delta = valueDragDelta(dx, dy, side = requireNotNull(c.item).valueDragSide).toInt())
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
        transforming = false; transformRegion = null
        if (c.double && c.item?.kind == TableItemKind.CARD && c.item.zone != PlayZone.STACK && c.phase == Phase.PRESS)
            diagnostic("recognized")
        if (c.replacingLocal && c.phase == Phase.PRESS) emit(TableIntent.DismissTapUi)
        when (c.phase) {
            Phase.PRESS -> if (c.double && c.item == null && scene.worldInput(c.start)) {
                scene.world?.fit()?.let(::navigate)
            } else if (c.double && c.item?.zone == PlayZone.LIBRARY) {
                val hand = c.item.drawHandId
                val top = c.scope?.contextId
                if (writable && hand != null && top != null) emit(TableIntent.Draw(c.item.id, hand, top))
            } else if (c.double && c.item?.viewport == "combat") {
                // Combat has no double-tap source action or ordinary card menu.
                diagnostic("dropped:combatHasNoRotation")
            } else if (c.double && c.item?.zone == PlayZone.STACK) {
                single(c, explicit = true)
            } else if (c.double && c.item?.kind == TableItemKind.CARD) {
                if (writable && c.scope != null && targetsExist(c.scope)) {
                    emit(TableIntent.Selection(c.selection.afterDoubleTap(c.scope.contextId)))
                    emit(TableIntent.Rotate(rotatable(c.scope), !c.item.tapped, c.sources, toggle = true))
                } else diagnostic("dropped:" + when {
                    !writable -> "writable"
                    c.scope == null -> "scopeMissing"
                    else -> "targetMissing"
                })
            } else if (doubleTapEnabled && ((c.selection.mode != TableMode.PING && c.item?.kind == TableItemKind.CARD) ||
                (c.selection.mode != TableMode.PING && c.item?.zone in listOf(PlayZone.LIBRARY, PlayZone.STACK)))) {
                // Opening personal UI never waits for double-tap recognition. The second gesture
                // dismisses that UI before drawing/rotating/entering explicit Stack actions.
                single(c)
                pending = Tap(c, time + doubleMillis, true)
                recentTap = pending; pendingCleared = null
            } else {
                single(c)
                // Empty taps remain immediate; retain only recognition metadata for navigation.
                if (doubleTapEnabled && c.item == null && scene.worldInput(c.start)) pending = Tap(c, time + doubleMillis, true)
            }
            Phase.MARK -> if (writable && c.bubble != null && (preview.markIndex != c.bubble.selected || c.bubble.multipleMarks) &&
                c.bubble.entryId in scene.items.flatMap { it.cards + listOfNotNull(it.cardId) }) {
                emit(TableIntent.Submit(org.asyncttwiz.protocol.PlayAction.Stack(
                    org.asyncttwiz.protocol.StackAction.MarkText(c.bubble.entryId, preview.markIndex)), null))
            }
            Phase.DRAG -> {
                if (writable && c.item?.kind in listOf(TableItemKind.LIFE, TableItemKind.COUNTER)) {
                    val bound = requireNotNull(c.item)
                    val delta = valueDragDelta(point.x - c.start.x, point.y - c.start.y, side = bound.valueDragSide).toInt()
                    // A counter row can disappear during sync. The captured semantic field still exists.
                    if (delta != 0 && (bound.value.isEmpty() || Regex("[+-]?[0-9]+").matches(bound.value)))
                        emit(TableIntent.PlayerDelta(requireNotNull(bound.owner), bound.counter, delta))
                } else if (writable && c.scope != null && targetsExist(c.scope) && c.item?.let(scene::canDrag) == true) {
                    scene.panelReorder(point, c.scope)?.let {
                        emit(TableIntent.Submit(it, null)); preview = TableGesturePreview(); return
                    }
                    c.destination?.let { d ->
                        emit(TableIntent.Selection(c.selection.afterMove(c.scope.contextId)))
                        emit(TableIntent.Move(c.scope, d, c.sources))
                    }
                }
            }
            Phase.RECTANGLE -> emit(TableIntent.Selection(c.selection.copy(ids = scene.rectangle(rectangle(c.start, point)))))
            Phase.HOLD -> if (peekId(c.item) != null) emit(TableIntent.Peek(null))
            else -> Unit
        }
        settleCamera()
        preview = TableGesturePreview()
    }
    /** A second pointer cancels the single-pointer operation; lifting one never resumes a drag. */
    fun transform(pan: TablePoint, scale: Float, centroid: TablePoint) {
        if (!scale.isFinite() || scale <= 0f) return
        if (capture?.double == true && capture?.item?.kind == TableItemKind.CARD) diagnostic("nearMiss:secondPointer")
        pendingCleared = "secondPointer"; pending = null
        if (capture?.phase == Phase.HOLD) emit(TableIntent.Peek(null))
        capture?.phase = Phase.TRANSFORM
        preview = TableGesturePreview()
        if (!transforming) {
            transformRegion = if (capture != null) capture?.panRegion else scene.panRegion(centroid)
            transforming = true
        }
        val region = transformRegion
        if (region != WORLD_PAN_REGION) {
            val port = scene.viewports.firstOrNull { it.id == region } ?: return
            // Pinching a scroll viewport must never zoom the battlefield, even at its scroll limit.
            val vertical = port.maxScrollY > 0 && (port.maxScroll == 0f || abs(pan.y) > abs(pan.x))
            val next = if (vertical) (port.scrollY - pan.y).coerceIn(0f, port.maxScrollY)
                else (port.scroll - pan.x).coerceIn(0f, port.maxScroll)
            scene = scene.copy(viewports = scene.viewports.map { if (it.id != port.id) it
                else if (vertical) it.copy(scrollY = next) else it.copy(scroll = next) })
            if (port.maxScroll > 0 || port.maxScrollY > 0) emit(TableIntent.Scroll(port.id, next, vertical))
            return
        }
        val old = navigationCamera ?: scene.camera
        if (scene.world == null) return
        val zoom = (old.zoom * scale).coerceIn(TablePresentation.MIN_ZOOM, TablePresentation.MAX_ZOOM)
        val center = requireNotNull(scene.world).viewport.center
        val anchorX = centroid.x - center.x
        val anchorY = centroid.y - center.y
        // centroid is the CURRENT centroid; keep the previous centroid's world point under it.
        val next = TableCamera(old.x + (anchorX - pan.x) / old.zoom - anchorX / zoom,
            old.y + (anchorY - pan.y) / old.zoom - anchorY / zoom, zoom)
        navigate(next, elastic = true)
    }
    fun panBy(x: Float, y: Float) = navigate(scene.camera.copy(x = scene.camera.x + x, y = scene.camera.y + y))
    private fun navigate(camera: TableCamera, elastic: Boolean = false) {
        spring = null
        navigationCamera = camera.takeIf { elastic }
        val next = if (elastic) scene.world?.resist(camera) else scene.world?.clamp(camera)
        publishCamera(next ?: camera)
    }
    private fun publishCamera(camera: TableCamera) {
        scene = scene.copy(camera = camera) // coalesce rapid pointer frames without losing personal navigation deltas
        emit(TableIntent.Camera(camera))
    }
    private fun settleCamera() {
        if (navigationCamera == null) return
        navigationCamera = null
        val target = scene.world?.clamp(scene.camera) ?: return
        if (target != scene.camera) spring = SpringReturn(scene.camera, target)
    }
    fun cancel() {
        if (capture?.phase == Phase.HOLD) emit(TableIntent.Peek(null))
        if (capture?.double == true && capture?.item?.kind == TableItemKind.CARD) diagnostic("nearMiss:cancelled")
        capture = null; pendingCleared = "cancelled"; pending = null; preview = TableGesturePreview()
        transforming = false; transformRegion = null
        settleCamera()
    }
    fun actions(item: TableItem) {
        val id = item.cardId ?: item.cards.firstOrNull().takeIf { item.zone == PlayZone.LIBRARY }
        val scope = id?.let { selection.scope(it, order()) }
        single(Capture(item, item.bounds.center, 0, selection, scope, null,
            sources = scene.sources.filterKeys { it in scope?.cardIds.orEmpty() }), explicit = true)
    }
    fun inspect(item: TableItem) { peekId(item)?.let { emit(TableIntent.Inspect(it)) } }
    fun rotate(item: TableItem, tapped: Boolean) {
        if (item.zone == PlayZone.STACK) return
        val id = item.cardId ?: return
        val scope = rotatable(selection.scope(id, order()))
        if (writable) emit(TableIntent.Rotate(scope, tapped, scene.sources.filterKeys { it in scope.cardIds }))
    }
    fun accessibleMove(item: TableItem, destination: TableDestination) {
        if (!scene.canDrag(item)) return
        val id = item.cardId ?: return
        val scope = if (item.zone == PlayZone.STACK) TableScope(id, listOf(id)) else selection.scope(id, order())
        if (writable && targetsExist(scope)) emit(TableIntent.Move(scope, destination, scene.sources.filterKeys { it in scope.cardIds }))
    }
    fun select(item: TableItem) { item.cardId?.let { emit(TableIntent.Selection(selection.toggle(it))) } }
    private fun rotatable(scope: TableScope) = scope.copy(cardIds = scope.cardIds.filter { id ->
        scene.items.none { it.cardId == id && it.zone == PlayZone.STACK }
    })
    private fun targetsExist(scope: TableScope) = scope.cardIds.all { id -> scene.items.any { it.cardId == id || id in it.cards } }
    private fun single(c: Capture, explicit: Boolean = false) {
        val item = c.item
        if (item?.id?.startsWith("combatBubble/") == true) {
            if (writable) c.bubble?.actions?.get(item.id)?.let(emit)
            return
        }
        if (item?.id?.startsWith("stackBubble/") == true) {
            if (writable && c.bubble != null) when (item.id) {
                "stackBubble/battlefield" -> emit(TableIntent.StackDestination(c.bubble.entryId, PlayZone.BATTLEFIELD, fromBubble = true))
                "stackBubble/graveyard" -> emit(TableIntent.StackDestination(c.bubble.entryId, PlayZone.GRAVEYARD, fromBubble = true))
                "stackBubble/remove" -> emit(TableIntent.StackBubbleRemove(c.bubble.entryId))
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
        } else if (item?.viewport == "combat") {
            emit(TableIntent.CombatBubbles(item.cardId))
        } else if (!explicit && item?.zone == PlayZone.STACK) {
            if (item.kind == TableItemKind.CARD && item.cardId == scene.stackTopId) emit(TableIntent.StackBubbles(item.cardId))
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
        val destination = rawDestination(p, scope) ?: return null
        return scope?.let { scene.ownerMoveDestination(it.contextId, destination) } ?: destination
    }
    private fun rawDestination(p: TablePoint, scope: TableScope?): TableDestination? {
        if (scene.cardPanels.any { it.bounds.contains(p) }) return null
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
            if (scope?.cardIds?.any { id -> scene.items.any { card -> card.cardId == id && card.zone == PlayZone.STACK } } == true) return null
            if (scope == null || scope.cardIds.any { id -> scene.sources[id]?.containerId.let { source ->
                    scene.items.any { item -> item.id == source && item.zone == PlayZone.LIBRARY } } }) return null
            return TableDestination.Container(it.id, PlayZone.STACK)
        }
        if (scene.worldInput(p)) scene.combatDestination(p, scope)?.let { return TableDestination.Combat(it) }
        val hit = scene.items.asReversed().firstOrNull { i -> i.cardId !in (scope?.cardIds ?: emptyList()) && i.visible.any { it.contains(p) } }
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
        return TableDestination.Grid(f.containerId, floor(x / TablePresentation.COLUMN_PITCH).toInt().coerceAtLeast(0), floor(y / f.rowHeight).toInt().coerceAtLeast(0))
    }
    private fun distance(a: TablePoint, b: TablePoint) = hypot(a.x - b.x, a.y - b.y)
    private fun rectangle(a: TablePoint, b: TablePoint) = TableBounds(minOf(a.x, b.x), minOf(a.y, b.y), abs(a.x - b.x), abs(a.y - b.y))
}
