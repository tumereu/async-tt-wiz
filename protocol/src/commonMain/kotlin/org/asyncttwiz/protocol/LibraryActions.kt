package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

@Serializable enum class ContentsSelection { TOP, BOTTOM, ALL }
@Serializable enum class ContentsRest { TOP, BOTTOM, BOTTOM_RANDOM, GRAVEYARD, SHUFFLE, EXILE }
/** A shared tray of references, not a second containing zone. Order is top first. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class ContentsDisplay(val id: String, val author: String, val containerId: String,
    val selection: ContentsSelection, val requestedCount: Int, val reveal: Boolean, val cardIds: List<String>,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val revealedIds: Set<String> = emptySet(),
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val panelVersion: Int = 1) {
    fun facesVisibleTo(accountId: String) = reveal || author == accountId
    fun faceVisibleTo(cardId: String, accountId: String) = facesVisibleTo(accountId) || cardId in revealedIds
    val search get() = selection == ContentsSelection.ALL && !reveal
    val title get() = if (reveal) "Reveal" else if (search) "Search" else "Look"
}
@Serializable data class LibraryOrderChange(val containerId: String, val from: List<String>, val to: List<String>)
@Serializable data class ContentsDisplayChange(val from: ContentsDisplay? = null, val to: ContentsDisplay? = null)

/** Shared processing helpers called only by GameplayRules, never an independent client engine. */
object LibraryRules {
    fun slice(cards: List<String>, selection: ContentsSelection, count: Int): List<String> {
        require(count >= 0)
        return when (selection) {
            ContentsSelection.TOP -> cards.take(count)
            ContentsSelection.BOTTOM -> cards.takeLast(count)
            ContentsSelection.ALL -> cards.toList()
        }
    }

