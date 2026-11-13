@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package org.asyncttwiz.automatum.boundary

import kotlin.time.TimeSource

/** Where the boundary's log lines go (the app log, which problem reports carry). Must not throw. */
fun interface EngineLog {
    fun log(warn: Boolean, text: String)
}

/**
 * How the engine side gets a board it doesn't hold (a Web Worker: [MessageEngineChannel]): the board as a snapshot
 * ([snapshot]: the game's card definitions and its model in the engine's stored format), or, cheaper, the events from a
 * board of the same game the engine side holds ([since]).
 */
interface BoardTransfer {
    /** The board's card definitions and model ([org.asyncttwiz.protocol.game.EngineJson]). */
    fun snapshot(): Snapshot

    /** The events from the game's board [from] to this one, null when not known (a snapshot is sent). */
    fun since(from: Long): Delta? = null

    class Snapshot(val definitions: String, val model: String)

    /** Events in the engine's stored format: [revert]ed first (the latest first), then [apply]ed in order. */
    class Delta(val apply: List<String>, val revert: List<String> = emptyList())
}

/**
 * The page's side of the boundary (docs/specs/web-client.md §1 item 3): asks the engine side for planning work on a board
 * ([EngineRequest.HandPlaysOf], [EngineRequest.CardPlaysOf]) and gets its answer. [InProcessEngineChannel] answers on the
 * caller's thread with the board it is given (Android's engine thread); [MessageEngineChannel] sends JSON strings to a Web
 * Worker, with the board's transfer, and answers later on the page's thread.
 */
interface EngineChannel {
    /** Makes a request's number. */
    fun next(): Long

    /**
     * Asks [request] about [board] ([transfer]: how the engine side gets it when it doesn't hold it; the in-process path
     * never needs it). [done] gets the answer, unless it is dropped as stale ([EngineRequests]).
     */
    fun ask(request: EngineRequest, board: PlanningBoard?, transfer: BoardTransfer?, done: (EngineAnswer) -> Unit)

    /** The page shows [board] of [game] now: answers about older boards of it are stale. */
    fun moved(game: String, board: Long)

    /** The page is done with [game]: its waiting answers are dropped and the engine side forgets its board. */
    fun close(game: String)
}

/**
 * The in-process path (Android): the request is answered at once on the caller's thread (the engine thread) on the board it
 * is given, the same objects handed over, nothing serialized; the answer is never stale (it is about the board asked with).
 * Logs failures and work that took [slowMs] or longer.
 */
class InProcessEngineChannel(private val log: EngineLog, private val slowMs: Int = EngineRequests.SLOW_MS) : EngineChannel {
    private val seq = kotlin.concurrent.atomics.AtomicLong(0L)
    override fun next(): Long = seq.addAndFetch(1L)
    override fun ask(request: EngineRequest, board: PlanningBoard?, transfer: BoardTransfer?, done: (EngineAnswer) -> Unit) {
        done(call(request, requireNotNull(board) { "The in-process path plans on the board it is given" }))
    }

    /** [ask] returning the answer. */
    fun call(request: EngineRequest, board: PlanningBoard): EngineAnswer = EnginePlanner.plan(request, board).also { answer ->
        if (answer is EngineAnswer.Failed) log.log(true, "Engine boundary: ${describe(request)} failed in-process: ${answer.kind}" +
            (answer.message?.let { ": $it" } ?: ""))
        else if (answer.micros >= slowMs * 1000L) log.log(false, "Engine boundary: ${describe(request)} slow in-process: ${answer.micros / 1000} ms")
    }

    override fun moved(game: String, board: Long) = Unit
    override fun close(game: String) = Unit
}

/** A request in the log's words: its kind, number, game and board. */
internal fun describe(request: EngineRequest): String {
    val kind = when (request) {
        is EngineRequest.Load -> "load"
        is EngineRequest.Advance -> "advance from ${request.from} (${request.revert.size} back, ${request.apply.size} on)"
        is EngineRequest.Close -> "close"
        is EngineRequest.HandPlaysOf -> "hand plays for ${request.viewer.take(8)}"
        is EngineRequest.CardPlaysOf -> "card plays for ${request.viewer.take(8)}"
    }
    return "#${request.seq} $kind, game ${request.game.take(8)} board ${request.board}"
}

/**
 * The boundary across a Web Worker (or any message pipe): requests [post]ed as JSON strings, answers [received] as JSON
 * strings, both on the page's one thread. A board the engine side doesn't hold goes before the request that needs it: the
 * events from the board it holds when [BoardTransfer.since] knows them, else a snapshot. An answer saying the board is
 * missing (the worker restarted, a transfer failed or didn't fit) sends the snapshot and asks again, once.
 * Staleness, timings and the log are [EngineRequests]'.
 */
