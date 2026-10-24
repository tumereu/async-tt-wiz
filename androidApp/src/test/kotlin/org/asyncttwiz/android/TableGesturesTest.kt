package org.asyncttwiz.android

import org.asyncttwiz.protocol.PlayZone
import org.junit.Test
import kotlin.test.*

class TableGesturesTest {
    @Test fun hitTestingSamplesTheDrawnSceneWithoutCompositionUpdates() {
        val card = TableItem("card/a", TableBounds(0f, 0f, 28f, 40f), "Card", "",
            kind = TableItemKind.CARD, cardId = "a")
        val initial = TableScene(listOf(card), emptyList())
        var displayed = initial
        val events = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator(events::add)
        gestures.update(initial, TableSelection(), true, sceneSource = { displayed })
        val bounds = card.bounds.copy(x = 100f)
        displayed = initial.copy(items = listOf(card.copy(bounds = bounds, visible = listOf(bounds))))
        gestures.down(bounds.center, 0); gestures.up(bounds.center, 20)
        assertEquals(card.id, events.filterIsInstance<TableIntent.Actions>().single().itemId)
    }

    @Test fun panAndPinchRubberBandThenSpringBackOnBothAxesForLargeAndSmallContent() {
        for (extent in listOf(100f, 800f)) for (pinch in listOf(false, true)) {
            val world = TableWorld(TableBounds(0f, 0f, 400f, 400f), TableBounds(0f, 0f, extent, extent))
            val scene = TableScene(emptyList(), emptyList(), world = world, camera = world.fit())
            val events = mutableListOf<TableIntent>()
            val gestures = TableGestureCoordinator(events::add)
            gestures.update(scene, TableSelection(), true)
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

    @Test fun incrementalTransformsDoNotCompoundRubberResistanceAndNewPressInterruptsSpring() {
        val world = TableWorld(TableBounds(0f, 0f, 400f, 400f), TableBounds(0f, 0f, 400f, 400f))
        val events = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator(events::add)
        gestures.update(TableScene(emptyList(), emptyList(), world = world), TableSelection(), true)
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

    @Test fun emptySingleTapIsImmediateDoubleTapFitsAndDoubleTapHoldDragPansInsteadOfSelecting() {
        val scene = TableFixture.scene(TableFixture.play(board = 2), ui = TableLayoutState(TableCamera(zoom = 1.4f)))
        val point = TablePoint(scene.world!!.viewport.center.x, scene.world.viewport.center.y)
        assertNull(scene.hit(point))
        val f = Fixture(scene, TableSelection(setOf("a/BATTLEFIELD/0")))
        f.gestures.down(point, 0); f.gestures.up(point, 20)
        assertEquals(emptySet(), f.events.filterIsInstance<TableIntent.Selection>().single().selection.ids)
        f.gestures.down(point, 100); f.gestures.up(point, 120)
        assertEquals(scene.world.fit(), f.events.filterIsInstance<TableIntent.Camera>().single().camera)
        assertTrue(f.events.none { it is TableIntent.Move || it is TableIntent.Rotate })

        f.gestures.update(scene, TableSelection(), true)
        f.events.clear()
        f.gestures.down(point, 500); f.gestures.up(point, 520)
        f.gestures.down(point, 600); f.gestures.tick(1100) // Holding the second tap must not become a peek.
        val end = TablePoint(point.x + 42, point.y - 28)
        f.gestures.move(end, 1120)
        assertNull(f.gestures.preview.rectangle)
        f.gestures.up(end, 1140)
        val camera = f.events.filterIsInstance<TableIntent.Camera>().last().camera
        assertEquals(-30f, camera.x, .001f)
        assertEquals(20f, camera.y, .001f)
        assertEquals(1.4f, camera.zoom)
        assertEquals(1, f.events.filterIsInstance<TableIntent.Selection>().size) // Only the immediate first tap.
        assertTrue(f.events.none { it is TableIntent.Move || it is TableIntent.Peek })
    }

    @Test fun ordinaryEmptyDragStillSelectsAndEmptyDoubleTapDoesNotRecognizeAcrossHud() {
        val scene = TableFixture.scene()
        val f = Fixture(scene)
        val start = TablePoint(310f, scene.fields.last().bounds.y + 4)
        assertNull(scene.hit(start))
        val end = scene.items.single { it.cardId == "a/BATTLEFIELD/0" }.bounds.center
        f.gestures.down(start, 0); f.gestures.move(end, 80)
        assertNotNull(f.gestures.preview.rectangle)
        val previewIds = scene.rectangle(f.gestures.preview.rectangle!!)
        assertTrue("a/BATTLEFIELD/0" in previewIds)
        assertTrue(f.events.none { it is TableIntent.Selection })
        f.gestures.up(end, 100)
        assertEquals(previewIds, f.events.filterIsInstance<TableIntent.Selection>().single().selection.ids)
        assertTrue("a/BATTLEFIELD/0" in f.events.filterIsInstance<TableIntent.Selection>().single().selection.ids)
        assertTrue(f.events.none { it is TableIntent.Camera })
        f.events.clear()
        val hud = scene.stackRegion!!.center
        f.gestures.transform(TablePoint(50f, 50f), 1.5f, hud)
        assertTrue(f.events.isEmpty())
    }

    @Test fun pinchUsesOneCentroidTransformAcrossBothFieldsAndCenterBandAndClampsPan() {
        val scene = TableFixture.scene()
        val world = scene.world!!
        val f = Fixture(scene)
        val centroid = TablePoint(world.viewport.center.x - 60, world.viewport.center.y + 12)
        val pan = TablePoint(20f, -15f)
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
        val f = Fixture()
        val point = f.point()
        f.tap(0); f.gestures.tick(400); f.events.clear()
        f.gestures.down(point, 500, f.item().cardId); f.gestures.up(point, 520)
        assertTrue(f.events.isEmpty())
        f.gestures.down(point, 600); f.gestures.up(point, 620)
        assertEquals(1, f.events.filterIsInstance<TableIntent.Rotate>().size)
        assertTrue(f.events.none { it is TableIntent.Actions })
        f.events.clear()
        val end = TablePoint(350f, f.scene.fields.last().bounds.center.y)
        f.gestures.down(point, 1000, f.item().cardId); f.gestures.move(end, 1080); f.gestures.up(end, 1100)
        assertEquals(1, f.events.filterIsInstance<TableIntent.Move>().size)
        f.events.clear()
        f.gestures.down(point, 1500, f.item().cardId); f.gestures.tick(1849)
        assertTrue(f.events.isEmpty())
        f.gestures.tick(1850)
        assertEquals(listOf<TableIntent>(TableIntent.Peek(f.item().cardId)), f.events)
    }

    @Test fun dismissingRingDoesNotSuppressAnotherCardsActions() {
        val f = Fixture()
        f.gestures.down(f.point("a/BATTLEFIELD/1"), 500, f.item().cardId)
        f.gestures.up(f.point("a/BATTLEFIELD/1"), 520)
        assertEquals("card/a/BATTLEFIELD/1", f.events.filterIsInstance<TableIntent.Actions>().single().itemId)
    }

    @Test fun sharedSingleTapKeepsWaitingAndDoubleDoesNotPerformOrDismissIt() {
        val scene = TableFixture.scene()
        val point = scene.items.single { it.cardId == "a/BATTLEFIELD/0" }.visible.first().center
        val events = mutableListOf<TableIntent>()
        val coordinator = TableGestureCoordinator(events::add, singleTapIsLocal = { false })
        coordinator.update(scene, TableSelection(), true)
        coordinator.down(point, 0); coordinator.up(point, 20)
        assertTrue(events.isEmpty())
        assertFalse(coordinator.replacesTap(point, 100))
        coordinator.tick(419)
        assertTrue(events.isEmpty())
        coordinator.tick(420)
        assertIs<TableIntent.Actions>(events.single())
        events.clear()
        coordinator.down(point, 500); coordinator.up(point, 520)
        coordinator.down(point, 600); coordinator.up(point, 620)
        assertEquals(1, events.filterIsInstance<TableIntent.Rotate>().size)
        assertTrue(events.none { it is TableIntent.Actions || it == TableIntent.DismissTapUi })
    }

    @Test fun peekEndsOnReleaseCancelAndSecondPointerButExplicitInspectPersists() {
        for (end in 0..2) {
            val f = Fixture(); val point = f.point()
            f.gestures.down(point, 0); f.gestures.tick(500)
            assertEquals(listOf<TableIntent>(TableIntent.Peek("a/BATTLEFIELD/0")), f.events)
            when (end) {
                0 -> f.gestures.up(point, 600)
                1 -> f.gestures.cancel()
                else -> f.gestures.transform(TablePoint(0f, 0f), 1f, point)
            }
            assertEquals(TableIntent.Peek(null), f.events.filterIsInstance<TableIntent.Peek>().last())
            f.events.clear(); f.gestures.inspect(f.item())
            assertEquals(listOf<TableIntent>(TableIntent.Inspect("a/BATTLEFIELD/0")), f.events)
        }
    }

    @Test fun heldPointerScrollsInspectionIncrementallyInDpWithoutMovingCardsAndStopsOnRelease() {
        for (density in listOf(1f, 2.625f)) {
            val events = mutableListOf<TableIntent>()
            val scrolls = mutableListOf<Float>()
            val scene = TableFixture.scene()
            val gestures = TableGestureCoordinator(events::add, density = density, onPeekScroll = scrolls::add)
            gestures.update(scene, TableSelection(), false) // Inspection also works while read-only.
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
            assertEquals(listOf<TableIntent>(TableIntent.Peek("a/BATTLEFIELD/0"), TableIntent.Peek(null)), events)
        }
    }

    @Test fun handInsertionExcludesDraggedMembersAndUsesSamePreviewAndReleaseGap() {
        val f = Fixture(TableFixture.scene(TableFixture.play(board = 1, hand = 5)))
        val hand = f.scene.items.single { it.kind == TableItemKind.HAND && it.owner == "a" }
        val start = f.point("a/HAND/2")
        for ((x, left, right) in listOf(Triple(hand.bounds.x + 1, null, "a/HAND/0"),
            Triple(f.item("a/HAND/3").bounds.x - 2, "a/HAND/1", "a/HAND/3"),
            Triple(hand.bounds.right - 2, "a/HAND/4", null))) {
            f.events.clear()
            val end = TablePoint(x, hand.bounds.center.y)
            f.gestures.down(start, 0); f.gestures.move(end, 80)
            val destination = assertIs<TableDestination.Container>(f.gestures.preview.destination)
            assertEquals(org.asyncttwiz.protocol.IdentityGap(left, right), destination.handGap)
            val landing = tableDragLanding(f.scene, f.gestures.preview)
            assertEquals(hand.bounds, landing.hand)
            assertNotNull(landing.insertionX)
            assertTrue(landing.insertionX in hand.bounds.x..hand.bounds.right)
            if (left != null) assertTrue(landing.insertionX > start.x)
            assertTrue(landing.outlines.isEmpty())
            f.gestures.up(end, 100)
            assertEquals(destination, f.events.filterIsInstance<TableIntent.Move>().single().destination)
        }
    }

    @Test fun landingOutlinesTrackSnappedRowsMultiCardOffsetsAndContainers() {
        val scene = TableFixture.scene(TableFixture.play(board = 2))
        val scope = TableScope("a/BATTLEFIELD/0", listOf("a/BATTLEFIELD/0", "a/BATTLEFIELD/1"))
        for (row in 0..1) {
            val d = TableDestination.Grid("a/BATTLEFIELD", 5, row)
            val preview = TableGesturePreview(scope = scope, destination = d)
            val landing = tableDragLanding(scene, preview)
            assertEquals(2, landing.outlines.size)
            assertNotEquals(landing.outlines[0], landing.outlines[1])
            val field = scene.fields.last()
            assertTrue(landing.outlines.all { it.intersection(field.bounds) == it })
            if (row == 0) assertTrue(landing.outlines.all { it.intersection(field.landsBounds(true, scene.camera)) != null })
        }
        for (zone in listOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.STACK)) {
            val item = scene.items.first { it.kind == TableItemKind.PILE && it.zone == zone }
            val preview = TableGesturePreview(scope = scope, destination = TableDestination.Container(item.id, zone))
            assertEquals(2, tableDragLanding(scene, preview).outlines.size, "$zone")
        }
        assertTrue(tableDragLanding(scene, TableGesturePreview(scope = scope)).outlines.isEmpty())
    }

    @Test fun transformedLandsBandsStillTargetRowZero() {
        for (zoom in listOf(.65f, 1f, 2f)) for (pan in listOf(-12f, 0f, 12f)) for (owner in listOf("a", "b")) {
            val home = TablePresentation.scene(LiveTableFixture.game, LiveTableFixture.play(), "a", 892f, 412f, true)
            val anchor = home.fields.single { it.owner == owner }.landsBounds(owner == "a").center
            val scene = TablePresentation.scene(LiveTableFixture.game, LiveTableFixture.play(), "a", 892f, 412f, true,
                TableLayoutState(TableCamera(y = anchor.y - home.world!!.viewport.center.y + pan, zoom = zoom)))
            val start = scene.items.single { it.cardId == "a/HAND/2" }.visible.first().center
            for (field in scene.fields.filter { it.owner == owner }) {
                val band = assertNotNull(field.landsBounds(field.owner == "a", scene.camera).intersection(field.bounds))
                val end = TablePoint(field.bounds.right - 24, band.center.y)
                val events = mutableListOf<TableIntent>()
                val gesture = TableGestureCoordinator(events::add)
                gesture.update(scene, TableSelection(), true)
                gesture.down(start, 0); gesture.move(end, 80); gesture.up(end, 100)
                val destination = assertIs<TableDestination.Grid>(events.filterIsInstance<TableIntent.Move>().single().destination)
                assertEquals(0, destination.row)
                val geometry = TableAnnotationGeometry(scene, "a")
                assertEquals(0, Math.floorDiv(geometry.location(end)!!.position!!.y, 2))
            }
        }
    }

    @Test fun landsBandsAndAdjacentMainRowsUseTheDrawnGridForDropsAndLocationPings() {
        for ((width, height, landscape) in listOf(Triple(412f, 892f, false), Triple(892f, 412f, true),
            Triple(640f, 360f, true), Triple(892f, 364f, true))) {
            for (reviewing in listOf(false, true)) for (combat in listOf(false, true)) {
                val initial = LiveTableFixture.play()
                val play = if (combat) initial.copy(board = initial.board.copy(combat = org.asyncttwiz.protocol.CombatArea("combat"))) else initial
                val scene = TablePresentation.scene(LiveTableFixture.game, play, "a", width, height, landscape, reviewing = reviewing)
                val start = scene.items.single { it.cardId == "a/HAND/2" }.visible.first().center
                val geometry = TableAnnotationGeometry(scene, "a")
                for (field in scene.fields) for (row in 0..1) for (fraction in listOf(.1f, .5f, .9f)) {
                    val distance = (row + fraction) * field.rowHeight
                    val end = TablePoint(field.bounds.x + 5 * TablePresentation.GRID,
                        if (field.owner == "a") field.gridBounds.bottom - distance else field.gridBounds.y + distance)
                    val events = mutableListOf<TableIntent>()
                    val gesture = TableGestureCoordinator(events::add)
                    gesture.update(scene, TableSelection(), true)
                    gesture.down(start, 0); gesture.move(end, 80); gesture.up(end, 100)
                    val destination = assertIs<TableDestination.Grid>(events.filterIsInstance<TableIntent.Move>().single().destination,
                        "$width x $height, reviewing=$reviewing, combat=$combat, ${field.owner} row $row at $end")
                    assertEquals(field.containerId, destination.containerId)
                    assertEquals(row, destination.row)
                    val target = assertNotNull(geometry.location(end))
                    assertEquals(row, Math.floorDiv(target.position!!.y, 2))
                    val marker = assertNotNull(geometry.endpoint(target))
                    assertEquals(target, geometry.location(marker))
                    assertEquals(row == 0, field.landsBounds(field.owner == "a").contains(marker))
                }
            }
        }
    }

    @Test fun combatInsertionDragBindsOnlyTheRequestedOccurrenceAndCancellingDoesNotCreatePairs() {
        val f = TableStatesFixture
        val play = f.combat()
        for (landscape in listOf(false, true)) {
            val scene = TablePresentation.scene(f.game, play, f.a, if (landscape) 892f else 412f,
                if (landscape) 412f else 892f, landscape)
            val events = mutableListOf<TableIntent>()
            val gesture = TableGestureCoordinator(events::add)
            gesture.update(scene, TableSelection(), true)
            val occurrence = scene.items.single { it.cardId == f.id("attacker2") }
            val gap = scene.combatDrops.first { it.destination.pairId == null && it.destination.role == org.asyncttwiz.protocol.CombatRole.ATTACKER }
            gesture.down(occurrence.visible.first().center, 0)
            gesture.move(gap.bounds.center, 80); gesture.up(gap.bounds.center, 100)
            val move = events.filterIsInstance<TableIntent.Move>().single()
            assertEquals(listOf(occurrence.cardId), move.scope.cardIds)
            assertEquals(TableDestination.Combat(gap.destination), move.destination)
            assertEquals(f.source(f.a, "1/2").id, move.sources.getValue(occurrence.cardId!!).sourceId)
            assertEquals(2, play.board.combat!!.pairs.size)
            assertTrue(events.none { it is TableIntent.Rotate || it is TableIntent.Actions })
            events.clear()
            gesture.down(occurrence.visible.first().center, 200); gesture.move(gap.bounds.center, 280); gesture.cancel()
            assertTrue(events.isEmpty())
        }
    }

    @Test fun stackSingleTapOpensBubblesDoubleTapActionsHoldInspectsAndBadgeRequiresDoubleTap() {
        val f = TableStatesFixture
        val play = f.combat()
        val scene = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false)
        val events = mutableListOf<TableIntent>()
        val gesture = TableGestureCoordinator(events::add)
        gesture.update(scene, TableSelection(), true)
        val stack = scene.items.single { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }
        val top = scene.items.last { it.id.startsWith("stackDrawing/") && it.visible.isNotEmpty() }
        val badge = TablePoint(stack.bounds.x + 10, stack.bounds.y + 10)
        gesture.down(badge, 0); gesture.up(badge, 20)
        assertTrue(events.isEmpty())
        gesture.down(badge, 50); gesture.up(badge, 70)
        assertEquals(listOf<TableIntent>(TableIntent.DismissTapUi, TableIntent.Actions(stack.id, null)), events)
        events.clear()
        val point = TablePoint(top.bounds.center.x, top.bounds.bottom - 8)
        gesture.down(point, 100); gesture.up(point, 120); gesture.tick(500)
        assertEquals(listOf<TableIntent>(TableIntent.StackBubbles(top.cardId)), events)
        events.clear()
        gesture.down(point, 600); gesture.up(point, 620); gesture.down(point, 700); gesture.up(point, 720); gesture.tick(1100)
        assertEquals(top.id, events.filterIsInstance<TableIntent.Actions>().single().itemId)
        assertEquals(listOf<TableIntent>(TableIntent.StackBubbles(top.cardId), TableIntent.DismissTapUi,
            TableIntent.Actions(top.id, TableScope(top.cardId!!, listOf(top.cardId)))), events)
        events.clear()
        gesture.down(point, 1200); gesture.tick(1750); gesture.up(point, 1800)
        assertEquals(listOf<TableIntent>(TableIntent.Peek(top.cardId!!), TableIntent.Peek(null)), events)
    }
    @Test fun stackMarkerDragUsesOnlyVerticalDistanceAndNoRequestOnReturningOrCancelling() {
        val f = TableStatesFixture
        val play = f.combat()
        val entry = f.id("effect")
        val scene = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false)
        val anchor = scene.items.single { it.cardId == entry }.bounds
        val bubble = StackBubbleGeometry.create(entry, anchor, 412f, 892f, 1, 5)
        val events = mutableListOf<TableIntent>()
        val gesture = TableGestureCoordinator(events::add)
        gesture.update(scene.copy(stackBubbles = bubble), TableSelection(), true)
        val start = bubble.controls.last().bounds.center
        fun end(dx: Float = 0f, dy: Float = 56f) = TablePoint(start.x + dx, start.y + dy)
        gesture.down(start, 0); gesture.move(end(100f), 80)
        assertTrue(gesture.preview.marking); assertEquals(3, gesture.preview.markIndex)
        gesture.up(end(100f), 100)
        assertEquals(org.asyncttwiz.protocol.PlayAction.Stack(org.asyncttwiz.protocol.StackAction.MarkText(entry, 3)),
            events.filterIsInstance<TableIntent.Submit>().single().action)
        events.clear()
        gesture.down(start, 200); gesture.move(end(), 240); gesture.up(start, 280)
        gesture.down(start, 300); gesture.up(end(120f, 0f), 340)
        gesture.down(start, 400); gesture.move(end(), 440); gesture.cancel()
        assertTrue(events.isEmpty())
        gesture.down(start, 500); gesture.up(end(dy = -56f), 540)
        assertEquals(org.asyncttwiz.protocol.PlayAction.Stack(org.asyncttwiz.protocol.StackAction.MarkText(entry, null)),
            events.filterIsInstance<TableIntent.Submit>().single().action)
    }
    @Test fun markTextDownSelectsFirstEvenWithOneOptionAndFollowsReadingOrderAtBothDensities() {
        for (density in listOf(1f, 2.625f)) {
            assertMarkDrag(density, null, 1, 28f * density, 0)
            assertMarkDrag(density, null, 5, 56f * density, 1)
            assertMarkDrag(density, 0, 5, -28f * density, null)
            assertMarkDrag(density, null, 1, 11f * density, null)
        }
    }

