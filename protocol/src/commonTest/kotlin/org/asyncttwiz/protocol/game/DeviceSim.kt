package org.asyncttwiz.protocol.game

import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.EngineRewindRequest
import org.asyncttwiz.protocol.EngineSnapshot
import org.asyncttwiz.protocol.EngineUpdate
import org.asyncttwiz.protocol.GameAction
import kotlin.random.Random

/**
 * Two devices ([GameDevice]) and the in-memory server ([AsyncServer]) exchanging only what the app and the server
 * exchange (I-D56 step B): the devices' [GameAction]s go to the server in order, and each device gets the server's
 * engine revisions (events and [EngineUpdate]) and its own refusals, in order, when [deliver]ed. Delivery is under the
 * test's control, so live play (prompt delivery), async play (a device that is away gets nothing and loads the game
 * again when it comes back) and races (actions crossing on the wire) are deterministic.
 *
 * The leak oracle runs on every event a device shows: while the player's line is tentative on the server (an
 * assumption of theirs not yet confirmed), nothing shown may reveal a card they couldn't see before (I-D56 §5, §13).
 */
internal class DeviceSim(val games: EngineGames, every: Int = 16) {
    val server = AsyncServer(games, every)
    val alice get() = games.alice
    val bob get() = games.bob
    val devices = mutableMapOf<PlayerId, GameDevice>()
    private val inbox = mutableMapOf<PlayerId, ArrayDeque<Delivery>>()
    var now = 0L
    private var ids = 0
    val settings = mutableMapOf<PlayerId, PlaySettings>()
    /** Each player's presence as the other's device sees it (the green dot). */
    val live = mutableMapOf<PlayerId, Boolean>()
    /** How many events each oracle looked at, and leaks found. */
    var shownChecked = 0
    val leaks = mutableListOf<String>()
    var branches = 0
    var confirmations = 0
    var rewinds = 0

    sealed interface Delivery {
        data class Revision(val events: List<GameEvent>, val update: EngineUpdate) : Delivery
        data class Refusal(val action: GameAction, val reason: String) : Delivery
    }

    fun other(p: PlayerId) = if (p == alice) bob else alice
    fun device(p: PlayerId): GameDevice = devices.getValue(p)

    /** Starts the game (the server's own first event) and loads both devices. */
    fun start(starter: PlayerId = alice, mode: ResponseMode = ResponseMode.AutoSkip) {
        server.start(starter)
        for (p in listOf(alice, bob)) { settings[p] = PlaySettings(mode = mode); live[p] = false; load(p) }
    }

    /** [p]'s device (re)loaded from the server's snapshot, as the app opening the game: at their local head with the
     * events past it buffered (I-D56 §7). */
    fun load(p: PlayerId): GameDevice {
        val baseId = server.heads.values.minByOrNull { depth(it) } ?: server.head
        val after = MainLine.after(server.history, server.head, baseId)!!
        val codec = server.history.codec
        val snapshot = EngineSnapshot(codec.encodeDefinitions(), "root", server.head, codec.encodeModel(server.state()),
            server.heads.mapKeys { it.key.value }, settings[p], server.request, server.lines,
            baseId.takeIf { after.isNotEmpty() }, if (after.isEmpty()) null else codec.encodeModel(server.stateAt(baseId)),
            after.map(codec::encodeEvent))
        val (device, _) = GameDevice.load(snapshot, p) { "${p.value}-${++ids}" }
        device.presence(live[other(p)] == true, now)
        watch(device)
        devices[p] = device
        inbox[p] = ArrayDeque()
        return device
    }

    private fun depth(id: String) = server.history.depthOf(id)

    /** The leak oracle on [device]: every event shown while the player's line is tentative on the server. */
    private fun watch(device: GameDevice) {
        device.session.beforeShow = { before, event -> check(device, before, event) }
        device.session.onSent = { before, event -> check(device, before, event) }
    }

    private fun check(device: GameDevice, before: arcanum.engine.state.GameState, event: GameEvent) {
        shownChecked++
        val me = device.me
        val other = server.heads[other(me)] ?: return
        val tentative = (MainLine.after(server.history, server.head, other).orEmpty() + device.session.pending)
            .any { it.assumed?.assumedBy == me && it.id != event.id } || event.assumed?.assumedBy == me
        if (!tentative) return
        val shown = InformationRule.revealing(before, event.engine?.changes.orEmpty(), me)
        if (shown.isNotEmpty()) leaks += "${me.value} shown $shown by ${event.id}"
    }

    /** Sends [p]'s device's outbox to the server, in order; the server's answers go into the inboxes. */
    fun flush(p: PlayerId) {
        for (action in device(p).take(now)) handle(p, action)
    }

