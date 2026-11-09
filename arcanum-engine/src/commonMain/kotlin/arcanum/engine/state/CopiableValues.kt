package arcanum.engine.state

import arcanum.api.CardDefinition
import arcanum.api.FaceDefinition
import arcanum.api.Layout
import kotlinx.serialization.Serializable

/**
 * An object's copiable values as a whole (CR 707.2): its [layout] and all its [faces], with what copy effects, merging and
 * its face-down status made of them: both halves of a split card (CR 709.5b), a flip card's two versions (CR 707.3), the
 * alternative characteristics of an Adventure, an Omen or a prepare spell (CR 715.2b, 720.2b, 722.2b), both faces of a
 * double-faced object. [up] is the face up (of a double-faced object or a spell), or the face its flipped status picks.
 *
 * What rules a permanent works by comes from these values, read from the view ([arcanum.engine.view.ObjectView.copiable]): a
 * Room's doors, a prepare spell. Designations (unlocked, prepared: CR 709.5c, 722.3a) and status (CR 110.5) aren't among them:
 * they stay on the object ([GameObject.marks], [GameObject.face]). What a permanent physically is decides only what its cards
 * can do: whether it can transform (CR 712.9) or meld (CR 701.42c), read from [GameObject.definition].
 */
@Serializable
data class CopiableValues(val layout: Layout, val faces: List<FaceDefinition>, val up: Int = 0) {
    /** These values as a definition, what a token holds. */
    val definition: CardDefinition get() = CardDefinition(layout, faces)

    /** The face up; the first when [up] is no face of these values (a flipped status that isn't relevant now, CR 110.5c). */
    val upFace: Int get() = if (up in faces.indices) up else 0

    companion object {
        /** The values of an object with one [face]. */
        fun of(face: FaceDefinition) = CopiableValues(Layout.Normal, listOf(face))
    }
}
