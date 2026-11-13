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
        // dft
        /** Only abilities with this label ("whenever you activate an exhaust ability", CR 702.177a). */
        val label: AbilityLabel? = null,
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
        // msh
        /** Only the event that puts the [reaching]th counter of [kind] on the object: it had fewer before and at least that many
         * after ("When the fourth plan counter is put on this enchantment"). */
        val reaching: Int? = null,
        // fdn
        /** Only counters an effect puts ("If an effect would put one or more counters"), not those put as a cost (a loyalty ability's
         * "+1", CR 118.3, 606.4) or by a turn-based action (a Saga's lore counter, CR 714.3b). */
        val byEffect: Boolean = false,
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
    /** An object matching [objects] becomes tapped; [forMana]: only (true) or never (false) for mana; [forCost]: only when
     * tapped to pay that keyword's cost ("becomes tapped to pay a teamwork cost", CR 702.194a; dft: "whenever this creature saddles a Mount
     * or crews a Vehicle", CR 702.171c, 702.122b). Role object (the tapped one); with [forCost] of a crew or saddle ability, role source is the
     * permanent whose ability it paid for ("that Mount or Vehicle"). */
    @Serializable @SerialName("tap") data class Tap(
        val objects: ObjectFilter, val forMana: Boolean? = null,
        // msh
        @Serializable(with = KeywordKindSerializer::class) val forCost: KeywordKind? = null,
        // fin
        /** With [forMana]: only when the mana produced includes this ("Whenever you tap a land for {C}", CR 106.12). */
        val producing: ManaCost? = null,
    ) : EventPattern
    @Serializable @SerialName("untap") data class Untap(val objects: ObjectFilter) : EventPattern
    // ltr chunk 13
    /** The final chapter ability of a Saga matching [sagas] resolves (CR 714.2c, the chapter ability with the highest number). Role object:
     * the Saga. */
    @Serializable @SerialName("final-chapter-resolves") data class FinalChapterResolves(val sagas: ObjectFilter) : EventPattern
    // ltr chunk 12
    /** A permanent matching [objects] phases in (CR 702.26c). Role object: the permanent. */
    @Serializable @SerialName("phase-in") data class PhaseIn(val objects: ObjectFilter) : EventPattern
    /** A creature attacks; [alone] for "attacks alone"; [target] narrows what it attacks. Roles object (the attacker),
     * defender (a planeswalker or battle attacked); [Players.EventPlayer] is the player attacked. */
    @Serializable @SerialName("attack") data class Attack(
        val attacker: ObjectFilter, val alone: Boolean = false, val target: RecipientFilter? = null,
        // gtc chunk 01
        /** Only when at least this many other creatures are declared as attackers with it ("Whenever this creature and at
         * least two other creatures attack", battalion, an ability word, CR 207.2c); 0: any number. */
        val withOthers: Int = 0,
        // mom
        /** What the other attackers counted by [withOthers] must be ("this token and at least one other creature token attack"); null:
         * any creatures. */
        val others: ObjectFilter? = null,
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
    @Serializable @SerialName("becomes-target") data class BecomesTarget(
        val objects: ObjectFilter, val by: ObjectFilter,
        // msh
        /** Players matching this becoming the target count too ("a player or permanent becomes the target of …"); null: none. */
        val players: PlayerFilter? = null,
    ) : EventPattern
    @Serializable @SerialName("cycle") data class Cycle(val cards: ObjectFilter, val players: PlayerFilter) : EventPattern
    @Serializable @SerialName("create-token") data class CreateToken(val tokens: ObjectFilter) : EventPattern
    @Serializable @SerialName("transform") data class Transform(val objects: ObjectFilter) : EventPattern
    // fin
    /** A player matching [players] loses the game (CR 104.3; "When the chosen player loses the game, you win the game"). */
    @Serializable @SerialName("lose-game") data class LoseGame(val players: PlayerFilter) : EventPattern
    /** Control of a permanent matching [objects] passes from a player matching [from] to one matching [to] (null: any; CR 613.1b): "Whenever an
     * opponent gains control of a permanent from you", "When you lose control of that Equipment". */
    @Serializable @SerialName("control-change") data class ControlChange(val objects: ObjectFilter, val from: PlayerFilter?, val to: PlayerFilter?) : EventPattern
    /** A player matching [players] mills cards (CR 701.17); the event's amount is how many ("If an opponent would mill one or more cards"). */
    @Serializable @SerialName("mill") data class Mill(val players: PlayerFilter) : EventPattern
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
    /** A player commits a crime: targets an opponent or their objects (CR 700.13). */
    @Serializable @SerialName("crime") data class CommitCrime(val players: PlayerFilter) : EventPattern
    /** A player plays a land (a special action, CR 116.2a, 305.1). */
    @Serializable @SerialName("play-land") data class PlayLand(
        val lands: ObjectFilter, val players: PlayerFilter,
        // spm
        /** Only a land played from this zone ("whenever you play a land from exile"); [notFrom]: from any zone but this one ("from
         * anywhere other than your hand"). */
        val from: Zone? = null, val notFrom: Zone? = null,
    ) : EventPattern
    /** An Aura, Equipment or Fortification matching [objects] becomes attached to something matching [to]. Role object
     * (the attachment). */
    @Serializable @SerialName("attach") data class Attach(val objects: ObjectFilter, val to: ObjectFilter) : EventPattern
    @Serializable @SerialName("roll-die") data class RollDie(val players: PlayerFilter) : EventPattern
    /** A player matching [players] flips a coin (CR 705); mh2: [won], only a flip that player wins ("whenever you win a coin flip", CR 705.2). */
    @Serializable @SerialName("flip-coin") data class FlipCoin(val players: PlayerFilter, val won: Boolean = false) : EventPattern
    /** A Vehicle matching [objects] becomes crewed (CR 702.122). Roles object (the Vehicle), other combatant unused. */
    @Serializable @SerialName("crewed") data class Crewed(val objects: ObjectFilter) : EventPattern
    /** A player expends [amount]: reaches [amount] total mana spent to cast spells this turn (CR 700.14, "Whenever you expend 4"). */
    @Serializable @SerialName("expend") data class Expend(val amount: Int, val players: PlayerFilter) : EventPattern
    /** A Class matching [objects] becomes level [level] (CR 716.2a, "When this Class becomes level 2"). */
    @Serializable @SerialName("class-level") data class BecomesLevel(val objects: ObjectFilter, val level: Int) : EventPattern
    /** A creature matching [objects] mutates (CR 702.140d): a mutating creature spell merged with it as it resolved. Role object: the
     * merged permanent. */
    @Serializable @SerialName("mutates") data class Mutates(val objects: ObjectFilter) : EventPattern
    /** [objects] enlists a creature (CR 702.154c): roles object (the attacker) and other combatant (the creature it tapped). */
    @Serializable @SerialName("enlists") data class Enlists(val objects: ObjectFilter) : EventPattern
    /** "Whenever the Ring tempts you" (CR 701.54d): once the player has chosen their Ring-bearer, or couldn't. Role object: the
     * creature chosen. */
    @Serializable @SerialName("ring-tempts") data class RingTempts(
        val players: PlayerFilter,
        /** "Whenever you choose a creature as your Ring-bearer": only when a creature was chosen. */
        val chosen: Boolean = false,
    ) : EventPattern
    /** A player completes a dungeon (CR 309.7, "Whenever you complete a dungeon"). */
    @Serializable @SerialName("completes-dungeon") data class CompletesDungeon(val players: PlayerFilter) : EventPattern
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
    // msh
    /** A permanent matching [objects] would connive or connives (CR 701.50): what a replacement replaces ("If a creature you
     * control would connive, instead …"). Role object (the conniving permanent); [Players.EventPlayer] is its controller. */
    @Serializable @SerialName("connive") data class Connive(val objects: ObjectFilter) : EventPattern
    // tla
    /** A triggered ability of an object matching [source] triggers on an event matching [cause] ("Whenever a creature you control
     * attacking causes a triggered ability of that creature to trigger", CR 603.2). Roles object (the triggered ability, once it is
     * put on the Stack) and source (its source). */
    @Serializable @SerialName("ability-triggers") data class AbilityTriggers(val source: ObjectFilter, val cause: EventPattern) : EventPattern
    // mh2
    /** A creature matching [exploiter] exploits a creature: the controller of its exploit ability sacrifices a creature as that ability
     * resolves (CR 702.110b). Roles object (the sacrificed creature, as it last existed) and source (the exploiting creature). */
    @Serializable @SerialName("exploits") data class Exploits(val exploiter: ObjectFilter) : EventPattern
    /** A player matching [players] (null: any) does the keyword action [action], with an object matching [objects] (null: with or
     * without one): "Whenever you proliferate", "Whenever a creature you control explores", "Whenever you exert a creature". One pattern
     * for every verb, so a new verb is a new [KeywordActionKind], not a new event type. When an action counts as done is its own rule: for
     * most, once its whole process is done even if some of it was impossible (CR 701.22d, 701.25d, 701.44b, 701.50f, 701.57b). Role
     * object (the objects the action names, as they last existed if they have left, CR 701.44c, 701.50b); [Players.EventPlayer] is
     * who did it (a permanent's controller, for what a permanent does); for scry and surveil, the event's amount is the cards looked at, which a short library makes fewer than N ("where X is the number of cards looked at while scrying this way"). */
    @Serializable @SerialName("keyword-action-done") data class KeywordActionDone(
        val action: KeywordActionKind, val players: PlayerFilter? = null, val objects: ObjectFilter? = null,
    ) : EventPattern
    /** An object matching [objects] gains the designation [mark], which it didn't have: "Whenever a creature you control becomes monstrous"
     * (CR 701.37b, 702.112b, 701.60a, 722.3a, 702.171b). One that already has it can't gain it again (CR 701.60d, 722.3a). One pattern for
     * every mark, so a new mark needs no new event type. Role object (the marked object); [Players.EventPlayer] is its controller. */
    @Serializable @SerialName("got-mark") data class GotMark(val mark: ObjectMark, val objects: ObjectFilter) : EventPattern
}

