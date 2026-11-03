package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** One card of a cube's [section]: [SavedDeckSection.MAINBOARD] (drafted) or [SavedDeckSection.MAYBEBOARD] (never drafted).
 * Main is omitted on the wire, so Main-only cubes read and hash exactly as before Maybe existed. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class SavedCubeEntry(val definition: String, val quantity: Int,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val section: SavedDeckSection = SavedDeckSection.MAINBOARD)
@Serializable data class CreateCube(val requestId: String, val name: String, val entries: List<SavedCubeEntry>, val cubeCobraId: String? = null)
@Serializable data class UpdateCube(val requestId: String, val name: String? = null,
    val entries: List<SavedCubeEntry>? = null, val cubeCobraId: String? = null)
@Serializable data class DeleteCube(val requestId: String)
@Serializable data class CubeDeleted(val id: String)
/** [totalCopies] counts Main, the copies a draft uses; [maybeCopies] counts Maybe and is omitted when none. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class CubeSummary(val id: String, val name: String, val revision: String,
    val createdAt: String, val updatedAt: String, val totalCopies: Long, val cubeCobraId: String? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val maybeCopies: Long = 0,
    /** Which cards Automatum plays by hand (I-D51; null until a device worked it out for this revision). */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val automatum: CardListSupport? = null)
@Serializable data class SavedCube(val cube: CubeSummary, val entries: List<SavedCubeEntry>)
@Serializable data class CubeList(val cubes: List<CubeSummary>, val next: String? = null)
@Serializable data class ReadCubeDefinitions(val digests: List<String>)
@Serializable data class SubmittedDefinitions(val definitions: List<SubmittedDefinition>)

object CubeValidation {
    const val MAX_CARDS = 3000
    const val MAX_DEFINITIONS_PER_READ = 250
    fun definitionRead(digests: List<String>) = digests.size in 1..MAX_DEFINITIONS_PER_READ &&
        digests.all(DeckValidation::digest)
    fun name(value: String) = value.isNotBlank() && DeckValidation.text(value)
    fun provenance(value: String?) = value == null || Regex("[A-Za-z0-9_-]{1,256}").matches(value)
    /** The limit counts every section (DRF-007); a card may be in both Main and Maybe. */
    fun entries(value: List<SavedCubeEntry>) = value.isNotEmpty() &&
        value.all { DeckValidation.digest(it.definition) && it.quantity in 1..MAX_CARDS && it.section in SavedDeckSection.CUBE } &&
        value.sumOf { it.quantity.toLong() } in 1..MAX_CARDS.toLong() &&
        value.map { it.section to it.definition }.distinct().size == value.size
}
