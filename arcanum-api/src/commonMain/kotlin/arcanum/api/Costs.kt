package arcanum.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A cost (CR 118): its parts are paid together, in any order the player chooses (CR 601.2h). */
@Serializable
data class Cost(val parts: List<CostPart>) {
    companion object {
        fun mana(cost: ManaCost) = Cost(listOf(CostPart.Mana(cost)))
    }
}

/** One part of a cost. Parts that use objects name them for the effect ("the sacrificed creature's power"): [bind]
 * when they stay where they are (tap, reveal), `preBind`/`postBind` when they move (as in [Effect]). */
@Serializable
sealed interface CostPart {
    @Serializable @SerialName("mana") data class Mana(val cost: ManaCost) : CostPart
    /** {T}: tap this permanent (CR 107.5). */
    @Serializable @SerialName("tap") data object TapSelf : CostPart
    /** {Q}: untap this permanent (CR 107.6). */
    @Serializable @SerialName("untap") data object UntapSelf : CostPart
    /** Tap [count] untapped objects matching [filter] you control, or any number with total power [totalPower] or more
     * (crew). */
    @Serializable @SerialName("tap-objects") data class TapObjects(
        val filter: ObjectFilter, val count: Value? = null, val totalPower: Value? = null, val bind: String? = null,
    ) : CostPart
    @Serializable @SerialName("sacrifice") data class Sacrifice(val what: CostObjects, val preBind: String? = null, val postBind: String? = null) : CostPart
    @Serializable @SerialName("discard") data class Discard(
        val what: CostObjects, val random: Boolean = false, val preBind: String? = null, val postBind: String? = null,
    ) : CostPart
    @Serializable @SerialName("exile") data class Exile(val what: CostObjects, val from: Zone, val preBind: String? = null, val postBind: String? = null) : CostPart
    @Serializable @SerialName("return-to-hand") data class ReturnToHand(val what: CostObjects, val preBind: String? = null, val postBind: String? = null) : CostPart
    @Serializable @SerialName("reveal") data class Reveal(val what: CostObjects, val bind: String? = null) : CostPart
    @Serializable @SerialName("pay-life") data class PayLife(val amount: Value) : CostPart
    @Serializable @SerialName("pay-energy") data class PayEnergy(val amount: Value) : CostPart
    @Serializable @SerialName("remove-counters") data class RemoveCounters(val kind: CounterKind?, val amount: Value, val from: CostObjects = CostObjects.Self) : CostPart
    @Serializable @SerialName("put-counters") data class PutCounters(val kind: CounterKind, val amount: Value, val on: CostObjects = CostObjects.Self) : CostPart
    /** A loyalty ability's cost: put or remove loyalty counters (CR 606); [amount] may be negative or announced (−X). */
    @Serializable @SerialName("loyalty") data class Loyalty(val amount: Value) : CostPart
    @Serializable @SerialName("mill") data class Mill(val count: Value) : CostPart
    /** Text the definition can't express as a cost part; shown as a reminder. */
    @Serializable @SerialName("manual") data class Manual(val text: String) : CostPart
}

/** The objects a cost part uses: this object ("Sacrifice ~", "Discard this card") or [count] chosen ones matching
 * [filter] (from the zone the part implies). */
@Serializable
sealed interface CostObjects {
    @Serializable @SerialName("self") data object Self : CostObjects
    @Serializable @SerialName("choose") data class Choose(val filter: ObjectFilter, val count: Value = Value.Fixed(1)) : CostObjects
}

/** Mana an ability or effect adds (CR 106). [restriction] limits what it may be spent on ("spend this mana only to
 * cast creature spells", CR 106.6). */
@Serializable
data class ManaProduction(val kind: ManaKind, val restriction: ObjectFilter? = null)

@Serializable
sealed interface ManaKind {
    /** Exactly these symbols ("{G}{G}", "{C}"). */
    @Serializable @SerialName("fixed") data class Fixed(val mana: ManaCost) : ManaKind
    /** [amount] mana of one colour among [colors] (all five when null): "Add one mana of any color". [combination]:
     * any combination of those colours instead of one colour. */
    @Serializable @SerialName("choose") data class Choose(val amount: Value, val colors: List<Color>? = null, val combination: Boolean = false) : ManaKind
    /** [amount] mana of any colour/type a land matching [filter] could produce (Reflecting Pool style). */
    @Serializable @SerialName("as-could") data class AsCouldProduce(val filter: ObjectFilter, val amount: Value = Value.Fixed(1)) : ManaKind
    /** [amount] colourless mana. */
    @Serializable @SerialName("colorless") data class Colorless(val amount: Value) : ManaKind
}
