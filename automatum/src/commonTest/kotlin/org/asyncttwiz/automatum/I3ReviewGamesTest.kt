package org.asyncttwiz.automatum

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import org.asyncttwiz.protocol.CombatAction
import org.asyncttwiz.protocol.CombatDestination
import org.asyncttwiz.protocol.CombatRole
import org.asyncttwiz.protocol.CommandIds
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameCommand
import org.asyncttwiz.protocol.GameScope
import org.asyncttwiz.protocol.GameplayRules
import org.asyncttwiz.protocol.IdentityGap
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Review of I2b/I3 (report `arcanum-i3-review.md`): seeded games played the way the app plays them, through [SharedFlow]
 * (the step button for whoever holds priority, Let it resolve, Choose for them, divisions) with the declarations drafted in
 * ATW's lanes by ordinary events (CMB-028) and declared from them, over many turns with combat. Every call is checked by
 * [RoundTrip]; after each Automatum step the move is with who must act (TRN-058, I-D34), the step shown is the engine's
 * (TRN-056), and the step is one history event (HIS-092).
 */
class I3ReviewGamesTest {
    init { CombatCards.ensured }

    private val aliceDeck = List(7) { "Mountain" } + List(4) { "Swamp" } + List(3) { "Plains" } + listOf("Storm Spark",
        "Cinder Bolt", "Festering Gremlin", "Viper Adept", "Grim Prowler", "Bloodbond Acolyte", "Hill Ogre",
        "Twinblade Duelist", "Dawnlance Squire", "Hill Ogre", "Stout Bear", "Dawnlance Squire")
    private val bobDeck = List(8) { "Island" } + List(6) { "Forest" } + listOf("Glance Ahead", "Gearwing Glider",
        "Ironhide Brute", "Stout Bear", "Stout Bear", "Haven Warden", "Sunning Mudlizard", "Time Stride", "Ironhide Brute",
        "Viper Adept", "Stout Bear")

    private var manualRequests = 0

    private fun manual(rt: RoundTrip, actor: String, action: PlayAction) {
        val command = GameCommand(CommandIds.uuidV5(AtwTable.NAMESPACE, "i3-games/${manualRequests++}"),
            GameScope(rt.state.gameId, playId = rt.state.id), GameAction.Play(action), CommandIds.uuidV5(AtwTable.NAMESPACE, "lanes"), "1")
        val change = GameplayRules.change(rt.state, actor, command)
        assertEquals("accepted", change.outcome, "$action: ${change.reason}")
        rt.state = change.state!!
        rt.tags += null
    }

    private fun flow(rt: RoundTrip) = SharedFlow(rt.state, rt.definitions, EngineLog.from(rt.tags, rt.state, rt.definitions), rt::seed)

    /** Who must act on [s] (I-D34): an open decision's player, the priority holder, or who declares. */
    private fun acting(s: GameState): String? {
        if (Engine.result(s) != null) return null
        s.pending?.decision?.player?.let { return it.value }
        s.priority.holder?.let { return it.value }
        return s.players.firstOrNull { p -> Engine.options(s, p.id).any { it is Input.DeclareAttackers || it is Input.DeclareBlockers } }?.id?.value
    }

    private fun checkStep(rt: RoundTrip, seed: Long, what: String) {
        val s = rt.engine()
        acting(s)?.let { assertEquals(it, rt.state.board.effectiveAttentionHolder, "seed $seed after $what: the move is with who must act") }
        val shown = TurnPosition.of(rt.state.board.turn)
        val engine = EngineCodec.position(s.turn.step, s.turn.step == TurnStep.CombatDamage && s.combat?.firstStrikeStep == true)
        assertEquals(engine, shown, "seed $seed after $what: the step shown is the engine's")
        val batch = rt.batches.lastOrNull() ?: return
        assertFalse(batch.first().grouping?.folded == true, "seed $seed after $what: the step's first event is shown")
        assertTrue(batch.drop(1).all { it.grouping?.folded == true }, "seed $seed after $what: the rest is folded")
    }

