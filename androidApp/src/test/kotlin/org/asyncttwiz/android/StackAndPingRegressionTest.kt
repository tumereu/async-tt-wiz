package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class StackAndPingRegressionTest {
    @Test fun activePingNumbersCloseGapsWithoutChangingRecordedHistory() {
        val f = BasicTableFixture()
        val group = f.id(700)
        val reference = ReferencedEventContext(EventReference(f.root.id, f.root.currentNodeId), "context")
        val a = PingTarget(PingTargetKind.CARD, f.id(40))
        val b = PingTarget(PingTargetKind.CARD, f.id(41))
        fun record(n: Int, target: PingTarget, from: PingPresence?, to: PingPresence?, groupId: String = group) =
            RecordedPingChange(AnnotationRecording(f.root.id, f.root.root.branchId, f.root.currentNodeId,
                f.id(n), groupId, f.id(n + 100), f.actor), reference, target, from, to)
        val first = PingPresence(a, "5", f.id(701)) // Legacy ordinals need not be contiguous.
        val second = PingPresence(b, "8", f.id(702))
        val added = EventAnnotationState(pings = listOf(record(701, a, null, first), record(702, b, null, second)))
        assertEquals(listOf("1", "2"), numberedActivePings(added, group).map { it.to!!.ordinal })
        val removed = added.copy(pings = added.pings + record(703, a, first, null))
        assertEquals(listOf(b), numberedActivePings(removed, group).map { it.target })
        assertEquals("1", numberedActivePings(removed, group).single().to!!.ordinal)
        assertEquals("8", EventAnnotations.activePings(removed, group).single().to!!.ordinal)
        val readded = removed.copy(pings = removed.pings + record(704, a, null, first.copy(ordinal = "9")))
        assertEquals(listOf(b, a), numberedActivePings(readded, group).map { it.target })
        assertEquals(listOf("1", "2"), numberedActivePings(readded, group).map { it.to!!.ordinal })
        assertTrue(numberedActivePings(readded, f.id(800)).isEmpty())
        assertEquals(listOf("5", "8"), added.pings.map { it.to!!.ordinal })
    }

    @Test fun onlyTopEntryBubbleActionsAdvanceFocusIncludingDelayedPrediction() {
        for (kind in listOf("battlefield", "graveyard", "remove", "menuRemove", "menuDestination", "drag")) {
            val f = BasicTableFixture()
            f.connect()
            try {
                val setup = TableToolkitController(f.actor, { f.play }, { true }, f.lobby::gameplay, playBatch = f.lobby::gameplayBatch)
                assertTrue(f.move(40, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 1), true))
                assertTrue(setup.addEffects(listOf(f.id(40))))
                assertTrue(setup.addEffects(listOf(f.id(40))))
                val order = CombatStackRules.stack(f.play.board).cards
                assertEquals(2, order.size)
                val top = order.first()
                var published = f.play
                val ui = TableToolkitController(f.actor, { published }, { true }, f.lobby::gameplay, playBatch = f.lobby::gameplayBatch)
                ui.showStackBubbles(top)
                val intent = when (kind) {
                    "battlefield" -> TableIntent.StackDestination(top, PlayZone.BATTLEFIELD, fromBubble = true)
                    "graveyard" -> TableIntent.StackDestination(top, PlayZone.GRAVEYARD, fromBubble = true)
                    "remove" -> TableIntent.StackBubbleRemove(top)
                    "menuRemove" -> TableIntent.Submit(StackMenuActions.remove(top), null)
                    "menuDestination" -> TableIntent.StackDestination(top, PlayZone.BATTLEFIELD)
                    else -> TableIntent.Move(TableScope(top, listOf(top)), TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 1, 1),
                        TablePresentation.scene(f.game, published, f.actor, 412f, 892f, false).sources)
                }
                val advance = kind in listOf("battlefield", "graveyard", "remove")
                assertEquals(if (advance) top else null, stackBubbleAdvanceEntry(intent, published.board))
                assertTrue(ui.handle(intent), kind)
                published = f.play
                ui.refreshStackBubbles()
                assertEquals(order.drop(1), CombatStackRules.stack(published.board).cards, kind)
                assertEquals(if (advance) order[1] else null, ui.stackBubbleEntry, kind)
                assertNull(stackBubbleAdvanceEntry(TableIntent.StackBubbleRemove(top), published.board))
                ui.showStackBubbles(null)
                ui.refreshStackBubbles()
                assertNull(ui.stackBubbleEntry)
                assertTrue(ui.handle(TableIntent.StackBubbleRemove(order[1])))
                published = f.play
                ui.refreshStackBubbles()
                assertTrue(CombatStackRules.stack(published.board).cards.isEmpty())
                assertNull(ui.stackBubbleEntry)
            } finally { f.lobby.close(); f.worker.drain() }
        }
    }

    @Test fun manageStackTogglesArePredictedAndBubbleMarkReplacesTheSet() {
        val f = BasicTableFixture()
        val definition = f.definition.copy(faces = f.definition.faces.map { it.copy(rulesText = "First\nSecond") })
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        f.connect(f.root.copy(definitions = listOf(submitted), board = f.root.board.copy(cards = f.root.board.cards.map {
            it.copy(definition = submitted.digest) })))
        try {
            assertTrue(f.move(40, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 1), true))
            val ui = TableToolkitController(f.actor, { f.play }, { true }, f.lobby::gameplay, playBatch = f.lobby::gameplayBatch)
            assertTrue(ui.addEffects(listOf(f.id(40))))
            val entry = CombatStackRules.stack(f.play.board).cards.single()
            assertTrue(ui.handle(TableIntent.Submit(StackMenuActions.toggleMark(entry, 0), null)))
            assertTrue(ui.handle(TableIntent.Submit(StackMenuActions.toggleMark(entry, 1), null)))
            assertEquals(setOf(0, 1), f.play.board.stackTextMarks[entry])
            assertTrue(ui.handle(TableIntent.Submit(StackMenuActions.mark(entry, 0), null)))
            assertEquals(setOf(0), f.play.board.stackTextMarks[entry])
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun multiMarkHandleReplacesEvenWhenDraggedBackToItsStartingOption() {
        val f = TableStatesFixture
        val entry = f.id("effect")
        val scene = TablePresentation.scene(f.game, f.combat(), f.a, 412f, 892f, false)
        val bubble = StackBubbleGeometry.create(entry, scene.items.single { it.cardId == entry }.bounds,
            412f, 892f, 0, 3).copy(multipleMarks = true)
        val events = mutableListOf<TableIntent>()
        val gesture = TableGestureCoordinator(events::add)
        gesture.update(scene.copy(stackBubbles = bubble), TableSelection(), true)
        val start = bubble.controls.last().bounds.center
        val end = TablePoint(start.x, start.y + 28)
        gesture.down(start, 0); gesture.move(end, 80); gesture.up(start, 100)
        assertEquals(PlayAction.Stack(StackAction.MarkText(entry, 0)), events.filterIsInstance<TableIntent.Submit>().single().action)
    }

    @Test fun cardMenuHasNoPileMovesEvenWhenAnEligiblePileExists() {
        val f = BasicTableFixture()
        f.connect()
        try {
            assertTrue(f.move(40, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 1), true))
            assertTrue(f.move(41, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 1, 1), true))
            val controls = basicMoveTools(f.play, emptyMap(), true, TableScope(f.id(40), listOf(f.id(40))),
                emptyMap(), { false }, {})
            assertTrue(controls.none { it.name.startsWith("Move to pile") })
            assertTrue(controls.any { it.name == "Move to Stack" })
        } finally { f.lobby.close(); f.worker.drain() }
    }
}
