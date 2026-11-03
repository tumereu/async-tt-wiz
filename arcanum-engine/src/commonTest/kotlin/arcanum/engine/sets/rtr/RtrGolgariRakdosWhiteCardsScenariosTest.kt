package arcanum.engine.sets.rtr

import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.Rtr.bear
import arcanum.engine.sets.rtr.Rtr.charm
import arcanum.engine.sets.rtr.Rtr.doom
import arcanum.engine.sets.rtr.Rtr.giant
import arcanum.engine.sets.rtr.Rtr.human
import arcanum.engine.sets.rtr.Rtr.ogre
import arcanum.engine.sets.rtr.Rtr.p1p1
import arcanum.engine.sets.rtr.Rtr.ponder
import arcanum.engine.sets.rtr.Rtr.shock
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Return to Ravnica's scavenge, unleash, Golgari, Rakdos and white cards in play (parser chunks 04–06), beyond the mechanics played in [RtrMechanicsScenariosTest]. */
class RtrGolgariRakdosWhiteCardsScenariosTest {
    private val mudcryptWurm = Cards.invent("Mudcrypt Wurm", "{6}{B}", "Creature — Zombie Wurm", "Scavenge {6}{B} ({6}{B}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)", "6/6")
    private val tombSwarmLocust = Cards.invent("Tomb Swarm Locust", "{5}{B}", "Creature — Insect", """
        Flying
        Scavenge {2}{B}{B} ({2}{B}{B}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)
    """, "3/3")
    private val rotspanBehemoth = Cards.invent("Rotspan Behemoth", "{2}{G}{G}", "Creature — Insect", "Scavenge {4}{G}{G} ({4}{G}{G}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)", "6/2")
    private val toilingScarab = Cards.invent("Toiling Scarab", "{1}{G}", "Creature — Insect", "Scavenge {5}{G} ({5}{G}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)", "2/2")
    private val swampfenMonitor = Cards.invent("Swampfen Monitor", "{2}{G}{G}", "Creature — Lizard", """
        Trample
        Scavenge {5}{G}{G} ({5}{G}{G}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)
    """, "3/3")
    private val siltMauler = Cards.invent("Silt Mauler", "{1}{B}{G}", "Creature — Plant Zombie", """
        Haste
        Scavenge {3}{B}{G} ({3}{B}{G}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)
    """, "2/1")
    private val creepingSprout = Cards.invent("Creeping Sprout", "{B/G}", "Creature — Plant Zombie", "Scavenge {0} ({0}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)", "1/1")
    private val drainageStinger = Cards.invent("Drainage Stinger", "{2}{B}{G}", "Creature — Scorpion", """
        Deathtouch (Any amount of damage this deals to a creature is enough to destroy it.)
        Scavenge {1}{B}{G} ({1}{B}{G}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)
    """, "1/1")
    private val rotFarmSigil = Cards.invent("Rot Farm Sigil", "{B}{G}", "Instant", """
        Choose one —
        • All creatures get -1/-1 until end of turn.
        • Destroy target enchantment.
        • Regenerate each creature you control.
    """)
    private val varkasUndercityLich = Cards.invent("Varkas, Undercity Lich", "{B}{B}{G}{G}", "Legendary Creature — Zombie Elf", """
        Varkas gets +1/+1 for each creature card in your graveyard.
        {1}{B}{G}, Sacrifice another creature: Each opponent loses life equal to the sacrificed creature's power.
        Sacrifice a Swamp and a Forest: Return this card from your graveyard to your hand.
    """, "1/1")
    private val lichLordsDecree = Cards.invent("Lich Lord's Decree", "{2}{B}{G}", "Sorcery", "Search your library for up to two creature cards and reveal them. Put one into your hand and the other into your graveyard. Then shuffle.")
    private val fungalRiteKeeper = Cards.invent("Fungal Rite-Keeper", "{B}{G}", "Creature — Elf Shaman", """
        {1}{B}{G}: Target creature gets +1/+1 and gains intimidate until end of turn. (It can't be blocked except by artifact creatures and/or creatures that share a color with it.)
        {2}{B}{G}, Sacrifice a nontoken creature: Create X 1/1 green Saproling creature tokens, where X is the sacrificed creature's toughness.
    """, "2/2")
    private val molderingTroll = Cards.invent("Moldering Troll", "{B}{G}", "Creature — Zombie Troll", """
        Trample
        Discard a creature card: Put a +1/+1 counter on this creature.
        {B}: Regenerate this creature.
    """, "2/1")
    private val bridgepostTroll = Cards.invent("Bridgepost Troll", "{1}{B}{G}", "Creature — Troll", """
        Defender
        Reach (This creature can block creatures with flying.)
        {1}{B}{G}: Regenerate this creature.
    """, "0/4")
    private val harvestOfRot = Cards.invent("Harvest of Rot", "{4}{B}{G}", "Sorcery", "Target creature gets +3/+3 until end of turn. Another target creature gets -3/-3 until end of turn.")
    private val gruesomeScavenging = Cards.invent("Gruesome Scavenging", "{B}{G}", "Instant", "Reveal the top five cards of your library. You may put a creature or land card from among them into your hand. Put the rest into your graveyard.")
    private val unearthedKeepsake = Cards.invent("Unearthed Keepsake", "{B}{G}", "Sorcery", "Return target card from your graveyard to your hand. Exile Unearthed Keepsake.")
    private val gravebloomDruid = Cards.invent("Gravebloom Druid", "{B/G}", "Creature — Elf Shaman", """
        {T}: Exile target land card from a graveyard. Add one mana of any color. (Activate only as an instant.)
        {B}, {T}: Exile target instant or sorcery card from a graveyard. Each opponent loses 2 life.
        {G}, {T}: Exile target creature card from a graveyard. You gain 2 life.
    """, "1/2")
    private val revelCutthroat = Cards.invent("Revel Cutthroat", "{1}{B}", "Creature — Human Assassin", """
        Deathtouch
        Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)
    """, "1/1")
    private val brawlhouseGiant = Cards.invent("Brawlhouse Giant", "{2}{R}{R}", "Creature — Giant", """
        Trample
        Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)
    """, "2/2")
    private val mayhemImps = Cards.invent("Mayhem Imps", "{4}{R}{R}", "Creature — Imp", """
        Flying
        Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)
        This creature has trample as long as it has a +1/+1 counter on it.
    """, "6/5")
    private val chainhookBrawler = Cards.invent("Chainhook Brawler", "{1}{R}", "Creature — Human Warrior", "Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)", "2/1")
    private val spatterBruiser = Cards.invent("Spatter Bruiser", "{2}{R}", "Creature — Human Warrior", """
        First strike
        Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)
    """, "2/2")
    private val fairgroundNightmare = Cards.invent("Fairground Nightmare", "{4}{B}{R}", "Creature — Nightmare Horse", """
        First strike, haste
        Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)
    """, "4/4")
    private val pitfightFlailer = Cards.invent("Pitfight Flailer", "{1}{B}{R}", "Creature — Ogre Warrior", """
        Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)
        {2}{B}{R}, Sacrifice this creature: It deals damage equal to its power to target player or planeswalker.
    """, "2/2")
    private val gigglingFiendling = Cards.invent("Giggling Fiendling", "{B/R}", "Creature — Devil", "Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)", "2/2")
    private val spawnOfThePit = Cards.invent("Spawn of the Pit", "{3}{B}{R}", "Creature — Horror", "Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)", "5/5")
    private val pitRingmaster = Cards.invent("Pit Ringmaster", "{B}{R}", "Creature — Human Shaman", """
        {B}{R}: Target blocking creature gets -1/-1 until end of turn.
        {B}{R}: Target player who lost life this turn loses 1 life.
    """, "2/2")
    private val cranialRupture = Cards.invent("Cranial Rupture", "{3}{B}{R}", "Sorcery", "Cranial Rupture deals 2 damage to each opponent. Those players each discard two cards at random.")
    private val butcherySpectacle = Cards.invent("Butchery Spectacle", "{2}{B}{R}", "Sorcery", """
        This spell can't be countered.
        Choose a nonland card name. Search target opponent's graveyard, hand, and library for any number of cards with that name and exile them. Then that player shuffles.
    """)
    private val carnivalSigil = Cards.invent("Carnival Sigil", "{B}{R}", "Instant", """
        Choose one —
        • Exile target player's graveyard.
        • Destroy target artifact.
        • Each creature deals 1 damage to its controller.
    """)
    private val revelRingleader = Cards.invent("Revel Ringleader", "{4}{B}{R}", "Creature — Skeleton Warrior", """
        First strike
        Whenever this creature deals combat damage to a player, that player discards a card at random.
        {B}: Regenerate this creature.
    """, "3/1")
    private val drillfrenzy = Cards.invent("Drillfrenzy", "{1}{B}{R}", "Instant", "Target creature gets +4/-4 until end of turn.")
    private val doomdrill = Cards.invent("Doomdrill", "{B}{R}", "Sorcery", "Destroy target creature or planeswalker.")
    private val gatehouseSentinel = Cards.invent("Gatehouse Sentinel", "{3}{W}", "Creature — Giant Soldier", "This creature has vigilance as long as you control a Gate.", "2/5")
    private val marketPackBeast = Cards.invent("Market Pack-Beast", "{4}{W}", "Creature — Beast", "Whenever this creature attacks, another target attacking creature gets +0/+2 until end of turn. Untap that creature.", "2/5")
    private val spiritPlate = Cards.invent("Spirit Plate", "{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +1/+1 for each enchantment you control and has first strike.
    """)
    private val wailingSpecter = Cards.invent("Wailing Specter", "{1}{W}", "Creature — Spirit", "Sacrifice this creature: Destroy target enchantment.", "2/1")
    private val chivalricOath = Cards.invent("Chivalric Oath", "{4}{W}", "Enchantment — Aura", """
        Enchant creature
        When this Aura enters, create a 2/2 white Knight creature token with vigilance. (Attacking doesn't cause it to tap.)
        Enchanted creature gets +2/+2 and has vigilance.
    """)
    private val spectralCommander = Cards.invent("Spectral Commander", "{3}{W}", "Creature — Spirit Soldier", "Creature tokens you control get +1/+1.", "2/3")
    private val wardCaptain = Cards.invent("Ward Captain", "{W}{W}", "Creature — Human Soldier", """
        First strike
        Whenever this creature deals combat damage to a player, create a 1/1 white Soldier creature token.
    """, "2/2")
    private val checkpointBarricade = Cards.invent("Checkpoint Barricade", "{2}{W}", "Enchantment — Aura", """
        Enchant land
        When this Aura enters, create a 2/2 white Knight creature token with vigilance.
        Enchanted land has "{T}: Prevent the next 1 damage that would be dealt to you this turn."
    """)
    private val conclaveLookout = Cards.invent("Conclave Lookout", "{2}{W}", "Creature — Elephant Soldier", "{5}{G}: Regenerate this creature.", "2/3")
    private val birdcageVendor = Cards.invent("Birdcage Vendor", "{2}{W}", "Creature — Human", "When this creature enters, create a 1/1 white Bird creature token with flying.", "1/2")
    private val summaryJustice = Cards.invent("Summary Justice", "{W}", "Instant", "Until end of turn, target creature gets +1/+0 and gains first strike and lifelink.")
    private val gate = Cards.invent("Tenth Street Gate", null, "Land — Gate", "{T}: Add {W}.")

    // ---- Chunk 04: scavenge and Golgari (scavenge itself: RtrMechanicsScenariosTest)

    /** Each scavenge card, paid with plenty of lands, puts counters equal to its power on the bear. */
    // CR 702.97a
    @Test
    fun eachScavengeCardGivesItsPower() {
        listOf(mudcryptWurm to 6, tombSwarmLocust to 3, rotspanBehemoth to 6, toilingScarab to 2, swampfenMonitor to 3,
            siltMauler to 2, creepingSprout to 1, drainageStinger to 1).forEach { (card, power) ->
            scenario {
                alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Forest", "Forest", "Forest", "Forest", bear)
                alice.graveyard(card)
                alice.activates(card, target = bear)
                resolveAll()
                expect {
                    bear.counters(p1p1) shouldBe power
                    alice.exile has card
                }
            }
        }
    }

    // CR 701.19a: regenerate each creature you control
    @Test
    fun rotFarmSigilRegeneratesTheTeam() = scenario {
        alice.battlefield("Swamp", "Forest", "Swamp", bear)
        alice.hand(rotFarmSigil, doom)
        alice.casts(rotFarmSigil, modes = listOf(2), paying = listOf("Swamp", "Forest"))
        resolveAll()
        alice.casts(doom, target = bear)
        resolveAll()
        expect {
            alice.battlefield has bear
            bear.tapped shouldBe true
        }
    }

    @Test
    fun rotFarmSigilShrinksEverything() = scenario {
        alice.battlefield("Swamp", "Forest", human)
        alice.hand(rotFarmSigil)
        bob.battlefield(human, ogre)
        alice.casts(rotFarmSigil, modes = listOf(0))
        resolveAll()
        expect {
            alice.graveyard has human
            bob.graveyard has human
            ogre.power shouldBe 2
        }
    }

    @Test
    fun varkasGrowsAndFlingsAndComesBack() = scenario {
        alice.battlefield(varkasUndercityLich, ogre, "Swamp", "Forest", "Swamp")
        alice.graveyard(bear, giant)
        expect { varkasUndercityLich.power shouldBe 3 }
        alice.activates(varkasUndercityLich, ability = 0, choosing = listOf(ogre))
        resolveAll()
        expect {
            bob.life shouldBe 17
            varkasUndercityLich.power shouldBe 4
        }
    }

    @Test
    fun varkasReturnsFromTheGraveyard() = scenario {
        alice.battlefield("Swamp", "Forest")
        alice.graveyard(varkasUndercityLich)
        // its abilities: the sacrifice one, then the one that works from the graveyard
        alice.activates(varkasUndercityLich, ability = 1, choosing = listOf("Swamp", "Forest"))
        resolveAll()
        expect { alice.hand has varkasUndercityLich }
    }

    @Test
    fun lichLordsDecreeSplitsTwoCreatures() = scenario {
        alice.battlefield("Swamp", "Swamp", "Forest", "Forest")
        alice.hand(lichLordsDecree)
        alice.library(bear, "Island", giant)
        alice.casts(lichLordsDecree)
        resolveAll()
        alice.chooses(bear, giant)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(giant)
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf(giant)
            alice.graveyard has bear
            alice.library.names shouldBe listOf("Island")
        }
    }

    // CR 702.13a: intimidate
    @Test
    fun fungalRiteKeeperPumpsAndMakesSaprolings() = scenario {
        alice.battlefield(fungalRiteKeeper, giant, "Swamp", "Forest", "Swamp", "Swamp", "Forest", "Forest", "Swamp")
        alice.activates(fungalRiteKeeper, ability = 0, target = giant, paying = listOf("Swamp", "Forest", "Swamp#1"))
        resolveAll()
        expect { giant hasKeyword Keyword.Intimidate }
        alice.activates(fungalRiteKeeper, ability = 1, choosing = listOf(giant))
        resolveAll()
        expect { alice.battlefield.names.count { it == "Saproling Token" } shouldBe 6 }
    }

    @Test
    fun molderingTrollGrowsByDiscardAndRegenerates() = scenario {
        alice.battlefield(molderingTroll, "Swamp", "Swamp")
        alice.hand(bear, doom)
        alice.activates(molderingTroll, ability = 0, choosing = listOf(bear))
        resolveAll()
        expect { molderingTroll.counters(p1p1) shouldBe 1 }
        alice.activates(molderingTroll, ability = 1, paying = listOf("Swamp"))
        resolveAll()
        alice.casts(doom, target = molderingTroll, paying = listOf("Swamp#1"))
        resolveAll()
        expect { alice.battlefield has molderingTroll }
    }

    @Test
    fun bridgepostTrollRegenerates() = scenario {
        alice.battlefield(bridgepostTroll, "Swamp", "Forest", "Swamp", "Swamp")
        alice.hand(doom)
        alice.activates(bridgepostTroll, paying = listOf("Swamp", "Forest", "Swamp#1"))
        resolveAll()
        alice.casts(doom, target = bridgepostTroll)
        resolveAll()
        expect {
            alice.battlefield has bridgepostTroll
            bridgepostTroll.tapped shouldBe true
        }
    }

    @Test
    fun harvestOfRotPumpsOneShrinksAnother() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Forest", "Forest", bear)
        alice.hand(harvestOfRot)
        bob.battlefield(ogre)
        alice.casts(harvestOfRot, targets = listOf(bear, ogre))
        resolveAll()
        expect {
            bear.power shouldBe 5
            bob.graveyard has ogre
        }
    }

    @Test
    fun gruesomeScavengingKeepsOne() = scenario {
        alice.battlefield("Swamp", "Forest")
        alice.hand(gruesomeScavenging)
        alice.library(bear, ponder, "Island", ogre, giant, human)
        alice.casts(gruesomeScavenging)
        resolveAll()
        alice.chooses(giant)
        resolveAll()
        expect {
            alice.hand has giant
            alice.graveyard.size shouldBe 5
            alice.library.names shouldBe listOf(human)
        }
    }

    @Test
    fun unearthedKeepsakeReturnsAnyCardAndIsExiled() = scenario {
        alice.battlefield("Swamp", "Forest")
        alice.hand(unearthedKeepsake)
        alice.graveyard(ponder)
        alice.casts(unearthedKeepsake, target = ponder)
        resolveAll()
        expect {
            alice.hand has ponder
            alice.exile has unearthedKeepsake
        }
    }

    @Test
    fun gravebloomDruidExilesForEffects() = scenario {
        alice.battlefield(gravebloomDruid, "Swamp")
        bob.graveyard(shock)
        alice.activates(gravebloomDruid, ability = 1, target = shock)
        resolveAll()
        expect {
            bob.exile has shock
            bob.life shouldBe 18
        }
    }

    // ---- Chunk 05: unleash and Rakdos (unleash itself: RtrMechanicsScenariosTest)

    /** Each unleash creature may enter with a +1/+1 counter, and then can't block. */
    // CR 702.98a
    @Test
    fun eachUnleashCreatureEntersWithACounter() {
        listOf(revelCutthroat, brawlhouseGiant, chainhookBrawler, spatterBruiser, fairgroundNightmare, pitfightFlailer,
            gigglingFiendling, spawnOfThePit).forEach { card ->
            scenario {
                alice.battlefield("Swamp", "Swamp", "Swamp", "Mountain", "Mountain", "Mountain")
                alice.hand(card)
                alice.casts(card)
                resolveAll()
                may(true)
                resolveAll()
                expect {
                    card.counters(p1p1) shouldBe 1
                    card hasMechanic { it is Mechanic.CantBlock }
                }
            }
        }
    }

    @Test
    fun mayhemImpsTrampleWithItsCounter() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(mayhemImps)
        alice.casts(mayhemImps)
        resolveAll()
        may(true)
        resolveAll()
        expect { mayhemImps hasKeyword Keyword.Trample }
    }

    @Test
    fun pitfightFlailerFlingsItself() = scenario {
        alice.battlefield(pitfightFlailer, "Swamp", "Swamp", "Mountain", "Mountain")
        alice.activates(pitfightFlailer, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.graveyard has pitfightFlailer
        }
    }

    @Test
    fun pitRingmasterDrainsAPlayerWhoLostLife() = scenario {
        alice.battlefield(pitRingmaster, "Swamp", "Mountain", "Mountain")
        alice.hand(shock)
        assertFailsWith<IllegalArgumentException> { alice.activates(pitRingmaster, ability = 1, target = "bob", paying = listOf("Swamp", "Mountain")) }
    }

    @Test
    fun pitRingmasterAfterALoss() = scenario {
        alice.battlefield(pitRingmaster, "Swamp", "Mountain", "Mountain")
        alice.hand(shock)
        alice.casts(shock, target = "bob", paying = listOf("Mountain#1"))
        resolveAll()
        alice.activates(pitRingmaster, ability = 1, target = "bob", paying = listOf("Swamp", "Mountain"))
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    // CR 701.9b: discarding at random
    @Test
    fun cranialRuptureBurnsAndDiscardsAtRandom() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Mountain", "Mountain")
        alice.hand(cranialRupture)
        bob.hand(ogre, giant, bear)
        alice.casts(cranialRupture)
        resolveAll()
        expect {
            bob.life shouldBe 18
            bob.hand.size shouldBe 1
        }
    }

    // CR 201.3: a named card
    @Test
    fun butcherySpectacleExilesEveryCopy() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Mountain")
        alice.hand(butcherySpectacle)
        bob.hand(ogre)
        bob.graveyard(ogre)
        bob.library(bear, ogre)
        alice.casts(butcherySpectacle, target = "bob")
        resolveAll()
        answer(Answer.CardName(ogre))
        resolveAll()
        if (pending is Decision.ChooseObjects) answer(Answer.Objects(decision<Decision.ChooseObjects>().options))
        resolveAll()
        expect {
            bob.exile.names.count { it == ogre } shouldBe 3
            bob.library.names shouldBe listOf(bear)
        }
    }

    @Test
    fun carnivalSigilHurtsEachController() = scenario {
        alice.battlefield("Swamp", "Mountain", bear)
        alice.hand(carnivalSigil)
        bob.battlefield(ogre, human)
        alice.casts(carnivalSigil, modes = listOf(2))
        resolveAll()
        expect {
            alice.life shouldBe 19
            bob.life shouldBe 18
        }
    }

    @Test
    fun revelRingleaderMakesThemDiscardAtRandom() = scenario {
        alice.battlefield(revelRingleader)
        bob.hand(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(revelRingleader)
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun drillfrenzyKillsWithMinusFour() = scenario {
        alice.battlefield("Swamp", "Mountain", "Mountain")
        alice.hand(drillfrenzy)
        bob.battlefield(ogre)
        alice.casts(drillfrenzy, target = ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun doomdrillDestroys() = scenario {
        alice.battlefield("Swamp", "Mountain")
        alice.hand(doomdrill)
        bob.battlefield(giant)
        alice.casts(doomdrill, target = giant)
        resolveAll()
        expect { bob.graveyard has giant }
    }

    // ---- Chunk 06: white

    @Test
    fun gatehouseSentinelGuardsWithAGate() = scenario {
        alice.battlefield(gatehouseSentinel)
        expect { gatehouseSentinel lacksKeyword Keyword.Vigilance }
        alice.hand(gate)
        alice.plays(gate)
        expect { gatehouseSentinel hasKeyword Keyword.Vigilance }
    }

    @Test
    fun marketPackBeastUntapsAnotherAttacker() = scenario {
        alice.battlefield(marketPackBeast, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(marketPackBeast, bear)
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bear.toughness shouldBe 4
            bear.tapped shouldBe false
        }
    }

    @Test
    fun spiritPlateCountsEnchantments() = scenario {
        alice.battlefield("Plains", bear, charm)
        alice.hand(spiritPlate)
        alice.casts(spiritPlate, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.FirstStrike
        }
    }

    @Test
    fun wailingSpecterDestroysAnEnchantment() = scenario {
        alice.battlefield(wailingSpecter)
        bob.battlefield(charm)
        alice.activates(wailingSpecter, target = charm)
        resolveAll()
        expect { bob.graveyard has charm }
    }

    @Test
    fun chivalricOathMakesAKnightAndPumps() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(chivalricOath)
        alice.casts(chivalricOath, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Vigilance
            "Knight Token" hasKeyword Keyword.Vigilance
        }
    }

    @Test
    fun spectralCommanderPumpsTokens() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", spectralCommander)
        alice.hand(birdcageVendor)
        alice.casts(birdcageVendor, paying = listOf("Plains", "Plains#1", "Plains#2"))
        resolveAll()
        expect {
            "Bird Token".power shouldBe 2
            "Bird Token" hasKeyword Keyword.Flying
        }
    }

    @Test
    fun wardCaptainMakesASoldierOnDamage() = scenario {
        alice.battlefield(wardCaptain)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wardCaptain)
        advanceTo(TurnStep.EndOfCombat)
        expect { alice.battlefield has "Soldier Token" }
    }

    // CR 615.1: a prevention shield
    @Test
    fun checkpointBarricadeMakesAKnightAndAShield() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Island")
        alice.hand(checkpointBarricade)
        bob.battlefield("Mountain")
        bob.hand(shock)
        alice.casts(checkpointBarricade, target = "Island", paying = listOf("Plains", "Plains#1", "Plains#2"))
        resolveAll()
        expect { alice.battlefield has "Knight Token" }
        alice.activates("Island", ability = 1)
        resolveAll()
        alice.passes()
        bob.casts(shock, target = "alice")
        resolveAll()
        expect { alice.life shouldBe 19 }
    }

    @Test
    fun conclaveLookoutRegenerates() = scenario {
        alice.battlefield(conclaveLookout, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Swamp")
        alice.hand(doom)
        alice.activates(conclaveLookout, paying = listOf("Forest", "Forest#1", "Forest#2", "Forest#3", "Forest#4", "Forest#5"))
        resolveAll()
        alice.casts(doom, target = conclaveLookout)
        resolveAll()
        expect { alice.battlefield has conclaveLookout }
    }

    @Test
    fun summaryJusticeGivesFirstStrikeAndLifelink() = scenario {
        alice.battlefield("Plains", bear)
        alice.hand(summaryJustice)
        alice.casts(summaryJustice, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.FirstStrike
            bear hasKeyword Keyword.Lifelink
        }
    }
}
