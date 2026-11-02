package org.asyncttwiz.automatum

import arcanum.engine.Engine
import arcanum.engine.Pending
import arcanum.engine.decision.Answer
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId

/**
 * Steps and turns through the engine (integration spec §3 I3, I-D18, I-D22, I-D28): with Automatum on the step button
 * is a priority pass. When all players pass with an empty Stack the step ends and the next one begins (CR 117.4, 500.2)
 * with its turn-based actions (untap, draw, cleanup discard and damage removal, durations ending, mana emptying), its
 * "at the beginning of …" triggers, the first-turn draw skipped (CR 103.8a) and extra turns taken (CR 500.7, I-D12);
 * all of that is the engine's, and [EngineBatch] says it with ATW's step and turn events.
 *
 * Every function here works on an [EngineCall] the caller made (best with full information, I-D17/I-D27: a viewer's
 * state can't tell what the other player's unknown hand would let them do, so the host never passes for them there),
 * then sends it as one batch with [EngineBatch.plan]. Each ends with the host's automatic passes ([automatic]), so
 * one call runs on to the next point where a player could act (I-D22): a player with a choice holds priority, a
 * decision is open (a declaration of attackers or blockers, a cleanup discard, a trigger's target, a division of
 * combat damage), or the game is over. The passes the host makes are folded into the batch's visible event; steps passed
 * on the way are recorded as passed steps (`ChooseStep(through = true)`).
 */
object StepFlow {
    /** How far a player's own passing goes. */
    sealed interface Until {
        /** One pass ("Next step"): priority goes on, the top of the Stack resolves or the step ends. */
        data object Pass : Until

        /**
         * "End turn": the player passes now and the host goes on passing for them while this turn lasts (their later
         * passes are automatic, folded, I-D22); in their own turn they attack with nothing (or only what must attack,
         * CR 508.1d). It still stops when another player has priority and a choice, or a decision opens (blockers to
         * declare, their own triggers' targets, the cleanup discard).
         */
        data object EndOfTurn : Until

        /**
         * A later step of this turn (the phase dialog, TRN-009): as [EndOfTurn], but the host stops passing for the
         * player once the turn reaches [position] (or a step after it, when the turn skips it); combat with nothing
         * attacking when [position] comes after combat.
         */
        data class Step(val position: org.asyncttwiz.protocol.TurnPosition) : Until
    }

    /**
     * The most passes the host makes in one call: a bound for a game where nobody has anything to do (each player's
     * library then runs out long before; the call stops anyway after one whole round of turns, [automatic]).
     */
    const val MAX_AUTOMATIC_PASSES = 200

    /**
     * [player] passes priority (the step button, or "Pass" in a priority window), then the host's automatic passes.
     * Returns what the engine waits for (null: nothing; a player holds priority, or the game is over).
     *
     * @throws IllegalArgumentException when [player] doesn't hold priority or a decision is open; nothing changes then.
     */
    fun pass(call: EngineCall, player: String, until: Until = Until.Pass): Pending? {
        HostPriority.settle(call)
        val s = call.working
        require(s.pending == null) { "A decision is open: ${s.pending?.decision}" }
        val keep = when (until) {
            Until.Pass -> null
            Until.EndOfTurn -> Keep(player, s.turn.number, null)
            is Until.Step -> Keep(player, s.turn.number, until.position)
        }
        // Attackers awaited (CR 508.1): "End turn" or a step after combat declares none for its player (or what must
        // attack) and goes on; nobody holds priority to pass
        if (s.priority.holder == null && keep != null && s.turn.active.value == player && keep.declaresNone(s)) return automatic(call, keep)
        require(s.priority.holder?.value == player) { "$player doesn't hold priority (${s.priority.holder ?: "nobody"} does)" }
        call.perform(Input.PassPriority(PlayerId(player)))?.let { return it }
        return automatic(call, keep)
    }

    /** The player the host goes on passing for in turn [turn] (I-D22), until the turn reaches [stop] (null: its end). */
    private class Keep(val player: String, val turn: Int, val stop: org.asyncttwiz.protocol.TurnPosition?) {
        /** Whether [s] is in this keep's turn and hasn't reached [stop] yet. */
        fun going(s: arcanum.engine.state.GameState): Boolean = s.turn.number == turn && !reached(s)

        /** Whether the turn reached [stop] or went past it. */
        fun reached(s: arcanum.engine.state.GameState): Boolean = stop != null && s.turn.number == turn &&
            EngineCodec.position(s.turn.step, s.combat?.firstStrikeStep == true).ordinal >= stop.ordinal

        /** Whether its player declares no attackers when asked: the turn ends, or [stop] comes after combat. */
        fun declaresNone(s: arcanum.engine.state.GameState): Boolean = s.turn.number == turn &&
            (stop == null || stop.ordinal >= org.asyncttwiz.protocol.TurnPosition.END_COMBAT.ordinal)
    }

    /**
     * Answers the open decision of a shared flow (a cleanup discard, CR 514.1; a trigger's target or order; anyone may
     * answer for its player, D11), then the host's automatic passes. A division of combat damage has its own
     * [CombatFlow.divide].
     * @throws IllegalArgumentException when nothing is open or the engine refuses [answer]; nothing changes then.
     */
    fun answer(call: EngineCall, answer: Answer): Pending? {
        val open = requireNotNull(call.working.pending?.decision) { "No decision is open" }
        // Checked first, so a refusal leaves the call as it was
        Engine.perform(call.working, Input.Answer(open.id, answer))
        call.perform(Input.Answer(open.id, answer))?.let { return it }
        return automatic(call)
    }

    /**
     * Answers the open decision with the early answer given for it (I-D33, #8: given while the batch that opened the
     * decision was on its way, [PreAnswers.forOpen]), then the host's automatic passes.
     * @throws IllegalArgumentException when no early answer the engine takes is given for it; nothing changes then.
     */
    fun answerEarly(call: EngineCall): Pending? {
        call.performEarly()?.let { return it }
        return automatic(call)
    }

    /**
     * The host's automatic passes after any input (I-D3, I-D18): while the priority holder can do nothing but pass
     * (`Engine.shouldPassFor`, which follows each player's "Skip when I have no choice"), the host passes for them.
     * Stops at an open decision, a player with a choice, the end of the game, after [MAX_AUTOMATIC_PASSES], or once a
     * whole round of turns went by in this call (turn number two up from where the call is now).
     */
    fun automatic(call: EngineCall): Pending? = automatic(call, null)

    private fun automatic(call: EngineCall, keep: Keep?): Pending? {
        val start = call.working.turn.number
        repeat(MAX_AUTOMATIC_PASSES) {
            val s = call.working
            s.pending?.let { return it }
            if (Engine.result(s) != null) return null
            if (s.turn.number >= start + 2) return null
            // The step the player chose is reached: they act in it (TRN-009)
            if (keep != null && keep.reached(s)) return null
            val going = keep != null && keep.going(s)
            val holder = s.priority.holder ?: run {
                // "End turn" declares no attackers for its player, or the least the requirements make attack (CR 508.1d)
                val declaration = if (!going || s.turn.active.value != keep!!.player || !keep.declaresNone(s)) null
                    else Engine.options(s, s.turn.active).filterIsInstance<Input.DeclareAttackers>().firstOrNull()
                declaration ?: return null
                call.perform(declaration)?.let { return it }
                return@repeat
            }
            val intent = going && holder.value == keep!!.player
            if (!intent && !Engine.shouldPassFor(s, holder)) return null
            call.perform(Input.PassPriority(holder), automatic = true)?.let { return it }
        }
        return call.pending
    }
}
