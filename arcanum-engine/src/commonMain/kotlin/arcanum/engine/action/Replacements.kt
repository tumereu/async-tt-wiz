package arcanum.engine.action

import arcanum.api.Ability
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.engine.change.Change
import arcanum.engine.change.Meaning
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ReplacementOption
import arcanum.engine.decision.WaitingTrigger
import arcanum.engine.query.Board
import arcanum.engine.query.Context
import arcanum.engine.query.Evaluator
import arcanum.engine.state.Bindings
import arcanum.engine.state.EffectId
import arcanum.engine.state.GameObject
import arcanum.engine.state.ObjectId
import arcanum.engine.state.ObjectSnapshot
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TriggerEvent
import arcanum.engine.trigger.EventMatcher
import arcanum.engine.trigger.Triggers
import arcanum.engine.view.Characteristics

/**
 * One replacement effect that could apply (CR 614): its [mechanic], whose "you" is [controller], from [source] (a static
 * ability) or [effect] (an effect object, a shield), with the [names] it uses. [own] marks a spell's or ability's own
 * replacement (CR 614.15), applied first (CR 616.1a). [key] tells it apart, so each applies once per event (CR 614.5).
 * A [prevention] effect (CR 615) takes part in the same choice of order (CR 616.1): [mechanic] then holds only its event.
 */
internal data class Replacement(
    val mechanic: Mechanic.Replacement,
    val controller: PlayerId,
    val source: ObjectId?,
    val names: Bindings,
    val effect: EffectId?,
    val own: Boolean,
    val key: String,
    val prevention: Prevention? = null,
    /** The printed text of the ability it comes from, for a choice among replacements (CR 616.1). */
    val text: String? = null,
)

/** What a prevention effect among the replacements of a damage event prevents (CR 615, 616.1). */
internal sealed interface Prevention {
    /** All damage to [recipient] from sources [from] matches (protection, CR 702.16e). */
    data class Protection(val from: arcanum.api.ObjectFilter, val recipient: arcanum.engine.state.TargetRef) : Prevention

    /** All of the damage, or [each] of each such damage event; then its [additional] effect (CR 615.5). */
    data class Amount(val each: arcanum.api.Value?, val additional: arcanum.api.Instructions? = null) : Prevention

    /** Up to the amount left on a shield (CR 615.7, D26), used up as it prevents. */
    data class Shield(val effect: EffectId, val redirectTo: arcanum.api.Recipients? = null) : Prevention

    /** Prevents all of the damage, whatever the amount. */
    val all: Boolean get() = this is Protection || (this is Amount && each == null)

    /** Does more than prevent (CR 615.5), so the order it applies in can matter. */
    val hasAdditional: Boolean get() = this is Amount && additional != null || isRedirect

    /** rvr: a redirection shield ([arcanum.api.Effect.PreventDamage.redirectTo]): a replacement, not prevention (CR 614.9). */
    val isRedirect: Boolean get() = this is Shield && redirectTo != null
}

/**
 * Finding and applying replacement effects to one event (CR 614, 616). The event is proposed as the change it would be;
 * replacements whose pattern matches it ([EventMatcher], tested on the objects as they are before the event, CR
 * 614.12) apply one at a time: the event's own replacements first, then those that decide who controls an entering
 * permanent, then copy effects, then entering transformed (CR 616.1a–d); when two or more others remain and their order
 * matters, the affected player chooses (CR 616.1e), and the rest are checked again after each (CR 616.1f, D36).
 */
