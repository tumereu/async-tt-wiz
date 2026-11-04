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
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
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
    /**
     * Why [input] (a land play, cast or activation) can't be begun on [snap]'s state with nothing open: the refusal its
     * flow below throws before it does anything, word for word ([arcanum.engine.Engine.refusal]); null when it may be
     * begun, or for a card the state doesn't know (a viewer's state fills it in first, [fillPlayed]) and other inputs.
     */
    fun refusal(snap: Snapshot, input: Input): String? = when (input) {
        is Input.PlayLand -> if (snap.state.obj(input.card)?.unknown == true) null
            else Legality.cantPlayLand(snap, input.player, input.card, input.face)?.let { "Can't play that land: $it" }
        is Input.Cast -> if (snap.state.obj(input.card)?.unknown == true) null
            else Legality.cantCast(snap, input.player, input.card, input.way)?.let { "Can't cast that: $it" }
        is Input.Activate -> if (!arcanum.engine.foretell.Foretell.isForetell(input.ability) && snap.state.obj(input.source)?.unknown == true) null
            else when (val instance = Legality.instance(snap, input.source, input.ability)) {
                null -> "${input.source} has no ability ${input.ability.key}"
                else -> Legality.cantActivate(snap, input.player, input.source, instance)?.let { "Can't activate that: $it" }
            }
        else -> null
    }

    fun playLand(recorder: Recorder, input: Input.PlayLand): Progress {
        fillPlayed(recorder, input, input.player, input.card, "played")?.let { return it }
        val snap = recorder.snapshot()
        refusal(snap, input)?.let { throw IllegalArgumentException(it) }
        val s = recorder.state
        // CR 305.1: a special action; the land simply enters the battlefield (CR 712.11: with the face played up)
        val turn = s.turn
        recorder.record(Changes.step(s, turn.copy(landsPlayed = turn.landsPlayed + (input.player to (turn.landsPlayed[input.player] ?: 0) + 1))))
        // The face played is up as it enters, so that face's "as this land enters" applies (CR 712.11, 614.12)
        s.obj(input.card)?.let { if (input.face != it.face) recorder.record(Change.Face(input.card, it.face, input.face)) }
        // Through the event pipeline: "enters tapped", "as this land enters" choices (CR 614.12); a choice waits
        // eoe: "Each land played this way enters tapped" (the permission it is played with, CR 614.1c)
        val fromHand = s.obj(input.card)?.let { it.zone == Zone.Hand && it.owner == input.player } == true
        val tapped = !fromHand && Legality.permissions(snap, input.player, input.card, PlayKind.Play).firstOrNull()
            ?.let { (it.second.mechanic as? arcanum.api.Mechanic.MayPlay)?.landsEnterTapped } == true
        val entering = arcanum.engine.action.Events.run(recorder, arcanum.engine.action.PlannedEvent.Move(listOf(input.card), Zone.Battlefield,
            listOf(Meaning.PlayedLand), under = input.player, tapped = tapped, controller = input.player, source = input.card))
        if (entering is Progress.Waiting) return entering
        keepPriority(recorder, input.player)
        return Progress.Done
    }

    fun cast(recorder: Recorder, input: Input.Cast, from: Int = recorder.changes.size): Progress {
        fillPlayed(recorder, input, input.player, input.card, "cast")?.let { return it }
        val snap = recorder.snapshot()
        refusal(snap, input)?.let { throw IllegalArgumentException(it) }
        val o = recorder.state.objOrThrow(input.card)
        val fromHand = o.zone == Zone.Hand && o.owner == input.player
        val permitted = if (fromHand && input.way.permission == null) null
            else Legality.permission(snap, input.player, input.card, input.way, PlayKind.Cast)
        val way = input.way.copy(permission = permitted?.first)
        val alternative = Legality.alternative(snap, input.card, way, input.player)
        val faceDown = alternative?.second?.faceDown == true
        val start = from
        // CR 601.2a: the card moves to the Stack and becomes the topmost object there
        recorder.record(Changes.move(recorder.state, listOf(input.card), Zone.Stack, controller = input.player, faceDown = faceDown))
        val onStack = (input.card as ObjectId.Card).next()
        // CR 702.146a, 712.11a: a double-faced card cast transformed (disturb) is its back face on the Stack and enters that way
        val castTransformed = (permitted?.second?.mechanic as? arcanum.api.Mechanic.MayPlay)?.transformed == true &&
            o.definition?.layout == arcanum.api.Layout.Transform && (o.definition.faces.size) > 1
        val face = if (castTransformed) 1 else way.face
        if (face != recorder.state.objOrThrow(onStack).face) recorder.record(Change.Face(onStack, recorder.state.objOrThrow(onStack).face, face))
        // dsk gaps: from here on it is a spell being cast from where it was (CR 601.2a), so "a spell you cast from exile"
        // is known as its costs are determined (CR 601.2b, f)
        recorder.state.objOrThrow(onStack).let { moved ->
            recorder.record(Change.Facts(onStack, moved.facts, moved.facts.copy(castFrom = o.zone, fused = way.fused)))
        }
        var names = Bindings.EMPTY.with(Plan.FROM, NamedValue.Option(Plan.zoneName(o.zone)))
        if (faceDown) names = names.with(FACE_DOWN, NamedValue.Option(alternative!!.second.keyword?.id ?: "morph"))
        // gtc: a fused split spell is both halves, left then right (CR 702.102d): as its two "modes", both chosen
        if (way.fused) names = names.with(Plan.MODES_ASKED, NamedValue.Number(1))
        val c = Continuation.Announcing(input.player, Announced.Spell(onStack, way, permitted?.second?.mechanic as? arcanum.api.Mechanic.MayPlay,
            alternativeAsCast = alternative?.second), AnnounceStage.Modes,
            Choices(names = names, modes = if (way.fused) listOf(0, 1) else emptyList()))
        return advance(recorder, c, start)
    }

    fun activate(recorder: Recorder, input: Input.Activate, from: Int = recorder.changes.size): Progress {
        // CR 702.143a: foretelling keeps the card hidden, so a viewer's state takes it unknown (D60)
        if (!arcanum.engine.foretell.Foretell.isForetell(input.ability))
            fillPlayed(recorder, input, input.player, input.source, "activated")?.let { return it }
        val snap = recorder.snapshot()
        refusal(snap, input)?.let { throw IllegalArgumentException(it) }
        val instance = checkNotNull(Legality.instance(snap, input.source, input.ability))
        // CR 602.2a: the ability is created on the Stack (its item is added once its costs are paid)
        val id = "a${recorder.state.nextTimestamp()}.${recorder.state.stack.size}"
        val c = Continuation.Announcing(input.player, Announced.Ability(input.source, input.ability, instance.ability),
            AnnounceStage.Modes, Choices(names = Bindings.EMPTY.with(Plan.ITEM, NamedValue.Option(id))))
        return advance(recorder, c, from)
    }

    /**
     * D60: in a viewer's state ([arcanum.engine.state.GameState.viewer]), another player plays, casts or activates a card
     * the viewer doesn't know (a host keeping one state per player forwards their input). The card becomes public as it
     * is played: a land enters face up (CR 305.1), a spell's card moves to the Stack (CR 601.2a), an ability activated
     * from a hidden zone reveals its card (CR 602.2a), a face-down permanent's only ability turns it face up (CR 708.2,
     * 702.37e). So the host fills exactly that card in publicly ([Decision.HostFill]) and the input is redone
     * ([Continuation.Played]); its legality is checked then. Null when the card is known.
     */
    private fun fillPlayed(recorder: Recorder, input: Input, player: arcanum.engine.state.PlayerId, card: ObjectId, reason: String): Progress? {
        val o = recorder.state.obj(card) ?: return null
        if (!o.unknown) return null
        val decision = Decision.HostFill(decisionId(recorder.state, "played"), player, card, listOf(card), reason, public = true)
        return Progress.Waiting(Pending(decision, Continuation.Played(input)))
    }

    /** The card of a [Continuation.Played] is filled in (checked against its [Decision.HostFill]): the input is redone. */
    fun resumePlayed(recorder: Recorder, continuation: Continuation.Played, answer: Answer): Progress {
        // The fill is part of the cast or activation: cancelling it forgets the card again (CR 601.2, D58)
        val from = recorder.changes.size
        val filled = (answer as Answer.Filled).objects
        filled.forEach { f ->
            val before = recorder.state.objOrThrow(f.id)
            recorder.record(Change.Filled(before, before.copy(definition = f.definition)))
        }
        return when (val input = continuation.input) {
            is Input.PlayLand -> playLand(recorder, input)
            is Input.Cast -> cast(recorder, input, from)
            is Input.Activate -> activate(recorder, input, from)
            else -> throw IllegalStateException("Only plays, casts and activations wait for their card: $input")
        }
    }

    fun resume(recorder: Recorder, continuation: Continuation.Announcing, answer: Answer): Progress {
        val start = recorder.changes.size
        if (continuation.choices.names[Plan.MANUAL] != null) {
            require(answer == Answer.Done) { "Answer the manual step with Done" }
            return Progress.Done
        }
        // A payment that waited for a decision is redone with the answers so far (D29)
        continuation.choices.paying?.let { paying ->
            return Payment.finish(recorder, continuation.copy(choices = continuation.choices.copy(paying = null)), paying.uses, start,
                paying.answers + answer)
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

    /** [Input.Cancel]: [player] takes back their open cast or activation, everything it did, last first (CR 601.2,
     * D58). Only the player casting or activating it may. */
    fun cancel(recorder: Recorder, continuation: Continuation, player: arcanum.engine.state.PlayerId): Progress {
        if (continuation is Continuation.Played) {
            val by = when (val i = continuation.input) {
                is Input.PlayLand -> i.player
                is Input.Cast -> i.player
                is Input.Activate -> i.player
                else -> null
            }
            require(by == player) { "Only $by can take back their play, not $player" }
            return Progress.Done
        }
        val open = continuation as? Continuation.Announcing
            ?: throw IllegalArgumentException("Only a cast or an activation can be cancelled")
        require(open.player == player) { "Only ${open.player} can cancel what they are casting or activating, not $player" }
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
            // the input (or answer) is refused; an open cast can still be cancelled. Everything known so far is checked,
            // not only the next question, so a cast isn't begun that can't be finished.
            plan.blocker(q)?.let { throw IllegalArgumentException(it) }
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
            val decision = plan.decision(q, decisionId(recorder.state, "cast:${q.stage}:${q.kind}"))
            return Progress.Waiting(Pending(decision, c.copy(stage = q.stage, made = c.made + recorder.madeSince(from))))
        }
    }

    /**
     * [player] casts [card] while a spell or ability resolves (CR 608.2g): the steps of CR 601.2a–i on the same [Plan] as
     * [cast], on the effect's terms: without paying its mana cost ([withoutPaying], an alternative cost, CR 118.9; X is
     * then 0, CR 107.3b), for [cost] instead of its mana cost (madness, CR 702.35a), or else for its own mana cost or an
     * alternative cost the player announces ([chooseAlternative], CR 601.2b). Its face is chosen when it has several to
     * cast (CR 709.3, 715.3, 712.11); modes, optional and additional costs, X, targets, objects for costs and mana are
     * asked with [act]'s decisions, redone with their answers (D29); a target slot with only as many legal targets as it needs takes them without asking (P5). Nobody gets priority afterwards.
     * Returns the spell, or null when it can't be cast (a prohibition, too few legal targets or objects for its costs, no
     * mana to pay with; CR 101.2, 601.2c, 601.2h, 118.3): then nothing about it happened and the card stays where it is.
     */
    fun castByEffect(act: arcanum.engine.action.Act, player: arcanum.engine.state.PlayerId, card: ObjectId,
                     withoutPaying: Boolean, cost: arcanum.api.Cost?, transformed: Boolean = false, manaAsAnyType: Boolean = false): ObjectId? {
        val o = act.state.obj(card) ?: return null
        val snap = Snapshot(act.state)
        // CR 712.11a: cast transformed, a transforming double-faced card is its back face (a Siege, CR 310.12b)
        val castTransformed = transformed && o.definition?.layout == arcanum.api.Layout.Transform && o.definition.faces.size > 1
        val faces = (if (castTransformed) listOf(1) else Legality.castableFaces(o)).filter { Legality.cantCastByEffect(snap, player, card, it) == null }
        if (faces.isEmpty()) return null
        val face = if (faces.size == 1) faces.single() else {
            val names = faces.map { o.definition!!.faces[it].name }
            val chosen = names.indexOf(act.chooseOption(player, card, names))
            require(chosen >= 0) { "Not a face of ${o.definition?.faces?.firstOrNull()?.name}" }
            faces[chosen]
        }
        val alternative = chooseAlternative(act, snap, player, card, face, withoutPaying || cost != null)
        val mark = act.recorder.changes.size
        fun cannot(): ObjectId? {
            act.recorder.rollback(mark)
            return null
        }
        // CR 601.2a: the card moves to the Stack with the face cast up
        act.record(Changes.move(act.state, listOf(card), Zone.Stack, controller = player))
        val onStack = (card as ObjectId.Card).next()
        val now = act.state.objOrThrow(onStack)
        if (face != now.face) act.record(Change.Face(onStack, now.face, face))
        // As for a cast with priority: from here on it is a spell cast from where its card was (CR 601.2a), so an
        // alternative cost for "a spell you cast from exile" still applies as its costs are determined (CR 601.2b, f)
        act.record(Change.Facts(onStack, now.facts, now.facts.copy(castFrom = o.zone)))
        // fin: "mana of any type can be spent to cast that spell" (CR 609.4b)
        val terms = arcanum.api.Mechanic.MayPlay(o.zone, PlayKind.Cast, cost = cost, withoutPaying = withoutPaying && cost == null,
            holder = arcanum.api.PlayerFilter.You, manaAsAnyColor = manaAsAnyType)
        var asked = false
        var c = Continuation.Announcing(player, Announced.Spell(onStack, CastWay(face, alternative = alternative), terms, byEffect = true), AnnounceStage.Modes,
            Choices(names = Bindings.EMPTY.with(Plan.FROM, NamedValue.Option(Plan.zoneName(o.zone)))))
        while (true) {
            val plan = Plan(act.state, c)
            val q = plan.next()
            // Blocked from the start, it can't be cast; blocked by an answer, that answer is refused (CR 601.2, 727)
            plan.blocker(q)?.let { why -> if (asked) throw IllegalArgumentException(why) else return cannot() }
            if (q == null) break
            if (q is Question.Pay) {
                // CR 118.3: a cost that can't be paid isn't; the spell then can't be cast
                if (arcanum.engine.mana.Mana.suggest(plan.snap, player, q.cost, plan.itemId, plan.manaWays, !plan.cantSpendMana, plan.manaAsAnyColor).isEmpty()) return cannot()
                val uses = (act.ask { plan.decision(q, it) } as? Answer.ManaPayment)?.uses
                    ?: throw IllegalArgumentException("Expected a mana payment")
                Payment.finish(act.recorder, c, uses, mark)
                return onStack
            }
            val answer = Answers.automatic(plan, q) ?: forcedByEffect(plan, q) ?: act.ask { plan.decision(q, it) }.also { asked = true }
            c = c.copy(choices = Answers.apply(plan, q, answer))
        }
        Payment.finish(act.recorder, c, emptyList(), mark)
        return onStack
    }

    /**
     * The alternative cost [player] announces for a spell cast by an effect (CR 601.2b, 118.9), as for a cast with
     * priority ([Legality.alternativeCosts]: the card's own and those another object gives the spells its controller
     * casts, each within its limit each turn), or null for its mana cost. None when the effect's terms already are an
     * alternative cost ([effectsCost]: without paying its mana cost, madness; only one may be used, CR 118.9a), and no
     * face-down one (a card cast by an effect is cast face up). Asked only when there is one to choose.
     */
    private fun chooseAlternative(act: arcanum.engine.action.Act, snap: Snapshot, player: arcanum.engine.state.PlayerId,
                                  card: ObjectId, face: Int, effectsCost: Boolean): arcanum.engine.view.AbilityOrigin? {
        if (effectsCost) return null
        val alternatives = Legality.alternativeCosts(snap, card, player).filter { !it.second.faceDown }
        if (alternatives.isEmpty()) return null
        val manaCost = snap.state.obj(card)?.definition?.faces?.getOrNull(face)?.manaCost
        val labels = listOf(if (manaCost != null) "Pay its mana cost $manaCost" else "Pay no mana cost") + alternatives.map { (inst, m) ->
            val by = (inst.origin as? arcanum.engine.view.AbilityOrigin.FromAbility)?.source?.let { snap.view[it]?.characteristics?.name }
            val what = m.keyword?.let { "${it.id.replace('-', ' ').replaceFirstChar { c -> c.uppercase() }} " +
                arcanum.engine.resolution.Costs.describe(m.cost) }
                ?: "Pay ${arcanum.engine.resolution.Costs.describe(m.cost)} rather than its mana cost"
            if (by != null) "$what ($by)" else what
        }
        val chosen = labels.indexOf(act.chooseOption(player, card, labels))
        require(chosen >= 0) { "Not a way to pay for it: $labels" }
        return if (chosen == 0) null else alternatives[chosen - 1].first.origin
    }

    /** Answers a spell cast by an effect takes without asking: X is 0 when no cost paid includes it (CR 107.3b); a target
     * slot with no more legal targets than it needs takes them all (P5, as a trigger's does). */
    private fun forcedByEffect(plan: Plan, q: Question): Answer? = when {
        q is Question.Announce && q.name == "X" && !Costs.mentionsX(plan.contributions) -> Answer.Number(0)
        q is Question.Target && q.options.isNotEmpty() && q.options.size <= q.min -> Answer.Targets(q.options)
        else -> null
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
