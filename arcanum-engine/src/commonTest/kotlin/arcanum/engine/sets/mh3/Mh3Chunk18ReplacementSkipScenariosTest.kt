package arcanum.engine.sets.mh3

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test

/**
 * The MH3 cards of chunk 18, played: the invented names carry the oracle text of Branching Evolution, Faithful Watchdog,
 * Worn Powerstone and Necrodominance (costs cut where they don't change play).
 */
class Mh3Chunk18ReplacementSkipScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val doubler = Cards.invent("Doubling Bloom", "{2}{G}", "Enchantment",
        "If one or more +1/+1 counters would be put on a creature you control, twice that many +1/+1 counters are put on that creature instead.")
    private val boost = Cards.invent("Spore Charm", "{G}", "Instant", "Put a +1/+1 counter on target creature.")
    private val bear = Cards.invent("Reaping Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val hound = Cards.invent("Loyal Hound", "{G}{W}", "Creature — Dog", """
        Vigilance
        This creature enters with three +1/+1 counters on it.
    """, "0/0")
    private val stone = Cards.invent("Humble Powerstone", "{3}", "Artifact", """
        This artifact enters tapped.
        {T}: Add {C}{C}.
    """)
    private val dominion = Cards.invent("Grim Dominion", "{B}{B}{B}", "Legendary Enchantment", """
        Skip your draw step.
        At the beginning of your end step, you may pay any amount of life. If you do, draw that many cards.
        Your maximum hand size is five.
        If a card or token would be put into your graveyard from anywhere, exile it instead.
    """)

    // CR 614.1a, 122.6: one counter becomes two, on a creature you control only
    @Test
    fun doublerDoublesCountersOnYourCreatures() = scenario {
        alice.battlefield(doubler, bear, "Forest")
        alice.hand(boost)
        alice.casts(boost, target = bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 2 }
    }

    // CR 614.1a: the opponent's creature isn't covered
    @Test
    fun doublerLeavesAnOpponentsCreatureAlone() = scenario {
        val foe = Cards.invent("Pale Cub", "{1}{G}", "Creature — Bear", "", "2/2")
        alice.battlefield(doubler, "Forest")
        bob.battlefield(foe)
        alice.hand(boost)
        alice.casts(boost, target = foe)
        resolveAll()
        expect { foe.counters(p1p1) shouldBe 1 }
    }

    // CR 122.6, 614.1c: "enters with three counters" is putting counters on it, so the doubler applies
    @Test
    fun houndEntersWithThreeCounters() = scenario {
        alice.hand(hound)
        alice.battlefield("Forest", "Plains")
        alice.casts(hound)
        resolveAll()
        expect {
            hound.counters(p1p1) shouldBe 3
            hound.power shouldBe 3
        }
    }

    // CR 122.6a, 614.1a: with the doubler out, the Hound enters with twice that many
    @Test
    fun houndEntersWithSixCountersUnderTheDoubler() = scenario {
        alice.hand(hound)
        alice.battlefield(doubler, "Forest", "Plains")
        alice.casts(hound)
        resolveAll()
        expect { hound.counters(p1p1) shouldBe 6 }
    }

    // CR 614.1c: an artifact that enters tapped
    @Test
    fun powerstoneEntersTapped() = scenario {
        alice.hand(stone)
        alice.battlefield("Forest", "Forest", "Forest")
        alice.casts(stone)
        allPass()
        expect { stone.tapped shouldBe true }
    }

    // CR 614.10: the draw step is skipped; CR 603.2: the end step trigger lets you pay life for cards
    @Test
    fun dominionSkipsTheDrawStep() = scenario {
        alice.battlefield(dominion)
        alice.library("Plains", "Island", "Swamp", "Forest")
        turn(number = 3, step = TurnStep.Upkeep)
        advanceTo(TurnStep.PrecombatMain)
        expect {
            alice.hand.names.size shouldBe 0
            alice.library.names.size shouldBe 4
        }
    }

    @Test
    fun dominionEndStepPaysLifeForCards() = scenario {
        alice.battlefield(dominion)
        alice.library("Plains", "Island", "Swamp", "Forest")
        advanceTo(TurnStep.End)
        resolveAll()
        answer(Answer.YesNo(true))
        answer(Answer.Number(2))
        resolveAll()
        expect {
            alice.life shouldBe 18
            alice.hand.names.size shouldBe 2
        }
    }
}
