package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.FaceDefinition
import arcanum.api.Layout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What ATW shows of a token with several faces, a copy of a card that has them (SYS-080). */
class AtwTokensTest {
    private fun face(name: String, text: String) = FaceDefinition(name, types = listOf(CardType.Enchantment), abilities = listOf(Ability.Unparsed(text)))

    // SYS-080, TBL-106: a double-faced token shows each side
    @Test
    fun aDoubleFacedTokenHasTwoFaces() {
        val card = AtwTokens.submitted(CardDefinition(Layout.Transform, listOf(face("Pale Pup", "Bites."), face("Pale Howler", "Howls.")))).definition
        assertEquals("transform", card.layout)
        assertEquals(listOf("Pale Pup", "Pale Howler"), card.faces.map { it.name })
    }

    // SYS-080: a token with the halves of a Room is one physical face, as the card is, and reads both halves
    @Test
    fun aTokenOfARoomIsOneFaceWithBothHalves() {
        val card = AtwTokens.submitted(CardDefinition(Layout.Split, listOf(face("Lantern Foyer", "Glows."), face("Quiet Pantry", "Hushes.")))).definition
        assertEquals("split", card.layout)
        assertEquals("Lantern Foyer // Quiet Pantry", card.name)
        val face = card.faces.single()
        assertEquals(listOf("Lantern Foyer", "Quiet Pantry"), face.parts.map { it.name })
        assertTrue("Glows." in face.rulesText && "Hushes." in face.rulesText, face.rulesText)
    }

    // A one-faced token stays as it was
    @Test
    fun aOneFacedTokenHasOneFace() {
        val card = AtwTokens.submitted(CardDefinition(Layout.Normal, listOf(face("Pale Pup", "Bites.")))).definition
        assertEquals("normal", card.layout)
        assertEquals("Bites.", card.faces.single().rulesText)
        assertTrue(card.faces.single().parts.isEmpty())
    }
}
