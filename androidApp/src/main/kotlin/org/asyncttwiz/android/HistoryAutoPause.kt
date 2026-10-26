package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

enum class PlaybackAutoPause(val label: String, val defaultEnabled: Boolean = true) {
    STACK_ADDED("Added to Stack"),
    STACK_REMOVED("Removed from Stack", false),
    REVEAL("Reveal"),
    PINGS_COMMENTS("Pings / comments"),
    RANDOM("Die / coin results"),
    YOURS("Your life / cards hit"),
    ATTENTION("Move passes to you"),
    ATTACKERS("Attackers declared", false),
    LAND_FROM_HAND("Opponent plays a land", false),
    NONLAND_FROM_HAND("Opponent puts a nonland card onto the battlefield from hand", false);

    companion object {
        val defaults = entries.filter { it.defaultEnabled }.toSet()
    }
}

/** Reads only the advancing delta and a nearby materialized board, never ancestors or a replay.
 * Ownership/location changes within the group are folded into this small, touched-card cache. */
internal class HistoryAutoPause(private val viewer: String, private val board: BoardState?,
    private val containers: List<PlayContainer> = board?.containers.orEmpty(),
    private val definitions: List<SubmittedDefinition> = emptyList()) {
    private data class Card(val owner: String, val container: String, val definition: String = "", val face: String = "")
    private fun PlayCard.context() = Card(owner, containerId, definition, faceId)
    private val cards = mutableMapOf<String, Card?>()
    private val effects = mutableMapOf<String, StackEffect?>()
    private var turnPlayer = board?.turn?.accountId
    private fun card(id: String): Card? {
        if (id !in cards) cards[id] = board?.cards?.firstOrNull { it.id == id }?.context()
        return cards[id]
    }
    private fun effect(id: String): StackEffect? = if (id in effects) effects[id] else
        board?.stackEffects?.firstOrNull { it.id == id }
    private fun yours(id: String): Boolean {
        val effect = effect(id)
        if (effect?.controller == viewer) return true
        val combatSource = board?.combat?.pairs?.firstNotNullOfOrNull { pair ->
            (pair.attackers.firstOrNull { it.id == id } ?: pair.blockers.firstOrNull { it.id == id })?.sourceId
        }
        val card = card(effect?.sourceId ?: combatSource ?: id) ?: effect?.frozen?.let { Card(it.owner, it.containerId) } ?: return false
        return card.owner == viewer || containers.any { it.id == card.container && it.accountId == viewer }
    }

    fun triggers(node: GameplayMutation): Set<PlaybackAutoPause> {
        val t = node.transition
        val action = node.action
        val result = mutableSetOf<PlaybackAutoPause>()
        if (node.author != viewer) {
            val movement = t.movement
            fun zone(id: String) = containers.firstOrNull { it.id == id }?.zone
            val stack = t.combatStack
            if (stack?.let { s -> s.effects.any { it.from == null && it.to != null } ||
                    s.order.any { it.from == null && it.to != null } } == true ||
                movement?.let { zone(it.from.containerId) != PlayZone.STACK && zone(it.to.containerId) == PlayZone.STACK } == true)
                result += PlaybackAutoPause.STACK_ADDED
            if (stack?.let { s -> s.effects.any { it.from != null && it.to == null } ||
                    s.order.any { it.from != null && it.to == null } } == true ||
                movement?.let { zone(it.from.containerId) == PlayZone.STACK && zone(it.to.containerId) != PlayZone.STACK } == true)
                result += PlaybackAutoPause.STACK_REMOVED
            if (movement != null && zone(movement.from.containerId) == PlayZone.HAND && zone(movement.to.containerId) == PlayZone.BATTLEFIELD) {
                val card = card(movement.cardId)
                val faceId = t.disclosures.firstOrNull { it.cardId == movement.cardId }?.faceId ?:
                    t.fields.lastOrNull { it.targetId == movement.cardId && it.field == GameplayField.FACE }?.to ?: card?.face
                val face = definitions.firstOrNull { it.digest == card?.definition }?.definition?.faces?.firstOrNull { it.faceId == faceId }
                if (face != null) result += if ("Land" in face.typeLine.substringBefore('—').split(' '))
                    PlaybackAutoPause.LAND_FROM_HAND else PlaybackAutoPause.NONLAND_FROM_HAND
            }
            val explicitDisclosure = action is PlayAction.ShowContents || action is PlayAction.RevealContentsCard || action is PlayAction.InspectFace
            if (explicitDisclosure && t.disclosures.any { viewer in it.viewers } || t.contents?.let { change ->
                    change.to?.let { display -> change.from == null && display.cardIds.any { display.faceVisibleTo(it, viewer) } } == true
                } == true) result += PlaybackAutoPause.REVEAL
            if (action is PlayAction.Ping || action is PlayAction.EventComment) result += PlaybackAutoPause.PINGS_COMMENTS
            if (action is PlayAction.RollDie || action is PlayAction.FlipCoin) result += PlaybackAutoPause.RANDOM
            if (t.attention?.let { it.to == viewer && (it.from ?: turnPlayer) != viewer } == true)
                result += PlaybackAutoPause.ATTENTION
            if (action is PlayAction.Combat && (action.action is CombatAction.Declare ||
                    (action.action as? CombatAction.Place)?.destination?.role == CombatRole.ATTACKER)) result += PlaybackAutoPause.ATTACKERS

            val targets = t.fields.map { it.targetId } + listOfNotNull(movement?.cardId) +
                t.tokens.mapNotNull { it.from?.card?.id } + t.combatStack?.effects.orEmpty().map { it.id } +
                t.combatStack?.occurrences.orEmpty().mapNotNull { (it.from ?: it.to)?.occurrence?.sourceId } +
                t.links.flatMap { listOfNotNull(it.from?.first?.id, it.from?.second?.id, it.to?.first?.id, it.to?.second?.id) } +
                t.combatStack?.let { change -> (change.addedLinks + change.removedLinks).flatMap { listOf(it.first, it.second) } }.orEmpty() +
                // Panel resolutions and library reorders move cards without a single movement record.
                t.contents?.let { it.from?.cardIds.orEmpty() + it.to?.cardIds.orEmpty() }.orEmpty() +
                t.libraryOrder?.let { it.from + it.to }.orEmpty() +
                when (action) {
                    is PlayAction.CloneToken -> listOf(action.cardId)
                    is PlayAction.LinkProjections -> listOf(action.first.occurrenceId, action.second.occurrenceId)
                    is PlayAction.Stack -> when (val stack = action.action) {
                        is StackAction.AddEffect -> listOf(stack.sourceId)
                        is StackAction.CopyEffect -> listOf(stack.entryId)
                        is StackAction.MarkText -> listOf(stack.entryId)
                        is StackAction.ToggleTextMark -> listOf(stack.entryId)
                        else -> emptyList()
                    }
                    else -> emptyList()
                }
            if (targets.any { yours(it) } || t.fields.any {
                    it.targetId == viewer && it.field in setOf(GameplayField.LIFE, GameplayField.COUNTER, GameplayField.PLAYER_COUNTER) ||
                    it.field == GameplayField.OWNER && it.to == viewer
                }) result += PlaybackAutoPause.YOURS
        }
        // Even own actions can change the context for a later opponent action in the same delta.
        for (token in t.tokens) {
            val next = token.to?.card
            cards[(next ?: token.from?.card)!!.id] = next?.context()
        }
        for (field in t.fields) {
            if (field.field == GameplayField.TURN_PLAYER) turnPlayer = field.to
            val old = card(field.targetId) ?: continue
            when (field.field) {
                GameplayField.OWNER -> field.to?.let { cards[field.targetId] = old.copy(owner = it) }
                GameplayField.CONTAINER -> field.to?.let { cards[field.targetId] = old.copy(container = it) }
                GameplayField.FACE -> field.to?.let { cards[field.targetId] = old.copy(face = it) }
                else -> Unit
            }
        }
        t.movement?.let { move -> card(move.cardId)?.let { cards[move.cardId] = it.copy(container = move.to.containerId) } }
        t.combatStack?.effects?.forEach { effects[it.id] = it.to }
        return result
    }
}

/** O(newly traversed group), including when starting in the middle of an open group. */
internal fun HistoryTree.autoPauseBetween(from: String, to: String, viewer: String,
    enabled: Set<PlaybackAutoPause>, board: BoardState?, definitions: List<SubmittedDefinition> = emptyList()): Boolean {
    if (enabled.isEmpty() || from == to) return false
    val delta = mutableListOf<GameplayMutation>()
    var cursor = to
    while (cursor != from) {
        val node = nodes.getValue(cursor)
        delta += node
        cursor = node.parentId
    }
    val classifier = HistoryAutoPause(viewer, board, containers.ifEmpty { board?.containers.orEmpty() }, definitions)
    return delta.asReversed().any { classifier.triggers(it).any(enabled::contains) }
}
