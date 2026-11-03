package arcanum.engine.sets.hob

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Hobbit's cards with new rules pieces in play: a total-power attack trigger and an extra combat, odd or even, a
 * face-down pile, cards played while you control a Wizard, a graveyard's activated abilities, dead creatures returning as
 * Food, a creature returning as an artifact, Treasure mana, doubled draws and tokens. */
class HobCardsScenariosTest {
    private val wereWyrm = Cards.invent("Dune Were-Wyrm", "{4}{R}{R}", "Creature — Dragon Wurm", """
        This creature gets +2/+0 for each Mountain you control.
        Whenever you attack with creatures with total power 12 or greater for the first time each turn, untap all attacking creatures. After this phase, there is an additional combat phase.
    """, "0/5")
    private val smeggle = Cards.invent("Smeggle, Puzzle Master", "{1}{B}", "Legendary Creature — Halfling Horror", """
        As Smeggle enters, choose odd or even. (Zero is even.)
        Whenever an opponent casts a spell with mana value of the chosen quality, choose one that hasn't been chosen —
        • Put a +1/+1 counter on Smeggle.
        • Each opponent loses 2 life and you gain 2 life.
        • Draw a card.
    """, "3/1")
    private val puzzles = Cards.invent("Puzzles in the Gloom", "{2}{U}", "Instant",
        "Look at the top four cards of your library and separate them into a face-down pile and a face-up pile. An opponent chooses one of the piles. Put that pile into your hand and the other into your graveyard.")
    private val thalorin = Cards.invent("Thalorin, the Elf-King", "{2}{B}{G}{U}", "Legendary Creature — Elf Noble", """
        Thalorin has all activated abilities of all Elf cards in your graveyard.
        Whenever another legendary Elf you control enters, draw two cards, then discard a card.
    """, "5/6")
    private val elfHealer = Cards.invent("Elf Herbmonger", "{G}", "Creature — Elf Druid", "{T}: You gain 2 life.", "1/1")
    private val dinner = Cards.invent("Dinner for Webs", "{1}{B}", "Instant",
        "Put onto the battlefield under your control all creature cards in your opponents' graveyards that were put there from the battlefield this turn. They are Food artifacts with \"{2}, {T}, Sacrifice this artifact: You gain 3 life.\" (They lose all other types and subtypes.)")
    private val trolls = Cards.invent("Bob, Rob, and Gob", "{3}{B}{G}", "Legendary Creature — Troll", """
        {1}, Sacrifice another creature: Draw cards equal to the sacrificed creature's power, then discard a card.
        When Bob, Rob, and Gob die, if they were a creature, return them to the battlefield. They're an artifact. (They're no longer a creature.)
    """, "5/5")
    private val skarg = Cards.invent("Skarg, Vile Wyrm", "{3}{B}{R}", "Legendary Creature — Dragon", """
        Flying
        When Skarg enters, create X tapped Treasure tokens, where X is the number of artifacts your opponents control.
        Whenever you cast a spell, if mana from a Treasure was spent to cast it, you draw a card and lose 1 life.
    """, "5/5")
    private val brand = Cards.invent("Brand, King of the Vale", "{4}{W}{U}", "Legendary Creature — Human Noble Archer", """
        Reach, vigilance
        If you would draw a card except the first one you draw in each of your draw steps, draw two cards instead.
        If one or more tokens would be created under your control, twice that many of those tokens are created instead.
    """, "3/5")
    private val trinket = Cards.invent("Brass Trinket", "{1}", "Artifact")
    private val bear = Cards.invent("Market Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Hill Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val wizard = Cards.invent("Hedge Wizard", "{1}{U}", "Creature — Human Wizard", "", "1/1")
    private val shock = Cards.invent("Quick Shock", "{R}", "Instant", "Quick Shock deals 2 damage to any target.")
    private val twinShock = Cards.invent("Twin Shock", "{1}{R}", "Instant", "Twin Shock deals 2 damage to any target.")
    private val lethalCut = Cards.invent("Tobbo's Lethal Cut", "{1}{B}{B}", "Instant", "Destroy target creature.")
    private val opt = Cards.invent("Peek of Fortune", "{U}", "Instant", "Draw a card.")
    private val divination = Cards.invent("Ponder the Signs", "{2}{U}", "Sorcery", "Draw two cards.")
    private val hunt = Cards.invent("Treasure Hunt", "{1}{R}", "Sorcery", "Create a Treasure token.")
    private val soldiers = Cards.invent("Call the Pikemen", "{1}{W}", "Sorcery", "Create a 1/1 white Soldier creature token.")
    private val fireform = Cards.inventFaces("Adventure",
        arcanum.parser.OracleCard("Grimbald, Orcs' Bane", "{2}{R}", "Legendary Creature — Avatar Wizard",
            "Whenever you cast a noncreature spell, Grimbald gets +1/+1 until end of turn and deals 1 damage to each opponent.", "2", "3"),
        arcanum.parser.OracleCard("Fireform", "{1}{R}", "Sorcery — Adventure",
            "Look at the top two cards of your library and exile them face down. For as long as they remain exiled, you may play them if you control a Wizard. (Then exile this card. You may cast the creature later from exile.)"))
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    /** Attacking with total power 12 or more the first time: the attackers untap and another combat phase follows. */
    // CR 508.3d, 500.8
    @Test
    fun totalPowerTwelveGivesAnotherCombat() = scenario {
        alice.battlefield(wereWyrm, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.library(bear, bear)
        bob.library(bear, bear)
        expect { wereWyrm.power shouldBe 12 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wereWyrm)
        resolveAll()
        expect { wereWyrm.tapped shouldBe false }
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 8 }
        // the additional combat phase of the same turn
        advanceTo(TurnStep.DeclareAttackers)
        assertEquals(1, state.turn.number)
        alice.attacks(wereWyrm)
        resolveAll()
        // the trigger works only the first time each turn
        expect { wereWyrm.tapped shouldBe true }
    }

    /** Less than 12 total power: no trigger. */
    // CR 603.2c
    @Test
    fun lessPowerNoTrigger() = scenario {
        alice.battlefield(wereWyrm, "Mountain", "Mountain")
        alice.library(bear, bear)
        bob.library(bear, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wereWyrm)
        resolveAll()
        expect { wereWyrm.tapped shouldBe true }
    }

    /** Odd chosen: an opponent's one-mana spell triggers it, a two-mana spell doesn't (zero is even). */
    // CR 607.2a, 202.3
    @Test
    fun oddOrEvenChosenAsItEnters() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(smeggle)
        alice.library(bear, bear)
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand(shock, twinShock)
        alice.casts(smeggle)
        resolveAll()
        alice.answers(Answer.Option("odd"))
        passPriority()
        bob.casts(shock, target = "alice")
        // the trigger's mode is chosen as it is put on the Stack (CR 603.3c)
        alice.answers(Answer.Modes(listOf(2)))
        expect { stack.size shouldBe 2 }
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
        passPriority()
        bob.casts(twinShock, target = "alice")
        expect { stack.size shouldBe 1 }
    }

