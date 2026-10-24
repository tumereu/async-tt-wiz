package org.asyncttwiz.android

import org.junit.Test
import kotlin.test.*

class CardTextParserTest {
    @Test fun parsesAllSimpleSymbolsAndAdjacentManaCosts() {
        val tokens = listOf("W", "U", "B", "R", "G", "C", "X", "Y", "Z", "T", "Q", "S", "E") + (0..20).map(Int::toString)
        assertEquals(tokens.map(CardTextPart::Symbol), parseCardText(tokens.joinToString("") { "{$it}" }))
    }

    @Test fun parsesEveryColorPairTwoBridAndPhyrexianCombination() {
        val colors = listOf("W", "U", "B", "R", "G")
        val tokens = colors.flatMap { first ->
            listOf("2/$first", "$first/P") + colors.filter { it != first }.flatMap { second ->
                listOf("$first/$second", "$first/$second/P")
            }
        }
        for (token in tokens) assertEquals(listOf(CardTextPart.Symbol(token)), parseCardText("{$token}"))
    }

    @Test fun preservesWhitespaceUnicodePunctuationAndUnknownTokensExactly() {
        val prefix = "  🧙 {CHAOS} {21} {01} {w} {W/W} {C/P} {P/W} {W/2} {}\n"
        val suffix = ": Add {unknown}.\nReminder (unchanged). {unclosed"
        val text = prefix + "{T}" + suffix
        assertEquals(listOf(CardTextPart.Plain(prefix), CardTextPart.Symbol("T"), CardTextPart.Plain(suffix)), parseCardText(text))
        assertEquals(text, parseCardText(text).joinToString("") { when (it) {
            is CardTextPart.Plain -> it.text
            is CardTextPart.Symbol -> "{${it.token}}"
        } })
        assertEquals(emptyList(), parseCardText(""))
        assertEquals(listOf(CardTextPart.Plain("No symbols\n{UNKNOWN}")), parseCardText("No symbols\n{UNKNOWN}"))
    }
}
