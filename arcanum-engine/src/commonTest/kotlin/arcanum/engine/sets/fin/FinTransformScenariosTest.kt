package arcanum.engine.sets.fin

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.FinExtras.bear
import arcanum.engine.sets.fin.FinExtras.charm
import arcanum.engine.sets.fin.FinExtras.doom
import arcanum.engine.sets.fin.FinExtras.finality
import arcanum.engine.sets.fin.FinExtras.giant
import arcanum.engine.sets.fin.FinExtras.legend
import arcanum.engine.sets.fin.FinExtras.lore
import arcanum.engine.sets.fin.FinExtras.notes
import arcanum.engine.sets.fin.FinExtras.ogre
import arcanum.engine.sets.fin.FinExtras.p1p1
import arcanum.engine.sets.fin.FinExtras.zap
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Final Fantasy chunk 04 in play: legends that exile themselves and return transformed into Saga creatures (CR 712.14, 714.3a: a Saga
 * entering gets its first lore counter), and the Sagas that exile themselves and return with their front face up (CR 400.7: a new object).
 */
class FinTransformScenariosTest {
    private fun Scenario.faceName(name: String): String? = view()[card(name, zone = Zone.Battlefield)]?.characteristics?.name

    private fun lands(land: String, n: Int) = Array(n) { land }

