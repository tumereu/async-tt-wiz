package arcanum.engine.sets.trk

import arcanum.api.CounterKind
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.modes
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.dft.payYes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Star Trek chunk 01 in play: the lands that trigger when they enter untapped, a creature that changes its creature types
 * and gains abilities in two steps, a resolution cost of two parts with a reflexive trigger, and plain untap, destroy and
 * graveyard-to-library abilities. The modal enters and attack triggers, the end-step token and the once-each-turn
 * graveyard trigger repeat shapes tested before. */
class TrkChunk01SurveilScenariosTest {
    private val den = Cards.invent("Smoky Tavern Den", null, "Land", """
        This land enters tapped unless you control two or more other lands.
        When this land enters untapped, choose one —
        • This land deals 1 damage to each opponent.
        • Surveil 1.
        {T}: Add {R}.
    """)
    private val brin = Cards.invent("Brin, Eager Trainee", "{B}{G}", "Legendary Creature — Ferengi Rogue", """
        Menace
        {1}: If Brin is a Rogue, he becomes a Ferengi Scout with base power and toughness 3/4 and gains "Whenever Brin becomes tapped, surveil 2."
        {3}: If Brin is a Scout, he becomes a Ferengi Officer and gains "You may play lands from your graveyard" and "You may play an additional land on each of your turns."
    """, "2/2")
    private val mira = Cards.invent("Mira Quell, Keen Explorer", "{2}{G}", "Legendary Creature — Human Scientist Artificer", """
        When Mira Quell enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        At the beginning of combat on your turn, you may pay {1} and exile a card from your graveyard. When you do, put a +1/+1 counter on target creature.
    """, "2/2")
    private val pell = Cards.invent("Pell Ostrow, Hopeful Ensign", "{1}{G/U}{G/U}", "Legendary Creature — Orion Scientist", """
        {T}: Untap another target permanent.
        {3}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
    """, "2/4")
    private val verdict = Cards.invent("Flatlined Verdict", "{3}{B}", "Instant",
        "Destroy target creature. Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)")
    private val analyst = Cards.invent("Augmented Analyst", "{2}", "Artifact Creature — Human Scientist", """
        When this creature enters, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        {1}, {T}: Put target card from your graveyard on the bottom of your library.
    """, "2/1")
    private val cadet = Cards.invent("Trk Test Cadet", "{1}{G}", "Creature — Human Soldier", "", "2/2")
    // Amulet of Vigor's wording, read by the same rule as "enters untapped"
    private val charm = Cards.invent("Brass Charm of Vigor", "{1}", "Artifact", "Whenever a permanent you control enters tapped, untap it.")
    private val quarry = Cards.invent("Trk Tapped Quarry", null, "Land", """
        This land enters tapped.
        {T}: Add {R}.
    """)

