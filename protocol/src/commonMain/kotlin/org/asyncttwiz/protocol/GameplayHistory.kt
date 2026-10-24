package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** Exact relevant (from,to) tuples; fields absent from these effects are unchanged. No whole-card snapshots. */
@Serializable enum class GameplayField { TAPPED, LIFE, CORNER, COUNTER, PLAYER_COUNTER, NOTE, OWNER, FACE, COVERED, CONTAINER,
    PHASE, STEP, TURN_PLAYER, TURN_NUMBER, EXPLICITLY_COVERED }
@Serializable data class GameplayFieldChange(val targetId: String, val field: GameplayField,
    val from: String?, val to: String?, val color: CounterColor? = null)
@Serializable data class CardLocation(val containerId: String, val placement: PlayPlacement? = null,
    val afterId: String? = null, val beforeId: String? = null)
@Serializable data class GameplayMoveChange(val cardId: String, val from: CardLocation, val to: CardLocation,
    val destinationSize: Int? = null, val stackFrom: String? = null, val stackTo: String? = null,
    /** Version 1 retained the old covered flag on all moves. Keep old retained trees replayable. */
    val coverRulesVersion: Int = 1)
@Serializable data class GameplayRead(val targetId: String, val field: GameplayField, val value: String)
/** Accepted information exposure, not a whole-card snapshot or a local inspection permission. */
@Serializable data class GameplayDisclosure(val cardId: String, val faceId: String, val containerId: String,
    val viewers: List<String>)
@Serializable data class AttentionChange(val from: String?, val to: String)
@Serializable data class GameplayTransition(val fields: List<GameplayFieldChange> = emptyList(),
    val movement: GameplayMoveChange? = null, val libraryOrder: LibraryOrderChange? = null,
    val contents: ContentsDisplayChange? = null, val random: GameplayRandomResult? = null,
    val annotation: AnnotationTransition? = null, val reads: List<GameplayRead> = emptyList(),
    val disclosures: List<GameplayDisclosure> = emptyList(), val tokens: List<TokenChange> = emptyList(),
    val links: List<LinkChange> = emptyList(), val cloneRead: CloneRead? = null,
    val tokenMoveRead: TokenMoveRead? = null, val combatStack: CombatStackTransition? = null,
    val resultSuggestion: ResultSuggestionChange? = null, val attention: AttentionChange? = null,
    val delta: GameplayDelta? = null)
@Serializable enum class RandomTool { DIE, COIN }
@Serializable data class GameplayRandomResult(val tool: RandomTool, val value: String, val sides: String? = null)
@Serializable data class GameplayMutation(val id: String, val parentId: String, val branchId: String,
    val requestId: String, val author: String, val invocationId: String?, val action: PlayAction,
    val transition: GameplayTransition, val schemaVersion: Int = 1, val grouping: HistorySemantic? = null,
    val revealsInformation: Boolean = false, val references: List<GameplayNodeReference> = emptyList(),
    val bookkeeping: GameplayBookkeeping? = null)
/** An accepted dependency on a concrete logical event and its relevant transition, never a retargetable label. */
@Serializable data class GameplayNodeReference(val nodeId: String, val transition: GameplayTransition)

/** Meter actual relevant fan-out, including mandatory cleanup and accepted disclosure members.
 * Serialization meters additionally cover nested payload/counters/defaults and board reconstruction. */
internal fun GameplayTransition.effectUnits(): Long = fields.size.toLong() + reads.size + disclosures.size +
    tokens.size + links.size + listOfNotNull(movement, libraryOrder, contents, random, annotation, cloneRead, tokenMoveRead).size +
    (libraryOrder?.from?.size ?: 0) + (libraryOrder?.to?.size ?: 0) +
    (contents?.from?.cardIds?.size ?: 0) + (contents?.to?.cardIds?.size ?: 0) + (if (attention == null) 0 else 1) +
    (combatStack?.let { it.occurrences.size + it.pairs.size + it.effects.size + it.order.size + it.textMarks.size +
        listOfNotNull(it.area, it.stackGuard).size } ?: 0) + GameplayHistory.projectionLinkWorkUnits(this)
