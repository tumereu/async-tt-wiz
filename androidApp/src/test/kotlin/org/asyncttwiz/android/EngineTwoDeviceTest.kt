package org.asyncttwiz.android

import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Block
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.game.Acceptance
import org.asyncttwiz.protocol.game.Authority
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameRepro
import org.asyncttwiz.protocol.game.TableChange
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * I-D56 step D (engine-state-architecture.md §12 item 12): seeded whole games of two players through the app's engine
 * games ([EngineGame]: what the table calls) on one simulated server ([EngineAppSim]), each player acting the way the
 * table lets them: the opening (mulligans, keeps, assumed keeps), casting Storm Sparks with their targets, playing lands
 * and Ember Cubs, staging attacks and blocks as shared drafts (I-D54 §8) and declaring them, End turn, the Assume buttons
 * and the Assume blockers screen with edited blocks, playback and decision stops (confirming mostly, branching
 * sometimes), undo and undo requests, and the result's OK; live, async (players away, coming back to the catch-up) and
 * mixed. Every round also builds what the table shows (the board, the strip, a reviewed board).
 *
 * Oracles: the leak oracle on every event shown or sent; at the end each game's main line and state equal the server's
 * and each game's problem-report bundle replays exactly.
 */
class EngineTwoDeviceTest {
    private enum class Mode { LIVE, ASYNC, MIXED }

    private class Stats {
        var games = 0; var events = 0; var branches = 0; var confirmations = 0; var rewinds = 0; var requests = 0
        var assumed = 0; var assumedBlocks = 0; var attacks = 0; var blocks = 0; var drafts = 0; var results = 0
        var catchUps = 0; var shown = 0
        override fun toString() = "games $games, events $events, branches $branches, confirmations $confirmations, rewinds $rewinds, " +
            "requests $requests, assumed $assumed (blocks $assumedBlocks), attacks $attacks, blocks $blocks, drafts $drafts, " +
            "results $results, catch-ups $catchUps, shown $shown"
    }

