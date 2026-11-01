package arcanum.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A one-shot effect (CR 610): something done once when a spell or ability resolves.
 *
 * Effects that produce objects name them for later steps ("the exiled card", "cards drawn this way"). An effect that
 * doesn't change the objects' identity has one `bind` (create token, copy, search, reveal, look). An effect that moves
 * objects between zones gives each object a new identity (CR 400.7): `preBind` names them as they were (their last
 * known information, readable but not acted on, CR 608.2h) and `postBind` as they are after the move. Continuous rules are
 * [Mechanic]s; [ApplyMechanics] is the bridge that creates a continuous effect for a [Duration] (CR 611.2).
 * Keyword actions that are shorthand for other effects (investigate, amass, …) are [KeywordAction]s, expanded by the
 * shared table like keywords.
 */
@Serializable
sealed interface Effect {
    /** [source] deals [amount] damage to [to] (CR 120). [to] may hold objects and players. */
    @Serializable @SerialName("damage") data class DealDamage(
        val to: Recipients, val amount: Value, val source: Objects = Objects.Self,
        /** "This damage can't be prevented" (CR 615.12). */
        val cantBePrevented: Boolean = false,
        /** Names the damage actually dealt, after prevention and replacement ("the damage dealt this way"). */
        val bind: String? = null,
    ) : Effect
    /** [first] and [second] each deal damage equal to their power to the other (CR 701.14). */
    @Serializable @SerialName("fight") data class Fight(val first: Objects, val second: Objects) : Effect

    @Serializable @SerialName("destroy") data class Destroy(
        val objects: Objects, val noRegeneration: Boolean = false, val preBind: String? = null, val postBind: String? = null,
    ) : Effect
    @Serializable @SerialName("sacrifice") data class Sacrifice(val objects: Objects, val preBind: String? = null, val postBind: String? = null) : Effect
    /** Move [objects] to another zone (exile, return to hand, put onto the battlefield, put into a library, …).
     * [untilLeaves]: "exile … until ~ leaves the battlefield": when that object leaves, the moved cards return at once
     * without using the Stack, and if it has already left, nothing is moved (CR 610.3). */
    @Serializable @SerialName("move") data class Move(
        val objects: Objects, val to: Destination, val untilLeaves: Objects? = null,
        val preBind: String? = null, val postBind: String? = null,
    ) : Effect
    @Serializable @SerialName("draw") data class Draw(
        val players: Players, val count: Value = Value.Fixed(1), val preBind: String? = null, val postBind: String? = null,
    ) : Effect
    /** [players] discard [count] cards matching [filter] of their own choice (or at [random]); or exactly [objects]. When
     * someone else chooses ("You choose a nonland card from it. That player discards that card") it is a [Step.Choose]
     * then `Discard(objects = …)`. */
    @Serializable @SerialName("discard") data class Discard(
        val players: Players, val count: Value = Value.Fixed(1), val filter: ObjectFilter = ObjectFilter.Any, val objects: Objects? = null,
        val random: Boolean = false, val preBind: String? = null, val postBind: String? = null,
    ) : Effect
    @Serializable @SerialName("mill") data class Mill(val players: Players, val count: Value, val preBind: String? = null, val postBind: String? = null) : Effect
    /** [bind] names the cards put on the bottom (scry) or into the graveyard (surveil). */
    @Serializable @SerialName("scry") data class Scry(val players: Players, val count: Value, val bind: String? = null) : Effect
    @Serializable @SerialName("surveil") data class Surveil(val players: Players, val count: Value, val bind: String? = null) : Effect
    /** Search [library]'s [zones] (their library, "and/or graveyard") for up to [max] cards matching [filter], with
     * [differentNames] if they must all have different names; the found cards are named [bind] and moved by a following
     * step. [reveal] shows them (CR 701.23). */
    @Serializable @SerialName("search") data class Search(
        val library: Players, val filter: ObjectFilter, val max: Value = Value.Fixed(1), val reveal: Boolean = false,
        val zones: List<Zone> = listOf(Zone.Library), val differentNames: Boolean = false, val bind: String? = null,
    ) : Effect
    /** Shuffle [library]'s library, leaving out [except] (cards just found by a search: "then shuffle and put that card
     * on top", CR 701.24b); the cards left out keep their identity. */
    @Serializable @SerialName("shuffle") data class Shuffle(val library: Players, val except: Objects? = null) : Effect
    /** [chooser] puts [objects] back in any order where they are ("put them back in any order"): not a zone change,
     * they keep their identity (unless they were revealed, CR 701.20d). */
    @Serializable @SerialName("reorder") data class Reorder(val objects: Objects, val chooser: Players = Players.You) : Effect
    @Serializable @SerialName("reveal") data class Reveal(val objects: Objects, val bind: String? = null) : Effect
    /** [players] look at [objects] (hidden from others); [bind] names them for "from among them". */
    @Serializable @SerialName("look") data class LookAt(val objects: Objects, val players: Players = Players.You, val bind: String? = null) : Effect

