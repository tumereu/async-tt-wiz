@file:OptIn(arcanum.engine.state.ArcanumTestSetup::class)

package org.asyncttwiz.protocol.game

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import arcanum.engine.state.setup
import org.asyncttwiz.protocol.AssumeReveals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * LOB-135 (owner 2026-11-07): the game's assume reveal settings relax the information rule (TRN-067, TRN-071): with
 * "Allow assumes that reveal the opponent's library" (on by default) the other player's library cards don't hold back an
 * assumption, with "Allow assumes that reveal hands" (off by default) their hand cards don't either; face-down cards and
 * random results are held back regardless. The device and the server's check honour the same settings.
 */
class AssumeRevealsTest {
    private val augury = Cards.invent("Prying Augury", "{0}", "Sorcery", "Target player reveals the top two cards of their library.")
    private val exposure = Cards.invent("Prying Exposure", "{0}", "Sorcery", "Target player reveals their hand.")
    private val gamble = Cards.invent("Prying Gamble", "{0}", "Sorcery", "Target opponent loses 1 life, then reveals a card at random from their hand.")
    private val toss = Cards.invent("Review Toss", "{U}", "Instant", "Flip a coin. If you win the flip, you gain 6 life.")

    private fun hand(model: GameModel, player: PlayerId, name: String): ObjectId =
        model.engine.player(player).hand.first { model.engine.obj(it)?.definition?.faces?.first()?.name == name }

    private fun decks(spell: String) = List(40) { "Forest" } to (List(20) { "Island" } + List(20) { spell })

