package arcanum.engine.sets.fin

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.FinExtras.bear
import arcanum.engine.sets.fin.FinExtras.charm
import arcanum.engine.sets.fin.FinExtras.doom
import arcanum.engine.sets.fin.FinExtras.flier
import arcanum.engine.sets.fin.FinExtras.giant
import arcanum.engine.sets.fin.FinExtras.legend
import arcanum.engine.sets.fin.FinExtras.notes
import arcanum.engine.sets.fin.FinExtras.ogre
import arcanum.engine.sets.fin.FinExtras.p1p1
import arcanum.engine.sets.fin.FinExtras.trinket
import arcanum.engine.sets.fin.FinExtras.zap
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Final Fantasy chunk 05 in play: sidequest enchantments and legends that transform when a condition is met (CR 701.27a, intervening ifs
 * CR 603.4), "as this transforms into" (an emblem, a chosen opponent) and "when this transforms into" triggers, and the back faces.
 */
class FinQuestScenariosTest {
    private fun Scenario.faceName(name: String): String? = view()[card(name, zone = Zone.Battlefield)]?.characteristics?.name

    private fun lands(land: String, n: Int) = Array(n) { land }

    @Test
    fun theTyrantOfVeshGrowsOnBigSpellsAndTransforms() = scenario {
        libraries()
        val big = arcanum.engine.cards.Cards.invent("Crystal Grand Notes", "{3}{U}", "Sorcery", "Draw a card.")
        alice.battlefield(FinSet.theTyrantOfVesh, counters = listOf(p1p1 to 2))
        alice.battlefield(*lands("Island", 4))
        alice.hand(big)
        alice.casts(big)
        resolveAll()
        assertEquals("The Lord of the Abyss", faceName(FinSet.theTyrantOfVesh))
    }

    @Test
    fun theLordOfTheAbyssBurnsOnAttack() = scenario {
        libraries()
        alice.battlefield(FinSet.theTyrantOfVesh)
        backFaceUp(FinSet.theTyrantOfVesh)
        alice.graveyard(zap, notes, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.theTyrantOfVesh)
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    // CR 106.6: the Tyrant's mana only for noncreature spells
    @Test
    fun theTyrantOfVeshManaIsForNoncreatureSpells() = scenario {
        alice.battlefield(FinSet.theTyrantOfVesh)
        alice.hand(zap)
        alice.casts(zap, target = "bob", paying = listOf("${FinSet.theTyrantOfVesh}:R"))
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun zerandShrinksOthersThenBecomesShenra() = scenario {
        libraries()
        alice.battlefield(*lands("Swamp", 6), bear)
        alice.hand(FinSet.zerandTheUnchained, doom)
        bob.battlefield(giant, ogre)
        alice.casts(FinSet.zerandTheUnchained)
        resolveAll()
        choose(giant)
        expect {
            bear.zone shouldBe Zone.Graveyard
            ogre.power shouldBe 1
            giant.power shouldBe 5
        }
        alice.casts(doom, target = giant)
        resolveAll()
        assertEquals("Shenra, Transcendent Rival", faceName(FinSet.zerandTheUnchained))
    }

    // CR 104.3e, 104.2b: an effect makes a player win when the chosen player loses
    @Test
    fun shenraWinsWhenTheChosenPlayerLoses() = scenario(players = 3) {
        libraries()
        carol.library(notes, notes)
        bob.life = 2
        alice.battlefield(*lands("Swamp", 6), "Mountain")
        alice.hand(FinSet.zerandTheUnchained, doom, zap)
        bob.battlefield(giant)
        alice.casts(FinSet.zerandTheUnchained)
        resolveAll()
        choose(giant)
        alice.casts(doom, target = giant)
        resolveAll()
        choosePlayer(bob)
        alice.casts(zap, target = "bob")
        resolveAll()
        expect {
            state.player(bob.id).lost shouldBe true
            state.player(carol.id).lost shouldBe true
        }
    }

    @Test
    fun ravelMakesWizardsAndTransforms() = scenario {
        libraries()
        alice.battlefield(FinSet.ravelGenomeSorcerer, FinSet.mysticElder, FinSet.telmarGreatSage)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield has "Wizard Token" }
        assertEquals("Trance Ravel, Fate Defied", faceName(FinSet.ravelGenomeSorcerer))
    }

    // CR 614.1a: a Wizard's damage doubled
    @Test
    fun tranceRavelDoublesWizardDamage() = scenario {
        libraries()
        alice.battlefield(FinSet.ravelGenomeSorcerer, FinSet.grimWaltzerNo5, "Mountain")
        backFaceUp(FinSet.ravelGenomeSorcerer)
        alice.hand(zap)
        alice.casts(zap, target = "bob")
        resolveAll()
        // Crystal Zap (not a Wizard) deals 2; Grim Waltzer's 2 doubled to 4
        expect { bob.life shouldBe 14 }
    }

    @Test
    fun valenGrowsByTheDeadCreaturesPower() = scenario {
        alice.battlefield(FinSet.valenShadegunner, "Swamp")
        alice.hand(doom)
        bob.battlefield(giant)
        alice.casts(doom, target = giant)
        resolveAll()
        expect { FinSet.valenShadegunner.counters(p1p1) shouldBe 5 }
    }

    @Test
    fun valenTransformsWhenAttackingAndReturnsWhenItDies() = scenario {
        libraries()
        alice.battlefield(FinSet.valenShadegunner)
        bob.battlefield(giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.valenShadegunner)
        resolveAll()
        yes()
        assertEquals("Wildborn Beast", faceName(FinSet.valenShadegunner))
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(giant to FinSet.valenShadegunner)
        resolveAll()
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            FinSet.valenShadegunner.zone shouldBe Zone.Battlefield
            FinSet.valenShadegunner.tapped shouldBe true
        }
    }

