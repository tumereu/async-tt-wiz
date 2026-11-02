package arcanum.engine.review

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Review 2: planeswalkers (CR 306, 606): loyalty abilities, damage and loyalty, the legend rule, phasing. */
class Review2PlaneswalkerTest {
    private fun loyalty(n: Int) = listOf(CounterKind.Loyalty to n)
    private val minusTwo = arcanum.api.Mechanic.ModifyPowerToughness(arcanum.api.Value.Fixed(-2), arcanum.api.Value.Fixed(0))

    private fun arcanum.engine.scenario.Scenario.loyaltyInputs(name: String) =
        Engine.options(state, alice.id).filterIsInstance<Input.Activate>().filter { it.source == card(name) }

    /** Only one loyalty ability of a permanent per turn: after Ignia's [0], none of its abilities is offered, and
     * activating one is refused. */
    // CR 606.3
    @Test
    fun oneLoyaltyAbilityPerTurn() = scenario {
        alice.battlefield("Ignia, Acolyte of Embers", counters = loyalty(4))
        alice.activates("Ignia, Acolyte of Embers", ability = 1)
        allPass()
        expect { alice.battlefield.names.count { it == "Elemental Token" } shouldBe 2 }
        assertTrue(loyaltyInputs("Ignia, Acolyte of Embers").isEmpty())
        val again = Input.Activate(alice.id, card("Ignia, Acolyte of Embers"),
            view()[card("Ignia, Acolyte of Embers")]!!.characteristics!!.abilities.filter { it.ability is Ability.Activated }[0].origin)
        assertFailsWith<IllegalArgumentException> { Engine.perform(state, again) }
    }

    /** Loyalty abilities only with an empty Stack, in a main phase of the controller's turn. */
    // CR 606.3
    @Test
    fun loyaltyAbilitiesOnlyWithAnEmptyStack() = scenario {
        alice.battlefield("Ignia, Acolyte of Embers", counters = loyalty(4))
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        alice.casts("Storm Spark", target = "bob")
        assertTrue(loyaltyInputs("Ignia, Acolyte of Embers").isEmpty(), "a spell is on the Stack")
    }

    /** Not on the opponent's turn. */
    // CR 606.3
    @Test
    fun loyaltyAbilitiesNotOnTheOpponentsTurn() = scenario {
        alice.battlefield("Ignia, Acolyte of Embers", counters = loyalty(4))
        turn(number = 2, active = bob, priority = alice)
        assertTrue(loyaltyInputs("Ignia, Acolyte of Embers").isEmpty())
    }

    /** Ignia's [0] puts a loyalty counter on each red planeswalker, herself included: 4 → 5. */
    // CR 606.4, 306.5c
    @Test
    fun zeroAbilityAddsLoyaltyToItself() = scenario {
        alice.battlefield("Ignia, Acolyte of Embers", counters = loyalty(4))
        alice.activates("Ignia, Acolyte of Embers", ability = 0)
        allPass()
        expect { "Ignia, Acolyte of Embers".counters(CounterKind.Loyalty) shouldBe 5 }
    }

    /** Damage to a planeswalker removes that many loyalty counters and isn't marked as damage. */
    // CR 306.8, 120.3c
    @Test
    fun damageRemovesLoyalty() = scenario {
        alice.battlefield("Ignia, Acolyte of Embers", counters = loyalty(4))
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        turn(number = 2, active = bob)
        bob.casts("Storm Spark", target = "Ignia, Acolyte of Embers")
        allPass()
        expect { "Ignia, Acolyte of Embers".counters(CounterKind.Loyalty) shouldBe 1 }
        assertEquals(0, state.objOrThrow(card("Ignia, Acolyte of Embers")).damage)
    }

    /** A planeswalker with 0 loyalty goes to the graveyard. */
    // CR 704.5i
    @Test
    fun zeroLoyaltyDies() = scenario {
        alice.battlefield("Renji Kagehara", counters = loyalty(3))
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        turn(number = 2, active = bob)
        bob.casts("Storm Spark", target = "Renji Kagehara")
        allPass()
        expect { alice.graveyard has "Renji Kagehara" }
    }

