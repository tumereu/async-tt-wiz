package org.asyncttwiz.automatum

import arcanum.api.Zone
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * I-D50 on a game's own engine state: a raised hand card's cost chips ([HandCasts]): the ways to cast it (the regular
 * cast first) and its extras, each with its mana and whether it is paid now, and the sources Autotap would tap.
 */
class EngineHandCastsTest {
    private val table = EngineTable()
    private val alice get() = table.alice

    private fun options(card: String, way: Int = 0, times: List<Int> = emptyList()): HandCastOptions? {
        val state = table.state()
        val plays = HandPlays.of(state, alice, null).of(card)
        return HandCasts.options(table.source(state), table.definitions, alice, card, plays, way, times,
            CastFlow.Seeds(table::seed))
    }

    // ZON-080: the regular cast with its mana, chosen; the kicker an extra, unaffordable with two Mountains ({4}{R}); the
    // sources Autotap would tap follow the chosen way
    @Test fun regularCastAndAnUnaffordableKicker() {
        val mountains = table.put(alice, Zone.Battlefield, "Mountain", "Mountain")
        val (surge) = table.put(alice, Zone.Hand, "Flash Surge")
        val chips = assertNotNull(options(surge))
        assertEquals(listOf("Cast"), chips.ways.map { it.name })
        assertEquals("{R}", chips.chosen.cost)
        assertTrue(chips.chosen.affordable)
        val kicker = chips.extras.single()
        assertEquals("Kicker", kicker.name)
        assertEquals("{4}", kicker.cost)
        assertFalse(kicker.affordable)
        assertEquals(1, chips.sources.size)
        assertTrue(chips.sources.single() in mountains)
    }

    // ZON-080: with the kicker chosen the sources follow the extras (five Mountains for {4}{R}); the way keeps its own cost
    @Test fun sourcesFollowTheChosenExtras() {
        val mountains = table.put(alice, Zone.Battlefield, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        val (surge) = table.put(alice, Zone.Hand, "Flash Surge")
        val kicked = assertNotNull(options(surge, times = listOf(1)))
        assertEquals(listOf(1), kicked.times)
        assertEquals(mountains.toSet(), kicked.sources)
        assertEquals("{R}", kicked.chosen.cost)
    }

    // ZON-080, STK-023: a cast begun with the chosen kicker never asks it: the cast asks only for its target, then pays
    @Test fun aCastWithTheChosenKickerAsksOnlyTheTarget() {
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        val (surge) = table.put(alice, Zone.Hand, "Flash Surge")
        val state = table.state()
        val begin = HandPlays.of(state, alice, null).of(surge).first()
        val flow = CastFlow.start(table.source(state), alice, begin, CastFlow.Seeds(table::seed), extras = listOf(1))
        assertIs<Decision.Targets>(flow.pending)
        assertTrue(flow.steps.isEmpty())
    }

    // ZON-080: an alternative cost is a way of its own (Evoke) with its own mana; the regular cast is greyed when unpaid
    @Test fun evokeIsAWay() {
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain")
        val (vivacity) = table.put(alice, Zone.Hand, "Vivacity")
        val chips = assertNotNull(options(vivacity))
        assertEquals(listOf("Cast", "Evoke"), chips.ways.map { it.name })
        assertFalse(chips.ways[0].affordable)
        assertTrue(chips.ways[1].affordable)
        assertEquals("{R/G}{R/G}", chips.ways[1].cost)
        assertIs<Input.Cast>(assertNotNull(options(vivacity, way = 1)).chosen.input)
    }

    // ZON-080: a land's only way is Play, without a cost; a card the player can't pay for now keeps its chips, greyed
    @Test fun aLandPlaysAndAnUnpaidCastIsGreyed() {
        val (mountain) = table.put(alice, Zone.Hand, "Mountain")
        val (surge) = table.put(alice, Zone.Hand, "Flash Surge")
        assertEquals(listOf("Play"), assertNotNull(options(mountain)).ways.map { it.name })
        val chips = assertNotNull(options(surge))
        assertEquals(listOf("Cast"), chips.ways.map { it.name })
        assertFalse(chips.affordable)
        assertTrue(chips.sources.isEmpty())
    }
}
