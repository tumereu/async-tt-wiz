package arcanum.engine.review

import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.api.Players
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Review 1: triggered abilities (CR 603). */
class Review1TriggersTest {
    /** Resolves everything: "may" yes, targets by [names] or the first option, objects the first ones. */
    private fun Scenario.settleStack(vararg names: String) {
        var guard = 30
        while ((state.stack.isNotEmpty() || pending != null) && guard-- > 0) {
            when (val d = pending) {
                is Decision.Targets -> answer(Answer.Targets(names.map { target(it) }.filter { it in d.options }.take(d.max ?: 1)
                    .ifEmpty { d.options.take(d.min.coerceAtLeast(1)) }))
                is Decision.May -> answer(Answer.YesNo(true))
                is Decision.ChooseObjects -> answer(Answer.Objects(d.options.take(d.min)))
                is Decision.OrderTriggers -> answer(Answer.Order(d.triggers.indices.toList()))
                null -> allPass()
                else -> return
            }
        }
    }

    /** "This ability triggers only once each turn": two leave events before anyone gets priority trigger it once. */
    // CR 603.2, 603.3
    @Test
    fun onceEachTurnWithTwoEventsBeforePriority() = scenario {
        alice.battlefield("Demon-Cult Forge", "Sun Band", "Tinkered Mite", "Swamp", "Mountain", "Mountain")
        alice.hand("Warlord's Decree")
        alice.casts("Warlord's Decree", modes = listOf(2, 3), targets = listOf("Sun Band", "Tinkered Mite"))
        settleStack()
        expect {
            alice.graveyard has "Sun Band"
            alice.graveyard has "Tinkered Mite"
            alice.battlefield.names.count { it == "Construct Token" } shouldBe 1
        }
    }

    /** An intervening "if" is checked again on resolution: the card that left the graveyard makes no token. */
    // CR 603.4
    @Test
    fun interveningIfCheckedAgainOnResolution() = scenario {
        alice.graveyard("Stairway from the Depths")
        alice.battlefield("Thicketwood Elves")
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.passes()
        bob.casts("Storm Spark", target = "Thicketwood Elves")
        allPass()
        expect { stack.size shouldBe 1 }
        move("Stairway from the Depths", Zone.Exile)
        settleStack()
        expect { alice.battlefield.names.count { it == "Zombie Token" } shouldBe 0 }
    }

    /** The same, with the condition still true: a Zombie. */
    // CR 603.4
    @Test
    fun interveningIfTrueMakesAToken() = scenario {
        alice.graveyard("Stairway from the Depths")
        alice.battlefield("Thicketwood Elves")
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.passes()
        bob.casts("Storm Spark", target = "Thicketwood Elves")
        settleStack()
        expect { alice.battlefield.names.count { it == "Zombie Token" } shouldBe 1 }
    }

    /** A trigger controlled by one player whose effect names another: Origin Vault gives the token to the entering
     * creature's controller. */
    // CR 603.3a, 109.5
    @Test
    fun triggerGivesTheOtherPlayerAToken() = scenario {
        alice.battlefield("Origin Vault")
        bob.battlefield("Forest")
        bob.hand("Elvish Seer")
        turn(number = 2, active = bob)
        bob.casts("Elvish Seer")
        settleStack()
        expect {
            bob.battlefield has "Myr Token"
            alice.battlefield lacks "Myr Token"
        }
    }

    /** "That creature's controller may draw a card": the choice is that player's, not the trigger's controller's. */
    // CR 603.3a, 608.2d
    @Test
    fun mayDrawIsAskedOfTheCreaturesController() = scenario {
        alice.battlefield("Fertile Passing", "Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Thicketwood Elves")
        bob.library("Island")
        alice.casts("Storm Spark", target = "Thicketwood Elves")
        allPass()
        allPass()
        assertEquals(bob.id, decision<Decision.May>().player)
        answer(Answer.YesNo(true))
        expect { bob.hand has "Island" }
    }

