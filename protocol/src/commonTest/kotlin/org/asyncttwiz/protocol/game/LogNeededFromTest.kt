package org.asyncttwiz.protocol.game

import arcanum.engine.Engine
import arcanum.engine.Inspect
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.Seat
import arcanum.engine.setup.StartOptions
import arcanum.engine.state.CardId
import arcanum.engine.state.GameState
import arcanum.engine.state.LoggedChange
import arcanum.engine.state.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The model's log trim keeps each player's last turn ([GameState.logNeededFrom], CR 800.4i), which with extra turns or
 * 3+ players is older than last turn: reverting stays exact (the same log as reached forwards), or says it can't
 * ([GameModel.stale]) and the history takes the state from a checkpoint.
 */
class LogNeededFromTest {
    private val hour = Cards.invent("Borrowed Hour", "{0}", "Sorcery", "Take an extra turn after this one.")
    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")
    private val carol = PlayerId("carol")

    /** A game of [players] in this order, Alice's deck of Borrowed Hours, the others' of Islands; Alice starts. */
    private inner class Scripted(players: List<PlayerId>) {
        val initial: GameState = Engine.newGame(players.map { p ->
            Seat(p, List(30) { i -> DeckCard(CardId("${p.value}.$i"), Cards.definition(if (p == alice) hour else "Island")) })
        })
        val codec = EngineJson(GameDefinitions.of(initial))
        val secret: String = LineRandom.secret(ByteArray(32) { it.toByte() })
        val model = GameModel(initial.deepCopy(), TableState(secret = secret))
        /** The events so far, each with the state after it. */
        val played = mutableListOf<Pair<GameEvent, GameModel>>()
        private var parent = "root"

        fun root(): GameModel = GameModel(initial.deepCopy(), TableState(secret = secret))

        /** One event: [input], then the random answers. */
        fun event(input: Input): GameEvent {
            val builder = EventBuilder(model, "e${played.size + 1}", parent, alice.value)
            builder.perform(input)
            builder.answerRandoms()
            return builder.build().also { parent = it.id; played += it to model.copy() }
        }

        /** The next event that moves the game on: the start, the first listed answer, or a pass (no attacks or blocks). */
        fun pass(): GameEvent {
            val state = model.engine
            val d = state.pending?.decision
            if (state.turn.number == 0 && d == null) return event(Input.StartGame(StartOptions(startingPlayer = alice)))
            val listed = state.players.flatMap { Engine.options(state, it.id, EnginePreferences.of(state)) }.distinct().filter { it !is Input.Concede }
            if (d != null) return event(listed.first())
            return event(listed.first {
                it is Input.PassPriority || (it is Input.DeclareAttackers && it.attacks.isEmpty()) || (it is Input.DeclareBlockers && it.blocks.isEmpty())
            })
        }

        /** Passes until turn [number] is begun (the event that began it is the last one played). */
        fun passTo(number: Int) {
            while (model.engine.turn.number < number) pass()
        }

        /** Alice casts a Borrowed Hour in her main phase (passing to it) and it resolves. */
        fun aliceTakesAnExtraTurn() {
            val turn = model.engine.turn.number
            while (true) {
                val state = model.engine
                check(state.turn.number == turn) { "Alice found no main phase to cast in" }
                val cast = if (state.pending == null) Engine.options(state, alice, EnginePreferences.of(state))
                    .filterIsInstance<Input.Cast>().firstOrNull() else null
                if (cast != null) { event(cast); break }
                pass()
            }
            while (model.engine.stack.isNotEmpty() || model.engine.pending != null) pass()
            assertEquals(listOf(alice), model.engine.turn.extraTurns, "Alice's extra turn is to come")
        }

        fun log(): List<LoggedChange> = Inspect.events(model.engine)
    }

