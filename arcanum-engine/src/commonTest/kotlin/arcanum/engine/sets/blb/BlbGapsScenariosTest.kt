package arcanum.engine.sets.blb

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.change.Changes
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Bloomburrow's last wordings (blb gaps), played with the invented cards of the parser's chunk 14. */
class BlbGapsScenariosTest {
    private val beastwarden = Cards.invent("Cerulean Beastwarden", "{1}{U}", "Creature — Rat Rogue", """
        Vigilance
        This creature can't be blocked by creatures with power 2 or greater.
        Whenever this creature attacks, up to one target artifact, creature, or planeswalker an opponent controls loses all abilities until your next turn. If it's a creature, it also has base power and toughness 2/2 until your next turn.
    """, "1/3")
    private val morrow = Cards.invent("Morrow, the Endless Tide", "{1}{U}{U}{U}", "Legendary Creature — Elemental Fish", """
        Morrow's power and toughness are each equal to the number of Islands you control.
        Whenever Morrow enters or attacks, put a flood counter on target land. It's an Island in addition to its other types for as long as it has a flood counter on it.
        The first instant or sorcery spell you cast each turn costs {U} (or {1}) less to cast for each land you control with a flood counter on it.
    """, "*/*")
    private val skulker = Cards.invent("Wide Creek Skulker", "{2}{U}", "Creature — Frog Scout", """
        Ward {1}
        Other Frogs you control have ward {1}.
        When this creature enters, target creature you control can't be blocked this turn. Whenever that creature deals combat damage this turn, you may exile it. If you do, return it to the battlefield under its owner's control.
    """, "2/3")
    private val thrush = Cards.invent("Echo Thrush", "{X}{U}", "Creature — Bird Bard", """
        Flying
        You may have this creature enter as a copy of any creature on the battlefield with mana value less than or equal to the amount of mana spent to cast this creature, except it's a Bird in addition to its other types and it has flying.
    """, "1/1")
    private val omen = Cards.invent("Omen of Ruin", "{X}{U}", "Sorcery",
        "Reveal the top X cards of your library. For each card type, you may exile a card of that type from among them. Put the rest into your graveyard. You may cast a spell from among the exiled cards without paying its mana cost if you exiled four or more cards this way. Then put the rest of the exiled cards into your hand.")
    private val adder = Cards.invent("Rotfang Adder", "{5}{B}", "Creature — Elemental Snake", """
        As an additional cost to cast this spell, you may sacrifice any number of nonland permanents. This spell costs {1} less to cast for each permanent sacrificed this way.
        Whenever this creature enters or attacks, put a blight counter on it. Then for each blight counter on it, each opponent loses 4 life unless that player sacrifices a nonland permanent of their choice or discards a card.
    """, "6/6")
    private val stormwing = Cards.invent("Stormwing, Doom's Squall", "{3}{R}{R}", "Legendary Creature — Bird Dragon", """
        Flying
        Whenever Stormwing enters or attacks, exile the top X cards of your library, where X is the number of creatures you control with power 4 or greater. You may play those cards until your next end step. At the beginning of your next end step, Stormwing deals 2 damage to each opponent for each of those cards that are still exiled.
    """, "5/5")
    private val conscriptor = Cards.invent("Saurian Conscriptor", "{3}{R}{R}", "Creature — Lizard Warrior", """
        Trample
        When this creature enters, choose target creature. If that creature's power is 2 or less or if you control another Lizard, gain control of that creature until end of turn, untap it, and it gains haste until end of turn.
    """, "4/2")

