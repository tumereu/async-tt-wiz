package org.asyncttwiz.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Highest recorded-transition rules understood by this build; also used when recording new events. */
const val CURRENT_GAMEPLAY_RULES_VERSION = 36
/** The socket protocol version of this build (SYS-033: one compatible set of apps and server). Apps offer it
 *  as [GameHello.maxMinor] and the server welcomes with its own: a lower offer gets [GameUpgradeRequired] (the app
 *  is older), and an app welcomed with a lower minor waits for the server update. Bump it with any socket wire change. */
const val GAME_PROTOCOL_MINOR = 7

@Serializable enum class PresenceState { ONLINE, IDLE, OFFLINE }
/** The opponent's presence in this game. Ages are whole seconds as of sending, so the clocks never need to agree.
 *  [idleSeconds]: since their last input while the game is open (ONLINE/IDLE only).
 *  [lastSeenSeconds]: since they last had the game open; null when they never have. */
@Serializable data class OpponentPresence(val state: PresenceState, val idleSeconds: Int? = null, val lastSeenSeconds: Int? = null)

@Serializable data class GameScope(val gameId: String, val preparationId: String? = null,
    val invitationId: String? = null, val playId: String? = null)
@Serializable data class Continuation(val nodeId: String, val explicit: Boolean)
@Serializable sealed interface GameAction {
    @Serializable @SerialName("undo") data object Undo : GameAction
    @Serializable @SerialName("redo") data object Redo : GameAction
    @Serializable @SerialName("acceptResult") data class AcceptResult(val suggestionId: String) : GameAction
    /** CR 104.3a (I-D52): the actor concedes and loses at once, no agreement needed: their loss is recorded as the play's
     * result (a waiting suggestion withdrawn or declined first) and the play ends as an accepted result ends it. */
    @Serializable @SerialName("concede") data object Concede : GameAction
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
    /** I-D56 §13: the actor's private play settings for this game (response mode, Always assume resolves, Autotap). */
    @Serializable @SerialName("engineSettings") data class EngineSettings(val settings: org.asyncttwiz.protocol.game.PlaySettings) : GameAction
    /** [tag] (rules v36): the Magic meaning of the event, made by the engine (D33); absent only for what a player may
     * send outside Automatum ([AutomatumOnly]). */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Serializable @SerialName("play") data class Play(val action: PlayAction,
        @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val tag: MtgTag? = null) : GameAction
    /** [tags] (rules v36): the tag of each member by index, as [Play.tag]; at most one per member. */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Serializable @SerialName("playBatch") data class PlayBatch(val actions: List<PlayAction>,
        @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val tags: List<MtgTag?> = emptyList()) : GameAction
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
/** [expectedSelection] (ordinary play only): the active line the client acted on, as the
 *  tree's selection control id ([GameplayTree.selectionKey]). It changes only when a continuation forks and
 *  becomes active, never by Undo/Redo or play on the same line. If it no longer matches, the action raced
 *  with a deliberate branch and is refused ("branched"), never moved to another line. Absent: no check. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable @SerialName("command") data class GameCommand(val requestId: String, val scope: GameScope,
    val action: GameAction, val streamId: String, val generation: String, val commandVersion: Int = 1,
    val invocationId: String? = null, val continuation: Continuation? = null,
    /** Only with [GameAction.Ready]: the player's dungeon cards outside the game ([org.asyncttwiz.protocol.game.OutsideCards]). */
    val inlineDefinitions: List<SubmittedDefinition> = emptyList(),
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val expectedSelection: String? = null) : GameClientMessage
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
    val commitRevision: String? = null, val createdIds: List<String> = emptyList(),
    val mutation: GameplayMutation? = null, val targetNodeId: String? = null) : GameServerMessage
@Serializable @SerialName("admissionError") data class GameAdmissionError(val code: String,
    val retryable: Boolean, val requestId: String? = null, val gameId: String? = null) : GameServerMessage
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable @SerialName("head") data class GameHead(val gameId: String, val revision: String,
    val summary: GameSummary, val minimumResumableRevision: String = "0", val engineVersion: Int = 1,
    val readFormat: Int = 1, val preparation: LobbyPreparation? = null, val play: PlayState? = null,
    val chat: List<ChatMessage> = emptyList(), val retainedTreeRequired: Boolean = false,
    val chatSync: ChatSync? = null, val boundedEndpoint: Boolean = false,
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
    /** The reader's own private play settings for this game (I-D56 §13); never the other player's. */
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
    val events: List<String> = emptyList())
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
    val rewoundBy: String? = null)
@Serializable @SerialName("revision") data class GameRevision(val gameId: String, val baseRevision: String,
    val resultRevision: String, val requestId: String, val author: String, val scope: GameScope,
    val summary: GameSummary, val preparation: LobbyPreparation? = null, val play: PlayState? = null,
    val mutation: GameplayMutation? = null, val group: GameplayGroup? = null,
    val chatMessage: ChatMessage? = null, val treeDelta: GameplayTreeDelta? = null,
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
    /** The largest game socket message either way (I-D14): an automated batch of [PlayBatches.MAX_AUTOMATED] members
     * and the revision that answers it, at a few KB per member. */
    const val MAX_MESSAGE_BYTES = 8 * 1024 * 1024
    /** The most engine inputs, and the most table changes, one [GameAction.Submit] may hold. */
    const val MAX_SUBMITTED = 1024
    val json = DeckJson.format
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
        command.continuation?.let {
            require(command.action.isPlay && it.explicit && DeckValidation.uuid(it.nodeId))
        }
        command.expectedSelection?.let { require(command.action.isPlay && command.continuation == null && DeckValidation.uuid(it)) }
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
        } else if (command.action.isEngine) {
            require(command.scope.playId != null && command.scope.preparationId == null && command.scope.invitationId == null)
            when (val action = command.action) {
                is GameAction.EngineSeen -> require(DeckValidation.uuid(action.eventId))
                is GameAction.EngineRewind -> require(DeckValidation.uuid(action.target))
                is GameAction.EngineRequestRewind -> require(DeckValidation.uuid(action.target) && action.message.length <= 500)
                else -> {}
            }
        } else if (command.action is GameAction.AcceptResult || command.action == GameAction.Concede) {
            require(command.scope.playId != null && command.scope.preparationId == null && command.scope.invitationId == null)
            if (command.action is GameAction.AcceptResult) require(DeckValidation.uuid(command.action.suggestionId))
        } else if (command.action.isPlay || command.action == GameAction.Undo || command.action == GameAction.Redo) {
            require(command.scope.playId != null && command.scope.preparationId == null && command.scope.invitationId == null)
            if (command.action is GameAction.Play) require(GameplayRules.valid(command.action.action) &&
                command.action.tag?.let(MtgTag::valid) != false)
            if (command.action is GameAction.PlayBatch) require(command.action.actions.size in 1..PlayBatches.limit(command.action) &&
                command.action.actions.all(GameplayRules::valid) && command.action.tags.size <= command.action.actions.size &&
                command.action.tags.all { it?.let(MtgTag::valid) != false })
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
