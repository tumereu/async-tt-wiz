package org.asyncttwiz.automatum.boundary

import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.input.Input
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.Seat
import arcanum.engine.setup.StartOptions
import arcanum.engine.state.CardId
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.EventBuilder
import org.asyncttwiz.protocol.game.GameDefinitions
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.LineRandom
import org.asyncttwiz.protocol.game.TableState
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Web client step 4c: the page's copy of a game's board states ([BoardMirror]) stays equal to the device's through a
 * random game: events made by both players (random legal inputs of invented cube cards), branches (events taken back
 * and others made), the line's start trimmed, drafts (an event still being made), a step of a motion and of a take-back
 * ([StateOrigin.Steps]), and the page loading the game anew; every reference crossing as JSON. The engine side's copy
 * ([MirrorShadow]) sends a snapshot only where it must.
 */
class BoardMirrorTest {
    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")
    private val names = listOf("Mountain", "Forest", "Island", "Storm Spark", "Elvish Seer")

    /** A device's game: its model at the end of [line] (after the event [start]), every event made by id. */
    private inner class Game(seed: Int) {
        val rnd = Random(seed)
        val model: GameModel
        val codec: EngineJson
        var start = "root"
        val line = ArrayList<GameEvent>()
        val all = HashMap<String, GameEvent>()
        private var made = 0

        init {
            val cube = Cards.cubeNames.shuffled(rnd).take(12)
            fun deck(p: PlayerId) = List(40) { i -> DeckCard(CardId("${p.value}-$i"),
                Cards.definition(if (i % 5 < 2) names[i % 3] else (cube + names).let { it[(i * 7 + seed) % it.size] })) }
            model = GameModel(Engine.newGame(listOf(Seat(alice, deck(alice)), Seat(bob, deck(bob)))),
                TableState(secret = LineRandom.secret(ByteArray(32) { seed.toByte() })))
            codec = EngineJson(GameDefinitions.of(model.engine))
            make { Input.StartGame(StartOptions(startingPlayer = alice, simultaneousMulligans = true, freeFirstMulligan = true)) }
        }

        val tip: String get() = line.lastOrNull()?.id ?: start

        /** One event made on the model at the tip with [input]'s input, its random answers following. */
        fun make(input: (GameState) -> Input?): GameEvent? {
            val i = input(model.engine) ?: return null
            val builder = EventBuilder(model, "e${made++}", tip, "x")
            return try {
                builder.perform(i); builder.answerRandoms()
                builder.build().also { line += it; all[it.id] = it }
            } catch (problem: RuntimeException) { builder.abandon(); null }
        }

        /** A random legal input of whoever may act (setup answers keep, mostly passes, sometimes anything). */
        fun input(state: GameState): Input? {
            // The opening: mostly keeps (a wrong answer is refused and something else is made)
            val d = state.pending?.decision
            if (d != null && state.turn.number == 0 && rnd.nextInt(4) > 0) return Input.Answer(d.id, Answer.Option("Keep"))
            val options = listOf(alice, bob).flatMap { p -> runCatching { Engine.options(state, p) }.getOrDefault(emptyList()) }
            if (options.isEmpty()) return null
            val passes = options.filterIsInstance<Input.PassPriority>()
            return if (passes.isNotEmpty() && rnd.nextInt(3) > 0) passes.random(rnd) else options.random(rnd)
        }

        fun forward(n: Int): List<GameEvent> = (0 until n).mapNotNull { make(::input) }

        fun back(n: Int): List<GameEvent> = (0 until minOf(n, line.size)).map { line.removeAt(line.lastIndex).also(model::revert) }
    }

    /** A reference as it crosses the boundary. */
    private fun crossed(ref: StateRef): StateRef = EngineMessages.json.decodeFromString(StateRef.serializer(),
        EngineMessages.json.encodeToString(StateRef.serializer(), ref))

    private fun assertSame(expected: Pair<GameState, TableState>, actual: Pair<GameState, TableState>, what: String) {
        assertEquals(expected.second, actual.second, "$what: the table state")
        assertTrue(expected.first == actual.first, "$what: the page's engine state differs from the device's")
    }

    @Test fun thePageCopyFollowsTheDeviceThroughARandomGame() {
        val drafts = (1..4).sumOf { play(it) }
        assertTrue(drafts > 0, "$drafts drafts in all")
    }

