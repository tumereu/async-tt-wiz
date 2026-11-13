package org.asyncttwiz.automatum.boundary

import arcanum.engine.input.Block
import arcanum.engine.input.Input
import arcanum.engine.setup.GameResult
import arcanum.engine.state.TurnState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.asyncttwiz.automatum.TokenArtQuery
import org.asyncttwiz.protocol.AssumeReveals
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.EngineHistoryPage
import org.asyncttwiz.protocol.EngineSnapshot
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameRevision
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameRepro
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.TableChange

/*
 * Web client step 4c part 3 (docs/specs/web-client.md §1 item 3): a game's device across the engine boundary. In the
 * browser the device (app-engine's EngineGame with its GameDevice) lives in the engine's Web Worker; the page sends it the
 * player's actions, the server's events and the table's reads as [DeviceRequest]s and gets [DeviceAnswer]s back: each board
 * the device publishes ([DeviceAnswer.Published], a [ShownWire]), each request's result ([DeviceAnswer.Done]), the worker's
 * log lines and its lookups in the page's card records. One channel per page, messages in order both ways; every message
 * names its game. Android keeps the device in-process on its engine thread and sends none of these.
 */

/** A request to a game's device on the engine side; [id] numbers it on the page (its answer names it). */
@Serializable
sealed interface DeviceRequest {
    val id: Long

    /** The board the player acted on: the version of the page's last published board ([seen]; null before one). */
    sealed interface Own : DeviceRequest { val seen: Long? }

    // ---- The game opening and the server
    /** Opens the device for [me] on the server's [snapshot] of the play [base]. */
    @Serializable @SerialName("open") data class Open(override val id: Long, val me: String, val base: PlayState,
        val snapshot: EngineSnapshot, val showPlays: Boolean = false) : DeviceRequest
    /** Loads the game again from [snapshot] ([live]: from the open game's stream). */
    @Serializable @SerialName("load") data class Load(override val id: Long, val snapshot: EngineSnapshot, val base: PlayState,
        val live: Boolean) : DeviceRequest
    @Serializable @SerialName("revision") data class Revision(override val id: Long, val message: GameRevision) : DeviceRequest
    /** The revision ending the play while the player stands at the table; [DeviceResult.Flag] false: the device can't show it. */
    @Serializable @SerialName("hold-end") data class HoldEnd(override val id: Long, val message: GameRevision) : DeviceRequest
    @Serializable @SerialName("refused") data class Refused(override val id: Long, val action: GameAction, val reason: String?) : DeviceRequest
    @Serializable @SerialName("reveals") data class Reveals(override val id: Long, val value: AssumeReveals) : DeviceRequest
    @Serializable @SerialName("tick") data class Tick(override val id: Long, val now: Long) : DeviceRequest
    /** What to send now, drained: [DeviceResult.Actions]. */
    @Serializable @SerialName("take") data class Take(override val id: Long, val now: Long) : DeviceRequest
    @Serializable @SerialName("away") data class Away(override val id: Long) : DeviceRequest
    @Serializable @SerialName("returned") data class Returned(override val id: Long) : DeviceRequest

