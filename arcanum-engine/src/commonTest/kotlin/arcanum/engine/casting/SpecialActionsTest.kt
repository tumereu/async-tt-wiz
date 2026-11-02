package arcanum.engine.casting

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Special actions (CR 116.2), playing lands with a permission (CR 305.1, D41), and spells mana can't pay for. */
class SpecialActionsTest {
    private fun Scenario.specialAction(name: String): Input.Activate {
        val id = card(name)
        val action = view()[id]!!.characteristics!!.abilities.first { it.ability is Ability.SpecialAction }
        return Input.Activate(state.priority.holder ?: alice.id, id, action.origin)
    }

    // CR 702.62a, 116.2f: suspend: pay its cost and exile it with time counters, without using the Stack
    @Test
    fun suspend() = scenario {
        alice.hand("Lotus Bud")
        perform(specialAction("Lotus Bud"))
        expect {
            alice.exile has "Lotus Bud"
            "Lotus Bud".counters(CounterKind.Time) shouldBe 3
            stack.isEmpty()
        }
        // CR 117.3c: the player keeps priority
        assertEquals(alice.id, state.priority.holder)
    }

    // CR 702.62a: "if you could begin to cast this card": an artifact only at sorcery speed
    @Test
    fun suspendOnlyWhenItCouldBeCast() = scenario {
        bob.hand("Lotus Bud")
        alice.passes()
        val action = specialAction("Lotus Bud").copy(player = bob.id)
        assertFailsWith<IllegalArgumentException> { perform(action) }
    }

    // CR 702.37e, 116.2b: turning a face-down permanent face up for its morph cost, any time with priority
    @Test
    fun turnFaceUp() = scenario {
        bob.battlefield("Barkwood Pathfinders", faceDown = true)
        bob.battlefield("Forest")
        alice.passes()
        val before = state.objOrThrow(card("Barkwood Pathfinders")).timestamp
        val turnUp = arcanum.engine.Engine.options(state, bob.id).filterIsInstance<Input.Activate>()
            .single { it.source == card("Barkwood Pathfinders") }
        perform(turnUp)
        answer(Answer.ManaPayment(assertIs<Decision.ManaPayment>(pending).suggestion))
        expect {
            "Barkwood Pathfinders".power shouldBe 1
            "Forest".tapped shouldBe true
            stack.isEmpty()
        }
        assertTrue(!state.objOrThrow(card("Barkwood Pathfinders")).faceDown)
        // CR 613.7f: turning face up gives it a new timestamp
        assertTrue(state.objOrThrow(card("Barkwood Pathfinders")).timestamp > before)
    }

    // CR 702.37e: only a face-down permanent turns face up
    @Test
    fun faceUpCantTurnFaceUp() = scenario {
        alice.battlefield("Barkwood Pathfinders", "Forest")
        assertFailsWith<IllegalArgumentException> { perform(specialAction("Barkwood Pathfinders")) }
    }

    // CR 305.1, D41: a permission to play lands from the graveyard; it uses the land play
    @Test
    fun playALandFromTheGraveyard() = scenario {
        alice.graveyard("Forest", "Island")
        effect(alice, Mechanic.MayPlay(Zone.Graveyard, PlayKind.Play, holder = PlayerFilter.You),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.Type(CardType.Land), ObjectFilter.OwnedBy(PlayerFilter.You))))),
            duration = Duration.EndOfTurn)
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
        assertFailsWith<IllegalArgumentException> { alice.plays("Island") }
    }

    // CR 305.1: without the permission, not from the graveyard
    @Test
    fun noLandFromTheGraveyardWithoutPermission() = scenario {
        alice.graveyard("Forest")
        assertFailsWith<IllegalArgumentException> { alice.plays("Forest") }
    }

    // "You can't spend mana to cast this spell": convoke and delve pay all of it
    @Test
    fun cantSpendManaPaidByConvokeAndDelve() = scenario {
        alice.battlefield("Canopy Browser", "Persistence", "Forest")
        alice.graveyard("Storm Spark", "Denial", "Mountain", "Swamp", "Plains")
        alice.hand("Grommak, Risen Charnelhost")
        perform(Input.Cast(alice.id, card("Grommak, Risen Charnelhost")))
        val d = assertIs<Decision.ManaPayment>(pending)
        // The suggested payment uses only the other ways (convoke, delve), never mana
        assertTrue(d.suggestion.isNotEmpty() && d.suggestion.all { it is ManaUse.Special })
        assertTrue(d.options.none { it is ManaUse.Ability || it is ManaUse.Pool })
        val uses = listOf("Canopy Browser", "Persistence", "Storm Spark", "Denial", "Mountain", "Swamp", "Plains").map { name ->
            d.options.filterIsInstance<ManaUse.Special>().first { it.objects == listOf(card(name)) }
        }
        answer(Answer.ManaPayment(uses))
        expect {
            stack.names shouldBe listOf("Grommak, Risen Charnelhost")
            "Forest".tapped shouldBe false
            alice.exile.size shouldBe 5
        }
    }

    // CR 702.62a, 122.1: suspend's upkeep trigger removes a time counter from the card in exile
    @Test
    fun suspendCountsDownInExile() = scenario {
        alice.hand("Lotus Bud")
        perform(specialAction("Lotus Bud"))
        beginStep(arcanum.api.TurnStep.Upkeep, active = alice)
        resolve()
        expect {
            alice.exile has "Lotus Bud"
            "Lotus Bud".counters(CounterKind.Time) shouldBe 2
        }
    }

    // CR 702.62a, 400.7d: a creature cast with the last time counter removed keeps haste as the permanent it becomes
    @Test
    fun suspendedCreatureHasHaste() = scenario {
        alice.battlefield("Swamp")
        put(alice, listOf("Colossal Rumblebeast"), Zone.Exile, counters = listOf(CounterKind.Time to 1))
        beginStep(TurnStep.Upkeep, active = alice)
        resolve()
        resolve()
        answer(Answer.YesNo(true))
        resolve()
        expect {
            alice.battlefield has "Colossal Rumblebeast"
            "Colossal Rumblebeast" hasKeyword arcanum.api.Keyword.Haste
        }
    }
}
