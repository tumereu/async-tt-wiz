package arcanum.engine.review

import arcanum.api.Ability
import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Review 1: planeswalkers (CR 606), phasing, delayed sacrifices, special casting costs and undo of a cancel. */
class Review1PermanentsTest {
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

    private fun Scenario.loyaltyAbilities(name: String) = Engine.options(state, alice.id).filterIsInstance<Input.Activate>()
        .filter { it.source == card(name) }

    /** A planeswalker's loyalty abilities: one activation per turn for that planeswalker. */
    // CR 606.3
    @Test
    fun oneLoyaltyAbilityEachTurn() = scenario {
        alice.battlefield("Ignia, Acolyte of Embers", counters = listOf(CounterKind.Loyalty to 4))
        alice.activates("Ignia, Acolyte of Embers", ability = 1)
        settleStack()
        assertEquals(emptyList(), loyaltyAbilities("Ignia, Acolyte of Embers"))
    }

    /** Loyalty abilities are sorcery speed: not with something on the Stack. */
    // CR 606.3
    @Test
    fun loyaltyAbilityNotWithTheStackFull() = scenario {
        alice.battlefield("Ignia, Acolyte of Embers", counters = listOf(CounterKind.Loyalty to 4))
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        alice.casts("Storm Spark", target = "bob")
        assertEquals(emptyList(), loyaltyAbilities("Ignia, Acolyte of Embers"))
    }

    /** A minus ability that takes loyalty to 0: the planeswalker dies, the ability still resolves. */
    // CR 606.6, 704.5i, 113.7a
    @Test
    fun abilityResolvesAfterItsPlaneswalkerDied() = scenario {
        alice.battlefield("Renji Kagehara", counters = listOf(CounterKind.Loyalty to 2))
        alice.activates("Renji Kagehara", ability = 1)
        expect { alice.graveyard has "Renji Kagehara" }
        settleStack()
        expect { alice.battlefield has "Ninja Token" }
    }

    /** Ignia's tokens are sacrificed at the beginning of the next end step. */
    // CR 603.7
    @Test
    fun tokensSacrificedAtTheNextEndStep() = scenario {
        alice.battlefield("Ignia, Acolyte of Embers", counters = listOf(CounterKind.Loyalty to 4))
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        alice.activates("Ignia, Acolyte of Embers", ability = 1)
        settleStack()
        expect { alice.battlefield.names.count { it == "Elemental Token" } shouldBe 2 }
        advanceTo(TurnStep.End)
        settleStack()
        expect { alice.battlefield.names.count { it == "Elemental Token" } shouldBe 0 }
    }

    /** Renji phases out at its controller's end step the turn it entered, and phases in at their next untap step;
     * while phased out it can't be attacked. */
    // CR 702.26a, 702.26b
    @Test
    fun phasedOutPlaneswalkerCantBeAttacked() = scenario {
        alice.battlefield("Island", "Swamp", "Mountain")
        alice.hand("Renji Kagehara")
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        bob.battlefield("Thicketwood Elves")
        alice.casts("Renji Kagehara")
        settleStack()
        advanceTo(TurnStep.End)
        settleStack()
        assertTrue(state.objOrThrow(card("Renji Kagehara")).phasedOut)
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        val targets = Engine.options(state, bob.id).filterIsInstance<Input.DeclareAttackers>().flatMap { it.attacks.map { a -> a.target } }
        assertTrue(targets.none { it == arcanum.engine.state.TargetRef.Object(card("Renji Kagehara")) }, "Attackable: $targets")
        bob.attacks("Thicketwood Elves")
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertTrue(!state.objOrThrow(card("Renji Kagehara")).phasedOut)
        expect { "Renji Kagehara".loyalty shouldBe 3 }
    }

