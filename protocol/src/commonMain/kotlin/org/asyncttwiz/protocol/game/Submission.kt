package org.asyncttwiz.protocol.game

import arcanum.engine.Engine
import arcanum.engine.change.Change
import arcanum.engine.change.Changes
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.RandomKind
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import kotlin.random.Random
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One acting point as a device sends it (I-D54 §3: inputs, not results): the new event's [id], the event it was made on
 * ([parentId]), what the engine is to do ([acts], in order: the player's input, the device's random answers that
 * followed it, passes the device made for players) and the event's [table] changes. The server makes the event from it
 * ([Authority.make]) on the state at [parentId] and gets the same event the device made, since the engine is
 * deterministic and the random results come with the inputs.
 */
@Serializable
data class Submission(val id: String, val parentId: String, val acts: List<Act> = emptyList(), val table: List<TableChange> = emptyList()) {
    companion object {
        /** What a device sends for [event], an event it made itself ([EventBuilder]). */
        fun of(event: GameEvent): Submission = Submission(event.id, event.parentId, event.engine?.steps.orEmpty().flatMap { step ->
            step.input?.let { listOf(Act.Perform(it)) }
                ?: step.changes.map { change ->
                    check(change is Change.SkipWhenNoChoice) { "A host step holds only setting changes, not $change" }
                    Act.SkipWhenNoChoice(change.player, change.after)
                }
        }, event.table)
    }
}

/** One thing a [Submission] asks the engine to do. */
@Serializable
sealed interface Act {
    /** Perform [input] ([Engine.perform]). */
    @Serializable @SerialName("input")
    data class Perform(val input: Input) : Act

    /** [player]'s "Skip when I have no choice" set to [on] (engine state the engine never changes itself, I-D41). */
    @Serializable @SerialName("skip-when-no-choice")
    data class SkipWhenNoChoice(val player: PlayerId, val on: Boolean) : Act
}

/** Why a [Submission] was refused: the input or table change it names isn't possible on the state it was made on. */
class Refusal(val reason: String) : RuntimeException(reason)

/**
 * The server's gate (I-D54 §3): a submission is accepted when every engine input is one the engine accepts on the state
 * (the engine is the only rules check, so no ATW rules gate is left) and every table change fits the table and is the
 * author's to make. Each player acts only for themselves (I-D55): an input naming a player, or answering a player's
 * decision, is the author's only when they are its decider ([Engine.decider], CR 723); the host's pass for a player
 * whose own setting passes now ([Engine.shouldPassFor]) and the host's random answers and fills are anyone's. Also
 * the player's own: conceding (CR 104.3a), their settings and early answers, their result suggestion, and accepting a
 * result the other player suggested (an [Input.AgreedResult] only answers the other player's [TableState.suggestion]
 * of that result). Random results come from the device and are taken as given (owner, 2026-11-03).
 */
object Authority {
    /**
     * Makes the event [submission] describes, by [author], on [model] (the state at its parent): on success [model] is
     * the event's state; refused ([Refusal]) it is as it was.
     */
    fun make(model: GameModel, submission: Submission, author: PlayerId): GameEvent {
        val builder = EventBuilder(model, submission.id, submission.parentId, author.value)
        try {
            val suggestion = model.table.suggestion
            for (act in submission.acts) when (act) {
                is Act.Perform -> {
                    allowed(act.input, author, suggestion, model.engine)
                    try { builder.perform(act.input) }
                    catch (e: IllegalArgumentException) { throw Refusal("Refused by the engine: ${e.message}") }
                    catch (e: IllegalStateException) { throw Refusal("Refused by the engine: ${e.message}") }
                }
                is Act.SkipWhenNoChoice -> {
                    if (act.player != author) throw Refusal("Only ${act.player} changes their own settings")
                    if (model.engine.players.none { it.id == act.player }) throw Refusal("${act.player} isn't in the game")
                    builder.host(listOf(Changes.skipWhenNoChoice(model.engine, act.player, act.on)))
                }
            }
            for (change in submission.table) {
                allowed(change, author, model)
                try { builder.table(change) } catch (e: IllegalStateException) { throw Refusal("Table change doesn't fit: ${e.message}") }
            }
            if (submission.acts.isEmpty() && submission.table.isEmpty()) throw Refusal("An empty submission")
            return builder.build()
        } catch (refusal: Refusal) {
            builder.abandon()
            throw refusal
        }
    }

    /** Whether [author] may make [input] on [state] (I-D55), the engine's own check aside: what a device offers its
     * player of [Engine.options], which lists answers to every player. */
    fun mayMake(state: GameState, input: Input, author: PlayerId, suggestion: ResultSuggestion? = null): Boolean =
        try { allowed(input, author, suggestion, state); true } catch (_: Refusal) { false }

