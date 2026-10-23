package org.asyncttwiz.android

import org.asyncttwiz.protocol.PlayZone
import org.junit.Test
import kotlin.test.*

class TableGesturesTest {
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
            val destination = TablePoint(field.bounds.right - 24f, field.bounds.center.y)
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
            assertTrue(events.none { it is TableIntent.Move || it is TableIntent.Actions })
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
        assertEquals(TableDestination.Container("a/HAND", PlayZone.HAND), events.filterIsInstance<TableIntent.Move>().single().destination)
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
    @Test fun normalSingleWaitsDoubleDecisionAndOnlyOpensCapturedActions() {
        val f = Fixture(initial = TableSelection(setOf("a/BATTLEFIELD/0", "a/BATTLEFIELD/1")))
        f.tap(0); assertTrue(f.events.isEmpty()); f.gestures.tick(299); assertTrue(f.events.isEmpty())
        f.gestures.update(f.scene, TableSelection(setOf("a/BATTLEFIELD/2")), true)
        f.gestures.tick(300)
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
        assertTrue(f.events.none { it is TableIntent.Actions || it is TableIntent.Inspect })
    }
    @Test fun doubleUnselectedClearsSelectionButSingleUnselectedPreservesIt() {
        val f = Fixture(initial = TableSelection(setOf("a/BATTLEFIELD/1")))
        f.tap(0); f.gestures.tick(301); assertEquals(setOf("a/BATTLEFIELD/1"), f.selection.ids)
        f.events.clear(); f.tap(400); f.tap(500)
        assertTrue(f.selection.ids.isEmpty())
        assertEquals(listOf("a/BATTLEFIELD/0"), f.events.filterIsInstance<TableIntent.Rotate>().single().scope.cardIds)
    }
    @Test fun selectAndPingHaveIndependentImmediateTapsWithoutDoubleRecognizer() {
        for (mode in listOf(TableMode.SELECT, TableMode.PING)) {
            val f = Fixture(initial = TableSelection(mode = mode))
            f.tap(0); f.refresh(); f.tap(100); f.refresh(); f.gestures.tick(1000)
            assertEquals(2, f.events.size)
            assertTrue(f.events.none { it is TableIntent.Actions || it is TableIntent.Rotate })
            if (mode == TableMode.SELECT) assertTrue(f.selection.ids.isEmpty()) else assertEquals(2, f.events.filterIsInstance<TableIntent.Ping>().size)
        }
    }
    @Test fun holdConsumesTapAndCannotTurnIntoDragOrExposePile() {
        val f = Fixture(); val p = f.point()
        f.gestures.down(p, 0); f.gestures.tick(500)
        f.gestures.move(TablePoint(p.x + 150, p.y - 80), 600); f.gestures.up(p, 700); f.gestures.tick(1100)
        assertEquals(listOf<TableIntent>(TableIntent.Inspect("a/BATTLEFIELD/0")), f.events)
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
        assertEquals(1.5f, f.events.filterIsInstance<TableIntent.Camera>().single().camera.zoom)
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
    @Test fun overflowHandHorizontalPanIsNotMoveButVerticalDragIs() {
        val f = Fixture(TableFixture.scene(TableFixture.play(hand = 40)))
        val p = f.point("a/HAND/2")
        f.gestures.down(p, 0); f.gestures.move(TablePoint(p.x - 60, p.y), 80); f.gestures.up(TablePoint(p.x - 60, p.y), 100)
        assertTrue(f.events.any { it is TableIntent.Scroll }); assertTrue(f.events.none { it is TableIntent.Move || it is TableIntent.Actions })
        f.events.clear(); f.gestures.down(p, 200)
        val dest = f.scene.fields.last().bounds.center
        f.gestures.move(dest, 250); f.gestures.up(dest, 300)
        assertEquals("a/HAND/2", f.events.filterIsInstance<TableIntent.Move>().single().scope.contextId)
    }
    @Test fun pileTapIsImmediateContentsButDragBindsActualLibraryTopNotNextTop() {
        val f = Fixture()
        val pile = f.scene.items.single { it.id == "a/LIBRARY" }; val p = pile.visible.first().center
        f.tap(0, p)
        assertEquals(TableIntent.Actions(pile.id, null), f.events.single())
        f.events.clear(); f.gestures.down(p, 100)
        val dest = f.scene.fields.last().bounds.center
        f.gestures.move(dest, 200); f.gestures.up(dest, 250)
        assertEquals(listOf("a/LIBRARY/0"), f.events.filterIsInstance<TableIntent.Move>().single().scope.cardIds)
        assertTrue(f.events.filterIsInstance<TableIntent.Move>().single().sources.getValue("a/LIBRARY/0").libraryTop)
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
            assertTrue(f.events.isEmpty()); assertEquals(2, f.gestures.preview.delta)
            when (variant) {
                0 -> f.gestures.up(TablePoint(p.x, p.y - 48), 150)
                1 -> f.gestures.cancel()
                2 -> f.gestures.up(p, 150)
                3 -> { f.refresh(false); f.gestures.up(TablePoint(p.x, p.y - 48), 150) }
            }
            assertEquals(if (variant == 0) listOf(TableIntent.PlayerDelta("a", null, 2)) else emptyList(), f.events)
        }
    }

    @Test fun rapidTransformFramesAccumulateWithoutWaitingForComposition() {
        val f = Fixture(); val point = f.scene.fields.last().bounds.center
        f.gestures.transform(TablePoint(-10f, 0f), 1f, point)
        f.gestures.transform(TablePoint(-10f, 0f), 1f, point)
        assertEquals(20f, f.events.filterIsInstance<TableIntent.Camera>().last().camera.x)
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
        val scene = TableFixture.scene(TableFixture.play(), true, TableLayoutState(TableCamera(zoom = 2f)))
        val f = Fixture(scene)
        val sidebar = scene.viewports.single { it.id == "sidebar" }
        f.gestures.transform(TablePoint(0f, -60f), 1f, sidebar.bounds.center)
        assertEquals(TableIntent.Scroll("sidebar", 60f, true), f.events.single())
        assertTrue(f.events.none { it is TableIntent.Camera || it is TableIntent.Move })
    }
}
