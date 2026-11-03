package arcanum.engine.sets.fin

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.FinExtras.bear
import arcanum.engine.sets.fin.FinExtras.charm
import arcanum.engine.sets.fin.FinExtras.indestructible
import arcanum.engine.sets.fin.FinExtras.lore
import arcanum.engine.sets.fin.FinExtras.notes
import arcanum.engine.sets.fin.FinExtras.ogre
import arcanum.engine.sets.fin.FinExtras.stun
import arcanum.engine.sets.fin.FinExtras.trinket
import arcanum.engine.sets.fin.FinExtras.zap
import kotlin.test.Test

/**
 * Final Fantasy chunk 03 in play: Saga creatures (summons, CR 714): a lore counter as each one enters and in each of its controller's
 * precombat main phases (CR 714.3a, b) triggers that chapter; after the last chapter resolves it is sacrificed (CR 714.4). Conjure Featherfoot
 * Stampede's chapters repeat the tested "other creatures you control get" anthem chapter of Conjure Roundtable Paladins.
 */
class FinSummonScenariosTest {
    /** [name] on the battlefield with the lore counters of the chapters before [chapter]; its precombat main phase begins, adding the
     * counter of [chapter] (CR 714.3b). */
    private fun Scenario.reachChapter(name: String, chapter: Int) {
        libraries()
        alice.battlefield(name, counters = if (chapter > 1) listOf(lore to chapter - 1) else emptyList())
        turn(step = TurnStep.Upkeep)
        advanceTo(TurnStep.PrecombatMain)
    }

    @Test
    fun conjureThreefoldHoundSurveilsThenCopiesTwice() = scenario {
        libraries()
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(FinSet.conjureThreefoldHound)
        alice.casts(FinSet.conjureThreefoldHound)
        resolveAll()
        choose()
        expect { FinSet.conjureThreefoldHound.counters(lore) shouldBe 1 }
    }

    // CR 707.10: "when you next cast an instant or sorcery spell this turn, copy it twice"
    @Test
    fun conjureThreefoldHoundTripleChapter() = scenario {
        alice.battlefield("Mountain")
        alice.hand(zap)
        reachChapter(FinSet.conjureThreefoldHound, 3)
        resolveAll()
        alice.casts(zap, target = "bob")
        resolveAll()
        while (pending is arcanum.engine.decision.Decision.May) yes(false)
        resolveAll()
        expect {
            bob.life shouldBe 14
            FinSet.conjureThreefoldHound.zone shouldBe Zone.Graveyard
        }
    }

    @Test
    fun conjureRotundStriderMakesABirdThenTrample() = scenario {
        libraries()
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(FinSet.conjureRotundStrider, "Forest")
        alice.casts(FinSet.conjureRotundStrider)
        resolveAll()
        alice.plays("Forest")
        resolveAll()
        expect { card("Bird Token").view.characteristics!!.power shouldBe 3 }
    }

    @Test
    fun conjureRotundStriderGivesTrample() = scenario {
        reachChapter(FinSet.conjureRotundStrider, 2)
        resolveAll()
        expect { FinSet.conjureRotundStrider hasKeyword Keyword.Trample }
    }

    @Test
    fun conjureMoonhowlWolfFetchesALand() = scenario {
        alice.library("Forest", notes)
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(FinSet.conjureMoonhowlWolf)
        alice.casts(FinSet.conjureMoonhowlWolf)
        resolveAll()
        choose("Forest")
        expect { alice.battlefield.names.count { it == "Forest" } shouldBe 4 }
    }

