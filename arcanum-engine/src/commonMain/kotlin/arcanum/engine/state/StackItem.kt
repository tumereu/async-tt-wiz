package arcanum.engine.state

import arcanum.api.Ability
import arcanum.api.EventRole
import arcanum.api.Zone
import arcanum.engine.view.AbilityOrigin
import arcanum.engine.view.Characteristics
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A spell or ability on the Stack with the choices made for it (D19, D35): what it runs, who controls it, its targets
 * by slot, modes, announced values and the names its costs bound. A spell's card is also an object in the Stack zone
 * ([GameState.objects]); an ability or a copy of a spell exists only as this item.
 */
@Serializable
data class StackItem(
    /** The spell's card object, or [ObjectId.StackItem] for an ability or a copy (D35). */
    val id: ObjectId,
    val kind: StackItemKind,
    /** Captured when it was put on the Stack ("you", D19); a control change of a spell updates it (CR 110.2b). */
    val controller: PlayerId,
    /** Where it came from: the card of a spell, the object of an ability. May be stale; [snapshots] holds its last
     * known information (CR 113.7a, 608.2h). */
    val source: ObjectId?,
    /** What it runs, copied when put on the Stack (D23): the spell ability of the cast face, the activated or
     * triggered ability; null for a permanent spell without a spell ability. */
    val ability: Ability?,
    /** Where [ability] came from (D42/D44): printed, granted, keyword expansion. */
    val origin: AbilityOrigin? = null,
    /** Chosen targets by slot name (CR 601.2c); a slot of an unchosen mode is absent (CR 700.2c). */
    val targets: Map<String, List<TargetRef>> = emptyMap(),
    /** How an amount is divided among a slot's targets (CR 601.2d). */
    val divided: List<DividedShare> = emptyList(),
    /** Chosen mode indices (CR 700.2), in order; repeats allowed for repeatable modes. */
    val modes: List<Int> = emptyList(),
    /** Announced values by name ("X", CR 107.3, 601.2b). */
    val announced: Map<String, Int> = emptyMap(),
    /** Names bound while casting or activating: cost names ("the sacrificed creature"), paid optional costs (D37). */
    val names: Bindings = Bindings.EMPTY,
    /** Last known information of objects it refers to that have changed zones (D21): its source, sacrificed objects. */
    val snapshots: List<ObjectSnapshot> = emptyList(),
    /** For a triggered ability: what it triggered on (CR 603.2), read by "it", "that player", "that much". */
    val event: TriggerEvent? = null,
    /** For a delayed triggered ability: the delayed trigger that created it. */
    val delayedTrigger: EffectId? = null,
    /** CR 613.7d: when it was put on the Stack. */
    val timestamp: Long,
)

/** What kind of Stack object a [StackItem] is (CR 112, 113.7, 707.10). */
@Serializable
sealed interface StackItemKind {
    /** A spell from a cast card (CR 112.1). */
    @Serializable @SerialName("spell") data object Spell : StackItemKind
    /** A copy of a spell (CR 707.10): not a card; ceases to exist when it leaves the Stack (CR 704.5e). */
    @Serializable @SerialName("spell-copy") data class SpellCopy(val copied: ObjectId) : StackItemKind
    /** An activated ability (CR 602). */
    @Serializable @SerialName("activated") data object Activated : StackItemKind
    /** A triggered ability (CR 603), delayed and reflexive ones included. */
    @Serializable @SerialName("triggered") data object Triggered : StackItemKind
}

/** [amount] of a divided amount assigned to [target] of slot [slot] (CR 601.2d). */
@Serializable
data class DividedShare(val slot: String, val target: TargetRef, val amount: Int)

/** The event a triggered ability triggered on (CR 603.2): its objects by role, players and amount ([arcanum.api.EventPattern]). */
@Serializable
data class TriggerEvent(
    val objects: List<RoleObjects> = emptyList(),
    val players: List<PlayerId> = emptyList(),
    val amount: Int? = null,
)

/** The objects of an event in one [role] ("it", "that creature"). */
@Serializable
data class RoleObjects(val role: EventRole, val ids: List<ObjectId>)

/**
 * Last known information of an object (CR 608.2h, 603.10): what it was just before it left its zone, as everyone
 * could see it (D33).
 */
@Serializable
data class ObjectSnapshot(
    val id: ObjectId,
    val owner: PlayerId,
    val controller: PlayerId?,
    val zone: Zone,
    /** Null when it was unknown to the caller (D60). */
    val characteristics: Characteristics?,
    val counters: List<CounterEntry> = emptyList(),
    val damage: Int = 0,
    val tapped: Boolean = false,
    val token: Boolean = false,
    val attachedTo: TargetRef? = null,
    val facts: ObjectFacts = ObjectFacts(),
)
