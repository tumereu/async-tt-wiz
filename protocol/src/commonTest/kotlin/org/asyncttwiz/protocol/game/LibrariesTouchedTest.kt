package org.asyncttwiz.protocol.game

import arcanum.engine.change.Change
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** HIS-235, HIS-236 (owner 2026-11-19, playtest N4): which libraries the events an undo takes back touched. */
class LibrariesTouchedTest {
    private val games = EngineGames(21)
    private val model = games.model()
    private var parent = "root"

    // HIS-235: every event that changes a library's cards (a draw, a mill, a search) names that library's owner; an event that
    // touches no library names none
    @Test
    fun anEventThatChangesALibraryNamesItsOwner() {
        var drawn = 0
        var untouched = 0
        repeat(400) {
            if (games.over(model)) return@repeat
            val before = model.engine.players.associate { it.id to it.library.toList() }
            val event = games.next(model, parent).also { parent = it.id }
            val changed = model.engine.players.filter { it.library.toList() != before[it.id] }.map { it.id }.toSet()
            val touched = Rewind.librariesTouched(listOf(event))
            assertTrue(touched.containsAll(changed), "event ${event.id} changed the libraries of $changed, touched $touched")
            if (changed.isNotEmpty()) drawn++
            if (touched.isEmpty()) untouched++
        }
        assertTrue(drawn > 0, "some events drew")
        assertTrue(untouched > 0, "some events touched no library")
    }

    // HIS-235: a new line's own reshuffle (a step without an input) shows no card: no touch
    @Test
    fun aNewLinesReshuffleIsNoTouch() {
        val reshuffle = GameEvent("e1", "root", games.alice.value, EnginePart(0, listOf(EngineStep(null,
            listOf(Change.Shuffle(games.alice, emptyList(), emptyList()), Change.Shuffle(games.bob, emptyList(), emptyList()))))))
        assertEquals(emptySet(), Rewind.librariesTouched(listOf(reshuffle)))
        val searched = reshuffle.copy(engine = EnginePart(0, listOf(EngineStep(arcanum.engine.input.Input.PassPriority(games.bob),
            listOf(Change.Shuffle(games.bob, emptyList(), emptyList()))))))
        assertEquals(setOf(games.bob), Rewind.librariesTouched(listOf(reshuffle, searched)))
    }
}
