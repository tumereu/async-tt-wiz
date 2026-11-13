package arcanum.engine.sets.mom

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.dft.yes
import arcanum.engine.sets.fin.choose
import arcanum.engine.sets.fin.pay
import arcanum.engine.sets.fin.resolveAll
import arcanum.engine.sets.ltr.permitted
import arcanum.engine.sets.spm.drive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** MOM chunk 07: each player mills, then cards chosen from graveyards: one from each player's onto your battlefield, up to two back to your
 * hand, an opponent's land kept until it would leave; a graveyard-size condition; entering as a copy of a graveyard card; an emblem that
 * plays lands and permanent spells from your graveyard. */
class MomChunk07MillScenariosTest {
    private val rupture = Cards.invent("Rupture the Omniverse", "{5}{B}{B}", "Sorcery",
        "Each player mills ten cards. For each player, choose a creature or planeswalker card in that player's graveyard. Put those cards onto the battlefield under your control. Then each creature you control becomes a Phyrexian in addition to its other types.")
    private val unbar = Cards.invent("Unbar the Ossuary", "{2}{B}", "Instant",
        "Each player mills three cards. Then you return up to two creature cards from your graveyard to your hand. (To mill three cards, a player puts the top three cards of their library into their graveyard.)")
    private val oilspeaker = Cards.invent("Veteran Oilspeaker", "{3}{B}", "Creature — Phyrexian Wizard", """
        Menace (This creature can't be blocked except by two or more creatures.)
        This creature gets +3/+0 as long as an opponent has eight or more cards in their graveyard.
        Whenever this creature attacks or blocks, each player mills a card.
    """.trimIndent(), "2/4")
    private val worldsplitter = Cards.invent("Worldsplitter, the Conquest Root", "{3}", "Legendary Artifact", """
        {2}, {T}: Target opponent mills three cards. Put a land card from their graveyard onto the battlefield tapped under your control. It gains "If this land would leave the battlefield, exile it instead of putting it anywhere else."
        {10}, {T}, Sacrifice Worldsplitter: Search your library for any number of Praetor cards, put them onto the battlefield, then shuffle.
    """.trimIndent())
    private val morvayne = Cards.inventFaces("Transform",
        face("Morvayne", "{3}{B}{B}", "Legendary Creature — Phyrexian Praetor", """
            Menace
            When Morvayne enters, each opponent sacrifices a nontoken creature or planeswalker of their choice.
            {4}{B}: Exile Morvayne, then return it to the battlefield transformed under its owner's control. Activate only as a sorcery and only if an opponent has eight or more cards in their graveyard.
        """.trimIndent(), "4/5", null, null),
        face("The Bleak Scriptures", null, "Enchantment — Saga", """
            (As this Saga enters and after your draw step, add a lore counter.)
            I — For each opponent, destroy up to one target creature or planeswalker that player controls.
            II — Each opponent discards three cards, then mills three cards.
            III — Put all creature cards from all graveyards onto the battlefield under your control. Exile this Saga, then return it to the battlefield (front face up).
        """.trimIndent(), null, null, "B"))
    private val spokerunner = Cards.inventFaces("Transform",
        face("Rat Spokerunner", "{3}{B}", "Creature — Rat Samurai", """
            Menace
            When this creature enters, each player mills three cards. (To mill three cards, a player puts the top three cards of their library into their graveyard.)
            {5}{W/P}: Transform this creature. Activate only as a sorcery. ({W/P} can be paid with either {W} or 2 life.)
        """.trimIndent(), "3/3", null, null),
        face("Ghastly Spokewheel", null, "Creature — Phyrexian Rat", """
            Menace (This creature can't be blocked except by two or more creatures.)
            When this creature transforms into Ghastly Spokewheel, put target permanent card with mana value 2 or less from a graveyard onto the battlefield under your control.
        """.trimIndent(), "4/5", null, "B"))
    // Lazotep Convert's text on a single-faced card (a Siege's back face can't be set up face up, see notes)
    private val mimic = Cards.invent("Lazuli Mimic", "{2}{U}{B}", "Creature — Zombie",
        "You may have this creature enter as a copy of any creature card in a graveyard, except it's a 4/4 black Zombie in addition to its other colors and types.", "4/4")
    private val skirmisher = Cards.invent("Darting Skirmisher", "{2}{B}", "Creature — Faerie Rogue", """
        Flying
        When this creature dies, each player mills two cards. Then you may exile this card. When you do, put target creature or battle card from your graveyard on top of your library. (To mill two cards, a player puts the top two cards of their library into their graveyard.)
    """.trimIndent(), "2/2")
    private val lirra = Cards.invent("Lirra and Rootwalker", "{1}{G}{G}", "Legendary Planeswalker — Wrenn", """
        Lands you control have "{T}: Add one mana of any color."
        [+1]: Up to one target land you control becomes a 3/3 Elemental creature with vigilance, hexproof, and haste until your next turn. It's still a land.
        [−2]: Mill three cards. You may put a permanent card from among the milled cards into your hand.
        [−7]: You get an emblem with "You may play lands and cast permanent spells from your graveyard."
    """.trimIndent(), loyalty = "4")

