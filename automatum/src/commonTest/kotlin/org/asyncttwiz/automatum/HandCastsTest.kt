package org.asyncttwiz.automatum

import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import org.asyncttwiz.protocol.PlayZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * I-D50: a raised hand card's cost chips. The ways to cast it (the regular cast first) and its extras (optional
 * additional costs), each with its mana and whether it is paid by what the player has, and the sources Autotap would tap
 * for the choice; a cast begun with the chosen extras answers their questions itself, so the cast panel never asks them.
 */
class HandCastsTest {
    private val table = AtwTable("hand-casts")
    private val alice get() = table.alice
    private val random = Random(5)
    private fun seeds() = CastFlow.Seeds { org.asyncttwiz.protocol.RandomV1.encodeSeed(random.nextBytes(32)) }
    private fun log(game: TreeGame) = EngineLog.from(game.tags(), game.state, table.definitions)
    private fun plays(game: TreeGame, card: String) = HandPlays.of(game.state, table.definitions, alice, log(game)).of(card)
    private fun options(game: TreeGame, card: String, way: Int = 0, times: List<Int> = emptyList()) =
        HandCasts.options(game.state, table.definitions, alice, log(game), card, plays(game, card), way, times)

    // I-D50 (ZON-080): the regular cast with its mana, the kicker as an extra; {4} more is more than two Mountains pay, so
    // the extra is unaffordable; Autotap would tap one Mountain for the regular cast
    @Test fun regularCastAndAnUnaffordableKicker() {
        val mountains = table.put(alice, PlayZone.BATTLEFIELD, "Mountain", "Mountain")
        val (surge) = table.put(alice, PlayZone.HAND, "Flash Surge")
        val game = TreeGame(table.state(), table.definitions, name = "chips-short")
        val chips = assertNotNull(options(game, surge))
        assertEquals(listOf("Cast"), chips.ways.map { it.name })
        assertEquals("{R}", chips.chosen.cost)
        assertTrue(chips.chosen.affordable)
        val kicker = chips.extras.single()
        assertEquals("Kicker", kicker.name)
        assertEquals("{4}", kicker.cost)
        assertFalse(kicker.repeatable)
        assertFalse(kicker.affordable, "{4}{R} is more than two Mountains make")
        assertEquals(listOf(0), chips.times)
        assertEquals(1, chips.sources.size)
        assertTrue(chips.sources.single() in mountains)
        assertTrue(chips.affordable)
    }

    // I-D50: with the kicker chosen the sources follow it (all five Mountains for {4}{R})
    @Test fun sourcesFollowTheChosenExtras() {
        val mountains = table.put(alice, PlayZone.BATTLEFIELD, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        val (surge) = table.put(alice, PlayZone.HAND, "Flash Surge")
        val game = TreeGame(table.state(), table.definitions, name = "chips-kicked")
        assertTrue(assertNotNull(options(game, surge)).extras.single().affordable)
        val kicked = assertNotNull(options(game, surge, times = listOf(1)))
        assertEquals(listOf(1), kicked.times)
        assertEquals(mountains.toSet(), kicked.sources)
        assertTrue(kicked.affordable)
        assertEquals("{R}", kicked.chosen.cost, "the way's own cost, without the extra")
    }

    // I-D50, CR 702.33: a cast begun with the kicker chosen never asks it (the panel has no kicker question); it goes
    // out kicked: five Mountains tapped, 4 damage
    @Test fun aCastWithTheChosenKickerIsKicked() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        val (surge) = table.put(alice, PlayZone.HAND, "Flash Surge")
        val game = TreeGame(table.state(), table.definitions, name = "chips-cast")
        val life = game.state.board.players.single { it.accountId == table.bob }.life.toInt()
        var flow = CastFlow.start(game.state, table.definitions, alice, log(game), plays(game, surge).first(), seeds(), extras = listOf(1))
        val targets = assertIs<Decision.Targets>(flow.pending, "the kicker isn't asked")
        assertTrue(flow.steps.isEmpty())
        flow = flow.answer(Answer.Targets(listOf(targets.options.first {
            it == arcanum.engine.state.TargetRef.Player(arcanum.engine.state.PlayerId(table.bob)) })))
        val payment = assertIs<Decision.ManaPayment>(flow.pending)
        assertEquals("{4}{R}", payment.cost.toString())
        assertNull(game.send(flow.sending(payment.suggestion).sent(), alice).reason)
        assertEquals(5, game.state.board.cards.count { it.tapped })
        assertEquals(life - 4, game.state.board.players.single { it.accountId == table.bob }.life.toInt())
    }

    // I-D50: without chosen extras the kicker is answered "no" (a regular cast from the hand)
    @Test fun noExtrasChosenMeansNotKicked() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        val (surge) = table.put(alice, PlayZone.HAND, "Flash Surge")
        val game = TreeGame(table.state(), table.definitions, name = "chips-plain")
        val flow = CastFlow.start(game.state, table.definitions, alice, log(game), plays(game, surge).first(), seeds(), extras = emptyList())
        assertIs<Decision.Targets>(flow.pending)
        // The menu's cast (extras not chosen beforehand) still asks
        assertIs<Decision.May>(CastFlow.start(game.state, table.definitions, alice, log(game), plays(game, surge).first(), seeds()).pending)
    }

    // I-D50: a land's only way is Play, with no cost and no sources; it is always affordable
    @Test fun aLandPlays() {
        val (mountain) = table.put(alice, PlayZone.HAND, "Mountain")
        val game = TreeGame(table.state(), table.definitions, name = "chips-land")
        val chips = assertNotNull(options(game, mountain))
        assertEquals(listOf("Play"), chips.ways.map { it.name })
        assertTrue(chips.extras.isEmpty())
        assertTrue(chips.sources.isEmpty())
        assertTrue(chips.affordable)
        assertNull(chips.chosen.cost)
    }

    // I-D50: an alternative cost is a way of its own (evoke), named by its keyword, with its own mana
    @Test fun evokeIsAWay() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain", "Mountain")
        val (vivacity) = table.put(alice, PlayZone.HAND, "Vivacity")
        val game = TreeGame(table.state(), table.definitions, name = "chips-evoke")
        val chips = assertNotNull(options(game, vivacity))
        assertEquals(listOf("Cast", "Evoke"), chips.ways.map { it.name })
        assertFalse(chips.ways[0].affordable, "{3}{R/G}{R/G} is more than two Mountains")
        assertTrue(chips.ways[1].affordable)
        assertEquals("{R/G}{R/G}", chips.ways[1].cost)
        assertNotNull(chips.ways[1].text)
        val evoked = assertNotNull(options(game, vivacity, way = 1))
        assertEquals(2, evoked.sources.size)
        assertIs<Input.Cast>(evoked.chosen.input)
    }

    // I-D50 (ZON-080): a card the player can't pay for now still has its chips, greyed
    @Test fun unaffordableRegularCast() {
        val (surge) = table.put(alice, PlayZone.HAND, "Flash Surge")
        val game = TreeGame(table.state(), table.definitions, name = "chips-none")
        assertTrue(plays(game, surge).isEmpty(), "the engine offers only what it can pay")
        val chips = assertNotNull(options(game, surge))
        assertEquals(listOf("Cast"), chips.ways.map { it.name })
        assertEquals("{R}", chips.chosen.cost)
        assertFalse(chips.affordable)
        assertFalse(chips.chosen.affordable)
        assertTrue(chips.sources.isEmpty())
    }
}