    internal fun change(state: PlayState, actor: String, command: GameCommand, action: PlayAction,
        contentsRulesVersion: Int = 2): GameplayChange {
        val board = state.board
        fun record(next: BoardState, transition: GameplayTransition) =
            GameplayRules.record(state, actor, command, next, transition, contentsRulesVersion)
        return when (action) {
            is PlayAction.SetKeepTopRevealed -> {
                val library = board.containers.singleOrNull { it.id == action.containerId && it.zone == PlayZone.LIBRARY }
                    ?: return GameplayChange(reason = "missingReference")
                if (library.keepTopRevealed == action.enabled) return GameplayChange(state)
                record(board.copy(containers = board.containers.map {
                    if (it.id == library.id) it.copy(keepTopRevealed = action.enabled) else it
                }), GameplayTransition())
            }
            is PlayAction.Mulligan -> {
                val library = board.containers.singleOrNull { it.id == action.libraryId && it.zone == PlayZone.LIBRARY }
                    ?: return GameplayChange(reason = "missingReference")
                val hand = board.containers.singleOrNull { it.id == action.handId && it.zone == PlayZone.HAND &&
                    it.accountId == library.accountId } ?: return GameplayChange(reason = "missingReference")
                if (action.displayedHand.toSet() != hand.cards.toSet()) return GameplayChange(reason = "movementConflict")
                val returned = action.displayedHand.toSet()
                if (board.cards.any { it.id in returned && it.token } || board.contentsDisplays.any {
                        it.containerId == library.id || it.cardIds.any { id -> id in returned } })
                    return GameplayChange(reason = "inapplicable")
                val order = RandomV1.shuffle(action.displayedHand + library.cards, action.seed)
                val drawn = order.take(action.drawCount)
                val drawnIds = drawn.toSet()
                val affected = returned + library.cards
                val cards = board.cards.map { card -> if (card.id !in affected) card else card.copy(
                    containerId = if (card.id in drawnIds) hand.id else library.id, placement = null,
                    covered = true, explicitlyCovered = false, knownToAll = false, tapped = false,
                    note = if (card.id in returned || card.id in drawnIds) "" else card.note) }
                val byId = cards.associateBy { it.id }
                val fields = board.cards.filter { it.id in affected }.flatMap { card ->
                    val next = byId.getValue(card.id)
                    listOf(GameplayFieldChange(card.id, GameplayField.NOTE, card.note, next.note),
                        GameplayFieldChange(card.id, GameplayField.TAPPED, card.tapped.toString(), next.tapped.toString()),
                        GameplayFieldChange(card.id, GameplayField.COVERED, card.covered.toString(), next.covered.toString()),
                        GameplayFieldChange(card.id, GameplayField.EXPLICITLY_COVERED, card.explicitlyCovered.toString(), next.explicitlyCovered.toString()))
                        .filter { it.from != it.to }
                }
                record(board.copy(cards = cards, containers = board.containers.map { when (it.id) {
                    library.id -> it.copy(cards = order.drop(drawn.size), knownTop = emptyList(), knownBottom = emptyList())
                    hand.id -> it.copy(cards = drawn)
                    else -> it
                } }), GameplayTransition(fields = fields, disclosures = drawn.map { id ->
                    GameplayDisclosure(id, byId.getValue(id).faceId, hand.id, listOfNotNull(hand.accountId)) }))
            }
            is PlayAction.ShuffleLibrary -> {
                val source = board.containers.singleOrNull { it.id == action.containerId && it.zone == PlayZone.LIBRARY }
                    ?: return GameplayChange(reason = "missingReference")
                val order = RandomV1.shuffle(source.cards, action.seed, action.algorithm)
                // Even a coincidentally identical permutation retains this fresh random invocation.
                record(board.copy(containers = board.containers.map {
                    if (it.id == source.id) it.copy(cards = order) else it
                }, cards = board.cards.map { if (it.containerId == source.id) it.copy(knownToAll = false) else it }),
                    GameplayTransition(libraryOrder = LibraryOrderChange(source.id, source.cards, order)))
            }
            is PlayAction.ShowContents -> {
                val source = board.containers.singleOrNull { it.id == action.containerId }
                    ?: return GameplayChange(reason = "missingReference")
                if (source.zone !in specialZones || source.zone != PlayZone.LIBRARY && action.selection != ContentsSelection.ALL)
                    return GameplayChange(reason = "inapplicable")
                if (board.contentsDisplays.isNotEmpty()) return GameplayChange(reason = "panelAlreadyOpen")
                val members = slice(source.cards, action.selection, action.count)
                // ALL is orderless, including in history: no hidden Library indices in a full browser.
                val display = ContentsDisplay(CommandIds.uuidV5(command.requestId, "contents/0"), actor, source.id,
                    action.selection, action.count, action.reveal,
                    if (action.selection == ContentsSelection.ALL) members.sorted() else members,
                    panelVersion = if (contentsRulesVersion >= 2) 3 else 2)
                val viewers = if (action.reveal) board.players.map { it.accountId } else listOf(actor)
                record(
                    board.copy(contentsDisplays = board.contentsDisplays + display,
                        cards = if (!action.reveal && contentsRulesVersion < 2) board.cards else board.cards.map {
                            if (it.id in members) it.copy(knownToAll = action.reveal && contentsRulesVersion < 2) else it }),
                    GameplayTransition(contents = ContentsDisplayChange(to = display), disclosures = members.map { id ->
                        val card = board.cards.single { it.id == id }
                        GameplayDisclosure(id, card.faceId, source.id, viewers)
                    }))
            }
            is PlayAction.DismissContents -> {
                val display = board.contentsDisplays.singleOrNull { it.id == action.displayId } ?: return GameplayChange(state)
                if (display.author != actor) return GameplayChange(reason = "accessDenied")
                val source = board.containers.single { it.id == display.containerId }
                val ids = display.cardIds.filter { it in source.cards }
                val rest = source.cards.filterNot { it in ids }
                // Older clients omitted a seed on dismiss. The request still binds one reproducible result.
                val seed = action.seed ?: RandomV1.encodeSeed(DeckJson.intent("contents-rest", command.requestId)
                    .chunked(2).map { it.toInt(16).toByte() }.toByteArray())
                val resolution = if (display.search) ContentsRest.SHUFFLE else action.rest
                val restZone = when (resolution) {
                    ContentsRest.GRAVEYARD -> PlayZone.GRAVEYARD
                    ContentsRest.EXILE -> PlayZone.EXILE
                    else -> null
                }
                val restDestination = board.containers.singleOrNull { it.accountId == source.accountId && it.zone == restZone }
                val order = when (resolution) {
                    ContentsRest.TOP -> ids + rest
                    ContentsRest.BOTTOM -> rest + ids
                    ContentsRest.BOTTOM_RANDOM -> rest + RandomV1.shuffle(ids, seed)
                    ContentsRest.GRAVEYARD, ContentsRest.EXILE -> rest
                    ContentsRest.SHUFFLE -> RandomV1.shuffle(source.cards, seed)
                }
                if (restZone != null && restDestination == null) return GameplayChange(reason = "missingReference")
                val next = board.copy(contentsDisplays = board.contentsDisplays.filterNot { it.id == display.id },
                    containers = board.containers.map { when (it.id) {
                        source.id -> it.copy(cards = order)
                        restDestination?.id -> it.copy(cards = (it.cards + ids).sorted())
                        else -> it
                    } }, cards = board.cards.map { card -> when {
                        card.id in ids && restZone != null -> card.copy(containerId = restDestination!!.id,
                            covered = false, explicitlyCovered = false, knownToAll = false, counters = emptyMap(),
                            corner = state.definitions.single { it.digest == card.definition }.definition.faces.single { it.faceId == card.faceId }.corner)
                        card.containerId == source.id && resolution == ContentsRest.SHUFFLE -> card.copy(knownToAll = false)
                        contentsRulesVersion >= 2 && card.id in ids -> card.copy(knownToAll = false)
                        else -> card
                    } })
                record(next, GameplayTransition(contents = ContentsDisplayChange(from = display),
                    libraryOrder = LibraryOrderChange(source.id, source.cards, order),
                    disclosures = if (restZone == null) emptyList() else ids.map { id ->
                        val card = board.cards.single { it.id == id }
                        GameplayDisclosure(id, card.faceId, restDestination!!.id, board.players.map { it.accountId })
                    }))
            }
            is PlayAction.RevealContentsCard -> {
                val display = board.contentsDisplays.singleOrNull { it.id == action.displayId } ?: return GameplayChange(reason = "missingReference")
                if (display.author != actor) return GameplayChange(reason = "accessDenied")
                if (action.cardId !in display.cardIds) return GameplayChange(reason = "missingReference")
                if (display.faceVisibleTo(action.cardId, board.players.first { it.accountId != actor }.accountId)) return GameplayChange(state)
                val card = board.cards.single { it.id == action.cardId }
                val updated = display.copy(revealedIds = display.revealedIds + card.id,
                    panelVersion = if (contentsRulesVersion >= 2) 3 else display.panelVersion)
                record(board.copy(
                    contentsDisplays = board.contentsDisplays.map { if (it.id == display.id) updated else it },
                    cards = board.cards.map { if (it.id == card.id) it.copy(knownToAll = contentsRulesVersion < 2) else it }),
                    GameplayTransition(contents = ContentsDisplayChange(display, updated), disclosures = listOf(
                        GameplayDisclosure(card.id, card.faceId, card.containerId, board.players.map { it.accountId }))))
            }
            is PlayAction.ReorderContents -> {
                val display = board.contentsDisplays.singleOrNull { it.id == action.displayId } ?: return GameplayChange(reason = "missingReference")
                if (display.author != actor) return GameplayChange(reason = "accessDenied")
                if (display.search || action.cardId !in display.cardIds || action.beforeId == action.cardId ||
                    action.beforeId != null && action.beforeId !in display.cardIds) return GameplayChange(reason = "inapplicable")
                val remaining = display.cardIds - action.cardId
                val order = remaining.toMutableList().apply { add(action.beforeId?.let(::indexOf) ?: size, action.cardId) }
                if (order == display.cardIds) return GameplayChange(state)
                val updated = display.copy(cardIds = order)
                record(board.copy(contentsDisplays = board.contentsDisplays.map {
                    if (it.id == display.id) updated else it }), GameplayTransition(contents = ContentsDisplayChange(display, updated)))
            }
            else -> error("Not a Library action")
        }
    }

