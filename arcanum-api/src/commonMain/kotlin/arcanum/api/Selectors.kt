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
    @Serializable @SerialName("event") data class EventObject(val role: EventRole) : Objects
    /** The object a source Aura or Equipment is attached to ("enchanted creature", "equipped creature"). */
    @Serializable @SerialName("attached-to") data class AttachedTo(val of: Objects) : Objects
    /** The source's new object after it changed zones, when it went to a public zone ("return this card from your
     * graveyard" in a trigger on the enchanted creature dying, CR 400.7e–f, 603.6e). */
    @Serializable @SerialName("self-after") data object SelfAfter : Objects
    /** Inside an [ObjectFilter]: the object being tested ("creatures whose power is greater than their toughness"). */
    @Serializable @SerialName("tested") data object Tested : Objects
    /** The planeswalkers or battles [attackers] are attacking ("the planeswalker it's attacking"). */
    @Serializable @SerialName("attacked-by") data class AttackedBy(val attackers: Objects) : Objects
    /** Objects attached to [of] ("Auras attached to it"). */
    @Serializable @SerialName("attachments") data class AttachmentsOf(val of: Objects, val filter: ObjectFilter) : Objects
    /** Every object matching [filter] ("each creature", "creatures you control"). On the battlefield unless the filter
     * names zones with [ObjectFilter.InZone] (anywhere in it: "cards you own in exile and in your graveyard"). */
    @Serializable @SerialName("each") data class Each(val filter: ObjectFilter) : Objects
    /** The top or bottom [count] cards of a library. */
    @Serializable @SerialName("library-cards") data class LibraryCards(val of: Players, val count: Value, val fromTop: Boolean) : Objects
    @Serializable @SerialName("union") data class Union(val parts: List<Objects>) : Objects
    /** The creature each of [of] is paired with by soulbond (CR 702.95b): "the creature it's paired with". */
    @Serializable @SerialName("paired-with") data class PairedWith(val of: Objects) : Objects
    /** In a static ability's or effect object's mechanics or condition: each affected object in turn ("for each other
     * creature that shares a creature type with it"). */
    @Serializable @SerialName("affected") data object Affected : Objects
    /** The cards linked to this object by its [keyword] ("the exiled card" of hideaway, "the haunted creature", the
     * cards used to craft it; CR 607.2a). The keyword table keeps them as card memory under the keyword's id. */
    @Serializable @SerialName("linked-to") data class LinkedTo(
        @Serializable(with = KeywordKindSerializer::class) val keyword: KeywordKind,
    ) : Objects
    /** The objects used to pay [of]'s costs in [way] ("each creature that convoked it", CR 702.51c). */
    @Serializable @SerialName("paid-with") data class PaidWith(val way: ManaPaymentWay, val of: Objects) : Objects
    /** [count] of [of] chosen at random ("a card at random in target player's hand"); answered by the host (D25). */
    @Serializable @SerialName("random") data class Random(val of: Objects, val count: Value) : Objects
    /** Those of [objects] that match [filter], wherever they are ("each nonland card discarded this way"); unlike
     * [Each], which looks at the battlefield unless the filter names zones. */
    @Serializable @SerialName("where") data class Where(val objects: Objects, val filter: ObjectFilter) : Objects
    /** [base] without [minus] ("the rest", "the other cards"). */
    @Serializable @SerialName("except") data class Except(val base: Objects, val minus: Objects) : Objects
    // tla
    /** In an ability another object's static ability gives (layer 6), the object giving it ('Equipped creature has "… Return this
     * Equipment to its owner's hand."', CR 113.10); elsewhere [Self]. */
    @Serializable @SerialName("granter") data object Granter : Objects
    // isd
    /** In a token's ability, the object whose ability created the token, as long as it is still that object ('create a green Ooze
     * creature token with "This token's power and toughness are each equal to the number of slime counters on Gutter Grime."',
     * CR 201.5a); a copy of the token refers to the object that created the original. Nothing once that object has left its zone
     * (CR 400.7) or for an object no ability created. */
    @Serializable @SerialName("creator") data object Creator : Objects
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
     * blocked by"); in an enlist: the creature enlisted. */
    @Serializable @SerialName("other-combatant") data object OtherCombatant : EventRole
    /** In an attack: the planeswalker or battle attacked (the attacked player is [Players.EventPlayer]). */
    @Serializable @SerialName("defender") data object Defender : EventRole
    /** In an unattach event: the object or player it was attached to. */
    @Serializable @SerialName("from") data object From : EventRole
    /** In a manifest-dread event: the cards put into the graveyard (CR 701.62a). */
    @Serializable @SerialName("rest") data object Rest : EventRole
    // otj, one
    /** In an attach event: the object or player it became attached to ("that creature" of "whenever this Equipment becomes
     * attached to a creature", "for as long as this Equipment remains attached to it", CR 701.3a, 608.2h). */
    @Serializable @SerialName("to") data object To : EventRole
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
    /** Has [cardType] ("creature", or "the chosen type" through [CardTypeRef.Chosen]); [ignoringTypeChanges]: as it would
     * be without type-changing effects (sunburst, CR 702.44a). */
    @Serializable @SerialName("type") data class Type(val cardType: CardTypeRef, val ignoringTypeChanges: Boolean = false) : ObjectFilter {
        constructor(cardType: CardType, ignoringTypeChanges: Boolean = false) : this(CardTypeRef.Literal(cardType), ignoringTypeChanges)
    }
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
    /** Has the card name chosen by the [Step.Choose] named [name] in this resolution. */
    @Serializable @SerialName("chosen-name") data class ChosenName(val name: String) : ObjectFilter
    /** Has the card name kept as card memory under [key] ("the chosen name" of a permanent). */
    @Serializable @SerialName("remembered-name") data class RememberedName(val key: String) : ObjectFilter

    /** Has the keyword ability [keyword] ("creatures with flying"), including typecycling for cycling (CR 702.29f). */
    @Serializable @SerialName("has-keyword") data class HasKeyword(
        @Serializable(with = KeywordKindSerializer::class) val keyword: KeywordKind,
    ) : ObjectFilter
    /** Has at least one ability of [kind] ("creatures with activated abilities"). */
    @Serializable @SerialName("has-ability-kind") data class HasAbilityKind(val kind: AbilityKind) : ObjectFilter
    // rvr
    /** Has an activated ability with the tap symbol in its cost (CR 107.5: "a creature that has an activated ability with {T} in its
     * cost"); mana abilities count (CR 605.1a). */
    @Serializable @SerialName("has-tap-ability") data object HasTapAbility : ObjectFilter
    /** A permanent one of [attachment] could legally be attached to as it is (CR 701.3b): an Aura as its enchant abilities allow
     * (CR 303.4d), an Equipment to a creature (CR 301.5c), a Fortification to a land (CR 301.6), never to itself or to a permanent
     * with protection from it (CR 702.16c, d) ("attach target Aura … to another permanent" chooses only among these). */
    @Serializable @SerialName("attachable-by") data class AttachableBy(val attachment: Objects) : ObjectFilter
    /** An ability (on the Stack, or being activated) of [kind] (any when null), whose source matches [source], labelled
     * [label] ("activated or triggered ability you control from an artifact source", "an exhaust ability"). Its
     * controller is the ability's controller. Spells and abilities in other filters are matched as Stack objects too. */
    @Serializable @SerialName("ability") data class AbilityOf(
        val kind: AbilityKind? = null, val source: ObjectFilter? = null, val label: AbilityLabel? = null,
        /** An ability from this keyword's expansion ("a modular triggered ability"), or foretelling, the special action the rules give
         * foretell (CR 702.143a; what changes its cost or timing names it this way). */
        @Serializable(with = KeywordKindSerializer::class) val keyword: KeywordKind? = null,
    ) : ObjectFilter
    /** A spell (or the permanent it became, CR 400.7d) that was cast, from [from] when given ("spells you cast from
     * exile"). */
    @Serializable @SerialName("was-cast") data class WasCast(val from: Zone? = null) : ObjectFilter
    /** Its [keyword] optional cost was paid at least [times] ("kicked spells"), or its [keyword] alternative cost (eoe: "a spell was warped",
     * CR 702.185c). */
    @Serializable @SerialName("was-paid") data class WasPaid(
        @Serializable(with = KeywordKindSerializer::class) val keyword: KeywordKind, val times: Int,
    ) : ObjectFilter
    @Serializable @SerialName("status") data class HasStatus(val status: Status) : ObjectFilter
    /** Its controller hasn't controlled it continuously since their most recent turn began (CR 302.6). */
    @Serializable @SerialName("summoning-sick") data object SummoningSick : ObjectFilter
    /** A creature paired with another by soulbond (CR 702.95b); "unpaired" is its [Not]. */
    @Serializable @SerialName("paired") data object Paired : ObjectFilter
    /** Has [atLeast] counters of [kind], or of any kind when null ("with a counter on it"). */
    @Serializable @SerialName("counter") data class HasCounter(val kind: CounterKind?, val atLeast: Int) : ObjectFilter

    @Serializable @SerialName("power") data class Power(val comparison: Comparison, val value: Value) : ObjectFilter
    @Serializable @SerialName("toughness") data class Toughness(val comparison: Comparison, val value: Value) : ObjectFilter
    @Serializable @SerialName("mana-value") data class ManaValue(val comparison: Comparison, val value: Value) : ObjectFilter

    /** A spell or ability that targets something in [objects] or [players] ("spells that target you"). */
    @Serializable @SerialName("targets") data class Targets(val objects: Objects? = null, val players: Players? = null) : ObjectFilter
    /** Attached to something in [objects] ("Auras attached to it") or has attachments matching [attachments]
     * ("enchanted creatures"); or attached to one of [toPlayers] ("Curses attached to you", an Aura enchanting a player, CR 303.4e). */
    @Serializable @SerialName("attached") data class Attached(val to: Objects? = null, val attachments: ObjectFilter? = null, val toPlayers: Players? = null) : ObjectFilter
    /** Something happened to it this turn ("creatures that attacked this turn", "entered this turn"), in [role] of the
     * event when given (any role when null): "creature that dealt damage this turn" is the [EventRole.Source] of a damage
     * event, "creature that was dealt damage this turn" its [EventRole.Object]. */
    @Serializable @SerialName("this-turn") data class ThisTurn(
        val event: EventPattern,
        // rtr chunk 05
        val role: EventRole? = null,
    ) : ObjectFilter
    /** A spell or ability with exactly one target, which matches [target] when given ("target spell or ability with a
     * single target", "a spell that targets only a single creature you control", CR 115.7). */
    @Serializable @SerialName("single-target") data class SingleTarget(
        val target: ObjectFilter? = null,
        // mh2
        /** With [target]: the single target may also be a player matching this ("a spell that targets only a single permanent or player"). */
        val players: PlayerFilter? = null,
    ) : ObjectFilter
    // gtc chunk 07
    /** A blocking creature that blocks one of [objects] ("creatures blocking it", CR 509.1a). */
    @Serializable @SerialName("blocking") data class Blocking(val objects: Objects) : ObjectFilter
    /** An attacking creature blocked by one of [objects] ("creatures blocked by this creature", CR 509.1h). */
    @Serializable @SerialName("blocked-by") data class BlockedBy(val objects: Objects) : ObjectFilter
    // blb gaps 2
    /** Its mana cost has an {X} in it ("creature spells with {X} in their mana costs", CR 107.3, 202.1). */
    @Serializable @SerialName("x-in-mana-cost") data object XInManaCost : ObjectFilter
    /** A spell that is the [n]th spell matching [spells] its caster cast this turn ("if it's the first instant spell …
     * you've cast this turn", CR 603.4): cast spells counted in order, as they were as cast (CR 601.2i); copies aren't
     * cast (CR 707.10). A spell that became this object (CR 400.7) is judged as this object, so "the first Otter spell other
     * than [this]" doesn't count the spell this permanent was. Which spell of the turn it is can't change once it is cast. */
    @Serializable @SerialName("nth-spell-this-turn") data class NthSpellThisTurn(val spells: ObjectFilter, val n: Int) : ObjectFilter
    // fra
    /** A prepare spell: the copy of a prepared permanent's prepare spell cast from exile, or a copy of such a spell
     * ("Whenever you cast a prepared spell", CR 722.3c, d). */
    @Serializable @SerialName("prepare-spell") data object PrepareSpell : ObjectFilter
    /** It has dealt combat damage since it became this object (CR 400.7): "as long as they haven't dealt combat damage
     * yet". */
    @Serializable @SerialName("has-dealt-combat-damage") data object HasDealtCombatDamage : ObjectFilter
    // tdm
    /** It has dealt damage, combat or not, since it became this object (CR 400.7): "as long as it hasn't dealt damage yet". */
    @Serializable @SerialName("has-dealt-damage") data object HasDealtDamage : ObjectFilter
    // msh
    /** An attacking creature that attacked alone: the only creature declared as an attacker in its combat (CR 506.5, "a
     * creature you control that's attacking alone"). */
    @Serializable @SerialName("attacking-alone") data object AttackingAlone : ObjectFilter
    /** Targeted by one of [objects], spells or abilities ("Whenever you cast a spell that targets one or more creatures, those
     * creatures gain flying": the creatures that spell targets, CR 115.1). */
    @Serializable @SerialName("targeted-by") data class TargetedBy(val objects: Objects) : ObjectFilter
    // hob
    /** The object passes [condition], read with [Objects.Tested] as that object ("a spell with mana value of the chosen
     * quality"). */
    @Serializable @SerialName("where") data class Where(val condition: Condition) : ObjectFilter
    // tmt
    /** It crewed one of [vehicles] this turn: it was tapped to pay the cost of that Vehicle's crew ability (CR 702.122b, c;
     * "target creature that crewed it this turn"). */
    @Serializable @SerialName("crewed-this-turn") data class CrewedThisTurn(val vehicles: Objects) : ObjectFilter
    // fin
    /** A Vehicle that [crew] crewed this turn (one of the creatures tapped to pay its crew cost, CR 702.122b; "a Vehicle crewed by Balthier
     * and Fran this turn"). */
    @Serializable @SerialName("crewed-by") data class CrewedBy(val crew: Objects) : ObjectFilter
    // otj
    /** It saddled one of [mounts] this turn: it was tapped to pay the cost of that Mount's saddle ability (CR 702.171c; "target
     * creature that saddled it this turn"). */
    @Serializable @SerialName("saddled-this-turn") data class SaddledThisTurn(val mounts: Objects) : ObjectFilter
    /** A modal spell or card (CR 700.2): its spell ability has modes ("Choose one —", spree's "+ {1} —" modes; a permanent spell
     * with a modal triggered ability isn't one). A card of several faces is judged by the face it has now (cast, CR 709.3). */
    @Serializable @SerialName("modal") data object Modal : ObjectFilter
    // spm
    /** A modified permanent (CR 700.9): it has a counter on it, is equipped, or is enchanted by an Aura its controller
     * controls ("a modified creature you control"). */
    @Serializable @SerialName("modified") data object Modified : ObjectFilter
    // dft
    /** It has no abilities (CR 113.1: no spell, activated, triggered or static ability, keywords included; "a creature card with no
     * abilities"). */
    @Serializable @SerialName("no-abilities") data object NoAbilities : ObjectFilter
    // mh2
    /** It could produce mana of [mana]'s kind, a single symbol ("{C}", "{R}"): one of its mana abilities, those its land types give
     * included, adds mana of that colour or colourless mana, whatever its costs (CR 106.7: "a land that could produce {C}"). */
    @Serializable @SerialName("could-produce") data class CouldProduce(val mana: ManaCost) : ObjectFilter
    /** Its mana cost is exactly [cost] ("an artifact card with mana cost {0} or {1}", CR 202.1); a card with no mana cost has none. */
    @Serializable @SerialName("has-mana-cost") data class HasManaCost(val cost: ManaCost) : ObjectFilter
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

