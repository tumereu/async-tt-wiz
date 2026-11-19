package org.asyncttwiz.protocol.game

import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.setup.StartOptions
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.replaceEach

/**
 * A server holding one game in memory as the server does (I-D54 phase 3, I-D56 step A): the tree of events with
 * checkpoints, the main line's head, each player's local head, the line counter and the warm state; submissions go
 * through [Acceptance], rewinds through [Rewind]. Devices make their events on copies of the states they show.
 */
internal class AsyncServer(val games: EngineGames, every: Int = 16) {
    val store = InMemoryHistory("root", games.codec.encodeModel(initial(games)))
    val history = GameHistory(store, games.codec, every)
    var head = "root"
        private set
    var lines = 0
        private set
    val heads = mutableMapOf<PlayerId, String>()
    /** The open undo request (I-D56 §10): only a concession is taken while it is. */
    var request: org.asyncttwiz.protocol.EngineRewindRequest? = null
    private var warm: LiveState? = null
    /** Submissions dropped as duplicates of main-line events, by id ([Acceptance.Confirmed.alias]), as the server keeps them. */
    val aliases = mutableMapOf<String, String>()
    /** The game's assume reveal settings (LOB-135) the information rule honours; nothing relaxed unless a test says. */
    var reveals: org.asyncttwiz.protocol.AssumeReveals = org.asyncttwiz.protocol.AssumeReveals.NONE
    /** The players on Quick now (SYS-063): any device may answer their one-answer questions; none unless a test says. */
    var quick: Set<PlayerId> = emptySet()
    private var ids = 0
    val alice get() = games.alice
    val bob get() = games.bob

    /** The game's start, made by the server itself: both local heads at it (each player is shown their opening);
     * [handSizes]: opening hands other than seven cards. */
    fun start(starter: PlayerId = alice, handSizes: Map<PlayerId, Int> = emptyMap(), freeFirstMulligan: Boolean? = null): GameEvent {
        val model = history.stateAt("root")
        val builder = EventBuilder(model, "start", "root", starter.value)
        builder.perform(Input.StartGame(StartOptions(startingPlayer = starter, simultaneousMulligans = true, handSizes = handSizes, freeFirstMulligan = freeFirstMulligan)))
        builder.answerRandoms()
        val event = builder.build()
        add(event, model)
        heads[alice] = event.id; heads[bob] = event.id
        return event
    }

    private fun add(event: GameEvent, model: GameModel) {
        store.add(event, history.checkpointFor(store.depthOfParent(event) + 1, model))
        head = event.id
        warm = LiveState(event.id, model)
    }

    /** Takes [submission] by [author]: the result, or the refusal. Branches move the local heads on the discarded line
     * back to the branch point. */
    fun submit(submission: Submission, author: PlayerId): Result<Acceptance.Result> = try {
        val oldHead = head
        val result = Acceptance.accept(history, head, warm, submission, author, heads, lines + 1, rewindOpen = request != null,
            aliases = aliases, reveals = reveals, ids = { "r${++ids}" }, quick = quick)
        when (result) {
            is Acceptance.Reapplied -> {
                // As the server: the event and the re-made line after it; local heads on the old line go to the events made
                // from theirs, or back to the branch point when theirs was taken back (HIS-115, HIS-116)
                val event = result.event
                val gone = MainLine.after(history, oldHead, event.parentId)!!.map { it.id }.toSet()
                val made = listOf(event) + result.reapply.events
                var model = history.stateAt(event.parentId)
                for (e in made) {
                    model.apply(e)
                    store.add(e, history.checkpointFor(store.depthOfParent(e) + 1, model))
                }
                head = made.last().id
                warm = result.live
                heads[author] = event.id
                val carried = result.reapply.events.associateBy { it.carriedFrom }
                heads.replaceEach { _, h -> if (h in gone) carried[h]?.id ?: head else h }
                if (result.reapply.stoppedAt != null) lines = result.nextLine
                request = null
            }
            is Acceptance.Accepted -> {
                val event = result.event
                if (result.discarded != null) {
                    val gone = MainLine.after(history, oldHead, event.parentId)!!.map { it.id }.toSet()
                    heads.replaceEach { _, h -> if (h in gone) event.parentId else h }
                }
                event.startsLine?.let { lines = it }
                store.add(event, history.checkpointFor(store.depthOfParent(event) + 1, result.live.model))
                head = event.id
                warm = result.live
                if (result.authorHead) heads[author] = event.id
                // A concession ends an open undo request
                request = null
            }
            is Acceptance.Confirmed -> {
                heads[author] = result.event
                result.alias?.let { aliases[it] = result.event }
            }
        }
        Result.success(result)
    } catch (refusal: Refusal) {
        // As the server: the state the refusal worked on stays warm, moved back to the head
        warm = refusal.state?.let { LiveState(head, history.move(it.model, it.at, head)) }
        Result.failure(refusal)
    }

    /** [player]'s device showed the main line up to [id]. */
    fun seen(player: PlayerId, id: String) {
        val after = MainLine.after(history, head, heads.getValue(player))!!
        check(id == heads[player] || after.any { it.id == id }) { "$id isn't ahead of ${player.value}'s local head" }
        heads[player] = id
    }

    /** [player]'s next stop with [mode]. */
    fun nextStop(player: PlayerId, mode: ResponseMode = ResponseMode.AutoSkip): NextStop.Stop? {
        val local = heads.getValue(player)
        return NextStop.of(stateAt(local), local, MainLine.after(history, head, local)!!, player, mode)
    }

