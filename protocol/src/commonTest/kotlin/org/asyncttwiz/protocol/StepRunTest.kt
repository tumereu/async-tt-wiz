package org.asyncttwiz.protocol

import kotlin.test.*

/** Rules v32: multi-step advances record every step they pass, ending exactly where the single event did. */
class StepRunTest {
    private val f = UndoFixture
    private val p = TurnPosition.entries
    private fun state(position: TurnPosition, number: String = "2", attackers: Boolean = false, holder: String? = null): PlayState {
        val root = f.root()
        return root.copy(board = root.board.copy(turn = TurnState(f.a, number, position.phase, position.step),
            attentionHolder = holder,
            combat = if (attackers) CombatArea(f.id(50), listOf(CombatPair(f.id(51), listOf(CombatOccurrence(f.id(52), f.id(9))))))
                else CombatArea(f.id(50))))
    }
    private fun batch(state: PlayState, actions: List<PlayAction>, actor: String = f.a): GameplayTree {
        val tree = GameplayTree(state)
        val command = if (actions.size == 1) f.command(GameAction.Play(actions.single()), 700)
            else f.command(GameAction.PlayBatch(actions), 700)
        val change = HistoryContinuation.change(tree, actor, command)
        return assertNotNull(change.tree, change.reason)
    }
    private fun board(tree: GameplayTree) = tree.stateAt(tree.currentNodeId)!!.board
    private fun path(tree: GameplayTree): List<GameplayMutation> {
        val result = mutableListOf<GameplayMutation>()
        var id = tree.currentNodeId
        while (id in tree.nodes) { val node = tree.nodes.getValue(id); result += node; id = node.parentId }
        return result.asReversed()
    }

    @Test fun betweenFillsEveryStepToTheEndOfTheTurn() {
        val all = p.drop(1)
        assertEquals(all - TurnPosition.DECLARE_BLOCKERS - TurnPosition.COMBAT_DAMAGE,
            StepRun.between(state(TurnPosition.UNTAP).board, null))
        assertEquals(all, StepRun.between(state(TurnPosition.UNTAP, attackers = true).board, null))
        for (from in p) for (attackers in listOf(false, true)) {
            val expected = p.drop(from.ordinal + 1).filter { attackers ||
                it != TurnPosition.DECLARE_BLOCKERS && it != TurnPosition.COMBAT_DAMAGE }
            assertEquals(expected, StepRun.between(state(from, attackers = attackers).board, null), "$from $attackers")
        }
        assertEquals(emptyList(), StepRun.between(state(TurnPosition.CLEANUP).board, null))
    }

    @Test fun turnOneSkipsTheDrawStep() {
        assertEquals(listOf(TurnPosition.UPKEEP), StepRun.between(state(TurnPosition.UNTAP, "1").board, TurnPosition.PRECOMBAT_MAIN))
        assertEquals(listOf(TurnPosition.UPKEEP, TurnPosition.DRAW),
            StepRun.between(state(TurnPosition.UNTAP, "2").board, TurnPosition.PRECOMBAT_MAIN))
        assertEquals(emptyList(), StepRun.between(state(TurnPosition.UPKEEP, "1").board, TurnPosition.PRECOMBAT_MAIN))
        // "1" is only the starting player's first turn; turn 11 draws.
        assertTrue(TurnPosition.DRAW in StepRun.between(state(TurnPosition.UNTAP, "11").board, null))
        assertFalse(TurnPosition.DRAW in StepRun.between(state(TurnPosition.UNTAP, "1").board, null))
    }

    @Test fun betweenStopsBeforeTheTargetAndIsEmptyForEarlierTargets() {
        for (from in p) for (to in p) {
            val between = StepRun.between(state(from, attackers = true).board, to)
            assertEquals(if (to.ordinal > from.ordinal) p.subList(from.ordinal + 1, to.ordinal) else emptyList(), between, "$from → $to")
        }
        assertEquals(listOf(TurnPosition.BEGIN_COMBAT, TurnPosition.DECLARE_ATTACKERS, TurnPosition.END_COMBAT,
            TurnPosition.POSTCOMBAT_MAIN), StepRun.between(state(TurnPosition.PRECOMBAT_MAIN).board, TurnPosition.END_STEP))
        assertEquals(listOf(TurnPosition.END_COMBAT),
            StepRun.between(state(TurnPosition.DECLARE_ATTACKERS).board, TurnPosition.POSTCOMBAT_MAIN))
        assertEquals(listOf(TurnPosition.END_COMBAT),
            StepRun.between(state(TurnPosition.COMBAT_DAMAGE, attackers = true).board, TurnPosition.POSTCOMBAT_MAIN))
    }

