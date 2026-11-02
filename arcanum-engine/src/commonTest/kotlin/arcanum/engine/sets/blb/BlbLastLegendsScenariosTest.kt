package arcanum.engine.sets.blb

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertTrue

/** Bloomburrow's last legends and an artifact (blb gaps 2), played with invented cards of the parser's chunk 15. */
class BlbLastLegendsScenariosTest {
    private val mirelle = Cards.invent("Mirelle, Branching Tempest", "{3}{U}{R}", "Legendary Creature — Otter Wizard",
        "Whenever you cast a spell, if it's the first instant spell, the first sorcery spell, or the first Otter spell other than Mirelle you've cast this turn, you may have target opponent draw a card. If you do, copy that spell. You may choose new targets for the copy.",
        "3/5")
    private val wenna = Cards.invent("Wenna, Startled Oracle", "{G}{W}{U}", "Legendary Creature — Frog Druid", """
        Whenever you cast a creature spell with mana value 4 or greater, you draw a card, gain 1 life, and put a +1/+1 counter on Wenna.
        {T}: Add X mana of any one color, where X is Wenna's power. Spend this mana only to cast creature spells with mana value 4 or greater or creature spells with {X} in their mana costs.
    """, "1/3")
    private val thornbristle = Cards.invent("Thornbristle, Grim Warden", "{X}{R}{R}{G}{G}", "Legendary Creature — Badger Warrior", """
        Trample
        When Thornbristle enters, exile the top X cards of your library. Until the end of your next turn, you may play those cards.
        You may play an additional land on each of your turns.
    """, "5/5")
    private val snaketooth = Cards.invent("The Notorious Snaketooth", "{1}{B}{R}", "Legendary Creature — Weasel Mercenary", """
        Menace
        Whenever The Notorious Snaketooth deals combat damage to a player, exile cards from the top of your library until you exile a nonland card. You may cast that card by discarding a card rather than paying its mana cost.
    """, "3/3")
    private val feathra = Cards.invent("Feathra, the Gale-Crowned", "{3}{W}{U}", "Legendary Creature — Bird Scout", """
        Flying
        Whenever one or more Birds you control deal combat damage to a player, choose one —
        • You may put a Bird creature card from your hand or graveyard onto the battlefield with a finality counter on it.
        • Put a +1/+1 counter on each Bird you control.
        • Draw a card.
    """, "4/5")
    private val zelka = Cards.invent("Zelka, Sparking Wit", "{2}{U}{R}", "Legendary Planeswalker — Ral", """
        Whenever you cast a noncreature spell, put a loyalty counter on Zelka.
        [+1]: Create a 1/1 blue and red Otter creature token with prowess.
        [−3]: Draw three cards, then discard two cards.
        [−10]: Draw three cards. You get an emblem with "Instant and sorcery spells you cast have storm." (Whenever you cast an instant or sorcery spell, copy it for each spell cast before it this turn.)
    """, loyalty = "4")
    private val gnawk = Cards.invent("Gnawk, the Unyielding", "{2}{U}{B}", "Legendary Creature — Rat Rogue", """
        Ward {2}
        If a creature an opponent controls would die, exile it instead.
        At the beginning of each end step, create X 1/1 black Rat creature tokens with "This token gets +1/+1 for each other Rat you control," where X is the number of creatures that were exiled under your opponents' control this turn.
    """, "3/4")
    private val chronicle = Cards.invent("Inherited Chronicle", "{1}", "Artifact — Book",
        "{4}, {T}: Draw a card. For each mana in this ability's activation cost, you may tap an untapped creature you control rather than pay that mana. Activate only as a sorcery.")

    private val spark = "Storm Spark" // an instant: 3 damage to any target
    private val musing = Cards.invent("Tidal Musing", "{U}", "Sorcery", "Draw a card.")
    private val otter = Cards.invent("Brookpaw Otter", "{1}{U}", "Creature — Otter", "", "2/2")
    private val lark = Cards.invent("Gustwing Lark", "{1}{W}", "Creature — Bird", "Flying", "1/1")
    private val ooze = Cards.invent("Swelling Ooze", "{X}{G}", "Creature — Ooze", "This creature enters with X +1/+1 counters on it.", "0/0")
    private val boar = Cards.invent("Hulking Boar", "{3}{G}", "Creature — Boar", "", "4/4")
    private val sentry = Cards.invent("Hedge Sentry", "{1}{W}", "Creature — Rabbit Soldier", "", "1/1")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private fun Scenario.yes() = answer(Answer.YesNo(true))

    /** "You may choose new targets for the copy": keeps them. */
    private fun Scenario.keepTargets() {
        if (pending is Decision.May) answer(Answer.YesNo(false))
    }

