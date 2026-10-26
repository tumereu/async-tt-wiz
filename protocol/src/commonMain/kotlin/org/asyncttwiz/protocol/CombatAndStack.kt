@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package org.asyncttwiz.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable enum class CombatRole { ATTACKER, BLOCKER }
@Serializable data class CombatOccurrence(val id: String, val sourceId: String,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val note: String = "")
@Serializable data class CombatPair(val id: String, val attackers: List<CombatOccurrence> = emptyList(),
    val blockers: List<CombatOccurrence> = emptyList()) {
    fun side(role: CombatRole) = if (role == CombatRole.ATTACKER) attackers else blockers
    fun withSide(role: CombatRole, cards: List<CombatOccurrence>) =
        if (role == CombatRole.ATTACKER) copy(attackers = cards) else copy(blockers = cards)
}
@Serializable data class CombatArea(val id: String, val pairs: List<CombatPair> = emptyList())
/** Only disappearance retains card data. Live effects never cache fields or a creation-time snapshot. */
@Serializable data class StackEffect(val id: String, val sourceId: String, val frozen: PlayCard? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val controller: String? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val isCopy: Boolean = false,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val note: String = "")
/** D-120 projection-to-projection links, not the source relationship and not selection. */
@Serializable data class ProjectionEndpoint(val occurrenceId: String)
@Serializable data class ProjectionLink(val id: String, val first: String, val second: String)
@Serializable data class ProjectionLinkChange(val from: ProjectionLink?, val to: ProjectionLink?)
@Serializable data class IdentityGap(val afterId: String? = null, val beforeId: String? = null)
@Serializable enum class CombatInsertion { APPEND, GAP }
/** A null pair names a new pair at pairGap. No empty shared pair is created ahead of placement. */
@Serializable data class CombatDestination(val areaId: String, val role: CombatRole,
    val pairId: String? = null, val pairGap: IdentityGap = IdentityGap(),
    val insertion: CombatInsertion = CombatInsertion.APPEND, val cardGap: IdentityGap = IdentityGap(),
    val pendingSourceIds: List<String> = emptyList())

@Serializable sealed interface CombatAction {
    @Serializable @SerialName("open") data object Open : CombatAction
    @Serializable @SerialName("close") data class Close(val areaId: String?) : CombatAction
    @Serializable @SerialName("declare") data class Declare(val areaId: String, val sourceId: String) : CombatAction
    @Serializable @SerialName("place") data class Place(val sourceId: String, val destination: CombatDestination,
        val expectedOccurrence: CombatLocation? = null) : CombatAction
    @Serializable @SerialName("remove") data class Remove(val areaId: String, val occurrenceId: String) : CombatAction
}
@Serializable sealed interface StackAction {
    @Serializable @SerialName("markText") data class MarkText(val entryId: String, val optionIndex: Int?) : StackAction
    @Serializable @SerialName("toggleTextMark") data class ToggleTextMark(val entryId: String, val optionIndex: Int) : StackAction
    @Serializable @SerialName("addEffect") data class AddEffect(val sourceId: String, val expectedStackDigest: String) : StackAction
    @Serializable @SerialName("copyEffect") data class CopyEffect(val entryId: String, val expectedStackDigest: String) : StackAction
    @Serializable @SerialName("setController") data class SetController(val occurrenceId: String, val accountId: String) : StackAction
    @Serializable @SerialName("removeEffect") data class RemoveEffect(val occurrenceId: String) : StackAction
    // Legacy: retained history replay/undo only; the server rejects new reorder commands.
    @Serializable @SerialName("reorderEffect") data class ReorderEffect(val occurrenceId: String,
        val expected: IdentityGap, val destination: IdentityGap) : StackAction
}

@Serializable data class CombatLocation(val areaId: String, val pairId: String, val role: CombatRole,
    val gap: IdentityGap)