    /** One face-up pile and one face-down: the opponent picks one, it goes to the hand and the other to the graveyard. */
    // CR 701.20
    @Test
    fun facedownPile() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(puzzles)
        alice.library(bear, ogre, wizard, opt)
        alice.casts(puzzles)
        resolveAll()
        alice.chooses(bear, ogre)
        bob.answers(Answer.Option("second"))
        resolveAll()
        expect {
            alice.hand has wizard
            alice.hand has opt
            alice.graveyard has bear
            alice.graveyard has ogre
        }
    }

    /** The exiled cards can be played only while you control a Wizard. */
    // CR 406.3
    @Test
    fun playedOnlyWhileYouControlAWizard() = scenario {
        alice.battlefield("Mountain", "Mountain", "Island", "Island", "Island")
        alice.hand(fireform, wizard)
        alice.library(bear, opt)
        alice.casts("Fireform", way = arcanum.engine.input.CastWay(face = 1), paying = listOf("Mountain", "Mountain"))
        resolveAll()
        fun castable() = Engine.options(state, alice.id).filterIsInstance<Input.Cast>()
            .any { state.obj(it.card)?.zone == Zone.Exile && nameOf(state.obj(it.card)!!) == opt }
        expect { alice.exile.size shouldBe 3 }
        assertTrue(!castable())
        alice.casts(wizard, paying = listOf("Island", "Island#1"))
        resolveAll()
        assertTrue(castable())
    }

    /** It has the activated abilities of the Elf cards in your graveyard. */
    // CR 613.1f
    @Test
    fun hasTheGraveyardElvesAbilities() = scenario {
        alice.battlefield(thalorin)
        alice.graveyard(elfHealer)
        alice.activates(thalorin)
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    /** Creature cards put into an opponent's graveyard from the battlefield this turn come back as your Food artifacts. */
    // CR 611.2e, 205.1a
    @Test
    fun deadCreaturesReturnAsFood() = scenario {
        alice.battlefield("Mountain", "Swamp", "Swamp")
        alice.hand(shock, dinner)
        bob.battlefield(bear)
        alice.casts(shock, target = bear)
        resolveAll()
        alice.casts(dinner)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Battlefield
            bear.controller shouldBe alice.id
            bear.types shouldBe listOf(CardType.Artifact)
            bear.subtypes.map { it.name } shouldBe listOf("Food")
        }
    }

    /** It dies and comes back as an artifact, no longer a creature. */
    // CR 611.2e, 603.10a
    @Test
    fun trollsReturnAsAnArtifact() = scenario {
        alice.battlefield(trolls, "Swamp", "Swamp", "Swamp")
        alice.hand(lethalCut)
        alice.casts(lethalCut, target = trolls)
        resolveAll()
        expect {
            trolls.zone shouldBe Zone.Battlefield
            trolls.types shouldBe listOf(CardType.Artifact)
        }
    }

    /** Treasure mana spent on a spell: draw a card and lose 1 life; other mana: nothing. */
    // CR 106.3, 603.4
    @Test
    fun treasureManaDraws() = scenario {
        alice.battlefield(skarg, "Mountain", "Mountain", "Island")
        alice.hand(hunt, opt, opt)
        alice.library(bear, bear, bear)
        alice.casts(hunt)
        resolveAll()
        alice.casts(opt, paying = listOf("Island"))
        resolveAll()
        expect {
            alice.life shouldBe 20
            alice.hand.size shouldBe 2
        }
        alice.casts(opt, paying = listOf("Treasure Token:U"))
        resolveAll()
        expect {
            alice.life shouldBe 19
            alice.hand.size shouldBe 3
        }
    }

    /** The second card drawn in a turn becomes two; tokens are doubled. */
    // CR 614.11, 614.1a
    @Test
    fun drawsAndTokensDoubled() = scenario {
        alice.battlefield(brand, "Island", "Plains", "Plains")
        alice.hand(opt, soldiers)
        alice.library(bear, bear, bear, bear)
        alice.casts(opt)
        resolveAll()
        expect { alice.hand.size shouldBe 3 }
        alice.casts(soldiers)
        resolveAll()
        assertEquals(2, state.battlefield.count { state.obj(it)?.token == true })
    }
}