    private val specialZones = setOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE)

    /** Early I13 seam: concrete Hand/special-container moves. Board/Stack and token cleanup stay I13/I19. */
    internal fun move(state: PlayState, actor: String, command: GameCommand, action: PlayAction.MoveCard): GameplayChange {
        fun reject() = GameplayChange(reason = "inapplicable")
        val board = state.board
        val card = board.cards.singleOrNull { it.id == action.cardId } ?: return GameplayChange(reason = "missingReference")
        val source = board.containers.singleOrNull { it.id == action.source.containerId } ?: return reject()
        val destination = board.containers.singleOrNull { it.id == action.destination.containerId } ?: return reject()
        if (source.zone !in specialZones + PlayZone.HAND || destination.zone !in specialZones + PlayZone.HAND ||
            card.token || card.containerId != source.id || card.id !in source.cards ||
            card.placement != action.source.placement || action.destination.position != null ||
            action.destination.pileId != null || action.expectedStackDigest != null) return reject()
        if (action.source.edge != MoveSourceEdge.MEMBER && source.zone != PlayZone.LIBRARY) return reject()
        if (action.source.edge == MoveSourceEdge.TOP && source.cards.firstOrNull() != card.id ||
            action.source.edge == MoveSourceEdge.BOTTOM && source.cards.lastOrNull() != card.id) return reject()
        val slice = action.source.bottomSlice
        if (slice.isNotEmpty() && (source.zone != PlayZone.LIBRARY || slice.first() != card.id ||
                source.cards.takeLast(slice.size) != slice)) return reject()
        val target = action.destination
        val remaining = destination.cards.filterNot { it == card.id }
        val index = if (destination.zone == PlayZone.LIBRARY) when (target.insertion) {
            MoveInsertion.TOP -> 0
            MoveInsertion.BOTTOM -> remaining.size
            MoveInsertion.GAP -> {
                if (card.id in target.pendingIds || target.afterId in target.pendingIds || target.beforeId in target.pendingIds)
                    return reject()
                // Still-pending members of this invocation do not invalidate its normalized gap.
                val reduced = remaining.filterNot { it in target.pendingIds }
                val left = target.afterId?.let { reduced.indexOf(it).takeIf { index -> index >= 0 } ?: return reject() } ?: -1
                val right = target.beforeId?.let { reduced.indexOf(it).takeIf { index -> index >= 0 } ?: return reject() } ?: reduced.size
                if (right != left + 1) return reject()
                target.afterId?.let { remaining.indexOf(it) + 1 } ?: 0
            }
            else -> return reject()
        } else {
            if (target.insertion != MoveInsertion.UNORDERED) return reject()
            remaining.size
        }
        val order = remaining.toMutableList().apply { add(index, card.id) }
        val entering = source.id != destination.id
        val reset = entering && destination.zone in specialZones
        val defaultCorner = state.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces
            ?.singleOrNull { it.faceId == card.faceId }?.corner.orEmpty()
        val updated = card.copy(containerId = destination.id, placement = null,
            corner = if (reset) defaultCorner else card.corner, counters = if (reset) emptyMap() else card.counters)
        if (source.id == destination.id && destination.zone != PlayZone.LIBRARY ||
            source.id == destination.id && order == source.cards && updated == card) return GameplayChange(state)
        val fields = buildList {
            if (updated.corner != card.corner) add(GameplayFieldChange(card.id, GameplayField.CORNER, card.corner, updated.corner))
            for (color in CounterColor.entries) if (reset && color in card.counters)
                add(GameplayFieldChange(card.id, GameplayField.COUNTER, card.counters.getValue(color), null, color))
        }
        fun location(container: PlayContainer, cards: List<String>): CardLocation {
            val at = cards.indexOf(card.id)
            return if (container.zone == PlayZone.LIBRARY) CardLocation(container.id,
                afterId = cards.getOrNull(at - 1), beforeId = cards.getOrNull(at + 1)) else CardLocation(container.id)
        }
        val next = board.copy(cards = board.cards.map { if (it.id == card.id) updated else it },
            containers = board.containers.map {
                when (it.id) {
                    destination.id -> it.copy(cards = order)
                    source.id -> it.copy(cards = it.cards.filterNot { id -> id == card.id })
                    else -> it
                }
            })
        return GameplayRules.record(state, actor, command, next, GameplayTransition(fields,
            GameplayMoveChange(card.id, location(source, source.cards), location(destination, order))))
    }
}

