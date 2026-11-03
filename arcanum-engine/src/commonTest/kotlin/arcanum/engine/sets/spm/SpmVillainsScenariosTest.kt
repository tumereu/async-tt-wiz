package arcanum.engine.sets.spm

import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Marvel's Spider-Man villains (blue and black) in play: tokens exiled as their maker leaves, a base 8/8 with a full graveyard,
 * X targets exiled until the end step, a Villain turned land until your next turn, different names among targets, a sacrifice that
 * draws X and puts a permanent, each player's revealed card costing the other life, surveil by counters, life for creatures,
 * unspent red mana kept, X damage paid as it leaves. */
class SpmVillainsScenariosTest {
    private val phantasmo = Cards.invent("Phantasmo, Lord of Mirages", "{3}{U}", "Legendary Creature — Human Villain",
        "When Phantasmo enters, create a 3/3 blue Illusion Villain creature token for each nontoken Villain you control. Exile those tokens when Phantasmo leaves the battlefield.", "3/3")
    private val scholar = Cards.invent("Professor Grapple, Baleful Scholar", "{4}{U}", "Legendary Creature — Human Scientist Villain", """
        As long as there are eight or more cards in your graveyard, Professor Grapple has base power and toughness 8/8.
        As long as you control another Villain, Professor Grapple has hexproof. (He can't be the target of spells or abilities your opponents control.)
    """, "4/5")
    private val rafters = Cards.invent("Cling to the Rafters", "{X}{U}", "Instant",
        "Exile X target artifacts and/or creatures. Return the exiled cards to the battlefield under their owners' control at the beginning of the next end step.")
    private val tidewretch = Cards.invent("Tidewretch, Liquid Outlaw", "{U}{U}", "Legendary Creature — Elemental Villain", """
        Whenever you cast a blue spell, if Tidewretch is a creature, he gets +1/+1 until end of turn.
        At the beginning of your end step, untap Tidewretch. Until your next turn, he becomes a land and gains "{T}: Add {U}." (He's not a creature during that time.)
    """, "2/2")
    private val witness = Cards.invent("Witness the Wicked Half-Dozen!", "{6}{B}", "Sorcery",
        "Return up to six target creature cards with different names from your graveyard to the battlefield.")
    private val guardian = Cards.invent("Ink Fiend, Deadly Guardian", "{3}{B}{R}{G}", "Legendary Creature — Symbiote Hero Villain", """
        Menace, trample, haste
        Whenever Ink Fiend attacks, you may sacrifice another creature. If you do, draw X cards, then you may put a permanent card with mana value X or less from your hand onto the battlefield, where X is the sacrificed creature's mana value.
    """, "5/5")
    private val fortune = Cards.invent("Rotten Fortune", "{2}{B}", "Enchantment",
        "At the beginning of your end step, two target players each reveal the top card of their library. They each lose life equal to the mana value of the card revealed by the other player. Then they each put the card they revealed into their hand.")
    private val shadow = Cards.invent("Webcrawler in Shadow", "{4}{B}", "Legendary Creature — Spider Human Hero", """
        Menace
        Whenever a creature you control attacks alone, put a +1/+1 counter on it. Then surveil X, where X is the number of counters on it. (Look at the top X cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
    """, "4/4")
    private val fury = Cards.invent("Felonious Fury", "{3}{B}{B}", "Sorcery",
        "Target opponent loses life equal to the number of creatures they control. Then destroy all creatures.")
    private val voltrix = Cards.invent("Voltrix, Charged Assailant", "{1}{R}{R}", "Legendary Creature — Human Villain", """
        Flying
        You don't lose unspent red mana as steps and phases end.
        Whenever you cast an instant or sorcery spell, add {R}.
        When Voltrix leaves the battlefield, you may pay {X}. When you do, he deals X damage to target player.
    """, "2/3")
    private val barb = Cards.invent("Stinger's Barb", "{1}{B}", "Instant", "Target creature gets -3/-3 until end of turn.")
    private val study = Cards.invent("Triple Study", "{2}{U}", "Sorcery", "Draw three cards.")
    private val jolt = Cards.invent("Static Jolt", "{R}", "Instant", "Static Jolt deals 2 damage to any target.")
    private val bear = spmBear
    private val ogre = spmOgre
    private val giant = spmGiant
    private val thug = spmVillain

    /** One token for each nontoken Villain; the tokens are exiled when it leaves. */
    // CR 111.1, 603.7
    @Test
    fun tokensLeaveWithTheirMaker() = scenario {
        alice.battlefield(thug, "Island", "Island", "Island", "Island", "Swamp", "Swamp")
        alice.hand(phantasmo, barb)
        alice.casts(phantasmo, paying = listOf("Island", "Island", "Island", "Island"))
        resolveAll()
        assertEquals(2, state.battlefield.count { state.obj(it)!!.token })
        alice.casts(barb, target = phantasmo, paying = listOf("Swamp", "Swamp"))
        resolveAll()
        expect { alice.graveyard has phantasmo }
        assertEquals(0, state.battlefield.count { state.obj(it)!!.token })
    }

    /** A base 8/8 with eight cards in the graveyard; hexproof with another Villain. */
    // CR 613.4b
    @Test
    fun baseEightWithAFullGraveyard() = scenario {
        alice.battlefield(scholar, thug)
        alice.graveyard(bear, bear, bear, bear, bear, bear, bear)
        expect { scholar.power shouldBe 4 }
        alice.graveyard(bear)
        expect {
            scholar.power shouldBe 8
            scholar hasKeyword Keyword.Hexproof
        }
    }

