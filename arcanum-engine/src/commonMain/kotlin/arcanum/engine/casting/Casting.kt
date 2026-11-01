package arcanum.engine.casting

import arcanum.api.KeywordKind
import arcanum.api.PlayKind
import arcanum.api.Zone
import arcanum.engine.Pending
import arcanum.engine.Progress
import arcanum.engine.change.Change
import arcanum.engine.change.Changes
import arcanum.engine.change.Meaning
import arcanum.engine.change.Recorder
import arcanum.engine.decision.AnnounceStage
import arcanum.engine.decision.Announced
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Choices
import arcanum.engine.decision.Continuation
import arcanum.engine.input.Input
import arcanum.engine.state.Bindings
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PriorityState

/**
 * Playing lands (CR 305), casting spells (CR 601.2) and activating abilities (CR 602.2, 605, 606), and special
 * actions (CR 116.2): announcing, modes, X, targets, costs and mana payment ([arcanum.engine.mana]), each choice a
 * decision ([Continuation.Announcing]); cancelling rewinds the whole cast with [Change.Reverted] (D58).
 *
 * A spell's card moves to the Stack first (CR 601.2a); its [arcanum.engine.state.StackItem] is added with
 * [Meaning.Cast] once all choices are made and the costs paid (CR 601.2i), so "becomes cast" is that change. An
 * activated ability's item is added the same way with [Meaning.Activated], after a [Change.Happened]
 * ([arcanum.engine.change.GameEvent.Activated]). Mana abilities don't use the Stack (CR 605.3b).
 */
internal object Casting {
    fun playLand(recorder: Recorder, input: Input.PlayLand): Progress {
        val snap = recorder.snapshot()
        Legality.cantPlayLand(snap, input.player, input.card, input.face)?.let { throw IllegalArgumentException("Can't play that land: $it") }
        val s = recorder.state
        // CR 305.1: a special action; the land simply enters the battlefield (CR 712.11: with the face played up)
        val turn = s.turn
        recorder.record(Changes.step(s, turn.copy(landsPlayed = turn.landsPlayed + (input.player to (turn.landsPlayed[input.player] ?: 0) + 1))))
        // The face played is up as it enters, so that face's "as this land enters" applies (CR 712.11, 614.12)
        s.obj(input.card)?.let { if (input.face != it.face) recorder.record(Change.Face(input.card, it.face, input.face)) }
        // Through the event pipeline: "enters tapped", "as this land enters" choices (CR 614.12); a choice waits
        val entering = arcanum.engine.action.Events.run(recorder, arcanum.engine.action.PlannedEvent.Move(listOf(input.card), Zone.Battlefield,
            listOf(Meaning.PlayedLand), under = input.player, controller = input.player, source = input.card))
        if (entering is Progress.Waiting) return entering
        keepPriority(recorder, input.player)
        return Progress.Done
    }

    fun cast(recorder: Recorder, input: Input.Cast): Progress {
        val snap = recorder.snapshot()
        Legality.cantCast(snap, input.player, input.card, input.way)?.let { throw IllegalArgumentException("Can't cast that: $it") }
        val o = recorder.state.objOrThrow(input.card)
        val fromHand = o.zone == Zone.Hand && o.owner == input.player
        val permission = if (fromHand && input.way.permission == null) null
            else Legality.permission(snap, input.player, input.card, input.way, PlayKind.Cast)?.first
        val way = input.way.copy(permission = permission)
        val alternative = Legality.alternative(snap, input.card, way)
        val faceDown = alternative?.second?.faceDown == true
        val start = recorder.changes.size
        // CR 601.2a: the card moves to the Stack and becomes the topmost object there
        recorder.record(Changes.move(recorder.state, listOf(input.card), Zone.Stack, controller = input.player, faceDown = faceDown))
        val onStack = (input.card as ObjectId.Card).next()
        if (way.face != recorder.state.objOrThrow(onStack).face) recorder.record(Change.Face(onStack, recorder.state.objOrThrow(onStack).face, way.face))
        var names = Bindings.EMPTY.with(Plan.FROM, NamedValue.Option(Plan.zoneName(o.zone)))
        if (faceDown) names = names.with(FACE_DOWN, NamedValue.Option(alternative!!.second.keyword?.id ?: "morph"))
        val c = Continuation.Announcing(input.player, Announced.Spell(onStack, way), AnnounceStage.Modes, Choices(names = names))
        return advance(recorder, c, start)
    }

