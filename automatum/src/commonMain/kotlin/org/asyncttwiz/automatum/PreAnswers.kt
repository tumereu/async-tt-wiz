package org.asyncttwiz.automatum

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.Share
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.EngineRecord
import org.asyncttwiz.protocol.EngineRecordKind
import org.asyncttwiz.protocol.MtgAction
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.PlayAction

/**
 * A pre-event (integration spec I-D33): an early answer a player gives to a decision of theirs that isn't open yet, so an
 * async game needn't wait for them once it opens (first use: the defender's division of combat damage while the attacker
 * still divides theirs, I-D29). [player] is whose decision it answers; anyone may give it for them (D11). [key] names the
 * decision it answers: the kind and what it is about, so it answers only that decision.
 */
@Serializable
sealed interface PreAnswer {
    val player: String
    val key: String

    /**
     * How [player] divides the combat damage of their creature [source] (CR 510.1c–d): [shares]. It answers that
     * creature's division in turn [turn] (null: any turn), in the first-strike combat damage step when [firstStrike], else
     * in the regular one (CR 510.4: a double striker divides in both).
     */
    @Serializable @SerialName("division")
    data class Division(override val player: String, val source: MtgRef.Card, val shares: List<PreShare>,
        val turn: Int? = null, val firstStrike: Boolean = false) : PreAnswer {
        override val key: String get() = divisionKey(source, firstStrike)
    }

    companion object {
        fun divisionKey(source: MtgRef.Card, firstStrike: Boolean = false) =
            "division:${source.cardId}:${source.zoneChanges}" + if (firstStrike) ":first-strike" else ""
    }
}

/** [amount] of a division to [target]. */
@Serializable data class PreShare(val target: MtgRef, val amount: Int)

/**
 * Pre-events kept in the game (I-D33). Each early answer is its own engine record ([EngineRecordKind.PreAnswer], id
 * "pre:" + [PreAnswer.key]), set and withdrawn with an ordinary undoable event ([set], [withdraw]).
 *
 * - **Applied when its decision opens:** [EngineCall.perform] answers an opened decision with the early answer that
 *   names it ([matching]) when the engine takes it, and goes on, in the same call. One the engine refuses is skipped and
 *   the decision is asked as usual.
 * - **Kept until then:** automated batches keep an early answer while its decision can still open ([kept]: the same
 *   turn, its creature still in combat, its damage step not over). The batch in which its decision opened (applied or
 *   refused) removes it, and so does the first batch after the situation really changed (the combat or its damage step
 *   ended, the creature left combat).
 * - They aren't part of the board an automated batch is planned on (`MtgFacts.digest`): one given meanwhile never
 *   refuses the batch that answers the decision before it. When that batch opens the decision it answers, the decision
 *   is open with its early answer beside it: the next call answers it with it ([forOpen], [StepFlow.answerEarly]).
 */
object PreAnswers {
    private const val PREFIX = "pre:"

    /** The early answers on [board], oldest key first; records this version can't read are left out. */
    fun of(board: BoardState): List<PreAnswer> = board.engineRecords.filter { it.kind == EngineRecordKind.PreAnswer }
        .mapNotNull { runCatching { decode(it) }.getOrNull() }

    /** The early answer with [key] on [board], if any. */
    fun find(board: BoardState, key: String): PreAnswer? = of(board).firstOrNull { it.key == key }

    fun record(answer: PreAnswer): EngineRecord =
        EngineRecord(id(answer), EngineRecordKind.PreAnswer, data = EngineCodec.encode(PreAnswer.serializer(), answer),
            player = answer.player)

    /** Reads a record's early answer. @throws Exception when it can't be read. */
    fun decode(record: EngineRecord): PreAnswer = EngineCodec.decode(PreAnswer.serializer(), record.data)