internal object Replacements {
    /** Every replacement effect in force now, the resolving [cause]'s own first. */
    fun candidates(act: Act, cause: Cause?): List<Replacement> {
        val own = cause?.item?.let { item ->
            // Its own names: targets, announced values and what its costs bound
            var names = item.names
            item.targets.forEach { (slot, refs) -> names = names.with(slot, arcanum.engine.state.NamedValue.Targets(refs)) }
            item.announced.forEach { (n, v) -> names = names.with(n, arcanum.engine.state.NamedValue.Number(v)) }
            instructionsOf(item.ability)?.replacements.orEmpty().mapIndexed { i, r ->
                Replacement(r, item.controller, item.source, names, null, own = true, key = "own:${item.id}:$i",
                    text = arcanum.engine.decision.Texts.of(item.ability))
            }
        }.orEmpty()
        val rules = act.view().game.rules.mapIndexedNotNull { i, applied ->
            val r = applied.mechanic as? Mechanic.Replacement ?: return@mapIndexedNotNull null
            val key = applied.effect?.let { "e:${it.value}:${applied.effect}" } ?: "s:${applied.source}:${applied.origin?.key}:$i"
            Replacement(r, applied.controller, applied.source, applied.names, applied.effect, own = false, key = "$key/${r.hashCode()}",
                text = arcanum.engine.decision.Texts.of(null, applied.origin, applied.source?.let { act.view()[it]?.characteristics }))
        }
        return own + rules
    }

    fun instructionsOf(ability: Ability?): arcanum.api.Instructions? = when (ability) {
        is Ability.Spell -> ability.instructions
        is Ability.Activated -> ability.instructions
        is Ability.Triggered -> ability.instructions
        else -> null
    }

    /** The instances of the proposed [change] that [r] applies to (empty: it doesn't apply). */
    fun matches(act: Act, r: Replacement, change: Change, board: Board = act.board()): List<TriggerEvent> {
        val e = Evaluator(board, Context(you = r.controller, self = r.source, names = r.names))
        val protection = r.prevention as? Prevention.Protection
        if (protection != null) {
            // CR 702.16e: damage to the protected permanent from a source with the quality
            val hit = (change as? Change.Damage)?.dealt.orEmpty().any { d ->
                d.recipient == protection.recipient &&
                    e.with(Context(r.controller, (protection.recipient as? arcanum.engine.state.TargetRef.Object)?.id)).matches(protection.from, d.source)
            }
            return if (hit) listOf(TriggerEvent()) else emptyList()
        }
        // blb: in an effect on locked objects (a spell and the permanent it becomes, CR 400.7d), "the affected object" is
        // each of them ("that creature enters with a finality counter on it")
        val locked = r.effect?.let { act.state.effects[it]?.locked }.orEmpty()
        if (locked.isNotEmpty()) return locked.flatMap { id -> EventMatcher.instances(e.with(e.ctx.copy(affected = id)), r.mechanic.event, change, proposed = true) }
        return EventMatcher.instances(e, r.mechanic.event, change, proposed = true)
    }

    /**
     * Applies replacements to one event of type [T] (D29, D36): [proposed] builds the change the event would be (null:
     * nothing left to replace); [modify] applies one replacement's modifications, returning the changed event or null
     * when it no longer happens ([Modification.Skip]). An "instead" replacement runs its instructions in place of the
     * event ([instead]) and the event doesn't happen: the result is null. [affected] chooses among replacements whose
     * order matters (CR 616.1e). Linked "when applied" triggers (CR 603.11) are returned in [linked]. [extra] are
     * candidates of this event only (prevention effects, CR 616.1); [skip] leaves out candidates for the event as it is now.
     */
    fun <T> apply(
        act: Act, cause: Cause?, initial: T, affected: PlayerId, about: ObjectId?,
        proposed: (T) -> Change?,
        modify: (T, Replacement, TriggerEvent) -> T?,
        instead: (Replacement, TriggerEvent) -> Unit,
        linked: MutableList<WaitingTrigger>,
        board: (T) -> Board = { act.board() },
        skip: (Replacement, T) -> Boolean = { _, _ -> false },
        extra: List<Replacement> = emptyList(),
    ): T? {
        val all = candidates(act, cause) + extra
        if (all.isEmpty()) return initial
        val used = mutableSetOf<String>()
        var current = initial
        while (true) {
            val change = proposed(current) ?: return current
            val matching = all.filter { it.key !in used && it.key !in act.replacing && !skip(it, current) }.mapNotNull { r ->
                matches(act, r, change, board(current)).firstOrNull()?.let { r to it }
            }
            if (matching.isEmpty()) return current
            val (r, instance) = pick(act, matching, affected, about)
            used += r.key
            if (r.mechanic.optional && !act.yesNo(r.controller, r.source, "Apply ${describe(r)}?")) continue
            useUp(act, r)
            // spm: an entering copy's linked trigger is made when the copied object is chosen (it names it), and only if one is
            r.mechanic.whenApplied?.takeIf { r.mechanic.modify.none { m -> m is arcanum.api.Modification.EntersAsCopy && m.bind != null } }
                ?.let { linked += linkedTrigger(r, it, instance) }
            if (r.mechanic.instead != null) {
                instead(r, instance)
                return null
            }
            current = modify(current, r, instance) ?: return null
        }
    }

