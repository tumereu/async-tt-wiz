package arcanum.engine.sets.mh2

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * Modern Horizons 2 cards of parser chunks 13–14 in play: the artifacts and lands. Trivial ones not played here: Ornithopter of Bliss
 * (flying, a mana ability), Sun Talisman (suspend as Rift Seeder, a mana ability), the fetchlands Parched Butte, Bog Barrens, Fogbound
 * Jungle, Boiling Mere and Mossy Crypts (pay life, sacrifice, a search) and the ten indestructible artifact Spans (enter tapped, two
 * colours).
 */
class Mh2ArtifactsAndLandsScenariosTest {
    private val s = Mh2Set
    private val p = Mh2Plain
    private val p1p1 = Mh2Plain.p1p1

    // ---- Chunk 13

    /** Investigating makes a Clue, a Food and a Treasure. */
    // CR 614.1a
    @Test
    fun academyFabricatorOneOfEach() = scenario {
        alice.battlefield("Island", s.academyFabricator)
        alice.hand(s.firmTestimony)
        alice.casts(s.firmTestimony)
        playOut()
        expect {
            alice.battlefield has "Clue Token"
            alice.battlefield has "Food Token"
            alice.battlefield has "Treasure Token"
        }
    }

    /** A creature attacking alone gets +X/+X for the card types in all graveyards. */
    @Test
    fun shrineOfTheGoyfPumps() = scenario {
        alice.battlefield(s.shrineOfTheGoyf, p.bear)
        alice.graveyard("Island", p.zap)
        bob.graveyard(p.ogre)
        unblocked(p.bear)
        expect { p.bear.power shouldBe 5 }
    }

