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
        var fizzles = false
        val names = start(act, item) { fizzles = true } ?: return if (fizzles) fizzle(recorder, item) else Progress.Done
        return run(recorder, Continuation.Resolving(top, 0, names))
    }

    /**
     * CR 608.2b: a spell or ability that doesn't resolve leaves the Stack; a spell's card goes to its owner's graveyard
     * (or where its permission says) as an event outside resolution ([Events.run]), so a replacement's order or a host
     * fill (CR 708.9) waits for its answer.
     */
    private fun fizzle(recorder: Recorder, item: StackItem): Progress {
        val act = Act(recorder, Asker.NONE)
        if (item.kind != StackItemKind.Spell || recorder.state.obj(item.id) == null) {
            finish(act, item, fizzled = true)
            return Progress.Done
        }
        val to = leavesTo(act, item)
        recorder.record(Change.StackItemRemoved(item, null, listOf(Meaning.Fizzled)))
        return Events.run(recorder, arcanum.engine.action.PlannedEvent.Move(listOf(item.id), to, listOf(Meaning.Fizzled),
            controller = item.controller, source = item.id))
    }

    fun resume(recorder: Recorder, continuation: Continuation.Resolving, answer: Answer): Progress {
        if (continuation.otherView != null) {
            val sent = answer as Answer.OtherView
            return OtherView.run(recorder, continuation.copy(answers = continuation.answers + sent.answers),
                sent.filled.associate { it.id to it.definition })
        }
        return run(recorder, continuation.copy(answers = continuation.answers + answer))
    }

    fun resumeEvent(recorder: Recorder, continuation: Continuation.Event, answer: Answer): Progress =
        Events.resume(recorder, continuation, answer)

    /** The steps of [item]: the chosen modes' steps in order, then the common ones (CR 700.2). */
    fun stepsOf(item: StackItem): List<Step> {
        val instructions = Replacements.instructionsOf(item.ability) ?: return emptyList()
        return item.modes.flatMap { instructions.modes?.options?.getOrNull(it)?.instructions?.steps.orEmpty() } + instructions.steps
    }

    fun causeOf(item: StackItem) = Cause(item.controller, selfOf(item), item, item.id.toString())

    // gtc
    /**
     * The names the step at [step] of [item] starts with: as a chosen mode's steps begin, each of its slots kept as
     * "name@position" (casting's targetKey: a name an earlier chosen mode used, CR 700.2d) is bound under its name, so
     * the mode's steps read its own targets (a fused split spell's right half, CR 702.102d).
     */
    private fun modeNames(item: StackItem, step: Int, names: Bindings): Bindings {
        val instructions = Replacements.instructionsOf(item.ability) ?: return names
        val options = instructions.modes?.options ?: return names
        var start = 0
        item.modes.forEachIndexed { position, index ->
            val mode = options.getOrNull(index) ?: return names
            if (step == start && mode.instructions.steps.isNotEmpty()) {
                return mode.instructions.targets.fold(names) { n, slot ->
                    val key = arcanum.engine.casting.targetKey(instructions, item.modes, position, slot.name)
                    val value = names[key]
                    if (key != slot.name && value != null) n.with(slot.name, value) else n
                }
            }
            start += mode.instructions.steps.size
        }
        return names
    }

    /** What "this" is while [item] resolves: a copy of a spell is its own object (CR 707.10), not the card it copies;
     * an ability's is its source (a copy of an ability has the original's source, CR 707.10b). */
    fun selfOf(item: StackItem): arcanum.engine.state.ObjectId =
        if (item.kind is StackItemKind.SpellCopy) item.id else item.source ?: item.id

    internal fun run(recorder: Recorder, start: Continuation.Resolving): Progress {
        var c = start
        while (true) {
            val item = c.left ?: recorder.state.stackItems[c.item] ?: return Progress.Done
            val steps = stepsOf(item)
            val mark = recorder.changes.size
            val act = Act(recorder, Asker(c.answers, "${c.item}/${c.step}", mayAskOtherView = true))
            try {
                // dsk gaps: "Do this only once each turn" (CR 603.2h): once its action was taken this turn, it does nothing
                val once = (item.ability as? Ability.Triggered)?.takeIf { it.actionOncePerTurn } != null
                if (once && c.step == 0 && arcanum.engine.trigger.Triggers.actionTaken(recorder.state, item.source, item.origin, item.controller)) {
                    c = c.copy(step = steps.size)
                    continue
                }
                if (c.step < steps.size) {
                    val scope = Scope(act, causeOf(item), modeNames(item, c.step, c.names), item.event)
                    Steps.run(scope, steps[c.step])
                    if (once && c.step == actionStep(steps) && actionDone(steps[c.step], scope, recorder.changes.size > mark)) {
                        actionTakenBy(act, item)
                    }
                    // Its card may have left the Stack (CR 400.7): the item stays with the flow for the steps left
                    val left = c.left ?: item.takeIf { it.id !in recorder.state.stackItems }
                    c = c.copy(step = c.step + 1, names = scope.names, answers = emptyList(), left = left)
                } else {
                    if (c.left == null) finish(act, item, fizzled = false)
                    return Progress.Done
                }
            } catch (n: NeedDecision) {
                recorder.rollback(mark)
                return Progress.Waiting(Pending(n.decision, c))
            } catch (n: arcanum.engine.action.NeedOtherView) {
                // D60, D31: cards only another player may look at; from this step on, worked out as in their view
                recorder.rollback(mark)
                return OtherView.start(c, n)
            }
        }
    }

    // dsk gaps
    /** The step that is the action of a "Do this only once each turn" ability (CR 603.2h): its first optional part, or
     * its last step when it has none. */
    private fun actionStep(steps: List<Step>): Int =
        steps.indexOfFirst { (it is Step.Pay && !it.mandatory) || it is Step.May }.takeIf { it >= 0 } ?: steps.lastIndex

    /** Whether the action [step] was taken: a payment made, a "you may" that did something, any other step done. */
    private fun actionDone(step: Step, scope: Scope, changed: Boolean): Boolean = when (step) {
        is Step.Pay -> (step.bind?.let { scope.names[it] } as? NamedValue.Payment)?.outcome == arcanum.engine.state.PayOutcome.Paid ||
            (step.bind == null && changed)
        is Step.May -> changed
        else -> true
    }

    /** Counts the action of [item]'s ability as taken this turn by its controller, on its source (D41). */
    private fun actionTakenBy(act: Act, item: StackItem) {
        val origin = item.origin ?: return
        val o = item.source?.let { act.state.obj(it) } ?: return
        val key = arcanum.engine.trigger.Triggers.actionKey(origin, item.controller)
        act.record(Change.Facts(o.id, o.facts, o.facts.copy(
            activations = arcanum.engine.casting.Legality.counted(o.facts.activations, key, act.state.turn.number))))
    }

    /**
     * Before the steps (CR 608.2b): targets no longer legal are left out, and a spell or ability all of whose targets
     * are illegal doesn't resolve; a triggered ability's intervening "if" is checked again (CR 603.4). Returns the names
     * the steps start with, or null when it was removed instead.
     */
    private fun start(act: Act, item: StackItem, fizzles: () -> Unit): Bindings? {
        val instructions = Replacements.instructionsOf(item.ability) ?: Instructions()
        val slots = instructions.targets + item.modes.flatMap { instructions.modes?.options?.getOrNull(it)?.instructions?.targets.orEmpty() }
        val source = selfOf(item)
        var names = item.names
        item.announced.forEach { (name, value) -> names = names.with(name, NamedValue.Number(value)) }
        var anyTargets = false
        var anyLegal = false
        // A slot's filter may name another slot's target ("artifact card in that player's graveyard")
        val chosenNames = item.targets.entries.fold(item.names) { n, (k, v) -> n.with(k, NamedValue.Targets(v)) }
        // gtc: a mode's slot kept as "name@position" is checked against that mode's slot (a fused spell's halves, CR 702.102d)
        val keyed = arcanum.engine.casting.Targets.slots(instructions, item.modes).associate { it.key to it.slot }
        item.targets.forEach { (slotName, chosen) ->
            if (chosen.isEmpty()) return@forEach
            anyTargets = true
            // Repeated modes keep their slots as "name@position" (casting's targetKey): bound under the name too
            val baseName = slotName.substringBefore('@')
            val slot = keyed[slotName] ?: slots.firstOrNull { it.name == baseName }
            val legal = chosen.filter { ref ->
                if (slot == null) when (ref) {
                    is TargetRef.Object -> act.state.obj(ref.id) != null || ref.id in act.state.stackItems
                    is TargetRef.Player -> !act.state.player(ref.id).lost
                } else Targeting.legal(act, slot, ref, item.controller, source, chosenNames, item)
            }
            if (legal.isNotEmpty()) anyLegal = true
            names = names.with(slotName, NamedValue.Targets(legal))
            if (baseName != slotName && names[baseName] == null) names = names.with(baseName, NamedValue.Targets(legal))
        }
        if (anyTargets && !anyLegal && act.state.obj(item.id)?.bestowed == true) {
            // CR 702.103e, 608.3b: a bestowed Aura spell whose target is illegal resolves as a creature spell
            arcanum.engine.sba.StateBasedActions.Bestow.cease(act, item.id)
        } else if (anyTargets && !anyLegal && act.state.obj(item.id)?.let { arcanum.engine.mutate.Mutate.mutating(it) } == true) {
            // CR 702.140b: a mutating creature spell whose target is illegal resolves as a creature spell
        } else if (anyTargets && !anyLegal) {
            // CR 608.2b: it doesn't resolve; a spell goes to its owner's graveyard
            fizzles()
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
     * blb: CR 702.174c: a player gives a gift as an instant or sorcery spell they control whose gift was promised
     * resolves, or as the gift trigger of a permanent they control resolves (the triggered ability of the gift keyword's
     * expansion, [arcanum.api.KeywordExpansions]).
     */
    private fun givesGift(act: Act, item: StackItem): Boolean {
        if (item.kind == StackItemKind.Spell || item.kind is StackItemKind.SpellCopy) {
            val types = act.view()[item.id]?.characteristics?.types.orEmpty()
            if (types.none { it == arcanum.api.CardType.Instant || it == arcanum.api.CardType.Sorcery }) return false
            val facts = item.facts ?: act.state.obj(item.id)?.facts ?: return false
            return facts.paid.any { it.keyword == arcanum.api.Keyword.Gift.Kind && it.times > 0 }
        }
        val origin = item.origin as? arcanum.engine.view.AbilityOrigin.Expansion ?: return false
        if (item.ability !is arcanum.api.Ability.Triggered) return false
        val printed = origin.keyword as? arcanum.engine.view.AbilityOrigin.Printed ?: return false
        val card = (item.source as? arcanum.engine.state.ObjectId.Card)?.card ?: return false
        val definition = act.state.objects.values.firstOrNull { it.card == card }?.definition ?: return false
        return definition.faces.getOrNull(printed.face)?.abilities?.getOrNull(printed.index) is arcanum.api.Keyword.Gift
    }

    /** tla: whether [item] is the triggered ability of a firebending keyword's expansion (CR 702.189a), printed or granted. */
    private fun isFirebending(item: StackItem): Boolean {
        if (item.origin !is arcanum.engine.view.AbilityOrigin.Expansion) return false
        val ability = item.ability as? arcanum.api.Ability.Triggered ?: return false
        val add = (ability.instructions.steps.singleOrNull() as? arcanum.api.Step.Do)?.effect as? arcanum.api.Effect.AddMana ?: return false
        val kind = add.mana.kind as? arcanum.api.ManaKind.Fixed ?: return false
        return add.mana.keptUntil == arcanum.api.Duration.EndOfCombat && kind.mana == arcanum.api.ManaCost.parse("{R}") &&
            ability.trigger == arcanum.api.Trigger.On(arcanum.api.EventPattern.Attack(arcanum.api.ObjectFilter.Is(arcanum.api.Objects.Self)))
    }

    /**
     * The item leaves the Stack (CR 608.2n): an instant or sorcery card goes to its owner's graveyard (or where its
     * permission says, flashback CR 702.34a); a permanent spell enters the battlefield under its controller's control,
     * an Aura attached to what it targeted (CR 608.3, 303.4f); abilities and copies just leave.
     */
    internal fun finish(act: Act, item: StackItem, fizzled: Boolean) {
        // sos: paradigm reads which names resolved earlier this game (CR 702.192a): counted once the spell has finished resolving
        val resolvedNames = if (fizzled || (item.kind != StackItemKind.Spell && item.kind !is StackItemKind.SpellCopy)) emptyList()
            else act.view()[item.id]?.characteristics?.name?.split(" // ").orEmpty()
        finishLeaving(act, item, fizzled)
        resolvedNames.forEach { name ->
            val key = arcanum.engine.query.Evaluator.resolvedKey(name)
            val before = act.state.player(item.controller).ledger[key] ?: 0
            act.record(Change.Ledger(item.controller, key, before, before + 1))
        }
    }

    private fun finishLeaving(act: Act, item: StackItem, fizzled: Boolean) {
        val meaning = if (fizzled) Meaning.Fizzled else Meaning.Resolved
        // CR 702.50a: epic's spell abilities, the last of the spell's instructions
        if (!fizzled) arcanum.engine.epic.Epic.resolves(act, item)
        val card = act.state.obj(item.id)
        if (!fizzled && givesGift(act, item)) act.record(Change.Happened(arcanum.engine.change.GameEvent.GaveGift(item.controller)))
        // tla: CR 702.189b: a firebending ability its controller controls resolved
        if (!fizzled && isFirebending(item)) act.record(Change.Happened(arcanum.engine.change.GameEvent.Firebended(item.controller)))
        if (item.kind is StackItemKind.SpellCopy && !fizzled && card == null) {
            // CR 707.10f, 608.3f: a copy of a permanent spell becomes a token permanent as it resolves
            val c = act.view()[item.id]?.characteristics
            val face = arcanum.engine.view.Copiable(act.state).faces(item.id)?.firstOrNull()?.face
            if (c != null && face != null && PERMANENT_TYPES.any { c.isType(it) }) {
                val attachTo = if (c.subtypes.any { it.name == "Aura" }) item.targets.values.flatten().firstOrNull() else null
                act.record(Change.StackItemRemoved(item, act.state.stack.indexOf(item.id), listOf(meaning)))
                Actions.createTokens(act, causeOf(item), face, 1, item.controller, attachTo = attachTo,
                    fromSpellCopy = item.facts ?: arcanum.engine.state.ObjectFacts())
                return
            }
        }
        if (item.kind != StackItemKind.Spell || card == null) {
            act.record(Change.StackItemRemoved(item, act.state.stack.indexOf(item.id).takeIf { it >= 0 && card == null }, listOf(meaning)))
            return
        }
        val c = act.view()[item.id]?.characteristics
        val permanent = c != null && PERMANENT_TYPES.any { c.isType(it) }
        act.record(Change.StackItemRemoved(item, null, listOf(meaning)))
        if (permanent && !fizzled) {
            // CR 702.140c: a mutating creature spell with a legal target merges with it instead of entering
            if (arcanum.engine.mutate.Mutate.mutating(card) && arcanum.engine.mutate.Mutate.resolve(act, item, card)) return
            val attachTo = if (c.subtypes.any { it.name == "Aura" }) item.targets.values.flatten().firstOrNull() else null
            // msh: sneak (CR 702.190b): tapped and attacking what the returned creature attacked
            val sneaked = card.facts.alternative?.keyword == arcanum.api.Keyword.Sneak.Kind
            val attacking = if (!sneaked) null else (item.names[arcanum.api.KeywordExpansions.SNEAK_RETURNED] as? NamedValue.Objects)?.ids
                ?.let { returned -> arcanum.engine.combat.Combat.lastTarget(act.state, returned) }
                ?: act.state.opponents(item.controller).firstOrNull()?.let { TargetRef.Player(it) }.takeIf { sneaked }
            // CR 708.4: a face-down spell enters face down
            // ecl: cards a cost of the spell kept for the permanent ("the exiled card", CR 607.2a)
            val memory = Bindings(item.names.values.filterKeys { it.startsWith(arcanum.engine.casting.Payment.REMEMBERED) }
                .mapKeys { it.key.removePrefix(arcanum.engine.casting.Payment.REMEMBERED) } +
                // spm: the names a keyword's cost made ("the returned creature" of web-slinging) stay with the permanent
                item.names.values.filterKeys { it in arcanum.api.KeywordExpansions.COST_NAMES })
            Actions.move(act, null, listOf(PlannedMove(item.id, Zone.Battlefield, controller = item.controller,
                meanings = listOf(Meaning.Resolved), attachTo = attachTo, faceDown = card.faceDown, tapped = sneaked, attacking = attacking, memory = memory)))
            return
        }
        // CR 715.4: a resolving Adventure is exiled instead, and its controller may cast the creature from exile later
        if (!fizzled && card.definition?.layout == arcanum.api.Layout.Adventure && card.face == 1) {
            val exiled = Actions.move(act, null, listOf(PlannedMove(item.id, Zone.Exile, meanings = listOf(meaning))))[item.id]
            if (exiled != null && act.state.obj(exiled)?.zone == Zone.Exile) act.record(arcanum.engine.change.Changes.effect(act.state) { ts ->
                arcanum.engine.state.EffectObject(
                    id = arcanum.engine.state.EffectId("E$ts"),
                    mechanics = listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Cast, holder = arcanum.api.PlayerFilter.You,
                        resultingSpell = arcanum.api.ObjectFilter.Not(arcanum.api.ObjectFilter.Sub(arcanum.api.Subtype("Adventure"))))),
                    affects = arcanum.api.Affected.Objects(arcanum.api.Objects.Self), locked = listOf(exiled), source = exiled,
                    controller = item.controller, timestamp = ts, createdTurn = act.state.turn.number,
                    duration = arcanum.api.Duration.Indefinitely,
                )
            })
            return
        }
        // tdm: CR 720.3d: a resolving Omen spell is shuffled into its owner's library instead
        if (!fizzled && card.definition?.layout == arcanum.api.Layout.Omen && card.face == 1) {
            Actions.move(act, null, listOf(PlannedMove(item.id, Zone.Library, meanings = listOf(meaning))))
            Actions.shuffle(act, card.owner)
            return
        }
        Actions.move(act, null, listOf(PlannedMove(item.id, leavesTo(act, item), meanings = listOf(meaning))))
    }

    /** Where a resolved or countered spell's card goes: its owner's graveyard, unless the permission it was cast with
     * says otherwise (flashback: exile, CR 702.34a). */
    private fun leavesTo(act: Act, item: StackItem): Zone {
        // fin: CR 724.1b: the spell that ended the turn is exiled as it leaves
        if (act.state.turn.endingTurn) return Zone.Exile
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