    @Serializable @SerialName("gain-life") data class GainLife(val players: Players, val amount: Value, val bind: String? = null) : Effect
    @Serializable @SerialName("lose-life") data class LoseLife(val players: Players, val amount: Value, val bind: String? = null) : Effect
    @Serializable @SerialName("set-life") data class SetLife(val players: Players, val amount: Value) : Effect

    /** Put [amount] counters of [kind] on each of [objects], or, [distributed], [amount] in total distributed among them
     * as the controller chooses, at least one each ("distribute three +1/+1 counters among any number of creatures you
     * control", CR 608.2d). */
    @Serializable @SerialName("put-counters") data class PutCounters(
        val objects: Objects, val kind: CounterKind, val amount: Value, val distributed: Boolean = false,
    ) : Effect
    @Serializable @SerialName("remove-counters") data class RemoveCounters(val objects: Objects, val kind: CounterKind?, val amount: Value) : Effect
    @Serializable @SerialName("player-counters") data class GivePlayerCounters(val players: Players, val kind: CounterKind, val amount: Value) : Effect

    @Serializable @SerialName("tap") data class Tap(val objects: Objects) : Effect
    @Serializable @SerialName("untap") data class Untap(val objects: Objects) : Effect
    /** Attach an Aura, Equipment or Fortification to an object or player (CR 701.3). */
    @Serializable @SerialName("attach") data class Attach(val objects: Objects, val to: Recipients) : Effect
    @Serializable @SerialName("transform") data class Transform(val objects: Objects) : Effect
    /** Flip a flip card (CR 710). */
    @Serializable @SerialName("flip") data class Flip(val objects: Objects) : Effect
    /** [players] skip their next turn. */
    @Serializable @SerialName("skip-next-turn") data class SkipNextTurn(val players: Players = Players.You) : Effect
    @Serializable @SerialName("turn-face-up") data class TurnFaceUp(val objects: Objects) : Effect
    @Serializable @SerialName("turn-face-down") data class TurnFaceDown(val objects: Objects) : Effect
    @Serializable @SerialName("phase-out") data class PhaseOut(val objects: Objects) : Effect
    /** Put [mark] on [objects], or take it off ([remove], "is no longer suspected"). Saddled ends at end of turn or when
     * it leaves the battlefield (CR 702.171b). */
    @Serializable @SerialName("mark") data class Mark(val objects: Objects, val mark: ObjectMark, val remove: Boolean = false) : Effect
    @Serializable @SerialName("designation") data class GiveDesignation(val players: Players, val designation: Designation) : Effect

