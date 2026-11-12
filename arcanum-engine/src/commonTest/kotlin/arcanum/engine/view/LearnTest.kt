package arcanum.engine.view

import arcanum.engine.Host
import arcanum.engine.change.GameEvent
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Learn (CR 701.48a), played fully by the engine: invented cards, the printed reminder's wording. */
class LearnTest {
    private val study = Cards.invent("Learn Quiet Study", "{0}", "Sorcery", "Learn. (You may reveal a Lesson card you own from outside the game and put it into your hand, or discard a card to draw a card.)")
    private val primer = Cards.invent("Learn Small Primer", "{U}", "Sorcery — Lesson", "Draw a card.")
    private val treatise = Cards.invent("Learn Long Treatise", "{2}{U}", "Sorcery — Lesson", "Draw two cards.")
    private val bear = Cards.invent("Learn Plain Bear", "{1}{G}", "Creature — Bear", "", "2/2")

    private val lessonOption = Answer.Option("a Lesson card from outside the game")
    private val rummageOption = Answer.Option("discard a card to draw a card")
    private val nothingOption = Answer.Option("nothing")

    private fun Scenario.outside(vararg names: String) = put(alice, names.toList(), Zone.Outside)

    private fun Scenario.toTheQuestion() {
        alice.hand(study)
        alice.casts(study)
        allPass()
        assertIs<Decision.ChooseOption>(pending)
    }

    private fun Scenario.revealed() = applied.filterIsInstance<Change.Happened>().map { it.event }.filterIsInstance<GameEvent.Revealed>()

    // CR 701.48a: only Lessons are offered; the one chosen is revealed and goes to the hand; the rest stay outside the game
    @Test
    fun aLessonIsRevealedAndTakenAndTheRestStayOutside() = scenario {
        val (first, second, other) = outside(primer, treatise, bear)
        toTheQuestion()
        alice.answers(lessonOption)
        assertEquals(setOf(first, second), decision<Decision.ChooseObjects>().options.toSet())
        alice.chooses(treatise)
        assertEquals(listOf(listOf(second)), revealed().map { it.ids }, "only the Lesson is revealed")
        expect { alice.hand has treatise }
        assertEquals(setOf(first, other), state.player(alice.id).outside.toSet())
        assertNull(pending)
    }

    // CR 701.48a: with no Lesson outside the game, choosing one is no stall on a pick with nothing to pick: nothing happens
    @Test
    fun noLessonOutsideDoesNotStall() = scenario {
        outside(bear)
        toTheQuestion()
        alice.answers(lessonOption)
        assertNull(pending)
        assertEquals(emptyList(), revealed())
        expect { alice.hand.size shouldBe 0 }
    }

    // CR 701.48a, I-D18: the same with "Skip when I have no choice" off: cards only she sees decide, so she is asked the empty
    // choice, with only its one answer
    @Test
    fun noLessonOutsideIsAskedEmptyWithSkippingOff() = scenario {
        alice.skipWhenNoChoice = false
        outside(bear)
        toTheQuestion()
        alice.answers(lessonOption)
        val d = decision<Decision.ChooseObjects>()
        assertEquals(emptyList(), d.options)
        assertEquals(0 to 0, d.min to d.max)
        answer(Answer.Objects(emptyList()))
        assertNull(pending)
        assertEquals(emptyList(), revealed())
    }

    // CR 701.48a, CR 609.3: one Lesson outside, one she chose to take: nothing more to ask
    @Test
    fun theOnlyLessonIsTaken() = scenario {
        outside(primer, bear)
        toTheQuestion()
        alice.answers(lessonOption)
        assertNull(pending)
        expect { alice.hand has primer }
        assertEquals(1, revealed().size)
    }

    // CR 701.48a: discard a card, and only then draw; the option already said so, no second "may". She did discard, so no Lesson
    @Test
    fun rummagingDiscardsThenDraws() = scenario {
        outside(primer)
        alice.library("Island")
        alice.hand("Forest", "Plains")
        toTheQuestion()
        alice.answers(rummageOption)
        alice.chooses("Plains")
        assertNull(pending)
        expect {
            alice.graveyard has "Plains"
            alice.hand has "Forest"
            alice.hand has "Island"
        }
    }