    // CR 603.6a, 603.10: the land entered untapped (two other lands), so it triggers; CR 120.3: 1 damage to each opponent
    @Test
    fun untappedLandHitsEachOpponent() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(den)
        alice.plays(den)
        modes(0)
        resolveAll()
        expect { den.tapped shouldBe false }
        assertEquals(20, alice.life)
        assertEquals(19, bob.life)
        assertEquals(19, carol.life)
    }

    // CR 603.6a, 614.1c: entering tapped, it doesn't trigger
    @Test
    fun tappedLandDoesNotTrigger() = scenario(players = 3) {
        alice.battlefield("Mountain")
        alice.hand(den)
        alice.plays(den)
        assertNull(pending)
        assertTrue(state.stack.isEmpty())
        expect { den.tapped shouldBe true }
        assertEquals(20, bob.life)
    }

    // CR 205.1a, 613.4b: the Rogue becomes a 3/4 Scout for good; CR 603.2: its gained ability triggers when it becomes tapped
    @Test
    fun brinBecomesAScoutThatSurveilsWhenTapped() = scenario {
        alice.battlefield(brin, "Swamp")
        alice.library("Island", "Forest", "Plains")
        alice.activates(brin)
        resolveAll()
        expect {
            brin.power shouldBe 3
            brin.toughness shouldBe 4
            brin.subtypes shouldBe listOf(Subtype("Ferengi"), Subtype("Scout"))
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(brin)
        resolveAll()
        val surveil = decision<Decision.ChooseObjects>()
        answer(Answer.Objects(surveil.options.take(2)))
        resolveAll()
        expect { alice.graveyard.size shouldBe 2 }
    }

    // CR 608.2b: "If Brin is a Scout" is checked as it resolves: a Rogue gains nothing; CR 305.2: then, as an Officer, one more
    // land play, and lands are played from the graveyard
    @Test
    fun brinBecomesAnOfficerOnlyAfterScout() = scenario {
        alice.battlefield(brin, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand("Plains")
        alice.graveyard("Forest")
        alice.activates(brin, ability = 1)
        resolveAll()
        expect { brin.subtypes shouldBe listOf(Subtype("Ferengi"), Subtype("Rogue")) }
        assertFailsWith<IllegalArgumentException> { alice.plays("Forest") }
        alice.activates(brin, ability = 0)
        resolveAll()
        alice.activates(brin, ability = 1)
        resolveAll()
        expect {
            brin.subtypes shouldBe listOf(Subtype("Ferengi"), Subtype("Officer"))
            brin.power shouldBe 3
        }
        alice.plays("Plains")
        alice.plays("Forest")
        expect {
            alice.battlefield has "Plains"
            alice.battlefield has "Forest"
        }
    }

    // CR 118.12: the cost of two parts paid on resolution; CR 603.12: then the reflexive trigger targets
    @Test
    fun miraPaysAndExilesForACounter() = scenario {
        alice.battlefield(mira, cadet, "Forest")
        alice.graveyard("Island")
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        payYes()
        answer(Answer.Targets(listOf(target(cadet))))
        resolveAll()
        expect {
            cadet.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            alice.exile has "Island"
            alice.graveyard.size shouldBe 0
        }
    }

    // CR 118.12, 603.12: with no card in the graveyard the cost can't be paid, so nothing gets a counter
    @Test
    fun miraWithAnEmptyGraveyardPutsNoCounter() = scenario {
        alice.battlefield(mira, cadet, "Forest")
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        resolveAll()
        expect {
            cadet.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
            mira.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
            "Forest".tapped shouldBe false
        }
    }

    // CR 701.26b, 115.3: untap another permanent, never itself
    @Test
    fun pellUntapsAnotherPermanent() = scenario {
        alice.battlefield(pell)
        alice.battlefield("Forest", tapped = true)
        alice.activates(pell, target = "Forest")
        resolveAll()
        expect {
            "Forest".tapped shouldBe false
            pell.tapped shouldBe true
        }
    }

    // CR 701.8a, 701.25a: destroy, then surveil
    @Test
    fun verdictDestroysThenSurveils() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(verdict)
        alice.library("Island")
        bob.battlefield(cadet)
        alice.casts(verdict, target = cadet)
        resolveAll()
        answer(Answer.Objects(decision<Decision.ChooseObjects>().options))
        resolveAll()
        expect {
            bob.graveyard has cadet
            alice.graveyard has "Island"
        }
    }

    // CR 401.4: a card from the graveyard to the bottom of the library
    @Test
    fun analystPutsACardOnTheBottom() = scenario {
        alice.battlefield(analyst, "Island")
        alice.graveyard("Forest")
        alice.library("Island", "Island")
        alice.activates(analyst, target = "Forest")
        resolveAll()
        expect { alice.library.names.last() shouldBe "Forest" }
    }

    // CR 603.6a, 603.10: a permanent of yours that entered tapped triggers it, and it untaps; one that entered untapped doesn't
    @Test
    fun charmUntapsWhatEntersTapped() = scenario {
        alice.battlefield(charm)
        alice.hand(quarry)
        alice.plays(quarry)
        resolveAll()
        expect { quarry.tapped shouldBe false }
        assertTrue(state.stack.isEmpty())
    }

    @Test
    fun charmIgnoresWhatEntersUntapped() = scenario {
        alice.battlefield(charm, "Mountain")
        alice.hand("Forest")
        alice.plays("Forest")
        assertNull(pending)
        assertTrue(state.stack.isEmpty())
    }
}
