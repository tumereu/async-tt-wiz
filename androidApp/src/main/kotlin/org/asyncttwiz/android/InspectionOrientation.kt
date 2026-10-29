package org.asyncttwiz.android

import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.DefinitionFace

/**
 * How an inspection view turns a printed face's ARTWORK (personal display only; text stays upright).
 * [degrees] is the clockwise turn shown at rest; [alternate] is the clockwise turn the rotate button
 * switches to, or null when the face has no rotate button.
 *
 * Card images come in the physical upright orientation. Split, Room, Plane/Phenomenon and Battle faces
 * are printed with their text running bottom to top, so a quarter turn clockwise reads them. An
 * aftermath half runs top to bottom (a quarter turn counter-clockwise); a flip card's other half is
 * upside down.
 */
data class InspectionOrientation(val degrees: Int, val alternate: Int? = null) {
    val sideways get() = degrees % 180 != 0
    fun shown(turned: Boolean) = if (turned && alternate != null) alternate else degrees
    companion object {
        val UPRIGHT = InspectionOrientation(0)

        fun of(definition: CardDefinition, face: DefinitionFace): InspectionOrientation = when {
            definition.layout in sidewaysLayouts || typeWord(face.typeLine, "Battle") ||
                typeWord(face.typeLine, "Plane") || typeWord(face.typeLine, "Phenomenon") -> InspectionOrientation(90)
            definition.layout == "aftermath" -> InspectionOrientation(0, 270)
            definition.layout == "flip" -> InspectionOrientation(0, 180)
            else -> UPRIGHT
        }

        // MTGJSON calls Rooms "split"; "room" covers definitions from other sources. Battles go by
        // type line, so a transforming battle's back face stays upright.
        private val sidewaysLayouts = setOf("split", "room", "planar")

        /** A whole card type before the dash, so "Planeswalker" or "Battlemage" never match. */
        private fun typeWord(typeLine: String, type: String) = typeLine.substringBefore('—').substringBefore(" - ")
            .split(Regex("\\s+")).any { it.equals(type, ignoreCase = true) }
    }
}

fun InspectedCard.orientation(face: Int): InspectionOrientation =
    definition.faces.getOrNull(face)?.let { InspectionOrientation.of(definition, it) } ?: InspectionOrientation.UPRIGHT
