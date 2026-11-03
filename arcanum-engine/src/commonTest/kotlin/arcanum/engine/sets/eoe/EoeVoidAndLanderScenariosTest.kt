package arcanum.engine.sets.eoe

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.eoe.EoeExtras.bear
import arcanum.engine.sets.eoe.EoeExtras.charm
import arcanum.engine.sets.eoe.EoeExtras.doom
import arcanum.engine.sets.eoe.EoeExtras.giant
import arcanum.engine.sets.eoe.EoeExtras.gizmo
import arcanum.engine.sets.eoe.EoeExtras.notes
import arcanum.engine.sets.eoe.EoeExtras.ogre
import arcanum.engine.sets.eoe.EoeExtras.p1p1
import arcanum.engine.sets.eoe.EoeExtras.trinket
import arcanum.engine.sets.eoe.EoeExtras.warper
import arcanum.engine.sets.eoe.EoeExtras.zap
import kotlin.test.Test

/**
 * Edge of Eternities chunks 05 and 06 in play: void (a nonland permanent left the battlefield this turn or a spell was warped this turn)
 * and Lander tokens (CR 111.10u). Ravenous Scuttlejaw, Pyrrhos Cloudsentry and Interdiction Automaton's end-step counters repeat
 * [nullwroughtColossusVoidEndStep]'s template; the "When this creature enters/dies, create a Lander token" cards (Lasercut Surveyor,
 * Bioforge Machinist, Gene-Lab Expert, Starlane Voyager, Icefield Worldmaw) repeat [EoeMechanicsScenariosTest.landerFetchesABasicLand]'s
 * token with their own triggers tested where they differ.
 */
class EoeVoidAndLanderScenariosTest {
    private fun Scenario.libraries() {
        alice.library(notes, notes, notes, notes, notes)
        bob.library(notes, notes, notes, notes, notes)
    }

    /** Kills a creature this turn so void holds. */
    private fun Scenario.voidByDeath() {
        alice.casts(doom, target = bear)
        resolveAll()
    }

    // ---- chunk 05

    @Test
    fun velunirHalvesTheDefendersLife() = scenario {
        libraries()
        alice.battlefield(EoeSet.velunirRockchosen, "Swamp")
        alice.hand(doom)
        bob.battlefield(bear)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        voidByDeath()
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(EoeSet.velunirRockchosen)
        resolveAll()
        expect { bob.life shouldBe 10 }
    }

    /** Ward—discard a card at random: the opponent's spell is countered unless they discard one. */
    // CR 702.21a
    @Test
    fun velunirWardDiscardsAtRandom() = scenario {
        alice.battlefield(EoeSet.velunirRockchosen)
        bob.battlefield("Swamp")
        bob.hand(doom, notes)
        turn(active = bob)
        bob.casts(doom, target = EoeSet.velunirRockchosen)
        resolveAll()
        yes()
        resolveAll()
        expect {
            bob.hand.size shouldBe 0
            EoeSet.velunirRockchosen.zone shouldBe Zone.Graveyard
        }
    }

    /** Reanimates a creature card from the defending player's graveyard tapped and attacking. */
    // CR 508.4
    @Test
    fun hymnalOfTheAbyssStealsTheFallen() = scenario {
        libraries()
        alice.battlefield(EoeSet.hymnalOfTheAbyss, bear)
        attach(EoeSet.hymnalOfTheAbyss, bear)
        bob.graveyard(giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        targets(giant)
        expect {
            giant.zone shouldBe Zone.Battlefield
            giant.controller shouldBe alice.id
            giant.tapped shouldBe true
        }
    }

    @Test
    fun hymnalOfTheAbyssSacrificedWithoutVoid() = scenario {
        libraries()
        alice.battlefield(EoeSet.hymnalOfTheAbyss, bear)
        attach(EoeSet.hymnalOfTheAbyss, bear)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { EoeSet.hymnalOfTheAbyss.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun decipherSignalsWithAndWithoutVoid() = scenario {
        libraries()
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(EoeSet.decipherSignals, EoeSet.decipherSignals, doom)
        bob.battlefield(bear)
        alice.casts(EoeSet.decipherSignals)
        resolveAll()
        expect { alice.life shouldBe 18 }
        voidByDeath()
        alice.casts(EoeSet.decipherSignals)
        resolveAll()
        expect {
            alice.life shouldBe 18
            bob.life shouldBe 18
        }
    }

    @Test
    fun lamentNoviceDrawsAndMakesRobots() = scenario {
        libraries()
        alice.battlefield(EoeSet.lamentNovice, "Swamp")
        alice.hand(doom)
        bob.battlefield(bear)
        voidByDeath()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield has "Robot Token" }
    }

    @Test
    fun lamentNoviceCombatDraw() = scenario {
        libraries()
        alice.battlefield(EoeSet.lamentNovice)
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(EoeSet.lamentNovice)
        advanceTo(TurnStep.EndOfCombat, turn = 3)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            alice.life shouldBe 23
        }
    }

    /** Void: at your end step, attach it to target creature you control. */
    @Test
    fun nullsteelEdgeAttachesItself() = scenario {
        libraries()
        alice.battlefield(EoeSet.nullsteelEdge, ogre, "Swamp")
        alice.hand(doom)
        bob.battlefield(bear)
        voidByDeath()
        advanceTo(TurnStep.End)
        resolveAll()
        targets(ogre)
        expect { ogre.power shouldBe 6 }
    }

    @Test
    fun psalmOfTheFallenStarDrawsAnother() = scenario {
        libraries()
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(EoeSet.psalmOfTheFallenStar, doom)
        bob.battlefield(bear)
        voidByDeath()
        alice.casts(EoeSet.psalmOfTheFallenStar)
        resolveAll()
        choose()
        expect {
            alice.hand.size shouldBe 2
            alice.life shouldBe 19
        }
    }

    @Test
    fun nullwroughtColossusVoidEndStep() = scenario {
        libraries()
        alice.battlefield(EoeSet.nullwroughtColossus, "Swamp")
        alice.hand(doom)
        bob.battlefield(bear)
        voidByDeath()
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            alice.life shouldBe 19
        }
    }

