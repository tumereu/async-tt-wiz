package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

@Serializable data class SavedCubeEntry(val definition: String, val quantity: Int)
@Serializable data class CreateCube(val requestId: String, val name: String, val entries: List<SavedCubeEntry>, val cubeCobraId: String? = null)
@Serializable data class UpdateCube(val requestId: String, val name: String? = null,
    val entries: List<SavedCubeEntry>? = null, val cubeCobraId: String? = null)
@Serializable data class DeleteCube(val requestId: String)
@Serializable data class CubeDeleted(val id: String)
@Serializable data class CubeSummary(val id: String, val name: String, val revision: String,
    val createdAt: String, val updatedAt: String, val totalCopies: Long, val cubeCobraId: String? = null)
@Serializable data class SavedCube(val cube: CubeSummary, val entries: List<SavedCubeEntry>)
@Serializable data class CubeList(val cubes: List<CubeSummary>, val next: String? = null)

object CubeValidation {
    const val MAX_CARDS = 3000
    fun name(value: String) = value.isNotBlank() && DeckValidation.text(value)
    fun provenance(value: String?) = value == null || Regex("[A-Za-z0-9_-]{1,256}").matches(value)
    fun entries(value: List<SavedCubeEntry>) = value.isNotEmpty() &&
        value.all { DeckValidation.digest(it.definition) && it.quantity in 1..MAX_CARDS } &&
        value.sumOf { it.quantity.toLong() } in 1..MAX_CARDS.toLong() &&
        value.map { it.definition }.distinct().size == value.size
}
