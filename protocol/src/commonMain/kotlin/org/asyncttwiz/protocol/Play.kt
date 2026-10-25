package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** No implicit Random roll/default: a lobby choice names a participant or explicitly selects Random. */
@Serializable data class StarterChoice(val accountId: String? = null) // null means Random
@Serializable enum class PlayZone { LIBRARY, HAND, BATTLEFIELD, GRAVEYARD, EXILE, STACK }
@Serializable enum class CounterColor { RED, BLUE, GREEN, YELLOW, PURPLE, GRAY }
@Serializable data class PlayPlayer(val accountId: String, val life: String = "20",
    val counters: Map<CounterColor, String> = emptyMap())
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class PlayCard(val id: String, val definition: String, val owner: String,
    val containerId: String, val faceId: String, val covered: Boolean = true, val tapped: Boolean = false,
    val corner: String = "", val counters: Map<CounterColor, String> = emptyMap(), val note: String = "",
    val token: Boolean = false, val placement: PlayPlacement? = null,
    /** Old snapshots used covered=true for hidden-zone defaults, even after entering public zones. */
    val explicitlyCovered: Boolean = false,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val knownToAll: Boolean = false) {
    val isFaceDown: Boolean get() = covered && explicitlyCovered
}
/** Library order is top first. Other empty zones already have stable identities for later commands. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class PlayContainer(val id: String, val zone: PlayZone, val accountId: String? = null,
    val cards: List<String> = emptyList(),
    /** Library cards are knownTop + unknown middle + knownBottom, all top first.
     * Old snapshots have no knowledge metadata and therefore start entirely unknown. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val knownTop: List<String> = emptyList(),
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val knownBottom: List<String> = emptyList()) {
    val unknownMiddle: List<String> get() = cards.subList(knownTop.size, cards.size - knownBottom.size)
}

/** Ordinary presentation only; explicit logged inspections/contents displays are separate grants. */
object CardVisibility {
    fun isVisibleFaceUp(card: PlayCard, zone: PlayZone, holder: String?, viewer: String): Boolean = when (zone) {
        PlayZone.LIBRARY -> false
        PlayZone.HAND -> holder == viewer
        else -> !card.isFaceDown
    }

    fun isVisibleFaceUp(board: BoardState, card: PlayCard, viewer: String): Boolean {
        val container = board.containers.singleOrNull { it.id == card.containerId } ?: return false
        if (card.knownToAll) return true
        if (board.contentsDisplays.any { it.panelVersion >= 3 && it.containerId == card.containerId &&
                card.id in it.cardIds && it.faceVisibleTo(card.id, viewer) }) return true
        if (container.zone == PlayZone.HAND && container.accountId == card.owner &&
            board.stackEffects.any { it.sourceId == card.id && it.frozen == null &&
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
                        face.faceId, corner = face.corner).also(cards::add).id
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
            BoardState(players.map { PlayPlayer(it.accountId) }, cards.sortedBy { it.id }, containers, TurnState(starter.accountId)),
            needed.sorted().map { definitions.getValue(it) }, shuffles, starter, librarySecret = librarySecret)
    }
}