    /** I-D55: [author] makes [input] on [state] only as the player who decides it ([Engine.decider], CR 723). */
    private fun allowed(input: Input, author: PlayerId, suggestion: ResultSuggestion?, state: GameState) {
        fun own(player: PlayerId) {
            if (Engine.decider(state, player) != author) throw Refusal("Only ${Engine.decider(state, player)} acts for $player (I-D55)")
        }
        when (input) {
            is Input.Concede -> if (input.player != author) throw Refusal("Only ${input.player} concedes for themselves (CR 104.3a, 723.6)")
            is Input.AgreedResult -> if (suggestion == null || suggestion.result != input.result || suggestion.by == author)
                throw Refusal("A result is agreed by accepting the other player's suggestion of it")
            // The host's pass for a player whose own setting passes now (Engine.shouldPassFor): no choice of anyone's
            is Input.PassPriority -> if (!Engine.shouldPassFor(state, input.player)) own(input.player)
            is Input.PlayLand -> own(input.player)
            is Input.Cast -> own(input.player)
            is Input.Activate -> own(input.player)
            is Input.DeclareAttackers -> own(input.player)
            is Input.DeclareBlockers -> own(input.player)
            is Input.Cancel -> own(input.player)
            is Input.SetupAnswer -> own(input.player)
            // Random results and fills are the host's (D25, D60): the device that made the event answers them
            is Input.Answer -> when (val d = state.pending?.decision) {
                null, is Decision.Random, is Decision.HostFill -> {}
                else -> own(d.player)
            }
            is Input.StartGame, Input.MandatoryLoop -> {}
        }
    }

    private fun allowed(change: TableChange, author: PlayerId, model: GameModel) {
        val own: PlayerId? = when (change) {
            is TableChange.Settings -> change.player
            is TableChange.EarlyAnswered -> (change.after ?: change.before)?.player
            // Anyone may take a suggestion back or decline it; only its author makes it
            is TableChange.Suggestion -> change.after?.by
            is TableChange.ResultConfirmed -> {
                if (change.after != null && Engine.result(model.engine) == null) throw Refusal("No result to confirm")
                change.after
            }
            is TableChange.Placed, is TableChange.AttackDraft, is TableChange.BlockDraft -> null
        }
        if (own != null && own != author) throw Refusal("Only $own makes this change")
    }
}

/** A game's state [model] at event [at], kept between submissions (the server keeps active games warm, I-D54 §4). */
class LiveState(val at: String, val model: GameModel)

/**
 * The server's order (I-D54 §3): submissions are taken in arrival order, each on the live line's last event [head]; one
 * made on another event is refused as stale (another player's came first; the device resyncs when that event reaches
 * it), unless it is a deliberate [branch] from an event of the tree. The state at the parent is [warm]'s when it is
 * there or near ([GameHistory.move]), else a checkpoint and the events after it ([GameHistory.stateAt]).
 */
object Acceptance {
    /** The accepted [event] and the state after it ([live]). */
    class Accepted(val event: GameEvent, val live: LiveState)

    /** Accepts [submission]; refused ([Refusal]), [warm] is no longer usable. */
    fun accept(history: GameHistory, head: String, warm: LiveState?, submission: Submission, author: PlayerId, branch: Boolean): Accepted {
        val parent = submission.parentId
        if (!branch && parent != head) throw Refusal("stale")
        if (parent != history.store.rootId && history.store.node(parent) == null) throw Refusal("No event $parent")
        if (submission.id == history.store.rootId || history.store.node(submission.id) != null) throw Refusal("Event ${submission.id} exists")
        val model = when {
            warm == null -> history.stateAt(parent)
            else -> history.move(warm.model, warm.at, parent)
        }
        val event = Authority.make(model, submission, author)
        return Accepted(event, LiveState(event.id, model))
    }
}

/**
 * The random results a device makes for the engine's [Decision.Random] decisions (I-D54 §3, D25: shuffles, coin flips,
 * die rolls, random choices), answered on [builder] until none is open; with full information the engine keeps ids.
 */
fun EventBuilder.answerRandoms(model: GameModel, random: Random) {
    while (true) {
        val d = model.engine.pending?.decision as? Decision.Random ?: return
        perform(Input.Answer(d.id, randomAnswer(d.what, random)))
    }
}

/** A random result of [kind] from [random]. */
fun randomAnswer(kind: RandomKind, random: Random): Answer = when (kind) {
    RandomKind.CoinFlip -> Answer.CoinFlip(random.nextBoolean())
    is RandomKind.Die -> Answer.DieRolls(List(kind.count) { random.nextInt(1, kind.sides + 1) })
    is RandomKind.Objects -> Answer.RandomObjects(kind.among.shuffled(random).take(kind.count))
}
