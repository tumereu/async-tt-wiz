package org.asyncttwiz.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** The game rules this build plays by (SYS-033: apps and server agree on it as they connect). */
const val CURRENT_GAMEPLAY_RULES_VERSION = 36
/** The socket protocol version of this build (SYS-033: one compatible set of apps and server). Apps offer it
 *  as [GameHello.maxMinor] and the server welcomes with its own: a lower offer gets [GameUpgradeRequired] (the app
 *  is older), and an app welcomed with a lower minor waits for the server update. Bump it with any socket wire change. */
const val GAME_PROTOCOL_MINOR = 10

@Serializable enum class PresenceState { ONLINE, IDLE, OFFLINE }
/** The opponent's presence in this game. Ages are whole seconds as of sending, so the clocks never need to agree.
 *  [idleSeconds]: since their last input while the game is open (ONLINE/IDLE only).
 *  [lastSeenSeconds]: since they last had the game open; null when they never have. */
@Serializable data class OpponentPresence(val state: PresenceState, val idleSeconds: Int? = null, val lastSeenSeconds: Int? = null)

@Serializable data class GameScope(val gameId: String, val preparationId: String? = null,
    val invitationId: String? = null, val playId: String? = null)
@Serializable sealed interface GameAction {
    /** I-D54 phase 3: one acting point of a game whose state is the engine's ([org.asyncttwiz.protocol.game]): engine
     * inputs (with the line's random answers) and table changes on the event it was made on. The server makes the event
     * ([org.asyncttwiz.protocol.game.Authority]) at the head, confirms or branches at an assumed input about the author
     * (I-D56 §8, [org.asyncttwiz.protocol.game.Acceptance]), and refuses it as "stale" when another player's event came
     * first, or when an input or table change isn't possible there ("refusedByEngine", "revealsHidden"). */
    @Serializable @SerialName("submit") data class Submit(val submission: org.asyncttwiz.protocol.game.Submission) : GameAction
    /** I-D56 §7: the actor's device showed the main line up to [eventId]: their local head moves forward to it (moving
     * past an assumed input about them confirms it). Sent throttled; never moves a local head back. */
    @Serializable @SerialName("engineSeen") data class EngineSeen(val eventId: String) : GameAction
    /** I-D56 §10: takes the main line back to just after [target] when that is free for the actor (everything after it
     * their own and nothing new shown to them); else refused "requestNeeded". Starts a new line. */
    @Serializable @SerialName("engineRewind") data class EngineRewind(val target: String) : GameAction
    /** I-D56 §10: asks the other player to approve taking the main line back to just after [target], with [message]. */
    @Serializable @SerialName("engineRequestRewind") data class EngineRequestRewind(val target: String, val message: String = "") : GameAction
    /** I-D56 §10: the other player's answer to the open undo request: [accept] rewinds, else it is closed. */
    @Serializable @SerialName("engineAnswerRewind") data class EngineAnswerRewind(val accept: Boolean) : GameAction
    /** I-D56 §10: the requester takes their open undo request back. */
    @Serializable @SerialName("engineCancelRewind") data object EngineCancelRewind : GameAction
    /** I-D56 §13: the actor's play settings for this game (response mode, Assume resolves, Autotap); the response mode
     * is shared with the other player at once ([EngineUpdate.modes], owner 2026-11-06). */
    @Serializable @SerialName("engineSettings") data class EngineSettings(val settings: org.asyncttwiz.protocol.game.PlaySettings) : GameAction
    /** LOB-135: the game's assume reveal settings; only its host (the inviter) changes them, before and during the game.
     * Scoped to the game alone (no preparation, invitation or play). */
    @Serializable @SerialName("setAssumeReveals") data class SetAssumeReveals(val reveals: AssumeReveals) : GameAction
    @Serializable @SerialName("acceptInvitation") data object AcceptInvitation : GameAction
    @Serializable @SerialName("declineInvitation") data object DeclineInvitation : GameAction
    @Serializable @SerialName("reinvite") data object Reinvite : GameAction
    @Serializable @SerialName("cancelSetup") data object CancelSetup : GameAction
    @Serializable @SerialName("setStarter") data class SetStarter(val choice: StarterChoice) : GameAction
    /** Lobby toggle: after each play the starter choice switches to the player who did not start it. */
    @Serializable @SerialName("setAlternateStarter") data class SetAlternateStarter(val enabled: Boolean) : GameAction
    /** Lobby toggle for the actor's own deck: show its name and cover to the opponent (this game only). */
    @Serializable @SerialName("showDeck") data class ShowDeck(val shown: Boolean) : GameAction
    @Serializable @SerialName("ready") data object Ready : GameAction
    @Serializable @SerialName("withdrawReady") data object WithdrawReady : GameAction
    @Serializable @SerialName("deleteGame") data object DeleteGame : GameAction
    @Serializable @SerialName("selectDeck") data class SelectDeck(val deckId: String, val sourceRevision: String) : GameAction
    @Serializable @SerialName("transferPreparedCard") data class TransferPreparedCard(val deckId: String,
        val cardId: String, val cardIndex: String, val source: SavedDeckSection) : GameAction
    @Serializable @SerialName("sendChat") data class SendChat(val text: String) : GameAction
    @Serializable @SerialName("setPodGameBasics") data class SetPodGameBasics(val deckId: String,
        val basics: PodDeckBasics, val basicDefinitions: List<SubmittedDefinition>,
        val section: SavedDeckSection = SavedDeckSection.MAINBOARD) : GameAction
}
@Serializable sealed interface GameClientMessage
@Serializable @SerialName("hello") data class GameHello(val processId: String, val installationId: String,
    val protocolMajor: Int = 1, val minMinor: Int = 0, val maxMinor: Int = GAME_PROTOCOL_MINOR,
    val engineVersions: List<Int> = listOf(1), val readFormats: List<Int> = listOf(1),
    val gameplayRulesVersion: Int = 1) : GameClientMessage