    fun activate(recorder: Recorder, input: Input.Activate): Progress {
        val snap = recorder.snapshot()
        val instance = Legality.instance(snap, input.source, input.ability)
            ?: throw IllegalArgumentException("${input.source} has no ability ${input.ability.key}")
        Legality.cantActivate(snap, input.player, input.source, instance)?.let { throw IllegalArgumentException("Can't activate that: $it") }
        // CR 602.2a: the ability is created on the Stack (its item is added once its costs are paid)
        val id = "a${recorder.state.nextTimestamp()}.${recorder.state.stack.size}"
        val c = Continuation.Announcing(input.player, Announced.Ability(input.source, input.ability, instance.ability),
            AnnounceStage.Modes, Choices(names = Bindings.EMPTY.with(Plan.ITEM, NamedValue.Option(id))))
        return advance(recorder, c, recorder.changes.size)
    }

    fun resume(recorder: Recorder, continuation: Continuation.Announcing, answer: Answer): Progress {
        val start = recorder.changes.size
        if (continuation.choices.names[Plan.MANUAL] != null) {
            require(answer == Answer.Done) { "Answer the manual step with Done" }
            return Progress.Done
        }
        val plan = Plan(recorder.state, continuation)
        val q = plan.next() ?: throw IllegalStateException("Nothing was asked")
        if (q is Question.Pay) {
            val uses = (answer as? Answer.ManaPayment)?.uses ?: throw IllegalArgumentException("Expected a mana payment")
            return Payment.finish(recorder, continuation, uses, start)
        }
        val choices = Answers.apply(plan, q, answer)
        return advance(recorder, continuation.copy(choices = choices), start)
    }

    /** [Input.Cancel]: takes back an open cast or activation, everything it did, last first (CR 601.2, D58). */
    fun cancel(recorder: Recorder, continuation: Continuation): Progress {
        val open = continuation as? Continuation.Announcing
            ?: throw IllegalArgumentException("Only a cast or an activation can be cancelled")
        open.made.asReversed().forEach { recorder.record(Change.Reverted(it)) }
        return Progress.Done
    }

    /** Asks the next question, answering those with one possible answer (P5), or finishes when nothing is left. */
    internal fun advance(recorder: Recorder, start: Continuation.Announcing, from: Int): Progress {
        var c = start
        while (true) {
            val plan = Plan(recorder.state, c)
            val q = plan.next() ?: return Payment.finish(recorder, c, emptyList(), from)
            // CR 601.2c, 601.2h: without enough legal targets or objects to pay with, it can't be cast or activated:
            // the input (or answer) is refused; an open cast can still be cancelled
            when {
                q is Question.Target && q.options.size < q.min ->
                    throw IllegalArgumentException("Not enough legal targets for ${q.slot.key}: ${q.options.size} of ${q.min}")
                q is Question.Objects && q.choice.options.size < q.choice.min ->
                    throw IllegalArgumentException("Not enough to ${q.choice.text.lowercase()}: ${q.choice.options.size} of ${q.choice.min}")
                q is Question.OneOf && q.part.options.none { option -> Answers.payable(plan, option.parts) } ->
                    throw IllegalArgumentException("None of ${q.labels} can be paid")
            }
            val auto = Answers.automatic(plan, q)
            if (auto != null) {
                c = c.copy(choices = Answers.apply(plan, q, auto))
                continue
            }
            val decision = plan.decision(q, decisionId(recorder.state, "cast:${q.stage}:${q::class.simpleName}"))
            return Progress.Waiting(Pending(decision, c.copy(stage = q.stage, made = c.made + recorder.madeSince(from))))
        }
    }

    /** CR 117.3c: a player who casts, activates or takes a special action receives priority afterwards. */
    internal fun keepPriority(recorder: Recorder, player: arcanum.engine.state.PlayerId) {
        val after = PriorityState(player, 0)
        if (recorder.state.priority != after) recorder.record(Changes.priority(recorder.state, after, listOf(Meaning.PriorityGranted)))
    }

    /** Card memory key of a spell cast face down: the keyword's id (morph). */
    const val FACE_DOWN = "\$face-down"

    /** The keyword kind named by its [id]. */
    fun keywordKind(id: String): KeywordKind? = KeywordKind.all.firstOrNull { it.id == id }
}
