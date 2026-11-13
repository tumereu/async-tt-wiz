package arcanum.engine.sets.mh3

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.ecl.paySuggested
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The MH3 cards of chunk 10, played: the invented names carry the oracle text of Ulamog, the Defiler, Kappa Cannoneer, Birthing Ritual,
 * Disciple of Freyalise, Powerbalance, Ugin's Labyrinth, Izzet Generatorium, Lethal Throwdown, Shilgengar, Sire of Famine, Flare of Fortitude,
 * Invert Polarity, Kami of Jealous Thirst and Cursed Mirror.
 */
class Mh3Chunk10WardConditionsScenariosTest {
    private val vorrak = Cards.invent("Vorrak, the Unmaker", "{10}", "Legendary Creature — Eldrazi", """
        When you cast this spell, target opponent exiles the top half of their library, rounded up.
        Ward—Sacrifice two permanents.
        Vorrak enters with a number of +1/+1 counters on it equal to the greatest mana value among cards in exile.
        Vorrak has annihilator X, where X is the number of +1/+1 counters on it.
    """, "7/7")
    private val gunner = Cards.invent("Shellbore Gunner", "{5}{U}", "Artifact Creature — Turtle Warrior", """
        Improvise (Your artifacts can help cast this spell. Each artifact you tap after you're done activating mana abilities pays for {1}.)
        Ward {4}
        Whenever this creature or another artifact you control enters, put a +1/+1 counter on this creature. It can't be blocked this turn.
    """, "4/4")
    private val rite = Cards.invent("Rite of Spawning", "{1}{G}", "Enchantment",
        "At the beginning of your end step, if you control a creature, look at the top seven cards of your library. Then you may sacrifice a creature. If you do, you may put a creature card with mana value X or less from among those cards onto the battlefield, where X is 1 plus the sacrificed creature's mana value. Put the rest on the bottom of your library in a random order.")
    private val acolyte = Cards.inventFaces("ModalDoubleFaced",
        face("Acolyte of the Grove", "{3}{G}{G}{G}", "Creature — Elf Druid",
            "When this creature enters, you may sacrifice another creature. If you do, you gain X life and draw X cards, where X is that creature's power.",
            "3/3", null, null),
        face("Grove of the Acolyte", null, "Land", "As this land enters, you may pay 3 life. If you don't, it enters tapped.\n{T}: Add {G}.", null, null, null))
    private val scales = Cards.invent("Scales of Retort", "{R}{R}", "Enchantment",
        "Whenever an opponent casts a spell, you may reveal the top card of your library. If you do, you may cast that card without paying its mana cost if the two spells have the same mana value.")
    private val maze = Cards.invent("Maze of the Spirit Dragon", null, "Land", """
        Imprint — When this land enters, you may exile a colorless card with mana value 7 or greater from your hand.
        {T}: Add {C}. If a card is exiled with this land, add {C}{C} instead.
        {T}: Return the exiled card to its owner's hand.
    """)
    private val dynamo = Cards.invent("Sparkcoil Dynamo", "{U}{R}", "Artifact", """
        If you would get one or more {E} (energy counters), you get that many plus one {E} instead.
        {T}: Draw a card. Activate only if you've paid or lost four or more {E} this turn.
    """)
    private val takedown = Cards.invent("Brutal Takedown", "{B}", "Sorcery", """
        As an additional cost to cast this spell, sacrifice a creature or sacrifice a modified creature. (Equipment, Auras you control, and counters are modifications.)
        Destroy target creature or planeswalker. If the modified creature was sacrificed, draw a card.
    """)
    private val vorsk = Cards.invent("Vorsk, Lord of Hunger", "{3}{B}{B}", "Legendary Creature — Elder Demon", """
        Flying
        Sacrifice another creature: Create a Blood token. If you sacrificed an Angel this way, create a number of Blood tokens equal to its toughness instead.
        {W/B}{W/B}{W/B}, Sacrifice six Blood tokens: Return each creature card from your graveyard to the battlefield with a finality counter on it. Those creatures are Vampires in addition to their other types.
    """, "6/6")
    private val blaze = Cards.invent("Blaze of Steadfastness", "{2}{W}{W}", "Instant", """
        You may sacrifice a nontoken white creature rather than pay this spell's mana cost.
        Until end of turn, your life total can't change, and permanents you control gain hexproof and indestructible.
    """)
    private val reverse = Cards.invent("Reverse Charge", "{U}{U}{R}", "Instant",
        "Choose target spell, then flip a coin. If you win the flip, gain control of that spell and you may choose new targets for it. If you lose the flip, counter that spell.")
    private val spirit = Cards.invent("Spirit of Envious Hunger", "{2}{B}", "Creature — Spirit", """
        Deathtouch
        {4}{B}: Each opponent loses 2 life and you gain 2 life. This ability costs {4}{B} less to activate if you've drawn three or more cards this turn. Activate only once each turn.
    """, "1/3")
    private val glass = Cards.invent("Haunted Looking Glass", "{2}{R}", "Artifact", """
        {T}: Add {R}.
        As this artifact enters, you may have it become a copy of any creature on the battlefield until end of turn, except it has haste.
    """)