    private val drake = Cards.invent("Cloud Drake", "{3}{U}", "Creature — Drake", "Flying", "4/4")
    private val banner = Cards.invent("Rally Banner", "{3}", "Artifact", "Creatures you control get +1/+1.")
    private val vole = Cards.invent("Mossback Vole", "{2}{G}", "Creature — Mouse", "", "3/3")
    private val shrew = Cards.invent("Tiny Shrew", "{W}", "Creature — Mouse", "", "1/1")
    private val behemoth = Cards.invent("Moss Behemoth", "{4}{G}", "Creature — Beast", "", "5/5")
    private val golem = Cards.invent("Brass Golem", "{2}", "Artifact Creature — Golem", "", "2/2")
    private val spark = Cards.invent("Twin Spark", "{1}{R}", "Instant", "Twin Spark deals 1 damage to any target.")
    private val salve = Cards.invent("Small Salve", "{W}", "Instant", "You gain 2 life.")
    private val insight = Cards.invent("Quick Insight", "{1}{U}", "Instant", "Draw a card.")
    private val giant = Cards.invent("Hill Brute", "{3}{R}", "Creature — Giant", "", "4/4")
    private val gecko = Cards.invent("Sun Gecko", "{R}", "Creature — Lizard", "", "1/1")

    private val flood = CounterKind.Named("flood")
    private val blight = CounterKind.Named("blight")

    private fun Scenario.chooseTargets(vararg names: String) {
        if (pending is Decision.Targets) answer(Answer.Targets(names.map { target(it) }))
    }

    // ---- Losing all abilities until your next turn

    /** The creature loses flying and is a 2/2 through the opponent's turn, and is itself again on the attacker's next turn. */
    // CR 613.1f, 613.4b, 611.2a: layers 6 and 7b, until your next turn
    @Test
    fun creatureLosesAbilitiesAndIsTwoTwoUntilYourNextTurn() = scenario {
        alice.battlefield(beastwarden)
        bob.battlefield(drake)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(beastwarden)
        chooseTargets(drake)
        allPass()
        expect {
            drake.power shouldBe 2
            drake.toughness shouldBe 2
            drake lacksKeyword Keyword.Flying
        }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        expect {
            drake.power shouldBe 2
            drake lacksKeyword Keyword.Flying
        }
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect {
            drake.power shouldBe 4
            drake hasKeyword Keyword.Flying
        }
    }

    /** An artifact that isn't a creature loses its anthem; it gets no power and toughness. */
    // CR 613.1f; "if it's a creature" is checked as the ability resolves (CR 608.2c)
    @Test
    fun artifactLosesItsAbilityOnly() = scenario {
        alice.battlefield(beastwarden)
        bob.battlefield(banner, vole)
        expect { vole.power shouldBe 4 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(beastwarden)
        chooseTargets(banner)
        allPass()
        expect {
            vole.power shouldBe 3
            banner.power shouldBe null
        }
    }

    // ---- Flood counters and the first instant or sorcery each turn

    /** The flooded land is an Island too while the counter stays, so it counts for the creature's power. */
    // CR 305.7: "in addition to its other types" keeps its types; CR 611.2b: the effect ends with the counter
    @Test
    fun floodedLandIsAnIslandWhileItHasTheCounter() = scenario {
        alice.battlefield(morrow, "Island", "Island", "Plains")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(morrow)
        chooseTargets("Plains")
        allPass()
        expect {
            "Plains".counters(flood) shouldBe 1
            "Plains".subtypes shouldBe listOf(Subtype("Plains"), Subtype("Island"))
            morrow.power shouldBe 3
        }
        change(Changes.counters(state, TargetRef.Object(card("Plains")), flood, -1))
        expect {
            "Plains".subtypes shouldBe listOf(Subtype("Plains"))
            morrow.power shouldBe 2
        }
    }

    /** With two flooded lands, the first instant costs {U}{U} less: {1}{U} is paid with nothing. The second costs it all. */
    // CR 118.7c: {U}{U} less than {1}{U} leaves nothing; CR 601.2f, 601.2i: only the first such spell of the turn
    @Test
    fun firstInstantEachTurnCostsLess() = scenario {
        alice.battlefield(morrow, "Island", "Island")
        alice.battlefield("Plains", "Mountain", counters = listOf(flood to 1))
        alice.hand(insight, insight)
        alice.library("Forest", "Forest")

        alice.casts(insight)
        expect { tappedLands() shouldBe 0 }
        allPass()
        alice.casts(insight)
        allPass()
        expect {
            alice.hand.names shouldBe listOf("Forest", "Forest")
            tappedLands() shouldBe 2
        }
    }

    private fun arcanum.engine.scenario.Expectations.tappedLands(): Int =
        view.objects.values.count { it.zone == Zone.Battlefield && CardType.Land in it.characteristics?.types.orEmpty() && it.obj?.tapped == true }

    /** A generic cost is reduced by the {U} it doesn't need. */
    // CR 118.7b
    @Test
    fun blueReductionReducesGenericMana() = scenario {
        val idea = Cards.invent("Plain Idea", "{2}", "Instant", "Draw a card.")
        alice.battlefield(morrow, "Island", "Island", "Island")
        alice.battlefield("Plains", counters = listOf(flood to 1))
        alice.hand(idea)
        alice.library("Forest")

        alice.casts(idea)
        expect { tappedLands() shouldBe 1 }
    }

    // ---- A creature that can't be blocked and blinks after dealing combat damage

    /** The creature deals its damage unblocked, then is exiled and returns as a new, untapped object. */
    // CR 603.7b: a delayed trigger with a duration; CR 400.7: it returns as a new object
    @Test
    fun unblockableCreatureBlinksAfterCombatDamage() = scenario {
        alice.battlefield(vole, "Island", "Island", "Island")
        alice.hand(skulker)
        bob.battlefield(drake)

        alice.casts(skulker)
        allPass()
        chooseTargets(vole)
        allPass()
        expect { vole hasMechanic { it == arcanum.api.Mechanic.CantBeBlocked } }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(vole)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(drake to vole) }
        advanceTo(TurnStep.CombatDamage)
        allPass()
        answer(Answer.YesNo(true))
        expect {
            bob.life shouldBe 17
            vole.zone shouldBe Zone.Battlefield
            card(vole).tapped shouldBe false
        }
    }

