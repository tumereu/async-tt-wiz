package org.asyncttwiz.protocol.game

import arcanum.engine.Engine
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * I-D54 phase 2, the owner's undo rule on events: an event can be undone when no later event changed or relied on what
 * it changed; an engine input relied on the whole engine state; refusing is the safe side.
 */
class UndoRuleTest {
    private val games = EngineGames(21)
    private val model = games.model()
    private var parent = "root"
    private var n = 0

    /** Plays on until the active player may play a land with nothing pending and two permanents out. */
    private fun untilALandCanBePlayed(): Input.PlayLand {
        while (true) {
            val s = model.engine
            if (s.pending == null && s.priority.holder == s.turn.active && s.battlefield.size >= 2)
                Engine.options(s, s.turn.active).filterIsInstance<Input.PlayLand>().firstOrNull()?.let { return it }
            parent = games.next(model, parent).id
        }
    }

    private fun event(build: EventBuilder.() -> Unit): GameEvent =
        EventBuilder(model, "u${++n}", parent, "alice").apply(build).build().also { parent = it.id }

    private fun place(obj: ObjectId, x: Int) = TableChange.Placed(obj, model.table.placements[obj], Placement(x, 9))

    @Test
    fun aCardPlacedLaterBlocksUndoingTheEventThatPutItThere() {
        val land = untilALandCanBePlayed()
        val play = event { perform(land) }
        val entered = model.engine.battlefield.last()
        val placing = event { table(place(entered, 0)) }
        assertSame(placing, UndoRule.blocking(play, listOf(placing)))
        val snapshot = model.copy()
        assertFailsWith<IllegalStateException> { UndoRule.undo(model, play, listOf(placing)) }
        assertTrue(model.sameAs(snapshot), "a refused undo changes nothing")
    }

    @Test
    fun tableEventsOnOtherThingsDoNotBlockAnEngineEvent() {
        val land = untilALandCanBePlayed()
        val before = model.copy()
        val play = event { perform(land) }
        val touched = Concerns.of(play).writes
        val elsewhere = model.engine.battlefield.filterIsInstance<ObjectId.Card>().first { StateKey.Presence(it.card) !in touched }
        val moving = event { table(place(elsewhere, 5)) }
        val setting = event { table(TableChange.Settings(games.bob, model.table.settingsOf(games.bob), PlaySettings(autotap = false))) }
        assertNull(UndoRule.blocking(play, listOf(moving, setting)))
        UndoRule.undo(model, play, listOf(moving, setting))
        // The land is back in hand; the later table events keep their values
        val expected = before.also { it.apply(moving.copy(parentId = "x")); it.apply(setting) }
        assertTrue(model.sameAs(expected))
    }

    @Test
    fun aLaterEngineInputBlocksUndoingAnyEarlierEngineChange() {
        val land = untilALandCanBePlayed()
        val play = event { perform(land) }
        val pass = event { perform(Input.PassPriority(model.engine.priority.holder!!)) }
        assertSame(pass, UndoRule.blocking(play, listOf(pass)))
        // ... but a table-only event before it is free to go: no engine input reads the table
        val obj = model.engine.battlefield.last()
        val placing = event { table(place(obj, 1)) }
        val any = model.engine.players.flatMap { Engine.options(model.engine, it.id) }.first { it !is Input.Concede }
        val pass2 = event { perform(any) }
        assertNull(UndoRule.blocking(placing, listOf(pass2)))
    }

    @Test
    fun tableEventsBlockEachOtherOnlyOnTheSameThing() {
        untilALandCanBePlayed()
        val obj = model.engine.battlefield.first()
        val other = model.engine.battlefield.last()
        val first = event { table(place(obj, 1)) }
        val unrelated = event { table(place(other, 2)) }
        val again = event { table(place(obj, 3)) }
        assertNull(UndoRule.blocking(first, listOf(unrelated)))
        assertEquals(again, UndoRule.blocking(first, listOf(unrelated, again)))
        // The latest event can always be undone
        assertNull(UndoRule.blocking(again, emptyList()))
        val alice = PlayerId("alice")
        val settings = event { table(TableChange.Settings(alice, model.table.settingsOf(alice), PlaySettings(fullControl = true))) }
        val result = event { table(TableChange.Suggestion(null, ResultSuggestion(alice, arcanum.engine.setup.GameResult.Draw))) }
        assertNull(UndoRule.blocking(settings, listOf(result)))
    }
}