@Serializable data class CombatOccurrenceState(val occurrence: CombatOccurrence, val location: CombatLocation)
@Serializable data class CombatOccurrenceChange(val id: String, val from: CombatOccurrenceState?, val to: CombatOccurrenceState?)
@Serializable data class CombatPairPosition(val areaId: String, val gap: IdentityGap)
@Serializable data class CombatPairChange(val id: String, val from: CombatPairPosition?, val to: CombatPairPosition?)
@Serializable data class CombatAreaChange(val from: String?, val to: String?)
@Serializable data class StackEffectChange(val id: String, val from: StackEffect?, val to: StackEffect?)
@Serializable data class StackOrderChange(val id: String, val from: IdentityGap?, val to: IdentityGap?)
@Serializable data class StackGuardChange(val from: String, val to: String)
@Serializable data class StackTextMarkChange(val entryId: String, val from: StackTextMarks?, val to: StackTextMarks?)
/** Exact relevant tuples: no unrelated card fields, full board, or surviving pair contents. */
@Serializable data class CombatStackTransition(val area: CombatAreaChange? = null,
    val occurrences: List<CombatOccurrenceChange> = emptyList(), val pairs: List<CombatPairChange> = emptyList(),
    val effects: List<StackEffectChange> = emptyList(), val order: List<StackOrderChange> = emptyList(),
    val removedLinks: List<ProjectionLink> = emptyList(), val stackGuard: StackGuardChange? = null,
    val addedLinks: List<ProjectionLink> = emptyList(),
    val textMarks: List<StackTextMarkChange> = emptyList())

@Serializable data class StackIdentity(val kind: String, val id: String, val sourceId: String)
@Serializable private data class StackProjection(val playId: String, val entries: List<StackIdentity>)

object CombatStackRules {
    fun projectionNote(board: BoardState, id: String): String? = board.stackEffects.singleOrNull { it.id == id }?.note
        ?: occurrences(board.combat).singleOrNull { it.id == id }?.note
    fun occurrences(area: CombatArea?): List<CombatOccurrence> = area?.pairs.orEmpty().flatMap { it.blockers + it.attackers }
    fun projectionIds(board: BoardState): Set<String> =
        (occurrences(board.combat).map { it.id } + board.stackEffects.map { it.id }).toSet()
    fun exists(board: BoardState, endpoint: ProjectionEndpoint) = endpoint.occurrenceId in projectionIds(board)
    fun projectionPair(first: String, second: String) = if (first <= second) first to second else second to first
    fun stack(board: BoardState) = board.containers.single { it.zone == PlayZone.STACK }
    fun canProject(board: BoardState, card: PlayCard): Boolean {
        val source = board.containers.singleOrNull { it.id == card.containerId } ?: return false
        return source.zone in setOf(PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD, PlayZone.EXILE) ||
            source.zone == PlayZone.HAND && source.accountId == card.owner
    }
    /** Top first; the Stack container holds real card IDs AND independent effect occurrence IDs. */
    fun identities(board: BoardState): List<StackIdentity> = stack(board).cards.map { id ->
        val effect = board.stackEffects.singleOrNull { it.id == id }
        if (effect != null) StackIdentity("effect", id, effect.sourceId) else {
            require(board.cards.singleOrNull { it.id == id }?.containerId == stack(board).id)
            StackIdentity("card", id, id)
        }
    }
    fun stackDigest(playId: String, board: BoardState): String = "sha256:" +
        DeckJson.intent("stack", DeckJson.canonical(StackProjection(playId, identities(board))))
    fun stackDigest(state: PlayState) = stackDigest(state.id, state.board)

