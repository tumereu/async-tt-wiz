package arcanum.engine.sets.fin

import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.FinExtras.bear
import arcanum.engine.sets.fin.FinExtras.notes
import arcanum.engine.sets.fin.FinExtras.ogre
import arcanum.engine.sets.fin.FinExtras.p1p1
import arcanum.engine.sets.fin.FinExtras.trinket
import arcanum.engine.sets.fin.FinExtras.zap
import kotlin.test.Test

/**
 * Final Fantasy chunk 01 in play: job select Equipment (CR 702.182a) make a Hero token and attach to it; the equipped creature gets the
 * Equipment's bonus, creature type (layer 4) and abilities (layer 6). Berserker's Greataxe, Ronin's Nodachi, Troubadour's Longbow and
 * Pugilist's Cesti only grant a bonus, a type and keywords, as Wyrmrider's Lance does (tested); Pickpocket's Dirk's combat-damage draw and
 * Mender's Crook's attack life gain repeat tested granted triggers (Shinobi Twin Edges, Mystik Petalblades).
 */
class FinJobSelectScenariosTest {
    private val hero = "Hero Token"

    // CR 702.182a, 205.1b
    @Test
    fun wyrmridersLanceMakesAKnightHeroThatFliesOnYourTurn() = scenario {
        libraries()
        alice.battlefield("Plains", "Plains")
        alice.hand(FinSet.wyrmridersLance)
        alice.casts(FinSet.wyrmridersLance)
        resolveAll()
        expect {
            hero.power shouldBe 2
            hero.toughness shouldBe 1
            (Subtype("Knight") in hero.subtypes) shouldBe true
            hero hasKeyword Keyword.Flying
        }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { hero lacksKeyword Keyword.Flying }
    }

    @Test
    fun shadowcastersWandPingsOnNoncreatureSpells() = scenario {
        libraries()
        alice.battlefield("Swamp", "Swamp", "Mountain")
        alice.hand(FinSet.shadowcastersWand, zap)
        alice.casts(FinSet.shadowcastersWand)
        resolveAll()
        alice.casts(zap, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 17
            (Subtype("Wizard") in hero.subtypes) shouldBe true
        }
    }

    @Test
    fun oathkeepersPlateGivesWard() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(FinSet.oathkeepersPlate)
        alice.casts(FinSet.oathkeepersPlate)
        resolveAll()
        expect {
            hero.power shouldBe 3
            hero.toughness shouldBe 2
            hero hasKeyword Keyword.Ward.Kind
        }
    }

    // CR 702.6a: equip moves it to another creature
    @Test
    fun berserkersGreataxeEquipsAnotherCreature() = scenario {
        alice.battlefield(FinSet.berserkersGreataxe, bear, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.activates(FinSet.berserkersGreataxe, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 5
            bear.toughness shouldBe 4
            (Subtype("Warrior") in bear.subtypes) shouldBe true
        }
    }

    @Test
    fun stargazersOrreryCountsTheThirdDraw() = scenario {
        libraries()
        alice.battlefield(FinSet.stargazersOrrery, bear, "Island", "Island", "Island", "Island")
        alice.hand(notes, notes)
        alice.activates(FinSet.stargazersOrrery, target = bear)
        resolveAll()
        alice.casts(notes)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
        alice.casts(notes)
        resolveAll()
        // the second spell, then the third card drawn this turn (CR 504.1: no draw step in this test's turn)
        expect { bear.counters(p1p1) shouldBe 2 }
    }

    @Test
    fun shinobiTwinEdgesLootsAndDrains() = scenario {
        alice.library(notes, notes)
        bob.library(notes)
        alice.battlefield(bear)
        alice.battlefield(FinSet.shinobiTwinEdges)
        attach(FinSet.shinobiTwinEdges, bear)
        alice.hand(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        choose(ogre)
        expect {
            alice.graveyard has ogre
            // 3 combat damage, then 3 for the discarded card's mana value
            bob.life shouldBe 14
        }
    }

    @Test
    fun mystikPetalbladesUntapsAnAttacker() = scenario {
        alice.battlefield(bear, ogre)
        alice.battlefield(FinSet.mystikPetalblades)
        attach(FinSet.mystikPetalblades, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear, ogre)
        resolveAll()
        targets(ogre)
        expect { ogre.tapped shouldBe false }
    }

    @Test
    fun crimsonDuelistsFoilPumpsOnSpells() = scenario {
        alice.battlefield(bear, "Mountain")
        alice.battlefield(FinSet.crimsonDuelistsFoil)
        attach(FinSet.crimsonDuelistsFoil, bear)
        alice.hand(zap)
        alice.casts(zap, target = "bob")
        resolveAll()
        expect { bear.power shouldBe 4 }
    }

    // CR 118.3, 602.5b: an equip cost of life, once each turn
    @Test
    fun abyssKnightsClaymoreEquipsForLife() = scenario {
        alice.battlefield(FinSet.abyssKnightsClaymore, bear)
        alice.activates(FinSet.abyssKnightsClaymore, target = bear)
        resolveAll()
        expect {
            alice.life shouldBe 17
            bear.power shouldBe 5
            (Subtype("Knight") in bear.subtypes) shouldBe true
        }
    }

    @Test
    fun conjurersTomePutsACreatureOntoTheBattlefield() = scenario {
        alice.battlefield(bear)
        alice.battlefield(FinSet.conjurersTome)
        attach(FinSet.conjurersTome, bear)
        alice.hand(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        choose(ogre)
        expect {
            ogre.zone shouldBe Zone.Battlefield
            ogre.tapped shouldBe false
        }
    }

    @Test
    fun tinkerersArsenalCountsArtifacts() = scenario {
        alice.battlefield(trinket, "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(FinSet.tinkerersArsenal)
        alice.casts(FinSet.tinkerersArsenal)
        resolveAll()
        // two artifacts: the Arsenal and the Trinket
        expect { hero.power shouldBe 5 }
    }
}
