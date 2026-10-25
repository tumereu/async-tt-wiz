package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

@Serializable enum class CornerPart { WHOLE, LEFT, RIGHT, BOTH }
@Serializable enum class CounterTarget { CARD, PLAYER }

/** Text first. Signed decimal integers use the existing life tool's checked 64-bit arithmetic. */
object ManualText {
    private val integer = Regex("[+-]?[0-9]+")
    fun number(text: String): Long? = text.trim().takeIf { integer.matches(it) }?.toLongOrNull()
    fun add(text: String, delta: Long): String? {
        val from = number(text) ?: return null
        val sum = from + delta
        return if ((from xor sum) and (delta xor sum) < 0) null else sum.toString()
    }
    fun corner(text: String, part: CornerPart, delta: Int): String? {
        if (delta != 1 && delta != -1) return null
        val value = text.trim()
        if (part == CornerPart.WHOLE) return add(value, delta.toLong())
        val pair = value.split('/')
        if (pair.size != 2 || pair.any { number(it) == null || it != it.trim() }) return null
        val left = if (part != CornerPart.RIGHT) add(pair[0], delta.toLong()) ?: return null else pair[0]
        val right = if (part != CornerPart.LEFT) add(pair[1], delta.toLong()) ?: return null else pair[1]
        return "$left/$right"
    }
    fun playerCounterText(value: String?): String = value.orEmpty()
    fun cardCounterText(value: String?): String = value?.takeIf { it.isNotEmpty() } ?: "–"
    fun playerRowVisible(counters: Map<CounterColor, String>): Boolean = counters.values.any {
        val text = it.trim()
        text.isNotEmpty() && !(integer.matches(text) && text.removePrefix("+").removePrefix("-").all { c -> c == '0' })
    }
}

/** A permission calculation, not evidence of a logged inspection or a cache-based disclosure grant. */
object ManualDisclosure {
    fun ownRegion(state: PlayState, card: PlayCard, viewer: String): Boolean =
        state.board.containers.singleOrNull { it.id == card.containerId }?.accountId == viewer

    fun permittedFaces(state: PlayState, card: PlayCard, viewer: String, inspecting: Boolean): List<DefinitionFace> {
        val faces = state.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces.orEmpty()
        return if (inspecting && ownRegion(state, card, viewer)) faces
        else if (CardVisibility.isVisibleFaceUp(state.board, card, viewer)) faces.filter { it.faceId == card.faceId } else emptyList()
    }
}

/** Called only through GameplayRules, after its scope/access/wire checks. No second authoritative path. */
object ManualEditRules {
    fun valid(action: PlayAction.Manual): Boolean = when (action) {
        is PlayAction.SetCorner -> DeckValidation.uuid(action.cardId) && DeckValidation.text(action.value.trim())
        is PlayAction.AdjustCorner -> DeckValidation.uuid(action.cardId) && action.delta in listOf(-1, 1)
        is PlayAction.ResetCorner -> DeckValidation.uuid(action.cardId)
        is PlayAction.SetCounter -> DeckValidation.uuid(action.targetId) && DeckValidation.text(action.value.trim())
        is PlayAction.AdjustCounter -> DeckValidation.uuid(action.targetId) && ManualText.number(action.delta) != null &&
            action.delta == action.delta.trim()
        is PlayAction.SetNote -> DeckValidation.uuid(action.cardId) && DeckValidation.text(action.value.trim())
        is PlayAction.SetOwner -> DeckValidation.uuid(action.cardId) && DeckValidation.uuid(action.accountId)
        is PlayAction.SetFace -> DeckValidation.uuid(action.cardId) && DeckValidation.uuid(action.faceId)
        is PlayAction.SetCovered -> DeckValidation.uuid(action.cardId)
        is PlayAction.InspectFace -> listOf(action.cardId, action.faceId, action.containerId).all(DeckValidation::uuid)
    }

    internal fun change(state: PlayState, actor: String, command: GameCommand, action: PlayAction.Manual): GameplayChange {
        val transition = transition(state, actor, action) ?: return GameplayChange(reason = "inapplicable")
        if (transition.fields.isEmpty() && transition.disclosures.isEmpty()) return GameplayChange(state)
        return GameplayRules.record(state, actor, command, apply(state.board, transition.fields), transition)
    }