    /** A cast trigger resolves before the spell that caused it. */
    // CR 601.2i, 603.3, 405.2
    @Test
    fun castTriggerResolvesFirst() = scenario {
        alice.battlefield("Aldric, Fist of the Seraphs", "Forest")
        alice.hand("Elvish Seer")
        alice.casts("Elvish Seer")
        expect { stack.size shouldBe 2 }
        allPass()
        expect {
            alice.battlefield.names.count { it == "Human Soldier Token" } shouldBe 1
            alice.battlefield lacks "Elvish Seer"
        }
    }

    /** A dies trigger of a creature another player controlled is controlled by that player (last known information). */
    // CR 603.3a, 603.10a
    @Test
    fun diesTriggerIsControlledByWhoeverControlledIt() = scenario {
        alice.battlefield("Harborside Knifer", "Mountain")
        alice.hand("Storm Spark")
        effect(bob, Mechanic.ControlledBy(Players.You), on = listOf("Harborside Knifer"))
        alice.casts("Storm Spark", target = "Harborside Knifer")
        settleStack()
        expect {
            bob.life shouldBe 21
            alice.life shouldBe 19
        }
    }

    /** A delayed trigger: Seer's Trinket draws at the beginning of the next turn's upkeep. */
    // CR 603.7
    @Test
    fun delayedDrawAtTheNextUpkeep() = scenario {
        alice.battlefield("Seer's Trinket")
        alice.library("Island", "Island", "Island")
        bob.library("Mountain", "Mountain")
        alice.activates("Seer's Trinket", target = "bob")
        settleStack()
        expect { alice.hand.size shouldBe 0 }
        advanceTo(TurnStep.Upkeep, turn = 2)
        settleStack()
        expect { alice.hand.size shouldBe 1 }
    }