@Serializable @SerialName("openStream") data class OpenGameStream(val gameId: String, val streamId: String) : GameClientMessage
@Serializable @SerialName("resumeStream") data class ResumeGameStream(val gameId: String, val streamId: String,
    val requestIds: List<String>) : GameClientMessage
/** Subscribes to [gameId]'s revisions after [revision] and its head. [engine] (switch-review #11): the open game, whose
 * head carries its engine state and whose revisions their engine events; otherwise (a game in the list) the revisions
 * carry only their summary, chat and preparation, and every head is a summary. Sent again it switches between the two. */
@Serializable @SerialName("subscribe") data class SubscribeGame(val gameId: String, val revision: String = "0",
    val readFormats: List<Int> = listOf(1), val engine: Boolean = true) : GameClientMessage
@Serializable @SerialName("command") data class GameCommand(val requestId: String, val scope: GameScope,
    val action: GameAction, val streamId: String, val generation: String, val commandVersion: Int = 1,
    val invocationId: String? = null,
    /** Only with [GameAction.Ready]: the player's dungeon cards outside the game ([org.asyncttwiz.protocol.game.OutsideCards]). */
    val inlineDefinitions: List<SubmittedDefinition> = emptyList()) : GameClientMessage
@Serializable @SerialName("ping") data class GamePing(val correlationId: String, val presence: GamePresence? = null) : GameClientMessage

@Serializable sealed interface GameServerMessage
@Serializable @SerialName("welcome") data class GameWelcome(val protocolMajor: Int = 1, val minor: Int = GAME_PROTOCOL_MINOR,
    val engineVersion: Int = 1, val readFormat: Int = 1, val heartbeatSeconds: Int = 30,
    val commandBytes: Int = GameWire.MAX_MESSAGE_BYTES, val pendingRequests: Int = 1024,
    val gameplayRulesVersion: Int = 1) : GameServerMessage
@Serializable @SerialName("upgradeRequired") data class GameUpgradeRequired(val protocolMajor: Int = 1) : GameServerMessage
@Serializable data class GameRequestStatus(val requestId: String, val status: String, val result: GameResult? = null)
@Serializable @SerialName("streamReady") data class GameStreamReady(val gameId: String, val streamId: String,
    val generation: String, val statuses: List<GameRequestStatus> = emptyList(),
    val ended: GameSummary? = null) : GameServerMessage
@Serializable @SerialName("receipt") data class GameReceipt(val requestId: String, val scope: GameScope,
    val ingressOrdinal: String) : GameServerMessage
@Serializable @SerialName("result") data class GameResult(val requestId: String, val scope: GameScope,
    val outcome: String, val reason: String? = null, val ingressOrdinal: String,
    val commitRevision: String? = null, val createdIds: List<String> = emptyList()) : GameServerMessage
