package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** No implicit Random roll/default: a lobby choice names a participant or explicitly selects Random. */
@Serializable data class StarterChoice(val accountId: String? = null) // null means Random
@Serializable enum class PlayZone { LIBRARY, HAND, BATTLEFIELD, GRAVEYARD, EXILE, STACK }
@Serializable enum class CounterColor { RED, BLUE, GREEN, YELLOW, PURPLE, GRAY }
/** [status] is the player's short shared status (rules v26), e.g. "Wait"; empty is omitted on the wire. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class PlayPlayer(val accountId: String, val life: String = "20",
    val counters: Map<CounterColor, String> = emptyMap(),
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val status: String = "")

/** Rules v26: a player's own short status shown in a bubble by their life total. */
object PlayerStatus {
    const val MAX_LENGTH = 24
    /** Both players start each new play with this exact status until they set another one. */
    const val OPENING = "Mulliganing"
    /** Empty (cleared), or already trimmed, at most [MAX_LENGTH] characters, one line without control characters. */
    fun valid(text: String): Boolean = text == text.trim() && text.length <= MAX_LENGTH &&
        text.none { it.isISOControl() } && DeckValidation.text(text)
    fun opening(board: BoardState, accountId: String?): Boolean =
        board.players.any { it.accountId == accountId && it.status == OPENING }
    fun anyOpening(board: BoardState): Boolean = board.players.any { it.status == OPENING }
}
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class PlayCard(val id: String, val definition: String, val owner: String,
    val containerId: String, val faceId: String, val covered: Boolean = true, val tapped: Boolean = false,
    val corner: String = "", val counters: Map<CounterColor, String> = emptyMap(), val note: String = "",
    val token: Boolean = false, val placement: PlayPlacement? = null,
    /** Old snapshots used covered=true for hidden-zone defaults, even after entering public zones. */
    val explicitlyCovered: Boolean = false,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val knownToAll: Boolean = false,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val damage: String = "",
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val eotCorner: String = "",
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val eotNote: String = "") {
    val isFaceDown: Boolean get() = covered && explicitlyCovered
}
/** Library order is top first. Other empty zones already have stable identities for later commands. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class PlayContainer(val id: String, val zone: PlayZone, val accountId: String? = null,
    val cards: List<String> = emptyList(),
    /** Library cards are knownTop + unknown middle + knownBottom, all top first.
     * Old snapshots have no knowledge metadata and therefore start entirely unknown. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val knownTop: List<String> = emptyList(),
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val knownBottom: List<String> = emptyList(),
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val keepTopRevealed: Boolean = false,
    /** Rules v23: a player's lazily-created Temp pile. It is a LIBRARY-zone container (hidden faces,
     * unknown order after shuffle) but never "the" Library: owner lookups must exclude it. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val temp: Boolean = false) {
    val unknownMiddle: List<String> get() = cards.subList(knownTop.size, cards.size - knownBottom.size)
}

/** The player's single Temp pile. Created lazily by the first move into its deterministic ID, so old
 * games gain it without any board migration and retained events keep replaying unchanged. */
object TempPiles {
    fun containerId(playId: String, accountId: String): String = CommandIds.uuidV5(playId, "container/$accountId/TEMP")
    /** Owner -> deterministic Temp pile ID, whether created yet or not. */
    fun containerIds(state: PlayState): Map<String, String> = if (!DeckValidation.uuid(state.id)) emptyMap()
        else state.board.players.associate { it.accountId to containerId(state.id, it.accountId) }
    fun container(board: BoardState, accountId: String?): PlayContainer? =
        board.containers.firstOrNull { it.temp && it.accountId == accountId }

    /** The board with the player's (possibly not yet created) empty Temp pile, for planning moves. */
    fun ensure(board: BoardState, playId: String, accountId: String): BoardState =
        if (container(board, accountId) != null) board
        else board.copy(containers = board.containers + PlayContainer(containerId(playId, accountId), PlayZone.LIBRARY, accountId, temp = true))

    /** The state a move into a missing Temp pile executes against. The recorded delta is still taken
     * from the original state, so it creates the container and its exact inverse removes it. */
    internal fun prepare(state: PlayState, command: GameCommand): PlayState {
        val move = (command.action as? GameAction.Play)?.action as? PlayAction.MoveCard ?: return state
        val target = move.destination.containerId
        if (state.board.containers.any { it.id == target }) return state
        if (state.board.cards.singleOrNull { it.id == move.cardId }?.token != false) return state
        val owner = containerIds(state).entries.firstOrNull { it.value == target }?.key ?: return state
        return state.copy(board = ensure(state.board, state.id, owner))
    }

