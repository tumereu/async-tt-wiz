package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class TableGesturesTest {

    @Test fun doubleTapDiagnosticsDistinguishGatesNearMissesAndBenignSceneUpdates() {
        val item = TableItem("card/a", TableBounds(0f, 0f, 100f, 100f), "Card", "",
            kind = TableItemKind.CARD, cardId = "a", zone = PlayZone.BATTLEFIELD)
        val scene = TableScene(listOf(item), emptyList())
        val events = mutableListOf<TableIntent>()
        val diagnostics = mutableListOf<String>()
        val g = TableGestureCoordinator(events::add, diagnostic = diagnostics::add)
        val point = item.bounds.center
        fun first(time: Long) { g.down(point, time); g.up(point, time + 10) }
        g.update(scene, true)
        first(0)
        g.update(scene.copy(items = listOf(item.copy(tapped = true))), true)
        first(100)
        assertEquals(listOf("recognized"), diagnostics)
        // TBL-043: the second tap adds nothing to the first tap's actions
        assertEquals(1, events.count { it is TableIntent.Actions }, "$events")
        diagnostics.clear(); events.clear()
        first(1000); g.update(scene, false); first(1100)
        assertEquals(listOf("recognized"), diagnostics)
        g.update(scene, true); diagnostics.clear()
        first(2000); first(2450)
        assertTrue("nearMiss:deadline" in diagnostics)
        g.cancel(); diagnostics.clear()
        first(3000); g.down(TablePoint(90f, 50f), 3100); g.up(TablePoint(90f, 50f), 3110)
        assertTrue("nearMiss:distance" in diagnostics)
        g.cancel(); diagnostics.clear()
        first(4000); g.down(point, 4100); g.move(TablePoint(60f, 50f), 4110); g.up(TablePoint(60f, 50f), 4120)
        assertEquals(listOf("nearMiss:becameDrag"), diagnostics)
        g.cancel(); diagnostics.clear()
        first(5000); g.down(point, 5100); g.tick(5800); g.up(point, 5810)
        assertEquals(listOf("nearMiss:becameHold"), diagnostics)
        g.cancel(); diagnostics.clear()
        first(6000); g.cancel(); first(6100)
        assertEquals(listOf("nearMiss:cancelled"), diagnostics)
    }
    // TBL-016
    @Test fun firstDownOwnsPanAcrossAllViewportBoundariesAndPinchScalesUntilRelease() {
        val world = TableWorld(TableBounds(200f, 0f, 300f, 400f), TableBounds(0f, 0f, 800f, 800f))
        val ports = listOf(
            TableViewport("cardPanel/test", TableBounds(0f, 0f, 100f, 200f), 100f, 0f, contentHeight = 800f, scrollY = 50f),
            TableViewport("hand", TableBounds(200f, 400f, 300f, 50f), 800f, 50f, hand = true),
            TableViewport("stack", TableBounds(100f, 0f, 100f, 200f), 800f, 50f),
            TableViewport("other", TableBounds(0f, 200f, 100f, 200f), 800f, 50f))
        val scene = TableScene(emptyList(), emptyList(), ports, world = world)
        val events = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator(events::add)
        for (port in ports) {
            gestures.update(scene, true)
            gestures.down(port.bounds.center, 0)
            gestures.transform(TablePoint(-10f, -10f), 1f, world.viewport.center)
            gestures.transform(TablePoint(-10f, -10f), 1.5f, ports.last().bounds.center)
            assertEquals(listOf(port.id, port.id), events.filterIsInstance<TableIntent.Scroll>().map { it.viewportId })
            assertTrue(events.none { it is TableIntent.Camera })
            val scrolls = events.filterIsInstance<TableIntent.Scroll>()
            assertEquals(60f, scrolls.first().offset)
            assertEquals(70f, scrolls.last().offset)
            gestures.cancel(); events.clear()
        }
        gestures.update(scene, true)
        gestures.down(world.viewport.center, 0)
        for (port in ports) gestures.transform(TablePoint(10f, 10f), 1f, port.bounds.center)
        assertEquals(ports.size, events.filterIsInstance<TableIntent.Camera>().size)
        assertTrue(events.none { it is TableIntent.Scroll })
        gestures.cancel(); events.clear()
        gestures.down(ports.first().bounds.center, 100)
        gestures.transform(TablePoint(0f, -10f), 1f, world.viewport.center)
        assertEquals(ports.first().id, events.filterIsInstance<TableIntent.Scroll>().single().viewportId)
    }

    // TBL-016
    @Test fun oneFingerViewportScrollAndSecondPointerKeepOriginalOwnerAcrossWorld() {
        val port = TableViewport("cardPanel/test", TableBounds(0f, 0f, 100f, 200f), 100f, 0f,
            contentHeight = 600f)
        val world = TableWorld(TableBounds(100f, 0f, 300f, 400f), TableBounds(0f, 0f, 800f, 800f))
        val scene = TableScene(emptyList(), emptyList(), listOf(port), world = world)
        val events = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator(events::add)
        gestures.update(scene, true)
        // A panel pans with a double-tap-drag (a plain one-finger drag on empty panel space draws a rectangle).
        gestures.down(TablePoint(50f, 100f), 0); gestures.up(TablePoint(50f, 100f), 10); events.clear()
        gestures.down(TablePoint(50f, 100f), 100)
        gestures.move(TablePoint(60f, 80f), 120)
        gestures.move(TablePoint(300f, 50f), 140)
        gestures.transform(TablePoint(0f, -10f), 1f, world.viewport.center)
        assertEquals(3, events.filterIsInstance<TableIntent.Scroll>().size)
        assertTrue(events.filterIsInstance<TableIntent.Scroll>().all { it.viewportId == port.id && it.vertical })
        assertTrue(events.none { it is TableIntent.Camera || it is TableIntent.Move })
    }

    private val panelPort = TableViewport("cardPanel/test", TableBounds(0f, 0f, 100f, 200f), 100f, 0f, contentHeight = 600f)
    private fun panelCard(id: String, y: Float) = TableItem("card/$id", TableBounds(10f, y, 28f, 40f), "Panel $id", id,
        kind = TableItemKind.CARD, cardId = id, zone = PlayZone.GRAVEYARD, viewport = panelPort.id)
    private fun panelScene(): TableScene {
        val field = TableItem("card/f", TableBounds(110f, 10f, 28f, 40f), "Field f", "f",
            kind = TableItemKind.CARD, cardId = "f", zone = PlayZone.BATTLEFIELD)
        val world = TableWorld(TableBounds(100f, 0f, 300f, 400f), TableBounds(0f, 0f, 800f, 800f))
        return TableScene(listOf(panelCard("p1", 10f), panelCard("p2", 60f), field), emptyList(), listOf(panelPort), world = world)
    }



    // ZON-061
    @Test fun panelDoubleTapDragAndTwoFingersScrollOverCardsWhileOneFingerDragsTheCard() {
        val events = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator(events::add)
        val card = panelCard("p1", 10f).bounds.center
        gestures.update(panelScene(), true)
        // One finger on a card drags it.
        gestures.down(card, 0); gestures.move(TablePoint(card.x, card.y + 60f), 20)
        assertEquals("p1", gestures.preview.scope?.contextId)
        gestures.cancel(); events.clear()
        // Double-tap-drag over the card scrolls the panel.
        gestures.down(card, 1000); gestures.up(card, 1010)
        assertTrue(events.any { it is TableIntent.Actions })
        events.clear()
        gestures.down(card, 1100); gestures.move(TablePoint(card.x, card.y - 30f), 1120); gestures.up(TablePoint(card.x, card.y - 30f), 1130)
        assertEquals(TableIntent.Scroll(panelPort.id, 30f, vertical = true), events.filterIsInstance<TableIntent.Scroll>().last())
        assertTrue(events.none { it is TableIntent.Move || it is TableIntent.Actions || it is TableIntent.EmptyTap })
        events.clear()
        // Two fingers starting on a card scroll too.
        gestures.down(card, 3000)
        gestures.transform(TablePoint(0f, -20f), 1f, card)
        assertEquals(TableIntent.Scroll(panelPort.id, 20f, vertical = true), events.filterIsInstance<TableIntent.Scroll>().single())
        assertTrue(events.none { it is TableIntent.Camera })
    }


    // ZON-032
    @Test fun holdingAGraveyardOrExilePilePreviewsItsTopCardWithoutActionsOrSearch() {
        val base = TableFixture.play(board = 1)
        val piles = listOf(PlayZone.GRAVEYARD, PlayZone.EXILE)
        val faceUp = (0..1).map { n -> PlayCard("pile$n", TableFixture.submitted.digest, "a", "", "face", covered = false) }
        // Each owner's graveyard and exile hold two face-up cards, top first.
        val cards = listOf("a", "b").flatMap { owner -> piles.flatMap { zone -> faceUp.map {
            it.copy(id = "$owner/$zone/${it.id}", owner = owner, containerId = TableFixture.container(owner, zone)) } } }
        val play = base.copy(board = base.board.copy(cards = base.board.cards + cards, containers = base.board.containers.map { c ->
            if (c.zone in piles) c.copy(cards = cards.filter { it.containerId == c.id }.map { it.id }) else c }))
        val scene = TableFixture.scene(play)
        for (owner in listOf("a", "b")) for (zone in piles) {
            val pile = scene.items.single { it.id == TableFixture.container(owner, zone) }
            assertEquals(listOf("$owner/$zone/pile0", "$owner/$zone/pile1"), pile.cards)
            val events = mutableListOf<TableIntent>()
            val gestures = TableGestureCoordinator(events::add)
            gestures.update(scene, true)
            val p = pile.bounds.center
            gestures.down(p, 0); gestures.tick(1000); gestures.up(p, 1050); gestures.tick(2000)
            assertEquals(listOf<TableIntent>(TableIntent.Peek("$owner/$zone/pile0"), TableIntent.Peek(null, released = true)), events, pile.id)
        }
    }

    // TBL-010
    @Test fun pinchZoomStaysBetweenPointSixFiveAndTwoAndATwoFingerDragOnlyPans() {
        val scene = TableFixture.scene()
        val world = scene.world!!
        val center = world.viewport.center
        for ((scale, expected) in listOf(10f to 2f, .1f to .65f)) {
            val f = Fixture(scene)
            f.gestures.transform(TablePoint(0f, 0f), scale, center)
            assertEquals(expected, f.events.filterIsInstance<TableIntent.Camera>().last().camera.zoom)
            f.gestures.cancel(); f.gestures.tick(0); f.gestures.tick(1000)
            assertEquals(expected, f.events.filterIsInstance<TableIntent.Camera>().last().camera.zoom, "after release")
        }
        val f = Fixture(scene)
        val before = scene.camera
        f.gestures.transform(TablePoint(20f, -15f), 1f, center)
        val camera = f.events.filterIsInstance<TableIntent.Camera>().single().camera
        assertEquals(before.zoom, camera.zoom)
        assertTrue(camera.x < before.x && camera.y > before.y, "the battlefield follows the fingers: $before -> $camera")
    }

    @Test fun hitTestingSamplesTheDrawnSceneWithoutCompositionUpdates() {
        val card = TableItem("card/a", TableBounds(0f, 0f, 28f, 40f), "Card", "",
            kind = TableItemKind.CARD, cardId = "a")
        val initial = TableScene(listOf(card), emptyList())
        var displayed = initial
        val events = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator(events::add)
        gestures.update(initial, true, sceneSource = { displayed })
        val bounds = card.bounds.copy(x = 100f)
        displayed = initial.copy(items = listOf(card.copy(bounds = bounds, visible = listOf(bounds))))
        gestures.down(bounds.center, 0); gestures.up(bounds.center, 20)
        assertEquals(card.id, events.filterIsInstance<TableIntent.Actions>().single().itemId)
    }

    // TBL-014
    @Test fun panAndPinchRubberBandThenSpringBackOnBothAxesForLargeAndSmallContent() {
        for (extent in listOf(100f, 800f)) for (pinch in listOf(false, true)) {
            val world = TableWorld(TableBounds(0f, 0f, 400f, 400f), TableBounds(0f, 0f, extent, extent))
            val scene = TableScene(emptyList(), emptyList(), world = world, camera = world.fit())
            val events = mutableListOf<TableIntent>()
            val gestures = TableGestureCoordinator(events::add)
            gestures.update(scene, true)
            val start = world.viewport.center
            if (pinch) {
                gestures.down(start, 0)
                repeat(20) { gestures.transform(TablePoint(40f, -40f), .98f, start) }
                gestures.cancel() // production pinch release/cancellation path
            } else {
                gestures.down(start, 0); gestures.up(start, 20); gestures.down(start, 100)
                val end = TablePoint(start.x + 800, start.y - 800)
                gestures.move(end, 140); gestures.up(end, 160)
            }
            val released = events.filterIsInstance<TableIntent.Camera>().last().camera
            val target = world.clamp(released)
            assertNotEquals(target.x, released.x); assertNotEquals(target.y, released.y)
            assertTrue(kotlin.math.abs(released.x - target.x) * released.zoom < 96f)
            assertTrue(kotlin.math.abs(released.y - target.y) * released.zoom < 96f)
            assertTrue(gestures.busy)
            gestures.tick(200); gestures.tick(300)
            val frame = events.filterIsInstance<TableIntent.Camera>().last().camera
            assertTrue(kotlin.math.abs(frame.x - target.x) < kotlin.math.abs(released.x - target.x))
            assertTrue(kotlin.math.abs(frame.y - target.y) < kotlin.math.abs(released.y - target.y))
            gestures.tick(900)
            assertEquals(target, events.filterIsInstance<TableIntent.Camera>().last().camera)
            assertFalse(gestures.busy)
        }
    }

    // TBL-014
    @Test fun incrementalTransformsDoNotCompoundRubberResistanceAndNewPressInterruptsSpring() {
        val world = TableWorld(TableBounds(0f, 0f, 400f, 400f), TableBounds(0f, 0f, 400f, 400f))
        val events = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator(events::add)
        gestures.update(TableScene(emptyList(), emptyList(), world = world), true)
        repeat(20) { gestures.transform(TablePoint(20f, 0f), 1f, world.viewport.center) }
        assertEquals(world.resist(TableCamera(-400f, 0f)), events.filterIsInstance<TableIntent.Camera>().last().camera)
        gestures.cancel(); gestures.tick(0); gestures.tick(100)
        gestures.down(world.viewport.center, 100)
        val stopped = events.filterIsInstance<TableIntent.Camera>().last().camera
        gestures.tick(1000)
        assertEquals(stopped, events.filterIsInstance<TableIntent.Camera>().last().camera)
        gestures.transform(TablePoint(0f, 0f), 1f, world.viewport.center)
        assertEquals(stopped.x, events.filterIsInstance<TableIntent.Camera>().last().camera.x, .001f,
            "Grabbing the spring must not compound resistance")
        gestures.up(world.viewport.center, 1100)
        gestures.tick(1200); gestures.tick(1900)
        assertEquals(world.clamp(stopped), events.filterIsInstance<TableIntent.Camera>().last().camera,
            "Even a stationary grab must resume spring-back on release")
    }

    // SYS-029, TBL-011, TBL-012
    @Test fun emptySingleTapIsImmediateDoubleTapFitsAndDoubleTapHoldDragPans() {
        val scene = TableFixture.scene(TableFixture.play(board = 2), ui = TableLayoutState(TableCamera(zoom = 1.4f)))
        val point = TablePoint(scene.world!!.viewport.center.x, scene.world.viewport.center.y)
        assertNull(scene.hit(point))
        val f = Fixture(scene)
        f.gestures.down(point, 0); f.gestures.up(point, 20)
        assertEquals(1, f.events.count { it == TableIntent.EmptyTap })
        f.gestures.down(point, 100); f.gestures.up(point, 120)
        assertEquals(scene.world.fit(), f.events.filterIsInstance<TableIntent.Camera>().single().camera)
        assertTrue(f.events.none { it is TableIntent.Move })

        f.gestures.update(scene, true)
        f.events.clear()
        f.gestures.down(point, 500); f.gestures.up(point, 520)
        f.gestures.down(point, 600); f.gestures.tick(1100) // Holding the second tap must not become a peek.
        val end = TablePoint(point.x + 42, point.y - 28)
        f.gestures.move(end, 1120)
        f.gestures.up(end, 1140)
        val camera = f.events.filterIsInstance<TableIntent.Camera>().last().camera
        assertEquals(-30f, camera.x, .001f)
        assertEquals(20f, camera.y, .001f)
        assertEquals(1.4f, camera.zoom)
        assertEquals(1, f.events.count { it == TableIntent.EmptyTap }) // Only the immediate first tap.
        assertTrue(f.events.none { it is TableIntent.Move || it is TableIntent.Peek })
    }

    // TBL-011 (I-D52): a one-finger drag that starts on empty table pans it; there is no selection rectangle
    @Test fun oneFingerDragOnEmptyTablePans() {
        val scene = TableFixture.scene(TableFixture.play(board = 2), ui = TableLayoutState(TableCamera(zoom = 1.4f)))
        val point = TablePoint(scene.world!!.viewport.center.x, scene.world.viewport.center.y)
        assertNull(scene.hit(point))
        val f = Fixture(scene)
        val end = TablePoint(point.x + 42, point.y - 28)
        f.gestures.down(point, 0); f.gestures.move(end, 40); f.gestures.up(end, 60)
        val camera = f.events.filterIsInstance<TableIntent.Camera>().last().camera
        assertEquals(-30f, camera.x, .001f)
        assertEquals(20f, camera.y, .001f)
        assertEquals(1.4f, camera.zoom)
        assertTrue(f.events.none { it is TableIntent.Move || it is TableIntent.Actions || it == TableIntent.EmptyTap })
    }


    // TBL-001
    @Test fun pinchUsesOneCentroidTransformAcrossBothFieldsAndCenterBandAndClampsPan() {
        val scene = TableFixture.scene()
        val world = scene.world!!
        val f = Fixture(scene)
        val centroid = TablePoint(world.viewport.center.x - 60, world.viewport.center.y + 12)
        val pan = TablePoint(20f, -5f)
        f.gestures.transform(pan, 1.5f, centroid)
        val camera = f.events.filterIsInstance<TableIntent.Camera>().single().camera
        val anchor = TablePoint(world.viewport.width / 2 + centroid.x - pan.x - world.viewport.center.x,
            world.viewport.height / 2 + centroid.y - pan.y - world.viewport.center.y)
        val after = world.screen(anchor, camera)
        assertEquals(centroid.x, after.x, .001f); assertEquals(centroid.y, after.y, .001f)
        f.gestures.transform(TablePoint(Float.MAX_VALUE, Float.MAX_VALUE), 1f, centroid)
        val clamped = f.events.filterIsInstance<TableIntent.Camera>().last().camera
        assertNotNull(world.screen(world.bounds, clamped).intersection(world.viewport))
    }

    @Test fun closingSameCardRingSuppressesOnlySingleTapAndStillAllowsDoubleDragAndPeek() {
        // The battlefield cards are creatures: their stack lies at the left of the row nearest the middle, away from the Stack
        // pinned at the right edge (its first card's strip is what is pressed)
        val creatures = TableFixture.play(board = 3, hand = 2).let { p -> p.copy(definitions = p.definitions.map { d -> d.copy(
            definition = d.definition.copy(faces = d.definition.faces.map { it.copy(typeLine = "Creature — Test") })) }) }
        val f = Fixture(TableFixture.scene(creatures))
        val point = f.point()
        f.tap(0); f.gestures.tick(400); f.events.clear()
        f.gestures.down(point, 500, f.item().cardId); f.gestures.up(point, 520)
        assertTrue(f.events.isEmpty())
        f.gestures.down(point, 600); f.gestures.up(point, 620)
        assertTrue(f.events.none { it is TableIntent.Actions })
        f.events.clear()
        // ZON-077: a drag from the battlefield card pans the table
        val end = TablePoint(350f, f.scene.fields.last().bounds.center.y)
        f.gestures.down(point, 1000, f.item().cardId); f.gestures.move(end, 1080); f.gestures.up(end, 1100)
        assertTrue(f.events.any { it is TableIntent.Camera } && f.events.none { it is TableIntent.Move })
        // The pan springs back to its bounds meanwhile (Camera); only the hold's own intents count
        f.gestures.tick(1400); f.events.clear()
        f.gestures.down(point, 1500, f.item().cardId); f.gestures.tick(1849)
        assertTrue(f.events.none { it !is TableIntent.Camera })
        f.gestures.tick(1850)
        assertEquals(listOf<TableIntent>(TableIntent.Peek(f.item().cardId)), f.events.filter { it !is TableIntent.Camera })
    }

    @Test fun dismissingRingDoesNotSuppressAnotherCardsActions() {
        val f = Fixture()
        f.gestures.down(f.point("a/BATTLEFIELD/1"), 500, f.item().cardId)
        f.gestures.up(f.point("a/BATTLEFIELD/1"), 520)
        assertEquals("card/a/BATTLEFIELD/1", f.events.filterIsInstance<TableIntent.Actions>().single().itemId)
    }


    // TBL-104
    @Test fun peekEndsOnReleaseCancelAndSecondPointer() {
        for (end in 0..2) {
            val f = Fixture(); val point = f.point()
            f.gestures.down(point, 0); f.gestures.tick(500)
            assertEquals(listOf<TableIntent>(TableIntent.Peek("a/BATTLEFIELD/0")), f.events)
            when (end) {
                0 -> f.gestures.up(point, 600)
                1 -> f.gestures.cancel()
                else -> f.gestures.transform(TablePoint(0f, 0f), 1f, point)
            }
            assertEquals(TableIntent.Peek(null, released = end == 0), f.events.filterIsInstance<TableIntent.Peek>().last())
        }
    }

    // TBL-104
    @Test fun heldPointerScrollsInspectionIncrementallyInDpWithoutMovingCardsAndStopsOnRelease() {
        for (density in listOf(1f, 2.625f)) {
            val events = mutableListOf<TableIntent>()
            val scrolls = mutableListOf<Float>()
            val scene = TableFixture.scene()
            val gestures = TableGestureCoordinator(events::add, density = density, onPeekScroll = scrolls::add)
            gestures.update(scene, false) // Inspection also works while read-only.
            val start = scene.items.single { it.cardId == "a/BATTLEFIELD/0" }.visible.first().center
            fun point(dx: Float = 0f, dy: Float = 0f) = gestures.pointFromPixels((start.x + dx) * density, (start.y + dy) * density)
            gestures.down(point(), 0)
            gestures.move(point(dy = -4f), 40)
            assertTrue(scrolls.isEmpty())
            gestures.tick(400)
            assertTrue(gestures.peeking)
            gestures.move(point(dx = 100f, dy = -44f), 420)
            gestures.move(point(dx = -100f, dy = -44f), 440) // Horizontal motion does not scroll.
            gestures.move(point(dy = -24f), 460)
            gestures.up(point(dy = -24f), 480)
            gestures.move(point(dy = -100f), 500)
            assertFalse(gestures.peeking)
            assertEquals(2, scrolls.size)
            assertEquals(40f, scrolls[0], .001f)
            assertEquals(-20f, scrolls[1], .001f)
            assertEquals(listOf<TableIntent>(TableIntent.Peek("a/BATTLEFIELD/0"), TableIntent.Peek(null, released = true)), events)
        }
    }

    // TBL-131 (owner 2026-11-06): while inspecting, a drag to the left is the pinned swipe's (measured in dp from where the
    // hold began, nothing scrolls meanwhile); once the finger has scrolled up or down, sideways movement stays the scroll's
    @Test fun aHeldDragToTheLeftIsThePinnedSwipeUnlessScrollingBegan() {
        for (scrollFirst in listOf(false, true)) {
            val events = mutableListOf<TableIntent>()
            val scrolls = mutableListOf<Float>()
            val swipe = InspectSwipe()
            val scene = TableFixture.scene()
            val gestures = TableGestureCoordinator(events::add, density = 2f, onPeekScroll = scrolls::add, onPeekSwipe = swipe::moved)
            gestures.update(scene, false)
            val start = scene.items.single { it.cardId == "a/BATTLEFIELD/0" }.visible.first().center
            fun point(dx: Float = 0f, dy: Float = 0f) = gestures.pointFromPixels((start.x + dx) * 2f, (start.y + dy) * 2f)
            gestures.down(point(), 0)
            gestures.tick(400)
            swipe.begin()
            if (scrollFirst) gestures.move(point(dy = -40f), 420)
            val scrolled = scrolls.size
            gestures.move(point(dx = -60f, dy = if (scrollFirst) -42f else -4f), 440)
            gestures.move(point(dx = -140f, dy = if (scrollFirst) -44f else -6f), 460)
            gestures.up(point(dx = -140f, dy = if (scrollFirst) -44f else -6f), 480)
            if (scrollFirst) {
                assertEquals(0f, swipe.progress)
                assertTrue(scrolls.size > scrolled, "still scrolling: $scrolls")
                assertFalse(swipe.released())
            } else {
                assertEquals(scrolled, scrolls.size, "nothing scrolls during the swipe")
                assertEquals(1f, swipe.progress)
                assertTrue(swipe.released())
                assertTrue(swipe.pinned)
            }
            assertEquals(TableIntent.Peek(null, released = true), events.last())
        }
    }


    // TBL-052
    @Test fun landingOutlinesTrackSnappedRowsMultiCardOffsetsAndContainers() {
        val scene = TableFixture.scene(TableFixture.play(board = 2))
        val scope = TableScope("a/BATTLEFIELD/0", listOf("a/BATTLEFIELD/0", "a/BATTLEFIELD/1"))
        for (row in 0..1) {
            val d = TableDestination.Grid("a/BATTLEFIELD", 5, row)
            val preview = TableGesturePreview(scope = scope, destination = d)
            val landing = tableDragLanding(scene, preview)
            // TBL-004: the battlefield lays itself out: the drop lights up the battlefield it lands on
            assertEquals(listOf(scene.fields.last().bounds), landing.outlines)
        }
        for (zone in listOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.STACK)) {
            val item = scene.items.first { it.kind == TableItemKind.PILE && it.zone == zone }
            val preview = TableGesturePreview(scope = scope, destination = TableDestination.Container(item.id, zone))
            assertEquals(2, tableDragLanding(scene, preview).outlines.size, "$zone")
        }
        assertTrue(tableDragLanding(scene, TableGesturePreview(scope = scope)).outlines.isEmpty())
    }

    // TBL-004
    @Test fun transformedLandsBandsStillTargetRowZero() {
        for (zoom in listOf(.65f, 1f, 2f)) for (pan in listOf(-12f, 0f, 12f)) for (owner in listOf("a", "b")) {
            val home = TablePresentation.scene(LiveTableFixture.game, tableBoard(LiveTableFixture.play()), "a", 892f, 412f, true)
            val anchor = home.fields.single { it.owner == owner }.landsBounds(owner == "a").center
            val scene = TablePresentation.scene(LiveTableFixture.game, tableBoard(LiveTableFixture.play()), "a", 892f, 412f, true,
                TableLayoutState(TableCamera(y = anchor.y - home.world!!.viewport.center.y + pan, zoom = zoom)))
            val start = scene.items.single { it.cardId == "a/HAND/2" }.visible.first().center
            for (field in scene.fields.filter { it.owner == owner }) {
                val band = assertNotNull(field.landsBounds(field.owner == "a", scene.camera).intersection(field.bounds))
                val end = TablePoint(field.bounds.right - 24, band.center.y)
                val events = mutableListOf<TableIntent>()
                val gesture = TableGestureCoordinator(events::add)
                gesture.update(scene, true)
                // ZON-081: a hand card is dragged by first moving up or down (sideways pans the hand)
                gesture.down(start, 0); gesture.move(TablePoint(start.x, start.y + if (end.y > start.y) 20f else -20f), 40)
                gesture.move(end, 80); gesture.up(end, 100)
                val destination = assertIs<TableDestination.Grid>(events.filterIsInstance<TableIntent.Move>().single().destination)
                assertEquals(0, destination.row)
            }
        }
    }


    // CMB-009
    @Test fun combatInsertionDragBindsOnlyTheRequestedOccurrenceAndCancellingDoesNotCreatePairs() {
        val f = TableStatesFixture
        val play = f.combat()
        for (landscape in listOf(false, true)) {
            val scene = TablePresentation.scene(f.game, tableBoard(play), f.a, if (landscape) 892f else 412f,
                if (landscape) 412f else 892f, landscape)
            val events = mutableListOf<TableIntent>()
            val gesture = TableGestureCoordinator(events::add)
            gesture.update(scene, true)
            val occurrence = scene.items.single { it.cardId == f.id("attacker2") }
            val gap = scene.combatDrops.first { it.destination.pairId == null && it.destination.role == org.asyncttwiz.protocol.CombatRole.ATTACKER }
            gesture.down(occurrence.visible.first().center, 0)
            gesture.move(gap.bounds.center, 80); gesture.up(gap.bounds.center, 100)
            val move = events.filterIsInstance<TableIntent.Move>().single()
            assertEquals(listOf(occurrence.cardId), move.scope.cardIds)
            assertEquals(TableDestination.Combat(gap.destination), move.destination)
            assertEquals(f.source(f.a, "1/2").id, move.sources.getValue(occurrence.cardId!!).sourceId)
            assertEquals(2, play.board.combat!!.pairs.size)
            assertTrue(events.none { it is TableIntent.Actions })
            events.clear()
            gesture.down(occurrence.visible.first().center, 200); gesture.move(gap.bounds.center, 280); gesture.cancel()
            assertTrue(events.isEmpty())
        }
    }

    // CMB-003, TBL-044
    @Test fun combatTapOpensCombatMenuDoubleTapDoesNothingAndHoldInspects() {
        val f = TableStatesFixture
        val play = f.combat()
        val scene = TablePresentation.scene(f.game, tableBoard(play), f.a, 412f, 892f, false)
        val card = scene.items.single { it.cardId == f.id("attacker2") }
        val events = mutableListOf<TableIntent>()
        val gesture = TableGestureCoordinator(events::add)
        gesture.update(scene, true)
        val p = card.visible.first().center
        gesture.down(p, 0); gesture.up(p, 20)
        assertEquals(card.id, events.filterIsInstance<TableIntent.Actions>().single().itemId)
        events.clear()
        gesture.down(p, 60); gesture.up(p, 80); gesture.tick(400)
        // Owner playtest 2026-11-06: nothing but closing what the first tap opened, no message (the old by-hand rotation
        // said "Rotation not queued")
        assertTrue(events.all { it == TableIntent.DismissTapUi || it is TableIntent.Camera }, "$events")
        events.clear()
        gesture.down(p, 500); gesture.tick(1100); gesture.up(p, 1150)
        assertEquals(listOf<TableIntent>(TableIntent.Peek(card.cardId), TableIntent.Peek(null, released = true)), events)
    }

    // CMB-009, CMB-007 (owner playtest 2026-11-06: the band takes combat drops; its end drops reach past the lanes to the
    // band's ends)
    @Test fun combatDropEndsReachThePanelsEdgesDividersAreLenientAndPreviewIsUprightAtLanding() {
        val f = TableStatesFixture
        val play = f.combat()
        for (zoom in listOf(.65f, 1f, 2f)) {
            val base = TablePresentation.scene(f.game, tableBoard(play), f.a, 892f, 412f, true,
                TableLayoutState(camera = TableCamera(x = -CombatStackPresentation.combat(play.board, 28f, 40f).width / 2 + 50f / zoom, zoom = zoom)))
            val world = base.world!!
            // The area's bigger cards (COMBAT_MIN_CARD) reach past a landscape viewport at zoom 2: the viewport is unbounded
            val scene = base.copy(world = world.copy(viewport = world.viewport.copy(x = -1e6f, width = 2e6f, y = -1e6f, height = 2e6f)))
            val occurrence = scene.items.first { it.viewport == "combat" && it.visible.isNotEmpty() }
            val events = mutableListOf<TableIntent>()
            val gesture = TableGestureCoordinator(events::add)
            gesture.update(scene, true)
            val panel = assertNotNull(scene.combatPanel)
            fun edge(r: CombatDropRegion) = r.bounds.x < panel.x + .01f || r.bounds.right > panel.right - .01f
            val regions = scene.combatDrops.filter { it.destination.role == org.asyncttwiz.protocol.CombatRole.ATTACKER }
            val left = regions.single { it.destination.pairId == null && it.bounds.x < panel.x + .01f }
            val right = regions.single { it.destination.pairId == null && it.bounds.right > panel.right - .01f }
            val divider = regions.single { it.destination.pairId == null && !edge(it) }
            val lowerDivider = scene.combatDrops.single { it.destination.role == org.asyncttwiz.protocol.CombatRole.BLOCKER &&
                it.destination.pairId == null && !edge(it) }
            // Beside the lanes, still in the band, the end drops take it
            assertEquals(left.destination, scene.combatDestination(TablePoint(panel.x - 1f, left.bounds.center.y), null))
            assertEquals(right.destination, scene.combatDestination(TablePoint(panel.right + 1f, right.bounds.center.y), null))
            // CMB-027: covering half of a neighbouring card goes beside it, so between two attackers the divider
            // only takes drops within a slot margin; the blocker row has a card on one side only and stays lenient.
            val near = CombatStackPresentation.slotMargin(scene.combatCardWidth) * .5f
            for ((region, point) in listOf(left to TablePoint(panel.x + .5f, left.bounds.center.y),
                right to TablePoint(panel.right - .5f, right.bounds.center.y),
                divider to TablePoint(divider.bounds.center.x - near, divider.bounds.center.y),
                divider to TablePoint(divider.bounds.center.x + near, divider.bounds.center.y),
                lowerDivider to TablePoint(lowerDivider.bounds.center.x + minOf(11f, scene.combatCardWidth / 2), lowerDivider.bounds.center.y))) {
                events.clear()
                gesture.down(occurrence.visible.first().center, 0); gesture.move(point, 80)
                assertEquals(TableDestination.Combat(region.destination), gesture.preview.destination)
                val landing = tableDragLanding(scene, gesture.preview).outlines.single()
                assertEquals(scene.combatCardWidth, landing.width, .001f)
                assertEquals(scene.combatCardHeight, landing.height, .001f)
                val prediction = CombatBoards(f.a).place(play, listOf(occurrence.cardId!!), region.destination)
                val expected = TablePresentation.scene(f.game, tableBoard(prediction), f.a, 892f, 412f, true,
                    TableLayoutState(camera = scene.camera)).items.single { it.cardId == occurrence.cardId }.bounds
                assertEquals(expected.x, landing.x, .001f)
                assertEquals(expected.y, landing.y, .001f)
                gesture.up(point, 100)
                assertEquals(TableDestination.Combat(region.destination), events.filterIsInstance<TableIntent.Move>().single().destination)
            }
            val last = scene.items.single { it.cardId == f.id("attacker2") }
            val end = TablePoint(panel.right - .5f, right.bounds.center.y)
            gesture.down(last.bounds.center, 200); gesture.move(end, 280)
            assertEquals(TableDestination.Combat(right.destination), gesture.preview.destination)
            // The old last group vanishes, so its replacement lands at that same packed position.
            assertEquals(last.bounds.x, tableDragLanding(scene, gesture.preview).outlines.single().x, .001f)
            gesture.cancel()
        }
    }




    // TBL-042
    @Test fun doubleTapAllowsFourHundredMillisAndThirtyTwoDpButNotBeyondEither() {
        for (density in listOf(1f, 2.625f)) for (delay in listOf(399L, 401L)) for (distance in listOf(31f, 33f)) {
            val card = TableItem("card/a", TableBounds(0f, 0f, 100f, 100f), "Card", "",
                kind = TableItemKind.CARD, cardId = "a", zone = PlayZone.BATTLEFIELD)
            val diagnostics = mutableListOf<String>()
            val gestures = TableGestureCoordinator({}, density = density, diagnostic = diagnostics::add)
            gestures.update(TableScene(listOf(card), emptyList()), true)
            val first = gestures.pointFromPixels(10 * density, 10 * density)
            val second = gestures.pointFromPixels((10 + distance) * density, 10 * density)
            gestures.down(first, 0); gestures.up(first, 20)
            val expected = delay <= 400 && distance <= 32
            assertEquals(expected, gestures.replacesTap(second, 20 + delay))
            gestures.down(second, 20 + delay); gestures.up(second, 40 + delay)
            assertEquals(expected, "recognized" in diagnostics)
        }
    }
    @Test fun handDragTargetsViewerBattlefieldAtRealDeviceViewportsAndDoubleTapAddsNothing() {
        for ((width, height, landscape) in listOf(Triple(412f, 892f, false), Triple(892f, 412f, true),
            Triple(360f, 640f, false), Triple(640f, 360f, true))) {
            val play = LiveTableFixture.play()
            val scene = TablePresentation.scene(LiveTableFixture.game, tableBoard(play), "a", width, height, landscape)
            val field = scene.fields.single { it.owner == "a" }
            assertTrue(field.bounds.height > 0)
            val hand = scene.items.single { it.cardId == "a/HAND/2" }
            assertFalse(hand.covered)
            val start = hand.visible.first().center
            // The right-edge midpoint is now the screen-fixed Stack, not empty battlefield.
            val destination = TablePoint(field.bounds.x + 5 * TablePresentation.GRID, field.bounds.center.y)
            val events = mutableListOf<TableIntent>()
            val coordinator = TableGestureCoordinator(events::add)
            coordinator.update(scene, true)
            // ZON-081: a hand card is dragged by first moving up (sideways pans the hand)
            coordinator.down(start, 0); coordinator.move(TablePoint(start.x, start.y - 20f), 40)
            coordinator.move(destination, 80); coordinator.up(destination, 100)
            val move = events.filterIsInstance<TableIntent.Move>().single()
            assertEquals("a/BATTLEFIELD", (move.destination as TableDestination.Grid).containerId)
            assertEquals(listOf("a/HAND/2"), move.scope.cardIds)
            assertEquals("a/HAND", move.sources.getValue("a/HAND/2").containerId)
            assertTrue(events.none { it is TableIntent.Actions })
            events.clear()
            val card = scene.items.single { it.cardId == "a/BATTLEFIELD/2" }.visible.first().center
            coordinator.down(card, 200); coordinator.up(card, 220)
            coordinator.down(card, 300); coordinator.up(card, 320); coordinator.tick(1000)
            assertTrue(events.none { it is TableIntent.Move })
            assertEquals(1, events.filterIsInstance<TableIntent.Actions>().size)
            assertEquals(1, events.count { it == TableIntent.DismissTapUi })
        }
    }
    // ZON-077: nothing is placed by hand: a drag that starts on a battlefield card pans the table, even onto a hand
    @Test fun battlefieldDragPansTheTableInsteadOfMovingTheCard() {
        val scene = TableFixture.scene(TableFixture.play(board = 1, hand = 2))
        val events = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator(events::add)
        gestures.update(scene, true)
        val start = scene.items.single { it.cardId == "a/BATTLEFIELD/0" }.visible.first().center
        val end = scene.items.single { it.cardId == "a/HAND/1" }.visible.first().center
        gestures.down(start, 0); gestures.move(end, 80)
        assertNull(gestures.preview.scope, "no card lifted")
        gestures.up(end, 100)
        assertTrue(events.any { it is TableIntent.Camera })
        assertTrue(events.none { it is TableIntent.Move || it is TableIntent.Actions })
    }
    private class Fixture(val scene: TableScene = TableFixture.scene(), writable: Boolean = true) {
        val events = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator({ events += it })
        init { gestures.update(scene, writable) }
        fun item(id: String = "a/BATTLEFIELD/0") = scene.items.single { it.cardId == id }
        fun point(id: String = "a/BATTLEFIELD/0") = item(id).visible.first().center
        fun tap(time: Long, point: TablePoint = point()) { gestures.down(point, time); gestures.up(point, time + 20) }
        fun refresh(writable: Boolean = true) = gestures.update(scene, writable)
    }
    // SYS-029, TBL-041
    @Test fun normalSingleImmediatelyOpensCapturedActionsAndTimeoutDoesNotRepeatIt() {
        val f = Fixture()
        f.tap(0); assertEquals(1, f.events.size); f.gestures.tick(419); assertEquals(1, f.events.size)
        f.gestures.update(f.scene, true)
        f.gestures.tick(420)
        val action = f.events.single() as TableIntent.Actions
        assertEquals(listOf("a/BATTLEFIELD/0"), action.scope!!.cardIds)
    }
    // TBL-043 (owner playtest 2026-11-06: the by-hand rotation is gone): the second tap of a battlefield card's double tap
    // only dismisses what the first tap opened; nothing is turned
    @Test fun aDoubleTapsSecondTapAddsNothing() {
        val f = Fixture()
        f.tap(0); f.gestures.update(f.scene, true); f.tap(100)
        f.gestures.tick(1000)
        assertIs<TableIntent.Actions>(f.events.first())
        assertEquals(listOf<TableIntent>(TableIntent.DismissTapUi), f.events.drop(1))
    }
    // TBL-047, TBL-048
    @Test fun holdConsumesTapAndCannotTurnIntoDragOrExposePile() {
        val f = Fixture(); val p = f.point()
        f.gestures.down(p, 0); f.gestures.tick(500)
        f.gestures.move(TablePoint(p.x + 150, p.y - 80), 600); f.gestures.up(p, 700); f.gestures.tick(1100)
        assertEquals(listOf<TableIntent>(TableIntent.Peek("a/BATTLEFIELD/0"), TableIntent.Peek(null, released = true)), f.events)
        val pile = f.scene.items.single { it.id == "a/LIBRARY" }.visible.first().center
        f.events.clear(); f.gestures.down(pile, 1200); f.gestures.tick(1800); f.gestures.up(pile, 1810)
        assertTrue(f.events.isEmpty())
    }
    // TBL-051
    @Test fun dragCapturesIdsDuringReflowAndEmitsOnlyOnceOnValidRelease() {
        val f = Fixture()
        val start = f.point("a/HAND/0"); val dest = TablePoint(start.x, f.scene.fields.last().bounds.center.y)
        f.gestures.down(start, 0); f.gestures.move(dest, 100)
        assertTrue(f.events.isEmpty()); assertNotNull(f.gestures.preview.destination)
        f.gestures.update(f.scene.copy(items = f.scene.items.reversed()), true)
        f.gestures.up(dest, 200); f.gestures.tick(1000)
        val move = f.events.filterIsInstance<TableIntent.Move>().single()
        assertEquals(listOf("a/HAND/0"), move.scope.cardIds)
        assertTrue(move.destination is TableDestination.Grid)
        assertEquals("a/HAND", move.sources.getValue("a/HAND/0").containerId)
        assertTrue(f.events.none { it is TableIntent.Actions || it is TableIntent.Peek })
    }



    @Test fun rapidTransformFramesAccumulateWithoutWaitingForComposition() {
        val f = Fixture(); val point = f.scene.fields.last().bounds.center
        f.gestures.transform(TablePoint(-10f, 0f), 1f, point)
        f.gestures.transform(TablePoint(-10f, 0f), 1f, point)
        assertEquals(f.scene.world!!.resist(TableCamera(20f, 0f)),
            f.events.filterIsInstance<TableIntent.Camera>().last().camera)
    }


    // SYS-005
    @Test fun twoFingerCompactSidebarPanIsPersonalNotABoardMove() {
        val scene = TablePresentation.scene(TableFixture.game, tableBoard(TableFixture.play()), "a", 640f, 260f, true,
            TableLayoutState(TableCamera(zoom = 2f)))
        val f = Fixture(scene)
        val sidebar = scene.viewports.single { it.id == "sidebar" }
        // The narrow gutter beside the Stack belongs to the sidebar, not the horizontal counter rail.
        val background = TablePoint(sidebar.bounds.x + 1f, scene.stackRegion!!.center.y)
        f.gestures.down(background, 0)
        f.gestures.transform(TablePoint(0f, -60f), 1f, sidebar.bounds.center)
        assertEquals(TableIntent.Scroll("sidebar", 60f, true), f.events.single())
        assertTrue(f.events.none { it is TableIntent.Camera || it is TableIntent.Move })
    }
}
