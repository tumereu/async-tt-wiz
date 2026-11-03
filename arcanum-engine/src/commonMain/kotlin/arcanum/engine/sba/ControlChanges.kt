package arcanum.engine.sba

import arcanum.api.Zone
import arcanum.engine.change.Change
import arcanum.engine.change.GameEvent
import arcanum.engine.change.Recorder
import arcanum.engine.state.ControlSince
import arcanum.engine.casting.Snapshot

/**
 * fin: control of a permanent changing hands (CR 613.1b). Continuous effects change control without an event of their own, so the
 * change is noticed when the game is next checked (with the state-based actions, and as effects end in the cleanup step): the permanent's
 * [arcanum.engine.state.GameObject.controlSince] moves to its new controller (CR 302.6: they haven't controlled it continuously) and a
 * [GameEvent.ControlChanged] is recorded for "whenever … comes under an opponent's control" and "when you lose control of …".
 */
internal object ControlChanges {
    fun notice(recorder: Recorder) {
        val state = recorder.state
        val view = Snapshot(state).view
        state.battlefield.toList().forEach { id ->
            val o = state.obj(id)?.takeIf { it.zone == Zone.Battlefield && !it.phasedOut } ?: return@forEach
            val since = o.controlSince ?: return@forEach
            val now = view[id]?.controller ?: return@forEach
            if (now == since.player) return@forEach
            recorder.record(Change.ControlStart(id, since, ControlSince(now, state.turn.number)))
            recorder.record(Change.Happened(GameEvent.ControlChanged(id, since.player, now)))
        }
    }
}
