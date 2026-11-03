package org.asyncttwiz.automatum

import arcanum.api.CardDefinition
import arcanum.api.Layout
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** I-D51: a card is supported when Automatum plays all of it by the rules; invented cards with oracle-style wording. */
class AutomatumSupportTest {
    private fun card(text: String, type: String = "Creature — Bear", cost: String = "{1}{G}") = CardDefinition(Layout.Normal,
        listOf(OracleParser.parse(OracleCard("Mossback Cub", cost, type, text, power = "2", toughness = "2"))))

    @Test fun aFullyParsedCardIsSupported() {
        val definition = card("Trample\nWhen this creature enters, you gain 2 life.")
        assertEquals(emptyList(), AutomatumSupport.handParts(definition))
        assertFalse(AutomatumSupport.byHand(definition))
    }

    @Test fun anUnparsedLineIsPlayedByHand() {
        val definition = card("Trample\nWhenever the moon is full, this creature dances a slow jig with a goblin.")
        assertTrue(AutomatumSupport.byHand(definition))
        assertEquals(1, AutomatumSupport.handParts(definition).size)
        assertTrue(AutomatumSupport.handParts(definition).single().startsWith("unread text"))
    }

    // Banding was the last keyword done only by hand; the engine now carries it out (CR 702.22), so the card is supported
    @Test fun aKeywordTheEngineCarriesOutIsSupported() {
        val definition = card("Banding")
        assertTrue(definition.faces.single().abilities.none { it is arcanum.api.Ability.Unparsed }, "the parser reads the keyword")
        assertEquals(emptyList(), AutomatumSupport.handParts(definition))
    }

    @Test fun aManualStepIsPlayedByHand() {
        val definition = CardDefinition(Layout.Normal, listOf(arcanum.api.FaceDefinition("Odd Rite", types = listOf(arcanum.api.CardType.Sorcery),
            abilities = listOf(arcanum.api.Ability.Spell(arcanum.api.Instructions(steps = listOf(arcanum.api.Step.Manual("Do the odd thing"))))))))
        assertEquals(listOf("by hand: Do the odd thing"), AutomatumSupport.handParts(definition))
    }

    // SYS-064: a card the device couldn't read plays with its whole text by hand
    @Test fun aPrintedOnlyCardIsPlayedByHand() {
        val printed = PrintedCards.definition(org.asyncttwiz.protocol.CardDefinition("Odd", listOf(
            org.asyncttwiz.protocol.DefinitionFace("o", "Odd", typeLine = "Creature — Bear", rulesText = "Flying"))))
        assertTrue(AutomatumSupport.byHand(printed))
    }
}
