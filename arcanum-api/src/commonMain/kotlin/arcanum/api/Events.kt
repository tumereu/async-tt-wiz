package arcanum.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A kind of game event, used three ways: what a [Trigger] waits for (CR 603), what a [Mechanic.Replacement] replaces
 * (CR 614), and what happened this turn ([Condition.HappenedThisTurn]). The [EventRole]s each pattern has are named in
 * its KDoc ([EventRole.Object] unless said otherwise); [Players.EventPlayer] and [Value.EventAmount] read its player
 * and amount.
 */
@Serializable
sealed interface EventPattern {
    /** An object matching [objects] moves from [from] to [to] (null = any zone). "Enters" = to battlefield; "dies" =
     * put into a graveyard from the battlefield (CR 700.4). Roles [EventRole.Before] (last known information) and
     * [EventRole.After] (the new object). Trigger conditions look back in time for leaving the battlefield, sacrifice,
     * leaving a graveyard, and an object all players can see put into a hand or library (CR 603.10a). Playing a land
     * is also [PlayLand]. */
    @Serializable @SerialName("zone-change") data class ZoneChange(
        val objects: ObjectFilter, val from: Zone? = null, val to: Zone? = null,
        /** Only the move of a spell as it finishes resolving ("as it resolves", CR 608.2n), not a countered spell. */
        val resolving: Boolean = false,
        /** Only a move to any zone but this one ("leaves the battlefield without dying": not to a graveyard, CR 700.4). */
        val notTo: Zone? = null,
    ) : EventPattern
    /** A spell matching [spell] is cast by a player matching [by], from [from] when given ("whenever you cast a spell
     * from exile"). Role object = the spell. */
    @Serializable @SerialName("cast") data class Cast(
        val spell: ObjectFilter, val by: PlayerFilter, val from: Zone? = null,
    ) : EventPattern
    /** An ability of an object matching [source] is activated; [manaAbilities] whether mana abilities count. */
    @Serializable @SerialName("activate") data class Activate(
        val source: ObjectFilter, val by: PlayerFilter, val manaAbilities: Boolean = false,
        // fra
        /** Only abilities of this kind ("whenever you activate a loyalty ability", CR 606). */
        val kind: AbilityKind? = null,
        /** Only a loyalty ability whose cost removed at least this many loyalty counters ("if you removed two or more loyalty
         * counters to activate it": its cost can't change once paid, so it is part of the event, CR 606.4, 603.4). */
        val removedLoyaltyAtLeast: Int? = null,
    ) : EventPattern
    /** Damage from [source] to [to]; [combat] true = combat damage only, false = noncombat only, null = any; [excess]:
     * only damage over lethal (CR 120.4a). Roles source, object (the recipient); amount = the damage. */
    @Serializable @SerialName("damage") data class Damage(
        val source: ObjectFilter, val to: RecipientFilter, val combat: Boolean? = null,
        val excess: Boolean = false,
    ) : EventPattern
    @Serializable @SerialName("gain-life") data class GainLife(val players: PlayerFilter) : EventPattern
    @Serializable @SerialName("lose-life") data class LoseLife(val players: PlayerFilter) : EventPattern
    /** A player draws a card ("your second card each turn" is [Trigger.On.nthThisTurn]); [exceptFirstIn]: not the first card
     * that player draws in each of their own [exceptFirstIn] steps ("except the first one they draw in each of their draw
     * steps", dgm). */
    @Serializable @SerialName("draw") data class Draw(
        val players: PlayerFilter,
        // dgm
        val exceptFirstIn: TurnStep? = null,
    ) : EventPattern
    /** A player discards a card. Roles before (the card in hand, last known information) and after (the card in the
     * graveyard, or wherever a replacement put it). */
    @Serializable @SerialName("discard") data class Discard(
        val cards: ObjectFilter, val players: PlayerFilter,
        // rtr chunk 06
        /** Only a discard caused by a spell or ability matching this ("If a spell or ability an opponent controls causes you to
         * discard this card"); a discard as a cost or a game rule (the cleanup step) has no such cause. */
        val causedBy: ObjectFilter? = null,
    ) : EventPattern
    @Serializable @SerialName("sacrifice") data class Sacrifice(val objects: ObjectFilter) : EventPattern
    /** Counters of [kind] are put on an object matching [objects], by an effect of a spell or ability matching [causedBy]
     * when given ("by a modular triggered ability"). */
    @Serializable @SerialName("counters") data class CountersPut(
        val objects: ObjectFilter, val kind: CounterKind? = null, val causedBy: ObjectFilter? = null,
    ) : EventPattern
    /** An Aura, Equipment or Fortification matching [objects] becomes unattached from something matching [from]
     * (CR 701.3d). Roles object (the attachment), from (what it was attached to, last known information). */
    @Serializable @SerialName("unattach") data class Unattach(val objects: ObjectFilter, val from: ObjectFilter) : EventPattern
    /** Counters of [kind] are removed from an object matching [objects]; [last]: only when that removal leaves none
     * ("when the last time counter is removed", CR 702.63a), part of the trigger event, not an intervening if. */
    @Serializable @SerialName("counters-removed") data class CountersRemoved(
        val objects: ObjectFilter, val kind: CounterKind? = null, val last: Boolean = false,
    ) : EventPattern
    /** Counters of [kind] are put on a player matching [players], by an effect of a spell or ability matching [causedBy]
     * when given (blb: "If you would put one or more counters on a permanent or player"). */
    @Serializable @SerialName("player-counters") data class PlayerCountersPut(
        val players: PlayerFilter, val kind: CounterKind? = null, val causedBy: ObjectFilter? = null,
    ) : EventPattern
    @Serializable @SerialName("tap") data class Tap(val objects: ObjectFilter, val forMana: Boolean? = null) : EventPattern
    @Serializable @SerialName("untap") data class Untap(val objects: ObjectFilter) : EventPattern
    /** A creature attacks; [alone] for "attacks alone"; [target] narrows what it attacks. Roles object (the attacker),
     * defender (a planeswalker or battle attacked); [Players.EventPlayer] is the player attacked. */
    @Serializable @SerialName("attack") data class Attack(
        val attacker: ObjectFilter, val alone: Boolean = false, val target: RecipientFilter? = null,
        // gtc chunk 01
        /** Only when at least this many other creatures are declared as attackers with it ("Whenever this creature and at
         * least two other creatures attack", battalion, an ability word, CR 207.2c); 0: any number. */
        val withOthers: Int = 0,
    ) : EventPattern
    /** "Whenever [blocker] blocks": once per combat even if it blocks several; [perAttacker] for "blocks a creature",
     * once per attacker blocked (CR 509.3a, b). Roles object (the blocker), other combatant (the attacker). */
    @Serializable @SerialName("block") data class Block(
        val blocker: ObjectFilter, val attacker: ObjectFilter, val perAttacker: Boolean = false,
    ) : EventPattern
    /** "Whenever [attacker] becomes blocked": once per combat; [perBlocker] for "becomes blocked by a creature", once per
     * blocker matching [blocker] (CR 509.3d). Roles object (the attacker), other combatant (the blocker). */
    @Serializable @SerialName("becomes-blocked") data class BecomesBlocked(
        val attacker: ObjectFilter, val blocker: ObjectFilter, val perBlocker: Boolean = false,
    ) : EventPattern
    /** An attacker matching [attacker] isn't blocked (CR 509.1h); [target] narrows what it attacks ("attacks a player and isn't
     * blocked"). Role object (the attacker); [Players.EventPlayer] is the player attacked. */
    @Serializable @SerialName("unblocked") data class AttacksUnblocked(
        val attacker: ObjectFilter,
        // dgm
        val target: RecipientFilter? = null,
    ) : EventPattern
    /** An object or player becomes the target of a spell or ability matching [by] (heroic, ward). Roles object,
     * source (the spell or ability). */
    @Serializable @SerialName("becomes-target") data class BecomesTarget(val objects: ObjectFilter, val by: ObjectFilter) : EventPattern
    @Serializable @SerialName("cycle") data class Cycle(val cards: ObjectFilter, val players: PlayerFilter) : EventPattern
    @Serializable @SerialName("create-token") data class CreateToken(val tokens: ObjectFilter) : EventPattern
    @Serializable @SerialName("transform") data class Transform(val objects: ObjectFilter) : EventPattern
    @Serializable @SerialName("turned-face-up") data class TurnedFaceUp(val objects: ObjectFilter) : EventPattern
    @Serializable @SerialName("search") data class Search(val players: PlayerFilter) : EventPattern
    @Serializable @SerialName("countered") data class Countered(val objects: ObjectFilter) : EventPattern
    @Serializable @SerialName("destroyed") data class Destroyed(val objects: ObjectFilter) : EventPattern
    /** A step begins ("at the beginning of your upkeep"); [whose] is relative to the ability's controller. */
    @Serializable @SerialName("step-begins") data class StepBegins(val step: TurnStep, val whose: PlayerFilter) : EventPattern
    @Serializable @SerialName("step-ends") data class StepEnds(val step: TurnStep, val whose: PlayerFilter) : EventPattern
    @Serializable @SerialName("day-night") data class DayNightChanges(val toNight: Boolean? = null) : EventPattern
    /** A spell matching [spell] is copied by a player matching [by] ("whenever you copy a spell"). */
    @Serializable @SerialName("copy") data class Copy(val spell: ObjectFilter, val by: PlayerFilter) : EventPattern
    @Serializable @SerialName("scry") data class Scry(val players: PlayerFilter) : EventPattern
    @Serializable @SerialName("surveil") data class Surveil(val players: PlayerFilter) : EventPattern
    /** A player commits a crime: targets an opponent or their objects (CR 700.13). */
    @Serializable @SerialName("crime") data class CommitCrime(val players: PlayerFilter) : EventPattern
    /** A player plays a land (a special action, CR 116.2a, 305.1). */
    @Serializable @SerialName("play-land") data class PlayLand(val lands: ObjectFilter, val players: PlayerFilter) : EventPattern
    /** An Aura, Equipment or Fortification matching [objects] becomes attached to something matching [to]. Role object
     * (the attachment). */
    @Serializable @SerialName("attach") data class Attach(val objects: ObjectFilter, val to: ObjectFilter) : EventPattern
    @Serializable @SerialName("roll-die") data class RollDie(val players: PlayerFilter) : EventPattern
    @Serializable @SerialName("flip-coin") data class FlipCoin(val players: PlayerFilter) : EventPattern
    /** A Vehicle matching [objects] becomes crewed (CR 702.122). Roles object (the Vehicle), other combatant unused. */
    @Serializable @SerialName("crewed") data class Crewed(val objects: ObjectFilter) : EventPattern
    /** A Mount matching [objects] becomes saddled (CR 702.171). */
    @Serializable @SerialName("saddled") data class Saddled(val objects: ObjectFilter) : EventPattern
    /** A player expends [amount]: reaches [amount] total mana spent to cast spells this turn (CR 700.14, "Whenever you expend 4"). */
    @Serializable @SerialName("expend") data class Expend(val amount: Int, val players: PlayerFilter) : EventPattern
    /** A player gives a gift: an instant or sorcery spell they control whose gift was promised resolves, or the gift
     * trigger of a permanent they control resolves (CR 702.174c). */
    @Serializable @SerialName("give-gift") data class GiveGift(val players: PlayerFilter) : EventPattern
    /** A Class matching [objects] becomes level [level] (CR 716.2a, "When this Class becomes level 2"). */
    @Serializable @SerialName("class-level") data class BecomesLevel(val objects: ObjectFilter, val level: Int) : EventPattern
    /** A player forages (CR 701.61a, "Whenever you forage"). */
    @Serializable @SerialName("forage") data class Forage(val players: PlayerFilter) : EventPattern
    /** A permanent matching [objects] is given an unlocked designation (CR 709.5h), as it enters or later; [thisDoor]:
     * only the half the triggered ability is printed on ("When you unlock this door"); [fully]: only when it then has
     * both ("whenever you fully unlock a Room", CR 709.5i). Role object (the permanent); [Players.EventPlayer] is its
     * controller, the player who unlocked it. */
    @Serializable @SerialName("unlock") data class Unlock(
        val objects: ObjectFilter, val thisDoor: Boolean = false, val fully: Boolean = false,
    ) : EventPattern
    /** A player matching [players] manifests dread (CR 701.62b), once the whole action is done. Roles object (the
     * manifested permanent, if any) and rest (the cards put into the graveyard). */
    @Serializable @SerialName("manifest-dread") data class ManifestDread(val players: PlayerFilter) : EventPattern
}