    private fun play(seed: Long, mode: Mode, stats: Stats, maxEvents: Int = 320) {
        val f = EngineGameFixture(if (seed % 2 == 0L) TableStatesFixture.a else TableStatesFixture.b, creatures = true)
        val sim = EngineAppSim(f)
        val rnd = Random(seed * 7_919 + mode.ordinal)
        val players = listOf(f.a, f.b)
        val names = mapOf(f.a to "Alice", f.b to "Bob")
        val online = players.associateWith { true }.toMutableMap()
        // HIS-096: whether the other player's pauses are assumed at once is each player's setting, never their presence
        for (p in players) sim.settings[p] = org.asyncttwiz.protocol.game.PlaySettings(alwaysAssumeResolves = when (mode) {
            Mode.LIVE -> false; Mode.ASYNC -> true; Mode.MIXED -> seed % 2 == 0L })
        for (p in players) sim.load(p)
        var ticks = 0
        var quiet = 0
        var lastProgress = ""
        while (ticks++ < 5_000) {
            if (players.all { (sim.game(it).device.status as? GameDevice.Status.Over)?.final == true }) break
            if (sim.store.size > maxEvents) break
            val p = players[rnd.nextInt(2)]
            if (mode != Mode.LIVE) {
                if (!online.getValue(p)) {
                    if (rnd.nextInt(if (mode == Mode.ASYNC) 6 else 3) == 0) {
                        online[p] = true
                        if (sim.load(p).device.catchUp) stats.catchUps++
                    } else { sim.now += rnd.nextLong(0, 30_000); continue }
                } else if (rnd.nextInt(if (mode == Mode.ASYNC) 8 else 25) == 0) { online[p] = false; continue }
            }
            val game = sim.game(p)
            if (sim.pending(p) > 0) sim.deliver(p, if (rnd.nextBoolean()) null else rnd.nextInt(1, 4))
            game.tick(sim.now)
            turn(sim, game, rnd)
            sim.flush(p)
            shows(game, names, rnd)
            sim.now += rnd.nextLong(0, 4_000)
            val progress = "${sim.head}/${sim.heads}/${sim.request}/${players.map { sim.game(it).device.session.localHead }}"
            if (progress == lastProgress && players.all { sim.pending(it) == 0 }) quiet++ else quiet = 0
            lastProgress = progress
            if (quiet > 400) fail("seed $seed $mode: stuck at ${players.map { "$it: ${sim.game(it).device.status} ${online[it]}" }}")
        }
        // Everyone comes back and plays on to the head, confirming what waits for them
        for (p in players) online[p] = true
        repeat(60) {
            for (p in players) {
                val game = sim.game(p)
                if (game.needsReload) sim.load(p)
                sim.deliver(p)
                if (game.device.catchUp) game.startPlayback()
                game.playToStop()
                game.tick(sim.now)
                (game.device.status as? GameDevice.Status.Stopped)?.let { game.act(it.assumed) }
                sim.flush(p)
            }
        }
        if (sim.leaks.isNotEmpty()) fail("seed $seed $mode: leaks ${sim.leaks}")
        for (p in players) {
            val game = sim.game(p)
            val d = game.device
            assertTrue(!d.needsReload, "seed $seed $mode $p: reload")
            assertEquals(sim.head, d.session.head, "seed $seed $mode $p: the game's main line ends at the server's head")
            assertEquals(sim.line().takeLast(d.session.events.size), d.session.events, "seed $seed $mode $p: the same main line")
            if (d.session.pending.isEmpty()) assertTrue(d.model.sameAs(sim.history.stateAt(d.session.localHead)),
                "seed $seed $mode $p: the shown state is the server's at the local head")
            val repro = assertNotNullRepro(game)
            assertEquals(emptyList(), repro.check(), "seed $seed $mode $p: the problem report's bundle replays")
            // TRN-058 (I-D57): the table's move pill and bar colours show whom the game really waits for
            assertEquals(d.move?.value, game.play().board.move, "seed $seed $mode $p: the move shown (${d.status})")
            shows(game, names, rnd)
        }
        val line = sim.line()
        stats.games++
        stats.events += line.size
        stats.branches += sim.branches
        stats.confirmations += sim.confirmations
        stats.rewinds += sim.rewinds
        stats.requests += sim.requests
        stats.shown += sim.shownChecked
        stats.assumed += line.count { it.assumed != null }
        stats.assumedBlocks += line.count { it.assumed?.input is Input.DeclareBlockers }
        stats.attacks += line.count { e -> e.engine?.steps.orEmpty().any { (it.input as? Input.DeclareAttackers)?.attacks?.isNotEmpty() == true } }
        stats.blocks += line.count { e -> e.engine?.steps.orEmpty().any { (it.input as? Input.DeclareBlockers)?.blocks?.isNotEmpty() == true } }
        stats.drafts += line.count { e -> e.table.any { it is TableChange.AttackDraft || it is TableChange.BlockDraft } }
        if (Engine.result(sim.state().engine) != null) stats.results++
    }

    private fun assertNotNullRepro(game: EngineGame): GameRepro =
        GameRepro.decode((game.repro() ?: fail("no bundle")).encode())

    /** What the table shows of [game] now: the board, the strip and, now and then, the board at an earlier event. */
    private fun shows(game: EngineGame, names: Map<String, String>, rnd: Random) {
        val play = game.play()
        assertTrue(play.engine != null)
        val strip = game.timeline.events(game, names)
        if (strip.isNotEmpty() && rnd.nextInt(10) == 0) game.reviewPlay(strip[rnd.nextInt(strip.size)].id)
    }