    /** The event that gives [answer] (or changes the one given before for the same decision). */
    fun set(board: BoardState, answer: PreAnswer): PlayAction {
        val id = id(answer)
        return PlayAction.Mtg(MtgAction.SetRecord(id, board.engineRecords.singleOrNull { it.id == id }, record(answer)))
    }

    /** The event that withdraws the early answer with [key]; null when none is given. */
    fun withdraw(board: BoardState, key: String): PlayAction? {
        val current = board.engineRecords.singleOrNull { it.id == PREFIX + key } ?: return null
        return PlayAction.Mtg(MtgAction.SetRecord(current.id, current, null))
    }

    /** The record id of [answer]. */
    internal fun id(answer: PreAnswer): String = PREFIX + answer.key

    /** The early answer on [board] that names [decision], open in [state], whether the engine takes it or not; null when
     * none does. */
    internal fun matching(board: BoardState, state: GameState, ids: CardIds, decision: Decision): PreAnswer? {
        val given = of(board).ifEmpty { return null }
        return when (decision) {
            is Decision.Divide -> {
                val source = decision.source as? ObjectId.Card ?: return null
                val ref = ids.ref(source) as? MtgRef.Card ?: return null
                // CR 510.4: the first-strike step's division and the regular step's are different decisions
                val key = PreAnswer.divisionKey(ref, state.combat?.firstStrikeStep == true)
                given.filterIsInstance<PreAnswer.Division>().firstOrNull {
                    it.key == key && it.player == decision.player.value && (it.turn == null || it.turn == state.turn.number)
                }
            }
            else -> null
        }
    }

    /** [early] as the engine's answer to [decision] open in [state] when the engine takes it; else null (the situation
     * changed so it no longer fits). */
    internal fun answer(early: PreAnswer, state: GameState, ids: CardIds, decision: Decision): Answer? {
        val answer = when {
            early is PreAnswer.Division && decision is Decision.Divide ->
                Answer.Division(early.shares.map { Share(ids.target(it.target), it.amount) })
            else -> return null
        }
        return answer.takeIf { runCatching { Engine.perform(state, Input.Answer(decision.id, it)) }.isSuccess }
    }

    /** The early answer to the decision open in [call]'s state that the engine takes, if any (#8: one given while the
     * batch opening its decision was on its way). */
    fun forOpen(call: EngineCall): PreAnswer? {
        val decision = call.working.pending?.decision ?: return null
        val early = matching(call.state.board, call.working, call.ids, decision) ?: return null
        return early.takeIf { answer(it, call.working, call.ids, decision) != null }
    }

    /**
     * The early answers on [board] an automated batch keeps (I-D33, #7): those whose decision can still open in [state]
     * (the engine's state the batch ends in) and didn't open in the batch's call ([used], record ids): of this turn, their
     * creature still attacking or blocking, their damage step not over (CR 510.4). Records this version can't read go.
     */
    internal fun kept(board: BoardState, state: GameState, ids: CardIds, used: Set<String>): List<EngineRecord> =
        board.engineRecords.filter { record ->
            if (record.kind != EngineRecordKind.PreAnswer || record.id in used) return@filter false
            when (val early = runCatching { decode(record) }.getOrNull()) {
                is PreAnswer.Division -> {
                    val combat = state.combat ?: return@filter false
                    val source = ObjectId.Card(ids.engine(early.source.cardId), early.source.zoneChanges)
                    val step = state.turn.step
                    (combat.attackers.any { it.id == source } || combat.blockers.any { it.id == source }) &&
                        (early.turn == null || early.turn == state.turn.number) &&
                        step in COMBAT_STEPS &&
                        // The first-strike step is over once the regular one began (the engine's combat no longer marks it)
                        !(early.firstStrike && step == TurnStep.CombatDamage && !combat.firstStrikeStep)
                }
                null -> false
            }
        }

    private val COMBAT_STEPS = setOf(TurnStep.DeclareAttackers, TurnStep.DeclareBlockers, TurnStep.CombatDamage)
}
