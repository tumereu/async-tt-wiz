package arcanum.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A number worked out when used (CR 107): fixed, announced (X), counted, or read from the game. */
@Serializable
sealed interface Value {
    @Serializable @SerialName("fixed") data class Fixed(val value: Int) : Value
    /** A value announced when cast or activated ([Instructions.announced]), usually "X". */
    @Serializable @SerialName("announced") data class Announced(val name: String = "X") : Value
    /** A number chosen or bound by an earlier step (the amount a damage or life effect actually dealt, gained or lost,
     * the number chosen), or remembered on the source. */
    @Serializable @SerialName("bound") data class Bound(val name: String) : Value
    @Serializable @SerialName("remembered") data class Remembered(val key: String) : Value
    /** The amount the triggering or replaced event was about ("that much damage", "that much life"). */
    @Serializable @SerialName("event-amount") data object EventAmount : Value

    @Serializable @SerialName("count") data class Count(val objects: Objects) : Value
    @Serializable @SerialName("count-players") data class CountPlayers(val players: Players) : Value
    /** How many distinct values of [quality] [objects] have ("the number of colours among permanents you control"). */
    @Serializable @SerialName("count-distinct") data class CountDistinct(val objects: Objects, val quality: SharedQuality) : Value
    /** A number read from [objects]: [property] of one object ("its power"), or of a group combined as [how] says
     * ("the greatest power among creatures you control", "their total power"). An empty group gives 0. */
    @Serializable @SerialName("of") data class Of(val property: NumericProperty, val objects: Objects, val how: Combine) : Value
    @Serializable @SerialName("player-counters") data class PlayerCounters(val players: Players, val kind: CounterKind) : Value
    @Serializable @SerialName("life") data class LifeOf(val players: Players) : Value
    @Serializable @SerialName("hand-size") data class HandSize(val players: Players) : Value
    /** Devotion to [colors] (CR 700.5). */
    @Serializable @SerialName("devotion") data class Devotion(val colors: List<Color>, val players: Players = Players.You) : Value
    /** How many times [event] happened this turn ("the number of creatures that died this turn"). */
    @Serializable @SerialName("events-this-turn") data class EventsThisTurn(val event: EventPattern, val measure: Measure = Measure.Objects) : Value
    /** Mana spent to cast [of] (this object by default; "that spell" in a cast trigger): all of it, only mana of [color]
     * ("if at least three white mana was spent"), or the number of colours ([distinctColors]). A permanent keeps this
     * from the spell it was (CR 400.7d). */
    @Serializable @SerialName("mana-spent") data class ManaSpent(
        val color: Color? = null, val distinctColors: Boolean = false, val of: Objects = Objects.Self,
    ) : Value
    /** How many times [keyword]'s optional cost was paid for [of] ("for each time it was kicked", CR 702.33d–f). */
    @Serializable @SerialName("times-paid") data class TimesPaid(
        @Serializable(with = KeywordKindSerializer::class) val keyword: KeywordKind, val of: Objects = Objects.Self,
    ) : Value
    /** How many times this ability has been activated ([resolved] false) or has resolved this turn ("If this ability has
     * been activated four or more times this turn"). */
    @Serializable @SerialName("ability-uses") data class AbilityUsesThisTurn(val resolved: Boolean = false) : Value
    /** The damage over lethal in a damage effect bound as [name] ("excess damage", CR 120.4a); [Bound] reads the whole
     * amount dealt. */
    @Serializable @SerialName("excess") data class Excess(val name: String) : Value
    /** In an effect on the targets of a slot with [TargetSlot.divided]: each target's share, chosen when the spell
     * or ability was put on the Stack ("3 damage divided as you choose", CR 601.2d). */
    @Serializable @SerialName("divided-share") data object DividedShare : Value

    @Serializable @SerialName("sum") data class Sum(val parts: List<Value>) : Value
    @Serializable @SerialName("difference") data class Difference(val from: Value, val minus: Value) : Value
    @Serializable @SerialName("times") data class Times(val value: Value, val factor: Int) : Value
    @Serializable @SerialName("half") data class Half(val value: Value, val roundUp: Boolean) : Value
    @Serializable @SerialName("max") data class Max(val parts: List<Value>) : Value
    @Serializable @SerialName("min") data class Min(val parts: List<Value>) : Value
}

/**
 * What a "this turn" count counts ([Value.EventsThisTurn], [Condition.HappenedThisTurn]): events (a board wipe is
 * one), objects (it is five creatures that died), or the amounts the events carried (life gained, damage dealt).
 * Each tagged event (D33) is one event: one draw, one zone change of several objects at once, one damage event.
 */
@Serializable
sealed interface Measure {
    @Serializable @SerialName("events") data object Events : Measure
    @Serializable @SerialName("objects") data object Objects : Measure
    @Serializable @SerialName("amount") data object Amount : Measure
}

/** A number an object has (part of its properties). */
@Serializable
sealed interface NumericProperty {
    @Serializable @SerialName("power") data object Power : NumericProperty
    @Serializable @SerialName("toughness") data object Toughness : NumericProperty
    @Serializable @SerialName("mana-value") data object ManaValue : NumericProperty
    @Serializable @SerialName("loyalty") data object Loyalty : NumericProperty
    /** Counters of [kind], or of every kind ("the number of counters on it") when null. */
    @Serializable @SerialName("counters") data class Counters(val kind: CounterKind?) : NumericProperty
}

