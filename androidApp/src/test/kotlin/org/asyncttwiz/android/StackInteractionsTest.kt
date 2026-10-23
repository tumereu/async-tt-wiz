package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class StackInteractionsTest {
    @Test fun realSessionAutomaticallyShowsNewEntriesAndBubblesUseOneInvocationWithOrdinaryRequests() {
        val f = BasicTableFixture()
        f.connect()
        val toolkit = TableToolkitController(f.actor, { f.play }, { true }, f.lobby::gameplay)
        val id = f.id(40)
        val scope = TableScope(id, listOf(id))
        assertTrue(toolkit.handle(TableIntent.Move(scope, TableDestination.Container(f.container(PlayZone.STACK), PlayZone.STACK),
            BasicTableController.sources(f.play, listOf(id)))))
        assertEquals(id, toolkit.stackBubbleEntry)
        assertTrue(toolkit.handle(TableIntent.StackDestination(id, PlayZone.BATTLEFIELD)))
        assertEquals(BoardPosition(0, 1), f.card(40).placement!!.position)
        assertNull(toolkit.stackBubbleEntry)
        assertTrue(toolkit.addEffects(listOf(id)))
        val effect = f.play.board.stackEffects.single()
        assertEquals(effect.id, toolkit.stackBubbleEntry)
        val before = f.commands().size
        assertTrue(toolkit.handle(TableIntent.StackDestination(effect.id, PlayZone.GRAVEYARD)))
        val commands = f.commands().drop(before)
        assertEquals(1, commands.map { it.invocationId }.distinct().size)
        assertIs<StackAction.RemoveEffect>((commands.first().action as GameAction.Play).action.let { (it as PlayAction.Stack).action })
        assertTrue(f.play.board.stackEffects.isEmpty())
        assertEquals(f.container(PlayZone.GRAVEYARD), f.card(40).containerId)
        assertTrue(commands.any { (it.action as GameAction.Play).action is PlayAction.MoveCard })
        assertEquals(1, commands.count { (it.action as GameAction.Play).action is PlayAction.ResetCorner })
        assertEquals(1, commands.count { (it.action as GameAction.Play).action is PlayAction.ClearCardCounter })
        assertTrue(f.commands().all(GameWire::valid))
    }
    @Test fun markerHistoryHasReadableLiteralTextAndInspectionRetainsOccurrenceNotSourceScope() {
        val f = BasicTableFixture()
        val definition = f.definition.copy(faces = f.definition.faces.map { it.copy(rulesText = "First paragraph\nSecond paragraph") })
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        val initial = f.root.copy(definitions = listOf(submitted), board = f.root.board.copy(cards = f.root.board.cards.map { it.copy(definition = submitted.digest) }))
        f.connect(initial)
        val toolkit = TableToolkitController(f.actor, { f.play }, { true }, f.lobby::gameplay)
        val id = f.id(40)
        toolkit.handle(TableIntent.Move(TableScope(id, listOf(id)), TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 1),
            BasicTableController.sources(f.play, listOf(id))))
        toolkit.addEffects(listOf(id))
        val entry = f.play.board.stackEffects.single().id
        val before = f.play
        assertTrue(toolkit.handle(TableIntent.Submit(PlayAction.Stack(StackAction.MarkText(entry, 1)), null)))
        assertEquals(mapOf(entry to 1), f.play.board.stackTextMarks)
        val change = GameplayRules.change(before, f.actor, f.commands().last())
        val text = historyText(change.mutation!!, before, f.actor, mapOf(f.actor to "bob"))
        assertTrue(text.description.startsWith("bob marked")); assertTrue(text.description.endsWith("2 — Second paragraph"))
        toolkit.inspect(entry)
        assertEquals(entry, toolkit.inspection!!.stackEntryId)
        toolkit.inspect(id)
        assertNull(toolkit.inspection!!.stackEntryId)
        assertEquals(mapOf(entry to 1), f.play.board.stackTextMarks)
    }
}
