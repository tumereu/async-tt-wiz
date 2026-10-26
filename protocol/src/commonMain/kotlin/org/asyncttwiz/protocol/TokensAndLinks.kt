package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** Persistent visual relationships, not source relationships or event-scoped pings. */
@Serializable enum class LinkEndpointKind { CARD, PLAYER }
@Serializable data class LinkEndpoint(val kind: LinkEndpointKind, val id: String)
@Serializable data class PlayLink(val id: String, val first: LinkEndpoint, val second: LinkEndpoint)
@Serializable data class LinkChange(val from: PlayLink?, val to: PlayLink?)
/** Full object data is relevant to creation/destruction only, never an ordinary field-edit lock. */
@Serializable data class TokenPresence(val card: PlayCard, val location: CardLocation)
@Serializable data class TokenChange(val from: TokenPresence?, val to: TokenPresence?)
@Serializable data class TokenMoveRead(val destinationId: String, val destinationZone: PlayZone)
/** Clone does not read the source's owner, token flag or location. Definition retains face defaults. */
@Serializable data class CloneRead(val sourceId: String, val definition: SubmittedDefinition,
    val faceId: String, val covered: Boolean, val tapped: Boolean, val corner: String,
    val counters: Map<CounterColor, String>, val note: String, val explicitlyCovered: Boolean = false)

object TokenCatalog {
    val blank: SubmittedDefinition = CardDefinition("Blank", listOf(DefinitionFace(
        "548c71f3-20cd-53f0-b9b0-737030a14f51", "Blank")), provider = "async-tt-wiz",
        sourceVersion = "blank-v1", token = true).let { SubmittedDefinition(DeckJson.digest(it), it) }
}

/** Extension of GameplayRules, not a second state processor. All outputs use the existing record path. */
object TokenLinkRules {
    fun validDefinition(value: SubmittedDefinition) = DeckValidation.definition(value.definition) &&
        value.definition.token && value.digest == DeckJson.digest(value.definition)

    fun exists(board: BoardState, endpoint: LinkEndpoint): Boolean = when (endpoint.kind) {
        LinkEndpointKind.CARD -> board.cards.any { it.id == endpoint.id }
        LinkEndpointKind.PLAYER -> board.players.any { it.accountId == endpoint.id }
    }

    fun pair(first: LinkEndpoint, second: LinkEndpoint): Pair<LinkEndpoint, LinkEndpoint> =
        if ("${first.kind}/${first.id}" <= "${second.kind}/${second.id}") first to second else second to first

