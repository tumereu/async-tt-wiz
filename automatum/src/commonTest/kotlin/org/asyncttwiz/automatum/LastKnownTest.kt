package org.asyncttwiz.automatum

import arcanum.api.Zone
import arcanum.engine.cards.Cards
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a gone object may be shown as ([LastKnown]): problem report 3UE2NW, a trigger whose source died. */
class LastKnownTest {
    private val table = EngineTable()
    private val alice get() = table.alice
    private val bob get() = table.bob

    // CR 608.2h, CR 400.7, STK-030: a creature that died was public on the battlefield, so it is shown by what it was (its
    // death trigger to order names it); a card that left the hand (a hidden zone) isn't, nor is an object that still exists
    @Test fun aCreatureThatDiedIsPublicACardThatLeftTheHandIsNot() {
        val martyr = Cards.invent("Lingering Martyr", "{0}", "Creature — Elemental", "When this creature dies, you gain 2 life.", "1/1")
        val sweep = Cards.invent("Sweeping Ash", "{0}", "Sorcery", "Destroy all creatures.")
        val (dying) = table.put(alice, Zone.Battlefield, martyr)
        val (land) = table.put(alice, Zone.Battlefield, "Island")
        table.put(alice, Zone.Library, "Island", "Island")
        table.put(bob, Zone.Library, "Island")
        val (spell) = table.put(alice, Zone.Hand, sweep)
        val died = table.obj(dying)
        val cast = table.obj(spell)
        val state = table.state()
        val working = CastFlow.start(table.source(state), alice, HandPlays.of(state, alice, null).of(spell).first(), CastFlow.Seeds(table::seed))
            .sending(null).cast.working
        val resolved = table.flow(table.flow(working).pass(alice).working).pass(bob).working
        assertNull(resolved.obj(died))
        assertTrue(LastKnown.public(resolved, died), "the Martyr died from the battlefield")
        assertEquals("Lingering Martyr", arcanum.engine.Engine.lastKnown(resolved, died)?.characteristics?.name)
        assertNull(resolved.obj(cast))
        assertFalse(LastKnown.public(resolved, cast), "the Sweep left Alice's hand")
        assertFalse(LastKnown.public(resolved, table.obj(land)), "the Island is still there")
    }
}