    // ---- The player's own actions ([DeviceResult.Acted] unless said)
    @Serializable @SerialName("pass") data class Pass(override val id: Long, override val seen: Long?) : Own
    @Serializable @SerialName("hold-resolve") data class HoldResolve(override val id: Long, override val seen: Long?) : Own
    @Serializable @SerialName("hold-assume") data class HoldAssume(override val id: Long, override val seen: Long?) : Own
    @Serializable @SerialName("send") data class Send(override val id: Long, override val seen: Long?, val inputs: List<Input>,
        val table: List<TableChange> = emptyList()) : Own
    @Serializable @SerialName("act") data class Act(override val id: Long, override val seen: Long?, val input: Input) : Own
    /** [DeviceResult.None]. */
    @Serializable @SerialName("end-turn") data class EndTurn(override val id: Long, override val seen: Long?) : Own
    @Serializable @SerialName("assume") data class Assume(override val id: Long, override val seen: Long?, val blocks: List<Block> = emptyList()) : Own
    @Serializable @SerialName("table") data class Table(override val id: Long, override val seen: Long?, val changes: List<TableChange>) : Own
    @Serializable @SerialName("concede") data class Concede(override val id: Long, override val seen: Long?) : Own
    @Serializable @SerialName("suggest") data class Suggest(override val id: Long, override val seen: Long?, val result: GameResult,
        val note: String = "") : Own
    @Serializable @SerialName("answer-suggestion") data class AnswerSuggestion(override val id: Long, override val seen: Long?,
        val accept: Boolean) : Own
    @Serializable @SerialName("confirm-result") data class ConfirmResult(override val id: Long, override val seen: Long?) : Own
    @Serializable @SerialName("take-back-result") data class TakeBackResult(override val id: Long, override val seen: Long?) : Own
    /** [DeviceResult.None]. */
    @Serializable @SerialName("settings") data class Settings(override val id: Long, val settings: PlaySettings) : DeviceRequest
    /** [DeviceResult.Acted] or [DeviceResult.None] (no opening open). */
    @Serializable @SerialName("mulligan") data class Mulligan(override val id: Long, override val seen: Long?) : Own
    @Serializable @SerialName("keep") data class Keep(override val id: Long, override val seen: Long?) : Own
    @Serializable @SerialName("bottom") data class Bottom(override val id: Long, override val seen: Long?, val cards: List<String>) : Own
    /** [DeviceResult.Undo]. */
    @Serializable @SerialName("undo") data class Undo(override val id: Long, override val seen: Long?, val target: String? = null) : Own
    @Serializable @SerialName("request-undo") data class RequestUndo(override val id: Long, val target: String, val message: String) : DeviceRequest
    @Serializable @SerialName("answer-request") data class AnswerRequest(override val id: Long, val accept: Boolean) : DeviceRequest
    @Serializable @SerialName("cancel-request") data class CancelRequest(override val id: Long) : DeviceRequest
    /** Web client step 4d: the table's own changes its controls make (a card's place, the combat staging, attack choices,
     * early answers), worked out on the board the page showed: [DeviceResult.Flag] taken. */
    @Serializable @SerialName("staging") data class Staging(override val id: Long, override val seen: Long?,
        val actions: List<org.asyncttwiz.protocol.PlayAction>, val requestId: String? = null) : Own
    @Serializable @SerialName("stage") data class Stage(override val id: Long, val board: BoardState) : DeviceRequest
    @Serializable @SerialName("unstage") data class Unstage(override val id: Long) : DeviceRequest
    /** Web client step 4e: a step of a cast flow session where the device is ([CastRequest]): [DeviceResult.Cast]. */
    @Serializable @SerialName("cast") data class Cast(override val id: Long, val request: CastRequest) : DeviceRequest

    // ---- Playback and the view's holds
    @Serializable @SerialName("start-playback") data class StartPlayback(override val id: Long) : DeviceRequest
    @Serializable @SerialName("dismiss-reveal") data class DismissReveal(override val id: Long, val reveal: GameDevice.Reveal,
        val pressedAt: Long) : DeviceRequest
    @Serializable @SerialName("show-plays") data class ShowPlays(override val id: Long, val on: Boolean) : DeviceRequest
    @Serializable @SerialName("dismiss-show") data class DismissShow(override val id: Long, val show: GameDevice.PlayShow) : DeviceRequest
    @Serializable @SerialName("dismiss-combat-hold") data class DismissCombatHold(override val id: Long, val hold: GameDevice.CombatHold,
        val why: String) : DeviceRequest
    @Serializable @SerialName("dismiss-shows") data class DismissShows(override val id: Long) : DeviceRequest
    @Serializable @SerialName("pause") data class Pause(override val id: Long) : DeviceRequest
    @Serializable @SerialName("hold-playback") data class HoldPlayback(override val id: Long) : DeviceRequest
    /** The next buffered event played: [DeviceResult.Event]. */
    @Serializable @SerialName("step") data class Step(override val id: Long) : DeviceRequest

