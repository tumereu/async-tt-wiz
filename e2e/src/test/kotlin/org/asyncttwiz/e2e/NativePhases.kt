package org.asyncttwiz.e2e

import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/** Checkpoints describe assertions actually reached in this invocation, never seeded app state.
 * A green continuation is still partial evidence; it cannot certify the skipped prefix.
 * ATW_NATIVE_PHASE selects a named phase; supported legacy flags remain compatible,
 * but contradictory flags fail before any Appium session is opened.
 */
internal class NativePhases(directory: Path, journey: String, val start: String, private val phases: List<String>) {
    companion object {
        fun requested(legacy: String): String {
            val requested = System.getenv("ATW_NATIVE_PHASE")?.takeIf { it.isNotEmpty() } ?: return legacy
            require(legacy == "setup" || legacy == requested) { "Conflicting native continuation phase flags" }
            return requested
        }
    }
    private val completed = mutableListOf<String>()
    private val file: Path
    val fullJourney: Boolean get() = start == phases.first()

    init {
        require(phases.isNotEmpty() && phases.distinct() == phases && start in phases) { "Unknown $journey phase: $start" }
        require(journey.matches(Regex("[a-z-]+")))
        file = directory.resolve("$journey-phases-${UUID.randomUUID()}.json")
        write("running")
    }

    fun includes(phase: String): Boolean {
        require(phase in phases)
        return phases.indexOf(phase) >= phases.indexOf(start)
    }

    fun completed(phase: String) {
        val expected = phases.drop(phases.indexOf(start)).getOrNull(completed.size)
        check(phase == expected) { "Expected native checkpoint $expected, not $phase" }
        completed += phase
        write("running")
    }

    fun finish(): Map<String, Any> {
        check(completed == phases.drop(phases.indexOf(start))) { "Unfinished native assertions: $completed" }
        write(if (fullJourney) "passed" else "continuation-passed")
        return mapOf("fullJourney" to fullJourney, "startPhase" to start, "assertedPhases" to completed.toList(),
            "skippedPhases" to phases.take(phases.indexOf(start)), "phaseEvidence" to file.toString())
    }

    private fun write(status: String) {
        Files.writeString(file, GsonBuilder().setPrettyPrinting().create().toJson(mapOf(
            "status" to status, "fullJourney" to fullJourney, "startPhase" to start,
            "assertedPhases" to completed.toList(), "skippedPhases" to phases.take(phases.indexOf(start)),
            "updatedAt" to Instant.now().toString(),
            "scope" to "Only assertions reached in this invocation; no aggregation of earlier red runs")) + "\n")
    }
}
