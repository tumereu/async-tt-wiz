package arcanum.engine.sets.bro

import arcanum.api.CardType
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Brothers' War chunk 09 in play: one free spell each turn from the hand or the top of the library, up to X cards put onto the
 * battlefield from the top seven, every player revealing as many cards as the nonland permanents they control at once (three
 * players), a chosen name searched for in an opponent's graveyard, hand and library with a card drawn for each one from the hand, an
 * enters trigger for creatures with equal power and toughness, and a Powerstone for an artifact put into exile from the battlefield.
 */
class BroChunk09PutRevealAndSearchScenariosTest {
    private val unity = Cards.invent("Unity of All Libraries", "{6}{U}{U}", "Enchantment", """
        You may look at the top card of your library any time.
        You may play lands and cast spells from the top of your library.
        Once during each of your turns, you may cast a spell from your hand or the top of your library without paying its mana cost.
    """)
    private val rebuild = Cards.invent("Restorer's Rebuild", "{X}{W}{W}{W}", "Sorcery",
        "Look at the top seven cards of your library. Put up to X artifact and/or creature cards with mana value 3 or less from among them onto the battlefield. Put the rest on the bottom of your library in a random order.")
    private val onslaught = Cards.invent("Overflowing Onslaught", "{5}{R}{R}", "Sorcery",
        "Each player reveals a number of cards from the top of their library equal to the number of nonland permanents they control, puts all permanent cards they revealed this way onto the battlefield, and puts the rest into their graveyard.")
    private val mind = Cards.invent("The Granite Mind", "{2}", "Legendary Artifact",
        "{2}, {T}, Exile The Granite Mind: Choose a card name. Search target opponent's graveyard, hand, and library for up to four cards with that name and exile them. That player shuffles, then draws a card for each card exiled from their hand this way. Activate only as a sorcery.")
    private val lattice = Cards.invent("Balance Lattice", "{4}", "Artifact",
        "Whenever a creature you control with power equal to its toughness enters, you may pay {1}. If you do, draw a card.")
    private val foundry = Cards.invent("Cinderrock Foundry", "{4}", "Artifact",
        "Whenever this artifact or another nontoken artifact you control is put into a graveyard from the battlefield or is put into exile from the battlefield, create a tapped Powerstone token. (It's an artifact with \"{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.\")")

    private val squire = Cards.invent("Lattice Squire", "{1}", "Creature — Human Soldier", "", "2/2")
    private val brute = Cards.invent("Lattice Brute", "{1}", "Creature — Human Warrior", "", "3/2")
    private val cog = Cards.invent("Refinery Cog", "{2}", "Artifact", "")
    private val ogre = Cards.invent("Rebuild Ogre", "{4}", "Creature — Ogre", "", "4/4")
    private val spark = Cards.invent("Onslaught Spark", "{R}", "Instant", "Onslaught Spark deals 1 damage to any target.")
    private val purge = Cards.invent("Foundry Purge", "{W}", "Instant", "Exile target artifact.")
    private val zap = Cards.invent("Mindstone Zap", "{R}", "Instant", "Mindstone Zap deals 2 damage to any target.")

    private fun arcanum.engine.scenario.Scenario.freeCasts() = Engine.options(state, alice.id).filterIsInstance<Input.Cast>()

    // CR 118.9, 601.2a: one spell without paying its mana cost each turn, from the top of the library or the hand, not one from each
    @Test
    fun unityCastsOneFreeSpellEachTurnFromTheTopOrTheHand() = scenario {
        alice.battlefield(unity)
        alice.hand(squire)
        alice.library(ogre, brute, "Island")
        val free = freeCasts()
        assertEquals(setOf(ogre, squire), free.map { state.obj(it.card)!!.definition.faces.first().name }.toSet(), "the top card and the hand card, both free")
        perform(free.first { state.obj(it.card)!!.definition.faces.first().name == ogre })
        allPass()
        expect { alice.battlefield has ogre }
        assertTrue(freeCasts().isEmpty(), "the free spell was used; nothing else can be paid for")
    }