/** Compact prefix context, not a recursive history or client-assigned group. */
@Serializable data class GameplayGroup(val id: String, val author: String, val firstNodeId: String,
    val lastNodeId: String, val category: String, val key: String?, val count: String,
    val tapsAndPingsOnly: Boolean = category == "tap", val representative: HistoryRepresentative? = null)
@Serializable data class GameplayHistoryPage(val playId: String, val mutations: List<GameplayMutation>,
    val groups: List<GameplayGroup>, val throughRevision: String, val next: String? = null)

object GameplayHistory {
    /** No rule execution: all choices and exact inverse data were recorded at acceptance. */
    fun apply(state: PlayState, mutation: GameplayMutation): PlayState? {
        if (mutation.schemaVersion != 2 || state.currentNodeId != mutation.parentId ||
            mutation.author !in state.board.players.map { it.accountId } ||
            mutation.id != CommandIds.uuidV5(mutation.requestId, "board-node") ||
            mutation.transition.delta?.seal != GameplayDeltas.seal(mutation)) return null
        val context = mutation.bookkeeping ?: return null
        if (state.currentGroup != context.fromGroup || state.currentBranchId != context.fromBranchId ||
            !readsMatch(state, mutation)) return null
        val next = GameplayDeltas.apply(state, mutation.transition.delta, false) ?: return null
        return next.copy(currentNodeId = mutation.id, currentBranchId = mutation.branchId, currentGroup = context.toGroup)
    }

    fun unapply(state: PlayState, mutation: GameplayMutation): PlayState? {
        if (mutation.schemaVersion != 2 || state.currentNodeId != mutation.id ||
            mutation.id != CommandIds.uuidV5(mutation.requestId, "board-node") ||
            mutation.transition.delta?.seal != GameplayDeltas.seal(mutation)) return null
        val context = mutation.bookkeeping ?: return null
        if (state.currentGroup != context.toGroup || state.currentBranchId != mutation.branchId) return null
        val previous = GameplayDeltas.apply(state, mutation.transition.delta, true) ?: return null
        if (!readsMatch(previous, mutation)) return null
        return previous.copy(currentNodeId = mutation.parentId, currentBranchId = context.fromBranchId, currentGroup = context.fromGroup)
    }

    internal fun recontextualize(state: PlayState, mutation: GameplayMutation): GameplayMutation = mutation.copy(
        bookkeeping = GameplayBookkeeping(state.currentGroup, state.currentBranchId,
            append(state.currentGroup, mutation, state.board.containers)))

