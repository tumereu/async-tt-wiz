package arcanum.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A number worked out when used (CR 107): fixed, announced (X), counted, or read from the game. */
@Serializable
sealed interface Value {
    @Serializable @SerialName("fixed") data class Fixed(val value: Int) : Value
    /** A value announced when cast or activated ([Instructions.announced]), usually "X". */
    @Serializable @SerialName("announced") data class Announced(val name: String) : Value
    /** A number named by an earlier step of this resolution (the amount a damage or life effect actually dealt, gained
     * or lost, the number chosen). Card memory is [Remembered]. */
    @Serializable @SerialName("bound") data class Bound(val name: String) : Value
    @Serializable @SerialName("remembered") data class Remembered(val key: String) : Value
    /** The value of X chosen when [of] was cast, for any of its costs (mana, kicker, flashback, …): read by the
     * permanent's enters triggers and replacements and by the spell's own static abilities (CR 107.3a, 107.3m). Inside
     * the spell's instructions, X is [Announced]. */
    @Serializable @SerialName("cast-x") data class CastX(val of: Objects) : Value
    /** Spells cast this turn before [of] (storm, CR 702.40a); spells cast in response to the storm trigger don't count. */
    @Serializable @SerialName("spells-cast-before") data class SpellsCastBefore(val of: Objects) : Value
    /** How many modes were chosen for the spell or ability (escalate counts those beyond the first, CR 702.120a). */
    @Serializable @SerialName("modes-chosen") data object ModesChosen : Value
    /** The amount the triggering or replaced event was about ("that much damage", "that much life"); in a batch
     * trigger, the batch's total. */
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
    @Serializable @SerialName("devotion") data class Devotion(val colors: List<Color>, val players: Players) : Value
    /** How many times [event] happened this turn ("the number of creatures that died this turn"). */
    @Serializable @SerialName("events-this-turn") data class EventsThisTurn(val event: EventPattern, val measure: Measure) : Value
    /** [players]'s starting life total (CR 103.4). */
    @Serializable @SerialName("starting-life") data class StartingLife(val players: Players) : Value
    /** Mana spent to cast [of] (this object, or "that spell" in a cast trigger): all of it, only mana of [color]
     * ("if at least three white mana was spent"), or the number of colours ([distinctColors]). A permanent keeps this
     * from the spell it was (CR 400.7d). */
    @Serializable @SerialName("mana-spent") data class ManaSpent(
        val color: Color? = null, val distinctColors: Boolean = false, val of: Objects,
    ) : Value
    /** How many times [keyword]'s optional cost was paid for [of] ("for each time it was kicked", CR 702.33d–f). */
    @Serializable @SerialName("times-paid") data class TimesPaid(
        @Serializable(with = KeywordKindSerializer::class) val keyword: KeywordKind, val of: Objects,
    ) : Value
    /** How many times this ability has been activated ([resolved] false) or has resolved this turn ("If this ability has
     * been activated four or more times this turn"). */
    /** Counted including the current activation or resolution ("if this is the second time this ability has resolved
     * this turn": `AbilityUsesThisTurn(resolved = true)` equals 2). */
    @Serializable @SerialName("ability-uses") data class AbilityUsesThisTurn(val resolved: Boolean = false) : Value
    /** The damage over lethal in a damage effect bound as [name] ("excess damage", CR 120.4a); [Bound] reads the whole
     * amount dealt. */
    @Serializable @SerialName("excess") data class Excess(val name: String) : Value
    /** In an effect on the targets of a slot with [TargetSlot.divided]: each target's share, chosen when the spell
     * or ability was put on the Stack ("3 damage divided as you choose", CR 601.2d). */
    @Serializable @SerialName("divided-share") data object DividedShare : Value

    // gtc chunk 05
    /** The greatest of [value] for each of [players], read with [Players.Tested] as that player ("the greatest number of cards
     * a player discarded this way"); 0 when there are none. */
    @Serializable @SerialName("greatest-for") data class GreatestFor(val players: Players, val value: Value) : Value

