package arcanum.engine.view

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.KeywordKind
import arcanum.api.ManaCost
import arcanum.api.ManaSymbol
import arcanum.api.Mechanic
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.Zone
import arcanum.engine.state.Bindings
import arcanum.engine.state.EffectId
import arcanum.engine.state.GameObject
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The derived state of a game ([arcanum.engine.Engine.view], P3): every object's characteristics after continuous
 * effects (CR 613), the rules applied to each object and player, and the game rules in force (CR 613.10, 613.11).
 */
@Serializable
data class View(
    /** Cards, tokens and copies of spells, in any zone; abilities on the Stack have no characteristics. */
    val objects: Map<ObjectId, ObjectView>,
    val players: Map<PlayerId, PlayerView>,
    /** The game as a whole: its statuses and the rules in force. */
    val game: GameView,
) {
    operator fun get(id: ObjectId): ObjectView? = objects[id]
}

/** One object as the view sees it. */
@Serializable
data class ObjectView(
    val id: ObjectId,
    val owner: PlayerId,
    /** After control-changing effects (layer 2); null outside the battlefield and the Stack (CR 108.4). */
    val controller: PlayerId?,
    val zone: Zone,
    /** Null when it has none (an object whose characteristics couldn't be worked out). */
    val characteristics: Characteristics?,
    /** Rules applied to it that are not characteristics (D24): from its own abilities (keyword meanings: deathtouch,
     * "can't be blocked except by …") and from other sources ("can't block" from an Aura, CR 113.12). */
    val mechanics: List<AppliedMechanic>,
    /** CR 302.6: its controller hasn't controlled it continuously since their most recent turn began. */
    val summoningSick: Boolean,
    /** The object as stored, before continuous effects. */
    val obj: GameObject?,
    /** Its characteristics after layer 1 only (printed, copied or face down, CR 613.2), before every other continuous
     * effect: what [characteristics] is compared with to show what effects changed. */
    val base: Characteristics? = null,
    /** Its copiable values as a whole (CR 707.2): layout and all faces, printed or copied, before its Room's doors lock
     * anything (CR 709.5). */
    val copiable: arcanum.engine.state.CopiableValues? = null,
    /** lci: its base power (CR 208.4b): its power after layers 7a and 7b, before effects and counters that modify it; null for a
     * noncreature. */
    val basePower: Int? = null,
    /** Its statuses ([GameObject.statuses]) with those the view derives: a Class on the battlefield that has gained no level
     * shows level 1 ([arcanum.engine.state.ObjectStatus.ClassLevel], CR 716.2a). */
    val statuses: List<arcanum.engine.state.ObjectStatus> = emptyList(),
) {
    val name: String? get() = characteristics?.name
    val power: Int? get() = characteristics?.power
    val toughness: Int? get() = characteristics?.toughness

    fun has(mechanic: (Mechanic) -> Boolean): Boolean = mechanics.any { mechanic(it.mechanic) }
}

/** A player as the view sees it: their [life] total, the rules applied to them (CR 613.10) and their statuses
 * ([arcanum.engine.state.PlayerStatus]: speed, the Ring, dungeons completed, designations). */
@Serializable
data class PlayerView(
    val id: PlayerId,
    val life: Int,
    val mechanics: List<AppliedMechanic>,
    val statuses: List<arcanum.engine.state.PlayerStatus> = emptyList(),
)

/**
 * The game as the view sees it: its [statuses] ([arcanum.engine.state.GameStatus]: day or night) and the [rules] of the game
 * from static abilities and effects with their own scope: replacements, prevention, cost changes, "triggers an additional
 * time", "damage can't be prevented", effects on the next spells (CR 613.11).
 */
@Serializable
data class GameView(val statuses: List<arcanum.engine.state.GameStatus>, val rules: List<AppliedMechanic>)

/** A [mechanic] in force, with where it comes from: a static ability ([source], [origin]) or an effect object
 * ([effect]); [controller] is its "you" (CR 109.5) and [names] the names it uses. */
@Serializable
data class AppliedMechanic(
    val mechanic: Mechanic,
    val controller: PlayerId,
    val timestamp: Long,
    val source: ObjectId? = null,
    val origin: AbilityOrigin? = null,
    val effect: EffectId? = null,
    val names: Bindings = Bindings.EMPTY,
)

/**
 * An object's characteristics (CR 109.3) after continuous effects. [name] is null for a face-down object (CR
 * 708.2a); [colors] in WUBRG order; [allCreatureTypes] for "is every creature type" (CR 702.73a, 205.3m).
 */
