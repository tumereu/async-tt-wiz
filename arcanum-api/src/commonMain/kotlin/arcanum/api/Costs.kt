package arcanum.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A cost (CR 118): its parts are paid together, in any order the player chooses, except that random parts and moving
 * cards from a library to a public zone are paid last (CR 601.2h). It can't be paid in part. */
@Serializable
data class Cost(val parts: List<CostPart>) {
    companion object {
        fun mana(cost: ManaCost) = Cost(listOf(CostPart.Mana(cost)))
    }
}

/**
 * A change to what a spell or ability costs (CR 601.2f): [mana] more or less, [times] times ("costs {1} less to cast
 * for each artifact you control": `Less({1}, number of artifacts)`; "costs {W}{B} less": `Less({W}{B}, 1)`). The
 * engine applies the symbols by CR 118.7: generic reductions reduce only generic mana (118.7a), coloured or colourless
 * ones the cost doesn't need reduce generic mana (118.7b–d), hybrid halves are chosen as it applies (118.7e).
 */
@Serializable
sealed interface CostChange {
    val mana: ManaCost
    val times: Value

    /** An increase ("costs {1} more"). */
    @Serializable @SerialName("more") data class More(override val mana: ManaCost, override val times: Value) : CostChange
    /** A reduction ("costs {2} less", "{W}{B} less"); the cost's mana component can't go below {0} (CR 118.7). */
    @Serializable @SerialName("less") data class Less(override val mana: ManaCost, override val times: Value) : CostChange
}

/** One part of a cost. Parts that use objects name them for the effect ("the sacrificed creature's power"): [bind]
 * when they stay where they are (tap, reveal), `preBind`/`postBind` when they move (as in [Effect]). */
@Serializable
sealed interface CostPart {
    /** [cost], paid [times] times when given ("{1} for each creature"); [x] sets the value of {X} in it when the text
     * defines it ("pays {X}, where X is …"). */
    @Serializable @SerialName("mana") data class Mana(val cost: ManaCost, val times: Value? = null, val x: Value? = null) : CostPart
    /** "Pay its mana cost" / a cost "equal to its mana cost" (the object's own mana cost). */
    @Serializable @SerialName("its-mana-cost") data object ItsManaCost : CostPart
    /** {T}: tap this permanent (CR 107.5). */
    @Serializable @SerialName("tap") data object TapSelf : CostPart
    /** {Q}: untap this permanent (CR 107.6). */
    @Serializable @SerialName("untap") data object UntapSelf : CostPart
    /** Tap untapped objects you control: [what] chooses them (crew: any number with total power N or more, through
     * [CostObjects.Choose.total]; crew taps other creatures, so its filter excludes the source, CR 702.122a). */
    @Serializable @SerialName("tap-objects") data class TapObjects(val what: CostObjects, val bind: String? = null) : CostPart
    @Serializable @SerialName("sacrifice") data class Sacrifice(val what: CostObjects, val preBind: String? = null, val postBind: String? = null) : CostPart
    @Serializable @SerialName("discard") data class Discard(
        val what: CostObjects, val random: Boolean = false, val preBind: String? = null, val postBind: String? = null,
    ) : CostPart
    /** Exile [what] from [from]; [remember] keeps the exiled cards as card memory of the object, so the permanent a spell
     * becomes can still find them ("return the exiled card", CR 607.2q). */
    @Serializable @SerialName("exile") data class Exile(
        val what: CostObjects, val from: Zone, val preBind: String? = null, val postBind: String? = null, val remember: String? = null,
    ) : CostPart
    /** Pay one of [options] ("discard a card or pay {5}"). */
    @Serializable @SerialName("one-of") data class OneOf(val options: List<Cost>) : CostPart
    /** A keyword action as a cost: behold, collect evidence, forage, exert; [bind] names what it used ("the beheld card"). */
    @Serializable @SerialName("action") data class Action(val action: KeywordAction, val bind: String? = null) : CostPart
    @Serializable @SerialName("return-to-hand") data class ReturnToHand(val what: CostObjects, val preBind: String? = null, val postBind: String? = null) : CostPart
    @Serializable @SerialName("reveal") data class Reveal(val what: CostObjects, val bind: String? = null) : CostPart
    @Serializable @SerialName("pay-life") data class PayLife(val amount: Value) : CostPart
    @Serializable @SerialName("pay-energy") data class PayEnergy(val amount: Value) : CostPart
    /** Remove [amount] counters of [kind] from each of [from]'s objects, or ([distributed]) [amount] in total from among
     * them as the payer chooses ("Remove one or more +1/+1 counters from among creatures you control", gtc chunk 04). */
    @Serializable @SerialName("remove-counters") data class RemoveCounters(
        val kind: CounterKind?, val amount: Value, val from: CostObjects, val distributed: Boolean = false,
    ) : CostPart
    @Serializable @SerialName("put-counters") data class PutCounters(val kind: CounterKind, val amount: Value, val on: CostObjects) : CostPart
    /** A loyalty ability's cost: put or remove loyalty counters (CR 606); [amount] may be negative or announced (−X). */
    @Serializable @SerialName("loyalty") data class Loyalty(val amount: Value) : CostPart
    @Serializable @SerialName("mill") data class Mill(val count: Value) : CostPart
    /** Choose a player matching [filter] ("you may choose an opponent", gift, CR 702.174a); kept with the spell and the
     * permanent it becomes, read with [Players.ChosenAsCost]. */
    @Serializable @SerialName("choose-player") data class ChoosePlayer(val filter: PlayerFilter) : CostPart
    // msh
    /** The payer gets [amount] [kind] counters ("Ward—Get five poison counters", CR 122.1). */
    @Serializable @SerialName("get-counters") data class GetCounters(val kind: CounterKind, val amount: Value) : CostPart
    // tla
    /** "Waterbend [cost]" (CR 701.67a): pay [cost]; for each generic mana in it, the payer may tap an untapped artifact or
     * creature they control rather than pay that mana ([ManaPaymentWay.Waterbend]; 701.67b: only that generic mana). [x] sets its
     * {X} when the text defines it ("waterbending {X} …, where X is its mana value"). Paying it is waterbending (701.67c). */
    @Serializable @SerialName("waterbend") data class Waterbend(val cost: ManaCost, val x: Value? = null) : CostPart
    /** Text the definition can't express as a cost part; shown as a reminder. */
    @Serializable @SerialName("manual") data class Manual(val text: String) : CostPart
}

