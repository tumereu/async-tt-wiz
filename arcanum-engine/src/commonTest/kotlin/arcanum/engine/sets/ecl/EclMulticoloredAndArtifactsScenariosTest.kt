package arcanum.engine.sets.ecl

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ecl.Ecl.bear
import arcanum.engine.sets.ecl.Ecl.bigSpell
import arcanum.engine.sets.ecl.Ecl.elf
import arcanum.engine.sets.ecl.Ecl.elfMatron
import arcanum.engine.sets.ecl.Ecl.goblin
import arcanum.engine.sets.ecl.Ecl.jolt
import arcanum.engine.sets.ecl.Ecl.kithkin
import arcanum.engine.sets.ecl.Ecl.ogre
import kotlin.test.Test
import kotlin.test.assertEquals

/** Lorwyn Eclipsed's multicoloured cards, artifacts and lands in play (chunks 15–16). */
class EclMulticoloredAndArtifactsScenariosTest {
    private val wren = Cards.invent("Wren, Articulate Freshling", "{W/B}{W/B}", "Legendary Creature — Bird Bard", """
        Flying, first strike, lifelink
        When Wren enters, up to one other target creature loses all abilities. Put a flying counter, a first strike counter, and a lifelink counter on that creature.
    """, "1/1")
    private val hobgoblinHexsmith = Cards.invent("Hobgoblin Hexsmith", "{B}{R}", "Creature — Goblin Warlock", """
        Deathtouch
        Whenever another Goblin you control dies, this creature deals 1 damage to each opponent.
    """, "2/3")
    private val haldOfClanBroadhand = Cards.invent("Hald of Clan Broadhand", "{2}{R}{W}", "Legendary Creature — Giant Warrior", """
        {1}{W}, {T}: Another target creature you control gains flying and lifelink until end of turn.
        At the beginning of your end step, if you gained life this turn, exile cards from the top of your library until you exile a nonland card. You may cast that card without paying its mana cost if the spell's mana value is less than or equal to the amount of life you gained this turn. Otherwise, put it into your hand.
    """, "4/4")
    private val oakhart = Cards.invent("Oakhart, Weathered by Ages", "{1}{W}{B}{G}", "Legendary Creature — Treefolk Druid", """
        Each creature spell you cast with toughness greater than its power costs {1} less to cast.
        Whenever a creature you control attacks or blocks, it gets +X/+X until end of turn, where X is the difference between its power and toughness.
    """, "0/5")
    private val shadowedHobgoblin = Cards.invent("Shadowed Hobgoblin", "{B/R}{B/R}{B/R}", "Creature — Goblin Scout", "When this creature enters, look at the top four cards of your library. You may reveal a Goblin, Swamp, or Mountain card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.", "2/3")
    private val shapeOfLegend = Cards.invent("Shape of Legend", "{G/W}", "Creature — Kithkin", """
        {G/W}: This creature becomes a Kithkin Scout with base power and toughness 2/3.
        {1}{G/W}{G/W}: If this creature is a Scout, it becomes a Kithkin Soldier with base power and toughness 4/5.
        {3}{G/W}{G/W}{G/W}: If this creature is a Soldier, it becomes a Kithkin Avatar with base power and toughness 7/8 and protection from each of your opponents.
    """, "1/1")
    private val flickeringEmber = Cards.invent("Flickering Ember", "{1}{U/R}{U/R}", "Creature — Elemental Sorcerer", "When this creature enters and whenever you cast a spell with mana value 4 or greater, you may discard a card. If you do, draw a card.", "3/2")
    private val ferra = Cards.invent("Ferra, Flawed Botanist", "{B/G}{B/G}", "Legendary Creature — Elf Druid", """
        When Ferra enters, mill four cards, then you may put a creature or land card from among the milled cards on top of your library.
        {2}{B/G}{B/G}{B/G}, {T}, Discard a land card: Create a 1/1 black and green Worm creature token for each land card in your graveyard.
    """, "1/3")
    private val velsynsDevotee = Cards.invent("Velsyn's Devotee", "{1}{B}{G}", "Creature — Elf Warrior", """
        Other Elves you control get +1/+1.
        When this creature dies, return another target Elf card from your graveyard to your hand.
    """, "3/2")
    private val nogglePilferer = Cards.invent("Noggle Pilferer", "{1}{R/G}{R/G}", "Creature — Noggle Rogue", """
        When this creature enters or dies, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
    """, "3/3")
    private val mindweaveCaptain = Cards.invent("Mindweave Captain", "{G}{W}", "Creature — Kithkin Soldier", "Whenever this creature or another Kithkin you control enters, target creature you control gets +1/+1 and gains trample until end of turn.", "2/2")
    private val ravenousPageFlicker = Cards.invent("Ravenous Page-Flicker", "{U/B}{U/B}{U/B}", "Creature — Faerie Rogue", """
        Flying
        Whenever you cast a spell during an opponent's turn, you may pay 1 life. If you do, draw a card.
    """, "2/3")
    private val cautiousGrower = Cards.invent("Cautious Grower", "{1}{G/W}{G/W}", "Creature — Kithkin Citizen", "At the beginning of your end step, if another creature entered the battlefield under your control this turn, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)", "3/3")
    private val scavengingStrawmaw = Cards.invent("Scavenging Strawmaw", "{2}", "Artifact Creature — Scarecrow", """
        When this creature enters, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        {1}: Add one mana of any color. This creature becomes that color until end of turn. Activate only once each turn.
    """, "1/3")
    private val pookasGaze = Cards.invent("Pooka's Gaze", "{2}", "Artifact", """
        When this artifact enters, draw a card, then choose a color. This artifact becomes the chosen color.
        {3}, {T}: Draw a card. Activate only if there are five colors among permanents you control.
    """)
    private val shiftingBarrens = Cards.invent("Shifting Barrens", null, "Land", "{T}, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
    private val barkSentry = Cards.invent("Bark Sentry", "{2}{G}", "Creature — Treefolk", "", "1/4")
    private val strandSkulker = Cards.invent("Strand Skulker", "{3}{W}", "Creature — Merfolk Scout", """
        Flying
        When this creature enters, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
    """, "3/3")
    private val scarknifeRanger = Cards.invent("Scarknife Ranger", "{1}{B}", "Creature — Elf Scout", """
        Lifelink
        When this creature enters, mill two cards. (Put the top two cards of your library into your graveyard.)
    """, "2/2")

    // CR 701.25a: surveil 1: the top card may go to the graveyard
    @Test
    fun strandSkulkerSurveils() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(strandSkulker)
        alice.library(ogre, bear)
        alice.casts(strandSkulker)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        expect {
            alice.graveyard has ogre
            alice.library has bear
        }
    }

