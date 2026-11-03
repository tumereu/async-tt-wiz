package arcanum.engine.sets.fin

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.FinExtras.bear
import arcanum.engine.sets.fin.FinExtras.charm
import arcanum.engine.sets.fin.FinExtras.giant
import arcanum.engine.sets.fin.FinExtras.lance
import arcanum.engine.sets.fin.FinExtras.notes
import arcanum.engine.sets.fin.FinExtras.ogre
import arcanum.engine.sets.fin.FinExtras.p1p1
import arcanum.engine.sets.fin.FinExtras.stun
import arcanum.engine.sets.fin.FinExtras.trinket
import arcanum.engine.sets.fin.FinExtras.zap
import kotlin.test.Test

/**
 * Final Fantasy chunks 09 and 10 in play: the blue cards. An additional end step (CR 500.9), coin flips won by a replacement (CR 705.2,
 * 614.1a), countering abilities (CR 701.6b), an ability that can't be copied (CR 707.10), casting from a graveyard with mana of any type
 * (CR 609.4b), borrowing an Equipment until control is lost (CR 603.7c), a bigger mill (CR 614.1a). Dream of the Faraway, Ejection Seat,
 * Valkyrie Gunship, Stinger Sentry, Journey Across the World and Frost Pudding use tested surveil, bounce, affinity, land-count and stun
 * templates.
 */
class FinBlueScenariosTest {
    private fun lands(land: String, n: Int) = Array(n) { land }
    private val coin = Cards.invent("Crystal Coin Toss", "{U}", "Sorcery", "Flip a coin. If you win the flip, draw two cards.")

    // ---- chunk 09

    // CR 500.9: an additional end step after this one
    @Test
    fun ysraBlinksAndAddsAnEndStep() = scenario {
        libraries()
        alice.battlefield(FinSet.ysraWoodSeer, bear)
        advanceTo(TurnStep.End)
        resolveAll()
        targets(bear)
        expect {
            bear.zone shouldBe Zone.Battlefield
            state.turn.step shouldBe TurnStep.End
        }
        allPass()
        resolveAll()
        targets(bear)
        // the second end step: another blink, no third step
        expect { state.turn.step shouldBe TurnStep.End }
        allPass()
        resolveAll()
        expect { state.turn.active shouldBe bob.id }
    }

