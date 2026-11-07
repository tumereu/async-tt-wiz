package org.asyncttwiz.android

import java.io.File

/**
 * Build check for regex patterns Android can't compile. Android's `java.util.regex` is backed by ICU, which rejects a
 * `}` or `{` outside a character class that isn't part of a quantifier (`{n}`, `{n,}`, `{n,m}`) or a `\p{..}`-style
 * escape; the host JVM accepts them, so JVM tests pass and the phone crashes (problem report FNFEW6, see
 * AndroidRegexDeviceTest). This reads Kotlin sources, finds the regex pattern literals (`Regex(..)`,
 * `Pattern.compile(..)`, `".."`.toRegex()), decodes the Kotlin string literal and checks the regex text's braces.
 *
 * Dynamic patterns are checked on their literal parts: string templates and non-literal `+` operands become unknown
 * parts, except names of string constants declared in the same file, which are read, and `Regex.escape(..)`, which is
 * safe. Patterns with no literal part are listed in [Report.unanalysed]; unknown parts in [Report.partial].
 */
object AndroidRegexCheck {
    /** Modules whose code runs on the phone. */
    val PHONE_MODULES = listOf(
        "androidApp", "automatum", "protocol", "core", "client-storage", "arcanum-api", "arcanum-parser", "arcanum-engine",
    )

    /** Source sets that don't run on the phone: tests, and the JVM (server) side of multiplatform modules. */
    private val SKIPPED_SOURCE_SETS = setOf(
        "test", "androidTest", "commonTest", "jvmTest", "androidUnitTest", "androidInstrumentedTest", "androidHostTest",
        "androidDeviceTest", "jvmMain",
    )

    /** A brace Android's ICU regex rejects. */
    data class Finding(val file: String, val line: Int, val pattern: String, val problem: String) {
        override fun toString() = "$file:$line: $problem\n    pattern: $pattern"
    }

    /** A regex call site (or a part of one) the check could not read. */
    data class Unanalysed(val file: String, val line: Int, val expression: String) {
        override fun toString() = "$file:$line: $expression"
    }

    data class Report(
        val findings: List<Finding>,
        val unanalysed: List<Unanalysed>,
        val partial: List<Unanalysed>,
        val patterns: Int,
        val files: Int = 1,
    ) {
        operator fun plus(other: Report) = Report(
            findings + other.findings, unanalysed + other.unanalysed, partial + other.partial,
            patterns + other.patterns, files + other.files,
        )
    }