    private fun readsMatch(state: PlayState, mutation: GameplayMutation): Boolean {
        val board = state.board
        if (mutation.grouping != null && mutation.grouping != runCatching {
            HistoryGrouping.classify(mutation.action, board.containers, mutation.transition)
        }.getOrNull()) return false
        for (field in mutation.transition.fields) {
            val card = board.cards.singleOrNull { it.id == field.targetId }
            val player = board.players.singleOrNull { it.accountId == field.targetId }
            val value = when (field.field) {
                GameplayField.LIFE -> player?.life
                GameplayField.PLAYER_COUNTER -> player?.counters?.get(field.color)
                GameplayField.TAPPED -> card?.tapped?.toString()
                GameplayField.CORNER -> card?.corner
                GameplayField.COUNTER -> (card?.counters ?: player?.counters)?.get(field.color)
                GameplayField.NOTE -> card?.note
                GameplayField.OWNER -> card?.owner
                GameplayField.FACE -> card?.faceId
                GameplayField.COVERED -> card?.covered?.toString()
                GameplayField.EXPLICITLY_COVERED -> card?.explicitlyCovered?.toString()
                GameplayField.CONTAINER -> card?.containerId
                GameplayField.PHASE -> board.turn.phase
                GameplayField.STEP -> board.turn.step
                GameplayField.TURN_PLAYER -> board.turn.accountId
                GameplayField.TURN_NUMBER -> board.turn.number
            }
            if (value != field.from) return false
        }
        for (read in mutation.transition.reads) {
            if (read.field == GameplayField.TURN_PLAYER) {
                if (board.turn.accountId != read.value) return false
                continue
            }
            val card = board.cards.singleOrNull { it.id == read.targetId } ?: return false
            val value = when (read.field) {
                GameplayField.FACE -> card.faceId
                GameplayField.COVERED -> card.covered.toString()
                GameplayField.EXPLICITLY_COVERED -> card.explicitlyCovered.toString()
                GameplayField.CONTAINER -> card.containerId
                else -> return false
            }
            if (value != read.value) return false
        }
        mutation.transition.cloneRead?.let { read ->
            val card = board.cards.singleOrNull { it.id == read.sourceId } ?: return false
            if (state.definitions.singleOrNull { it.digest == read.definition.digest } != read.definition) return false
            if (card.definition != read.definition.digest || card.faceId != read.faceId || card.covered != read.covered ||
                card.tapped != read.tapped || card.corner != read.corner || card.counters != read.counters ||
                card.note != read.note || card.explicitlyCovered != read.explicitlyCovered) return false
        }
        mutation.transition.tokenMoveRead?.let { read ->
            if (board.containers.singleOrNull { it.id == read.destinationId }?.zone != read.destinationZone) return false
        }
        for (disclosure in mutation.transition.disclosures) {
            if (mutation.action is PlayAction.InspectFace) {
                // Inspection can expose any retained face, without changing the active face.
                // Its region dependency is checked by the recorded CONTAINER read above.
                val card = board.cards.singleOrNull { it.id == disclosure.cardId } ?: return false
                if (state.definitions.none { it.digest == card.definition &&
                    it.definition.faces.any { face -> face.faceId == disclosure.faceId } }) return false
                continue
            }
            val face = mutation.transition.fields.lastOrNull { it.targetId == disclosure.cardId && it.field == GameplayField.FACE }?.to
                ?: board.cards.singleOrNull { it.id == disclosure.cardId }?.faceId
            if (face != disclosure.faceId) return false
        }
        mutation.transition.libraryOrder?.let { order ->
            if (board.containers.singleOrNull { it.id == order.containerId }?.cards != order.from) return false
        }
        mutation.transition.contents?.to?.let { display ->
            val container = board.containers.singleOrNull { it.id == display.containerId } ?: return false
            val slice = LibraryRules.slice(container.cards, display.selection, display.requestedCount)
            if ((if (display.selection == ContentsSelection.ALL) slice.sorted() else slice) != display.cardIds) return false
        }
        for (link in mutation.transition.links.mapNotNull { it.to }) {
            if (!TokenLinkRules.exists(board, link.first) || !TokenLinkRules.exists(board, link.second)) return false
        }
        mutation.transition.movement?.let { move ->
            if (board.cards.none { it.id == move.cardId }) return false
            if (GameplayRules.location(board, move.cardId, orderedHand = true) != move.from) return false
            if (move.stackFrom != null && GameplayRules.stackDigest(state) != move.stackFrom) return false
        }
        mutation.transition.combatStack?.stackGuard?.let { if (GameplayRules.stackDigest(state) != it.from) return false }
        mutation.transition.annotation?.let { annotation ->
            if (append(state.currentGroup, mutation, board.containers).id != annotation.groupId ||
                mutation.branchId != annotation.branchId || state.id != annotation.playId) return false
        }
        for (link in mutation.transition.combatStack?.addedLinks.orEmpty()) {
            if (!CombatStackRules.exists(board, ProjectionEndpoint(link.first)) ||
                !CombatStackRules.exists(board, ProjectionEndpoint(link.second))) return false
        }
        when (val action = mutation.action) {
            is PlayAction.Stack -> when (val stack = action.action) {
                is StackAction.AddEffect -> {
                    val source = board.cards.singleOrNull { it.id == stack.sourceId } ?: return false
                    if (!CombatStackRules.canProject(board, source) || GameplayRules.stackDigest(state) != stack.expectedStackDigest) return false
                }
                is StackAction.MarkText -> if (stack.entryId !in CombatStackRules.stack(board).cards) return false
                is StackAction.ToggleTextMark -> if (stack.entryId !in CombatStackRules.stack(board).cards) return false
                else -> Unit
            }
            is PlayAction.Combat -> {
                val sourceId = when (val combat = action.action) {
                    is CombatAction.Declare -> combat.sourceId
                    is CombatAction.Place -> combat.sourceId
                    else -> null
                }
                if (sourceId != null) {
                    val source = board.cards.singleOrNull { it.id == sourceId } ?: return false
                    if (board.containers.singleOrNull { it.id == source.containerId }?.zone != PlayZone.BATTLEFIELD) return false
                }
            }
            else -> Unit
        }
        return true
    }

