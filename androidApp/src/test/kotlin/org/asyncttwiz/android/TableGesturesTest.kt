package org.asyncttwiz.android

import org.asyncttwiz.protocol.PlayZone
import org.junit.Test
import kotlin.test.*

class TableGesturesTest {
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

    @Test fun stackSingleTapOpensBubblesDoubleTapActionsHoldInspectsAndBadgeOpensContents() {
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
        assertEquals(listOf<TableIntent>(TableIntent.Actions(stack.id, null)), events)
        events.clear()
        val point = TablePoint(top.bounds.center.x, top.bounds.bottom - 8)
        gesture.down(point, 100); gesture.up(point, 120); gesture.tick(500)
        assertEquals(listOf<TableIntent>(TableIntent.StackBubbles(top.cardId)), events)
        events.clear()
        gesture.down(point, 600); gesture.up(point, 620); gesture.down(point, 700); gesture.up(point, 720); gesture.tick(1100)
        assertEquals(top.id, events.filterIsInstance<TableIntent.Actions>().single().itemId)
        assertTrue(events.none { it is TableIntent.Rotate || it is TableIntent.StackBubbles })
        events.clear()
        gesture.down(point, 1200); gesture.tick(1750); gesture.up(point, 1800)
        assertEquals(listOf<TableIntent>(TableIntent.Inspect(top.cardId!!)), events)
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
        fun end(dx: Float = 0f, dy: Float = -56f) = TablePoint(start.x + dx, start.y + dy)
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
        gesture.down(start, 500); gesture.up(end(dy = 56f), 540)
        assertEquals(org.asyncttwiz.protocol.PlayAction.Stack(org.asyncttwiz.protocol.StackAction.MarkText(entry, null)),
            events.filterIsInstance<TableIntent.Submit>().single().action)
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
        // Keep this a vertical gesture as battlefield height changes with the viewport.
        val dest = TablePoint(p.x, f.scene.fields.last().bounds.center.y)
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
