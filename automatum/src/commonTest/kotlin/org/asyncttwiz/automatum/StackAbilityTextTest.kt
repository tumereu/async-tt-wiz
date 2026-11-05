package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.engine.cards.Cards
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind
import org.asyncttwiz.protocol.MtgRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StackAbilityTextTest {
    private val heron = Cards.invent("Tidewatch Heron", "{2}{U}", "Creature — Bird", """
        Flying
        When this creature enters, choose one —
        • Tap target nonland permanent.
        • Untap target nonland permanent.
    """, "2/2")

    private fun entry(modes: List<Int>) = Cards.definition(heron).faces.single().abilities.filterIsInstance<Ability.Triggered>().single()
        .let { ability ->
            EngineCodec.stackItem(StackItem(ObjectId.StackItem("S1"), StackItemKind.Triggered, PlayerId("alice"), null, ability,
                modes = modes, timestamp = 1)) { MtgRef.StackItem("S1") }
        }

    // STK-050: holding an ability on the Stack names its chosen modes on the ability's tile (Inspect shows every face)
    @Test fun aTriggeredAbilityNamesItsChosenModes() {
        assertEquals(listOf("Untap target nonland permanent."), StackAbilityText.modes(entry(listOf(1))))
        assertTrue(StackAbilityText.modes(entry(emptyList())).isEmpty())
    }
}
