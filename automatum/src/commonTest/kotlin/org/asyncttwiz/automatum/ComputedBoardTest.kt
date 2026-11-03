package org.asyncttwiz.automatum

import arcanum.api.Affected
import arcanum.api.Duration
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Objects
import arcanum.api.Value
import arcanum.engine.state.CardId
import arcanum.engine.state.EffectId
import arcanum.engine.state.EffectObject
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.EngineRecord
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.StackEffect
import org.asyncttwiz.protocol.TypedCounter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** I1 (integration spec §3): the engine's view of the board while Automatum is on, and Inspect's explanation. */
class ComputedBoardTest {
    private val table = AtwTable("computed")
    private val hierophant = table.put(table.alice, PlayZone.BATTLEFIELD, "Elvish Hierophant").single()
    private val seer = table.put(table.alice, PlayZone.BATTLEFIELD, "Elvish Seer").single()
    private val druid = table.put(table.bob, PlayZone.BATTLEFIELD, "Lineage Druid").single()
    private val spark = table.put(table.alice, PlayZone.GRAVEYARD, "Storm Spark").single()

    private fun effect(id: String, mechanic: Mechanic, on: String, timestamp: Long, duration: Duration = Duration.EndOfTurn): EngineRecord =
        EngineCodec.effectRecord(EffectObject(EffectId(id), listOf(mechanic), Affected.Objects(Objects.Self),
            locked = listOf(ObjectId.Card(CardId(on), 0)), source = ObjectId.Card(CardId(spark), 0),
            controller = PlayerId(table.alice), timestamp = timestamp, createdTurn = 1, duration = duration))

    private fun board(vararg records: EngineRecord, edit: (PlayState) -> PlayState = { it }): PlayState = table.state().let { s ->
        edit(s.copy(board = s.board.copy(engineRecords = records.toList(), cards = s.board.cards.map { card ->
            when (card.id) {
                hierophant -> card.copy(timestamp = 1)
                seer -> card.copy(timestamp = 2, typedCounters = listOf(TypedCounter("+1/+1", 2, 3)))
                druid -> card.copy(timestamp = 5)
                else -> card
            }
        })))
    }

    // CR 613.4b–c: the corner's P/T is the engine's: printed 1/1, +1/+1 from an anthem, two +1/+1 counters and an
    // until-end-of-turn +3/+3, in timestamp order
    @Test
    fun computedPowerToughnessAndItsExplanation() {
        val play = board(effect("pump", Mechanic.ModifyPowerToughness(Value.Fixed(3), Value.Fixed(3)), seer, 4))
        val computed = ComputedBoard.of(play, table.definitions, table.alice)
        val card = computed.cards.getValue(seer)
        assertEquals("7/7", card.corner)
        assertEquals(1, card.trend)
        assertTrue(card.untilEndOfTurn)
        assertEquals(listOf("+1/+1 ×2"), card.counters)
        assertEquals(listOf("7/7: 1/1 printed, +1/+1 Elvish Hierophant, +2/+2 counters, +3/+3 Storm Spark until end of turn"),
            computed.explain(seer, table.alice))
        // The anthem pumps only its controller's other Elves (CR 613.4c); Bob's Elf is printed
        assertEquals("1/1", computed.cards.getValue(druid).corner)
        assertEquals(0, computed.cards.getValue(druid).trend)
        assertEquals(listOf("1/1: 1/1 printed"), computed.explain(druid, table.alice))
        // Graveyard cards aren't shown computed
        assertTrue(spark !in computed.cards)
    }

    // CR 613.1f: a granted keyword is a mark of its own; an effect lowering P/T reads as down
    @Test
    fun grantedKeywordAndLoweredPowerToughness() {
        val play = board(effect("wings", Mechanic.GainAbilities(listOf(Keyword.Flying)), druid, 6),
            effect("shrink", Mechanic.ModifyPowerToughness(Value.Fixed(-1), Value.Fixed(0)), druid, 7, Duration.Indefinitely))
        val computed = ComputedBoard.of(play, table.definitions, table.bob)
        val card = computed.cards.getValue(druid)
        assertEquals("0/1", card.corner)
        assertEquals(-1, card.trend)
        assertEquals(false, card.untilEndOfTurn)
        assertEquals(listOf("Flying"), card.marks)
        assertEquals(listOf("0/1: 1/1 printed, −1/+0 Storm Spark", "Flying — Storm Spark until end of turn"),
            computed.explain(druid, table.bob))
    }

    // §5: a board the engine can't take over (a hand-made Stack entry) computes nothing; every card stays manual
    @Test
    fun unsupportedBoardFallsBackToManual() {
        val play = board { s ->
            val stack = s.board.containers.single { it.zone == PlayZone.STACK }
            val entry = "manual-entry"
            s.copy(board = s.board.copy(stackEffects = listOf(StackEffect(entry, druid)),
                containers = s.board.containers.map { if (it.id == stack.id) it.copy(cards = listOf(entry)) else it }))
        }
        val computed = ComputedBoard.of(play, table.definitions, table.alice)
        assertTrue(computed.cards.isEmpty())
        assertTrue(computed.unsupported.single().contains("manual entry"))
        assertEquals(emptyList(), computed.explain(seer, table.alice))
    }

    // I-D21: a card without a definition plays with its printed values, its rules text one manual ability
    @Test
    fun manualAbilitiesAreCounted() {
        val play = board()
        val withoutSpark = table.definitions.filterValues { d -> d.faces.none { it.name == "Storm Spark" } }
        val computed = ComputedBoard.of(play, withoutSpark, table.alice)
        assertEquals(1, computed.manualAbilities)
        assertEquals(listOf("Storm Spark" to 1), computed.manualCards)
    }
}