/** How [Value.Of] turns the objects' numbers into one. */
@Serializable
sealed interface Combine {
    /** Exactly one object ("its power"); a reference that finds several is a definition error. */
    @Serializable @SerialName("single") data object Single : Combine
    @Serializable @SerialName("total") data object Total : Combine
    @Serializable @SerialName("greatest") data object Greatest : Combine
    @Serializable @SerialName("least") data object Least : Combine
}

/** A yes/no test worked out when used. */
@Serializable
sealed interface Condition {
    @Serializable @SerialName("and") data class And(val all: List<Condition>) : Condition
    @Serializable @SerialName("or") data class Or(val any: List<Condition>) : Condition
    @Serializable @SerialName("not") data class Not(val condition: Condition) : Condition
    @Serializable @SerialName("compare") data class Compare(val left: Value, val comparison: Comparison, val right: Value) : Condition
    /** At least [atLeast] objects match ("if you control an artifact"). */
    @Serializable @SerialName("exists") data class Exists(val filter: ObjectFilter, val atLeast: Int = 1) : Condition
    @Serializable @SerialName("player-is") data class PlayerMatches(val players: Players, val filter: PlayerFilter) : Condition
    @Serializable @SerialName("object-is") data class ObjectMatches(val objects: Objects, val filter: ObjectFilter) : Condition
    @Serializable @SerialName("your-turn") data object YourTurn : Condition
    @Serializable @SerialName("step") data class StepIs(val step: TurnStep) : Condition
    /** "If you do": the [Step.Pay] named [name] was paid: the player chose to pay it, or began a mandatory payment,
     * whatever events then happened (CR 118.12). Also "if they accepted" for a [Step.May] named [name]. "… this way"
     * checks the effect's own binding instead ("if a creature died this way": at least one object bound). */
    @Serializable @SerialName("did") data class Did(val name: String) : Condition
    /** "If you can't": the mandatory [Step.Pay] named [name] couldn't be paid (CR 118.12). */
    @Serializable @SerialName("could-not") data class CouldNot(val name: String) : Condition
    /** The coin flip named [name] was won. */
    @Serializable @SerialName("won-flip") data class WonFlip(val name: String) : Condition
    /** An optional or additional cost was paid when casting [of] ("if it was kicked", "if that spell was kicked"). */
    @Serializable @SerialName("paid") data class Paid(
        @Serializable(with = KeywordKindSerializer::class) val keyword: KeywordKind? = null,
        val times: Int = 1,
        /** Which of the keyword's costs ("kicked with its {1}{G} kicker", CR 702.33f): index into [Keyword.Kicker.costs]. */
        val costIndex: Int? = null,
        val of: Objects = Objects.Self,
    ) : Condition
    /** [of] was cast from [zone], or cast at all (null): "if you cast it from your hand", "if you cast it from a
     * graveyard" about another creature. */
    @Serializable @SerialName("cast-from") data class CastFrom(val zone: Zone?, val of: Objects = Objects.Self) : Condition
    /** [event] happened this turn, at least [atLeast] times by [measure] ("if a creature died this turn", "if you gained
     * 3 or more life this turn"). */
    @Serializable @SerialName("this-turn") data class HappenedThisTurn(
        val event: EventPattern, val atLeast: Int = 1, val measure: Measure = Measure.Objects,
    ) : Condition
    /** The objects named [name] by an effect are not empty ("if a creature died this way"). */
    @Serializable @SerialName("non-empty") data class NonEmpty(val name: String) : Condition
    /** Your speed is 4, the most it can be (max speed, CR 702.178). */
    @Serializable @SerialName("max-speed") data object MaxSpeed : Condition
    /** The chosen mode / option [option] of the resolving spell or of the choice bound under [name]. */
    @Serializable @SerialName("chose") data class Chose(val name: String, val option: String) : Condition
    /** The source Class is at [level] or higher (CR 716). Levelers compare their [CounterKind.Level] counters. */
    @Serializable @SerialName("class-level") data class ClassLevelAtLeast(val level: Int) : Condition
    @Serializable @SerialName("day") data object IsDay : Condition
    @Serializable @SerialName("night") data object IsNight : Condition
}

/** How long a continuous effect created by a resolving spell or ability lasts (CR 611.2). */
@Serializable
sealed interface Duration {
    @Serializable @SerialName("end-of-turn") data object EndOfTurn : Duration
    @Serializable @SerialName("end-of-combat") data object EndOfCombat : Duration
    /** "until your next turn": ends as your next turn begins. Not written as [UntilStep]. */
    @Serializable @SerialName("your-next-turn") data object UntilYourNextTurn : Duration
    /** Through the next [step] of [whose] turn, ending when that step ends ("doesn't untap during its controller's next
     * untap step"). */
    @Serializable @SerialName("through-next-step") data class ThroughNextStep(val step: TurnStep, val whose: Players) : Duration
    /** "until the end of your next turn" */
    @Serializable @SerialName("end-of-your-next-turn") data object UntilEndOfYourNextTurn : Duration
    /** "until your next upkeep", "until the beginning of the next end step" */
    @Serializable @SerialName("until-step") data class UntilStep(val step: TurnStep, val whose: PlayerFilter = PlayerFilter.Any) : Duration
    /** "for as long as …" ("for as long as you control ~", "for as long as ~ remains tapped"). */
    @Serializable @SerialName("as-long-as") data class AsLongAs(val condition: Condition) : Duration
    /** "until ~ leaves the battlefield" (CR 610.3). */
    @Serializable @SerialName("until-leaves") data class UntilLeaves(val objects: Objects = Objects.Self) : Duration
    /** No end ("gains control of target creature"). */
    @Serializable @SerialName("indefinitely") data object Indefinitely : Duration
}