class MessageEngineChannel(private val post: (String) -> Unit, log: EngineLog,
    now: () -> Double = TimeSource.Monotonic.markNow().let { epoch -> { epoch.elapsedNow().inWholeMicroseconds / 1000.0 } }) : EngineChannel {
    val requests = EngineRequests(log, now)
    private val log = log

    /** A planning request waiting for its answer, with what resends its board. */
    private class Waiting(val request: EngineRequest, val transfer: BoardTransfer?, val done: (EngineAnswer) -> Unit, var retried: Boolean = false)
    private val waiting = HashMap<Long, Waiting>()

    /** The board of each game the engine side holds, as far as this page knows (set as a transfer is sent). */
    private val held = HashMap<String, Long>()

    override fun next(): Long = requests.next()

    override fun ask(request: EngineRequest, board: PlanningBoard?, transfer: BoardTransfer?, done: (EngineAnswer) -> Unit) {
        requests.moved(request.game, request.board)
        if (held[request.game] != request.board && transfer != null) send(transferOf(request.game, request.board, transfer))
        waiting[request.seq] = Waiting(request, transfer, done)
        send(request)
    }

    override fun moved(game: String, board: Long) = requests.moved(game, board)

    override fun close(game: String) {
        waiting.values.removeAll { it.request.game == game }
        held.remove(game)
        send(EngineRequest.Close(next(), game))
        requests.closed(game)
    }

    /** An answer from the engine side (its JSON string). */
    fun received(text: String) {
        val answer = try { EngineMessages.decodeAnswer(text) } catch (problem: Exception) {
            log.log(true, "Engine boundary: an answer couldn't be read (${problem::class.simpleName}): ${text.take(200)}")
            return
        }
        val taken = requests.answered(answer)
        when (answer) {
            is EngineAnswer.Held -> return
            is EngineAnswer.Failed, is EngineAnswer.Missing -> {
                // A board transfer that failed or didn't fit: the engine side holds no board of the game now
                val wait = waiting[answer.seq]
                if (wait == null) { if (held[answer.game] == answer.board) held.remove(answer.game); return }
                if (answer is EngineAnswer.Missing && !wait.retried && wait.transfer != null && taken) {
                    // Sends the board whole and asks again (the request keeps its number)
                    wait.retried = true
                    send(load(answer.game, answer.board, wait.transfer))
                    requests.resent(wait.request)
                    send(wait.request)
                    return
                }
                waiting.remove(answer.seq)
                if (taken) wait.done(answer)
            }
            else -> {
                val wait = waiting.remove(answer.seq) ?: return
                if (taken) wait.done(answer)
            }
        }
    }

    private fun transferOf(game: String, board: Long, transfer: BoardTransfer): EngineRequest {
        val from = held[game]
        val delta = from?.let { runCatching { transfer.since(it) }.getOrNull() }
        return if (from != null && delta != null) EngineRequest.Advance(next(), game, board, from, delta.apply, delta.revert)
        else load(game, board, transfer)
    }

    private fun load(game: String, board: Long, transfer: BoardTransfer): EngineRequest.Load {
        val snapshot = transfer.snapshot()
        return EngineRequest.Load(next(), game, board, snapshot.definitions, snapshot.model)
    }

    private fun send(request: EngineRequest) {
        if (request is EngineRequest.Load || request is EngineRequest.Advance) held[request.game] = request.board
        val text = EngineMessages.encode(request)
        requests.sent(request, text.length)
        post(text)
    }
}

/**
 * The page's ledger of the boundary's requests: their numbers, which answers are stale, the timings and the log.
 *
 * Stale answers are dropped as Android drops its background results today: one about a board older than the newest the
 * page shows of its game (the engine thread's board moved on: an own action's [stale] refusal, the toolkit's plays asked
 * for a board since replaced), and one whose request a newer one of the same kind on the same game replaced (a cast step's
 * result that something replaced meanwhile). A request asked again ([resent]) keeps its number.
 *
 * The log (the problem report's app log; volume kept small): every snapshot sent and every answer dropped, missing or
 * failed, slow answers ([SLOW_MS]), and per game a summary of the answers every [SUMMARY_EVERY] of them and when it closes.
 */
class EngineRequests(private val log: EngineLog, private val now: () -> Double) {
    private var seq = 0L
    private class Sent(val request: EngineRequest, val at: Double, val chars: Int)
    private val sent = HashMap<Long, Sent>()
    /** The newest board of each game the page shows. */
    private val newest = HashMap<String, Long>()
    /** The newest planning request of each kind on each game. */
    private val latest = HashMap<Pair<String, String>, Long>()
    private class Summary(var answers: Int = 0, var dropped: Int = 0, var slow: Int = 0, var maxMs: Double = 0.0, var totalMs: Double = 0.0,
        var transfers: Int = 0, var transferChars: Long = 0)
    private val summaries = HashMap<String, Summary>()

    fun next(): Long = ++seq

