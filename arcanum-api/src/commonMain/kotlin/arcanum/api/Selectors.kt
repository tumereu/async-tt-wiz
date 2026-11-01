package arcanum.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Which objects something refers to, worked out when it is used: the source itself, a target, something bound by
 * an earlier step, the object an event was about, or every object matching a filter.
 */
@Serializable
sealed interface Objects {
    /** The object the ability is on ("~", "this creature"). */
    @Serializable @SerialName("self") data object Self : Objects
    /** The chosen targets of slot [slot]. */
    @Serializable @SerialName("target") data class Target(val slot: String) : Objects
    /** What an earlier step bound under [name] ("the exiled card", "that creature"). */
    @Serializable @SerialName("bound") data class Bound(val name: String) : Objects
    /** Card memory kept on the source under [key] ("the exiled card" of an O-Ring, CR 607). */
    @Serializable @SerialName("remembered") data class Remembered(val key: String) : Objects
    /** An object of the triggering or replaced event ("it", "that creature"); [role] picks which one. */
    @Serializable @SerialName("event") data class EventObject(val role: EventRole = EventRole.Object) : Objects
    /** The object a source Aura or Equipment is attached to ("enchanted creature", "equipped creature"). */
    @Serializable @SerialName("attached-to") data class AttachedTo(val of: Objects = Self) : Objects
    /** Objects attached to [of] ("Auras attached to it"). */
    @Serializable @SerialName("attachments") data class AttachmentsOf(val of: Objects, val filter: ObjectFilter = ObjectFilter.Any) : Objects
    /** Every object matching [filter] ("each creature", "creatures you control"). Battlefield unless the filter says. */
    @Serializable @SerialName("each") data class Each(val filter: ObjectFilter) : Objects
    /** The top or bottom [count] cards of a library. */
    @Serializable @SerialName("library-cards") data class LibraryCards(val of: Players, val count: Value, val fromTop: Boolean = true) : Objects
    @Serializable @SerialName("union") data class Union(val parts: List<Objects>) : Objects
    /** [base] without [minus] ("the rest", "the other cards"). */
    @Serializable @SerialName("except") data class Except(val base: Objects, val minus: Objects) : Objects
}

/**
 * Which object of an event a reference means. Each [EventPattern] names the roles it has; [DefinitionChecks] reports
 * roles an ability's trigger events don't have.
 */
@Serializable
sealed interface EventRole {
    /** The object the event is about: the spell cast, the creature attacking or blocking, the permanent tapped, the
     * object or player's object dealt damage, the object that became a target. */
    @Serializable @SerialName("object") data object Object : EventRole
    /** In a zone change: the object as it was before the move, its last known information (CR 603.10a, 608.2h). */
    @Serializable @SerialName("before") data object Before : EventRole
    /** In a zone change: the new object after the move (CR 400.7). */
    @Serializable @SerialName("after") data object After : EventRole
    /** The source of damage, or the spell or ability that targeted (CR 113.7 for an ability's source). */
    @Serializable @SerialName("source") data object Source : EventRole
    /** In a block: the attacking creature blocked. */
    @Serializable @SerialName("attacker") data object Attacker : EventRole
}

/**
 * A test an object passes or not. Combine with [And], [Or], [Not]. Filters without a zone mean the battlefield when
 * used for permanents (CR 109.2), the Stack for spells.
 */
@Serializable
sealed interface ObjectFilter {
    @Serializable @SerialName("any") data object Any : ObjectFilter
    @Serializable @SerialName("and") data class And(val all: List<ObjectFilter>) : ObjectFilter
    @Serializable @SerialName("or") data class Or(val any: List<ObjectFilter>) : ObjectFilter
    @Serializable @SerialName("not") data class Not(val filter: ObjectFilter) : ObjectFilter
    /** Is one of [objects] ("~", "that creature"); "another" is Not(Is(Self)). */
    @Serializable @SerialName("is") data class Is(val objects: Objects) : ObjectFilter

    /** What kind of object (CR 109.1): card, token, spell, permanent, ability on the Stack. */
    @Serializable @SerialName("category") data class Category(val category: ObjectCategory) : ObjectFilter
    @Serializable @SerialName("type") data class Type(val cardType: CardType) : ObjectFilter
    @Serializable @SerialName("supertype") data class Super(val supertype: Supertype) : ObjectFilter
    @Serializable @SerialName("subtype") data class Sub(val subtype: Subtype) : ObjectFilter
    /** Has the creature type remembered or bound under [key] ("creatures of the chosen type"). */
    @Serializable @SerialName("chosen-subtype") data class ChosenSub(val key: String) : ObjectFilter
    @Serializable @SerialName("color") data class OfColor(val color: Color) : ObjectFilter
    @Serializable @SerialName("chosen-color") data class ChosenColor(val key: String) : ObjectFilter
    @Serializable @SerialName("colorless") data object Colorless : ObjectFilter
    @Serializable @SerialName("multicolored") data object Multicolored : ObjectFilter
    @Serializable @SerialName("monocolored") data object Monocolored : ObjectFilter
    /** Shares a colour / card type / creature type with [objects]. */
    @Serializable @SerialName("shares") data class Shares(val quality: SharedQuality, val objects: Objects) : ObjectFilter

    @Serializable @SerialName("controlled-by") data class ControlledBy(val players: PlayerFilter) : ObjectFilter
    @Serializable @SerialName("owned-by") data class OwnedBy(val players: PlayerFilter) : ObjectFilter
    @Serializable @SerialName("in-zone") data class InZone(val zone: Zone) : ObjectFilter

