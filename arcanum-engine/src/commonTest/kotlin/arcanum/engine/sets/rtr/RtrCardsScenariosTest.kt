package arcanum.engine.sets.rtr

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Return to Ravnica's more involved cards in play, with the invented cards of the parser's chunks. */
class RtrCardsScenariosTest {
    private val comb = Cards.invent("Comb the Districts", "{4}{U}", "Enchantment", """
        When this enchantment enters, exile the top five cards of your library.
        Whenever you play a card with the same name as one of the exiled cards, you may put one of those cards with that name into its owner's hand. Then if there are no cards exiled with this enchantment, sacrifice it. If you do, take an extra turn after this one.
    """)
    private val demon = Cards.invent("Defiling Demon", "{2}{B}{B}", "Creature — Demon", """
        Flying
        At the beginning of each combat, any opponent may sacrifice a creature of their choice. If a player does, tap this creature and put a +1/+1 counter on it.
    """, "6/6")
    private val prescience = Cards.invent("Mindforge Prescience", "{5}{U}{R}", "Instant",
        "Search your library for an instant card with mana value 3, reveal it, and put it into your hand. Then repeat this process for instant cards with mana values 2 and 1. Then shuffle.")
    private val hypothesis = Cards.invent("Grand Hypothesis", "{X}{U}{R}", "Sorcery",
        "Exile the top X cards of your library. You may cast instant and sorcery spells with mana value X or less from among them without paying their mana costs. Then put all cards exiled this way that weren't cast into your graveyard.")
    private val strife = Cards.invent("Faction Strife", "{5}{R}", "Enchantment",
        "At the beginning of your upkeep, target opponent reveals the top three cards of their library, may put a creature card from among them onto the battlefield, then puts the rest into their graveyard. You do the same with the top three cards of your library. If two creatures are put onto the battlefield this way, those creatures fight each other.")
    private val wurm = Cards.invent("Worldroot Wurm", "{8}{G}{G}{G}", "Creature — Wurm", """
        Trample
        When this creature dies, create three 5/5 green Wurm creature tokens with trample.
        When Worldroot Wurm is put into a graveyard from anywhere, shuffle it into its owner's library.
    """, "15/15")
    private val horror = Cards.invent("Sporeswell Horror", "{2}{B}{G}", "Creature — Fungus",
        "If one or more +1/+1 counters would be put on a creature you control, twice that many +1/+1 counters are put on it instead.", "4/4")
    private val spike = Cards.invent("Pinning Spike", "{1}", "Artifact", """
        As this artifact enters, choose a card name.
        Activated abilities of sources with the chosen name can't be activated unless they're mana abilities.
    """)
    private val runestone = Cards.invent("Senate Runestone", "{3}", "Artifact", """
        {T}: Add {W} or {U}.
        {W}{U}: This artifact becomes a 2/2 white and blue Bird artifact creature with flying until end of turn.
    """)
    private val levy = Cards.invent("Spirit Levy", "{1}{W}", "Enchantment — Aura", """
        Enchant nonland permanent
        At the beginning of the upkeep of enchanted permanent's controller, that player sacrifices it unless they pay {X}, where X is its mana value.
    """)
    private val filibusterers = Cards.invent("Senate Filibusterers", "{3}{W/U}{W/U}", "Creature — Human Advisor", """
        At the beginning of your upkeep, put a filibuster counter on this creature. Then if this creature has five or more filibuster counters on it, you win the game.
        Whenever a source deals damage to you, remove a filibuster counter from this creature.
    """, "3/5")
    private val tamsin = Cards.invent("Tamsin, Weaver of Thought", "{2}{U}{U}", "Legendary Planeswalker — Jace", """
        [+1]: Until your next turn, whenever a creature an opponent controls attacks, it gets -1/-0 until end of turn.
        [−2]: Reveal the top three cards of your library. An opponent separates those cards into two piles. Put one pile into your hand and the other on the bottom of your library in any order.
        [−8]: For each player, search that player's library for a nonland card and exile it, then that player shuffles. You may cast those cards without paying their mana costs.
    """, loyalty = "4")
    private val seraph = Cards.invent("Seraph of Stillness", "{4}{W}{W}{W}", "Creature — Angel", """
        Flying
        When this creature enters, you may exile up to three other target creatures from the battlefield and/or creature cards from graveyards.
        When this creature leaves the battlefield, return the exiled cards to their owners' hands.
    """, "5/6")

