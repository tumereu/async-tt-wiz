package org.asyncttwiz.protocol

/** MTGJSON parts are not necessarily physical sides. Run only when building new definitions. */
object PhysicalCardFaces {
    fun normalize(definition: CardDefinition): CardDefinition {
        if (definition.faces.size < 2 || definition.layout in setOf("transform", "modal_dfc", "meld", "reversible_card") ||
            definition.faces.any { "Battle" in it.typeLine }) return definition
        // Leave hand-authored/non-catalog multi-face definitions alone.
        if (definition.provider != "mtgjson" && definition.layout !in setOf("adventure", "split", "flip", "aftermath")) return definition
        val first = definition.faces.first()
        val text = definition.faces.joinToString("\n\n────────\n\n") { face ->
            listOf(face.name, face.manaCost, face.typeLine, face.rulesText,
                if (face.power.isNotEmpty() || face.toughness.isNotEmpty()) "${face.power}/${face.toughness}" else "",
                face.loyalty.takeIf { it.isNotEmpty() }?.let { "Loyalty: $it" }.orEmpty(),
                face.defense.takeIf { it.isNotEmpty() }?.let { "Defense: $it" }.orEmpty())
                .filter { it.isNotEmpty() }.joinToString("\n")
        }
        return definition.copy(faces = listOf(first.copy(name = definition.name, otherFaceIds = emptyList(),
            manaCost = "", rulesText = text, power = "", toughness = "", loyalty = "", defense = "")))
    }
}