    // ---- Older history and the table's reads
    /** The event ending the next older page to ask for: [DeviceResult.Text]. */
    @Serializable @SerialName("older-through") data class OlderThrough(override val id: Long) : DeviceRequest
    /** The page of older history the board at [event] needs: [DeviceResult.Text]. */
    @Serializable @SerialName("needs-for") data class NeedsFor(override val id: Long, val event: String) : DeviceRequest
    /** The page of older history the open undo request's preview needs: [DeviceResult.Text]. */
    @Serializable @SerialName("request-needs") data class RequestNeeds(override val id: Long) : DeviceRequest
    /** The page ending at [through] the page fetched from the server (null: it failed): [DeviceResult.Flag] taken. */
    @Serializable @SerialName("page") data class Page(override val id: Long, val through: String, val page: EngineHistoryPage?) : DeviceRequest
    /** Timeline mode's board at [event]: [DeviceResult.Board]. */
    @Serializable @SerialName("review") data class Review(override val id: Long, val event: String) : DeviceRequest
    /** The Undo dialog's view of going back to just after [target]: [DeviceResult.Preview]. */
    @Serializable @SerialName("undo-preview") data class UndoPreview(override val id: Long, val target: String) : DeviceRequest
    /** The incoming undo request's dialog: [DeviceResult.View]. */
    @Serializable @SerialName("request-view") data class RequestView(override val id: Long, val viewer: String, val localHead: String,
        val target: String) : DeviceRequest
    /** Web client step 4d: the hand plays and card menus' plays of the published board numbered [board] (its
     * app-engine's EngineBoardValues number), worked out on the engine side where the board is: [DeviceResult.Plays]. */
    @Serializable @SerialName("plays") data class Plays(override val id: Long, val board: Long) : DeviceRequest
    /** The device's reproduction bundle: [DeviceResult.Repro]. */
    @Serializable @SerialName("repro") data class Repro(override val id: Long) : DeviceRequest
    /** The board shown and the player's options into the log (a problem report follows). */
    @Serializable @SerialName("log-board") data class LogBoard(override val id: Long) : DeviceRequest

    // ---- The page's card records, answering the device's lookups ([DeviceAnswer.Lookup])
    @Serializable @SerialName("token-art") data class TokenArtFound(override val id: Long, val query: TokenArtQuery,
        val scryfallId: String?) : DeviceRequest
    @Serializable @SerialName("named-card") data class NamedCardFound(override val id: Long, val name: String,
        val definition: arcanum.api.CardDefinition?) : DeviceRequest

    /** The game closes on the page: the engine side forgets it. */
    @Serializable @SerialName("close") data class Close(override val id: Long) : DeviceRequest
}

/** What a [DeviceRequest] did, as its [DeviceAnswer.Done] carries it. */
@Serializable
sealed interface DeviceResult {
    @Serializable @SerialName("none") data object None : DeviceResult
    @Serializable @SerialName("acted") data class Acted(val acted: ActedWire) : DeviceResult
    @Serializable @SerialName("undo") data class Undo(val undo: GameDevice.Undo) : DeviceResult
    /** An event of the line by its id (the board published before the answer shows it), with the event itself as the
     * game's codec writes it ([org.asyncttwiz.protocol.game.EngineJson.encodeElement]); null for none. */
    @Serializable @SerialName("event") data class Event(val event: JsonElement?) : DeviceResult
    @Serializable @SerialName("actions") data class Actions(val actions: List<GameAction>) : DeviceResult
    @Serializable @SerialName("text") data class Text(val text: String?) : DeviceResult
    @Serializable @SerialName("flag") data class Flag(val flag: Boolean) : DeviceResult
    /** A board outside the published ones (timeline mode's, a preview's), with the events and card data the page lacks. */
    @Serializable @SerialName("board") data class Board(val board: LooseBoard?) : DeviceResult
    @Serializable @SerialName("preview") data class Preview(val board: LooseBoard?, val needsRequest: Boolean?, val loading: Boolean) : DeviceResult
    @Serializable @SerialName("view") data class View(val message: String, val unseen: Boolean, val turn: TurnState,
        val board: LooseBoard?) : DeviceResult
    @Serializable @SerialName("view-none") data object NoView : DeviceResult
    @Serializable @SerialName("repro") data class Repro(val repro: GameRepro?) : DeviceResult
    /** A published board's plays ([DeviceRequest.Plays]); null where the engine side no longer holds the board. */
    @Serializable @SerialName("plays") data class Plays(
        @Serializable(with = HandPlaysSerializer::class) val hand: org.asyncttwiz.automatum.HandPlays? = null,
        @Serializable(with = CardPlaysSerializer::class) val card: org.asyncttwiz.automatum.CardPlays? = null) : DeviceResult
    /** A cast flow session's step's answer ([DeviceRequest.Cast]). */
    @Serializable @SerialName("cast") data class Cast(val answer: CastAnswer) : DeviceResult
}

