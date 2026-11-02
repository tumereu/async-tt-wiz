package arcanum.engine.review

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Review 1: games of three or more players (CR 800–802) and other interactions of the flow. */
class Review1MultiplayerTest {
    private fun Scenario.settleStack(vararg names: String) {
        var guard = 30
        while ((state.stack.isNotEmpty() || pending != null) && guard-- > 0) {
            when (val d = pending) {
                is Decision.Targets -> answer(Answer.Targets(names.map { target(it) }.filter { it in d.options }.take(d.max ?: 1)
                    .ifEmpty { d.options.take(d.min.coerceAtLeast(1)) }))
                is Decision.May -> answer(Answer.YesNo(true))
                is Decision.OrderTriggers -> answer(Answer.Order(d.triggers.indices.toList()))
                is Decision.ChooseObjects -> answer(Answer.Objects(d.options.take(d.min)))
                null -> allPass()
                else -> return
            }
        }
    }

    /** When the active player leaves the game during their turn, the next player in turn order gets priority. */
    // CR 800.4j
    @Test
    fun activePlayerLeavesPriorityGoesToTheNext() = scenario(players = 3) {
        turn(number = 2, active = bob)
        bob.life = 2
        bob.battlefield("Swamp", "Swamp")
        bob.hand("Midnight Murmur")
        bob.library("Island", "Island")
        bob.casts("Midnight Murmur")
        allPass()
        assertTrue(state.player(bob.id).lost)
        assertEquals(carol.id, state.priority.holder)
    }

    /** A creature sacrificed as a cost: its dies trigger goes on the Stack above the spell and resolves first. */
    // CR 601.2h, 603.3
    @Test
    fun sacrificedAsACostTriggerResolvesFirst() = scenario {
        alice.battlefield("Swamp", "Crimson Mourner")
        alice.hand("Bone Splinters")
        bob.battlefield("Thicketwood Elves")
        alice.casts("Bone Splinters", target = "Thicketwood Elves", option = "Sacrifice", choosing = listOf("Crimson Mourner"))
        var guard = 3
        while (pending is Decision.Targets && guard-- > 0) answer(Answer.Targets(listOf(target("bob"))))
        expect {
            stack.size shouldBe 2
            alice.graveyard has "Crimson Mourner"
        }
        allPass()
        expect {
            bob.life shouldBe 19
            bob.battlefield has "Thicketwood Elves"
        }
    }

    /** Ward also triggers on an activated ability that targets the permanent. */
    // CR 702.21a
    @Test
    fun wardTriggersOnAnAbility() = scenario {
        alice.battlefield("Cinder Spellcharge", "Mountain")
        bob.battlefield("Scrapquilt Automaton")
        alice.activates("Cinder Spellcharge", target = "Scrapquilt Automaton")
        expect { stack.size shouldBe 2 }
        settleStack()
        expect { bob.battlefield has "Scrapquilt Automaton" }
    }

    /** Shroud: even its controller can't target it. */
    // CR 702.18a
    @Test
    fun shroudStopsItsOwnController() = scenario {
        alice.battlefield("Mossbound Keeper", "Forest", "Mountain")
        alice.hand("Storm Spark")
        alice.activates("Mossbound Keeper", target = "Mossbound Keeper", choosing = listOf("Forest"))
        settleStack()
        perform(Input.Cast(alice.id, card("Storm Spark")))
        assertFalse(TargetRef.Object(card("Mossbound Keeper")) in decision<Decision.Targets>().options)
    }

    /** "Any player may cast creature spells … without paying their mana costs and as though they had flash": the
     * opponent casts one on alice's turn. */
    // CR 601.2, 702.8a
    @Test
    fun opponentCastsForFreeWithFlash() = scenario {
        alice.battlefield("Verdant Hollowmere")
        bob.hand("Elvish Seer")
        alice.passes()
        val free = Engine.options(state, bob.id).filterIsInstance<Input.Cast>().filter { it.card == card("Elvish Seer") }
        assertTrue(free.isNotEmpty(), "Not offered")
        perform(free.first { it.way.permission != null })
        allPass()
        expect { bob.battlefield has "Elvish Seer" }
    }

