package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class TokenActionsTest {
    @Test fun flatListDeduplicatesPrintingsAndSortsBlankAlphabeticallyWithinFavoritesAndOthers() {
        fun token(name: String, printing: String = name, rules: String = "") = CardDefinition(name,
            listOf(DefinitionFace(id(1), name, corner = "1/1", rulesText = rules)), token = true, printingId = printing)
            .let { SubmittedDefinition(DeckJson.digest(it), it) }
        val spirit = token("Spirit", rules = "Flying")
        val soldier = token("Soldier")
        val treasure = token("Treasure")
        val food = token("Food")
        val angel = token("Angel")
        val tokens = listOf(treasure, soldier, spirit, token("Treasure", "another"), food, angel, TokenCatalog.blank, TokenCatalog.blank)
        assertEquals(listOf("Food", "Treasure", "Angel", "Blank", "Soldier", "Spirit"), TokenActions.ordered(tokens).map { it.definition.name })
        val favorites = TokenFavorites(listOf(TokenIdentity.key(spirit.definition), TokenIdentity.key(soldier.definition)))
        assertEquals(listOf("Soldier", "Spirit", "Angel", "Blank", "Food", "Treasure"), TokenActions.ordered(tokens, favorites).map { it.definition.name })
        val noFavorites = TokenFavorites(emptyList())
        assertEquals(listOf("Angel", "Blank", "Food", "Soldier", "Spirit", "Treasure"),
            TokenActions.ordered(tokens, noFavorites).map { it.definition.name })
        assertEquals(TokenActions.ordered(tokens, noFavorites),
            TokenChoiceIndex(tokens.map(org.asyncttwiz.storage.CatalogTokenChoice::from)).ordered(noFavorites).map { it.submitted })
        assertEquals("1/1 · Flying", TokenActions.detail(spirit.definition))
        val filtered = TokenActions.ordered(TokenActions.catalog(tokens, "TREASURE"), favorites)
        assertEquals(1, filtered.size)
        assertEquals(TokenIdentity.key(treasure.definition), TokenIdentity.key(filtered.single().definition))
    }
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"

    @Test fun tokenDescriptionsUseTheSharedCardSymbolVocabulary() {
        val definition = CardDefinition("Treasure", listOf(DefinitionFace(id(1), "Treasure",
            typeLine = "Token Artifact — Treasure", rulesText = "{T}, Sacrifice: Add {G} or {W/U}. Pay {X}.")), token = true)
        assertEquals(listOf("T", "G", "W/U", "X"), parseCardText(TokenActions.detail(definition))
            .filterIsInstance<CardTextPart.Symbol>().map { it.token })
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        assertEquals(TokenActions.detail(definition), org.asyncttwiz.storage.CatalogTokenChoice.from(submitted).detail)
    }

    @Test fun searchIncludesBlankFiltersNonTokensAndPreservesExactPrintingIdentity() {
        fun soldier(printing: String): SubmittedDefinition = CardDefinition("Soldier", listOf(
            DefinitionFace(id(1), "Soldier", corner = "1/1")), token = true, printingId = printing)
            .let { SubmittedDefinition(DeckJson.digest(it), it) }
        val first = soldier("first"); val second = soldier("second")
        val ordinary = first.definition.copy(token = false).let { SubmittedDefinition(DeckJson.digest(it), it) }
        val catalog = listOf(first, second, ordinary, first, first.copy(digest = "bad"))
        assertEquals(setOf(first, second, TokenCatalog.blank), TokenActions.catalog(catalog, "").toSet())
        assertEquals(setOf(first, second), TokenActions.catalog(catalog, " SOLDIER ").toSet())
        assertEquals(listOf(TokenCatalog.blank), TokenActions.catalog(emptyList(), "blank"))
        assertTrue(TokenActions.catalog(catalog, "unknown").isEmpty())
    }

    @Test fun endpointLabelsDoNotRevealCoveredNamesAndConnectionsStayIdentityBased() {
        val definition = CardDefinition("Secret", listOf(DefinitionFace(id(11), "Secret")))
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        val a = PlayCard(id(9), submitted.digest, id(7), id(12), id(11), covered = true)
        val b = a.copy(id = id(10), covered = false, token = true)
        val pair = PlayLink(id(20), LinkEndpoint(LinkEndpointKind.CARD, a.id), LinkEndpoint(LinkEndpointKind.PLAYER, id(8)))
        val play = PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))), listOf(a, b),
                listOf(PlayContainer(id(12), PlayZone.BATTLEFIELD, id(7), listOf(a.id, b.id))), TurnState(id(7)), links = listOf(pair)),
            listOf(submitted), emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))
        val endpoints = LinkActions.endpoints(play, mapOf(id(8) to "Other player"))
        assertFalse(endpoints.single { it.endpoint.id == a.id }.label.contains("Secret"))
        assertTrue(endpoints.single { it.endpoint.id == b.id }.label.contains("Secret"))
        assertEquals("Other player", endpoints.single { it.endpoint.id == id(8) }.label)
        assertEquals(listOf(pair), LinkActions.connections(play, TokenLinkScope(a.id, listOf(a.id, b.id))))
        assertTrue(LinkActions.connections(play, TokenLinkScope(b.id, listOf(b.id))).isEmpty()) // equal definition is not a relationship
        assertTrue(TokenActions.removable(play, TokenLinkScope(a.id, listOf(a.id))).isEmpty())
        assertEquals(listOf(b.id), TokenActions.removable(play, TokenLinkScope(a.id, listOf(a.id, b.id))))
    }
}