    private val bear = Cards.invent("Market Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val wolf = Cards.invent("Alley Wolf", "{1}{G}", "Creature — Wolf", "", "3/3")
    private val boar = Cards.invent("Rubble Boar", "{2}{G}", "Creature — Boar", "", "4/4")
    private val insight = Cards.invent("Quick Insight", "{1}{U}", "Instant", "Draw a card.")
    private val zap = Cards.invent("Tiny Zap", "{U}", "Instant", "Tiny Zap deals 1 damage to any target.")
    private val ponder = Cards.invent("Ponder the Odds", "{1}{U}{U}", "Instant", "Draw two cards.")
    private val musing = Cards.invent("Long Musing", "{2}{U}{U}", "Instant", "Draw three cards.")
    private val growth = Cards.invent("Sudden Growth", "{G}", "Instant", "Put a +1/+1 counter on target creature.")
    private val tinker = Cards.invent("Spark Tinker", "{1}{R}", "Creature — Human", "{T}: Spark Tinker deals 1 damage to any target.", "1/1")
    private val rot = Cards.invent("Thought Rot", "{2}{B}", "Sorcery", "Target player discards two cards.")
    private val murder = Cards.invent("Quick Execution", "{1}{B}{B}", "Instant", "Destroy target creature.")

    private fun Scenario.yes() = answer(Answer.YesNo(true))

    // ---- Linked exile and playing a card with the same name (CR 607.2a)

    /** Playing a land with an exiled card's name lets you put that card into your hand; once none is left, the enchantment is
     * sacrificed and an extra turn follows. */
    @Test
    fun combTheDistrictsLastCardGivesAnExtraTurn() = scenario {
        alice.hand(comb, "Island")
        alice.library("Island")
        alice.castsWithoutCosts(comb)
        resolve()
        resolve()
        expect { alice.exile.names shouldBe listOf("Island") }
        alice.plays("Island")
        settle()
        resolve()
        alice.chooses("Island")
        expect {
            alice.hand.names shouldBe listOf("Island")
            alice.graveyard has comb
        }
        assertEquals(listOf(alice.id), state.turn.extraTurns)
    }

    // ---- Any opponent may sacrifice (CR 118.12)

    @Test
    fun opponentSacrificesToTapTheDemon() = scenario {
        alice.battlefield(demon)
        bob.battlefield(bear)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveStack()
        if (pending is Decision.May) yes()
        expect {
            bob.graveyard has bear
            demon.tapped shouldBe true
            demon.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    // ---- Searches repeated per mana value

    @Test
    fun threeSearchesThenOneShuffle() = scenario {
        alice.hand(prescience)
        alice.library(ponder, musing, insight, zap, "Island")
        alice.castsWithoutCosts(prescience)
        resolve()
        alice.chooses(ponder)
        alice.chooses(insight)
        alice.chooses(zap)
        expect {
            alice.hand.names shouldBe listOf(ponder, insight, zap)
            alice.library.size shouldBe 2
        }
    }

    // ---- Casting exiled cards without paying (CR 608.2g)

    @Test
    fun castFromAmongTheExiledCards() = scenario {
        alice.hand(hypothesis)
        alice.library(zap, "Island", musing)
        bob.battlefield(tinker)
        alice.castsWithoutCosts(hypothesis, x = 3)
        resolve()
        // Tiny Zap (mana value 1) may be cast; Long Musing (mana value 4) can't
        yes()
        answer(Answer.Targets(listOf(target(tinker))))
        expect {
            stack.names shouldBe listOf(zap)
            alice.graveyard has "Island"
            alice.graveyard has musing
        }
        resolve()
        expect { bob.graveyard has tinker }
    }

    // ---- Revealing, putting creatures and fighting (CR 701.14)

    @Test
    fun factionStrifeFight() = scenario {
        alice.battlefield(strife)
        alice.library(bear, "Island", "Island")
        bob.library(boar, "Swamp", "Swamp")
        bob.battlefield("Swamp")
        beginStep(TurnStep.Upkeep)
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(TargetRef.Player(bob.id))))
        resolve()
        bob.chooses(boar)
        alice.chooses(bear)
        expect {
            bob.battlefield has boar
            alice.graveyard has bear
            bob.graveyard.size shouldBe 2
            alice.graveyard.size shouldBe 3
        }
    }

    // ---- "Put into a graveyard from anywhere" (CR 603.6c)

    /** Milled or discarded, it is shuffled back; dying, the tokens are made too. */
    @Test
    fun worldrootWurmFromHand() = scenario {
        alice.hand(wurm)
        alice.library("Forest")
        bob.hand(rot)
        bob.castsWithoutCosts(rot, target = "alice")
        resolve()
        resolveStack()
        expect { alice.library has wurm }
    }

    @Test
    fun worldrootWurmDies() = scenario {
        alice.battlefield(wurm)
        bob.hand(murder)
        bob.castsWithoutCosts(murder, target = wurm)
        resolve()
        if (pending is Decision.OrderTriggers) answer(Answer.Order(decision<Decision.OrderTriggers>().triggers.map { it.index }))
        resolveStack()
        expect {
            alice.battlefield.names shouldBe listOf("Wurm Token", "Wurm Token", "Wurm Token")
            alice.library has wurm
        }
    }

    // ---- Doubled counters (CR 614.1a)

    @Test
    fun countersDoubled() = scenario {
        alice.battlefield(horror, bear)
        alice.hand(growth)
        alice.castsWithoutCosts(growth, target = bear)
        resolve()
        expect { bear.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2 }
    }

    // ---- A named card's activated abilities stopped (CR 201.3)

    @Test
    fun pinningSpikeStopsTheNamedCard() = scenario {
        alice.hand(spike)
        bob.battlefield(tinker)
        alice.castsWithoutCosts(spike)
        resolve()
        answer(Answer.CardName(tinker))
        assertTrue(Engine.options(state, bob.id).none { it is Input.Activate && it.source == card(tinker) })
    }

    // ---- An artifact that becomes a creature (CR 205.1b)

    @Test
    fun runestoneBecomesABird() = scenario {
        alice.battlefield(runestone, "Plains", "Island")
        alice.activates(runestone, ability = 1)
        allPass()
        expect {
            runestone.power shouldBe 2
            runestone.types shouldBe listOf(CardType.Artifact, CardType.Creature)
            runestone.subtypes shouldBe listOf(Subtype("Bird"))
            runestone.colors.toSet() shouldBe setOf(Color.White, Color.Blue)
            runestone hasKeyword Keyword.Flying
        }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { runestone.power shouldBe null }
    }

    // ---- An upkeep tax on the enchanted permanent (CR 118.12a)

    @Test
    fun spiritLevyWithoutPaying() = scenario {
        alice.hand(levy)
        bob.battlefield(wolf)
        alice.castsWithoutCosts(levy, target = wolf)
        resolve()
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveStack()
        expect { bob.graveyard has wolf }
    }

    // ---- Filibuster counters (CR 104.2b: an effect wins the game)

    @Test
    fun fiveFilibusterCountersWin() = scenario {
        alice.battlefield(filibusterers)
        change(arcanum.engine.change.Changes.counters(state, TargetRef.Object(card(filibusterers)), CounterKind.Named("filibuster"), 4))
        beginStep(TurnStep.Upkeep)
        resolve()
        assertEquals(arcanum.engine.setup.GameResult.Won(alice.id), Engine.result(state))
    }

    // ---- A repeating delayed trigger until your next turn (CR 603.7b)

    @Test
    fun tamsinShrinksAttackers() = scenario {
        alice.battlefield(tamsin)
        bob.battlefield(wolf, boar)
        alice.library("Island", "Island")
        bob.library("Swamp", "Swamp")
        change(arcanum.engine.change.Changes.counters(state, TargetRef.Object(card(tamsin)), CounterKind.Loyalty, 4))
        alice.activates(tamsin, ability = 0)
        allPass()
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(wolf, boar)
        resolveStack()
        expect {
            wolf.power shouldBe 2
            boar.power shouldBe 3
        }
    }

    // ---- Exiling from the battlefield and graveyards, returned to hands when it leaves (CR 607.2a)

    @Test
    fun seraphExilesAndReturns() = scenario {
        alice.hand(seraph)
        bob.hand(murder)
        bob.battlefield(wolf)
        bob.graveyard(boar)
        alice.castsWithoutCosts(seraph)
        resolve()
        answer(Answer.Targets(listOf(target(wolf), TargetRef.Object(card(boar)))))
        resolve()
        yes()
        expect { bob.exile.names shouldBe listOf(wolf, boar) }
        bob.castsWithoutCosts(murder, target = seraph)
        resolve()
        resolveStack()
        expect { bob.hand.names.containsAll(listOf(wolf, boar)) shouldBe true }
    }
}
