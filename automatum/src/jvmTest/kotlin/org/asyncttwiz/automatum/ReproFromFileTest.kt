package org.asyncttwiz.automatum

import java.io.File
import kotlin.test.Test

/**
 * I-D43: replays a saved report bundle when `-Pautomatum.bundle=<file>` is given (does nothing otherwise): prints the
 * Automatum log, then each recorded engine call step by step and whether the latest board still rebuilds. A starting
 * point for a test reproducing the report ([ReproBundles]).
 */
class ReproFromFileTest {
    @Test
    fun replaysTheGivenBundle() {
        val path = System.getProperty("automatum.bundle")?.takeIf { it.isNotBlank() } ?: return
        val bundle = ReproBundles.load(File(path))
        println("Game ${bundle.game}, viewer ${bundle.viewer ?: "full information"}, ${bundle.definitions.size} definitions")
        bundle.entries.forEach { println("  ${it.at} ${it.line()}") }
        for (call in bundle.calls) {
            println("Call ${call.call} (${call.steps.size} steps):")
            for (step in ReproBundles.replay(call).steps) println("  ${step.step} -> " + (step.refused?.let { "refused: ${it.message}" }
                ?: step.outcome?.let { "${it.changes.size} changes, waits on ${it.pending?.decision?.let { d -> d::class.simpleName }}" } ?: "host change"))
        }
        bundle.engineError?.let { println("The board couldn't be built on the device: $it") }
        if (bundle.play != null) println("Rebuilt now: " + runCatching { ReproBundles.rebuild(bundle) }.fold(
            { if (it == bundle.engine) "the same engine state" else "a different engine state than the device's" },
            { "fails: ${it::class.simpleName}: ${it.message}" }))
    }
}
