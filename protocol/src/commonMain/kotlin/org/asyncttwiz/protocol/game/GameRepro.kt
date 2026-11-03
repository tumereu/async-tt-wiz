package org.asyncttwiz.protocol.game

import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A device's reproduction bundle on the engine-state model (I-D43, I-D54 §4: the engine inputs are the reproduction):
 * the game's card [definitions], the state at [lineStart] ([base], [EngineJson.encodeModel]; null when the device
 * couldn't reach it), the main line's [events] after it as the device holds them, its own [pending] events, its
 * [localHead], the local heads, line counter and private settings it knew, and the [log] of what it sent, received and
 * told the player. A developer replays it with [check]: every event is made again from its inputs on the state before
 * it, as the server makes it.
 */
@Serializable
data class GameRepro(
    val format: Int = FORMAT,
    val me: String,
    val definitions: String,
    val lineStart: String,
    val base: String? = null,
    val events: List<String> = emptyList(),
    val pending: List<String> = emptyList(),
    val localHead: String,
    val localHeads: Map<String, String> = emptyMap(),
    val lines: Int = 0,
    val settings: PlaySettings = PlaySettings(),
    val log: List<String> = emptyList(),
) {
    fun encode(): String = JSON.encodeToString(serializer(), this)

    /** One event [check] made differently than recorded, or couldn't make ([reason]). */
    data class Mismatch(val event: String, val reason: String)

    /**
     * Replays the bundle: each recorded event (then each pending one) with inputs is made again from [Submission.of] it
     * on the state before it ([Authority.make], the server's gate) and compared with the recorded one; events without
     * inputs (a rewind's line start) and the server's own game start are applied as recorded. Empty when everything is
     * made again exactly.
     */
    fun check(): List<Mismatch> {
        val codec = EngineJson(EngineJson.decodeDefinitions(definitions))
        val model = codec.decodeModel(base ?: return listOf(Mismatch(lineStart, "no base state")))
        val out = mutableListOf<Mismatch>()
        for (event in (events + pending).map(codec::decodeEvent)) {
            val inputs = event.engine?.steps.orEmpty().mapNotNull { it.input }
            if (inputs.isNotEmpty() && inputs.none { it is Input.StartGame }) {
                val made = try {
                    Authority.make(model.copy(), Submission.of(event), PlayerId(event.author), Authority.Rules(nextLine = event.startsLine))
                } catch (e: Refusal) { out += Mismatch(event.id, e.reason); null }
                if (made != null && made != event) out += Mismatch(event.id, "made differently")
            }
            try { model.apply(event) } catch (e: RuntimeException) { return out + Mismatch(event.id, "doesn't apply: ${e.message}") }
        }
        return out
    }

    companion object {
        const val FORMAT = 1
        /** The first line of a bundle in a problem report; the rest is its JSON, gzip-compressed and base64-encoded. */
        const val REPORT_HEADER = "atw-engine-bundle/1 gzip+base64"
        private val JSON = Json { ignoreUnknownKeys = true }

        fun decode(text: String): GameRepro = JSON.decodeFromString(serializer(), text)
    }
}
