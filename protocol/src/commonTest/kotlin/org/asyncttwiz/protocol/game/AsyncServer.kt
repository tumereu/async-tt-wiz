package org.asyncttwiz.protocol.game

import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.setup.StartOptions
import arcanum.engine.state.PlayerId

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
    private var ids = 0
    val alice get() = games.alice
    val bob get() = games.bob

    /** The game's start, made by the server itself: both local heads at it (each player is shown their opening). */
    fun start(starter: PlayerId = alice): GameEvent {
        val model = history.stateAt("root")
        val builder = EventBuilder(model, "start", "root", starter.value)
        builder.perform(Input.StartGame(StartOptions(startingPlayer = starter, simultaneousMulligans = true)))
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
        val result = Acceptance.accept(history, head, warm, submission, author, heads, lines + 1, rewindOpen = request != null)
        when (result) {
            is Acceptance.Accepted -> {
                val event = result.event
                if (result.discarded != null) {
                    val gone = MainLine.after(history, oldHead, event.parentId)!!.map { it.id }.toSet()
                    heads.replaceAll { _, h -> if (h in gone) event.parentId else h }
                }
                event.startsLine?.let { lines = it }
                store.add(event, history.checkpointFor(store.depthOfParent(event) + 1, result.live.model))
                head = event.id
                warm = result.live
                if (result.authorHead) heads[author] = event.id
                // A concession ends an open undo request
                request = null
            }
            is Acceptance.Confirmed -> heads[author] = result.event
        }
        Result.success(result)
    } catch (refusal: Refusal) {
        warm = null
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
    fun rewindNeeds(player: PlayerId, target: String): String? {
        val after = MainLine.after(history, head, target)!!
        return Rewind.requestNeeded(stateAt(target), target, after, player, heads.filterKeys { it != player }.values.toList())
    }

    /** Takes the main line back to just after [target] (free or approved): its events go, a new line starts there with
     * a reshuffle of the unknown library parts, and local heads past the target move to the new line's first event. */
    fun rewind(player: PlayerId, target: String): GameEvent = rewindAll(player, target).first()

    /** [rewind]'s events: the new line's first, then the other players' table-only events added again while they apply
     * (as the server does); local heads past the target move to the last. Closes an open undo request. */
    fun rewindAll(player: PlayerId, target: String): List<GameEvent> {
        val after = MainLine.after(history, head, target)!!
        val standing = Rewind.standing(after, player)
        val gone = after.map { it.id }.toSet()
        store.remove(gone)
        val model = history.stateAt(target)
        lines++
        val builder = EventBuilder(model, "line$lines", target, player.value)
        builder.startLine(lines)
        val events = mutableListOf(builder.build())
        add(events.last(), model)
        for ((n, kept) in standing.withIndex()) {
            val again = EventBuilder(model, "line${lines}s$n", events.last().id, kept.author)
            try { kept.table.forEach(again::table) } catch (_: IllegalStateException) { again.abandon(); continue }
            events += again.build()
            add(events.last(), model)
        }
        heads.replaceAll { _, h -> if (h in gone || h == target) events.last().id else h }
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
        val (submission, made) = try { make(state(), head, author, input, assumed, follow = follow) }
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
        if (d == null || d is Decision.Random || Engine.decider(model.engine, d.player) != player) null
        else choose(d, Engine.options(model.engine, player).filterIsInstance<Input.Answer>())
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
        fun initial(games: EngineGames): GameModel {
            val model = games.model()
            for (p in model.engine.players) model.engine.apply(arcanum.engine.change.Changes.skipWhenNoChoice(model.engine, p.id, false))
            return model
        }
    }
}
