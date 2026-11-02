package arcanum.engine.review

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Mechanic
import arcanum.api.Players
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.change.Changes
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.scenario.scenario
import arcanum.engine.state.EffectId
import arcanum.engine.state.EffectObject
import arcanum.engine.state.TargetRef
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/** Review 1: copies of spells, special costs, removal from combat, hidden information (D60) and randomness (D25). */
class Review1SpellsTest {
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

    /** [player] gains control of [name] now, as if an effect had just resolved (CR 613.1b). */
    private fun Scenario.gainControlNow(player: ScenarioPlayer, name: String) {
        val id = card(name)
        change(Changes.effect(state) { ts ->
            EffectObject(EffectId("review-control:$ts"), listOf(Mechanic.ControlledBy(Players.You)), affects = null,
                locked = listOf(id), source = null, controller = player.id, timestamp = ts, createdTurn = state.turn.number,
                duration = Duration.Indefinitely)
        })
    }

    /** A copy of a spell isn't cast: "whenever you cast" doesn't trigger for it. */
    // CR 707.10, 601.2i
    @Test
    fun aCopyIsntCast() = scenario {
        alice.battlefield("Novice Flamecaller", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Storm Spark", "Flash of Mirroring")
        alice.casts("Storm Spark", target = "bob", paying = listOf("Mountain"))
        settleStackTop()
        alice.casts("Flash of Mirroring", target = "Storm Spark")
        settleStack("bob")
        expect {
            bob.life shouldBe 14
            alice.battlefield.names.count { it == "Elemental Token" } shouldBe 2
        }
    }

    /** Resolves only the triggers above the first spell (the cast trigger), leaving the spell. */
    private fun Scenario.settleStackTop() {
        var guard = 5
        while (state.stack.size > 1 && guard-- > 0) allPass()
    }

    /** A copy of a kicked spell is kicked too. */
    // CR 707.10
    @Test
    fun copyOfAKickedSpellIsKicked() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Island", "Island")
        alice.hand("Flash Surge", "Expanse")
        alice.casts("Flash Surge", target = "bob", kicked = 1, paying = listOf("Mountain", "Mountain#1", "Mountain#2", "Mountain#3", "Mountain#4"))
        alice.casts("Expanse", target = "Flash Surge", paying = listOf("Island", "Island#1"))
        settleStack("bob")
        expect { bob.life shouldBe 12 }
    }

