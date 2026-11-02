package arcanum.engine.sets.fra

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** More Reality Fracture cards in play: an X loyalty cost, targets for each opponent from an activated and a reflexive
 * ability, a reflexive trigger with as many targets as nonland cards discarded, removing up to three counters, searching for
 * as many lands as the damage dealt, discarding a card that triggers itself, a delayed trigger for attacks until your next
 * turn, an activation needing loyalty among Jaces, and an attack trigger after a loyalty activation. */
class FraMoreCardsScenariosTest {
    private val frost = Cards.invent("Velka, Frost of Obedience", "{1}{U}{U}", "Legendary Planeswalker — Chandra", """
        [+1]: Surveil 1. If you put a noncreature, nonland card into your graveyard this way, put that card into your hand.
        [+1]: Add {U}. Spend this mana only to cast a noncreature spell.
        [−X]: Tap target artifact or creature. Put X stun counters on it.
        [−6]: You get an emblem with "Whenever you cast a spell, draw a card."
    """, loyalty = "3")
    private val postulant = Cards.invent("The Postulant, Iren Valcourt", "{2}{U}{U}", "Legendary Planeswalker — Jace", """
        At the beginning of each opponent's draw step, you draw a card.
        [+1]: Create a 1/1 blue Illusion creature token.
        [−2]: For each opponent, return up to one target artifact or creature that player controls to its owner's hand.
        [−6]: Draw three cards. Then put X +1/+1 counters on each creature you control, where X is the number of cards in your hand.
    """, loyalty = "3")
    private val charm = Cards.invent("Lichwarden's Charm", "{B}", "Artifact — Equipment", """
        When this Equipment enters, you may pay {2}. When you do, for each opponent, destroy up to one target creature or planeswalker that player controls.
        Equipped creature gets +2/+1.
        Equip {2}
    """)
    private val frostweaver = Cards.invent("Veteran Frostweaver", "{1}{U}{U}", "Creature — Human Wizard", """
        When this creature enters, draw two cards, then discard two cards. When you discard one or more nonland cards this way, tap up to that many target creatures and put a stun counter on each of them.
        {3}{U}{U}, Exile this card from your graveyard: Draw two cards.
    """, "2/2")
    private val pipwick = Cards.invent("Pipwick, Sour Hermit", "{B}", "Legendary Creature — Mouse Warlock", """
        Deathtouch
        When Pipwick enters, remove up to three counters from another target creature or planeswalker.
    """, "1/1")
    private val revitalizer = Cards.invent("Hexward Revitalizer", "{G}{G}{G}{G}", "Creature — Chimera Horror", """
        Vigilance
        Whenever this creature is dealt damage, you may search your library for up to that many land cards, put them onto the battlefield tapped, then shuffle.
    """, "6/6")
    private val colossbones = Cards.invent("Colossbones, Looming Heart", "{3}{G}", "Legendary Creature — Skeleton Druid", """
        Reach
        Whenever you gain life, put two +1/+1 counters on Colossbones.
        When you discard this card, you gain 3 life.
    """, "4/3")
    private val sunderer = Cards.invent("Morrow, Hex Sunderer", "{3}{G}{G}", "Legendary Planeswalker — Garruk", """
        Whenever a creature you control with power 4 or greater enters, draw a card.
        [+2]: Untap up to two target lands.
        [−3]: Create a 4/4 green Beast creature token with trample.
        [−4]: Until your next turn, whenever one or more creatures attack one of your opponents, those creatures get +2/+2 and gain trample until end of turn.
    """, loyalty = "5")
    private val shaper = Cards.invent("Iren, Reality Shaper", "{3}{U}{U}", "Legendary Planeswalker — Jace", """
        [+1]: Empower Jace X, where X is the number of Islands you control.
        [−3]: Until your next turn, whenever a creature attacks you or a planeswalker you control, it gets -5/-0 until end of turn.
        [0]: Exile all but the bottom card of each opponent's library. Activate only if there are twenty-five or more loyalty counters among Jaces you control.
    """, loyalty = "5")
    private val seraphine = Cards.invent("Seraphine of Brine and Dune", "{1}{G}{U}", "Legendary Creature — Merfolk Noble", """
        Whenever you attack, if you've activated a loyalty ability this turn, untap target attacking creature. It can't be blocked this turn.
        Planeswalkers you control have "[−8]: Create an 8/8 blue Leviathan creature token with hexproof."
    """, "2/4")
    private val bear = Cards.invent("Market Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Wide Ox", "{2}{G}", "Creature — Ox", "", "1/4")
    private val study = Cards.invent("Deep Study", "{2}{U}", "Sorcery", "Draw two cards.")
    private val shock = Cards.invent("Quick Shock", "{R}", "Instant", "Quick Shock deals 2 damage to any target.")
    private val heal = Cards.invent("Warm Embrace", "{W}", "Instant", "You gain 3 life.")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private fun Scenario.resolveStackFully(yes: Boolean = true) {
        var guard = 40
        while (guard-- > 0) {
            when {
                pending is Decision.May -> answer(Answer.YesNo(yes))
                pending is Decision.OrderTriggers -> answer(Answer.Order(decision<Decision.OrderTriggers>().triggers.map { it.index }))
                pending != null -> return
                state.stack.isNotEmpty() -> allPass()
                else -> return
            }
        }
    }

