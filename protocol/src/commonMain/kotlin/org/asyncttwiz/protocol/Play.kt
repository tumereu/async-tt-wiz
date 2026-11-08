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
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val status: String = "",
    /** Rules v36 ([Automatum]): mana added and not yet spent (CR 106.4). */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val manaPool: List<PoolMana> = emptyList(),
    /** Rules v36: typed player counters (poison, energy, …), sorted by kind. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val typedCounters: List<TypedCounter> = emptyList(),
    /** Rules v36: the engine's opaque player ledger (D22). */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val ledger: String = "",
    /** Rules v36 (I-D18): this player's "Skip when I have no choice", on unless they turned it off:
     * the engine reads it whoever's client makes the call. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val skipWhenNoChoice: Boolean = true,
    /** I-D41: this player's "Full control", off unless they turned it on: Automatum also
     * pauses for them where they usually wouldn't respond (their own spells, every step in which they could act); the
     * host reads it whoever's client makes the call. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val fullControl: Boolean = false,
    /** I-D49: the mulligans this player took in their opening with Automatum on; with
     * [GameRules.owed] it says how many cards they put on the bottom of their library when they keep. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val mulligans: Int = 0)

/** Rules v26: a player's own status shown in a bubble by their life total (up to 512 characters since rules v30;
 * the limit only ever grew, so every recorded status stays valid). */
object PlayerStatus {
    const val MAX_LENGTH = 512
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
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val eotNote: String = "",
    /** Rules v29: the turn number in which this card last entered a Battlefield, see [EnteredTurn];
     * empty off the Battlefield and in older games. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val enteredTurn: String = "",
    /** Rules v36 ([Automatum], D21): how often this card changed zones; the engine's object id is the card plus it. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val zoneChanges: Int = 0,
    /** Rules v36 (CR 613.7, D34): set as it enters a zone, is attached, turns face up or down or shows another face
     * on the Battlefield; 0 when never stamped. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val timestamp: Long = 0,
    /** Rules v36: typed counters (+1/+1, loyalty, …), sorted by kind, beside the manual colour [counters]. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val typedCounters: List<TypedCounter> = emptyList(),
    /** Rules v36: the object or player this Aura, Equipment or Fortification is attached to (CR 301.5). */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val attachedTo: MtgRef? = null,
    /** Rules v36: the engine's opaque ledger of this object (D22): cast facts, activations, card memory. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val ledger: String = "") {
    val isFaceDown: Boolean get() = covered && explicitlyCovered
}
/** Library order is top first. Other empty zones already have stable identities for later commands. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class PlayContainer(val id: String, val zone: PlayZone, val accountId: String? = null,
    val cards: List<String> = emptyList(),
    /** Rules v29: a LIBRARY-zone container's [cards] split into layers, top first, see [LibraryLayers].
     * Empty for every other zone and for an empty library. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val layers: List<LibraryLayer> = emptyList(),
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val keepTopRevealed: Boolean = false,
    /** Rules v23: a player's lazily-created Temp pile. It is a LIBRARY-zone container (hidden faces,
     * unknown order after shuffle) but never "the" Library: owner lookups must exclude it. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val temp: Boolean = false,
    /** Rules v29: which of the player's Temp piles (1..4) this is. Only meaningful when [temp]; old
     * snapshots have only pile 1 and therefore omit it. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val tempNumber: Int = 1)

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
        // A face-down entry (STK-005) shows its hand source to nobody but the holder.
        if (board.stackEffects.any { it.sourceId == card.id && it.frozen == null && !it.faceDown &&
                (container.zone == PlayZone.HAND && container.accountId == card.owner ||
                    container.zone == PlayZone.LIBRARY && it.revealsLibrarySource) &&
                it.id in board.containers.singleOrNull { c -> c.zone == PlayZone.STACK }?.cards.orEmpty() }) return true
        return isVisibleFaceUp(card, container.zone, container.accountId, viewer)
    }
}
@Serializable data class TurnState(val accountId: String, val number: String = "1",
    val phase: String = "BEGINNING", val step: String = "UNTAP")
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class BoardState(val players: List<PlayPlayer>, val cards: List<PlayCard>,
    val containers: List<PlayContainer>, val turn: TurnState, val schemaVersion: Int = 1,
    val contentsDisplays: List<ContentsDisplay> = emptyList(), val links: List<PlayLink> = emptyList(),
    val combat: CombatArea? = null, val stackEffects: List<StackEffect> = emptyList(),
    val projectionLinks: List<ProjectionLink> = emptyList(),
    val resultSuggestion: ResultSuggestion? = null,
    /** Entry metadata keyed by Stack card/occurrence ID, never a field on its real source. */
    val stackTextMarks: Map<String, StackTextMarks> = emptyMap(),
    /** Null retains the legacy meaning: attention follows the turn player. */
    val attentionHolder: String? = null,
    /** Rules v36: who has priority and how many passed in a row (D57); null while nobody tracks it. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val priority: MtgPriority? = null,
    /** Rules v36: the engine's own records (§4.3), sorted by id, each changed on its own. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val engineRecords: List<EngineRecord> = emptyList(),
    /** TRN-058 (I-D57): on a board shown for a game whose state is the engine's, whose move the game is for the viewer:
     * the player it really waits for ([org.asyncttwiz.protocol.game.GameDevice.move]); null: nobody's, or a board without
     * one. Never serialized. [attentionHolder] stays who acts on the board shown (a decision stop in playback is the
     * viewer's to answer while the move is the other player's). */
    @kotlinx.serialization.Transient val move: String? = null) {
    val effectiveAttentionHolder: String get() = attentionHolder ?: turn.accountId
    /** Whose move the move pill, the bar colours and the fields show (TRN-058): [move], else [effectiveAttentionHolder]. */
    val moveHolder: String get() = move ?: effectiveAttentionHolder
}
@Serializable data class InitialShuffle(val accountId: String, val seed: String, val order: List<String>,
    val algorithm: String = "shuffle-v1")
