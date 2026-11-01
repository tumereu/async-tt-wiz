package arcanum.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A one-shot effect (CR 610): something done once when a spell or ability resolves. Continuous rules are
 * [Mechanic]s; [ApplyMechanics] is the bridge that creates a continuous effect for a [Duration] (CR 611.2).
 * Keyword actions that are shorthand for other effects (investigate, amass, …) are [KeywordAction]s, expanded by the
 * shared table like keywords.
 */
@Serializable
sealed interface Effect {
    /** [source] deals [amount] damage to [to] (CR 120). [to] may hold objects and players. */
    @Serializable @SerialName("damage") data class DealDamage(
        val to: Recipients, val amount: Value, val source: Objects = Objects.Self,
    ) : Effect
    /** [first] and [second] each deal damage equal to their power to the other (CR 701.14). */
    @Serializable @SerialName("fight") data class Fight(val first: Objects, val second: Objects) : Effect

    @Serializable @SerialName("destroy") data class Destroy(val objects: Objects, val noRegeneration: Boolean = false) : Effect
    @Serializable @SerialName("sacrifice") data class Sacrifice(val objects: Objects) : Effect
    /** Move [objects] to another zone (exile, return to hand, put onto the battlefield, put into a library, …). */
    @Serializable @SerialName("move") data class Move(val objects: Objects, val to: Destination) : Effect
    @Serializable @SerialName("draw") data class Draw(val players: Players, val count: Value = Value.Fixed(1)) : Effect
    @Serializable @SerialName("discard") data class Discard(
        val players: Players, val count: Value, val filter: ObjectFilter = ObjectFilter.Any,
        val random: Boolean = false, val chooser: Players? = null,
    ) : Effect
    @Serializable @SerialName("mill") data class Mill(val players: Players, val count: Value) : Effect
    @Serializable @SerialName("scry") data class Scry(val players: Players, val count: Value) : Effect
    @Serializable @SerialName("surveil") data class Surveil(val players: Players, val count: Value) : Effect
    /** Search [library]'s library for [min]..[max] cards matching [filter]; found cards are bound by [Step.Do.bind]
     * and then moved by a following step. [reveal] shows them (CR 701.23). */
    @Serializable @SerialName("search") data class Search(
        val library: Players, val filter: ObjectFilter, val max: Value = Value.Fixed(1), val reveal: Boolean = false,
    ) : Effect
    @Serializable @SerialName("shuffle") data class Shuffle(val library: Players) : Effect
    @Serializable @SerialName("reveal") data class Reveal(val objects: Objects) : Effect
    /** [players] look at [objects] (hidden from others). */
    @Serializable @SerialName("look") data class LookAt(val objects: Objects, val players: Players = Players.You) : Effect

    @Serializable @SerialName("gain-life") data class GainLife(val players: Players, val amount: Value) : Effect
    @Serializable @SerialName("lose-life") data class LoseLife(val players: Players, val amount: Value) : Effect
    @Serializable @SerialName("set-life") data class SetLife(val players: Players, val amount: Value) : Effect

    @Serializable @SerialName("put-counters") data class PutCounters(val objects: Objects, val kind: CounterKind, val amount: Value) : Effect
    @Serializable @SerialName("remove-counters") data class RemoveCounters(val objects: Objects, val kind: CounterKind?, val amount: Value) : Effect
    @Serializable @SerialName("player-counters") data class GivePlayerCounters(val players: Players, val kind: CounterKind, val amount: Value) : Effect

    @Serializable @SerialName("tap") data class Tap(val objects: Objects) : Effect
    @Serializable @SerialName("untap") data class Untap(val objects: Objects) : Effect
    /** Attach an Aura, Equipment or Fortification to an object or player (CR 701.3). */
    @Serializable @SerialName("attach") data class Attach(val objects: Objects, val to: Recipients) : Effect
    @Serializable @SerialName("transform") data class Transform(val objects: Objects) : Effect
    @Serializable @SerialName("turn-face-up") data class TurnFaceUp(val objects: Objects) : Effect
    @Serializable @SerialName("turn-face-down") data class TurnFaceDown(val objects: Objects) : Effect
    @Serializable @SerialName("phase-out") data class PhaseOut(val objects: Objects) : Effect
    @Serializable @SerialName("set-status") data class SetStatus(val objects: Objects, val status: Status) : Effect
    @Serializable @SerialName("designation") data class GiveDesignation(val players: Players, val designation: Designation) : Effect

