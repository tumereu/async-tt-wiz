package arcanum.engine.state

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Designation
import arcanum.api.Duration
import arcanum.api.Instructions
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.TurnStep
import kotlinx.serialization.Serializable

/**
 * A player (CR 102) and their own zones. Library index 0 is the top card; hand, graveyard and outside list cards in
 * the order they arrived (the last is the newest, the top of the graveyard).
 */
@Serializable
data class PlayerState(
    val id: PlayerId,
    val life: Int,
    /** Starting life total (CR 103.4). */
    val startingLife: Int = 20,
    /** Player counters: poison, energy, rad, … (CR 122.1). */
    val counters: List<CounterEntry> = emptyList(),
    /** Mana added and not yet spent (CR 106.4). */
    val manaPool: List<ManaUnit> = emptyList(),
    /** Lost the game (CR 104.3); kept in turn order, skipped by turns and priority. */
    val lost: Boolean = false,
    /** Why they lost ([lost]), null while they haven't; set and taken back with [arcanum.engine.change.Change.Lost]. */
    val lossReason: arcanum.engine.setup.LossReason? = null,
    val designations: List<Designation> = emptyList(),
    val library: List<ObjectId> = emptyList(),
    val hand: List<ObjectId> = emptyList(),
    val graveyard: List<ObjectId> = emptyList(),
    /** Outside the game: sideboard, companion (CR 400.11). */
    val outside: List<ObjectId> = emptyList(),
    /** Player-scoped ledger entries (D22), by key. */
    val ledger: Map<String, Int> = emptyMap(),
    /**
     * "Skip when I have no choice" (I-D18), on by default: the engine skips this player's questions that leave nothing
     * to choose, and [arcanum.engine.Engine.shouldPassFor] says to pass priority for them when they can do nothing.
     * Off: a skip or a pass that depends on cards only this player may see (their hand, face-down cards) is asked
     * instead, so how fast the game goes on tells the other players nothing about those cards. Skips that depend on
     * nothing hidden stay automatic either way (P5). A host changes it with [arcanum.engine.change.Change.SkipWhenNoChoice].
     */
    val skipWhenNoChoice: Boolean = true,
    /**
     * The library cards whose place some player knows since the library was last shuffled (CR 401.2: the order is
     * otherwise unknown): 0 for a card whose exact place is known (put on the top, the bottom or Nth from the top, looked
     * at or revealed where it lies, put back in a chosen order), a group number above 0 for cards put in together in a
     * random order (their places are known as a group, not which is where). Cards without an entry are the unknown part.
     * Entries of cards that left the library are dropped when it next changes. Kept by the engine
     * ([arcanum.engine.change.Change.LibraryPlaces]); a host reorders only unknown parts ([arcanum.engine.Engine.unknownOrder]).
     */
    val libraryPlaces: Map<ObjectId, Int> = emptyMap(),
) {
    fun counters(kind: CounterKind): Int = counters.firstOrNull { it.kind == kind }?.count ?: 0
}

/**
 * One mana in a pool (CR 106.4): its [color] (null: colourless), where it came from, what it may be spent on
 * ([restriction], CR 106.6), what it gives the spell it pays for ([spellGets], CR 106.6a), how long it stays, and
 * its "when spent" trigger (D42).
 */
@Serializable
data class ManaUnit(
    val color: Color?,
    val source: ObjectId? = null,
    val snow: Boolean = false,
    val restriction: ObjectFilter? = null,
    val spellGets: List<Mechanic> = emptyList(),
    val keptUntil: Duration? = null,
    val whenSpent: Instructions? = null,
)

/**
 * Whose turn it is and where (CR 500). [turnStarted] holds, for each player, the number of the latest turn they
 * began: the summoning sickness rule reads it (CR 302.6).
 */
