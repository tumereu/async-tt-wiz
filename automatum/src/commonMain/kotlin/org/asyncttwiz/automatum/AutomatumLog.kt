@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class, kotlin.time.ExperimentalTime::class)

package org.asyncttwiz.automatum

import arcanum.api.CardDefinition
import arcanum.engine.EngineTrace
import arcanum.engine.TraceEntry
import arcanum.engine.change.Change
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.LoggedChange
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.incrementAndFetch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.asyncttwiz.protocol.PlayState

/**
 * What Automatum did lately, per game (integration spec I-D43): a bounded in-memory log of engine calls (through the
 * engine's [EngineTrace]), batch plans, flows started and ended, early answers (pre-events), the switch, refusals and
 * mismatches with the server, plus the latest engine calls themselves (each one's start state and inputs) and the board
 * they were made on. A problem or crash report carries [bundle], from which a developer rebuilds the engine situation
 * offline and replays it as a test.
 *
 * Kept for the [GAMES] latest games, [ENTRIES] entries and [CALLS] calls each; safe to use from several threads.
 * Nothing is serialized until a report asks for it, so recording costs a few small objects per engine call.
 */
object AutomatumLog {
    const val ENTRIES = 400
    const val CALLS = 6
    const val GAMES = 3

    /** Gets every entry as it is added (the app writes them to the Android log). Must not throw or block. */
    @kotlin.concurrent.Volatile var listener: ((AutomatumEntry) -> Unit)? = null

    /** The JSON of [ReproBundle]s. */
    val json = Json { ignoreUnknownKeys = true }

    private val games = AtomicReference<List<GameLog>>(emptyList())

