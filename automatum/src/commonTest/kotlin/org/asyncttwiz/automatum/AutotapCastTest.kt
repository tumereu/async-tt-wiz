package org.asyncttwiz.automatum

import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import org.asyncttwiz.protocol.GameAction
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
 * I-D47, "Autotap mana sources": a cast completed with the engine's suggested payment in one go ([CastFlow.sending]), mana
 * already in the pool paid first, and the owner's way to pay differently: Undo the cast, tap the wanted source by hand
 * (its mana ability, the mana into the pool), cast again, and autotap pays only the rest.
 */
class AutotapCastTest {
    private val table = AtwTable("autotap")
    private val alice get() = table.alice
    private val random = Random(11)
    private fun seeds() = CastFlow.Seeds { org.asyncttwiz.protocol.RandomV1.encodeSeed(random.nextBytes(32)) }

    private fun log(game: TreeGame) = EngineLog.from(game.tags(), game.state, table.definitions)
    private fun cast(game: TreeGame, card: String): CastFlow = CastFlow.start(game.state, table.definitions, alice, log(game),
        HandPlays.of(game.state, table.definitions, alice, log(game)).of(card).first(), seeds())
    /** [card]'s cast paid with the engine's suggestion and sent as the app sends it (the cast alone first, then on). */
    private fun autotapCast(game: TreeGame, card: String): CastFlow.Sending {
        val flow = cast(game, card)
        val payment = assertIs<Decision.ManaPayment>(flow.pending)
        val sending = flow.sending(payment.suggestion)
        assertNull(game.send(sending.sent(), alice).reason)
        return sending
    }
    private fun tapped(game: TreeGame, card: String) = game.state.board.cards.single { it.id == card }.tapped

    // I-D47, CR 601.2g–h: the suggestion pays with the cheapest source (a basic before a dual land); the cast and what
    // follows it go out as one batch, the creature resolving with nothing to respond with (STK-026)
    @Test fun autotapPaysWithTheSuggestionInOneBatch() {
        val (forest, grassland) = table.put(alice, PlayZone.BATTLEFIELD, "Forest", "Golden Grassland")
        val (seer) = table.put(alice, PlayZone.HAND, "Elvish Seer")
        val game = TreeGame(table.state(), table.definitions, name = "autotap-one")
        val sending = autotapCast(game, seer)
        assertTrue(tapped(game, forest))
        assertFalse(tapped(game, grassland))
        assertEquals(PlayZone.BATTLEFIELD, game.state.board.containers.single { seer in it.cards }.zone)
        assertTrue(sending.cast.working.stack.isEmpty(), "the same call went on: the creature resolved")
    }

    // I-D47: the preview is the cast alone: the card's Stack entry and the tapped source, before any pass
    @Test fun theCastAloneIsThePreview() {
        val (forest) = table.put(alice, PlayZone.BATTLEFIELD, "Forest")
        val (seer) = table.put(alice, PlayZone.HAND, "Elvish Seer")
        val game = TreeGame(table.state(), table.definitions, name = "autotap-preview")
        val flow = cast(game, seer)
        val sending = flow.sending((flow.pending as Decision.ManaPayment).suggestion)
        val command = game.command(GameAction.PlayBatch(listOf(org.asyncttwiz.protocol.PlayAction.PassAttention)))
        val preview = EngineBatch.plan(sending.cast, alice, command)
        assertTrue(preview.state.board.cards.single { it.id == forest }.tapped)
        assertEquals(seer, preview.state.board.stackEffects.single().sourceId, "the spell's Stack entry")
        // Then the same call goes on: the caster passes and the creature resolves
        val full = EngineBatch.plan(sending.sent(), alice, game.command(GameAction.PlayBatch(listOf(org.asyncttwiz.protocol.PlayAction.PassAttention))))
        assertTrue(full.state.board.stackEffects.isEmpty())
        assertTrue(full.actions.size > preview.actions.size)
        assertEquals(sending.sent(), sending.sent(), "what follows the cast is performed once")
    }

