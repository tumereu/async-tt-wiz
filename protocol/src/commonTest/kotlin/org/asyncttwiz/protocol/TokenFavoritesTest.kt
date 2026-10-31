package org.asyncttwiz.protocol

import kotlin.test.*

class TokenFavoritesTest {
    private val token = CardDefinition("Spirit", listOf(DefinitionFace("00000000-0000-4000-8000-000000000001", "Spirit",
        typeLine = "Token Creature — Spirit", rulesText = "Flying", power = "1", toughness = "1", corner = "1/1",
        sourceData = """{"colors":["W","U"],"keywords":["Flying"]}""")), token = true)

    @Test fun identityIgnoresPrintingButIncludesFunctionalFieldsAndNormalizesSets() {
        val key = TokenIdentity.key(token)
        assertTrue(TokenIdentity.validKey(key))
        assertEquals(key, TokenIdentity.key(token.copy(printingId = "new", sourceVersion = "new", faces = token.faces.map {
            it.copy(faceId = "new", scryfallId = "new", imageUri = "new",
                sourceData = """{"colors":["U","W"],"keywords":["Flying"],"setCode":"NEW"}""")
        })))
        for (face in listOf(token.faces.first().copy(power = "2"), token.faces.first().copy(rulesText = "Vigilance"),
            token.faces.first().copy(typeLine = "Token Creature — Bird"), token.faces.first().copy(sourceData = """{"colors":["B"]}""")))
            assertNotEquals(key, TokenIdentity.key(token.copy(faces = listOf(face))))
    }

    // TBL-099
    @Test fun neverSetAndExplicitlyEmptyAreDifferent() {
        assertTrue(TokenIdentity.favorite(token.copy(name = "Treasure"), TokenFavorites()))
        assertFalse(TokenIdentity.favorite(token.copy(name = "Treasure"), TokenFavorites(emptyList())))
    }
}
