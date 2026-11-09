package arcanum.parser

import arcanum.tools.RegexProbe
import kotlin.text.Regex as RealRegex

/**
 * Measuring only, never shipped: compiled together with a copy of the parser's sources for `:arcanum-tools:parserMetrics`.
 * In package `arcanum.parser` this class outranks `kotlin.text.Regex`, so every rule uses it with no edit. It behaves as
 * the real regex and tells [RegexProbe] when it compiles and when it matches.
 */
class Regex private constructor(private val real: RealRegex) {
    constructor(pattern: String) : this(RealRegex(pattern))
    constructor(pattern: String, option: RegexOption) : this(RealRegex(pattern, option))
    constructor(pattern: String, options: Set<RegexOption>) : this(RealRegex(pattern, options))

    private val usage = RegexProbe.compiled(real.pattern)

    val pattern: String get() = real.pattern
    val options: Set<RegexOption> get() = real.options

    fun matchEntire(input: CharSequence): MatchResult? = real.matchEntire(input)?.also { usage.matched() }
    infix fun matches(input: CharSequence): Boolean = real.matches(input).also { if (it) usage.matched() }
    fun containsMatchIn(input: CharSequence): Boolean = real.containsMatchIn(input).also { if (it) usage.matched() }
    fun find(input: CharSequence, startIndex: Int = 0): MatchResult? = real.find(input, startIndex)?.also { usage.matched() }
    fun findAll(input: CharSequence, startIndex: Int = 0): Sequence<MatchResult> = real.findAll(input, startIndex).onEach { usage.matched() }
    fun matchAt(input: CharSequence, index: Int): MatchResult? = real.matchAt(input, index)?.also { usage.matched() }
    fun matchesAt(input: CharSequence, index: Int): Boolean = real.matchesAt(input, index).also { if (it) usage.matched() }

    fun replace(input: CharSequence, replacement: String): String {
        if (real.containsMatchIn(input)) usage.matched()
        return real.replace(input, replacement)
    }

    fun replace(input: CharSequence, transform: (MatchResult) -> CharSequence): String {
        if (real.containsMatchIn(input)) usage.matched()
        return real.replace(input, transform)
    }

    fun replaceFirst(input: CharSequence, replacement: String): String {
        if (real.containsMatchIn(input)) usage.matched()
        return real.replaceFirst(input, replacement)
    }

    fun split(input: CharSequence, limit: Int = 0): List<String> {
        if (real.containsMatchIn(input)) usage.matched()
        return real.split(input, limit)
    }

    override fun toString(): String = real.toString()

    companion object {
        fun escape(literal: String): String = RealRegex.escape(literal)
    }
}

// The regex taking functions of kotlin.text, for the shadow type.
operator fun CharSequence.contains(regex: Regex): Boolean = regex.containsMatchIn(this)
infix fun CharSequence.matches(regex: Regex): Boolean = regex.matches(this)
fun CharSequence.replace(regex: Regex, replacement: String): String = regex.replace(this, replacement)
fun CharSequence.replace(regex: Regex, transform: (MatchResult) -> CharSequence): String = regex.replace(this, transform)
fun CharSequence.replaceFirst(regex: Regex, replacement: String): String = regex.replaceFirst(this, replacement)
fun CharSequence.split(regex: Regex, limit: Int = 0): List<String> = regex.split(this, limit)
