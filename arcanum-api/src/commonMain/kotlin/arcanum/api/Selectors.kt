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
    /** An object of the triggering or replaced event ("it", "that creature"); [role] picks which one. In a trigger that
     * fires once for a batch ([Trigger.On.oneOrMore]) it is every object of the batch in that role ("those creatures"). */
    @Serializable @SerialName("event") data class EventObject(val role: EventRole = EventRole.Object) : Objects
    /** The object a source Aura or Equipment is attached to ("enchanted creature", "equipped creature"). */
    @Serializable @SerialName("attached-to") data class AttachedTo(val of: Objects = Self) : Objects
    /** The source's new object after it changed zones, when it went to a public zone ("return this card from your
     * graveyard" in a trigger on the enchanted creature dying, CR 400.7e–f, 603.6e). */
    @Serializable @SerialName("self-after") data object SelfAfter : Objects
    /** Inside an [ObjectFilter]: the object being tested ("creatures whose power is greater than their toughness"). */
    @Serializable @SerialName("tested") data object Tested : Objects
    /** The planeswalkers or battles [attackers] are attacking ("the planeswalker it's attacking"). */
    @Serializable @SerialName("attacked-by") data class AttackedBy(val attackers: Objects) : Objects
    /** Objects attached to [of] ("Auras attached to it"). */
    @Serializable @SerialName("attachments") data class AttachmentsOf(val of: Objects, val filter: ObjectFilter = ObjectFilter.Any) : Objects
    /** Every object matching [filter] ("each creature", "creatures you control"). On the battlefield unless the filter
     * names zones with [ObjectFilter.InZone] (anywhere in it: "cards you own in exile and in your graveyard"). */
    @Serializable @SerialName("each") data class Each(val filter: ObjectFilter) : Objects
    /** The top or bottom [count] cards of a library. */
    @Serializable @SerialName("library-cards") data class LibraryCards(val of: Players, val count: Value, val fromTop: Boolean = true) : Objects
    @Serializable @SerialName("union") data class Union(val parts: List<Objects>) : Objects
    /** In a static ability's or effect object's mechanics or condition: each affected object in turn ("for each other
     * creature that shares a creature type with it"). */
    @Serializable @SerialName("affected") data object Affected : Objects
    /** The cards linked to this object by its [keyword] ("the exiled card" of hideaway, "the haunted creature", the
     * cards used to craft it; CR 607.2a). The keyword table keeps them as card memory under the keyword's id. */
    @Serializable @SerialName("linked-to") data class LinkedTo(
        @Serializable(with = KeywordKindSerializer::class) val keyword: KeywordKind,
    ) : Objects
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
    /** The source of damage, or the spell or ability that targeted (for an ability, protection and hexproof-from look
     * at the object that generated it, CR 113.7). */
    @Serializable @SerialName("source") data object Source : EventRole
    /** In a block or becoming blocked: the creature on the other side ("destroy that creature" for "blocks or becomes
     * blocked by"). */
    @Serializable @SerialName("other-combatant") data object OtherCombatant : EventRole
    /** In an attack: the planeswalker or battle attacked (the attacked player is [Players.EventPlayer]). */
    @Serializable @SerialName("defender") data object Defender : EventRole
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
    /** Has [subtype] ("Elf", or "the chosen type" through [SubtypeRef.Chosen]). */
    @Serializable @SerialName("subtype") data class Sub(val subtype: SubtypeRef) : ObjectFilter {
        constructor(subtype: Subtype) : this(SubtypeRef.Literal(subtype))
    }
    /** Is [color] ("red", or "the chosen colour" through [ColorRef.Chosen]). */
    @Serializable @SerialName("color") data class OfColor(val color: ColorRef) : ObjectFilter {
        constructor(color: Color) : this(ColorRef.Literal(color))
    }
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
    /** An ability (on the Stack, or being activated) of [kind] (any when null), whose source matches [source], labelled
     * [label] ("activated or triggered ability you control from an artifact source", "an exhaust ability"). Its
     * controller is the ability's controller. Spells and abilities in other filters are matched as Stack objects too. */
    @Serializable @SerialName("ability") data class AbilityOf(
        val kind: AbilityKind? = null, val source: ObjectFilter? = null, val label: AbilityLabel? = null,
    ) : ObjectFilter
    /** A spell (or the permanent it became, CR 400.7d) that was cast, from [from] when given ("spells you cast from
     * exile"). */
    @Serializable @SerialName("was-cast") data class WasCast(val from: Zone? = null) : ObjectFilter
    /** Its [keyword] optional cost was paid at least [times] ("kicked spells"). */
    @Serializable @SerialName("was-paid") data class WasPaid(
        @Serializable(with = KeywordKindSerializer::class) val keyword: KeywordKind, val times: Int = 1,
    ) : ObjectFilter
    @Serializable @SerialName("status") data class HasStatus(val status: Status) : ObjectFilter
    /** Its controller hasn't controlled it continuously since their most recent turn began (CR 302.6). */
    @Serializable @SerialName("summoning-sick") data object SummoningSick : ObjectFilter
    /** Has [atLeast] counters of [kind], or of any kind when null ("with a counter on it"). */
    @Serializable @SerialName("counter") data class HasCounter(val kind: CounterKind?, val atLeast: Int = 1) : ObjectFilter

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