    /** "[−X]: Tap target artifact or creature. Put X stun counters on it.": X loyalty counters removed. */
    // CR 107.3a, 606.4, 122.1d
    @Test
    fun minusXStuns() = scenario {
        alice.battlefield(frost, counters = listOf(CounterKind.Loyalty to 3))
        bob.battlefield(ox)
        alice.activates(frost, ability = 2, target = "Wide Ox", x = 2)
        resolveStackFully()
        expect {
            "Wide Ox".tapped shouldBe true
            "Wide Ox".counters(CounterKind.Stun) shouldBe 2
            frost.counters(CounterKind.Loyalty) shouldBe 1
        }
    }

    /** "For each opponent, return up to one target … that player controls to its owner's hand." */
    // CR 601.2c, 608.2b
    @Test
    fun postulantBouncesOnePerOpponent() = scenario {
        alice.battlefield(postulant, counters = listOf(CounterKind.Loyalty to 3))
        bob.battlefield(ox)
        alice.activates(postulant, ability = 1, target = "Wide Ox")
        resolveStackFully()
        expect { bob.hand has "Wide Ox" }
    }

    /** A reflexive trigger after paying {2}, destroying one creature for each opponent. */
    // CR 603.12, 601.2c
    @Test
    fun charmDestroysAfterPaying() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        bob.battlefield(ox)
        alice.hand(charm)
        alice.casts(charm, paying = listOf("Swamp"))
        resolveStackFully()
        if (pending is Decision.ManaPayment) alice.answers(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target("Wide Ox"))))
        resolveStackFully()
        expect { bob.graveyard has "Wide Ox" }
    }

    /** As many targets as nonland cards discarded, each tapped with a stun counter. */
    // CR 603.12, 601.2c
    @Test
    fun frostweaverStunsPerNonlandDiscard() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.library(shock, study)
        bob.battlefield(bear, ox)
        alice.hand(frostweaver)
        alice.casts(frostweaver)
        resolveStackFully()
        if (pending is Decision.ChooseObjects) alice.chooses(shock, study)
        resolveStackFully()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target("Market Bear"), target("Wide Ox"))))
        resolveStackFully()
        expect {
            "Market Bear".counters(CounterKind.Stun) shouldBe 1
            "Wide Ox".counters(CounterKind.Stun) shouldBe 1
        }
    }

    /** "Remove up to three counters from another target creature or planeswalker." */
    // CR 122.8
    @Test
    fun pipwickRemovesCounters() = scenario {
        alice.battlefield("Swamp")
        bob.battlefield(ox, counters = listOf(p1p1 to 4))
        alice.hand(pipwick)
        alice.casts(pipwick)
        resolveStackFully()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target("Wide Ox"))))
        resolveStackFully()
        if (pending is Decision.ChooseNumber) answer(Answer.Number(3))
        resolveStackFully()
        expect { "Wide Ox".counters(p1p1) shouldBe 1 }
    }

    /** Dealt damage: search for up to that many lands, onto the battlefield tapped. */
    // CR 120.3, 701.23
    @Test
    fun revitalizerFetchesLands() = scenario {
        alice.battlefield(revitalizer)
        alice.library("Forest", "Forest", "Island")
        bob.battlefield("Mountain")
        bob.hand(shock)
        turn(active = bob)
        bob.casts(shock, target = "Hexward Revitalizer")
        resolveStackFully()
        if (pending is Decision.ChooseObjects) alice.chooses("Forest", "Forest")
        resolveStackFully()
        expect { alice.library.names shouldBe listOf("Island") }
    }

    /** "When you discard this card, you gain 3 life.": it works from the hand. */
    // CR 113.6k, 701.9a
    @Test
    fun colossbonesDiscarded() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.library(bear, bear)
        alice.hand(colossbones, Cards.invent("Rummage Through", "{1}{R}", "Sorcery", "Discard a card, then draw two cards."))
        alice.battlefield("Mountain")
        alice.casts("Rummage Through")
        resolveStackFully()
        if (pending is Decision.ChooseObjects) alice.chooses(colossbones)
        resolveStackFully()
        expect { alice.life shouldBe 23 }
    }

    /** "Until your next turn, whenever one or more creatures attack one of your opponents, those creatures get +2/+2 and
     * gain trample until end of turn." */
    // CR 603.7b
    @Test
    fun sundererPumpsAttackers() = scenario {
        alice.battlefield(bear)
        alice.battlefield(sunderer, counters = listOf(CounterKind.Loyalty to 5))
        alice.activates(sunderer, ability = 2)
        resolveStackFully()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveStackFully()
        expect {
            "Market Bear".power shouldBe 4
            "Market Bear" hasKeyword Keyword.Trample
        }
    }

    /** "[0]: … Activate only if there are twenty-five or more loyalty counters among Jaces you control." */
    // CR 602.5b
    @Test
    fun shaperNeedsTwentyFiveLoyalty() = scenario {
        alice.battlefield(shaper, counters = listOf(CounterKind.Loyalty to 5))
        bob.library(bear, ox, bear)
        assertFailsWith<IllegalArgumentException> { alice.activates(shaper, ability = 2) }
    }

    /** With twenty-five, all but the bottom card of each opponent's library is exiled. */
    // CR 602.5b
    @Test
    fun shaperExilesLibraries() = scenario {
        alice.battlefield(shaper, counters = listOf(CounterKind.Loyalty to 25))
        bob.library(bear, ox, study)
        alice.activates(shaper, ability = 2)
        resolveStackFully()
        expect {
            bob.library.names shouldBe listOf("Deep Study")
            bob.exile.size shouldBe 2
        }
    }

    /** "Whenever you attack, if you've activated a loyalty ability this turn, untap target attacking creature. It can't be
     * blocked this turn." */
    // CR 508.3d, 603.4
    @Test
    fun seraphineAfterALoyaltyAbility() = scenario {
        alice.battlefield(seraphine, bear)
        alice.battlefield(sunderer, counters = listOf(CounterKind.Loyalty to 5))
        alice.library(study, study)
        alice.activates(sunderer, ability = 1)
        resolveStackFully()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveStackFully()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target("Market Bear"))))
        resolveStackFully()
        expect {
            "Market Bear".tapped shouldBe false
            "Market Bear" hasMechanic { it == arcanum.api.Mechanic.CantBeBlocked }
        }
    }
}