    @Serializable @SerialName("name") data class Named(val name: String) : ObjectFilter
    @Serializable @SerialName("same-name") data class SameNameAs(val objects: Objects) : ObjectFilter
    @Serializable @SerialName("chosen-name") data class ChosenName(val key: String) : ObjectFilter

    /** Has the keyword ability [keyword] ("creatures with flying"), including typecycling for cycling (CR 702.29f). */
    @Serializable @SerialName("has-keyword") data class HasKeyword(
        @Serializable(with = KeywordKindSerializer::class) val keyword: KeywordKind,
    ) : ObjectFilter
    /** Has at least one ability of [kind] ("creatures with activated abilities"). */
    @Serializable @SerialName("has-ability-kind") data class HasAbilityKind(val kind: AbilityKind) : ObjectFilter
    @Serializable @SerialName("status") data class HasStatus(val status: Status) : ObjectFilter
    /** Its controller hasn't controlled it continuously since their most recent turn began (CR 302.6). */
    @Serializable @SerialName("summoning-sick") data object SummoningSick : ObjectFilter
    @Serializable @SerialName("counter") data class HasCounter(val kind: CounterKind, val atLeast: Int = 1) : ObjectFilter

    @Serializable @SerialName("power") data class Power(val comparison: Comparison, val value: Value) : ObjectFilter
    @Serializable @SerialName("toughness") data class Toughness(val comparison: Comparison, val value: Value) : ObjectFilter
    @Serializable @SerialName("mana-value") data class ManaValue(val comparison: Comparison, val value: Value) : ObjectFilter

    /** A spell or ability that targets something in [objects] or [players] ("spells that target you"). */
    @Serializable @SerialName("targets") data class Targets(val objects: Objects? = null, val players: Players? = null) : ObjectFilter
    /** Attached to something in [objects] ("Auras attached to it") or has attachments matching [attachments]
     * ("enchanted creatures"). */
    @Serializable @SerialName("attached") data class Attached(val to: Objects? = null, val attachments: ObjectFilter? = null) : ObjectFilter
    /** Something happened to it this turn ("creatures that attacked this turn", "entered this turn"). */
    @Serializable @SerialName("this-turn") data class ThisTurn(val event: EventPattern) : ObjectFilter
}

/** Object categories (CR 109.1, 110, 111, 112, 113). */
@Serializable
sealed interface ObjectCategory {
    @Serializable @SerialName("card") data object Card : ObjectCategory
    @Serializable @SerialName("token") data object Token : ObjectCategory
    @Serializable @SerialName("permanent") data object Permanent : ObjectCategory
    @Serializable @SerialName("spell") data object Spell : ObjectCategory
    @Serializable @SerialName("ability") data object StackAbility : ObjectCategory
}

@Serializable
sealed interface AbilityKind {
    @Serializable @SerialName("activated") data object Activated : AbilityKind
    @Serializable @SerialName("triggered") data object Triggered : AbilityKind
    @Serializable @SerialName("static") data object Static : AbilityKind
    @Serializable @SerialName("mana") data object Mana : AbilityKind
}

@Serializable
sealed interface SharedQuality {
    @Serializable @SerialName("color") data object Color : SharedQuality
    @Serializable @SerialName("card-type") data object CardType : SharedQuality
    @Serializable @SerialName("creature-type") data object CreatureType : SharedQuality
    @Serializable @SerialName("name") data object Name : SharedQuality
}

/** Which players something refers to, worked out when it is used. "You" is the controller of the spell or ability
 * on the Stack, or of the effect it created, captured when it was put on the Stack or created; for a static ability,
 * the current controller of its source (CR 109.5). */
@Serializable
sealed interface Players {
    @Serializable @SerialName("you") data object You : Players
    @Serializable @SerialName("target") data class Target(val slot: String) : Players
    @Serializable @SerialName("bound") data class Bound(val name: String) : Players
    @Serializable @SerialName("remembered") data class Remembered(val key: String) : Players
    /** The player the triggering or replaced event was about ("that player"): the caster, the player dealt damage, the
     * player who drew, discarded, gained or lost life. */
    @Serializable @SerialName("event-player") data object EventPlayer : Players
    @Serializable @SerialName("controller-of") data class ControllerOf(val objects: Objects) : Players
    @Serializable @SerialName("owner-of") data class OwnerOf(val objects: Objects) : Players
    @Serializable @SerialName("active") data object Active : Players
    @Serializable @SerialName("defending") data object Defending : Players
    /** Every player matching [filter] ("each opponent"). */
    @Serializable @SerialName("each") data class Each(val filter: PlayerFilter) : Players
}

/** A test a player passes or not, relative to the ability's controller ("you", "an opponent"). */
@Serializable
sealed interface PlayerFilter {
    @Serializable @SerialName("any") data object Any : PlayerFilter
    @Serializable @SerialName("you") data object You : PlayerFilter
    @Serializable @SerialName("opponent") data object Opponent : PlayerFilter
    @Serializable @SerialName("is") data class Is(val players: Players) : PlayerFilter
    @Serializable @SerialName("and") data class And(val all: List<PlayerFilter>) : PlayerFilter
    @Serializable @SerialName("or") data class Or(val any: List<PlayerFilter>) : PlayerFilter
    @Serializable @SerialName("not") data class Not(val filter: PlayerFilter) : PlayerFilter
    @Serializable @SerialName("designation") data class Has(val designation: Designation) : PlayerFilter
    @Serializable @SerialName("life") data class Life(val comparison: Comparison, val value: Value) : PlayerFilter
    @Serializable @SerialName("this-turn") data class ThisTurn(val event: EventPattern) : PlayerFilter
}
