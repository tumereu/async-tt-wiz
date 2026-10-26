package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ArtworkReference
import org.junit.Test
import kotlin.test.*

class TableArtworkTest {
    @Test fun everyServerAcceptedFaceCountFallsBackForUnsupportedArtworkIndices() {
        for (count in listOf(17, 64)) {
            val ids = List(count) { java.util.UUID.randomUUID().toString() }
            val definition = CardDefinition("Many faces", ids.map { id ->
                DefinitionFace(id, "Face", ids - id, scryfallId = printing, imageUri = custom.customUrl)
            })
            assertTrue(DeckValidation.definition(definition))
            val submitted = SubmittedDefinition(DeckJson.digest(definition), DeckJson.normalized(definition))
            assertEquals((0..15).toList(), gameArtworkReferences(listOf(submitted)).map { it.face })
            assertTrue(InspectedCard.retained(submitted.digest, definition).artwork.drop(16).all { it == null })
            val initial = TableFixture.play(board = 1, hand = 1)
            val play = initial.copy(definitions = listOf(submitted), board = initial.board.copy(cards = initial.board.cards.map {
                it.copy(definition = submitted.digest, faceId = ids.last())
            }))
            assertTrue(TableFixture.scene(play).items.filter { it.cardId != null }.all { it.artwork == null })
        }
    }

    private val printing = "2487c5bd-8d7f-5925-af7a-a2f6e5eb6b7a"
    private val full = ArtworkReference(printing, 0, "v1")
    private val custom = full.copy(customUrl = "https://example.com/card.png")

    @Test fun tableUsesArtCropButCustomUrlTakesPrecedenceOverPrinting() {
        assertEquals("art_crop", full.forTable().size)
        assertEquals(TableArtworkMode.ART_CROP, tableArtworkMode(full.forTable(), true))
        assertEquals("normal", custom.forTable().size)
        assertEquals(custom.customUrl, custom.forTable().customUrl)
        assertEquals(TableArtworkMode.CUSTOM_CARD, tableArtworkMode(custom.forTable(), true))
        assertEquals(TableArtworkMode.CUSTOM_CARD, tableArtworkMode(custom.copy(printing = null).forTable(), true))
        assertEquals("normal", full.size, "table conversion must not change full-card consumers")
        assertNotEquals(CardArtworkRequest(full, 100, 140).key, CardArtworkRequest(full.forTable(), 100, 140).key)
    }

    @Test fun unavailableImagesKeepTheTypeIconFallback() {
        for (reference in listOf(full.forTable(), custom.forTable(), null)) {
            assertEquals(TableArtworkMode.TYPE_ICON, tableArtworkMode(reference, false))
        }
        assertEquals(TableArtworkMode.TYPE_ICON, tableArtworkMode(null, true))
    }

    @Test fun gameWarmupIncludesNormalAndCropForEachFaceAndCustomImagesOnlyOnce() {
        val definition = CardDefinition("Card", listOf(
            DefinitionFace("front", "Front", scryfallId = printing),
            DefinitionFace("back", "Back", scryfallId = printing),
            DefinitionFace("custom", "Custom", scryfallId = printing, imageUri = custom.customUrl),
            DefinitionFace("missing", "Missing")))
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        val references = gameArtworkReferences(listOf(submitted, submitted))
        assertEquals(listOf(0 to "normal", 0 to "art_crop", 1 to "normal", 1 to "art_crop", 2 to "normal"),
            references.map { it.face to it.size })
        assertTrue(references.all { it.version == submitted.digest })
        assertEquals(custom.customUrl, references.last().customUrl)
    }

    @Test fun sceneRequestsTableVariantsWhileInspectionKeepsFullImagesAndCoveredCardsStayHidden() {
        for (imageUri in listOf(null, custom.customUrl)) {
            val definition = TableFixture.definition.copy(faces = TableFixture.definition.faces.map {
                it.copy(scryfallId = printing, imageUri = imageUri)
            })
            val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
            val initial = TableFixture.play(board = 1, hand = 1)
            val play = initial.copy(definitions = listOf(submitted), board = initial.board.copy(cards = initial.board.cards.map {
                it.copy(definition = submitted.digest)
            }))
            val scene = TableFixture.scene(play)
            val visible = scene.items.filter { it.cardId != null && !it.covered }
            assertTrue(visible.isNotEmpty())
            assertTrue(visible.all { it.artwork?.size == if (imageUri == null) "art_crop" else "normal" })
            assertTrue(scene.items.filter { it.covered }.all { it.artwork == null })
            assertEquals("large", InspectedCard.retained(submitted.digest, definition).artwork.single()?.size)
        }
    }
}