    @Test
    fun nullwroughtColossusWithoutVoid() = scenario {
        libraries()
        alice.battlefield(EoeSet.nullwroughtColossus)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.life shouldBe 20 }
    }

    /** Void: exile an instant or sorcery card with mana value 2 or less from your graveyard, copy it and cast the copy free. */
    // CR 707.12
    @Test
    fun wanderingServomotorRecastsASpell() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Swamp")
        alice.hand(doom, EoeSet.wanderingServomotor)
        alice.graveyard(zap)
        bob.battlefield(bear)
        voidByDeath()
        alice.casts(EoeSet.wanderingServomotor)
        resolveAll()
        targets(zap)
        yes()
        targets("bob")
        expect {
            bob.life shouldBe 18
            zap.zone shouldBe Zone.Exile
        }
    }

    @Test
    fun chronalInterferenceCheaperWithVoid() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(doom, EoeSet.chronalInterference)
        bob.battlefield(bear)
        bob.hand(gizmo, "Island")
        voidByDeath()
        alice.casts(EoeSet.chronalInterference, target = "bob")
        resolveAll()
        choose(gizmo)
        expect {
            bob.graveyard has gizmo
            bob.hand has "Island"
        }
    }

    @Test
    fun doomedArcWithAndWithoutVoid() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(EoeSet.doomedArc, EoeSet.doomedArc, doom)
        bob.battlefield(bear, giant, ogre)
        alice.casts(EoeSet.doomedArc, target = giant)
        resolveAll()
        expect { giant.power shouldBe 3 }
        voidByDeath()
        alice.casts(EoeSet.doomedArc, target = ogre)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Graveyard }
    }

    // ---- chunk 06

    /** Each creature you control enters with a counter for each land that entered under your control this turn. */
    @Test
    fun geneTailoredTomorrowCountsLands() = scenario {
        alice.battlefield(EoeSet.geneTailoredTomorrow, "Forest")
        alice.hand("Forest", bear)
        alice.plays("Forest")
        alice.casts(bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun geneTailoredTomorrowMakesALander() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(EoeSet.geneTailoredTomorrow)
        alice.casts(EoeSet.geneTailoredTomorrow)
        resolveAll()
        expect { alice.battlefield has "Lander Token" }
    }

    @Test
    fun bioforgeMachinistDrawsAndBuilds() = scenario {
        alice.battlefield(EoeSet.bioforgeMachinist, *Array(8) { "Island" })
        alice.library(notes, notes)
        alice.activates(EoeSet.bioforgeMachinist)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            alice.battlefield has "Robot Token"
        }
    }

    /** Sacrificing an artifact (a Lander) deals 2 to target opponent. */
    @Test
    fun geneLabExpertPunishesSacrifice() = scenario {
        alice.battlefield("Forest", "Mountain", "Island", "Island")
        alice.hand(EoeSet.geneLabExpert)
        alice.library("Forest")
        alice.casts(EoeSet.geneLabExpert)
        resolveAll()
        alice.activates("Lander Token")
        resolveAll()
        targets("bob")
        choose("Forest")
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun fearlessSalvagebotExilesGraveyards() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(EoeSet.fearlessSalvagebot)
        bob.graveyard(bear, ogre)
        alice.casts(EoeSet.fearlessSalvagebot)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Exile
            alice.battlefield has "Lander Token"
        }
    }

    /** Counter unless its controller pays {2}; if they pay, you get a Lander. */
    // CR 118.12a
    @Test
    fun avertCatastrophePaidGivesALander() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(EoeSet.avertCatastrophe)
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand(zap)
        turn(active = bob)
        bob.casts(zap, target = "alice")
        bob.passes()
        alice.casts(EoeSet.avertCatastrophe, target = zap)
        resolveAll()
        yes()
        pay()
        expect {
            alice.battlefield has "Lander Token"
            alice.life shouldBe 18
        }
    }

    @Test
    fun frontierCrawlerLandersForEveryone() = scenario {
        alice.battlefield(EoeSet.frontierCrawler)
        bob.battlefield("Swamp")
        bob.hand(doom)
        alice.passes()
        bob.casts(doom, target = EoeSet.frontierCrawler)
        resolveAll()
        expect {
            alice.battlefield has "Lander Token"
            bob.battlefield has "Lander Token"
        }
    }

    @Test
    fun escapePodLaunchGivesItsControllerALander() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(EoeSet.escapePodLaunch)
        bob.battlefield(charm)
        alice.casts(EoeSet.escapePodLaunch, target = charm)
        resolveAll()
        expect {
            charm.zone shouldBe Zone.Graveyard
            bob.battlefield has "Lander Token"
        }
    }

    @Test
    fun icefieldWorldmawLandfallPumps() = scenario {
        alice.battlefield(EoeSet.icefieldWorldmaw, bear)
        alice.hand("Forest")
        alice.plays("Forest")
        resolveAll()
        expect { bear.power shouldBe 3 }
    }

    /** The Lander is sacrificed at the beginning of the end step on your next turn, not this turn's. */
    // CR 603.7
    @Test
    fun kavuGroundfinderLanderLastsATurn() = scenario {
        libraries()
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(EoeSet.kavuGroundfinder)
        alice.casts(EoeSet.kavuGroundfinder)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield has "Lander Token" }
        advanceTo(TurnStep.End, turn = 2)
        resolveAll()
        expect { alice.battlefield has "Lander Token" }
        advanceTo(TurnStep.End, turn = 3)
        resolveAll()
        expect { alice.battlefield lacks "Lander Token" }
    }

    @Test
    fun hardTouchdownSweeps() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", trinket)
        alice.hand(EoeSet.hardTouchdown)
        bob.battlefield(bear, giant)
        alice.casts(EoeSet.hardTouchdown)
        resolveAll()
        yes()
        choose(trinket)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            giant.zone shouldBe Zone.Battlefield
            alice.battlefield has "Lander Token"
        }
    }

    /** Excess damage makes a Lander. */
    // CR 120.4a
    @Test
    fun reentryDiveExcessDamage() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(EoeSet.reentryDive, EoeSet.reentryDive)
        bob.battlefield(bear, EoeSet.twinStarDisciples)
        bob.battlefield(EoeExtras.wall, counters = listOf(p1p1 to 2))
        alice.casts(EoeSet.reentryDive, target = EoeExtras.wall)
        resolveAll()
        expect { alice.battlefield lacks "Lander Token" }
        alice.casts(EoeSet.reentryDive, target = bear)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Lander Token" } shouldBe 1 }
    }

    @Test
    fun scavengeForForeverReturnsAndLands() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", trinket)
        alice.hand(EoeSet.scavengeForForever)
        alice.graveyard(ogre)
        alice.casts(EoeSet.scavengeForForever, target = ogre, choosing = listOf(trinket))
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Battlefield
            alice.battlefield has "Lander Token"
        }
    }

    @Test
    fun sowingArkCultivatorTappedAndLandfall() = scenario {
        libraries()
        alice.battlefield(EoeSet.sowingArkCultivator)
        alice.hand("Forest")
        alice.plays("Forest")
        resolveAll()
        expect { EoeSet.sowingArkCultivator.counters(p1p1) shouldBe 1 }
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(EoeSet.sowingArkCultivator)
        resolveAll()
        expect { alice.battlefield has "Lander Token" }
    }

    @Test
    fun sowingArkCollisionCheapTargetGivesALander() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(EoeSet.sowingArkCollision, EoeSet.sowingArkCollision)
        bob.battlefield(trinket, gizmo)
        alice.casts(EoeSet.sowingArkCollision, target = trinket)
        resolveAll()
        alice.casts(EoeSet.sowingArkCollision, target = gizmo)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Lander Token" } shouldBe 1 }
    }

    @Test
    fun daystarFrontieristWhenBehindOnLands() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(EoeSet.daystarFrontierist)
        bob.battlefield("Swamp", "Swamp", "Swamp")
        alice.casts(EoeSet.daystarFrontierist)
        resolveAll()
        expect { alice.battlefield has "Lander Token" }
    }

    /** The opponent chooses: two Landers for you, or two counters on it. */
    // CR 118.12
    @Test
    fun groundtreatyEnforcerOpponentChooses() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(EoeSet.groundtreatyEnforcer)
        alice.casts(EoeSet.groundtreatyEnforcer)
        resolveAll()
        targets("bob")
        yes(false)
        expect {
            EoeSet.groundtreatyEnforcer.counters(p1p1) shouldBe 2
            alice.battlefield lacks "Lander Token"
        }
    }
}