    /** A copy keeps the original's X. */
    // CR 707.10
    @Test
    fun copyKeepsX() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Island", "Island")
        alice.hand("Doomfire", "Expanse")
        alice.casts("Doomfire", target = "bob", x = 3, paying = listOf("Mountain", "Mountain#1", "Mountain#2", "Mountain#3"))
        alice.casts("Expanse", target = "Doomfire", paying = listOf("Island", "Island#1"))
        settleStack("bob")
        expect { bob.life shouldBe 14 }
    }

    /** A creature whose controller changes is removed from combat: it deals no combat damage. */
    // CR 506.4
    @Test
    fun controlChangeRemovesFromCombat() = scenario {
        Cards.invent("Review Ox", "{2}{G}", "Creature — Ox", "", "3/3")
        alice.battlefield("Review Ox")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Review Ox")
        gainControlNow(bob, "Review Ox")
        // A change made by hand doesn't run the engine; its next run removes the creature (as after any event)
        settle()
        assertTrue(state.combat!!.attackers.none { it.id == card("Review Ox") }, "Still attacking: ${state.combat}")
    }

    /** The same, by the engine's own flow: damage isn't dealt by a creature its defender now controls. */
    // CR 506.4
    @Test
    fun stolenAttackerDealsNoDamage() = scenario {
        Cards.invent("Review Ox", "{2}{G}", "Creature — Ox", "", "3/3")
        alice.battlefield("Review Ox")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Review Ox")
        allPass()
        gainControlNow(bob, "Review Ox")
        settleStack()
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 20 }
    }

    /** An attacking creature that stops being a creature is removed from combat. */
    // CR 506.4
    @Test
    fun noncreatureIsRemovedFromCombat() = scenario {
        Cards.invent("Review Ox", "{2}{G}", "Creature — Ox", "", "3/3")
        alice.battlefield("Review Ox")
        bob.battlefield("Plains")
        bob.hand("Rapid Retrofit")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Review Ox")
        alice.passes()
        bob.casts("Rapid Retrofit", target = "Review Ox")
        settleStack()
        assertTrue(state.combat!!.attackers.none { it.id == card("Review Ox") }, "Still attacking: ${state.combat}")
    }

    /** A legend put into the graveyard by the legend rule dies: its dies trigger works. */
    // CR 704.5j, 700.4
    @Test
    fun legendRuleDiesTrigger() = scenario {
        alice.hand("Velo, Master of Ceremonies")
        alice.battlefield("Velo, Master of Ceremonies", counters = listOf(CounterKind.PowerToughness(1, 1) to 2))
        alice.battlefield("Swamp", "Mountain")
        alice.casts("Velo, Master of Ceremonies")
        allPass()
        // Keep the new one: the old 3/3 dies
        answer(Answer.Keep(card("Velo, Master of Ceremonies", 0)))
        settleStack("bob")
        expect { bob.life shouldBe 17 }
    }

    /** Sacrificing a creature to a mana ability: its dies trigger and "whenever you sacrifice" wait for the Stack. */
    // CR 605.3b, 603.3
    @Test
    fun sacrificeForManaTriggers() = scenario {
        alice.battlefield("Renderer's Altar", "Velo, Master of Ceremonies", "Crimson Mourner")
        alice.activates("Renderer's Altar", choosing = listOf("Crimson Mourner"))
        expect { alice.pool.size shouldBe 2 }
        settleStack("bob")
        expect {
            "Velo, Master of Ceremonies".counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            bob.life shouldBe 19
        }
    }

    /** Fused Battleplate: equipping another creature unattaches it from the first, which is sacrificed. */
    // CR 701.3c, 603.2
    @Test
    fun unattachedTriggerSacrifices() = scenario {
        alice.battlefield("Fused Battleplate", "Thicketwood Elves", "Elvish Seer")
        attach("Fused Battleplate", "Thicketwood Elves")
        alice.activates("Fused Battleplate", target = "Elvish Seer")
        settleStack()
        expect {
            alice.graveyard has "Thicketwood Elves"
            "Elvish Seer".power shouldBe 4
        }
    }

    /** Living weapon: the Germ token enters equipped. */
    // CR 702.92a
    @Test
    fun livingWeapon() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Sun Band")
        alice.hand("Thornpod Husk")
        alice.casts("Thornpod Husk", paying = listOf("Mountain", "Mountain#1", "Mountain#2"))
        settleStack()
        expect {
            alice.battlefield has "Phyrexian Germ Token"
            "Phyrexian Germ Token".power shouldBe 2
        }
    }

    /** Protection from multicoloured: a multicoloured spell can't target it. */
    // CR 702.16b
    @Test
    fun protectionFromMulticolored() = scenario {
        alice.battlefield("Swamp", "Mountain", "Mountain")
        alice.hand("Warlord's Decree")
        bob.battlefield("Shalecoil Wyrm", counters = listOf(CounterKind.PowerToughness(1, 1) to 2))
        perform(Input.Cast(alice.id, card("Warlord's Decree")))
        answer(Answer.Modes(listOf(3, 1)))
        val d = decision<Decision.Targets>()
        assertFalse(TargetRef.Object(card("Shalecoil Wyrm")) in d.options)
    }

    /** Evoke: the creature is sacrificed as it enters, and its enters trigger still resolves; its controller orders
     * the two. */
    // CR 702.74a, 603.3b
    @Test
    fun evokeSacrificesAndTheEntersTriggerStillWorks() = scenario {
        alice.hand("Seclusion", "Plowshare Decree")
        bob.battlefield("Review Ox".also { Cards.invent("Review Ox", "{2}{G}", "Creature — Ox", "", "3/3") })
        val way = arcanum.engine.input.CastWay(alternative = view()[card("Seclusion")]!!.characteristics!!.abilities.first { inst ->
            (inst.ability as? arcanum.api.Ability.Static)?.mechanics?.any { it is Mechanic.AlternativeCost } == true
        }.origin)
        perform(Input.Cast(alice.id, card("Seclusion"), way))
        var guard = 5
        while (pending != null && guard-- > 0) when (val d = pending) {
            is Decision.ChooseObjects -> answer(Answer.Objects(listOf(card("Plowshare Decree"))))
            is Decision.ManaPayment -> answer(Answer.ManaPayment(d.suggestion))
            else -> fail("Unexpected $d")
        }
        settleStack("Review Ox")
        expect {
            alice.graveyard has "Seclusion"
            bob.exile has "Review Ox"
            bob.life shouldBe 23
        }
    }

    /** Delve exiles cards from the graveyard to pay generic mana. */
    // CR 702.66a
    @Test
    fun delvePays() = scenario {
        alice.battlefield("Island")
        alice.hand("Hoard Voyage")
        alice.graveyard("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.library("Island", "Island", "Island")
        perform(Input.Cast(alice.id, card("Hoard Voyage")))
        val d = decision<Decision.ManaPayment>()
        val uses = d.options.filterIsInstance<arcanum.engine.decision.ManaUse.Special>().take(7) +
            d.options.filterIsInstance<arcanum.engine.decision.ManaUse.Ability>().take(1)
        answer(Answer.ManaPayment(uses))
        settleStack()
        expect {
            alice.exile.size shouldBe 7
            alice.hand.size shouldBe 3
        }
    }

    /** A spell from a hidden hand chosen from a revealed hand: the host is asked what the cards are (D60). */
    // CR 701.20a, D60
    @Test
    fun revealedHiddenHandIsFilledByTheHost() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Mind Pilfer")
        bob.hiddenHand("Storm Spark", "Forest")
        alice.casts("Mind Pilfer", target = "bob")
        allPass()
        assertTrue(applied.any { it is Change.PendingSet && it.after?.decision is Decision.HostFill })
        expect {
            bob.graveyard has "Storm Spark"
            bob.hand.size shouldBe 1
        }
    }

    /** A plain draw from a hidden library asks the host nothing (D60). */
    // D60
    @Test
    fun plainDrawNeedsNoFill() = scenario {
        turn(number = 3, step = TurnStep.Upkeep)
        alice.library("Island", "Forest", hidden = true)
        allPass()
        assertTrue(applied.none { it is Change.PendingSet && it.after?.decision is Decision.HostFill })
        expect { alice.hand.size shouldBe 1 }
    }

    /** Discarding at random asks the host for the random pick and fills the discarded card (D25, D60). */
    // CR 701.9a, D25, D60
    @Test
    fun randomDiscardFromAHiddenHand() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Searing Question")
        alice.library("Island", "Island", "Island")
        bob.library("Island", "Island", "Island")
        bob.hiddenHand("Storm Spark", "Forest")
        alice.casts("Searing Question")
        allPass()
        expect {
            bob.graveyard.size shouldBe 3
            bob.hand.size shouldBe 2
            alice.graveyard.size shouldBe 4
        }
        assertTrue(applied.any { it is Change.PendingSet && it.after?.decision is Decision.Random })
    }

    /** Revealing the top card of a hidden library to lose life by its mana value asks the host (D60). */
    // CR 701.20a, D60
    @Test
    fun revealTopOfAHiddenLibrary() = scenario {
        turn(number = 2, active = bob, step = TurnStep.End)
        alice.battlefield("Shadow Informant")
        alice.library("Doomfire", "Island", hidden = true)
        advanceTo(TurnStep.Upkeep, turn = 3)
        settleStack()
        expect {
            alice.life shouldBe 19
            alice.hand.size shouldBe 1
        }
    }

    /** A coin flip result scripted by the host decides Mana Reliquary's damage. */
    // CR 705.1, D25
    @Test
    fun coinFlipLost() = scenario {
        turn(number = 2, active = bob, step = TurnStep.End)
        alice.battlefield("Mana Reliquary")
        alice.library("Island", "Island")
        random(Answer.CoinFlip(false))
        advanceTo(TurnStep.Upkeep, turn = 3)
        settleStack()
        expect { alice.life shouldBe 17 }
    }

    /** Reverting a cast that asked the host for a hidden card's identity restores the unknown card (D59, D60). */
    // D59, D60
    @Test
    fun revertingAFillForgetsNothingButRestores() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Mind Pilfer")
        bob.hiddenHand("Storm Spark")
        val before = state.deepCopy()
        alice.casts("Mind Pilfer", target = "bob")
        allPass()
        val reverted = state.deepCopy()
        applied.asReversed().forEach { reverted.revert(it) }
        reverted.timestampCounter = before.timestampCounter
        assertEquals(before, reverted)
    }
}
