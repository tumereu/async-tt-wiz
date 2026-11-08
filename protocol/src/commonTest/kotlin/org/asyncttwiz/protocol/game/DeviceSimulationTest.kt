package org.asyncttwiz.protocol.game

import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * I-D56 step B, §13's tests: seeded whole games of two players through their devices ([GameDevice]) and the in-memory
 * server ([DeviceSim]), each player acting only through their own device the way a player would (playing back, answering
 * at decision stops: mostly the assumed answer, sometimes another, which branches; acting at their move; pressing
 * Assume buttons now and then; undoing, asking to undo and answering requests; conceding rarely, also with the other
 * player's events still on the wire), live (prompt delivery, green presence) or async (players away, coming back to a
 * catch-up), with messages crossing on the wire. Flows are often made as drafts ([GameDevice.begin]/[GameDevice.send],
 * at the head and at decision stops) with the server's events delivered in the middle of them, and players arrange
 * their own cards and give early answers (table-only events) at any time.
 *
 * Oracles: the leak oracle on every event shown or sent (nothing revealed while the player's line is tentative, §5),
 * worked out independently of the code under test ([DeviceSim]); at the end, each device's line and state equal the
 * server's, each device's reproduction bundle replays exactly, and the main line replayed input by input on the bare
 * engine gives every recorded change and the head's state ([DeviceSim.equivalence]); with no branch or rewind the game
 * stayed on one line.
 */
class DeviceSimulationTest {
    private enum class Mode { LIVE, ASYNC, MIXED }

    private class Stats {
        var games = 0; var events = 0; var branches = 0; var confirmations = 0; var rewinds = 0; var requests = 0
        var assumed = 0; var assumedBlocks = 0; var results = 0; var catchUps = 0; var shown = 0
        var drafts = 0; var draftsDropped = 0; var table = 0; var concessions = 0
        val undos = mutableMapOf<String, Int>()
        override fun toString() = "games $games, events $events, branches $branches, confirmations $confirmations, " +
            "rewinds $rewinds, requests $requests, assumed $assumed (blocks $assumedBlocks), results $results, catch-ups $catchUps, " +
            "shown $shown, drafts $drafts (dropped $draftsDropped), table-only $table, concessions $concessions, undos $undos"
    }

    private fun play(seed: Long, mode: Mode, decks: Pair<List<String>, List<String>>, stats: Stats, maxEvents: Int = 260) {
        val sim = DeviceSim(EngineGames(seed, decks, life = 7))
        val rnd = Random(seed * 1_000_003 + mode.ordinal)
        val players = listOf(sim.alice, sim.bob)
        val online = players.associateWith { true }.toMutableMap()
        // HIS-096: whether the other player's pauses are assumed at once is each player's setting, never their presence:
        // live play waits at them (with Assume), async play goes on
        val starter = if (rnd.nextBoolean()) sim.alice else sim.bob
        val drawn = if (rnd.nextInt(4) == 0) ResponseMode.Normal else ResponseMode.AutoSkip
        // STK-036, HIS-095: some players are in Control (their playback stops wherever they get priority, too)
        sim.start(starter, if (seed % 3 == 0L) ResponseMode.FullControl else drawn,
            alwaysAssume = when (mode) { Mode.LIVE -> false; Mode.ASYNC -> true; Mode.MIXED -> seed % 2 == 0L })
        var quiet = 0
        var lastProgress = ""
        var ticks = 0
        while (ticks++ < 6_000) {
            if (players.all { (sim.device(it).status as? GameDevice.Status.Over)?.final == true }) break
            if (sim.server.store.size > maxEvents) break
            val p = players[rnd.nextInt(2)]
            // Presence: async players come and go; a player coming back loads the game (catch-up)
            if (mode != Mode.LIVE) {
                if (!online.getValue(p)) {
                    if (rnd.nextInt(if (mode == Mode.ASYNC) 6 else 3) == 0) {
                        online[p] = true
                        if (sim.load(p).catchUp) stats.catchUps++
                    } else { sim.now += rnd.nextLong(0, 30_000); continue }
                } else if (rnd.nextInt(if (mode == Mode.ASYNC) 8 else 25) == 0) { online[p] = false; continue }
            }
            if (sim.pendingDeliveries(p) > 0) sim.deliver(p, if (rnd.nextBoolean()) null else rnd.nextInt(1, 4))
            turn(sim, p, rnd, stats)
            sim.flush(p)
            sim.now += rnd.nextLong(0, 4_000)
            val progress = "${sim.server.head}/${sim.server.heads}/${sim.server.request}/${players.map { sim.device(it).session.localHead }}"
            if (progress == lastProgress && players.all { sim.pendingDeliveries(it) == 0 }) quiet++ else quiet = 0
            lastProgress = progress
            if (quiet > 400) fail("seed $seed $mode: stuck at ${players.map { "${it.value}: ${sim.device(it).status} ${online[it]}" }}, " +
                "server ${sim.server.state().engine.turn} pending ${sim.server.state().engine.pending?.decision} holder ${sim.server.state().engine.priority.holder}")
        }
        // Everyone comes back and plays on to the head, confirming what waits for them
        for (p in players) online[p] = true
        repeat(60) {
            sim.settle()
            for (p in players) {
                val d = sim.device(p)
                if (d.needsReload) sim.load(p)
                sim.device(p).play()
                (sim.device(p).status as? GameDevice.Status.Stopped)?.let { sim.device(p).act(it.assumed); sim.flush(p) }
            }
        }
        sim.settle()
        if (sim.leaks.isNotEmpty()) fail("seed $seed $mode: leaks ${sim.leaks}")
        assertEquals(emptyList(), sim.equivalence(), "seed $seed $mode: the main line is the engine's play of its inputs")
        if (sim.branches == 0 && sim.rewinds == 0) assertEquals(0, sim.server.lines, "seed $seed $mode: one line without branches")
        for (p in players) {
            val d = sim.device(p)
            assertTrue(!d.needsReload, "seed $seed $mode ${p.value}: reload")
            assertEquals(sim.server.head, d.session.head, "seed $seed $mode ${p.value}: the device's main line ends at the server's head")
            assertEquals(sim.server.line().takeLast(d.session.events.size), d.session.events, "seed $seed $mode ${p.value}: the same main line")
            if (d.session.pending.isEmpty()) assertTrue(d.model.sameAs(sim.server.stateAt(d.session.localHead)),
                "seed $seed $mode ${p.value}: the shown state is the server's at the local head")
            val repro = GameRepro.decode(d.repro(sim.server.history.codec).encode())
            assertEquals(emptyList(), repro.check(), "seed $seed $mode ${p.value}: the bundle replays")
            // TRN-058, TRN-013 (I-D57): the device's move (pill, bar colours) is the server's (game list, notifications)
            if (d.session.pending.isEmpty() && !d.session.drafting && sim.pendingDeliveries(p) == 0)
                assertEquals(sim.serverMove(p), d.move, "seed $seed $mode ${p.value}: whose move it is (${d.status})")
        }
        stats.games++
        stats.events += sim.server.line().size
        stats.branches += sim.branches
        stats.confirmations += sim.confirmations
        stats.rewinds += sim.rewinds
        stats.shown += sim.shownChecked
        stats.assumed += sim.server.line().count { it.assumed != null }
        stats.assumedBlocks += sim.server.line().count { it.assumed?.input is Input.DeclareBlockers }
        if (Engine.result(sim.server.state().engine) != null) stats.results++
    }

    /** One thing player [p] does on their device, as a player would. */
    private fun turn(sim: DeviceSim, p: PlayerId, rnd: Random, stats: Stats) {
        val d = sim.device(p)
        d.advance(sim.now)
        d.notes()
        // Conceding at any moment, also with the other player's events still on the wire (CR 104.3a, I-D56 §11)
        if (rnd.nextInt(2_500) == 0 && d.concede() is GameDevice.Acted.Sent) { stats.concessions++; return }
        // Arranging their own cards or giving an early answer (table-only), whatever else goes on
        if (rnd.nextInt(10) == 0) tableOnly(d, rnd, stats)
        d.request?.let { r ->
            if (r.by != p.value && rnd.nextInt(3) == 0) { d.requestPreview(); d.answerRequest(rnd.nextBoolean()); return }
            if (r.by == p.value && rnd.nextInt(15) == 0) { d.cancelRequest(); return }
        }
        when (val status = d.status) {
            GameDevice.Status.Reload -> sim.load(p)
            GameDevice.Status.CatchUp -> d.play()
            is GameDevice.Status.Playing -> repeat(rnd.nextInt(1, 8)) { d.step() }
            is GameDevice.Status.Stopped -> answer(sim, d, status, rnd, stats)
            GameDevice.Status.YourMove -> move(sim, d, rnd, stats)
            is GameDevice.Status.Waiting -> when {
                // CMB-040: the Assume blockers screen the player opens, OK or Back
                status.offer == GameDevice.Offer.AssumeBlockers && sim.settings.getValue(p).assume == AssumeResolves.Always ->
                    if (rnd.nextInt(4) > 0) d.assume(blocks(d, rnd))
                status.offer != null && rnd.nextInt(6) == 0 -> d.assume(if (status.offer == GameDevice.Offer.AssumeBlockers) blocks(d, rnd) else emptyList())
                rnd.nextInt(15) == 0 && d.undoTarget() != null -> undo(d, rnd, stats)
                else -> {}
            }
            is GameDevice.Status.Over -> if (status.confirmable && !status.final) d.confirmResult()
            else -> {}
        }
    }

    /** Blocks the device's player assumes: "No blocks" mostly, else some the engine lists. */
    private fun blocks(d: GameDevice, rnd: Random): List<arcanum.engine.input.Block> =
        if (rnd.nextInt(3) > 0) emptyList()
        else Engine.options(d.model.engine, d.opponent).filterIsInstance<Input.DeclareBlockers>().randomOrNull(rnd)?.blocks.orEmpty()

    /** At a decision stop: mostly the assumed answer (a confirmation), sometimes another one (a branch). */
    private fun answer(sim: DeviceSim, d: GameDevice, stop: GameDevice.Status.Stopped, rnd: Random, stats: Stats) {
        val s = d.model.engine
        val others = Engine.options(s, d.me).filter { it !is Input.Concede && !Acceptance.same(it, stop.assumed) &&
            Authority.mayMake(s, it, d.me, d.model.table) }
        val input = if (others.isEmpty() || rnd.nextInt(10) < 7) stop.assumed else others.random(rnd)
        // Sometimes as a flow (a draft at the stop: the same first input confirms, the rest is their next action)
        if (rnd.nextInt(4) == 0) viaDraft(sim, d, input, rnd, stats)
        else if (input == stop.assumed) d.act(input) else d.act(input, follow = sim.flow(d.me, rnd))
    }

    /** [input] and the flow after it made in a draft, with the server's messages delivered in the middle now and then
     * (the other player's events crossing the flow), then sent (or, rarely, abandoned). */
    private fun viaDraft(sim: DeviceSim, d: GameDevice, input: Input, rnd: Random, stats: Stats) {
        val draft = d.begin() ?: return
        stats.drafts++
        val flow = sim.flow(d.me, rnd)
        var made = try { draft.perform(input); true } catch (_: RuntimeException) { false }
        var n = 0
        while (made && n++ < 20) {
            if (rnd.nextInt(3) == 0 && sim.pendingDeliveries(d.me) > 0) sim.deliver(d.me, 1)
            if (!d.session.drafting) break
            val next = flow(d.model) ?: break
            try { draft.perform(next) } catch (_: RuntimeException) { break }
        }
        if (!d.session.drafting) made = false
        if (!made || rnd.nextInt(15) == 0) { d.abandon(); if (!made) stats.draftsDropped++; return }
        d.send()
    }

    /** The player gives or takes back an early answer (I-D56 §6). */
    private fun tableOnly(d: GameDevice, rnd: Random, stats: Stats) {
        val key = "early:${d.me.value}:${rnd.nextInt(3)}"
        val before = d.model.table.earlyAnswers[key]
        val change = TableChange.EarlyAnswered(key, before, if (before == null) EarlyAnswer(d.me, key, Answer.YesNo(true)) else null)
        if (d.table(listOf(change)) is GameDevice.Acted.Sent) stats.table++
    }

    /** At the player's move: an action of theirs (doing something mostly, passing sometimes), an End turn, an undo. */
    private fun move(sim: DeviceSim, d: GameDevice, rnd: Random, stats: Stats) {
        val s = d.model.engine
        if (rnd.nextInt(12) == 0 && d.undoTarget() != null) return undo(d, rnd, stats)
        if (rnd.nextInt(1_500) == 0) { d.concede(); return }
        if (s.priority.holder == d.me && s.pending == null && rnd.nextInt(12) == 0) { d.endTurn(sim.now); return }
        val listed = Engine.options(s, d.me).filter { it !is Input.Concede && Authority.mayMake(s, it, d.me, d.model.table) }
        if (listed.isEmpty()) return
        val doing = listed.filterNot { it is Input.PassPriority || (it is Input.DeclareAttackers && it.attacks.isEmpty()) ||
            (it is Input.DeclareBlockers && it.blocks.isEmpty()) }
        val input = if (doing.isNotEmpty() && rnd.nextInt(3) > 0) doing.random(rnd) else (listed - doing.toSet()).ifEmpty { listed }.random(rnd)
        val mulligan = input is Input.Answer && input.answer == Answer.Option("Mulligan") || input is Input.SetupAnswer && input.answer == Answer.Option("Mulligan")
        if (mulligan && rnd.nextInt(3) > 0) return
        if (rnd.nextInt(3) == 0) viaDraft(sim, d, input, rnd, stats) else d.act(input, follow = sim.flow(d.me, rnd))
    }

    /** Undo: a tap (just before the player's latest own event), or now and then a pick in the rewind dialog (just before
     * their latest own engine input, past their own table-only events). */
    private fun undo(d: GameDevice, rnd: Random, stats: Stats) {
        val picked = if (rnd.nextBoolean()) null else d.session.events.take((d.session.indexOf(d.session.localHead) ?: -1) + 1)
            .lastOrNull { e -> e.author == d.me.value && e.assumed == null && e.engine?.steps?.any { it.input != null } == true }?.parentId
        val undo = d.undo(picked ?: d.undoTarget())
        val kind = when (undo) { is GameDevice.Undo.Free -> "free"; is GameDevice.Undo.Request -> undo.reason; is GameDevice.Undo.Unavailable -> undo.reason; is GameDevice.Undo.Queued -> "queued" }
        stats.undos[kind] = (stats.undos[kind] ?: 0) + 1
        when (undo) {
            is GameDevice.Undo.Request -> if (rnd.nextBoolean()) { stats.requests++; d.requestUndo(undo.target, "sorry") }
            else -> {}
        }
    }

    private fun run(mode: Mode, seeds: LongRange, decks: Pair<List<String>, List<String>> = EngineGames.CREATURES to EngineGames.SPELLS): Stats {
        val stats = Stats()
        for (seed in seeds) play(seed, mode, decks, stats)
        println("$mode: $stats")
        return stats
    }

    // I-D56 §4, §6, §7, §8: live play: waits at pauses, prompt playback, races of assumed and real passes, drafts and
    // table-only events crossing on the wire (abreview #1, #2)
    @Test
    fun liveGames() {
        val stats = run(Mode.LIVE, 1L..3L)
        assertTrue(stats.confirmations > 0 && stats.assumed > 0 && stats.drafts > 0 && stats.table > 0, "$stats")
    }

    // I-D56 §4, §7, §8: async play: assumptions at once, catch-up playback stopping by mode, branches
    @Test
    fun asyncGames() {
        val stats = run(Mode.ASYNC, 11L..13L)
        assertTrue(stats.confirmations > 0 && stats.branches > 0 && stats.catchUps > 0, "$stats")
    }

    // I-D56 §4, §10: players coming and going (grace, live and async waits), undo requests, results
    @Test
    fun mixedGames() {
        val stats = run(Mode.MIXED, 21L..22L)
        val more = run(Mode.MIXED, 31L..32L, EngineGames.GRAVEYARD to EngineGames.CREATURES)
        assertTrue(stats.rewinds + more.rewinds > 0 && stats.requests + more.requests > 0, "$stats / $more")
    }
}