    @Test
    fun aelthaDrawsOnLegendarySpellsOnceEachTurn() = scenario {
        libraries()
        alice.battlefield(FinSet.aelthaHeartOfTheCrystal, "Plains", "Plains", "Plains", "Plains")
        alice.hand(legend, legend)
        alice.casts(legend)
        resolveAll()
        alice.casts(legend)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 701.27a: transform
    @Test
    fun aelthaExilesAndTransforms() = scenario {
        libraries()
        alice.battlefield(FinSet.aelthaHeartOfTheCrystal, bear, *lands("Plains", 7))
        bob.battlefield(charm)
        alice.activates(FinSet.aelthaHeartOfTheCrystal, target = charm)
        resolveAll()
        assertEquals("Aeltha, the Mothercrystal", faceName(FinSet.aelthaHeartOfTheCrystal))
        expect {
            charm.zone shouldBe Zone.Exile
            FinSet.aelthaHeartOfTheCrystal hasKeyword Keyword.Indestructible
        }
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        targets(bear)
        expect {
            bear.counters(p1p1) shouldBe 1
            bear hasKeyword Keyword.Indestructible
        }
    }

    @Test
    fun maraBouncesThenBecomesASaga() = scenario {
        libraries()
        alice.battlefield(*lands("Island", 8))
        alice.hand(FinSet.maraFrostboundNoble)
        bob.battlefield(charm)
        alice.casts(FinSet.maraFrostboundNoble)
        resolveAll()
        targets(charm)
        expect { bob.hand has charm }
    }

    @Test
    fun maraReturnsAsRimeheart() = scenario {
        libraries()
        alice.battlefield(FinSet.maraFrostboundNoble, bear, *lands("Island", 5))
        alice.activates(FinSet.maraFrostboundNoble)
        resolveAll()
        targets(bear)
        assertEquals("Rimeheart, Warden of Frost", faceName(FinSet.maraFrostboundNoble))
        expect { FinSet.maraFrostboundNoble.counters(lore) shouldBe 1 }
    }

    // CR 714.4: the last chapter exiles it and returns it (front face up, a new object)
    @Test
    fun rimeheartColdSnapTapsLandsAndReturnsMara() = scenario {
        libraries()
        bob.battlefield("Forest", "Forest")
        alice.battlefield(FinSet.maraFrostboundNoble, counters = listOf(lore to 2))
        backFaceUp(FinSet.maraFrostboundNoble)
        turn(step = TurnStep.Upkeep)
        advanceTo(TurnStep.PrecombatMain)
        resolveAll()
        targets()
        expect { card("Forest", 0).tapped shouldBe true }
        assertEquals("Mara, Frostbound Noble", faceName(FinSet.maraFrostboundNoble))
    }

    @Test
    fun shardOfTheArbiterBecomesAPreventingSaga() = scenario {
        libraries()
        alice.battlefield(FinSet.shardOfTheArbiter, bear, "Mountain", *lands("Plains", 7))
        alice.hand(zap)
        alice.activates(FinSet.shardOfTheArbiter, ability = 0)
        resolveAll()
        assertEquals("Conjure: The Arbiter", faceName(FinSet.shardOfTheArbiter))
        alice.casts(zap, target = bear)
        resolveAll()
        expect { bear.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun corvanDiscardsAndDrawsByDevotion() = scenario {
        alice.library(notes, notes, notes, notes)
        alice.battlefield(*lands("Mountain", 6))
        alice.hand(FinSet.corvanEmberboundNoble, ogre)
        alice.casts(FinSet.corvanEmberboundNoble)
        resolveAll()
        yes()
        expect {
            alice.graveyard has ogre
            // devotion to red: {R}{R} of Corvan
            alice.hand.size shouldBe 2
        }
    }

    @Test
    fun pyreheartFightsThenAddsMana() = scenario {
        libraries()
        alice.battlefield(FinSet.corvanEmberboundNoble, *lands("Mountain", 6))
        bob.battlefield(giant)
        alice.activates(FinSet.corvanEmberboundNoble)
        resolveAll()
        targets(giant)
        expect { giant.zone shouldBe Zone.Graveyard }
    }

    // CR 702.34a flashback; CR 712.14 put onto the battlefield transformed
    @Test
    fun espersAwakeningFromTheGraveyardBecomesASaga() = scenario {
        alice.library(ogre, notes, notes, notes)
        alice.battlefield(*lands("Forest", 4))
        alice.graveyard(FinSet.espersAwakening)
        alice.casts(FinSet.espersAwakening)
        resolveAll()
        choose()
        choose(ogre, notes)
        expect {
            alice.life shouldBe 22
            FinSet.espersAwakening.zone shouldBe Zone.Battlefield
            FinSet.espersAwakening.counters(finality) shouldBe 1
            alice.hand has ogre
        }
    }

    @Test
    fun brannoTransformsAfterCombatDamage() = scenario {
        libraries()
        bob.hand(notes)
        alice.battlefield(FinSet.brannoReluctantGuardian)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.brannoReluctantGuardian)
        resolveAll()
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        yes()
        assertEquals("Branno's Final Aeon", faceName(FinSet.brannoReluctantGuardian))
        expect {
            bob.graveyard.size shouldBe 1
            alice.hand.size shouldBe 1
        }
    }

    @Test
    fun gerrandSurveilsAndReturnsAsRuin() = scenario {
        libraries()
        alice.battlefield(FinSet.gerrandFallenKnight, "Mountain")
        alice.hand(zap)
        alice.casts(zap, target = "bob")
        resolveAll()
        choose()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun ruinReturnsFromTheGraveyardAndGoesToTheLibraryBottom() = scenario {
        libraries()
        alice.graveyard(FinSet.gerrandFallenKnight)
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(doom)
        alice.activates(FinSet.gerrandFallenKnight, paying = listOf("Mountain", "Mountain", "Mountain", "Mountain", "Swamp", "Swamp", "Swamp"))
        resolveAll()
        assertEquals("Ruin, the Endless", faceName(FinSet.gerrandFallenKnight))
        alice.casts(doom, target = FinSet.gerrandFallenKnight)
        resolveAll()
        expect { FinSet.gerrandFallenKnight.zone shouldBe Zone.Library }
    }

    @Test
    fun cyrenRedrawsUpToTwo() = scenario {
        libraries()
        alice.battlefield("Mountain", "Mountain", "Plains")
        alice.hand(FinSet.cyrenAshbornNoble, ogre, bear)
        alice.casts(FinSet.cyrenAshbornNoble)
        resolveAll()
        choose(ogre, bear)
        expect {
            alice.graveyard has ogre
            alice.hand.size shouldBe 2
        }
    }

    @Test
    fun ashwingBurnsOpponents() = scenario {
        libraries()
        alice.battlefield(FinSet.cyrenAshbornNoble, "Mountain", "Mountain", "Mountain", "Plains", "Plains")
        alice.activates(FinSet.cyrenAshbornNoble)
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun aurelGivesKnightsFlyingOnYourTurn() = scenario {
        libraries()
        alice.battlefield(*lands("Plains", 4))
        alice.hand(FinSet.aurelWyrmSwornKnight)
        alice.casts(FinSet.aurelWyrmSwornKnight)
        resolveAll()
        expect {
            FinSet.aurelWyrmSwornKnight hasKeyword Keyword.Flying
            card("Knight Token").view.characteristics!!.abilities.isNotEmpty() shouldBe true
        }
    }

    @Test
    fun radiantWyrmSpreadsCounters() = scenario {
        libraries()
        alice.battlefield(FinSet.aurelWyrmSwornKnight, bear, *lands("Plains", 6))
        alice.activates(FinSet.aurelWyrmSwornKnight)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            bear hasKeyword Keyword.Flying
        }
    }

    @Test
    fun taliaMillsAndKeepsAnEnchantment() = scenario {
        alice.library(notes, charm, notes, notes, notes, notes)
        alice.battlefield("Mountain", "Mountain", "Forest")
        alice.hand(FinSet.taliaArcaneProdigy)
        alice.casts(FinSet.taliaArcaneProdigy)
        resolveAll()
        choose(charm)
        expect {
            alice.hand has charm
            alice.graveyard.size shouldBe 4
        }
    }

    @Test
    fun arcaneTaliaCopiesAnEnchantment() = scenario {
        libraries()
        alice.battlefield(FinSet.taliaArcaneProdigy, charm, "Mountain", *lands("Forest", 5))
        alice.activates(FinSet.taliaArcaneProdigy)
        resolveAll()
        targets(charm)
        expect { alice.battlefield.names.count { it == charm } shouldBe 2 }
    }
}
