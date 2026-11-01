package arcanum.engine.cube

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Chunk 47 in play: creatures with triggered abilities: upkeep and end step, casting, attacking, sacrificing,
 * discarding. */
class Chunk47TriggeredCreaturesScenariosTest {
    private val plusOne = CounterKind.PowerToughness(1, 1)

    /** Says yes to a "you may pay" and pays with the suggested mana. */
    private fun Scenario.payYes() {
        assertTrue(decision<Decision.May>().cost != null)
        answer(Answer.YesNo(true))
        (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
    }

    /** Gloomblossom Carrier: flash, flying; at your upkeep you lose 1 life and make a 1/1 flying Faerie. */
    @Test
    fun gloomblossomCarrierUpkeep() = scenario {
        alice.battlefield("Gloomblossom Carrier")
        beginStep(TurnStep.Upkeep)
        resolve()
        expect {
            alice.life shouldBe 19
            alice.battlefield.size shouldBe 2
            card("Faerie Token") hasKeyword Keyword.Flying
            card("Faerie Token").power shouldBe 1
        }
    }

    // CR 702.8a: flash: cast on the opponent's turn
    @Test
    fun gloomblossomCarrierFlash() = scenario {
        turn(number = 2, active = bob, priority = alice)
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Gloomblossom Carrier")
        alice.casts("Gloomblossom Carrier")
        allPass()
        expect {
            alice.battlefield has "Gloomblossom Carrier"
            "Gloomblossom Carrier" hasKeyword Keyword.Flying
        }
    }

    /** Shadow Informant: at your upkeep, the top card goes to your hand and you lose life equal to its mana value. */
    @Test
    fun shadowInformant() = scenario {
        alice.battlefield("Shadow Informant")
        alice.library("Storm Spark", "Forest")
        beginStep(TurnStep.Upkeep)
        resolve()
        expect {
            alice.hand.names shouldBe listOf("Storm Spark")
            alice.life shouldBe 19
        }
    }

    /** Morvek: at your end step, with no decayed creature, a 2/2 Zombie with decayed. */
    // CR 603.4
    @Test
    fun morvekMakesADecayedZombie() = scenario {
        alice.battlefield("Morvek, Ghoulherd of Stensia")
        beginStep(TurnStep.End)
        resolve()
        expect {
            card("Zombie Token").power shouldBe 2
            card("Zombie Token") hasKeyword Keyword.Decayed
        }
        // The next end step: a decayed Zombie is there, so no trigger
        beginStep(TurnStep.End, active = alice)
        expect { stack.isEmpty() }
    }

    /** Excavation Tinker: casting an artifact spell, you may pay {2} for a Construct that grows with your artifacts. */
    @Test
    fun excavationTinker() = scenario {
        alice.battlefield("Excavation Tinker", "Mountain", "Mountain", "Mountain")
        alice.hand("Sun Band")
        alice.casts("Sun Band")
        allPass()
        payYes()
        expect {
            (0..2).all { card("Mountain", it).tapped } shouldBe true
            card("Construct Token").power shouldBe 1
        }
        allPass()
        expect { card("Construct Token").power shouldBe 2 }
    }

    /** Etherglass Watcher: an opponent's first noncreature spell each turn draws you a card unless they pay {X}. */
    @Test
    fun etherglassWatcherDrawsUnlessPaid() = scenario {
        alice.battlefield("Etherglass Watcher")
        alice.library("Island")
        bob.battlefield("Mountain", "Island")
        bob.hand("Storm Spark", "Forebear's Insight")
        alice.passes()
        bob.casts("Storm Spark", target = "alice")
        allPass()
        bob.answers(Answer.YesNo(false))
        expect { alice.hand.names shouldBe listOf("Island") }
        allPass()
        alice.passes()
        // The second noncreature spell this turn: no trigger
        bob.casts("Forebear's Insight", target = "bob")
        expect { stack.names shouldBe listOf("Forebear's Insight") }
    }

    @Test
    fun etherglassWatcherPaid() = scenario {
        alice.battlefield("Etherglass Watcher")
        alice.library("Island")
        bob.battlefield("Mountain", "Mountain")
        bob.hand("Storm Spark")
        alice.passes()
        bob.casts("Storm Spark", target = "alice", paying = listOf("Mountain"))
        allPass()
        payYes()
        expect {
            alice.hand.isEmpty()
            card("Mountain", 1).tapped shouldBe true
        }
    }

    /** Invoice Shredder: a player's second spell each turn makes it connive. */
    // CR 701.50a
    @Test
    fun invoiceShredderConnives() = scenario {
        alice.battlefield("Invoice Shredder", "Mountain", "Mountain")
        alice.hand("Storm Spark", "Flash Surge", "Forest")
        alice.library("Cinder Bolt")
        alice.casts("Storm Spark", target = "bob")
        expect { stack.size shouldBe 1 }
        alice.casts("Flash Surge", target = "bob", kicked = 0)
        expect { stack.size shouldBe 3 }
        allPass()
        // Draws Cinder Bolt, then discards it: a nonland card. The draw is part of the same step as the discard, so
        // the decision names the drawn card by its id in the hand (D29)
        val discard = decision<Decision.ChooseObjects>()
        alice.answers(Answer.Objects(discard.options.filter { it !in state.player(alice.id).hand }))
        expect {
            alice.graveyard has "Cinder Bolt"
            alice.hand.names shouldBe listOf("Forest")
            "Invoice Shredder".counters(plusOne) shouldBe 1
        }
    }

    /** Grubb: casting an instant or sorcery, flip a coin: win, copy it; lose, it returns to its owner's hand. */
    // CR 705, 707.10
    @Test
    fun grubbWinsTheFlip() = scenario {
        alice.battlefield("Grubb, the Fingerless", "Mountain")
        alice.hand("Storm Spark")
        random(Answer.CoinFlip(true))
        alice.casts("Storm Spark", target = "bob")
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(false))
        expect { stack.names shouldBe listOf("Storm Spark", "copy of Storm Spark") }
        allPass()
        allPass()
        expect { bob.life shouldBe 14 }
    }

