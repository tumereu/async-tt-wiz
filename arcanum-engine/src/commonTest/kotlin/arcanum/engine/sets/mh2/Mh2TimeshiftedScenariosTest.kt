package arcanum.engine.sets.mh2

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * Modern Horizons 2 cards of parser chunks 15–16 in play: the timeshifted reprints. Trivial ones not played here: Seraphic Curator
 * (flying, protection), Spell Refusal (counter target spell), Sigil of Removal (a sacrifice bounce, as Sigil of Cleansing), Flame
 * Fissure (4 damage to each player, played with Lonely Confinement), Prowling Pack and Shardbound Envoy (storm and cascade, as
 * Spreading Revolt and Etherforged Sphinx), Vindication (destroy target permanent), Enchantress's Influence (a cast trigger, as Sythria),
 * Riptide Workshop (a bounce of a Wizard), and the split cards Spoken // Finished and Swift // Feral played with the blue and red cards.
 */
class Mh2TimeshiftedScenariosTest {
    private val s = Mh2Set
    private val p = Mh2Plain
    private val p1p1 = Mh2Plain.p1p1

    private fun pool(sc: arcanum.engine.scenario.Scenario) = sc.state.player(sc.alice.id).manaPool.map { it.color }

    // ---- Chunk 15

    @Test
    fun karmicShepherdReturns() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(s.karmicShepherd)
        alice.graveyard(p.giant)
        alice.casts(s.karmicShepherd)
        playOut(p.giant)
        expect { alice.battlefield has p.giant }
    }

    @Test
    fun sigilOfCleansingDestroys() = scenario {
        alice.battlefield(s.sigilOfCleansing)
        bob.battlefield(p.trinket)
        alice.activates(s.sigilOfCleansing, target = p.trinket)
        playOut()
        expect { bob.graveyard has p.trinket }
    }

    /** No damage to Alice, and she can't be targeted. */
    // CR 615.1, 702.18a
    @Test
    fun lonelyConfinementProtects() = scenario {
        alice.battlefield(s.lonelyConfinement)
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand(s.flameFissure, p.zap)
        turn(active = bob)
        bob.casts(s.flameFissure)
        allPass()
        playOut()
        expect {
            alice.life shouldBe 20
            bob.life shouldBe 16
        }
        assertFails { bob.casts(p.zap, target = "alice") }
    }

    /** Unpaid at upkeep it is sacrificed; paid with a discard it stays. */
    @Test
    fun lonelyConfinementUpkeep() = scenario {
        alice.battlefield(s.lonelyConfinement)
        alice.hand(p.elf)
        aliceUpkeep()
        playOut(p.elf)
        expect {
            alice.battlefield has s.lonelyConfinement
            alice.graveyard has p.elf
        }
    }

    @Test
    fun spiritSnareExilesAnAttacker() = scenario {
        alice.battlefield("Plains", s.spiritSnare)
        bob.battlefield(p.ogre)
        turn(active = bob, step = TurnStep.DeclareAttackers, priority = null)
        bob.attacks(p.ogre)
        playOut()
        bob.passes()
        alice.activates(s.spiritSnare, target = p.ogre)
        playOut()
        expect { bob.exile has p.ogre }
    }

    @Test
    fun brineDrakeReturnsTwoLands() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(s.brineDrake)
        alice.casts(s.brineDrake)
        playOut()
        answer(Answer.Targets(listOf(card("Island", 0), card("Island", 1)).map { arcanum.engine.state.TargetRef.Object(it) }))
        playOut()
        expect { alice.hand.names.count { it == "Island" } shouldBe 2 }
    }

    @Test
    fun upturningReturnsEverything() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", p.bear)
        bob.battlefield(p.ogre, "Mountain")
        alice.hand(s.upturning)
        alice.casts(s.upturning)
        playOut()
        expect {
            alice.battlefield.size shouldBe 0
            bob.battlefield.size shouldBe 0
            bob.hand has p.ogre
            alice.hand has p.bear
        }
    }

    /** In the graveyard with an Island: creatures fly. */
    // CR 113.6
    @Test
    fun amazementFromTheGraveyard() = scenario {
        alice.battlefield("Island", p.bear)
        alice.graveyard(s.amazement)
        expect { p.bear hasKeyword Keyword.Flying }
    }

    @Test
    fun boneShearerDestroys() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(s.boneShearer)
        bob.battlefield(p.bear, p.knight)
        alice.casts(s.boneShearer)
        playOut(p.bear)
        expect { bob.graveyard has p.bear }
    }

    /** At each player's upkeep, that player sacrifices one of their artifacts, creatures or lands. */
    @Test
    fun braidaTaxesUpkeep() = scenario {
        alice.battlefield(s.braidaCabalServant, p.bear)
        aliceUpkeep()
        playOut(p.bear)
        expect { alice.graveyard has p.bear }
    }

    @Test
    fun covetousnessDraws() = scenario {
        alice.battlefield("Swamp", s.covetousness)
        alice.library(p.ogre)
        alice.activates(s.covetousness)
        playOut()
        expect {
            alice.hand has p.ogre
            alice.life shouldBe 18
        }
    }

    /** Each player names a creature type and returns their own creature cards of the types named. */
    @Test
    fun patriarchsSummonsReturns() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(s.patriarchsSummons)
        alice.graveyard(p.bear, p.ogre)
        bob.graveyard(p.elf, p.giant)
        alice.casts(s.patriarchsSummons)
        allPass()
        repeat(2) {
            val d = decision<Decision.ChooseSubtype>()
            answer(Answer.SubtypeChoice(Subtype(if (d.player == alice.id) "Bear" else "Elf")))
        }
        playOut()
        expect {
            alice.battlefield has p.bear
            alice.graveyard has p.ogre
            bob.battlefield has p.elf
            bob.graveyard has p.giant
        }
    }

    /** Discarding a card: {B}. */
    // CR 605.1a
    @Test
    fun skirgeCompanionMana() = scenario {
        alice.battlefield(s.skirgeCompanion)
        alice.hand(p.elf)
        alice.activates(s.skirgeCompanion, choosing = listOf(p.elf))
        expect { alice.graveyard has p.elf }
        assertEquals(listOf(Color.Black), pool(this))
    }

    /** A coin flip won puts a luck counter on it. */
    @Test
    fun luckyEncounterCounts() = scenario {
        alice.battlefield("Mountain", s.taproomScoundrel, p.trinket, s.luckyEncounter)
        random(Answer.CoinFlip(true))
        alice.activates(s.taproomScoundrel, choosing = listOf(p.trinket))
        playOut()
        expect { s.luckyEncounter.counters(CounterKind.Named("luck")) shouldBe 1 }
    }

    /** Ten luck counters at upkeep: Alice wins. */
    // CR 104.2b
    @Test
    fun luckyEncounterWins() = scenario {
        alice.battlefield(s.luckyEncounter, counters = listOf(CounterKind.Named("luck") to 10))
        aliceUpkeep()
        playOut()
        assertTrue(state.player(bob.id).lost)
    }

    @Test
    fun goblinShellfirePings() = scenario {
        alice.battlefield(s.goblinShellfire, p.elf)
        alice.activates(s.goblinShellfire, target = "bob", choosing = listOf(p.elf))
        playOut()
        expect { bob.life shouldBe 19 }
    }

    /** {X}{X}{1} with X 1 destroys a noncreature artifact with mana value 1. */
    // CR 107.3
    @Test
    fun gorillaMysticDestroys() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", s.gorillaMystic)
        bob.battlefield(p.trinket)
        alice.activates(s.gorillaMystic, x = 1, target = p.trinket)
        playOut()
        expect { bob.graveyard has p.trinket }
    }

    @Test
    fun imperialEnlisterFinds() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(s.imperialEnlister)
        alice.library(p.giant, p.elf)
        alice.casts(s.imperialEnlister)
        playOut(p.elf)
        expect { alice.hand has p.elf }
    }

    /** Free when an opponent controls an Island and Alice a Mountain. */
    @Test
    fun moggScavengingFree() = scenario {
        alice.battlefield("Mountain", tapped = true)
        alice.hand(s.moggScavenging)
        bob.battlefield("Island", p.trinket)
        alice.casts(s.moggScavenging, way = permitted(s.moggScavenging), target = p.trinket)
        playOut()
        expect { bob.graveyard has p.trinket }
    }

    /** Returning a Forest untaps a creature, once each turn. */
    @Test
    fun quirionWardenUntaps() = scenario {
        alice.battlefield("Forest", s.quirionWarden)
        alice.battlefield(p.bear, tapped = true)
        alice.activates(s.quirionWarden, target = p.bear, choosing = listOf("Forest"))
        playOut()
        expect {
            p.bear.tapped shouldBe false
            alice.hand has "Forest"
        }
    }

    @Test
    fun squirrelThrongCountsSquirrels() = scenario {
        alice.battlefield(s.squirrelThrong, p.squirrel)
        bob.battlefield(p.squirrel)
        expect { s.squirrelThrong.power shouldBe 4 }
    }

    /** A land returned as it enters; a land going to the graveyard makes an Elemental. */
    @Test
    fun titanyaReturnsALandAndMakesElementals() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", s.zuranSphere)
        alice.hand(s.titanyaKeeperOfArgoth)
        alice.graveyard("Island")
        alice.casts(s.titanyaKeeperOfArgoth)
        playOut("Island")
        expect { alice.battlefield has "Island" }
        alice.activates(s.zuranSphere, choosing = listOf("Island"))
        playOut()
        expect {
            alice.life shouldBe 22
            alice.battlefield has "Elemental Token"
        }
    }

    /** Sacrificed: a card, and dying finds two basic lands. */
    @Test
    fun yavimayaAncientSacrificed() = scenario {
        alice.battlefield("Forest", "Forest", s.yavimayaAncient)
        alice.library(p.ogre, "Forest", "Island")
        alice.activates(s.yavimayaAncient)
        playOut("Forest", "Island")
        expect {
            alice.hand has p.ogre
            alice.hand has "Forest"
            alice.hand has "Island"
        }
    }

    /** Discarding a creature card lets it be cast from the graveyard, and it gains haste. */
    @Test
    fun charnelCastsFromTheGraveyard() = scenario {
        alice.battlefield("Forest", "Forest", s.charnelNightmareAdept)
        alice.hand(p.bear)
        alice.activates(s.charnelNightmareAdept, choosing = listOf(p.bear))
        playOut()
        alice.casts(p.bear, way = permitted(p.bear))
        playOut()
        expect {
            alice.battlefield has p.bear
            p.bear hasKeyword Keyword.Haste
        }
    }

    /** Creatures get +1/+1; a land tapped for mana adds one more of its type. */
    // CR 605.1b
    @Test
    fun mirarisEchoDoublesLands() = scenario {
        alice.battlefield("Forest", s.mirarisEcho)
        alice.hand(p.bear)
        alice.casts(p.bear, paying = listOf("Forest"))
        playOut()
        expect {
            alice.battlefield has p.bear
            p.bear.power shouldBe 3
        }
    }

    /** Other enchantments have shroud; sacrificed it puts an enchantment card on top. */
    @Test
    fun silverleafGroveSearches() = scenario {
        alice.battlefield("Forest", s.silverleafGrove)
        alice.library(p.ogre, p.charm, p.elf)
        alice.activates(s.silverleafGrove)
        playOut(p.charm)
        expect { alice.library.names.first() shouldBe p.charm }
    }

    @Test
    fun cursedIdolStopsCreatureAbilities() = scenario {
        alice.battlefield(s.cursedIdol, s.myrScrapper, p.bear)
        assertFails { alice.activates(s.myrScrapper, target = p.bear) }
    }

    @Test
    fun extrusionGolemEatsArtifacts() = scenario {
        alice.battlefield(s.extrusionGolem, p.trinket)
        alice.activates(s.extrusionGolem, target = s.extrusionGolem, choosing = listOf(p.trinket))
        playOut()
        expect { s.extrusionGolem.counters(p1p1) shouldBe 1 }
    }

    /** {T}, mill a card: {C}; moving a library card, it isn't a mana ability and uses the Stack. */
    // CR 605.1a
    @Test
    fun grindletMana() = scenario {
        alice.battlefield(s.grindlet)
        alice.library(p.ogre)
        alice.activates(s.grindlet)
        playOut()
        expect { alice.graveyard has p.ogre }
        assertEquals(listOf<Color?>(null), pool(this))
    }

    @Test
    fun nevinyrralsPlatterWipes() = scenario {
        alice.battlefield("Plains", s.nevinyrralsPlatter, p.bear, p.charm)
        bob.battlefield(p.trinket, "Island")
        alice.activates(s.nevinyrralsPlatter)
        playOut()
        expect {
            alice.graveyard has s.nevinyrralsPlatter
            alice.graveyard has p.bear
            alice.graveyard has p.charm
            bob.graveyard has p.trinket
            bob.battlefield has "Island"
        }
    }

    @Test
    fun nevinyrralsPlatterEntersTapped() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(s.nevinyrralsPlatter)
        alice.casts(s.nevinyrralsPlatter)
        playOut()
        expect { s.nevinyrralsPlatter.tapped shouldBe true }
    }

    /** A regeneration shield saves it from destruction, tapped. */
    // CR 701.19a
    @Test
    fun quiltworkGnomesRegenerate() = scenario {
        alice.battlefield("Swamp", s.quiltworkGnomes)
        alice.hand(p.elf, p.doom)
        alice.activates(s.quiltworkGnomes, choosing = listOf(p.elf))
        playOut()
        alice.casts(p.doom, target = s.quiltworkGnomes)
        playOut()
        expect {
            alice.battlefield has s.quiltworkGnomes
            s.quiltworkGnomes.tapped shouldBe true
        }
    }

    // ---- Chunk 16

    /** {2}, {T}: {B} for each Swamp. */
    @Test
    fun cabalVaultsMana() = scenario {
        alice.battlefield(s.cabalVaults, "Swamp", "Swamp", "Swamp")
        alice.activates(s.cabalVaults, ability = 0, paying = listOf("Swamp", "Swamp#1"))
        assertEquals(List(3) { Color.Black }, pool(this))
    }

    /** It becomes a 2/2 creature that is still a land; its other ability pumps an Assembly-Worker. */
    @Test
    fun mishrasWorkfloorAnimatesAndPumps() = scenario {
        alice.battlefield("Plains", s.mishrasWorkfloor, s.academyFabricator)
        alice.activates(s.mishrasWorkfloor, ability = 2, target = s.academyFabricator)
        playOut()
        expect { s.academyFabricator.power shouldBe 2 }
    }

    @Test
    fun mishrasWorkfloorBecomesACreature() = scenario {
        alice.battlefield("Plains", s.mishrasWorkfloor)
        alice.activates(s.mishrasWorkfloor, ability = 1)
        playOut()
        expect {
            s.mishrasWorkfloor.power shouldBe 2
            assertTrue(arcanum.api.CardType.Land in s.mishrasWorkfloor.types)
        }
    }

    /** The chosen number stops noncreature spells of that mana value. */
    @Test
    fun sanctumHierarchStopsANumber() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Mountain")
        alice.hand(s.sanctumHierarch, p.zap)
        alice.casts(s.sanctumHierarch, paying = listOf("Plains", "Plains#1", "Plains#2"))
        playOut()
        decision<Decision.ChooseNumber>()
        answer(Answer.Number(1))
        playOut()
        assertFails { alice.casts(p.zap, target = "bob") }
    }

    /** Aftermath: Wreck is cast from the graveyard, damage equal to the lands. */
    // CR 702.127a
    @Test
    fun wreckFromTheGraveyard() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard(s.wayWreck)
        bob.battlefield(p.ogre)
        alice.casts(s.wayWreck, way = arcanum.engine.input.CastWay(face = 1), target = p.ogre)
        playOut()
        expect {
            bob.graveyard has p.ogre
            alice.exile has s.wayWreck
        }
    }

    /** Smolder divides 2 damage between two targets. */
    // CR 601.2d
    @Test
    fun smolderDivides() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(s.smolderSleet)
        bob.battlefield(p.elf, p.squirrel)
        alice.casts(s.smolderSleet, way = arcanum.engine.input.CastWay(face = 0), targets = listOf(p.elf, p.squirrel))
        playOut()
        expect {
            bob.graveyard has p.elf
            bob.graveyard has p.squirrel
        }
    }
}
