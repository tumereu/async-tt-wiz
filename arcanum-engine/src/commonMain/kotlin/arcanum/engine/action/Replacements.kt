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
    data class Protection(val from: arcanum.api.ObjectFilter, val recipient: ObjectId) : Prevention

    /** All of the damage, or [each] of each such damage event. */
    data class Amount(val each: arcanum.api.Value?) : Prevention

    /** Up to the amount left on a shield (CR 615.7, D26), used up as it prevents. */
    data class Shield(val effect: EffectId) : Prevention

    /** Prevents all of the damage, whatever the amount. */
    val all: Boolean get() = this is Protection || (this is Amount && each == null)
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
        val rules = act.view().rules.mapIndexedNotNull { i, applied ->
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
                d.recipient == arcanum.engine.state.TargetRef.Object(protection.recipient) &&
                    e.with(Context(r.controller, protection.recipient)).matches(protection.from, d.source)
            }
            return if (hit) listOf(TriggerEvent()) else emptyList()
        }
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
            r.mechanic.whenApplied?.let { linked += linkedTrigger(r, it, instance) }
            if (r.mechanic.instead != null) {
                instead(r, instance)
                return null
            }
            current = modify(current, r, instance) ?: return null
        }
    }

    /** CR 616.1a–e: the next replacement to apply. */
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
        if (preventions.isNotEmpty()) {
            val all = preventions.firstOrNull { it.first.prevention!!.all }
            if (all != null && matching.all { (r, _) -> r.prevention != null || onlyAmount(r) }) return all
            if (preventions.size == matching.size) return preventions.sortedBy { if (it.first.prevention is Prevention.Shield) 1 else 0 }.first()
        }
        val options = matching.mapIndexed { i, (r, _) -> ReplacementOption(i, r.source, describe(r)) }
        val chosen = (act.asker.ask { Decision.ChooseReplacement(it, affected, about, options) } as Answer.Replacement).index
        return matching.getOrNull(chosen) ?: throw IllegalArgumentException("No replacement $chosen")
    }

    /** Only changes the amount: doubling, "that much plus one". */
    private fun onlyAmount(r: Replacement): Boolean = r.prevention == null && r.mechanic.instead == null && r.mechanic.whenApplied == null &&
        !r.mechanic.optional && r.mechanic.modify.isNotEmpty() && r.mechanic.modify.all { it is Modification.MultiplyAmount || it is Modification.AddAmount }

    private fun commutes(r: Replacement): Boolean = r.prevention == null && r.mechanic.instead == null && r.mechanic.modify.all {
        it is Modification.EntersTapped || it is Modification.EntersWithCounters || it is Modification.EntersWithAbilities ||
            it is Modification.AsEnters
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
}