    private fun assertMarkDrag(density: Float, selected: Int?, count: Int, dyPixels: Float, expected: Int?) {
        val f = TableStatesFixture
        val entry = f.id("effect")
        val scene = TablePresentation.scene(f.game, f.combat(), f.a, 412f, 892f, false)
        val bubble = StackBubbleGeometry.create(entry, scene.items.single { it.cardId == entry }.bounds,
            412f, 892f, selected, count)
        val events = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator(events::add, density = density)
        gestures.update(scene.copy(stackBubbles = bubble), TableSelection(), true)
        val start = bubble.controls.last().bounds.center
        val down = gestures.pointFromPixels(start.x * density, start.y * density)
        val end = gestures.pointFromPixels(start.x * density, start.y * density + dyPixels)
        gestures.down(down, 0); gestures.move(end, 80)
        assertTrue(gestures.preview.marking)
        assertEquals(expected, gestures.preview.markIndex)
        gestures.up(end, 100)
        val requests = events.filterIsInstance<TableIntent.Submit>()
        if (selected == expected) assertTrue(requests.isEmpty())
        else assertEquals(org.asyncttwiz.protocol.PlayAction.Stack(org.asyncttwiz.protocol.StackAction.MarkText(entry, expected)),
            requests.single().action)
    }

