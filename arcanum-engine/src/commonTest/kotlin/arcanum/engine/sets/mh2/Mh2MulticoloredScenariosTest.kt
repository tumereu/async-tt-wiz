package arcanum.engine.sets.mh2

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Modern Horizons 2 cards of parser chunks 11–12 in play: the multicolored cards. Trivial ones not played here: Mortal Agony (destroy,
 * madness as Pit Mongrel), Domain Kavu's attack modes.
 */
class Mh2MulticoloredScenariosTest {
    private val s = Mh2Set
    private val p = Mh2Plain
    private val p1p1 = Mh2Plain.p1p1
    private val fourTypes = arrayOf("Island", Mh2Plain.bear, Mh2Plain.zap, Mh2Plain.notes)

    // ---- Chunk 11

    /** Modular 2 and a counter on each other artifact creature. */
    // CR 702.43a
    @Test
    fun arcwiredHunterEnters() = scenario {
        alice.battlefield("Mountain", "Plains", "Plains", p.golem)
        alice.hand(s.arcwiredHunter)
        alice.casts(s.arcwiredHunter)
        playOut()
        expect {
            s.arcwiredHunter.counters(p1p1) shouldBe 2
            p.golem.counters(p1p1) shouldBe 1
        }
    }

    /** A creature without a +1/+1 counter has outlast. */
    // CR 702.107a
    @Test
    fun arcusDevoteeGivesOutlast() = scenario {
        alice.battlefield("Forest", s.arcusDevotee, p.bear)
        alice.activates(p.bear)
        playOut()
        expect { p.bear.counters(p1p1) shouldBe 1 }
    }

    /** Having discarded a card this turn, it can be cast for {B/R}. */
    @Test
    fun pollywhistlebrambletonAfterADiscard() = scenario {
        alice.battlefield("Island", "Swamp", "Swamp", "Mountain")
        alice.hand(s.dihadasGambit, s.pollywhistlebrambleton)
        alice.library(p.ogre, p.elf, p.bear)
        alice.casts(s.dihadasGambit)
        playOut(p.ogre)
        alice.casts(s.pollywhistlebrambleton, way = alternativeOf(s.pollywhistlebrambleton, null))
        playOut()
        expect {
            alice.battlefield has s.pollywhistlebrambleton
            alice.graveyard has p.ogre
            alice.life shouldBe 21
        }
    }

    /** A creature returned from a graveyard with an extra counter; the Paladin gets a counter for it. */
    @Test
    fun breathlessPaladinAndGentleRestoration() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Swamp", "Swamp", s.breathlessPaladin)
        alice.hand(s.gentleRestoration)
        alice.graveyard(p.giant)
        alice.casts(s.gentleRestoration, modes = listOf(0), target = p.giant)
        playOut()
        expect {
            p.giant.counters(p1p1) shouldBe 1
            s.breathlessPaladin.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun gentleRestorationTwoSmall() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Swamp", "Swamp")
        alice.hand(s.gentleRestoration)
        alice.graveyard(p.bear, p.elf, p.giant)
        alice.casts(s.gentleRestoration, modes = listOf(1), targets = listOf(p.bear, p.elf))
        playOut()
        expect {
            alice.battlefield has p.bear
            alice.battlefield has p.elf
            alice.graveyard has p.giant
        }
    }

    /** Support 2: a counter on each of up to two target creatures. */
    @Test
    fun snaredByLagacsSupports() = scenario {
        alice.battlefield("Forest", "Plains", "Plains", p.bear, p.elf)
        alice.hand(s.snaredByLagacs)
        bob.battlefield(p.ogre)
        alice.casts(s.snaredByLagacs, target = p.ogre)
        playOut(p.bear, p.elf)
        expect {
            p.bear.counters(p1p1) shouldBe 1
            p.elf.counters(p1p1) shouldBe 1
        }
    }

    /** Loyalty abilities cost an additional [+1]: the +1 ability puts two loyalty counters. */
    // CR 606.4
    @Test
    fun marthAddsToLoyaltyCosts() = scenario {
        alice.battlefield(s.marthTheLynx, p.walker, counters = listOf(CounterKind.Loyalty to 3))
        alice.activates(p.walker)
        playOut()
        expect {
            p.walker.counters(CounterKind.Loyalty) shouldBe 5
            alice.life shouldBe 21
        }
    }

    @Test
    fun marthFindsAPlaneswalker() = scenario {
        alice.battlefield("Forest", "Forest", "Swamp", "Swamp")
        alice.hand(s.marthTheLynx)
        alice.library(p.ogre, p.walker, p.elf)
        alice.casts(s.marthTheLynx)
        playOut(p.walker)
        expect { alice.hand has p.walker }
    }