    /** Bind source once when the menu opens. A frozen occurrence can never redirect to another card. */
    fun liveSource(board: BoardState, viewId: String): PlayCard? {
        board.cards.singleOrNull { it.id == viewId }?.let { return it }
        val effect = board.stackEffects.singleOrNull { it.id == viewId }
        if (effect?.frozen != null) return null
        val source = effect?.sourceId ?: occurrences(board.combat).singleOrNull { it.id == viewId }?.sourceId
        return board.cards.singleOrNull { it.id == source }
    }
    fun inspection(board: BoardState, viewId: String): PlayCard? =
        board.stackEffects.singleOrNull { it.id == viewId }?.frozen ?: liveSource(board, viewId)
    fun controller(board: BoardState, viewId: String): String? =
        board.stackEffects.singleOrNull { it.id == viewId }?.controller ?: inspection(board, viewId)?.owner
    fun relatedViews(board: BoardState, viewId: String): Set<String> {
        val source = board.cards.find { it.id == viewId }?.id ?: board.stackEffects.find { it.id == viewId }?.sourceId
            ?: occurrences(board.combat).find { it.id == viewId }?.sourceId ?: return emptySet()
        return (listOf(source) + occurrences(board.combat).filter { it.sourceId == source }.map { it.id } +
            board.stackEffects.filter { it.sourceId == source }.map { it.id }).toSet()
    }

    private fun valid(gap: IdentityGap) = listOfNotNull(gap.afterId, gap.beforeId).all(DeckValidation::uuid) &&
        (gap.afterId == null || gap.afterId != gap.beforeId)
    private fun valid(location: CombatLocation) = DeckValidation.uuid(location.areaId) &&
        DeckValidation.uuid(location.pairId) && valid(location.gap)
    fun valid(action: CombatAction): Boolean = when (action) {
        CombatAction.Open -> true
        is CombatAction.Close -> action.areaId == null || DeckValidation.uuid(action.areaId)
        is CombatAction.Declare -> DeckValidation.uuid(action.areaId) && DeckValidation.uuid(action.sourceId)
        is CombatAction.Remove -> DeckValidation.uuid(action.areaId) && DeckValidation.uuid(action.occurrenceId)
        is CombatAction.Place -> with(action.destination) {
            DeckValidation.uuid(action.sourceId) && DeckValidation.uuid(areaId) &&
                (pairId == null || DeckValidation.uuid(pairId)) && valid(pairGap) && valid(cardGap) &&
                pendingSourceIds.distinct().size == pendingSourceIds.size && pendingSourceIds.all(DeckValidation::uuid) &&
                (action.expectedOccurrence == null || valid(action.expectedOccurrence))
        }
    }
    fun valid(action: StackAction): Boolean = when (action) {
        is StackAction.MarkText -> DeckValidation.uuid(action.entryId) && (action.optionIndex == null || action.optionIndex >= 0)
        is StackAction.ToggleTextMark -> DeckValidation.uuid(action.entryId) && action.optionIndex >= 0
        is StackAction.AddEffect -> DeckValidation.uuid(action.sourceId) && DeckValidation.digest(action.expectedStackDigest)
        is StackAction.CopyEffect -> DeckValidation.uuid(action.entryId) && DeckValidation.digest(action.expectedStackDigest)
        is StackAction.SetController -> DeckValidation.uuid(action.occurrenceId) && DeckValidation.uuid(action.accountId)
        is StackAction.RemoveEffect -> DeckValidation.uuid(action.occurrenceId)
        is StackAction.ReorderEffect -> DeckValidation.uuid(action.occurrenceId) && valid(action.expected) && valid(action.destination)
    }