    private val walker = Cards.invent("Breach Walker", "{1}{B}", "Creature — Human", "", "2/2")
    private val hound = Cards.invent("Rift Hound", "{1}{G}", "Creature — Dog", "", "2/2")
    private val ghoul = Cards.invent("Rift Ghoul", "{2}{B}", "Creature — Zombie", "", "2/3")
    private val oracle = Cards.invent("Rift Oracle", "{2}{U}", "Legendary Planeswalker — Jace", "[+1]: Draw a card.", loyalty = "3")
    private val brute = Cards.invent("Spoke Brute", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val shambler = Cards.invent("Ossuary Shambler", "{1}{B}", "Creature — Zombie", "", "2/2")
    private val wight = Cards.invent("Ossuary Wight", "{2}{B}", "Creature — Spirit", "", "2/1")
    private val quake = Cards.invent("Quake Shatter", "{2}{R}", "Sorcery", "Destroy target land.")
    private val vexhal = Cards.invent("Vexhal, Praetor of Rust", "{4}{B}", "Legendary Creature — Phyrexian Praetor", "", "5/5")
    private val ombrith = Cards.invent("Ombrith, Praetor of Ash", "{4}{R}", "Legendary Creature — Phyrexian Praetor", "", "4/4")
    private val hulk = Cards.invent("Grave Hulk", "{3}{G}", "Creature — Beast", "Trample", "2/6")
    private val bauble = Cards.invent("Tin Bauble", "{1}", "Artifact", "")
    private val seedling = Cards.invent("Root Seedling", "{G}", "Creature — Plant", "", "1/1")
    private val sprinkle = Cards.invent("Ash Sprinkle", "{R}", "Instant", "Ash Sprinkle deals 2 damage to any target.")

    // CR 701.17a: every library is milled; CR 608.2c: one card chosen in each player's graveyard; CR 110.2a: under Alice's control;
    // CR 611.2c: only the creatures she controls as it resolves become Phyrexians (a planeswalker isn't one)
    @Test
    fun ruptureTakesOneCardFromEachGraveyardAndMakesPhyrexians() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", walker)
        alice.hand(rupture)
        alice.library("Island")
        bob.library("Island")
        carol.library("Island")
        alice.graveyard(hound)
        bob.graveyard(ghoul)
        carol.graveyard(oracle, brute)
        alice.casts(rupture)
        resolveAll()
        choose(oracle)
        expect {
            alice.library.isEmpty()
            bob.library.isEmpty()
            carol.library.isEmpty()
            hound.zone shouldBe Zone.Battlefield
            ghoul.controller shouldBe alice.id
            oracle.controller shouldBe alice.id
            brute.zone shouldBe Zone.Graveyard
            assertTrue(Subtype("Phyrexian") in walker.subtypes)
            assertTrue(Subtype("Phyrexian") in ghoul.subtypes)
            assertTrue(Subtype("Zombie") in ghoul.subtypes)
            assertTrue(Subtype("Phyrexian") !in oracle.subtypes)
        }
    }