    @Test
    fun mentorsSacrificeCountersAnActivatedAbility() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(FinSet.mentorsSacrifice)
        bob.battlefield(FinSet.hamletMerchant, "Island")
        bob.library(notes)
        turn(active = bob)
        bob.activates(FinSet.hamletMerchant)
        bob.passes()
        alice.casts(FinSet.mentorsSacrifice)
        if (pending is arcanum.engine.decision.Decision.ChooseOption) answer(arcanum.engine.decision.Answer.Option((pending as arcanum.engine.decision.Decision.ChooseOption).options.last()))
        targetStackItem()
        resolveAll()
        expect { bob.hand.size shouldBe 0 }
    }

    @Test
    fun theMoonwardWhaleLetsYouPlayTheTopCardAfterAttacking() = scenario {
        alice.library(ogre, notes)
        bob.library(notes)
        alice.battlefield(FinSet.theMoonwardWhale, bear, "Mountain", "Mountain", "Mountain")
        alice.activates(FinSet.theMoonwardWhale, choosing = listOf(bear))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.theMoonwardWhale)
        resolveAll()
        advanceTo(TurnStep.PostcombatMain)
        alice.casts(ogre)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun recollectionsReturningHandsOutCards() = scenario {
        alice.library(ogre, bear, giant, charm, trinket, notes)
        alice.battlefield(*lands("Island", 4))
        alice.hand(FinSet.recollectionsReturning)
        alice.casts(FinSet.recollectionsReturning)
        resolveAll()
        choose(ogre)
        choose(bear)
        choose(giant)
        choose(charm)
        resolveAll()
        expect {
            alice.hand has ogre
            alice.hand has giant
            alice.hand has trinket
            alice.hand.size shouldBe 3
        }
    }

    // CR 118.12a: counter unless its controller pays
    @Test
    fun devouredByTheDeepCountersWhatIsntPaidFor() = scenario {
        alice.library(notes, notes, notes)
        alice.battlefield(*lands("Island", 3))
        alice.hand(FinSet.devouredByTheDeep)
        alice.graveyard(notes)
        bob.battlefield("Mountain")
        bob.hand(zap)
        turn(active = bob)
        bob.casts(zap, target = "alice")
        bob.passes()
        alice.casts(FinSet.devouredByTheDeep)
        targetStackItem()
        resolveAll()
        choose(notes, notes)
        resolveAll()
        expect {
            alice.life shouldBe 20
            bob.graveyard has zap
        }
    }

    // CR 614.1a: a mill of more
    @Test
    fun theTideCrystalMakesOpponentsMillMore() = scenario {
        alice.battlefield(FinSet.theTideCrystal, *lands("Island", 6))
        alice.hand(notes)
        bob.library(*Array(10) { notes })
        alice.activates(FinSet.theTideCrystal)
        resolveAll()
        // one card in hand, plus four
        expect { bob.graveyard.size shouldBe 5 }
    }

    @Test
    fun trappedInTheSanctumLocksAPermanent() = scenario {
        libraries()
        alice.battlefield(*lands("Island", 3))
        alice.hand(FinSet.trappedInTheSanctum)
        bob.battlefield(FinSet.hamletMerchant, "Island")
        alice.casts(FinSet.trappedInTheSanctum, target = FinSet.hamletMerchant)
        resolveAll()
        expect { FinSet.hamletMerchant.tapped shouldBe true }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { FinSet.hamletMerchant.tapped shouldBe true }
    }

    @Test
    fun recoverTheEsperFromTheGraveyardGetsCounters() = scenario {
        alice.battlefield(*lands("Island", 6))
        alice.graveyard(FinSet.recoverTheEsper)
        alice.casts(FinSet.recoverTheEsper)
        resolveAll()
        expect { card("Robot Warrior Token").counters(p1p1) shouldBe 2 }
    }

    // CR 705.2: the first flip each turn is won
    @Test
    fun edrikDrawsForArtifactsAndWinsTheFirstFlip() = scenario {
        libraries()
        alice.battlefield(trinket, lance, *lands("Island", 7))
        alice.hand(FinSet.edrikKingOfTheSands, coin)
        alice.casts(FinSet.edrikKingOfTheSands)
        resolveAll()
        expect { alice.hand.size shouldBe 3 }
        random(arcanum.engine.decision.Answer.CoinFlip(false))
        alice.casts(coin)
        resolveAll()
        expect { alice.hand.size shouldBe 4 }
    }

    @Test
    fun quessaCastsFromAGraveyardWithAnyManaAndExilesIt() = scenario {
        alice.battlefield(*lands("Island", 3), "Plains")
        alice.hand(FinSet.quessaTrepid)
        bob.graveyard(zap)
        alice.casts(FinSet.quessaTrepid, paying = listOf("Island", "Island", "Island"))
        resolveAll()
        yes()
        targets("bob")
        pay()
        expect {
            bob.life shouldBe 18
            zap.zone shouldBe Zone.Exile
        }
    }

    // CR 707.10: copies of an ability; this ability can't be copied
    @Test
    fun mimoCopiesAnAbilityXTimes() = scenario {
        alice.battlefield(FinSet.mimoMasterOfMimicry, FinSet.hamletMerchant, *lands("Island", 5))
        alice.library(notes, notes, notes, notes)
        alice.hand(ogre, ogre, ogre)
        alice.activates(FinSet.hamletMerchant)
        alice.activates(FinSet.mimoMasterOfMimicry, x = 2)
        targetStackItem()
        resolveAll()
        while (pending is arcanum.engine.decision.Decision.May) yes(false)
        while (pending is arcanum.engine.decision.Decision.ChooseObjects) choose(ogre)
        expect { alice.graveyard.names.count { it == ogre } shouldBe 3 }
    }

    // CR 603.7c: the delayed trigger when control is lost
    @Test
    fun borrowedUniformTakesAnEquipmentForTheTurn() = scenario {
        libraries()
        alice.battlefield(bear, "Island")
        alice.hand(FinSet.borrowedUniform)
        bob.battlefield(lance)
        alice.casts(FinSet.borrowedUniform, targets = listOf(bear, lance))
        resolveAll()
        expect {
            lance.controller shouldBe alice.id
            bear.power shouldBe 3
        }
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        expect {
            lance.controller shouldBe bob.id
            bear.power shouldBe 2
        }
    }

    // ---- chunk 10

    @Test
    fun syncopatedRhythmExilesWhatItCounters() = scenario {
        alice.battlefield(*lands("Island", 3))
        alice.hand(FinSet.syncopatedRhythm)
        bob.battlefield("Mountain")
        bob.hand(zap)
        turn(active = bob)
        bob.casts(zap, target = "alice")
        bob.passes()
        alice.casts(FinSet.syncopatedRhythm, x = 2)
        targetStackItem()
        resolveAll()
        while (pending is arcanum.engine.decision.Decision.May) yes(false)
        expect { zap.zone shouldBe Zone.Exile }
    }

    @Test
    fun ultaraTapsOpponentsAndDrawsOnDamage() = scenario {
        libraries()
        alice.battlefield(*lands("Island", 6), bear)
        alice.hand(FinSet.ultaraTemporalThreat)
        bob.battlefield(ogre)
        alice.casts(FinSet.ultaraTemporalThreat)
        resolveAll()
        expect { ogre.tapped shouldBe true }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    @Test
    fun aetherTinctureCopiesTheNextSpell() = scenario {
        alice.battlefield(FinSet.aetherTincture, "Mountain")
        alice.hand(zap)
        alice.activates(FinSet.aetherTincture)
        resolveAll()
        alice.casts(zap, target = "bob", paying = listOf("Mountain"))
        resolveAll()
        while (pending is arcanum.engine.decision.Decision.May) yes(false)
        expect { bob.life shouldBe 16 }
    }

    // CR 106.6: restricted mana
    @Test
    fun freightSkiffManaForArtifacts() = scenario {
        alice.battlefield(FinSet.freightSkiff)
        alice.hand(trinket)
        alice.casts(trinket)
        resolveAll()
        expect { trinket.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun hamletMerchantDrawsThreeCheaperWithTowns() = scenario {
        alice.library(notes, notes, notes)
        alice.battlefield(FinSet.hamletMerchant, FinSet.firstVillage, FinSet.harborTown, *lands("Island", 5))
        alice.activates(FinSet.hamletMerchant, ability = 1)
        resolveAll()
        expect {
            alice.hand.size shouldBe 3
            FinSet.hamletMerchant.zone shouldBe Zone.Graveyard
        }
    }

    @Test
    fun spellDamperUntapsAndProtects() = scenario {
        alice.battlefield(bear, tapped = true)
        alice.battlefield("Island")
        alice.hand(FinSet.spellDamper)
        alice.casts(FinSet.spellDamper, target = bear)
        resolveAll()
        expect {
            bear.tapped shouldBe false
            bear.power shouldBe 3
            bear hasKeyword Keyword.Hexproof
        }
    }

    @Test
    fun watchtowerTurretLootsOnArtifacts() = scenario {
        libraries()
        alice.battlefield(FinSet.watchtowerTurret, "Island")
        alice.hand(trinket, ogre)
        alice.casts(trinket)
        resolveAll()
        yes()
        choose(ogre)
        expect { alice.graveyard has ogre }
    }

    @Test
    fun frostPuddingStunsOnEntering() = scenario {
        alice.battlefield(*lands("Island", 6))
        alice.hand(FinSet.frostPudding)
        bob.battlefield(ogre)
        alice.casts(FinSet.frostPudding)
        resolveAll()
        expect { ogre.counters(stun) shouldBe 1 }
    }

    @Test
    fun drillInstructionDrawsAndCounters() = scenario {
        libraries()
        alice.battlefield(*lands("Island", 3), bear)
        alice.hand(FinSet.drillInstruction)
        alice.casts(FinSet.drillInstruction, targets = listOf("alice", bear))
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            bear.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun portraitSketchCopiesALand() = scenario {
        alice.battlefield(*lands("Island", 4))
        alice.hand(FinSet.portraitSketch)
        bob.battlefield(giant)
        alice.casts(FinSet.portraitSketch, target = giant)
        resolveAll()
        expect { alice.battlefield has giant }
    }

    @Test
    fun stingerSentryWithSevenLands() = scenario {
        alice.battlefield(FinSet.stingerSentry, *lands("Island", 7))
        expect { FinSet.stingerSentry.power shouldBe 4 }
    }

    // CR 603.2: "when enchanted creature is dealt damage"
    @Test
    fun slumberCharmBreaksOnDamage() = scenario {
        alice.battlefield("Island", "Mountain")
        alice.hand(FinSet.slumberCharm, zap)
        bob.battlefield(giant)
        alice.casts(FinSet.slumberCharm, target = giant)
        resolveAll()
        expect { giant.tapped shouldBe true }
        alice.casts(zap, target = giant)
        resolveAll()
        expect { FinSet.slumberCharm.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun journeyAcrossTheWorldCostsLessWithTowns() = scenario {
        alice.library(notes, notes, notes, notes)
        alice.battlefield(FinSet.firstVillage, FinSet.harborTown, *lands("Island", 5))
        alice.hand(FinSet.journeyAcrossTheWorld)
        alice.casts(FinSet.journeyAcrossTheWorld)
        resolveAll()
        expect { alice.hand.size shouldBe 4 }
    }
}