    /** ONE batch moving the whole pile, in its current order, onto the owner's Library top or bottom. */
    fun allToLibrary(board: BoardState, tempId: String, bottom: Boolean): List<PlayAction.MoveCard> {
        val temp = board.containers.single { it.id == tempId && it.temp }
        val library = board.zoneContainer(temp.accountId, PlayZone.LIBRARY) ?: return emptyList()
        return LibraryActions.moveCaptured(board, temp.cards,
            MoveDestination(library.id, if (bottom) MoveInsertion.BOTTOM else MoveInsertion.TOP))
    }
}

/** A player's ordinary zone container; never the Temp pile, which shares the LIBRARY zone. */
fun BoardState.zoneContainer(accountId: String?, zone: PlayZone): PlayContainer? =
    containers.firstOrNull { it.accountId == accountId && it.zone == zone && !it.temp }

/** Ordinary presentation only; explicit logged inspections/contents displays are separate grants. */
object CardVisibility {
    fun isVisibleFaceUp(card: PlayCard, zone: PlayZone, holder: String?, viewer: String): Boolean = when (zone) {
        PlayZone.LIBRARY -> false
        PlayZone.HAND -> holder == viewer
        else -> !card.isFaceDown
    }

    fun isVisibleFaceUp(board: BoardState, card: PlayCard, viewer: String): Boolean {
        val container = board.containers.singleOrNull { it.id == card.containerId } ?: return false
        if (container.zone == PlayZone.LIBRARY && container.keepTopRevealed && container.cards.firstOrNull() == card.id) return true
        if (card.knownToAll) return true
        if (board.contentsDisplays.any { it.panelVersion >= 3 && it.containerId == card.containerId &&
                card.id in it.cardIds && it.faceVisibleTo(card.id, viewer) }) return true
        if (board.stackEffects.any { it.sourceId == card.id && it.frozen == null &&
                (container.zone == PlayZone.HAND && container.accountId == card.owner ||
                    container.zone == PlayZone.LIBRARY && it.revealsLibrarySource) &&
                it.id in board.containers.singleOrNull { c -> c.zone == PlayZone.STACK }?.cards.orEmpty() }) return true
        return isVisibleFaceUp(card, container.zone, container.accountId, viewer)
    }

    fun viewers(board: BoardState, card: PlayCard): List<String> = board.players.map { it.accountId }
        .filter { isVisibleFaceUp(board, card, it) }.sorted()
}
@Serializable data class TurnState(val accountId: String, val number: String = "1",
    val phase: String = "BEGINNING", val step: String = "UNTAP")
@Serializable data class BoardState(val players: List<PlayPlayer>, val cards: List<PlayCard>,
    val containers: List<PlayContainer>, val turn: TurnState, val schemaVersion: Int = 1,
    val contentsDisplays: List<ContentsDisplay> = emptyList(), val links: List<PlayLink> = emptyList(),
    val combat: CombatArea? = null, val stackEffects: List<StackEffect> = emptyList(),
    val projectionLinks: List<ProjectionLink> = emptyList(),
    val resultSuggestion: ResultSuggestion? = null,
    /** Entry metadata keyed by Stack card/occurrence ID, never a field on its real source. */
    val stackTextMarks: Map<String, StackTextMarks> = emptyMap(),
    /** Null retains the legacy meaning: attention follows the turn player. */
    val attentionHolder: String? = null) {
    val effectiveAttentionHolder: String get() = attentionHolder ?: turn.accountId
}
@Serializable data class InitialShuffle(val accountId: String, val seed: String, val order: List<String>,
    val algorithm: String = "shuffle-v1")
@Serializable data class InitialStarter(val choice: StarterChoice, val accountId: String,
    val seed: String? = null, val algorithm: String? = null)
@Serializable data class PlayRoot(val id: String, val branchId: String, val parentId: String? = null)
/** One accepted incarnation. Initial outcomes/root are retained, not regenerated by reads/replay. */
@Serializable data class PlayState(val gameId: String, val id: String, val preparationRound: String,
    val startRequestId: String, val root: PlayRoot, val currentNodeId: String, val board: BoardState,
    val definitions: List<SubmittedDefinition>, val shuffles: List<InitialShuffle>, val starter: InitialStarter,
    val engineVersion: Int = 1, val readFormat: Int = 1, val currentGroup: GameplayGroup? = null,
    val annotations: EventAnnotationState = EventAnnotationState(),
    val currentBranchId: String = root.branchId,
    val librarySecret: String? = null)

