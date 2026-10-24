package org.asyncttwiz.android

internal sealed interface CardTextPart {
    data class Plain(val text: String) : CardTextPart
    data class Symbol(val token: String) : CardTextPart
}

private val cardTextToken = Regex("\\{([^{}]*)}")
private val manaColors = setOf("W", "U", "B", "R", "G")
private val simpleSymbols = manaColors + setOf("C", "X", "Y", "Z", "T", "Q", "S", "E") + (0..20).map(Int::toString)

/** Presentation only: preserve every character outside the explicitly supported symbol vocabulary. */
internal fun parseCardText(text: String): List<CardTextPart> = buildList {
    var start = 0
    for (match in cardTextToken.findAll(text)) {
        val token = match.groupValues[1]
        val parts = token.split('/')
        val known = token in simpleSymbols || when (parts.size) {
            2 -> (parts[0] in manaColors && parts[1] in manaColors && parts[0] != parts[1]) ||
                (parts[0] == "2" && parts[1] in manaColors) || (parts[0] in manaColors && parts[1] == "P")
            3 -> parts[0] in manaColors && parts[1] in manaColors && parts[0] != parts[1] && parts[2] == "P"
            else -> false
        }
        if (!known) continue
        if (match.range.first > start) add(CardTextPart.Plain(text.substring(start, match.range.first)))
        add(CardTextPart.Symbol(token))
        start = match.range.last + 1
    }
    if (start < text.length) add(CardTextPart.Plain(text.substring(start)))
}
