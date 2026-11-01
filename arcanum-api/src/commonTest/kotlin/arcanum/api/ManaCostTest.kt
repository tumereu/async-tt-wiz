package arcanum.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ManaCostTest {
    // CR 107.4: every kind of mana symbol reads and writes back unchanged.
    @Test
    fun everySymbolKindRoundTrips() {
        val text = "{X}{2}{W}{U}{B}{R}{G}{C}{S}{W/U}{2/B}{R/P}{G/W/P}{0}"
        val cost = ManaCost.parse(text)!!
        assertEquals(
            listOf(ManaSymbol.Variable('X'), ManaSymbol.Generic(2), ManaSymbol.Colored(Color.White),
                ManaSymbol.Colored(Color.Blue), ManaSymbol.Colored(Color.Black), ManaSymbol.Colored(Color.Red),
                ManaSymbol.Colored(Color.Green), ManaSymbol.Colorless, ManaSymbol.Snow,
                ManaSymbol.Hybrid(Color.White, Color.Blue), ManaSymbol.GenericHybrid(2, Color.Black),
                ManaSymbol.Phyrexian(Color.Red), ManaSymbol.PhyrexianHybrid(Color.Green, Color.White),
                ManaSymbol.Generic(0)),
            cost.symbols)
        assertEquals(text, cost.toString())
    }

    @Test
    fun anythingButManaSymbolsIsRefused() {
        listOf("", "2W", "{2}{W} and/or {1}", "{T}", "{W/W}", "{H}", "{Q}", "{W}{", "{W/U/B}").forEach {
            assertNull(ManaCost.parse(it), it)
        }
    }
}
