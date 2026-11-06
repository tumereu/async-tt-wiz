package arcanum.engine.sets.mh2

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Modern Horizons 2 cards of parser chunks 01–02 in play: the white cards. Trivial ones not played here: Mender's Flight (keywords
 * only), Arcwired Tabby (modular, as Arcwired Template), Alabaster Gargoyle (a pump ability, as Arcwired Whelp).
 */
class Mh2WhiteScenariosTest {
    private val s = Mh2Set
    private val p = Mh2Plain
    private val p1p1 = Mh2Plain.p1p1

    // ---- Chunk 01

    /** At the end step, the second mode returns a creature card with mana value 1. */
    // CR 700.2
    @Test
    fun steadfastBenedictionReturnsAOneDrop() = scenario {
        alice.battlefield(s.steadfastBenediction)
        alice.graveyard(p.elf)
        turn(step = TurnStep.End)
        beginStep(TurnStep.End)
        modes(1)
        playOut(p.elf)
        expect { alice.battlefield has p.elf }
    }

    /** Removing X counters deals X damage to an attacking creature. */
    // CR 107.3a, 702.43a
    @Test
    fun arcwiredLancerRemovesCounters() = scenario {
        alice.battlefield(s.arcwiredLancer, counters = listOf(p1p1 to 2))
        bob.battlefield(p.ogre)
        turn(active = bob, step = TurnStep.DeclareAttackers, priority = null)
        bob.attacks(p.ogre)
        playOut()
        bob.passes()
        alice.activates(s.arcwiredLancer, target = p.ogre, x = 2)
        playOut()
        expect {
            s.arcwiredLancer.counters(p1p1) shouldBe 0
            card(p.ogre).view.obj?.damage shouldBe 2
        }
    }

    /** Modular 2: it enters with two counters, and as it dies they go onto another artifact creature. */
    // CR 702.43a
    @Test
    fun arcwiredTemplateModular() = scenario {
        alice.battlefield("Plains", "Plains", p.golem)
        alice.hand(s.arcwiredTemplate)
        bob.hand(p.doom)
        bob.battlefield("Swamp")
        alice.casts(s.arcwiredTemplate)
        playOut()
        expect { s.arcwiredTemplate.counters(p1p1) shouldBe 2 }
        alice.passes()
        bob.casts(p.doom, target = s.arcwiredTemplate)
        playOut(p.golem)
        expect { p.golem.counters(p1p1) shouldBe 2 }
    }

