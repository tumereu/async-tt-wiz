package arcanum.engine.sets.eoe

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.eoe.EoeExtras.bear
import arcanum.engine.sets.eoe.EoeExtras.charge
import arcanum.engine.sets.eoe.EoeExtras.doom
import arcanum.engine.sets.eoe.EoeExtras.flier
import arcanum.engine.sets.eoe.EoeExtras.giant
import arcanum.engine.sets.eoe.EoeExtras.gizmo
import arcanum.engine.sets.eoe.EoeExtras.notes
import arcanum.engine.sets.eoe.EoeExtras.ogre
import arcanum.engine.sets.eoe.EoeExtras.p1p1
import arcanum.engine.sets.eoe.EoeExtras.shuttle
import arcanum.engine.sets.eoe.EoeExtras.trinket
import arcanum.engine.sets.eoe.EoeExtras.zap
import kotlin.test.Test

/**
 * Edge of Eternities chunks 11 and 12 in play: the black and red cards. Gravcrush, Welcome the Abyss's destruction, Cast Out by Ballot
 * (convoke is covered), Barrage Shot, Stardust Wyrm's 3 damage and Outfit for Battle use covered effects; Gravjet Zealot's tapped Robot
 * and Menagerie Automaton's pump repeat tested templates; Vanguard Battle-Brute repeats Daybreak Spearhead's tapped-creatures end step;
 * Hullgouger and Zealot Picket are keywords only.
 */
class EoeBlackRedScenariosTest {
    private fun Scenario.libraries() {
        alice.library(notes, notes, notes, notes, notes)
        bob.library(notes, notes, notes, notes, notes)
    }

    // ---- chunk 11

