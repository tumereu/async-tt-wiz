package org.asyncttwiz.android

import org.asyncttwiz.automatum.boundary.ActedWire
import org.asyncttwiz.automatum.boundary.DeviceAnswer
import org.asyncttwiz.automatum.boundary.DeviceCalls
import org.asyncttwiz.automatum.boundary.DeviceMessage
import org.asyncttwiz.automatum.boundary.DeviceRequest
import org.asyncttwiz.automatum.boundary.DeviceResult
import org.asyncttwiz.automatum.boundary.EngineLog
import org.asyncttwiz.automatum.boundary.LooseBoard
import org.asyncttwiz.protocol.EngineSnapshot
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent

/**
 * The page's one channel to the games' devices in the
 * engine's Web Worker ([DeviceHost] there). [post] sends a message to the worker; [received] takes each of its messages
 * ([DeviceMessage.isDevice]) on the page's thread, in order: a game's boards and results go to its [RemoteDevice], the
 * worker's log lines into the page's app log (so problem reports carry them), its lookups to the page's card records.
 */
class DeviceChannel(private val post: (String) -> Unit, val log: EngineLog = EngineBoundary.log) {
    private val devices = HashMap<String, RemoteDevice>()

    /** Opens game [game]'s device in the worker ([EngineGame] in remote mode). */
    internal fun open(game: EngineGame, snapshot: EngineSnapshot): RemoteDevice =
        RemoteDevice(game, this, snapshot).also { devices.put(game.gameId, it)?.closed("opened again") }

    internal fun send(game: String, request: DeviceRequest) = post(DeviceMessage.request(game, request))

    internal fun forget(device: RemoteDevice) { if (devices[device.gameId] === device) devices.remove(device.gameId) }

    /** A message from the worker. */
    fun received(text: String) {
        val start = kotlin.time.TimeSource.Monotonic.markNow()
        val message = try { DeviceMessage.decode(text) } catch (problem: Exception) {
            log.log(true, "Device boundary: an unreadable message from the worker (${problem::class.simpleName}): ${text.take(160)}"); return
        }
        val answer = message.answer ?: return
        if (answer is DeviceAnswer.Log) {
            for (line in answer.lines) when (line.priority) {
                org.asyncttwiz.appui.LogPriority.ERROR -> AppLog.error(line.tag, "[worker] ${line.message}")
                org.asyncttwiz.appui.LogPriority.WARN -> AppLog.warn(line.tag, "[worker] ${line.message}")
                else -> AppLog.info(line.tag, "[worker] ${line.message}")
            }
            return
        }
        val device = devices[message.game] ?: run {
            log.log(false, "Device boundary: ${answer::class.simpleName} for ${message.game.take(8)}, closed on the page: dropped"); return
        }
        device.received(answer, start.elapsedNow(), text.length)
    }

    /** The worker failed: every device's waiting calls end (their callbacks get their refusals). */
    fun lost(why: String) { devices.values.toList().forEach { it.closed("the worker is lost ($why)") } }
}

/** How long (ms) a published board may take to be taken in on the page before the app log says so ([RemoteDevice.received]). */
private const val SLOW_BOARD_MS = 16L

/**
 * Game [gameId]'s device in the worker, as its [EngineGame] on the page sees it: requests out ([ask]), the boards it
 * publishes in ([DeviceAnswer.Published]: decoded with the page's own copy of each board's engine state, [ShownDecoder]) and
 * each request's result to its callback, in the order the requests were made ([DeviceCalls]).
 */