    @Test
    fun severusSacrificesToDrawAndDrains() = scenario {
        libraries()
        alice.battlefield(*lands("Swamp", 3), bear)
        alice.hand(FinSet.severusFabledSoldier)
        alice.casts(FinSet.severusFabledSoldier)
        resolveAll()
        yes()
        choose(bear)
        targets("bob")
        expect {
            alice.hand.size shouldBe 1
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    // CR 603.4? the fourth resolution this turn transforms it; the emblem (CR 114) as it transforms
    @Test
    fun severusTransformsOnTheFourthDeathAndGetsAnEmblem() = scenario {
        libraries()
        alice.battlefield(FinSet.severusFabledSoldier, *lands("Swamp", 4))
        alice.hand(doom, doom, doom, doom)
        bob.battlefield(bear, ogre, giant, flier)
        listOf(bear, ogre, giant, flier).forEach { victim ->
            alice.casts(doom, target = victim)
            resolveAll()
            targets("bob")
        }
        assertEquals("Severus, One-Winged Seraph", faceName(FinSet.severusFabledSoldier))
        expect { state.command.size shouldBe 1 }
    }

    @Test
    fun questPlayTheMatchPumpsThenBecomesATrophy() = scenario {
        libraries()
        alice.battlefield(FinSet.questPlayTheMatch, giant)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        targets(giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(giant)
        resolveAll()
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        choose(giant)
        assertEquals("Champion's Trophy, Celestial Arm", faceName(FinSet.questPlayTheMatch))
        expect {
            bob.life shouldBe 13
            giant hasKeyword Keyword.DoubleStrike
        }
    }

    @Test
    fun cedricLosesLifeThenRedeems() = scenario {
        libraries()
        alice.life = 12
        alice.battlefield(FinSet.cedricDarkKnight)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.cedricDarkKnight)
        resolveAll()
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        assertEquals("Cedric, Redeemed Paladin", faceName(FinSet.cedricDarkKnight))
        expect {
            alice.life shouldBe 10
            FinSet.cedricDarkKnight.tapped shouldBe false
        }
    }

    @Test
    fun cedricRedeemedGivesAttackersIndestructible() = scenario {
        libraries()
        alice.battlefield(FinSet.cedricDarkKnight, bear)
        backFaceUp(FinSet.cedricDarkKnight)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.cedricDarkKnight, bear)
        resolveAll()
        expect { bear hasKeyword Keyword.Indestructible }
    }

    @Test
    fun questBountyBoardDestroysAndPaysTreasure() = scenario {
        libraries()
        alice.battlefield(*lands("Swamp", 5))
        alice.hand(FinSet.questBountyBoard)
        bob.battlefield(ogre)
        alice.casts(FinSet.questBountyBoard)
        resolveAll()
        targets(ogre)
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Graveyard
            alice.battlefield has "Treasure Token"
        }
    }