@Serializable @SerialName("admissionError") data class GameAdmissionError(val code: String,
    val retryable: Boolean, val requestId: String? = null, val gameId: String? = null) : GameServerMessage
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable @SerialName("head") data class GameHead(val gameId: String, val revision: String,
    val summary: GameSummary, val minimumResumableRevision: String = "0", val engineVersion: Int = 1,
    val readFormat: Int = 1, val preparation: LobbyPreparation? = null, val play: PlayState? = null,
    val chat: List<ChatMessage> = emptyList(),
    val chatSync: ChatSync? = null,
    /** Authorized caller's private pod deck name; not replay state or revision data. */
    val ownPodDeckName: String? = null,
    /** Socket heads only; omitted otherwise. Not replay state or revision data. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val opponentPresence: OpponentPresence? = null,
    /** I-D54: the play's engine state at its live line's last event, for a play whose state is the engine's. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val engine: EngineSnapshot? = null) : GameServerMessage
/** A play's engine state as a device loads it (I-D54 phase 4): its card [definitions]
 * ([org.asyncttwiz.protocol.game.EngineJson.encodeDefinitions]), its [rootId], the live line's last event [headId] and
 * the [state] there ([org.asyncttwiz.protocol.game.EngineJson.encodeModel]). */
@Serializable data class EngineSnapshot(val definitions: String, val rootId: String, val headId: String, val state: String,
    /** I-D56: each player's local head by account (the assumer's indicator shows how far the other has confirmed). */
    val localHeads: Map<String, String> = emptyMap(),
    /** The reader's own play settings for this game (I-D56 §13); never the other player's. */
    val settings: org.asyncttwiz.protocol.game.PlaySettings? = null,
    /** The open undo request, if any (I-D56 §10). */
    val rewindRequest: EngineRewindRequest? = null,
    /** The play's line counter: a branch starts line [lines] + 1 (I-D56 §9). */
    val lines: Int = 0,
    /** I-D56 §7: when a local head is behind the head, the earliest of them ([baseId]), the state there ([base]) and the
     * main line's events after it up to the head ([events], oldest first): a device shows the game from its own local
     * head, buffers the events past it, and can go back to the other player's (where a branch may start). */
    val baseId: String? = null,
    val base: String? = null,
    val events: List<String> = emptyList(),
    /** HIS-106: when another player's branch took the reader's local head back to the branch point (it is then behind),
     * the events of the discarded line they had been shown, from the branch point on, oldest first: their device opens
     * the game where they left it and shows the take-back ([org.asyncttwiz.protocol.game.GameDevice.load]). HIS-108: or,
     * with [undoneBy], the events the other player's undo took back that the reader had been shown, from the undo's point
     * (the reader's local head) on. */
    val takenBack: List<String> = emptyList(),
    /** Each player's current response mode by account (owner 2026-11-06: kept per game outside undo and history; the
     * other player's device assumes a pass wherever the player's own mode would make it). */
    val modes: Map<String, org.asyncttwiz.protocol.game.ResponseMode> = emptyMap(),
    /** HIS-108: who undid [takenBack] (an undo, not a branch); null otherwise. */
    val undoneBy: String? = null)
/**
 * HIS-111, HIS-099 (owner 2026-11-07: the timeline and the Undo dialog reach the game's first event): a page of a play's
 * main line ending at the event [through], read on demand (`GET /v1/games/{id}/timeline?through=`): the checkpoint above
 * it, [baseId] (the game's root before its first event when [fromStart]) with its encoded state [base]
 * ([org.asyncttwiz.protocol.game.EngineJson.encodeModel]), and the encoded events after it up to [through], oldest first
 * (at most [org.asyncttwiz.protocol.game.GameHistory.CHECKPOINT_EVERY]): the state at any of them is the base with the
 * events before it applied, never a replay from the game's start.
 */
@Serializable data class EngineHistoryPage(val through: String, val baseId: String, val base: String, val events: List<String>,
    val fromStart: Boolean)
/** An open undo request (I-D56 §10): [by] asks to take the main line back to just after [target], with [message]. */
@Serializable data class EngineRewindRequest(val by: String, val target: String, val message: String = "")
/** I-D56: what an engine revision changed beyond [GameRevision.engineEvent]: the events after the first ([events]), a
 * rewind's [target] (devices take the line back to it before applying [events]), the local heads, the line counter and
 * the open undo request. */
@Serializable data class EngineUpdate(val events: List<String> = emptyList(), val target: String? = null,
    val localHeads: Map<String, String> = emptyMap(), val lines: Int = 0, val rewindRequest: EngineRewindRequest? = null,
    /** The assumed event a submission confirmed (it made no event of its own). */
    val confirmed: String? = null,
    /** Server only, removed before a revision is sent: per account, whether they have the move: the game waits for them
     * (TRN-058, I-D57). */
    val moves: Map<String, Boolean>? = null,
    /** Server only, removed before a revision is sent: per account, their last-activity time (I-D56 §7). */
    val activity: Map<String, String>? = null,
    /** Whose undo a rewind ([target]) is: the requester's when approved (a rewind adds no event to tell it by). */
    val rewoundBy: String? = null,
    /** Each player's current response mode by account, as after this change ([EngineSnapshot.modes]): a player's change
     * of it reaches the other player's device at once. */
    val modes: Map<String, org.asyncttwiz.protocol.game.ResponseMode> = emptyMap())
@Serializable @SerialName("revision") data class GameRevision(val gameId: String, val baseRevision: String,
    val resultRevision: String, val requestId: String, val author: String, val scope: GameScope,
    val summary: GameSummary, val preparation: LobbyPreparation? = null, val play: PlayState? = null,
    val chatMessage: ChatMessage? = null,
    /** I-D54: the engine event this revision accepted, in the play's engine format
     * ([org.asyncttwiz.protocol.game.EngineJson.encodeEvent], definitions by reference to [GameHead.engine]'s). */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val engineEvent: String? = null,
    /** I-D56: the rest of what an engine revision changed. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val engine: EngineUpdate? = null) : GameServerMessage
/** The opponent's presence changed (opened/left the game, or came back from idle). */
@Serializable @SerialName("presence") data class GamePresenceUpdate(val gameId: String, val opponent: OpponentPresence) : GameServerMessage
@Serializable @SerialName("pong") data class GamePong(val correlationId: String) : GameServerMessage
@Serializable data class GameRevisions(val revisions: List<GameRevision>, val next: String? = null)
@Serializable data class LookupGameResults(val requestIds: List<String>)
@Serializable data class GameResults(val statuses: List<GameRequestStatus>)

/** I-D56: the actions of a play whose state is the engine's besides [GameAction.Submit] (local heads, rewinds, undo
 * requests, private settings). */
val GameAction.isEngine: Boolean get() = this is GameAction.EngineSeen || this is GameAction.EngineRewind ||
    this is GameAction.EngineRequestRewind || this is GameAction.EngineAnswerRewind || this == GameAction.EngineCancelRewind ||
    this is GameAction.EngineSettings

/** Future actions extend this same typed envelope, not a second lifecycle/gameplay queue. */
object GameWire {
    /** The largest game socket message either way (I-D14). */
    const val MAX_MESSAGE_BYTES = 8 * 1024 * 1024
    /** The most engine inputs, and the most table changes, one [GameAction.Submit] may hold. */
    const val MAX_SUBMITTED = 1024
    val json = DeckJson.format
    /** Reads what the server stored (a play, a revision, a request's result): a row written before the old model's fields
     * went (engine-responsiveness R4d) reads without them; anything else unknown is still refused (SYS-052). */
    fun <T> stored(deserializer: kotlinx.serialization.DeserializationStrategy<T>, text: String): T =
        json.decodeFromJsonElement(deserializer, withoutRetired(json.parseToJsonElement(text)))

    /** The old model's fields of stored plays (also a revision's [GameRevision.play]), revisions and results. */
    private val RETIRED = setOf("currentNodeId", "engineVersion", "readFormat", "currentGroup", "annotations", "currentBranchId",
        "mutation", "group", "treeDelta", "targetNodeId")
    private fun withoutRetired(element: JsonElement): JsonElement = if (element !is JsonObject) element
        else JsonObject(element.filterKeys { it !in RETIRED }.mapValues { (key, value) -> if (key == "play") withoutRetired(value) else value })
    fun decodeClient(text: String): GameClientMessage {
        require(text.encodeToByteArray().size <= MAX_MESSAGE_BYTES)
        StrictGameJson.check(text)
        return json.decodeFromString(text)
    }
    fun encode(message: GameServerMessage): String = json.encodeToString(message)
    fun ordinal(value: String): Long = ScalarCodecs.decodeRevision(JsonPrimitive(value).toString())
    fun valid(command: GameCommand): Boolean = runCatching {
        // A Ready carries its player's dungeon cards outside the game (OutsideCards), nothing else carries definitions
        require(command.commandVersion == 1 && (command.inlineDefinitions.isEmpty() || command.action == GameAction.Ready &&
            command.inlineDefinitions.size <= org.asyncttwiz.protocol.game.OutsideCards.MAX))
        require(listOfNotNull(command.requestId, command.scope.gameId, command.scope.preparationId,
            command.scope.invitationId, command.scope.playId, command.streamId, command.invocationId).all(DeckValidation::uuid))
        if (command.action == GameAction.DeleteGame || command.action is GameAction.SendChat) {
            require(command.scope.playId != null && command.scope.preparationId == null && command.scope.invitationId == null)
            if (command.action is GameAction.SendChat) require(ChatValidation.text(command.action.text))
        } else if (command.action is GameAction.Submit) {
            require(command.scope.playId != null && command.scope.preparationId == null && command.scope.invitationId == null)
            val submission = command.action.submission
            require(DeckValidation.uuid(submission.id) && DeckValidation.uuid(submission.parentId))
            require(submission.acts.size <= MAX_SUBMITTED && submission.table.size <= MAX_SUBMITTED)
            require(submission.acts.isNotEmpty() || submission.table.isNotEmpty())
        } else if (command.action is GameAction.SetAssumeReveals) {
            require(command.scope.playId == null && command.scope.preparationId == null && command.scope.invitationId == null)
        } else if (command.action.isEngine) {
            require(command.scope.playId != null && command.scope.preparationId == null && command.scope.invitationId == null)
            when (val action = command.action) {
                is GameAction.EngineSeen -> require(DeckValidation.uuid(action.eventId))
                is GameAction.EngineRewind -> require(DeckValidation.uuid(action.target))
                is GameAction.EngineRequestRewind -> require(DeckValidation.uuid(action.target) && action.message.length <= 500)
                else -> {}
            }
        } else {
            require(command.scope.preparationId != null && command.scope.playId == null)
            when (val action = command.action) {
                is GameAction.SetPodGameBasics -> {
                    require(command.scope.invitationId == null && DeckValidation.uuid(action.deckId))
                    require(action.basics.valid && action.basics.total <= DeckValidation.MAX_CARDS)
                    require(PodGamePreparation.basicDefinitions(action.basicDefinitions) != null)
                }
                is GameAction.SelectDeck -> {
                    require(command.scope.invitationId == null && DeckValidation.uuid(action.deckId))
                    require(ordinal(action.sourceRevision) > 0)
                }
                is GameAction.TransferPreparedCard -> {
                    require(command.scope.invitationId == null && DeckValidation.uuid(action.deckId) && DeckValidation.uuid(action.cardId))
                    ordinal(action.cardIndex)
                }
                is GameAction.SetStarter -> {
                    require(command.scope.invitationId == null)
                    require(action.choice.accountId == null || DeckValidation.uuid(action.choice.accountId))
                }
                is GameAction.SetAlternateStarter, is GameAction.ShowDeck -> require(command.scope.invitationId == null)
                GameAction.Ready, GameAction.WithdrawReady, GameAction.CancelSetup -> require(command.scope.invitationId == null)
                else -> require(command.scope.invitationId != null)
            }
        }
        ordinal(command.generation)
    }.isSuccess
    /** Auth, connection/fence and request identity are not semantic intent. Defaults are expanded. */
    fun canonical(command: GameCommand): String = DeckJson.canonicalElement(JsonObject(
        json.encodeToJsonElement(command).jsonObject.filterKeys { it !in setOf("requestId", "streamId", "generation") }))
    fun digest(command: GameCommand) = DeckJson.intent("gameCommand", canonical(command))
}

/** Reject duplicates before the library builds a map. The decoder checks grammar/types/unknowns. */
private object StrictGameJson {
    fun check(text: String) {
        var index = 0
        fun space() { while (index < text.length && text[index].isWhitespace()) index++ }
        fun string(): String {
            val start = index++
            while (index < text.length) {
                when (text[index++]) {
                    '\\' -> index++
                    '"' -> return Json.decodeFromString(text.substring(start, index))
                }
            }
            error("Unterminated string")
        }
        fun value(depth: Int): Unit {
            require(depth <= 32); space(); require(index < text.length)
            when (text[index]) {
                '{' -> {
                    index++; space(); val keys = mutableSetOf<String>()
                    if (index < text.length && text[index] == '}') { index++; return }
                    while (true) {
                        space(); require(index < text.length && text[index] == '"')
                        require(keys.add(string())) { "Duplicate key" }; space()
                        require(index < text.length && text[index++] == ':'); value(depth + 1); space()
                        require(index < text.length)
                        if (text[index++] == '}') break
                        require(text[index - 1] == ',')
                    }
                }
                '[' -> {
                    index++; space()
                    if (index < text.length && text[index] == ']') { index++; return }
                    while (true) {
                        value(depth + 1); space(); require(index < text.length)
                        if (text[index++] == ']') break
                        require(text[index - 1] == ',')
                    }
                }
                '"' -> string()
                else -> {
                    val start = index
                    while (index < text.length && text[index] !in ",]} \t\r\n") index++
                    require(index > start)
                }
            }
        }
        value(0); space(); require(index == text.length)
    }
}
