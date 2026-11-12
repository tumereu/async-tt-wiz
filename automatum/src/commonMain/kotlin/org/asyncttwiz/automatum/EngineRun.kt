package org.asyncttwiz.automatum

import org.asyncttwiz.protocol.game.EnginePreferences
import arcanum.engine.Engine
import arcanum.engine.Outcome
import arcanum.engine.Pending
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.RandomKind
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.RandomV1

/**
 * Inputs performed on a copy of an engine state, as the table's controllers work out a player's cast, answer, pass or
 * declaration (engine-responsiveness R4a): [start] is the state shown (never changed here; a game on engine state gives
 * its own full information state, I-D56), [board] the board the table shows of it, whose combat lanes and records (the
 * attacker's staged choices, early answers, [EngineDrafts]) the flows read. [perform] runs an input and answers what the
 * host answers inside the same run:
 *
 * - randomness with ATW's own seeded randomness ([RandomV1], fresh seeds from [seeds]); a shuffle's order is the one
 *   ATW's `ShuffleLibrary` gives for the same seed;
 * - a step the engine leaves to the players (an engine gap): answered Done and logged (pivot review #3);
 * - a player's decision answered early (I-D33, [PreAnswers]) with that answer, when the engine takes it.
 *
 * The run ends when the engine waits for a player ([pending]) or is done. [inputs] are the player's own inputs, what a
 * game on engine state sends (its device answers the randomness and makes the passes that follow by itself).
 */
class EngineRun(
    /** The engine state the run starts from. */
    val start: GameState,
    /** The board shown of [start]: its combat lanes and records. */
    val board: BoardState,
    /** The game, for the log of engine gaps and early answers ([AutomatumLog]). */
    val gameId: String,
    internal val ids: CardIds,
    private val seeds: () -> String,
) {
    /** The state after [changes]. */
    val working: GameState = start.deepCopy()
    val changes = mutableListOf<Change>()

    /** What the engine waits for now: a player's decision. */
    val pending: Pending? get() = working.pending

    /** Performs [input] and answers the host's decisions that follow; returns what the engine then waits for. */
    fun perform(input: Input): Pending? {
        own += input
        run(input)
        while (true) {
            val decision = working.pending?.decision ?: return null
            val answer = when (decision) {
                is Decision.Random -> random(decision)
                // Pivot review #3 (I-D52): every game is played by the rules, so a step the engine leaves to the players
                // is an engine gap: logged for a report (I-D43), answered Done at once and said in the history
                // ([MtgAction.Skipped], [gaps]); the game goes on without it
                is Decision.Manual -> {
                    gaps += decision.text
                    AutomatumLog.refusal(gameId, "engine gap", "skipped by hand step: ${decision.text}")
                    Answer.Done
                }
                // I-D33: a decision a player answered early is answered now, when the engine takes the early answer; the
                // early answer is used up either way (one the engine refuses leaves the decision asked as usual)
                else -> {
                    val early = PreAnswers.matching(board, working, ids, decision) ?: return working.pending
                    preAnswered += PreAnswers.id(early)
                    val taken = PreAnswers.answer(early, working, ids, decision)
                    AutomatumLog.preEvent(gameId, if (taken == null) "refused" else "used",
                        "${PreAnswers.id(early)} for ${decision.kind} ${decision.id.value}")
                    taken ?: return working.pending
                }
            }
            run(Input.Answer(decision.id, answer))
        }
    }

    /** The inputs a player made in this run, in order ([perform]): what a game on engine state sends (I-D56 step C), where
     * the device answers the randomness and makes the automatic passes itself. */
    val inputs: List<Input> get() = own
    private val own = mutableListOf<Input>()

    /** The steps the engine left to the players in this run, each answered Done ([perform]; pivot review #3). */
    val gaps = mutableListOf<String>()

    /** The early answers (record ids, I-D33) whose decision opened in this run: applied, or refused and asked as usual. */
    internal val preAnswered = mutableSetOf<String>()

    /**
     * Answers the decision open when the run started with the early answer given for it ([PreAnswers.forOpen], #8: given
     * while the event that opened the decision was on its way), then goes on as [perform] does; returns what the engine
     * then waits for.
     * @throws IllegalArgumentException when no early answer the engine takes is given for the open decision.
     */
    fun performEarly(): Pending? {
        val early = PreAnswers.forOpen(this)
        val decision = working.pending?.decision
        val answer = if (early != null && decision != null) PreAnswers.answer(early, working, ids, decision) else null
        if (early == null || decision == null || answer == null) throw IllegalArgumentException("No early answer for the open decision")
        preAnswered += PreAnswers.id(early)
        AutomatumLog.preEvent(gameId, "used", "${PreAnswers.id(early)} for the open ${decision.kind} ${decision.id.value}")
        return perform(Input.Answer(decision.id, answer))
    }

    /** The engine's outcome of [input] on [working], nothing applied yet. */
    private fun performed(input: Input): Outcome = Engine.perform(working, input, EnginePreferences.of(working))

    private fun run(input: Input) {
        val outcome = performed(input)
        outcome.changes.forEach { working.apply(it); changes += it }
    }

    private fun random(decision: Decision.Random): Answer = when (val what = decision.what) {
        RandomKind.CoinFlip -> Answer.CoinFlip(RandomV1.sample(2, RandomV1.Words(seeds())::next) == 0)
        is RandomKind.Die -> RandomV1.Words(seeds()).let { words -> Answer.DieRolls(List(what.count) { RandomV1.sample(what.sides, words::next) + 1 }) }
        is RandomKind.Objects -> objects(decision.player, what)
    }

    /** Random objects: a shuffle ([shuffled]) in the order ATW's `ShuffleLibrary` gives; else a random pick. */
    private fun objects(player: PlayerId, what: RandomKind.Objects): Answer.RandomObjects {
        val seed = seeds()
        val atw = what.among.map { (it as? ObjectId.Card)?.let { o -> ids.atw(o.card) } ?: error("Not a card: $it") }
        val owner = if (what.library.isNotEmpty()) player else shuffled(what)
        val order = if (owner == null) RandomV1.shuffle(atw, seed).take(what.count) else {
            // ATW shuffles the whole library with the seed; cards the shuffle leaves out (CR 701.24b) go back on top.
            // Final cast review #7: the library as the engine shuffles it, not as it is while the decision is asked (cards
            // found earlier in the step, "search …, put them onto the battlefield, then shuffle", have left it by then)
            val library = what.library.ifEmpty { working.player(owner).library }
                .map { ids.atw((it as ObjectId.Card).card) }
            RandomV1.shuffle(library, seed).filter { it in atw }
        }
        val byAtw = atw.zip(what.among).toMap()
        return Answer.RandomObjects(order.map { byAtw.getValue(it) })
    }

    /** The player whose library [what] shuffles: all of it (but cards the shuffle leaves out) in a random order. */
    private fun shuffled(what: RandomKind.Objects): PlayerId? {
        if (what.count != what.among.size || what.among.isEmpty()) return null
        return working.players.firstOrNull { p -> what.among.all { it in p.library } }?.id
    }

    companion object {
        /**
         * A run on a game's own engine state (I-D56): [state] (full information; kept as the run's start, so never
         * changed afterwards) shown as [board]. Card ids are the engine's own (a game on engine state keeps the game's
         * card ids). Without [seeds] a random result fails the run.
         */
        fun of(state: GameState, board: BoardState, gameId: String, seeds: () -> String = { error("No randomness here") }): EngineRun =
            EngineRun(state, board, gameId, CardIds(), seeds)
    }
}
