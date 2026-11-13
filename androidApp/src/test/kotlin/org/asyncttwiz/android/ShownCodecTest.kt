package org.asyncttwiz.android

import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.GameDefinitions
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.After
import org.junit.Test
import java.util.IdentityHashMap
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Web client step 4c: every board a game publishes ([EngineGame.Shown], an own action's early board too, with its notes)
 * crosses the engine boundary as a message ([ShownEncoder]) and comes out on the page ([ShownDecoder]) the same: the same
 * board, panels, line, transition with its motion steps and undo, and the same engine state, which the page keeps as its
 * own copy advanced by events. Played through the seeded whole games of two devices ([EngineTwoDeviceTest]: live, async
 * and mixed play with branches, catch-ups, undo, undo requests and results), each player's boards through their own
 * channel, as a page would get them.
 */
class ShownCodecTest {
    private class Channel(shown: EngineGame.Shown) {
        private val engineCodec = EngineJson(GameDefinitions.of(shown.engine))
        val encoder = ShownEncoder(shown.board.gameId, engineCodec)
        val decoder = ShownDecoder(shown.board.gameId, EngineJson(EngineJson.decodeDefinitions(engineCodec.encodeDefinitions())))
        var last = 0L
        /** The page's object for each engine side's board, engine view and transition: kept alike. */
        val boards = IdentityHashMap<Any, Any>()
    }

    private class Totals {
        var messages = 0; var chars = 0L; var fresh = 0; var loads = 0; var steps = 0; var maxChars = 0
        val failures = mutableListOf<String>()
        override fun toString() = "$messages boards, $fresh sent whole, $loads snapshots, $steps motion steps; " +
            "${if (messages == 0) 0 else chars / messages} chars a board on average, at most $maxChars"
    }

    @After fun stop() { EngineShownProbe.all = null }

    private fun watched(play: () -> Unit): Totals {
        val totals = Totals()
        val channels = HashMap<String, Channel>()
        EngineShownProbe.all = { me, shown, notes ->
            try {
                var channel = channels[me]
                // A new game on this seat (its boards' numbering starts again): a new page
                if (channel == null || shown.version <= channel.last) {
                    channel?.let { totals.loads += it.encoder.shadow.loads; totals.fresh += it.encoder.fresh }
                    channel = Channel(shown).also { channels[me] = it }
                }
                channel.last = shown.version
                val text = channel.encoder.encode(shown, notes)
                totals.messages++; totals.chars += text.length; totals.maxChars = maxOf(totals.maxChars, text.length)
                totals.steps += shown.transition?.steps?.size ?: 0
                val decoded = channel.decoder.decode(text)
                same(shown, decoded.shown, channel, "$me v${shown.version}")
                assertEquals(notes, decoded.notes, "$me v${shown.version}: the notes")
            } catch (problem: Throwable) {
                if (totals.failures.size < 5) totals.failures += "$me v${shown.version}: ${problem.message}\n" +
                    problem.stackTraceToString().lines().take(8).joinToString("\n")
            }
        }
        play()
        channels.values.forEach { totals.loads += it.encoder.shadow.loads; totals.fresh += it.encoder.fresh }
        if (totals.failures.isNotEmpty()) fail("Boards changed crossing the boundary ($totals):\n${totals.failures.joinToString("\n\n")}")
        println("Shown codec: $totals")
        return totals
    }

    private fun same(a: EngineGame.Shown, b: EngineGame.Shown, channel: Channel, at: String) {
        assertEquals(a.version, b.version)
        assertEquals(a.status, b.status, "$at: status")
        assertEquals(listOf(a.locked, a.behind, a.drafting, a.needsReload, a.pausing, a.takingBack, a.olderComplete, a.branchStop,
            a.ownActionShown, a.ownStepHeld), listOf(b.locked, b.behind, b.drafting, b.needsReload, b.pausing, b.takingBack,
            b.olderComplete, b.branchStop, b.ownActionShown, b.ownStepHeld), "$at: flags")
        assertEquals(listOf(a.settings, a.opponent, a.tip, a.localHead, a.request, a.indicator, a.undoTarget, a.reveal, a.show,
            a.showDone, a.combatHold, a.opponentAt, a.moments), listOf(b.settings, b.opponent, b.tip, b.localHead, b.request, b.indicator,
            b.undoTarget, b.reveal, b.show, b.showDone, b.combatHold, b.opponentAt, b.moments), "$at: the panels' values")
        assertEquals(a.line, b.line, "$at: line")
        assertEquals(a.held, b.held, "$at: held")
        assertEquals(a.older, b.older, "$at: older")
        assertEquals(a.takeBack?.let { it.by to it.undo }, b.takeBack?.let { it.by to it.undo }, "$at: take-back")
        assertEquals(a.turnChange?.let { listOf(it.serial, it.player, it.event, it.at, it.forward) },
            b.turnChange?.let { listOf(it.serial, it.player, it.event, it.at, it.forward) }, "$at: turn change")
        assertTrue(a.engine == b.engine, "$at: the engine state")
        board(a.board, b.board, channel, "$at board")
        val t = a.transition
        val u = b.transition
        assertEquals(t == null, u == null, "$at: a transition")
        if (t != null && u != null) {
            kept(t, u, channel, "$at transition")
            assertEquals(listOf(t.serial, t.cursor, t.jump, t.changes, t.shuffled, t.forward, t.backward),
                listOf(u.serial, u.cursor, u.jump, u.changes, u.shuffled, u.forward, u.backward), "$at: transition")
            assertEquals(t.steps.size, u.steps.size, "$at: motion steps")
            t.steps.zip(u.steps).forEachIndexed { i, (x, y) -> board(x, y, channel, "$at step $i") }
            assertEquals(t.undone?.let { listOf(it.by, it.event, it.branch) }, u.undone?.let { listOf(it.by, it.event, it.branch) }, "$at: undone")
            assertTrue(t.undone?.state == u.undone?.state, "$at: the undone state")
        }
    }

    private fun board(x: TableBoard, y: TableBoard, channel: Channel, at: String) {
        kept(x, y, channel, at)
        assertEquals(listOf(x.gameId, x.playId, x.nodeId, x.damageDealt), listOf(y.gameId, y.playId, y.nodeId, y.damageDealt), "$at: ids")
        assertEquals(x.board, y.board, "$at: the board")
        assertEquals(x.board.move, y.board.move, "$at: the move")
        assertEquals(x.definitions, y.definitions, "$at: card data")
        val e = x.engine
        val f = y.engine
        assertEquals(e == null, f == null, "$at: engine state")
        if (e == null || f == null) return
        kept(e, f, channel, "$at engine")
        assertTrue(e.state == f.state, "$at: the page's engine state differs from the device's")
        assertEquals(e.table, f.table, "$at: the table state")
        assertEquals(e.viewer, f.viewer, "$at: viewer")
        for (id in e.state.objects.keys) assertEquals(e.visible(id), f.visible(id), "$at: whether $id is visible")
        val v = x.values ?: return
        val w = checkNotNull(y.values) { "$at: no values" }
        assertEquals(listOf(v.number, v.viewer, v.openingBottom), listOf(w.number, w.viewer, w.openingBottom), "$at: values")
        val c = v.computed
        val d = w.computed
        assertEquals(listOf(c.cards, c.unsupported, c.manualAbilities, c.manualCards, c.exileStates, c.handRow),
            listOf(d.cards, d.unsupported, d.manualAbilities, d.manualCards, d.exileStates, d.handRow), "$at: computed values")
        w.handPlays?.let { assertEquals(v.handPlays?.let { it.byCard to it.offZone }, it.byCard to it.offZone, "$at: hand plays") }
        w.cardPlays?.let { assertEquals(v.cardPlays?.byCard, it.byCard, "$at: card plays") }
    }

    /** [a] the engine side's object, [b] the page's: the page keeps one object for each (a board published again is the
     * same board there too). */
    private fun kept(a: Any, b: Any, channel: Channel, at: String) {
        val had = channel.boards[a]
        if (had == null) channel.boards[a] = b else assertTrue(had === b, "$at: a new object for one the page had")
    }

    // Web client step 4c: live games' boards cross the boundary unchanged
    @Test fun liveGamesCrossTheBoundary() {
        val totals = watched { EngineTwoDeviceTest().liveGames() }
        assertTrue(totals.messages > 100 && totals.steps > 0, "$totals")
    }

    // Web client step 4c: async games' boards (catch-ups, branches and their take-backs) cross the boundary unchanged
    @Test fun asyncGamesCrossTheBoundary() {
        val totals = watched { EngineTwoDeviceTest().asyncGames() }
        assertTrue(totals.messages > 100, "$totals")
    }

    // Web client step 4c: mixed games' boards (undo, undo requests, results, reloads) cross the boundary unchanged
    @Test fun mixedGamesCrossTheBoundary() {
        val totals = watched { EngineTwoDeviceTest().mixedGames() }
        assertTrue(totals.messages > 100 && totals.loads < totals.messages / 4, "$totals")
    }
}