    @Test
    fun conjureMoonhowlWolfGivesTheNextCreatureACounter() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(bear)
        reachChapter(FinSet.conjureMoonhowlWolf, 2)
        resolveAll()
        alice.casts(bear)
        resolveAll()
        expect { bear.counters(FinExtras.p1p1) shouldBe 1 }
    }

    @Test
    fun conjureMoonhowlWolfDrawsWithTheGreatestPower() = scenario {
        reachChapter(FinSet.conjureMoonhowlWolf, 3)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    @Test
    fun conjureGaleHarpyShootsATappedCreature() = scenario {
        bob.battlefield(ogre, tapped = true)
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(FinSet.conjureGaleHarpy)
        alice.casts(FinSet.conjureGaleHarpy)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun conjureGaleHarpyLendsFlight() = scenario {
        alice.battlefield(bear)
        reachChapter(FinSet.conjureGaleHarpy, 2)
        resolveAll()
        expect {
            bear hasKeyword Keyword.Flying
            bear.power shouldBe 3
        }
    }

    @Test
    fun conjureChainboundValkyrieLetsYouPlayTheExiledCard() = scenario {
        alice.library(ogre, notes)
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(FinSet.conjureChainboundValkyrie)
        alice.casts(FinSet.conjureChainboundValkyrie)
        resolveAll()
        alice.casts(ogre)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun conjureChainboundValkyrieHastesTheNextCreature() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(ogre)
        reachChapter(FinSet.conjureChainboundValkyrie, 2)
        resolveAll()
        alice.casts(ogre)
        resolveAll()
        expect { ogre hasKeyword Keyword.Haste }
    }

    @Test
    fun conjureRoundtablePaladinsMakesKnights() = scenario {
        reachChapter(FinSet.conjureRoundtablePaladins, 2)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Knight Token" } shouldBe 3 }
    }

    @Test
    fun conjureRoundtablePaladinsUltimateEnd() = scenario {
        alice.battlefield(bear)
        reachChapter(FinSet.conjureRoundtablePaladins, 5)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear.counters(indestructible) shouldBe 1
            FinSet.conjureRoundtablePaladins.zone shouldBe Zone.Graveyard
        }
    }

    @Test
    fun conjureSorrowShadeDrawsThenEdicts() = scenario {
        bob.battlefield(ogre)
        reachChapter(FinSet.conjureSorrowShade, 4)
        resolveAll()
        choose(ogre)
        expect {
            ogre.zone shouldBe Zone.Graveyard
            bob.life shouldBe 17
        }
    }

    @Test
    fun conjureSorrowShadePain() = scenario {
        reachChapter(FinSet.conjureSorrowShade, 1)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            alice.life shouldBe 19
        }
    }

    @Test
    fun conjureSkyfireWyrmMegaFlare() = scenario {
        alice.battlefield(trinket, FinExtras.lance)
        reachChapter(FinSet.conjureSkyfireWyrm, 4)
        resolveAll()
        // Crystal Trinket and Crystal Lance: mana value 1 each
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun conjureSkyfireWyrmDestroysAPermanent() = scenario {
        bob.battlefield(charm)
        reachChapter(FinSet.conjureSkyfireWyrm, 1)
        resolveAll()
        targets(charm)
        expect { charm.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun conjureSpectralRiderDestroysThenWinsByDamage() = scenario {
        libraries()
        bob.battlefield(ogre)
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(FinSet.conjureSpectralRider)
        alice.casts(FinSet.conjureSpectralRider)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Graveyard }
    }

    // CR 104.3e: an effect says a player loses the game
    @Test
    fun conjureSpectralRiderZantetsuken() = scenario {
        reachChapter(FinSet.conjureSpectralRider, 2)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.conjureSpectralRider)
        resolveAll()
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect { state.player(bob.id).lost shouldBe true }
    }

    @Test
    fun conjureRimeMaidenStunsThenDraws() = scenario {
        bob.battlefield(ogre)
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(FinSet.conjureRimeMaiden)
        alice.casts(FinSet.conjureRimeMaiden)
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            ogre.counters(stun) shouldBe 1
        }
    }

    @Test
    fun conjureRimeMaidenDiamondDust() = scenario {
        bob.battlefield(ogre, bear, tapped = true)
        reachChapter(FinSet.conjureRimeMaiden, 3)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    @Test
    fun conjureBedrockGiantReturnsLands() = scenario {
        alice.graveyard("Forest", "Forest")
        reachChapter(FinSet.conjureBedrockGiant, 2)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Forest" } shouldBe 2 }
    }

    @Test
    fun conjureBedrockGiantPumpsByLands() = scenario {
        alice.battlefield(bear, "Forest", "Forest", "Forest")
        reachChapter(FinSet.conjureBedrockGiant, 3)
        resolveAll()
        expect {
            bear.power shouldBe 5
            bear hasKeyword Keyword.Trample
        }
    }

    @Test
    fun conjureBedrockGiantMills() = scenario {
        reachChapter(FinSet.conjureBedrockGiant, 1)
        resolveAll()
        expect { alice.graveyard.size shouldBe 5 }
    }

    @Test
    fun conjureFeatherfootStampedePumpsOthers() = scenario {
        alice.battlefield(bear)
        reachChapter(FinSet.conjureFeatherfootStampede, 2)
        resolveAll()
        expect {
            bear.power shouldBe 3
            FinSet.conjureFeatherfootStampede.power shouldBe 3
        }
    }

    @Test
    fun conjureStormcallerSageJudgmentBolt() = scenario {
        libraries()
        bob.battlefield(ogre)
        alice.graveyard(zap, notes, charm, bear)
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(FinSet.conjureStormcallerSage)
        alice.casts(FinSet.conjureStormcallerSage)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun conjureStormcallerSagePumpsWizards() = scenario {
        reachChapter(FinSet.conjureStormcallerSage, 2)
        resolveAll()
        expect { FinSet.conjureStormcallerSage.power shouldBe 4 }
    }

    @Test
    fun conjureAbyssalWyrmBouncesTheRest() = scenario {
        bob.battlefield(ogre)
        reachChapter(FinSet.conjureAbyssalWyrm, 1)
        resolveAll()
        expect {
            bob.hand has ogre
            FinSet.conjureAbyssalWyrm.zone shouldBe Zone.Battlefield
        }
    }

    @Test
    fun conjureAbyssalWyrmDrawsOnSeaAttacks() = scenario {
        reachChapter(FinSet.conjureAbyssalWyrm, 2)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.conjureAbyssalWyrm)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    @Test
    fun conjureCinderImpLoots() = scenario {
        libraries()
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(FinSet.conjureCinderImp, ogre)
        alice.casts(FinSet.conjureCinderImp)
        resolveAll()
        yes()
        choose(ogre)
        expect {
            alice.graveyard has ogre
            alice.hand.size shouldBe 1
        }
    }

    @Test
    fun conjureCinderImpAddsMana() = scenario {
        reachChapter(FinSet.conjureCinderImp, 3)
        resolveAll()
        expect { alice.pool.size shouldBe 1 }
    }

    @Test
    fun conjureFeatherfootStampedeIsSacrificedAfterTheLastChapter() = scenario {
        reachChapter(FinSet.conjureFeatherfootStampede, 4)
        resolveAll()
        expect { FinSet.conjureFeatherfootStampede.zone shouldBe Zone.Graveyard }
    }
}