    // ---- Mirelle (the first instant, sorcery or Otter spell of the turn)

    /** The first instant of the turn: the opponent draws, and the spell is copied. */
    // CR 603.4, 707.10, 707.10c
    @Test
    fun firstInstantIsCopiedForACard() = scenario {
        alice.battlefield(mirelle, "Mountain", "Mountain")
        alice.hand(spark, spark)
        bob.library("Island")

        alice.casts(spark, target = "bob")
        allPass()
        yes()
        keepTargets()
        allPass()
        allPass()
        expect {
            bob.life shouldBe 14
            bob.hand.size shouldBe 1
        }
        // the second instant of the turn doesn't trigger
        alice.casts(spark, target = "bob")
        expect { stack.names shouldBe listOf(spark) }
    }

    /** Declining the draw: no copy. */
    // CR 118.12: "if you do"
    @Test
    fun noDrawNoCopy() = scenario {
        alice.battlefield(mirelle, "Mountain")
        alice.hand(spark)
        bob.library("Island")

        alice.casts(spark, target = "bob")
        allPass()
        answer(Answer.YesNo(false))
        allPass()
        expect {
            bob.life shouldBe 17
            bob.hand.size shouldBe 0
        }
    }

    /** A sorcery after an instant is still the first sorcery. */
    // CR 603.4
    @Test
    fun firstSorceryAfterAnInstant() = scenario {
        alice.battlefield(mirelle, "Mountain", "Island")
        alice.hand(spark, musing)
        alice.library("Forest", "Forest")
        bob.library("Island")

        alice.casts(spark, target = "bob")
        allPass()
        answer(Answer.YesNo(false))
        allPass()
        alice.casts(musing)
        expect { stack.names.size shouldBe 2 }
        allPass()
        yes()
        allPass()
        allPass()
        expect { alice.hand.size shouldBe 2 }
    }

    /** Mirelle cast as an Otter spell doesn't count: the next Otter spell is the first one "other than Mirelle", and its
     * copy becomes a token. */
    // CR 400.7, 707.10f
    @Test
    fun mirelleItselfIsNotCounted() = scenario {
        alice.battlefield("Island", "Island", "Island", "Mountain", "Mountain", "Island", "Island")
        alice.hand(mirelle, otter)
        bob.library("Island")

        alice.casts(mirelle)
        allPass()
        expect { alice.battlefield has mirelle }
        alice.casts(otter)
        allPass()
        yes()
        allPass()
        allPass()
        expect {
            alice.battlefield.names.count { it == otter } shouldBe 2
            bob.hand.size shouldBe 1
        }
    }

    // ---- Wenna (creature spells with mana value 4 or greater; restricted mana)

    /** A 4-mana creature spell: a card, 1 life and a +1/+1 counter. */
    // CR 202.3
    @Test
    fun bigCreatureSpellTriggers() = scenario {
        alice.battlefield(wenna, "Forest", "Forest", "Forest", "Forest")
        alice.hand(boar)
        alice.library("Island")

        alice.casts(boar)
        allPass()
        expect {
            alice.hand.size shouldBe 1
            alice.life shouldBe 21
            card(wenna).counters(p1p1) shouldBe 1
        }
    }

    /** Power 2: two mana of one colour, spent on a creature spell with {X} in its mana cost (the whole cost, not only X). */
    // CR 106.6, 107.3
    @Test
    fun manaPaysForAnXCreature() = scenario {
        alice.battlefield(wenna, counters = listOf(p1p1 to 1))
        alice.hand(ooze, spark)

        alice.activates(wenna, ability = 0, color = arcanum.api.Color.Green)
        expect { alice.pool.size shouldBe 2 }
        // the mana can't be spent on an instant
        assertTrue(Engine.options(state, alice.id).none { it is Input.Cast && it.card == card(spark) })
        alice.casts(ooze, x = 1)
        allPass()
        expect { card(ooze).counters(p1p1) shouldBe 1 }
    }

    // ---- Thornbristle (X of its spell in its enters trigger)

    /** Cast with X = 2: the top two cards are exiled, and a land among them may be played. */
    // CR 107.3m, 305.2
    @Test
    fun exilesTheTopXCards() = scenario {
        alice.battlefield("Mountain", "Mountain", "Forest", "Forest", "Forest", "Forest")
        alice.hand(thornbristle)
        alice.library("Forest", "Hulking Boar", "Island")

        alice.casts(thornbristle, x = 2)
        allPass()
        allPass()
        expect {
            alice.exile.names.toSet() shouldBe setOf<String?>("Forest", boar)
            alice.library.size shouldBe 1
        }
        alice.plays("Forest")
        expect { alice.battlefield.names.count { it == "Forest" } shouldBe 5 }
    }