    /** Answers still awaited (tests). */
    val awaited: Int get() = sent.size

    /** The page shows [board] of [game] now (a board number never goes back). */
    fun moved(game: String, board: Long) {
        if ((newest[game] ?: Long.MIN_VALUE) < board) newest[game] = board
    }

    /** [request] went out as [chars] characters. */
    fun sent(request: EngineRequest, chars: Int) {
        sent[request.seq] = Sent(request, now(), chars)
        kindOf(request)?.let { latest[request.game to it] = request.seq }
        if (request is EngineRequest.Load || request is EngineRequest.Advance) summary(request.game).let { it.transfers++; it.transferChars += chars }
        if (request is EngineRequest.Load) log.log(false, "Engine boundary: ${describe(request)} sent, $chars chars")
    }

    /** [request] is going out again (keeping its number) after its board was missing and sent whole. */
    fun resent(request: EngineRequest) {
        log.log(false, "Engine boundary: ${describe(request)} asked again after its board was sent whole")
    }

    /**
     * An answer arrived: whether the page takes it (false: stale or unknown, dropped and logged). Logs what went wrong,
     * slow answers and the summaries.
     */
    fun answered(answer: EngineAnswer): Boolean {
        val out = sent.remove(answer.seq)
        if (out == null) {
            log.log(false, "Engine boundary: answer #${answer.seq} on game ${answer.game.take(8)} board ${answer.board} dropped: nothing waits for it")
            return false
        }
        val request = out.request
        val ms = now() - out.at
        val workMs = answer.micros / 1000.0
        val summary = summary(request.game)
        val stale = staleness(request)
        if (stale != null) {
            summary.dropped++
            log.log(false, "Engine boundary: ${describe(request)} answer dropped after ${ms.shown()} ms: $stale")
            return false
        }
        when (answer) {
            is EngineAnswer.Failed -> log.log(true, "Engine boundary: ${describe(request)} failed after ${ms.shown()} ms: ${answer.kind}" +
                (answer.message?.let { ": $it" } ?: ""))
            is EngineAnswer.Missing -> log.log(false, "Engine boundary: ${describe(request)}: board missing (held ${answer.held ?: "none"})")
            is EngineAnswer.Held -> if (request is EngineRequest.Load) log.log(false,
                "Engine boundary: ${describe(request)} held after ${ms.shown()} ms (worker ${workMs.shown()} ms)")
            else -> Unit
        }
        if (kindOf(request) != null) {
            summary.answers++; summary.totalMs += ms; if (ms > summary.maxMs) summary.maxMs = ms
            if (ms >= SLOW_MS) {
                summary.slow++
                log.log(false, "Engine boundary: ${describe(request)} slow: ${ms.shown()} ms round trip, worker ${workMs.shown()} ms")
            }
            if (summary.answers % SUMMARY_EVERY == 0) log.log(false, summarized(request.game, summary))
        }
        return true
    }

    /** The page is done with [game]: its summary is logged, its waiting requests forgotten. */
    fun closed(game: String) {
        summaries.remove(game)?.let { log.log(false, summarized(game, it) + " (closed)") }
        sent.values.removeAll { it.request.game == game && it.request !is EngineRequest.Close }
        newest.remove(game)
        latest.keys.removeAll { it.first == game }
    }

    /** Why an answer to [request] is stale now, or null when it isn't. */
    private fun staleness(request: EngineRequest): String? {
        val kind = kindOf(request) ?: return null
        val shown = newest[request.game]
        if (shown != null && request.board < shown) return "the page shows board $shown now"
        val newer = latest[request.game to kind]
        if (newer != null && newer != request.seq) return "replaced by #$newer"
        return null
    }

    private fun summary(game: String) = summaries.getOrPut(game) { Summary() }

    private fun summarized(game: String, s: Summary) = "Engine boundary: game ${game.take(8)}: ${s.answers} answers " +
        "(mean ${(if (s.answers == 0) 0.0 else s.totalMs / s.answers).shown()} ms, max ${s.maxMs.shown()} ms, ${s.slow} slow), " +
        "${s.dropped} dropped, ${s.transfers} board transfers (${s.transferChars} chars)"

    private fun Double.shown(): String = ((this * 10).toLong() / 10.0).toString()

    companion object {
        /** A round trip this long or longer is logged. */
        const val SLOW_MS = 100
        /** A game's answers are summarized in the log after every this many. */
        const val SUMMARY_EVERY = 200

        /** The kind of a planning request (staleness and timings are per kind), null for a board transfer or close. */
        internal fun kindOf(request: EngineRequest): String? = when (request) {
            is EngineRequest.HandPlaysOf -> "hand-plays:" + request.viewer
            is EngineRequest.CardPlaysOf -> "card-plays:" + request.viewer
            is EngineRequest.Load, is EngineRequest.Advance, is EngineRequest.Close -> null
        }
    }
}