    private val bear = Cards.invent("Reaping Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Tallowbone Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val seraph = Cards.invent("Gilded Seraph", "{3}{W}", "Creature — Angel", "Flying", "3/4")
    private val titan = Cards.invent("Hollow Titan", "{7}", "Creature — Eldrazi", "", "7/7")
    private val anvil = Cards.invent("Rust Anvil", "{3}", "Artifact", "")
    private val cinder = Cards.invent("Stray Cinder", "{R}", "Instant", "Stray Cinder deals 2 damage to any target.")
    private val bolt = Cards.invent("Shardless Bolt", "{2}", "Instant", "Shardless Bolt deals 2 damage to any target.")
    private val insight = Cards.invent("Quick Insight", "{1}{U}", "Instant", "Draw a card.")
    private val study = Cards.invent("Triple Study", "{2}{U}", "Sorcery", "Draw three cards.")
    private val flats = Cards.invent("Hollowstone Flats", null, "Land", "{T}: Add {C}.")
    private val spark = Cards.invent("Charged Spark", "{R}", "Sorcery", "You get {E}{E}.")
    private val battery = Cards.invent("Coil Battery", "{2}", "Artifact", "Pay {E}{E}{E}{E}: You gain 1 life.")

    /** Answers open "may" and yes/no decisions with yes, object choices and targets with [picks] in turn, resolving on. */
    private fun Scenario.drain(vararg picks: String) {
        var next = 0
        var guard = 30
        while (guard-- > 0) {
            when (pending) {
                is Decision.May -> answer(Answer.YesNo(true))
                is Decision.Targets -> targets(picks[next++])
                is Decision.ChooseObjects -> if (next < picks.size) alice.chooses(picks[next++]) else return
                null -> if (state.stack.isEmpty()) return else resolveAll()
                else -> return
            }
        }
    }

    // CR 603.6, 107.1a: as it is cast, the opponent exiles the top three of their five cards; CR 614.1c: it enters with counters equal to the
    // greatest mana value among cards in exile (the exiled Ogre's 3)
    @Test
    fun vorrakEntersWithTheGreatestManaValueInExile() = scenario {
        alice.battlefield(*Array(10) { flats })
        alice.hand(vorrak)
        bob.library(ogre, "Island", "Island", "Island", "Island")
        alice.casts(vorrak)
        drain()
        expect {
            bob.library.size shouldBe 2
            bob.exile.size shouldBe 3
            vorrak.counters(CounterKind.PowerToughness(1, 1)) shouldBe 3
            vorrak.power shouldBe 10
        }
    }

    // CR 702.86a: annihilator X with two +1/+1 counters: the defending player sacrifices two permanents
    @Test
    fun vorrakAnnihilatesItsCounters() = scenario {
        alice.battlefield(vorrak, counters = listOf(CounterKind.PowerToughness(1, 1) to 2))
        bob.battlefield(bear, ogre, "Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(vorrak)
        resolveAll()
        bob.chooses(bear, "Island")
        resolveAll()
        expect {
            bob.battlefield.names shouldBe listOf(ogre)
            bob.graveyard.size shouldBe 2
        }
    }

    // Audit: "It can't be blocked this turn" is this creature, also when another artifact entered (CR 603.2)
    @Test
    fun gunnerIsUnblockableWhenAnotherArtifactEnters() = scenario {
        alice.battlefield(gunner, flats, flats, flats)
        alice.hand(anvil)
        alice.casts(anvil)
        resolveAll()
        expect {
            gunner.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            gunner hasMechanic { it == Mechanic.CantBeBlocked }
        }
    }

    // CR 603.4, 608.2h: the sacrificed Bear's mana value 2 allows a creature card with mana value 3 or less; the rest go to the bottom
    @Test
    fun riteTradesUpByOneManaValue() = scenario {
        alice.battlefield(rite, bear)
        alice.library(seraph, ogre, "Forest")
        advanceTo(TurnStep.End)
        drain()
        assertTrue(pending is Decision.ChooseObjects, "the Seraph (mana value 4) can't be chosen: ${pending}")
        val options = (pending as Decision.ChooseObjects).options
        assertEquals(1, options.size)
        alice.chooses(ogre)
        resolveAll()
        expect {
            alice.battlefield has ogre
            alice.graveyard has bear
            alice.library.size shouldBe 2
        }
    }

    // CR 608.2h: X is the sacrificed Bear's power (2), not this creature's (3)
    @Test
    fun acolyteGainsAndDrawsTheSacrificedPower() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", bear)
        alice.hand(acolyte)
        alice.library("Island", "Island", "Island", "Island")
        alice.casts(acolyte)
        drain()
        expect {
            alice.graveyard has bear
            alice.life shouldBe 22
            alice.hand.size shouldBe 2
        }
    }

    // CR 601.3, 608.2g: the revealed card has the opponent's spell's mana value (2): cast without paying, on top of it
    @Test
    fun scalesCastsTheRevealedCardWithTheSameManaValue() = scenario {
        turn(number = 2, active = bob)
        alice.battlefield(scales)
        alice.library(insight, "Island")
        bob.battlefield(flats, flats)
        bob.hand(bolt)
        bob.casts(bolt, target = "alice")
        drain()
        expect {
            alice.hand has "Island"
            alice.graveyard has insight
            alice.life shouldBe 18
        }
    }

    // A revealed card with another mana value (1, against 2) can't be cast and stays on top
    @Test
    fun scalesDoesNotCastADifferentManaValue() = scenario {
        turn(number = 2, active = bob)
        alice.battlefield(scales)
        alice.library(cinder, "Island")
        bob.battlefield(flats, flats)
        bob.hand(bolt)
        bob.casts(bolt, target = "alice")
        drain()
        expect {
            alice.library.names shouldBe listOf(cinder, "Island")
            alice.life shouldBe 18
        }
    }

    // CR 607.2a: with a card exiled with it the land adds {C}{C}; once that card has left exile it adds {C} again
    @Test
    fun mazeAddsTwoWhileACardIsExiledWithIt() = scenario {
        alice.hand(maze, titan)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        alice.plays(maze)
        drain(titan)
        expect { titan.zone shouldBe Zone.Exile }
        alice.activates(maze, ability = 0)
        assertEquals(2, alice.pool.size)
    }

    @Test
    fun mazeAddsOneAfterTheExiledCardReturns() = scenario {
        alice.hand(maze, titan)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        alice.plays(maze)
        drain(titan)
        alice.activates(maze, ability = 1)
        resolveAll()
        expect { alice.hand has titan }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.activates(maze, ability = 0)
        assertEquals(1, alice.pool.size)
    }

    // CR 614.1a: two energy become three
    @Test
    fun dynamoAddsOneEnergy() = scenario {
        alice.battlefield(dynamo, "Mountain")
        alice.hand(spark)
        alice.casts(spark)
        resolveAll()
        expect { alice.counters(CounterKind.Energy) shouldBe 3 }
    }

    // CR 602.5b, 107.14: the draw ability works only after four energy were paid this turn
    @Test
    fun dynamoDrawsAfterFourEnergyPaid() = scenario {
        alice.battlefield(dynamo, battery)
        alice.counters(CounterKind.Energy, 4)
        alice.library("Island")
        assertFailsWith<IllegalArgumentException> { alice.activates(dynamo) }
        alice.activates(battery)
        resolveAll()
        alice.activates(dynamo)
        resolveAll()
        expect {
            alice.counters(CounterKind.Energy) shouldBe 0
            alice.hand has "Island"
            alice.life shouldBe 21
        }
    }

    // CR 700.9, 608.2h: the creature sacrificed had a +1/+1 counter: a card is drawn
    @Test
    fun takedownDrawsForAModifiedCreature() = scenario {
        alice.battlefield("Swamp")
        alice.battlefield(bear, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        alice.library("Island")
        alice.hand(takedown)
        bob.battlefield(ogre)
        alice.casts(takedown, target = ogre)
        (pending as? Decision.ChooseOption)?.let { answer(Answer.Option(it.options.last())) }
        if (pending is Decision.ManaPayment) paySuggested()
        resolveAll()
        expect {
            bob.graveyard has ogre
            alice.graveyard has bear
            alice.hand has "Island"
        }
    }

    // An unmodified creature sacrificed: no card
    @Test
    fun takedownDoesNotDrawForAnUnmodifiedCreature() = scenario {
        alice.battlefield("Swamp", bear)
        alice.library("Island")
        alice.hand(takedown)
        bob.battlefield(ogre)
        alice.casts(takedown, target = ogre)
        (pending as? Decision.ChooseOption)?.let { answer(Answer.Option(it.options.first())) }
        if (pending is Decision.ManaPayment) paySuggested()
        resolveAll()
        expect {
            bob.graveyard has ogre
            alice.graveyard has bear
            alice.hand.size shouldBe 0
        }
    }

    // CR 608.2h: an Angel with toughness 4 sacrificed: four Blood tokens instead of one
    @Test
    fun vorskMakesBloodForTheAngelsToughness() = scenario {
        alice.battlefield(vorsk, seraph)
        alice.activates(vorsk, ability = 0)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Blood Token" } shouldBe 4 }
    }

    // CR 119.7, 119.8: the life total can't change: damage doesn't lower it
    @Test
    fun blazeKeepsTheLifeTotal() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(blaze)
        bob.battlefield("Mountain")
        bob.hand(cinder)
        alice.casts(blaze)
        resolveAll()
        alice.passes()
        bob.casts(cinder, target = "alice")
        resolveAll()
        expect {
            alice.life shouldBe 20
            bob.graveyard has cinder
        }
    }

    // CR 705, 115.7: the flip won: the spell is yours and you choose its new target
    @Test
    fun reverseChargeWonTakesTheSpell() = scenario(seed = 2) {
        turn(number = 2, active = bob)
        alice.battlefield("Island", "Island", "Mountain")
        bob.battlefield("Mountain")
        bob.hand(cinder)
        alice.hand(reverse)
        bob.casts(cinder, target = "alice")
        bob.passes()
        alice.casts(reverse, target = cinder)
        resolve()
        drain("bob")
        resolveAll()
        expect {
            alice.life shouldBe 20
            bob.life shouldBe 18
        }
    }

    // CR 705, 701.6a: the flip lost: the spell is countered
    @Test
    fun reverseChargeLostCountersTheSpell() = scenario(seed = 1) {
        turn(number = 2, active = bob)
        alice.battlefield("Island", "Island", "Mountain")
        bob.battlefield("Mountain")
        bob.hand(cinder)
        alice.hand(reverse)
        bob.casts(cinder, target = "alice")
        bob.passes()
        alice.casts(reverse, target = cinder)
        resolveAll()
        expect {
            alice.life shouldBe 20
            bob.life shouldBe 20
            bob.graveyard has cinder
        }
    }

    // CR 601.2f, 602.2b: after three cards drawn this turn the ability costs nothing
    @Test
    fun spiritCostsNothingAfterThreeDraws() = scenario {
        alice.battlefield(spirit, "Island", "Island", "Island")
        alice.hand(study)
        alice.library("Island", "Island", "Island")
        alice.casts(study)
        resolveAll()
        alice.activates(spirit)
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    // CR 614.12, 707.9b: it enters as a copy of the Ogre with haste; CR 611.2a, 514.2: at the cleanup step it is the artifact again
    @Test
    fun glassIsACopyUntilEndOfTurn() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(glass)
        alice.library("Island")
        bob.battlefield(ogre)
        bob.library("Island")
        alice.casts(glass)
        drain(ogre)
        expect {
            card(glass).power shouldBe 3
            card(glass) hasKeyword Keyword.Haste.kind
            assertTrue(CardType.Creature in card(glass).types)
        }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect {
            assertTrue(CardType.Creature !in card(glass).types)
            card(glass) lacksKeyword Keyword.Haste.kind
        }
    }
}