@Serializable data class InitialStarter(val choice: StarterChoice, val accountId: String,
    val seed: String? = null, val algorithm: String? = null)
@Serializable data class PlayRoot(val id: String, val branchId: String, val parentId: String? = null)
/**
 * One play of a game as it started (the play row the server stores, [StartRules.initialize]): its ids, the players'
 * containers and cards as dealt, the card data of every card, the initial shuffles and the starter. A game on engine
 * state is played from it ([org.asyncttwiz.protocol.game]); the table draws the engine's state on its containers and
 * players. A play row stored with the old model's fields reads with [GameWire.stored].
 */
@Serializable data class PlayState(val gameId: String, val id: String, val preparationRound: String,
    val startRequestId: String, val root: PlayRoot, val board: BoardState,
    val definitions: List<SubmittedDefinition>, val shuffles: List<InitialShuffle>, val starter: InitialStarter,
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
            // Concerns later plays only, so nobody's Ready is withdrawn.
            is GameAction.SetAlternateStarter -> PreparationChange(current.copy(alternateStarter = action.enabled))
            // Display only: nobody's Ready is withdrawn. The server fills a shown pod deck's private name.
            is GameAction.ShowDeck -> PreparationChange(current.copy(players = current.players.map {
                if (it.accountId == actor) it.copy(deckShown = action.shown, shownPodDeckName = it.shownPodDeckName.takeIf { action.shown })
                else it
            }))
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
    /** [actor]'s Ready with the cards they own outside the game ([outside], checked by [org.asyncttwiz.protocol.game.OutsideCards.valid]):
     * kept with their prepared deck, replacing the ones of an earlier Ready. */
    fun withOutside(preparation: LobbyPreparation, actor: String, outside: List<SubmittedDefinition>): LobbyPreparation {
        val players = preparation.players.map { p ->
            if (p.accountId == actor) p.copy(deck = p.deck?.copy(outside = outside.map { it.digest })) else p
        }
        val definitions = (preparation.definitions + outside).associateBy { it.digest }.values.sortedBy { it.digest }
        return preparation.copy(players = players, definitions = definitions)
    }
    fun canStart(game: GameSummary, preparation: LobbyPreparation) = game.lifecycle == GameLifecycle.LOBBY &&
        game.invitationState == InvitationState.ACCEPTED &&
        // TRN-065: the next game starts only once both players have tapped OK on the last one's result
        game.resultNotice == null &&
        preparation.players.size == 2 && preparation.players.all { it.ready && it.deck != null }

    /** Inputs are acquired independently by the server only after the readiness transition applies. */
    /** Every play is played with Automatum (I-D52) and starts with each player's opening hand ([GameRules.openingHand])
     * already drawn from their shuffled library (I-D49). */
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
        // I-D49: each player's opening hand is drawn from the shuffled library at once
        val opening = GameRules.STANDARD.openingHand
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
            val hand = CommandIds.uuidV5(playId, "container/${player.accountId}/HAND")
            val drawn = order.take(opening)
            val rest = order.drop(opening)
            for (zone in PlayZone.entries.filter { it != PlayZone.STACK }) containers.add(PlayContainer(
                if (zone == PlayZone.LIBRARY) library else CommandIds.uuidV5(playId, "container/${player.accountId}/${zone.name}"),
                zone, player.accountId, when (zone) { PlayZone.LIBRARY -> rest; PlayZone.HAND -> drawn; else -> emptyList() },
                layers = if (zone == PlayZone.LIBRARY) LibraryLayers.initial(CommandIds.uuidV5(library, "layer/0"), rest) else emptyList()))
            val inHand = drawn.toSet()
            for (index in cards.indices) if (cards[index].id in inHand) cards[index] = cards[index].copy(containerId = hand)
        }
        containers.add(PlayContainer(CommandIds.uuidV5(playId, "container/STACK"), PlayZone.STACK))
        // The cards outside the game: Side and Maybe (LOB-141) and the dungeon cards (CR 309.2): their card data, for the table
        // and the choice that brings one in
        val needed = cards.map { it.definition }.toSet() + players.map { it.deck!! }.flatMap { deck ->
            deck.entries.filter { it.section != SavedDeckSection.MAINBOARD }.map { it.definition } + deck.outside }
        return PlayState(game.id, playId, preparation.roundId, requestId, root,
            BoardState(players.map { PlayPlayer(it.accountId, status = PlayerStatus.OPENING) }, cards.sortedBy { it.id }, containers,
                TurnState(starter.accountId)),
            needed.sorted().map { definitions.getValue(it) }, shuffles, starter, librarySecret = librarySecret)
    }
}
