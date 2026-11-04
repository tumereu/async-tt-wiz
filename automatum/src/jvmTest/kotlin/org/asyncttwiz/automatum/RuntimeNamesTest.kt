package org.asyncttwiz.automatum

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The code a phone and the server both run never builds what they must agree on (decision ids, early-answer keys, the
 * options of a choice, anything sent or kept) from a class's runtime name (problem report N7FU6Y). The shrunk Android
 * build drops the InnerClasses attribute of these classes, so `Decision.Divide::class.simpleName` is "Decision$Divide"
 * on a phone and "Divide" on the server; names that matter are written out (Decision.kind, Question.kind,
 * Texts.costName).
 *
 * Runtime names stay only in the files below, for logs, reports and texts. Using one anywhere else fails here: write
 * the name out, or, for a log or a text, add the file with the reason.
 */
class RuntimeNamesTest {
    private val allowed = mapOf(
        "arcanum-engine/src/commonMain/kotlin/arcanum/engine/decision/Texts.kt" to "Texts.className: texts, drops outer names",
        "arcanum-engine/src/commonMain/kotlin/arcanum/engine/decision/AnswerCheck.kt" to "an answer's kind in a refusal message",
        "arcanum-engine/src/commonMain/kotlin/arcanum/engine/Perform.kt" to "an exception's class in the engine trace",
        "arcanum-engine/src/commonMain/kotlin/arcanum/engine/EngineTrace.kt" to "a continuation's kind in the engine trace",
        "arcanum-parser/src/commonMain/kotlin/arcanum/parser/OracleParser.kt" to "an error message",
        "protocol/src/commonMain/kotlin/org/asyncttwiz/protocol/CrashReports.kt" to "latency labels, drop outer names",
        "protocol/src/commonMain/kotlin/org/asyncttwiz/protocol/game/GameDevice.kt" to "the device's debug memory",
        "protocol/src/commonMain/kotlin/org/asyncttwiz/protocol/game/GameCards.kt" to "a parse problem's text",
        "automatum/src/commonMain/kotlin/org/asyncttwiz/automatum/CombatFlow.kt" to "cost chip texts, drop outer names",
    )

    // Problem report N7FU6Y: no new runtime class names in shared code outside logs and texts
    @Test
    fun runtimeClassNamesStayInLogsAndTexts() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val shared = listOf("arcanum-api", "arcanum-engine", "arcanum-parser", "core", "protocol", "automatum")
        val name = Regex("""\bsimpleName\b|\bqualifiedName\b|\bjavaClass\b|::class\.java\.name""")
        val using = shared.flatMap { module ->
            File(root, module).resolve("src").listFiles().orEmpty()
                .filter { it.name.endsWith("Main") }
                .flatMap { set -> set.walk().filter { it.isFile && it.extension == "kt" }.toList() }
        }.filter { file -> file.readLines().any { name.containsMatchIn(it) } }
            .map { it.relativeTo(root).invariantSeparatorsPath }.sorted()
        assertEquals(allowed.keys.sorted(), using, "files that use a class's runtime name")
    }
}
