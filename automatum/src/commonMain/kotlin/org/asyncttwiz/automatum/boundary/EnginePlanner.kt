package org.asyncttwiz.automatum.boundary

import arcanum.engine.Engine
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.view.View
import kotlin.time.TimeSource
import org.asyncttwiz.automatum.CardPlays
import org.asyncttwiz.automatum.HandPlays
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.GameModel

/**
 * A board to plan on: the game's own [state] (full information, never changed here), its [view] when already worked out
 * (null: worked out where needed) and what the viewer may see of it ([visible]).
 */
class PlanningBoard(val state: GameState, val view: View?, val visible: Set<ObjectId>)

/**
 * The engine side of the boundary ([EngineRequest], [EngineAnswer]): the browser's Web Worker runs one, holding a copy of
 * each open game's board ([handle]: sent as a snapshot, then as the events that changed it); Android plans on the board
 * it already holds on its engine thread ([plan], through [InProcessEngineChannel]). The planning is the same code.
 * Not thread-safe: one thread (the worker's, the engine thread) uses it.
 */
class EnginePlanner {
    /** The board of a game held here: its number on the page, its codec and its model. */
    private class Held(val board: Long, val codec: EngineJson, val model: GameModel)
    private val games = HashMap<String, Held>()

    /** The board of [game] held now, null when none (tests and logs). */
    fun held(game: String): Long? = games[game]?.board

    /** Answers [request] on the boards held here. Never throws: a failure is [EngineAnswer.Failed]. */
    fun handle(request: EngineRequest): EngineAnswer {
        val started = TimeSource.Monotonic.markNow()
        fun micros() = started.elapsedNow().inWholeMicroseconds
        return try {
            when (request) {
                is EngineRequest.Load -> {
                    games.remove(request.game)
                    val codec = EngineJson(EngineJson.decodeDefinitions(request.definitions))
                    games[request.game] = Held(request.board, codec, codec.decodeModel(request.model))
                    EngineAnswer.Held(request.seq, request.game, request.board, micros())
                }
                is EngineRequest.Advance -> {
                    val held = games[request.game]
                    if (held == null || held.board != request.from) {
                        EngineAnswer.Missing(request.seq, request.game, request.board, micros(), held?.board)
                    } else {
                        // A transfer that doesn't fit leaves no half-changed board behind: the page sends a snapshot next
                        games.remove(request.game)
                        for (event in request.revert) held.model.revert(held.codec.decodeEvent(event))
                        for (event in request.apply) held.model.apply(held.codec.decodeEvent(event))
                        // A revert behind the oldest turn the model's log holds leaves it unusable (GameModel.stale)
                        if (held.model.stale) EngineAnswer.Missing(request.seq, request.game, request.board, micros())
                        else {
                            games[request.game] = Held(request.board, held.codec, held.model)
                            EngineAnswer.Held(request.seq, request.game, request.board, micros())
                        }
                    }
                }
                is EngineRequest.Close -> {
                    games.remove(request.game)
                    EngineAnswer.Held(request.seq, request.game, request.board, micros())
                }
                is EngineRequest.HandPlaysOf, is EngineRequest.CardPlaysOf -> {
                    val held = games[request.game]
                    if (held == null || held.board != request.board) EngineAnswer.Missing(request.seq, request.game, request.board, micros(), held?.board)
                    else {
                        val viewer = (request as? EngineRequest.HandPlaysOf)?.viewer ?: (request as EngineRequest.CardPlaysOf).viewer
                        val model = held.model
                        val view = model.view()
                        plan(request, PlanningBoard(model.engine, view, model.visible(PlayerId(viewer))), started)
                    }
                }
            }
        } catch (problem: Exception) {
            if (request is EngineRequest.Load || request is EngineRequest.Advance) games.remove(request.game)
            failed(request, problem, micros())
        }
    }

    companion object {
        /** Answers the planning [request] on [board] (the in-process path: Android's engine thread on the board it holds).
         * Never throws: a failure is [EngineAnswer.Failed]. A board transfer or [EngineRequest.Close] has nothing to plan:
         * [EngineAnswer.Held]. */
        fun plan(request: EngineRequest, board: PlanningBoard, started: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow()): EngineAnswer {
            fun micros() = started.elapsedNow().inWholeMicroseconds
            return try {
                when (request) {
                    is EngineRequest.HandPlaysOf -> EngineAnswer.HandPlaysAnswer(request.seq, request.game, request.board,
                        0, HandPlays.of(board.state, request.viewer, board.view)).let { it.copy(micros = micros()) }
                    is EngineRequest.CardPlaysOf -> EngineAnswer.CardPlaysAnswer(request.seq, request.game, request.board,
                        0, CardPlays.of(board.state, request.viewer, board.view ?: Engine.view(board.state), board.visible))
                        .let { it.copy(micros = micros()) }
                    is EngineRequest.Load, is EngineRequest.Advance, is EngineRequest.Close ->
                        EngineAnswer.Held(request.seq, request.game, request.board, micros())
                }
            } catch (problem: Exception) {
                failed(request, problem, micros())
            }
        }

        private fun failed(request: EngineRequest, problem: Exception, micros: Long) = EngineAnswer.Failed(request.seq,
            request.game, request.board, micros, problem::class.simpleName ?: "Exception", problem.message?.take(300))
    }
}
