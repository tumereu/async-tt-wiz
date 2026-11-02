package org.asyncttwiz.automatum

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
 * decision it answers: the kind and what it is about.
 */
@Serializable
sealed interface PreAnswer {
    val player: String
    val key: String

    /** How [player] divides the combat damage of their creature [source] (CR 510.1c–d): [shares]. */
    @Serializable @SerialName("division")
    data class Division(override val player: String, val source: MtgRef.Card, val shares: List<PreShare>) : PreAnswer {
        override val key: String get() = divisionKey(source)
    }

    companion object {
        fun divisionKey(source: MtgRef.Card) = "division:${source.cardId}:${source.zoneChanges}"
    }
}

/** [amount] of a division to [target]. */
@Serializable data class PreShare(val target: MtgRef, val amount: Int)

/**
 * Pre-events kept in the game (I-D33). Each early answer is its own engine record ([EngineRecordKind.PreAnswer], id
 * "pre:" + [PreAnswer.key]), set and withdrawn with an ordinary undoable event ([set], [withdraw]).
 *
 * - **Applied when its decision opens:** [EngineCall.perform] answers an opened decision with the early answer that
 *   names it ([answer]) when the engine takes it, and goes on, in the same call.
 * - **Invalidated when the situation changes:** the engine's state holds no early answers, so every automated batch
 *   removes them ([EngineBatch]): one applied is used up, and one whose decision didn't open in that batch is gone
 *   because the game moved on. One the engine refuses when its decision opens is skipped, and the decision is asked
 *   as usual.
 * - They aren't part of the board an automated batch is planned on (`MtgFacts.digest`): one given meanwhile never
 *   refuses the batch that answers the decision before it (that batch then leaves it to the next one).
 */
object PreAnswers {
    private const val PREFIX = "pre:"

    /** The early answers on [board], oldest key first; records this version can't read are left out. */
    fun of(board: BoardState): List<PreAnswer> = board.engineRecords.filter { it.kind == EngineRecordKind.PreAnswer }
        .mapNotNull { runCatching { decode(it) }.getOrNull() }

    /** The early answer with [key] on [board], if any. */
    fun find(board: BoardState, key: String): PreAnswer? = of(board).firstOrNull { it.key == key }

    fun record(answer: PreAnswer): EngineRecord =
        EngineRecord(PREFIX + answer.key, EngineRecordKind.PreAnswer, data = EngineCodec.encode(PreAnswer.serializer(), answer),
            player = answer.player)

    /** Reads a record's early answer. @throws Exception when it can't be read. */
    fun decode(record: EngineRecord): PreAnswer = EngineCodec.decode(PreAnswer.serializer(), record.data)

    /** The event that gives [answer] (or changes the one given before for the same decision). */
    fun set(board: BoardState, answer: PreAnswer): PlayAction {
        val id = PREFIX + answer.key
        return PlayAction.Mtg(MtgAction.SetRecord(id, board.engineRecords.singleOrNull { it.id == id }, record(answer)))
    }

    /** The event that withdraws the early answer with [key]; null when none is given. */
    fun withdraw(board: BoardState, key: String): PlayAction? {
        val current = board.engineRecords.singleOrNull { it.id == PREFIX + key } ?: return null
        return PlayAction.Mtg(MtgAction.SetRecord(current.id, current, null))
    }

    /** The early answer [board] holds for the opened [decision] in [state], as the engine's answer, when the engine takes
     * it; else null (none given, or the situation changed so it no longer fits). */
    internal fun answer(board: BoardState, state: GameState, ids: CardIds, decision: Decision): Answer? {
        val given = of(board).ifEmpty { return null }
        val answer = when (decision) {
            is Decision.Divide -> {
                val source = decision.source as? ObjectId.Card ?: return null
                val ref = ids.ref(source) as MtgRef.Card
                val division = given.filterIsInstance<PreAnswer.Division>()
                    .firstOrNull { it.source == ref && it.player == decision.player.value } ?: return null
                Answer.Division(division.shares.map { Share(ids.target(it.target), it.amount) })
            }
            else -> return null
        }
        return answer.takeIf { runCatching { Engine.perform(state, Input.Answer(decision.id, it)) }.isSuccess }
    }
}