/** A board sent outside a [ShownWire] (its own engine state, sent whole), with what the page lacks to read it. */
@Serializable
data class LooseBoard(val events: Map<String, JsonElement> = emptyMap(),
    val definitions: Map<String, org.asyncttwiz.protocol.SubmittedDefinition> = emptyMap(), val board: TableBoardWire)

/** [GameDevice.Acted] across the boundary (a sent event as the game's codec writes it). */
@Serializable
sealed interface ActedWire {
    @Serializable @SerialName("sent") data class Sent(val event: JsonElement) : ActedWire
    @Serializable @SerialName("confirmed") data object Confirmed : ActedWire
    @Serializable @SerialName("kept") data object Kept : ActedWire
    @Serializable @SerialName("made") data object Made : ActedWire
    @Serializable @SerialName("refused") data class Refused(val reason: String) : ActedWire
}

/** The engine side's message to the page about game [DeviceMessage.game] (or, for [Log], none). */
@Serializable
sealed interface DeviceAnswer {
    /** A board the device published, with the notes published with it ([ShownWire]); [takeBacks] and [lastTakeBack]: the
     * device's count of boards taken back after they were shown and the latest one's words. */
    @Serializable @SerialName("published") data class Published(val shown: ShownWire, val takeBacks: Long, val lastTakeBack: String,
        /** The game's card data ([org.asyncttwiz.protocol.game.EngineJson.encodeDefinitions]) when the device (re)loaded: the
         * page reads this board and the later ones anew with it. */
        val definitions: String? = null) : DeviceAnswer
    /** Request [id] done with [result]. */
    @Serializable @SerialName("done") data class Done(val id: Long, val result: DeviceResult) : DeviceAnswer
    /** Request [id] failed on the engine side ([problem]: what failed). */
    @Serializable @SerialName("failed") data class Failed(val id: Long, val problem: String) : DeviceAnswer
    /** The game couldn't be loaded on the engine side: the table says it can't be opened. */
    @Serializable @SerialName("load-failed") data object LoadFailed : DeviceAnswer
    /** The worker's log lines, in order, for the page's app log (problem reports carry it). */
    @Serializable @SerialName("log") data class Log(val lines: List<LogLine>) : DeviceAnswer
    /** The device asks the page's card records: a token's picture. Answered by [DeviceRequest.TokenArtFound]. */
    @Serializable @SerialName("token-art") data class TokenArt(val query: TokenArtQuery) : DeviceAnswer
    /** The device asks the page's card records: the card named [name] (CR 707.12). Answered by [DeviceRequest.NamedCardFound]. */
    @Serializable @SerialName("named-card") data class NamedCard(val name: String) : DeviceAnswer
}

/** One line of the worker's log: [priority] as the platform log numbers it (4 info, 5 warn, 6 error). */
@Serializable
data class LogLine(val priority: Int, val tag: String, val message: String)

/**
 * A device message as it crosses: the game it is about and the request or answer, one JSON string ([encode]); the
 * planning requests share the channel ([EngineMessages]) and never start with [PREFIX].
 */
@Serializable
data class DeviceMessage(val game: String, val request: DeviceRequest? = null, val answer: DeviceAnswer? = null) {
    companion object {
        /** What every device message starts with on the channel. */
        const val PREFIX = "{\"game\":"

        fun encode(message: DeviceMessage): String = EngineMessages.json.encodeToString(serializer(), message)
        fun decode(text: String): DeviceMessage = EngineMessages.json.decodeFromString(serializer(), text)
        fun request(game: String, request: DeviceRequest) = encode(DeviceMessage(game, request = request))
        fun answer(game: String, answer: DeviceAnswer) = encode(DeviceMessage(game, answer = answer))

        /** Whether [text] is a device message (else a planning request or answer). */
        fun isDevice(text: String): Boolean = text.startsWith(PREFIX)
    }
}
