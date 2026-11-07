package org.asyncttwiz.protocol.game

import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One acting point as a device sends it (I-D54 §3: inputs, not results): the new event's [id], the event it was made on
 * ([parentId]), what the engine is to do ([acts], in order: a new line's start when it branches, the player's input, the
 * random answers from the line's stream that followed it, engine-automatic answers) and the event's [table] changes. The
 * server makes the event from it ([Authority.make]) on the state at [parentId] and gets the same event the device made,
 * since the engine is deterministic and the random answers come from the line's stream (I-D56 §9).
 */
@Serializable
data class Submission(val id: String, val parentId: String, val acts: List<Act> = emptyList(), val table: List<TableChange> = emptyList(),
    /** The response mode the event records ([GameEvent.mode]). */
    val mode: ResponseMode? = null) {
    companion object {
        /** What a device sends for [event], an event it made itself ([EventBuilder]). */
        fun of(event: GameEvent): Submission = Submission(event.id, event.parentId,
            listOfNotNull(event.startsLine?.let { Act.NewLine(it) }) + event.engine?.steps.orEmpty().mapNotNull { step ->
                step.input?.let { Act.Perform(it, assumed = step.assumedBy != null) }
            }, event.table.filter { it !is TableChange.LineMoved }, event.mode)
    }
}

/** One thing a [Submission] asks the engine to do. */
@Serializable
sealed interface Act {
    /** Perform [input] ([Engine.perform]); [assumed]: the other player's input, assumed by the author (I-D56 §3). */
    @Serializable @SerialName("input")
    data class Perform(val input: Input, val assumed: Boolean = false) : Act

    /** The event starts line [number] (I-D56 §9: a branch at a mismatch, §8): the first act of a submission made on an
     * event other than the head; [number] is the play's next line. */
    @Serializable @SerialName("new-line")
    data class NewLine(val number: Int) : Act
}

/** Why a [Submission] was refused: the input or table change it names isn't possible on the state it was made on.
 * [reason] is short and stable ("stale", "revealsHidden", …) or says what the engine refused. */
class Refusal(val reason: String) : RuntimeException(reason) {
    /** The state at hand when [Acceptance] refused (the warm state, moved, or one it loaded), so the server keeps it
     * warm instead of loading a checkpoint for the next request; null when it used none. */
    var state: LiveState? = null
}

/**
 * The server's gate (I-D54 §3): a submission is accepted when every engine input is one the engine accepts on the state
 * (the engine is the only rules check), every input is one its author may make ([WhoActs], I-D56 §6), every random
 * answer is the line's ([LineRandom]) and every table change fits the table and is the author's to make. Always the
 * player's own: conceding (CR 104.3a), early answers, their result suggestion, accepting a result the other player
 * suggested (an [Input.AgreedResult] only answers the other player's [TableState.suggestion] of that result), and
 * confirming a result the engine reports.
 */
object Authority {
    /**
     * What a submission may do beyond its inputs: [nextLine], the number a new line takes here (null: none may start);
     * [tentative], the author's line is tentative (an assumption of theirs is unconfirmed), so the information rule
     * applies (I-D56 §5); [resultSeenByAll], every player's local head has reached the result the engine reports, so it
     * may be confirmed (I-D56 §11).
     */
    data class Rules(val nextLine: Int? = null, val tentative: Boolean = false, val resultSeenByAll: Boolean = true,
        /** The game's assume reveal settings (LOB-135): the cards the information rule lets through. */
        val reveals: org.asyncttwiz.protocol.AssumeReveals = org.asyncttwiz.protocol.AssumeReveals.NONE)

    /**
     * Makes the event [submission] describes, by [author], on [model] (the state at its parent): on success [model] is
     * the event's state; refused ([Refusal]) it is as it was.
     */
    fun make(model: GameModel, submission: Submission, author: PlayerId, rules: Rules = Rules()): GameEvent {
        val assumes = submission.acts.any { it is Act.Perform && it.assumed }
        var before = if (rules.tentative || assumes) InformationRule.Before.of(model) else null
        val builder = EventBuilder(model, submission.id, submission.parentId, author.value)
        builder.mode = submission.mode
        try {
            var performed = 0
            var assumed = false
            val changes = mutableListOf<Change>()
            for ((index, act) in submission.acts.withIndex()) when (act) {
                is Act.NewLine -> {
                    if (index != 0 || rules.nextLine == null) throw Refusal("A line starts only at a branch, as its first act")
                    if (act.number != rules.nextLine) throw Refusal("stale")
                    builder.startLine(act.number)
                    // The checked changes are made on the reordered libraries (the reorder itself shows nobody anything)
                    if (before != null) before = InformationRule.Before.of(model)
                }
                is Act.Perform -> {
                    val kind = WhoActs.kind(model, act.input, author, act.assumed)
                    when (kind) {
                        is WhoActs.Kind.Assumed -> if (performed > 0) throw Refusal("An assumed input begins its own event (I-D56 §13)")
                            else assumed = true
                        WhoActs.Kind.Own -> if (assumed) throw Refusal("Assumed and real inputs never share an event (I-D56 §13)")
                        WhoActs.Kind.Automatic, WhoActs.Kind.Random -> {}
                    }
                    if (kind == WhoActs.Kind.Random) {
                        val d = model.engine.pending!!.decision as Decision.Random
                        if ((act.input as Input.Answer).answer != builder.randomAnswer(d)) throw Refusal("A random answer that isn't the line's (I-D56 §9)")
                    }
                    try { changes += builder.perform(act.input, assumedBy = if (kind is WhoActs.Kind.Assumed) author else null).changes }
                    catch (e: IllegalArgumentException) { throw Refusal("Refused by the engine: ${e.message}") }
                    catch (e: IllegalStateException) { throw Refusal("Refused by the engine: ${e.message}") }
                    performed++
                }
            }
            for (change in submission.table) {
                allowed(change, author, model, rules)
                try { builder.table(change) } catch (e: IllegalStateException) { throw Refusal("Table change doesn't fit: ${e.message}") }
            }
            if (builder.empty) throw Refusal("An empty submission")
            // I-D56 §5: while the author's line is tentative, nothing may show them a card they couldn't see before
            // The model is the state after [changes] ([before] is after the line's reorder when the event starts one)
            val checked = before
            if (checked != null && InformationRule.revealing(checked, changes, author, model.view(), rules.reveals).isNotEmpty()) throw Refusal(REVEALS_HIDDEN)
            return builder.build()
        } catch (refusal: Refusal) {
            builder.abandon()
            throw refusal
        }
    }

    /** The refusal of an event that would show its author hidden cards while their line is tentative (I-D56 §5). */
    const val REVEALS_HIDDEN = "revealsHidden"

    /** Whether [author] may make [input] on [state] as their own real input ([WhoActs], the result rules), the
     * engine's own check aside: what a device offers its player of [Engine.options]. */
    fun mayMake(state: GameState, input: Input, author: PlayerId, table: TableState = TableState()): Boolean =
        try { WhoActs.kind(GameModel(state, table), input, author, assumed = false) == WhoActs.Kind.Own } catch (_: Refusal) { false }

    private fun allowed(change: TableChange, author: PlayerId, model: GameModel, rules: Rules) {
        val own: PlayerId? = when (change) {
            is TableChange.EarlyAnswered -> (change.after ?: change.before)?.player
            // Anyone may take a suggestion back or decline it; only its author makes it
            is TableChange.Suggestion -> change.after?.by
            is TableChange.ResultConfirmed -> {
                if (change.after != null && Engine.result(model.engine) == null) throw Refusal("No result to confirm")
                if (change.after != null && !rules.resultSeenByAll) throw Refusal("The result is confirmed once both players have seen it (I-D56 §11)")
                change.after
            }
            is TableChange.LineMoved -> throw Refusal("The line moves only with the event's own random answers")
            // I-D56 §6: placing your own cards on your battlefield (a place taken from an object that is gone: anyone)
            is TableChange.Placed -> {
                val obj = model.engine.obj(change.obj)
                if (change.after != null && (obj == null || change.obj !in model.engine.battlefield)) throw Refusal("Only a permanent has a place")
                obj?.controller
            }
            is TableChange.AttackDraft, is TableChange.BlockDraft -> null
        }
        if (own != null && own != author) throw Refusal("Only $own makes this change")
    }
}

/**
 * Who may submit which input (I-D56 §6), the same rules on the server and the device: a player's own real inputs where
 * they are the decider ([Engine.decider], CR 723.5); engine-automatic answers any device may make because they decide
 * nothing for anyone; random answers (the host's, D25); and the other player's input assumed by the author, of the
 * assumable kinds only (§3). Nothing else: no passing, choosing, dividing or declaring for the other player.
 */
object WhoActs {
    sealed interface Kind {
        /** The author's own real input. */
        data object Own : Kind
        /** An engine-automatic answer (§6 a–c): not assumed, never compared, never a branch. */
        data object Automatic : Kind
        /** The answer to the engine's random decision, from the line's stream. */
        data object Random : Kind
        /** [about]'s input, assumed by the author. */
        data class Assumed(val about: PlayerId) : Kind
    }

    /** What [input] by [author] on [model] is, or a [Refusal] saying why they may not make it. */
    fun kind(model: GameModel, input: Input, author: PlayerId, assumed: Boolean): Kind {
        val state = model.engine
        when (input) {
            is Input.Concede -> return if (input.player == author && !assumed) Kind.Own else throw Refusal("Only ${input.player} concedes for themselves (CR 104.3a)")
            is Input.AgreedResult -> {
                val suggestion = model.table.suggestion
                if (assumed || suggestion == null || suggestion.result != input.result || suggestion.by == author)
                    throw Refusal("A result is agreed by accepting the other player's suggestion of it")
                return Kind.Own
            }
            is Input.StartGame -> throw Refusal("The server starts the game")
            // The host's own input when passes made for players reach the same position again (CR 104.4b)
            Input.MandatoryLoop -> return if (assumed) throw Refusal("Not an assumable input") else Kind.Automatic
            else -> {}
        }
        val decision = (input as? Input.Answer)?.let { a -> state.pending?.decision?.takeIf { it.id == a.decision } }
        if (decision is Decision.Random) return if (assumed) throw Refusal("Not an assumable input") else Kind.Random
        val player = playerOf(state, input) ?: throw Refusal("Refused by the engine: no such decision")
        val decider = Engine.decider(state, player)
        if (!assumed) {
            if (decider == author) return Kind.Own
            if (automatic(model, input, player, decision)) return Kind.Automatic
            throw Refusal("Only $decider decides this (I-D56 §6)")
        }
        if (decider == author) throw Refusal("An input of your own isn't assumed")
        assumable(state, input, player, author)?.let { throw Refusal(it) }
        return Kind.Assumed(player)
    }

    /** Whose input [input] is by the rules on [state]: its player, or the open decision's. */
    fun playerOf(state: GameState, input: Input): PlayerId? = when (input) {
        is Input.PlayLand -> input.player
        is Input.Cast -> input.player
        is Input.Activate -> input.player
        is Input.PassPriority -> input.player
        is Input.DeclareAttackers -> input.player
        is Input.DeclareBlockers -> input.player
        is Input.Cancel -> input.player
        is Input.Concede -> input.player
        is Input.SetupAnswer -> input.player
        is Input.Answer -> state.pending?.decision?.takeIf { it.id == input.decision }?.player
        else -> null
    }

    /**
     * §6's engine-automatic answers: (a) a pass of a player who could only pass, judged without cards only they can see
     * (the shared state keeps every player's "Skip when I have no choice" off, so [Engine.shouldPassFor] is that public
     * judgement), never at their own main phase ([Stops.forcedPass]); (b) an order-free division of combat damage answered with the engine's suggestion (CMB-033: one
     * creature, or enough for lethal damage to each); (c) the decider's own early answer (I-D33), applied as its decision
     * opens. Choices with one legal answer that depend on nothing hidden the engine makes itself (P5).
     */
    private fun automatic(model: GameModel, input: Input, player: PlayerId, decision: Decision?): Boolean {
        if (input is Input.PassPriority) return Stops.forcedPass(model, player)
        if (input !is Input.Answer || decision == null || decision.player != player) return false
        return input == earlyAnswer(model, decision) || input == orderFree(decision)
    }

    /**
     * The engine-automatic answer to the open [decision] on [model] (§6 b, c), or null: the decider's own early answer to
     * it (I-D33), valid this turn; else an order-free division of combat damage with the engine's suggestion (CMB-033:
     * one creature, or enough for lethal damage to each). Any device running the step makes it.
     */
    fun automaticAnswer(model: GameModel, decision: Decision): Input.Answer? = earlyAnswer(model, decision) ?: orderFree(decision)

    private fun earlyAnswer(model: GameModel, decision: Decision): Input.Answer? {
        val key = EarlyAnswer.keyOf(decision)
        val turn = model.engine.turn.number
        return model.table.earlyAnswers.values.firstOrNull { early ->
            early.player == decision.player && early.key == key && (early.turn == null || early.turn == turn)
        }?.let { Input.Answer(decision.id, it.answer) }
    }

    private fun orderFree(decision: Decision): Input.Answer? {
        if (decision !is Decision.Divide || decision.lethal.isEmpty()) return null
        if (decision.lethal.size > 1 && decision.amount < decision.lethal.sumOf { it.amount }) return null
        return Input.Answer(decision.id, Answer.Division(decision.suggested))
    }

    /** Why [author] may not assume [input] of [player] here (I-D56 §3), or null: a pass anywhere but at [player]'s stop,
     * blocks without costs to block (CR 509.1d), or the opening keep. */
    private fun assumable(state: GameState, input: Input, player: PlayerId, author: PlayerId): String? = when {
        input is Input.PassPriority -> when {
            state.pending != null || state.priority.holder != player -> "Not an assumable input"
            Stops.stop(state, player) -> "Passing at their own main phase is skipping their turn, not assumed (I-D56 §3)"
            else -> null
        }
        input is Input.DeclareBlockers ->
            if (Engine.blockCosts(state, input.blocks)) "Blocks with a cost to block aren't assumed (CR 509.1d)" else null
        keep(input) -> when {
            state.turn.number != 0 -> "Not an assumable input"
            state.turn.active != author -> "Only the starting player assumes the other player's keep (I-D56 §3)"
            !logged(state) { it is arcanum.engine.change.GameEvent.Kept && it.player == author } -> "Assume a keep after your own"
            logged(state) { it is arcanum.engine.change.GameEvent.Mulliganed && it.player == player } -> "A keep that owes cards to the bottom isn't assumed"
            else -> null
        }
        else -> "Not an assumable input"
    }

    private fun keep(input: Input): Boolean = when (input) {
        is Input.SetupAnswer -> input.answer == Answer.Option(KEEP)
        is Input.Answer -> input.answer == Answer.Option(KEEP)
        else -> false
    }

    private fun logged(state: GameState, event: (arcanum.engine.change.GameEvent) -> Boolean): Boolean =
        state.log.any { ((it.change as? Change.Happened)?.event)?.let(event) == true }

    private const val KEEP = "Keep"
}

/** Stops and pauses (STK-036, I-D56 §2): where a player's own device stops for them. */
object Stops {
    /** [player]'s stop on [state]: their own main phase with an empty Stack (CR 505). */
    fun stop(state: GameState, player: PlayerId): Boolean = state.turn.active == player && state.stack.isEmpty() &&
        (state.turn.step == arcanum.api.TurnStep.PrecombatMain || state.turn.step == arcanum.api.TurnStep.PostcombatMain)

    /** Whether [player]'s pass on [state] is engine-automatic (§6 a): they could only pass ([Engine.shouldPassFor], the
     * public judgement), and it isn't their [stop]: their own main phases wait for them even with nothing to do (owner,
     * smoke 2026-11-04: a turn is never passed away without its player, STK-036). */
    fun forcedPass(state: GameState, player: PlayerId, view: arcanum.engine.view.View? = null): Boolean =
        Engine.shouldPassFor(state, player, view) && !stop(state, player)

    /** [forcedPass] on [model]'s engine state, with the pass check it keeps for the state ([GameModel.passCheck]). */
    fun forcedPass(model: GameModel, player: PlayerId): Boolean = model.passCheck(player).asSet && !stop(model.engine, player)

    /**
     * Whether [mode] passes [player]'s priority on [state] (STK-036): never in Control; in Normal wherever it isn't their
     * [stop] or [pause]; in Quick also at a pause where they can't respond ([ownSkipOn]: their own cards judged, asked
     * only there). Their own device passes for them by it, and the other player's device assumes their pass by it.
     */
    fun passes(mode: ResponseMode, state: GameState, player: PlayerId, ownSkipOn: () -> Boolean): Boolean {
        if (stop(state, player)) return false
        return when (mode) {
            ResponseMode.FullControl -> false
            ResponseMode.Normal -> !pause(state, player)
            ResponseMode.AutoSkip -> !pause(state, player) || ownSkipOn()
        }
    }

    /** [player]'s pause on [state]: the other player's spell or ability on top of the Stack, the other player's attack,
     * blocks (both players), the other player's end step. */
    fun pause(state: GameState, player: PlayerId): Boolean {
        val top = state.stack.lastOrNull()
        if (top != null) return (state.stackItems[top]?.controller ?: state.obj(top)?.controller) != player
        val attacking = state.combat?.attackers?.isNotEmpty() == true
        return when (state.turn.step) {
            arcanum.api.TurnStep.DeclareAttackers -> attacking && state.turn.active != player
            arcanum.api.TurnStep.DeclareBlockers -> attacking
            arcanum.api.TurnStep.End -> state.turn.active != player
            else -> false
        }
    }
}

/** A game's state [model] at event [at], kept between submissions (the server keeps active games warm, I-D54 §4). */
class LiveState(val at: String, val model: GameModel)

/**
 * The server's order (I-D54 §3, I-D56 §8): submissions are taken in arrival order. A player adds real inputs only at
 * their local head ([LocalHeads]): a submission at P first moves its author's local head to P (P at or past it on the
 * main line), then
 * - P is the head: appended (a table-only event may be made there by an author who is behind: it doesn't move their
 *   local head, I-D56 §6);
 * - only other table-only events follow P, none touching what the submission's table changes touch ([commutes]): they
 *   decide nothing and the engine state is P's, so the event is made on the head instead (I-D56 §6: they never make an
 *   input stale; the device slips them in under its pending events the same way, [GameSession]);
 * - the next main-line event after P is an assumed input about the author: the same input confirms it ([Confirmed]: the
 *   author's local head moves past it and the submission is dropped as a duplicate); any other input (a mana ability
 *   or special action first included) is a branch: the new event's parent is P, it starts a new line, and the old line
 *   after P is discarded (kept in storage, shown to nobody);
 * - the next main-line event after P is the other player's own input that the author's submission assumed for them
 *   (the same event, [duplicate]): their real answer came first, and the submission is dropped as its duplicate too
 *   ([Confirmed]; owner 2026-11-07, SYS-077);
 * - the author's concession at P is a branch too (I-D56 §11: the result never depends on what they haven't seen), its
 *   new line started by the server when the device didn't (a concession made at the device's head while an event was
 *   still on its way to it, CR 104.3a: conceding is taken at any time);
 * - otherwise: `stale`.
 * An assumed input is taken only at its author's own local head (I-D56 §6: never from a player who is behind). A
 * submission dropped as the duplicate of a main-line event ([duplicate]: the same event made on the same board) stands
 * for that event: the author's later submissions made on it are taken on that event instead ([Confirmed.alias], [accept]'s
 * aliases), so a confirmation that crossed the other player's assumption on the wire costs the author nothing (owner
 * 2026-11-07: "if you confirm something that is assumed, that's just a noop").
 * The invariant this keeps: unconfirmed assumptions on the main line are about one player, so there is never more than
 * one tentative line. The state at the parent is [warm]'s when it is there or near ([GameHistory.move]), else a
 * checkpoint and the events after it ([GameHistory.stateAt]).
 */
object Acceptance {
    sealed interface Result
    /** The accepted [event] and the state after it ([live]); [discarded]: the first event of the line it discarded;
     * [authorHead]: the author's local head moves to it (not for a table-only event added at the head while the author
     * is behind: it confirms nothing, I-D56 §6). */
    class Accepted(val event: GameEvent, val live: LiveState, val discarded: String? = null, val authorHead: Boolean = true) : Result
    /** The submission confirmed the assumed input [event] about its author, or was the author's assumption of the other
     * player's own input [event] that came first, and was dropped. [alias]: the submission's id when it was [event]'s
     * [duplicate] (the same event on the same board): the author's later submissions made on it are taken on [event]. */
    class Confirmed(val event: String, val alias: String? = null) : Result

    /**
     * Accepts [sent] by [author] on the main line ending at [head] with each player's local head [heads] (a player
     * without one is at the root); [nextLine] is the line a branch starts (one past the line counter: while the head's
     * line is behind the counter, after a rewind, an event at the head starts the counter's line, [Rewind.lineDue]); [rewindOpen]: an undo request is open, so
     * only a concession is taken (I-D56 §10); [aliases]: the author's earlier submissions dropped as duplicates of
     * main-line events, by id ([Confirmed.alias]): a submission made on one is taken on its event; [reveals]: the game's
     * assume reveal settings, the cards the information rule lets through (LOB-135). Refused ([Refusal]),
     * or accepted with a new event, [warm] is used up.
     */
    fun accept(history: GameHistory, head: String, warm: LiveState?, sent: Submission, author: PlayerId,
        heads: Map<PlayerId, String>, nextLine: Int, rewindOpen: Boolean = false, aliases: Map<String, String> = emptyMap(),
        reveals: org.asyncttwiz.protocol.AssumeReveals = org.asyncttwiz.protocol.AssumeReveals.NONE): Result {
        val submission = aliases[sent.parentId]?.let { sent.copy(parentId = it) } ?: sent
        val concession = submission.acts.filterIsInstance<Act.Perform>().firstOrNull()?.input.let { it is Input.Concede && it.player == author }
        var used: LiveState? = null
        try {
            if (rewindOpen && !concession) throw Refusal("rewindRequested")
            if (submission.id == history.store.rootId || history.store.node(submission.id) != null) throw Refusal("Event ${submission.id} exists")
            val own = heads[author] ?: history.store.rootId
            val ahead = MainLine.after(history, head, own) ?: throw Refusal("stale")
            val parent = submission.parentId
            val at = if (parent == own) 0 else ahead.indexOfFirst { it.id == parent } + 1
            if (parent != own && at == 0) throw Refusal("stale")
            // I-D56 §6: assumed inputs only by a player whose own local head is where they are made
            if (submission.acts.any { it is Act.Perform && it.assumed } && parent != own) throw Refusal("stale")
            val rest = ahead.drop(at)
            fun state(id: String): GameModel =
                (if (warm == null) history.stateAt(id) else history.move(warm.model, warm.at, id)).also { used = LiveState(id, it) }
            if (rest.all { commutes(it, submission.table) } && (rest.isEmpty() || submission.acts.firstOrNull() !is Act.NewLine || concession)) {
                // At the head, or made on the head over other table-only events (the same engine state)
                val model = state(head)
                // After a rewind that added no event, the next event at the head starts the new line (§9)
                val due = Rewind.lineDue(model, nextLine - 1)
                val acts = when {
                    due != null && concession -> listOf(Act.NewLine(due)) + submission.acts.filter { it !is Act.NewLine }
                    due != null -> if (submission.acts.firstOrNull() == Act.NewLine(due)) submission.acts else throw Refusal("stale")
                    concession -> submission.acts.filter { it !is Act.NewLine }
                    else -> submission.acts
                }
                if (due == null && acts.firstOrNull() is Act.NewLine) throw Refusal("stale")
                val others = model.engine.players.map { it.id }.filter { it != author }.map { heads[it] ?: history.store.rootId }
                val tentative = others.any { other -> tentativeFor(history, head, other, author) }
                // A result is confirmed once every player saw it, its author too (I-D56 §11)
                val seen = ahead.all { it.engine == null } &&
                    others.all { other -> MainLine.after(history, head, other)?.all { e -> e.engine == null } == true }
                val event = Authority.make(model, submission.copy(parentId = head, acts = acts), author,
                    Authority.Rules(nextLine = due, tentative = tentative, resultSeenByAll = seen, reveals = reveals))
                return Accepted(event, LiveState(event.id, model), authorHead = event.engine?.steps.orEmpty().any { it.input != null } || parent == own)
            }
            val next = rest.first()
            val first = submission.acts.filterIsInstance<Act.Perform>().firstOrNull()
            val assumed = next.assumed
            val players = (heads.keys + author).distinct()
            val about = assumed?.let { step -> assumedAbout(step, next, players) }
            // The author's assumption of the other player's input, which that player's own same input came before
            if (first != null && first.assumed && next.assumed == null && next.author != author.value && duplicate(submission, next))
                return Confirmed(next.id, alias = submission.id)
            if (first == null || first.assumed) throw Refusal("stale")
            if (about != author && !concession) throw Refusal("stale")
            if (about == author && !concession && confirms(first.input, assumed!!.input!!))
                return Confirmed(next.id, alias = submission.id.takeIf { duplicate(submission, next) })
            // A branch at the author's local head: a new line, the old line after it discarded. A concession's line is
            // the server's to start when the device didn't (it conceded at what it showed as the head, §11)
            val acts = when {
                concession -> listOf(Act.NewLine(nextLine)) + submission.acts.filter { it !is Act.NewLine }
                submission.acts.firstOrNull() is Act.NewLine -> submission.acts
                else -> throw Refusal("newLine")
            }
            val model = state(parent)
            val event = Authority.make(model, submission.copy(acts = acts), author, Authority.Rules(nextLine = nextLine, reveals = reveals))
            return Accepted(event, LiveState(event.id, model), discarded = next.id)
        } catch (refusal: Refusal) {
            refusal.state = used ?: warm
            throw refusal
        }
    }

    /**
     * Whether [event], another event on the main line before a submission lands, lets the submission be made after it
     * as if it weren't there (I-D56 §6): it is table-only (no engine part and no line moved: the engine state is the
     * same) and writes none of the table parts [table] (the submission's table changes) writes. The server makes such a
     * submission on the head ([accept]); a device slips such an event in under its pending events and open draft
     * ([GameSession.received]) by the same rule, so both make the same event.
     */
    fun commutes(event: GameEvent, table: List<TableChange>): Boolean =
        event.engine == null && event.table.none { it is TableChange.LineMoved } &&
            Concerns.tableWrites(event.table).none { it in Concerns.tableWrites(table) }

    /**
     * Whether a player's answer at a decision stop whose first input is [first] confirms the assumed input [assumed]
     * about them (I-D56 §8, §18.7): only the first input is compared (canonically, [same]); what else the answer holds
     * (later inputs, table changes) is the player's next action after the confirmed event, not part of the
     * confirmation. One rule for the server ([accept]) and the device ([GameDevice.act], [GameDevice.send]).
     */
    fun confirms(first: Input, assumed: Input): Boolean = same(first, assumed)

    /**
     * Whether [submission], made on [event]'s parent, is the same event as [event] but for who made it (owner 2026-11-07,
     * SYS-077): the same inputs in the same order (compared canonically, [same]: one side's own input and the other's
     * assumption of it alike), the same new line and the same table changes, so it gives the same board (the engine is
     * deterministic and the random answers are the line's). One rule for the server ([accept]) and the device
     * ([GameSession.received]).
     */
    fun duplicate(submission: Submission, event: GameEvent): Boolean {
        val acts = submission.acts.filterIsInstance<Act.Perform>().map { it.input }
        val inputs = event.engine?.steps.orEmpty().mapNotNull { it.input }
        return acts.size == inputs.size && acts.zip(inputs).all { (a, b) -> same(a, b) } &&
            submission.acts.filterIsInstance<Act.NewLine>().map { it.number } == listOfNotNull(event.startsLine) &&
            submission.table == event.table.filter { it !is TableChange.LineMoved }
    }

    /** Whether the main line after [other]'s local head holds an assumption of [author]'s: [author]'s line is
     * tentative (I-D56 §2). */
    fun tentativeFor(history: GameHistory, head: String, other: String, author: PlayerId): Boolean =
        MainLine.after(history, head, other)?.any { it.assumed?.assumedBy == author } ?: true

    /** Whom the assumed [step] of [event] is about: the input's player, or for an answer the player who isn't its
     * assumer (two players, I-D56). */
    fun assumedAbout(step: EngineStep, event: GameEvent, players: List<PlayerId>): PlayerId? {
        val input = step.input ?: return null
        val named = when (input) {
            is Input.PassPriority -> input.player
            is Input.DeclareBlockers -> input.player
            is Input.SetupAnswer -> input.player
            else -> null
        }
        return named ?: players.firstOrNull { it != step.assumedBy }
    }

    /** The same engine input, compared canonically (blocks as a set, I-D56 §8). */
    fun same(a: Input, b: Input): Boolean = when {
        a is Input.DeclareBlockers && b is Input.DeclareBlockers -> a.player == b.player && a.blocks.toSet() == b.blocks.toSet()
        else -> a == b
    }
}

/** A game's main line (I-D56 §2): the line of events ending at the head. */
object MainLine {
    /** The events after [from] up to [head], oldest first, when [from] is on the main line (the root or an event at or
     * before the head); else null. */
    fun after(history: GameHistory, head: String, from: String): List<GameEvent>? {
        if (from == head) return emptyList()
        val depth = if (from == history.store.rootId) 0 else history.store.node(from)?.depth ?: return null
        // One read of the line however far back [from] is (I-D54 §4: no query per event)
        val nodes = history.store.ancestors(head, depth)
        val last = nodes.lastOrNull() ?: return null
        if (last.depth != depth + 1 || last.event.parentId != from) return null
        return nodes.asReversed().map { it.event }
    }
}

/** The information rule (I-D56 §5): what going on would show a player that they couldn't see. */
object InformationRule {
    /**
     * The cards [changes] (made on [before]) show [viewer] at some moment that weren't visible to them in [before]
     * ([Engine.revealed]: compared by card, looks and reveals in passing included), apart from cards of their own library
     * (protected by the reseed, §9) and the cards the game's [allowed] settings let through (LOB-135: the other player's
     * library, their hand; where the card was in [before]).
     */
    fun revealing(before: GameState, changes: List<Change>, viewer: PlayerId,
        allowed: org.asyncttwiz.protocol.AssumeReveals = org.asyncttwiz.protocol.AssumeReveals.NONE): Set<arcanum.engine.state.CardId> =
        revealing(Before(before, null), changes, viewer, null, allowed)

    /** [revealing] on [before] with the views known ([Before.view]; [after]: the view of [before]'s state with [changes]
     * made), so the layers aren't worked out again (engine-responsiveness R2); null ones are worked out. */
    fun revealing(before: Before, changes: List<Change>, viewer: PlayerId, after: arcanum.engine.view.View?,
        allowed: org.asyncttwiz.protocol.AssumeReveals = org.asyncttwiz.protocol.AssumeReveals.NONE): Set<arcanum.engine.state.CardId> =
        Engine.revealed(before.state, changes, viewer, before.view, after).filterTo(mutableSetOf()) { card ->
            before.state.current(card)?.let { !passes(it, viewer, allowed) } ?: true
        }

    /** Whether [card] (where it is before an event) may be shown to [viewer] on a tentative line: a card of their own
     * library always; with [allowed]'s settings, a card of the other player's library or hand (LOB-135). */
    private fun passes(card: arcanum.engine.state.GameObject, viewer: PlayerId, allowed: org.asyncttwiz.protocol.AssumeReveals): Boolean =
        when (card.zone) {
            Zone.Library -> card.owner == viewer || allowed.library
            Zone.Hand -> card.owner != viewer && allowed.hands
            else -> false
        }

    /** The state before an event, kept for the rule's check: a copy of the model's engine state, with its view when the
     * model had it kept (null: worked out if the check needs it). */
    class Before(val state: GameState, val view: arcanum.engine.view.View?) {
        companion object {
            fun of(model: GameModel): Before = Before(model.engine.deepCopy(), model.keptView())
        }
    }

    /** Whether [event]'s random answers include a result players see (a coin flip, a die roll, a random choice; a
     * shuffle's order is hidden and a new line reshuffles it anyway). */
    fun randomResult(event: GameEvent, before: GameState): Boolean = randomResult(event.engine?.steps.orEmpty(), before)

    /** [randomResult] of the engine [steps] made on [before] (an event being made). With [allowed]'s hands setting
     * (LOB-135), a random pick among cards of a hand other than [viewer]'s (a random discard) isn't one: it shows only
     * cards of that hand, which the setting lets through. */
    fun randomResult(steps: List<EngineStep>, before: GameState, viewer: PlayerId? = null,
        allowed: org.asyncttwiz.protocol.AssumeReveals = org.asyncttwiz.protocol.AssumeReveals.NONE): Boolean {
        if (steps.none { it.input is Input.Answer }) return false
        val state = before.deepCopy()
        var found = false
        for (step in steps) {
            val d = state.pending?.decision as? Decision.Random
            val input = step.input
            if (d != null && input is Input.Answer && input.decision == d.id) {
                val what = d.what
                val shuffle = what is arcanum.engine.decision.RandomKind.Objects && what.library.isNotEmpty()
                val handPick = allowed.hands && viewer != null && what is arcanum.engine.decision.RandomKind.Objects &&
                    what.among.isNotEmpty() && what.among.all { id -> state.obj(id)?.let { it.zone == Zone.Hand && it.owner != viewer } == true }
                if (!shuffle && !handPick) found = true
            }
            step.changes.forEach { state.apply(it) }
        }
        return found
    }
}