    // I-D47 (owner's flow): the autotap tapped the Forest, but the player wanted the dual land tapped: Undo, tap the dual
    // land by hand (its mana into the pool, CR 605.3a: the player keeps priority), cast again: the pool pays and the
    // Forest stays untapped
    @Test fun undoThenTapTheWantedSourceThenCast() {
        val (forest, grassland) = table.put(alice, PlayZone.BATTLEFIELD, "Forest", "Golden Grassland")
        val (seer) = table.put(alice, PlayZone.HAND, "Elvish Seer")
        val game = TreeGame(table.state(), table.definitions, name = "autotap-undo")
        autotapCast(game, seer)
        assertTrue(tapped(game, forest))
        assertNotNull(game.control(alice, GameAction.Undo).tree, "the cast is undone")
        assertFalse(tapped(game, forest))
        assertTrue(game.state.board.cards.single { it.id == seer }.containerId == table.container(alice, PlayZone.HAND))
        // Tapping the dual land by hand: its mana ability (green), as the card menu or a double-tap begins it
        val green = CardPlays.of(game.state, table.definitions, alice, log(game)).of(grassland)
            .first { it.kind == CardPlay.Kind.Mana && it.legal }
        var tap = CastFlow.start(game.state, table.definitions, alice, log(game), green.input!!, seeds())
        while (!tap.complete) tap = when (val open = tap.pending) {
            is Decision.ChooseColor -> tap.answer(Answer.ColorChoice(open.among.first { it.symbol == 'G' }))
            else -> tap.answer(tap.listedAnswers().first())
        }
        assertNull(game.send(tap.sent(), alice).reason)
        assertTrue(tapped(game, grassland))
        assertEquals(1, game.state.board.players.single { it.accountId == alice }.manaPool.size, "one mana in the pool")
        assertEquals(alice, game.state.board.priority?.holder, "the player keeps priority after a mana ability")
        // Cast again: the suggestion uses the pool first and taps nothing else
        val again = cast(game, seer)
        val payment = assertIs<Decision.ManaPayment>(again.pending)
        assertTrue(payment.suggestion.all { it is arcanum.engine.decision.ManaUse.Pool }, "${payment.suggestion}")
        assertNull(game.send(again.sending(payment.suggestion).sent(), alice).reason)
        assertFalse(tapped(game, forest), "the Forest stays untapped")
        assertTrue(game.state.board.players.single { it.accountId == alice }.manaPool.isEmpty())
        assertEquals(PlayZone.BATTLEFIELD, game.state.board.containers.single { seer in it.cards }.zone)
    }

    // I-D47: partly by hand: a Forest tapped by hand pays {G} of a {1}{G} cost from the pool; autotap taps one more source
    @Test fun poolPaysFirstAndAutotapTapsTheRest() {
        val (forest, second, third) = table.put(alice, PlayZone.BATTLEFIELD, "Forest", "Forest", "Forest")
        val (druid) = table.put(alice, PlayZone.HAND, "Zealous Druid")
        val game = TreeGame(table.state(), table.definitions, name = "autotap-partial")
        val green = CardPlays.of(game.state, table.definitions, alice, log(game)).of(third).first { it.kind == CardPlay.Kind.Mana && it.legal }
        assertNull(game.send(CastFlow.start(game.state, table.definitions, alice, log(game), green.input!!, seeds()).sent(), alice).reason)
        val flow = cast(game, druid)
        val payment = assertIs<Decision.ManaPayment>(flow.pending)
        assertEquals(1, payment.suggestion.count { it is arcanum.engine.decision.ManaUse.Pool })
        assertEquals(1, payment.suggestion.count { it is arcanum.engine.decision.ManaUse.Ability })
        assertNull(game.send(flow.sending(payment.suggestion).sent(), alice).reason)
        assertEquals(2, listOf(forest, second, third).count { tapped(game, it) })
        assertTrue(tapped(game, third))
    }

    // I-D47: a spell whose cast needs nothing but the payment completes without a panel; one with a choice (kicker) stops
    // at it, and the payment is never a question with autotap
    @Test fun sendingNeedsTheChoicesMade() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain", "Mountain")
        val (surge) = table.put(alice, PlayZone.HAND, "Flash Surge")
        val game = TreeGame(table.state(), table.definitions, name = "autotap-choices")
        val flow = cast(game, surge)
        assertIs<Decision.May>(flow.pending)
        kotlin.test.assertFailsWith<IllegalArgumentException> { flow.sending(null) }
        assertIs<Input.Cast>(flow.begin)
    }
}