    /** Same indivisible compare/construct function for server execution and provisional prediction.
     * The server's existing transaction/arrival-order processor must call this on its selected state.
     */
    internal fun change(state: PlayState, actor: String, command: GameCommand): GameplayChange {
        fun reject(reason: String) = GameplayChange(reason = reason)
        val board = state.board
        val playAction = (command.action as GameAction.Play).action
        val next = when (playAction) {
            is PlayAction.Combat -> {
                val area = board.combat
                when (val action = playAction.action) {
                    CombatAction.Open -> if (area != null) board else board.copy(
                        combat = CombatArea(CommandIds.uuidV5(command.requestId, "combat-area/0")))
                    is CombatAction.Close -> {
                        // An absent captured area does not close a subsequently opened/replacement area.
                        if (action.areaId == null || area == null) board
                        else if (area.id != action.areaId) return reject("inapplicable")
                        else board.copy(combat = null)
                    }
                    is CombatAction.Remove -> {
                        if (area?.id != action.areaId) return reject("missingReference")
                        if (occurrences(area).none { it.id == action.occurrenceId }) return reject("missingReference")
                        board.copy(combat = without(area, action.occurrenceId))
                    }
                    is CombatAction.Declare, is CombatAction.Place -> {
                        val sourceId = when (action) {
                            is CombatAction.Declare -> action.sourceId
                            is CombatAction.Place -> action.sourceId
                            else -> error("unreachable")
                        }
                        val source = board.cards.singleOrNull { it.id == sourceId } ?: return reject("missingReference")
                        if (board.containers.singleOrNull { it.id == source.containerId }?.zone != PlayZone.BATTLEFIELD)
                            return reject("inapplicable")
                        val areaId = if (action is CombatAction.Declare) action.areaId else (action as CombatAction.Place).destination.areaId
                        if (area?.id != areaId) return reject("missingReference")
                        val existing = occurrences(area).singleOrNull { it.sourceId == sourceId }
                        val location = existing?.let { location(area, it.id) }
                        if (action is CombatAction.Place && location != action.expectedOccurrence) return reject("sourceChanged")
                        if (action is CombatAction.Declare && location?.role == CombatRole.ATTACKER) return GameplayChange(state)
                        val occurrence = existing ?: CombatOccurrence(CommandIds.uuidV5(command.requestId, "combat-occurrence/0"), sourceId)
                        val reduced = without(area, occurrence.id, removeEmpty = false)
                        val destination = if (action is CombatAction.Place) action.destination else CombatDestination(areaId,
                            CombatRole.ATTACKER, pairGap = IdentityGap(reduced.pairs.lastOrNull()?.id))
                        val pairs = reduced.pairs.toMutableList()
                        val pairIndex: Int
                        if (destination.pairId == null) {
                            pairIndex = gapIndex(pairs.map { it.id }, destination.pairGap) ?: return reject("destinationChanged")
                            pairs.add(pairIndex, CombatPair(CommandIds.uuidV5(command.requestId, "combat-pair/0")))
                        } else {
                            pairIndex = pairs.indexOfFirst { it.id == destination.pairId }
                            if (pairIndex < 0) return reject("missingReference")
                        }
                        val pair = pairs[pairIndex]
                        val side = pair.side(destination.role).toMutableList()
                        val index = if (destination.insertion == CombatInsertion.APPEND) side.size else {
                            val filtered = side.filterNot { it.sourceId in destination.pendingSourceIds }
                            val at = gapIndex(filtered.map { it.id }, destination.cardGap) ?: return reject("destinationChanged")
                            if (at == 0) 0 else side.indexOfFirst { it.id == filtered[at - 1].id } + 1
                        }
                        side.add(index, occurrence)
                        pairs[pairIndex] = pair.withSide(destination.role, side)
                        board.copy(combat = area.copy(pairs = pairs.filterNot(::empty)))
                    }
                }
            }
            is PlayAction.Stack -> when (val action = playAction.action) {
                is StackAction.MarkText, is StackAction.ToggleTextMark -> {
                    val entryId = if (action is StackAction.MarkText) action.entryId else (action as StackAction.ToggleTextMark).entryId
                    val index = if (action is StackAction.MarkText) action.optionIndex else (action as StackAction.ToggleTextMark).optionIndex
                    if (entryId !in stack(board).cards) return reject("missingReference")
                    if (index != null && index !in StackText.options(state, entryId).indices)
                        return reject("invalidInput")
                    val old = board.stackTextMarks[entryId].orEmpty()
                    val marks = if (action is StackAction.MarkText) setOfNotNull(index)
                        else if (index in old) old - index!! else old + index!!
                    board.copy(stackTextMarks = if (marks.isEmpty()) board.stackTextMarks - entryId
                        else board.stackTextMarks + (entryId to marks))
                }
                is StackAction.AddEffect -> {
                    if (stackDigest(state) != action.expectedStackDigest) return reject("stackChanged")
                    val source = board.cards.singleOrNull { it.id == action.sourceId } ?: return reject("missingReference")
                    if (!canProject(board, source)) return reject("inapplicable")
                    val effect = StackEffect(CommandIds.uuidV5(command.requestId, "stack-effect/0"), source.id)
                    if (effect.id in stack(board).cards || board.cards.any { it.id == effect.id }) return reject("invalidStructure")
                    board.copy(stackEffects = board.stackEffects + effect,
                        containers = board.containers.map { if (it.zone == PlayZone.STACK) it.copy(cards = listOf(effect.id) + it.cards) else it })
                }
                is StackAction.RemoveEffect -> {
                    if (board.stackEffects.none { it.id == action.occurrenceId }) return reject("missingReference")
                    board.copy(stackEffects = board.stackEffects.filterNot { it.id == action.occurrenceId },
                        containers = board.containers.map { if (it.zone == PlayZone.STACK)
                            it.copy(cards = it.cards - action.occurrenceId) else it })
                }
                is StackAction.CopyEffect -> {
                    if (stackDigest(state) != action.expectedStackDigest) return reject("stackChanged")
                    if (action.entryId !in stack(board).cards) return reject("missingReference")
                    val source = inspection(board, action.entryId) ?: return reject("missingReference")
                    val original = board.stackEffects.singleOrNull { it.id == action.entryId }
                    val effect = StackEffect(CommandIds.uuidV5(command.requestId, "stack-effect/0"), source.id,
                        original?.frozen, controller(board, action.entryId), isCopy = true)
                    board.copy(stackEffects = board.stackEffects + effect,
                        containers = board.containers.map { if (it.zone == PlayZone.STACK) it.copy(cards = listOf(effect.id) + it.cards) else it })
                }
                is StackAction.SetController -> {
                    if (board.players.none { it.accountId == action.accountId }) return reject("missingReference")
                    val effect = board.stackEffects.singleOrNull { it.id == action.occurrenceId } ?: return reject("missingReference")
                    if (controller(board, effect.id) == action.accountId) board else
                        board.copy(stackEffects = board.stackEffects.map { if (it.id == effect.id) it.copy(controller = action.accountId) else it })
                }
                is StackAction.ReorderEffect -> {
                    if (board.stackEffects.none { it.id == action.occurrenceId }) return reject("missingReference")
                    val stack = stack(board)
                    if (gap(stack.cards, action.occurrenceId) != action.expected) return reject("sourceChanged")
                    val remaining = stack.cards - action.occurrenceId
                    val index = gapIndex(remaining, action.destination) ?: return reject("destinationChanged")
                    val order = remaining.toMutableList().apply { add(index, action.occurrenceId) }
                    board.copy(containers = board.containers.map { if (it.id == stack.id) it.copy(cards = order) else it })
                }
            }
            else -> return reject("inapplicable")
        }
        if (next == board) return GameplayChange(state)
        val source = ((playAction as? PlayAction.Stack)?.action as? StackAction.AddEffect)?.sourceId
            ?.let { id -> board.cards.singleOrNull { it.id == id } }
        val viewers = source?.let { CardVisibility.viewers(next, it) - CardVisibility.viewers(board, it).toSet() }.orEmpty()
        return GameplayRules.record(state, actor, command, next, GameplayTransition(disclosures =
            if (source != null && viewers.isNotEmpty()) listOf(GameplayDisclosure(source.id, source.faceId, source.containerId, viewers))
            else emptyList()))
    }