    // ---- Entering as a copy of a creature with a limited mana value

    /** X = 2 spends three mana: a mana value 3 creature may be copied, a mana value 5 one may not. The copy is a Bird with
     * flying too. */
    // CR 614.1c, 707.9b; CR 400.7d: the mana spent to cast it
    @Test
    fun entersAsACopyWithinTheManaSpent() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(thrush)
        bob.battlefield(vole, behemoth)

        alice.casts(thrush, x = 2)
        allPass()
        val d = decision<Decision.ChooseObjects>()
        assertEquals(listOf(card(vole, zone = Zone.Battlefield)), d.options)
        alice.chooses(vole)
        expect {
            card(thrush).view.characteristics?.name shouldBe vole
            card(thrush).power shouldBe 3
            card(thrush).subtypes shouldBe listOf(Subtype("Mouse"), Subtype("Bird"))
            card(thrush) hasKeyword Keyword.Flying
        }
    }

    // ---- One card of each card type

    /** An artifact creature card goes as the artifact next to a creature card; four cards exiled let one be cast free, the
     * others go to the hand. */
    // CR 205.2a; CR 608.2g: the spell is cast while this one resolves
    @Test
    fun oneCardOfEachTypeAndAFreeSpell() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(omen)
        alice.library(golem, vole, "Forest", spark, salve)

        alice.casts(omen, x = 5)
        allPass()
        assertFailsWith<IllegalArgumentException> { alice.chooses(spark, salve) }
        alice.chooses(golem, vole, "Forest", spark)
        expect { alice.graveyard.names shouldBe listOf(salve) }
        alice.chooses(spark)
        chooseTargets("bob")
        expect {
            alice.hand.names.sortedBy { it } shouldBe listOf(golem, "Forest", vole).sortedBy { it }
            stack has spark
        }
        allPass()
        expect { bob.life shouldBe 19 }
    }

    /** Three cards exiled: nothing may be cast; all three go to the hand. */
    @Test
    fun fewerThanFourExiledCastsNothing() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(omen)
        alice.library(vole, "Forest", spark)

        alice.casts(omen, x = 3)
        allPass()
        alice.chooses(vole, "Forest", spark)
        expect {
            alice.hand.names.size shouldBe 3
            alice.graveyard.names shouldBe listOf(omen)
        }
    }

    // ---- Sacrificing any number of permanents to reduce the cost

    /** Two permanents sacrificed make it cost {3}{B}. */
    // CR 601.2b, 601.2f: the sacrifices are chosen, the total cost worked out, then paid (601.2h)
    @Test
    fun sacrificesReduceTheCost() = scenario {
        alice.battlefield(vole, shrew, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(adder)
        bob.battlefield(drake)

        alice.casts(adder, choosing = listOf(vole, shrew))
        expect {
            alice.graveyard.names.sortedBy { it } shouldBe listOf(vole, shrew).sortedBy { it }
            (0..5).count { card("Swamp", it).tapped } shouldBe 4
        }
    }

    /** Attacking with one blight counter makes two: each time, the opponent may pay; they discard once, then can't pay. */
    // CR 118.12a, 101.4; CR 608.2h: the counters are counted once
    @Test
    fun blightCountersRepeatThePunisher() = scenario {
        alice.battlefield(adder, counters = listOf(blight to 1))
        bob.hand("Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(adder)
        allPass()
        // bob pays the first time by discarding (the only cost he can pay), then can't pay
        answer(Answer.YesNo(true))
        if (pending is Decision.ChooseOption) answer(Answer.Option(decision<Decision.ChooseOption>().options.first { "iscard" in it }))
        if (pending is Decision.ChooseObjects) bob.chooses("Island")
        expect {
            adder.counters(blight) shouldBe 2
            bob.graveyard.names shouldBe listOf("Island")
            bob.life shouldBe 16
        }
    }

    // ---- Playing exiled cards until your next end step

    /** Two creatures with power 4 or more exile two cards; the land is played, so one card is left: 2 damage at the end step. */
    // CR 107.3c, 603.7a, 400.7: a card played from exile is a new object, no longer "still exiled"
    @Test
    fun exiledCardsLeftDealDamageAtYourNextEndStep() = scenario {
        alice.battlefield(stormwing, giant)
        alice.library("Mountain", spark)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(stormwing)
        allPass()
        expect { alice.exile.names.sortedBy { it } shouldBe listOf("Mountain", spark).sortedBy { it } }
        advanceTo(TurnStep.PostcombatMain)
        expect { bob.life shouldBe 15 }
        alice.plays("Mountain")
        advanceTo(TurnStep.End)
        allPass()
        expect {
            bob.life shouldBe 13
            alice.battlefield has "Mountain"
        }
    }

    // ---- Gaining control if a condition holds

    /** A creature with power 1 is taken for the turn, untapped and with haste, and goes back at end of turn. */
    // CR 613.1b, 611.2a
    @Test
    fun takesASmallCreatureUntilEndOfTurn() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(conscriptor)
        bob.battlefield(shrew, tapped = true)

        alice.casts(conscriptor)
        allPass()
        chooseTargets(shrew)
        allPass()
        expect {
            shrew.controller shouldBe alice.id
            shrew.tapped shouldBe false
            shrew hasKeyword Keyword.Haste
        }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { shrew.controller shouldBe bob.id }
    }

    /** A 3/3 stays where it is, unless another Lizard is there. */
    // CR 608.2c: the condition is checked as the ability resolves
    @Test
    fun largerCreatureNeedsAnotherLizard() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(conscriptor)
        bob.battlefield(vole)

        alice.casts(conscriptor)
        allPass()
        chooseTargets(vole)
        allPass()
        expect { vole.controller shouldBe bob.id }
    }

    @Test
    fun anotherLizardTakesALargerCreature() = scenario {
        alice.battlefield(gecko, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(conscriptor)
        bob.battlefield(vole)

        alice.casts(conscriptor)
        allPass()
        chooseTargets(vole)
        allPass()
        expect { vole.controller shouldBe alice.id }
    }
}