    /** Create [count] tokens, as one event; [attacking]: what they attack (CR 508.4); [attachedTo]: created attached
     * ("a Role token attached to target creature", CR 303.4f). */
    @Serializable @SerialName("create-token") data class CreateToken(
        val token: TokenSpec, val count: Value = Value.Fixed(1), val controller: Players = Players.You,
        val tapped: Boolean = false, val attacking: AttackTarget? = null, val attachedTo: Recipients? = null,
        val bind: String? = null,
    ) : Effect
    /** Copy a spell or ability on the Stack (CR 707.10); [newTargets] lets the copy's controller choose new ones. */
    @Serializable @SerialName("copy-spell") data class CopySpell(
        val objects: Objects, val controller: Players = Players.You, val newTargets: Boolean = true, val bind: String? = null,
    ) : Effect
    /** Counter a spell or ability (CR 701.6); "unless its controller pays" is a [Step.Unless] around it. */
    @Serializable @SerialName("counter") data class Counter(val objects: Objects) : Effect
    @Serializable @SerialName("add-mana") data class AddMana(val mana: ManaProduction, val players: Players = Players.You) : Effect

    /** Create a continuous effect (CR 611.2): [mechanics] apply to [affects] for [duration]. It becomes an effect
     * object. Each mechanic that changes characteristics or control locks in its affected objects when created; rule
     * changes keep applying to new objects; numbers, choices ("the colour of your choice") and named objects are fixed
     * at creation (CR 611.2c, 608.2h). Gaining control is [Mechanic.ControlledBy] here. [uses]: a replacement shield that
     * ends after applying that many times ("the next time …"); the effect object counts down. Giant Growth: +3/+3 to
     * the target until end of turn. */
    @Serializable @SerialName("apply") data class ApplyMechanics(
        /** Null for mechanics with their own scope (prevention, replacement, cost change, "damage can't be prevented":
         * Fog, "spells cost {1} more this turn"); they change the rules of the game (CR 611.2c). */
        val mechanics: List<Mechanic>, val affects: Affected?, val duration: Duration, val uses: Int? = null,
    ) : Effect
    /** "Prevent the next [amount] damage that would be dealt to [to] this turn" (CR 615.7): a shield effect object
     * whose amount goes down by the damage it prevents. [from] limits the sources ("by a source of your choice"). */
    @Serializable @SerialName("prevent-damage") data class PreventDamage(
        val to: Recipients, val amount: Value, val from: ObjectFilter = ObjectFilter.Any, val duration: Duration = Duration.EndOfTurn,
    ) : Effect
    /** Remove [objects] from combat (CR 506.4). */
    @Serializable @SerialName("remove-from-combat") data class RemoveFromCombat(val objects: Objects) : Effect
    /** Remove all damage marked on [objects]. */
    @Serializable @SerialName("remove-damage") data class RemoveAllDamage(val objects: Objects) : Effect
    /** "At the beginning of the next end step, …" (CR 603.7): a trigger created now, an effect object. It triggers once,
     * or for its [duration] ("this turn, whenever …", CR 603.7b). The objects it refers to are fixed when it is created
     * (CR 603.7c), as are announced values and earlier binds; values it computes are read when it resolves (608.2h). */
    @Serializable @SerialName("delayed-trigger") data class CreateDelayedTrigger(
        val trigger: Trigger, val instructions: Instructions, val duration: Duration? = null,
    ) : Effect
    /** Roll a [sides]-sided die [count] times; [bind] names the result (read with [Value.Bound]). Answered by the host
     * (D25). */
    @Serializable @SerialName("roll-die") data class RollDie(val sides: Int, val count: Int = 1, val players: Players = Players.You, val bind: String? = null) : Effect
    /** Flip a coin; [bind] names whether it was won ([Condition.WonFlip]). Answered by the host (D25). */
    @Serializable @SerialName("flip-coin") data class FlipCoin(val players: Players = Players.You, val bind: String? = null) : Effect
    /** Exchange control of [first] and [second] (CR 701.12); nothing happens unless both can be exchanged (701.12a). */
    @Serializable @SerialName("exchange-control") data class ExchangeControl(val first: Objects, val second: Objects) : Effect
    /** Move [amount] counters of [kind] (any kind when null) from [from] onto [to]. */
    @Serializable @SerialName("move-counters") data class MoveCounters(val from: Objects, val to: Objects, val kind: CounterKind?, val amount: Value) : Effect
    /** "Change the target of target spell or ability" / "you may choose new targets for it" (CR 115.7). */
    @Serializable @SerialName("change-targets") data class ChangeTargets(val objects: Objects, val chooser: Players = Players.You, val optional: Boolean = true) : Effect
    /** "[separator] separates [objects] into two piles; [chooser] chooses one" (Fact or Fiction style); [chosenBind] and
     * [otherBind] name the piles. */
    @Serializable @SerialName("two-piles") data class SeparatePiles(
        val objects: Objects, val separator: Players, val chooser: Players, val chosenBind: String? = null, val otherBind: String? = null,
    ) : Effect
    /** Keep [objects] on the source as card memory under [key] (CR 607 linked abilities: "exiled with ~"). */
    @Serializable @SerialName("remember") data class Remember(val key: String, val objects: Objects) : Effect
    @Serializable @SerialName("forget") data class Forget(val key: String) : Effect