    /** It makes a Thopter and attaches itself to it. */
    // CR 701.3a
    @Test
    fun thornedSpurEquipsItsThopter() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(s.thornedSpur)
        alice.casts(s.thornedSpur)
        playOut()
        expect { "Thopter Token".power shouldBe 2 }
    }

    /** An artifact creature gains hexproof and indestructible and gets +2/+2. */
    @Test
    fun smithsWardOnAnArtifactCreature() = scenario {
        alice.battlefield("Plains", p.golem)
        alice.hand(s.smithsWard)
        alice.casts(s.smithsWard, target = p.golem)
        playOut()
        expect {
            p.golem.power shouldBe 5
            p.golem hasKeyword Keyword.Indestructible
        }
    }

    /** Alice can't be targeted by Bob's spells until her next turn; rebound exiles it. */
    // CR 702.11c, 702.88a
    @Test
    fun bloomingStillnessHexproofAndRebound() = scenario {
        alice.battlefield("Plains")
        alice.hand(s.bloomingStillness)
        bob.battlefield("Mountain")
        bob.hand(p.zap)
        alice.casts(s.bloomingStillness)
        playOut()
        expect {
            alice.life shouldBe 22
            alice.exile has s.bloomingStillness
        }
        alice.passes()
        bob.casts(p.zap)
        val d = pending as? Decision.Targets
        assertTrue(d == null || d.options.none { it == target("alice") }, "Alice has hexproof")
    }

    /** One mode destroys an artifact; from the hand, reinforce puts a counter on a creature. */
    // CR 702.77a
    @Test
    fun severTheBondsModeAndReinforce() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", p.bear)
        alice.hand(s.severTheBonds)
        bob.battlefield(p.trinket)
        alice.activates(s.severTheBonds, target = p.bear)
        playOut()
        expect {
            p.bear.counters(p1p1) shouldBe 1
            alice.graveyard has s.severTheBonds
        }
    }

    @Test
    fun severTheBondsDestroysAnArtifact() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(s.severTheBonds)
        bob.battlefield(p.trinket)
        alice.casts(s.severTheBonds, modes = listOf(0), target = p.trinket)
        playOut()
        expect { bob.graveyard has p.trinket }
    }

    /** Devour artifact: it sacrifices an artifact as it enters and gets a counter. */
    // CR 702.82c
    @Test
    fun chromegoatDevoursAnArtifact() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", p.trinket)
        alice.hand(s.chromegoat)
        alice.casts(s.chromegoat)
        playOut(p.trinket)
        expect {
            alice.graveyard has p.trinket
            s.chromegoat.counters(p1p1) shouldBe 1
        }
    }

    /** Renown puts counters on it as it deals combat damage, and that exiles a permanent until it leaves. */
    // CR 702.112a, 610.3
    @Test
    fun wardenOfTheShireRenownExiles() = scenario {
        alice.battlefield(s.wardenOfTheShire)
        bob.battlefield(p.charm)
        hits(s.wardenOfTheShire, picks = listOf(p.charm))
        expect {
            s.wardenOfTheShire.counters(p1p1) shouldBe 2
            bob.exile has p.charm
        }
    }

    /** It returns a permanent card with mana value 3 or less. */
    @Test
    fun acolyteOfDawnlightReturnsACard() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.graveyard(p.ogre)
        alice.hand(s.acolyteOfDawnlight)
        alice.casts(s.acolyteOfDawnlight)
        playOut(p.ogre)
        expect { alice.hand has p.ogre }
    }

    /** Bob's first noncreature spell each turn draws Alice a card unless he pays {X}, X its power. */
    // CR 118.12a
    @Test
    fun esperLookoutTaxesTheFirstSpell() = scenario {
        alice.battlefield(s.esperLookout)
        alice.library(p.ogre)
        bob.battlefield("Island")
        bob.hand(p.notes)
        bob.library(p.ogre)
        turn(active = bob)
        bob.casts(p.notes)
        playOut(may = false)
        expect { alice.hand has p.ogre }
    }

    /** From the graveyard, exiling itself, it makes a Thopter. */
    // CR 113.6k
    @Test
    fun midwayPatrolFromTheGraveyard() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.graveyard(s.midwayPatrol)
        alice.activates(s.midwayPatrol)
        playOut()
        expect {
            alice.exile has s.midwayPatrol
            alice.battlefield has "Thopter Token"
        }
    }

    /** With more life than an opponent, it gains double strike at the beginning of combat. */
    // CR 603.4
    @Test
    fun radiantExecutorDoubleStrike() = scenario {
        alice.battlefield(s.radiantExecutor)
        bob.life = 10
        turn(step = TurnStep.BeginningOfCombat)
        beginStep(TurnStep.BeginningOfCombat)
        playOut()
        expect { s.radiantExecutor hasKeyword Keyword.DoubleStrike }
    }

    @Test
    fun watchfulKirinGrows() = scenario {
        alice.battlefield(s.watchfulKirin, p.bear)
        bob.battlefield("Swamp")
        bob.hand(p.doom)
        turn(active = bob)
        bob.casts(p.doom, target = p.bear)
        playOut()
        expect { s.watchfulKirin.counters(p1p1) shouldBe 1 }
    }

    /** Adapt puts a counter on it, and that gives it double strike. */
    // CR 701.46a
    @Test
    fun squireMyrAdapts() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", s.squireMyr)
        alice.activates(s.squireMyr)
        playOut()
        expect {
            s.squireMyr.counters(p1p1) shouldBe 1
            s.squireMyr hasKeyword Keyword.DoubleStrike
        }
    }

    /** A card of an opponent's graveyard goes to the bottom of their library. */
    // CR 401.4
    @Test
    fun groundskeeperColosBottomsACard() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(s.groundskeeperColos)
        bob.graveyard(p.ogre)
        bob.library(p.bear)
        alice.casts(s.groundskeeperColos)
        playOut(p.ogre)
        expect { bob.library.names.last() shouldBe p.ogre }
    }

    @Test
    fun tardySupperReturnsAndMakesFood() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.graveyard(p.ogre)
        alice.hand(s.tardySupper)
        alice.casts(s.tardySupper, target = p.ogre)
        playOut()
        expect {
            alice.battlefield has p.ogre
            alice.battlefield has "Food Token"
        }
    }

    // ---- Chunk 02

    /** Affinity for artifacts makes it cheaper ({3}{W} with one artifact); it deals 5 damage to an attacking creature. */
    // CR 702.41a
    @Test
    fun glintingGlareWithAffinity() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", p.trinket)
        alice.hand(s.glintingGlare)
        bob.battlefield(p.giant)
        turn(active = bob, step = TurnStep.DeclareAttackers, priority = null)
        bob.attacks(p.giant)
        playOut()
        bob.passes()
        alice.casts(s.glintingGlare, target = p.giant)
        playOut()
        expect { bob.graveyard has p.giant }
    }

    /** Gaining life puts that many counters on each creature, once each turn. */
    // CR 603.2h
    @Test
    fun nyxbornExemplarOnceEachTurn() = scenario {
        alice.battlefield("Plains", "Plains", s.nyxbornExemplar, p.bear)
        alice.hand(p.heal, p.heal)
        alice.casts(p.heal)
        playOut()
        alice.casts(p.heal)
        playOut()
        expect {
            p.bear.counters(p1p1) shouldBe 2
            s.nyxbornExemplar.counters(p1p1) shouldBe 2
        }
    }

    /** Creatures untap and phase out until it leaves; it gets a time counter for each, and they come back when it goes. */
    // CR 702.26, 610.3
    @Test
    fun untimelyStasisPhasesOutCreatures() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", p.bear)
        alice.hand(s.untimelyStasis)
        bob.battlefield(p.ogre, tapped = true)
        alice.casts(s.untimelyStasis)
        playOut()
        expect {
            s.untimelyStasis.counters(CounterKind.Time) shouldBe 2
            assertTrue(card(p.ogre).view.obj?.phasedOut == true, "phased out")
        }
    }

    /** Forecast during the upkeep taps a creature from the hand; the spell exiles a tapped creature. */
    // CR 702.57b
    @Test
    fun lancingSunbeamsForecast() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(s.lancingSunbeams)
        bob.battlefield(p.ogre)
        aliceUpkeep()
        playOut()
        alice.activates(s.lancingSunbeams, target = p.ogre)
        playOut()
        expect {
            p.ogre.tapped shouldBe true
            alice.hand has s.lancingSunbeams
        }
    }

    /** Converge: two colours spent exile a permanent with mana value 2. */
    @Test
    fun chromaticClosureConverge() = scenario {
        alice.battlefield("Plains", "Island")
        alice.hand(s.chromaticClosure)
        bob.battlefield(p.knight)
        alice.casts(s.chromaticClosure, target = p.knight, x = 1, paying = listOf("Plains", "Island"))
        playOut()
        expect { bob.exile has p.knight }
    }

    /** Suspend exiles it with time counters; resolved, it returns all enchantment cards. */
    // CR 702.62a
    @Test
    fun rekindledConvictionSuspendedAndCast() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(s.rekindledConviction)
        alice.graveyard(p.charm)
        suspends(s.rekindledConviction)
        expect { s.rekindledConviction.counters(CounterKind.Time) shouldBe 2 }
    }

    @Test
    fun rekindledConvictionReturnsEnchantments() = scenario {
        alice.hand(s.rekindledConviction)
        alice.graveyard(p.charm)
        alice.castsWithoutCosts(s.rekindledConviction)
        resolve()
        expect { alice.battlefield has p.charm }
    }

    /** It exiles black and red cards from graveyards, and black or red cards going to a graveyard are exiled. */
    // CR 614.1a
    @Test
    fun purifierEnDalExiles() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(s.purifierEnDal)
        bob.graveyard(p.ogre, p.bear)
        bob.battlefield("Swamp", p.knight)
        bob.hand(p.doom)
        alice.casts(s.purifierEnDal)
        playOut()
        expect {
            bob.exile has p.ogre
            bob.graveyard has p.bear
        }
        alice.passes()
        bob.casts(p.doom, target = p.knight)
        playOut()
        expect {
            bob.exile has p.knight
            bob.exile has p.doom
        }
    }

    /** The exiled card's toughness is the number of Birds. */
    // CR 608.2h
    @Test
    fun combTheDunesMakesBirds() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.graveyard(p.ogre)
        alice.hand(s.combTheDunes)
        alice.casts(s.combTheDunes, target = p.ogre)
        playOut()
        expect { alice.battlefield.names.count { it == "Bird Token" } shouldBe 3 }
    }

    @Test
    fun sweepTheGroundsInvestigates() = scenario {
        alice.battlefield(s.sweepTheGrounds)
        bob.battlefield(p.ogre)
        turn(active = bob, step = TurnStep.DeclareAttackers, priority = null)
        bob.attacks(p.ogre)
        playOut()
        expect { alice.battlefield has "Clue Token" }
    }

    /** Protection from the chosen card type: an instant can't target it. */
    // CR 614.12, 702.16
    @Test
    fun serrasHeraldProtection() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(s.serrasHerald)
        bob.battlefield("Mountain")
        bob.hand(p.zap)
        alice.casts(s.serrasHerald)
        playOut(options = listOf("Instant"))
        if (pending is Decision.ChooseCardType) answer(Answer.CardTypeChoice(arcanum.api.CardType.Instant))
        playOut()
        alice.passes()
        bob.casts(p.zap)
        val d = pending as? Decision.Targets
        assertTrue(d != null && d.options.none { it == target(s.serrasHerald) } && d.options.none { it == target("alice") }, "protected")
    }

    /** From the graveyard it returns to its owner's hand. */
    // CR 113.6b
    @Test
    fun skyknightsGiftFromTheGraveyard() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.graveyard(s.skyknightsGift)
        alice.activates(s.skyknightsGift)
        playOut()
        expect { alice.hand has s.skyknightsGift }
    }

    /** Evoked, it is sacrificed as it enters, but its enters ability exiles a creature. */
    // CR 702.74a
    @Test
    fun quietudeEvoked() = scenario {
        alice.hand(s.quietude, p.hawk)
        bob.battlefield(p.ogre)
        alice.casts(s.quietude, way = alternativeOf(s.quietude, Keyword.Evoke.Kind), choosing = listOf(p.hawk))
        playOut(p.ogre)
        expect {
            alice.graveyard has s.quietude
            bob.exile has p.ogre
            bob.life shouldBe 23
        }
    }

    @Test
    fun spiritOfPassageEvoked() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(s.spiritOfPassage)
        alice.casts(s.spiritOfPassage, way = alternativeOf(s.spiritOfPassage, Keyword.Evoke.Kind))
        playOut()
        expect {
            alice.graveyard has s.spiritOfPassage
            alice.battlefield.names.count { it == "Bird Token" } shouldBe 2
        }
    }

    /** Other nontoken creatures get +1/+1 and vigilance, tokens don't. */
    @Test
    fun thrabenOverseerLord() = scenario {
        alice.battlefield(s.thrabenOverseer, p.bear)
        expect {
            p.bear.power shouldBe 3
            p.bear hasKeyword Keyword.Vigilance
        }
    }

    /** Eternalize makes a 4/4 black Zombie copy from the graveyard. */
    // CR 702.129a
    @Test
    fun agelessWyrmEternalized() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.graveyard(s.agelessWyrm)
        alice.activates(s.agelessWyrm, ability = 1)
        playOut()
        expect { alice.exile has s.agelessWyrm }
    }

    /** Entwined, both modes: counters on two creatures and proliferate. */
    // CR 702.42a
    @Test
    fun boundlessPromiseEntwined() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", p.bear, p.elf)
        alice.hand(s.boundlessPromise)
        alice.casts(s.boundlessPromise, modes = listOf(0, 1), targets = listOf(p.bear, p.elf), kicked = 1)
        playOut(p.bear)
        expect { p.elf.counters(p1p1) shouldBe 1 }
    }
}