/**
 * A verb a [EventPattern.KeywordActionDone] can wait for: the keyword actions of CR 701 (every [KeywordAction] and those with their own
 * effect type, [Scry], [Surveil]), and what the rules name like one: foretelling a card (CR 702.143c), firebending (CR 702.189b), giving a
 * gift (CR 702.174c). Parameters such as the N of "scry N" aren't part of the verb.
 */
@Serializable
sealed interface KeywordActionKind {
    @Serializable @SerialName("adapt") data object Adapt : KeywordActionKind
    @Serializable @SerialName("airbend") data object Airbend : KeywordActionKind
    @Serializable @SerialName("amass") data object Amass : KeywordActionKind
    @Serializable @SerialName("behold") data object Behold : KeywordActionKind
    @Serializable @SerialName("blight") data object Blight : KeywordActionKind
    @Serializable @SerialName("bolster") data object Bolster : KeywordActionKind
    @Serializable @SerialName("cloak") data object Cloak : KeywordActionKind
    @Serializable @SerialName("collect-evidence") data object CollectEvidence : KeywordActionKind
    @Serializable @SerialName("connive") data object Connive : KeywordActionKind
    @Serializable @SerialName("detain") data object Detain : KeywordActionKind
    @Serializable @SerialName("discover") data object Discover : KeywordActionKind
    @Serializable @SerialName("double") data object Double : KeywordActionKind
    @Serializable @SerialName("earthbend") data object Earthbend : KeywordActionKind
    @Serializable @SerialName("empower-jace") data object EmpowerJace : KeywordActionKind
    @Serializable @SerialName("endure") data object Endure : KeywordActionKind
    @Serializable @SerialName("exert") data object Exert : KeywordActionKind
    @Serializable @SerialName("explore") data object Explore : KeywordActionKind
    @Serializable @SerialName("firebend") data object Firebend : KeywordActionKind
    @Serializable @SerialName("forage") data object Forage : KeywordActionKind
    @Serializable @SerialName("foretell") data object Foretell : KeywordActionKind
    @Serializable @SerialName("give-gift") data object GiveGift : KeywordActionKind
    @Serializable @SerialName("goad") data object Goad : KeywordActionKind
    /** mom chunk 03: CR 701.53. */
    @Serializable @SerialName("incubate") data object Incubate : KeywordActionKind
    @Serializable @SerialName("investigate") data object Investigate : KeywordActionKind
    @Serializable @SerialName("learn") data object Learn : KeywordActionKind
    @Serializable @SerialName("manifest") data object Manifest : KeywordActionKind
    @Serializable @SerialName("manifest-dread") data object ManifestDread : KeywordActionKind
    @Serializable @SerialName("meld") data object Meld : KeywordActionKind
    @Serializable @SerialName("monstrosity") data object Monstrosity : KeywordActionKind
    @Serializable @SerialName("populate") data object Populate : KeywordActionKind
    @Serializable @SerialName("proliferate") data object Proliferate : KeywordActionKind
    @Serializable @SerialName("recruit") data object Recruit : KeywordActionKind
    @Serializable @SerialName("regenerate") data object Regenerate : KeywordActionKind
    @Serializable @SerialName("ring-tempts") data object TheRingTemptsYou : KeywordActionKind
    @Serializable @SerialName("scry") data object Scry : KeywordActionKind
    @Serializable @SerialName("support") data object Support : KeywordActionKind
    @Serializable @SerialName("surveil") data object Surveil : KeywordActionKind
    @Serializable @SerialName("time-travel") data object TimeTravel : KeywordActionKind
    @Serializable @SerialName("venture") data object Venture : KeywordActionKind
    @Serializable @SerialName("waterbend") data object Waterbend : KeywordActionKind
}

