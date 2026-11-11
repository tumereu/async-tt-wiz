package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.assertEquals

/** TBL-104 (owner 2026-11-06): what Inspect lists under a battlefield card: only what is changed on it or added to it. */
class InspectChangesTest {
    private val f = TableStatesFixture

    // TBL-104: counters (owner, 2026-11-11: every counter in words, loyalty too), damage, what it is attached to and the enter
    // mark, in that order; an unchanged card and a card off the battlefield have none
    @Test fun aCardListsItsCountersDamageAttachmentAndEnterMark() {
        val creature = f.source(f.a, "1/3")
        val other = f.source(f.a, "1/2")
        val plain = f.source(f.b, "2/3")
        val inHand = f.initial.board.cards.first { it.containerId == f.id("a/HAND") }
        val board = f.initial.board
        val cards = board.cards.map { when (it.id) {
            creature.id -> it.copy(damage = "1", enteredTurn = board.turn.number, typedCounters = listOf(
                TypedCounter("+1/+1", 2, 1), TypedCounter("loyalty", 3, 2), TypedCounter("charge", 1, 3)))
            other.id -> it.copy(attachedTo = MtgRef.Card(creature.id, 0))
            else -> it
        } }
        val play = tableBoard(f.initial.copy(board = board.copy(cards = cards)))
        val shown = cards.filter { it.id in setOf(creature.id, other.id, plain.id, inHand.id) }
        val name = f.initial.definitions.single { it.digest == creature.definition }.definition.faces.single { it.faceId == creature.faceId }.name
        assertEquals(mapOf(
            creature.id to listOf("+1/+1 counter ×2", "loyalty counter ×3", "charge counter", "1 damage", "Entered this turn"),
            other.id to listOf("Attached to $name"),
        ), InspectChanges.of(play, f.a, shown, emptyMap()).mapValues { (_, lines) -> lines.map { it.text } })
    }

    // ZON-091, TBL-104: a card in exile lists its counters (a suspended card's time counters, CR 702.62a), nothing else
    @Test fun aCardInExileListsItsCounters() {
        val board = f.initial.board
        val exile = f.id("a/EXILE")
        val card = board.cards.first { it.containerId == f.id("a/HAND") }
        val cards = board.cards.map { if (it.id == card.id) it.copy(containerId = exile, typedCounters = listOf(
            TypedCounter("time", 3, 1))) else it }
        val containers = board.containers.map { when (it.id) {
            exile -> it.copy(cards = it.cards + card.id)
            card.containerId -> it.copy(cards = it.cards - card.id)
            else -> it
        } }
        val play = tableBoard(f.initial.copy(board = board.copy(cards = cards, containers = containers)))
        assertEquals(mapOf(card.id to listOf("time counter ×3")),
            InspectChanges.of(play, f.a, cards.filter { it.id == card.id }, emptyMap()).mapValues { (_, lines) -> lines.map { it.text } })
    }
}