class RemoteDevice internal constructor(val game: EngineGame, private val channel: DeviceChannel, snapshot: EngineSnapshot) {
    val gameId: String get() = game.gameId
    /** The game's codec as the worker's device has it (its card data), from the snapshot it last loaded. */
    var codec: EngineJson = EngineJson(EngineJson.decodeDefinitions(snapshot.definitions))
        private set
    /** A published board's plays the device hadn't worked out when it went out, asked of it by the
     * board's number (it holds the board: nothing else is sent). */
    private val plays = object : DevicePlays {
        override fun ask(board: Long, done: (hand: org.asyncttwiz.automatum.HandPlays?, card: org.asyncttwiz.automatum.CardPlays?) -> Unit) =
            request("plays", { DeviceRequest.Plays(it, board) }) { r ->
                val answer = r as? DeviceResult.Plays
                done(answer?.hand, answer?.card)
            }

        // A board's combat as the table shows it, worked out where the device is
        override fun combat(board: Long, lanes: org.asyncttwiz.protocol.CombatArea?, drawn: Boolean,
            done: (org.asyncttwiz.automatum.CombatShown?) -> Unit) =
            request("combat", { DeviceRequest.Combat(it, board, lanes, drawn) }, { DeviceResult.Combat(null) }) { r ->
                done((r as? DeviceResult.Combat)?.shown)
            }
    }
    private var decoder = ShownDecoder(gameId, codec, plays = plays)
    private val calls = DeviceCalls(channel.log)
    private var closed = false

    /** Asks the device for [make]'s request; [what] names it in the log. [done] gets the result on the page's thread. */
    fun request(what: String, make: (id: Long) -> DeviceRequest, failed: () -> DeviceResult = { DeviceResult.None }, done: (DeviceResult) -> Unit = {}) {
        if (closed) { channel.log.log(true, "Device boundary: $what asked of ${gameId.take(8)} after it closed"); done(failed()); return }
        val id = calls.next()
        calls.ask(id, what, failed, done)
        channel.send(gameId, make(id))
    }

    /** Sends [make]'s request, answered by nothing but the boards it publishes. */
    fun tell(make: (id: Long) -> DeviceRequest) { if (!closed) channel.send(gameId, make(calls.next())) }

    /** The page closes the game: the worker forgets it. */
    fun close() {
        if (closed) return
        channel.send(gameId, DeviceRequest.Close(calls.next()))
        closed("closed on the page")
    }

    internal fun closed(why: String) {
        closed = true
        calls.close(why)
        channel.forget(this)
    }

    /** The worker's [answer] for this game, its message [read] (parsed) in that long from [chars] characters. */
    internal fun received(answer: DeviceAnswer, read: kotlin.time.Duration = kotlin.time.Duration.ZERO, chars: Int = 0) {
        when (answer) {
            is DeviceAnswer.Published -> {
                val start = kotlin.time.TimeSource.Monotonic.markNow()
                answer.definitions?.let { text ->
                    // The device loaded (again): its boards are read anew, with its card data
                    codec = EngineJson(EngineJson.decodeDefinitions(text))
                    decoder = ShownDecoder(gameId, codec, plays = plays)
                }
                val decoded = try { decoder.decode(answer.shown) } catch (problem: Exception) {
                    AppLog.error("engine", "Device boundary: board v${answer.shown.version} of ${gameId.take(8)} couldn't be read", problem)
                    return
                }
                val built = start.elapsedNow()
                game.remotePublished(decoded.shown, decoded.notes, answer.takeBacks, answer.lastTakeBack, answer.rewinds)
                // Web frame rate (owner, 2026-11-15): a board slow to take in on the page says what took the time (its message
                // read, its engine state and values made on the page's copy, its publishing to the table and the game), as the
                // frame times' page work (WEB-057) doesn't
                val took = read + start.elapsedNow()
                if (took.inWholeMilliseconds >= SLOW_BOARD_MS) channel.log.log(false, "Device boundary: board v${answer.shown.version} " +
                    "of ${gameId.take(8)} taken in in ${took.inWholeMilliseconds} ms: its message ($chars chars) read in " +
                    "${read.inWholeMilliseconds} ms, the board made in ${built.inWholeMilliseconds} ms (${answer.shown.events.size} new " +
                    "events), published in ${(start.elapsedNow() - built).inWholeMilliseconds} ms")
            }
            is DeviceAnswer.Done -> calls.done(answer.id, answer.result)
            is DeviceAnswer.Failed -> calls.failed(answer.id, answer.problem)
            DeviceAnswer.LoadFailed -> game.remoteLoadFailed()
            // The table hasn't given the page its card records' lookup yet, or the page has no card records yet: answered once
            // it does (the worker asks once each)
            is DeviceAnswer.TokenArt -> if (tokenArtReady()) tokenArtFound(answer.query) else tokenQueries += answer.query
            is DeviceAnswer.NamedCard -> {
                val found = try { game.namedCardsOnPage.definition(answer.name) } catch (problem: Exception) {
                    AppLog.warn("engine", "Device boundary: the card named ${answer.name} couldn't be looked up: ${problem.message?.take(200)}"); null
                }
                tell { DeviceRequest.NamedCardFound(it, answer.name, found) }
            }
            is DeviceAnswer.Log -> Unit
        }
    }