    /** Checks the phone modules' Kotlin sources under the repository [root]. */
    fun checkRepository(root: File): Report = PHONE_MODULES.flatMap { module ->
        File(root, "$module/src").listFiles().orEmpty().filter { it.isDirectory && it.name !in SKIPPED_SOURCE_SETS }
            .flatMap { set -> set.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
    }.sorted().map { checkSource(it.relativeTo(root).invariantSeparatorsPath, it.readText()) }
        .fold(Report(emptyList(), emptyList(), emptyList(), 0, 0), Report::plus)

    /** Checks one Kotlin source file; [path] is only used in the messages. */
    fun checkSource(path: String, source: String): Report {
        val tokens = Lexer(source).lex()
        val constants = constants(tokens)
        val findings = mutableListOf<Finding>()
        val unanalysed = mutableListOf<Unanalysed>()
        val partial = mutableListOf<Unanalysed>()
        var patterns = 0
        for (pattern in regexCallSites(tokens)) {
            if (pattern.expression.isEmpty()) continue
            val decoded = decode(pattern.expression, constants, emptySet())
            if (decoded.chars.all { it.dynamic != null }) {
                unanalysed += Unanalysed(path, pattern.line, render(pattern.expression))
                continue
            }
            patterns++
            decoded.chars.mapNotNull { it.dynamic }.distinct()
                .forEach { partial += Unanalysed(path, pattern.line, "${render(pattern.expression)}  (unknown part: $it)") }
            val shown = decoded.chars.joinToString("") { char -> char.dynamic?.let { "\${$it}" } ?: char.char.toString() }
            for (problem in braceProblems(decoded.chars)) {
                val at = decoded.chars[problem.index]
                findings += Finding(path, at.line, shown, problem.message(at.raw))
            }
        }
        return Report(findings, unanalysed, partial, patterns)
    }

    // ---- Regex analysis ------------------------------------------------------------------------------------------

    /** A character of a decoded pattern: its source line, whether it came from a raw string, or an unknown part. */
    data class PatternChar(val char: Char, val line: Int, val raw: Boolean, val dynamic: String? = null)

    class BraceProblem(val index: Int, private val brace: Char, private val reason: String) {
        fun message(raw: Boolean): String {
            val inSource = if (raw) "\\$brace" else "\\\\$brace"
            return "unescaped '$brace' $reason. Android's regex engine (ICU) rejects it although the JVM accepts it, " +
                "so it crashes only on phones: escape it as $inSource in this ${if (raw) "raw" else "quoted"} string (regex \\$brace)"
        }
    }

    private val QUANTIFIER = Regex("""\{\d+(?:,\d*)?\}""")

    /** Braces in [pattern] that are neither escaped, in a character class, a quantifier nor a `\p{..}`-style escape. */
    fun braceProblems(pattern: List<PatternChar>): List<BraceProblem> {
        val text = pattern.joinToString("") { if (it.dynamic != null) "￿" else it.char.toString() }
        val problems = mutableListOf<BraceProblem>()
        var classDepth = 0
        var i = 0
        fun enterClass() {
            classDepth++
            i++
            if (i < text.length && text[i] == '^') i++
            if (i < text.length && text[i] == ']') i++ // a ']' first in a class is a literal
        }
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length -> {
                    val next = text[i + 1]
                    i = when {
                        next == 'Q' -> text.indexOf("\\E", i + 2).let { if (it < 0) text.length else it + 2 }
                        next in "pPxN" && text.getOrNull(i + 2) == '{' ->
                            text.indexOf('}', i + 3).let { if (it < 0) text.length else it + 1 }
                        else -> i + 2
                    }
                }
                c == '[' -> enterClass()
                classDepth > 0 -> { if (c == ']') classDepth--; i++ }
                c == '{' -> {
                    val quantifier = QUANTIFIER.matchAt(text, i)
                    val close = text.indexOf('}', i)
                    i = when {
                        quantifier != null -> i + quantifier.value.length
                        // {${n}}: a quantifier with an unknown count; can't tell, so don't fail on it.
                        close > 0 && '￿' in text.substring(i, close) -> close + 1
                        else -> {
                            problems += BraceProblem(i, '{', "that doesn't start a quantifier {n}, {n,} or {n,m}")
                            i + 1
                        }
                    }
                }
                c == '}' -> { problems += BraceProblem(i, '}', "outside a character class and not closing a quantifier"); i++ }
                else -> i++
            }
        }
        return problems
    }

    // ---- Kotlin call sites -----------------------------------------------------------------------------------------

    private class CallSite(val expression: List<Token>, val line: Int)

    private fun regexCallSites(tokens: List<Token>): List<CallSite> {
        val sites = mutableListOf<CallSite>()
        fun word(i: Int, text: String) = (tokens.getOrNull(i) as? Word)?.text == text
        fun mark(i: Int, char: Char) = (tokens.getOrNull(i) as? Mark)?.char == char
        for (i in tokens.indices) {
            val argsStart = when {
                word(i, "Regex") && mark(i + 1, '(') -> i + 2
                word(i, "Pattern") && mark(i + 1, '.') && word(i + 2, "compile") && mark(i + 3, '(') -> i + 4
                mark(i, '.') && (word(i + 1, "toRegex") || word(i + 1, "toPattern")) && mark(i + 2, '(') -> {
                    if (literalFlag(arguments(tokens, i + 3))) continue
                    sites += CallSite(receiver(tokens, i), tokens[i].line)
                    continue
                }
                else -> continue
            }
            val args = arguments(tokens, argsStart)
            if (args.isEmpty() || literalFlag(args)) continue
            var first = args.first()
            if ((first.getOrNull(0) as? Word)?.text == "pattern" && (first.getOrNull(1) as? Mark)?.char == '=') first = first.drop(2)
            sites += CallSite(first, tokens[i].line)
        }
        return sites
    }

    /** `Regex(.., RegexOption.LITERAL)` / `Pattern.LITERAL` isn't a regex. */
    private fun literalFlag(args: List<List<Token>>) = args.drop(1).flatten().any { (it as? Word)?.text == "LITERAL" }

    /** The comma-separated arguments of a call whose first argument starts at [start]. */
    private fun arguments(tokens: List<Token>, start: Int): List<List<Token>> {
        val args = mutableListOf<List<Token>>()
        var current = mutableListOf<Token>()
        var depth = 0
        var i = start
        while (i < tokens.size) {
            val token = tokens[i]
            if (token is Mark) {
                when (token.char) {
                    '(', '[', '{' -> depth++
                    ')', ']', '}' -> if (depth == 0) {
                        if (current.isNotEmpty()) args += current
                        return args
                    } else depth--
                    ',' -> if (depth == 0) { args += current; current = mutableListOf(); i++; continue }
                }
            }
            current += token
            i++
        }
        return args
    }

    /** The receiver of the `.toRegex()` whose dot is at [dot]: a literal, or a parenthesized expression. */
    private fun receiver(tokens: List<Token>, dot: Int): List<Token> {
        val before = tokens.getOrNull(dot - 1) ?: return emptyList()
        if (before is Str) return listOf(before)
        if (before is Mark && before.char == ')') {
            var depth = 0
            for (j in dot - 1 downTo 0) {
                val t = tokens[j] as? Mark ?: continue
                if (t.char in ")]}") depth++ else if (t.char in "([{") depth--
                if (depth == 0) {
                    // f(..).toRegex(): the call's result is unknown, so keep the whole call as one unknown part.
                    return if (tokens.getOrNull(j - 1) is Word) tokens.subList(j - 1, dot) else tokens.subList(j + 1, dot - 1)
                }
            }
        }
        return listOf(before)
    }

    // ---- Decoding pattern expressions ------------------------------------------------------------------------------

    private class Decoded(val chars: List<PatternChar>)

    /** Same-file `val NAME = "..." + "..."` string constants (names declared once only). */
    private fun constants(tokens: List<Token>): Map<String, List<Token>> {
        val found = mutableMapOf<String, List<Token>?>()
        for (i in tokens.indices) {
            if ((tokens[i] as? Word)?.text != "val") continue
            val name = (tokens.getOrNull(i + 1) as? Word)?.text ?: continue
            var j = i + 2
            if ((tokens.getOrNull(j) as? Mark)?.char == ':' && (tokens.getOrNull(j + 1) as? Word)?.text == "String") j += 2
            if ((tokens.getOrNull(j) as? Mark)?.char != '=') continue
            // "a" + "b", ending where the next token doesn't continue the expression with . or +
            val parts = mutableListOf<Token>()
            j++
            while (tokens.getOrNull(j) is Str) {
                parts += tokens[j]
                if ((tokens.getOrNull(j + 1) as? Mark)?.char == '+' && tokens.getOrNull(j + 2) is Str) { parts += tokens[j + 1]; j += 2 } else j++
            }
            val pure = parts.isNotEmpty() && (tokens.getOrNull(j) as? Mark)?.char.let { it != '.' && it != '+' }
            found[name] = if (name in found || !pure) null else parts
        }
        return found.filterValues { it != null }.mapValues { it.value!! }
    }

    private fun decode(expression: List<Token>, constants: Map<String, List<Token>>, seen: Set<String>): Decoded {
        val chars = mutableListOf<PatternChar>()
        for (operand in splitTopLevel(expression, '+')) {
            val single = operand.singleOrNull()
            val name = (single as? Word)?.text
            when {
                single is Str -> single.parts.forEach { part ->
                    when (part) {
                        is Chars -> part.text.forEachIndexed { k, c -> chars += PatternChar(c, part.lines[k], single.raw) }
                        is Template -> chars += template(part, single.raw, constants, seen)
                    }
                }
                name != null && name in constants && name !in seen -> chars += decode(constants.getValue(name), constants, seen + name).chars
                isEscape(operand) -> chars += PatternChar('x', operand.first().line, false)
                else -> chars += PatternChar('?', operand.firstOrNull()?.line ?: 0, false, render(operand))
            }
        }
        return Decoded(chars)
    }

    private fun template(part: Template, raw: Boolean, constants: Map<String, List<Token>>, seen: Set<String>): List<PatternChar> {
        val only = part.tokens.singleOrNull()
        val name = (only as? Word)?.text
        // ${'$'}: the usual way to write a literal $ in a raw string.
        if (name != null && name.length == 3 && name.startsWith('\'') && name.endsWith('\'')) return listOf(PatternChar(name[1], part.line, raw))
        if (name != null && name in constants && name !in seen) return decode(constants.getValue(name), constants, seen + name).chars
        if (isEscape(part.tokens)) return listOf(PatternChar('x', part.line, raw))
        return listOf(PatternChar('?', part.line, raw, part.source))
    }

    /** `Regex.escape(..)`: quoted literal text, safe whatever it holds; stands in as a plain letter. */
    private fun isEscape(tokens: List<Token>): Boolean {
        if (tokens.size < 5 || (tokens[0] as? Word)?.text != "Regex" || (tokens[1] as? Mark)?.char != '.' ||
            (tokens[2] as? Word)?.text != "escape" || (tokens[3] as? Mark)?.char != '(') return false
        var depth = 0
        tokens.drop(3).forEachIndexed { k, token ->
            val char = (token as? Mark)?.char
            if (char == '(') depth++ else if (char == ')') depth--
            if (depth == 0) return k == tokens.size - 4
        }
        return false
    }

    private fun splitTopLevel(tokens: List<Token>, separator: Char): List<List<Token>> {
        val parts = mutableListOf(mutableListOf<Token>())
        var depth = 0
        for (token in tokens) {
            if (token is Mark) when (token.char) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
                separator -> if (depth == 0) { parts += mutableListOf<Token>(); continue }
            }
            parts.last() += token
        }
        return parts
    }

    private fun render(tokens: List<Token>): String = tokens.joinToString(" ") { token ->
        when (token) {
            is Word -> token.text
            is Mark -> token.char.toString()
            is Str -> (if (token.raw) "\"\"\"" else "\"") + token.parts.joinToString("") { part ->
                when (part) { is Chars -> part.text; is Template -> "\${${part.source}}" }
            } + (if (token.raw) "\"\"\"" else "\"")
        }
    }.replace(" ( ", "(").replace(" )", ")").replace(" . ", ".")

    // ---- Kotlin lexer ----------------------------------------------------------------------------------------------

    private sealed interface Token { val line: Int }
    private class Word(val text: String, override val line: Int) : Token
    private class Mark(val char: Char, override val line: Int) : Token
    private class Str(val parts: List<StrPart>, val raw: Boolean, override val line: Int) : Token

    private sealed interface StrPart
    /** Decoded literal text of a string, with each character's source line. */
    private class Chars(val text: String, val lines: IntArray) : StrPart
    private class Template(val source: String, val tokens: List<Token>, val line: Int) : StrPart

    /** Just enough of Kotlin's lexical grammar: comments, char literals, strings (escapes, raw, templates), words. */
    private class Lexer(private val src: String) {
        private var pos = 0
        private var line = 1

        fun lex(): List<Token> = tokens(untilCloseBrace = false)

        private fun tokens(untilCloseBrace: Boolean): List<Token> {
            val out = mutableListOf<Token>()
            var depth = 0
            while (pos < src.length) {
                val c = src[pos]
                when {
                    c == '\n' -> { line++; pos++ }
                    c.isWhitespace() -> pos++
                    src.startsWith("//", pos) -> while (pos < src.length && src[pos] != '\n') pos++
                    src.startsWith("/*", pos) -> blockComment()
                    src.startsWith("\"\"\"", pos) -> out += string(raw = true)
                    c == '"' -> out += string(raw = false)
                    c == '\'' -> out += charLiteral()
                    c == '`' -> {
                        val start = pos
                        pos = src.indexOf('`', pos + 1).let { if (it < 0) src.length else it + 1 }
                        out += Word(src.substring(start + 1, (pos - 1).coerceAtLeast(start + 1)), line)
                    }
                    c.isLetterOrDigit() || c == '_' -> {
                        val start = pos
                        while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] == '_')) pos++
                        out += Word(src.substring(start, pos), line)
                    }
                    c == '}' && untilCloseBrace && depth == 0 -> { pos++; return out }
                    else -> {
                        if (c == '{') depth++ else if (c == '}') depth--
                        out += Mark(c, line)
                        pos++
                    }
                }
            }
            return out
        }

        private fun blockComment() {
            var depth = 0
            while (pos < src.length) {
                when {
                    src.startsWith("/*", pos) -> { depth++; pos += 2 }
                    src.startsWith("*/", pos) -> { depth--; pos += 2; if (depth == 0) return }
                    else -> { if (src[pos] == '\n') line++; pos++ }
                }
            }
        }

        private fun charLiteral(): Token {
            val start = pos
            val startLine = line
            pos++
            while (pos < src.length && src[pos] != '\'') pos += if (src[pos] == '\\') 2 else 1
            pos++
            return Word(src.substring(start, pos.coerceAtMost(src.length)), startLine)
        }

        private fun string(raw: Boolean): Str {
            val startLine = line
            pos += if (raw) 3 else 1
            val parts = mutableListOf<StrPart>()
            val text = StringBuilder()
            val lines = mutableListOf<Int>()
            fun add(c: Char) { text.append(c); lines += line }
            fun flush() {
                if (text.isNotEmpty()) parts += Chars(text.toString(), lines.toIntArray())
                text.clear(); lines.clear()
            }
            while (pos < src.length) {
                val c = src[pos]
                when {
                    raw && src.startsWith("\"\"\"", pos) -> {
                        // The closing quotes are the last three of a run; any before them belong to the string.
                        var end = pos
                        while (end < src.length && src[end] == '"') end++
                        repeat(end - pos - 3) { add('"') }
                        pos = end
                        flush()
                        return Str(parts, raw = true, startLine)
                    }
                    !raw && c == '"' -> { pos++; flush(); return Str(parts, raw = false, startLine) }
                    !raw && c == '\\' && pos + 1 < src.length -> {
                        val e = src[pos + 1]
                        pos += 2
                        when (e) {
                            't' -> add('\t'); 'b' -> add('\b'); 'n' -> add('\n'); 'r' -> add('\r')
                            'u' -> { add(src.substring(pos, (pos + 4).coerceAtMost(src.length)).toIntOrNull(16)?.toChar() ?: '?'); pos += 4 }
                            else -> add(e) // \\ \" \' \$
                        }
                    }
                    c == '$' && src.getOrNull(pos + 1) == '{' -> {
                        flush()
                        val templateLine = line
                        pos += 2
                        val start = pos
                        val inner = tokens(untilCloseBrace = true)
                        parts += Template(src.substring(start, (pos - 1).coerceAtLeast(start)), inner, templateLine)
                    }
                    c == '$' && src.getOrNull(pos + 1)?.let { it.isLetter() || it == '_' } == true -> {
                        flush()
                        val start = ++pos
                        while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] == '_')) pos++
                        val name = src.substring(start, pos)
                        parts += Template(name, listOf(Word(name, line)), line)
                    }
                    else -> { add(c); if (c == '\n') line++; pos++ }
                }
            }
            flush()
            return Str(parts, raw, startLine)
        }
    }
}
