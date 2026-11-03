package arcanum.engine.sets.tla

import arcanum.api.Color
import arcanum.api.Keyword
import arcanum.api.Mechanic
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
 * Avatar: The Last Airbender chunks 17 and 18 cards in play: the multicoloured creatures and enchantments, the artifacts and the lands.
 * Feigning Plaguebearers (an Ally token as it dies) repeats a tested template; the dual lands that enter tapped and sacrifice for a card
 * are one template, played with Airship Boiler Room (Scalding Pit Prison, Misty Hollow Bog, Reed Isle Village, Contemplation Pools,
 * Hazy Palms Oasis, Northern Ice Gates, Cliffside Capital, Wyrm's Strait and Sun-Kissed Summit are repeats).
 */
class TlaMulticoloredScenariosTest {
    private val looter = Cards.invent("Card Sage", "{U}", "Instant", "Draw two cards.")
    private val wolf = Cards.invent("Grey Wolf", "{1}{G}", "Creature — Wolf", "", "2/2")
    private val wolf2 = Cards.invent("Pack Wolf", "{1}{G}", "Creature — Wolf", "", "2/2")

    // ---- chunk 17

    @Test
    fun windWandererLegacy() = scenario {
        alice.battlefield(flier, bear, "Plains", "Island")
        alice.hand(TlaSet.windWandererLegacy)
        alice.casts(TlaSet.windWandererLegacy)
        resolveAll()
        expect {
            flier.power shouldBe 2
            bear.power shouldBe 2
            alice.battlefield has "Clue Token"
        }
    }

    @Test
    fun lynxHooterUntaps() = scenario {
        alice.battlefield(TlaSet.lynxHooter)
        alice.battlefield(bear, tapped = true)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(TlaSet.lynxHooter)
        resolveAll()
        targets(bear)
        expect { bear.tapped shouldBe false }
    }

    @Test
    fun stoneRealmSoldier() = scenario {
        alice.battlefield(bear, ogre, *Array(5) { "Forest" })
        alice.hand(TlaSet.stoneRealmSoldier)
        alice.casts(TlaSet.stoneRealmSoldier)
        resolveAll()
        targets(bear, ogre)
        expect {
            bear.counters(p1p1) shouldBe 1
            ogre.counters(p1p1) shouldBe 1
        }
    }

    /** A land entering this turn: +1/+0 and trample. */
    @Test
    fun stoneBrawlGrapplers() = scenario {
        alice.battlefield(TlaSet.stoneBrawlGrapplers)
        alice.hand("Forest")
        expect { TlaSet.stoneBrawlGrapplers lacksKeyword Keyword.Trample }
        alice.plays("Forest")
        expect {
            TlaSet.stoneBrawlGrapplers.power shouldBe 4
            TlaSet.stoneBrawlGrapplers hasKeyword Keyword.Trample
        }
    }

    @Test
    fun mistyBogSpiritWarden() = scenario {
        alice.battlefield(TlaSet.mistyBogSpiritWarden, "Island")
        alice.hand(looter)
        alice.library(notes, notes)
        alice.casts(looter)
        resolveAll()
        expect { alice.battlefield has "Spirit Token" }
    }

    /** Sacrificing an artifact as it enters: two counters; leaving, its counters move to another creature. */
    @Test
    fun moroLun() = scenario {
        alice.battlefield(bear, trinket, *Array(4) { "Plains" }, "Island")
        alice.hand(TlaSet.moroLunSpiritOfBalance, TlaSet.returningBladeBasics)
        alice.library(notes)
        alice.casts(TlaSet.moroLunSpiritOfBalance, paying = List(4) { "Plains" })
        resolveAll()
        yes()
        choose(trinket)
        expect { TlaSet.moroLunSpiritOfBalance.counters(p1p1) shouldBe 2 }
        alice.casts(TlaSet.returningBladeBasics, target = TlaSet.moroLunSpiritOfBalance)
        resolveAll()
        targets(bear)
        expect { bear.counters(p1p1) shouldBe 2 }
    }

    /** Damage equal to the creature count; dying, counters on two creatures. */
    @Test
    fun rookLibertyFighter() = scenario {
        alice.battlefield(bear, *Array(5) { "Mountain" }, "Mountain")
        alice.hand(TlaSet.rookLibertyFighter, zap)
        bob.battlefield(TlaExtras.flier)
        alice.casts(TlaSet.rookLibertyFighter, paying = List(5) { "Mountain" })
        resolveAll()
        targets(TlaExtras.flier)
        expect { TlaExtras.flier.zone shouldBe Zone.Graveyard }
        alice.casts(zap, target = TlaSet.rookLibertyFighter)
        resolveAll()
        targets(bear)
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun vosskGrandChancellor() = scenario {
        alice.battlefield(TlaSet.vosskGrandChancellor, bear, "Mountain")
        alice.hand(zap)
        alice.casts(zap, target = bear)
        resolveAll()
        targets(TlaSet.vosskGrandChancellor)
        expect { TlaSet.vosskGrandChancellor.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun courierFalcon() = scenario {
        alice.battlefield(TlaSet.courierFalcon, "Island")
        alice.hand(looter)
        alice.library(notes, notes)
        alice.casts(looter)
        resolveAll()
        expect { TlaSet.courierFalcon.power shouldBe 3 }
    }

    /** It loots; sacrificed during your turn, an instant comes back to hand. */
    @Test
    fun scholarQuill() = scenario {
        alice.battlefield(TlaSet.scholarQuillAntiquarian, "Island")
        alice.graveyard(zap)
        alice.activates(TlaSet.scholarQuillAntiquarian, ability = 1, target = zap)
        resolveAll()
        expect { alice.hand has zap }
    }

    @Test
    fun scholarQuillLoots() = scenario {
        alice.battlefield(TlaSet.scholarQuillAntiquarian)
        alice.hand(zap)
        alice.library(notes)
        alice.activates(TlaSet.scholarQuillAntiquarian, choosing = listOf(zap))
        resolveAll()
        expect { alice.hand has notes }
    }

    /** A sacrifice grows it; dying with power 2, it may exile itself to return a creature with mana value 2 or less. */
    // CR 603.12
    @Test
    fun dunecallerScavengers() = scenario {
        alice.battlefield(TlaSet.dunecallerScavengers, trinket, "Mountain")
        alice.graveyard(bear)
        alice.hand(zap)
        effect(alice, Mechanic.ModifyPowerToughness(arcanum.api.Value.Fixed(1), arcanum.api.Value.Fixed(0)), on = listOf(TlaSet.dunecallerScavengers))
        alice.casts(zap, target = TlaSet.dunecallerScavengers)
        resolveAll()
        yes()
        targets(bear)
        expect {
            bear.zone shouldBe Zone.Battlefield
            alice.exile has TlaSet.dunecallerScavengers
        }
    }

    /** It loots as it enters; an artifact spell puts a counter on it. */
    @Test
    fun tammBoldBladeThrower() = scenario {
        alice.battlefield("Island", "Mountain", "Island")
        alice.hand(TlaSet.tammBoldBladeThrower, zap, trinket)
        alice.library(notes, notes)
        alice.casts(TlaSet.tammBoldBladeThrower, paying = listOf("Island", "Mountain"))
        resolveAll()
        choose(zap)
        expect { alice.hand has notes }
        alice.casts(trinket)
        resolveAll()
        expect { TlaSet.tammBoldBladeThrower.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun tammLateralStrategist() = scenario {
        alice.battlefield(TlaSet.tammLateralStrategist, bear)
        alice.library(notes)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(TlaSet.tammLateralStrategist, bear)
        resolveAll()
        expect { alice.hand has notes }
    }

    /** Power is the creature count; attacking, a tapped and attacking Ally joins. */
    @Test
    fun yarraFanbladeWarrior() = scenario {
        alice.battlefield(TlaSet.yarraFanbladeWarrior, bear)
        expect { TlaSet.yarraFanbladeWarrior.power shouldBe 2 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(TlaSet.yarraFanbladeWarrior)
        resolveAll()
        advanceTo(TurnStep.CombatDamage)
        expect { bob.life shouldBe 16 }
    }

    /** A Clue as it enters; sacrificing it during your turn makes one Ally. */
    @Test
    fun costsOfConflict() = scenario {
        alice.battlefield("Plains", "Swamp", "Island", "Island")
        alice.hand(TlaSet.costsOfConflict)
        alice.library(notes)
        alice.casts(TlaSet.costsOfConflict, paying = listOf("Plains", "Swamp"))
        resolveAll()
        alice.activates("Clue Token")
        resolveAll()
        expect {
            alice.battlefield has "Ally Token"
            alice.hand has notes
        }
    }

    @Test
    fun roamingMinstrels() = scenario {
        alice.battlefield(TlaSet.roamingMinstrels, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(TlaSet.roamingMinstrels)
        resolveAll()
        expect { bear.power shouldBe 3 }
    }

    /** First main phase: a mode not chosen before, and 2 life. */
    @Test
    fun darrowTorn() = scenario {
        alice.battlefield(TlaSet.darrowTorn)
        alice.library(notes, notes, notes)
        bob.library(notes, notes)
        turn(step = TurnStep.Upkeep)
        advanceTo(TurnStep.PrecombatMain)
        resolveAll()
        modes(1)
        expect {
            TlaSet.darrowTorn.counters(p1p1) shouldBe 1
            alice.life shouldBe 18
        }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        resolveAll()
        val d = decision<Decision.Modes>()
        expect { d.options.first { it.index == 1 }.available shouldBe false }
    }

    // ---- chunk 18

    /** Mana once each turn; {5}, sacrifice: 5 damage. */
    @Test
    fun casksOfVolatileJelly() = scenario {
        alice.battlefield(TlaSet.casksOfVolatileJelly, *Array(5) { "Mountain" })
        bob.battlefield(ogre)
        alice.activates(TlaSet.casksOfVolatileJelly, ability = 1, target = ogre)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Graveyard }
    }

    /** It untaps during the opponent's untap step too. */
    @Test
    fun shapersWaterFlask() = scenario {
        alice.battlefield(TlaSet.shapersWaterFlask)
        alice.library(notes)
        bob.library(notes)
        alice.activates(TlaSet.shapersWaterFlask, color = Color.Blue)
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { TlaSet.shapersWaterFlask.tapped shouldBe false }
    }

    @Test
    fun emberLegionWarshipDies() = scenario {
        alice.battlefield(TlaSet.emberLegionWarship, "Forest", "Forest")
        alice.hand(TlaSet.dawnOfIronshaping)
        alice.casts(TlaSet.dawnOfIronshaping, modes = listOf(0), target = TlaSet.emberLegionWarship)
        resolveAll()
        expect { alice.battlefield has "Clue Token" }
    }

    @Test
    fun starfallSaber() = scenario {
        alice.battlefield(bear, *Array(10) { "Plains" })
        alice.hand(TlaSet.starfallSaber)
        bob.battlefield(ogre)
        alice.casts(TlaSet.starfallSaber, paying = List(7) { "Plains" })
        resolveAll()
        targets(ogre)
        alice.activates(TlaSet.starfallSaber, target = bear)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Graveyard
            bear.power shouldBe 5
        }
    }

    /** Scrying: the top card may then be cast without paying, once each turn. */
    @Test
    fun orreryOfOrvath() = scenario {
        alice.battlefield(TlaSet.orreryOfOrvath, "Island")
        alice.library(brute, notes, notes)
        alice.activates(TlaSet.orreryOfOrvath)
        resolveAll()
        choose()
        choose(brute, notes)
        yes()
        expect { brute.zone shouldBe Zone.Battlefield }
    }

    /** The equipped creature taps a creature and sends the Blade back to hand. */
    @Test
    fun faithfulReturningBlade() = scenario {
        alice.battlefield(TlaSet.faithfulReturningBlade, bear, "Plains", "Plains")
        bob.battlefield(ogre)
        alice.activates(TlaSet.faithfulReturningBlade, target = bear)
        resolveAll()
        alice.activates(bear, target = ogre)
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            alice.hand has TlaSet.faithfulReturningBlade
        }
    }

    @Test
    fun wallsOfStoneholm() = scenario {
        alice.battlefield(TlaSet.theWallsOfStoneholm, bear)
        expect {
            bear hasKeyword Keyword.Indestructible
            TlaSet.theWallsOfStoneholm lacksKeyword Keyword.Indestructible
        }
    }

    /** X is the most creatures sharing a creature type. */
    @Test
    fun paleBlossomTile() = scenario {
        alice.battlefield(TlaSet.paleBlossomTile, wolf, wolf2, bear)
        alice.activates(TlaSet.paleBlossomTile, color = Color.Green)
        expect { alice.pool.size shouldBe 2 }
    }

    @Test
    fun forsakenWindShrine() = scenario {
        alice.battlefield(TlaSet.forsakenWindShrine, bear, "Plains", "Plains", "Plains", "Plains")
        alice.activates(TlaSet.forsakenWindShrine, ability = 1)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun frostwallCitadel() = scenario {
        alice.battlefield(TlaSet.frostwallCitadel, "Island", "Island", "Island")
        alice.hand(zap)
        alice.library(notes)
        alice.activates(TlaSet.frostwallCitadel, ability = 1)
        resolveAll()
        choose(zap)
        expect { alice.hand has notes }
    }

    /** Enters tapped; {4}, {T}, sacrifice: a card. */
    @Test
    fun airshipBoilerRoom() = scenario {
        alice.battlefield(TlaSet.airshipBoilerRoom, *Array(4) { "Island" })
        alice.hand(TlaSet.scaldingPitPrison)
        alice.library(notes)
        alice.plays(TlaSet.scaldingPitPrison)
        expect { TlaSet.scaldingPitPrison.tapped shouldBe true }
        alice.activates(TlaSet.airshipBoilerRoom, ability = 1)
        resolveAll()
        expect {
            alice.hand has notes
            alice.graveyard has TlaSet.airshipBoilerRoom
        }
    }

    @Test
    fun domainOfVyrr() = scenario {
        alice.battlefield(TlaSet.domainOfVyrr, "Swamp", "Swamp", "Swamp", "Swamp")
        alice.activates(TlaSet.domainOfVyrr, ability = 1)
        resolveAll()
        expect { alice.battlefield has "Spirit Token" }
    }

    @Test
    fun brawlArena() = scenario {
        alice.battlefield("Island")
        alice.hand(TlaSet.brawlArena)
        alice.library(notes)
        alice.plays(TlaSet.brawlArena)
        resolveAll()
        choose()
        alice.activates(TlaSet.brawlArena, ability = 1, color = Color.Green)
        expect { alice.pool.single().color shouldBe Color.Green }
    }

    /** Two Wolves can't be blocked. */
    @Test
    fun hiddenPassage() = scenario {
        alice.battlefield(TlaSet.hiddenPassage, wolf, wolf2, "Forest", "Forest", "Forest", "Forest")
        alice.activates(TlaSet.hiddenPassage, ability = 1, targets = listOf(wolf, wolf2))
        resolveAll()
        expect {
            wolf hasMechanic { it == Mechanic.CantBeBlocked }
            wolf2 hasMechanic { it == Mechanic.CantBeBlocked }
        }
    }
}
