package org.asyncttwiz.automatum

import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.Automatum
import org.asyncttwiz.protocol.PlayZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A token that leaves the Battlefield while a resolving spell stops for a decision (final cast review, seed 111): ATW's
 * token stops existing at once (TBL-103), the engine's stays in its new zone until state-based actions are next checked
 * (CR 111.7, 704.5d) and the spell reads it there ("its controller", CR 608.2h). The open flow's record keeps it
 * ([Lingering]), so every call round-trips ([RoundTrip]: rebuilt state = the engine's own, undo restores the board).
 */
class LingeringTokenTest {
    private val table = AtwTable("lingering")
    private val alice get() = table.alice
    private val bob get() = table.bob

    private val find = Cards.invent("Glinting Find", "{R}", "Sorcery", "Create a Treasure token.")
    private val ruin = Cards.invent("Ruinous Reclaim", "{B}", "Instant",
        "Destroy target artifact. Its controller may search their library for a basic land card, put that card onto the battlefield tapped, then shuffle.")
    private val banish = Cards.invent("Banishing Reclaim", "{B}", "Instant",
        "Exile target artifact. Its controller may search their library for a basic land card, put that card onto the battlefield tapped, then shuffle.")

    private fun tokens(rt: RoundTrip) = rt.state.board.cards.filter { it.token }

    /** Alice makes a Treasure, passes, and Bob casts [spell] at it; both pass and it resolves up to "may search". */
    private fun reclaimed(spell: String): RoundTrip {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        table.put(alice, PlayZone.HAND, find)
        table.put(alice, PlayZone.GRAVEYARD, "Island")
        table.put(alice, PlayZone.LIBRARY, "Plains", "Island", "Forest")
        table.put(bob, PlayZone.BATTLEFIELD, "Swamp")
        table.put(bob, PlayZone.HAND, spell)
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = RoundTrip(table.state(), table.definitions)
        rt.cast(alice, find)
        rt.settle()
        rt.resolveAll()
        assertEquals(1, tokens(rt).size)
        rt.pass(alice)
        rt.cast(bob, spell)
        rt.target(TargetRef.Object(rt.engine().objects.values.single { it.token && it.zone == Zone.Battlefield }.id))
        rt.settle()
        rt.resolveAll()
        return rt
    }

    // CR 701.8a, 111.7, 704.5d, 608.2h, TBL-103: the destroyed Treasure is gone from the table at once, while the engine,
    // asking its controller "may search", still has it in Alice's graveyard (on top) until state-based actions
    @Test fun aDestroyedTokenStaysInTheEnginesGraveyardWhileTheSpellAsks() {
        val rt = reclaimed(ruin)
        val may = assertNotNull(rt.decision as? Decision.May, "${rt.decision}")
        assertEquals(PlayerId(alice), may.player, "the token's controller, by last known information")
        assertTrue(tokens(rt).isEmpty(), "TBL-103: players see no token in a graveyard")
        val engine = rt.engine()
        val graveyard = engine.player(PlayerId(alice)).graveyard.map { engine.objOrThrow(it) }
        assertEquals(listOf(false, false, true), graveyard.map { it.token }, "the Island and Glinting Find, then the token on top")
        // Both players' views read it too
        for (viewer in table.players) assertTrue(rt.engine(viewer).objects.values.any { it.token && it.zone == Zone.Graveyard })

        rt.answer { it == Answer.YesNo(true) }
        val search = rt.decision as Decision.ChooseObjects
        rt.answer { it is Answer.Objects && it.chosen.size == 1 }
        assertNull(rt.engine().pending, "$search answered")
        // CR 704.5d: the next state-based check removed the engine's token as well
        assertTrue(rt.engine().objects.values.none { it.token })
        assertNull(rt.state.board.engineRecords.singleOrNull { it.id == Automatum.CONTINUATION_ID })
        assertEquals(2, rt.state.board.containers.single { it.zone == PlayZone.GRAVEYARD && it.accountId == alice }.cards.size)
        assertEquals(2, rt.state.board.containers.single { it.zone == PlayZone.BATTLEFIELD && it.accountId == alice }.cards.size)
    }

    // CR 406.3, 111.7, 704.5d, TBL-103: an exiled token likewise, in Exile (timestamp order) until state-based actions
    @Test fun anExiledTokenStaysInTheEnginesExileWhileTheSpellAsks() {
        val rt = reclaimed(banish)
        assertNotNull(rt.decision as? Decision.May, "${rt.decision}")
        assertTrue(tokens(rt).isEmpty())
        assertTrue(rt.engine().exile.map { rt.engine().objOrThrow(it) }.single().token)
        rt.answer { it == Answer.YesNo(false) }
        assertTrue(rt.engine().exile.isEmpty())
        assertTrue(rt.engine().objects.values.none { it.token })
    }
}
