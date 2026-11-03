package arcanum.engine.sets.fin

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.FinExtras.bear
import arcanum.engine.sets.fin.FinExtras.finality
import arcanum.engine.sets.fin.FinExtras.notes
import arcanum.engine.sets.fin.FinExtras.ogre
import arcanum.engine.sets.fin.FinExtras.zap
import kotlin.test.Test

/**
 * Final Fantasy chunk 15 in play: artifacts, Equipment and Towns. A blight counter turning a land into a colorless one and "whenever you tap
 * a land for {C}" (CR 106.12, 605.1b), base power and toughness from your life (CR 613.4b), an extra trigger on a creature dying
 * (CR 603.2d), hideaway (CR 702.75), a cast without paying from combat damage (CR 601.2a). First Village's two mana abilities and Goal Ball's
 * any-colour mana are covered mana abilities.
 */
class FinArtifactsAndLandsScenariosTest {
    private fun lands(land: String, n: Int) = Array(n) { land }

    @Test
    fun pupuSaucerPutsALandOntoTheBattlefield() = scenario {
        alice.battlefield(FinSet.pupuSaucer)
        alice.hand("Forest")
        alice.activates(FinSet.pupuSaucer)
        resolveAll()
        choose("Forest")
        expect { card("Forest").zone shouldBe Zone.Battlefield }
    }

    @Test
    fun aegisAndBladeMakesYourLifeTheBaseStats() = scenario {
        alice.life = 13
        alice.battlefield(FinSet.aegisAndBlade, bear)
        attach(FinSet.aegisAndBlade, bear)
        expect {
            bear.power shouldBe 13
            bear.toughness shouldBe 13
        }
    }

    @Test
    fun theTrueKatanaGivesFirstStrikeWhileAttacking() = scenario {
        libraries()
        alice.battlefield(FinSet.theTrueKatana, bear)
        attach(FinSet.theTrueKatana, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        expect { bear hasKeyword Keyword.FirstStrike }
    }

    // CR 603.2d: an additional time
    @Test
    fun theTrueKatanaDoublesDeathTriggers() = scenario {
        alice.battlefield(FinSet.theTrueKatana, FinSet.salvageCrew, bear, "Swamp")
        attach(FinSet.theTrueKatana, FinSet.salvageCrew)
        alice.hand(FinExtras.doom)
        alice.casts(FinExtras.doom, target = bear)
        resolveAll()
        targets("bob")
        targets("bob")
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun broadswordDrawsAndCastsFree() = scenario {
        alice.library(zap, notes)
        bob.library(notes)
        alice.battlefield(FinSet.broadswordOfTheSoldier, bear)
        attach(FinSet.broadswordOfTheSoldier, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        choose(zap)
        targets("bob")
        expect { bob.life shouldBe 13 }
    }

    @Test
    fun relentlessHunterUnitReturnsWithFinality() = scenario {
        alice.graveyard(FinSet.relentlessHunterUnit)
        alice.battlefield(*lands("Plains", 8))
        alice.activates(FinSet.relentlessHunterUnit)
        resolveAll()
        expect {
            FinSet.relentlessHunterUnit.zone shouldBe Zone.Battlefield
            FinSet.relentlessHunterUnit.counters(finality) shouldBe 1
        }
    }

    @Test
    fun cureAllShufflesNonlandsBackForLife() = scenario {
        alice.battlefield(FinSet.cureAll, *lands("Plains", 5))
        alice.graveyard(bear, ogre, "Forest")
        alice.activates(FinSet.cureAll)
        resolveAll()
        expect {
            alice.life shouldBe 22
            alice.graveyard.names.count { it == "Forest" } shouldBe 1
        }
    }

    // CR 702.75a: hideaway
    @Test
    fun corvansRetreatHidesACard() = scenario {
        alice.library(ogre, notes, notes, notes, notes)
        alice.hand(FinSet.corvansRetreat)
        alice.plays(FinSet.corvansRetreat)
        resolveAll()
        choose(ogre)
        expect { ogre.zone shouldBe Zone.Exile }
    }

    @Test
    fun goalBallNeedsALegendaryHit() = scenario {
        alice.library(notes, notes, notes)
        bob.library(notes)
        alice.battlefield(FinSet.goalBall, FinExtras.legend)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinExtras.legend)
        resolveAll()
        advanceTo(TurnStep.PostcombatMain)
        alice.activates(FinSet.goalBall, ability = 1)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    @Test
    fun clockworkScytheAttachesOnEntering() = scenario {
        alice.battlefield(bear, *lands("Plains", 4))
        alice.hand(FinSet.clockworkScythe)
        alice.casts(FinSet.clockworkScythe)
        resolveAll()
        yes()
        targets(bear)
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.FirstStrike
        }
    }

    // CR 605.1b: a triggered mana ability on tapping a land for {C}
    @Test
    fun ultimusBlightsALandAndDoublesColorless() = scenario {
        libraries()
        alice.battlefield(FinSet.ultimusOriginOfTheVoid, FinSet.firstVillage)
        bob.battlefield("Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.ultimusOriginOfTheVoid)
        resolveAll()
        targets("Forest")
        expect { "Forest".subtypes.isEmpty() shouldBe true }
    }

    @Test
    fun ultimusAddsAnAdditionalColorless() = scenario {
        alice.battlefield(FinSet.ultimusOriginOfTheVoid, FinSet.firstVillage)
        alice.activates(FinSet.firstVillage, ability = 0)
        resolveAll()
        expect { alice.pool.size shouldBe 2 }
    }
}
