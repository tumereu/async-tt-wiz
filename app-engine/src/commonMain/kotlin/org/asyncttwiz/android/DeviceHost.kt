package org.asyncttwiz.android

import arcanum.api.CardDefinition
import org.asyncttwiz.automatum.TokenArt
import org.asyncttwiz.automatum.TokenArtQuery
import org.asyncttwiz.automatum.boundary.ActedWire
import org.asyncttwiz.automatum.boundary.DeviceAnswer
import org.asyncttwiz.automatum.boundary.DeviceMessage
import org.asyncttwiz.automatum.boundary.DeviceRequest
import org.asyncttwiz.automatum.boundary.DeviceResult
import org.asyncttwiz.automatum.boundary.LogLine
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.NamedCards

/**
 * Web client step 4c part 3 (docs/specs/web-client.md §1 item 3): the engine side of the games' devices in the browser's
 * engine Web Worker. Each game is an [EngineGame] on the worker's one thread ([HostWorker]: its work and its "main thread"
 * both inline, in order); each board it publishes goes to the page as a [DeviceAnswer.Published] ([ShownEncoder]), each
 * request's result as a [DeviceAnswer.Done], in the order they happen, so the page sees an own action's early board before
 * its result and its later boards after (SYS-025). [post] sends one message to the page.
 *
 * An own action is checked against the board the player acted on (the page's version, [DeviceRequest.Own.seen]), as
 * Android checks it against the one its main thread shows (SYS-011). What the device needs from the page's card records
 * (token pictures, named cards) is asked of the page and kept: until the answer comes a token keeps the plain frame and a
 * named card's decision waits, then the board is published again or the device steps on.
 */
