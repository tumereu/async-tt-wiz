package arcanum.engine.resolution

import arcanum.api.CardDefinition
import arcanum.engine.Pending
import arcanum.engine.Progress
import arcanum.engine.action.Act
import arcanum.engine.action.Asker
import arcanum.engine.action.Knowledge
import arcanum.engine.action.NeedDecision
import arcanum.engine.action.NeedOtherView
import arcanum.engine.action.OtherViewFills
import arcanum.engine.change.Change
import arcanum.engine.change.Recorder
import arcanum.engine.decision.AnswerCheck
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.decision.DecisionId
import arcanum.engine.decision.FilledObject
import arcanum.engine.decision.OtherViewFlow
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId

/**
 * A resolution worked out as in another player's view (D60, D31; [Decision.OtherView]). In a viewer's state, once a
 * step reaches cards only another player may look at (their search, scry, surveil, look, or a choice among cards hidden
 * from the viewer), it and the steps after it may depend on those cards ("if it's a land card"). So each step from
 * there is worked out with the identities the host sends with every answer, and the other player's answers from their
 * view, then committed without the identities only that player saw ([Knowledge.forgetHidden]): the changes say only
 * what the viewer may see. Between calls the state keeps the answers and which cards are hidden, never what they are.
 * Once no card of the resolution is hidden any more, it goes on as usual.
 */
internal object OtherView {
    /** The step that reached [n]'s cards begins to be worked out as in [NeedOtherView.player]'s view. */
    fun start(c: Continuation.Resolving, n: NeedOtherView): Progress {
        val next = c.copy(otherView = OtherViewFlow(n.player, n.source, n.reason))
        return Progress.Waiting(Pending(decision(next, n.objects), next))
    }

    /** [arcanum.engine.Host.otherViewDecision]: the decision the steps wait for with [filled], as its player sees it. */
    fun next(state: GameState, filled: List<FilledObject>): Decision? {
        val pending = state.pending ?: return null
        val c = pending.continuation as? Continuation.Resolving ?: return null
        if (pending.decision !is Decision.OtherView || c.otherView == null) return null
        val waiting = run(Recorder(state.deepCopy()), c, filled.associate { it.id to it.definition }, probe = true)
        return (waiting as? Progress.Waiting)?.pending?.decision?.takeIf { it !is Decision.OtherView }
    }

    /**
     * Works the steps of [start] out one by one with the identities [filled] and its answers: each committed as it is
     * done (D31), until one waits (asking [Decision.OtherView] again), the resolution ends, or nothing is hidden any
     * more. [probe]: a decision the steps wait for is returned as it is (with the cards only its player may see).
     */
    fun run(recorder: Recorder, start: Continuation.Resolving, filled: Map<ObjectId, CardDefinition>, probe: Boolean = false): Progress {
        var c = start
        while (true) {
            val flow = c.otherView ?: return Resolution.run(recorder, c)
            val item = c.left ?: recorder.state.stackItems[c.item] ?: return Progress.Done
            val steps = Resolution.stepsOf(item)
            val mark = recorder.changes.size
            val fills = OtherViewFills(flow.player, filled) { d, a -> AnswerCheck.check(recorder.state, d, a) }
            val act = Act(recorder, Asker(c.answers, "${c.item}/${c.step}", otherView = fills))
            try {
                // The cards only that player saw so far are filled again for this step (they may be referred to)
                refill(act, fills, flow)
                if (c.step >= steps.size) {
                    if (c.left == null) Resolution.finish(act, item, fizzled = false)
                    require(act.asker.unused.isEmpty()) { "More answers than the resolution asked: ${act.asker.unused}" }
                    Knowledge.forgetHidden(recorder, mark, fills.hidden)
                    return Progress.Done
                }
                val scope = Scope(act, Resolution.causeOf(item), c.names, item.event)
                Steps.run(scope, steps[c.step])
                val left = c.left ?: item.takeIf { it.id !in recorder.state.stackItems }
                // Still hidden: the cards the viewer hasn't seen that are still there (not given new ids by a shuffle)
                val hidden = Knowledge.forgetHidden(recorder, mark, fills.hidden).filter { recorder.state.current(it) != null }
                // Answers left over are for the next steps
                c = c.copy(step = c.step + 1, names = scope.names, answers = act.asker.unused, left = left,
                    otherView = if (hidden.isEmpty()) null else flow.copy(hidden = hidden))
            } catch (n: NeedDecision) {
                recorder.rollback(mark)
                return Progress.Waiting(Pending(if (probe) n.decision else decision(c, fills.needed), c))
            } catch (n: NeedOtherView) {
                recorder.rollback(mark)
                return Progress.Waiting(Pending(decision(c, n.objects), c))
            }
        }
    }

    /** Fills the cards [flow] keeps hidden in again, where they are unknown now. */
    private fun refill(act: Act, fills: OtherViewFills, flow: OtherViewFlow) {
        val again = flow.hidden.mapNotNull { act.state.current(it) }.filter { it.unknown }.map { it.id }
        again.filter { it !in fills.needed }.forEach { fills.needed += it }
        if (again.any { it !in fills.filled }) throw NeedOtherView(flow.player, flow.source, fills.needed.toList(), flow.reason)
        again.forEach { id ->
            val before = act.state.objOrThrow(id)
            val definition = fills.filled.getValue(id)
            fills.hidden[before.card] = definition
            act.record(Change.Filled(before, before.copy(definition = definition)))
        }
    }

    private fun decision(c: Continuation.Resolving, objects: List<ObjectId>): Decision.OtherView {
        val flow = c.otherView!!
        return Decision.OtherView(DecisionId("${c.item}/${c.step}:other-view.${c.answers.size}.${objects.size}"), flow.player, flow.source,
            objects, flow.reason, c.answers.size)
    }
}
