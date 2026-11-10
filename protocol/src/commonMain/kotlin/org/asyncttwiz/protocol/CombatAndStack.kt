@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package org.asyncttwiz.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable enum class CombatRole { ATTACKER, BLOCKER }
@Serializable data class CombatOccurrence(val id: String, val sourceId: String,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val note: String = "",
    /** CMB-031 (I-D32): a blocker's further blocks, the ids of the other pairs whose attackers it also blocks (CR
     * 509.1a), in the order they were added; its own pair comes first. Always empty on an attacker. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val alsoBlocks: List<String> = emptyList())
@Serializable data class CombatPair(val id: String, val attackers: List<CombatOccurrence> = emptyList(),
    val blockers: List<CombatOccurrence> = emptyList(),
    /** Absent/null targets the defending player, including all retained pre-v18 pairs. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val attackTarget: String? = null,
    /** Rules v20: remains true after the last blocker leaves this pair. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val blocked: Boolean = false) {
    fun side(role: CombatRole) = if (role == CombatRole.ATTACKER) attackers else blockers
    fun withSide(role: CombatRole, cards: List<CombatOccurrence>) =
        if (role == CombatRole.ATTACKER) copy(attackers = cards) else copy(blockers = cards)
}
@Serializable data class CombatArea(val id: String, val pairs: List<CombatPair> = emptyList())
/** Only disappearance retains card data. Live effects never cache fields or a creation-time snapshot. */
@Serializable data class StackEffect(val id: String, val sourceId: String, val frozen: PlayCard? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val controller: String? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val isCopy: Boolean = false,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val note: String = "",
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val revealsLibrarySource: Boolean = false,
    /** STK-005: a face-down cast's entry. It never shows its hand source to anyone but the source's holder. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val faceDown: Boolean = false,
    /** Rules v36: the structured Stack item (D19) when the engine made this entry; null for a manual entry (STK-016). */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val item: MtgStackItem? = null)
/** D-120 projection-to-projection links, not the source relationship and not selection. */
@Serializable data class ProjectionLink(val id: String, val first: String, val second: String)
@Serializable data class IdentityGap(val afterId: String? = null, val beforeId: String? = null)
@Serializable enum class CombatInsertion { APPEND, GAP }
/** A null pair names a new pair at pairGap. No empty shared pair is created ahead of placement. */
@Serializable data class CombatDestination(val areaId: String, val role: CombatRole,
    val pairId: String? = null, val pairGap: IdentityGap = IdentityGap(),
    val insertion: CombatInsertion = CombatInsertion.APPEND, val cardGap: IdentityGap = IdentityGap(),
    val pendingSourceIds: List<String> = emptyList(),
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val attackTarget: String? = null)

@Serializable sealed interface CombatAction {
    @Serializable @SerialName("open") data object Open : CombatAction
    @Serializable @SerialName("close") data class Close(val areaId: String?) : CombatAction
    @Serializable @SerialName("declare") data class Declare(val areaId: String, val sourceId: String) : CombatAction
    @Serializable @SerialName("place") data class Place(val sourceId: String, val destination: CombatDestination,
        val expectedOccurrence: CombatLocation? = null) : CombatAction
    @Serializable @SerialName("remove") data class Remove(val areaId: String, val occurrenceId: String) : CombatAction
    @Serializable @SerialName("setAttackTarget") data class SetAttackTarget(val areaId: String, val pairId: String,
        val cardId: String? = null) : CombatAction
    /**
     * CMB-031 (I-D32): the card [sourceId] blocks exactly the attackers of [pairIds] (CR 509.1a): its blocker copy sits in
     * the first pair (made there, or moved there from another pair, appended) and also blocks the others (arrows); an
     * empty list removes its blocker copy. [expected] is the list it blocks now (empty when it has no blocker copy):
     * another list refuses the event ("sourceChanged"). Refused for a card whose copy is an attacker.
     */
    @Serializable @SerialName("setBlocks") data class SetBlocks(val areaId: String, val sourceId: String,
        val pairIds: List<String>, val expected: List<String> = emptyList()) : CombatAction
}
@Serializable sealed interface StackAction {
    /** [faceDown] (STK-005) adds a face-down entry; only a hand card may be cast face down. */
    @Serializable @SerialName("addEffect") data class AddEffect(val sourceId: String, val expectedStackDigest: String,
        @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val faceDown: Boolean? = null) : StackAction
    @Serializable @SerialName("removeEffect") data class RemoveEffect(val occurrenceId: String) : StackAction
}

@Serializable data class CombatLocation(val areaId: String, val pairId: String, val role: CombatRole,
    val gap: IdentityGap)

@Serializable data class StackIdentity(val kind: String, val id: String, val sourceId: String)
@Serializable private data class StackProjection(val playId: String, val entries: List<StackIdentity>)

/** Rules v28: every printed battle is a Siege, which its caster controls and an opponent protects; the
 * caster attacks it. So a face-up Planeswalker is attackable on the defending player's battlefield and a
 * face-up Battle on the attacking (turn) player's own battlefield; a Battle on the defender's battlefield
 * is not (the attacker protects it). Types are read from the type part of the type line (before "—"), so
 * a subtype such as "Battlemage" is not a Battle. Events recorded under older rules keep the old check. */
object AttackTargets {
    const val RULES_VERSION = 28
    private val space = Regex("\\s+")
    /** The supertype and card type words of [typeLine], never its subtypes. */
    fun types(typeLine: String): List<String> =
        typeLine.substringBefore('—').substringBefore(" - ").split(space).filter { it.isNotEmpty() }
    fun hasType(typeLine: String, type: String) = types(typeLine).any { it.equals(type, ignoreCase = true) }
    /** Whose battlefield a face-up card with [typeLine] must be on to be attacked this combat, or null. */
    fun battlefieldOwner(board: BoardState, typeLine: String): String? = when {
        hasType(typeLine, "Planeswalker") -> CombatStackRules.defendingPlayer(board)
        hasType(typeLine, "Battle") -> CombatStackRules.attackingPlayer(board)
        else -> null
    }
}

object CombatStackRules {
    fun defendingPlayer(board: BoardState): String? = board.players.firstOrNull { it.accountId != board.turn.accountId }?.accountId
    /** The player whose combat it is (the turn player), derived like [defendingPlayer]. */
    fun attackingPlayer(board: BoardState): String? = board.players.firstOrNull { it.accountId == board.turn.accountId }?.accountId
    /** Whether the card [cardId] may be attacked this combat (a face-up planeswalker of the defending player, a battle
     * of the attacking player's), with [board]'s card data [definitions]. */
    fun canAttack(board: BoardState, definitions: List<SubmittedDefinition>, cardId: String,
        rulesVersion: Int = CURRENT_GAMEPLAY_RULES_VERSION): Boolean {
        val card = board.cards.singleOrNull { it.id == cardId } ?: return false
        val container = board.containers.singleOrNull { it.id == card.containerId } ?: return false
        val face = definitions.singleOrNull { it.digest == card.definition }?.definition?.faces
            ?.singleOrNull { it.faceId == card.faceId } ?: return false
        if (container.zone != PlayZone.BATTLEFIELD) return false
        if (rulesVersion < AttackTargets.RULES_VERSION) return container.accountId == defendingPlayer(board) &&
            (face.typeLine.contains("Planeswalker", ignoreCase = true) || face.typeLine.contains("Battle", ignoreCase = true))
        val owner = AttackTargets.battlefieldOwner(board, face.typeLine) ?: return false
        return !card.isFaceDown && container.accountId == owner
    }

    /**
     * The combat lanes [board] holds after [action] (the table's own staging of a declaration, CMB-028, CMB-034), with
     * the same checks and ids as the gameplay change gives ([requestId]: the action's request, naming new pairs and
     * occurrences; the wire's checks first, the lanes' invariants after); [board] itself when nothing changes.
     * [definitions]: the board's card data.
     * @throws IllegalArgumentException with the gameplay change's reason when refused.
     */
    fun stage(board: BoardState, definitions: List<SubmittedDefinition>, requestId: String, action: CombatAction,
        rulesVersion: Int = CURRENT_GAMEPLAY_RULES_VERSION): BoardState {
        require(valid(action)) { "invalidInput" }
        val next = lanes(board, definitions, requestId, action, rulesVersion)
        return if (next == board) board else closeInvariants(board, next, rulesVersion) ?: throw IllegalArgumentException("invalidStructure")
    }

    /** A face-down cast's entry (STK-005): shown as a card back to both players; only its holder may inspect it. */
    fun faceDownEntry(board: BoardState, id: String): Boolean = board.stackEffects.any { it.id == id && it.faceDown }
    fun occurrences(area: CombatArea?): List<CombatOccurrence> = area?.pairs.orEmpty().flatMap { it.blockers + it.attackers }
    /** CMB-031: the pairs whose attackers the blocker copy [occurrenceId] blocks, its own pair first; empty when it isn't
     * a blocker in [area]. */
    fun blockedPairs(area: CombatArea?, occurrenceId: String): List<String> {
        val own = area?.pairs?.firstOrNull { p -> p.blockers.any { it.id == occurrenceId } } ?: return emptyList()
        return listOf(own.id) + own.blockers.single { it.id == occurrenceId }.alsoBlocks
    }
    /** CMB-031: whether a blocker of another pair also blocks the attackers of [pairId]. */
    fun blockedFromElsewhere(area: CombatArea?, pairId: String): Boolean =
        area?.pairs.orEmpty().any { p -> p.id != pairId && p.blockers.any { pairId in it.alsoBlocks } }
    /** CMB-031: every block in [area] as (blocker copy, attacker copy): by the blockers in pair order, each with the
     * attackers of its own pair, then those of the pairs it also blocks. */
    fun blocks(area: CombatArea?): List<Pair<CombatOccurrence, CombatOccurrence>> = area?.pairs.orEmpty().flatMap { pair ->
        pair.blockers.flatMap { b ->
            blockedPairs(area, b.id).flatMap { id -> area!!.pairs.firstOrNull { it.id == id }?.attackers.orEmpty() }.map { b to it }
        }
    }
    fun projectionIds(board: BoardState): Set<String> =
        (occurrences(board.combat).map { it.id } + board.stackEffects.map { it.id }).toSet()
    fun projectionPair(first: String, second: String) = if (first <= second) first to second else second to first
    fun stack(board: BoardState) = board.containers.single { it.zone == PlayZone.STACK }
    fun canProject(board: BoardState, card: PlayCard, rulesVersion: Int = CURRENT_GAMEPLAY_RULES_VERSION): Boolean {
        val source = board.containers.singleOrNull { it.id == card.containerId } ?: return false
        return source.zone in setOf(PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD, PlayZone.EXILE) ||
            rulesVersion >= 21 && source.zone == PlayZone.LIBRARY ||
            source.zone == PlayZone.HAND && source.accountId == card.owner
    }
    /** Top first. The Stack holds only effect entries (STK-003): the engine's board puts nothing else there. */
    fun identities(board: BoardState): List<StackIdentity> = stack(board).cards.map { id ->
        StackIdentity("effect", id, board.stackEffects.single { it.id == id }.sourceId)
    }
    fun stackDigest(playId: String, board: BoardState): String = "sha256:" +
        DeckJson.intent("stack", DeckJson.canonical(StackProjection(playId, identities(board))))

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

    private fun valid(gap: IdentityGap) = listOfNotNull(gap.afterId, gap.beforeId).all(DeckValidation::uuid) &&
        (gap.afterId == null || gap.afterId != gap.beforeId)
    private fun valid(location: CombatLocation) = DeckValidation.uuid(location.areaId) &&
        DeckValidation.uuid(location.pairId) && valid(location.gap)
    fun valid(action: CombatAction): Boolean = when (action) {
        CombatAction.Open -> true
        is CombatAction.Close -> action.areaId == null || DeckValidation.uuid(action.areaId)
        is CombatAction.Declare -> DeckValidation.uuid(action.areaId) && DeckValidation.uuid(action.sourceId)
        is CombatAction.Remove -> DeckValidation.uuid(action.areaId) && DeckValidation.uuid(action.occurrenceId)
        is CombatAction.SetAttackTarget -> DeckValidation.uuid(action.areaId) && DeckValidation.uuid(action.pairId) &&
            (action.cardId == null || DeckValidation.uuid(action.cardId))
        is CombatAction.SetBlocks -> DeckValidation.uuid(action.areaId) && DeckValidation.uuid(action.sourceId) &&
            listOf(action.pairIds, action.expected).all { ids -> ids.distinct().size == ids.size && ids.all(DeckValidation::uuid) }
        is CombatAction.Place -> with(action.destination) {
            DeckValidation.uuid(action.sourceId) && DeckValidation.uuid(areaId) &&
                (pairId == null || DeckValidation.uuid(pairId)) && valid(pairGap) && valid(cardGap) &&
                pendingSourceIds.distinct().size == pendingSourceIds.size && pendingSourceIds.all(DeckValidation::uuid) &&
                (action.expectedOccurrence == null || valid(action.expectedOccurrence)) &&
                (attackTarget == null || DeckValidation.uuid(attackTarget))
        }
    }
    /** The next board of [action] on [board] ([requestId] naming what it makes), [board] itself for a no-op.
     * @throws IllegalArgumentException with the reason when refused. */
    private fun lanes(board: BoardState, definitions: List<SubmittedDefinition>, requestId: String, action: CombatAction,
        rulesVersion: Int): BoardState {
        fun reject(reason: String): Nothing = throw IllegalArgumentException(reason)
        val area = board.combat
        return when (action) {
            CombatAction.Open -> if (area != null) board else board.copy(
                combat = CombatArea(CommandIds.uuidV5(requestId, "combat-area/0")))
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
            is CombatAction.SetAttackTarget -> {
                if (area?.id != action.areaId || area.pairs.none { it.id == action.pairId }) return reject("missingReference")
                if (action.cardId != null && !canAttack(board, definitions, action.cardId, rulesVersion)) return reject("inapplicable")
                board.copy(combat = area.copy(pairs = area.pairs.map {
                    if (it.id == action.pairId) it.copy(attackTarget = action.cardId) else it
                }))
            }
            is CombatAction.SetBlocks -> {
                if (area?.id != action.areaId) return reject("missingReference")
                if (action.pairIds.any { id -> area.pairs.none { it.id == id } }) return reject("missingReference")
                val existing = occurrences(area).singleOrNull { it.sourceId == action.sourceId }
                if (existing != null && area.pairs.any { p -> p.attackers.any { it.id == existing.id } }) return reject("inapplicable")
                if ((existing?.let { blockedPairs(area, it.id) } ?: emptyList()) != action.expected) return reject("sourceChanged")
                if (action.pairIds.isEmpty()) {
                    if (existing == null) board else board.copy(combat = without(area, existing.id))
                } else {
                    val source = board.cards.singleOrNull { it.id == action.sourceId } ?: return reject("missingReference")
                    if (board.containers.singleOrNull { it.id == source.containerId }?.zone != PlayZone.BATTLEFIELD)
                        return reject("inapplicable")
                    val first = action.pairIds.first()
                    val occurrence = (existing ?: CombatOccurrence(CommandIds.uuidV5(requestId, "combat-occurrence/0"),
                        action.sourceId)).copy(alsoBlocks = action.pairIds.drop(1))
                    val stays = existing != null && area.pairs.single { it.id == first }.blockers.any { it.id == existing.id }
                    val base = if (existing == null || stays) area else without(area, existing.id, removeEmpty = false)
                    val pairs = base.pairs.map { pair ->
                        when {
                            pair.id != first -> pair
                            stays -> pair.copy(blockers = pair.blockers.map { if (it.id == occurrence.id) occurrence else it })
                            else -> pair.copy(blockers = pair.blockers + occurrence)
                        }
                    }
                    board.copy(combat = area.copy(pairs = pairs.filterNot(::empty)))
                }
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
                if (action is CombatAction.Declare && location?.role == CombatRole.ATTACKER) return board
                val occurrence = existing ?: CombatOccurrence(CommandIds.uuidV5(requestId, "combat-occurrence/0"), sourceId)
                val reduced = without(area, occurrence.id, removeEmpty = false)
                val destination = if (action is CombatAction.Place) action.destination else CombatDestination(areaId,
                    CombatRole.ATTACKER, pairGap = IdentityGap(reduced.pairs.lastOrNull()?.id))
                val pairs = reduced.pairs.toMutableList()
                val pairIndex: Int
                if (destination.pairId == null) {
                    if (destination.attackTarget != null && !canAttack(board, definitions, destination.attackTarget, rulesVersion)) return reject("inapplicable")
                    pairIndex = gapIndex(pairs.map { it.id }, destination.pairGap) ?: return reject("destinationChanged")
                    pairs.add(pairIndex, CombatPair(CommandIds.uuidV5(requestId, "combat-pair/0"), attackTarget = destination.attackTarget))
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

    /** The cards of [before] that are gone from [after]; the lists are hashed only when they don't line up by id. */
    private fun vanished(before: List<PlayCard>, after: List<PlayCard>): List<PlayCard> {
        if (before === after) return emptyList()
        if (before.size == after.size && before.indices.all { before[it] === after[it] || before[it].id == after[it].id })
            return emptyList()
        val remaining = after.mapTo(HashSet()) { it.id }
        return before.filter { old -> old.id !in remaining }
    }

    internal fun closeInvariants(before: BoardState, candidate: BoardState, rulesVersion: Int = 18): BoardState? {
        val vanished = vanished(before.cards, candidate.cards)
        if (vanished.any { !it.token }) return null
        val battlefield = candidate.containers.filter { it.zone == PlayZone.BATTLEFIELD }.map { it.id }.toSet()
        // Read only for combat, the Stack and entry ids: an event on a big board without them hashes no card here.
        val live by lazy { candidate.cards.associateBy { it.id } }
        val combat = candidate.combat?.let { area -> area.copy(pairs = area.pairs.map { pair ->
            fun keep(cards: List<CombatOccurrence>) = cards.filter { live[it.sourceId]?.containerId in battlefield }
            // CMB-031: an attacker blocks nothing; a blocker's further blocks name other pairs that still hold attackers
            val attacked = area.pairs.filter { p -> keep(p.attackers).isNotEmpty() }.map { it.id }.toSet()
            pair.copy(attackers = keep(pair.attackers).map { if (it.alsoBlocks.isEmpty()) it else it.copy(alsoBlocks = emptyList()) },
                blockers = keep(pair.blockers).map { b ->
                    val also = b.alsoBlocks.filter { it != pair.id && it in attacked }.distinct()
                    if (also == b.alsoBlocks) b else b.copy(alsoBlocks = also)
                },
                blocked = pair.blocked || rulesVersion >= 20 && (pair.blockers.isNotEmpty() || blockedFromElsewhere(area, pair.id) ||
                    before.combat?.takeIf { it.id == area.id }?.let { old ->
                        old.pairs.any { it.id == pair.id && it.blockers.isNotEmpty() } || blockedFromElsewhere(old, pair.id) } == true),
                // Attackers keep a target that left the battlefield: they attack nothing and deal no combat damage (CR
                // 506.4c, combat review #3; I-D52)
                attackTarget = pair.attackTarget)
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
        // (Their distinctness is part of all ids' below, checked once: either refuses the same board.)
        val stack = candidate.containers.singleOrNull { it.zone == PlayZone.STACK }
        if (effects.isNotEmpty() && stack == null) return null
        if (stack != null && (stack.cards.distinct().size != stack.cards.size ||
                effects.any { it.id !in stack.cards } || stack.cards.any { id ->
                    effects.none { it.id == id } && live[id]?.containerId != stack.id
                })) return null
        val links = candidate.projectionLinks.filter { it.first in ids && it.second in ids }
        if (links.any { it.first == it.second } || links.map { projectionPair(it.first, it.second) }.distinct().size != links.size)
            return null
        // Every id of the board distinct: cards, containers, entries, the combat and its pairs, players and links
        val seen = HashSet<String>((candidate.cards.size + candidate.containers.size + ids.size) * 2)
        if (!candidate.cards.all { seen.add(it.id) } || !candidate.containers.all { seen.add(it.id) } || !ids.all(seen::add) ||
            combat?.id?.let(seen::add) == false || !combat?.pairs.orEmpty().all { seen.add(it.id) } ||
            !candidate.players.all { seen.add(it.accountId) } || !candidate.links.all { seen.add(it.id) } || !links.all { seen.add(it.id) })
            return null
        return candidate.copy(combat = combat, stackEffects = effects, projectionLinks = links,
            stackTextMarks = candidate.stackTextMarks.filterKeys { it in stack?.cards.orEmpty() })
    }
}
