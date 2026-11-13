package org.asyncttwiz.automatum.boundary

import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.asyncttwiz.automatum.CardPlays
import org.asyncttwiz.automatum.ComputedBoard
import org.asyncttwiz.automatum.HandPlays
import org.asyncttwiz.protocol.CounterColor
import org.asyncttwiz.protocol.EngineRewindRequest
import org.asyncttwiz.protocol.PlayLink
import org.asyncttwiz.protocol.ProjectionLink
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings

/*
 * Web client step 4c (docs/specs/web-client.md §1 item 3): what the engine side publishes after each change of a game
 * (the app's `EngineGame.Shown`: the board the table draws and what its panels read of the device) as the page receives
 * it, one JSON message per published board ([ShownWire.encode]). Everything the page already holds is referred to, not
 * sent again: events by id, card data by digest, a board's cards and lists by what changed, a board's engine state by
 * how the page's copy reaches it ([StateRef]), a board, its engine state or a transition the page has by number.
 */

/** One published board of a game and the notes published with it. */
@Serializable
class ShownWire(
    val version: Long,
    /** The events the page doesn't hold yet, by id ([WireEventsOut]). */
    val events: Map<String, JsonElement> = emptyMap(),
    /** The card data the page doesn't hold yet, by digest ([WireDefinitionsOut]). */
    val definitions: Map<String, SubmittedDefinition> = emptyMap(),
    val board: TableBoardWire,
    val status: StatusWire,
    val locked: Boolean = false,
    val settings: PlaySettings = PlaySettings(),
    val opponent: PlayerId,
    val behind: Boolean = false,
    val tip: String,
    val localHead: String,
    val drafting: Boolean = false,
    val request: EngineRewindRequest? = null,
    val indicator: GameDevice.Indicator? = null,
    val undoTarget: String? = null,
    /** The shown line's ids, as the one of the board before changed; the first [held] of them are the main line's. */
    val line: ListDelta<String> = ListDelta(),
    val held: Int = 0,
    val transition: TransitionWire? = null,
    val needsReload: Boolean = false,
    val pausing: Boolean = false,
    val takingBack: Boolean = false,
    val reveal: GameDevice.Reveal? = null,
    val older: ListDelta<String> = ListDelta(),
    val olderComplete: Boolean = true,
    val show: GameDevice.PlayShow? = null,
    val showDone: String? = null,
    val takeBack: HeldWire? = null,
    val branchStop: Boolean = false,
    val turnChange: TurnChangeWire? = null,
    val combatHold: GameDevice.CombatHold? = null,
    val ownActionShown: Boolean = false,
    val opponentAt: GameDevice.OpponentAt? = null,
    val moments: List<GameDevice.ViewMoment> = emptyList(),
    val ownStepHeld: Boolean = false,
    /** The notes published with the board, to be shown once. */
    val notes: List<NoteWire> = emptyList(),
) {
    companion object {
        fun encode(wire: ShownWire): String = EngineMessages.json.encodeToString(serializer(), wire)
        fun decode(text: String): ShownWire = EngineMessages.json.decodeFromString(serializer(), text)
    }
}

/**
 * A board the table draws ([number]: its values' number on the boundary, null for a board without engine state). [reuse]:
 * [FRESH] (everything here), [SAME_ENGINE] (the engine state and values of the board [number] the page holds, with this
 * board's own fields), [SAME_BOARD] (the very board [number] the page holds, nothing else sent).
 */
@Serializable
class TableBoardWire(
    val number: Long? = null,
    val reuse: Int = FRESH,
    val gameId: String? = null,
    val playId: String? = null,
    val nodeId: String? = null,
    val board: BoardStateWire? = null,
    /** The card data's digests; null: the list of the board decoded before it. */
    val definitions: List<String>? = null,
    val state: StateRef? = null,
    val viewer: String? = null,
    /** What the viewer may see of the state, as the list of the board decoded before it changed. */
    val visible: ListDelta<ObjectId>? = null,
    val computed: ComputedBoard.Wire? = null,
    @Serializable(with = HandPlaysSerializer::class) val handPlays: HandPlays? = null,
    @Serializable(with = CardPlaysSerializer::class) val cardPlays: CardPlays? = null,
    val openingBottom: Int = 0,
    val damageDealt: Boolean = false,
) {
    companion object {
        const val FRESH = 0
        const val SAME_ENGINE = 1
        const val SAME_BOARD = 2
    }
}

/** A transition (the app's `EngineGame.Transition`): whole when new, else [known] (the page has the one of [serial]). */
@Serializable
class TransitionWire(
    val serial: Long,
    val known: Boolean = false,
    val cursor: String = "",
    val jump: Boolean = false,
    val changes: List<TableChangeWire> = emptyList(),
    val shuffled: List<String> = emptyList(),
    val forward: List<String> = emptyList(),
    val backward: List<String> = emptyList(),
    val steps: List<TableBoardWire> = emptyList(),
    val undone: UndoneWire? = null,
)

/** A change flash (the app's `TableChange`): its key, its deltas as decimal numbers, and what it links. */
@Serializable
class TableChangeWire(val target: String, val kind: String, val color: CounterColor? = null, val line: Int? = null,
    val delta: List<String> = emptyList(), val owner: String? = null, val link: PlayLink? = null, val projectionLink: ProjectionLink? = null)

/** What an undo took back (the app's `EngineGame.Undone`): its state as the board [board] the page holds, or [state] whole. */
@Serializable
class UndoneWire(val by: String, val event: String? = null, val board: Long? = null, val state: JsonElement? = null, val branch: Boolean = false)

/** A turn change (the app's `TurnChange`): whole when new, else [known]. */
@Serializable
class TurnChangeWire(val serial: Long, val known: Boolean = false, val player: String = "", val event: String = "", val at: Int = 0,
    val forward: List<String> = emptyList())

/** A take-back held for the player's tap (the app's `EngineGame.Held`). */
@Serializable
class HeldWire(val by: String, val undo: Boolean)