    private fun empty(pair: CombatPair) = pair.attackers.isEmpty() && pair.blockers.isEmpty()
    private fun without(area: CombatArea, id: String, removeEmpty: Boolean = true) = area.copy(pairs = area.pairs.map {
        it.copy(attackers = it.attackers.filterNot { card -> card.id == id }, blockers = it.blockers.filterNot { card -> card.id == id })
    }.filterNot { removeEmpty && empty(it) })
    private fun gap(ids: List<String>, id: String): IdentityGap {
        val index = ids.indexOf(id); require(index >= 0)
        return IdentityGap(ids.getOrNull(index - 1), ids.getOrNull(index + 1))
    }
    internal fun gapIndex(ids: List<String>, gap: IdentityGap): Int? {
        val index = if (gap.afterId == null) 0 else ids.indexOf(gap.afterId).takeIf { it >= 0 }?.plus(1) ?: return null
        return index.takeIf { ids.getOrNull(it) == gap.beforeId }
    }
    fun location(area: CombatArea, id: String): CombatLocation? {
        for (pair in area.pairs) for (role in CombatRole.entries) if (pair.side(role).any { it.id == id })
            return CombatLocation(area.id, pair.id, role, gap(pair.side(role).map { it.id }, id))
        return null
    }

    /** Called by the shared record boundary, including future real-move/token reducers. */
    internal fun closeInvariants(before: BoardState, candidate: BoardState): BoardState? {
        val vanished = before.cards.filter { old -> candidate.cards.none { it.id == old.id } }
        if (vanished.any { !it.token }) return null
        val battlefield = candidate.containers.filter { it.zone == PlayZone.BATTLEFIELD }.map { it.id }.toSet()
        val live = candidate.cards.associateBy { it.id }
        val combat = candidate.combat?.let { area -> area.copy(pairs = area.pairs.map { pair ->
            fun keep(cards: List<CombatOccurrence>) = cards.filter { live[it.sourceId]?.containerId in battlefield }
            pair.copy(attackers = keep(pair.attackers), blockers = keep(pair.blockers))
        }.filterNot(::empty)) }
        val effects = candidate.stackEffects.map { effect ->
            val old = vanished.singleOrNull { it.id == effect.sourceId }
            if (old != null && effect.frozen == null) effect.copy(frozen = old) else effect
        }
        val allOccurrences = occurrences(combat)
        if (allOccurrences.map { it.sourceId }.distinct().size != allOccurrences.size ||
            allOccurrences.map { it.id }.distinct().size != allOccurrences.size ||
            combat?.pairs.orEmpty().map { it.id }.distinct().size != combat?.pairs.orEmpty().size ||
            effects.map { it.id }.distinct().size != effects.size || effects.any {
                it.controller != null && candidate.players.none { player -> player.accountId == it.controller }
            } || effects.any {
                if (it.frozen == null) live[it.sourceId] == null
                else it.frozen.id != it.sourceId || !it.frozen.token || live[it.sourceId] != null
            }) return null
        val ids = (occurrences(combat).map { it.id } + effects.map { it.id }).toSet()
        if (ids.size != allOccurrences.size + effects.size || ids.any { it in live }) return null
        val entityIds = candidate.cards.map { it.id } + candidate.containers.map { it.id } + ids +
            listOfNotNull(combat?.id) + combat?.pairs.orEmpty().map { it.id }
        if (entityIds.distinct().size != entityIds.size) return null
        val stack = candidate.containers.singleOrNull { it.zone == PlayZone.STACK }
        if (effects.isNotEmpty() && stack == null) return null
        if (stack != null && (stack.cards.distinct().size != stack.cards.size ||
                effects.any { it.id !in stack.cards } || stack.cards.any { id ->
                    effects.none { it.id == id } && live[id]?.containerId != stack.id
                })) return null
        val links = candidate.projectionLinks.filter { it.first in ids && it.second in ids }
        if (links.any { it.first == it.second } || links.map { projectionPair(it.first, it.second) }.distinct().size != links.size)
            return null
        val allIds = entityIds + candidate.players.map { it.accountId } + candidate.links.map { it.id } + links.map { it.id }
        if (allIds.distinct().size != allIds.size) return null
        return candidate.copy(combat = combat, stackEffects = effects, projectionLinks = links,
            stackTextMarks = candidate.stackTextMarks.filterKeys { it in stack?.cards.orEmpty() })
    }

