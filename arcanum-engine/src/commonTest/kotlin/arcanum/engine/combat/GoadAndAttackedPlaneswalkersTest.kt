package arcanum.engine.combat

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.Progress
import arcanum.engine.action.Act
import arcanum.engine.action.Actions
import arcanum.engine.action.Asker
import arcanum.engine.change.Change
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Goaded creatures' attack requirements (CR 701.15) and attacked planeswalkers removed from combat (CR 506.4). */
class GoadAndAttackedPlaneswalkersTest {
    private val pw = "Ignia, Acolyte of Embers"

    /** [by] goads [name] (CR 701.15a), made by hand as an effect would. */
    private fun Scenario.goad(name: String, by: PlayerId) {
        val o = state.objOrThrow(card(name))
        change(Change.ObjectUpdated(o, o.copy(goadedBy = o.goadedBy + by)))
    }

    private fun Scenario.attack(name: String, target: TargetRef) =
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(name), target))))

    /** A goaded creature must attack: declaring no attackers is refused (CR 508.1d); attacking the goader is allowed
     * when no other player can be attacked. */
    // CR 701.15b, 508.1d
    @Test
    fun aGoadedCreatureAttacksIfAble() = scenario {
        alice.battlefield("Thicketwood Elves")
        goad("Thicketwood Elves", bob.id)
        advanceTo(TurnStep.BeginningOfCombat)
        allPass()
        assertFailsWith<IllegalArgumentException> { perform(Input.DeclareAttackers(alice.id, emptyList())) }
        assertTrue(Engine.options(state, alice.id).none { it is Input.DeclareAttackers && it.attacks.isEmpty() })
        attack("Thicketwood Elves", TargetRef.Player(bob.id))
        expect { state.combat!!.attackers.size shouldBe 1 }
    }

    /** With three players, a creature bob goaded must attack carol, not bob (CR 701.15b). */
    // CR 701.15b, 508.1d
    @Test
    fun aGoadedCreatureAttacksAnotherPlayerIfAble() = scenario(players = 3) {
        alice.battlefield("Thicketwood Elves")
        goad("Thicketwood Elves", bob.id)
        advanceTo(TurnStep.BeginningOfCombat)
        allPass()
        assertFailsWith<IllegalArgumentException> { attack("Thicketwood Elves", TargetRef.Player(bob.id)) }
        attack("Thicketwood Elves", TargetRef.Player(carol.id))
        expect { state.combat!!.attackers.single().target shouldBe TargetRef.Player(carol.id) }
    }

    /** Goading lasts until the goading player's next turn (CR 701.15a). */
    // CR 701.15a
    @Test
    fun goadingEndsAsTheGoadersTurnBegins() = scenario {
        alice.battlefield("Thicketwood Elves")
        goad("Thicketwood Elves", bob.id)
        advanceTo(TurnStep.DeclareAttackers)
        attack("Thicketwood Elves", TargetRef.Player(bob.id))
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertEquals(emptyList(), state.objOrThrow(card("Thicketwood Elves")).goadedBy)
    }

    /** The attacked planeswalker leaves the battlefield: it is removed from combat; the creature goes on attacking
     * nothing and deals no combat damage (CR 506.4c). */
    // CR 506.4, 506.4c
    @Test
    fun anAttackedPlaneswalkerThatLeavesIsRemovedFromCombat() = scenario {
        alice.battlefield("Thicketwood Elves")
        bob.battlefield(pw, counters = listOf(arcanum.api.CounterKind.Loyalty to 4))
        advanceTo(TurnStep.DeclareAttackers)
        attack("Thicketwood Elves", TargetRef.Object(card(pw)))
        drive { r ->
            Actions.moveTo(Act(r, Asker.NONE), null, listOf(card(pw)), Zone.Graveyard, emptyList())
            Progress.Done
        }
        val attacker = state.combat!!.attackers.single()
        assertTrue(attacker.targetRemoved, "the planeswalker was removed from combat")
        advanceTo(TurnStep.EndOfCombat)
        expect {
            bob.life shouldBe 20
            bob.graveyard has pw
        }
    }

    /** It stays attacked while it is still a planeswalker. */
    // CR 506.4
    @Test
    fun anAttackedPlaneswalkerStaysInCombat() = scenario {
        alice.battlefield("Thicketwood Elves")
        bob.battlefield(pw, counters = listOf(arcanum.api.CounterKind.Loyalty to 4))
        advanceTo(TurnStep.DeclareAttackers)
        attack("Thicketwood Elves", TargetRef.Object(card(pw)))
        advanceTo(TurnStep.CombatDamage)
        assertTrue(state.combat!!.attackers.none { it.targetRemoved })
    }
}