    /** Living weapon: a Germ wears it, 1/1 with lifelink. */
    // CR 702.92a
    @Test
    fun battlemarrowGerm() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(s.battlemarrow)
        alice.casts(s.battlemarrow)
        playOut()
        expect {
            "Phyrexian Germ Token".power shouldBe 1
            "Phyrexian Germ Token" hasKeyword Keyword.Lifelink
        }
    }

    @Test
    fun jarredGolemsDies() = scenario {
        alice.battlefield("Swamp", s.jarredGolems)
        alice.hand(p.doom)
        alice.casts(p.doom, target = s.jarredGolems)
        playOut()
        expect { alice.life shouldBe 23 }
    }

    /** Three cards drawn, two put back on top. */
    @Test
    fun thoughtrockDrawsThree() = scenario {
        alice.battlefield("Plains", "Plains", s.thoughtrock)
        alice.library(p.ogre, p.elf, p.bear)
        alice.activates(s.thoughtrock)
        playOut(p.ogre, p.elf)
        expect {
            alice.hand has p.bear
            alice.library.size shouldBe 2
        }
    }

    /** It exiles a creature card as it enters, and tapping two creatures makes it a copy that is still a Vehicle artifact. */
    // CR 707.9b
    @Test
    fun dermocabCopiesTheExiled() = scenario {
        alice.battlefield("Plains", "Plains", p.bear, p.elf)
        alice.hand(s.dermocab)
        bob.graveyard(p.giant)
        alice.casts(s.dermocab)
        playOut(p.giant)
        expect { bob.exile has p.giant }
        alice.activates(s.dermocab, choosing = listOf(p.bear, p.elf))
        playOut()
        expect {
            card(s.dermocab).view.characteristics?.name shouldBe p.giant
            card(s.dermocab).view.characteristics?.power shouldBe 5
            assertTrue(card(s.dermocab).view.characteristics?.subtypes.orEmpty().contains(Subtype("Vehicle")))
        }
    }

    /** Discarding the hand and sacrificing it: three mana of one colour. */
    // CR 605.1a
    @Test
    fun adamantLionMana() = scenario {
        alice.battlefield(s.adamantLion)
        alice.hand(p.ogre, p.elf)
        alice.activates(s.adamantLion, color = arcanum.api.Color.Red)
        expect {
            alice.graveyard has p.ogre
            alice.graveyard has p.elf
            alice.graveyard has s.adamantLion
        }
        kotlin.test.assertEquals(List(3) { arcanum.api.Color.Red }, state.player(alice.id).manaPool.map { it.color })
    }

    @Test
    fun chaffTosserPings() = scenario {
        alice.battlefield(s.chaffTosser)
        alice.hand(p.elf)
        alice.activates(s.chaffTosser, target = "bob", choosing = listOf(p.elf))
        playOut()
        expect { bob.life shouldBe 18 }
    }

    /** Its Germ is 5/5 with haste and indestructible. */
    @Test
    fun valdraCompleatGerm() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(s.valdraCompleat)
        alice.casts(s.valdraCompleat)
        playOut()
        expect {
            "Phyrexian Germ Token".power shouldBe 5
            "Phyrexian Germ Token" hasKeyword Keyword.Haste
            "Phyrexian Germ Token" hasKeyword Keyword.Indestructible
        }
    }

    @Test
    fun liquimetalWrenchMakesAnArtifact() = scenario {
        alice.battlefield(s.liquimetalWrench)
        bob.battlefield(p.bear)
        alice.activates(s.liquimetalWrench, ability = 1, target = p.bear)
        playOut()
        expect { assertTrue(CardType.Artifact in p.bear.types) }
    }

    @Test
    fun singleskelionSpendsItsCounter() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(s.singleskelion)
        alice.casts(s.singleskelion)
        playOut()
        alice.activates(s.singleskelion, target = "bob")
        playOut()
        expect {
            bob.life shouldBe 19
            s.singleskelion.counters(p1p1) shouldBe 0
        }
    }

    @Test
    fun myrScrapperSacrifices() = scenario {
        alice.battlefield(s.myrScrapper, p.bear)
        alice.activates(s.myrScrapper, target = p.bear)
        playOut()
        expect { p.bear.counters(p1p1) shouldBe 1 }
    }

    /** Its Germ gets +1/+1 for each artifact and enchantment. */
    @Test
    fun briarcystGerm() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", p.charm)
        alice.hand(s.briarcyst)
        alice.casts(s.briarcyst)
        playOut()
        expect { "Phyrexian Germ Token".power shouldBe 2 }
    }

    /** Attacking with three tokens: +2/+0 and first strike. */
    // CR 603.4
    @Test
    fun havenRaptorWithTokens() = scenario {
        alice.battlefield("Island", "Island", s.havenRaptor)
        alice.hand(s.firmTestimony, s.firmTestimony)
        alice.casts(s.firmTestimony)
        playOut()
        alice.casts(s.firmTestimony, paying = listOf("Island#1"))
        playOut()
        unblocked(s.havenRaptor)
        expect {
            s.havenRaptor.power shouldBe 4
            s.havenRaptor hasKeyword Keyword.FirstStrike
        }
    }

    /** Domain lowers its cost by {2} a type; creatures get keywords by colour. */
    @Test
    fun heirOfDracoCheapAndGiving() = scenario {
        alice.battlefield("Plains", "Island", "Swamp", "Mountain", "Forest", "Forest", "Forest", p.hawk, p.bear)
        alice.hand(s.heirOfDraco)
        alice.casts(s.heirOfDraco, paying = listOf("Forest#1", "Forest#2"))
        playOut()
        expect {
            alice.battlefield has s.heirOfDraco
            p.hawk hasKeyword Keyword.Vigilance
            p.bear hasKeyword Keyword.Trample
        }
    }

    /** Artifact landcycling finds an artifact land. */
    // CR 702.29a
    @Test
    fun wayfarersCompanionLandcycles() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(s.wayfarersCompanion)
        alice.library(p.ogre, s.powerStorehouse)
        alice.activates(s.wayfarersCompanion)
        playOut(s.powerStorehouse)
        expect { alice.hand has s.powerStorehouse }
    }

    /** At combat, a +1/+1 counter moves onto another creature. */
    @Test
    fun ironDromedaryMovesACounter() = scenario {
        alice.battlefield(s.ironDromedary, p.bear, counters = listOf(p1p1 to 2))
        turn(step = TurnStep.BeginningOfCombat)
        beginStep(TurnStep.BeginningOfCombat)
        playOut(p.bear)
        expect {
            s.ironDromedary.counters(p1p1) shouldBe 1
            p.bear.counters(p1p1) shouldBe 3
        }
    }

    @Test
    fun ironDromedaryEntersTapped() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(s.ironDromedary)
        alice.casts(s.ironDromedary)
        playOut()
        expect {
            s.ironDromedary.tapped shouldBe true
            s.ironDromedary.counters(p1p1) shouldBe 2
        }
    }

    /** Combat damage to a player: a creature of Alice's flickers along with a basic land from the library. */
    @Test
    fun bladeOfHearthAndFieldFlickers() = scenario {
        alice.battlefield("Plains", "Plains", s.bladeOfHearthAndField, p.bear, p.elf)
        alice.library("Forest", p.ogre)
        alice.activates(s.bladeOfHearthAndField, target = p.bear)
        playOut()
        hits(p.bear, picks = listOf(p.elf, "Forest"))
        expect {
            bob.life shouldBe 16
            alice.battlefield has "Forest"
            alice.battlefield has p.elf
        }
    }

    @Test
    fun tormodsGravekeeperExiles() = scenario {
        alice.battlefield(s.tormodsGravekeeper)
        bob.graveyard(p.ogre, p.elf)
        alice.activates(s.tormodsGravekeeper, target = "bob")
        playOut()
        expect {
            bob.exile has p.ogre
            bob.exile has p.elf
        }
    }

    @Test
    fun netherworldCookbookFoodAndReturn() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", s.theNetherworldCookbook)
        alice.hand(p.elf)
        alice.graveyard(p.giant)
        alice.activates(s.theNetherworldCookbook, ability = 0, choosing = listOf(p.elf))
        playOut()
        expect { alice.battlefield has "Food Token" }
    }

    /** Artifact landwalk: no block while the defender controls an artifact land. */
    // CR 702.14c
    @Test
    fun vectisGauntletsLandwalk() = scenario {
        alice.battlefield("Plains", "Plains", s.vectisGauntlets, p.bear)
        bob.battlefield(s.powerStorehouse, p.ogre)
        alice.activates(s.vectisGauntlets, target = p.bear)
        playOut()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(p.bear)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        assertFails { bob.blocks(p.ogre to p.bear) }
        expect { p.bear.power shouldBe 4 }
    }

    /** A spell cast with no coloured mana is countered; one with coloured mana isn't. */
    @Test
    fun nullMirrorCounters() = scenario {
        alice.battlefield(p.wastes, p.wastes, p.wastes, "Mountain", s.nullMirror)
        alice.hand(p.golem, p.zap)
        alice.casts(p.golem)
        playOut()
        alice.casts(p.zap, target = "bob")
        playOut()
        expect {
            alice.graveyard has p.golem
            bob.life shouldBe 18
        }
    }

    /** Another creature's modular trigger puts one more counter. */
    // CR 702.43a, 614.1a
    @Test
    fun zebazAddsToModular() = scenario {
        alice.battlefield("Mountain", p.golem)
        alice.battlefield(s.zebazTheGlimmerwing, counters = listOf(p1p1 to 1))
        alice.battlefield(s.arcwiredTracker, counters = listOf(p1p1 to 2))
        alice.activates(s.zebazTheGlimmerwing, ability = 0, target = s.arcwiredTracker)
        playOut(p.golem)
        expect { p.golem.counters(p1p1) shouldBe 3 }
    }

    /** Its own modular trigger gets nothing more: it is in the graveyard by then. */
    // CR 603.10a
    @Test
    fun zebazOwnModularUnchanged() = scenario {
        alice.battlefield("Mountain", p.golem)
        alice.battlefield(s.zebazTheGlimmerwing, counters = listOf(p1p1 to 1))
        alice.activates(s.zebazTheGlimmerwing, ability = 0, target = s.zebazTheGlimmerwing)
        playOut(p.golem)
        expect { p.golem.counters(p1p1) shouldBe 1 }
    }

    // ---- Chunk 14

    /** An artifact land that enters tapped with a +1/+1 counter. */
    @Test
    fun powerStorehouseModular() = scenario {
        alice.hand(s.powerStorehouse)
        alice.plays(s.powerStorehouse)
        playOut()
        expect {
            s.powerStorehouse.tapped shouldBe true
            s.powerStorehouse.counters(p1p1) shouldBe 1
        }
    }

    /** Chapter III finds an artifact with mana cost {0} or {1}. */
    // CR 714.2c
    @Test
    fun urzasEpicChapterThree() = scenario {
        alice.battlefield(s.urzasEpic, counters = listOf(CounterKind.Lore to 2))
        alice.library(p.trinket, p.golem)
        turn(step = TurnStep.Draw)
        advanceTo(TurnStep.PrecombatMain)
        playOut(p.trinket)
        expect {
            alice.battlefield has p.trinket
            alice.graveyard has s.urzasEpic
        }
    }

    /** Each land is a Forest: an Island taps for {G}. */
    // CR 305.7
    @Test
    fun yavimayaMakesForests() = scenario {
        alice.battlefield(s.yavimayaCradleOfRoots, "Island")
        alice.hand(p.elf)
        alice.casts(p.elf, paying = listOf("Island:G"))
        playOut()
        expect {
            alice.battlefield has p.elf
            assertTrue(Subtype("Forest") in "Island".subtypes)
        }
    }
}