@Serializable
data class Characteristics(
    val name: String?,
    val manaCost: ManaCost? = null,
    val colors: List<Color> = emptyList(),
    val supertypes: List<Supertype> = emptyList(),
    val types: List<CardType> = emptyList(),
    val subtypes: List<Subtype> = emptyList(),
    val allCreatureTypes: Boolean = false,
    val power: Int? = null,
    val toughness: Int? = null,
    val loyalty: Int? = null,
    val defense: Int? = null,
    val abilities: List<AbilityInstance> = emptyList(),
) {
    fun isType(type: CardType): Boolean = type in types

    /** Has [subtype]; "every creature type" covers every creature type (CR 702.73a). */
    fun hasSubtype(subtype: Subtype): Boolean =
        subtype in subtypes || (allCreatureTypes && Subtypes.kindOf(subtype) == SubtypeKind.Creature)

    /** The keyword abilities it has (CR 702), granted ones included. */
    val keywords: List<Keyword> get() = abilities.mapNotNull { it.ability as? Keyword }

    fun hasKeyword(kind: KeywordKind): Boolean = abilities.any { (it.ability as? Keyword)?.kind == kind }

    /** Mana value (CR 202.3): X counts as [x] (0 except on the Stack, CR 202.3e). */
    fun manaValue(x: Int = 0): Int = manaCost?.symbols?.sumOf { manaValueOf(it, x) } ?: 0

    private fun manaValueOf(symbol: ManaSymbol, x: Int): Int = when (symbol) {
        is ManaSymbol.Generic -> symbol.amount
        is ManaSymbol.Variable -> x
        is ManaSymbol.GenericHybrid -> symbol.amount
        else -> 1
    }
}

/** One ability an object has, with where it came from (D42, D44): its [origin] path identifies it, so "activate
 * ability 2" and per-turn counts survive other abilities coming and going. [timestamp] is the grant's, for granted
 * abilities (CR 613.7a). */
@Serializable
data class AbilityInstance(val ability: Ability, val origin: AbilityOrigin, val timestamp: Long? = null)

/** Where an ability came from (D42/D44): a path, so it stays the same while the object exists. */
@Serializable
sealed interface AbilityOrigin {
    /** A stable text form, for keys (activation counts per ability, D41). */
    val key: String

    /** Printed on face [face] (of its copiable values, so a copy's printed abilities are the copied card's) at
     * [index]. */
    @Serializable @SerialName("printed") data class Printed(
        val face: Int, val index: Int,
        /** Printed on this other component of a merged permanent (CR 702.140e), not on its own card. */
        val card: arcanum.engine.state.CardId? = null,
    ) : AbilityOrigin {
        override val key: String get() = if (card == null) "p$face.$index" else "p$face.$index@$card"
    }

    /** From keyword ability [keyword]'s expansion ([arcanum.api.KeywordExpansions]), at [index]. */
    @Serializable @SerialName("expansion") data class Expansion(val keyword: AbilityOrigin, val index: Int) : AbilityOrigin {
        override val key: String get() = "${keyword.key}/x$index"
    }

    /** Granted by effect object [effect]: ability [index] of its mechanic [mechanic] (layer 6). */
    @Serializable @SerialName("effect") data class FromEffect(val effect: EffectId, val mechanic: Int, val index: Int) : AbilityOrigin {
        override val key: String get() = "e:${effect.value}/m$mechanic.$index"
    }

    /** Granted by static ability [by] of [source]: ability [index] of its mechanic [mechanic] (layer 6). */
    @Serializable @SerialName("granted") data class FromAbility(
        val source: ObjectId, val by: AbilityOrigin, val mechanic: Int, val index: Int,
    ) : AbilityOrigin {
        override val key: String get() = "g:$source/${by.key}/m$mechanic.$index"
    }

    /** The keyword of a keyword counter (CR 122.1b). */
    @Serializable @SerialName("counter") data class FromCounter(val kind: CounterKind) : AbilityOrigin {
        override val key: String get() = "k:$kind"
    }

    /** The intrinsic mana ability of basic land type [landType] (CR 305.6). */
    @Serializable @SerialName("land-type") data class LandType(val landType: Subtype) : AbilityOrigin {
        override val key: String get() = "l:${landType.name}"
    }

    /** Activated ability [origin] of [from], had through "has all activated abilities of" (layer 6). */
    @Serializable @SerialName("borrowed") data class Borrowed(val from: ObjectId, val origin: AbilityOrigin) : AbilityOrigin {
        override val key: String get() = "b:$from/${origin.key}"
    }

    /** A special action the rules give an object rather than an ability it has (CR 116.2): unlocking a Room's locked
     * door (CR 709.5e, [name] "unlock-left" / "unlock-right"), turning a manifested permanent face up (CR 701.40b,
     * "manifested"). */
    @Serializable @SerialName("rule") data class Rule(val name: String) : AbilityOrigin {
        override val key: String get() = "r:$name"
    }

    /** The first origin in the path: where the ability chain started. */
    val root: AbilityOrigin
        get() = when (this) {
            is Expansion -> keyword.root
            else -> this
        }
}
