package org.asyncttwiz.android

import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.EngineRewindRequest
import org.asyncttwiz.protocol.EngineSnapshot
import org.asyncttwiz.protocol.EngineUpdate
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameRevision
import org.asyncttwiz.protocol.GameScope
import org.asyncttwiz.protocol.game.Acceptance
import org.asyncttwiz.protocol.game.EventBuilder
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.GameHistory
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.InMemoryHistory
import org.asyncttwiz.protocol.game.InformationRule
import org.asyncttwiz.protocol.game.LiveState
import org.asyncttwiz.protocol.game.MainLine
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.Refusal
import org.asyncttwiz.protocol.game.Rewind

/**
 * I-D56 step D: two of the app's engine games ([EngineGame], the layer the table talks to) and one simulated server
 * holding the game as the server does (the main line with checkpoints, local heads, the line counter, the open undo
 * request; [Acceptance] and [Rewind] decide), exchanging only what the app and the server exchange: each game's outbox
 * ([EngineGame.take]) goes to the server in order, and each game gets the server's [GameRevision]s and its own refusals
 * in order when [deliver]ed. A player who is away gets nothing and loads the game again when they come back
 * ([load], the catch-up).
 *
 * The leak oracle runs on every event a game shows or sends: while the player's line is tentative on the server, nothing
 * shown may reveal a card they couldn't see before (I-D56 §5).
 */
internal class EngineAppSim(val f: EngineGameFixture) {
    private val players = listOf(f.a, f.b)
    val store = InMemoryHistory(f.snapshot().rootId, f.rootState)
    val history = GameHistory(store, f.codec, 16)
    var head: String = f.start.id
        private set
    var lines = 0
        private set
    var request: EngineRewindRequest? = null
        private set
    val heads = mutableMapOf<String, String>()
    private var warm: LiveState? = null
    val games = mutableMapOf<String, EngineGame>()
    private val inbox = players.associateWith { ArrayDeque<Delivery>() }
    val settings = players.associateWith { PlaySettings() }.toMutableMap()
    var now = 0L
    private var revision = 1
    private var ids = 0
    var branches = 0; var confirmations = 0; var rewinds = 0; var requests = 0; var shownChecked = 0
    val leaks = mutableListOf<String>()

    sealed interface Delivery {
        data class Revision(val message: GameRevision) : Delivery
        data class Refused(val action: GameAction, val reason: String) : Delivery
    }

    init {
        val model = f.codec.decodeModel(f.snapshot().state)
        store.add(f.start, history.checkpointFor(store.depthOfParent(f.start) + 1, model))
        warm = LiveState(head, model)
        for (p in players) heads[p] = f.start.id
    }

    fun other(p: String) = if (p == f.a) f.b else f.a
    fun game(p: String): EngineGame = games.getValue(p)
    fun state(): GameModel = warm?.takeIf { it.at == head }?.model?.copy() ?: history.stateAt(head)
    fun line(): List<GameEvent> = MainLine.after(history, head, store.rootId)!!

    /** [p]'s game opened (or opened again) from the server's snapshot: at their local head, the rest buffered. */
    fun load(p: String): EngineGame {
        val baseId = heads.values.minByOrNull { history.depthOf(it) } ?: head
        val after = MainLine.after(history, head, baseId)!!
        val snapshot = EngineSnapshot(f.codec.encodeDefinitions(), store.rootId, head, f.codec.encodeModel(state()), heads.toMap(),
            settings[p], request, lines, baseId.takeIf { after.isNotEmpty() },
            if (after.isEmpty()) null else f.codec.encodeModel(history.stateAt(baseId)), after.map(f.codec::encodeEvent))
        val game = games[p]?.also { it.load(snapshot) } ?: EngineGame(f.game.id, p, f.base, snapshot) { "$p-${++ids}" }
        games[p] = game
        inbox.getValue(p).clear()
        watch(game)
        return game
    }

    private fun watch(game: EngineGame) {
        game.device.session.beforeShow = { before, event -> check(game, before, event) }
        game.device.session.onSent = { before, event -> check(game, before, event) }
    }

    private fun check(game: EngineGame, before: arcanum.engine.state.GameState, event: GameEvent) {
        shownChecked++
        val me = PlayerId(game.me)
        val other = heads[other(game.me)] ?: return
        val tentative = (MainLine.after(history, head, other).orEmpty() + game.device.session.pending)
            .any { it.assumed?.assumedBy == me && it.id != event.id } || event.assumed?.assumedBy == me
        if (!tentative) return
        val shown = InformationRule.revealing(before, event.engine?.changes.orEmpty(), me)
        if (shown.isNotEmpty()) leaks += "${game.me} shown $shown by ${event.id}"
    }

    /** Sends [p]'s game's outbox to the server, in order. */
    fun flush(p: String) { for (action in game(p).take(now)) handle(p, action) }

    fun pending(p: String) = inbox.getValue(p).size

