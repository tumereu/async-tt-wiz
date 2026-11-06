package arcanum.engine.sets.blb

import arcanum.api.Ability
import arcanum.api.CostPart
import arcanum.api.KeywordAction
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.Blb.bear
import arcanum.engine.sets.blb.Blb.food
import arcanum.engine.sets.blb.Blb.p1p1
import arcanum.engine.state.StackItemKind
import arcanum.engine.view.AbilityOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A creature with offspring and "When this creature enters, you may forage. If you do, put two +1/+1 counters on it."
 * (owner playtest 2026-11-06) played step by step: both enter triggers and their order, the token copy's own trigger,
 * forage's two ways to pay, declining, and what each question says.
 */
class BlbOffspringForageWalkthroughTest {
    private val sentry = Cards.invent("Hollowroot Sentry", "{2}{G}", "Creature — Squirrel Warrior", """
        Offspring {2} (You may pay an additional {2} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
        When this creature enters, you may forage. If you do, put two +1/+1 counters on it. (To forage, exile three cards from your graveyard or sacrifice a Food.)
    """, "2/2")

    private val forageText = "When this creature enters, you may forage. If you do, put two +1/+1 counters on it."

    private fun Scenario.paid() {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(sentry)
        alice.casts(sentry, kicked = 1)
        allPass()
    }

    /** The open "you may forage" question: its sentence and its cost. */
    private fun Scenario.forageQuestion(): Decision.May {
        val may = decision<Decision.May>()
        assertEquals("You may forage. If you do, put two +1/+1 counters on it.", may.text)
        assertEquals(listOf<CostPart>(CostPart.Action(KeywordAction.Forage)), may.cost?.parts)
        return may
    }

    /** Offspring paid: as the spell resolves, both enter abilities trigger at once and alice orders them; the offspring
     * one is the keyword's expansion (the Stack shows it by its keyword), the forage one its printed sentence. */
    // CR 702.175a: offspring is an optional additional cost and "when this permanent enters, if its offspring cost was
    // paid, create a token that's a copy of it, except it's 1/1"; CR 603.3b: triggers that trigger together are put
    // on the Stack in the order their controller chooses
    @Test
    fun bothEnterTriggersAreOrderedByTheirController() = scenario {
        paid()
        val order = decision<Decision.OrderTriggers>()
        assertEquals(alice.id, order.player)
        assertEquals(listOf("Offspring", forageText), order.triggers.map { it.text })
        assertTrue(order.triggers[0].origin is AbilityOrigin.Expansion)
        // Forage first: it goes on the Stack first and resolves last
        answer(Answer.Order(listOf(1, 0)))
        val items = state.stack.map { state.stackItems.getValue(it) }
        assertTrue(items.all { it.kind == StackItemKind.Triggered && it.source == card(sentry) })
        assertEquals(forageText, (items[0].ability as Ability.Triggered).text)
        assertNull((items[1].ability as Ability.Triggered).text)
        assertTrue(items[1].origin is AbilityOrigin.Expansion)
    }

    /** Offspring's trigger resolving first: the 1/1 token copy enters, and only its copied forage ability triggers:
     * its own offspring cost was never paid. Then the original's forage resolves, then the token's. */
    // CR 707.2, 707.9b: the token copies the printed abilities, except it's 1/1; CR 603.4: the copy's offspring
    // trigger checks "if its offspring cost was paid" as it would trigger, and it wasn't (the token wasn't cast)
    @Test
    fun theTokenCopyHasOnlyItsOwnForageTrigger() = scenario {
        alice.graveyard(bear, bear, bear)
        paid()
        // Offspring last on the Stack: it resolves first
        answer(Answer.Order(listOf(1, 0)))
        allPass()
        val token = card(sentry, 1)
        expect {
            card(sentry, 1).power shouldBe 1
            card(sentry, 1).toughness shouldBe 1
            stack.size shouldBe 2
        }
        assertTrue(state.objOrThrow(token).token)
        val top = state.stackItems.getValue(state.stack.last())
        assertEquals(token, top.source)
        assertEquals(forageText, (top.ability as Ability.Triggered).text)
        // The token's forage resolves first (it is on top): exiling the three cards is the only way to pay
        allPass()
        forageQuestion()
        answer(Answer.YesNo(true))
        expect {
            card(sentry, 1).counters(p1p1) shouldBe 2
            card(sentry, 1).power shouldBe 3
            alice.exile.size shouldBe 3
            stack.size shouldBe 1
        }
        // Then the original's: nothing is left to forage with, so nothing is asked and it gets no counters (CR 118.3)
        allPass()
        expect {
            card(sentry, 0).counters(p1p1) shouldBe 0
            stack.isEmpty()
        }
        assertNull(pending)
    }