/** Who received damage or is attacked: objects matching [objects], players matching [players]. Both null = any. */
@Serializable
data class RecipientFilter(val objects: ObjectFilter? = null, val players: PlayerFilter? = null)

/** What a triggered ability waits for (CR 603). */
@Serializable
sealed interface Trigger {
    /** "When/Whenever [events]": one ability with one or more trigger events ("enters or attacks"), so it stays one
     * ability for "counter target ability" or "loses this ability". [oneOrMore]: triggers once for a batch of
     * simultaneous events ("one or more creatures"; CR 603.2c is one trigger per event, the batching comes from the
     * wording), and only when the batch has at least [atLeast]
     * ("whenever you attack with three or more creatures"). [nthThisTurn]: only the nth such event for that player this
     * turn ("your second spell each turn", "for the first time each turn"), counted within [during] when given ("the
     * first card you draw during each of your draw steps"). */
    @Serializable @SerialName("on") data class On(
        val events: List<EventPattern>, val oneOrMore: Boolean = false, val atLeast: Int? = null,
        /** With [oneOrMore]: one batch per player the events are about ("deal combat damage to a player": one trigger per
         * player dealt damage, each with its own "that player" and amount). */
        val batchPerPlayer: Boolean = false,
        val nthThisTurn: Int? = null, val during: TurnStep? = null,
        // hob
        /** With [oneOrMore]: only a batch whose objects' [Total.property] adds up to at least [Total.atLeast] ("whenever you
         * attack with creatures with total power 12 or greater"); with [nthThisTurn], only such batches count. */
        val batchTotal: Total? = null,
    ) : Trigger {
        constructor(event: EventPattern, oneOrMore: Boolean = false) : this(listOf(event), oneOrMore)
    }
    /** A state trigger (CR 603.8): triggers whenever [condition] becomes true, once until it resolves. */
    @Serializable @SerialName("state") data class State(val condition: Condition) : Trigger
    /** Saga chapter abilities (CR 714.2): lore counters reach one of [chapters]. "I, II —" is written once but is one
     * chapter ability per listed chapter (CR 714.2c). */
    @Serializable @SerialName("chapter") data class Chapter(val chapters: List<Int>) : Trigger
}
