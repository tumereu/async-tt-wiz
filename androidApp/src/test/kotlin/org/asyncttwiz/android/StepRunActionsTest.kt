package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

/** Rules v32: multi-step turn actions record every passed step in one invocation, ending exactly as before. */
class StepRunActionsTest {
    private val id = TableToolFixture::id
    private val turnPlayer = id(7)
    private val other = id(8)

    /** A real tree, toolkit and batch sender, as the table wires them. */
    private class Harness(val start: PlayState, val actor: String) {
        var tree = GameplayTree(start)
        val commands = mutableListOf<GameCommand>()
        fun current() = requireNotNull(tree.stateAt(tree.currentNodeId))
        private fun enqueue(actions: List<PlayAction>, request: String?): Boolean {
            val command = GameCommand(request ?: java.util.UUID.randomUUID().toString(), GameScope(start.gameId, playId = start.id),
                GameAction.PlayBatch(actions), start.id, "0")
            val change = PlayBatches.change(tree, actor, command)
            assertNull(change.reason)
            tree = change.tree!!
            commands += command
            return true
        }
        val toolkit = TableToolkitController(actor, ::current, { true }, { action, request -> enqueue(listOf(action), request) },
            playBatch = ::enqueue, playTree = { tree })
        val scope get() = TableActionScope(start.gameId, start.id)
        fun quick(name: String) = toolkit.perform(QuickActions.contextual(current(),
            toolkitQuickContext(current(), toolkit.selectionState.value, null, actor), toolkit.table).single { it.name == name }.intent)
        /** Events of the last command, oldest first. */
        fun nodes(): List<GameplayMutation> {
            val result = mutableListOf<GameplayMutation>()
            var cursor = tree.currentNodeId
            while (cursor in tree.nodes) { val node = tree.nodes.getValue(cursor); result += node; cursor = node.parentId }
            return result.asReversed()
        }
        fun undo() {
            val change = SelectiveUndo.change(tree, actor, commands.last().copy(requestId = java.util.UUID.randomUUID().toString(),
                action = GameAction.Undo))
            assertNull(change.reason)
            tree = change.tree!!
        }
    }

    /** [TableToolFixture] plus its Library's top card, so the draw shortcuts really draw. */
    private fun at(position: TurnPosition, number: String = "3", combat: CombatArea? = null, holder: String? = null) =
        TableToolFixture.play(position, number).let { it.copy(board = it.board.copy(combat = combat, attentionHolder = holder,
            cards = it.board.cards + PlayCard(id(22), "def", turnPlayer, id(11), id(25)))) }

    /** The single-event end state used before rules v32. */
    private fun before(start: PlayState, actor: String, actions: List<PlayAction>): BoardState {
        var tree = GameplayTree(start)
        val change = PlayBatches.change(tree, actor, GameCommand(id(900), GameScope(start.gameId, playId = start.id),
            GameAction.PlayBatch(actions), start.id, "0"))
        assertNull(change.reason)
        tree = change.tree!!
        return tree.stateAt(tree.currentNodeId)!!.board
    }

    private fun passed(vararg positions: TurnPosition) = positions.map { PlayAction.ChooseStep(it, through = true) }

    /** One invocation, one group, no handover on the way. */
    private fun assertOneRun(h: Harness, start: PlayState, groups: Int = 1) {
        assertEquals(1, h.commands.size)
        val nodes = h.nodes()
        assertEquals(setOf(h.commands.single().requestId), nodes.map { it.invocationId }.toSet())
        assertEquals(groups, nodes.map { h.tree.stateAt(it.id)!!.currentGroup!!.id }.toSet().size)
        val holder = start.board.attentionHolder
        for (node in nodes.filter { (it.action as? PlayAction.ChooseStep)?.through == true }) {
            assertNull(node.transition.attention)
            assertEquals(holder, h.tree.stateAt(node.id)!!.board.attentionHolder)
            assertFalse(node.grouping!!.endsGroup)
            // Nothing on the way pauses playback for the other player.
            val viewer = if (node.author == turnPlayer) other else turnPlayer
            assertEquals(emptySet(), HistoryAutoPause(viewer, start.board).triggers(node))
            assertTrue(historyText(node, h.tree.stateAt(node.parentId), viewer).description.startsWith("Moved to "))
        }
        h.undo()
        assertEquals(start.board, h.current().board)
    }