    @Test
    fun nemesissCharmReturnsTwo() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(EoeSet.nemesissCharm)
        alice.graveyard(bear, ogre)
        alice.casts(EoeSet.nemesissCharm, modes = listOf(1), targets = listOf(bear, ogre))
        resolveAll()
        expect {
            alice.hand has bear
            alice.hand has ogre
        }
    }

    @Test
    fun nemesissCharmCountersAndLifelink() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", bear)
        alice.hand(EoeSet.nemesissCharm)
        alice.casts(EoeSet.nemesissCharm, modes = listOf(2), target = bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 2
            bear hasKeyword Keyword.Lifelink
        }
    }

    @Test
    fun meteorCreeperSacrificesForPower() = scenario {
        libraries()
        alice.battlefield(EoeSet.meteorCreeper, trinket)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(EoeSet.meteorCreeper)
        resolveAll()
        yes()
        choose(trinket)
        expect {
            EoeSet.meteorCreeper.power shouldBe 4
            trinket.zone shouldBe Zone.Graveyard
        }
    }

    /** Costs {1} less if it targets a blocking creature. */
    // CR 601.2f
    @Test
    fun shadowedFortitudeOnABlocker() = scenario {
        libraries()
        alice.battlefield("Swamp", bear)
        alice.hand(EoeSet.shadowedFortitude)
        bob.battlefield(ogre)
        turn(active = bob, step = TurnStep.DeclareAttackers)
        bob.attacks(ogre)
        allPass()
        alice.blocks(bear to ogre)
        bob.passes()
        alice.casts(EoeSet.shadowedFortitude, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Indestructible
        }
    }

    @Test
    fun ventTheAirlockDestroysAtZeroPower() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(EoeSet.ventTheAirlock, EoeSet.ventTheAirlock)
        bob.battlefield(ogre, giant)
        alice.casts(EoeSet.ventTheAirlock, target = ogre)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun ventTheAirlockSparesTheStrong() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(EoeSet.ventTheAirlock)
        bob.battlefield(giant)
        alice.casts(EoeSet.ventTheAirlock, target = giant)
        resolveAll()
        expect {
            giant.zone shouldBe Zone.Battlefield
            giant.power shouldBe 2
        }
    }

    @Test
    fun questionableMorselShrinksAndDrains() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(EoeSet.questionableMorsel)
        bob.battlefield(ogre)
        alice.casts(EoeSet.questionableMorsel)
        resolveAll()
        targets(ogre)
        expect { ogre.zone shouldBe Zone.Graveyard }
        alice.activates(EoeSet.questionableMorsel, ability = 1, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    @Test
    fun welcomeTheAbyssSacrificesAndDestroys() = scenario {
        alice.battlefield("Swamp", trinket)
        alice.hand(EoeSet.welcomeTheAbyss)
        bob.battlefield(shuttle)
        alice.casts(EoeSet.welcomeTheAbyss, target = shuttle, choosing = listOf(trinket))
        resolveAll()
        expect {
            shuttle.zone shouldBe Zone.Graveyard
            trinket.zone shouldBe Zone.Graveyard
        }
    }

    /** Destroys a creature; its controller draws two only if it wasn't dealt damage this turn. */
    @Test
    fun fallenStarsDevoteeCompensates() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(EoeSet.fallenStarsDevotee)
        bob.battlefield(giant)
        bob.library(notes, notes)
        alice.casts(EoeSet.fallenStarsDevotee)
        resolveAll()
        targets(giant)
        expect {
            giant.zone shouldBe Zone.Graveyard
            bob.hand.size shouldBe 2
        }
    }

    @Test
    fun fallenStarsDevoteeNoCardsAfterDamage() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Mountain")
        alice.hand(zap, EoeSet.fallenStarsDevotee)
        bob.battlefield(giant)
        bob.library(notes, notes)
        alice.casts(zap, target = giant)
        resolveAll()
        alice.casts(EoeSet.fallenStarsDevotee)
        resolveAll()
        targets(giant)
        expect { bob.hand.size shouldBe 0 }
    }

    @Test
    fun gravedgeBruiserWithAnArtifact() = scenario {
        alice.battlefield(EoeSet.gravedgeBruiser, trinket)
        expect {
            EoeSet.gravedgeBruiser.power shouldBe 4
            EoeSet.gravedgeBruiser hasKeyword Keyword.Deathtouch
        }
    }

    @Test
    fun lamplessPreacherGrowsOnSacrifice() = scenario {
        alice.battlefield(EoeSet.lamplessPreacher, EoeSet.shadowcollarFanatic, bear)
        alice.library(notes)
        alice.activates(EoeSet.shadowcollarFanatic, choosing = listOf(bear))
        resolveAll()
        choose()
        expect { EoeSet.lamplessPreacher.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun zealotCircuitLeechByArtifacts() = scenario {
        alice.battlefield(*Array(6) { "Swamp" }, bear, trinket)
        alice.hand(EoeSet.zealotCircuitLeech)
        bob.battlefield(ogre)
        alice.casts(EoeSet.zealotCircuitLeech)
        resolveAll()
        targets(bear, ogre)
        expect {
            bear.power shouldBe 4
            ogre.toughness shouldBe 1
        }
    }

    /** The creature gains "Whenever this creature is dealt damage, you draw that many cards and lose that much life"; its controller may ping it. */
    @Test
    fun dirgeObeliskDrawsOnDamage() = scenario {
        alice.battlefield(EoeSet.dirgeObelisk, giant)
        alice.library(notes, notes)
        alice.activates(EoeSet.dirgeObelisk, target = giant)
        resolveAll()
        yes()
        expect {
            alice.hand.size shouldBe 1
            alice.life shouldBe 19
        }
    }

    /** A creature of yours dies: each opponent exiles a creature; the end step returns one with two counters if a player has none. */
    @Test
    fun nerezzaSwapsCreatures() = scenario {
        libraries()
        alice.battlefield(EoeSet.nerezzaTheGreatHollow, bear, "Swamp")
        alice.hand(doom)
        bob.battlefield(giant)
        alice.casts(doom, target = bear)
        resolveAll()
        expect { giant.zone shouldBe Zone.Exile }
        advanceTo(TurnStep.End)
        resolveAll()
        choose(giant)
        expect {
            giant.zone shouldBe Zone.Battlefield
            giant.controller shouldBe alice.id
            giant.counters(p1p1) shouldBe 2
            EoeSet.nerezzaTheGreatHollow.zone shouldBe Zone.Graveyard
        }
    }

    @Test
    fun duskWreckerGivesACounter() = scenario {
        libraries()
        alice.battlefield(EoeSet.duskWrecker)
        bob.battlefield(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(EoeSet.duskWrecker)
        resolveAll()
        targets(bear)
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun hiveThinnerDrawsOnSacrifice() = scenario {
        libraries()
        alice.battlefield(EoeSet.hiveThinner, trinket)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(EoeSet.hiveThinner)
        resolveAll()
        yes()
        choose(trinket)
        expect { alice.hand.size shouldBe 1 }
    }

    @Test
    fun zhuRavelReturnsAVanillaSkeleton() = scenario {
        alice.battlefield(EoeSet.zhuRavelBonechorister)
        alice.graveyard(flier)
        alice.activates(EoeSet.zhuRavelBonechorister, target = flier)
        resolveAll()
        expect {
            flier.zone shouldBe Zone.Battlefield
            flier lacksKeyword Keyword.Flying
            assert(arcanum.api.Subtype("Skeleton") in flier.subtypes)
        }
    }

    /** X 6 or more: a creature card destroyed this way returns under your control. */
    @Test
    fun nullPointRequiemBringsOneBack() = scenario {
        alice.battlefield(*Array(7) { "Swamp" })
        alice.hand(EoeSet.nullPointRequiem)
        bob.battlefield(giant, bear)
        alice.casts(EoeSet.nullPointRequiem, x = 6)
        resolveAll()
        choose(giant)
        expect {
            giant.zone shouldBe Zone.Battlefield
            giant.controller shouldBe alice.id
            bear.zone shouldBe Zone.Graveyard
            alice.life shouldBe 14
        }
    }

    @Test
    fun nullPointRequiemSmallX() = scenario {
        alice.battlefield(*Array(3) { "Swamp" })
        alice.hand(EoeSet.nullPointRequiem)
        bob.battlefield(giant, bear)
        alice.casts(EoeSet.nullPointRequiem, x = 2)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            giant.zone shouldBe Zone.Battlefield
            alice.life shouldBe 18
        }
    }

    @Test
    fun plagueScarabMakesEachOpponentDiscard() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(EoeSet.plagueScarab)
        bob.hand(giant)
        alice.casts(EoeSet.plagueScarab)
        resolveAll()
        choose(giant)
        expect { bob.graveyard has giant }
    }

    // ---- chunk 12

    @Test
    fun severThrustersDoubleOnAFlier() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(EoeSet.severThrusters)
        bob.battlefield(EoeSet.riftsurgeLeviathan)
        alice.casts(EoeSet.severThrusters, target = EoeSet.riftsurgeLeviathan)
        resolveAll()
        expect { EoeSet.riftsurgeLeviathan.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun ruinousOffensiveCopiesWithHaste() = scenario {
        libraries()
        alice.battlefield(*Array(5) { "Mountain" }, bear)
        alice.hand(EoeSet.ruinousOffensive)
        alice.casts(EoeSet.ruinousOffensive, x = 2, target = bear)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == bear } shouldBe 3
            card(bear, 1) hasKeyword Keyword.Haste
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield.names.count { it == bear } shouldBe 1 }
    }

    @Test
    fun borePastTheCoreChargesASpacecraft() = scenario {
        alice.battlefield("Mountain", "Mountain", shuttle)
        alice.hand(EoeSet.borePastTheCore)
        alice.casts(EoeSet.borePastTheCore, modes = listOf(0), target = shuttle)
        resolveAll()
        expect {
            shuttle.counters(charge) shouldBe 5
            shuttle.power shouldBe 2
        }
    }

    @Test
    fun incursionTacticsWithASpacecraft() = scenario {
        alice.battlefield("Mountain", "Mountain", shuttle)
        alice.hand(EoeSet.incursionTactics)
        bob.battlefield(giant)
        alice.casts(EoeSet.incursionTactics, target = giant)
        resolveAll()
        expect { giant.zone shouldBe Zone.Graveyard }
    }

    /** Pay {2}: a Robot tapped and attacking, sacrificed at end of combat. */
    // CR 508.4
    @Test
    fun pyrrhosRaiderBringsARobot() = scenario {
        libraries()
        alice.battlefield(EoeSet.pyrrhosRaider, "Mountain", "Mountain")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(EoeSet.pyrrhosRaider)
        resolveAll()
        yes()
        pay()
        resolveAll()
        expect { alice.battlefield has "Robot Token" }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.battlefield lacks "Robot Token"
            bob.life shouldBe 16
        }
    }

    @Test
    fun pyrrhosBoostdroneGivesHaste() = scenario {
        alice.battlefield(EoeSet.pyrrhosBoostdrone, bear)
        alice.activates(EoeSet.pyrrhosBoostdrone, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Haste
        }
    }

    @Test
    fun fusedMoxstoneRummages() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(EoeSet.fusedMoxstone, giant)
        alice.library(notes, notes)
        alice.casts(EoeSet.fusedMoxstone)
        resolveAll()
        yes()
        choose(giant)
        expect { alice.hand.size shouldBe 2 }
    }

    /** Exiles one plus the sacrificed artifact's mana value; they may be played this turn. */
    @Test
    fun remembranceStrongroomExilesByManaValue() = scenario {
        alice.battlefield(EoeSet.remembranceStrongroom, trinket, "Mountain")
        alice.library(zap, notes, notes)
        alice.activates(EoeSet.remembranceStrongroom, choosing = listOf(trinket))
        resolveAll()
        expect {
            zap.zone shouldBe Zone.Exile
            alice.library.size shouldBe 1
        }
        alice.casts(zap, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun atomicTinkererFirstStrike() = scenario {
        libraries()
        alice.battlefield(EoeSet.atomicTinkerer, bear)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        targets(bear)
        expect { bear hasKeyword Keyword.FirstStrike }
    }

    @Test
    fun orehideArmadilloPaysForCounters() = scenario {
        alice.battlefield(EoeSet.orehideArmadillo, "Mountain", "Mountain")
        alice.hand(trinket)
        alice.casts(trinket)
        resolveAll()
        yes()
        pay()
        expect { EoeSet.orehideArmadillo.counters(p1p1) shouldBe 1 }
    }

    /** Enters: the enchanted creature deals its power to another target; damage to it is passed to each opponent. */
    @Test
    fun sufferingSharedRedirectsPain() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", giant)
        alice.hand(EoeSet.sufferingShared)
        bob.battlefield("Mountain")
        bob.hand(zap)
        alice.casts(EoeSet.sufferingShared, target = giant)
        resolveAll()
        targets("bob")
        expect { bob.life shouldBe 15 }
        alice.passes()
        bob.casts(zap, target = giant)
        resolveAll()
        expect { bob.life shouldBe 13 }
    }

    @Test
    fun calamitousStampedeExilesCheapArtifacts() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(EoeSet.calamitousStampede)
        bob.battlefield(trinket, gizmo)
        alice.casts(EoeSet.calamitousStampede, modes = listOf(1))
        resolveAll()
        expect {
            trinket.zone shouldBe Zone.Exile
            gizmo.zone shouldBe Zone.Exile
        }
    }

    @Test
    fun corrosionReaperGrowsAndShoots() = scenario {
        alice.battlefield(EoeSet.corrosionReaper, "Mountain", "Mountain")
        alice.graveyard(trinket)
        alice.activates(EoeSet.corrosionReaper, target = "bob", choosing = listOf(trinket))
        resolveAll()
        expect {
            EoeSet.corrosionReaper.counters(p1p1) shouldBe 1
            bob.life shouldBe 18
            trinket.zone shouldBe Zone.Exile
        }
    }

    @Test
    fun cinderboreSalvagerDraws() = scenario {
        alice.battlefield(EoeSet.cinderboreSalvager, "Mountain", "Mountain", trinket)
        alice.library(notes)
        alice.activates(EoeSet.cinderboreSalvager, choosing = listOf(trinket))
        resolveAll()
        expect { alice.hand has notes }
    }

    /** Steals a Spacecraft with ten charge counters until end of turn; the counters come off at the end step. */
    @Test
    fun controlHijackFlightsASpacecraft() = scenario {
        libraries()
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(EoeSet.controlHijack)
        bob.battlefield(shuttle)
        alice.casts(EoeSet.controlHijack, target = shuttle)
        resolveAll()
        expect {
            shuttle.controller shouldBe alice.id
            shuttle.power shouldBe 2
            shuttle hasKeyword Keyword.Haste
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { shuttle.counters(charge) shouldBe 0 }
    }

    /** Puts a creature from hand onto the battlefield with haste and two granted triggers. */
    @Test
    fun finalPlummetDeploysAndBlows() = scenario {
        libraries()
        alice.battlefield(*Array(6) { "Mountain" })
        alice.hand(EoeSet.finalPlummet, ogre)
        bob.battlefield(bear)
        alice.casts(EoeSet.finalPlummet)
        resolveAll()
        choose(ogre)
        expect { ogre hasKeyword Keyword.Haste }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Graveyard
            bear.zone shouldBe Zone.Graveyard
        }
    }

    @Test
    fun turfGuardingHornbackCastsTheExiled() = scenario {
        alice.battlefield(EoeSet.turfGuardingHornback, "Mountain")
        alice.hand("Forest")
        alice.library("Island", zap)
        alice.plays("Forest")
        resolveAll()
        expect { zap.zone shouldBe Zone.Exile }
        alice.casts(zap, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun strongroomSentinelDiscardsForTwo() = scenario {
        libraries()
        alice.battlefield(EoeSet.strongroomSentinel, bear, ogre)
        alice.hand(giant, flier)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear, ogre)
        advanceTo(TurnStep.End)
        resolveAll()
        yes()
        expect {
            alice.graveyard has giant
            alice.hand.size shouldBe 2
        }
    }

    @Test
    fun armamentAssemblyMunitions() = scenario {
        alice.battlefield(EoeSet.armamentAssembly, "Mountain")
        alice.hand(trinket)
        alice.casts(trinket)
        resolveAll()
        expect { alice.battlefield has "Munitions" }
    }
}