    /** The worker's token picture lookups waiting for the page's [EngineGame.tokenArt] (SYS-072). */
    private val tokenQueries = mutableListOf<org.asyncttwiz.automatum.TokenArtQuery>()

    /** Whether the page can answer the worker's lookups: its [EngineGame.tokenArt] set, with card records to look in. */
    private fun tokenArtReady() = game.tokenArt !== org.asyncttwiz.automatum.TokenArt.None && game.tokenArt.ready()

    /** The page's [EngineGame.tokenArt] was set, or its card records may have arrived (asked on each tick): the lookups
     * waiting for them are answered. */
    internal fun tokenArtSet() {
        if (tokenQueries.isEmpty() || !tokenArtReady()) return
        val waiting = tokenQueries.toList()
        tokenQueries.clear()
        AppLog.info("engine", "Device boundary: ${waiting.size} token picture lookups of ${gameId.take(8)} answered now the page has its card records")
        waiting.forEach(::tokenArtFound)
    }

    private fun tokenArtFound(query: org.asyncttwiz.automatum.TokenArtQuery) {
        val found = try { game.tokenArt.find(query) } catch (problem: Exception) {
            AppLog.warn("engine", "Device boundary: a token picture couldn't be looked up: ${problem.message?.take(200)}"); null
        }
        tell { DeviceRequest.TokenArtFound(it, query, found) }
    }

    /** [r]'s action result as the device's own ([GameDevice.Acted]). */
    fun acted(r: DeviceResult): GameDevice.Acted = when (val a = (r as? DeviceResult.Acted)?.acted) {
        is ActedWire.Sent -> GameDevice.Acted.Sent(codec.decodeElement(GameEvent.serializer(), a.event))
        ActedWire.Confirmed -> GameDevice.Acted.Confirmed
        ActedWire.Kept -> GameDevice.Acted.Kept
        ActedWire.Made -> GameDevice.Acted.Made
        is ActedWire.Refused -> GameDevice.Acted.Refused(a.reason)
        null -> GameDevice.Acted.Refused("failed")
    }

    /** What an own action answers when it fails or its game closes first. */
    val actedFailed: () -> DeviceResult = { DeviceResult.Acted(ActedWire.Refused("failed")) }

    /** [r]'s action result, or null when the action wasn't open ([DeviceResult.None]). */
    fun actedOrNull(r: DeviceResult): GameDevice.Acted? = if (r is DeviceResult.Acted) acted(r) else null

    /** [r]'s text, or null. */
    fun text(r: DeviceResult): String? = (r as? DeviceResult.Text)?.text

    /** [r]'s actions to send, or none. */
    fun actions(r: DeviceResult): List<org.asyncttwiz.protocol.GameAction> = (r as? DeviceResult.Actions)?.actions.orEmpty()

    /** [r]'s event, or null. */
    fun event(r: DeviceResult): GameEvent? = (r as? DeviceResult.Event)?.event?.let { codec.decodeElement(GameEvent.serializer(), it) }

    /** A board sent outside the published ones, read with the page's copy of the game's card data and events. */
    fun board(loose: LooseBoard?): TableBoard? = loose?.let { decoder.loose(it) }
}
