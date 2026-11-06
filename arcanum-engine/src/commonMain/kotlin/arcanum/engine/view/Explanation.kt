package arcanum.engine.view

import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Layer
import arcanum.engine.state.EffectId
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where an object's characteristics come from ([arcanum.engine.Engine.explain], spec §7.1 "Inspect"): its values
 * after layer 1 ([base]: printed, copied or face down, CR 613.2), then every continuous effect that changed them, in
 * the order the layer system applied them (CR 613.1–613.8). [characteristics] is the result, as
 * [arcanum.engine.Engine.view] gives it. [effects] lists the same steps by the effect that made them.
 */
@Serializable
data class Explanation(
    val id: ObjectId,
    val base: Characteristics,
    val steps: List<ExplainedEffect>,
    val characteristics: Characteristics,
    /** The continuous effects that changed the object, one entry each with its steps in every layer, in the order each
     * was first applied: what a host lists as "+3/+3 and trample until end of turn". */
    val effects: List<AppliedEffect> = emptyList(),
)

/**
 * One continuous effect that changed an explained object ([Explanation.effects]): its [source], the object that made
 * it ([madeBy]: the static ability's object, the source of the spell or ability that created an effect object, the
 * object with the counters; null when not known), how long it lasts ([duration]: an effect object's, CR 611.2; null for
 * a static ability or counters, which apply while they are there, CR 611.3a), its [timestamp] (CR 613.7) and its
 * [steps], one per layer it changed something in, in the order applied.
 */
@Serializable
data class AppliedEffect(
    val source: EffectSource,
    val madeBy: ObjectId?,
    val duration: Duration?,
    val timestamp: Long,
    val steps: List<ExplainedEffect>,
)

/**
 * One continuous effect applied to the explained object in [layer]: its characteristics [before] and [after] it (and
 * its controller, layer 2). A step is listed only when it changed something; [timestamp] is the effect's (CR 613.7).
 */
@Serializable
data class ExplainedEffect(
    val layer: Layer,
    val source: EffectSource,
    val timestamp: Long,
    val before: Characteristics,
    val after: Characteristics,
    val controllerBefore: PlayerId? = null,
    val controllerAfter: PlayerId? = null,
)

/** What made a continuous effect (CR 611.3, 611.2, 613.4c). */
@Serializable
sealed interface EffectSource {
    /** A static ability ([origin]) of [obj]; [characteristicDefining] for a CDA (CR 604.3). */
    @Serializable @SerialName("static")
    data class StaticAbility(val obj: ObjectId, val origin: AbilityOrigin, val characteristicDefining: Boolean = false) : EffectSource

    /** An effect object of a resolved spell or ability ([arcanum.engine.state.GameState.effects]; its duration there). */
    @Serializable @SerialName("effect")
    data class EffectObject(val effect: EffectId) : EffectSource

    /** [count] counters of [kind] on the object (P/T counters in 7c, keyword counters in layer 6). */
    @Serializable @SerialName("counters")
    data class Counters(val obj: ObjectId, val kind: CounterKind, val count: Int) : EffectSource
}