    internal fun transition(state: PlayState, next: BoardState, action: PlayAction): CombatStackTransition? {
        val before = state.board
        val oldOccurrences = occurrences(before.combat).associateBy { it.id }
        val newOccurrences = occurrences(next.combat).associateBy { it.id }
        val movedSource = ((action as? PlayAction.Combat)?.action as? CombatAction.Place)?.sourceId
            ?: ((action as? PlayAction.Combat)?.action as? CombatAction.Declare)?.sourceId
        val changed = (oldOccurrences.keys + newOccurrences.keys).filter { id ->
            oldOccurrences[id] != newOccurrences[id] || oldOccurrences[id]?.sourceId == movedSource
        }
        fun occurrence(board: BoardState, id: String) = occurrences(board.combat).find { it.id == id }?.let {
            CombatOccurrenceState(it, location(board.combat!!, id)!!)
        }
        val occurrenceChanges = changed.map { CombatOccurrenceChange(it, occurrence(before, it), occurrence(next, it)) }
            .filter { it.from != it.to }
        val oldPairs = before.combat?.pairs.orEmpty().map { it.id }
        val newPairs = next.combat?.pairs.orEmpty().map { it.id }
        fun pairPosition(board: BoardState, id: String) = board.combat?.takeIf { area -> area.pairs.any { it.id == id } }?.let {
            CombatPairPosition(it.id, gap(it.pairs.map { pair -> pair.id }, id))
        }
        val pairChanges = (oldPairs + newPairs).distinct().filter { (it in oldPairs) != (it in newPairs) }
            .map { CombatPairChange(it, pairPosition(before, it), pairPosition(next, it)) }
        val oldEffects = before.stackEffects.associateBy { it.id }; val newEffects = next.stackEffects.associateBy { it.id }
        val effectChanges = (oldEffects.keys + newEffects.keys).filter { oldEffects[it] != newEffects[it] }
            .map { StackEffectChange(it, oldEffects[it], newEffects[it]) }
        val oldOrder = before.containers.singleOrNull { it.zone == PlayZone.STACK }?.cards.orEmpty()
        val newOrder = next.containers.singleOrNull { it.zone == PlayZone.STACK }?.cards.orEmpty()
        val reordered = ((action as? PlayAction.Stack)?.action as? StackAction.ReorderEffect)?.occurrenceId
        val orderChanges = (oldOrder + newOrder).distinct().filter { (it in oldOrder) != (it in newOrder) || it == reordered }
            .map { StackOrderChange(it, if (it in oldOrder) gap(oldOrder, it) else null, if (it in newOrder) gap(newOrder, it) else null) }
            .filter { it.from != it.to }
        val links = before.projectionLinks.filter { it !in next.projectionLinks }
        val addedLinks = next.projectionLinks.filter { it !in before.projectionLinks }
        val marks = (before.stackTextMarks.keys + next.stackTextMarks.keys).sorted().mapNotNull { id ->
            val from = before.stackTextMarks[id]; val to = next.stackTextMarks[id]
            if (from == to) null else StackTextMarkChange(id, from, to)
        }
        val area = if (before.combat?.id == next.combat?.id) null else CombatAreaChange(before.combat?.id, next.combat?.id)
        val guard = if ((action as? PlayAction.Stack)?.action.let { it is StackAction.AddEffect || it is StackAction.CopyEffect })
            StackGuardChange(stackDigest(state), stackDigest(state.id, next)) else null
        return if (area == null && occurrenceChanges.isEmpty() && pairChanges.isEmpty() && effectChanges.isEmpty() &&
            orderChanges.isEmpty() && links.isEmpty() && addedLinks.isEmpty() && marks.isEmpty() && guard == null) null else
            CombatStackTransition(area, occurrenceChanges, pairChanges, effectChanges, orderChanges, links.sortedBy { it.id }, guard,
                addedLinks.sortedBy { it.id }, marks)
    }
}