    /** A minus ability costing more loyalty than the planeswalker has can't be activated (Renji's [−7] at 3). */
    // CR 606.6
    @Test
    fun aMinusAbilityNeedsEnoughLoyalty() = scenario {
        alice.battlefield("Renji Kagehara", counters = loyalty(3))
        val abilities = view()[card("Renji Kagehara")]!!.characteristics!!.abilities.filter { it.ability is Ability.Activated }
        val minusSeven = Input.Activate(alice.id, card("Renji Kagehara"), abilities[2].origin)
        assertFalse(minusSeven in loyaltyInputs("Renji Kagehara"))
        assertFailsWith<IllegalArgumentException> { Engine.perform(state, minusSeven) }
        // [−2] is fine
        assertTrue(Input.Activate(alice.id, card("Renji Kagehara"), abilities[1].origin) in loyaltyInputs("Renji Kagehara"))
    }

    /** Combat damage to an attacked planeswalker removes loyalty. */
    // CR 306.8, 510.2
    @Test
    fun attackingAPlaneswalker() = scenario {
        alice.battlefield("Ignia, Acolyte of Embers", counters = loyalty(4))
        bob.battlefield("Brakka, Wild Whelp")
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks("Brakka, Wild Whelp", defender = "Ignia, Acolyte of Embers")
        advanceTo(TurnStep.EndOfCombat)
        expect {
            // Rage: +1/+1 counter as it attacks, so 3 damage
            "Ignia, Acolyte of Embers".counters(CounterKind.Loyalty) shouldBe 1
            alice.life shouldBe 20
        }
    }

    /** The legend rule for two planeswalkers with the same name. */
    // CR 704.5j
    @Test
    fun legendRuleForPlaneswalkers() = scenario {
        alice.battlefield("Ignia, Acolyte of Embers", counters = loyalty(1))
        alice.battlefield("Ignia, Acolyte of Embers", counters = loyalty(4))
        settle()
        val d = decision<Decision.LegendRule>()
        alice.answers(Answer.Keep(d.among.last()))
        expect { alice.graveyard has "Ignia, Acolyte of Embers" }
        assertEquals(4, state.objOrThrow(d.among.last()).counters(CounterKind.Loyalty))
    }

    /** Renji, entered this turn, phases out at alice's end step; phased out, it can't be attacked on bob's turn; it
     * phases in at alice's untap step. */
    // CR 702.26b, 702.26a, 502.1
    @Test
    fun renjiPhasesOutAndCantBeAttacked() = scenario {
        alice.battlefield("Island", "Swamp", "Island")
        alice.hand("Renji Kagehara")
        bob.battlefield("Brakka, Wild Whelp")
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        alice.casts("Renji Kagehara")
        allPass()
        expect { "Renji Kagehara".counters(CounterKind.Loyalty) shouldBe 3 }
        advanceTo(TurnStep.End)
        resolveStack()
        assertTrue(state.objOrThrow(card("Renji Kagehara")).phasedOut)
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        val targets = Engine.options(state, bob.id).filterIsInstance<Input.DeclareAttackers>().flatMap { it.attacks }.map { it.target }
        assertFalse(TargetRef.Object(card("Renji Kagehara")) in targets)
        bob.attacks()
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertFalse(state.objOrThrow(card("Renji Kagehara")).phasedOut)
    }

