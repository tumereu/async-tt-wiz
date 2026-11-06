package org.asyncttwiz.android

import org.asyncttwiz.protocol.PlayZone
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot

data class TableGesturePreview(val pressedId: String? = null, val scope: TableScope? = null,
    val point: TablePoint? = null, val destination: TableDestination? = null)

/** The ONLY pointer arbiter on the table, including docks/hands. Scene, preview and gesture distances
 * are dp; hosts convert raw pointer pixels with [pointFromPixels], including pan deltas/centroids. */
class TableGestureCoordinator(
    private val emit: (TableIntent) -> Unit,
    private val slopDp: Float = 8f, private val doubleMillis: Long = 400, private val holdMillis: Long = CardPeekHoldMillis,
    private val density: Float = 1f, private val doubleTapEnabled: Boolean = true,
    private val onPeekScroll: (Float) -> Unit = {}, // Incremental content scroll in dp; positive reveals lower text.
    // TBL-104: the held finger dx, dy dp from where the hold began; true when it is the pinned swipe's (no scroll then).
    private val onPeekSwipe: (Float, Float) -> Boolean = { _, _ -> false },
    private val diagnostic: (String) -> Unit = {}
) {
    init { require(density.isFinite() && density > 0f) }
    fun pointFromPixels(x: Float, y: Float) = TablePoint(x / density, y / density)
    private enum class Phase { PRESS, DRAG, SCROLL, HOLD, TRANSFORM, PAN, HAND_PAN }
    private data class Capture(val item: TableItem?, val start: TablePoint, val time: Long,
        val scope: TableScope?, val viewport: TableViewport?, var phase: Phase = Phase.PRESS,
        var point: TablePoint = start, var double: Boolean = false, val sources: Map<String, TableMoveSource> = emptyMap(),
        var destination: TableDestination? = null, var replacingLocal: Boolean = false,
        val suppressTapUi: Boolean = false, val camera: TableCamera = TableCamera(), val panRegion: String? = null,
        /** ZON-081: where a hand pan started (its scroll plus the pull past its end shown then). */
        var handStart: Float = 0f,
        /** TBL-104: where the finger was when the hold began. */
        var holdStart: TablePoint? = null)
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
    private var writable = false
    private var reviewing = false
    private var navigationCamera: TableCamera? = null // unresisted pointer position, never compounded rubber
    private data class SpringReturn(val from: TableCamera, val to: TableCamera, var start: Long? = null)
    private var spring: SpringReturn? = null
    /** ZON-081 (I-D50): a hand pulled past its end springs back like the battlefield (TBL-014). */
    private data class HandSpring(val viewport: String, val from: Float, var start: Long? = null)
    private var handSpring: HandSpring? = null
    /** The pull past its end each hand shows now (dp, by viewport id). */
    private val handPull = mutableMapOf<String, Float>()
    var preview = TableGesturePreview(); private set
    var capturedActions: TableIntent.Actions? = null; private set
    var actionItem: TableItem? = null; private set
    /** Whether the last tap (an [TableIntent.EmptyTap] or [TableIntent.Actions]) was on the hand row or a hand card. */
    var tapOnHand: Boolean = false; private set
    val busy get() = capture != null || pending != null || spring != null || handSpring != null
    // The hand row background previews nothing; its cards have their own items.
    private fun peekId(item: TableItem?) = item?.cardId ?: item?.cards?.firstOrNull()?.takeIf { !item.covered && item.kind != TableItemKind.HAND }
    val peeking get() = capture?.let { it.phase == Phase.HOLD && peekId(it.item) != null } == true
    fun replacesTap(point: TablePoint, time: Long): Boolean = pending?.let {
        it.shown && time <= it.deadline && scene.hit(point)?.id == it.capture.item?.id &&
            distance(point, it.capture.start) <= slopDp * 4
    } == true

    fun update(scene: TableScene, writable: Boolean, sceneSource: (() -> TableScene)? = null,
        reviewing: Boolean = false) {
        this.scene = scene; this.sceneSource = sceneSource; lastSample = null
        this.reviewing = reviewing
        this.writable = writable
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
    fun down(point: TablePoint, time: Long, dismissedRingCard: String? = null) {
        if (capture != null) cancel()
        tick(time)
        spring = null // a new gesture interrupts the return at its displayed position
        handSpring = null
        transforming = false; transformRegion = null
        navigationCamera = scene.world?.unresist(scene.camera) ?: scene.camera
        val item = scene.hit(point)
        val id = item?.cardId ?: if (item?.kind == TableItemKind.PILE && item.zone != PlayZone.STACK) {
            item.cards.firstOrNull() // the displayed real card, not an invented logical top in unordered bins
        } else null
        val scope = id?.let { if (item?.zone == PlayZone.STACK) TableScope(it, listOf(it)) else
            TableScope(it, listOf(it)) }
        val region = scene.panRegion(point)
        val next = Capture(item, point, time, scope, scene.viewports.firstOrNull { it.id == region },
            sources = scene.sources.filterKeys { it in scope?.cardIds.orEmpty() },
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
                    (item != null && (item.kind == TableItemKind.CARD || item.zone == PlayZone.LIBRARY || item.publicPile) &&
                        item.id == old.capture.item?.id) ||
                    (item == null && old.capture.item == null && scene.worldInput(point)) ||
                    (item == null && old.capture.item == null && region.isCardPanel && region == old.capture.panRegion)) &&
                distance(point, old.capture.start) <= slopDp * 4 && time <= old.deadline) {
                next.double = true
                next.replacingLocal = old.shown && item != null
                // The first press's stable scope wins even if sync changed between taps.
                capture = next.copy(scope = old.capture.scope, sources = old.capture.sources)
                pending = null
                recentTap = null
            } else { pending = null; if (!old.shown) single(old.capture); capture = next }
        } else capture = next
        preview = TableGesturePreview(pressedId = item?.id)
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
        handSpring?.let { s ->
            val start = s.start ?: time.also { s.start = it }
            val seconds = ((time - start).coerceAtLeast(0) / 1000f)
            if (seconds >= .65f) { handSpring = null; pull(s.viewport, 0f) }
            else pull(s.viewport, s.from * (1f + 16f * seconds) * exp(-16f * seconds))
        }
        val p = pending
        if (p != null && time >= p.deadline) { pending = null; if (!p.shown) single(p.capture) }
        val c = capture ?: return
        if (c.phase == Phase.PRESS && !(c.double && c.item == null) && time - c.time >= holdMillis) {
            if (c.double && c.item?.kind == TableItemKind.CARD) diagnostic("nearMiss:becameHold")
            c.phase = Phase.HOLD
            c.holdStart = c.point
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
            val from = c.holdStart ?: c.start
            if (peeking && changed && !onPeekSwipe(point.x - from.x, point.y - from.y) && scrollDelta != 0f) onPeekScroll(scrollDelta)
            return
        }
        if (c.phase == Phase.TRANSFORM) return
        val dx = point.x - c.start.x; val dy = point.y - c.start.y
        if (c.phase == Phase.PRESS && hypot(dx, dy) > slopDp) {
            if (c.double && c.item?.kind == TableItemKind.CARD) diagnostic("nearMiss:becameDrag")
            // Crossing slop wins over hold even when a delayed input frame arrives after its deadline.
            pending = null
            c.phase = when {
                // ZON-081 (I-D50): with Automatum on a drag on a hand that first moves sideways pans it (a card's too)
                c.viewport?.hand == true && abs(dx) > abs(dy) && !c.double -> Phase.HAND_PAN.also {
                    c.handStart = c.viewport.scroll + (handPull[c.viewport.id] ?: 0f)
                }
                c.double && c.item == null && c.panRegion == WORLD_PAN_REGION -> Phase.PAN
                // Panels pan like the table: a drag on empty panel space (or a double-tap-drag anywhere) scrolls; a
                // one-finger drag on a card drags the card.
                c.panRegion.isCardPanel && (c.double || c.item == null) -> Phase.SCROLL
                c.viewport?.maxScrollY?.let { it > 0 } == true && abs(dy) > abs(dx) && c.item == null -> Phase.SCROLL
                c.viewport?.maxScroll?.let { it > 0 } == true && abs(dx) > abs(dy) &&
                    c.item?.kind != TableItemKind.CARD -> Phase.SCROLL
                // TBL-011 (I-D52): a one-finger drag starting on empty table pans it (there is no selection rectangle)
                c.item?.kind == TableItemKind.HAND || c.item == null ->
                    if (c.panRegion == WORLD_PAN_REGION) Phase.PAN else Phase.HOLD
                // ZON-077 (Arena style): nothing is placed by hand on the battlefield: a drag that starts on a battlefield
                // card pans the table, unless the combat area is there to take it (CMB-028, CMB-034)
                c.item?.battlefieldCard == true && scene.combatDrops.isEmpty() ->
                    if (c.panRegion == WORLD_PAN_REGION) Phase.PAN else Phase.HOLD
                c.scope != null && scene.canDrag(c.item) -> Phase.DRAG
                else -> Phase.HOLD // non-draggable control consumes movement, not a release-time click
            }
        }
        when (c.phase) {
            Phase.HAND_PAN -> c.viewport?.let { port ->
                val raw = c.handStart - dx
                val scroll = raw.coerceIn(0f, port.maxScroll)
                val over = raw - scroll
                if (port.maxScroll > 0f) emit(TableIntent.Scroll(port.id, scroll))
                pull(port.id, over / (2f + abs(over) / 96f))
            }
            Phase.PAN -> navigate(c.camera.copy(x = c.camera.x - dx / c.camera.zoom, y = c.camera.y - dy / c.camera.zoom), elastic = true)
            Phase.SCROLL -> c.viewport?.let { p -> if (p.maxScrollY > 0 && (p.id.startsWith("cardPanel/") || abs(dy) > abs(dx)))
                emit(TableIntent.Scroll(p.id, (p.scrollY - dy).coerceIn(0f, p.maxScrollY), vertical = true))
                else emit(TableIntent.Scroll(p.id, (p.scroll - dx).coerceIn(0f, p.maxScroll))) }
            Phase.DRAG -> {
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
        if (reviewing && c.double && c.item != null && c.phase == Phase.PRESS) {
            if (c.replacingLocal) emit(TableIntent.DismissTapUi)
            preview = TableGesturePreview()
            return
        }
        if (c.double && c.item?.kind == TableItemKind.CARD && c.item.zone != PlayZone.STACK && c.phase == Phase.PRESS)
            diagnostic("recognized")
        if (c.replacingLocal && c.phase == Phase.PRESS) emit(TableIntent.DismissTapUi)
        when (c.phase) {
            Phase.PRESS -> if (c.double && c.item == null && scene.worldInput(c.start)) {
                scene.world?.fit()?.let(::navigate)
            } else if (c.double && c.item?.publicPile == true) {
                // Graveyard/Exile double-tap: the search panel (the single tap already opened the pile menu).
                emit(TableIntent.SearchPile(c.item.id))
            } else if (c.double && c.item?.zone == PlayZone.LIBRARY) {
                // I-D52: nothing is drawn by hand; the single tap already opened (or shook) the Library
                Unit
            } else if (c.double && c.item?.viewport == "combat") {
                // Combat has no double-tap source action or ordinary card menu.
                diagnostic("dropped:combatHasNoRotation")
            } else if (c.double && c.item?.zone == PlayZone.STACK) {
                single(c, explicit = true)
            } else if (c.double && c.item?.kind == TableItemKind.CARD && c.item.zone == PlayZone.HAND) {
                val id = c.scope?.contextId
                val item = scene.items.firstOrNull { it.cardId == id && it.zone == PlayZone.HAND }
                val destination = item?.let { handDoubleTapDestination(scene, it) }
                if (writable && id != null && destination != null) {
                    emit(TableIntent.Move(TableScope(id, listOf(id)), destination, c.sources.filterKeys { it == id }, handDoubleTap = true))
                }
            } else if (c.double && c.item?.kind == TableItemKind.CARD && c.item.zone != PlayZone.BATTLEFIELD) {
                // Non-Battlefield cards have no orientation gesture, including legacy tapped cards.
                Unit
            } else if (c.double && c.item?.kind == TableItemKind.CARD) {
                // TBL-043: Automatum taps it for mana (nothing is tapped by hand)
                if (writable && c.scope != null && targetsExist(c.scope)) {
                    emit(TableIntent.Rotate(rotatable(c.scope), !c.item.tapped, c.sources, toggle = true))
                } else diagnostic("dropped:" + when {
                    !writable -> "writable"
                    c.scope == null -> "scopeMissing"
                    else -> "targetMissing"
                })
            } else if (doubleTapEnabled && (c.item?.kind == TableItemKind.CARD ||
                c.item?.zone in listOf(PlayZone.LIBRARY, PlayZone.STACK) || c.item?.publicPile == true)) {
                // Opening personal UI never waits for double-tap recognition. The second gesture
                // dismisses that UI before drawing/rotating/entering explicit Stack actions.
                single(c)
                pending = Tap(c, time + doubleMillis, true)
                recentTap = pending; pendingCleared = null
            } else {
                single(c)
                // Empty taps remain immediate; retain only recognition metadata for navigation.
                if (doubleTapEnabled && c.item == null && (scene.worldInput(c.start) || c.panRegion.isCardPanel))
                    pending = Tap(c, time + doubleMillis, true)
            }
            Phase.DRAG -> {
                if (writable && c.scope != null && targetsExist(c.scope) && c.item?.let(scene::canDrag) == true)
                    c.destination?.let { d -> emit(TableIntent.Move(c.scope, d, c.sources, drag = true)) }
            }
            Phase.HOLD -> if (peekId(c.item) != null) emit(TableIntent.Peek(null, released = true))
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
    private fun pull(viewport: String, offset: Float) {
        if (offset == 0f) handPull.remove(viewport) else handPull[viewport] = offset
        emit(TableIntent.Overscroll(viewport, offset))
    }
    /** ZON-081: a hand pulled past its end springs back on release. */
    private fun settleHand() {
        val (viewport, from) = handPull.entries.firstOrNull { it.value != 0f }?.toPair() ?: return
        if (handSpring == null) handSpring = HandSpring(viewport, from)
    }
    private fun settleCamera() {
        settleHand()
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
        val scope = id?.let { TableScope(it, listOf(it)) }
        single(Capture(item, item.bounds.center, 0, scope, null,
            sources = scene.sources.filterKeys { it in scope?.cardIds.orEmpty() }), explicit = true)
    }
    private fun rotatable(scope: TableScope) = scope.copy(cardIds = scope.cardIds.filter { id ->
        scene.items.any { it.cardId == id && it.zone == PlayZone.BATTLEFIELD && !it.projection }
    })
    private fun targetsExist(scope: TableScope) = scope.cardIds.all { id -> scene.items.any { it.cardId == id || id in it.cards } }
    private fun single(c: Capture, explicit: Boolean = false) {
        val item = c.item
        if (item != null && scene.items.none { it.id == item.id }) return
        if (!explicit && c.suppressTapUi) {
            // Closing this card's ring is the single-tap action, but still seeds a normal double tap.
            return
        }
        tapOnHand = item?.kind == TableItemKind.HAND || item?.zone == PlayZone.HAND
        if (item == null || item.kind == TableItemKind.HAND) {
            emit(TableIntent.EmptyTap)
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
            (it.stackBadge.contains(p) || it.visible.any { bounds -> bounds.contains(p) }) }?.let {
            if (scope?.cardIds?.any { id -> scene.items.any { card -> card.cardId == id && card.zone == PlayZone.STACK } } == true) return null
            if (scope == null) return null
            // ZON-022: of library cards, only a library's own top card dragged alone goes onto the Stack (as the panels' Stack action).
            val fromLibrary = scope.cardIds.filter { id -> scene.sources[id]?.containerId.let { source ->
                scene.items.any { item -> item.id == source && item.zone == PlayZone.LIBRARY } } }
            val libraryTop = scope.cardIds.singleOrNull()?.let { id -> scene.sources[id]?.takeIf { source -> source.libraryTop &&
                scene.items.any { item -> item.id == source.containerId && item.zone == PlayZone.LIBRARY && !item.temp } } } != null
            if (fromLibrary.isNotEmpty() && !libraryTop) return null
            return TableDestination.Container(it.id, PlayZone.STACK)
        }
        if (scene.worldInput(p)) scene.combatDestination(p, scope)?.let { return TableDestination.Combat(it) }
        // CMB-007: the rest of the combat panel (its headers, its margins) takes no drop
        if (scene.worldInput(p) && scene.combatPanel?.contains(p) == true) return null
        // ZON-077: a battlefield card dropped anywhere on the battlefields stays where the layout puts it (no move)
        if (scope != null && scope.cardIds.all { id -> scene.items.any { it.cardId == id && it.battlefieldCard } } &&
            scene.worldInput(p) && scene.fields.any { it.bounds.contains(p) }) return null
        val hit = scene.items.asReversed().firstOrNull { i -> i.cardId !in (scope?.cardIds ?: emptyList()) && i.visible.any { it.contains(p) } }
        if (hit != null) {
            // A face inside a Hand is still a drop onto that Hand, not an ordinary battlefield pile.
            if (hit.kind == TableItemKind.CARD && hit.zone == PlayZone.HAND)
                return TableDestination.Container(hit.viewport, PlayZone.HAND)
            if (hit.kind == TableItemKind.PILE || hit.kind == TableItemKind.HAND)
                return hit.zone?.let { TableDestination.Container(hit.id, it) }
            if (hit.kind == TableItemKind.CARD && hit.zone == PlayZone.BATTLEFIELD && !hit.projection)
                return TableDestination.CardPile(requireNotNull(hit.cardId), scene.sources[hit.cardId])
            if (hit.kind != TableItemKind.CARD) return null
        }
        if (!scene.worldInput(p)) return null
        val f = scene.fields.firstOrNull { it.bounds.contains(p) } ?: return null
        val x = p.x - f.originX
        val y = if (f == scene.fields.last()) f.gridBounds.bottom - p.y else p.y - f.gridBounds.y
        // The battlefield feels infinite: drags may land left of column 0. Rows stay non-negative.
        return TableDestination.Grid(f.containerId, floor(x / scene.columnPitch).toInt(), floor(y / f.rowHeight).toInt().coerceAtLeast(0))
    }
    private fun distance(a: TablePoint, b: TablePoint) = hypot(a.x - b.x, a.y - b.y)
}

private val String?.isCardPanel get() = this?.startsWith("cardPanel/") == true
/** A real card on a battlefield (not a combat copy, a Stack drawing or a panel card). */
internal val TableItem.battlefieldCard get() = kind == TableItemKind.CARD && zone == PlayZone.BATTLEFIELD && !projection &&
    viewport != "combat" && !viewport.startsWith("cardPanel/")
/** A Graveyard or Exile pile control (not a card inside a panel). */
internal val TableItem.publicPile get() = kind == TableItemKind.PILE && zone in PublicPiles.zones
/** The same semantic destination and Move intent as a drag; no special mutation/animation path. */
internal fun handDoubleTapDestination(scene: TableScene, item: TableItem): TableDestination? {
    if (item.zone != PlayZone.HAND) return null
    if ("Land" !in StackContexts.types(item.frontTypeLine)) return scene.items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }
        ?.let { TableDestination.Container(it.id, PlayZone.STACK) }
    val field = scene.fields.firstOrNull { it.owner == item.owner } ?: return null
    val occupied = scene.sources.values.filter { it.containerId == field.containerId }.mapNotNull { it.placement }
        .filter { it.row == 0f }.map { it.column.toInt() }.toSet()
    val column = generateSequence(0) { it + 1 }.first { it !in occupied }
    return TableDestination.Grid(field.containerId, column, 0)
}
