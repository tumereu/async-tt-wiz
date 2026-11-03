package arcanum.engine.sets.ecl

import arcanum.api.Color
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ecl.Ecl.bear
import arcanum.engine.sets.ecl.Ecl.bigSpell
import arcanum.engine.sets.ecl.Ecl.charm
import arcanum.engine.sets.ecl.Ecl.elemental
import arcanum.engine.sets.ecl.Ecl.elf
import arcanum.engine.sets.ecl.Ecl.elfMatron
import arcanum.engine.sets.ecl.Ecl.faerie
import arcanum.engine.sets.ecl.Ecl.giant
import arcanum.engine.sets.ecl.Ecl.goblin
import arcanum.engine.sets.ecl.Ecl.jolt
import arcanum.engine.sets.ecl.Ecl.kithkin
import arcanum.engine.sets.ecl.Ecl.ogre
import arcanum.engine.sets.ecl.Ecl.redBear
import arcanum.engine.sets.ecl.Ecl.totem
import kotlin.test.Test
import kotlin.test.assertEquals

/** Lorwyn Eclipsed's red and green cards in play (chunks 13–14). */
class EclRedAndGreenScenariosTest {
    private val highcragAssailant = Cards.invent("Highcrag Assailant", "{3}{R}{R}", "Creature — Giant Warrior", """
        Double strike
        Other Giants you control have double strike.
    """, "2/5")
    private val bonemaulFrenzier = Cards.invent("Bonemaul Frenzier", "{3}{R}", "Creature — Goblin Berserker", "This creature gets +2/+0 for each other Goblin you control.", "2/4")
    private val rockslideRush = Cards.invent("Rockslide Rush", "{1}{R}", "Sorcery", "Rockslide Rush deals 2 damage to any target and 1 damage to any other target.")
    private val lastFlameInsight = Cards.invent("Last-Flame Insight", "{X}{R}", "Instant", "Last-Flame Insight deals X damage to target creature. When that creature dies this turn, exile a number of cards from the top of your library equal to its power, then choose a card exiled this way. Until the end of your next turn, you may play that card.")
    private val furiousFirecaller = Cards.invent("Furious Firecaller", "{2}{R}", "Creature — Elemental Sorcerer", """
        Reach
        Whenever you cast a spell with mana value 4 or greater, this creature deals 2 damage to each opponent.
    """, "3/2")
    private val stokeThePyre = Cards.invent("Stoke the Pyre", "{3}{R}", "Instant", "Stoke the Pyre deals 5 damage to target creature. If that creature would die this turn, exile it instead.")
    private val fireLinkBruiser = Cards.invent("Fire-Link Bruiser", "{1}{R}", "Creature — Elemental Warrior", "{1}{R}: This creature gets +1/+0 and gains menace until end of turn. (It can't be blocked except by two or more creatures.)", "2/2")
    private val flameplaiter = Cards.invent("Flameplaiter", "{1}{R}", "Creature — Elemental Bard", "{T}: Add two mana in any combination of colors. Spend this mana only to cast Elemental spells or activate abilities of Elemental sources.", "2/2")
    private val titanfallBlow = Cards.invent("Titanfall Blow", "{1}{R}", "Instant", """
        Choose one —
        • Target creature you control deals damage equal to its power to target creature an opponent controls.
        • Destroy target artifact.
    """)
    private val goatgrab = Cards.invent("Goatgrab", "{2}{R}", "Sorcery", "Gain control of target creature until end of turn. Untap that creature. It gains haste until end of turn. If that creature is a Goat, it also gets +3/+0 until end of turn.")
    private val jinxingSilencer = Cards.invent("Jinxing Silencer", "{1}{R}", "Creature — Goblin Sorcerer", """
        This spell can't be countered.
        Ward—Pay 2 life.
        Spells you control can't be countered.
        Other creatures you control have "Ward—Pay 2 life."
    """, "2/2")
    private val rudeArrival = Cards.invent("Rude Arrival", "{R}", "Sorcery", """
        Target creature gains trample and haste until end of turn.
        Draw a card.
    """)
    private val emberkinFanatic = Cards.invent("Emberkin Fanatic", "{5}{R}", "Creature — Elemental Warrior", """
        When this creature enters, exile the top card of your library. Until the end of your next turn, you may play that card.
        Basic landcycling {1}{R} ({1}{R}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
    """, "6/5")
    private val lingeringPitchblaze = Cards.invent("Lingering Pitchblaze", "{1}{R}", "Enchantment", "At the beginning of each end step, if you put a counter on a creature this turn, this enchantment deals 2 damage to each opponent.")
    private val magmavaulter = Cards.invent("Magmavaulter", "{3}{R}", "Creature — Elemental", """
        All creatures have haste.
        Whenever a player taps a basic land for mana, that player adds one mana of any type that land produced.
    """, "4/4")
    private val timidAssault = Cards.invent("Timid Assault", "{2}{R}", "Enchantment", "{1}{R}: You may put a creature card with total power and toughness 5 or less from your hand onto the battlefield. That creature gains haste. At the beginning of the next end step, sacrifice that creature.")
    private val heedlessPlundering = Cards.invent("Heedless Plundering", "{1}{R}", "Instant", """
        Target creature gets +3/+2 until end of turn. Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
    """)
    private val pinchBlast = Cards.invent("Pinch Blast", "{2}{R}", "Instant", "Pinch Blast deals 3 damage to any target. You may discard a card. If you do, draw a card.")
    private val declareFlawlessness = Cards.invent("Declare Flawlessness", "{1}{G}", "Sorcery", "Target creature you control gets +1/+0 until end of turn. It deals damage equal to its power to up to one target creature an opponent controls.")
    private val burgeoningShield = Cards.invent("Burgeoning Shield", "{G}", "Instant", "Target creature you control gets +2/+2 and gains hexproof until end of turn. (It can't be the target of spells or abilities your opponents control.)")
    private val spinebaneRunner = Cards.invent("Spinebane Runner", "{3}{G}", "Creature — Kithkin Knight", """
        This creature can't be blocked by creatures with power 2 or less.
        As long as another creature entered the battlefield under your control this turn, this creature gets +2/+0.
    """, "3/5")
    private val waymeetSentinel = Cards.invent("Waymeet Sentinel", "{2}{G}", "Creature — Kithkin Ranger", """
        Trample
        Whenever another creature you control enters, this creature gets +1/+0 until end of turn.
    """, "3/3")
    private val hillmootWeaver = Cards.invent("Hillmoot Weaver", "{1}{G}", "Creature — Kithkin Druid", "When this creature enters, if you control three or more creatures, return target permanent card from your graveyard to your hand.", "2/1")
    private val goldLeafsClasp = Cards.invent("Gold-Leaf's Clasp", "{2}{G}", "Enchantment — Aura", """
        Flash
        Enchant creature
        When this Aura enters, enchanted creature gains trample and indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it. If its toughness is 0 or less, it still dies.)
        Enchanted creature gets +2/+0.
    """)
    private val lysValenLookout = Cards.invent("Lys Valen Lookout", "{1}{G}", "Creature — Elf Scout", "When this creature enters or dies, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)", "3/1")
    private val moonlitFurrowing = Cards.invent("Moonlit Furrowing", "{1}{G}", "Instant", "Mill four cards, then you may return a permanent card from among them to your hand. (To mill four cards, put the top four cards of your library into your graveyard.)")
    private val fogvaleAssembly = Cards.invent("Fogvale Assembly", "{4}{G}", "Creature — Kithkin Advisor", """
        This spell costs {1} less to cast if you control a Kithkin.
        When this creature enters, draw a card.
    """, "4/3")
    private val moonWatchDevotees = Cards.invent("Moon-Watch Devotees", "{2}{G}{G}", "Creature — Elf Druid", """
        Trample
        This creature gets +1/+1 for each creature you control and each creature card in your graveyard.
    """, "0/0")
    private val velsynsGaze = Cards.invent("Velsyn's Gaze", "{1}{G}", "Kindred Enchantment — Elf", """
        At the beginning of your upkeep, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        {4}{G}{G}, Sacrifice this enchantment: Create X 2/2 black and green Elf creature tokens, where X is the number of Elf cards in your graveyard. Activate only as a sorcery.
    """)
    private val mercilessKnuckles = Cards.invent("Merciless Knuckles", "{3}{G}", "Enchantment — Aura", """
        Enchant creature you control
        When this Aura enters, enchanted creature fights up to one target creature an opponent controls. (Each deals damage equal to its power to the other.)
        Enchanted creature gets +2/+2.
    """)
    private val bruiserForCoin = Cards.invent("Bruiser for Coin", "{4}{G}", "Creature — Giant Mercenary", """
        Reach, vigilance
        Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
        When this creature enters, you gain X life, where X is the greatest power among Giants you control.
    """, "4/4")
    private val keeperOutriders = Cards.invent("Keeper Outriders", "{3}{G}", "Creature — Elf Warrior", """
        This creature can't be blocked by more than one creature.
        {5}: Target Elf you control gets +2/+2 until end of turn.
    """, "4/4")
    private val seedlingGrove = Cards.invent("Seedling Grove", "{6}{G}{G}", "Enchantment", """
        Affinity for Forests (This spell costs {1} less to cast for each Forest you control.)
        Landfall — Whenever a land you control enters, create a 3/4 green Treefolk creature token with reach.
        {1}{G}, Exile this enchantment: Treefolk and Forests you control gain indestructible until end of turn.
    """)
    private val glimmerwoodBloom = Cards.invent("Glimmerwood Bloom", "{1}{G}", "Enchantment — Aura", """
        Enchant land
        As this Aura enters, choose a color.
        Enchanted land is the chosen color.
        Whenever enchanted land is tapped for mana, its controller adds an additional one mana of the chosen color.
    """)
    private val nimbleAndHale = Cards.invent("Nimble and Hale", "{4}{G}", "Sorcery", "Choose exactly two creatures you control. You draw X cards and the chosen creatures get +X/+X and gain trample until end of turn, where X is the difference between the chosen creatures' powers.")
    private val gruffShoer = Cards.invent("Gruff Shoer", "{1}{G}", "Creature — Kithkin Citizen", "{T}: Target creature you control gets +1/+1 and gains vigilance until end of turn. Activate only as a sorcery.", "2/2")
    private val nurtureTheShoots = Cards.invent("Nurture the Shoots", "{2}{G}", "Sorcery", "Search your library for a basic land card, put it onto the battlefield tapped, then shuffle. Then if you control seven or more lands and/or Treefolk, create a 3/4 green Treefolk creature token with reach. (It can block creatures with flying.)")
    private val mindweaveRush = Cards.invent("Mindweave Rush", "{1}{G}", "Instant", "Target creature gets +3/+3 until end of turn. If a creature entered the battlefield under your control this turn, draw a card.")
    private val relentlessShot = Cards.invent("Relentless Shot", "{2}{G}", "Instant", """
        Choose one —
        • Destroy target creature with flying.
        • Destroy target enchantment.
        • Create a 2/2 black and green Elf creature token.
    """)
    private val vinebornScrapper = Cards.invent("Vineborn Scrapper", "{2}{G}", "Creature — Elf Berserker", """
        This creature must be blocked if able.
        Whenever this creature attacks, another target Elf you control gets +2/+1 until end of turn.
    """, "4/2")
    private val venomousEnvoy = Cards.invent("Venomous Envoy", "{G}", "Creature — Elf Assassin", """
        Deathtouch
        Whenever another creature you control enters, you gain 1 life.
    """, "1/1")
    private val blightRot = Cards.invent("Canker Decay", "{2}{B}", "Instant", "Put four -1/-1 counters on target creature.")

