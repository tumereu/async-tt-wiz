package arcanum.engine.sets.tla

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.tla.TlaExtras.bear
import arcanum.engine.sets.tla.TlaExtras.brute
import arcanum.engine.sets.tla.TlaExtras.flier
import arcanum.engine.sets.tla.TlaExtras.notes
import arcanum.engine.sets.tla.TlaExtras.ogre
import arcanum.engine.sets.tla.TlaExtras.p1p1
import arcanum.engine.sets.tla.TlaExtras.trinket
import arcanum.engine.sets.tla.TlaExtras.zap
import kotlin.test.Test

/**
 * Avatar: The Last Airbender chunks 11 and 12 cards in play: Allies and the cards that reward them, the Shrines that count each other,
 * and the white creatures and spells. Siege Reinforcements is played in [TlaEarthbendScenariosTest.tobinEarthbendsForAllies]; Fanblade
 * Warriors and Canopy Liberators (an Ally token as they enter), Kite Youngsters (scry 1 as it enters) and Old Master's Deserters (a
 * counter as it enters) repeat that tested template; Hopperoo Troop's plainscycling is a covered keyword.
 */
class TlaAllyScenariosTest {
    private val villager = Cards.invent("Village Ally", "{W}", "Creature — Human Ally", "", "1/1")
    private val shrine = Cards.invent("Quiet Shrine", "{1}", "Legendary Enchantment — Shrine", "")
    private val shrine2 = Cards.invent("Still Shrine", "{1}", "Legendary Enchantment — Shrine", "")
    private val hawk = Cards.invent("Sky Hawk", "{1}{W}", "Creature — Bird", "Flying", "1/1")
    private fun allyTokens(n: Int) = "Ally Token" to n

    // ---- chunk 11