/** A card type written out, or the one chosen under [Chosen.name] (a choice of this resolution) or kept under
 * [Remembered.key] (card memory of the source, CR 607.2d). */
@Serializable
sealed interface CardTypeRef {
    @Serializable @SerialName("literal") data class Literal(val cardType: CardType) : CardTypeRef
    /** Chosen by the [Step.Choose] named [name] in this resolution. */
    @Serializable @SerialName("chosen") data class Chosen(val name: String) : CardTypeRef
    /** Kept as card memory under [key] ("the chosen card type" of a permanent, CR 607.2d). */
    @Serializable @SerialName("remembered") data class Remembered(val key: String) : CardTypeRef
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
    /** The special action of paying a locked door's mana cost to unlock it (CR 709.5e): "unlock costs", "spend this mana
     * only to unlock a door". */
    @Serializable @SerialName("unlock") data object Unlock : AbilityKind
    /** A special action that turns a face-down permanent face up (CR 116.2b: morph, disguise, manifest, cloak): "spend this
     * mana only to turn a permanent face up". */
    @Serializable @SerialName("turn-face-up") data object TurnFaceUp : AbilityKind
}

@Serializable
sealed interface SharedQuality {
    @Serializable @SerialName("color") data object Color : SharedQuality
    @Serializable @SerialName("card-type") data object CardType : SharedQuality
    @Serializable @SerialName("creature-type") data object CreatureType : SharedQuality
    @Serializable @SerialName("name") data object Name : SharedQuality
    /** Basic land types (domain: "the number of basic land types among lands you control"). */
    @Serializable @SerialName("basic-land-type") data object BasicLandType : SharedQuality
    // fra
    /** Planeswalker types (CR 205.3j: "the number of planeswalker types among planeswalkers you control"). */
    @Serializable @SerialName("planeswalker-type") data object PlaneswalkerType : SharedQuality
    /** The value of [property] ("creatures with different powers"). */
    @Serializable @SerialName("number") data class Number(val property: NumericProperty) : SharedQuality
    // tdm
    /** Kinds of counters on it (CR 122.1: "three or more different kinds of counters among creatures you control"). */
    @Serializable @SerialName("counter-kind") data object CounterKind : SharedQuality
    // rvr
    /** Permanent types: artifact, battle, creature, enchantment, land, planeswalker (CR 110.4a: "shares a permanent type with it"). */
    @Serializable @SerialName("permanent-type") data object PermanentType : SharedQuality
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
    /** In a permission ([Mechanic.MayPlay]): the player using it ("Any player may cast creature spells … from their
     * hand": cards owned by the holder). */
    @Serializable @SerialName("holder") data object Holder : Players
    /** Inside a [PlayerFilter.Where] condition: the player being tested. */
    @Serializable @SerialName("tested") data object Tested : Players
    /** In a [TargetSlot.perPlayer] slot's filter: the player this target is for ("target creature that player controls"). */
    @Serializable @SerialName("slot-player") data object SlotPlayer : Players
    /** The player [of] is attached to ("enchanted player", Curses, CR 303.4m). */
    @Serializable @SerialName("attached-to") data class AttachedTo(val of: Objects) : Players
    /** In a static ability's mechanics or condition: each affected player in turn ("Each player can't cast more than one
     * spell each turn"). */
    @Serializable @SerialName("affected") data object Affected : Players
    /** Every player matching [filter] ("each opponent"). */
    @Serializable @SerialName("each") data class Each(val filter: PlayerFilter) : Players
    /** The player chosen by a [CostPart.ChoosePlayer] cost of [of] ("the chosen player" of a gift, CR 702.174d), kept by
     * the permanent a spell becomes (CR 400.7d); none when it wasn't paid. */
    @Serializable @SerialName("chosen-as-cost") data class ChosenAsCost(val of: Objects) : Players
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