    /** A beginning-of-combat trigger with a target. */
    // CR 507.1, 603.3d
    @Test
    fun beginningOfCombatTrigger() = scenario {
        alice.battlefield("Radiant Acolyte", "Thicketwood Elves")
        advanceTo(TurnStep.BeginningOfCombat)
        settleStack("Thicketwood Elves")
        expect { "Thicketwood Elves".counters(CounterKind.PowerToughness(1, 1)) shouldBe 1 }
    }

    /** Four players: attacking one of them asks only that player for blockers. */
    // CR 802.1, 506.2
    @Test
    fun onlyTheAttackedPlayerBlocks() = scenario(players = 4) {
        alice.battlefield("Thicketwood Elves")
        bob.battlefield("Elvish Seer")
        carol.battlefield("Umbral Elves")
        dave.battlefield("Wall of Portents")
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card("Thicketwood Elves"), TargetRef.Player(dave.id)))))
        allPass()
        assertTrue(Engine.options(state, bob.id).none { it is Input.DeclareBlockers })
        assertTrue(Engine.options(state, carol.id).none { it is Input.DeclareBlockers })
        assertTrue(Engine.options(state, dave.id).any { it is Input.DeclareBlockers })
    }

    /** "Each opponent loses 1 life" with two opponents. */
    // CR 102.2, 102.3
    @Test
    fun eachOpponentWithTwoOpponents() = scenario(players = 3) {
        alice.battlefield("Ghoulish Reveler", "Mountain")
        alice.hand("Storm Spark")
        alice.casts("Storm Spark", target = "Ghoulish Reveler")
        settleStack()
        expect {
            bob.life shouldBe 19
            carol.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    /** An extra turn comes right after this one, before the next player's turn. */
    // CR 500.7
    @Test
    fun extraTurnBeforeTheNextPlayer() = scenario(players = 3) {
        alice.battlefield("Island", "Island")
        alice.hand("Time Stride")
        alice.library("Island", "Island", "Island")
        bob.library("Island", "Island")
        carol.library("Island", "Island")
        alice.casts("Time Stride")
        settleStack()
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertEquals(alice.id, state.turn.active)
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertEquals(bob.id, state.turn.active)
    }

    /** A player who left the game isn't a legal target. */
    // CR 800.4a, 115.1
    @Test
    fun aLostPlayerIsntATarget() = scenario(players = 3) {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        state.players[2] = state.players[2].copy(lost = true)
        perform(Input.Cast(alice.id, card("Storm Spark")))
        assertFalse(TargetRef.Player(carol.id) in decision<Decision.Targets>().options)
    }

    /** Storm counts the spells every player cast this turn. */
    // CR 702.40a
    @Test
    fun stormCountsTheOpponentsSpells() = scenario {
        alice.battlefield("Forest", "Forest", "Mountain")
        alice.hand("Storm Spark", "Chitterstorm")
        bob.battlefield("Mountain")
        bob.hand("Magma Needle")
        alice.casts("Storm Spark", target = "bob", paying = listOf("Mountain"))
        alice.passes()
        bob.casts("Magma Needle", target = "alice")
        settleStack()
        alice.casts("Chitterstorm")
        settleStack()
        expect { alice.battlefield.names.count { it == "Squirrel Token" } shouldBe 3 }
    }

    /** A Saga with its last chapter on the Stack isn't sacrificed until that chapter has resolved. */
    // CR 714.4
    @Test
    fun sagaWaitsForItsLastChapter() = scenario {
        alice.battlefield("Haruka's Age of Honor", counters = listOf(CounterKind.Lore to 2))
        alice.battlefield("Thicketwood Elves")
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        turn(number = 2, active = bob, step = TurnStep.End)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        expect {
            stack.size shouldBe 1
            "Haruka's Age of Honor".zone shouldBe arcanum.api.Zone.Battlefield
        }
        settleStack("Thicketwood Elves")
        expect {
            "Haruka's Age of Honor".view.characteristics!!.name shouldBe "Likeness of Haruka"
            "Haruka's Age of Honor".zone shouldBe arcanum.api.Zone.Battlefield
        }
    }
}