/** A subtype written out, or the one chosen under [Chosen.key] (a choice of this resolution or remembered on the source,
 * CR 607.2d). */
@Serializable
sealed interface SubtypeRef {
    @Serializable @SerialName("literal") data class Literal(val subtype: Subtype) : SubtypeRef
    /** Chosen by the [Step.Choose] named [name] in this resolution. */
    @Serializable @SerialName("chosen") data class Chosen(val name: String) : SubtypeRef
    /** Kept as card memory under [key] ("the chosen type" of a permanent, CR 607.2d). */
    @Serializable @SerialName("remembered") data class Remembered(val key: String) : SubtypeRef
}

/** A colour written out, the one chosen under [Chosen.key], or each colour of [Of.objects] ("a colour among the exiled
 * cards"). */
@Serializable
sealed interface ColorRef {
    @Serializable @SerialName("literal") data class Literal(val color: Color) : ColorRef
    @Serializable @SerialName("chosen") data class Chosen(val name: String) : ColorRef
    @Serializable @SerialName("remembered") data class Remembered(val key: String) : ColorRef
    /** Each colour of [objects]; for mana and colour-setting only (a filter for "shares a colour with" is
     * [ObjectFilter.Shares]). */
    @Serializable @SerialName("of") data class Of(val objects: Objects) : ColorRef
}

/** Object categories (CR 109.1, 110, 111, 112, 113). */
@Serializable
sealed interface ObjectCategory {
    @Serializable @SerialName("card") data object Card : ObjectCategory
    @Serializable @SerialName("token") data object Token : ObjectCategory
    @Serializable @SerialName("permanent") data object Permanent : ObjectCategory
    @Serializable @SerialName("spell") data object Spell : ObjectCategory
    /** An ability on the Stack, of any kind; [ObjectFilter.AbilityOf] says which kind and source. */
    @Serializable @SerialName("ability") data object StackAbility : ObjectCategory
}

@Serializable
sealed interface AbilityKind {
    @Serializable @SerialName("activated") data object Activated : AbilityKind
    @Serializable @SerialName("triggered") data object Triggered : AbilityKind
    @Serializable @SerialName("static") data object Static : AbilityKind
    @Serializable @SerialName("mana") data object Mana : AbilityKind
    @Serializable @SerialName("loyalty") data object Loyalty : AbilityKind
}

@Serializable
sealed interface SharedQuality {
    @Serializable @SerialName("color") data object Color : SharedQuality
    @Serializable @SerialName("card-type") data object CardType : SharedQuality
    @Serializable @SerialName("creature-type") data object CreatureType : SharedQuality
    @Serializable @SerialName("name") data object Name : SharedQuality
    /** Basic land types (domain: "the number of basic land types among lands you control"). */
    @Serializable @SerialName("basic-land-type") data object BasicLandType : SharedQuality
}