    /** With three cards in the graveyard and a Food, the question after "yes" is which way to forage, saying what foraging
     * is; exiling then picks the three cards when there are more. */
    // CR 701.61a: to forage means "exile three cards from your graveyard or sacrifice a Food"; CR 118.12: "you may
    // [do something]. If you do, [effect]": the action is a cost paid on resolution
    @Test
    fun forageAsksWhichWayAndExileChoosesTheCards() = scenario {
        alice.battlefield(food)
        alice.graveyard(bear, bear, bear, bear)
        paid()
        // The offspring trigger first on the Stack, forage on top: the original forages first
        answer(Answer.Order(listOf(0, 1)))
        allPass()
        val may = forageQuestion()
        assertEquals(card(sentry), may.source)
        assertTrue(may.payable)
        answer(Answer.YesNo(true))
        val way = decision<Decision.ChooseOption>()
        assertEquals(listOf("exile", "sacrifice"), way.options)
        assertEquals("To forage, exile three cards from your graveyard or sacrifice a Food.", way.text)
        answer(Answer.Option("exile"))
        val cards = decision<Decision.ChooseObjects>()
        assertEquals(3, cards.min)
        assertEquals(3, cards.max)
        assertEquals(4, cards.options.size)
        answer(Answer.Objects(cards.options.take(3)))
        expect {
            card(sentry, 0).counters(p1p1) shouldBe 2
            card(sentry, 0).power shouldBe 4
            alice.exile.size shouldBe 3
            alice.battlefield has food
        }
        // The offspring trigger: the token copy enters without the counters (CR 707.2: counters aren't copied)
        allPass()
        expect { card(sentry, 1).counters(p1p1) shouldBe 0 }
        // The token's forage: one card is left in the graveyard, so sacrificing the Food is the only way, asked no more
        allPass()
        forageQuestion()
        answer(Answer.YesNo(true))
        expect {
            card(sentry, 1).counters(p1p1) shouldBe 2
            alice.graveyard has food
            stack.isEmpty()
        }
    }

    /** Sacrificing the Food is the other way. */
    // CR 701.61a
    @Test
    fun forageBySacrificingTheFood() = scenario {
        alice.battlefield(food)
        alice.graveyard(bear, bear, bear)
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(sentry)
        alice.casts(sentry, kicked = 0)
        allPass()
        allPass()
        forageQuestion()
        answer(Answer.YesNo(true))
        answer(Answer.Option("sacrifice"))
        expect {
            card(sentry, 0).counters(p1p1) shouldBe 2
            alice.graveyard has food
            alice.graveyard.size shouldBe 4
            stack.isEmpty()
        }
    }

    /** Declining: nothing is paid and nothing is put on it ("if you do" checks the choice to pay). */
    // CR 118.12, 608.2d
    @Test
    fun decliningForagesNothing() = scenario {
        alice.battlefield(food)
        alice.graveyard(bear, bear, bear)
        paid()
        answer(Answer.Order(listOf(0, 1)))
        allPass()
        forageQuestion()
        answer(Answer.YesNo(false))
        expect {
            card(sentry, 0).counters(p1p1) shouldBe 0
            alice.battlefield has food
            alice.graveyard.size shouldBe 3
        }
        allPass()
        allPass()
        forageQuestion()
        answer(Answer.YesNo(false))
        expect {
            card(sentry, 1).counters(p1p1) shouldBe 0
            stack.isEmpty()
        }
    }

    /** Offspring not paid: only the forage ability triggers, so there is nothing to order; with nothing to forage with,
     * nothing is asked. */
    // CR 603.4: the intervening "if" is false, so the offspring ability doesn't trigger; CR 118.3: a cost that can't be
    // paid isn't offered
    @Test
    fun withoutOffspringOnlyForageTriggers() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(sentry)
        alice.casts(sentry, kicked = 0)
        allPass()
        expect { stack.size shouldBe 1 }
        assertEquals(forageText, (state.stackItems.getValue(state.stack.single()).ability as Ability.Triggered).text)
        allPass()
        assertNull(pending)
        expect {
            alice.battlefield.names.count { it == sentry } shouldBe 1
            card(sentry, 0).counters(p1p1) shouldBe 0
            stack.isEmpty()
        }
        assertEquals(Zone.Battlefield, state.objOrThrow(card(sentry)).zone)
    }
}
