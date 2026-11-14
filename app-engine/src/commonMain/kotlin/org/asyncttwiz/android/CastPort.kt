package org.asyncttwiz.android

import arcanum.engine.state.GameState
import org.asyncttwiz.automatum.boundary.CastAnswer
import org.asyncttwiz.automatum.boundary.CastFlowValue
import org.asyncttwiz.automatum.boundary.CastOp
import org.asyncttwiz.automatum.boundary.CastRequest

/**
 * Web client step 4e (docs/specs/web-client.md §1 item 3): where a table's cast flow sessions run ([CastSessions]) as the page
 * sees it: in-process ([LocalCastPort]: Android, tests, a browser page holding the device itself) or the browser's engine Web
 * Worker beside the game's device ([EngineGame.castStep]). The answers come on the page's (main) thread, in the order asked.
 */
interface CastPort {
    /**
     * Sends [request] made on [play] (the board [CastRequest.board] names; null for none), then [done] with its answer on
     * the main thread. [stacks]: the stacks of identical battlefield cards on the board acted on now (TBL-130), read where
     * the step is worked out in-process, and on the page before it goes across.
     */
    fun ask(request: CastRequest, play: TableBoard?, stacks: () -> List<List<String>>?, done: (CastAnswer) -> Unit)

    /** The engine state [flow] is at, on [board] (the board it runs on): in-process the flow's own, else the page's copy of the
     * board's with the flow's changes applied. */
    fun state(flow: CastFlowValue, board: TableBoard): GameState

    companion object {
        /** Whether [op] may need the stacks of identical sources (Autotap's suggestion). */
        fun needsStacks(op: CastOp): Boolean = op !is CastOp.Tap && op !is CastOp.ChooseWay && op !is CastOp.Selection && op !is CastOp.Close
    }
}

/** The sessions in-process, worked out by [run] (the Automatum worker in the app, at once in tests) with the answers on the
 * main thread. */
class LocalCastPort(viewer: String, private val run: (work: () -> CastAnswer, done: (CastAnswer) -> Unit) -> Unit) : CastPort {
    private val sessions = CastSessions(viewer)

    override fun ask(request: CastRequest, play: TableBoard?, stacks: () -> List<List<String>>?, done: (CastAnswer) -> Unit) =
        run({ sessions.handle(request, play, stacks) }, done)

    override fun state(flow: CastFlowValue, board: TableBoard): GameState =
        flow.state ?: error("A flow worked out in-process without its state")
}

/**
 * A table's cast sessions where its game's device is ([game]): in the browser's engine Web Worker when the device is there
 * ([EngineGame.inWorker]), else in-process ([local]).
 */
class TableCastPort(private val local: CastPort, private val game: () -> EngineGame?) : CastPort {
    private fun worker(): EngineGame? = game()?.takeIf { it.inWorker }

    override fun ask(request: CastRequest, play: TableBoard?, stacks: () -> List<List<String>>?, done: (CastAnswer) -> Unit) {
        val g = worker() ?: return local.ask(request, play, stacks, done)
        val sent = if (CastPort.needsStacks(request.op) && request.stacks == null) request.copy(stacks = stacks()) else request
        g.castStep(sent, done)
    }

    override fun state(flow: CastFlowValue, board: TableBoard): GameState {
        val g = worker() ?: return local.state(flow, board)
        return g.castState(flow, board)
    }
}
