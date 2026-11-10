package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** One card of a cube's [section]: [SavedDeckSection.MAINBOARD] (drafted) or [SavedDeckSection.MAYBEBOARD] (never drafted),
 * by printing as a deck's ([SavedDeckEntry]). Main is omitted on the wire. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class SavedCubeEntry(val printingId: String?, val name: String, val quantity: Int,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val section: SavedDeckSection = SavedDeckSection.MAINBOARD) {
    /** The card's key in its list ([SavedCards.key]). */
    val card: String get() = SavedCards.key(printingId, name)
}
/** A new cube of [entries] (as [CardListEntry]: their card data comes from the server's catalog). */
@Serializable data class CreateCube(val requestId: String, val name: String, val entries: List<CardListEntry>, val cubeCobraId: String? = null)
@Serializable data class UpdateCube(val requestId: String, val name: String? = null,
    val entries: List<CardListEntry>? = null, val cubeCobraId: String? = null)
@Serializable data class DeleteCube(val requestId: String)
@Serializable data class CubeDeleted(val id: String)
/** [totalCopies] counts Main, the copies a draft uses; [maybeCopies] counts Maybe and is omitted when none. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class CubeSummary(val id: String, val name: String, val revision: String,
    val createdAt: String, val updatedAt: String, val totalCopies: Long, val cubeCobraId: String? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val maybeCopies: Long = 0,
    /** Which cards Automatum can't play yet (I-D51, LOB-124, DRF-161), by the server's card catalog's marks on every
     * read; null when it has no catalog. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val automatum: CardListSupport? = null)
@Serializable data class SavedCube(val cube: CubeSummary, val entries: List<SavedCubeEntry>)
@Serializable data class CubeList(val cubes: List<CubeSummary>, val next: String? = null)

object CubeValidation {
    const val MAX_CARDS = 3000
    fun name(value: String) = value.isNotBlank() && DeckValidation.text(value)
    fun provenance(value: String?) = value == null || Regex("[A-Za-z0-9_-]{1,256}").matches(value)
    /** A save's cards: Main and Maybe only, each a printing or a name ([DeckValidation.card]); the limit counts every
     * section (DRF-007). */
    fun input(value: List<CardListEntry>) = value.isNotEmpty() &&
        value.all { DeckValidation.card(it) && it.quantity in 1..MAX_CARDS &&
            it.section in SavedDeckSection.CUBE } &&
        value.sumOf { it.quantity.toLong() } in 1..MAX_CARDS.toLong()
    /** The limit counts every section (DRF-007); a card may be in both Main and Maybe. */
    fun entries(value: List<SavedCubeEntry>) = value.isNotEmpty() &&
        value.all { (it.printingId == null || DeckValidation.uuid(it.printingId)) && DeckValidation.name(it.name) &&
            it.quantity in 1..MAX_CARDS && it.section in SavedDeckSection.CUBE } &&
        value.sumOf { it.quantity.toLong() } in 1..MAX_CARDS.toLong() &&
        value.map { it.section to it.card }.distinct().size == value.size
}