    @Serializable @SerialName("extra-turn") data class ExtraTurn(val players: Players = Players.You) : Effect
    /** An additional combat phase after this one, followed by an additional main phase when [followedByMain] (CR 500.8). */
    @Serializable @SerialName("extra-combat") data class ExtraCombat(val untapAttackers: Boolean = false, val followedByMain: Boolean = false) : Effect
    @Serializable @SerialName("exchange-life") data class ExchangeLife(val first: Players, val second: Players) : Effect
    /** [mechanics] apply to the spell [objects] and to the permanent it becomes (CR 400.7d): "that creature enters with
     * two additional +1/+1 counters", "it can't be countered". Inside [mechanics], [Objects.Affected] is the spell and the
     * permanent it becomes; values are fixed when this effect happens (CR 608.2h). Mana riders use the same idea
     * ([ManaProduction.spellGets]). */
    @Serializable @SerialName("spell-gets") data class SpellGets(val objects: Objects, val mechanics: List<Mechanic>) : Effect
    @Serializable @SerialName("win") data class WinGame(val players: Players = Players.You) : Effect
    @Serializable @SerialName("lose") data class LoseGame(val players: Players) : Effect
    /** Cast [objects] now ([how] play: a land is played instead, CR 305.1), without paying their mana cost or by paying
     * [cost]; [bind] names the resulting spell ("If that spell would be put into your graveyard, exile it instead"). */
    @Serializable @SerialName("cast") data class Cast(
        val objects: Objects, val how: PlayKind = PlayKind.Cast, val withoutPaying: Boolean = false, val cost: Cost? = null,
        val bind: String? = null,
    ) : Effect
    /** "When you do, …" (CR 603.12): a reflexive triggered ability, put on the Stack as its own item, targets chosen
     * then. Placed inside a [Step.If] on what was done. It triggers once per time the action is taken, but only once
     * for a cost paid several times (603.12a). */
    @Serializable @SerialName("reflexive-trigger") data class ReflexiveTrigger(val instructions: Instructions) : Effect
    /** "Reveal cards from the top of [library]'s library until you reveal [count] cards matching [filter]" (cascade,
     * discover too): [foundBind] names the matching cards, [restBind] the other cards turned over. [into]: each card is
     * put there as it is turned over ("exile cards from the top until you exile a nonland card"); null: revealed in
     * place ([reveal] false: looked at only by the player). */
    @Serializable @SerialName("reveal-until") data class RevealUntil(
        val filter: ObjectFilter, val library: Players = Players.You, val count: Value = Value.Fixed(1),
        val reveal: Boolean = true, val into: Zone? = null, val foundBind: String? = null, val restBind: String? = null,
    ) : Effect
    /** Create an emblem (CR 114) with [abilities] in the command zone of [players]; "you" in them is its owner, and the
     * names and choices they use are fixed when it is created. */
    @Serializable @SerialName("emblem") data class CreateEmblem(val abilities: List<Ability>, val players: Players = Players.You) : Effect
    /** A keyword action; [bind] names what it acted on ("the beheld card", "the evidence collected"). */
    @Serializable @SerialName("keyword-action") data class Action(val action: KeywordAction, val bind: String? = null) : Effect
}