/** Which players something refers to, worked out when it is used. "You" is the controller of the spell or ability
 * on the Stack, or of the effect it created, captured when it was put on the Stack or created; for a static ability,
 * the current controller of the object it's on, or its owner if it has none, so a granted ability's "you" is the
 * creature's controller, not the Aura's (CR 109.5). */
@Serializable
sealed interface Players {
    @Serializable @SerialName("you") data object You : Players
    @Serializable @SerialName("target") data class Target(val slot: String) : Players
    @Serializable @SerialName("bound") data class Bound(val name: String) : Players
    @Serializable @SerialName("remembered") data class Remembered(val key: String) : Players
    /** The player the triggering or replaced event was about ("that player"): the caster, the player dealt damage, the
     * player who drew, discarded, gained or lost life, the active player of a step that begins, the player attacked
     * (or controlling what was attacked). In a batch trigger, every such player. */
    @Serializable @SerialName("event-player") data object EventPlayer : Players
    @Serializable @SerialName("controller-of") data class ControllerOf(val objects: Objects) : Players
    @Serializable @SerialName("owner-of") data class OwnerOf(val objects: Objects) : Players
    @Serializable @SerialName("active") data object Active : Players
    /** "Defending player" of [attackers]: the player they attack, or the controller of the planeswalker or protector of
     * the battle they attack (CR 506.2, 802.2a). In an attack trigger, "defending player" is
     * `DefendingPlayerOf(EventObject())`; [EventPlayer] is only for "the player it's attacking". */
    @Serializable @SerialName("defending-player-of") data class DefendingPlayerOf(val attackers: Objects) : Players
    /** Inside a [PlayerFilter.Where] condition: the player being tested. */
    @Serializable @SerialName("tested") data object Tested : Players
    /** In a [TargetSlot.perPlayer] slot's filter: the player this target is for ("target creature that player controls"). */
    @Serializable @SerialName("slot-player") data object SlotPlayer : Players
    /** The player [of] is attached to ("enchanted player", Curses, CR 303.4m). */
    @Serializable @SerialName("attached-to") data class AttachedTo(val of: Objects = Objects.Self) : Players
    /** In a static ability's mechanics or condition: each affected player in turn ("Each player can't cast more than one
     * spell each turn"). */
    @Serializable @SerialName("affected") data object Affected : Players
    /** Every player matching [filter] ("each opponent"). */
    @Serializable @SerialName("each") data class Each(val filter: PlayerFilter) : Players
}

/** A test a player passes or not, relative to the ability's controller ("you", "an opponent"). */
@Serializable
sealed interface PlayerFilter {
    @Serializable @SerialName("any") data object Any : PlayerFilter
    @Serializable @SerialName("you") data object You : PlayerFilter
    @Serializable @SerialName("opponent") data object Opponent : PlayerFilter
    /** The player passes [condition], read with [Players.Tested] ("each opponent who controls more lands than you", "the
     * player with the most life"). */
    @Serializable @SerialName("where") data class Where(val condition: Condition) : PlayerFilter
    @Serializable @SerialName("is") data class Is(val players: Players) : PlayerFilter
    @Serializable @SerialName("and") data class And(val all: List<PlayerFilter>) : PlayerFilter
    @Serializable @SerialName("or") data class Or(val any: List<PlayerFilter>) : PlayerFilter
    @Serializable @SerialName("not") data class Not(val filter: PlayerFilter) : PlayerFilter
    @Serializable @SerialName("designation") data class Has(val designation: Designation) : PlayerFilter
    @Serializable @SerialName("life") data class Life(val comparison: Comparison, val value: Value) : PlayerFilter
    @Serializable @SerialName("this-turn") data class ThisTurn(val event: EventPattern) : PlayerFilter
}
