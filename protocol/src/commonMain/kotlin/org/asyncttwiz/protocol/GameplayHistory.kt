package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** Exact relevant (from,to) tuples; fields absent from these effects are unchanged. No whole-card snapshots. */
@Serializable enum class GameplayField { TAPPED, LIFE, CORNER, COUNTER, PLAYER_COUNTER, NOTE, OWNER, FACE, COVERED, CONTAINER,
    PHASE, STEP, TURN_PLAYER, TURN_NUMBER, EXPLICITLY_COVERED, DAMAGE, EOT_CORNER, EOT_NOTE, STATUS }
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
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class GameplayDisclosure(val cardId: String, val faceId: String, val containerId: String,
    val viewers: List<String>,
    /** Continuous top exposure is information, but not a separate Reveal playback stop. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val automatic: Boolean = false)
@Serializable data class AttentionChange(val from: String?, val to: String)
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class GameplayTransition(val fields: List<GameplayFieldChange> = emptyList(),
    val movement: GameplayMoveChange? = null, val libraryOrder: LibraryOrderChange? = null,
    val contents: ContentsDisplayChange? = null, val random: GameplayRandomResult? = null,
    val annotation: AnnotationTransition? = null, val reads: List<GameplayRead> = emptyList(),
    val disclosures: List<GameplayDisclosure> = emptyList(), val tokens: List<TokenChange> = emptyList(),
    val links: List<LinkChange> = emptyList(), val cloneRead: CloneRead? = null,
    val tokenMoveRead: TokenMoveRead? = null, val combatStack: CombatStackTransition? = null,
    val resultSuggestion: ResultSuggestionChange? = null, val attention: AttentionChange? = null,
    val delta: GameplayDelta? = null,
    /** Version 2 expires contents visibility and dismisses emptied displays in the removing event.
     * Omit the legacy default so existing event seals and retained deltas remain unchanged. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val contentsRulesVersion: Int = 1,
    /** Absent on retained v2 events: their validation and seals must not change. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val concerns: ConcernSet? = null,
    /** New acceptance rules only. Retained events are applied from their sealed deltas, never re-executed. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val rulesVersion: Int = 1)
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
    (combatStack?.let { it.occurrences.size + it.pairs.size + it.effects.size + it.order.size + it.textMarks.size + it.attackTargets.size +
        listOfNotNull(it.area, it.stackGuard).size } ?: 0) + GameplayHistory.projectionLinkWorkUnits(this) +
    delta?.libraryReseeds.orEmpty().sumOf { it.from.size.toLong() + it.to.size }
/** Compact prefix context, not a recursive history or client-assigned group. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class GameplayGroup(val id: String, val author: String, val firstNodeId: String,
    val lastNodeId: String, val category: String, val key: String?, val count: String,
    val tapsAndPingsOnly: Boolean = category == "tap", val representative: HistoryRepresentative? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val panelSession: String? = null,
    /** Only the immediately preceding event, not any earlier move in this group. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val lastMoveIntoLibrary: String? = null,
    /** The casting anchor only; text edits never establish casting context themselves. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val stackEntryId: String? = null)
@Serializable data class GameplayHistoryPage(val playId: String, val mutations: List<GameplayMutation>,
    val groups: List<GameplayGroup>, val throughRevision: String, val next: String? = null)

object GameplayHistory {
    /** No rule execution: all choices and exact inverse data were recorded at acceptance. */
    fun apply(state: PlayState, mutation: GameplayMutation, work: GameplayWork? = null): PlayState? {
        if (mutation.schemaVersion != 2 || state.currentNodeId != mutation.parentId ||
            mutation.author !in state.board.players.map { it.accountId } ||
            mutation.id != CommandIds.uuidV5(mutation.requestId, "board-node") ||
            mutation.transition.delta?.seal != GameplayDeltas.seal(mutation)) return null
        val context = mutation.bookkeeping ?: return null
        if (state.currentGroup != context.fromGroup || state.currentBranchId != context.fromBranchId ||
            !GameplayConcerns.matches(state, mutation, false) ||
            !readsMatch(state, mutation)) return null
        val next = GameplayDeltas.apply(state, mutation.transition.delta, false, mutation, work) ?: return null
        if (!GameplayConcerns.matches(next, mutation, true)) return null
        return next.copy(currentNodeId = mutation.id, currentBranchId = mutation.branchId, currentGroup = context.toGroup)
    }

    fun unapply(state: PlayState, mutation: GameplayMutation, work: GameplayWork? = null): PlayState? {
        if (mutation.schemaVersion != 2 || state.currentNodeId != mutation.id ||
            mutation.id != CommandIds.uuidV5(mutation.requestId, "board-node") ||
            mutation.transition.delta?.seal != GameplayDeltas.seal(mutation)) return null
        val context = mutation.bookkeeping ?: return null
        if (state.currentGroup != context.toGroup || state.currentBranchId != mutation.branchId) return null
        if (!GameplayConcerns.matches(state, mutation, true)) return null
        val previous = GameplayDeltas.apply(state, mutation.transition.delta, true, mutation, work) ?: return null
        if (!GameplayConcerns.matches(previous, mutation, false)) return null
        if (!readsMatch(previous, mutation)) return null
        return previous.copy(currentNodeId = mutation.parentId, currentBranchId = context.fromBranchId, currentGroup = context.fromGroup)
    }

    internal fun recontextualize(state: PlayState, mutation: GameplayMutation): GameplayMutation = mutation.copy(
        bookkeeping = GameplayBookkeeping(state.currentGroup, state.currentBranchId,
            append(state.currentGroup, mutation, state.board.containers),
            if (mutation.transition.concerns != null && mutation.action is PlayAction.MoveCard)
                mutation.transition.tokens.mapNotNull { token -> token.from?.card?.id?.takeIf { token.to == null }?.let { id ->
                    state.board.cards.singleOrNull { it.id == id }?.owner?.let { id to it }
                } }.toMap() else emptyMap()))

    /** Only selective suffix replay may rebase a fork's inverse reseed input. All action effects and
     * the accepted output order stay exact; normal traversal continues to validate the sealed tuple. */
    internal fun recontextualizeRetained(state: PlayState, mutation: GameplayMutation): GameplayMutation? {
        val delta = mutation.transition.delta ?: return null
        if (mutation.transition.concerns != null) return recontextualize(state, mutation)
        if (delta.libraryReseeds.isEmpty()) return recontextualize(state, mutation)
        if (delta.seal != GameplayDeltas.seal(mutation)) return null
        val reseeds = LibraryKnowledge.rebaseReseeds(state, delta.libraryReseeds) ?: return null
        val retained = mutation.copy(transition = mutation.transition.copy(delta = delta.copy(libraryReseeds = reseeds)))
        val sealed = retained.copy(transition = retained.transition.copy(
            delta = retained.transition.delta!!.copy(seal = GameplayDeltas.seal(retained))))
        return recontextualize(state, sealed)
    }

    private fun readsMatch(state: PlayState, mutation: GameplayMutation): Boolean {
        val board = (LibraryKnowledge.applyReseeds(state, mutation.transition.delta?.libraryReseeds.orEmpty(), false)
            ?: return false).board
        if (mutation.transition.concerns == null && mutation.grouping != null && mutation.grouping != runCatching {
            HistoryGrouping.classify(mutation.action, board, mutation.author, mutation.transition)
        }.getOrNull()) return false
        for (field in mutation.transition.fields) {
            val card = board.cards.singleOrNull { it.id == field.targetId }
            val player = board.players.singleOrNull { it.accountId == field.targetId }
            val value = when (field.field) {
                GameplayField.LIFE -> player?.life
                GameplayField.PLAYER_COUNTER -> player?.counters?.get(field.color)
                GameplayField.STATUS -> player?.status
                GameplayField.TAPPED -> card?.tapped?.toString()
                GameplayField.CORNER -> card?.corner
                GameplayField.DAMAGE -> card?.damage
                GameplayField.EOT_CORNER -> card?.eotCorner
                GameplayField.EOT_NOTE -> card?.eotNote
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
        mutation.transition.contents?.to?.takeIf { mutation.action is PlayAction.ShowContents }?.let { display ->
            val container = board.containers.singleOrNull { it.id == display.containerId } ?: return false
            val slice = LibraryRules.slice(container.cards, display.selection, display.requestedCount)
            if ((if (display.selection == ContentsSelection.ALL) slice.sorted() else slice) != display.cardIds) return false
        }
        for (link in mutation.transition.links.mapNotNull { it.to }) {
            if (!TokenLinkRules.exists(board, link.first) || !TokenLinkRules.exists(board, link.second)) return false
        }
        mutation.transition.movement?.let { move ->
            if (board.cards.none { it.id == move.cardId }) return false
            val location = GameplayRules.location(board, move.cardId, orderedHand = true)
            if (if (GameplayConcerns.knownTopDraw(mutation)) location.copy(beforeId = move.from.beforeId) != move.from
                else location != move.from) return false
            if (move.stackFrom != null && GameplayRules.stackDigest(state) != move.stackFrom) return false
        }
        mutation.transition.combatStack?.stackGuard?.let { if (GameplayRules.stackDigest(state) != it.from) return false }
        mutation.transition.annotation?.let { annotation ->
            // Records and ping ordinals are keyed by the accepted group. Until they can be
            // migrated together, refuse regrouping rather than strand retained annotations.
            if (append(state.currentGroup, mutation, board.containers).id != annotation.groupId ||
                mutation.branchId != annotation.branchId || state.id != annotation.playId) return false
        }
        for (link in mutation.transition.combatStack?.addedLinks.orEmpty()) {
            if (!CombatStackRules.exists(board, ProjectionEndpoint(link.first)) ||
                !CombatStackRules.exists(board, ProjectionEndpoint(link.second))) return false
        }
        when (val action = mutation.action) {
            is PlayAction.DeclineResult -> {
                if (mutation.transition.rulesVersion < 22 || board.resultSuggestion?.id != action.suggestionId ||
                    board.resultSuggestion?.author == mutation.author) return false
            }
            is PlayAction.WithdrawResult -> {
                if (mutation.transition.rulesVersion < 22 || board.resultSuggestion?.id != action.suggestionId ||
                    board.resultSuggestion?.author != mutation.author) return false
            }
            is PlayAction.Stack -> when (val stack = action.action) {
                is StackAction.AddEffect -> {
                    val source = board.cards.singleOrNull { it.id == stack.sourceId } ?: return false
                    if (!CombatStackRules.canProject(board, source, mutation.transition.rulesVersion) ||
                        GameplayRules.stackDigest(state) != stack.expectedStackDigest) return false
                }
                is StackAction.CopyEffect -> if (stack.entryId !in CombatStackRules.stack(board).cards ||
                    GameplayRules.stackDigest(state) != stack.expectedStackDigest) return false
                is StackAction.SetController -> if (board.players.none { it.accountId == stack.accountId }) return false
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
        references: EventReferenceResolver = EventReferenceResolver.UNAVAILABLE, work: GameplayWork? = null): GameplayChange {
        if (mutation.bookkeeping == null || mutation.transition.delta == null) return GameplayChange(reason = "replayBlocked")
        if (mutation.schemaVersion == 2) {
            mutation.transition.annotation?.target?.takeIf { mutation.transition.concerns != null }?.let { target ->
                if (!references.targetPresent(state, mutation.author, mutation.transition.annotation.reference.reference, target))
                    return GameplayChange(reason = "replayBlocked")
            }
            if (mutation.transition.concerns != null && mutation.action is PlayAction.EventComment &&
                references.context(state, mutation.author, mutation.action.reference) != mutation.transition.annotation?.reference)
                return GameplayChange(reason = "replayBlocked")
            mutation.transition.annotation?.takeIf { mutation.transition.concerns == null }?.let { annotation ->
                val retained = references.resolve(state, mutation.author, annotation.reference.reference)
                if (retained?.context != annotation.reference ||
                    (annotation.target != null && annotation.target.kind != PingTargetKind.LOCATION && annotation.target !in retained.targets))
                    return GameplayChange(reason = "replayBlocked")
            }
            val rebased = recontextualize(state, mutation.copy(parentId = state.currentNodeId))
            return apply(state, rebased, work)?.let { GameplayChange(it, rebased) }
            ?: GameplayChange(reason = "replayBlocked")
        }
        return GameplayChange(reason = "replayBlocked")
    }

    /** Exact recorded inverse for traversal; selective Undo selection/topology/suffix validation remains I23. */
    fun reverse(state: PlayState, mutation: GameplayMutation): BoardState? {
        if (mutation.schemaVersion == 2 && GameplayConcerns.matches(state, mutation, true))
            return GameplayDeltas.apply(state, mutation.transition.delta ?: return null, true, mutation)
                ?.takeIf { readsMatch(it, mutation) && GameplayConcerns.matches(it, mutation, false) &&
                    mutation.transition.delta.seal == GameplayDeltas.seal(mutation) }?.board
        return null
    }
    /** Exact lifecycle/link-only inverse. Compound combat/Stack effects require I23's shared traversal;
     * never partially undo them here. Shared Undo eligibility remains the history owner's job. */
    fun inverseTokenLinks(board: BoardState, transition: GameplayTransition): BoardState? =
        TokenLinkRules.inverse(board, transition.copy(delta = null))

    /** Runtime traversal owner must compose ALL families for cleanup nodes, or cold-replay from root.
     * This hook deliberately refuses a compound inverse rather than returning a partially restored board. */
    fun inverseProjectionLinks(board: BoardState, transition: GameplayTransition): BoardState? {
        val effects = transition.combatStack ?: return null
        if (transition.copy(combatStack = null, delta = null, concerns = null, rulesVersion = 1) != GameplayTransition() ||
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