    // CR 701.17a; CR 608.2c: up to two creature cards from Alice's graveyard, chosen after the mill
    @Test
    fun unbarMillsEveryoneThenReturnsUpToTwoCreatures() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(unbar)
        alice.library(shambler, wight, "Island", "Island")
        bob.library("Plains", "Plains", "Plains", "Plains")
        carol.library("Plains", "Plains", "Plains", "Plains")
        alice.casts(unbar)
        resolveAll()
        assertEquals(2, (pending as Decision.ChooseObjects).options.size)
        choose(shambler, wight)
        expect {
            alice.hand has shambler
            alice.hand has wight
            bob.graveyard.size shouldBe 3
            carol.graveyard.size shouldBe 3
            bob.library.size shouldBe 1
        }
    }

    // CR 611.3a: +3/+0 once an opponent (any of them) has eight cards in their graveyard; CR 508.1m: the attack trigger mills each player
    @Test
    fun oilspeakerGrowsWhenAnOpponentReachesEightCards() = scenario(players = 3) {
        alice.battlefield(oilspeaker)
        alice.library("Island")
        bob.library("Plains")
        carol.library("Forest")
        bob.graveyard("Island", "Island", "Island", "Island", "Island", "Island", "Island")
        carol.graveyard("Island", "Island")
        expect { oilspeaker.power shouldBe 2 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(oilspeaker, defender = "carol")
        resolveAll()
        expect {
            bob.graveyard.size shouldBe 8
            carol.graveyard.size shouldBe 3
            alice.graveyard.size shouldBe 1
            oilspeaker.power shouldBe 5
        }
    }

    // CR 602.5b: the activation condition reads every opponent's graveyard; seven cards isn't enough
    @Test
    fun morvayneTransformsOnlyOnceAnOpponentHasEightCardsInTheirGraveyard() = scenario(players = 3) {
        alice.battlefield(morvayne, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        bob.graveyard("Island", "Island", "Island", "Island", "Island", "Island", "Island")
        assertFailsWith<IllegalArgumentException> { alice.activates(morvayne) }
    }

    // CR 602.5b, 701.27: with eight cards in Carol's graveyard Morvayne is exiled and returns as the Saga
    @Test
    fun morvayneTransformsWithEightCardsInAGraveyard() = scenario(players = 3) {
        alice.battlefield(morvayne, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        carol.graveyard("Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.activates(morvayne)
        resolve()
        assertEquals("The Bleak Scriptures", view()[card(morvayne, zone = Zone.Battlefield)]!!.characteristics!!.name)
        expect { card(morvayne).counters(CounterKind.Lore) shouldBe 1 }
    }

    // CR 608.2c: the land is chosen from the target opponent's milled cards and put under Alice's control tapped; CR 614.1a: destroyed, it is
    // exiled instead of going to its owner's graveyard
    @Test
    fun worldsplitterKeepsTheLandUntilItLeavesThenExilesIt() = scenario {
        alice.battlefield(worldsplitter, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(quake)
        bob.library("Forest", "Plains", "Island")
        alice.activates(worldsplitter, target = "bob", paying = listOf("Mountain", "Mountain#1"))
        resolveAll()
        choose("Forest")
        expect {
            "Forest".zone shouldBe Zone.Battlefield
            "Forest".controller shouldBe alice.id
            "Forest".tapped shouldBe true
            bob.graveyard.size shouldBe 2
        }
        alice.casts(quake, target = "Forest", paying = listOf("Mountain#2", "Mountain#3", "Mountain#4"))
        resolveAll()
        expect {
            bob.exile has "Forest"
            bob.graveyard lacks "Forest"
        }
    }

    // CR 701.23a: "any number of" Praetor cards, all found ones put onto the battlefield
    @Test
    fun worldsplitterFindsAnyNumberOfPraetors() = scenario {
        alice.battlefield(worldsplitter, *Array(10) { "Mountain" })
        alice.library(vexhal, "Swamp", ombrith)
        alice.activates(worldsplitter, ability = 1, paying = List(10) { if (it == 0) "Mountain" else "Mountain#$it" })
        resolveAll()
        choose(vexhal, ombrith)
        expect {
            vexhal.zone shouldBe Zone.Battlefield
            ombrith.zone shouldBe Zone.Battlefield
            worldsplitter.zone shouldBe Zone.Graveyard
            alice.library has "Swamp"
        }
    }

    // CR 701.27, 603.6: the transform trigger targets a permanent card with mana value 2 or less in an opponent's graveyard; under Alice's control
    @Test
    fun ghastlySpokewheelTakesACheapPermanentFromAnyGraveyard() = scenario {
        alice.battlefield(spokerunner, "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        bob.graveyard(bauble, brute)
        alice.activates(spokerunner)
        pay()
        resolveAll()
        expect {
            bauble.zone shouldBe Zone.Battlefield
            bauble.controller shouldBe alice.id
            brute.zone shouldBe Zone.Graveyard
        }
    }

    // CR 707.9b: a copy of Bob's graveyard creature that is also a 4/4 black Zombie; CR 614.1c: the card stays in the graveyard
    @Test
    fun mimicEntersAsACopyOfAGraveyardCreatureCard() = scenario {
        alice.battlefield("Island", "Island", "Swamp", "Swamp")
        alice.hand(mimic)
        bob.graveyard(hulk)
        alice.casts(mimic)
        resolveAll()
        drive(
            { d: Decision -> d is Decision.ChooseObjects } to { alice.chooses(hulk) },
            { d: Decision -> d is Decision.May } to { yes() },
        )
        resolveAll()
        // a copy keeps its own object name: found by the card cast
        val c = view()[card(mimic, zone = Zone.Battlefield)]!!.characteristics!!
        assertEquals(hulk, c.name)
        assertEquals(4, c.power)
        assertEquals(4, c.toughness)
        assertEquals(setOf(Color.Green, Color.Black), c.colors.toSet())
        assertTrue(Subtype("Zombie") in c.subtypes && Subtype("Beast") in c.subtypes)
        assertTrue(c.keywords.any { it is Keyword.Trample })
        expect { bob.graveyard has hulk }
    }

    // CR 400.7, 603.12: "exile this card" exiles the card from the graveyard; when it does, a creature card goes on top of the library
    @Test
    fun skirmisherExilesItselfFromTheGraveyardToTopACreature() = scenario {
        alice.battlefield(skirmisher, "Mountain")
        alice.hand(sprinkle)
        alice.library("Island", "Island", "Island")
        bob.library("Plains", "Plains")
        alice.graveyard(hound)
        alice.casts(sprinkle, target = skirmisher)
        resolveAll()
        yes()
        expect {
            alice.exile has skirmisher
            hound.zone shouldBe Zone.Library
            alice.library.names.first() shouldBe hound
            bob.graveyard.size shouldBe 2
        }
    }

    // CR 205.1b: the animated land is still a Forest; CR 611.2b: until Alice's next turn
    @Test
    fun lirraAnimatesALandThatStaysAForest() = scenario {
        alice.battlefield(lirra, "Forest", counters = listOf(CounterKind.Loyalty to 4))
        alice.activates(lirra, ability = 0, target = "Forest")
        resolveAll()
        expect {
            "Forest".power shouldBe 3
            assertTrue(CardType.Creature in "Forest".types && CardType.Land in "Forest".types)
            assertTrue(Subtype("Forest") in "Forest".subtypes && Subtype("Elemental") in "Forest".subtypes)
            "Forest" hasKeyword Keyword.Haste.kind
            lirra.loyalty shouldBe 5
        }
    }

    // CR 114.2: the emblem's permission: a land played and a permanent spell cast from the graveyard (CR 305.1, 601.2); not an instant
    @Test
    fun lirraEmblemPlaysLandsAndCastsPermanentSpellsFromTheGraveyard() = scenario {
        alice.battlefield(lirra, "Forest", counters = listOf(CounterKind.Loyalty to 7))
        alice.graveyard("Plains", seedling, sprinkle)
        alice.activates(lirra, ability = 2)
        resolveAll()
        alice.plays("Plains")
        alice.casts(seedling, way = permitted(seedling), paying = listOf("Forest"))
        resolveAll()
        expect {
            "Plains".zone shouldBe Zone.Battlefield
            seedling.zone shouldBe Zone.Battlefield
        }
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.Cast>().none { it.card == card(sprinkle) })
    }
}
