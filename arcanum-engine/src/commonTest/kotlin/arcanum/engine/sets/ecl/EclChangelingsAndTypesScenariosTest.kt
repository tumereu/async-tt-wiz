package arcanum.engine.sets.ecl

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ecl.Ecl.bear
import arcanum.engine.sets.ecl.Ecl.elemental
import arcanum.engine.sets.ecl.Ecl.elf
import arcanum.engine.sets.ecl.Ecl.elfMatron
import arcanum.engine.sets.ecl.Ecl.faerie
import arcanum.engine.sets.ecl.Ecl.goblin
import arcanum.engine.sets.ecl.Ecl.jolt
import arcanum.engine.sets.ecl.Ecl.m1m1
import arcanum.engine.sets.ecl.Ecl.ogre
import arcanum.engine.sets.ecl.Ecl.redBear
import kotlin.test.Test
import kotlin.test.assertEquals

/** Lorwyn Eclipsed's changelings and creature-type cards in play (chunks 5–6): Shapeshifter tokens, all creature types, a clone with
 * changeling, a creature type chosen as a permanent enters or as a spell resolves, and what depends on it. */
class EclChangelingsAndTypesScenariosTest {
    private val changelingPathseeker = Cards.invent("Changeling Pathseeker", "{3}", "Creature — Shapeshifter", """
        Changeling (This card is every creature type.)
        When this creature enters, you may search your library for a basic land card, reveal it, put it into your hand, then shuffle.
    """, "1/2")
    private val gableLurker = Cards.invent("Gable Lurker", "{5}", "Creature — Shapeshifter", """
        Changeling (This card is every creature type.)
        Flying
        When this creature enters, exile up to two target cards from graveyards. You gain 3 life.
    """, "3/3")
    private val cradleTrade = Cards.invent("Cradle Trade", "{2}{W}", "Kindred Instant — Shapeshifter", """
        Changeling (This card is every creature type.)
        Exile target creature. Its controller creates a 1/1 colorless Shapeshifter creature token with changeling.
    """)
    private val coveyPretender = Cards.invent("Covey Pretender", "{2}{W}", "Creature — Shapeshifter", """
        Changeling (This card is every creature type.)
        Flash
        Flying
        When this creature enters, return up to one other target creature you control to its owner's hand.
    """, "2/2")
    private val embodiment = Cards.invent("Embodiment", "{1}{W}", "Instant", "Exile target creature you control, then return that card to the battlefield under its owner's control. Create a 1/1 colorless Shapeshifter creature token with changeling. (It's every creature type.)")
    private val tombshifter = Cards.invent("Tombshifter", "{3}{B}", "Creature — Shapeshifter", """
        Changeling (This card is every creature type.)
        When this creature enters, you may return target creature card from your graveyard to your hand.
    """, "2/2")
    private val searingChangeling = Cards.invent("Searing Changeling", "{2}{R}", "Creature — Shapeshifter", """
        Changeling (This card is every creature type.)
        When this creature dies, exile the top card of your library. Until the end of your next turn, you may play that card.
    """, "3/2")
    private val scrappyBarbling = Cards.invent("Scrappy Barbling", "{1}{R/W}", "Creature — Shapeshifter", """
        Changeling (This card is every creature type.)
        During your turn, this creature has first strike.
    """, "2/1")
    private val mossbrookHeart = Cards.invent("Mossbrook Heart", "{3}", "Kindred Artifact — Shapeshifter", """
        Changeling (This card is every creature type.)
        {T}: Add one mana of any color.
        {4}: This artifact becomes a 4/4 artifact creature until end of turn.
    """)
    private val dripstoneDirk = Cards.invent("Dripstone Dirk", "{2}", "Artifact — Equipment", """
        When this Equipment enters, create a 1/1 colorless Shapeshifter creature token with changeling. (It's every creature type.)
        Equipped creature gets +1/+1 and is all creature types.
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val everyChangeling = Cards.invent("Every-Changeling", "{3}{U}{U}", "Creature — Shapeshifter", """
        Changeling (This card is every creature type.)
        Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
        You may have this creature enter as a copy of any creature on the battlefield, except it has changeling.
    """, "0/0")
    private val cullingSift = Cards.invent("Culling Sift", "{4}{W}{W}", "Sorcery", """
        Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
        For each player, you choose a creature that player controls. Then each player sacrifices all other creatures they control that don't share a creature type with the chosen creature they control.
    """)
    private val glamourGiver = Cards.invent("Glamour Giver", "{1}{U}", "Creature — Faerie Wizard", """
        Flash
        Flying
        When this creature enters, choose up to one other target creature. Until end of turn, that creature has base power and toughness 4/4 and gains all creature types.
    """, "1/2")
    private val chorusedSwell = Cards.invent("Chorused Swell", "{4}{U}{U}", "Instant", """
        Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
        Choose a creature type. Draw a card for each permanent you control of that type.
    """)
    private val noggleTheWits = Cards.invent("Noggle the Wits", "{1}{U}", "Enchantment — Aura", """
        Flash
        Enchant creature
        Enchanted creature loses all abilities and is a colorless Noggle with base power and toughness 1/1. (It loses all colors and all other creature types.)
    """)
    private val kinlineSummons = Cards.invent("Kinline Summons", "{6}{B}{B}", "Sorcery", """
        Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
        Choose a creature type. Return all creature cards of the chosen type from your graveyard to the battlefield.
    """)
    private val gatheredBlaze = Cards.invent("Gathered Blaze", "{3}{R}{R}", "Enchantment", """
        Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
        As this enchantment enters, choose a creature type.
        Double all damage that sources you control of the chosen type would deal.
    """)
    private val devotedKeeper = Cards.invent("Devoted Keeper", "{3}{G}{G}", "Creature — Elf Warrior", """
        Flash
        Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
        When this creature enters, choose a creature type. Other permanents you control of that type gain hexproof and indestructible until end of turn.
    """, "4/2")
    private val annalsOfTriumph = Cards.invent("Annals of Triumph", "{6}", "Legendary Artifact", """
        As Annals of Triumph enters, choose a creature type.
        Creatures you control of the chosen type get +2/+2 and have first strike and trample.
        Whenever you cast a spell of the chosen type, draw a card.
    """)
    private val musteringStone = Cards.invent("Mustering Stone", "{4}", "Artifact", """
        As this artifact enters, choose a creature type.
        Spells you cast of the chosen type cost {1} less to cast.
        When this artifact enters and at the beginning of your upkeep, look at the top card of your library. If it's a card of the chosen type, you may reveal it and put it into your hand. If you don't put the card into your hand, you may put it into your graveyard.
    """)
    private val mornTouchedBanner = Cards.invent("Morn-Touched Banner", "{1}", "Artifact", """
        As this artifact enters, choose Elemental, Elf, Faerie, Giant, Goblin, Kithkin, Merfolk, or Treefolk.
        Whenever a permanent you control of the chosen type enters, you gain 1 life.
        {2}, {T}, Sacrifice this artifact: Return target card of the chosen type from your graveyard to your hand.
    """)

    private fun Scenario.chooseType(name: String) {
        if (pending is Decision.ChooseSubtype) answer(Answer.SubtypeChoice(Subtype(name)))
    }

    private fun Scenario.targets(vararg names: String) {
        if (pending is Decision.Targets) answer(Answer.Targets(names.map { target(it) }))
    }

    // CR 701.23: a basic land card found and put into the hand
    @Test
    fun changelingPathseekerFindsABasicLand() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(changelingPathseeker)
        alice.library(bear, "Mountain")
        alice.casts(changelingPathseeker)
        resolveAll()
        say(true)
        if (pending is Decision.ChooseObjects) alice.chooses("Mountain")
        resolveAll()
        expect {
            alice.hand has "Mountain"
            changelingPathseeker.view.characteristics?.hasSubtype(Subtype("Goblin")) shouldBe true
        }
    }

    @Test
    fun gableLurkerExilesFromGraveyards() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(gableLurker)
        bob.graveyard(bear, ogre)
        alice.casts(gableLurker)
        resolveAll()
        targets(bear, ogre)
        resolveAll()
        expect {
            bob.exile has bear
            bob.exile has ogre
        }
        assertEquals(23, alice.life)
    }

    // CR 608.2h: the exiled creature's controller gets the token
    @Test
    fun cradleTradeGivesAShapeshifter() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(cradleTrade)
        bob.battlefield(ogre)
        alice.casts(cradleTrade, target = ogre)
        resolveAll()
        expect {
            bob.exile has ogre
            bob.battlefield has "Shapeshifter Token"
            "Shapeshifter Token" hasKeyword Keyword.Changeling
        }
    }

    @Test
    fun coveyPretenderReturnsACreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", bear)
        alice.hand(coveyPretender)
        alice.casts(coveyPretender)
        resolveAll()
        targets(bear)
        resolveAll()
        expect { alice.hand has bear }
    }

    // CR 400.7: the returned card is a new object without its counters
    @Test
    fun embodimentBlinksAndMakesAToken() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.battlefield(ogre, counters = listOf(m1m1 to 2))
        alice.hand(embodiment)
        alice.casts(embodiment, target = ogre)
        resolveAll()
        expect {
            alice.battlefield has ogre
            ogre.counters(m1m1) shouldBe 0
            alice.battlefield has "Shapeshifter Token"
        }
    }

    @Test
    fun tombshifterReturnsACreatureCard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(tombshifter)
        alice.graveyard(ogre)
        alice.casts(tombshifter)
        resolveAll()
        targets(ogre)
        resolveAll()
        say(true)
        resolveAll()
        expect { alice.hand has ogre }
    }

    @Test
    fun searingChangelingExilesAPlayableCard() = scenario {
        alice.battlefield("Mountain", searingChangeling)
        alice.hand(jolt)
        alice.library("Forest", bear)
        alice.casts(jolt, target = searingChangeling)
        resolveAll()
        expect { alice.exile has "Forest" }
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
    }

    // CR 611.3a: first strike only during its controller's turn
    @Test
    fun scrappyBarblingFirstStrikeOnYourTurn() = scenario {
        alice.battlefield(scrappyBarbling)
        expect { scrappyBarbling hasKeyword Keyword.FirstStrike }
        turn(active = bob)
        expect { scrappyBarbling lacksKeyword Keyword.FirstStrike }
    }

    // CR 205.1b: "becomes an artifact creature" keeps its other types (Kindred) and creature types
    @Test
    fun mossbrookHeartBecomesACreature() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", mossbrookHeart)
        alice.activates(mossbrookHeart, ability = 1)
        resolveAll()
        expect {
            mossbrookHeart.power shouldBe 4
            mossbrookHeart.types shouldBe listOf(CardType.Kindred, CardType.Artifact, CardType.Creature)
            mossbrookHeart.view.characteristics?.hasSubtype(Subtype("Elf")) shouldBe true
        }
    }

    @Test
    fun dripstoneDirkMakesAChangelingAndEquips() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(dripstoneDirk)
        alice.casts(dripstoneDirk)
        resolveAll()
        expect { alice.battlefield has "Shapeshifter Token" }
        alice.activates(dripstoneDirk, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear.view.characteristics?.hasSubtype(Subtype("Merfolk")) shouldBe true
        }
    }

    // CR 707.9b: a copy with changeling
    @Test
    fun everyChangelingCopiesACreature() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(everyChangeling)
        bob.battlefield(ogre)
        alice.casts(everyChangeling)
        resolveAll()
        say(true)
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        expect {
            card("Every-Changeling").view.name shouldBe "Plaza Ogre"
            card("Every-Changeling").view.characteristics?.power shouldBe 3
            card("Every-Changeling") hasKeyword Keyword.Changeling
        }
    }

    // CR 701.21a: each player keeps the creatures sharing a type with the one chosen for them
    @Test
    fun cullingSiftKeepsOneType() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", elf, elfMatron, ogre)
        alice.hand(cullingSift)
        bob.battlefield(bear, redBear, goblin)
        alice.casts(cullingSift, paying = listOf("Plains", "Plains", "Plains", "Plains", "Plains", "Plains"))
        resolveAll()
        var guard = 4
        while (pending is Decision.ChooseObjects && guard-- > 0) {
            val d = pending as Decision.ChooseObjects
            alice.chooses(if (d.options.any { state.obj(it)?.owner == alice.id }) elf else bear)
        }
        resolveAll()
        expect {
            alice.battlefield has elfMatron
            alice.graveyard has ogre
            bob.battlefield has redBear
            bob.graveyard has goblin
        }
    }

    // CR 613.4b, 613.1d: base 4/4 and all creature types until end of turn
    @Test
    fun glamourGiverMakesA44OfAllTypes() = scenario {
        alice.battlefield("Island", "Island", bear)
        alice.hand(glamourGiver)
        alice.casts(glamourGiver)
        resolveAll()
        targets(bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear.view.characteristics?.hasSubtype(Subtype("Giant")) shouldBe true
        }
    }

    @Test
    fun chorusedSwellDrawsForEachOfAType() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", elf, elfMatron, ogre)
        alice.hand(chorusedSwell)
        alice.library(bear, bear, bear)
        alice.casts(chorusedSwell, paying = listOf("Island", "Island", "Island", "Island", "Island", "Island"))
        resolveAll()
        chooseType("Elf")
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    // CR 613.1d–f: no abilities, colourless, a Noggle 1/1
    @Test
    fun noggleTheWitsMakesANoggle() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(noggleTheWits)
        bob.battlefield(faerie, ogre)
        alice.casts(noggleTheWits, target = ogre)
        resolveAll()
        expect {
            ogre.power shouldBe 1
            ogre.subtypes shouldBe listOf(Subtype("Noggle"))
            ogre.colors shouldBe emptyList()
        }
    }

    @Test
    fun kinlineSummonsReturnsAType() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(kinlineSummons)
        alice.graveyard(elf, elfMatron, ogre)
        alice.casts(kinlineSummons)
        resolveAll()
        chooseType("Elf")
        resolveAll()
        expect {
            alice.battlefield has elf
            alice.battlefield has elfMatron
            alice.graveyard has ogre
        }
    }

    // CR 614.1a: the damage of an Elemental of yours is doubled
    @Test
    fun gatheredBlazeDoublesDamage() = scenario {
        alice.library(bear)
        bob.library(bear)
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", elemental)
        alice.hand(gatheredBlaze)
        alice.casts(gatheredBlaze)
        resolveAll()
        chooseType("Elemental")
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(elemental)
        advanceTo(TurnStep.EndOfCombat)
        assertEquals(16, bob.life)
    }

    @Test
    fun devotedKeeperProtectsAType() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", elf, ogre)
        alice.hand(devotedKeeper)
        alice.casts(devotedKeeper)
        resolveAll()
        chooseType("Elf")
        resolveAll()
        expect {
            elf hasKeyword Keyword.Indestructible
            elf hasKeyword Keyword.Hexproof
            ogre lacksKeyword Keyword.Indestructible
            devotedKeeper lacksKeyword Keyword.Indestructible
        }
    }

    @Test
    fun annalsOfTriumphPumpsAndDraws() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", elf)
        alice.hand(annalsOfTriumph, elfMatron)
        alice.library(bear)
        alice.casts(annalsOfTriumph, paying = listOf("Forest", "Forest", "Forest", "Forest", "Forest", "Forest"))
        resolveAll()
        chooseType("Elf")
        resolveAll()
        expect {
            elf.power shouldBe 3
            elf hasKeyword Keyword.FirstStrike
            elf hasKeyword Keyword.Trample
        }
        alice.casts(elfMatron)
        resolveAll()
        expect { alice.hand has bear }
    }

    // CR 601.2f: an Elf spell costs {1} less; the top card of the chosen type to the hand
    @Test
    fun musteringStoneFindsAndDiscounts() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(musteringStone)
        alice.library(elfMatron, bear)
        alice.casts(musteringStone)
        resolveAll()
        chooseType("Elf")
        resolveAll()
        say(true)
        resolveAll()
        expect { alice.hand has elfMatron }
        alice.casts(elfMatron)
        resolveAll()
        expect { alice.battlefield has elfMatron }
    }

    @Test
    fun mornTouchedBannerGainsAndReturns() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(mornTouchedBanner, elf)
        alice.graveyard(elfMatron)
        alice.casts(mornTouchedBanner)
        resolveAll()
        chooseType("Elf")
        resolveAll()
        alice.casts(elf)
        resolveAll()
        assertEquals(21, alice.life)
        alice.activates(mornTouchedBanner, target = elfMatron)
        resolveAll()
        expect { alice.hand has elfMatron }
    }
}