    @Test fun actionsArePassedStepsThenTheOrdinaryFinalEvent() {
        val main = state(TurnPosition.POSTCOMBAT_MAIN).board
        assertEquals(listOf(PlayAction.ChooseStep(TurnPosition.END_STEP, through = true),
            PlayAction.ChooseStep(TurnPosition.CLEANUP, through = true), PlayAction.EndTurn), StepRun.endTurn(main))
        assertEquals(listOf(PlayAction.EndTurn), StepRun.endTurn(state(TurnPosition.CLEANUP).board))
        assertEquals(listOf(PlayAction.ChooseStep(TurnPosition.UNTAP)), StepRun.choose(main, TurnPosition.UNTAP))
        assertEquals(listOf(PlayAction.ChooseStep(TurnPosition.END_STEP)), StepRun.choose(main, TurnPosition.END_STEP))
        assertEquals(listOf(PlayAction.ChooseStep(TurnPosition.UPKEEP, through = true), PlayAction.ChooseStep(TurnPosition.DRAW, through = true)),
            StepRun.passTo(state(TurnPosition.UNTAP).board, TurnPosition.DRAW))
        assertEquals(emptyList(), StepRun.passTo(main, TurnPosition.DRAW))
        assertEquals(emptyList(), StepRun.passTo(main, TurnPosition.POSTCOMBAT_MAIN))
    }

    @Test fun wireKeepsOldChooseStepUnchanged() {
        val plain = GameWire.json.encodeToString(PlayAction.serializer(), PlayAction.ChooseStep(TurnPosition.DRAW))
        assertFalse("through" in plain, plain)
        val passed = PlayAction.ChooseStep(TurnPosition.DRAW, through = true)
        val json = GameWire.json.encodeToString(PlayAction.serializer(), passed)
        assertTrue("\"through\":true" in json, json)
        assertEquals(passed, GameWire.json.decodeFromString(PlayAction.serializer(), json))
        assertEquals(32, StepRun.RULES_VERSION)
        assertEquals(StepRun.RULES_VERSION, CURRENT_GAMEPLAY_RULES_VERSION)
        assertEquals(StepRun.RULES_VERSION, HistoryGrouping.STEP_RUN_RULES_VERSION)
    }

    @Test fun endTurnRunEndsInTheSameStateAsTheSingleEndTurn() {
        for (from in p) for (number in listOf("1", "2")) for (attackers in listOf(false, true)) for (holder in listOf(null, f.a, f.b)) {
            val start = state(from, number, attackers, holder)
            val single = batch(start, listOf(PlayAction.EndTurn))
            val run = batch(start, StepRun.endTurn(start.board))
            assertEquals(board(single), board(run), "$from $number $attackers $holder")
            val nodes = path(run)
            assertEquals(StepRun.between(start.board, null).size + 1, nodes.size)
            assertEquals(single.nodes.values.single().transition.attention, nodes.last().transition.attention)
        }
    }

    @Test fun laterChooseRunEndsInTheSameStateAsTheSingleChoice() {
        for (from in p) for (to in p) for (attackers in listOf(false, true)) for (holder in listOf(null, f.b)) {
            val start = state(from, "2", attackers, holder)
            val actions = StepRun.choose(start.board, to)
            if (to.ordinal <= from.ordinal) assertEquals(listOf(PlayAction.ChooseStep(to)), actions)
            if (from == to) continue
            val single = batch(start, listOf(PlayAction.ChooseStep(to)))
            assertEquals(board(single), board(batch(start, actions)), "$from → $to $attackers $holder")
        }
    }

    @Test fun passedStepsNeverMoveAttentionAndRecordEachStep() {
        val start = state(TurnPosition.DECLARE_ATTACKERS, attackers = true, holder = f.a)
        val run = batch(start, StepRun.endTurn(start.board))
        val nodes = path(run)
        val passed = nodes.dropLast(1)
        assertEquals(listOf(TurnPosition.DECLARE_BLOCKERS, TurnPosition.COMBAT_DAMAGE, TurnPosition.END_COMBAT,
            TurnPosition.POSTCOMBAT_MAIN, TurnPosition.END_STEP, TurnPosition.CLEANUP), passed.map { TurnPosition.of(run.stateAt(it.id)!!.board.turn) })
        for (node in passed) {
            assertNull(node.transition.attention, "${node.action}")
            // Even Declare blockers keeps the move with the turn player inside a run.
            assertEquals(f.a, run.stateAt(node.id)!!.board.attentionHolder)
            assertEquals(HistorySignificance.STEP_PASSED, node.grouping!!.significance)
            assertFalse(node.grouping!!.endsGroup)
        }
        assertEquals(AttentionChange(f.a, f.b), nodes.last().transition.attention)
        assertEquals(TurnState(f.b, "3"), board(run).turn)
    }