    /** Why [player]'s rewind to [target] needs a request, or null when it is free. */
    fun rewindNeeds(player: PlayerId, target: String, passes: Boolean = false): String? {
        val after = MainLine.after(history, head, target)!!
        return Rewind.requestNeeded(stateAt(target), target, after, player, heads.filterKeys { it != player }.values.toList(), passes)
    }

    /** Takes the main line back to just after [target] (free or approved), as the server does: its events go, a new
     * line is due there (the next event starts it), and local heads past the target move to the new head; the other
     * players' table-only events that may stand are added again (the first starting the line). Closes an open undo
     * request. Returns the events added. */
    /** What the last [rewind] made again when it re-applied the other player's line (HIS-115), else null. */
    var rewound: Rewind.Reapplied? = null
        private set

    fun rewind(player: PlayerId, target: String): List<GameEvent> {
        val after = MainLine.after(history, head, target)!!
        val gone = after.map { it.id }.toSet()
        val at = Rewind.reapplies(history.stateAt(target), after, player)
        store.remove(gone)
        val model = history.stateAt(target)
        lines++
        head = target
        warm = LiveState(target, model)
        var n = 0
        rewound = null
        if (at != null) {
            // HIS-115: inside the player's opening, before the other player's assumed keep about them: that line is made again
            val r = Rewind.reapply(model, target, after, at, player, lines, { "line${lines}s${n++}" }) { add(it, model) }
            val states = r.atBase
            for (e in r.reapply.events) {
                states.apply(e)
                store.add(e, history.checkpointFor(store.depthOfParent(e) + 1, states))
                head = e.id
            }
            warm = LiveState(head, model)
            val carried = r.reapply.events.associateBy { it.carriedFrom }
            heads.replaceEach { p, h -> when {
                p == player -> if (h in gone || h == target) r.base else h
                h in gone -> carried[h]?.id ?: head
                else -> h
            } }
            request = null
            rewound = r
            return r.standing + r.reapply.events
        }
        val events = Rewind.again(model, target, after, player, lines, { "line${lines}s${n++}" }) { add(it, model) }
        heads.replaceEach { _, h -> if (h in gone || h == target) head else h }
        request = null
        return events
    }

    fun state(): GameModel = warm?.takeIf { it.at == head }?.model?.copy() ?: history.stateAt(head)
    fun stateAt(id: String): GameModel = history.stateAt(id)
    fun line(): List<GameEvent> = MainLine.after(history, head, "root")!!

    // ---- Devices

    /**
     * What a device makes for [author] on [model] at [parent] (the device's own copy is used): [input] (assumed when
     * [assumed]), then [follow]'s further inputs of the author's own flow (a cast's choices), then the line's random
     * answers; [newLine] starts a line first (a branch).
     */
    fun make(model: GameModel, parent: String, author: PlayerId, input: Input, assumed: Boolean = false, newLine: Int? = null,
        follow: (GameModel) -> Input? = { null }): Pair<Submission, GameEvent> {
        val builder = EventBuilder(model, "e${++ids}", parent, author.value)
        newLine?.let(builder::startLine)
        builder.perform(input, assumedBy = if (assumed) author else null)
        builder.answerRandoms()
        while (true) {
            val next = follow(model) ?: break
            builder.perform(next)
            builder.answerRandoms()
        }
        val event = builder.build()
        return Submission.of(event) to event
    }

    /** [author]'s own input at the head, made on a copy of the head's state and submitted. */
    fun act(author: PlayerId, input: Input, assumed: Boolean = false, follow: (GameModel) -> Input? = { null }): Result<Acceptance.Result> {
        // A device's engine refuses what the server's would: nothing is sent
        val model = state()
        val (submission, made) = try { make(model, head, author, input, assumed, newLine = Rewind.lineDue(model, lines), follow = follow) }
            catch (e: IllegalArgumentException) { return Result.failure(e) } catch (e: IllegalStateException) { return Result.failure(e) }
        return submit(submission, author).also { r ->
            val accepted = r.getOrNull() as? Acceptance.Accepted
            if (accepted != null) check(accepted.event == made) { "the server made another event than the device" }
        }
    }

    /** The open decision at the head. */
    fun pending(): Decision? = state().engine.pending?.decision

    /** Keeps answering [player]'s own decisions of a flow with the first answer listed ([choose] picks otherwise). */
    fun flow(player: PlayerId, choose: (Decision, List<Input.Answer>) -> Input.Answer? = { _, a -> a.firstOrNull() }): (GameModel) -> Input? = { model ->
        val d = model.engine.pending?.decision
        if (d == null || d is Decision.Random || Host.decider(model.engine, d.player) != player) null
        else choose(d, Engine.options(model.engine, player, org.asyncttwiz.protocol.game.EnginePreferences.of(model.engine)).filterIsInstance<Input.Answer>())
    }

    /** Plays the opening: [starter] keeps, then assumes the other player's keep (I-D56 §3, kind 3). */
    fun keepAndAssumeKeep(starter: PlayerId) {
        val other = if (starter == alice) bob else alice
        act(starter, Input.Answer(pending()!!.id, Answer.Option("Keep"))).getOrThrow()
        act(starter, Input.Answer(pending()!!.id, Answer.Option("Keep")), assumed = true).getOrThrow()
        check(state().engine.turn.number == 1) { "the first turn began" }
        check(other != starter)
    }

    companion object {
        /** The game before it starts: every player's "Skip when I have no choice" off in the shared state (I-D56 §13),
         * the game's secret in the table state. */
        fun initial(games: EngineGames): GameModel = games.model()
    }
}
