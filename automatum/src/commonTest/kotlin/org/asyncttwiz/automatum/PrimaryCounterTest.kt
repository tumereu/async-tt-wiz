@file:OptIn(arcanum.engine.state.ArcanumTestSetup::class)

package org.asyncttwiz.automatum

import arcanum.api.CounterKind
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.state.CounterEntry
import arcanum.engine.state.GameState
import arcanum.engine.state.setup
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * TBL-021 (owner, 2026-11-11): a permanent without P/T shows the counter its rules make primary as its corner number (a
 * planeswalker's loyalty, a battle's defense, a Saga's lore); with P/T such a counter is one of its bubbles, as every other
 * counter is (counters as visuals).
 */
class PrimaryCounterTest {
    init {
        Cards.invent("Ash Sage", "{2}{R}", "Legendary Planeswalker — Chandra", "[+1]: You gain 1 life.", loyalty = "3")
        Cards.invent("Ember Chronicle", "{1}{R}", "Enchantment — Saga", "(As this Saga enters and after your draw step, add a lore counter.)\nI — You gain 1 life.\nII — You gain 1 life.\nIII — You gain 1 life.")
        Cards.invent("Cinder Squire", "{1}{R}", "Creature — Human", "", "2/2")
    }
    private val table = EngineTable()

    private fun computed(card: String, vararg counters: CounterEntry): ComputedCard {
        val state: GameState = table.state()
        val id = table.obj(card)
        state.setup.objects[id] = state.objects.getValue(id).copy(counters = counters.toList())
        val board = ComputedBoard.of(table.board(state, table.alice), table.base().definitions, table.definitions, table.alice, state,
            Engine.view(state))
        return board.cards.getValue(card)
    }

    // TBL-021, CR 306.5c: a planeswalker's corner is its loyalty counters; its other counters are bubbles
    @Test fun aPlaneswalkersCornerIsItsLoyalty() {
        val sage = table.put(table.alice, Zone.Battlefield, "Ash Sage").single()
        val card = computed(sage, CounterEntry(CounterKind.Loyalty, 4, 1), CounterEntry(CounterKind.Named("oil"), 2, 2))
        assertEquals("4", card.corner)
        assertEquals(listOf(ShownCounter(CounterKind.Named("oil"), "oil", 2)), card.bubbles)
        assertEquals(listOf("loyalty counter ×4", "oil counter ×2"), card.counters.map { it.words })
        assertEquals(emptyList(), card.marks, "no counter words on the chip")
    }

    // TBL-021, CR 714.2b: a Saga's corner is its lore counters, 0 when it has none
    @Test fun aSagasCornerIsItsLore() {
        val saga = table.put(table.alice, Zone.Battlefield, "Ember Chronicle").single()
        assertEquals("2", computed(saga, CounterEntry(CounterKind.Lore, 2, 1)).corner)
        assertEquals("0", computed(saga).corner)
    }

    // TBL-021: a creature's corner stays its P/T; a loyalty counter on it is a bubble like its +1/+1 counters
    @Test fun aCreaturesPrimaryKindCounterIsABubble() {
        val squire = table.put(table.alice, Zone.Battlefield, "Cinder Squire").single()
        val card = computed(squire, CounterEntry(CounterKind.PowerToughness(1, 1), 2, 1), CounterEntry(CounterKind.Loyalty, 1, 2))
        assertEquals("4/4", card.corner)
        assertEquals(listOf("+1/+1", "loyalty"), card.bubbles.map { it.name })
    }
}