    @Test fun pointerPixelsKeepSlopDoubleTapRadiusAndValueStepInDpAtBothDensities() {
        for (density in listOf(1f, 2.625f)) {
            val scene = TableFixture.scene()
            val events = mutableListOf<TableIntent>()
            val gestures = TableGestureCoordinator(events::add, density = density)
            gestures.update(scene, TableSelection(), true)
            val start = scene.items.single { it.cardId == "a/BATTLEFIELD/0" }.visible.first().center
            fun point(dxDp: Float = 0f, dyDp: Float = 0f) = gestures.pointFromPixels((start.x + dxDp) * density, (start.y + dyDp) * density)
            gestures.down(point(), 0); gestures.move(point(7.9f), 40)
            assertNull(gestures.preview.scope)
            gestures.up(point(7.9f), 60); gestures.tick(340)
            assertEquals(1, events.filterIsInstance<TableIntent.Actions>().size)
            assertTrue(events.none { it is TableIntent.Move })
            events.clear()
            gestures.down(point(), 500); gestures.move(point(8.1f), 540)
            assertNotNull(gestures.preview.scope)
            gestures.up(point(8.1f), 560); gestures.tick(800)
            assertEquals(1, events.filterIsInstance<TableIntent.Move>().size)
            assertTrue(events.none { it is TableIntent.Actions || it is TableIntent.Rotate })
            events.clear()
            gestures.down(point(), 900); gestures.up(point(), 920)
            gestures.down(point(dyDp = 15f), 1000); gestures.up(point(dyDp = 15f), 1020); gestures.tick(1400)
            assertEquals(1, events.filterIsInstance<TableIntent.Rotate>().size)
            assertEquals(1, events.filterIsInstance<TableIntent.Actions>().size)
            assertEquals(1, events.count { it == TableIntent.DismissTapUi })
            assertTrue(events.none { it is TableIntent.Move })
            events.clear()
            val life = scene.items.single { it.id == "life/a" }.bounds.center
            gestures.down(gestures.pointFromPixels(life.x * density, life.y * density), 1500)
            val end = gestures.pointFromPixels(life.x * density, (life.y - 24f) * density)
            gestures.move(end, 1540)
            assertEquals(2, gestures.preview.delta)
            gestures.up(end, 1560)
            assertEquals(listOf<TableIntent>(TableIntent.PlayerDelta("a", null, 2)), events)
        }
    }
    @Test fun doubleTapAllowsFourHundredMillisAndThirtyTwoDpButNotBeyondEither() {
        for (density in listOf(1f, 2.625f)) for (delay in listOf(399L, 401L)) for (distance in listOf(31f, 33f)) {
            val card = TableItem("card/a", TableBounds(0f, 0f, 100f, 100f), "Card", "",
                kind = TableItemKind.CARD, cardId = "a")
            val events = mutableListOf<TableIntent>()
            val gestures = TableGestureCoordinator(events::add, density = density)
            gestures.update(TableScene(listOf(card), emptyList()), TableSelection(), true)
            val first = gestures.pointFromPixels(10 * density, 10 * density)
            val second = gestures.pointFromPixels((10 + distance) * density, 10 * density)
            gestures.down(first, 0); gestures.up(first, 20)
            val expected = delay <= 400 && distance <= 32
            assertEquals(expected, gestures.replacesTap(second, 20 + delay))
            gestures.down(second, 20 + delay); gestures.up(second, 40 + delay)
            assertEquals(if (expected) 1 else 0, events.filterIsInstance<TableIntent.Rotate>().size)
        }
    }
    @Test fun bubbleHitRegionsStayDistinctOnScreenAtAllCornersInBothOrientations() {
        for ((w, h) in listOf(412f to 892f, 892f to 412f)) for (x in listOf(0f, w - 28f)) for (y in listOf(0f, h - 40f)) {
            val bubble = StackBubbleGeometry.create("entry", TableBounds(x, y, 28f, 40f), w, h, null, 4)
            for (control in bubble.controls) {
                assertTrue(control.bounds.x >= 0 && control.bounds.right <= w && control.bounds.y >= 0 && control.bounds.bottom <= h)
                assertTrue(bubble.controls.filter { it != control }.none { it.bounds.intersection(control.bounds) != null })
            }
        }
    }
    @Test fun handDragTargetsViewerBattlefieldAtRealDeviceViewportsAndDoubleTapRotates() {
        for ((width, height, landscape) in listOf(Triple(412f, 892f, false), Triple(892f, 412f, true),
            Triple(360f, 640f, false), Triple(640f, 360f, true))) {
            val play = LiveTableFixture.play()
            val scene = TablePresentation.scene(LiveTableFixture.game, play, "a", width, height, landscape)
            val field = scene.fields.single { it.owner == "a" }
            assertTrue(field.bounds.height > 0)
            val hand = scene.items.single { it.cardId == "a/HAND/2" }
            assertFalse(hand.covered)
            val start = hand.visible.first().center
            // The right-edge midpoint is now the screen-fixed Stack, not empty battlefield.
            val destination = TablePoint(field.bounds.x + 5 * TablePresentation.GRID, field.bounds.center.y)
            val events = mutableListOf<TableIntent>()
            val coordinator = TableGestureCoordinator(events::add)
            coordinator.update(scene, TableSelection(), true)
            coordinator.down(start, 0); coordinator.move(destination, 80); coordinator.up(destination, 100)
            val move = events.filterIsInstance<TableIntent.Move>().single()
            assertEquals("a/BATTLEFIELD", (move.destination as TableDestination.Grid).containerId)
            assertEquals(listOf("a/HAND/2"), move.scope.cardIds)
            assertEquals("a/HAND", move.sources.getValue("a/HAND/2").containerId)
            assertTrue(events.none { it is TableIntent.Rotate || it is TableIntent.Actions })
            events.clear()
            val card = scene.items.single { it.cardId == "a/BATTLEFIELD/2" }.visible.first().center
            coordinator.down(card, 200); coordinator.up(card, 220)
            coordinator.down(card, 300); coordinator.up(card, 320); coordinator.tick(1000)
            assertEquals(listOf("a/BATTLEFIELD/2"), events.filterIsInstance<TableIntent.Rotate>().single().scope.cardIds)
            assertTrue(events.none { it is TableIntent.Move })
            assertEquals(1, events.filterIsInstance<TableIntent.Actions>().size)
            assertEquals(1, events.count { it == TableIntent.DismissTapUi })
        }
    }
    @Test fun battlefieldDragOntoHandFaceTargetsHandContainer() {
        val scene = TableFixture.scene(TableFixture.play(board = 1, hand = 2))
        val events = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator(events::add)
        gestures.update(scene, TableSelection(), true)
        val start = scene.items.single { it.cardId == "a/BATTLEFIELD/0" }.visible.first().center
        val end = scene.items.single { it.cardId == "a/HAND/1" }.visible.first().center
        gestures.down(start, 0); gestures.move(end, 80); gestures.up(end, 100)
        val d = assertIs<TableDestination.Container>(events.filterIsInstance<TableIntent.Move>().single().destination)
        assertEquals("a/HAND", d.id)
        assertEquals(PlayZone.HAND, d.zone)
        assertNotNull(d.handGap)
        assertTrue(events.none { it is TableIntent.Actions || it is TableIntent.Rotate })
    }
    private class Fixture(val scene: TableScene = TableFixture.scene(), initial: TableSelection = TableSelection(), writable: Boolean = true) {
        val events = mutableListOf<TableIntent>()
        var selection = initial
        val gestures = TableGestureCoordinator({ events += it; if (it is TableIntent.Selection) selection = it.selection })
        init { gestures.update(scene, selection, writable) }
        fun item(id: String = "a/BATTLEFIELD/0") = scene.items.single { it.cardId == id }
        fun point(id: String = "a/BATTLEFIELD/0") = item(id).visible.first().center
        fun tap(time: Long, point: TablePoint = point()) { gestures.down(point, time); gestures.up(point, time + 20) }
        fun refresh(writable: Boolean = true) = gestures.update(scene, selection, writable)
    }
    @Test fun normalSingleImmediatelyOpensCapturedActionsAndTimeoutDoesNotRepeatIt() {
        val f = Fixture(initial = TableSelection(setOf("a/BATTLEFIELD/0", "a/BATTLEFIELD/1")))
        f.tap(0); assertEquals(1, f.events.size); f.gestures.tick(419); assertEquals(1, f.events.size)
        f.gestures.update(f.scene, TableSelection(setOf("a/BATTLEFIELD/2")), true)
        f.gestures.tick(420)
        val action = f.events.single() as TableIntent.Actions
        assertEquals(listOf("a/BATTLEFIELD/0", "a/BATTLEFIELD/1"), action.scope!!.cardIds)
        assertTrue(f.events.none { it is TableIntent.Rotate })
    }
    @Test fun doubleTapRotatesOnceUsingTriggerOrientationAndFirstCapturedScope() {
        val f = Fixture(initial = TableSelection(setOf("a/BATTLEFIELD/0", "a/BATTLEFIELD/1")))
        f.tap(0); f.gestures.update(f.scene, TableSelection(setOf("a/BATTLEFIELD/2")), true); f.tap(100)
        f.gestures.tick(1000)
        assertEquals(1, f.events.filterIsInstance<TableIntent.Rotate>().size)
        assertEquals(listOf("a/BATTLEFIELD/0", "a/BATTLEFIELD/1"), f.events.filterIsInstance<TableIntent.Rotate>().single().scope.cardIds)
        assertTrue(f.events.filterIsInstance<TableIntent.Rotate>().single().tapped)
        assertIs<TableIntent.Actions>(f.events.first())
        assertEquals(TableIntent.DismissTapUi, f.events[1])
        assertTrue(f.events.none { it is TableIntent.Inspect })
    }
    @Test fun doubleUnselectedClearsSelectionButSingleUnselectedPreservesIt() {
        val f = Fixture(initial = TableSelection(setOf("a/BATTLEFIELD/1")))
        f.tap(0); f.gestures.tick(420); assertEquals(setOf("a/BATTLEFIELD/1"), f.selection.ids)
        f.events.clear(); f.tap(500); f.tap(600)
        assertTrue(f.selection.ids.isEmpty())
        assertEquals(listOf("a/BATTLEFIELD/0"), f.events.filterIsInstance<TableIntent.Rotate>().single().scope.cardIds)
    }
    @Test fun pingTapsRemainIndependentWithoutDoubleRecognizer() {
        val f = Fixture(initial = TableSelection(mode = TableMode.PING))
        f.tap(0); f.refresh(); f.tap(100); f.refresh(); f.gestures.tick(1000)
        assertEquals(2, f.events.filterIsInstance<TableIntent.Ping>().size)
        assertTrue(f.events.none { it is TableIntent.Actions || it is TableIntent.Rotate })
    }
    @Test fun selectDoubleTapTogglesCapturedGroupInTriggerDirectionAndReturnsToNormal() {
        for (tapped in listOf(false, true)) {
            val scene = TableFixture.scene().let { scene -> scene.copy(items = scene.items.map {
                if (it.cardId == "a/BATTLEFIELD/0") it.copy(tapped = tapped) else it
            }) }
            val f = Fixture(scene, TableSelection(setOf("a/BATTLEFIELD/0", "a/BATTLEFIELD/1"), TableMode.SELECT))
            f.tap(0); f.refresh(); f.tap(100); f.gestures.tick(1000)
            val rotate = f.events.filterIsInstance<TableIntent.Rotate>().single()
            assertEquals(listOf("a/BATTLEFIELD/0", "a/BATTLEFIELD/1"), rotate.scope.cardIds)
            assertEquals(!tapped, rotate.tapped)
            assertEquals(TableSelection(), f.selection)
            assertTrue(f.events.none { it is TableIntent.Actions })
        }
    }
    @Test fun selectDoubleTapUnselectedTogglesOnlyItAndRestoresOriginalSelection() {
        val initial = TableSelection(setOf("a/BATTLEFIELD/1"), TableMode.SELECT)
        val f = Fixture(initial = initial)
        f.tap(0); f.refresh(); f.tap(100)
        assertEquals(initial, f.selection)
        assertEquals(listOf("a/BATTLEFIELD/0"), f.events.filterIsInstance<TableIntent.Rotate>().single().scope.cardIds)
    }
    @Test fun selectDoubleTapNeverTapsStackEntriesEvenInMixedScope() {
        val scene = TableFixture.scene().let { scene -> scene.copy(items = scene.items.map {
            if (it.cardId == "a/BATTLEFIELD/1") it.copy(zone = PlayZone.STACK) else it
        }) }
        val stack = scene.items.first { it.kind == TableItemKind.CARD && it.zone == PlayZone.STACK }
        val selected = setOf("a/BATTLEFIELD/0", stack.cardId!!)
        val f = Fixture(scene, TableSelection(selected, TableMode.SELECT))
        f.tap(0); f.refresh(); f.tap(100)
        assertEquals(listOf("a/BATTLEFIELD/0"), f.events.filterIsInstance<TableIntent.Rotate>().single().scope.cardIds)
        val g = Fixture(scene, TableSelection(selected, TableMode.SELECT))
        g.tap(0, stack.visible.first().center); g.refresh(); g.tap(100, stack.visible.first().center)
        assertTrue(g.events.none { it is TableIntent.Rotate })
    }
    @Test fun holdConsumesTapAndCannotTurnIntoDragOrExposePile() {
        val f = Fixture(); val p = f.point()
        f.gestures.down(p, 0); f.gestures.tick(500)
        f.gestures.move(TablePoint(p.x + 150, p.y - 80), 600); f.gestures.up(p, 700); f.gestures.tick(1100)
        assertEquals(listOf<TableIntent>(TableIntent.Peek("a/BATTLEFIELD/0"), TableIntent.Peek(null)), f.events)
        val pile = f.scene.items.single { it.id == "a/LIBRARY" }.visible.first().center
        f.events.clear(); f.gestures.down(pile, 1200); f.gestures.tick(1800); f.gestures.up(pile, 1810)
        assertTrue(f.events.isEmpty())
    }
    @Test fun dragCapturesIdsDuringReflowAndEmitsOnlyOnceOnValidRelease() {
        val f = Fixture(initial = TableSelection(setOf("a/BATTLEFIELD/0", "a/BATTLEFIELD/1")))
        val start = f.point(); val dest = TablePoint(350f, f.scene.fields.last().bounds.center.y)
        f.gestures.down(start, 0); f.gestures.move(dest, 100)
        assertTrue(f.events.isEmpty()); assertNotNull(f.gestures.preview.destination)
        f.gestures.update(f.scene.copy(items = f.scene.items.reversed()), TableSelection(setOf("a/BATTLEFIELD/2")), true)
        f.gestures.up(dest, 200); f.gestures.tick(1000)
        val move = f.events.filterIsInstance<TableIntent.Move>().single()
        assertEquals(listOf("a/BATTLEFIELD/0", "a/BATTLEFIELD/1"), move.scope.cardIds)
        assertTrue(move.destination is TableDestination.Grid)
        assertEquals("a/BATTLEFIELD", move.sources.getValue("a/BATTLEFIELD/0").containerId)
        assertEquals(setOf("a/BATTLEFIELD/0", "a/BATTLEFIELD/1"), f.selection.ids)
        assertTrue(f.events.none { it is TableIntent.Actions || it is TableIntent.Rotate || it is TableIntent.Inspect })
    }
    @Test fun cancellationInvalidDropLostTargetAndDisconnectNeverMutateOrClearSelection() {
        for (variant in 0..3) {
            val f = Fixture(initial = TableSelection(setOf("a/BATTLEFIELD/1")))
            val p = f.point(); f.gestures.down(p, 0); f.gestures.move(TablePoint(p.x + 20, p.y - 20), 80)
            when (variant) {
                0 -> f.gestures.cancel()
                1 -> f.gestures.up(TablePoint(-100f, -100f), 100)
                2 -> { f.gestures.update(f.scene.copy(items = f.scene.items.filter { it.cardId != "a/BATTLEFIELD/0" }), f.selection, true); f.gestures.up(p, 100) }
                3 -> { f.refresh(false); f.gestures.up(TablePoint(350f, f.scene.fields.last().bounds.center.y), 100) }
            }
            f.gestures.tick(1000)
            assertTrue(f.events.isEmpty(), "$variant: ${f.events}"); assertEquals(setOf("a/BATTLEFIELD/1"), f.selection.ids)
        }
    }
    @Test fun secondPointerCancelsRectangleAndCardDragBeforePanPinch() {
        val f = Fixture(); val p = f.point()
        f.gestures.down(p, 0); f.gestures.move(TablePoint(p.x + 30, p.y), 50)
        f.gestures.transform(TablePoint(20f, 0f), 1.5f, p)
        f.gestures.up(p, 150); f.gestures.tick(1000)
        assertTrue(f.events.filterIsInstance<TableIntent.Camera>().all { it.camera.zoom == 1.5f })
        f.gestures.tick(1700)
        val settled = f.events.filterIsInstance<TableIntent.Camera>().last().camera
        assertEquals(f.scene.world!!.clamp(settled), settled)
        assertTrue(f.events.none { it is TableIntent.Move || it is TableIntent.Actions || it is TableIntent.Inspect })
        assertEquals(TableGesturePreview(), f.gestures.preview)
    }
    @Test fun emptyTapClearsAndRectangleReplacesByAnyVisibleOverlap() {
        val f = Fixture(initial = TableSelection(setOf("a/HAND/0")))
        val empty = TablePoint(350f, f.scene.fields.last().bounds.center.y)
        f.tap(0, empty); assertTrue(f.selection.ids.isEmpty()); f.refresh()
        val target = f.item().bounds
        val start = TablePoint(target.x - 8, target.y - 8); val end = TablePoint(target.x + 2, target.y + 2)
        f.gestures.down(start, 100); f.gestures.move(end, 150); f.gestures.up(end, 200)
        assertEquals(setOf("a/BATTLEFIELD/0"), f.selection.ids)
        assertTrue(f.events.none { it is TableIntent.Move })
    }
    @Test fun overflowHandCardDragsReorderWhileBackgroundStillScrolls() {
        val f = Fixture(TableFixture.scene(TableFixture.play(hand = 40)))
        val p = f.point("a/HAND/2")
        f.gestures.down(p, 0); f.gestures.move(TablePoint(p.x - 60, p.y), 80); f.gestures.up(TablePoint(p.x - 60, p.y), 100)
        assertTrue(f.events.none { it is TableIntent.Scroll || it is TableIntent.Actions })
        assertEquals(PlayZone.HAND, assertIs<TableDestination.Container>(f.events.filterIsInstance<TableIntent.Move>().single().destination).zone)
        f.events.clear(); f.gestures.down(p, 200)
        // Keep this a vertical gesture as battlefield height changes with the viewport.
        val dest = TablePoint(p.x, f.scene.fields.last().bounds.center.y)
        f.gestures.move(dest, 250); f.gestures.up(dest, 300)
        assertEquals("a/HAND/2", f.events.filterIsInstance<TableIntent.Move>().single().scope.contextId)
        f.events.clear()
        val hand = f.scene.items.single { it.kind == TableItemKind.HAND && it.owner == "a" }.bounds
        val background = TablePoint(hand.x + 1, hand.y + 1)
        val end = TablePoint(background.x - 60, background.y)
        f.gestures.down(background, 400); f.gestures.move(end, 450); f.gestures.up(end, 470)
        assertTrue(f.events.any { it is TableIntent.Scroll })
        assertTrue(f.events.none { it is TableIntent.Move || it is TableIntent.Actions })
        f.events.clear()
        f.gestures.transform(TablePoint(-20f, 0f), 1f, p)
        assertEquals(TableIntent.Scroll("a/HAND", 20f), f.events.single())
    }
    @Test fun librarySingleTapOpensActionsAfterDoubleWindowAndDragBindsActualTop() {
        val f = Fixture()
        val pile = f.scene.items.single { it.id == "a/LIBRARY" }; val p = pile.visible.first().center
        f.tap(0, p)
        assertTrue(f.events.isEmpty())
        f.gestures.tick(420)
        assertEquals(TableIntent.Actions(pile.id, null), f.events.single())
        f.events.clear(); f.gestures.down(p, 450)
        val dest = f.scene.fields.last().bounds.center
        f.gestures.move(dest, 500); f.gestures.up(dest, 550)
        assertEquals(listOf("a/LIBRARY/0"), f.events.filterIsInstance<TableIntent.Move>().single().scope.cardIds)
        assertTrue(f.events.filterIsInstance<TableIntent.Move>().single().sources.getValue("a/LIBRARY/0").libraryTop)
    }