    @Test fun theWholeRunIsOneGroupAndOneUndo() {
        val start = state(TurnPosition.DECLARE_ATTACKERS, attackers = true)
        // The author's attacker declaration group continues through the run, Declare blockers included.
        val declared = f.play(GameplayTree(start), f.a, PlayAction.SetTapped(f.id(9), true), 600)
        val group = declared.stateAt(declared.currentNodeId)!!.currentGroup!!
        val command = f.command(GameAction.PlayBatch(StepRun.endTurn(declared.stateAt(declared.currentNodeId)!!.board)), 700)
        val run = assertNotNull(HistoryContinuation.change(declared, f.a, command).tree)
        for (node in path(run).drop(1)) {
            val at = run.stateAt(node.id)!!.currentGroup!!
            assertEquals(group.id, at.id, "${node.action}")
            assertFalse(at.closed)
        }
        // The group reads as it did with the single End turn (here the tap still represents it).
        val single = f.play(declared, f.a, PlayAction.EndTurn, 700)
        val last = run.stateAt(run.currentNodeId)!!.currentGroup!!
        assertEquals(single.stateAt(single.currentNodeId)!!.currentGroup!!.representative, last.representative)
        assertEquals(StepRun.between(start.board, null).size + 2, last.count.toInt())
        val undone = assertNotNull(f.undo(run).tree)
        assertEquals(declared.stateAt(declared.currentNodeId)!!.board, undone.stateAt(undone.currentNodeId)!!.board)
    }

    @Test fun theRunsFinalEventRepresentsAStepOnlyGroup() {
        val end = state(TurnPosition.PRECOMBAT_MAIN)
        val turn = batch(end, StepRun.endTurn(end.board))
        assertEquals(turn.currentNodeId, turn.stateAt(turn.currentNodeId)!!.currentGroup!!.representative!!.nodeId)
    }

    @Test fun theRunsFinalStepRepresentsItsGroup() {
        val start = state(TurnPosition.PRECOMBAT_MAIN)
        val run = batch(start, StepRun.choose(start.board, TurnPosition.END_STEP))
        val nodes = path(run)
        assertEquals(5, nodes.size)
        val group = run.stateAt(run.currentNodeId)!!.currentGroup!!
        assertEquals(nodes.first().id, group.firstNodeId)
        assertEquals(nodes.last().id, group.representative!!.nodeId)
        assertEquals(TurnPosition.END_STEP, (nodes.last().action as PlayAction.ChooseStep).position)
    }

    @Test fun aGroupOfOnlyPassedStepsTakesTheNextAnchorOfTheSameAction() {
        val semantic = HistoryGrouping.classify(PlayAction.ChooseStep(TurnPosition.DRAW, through = true), emptyList(),
            GameplayTransition(rulesVersion = 32))
        val passed = HistoryGrouping.append(null, f.id(100), f.id(1100), f.a, semantic)
        val draw = HistorySemantic(HistorySignificance.CROSS_ZONE_MOVE, HistoryIcon.DOWNLOAD, "library/hand", rulesVersion = 3)
        val joined = HistoryGrouping.append(passed, f.id(101), f.id(1101), f.a, draw)
        assertEquals(passed.id, joined.id)
        assertEquals(HistoryCategory.M.key, joined.category)
        assertEquals(f.id(101), joined.representative!!.nodeId)
        // An ordinary step-only group still does not take a following move.
        val ordinary = HistoryGrouping.append(null, f.id(100), f.id(1100), f.a, HistoryGrouping.classify(
            PlayAction.ChooseStep(TurnPosition.DRAW), emptyList(), GameplayTransition(rulesVersion = 32)))
        assertNotEquals(ordinary.id, HistoryGrouping.append(ordinary, f.id(101), f.id(1101), f.a, draw).id)
        // Nor does the other player's action join a passed-step group.
        assertNotEquals(passed.id, HistoryGrouping.append(passed, f.id(101), f.id(1101), f.b, draw).id)
    }

    @Test fun aPassedStepOnlyMovesForward() {
        val start = state(TurnPosition.END_STEP)
        val back = HistoryContinuation.change(GameplayTree(start), f.a,
            f.command(GameAction.Play(PlayAction.ChooseStep(TurnPosition.UPKEEP, through = true)), 700))
        assertEquals("inapplicable", back.reason)
        // The ordinary choice remains a correction to any step.
        assertNotNull(HistoryContinuation.change(GameplayTree(start), f.a,
            f.command(GameAction.Play(PlayAction.ChooseStep(TurnPosition.UPKEEP)), 700)).tree)
    }

    @Test fun ordinaryStepIntoDeclareBlockersStillEndsItsGroupAndHandsOver() {
        val start = state(TurnPosition.DECLARE_ATTACKERS, attackers = true)
        val tree = batch(start, listOf(PlayAction.ChooseStep(TurnPosition.DECLARE_BLOCKERS)))
        assertTrue(tree.stateAt(tree.currentNodeId)!!.currentGroup!!.closed)
        assertEquals(f.b, board(tree).attentionHolder)
    }
}
