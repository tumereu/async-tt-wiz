package arcanum.engine.mutate

import arcanum.api.CardType
import arcanum.api.Keyword
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.action.Act
import arcanum.engine.action.Targeting
import arcanum.engine.change.Change
import arcanum.engine.change.GameEvent
import arcanum.engine.change.Meaning
import arcanum.engine.change.Placed
import arcanum.engine.state.GameObject
import arcanum.engine.state.MergedPart
import arcanum.engine.state.StackItem
import arcanum.engine.state.TargetRef

/**
 * Mutate (CR 702.140) and merging (CR 730): a spell cast for its mutate cost is a mutating creature spell that targets a non-Human
 * creature with the same owner ([slot]); as it resolves with that target legal it merges with the creature, on top or under as its
 * controller chooses, instead of entering ([resolve]). The merged permanent is the same object (CR 730.2c) and keeps its other
 * components in [GameObject.mergedAbove] / [GameObject.mergedBelow]: its characteristics are the topmost component's, its abilities
 * every component's (layer 1, [arcanum.engine.view.Copiable]); as it leaves the battlefield every component goes to the new zone
 * ([arcanum.engine.change.Move.parts]).
 */
internal object Mutate {
    const val SLOT: String = "mutate"
    const val ON_TOP: String = "on top"
    const val UNDER: String = "under"

    /** CR 702.140a: "targets a non-Human creature with the same owner as this spell". */
    val slot: TargetSlot = TargetSlot(SLOT, TargetKind.Objects(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Creature),
        ObjectFilter.Not(ObjectFilter.Sub(SubtypeRef.Literal(Subtype("Human")))),
        ObjectFilter.OwnedBy(PlayerFilter.Is(Players.OwnerOf(Objects.Self)))))), min = Value.Fixed(1), max = Value.Fixed(1))

    /** A mutating creature spell (CR 702.140a): cast for its mutate cost, on the Stack. */
    fun mutating(o: GameObject): Boolean = o.zone == Zone.Stack && o.facts.alternative?.keyword == Keyword.Mutate.Kind

    /**
     * CR 702.140b, c: as the mutating creature spell [card] resolves, if its target is legal it merges with it (its controller choosing
     * on top or under) rather than entering; returns false when the target is illegal and it resolves as a creature spell.
     */
    fun resolve(act: Act, item: StackItem, card: GameObject): Boolean {
        val ref = item.targets[SLOT]?.singleOrNull() as? TargetRef.Object ?: return false
        if (!Targeting.legal(act, slot, ref, item.controller, item.id, item.names, item)) return false
        val target = act.state.obj(ref.id)?.takeIf { it.zone == Zone.Battlefield } ?: return false
        val onTop = act.chooseOption(item.controller, item.id, listOf(ON_TOP, UNDER)) == ON_TOP
        // CR 730.2e: merged on top of a face-down permanent, it is face up (not turned face up): its own card is seen, as its
        // abilities now count
        if (onTop && target.faceDown) act.show(target.owner, listOf(target.id), public = true)
        val now = act.state.objOrThrow(target.id)
        val spell = act.state.objOrThrow(card.id)
        // CR 730.2a: the merge is a copy effect timestamped as the objects merge
        val part = MergedPart(spell.id, spell.owner, spell.definition, spell.face, spell.token, spell.copyOfCard, act.state.nextTimestamp())
        // CR 730.2b: it leaves the Stack and becomes part of the permanent, which hasn't entered
        act.record(Change.Removed(listOf(Placed(spell, act.state.stack.indexOf(spell.id))), listOf(Meaning.Merged)))
        act.record(Change.ObjectUpdated(now, if (onTop) now.copy(mergedAbove = listOf(part) + now.mergedAbove, faceDown = false)
            else now.copy(mergedBelow = now.mergedBelow + part)))
        act.record(Change.Happened(GameEvent.Mutated(now.id, spell.id, onTop)))
        return true
    }
}