    // CR 107.3a: up to X (2) of the artifact and creature cards with mana value 3 or less; the rest go to the bottom
    @Test
    fun rebuildPutsUpToXCardsOntoTheBattlefield() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(rebuild)
        alice.library(squire, cog, brute, ogre, "Plains", "Plains", "Plains", "Island")
        alice.casts(rebuild, x = 2)
        allPass()
        val d = decision<Decision.ChooseObjects>()
        assertEquals(2, d.max)
        assertEquals(setOf(squire, cog, brute), d.options.map { state.obj(it)!!.definition.faces.first().name }.toSet(), "the 4-mana Ogre and lands aren't offered")
        playOut(squire, cog)
        expect {
            alice.battlefield has squire
            alice.battlefield has cog
            alice.library has brute
            alice.library.names.first() shouldBe "Island"
        }
    }

    // CR 101.4, 608.2e: each player reveals as many cards as nonland permanents they control (2, 1 and 0), permanent cards (the land too)
    // enter, the rest go to the graveyard
    @Test
    fun onslaughtRevealsForEachPlayerByTheirOwnPermanents() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", squire, cog)
        alice.hand(onslaught)
        alice.library(brute, spark, ogre)
        bob.battlefield("Forest", squire)
        bob.library("Forest", cog)
        carol.battlefield("Plains")
        carol.library(ogre)
        alice.casts(onslaught)
        allPass()
        playOut()
        expect {
            alice.battlefield has brute
            alice.graveyard has spark
            alice.library has ogre
            bob.battlefield.names.count { it == "Forest" } shouldBe 2
            bob.library has cog
            carol.library has ogre
            carol.battlefield lacks ogre
        }
    }

    // CR 201.4, 400.7: four cards named Zap exiled (two from the hand); the opponent shuffles and draws two
    @Test
    fun graniteMindDrawsForEachCardExiledFromTheHand() = scenario {
        alice.battlefield(mind, "Plains", "Plains")
        bob.hand(zap, zap, squire)
        bob.graveyard(zap)
        bob.library(zap, "Forest", "Forest", "Forest")
        alice.activates(mind, target = "bob")
        allPass()
        answer(Answer.CardName(zap))
        playOut(zap, zap, zap, zap)
        expect {
            bob.exile.names.count { it == zap } shouldBe 4
            bob.graveyard lacks zap
            bob.hand.size shouldBe 3
            bob.hand has squire
            bob.library.size shouldBe 1
            alice.exile has mind
        }
    }

    // CR 603.2: a 3/2 entering doesn't trigger it, a 2/2 does ("its toughness" is the entering creature's own, read in the trigger's filter)
    @Test
    fun latticeTriggersOnlyForEqualPowerAndToughness() = scenario {
        alice.battlefield(lattice, "Plains", "Plains", "Plains")
        alice.hand(squire, brute)
        alice.library("Island", "Island")
        alice.casts(brute)
        allPass()
        expect { alice.battlefield has brute }
        assertTrue(state.stack.isEmpty() && pending == null, "no trigger for a 3/2")
        alice.casts(squire)
        allPass()
        playOut()
        expect {
            alice.battlefield has squire
            alice.hand has "Island"
        }
    }

    // CR 406.3: a nontoken artifact put into exile from the battlefield; CR 111.10h: a tapped Powerstone
    @Test
    fun foundryMakesAPowerstoneWhenAnArtifactIsExiled() = scenario {
        alice.battlefield(foundry, cog, "Plains")
        alice.hand(purge)
        alice.casts(purge, target = cog)
        allPass()
        playOut()
        expect {
            alice.exile has cog
            alice.battlefield has "Powerstone Token"
            "Powerstone Token".tapped shouldBe true
            "Powerstone Token".types shouldBe listOf(CardType.Artifact)
        }
    }

    // CR 603.10a: it looks back in time when it is put into exile itself
    @Test
    fun foundryMakesAPowerstoneWhenItIsExiledItself() = scenario {
        alice.battlefield(foundry, "Plains")
        alice.hand(purge)
        alice.casts(purge, target = foundry)
        allPass()
        playOut()
        expect {
            alice.exile has foundry
            alice.battlefield has "Powerstone Token"
        }
    }

}
