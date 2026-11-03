package arcanum.engine.state

import arcanum.api.Affected
import arcanum.api.Duration
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Mechanic
import arcanum.api.Trigger
import kotlinx.serialization.Serializable

/**
 * A continuous effect created by a resolving spell or ability (CR 611.2, D19, D26): [mechanics] applied to
 * [affects] for [duration]. The host stores it and passes it back; it is visible and removable (P2).
 *
 * Mechanics that change characteristics or control lock their affected set when created (CR 611.2c, D39): those are
 * [locked]. Other mechanics keep applying to whatever matches [affects] ("creatures you control can't be blocked this
 * turn" covers creatures that arrive later). Numbers and choices are fixed when created: the resolution writes fixed
 * values into [mechanics] and freezes the names they use in [names] (CR 608.2h, D37).
 */
@Serializable
data class EffectObject(
    val id: EffectId,
    val mechanics: List<Mechanic>,
    /** Null for mechanics with their own scope (prevention, replacement, cost change: CR 611.2c). */
    val affects: Affected?,
    /** The objects the layer mechanics apply to, locked in when created (CR 611.2c); null: worked out from [affects]
     * each time (an effect that never had a layer mechanic). */
    val locked: List<ObjectId>? = null,
    /** The players player mechanics apply to, locked in when created (CR 611.2c). */
    val lockedPlayers: List<PlayerId>? = null,
    /** The object whose spell or ability created it (may be stale; it keeps applying, CR 611.2b). */
    val source: ObjectId?,
    /** "You" for its mechanics: the controller of the spell or ability that created it (D19). */
    val controller: PlayerId,
    /** CR 613.7b: when it was created. */
    val timestamp: Long,
    /** The turn it was created in ("until your next turn" and similar durations count from it). */
    val createdTurn: Int,
    val duration: Duration,
    /** A replacement shield's uses left ("the next time …", regeneration, D26); null: unlimited. */
    val usesLeft: Int? = null,
    /** A prevention shield's damage left ("prevent the next 3 damage", CR 615.7, D26); null: not a shield. */
    val amountLeft: Int? = null,
    /** Names fixed when it was created: targets, choices, bound objects (D37). */
    val names: Bindings = Bindings.EMPTY,
    /** For a copy effect ([Mechanic.CopyOf]): the copiable values fixed when it started (CR 707.2b, 707.2c). */
    val copied: FaceDefinition? = null,
)

/**
 * A delayed triggered ability (CR 603.7), created by a resolving spell or ability: it triggers the next time its
 * event happens ([repeats]: every time during [duration]), with the names it refers to fixed when created (CR 603.7c).
 */
@Serializable
data class DelayedTrigger(
    val id: EffectId,
    val trigger: Trigger,
    val instructions: Instructions,
    val duration: arcanum.api.Duration? = null,
    val repeats: Boolean = false,
    val source: ObjectId?,
    /** The player who controlled the creating spell or ability (CR 603.7d). */
    val controller: PlayerId,
    val timestamp: Long,
    val createdTurn: Int,
    val names: Bindings = Bindings.EMPTY,
    /** Snapshots of the objects it refers to (D21). */
    val snapshots: List<ObjectSnapshot> = emptyList(),
    /** Not a triggered ability: the second one-shot effect of an "until" (CR 610.3), done right after its event without
     * the Stack. */
    val oneShot: Boolean = false,
    /** eoe: triggers only in a later turn than [createdTurn] ("at the beginning of the end step on your next turn"). */
    val laterTurn: Boolean = false,
)