    private fun Scenario.targets(vararg names: String) {
        if (pending is Decision.Targets) answer(Answer.Targets(names.map { target(it) }))
    }

    @Test
    fun highcragAssailantGivesGiantsDoubleStrike() = scenario {
        alice.battlefield(highcragAssailant, giant, ogre)
        expect {
            giant hasKeyword Keyword.DoubleStrike
            ogre lacksKeyword Keyword.DoubleStrike
        }
    }

    @Test
    fun bonemaulFrenzierCountsGoblins() = scenario {
        alice.battlefield(bonemaulFrenzier, goblin, goblin)
        expect { bonemaulFrenzier.power shouldBe 6 }
    }

    // CR 115.3: two different targets
    @Test
    fun rockslideRushSplitsDamage() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(rockslideRush)
        bob.battlefield(elf)
        alice.casts(rockslideRush, targets = listOf("bob", elf))
        resolveAll()
        assertEquals(18, bob.life)
        expect { bob.graveyard has elf }
    }

    // CR 603.7c: when the creature dies this turn, cards equal to its power exiled; one chosen is playable
    @Test
    fun lastFlameInsightExilesForTheDeadCreature() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(lastFlameInsight)
        alice.library("Forest", bear, ogre)
        bob.battlefield(redBear)
        alice.casts(lastFlameInsight, target = redBear, x = 2)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Forest")
        resolveAll()
        expect {
            bob.graveyard has redBear
            alice.exile has "Forest"
            alice.exile has bear
            alice.library has ogre
        }
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
    }

    @Test
    fun furiousFirecallerOnABigSpell() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", furiousFirecaller)
        alice.hand(bigSpell)
        alice.library(bear, bear)
        alice.casts(bigSpell)
        resolveAll()
        assertEquals(18, bob.life)
    }

    // CR 614.1a: it would die: exiled instead
    @Test
    fun stokeThePyreExiles() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(stokeThePyre)
        bob.battlefield(ogre)
        alice.casts(stokeThePyre, target = ogre)
        resolveAll()
        expect { bob.exile has ogre }
    }

    @Test
    fun fireLinkBruiserGainsMenace() = scenario {
        alice.battlefield("Mountain", "Mountain", fireLinkBruiser)
        alice.activates(fireLinkBruiser)
        resolveAll()
        expect {
            fireLinkBruiser.power shouldBe 3
            fireLinkBruiser hasKeyword Keyword.Menace
        }
    }

    // CR 106.6: the restricted mana casts an Elemental spell
    @Test
    fun flameplaiterPaysForAnElemental() = scenario {
        alice.battlefield(flameplaiter)
        alice.hand(elemental)
        alice.activates(flameplaiter, color = Color.Red)
        expect { alice.pool.size shouldBe 2 }
        alice.casts(elemental)
        resolveAll()
        expect { alice.battlefield has elemental }
    }

    @Test
    fun flameplaiterManaCantCastOthers() = scenario {
        alice.battlefield(flameplaiter)
        alice.hand(redBear)
        alice.activates(flameplaiter, color = Color.Red)
        // CR 106.6: no payment can be found from that mana
        alice.casts(redBear)
        expect { (pending as? Decision.ManaPayment)?.suggestion.orEmpty() shouldBe emptyList() }
    }

    @Test
    fun titanfallBlowBites() = scenario {
        alice.battlefield("Mountain", "Mountain", ogre)
        alice.hand(titanfallBlow)
        bob.battlefield(redBear)
        alice.casts(titanfallBlow, modes = listOf(0), targets = listOf(ogre, redBear))
        resolveAll()
        expect {
            bob.graveyard has redBear
            ogre.view.obj?.damage shouldBe 0
        }
    }

    // CR 613.1b: control until end of turn; untapped, hasty
    @Test
    fun goatgrabStealsForATurn() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(goatgrab)
        bob.battlefield(ogre, tapped = true)
        alice.casts(goatgrab, target = ogre)
        resolveAll()
        expect {
            ogre.controller shouldBe alice.id
            ogre.tapped shouldBe false
            ogre hasKeyword Keyword.Haste
            ogre.power shouldBe 3
        }
    }

    // CR 702.21a: ward given to other creatures: the opponent pays 2 life or the spell is countered
    @Test
    fun jinxingSilencerWardsOtherCreatures() = scenario {
        alice.battlefield(jinxingSilencer, bear)
        bob.battlefield("Mountain")
        bob.hand(jolt)
        turn(active = bob)
        bob.casts(jolt, target = bear)
        resolveAll()
        say(true)
        resolveAll()
        assertEquals(18, bob.life)
        expect { alice.graveyard has bear }
    }

    @Test
    fun jinxingSilencerWardCountersWhenUnpaid() = scenario {
        alice.battlefield(jinxingSilencer, bear)
        bob.battlefield("Mountain")
        bob.hand(jolt)
        turn(active = bob)
        bob.casts(jolt, target = bear)
        resolveAll()
        say(false)
        resolveAll()
        expect {
            alice.battlefield has bear
            bob.graveyard has jolt
        }
    }

    @Test
    fun rudeArrivalHastesAndDraws() = scenario {
        alice.battlefield("Mountain")
        alice.battlefield(ogre, sick = true)
        alice.hand(rudeArrival)
        alice.library(bear)
        alice.casts(rudeArrival, target = ogre)
        resolveAll()
        expect {
            ogre hasKeyword Keyword.Haste
            ogre hasKeyword Keyword.Trample
            alice.hand has bear
        }
    }

    @Test
    fun emberkinFanaticExilesAPlayableCard() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(emberkinFanatic)
        alice.library("Forest", bear)
        alice.casts(emberkinFanatic)
        resolveAll()
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
    }

    // CR 603.4: only if you put a counter on a creature this turn
    @Test
    fun lingeringPitchblazeAfterACounter() = scenario {
        alice.library(bear)
        bob.library(bear)
        alice.battlefield("Swamp", "Swamp", "Swamp", lingeringPitchblaze)
        alice.hand(blightRot)
        bob.battlefield(Ecl.bigElemental)
        alice.casts(blightRot, target = Ecl.bigElemental)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        assertEquals(18, bob.life)
    }

    @Test
    fun lingeringPitchblazeWithoutACounter() = scenario {
        alice.library(bear)
        bob.library(bear)
        alice.battlefield(lingeringPitchblaze)
        advanceTo(TurnStep.End)
        resolveAll()
        assertEquals(20, bob.life)
    }

    // CR 106.12a: a basic land tapped for mana adds one more
    @Test
    fun magmavaulterDoublesBasicLands() = scenario {
        alice.battlefield("Forest", magmavaulter)
        alice.battlefield(bear, sick = true)
        alice.activates("Forest")
        resolveAll()
        expect {
            alice.pool.size shouldBe 2
            bear hasKeyword Keyword.Haste
        }
    }

    // CR 603.7: put in with haste, sacrificed at the next end step
    @Test
    fun timidAssaultPutsInAHastyCreature() = scenario {
        alice.library(bear)
        bob.library(bear)
        alice.battlefield("Mountain", "Mountain", timidAssault)
        alice.hand(bear, ogre)
        alice.activates(timidAssault)
        resolveAll()
        say(true)
        // the ogre's power and toughness total 6: only the bear may be put in
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        expect {
            alice.battlefield has bear
            bear hasKeyword Keyword.Haste
            alice.hand has ogre
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.graveyard has bear }
    }

    @Test
    fun heedlessPlunderingPumpsAndMakesTreasure() = scenario {
        alice.battlefield("Mountain", "Mountain", bear)
        alice.hand(heedlessPlundering)
        alice.casts(heedlessPlundering, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 5
            alice.battlefield has "Treasure Token"
        }
    }

    @Test
    fun pinchBlastBurnsAndRummages() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(pinchBlast, ogre)
        alice.library(bear)
        alice.casts(pinchBlast, target = "bob")
        resolveAll()
        say(true)
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        assertEquals(17, bob.life)
        expect { alice.hand has bear }
    }

    // CR 120.3: the pumped creature deals damage equal to its power (one-sided)
    @Test
    fun declareFlawlessnessBites() = scenario {
        alice.battlefield("Forest", "Forest", bear)
        alice.hand(declareFlawlessness)
        bob.battlefield(ogre)
        alice.casts(declareFlawlessness, targets = listOf(bear, ogre))
        resolveAll()
        expect {
            bob.graveyard has ogre
            bear.view.obj?.damage shouldBe 0
        }
    }

    @Test
    fun burgeoningShieldProtects() = scenario {
        alice.battlefield("Forest", bear)
        alice.hand(burgeoningShield)
        alice.casts(burgeoningShield, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Hexproof
        }
    }

    @Test
    fun spinebaneRunnerAfterAnArrival() = scenario {
        alice.battlefield("Forest", spinebaneRunner)
        alice.hand(elf)
        expect { spinebaneRunner.power shouldBe 3 }
        alice.casts(elf)
        resolveAll()
        expect {
            spinebaneRunner.power shouldBe 5
            spinebaneRunner hasMechanic { it is Mechanic.CantBeBlockedBy }
        }
    }

    @Test
    fun waymeetSentinelGrowsOnArrivals() = scenario {
        alice.battlefield("Forest", waymeetSentinel)
        alice.hand(elf)
        alice.casts(elf)
        resolveAll()
        expect { waymeetSentinel.power shouldBe 4 }
    }

    // CR 603.4: three creatures: a permanent card returns
    @Test
    fun hillmootWeaverWithThreeCreatures() = scenario {
        alice.battlefield("Forest", "Forest", bear, elf)
        alice.hand(hillmootWeaver)
        alice.graveyard(totem)
        alice.casts(hillmootWeaver)
        resolveAll()
        targets(totem)
        resolveAll()
        expect { alice.hand has totem }
    }

    @Test
    fun goldLeafsClaspGivesTrampleAndIndestructible() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", bear)
        alice.hand(goldLeafsClasp)
        alice.casts(goldLeafsClasp, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Indestructible
            bear hasKeyword Keyword.Trample
        }
    }

    @Test
    fun lysValenLookoutSurveilsOnEnteringAndDying() = scenario {
        alice.battlefield("Forest", "Forest", "Mountain")
        alice.hand(lysValenLookout, jolt)
        alice.library(ogre, bear)
        alice.casts(lysValenLookout)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        expect { alice.graveyard has ogre }
        alice.casts(jolt, target = lysValenLookout)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        expect { alice.graveyard has bear }
    }

    @Test
    fun moonlitFurrowingReturnsAPermanentCard() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(moonlitFurrowing)
        alice.library(jolt, ogre, bear, totem, elf)
        alice.casts(moonlitFurrowing)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        expect {
            alice.hand has ogre
            alice.graveyard has jolt
            alice.library has elf
        }
    }

    @Test
    fun fogvaleAssemblyCostsLessWithAKithkin() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", kithkin)
        alice.hand(fogvaleAssembly)
        alice.library(bear)
        alice.casts(fogvaleAssembly)
        resolveAll()
        expect {
            alice.battlefield has fogvaleAssembly
            alice.hand has bear
        }
    }

    // CR 604.3: counts itself among the creatures you control
    @Test
    fun moonWatchDevoteesCountsCreatures() = scenario {
        alice.battlefield(moonWatchDevotees, bear)
        alice.graveyard(ogre, totem)
        expect { moonWatchDevotees.power shouldBe 3 }
    }

    @Test
    fun velsynsGazeMakesElves() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", velsynsGaze)
        alice.graveyard(elf, elfMatron, ogre)
        alice.activates(velsynsGaze)
        resolveAll()
        // CR 608.2h: counted as it resolves: the sacrificed Kindred Elf enchantment is an Elf card in the graveyard too
        expect { alice.battlefield.names.count { it == "Elf Token" } shouldBe 3 }
    }

    @Test
    fun velsynsGazeSurveilsInTheUpkeep() = scenario {
        alice.library(bear, ogre, elf)
        bob.library(bear)
        alice.battlefield(velsynsGaze)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        expect { alice.graveyard has bear }
    }

    // CR 701.14a: the enchanted creature, now 4/4, fights
    @Test
    fun mercilessKnucklesFights() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", bear)
        alice.hand(mercilessKnuckles)
        bob.battlefield(ogre)
        alice.casts(mercilessKnuckles, target = bear)
        resolveAll()
        targets(ogre)
        resolveAll()
        expect {
            bob.graveyard has ogre
            alice.battlefield has bear
            bear.power shouldBe 4
        }
    }

    @Test
    fun bruiserForCoinGainsByGiants() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", giant)
        alice.hand(bruiserForCoin)
        alice.casts(bruiserForCoin)
        resolveAll()
        assertEquals(24, alice.life)
    }

    @Test
    fun keeperOutridersPumpsAnElf() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", keeperOutriders, elf)
        alice.activates(keeperOutriders, target = elf)
        resolveAll()
        expect {
            elf.power shouldBe 3
            keeperOutriders hasMechanic { it is Mechanic.MaximumBlockers }
        }
    }

    // CR 702.41a: affinity for Forests; landfall makes a Treefolk
    @Test
    fun seedlingGroveCheapWithForests() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(seedlingGrove, "Forest")
        alice.casts(seedlingGrove)
        resolveAll()
        expect {
            alice.battlefield has seedlingGrove
            card("Forest", 2).tapped shouldBe false
        }
        alice.plays("Forest")
        resolveAll()
        expect { alice.battlefield has "Treefolk Token" }
    }

    // CR 106.12a: the enchanted land makes an additional mana of the chosen colour
    @Test
    fun glimmerwoodBloomAddsAColor() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(glimmerwoodBloom)
        alice.casts(glimmerwoodBloom, target = "Forest", paying = listOf("Forest#1", "Forest#2"))
        resolveAll()
        if (pending is Decision.ChooseColor) alice.answers(Answer.ColorChoice(Color.Red))
        resolveAll()
        expect { card("Forest", 0).colors shouldBe listOf(Color.Red) }
        alice.activates("Forest")
        resolveAll()
        expect { alice.pool.map { it.color }.toSet() shouldBe setOf(Color.Green, Color.Red) }
    }

    // X is the difference between the two powers
    @Test
    fun nimbleAndHaleDrawsTheDifference() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", ogre, elf)
        alice.hand(nimbleAndHale)
        alice.library(bear, bear, bear)
        alice.casts(nimbleAndHale)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(ogre, elf)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            ogre.power shouldBe 5
            elf hasKeyword Keyword.Trample
        }
    }

    @Test
    fun gruffShoerPumps() = scenario {
        alice.battlefield(gruffShoer, bear)
        alice.activates(gruffShoer, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Vigilance
        }
    }

    // seven lands with the found one: a Treefolk
    @Test
    fun nurtureTheShootsFindsALandAndATreefolk() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(nurtureTheShoots)
        alice.library("Mountain", bear)
        alice.casts(nurtureTheShoots)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Mountain")
        resolveAll()
        expect {
            alice.battlefield has "Mountain"
            "Mountain".tapped shouldBe true
            alice.battlefield has "Treefolk Token"
        }
    }

    @Test
    fun mindweaveRushDrawsAfterAnArrival() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(elf, mindweaveRush)
        alice.library(bear)
        alice.casts(elf)
        resolveAll()
        alice.casts(mindweaveRush, target = elf)
        resolveAll()
        expect {
            elf.power shouldBe 4
            alice.hand has bear
        }
    }

    @Test
    fun relentlessShotModes() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(relentlessShot, relentlessShot)
        bob.battlefield(faerie)
        alice.casts(relentlessShot, modes = listOf(0), target = faerie)
        resolveAll()
        alice.casts(relentlessShot, modes = listOf(2))
        resolveAll()
        expect {
            bob.graveyard has faerie
            alice.battlefield has "Elf Token"
        }
    }

    // CR 509.1c: it must be blocked if able; another Elf gets +2/+1
    @Test
    fun vinebornScrapperPumpsAnElf() = scenario {
        alice.battlefield(vinebornScrapper, elf)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(vinebornScrapper)
        targets(elf)
        resolveAll()
        expect {
            elf.power shouldBe 3
            vinebornScrapper hasMechanic { it is Mechanic.MustBeBlocked }
        }
    }

    @Test
    fun venomousEnvoyGainsOnArrivals() = scenario {
        alice.battlefield("Forest", venomousEnvoy)
        alice.hand(elf)
        alice.casts(elf)
        resolveAll()
        assertEquals(21, alice.life)
    }
}