    @Test
    fun heraldEnthusiastsGrowWithAllies() = scenario {
        alice.battlefield(TlaSet.heraldEnthusiasts, "Plains")
        alice.hand(villager)
        alice.casts(villager)
        resolveAll()
        expect { TlaSet.heraldEnthusiasts.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun stoneRealmProtectorsSacrifice() = scenario {
        alice.battlefield(TlaSet.stoneRealmProtectors, villager)
        alice.activates(TlaSet.stoneRealmProtectors, target = villager)
        resolveAll()
        expect {
            villager hasKeyword Keyword.Indestructible
            alice.graveyard has TlaSet.stoneRealmProtectors
        }
    }

    @Test
    fun gatherThePaleBlossom() = scenario {
        alice.battlefield(*Array(5) { "Plains" })
        alice.hand(TlaSet.gatherThePaleBlossom)
        alice.library(notes, notes)
        alice.casts(TlaSet.gatherThePaleBlossom)
        resolveAll()
        choose()
        choose(notes, notes)
        expect { alice.battlefield.names.count { it == allyTokens(5).first } shouldBe 5 }
    }

    /** An Ally cast from the top of the library; sacrificed, creatures get +0/+5 and indestructible. */
    // CR 601.2a
    @Test
    fun torvaldCastsAlliesFromTheTop() = scenario {
        alice.battlefield(TlaSet.torvaldSelflessCommander, bear, "Plains")
        alice.library(villager)
        alice.casts(villager)
        resolveAll()
        expect { villager.zone shouldBe Zone.Battlefield }
        alice.activates(TlaSet.torvaldSelflessCommander)
        resolveAll()
        expect {
            bear.toughness shouldBe 7
            bear hasKeyword Keyword.Indestructible
        }
    }

    /** Two Allies enter: 1 life each, and the second resolution draws. */
    @Test
    fun southernIceVoyagerSecondTimeDraws() = scenario {
        alice.battlefield(TlaSet.southernIceVoyager, "Plains", "Plains")
        alice.hand(TlaSet.siegeReinforcements)
        alice.library(notes)
        alice.casts(TlaSet.siegeReinforcements)
        resolveAll()
        expect {
            alice.life shouldBe 22
            alice.hand has notes
        }
    }

    /** Others get +1/+0; a permanent leaving during your turn makes one Ally, once each turn. */
    @Test
    fun yarraMakesAnAllyOnce() = scenario {
        alice.battlefield(TlaSet.yarraCourageousRescuer, bear, ogre, "Mountain", "Mountain")
        alice.hand(zap, zap)
        expect { bear.power shouldBe 3 }
        alice.casts(zap, target = bear)
        resolveAll()
        alice.casts(zap, target = ogre)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Ally Token" } shouldBe 1 }
    }

    @Test
    fun alliedVanguard() = scenario {
        alice.battlefield(bear, "Plains", "Plains", "Plains", "Plains")
        alice.hand(TlaSet.alliedVanguard)
        alice.casts(TlaSet.alliedVanguard, x = 2)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Ally Token" } shouldBe 2
            bear.counters(p1p1) shouldBe 1
            "Ally Token".power shouldBe 2
        }
    }

    @Test
    fun tideclanCaptainPumps() = scenario {
        alice.battlefield(TlaSet.tideclanCaptain, bear, *Array(5) { "Plains" })
        alice.activates(TlaSet.tideclanCaptain)
        resolveAll()
        expect { bear.power shouldBe 3 }
    }

    @Test
    fun wartimeDissentersHasteNewAllies() = scenario {
        alice.battlefield(TlaSet.wartimeDissenters, "Plains")
        alice.hand(villager)
        alice.casts(villager)
        resolveAll()
        expect {
            villager.counters(p1p1) shouldBe 1
            villager hasKeyword Keyword.Haste
        }
    }

    /** Affinity for two Allies: {G}; both creatures deal their power to the opponent's creature. */
    // CR 702.41a
    @Test
    fun comradesAtLast() = scenario {
        alice.battlefield(villager, TlaSet.heraldEnthusiasts, "Forest")
        alice.hand(TlaSet.comradesAtLast)
        bob.battlefield(TlaExtras.brute)
        alice.casts(TlaSet.comradesAtLast, targets = listOf(villager, TlaSet.heraldEnthusiasts, brute))
        resolveAll()
        expect { brute.view.obj?.damage shouldBe 3 }
    }

    @Test
    fun grandChasmGuideAnyColor() = scenario {
        alice.battlefield(TlaSet.grandChasmGuide, "Forest")
        alice.activates("Forest", ability = 1, color = Color.Blue)
        expect { alice.pool.single().color shouldBe Color.Blue }
    }

    @Test
    fun siegeTactics() = scenario {
        alice.battlefield(villager, *Array(5) { "Forest" })
        alice.hand(TlaSet.siegeTactics)
        alice.library(notes)
        alice.casts(TlaSet.siegeTactics)
        resolveAll()
        expect { villager.power shouldBe 3 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(villager)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect { alice.hand has notes }
    }

    @Test
    fun stoneSovereignsLieutenant() = scenario {
        alice.battlefield(villager, "Forest", "Plains", "Plains")
        alice.hand(TlaSet.stoneSovereignsLieutenant, TlaExtras.zap.let { villager })
        alice.casts(TlaSet.stoneSovereignsLieutenant, paying = listOf("Forest", "Plains"))
        resolveAll()
        expect { card(villager, 0).counters(p1p1) shouldBe 1 }
        alice.casts(villager)
        resolveAll()
        expect { TlaSet.stoneSovereignsLieutenant.counters(p1p1) shouldBe 1 }
    }

    /** An Ally's triggered ability triggers an additional time. */
    // CR 603.2d
    @Test
    fun nerysTheFearlessDoublesAllyTriggers() = scenario {
        alice.battlefield(TlaSet.nerysTheFearless, TlaSet.heraldEnthusiasts, "Plains")
        alice.hand(villager)
        alice.casts(villager)
        resolveAll()
        expect { TlaSet.heraldEnthusiasts.counters(p1p1) shouldBe 2 }
    }

    /** Other Allies have prowess; a noncreature spell makes an Ally. */
    @Test
    fun tammMakesAllies() = scenario {
        alice.battlefield(TlaSet.tammTenaciousTactician, villager, "Island")
        alice.hand(notes)
        alice.library(notes)
        alice.casts(notes)
        resolveAll()
        expect {
            villager.power shouldBe 2
            villager hasKeyword Keyword.Menace
            alice.battlefield has "Ally Token"
        }
    }

    @Test
    fun paleBlossomReinforcementsLord() = scenario {
        alice.battlefield(TlaSet.paleBlossomReinforcements, villager, bear)
        expect {
            villager.power shouldBe 2
            bear.power shouldBe 2
        }
    }

    /** At combat, a permanent given to the opponent: an Ally with a counter for each permanent you own they control. */
    // CR 603.12
    @Test
    fun baldricBrewMasterGivesAway() = scenario {
        alice.battlefield(TlaSet.baldricBrewMaster, trinket)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        targets(trinket, "bob")
        if (pending is Decision.May) yes()
        expect {
            bob.battlefield has trinket
            "Ally Token".counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun baldricBrewMasterFood() = scenario {
        alice.battlefield("Mountain", "Plains", "Plains")
        alice.hand(TlaSet.baldricBrewMaster)
        alice.casts(TlaSet.baldricBrewMaster)
        resolveAll()
        expect { alice.battlefield has "Food Token" }
    }

    @Test
    fun fanbladeWarFanEquipsItsAlly() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(TlaSet.fanbladeWarFan)
        alice.casts(TlaSet.fanbladeWarFan)
        resolveAll()
        expect { "Ally Token".power shouldBe 2 }
    }

    @Test
    fun goldenWyrmTeahouse() = scenario {
        alice.battlefield(TlaSet.goldenWyrmTeahouse, *Array(5) { "Plains" })
        alice.hand(villager)
        alice.activates(TlaSet.goldenWyrmTeahouse, ability = 1, color = Color.White)
        alice.casts(villager)
        resolveAll()
        expect { villager.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun goldenWyrmTeahouseMakesAnAlly() = scenario {
        alice.battlefield(TlaSet.goldenWyrmTeahouse, *Array(5) { "Plains" })
        alice.activates(TlaSet.goldenWyrmTeahouse, ability = 2)
        resolveAll()
        expect { alice.battlefield has "Ally Token" }
    }

    // ---- chunk 12

    /** Two Shrines: two counters on each creature; another Shrine entering adds one more. */
    @Test
    fun southernWindShrine() = scenario {
        alice.battlefield(shrine, bear, *Array(5) { "Plains" })
        alice.hand(TlaSet.southernWindShrine, shrine2)
        alice.casts(TlaSet.southernWindShrine, paying = List(4) { "Plains" })
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 2 }
        alice.casts(shrine2)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 3 }
    }

    @Test
    fun moonlitOasis() = scenario {
        alice.battlefield(shrine, "Island", "Island", "Island", "Plains")
        alice.hand(TlaSet.theMoonlitOasis, shrine2)
        alice.library(notes, notes, notes)
        alice.casts(TlaSet.theMoonlitOasis, paying = listOf("Island", "Island", "Island"))
        resolveAll()
        expect { alice.hand.size shouldBe 3 }
        alice.casts(shrine2)
        resolveAll()
        expect { alice.hand.size shouldBe 3 }
    }

    @Test
    fun northernWindShrine() = scenario {
        alice.battlefield(shrine, "Swamp", "Plains")
        alice.hand(TlaSet.northernWindShrine, shrine2)
        alice.casts(TlaSet.northernWindShrine)
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
        alice.casts(shrine2)
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    @Test
    fun crescentIsleShrine() = scenario {
        alice.battlefield(shrine, *Array(4) { "Mountain" })
        alice.hand(TlaSet.crescentIsleShrine)
        alice.casts(TlaSet.crescentIsleShrine)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Monk Token" } shouldBe 2 }
    }

    @Test
    fun reedIslePlaza() = scenario {
        alice.battlefield(shrine, *Array(4) { "Forest" })
        alice.hand(TlaSet.reedIslePlaza)
        alice.library("Island", "Plains")
        alice.casts(TlaSet.reedIslePlaza)
        resolveAll()
        choose("Island", "Plains")
        expect {
            "Island".tapped shouldBe true
            "Plains".tapped shouldBe true
        }
    }

    @Test
    fun gentleMenderTapped() = scenario {
        alice.battlefield(TlaSet.gentleMender)
        alice.library(notes)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(TlaSet.gentleMender)
        resolveAll()
        choose()
        expect { alice.life shouldBe 21 }
    }

    /** Sacrificed, it destroys an enchantment, and its dies trigger gains 3 life. */
    @Test
    fun inquisitiveBarnyard() = scenario {
        alice.battlefield(TlaSet.inquisitiveBarnyard, "Plains", "Plains")
        bob.battlefield(TlaSet.siegeTactics)
        alice.activates(TlaSet.inquisitiveBarnyard, target = TlaSet.siegeTactics)
        resolveAll()
        expect {
            bob.graveyard has TlaSet.siegeTactics
            alice.life shouldBe 23
        }
    }

    /** Each player keeps creatures with total power 4 or less and sacrifices the rest. */
    // CR 101.4
    @Test
    fun fatedShowdown() = scenario {
        alice.battlefield(bear, ogre, "Plains", "Plains", "Plains", "Plains")
        alice.hand(TlaSet.fatedShowdown)
        bob.battlefield(brute, flier)
        alice.casts(TlaSet.fatedShowdown)
        resolveAll()
        choose(ogre)
        choose(brute)
        expect {
            alice.graveyard has bear
            bob.graveyard has flier
            bob.battlefield has brute
        }
    }

    @Test
    fun stoneRealmJailer() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Mountain")
        alice.hand(TlaSet.stoneRealmJailer, zap)
        bob.battlefield(ogre)
        alice.casts(TlaSet.stoneRealmJailer, paying = listOf("Plains", "Plains", "Plains"))
        resolveAll()
        targets(ogre)
        expect { ogre.zone shouldBe Zone.Exile }
    }

    /** The first flying creature spell costs {1} less; it enters and Pipkin gets +1/+1. */
    @Test
    fun pipkinFriendlyFlier() = scenario {
        alice.battlefield(TlaSet.pipkinFriendlyFlier, "Plains")
        alice.hand(hawk)
        alice.casts(hawk)
        resolveAll()
        expect {
            hawk.zone shouldBe Zone.Battlefield
            TlaSet.pipkinFriendlyFlier.power shouldBe 2
        }
    }

    @Test
    fun pipkinPlayfulPetLeaves() = scenario {
        alice.battlefield(TlaSet.pipkinPlayfulPet, "Mountain")
        alice.hand(zap)
        alice.casts(zap, target = TlaSet.pipkinPlayfulPet)
        resolveAll()
        modes(0)
        expect { alice.battlefield has "Food Token" }
    }

    /** Can't attack or block; {5}, sacrifice it during your turn: the creature exiled and you get an Ally. */
    @Test
    fun roadToAtonement() = scenario {
        alice.battlefield(*Array(7) { "Plains" })
        alice.hand(TlaSet.roadToAtonement)
        bob.battlefield(ogre)
        alice.casts(TlaSet.roadToAtonement, target = ogre, paying = listOf("Plains", "Plains"))
        resolveAll()
        alice.activates(TlaSet.roadToAtonement)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Exile
            alice.battlefield has "Ally Token"
        }
    }

    @Test
    fun hopperooTroopLandfall() = scenario {
        alice.battlefield(TlaSet.hopperooTroop)
        alice.hand("Plains")
        alice.plays("Plains")
        resolveAll()
        expect {
            TlaSet.hopperooTroop hasKeyword Keyword.Flying
            alice.life shouldBe 21
        }
    }

    /** 4 damage to an attacking 2/2: the excess 2 is gained as life. */
    // CR 120.4a
    @Test
    fun edgedHoopsExcessLife() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(TlaSet.edgedHoops)
        alice.library(notes)
        bob.battlefield(bear)
        bob.library(notes, notes)
        turn(active = bob, step = TurnStep.DeclareAttackers)
        bob.attacks(bear)
        bob.passes()
        alice.casts(TlaSet.edgedHoops, target = bear)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            alice.life shouldBe 22
        }
    }

    @Test
    fun heraldsBandAttackAlone() = scenario {
        alice.battlefield(TlaSet.heraldsBand, bear, ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        expect { bear.power shouldBe 4 }
    }

    /** Discarded from hand: damage equal to your creature count. */
    @Test
    fun heraldsBandFromHand() = scenario {
        alice.battlefield(bear, ogre, "Plains", "Plains", "Plains")
        alice.hand(TlaSet.heraldsBand)
        bob.battlefield(TlaExtras.flier)
        alice.activates(TlaSet.heraldsBand, target = TlaExtras.flier)
        resolveAll()
        expect { TlaExtras.flier.zone shouldBe Zone.Graveyard }
    }

    /** It taps the opponent's creature; sacrificing an artifact puts a stun counter on it. */
    @Test
    fun wrathfulTownsfolk() = scenario {
        alice.battlefield(TlaSet.wrathfulTownsfolk, trinket)
        bob.battlefield(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(TlaSet.wrathfulTownsfolk)
        resolveAll()
        targets(ogre)
        if (pending is Decision.May) yes()
        choose(trinket)
        expect {
            ogre.tapped shouldBe true
            ogre.counters(CounterKind.Stun) shouldBe 1
        }
    }
}
