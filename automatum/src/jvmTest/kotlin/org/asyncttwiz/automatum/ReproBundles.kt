package org.asyncttwiz.automatum

import arcanum.engine.Engine
import arcanum.engine.Outcome
import arcanum.engine.state.GameState
import java.io.File
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Turns an Automatum report into a test (I-D43). A problem or crash report's Automatum field (the server's `automatum`
 * column) holds a [ReproBundle] as [ReproBundle.REPORT_HEADER] and gzip+base64, the way the app writes it; save it to
 * a file and:
 *
 * ```kotlin
 * val bundle = ReproBundles.load(File("report.txt"))
 * val replay = ReproBundles.replay(bundle.calls.last())   // the latest engine call, input by input
 * // assert what should have happened on replay.state, or on bundle.engine / ReproBundles.rebuild(bundle)
 * ```
 *
 * `./scripts/gradle.sh :automatum:jvmTest --tests '*ReproFromFileTest' -Pautomatum.bundle=/path/report.txt` prints the
 * log and replays every call of a saved bundle.
 */
object ReproBundles {
    /** A bundle from a report's text (header and gzip+base64) or plain bundle JSON. */
    fun load(text: String): ReproBundle {
        val trimmed = text.trim()
        if (trimmed.startsWith("{")) return ReproBundle.decode(trimmed)
        val body = trimmed.removePrefix(ReproBundle.REPORT_HEADER).trim()
        val json = GZIPInputStream(Base64.getMimeDecoder().decode(body).inputStream()).use { it.readBytes() }.decodeToString()
        return ReproBundle.decode(json)
    }

    fun load(file: File): ReproBundle = load(file.readText())

    /** [bundle] as the app puts it in a report (the same format, for tests). */
    fun reportText(bundle: ReproBundle): String {
        val bytes = java.io.ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(bundle.encode().encodeToByteArray()) }
        return ReproBundle.REPORT_HEADER + "\n" + Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    /** One replayed step: its [step], and the engine's [outcome] or why it was [refused]. */
    class Replayed(val step: CallStep, val outcome: Outcome?, val refused: Throwable?)

    /** A replayed call: the [state] after its steps and each step's result. */
    class Replay(val state: GameState, val steps: List<Replayed>)

    /** Replays [call] from its start state: the host's changes applied, each input performed and its changes applied
     * (a refused input changes nothing, as it did on the device). */
    fun replay(call: RecordedCall): Replay {
        val state = call.start.deepCopy()
        val steps = call.steps.map { step ->
            when (step) {
                is CallStep.Host -> { state.apply(step.change); Replayed(step, null, null) }
                is CallStep.Perform -> try {
                    Engine.perform(state, step.input).also { o -> o.changes.forEach(state::apply) }.let { Replayed(step, it, null) }
                } catch (refused: IllegalArgumentException) {
                    Replayed(step, null, refused)
                } catch (refused: IllegalStateException) {
                    Replayed(step, null, refused)
                }
            }
        }
        return Replay(state, steps)
    }

    /** The engine state of the bundle's latest board built again from ATW's state (the mapping, [EngineState.from]). */
    fun rebuild(bundle: ReproBundle): GameState =
        EngineState.from(requireNotNull(bundle.play) { "The bundle has no board" }, bundle.definitions, bundle.viewer, bundle.log)
}