/** Captured plans are immutable request intent; callers enqueue each constituent without awaiting results. */
object LibraryActions {
    fun draw(board: BoardState, libraryId: String, handId: String, count: Int): List<PlayAction.MoveCard> {
        require(count >= 0)
        val library = board.containers.single { it.id == libraryId && it.zone == PlayZone.LIBRARY }
        require(board.containers.any { it.id == handId && it.zone == PlayZone.HAND && it.accountId == library.accountId })
        return library.cards.take(count).map { PlayAction.MoveCard(it, MoveSource(libraryId, edge = MoveSourceEdge.TOP),
            MoveDestination(handId, MoveInsertion.UNORDERED)) }
    }

    fun moveSlice(board: BoardState, libraryId: String, selection: ContentsSelection, count: Int,
        destination: MoveDestination): List<PlayAction.MoveCard> {
        val library = board.containers.single { it.id == libraryId && it.zone == PlayZone.LIBRARY }
        val ids = LibraryRules.slice(library.cards, selection, count)
        if (destination.containerId == libraryId) return moveCaptured(board, ids, destination)
        return moveCaptured(board, ids, destination).mapIndexed { index, action -> action.copy(source = action.source.copy(
            edge = if (selection == ContentsSelection.TOP) MoveSourceEdge.TOP else MoveSourceEdge.MEMBER,
            bottomSlice = if (selection == ContentsSelection.BOTTOM) ids.drop(index) else emptyList())) }
    }