    @Test fun libraryDoubleTapDrawsOnlyOwnCapturedTopAndDoesNotOpenActions() {
        for (owner in listOf("a", "b")) for (writable in listOf(true, false)) for (mode in listOf(TableMode.NORMAL, TableMode.SELECT)) {
            val f = Fixture(initial = TableSelection(setOf("a/BATTLEFIELD/0"), mode), writable = writable)
            val pile = f.scene.items.single { it.id == "$owner/LIBRARY" }
            val p = pile.visible.first().center
            f.tap(0, p); f.tap(100, p); f.gestures.tick(1000)
            assertEquals(if (owner == "a" && writable)
                listOf<TableIntent>(TableIntent.Draw("a/LIBRARY", "a/HAND", "a/LIBRARY/0")) else emptyList(), f.events)
        }
    }

    @Test fun stackDoubleTapAndAccessibilityNeverProduceRotateIncludingMixedSelections() {
        val scene = TableFixture.scene()
        val card = scene.items.first { it.kind == TableItemKind.CARD && it.visible.isNotEmpty() }
        val stackCard = card.copy(zone = PlayZone.STACK)
        val f = Fixture(scene.copy(items = scene.items.map { if (it.id == card.id) stackCard else it }))
        val p = stackCard.visible.first().center
        f.tap(0, p); f.tap(100, p); f.gestures.tick(1000)
        f.gestures.rotate(stackCard, true)
        assertTrue(f.events.none { it is TableIntent.Rotate })
        val other = scene.items.first { it.kind == TableItemKind.CARD && it.cardId != card.cardId }
        f.gestures.update(f.scene, TableSelection(setOf(card.cardId!!, other.cardId!!)), true)
        f.gestures.rotate(other, true)
        assertEquals(listOf(other.cardId), f.events.filterIsInstance<TableIntent.Rotate>().single().scope.cardIds)
    }
    @Test fun accessibleAlternativesUseSameCapturedScopeAndReadOnlyGate() {
        val f = Fixture(initial = TableSelection(setOf("a/BATTLEFIELD/0", "a/BATTLEFIELD/1")))
        val item = f.item(); f.gestures.actions(item); f.gestures.inspect(item); f.gestures.rotate(item, true)
        f.gestures.accessibleMove(item, TableDestination.Container("a/HAND", PlayZone.HAND))
        assertEquals(2, f.events.filterIsInstance<TableIntent.Actions>().single().scope!!.cardIds.size)
        assertEquals(2, f.events.filterIsInstance<TableIntent.Move>().single().scope.cardIds.size)
        f.events.clear(); f.refresh(false); f.gestures.rotate(item, true)
        f.gestures.accessibleMove(item, TableDestination.Container("a/HAND", PlayZone.HAND)); assertTrue(f.events.isEmpty())
        f.gestures.inspect(item); assertTrue(f.events.single() is TableIntent.Inspect)
    }
    @Test fun lifeDeltaIsRelativeOneReleaseRequestAndNoTapOnCancelZeroOrDisconnect() {
        for (variant in 0..3) {
            val f = Fixture(); val p = f.scene.items.single { it.id == "life/a" }.visible.first().center
            f.gestures.down(p, 0); f.gestures.move(TablePoint(p.x, p.y - 48), 100)
            assertTrue(f.events.isEmpty()); assertEquals(4, f.gestures.preview.delta)
            when (variant) {
                0 -> f.gestures.up(TablePoint(p.x, p.y - 48), 150)
                1 -> f.gestures.cancel()
                2 -> f.gestures.up(p, 150)
                3 -> { f.refresh(false); f.gestures.up(TablePoint(p.x, p.y - 48), 150) }
            }
            assertEquals(if (variant == 0) listOf(TableIntent.PlayerDelta("a", null, 4)) else emptyList(), f.events)
        }
    }

