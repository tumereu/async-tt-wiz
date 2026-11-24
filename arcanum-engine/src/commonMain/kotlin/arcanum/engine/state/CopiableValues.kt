package arcanum.engine.state

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.FaceDefinition
import arcanum.api.Layout
import kotlinx.serialization.Serializable

/**
 * An object's copiable values as a whole (CR 707.2): its [layout] and all its [faces], with what copy effects, merging and
 * its face-down status made of them: both halves of a split card (CR 709.5b), a flip card's two versions (CR 707.3), the
 * alternative characteristics of an Adventure, an Omen or a prepare spell (CR 716.2b, 721.2b, 723.2b), both faces of a
 * double-faced object. [up] is the face up (of a double-faced object or a spell), or for a flip card the version its flipped
 * status picks. [gained]: abilities in none of its faces, so whichever door is locked or face is up it has them, once: what a
 * split card gained in copying (CR 707.9a, 709.5), and the other components' abilities of a merged permanent with its own card
 * on top (CR 702.140e).
 *
 * [faceParts]: a layout per face. Each face of a double-faced object has its own set of characteristics (CR 712.6), and a token
 * copy of a double-faced permanent that copies a multi-part object (a Room, a prepare creature) has that object's layout and
 * parts on each face (CR 707.8a): then [faceParts] holds each face's layout and parts, and [faces] each face's first part (what
 * reads the face as one, its name or types); empty: each face is its one entry of [faces]. [faceUp] is the face up alone.
 *
 * What rules a permanent works by comes from these values, read from the view ([arcanum.engine.view.ObjectView.copiable]): a
 * Room's doors, a prepare spell (of [faceUp]). Designations (unlocked, prepared: CR 709.5c, 723.3a) and status (CR 110.5) aren't
 * among them: they stay on the object ([GameObject.marks], [GameObject.flipped], from [ObjectStatus.Flipped] unless it is a flip
 * card, whose [GameObject.face] is its status). What a permanent physically is decides only what its cards
 * can do: whether it can transform (CR 712.7) or meld (CR 701.42c), read from [GameObject.definition].
 */
@Serializable
data class CopiableValues(
    val layout: Layout, val faces: List<FaceDefinition>, val up: Int = 0, val gained: List<Ability> = emptyList(),
    /** isd: for a token, the object whose ability created it, what its abilities' [arcanum.api.Objects.Creator] means; part of the
     * token's text, so a copy of the token refers to the same object (CR 111.3, 201.5a). */
    val createdBy: ObjectId? = null,
    /** Of a double-faced object whose faces are multi-part, each face's layout and parts (CR 707.8a, 712.6); empty: none is. */
    val faceParts: List<CardDefinition> = emptyList(),
) {
    /** These values as a definition, what a token holds (with [ObjectStatus.GainedInCopying] and [ObjectStatus.FaceParts]). */
    val definition: CardDefinition get() = CardDefinition(layout, faces)

    /** The values of the face up alone (CR 712.6c, d): of a double-faced object whose faces are multi-part ([faceParts]), that face's
     * layout and parts, its first part up (a flip card's version is its object's flipped status); else these values. */
    val faceUp: CopiableValues get() =
        faceParts.getOrNull(up)?.let { CopiableValues(it.layout, it.faces, 0, gained, createdBy) } ?: this

    /** These values with [change] made to every face, and to every part of each face ([faceParts]). */
    fun mapFaces(change: (FaceDefinition) -> FaceDefinition): CopiableValues =
        if (faceParts.isEmpty()) copy(faces = faces.map(change))
        else withFaceParts(faceParts.map { it.copy(faces = it.faces.map(change)) })

    /** These values with [parts] as each face's layout and parts ([faceParts]), [faces] their first parts. */
    fun withFaceParts(parts: List<CardDefinition>): CopiableValues =
        copy(faces = parts.map { it.faces.firstOrNull() ?: FaceDefinition("") }, faceParts = parts)

    companion object {
        /** The values of an object with one [face]. */
        fun of(face: FaceDefinition) = CopiableValues(Layout.Normal, listOf(face))
    }
}
