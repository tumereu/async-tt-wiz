package org.asyncttwiz.android

import org.asyncttwiz.appui.simpleClassName
import org.asyncttwiz.automatum.CastFlow
import org.asyncttwiz.automatum.HandCastChips
import org.asyncttwiz.automatum.HandCasts

/**
 * ZON-080 (I-D50): the cost chips of a raised hand card ([HandCasts.chips]), worked out where the engine is (web client step
 * 4e: the browser's engine worker beside the game's device, [DeviceHost]; Android on the Automatum worker).
 */
object HandChips {
    /** [cardId]'s chips on [play] for [viewer], on the game's own engine state (R4a); null when they couldn't be worked out. */
    fun of(play: TableBoard, viewer: String, cardId: String): HandCastChips? = try {
        val definitions = AutomatumBoards.definitions(play)
        val offered = AutomatumBoards.handPlays(play, viewer).of(cardId)
        val seeds = CastFlow.Seeds { DeviceRandom.seed() }
        val engine = requireNotNull(play.engine) { "No board to cast on" }
        HandCasts.chips(CastFlow.Source.engine(engine.state, play.board, play.gameId), definitions, viewer, cardId, offered, seeds)
    } catch (problem: Exception) {
        AppLog.warn("automatum", "Couldn't work the cost chips out: ${simpleClassName(problem)}")
        null
    }
}