    @Test fun rapidTransformFramesAccumulateWithoutWaitingForComposition() {
        val f = Fixture(); val point = f.scene.fields.last().bounds.center
        f.gestures.transform(TablePoint(-10f, 0f), 1f, point)
        f.gestures.transform(TablePoint(-10f, 0f), 1f, point)
        assertEquals(f.scene.world!!.resist(TableCamera(20f, 0f)),
            f.events.filterIsInstance<TableIntent.Camera>().last().camera)
    }

    @Test fun ordinaryPileCapacityRejectsWholeSelectionWithoutPartialMove() {
        val p = TableFixture.play(board = 5)
        val placements = p.board.cards.filter { it.containerId.endsWith("BATTLEFIELD") }.associate { c ->
            val n = c.id.last().digitToInt()
            c.id to if (n < 3) TablePlacement(0, 0, n) else TablePlacement(n - 2, 0)
        }
        val scene = TableFixture.scene(p, placements = placements)
        val f = Fixture(scene, TableSelection(setOf("a/BATTLEFIELD/3", "a/BATTLEFIELD/4")))
        val target = f.point("a/BATTLEFIELD/2"); val start = f.point("a/BATTLEFIELD/3")
        f.gestures.down(start, 0); f.gestures.move(target, 100)
        assertNull(f.gestures.preview.destination)
        f.gestures.up(target, 150); assertTrue(f.events.isEmpty())
        assertEquals(setOf("a/BATTLEFIELD/3", "a/BATTLEFIELD/4"), f.selection.ids)
    }

    @Test fun twoFingerCompactSidebarPanIsPersonalNotABoardMove() {
        val scene = TablePresentation.scene(TableFixture.game, TableFixture.play(), "a", 640f, 260f, true,
            TableLayoutState(TableCamera(zoom = 2f)))
        val f = Fixture(scene)
        val sidebar = scene.viewports.single { it.id == "sidebar" }
        f.gestures.transform(TablePoint(0f, -60f), 1f, sidebar.bounds.center)
        assertEquals(TableIntent.Scroll("sidebar", 60f, true), f.events.single())
        assertTrue(f.events.none { it is TableIntent.Camera || it is TableIntent.Move })
    }
}
