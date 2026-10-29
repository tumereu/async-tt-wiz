package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

/** "Assume no blocks" and the once-only combat damage buttons. */
class CombatQuickActionsTest {
    private fun offerings(play: PlayState, context: QuickContext = QuickContext(viewer = play.board.turn.accountId)) =
        QuickActions.contextual(play, context, TableActionController({ play }, { true }, { true }, RecordingRoutes()))

    private fun at(play: PlayState, position: TurnPosition) =
        play.copy(board = play.board.copy(turn = play.board.turn.copy(phase = position.phase, step = position.step)))

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
        fun undo() {
            val change = SelectiveUndo.change(tree, actor, commands.last().copy(requestId = java.util.UUID.randomUUID().toString(),
                action = GameAction.Undo))
            assertNull(change.reason)
            tree = change.tree!!
        }
    }

    @Test fun assumeNoBlocksOnlyForTheTurnPlayerWithDeclaredAttackersAndNothingSelected() {
        val f = CombatDamageFixture()
        val declared = at(f.play, TurnPosition.DECLARE_ATTACKERS)
        val action = offerings(declared).single { it.name == QuickActions.ASSUME_NO_BLOCKS }
        assertEquals(CombatTool.ASSUME_NO_BLOCKS, assertIs<TableActionIntent.Combat>(action.intent).tool)
        assertTrue(action.enabled)
        assertEquals(listOf("Select more attackers", QuickActions.ASSUME_NO_BLOCKS), offerings(declared).map { it.name })
        assertTrue(offerings(declared, QuickContext(viewer = f.defender)).none { it.name == QuickActions.ASSUME_NO_BLOCKS })
        assertTrue(offerings(declared, QuickContext(viewer = f.actor, selectedSourceIds = listOf(f.attackerIds.single())))
            .none { it.name == QuickActions.ASSUME_NO_BLOCKS })
        val empty = declared.copy(board = declared.board.copy(combat = declared.board.combat!!.copy(pairs = emptyList())))
        assertEquals(listOf("Select attackers", "No attack"), offerings(empty).map { it.name })
        for (position in TurnPosition.entries.filter { it != TurnPosition.DECLARE_ATTACKERS })
            assertTrue(offerings(at(f.play, position)).none { it.name == QuickActions.ASSUME_NO_BLOCKS })
    }

    @Test fun assumeNoBlocksRecordsTwoSeparateStepsAndGroupsWithDeclareBlockersAsAnEndpoint() {
        val f = CombatDamageFixture()
        val h = Harness(at(f.play, TurnPosition.DECLARE_ATTACKERS), f.actor)
        assertTrue(h.toolkit.perform(offerings(h.current()).single { it.name == QuickActions.ASSUME_NO_BLOCKS }.intent))
        assertEquals(listOf(listOf<PlayAction>(PlayAction.ChooseStep(TurnPosition.DECLARE_BLOCKERS)),
            listOf<PlayAction>(PlayAction.ChooseStep(TurnPosition.COMBAT_DAMAGE))), h.commands.map { it.playActions() })
        // Two events with their own invocations: not one atomic batch.
        val nodes = h.tree.nodes.values.toList()
        assertEquals(2, nodes.size)
        assertEquals(2, nodes.map { it.invocationId }.distinct().size)
        assertEquals(TurnPosition.COMBAT_DAMAGE, TurnPosition.of(h.current().board.turn))
        val blockers = h.tree.nodes.getValue(h.tree.nodes.getValue(h.tree.currentNodeId).parentId)
        val boundary = requireNotNull(h.tree.stateAt(blockers.id))
        assertEquals(TurnPosition.DECLARE_BLOCKERS, TurnPosition.of(boundary.board.turn))
        assertEquals(f.defender, boundary.board.attentionHolder)
        // Rules v29: Declare blockers is its own history group, a timeline endpoint the defender can continue from.
        assertNotEquals(boundary.currentGroup!!.id, h.current().currentGroup!!.id)
        assertEquals(blockers.id, boundary.currentGroup!!.lastNodeId)
        // One undo returns to Declare blockers, not to Declare attackers.
        h.undo()
        assertEquals(TurnPosition.DECLARE_BLOCKERS, TurnPosition.of(h.current().board.turn))
    }

    @Test fun singleDamageButtonLocksAfterItsDamageAndUndoUnlocksIt() {
        val f = CombatDamageFixture(blockers = listOf("2/2"), note = "Trample Lifelink")
        val h = Harness(f.play, f.actor)
        fun buttons() = combatDamageQuickActions(h.current(), h.toolkit.combatDamageEdits(h.current())).associate { it.name to it.enabled }
        assertEquals(mapOf("Deal combat damage" to true, "Close + next" to true), buttons())
        assertTrue(h.toolkit.perform(offerings(h.current()).single { it.name == "Deal combat damage" }.intent))
        assertEquals(1, h.toolkit.combatDamageEdits(h.current()))
        assertEquals(mapOf("Deal combat damage" to false, "Close + next" to true), buttons())
        val context = toolkitQuickContext(h.current(), TableSelection(), null, f.actor)
            .copy(combatDamageEdits = h.toolkit.combatDamageEdits(h.current()))
        assertFalse(offerings(h.current(), context).single { it.name == "Deal combat damage" }.enabled)
        h.undo()
        assertEquals(0, h.toolkit.combatDamageEdits(h.current()))
        assertEquals(mapOf("Deal combat damage" to true, "Close + next" to true), buttons())
    }

    @Test fun splitDamageLocksFirstStrikeAfterOneAndRegularAfterTwo() {
        val f = CombatDamageFixture(attackers = listOf("3/3", "2/2"), keyword = "Double strike")
        val h = Harness(f.play, f.actor)
        fun buttons() = combatDamageQuickActions(h.current(), h.toolkit.combatDamageEdits(h.current())).map { it.enabled }
        assertEquals(listOf(true, true, true), buttons())
        assertTrue(h.toolkit.perform(offerings(h.current()).single { it.name == "First strike damage" }.intent))
        assertEquals(listOf(false, true, true), buttons())
        assertTrue(h.toolkit.perform(offerings(h.current()).single { it.name == "Regular damage" }.intent))
        assertEquals(2, h.toolkit.combatDamageEdits(h.current()))
        assertEquals(listOf(false, false, true), buttons())
        h.undo()
        assertEquals(listOf(false, true, true), buttons())
    }

    // The counting rule itself, on hand-made events.
    private val area = "area"
    private val combatCard = "attacker"
    private val board = TableToolFixture.play(TurnPosition.COMBAT_DAMAGE).let { play ->
        play.copy(board = play.board.copy(combat = CombatArea(area, listOf(CombatPair("pair",
            attackers = listOf(CombatOccurrence("view", combatCard)), attackTarget = "walker")))))
    }

    private class Events(val play: PlayState) {
        val nodes = linkedMapOf<String, GameplayMutation>()
        private var head = "root"
        fun add(action: PlayAction, invocation: String? = null, transition: GameplayTransition = GameplayTransition()) {
            val id = "n${nodes.size}"
            nodes[id] = GameplayMutation(id, head, "branch", "r$id", "author", invocation, action, transition)
            head = id
        }
        fun count(limit: Int = 50) = CombatDamage.recentDamageEdits(play.copy(currentNodeId = head), nodes::get, limit)
    }

    private fun step(from: TurnPosition, to: TurnPosition) = GameplayTransition(listOf(
        GameplayFieldChange("play", GameplayField.PHASE, from.phase, to.phase),
        GameplayFieldChange("play", GameplayField.STEP, from.step, to.step)))

    @Test fun countsDamageInvocationsOfThisCombatsDamageStepOnly() {
        val e = Events(board)
        e.add(PlayAction.Combat(CombatAction.Open), transition = GameplayTransition(combatStack = CombatStackTransition(CombatAreaChange(null, area))))
        e.add(PlayAction.ChooseStep(TurnPosition.DECLARE_BLOCKERS), transition = step(TurnPosition.DECLARE_ATTACKERS, TurnPosition.DECLARE_BLOCKERS))
        // Damage before the damage step does not count.
        e.add(PlayAction.AdjustDamage(combatCard, "1"), "early")
        e.add(PlayAction.ChooseStep(TurnPosition.COMBAT_DAMAGE), transition = step(TurnPosition.DECLARE_BLOCKERS, TurnPosition.COMBAT_DAMAGE))
        assertEquals(0, e.count())
        // One batch of several edits is one press.
        e.add(PlayAction.AdjustDamage(combatCard, "2"), "press1")
        e.add(PlayAction.SetCorner("walker", "1"), "press1")
        e.add(PlayAction.AdjustLife("defender", "-3"), "press1")
        assertEquals(1, e.count())
        // Edits on cards outside combat, or other kinds of edits, are not damage presses.
        e.add(PlayAction.AdjustDamage("bystander", "1"), "other")
        e.add(PlayAction.SetTapped(combatCard, true), "tap")
        assertEquals(1, e.count())
        e.add(PlayAction.AdjustLife("defender", "-1"), "press2")
        assertEquals(2, e.count())
        // The walk is bounded: only the most recent events are read.
        assertEquals(1, e.count(limit = 1))
    }

    @Test fun anEarlierCombatsDamageNeverCounts() {
        val e = Events(board)
        e.add(PlayAction.AdjustDamage(combatCard, "2"), "old combat")
        e.add(PlayAction.Combat(CombatAction.Open), transition = GameplayTransition(combatStack = CombatStackTransition(CombatAreaChange(null, area))))
        assertEquals(0, e.count())
        assertEquals(0, CombatDamage.recentDamageEdits(board.copy(board = board.board.copy(combat = null)), e.nodes::get))
    }
}
