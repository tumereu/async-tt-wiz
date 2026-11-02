package arcanum.engine.combat

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.change.Changes
import arcanum.engine.change.Meaning
import arcanum.engine.decision.Decision
import arcanum.engine.decision.Share
import arcanum.engine.input.Attack
import arcanum.engine.input.Block
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.scenario.scenario
import arcanum.engine.state.EffectId
import arcanum.engine.state.EffectObject
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The combat state kept current: removal from combat (CR 506.4), blocked and unblocked (CR 509.1h, 509.3), attack and
 * block requirements against restrictions (CR 508.1d, 509.1c), and dividing damage with others' damage counted
 * (CR 702.19b). */
class CombatStateTest {
    private fun cards() {
        Cards.invent("State Bear", "{1}{G}", "Creature — Bear", "", "2/2")
        Cards.invent("State Ox", "{2}{G}", "Creature — Ox", "", "3/3")
        Cards.invent("State Mammoth", "{4}{G}", "Creature — Elephant", "", "5/5")
    }

    /** A 2/2 creature: when [event] happens, you gain 2 life. */
    private fun creature(name: String, event: EventPattern) = Cards.define(name, CardDefinition(Layout.Normal, listOf(FaceDefinition(
        name, ManaCost.parse("{1}{W}"), types = listOf(CardType.Creature), power = PrintedNumber.Fixed(2), toughness = PrintedNumber.Fixed(2),
        abilities = listOf(Ability.Triggered(Trigger.On(listOf(event)),
            Instructions(steps = listOf(Step.Do(Effect.GainLife(Players.You, Value.Fixed(2))))))),
    ))))

    /** "Whenever this creature attacks and isn't blocked, you gain 2 life." */
    private val fox get() = creature("State Slipping Fox", EventPattern.AttacksUnblocked(ObjectFilter.Is(Objects.Self)))

    /** "Whenever this creature becomes blocked, you gain 2 life." */
    private val hound get() = creature("State Lure Hound", EventPattern.BecomesBlocked(ObjectFilter.Is(Objects.Self), ObjectFilter.Any))

    /** [player] gains control of [name] by an effect made by hand (CR 613.1b). */
    private fun Scenario.gainControl(player: ScenarioPlayer, name: String) {
        val id = card(name)
        change(Changes.effect(state) { ts ->
            EffectObject(EffectId("state-control:$ts"), listOf(Mechanic.ControlledBy(Players.You)), affects = null,
                locked = listOf(id), source = null, controller = player.id, timestamp = ts, createdTurn = state.turn.number,
                duration = Duration.Indefinitely)
        })
    }

    private fun Scenario.attacking(name: String) = state.combat?.attackers.orEmpty().any { it.id == card(name) }

    // ---- Removal from combat (CR 506.4)

