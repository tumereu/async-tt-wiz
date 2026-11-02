package arcanum.engine.view

import arcanum.api.CounterKind
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
 * [arcanum.engine.Engine.view] gives it.
 */
@Serializable
data class Explanation(
    val id: ObjectId,
    val base: Characteristics,
    val steps: List<ExplainedEffect>,
    val characteristics: Characteristics,
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
