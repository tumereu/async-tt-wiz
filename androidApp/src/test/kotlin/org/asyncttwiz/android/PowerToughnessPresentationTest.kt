package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class PowerToughnessPresentationTest {
    // TBL-022
    @Test fun onlyPrintedPowerToughnessIsComparedIncludingVariableValues() {
        val face = DefinitionFace("front", "Creature", power = "2", toughness = "3", corner = "2/3")
        assertFalse(changedPowerToughness("2/3", face))
        assertTrue(changedPowerToughness("3/3", face))
        assertTrue(changedPowerToughness("", face))
        assertFalse(changedPowerToughness("3/3", null))
        assertFalse(changedPowerToughness("5", DefinitionFace("walker", "Walker", loyalty = "3", corner = "3")))
        val variable = face.copy(power = "*", toughness = "1+*")
        assertFalse(changedPowerToughness("*/1+*", variable))
        assertTrue(changedPowerToughness("4/5", variable))
    }

    // TBL-022
    @Test fun sceneUsesCurrentFaceForRealCardsAndTokensAndDoesNotExposeHiddenChanges() {
        val front = DefinitionFace("front", "Front", power = "2", toughness = "3", corner = "2/3")
        val back = DefinitionFace("back", "Back", power = "4", toughness = "5", corner = "4/5")
        val definition = SubmittedDefinition("pt", CardDefinition("Two faces", listOf(front, back)))
        val initial = TableFixture.play(hand = 1, board = 1, library = 1)
        for (token in listOf(false, true)) for (face in listOf(front, back)) for (changed in listOf(false, true)) {
            val play = initial.copy(definitions = listOf(definition), board = initial.board.copy(cards = initial.board.cards.map {
                it.copy(definition = definition.digest, faceId = face.faceId, token = token,
                    corner = if (changed) "7/8" else face.corner)
            }))
            val scene = TableFixture.scene(play)
            for (item in scene.items.filter { it.kind == TableItemKind.CARD || it.kind == TableItemKind.PILE && it.cards.isNotEmpty() }) {
                assertEquals(changed && !item.covered, item.changedPowerToughness, "${item.id}, token=$token, face=${face.faceId}")
            }
        }
    }
}