    /** CR 616.1a–e: the next replacement to apply. */
    /**
     * inr: a draw from an empty library (CR 614.11: replacements of drawing apply even when no card could be drawn): the first "instead"
     * replacement of [player]'s draw runs [instead] and the draw doesn't happen. Returns whether one did. One that spares the first draw of
     * a step ([arcanum.api.EventPattern.Draw.exceptFirstIn]) applies as it does to a draw with cards left (inr sweep).
     */
    fun replaceEmptyDraw(act: Act, cause: Cause?, player: PlayerId, instead: (Replacement, TriggerEvent) -> Unit): Boolean {
        val matching = candidates(act, cause).filter { r ->
            val pattern = r.mechanic.event as? arcanum.api.EventPattern.Draw ?: return@filter false
            r.key !in act.replacing && r.mechanic.instead != null &&
                (pattern.exceptFirstIn?.let { step -> !arcanum.engine.trigger.EventMatcher.firstDrawIn(act.state, step, player) } ?: true) &&
                Evaluator(act.board(), Context(you = r.controller, self = r.source, names = r.names)).matches(pattern.players, player)
        }.map { it to TriggerEvent(players = listOf(player)) }
        if (matching.isEmpty()) return false
        val (r, instance) = pick(act, matching, player, null)
        if (r.mechanic.optional && !act.yesNo(r.controller, r.source, "Apply ${describe(r)}?")) return false
        useUp(act, r)
        instead(r, instance)
        return true
    }

    private fun pick(act: Act, matching: List<Pair<Replacement, TriggerEvent>>, affected: PlayerId, about: ObjectId?): Pair<Replacement, TriggerEvent> {
        matching.firstOrNull { it.first.own }?.let { return it }
        matching.firstOrNull { (r, _) -> r.mechanic.modify.any { it is Modification.EntersUnderControlOf } }?.let { return it }
        matching.firstOrNull { (r, _) -> r.mechanic.modify.any { it is Modification.EntersAsCopy } }?.let { return it }
        matching.firstOrNull { (r, _) -> r.mechanic.modify.any { it is Modification.ChangeDestination && it.to.transformed } }?.let { return it }
        // Only ask when the order can matter: modifications that just add to how something enters commute.
        if (matching.size < 2 || matching.all { (r, _) -> commutes(r) }) return matching.first()
        // CR 616.1e with prevention effects: the order can't matter when one prevents all of the damage and the others
        // only prevent or change the amount (none is left either way); among prevention effects only, those that aren't
        // used up go first, then shields oldest first, so no shield is used up for nothing (P5)
        val preventions = matching.filter { it.first.prevention != null }
        // dsk gaps: an additional effect depends on how much is left to prevent (CR 615.5), so its order matters
        if (preventions.isNotEmpty() && preventions.none { it.first.prevention!!.hasAdditional }) {
            val all = preventions.firstOrNull { it.first.prevention!!.all }
            if (all != null && matching.all { (r, _) -> r.prevention != null || onlyAmount(r) }) return all
            if (preventions.size == matching.size) return preventions.sortedBy { if (it.first.prevention is Prevention.Shield) 1 else 0 }.first()
        }
        val options = matching.mapIndexed { i, (r, _) -> ReplacementOption(i, r.source, describe(r)) }
        val chosen = (act.ask { Decision.ChooseReplacement(it, affected, about, options) } as Answer.Replacement).index
        return matching.getOrNull(chosen) ?: throw IllegalArgumentException("No replacement $chosen")
    }

