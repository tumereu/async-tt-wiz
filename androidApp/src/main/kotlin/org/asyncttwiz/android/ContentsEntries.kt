package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

data class ContentsEntry(val cardId: String, val name: String, val category: String, val location: String,
    val bottommost: Boolean = false, val faceVisible: Boolean = true)

/** Live personal browsing; unlike ContentsDisplay this is not a shared disclosure or fixed tray. */
data class PileContents(val containerId: String, val title: String, val cardIds: List<String>, val ordered: Boolean = false)

private fun contentsCategory(type: String) = listOf("Creature", "Land", "Artifact", "Enchantment", "Planeswalker", "Instant", "Sorcery", "Battle")
    .firstOrNull { type.contains(it, ignoreCase = true) } ?: "Other"

private val contentsOrder = compareBy<ContentsEntry>({ it.category }, { it.name.lowercase() }, { it.cardId })

fun contentsEntries(state: PlayState, pile: PileContents, viewer: String, search: String = ""): List<ContentsEntry> {
    val entries = pile.cardIds.mapIndexed { index, id ->
        val card = CombatStackRules.inspection(state.board, id)
        val visible = card != null && CardVisibility.isVisibleFaceUp(state.board, card, viewer)
        val face = if (visible) state.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces
            ?.singleOrNull { it.faceId == card.faceId } else null
        val effect = state.board.stackEffects.singleOrNull { it.id == id }
        val location = card?.let { c -> state.board.containers.singleOrNull { it.id == c.containerId }?.zone?.name?.lowercase() } ?: "Unavailable"
        ContentsEntry(id, if (visible) face?.name ?: "Unavailable card" else "Card back",
            if (visible) contentsCategory(face?.typeLine.orEmpty()) else "Cards",
            (if (pile.ordered) "${index + 1} · " else "") + location +
                when { effect?.frozen != null -> " · Retained effect"; effect != null -> " · Effect"; else -> "" },
            faceVisible = visible)
    }.filter { !it.faceVisible || it.name.contains(search, ignoreCase = true) }
    return if (pile.ordered) entries else entries.sortedWith(contentsOrder)
}

/** Permission-aware presentation: backs contain no definition-derived names, types or search matches. */
fun contentsEntries(state: PlayState, display: ContentsDisplay, viewer: String, search: String = ""): List<ContentsEntry> {
    val entries = display.cardIds.mapIndexed { index, id ->
        val visible = display.faceVisibleTo(id, viewer)
        val card = state.board.cards.singleOrNull { it.id == id }
        val face = if (visible) state.definitions.singleOrNull { it.digest == card?.definition }?.definition?.faces
            ?.singleOrNull { it.faceId == card?.faceId } else null
        val type = face?.typeLine.orEmpty()
        val category = if (!visible) "Cards" else contentsCategory(type)
        ContentsEntry(id, if (visible) face?.name ?: "Unavailable card" else "Card back", category,
            card?.let { c -> state.board.containers.singleOrNull { it.id == c.containerId }?.zone?.name?.lowercase() } ?: "Unavailable",
            display.selection == ContentsSelection.BOTTOM && index == display.cardIds.lastIndex, faceVisible = visible)
    }
    // Covered rows must not be removed/reordered by a hidden name query.
    val filtered = entries.filter { !it.faceVisible || it.name.contains(search, ignoreCase = true) }
    return if (display.search && display.author == viewer) filtered.sortedWith(contentsOrder) else filtered
}
