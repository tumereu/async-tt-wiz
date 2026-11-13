package arcanum.engine.sets.mom

import arcanum.api.CounterKind
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals

/** MOM fix chunk: rules fixes found while working the set. */
class MomFixesScenariosTest {
    private val renderLifeless = Cards.invent("Render Lifeless", "{2}{B}", "Sorcery", """
        Remove up to five counters from target permanent.
        Draw a card.
    """.trimIndent())
    private val drudge = Cards.invent("Momentide Drudge", "{2}{G}", "Creature — Zombie", "", "1/1")
    private val exhumer = Cards.invent("Momentide Exhumer", "{3}{B}", "Creature — Zombie",
        "When this creature enters, put up to two creature cards from your graveyard on top of your library.", "3/3")
    private val bear = Cards.invent("Momentide Grave Bear", "{1}{G}", "Creature — Bear", "", "2/2")

    // CR 608.2d: "remove up to N counters" names no kind: as it resolves, its controller chooses which counters (here the stun
    // counter and one of the three +1/+1 counters added before it), not the first ones added
    @Test
    fun removingCountersOfAnyKindAsksWhichCounters() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(renderLifeless)
        alice.library("Swamp")
        bob.battlefield(drudge, counters = listOf(CounterKind.PowerToughness(1, 1) to 3, CounterKind.Stun to 1))
        alice.casts(renderLifeless, target = drudge)
        resolveAll()
        answer(Answer.Number(2))
        val choice = pending as Decision.ChooseOption
        assertEquals(alice.id, choice.player)
        assertEquals(setOf("+1/+1", "stun"), choice.options.toSet())
        answer(Answer.Option("stun"))
        // only +1/+1 counters left: the second needs no question
        resolveAll()
        expect {
            drudge.counters(CounterKind.Stun) shouldBe 0
            drudge.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            alice.hand.size shouldBe 1
        }
    }

    // CR 609.3: with every counter going, nothing is left to choose
    @Test
    fun removingAtLeastAllCountersAsksNothing() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(renderLifeless)
        alice.library("Swamp")
        bob.battlefield(drudge, counters = listOf(CounterKind.PowerToughness(1, 1) to 1, CounterKind.Stun to 1))
        alice.casts(renderLifeless, target = drudge)
        resolveAll()
        answer(Answer.Number(5))
        resolveAll()
        expect {
            drudge.counters(CounterKind.Stun) shouldBe 0
            drudge.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    // CR 608.2c: two cards chosen as it resolves; CR 401.4: put on top at once, their owner orders them (first: topmost)
    @Test
    fun twoCardsPutOnTopAreOrderedByTheirOwner() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(exhumer)
        alice.graveyard(drudge, bear)
        alice.library("Swamp")
        alice.casts(exhumer)
        resolveAll()
        alice.chooses(drudge, bear)
        val order = pending as Decision.ChooseObjects
        assertEquals(alice.id, order.player)
        answer(Answer.Objects(listOf(card(bear), card(drudge))))
        resolveAll()
        assertEquals(listOf(card(bear), card(drudge)), state.player(alice.id).library.take(2))
    }
}