    fun append(previous: GameplayGroup?, mutation: GameplayMutation, containers: List<PlayContainer> = emptyList()): GameplayGroup {
        return HistoryGrouping.append(previous, mutation.id, mutation.requestId, mutation.author,
            mutation.grouping ?: HistoryGrouping.classify(mutation.action, containers, mutation.transition))
    }

    /** Recorded replay never accepts a different relevant transition, including changed numeric inputs. */
    fun replay(state: PlayState, mutation: GameplayMutation,
        references: EventReferenceResolver = EventReferenceResolver.UNAVAILABLE): GameplayChange {
        if (mutation.schemaVersion == 2) {
            mutation.transition.annotation?.let { annotation ->
                val retained = references.resolve(state, mutation.author, annotation.reference.reference)
                if (retained?.context != annotation.reference ||
                    (annotation.target != null && annotation.target.kind != PingTargetKind.LOCATION && annotation.target !in retained.targets))
                    return GameplayChange(reason = "replayBlocked")
            }
            val rebased = recontextualize(state, mutation.copy(parentId = state.currentNodeId))
            return apply(state, rebased)?.let { GameplayChange(it, rebased) }
            ?: GameplayChange(reason = "replayBlocked")
        }
        if (mutation.id != CommandIds.uuidV5(mutation.requestId, "board-node")) return GameplayChange(reason = "replayBlocked")
        val command = GameCommand(mutation.requestId, GameScope(state.gameId, playId = state.id),
            GameAction.Play(mutation.action), state.id, "0", invocationId = mutation.invocationId)
        val execution = state.copy(currentBranchId = mutation.branchId)
        var candidate = when (mutation.action) {
            is PlayAction.Manual -> ManualEditRules.replay(execution, mutation, command)
            is PlayAction.MoveCard -> GameplayRules.replayMove(execution, mutation, command)
            else -> GameplayRules.legacyChange(execution, mutation.author, command, references)
        }
        // Old step events predate the attention rule. Keep their recorded transition and the
        // legacy fallback, rather than retroactively passing to the defender during cold replay.
        if (mutation.transition.attention == null && mutation.action.let {
                it == PlayAction.NextStep || it == PlayAction.EndTurn || it is PlayAction.ChooseStep }) {
            candidate = candidate.copy(mutation = candidate.mutation?.let {
                it.copy(transition = it.transition.copy(attention = null)) },
                state = candidate.state?.let { it.copy(board = it.board.copy(attentionHolder = state.board.attentionHolder)) })
        }
        return if (mutation.schemaVersion == 1 && candidate.mutation?.transition?.copy(delta = null) == mutation.transition &&
            (mutation.grouping == null || candidate.mutation.grouping == mutation.grouping)) {
            // Tree edits change topology, not logical identities/recorded choices or authorship.
            candidate.copy(mutation = mutation, state = candidate.state!!.copy(currentNodeId = mutation.id,
                currentGroup = append(state.currentGroup, mutation, state.board.containers), currentBranchId = mutation.branchId))
        } else GameplayChange(reason = "replayBlocked")
    }