    /** Only changes the amount: doubling, "that much plus one". */
    private fun onlyAmount(r: Replacement): Boolean = r.prevention == null && r.mechanic.instead == null && r.mechanic.whenApplied == null &&
        !r.mechanic.optional && r.mechanic.modify.isNotEmpty() && r.mechanic.modify.all { it is Modification.MultiplyAmount || it is Modification.AddAmount }

    private fun commutes(r: Replacement): Boolean = r.prevention == null && r.mechanic.instead == null && r.mechanic.modify.all {
        it is Modification.EntersTapped || it is Modification.EntersWithCounters || it is Modification.EntersWithAbilities ||
            it is Modification.AsEnters || it is Modification.EntersAs
    }

    /** What a player reads for [r]: its ability's printed text, else plain words (API review 3). */
    private fun describe(r: Replacement): String = r.text ?: if (r.prevention != null) "Prevent damage" else "A replacement effect"

    /** A shield with a count ("the next time …", regeneration) is used once more (D26). */
    private fun useUp(act: Act, r: Replacement) {
        val id = r.effect ?: return
        val effect = act.state.effects[id] ?: return
        val left = effect.usesLeft ?: return
        if (left <= 1) act.record(Change.EffectRemoved(effect, listOf(Meaning.ShieldUsed)))
        else act.record(Change.EffectUpdated(effect, effect.copy(usesLeft = left - 1)))
    }

    private fun linkedTrigger(r: Replacement, instructions: arcanum.api.Instructions, instance: TriggerEvent) = WaitingTrigger(
        controller = r.controller,
        ability = Ability.Triggered(arcanum.api.Trigger.On(emptyList()), instructions),
        origin = null, source = r.source, event = instance, names = r.names,
    )

    /** Puts [linked] triggers among those waiting to go on the Stack (CR 603.2, 603.11). */
    fun addLinked(act: Act, linked: List<WaitingTrigger>) {
        if (linked.isNotEmpty()) Triggers.addWaiting(act.recorder, linked)
    }
}

/**
 * A board with objects that don't exist yet (tokens about to be created), so replacement patterns can be tested on
 * them (CR 614.12): their characteristics are their printed ones.
 */
internal class ProposedBoard(private val base: Board, private val extra: Map<ObjectId, GameObject>) : Board {
    override val state get() = base.state
    override fun characteristics(id: ObjectId): Characteristics? =
        extra[id]?.let { arcanum.engine.query.Lki.printed(it) } ?: base.characteristics(id)

    override fun controller(id: ObjectId): PlayerId? = extra[id]?.let { it.controller ?: it.owner } ?: base.controller(id)

    override fun snapshot(id: ObjectId): ObjectSnapshot? = extra[id]?.let {
        ObjectSnapshot(it.id, it.owner, it.controller, it.zone, arcanum.engine.query.Lki.printed(it), it.counters, token = it.token)
    } ?: base.snapshot(id)

    override fun proposedZone(id: ObjectId): arcanum.api.Zone? = extra[id]?.zone ?: base.proposedZone(id)

    override fun createdBy(id: ObjectId): ObjectId? = extra[id]?.statuses?.filterIsInstance<arcanum.engine.state.ObjectStatus.CreatedBy>()?.firstOrNull()?.source
        ?: base.createdBy(id)
}
