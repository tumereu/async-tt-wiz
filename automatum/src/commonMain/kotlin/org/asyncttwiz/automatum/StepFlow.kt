package org.asyncttwiz.automatum

import arcanum.engine.Engine
import arcanum.engine.Pending
import arcanum.engine.decision.Answer
import arcanum.engine.input.Input
import arcanum.engine.setup.result
import arcanum.engine.state.PlayerId

/**
 * Steps and turns through the engine (integration spec §3 I3, I-D28): with Automatum on the step button is a priority
 * pass. When all players pass with an empty Stack the step ends and the next one begins (CR 117.4, 500.2) with its
 * turn-based actions and "at the beginning of …" triggers; all of that is the engine's.
 *
 * Every function here works on an [EngineRun] the caller made; only the player's own inputs of it go out, and the device
 * makes the passes that follow by itself (I-D56 step C).
 */
object StepFlow {
    /**
     * [player] passes priority (the step button, or "Pass" in a priority window). Returns what the engine waits for (null:
     * nothing; a player holds priority, or the game is over).
     *
     * @throws IllegalArgumentException when [player] doesn't hold priority or a decision is open; nothing changes then.
     */
    fun pass(call: EngineRun, player: String): Pending? {
        val s = call.working
        require(s.pending == null) { "A decision is open: ${s.pending?.decision}" }
        require(s.priority.holder?.value == player) { "$player doesn't hold priority (${s.priority.holder ?: "nobody"} does)" }
        return call.perform(Input.PassPriority(PlayerId(player)))
    }

    /** Where [s] is in its turn, as ATW names the steps (the first-strike damage step included, I-D31). */
    fun position(s: arcanum.engine.state.GameState): org.asyncttwiz.protocol.TurnPosition =
        EngineCodec.position(s.turn.step, s.combat?.firstStrikeStep == true)

    /**
     * [s] rests in an untap step with nobody holding priority and nothing to decide (CR 502.4): before the game begins
     * after the opening (CR 103.8).
     */
    fun resting(s: arcanum.engine.state.GameState): Boolean = s.pending == null && s.result == null &&
        s.turn.step == arcanum.api.TurnStep.Untap && s.priority.holder == null

    /**
     * The priority [s] should have for a look ahead from it (null: leave it as it is): nobody holding it, the active
     * player gets it (no passes); with an empty Stack and no passes made, a holder other than the active player is left
     * from a turn changed by hand and the active player gets it, unless [kept] (the engine left it there). In the untap
     * and cleanup steps nobody gets priority (CR 502.4, 514.3), nor while attackers or blockers are awaited (CR 508.1,
     * 509.1).
     */
    fun settledPriority(s: arcanum.engine.state.GameState, kept: Boolean = false): arcanum.engine.state.PriorityState? {
        if (s.pending != null || s.turn.step == arcanum.api.TurnStep.Untap || s.turn.step == arcanum.api.TurnStep.Cleanup ||
            awaitsDeclaration(s)) return null
        val active = s.turn.active
        val holder = s.priority.holder
        return when {
            holder == null -> arcanum.engine.state.PriorityState(active, 0)
            s.stack.isEmpty() && s.priority.passes == 0 && holder != active && !kept -> arcanum.engine.state.PriorityState(active, 0)
            else -> null
        }
    }

    /** Attackers or blockers are awaited (CR 508.1, 509.1): nobody has priority until they are declared. */
    private fun awaitsDeclaration(s: arcanum.engine.state.GameState): Boolean = s.priority.holder == null && when (s.turn.step) {
        arcanum.api.TurnStep.DeclareAttackers -> s.combat == null
        arcanum.api.TurnStep.DeclareBlockers -> s.combat?.let { !it.blocksDecided && it.attackers.isNotEmpty() } == true
        else -> false
    }

    /**
     * Answers the open decision of a shared flow (a cleanup discard, CR 514.1; a trigger's target or order; anyone may
     * answer for its player, D11). A division of combat damage has its own
     * [CombatFlow.divide].
     * @throws IllegalArgumentException when nothing is open or the engine refuses [answer]; nothing changes then.
     */
    fun answer(call: EngineRun, answer: Answer): Pending? {
        val open = requireNotNull(call.working.pending?.decision) { "No decision is open" }
        // Checked first, so a refusal leaves the call as it was
        Engine.perform(call.working, Input.Answer(open.id, answer))
        return call.perform(Input.Answer(open.id, answer))
    }

    /**
     * Answers the open decision with the early answer given for it (I-D33, #8: given while the batch that opened the
     * decision was on its way, [PreAnswers.forOpen]).
     * @throws IllegalArgumentException when no early answer the engine takes is given for it; nothing changes then.
     */
    fun answerEarly(call: EngineRun): Pending? = call.performEarly()
}