    @Serializable @SerialName("sum") data class Sum(val parts: List<Value>) : Value
    @Serializable @SerialName("difference") data class Difference(val from: Value, val minus: Value) : Value
    @Serializable @SerialName("times") data class Times(val value: Value, val factor: Int) : Value
    @Serializable @SerialName("half") data class Half(val value: Value, val roundUp: Boolean) : Value
    @Serializable @SerialName("max") data class Max(val parts: List<Value>) : Value
    // rtr chunk 09
    /** How many of [colors] [objects] has, each colour counted once ("1 life for each of the chosen colors it is", CR 105.2);
     * a [ColorRef] that stands for several colours (a [Choice.Colors]) counts each of them. */
    @Serializable @SerialName("shared-colors") data class SharedColors(val objects: Objects, val colors: List<ColorRef>) : Value
    @Serializable @SerialName("min") data class Min(val parts: List<Value>) : Value
    // fra
    /** How many whole times [divisor] fits in [value] ("for every seven cards in your graveyard": the count divided by
     * seven, rounded down). */
    @Serializable @SerialName("quotient") data class Quotient(val value: Value, val divisor: Int) : Value
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
    /** How many of its halves are unlocked (CR 709.5c): 0–2 for a Room, 0 for other objects ("the number of unlocked
     * doors among Rooms you control" is their total). */
    @Serializable @SerialName("unlocked-doors") data object UnlockedDoors : NumericProperty
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
    /** At least [atLeast] objects match ("if you control three or more artifacts"); not written as a count compared
     * with a fixed number. */
    @Serializable @SerialName("exists") data class Exists(val filter: ObjectFilter, val atLeast: Int) : Condition
    /** Every one of [players] matches [filter]. */
    @Serializable @SerialName("player-is") data class PlayerMatches(val players: Players, val filter: PlayerFilter) : Condition
    /** At least [atLeast] players match [filter] ("if an opponent controls four or more lands": players who are an
     * opponent and [PlayerFilter.Where] that player controls four or more lands; every player = none fails). */
    @Serializable @SerialName("player-exists") data class PlayerExists(val filter: PlayerFilter, val atLeast: Int) : Condition
    /** Every one of [objects] matches [filter]; false when there are none ("if an artifact was destroyed this way"). */
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
    /** An optional, additional or alternative cost of [keyword] was paid when casting [of] ("if it was kicked", "if
     * its evoke cost was paid", "if it escaped", "if that spell was kicked"; CR 607.2i). [keyword] null: the spell's own
     * additional cost written without a keyword ("if this spell's additional cost was paid"). */
    @Serializable @SerialName("paid") data class Paid(
        @Serializable(with = KeywordKindSerializer::class) val keyword: KeywordKind? = null,
        val times: Int,
        /** Which of the keyword's costs ("kicked with its {1}{G} kicker", CR 702.33f): index into [Keyword.Kicker.costs]. */
        val costIndex: Int? = null,
        val of: Objects,
    ) : Condition
    /** [of] was cast from [zone], or cast at all (null): "if you cast it from your hand", "if you cast it from a
     * graveyard" about another creature. */
    @Serializable @SerialName("cast-from") data class CastFrom(val zone: Zone?, val of: Objects) : Condition
    /** [event] happened this turn, at least [atLeast] times by [measure] ("if a creature died this turn", "if you gained
     * 3 or more life this turn"). */
    @Serializable @SerialName("this-turn") data class HappenedThisTurn(
        val event: EventPattern, val atLeast: Int, val measure: Measure,
    ) : Condition
    // fra
    /** [event] happened during the previous turn, at least [atLeast] times by [measure] ("if an opponent was dealt noncombat
     * damage last turn"). */
    @Serializable @SerialName("last-turn") data class HappenedLastTurn(
        val event: EventPattern, val atLeast: Int, val measure: Measure,
    ) : Condition
    /** The objects named [name] by an effect are not empty ("if a creature died this way"). */
    @Serializable @SerialName("non-empty") data class NonEmpty(val name: String) : Condition
    /** Your speed is 4, the most it can be (max speed, CR 702.178). */
    @Serializable @SerialName("max-speed") data object MaxSpeed : Condition
    /** This card is the companion its owner revealed before the game (CR 702.139a). */
    @Serializable @SerialName("revealed-companion") data object RevealedCompanion : Condition
    /** The option [option] was chosen in the [Step.Choose] named [name]. */
    @Serializable @SerialName("chose") data class Chose(val name: String, val option: String) : Condition
    /** The mode at [index] (from 0) of the resolving spell or ability was chosen. */
    @Serializable @SerialName("mode-chosen") data class ModeChosen(val index: Int) : Condition
    /** The targets in [slots] are all still legal ("If both targets are still legal", CR 608.2b). */
    @Serializable @SerialName("targets-legal") data class TargetsLegal(val slots: List<String>) : Condition
    /** The [Effect.Search] named [name] searched [zone] ("If you search your library this way, shuffle"). */
    @Serializable @SerialName("searched") data class Searched(val name: String, val zone: Zone) : Condition
    /** The source Class is at [level] or higher (CR 716). Levelers compare their [CounterKind.Level] counters. */
    @Serializable @SerialName("class-level") data class ClassLevelAtLeast(val level: Int) : Condition
    /** [value] is a prime number ("you control a prime number of lands"). */
    @Serializable @SerialName("prime") data class Prime(val value: Value) : Condition
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
    @Serializable @SerialName("until-step") data class UntilStep(val step: TurnStep, val whose: PlayerFilter) : Duration
    /** "for as long as …" ("for as long as you control ~", "for as long as ~ remains tapped"). */
    @Serializable @SerialName("as-long-as") data class AsLongAs(val condition: Condition) : Duration
    /** "until ~ leaves the battlefield" (CR 610.3). */
    @Serializable @SerialName("until-leaves") data class UntilLeaves(val objects: Objects) : Duration
    /** No end ("gains control of target creature"). */
    @Serializable @SerialName("indefinitely") data object Indefinitely : Duration
}