    /** Delivers [p]'s next [count] messages (all when null), in order. */
    fun deliver(p: String, count: Int? = null) {
        val queue = inbox.getValue(p)
        var n = 0
        while (queue.isNotEmpty() && (count == null || n++ < count)) when (val d = queue.removeFirst()) {
            is Delivery.Revision -> game(p).revision(d.message)
            is Delivery.Refused -> game(p).refused(d.action, d.reason)
        }
    }

    private fun broadcast(events: List<GameEvent>, author: String, target: String? = null, confirmed: String? = null) {
        val update = EngineUpdate(events.drop(1).map(f.codec::encodeEvent), target, heads.toMap(), lines, request, confirmed)
        val n = revision++
        for (p in players) inbox.getValue(p) += Delivery.Revision(GameRevision(f.game.id, "$n", "${n + 1}", DeviceRandom.uuid(), author,
            GameScope(f.game.id, playId = f.base.id), f.game, engineEvent = events.firstOrNull()?.let(f.codec::encodeEvent), engine = update))
    }

    private fun refuse(p: String, action: GameAction, reason: String) { inbox.getValue(p) += Delivery.Refused(action, reason) }

    private fun handle(p: String, action: GameAction) {
        val me = PlayerId(p)
        when (action) {
            is GameAction.Submit -> try {
                val oldHead = head
                when (val result = Acceptance.accept(history, head, warm, action.submission, me, heads.mapKeys { PlayerId(it.key) },
                    lines + 1, rewindOpen = request != null)) {
                    is Acceptance.Accepted -> {
                        val event = result.event
                        if (result.discarded != null) {
                            branches++
                            val gone = MainLine.after(history, oldHead, event.parentId)!!.map { it.id }.toSet()
                            heads.replaceAll { _, h -> if (h in gone) event.parentId else h }
                        }
                        event.startsLine?.let { lines = it }
                        store.add(event, history.checkpointFor(store.depthOfParent(event) + 1, result.live.model))
                        head = event.id
                        warm = result.live
                        if (result.authorHead) heads[p] = event.id
                        request = null
                        broadcast(listOf(event), p)
                    }
                    is Acceptance.Confirmed -> { confirmations++; heads[p] = result.event; broadcast(emptyList(), p, confirmed = result.event) }
                }
            } catch (refusal: Refusal) { warm = null; refuse(p, action, refusal.reason) }
            is GameAction.EngineSeen -> {
                val after = MainLine.after(history, head, heads.getValue(p))
                if (after == null || after.none { it.id == action.eventId }) refuse(p, action, "stale")
                else { heads[p] = action.eventId; broadcast(emptyList(), p) }
            }
            is GameAction.EngineRewind -> when {
                request != null -> refuse(p, action, "rewindRequested")
                MainLine.after(history, head, action.target).isNullOrEmpty() -> refuse(p, action, "inapplicable")
                Rewind.requestNeeded(history.stateAt(action.target), action.target, MainLine.after(history, head, action.target)!!, me,
                    heads.filterKeys { it != p }.values.toList()) != null -> refuse(p, action, "requestNeeded")
                else -> rewind(p, action.target)
            }
            is GameAction.EngineRequestRewind ->
                if (request != null || MainLine.after(history, head, action.target).isNullOrEmpty()) refuse(p, action, "inapplicable")
                else { requests++; request = EngineRewindRequest(p, action.target, action.message); broadcast(emptyList(), p) }
            is GameAction.EngineAnswerRewind -> {
                val r = request
                if (r == null || r.by == p) refuse(p, action, "inapplicable")
                else if (action.accept) rewind(r.by, r.target)
                else { request = null; broadcast(emptyList(), p) }
            }
            GameAction.EngineCancelRewind ->
                if (request?.by != p) refuse(p, action, "accessDenied") else { request = null; broadcast(emptyList(), p) }
            is GameAction.EngineSettings -> settings[p] = action.settings
            else -> error("not an engine action: $action")
        }
    }

    /** The server's rewind (I-D56 §10): the events after [target] go, a new line starts there, the other player's
     * table-only events that may stand are added again, local heads past the target move to the new line. */
    private fun rewind(p: String, target: String) {
        rewinds++
        val after = MainLine.after(history, head, target)!!
        val standing = Rewind.standing(after, PlayerId(p))
        val gone = after.map { it.id }.toSet()
        store.remove(gone)
        val model = history.stateAt(target)
        lines++
        val events = mutableListOf(EventBuilder(model, "line$lines", target, p).also { it.startLine(lines) }.build())
        add(events.last(), model)
        for ((n, kept) in standing.withIndex()) {
            val again = EventBuilder(model, "line${lines}s$n", events.last().id, kept.author)
            try { kept.table.forEach(again::table) } catch (_: IllegalStateException) { again.abandon(); continue }
            events += again.build()
            add(events.last(), model)
        }
        heads.replaceAll { _, h -> if (h in gone || h == target) events.last().id else h }
        request = null
        broadcast(events, p, target = target)
    }

    private fun add(event: GameEvent, model: GameModel) {
        store.add(event, history.checkpointFor(store.depthOfParent(event) + 1, model))
        head = event.id
        warm = LiveState(event.id, model)
    }
}
