package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class TokenActionsTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"

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
