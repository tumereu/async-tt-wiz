package arcanum.engine.state

import arcanum.api.Designation
import arcanum.api.ObjectMark
import arcanum.engine.change.Change
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The typed statuses of objects, players and the game: one of each key, kept in key order, changed and taken back exactly
 * (D59). */
class StatusesTest {
    // D59: a status set and taken back gives back the same list, in key order whatever order they were set in
    @Test
    fun statusesKeepOneOrderAndRevertExactly() = scenario {
        alice.battlefield("Forest")
        val id = card("Forest")
        val before = state.deepCopy()
        val changes = listOf(
            Change.ObjectStatusChanged(id, null, ObjectStatus.Protector(bob.id)),
            Change.ObjectStatusChanged(id, null, ObjectStatus.ClassLevel(2)),
            Change.ObjectStatusChanged(id, ObjectStatus.ClassLevel(2), ObjectStatus.ClassLevel(3)),
        )
        changes.forEach { state.apply(it) }
        assertEquals(listOf(ObjectStatus.ClassLevel(3), ObjectStatus.Protector(bob.id)), state.objOrThrow(id).statuses)
        changes.reversed().forEach { state.revert(it) }
        assertEquals(before, state)
    }

    // D59: a change made for another state fails loudly
    @Test
    fun aStatusChangeChecksItsBeforeValue() = scenario {
        alice.battlefield("Forest")
        assertFailsWith<IllegalStateException> { state.apply(Change.ObjectStatusChanged(card("Forest"), ObjectStatus.ClassLevel(2), null)) }
        assertFailsWith<IllegalStateException> { state.apply(Change.PlayerStatusChanged(alice.id, arcanum.engine.state.PlayerStatus.Speed(1), null)) }
    }

    // CR 724–726: gaining or losing a designation is an event; a player's other statuses and the game's are not
    @Test
    fun aDesignationIsAnEvent() {
        assertTrue(Change.PlayerStatusChanged(PlayerId("a"), null, PlayerStatus.Designated(Designation.Monarch)).isEvent)
        assertFalse(Change.PlayerStatusChanged(PlayerId("a"), null, PlayerStatus.Speed(1)).isEvent)
        assertFalse(Change.GameStatusChanged(null, GameStatus.DayNight(night = true)).isEvent)
    }

    // CR 702.143a, 702.170d: an object keeps one record per mark, the latest
    @Test
    fun theLatestRecordOfAMarkCounts() = scenario {
        alice.battlefield("Forest")
        val o = state.objOrThrow(card("Forest"))
        val once = o.withStatus("marks-gained", o.gaining(MarkGained(ObjectMark.Plotted, 2)))
        val twice = once.gaining(MarkGained(ObjectMark.Plotted, 4))
        assertEquals(listOf(MarkGained(ObjectMark.Plotted, 4)), twice.records)
    }
}
