package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.FaceDefinition
import arcanum.api.Keyword
import arcanum.api.PredefinedTokens
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

    // SYS-080: what a token has in no face reads on the card, with both doors
    @Test
    fun whatATokenHasInNoFaceIsReadOnIt() {
        val face = AtwTokens.submitted(CardDefinition(Layout.Split, listOf(face("Lantern Foyer", "Glows."), face("Quiet Pantry", "Hushes."))),
            gained = listOf(Ability.Unparsed("Soars."))).definition.faces.single()
        assertTrue(face.parts.all { "Soars." in it.rulesText }, face.rulesText)
    }

    // A one-faced token stays as it was
    @Test
    fun aOneFacedTokenHasOneFace() {
        val card = AtwTokens.submitted(CardDefinition(Layout.Normal, listOf(face("Pale Pup", "Bites.")))).definition
        assertEquals("normal", card.layout)
        assertEquals("Bites.", card.faces.single().rulesText)
        assertTrue(card.faces.single().parts.isEmpty())
    }

    /** The faults reported while [block] runs. */
    private fun faults(block: () -> Unit): List<org.asyncttwiz.protocol.Fault> {
        val faults = mutableListOf<org.asyncttwiz.protocol.Fault>()
        val sink = org.asyncttwiz.protocol.Faults.sink
        org.asyncttwiz.protocol.Faults.sink = { faults += it }
        try { block() } finally { org.asyncttwiz.protocol.Faults.sink = sink }
        return faults
    }

    private fun rulesText(face: FaceDefinition) = AtwTokens.submitted(CardDefinition(Layout.Normal, listOf(face))).definition.faces.single().rulesText

    // SYS-086, SYS-072 (CR 111.10, owner fault 2PTQG5): every predefined token's card reads each of its abilities as the rules
    // word it (a Role's enchant keyword aside), without a fault
    @Test
    fun aPredefinedTokenReadsItsAbilitiesAsTheRulesWordThem() {
        val faults = faults {
            PredefinedTokens.names.forEach { name ->
                val face = PredefinedTokens.face(name)!!
                val expected = face.abilities.filter { it !is Keyword }.map { CastFlow.text(it)!! }.joinToString("\n")
                assertEquals(expected, rulesText(face), name)
            }
            fun text(name: String) = rulesText(PredefinedTokens.face(name)!!)
            assertEquals("{2}, {T}, Sacrifice this token: You gain 3 life.", text("Food"))
            assertEquals("{T}, Sacrifice this token: Add one mana of any color.", text("Treasure"))
            assertEquals("{2}, Sacrifice this token: Draw a card.", text("Clue"))
            assertEquals("{1}, {T}, Sacrifice this token: Target creature you control explores. Activate only as a sorcery.", text("Map"))
            assertEquals("Enchanted creature gets +1/+1.\nWhen this token is put into a graveyard from the battlefield, each opponent loses 1 life.",
                text("Wicked Role"))
            assertEquals("", text("Walker"))
        }
        assertEquals(emptyList(), faults)
    }

    // SYS-086: an ability with no text of its own is left out of a token's rules text and reported, naming the token
    @Test
    fun anAbilityWithoutTextIsReported() {
        val food = PredefinedTokens.face("Food")!!
        val bare = food.copy(name = "Bare Food", abilities = food.abilities.map { (it as Ability.Activated).copy(text = null) })
        val faults = faults { assertEquals("", rulesText(bare)) }
        assertEquals("Bare Food", faults.single().details["face"])
    }
}