    private fun broadcast(events: List<GameEvent>, target: String? = null, confirmed: String? = null) {
        val update = EngineUpdate(emptyList(), target, server.heads.mapKeys { it.key.value }, server.lines, server.request, confirmed)
        for (q in listOf(alice, bob)) inbox.getValue(q) += Delivery.Revision(events, update)
    }

    private fun refuse(p: PlayerId, action: GameAction, reason: String) { inbox.getValue(p) += Delivery.Refusal(action, reason) }

    private fun handle(p: PlayerId, action: GameAction) {
        when (action) {
            is GameAction.Submit -> server.submit(action.submission, p).fold({ result ->
                when (result) {
                    is Acceptance.Accepted -> { if (result.discarded != null) branches++; broadcast(listOf(result.event)) }
                    is Acceptance.Confirmed -> { confirmations++; broadcast(emptyList(), confirmed = result.event) }
                }
            }, { refuse(p, action, it.message ?: "refused") })
            is GameAction.EngineSeen -> {
                val after = MainLine.after(server.history, server.head, server.heads.getValue(p))
                if (after == null || after.none { it.id == action.eventId }) refuse(p, action, "stale")
                else { server.seen(p, action.eventId); broadcast(emptyList()) }
            }
            is GameAction.EngineRewind -> when {
                server.request != null -> refuse(p, action, "rewindRequested")
                MainLine.after(server.history, server.head, action.target).isNullOrEmpty() -> refuse(p, action, "inapplicable")
                server.rewindNeeds(p, action.target) != null -> refuse(p, action, "requestNeeded")
                else -> rewind(p, action.target)
            }
            is GameAction.EngineRequestRewind ->
                if (server.request != null || MainLine.after(server.history, server.head, action.target).isNullOrEmpty()) refuse(p, action, "inapplicable")
                else { server.request = EngineRewindRequest(p.value, action.target, action.message); broadcast(emptyList()) }
            is GameAction.EngineAnswerRewind -> {
                val r = server.request
                if (r == null || r.by == p.value) refuse(p, action, "inapplicable")
                else if (action.accept) rewind(PlayerId(r.by), r.target)
                else { server.request = null; broadcast(emptyList()) }
            }
            GameAction.EngineCancelRewind ->
                if (server.request?.by != p.value) refuse(p, action, "accessDenied") else { server.request = null; broadcast(emptyList()) }
            is GameAction.EngineSettings -> settings[p] = action.settings
            else -> error("not an engine action: $action")
        }
    }

    private fun rewind(p: PlayerId, target: String) {
        rewinds++
        val events = server.rewindAll(p, target)
        broadcast(events, target = target)
    }

    /** Delivers [p]'s next [count] messages (all when null), in order. */
    fun deliver(p: PlayerId, count: Int? = null) {
        val queue = inbox.getValue(p)
        var n = 0
        while (queue.isNotEmpty() && (count == null || n < count)) {
            when (val d = queue.removeFirst()) {
                is Delivery.Revision -> device(p).revision(d.events, d.update)
                is Delivery.Refusal -> device(p).refused(d.action, d.reason)
            }
            n++
        }
    }

    fun pendingDeliveries(p: PlayerId) = inbox.getValue(p).size

    /** Both devices advanced, flushed and delivered until nothing moves (live play with prompt delivery; playback
     * plays on to each device's next decision point, as the UI does). */
    fun settle(rounds: Int = 50, only: List<PlayerId> = listOf(alice, bob)) {
        repeat(rounds) {
            var moved = false
            for (p in only) {
                if (!device(p).catchUp && device(p).playToStop().isNotEmpty()) moved = true
                device(p).advance(now)
                val out = device(p).take(now)
                if (out.isNotEmpty()) moved = true
                for (a in out) handle(p, a)
            }
            for (p in only) if (pendingDeliveries(p) > 0) { moved = true; deliver(p) }
            if (!moved) return
        }
    }

    /** [p]'s keep, or the first answer listed for [p]'s open decision. */
    fun keep(p: PlayerId): Input {
        val s = device(p).model.engine
        return Engine.options(s, p).first { it is Input.SetupAnswer && it.answer == Answer.Option("Keep") ||
            it is Input.Answer && it.answer == Answer.Option("Keep") }
    }

    /** Keeps answering [player]'s own decisions of a flow with a random listed answer. */
    fun flow(player: PlayerId, rnd: Random): (GameModel) -> Input? = { model ->
        val d = model.engine.pending?.decision
        if (d == null || d is Decision.Random || Engine.decider(model.engine, d.player) != player) null
        else Engine.options(model.engine, player).filterIsInstance<Input.Answer>().randomOrNull(rnd)
    }
}