    @Test
    fun grubbLosesTheFlip() = scenario {
        alice.battlefield("Grubb, the Fingerless", "Mountain")
        alice.hand("Storm Spark")
        random(Answer.CoinFlip(false))
        alice.casts("Storm Spark", target = "bob")
        allPass()
        expect {
            alice.hand has "Storm Spark"
            stack.isEmpty()
            bob.life shouldBe 20
        }
    }

    /** Atoq: attacking, you may discard a card to put a +1/+1 counter and trample on an attacker; discarding exiles your
     * top card, which you may play until your next end step. */
    // CR 603.12
    @Test
    fun atoqStewardOfTheDawn() = scenario {
        alice.battlefield("Atoq, Steward of the Dawn", "Elvish Seer")
        alice.hand("Storm Spark")
        alice.library("Forest", "Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Elvish Seer")
        expect { stack.names shouldBe listOf("ability of Atoq, Steward of the Dawn") }
        allPass()
        assertTrue(decision<Decision.May>().cost != null)
        // Storm Spark, the only card in hand, is discarded
        alice.answers(Answer.YesNo(true))
        expect { alice.graveyard has "Storm Spark" }
        // Two triggers wait: the reflexive one (its only legal target, the attacking Elvish Seer, chosen at once) and
        // the discard one
        alice.answers(Answer.Order(listOf(0, 1)))
        expect { stack.size shouldBe 2 }
        allPass()
        allPass()
        expect {
            "Elvish Seer".counters(plusOne) shouldBe 1
            "Elvish Seer" hasKeyword Keyword.Trample
            alice.exile has "Forest"
        }
        // Played later this turn, in the second main phase
        advanceTo(TurnStep.PostcombatMain)
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
    }

    /** Havoc Imp: whenever a player sacrifices a permanent, 1 damage to any target. */
    @Test
    fun havocImp() = scenario {
        alice.battlefield("Havoc Imp", "Corpse Gnawer", "Elvish Seer")
        alice.activates("Corpse Gnawer", choosing = listOf("Elvish Seer"))
        alice.answers(Answer.Targets(listOf(target("bob"))))
        allPass()
        expect { bob.life shouldBe 19 }
    }

    /** Quarantine Automaton: discarding a card, you may exile it from your graveyard and play it this turn. */
    @Test
    fun quarantineAutomaton() = scenario {
        alice.battlefield("Quarantine Automaton", "Rancid Imp")
        alice.hand("Forest")
        alice.activates("Rancid Imp", choosing = listOf("Forest"))
        expect { alice.graveyard has "Forest" }
        allPass()
        alice.answers(Answer.YesNo(true))
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        expect { alice.exile has "Forest" }
        allPass()
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
    }

    /** Rampaging Reefshark: discarding one or more cards, that many +1/+1 counters; cycling {2}. */
    @Test
    fun rampagingReefsharkGrows() = scenario {
        alice.battlefield("Rampaging Reefshark", "Island", "Island", "Island")
        alice.hand("Hasty Rummage", "Forest", "Swamp")
        alice.library("Plains", "Mountain")
        alice.casts("Hasty Rummage")
        allPass()
        alice.chooses("Forest", "Swamp")
        while (pending is Decision.ChooseObjects || pending is Decision.Targets) {
            if (pending is Decision.Targets) alice.answers(Answer.Targets(emptyList())) else alice.answers(Answer.Objects(emptyList()))
        }
        allPass()
        expect { "Rampaging Reefshark".counters(plusOne) shouldBe 2 }
    }

    // CR 702.29a
    @Test
    fun rampagingReefsharkCycles() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Rampaging Reefshark")
        alice.library("Island")
        alice.activates("Rampaging Reefshark")
        expect { alice.graveyard has "Rampaging Reefshark" }
        allPass()
        expect { alice.hand.names shouldBe listOf("Island") }
    }

    // Only an opponent's spell triggers it
    @Test
    fun etherglassWatcherIgnoresYourSpells() = scenario {
        alice.battlefield("Etherglass Watcher", "Mountain")
        alice.hand("Storm Spark")
        alice.casts("Storm Spark", target = "bob")
        expect { stack.names shouldBe listOf("Storm Spark") }
    }
}