    /** Overload: "each" instead of "target": every creature, the caster's own too. */
    // CR 702.96a
    @Test
    fun overloadHitsEach() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", "Swamp", "Elvish Seer")
        alice.hand("Curse Word")
        bob.battlefield("Thicketwood Elves", "Umbral Elves")
        val way = CastWay(alternative = view()[card("Curse Word")]!!.characteristics!!.abilities.first { inst ->
            (inst.ability as? Ability.Static)?.mechanics?.any { it is Mechanic.AlternativeCost } == true
        }.origin)
        perform(Input.Cast(alice.id, card("Curse Word"), way))
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        settleStack()
        expect {
            bob.graveyard.size shouldBe 2
            alice.graveyard has "Elvish Seer"
        }
    }

    /** Escalate: each mode beyond the first costs a discard. */
    // CR 702.120a
    @Test
    fun escalateCostsADiscardPerExtraMode() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Communal Cruelty", "Island")
        bob.battlefield("Thicketwood Elves")
        alice.casts("Communal Cruelty", modes = listOf(1, 2), targets = listOf("Thicketwood Elves", "bob"), choosing = listOf("Island"))
        settleStack()
        expect {
            alice.graveyard has "Island"
            bob.graveyard has "Thicketwood Elves"
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    /** Retrace: cast from the graveyard by discarding a land in addition. */
    // CR 702.81a
    @Test
    fun retraceFromTheGraveyard() = scenario {
        alice.battlefield("Swamp")
        alice.graveyard("Crow's Larceny")
        alice.hand("Forest")
        bob.hand("Island")
        alice.casts("Crow's Larceny", target = "bob", choosing = listOf("Forest"))
        settleStack()
        expect {
            alice.graveyard has "Forest"
            bob.graveyard has "Island"
            alice.graveyard has "Crow's Larceny"
        }
    }

    /** Mana that can only be spent on artifact spells pays for an artifact. */
    // CR 106.6
    @Test
    fun restrictedManaPaysForAnArtifact() = scenario {
        alice.battlefield("Tinker's Atelier")
        alice.hand("Sun Band")
        alice.casts("Sun Band")
        allPass()
        expect { alice.battlefield has "Sun Band" }
    }

    /** A spell only restricted mana can pay for is offered; one it can't pay for isn't. */
    // CR 106.6, 117.1
    @Test
    fun restrictedManaSpellIsOffered() = scenario {
        alice.battlefield("Tinker's Atelier")
        alice.hand("Magma Needle", "Sun Band")
        val casts = Engine.options(state, alice.id).filterIsInstance<Input.Cast>()
        assertTrue(casts.any { it.card == card("Sun Band") }, "Not offered: $casts")
        assertTrue(casts.none { it.card == card("Magma Needle") }, "Offered: $casts")
    }

    /** Undoing a cancel gives back the open cast exactly (D59). */
    // D58, D59
    @Test
    fun cancelOutcomeReverts() = scenario {
        alice.battlefield("Stitchwork Porter", "Sinister Rootwork")
        alice.graveyard("Tombscuttler")
        perform(Input.Cast(alice.id, card("Tombscuttler")))
        performReverting(Input.Cancel(alice.id))
    }

    /** Kicker {X}: Pilfering Skydiver kicked for 2 takes an artifact of mana value 2 or less and wears it if it is
     * Equipment. */
    // CR 702.33, 601.2b
    @Test
    fun kickerXSteals() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand("Pilfering Skydiver")
        bob.battlefield("Skull Bracer")
        perform(Input.Cast(alice.id, card("Pilfering Skydiver")))
        var guard = 10
        while (pending != null && guard-- > 0) when (val d = pending) {
            is Decision.May -> answer(Answer.YesNo(true))
            is Decision.ChooseNumber -> answer(Answer.Number(1))
            is Decision.Announce -> answer(Answer.Number(2))
            is Decision.ManaPayment -> answer(Answer.ManaPayment(d.suggestion))
            else -> fail("Unexpected $d")
        }
        settleStack("Skull Bracer")
        expect {
            "Skull Bracer".controller shouldBe alice.id
            "Pilfering Skydiver".power shouldBe 3
        }
    }

    /** "Each player discards a card" with three players: each one chooses their own. */
    // CR 101.4, 701.9
    @Test
    fun eachPlayerDiscardsTheirOwnChoice() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Festering Vermin", "Island")
        bob.hand("Forest", "Storm Spark")
        carol.hand("Plains", "Denial")
        alice.casts("Festering Vermin")
        allPass()
        allPass()
        val asked = mutableListOf<arcanum.engine.state.PlayerId>()
        var guard = 6
        while (pending is Decision.ChooseObjects && guard-- > 0) {
            val d = decision<Decision.ChooseObjects>()
            asked += d.player
            answer(Answer.Objects(listOf(d.options.first())))
        }
        // alice's only card goes without asking (P5)
        assertEquals(setOf(bob.id, carol.id), asked.toSet())
        expect {
            bob.hand.size shouldBe 1
            carol.hand.size shouldBe 1
            alice.hand.size shouldBe 0
        }
    }
}