    // CR 701.48a: "If you do, draw a card": with an empty hand nothing is discarded and nothing is drawn
    @Test
    fun rummagingWithAnEmptyHandDoesNotDraw() = scenario {
        alice.library("Island")
        toTheQuestion()
        alice.answers(rummageOption)
        assertNull(pending)
        expect {
            alice.hand.size shouldBe 0
            alice.library.size shouldBe 1
        }
    }

    // CR 701.48a: "If you didn't discard a card, you may reveal a Lesson": choosing to discard with an empty hand discards nothing,
    // so she still may: she takes it, and still draws nothing
    @Test
    fun anEmptyHandMayStillTakeALesson() = scenario {
        val (first) = outside(primer)
        alice.library("Island")
        toTheQuestion()
        alice.answers(rummageOption)
        val d = decision<Decision.ChooseObjects>()
        assertEquals(listOf(first), d.options)
        assertEquals(0 to 1, d.min to d.max)
        alice.chooses(primer)
        assertNull(pending)
        assertEquals(listOf(listOf(first)), revealed().map { it.ids })
        expect {
            alice.hand has primer
            alice.library.size shouldBe 1
        }
    }

    // CR 701.48a: that "may" is hers to decline: choosing none reveals nothing, and the Lesson stays outside the game
    @Test
    fun anEmptyHandMayDeclineTheLesson() = scenario {
        val (first) = outside(primer)
        toTheQuestion()
        alice.answers(rummageOption)
        answer(Answer.Objects(emptyList()))
        assertNull(pending)
        assertEquals(emptyList(), revealed())
        assertEquals(listOf(first), state.player(alice.id).outside.toList())
        expect { alice.hand.size shouldBe 0 }
    }

    // CR 701.48a, I-D18: an empty hand and no Lesson outside: no stall, skipping on or off (off, cards only she sees decide, so
    // she is asked the empty choice with its one answer)
    @Test
    fun anEmptyHandWithNoLessonOutsideDoesNotStall() = listOf(true, false).forEach { skip ->
        scenario {
            alice.skipWhenNoChoice = skip
            outside(bear)
            toTheQuestion()
            alice.answers(rummageOption)
            if (!skip) {
                val d = decision<Decision.ChooseObjects>()
                assertEquals(emptyList(), d.options)
                answer(Answer.Objects(emptyList()))
            }
            assertNull(pending)
            assertEquals(emptyList(), revealed())
        }
    }

    // CR 701.48a: both parts are "may": she may do neither
    @Test
    fun sheMayDoNeither() = scenario {
        outside(primer)
        alice.library("Island")
        alice.hand("Forest")
        toTheQuestion()
        alice.answers(nothingOption)
        assertNull(pending)
        expect {
            alice.hand has "Forest"
            alice.hand lacks primer
            alice.library.size shouldBe 1
        }
        assertEquals(emptyList(), revealed())
    }

    // CR 701.48a, CR 400.2, D60: in Bob's view her cards outside the game are unknown; he learns the Lesson she reveals and nothing else
    @Test
    fun theOtherPlayerLearnsOnlyTheRevealedLesson() = scenario {
        viewer(bob)
        val (_, second, other) = outside(primer, treatise, bear)
        alice.hand(study)
        alice.casts(study)
        allPass()
        var guard = 6
        while (pending != null && guard-- > 0) {
            val d = pending
            val asked = if (d is Decision.OtherView) Host.otherViewDecision(state, otherView(d, emptyList()).filled) else d
            when (asked) {
                is Decision.ChooseOption -> alice.answers(lessonOption)
                is Decision.ChooseObjects -> alice.chooses(treatise)
                null -> break
                else -> kotlin.test.fail("Unexpected: $asked")
            }
        }
        assertNull(pending, "$pending")
        val seen = applied.filterIsInstance<Change.Filled>().mapNotNull { it.after.definition?.faces?.first()?.name }.toSet()
        assertTrue(hostKnows(second).faces.first().name in seen, "the revealed Lesson: $seen")
        assertTrue(primer !in seen && bear !in seen, "nothing of the rest: $seen")
        assertTrue(state.player(alice.id).outside.all { state.objOrThrow(it).unknown }, "the rest still unknown")
        assertTrue(other in state.player(alice.id).outside)
        assertEquals(1, state.player(alice.id).hand.size)
    }
}