    /** X artifacts and creatures exiled return under their owners' control at the next end step. */
    // CR 603.7, 107.3
    @Test
    fun exiledUntilTheEndStep() = scenario {
        alice.battlefield("Island", "Island", "Island", bear)
        alice.hand(rafters)
        bob.battlefield(ogre)
        alice.casts(rafters, x = 2, targets = listOf(bear, ogre))
        resolveAll()
        expect {
            alice.exile has bear
            bob.exile has ogre
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Battlefield
            ogre.controller shouldBe bob.id
        }
    }

    /** At the end step it untaps and becomes a land with "{T}: Add {U}" until your next turn; a land doesn't get +1/+1. */
    // CR 205.1b, 611.2a
    @Test
    fun becomesALandUntilYourNextTurn() = scenario {
        alice.battlefield(tidewretch, tapped = true)
        alice.battlefield("Island", "Island", "Island")
        alice.hand(study)
        alice.library(bear, bear, bear, bear, bear)
        bob.library(bear, bear)
        turn(number = 3)
        alice.casts(study)
        resolveAll()
        expect { tidewretch.power shouldBe 3 }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            tidewretch.tapped shouldBe false
            tidewretch.types shouldBe listOf(arcanum.api.CardType.Land)
        }
    }

    /** Targets with the same name are refused; different names return. */
    // CR 601.2c
    @Test
    fun differentNames() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(bear, bear, ogre)
        alice.hand(witness)
        assertTrue(runCatching { alice.casts(witness, targets = listOf(bear, bear)) }.isFailure)
    }

    @Test
    fun differentNamesReturn() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(bear, ogre)
        alice.hand(witness)
        alice.casts(witness, targets = listOf(bear, ogre))
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Battlefield
            ogre.zone shouldBe Zone.Battlefield
        }
    }

    /** A sacrificed 3-drop: three cards drawn, then a permanent card with mana value 3 or less put onto the battlefield. */
    // CR 608.2h, 107.3c
    @Test
    fun sacrificeDrawsAndPuts() = scenario {
        alice.battlefield(guardian, ogre)
        alice.library(bear, giant, bear)
        bob.library(bear, bear)
        turn(number = 3)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(guardian)
        drive(
            { d: Decision -> d is Decision.May } to { yes() },
            // a card drawn is a new object (CR 400.7): the first bear offered
            { d: Decision -> d is Decision.ChooseObjects } to { answer(Answer.Objects(listOf((pending as Decision.ChooseObjects).options.first()))) },
        )
        expect {
            ogre.zone shouldBe Zone.Graveyard
            alice.hand has giant
            alice.battlefield has bear
        }
    }

    /** Each player loses life equal to the other's revealed card's mana value; both cards go to their owners' hands. */
    // CR 400.3, 701.20a
    @Test
    fun eachLosesByTheOthersCard() = scenario {
        alice.battlefield(fortune)
        alice.library(giant)
        bob.library(ogre)
        advanceTo(TurnStep.End)
        drive({ d: Decision -> d is Decision.Targets } to { targets("alice", "bob") })
        assertEquals(17, alice.life)
        assertEquals(15, bob.life)
        expect {
            alice.hand has giant
            bob.hand has ogre
        }
    }

    /** Attacking alone: a counter, then surveil as many as its counters. */
    // CR 506.5, 701.25a
    @Test
    fun aloneCounterThenSurveil() = scenario {
        alice.battlefield(shadow)
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        alice.library(ogre, ogre, giant)
        bob.library(bear, bear)
        turn(number = 3)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        drive({ d: Decision -> d is Decision.ChooseObjects } to { alice.chooses(ogre, ogre) })
        expect {
            bear.counters(p1p1) shouldBe 2
            alice.graveyard has ogre
        }
        assertEquals(2, alice.graveyard.size)
    }

    /** The opponent loses a life for each creature they control, then all creatures die. */
    // CR 608.2c
    @Test
    fun lifeThenWrath() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", bear)
        alice.hand(fury)
        bob.battlefield(ogre, ogre, giant)
        alice.casts(fury, target = "bob")
        resolveAll()
        assertEquals(17, bob.life)
        assertEquals(0, state.battlefield.count { view()[it]!!.characteristics!!.types.contains(arcanum.api.CardType.Creature) })
    }

    /** Red mana stays in the pool between steps; leaving, X is paid for X damage to a player. */
    // CR 500.4, 107.3a, 603.12
    @Test
    fun redManaKeptAndDamageAsItLeaves() = scenario {
        alice.battlefield(voltrix, "Mountain", "Swamp", "Swamp", "Island")
        alice.hand(jolt, barb)
        alice.library(bear, bear)
        alice.casts(jolt, target = "bob", paying = listOf("Mountain"))
        resolveAll()
        assertEquals(1, alice.pool.size)
        advanceTo(TurnStep.BeginningOfCombat)
        assertEquals(1, alice.pool.size)
        alice.casts(barb, target = voltrix, paying = listOf("Swamp", "Swamp"))
        drive(
            { d: Decision -> d is Decision.ChooseNumber } to { answer(Answer.Number(2)) },
            { d: Decision -> d is Decision.May } to { yes() },
            { d: Decision -> d is Decision.ManaPayment } to { answer(Answer.ManaPayment((pending as Decision.ManaPayment).suggestion)) },
            { d: Decision -> d is Decision.Targets } to { targets("bob") },
        )
        assertEquals(16, bob.life)
    }
}
