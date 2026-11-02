package arcanum.engine.review

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.api.Players
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Review 1: combat (CR 506–511) and state-based actions (CR 704). */
class Review1CombatTest {
    private fun cards() {
        Cards.invent("Review Bear", "{1}{G}", "Creature — Bear", "", "2/2")
        Cards.invent("Review Ox", "{2}{G}", "Creature — Ox", "", "3/3")
        Cards.invent("Review Wurm", "{4}{G}", "Creature — Wurm", "Trample", "5/5")
        Cards.invent("Review Mammoth", "{3}{G}", "Creature — Elephant", "", "4/4")
    }

    /** Settles the Stack, answering "may" with yes and targets with the first option. */
    private fun Scenario.settleStack() {
        var guard = 20
        while ((state.stack.isNotEmpty() || pending != null) && guard-- > 0) {
            when (val d = pending) {
                is Decision.Targets -> answer(Answer.Targets(d.options.take(d.min.coerceAtLeast(1))))
                is Decision.May -> answer(Answer.YesNo(true))
                is Decision.ChooseObjects -> answer(Answer.Objects(d.options.take(d.min)))
                is Decision.OrderTriggers -> answer(Answer.Order(d.triggers.indices.toList()))
                null -> allPass()
                else -> return
            }
        }
    }

    /** Lifelink life is gained at the same time as the combat damage: a player at 2 who takes 3 and gains 3 lives. */
    // CR 510.2, 702.15b, 120.3f
    @Test
    fun lifelinkIsSimultaneousWithDamage() = scenario {
        cards()
        turn(number = 2, active = bob, step = TurnStep.PrecombatMain)
        alice.life = 2
        alice.battlefield("Velmora of the Sleep-Den")
        bob.battlefield("Review Ox", "Review Bear")
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks("Review Ox", "Review Bear")
        allPass()
        alice.blocks("Velmora of the Sleep-Den" to "Review Bear")
        allPass()
        expect { alice.life shouldBe 2 }
        assertTrue(!state.player(alice.id).lost)
    }

    /** A first-striking blocker kills the attacker before it deals damage. */
    // CR 510.4, 702.7b
    @Test
    fun firstStrikeBlockerSurvives() = scenario {
        cards()
        alice.battlefield("Review Bear")
        bob.battlefield("Seraphine, Keeper of the Gate")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Review Bear")
        allPass()
        bob.blocks("Seraphine, Keeper of the Gate" to "Review Bear")
        allPass()
        allPass()
        expect {
            bob.battlefield has "Seraphine, Keeper of the Gate"
            alice.graveyard has "Review Bear"
        }
    }

    /** Several blockers: the attacker's controller divides the damage as they choose, even with enough for each. */
    // CR 510.1c
    @Test
    fun damageAmongBlockersIsAChoice() = scenario {
        cards()
        alice.battlefield("Review Mammoth")
        bob.battlefield("Review Bear", "Thicketwood Elves")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Review Mammoth")
        allPass()
        bob.blocks("Review Bear" to "Review Mammoth", "Thicketwood Elves" to "Review Mammoth")
        allPass()
        assertIs<Decision.Divide>(pending)
    }

    /** A blocked trampler whose blocker left assigns all its damage to the player. */
    // CR 702.19e
    @Test
    fun trampleWithTheBlockerGone() = scenario {
        cards()
        alice.battlefield("Review Wurm")
        bob.battlefield("Review Bear", "Mountain")
        bob.hand("Storm Spark")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Review Wurm")
        allPass()
        bob.blocks("Review Bear" to "Review Wurm")
        alice.passes()
        bob.casts("Storm Spark", target = "Review Bear")
        allPass()
        allPass()
        expect { bob.life shouldBe 15 }
    }

    /** A trampler attacking a planeswalker and blocked assigns the excess to the planeswalker. */
    // CR 702.19b, 506.4
    @Test
    fun trampleOverABlockerIntoAPlaneswalker() = scenario {
        cards()
        alice.battlefield("Review Wurm")
        bob.battlefield("Thicketwood Elves")
        bob.battlefield("Ignia, Acolyte of Embers", counters = listOf(CounterKind.Loyalty to 4))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Review Wurm", defender = "Ignia, Acolyte of Embers")
        allPass()
        bob.blocks("Thicketwood Elves" to "Review Wurm")
        allPass()
        // CR 702.19b: the excess is its controller's choice; the suggestion sends it to the planeswalker
        divideAsSuggested()
        expect {
            bob.graveyard has "Ignia, Acolyte of Embers"
            bob.life shouldBe 20
        }
    }