    /** One thing [game]'s player does, as the table lets them. */
    private fun turn(sim: EngineAppSim, game: EngineGame, rnd: Random) {
        val d = game.device
        game.notes.clear()
        d.request?.let { r ->
            if (r.by != game.me && rnd.nextInt(3) == 0) { d.requestPreview(); game.answerRequest(rnd.nextBoolean()); return }
            if (r.by == game.me && rnd.nextInt(15) == 0) { game.cancelRequest(); return }
        }
        when (val status = d.status) {
            GameDevice.Status.Reload -> sim.load(game.me)
            GameDevice.Status.CatchUp -> game.startPlayback()
            is GameDevice.Status.Playing -> repeat(rnd.nextInt(1, 8)) { game.step() }
            is GameDevice.Status.Stopped -> answer(game, status, rnd)
            GameDevice.Status.YourMove -> move(game, rnd)
            is GameDevice.Status.Waiting -> when {
                // CMB-040: a player who plays on opens the Assume blockers screen and mostly OKs it
                status.offer == GameDevice.Offer.AssumeBlockers && d.settings.alwaysAssumeResolves ->
                    if (rnd.nextInt(4) > 0) game.assume(blocks(d.model.engine, d.opponent, rnd))
                status.offer != null && rnd.nextInt(6) == 0 ->
                    game.assume(if (status.offer == GameDevice.Offer.AssumeBlockers) blocks(d.model.engine, d.opponent, rnd) else emptyList())
                rnd.nextInt(30) == 0 && d.undoTarget() != null -> undo(game, rnd)
                else -> {}
            }
            is GameDevice.Status.Over -> if (status.confirmable && !status.final) game.confirmResult()
            else -> {}
        }
    }

    /** At a decision stop: mostly the assumed answer (a confirmation), sometimes another one (a branch). */
    private fun answer(game: EngineGame, stop: GameDevice.Status.Stopped, rnd: Random) {
        val d = game.device
        val s = d.model.engine
        val others = Engine.options(s, d.me).filter { it !is Input.Concede && !Acceptance.same(it, stop.assumed) &&
            Authority.mayMake(s, it, d.me, d.model.table) && it !is Input.DeclareAttackers }
        if (others.isEmpty() || rnd.nextInt(10) < 7) game.act(stop.assumed)
        else game.send(flow(s, d.me, others.random(rnd), rnd))
    }

    /** At the player's move: the opening, a land, a creature, a Storm Spark at something, a staged and declared attack or
     * blocks, a pass, End turn, an undo. */
    private fun move(game: EngineGame, rnd: Random) {
        val d = game.device
        val s = d.model.engine
        val me = d.me
        if (rnd.nextInt(25) == 0 && d.undoTarget() != null) return undo(game, rnd)
        if (s.turn.number == 0) {
            // TRN-062: the opening's Mulligan and Keep (the first mulligan is free; later ones owe cards to the bottom)
            val mulligans = s.log.count { l -> (l.change as? arcanum.engine.change.Change.Happened)?.event.let {
                it is arcanum.engine.change.GameEvent.Mulliganed && it.player == me } }
            if (mulligans < 2 && rnd.nextInt(5) == 0 && game.mulligan() is GameDevice.Acted.Sent) return
            val hand = s.player(me).hand.mapNotNull { (it as? ObjectId.Card)?.card?.value }
            if (game.keep(hand.take((mulligans - 1).coerceAtLeast(0))) is GameDevice.Acted.Sent) return
        }
        if (s.priority.holder == me && s.pending == null && rnd.nextInt(14) == 0) { game.endTurn(); return }
        val listed = Engine.options(s, me).filter { it !is Input.Concede && Authority.mayMake(s, it, me, d.model.table) }
        val attackers = listed.any { it is Input.DeclareAttackers }
        val blockers = listed.any { it is Input.DeclareBlockers }
        when {
            attackers -> declareAttackers(game, s, me, rnd)
            blockers -> declareBlockers(game, s, me, rnd)
            listed.isEmpty() -> {}
            else -> {
                val doing = listed.filterNot { it is Input.PassPriority }
                val input = if (doing.isNotEmpty() && rnd.nextInt(3) > 0) doing.random(rnd) else listed.random(rnd)
                game.send(flow(s, me, input, rnd))
            }
        }
    }

    /** CMB-043 (I-D54 §8): the attacker stages some creatures (a shared draft, sometimes changed once), then declares. */
    private fun declareAttackers(game: EngineGame, s: GameState, me: PlayerId, rnd: Random) {
        val defender = s.players.first { it.id != me }.id
        val view = Engine.view(s)
        val able = s.battlefield.filter { id -> val o = s.obj(id); val v = view[id]
            o != null && !o.tapped && v?.controller == me && v.power != null && !v.summoningSick }
        val attacks = able.filter { rnd.nextInt(3) > 0 }.map { Attack(it, TargetRef.Player(defender)) }
        if (attacks.isNotEmpty() && rnd.nextBoolean()) {
            game.table(listOf(TableChange.AttackDraft(game.device.model.table.attackDraft, attacks.take(1))))
            game.table(listOf(TableChange.AttackDraft(game.device.model.table.attackDraft, attacks)))
        }
        if (game.send(listOf(Input.DeclareAttackers(me, attacks))) is GameDevice.Acted.Refused) game.send(listOf(Input.DeclareAttackers(me, emptyList())))
    }

