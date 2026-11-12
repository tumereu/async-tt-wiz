package org.asyncttwiz.automatum

import org.asyncttwiz.protocol.game.EnginePreferences
import arcanum.api.Mechanic
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Share
import arcanum.engine.input.Attack
import arcanum.engine.input.Block
import arcanum.engine.input.Input
import arcanum.engine.state.CardId
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.EngineRecord
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.game.EarlyAnswer
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.TableChange
import org.asyncttwiz.protocol.game.TableState

/**
 * The shared combat staging and early answers of a game on engine state (I-D54 §8: drafts stay shared; I-D56 step D),
 * between the table's combat controls and the [TableState]:
 *
 * - **Attack staging** (CMB-028) with the attacker's choices (enlist, optional costs to attack, bands: CMB-036–CMB-038)
 *   is the table's [TableState.attackDraft]: the attacks as the engine's own [Attack]s, so the choices travel with them.
 * - **Block staging** (CMB-034) is [TableState.blockDraft].
 * - **Early divisions of combat damage** (STK-034, I-D33) are [TableState.earlyAnswers] keyed as the engine's division
 *   decision will be ([EarlyAnswer.keyOf]: kind, player, the creature dealing the damage), for the turn they were given in.
 *
 * [changes] turns a board the combat controls made (their lanes and early-answer records) into the table changes that
 * share it; [records] and [blockLanes] show the table's drafts and early answers on the projected board again, so both
 * players see the same staging through the same controls.
 */
object EngineDrafts {
    /**
     * The table changes that make [model]'s table hold what [staged] (a board the combat controls made from the shown one)
     * stages for [viewer]: the attack or block draft of the declaration the engine waits for, and the viewer's own early
     * divisions. Empty when nothing changes.
     */
    fun changes(model: GameModel, staged: BoardState, viewer: String): List<TableChange> {
        val s = model.engine
        val table = model.table
        val me = PlayerId(viewer)
        val out = mutableListOf<TableChange>()
        val options = runCatching { Engine.options(s, s.turn.active, EnginePreferences.of(s)) + s.players.filter { it.id != s.turn.active }.flatMap { Engine.options(s, it.id, EnginePreferences.of(s)) } }
            .getOrDefault(emptyList())
        val (lanes, blocks) = CombatFlow.lanes(staged.combat)
        if (options.any { it is Input.DeclareAttackers }) {
            val attacks = CombatFlow.withChoices(lanes, staged).mapNotNull { attack(s, it) }
            val after = attacks.ifEmpty { null }
            if (after != table.attackDraft) out += TableChange.AttackDraft(table.attackDraft, after)
        } else if (options.any { it is Input.DeclareBlockers }) {
            val after = blocks.mapNotNull { b -> Block(card(s, b.blockerId) ?: return@mapNotNull null, card(s, b.attackerId) ?: return@mapNotNull null) }
                .ifEmpty { null }
            if (after != table.blockDraft) out += TableChange.BlockDraft(table.blockDraft, after)
        }
        // STK-034: only the viewer's own early answers
        val given = PreAnswers.of(staged).filterIsInstance<PreAnswer.Division>().filter { it.player == viewer }
            .mapNotNull { early(s, it) }.associateBy { it.key }
        val had = table.earlyAnswers.filterValues { it.player == me && it.key.startsWith(DIVIDE) }
        for ((key, answer) in given) if (had[key] != answer) out += TableChange.EarlyAnswered(key, table.earlyAnswers[key], answer)
        for ((key, answer) in had) if (key !in given) out += TableChange.EarlyAnswered(key, answer, null)
        return out
    }