    @Test fun endTurnHoldQuickActionAndDialogRecordEveryRemainingStep() {
        for (route in listOf("hold", "quick", "dialog")) {
            val start = at(TurnPosition.POSTCOMBAT_MAIN, holder = turnPlayer)
            val h = Harness(start, turnPlayer)
            assertTrue(if (route == "quick") h.quick("End turn") else if (route == "hold") h.toolkit.perform(TableActionIntent.End(h.scope))
                else h.toolkit.table.perform(TableActionIntent.End(h.scope)), route)
            assertEquals(passed(TurnPosition.END_STEP, TurnPosition.CLEANUP) + PlayAction.EndTurn, h.commands.single().playActions())
            assertEquals(before(start, turnPlayer, listOf(PlayAction.EndTurn)), h.current().board)
            assertEquals(TurnState(other, "4"), h.current().board.turn)
            assertEquals(other, h.current().board.attentionHolder)
            assertOneRun(h, start)
        }
    }

    @Test fun endTurnFromUntapOfTurnOneSkipsDrawAndCombatSteps() {
        val start = at(TurnPosition.UNTAP, "1")
        val h = Harness(start, turnPlayer)
        assertTrue(h.toolkit.perform(TableActionIntent.End(h.scope)))
        assertEquals(passed(TurnPosition.UPKEEP, TurnPosition.PRECOMBAT_MAIN, TurnPosition.BEGIN_COMBAT,
            TurnPosition.DECLARE_ATTACKERS, TurnPosition.END_COMBAT, TurnPosition.POSTCOMBAT_MAIN, TurnPosition.END_STEP,
            TurnPosition.CLEANUP) + PlayAction.EndTurn, h.commands.single().playActions())
        assertEquals(before(start, turnPlayer, listOf(PlayAction.EndTurn)), h.current().board)
        assertOneRun(h, start)
    }

    @Test fun endOpponentsTurnFromDeclareAttackersPassesBlockersWithoutHandingTheMoveOver() {
        val f = CombatDamageFixture()
        val start = f.play.let { it.copy(board = it.board.copy(turn = it.board.turn.copy(
            phase = TurnPosition.DECLARE_ATTACKERS.phase, step = TurnPosition.DECLARE_ATTACKERS.step), attentionHolder = f.defender)) }
        val h = Harness(start, f.defender)
        assertTrue(h.toolkit.table.perform(TableActionIntent.End(h.scope)))
        assertEquals(passed(TurnPosition.DECLARE_BLOCKERS, TurnPosition.COMBAT_DAMAGE, TurnPosition.END_COMBAT,
            TurnPosition.POSTCOMBAT_MAIN, TurnPosition.END_STEP, TurnPosition.CLEANUP) + PlayAction.EndTurn, h.commands.single().playActions())
        assertEquals(before(start, f.defender, listOf(PlayAction.EndTurn)), h.current().board)
        assertFalse(h.current().currentGroup!!.closed)
        assertOneRun(h, start)
    }

    @Test fun dialogChoosingALaterStepFillsTheStepsBetweenAndAnEarlierStepIsOneEvent() {
        val start = at(TurnPosition.PRECOMBAT_MAIN)
        val h = Harness(start, turnPlayer)
        assertTrue(h.toolkit.table.perform(TableActionIntent.Choose(h.scope, TurnPosition.CLEANUP)))
        assertEquals(passed(TurnPosition.BEGIN_COMBAT, TurnPosition.DECLARE_ATTACKERS, TurnPosition.END_COMBAT,
            TurnPosition.POSTCOMBAT_MAIN, TurnPosition.END_STEP) + PlayAction.ChooseStep(TurnPosition.CLEANUP),
            h.commands.single().playActions())
        assertEquals(before(start, turnPlayer, listOf(PlayAction.ChooseStep(TurnPosition.CLEANUP))), h.current().board)
        assertOneRun(h, start)

        val late = Harness(at(TurnPosition.END_STEP), turnPlayer)
        assertTrue(late.toolkit.table.perform(TableActionIntent.Choose(late.scope, TurnPosition.UPKEEP)))
        assertEquals(listOf<PlayAction>(PlayAction.ChooseStep(TurnPosition.UPKEEP)), late.commands.single().playActions())
    }