    /** CMB-034, CMB-043 (I-D54 §8): the defender arranges blocks (a shared draft), then declares them. */
    private fun declareBlockers(game: EngineGame, s: GameState, me: PlayerId, rnd: Random) {
        val blocks = blocks(s, me, rnd)
        if (blocks.isNotEmpty() && rnd.nextBoolean()) game.table(listOf(TableChange.BlockDraft(game.device.model.table.blockDraft, blocks)))
        if (game.send(listOf(Input.DeclareBlockers(me, blocks))) is GameDevice.Acted.Refused) game.send(listOf(Input.DeclareBlockers(me, emptyList())))
    }

    /** Blocks by [defender]'s untapped creatures, each at a random attacker (none at times). */
    private fun blocks(s: GameState, defender: PlayerId, rnd: Random): List<Block> {
        val attackers = s.combat?.attackers.orEmpty().map { it.id }
        if (attackers.isEmpty() || rnd.nextInt(3) == 0) return emptyList()
        val view = Engine.view(s)
        return s.battlefield.filter { id -> val o = s.obj(id); val v = view[id]
            o != null && !o.tapped && v?.controller == defender && v.power != null }
            .filter { rnd.nextBoolean() }.map { Block(it, attackers.random(rnd)) }
    }

    /** [first] and the player's own answers that follow it in the same flow (targets, payments), worked out on a copy of
     * [s] as the table's cast and choice panels do; a random decision ends it (the device answers it). */
    private fun flow(s: GameState, me: PlayerId, first: Input, rnd: Random): List<Input> {
        val copy = s.deepCopy()
        val inputs = mutableListOf(first)
        var input = first
        repeat(20) {
            val outcome = try { Engine.perform(copy, input) } catch (_: RuntimeException) { return inputs.dropLast(1).ifEmpty { listOf(first) } }
            outcome.changes.forEach(copy::apply)
            val d = copy.pending?.decision ?: return inputs
            if (d is Decision.Random || Engine.decider(copy, d.player) != me) return inputs
            input = if (d is Decision.ManaPayment) Input.Answer(d.id, Answer.ManaPayment(d.suggestion))
                else Engine.options(copy, me).filterIsInstance<Input.Answer>().filter { it.decision == d.id }.randomOrNull(rnd) ?: return inputs
            inputs += input
        }
        return inputs
    }

    private fun undo(game: EngineGame, rnd: Random) {
        when (val undo = game.undo()) {
            is GameDevice.Undo.Request -> if (rnd.nextBoolean()) game.requestUndo(undo.target, "sorry")
            else -> {}
        }
    }

    private fun run(mode: Mode, seeds: LongRange): Stats {
        val stats = Stats()
        for (seed in seeds) play(seed, mode, stats)
        println("app $mode: $stats")
        return stats
    }

    // HIS-096, HIS-095, CMB-028, CMB-034 (I-D56 §4, §7; I-D54 §8): live play through the app's games: waits, prompt
    // playback, shared staging, combat, results
    @Test fun liveGames() {
        val stats = run(Mode.LIVE, 1L..2L)
        assertTrue(stats.confirmations > 0 && stats.assumed > 0 && stats.attacks > 0 && stats.drafts > 0, "$stats")
    }

    // HIS-033, HIS-097 (I-D56 §7, §8): async play: assumptions at once, catch-ups, branches
    @Test fun asyncGames() {
        val stats = run(Mode.ASYNC, 11L..13L)
        assertTrue(stats.confirmations > 0 && stats.branches > 0 && stats.catchUps > 0, "$stats")
    }

    // HIS-099, HIS-100, TRN-065 (I-D56 §10, §11): players coming and going, undo requests, results
    @Test fun mixedGames() {
        val stats = run(Mode.MIXED, 33L..37L)
        assertTrue(stats.rewinds > 0 && stats.requests > 0 && stats.results > 0, "$stats")
    }
}
