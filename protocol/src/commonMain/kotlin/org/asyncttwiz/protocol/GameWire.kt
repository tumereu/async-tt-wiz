package org.asyncttwiz.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Highest recorded-transition rules understood by this build; also used when recording new events. */
const val CURRENT_GAMEPLAY_RULES_VERSION = 27

@Serializable data class GameScope(val gameId: String, val preparationId: String? = null,
    val invitationId: String? = null, val playId: String? = null)
@Serializable data class Continuation(val nodeId: String, val explicit: Boolean)
@Serializable sealed interface GameAction {
    @Serializable @SerialName("undo") data object Undo : GameAction
    @Serializable @SerialName("redo") data object Redo : GameAction
    @Serializable @SerialName("acceptResult") data class AcceptResult(val suggestionId: String) : GameAction
    @Serializable @SerialName("play") data class Play(val action: PlayAction) : GameAction
    @Serializable @SerialName("playBatch") data class PlayBatch(val actions: List<PlayAction>) : GameAction
    @Serializable @SerialName("acceptInvitation") data object AcceptInvitation : GameAction
    @Serializable @SerialName("declineInvitation") data object DeclineInvitation : GameAction
    @Serializable @SerialName("reinvite") data object Reinvite : GameAction
    @Serializable @SerialName("cancelSetup") data object CancelSetup : GameAction
    @Serializable @SerialName("setStarter") data class SetStarter(val choice: StarterChoice) : GameAction
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
    val protocolMajor: Int = 1, val minMinor: Int = 0, val maxMinor: Int = 0,
    val engineVersions: List<Int> = listOf(1), val readFormats: List<Int> = listOf(1),
    val gameplayRulesVersion: Int = 1) : GameClientMessage
@Serializable @SerialName("openStream") data class OpenGameStream(val gameId: String, val streamId: String) : GameClientMessage
@Serializable @SerialName("resumeStream") data class ResumeGameStream(val gameId: String, val streamId: String,
    val requestIds: List<String>) : GameClientMessage
@Serializable @SerialName("subscribe") data class SubscribeGame(val gameId: String, val revision: String = "0",
    val readFormats: List<Int> = listOf(1)) : GameClientMessage
@Serializable @SerialName("command") data class GameCommand(val requestId: String, val scope: GameScope,
    val action: GameAction, val streamId: String, val generation: String, val commandVersion: Int = 1,
    val invocationId: String? = null, val continuation: Continuation? = null,
    val inlineDefinitions: List<SubmittedDefinition> = emptyList()) : GameClientMessage
@Serializable @SerialName("ping") data class GamePing(val correlationId: String, val presence: GamePresence? = null) : GameClientMessage

@Serializable sealed interface GameServerMessage
@Serializable @SerialName("welcome") data class GameWelcome(val protocolMajor: Int = 1, val minor: Int = 0,
    val engineVersion: Int = 1, val readFormat: Int = 1, val heartbeatSeconds: Int = 30,
    val commandBytes: Int = 1048576, val pendingRequests: Int = 1024,
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
@Serializable @SerialName("head") data class GameHead(val gameId: String, val revision: String,
    val summary: GameSummary, val minimumResumableRevision: String = "0", val engineVersion: Int = 1,
    val readFormat: Int = 1, val preparation: LobbyPreparation? = null, val play: PlayState? = null,
    val chat: List<ChatMessage> = emptyList(), val retainedTreeRequired: Boolean = false,
    val chatSync: ChatSync? = null, val boundedEndpoint: Boolean = false,
    /** Authorized caller's private pod deck name; not replay state or revision data. */
    val ownPodDeckName: String? = null) : GameServerMessage
@Serializable @SerialName("revision") data class GameRevision(val gameId: String, val baseRevision: String,
    val resultRevision: String, val requestId: String, val author: String, val scope: GameScope,
    val summary: GameSummary, val preparation: LobbyPreparation? = null, val play: PlayState? = null,
    val mutation: GameplayMutation? = null, val group: GameplayGroup? = null,
    val chatMessage: ChatMessage? = null, val treeDelta: GameplayTreeDelta? = null) : GameServerMessage
@Serializable @SerialName("pong") data class GamePong(val correlationId: String) : GameServerMessage
@Serializable data class GameRevisions(val revisions: List<GameRevision>, val next: String? = null)
@Serializable data class LookupGameResults(val requestIds: List<String>)
@Serializable data class GameResults(val statuses: List<GameRequestStatus>)

/** Future actions extend this same typed envelope, not a second lifecycle/gameplay queue. */
object GameWire {
    val json = DeckJson.format
    fun decodeClient(text: String): GameClientMessage {
        require(text.encodeToByteArray().size <= 1048576)
        StrictGameJson.check(text)
        return json.decodeFromString(text)
    }
    fun encode(message: GameServerMessage): String = json.encodeToString(message)
    fun ordinal(value: String): Long = ScalarCodecs.decodeRevision(JsonPrimitive(value).toString())
    fun valid(command: GameCommand): Boolean = runCatching {
        require(command.commandVersion == 1 && command.inlineDefinitions.isEmpty())
        command.continuation?.let {
            require(command.action.isPlay && it.explicit && DeckValidation.uuid(it.nodeId))
        }
        require(listOfNotNull(command.requestId, command.scope.gameId, command.scope.preparationId,
            command.scope.invitationId, command.scope.playId, command.streamId, command.invocationId).all(DeckValidation::uuid))
        if (command.action == GameAction.DeleteGame || command.action is GameAction.SendChat) {
            require(command.scope.playId != null && command.scope.preparationId == null && command.scope.invitationId == null)
            if (command.action is GameAction.SendChat) require(ChatValidation.text(command.action.text))
        } else if (command.action is GameAction.AcceptResult) {
            require(command.scope.playId != null && command.scope.preparationId == null && command.scope.invitationId == null)
            require(DeckValidation.uuid(command.action.suggestionId))
        } else if (command.action.isPlay || command.action == GameAction.Undo || command.action == GameAction.Redo) {
            require(command.scope.playId != null && command.scope.preparationId == null && command.scope.invitationId == null)
            if (command.action is GameAction.Play) require(GameplayRules.valid(command.action.action))
            if (command.action is GameAction.PlayBatch) require(command.action.actions.size in 1..100 &&
                command.action.actions.all(GameplayRules::valid))
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
