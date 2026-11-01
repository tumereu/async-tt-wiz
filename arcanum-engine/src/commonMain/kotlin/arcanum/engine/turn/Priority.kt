package arcanum.engine.turn

import arcanum.api.TurnStep
import arcanum.engine.Progress
import arcanum.engine.change.Changes
import arcanum.engine.change.Meaning
import arcanum.engine.change.Recorder
import arcanum.engine.combat.Combat
import arcanum.engine.input.Input
import arcanum.engine.resolution.Resolution
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import arcanum.engine.state.PriorityState

/**
 * Priority (CR 117, D57): passing it to the next player in turn order, and when all players still in the game pass
 * in succession, resolving the top of the Stack (CR 117.4, via [Resolution.resolveTop]) or ending the step
 * ([Steps.endStep], CR 500.2). Who gets priority next is set by the flow that knows it: the caster keeps it (CR 117.3c);
 * after a resolution and as a step begins nobody holds it, and [give] gives it to the active player (CR 117.3a–b) once
 * state-based actions and triggers are done (CR 117.5).
 */
internal object Priority {
    fun pass(recorder: Recorder, input: Input.PassPriority): Progress {
        val s = recorder.state
        require(s.priority.holder == input.player) { "${input.player} doesn't have priority (${s.priority.holder} has)" }
        val passes = s.priority.passes + 1
        // CR 117.4: all players pass in succession
        if (passes >= s.playersFrom(input.player).size) {
            recorder.record(Changes.priority(s, PriorityState(null, 0), listOf(Meaning.PriorityPassed)))
            return if (s.stack.isNotEmpty()) resolveTop(recorder) else Steps.endStep(recorder)
        }
        // CR 117.3d: the next player in turn order receives priority
        recorder.record(Changes.priority(s, PriorityState(nextPlayer(s, input.player), passes), listOf(Meaning.PriorityPassed)))
        return Progress.Done
    }

    /** Gives priority to the active player when nobody holds it (CR 117.3a–b): not in the untap step (CR 502.4) and not
     * while a declaration of attackers or blockers is awaited (CR 508.1, 509.1). The active player gets it even in the
     * cleanup step, which only gets here when something happened in it (CR 514.3a). */
    fun give(recorder: Recorder): Progress {
        val s = recorder.state
        if (s.priority.holder != null || s.turn.step == TurnStep.Untap || Combat.waitingForDeclaration(s)) return Progress.Done
        val to = s.playersFrom(s.turn.active).firstOrNull() ?: return Progress.Done
        recorder.record(Changes.priority(s, PriorityState(to, 0), listOf(Meaning.PriorityGranted)))
        return Progress.Done
    }

    /** The next player still in the game after [player] in turn order (CR 101.4, 117.3d). */
    fun nextPlayer(s: GameState, player: PlayerId): PlayerId {
        val order = s.players.map { it.id }
        val start = order.indexOf(player)
        for (k in 1..order.size) {
            val p = s.players[(start + k) % order.size]
            if (!p.lost) return p.id
        }
        return player
    }

    /** Resolves the top of the Stack (CR 608). */
    private fun resolveTop(recorder: Recorder): Progress = Resolution.resolveTop(recorder)
}