    // CR 800.4i, CR 500.7: Alice's extra turn 4 follows her turn 3; Bob's last turn is still turn 2, so the log keeps it.
    // Reverting the event that began the extra turn gives back the state and log of before it without a checkpoint, and so
    // does every event back to the game's start
    @Test
    fun revertingTheStartOfAnExtraTurnKeepsTheLogExact() {
        val game = Scripted(listOf(alice, bob))
        game.passTo(3)
        assertEquals(alice, game.model.engine.turn.active)
        game.aliceTakesAnExtraTurn()
        assertEquals(2, game.model.engine.logNeededFrom, "in Alice's turn 3, Bob's last turn is turn 2")

        game.passTo(4)
        val began = game.played.last().first
        // The state and log just before the event that began the extra turn
        val before = game.played[game.played.size - 2].second
        val logBefore = Inspect.events(before.engine)
        val engine = game.model.engine
        assertEquals(alice, engine.turn.active, "turn 4 is Alice's extra turn")
        assertEquals(mapOf(alice to 4, bob to 2), engine.turn.turnStarted)
        assertEquals(2, engine.logNeededFrom, "Bob's last turn is still turn 2, older than last turn")
        assertTrue(game.log().any { it.turn == 2 }, "turn 2's events are kept")

        val reverted = game.model.copy().also { it.revert(began) }
        assertFalse(reverted.stale, "reverting the start of the extra turn needs no checkpoint")
        assertEquals(logBefore, Inspect.events(reverted.engine), "the log of before the extra turn began")
        assertTrue(reverted.sameAs(before))

        // Bob's turn 5 drops turns 2 and 3; reverting its start puts them back
        game.passTo(5)
        assertEquals(bob, game.model.engine.turn.active)
        assertEquals(4, game.model.engine.logNeededFrom)
        assertTrue(game.log().none { it.turn < 4 }, "turns before Alice's last one are dropped")

        // Every event back to the start: each state the one recorded forwards, its log the same
        val model = game.model
        for (i in game.played.indices.reversed()) {
            val event = game.played[i].first
            model.revert(event)
            assertFalse(model.stale, "back before ${event.id} needs no checkpoint")
            val expected = if (i == 0) game.root() else game.played[i - 1].second
            assertEquals(Inspect.events(expected.engine), Inspect.events(model.engine), "the log before ${event.id}")
            assertTrue(model.sameAs(expected), "the state before ${event.id}")
        }
    }

    // CR 800.4i, CR 104.3a: with three players, Bob's last turn (2) is kept in Alice's turn 4; Bob conceding in it drops turn 2
    // (Carol's last turn is 3). Reverting the concession can't give that log back: the model is stale, and the history takes
    // the state from a checkpoint, the same as the one recorded forwards
    @Test
    fun aPlayerLeavingMidTurnFallsBackToACheckpoint() {
        val game = Scripted(listOf(alice, bob, carol))
        game.passTo(4)
        assertEquals(alice, game.model.engine.turn.active)
        // Alice's upkeep or later: the event that began the turn is behind
        game.pass()
        assertEquals(2, game.model.engine.logNeededFrom, "Bob's last turn is turn 2")
        assertTrue(game.log().any { it.turn == 2 }, "turn 2's events are kept")
        val before = game.played.last()

        val conceded = game.event(Input.Concede(bob))
        assertTrue(game.model.engine.player(bob).lost)
        assertEquals(3, game.model.engine.logNeededFrom, "with Bob out, Carol's last turn 3 is the oldest needed")
        assertTrue(game.log().none { it.turn < 3 }, "turn 2's events are dropped")

        val reverted = game.model.copy().also { it.revert(conceded) }
        assertTrue(reverted.stale, "the dropped turn 2 isn't held: the model says it can't go back exactly")

        val store = InMemoryHistory("root", game.codec.encodeModel(game.root()))
        val history = GameHistory(store, game.codec, every = 8)
        for ((event, state) in game.played) store.add(event, history.checkpointFor(store.depthOfParent(event) + 1, state))
        val moved = history.move(game.model.copy(), conceded.id, before.first.id)
        assertFalse(moved.stale)
        assertEquals(Inspect.events(before.second.engine), Inspect.events(moved.engine), "the log of before the concession")
        assertTrue(moved.sameAs(before.second), "the state of before the concession, from a checkpoint")
    }
}