    // CR 701.17a
    @Test
    fun scarknifeRangerMillsTwo() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(scarknifeRanger)
        alice.library(ogre, bear, elf)
        alice.casts(scarknifeRanger)
        resolveAll()
        expect {
            alice.graveyard has ogre
            alice.graveyard has bear
            alice.library has elf
        }
    }

    private fun Scenario.targets(vararg names: String) {
        if (pending is Decision.Targets) answer(Answer.Targets(names.map { target(it) }))
    }

    // CR 613.1f, 122.1b: abilities lost, then keyword counters give flying, first strike and lifelink
    @Test
    fun wrenRewritesACreature() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(wren)
        bob.battlefield(hobgoblinHexsmith)
        alice.casts(wren)
        resolveAll()
        targets(hobgoblinHexsmith)
        resolveAll()
        expect {
            hobgoblinHexsmith lacksKeyword Keyword.Deathtouch
            hobgoblinHexsmith hasKeyword Keyword.Flying
            hobgoblinHexsmith hasKeyword Keyword.FirstStrike
            hobgoblinHexsmith hasKeyword Keyword.Lifelink
            hobgoblinHexsmith.counters(CounterKind.Keyword(Keyword.Flying.kind)) shouldBe 1
        }
    }

    @Test
    fun hobgoblinHexsmithPingsWhenAGoblinDies() = scenario {
        alice.battlefield("Mountain", hobgoblinHexsmith, goblin)
        alice.hand(jolt)
        alice.casts(jolt, target = goblin)
        resolveAll()
        assertEquals(19, bob.life)
    }

    // CR 601.2a: the exiled card is cast for free when its mana value is at most the life gained this turn
    @Test
    fun haldCastsForFreeAfterGainingLife() = scenario {
        alice.library("Forest", jolt, bear)
        bob.library(bear)
        alice.battlefield("Plains", "Plains", haldOfClanBroadhand, ogre)
        alice.activates(haldOfClanBroadhand, target = ogre)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ogre)
        advanceTo(TurnStep.End)
        resolveAll()
        assertEquals(23, alice.life)
        say(true)
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("bob"))))
        resolveAll()
        expect {
            alice.exile has "Forest"
            alice.graveyard has jolt
        }
        assertEquals(15, bob.life)
    }

    @Test
    fun haldWithoutLifeGainDoesNothing() = scenario {
        alice.library("Forest", jolt, bear)
        bob.library(bear)
        alice.battlefield(haldOfClanBroadhand)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.library has jolt }
    }

    // CR 601.2f: a creature spell with toughness greater than power costs {1} less; attacking, +X/+X for the difference
    @Test
    fun oakhartDiscountsAndPumps() = scenario {
        alice.battlefield("Forest", "Forest", oakhart)
        alice.hand(barkSentry)
        alice.casts(barkSentry)
        resolveAll()
        expect { alice.battlefield has barkSentry }
    }

    @Test
    fun oakhartPumpsAttackers() = scenario {
        alice.battlefield(oakhart, barkSentry)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(barkSentry)
        resolveAll()
        expect {
            barkSentry.power shouldBe 4
            barkSentry.toughness shouldBe 7
        }
    }

    // CR 701.20a: a Goblin, Swamp or Mountain card revealed to the hand; the rest to the bottom
    @Test
    fun shadowedHobgoblinFindsAGoblin() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(shadowedHobgoblin)
        alice.library(bear, goblin, ogre, elf, elfMatron)
        alice.casts(shadowedHobgoblin)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(goblin)
        resolveAll()
        expect { alice.hand has goblin }
        assertEquals(elfMatron, state.obj(state.player(alice.id).library.first())?.definition?.faces?.get(0)?.name)
    }

    // CR 613.1d, 613.4b: a Scout, then a Soldier; the Soldier step needs a Scout
    @Test
    fun shapeOfLegendLevelsUp() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", shapeOfLegend)
        alice.activates(shapeOfLegend)
        resolveAll()
        expect {
            shapeOfLegend.power shouldBe 2
            shapeOfLegend.subtypes shouldBe listOf(Subtype("Kithkin"), Subtype("Scout"))
        }
        alice.activates(shapeOfLegend, ability = 1)
        resolveAll()
        expect {
            shapeOfLegend.power shouldBe 4
            shapeOfLegend.toughness shouldBe 5
            shapeOfLegend.subtypes shouldBe listOf(Subtype("Kithkin"), Subtype("Soldier"))
        }
    }

    @Test
    fun shapeOfLegendSoldierNeedsAScout() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", shapeOfLegend)
        alice.activates(shapeOfLegend, ability = 1)
        resolveAll()
        expect { shapeOfLegend.power shouldBe 1 }
    }

    @Test
    fun flickeringEmberRummagesOnABigSpell() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", flickeringEmber)
        alice.hand(bigSpell, ogre)
        alice.library(bear, elf, elfMatron)
        alice.casts(bigSpell)
        resolveAll()
        say(true)
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        expect {
            alice.graveyard has ogre
            alice.hand.size shouldBe 3
        }
    }

    // CR 701.17a: a milled land put back on top
    @Test
    fun ferraPutsAMilledLandOnTop() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(ferra)
        alice.library(jolt, "Forest", bear, ogre, elf)
        alice.casts(ferra)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Forest")
        resolveAll()
        expect {
            alice.graveyard has jolt
            alice.graveyard has ogre
        }
        assertEquals("Forest", state.obj(state.player(alice.id).library.first())?.definition?.faces?.get(0)?.name)
    }

    @Test
    fun ferraMakesWorms() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", ferra)
        alice.hand("Forest")
        alice.graveyard("Mountain")
        alice.activates(ferra, choosing = listOf("Forest"))
        resolveAll()
        expect { alice.battlefield.names.count { it == "Worm Token" } shouldBe 2 }
    }

    @Test
    fun velsynsDevoteeLordAndReturn() = scenario {
        alice.battlefield("Mountain", velsynsDevotee, elf)
        alice.hand(jolt)
        alice.graveyard(elfMatron)
        expect { elf.power shouldBe 2 }
        alice.casts(jolt, target = velsynsDevotee)
        resolveAll()
        targets(elfMatron)
        resolveAll()
        expect { alice.hand has elfMatron }
    }

    @Test
    fun nogglePilfererTreasureOnEnteringAndDying() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(nogglePilferer, jolt, jolt)
        alice.casts(nogglePilferer, paying = listOf("Mountain", "Mountain", "Mountain"))
        resolveAll()
        expect { alice.battlefield.names.count { it == "Treasure Token" } shouldBe 1 }
        alice.casts(jolt, target = nogglePilferer, paying = listOf("Mountain#3"))
        alice.casts(jolt, target = nogglePilferer)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Treasure Token" } shouldBe 1 }
    }

    @Test
    fun mindweaveCaptainPumpsWhenAKithkinEnters() = scenario {
        alice.battlefield("Plains", mindweaveCaptain, bear)
        alice.hand(kithkin)
        alice.casts(kithkin)
        resolveAll()
        targets(bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Trample
        }
    }

    @Test
    fun ravenousPageFlickerDrawsOnTheirTurn() = scenario {
        alice.battlefield("Mountain", ravenousPageFlicker)
        alice.hand(jolt)
        alice.library(bear)
        turn(active = bob)
        bob.passes()
        alice.casts(jolt, target = "bob")
        resolveAll()
        say(true)
        resolveAll()
        expect { alice.hand has bear }
        assertEquals(19, alice.life)
    }

    @Test
    fun cautiousGrowerSurveilsAfterAnArrival() = scenario {
        alice.library(ogre, bear)
        bob.library(bear)
        alice.battlefield("Forest", cautiousGrower)
        alice.hand(elf)
        alice.casts(elf)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        expect { alice.graveyard has ogre }
    }

    // CR 106.1: one mana of the chosen colour; it becomes that colour
    @Test
    fun scavengingStrawmawFiltersMana() = scenario {
        alice.battlefield("Forest", scavengingStrawmaw)
        alice.activates(scavengingStrawmaw, ability = 0, color = Color.Red)
        resolveAll()
        expect {
            alice.pool.map { it.color } shouldBe listOf(Color.Red)
            scavengingStrawmaw.colors shouldBe listOf(Color.Red)
        }
    }

    @Test
    fun pookasGazeDrawsAndTakesAColor() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(pookasGaze)
        alice.library(bear)
        alice.casts(pookasGaze)
        resolveAll()
        if (pending is Decision.ChooseColor) alice.answers(Answer.ColorChoice(Color.Blue))
        resolveAll()
        expect {
            alice.hand has bear
            pookasGaze.colors shouldBe listOf(Color.Blue)
        }
    }

    // CR 602.5b: only with five colours among permanents you control
    @Test
    fun pookasGazeNeedsFiveColors() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", pookasGaze, bear)
        kotlin.test.assertFailsWith<IllegalArgumentException> { alice.activates(pookasGaze) }
    }

    @Test
    fun shiftingBarrensFetchesTapped() = scenario {
        alice.battlefield(shiftingBarrens)
        alice.library("Mountain", bear)
        alice.activates(shiftingBarrens)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Mountain")
        resolveAll()
        expect {
            alice.battlefield has "Mountain"
            "Mountain".tapped shouldBe true
            alice.graveyard has shiftingBarrens
        }
    }
}
