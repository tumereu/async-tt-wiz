package arcanum.engine.resolution

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Instructions
import arcanum.api.Mechanic
import arcanum.api.Step
import arcanum.api.Zone
import arcanum.engine.Pending
import arcanum.engine.Progress
import arcanum.engine.action.Act
import arcanum.engine.action.Actions
import arcanum.engine.action.Asker
import arcanum.engine.action.Cause
import arcanum.engine.action.Events
import arcanum.engine.action.NeedDecision
import arcanum.engine.action.PlannedMove
import arcanum.engine.action.Replacements
import arcanum.engine.action.Targeting
import arcanum.engine.change.Change
import arcanum.engine.change.Meaning
import arcanum.engine.change.Recorder
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.query.Context
import arcanum.engine.query.Evaluator
import arcanum.engine.state.Bindings
import arcanum.engine.state.NamedValue
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.TargetRef
import arcanum.engine.state.TurnCount

/**
 * Resolving the top of the Stack (CR 608): checking targets (CR 608.2b), running its steps one committed step at a
 * time ([Continuation.Resolving], D31, D58), effects and their replacements (CR 614, 616; [Continuation.Event]), and
 * the spell's card leaving the Stack (CR 608.2n) or entering the battlefield (CR 608.3).
 *
 * A step that needs a decision is taken back and redone from its start once answered (D29); the steps before it stay
 * done, so a player sees what they drew before choosing what to discard.
 */
internal object Resolution {
    /** Resolves the top object of the Stack (CR 117.4, 608.1). */
    fun resolveTop(recorder: Recorder): Progress {
        val top = recorder.state.topOfStack() ?: return Progress.Done
        val item = recorder.state.stackItems[top] ?: return Progress.Done
        val act = Act(recorder, Asker.NONE)
        val names = start(act, item) ?: return Progress.Done
        return run(recorder, Continuation.Resolving(top, 0, names))
    }

    fun resume(recorder: Recorder, continuation: Continuation.Resolving, answer: Answer): Progress =
        run(recorder, continuation.copy(answers = continuation.answers + answer))

    fun resumeEvent(recorder: Recorder, continuation: Continuation.Event, answer: Answer): Progress =
        Events.resume(recorder, continuation, answer)

    /** The steps of [item]: the chosen modes' steps in order, then the common ones (CR 700.2). */
    fun stepsOf(item: StackItem): List<Step> {
        val instructions = Replacements.instructionsOf(item.ability) ?: return emptyList()
        return item.modes.flatMap { instructions.modes?.options?.getOrNull(it)?.instructions?.steps.orEmpty() } + instructions.steps
    }

    fun causeOf(item: StackItem) = Cause(item.controller, item.source ?: item.id, item, item.id.toString())

    private fun run(recorder: Recorder, start: Continuation.Resolving): Progress {
        var c = start
        while (true) {
            val item = recorder.state.stackItems[c.item] ?: return Progress.Done
            val steps = stepsOf(item)
            val mark = recorder.changes.size
            val act = Act(recorder, Asker(c.answers, "${c.item}/${c.step}"))
            try {
                if (c.step < steps.size) {
                    val scope = Scope(act, causeOf(item), c.names, item.event)
                    Steps.run(scope, steps[c.step])
                    c = c.copy(step = c.step + 1, names = scope.names, answers = emptyList())
                } else {
                    finish(act, item, fizzled = false)
                    return Progress.Done
                }
            } catch (n: NeedDecision) {
                recorder.rollback(mark)
                return Progress.Waiting(Pending(n.decision, c))
            }
        }
    }

