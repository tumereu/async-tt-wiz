package org.asyncttwiz.android

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The build check for regex braces Android's ICU regex engine rejects (problem report FNFEW6; see [AndroidRegexCheck]).
 * [phoneSourcesHaveNoRegexBracesAndroidRejects] is the check on the repository; the rest test the checker.
 *
 * Kotlin snippets below are written in raw strings: `'''` stands for a raw-string quote and `§` for `$`.
 */
class AndroidRegexCheckTest {
    @Test fun phoneSourcesHaveNoRegexBracesAndroidRejects() {
        var root = File("").absoluteFile
        while (!File(root, "settings.gradle.kts").isFile) root = root.parentFile
        val report = AndroidRegexCheck.checkRepository(root)
        if (report.unanalysed.isNotEmpty()) println("Regex patterns not checked (no literal part):\n" + report.unanalysed.joinToString("\n"))
        if (report.partial.isNotEmpty()) println("Regex patterns checked on their literal parts only:\n" + report.partial.joinToString("\n"))
        println("Checked ${report.patterns} regex patterns in ${report.files} files")
        assertTrue(report.files > 100 && report.patterns > 1000, "the scan found too little: ${report.files} files, ${report.patterns} patterns")
        assertTrue(report.findings.isEmpty(), "Regex patterns Android can't compile:\n" + report.findings.joinToString("\n"))
    }

    // The two patterns of FNFEW6 (0.2.0-beta3), before and after the fix.
    @Test fun theCrashPatternsAreFlaggedAndTheirFixesPass() {
        assertEquals(listOf(1), lines("""private val LONE_X = Regex("(?<!\\{)\\bX\\b(?!})")"""))
        assertEquals(listOf(1), lines("""val letters = Regex("\\{([^}]*)}").findAll(text)"""))
        assertOk("""private val LONE_X = Regex("(?<!\\{)\\bX\\b(?!\\})")""")
        assertOk("""val letters = Regex("\\{([^}]*)\\}").findAll(text)""")
    }

    @Test fun quantifiersPass() {
        assertOk("""Regex("a{2}b{2,}c{2,15}")""")
        assertOk("""Regex("(ab){3}")""")
    }

    @Test fun bracesThatAreNotQuantifiersAreFlagged() {
        assertEquals(2, findings("""Regex("{X}")""").size)
        assertEquals(2, findings("""Regex("a{,5}")""").size, "{,m} isn't a quantifier")
        assertEquals(listOf('{', '}'), findings("""Regex("a{x}")""").map { it.problem[it.problem.indexOf('\'') + 1] })
        assertEquals(1, findings("""Regex("a}")""").size)
    }

    @Test fun propertyAndCodePointEscapesPass() {
        assertOk("""Regex("\\p{L}+\\P{Lu}\\x{1F600}\\N{LATIN SMALL LETTER A}\\p{IsAlphabetic}")""")
    }

    @Test fun bracesInCharacterClassesPass() {
        assertOk("""Regex("[{}][^}]+[a[{]][\\]}][]}][a&&[^}]]")""")
        assertEquals(1, findings("""Regex("[a]}")""").size, "the class ends before the brace")
    }

    @Test fun escapesAreRespected() {
        assertOk("""Regex("\\{T\\}: Add \\{G\\}")""")
        assertOk("""Regex("\\Q{X}\\E")""")
        assertEquals(1, findings("""Regex("\\\\}")""").size, "an escaped backslash doesn't escape the brace")
    }

    @Test fun rawStringsAreReadWithoutEscapes() {
        assertOk(kt("""Regex('''\{T\}: Add \{G\}\.''')"""))
        val raw = findings(kt("""Regex('''{T}''')"""))
        assertEquals(2, raw.size)
        assertTrue("escape it as \\} in this raw string" in raw.last().problem, raw.last().problem)
        val quoted = findings("""Regex("{T}")""")
        assertTrue("escape it as \\\\} in this quoted string" in quoted.last().problem, quoted.last().problem)
        assertTrue("ICU" in quoted.last().problem)
        // The line is the brace's own line in a multi-line raw string.
        assertEquals(listOf(4), lines(kt("val r = Regex(\n    '''\n    a\n    b}\n    ''')")))
        assertOk(kt("Regex('''a\"\"''')"))
    }

    @Test fun otherCallFormsAreChecked() {
        assertEquals(1, findings(""""a}".toRegex()""").size)
        assertEquals(1, findings("""("a" + "}").toRegex()""").size)
        assertEquals(1, findings("""java.util.regex.Pattern.compile("a}")""").size)
        assertEquals(1, findings("""Regex(pattern = "a}", option = RegexOption.IGNORE_CASE)""").size)
        assertEquals(1, findings(""""x".replace(Regex("a}")) { it.value }""").size)
    }

    @Test fun literalPatternsCommentsAndOtherStringsAreNotRegexes() {
        assertOk("""Regex("a}", RegexOption.LITERAL)""")
        assertOk("""Pattern.compile("{X}", Pattern.LITERAL)""")
        assertOk("// Regex(\"}\")\n/* Regex(\"}\") /* nested */ Regex(\"}\") */")
        assertOk("""val s = "Regex(\"}\")"; val c = '"'; val t = "{X}"""")
    }

    @Test fun templatesAndConcatenationAreCheckedOnTheirLiteralParts() {
        assertEquals(1, findings(kt("""Regex("\\b§name}")""")).size)
        assertEquals(1, findings(kt("""Regex(Regex.escape(name) + " a}")""")).size)
        assertOk(kt("""Regex("a{§n}b{§{n + 1},}")"""))
        val partial = AndroidRegexCheck.checkSource("F.kt", kt("""Regex("\\b(§{words.joinToString("|")})\\b")""")).partial
        assertEquals(1, partial.size)
        assertTrue("words.joinToString(\"|\")" in partial.single().expression, partial.single().expression)
        assertOk(kt("""Regex('''§{'§'}\{''' + "\\}")"""))
        // Regex.escape(..) is quoted text: safe, so not listed as unknown.
        val escaped = AndroidRegexCheck.checkSource("F.kt", kt("""Regex("\\b§{Regex.escape(a)}" + Regex.escape(b) + "\\b")"""))
        assertEquals(emptyList(), escaped.partial + escaped.unanalysed)
    }

    @Test fun sameFileStringConstantsAreRead() {
        assertEquals(listOf(1), lines(kt("const val P = \"x}\"\nval r = Regex(P)")))
        assertEquals(listOf(1), lines(kt("private const val P = \"a|\" + \"b}\"\nval r = Regex(\"(§P)\")")))
        assertOk(kt("const val P = \"x\\\\}\"\nval r = Regex(\"(§{P})\")"))
    }

    @Test fun patternsWithNoLiteralPartAreListedNotFailed() {
        val report = AndroidRegexCheck.checkSource("F.kt", "val r = Regex(pattern)\nval s = names.toRegex()\nval t = Regex(f(x))")
        assertEquals(emptyList(), report.findings)
        assertEquals(listOf(1, 2, 3), report.unanalysed.map { it.line })
    }

    private fun kt(source: String) = source.replace("'''", "\"\"\"").replace('§', '$')
    private fun findings(source: String) = AndroidRegexCheck.checkSource("F.kt", source).findings
    private fun lines(source: String) = findings(source).map { it.line }
    private fun assertOk(source: String) = assertEquals(emptyList(), findings(source), source)
}