    private fun transition(state: PlayState, actor: String, action: PlayAction.Manual): GameplayTransition? {
        val board = state.board
        fun field(id: String, type: GameplayField, from: String?, to: String?, color: CounterColor? = null,
            reads: List<GameplayRead> = emptyList(), disclosures: List<GameplayDisclosure> = emptyList()) =
            GameplayTransition(if (from == to) emptyList() else listOf(GameplayFieldChange(id, type, from, to, color)),
                reads = reads, disclosures = disclosures)
        if (action is PlayAction.SetCounter || action is PlayAction.AdjustCounter) {
            val target = if (action is PlayAction.SetCounter) action.target else (action as PlayAction.AdjustCounter).target
            val id = if (action is PlayAction.SetCounter) action.targetId else (action as PlayAction.AdjustCounter).targetId
            val color = if (action is PlayAction.SetCounter) action.color else (action as PlayAction.AdjustCounter).color
            val counters = when (target) {
                CounterTarget.CARD -> board.cards.singleOrNull { it.id == id }?.counters
                CounterTarget.PLAYER -> board.players.singleOrNull { it.accountId == id }?.counters
            } ?: return null
            val from = counters[color]
            val to = if (action is PlayAction.SetCounter) action.value.trim().ifEmpty { null }
                else ManualText.add(from ?: "0", (action as PlayAction.AdjustCounter).delta.toLong()) ?: return null
            return field(id, if (target == CounterTarget.CARD) GameplayField.COUNTER else GameplayField.PLAYER_COUNTER,
                from, to, color)
        }
        val id = when (action) {
            is PlayAction.SetCorner -> action.cardId
            is PlayAction.AdjustCorner -> action.cardId
            is PlayAction.ResetCorner -> action.cardId
            is PlayAction.SetNote -> action.cardId
            is PlayAction.SetOwner -> action.cardId
            is PlayAction.SetFace -> action.cardId
            is PlayAction.SetCovered -> action.cardId
            is PlayAction.InspectFace -> action.cardId
            else -> return null
        }
        val card = board.cards.singleOrNull { it.id == id } ?: return null
        val faces = state.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces.orEmpty()
        fun exposure(face: String, viewers: List<String>) = GameplayDisclosure(id, face, card.containerId, viewers.sorted())
        return when (action) {
            is PlayAction.SetCorner -> field(id, GameplayField.CORNER, card.corner, action.value.trim())
            is PlayAction.AdjustCorner -> field(id, GameplayField.CORNER, card.corner,
                ManualText.corner(card.corner, action.part, action.delta) ?: return null)
            is PlayAction.ResetCorner -> field(id, GameplayField.CORNER, card.corner,
                faces.singleOrNull { it.faceId == card.faceId }?.corner.orEmpty(),
                reads = listOf(GameplayRead(id, GameplayField.FACE, card.faceId)))
            is PlayAction.SetNote -> field(id, GameplayField.NOTE, card.note, action.value.trim())
            is PlayAction.SetOwner -> if (board.players.none { it.accountId == action.accountId }) null
                else field(id, GameplayField.OWNER, card.owner, action.accountId)
            is PlayAction.SetFace -> if (faces.none { it.faceId == action.faceId }) null
                else field(id, GameplayField.FACE, card.faceId, action.faceId,
                    reads = listOf(GameplayRead(id, GameplayField.COVERED, card.covered.toString()),
                        GameplayRead(id, GameplayField.EXPLICITLY_COVERED, card.explicitlyCovered.toString()),
                        GameplayRead(id, GameplayField.CONTAINER, card.containerId)),
                    disclosures = CardVisibility.viewers(board, card).takeIf { card.faceId != action.faceId && it.isNotEmpty() }
                        ?.let { listOf(exposure(action.faceId, it)) }.orEmpty())
            is PlayAction.SetCovered -> {
                val changed = card.copy(covered = action.covered, explicitlyCovered = action.covered)
                val viewers = CardVisibility.viewers(board, changed) - CardVisibility.viewers(board, card).toSet()
                GameplayTransition(fields = listOf(
                    GameplayFieldChange(id, GameplayField.COVERED, card.covered.toString(), changed.covered.toString()),
                    GameplayFieldChange(id, GameplayField.EXPLICITLY_COVERED, card.explicitlyCovered.toString(), changed.explicitlyCovered.toString())
                ).filter { it.from != it.to },
                    reads = if (viewers.isNotEmpty()) listOf(GameplayRead(id, GameplayField.FACE, card.faceId)) else emptyList(),
                    disclosures = if (viewers.isNotEmpty()) listOf(exposure(card.faceId, viewers)) else emptyList())
            }
            is PlayAction.InspectFace -> if (card.containerId != action.containerId || faces.none { it.faceId == action.faceId }) null
                else GameplayTransition(reads = listOf(GameplayRead(id, GameplayField.CONTAINER, card.containerId)),
                    disclosures = listOf(exposure(action.faceId, listOf(actor))))
            else -> null
        }
    }

    private fun apply(board: BoardState, fields: List<GameplayFieldChange>): BoardState {
        var next = board
        for (field in fields) {
            fun counters(values: Map<CounterColor, String>): Map<CounterColor, String> =
                if (field.to == null) values - field.color!! else values + (field.color!! to field.to)
            next = if (field.field == GameplayField.PLAYER_COUNTER) next.copy(players = next.players.map {
                if (it.accountId == field.targetId) it.copy(counters = counters(it.counters)) else it
            }) else next.copy(cards = next.cards.map {
                if (it.id != field.targetId) it else when (field.field) {
                    GameplayField.CORNER -> it.copy(corner = field.to!!)
                    GameplayField.COUNTER -> it.copy(counters = counters(it.counters))
                    GameplayField.NOTE -> it.copy(note = field.to!!)
                    GameplayField.OWNER -> it.copy(owner = field.to!!)
                    GameplayField.FACE -> it.copy(faceId = field.to!!)
                    GameplayField.COVERED -> it.copy(covered = field.to!!.toBooleanStrict())
                    GameplayField.EXPLICITLY_COVERED -> it.copy(explicitlyCovered = field.to!!.toBooleanStrict())
                    else -> error("Not a manual card field")
                }
            })
        }
        return next
    }
}
