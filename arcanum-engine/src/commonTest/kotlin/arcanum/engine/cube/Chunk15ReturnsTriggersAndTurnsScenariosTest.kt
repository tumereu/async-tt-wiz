package arcanum.engine.cube

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.TurnStep
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals

/** Chunk 15 of the cube in play: returns from the graveyard, turn-long triggers, and turns. */
class Chunk15ReturnsTriggersAndTurnsScenariosTest {
    /** Muster the Forebears with X=1: cheap creature cards return, then are exiled at the next upkeep; it exiles itself. */
    @Test
    fun musterTheForebearsReturnsUntilTheNextUpkeep() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand("Muster the Forebears")
        alice.graveyard("Elvish Seer", "Shapeless Exile", "Elvish Hierophant")

        alice.casts("Muster the Forebears", x = 1)
        allPass()
        expect {
            alice.battlefield has "Elvish Seer"
            alice.battlefield has "Shapeless Exile"
            alice.graveyard.names shouldBe listOf("Elvish Hierophant")
            alice.exile.names shouldBe listOf("Muster the Forebears")
        }

        // CR 603.7: the delayed trigger at alice's next upkeep
        beginStep(TurnStep.Upkeep, active = alice)
        resolveStack()
        expect {
            alice.exile has "Elvish Seer"
            alice.exile has "Shapeless Exile"
            alice.battlefield lacks "Elvish Seer"
        }
    }

    /** Hauled Back: up to two permanent cards that went to the graveyard from the battlefield this turn return tapped. */
    @Test
    fun hauledBackReturnsWhatDiedThisTurn() = scenario {
        alice.battlefield("Plains", "Plains", "Mountain", "Elvish Seer")
        alice.hand("Storm Spark", "Hauled Back")
        alice.graveyard("Tinkered Mite")

        alice.casts("Storm Spark", target = "Elvish Seer")
        allPass()
        alice.casts("Hauled Back")
        // CR 115.1: only the card that was put there from the battlefield this turn can be targeted
        assertEquals(listOf(target("Elvish Seer")), decision<Decision.Targets>().options)
        answer(Answer.Targets(listOf(target("Elvish Seer"))))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()

        expect {
            alice.battlefield has "Elvish Seer"
            "Elvish Seer".tapped shouldBe true
            alice.graveyard.names shouldBe listOf("Tinkered Mite", "Storm Spark", "Hauled Back")
        }
    }

    /** Devotion's Reward: every permanent card put into your graveyard from the battlefield this turn returns. */
    @Test
    fun devotionsRewardReturnsEverythingThatDied() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Lotus Frond", "Elvish Seer")
        alice.hand("Devotion's Reward")
        alice.graveyard("Tinkered Mite")
        bob.battlefield("Thicketwood Elves")
        bob.hand("Storm Spark", "Old Enmity")
        bob.battlefield("Mountain", "Mountain", "Mountain")

        alice.passes()
        bob.casts("Storm Spark", target = "Elvish Seer")
        allPass()
        alice.passes()
        bob.casts("Old Enmity", target = "Lotus Frond", paying = listOf("Mountain#1", "Mountain#2"))
        allPass()
        alice.casts("Devotion's Reward")
        allPass()

        expect {
            alice.battlefield has "Elvish Seer"
            alice.battlefield has "Lotus Frond"
            alice.graveyard.names shouldBe listOf("Tinkered Mite", "Devotion's Reward")
        }
    }

    /** Second Dawning: each player gets back what went to their graveyard from the battlefield this turn. */
    @Test
    fun secondDawningReturnsForEachPlayer() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Mountain", "Elvish Seer")
        alice.hand("Storm Spark", "Second Dawning")
        bob.battlefield("Thicketwood Elves")
        bob.graveyard("Lotus Frond")

        alice.casts("Storm Spark", target = "Thicketwood Elves")
        allPass()
        alice.casts("Second Dawning", paying = listOf("Plains", "Plains#1", "Plains#2"))
        allPass()

        expect {
            bob.battlefield has "Thicketwood Elves"
            bob.graveyard.names shouldBe listOf("Lotus Frond")
            alice.battlefield has "Elvish Seer"
        }
    }

    /** Rite of Concord: this turn, each creature or enchantment entering under your control draws a card. */
    @Test
    fun riteOfConcordDrawsForEachEntering() = scenario {
        alice.battlefield("Forest", "Plains", "Forest")
        alice.hand("Rite of Concord", "Elvish Seer")
        alice.library("Island", "Swamp")

        alice.casts("Rite of Concord")
        allPass()
        alice.casts("Elvish Seer")
        allPass()
        expect { stack.names shouldBe listOf("ability of Rite of Concord") }
        allPass()

        expect {
            alice.battlefield has "Elvish Seer"
            alice.hand.names shouldBe listOf("Island")
        }
    }

    /** Rite of Concord's flashback, then an opponent's creature doesn't count. */
    @Test
    fun riteOfConcordFlashback() = scenario {
        // CR 702.34a
        alice.battlefield("Forest", "Forest", "Plains", "Plains", "Forest")
        alice.graveyard("Rite of Concord")
        alice.hand("Elvish Seer")
        alice.library("Island")

        alice.casts("Rite of Concord", paying = listOf("Forest", "Forest#1", "Plains", "Plains#1"))
        allPass()
        alice.casts("Elvish Seer")
        repeat(2) { allPass() }

        expect {
            alice.exile has "Rite of Concord"
            alice.hand.names shouldBe listOf("Island")
        }
    }

    /** Swelling Tide: this turn, each Island tapped for mana adds an extra {U}. */
    @Test
    fun swellingTideDoublesIslands() = scenario {
        // CR 605.1b: a triggered mana ability resolves at once
        alice.battlefield("Island", "Island")
        alice.hand("Swelling Tide")

        alice.casts("Swelling Tide", paying = listOf("Island#1"))
        allPass()
        alice.activates("Island", ability = 0)
        expect {
            stack.isEmpty()
        }
        assertEquals(listOf(Color.Blue, Color.Blue), alice.pool.map { it.color })
    }

    /** Chastening Flame: 2 damage; when an opponent gains life, pay {R} to return it from the graveyard to hand. */
    @Test
    fun chasteningFlameReturnsWhenAnOpponentGainsLife() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand("Chastening Flame")
        bob.battlefield("Plains", "Elvish Seer")
        bob.hand("Plowshare Decree")

        alice.casts("Chastening Flame", target = "bob", paying = listOf("Mountain", "Mountain#1"))
        allPass()
        expect { bob.life shouldBe 18 }

        alice.passes()
        bob.casts("Plowshare Decree", target = "Elvish Seer")
        allPass()
        // Bob gained 1 life: the card in alice's graveyard triggers
        expect { stack.names shouldBe listOf("ability of Chastening Flame") }
        allPass()
        answer(Answer.YesNo(true))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))

        expect {
            bob.life shouldBe 19
            alice.hand has "Chastening Flame"
            card("Mountain", 2).tapped shouldBe true
        }
    }

    /** Gathered Fellowship: two cheap creature cards from the top six onto the battlefield; the rest to the bottom. */
    @Test
    fun gatheredFellowshipPutsTwoCreatures() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand("Gathered Fellowship")
        alice.library("Elvish Seer", "Elvish Hierophant", "Deadly Plotting", "Island", "Thicketwood Elves", "Shapeless Exile", "Swamp")

        alice.casts("Gathered Fellowship")
        allPass()
        val choice = decision<Decision.ChooseObjects>()
        assertEquals(setOf("Elvish Seer", "Elvish Hierophant", "Thicketwood Elves", "Shapeless Exile"),
            choice.options.map { id -> state.objOrThrow(id).definition!!.faces[0].name }.toSet())
        alice.chooses("Elvish Hierophant", "Shapeless Exile")
        // The rest go to the bottom in the order alice chooses (CR 401.4)
        alice.chooses("Island", "Deadly Plotting", "Thicketwood Elves", "Elvish Seer")

        expect {
            alice.battlefield has "Elvish Hierophant"
            alice.battlefield has "Shapeless Exile"
            alice.library.names shouldBe listOf("Swamp", "Island", "Deadly Plotting", "Thicketwood Elves", "Elvish Seer")
        }
    }

    /** Fleeting Whim: one of the top four to hand, the rest to the bottom. */
    @Test
    fun fleetingWhimPicksOne() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand("Fleeting Whim")
        alice.library("Forest", "Storm Spark", "Plains", "Swamp", "Mountain")

        alice.casts("Fleeting Whim")
        allPass()
        alice.chooses("Storm Spark")
        alice.chooses("Swamp", "Forest", "Plains")

        expect {
            alice.hand.names shouldBe listOf("Storm Spark")
            alice.library.names shouldBe listOf("Mountain", "Swamp", "Forest", "Plains")
        }
    }

    /** Time Stride: alice takes another turn after this one. */
    @Test
    fun timeStrideTakesAnExtraTurn() = scenario {
        // CR 500.7
        alice.battlefield("Island", "Island")
        alice.hand("Time Stride")
        alice.library("Forest", "Island")
        bob.library("Swamp")

        alice.casts("Time Stride")
        allPass()
        advanceTo(TurnStep.Upkeep, turn = 2)

        assertEquals(alice.id, state.turn.active)
    }

    /** Tidecall Reversal: choose creature and tap all of bob's untapped creatures. */
    @Test
    fun tidecallReversalTapsAType() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand("Tidecall Reversal")
        bob.battlefield("Elvish Seer", "Thicketwood Elves", "Forest")

        alice.casts("Tidecall Reversal", target = "bob")
        allPass()
        answer(Answer.CardTypeChoice(CardType.Creature))
        answer(Answer.Option("tap"))

        expect {
            "Elvish Seer".tapped shouldBe true
            "Thicketwood Elves".tapped shouldBe true
            "Forest".tapped shouldBe false
        }
    }

    /** Tidecall Reversal: choose land and untap all of your own tapped lands. */
    @Test
    fun tidecallReversalUntapsAType() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Plains")
        alice.battlefield("Lotus Frond", tapped = true)
        alice.hand("Tidecall Reversal")

        alice.casts("Tidecall Reversal", target = "alice", paying = listOf("Island", "Island#1", "Island#2", "Island#3"))
        allPass()
        answer(Answer.CardTypeChoice(CardType.Land))
        answer(Answer.Option("untap"))

        expect {
            (0..3).none { card("Island", it).tapped } shouldBe true
            "Lotus Frond".tapped shouldBe true
        }
    }
}
