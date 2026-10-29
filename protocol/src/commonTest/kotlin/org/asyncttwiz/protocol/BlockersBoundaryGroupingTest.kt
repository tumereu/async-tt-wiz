package org.asyncttwiz.protocol

import kotlin.test.*

/** Rules v29: a step change into Declare blockers is its own history group, so the defender can continue from it. */
class BlockersBoundaryGroupingTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val a = id(7)
    private val b = id(8)
    private fun state(position: TurnPosition) = PlayState(id(1), id(2), id(3), id(4),
        PlayRoot(id(5), id(6)), id(5), BoardState(listOf(PlayPlayer(a, life = "20"), PlayPlayer(b, life = "20")), emptyList(), emptyList(),
            TurnState(a, phase = position.phase, step = position.step)),
        emptyList(), emptyList(), InitialStarter(StarterChoice(a), a))
    private fun play(action: PlayAction, n: Int) = GameCommand(id(n), GameScope(id(1), playId = id(2)), GameAction.Play(action), id(9), "1")

    private fun step(from: TurnPosition, to: TurnPosition, version: Int) = GameplayTransition(listOf(
        GameplayFieldChange(id(2), GameplayField.PHASE, from.phase, to.phase),
        GameplayFieldChange(id(2), GameplayField.STEP, from.step, to.step)), rulesVersion = version)
    private fun classify(action: PlayAction, from: TurnPosition, to: TurnPosition, version: Int) =
        HistoryGrouping.classify(action, emptyList(), step(from, to, version))
    private fun append(group: GameplayGroup?, semantic: HistorySemantic, n: Int, actor: String = a) =
        HistoryGrouping.append(group, id(n), id(n + 1000), actor, semantic)

    @Test fun rulesVersionIs29() {
        assertEquals(29, HistoryGrouping.BLOCKERS_BOUNDARY_RULES_VERSION)
        assertTrue(CURRENT_GAMEPLAY_RULES_VERSION >= HistoryGrouping.BLOCKERS_BOUNDARY_RULES_VERSION)
    }

    @Test fun v29StepIntoDeclareBlockersIsIsolatedAndV28GroupsExactlyAsBefore() {
        val edit = HistoryGrouping.classify(PlayAction.AdjustLife(a, "1"), emptyList(), GameplayTransition(rulesVersion = 29))
        for (action in listOf(PlayAction.NextStep, PlayAction.ChooseStep(TurnPosition.DECLARE_BLOCKERS))) {
            for (from in listOf(TurnPosition.DECLARE_ATTACKERS, TurnPosition.PRECOMBAT_MAIN)) {
                val old = classify(action, from, TurnPosition.DECLARE_BLOCKERS, 28)
                assertFalse(old.isolated)
                assertFalse("isolated" in GameWire.json.encodeToString(HistorySemantic.serializer(), old))
                val previous = append(null, edit, 100)
                // v28: the turn player's step change joins their preceding group, as it always did.
                assertEquals(previous.id, append(previous, old, 101).id)
                val new = classify(action, from, TurnPosition.DECLARE_BLOCKERS, 29)
                assertTrue(new.isolated, "$action from $from")
                assertEquals(old.copy(isolated = true), new)
                val boundary = append(previous, new, 101)
                assertNotEquals(previous.id, boundary.id)
                assertEquals(id(101), boundary.firstNodeId)
                // Nothing joins it: the author's next step change starts another group.
                val next = classify(PlayAction.ChooseStep(TurnPosition.COMBAT_DAMAGE), TurnPosition.DECLARE_BLOCKERS,
                    TurnPosition.COMBAT_DAMAGE, 29)
                assertFalse(next.isolated)
                assertNotEquals(boundary.id, append(boundary, next, 102).id)
                assertNotEquals(boundary.id, append(boundary, HistoryGrouping.classify(PlayAction.PassAttention), 102, b).id)
            }
        }
        // Other step targets keep joining in v29.
        for (to in TurnPosition.entries.filter { it != TurnPosition.DECLARE_BLOCKERS && it != TurnPosition.DECLARE_ATTACKERS }) {
            val previous = append(null, edit, 100)
            val semantic = classify(PlayAction.ChooseStep(to), TurnPosition.DECLARE_ATTACKERS, to, 29)
            assertFalse(semantic.isolated, "$to")
            assertEquals(previous.id, append(previous, semantic, 101).id)
        }
    }

    @Test fun assumeNoBlocksStepsAreTwoGroupsAndTheDefenderContinuesFromDeclareBlockers() {
        var tree = GameplayTree(state(TurnPosition.DECLARE_ATTACKERS))
        tree = assertNotNull(tree.play(a, play(PlayAction.AdjustLife(a, "1"), 20)).tree)
        val attacks = tree.currentNodeId
        tree = assertNotNull(tree.play(a, play(PlayAction.ChooseStep(TurnPosition.DECLARE_BLOCKERS), 21)).tree)
        val blockers = tree.currentNodeId
        tree = assertNotNull(tree.play(a, play(PlayAction.ChooseStep(TurnPosition.COMBAT_DAMAGE), 22)).tree)
        val damage = tree.currentNodeId
        val groups = listOf(attacks, blockers, damage).map { assertNotNull(tree.stateAt(it)!!.currentGroup) }
        assertEquals(3, groups.map { it.id }.distinct().size)
        // Declare blockers is a complete group of its own: a timeline endpoint.
        assertEquals(blockers, groups[1].firstNodeId)
        assertEquals(blockers, groups[1].lastNodeId)
        val boundary = tree.stateAt(blockers)!!
        assertEquals(TurnPosition.DECLARE_BLOCKERS, TurnPosition.of(boundary.board.turn))
        assertEquals(b, boundary.board.effectiveAttentionHolder)
        // The defender continues from there and plays on a new branch.
        val continued = tree.play(b, play(PlayAction.AdjustLife(b, "-2"), 23).copy(continuation = Continuation(blockers, true)))
        assertNull(continued.reason)
        val branched = assertNotNull(continued.tree)
        val node = branched.nodes.getValue(branched.currentNodeId)
        assertEquals(blockers, node.parentId)
        assertEquals(TurnPosition.DECLARE_BLOCKERS, TurnPosition.of(branched.stateAt(branched.currentNodeId)!!.board.turn))
        assertEquals("18", branched.stateAt(branched.currentNodeId)!!.board.players.single { it.accountId == b }.life)
        // Cold replay reproduces the same groups.
        assertEquals(groups, listOf(attacks, blockers, damage).map { tree.copy().stateAt(it)!!.currentGroup })
    }
}
