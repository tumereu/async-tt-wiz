package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Null means the account has never customized its favorites; empty means explicitly none. */
@Serializable data class TokenFavorites(val keys: List<String>? = null)

object TokenIdentity {
    val defaultNames = setOf("Treasure", "Food", "Clue", "Blood", "Map", "Gold")

    private fun metadata(face: DefinitionFace, field: String): List<String> = runCatching {
        (face.sourceData?.let { DeckJson.format.parseToJsonElement(it).jsonObject[field] } as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.sorted().orEmpty()
    }.getOrDefault(emptyList())

    /** Printing, artwork, provider generation and face IDs deliberately do not participate. */
    fun key(definition: CardDefinition): String = "token-v1:" + DeckJson.intent("token-functional", DeckJson.canonical(
        listOf(listOf(definition.name)) + definition.faces.map { face ->
            val pt = if (face.power.isNotEmpty() || face.toughness.isNotEmpty()) "${face.power}/${face.toughness}" else face.corner
            listOf(face.name, pt, face.typeLine, face.rulesText,
                DeckJson.canonical(metadata(face, "colors")), DeckJson.canonical(metadata(face, "keywords")))
        }))

    fun favorite(definition: CardDefinition, favorites: TokenFavorites): Boolean =
        favorites.keys?.contains(key(definition)) ?: (definition.name in defaultNames)

    fun validKey(key: String) = Regex("token-v1:[0-9a-f]{64}").matches(key)

    fun attributes(face: DefinitionFace): String = metadata(face, "keywords").joinToString(", ")
        .ifEmpty { face.rulesText.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() }
        .ifEmpty { face.typeLine }.take(100)
}