    /** Exalted: a creature attacking alone gets +1/+1 for each exalted instance. */
    // CR 702.83a, 702.83b
    @Test
    fun exaltedStacks() = scenario {
        cards()
        alice.battlefield("Review Bear", "Base Exarch", "Highborn Exarch")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Review Bear")
        settleStack()
        expect { "Review Bear".power shouldBe 4 }
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 16 }
    }

    /** Battle cry pumps each other attacking creature, not itself. */
    // CR 702.91a
    @Test
    fun battleCryPumpsTheOthers() = scenario {
        cards()
        alice.battlefield("Beacon Pest", "Review Bear", "Review Ox")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Beacon Pest", "Review Bear", "Review Ox")
        settleStack()
        expect {
            "Beacon Pest".power shouldBe 0
            "Review Bear".power shouldBe 3
            "Review Ox".power shouldBe 4
        }
    }

    /** Ninjutsu swaps an unblocked attacker for the Ninja, which deals combat damage and triggers. */
    // CR 702.49a, 702.49c
    @Test
    fun ninjutsuSwapsAnUnblockedAttacker() = scenario {
        cards()
        alice.battlefield("Review Bear", "Island", "Island")
        alice.hand("Ninja of the Silent Hours")
        alice.library("Mountain", "Mountain")
        bob.battlefield("Wall of Portents")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Review Bear")
        allPass()
        bob.blocks()
        // In the declare blockers step, after blockers: ninjutsu
        val ninjutsu = arcanum.engine.Engine.options(state, alice.id).filterIsInstance<Input.Activate>()
            .firstOrNull { it.source == card("Ninja of the Silent Hours") } ?: kotlin.test.fail("No ninjutsu offered")
        perform(ninjutsu)
        var guard = 5
        while (pending != null && guard-- > 0) when (val d = pending) {
            is Decision.ChooseObjects -> answer(Answer.Objects(listOf(card("Review Bear"))))
            is Decision.ManaPayment -> answer(Answer.ManaPayment(d.suggestion))
            else -> kotlin.test.fail("Unexpected $d")
        }
        settleStack()
        expect {
            alice.hand has "Review Bear"
            alice.battlefield has "Ninja of the Silent Hours"
        }
        assertTrue(state.combat!!.attackers.any { it.id == card("Ninja of the Silent Hours") })
        allPass()
        settleStack()
        expect {
            bob.life shouldBe 18
            alice.hand has "Mountain"
        }
    }

    /** With three players, each defending player declares blockers for the creatures attacking them. */
    // CR 506.2, 509.1
    @Test
    fun twoDefendersBlockSeparately() = scenario(players = 3) {
        cards()
        alice.battlefield("Review Bear", "Review Ox")
        bob.battlefield("Thicketwood Elves")
        carol.battlefield("Elvish Seer")
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(
            arcanum.engine.input.Attack(card("Review Bear"), TargetRef.Player(bob.id)),
            arcanum.engine.input.Attack(card("Review Ox"), TargetRef.Player(carol.id)),
        )))
        allPass()
        assertTrue(Review1.refusedOptions(state).isEmpty(), Review1.refusedOptions(state).toString())
        // bob can't block the Ox (it attacks carol)
        kotlin.test.assertFailsWith<IllegalArgumentException> { bob.blocks("Thicketwood Elves" to "Review Ox") }
        bob.blocks("Thicketwood Elves" to "Review Bear")
        carol.blocks()
        allPass()
        expect {
            carol.life shouldBe 17
            bob.graveyard has "Thicketwood Elves"
        }
    }

    /** A creature that must attack each combat is declared even if the player declares nothing. */
    // CR 508.1d
    @Test
    fun mustAttackCreatureCantBeLeftHome() = scenario {
        alice.battlefield("Kael's Marauder")
        advanceTo(TurnStep.DeclareAttackers)
        kotlin.test.assertFailsWith<IllegalArgumentException> { perform(Input.DeclareAttackers(alice.id, emptyList())) }
    }

    /** A crewed Vehicle is an artifact creature until end of turn and can attack. */
    // CR 702.122a, 301.7b
    @Test
    fun crewedVehicleAttacks() = scenario {
        cards()
        alice.battlefield("Runner's Skiff", "Review Bear")
        alice.library("Island", "Island")
        alice.activates("Runner's Skiff", choosing = listOf("Review Bear"))
        allPass()
        expect { assertTrue(CardType.Creature in "Runner's Skiff".types) }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Runner's Skiff")
        settleStack()
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 17 }
    }

    // ---- State-based actions

    /** Two legendary permanents with the same name controlled by different players both stay. */
    // CR 704.5j
    @Test
    fun legendRuleIsPerController() = scenario {
        alice.battlefield("Velo, Master of Ceremonies")
        bob.battlefield("Velo, Master of Ceremonies")
        settle()
        expect {
            alice.battlefield has "Velo, Master of Ceremonies"
            bob.battlefield has "Velo, Master of Ceremonies"
        }
    }

    /** Gaining control of a legend with the same name as yours: the legend rule applies to you. */
    // CR 704.5j, 613.1b
    @Test
    fun legendRuleAfterAControlChange() = scenario {
        alice.battlefield("Velo, Master of Ceremonies")
        bob.battlefield("Velo, Master of Ceremonies")
        // bob gains control of alice's Velo: bob now controls two
        effect(bob, Mechanic.ControlledBy(Players.You), on = listOf("Velo, Master of Ceremonies"))
        settle()
        assertEquals(bob.id, decision<Decision.LegendRule>().player)
    }

    /** A creature with damage whose toughness drops to that damage is destroyed (lethal damage), not put into the
     * graveyard for 0 toughness. */
    // CR 704.5g, 704.5f
    @Test
    fun loweredToughnessMakesDamageLethal() = scenario {
        alice.battlefield("Wall of Portents", "Swamp", "Swamp")
        alice.hand("Communal Cruelty")
        bob.battlefield("Mountain", "Mountain")
        bob.hand("Storm Spark")
        alice.passes()
        bob.casts("Storm Spark", target = "Wall of Portents")
        allPass()
        expect { "Wall of Portents".view.obj!!.damage shouldBe 3 }
        alice.casts("Communal Cruelty", modes = listOf(1), target = "Wall of Portents")
        settleStack()
        expect { alice.graveyard has "Wall of Portents" }
        assertTrue(applied.any { c -> c is arcanum.engine.change.Change.ZoneChange && c.moves.any { m ->
            arcanum.engine.change.Meaning.Destroyed in m.meanings } }, "Destroyed, by lethal damage")
    }

    /** In a multiplayer game, a player who loses leaves the game: their permanents leave too. */
    // CR 800.4a
    @Test
    fun aLosingPlayersPermanentsLeave() = scenario(players = 3) {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        carol.life = 3
        carol.battlefield("Thicketwood Elves", "Forest")
        alice.casts("Storm Spark", target = "carol")
        allPass()
        assertTrue(state.player(carol.id).lost)
        expect { carol.battlefield.isEmpty() }
    }

    /** In a multiplayer game, the effects that gave a player who left control of something end: the creature goes
     * back to its owner. */
    // CR 800.4a
    @Test
    fun aLosingPlayersStolenPermanentGoesBack() = scenario(players = 3) {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Thicketwood Elves")
        carol.life = 3
        effect(carol, Mechanic.ControlledBy(Players.You), on = listOf("Thicketwood Elves"))
        alice.casts("Storm Spark", target = "carol")
        allPass()
        expect {
            bob.battlefield has "Thicketwood Elves"
            "Thicketwood Elves".controller shouldBe bob.id
        }
    }

    /** In a two-player game, the last player standing wins: the game is over, nobody gets priority. */
    // CR 104.2a
    @Test
    fun theGameEndsWhenOnePlayerIsLeft() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        bob.life = 3
        alice.casts("Storm Spark", target = "bob")
        allPass()
        assertTrue(state.player(bob.id).lost)
        assertEquals(emptyList(), arcanum.engine.Engine.options(state, alice.id).filterNot { it is Input.PassPriority }.filterIsInstance<Input.Cast>())
    }

    /** An Aura on a creature that gains protection from the Aura's colour goes to the graveyard. */
    // CR 704.5m, 702.16c
    @Test
    fun auraFallsOffAProtectedCreature() = scenario {
        cards()
        alice.battlefield("Review Bear", "Rapid Retrofit")
        attach("Rapid Retrofit", "Review Bear")
        effect(alice, Mechanic.Protection(arcanum.api.ObjectFilter.OfColor(arcanum.api.Color.White)),
            affects = arcanum.api.Affected.Objects(arcanum.api.Objects.Bound("it")), names = mapOf("it" to "Review Bear"))
        settle()
        expect { alice.graveyard has "Rapid Retrofit" }
    }
}