    /** [a] (a lane's attacker with its choices) as the engine's [Attack] on [s], or null when its card isn't there. */
    private fun attack(s: GameState, a: LaneAttack): Attack? {
        val attacker = card(s, a.cardId) ?: return null
        val defender = s.players.firstOrNull { it.id != s.turn.active && !it.lost }?.id ?: return null
        val target = a.target?.let { t -> card(s, t)?.let { TargetRef.Object(it) } } ?: TargetRef.Player(defender)
        val paying = if (a.paying.isEmpty()) emptyList() else runCatching { Engine.view(s)[attacker]?.mechanics.orEmpty() }.getOrDefault(emptyList())
            .filter { it.mechanic is Mechanic.AsAttacks }.mapNotNull { it.origin }.filter { it.key in a.paying }.distinct()
        return Attack(attacker, target, a.enlisted.mapNotNull { card(s, it) }, a.band, paying)
    }

    /** The early division [d] as the table's [EarlyAnswer] on [s], or null when its creature isn't there. */
    private fun early(s: GameState, d: PreAnswer.Division): EarlyAnswer? {
        val source = ObjectId.Card(CardId(d.source.cardId), d.source.zoneChanges)
        if (source !in s.battlefield) return null
        val shares = d.shares.mapNotNull { share ->
            val target = when (val t = share.target) {
                is MtgRef.Card -> TargetRef.Object(ObjectId.Card(CardId(t.cardId), t.zoneChanges))
                is MtgRef.Player -> TargetRef.Player(PlayerId(t.accountId))
                is MtgRef.StackItem -> return@mapNotNull null
            }
            Share(target, share.amount)
        }
        return EarlyAnswer(PlayerId(d.player), divisionKey(PlayerId(d.player), source), Answer.Division(shares), d.turn)
    }

    /** The key of [player]'s division of [source]'s combat damage ([EarlyAnswer.keyOf] of that decision). */
    fun divisionKey(player: PlayerId, source: ObjectId): String = "$DIVIDE${player.value}:$source"

    private const val DIVIDE = "Divide:"

    private fun card(s: GameState, id: String): ObjectId? = s.battlefield.firstOrNull { (it as? ObjectId.Card)?.card?.value == id }

    /**
     * The early-answer records the combat controls read on the projected board (CMB-036–CMB-038, STK-034): the attacker's
     * choices with the staged [TableState.attackDraft], and every early division in the table (as both damage steps' of
     * its creature: one early answer answers the creature's division in the step it comes in).
     */
    fun records(s: GameState, table: TableState): List<EngineRecord> = buildList {
        table.attackDraft?.takeIf { s.turn.step == arcanum.api.TurnStep.DeclareAttackers }?.let { draft ->
            val choices = draft.mapNotNull { a ->
                val id = (a.attacker as? ObjectId.Card)?.card?.value ?: return@mapNotNull null
                AttackChoice(id, a.enlisted.mapNotNull { (it as? ObjectId.Card)?.card?.value }, a.paying.map { it.key }, a.band)
            }.filterNot { it.empty }
            if (choices.isNotEmpty()) add(PreAnswers.record(PreAnswer.AttackChoices(s.turn.active.value, s.turn.number, choices)))
        }
        for (early in table.earlyAnswers.values) {
            if (!early.key.startsWith(DIVIDE)) continue
            val division = early.answer as? Answer.Division ?: continue
            val sourceText = early.key.removePrefix(DIVIDE).substringAfter(':')
            val card = sourceText.substringBeforeLast('#')
            val zoneChanges = sourceText.substringAfterLast('#').toIntOrNull() ?: continue
            val shares = division.shares.mapNotNull { share ->
                val target = when (val t = share.target) {
                    is TargetRef.Object -> (t.id as? ObjectId.Card)?.let { MtgRef.Card(it.card.value, it.zoneChanges) }
                    is TargetRef.Player -> MtgRef.Player(t.id.value)
                } ?: return@mapNotNull null
                PreShare(target, share.amount)
            }
            for (firstStrike in listOf(false, true))
                add(PreAnswers.record(PreAnswer.Division(early.player.value, MtgRef.Card(card, zoneChanges), shares, early.turn, firstStrike)))
        }
    }
}