    /** One random game; the drafts it showed. */
    private fun play(seed: Int): Int {
        val g = Game(seed)
        val shadow = MirrorShadow(g.codec)
        // The page decodes with its own codec, from the definitions as sent
        val pageCodec = EngineJson(EngineJson.decodeDefinitions(g.codec.encodeDefinitions()))
        val page = BoardMirror(pageCodec)
        val lookup = { id: String -> pageCodec.decodeEvent(g.codec.encodeEvent(g.all.getValue(id))) }
        var lines = 0; var steps = 0; var drafts = 0
        fun board(draft: GameEvent? = null, what: String) {
            val origin = StateOrigin.Line(g.start, g.line.toList(), draft)
            val ref = crossed(shadow.line(origin, g.model.engine.deepCopy(), g.model.table))
            page.move(ref, lookup)
            assertSame(g.model.engine to g.model.table, page.state(ref, lookup), "seed $seed, $what")
            assertEquals(shadow.mirror.events, page.events, "seed $seed, $what: the mirror line")
            lines++
        }
        board(what = "the start")
        repeat(60) { round ->
            when (g.rnd.nextInt(10)) {
                0, 1 -> { g.back(g.rnd.nextInt(1, 4)); board(what = "round $round, taken back") }
                2 -> {
                    // The line's start moves on: the device keeps the newer part of its line
                    val drop = g.rnd.nextInt(0, (g.line.size / 2).coerceAtLeast(1))
                    if (drop > 0) { g.start = g.line[drop - 1].id; repeat(drop) { g.line.removeAt(0) } }
                    board(what = "round $round, start trimmed")
                }
                3 -> {
                    // A draft: an event still being made shows with the line under it
                    val input = g.input(g.model.engine) ?: return@repeat
                    val builder = EventBuilder(g.model, "draft-$round", g.tip, "x")
                    try {
                        builder.perform(input)
                        board(builder.provisional(), "round $round, a draft")
                        drafts++
                    } catch (problem: RuntimeException) { } finally { if (!builder.finished) builder.abandon() }
                }
                4 -> { shadow.clear(); page.clear(); board(what = "round $round, loaded anew") }
                else -> {
                    val forward = g.forward(g.rnd.nextInt(1, 5))
                    board(what = "round $round, on")
                    if (forward.isNotEmpty()) {
                        // The motion's steps: from the board back along the events, then on change by change
                        val base = StateOrigin.Line(g.start, g.line.toList())
                        val back = forward.asReversed().toList()
                        val i = g.rnd.nextInt(forward.size)
                        val c = g.rnd.nextInt((forward[i].engine?.changes?.size ?: 0) + 1)
                        val expected = BoardMirror.walk(g.model, back, forward, i, c)
                        val ref = crossed(shadow.steps(StateOrigin.Steps(base, back, forward, i, c), expected.first, expected.second))
                        assertSame(expected, page.state(ref, lookup), "seed $seed, round $round, a step")
                        if (ref is StateRef.Steps) steps++
                    }
                }
            }
        }
        assertTrue(lines > 40 && steps > 3, "seed $seed: $lines boards, $steps steps")
        assertTrue(shadow.loads < lines / 3, "seed $seed: ${shadow.loads} snapshots for $lines boards")
        return drafts
    }

    @Test fun aRevertPastTheTurnsTheMirrorHoldsGoesAsASnapshot() {
        val g = Game(9)
        val shadow = MirrorShadow(g.codec)
        val page = BoardMirror(g.codec)
        val lookup = { id: String -> g.all.getValue(id) }
        // Loaded at a later turn, the mirror holds no dropped turns: going back past them can't be exact
        while (g.model.engine.turn.number < 4) if (g.forward(1).isEmpty()) break
        page.move(shadow.line(StateOrigin.Line(g.start, g.line.toList()), g.model.engine.deepCopy(), g.model.table), lookup)
        while (g.line.isNotEmpty() && g.model.engine.turn.number >= 3) g.back(1)
        val ref = shadow.line(StateOrigin.Line(g.start, g.line.toList()), g.model.engine.deepCopy(), g.model.table)
        assertIs<StateRef.Load>(ref)
        page.move(ref, lookup)
        assertSame(g.model.engine to g.model.table, page.state(ref, lookup), "after the snapshot")
    }

    @Test fun listsGoAsWhatChanged() {
        val before = listOf("a", "b", "c", "d")
        val grown = ListDelta.of(before, listOf("a", "b", "c", "d", "e"))
        assertEquals(ListDelta(0, 4, listOf("e")), grown)
        val trimmed = ListDelta.of(before, listOf("c", "d", "x"))
        assertEquals(listOf("c", "d", "x"), trimmed.applied(before))
        assertEquals(listOf("y"), ListDelta.of(before, listOf("y")).applied(before))
        assertEquals(emptyList(), ListDelta.of(before, emptyList()).applied(before))
    }
}
