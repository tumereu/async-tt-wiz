package org.asyncttwiz.android

import org.junit.Assert.assertEquals
import org.junit.Test

/** Android's ICU regex engine rejects an unescaped closing brace that the host JVM accepts.
 * Keep this on-device: even Robolectric native-graphics tests use the host regex engine. */
class CardTextParserDeviceTest {
    @Test fun plainCardNamesAndSymbolsInitializeParserOnAndroid() {
        // The original static initializer crashed before it could parse even a plain card name.
        assertEquals(listOf(CardTextPart.Plain("Island")), parseCardText("Island"))
        assertEquals(listOf(
            CardTextPart.Symbol("T"), CardTextPart.Plain(": Add "),
            CardTextPart.Symbol("U"), CardTextPart.Plain(".")
        ), parseCardText("{T}: Add {U}."))
        assertEquals(listOf("2", "W/U", "B/P", "G/U/P", "Q").map(CardTextPart::Symbol),
            parseCardText("{2}{W/U}{B/P}{G/U/P}{Q}"))
        assertEquals(listOf(CardTextPart.Plain("Unrecognized {UNKNOWN}, {}, {U and } stay literal.")),
            parseCardText("Unrecognized {UNKNOWN}, {}, {U and } stay literal."))
    }
}