    @Test
    fun varrokGainsIndestructibleAndTaps() = scenario {
        alice.battlefield(FinSet.questBountyBoard, trinket, "Swamp", "Swamp")
        backFaceUp(FinSet.questBountyBoard)
        alice.activates(FinSet.questBountyBoard, choosing = listOf(trinket))
        resolveAll()
        expect {
            FinSet.questBountyBoard hasKeyword Keyword.Indestructible
            FinSet.questBountyBoard.tapped shouldBe true
        }
    }

    @Test
    fun kaelDiscardsAndDrawsByCardTypes() = scenario {
        libraries()
        alice.battlefield(*lands("Island", 2), *lands("Swamp", 2), "Mountain")
        alice.hand(FinSet.kaelCourtJester, ogre)
        bob.hand(notes)
        alice.casts(FinSet.kaelCourtJester)
        resolveAll()
        choose(ogre)
        choose(notes)
        expect { alice.hand.size shouldBe 2 }
    }

    @Test
    fun kaelRulerDrawsWhenOpponentsLoseLife() = scenario {
        libraries()
        alice.battlefield(FinSet.kaelCourtJester, "Mountain")
        backFaceUp(FinSet.kaelCourtJester)
        alice.hand(zap)
        alice.casts(zap, target = "bob")
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    // CR 601.2f: costs {1} less for each other Town
    @Test
    fun skyhavenTransformsCheaperWithTowns() = scenario {
        alice.battlefield(FinSet.skyhavenMercenaryAcademy, FinSet.firstVillage, *lands("Forest", 3), *lands("Island", 2))
        alice.activates(FinSet.skyhavenMercenaryAcademy, ability = 1)
        resolveAll()
        assertEquals("Skyhaven, Airborne", faceName(FinSet.skyhavenMercenaryAcademy))
    }

    @Test
    fun morvathTransformsWithAFullGraveyard() = scenario {
        libraries()
        alice.battlefield(FinSet.morvathVoidWarlock)
        alice.graveyard(bear, bear, ogre, ogre, giant, charm)
        advanceTo(TurnStep.End)
        resolveAll()
        assertEquals("Neo Morvath, Dimension's End", faceName(FinSet.morvathVoidWarlock))
        expect { FinSet.morvathVoidWarlock.power shouldBe 6 }
    }

    @Test
    fun sylvaMakesLegendsCheaperAndTransforms() = scenario {
        libraries()
        alice.battlefield(FinSet.sylvaFarron, legend, FinSet.gerraTiel)
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(FinSet.aelthaHeartOfTheCrystal)
        alice.casts(FinSet.aelthaHeartOfTheCrystal)
        resolveAll()
        expect { FinSet.aelthaHeartOfTheCrystal.zone shouldBe Zone.Battlefield }
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        yes()
        assertEquals("Crystallized Sylva", faceName(FinSet.sylvaFarron))
        expect { legend.power shouldBe 4 }
    }

    @Test
    fun questCollectorsAlbumDrawsThreeDiscardsTwo() = scenario {
        libraries()
        alice.battlefield(*lands("Island", 4))
        alice.hand(FinSet.questCollectorsAlbum)
        alice.casts(FinSet.questCollectorsAlbum)
        resolveAll()
        choose(notes, notes)
        expect {
            alice.hand.size shouldBe 1
            alice.graveyard.size shouldBe 2
        }
    }

    @Test
    fun questCollectorsAlbumBecomesACard() = scenario {
        libraries()
        alice.battlefield(FinSet.questCollectorsAlbum)
        alice.graveyard(notes, notes, notes, notes, notes, notes, notes, notes)
        advanceTo(TurnStep.End)
        resolveAll()
        assertEquals("Enchanted Playing Card", faceName(FinSet.questCollectorsAlbum))
    }

    @Test
    fun questRaiseARunnerBecomesEbonyRunner() = scenario {
        alice.library("Forest", notes, notes)
        bob.library(notes)
        alice.battlefield(FinSet.questRaiseARunner, flier, flier, flier, flier)
        turn(step = TurnStep.Upkeep)
        advanceTo(TurnStep.PrecombatMain)
        resolveAll()
        choose("Forest")
        assertEquals("Ebony Runner", faceName(FinSet.questRaiseARunner))
        expect { card("Forest").zone shouldBe Zone.Battlefield }
    }

    @Test
    fun questRaiseARunnerMakesABird() = scenario {
        alice.battlefield(*lands("Forest", 2))
        alice.hand(FinSet.questRaiseARunner)
        alice.casts(FinSet.questRaiseARunner)
        resolveAll()
        expect { alice.battlefield has "Bird Token" }
    }

    // CR 500.7: an extra turn
    @Test
    fun ysoldeTransformsAndTakesAnExtraTurn() = scenario {
        libraries()
        alice.battlefield(FinSet.ysoldeTimeSorceress, *lands("Island", 6), *lands("Swamp", 2))
        alice.graveyard(notes, notes, notes, notes, notes, notes, notes, notes)
        advanceTo(TurnStep.End)
        resolveAll()
        yes()
        pay()
        choose(notes, notes, notes, notes, notes, notes, notes, notes)
        assertEquals("Ysolde, Omnipotent", faceName(FinSet.ysoldeTimeSorceress))
        expect { state.turn.extraTurns shouldBe listOf(alice.id) }
    }

    @Test
    fun aethelOfTheDeepPlaysFromTheGraveyardAndExilesWhatDies() = scenario {
        alice.battlefield(FinSet.aethelUnsundered, bear, "Mountain", "Mountain", "Mountain", "Mountain")
        backFaceUp(FinSet.aethelUnsundered)
        alice.graveyard(ogre)
        alice.hand(zap)
        alice.casts(ogre)
        resolveAll()
        alice.casts(zap, target = bear)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Battlefield
            bear.zone shouldBe Zone.Exile
            zap.zone shouldBe Zone.Exile
        }
    }

    @Test
    fun aethelLootsOnEntering() = scenario {
        libraries()
        alice.battlefield("Island", "Swamp", "Swamp")
        alice.hand(FinSet.aethelUnsundered, ogre)
        alice.casts(FinSet.aethelUnsundered)
        resolveAll()
        choose(ogre)
        expect { alice.graveyard has ogre }
    }

    @Test
    fun questGoneFishingFindsACreatureAndBecomesACamp() = scenario {
        alice.library(ogre, notes)
        bob.library(notes)
        alice.battlefield(FinSet.questGoneFishing)
        turn(step = TurnStep.Untap)
        advanceTo(TurnStep.Upkeep)
        resolveAll()
        yes()
        assertEquals("Riverside Camp", faceName(FinSet.questGoneFishing))
        expect {
            alice.hand has ogre
            alice.battlefield has "Food Token"
        }
    }

    @Test
    fun riversideCampCountersEveryCreature() = scenario {
        alice.battlefield(FinSet.questGoneFishing, bear, trinket, "Plains", "Plains", "Plains")
        backFaceUp(FinSet.questGoneFishing)
        alice.activates(FinSet.questGoneFishing, ability = 1, choosing = listOf(trinket))
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }
}