object StartRules {
    fun change(game: GameSummary, current: LobbyPreparation, actor: String, scope: GameScope,
        action: GameAction): PreparationChange {
        fun reject(reason: String) = PreparationChange(reason = reason)
        if (game.lifecycle != GameLifecycle.LOBBY) return reject("lifecycleEnded")
        if (scope.gameId != game.id || scope.preparationId != game.preparationRound || scope.playId != null ||
            current.gameId != game.id || current.roundId != game.preparationRound) return reject("inapplicable")
        if (actor != game.inviter.accountId && (actor != game.recipient.accountId || game.invitationState != InvitationState.ACCEPTED))
            return reject("accessDenied")
        val player = current.players.single { it.accountId == actor }
        return when (action) {
            is GameAction.SetStarter -> {
                if (action.choice.accountId != null && action.choice.accountId !in current.players.map { it.accountId })
                    reject("inapplicable")
                else PreparationChange(current.copy(starter = action.choice, players = current.players.map {
                    if (it.accountId == actor && current.startingChoice != action.choice) it.copy(ready = false) else it
                }))
            }
            GameAction.Ready -> when {
                player.deck == null -> reject("deckRequired")
                PodGamePreparation.readyProblem(current, actor) != null -> reject(PodGamePreparation.readyProblem(current, actor)!!)
                else -> PreparationChange(current.copy(players = current.players.map {
                    if (it.accountId == actor) it.copy(ready = true) else it
                }))
            }
            GameAction.WithdrawReady -> PreparationChange(current.copy(players = current.players.map {
                if (it.accountId == actor) it.copy(ready = false) else it
            }))
            else -> error("Not a readiness action")
        }
    }
    fun canStart(game: GameSummary, preparation: LobbyPreparation) = game.lifecycle == GameLifecycle.LOBBY &&
        game.invitationState == InvitationState.ACCEPTED &&
        preparation.players.size == 2 && preparation.players.all { it.ready && it.deck != null }

    /** Inputs are acquired independently by the server only after the readiness transition applies. */
    fun initialize(game: GameSummary, preparation: LobbyPreparation, requestId: String,
        librarySeeds: Map<String, String>, starterSeed: String? = null, librarySecret: String? = null): PlayState {
        librarySecret?.let(RandomV1::decodeSeed)
        require(canStart(game, preparation) && preparation.gameId == game.id && preparation.roundId == game.preparationRound)
        val players = preparation.players.sortedBy { it.accountId }
        require(players.all { DeckValidation.quantities(it.deck!!.entries.map { entry -> entry.quantity }) })
        require(librarySeeds.keys == players.map { it.accountId }.toSet())
        val choice = preparation.startingChoice
        val starter = if (choice.accountId == null) InitialStarter(choice,
            RandomV1.starter(players.map { it.accountId }, requireNotNull(starterSeed)), starterSeed, "starter-v1")
        else {
            require(starterSeed == null && choice.accountId in players.map { it.accountId })
            InitialStarter(choice, choice.accountId)
        }
        val playId = CommandIds.uuidV5(requestId, "play-incarnation")
        val root = PlayRoot(CommandIds.uuidV5(requestId, "board-node"), CommandIds.uuidV5(requestId, "initial-branch"))
        val definitions = preparation.definitions.associateBy { it.digest }
        val cards = mutableListOf<PlayCard>(); val containers = mutableListOf<PlayContainer>()
        val shuffles = mutableListOf<InitialShuffle>()
        for (player in players) {
            val deck = player.deck!!
            val library = CommandIds.uuidV5(playId, "container/${player.accountId}/LIBRARY")
            val members = deck.entries.filter { it.section == SavedDeckSection.MAINBOARD }.flatMap { entry ->
                val definition = definitions.getValue(entry.definition).definition
                val face = definition.faces.first()
                List(entry.quantity) { offset ->
                    PlayCard(deck.card(entry, offset).id, entry.definition, player.accountId, library,
                        face.faceId, corner = face.defaultCorner()).also(cards::add).id
                }
            }
            val seed = librarySeeds.getValue(player.accountId)
            val order = RandomV1.shuffle(members, seed)
            shuffles.add(InitialShuffle(player.accountId, seed, order))
            for (zone in PlayZone.entries.filter { it != PlayZone.STACK }) containers.add(PlayContainer(
                if (zone == PlayZone.LIBRARY) library else CommandIds.uuidV5(playId, "container/${player.accountId}/${zone.name}"),
                zone, player.accountId, if (zone == PlayZone.LIBRARY) order else emptyList()))
        }
        containers.add(PlayContainer(CommandIds.uuidV5(playId, "container/STACK"), PlayZone.STACK))
        val needed = cards.map { it.definition }.toSet()
        return PlayState(game.id, playId, preparation.roundId, requestId, root, root.id,
            BoardState(players.map { PlayPlayer(it.accountId, status = PlayerStatus.OPENING) }, cards.sortedBy { it.id }, containers, TurnState(starter.accountId)),
            needed.sorted().map { definitions.getValue(it) }, shuffles, starter, librarySecret = librarySecret)
    }
}