    fun moveCaptured(board: BoardState, capturedIds: List<String>, destination: MoveDestination): List<PlayAction.MoveCard> {
        val ids = capturedIds.distinct()
        val cards = ids.map { id -> board.cards.single { it.id == id } }
        val target = board.containers.single { it.id == destination.containerId }
        val reduced = target.cards.filterNot { it in ids }
        if (target.zone == PlayZone.LIBRARY) require(destination.insertion in setOf(MoveInsertion.TOP, MoveInsertion.BOTTOM, MoveInsertion.GAP))
        else require(target.zone in setOf(PlayZone.HAND, PlayZone.GRAVEYARD, PlayZone.EXILE) && destination.insertion == MoveInsertion.UNORDERED)
        var after = when (destination.insertion) {
            MoveInsertion.BOTTOM -> reduced.lastOrNull()
            MoveInsertion.GAP -> destination.afterId
            else -> null
        }
        val before = when (destination.insertion) {
            MoveInsertion.TOP -> reduced.firstOrNull()
            MoveInsertion.GAP -> destination.beforeId
            else -> null
        }
        if (destination.insertion == MoveInsertion.GAP) {
            val left = after?.let { reduced.indexOf(it).also { index -> require(index >= 0) } } ?: -1
            val right = before?.let { reduced.indexOf(it).also { index -> require(index >= 0) } } ?: reduced.size
            require(right == left + 1)
        }
        if (cards.all { it.containerId == target.id } && target.zone == PlayZone.LIBRARY) {
            val at = after?.let { reduced.indexOf(it) + 1 } ?: 0
            val final = reduced.toMutableList().apply { addAll(at, ids) }
            if (final == target.cards && (destination.insertion == MoveInsertion.GAP || ids.all {
                    it in if (destination.insertion == MoveInsertion.TOP) target.knownTop else target.knownBottom })) return emptyList()
        }
        return cards.mapIndexed { index, card ->
            val move = PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement),
                if (target.zone == PlayZone.LIBRARY) destination.copy(insertion = MoveInsertion.GAP,
                    afterId = after, beforeId = before, pendingIds = ids.drop(index + 1),
                    libraryInsertion = destination.libraryInsertion ?: destination.insertion.takeIf {
                        it == MoveInsertion.TOP || it == MoveInsertion.BOTTOM }) else destination)
            // I19's token-removal processor must never turn a disappearing member into an anchor.
            if (!card.token || target.zone !in setOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE)) after = card.id
            move
        }
    }

    data class Mulligan(val libraryId: String, val handId: String, val handIds: List<String>,
        val preReturnSize: Int, val drawCount: Int, val seed: String, val returns: List<PlayAction.MoveCard>) {
        fun prefix(): List<PlayAction> = returns + PlayAction.ShuffleLibrary(libraryId, seed)
        fun action() = PlayAction.Mulligan(libraryId, handId, handIds, drawCount, seed)
    }
    fun mulligan(board: BoardState, libraryId: String, handId: String, displayedHand: List<String>, seed: String,
        count: Int = 7, minusOne: Boolean = false): Mulligan {
        require(count >= 0); RandomV1.decodeSeed(seed)
        val library = board.containers.single { it.id == libraryId && it.zone == PlayZone.LIBRARY }
        val hand = board.containers.single { it.id == handId && it.zone == PlayZone.HAND && it.accountId == library.accountId }
        require(displayedHand.distinct().size == displayedHand.size && displayedHand.toSet() == hand.cards.toSet())
        return Mulligan(libraryId, handId, displayedHand.toList(), displayedHand.size,
            if (minusOne) (displayedHand.size - 1).coerceAtLeast(0) else count, seed,
            moveCaptured(board, displayedHand, MoveDestination(libraryId, MoveInsertion.TOP)))
    }
}