    /** Exact recorded inverse for traversal; selective Undo selection/topology/suffix validation remains I23. */
    fun reverse(state: PlayState, mutation: GameplayMutation): BoardState? {
        if (mutation.schemaVersion == 2) return GameplayDeltas.apply(state, mutation.transition.delta ?: return null, true)
            ?.takeIf { readsMatch(it, mutation) && mutation.transition.delta.seal == GameplayDeltas.seal(mutation) }?.board
        if (mutation.schemaVersion != 1) return null
        // This scalar/movement helper must never return a partial inverse for a merged family.
        val transition = mutation.transition
        if (transition.libraryOrder != null || transition.contents != null || transition.random != null ||
            transition.annotation != null || transition.tokens.isNotEmpty() || transition.links.isNotEmpty() ||
            transition.cloneRead != null || transition.tokenMoveRead != null || transition.combatStack != null) return null
        for (read in mutation.transition.reads) {
            val card = state.board.cards.singleOrNull { it.id == read.targetId } ?: return null
            val value = when (read.field) {
                GameplayField.FACE -> card.faceId
                GameplayField.COVERED -> card.covered.toString()
                GameplayField.EXPLICITLY_COVERED -> card.explicitlyCovered.toString()
                GameplayField.CONTAINER -> card.containerId
                else -> return null
            }
            if (value != read.value) return null
        }
        var board = state.board
        transition.attention?.let {
            if (board.attentionHolder != it.to) return null
            board = board.copy(attentionHolder = it.from)
        }
        for (field in mutation.transition.fields.asReversed()) {
            when (field.field) {
                GameplayField.LIFE -> {
                    val player = board.players.singleOrNull { it.accountId == field.targetId } ?: return null
                    if (player.life != field.to || field.from == null) return null
                    board = board.copy(players = board.players.map { if (it == player) it.copy(life = field.from) else it })
                }
                GameplayField.PLAYER_COUNTER -> {
                    val player = board.players.singleOrNull { it.accountId == field.targetId } ?: return null
                    val color = field.color ?: return null
                    if (player.counters[color] != field.to) return null
                    board = board.copy(players = board.players.map { if (it == player) it.copy(counters =
                        if (field.from == null) it.counters - color else it.counters + (color to field.from)) else it })
                }
                else -> {
                    val card = board.cards.singleOrNull { it.id == field.targetId } ?: return null
                    val current = when (field.field) {
                        GameplayField.TAPPED -> card.tapped.toString()
                        GameplayField.CORNER -> card.corner
                        GameplayField.COUNTER -> card.counters[field.color]
                        GameplayField.NOTE -> card.note
                        GameplayField.OWNER -> card.owner
                        GameplayField.FACE -> card.faceId
                        GameplayField.COVERED -> card.covered.toString()
                        GameplayField.EXPLICITLY_COVERED -> card.explicitlyCovered.toString()
                        else -> return null
                    }
                    if (current != field.to) return null
                    val next = when (field.field) {
                        GameplayField.TAPPED -> card.copy(tapped = field.from?.toBooleanStrictOrNull() ?: return null)
                        GameplayField.CORNER -> card.copy(corner = field.from ?: return null)
                        GameplayField.COUNTER -> card.copy(counters = if (field.from == null) card.counters - (field.color ?: return null)
                            else card.counters + ((field.color ?: return null) to field.from))
                        GameplayField.NOTE -> card.copy(note = field.from ?: return null)
                        GameplayField.OWNER -> card.copy(owner = field.from ?: return null)
                        GameplayField.FACE -> card.copy(faceId = field.from ?: return null)
                        GameplayField.COVERED -> card.copy(covered = field.from?.toBooleanStrictOrNull() ?: return null)
                        GameplayField.EXPLICITLY_COVERED -> card.copy(explicitlyCovered = field.from?.toBooleanStrictOrNull() ?: return null)
                        else -> return null
                    }
                    board = board.copy(cards = board.cards.map { if (it == card) next else it })
                }
            }
        }
        val move = mutation.transition.movement ?: return board
        if (board.cards.none { it.id == move.cardId } || GameplayRules.location(board, move.cardId, orderedHand = move.coverRulesVersion >= 3) != move.to) return null
        if (move.stackTo != null && GameplayRules.stackDigest(state.copy(board = board)) != move.stackTo) return null
        val source = board.containers.singleOrNull { it.id == move.from.containerId } ?: return null
        val members = GameplayRules.members(board, source, move.from.placement).filter { it != move.cardId }
        val unordered = source.zone in setOf(PlayZone.GRAVEYARD, PlayZone.EXILE) || source.zone == PlayZone.HAND && move.coverRulesVersion < 3
        val insert = if (unordered) (members + move.cardId).sorted() else {
            val left = move.from.afterId?.let { members.indexOf(it).takeIf { index -> index >= 0 } ?: return null } ?: -1
            if (members.getOrNull(left + 1) != move.from.beforeId) return null
            members.toMutableList().also { it.add(left + 1, move.cardId) }
        }
        if (source.zone == PlayZone.BATTLEFIELD) {
            if (move.from.placement == null || members.size >= 3) return null
            if (board.cards.any { it.id != move.cardId && it.containerId == source.id &&
                it.placement?.position == move.from.placement.position && it.placement.pileId != move.from.placement.pileId }) return null
        }
        board = board.copy(cards = board.cards.map { if (it.id == move.cardId)
            it.copy(containerId = move.from.containerId, placement = move.from.placement) else it },
            containers = board.containers.map { container -> when (container.id) {
                source.id -> container.copy(cards = if (source.zone == PlayZone.BATTLEFIELD)
                    container.cards.filter { it != move.cardId && it !in members } + insert else insert)
                move.to.containerId -> container.copy(cards = container.cards.filter { it != move.cardId })
                else -> container
            } })
        if (move.stackFrom != null && GameplayRules.stackDigest(state.copy(board = board)) != move.stackFrom) return null
        return GameplayRules.canonicalBoard(board)
    }
    /** Exact lifecycle/link-only inverse. Compound combat/Stack effects require I23's shared traversal;
     * never partially undo them here. Shared Undo eligibility remains the history owner's job. */
    fun inverseTokenLinks(board: BoardState, transition: GameplayTransition): BoardState? =
        TokenLinkRules.inverse(board, transition.copy(delta = null))