    internal fun change(state: PlayState, actor: String, command: GameCommand, action: PlayAction): GameplayChange {
        val board = state.board
        fun reject(reason: String) = GameplayChange(reason = reason)
        when (action) {
            is PlayAction.CreateToken, is PlayAction.CloneToken -> {
                val read = if (action is PlayAction.CloneToken) {
                    val card = board.cards.singleOrNull { it.id == action.cardId } ?: return reject("missingReference")
                    val definition = state.definitions.singleOrNull { it.digest == card.definition }
                        ?: return reject("missingReference")
                    if (definition.definition.faces.none { it.faceId == card.faceId }) return reject("missingReference")
                    CloneRead(card.id, definition, card.faceId, card.covered, card.tapped, card.corner, card.counters, card.note,
                        card.explicitlyCovered)
                } else null
                val definition = read?.definition ?: (action as PlayAction.CreateToken).definition
                val retained = state.definitions.singleOrNull { it.digest == definition.digest }
                if (retained != null && retained != definition) return reject("invalidInput")
                val container = board.containers.singleOrNull { it.zone == PlayZone.BATTLEFIELD && it.accountId == actor }
                    ?: return reject("missingReference")
                val tokenId = CommandIds.uuidV5(command.requestId, "token/0")
                val pileId = CommandIds.uuidV5(command.requestId, "pile/0")
                if (occupied(board, tokenId) || occupied(board, pileId)) return reject("inapplicable")
                // A fresh free-standing logical cell. Never insert in an existing pile or displace cards.
                val positions = board.cards.filter { it.containerId == container.id }.mapNotNull { it.placement?.position }.toSet()
                var x = 0
                while (BoardPosition(x, 0) in positions) x++
                val face = definition.definition.faces.first()
                val token = PlayCard(tokenId, definition.digest, actor, container.id, read?.faceId ?: face.faceId,
                    covered = read?.covered ?: false, tapped = read?.tapped ?: false, corner = read?.corner ?: face.corner,
                    counters = read?.counters ?: emptyMap(), note = read?.note ?: "", token = true,
                    placement = PlayPlacement(BoardPosition(x, 0), pileId), explicitlyCovered = read?.explicitlyCovered ?: false)
                val presence = TokenPresence(token, CardLocation(container.id, token.placement))
                val next = GameplayRules.canonicalBoard(board.copy(cards = (board.cards + token).sortedBy { it.id },
                    containers = board.containers.map {
                        if (it.id == container.id) it.copy(cards = it.cards + token.id) else it
                    }))
                return GameplayRules.record(state.copy(definitions = (state.definitions + definition).distinctBy { it.digest }.sortedBy { it.digest }),
                    actor, command, next, GameplayTransition(tokens = listOf(TokenChange(null, presence)), cloneRead = read))
            }
            is PlayAction.RemoveToken -> {
                val card = board.cards.singleOrNull { it.id == action.cardId } ?: return reject("missingReference")
                if (!card.token) return reject("inapplicable")
                return remove(state, actor, command, card)
            }
            is PlayAction.Link -> {
                if (!exists(board, action.first) || !exists(board, action.second)) return reject("missingReference")
                if (action.first == action.second) return reject("inapplicable")
                val (first, second) = pair(action.first, action.second)
                if (board.links.any { pair(it.first, it.second) == (first to second) }) return GameplayChange(state)
                val id = CommandIds.uuidV5(command.requestId, "link/0")
                if (occupied(board, id)) return reject("inapplicable")
                val link = PlayLink(id, first, second)
                return GameplayRules.record(state, actor, command, board.copy(links = (board.links + link).sortedBy { it.id }),
                    GameplayTransition(links = listOf(LinkChange(null, link))))
            }
            is PlayAction.RemoveLink -> {
                val link = board.links.singleOrNull { it.id == action.linkId } ?: return GameplayChange(state)
                return GameplayRules.record(state, actor, command, board.copy(links = board.links - link),
                    GameplayTransition(links = listOf(LinkChange(link, null))))
            }
            is PlayAction.LinkProjections -> {
                if (!CombatStackRules.exists(board, action.first) || !CombatStackRules.exists(board, action.second))
                    return reject("missingReference")
                val (first, second) = CombatStackRules.projectionPair(action.first.occurrenceId, action.second.occurrenceId)
                if (first == second) return reject("inapplicable")
                if (board.projectionLinks.any { CombatStackRules.projectionPair(it.first, it.second) == (first to second) })
                    return GameplayChange(state)
                val id = CommandIds.uuidV5(command.requestId, "projection-link/0")
                if (occupied(board, id)) return reject("inapplicable")
                val link = ProjectionLink(id, first, second)
                return GameplayRules.record(state, actor, command,
                    board.copy(projectionLinks = (board.projectionLinks + link).sortedBy { it.id }), GameplayTransition())
            }
            is PlayAction.RemoveProjectionLink -> {
                val link = board.projectionLinks.singleOrNull { it.id == action.linkId } ?: return GameplayChange(state)
                return GameplayRules.record(state, actor, command,
                    board.copy(projectionLinks = board.projectionLinks - link), GameplayTransition())
            }
            else -> error("Not a token/link action")
        }
    }

    internal fun occupied(board: BoardState, id: String) = board.cards.any { it.id == id || it.placement?.pileId == id } ||
        board.containers.any { it.id == id } || board.players.any { it.accountId == id } || board.links.any { it.id == id } ||
        board.projectionLinks.any { it.id == id } || id in CombatStackRules.projectionIds(board) ||
        board.combat?.id == id || board.combat?.pairs.orEmpty().any { it.id == id }

    private fun location(board: BoardState, card: PlayCard): CardLocation? {
        val container = board.containers.singleOrNull { it.id == card.containerId } ?: return null
        val index = container.cards.indexOf(card.id)
        if (index < 0) return null
        val ordered = container.zone in setOf(PlayZone.LIBRARY, PlayZone.HAND, PlayZone.STACK)
        return CardLocation(container.id, card.placement,
            if (ordered) container.cards.getOrNull(index - 1) else null,
            if (ordered) container.cards.getOrNull(index + 1) else null)
    }

    private fun remove(state: PlayState, actor: String, command: GameCommand, card: PlayCard,
        moveRead: TokenMoveRead? = null): GameplayChange {
        val location = location(state.board, card) ?: return GameplayChange(reason = "missingReference")
        val endpoint = LinkEndpoint(LinkEndpointKind.CARD, card.id)
        val links = state.board.links.filter { it.first == endpoint || it.second == endpoint }
        return GameplayRules.record(state, actor, command, state.board.copy(cards = state.board.cards - card,
            containers = state.board.containers.map { it.copy(cards = it.cards - card.id) }, links = state.board.links - links.toSet()),
            GameplayTransition(tokens = listOf(TokenChange(TokenPresence(card, location), null)),
                links = links.sortedBy { it.id }.map { LinkChange(it, null) }, tokenMoveRead = moveRead))
    }

