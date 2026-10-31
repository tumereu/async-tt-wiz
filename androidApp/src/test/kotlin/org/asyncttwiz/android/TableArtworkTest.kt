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
            val warmed = gameArtworkReferences(listOf(submitted))
            for (size in listOf("normal", "large")) assertEquals((0..15).toList(), warmed.filter { it.size == size }.map { it.face })
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

    // TBL-019
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

    // TBL-019
    @Test fun unavailableImagesKeepTheTypeIconFallback() {
        for (reference in listOf(full.forTable(), custom.forTable(), null)) {
            assertEquals(TableArtworkMode.TYPE_ICON, tableArtworkMode(reference, false))
        }
        assertEquals(TableArtworkMode.TYPE_ICON, tableArtworkMode(null, true))
    }

    @Test fun warmupUsesExactlyTheTableThenInspectorReferencesForEachFace() {
        val definition = CardDefinition("Card", listOf(
            DefinitionFace("front", "Front", scryfallId = printing),
            DefinitionFace("back", "Back", scryfallId = printing),
            DefinitionFace("custom", "Custom", scryfallId = printing, imageUri = custom.customUrl),
            DefinitionFace("missing", "Missing")))
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        val references = gameArtworkReferences(listOf(submitted, submitted))
        assertEquals(listOf(0 to "art_crop", 1 to "art_crop", 2 to "normal", 0 to "large", 1 to "large", 2 to "large"),
            references.map { it.face to it.size })
        assertTrue(references.all { it.version == submitted.digest })
        assertEquals(custom.customUrl, references.last().customUrl)
        // The very keys the inspector and the table request, so warmed entries are reused as-is.
        val inspected = InspectedCard.retained(submitted.digest, definition).artwork.filterNotNull()
            .filter { it.printing != null || it.customUrl != null }
        assertEquals(inspected, references.drop(3))
        val initial = TableFixture.play(board = 1, hand = 1)
        val play = initial.copy(definitions = listOf(submitted), board = initial.board.copy(cards = initial.board.cards.map {
            it.copy(definition = submitted.digest, faceId = "front")
        }))
        val shown = TableFixture.scene(play).items.mapNotNull { it.artwork }.toSet()
        assertTrue(shown.isNotEmpty() && shown.all { it in references })
    }

    @Test fun gameWarmupStartsWithTheViewersHandThenBattlefield() {
        fun definition(name: String) = CardDefinition(name, listOf(DefinitionFace(java.util.UUID.randomUUID().toString(), name,
            scryfallId = java.util.UUID.nameUUIDFromBytes(name.toByteArray()).toString())))
        val initial = TableFixture.play(board = 1, hand = 1)
        val viewer = initial.board.players.first().accountId
        val hand = initial.board.containers.single { it.accountId == viewer && it.zone == PlayZone.HAND && !it.temp }
        val field = initial.board.containers.single { it.accountId == viewer && it.zone == PlayZone.BATTLEFIELD && !it.temp }
        val names = mapOf(hand.id to "Hand", field.id to "Field")
        val definitions = initial.board.cards.associate { card ->
            val digestOf = definition(names[card.containerId] ?: "Other ${card.id}")
            card.id to SubmittedDefinition(DeckJson.digest(digestOf), digestOf)
        }
        // Definition order deliberately puts the hand last.
        val play = initial.copy(definitions = definitions.values.distinct().sortedBy { if (it.definition.name == "Hand") 1 else 0 },
            board = initial.board.copy(cards = initial.board.cards.map { card ->
                definitions.getValue(card.id).let { card.copy(definition = it.digest, faceId = it.definition.faces.single().faceId) }
            }))
        val order = gameArtworkReferences(play, viewer).filter { it.size == "art_crop" }.map { reference ->
            play.definitions.single { it.digest == reference.version }.definition.name
        }
        assertEquals(listOf("Hand", "Field"), order.take(2))
        assertEquals(play.definitions.size, order.size)
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
