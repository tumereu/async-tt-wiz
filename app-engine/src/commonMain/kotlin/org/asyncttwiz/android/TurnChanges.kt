package org.asyncttwiz.android

import arcanum.engine.change.Change
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent

/** TRN-074: the newest turn change among what the transition [serial] brought: [player]'s turn began in [event], at [at] among
 * its changes; [forward]: the ids of the events the transition showed, in order. */
class TurnChange(val serial: Long, val player: String, val event: GameEvent, val at: Int, val forward: List<String>)

object TurnBanners {
    /**
     * TRN-074: the newest turn change [transition] shows going forward, or null: none on a jump (opening a game, loading it
     * again), a take-back or an undo.
     */
    fun of(transition: EngineGame.Transition): TurnChange? {
        if (transition.jump || transition.undone != null || transition.backward.isNotEmpty()) return null
        for (event in transition.forward.asReversed()) {
            val changes = event.engine?.changes.orEmpty()
            val at = changes.indexOfLast { it is Change.Step && it.before.number != it.after.number && it.after.number > 0 }
            if (at >= 0) return TurnChange(transition.serial, (changes[at] as Change.Step).after.active.value, event, at, transition.forward.map { it.id })
        }
        return null
    }

    /** Whether the banner of [change] comes after the play shown [show] (HIS-112): when the play went on the Stack first. */
    fun afterPlay(change: TurnChange, show: GameDevice.PlayShow?): Boolean {
        if (show == null) return false
        if (show.event != change.event.id) return change.forward.indexOf(show.event) < change.forward.indexOf(change.event.id)
        val added = change.event.engine?.changes.orEmpty().indexOfFirst { it is Change.StackItemAdded && it.item.id == show.entry }
        return added in 0 until change.at
    }
}
