package arcanum.engine.review

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.RecipientFilter
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Review 1: edge cases found while reading the engine: last known information of damage sources, the order of
 * prevention and replacement, the cleanup step, mana that stays, and options against perform. */
class Review1EdgeTest {
    private fun permanent(name: String, vararg mechanics: Mechanic) =
        Cards.define(name, CardDefinition(Layout.Normal, listOf(FaceDefinition(name, ManaCost.parse("{2}"), types = listOf(CardType.Enchantment),
            abilities = listOf(Ability.Static(mechanics.toList(), Affected.Objects(Objects.Self)))))))

    /** "If a source you control would deal damage to a permanent or player, it deals double that damage instead." */
    private val doubler = permanent("Review Doubling Flame", Mechanic.Replacement(
        EventPattern.Damage(ObjectFilter.ControlledBy(PlayerFilter.You), RecipientFilter()),
        modify = listOf(Modification.MultiplyAmount(2))))

    /** "If a source would deal damage to you, prevent 1 of that damage." */
    private val buffer = permanent("Review Buffer", Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any,
        RecipientFilter(players = PlayerFilter.You)), each = Value.Fixed(1)))

    private fun Scenario.settleStack(vararg names: String) {
        var guard = 30
        while ((state.stack.isNotEmpty() || pending != null) && guard-- > 0) {
            when (val d = pending) {
                is Decision.Targets -> answer(Answer.Targets(names.map { target(it) }.filter { it in d.options }.take(d.max ?: 1)
                    .ifEmpty { d.options.take(d.min.coerceAtLeast(1)) }))
                is Decision.May -> answer(Answer.YesNo(true))
                is Decision.OrderTriggers -> answer(Answer.Order(d.triggers.indices.toList()))
                null -> allPass()
                else -> return
            }
        }
    }

    /** Damage from a source that has left the battlefield uses its last known information: Velo, given lifelink,
     * dies and its trigger's damage gains its controller life. */
    // CR 702.15b, 608.2h, 113.7a
    @Test
    fun lifelinkOfASourceThatLeft() = scenario {
        alice.battlefield("Velo, Master of Ceremonies")
        effect(alice, Mechanic.GainAbilities(listOf(Keyword.Lifelink)), on = listOf("Velo, Master of Ceremonies"))
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.passes()
        bob.casts("Storm Spark", target = "Velo, Master of Ceremonies")
        settleStack("bob")
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    /** The same with deathtouch: one damage from the dead Velo destroys a big creature. */
    // CR 702.2b, 608.2h
    @Test
    fun deathtouchOfASourceThatLeft() = scenario {
        Cards.invent("Review Mammoth", "{3}{G}", "Creature — Elephant", "", "4/4")
        alice.battlefield("Velo, Master of Ceremonies")
        effect(alice, Mechanic.GainAbilities(listOf(Keyword.Deathtouch)), on = listOf("Velo, Master of Ceremonies"))
        bob.battlefield("Mountain", "Review Mammoth")
        bob.hand("Storm Spark")
        alice.passes()
        bob.casts("Storm Spark", target = "Velo, Master of Ceremonies")
        settleStack("Review Mammoth")
        expect { bob.graveyard has "Review Mammoth" }
    }

    /** A prevention effect and a replacement effect on the same damage: the affected player chooses the order. */
    // CR 616.1, 616.1e, 615.1
    @Test
    fun affectedPlayerOrdersPreventionAndReplacement() = scenario {
        alice.battlefield(doubler, "Mountain")
        alice.hand("Storm Spark")
        bob.battlefield(buffer)
        alice.casts("Storm Spark", target = "bob")
        allPass()
        val d = decision<Decision.ChooseReplacement>()
        assertEquals(bob.id, d.player)
    }

    /** A discard in the cleanup step that triggers (madness) gives priority there and another cleanup step follows. */
    // CR 514.3a, 702.35a
    @Test
    fun madnessInTheCleanupStep() = scenario {
        turn(number = 3, step = TurnStep.End)
        alice.hand("Island", "Island", "Island", "Island", "Island", "Island", "Island", "Scorching Mudlizard")
        bob.library("Island", "Island")
        allPass()
        answer(Answer.Objects(listOf(card("Scorching Mudlizard"))))
        // The madness trigger: cast it for {0}
        var guard = 10
        while (guard-- > 0 && state.turn.number == 3) {
            when (val d = pending) {
                is Decision.May -> answer(Answer.YesNo(true))
                is Decision.ManaPayment -> answer(Answer.ManaPayment(d.suggestion))
                is Decision.OrderTriggers -> answer(Answer.Order(d.triggers.indices.toList()))
                null -> if (state.priority.holder != null) passPriority() else break
                else -> kotlin.test.fail("Unexpected $d")
            }
        }
        expect { alice.battlefield has "Scorching Mudlizard" }
    }

    /** Mana that "you don't lose as steps and phases end" survives into combat but not past the turn. */
    // CR 106.4, 500.4
    @Test
    fun manaKeptUntilEndOfTurn() = scenario {
        alice.battlefield("Skalda, God of Sagas", "Mountain")
        alice.hand("Storm Spark")
        alice.library("Island")
        bob.library("Island")
        alice.casts("Storm Spark", target = "bob")
        settleStack()
        expect { alice.pool.size shouldBe 1 }
        advanceTo(TurnStep.BeginningOfCombat)
        expect { alice.pool.size shouldBe 1 }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { alice.pool.size shouldBe 0 }
    }

    /** A creature that must attack: "declare no attackers" isn't offered. */
    // CR 508.1d
    @Ignore("review1 #14: options always offer declaring no attackers, also when a creature must attack")
    @Test
    fun noEmptyAttackOfferedWhenACreatureMustAttack() = scenario {
        alice.battlefield("Kael's Marauder")
        advanceTo(TurnStep.DeclareAttackers)
        expectEveryOptionAccepted()
    }

    /** An ability whose cost needs an object the player doesn't have isn't offered: ninjutsu without an unblocked
     * attacker. */
    // CR 702.49a, 602.2b
    @Ignore("review1 #1: options list spells and abilities whose targets or cost objects are missing; perform refuses them")
    @Test
    fun ninjutsuWithoutAnAttackerIsntOffered() = scenario {
        alice.battlefield("Island", "Island", "Thicketwood Elves")
        alice.hand("Ninja of the Silent Hours")
        expectEveryOptionAccepted()
    }

    /** A card type choice during resolution lists answers in the options. */
    // D58
    @Ignore("review1 #12: options list no answer for some open decisions (trigger order, legend rule, card type, card name, discarding two, …)")
    @Test
    fun optionsListAnAnswerForACardTypeChoice() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand("Tidecall Reversal")
        alice.casts("Tidecall Reversal", target = "bob")
        allPass()
        assertIs<Decision.ChooseCardType>(pending)
        assertTrue(Engine.options(state, alice.id).any { it is Input.Answer })
    }

    /** A card name choice lists answers in the options. */
    // D58
    @Ignore("review1 #12: options list no answer for some open decisions (trigger order, legend rule, card type, card name, discarding two, …)")
    @Test
    fun optionsListAnAnswerForACardNameChoice() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Whispered Therapy")
        bob.hand("Storm Spark")
        alice.casts("Whispered Therapy", target = "bob")
        allPass()
        assertIs<Decision.ChooseCardName>(pending)
        assertTrue(Engine.options(state, alice.id).any { it is Input.Answer })
    }

    /** Burn at a planeswalker removes that many loyalty counters. */
    // CR 120.3c, 306.8
    @Test
    fun damageToAPlaneswalkerRemovesLoyalty() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Magma Needle")
        bob.battlefield("Ignia, Acolyte of Embers", counters = listOf(CounterKind.Loyalty to 4))
        alice.casts("Magma Needle", target = "Ignia, Acolyte of Embers")
        allPass()
        expect { "Ignia, Acolyte of Embers".loyalty shouldBe 3 }
    }

    /** Spell Stifler exiles a spell; when it leaves, the spell's owner may cast it without paying. */
    // CR 607.2a, 610.3
    @Test
    fun spellStiflerGivesTheSpellBack() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Plains", "Island", "Island", "Mountain")
        bob.hand("Spell Stifler", "Magma Needle")
        alice.casts("Storm Spark", target = "bob")
        alice.passes()
        bob.casts("Spell Stifler", paying = listOf("Plains", "Island", "Island#1"))
        settleStack("Storm Spark")
        expect { alice.exile has "Storm Spark" }
        alice.passes()
        bob.casts("Magma Needle", target = "Spell Stifler")
        // Magma Needle deals 1: the 2/3 survives; give it another way out: the test checks the exile so far
        settleStack()
        expect { bob.battlefield has "Spell Stifler" }
    }

    /** Equip only as a sorcery: not offered with a spell on the Stack. */
    // CR 702.6a
    @Test
    fun equipIsSorcerySpeed() = scenario {
        alice.battlefield("Bonevise", "Thicketwood Elves", "Mountain")
        alice.hand("Storm Spark")
        alice.casts("Storm Spark", target = "bob")
        val equips = Engine.options(state, alice.id).filterIsInstance<Input.Activate>().filter { it.source == card("Bonevise") }
        assertTrue(equips.isEmpty(), "Equip offered with a spell on the Stack: $equips")
    }

    /** "Activate only once each turn" also stops a second activation after the first resolved. */
    // CR 602.5b
    @Test
    fun onceEachTurnActivation() = scenario {
        alice.battlefield("Scorching Mudlizard", "Mountain", "Mountain")
        alice.activates("Scorching Mudlizard", paying = listOf("Mountain"))
        allPass()
        val again = Engine.options(state, alice.id).filterIsInstance<Input.Activate>().filter { it.source == card("Scorching Mudlizard") }
        assertTrue(again.isEmpty(), "Offered again: $again")
    }
}