/** Who or what receives damage or an attachment: objects, players, or both; [target] is everything chosen for that
 * target slot, objects and players ("any target"). */
@Serializable
data class Recipients(val objects: Objects? = null, val players: Players? = null, val target: String? = null)

/** Where [Effect.Move] puts things (CR 400.7): the owner's zone of that kind. */
@Serializable
data class Destination(
    val zone: Zone,
    /** Library position counted from 0 at the top ("third from the top" = 2); -1 = bottom. */
    val libraryPosition: Int? = null,
    val tapped: Boolean = false,
    val faceDown: Boolean = false,
    /** Onto the battlefield under this player's control; null = the player putting it there (CR 110.2a). "Under its
     * owner's control" is [Players.OwnerOf]. */
    val controller: Players? = null,
    /** Onto the battlefield attached to … (an Aura returned to the battlefield). */
    val attachedTo: Recipients? = null,
    /** Onto the battlefield transformed / with its back face up. */
    val transformed: Boolean = false,
    /** Put into the library in a random order (several cards). */
    val randomOrder: Boolean = false,
    /** Enters the battlefield with these counters ("return it with a -1/-1 counter on it", CR 122.6). */
    val counters: List<CountersToPut> = emptyList(),
    /** Onto the battlefield attacking ([attacking] = what it attacks; null = not attacking). Not declared as an
     * attacker, so "whenever attacks" doesn't trigger (CR 508.4). */
    val attacking: AttackTarget? = null,
)

/** [amount] counters of [kind]. */
@Serializable
data class CountersToPut(val kind: CounterKind, val amount: Value = Value.Fixed(1))

/** What token to create (CR 111). */
@Serializable
sealed interface TokenSpec {
    /** A token described by its characteristics ("a 1/1 white Soldier creature token with flying"). Its name, when the
     * text gives none, is its subtypes plus "Token" ("Soldier Token", CR 111.4); its colour is written as a colour
     * indicator. [power]/[toughness] set computed values ("an X/X Ooze token"), fixed when created and part of its
     * copiable values (CR 707.2). */
    @Serializable @SerialName("defined") data class Defined(val face: FaceDefinition, val power: Value? = null, val toughness: Value? = null) : TokenSpec
    /** A predefined token (CR 111.10): Treasure, Clue, Food, Blood, Map, Powerstone, … by name; the shared table
     * holds their definitions. */
    @Serializable @SerialName("predefined") data class Predefined(val name: String) : TokenSpec
    /** A token that's a copy of [objects] (CR 707), with the exceptions [except] (CR 707.9b). */
    @Serializable @SerialName("copy") data class CopyOf(val objects: Objects, val except: List<CopyException> = emptyList()) : TokenSpec
}

/** What something put onto the battlefield attacking attacks (CR 508.4). */
@Serializable
sealed interface AttackTarget {
    /** Its controller chooses a player, planeswalker or battle it could attack ("tapped and attacking"). */
    @Serializable @SerialName("chosen") data object Chosen : AttackTarget
    /** The same player, planeswalker or battle [objects] is attacking (ninjutsu, myriad: "attacking that player"). */
    @Serializable @SerialName("same-as") data class SameAs(val objects: Objects) : AttackTarget
    /** These ("attacking that opponent"). */
    @Serializable @SerialName("these") data class These(val recipients: Recipients) : AttackTarget
}

/** What [KeywordAction.Double] doubles. */
@Serializable
sealed interface Doubled {
    @Serializable @SerialName("counters") data class Counters(val objects: Objects, val kind: CounterKind?) : Doubled
    @Serializable @SerialName("life") data class Life(val players: Players) : Doubled
    @Serializable @SerialName("power") data class Power(val objects: Objects) : Doubled
    @Serializable @SerialName("mana") data class Mana(val players: Players) : Doubled
}