    /** Runtime traversal owner must compose ALL families for cleanup nodes, or cold-replay from root.
     * This hook deliberately refuses a compound inverse rather than returning a partially restored board. */
    fun inverseProjectionLinks(board: BoardState, transition: GameplayTransition): BoardState? {
        val effects = transition.combatStack ?: return null
        if (transition.copy(combatStack = null, delta = null) != GameplayTransition() ||
            effects.copy(removedLinks = emptyList(), addedLinks = emptyList()) != CombatStackTransition()) return null
        if (effects.removedLinks.isEmpty() && effects.addedLinks.isEmpty()) return null
        var next = board
        for (link in effects.addedLinks.asReversed()) {
            if (next.projectionLinks.singleOrNull { it.id == link.id } != link) return null
            next = next.copy(projectionLinks = next.projectionLinks - link)
        }
        for (link in effects.removedLinks) {
            if (TokenLinkRules.occupied(next, link.id) ||
                !CombatStackRules.exists(next, ProjectionEndpoint(link.first)) ||
                !CombatStackRules.exists(next, ProjectionEndpoint(link.second)) ||
                next.projectionLinks.any { CombatStackRules.projectionPair(it.first, it.second) ==
                    CombatStackRules.projectionPair(link.first, link.second) }) return null
            next = next.copy(projectionLinks = (next.projectionLinks + link).sortedBy { it.id })
        }
        return next.takeIf { CombatStackRules.closeInvariants(board, it) == it }
    }

    /** Exact link tuples for the runtime owner's composite inverse/reference accounting adapter.
     * Includes automatic incident cleanup, never substitutes source IDs. */
    fun projectionLinkChanges(transition: GameplayTransition): List<ProjectionLinkChange> =
        transition.combatStack?.let { effects -> effects.removedLinks.map { ProjectionLinkChange(it, null) } +
            effects.addedLinks.map { ProjectionLinkChange(null, it) } }.orEmpty()

    fun projectionLinkReferences(transition: GameplayTransition): Set<String> =
        projectionLinkChanges(transition).flatMap { change -> listOfNotNull(change.from, change.to).flatMap {
            listOf(it.id, it.first, it.second)
        } }.toSet()

    /** Relevant projection-link fan-out, also used by transition inspection tests. */
    fun projectionLinkWorkUnits(transition: GameplayTransition): Long =
        transition.combatStack?.let { it.removedLinks.size.toLong() + it.addedLinks.size } ?: 0L
}