    /** Orla's [−2]: an artifact becomes a copy of a creature until end of turn, still an artifact; it reverts at end
     * of turn. */
    // CR 707.2, 707.9b, 514.2
    @Test
    fun orlaMakesATemporaryCopy() = scenario {
        alice.battlefield("Orla, Gilded Artificer", counters = loyalty(5))
        alice.battlefield("Sun Band", "Brakka, Wild Whelp")
        alice.activates("Orla, Gilded Artificer", targets = listOf("Sun Band", "Brakka, Wild Whelp"))
        allPass()
        expect {
            card("Sun Band").view.name shouldBe "Brakka, Wild Whelp"
            assertTrue(CardType.Artifact in card("Sun Band").types && CardType.Creature in card("Sun Band").types)
            card("Sun Band").power shouldBe 2
            "Orla, Gilded Artificer".counters(CounterKind.Loyalty) shouldBe 3
        }
        // A legend copy: the legend rule applies to the two Brakkas
        assertTrue(pending is Decision.LegendRule, "legend rule for two Brakkas: $pending")
    }

    /** −2/−0 "until your next turn" (as Orrin's [+1] makes) lasts through bob's turn and ends as alice's next turn
     * begins. */
    // CR 611.2a
    @Test
    fun untilYourNextTurnLastsThroughTheOpponentsTurn() = scenario {
        bob.battlefield("Brakka, Wild Whelp")
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        effect(alice, minusTwo, on = listOf("Brakka, Wild Whelp"), duration = Duration.UntilYourNextTurn)
        expect { "Brakka, Wild Whelp".power shouldBe 0 }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        expect { "Brakka, Wild Whelp".power shouldBe 0 }
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect { "Brakka, Wild Whelp".power shouldBe 2 }
    }

    /** "Until your next turn" from a player who leaves the game lasts until that turn would have begun. */
    // CR 800.4m
    @Test
    fun untilYourNextTurnWhenThePlayerLeaves() = scenario(players = 3) {
        bob.battlefield("Brakka, Wild Whelp")
        players.forEach { it.library("Island", "Island", "Island") }
        effect(alice, minusTwo, on = listOf("Brakka, Wild Whelp"), duration = Duration.UntilYourNextTurn)
        perform(Input.Concede(alice.id))
        // bob's turn, carol's turn: still -2/-0
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        expect { "Brakka, Wild Whelp".power shouldBe 0 }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        expect { "Brakka, Wild Whelp".power shouldBe 0 }
        // alice's turn would have begun now (turn 4 is bob's)
        advanceTo(TurnStep.PrecombatMain, turn = 4)
        expect { "Brakka, Wild Whelp".power shouldBe 2 }
    }

    /** A player who leaves during another player's turn: their "until your next turn" ends as the next turn begins
     * when that turn would have been theirs (carol's turn 3 → alice's seat → bob's turn 4). */
    // CR 800.4m
    @Test
    fun untilYourNextTurnWhenThePlayerLeavesInAnotherPlayersTurn() = scenario(players = 3) {
        val carol = players[2]
        bob.battlefield("Brakka, Wild Whelp")
        players.forEach { it.library("Island", "Island", "Island") }
        turn(number = 3, active = carol)
        effect(alice, minusTwo, on = listOf("Brakka, Wild Whelp"), duration = Duration.UntilYourNextTurn)
        perform(Input.Concede(alice.id))
        expect { "Brakka, Wild Whelp".power shouldBe 0 }
        advanceTo(TurnStep.Upkeep, turn = 4)
        expect { "Brakka, Wild Whelp".power shouldBe 2 }
    }

    /** "Until the end of your next turn" of a player who left also ends as that turn would have begun, not at the end
     * of the turn that follows it. */
    // CR 800.4m
    @Test
    fun untilTheEndOfYourNextTurnWhenThePlayerLeaves() = scenario(players = 3) {
        bob.battlefield("Brakka, Wild Whelp")
        players.forEach { it.library("Island", "Island", "Island") }
        effect(alice, minusTwo, on = listOf("Brakka, Wild Whelp"), duration = Duration.UntilEndOfYourNextTurn)
        perform(Input.Concede(alice.id))
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        expect { "Brakka, Wild Whelp".power shouldBe 0 }
        advanceTo(TurnStep.Upkeep, turn = 4)
        expect { "Brakka, Wild Whelp".power shouldBe 2 }
    }