/** Keyword actions (CR 701) that are shorthand for other effects; the shared table expands them. Actions with their
 * own effect type above (destroy, scry, mill, fight, …) are not repeated here. */
@Serializable
sealed interface KeywordAction {
    @Serializable @SerialName("investigate") data class Investigate(val count: Value = Value.Fixed(1)) : KeywordAction
    @Serializable @SerialName("proliferate") data object Proliferate : KeywordAction
    @Serializable @SerialName("populate") data object Populate : KeywordAction
    @Serializable @SerialName("explore") data class Explore(val objects: Objects = Objects.Self) : KeywordAction
    @Serializable @SerialName("amass") data class Amass(val subtype: Subtype, val count: Value) : KeywordAction
    @Serializable @SerialName("connive") data class Connive(val objects: Objects = Objects.Self, val count: Value = Value.Fixed(1)) : KeywordAction
    @Serializable @SerialName("discover") data class Discover(val count: Value) : KeywordAction
    @Serializable @SerialName("venture") data object Venture : KeywordAction
    @Serializable @SerialName("manifest") data class Manifest(val count: Value = Value.Fixed(1)) : KeywordAction
    @Serializable @SerialName("monstrosity") data class Monstrosity(val count: Value) : KeywordAction
    @Serializable @SerialName("adapt") data class Adapt(val count: Value) : KeywordAction
    @Serializable @SerialName("goad") data class Goad(val objects: Objects) : KeywordAction
    @Serializable @SerialName("learn") data object Learn : KeywordAction
    /** Double counters, life, power, or mana (CR 701.10). */
    @Serializable @SerialName("double") data class Double(val what: Doubled) : KeywordAction
    @Serializable @SerialName("bolster") data class Bolster(val count: Value) : KeywordAction
    @Serializable @SerialName("support") data class Support(val count: Value) : KeywordAction
    @Serializable @SerialName("detain") data class Detain(val objects: Objects) : KeywordAction
    @Serializable @SerialName("endure") data class Endure(val count: Value) : KeywordAction
    /** "Exile three cards from your graveyard or sacrifice a Food" (CR 701.61a). */
    @Serializable @SerialName("forage") data object Forage : KeywordAction
    @Serializable @SerialName("cloak") data class Cloak(val count: Value = Value.Fixed(1)) : KeywordAction
    @Serializable @SerialName("time-travel") data object TimeTravel : KeywordAction
    @Serializable @SerialName("ring-tempts") data object TheRingTemptsYou : KeywordAction
    /** Exert [objects]: they don't untap during their controller's next untap step (CR 701.43). */
    @Serializable @SerialName("exert") data class Exert(val objects: Objects = Objects.Self) : KeywordAction
    /** Behold a [filter]: reveal one from your hand or choose one you control (CR 701.4a). */
    @Serializable @SerialName("behold") data class Behold(val filter: ObjectFilter) : KeywordAction
    /** Exile cards from your graveyard with total mana value [amount] or more (CR 701.59a). */
    @Serializable @SerialName("collect-evidence") data class CollectEvidence(val amount: Value) : KeywordAction
    /** "Earthbend N": target land you control becomes … (CR 701.66a). Keyword actions that target name a [slot]
     * declared in the enclosing [Instructions.targets], one per use. */
    @Serializable @SerialName("earthbend") data class Earthbend(val amount: Value, val slot: String) : KeywordAction
    /** "The next time [objects] would be destroyed this turn, instead remove all damage marked on it and its controller
     * taps it. If it's an attacking or blocking creature, remove it from combat" (CR 701.19a): a one-use shield effect
     * object. */
    @Serializable @SerialName("regenerate") data class Regenerate(val objects: Objects = Objects.Self) : KeywordAction
}
