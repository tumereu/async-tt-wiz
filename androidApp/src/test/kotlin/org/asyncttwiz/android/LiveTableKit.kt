package org.asyncttwiz.android

import android.os.Looper
import android.os.SystemClock
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.*
import org.asyncttwiz.protocol.game.EnginePreferences
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.util.IdentityHashMap
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * Alice's whole app on a game played on engine state, at the real pace of its table: her [LobbyController] (its session,
 * engine game and ticks) talks over its connection to the simulated server ([EngineAppSim]), where Bob plays on his own
 * engine game, so [TableScreen] draws her table as the phone does, every wait on the clock (the test's frame clock and the
 * main looper's, moved together by [frame]). Before the table opens, both play on directly ([playUntil]) to the board a
 * test looks at; [open] then loads Alice's app there. Both players have a new game's settings (Quick, "No mana",
 * LOB-113). The server answers at once: no network time. The devices' random ids and seeds are seeded.
 */
internal class LiveTableKit(val f: EngineGameFixture) : AutoCloseable {
    // The devices' request ids and seeds the same on every run (DeviceRandom.seeded)
    init { DeviceRandom.seeded(LIVE_TABLE_SEED) }
    val sim = EngineAppSim(f).apply { settings[f.a] = PlaySettings.NEW_GAMES; settings[f.b] = PlaySettings.NEW_GAMES }
    val me = PlayerId(f.a)
    val other = PlayerId(f.b)
    val bob: EngineGame = sim.load(f.b)
    /** Alice's engine game: played directly until [open], then the one her app's session plays. */
    var alice: EngineGame = sim.load(f.a)
        private set
    private val worker = BasicTableFixture.Worker()
    private val wire = BasicTableFixture.Wire()
    private var summary: GameSummary = f.game
    private val api = object : Lobbies {
        override suspend fun contacts(token: String, after: String?) = ContactList(emptyList())
        override suspend fun games(token: String, invitations: Boolean, after: String?) = GameList(if (invitations) emptyList() else listOf(summary))
        override suspend fun head(token: String, id: String) = summary
        override suspend fun search(token: String, query: String) = UserSearch(emptyList())
        override suspend fun add(token: String, input: AddContact): AccountIdentity = error("not used")
        override suspend fun create(token: String, input: CreateInvitation): GameSummary = error("not used")
        override fun close() = Unit
    }
    lateinit var lobby: LobbyController
        private set
    private var forwarded = 0
    /** What crosses Alice's connection, told as it does (the clips' moments). */
    var log: (String) -> Unit = {}
    private var ordinal = 0

    fun state(p: String = f.a): GameState = sim.game(p).device.model.engine
    fun cards(s: GameState, player: PlayerId, zone: List<ObjectId>, name: String) =
        zone.filter { s.obj(it)?.definition?.faces?.first()?.name == name && s.obj(it)?.owner == player }
    fun hand(name: String, player: PlayerId, s: GameState = state()) = cards(s, player, s.player(player).hand, name)
    fun field(name: String, player: PlayerId, s: GameState = state()) = cards(s, player, s.battlefield, name)

    /** Both keep their opening hands. */
    fun keep() { alice.keep(); bob.keep(); settle() }

    /** Both games delivered, played on and flushed, each player's stops answered with what was assumed and their own
     * decisions with the first answer the engine lists. */
    fun settle() = repeat(20) {
        for (p in listOf(f.a, f.b)) step(p)
        sim.now += 500
    }

    private fun step(p: String) {
        val game = sim.game(p)
        sim.deliver(p)
        game.playToStop()
        game.tick(sim.now)
        (game.device.status as? GameDevice.Status.Stopped)?.let { game.act(it.assumed) }
        val s = game.device.model.engine
        val decision = s.pending?.decision
        if (game.device.status == GameDevice.Status.YourMove && decision != null && decision !is Decision.Random &&
            Host.decider(s, decision.player) == PlayerId(p))
            Engine.options(s, PlayerId(p), EnginePreferences.of(s)).firstOrNull { it is Input.Answer && it.decision == decision.id }
                ?.let { game.send(listOf(it)) }
        sim.flush(p)
        game.play()
    }

    /**
     * Plays on directly (before [open]) until [done]: each player at their move does what [plan] does first (true: it acted),
     * else declares no attackers and no blockers, answers their decisions with the first answer and passes.
     */
    fun playUntil(what: String, plan: (EngineGame, GameState) -> Boolean = { _, _ -> false }, done: () -> Boolean) {
        repeat(300) {
            if (done()) return
            settle()
            if (done()) return
            for (game in listOf(alice, bob)) {
                if (game.device.status != GameDevice.Status.YourMove) continue
                val s = game.device.model.engine
                val player = PlayerId(game.me)
                if (plan(game, s)) continue
                val options = Engine.options(s, player, EnginePreferences.of(s))
                when {
                    options.any { it is Input.DeclareAttackers } -> game.send(listOf(Input.DeclareAttackers(player, emptyList())))
                    options.any { it is Input.DeclareBlockers } -> game.send(listOf(Input.DeclareBlockers(player, emptyList())))
                    s.priority.holder == player -> game.pass()
                    else -> {}
                }
            }
        }
        fail("$what: never reached (${state().turn})")
    }

    /** [game]'s player casts their [name] from their hand (free: nothing to pay), its questions answered with the first
     * answer the engine lists (its one target, say). */
    fun cast(game: EngineGame, name: String) {
        val s = game.device.model.engine
        val player = PlayerId(game.me)
        val card = hand(name, player, s).first()
        val copy = s.deepCopy()
        val inputs = mutableListOf<Input>(Input.Cast(player, card))
        var next = inputs.first()
        while (true) {
            Engine.perform(copy, next, EnginePreferences.of(copy)).changes.forEach(copy::apply)
            val d = copy.pending?.decision ?: break
            if (d is Decision.Random || Host.decider(copy, d.player) != player) break
            next = if (d is Decision.ManaPayment) Input.Answer(d.id, Answer.ManaPayment(d.suggestion))
                else Engine.options(copy, player, EnginePreferences.of(copy)).filterIsInstance<Input.Answer>().first { it.decision == d.id }
            inputs += next
        }
        game.send(inputs)
    }

    /** Whether [game]'s player is in their own main phase [step] with priority and the Stack empty, at their move. */
    fun inMain(game: EngineGame, step: TurnStep = TurnStep.PrecombatMain): Boolean {
        val s = game.device.model.engine
        val player = PlayerId(game.me)
        return game.device.status == GameDevice.Status.YourMove && s.turn.active == player && s.turn.step == step &&
            s.stack.isEmpty() && s.priority.holder == player
    }

    /** Whether [game]'s player declares attackers now (their move). */
    fun declaring(game: EngineGame): Boolean {
        val s = game.device.model.engine
        return game.device.status == GameDevice.Status.YourMove &&
            Engine.options(s, PlayerId(game.me), EnginePreferences.of(s)).any { it is Input.DeclareAttackers }
    }

    // ---- Alice's app

    /**
     * Opens the game in Alice's app at the server's head: her session loads it from the engine head, as the server opens
     * it, and her engine game from then on is the session's. The main looper's clock first moves past the server's (the
     * devices' clock, [EngineAppSim.now]), which follows it from then on.
     */
    fun open() {
        settle()
        val looper = shadowOf(Looper.getMainLooper())
        if (SystemClock.uptimeMillis() < sim.now) looper.idleFor(Duration.ofMillis(sim.now - SystemClock.uptimeMillis() + 1_000))
        sim.now = SystemClock.uptimeMillis()
        val revision = "${sim.nextRevision}"
        summary = f.game.copy(lifecycle = GameLifecycle.ACTIVE, playId = f.base.id, preparationRound = f.base.preparationRound,
            revision = revision)
        lobby = LobbyController(api, { "A".repeat(43) }, worker, { it() }, gameConnection = wire, accountId = { f.a },
            clock = { SystemClock.uptimeMillis() }, engineWorker = EngineWorker.Direct)
        lobby.open(summary.id); worker.drain(); wire.listener.opened()
        message(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
        message(GameStreamReady(summary.id, wire.frames.filterIsInstance<OpenGameStream>().last().streamId, "1"))
        message(GameHead(summary.id, revision, summary, play = f.base, engine = sim.snapshot(f.a)))
        worker.drain()
        val game = assertNotNull(lobby.engineGame(), "the engine head loads the game in Alice's app")
        sim.games[f.a] = game
        alice = game
        sim.route = { p, delivery -> p == f.a && toAlice(delivery) }
        forwarded = wire.frames.size
    }

    private fun message(message: GameServerMessage) = wire.listener.message(message)

    /** What the server sends Alice goes over her connection: a revision with the game's summary at it; a refusal was
     * answered as the command's result already ([forward]). */
    private fun toAlice(delivery: EngineAppSim.Delivery): Boolean {
        if (delivery is EngineAppSim.Delivery.Revision) {
            log("server → Alice: revision ${delivery.message.resultRevision} by ${delivery.message.author.take(8)}" +
                (if (delivery.message.engineEvent != null) " with an event" else "") +
                (delivery.message.engine?.let { " (${it.events.size} more, confirmed ${it.confirmed?.take(8)})" } ?: ""))
            summary = summary.copy(revision = delivery.message.resultRevision)
            message(delivery.message.copy(summary = summary))
        }
        return true
    }

    /** Alice's commands sent since the last time reach the server, each answered with its result. */
    private fun forward() {
        while (forwarded < wire.frames.size) {
            val frame = wire.frames[forwarded++]
            if (frame !is GameCommand) continue
            val action = frame.action
            val reason = if (action is GameAction.Submit || action.isEngine) sim.submit(f.a, action) else null
            log("Alice → server: ${action::class.simpleName}${(action as? GameAction.Submit)?.let { " ${it.submission.id.take(8)}" } ?: ""}: " +
                (reason ?: "accepted"))
            message(GameResult(frame.requestId, frame.scope, if (reason == null) "accepted" else "refused", reason,
                ingressOrdinal = "${++ordinal}"))
        }
    }

    /** What Bob's phone does by itself: it gets the server's messages, plays them back at once, answers its stops with
     * what was assumed and Bob's decisions with the first answer, and sends what it made. */
    var bobOnline = true

    /** The network and Bob's phone, at once: Alice's commands reach the server, its messages reach both phones, Bob's phone
     * goes on ([bobOnline]) and what it sends reaches the server. */
    fun pump() {
        sim.now = SystemClock.uptimeMillis()
        repeat(4) {
            forward()
            sim.deliver(f.a)
            worker.drain()
            if (bobOnline) step(f.b)
        }
    }

    override fun close() {
        if (::lobby.isInitialized) { lobby.close(); worker.drain() }
        DeviceRandom.seeded(null)
    }
}

private const val LIVE_TABLE_SEED = 20261123L
