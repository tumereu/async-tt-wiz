@file:OptIn(arcanum.engine.state.ArcanumTestSetup::class)

package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.ObjectStatus
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.Faults
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.TableState
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SYS-068, SYS-206 (owner playtest 2026-11-19, N24), CR 723.3c: a prepared creature's spell, a copy of it in exile, feels like an ability
 * of the creature: the creature offers "{G}: Cast <spell>" among its plays (its chips and glow), the copy isn't in the hand row,
 * and the copy in exile shows the card's own picture.
 */
class PreparedSpellTest {
    private val seraph = Cards.inventFaces("Prepare",
        face("Prepared Seraph", "{3}{W}", "Creature — Angel", "{0}: This creature becomes prepared.", "2/4", null, null),
        face("Prepared Mending", "{G}", "Sorcery", "You gain 1 life.", null, null, null),
    )
    private val table = EngineTable(pictured = true)
    private val alice get() = table.alice
    private val faults = mutableListOf<org.asyncttwiz.protocol.Fault>()
    private val sink = Faults.sink.also { Faults.sink = { faults += it } }

    @AfterTest fun nothingReported() {
        Faults.sink = sink
        assertEquals(emptyList(), faults, "a picture or name was missing")
    }

    private fun GameState.perform(input: Input): GameState {
        Engine.perform(this, input, org.asyncttwiz.protocol.game.EnginePreferences.of(this)).changes.forEach(::apply)
        return this
    }

    /** Alice's Seraph becomes prepared: its ability activated and resolved. */
    private fun prepared(seraphId: String, forest: Boolean): GameState {
        if (forest) table.put(alice, Zone.Battlefield, "Forest")
        val state = table.state()
        val id = table.obj(seraphId)
        state.perform(Input.Activate(PlayerId(alice), id,
            assertNotNull(Engine.view(state)[id]?.characteristics?.abilities?.firstOrNull { it.ability is Ability.Activated }).origin))
        repeat(10) {
            if (state.stack.isEmpty() && state.exile.isNotEmpty()) return state
            val decision = state.pending?.decision
            if (decision is Decision.ManaPayment) state.perform(Input.Answer(decision.id, Answer.ManaPayment(decision.suggestion)))
            else state.perform(Input.PassPriority(assertNotNull(state.priority.holder)))
        }
        error("never prepared")
    }

    private fun copyOf(state: GameState): ObjectId.Card = state.exile.single { id ->
        state.obj(id)?.statuses?.any { it is ObjectStatus.PrepareSpellOf } == true } as ObjectId.Card

    private fun plays(state: GameState, viewer: String = alice): CardPlays {
        val view = Engine.view(state)
        return CardPlays.of(state, viewer, view, Host.visible(state, PlayerId(viewer), view))
    }

    // SYS-206, CR 723.3c: the prepared Seraph offers its spell among its plays, cast as the copy in exile; the copy isn't in the hand row
    @Test fun thePreparedCreatureOffersItsSpell() {
        val (s) = table.put(alice, Zone.Battlefield, seraph)
        val state = prepared(s, forest = true)
        val copy = copyOf(state)
        val offered = plays(state).of(s).filter { it.kind == CardPlay.Kind.Prepared }
        assertEquals(listOf("{G}: Cast Prepared Mending"), offered.map { it.text })
        val cast = assertNotNull(offered.single().input as? Input.Cast, "castable now: ${offered.single().refusal}")
        assertEquals(copy, cast.card, "cast as its copy in exile")
        val view = Engine.view(state)
        assertFalse(copy.card.value in OffZoneCards.permitted(state, view, alice), "not in the hand row: offered on the Seraph")
        val board = ComputedBoard.of(table.board(state, alice), table.base().definitions, table.definitions, alice, state, view)
        assertTrue(board.handRow.isEmpty(), "${board.handRow}")
        // The other player's table: greyed, not theirs
        val bobs = plays(state, table.bob).of(s).single { it.kind == CardPlay.Kind.Prepared }
        assertNull(bobs.input)
        assertEquals(CardPlay.Refusal.NotYours, bobs.refusal)
    }

    // SYS-206: without mana for it the spell is still offered on the Seraph, greyed with why
    @Test fun withoutManaItIsGreyed() {
        val (s) = table.put(alice, Zone.Battlefield, seraph)
        val state = prepared(s, forest = false)
        val offered = plays(state).of(s).single { it.kind == CardPlay.Kind.Prepared }
        assertEquals("{G}: Cast Prepared Mending", offered.text)
        assertNull(offered.input, "nothing pays {G}")
        assertNotNull(offered.refusal)
    }

    // D9, SYS-068, CR 723.3c: the copy the engine made in exile shows the card's own picture (its one physical face)
    @Test fun theCopyInExileShowsTheCardsPicture() {
        val (s) = table.put(alice, Zone.Battlefield, seraph)
        val state = prepared(s, forest = false)
        val copy = copyOf(state).card.value
        val drawn = EngineProjection.project(table.base(), GameModel(state.deepCopy(), TableState()), alice, "node")
        val card = drawn.board.cards.single { it.id == copy }
        val face = drawn.definitions.single { it.digest == card.definition }.definition.faces.let { faces -> faces.firstOrNull { it.faceId == card.faceId } ?: faces.first() }
        assertEquals("Prepared Mending", face.name)
        assertEquals(EngineTable.pictureOf("Prepared Seraph // Prepared Mending"), face.scryfallId)
    }
}
