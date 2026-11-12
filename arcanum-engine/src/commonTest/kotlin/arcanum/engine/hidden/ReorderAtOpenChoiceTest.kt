package arcanum.engine.hidden

import arcanum.api.Zone
import arcanum.engine.Host
import arcanum.engine.decision.Decision
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A host's new order of a library while a resolution waits on a decision (problem report DH3QX6): the step is redone from its
 * start with the answer (D29), so the cards it read on the way to the decision stay where they are; the rest of the library
 * is still reordered. Without this the step, redone, reads other cards than the decision shows and refuses the answer.
 */
class ReorderAtOpenChoiceTest {
    private val dread = Cards.invent("Looming Unease", "{0}", "Sorcery", "Manifest dread.")
    private val scry = Cards.invent("Glimpse Ahead", "{0}", "Sorcery", "Scry 2.")
    private val surveil = Cards.invent("Quiet Watch", "{0}", "Sorcery", "Surveil 2.")
    private val look = Cards.invent("Careful Study", "{0}", "Sorcery",
        "Look at the top three cards of your library. Put one of them into your hand and the rest on the bottom of your library in any order.")
    private val mill = Cards.invent("Rummage Below", "{0}", "Sorcery",
        "Mill two cards. You may put a creature card from among them into your hand.")
    private val search = Cards.invent("Patient Search", "{0}", "Sorcery",
        "Search your library for a creature card, put it into your hand, then shuffle.")

    private val deep = arrayOf("Elvish Seer", "Swamp", "Island", "Plains", "Forest", "Mountain")

    /** Every library put in the reverse of its unknown parts' order, as a new line's first step does in a new random one;
     * true when alice's moved. */
    private fun Scenario.newLine(): Boolean {
        val before = state.player(alice.id).library.toList()
        state.players.toList().forEach { p ->
            Host.reorderUnknown(state, p.id, Host.unknownOrder(state, p.id).map { it.reversed() })?.let(::change)
        }
        return state.player(alice.id).library != before
    }

    // CR 701.40e, CR 401.2: the two cards manifest dread looked at stay on top; the same decision is answered as shown
    @Test
    fun aReorderWhileManifestDreadAsksKeepsTheTwoCards() = scenario {
        alice.library(*deep)
        alice.hand(dread)
        alice.casts(dread)
        allPass()
        val asked = assertNotNull(pending as? Decision.ChooseObjects)
        val top = state.player(alice.id).library.take(2)
        assertEquals(top.toSet(), asked.options.toSet())
        assertEquals(listOf(state.player(alice.id).library.drop(2)), Host.unknownOrder(state, alice.id))
        assertTrue(newLine(), "the rest of the library moves")
        assertEquals(top, state.player(alice.id).library.take(2))
        alice.chooses("Elvish Seer")
        expect {
            card("Elvish Seer").zone shouldBe Zone.Battlefield
            alice.graveyard has "Swamp"
        }
    }

    // CR 701.22a: a scry's cards stay in place while it asks
    @Test
    fun aReorderWhileAScryAsksKeepsItsCards() = scenario {
        alice.library(*deep)
        alice.hand(scry)
        alice.casts(scry)
        allPass()
        assertNotNull(pending as? Decision.ChooseObjects)
        val top = state.player(alice.id).library.take(2)
        assertTrue(newLine(), "the rest of the library moves")
        assertEquals(top, state.player(alice.id).library.take(2))
        alice.chooses("Elvish Seer")
        expect { card("Elvish Seer").zone shouldBe Zone.Library }
    }

    // CR 701.25a: a surveil's cards stay in place while it asks
    @Test
    fun aReorderWhileASurveilAsksKeepsItsCards() = scenario {
        alice.library(*deep)
        alice.hand(surveil)
        alice.casts(surveil)
        allPass()
        assertNotNull(pending as? Decision.ChooseObjects)
        val top = state.player(alice.id).library.take(2)
        assertTrue(newLine(), "the rest of the library moves")
        assertEquals(top, state.player(alice.id).library.take(2))
        alice.chooses("Elvish Seer")
        expect { alice.graveyard has "Elvish Seer" }
    }

    // CR 401.2: a look at the top three stays on those three while it asks
    @Test
    fun aReorderWhileALookAtTheTopAsksKeepsTheCards() = scenario {
        alice.library(*deep)
        alice.hand(look)
        alice.casts(look)
        allPass()
        val asked = assertNotNull(pending as? Decision.ChooseObjects)
        val top = state.player(alice.id).library.take(3)
        assertEquals(top.toSet(), asked.options.toSet())
        assertTrue(newLine(), "the rest of the library moves")
        assertEquals(top, state.player(alice.id).library.take(3))
        alice.chooses("Island")
        expect { card("Island").zone shouldBe Zone.Hand }
    }

    // CR 701.17a: the cards a mill took before the question stay the ones it takes when it is redone
    @Test
    fun aReorderWhileAMillAsksKeepsTheMilledCards() = scenario {
        alice.library(*deep)
        alice.hand(mill)
        alice.casts(mill)
        allPass()
        assertNotNull(pending)
        newLine()
        val answered = pending
        if (answered is Decision.ChooseObjects) alice.chooses("Elvish Seer") else alice.answers(arcanum.engine.decision.Answer.YesNo(true))
        (pending as? Decision.ChooseObjects)?.let { alice.chooses("Elvish Seer") }
        expect {
            card("Elvish Seer").zone shouldBe Zone.Hand
            alice.graveyard has "Swamp"
        }
    }

    // CR 701.23a: a search's answer stands while it asks: the whole library it looks through stays as it is, and it is
    // shuffled after the choice anyway
    @Test
    fun aReorderWhileASearchAsksStillTakesTheAnswer() = scenario {
        alice.library(*deep)
        alice.hand(search)
        alice.casts(search)
        allPass()
        assertNotNull(pending as? Decision.ChooseObjects)
        newLine()
        alice.chooses("Elvish Seer")
        expect { card("Elvish Seer").zone shouldBe Zone.Hand }
    }
}
