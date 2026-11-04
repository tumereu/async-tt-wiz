package org.asyncttwiz.automatum

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Code shipped to the phone never calls `removeFirst()` / `removeLast()` (smoke 2026-11-04). On a MutableList these
 * compile to Java 21's `List.removeFirst/removeLast`, which Android has only from API 35: on older phones the call
 * throws NoSuchMethodError (undo never applied in the running app). A grep cannot see the receiver's type, so the
 * calls are out everywhere; write `removeAt(0)` / `removeAt(list.lastIndex)` (fast on ArrayDeque too).
 */
class OldAndroidListCallsTest {
    @Test
    fun shippedCodeAvoidsJava21ListCalls() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val shipped = listOf("arcanum-api", "arcanum-engine", "arcanum-parser", "core", "protocol", "automatum", "client-storage", "androidApp")
        val call = Regex("""\.remove(First|Last)\(\)""")
        val using = shipped.flatMap { module ->
            File(root, module).resolve("src").listFiles().orEmpty()
                .filter { it.name.endsWith("Main") || it.name == "main" }
                .flatMap { set -> set.walk().filter { it.isFile && it.extension == "kt" }.toList() }
        }.flatMap { file ->
            file.readLines().withIndex().filter { call.containsMatchIn(it.value) }
                .map { "${file.relativeTo(root).invariantSeparatorsPath}:${it.index + 1}" }
        }
        assertEquals(emptyList(), using, "removeFirst()/removeLast() calls in shipped code")
    }
}