    // ---- Snaketooth (exile until a nonland card, cast it by discarding)

    /** The nonland card is cast by discarding a card instead of its mana cost. */
    // CR 118.9, 608.2g
    @Test
    fun castsTheExiledCardByDiscarding() = scenario {
        alice.battlefield(snaketooth)
        alice.hand("Swamp", "Plains")
        alice.library("Forest", spark, "Island")

        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(snaketooth)
        advanceTo(TurnStep.CombatDamage)
        allPass()
        yes()
        // CR 601.2c, h: the target is chosen before the discard is paid
        answer(Answer.Targets(listOf(target("bob"))))
        alice.chooses("Swamp")
        allPass()
        expect {
            bob.life shouldBe 14
            alice.exile.names shouldBe listOf("Forest")
            alice.graveyard.names.toSet() shouldBe setOf<String?>("Swamp", spark)
            alice.hand.names shouldBe listOf<String?>("Plains")
        }
    }

    // ---- Feathra (finality counter)

    /** A Bird from the graveyard enters with a finality counter; dying, it is exiled instead. */
    // CR 122.1h
    @Test
    fun birdReturnsWithAFinalityCounter() = scenario {
        alice.battlefield(feathra)
        alice.graveyard(lark)
        bob.battlefield("Mountain")
        bob.hand(spark)

        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(feathra)
        advanceTo(TurnStep.CombatDamage)
        allPass()
        answer(Answer.Modes(listOf(0)))
        allPass()
        alice.chooses(lark)
        expect { card(lark).counters(CounterKind.Finality) shouldBe 1 }
        alice.passes()
        bob.casts(spark, target = lark)
        allPass()
        expect {
            alice.exile has lark
            alice.graveyard lacks lark
        }
    }

    /** A finality counter works on its own, whoever put it there. */
    // CR 122.1h
    @Test
    fun finalityCounterOnItsOwn() = scenario {
        alice.battlefield(lark, counters = listOf(CounterKind.Finality to 1))
        alice.battlefield("Mountain")
        alice.hand(spark)
        alice.casts(spark, target = lark)
        allPass()
        expect {
            alice.exile has lark
            alice.graveyard lacks lark
        }
    }

    // ---- Zelka (an emblem with storm)

    /** The emblem gives the second instant of the turn storm: one copy. */
    // CR 114.4, 702.40a
    @Test
    fun emblemGivesStorm() = scenario {
        alice.battlefield(zelka, counters = listOf(CounterKind.Loyalty to 10))
        alice.battlefield("Mountain", "Mountain")
        alice.hand(spark, spark)
        alice.library("Island", "Island", "Island")

        alice.activates(zelka, ability = 2)
        allPass()
        expect {
            alice.graveyard has zelka
            alice.hand.size shouldBe 5
        }
        // the first instant of the turn: storm with no spell before it, no copy
        alice.casts(spark, target = "bob")
        allPass()
        allPass()
        expect { bob.life shouldBe 17 }
        // the second: one copy
        alice.casts(spark, target = "bob")
        allPass()
        keepTargets()
        allPass()
        allPass()
        expect { bob.life shouldBe 11 }
    }

    // ---- Gnawk (dying replaced; Rats for creatures exiled under opponents' control)

    /** An opponent's creature that would die is exiled; at the end step a Rat is created for it. */
    // CR 614.1a, 700.4
    @Test
    fun opponentsCreaturesAreExiled() = scenario {
        alice.battlefield(gnawk, sentry, "Mountain", "Mountain", "Mountain")
        alice.hand(spark, spark, spark)
        bob.battlefield(boar)

        alice.casts(spark, target = boar)
        allPass()
        alice.casts(spark, target = boar)
        allPass()
        // its controller's own creatures die as usual
        alice.casts(spark, target = sentry)
        allPass()
        expect {
            bob.exile has boar
            bob.graveyard lacks boar
            alice.graveyard has sentry
        }
        advanceTo(TurnStep.End)
        allPass()
        expect {
            alice.battlefield.names.count { it == "Rat Token" } shouldBe 1
            // +1/+1 for the other Rat, Gnawk itself
            "Rat Token".power shouldBe 2
        }
    }

    // ---- Inherited Chronicle (tapping creatures for an activation's mana)

    /** Four creatures tapped pay the {4}. */
    @Test
    fun creaturesPayTheActivation() = scenario {
        alice.battlefield(chronicle, sentry, sentry, sentry, sentry)
        alice.library("Island")

        alice.activates(chronicle)
        allPass()
        expect {
            alice.hand.size shouldBe 1
            (0..3).all { card(sentry, it).tapped } shouldBe true
        }
    }
}
