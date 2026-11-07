package org.asyncttwiz.android

import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.DefinitionFace
import org.asyncttwiz.protocol.DeckColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android's ICU regex engine rejects an unescaped closing brace that the host JVM accepts (see CardTextParserDeviceTest),
 * so these run on a device. Problem report FNFEW6 (0.2.0-beta3): opening a game crashed in StackAbilityText's static
 * initializer, reached from the timeline's event texts (HIS-091).
 */
class AndroidRegexDeviceTest {
    @Test fun stackAbilityTextInitializesOnAndroid() {
        // Initializing the object compiles all its patterns; the crash was an ExceptionInInitializerError here.
        Class.forName("org.asyncttwiz.automatum.StackAbilityText", true, javaClass.classLoader)
        val short = org.asyncttwiz.automatum.StackAbilityText.short("Storm Spark deals X damage to any target.", "Storm Spark", x = 3)
        assertTrue(short, "3 damage to any target" in short)
    }

    // LOB-126 (game history): a deck's colours from its mana symbols
    @Test fun deckColorsReadManaSymbolsOnAndroid() {
        val card = CardDefinition("Ember Kit", listOf(DefinitionFace("face", "Ember Kit", typeLine = "Creature", manaCost = "{1}{R}{W/U}")))
        assertEquals("WUR", DeckColors.of(listOf(card)))
    }

    // STK-057: the parser runs on the phone (I-D2); its target spans (parser v39) compile on Android too
    @Test fun parserWithTargetSpansRunsOnAndroid() {
        val face = arcanum.parser.OracleParser.parse(arcanum.parser.OracleCard("Storm Spark", "{R}", "Instant", "Storm Spark deals 3 damage to any target."))
        assertTrue(face.toString(), face.toString().contains("span"))
    }
}