    @Test fun untapDrawToMainUntapsInUntapAndDrawsInTheDrawStep() {
        val start = at(TurnPosition.UNTAP)
        val h = Harness(start, turnPlayer)
        assertTrue(h.quick("Untap, draw → main"))
        val draw = PlayAction.MoveCard(id(22), MoveSource(id(11), edge = MoveSourceEdge.TOP), MoveDestination(id(12), MoveInsertion.UNORDERED))
        assertEquals(listOf(PlayAction.SetTapped(id(20), false)) + passed(TurnPosition.UPKEEP, TurnPosition.DRAW) + draw +
            PlayAction.ChooseStep(TurnPosition.PRECOMBAT_MAIN), h.commands.single().playActions())
        val nodes = h.nodes()
        fun stepOf(node: GameplayMutation) = TurnPosition.of(h.tree.stateAt(node.id)!!.board.turn)
        assertEquals(TurnPosition.UNTAP, stepOf(nodes.first()))
        assertEquals(TurnPosition.DRAW, stepOf(nodes.single { it.action is PlayAction.MoveCard }))
        assertEquals(before(start, turnPlayer, listOf(PlayAction.SetTapped(id(20), false), draw,
            PlayAction.ChooseStep(TurnPosition.PRECOMBAT_MAIN))), h.current().board)
        // As before: the untap is one group, the draw and Main another (the passed steps join the untap).
        assertEquals(nodes.first().id, h.tree.stateAt(nodes[2].id)!!.currentGroup!!.firstNodeId)
        assertOneRun(h, start, groups = 2)
    }

    @Test fun drawAndGoToMainDrawsInTheDrawStep() {
        val start = at(TurnPosition.UPKEEP)
        val h = Harness(start, turnPlayer)
        assertTrue(h.quick("Draw + go to main"))
        val actions = h.commands.single().playActions()
        assertEquals(passed(TurnPosition.DRAW), actions.take(1))
        assertIs<PlayAction.MoveCard>(actions[1])
        assertEquals(PlayAction.ChooseStep(TurnPosition.PRECOMBAT_MAIN), actions.last())
        assertEquals(3, actions.size)
        assertEquals(before(start, turnPlayer, listOf(actions[1], PlayAction.ChooseStep(TurnPosition.PRECOMBAT_MAIN))), h.current().board)
        assertOneRun(h, start)
    }

    @Test fun goToMainOnTurnOnePassesUpkeepOnly() {
        val start = at(TurnPosition.UNTAP, "1")
        val h = Harness(start, turnPlayer)
        assertTrue(h.quick("Go to main"))
        assertEquals(passed(TurnPosition.UPKEEP) + PlayAction.ChooseStep(TurnPosition.PRECOMBAT_MAIN), h.commands.single().playActions())
        assertOneRun(h, start)
    }

    @Test fun selectAttackersAndSkipCombatPassBeginningOfCombat() {
        val start = at(TurnPosition.PRECOMBAT_MAIN)
        val select = Harness(start, turnPlayer)
        assertTrue(select.quick("Select attackers"))
        assertEquals(passed(TurnPosition.BEGIN_COMBAT) + PlayAction.ChooseStep(TurnPosition.DECLARE_ATTACKERS),
            select.commands.single().playActions())
        assertTrue(select.toolkit.selectionState.value.attackSelection)
        assertOneRun(select, start)

        val skip = Harness(start, turnPlayer)
        assertTrue(skip.quick("Skip combat"))
        assertEquals(passed(TurnPosition.BEGIN_COMBAT, TurnPosition.DECLARE_ATTACKERS, TurnPosition.END_COMBAT,
            TurnPosition.POSTCOMBAT_MAIN) + PlayAction.ChooseStep(TurnPosition.END_STEP), skip.commands.single().playActions())
        assertEquals(before(start, turnPlayer, listOf(PlayAction.ChooseStep(TurnPosition.END_STEP))), skip.current().board)
        assertOneRun(skip, start)
    }

    @Test fun noAttackClosesTheEmptyAreaThenPassesEndOfCombat() {
        val start = at(TurnPosition.DECLARE_ATTACKERS, combat = CombatArea(id(90)))
        val h = Harness(start, turnPlayer)
        assertTrue(h.quick("No attack"))
        assertEquals(listOf<PlayAction>(PlayAction.Combat(CombatAction.Close(id(90)))) + passed(TurnPosition.END_COMBAT) +
            PlayAction.ChooseStep(TurnPosition.POSTCOMBAT_MAIN), h.commands.single().playActions())
        assertEquals(before(start, turnPlayer, listOf(PlayAction.Combat(CombatAction.Close(id(90))),
            PlayAction.ChooseStep(TurnPosition.POSTCOMBAT_MAIN))), h.current().board)
        assertOneRun(h, start)
    }

    @Test fun singleStepActionsAreUnchanged() {
        val h = Harness(at(TurnPosition.PRECOMBAT_MAIN), turnPlayer)
        assertTrue(h.toolkit.perform(TableActionIntent.Next(h.scope)))
        assertEquals(listOf<PlayAction>(PlayAction.NextStep), h.commands.single().playActions())
        val end = Harness(at(TurnPosition.CLEANUP), turnPlayer)
        assertTrue(end.toolkit.perform(TableActionIntent.End(end.scope)))
        assertEquals(listOf<PlayAction>(PlayAction.EndTurn), end.commands.single().playActions())
    }
}