    /** One of the top two to the hand, the other to the graveyard; an artifact card gains 3 life. */
    @Test
    fun chromeHeraldRevealsTwo() = scenario {
        alice.battlefield("Plains", "Island", "Island")
        alice.hand(s.chromeHerald)
        alice.library(p.ogre, p.golem)
        alice.casts(s.chromeHerald)
        playOut(p.golem)
        expect {
            alice.hand has p.golem
            alice.graveyard has p.ogre
            alice.life shouldBe 23
        }
    }

    /** Creature tokens fly; sacrificing a token makes a Beast. */
    @Test
    fun mergeChrysalisMakesABeast() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Forest", s.mergeChrysalis)
        alice.hand(s.firmTestimony)
        alice.casts(s.firmTestimony)
        playOut()
        expect { "Crab Token" hasKeyword Keyword.Flying }
        alice.activates(s.mergeChrysalis, choosing = listOf("Clue Token"))
        playOut()
        expect { alice.battlefield has "Beast Token" }
    }

    /** It enters with a loyalty counter for each land. */
    // CR 306.5b, 614.1c
    @Test
    fun varrokEntersWithLands() = scenario {
        alice.battlefield("Plains", "Island", "Swamp", "Swamp")
        alice.hand(s.varrokShadowSlayer)
        alice.casts(s.varrokShadowSlayer)
        playOut()
        expect { s.varrokShadowSlayer.counters(CounterKind.Loyalty) shouldBe 4 }
    }

    /** Two cards drawn, one discarded, life for each card discarded this turn. */
    @Test
    fun dihadasGambitDrawsAndGains() = scenario {
        alice.battlefield("Island", "Swamp", "Swamp")
        alice.hand(s.dihadasGambit)
        alice.library(p.ogre, p.elf)
        alice.casts(s.dihadasGambit)
        playOut(p.ogre)
        expect {
            alice.hand has p.elf
            alice.life shouldBe 21
        }
    }

    @Test
    fun dreyWardenSquirrels() = scenario {
        alice.battlefield("Forest", "Forest", "Swamp", "Swamp", "Swamp")
        alice.hand(s.dreyWarden)
        alice.casts(s.dreyWarden)
        playOut()
        expect { alice.battlefield.names.count { it == "Squirrel Token" } shouldBe 2 }
    }

    /** Affinity for artifacts lowers the cost; cascade casts a cheaper card. */
    // CR 702.41a, 702.85a
    @Test
    fun etherforgedSphinxCascades() = scenario {
        alice.battlefield("Plains", "Island", "Island", "Island", "Island", p.trinket, p.trinket, p.trinket, p.trinket)
        alice.hand(s.etherforgedSphinx)
        alice.library(p.elf, p.ogre)
        alice.casts(s.etherforgedSphinx)
        playOut()
        expect {
            alice.battlefield has p.elf
            alice.battlefield has s.etherforgedSphinx
        }
    }

    /** Sacrificing an artifact also gains 4 life. */
    @Test
    fun smelterHelixSacrificesAnArtifact() = scenario {
        alice.battlefield("Mountain", "Plains", "Plains", p.trinket)
        alice.hand(s.smelterHelix)
        alice.casts(s.smelterHelix, target = "bob", choosing = listOf(p.trinket))
        playOut()
        expect {
            bob.life shouldBe 16
            alice.life shouldBe 24
        }
    }

    @Test
    fun generalFerrusOnAMulticoloredSpell() = scenario {
        alice.battlefield("Forest", "Plains", s.generalFerrusRokard)
        alice.hand(s.arcusDevotee)
        alice.casts(s.arcusDevotee)
        playOut()
        expect { alice.battlefield has "Golem Token" }
    }

    /** [+1]: drain 2 and a corruption counter. */
    @Test
    fun vesadroneDrains() = scenario {
        alice.battlefield(s.vesadroneDihada, counters = listOf(CounterKind.Loyalty to 4))
        bob.battlefield(p.bear)
        alice.activates(s.vesadroneDihada, ability = 0, target = p.bear)
        playOut(p.bear)
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
            p.bear.counters(CounterKind.Named("corruption")) shouldBe 1
        }
    }

    /** [−3]: control of a creature until end of turn, untapped, with haste. */
    @Test
    fun vesadroneSteals() = scenario {
        alice.battlefield(s.vesadroneDihada, counters = listOf(CounterKind.Loyalty to 4))
        bob.battlefield(p.ogre, tapped = true)
        alice.activates(s.vesadroneDihada, ability = 1, target = p.ogre)
        playOut()
        expect {
            card(p.ogre).controller shouldBe alice.id
            p.ogre hasKeyword Keyword.Haste
        }
    }

    /** A copy of the named card is made and cast, paying its costs; the name can't be chosen again. */
    // CR 707.12
    @Test
    fun gorvanHalfEyeCopiesANamedCard() = scenario {
        alice.battlefield("Plains", "Plains", "Forest", s.gorvanHalfEye, s.quirionWarden)
        bob.battlefield(p.trinket)
        alice.activates(s.gorvanHalfEye)
        playOut(p.trinket, options = listOf(p.unmaking))
        expect {
            bob.graveyard has p.trinket
            s.gorvanHalfEye.tapped shouldBe true
        }
        alice.activates(s.quirionWarden, target = s.gorvanHalfEye, choosing = listOf("Forest"))
        playOut()
        alice.activates(s.gorvanHalfEye)
        playOut(may = false)
        val d = decision<Decision.ChooseOption>()
        kotlin.test.assertTrue(p.unmaking !in d.options && d.options.size == 5, "${d.options}")
    }

    // ---- Chunk 12

    /** A red spell costs {1} less. */
    @Test
    fun goblinChaosmongerReduces() = scenario {
        alice.battlefield("Mountain", "Mountain", s.goblinChaosmonger)
        alice.hand(p.ogre)
        alice.casts(p.ogre)
        playOut()
        expect { alice.battlefield has p.ogre }
    }

    /** [+1]: an Insect token, then a mill; an Insect card milled repeats it with a loyalty counter. */
    @Test
    fun grastRepeatsOnInsects() = scenario {
        alice.battlefield(s.grastTheRavenousTide, counters = listOf(CounterKind.Loyalty to 3))
        alice.library(s.duskshellCreeper, p.ogre, p.elf)
        alice.activates(s.grastTheRavenousTide, ability = 0)
        playOut()
        expect {
            alice.battlefield.names.count { it == "Insect Token" } shouldBe 2
            s.grastTheRavenousTide.counters(CounterKind.Loyalty) shouldBe 5
        }
    }

    /** [−2]: sacrificing a creature, a reflexive trigger destroys a creature. */
    // CR 603.12
    @Test
    fun grastSacrificesToDestroy() = scenario {
        alice.battlefield(s.grastTheRavenousTide, p.elf, counters = listOf(CounterKind.Loyalty to 3))
        bob.battlefield(p.giant)
        alice.activates(s.grastTheRavenousTide, ability = 1)
        playOut(p.elf, p.giant)
        expect {
            bob.graveyard has p.giant
            alice.graveyard has p.elf
        }
    }

    /** A discard pays {1} to amass Zombies 2. */
    // CR 701.47a
    @Test
    fun lazotepVizierAmasses() = scenario {
        alice.battlefield("Island", "Swamp", "Swamp", "Swamp", s.lazotepVizier)
        alice.hand(s.dihadasGambit)
        alice.library(p.ogre, p.elf)
        alice.casts(s.dihadasGambit)
        playOut(p.ogre)
        expect { "Zombie Army Token".counters(p1p1) shouldBe 2 }
    }

    /** Another nontoken creature investigates; sacrificing X Clues takes a permanent card from an opponent's top cards. */
    @Test
    fun lonasInvestigatesAndTakes() = scenario {
        alice.battlefield("Forest", "Forest", s.lonasCryptobotanist)
        alice.hand(p.bear)
        bob.library(p.elf, p.ogre)
        alice.casts(p.bear)
        playOut()
        expect { alice.battlefield has "Clue Token" }
        alice.activates(s.lonasCryptobotanist, x = 1, target = "bob", choosing = listOf("Clue Token"))
        playOut(p.elf)
        expect {
            card(p.elf).controller shouldBe alice.id
            bob.library has p.ogre
        }
    }

    /** At upkeep from the graveyard, paying 1 life returns it to the hand. */
    @Test
    fun lordOfDemiseReturns() = scenario {
        alice.graveyard(s.lordOfDemise)
        aliceUpkeep()
        playOut()
        expect {
            alice.hand has s.lordOfDemise
            alice.life shouldBe 19
        }
    }

    /** A spell draws a card; a second spell can't be cast. */
    @Test
    fun temperanceOneSpell() = scenario {
        alice.battlefield("Mountain", "Mountain", s.temperance)
        alice.hand(p.zap, p.zap)
        alice.library(p.ogre)
        alice.casts(p.zap, target = "bob")
        playOut()
        expect { alice.hand has p.ogre }
        assertFailsWith<IllegalArgumentException> { alice.casts(p.zap, target = "bob") }
    }

    /** Not paid for at upkeep, it is sacrificed and its death deals 7 to each nonlegendary creature. */
    @Test
    fun pirsuUnpaid() = scenario {
        alice.battlefield(s.pirsuTheUnstable)
        bob.battlefield(p.giant)
        aliceUpkeep()
        playOut(may = false)
        expect {
            alice.graveyard has s.pirsuTheUnstable
            bob.graveyard has p.giant
        }
    }

    @Test
    fun priestessOfFellRitesReturns() = scenario {
        alice.battlefield(s.priestessOfFellRites)
        alice.graveyard(p.giant)
        alice.activates(s.priestessOfFellRites, target = p.giant)
        playOut()
        expect {
            alice.battlefield has p.giant
            alice.life shouldBe 17
        }
    }

    /** With delirium both modes. */
    @Test
    fun prescientTitanBothModes() = scenario {
        alice.battlefield("Island", "Island", "Island", "Mountain", "Mountain", "Mountain")
        alice.graveyard(*fourTypes)
        alice.hand(s.prescientTitan)
        alice.library(p.ogre, p.elf, p.giant, p.hawk)
        alice.casts(s.prescientTitan)
        playOut("bob", p.giant)
        expect {
            bob.life shouldBe 16
            alice.hand has p.giant
        }
    }

    /** Its echo is paid by discarding a card at the next upkeep, and it stays. */
    // CR 702.30a
    @Test
    fun rakdosShowstopperEchoDiscards() = scenario {
        alice.battlefield("Swamp", "Mountain")
        alice.hand(s.rakdosShowstopper, p.elf)
        alice.library(p.ogre, p.ogre, p.ogre)
        bob.library(p.ogre, p.ogre, p.ogre)
        alice.casts(s.rakdosShowstopper)
        playOut()
        advanceTo(TurnStep.Upkeep, turn = 3)
        playOut(p.elf)
        expect {
            alice.battlefield has s.rakdosShowstopper
            alice.graveyard has p.elf
        }
    }

    /** Sacrificing a creature: a counter, life and a card. */
    @Test
    fun voraciousSquirrelEats() = scenario {
        alice.battlefield("Forest", "Swamp", "Swamp", s.voraciousSquirrel, p.bear)
        alice.library(p.ogre)
        alice.activates(s.voraciousSquirrel, choosing = listOf(p.bear))
        playOut()
        expect {
            s.voraciousSquirrel.counters(p1p1) shouldBe 1
            alice.life shouldBe 21
            alice.hand has p.ogre
        }
    }

    @Test
    fun stormGodsSeerDies() = scenario {
        alice.battlefield("Swamp", s.stormGodsSeer)
        alice.hand(p.doom)
        alice.casts(p.doom, target = s.stormGodsSeer)
        playOut("bob")
        expect { bob.life shouldBe 17 }
    }

    @Test
    fun sythriaOnAnEnchantment() = scenario {
        alice.battlefield("Plains", "Plains", s.sythriaHarvestsHand)
        alice.hand(p.charm)
        alice.library(p.ogre)
        alice.casts(p.charm)
        playOut()
        expect {
            alice.life shouldBe 21
            alice.hand has p.ogre
        }
    }

    /** Domain: as big as the basic land types. */
    @Test
    fun domainKavuSize() = scenario {
        alice.battlefield("Forest", "Mountain", "Island", s.domainKavu)
        expect { s.domainKavu.power shouldBe 3 }
    }

    /** Evoked, two Clues and then sacrificed. */
    @Test
    fun sievesifterEvoked() = scenario {
        alice.battlefield("Forest", "Island")
        alice.hand(s.sievesifter)
        alice.casts(s.sievesifter, way = alternativeOf(s.sievesifter, Keyword.Evoke.Kind))
        playOut()
        expect {
            alice.battlefield.names.count { it == "Clue Token" } shouldBe 2
            alice.graveyard has s.sievesifter
        }
    }

    /** Two flips, one won and one lost: a card and 2 damage. */
    // CR 705
    @Test
    fun yuzraFlips() = scenario {
        alice.battlefield(s.yuzraFortunesBlaze)
        alice.library(p.ogre, p.elf)
        random(Answer.CoinFlip(true), Answer.CoinFlip(false))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.yuzraFortunesBlaze)
        playOut()
        decision<Decision.ChooseNumber>()
        answer(Answer.Number(2))
        playOut()
        expect {
            alice.hand has p.ogre
            alice.life shouldBe 18
        }
    }
}