    /**
     * Before the steps (CR 608.2b): targets no longer legal are left out, and a spell or ability all of whose targets
     * are illegal doesn't resolve; a triggered ability's intervening "if" is checked again (CR 603.4). Returns the names
     * the steps start with, or null when it was removed instead.
     */
    private fun start(act: Act, item: StackItem): Bindings? {
        val instructions = Replacements.instructionsOf(item.ability) ?: Instructions()
        val slots = instructions.targets + item.modes.flatMap { instructions.modes?.options?.getOrNull(it)?.instructions?.targets.orEmpty() }
        val source = item.source ?: item.id
        var names = item.names
        item.announced.forEach { (name, value) -> names = names.with(name, NamedValue.Number(value)) }
        var anyTargets = false
        var anyLegal = false
        item.targets.forEach { (slotName, chosen) ->
            if (chosen.isEmpty()) return@forEach
            anyTargets = true
            // Repeated modes keep their slots as "name@position" (casting's targetKey): bound under the name too
            val baseName = slotName.substringBefore('@')
            val slot = slots.firstOrNull { it.name == baseName }
            val legal = chosen.filter { ref ->
                if (slot == null) when (ref) {
                    is TargetRef.Object -> act.state.obj(ref.id) != null || ref.id in act.state.stackItems
                    is TargetRef.Player -> !act.state.player(ref.id).lost
                } else Targeting.legal(act, slot, ref, item.controller, source, item.names, item)
            }
            if (legal.isNotEmpty()) anyLegal = true
            names = names.with(slotName, NamedValue.Targets(legal))
            if (baseName != slotName && names[baseName] == null) names = names.with(baseName, NamedValue.Targets(legal))
        }
        if (anyTargets && !anyLegal) {
            // CR 608.2b: it doesn't resolve; a spell goes to its owner's graveyard
            finish(act, item, fizzled = true)
            return null
        }
        val e = act.evaluator(Context(item.controller, source, names, event = item.event, stackItem = item))
        (item.ability as? Ability.Triggered)?.interveningIf?.let { condition ->
            if (!e.holds(condition)) {
                act.record(Change.StackItemRemoved(item, act.state.stack.indexOf(item.id), emptyList()))
                return null
            }
        }
        // "If this is the second time this ability has resolved this turn" counts this resolution
        item.origin?.let { origin ->
            val o = act.state.obj(source) ?: return@let
            val turn = act.state.turn.number
            val key = origin.key
            val count = (o.facts.resolutions.firstOrNull { it.key == key && it.turn == turn }?.count ?: 0) + 1
            val resolutions = o.facts.resolutions.filterNot { it.key == key && it.turn == turn } + TurnCount(key, turn, count)
            act.record(Change.Facts(source, o.facts, o.facts.copy(resolutions = resolutions)))
            names = names.with(Evaluator.USES_RESOLVED, NamedValue.Number(count))
            val activated = o.facts.activations.firstOrNull { it.key == key && it.turn == turn }?.count ?: 0
            names = names.with(Evaluator.USES_ACTIVATED, NamedValue.Number(activated))
        }
        return names
    }

    /**
     * The item leaves the Stack (CR 608.2n): an instant or sorcery card goes to its owner's graveyard (or where its
     * permission says, flashback CR 702.34a); a permanent spell enters the battlefield under its controller's control,
     * an Aura attached to what it targeted (CR 608.3, 303.4f); abilities and copies just leave.
     */
    private fun finish(act: Act, item: StackItem, fizzled: Boolean) {
        val meaning = if (fizzled) Meaning.Fizzled else Meaning.Resolved
        val card = act.state.obj(item.id)
        if (item.kind != StackItemKind.Spell || card == null) {
            act.record(Change.StackItemRemoved(item, act.state.stack.indexOf(item.id).takeIf { it >= 0 && card == null }, listOf(meaning)))
            return
        }
        val c = act.view()[item.id]?.characteristics
        val permanent = c != null && PERMANENT_TYPES.any { c.isType(it) }
        act.record(Change.StackItemRemoved(item, null, listOf(meaning)))
        if (permanent && !fizzled) {
            val attachTo = if (c.subtypes.any { it.name == "Aura" }) item.targets.values.flatten().firstOrNull() else null
            // CR 708.4: a face-down spell enters face down
            Actions.move(act, null, listOf(PlannedMove(item.id, Zone.Battlefield, controller = item.controller,
                meanings = listOf(Meaning.Resolved), attachTo = attachTo, faceDown = card.faceDown)))
            return
        }
        Actions.move(act, null, listOf(PlannedMove(item.id, leavesTo(act, item), meanings = listOf(meaning))))
    }

    /** Where a resolved or countered spell's card goes: its owner's graveyard, unless the permission it was cast with
     * says otherwise (flashback: exile, CR 702.34a). */
    private fun leavesTo(act: Act, item: StackItem): Zone {
        val o = act.state.obj(item.id) ?: return Zone.Graveyard
        val from = o.facts.castFrom ?: return Zone.Graveyard
        val permissions = act.view()[item.id]?.characteristics?.abilities.orEmpty()
            .flatMap { (it.ability as? Ability.Static)?.mechanics.orEmpty() }.filterIsInstance<Mechanic.MayPlay>()
        val permission = permissions.firstOrNull { it.from == from && it.then != null } ?: return Zone.Graveyard
        return if (permission.thenInsteadOf == null || permission.thenInsteadOf == Zone.Graveyard) permission.then!!.zone else Zone.Graveyard
    }

    private val PERMANENT_TYPES = listOf(CardType.Artifact, CardType.Battle, CardType.Creature, CardType.Enchantment,
        CardType.Land, CardType.Planeswalker)
}