@Serializable
data class TurnState(
    val number: Int,
    val active: PlayerId,
    val step: TurnStep,
    /** Lands each player played this turn (CR 305.2). */
    val landsPlayed: Map<PlayerId, Int> = emptyMap(),
    val turnStarted: Map<PlayerId, Int> = emptyMap(),
    /** Players taking extra turns after this one, in order (CR 500.7). */
    val extraTurns: List<PlayerId> = emptyList(),
    // gtc
    /** Additional phases still to come, by their first step, in order (CR 500.8): each begins as the phase under way ends. */
    val extraPhases: List<TurnStep> = emptyList(),
    /** Where the turn's own order goes on once the additional phases are over (CR 500.8). */
    val resumeAt: TurnStep? = null,
    // sos
    /** One entry for each turn a player will skip: their next turns that don't happen (CR 614.10, "skips their next X turns"). */
    val skippedTurns: List<PlayerId> = emptyList(),
    /** Control of players waiting for their next turn (or combat phase), oldest first (CR 723.1, 723.1a, b): each waits until
     * that player actually takes a turn. */
    val controlNext: List<PlayerControl> = emptyList(),
    /** The player controlled during this turn (CR 723.1: the whole turn, until the next one begins) or its combat phase
     * (723.2), and by whom. */
    val controlled: PlayerControl? = null,
    // fin
    /** Additional steps still to come right after the current one, in order (CR 500.9: "an additional end step after this step"). */
    val extraSteps: List<TurnStep> = emptyList(),
    /** An effect ended the turn (CR 724.1): once the resolving object has left the Stack, the game goes straight to the cleanup step. */
    val endingTurn: Boolean = false,
)

/**
 * [controller] controls [player] (CR 723): makes every choice and decision [player] is allowed or told to make, seeing what
 * they could see (723.4, 723.5), during [player]'s next turn, or only its combat phase when [combatOnly] (723.2).
 */
@Serializable
data class PlayerControl(val player: PlayerId, val controller: PlayerId, val combatOnly: Boolean = false)

/** Who has priority and how many players in a row have passed (CR 117, D57). Null holder: nobody (untap, cleanup). */
@Serializable
data class PriorityState(val holder: PlayerId?, val passes: Int = 0)

/** The current combat (CR 506): attacking creatures and what they attack, blockers and what they block. It exists from
 * the declaration of attackers (CR 508.1) to the end of combat (CR 511.3). */
@Serializable
data class CombatState(
    val attackers: List<Attacker> = emptyList(),
    val blockers: List<Blocker> = emptyList(),
    /** Defending players who have declared blockers (CR 509.1); the declare blockers step waits for the rest. */
    val blockersDeclaredBy: List<PlayerId> = emptyList(),
    /** The combat damage step now is the first-strike one (CR 510.4): a second one follows. */
    val firstStrikeStep: Boolean = false,
    /** The creatures that assigned combat damage in a first-strike step (CR 510.4, 702.4c–d); null when there was
     * none this combat. */
    val firstStrikers: List<ObjectId>? = null,
    /** CR 509.1h: every defending player has declared blockers, so each attacker is now blocked or unblocked
     * ([Attacker.blocked]); an attacker put onto the battlefield after that is unblocked (CR 508.4d). */
    val blocksDecided: Boolean = false,
)

/** [id] attacks [target] (a player, planeswalker or battle); [blocked] once blockers are declared (CR 509.1h). */
@Serializable
data class Attacker(
    val id: ObjectId, val target: TargetRef, val blocked: Boolean = false,
    /** CR 506.4, 506.4c: the planeswalker or battle it attacked was removed from combat (it left the battlefield or
     * stopped being a planeswalker or battle): it still attacks, but nothing, and deals no combat damage unblocked. */
    val targetRemoved: Boolean = false,
    /** The attacking band it is in (banding, CR 702.22c, e), by the number declared. */
    val band: Int? = null,
)

/** [id] blocks [attackers]; [player] is the defending player who declared it (CR 509.1g), null when unknown. */
@Serializable
data class Blocker(val id: ObjectId, val attackers: List<ObjectId>, val player: PlayerId? = null)
