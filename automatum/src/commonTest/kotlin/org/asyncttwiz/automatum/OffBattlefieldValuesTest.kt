@file:OptIn(arcanum.engine.state.ArcanumTestSetup::class)

package org.asyncttwiz.automatum

import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.of
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * TBL-239 (owner, 2026-11-22): the viewer's hand cards show the battlefield's bottom row, their card types and the P/T
 * (a planeswalker's loyalty) Automatum works out; the Stack's cards their types; never the other player's hidden hand.
 */
class OffBattlefieldValuesTest {
    init {
        Cards.invent("Sunbaked Basker", "{0}", "Creature — Lizard", "", "1/1")
        Cards.invent("Brass Plow", "{2}", "Artifact Creature — Ox", "", "2/2")
        Cards.invent("Quick Spark", "{R}", "Instant", "Quick Spark deals 2 damage to any target.")
        Cards.invent("Scorching Recall", "{R}", "Sorcery", "Scorching Recall deals 1 damage to any target.\nFlashback {1}{R}")
        Cards.invent("Vela, Ember Seer", "{2}{R}", "Legendary Planeswalker — Vela", "[+1]: You gain 1 life.", loyalty = "4")
    }
    private val table = EngineTable()

    private fun computed(state: GameState = table.state(), viewer: String = table.alice): ComputedBoard =
        ComputedBoard.of(table.board(state, viewer), table.base().definitions, table.definitions, viewer, state, Engine.view(state))

    // TBL-239, TBL-233: a hand card's types; a creature's P/T, a planeswalker's loyalty, nothing for an instant
    @Test fun yourHandCardsShowTheirTypesAndPowerAndToughness() {
        val plow = table.put(table.alice, Zone.Hand, "Brass Plow").single()
        val spark = table.put(table.alice, Zone.Hand, "Quick Spark").single()
        val vela = table.put(table.alice, Zone.Hand, "Vela, Ember Seer").single()
        val board = computed()
        assertEquals(listOf("Artifact", "Creature"), board.offBattlefield.getValue(plow).cardTypes)
        assertEquals("2/2", board.offBattlefield.getValue(plow).corner)
        assertEquals(0, board.offBattlefield.getValue(plow).trend, "printed values: no colour")
        assertEquals(listOf("Instant"), board.offBattlefield.getValue(spark).cardTypes)
        assertNull(board.offBattlefield.getValue(spark).corner)
        assertEquals(listOf("Planeswalker"), board.offBattlefield.getValue(vela).cardTypes)
        assertEquals("4", board.offBattlefield.getValue(vela).corner, "its loyalty")
        assertFalse(plow in board.cards, "the battlefield's values stay the battlefield's")
    }

    // TBL-239, ZON-106: a card of the viewer's hand row from another zone (flashback in the graveyard) shows its types too
    @Test fun aHandRowCardFromAnotherZoneShowsItsTypes() {
        val recall = table.put(table.alice, Zone.Graveyard, "Scorching Recall").single()
        val board = computed()
        kotlin.test.assertTrue(recall in board.handRow, "in the hand row")
        assertEquals(listOf("Sorcery"), board.offBattlefield.getValue(recall).cardTypes)
    }

    // TBL-239: the other player's hand is hidden: none of its cards has values for the viewer
    @Test fun theOtherPlayersHandShowsNothing() {
        val theirs = table.put(table.bob, Zone.Hand, "Sunbaked Basker").single()
        assertFalse(theirs in computed().offBattlefield)
        assertEquals("1/1", computed(viewer = table.bob).offBattlefield.getValue(theirs).corner)
    }

    // TBL-239: a spell on the Stack shows its types (for both players), no P/T
    @Test fun aSpellOnTheStackShowsItsTypes() {
        val basker = table.put(table.bob, Zone.Hand, "Sunbaked Basker").single()
        val before = table.state()
        val id = table.obj(basker)
        val spell = before.objects.getValue(id).copy(zone = Zone.Stack, controller = PlayerId(table.bob))
        val state = GameState.of(
            players = before.players.map { p -> p.copy(hand = p.hand - id) },
            objects = before.objects + (id to spell), battlefield = before.battlefield, stack = listOf(id),
            stackItems = mapOf(id to StackItem(id, StackItemKind.Spell, PlayerId(table.bob), id, null, timestamp = 99)),
            turn = before.turn, priority = before.priority, timestampCounter = 100)
        val shown = computed(state).offBattlefield.getValue(basker)
        assertEquals(listOf("Creature"), shown.cardTypes)
        assertNull(shown.corner)
    }
}
