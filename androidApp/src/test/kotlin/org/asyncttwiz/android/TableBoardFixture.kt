@file:OptIn(arcanum.engine.state.ArcanumTestSetup::class)

package org.asyncttwiz.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import arcanum.engine.state.PlayerId
import arcanum.engine.state.setup
import org.asyncttwiz.automatum.EngineProjection
import org.asyncttwiz.automatum.StepFlow
import org.asyncttwiz.protocol.EngineSnapshot
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.EngineView
import org.asyncttwiz.protocol.game.GameDefinitions
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.LineRandom
import org.asyncttwiz.protocol.game.TableState
import java.util.IdentityHashMap

/** The render model ([TableBoard]) of a test board: what the table draws of [play]'s board (no engine state, no worked-out
 * values), for the table's drawing, layout and gesture tests. */
internal fun tableBoard(play: PlayState): TableBoard =
    TableBoard(play.gameId, play.id, play.root.id, play.board, play.definitions)

/**
 * The engine table of a test board, for the screenshot tests (R4b follow-up): what the app draws of [play]'s board for
 * [viewer], made once per board ([of]).
 *
 * - [board]: [play]'s board with the engine state of it beside it ([TestEngineState]: the board as the engine's state,
 *   full information) and the values the engine thread works out with a board ([EngineBoardValues]: the computed card
 *   values, and when asked the plays, the shared flow and combat), as [EngineGame] makes them. The board itself is
 *   [play]'s, so the pictures keep the test board's cards and places.
 * - [routes]: the engine table's routes, an [EngineGame] loaded from the same engine state (both players' local heads
 *   there): the quick actions are the player's private toggles (TRN-059), and the step arrow, the wait and the Stack's
 *   button follow the game, as on [EngineTableScreen].
 *
 * A board the engine can't hold (made by hand on the old manual table: a Stack entry without an engine item, a combat
 * area drawn by hand) has no engine state of its own: [board] is the plain render model ([tableBoard]), as the old table
 * drew such a board (Automatum couldn't work it out), and [routes] are those of the same board without its Stack entries
 * and combat area, so the table around it (the quick actions, the step arrow) is still the engine table's.
 */
internal class EngineFixture private constructor(play: PlayState, viewer: String) {
    val board: TableBoard
    val routes: EngineTableRoutes?

    init {
        val model = held(play)
        if (model == null) {
            board = tableBoard(play)
            val shown = board
            routes = held(play.copy(board = play.board.copy(stackEffects = emptyList(), combat = null,
                containers = play.board.containers.map { if (it.zone == PlayZone.STACK) it.copy(cards = emptyList()) else it })))
                ?.let { routes(play, viewer, it) { shown } }
        } else {
            val me = PlayerId(viewer)
            val visible = model.visible(me)
            val engine = EngineView(model.engine, model.table, me, visible)
            val values = EngineBoardValues.make(EngineProjection.Board(play.board, play.definitions), play.gameId, engine.state,
                viewer, runCatching { model.view() }.getOrNull(), visible, EngineWorker.Direct)
            board = TableBoard(play.gameId, play.id, play.root.id, play.board, play.definitions, engine, values)
            val shown = board
            routes = routes(play, viewer, model) { shown }
        }
    }

    companion object {
        private val made = IdentityHashMap<PlayState, MutableMap<String, EngineFixture>>()

        /** [play]'s engine table for [viewer], the same object for the same play (recomposition draws it again). */
        fun of(play: PlayState, viewer: String): EngineFixture = synchronized(made) {
            made.getOrPut(play) { HashMap() }.getOrPut(viewer) { EngineFixture(play, viewer) }
        }

        /** [engineModel], or null when the engine can't hold [play]'s board. */
        private fun held(play: PlayState): GameModel? = try { engineModel(play) } catch (_: TestEngineState.NotHeld) { null }

        /** The routes of an engine game loaded from [model] on [play] (both players' local heads there) for [viewer]. */
        private fun routes(play: PlayState, viewer: String, model: GameModel, shown: () -> TableBoard): EngineTableRoutes {
            val codec = EngineJson(GameDefinitions.of(model.engine))
            val snapshot = EngineSnapshot(codec.encodeDefinitions(), "${play.id}/root", play.root.id, codec.encodeModel(model),
                localHeads = play.board.players.associate { it.accountId to play.root.id })
            val game = EngineGame(play.gameId, viewer, play, snapshot)
            return EngineTableRoutes(viewer, { game }, {}, shown)
        }

        /**
         * [play]'s board as an engine game's model: its engine state ([TestEngineState]) and a table state with a
         * secret. The test boards come from the manual table, which recorded no priority: the state gets the priority an
         * engine game has at that point ([StepFlow.settledPriority]: the active player's where players get priority), as
         * the old table's boards were worked out.
         */
        private fun engineModel(play: PlayState): GameModel {
            val state = TestEngineState.of(play)
            StepFlow.settledPriority(state)?.let { state.setup.priority = it }
            return GameModel(state, TableState(secret = LineRandom.secret(ByteArray(32) { 7 })))
        }
    }
}

/** [EngineFixture.board]: the render model the engine table draws of [play] for [viewer]. */
internal fun engineTableBoard(play: PlayState, viewer: String): TableBoard = EngineFixture.of(play, viewer).board

/** [content] (a table drawn with [TableSurface]) with the engine table's routes of [play] for [viewer]
 * ([EngineFixture.routes]), as [EngineTableScreen] provides them. */
@Composable
internal fun EngineFixtureTable(play: PlayState, viewer: String, content: @Composable () -> Unit) {
    val routes = EngineFixture.of(play, viewer).routes
    if (routes == null) content() else CompositionLocalProvider(LocalEngineRoutes provides routes, content = content)
}