/** The objects a cost part uses: this object ("Sacrifice ~", "Discard this card") or [count] chosen ones matching
 * [filter] (from the zone the part implies). */
@Serializable
sealed interface CostObjects {
    /** The source as it is now (its current object). */
    @Serializable @SerialName("self") data object Self : CostObjects
    /** These objects ("you may exile it", the new card in a dies trigger; a bound object). */
    @Serializable @SerialName("these") data class These(val objects: Objects) : CostObjects
    /** [count] objects matching [filter] ([count] null: any number), or (with [total]) any number whose
     * [Total.property] adds up to at least [Total.atLeast] ("tap any number of creatures with total power 3 or more").
     * "Your hand" is [These], not a count of its size. */
    @Serializable @SerialName("choose") data class Choose(
        val filter: ObjectFilter, val count: Value?, val total: Total? = null,
        // rtr chunk 07
        /** The chosen objects must all share this quality ("Discard two nonland cards with the same name", CR 201.2). */
        val shared: SharedQuality? = null,
    ) : CostObjects
}

/** Mana an ability or effect adds (CR 106). [restriction] limits what it may be spent on ("spend this mana only to
 * cast creature spells", CR 106.6). [spellGets] applies to a spell this mana is spent on ("that spell can't be
 * countered", "it enters with an additional +1/+1 counter", CR 106.6a). */
@Serializable
data class ManaProduction(
    val kind: ManaKind, val restriction: ObjectFilter? = null, val spellGets: List<Mechanic> = emptyList(),
    /** The mana isn't lost as steps and phases end until this ends ("until end of turn, you don't lose this mana",
     * firebending). */
    val keptUntil: Duration? = null,
    /** "When that mana is spent to cast a spell, …" (Pyromancer's Goggles). */
    val whenSpent: Instructions? = null,
)

/** [property] of the chosen objects added up, at least [atLeast]. */
@Serializable
data class Total(val property: NumericProperty, val atLeast: Value)

@Serializable
sealed interface ManaKind {
    /** One mana of any type the triggering mana ability produced, [amount] of it ("adds one additional mana of any type
     * that land produced"). */
    @Serializable @SerialName("same-as-produced") data class SameAsProduced(val amount: Value) : ManaKind
    /** These symbols ("{G}{G}", "{C}"), [times] times when given ("Add {G} for each Elf you control"). */
    @Serializable @SerialName("fixed") data class Fixed(val mana: ManaCost, val times: Value? = null) : ManaKind
    /** [amount] mana of one colour among [colors] (two or more, "the chosen color" through [ColorRef.Chosen], "a color
     * among exiled cards" through [ColorRef.Of]; all five when null). [combination]: any combination of those colours
     * instead of one colour. */
    @Serializable @SerialName("choose") data class Choose(val amount: Value, val colors: List<ColorRef>? = null, val combination: Boolean = false) : ManaKind
    /** [amount] mana of any colour/type a land matching [filter] could produce (Reflecting Pool style). */
    @Serializable @SerialName("as-could") data class AsCouldProduce(val filter: ObjectFilter, val amount: Value) : ManaKind
}
