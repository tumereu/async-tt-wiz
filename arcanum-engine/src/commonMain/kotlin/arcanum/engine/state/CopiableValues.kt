package arcanum.engine.state

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.FaceDefinition
import arcanum.api.Layout
import kotlinx.serialization.Serializable

/**
 * An object's copiable values as a whole (CR 707.2): its [layout] and all its [faces], with what copy effects, merging and
 * its face-down status made of them: both halves of a split card (CR 709.5b), a flip card's two versions (CR 707.3), the
 * alternative characteristics of an Adventure, an Omen or a prepare spell (CR 715.2b, 720.2b, 722.2b), both faces of a
 * double-faced object. [up] is the face up (of a double-faced object or a spell), or for a flip card the version its flipped
 * status picks. [gained]: abilities in none of its faces, so whichever door is locked or face is up it has them, once: what a
 * split card gained in copying (CR 707.9a, 709.5), and the other components' abilities of a merged permanent with its own card
 * on top (CR 702.140e).
 *
 * What rules a permanent works by comes from these values, read from the view ([arcanum.engine.view.ObjectView.copiable]): a
 * Room's doors, a prepare spell. Designations (unlocked, prepared: CR 709.5c, 722.3a) and status (CR 110.5) aren't among them:
 * they stay on the object ([GameObject.marks], [GameObject.flipped], from [ObjectStatus.Flipped] unless it is a flip card,
 * whose [GameObject.face] is its status). What a permanent physically is decides only what its cards
 * can do: whether it can transform (CR 712.9) or meld (CR 701.42c), read from [GameObject.definition].
 */
@Serializable
data class CopiableValues(
    val layout: Layout, val faces: List<FaceDefinition>, val up: Int = 0, val gained: List<Ability> = emptyList(),
    /** isd: for a token, the object whose ability created it, what its abilities' [arcanum.api.Objects.Creator] means; part of the
     * token's text, so a copy of the token refers to the same object (CR 111.3, 201.5a). */
    val createdBy: ObjectId? = null,
) {
    /** These values as a definition, what a token holds (and [ObjectStatus.GainedInCopying]). */
    val definition: CardDefinition get() = CardDefinition(layout, faces)

    companion object {
        /** The values of an object with one [face]. */
        fun of(face: FaceDefinition) = CopiableValues(Layout.Normal, listOf(face))
    }
}