    @Serializable @SerialName("create-token") data class CreateToken(
        val token: TokenSpec, val count: Value = Value.Fixed(1), val controller: Players = Players.You,
        val tapped: Boolean = false, val attacking: Boolean = false,
    ) : Effect
    /** Copy a spell or ability on the Stack (CR 707.10); [newTargets] lets the copy's controller choose new ones. */
    @Serializable @SerialName("copy-spell") data class CopySpell(
        val objects: Objects, val controller: Players = Players.You, val newTargets: Boolean = true,
    ) : Effect
    /** Counter a spell or ability (CR 701.6); [unlessPaid] for "unless its controller pays". */
    @Serializable @SerialName("counter") data class Counter(val objects: Objects, val unlessPaid: Cost? = null) : Effect
    @Serializable @SerialName("add-mana") data class AddMana(val mana: ManaProduction, val players: Players = Players.You) : Effect

    /** Create a continuous effect (CR 611.2): [mechanics] apply to [affects] for [duration]. In the host it becomes a
     * visible effect object that players can see and remove. Giant Growth: +3/+3 to the target until end of turn. */
    @Serializable @SerialName("apply") data class ApplyMechanics(
        val mechanics: List<Mechanic>, val affects: Affected, val duration: Duration,
    ) : Effect
    /** Gain control of [objects] for [duration] (layer 2; a shorthand for ApplyMechanics with [Mechanic.ControlledBy]). */
    @Serializable @SerialName("gain-control") data class GainControl(
        val objects: Objects, val duration: Duration = Duration.Indefinitely, val players: Players = Players.You,
    ) : Effect
    /** "At the beginning of the next end step, …" (CR 603.7): a one-time trigger created now. In the host it is a
     * visible object. */
    @Serializable @SerialName("delayed-trigger") data class CreateDelayedTrigger(
        val trigger: Trigger, val instructions: Instructions, val once: Boolean = true,
    ) : Effect
    /** Keep [objects] on the source as card memory under [key] (CR 607 linked abilities: "exiled with ~"). */
    @Serializable @SerialName("remember") data class Remember(val key: String, val objects: Objects) : Effect
    @Serializable @SerialName("forget") data class Forget(val key: String) : Effect

    @Serializable @SerialName("extra-turn") data class ExtraTurn(val players: Players = Players.You) : Effect
    @Serializable @SerialName("extra-combat") data class ExtraCombat(val untapAttackers: Boolean = false) : Effect
    @Serializable @SerialName("win") data class WinGame(val players: Players = Players.You) : Effect
    @Serializable @SerialName("lose") data class LoseGame(val players: Players) : Effect
    /** Cast [objects] without paying their mana cost, or by paying [cost] (CR 601.2, "you may cast it"). */
    @Serializable @SerialName("cast") data class Cast(val objects: Objects, val withoutPaying: Boolean = true, val cost: Cost? = null) : Effect
    @Serializable @SerialName("keyword-action") data class Action(val action: KeywordAction) : Effect
}

/** Who or what receives damage or an attachment: objects, players, or both. */
@Serializable
data class Recipients(val objects: Objects? = null, val players: Players? = null)

/** Where [Effect.Move] puts things (CR 400.7). [owner] true: the owner's zone of that kind (the normal case). */
@Serializable
data class Destination(
    val zone: Zone,
    /** Library position: 0 = top, -1 = bottom, n = nth from top. */
    val libraryPosition: Int? = null,
    val tapped: Boolean = false,
    val faceDown: Boolean = false,
    /** Onto the battlefield under this player's control; null = owner's. */
    val controller: Players? = null,
    /** Onto the battlefield attached to … (an Aura returned to the battlefield). */
    val attachedTo: Recipients? = null,
    /** Onto the battlefield transformed / with its back face up. */
    val transformed: Boolean = false,
    /** Put into the library in a random order (several cards). */
    val randomOrder: Boolean = false,
)

/** What token to create (CR 111). */
@Serializable
sealed interface TokenSpec {
    /** A token described by its characteristics ("a 1/1 white Soldier creature token with flying"). */
    @Serializable @SerialName("defined") data class Defined(val face: FaceDefinition) : TokenSpec
    /** A predefined token (CR 111.10): Treasure, Clue, Food, Blood, Map, Powerstone, … by name; the shared table
     * holds their definitions. */
    @Serializable @SerialName("predefined") data class Predefined(val name: String) : TokenSpec
    /** A token that's a copy of [objects] (CR 707), with [changes] applied as part of the copy effect. */
    @Serializable @SerialName("copy") data class CopyOf(val objects: Objects, val changes: List<Mechanic> = emptyList()) : TokenSpec
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
}
