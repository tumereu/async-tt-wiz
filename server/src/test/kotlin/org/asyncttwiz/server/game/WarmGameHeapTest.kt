package org.asyncttwiz.server.game

import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.Seat
import arcanum.engine.state.CardId
import arcanum.engine.state.PlayerId
import java.lang.ref.Reference
import java.util.UUID
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.EventBuilder
import org.asyncttwiz.protocol.game.GameDefinitions
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.HistoryNode
import org.asyncttwiz.protocol.game.TableState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * R6 (engine responsiveness): the heap one game kept warm by [EngineGames] holds, to set its warm capacity. A kept game is
 * its state at the main line's head (with the dropped log entries of its last [GameModel.KEPT_DROPS] turns, its view and
 * both players' visible sets) and its [EngineHistoryStore.Shared] cache (the decoded card definitions and up to 256 decoded
 * events). Each copy is built as the server builds one: the root's checkpoint decoded, then every event decoded and
 * applied, the last 256 kept. Measured as the used heap after GC over many copies held at once, divided by their number.
 * Prints sizes, asserts none. Runs only with `-Patw.warmHeap=true`:
 * `./scripts/gradle.sh :server:test -Patw.warmHeap=true --tests '*WarmGameHeapTest*'`.
 */
class WarmGameHeapTest {
    private val enabled get() = System.getProperty("atw.warmHeap") == "true"
    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")

    /** One game as the server stores it: its definitions, the root's checkpoint and its events, with the turn after each. */
    private class Recorded(val definitions: String, val root: String, val events: List<String>, val turns: List<Int>)

    /** A random game (the spike's random player) played to [turns] turns, or null when it ended before. */
    private fun record(seed: Long, turns: Int): Recorded? {
        val decks = EngineOnServerSpikeTest.Decks.creatures to EngineOnServerSpikeTest.Decks.graveyard
        val seats = listOf(alice to decks.first, bob to decks.second).map { (p, names) ->
            Seat(p, names.mapIndexed { i, name -> DeckCard(CardId("${p.value}.$i"), Cards.definition(name)) })
        }
        val model = GameModel(Engine.newGame(seats), TableState())
        val codec = EngineJson(GameDefinitions.of(model.engine))
        val root = codec.encodeModel(model)
        val chooser = EngineOnServerSpikeTest.Chooser(seed)
        val events = mutableListOf<String>()
        val after = mutableListOf<Int>()
        var parent = UUID.randomUUID().toString()
        var turn = 0
        while (model.engine.turn.number <= turns) {
            if (Engine.result(model.engine) != null || events.size > 20_000) return null
            val builder = EventBuilder(model, UUID.randomUUID().toString(), parent, alice.value)
            builder.perform(chooser.next(model.engine))
            val event = builder.build()
            events += codec.encodeEvent(event)
            after += model.engine.turn.number
            parent = event.id
            chooser.inputsThisTurn++
            if (model.engine.turn.number != turn) { turn = model.engine.turn.number; chooser.inputsThisTurn = 0 }
        }
        return Recorded(codec.encodeDefinitions(), root, events, after)
    }

    /** What [EngineGames] keeps of one play: the warm state and the play's shared cache. */
    private class Kept(val model: GameModel, val codec: EngineJson, val events: List<HistoryNode>)

    /** One kept game at the end of turn [turn] of [game], built as the server builds it. */
    private fun kept(game: Recorded, turn: Int): Kept {
        val codec = EngineJson(EngineJson.decodeDefinitions(game.definitions))
        val model = codec.decodeModel(game.root)
        val count = game.turns.indexOfFirst { it > turn }.let { if (it < 0) game.events.size else it }
        val cache = ArrayDeque<HistoryNode>()
        for (i in 0 until count) {
            val event = codec.decodeEvent(game.events[i])
            model.apply(event)
            cache.addLast(HistoryNode(event, i + 1))
            if (cache.size > 256) cache.removeFirst()
        }
        model.view()
        listOf(alice, bob).forEach(model::visible)
        return Kept(model, codec, cache.toList())
    }

    private fun used(): Long {
        val runtime = Runtime.getRuntime()
        repeat(5) { System.gc(); Thread.sleep(100) }
        return runtime.totalMemory() - runtime.freeMemory()
    }

    /** KiB per copy of what [part] keeps of a kept game, over [copies] copies held at once. */
    private fun perCopy(copies: Int, part: () -> Any): Long {
        val before = used()
        val held = List(copies) { part() }
        val after = used()
        Reference.reachabilityFence(held)
        return (after - before) / copies / 1024
    }

    private fun measure(game: Recorded, turn: Int, copies: Int) {
        val sample = kept(game, turn) // also the warm-up: classes loaded, lazy statics set
        val whole = perCopy(copies) { kept(game, turn) }
        val state = perCopy(copies) { kept(game, turn).model }
        val definitions = perCopy(copies) { kept(game, turn).codec }
        println("WARMHEAP turn $turn (${sample.model.engine.objects.size} objects, log ${sample.model.engine.log.size}, " +
            "events ${game.turns.count { it <= turn }}, cached ${sample.events.size}, definitions ${sample.codec.definitions.size}): " +
            "kept game $whole KiB: state $state KiB, decoded definitions $definitions KiB, cached events ${whole - state - definitions} KiB " +
            "(over $copies copies; the state's checkpoint ${sample.codec.encodeModel(sample.model).length / 1024} KiB as JSON)")
    }

    // The capacity set from the measurement: an eighth of the heap at 4 MiB a play, 32 to 128 plays
    @Test fun `the warm capacity follows the heap`() {
        val gib = 1L shl 30
        assertEquals(128, warmCapacity(4 * gib))
        assertEquals(64, warmCapacity(2 * gib))
        assertEquals(32, warmCapacity(gib))
        assertEquals(32, warmCapacity(gib / 2))
        assertEquals(128, warmCapacity(16 * gib))
    }

    @Test fun `a kept game's heap`() {
        if (!enabled) return
        println("WARMHEAP JVM ${Runtime.version()}, max heap ${Runtime.getRuntime().maxMemory() / (1 shl 20)} MiB")
        val game = (0L until 40L).firstNotNullOfOrNull { record(5000L + it, 40) } ?: error("no random game reached turn 40")
        measure(game, 10, 24)
        measure(game, 40, 12)
    }
}