    /** The current wall-clock time in milliseconds (entries' [AutomatumEntry.at]). */
    fun now(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    /** Adds [entry] to its game's log. */
    fun add(entry: AutomatumEntry) {
        val log = game(entry.game)
        log.entries.update { (it + entry).takeLast(ENTRIES) }
        try { listener?.invoke(entry) } catch (_: Exception) { /* A log listener never breaks the game. */ }
    }

    /** A private or shared flow [event] ("started", "completed", "cancelled", …) of kind [flow] ("cast", "shared", …). */
    fun flow(game: String, flow: String, event: String, detail: String = "") = add(AutomatumEntry.Flow(now(), game, flow, event, detail))

    /** Something Automatum refused or couldn't do, [where] (the flow or step) with the [reason]. */
    fun refusal(game: String, where: String, reason: String) = add(AutomatumEntry.Refusal(now(), game, where, reason.take(MAX_TEXT)))

    /** ATW and the engine (or the server and this client) disagree: [what] and how. */
    fun mismatch(game: String, what: String, detail: String) = add(AutomatumEntry.Mismatch(now(), game, what, detail.take(MAX_TEXT)))

    /** An early answer (pre-event, I-D33) given, withdrawn or used. */
    fun preEvent(game: String, what: String, detail: String = "") = add(AutomatumEntry.PreEvent(now(), game, what, detail.take(MAX_TEXT)))

    /** The entries kept for [game], oldest first. */
    fun entries(game: String): List<AutomatumEntry> = find(game)?.entries?.load().orEmpty()

    /** The game Automatum worked on last, or null. */
    fun latestGame(): String? = games.load().lastOrNull()?.game

    /** Forgets everything (tests). */
    fun clear() = games.store(emptyList())

    /**
     * Everything kept for [game] (the latest game by default) as one bundle, with the engine state of the latest board
     * worked out now; null when nothing was recorded. Never throws for a board the engine can't take (that is said in
     * [ReproBundle.engineError]).
     */
    fun bundle(game: String? = latestGame()): ReproBundle? {
        val log = game?.let(::find) ?: return null
        val context = log.context.load()
        val engine = context?.let { c -> runCatching { EngineState.from(c.play, c.definitions, c.viewer, c.log) } }
        return ReproBundle(
            game = log.game,
            definitions = context?.definitions.orEmpty(),
            play = context?.play,
            viewer = context?.viewer,
            log = context?.log.orEmpty(),
            engine = engine?.getOrNull(),
            engineError = engine?.exceptionOrNull()?.let { "${it::class.simpleName}: ${it.message?.take(MAX_TEXT)}" },
            calls = log.calls.load().map { it.recorded() },
            entries = log.entries.load(),
        )
    }

    // ---- Engine calls ([EngineCall] reports itself)

    /** The board [call] was made on: the report's latest board. */
    internal fun context(play: PlayState, definitions: Map<String, CardDefinition>, viewer: String?, log: List<LoggedChange>) {
        game(play.gameId).context.store(Context(play, definitions, viewer, log))
    }

    /** [call]'s first input: from now on its inputs are kept ([CALLS] latest calls) and traced. [host]: the changes the
     * host made before it. */
    internal fun begin(call: EngineCall, host: List<CallStep>): CallRecording {
        val log = game(call.state.gameId)
        val recording = CallRecording(log.game, log.next.incrementAndFetch(), call.viewer, call.start, host)
        log.calls.update { (it + recording).takeLast(CALLS) }
        return recording
    }

    private fun find(game: String): GameLog? = games.load().lastOrNull { it.game == game }

    private fun game(game: String): GameLog {
        while (true) {
            val all = games.load()
            val found = all.lastOrNull { it.game == game }
            if (found != null && all.last() === found) return found
            val next = (all.filter { it !== found } + (found ?: GameLog(game))).takeLast(GAMES)
            if (games.compareAndSet(all, next)) return next.last()
        }
    }

    private const val MAX_TEXT = 500

    private class Context(val play: PlayState, val definitions: Map<String, CardDefinition>, val viewer: String?,
        val log: List<LoggedChange>)

    private class GameLog(val game: String) {
        val entries = AtomicReference<List<AutomatumEntry>>(emptyList())
        val calls = AtomicReference<List<CallRecording>>(emptyList())
        val context = AtomicReference<Context?>(null)
        val next = AtomicInt(0)
    }
}

private inline fun <T> AtomicReference<T>.update(change: (T) -> T) {
    while (true) {
        val old = load()
        if (compareAndSet(old, change(old))) return
    }
}

/** One engine call being recorded: [start] (never changed) and the steps made on it so far. */
internal class CallRecording(val game: String, val number: Int, val viewer: String?, val start: GameState, host: List<CallStep>) {
    private val steps = AtomicReference(host)

    fun add(step: CallStep) = steps.update { it + step }

    /** Adds each engine call's [TraceEntry] to the game's log. */
    val trace = EngineTrace { entry -> AutomatumLog.add(AutomatumEntry.Engine(AutomatumLog.now(), game, number, entry)) }

    fun recorded() = RecordedCall(number, viewer, start, steps.load())
}

/** One entry of [AutomatumLog]: when ([at], epoch milliseconds), which [game], and what. */
@Serializable
sealed interface AutomatumEntry {
    val at: Long
    val game: String

    /** One line for people (the Android log). */
    fun line(): String

    /** Engine call [call] performed an input ([trace]: the engine's own record of it). */
    @Serializable @SerialName("engine")
    data class Engine(override val at: Long, override val game: String, val call: Int, val trace: TraceEntry) : AutomatumEntry {
        override fun line() = when (trace) {
            is TraceEntry.Performed -> "call $call: ${inputText(trace.input)} -> ${trace.changes} changes, ${trace.rounds} rounds" +
                (trace.pending?.let { ", waits on ${it.decision} for ${it.player.value} (${it.continuation})" } ?: "") +
                (trace.loopStop?.let { ", loop stop $it" } ?: "") + (if (trace.over) ", game over" else "") + ", ${trace.micros} us"
            is TraceEntry.Refused -> "call $call: ${inputText(trace.input)} refused (${trace.error}: ${trace.reason}), ${trace.micros} us"
        }
    }