/** The verb of this action, whatever its parameters. */
val KeywordAction.kind: KeywordActionKind get() = when (this) {
    is KeywordAction.Adapt -> KeywordActionKind.Adapt
    is KeywordAction.Airbend -> KeywordActionKind.Airbend
    is KeywordAction.Amass -> KeywordActionKind.Amass
    is KeywordAction.Behold -> KeywordActionKind.Behold
    is KeywordAction.Blight -> KeywordActionKind.Blight
    is KeywordAction.Bolster -> KeywordActionKind.Bolster
    is KeywordAction.Cloak -> KeywordActionKind.Cloak
    is KeywordAction.CollectEvidence -> KeywordActionKind.CollectEvidence
    is KeywordAction.Connive -> KeywordActionKind.Connive
    is KeywordAction.Detain -> KeywordActionKind.Detain
    is KeywordAction.Discover -> KeywordActionKind.Discover
    is KeywordAction.Double -> KeywordActionKind.Double
    is KeywordAction.Earthbend -> KeywordActionKind.Earthbend
    is KeywordAction.EmpowerJace -> KeywordActionKind.EmpowerJace
    is KeywordAction.Endure -> KeywordActionKind.Endure
    is KeywordAction.Exert -> KeywordActionKind.Exert
    is KeywordAction.Explore -> KeywordActionKind.Explore
    KeywordAction.Forage -> KeywordActionKind.Forage
    KeywordAction.Foretell -> KeywordActionKind.Foretell
    is KeywordAction.Goad -> KeywordActionKind.Goad
    is KeywordAction.Incubate -> KeywordActionKind.Incubate
    is KeywordAction.Investigate -> KeywordActionKind.Investigate
    KeywordAction.Learn -> KeywordActionKind.Learn
    is KeywordAction.Manifest -> KeywordActionKind.Manifest
    is KeywordAction.ManifestDread -> KeywordActionKind.ManifestDread
    is KeywordAction.Meld -> KeywordActionKind.Meld
    is KeywordAction.Monstrosity -> KeywordActionKind.Monstrosity
    KeywordAction.Populate -> KeywordActionKind.Populate
    KeywordAction.Proliferate -> KeywordActionKind.Proliferate
    KeywordAction.Recruit -> KeywordActionKind.Recruit
    is KeywordAction.Regenerate -> KeywordActionKind.Regenerate
    is KeywordAction.Support -> KeywordActionKind.Support
    KeywordAction.TheRingTemptsYou -> KeywordActionKind.TheRingTemptsYou
    KeywordAction.TimeTravel -> KeywordActionKind.TimeTravel
    KeywordAction.Venture, is KeywordAction.VentureInto -> KeywordActionKind.Venture
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
         * player dealt damage, each with its own "that player" and amount); damage to an object, one per object ("to a player or battle",
         * CR 310.6). */
        val batchPerPlayer: Boolean = false,
        val nthThisTurn: Int? = null, val during: TurnStep? = null,
        // hob
        /** With [oneOrMore]: only a batch whose objects' [Total.property] adds up to at least [Total.atLeast] ("whenever you
         * attack with creatures with total power 12 or greater"); with [nthThisTurn], only such batches count. */
        val batchTotal: Total? = null,
        // spm
        /** With [nthThisTurn]: each of the first [nthThisTurn] such events, not only the last of them ("whenever you draw your first or
         * second card each turn"). */
        val throughNth: Boolean = false,
    ) : Trigger {
        constructor(event: EventPattern, oneOrMore: Boolean = false) : this(listOf(event), oneOrMore)
    }
    /** A state trigger (CR 603.8): triggers whenever [condition] becomes true, once until it resolves. */
    @Serializable @SerialName("state") data class State(val condition: Condition) : Trigger
    /** Saga chapter abilities (CR 714.2): lore counters reach one of [chapters]. "I, II —" is written once but is one
     * chapter ability per listed chapter (CR 714.2c). */
    @Serializable @SerialName("chapter") data class Chapter(val chapters: List<Int>) : Trigger
}