    /** I13 calls this for all move routes before ordinary destination resets. Null means ordinary movement. */
    fun disappearForMove(state: PlayState, actor: String, command: GameCommand, action: PlayAction.MoveCard): GameplayChange? {
        val card = state.board.cards.singleOrNull { it.id == action.cardId } ?: return null
        val destination = state.board.containers.singleOrNull { it.id == action.destination.containerId } ?: return null
        if (!card.token || destination.zone !in setOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE)) return null
        if (card.containerId != action.source.containerId || card.placement != action.source.placement)
            return GameplayChange(reason = "inapplicable")
        val source = state.board.containers.singleOrNull { it.id == card.containerId } ?: return GameplayChange(reason = "missingReference")
        if (card.id !in source.cards || (action.source.edge == MoveSourceEdge.TOP && source.cards.firstOrNull() != card.id) ||
            (action.source.edge == MoveSourceEdge.BOTTOM && source.cards.lastOrNull() != card.id))
            return GameplayChange(reason = "inapplicable")
        val dest = action.destination
        if (dest.position != null || dest.pileId != null ||
            (destination.zone == PlayZone.LIBRARY && dest.insertion !in setOf(MoveInsertion.TOP, MoveInsertion.BOTTOM, MoveInsertion.GAP)) ||
            (destination.zone != PlayZone.LIBRARY && dest.insertion != MoveInsertion.UNORDERED) ||
            (dest.insertion != MoveInsertion.GAP && (dest.afterId != null || dest.beforeId != null)))
            return GameplayChange(reason = "inapplicable")
        if (dest.insertion == MoveInsertion.GAP) {
            if (card.id in dest.pendingIds || dest.afterId in dest.pendingIds || dest.beforeId in dest.pendingIds)
                return GameplayChange(reason = "inapplicable")
            val remaining = destination.cards.filterNot { it == card.id || it in dest.pendingIds }
            val index = if (dest.afterId == null) 0 else remaining.indexOf(dest.afterId).takeIf { it >= 0 }?.plus(1)
                ?: return GameplayChange(reason = "inapplicable")
            if (remaining.getOrNull(index) != dest.beforeId) return GameplayChange(reason = "inapplicable")
        }
        return remove(state, actor, command, card, TokenMoveRead(destination.id, destination.zone))
    }

    internal fun inverse(board: BoardState, transition: GameplayTransition): BoardState? {
        if (transition.fields.isNotEmpty() || transition.movement != null ||
            transition.libraryOrder != null || transition.contents != null || transition.random != null ||
            transition.annotation != null || transition.combatStack != null ||
            transition.reads.isNotEmpty() || transition.disclosures.isNotEmpty() ||
            (transition.tokens.isEmpty() && transition.links.isEmpty())) return null
        var next = board
        // Remove created links first; restored incident links are added only after their endpoints.
        for (change in transition.links) if (change.to != null) {
            if (next.links.singleOrNull { it.id == change.to.id } != change.to) return null
            next = next.copy(links = next.links - change.to)
        }
        for (change in transition.tokens.asReversed()) {
            change.to?.let { presence ->
                if (next.cards.singleOrNull { it.id == presence.card.id } != presence.card || location(next, presence.card) != presence.location ||
                    next.links.any { it.first == LinkEndpoint(LinkEndpointKind.CARD, presence.card.id) ||
                        it.second == LinkEndpoint(LinkEndpointKind.CARD, presence.card.id) }) return null
                next = next.copy(cards = next.cards - presence.card,
                    containers = next.containers.map { it.copy(cards = it.cards - presence.card.id) })
            }
            change.from?.let { presence ->
                val card = presence.card
                if (occupied(next, card.id)) return null
                val container = next.containers.singleOrNull { it.id == presence.location.containerId } ?: return null
                val loc = presence.location
                if (card.placement != loc.placement || card.containerId != container.id || !card.token) return null
                if (container.zone == PlayZone.BATTLEFIELD && card.placement != null && next.cards.count {
                        it.containerId == container.id && it.placement?.pileId == card.placement.pileId
                    } >= 3) return null
                val ordered = container.zone in setOf(PlayZone.LIBRARY, PlayZone.HAND, PlayZone.STACK)
                val index = if (loc.afterId == null) 0 else container.cards.indexOf(loc.afterId).takeIf { it >= 0 }?.plus(1) ?: return null
                if (ordered && container.cards.getOrNull(index) != loc.beforeId) return null
                next = next.copy(cards = (next.cards + card).sortedBy { it.id }, containers = next.containers.map {
                    if (it.id != container.id) it else it.copy(cards = it.cards.toMutableList().apply {
                        add(if (ordered) index else size, card.id)
                    }.let { ids -> if (ordered || transition.rulesVersion >= 16 && container.zone == PlayZone.BATTLEFIELD)
                        ids else ids.sorted() })
                }).let { if (transition.rulesVersion >= 16) GameplayRules.canonicalBoard(it) else it }
            }
        }
        for (change in transition.links) change.from?.let { link ->
            if (occupied(next, link.id) || !exists(next, link.first) || !exists(next, link.second) ||
                next.links.any { pair(it.first, it.second) == pair(link.first, link.second) }) return null
            next = next.copy(links = (next.links + link).sortedBy { it.id })
        }
        return next
    }
}