    /** Engine call [call]'s changes planned as one ATW batch of [actions] members ([changes] engine changes); whether a
     * shared flow was open before and after it. */
    @Serializable @SerialName("batch")
    data class Batch(override val at: Long, override val game: String, val call: Int?, val changes: Int, val actions: Int,
        val flowBefore: Boolean, val flowAfter: Boolean) : AutomatumEntry {
        override fun line() = "batch of call ${call ?: "-"}: $changes changes -> $actions actions" + when {
            !flowBefore && flowAfter -> ", flow opened"
            flowBefore && !flowAfter -> ", flow closed"
            flowAfter -> ", flow goes on"
            else -> ""
        }
    }

    @Serializable @SerialName("flow")
    data class Flow(override val at: Long, override val game: String, val flow: String, val event: String, val detail: String = "") : AutomatumEntry {
        override fun line() = "$flow flow $event${if (detail.isEmpty()) "" else ": $detail"}"
    }

    @Serializable @SerialName("pre")
    data class PreEvent(override val at: Long, override val game: String, val what: String, val detail: String = "") : AutomatumEntry {
        override fun line() = "early answer $what${if (detail.isEmpty()) "" else ": $detail"}"
    }

    @Serializable @SerialName("refusal")
    data class Refusal(override val at: Long, override val game: String, val where: String, val reason: String) : AutomatumEntry {
        override fun line() = "refused in $where: $reason"
    }

    @Serializable @SerialName("mismatch")
    data class Mismatch(override val at: Long, override val game: String, val what: String, val detail: String) : AutomatumEntry {
        override fun line() = "mismatch ($what): $detail"
    }

    companion object {
        private fun inputText(input: Input): String = input::class.simpleName + when (input) {
            is Input.Answer -> " ${input.answer::class.simpleName} to ${input.decision.value}"
            else -> ""
        }
    }
}

/** A step of a recorded engine call: a change the host made itself ([EngineCall] `host`), or an input performed. */
@Serializable
sealed interface CallStep {
    @Serializable @SerialName("host")
    data class Host(val change: Change) : CallStep

    @Serializable @SerialName("input")
    data class Perform(val input: Input) : CallStep
}

/** Engine call [call] as recorded: its [start] state ([viewer]'s view; null: full information) and its [steps]. */
@Serializable
data class RecordedCall(val call: Int, val viewer: String?, val start: GameState, val steps: List<CallStep>)

/**
 * What a report carries to rebuild an Automatum situation offline (I-D43): the game's card [definitions] in use (by
 * ATW definition digest), the latest board [play] with the [viewer] and event [log] its engine state was built with,
 * that [engine] state as built when the report was made ([engineError] when it couldn't be), the latest engine [calls]
 * (each from its start state, so it can be replayed exactly) and the log [entries]. May hold hidden cards (I-D27).
 */
@Serializable
data class ReproBundle(
    val format: Int = FORMAT,
    val game: String,
    val definitions: Map<String, CardDefinition> = emptyMap(),
    val play: PlayState? = null,
    val viewer: String? = null,
    val log: List<LoggedChange> = emptyList(),
    val engine: GameState? = null,
    val engineError: String? = null,
    val calls: List<RecordedCall> = emptyList(),
    val entries: List<AutomatumEntry> = emptyList(),
) {
    fun encode(): String = AutomatumLog.json.encodeToString(serializer(), this)

    companion object {
        const val FORMAT = 1

        /** The first line of a bundle in a report; the rest is its JSON, gzip-compressed and base64-encoded. */
        const val REPORT_HEADER = "atw-automatum-bundle/1 gzip+base64"

        fun decode(text: String): ReproBundle = AutomatumLog.json.decodeFromString(serializer(), text)
    }
}