    private fun play(seed: Long, calls: Int): Pair<Int, Int> {
        val random = Random(seed)
        val table = AtwTable("i3-games-$seed")
        for ((player, deck) in listOf(table.alice to aliceDeck, table.bob to bobDeck)) {
            val shuffled = deck.shuffled(random)
            table.put(player, PlayZone.HAND, *shuffled.take(7).toTypedArray())
            table.put(player, PlayZone.LIBRARY, *shuffled.drop(7).toTypedArray())
        }
        val rt = RoundTrip(table.state(), table.definitions, seed)
        val players = listOf(table.alice, table.bob)
        var attacks = 0
        repeat(calls) {
            val s = rt.engine()
            if (Engine.result(s) != null) return s.turn.number to attacks
            val decision = s.pending?.decision
            // D11: either player may act for the one who must
            val actor = players.random(random)
            val what: String
            when {
                decision is Decision.Divide -> {
                    rt.apply(flow(rt).divide(null), actor); what = "a division"
                }
                decision != null -> {
                    val listed = flow(rt).listedAnswers()
                    if (listed.isEmpty()) fail("seed $seed: nothing listed for $decision")
                    rt.apply(flow(rt).answer(listed.random(random)), actor); what = "an answer to ${decision::class.simpleName}"
                }
                s.priority.holder == null -> {
                    val choices = CombatFlow.choices(rt.call())
                    val declaring = choices.declaring ?: fail("seed $seed: nobody holds priority and nothing is awaited")
                    // The declaring player drafts in the lanes by ordinary events (CMB-028), as drags and Declare do
                    if (choices.attackers) {
                        val chosen = choices.attacks.keys.filter { random.nextBoolean() }
                        val lanes = choices.attacks.filterKeys { it in chosen }
                        if (CombatFlow.attackRefusal(rt.call(), lanes.map { (c, t) -> LaneAttack(c, t.first()) }) == null) {
                            for ((card, targets) in lanes) {
                                if (rt.state.board.combat == null) manual(rt, declaring, PlayAction.Combat(CombatAction.Open))
                                val area = rt.state.board.combat!!
                                manual(rt, declaring, PlayAction.Combat(CombatAction.Place(card, CombatDestination(area.id, CombatRole.ATTACKER,
                                    pairGap = IdentityGap(area.pairs.lastOrNull()?.id), attackTarget = targets.first()))))
                            }
                            if (lanes.isNotEmpty()) attacks++
                        }
                    } else {
                        for ((card, attackers) in choices.blocks) {
                            if (!random.nextBoolean()) continue
                            val attacker = attackers.random(random)
                            val area = rt.state.board.combat ?: continue
                            val pair = area.pairs.firstOrNull { p -> p.attackers.any { it.sourceId == attacker } } ?: continue
                            val draft = CombatFlow.lanes(area).second + LaneBlock(card, attacker)
                            if (CombatFlow.blockRefusal(rt.call(), draft) != null) continue
                            manual(rt, declaring, PlayAction.Combat(CombatAction.Place(card, CombatDestination(area.id, CombatRole.BLOCKER, pairId = pair.id))))
                        }
                    }
                    // Next step (or Attack / Block) declares what the lanes hold, whoever presses it
                    try {
                        rt.apply(flow(rt).declare(), actor)
                    } catch (refused: CombatRefused) {
                        fail("seed $seed: the lanes drafted only as the engine allows were refused: ${refused.reason}")
                    }
                    what = "a declaration"
                }
                else -> {
                    val holder = s.priority.holder!!.value
                    val doing = Engine.options(s, s.priority.holder!!).filter { it is Input.Cast || it is Input.PlayLand }
                    if (doing.isNotEmpty() && random.nextInt(3) > 0) {
                        rt.perform(doing.random(random), holder)
                        // A cast's choices, then its caster's automatic pass, as CastFlow sends it
                        var guard = 10
                        while (guard-- > 0) {
                            val d = rt.engine().pending?.decision ?: break
                            if (d is Decision.ManaPayment) rt.perform(Input.Answer(d.id, Answer.ManaPayment(d.suggestion)), holder)
                            else rt.answer()
                        }
                        what = "a cast"
                    } else if (s.stack.isNotEmpty()) {
                        rt.apply(flow(rt).pass(holder), actor); what = "Let it resolve"
                    } else {
                        val until = when (random.nextInt(6)) {
                            0 -> StepFlow.Until.EndOfTurn
                            1 -> StepFlow.Until.Step(TurnPosition.END_STEP)
                            else -> StepFlow.Until.Pass
                        }
                        val position = TurnPosition.of(rt.state.board.turn)!!
                        if (until is StepFlow.Until.Step && until.position.ordinal <= position.ordinal) rt.apply(flow(rt).step(holder, StepFlow.Until.Pass), actor)
                        else rt.apply(flow(rt).step(holder, until), actor)
                        what = "Next step $until"
                    }
                }
            }
            if (what != "a cast") checkStep(rt, seed, what)
        }
        return rt.engine().turn.number to attacks
    }

    // TRN-057, TRN-058, CMB-028, HIS-092: long games played as the app plays them
    @Test fun longGamesThroughTheAppsFlows() {
        for (seed in listOf(71L, 72L, 73L)) {
            val (turns, attacks) = play(seed, calls = 260)
            println("seed $seed: turn $turns, attacks $attacks")
            assertTrue(attacks > 0, "seed $seed: some combat")
        }
    }
}
