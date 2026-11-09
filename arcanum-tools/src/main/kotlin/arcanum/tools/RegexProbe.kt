package arcanum.tools

import java.io.File

/**
 * Counts how many cards each of the parser's regexes matches. The parser itself records nothing: `:arcanum-tools:parserMetrics`
 * runs a copy of its source compiled with `src/shadowParser`'s `arcanum.parser.Regex`, which outranks `kotlin.text.Regex` in
 * that package without a rule edited, wraps it and reports here. Against the real parser this object stays empty.
 * Not thread safe: one card at a time.
 */
object RegexProbe {
    /** One pattern's count; a regex keeps the one for its pattern. */
    class Usage(val pattern: String) {
        var supportedCards = 0
            private set
        var allCards = 0
            private set
        private var lastCard = -1

        /** The regex matched: counts the current card once. */
        fun matched() {
            if (card < 0 || card == lastCard) return
            lastCard = card
            allCards++
            if (cardIsSupported) supportedCards++
        }
    }

    private val usages = HashMap<String, Usage>()
    private var card = -1
    private var cardIsSupported = false

    /** Regexes compiled so far: repeats count. */
    var compilations = 0L
        private set

    /** Called by a shadow regex as it compiles. A card's name in a pattern (`Regex.escape(name)`) counts as one pattern. */
    fun compiled(pattern: String): Usage {
        compilations++
        val key = pattern.replace(QUOTED, "\\\\Q...\\\\E")
        return usages.getOrPut(key) { Usage(key) }
    }

    /** Matches from now on count for card number [index] until the next call, or [stop]. */
    fun startCard(index: Int, supported: Boolean) {
        card = index
        cardIsSupported = supported
    }

    fun stop() {
        card = -1
    }

    /** Whether the shadow parser is the one running. */
    val active: Boolean get() = compilations > 0

    fun usages(): List<Usage> = usages.values.sortedBy { it.pattern }

    /**
     * Compiles every regex the parser has, so one the cards never reached counts as matching none: initialises each of its
     * classes (the regexes of its objects) and reads each of its lazily compiled ones. A public-looking getter
     * returning the shadow type is such a regex.
     */
    fun compileAll() {
        val regex = Class.forName("arcanum.parser.Regex")
        val root = File(regex.protectionDomain.codeSource.location.toURI())
        val classes = File(root, "arcanum/parser").listFiles().orEmpty().filter { it.name.endsWith(".class") }.sortedBy { it.name }
        for (file in classes) {
            val loaded = try {
                Class.forName("arcanum.parser." + file.name.removeSuffix(".class"), true, regex.classLoader)
            } catch (e: ReflectiveOperationException) {
                continue
            } catch (e: LinkageError) {
                continue
            }
            val instance = loaded.declaredFields.firstOrNull { it.name == "INSTANCE" }?.apply { isAccessible = true }?.get(null) ?: continue
            loaded.declaredMethods.filter { it.parameterCount == 0 && it.returnType == regex }.forEach { it.isAccessible = true; it.invoke(instance) }
        }
    }

    private val QUOTED = Regex("""\\Q.*?\\E""")
}

/** How many regexes match how many cards: a row of the rule usage table. */
internal class UsageBucket(val label: String, val range: IntRange)

internal val USAGE_BUCKETS = listOf(
    UsageBucket("0", 0..0), UsageBucket("1", 1..1), UsageBucket("2-3", 2..3), UsageBucket("4-9", 4..9),
    UsageBucket("10-49", 10..49), UsageBucket("50+", 50..Int.MAX_VALUE),
)

/** How many of [cardCounts] fall into each of [USAGE_BUCKETS], in order. */
internal fun usageTable(cardCounts: List<Int>): List<Int> = USAGE_BUCKETS.map { b -> cardCounts.count { it in b.range } }