    /** Another player's "until your next turn" isn't touched by a player leaving: bob's effect lasts to bob's turn. */
    // CR 611.2b, 800.4m
    @Test
    fun untilYourNextTurnOfAPlayerStillInTheGame() = scenario(players = 3) {
        alice.battlefield("Brakka, Wild Whelp")
        players.forEach { it.library("Island", "Island", "Island") }
        turn(number = 3, active = players[2])
        effect(bob, minusTwo, on = listOf("Brakka, Wild Whelp"), duration = Duration.UntilYourNextTurn)
        perform(Input.Concede(players[2].id))
        // carol's turn goes on without her; alice's turn 4 passes over nobody who left between carol and alice
        advanceTo(TurnStep.PrecombatMain, turn = 4)
        expect { "Brakka, Wild Whelp".power shouldBe 0 }
        advanceTo(TurnStep.Upkeep, turn = 5)
        expect { "Brakka, Wild Whelp".power shouldBe 2 }
    }

    /** Ignia's [−2] casts an instant from the graveyard; it's exiled instead of going back to the graveyard. */
    // CR 608.2g, 614.1a
    @Test
    fun igniaCastsFromTheGraveyardAndExiles() = scenario {
        alice.battlefield("Ignia, Acolyte of Embers", counters = loyalty(4))
        alice.battlefield("Mountain")
        alice.graveyard("Storm Spark")
        alice.activates("Ignia, Acolyte of Embers", ability = 2, target = "Storm Spark")
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        while (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("bob"))))
        while (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()
        expect {
            bob.life shouldBe 17
            alice.exile has "Storm Spark"
        }
    }

    /** The opponent can't activate alice's loyalty abilities. */
    // CR 606.3, 602.2
    @Test
    fun onlyTheControllerActivatesLoyaltyAbilities() = scenario {
        alice.battlefield("Ignia, Acolyte of Embers", counters = loyalty(4))
        turn(number = 1, active = alice, priority = alice)
        val origin = view()[card("Ignia, Acolyte of Embers")]!!.characteristics!!.abilities.filter { it.ability is Ability.Activated }[1].origin
        assertFailsWith<IllegalArgumentException> { Engine.perform(state, Input.Activate(bob.id, card("Ignia, Acolyte of Embers"), origin)) }
    }

    /** A planeswalker cast from hand enters with its printed loyalty, and the Stack is empty for its ability at once. */
    // CR 306.5b, 606.3
    @Test
    fun aCastPlaneswalkerCanActivateAtOnce() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand("Ignia, Acolyte of Embers")
        alice.casts("Ignia, Acolyte of Embers")
        allPass()
        expect { "Ignia, Acolyte of Embers".counters(CounterKind.Loyalty) shouldBe 4 }
        // Both [0] abilities; [−2] needs a target instant or sorcery card in the graveyard
        assertEquals(2, loyaltyInputs("Ignia, Acolyte of Embers").size, "planeswalkers aren't summoning sick")
        assertEquals(Zone.Battlefield, state.objOrThrow(card("Ignia, Acolyte of Embers")).zone)
    }

    /** Orla makes a Servo token on a noncreature spell; her [−2] then makes Sun Band a copy of that token: a 1/1
     * Servo artifact creature, still a card (copying a token doesn't make a token). */
    // CR 707.2, 111.1
    @Test
    fun aCardCopyingATokenIsntAToken() = scenario {
        alice.battlefield("Orla, Gilded Artificer", counters = loyalty(5))
        alice.battlefield("Sun Band", "Mountain")
        alice.hand("Storm Spark")
        alice.casts("Storm Spark", target = "bob")
        repeat(2) { allPass() }
        expect { alice.battlefield has "Servo Token" }
        alice.activates("Orla, Gilded Artificer", targets = listOf("Sun Band", "Servo Token"))
        allPass()
        expect {
            card("Sun Band").power shouldBe 1
            assertTrue(arcanum.api.Subtype("Servo") in card("Sun Band").subtypes)
        }
        assertFalse(state.objOrThrow(card("Sun Band")).token)
    }
}
