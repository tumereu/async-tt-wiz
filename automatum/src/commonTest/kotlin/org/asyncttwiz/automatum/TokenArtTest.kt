package org.asyncttwiz.automatum

import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.PlayZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** SYS-072 (integration spec §4.6, tokens): a token Automatum makes carries the picture the host finds for it in its
 * own creation, while the engine keeps its definition; every call is checked by [RoundTrip] (the rebuilt engine state
 * equals the engine's own, and undoing the batch gives the board back). */
class TokenArtTest {
    private val table = AtwTable()
    private val alice get() = table.alice
    private val bob get() = table.bob
    private val picture = "00000000-0000-4000-9000-000000000001"

    private fun shades(art: TokenArt): RoundTrip {
        table.put(alice, PlayZone.BATTLEFIELD, "Plains", "Plains", "Plains")
        table.put(alice, PlayZone.HAND, "Lingering Shades")
        table.put(alice, PlayZone.LIBRARY, "Plains", "Plains")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = RoundTrip(table.state(), table.definitions, tokenArt = art)
        rt.cast(alice, "Lingering Shades")
        rt.settle()
        rt.resolveAll()
        return rt
    }
    private fun tokenFaces(rt: RoundTrip) = rt.state.board.cards.filter { it.token }.map { card ->
        rt.state.definitions.single { it.digest == card.definition }.definition.faces.single()
    }

    // SYS-072
    @Test fun theFoundPictureGoesIntoTheTokensCreationAndTheRoundTripHolds() {
        val asked = mutableListOf<TokenArtQuery>()
        val rt = shades { query -> asked += query; picture.takeIf { query.face.name == "Spirit Token" } }
        val faces = tokenFaces(rt)
        assertEquals(2, faces.size)
        assertTrue(faces.all { it.scryfallId == picture })
        // Its card data is otherwise what ATW shows of the engine's token
        assertTrue(faces.all { it.name == "Spirit Token" && "Spirit" in it.typeLine && "Flying" in it.rulesText }, "$faces")
        assertEquals(listOf("Lingering Shades"), asked.map { it.sourceName }.distinct())
        assertEquals("W", asked.first().face.colorIndicator)
        // The picture travels in the recorded creation itself: both players, undo, redo and branches replay it
        val created = rt.batches.flatten().map { it.action }.filterIsInstance<PlayAction.CreateToken>()
        assertEquals(2, created.size)
        assertTrue(created.all { it.definition.definition.faces.single().scryfallId == picture })
    }

    // SYS-072
    @Test fun withoutAPictureTheTokenKeepsThePlainFrame() {
        val faces = tokenFaces(shades(TokenArt.None))
        assertEquals(2, faces.size)
        assertTrue(faces.all { it.scryfallId == null })
    }

    // SYS-072
    @Test fun aFailedLookupKeepsThePlainFrame() {
        val faces = tokenFaces(shades { error("no card records") })
        assertEquals(2, faces.size)
        assertTrue(faces.all { it.scryfallId == null })
    }
}