    /** A blocker whose controller changes leaves combat; the attacker stays blocked and, with no blocker left, deals no
     * combat damage. */
    // CR 506.4, 509.1h, 510.1c
    @Test
    fun blockerStolenLeavesTheAttackerBlocked() = scenario {
        cards()
        alice.battlefield("State Ox")
        bob.battlefield("State Bear")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("State Ox")
        allPass()
        bob.blocks("State Bear" to "State Ox")
        gainControl(alice, "State Bear")
        settle()
        assertEquals(emptyList(), state.combat!!.blockers)
        assertTrue(state.combat!!.attackers.single().blocked)
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 20 }
        assertEquals(0, state.objOrThrow(card("State Bear")).damage)
    }

    /** The removal is a change of its own, right after the event that caused it. */
    // CR 506.4
    @Test
    fun removalIsItsOwnChange() = scenario {
        cards()
        alice.battlefield("State Ox")
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("State Ox")
        alice.passes()
        bob.casts("Storm Spark", target = "State Ox")
        allPass()
        expect { alice.graveyard has "State Ox" }
        val i = applied.indexOfLast { it is Change.ZoneChange }
        val next = applied[i + 1]
        assertIs<Change.Combat>(next)
        assertTrue(Meaning.RemovedFromCombat in next.meanings)
        assertEquals(emptyList(), state.combat!!.attackers)
    }

    /** Removal is for good: a creature whose control changes and changes back isn't attacking again. */
    // CR 506.4
    @Test
    fun controlBackDoesntReturnToCombat() = scenario {
        cards()
        alice.battlefield("State Ox")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("State Ox")
        val earlier = state.effects.keys.toSet()
        gainControl(bob, "State Ox")
        settle()
        change(Change.EffectRemoved(state.effects.entries.single { it.key !in earlier }.value))
        settle()
        expect { "State Ox".controller shouldBe alice.id }
        assertTrue(!attacking("State Ox"))
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 20 }
    }

    /** An attacker whose blocker was removed stays blocked; a blocker whose attacker left blocks nothing and deals no
     * damage. */
    // CR 506.4, 510.1d
    @Test
    fun blockerWithItsAttackerGoneDealsNoDamage() = scenario {
        cards()
        alice.battlefield("State Bear", "Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("State Mammoth")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("State Bear")
        allPass()
        bob.blocks("State Mammoth" to "State Bear")
        alice.casts("Storm Spark", target = "State Bear")
        allPass()
        assertEquals(emptyList(), state.combat!!.blockers.single().attackers)
        advanceTo(TurnStep.EndOfCombat)
        expect {
            alice.life shouldBe 20
            bob.life shouldBe 20
        }
    }

    // ---- Blocked and unblocked (CR 509.1h, 509.3)

    /** With two defending players, "isn't blocked" waits until both have declared, and triggers once. */
    // CR 509.1h, 509.3g
    @Test
    fun unblockedWaitsForEveryDefender() = scenario(players = 3) {
        cards()
        alice.battlefield(fox, "State Bear")
        carol.battlefield("State Ox")
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(
            Attack(card("State Slipping Fox"), TargetRef.Player(bob.id)),
            Attack(card("State Bear"), TargetRef.Player(carol.id)),
        )))
        allPass()
        assertTrue(state.triggered.isEmpty())
        carol.blocks("State Ox" to "State Bear")
        assertEquals(1, state.triggered.size + state.stack.size)
        allPass()
        expect { alice.life shouldBe 22 }
    }

    /** "Becomes blocked" triggers once however many creatures block it. */
    // CR 509.3c
    @Test
    fun becomesBlockedOnceByTwo() = scenario {
        cards()
        alice.battlefield(hound)
        bob.battlefield("State Bear", "State Ox")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("State Lure Hound")
        allPass()
        bob.blocks("State Bear" to "State Lure Hound", "State Ox" to "State Lure Hound")
        allPass()
        expect { alice.life shouldBe 22 }
    }

    /** With nothing able to block, the attacker is unblocked as the step begins and "isn't blocked" triggers. */
    // CR 509.1h, 509.3g
    @Test
    fun unblockedWhenNothingCanBlock() = scenario {
        alice.battlefield(fox)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("State Slipping Fox")
        allPass()
        allPass()
        expect { alice.life shouldBe 22 }
        assertTrue(state.combat!!.blocksDecided)
    }

    // ---- Requirements and restrictions (CR 508.1d, 509.1c)

    /** Two creatures that must attack under "no more than one creature can attack": the options offer each alone,
     * never none and never both. */
    // CR 508.1d
    @Test
    fun mustAttackOptionsUnderAMaximum() = scenario {
        alice.battlefield("Kael's Marauder", "Kael's Marauder")
        effect(bob, Mechanic.MaximumAttackers(1), affects = Affected.Objects(Objects.Each(ObjectFilter.Type(CardType.Creature))),
            duration = Duration.Indefinitely)
        advanceTo(TurnStep.DeclareAttackers)
        val declarations = Engine.options(state, alice.id).filterIsInstance<Input.DeclareAttackers>()
        assertEquals(setOf(card("Kael's Marauder", 0), card("Kael's Marauder", 1)), declarations.map { it.attacks.single().attacker }.toSet())
        assertFailsWith<IllegalArgumentException> { perform(Input.DeclareAttackers(alice.id, emptyList())) }
    }

    /** A creature that must attack is in every offered declaration; another creature may join it. */
    // CR 508.1d
    @Test
    fun mustAttackWithAnotherOptional() = scenario {
        cards()
        alice.battlefield("Kael's Marauder", "State Bear")
        advanceTo(TurnStep.DeclareAttackers)
        val declarations = Engine.options(state, alice.id).filterIsInstance<Input.DeclareAttackers>().map { d -> d.attacks.map { it.attacker }.toSet() }
        assertEquals(setOf(setOf(card("Kael's Marauder")), setOf(card("Kael's Marauder"), card("State Bear"))), declarations.toSet())
        assertFailsWith<IllegalArgumentException> { alice.attacks("State Bear") }
        alice.attacks("Kael's Marauder", "State Bear")
    }

    /** "Attacks [a player] each combat if able" is obeyed only by attacking that player: with two opponents, attacking
     * the other is illegal. */
    // CR 508.1d
    @Test
    fun mustAttackAChosenPlayer() = scenario(players = 3) {
        cards()
        alice.battlefield("State Bear")
        effect(carol, Mechanic.MustAttack(Recipients(players = Players.You)), affects = Affected.Objects(Objects.Bound("it")),
            names = mapOf("it" to "State Bear"), duration = Duration.Indefinitely)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> {
            perform(Input.DeclareAttackers(alice.id, listOf(Attack(card("State Bear"), TargetRef.Player(bob.id)))))
        }
        val offered = Engine.options(state, alice.id).filterIsInstance<Input.DeclareAttackers>()
        assertEquals(listOf(listOf(Attack(card("State Bear"), TargetRef.Player(carol.id)))), offered.map { it.attacks })
        perform(offered.single())
    }

    /** A creature that must block, against an attacker with menace: it can obey only with a second blocker, so blocking
     * with both is required. */
    // CR 509.1c, 702.111b
    @Test
    fun mustBlockAMenaceAttackerWithCompany() = scenario {
        alice.battlefield("Unyielding Dead")
        bob.battlefield("Thicketwood Elves", "Elvish Seer")
        effect(alice, Mechanic.MustBlock(ObjectFilter.Any), affects = Affected.Objects(Objects.Bound("it")),
            names = mapOf("it" to "Thicketwood Elves"), duration = Duration.Indefinitely)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Unyielding Dead")
        allPass()
        assertFailsWith<IllegalArgumentException> { bob.blocks() }
        assertFailsWith<IllegalArgumentException> { bob.blocks("Thicketwood Elves" to "Unyielding Dead") }
        val offered = Engine.options(state, bob.id).filterIsInstance<Input.DeclareBlockers>()
        assertEquals(listOf(setOf(card("Thicketwood Elves"), card("Elvish Seer"))), offered.map { d -> d.blocks.map { it.blocker }.toSet() })
        bob.blocks("Thicketwood Elves" to "Unyielding Dead", "Elvish Seer" to "Unyielding Dead")
    }

    /** A creature that must block but can't block (it's tapped): no blockers is legal and offered. */
    // CR 509.1a, 509.1c
    @Test
    fun mustBlockButTapped() = scenario {
        cards()
        alice.battlefield("State Bear")
        bob.battlefield("Thicketwood Elves", tapped = true)
        bob.battlefield("Elvish Seer")
        effect(alice, Mechanic.MustBlock(ObjectFilter.Any), affects = Affected.Objects(Objects.Bound("it")),
            names = mapOf("it" to "Thicketwood Elves"), duration = Duration.Indefinitely)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("State Bear")
        allPass()
        assertTrue(Engine.options(state, bob.id).any { it is Input.DeclareBlockers && it.blocks.isEmpty() })
        bob.blocks()
        allPass()
        expect { bob.life shouldBe 18 }
    }

    // ---- Dividing damage (CR 702.19b)

    /** Lethal damage for a trampler counts damage another attacker assigns to the same blocker in the step. */
    // CR 702.19b
    @Test
    fun lethalCountsOtherAttackersDamage() = scenario {
        cards()
        alice.battlefield("State Ox", "Mirebloat, the Undermuck")
        bob.battlefield("State Mammoth")
        effect(bob, Mechanic.CanBlockAdditional(1), affects = Affected.Objects(Objects.Bound("it")),
            names = mapOf("it" to "State Mammoth"), duration = Duration.Indefinitely)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("State Ox", "Mirebloat, the Undermuck")
        allPass()
        perform(Input.DeclareBlockers(bob.id, listOf(
            Block(card("State Mammoth"), card("State Ox")),
            Block(card("State Mammoth"), card("Mirebloat, the Undermuck")),
        )))
        allPass()
        // The Mammoth's own division comes after; the trampler's first
        var d = decision<Decision.Divide>()
        if (d.source != card("Mirebloat, the Undermuck")) {
            divideAsSuggested()
            d = decision()
        }
        assertEquals(listOf(Share(target("State Mammoth"), 2), Share(TargetRef.Player(bob.id), 1)), d.suggested)
        divideAsSuggested()
        while (pending is Decision.Divide) divideAsSuggested()
        expect {
            bob.graveyard has "State Mammoth"
            bob.life shouldBe 19
        }
    }
}
