package org.asyncttwiz.android

/**
 * STK-230, STK-142 (owner 2026-11-25): the part of a spell's or ability's whole text a panel is about, drawn as the target bar
 * draws the phrase picked now: [start] (inclusive) to [end] (exclusive) in the text, and [why] it is that part, for the log.
 */
data class AskedSpan(val start: Int, val end: Int, val why: String) {
    /** The highlighted words in [text]. */
    fun of(text: String): String = text.substring(start, end)
}

/**
 * STK-230, STK-142 (owner 2026-11-25: the part a panel is about highlighted only where it is known exactly, never a guess):
 * where in a spell's or ability's whole text the question a panel asks, or the reveal it shows, sits. A question's words
 * ([clauses]) are found as a run of whole clauses of the text, compared as [QuestionLine] compares them (the engine words a
 * "you may", a search or a "choose" as the printed sentence of its text); a reveal ([sentence]) as the one sentence of the text
 * saying what it did ("reveal", "exile"). Nothing when the words are found nowhere, more than once, or make up the whole text
 * (then nothing tells a part apart).
 */
object AskedPart {
    /** [asked]'s span in [text] when its words are exactly one run of whole clauses of it, not all of it; else null. */
    fun clauses(text: String, asked: String?): AskedSpan? {
        val want = tokens(asked ?: return null).map { it.word }.trimBreaks()
        if (want.none { it != BREAK }) return null
        val have = tokens(text)
        val found = (0..have.size - want.size).filter { at ->
            (0 until want.size).all { have[at + it].word == want[it] } &&
                (at == 0 || have[at - 1].word == BREAK) && (at + want.size == have.size || have[at + want.size].word == BREAK)
        }
        val at = found.singleOrNull() ?: return null
        val words = have.subList(at, at + want.size).filter { it.word != BREAK }
        return span(text, words.first().start, words.last().end, "the question's words are a run of whole clauses of the text")
    }

    /**
     * The span of the one sentence of [text] (a line or a sentence of it, a mode's bullet left out, text in quotation marks
     * another ability's) that [says] matches, as [what] for the log; null when none or several do, or it is the whole text.
     */
    fun sentence(text: String, says: Regex, what: String): AskedSpan? {
        val matching = sentences(text).filter { (start, end) -> says.findAll(text.substring(start, end)).any { !quoted(text, start + it.range.first) } }
        val (start, end) = matching.singleOrNull() ?: return null
        return span(text, start, end, "the one sentence that says $what")
    }

    /** Words that say a card was revealed ("reveal", "reveals"; not "revealed", which tells of an earlier reveal). */
    val REVEAL = Regex("""\breveals?\b""", RegexOption.IGNORE_CASE)

    /** Words that say cards are exiled ("exile", "exiles"). */
    val EXILE = Regex("""\bexiles?\b""", RegexOption.IGNORE_CASE)

    /** A sentence moving cards out of a graveyard or exile into [to] ("hand", "library"), naming both. */
    fun leaving(from: String, to: String): Regex =
        Regex("""^(?=.*\b(?:$from)\b)(?=.*\b(?:$to)\b)""", RegexOption.IGNORE_CASE)

    private fun span(text: String, wordsStart: Int, wordsEnd: Int, why: String): AskedSpan? {
        var start = wordsStart
        var end = wordsEnd
        // A symbol ("{2}", "{T}") is drawn whole
        while (start > 0 && text[start - 1] == '{') start--
        while (end < text.length && text[end] == '}') end++
        // The sentence's own full stop belongs to it
        while (end < text.length && text[end] in ".!?") end++
        if (text.substring(0, start).isBlank() && text.substring(end).isBlank()) return null
        return AskedSpan(start, end, why)
    }

    /** Each sentence of [text] as its start and end: its lines, split after ". ", "! " or "? " before a capital letter. */
    private fun sentences(text: String): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        var lineStart = 0
        for (line in text.split('\n')) {
            var from = line.indexOf("• ").takeIf { it >= 0 && line.substring(0, it).isBlank() }?.plus(2) ?: 0
            for (m in SENTENCE_BREAK.findAll(line)) {
                if (m.range.first > from) out += lineStart + from to lineStart + m.range.first
                from = m.range.last + 1
            }
            if (line.length > from) out += lineStart + from to lineStart + line.length
            lineStart += line.length + 1
        }
        return out.map { (s, e) ->
            var a = s; var b = e
            while (a < b && text[a].isWhitespace()) a++
            while (b > a && text[b - 1].isWhitespace()) b--
            a to b
        }.filter { it.second > it.first }
    }

    /** Whether [at] in [text] sits inside quotation marks (another ability's text, granted or made). */
    private fun quoted(text: String, at: Int): Boolean = text.substring(0, at).count { it == '"' || it == '“' || it == '”' } % 2 == 1

    private val SENTENCE_BREAK = Regex("""(?<=[.!?]|[.!?]["”])\s+(?=[A-Z"“])""")

    private const val BREAK = "|"

    private class Token(val word: String, val start: Int, val end: Int)

    /** [text] as lower-case words with a break at each clause break (as [QuestionLine]'s, and at a line break), with where each word sits. */
    private fun tokens(text: String): List<Token> {
        val out = mutableListOf<Token>()
        var wordStart = -1
        fun endWord(at: Int) { if (wordStart >= 0) { out += Token(text.substring(wordStart, at).lowercase(), wordStart, at); wordStart = -1 } }
        text.forEachIndexed { i, c ->
            when {
                c.isLetterOrDigit() -> if (wordStart < 0) wordStart = i
                c in CLAUSE_BREAKS -> { endWord(i); if (out.lastOrNull()?.word != BREAK) out += Token(BREAK, i, i + 1) }
                else -> endWord(i)
            }
        }
        endWord(text.length)
        return out
    }

    private fun List<String>.trimBreaks(): List<String> = dropWhile { it == BREAK }.dropLastWhile { it == BREAK }

    // A line break ends a clause too (a spell's lines)
    private const val CLAUSE_BREAKS = ".,;:!?()[]—–\n"
}
