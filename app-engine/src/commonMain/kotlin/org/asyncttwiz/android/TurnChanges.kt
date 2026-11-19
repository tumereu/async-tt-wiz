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
        return newest(transition.serial, transition.forward)
    }

    /**
     * TRN-074 (review 171522fa63-a): the newest turn change among [events], the events new to the player that the transition
     * [serial] jumped to when their own move lost a race to the server (another event came first, so their own events were
     * dropped and made again on it, SYS-131): no undo, so the turn change shows as any other.
     */
    fun corrected(serial: Long, events: List<GameEvent>): TurnChange? = newest(serial, events)

    private fun newest(serial: Long, events: List<GameEvent>): TurnChange? {
        for (event in events.asReversed()) {
            val changes = event.engine?.changes.orEmpty()
            val at = changes.indexOfLast { it is Change.Step && it.before.number != it.after.number && it.after.number > 0 }
            if (at >= 0) return TurnChange(serial, (changes[at] as Change.Step).after.active.value, event, at, events.map { it.id })
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