    /** A repeating delayed trigger made by a spell: Hint of the Wild draws for each creature spell this turn. */
    // CR 603.7c
    @Test
    fun hintOfTheWildDrawsForEachCreatureSpell() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand("Hint of the Wild", "Elvish Seer", "Thicketwood Elves")
        alice.library("Island", "Island", "Island")
        alice.casts("Hint of the Wild")
        settleStack()
        alice.casts("Elvish Seer")
        settleStack()
        alice.casts("Thicketwood Elves")
        settleStack()
        expect { alice.hand.size shouldBe 2 }
    }

    /** A triggered ability with no legal target is removed: "another target artifact card" can't be the dying
     * creature's own card, so nothing is returned. */
    // CR 603.3d
    @Test
    fun triggerWithoutTargetsIsRemoved() = scenario {
        alice.battlefield("Myr Gleaner")
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.passes()
        bob.casts("Storm Spark", target = "Myr Gleaner")
        allPass()
        expect { stack.isEmpty() }
        assertEquals(null, pending)
    }

    /** "Whenever you discard one or more cards for the first time each turn, draw that many": a discard of two draws
     * two. */
    // CR 603.2c
    @Test
    fun oneOrMoreDiscardsAreOneBatch() = scenario {
        alice.battlefield("Ysolde, the Unbound Scholar", "Mountain")
        alice.hand("Faithless Rummage", "Island", "Island", "Island")
        alice.library("Forest", "Forest", "Plains", "Plains")
        alice.casts("Faithless Rummage")
        allPass()
        while (pending is Decision.ChooseObjects) answer(Answer.Objects(listOf(card("Island"), card("Island", 1))))
        settleStack()
        expect {
            alice.hand.size shouldBe 5
            "Ysolde, the Unbound Scholar".power shouldBe 1
        }
    }

    /** The second spell a player casts in a turn triggers Invoice Shredder once, for any player. */
    // CR 603.2
    @Test
    fun secondSpellOfAnotherPlayer() = scenario {
        alice.battlefield("Invoice Shredder")
        alice.library("Island", "Island")
        bob.battlefield("Mountain", "Mountain")
        bob.hand("Storm Spark", "Magma Needle")
        alice.passes()
        bob.casts("Storm Spark", target = "alice", paying = listOf("Mountain"))
        expect { stack.size shouldBe 1 }
        bob.casts("Magma Needle", target = "alice", paying = listOf("Mountain#1"))
        expect { stack.size shouldBe 3 }
    }

    /** "Unless that player pays {X}": the opponent who cast the spell is asked, not the trigger's controller. */
    // CR 603.3a, 118.12
    @Test
    fun unlessPaysAsksTheOpponent() = scenario {
        alice.battlefield("Etherglass Watcher")
        alice.library("Island")
        bob.battlefield("Mountain", "Mountain")
        bob.hand("Storm Spark")
        alice.passes()
        bob.casts("Storm Spark", target = "alice", paying = listOf("Mountain"))
        allPass()
        var guard = 3
        while (pending != null && guard-- > 0) {
            assertEquals(bob.id, pending!!.player, "Asked: $pending")
            when (val d = pending) {
                is Decision.May -> answer(Answer.YesNo(false))
                is Decision.ManaPayment -> answer(Answer.ManaPayment(emptyList()))
                else -> kotlin.test.fail("Unexpected $d")
            }
        }
        allPass()
        expect { alice.hand has "Island" }
    }

    /** "Any player may activate this ability": the opponent activates it with their own discard. */
    // CR 602.1, 602.2
    @Test
    fun anyPlayerMayActivate() = scenario {
        alice.battlefield("Gloomwing Skulker")
        bob.hand("Island")
        alice.passes()
        bob.activates("Gloomwing Skulker", choosing = listOf("Island"))
        allPass()
        expect {
            "Gloomwing Skulker".power shouldBe 1
            bob.graveyard has "Island"
        }
    }

    /** "Whenever one or more creature cards leave your graveyard": cards leaving together trigger once. */
    // CR 603.2c, 603.10a
    @Test
    fun cardsLeavingTheGraveyardTogetherTriggerOnce() = scenario {
        alice.battlefield("Sinister Rootwork", "Plains", "Plains", "Plains", "Plains")
        alice.graveyard("Thicketwood Elves", "Elvish Seer")
        alice.hand("Muster the Forebears")
        alice.casts("Muster the Forebears", x = 1)
        settleStack()
        expect {
            alice.battlefield has "Thicketwood Elves"
            alice.battlefield has "Elvish Seer"
            alice.battlefield.names.count { it == "Plant Token" } shouldBe 1
            "Plant Token".counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    /** Muster the Forebears' creatures are exiled at the beginning of its controller's next upkeep. */
    // CR 603.7
    @Test
    fun musterExilesAtTheNextUpkeep() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.graveyard("Thicketwood Elves")
        alice.hand("Muster the Forebears")
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        alice.casts("Muster the Forebears", x = 1)
        settleStack()
        expect { alice.battlefield has "Thicketwood Elves" }
        advanceTo(TurnStep.Upkeep, turn = 2)
        settleStack()
        expect { alice.battlefield has "Thicketwood Elves" }
        advanceTo(TurnStep.Upkeep, turn = 3)
        settleStack()
        expect { alice.exile has "Thicketwood Elves" }
    }

    /** A leaves-the-battlefield trigger of a creature that leaves with others sees them all: The Bonehook Slaughter
     * counts each opponent's creature dying in one board wipe. */
    // CR 603.10a
    @Test
    fun boardWipeCountsEachDeath() = scenario {
        alice.battlefield("The Bonehook Slaughter", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Blazing Confluence")
        bob.battlefield("Thicketwood Elves", "Elvish Seer", "Umbral Elves")
        alice.casts("Blazing Confluence", modes = listOf(0, 0, 0))
        settleStack()
        expect {
            bob.graveyard.size shouldBe 3
            alice.life shouldBe 23
        }
    }
}