    /** Bob starts and keeps, Alice keeps and is away (his device assumes her passes up to his main phase, "Always"),
     * the game's settings [reveals]; Bob plays an Island and casts [spell] at Alice. */
    private fun bobCasts(spell: String, reveals: AssumeReveals): DeviceSim {
        val sim = DeviceSim(EngineGames(3, decks(spell)))
        sim.reveals = reveals
        val bob = sim.bob
        sim.start(bob)
        sim.keepWhileAway(bob)
        val bobs = sim.device(bob)
        assertEquals(GameDevice.Status.YourMove, bobs.status)
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.PlayLand(bob, hand(bobs.model, bob, "Island"))))
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.Cast(bob, hand(bobs.model, bob, spell))) { m ->
            val d = m.engine.pending?.decision
            if (d == null || d is Decision.Random || d.player != bob) null
            else Engine.options(m.engine, bob).filterIsInstance<Input.Answer>().let { answers ->
                if (d is Decision.Targets) answers.first { (it.answer as Answer.Targets).chosen == listOf(TargetRef.Player(sim.alice)) } else answers.first()
            }
        })
        sim.settle()
        return sim
    }

    /** Whether Bob's device assumed Alice's pass over his spell, which resolved, with nothing shown beyond the settings. */
    private fun assumedThrough(sim: DeviceSim): Boolean {
        val bob = sim.bob
        assertTrue(sim.leaks.isEmpty(), "${sim.leaks}")
        return sim.server.state().engine.stack.isEmpty() &&
            sim.server.line().any { it.assumed?.assumedBy == bob && sim.server.stateAt(it.parentId).engine.stack.isNotEmpty() }
    }

    private fun waitsForHiddenCards(sim: DeviceSim): Boolean =
        sim.device(sim.bob).status == GameDevice.Status.Waiting(GameDevice.Wait.HiddenCards) && sim.server.state().engine.stack.isNotEmpty()

    // LOB-135, TRN-067, TRN-071: with the library setting on (the game's default) Bob's device assumes Alice's pass over
    // his spell that reveals her library's top cards; with it off that assumption waits, as going on would show them
    @Test
    fun anOpponentsLibraryRevealIsAssumedOnlyWithTheLibrarySetting() {
        assertTrue(assumedThrough(bobCasts(augury, AssumeReveals())))
        assertTrue(waitsForHiddenCards(bobCasts(augury, AssumeReveals(library = false))))
    }

    // LOB-135, TRN-067, TRN-071: a reveal of her hand waits by default (hands off) and is assumed with the hands setting on,
    // also when the host turns it on during that wait (the change counts at once, outside undo)
    @Test
    fun anOpponentsHandRevealIsAssumedOnlyWithTheHandsSetting() {
        val waiting = bobCasts(exposure, AssumeReveals())
        assertTrue(waitsForHiddenCards(waiting))
        waiting.reveals = AssumeReveals(hands = true)
        waiting.settle()
        assertTrue(assumedThrough(waiting))
        assertTrue(assumedThrough(bobCasts(exposure, AssumeReveals(hands = true))))
    }

    // LOB-135: a card of her hand shown at random (a random pick among her hand) counts as her hand's, not as a random
    // result: it waits by default and is assumed with the hands setting on
    @Test
    fun aRandomCardOfHerHandFollowsTheHandsSetting() {
        assertTrue(waitsForHiddenCards(bobCasts(gamble, AssumeReveals())))
        assertTrue(assumedThrough(bobCasts(gamble, AssumeReveals(hands = true))))
    }

    // LOB-135, TRN-067: a random result (a coin flip) is held back whatever the settings
    @Test
    fun aRandomResultWaitsWhateverTheSettings() {
        assertTrue(waitsForHiddenCards(bobCasts(toss, AssumeReveals(library = true, hands = true))))
    }

    // LOB-135, TRN-067: a face-down card is held back whatever the settings; the other player's library and hand cards
    // follow them, and the viewer's own library is never held back
    @Test
    fun faceDownCardsAreHeldBackWhateverTheSettings() {
        val games = EngineGames(3, decks(augury))
        val state = games.model().engine
        val alice = games.alice; val bob = games.bob
        val library = state.player(alice).library.first()
        val own = state.player(bob).library.first()
        val handCard = state.player(alice).library[1]
        val faceDown = state.player(alice).library[2]
        state.setup.objects[handCard] = state.obj(handCard)!!.copy(zone = Zone.Hand)
        state.setup.objects[faceDown] = state.obj(faceDown)!!.copy(zone = Zone.Exile, faceDown = true)
        fun cardOf(id: ObjectId) = state.obj(id)!!.card
        fun shown(allowed: AssumeReveals) =
            InformationRule.revealing(state, listOf(Change.Shown(bob, listOf(library, own, handCard, faceDown))), bob, allowed)
        assertEquals(setOf(cardOf(library), cardOf(handCard), cardOf(faceDown)), shown(AssumeReveals.NONE))
        assertEquals(setOf(cardOf(handCard), cardOf(faceDown)), shown(AssumeReveals()))
        assertEquals(setOf(cardOf(faceDown)), shown(AssumeReveals(library = true, hands = true)))
    }

    // LOB-135, TRN-067: the server checks the same: an assumed pass whose outcome reveals her library's top cards is
    // refused with the library setting off and taken with it on
    @Test
    fun theServersCheckHonoursTheSettings() {
        for (library in listOf(false, true)) {
            val server = AsyncServer(EngineGames(3, decks(augury)))
            server.reveals = AssumeReveals(library = library)
            val alice = server.alice; val bob = server.bob
            server.start(bob)
            server.keepAndAssumeKeep(bob)
            repeat(40) {
                val s = server.state().engine
                if (s.turn.active == bob && s.turn.step == TurnStep.PrecombatMain && s.priority.holder == bob && s.stack.isEmpty()) return@repeat
                val holder = s.priority.holder!!
                server.act(bob, Input.PassPriority(holder), assumed = holder != bob).getOrThrow()
            }
            server.act(bob, Input.Cast(bob, hand(server.state(), bob, augury)), follow = server.flow(bob) { d, answers ->
                if (d is Decision.Targets) answers.first { (it.answer as Answer.Targets).chosen == listOf(TargetRef.Player(alice)) } else answers.first()
            }).getOrThrow()
            if (server.state().engine.priority.holder == bob) server.act(bob, Input.PassPriority(bob)).getOrThrow()
            val assumed = server.act(bob, Input.PassPriority(alice), assumed = true)
            if (library) assertTrue(assumed.isSuccess, "taken with the library setting on: ${assumed.exceptionOrNull()?.message}")
            else assertEquals(Authority.REVEALS_HIDDEN, assumed.exceptionOrNull()?.message)
        }
    }
}
