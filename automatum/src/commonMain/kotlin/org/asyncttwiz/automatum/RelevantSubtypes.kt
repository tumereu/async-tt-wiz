package org.asyncttwiz.automatum

import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.SubtypeReferences
import arcanum.api.Zone
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.NamedValue
import arcanum.engine.state.PlayerId
import arcanum.engine.state.Secrets
import arcanum.engine.view.AbilityOrigin
import arcanum.engine.view.Characteristics
import arcanum.engine.view.View

/**
 * TBL-234, TBL-237 (owner, 2026-11-21): the subtypes worth a tag on the battlefield for [viewer]: those the cards they can see refer to
 * ([SubtypeReferences]: "for each Lizard you control", "each saddled Mount"). The cards they can see are their own hand (every
 * face of each card), every face-up permanent (the abilities it has now, so gained and lost abilities count) and the face-up
 * spells and the abilities on the Stack; never another player's hand, a library or a face-down card, so the tags tell nothing
 * hidden. "The chosen type" a permanent keeps counts once chosen, unless it is a secret [viewer] doesn't know.
 */
object RelevantSubtypes {
    fun of(state: GameState, view: View, viewer: String): Set<Subtype> {
        val player = PlayerId(viewer)
        val found = LinkedHashSet<Subtype>()
        fun add(o: GameObject?, refs: Collection<SubtypeRef>) {
            for (ref in refs) when (ref) {
                is SubtypeRef.Literal -> found += ref.subtype
                is SubtypeRef.Remembered -> remembered(o, ref.key, player)?.let { found += it }
                is SubtypeRef.Chosen -> Unit
            }
        }
        if (state.players.any { it.id == player }) for (card in state.objectsIn(Zone.Hand, player)) {
            card.definition.faces.forEach { add(null, it.referencedSubtypes) }
        }
        for (o in state.objectsIn(Zone.Battlefield)) {
            if (o.faceDown || o.phasedOut) continue
            add(o, ofObject(o, view[o.id]?.characteristics))
        }
        for (id in state.stack) {
            val item = state.stackItems[id]
            val card = state.obj(id)
            if (card != null && !card.faceDown) add(card, ofObject(card, view[id]?.characteristics))
            item?.ability?.let { add(item.source?.let(state::obj), SubtypeReferences.of(it)) }
        }
        return found
    }

    /** What [o] refers to with the abilities it has now ([chars]; its face's when they can't be worked out): a printed ability's
     * references are kept on its face ([arcanum.api.FaceDefinition.abilitySubtypeReferences]), a gained one's worked out here. */
    private fun ofObject(o: GameObject, chars: Characteristics?): Set<SubtypeRef> {
        if (chars == null) return o.definition.faces.getOrNull(o.face)?.referencedSubtypes.orEmpty()
        val refs = LinkedHashSet<SubtypeRef>()
        for (instance in chars.abilities) {
            when (val origin = instance.origin) {
                // A keyword's expansion is its keyword's (SubtypeReferences.of(keyword)); a land type's mana ability, a keyword counter's
                // keyword and the rules' special actions refer to no subtype
                is AbilityOrigin.Expansion, is AbilityOrigin.LandType, is AbilityOrigin.FromCounter, is AbilityOrigin.Rule -> Unit
                is AbilityOrigin.Printed -> {
                    val face = if (origin.card == null) o.definition.faces.getOrNull(origin.face) else null
                    // A copy's printed abilities are the copied card's (CR 707.2): worked out from the ability itself
                    if (face != null && face.abilities.getOrNull(origin.index) === instance.ability) refs += face.abilitySubtypeReferences[origin.index]
                    else refs += SubtypeReferences.of(instance.ability)
                }
                else -> refs += SubtypeReferences.of(instance.ability)
            }
        }
        return refs
    }

    private fun remembered(o: GameObject?, key: String, viewer: PlayerId): Subtype? {
        o ?: return null
        if (Secrets.isSecret(o, key) && Secrets.knownBy(o, key)?.contains(viewer) != true) return null
        return (o.linked[key] as? NamedValue.SubtypeValue)?.subtype
    }
}
