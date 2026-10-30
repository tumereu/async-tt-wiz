package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

data class TransferFeedback(val serial: Long, val text: String)

/** Another player's game deck title stays "Deck" unless that player shows it ([PlayerPreparation.deckShown]).
 * A shown pod deck uses its private name, which the server sends only while the deck is shown. */
internal fun gameDeckName(owner: String, viewer: String?, name: String, shown: Boolean = false, shownPodName: String? = null): String =
    if (owner == viewer) name else if (shown) shownPodName ?: name else "Deck"

/** The cover art identifies a deck as much as its title: another player's stays a neutral placeholder unless shown. */
internal fun gameDeckCover(owner: String, viewer: String?, cover: String?, shown: Boolean = false): String? =
    cover?.takeIf { owner == viewer || shown }

/** Compare displayed membership, not submitted quantity or receipt counts. Includes reconciliation. */
internal fun preparationFeedback(before: LobbyPreparation?, after: LobbyPreparation?, actor: String?,
    previous: TransferFeedback?): TransferFeedback? {
    val old = before?.players?.find { it.accountId == actor }?.deck ?: return previous
    val next = after?.players?.find { it.accountId == actor }?.deck ?: return previous
    if (before.gameId != after.gameId || before.roundId != after.roundId || old.id != next.id) return null
    val changes = (old.entries + next.entries).map { it.definition }.distinct().mapNotNull { digest ->
        // Supplied basics are counts set with steppers, not moved copies.
        fun side(deck: PreparedDeck) = deck.entries.filter { it.definition == digest && it.section == SavedDeckSection.SIDEBOARD &&
            it.suppliedBasic == null }.sumOf { it.quantity.toLong() }
        val delta = side(next) - side(old)
        if (delta == 0L) null else {
            val name = after.definitions.find { it.digest == digest }?.definition?.name ?: "Card"
            "${kotlin.math.abs(delta)}× $name → ${if (delta > 0) "Sideboard" else "Mainboard"}"
        }
    }
    return if (changes.isEmpty()) previous else TransferFeedback((previous?.serial ?: 0) + 1, changes.joinToString("; "))
}

/** One undoable preparation tap: the exact copies a transfer moved, or a section's basics before a stepper tap. */
internal sealed interface PreparationStep {
    data class Moved(val transfer: PreparedTransfer) : PreparationStep
    data class Basics(val section: SavedDeckSection, val previous: PodDeckBasics) : PreparationStep
}

/** Personal undo for one game's preparation round and deck. Undo sends the inverse as an ordinary request. */
internal data class PreparationUndo(val gameId: String = "", val roundId: String = "", val deckId: String = "",
    val steps: List<PreparationStep> = emptyList()) {
    private fun matches(gameId: String, roundId: String, deckId: String) =
        gameId == this.gameId && roundId == this.roundId && deckId == this.deckId
    /** The latest step that still belongs to the shown preparation, or null once its game, round or deck changed. */
    fun last(preparation: LobbyPreparation?, actor: String?): PreparationStep? {
        val deck = preparation?.players?.find { it.accountId == actor }?.deck ?: return null
        return steps.lastOrNull()?.takeIf { matches(preparation.gameId, preparation.roundId, deck.id) }
    }
    fun push(gameId: String, roundId: String, deckId: String, step: PreparationStep): PreparationUndo =
        (if (matches(gameId, roundId, deckId)) this else PreparationUndo(gameId, roundId, deckId))
            .let { it.copy(steps = (it.steps + step).takeLast(LIMIT)) }
    fun pop() = copy(steps = steps.dropLast(1))
    companion object { const val LIMIT = 50 }
}

/** The same copies back to where they came from. */
internal fun PreparedTransfer.inverse(): PreparedTransfer {
    val target = if (source == SavedDeckSection.MAINBOARD) SavedDeckSection.SIDEBOARD else SavedDeckSection.MAINBOARD
    return copy(source = target, entries = entries.map { it.copy(section = target) })
}

/** A tile swipe moves one copy once it travels [threshold] in either direction; shorter swipes spring back. */
internal fun preparationSwipeCommits(offset: Float, threshold: Float): Boolean = threshold > 0f && kotlin.math.abs(offset) >= threshold
internal fun preparationSwipeProgress(offset: Float, threshold: Float): Float =
    if (threshold <= 0f) 0f else (kotlin.math.abs(offset) / threshold).coerceIn(0f, 1f)

/** Type groups of the preparation view, in display order; a card joins the first one its front type line names. */
internal enum class PreparationTypeGroup(val label: String, val type: String?) {
    CREATURES("Creatures", "Creature"), PLANESWALKERS("Planeswalkers", "Planeswalker"), BATTLES("Battles", "Battle"),
    INSTANTS("Instants", "Instant"), SORCERIES("Sorceries", "Sorcery"), ARTIFACTS("Artifacts", "Artifact"),
    ENCHANTMENTS("Enchantments", "Enchantment"), LANDS("Lands", "Land"), OTHER("Other", null)
}

/** Card types only: subtypes after a dash and later faces never decide the group. */
internal fun preparationTypeGroup(typeLine: String): PreparationTypeGroup {
    val types = typeLine.substringBefore("//").substringBefore('—').substringBefore(" - ")
        .split(' ', '\t').filter { it.isNotBlank() }.map { it.lowercase() }.toSet()
    return PreparationTypeGroup.entries.firstOrNull { group -> group.type?.lowercase()?.let { it in types } == true }
        ?: PreparationTypeGroup.OTHER
}

/** One tile: every copy of one card in one section. */
internal data class PreparationTile(val digest: String, val definition: CardDefinition?, val quantity: Long) {
    val name get() = definition?.name ?: "Card"
}
internal data class PreparationGroup(val type: PreparationTypeGroup, val tiles: List<PreparationTile>) {
    val count get() = tiles.sumOf { it.quantity }
}

/** A section's moved cards (not stepper-supplied basics) by type group; empty groups are left out, tiles by name. */
internal fun preparationGroups(deck: PreparedDeck, section: SavedDeckSection,
    definitions: Map<String, CardDefinition>): List<PreparationGroup> = preparationGroups(
    deck.entries.filter { it.section == section && it.suppliedBasic == null }.map { it.definition to it.quantity.toLong() }, definitions)

/** A saved deck's section in the same type groups as game preparation; every entry, basics included, is a tile. */
internal fun preparationGroups(deck: SavedDeck, section: SavedDeckSection,
    definitions: Map<String, CardDefinition>): List<PreparationGroup> = preparationGroups(
    deck.entries.filter { it.section == section }.map { it.definition to it.quantity.toLong() }, definitions)

private fun preparationGroups(cards: List<Pair<String, Long>>, definitions: Map<String, CardDefinition>): List<PreparationGroup> {
    val tiles = cards.groupBy({ it.first }, { it.second })
        .map { (digest, quantities) -> PreparationTile(digest, definitions[digest], quantities.sum()) }
        .filter { it.quantity > 0 }
    val byGroup = tiles.groupBy { preparationTypeGroup(it.definition?.faces?.firstOrNull()?.typeLine.orEmpty()) }
    return PreparationTypeGroup.entries.mapNotNull { group ->
        byGroup[group]?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, PreparationTile::name).thenBy { it.digest })
            ?.let { PreparationGroup(group, it) }
    }
}
