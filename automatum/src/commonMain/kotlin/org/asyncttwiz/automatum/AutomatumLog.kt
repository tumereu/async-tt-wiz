@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class, kotlin.time.ExperimentalTime::class)

package org.asyncttwiz.automatum

import kotlin.concurrent.atomics.AtomicReference
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What Automatum did lately, per game (integration spec I-D43): a bounded in-memory log of flows started and ended,
 * early answers (pre-events), refusals and mismatches with the server. A problem or crash report carries [bundle]
 * (the game's own state and events go with it separately, [org.asyncttwiz.protocol.game.GameRepro]).
 *
 * Kept for the [GAMES] latest games and [ENTRIES] entries each; safe to use from several threads. Nothing is serialized
 * until a report asks for it.
 */
object AutomatumLog {
    const val ENTRIES = 400
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

    /** Everything kept for [game] (the latest game by default) as one bundle; null when nothing was recorded. */
    fun bundle(game: String? = latestGame()): ReproBundle? {
        val log = game?.let(::find) ?: return null
        return ReproBundle(game = log.game, entries = log.entries.load())
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

    private class GameLog(val game: String) {
        val entries = AtomicReference<List<AutomatumEntry>>(emptyList())
    }
}

private inline fun <T> AtomicReference<T>.update(change: (T) -> T) {
    while (true) {
        val old = load()
        if (compareAndSet(old, change(old))) return
    }
}

/** One entry of [AutomatumLog]: when ([at], epoch milliseconds), which [game], and what. */
@Serializable
sealed interface AutomatumEntry {
    val at: Long
    val game: String

    /** One line for people (the Android log). */
    fun line(): String

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
}

/**
 * What a report carries of Automatum (I-D43): the log [entries] of [game].
 */
@Serializable
data class ReproBundle(
    val format: Int = FORMAT,
    val game: String,
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