class DeviceHost(private val post: (String) -> Unit, private val now: () -> Long = { org.asyncttwiz.appui.nanoTime() / 1_000_000 },
    /** The ids a game's device gives its own events (tests: the same as a device they compare with). */
    private val ids: (game: String) -> () -> String = { { org.asyncttwiz.appui.randomUuid() } }) {
    private val games = HashMap<String, Hosted>()
    /** The worker's log lines not sent yet ([AppLog.forward] fills it); they go before the next message. */
    private val logs = ArrayList<LogLine>()

    /** Installs the worker's log forwarding: every app log line made here reaches the page's app log. */
    fun forwardLogs() { AppLog.forward = { priority, tag, message -> logs += LogLine(priority, tag, message) } }

    /** One message from the page ([DeviceMessage.isDevice]). */
    fun handle(text: String) {
        val message = try { DeviceMessage.decode(text) } catch (problem: Exception) {
            AppLog.warn("engine", "Device boundary: an unreadable request (${problem::class.simpleName}): ${text.take(160)}")
            flushLogs(); return
        }
        val request = message.request ?: return
        try { handle(message.game, request) } catch (problem: Throwable) {
            AppLog.error("engine", "Device boundary: request ${request::class.simpleName} #${request.id} of ${message.game.take(8)} failed", problem)
            send(message.game, DeviceAnswer.Failed(request.id, "${problem::class.simpleName}: ${problem.message?.take(300)}"))
        }
        flushLogs()
    }

    private fun send(game: String, answer: DeviceAnswer) {
        flushLogs()
        post(DeviceMessage.answer(game, answer))
    }

    private fun flushLogs() {
        if (logs.isEmpty()) return
        val lines = logs.toList()
        logs.clear()
        post(DeviceMessage.answer("", DeviceAnswer.Log(lines)))
    }

    private fun handle(gameId: String, r: DeviceRequest) {
        if (r is DeviceRequest.Open) {
            games.remove(gameId)?.close()
            games[gameId] = Hosted(gameId, r, this)
            return
        }
        val hosted = games[gameId] ?: run {
            AppLog.warn("engine", "Device boundary: ${r::class.simpleName} #${r.id} for ${gameId.take(8)}, which isn't open here")
            send(gameId, DeviceAnswer.Failed(r.id, "not open"))
            return
        }
        if (r is DeviceRequest.Close) { games.remove(gameId); hosted.close(); return }
        hosted.handle(r)
    }

    /** One game's device on the worker. */
    private class Hosted(val gameId: String, open: DeviceRequest.Open, val host: DeviceHost) {
        /** The boards published lately by version: an own action is checked against the one the page showed. */
        private val recent = LinkedHashMap<Long, EngineGame.Shown>()
        private val tokenPictures = HashMap<TokenArtQuery, String?>()
        private val tokensAsked = HashSet<TokenArtQuery>()
        private val named = HashMap<String, CardDefinition?>()
        private val namesAsked = HashSet<String>()
        private var encoder: ShownEncoder? = null
        private var encoderCodec: EngineJson? = null
        private var building = true
        private var closed = false
        private val game: EngineGame

        init {
            AppLog.info("engine", "Device boundary: game ${gameId.take(8)} opens in the worker for ${open.me.take(8)}")
            game = EngineGame(gameId, open.me, open.base, open.snapshot, ids = host.ids(gameId), worker = HostWorker, onShown = { if (!building) published() },
                namedCards = NamedCards { name -> namedCard(name) })
            game.showPlays(open.showPlays)
            game.tokenArt = tokenArt()
            building = false
            published()
        }

        fun close() { closed = true; AppLog.info("engine", "Device boundary: game ${gameId.take(8)} closed in the worker") }

        /** The board just published (or the load failing), to the page with the notes published with it. */
        private fun published() {
            if (closed) return
            if (game.loadFailed) { host.send(gameId, DeviceAnswer.LoadFailed); return }
            val shown = game.shown ?: return
            val codec = game.codec
            var fresh = false
            val enc = encoder?.takeIf { encoderCodec === codec } ?: ShownEncoder(gameId, codec).also {
                encoder = it; encoderCodec = codec; fresh = true
            }
            val notes = game.notes.toList()
            game.notes.clear()
            recent[shown.version] = shown
            while (recent.size > ShownEncoder.KEEP) recent.remove(recent.keys.first())
            host.send(gameId, DeviceAnswer.Published(enc.wire(shown, notes), game.takeBacks, game.lastTakeBack,
                definitions = if (fresh) codec.encodeDefinitions() else null))
        }

        /** Notes made with no board of their own (an own action's refusal after its early board): sent with the last board
         * again, so the page reads them as a board arrives. */
        private fun leftNotes() { if (game.notes.isNotEmpty()) published() }

        private fun done(id: Long, result: DeviceResult) = host.send(gameId, DeviceAnswer.Done(id, result))

        private fun acted(a: GameDevice.Acted): DeviceResult = DeviceResult.Acted(when (a) {
            is GameDevice.Acted.Sent -> ActedWire.Sent(game.codec.encodeElement(GameEvent.serializer(), a.event))
            GameDevice.Acted.Confirmed -> ActedWire.Confirmed
            GameDevice.Acted.Kept -> ActedWire.Kept
            GameDevice.Acted.Made -> ActedWire.Made
            is GameDevice.Acted.Refused -> ActedWire.Refused(a.reason)
        })

        /** An own action, checked against the board the page showed ([DeviceRequest.Own.seen]). */
        private fun own(r: DeviceRequest.Own, action: () -> Unit) {
            val seen = r.seen?.let { v -> recent[v] ?: recent.values.firstOrNull().also {
                AppLog.info("engine", "Device boundary: ${r::class.simpleName} on board v$v, older than the worker keeps: checked on v${it?.version}")
            } }
            game.hostSeen = seen
            try { action() } finally { game.hostSeen = null }
            leftNotes()
        }

        fun handle(r: DeviceRequest) {
            when (r) {
                is DeviceRequest.Open, is DeviceRequest.Close -> Unit
                is DeviceRequest.Load -> game.load(r.snapshot, r.base, r.live)
                is DeviceRequest.Revision -> game.revision(r.message)
                is DeviceRequest.HoldEnd -> { var shownEnd = true; game.holdEnd(r.message) { shownEnd = false }; done(r.id, DeviceResult.Flag(shownEnd)) }
                is DeviceRequest.Refused -> game.refused(r.action, r.reason)
                is DeviceRequest.Reveals -> game.assumeReveals(r.value)
                is DeviceRequest.Tick -> game.tick(r.now)
                is DeviceRequest.Take -> done(r.id, DeviceResult.Actions(game.take(r.now)))
                is DeviceRequest.Away -> game.away()
                is DeviceRequest.Returned -> game.returned()
                is DeviceRequest.Pass -> own(r) { game.pass { done(r.id, acted(it)) } }
                is DeviceRequest.HoldResolve -> own(r) { game.holdResolve { done(r.id, acted(it)) } }
                is DeviceRequest.HoldAssume -> own(r) { game.holdAssume { done(r.id, acted(it)) } }
                is DeviceRequest.Send -> own(r) { game.send(r.inputs, r.table) { done(r.id, acted(it)) } }
                is DeviceRequest.Act -> own(r) { game.act(r.input) { done(r.id, acted(it)) } }
                is DeviceRequest.EndTurn -> own(r) { game.endTurn { done(r.id, DeviceResult.None) } }
                is DeviceRequest.Assume -> own(r) { game.assume(r.blocks) { done(r.id, acted(it)) } }
                is DeviceRequest.Table -> own(r) { game.table(r.changes) { done(r.id, acted(it)) } }
                is DeviceRequest.Concede -> own(r) { game.concede { done(r.id, acted(it)) } }
                is DeviceRequest.Suggest -> own(r) { game.suggest(r.result, r.note) { done(r.id, acted(it)) } }
                is DeviceRequest.AnswerSuggestion -> own(r) { game.answerSuggestion(r.accept) { done(r.id, acted(it)) } }
                is DeviceRequest.ConfirmResult -> own(r) { game.confirmResult { done(r.id, acted(it)) } }
                is DeviceRequest.TakeBackResult -> own(r) { game.takeBackResult { done(r.id, acted(it)) } }
                is DeviceRequest.Settings -> { game.settings(r.settings); done(r.id, DeviceResult.None) }
                is DeviceRequest.Mulligan -> own(r) { game.mulligan { done(r.id, it?.let(::acted) ?: DeviceResult.None) } }
                is DeviceRequest.Keep -> own(r) { game.keep { done(r.id, it?.let(::acted) ?: DeviceResult.None) } }
                is DeviceRequest.Bottom -> own(r) { game.bottom(r.cards) { done(r.id, it?.let(::acted) ?: DeviceResult.None) } }
                is DeviceRequest.Undo -> own(r) { game.undo(r.target) { done(r.id, DeviceResult.Undo(it)) } }
                is DeviceRequest.RequestUndo -> game.requestUndo(r.target, r.message)
                is DeviceRequest.AnswerRequest -> game.answerRequest(r.accept)
                is DeviceRequest.CancelRequest -> game.cancelRequest()
                is DeviceRequest.Stage -> game.stage(r.board)
                is DeviceRequest.Unstage -> game.unstage()
                is DeviceRequest.StartPlayback -> game.startPlayback()
                is DeviceRequest.DismissReveal -> game.dismissReveal(r.reveal, r.pressedAt)
                is DeviceRequest.ShowPlays -> game.showPlays(r.on)
                is DeviceRequest.DismissShow -> game.dismissShow(r.show)
                is DeviceRequest.DismissCombatHold -> game.dismissCombatHold(r.hold, r.why)
                is DeviceRequest.DismissShows -> game.dismissShows()
                is DeviceRequest.Pause -> game.pause()
                is DeviceRequest.HoldPlayback -> game.holdPlayback()
                is DeviceRequest.Step -> game.step { e -> done(r.id, DeviceResult.Event(e?.let { game.codec.encodeElement(GameEvent.serializer(), it) })) }
                is DeviceRequest.OlderThrough -> done(r.id, DeviceResult.Text(game.olderThroughOn()))
                is DeviceRequest.NeedsFor -> done(r.id, DeviceResult.Text(game.needsForOn(r.event)))
                is DeviceRequest.RequestNeeds -> done(r.id, DeviceResult.Text(game.requestNeedsOn()))
                is DeviceRequest.Page -> done(r.id, DeviceResult.Flag(game.pageOn(r.through, r.page)))
                is DeviceRequest.Review -> done(r.id, DeviceResult.Board(game.reviewPlayOn(r.event)?.let(::loose)))
                is DeviceRequest.UndoPreview -> game.undoPreviewOn(r.target).let { p ->
                    done(r.id, DeviceResult.Preview(p.board?.let(::loose), p.needsRequest, p.loading)) }
                is DeviceRequest.RequestView -> done(r.id, game.requestViewOn(r.viewer, r.localHead, r.target)?.let { v ->
                    DeviceResult.View(v.message, v.unseen, v.turn, v.board?.let(::loose)) } ?: DeviceResult.NoView)
                is DeviceRequest.Repro -> game.repro { done(r.id, DeviceResult.Repro(it)) }
                is DeviceRequest.LogBoard -> game.logBoard { done(r.id, DeviceResult.None) }
                is DeviceRequest.TokenArtFound -> {
                    tokenPictures[r.query] = r.scryfallId
                    // A new picture: the boards are projected again with it
                    if (r.scryfallId != null) game.tokenArt = tokenArt()
                }
                is DeviceRequest.NamedCardFound -> {
                    named[r.name] = r.definition
                    AppLog.info("engine", "Device boundary: the card named ${r.name} ${if (r.definition == null) "isn't" else "is"} in the page's card records")
                    // A named card's decision that waited for it goes on
                    if (r.definition != null) game.tick(host.now())
                }
            }
        }

        private fun loose(board: TableBoard) = checkNotNull(encoder) { "No board published yet" }.loose(board)

        /** Token pictures from the page's card records, asked once each: the plain frame until the answer comes. */
        private fun tokenArt() = TokenArt { query ->
            if (query in tokenPictures) tokenPictures[query]
            else { if (tokensAsked.add(query)) host.send(gameId, DeviceAnswer.TokenArt(query)); null }
        }

        /** CR 707.12: the card named [name] from the page's card records, asked once: its decision waits until the answer. */
        private fun namedCard(name: String): CardDefinition? {
            if (name in named) return named[name]
            if (namesAsked.add(name)) host.send(gameId, DeviceAnswer.NamedCard(name))
            return null
        }
    }
}

/** The worker's one thread as an engine game's worker (web client step 4c part 3): the engine work and its "main thread"
 * both inline, in order; an own action is released at its performed point as on Android ([EngineWorker.early]). */
object HostWorker : EngineWorker {
    override fun run(work: () -> Unit) = work()
    override fun <T> call(work: () -> T, done: (T) -> Unit) = done(work())
    override fun main(work: () -> Unit) = work()
    override fun early(work: (release: (() -> Unit)?) -> Unit, released: () -> Unit) {
        var gone = false
        try { work { if (!gone) { gone = true; released() } } }
        // As on Android's engine thread: a failing step is logged and the caller still gets its answer
        catch (failure: Throwable) { AppLog.error("engine", "Engine work failed", failure) }
        finally { if (!gone) { gone = true; released() } }
    }
}
