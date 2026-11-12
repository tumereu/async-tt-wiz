package arcanum.engine.casting

import arcanum.api.ObjectMark
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.foretell.Foretell
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Foretell (CR 702.143): the special action from the hand, the hidden foretold card, and casting it on a later turn. */
class ForetellTest {
    private val tidings = Cards.invent("Tidings of the Deep", "{3}{U}{U}", "Instant", """
        Draw three cards.
        Foretell {1}{U} (During your turn, you may pay {2} and exile this card from your hand face down. Cast it on a later turn for its foretell cost.)
    """)
    private val stalker = Cards.invent("Fjord Stalker", "{2}{G}{G}", "Creature — Wolf", """
        Foretell {G} (During your turn, you may pay {2} and exile this card from your hand face down. Cast it on a later turn for its foretell cost.)
    """, "4/4")

    // CR 702.143a, b: any time they have priority during their turn, pay {2} and exile it face down, without the Stack
    @Test
    fun foretellingExilesFaceDown() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(tidings)
        val action = Engine.options(state, alice.id).filterIsInstance<Input.Activate>().single { it.ability == Foretell.RULE }
        assertEquals(card(tidings), action.source)
        alice.takesSpecialAction(tidings, "foretell")
        expect {
            alice.exile has tidings
            "Island".tapped shouldBe true
            stack.isEmpty()
        }
        val o = state.objOrThrow(card(tidings))
        assertTrue(o.faceDown)
        assertTrue(ObjectMark.Foretold in o.marks)
        assertEquals(state.turn.number, o.facts.gained(ObjectMark.Foretold)?.turn)
        // CR 117.3c: a special action keeps priority
        assertEquals(alice.id, state.priority.holder)
    }

    // CR 702.143a: only during your own turn
    @Test
    fun notDuringAnotherPlayersTurn() = scenario {
        bob.battlefield("Island", "Island")
        bob.hand(tidings)
        assertTrue(Engine.options(state, bob.id).isEmpty())
        alice.passes()
        assertTrue(Engine.options(state, bob.id).none { it is Input.Activate && it.ability == Foretell.RULE })
        assertFailsWith<IllegalArgumentException> { bob.takesSpecialAction(tidings, "foretell") }
    }

    // CR 702.143a: cast it after the current turn has ended, for its foretell cost rather than its mana cost
    @Test
    fun castOnALaterTurnForItsForetellCost() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(tidings)
        alice.library("Forest", "Forest", "Forest", "Forest", "Forest")
        bob.library("Forest", "Forest")
        alice.takesSpecialAction(tidings, "foretell")
        // Not this turn
        assertTrue(Engine.options(state, alice.id).none { it is Input.Cast && it.card == card(tidings) })
        assertFailsWith<IllegalArgumentException> { alice.casts(tidings) }
        advanceTo(TurnStep.Upkeep, turn = 3)
        // The foretell cost {1}{U}: two Islands are enough (its mana cost is five)
        assertTrue(Engine.options(state, alice.id).any { it is Input.Cast && it.card == card(tidings) })
        alice.casts(tidings)
        allPass()
        expect {
            alice.graveyard has tidings
            // In the upkeep, before the draw step's draw
            alice.hand.size shouldBe 3
        }
    }

    // CR 702.143a: a foretold creature card is cast as a sorcery for its foretell cost
    @Test
    fun foretoldCreatureAsASorcery() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(stalker)
        alice.library("Forest", "Forest")
        bob.library("Forest", "Forest")
        alice.takesSpecialAction(stalker, "foretell")
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertFailsWith<IllegalArgumentException> { alice.casts(stalker) }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.casts(stalker)
        allPass()
        expect { alice.battlefield has stalker }
    }

    // CR 702.143a, 406.3: its owner may look at it; nobody else may (D60)
    @Test
    fun hiddenFromOthers() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(tidings)
        alice.takesSpecialAction(tidings, "foretell")
        val id = card(tidings)
        assertTrue(id in Host.visible(state, alice.id))
        assertFalse(id in Host.visible(state, bob.id))
    }

    // D60: in another player's view the card is foretold without being revealed, and stays unknown in exile
    @Test
    fun foretoldUnseenInAnotherPlayersView() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(tidings)
        viewer(bob)
        val id = card(tidings)
        perform(Input.Activate(alice.id, id, Foretell.RULE))
        if (pending is Decision.ManaPayment) answer(arcanum.engine.decision.Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        val o = state.objOrThrow(card(tidings))
        assertEquals(Zone.Exile, o.zone)
        assertTrue(o.unknown && o.faceDown && ObjectMark.Foretold in o.marks)
    }

    // D60: in its owner's view the foretold card stays known
    @Test
    fun foretoldKnownToItsOwner() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(tidings)
        viewer(alice)
        alice.takesSpecialAction(tidings, "foretell")
        val o = state.objOrThrow(card(tidings))
        assertTrue(!o.unknown && o.faceDown)
    }
}
