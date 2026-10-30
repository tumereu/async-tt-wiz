package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class CombatDamageActionsTest {
    private fun offerings(play: PlayState, viewer: String = play.board.turn.accountId): List<QuickAction> =
        QuickActions.contextual(play, QuickContext(viewer = viewer),
            TableActionController({ play }, { true }, { true }, RecordingRoutes()))

    @Test fun damageQuickOfferingsOnlyAtDamageStepForTurnPlayerAndSplitForEitherSidesKeywords() {
        for (keywords in listOf("", "First strike", "Double strike")) {
            val f = CombatDamageFixture(keyword = keywords)
            assertEquals(if (keywords.isEmpty()) listOf("Deal combat damage", "Close + next") else
                listOf("First strike damage", "Regular damage", "Close + next"), offerings(f.play).map { it.name })
            assertTrue(offerings(f.play, f.defender).isEmpty())
            for (position in TurnPosition.entries.filter { it != TurnPosition.COMBAT_DAMAGE }) {
                val otherStep = f.play.copy(board = f.play.board.copy(turn = f.play.board.turn.copy(phase = position.phase, step = position.step)))
                assertTrue(offerings(otherStep).none { (it.intent as? TableActionIntent.Combat)?.tool in setOf(
                    CombatTool.DEAL_DAMAGE, CombatTool.FIRST_STRIKE_DAMAGE, CombatTool.REGULAR_DAMAGE, CombatTool.DAMAGE_CLOSE_AND_NEXT) })
            }
        }
        val blocker = CombatDamageFixture(blockers = listOf("2/2"))
        val blockerStrike = blocker.play.copy(board = blocker.play.board.copy(cards = blocker.play.board.cards.map {
            if (it.id in blocker.blockerIds) it.copy(eotNote = "First strike") else it
        }))
        assertEquals(listOf("First strike damage", "Regular damage", "Close + next"), offerings(blockerStrike).map { it.name })
    }

    @Test fun quickDamageAndCloseEachSendOneBatchAndUndoAllTheirEditsTogether() {
        val f = CombatDamageFixture(blockers = listOf("2/2"), note = "Trample Lifelink")
        var tree = GameplayTree(f.play)
        val commands = mutableListOf<GameCommand>()
        fun current() = requireNotNull(tree.stateAt(tree.currentNodeId))
        fun enqueue(actions: List<PlayAction>, request: String?): Boolean {
            val command = GameCommand(request!!, GameScope(f.play.gameId, playId = f.play.id),
                GameAction.PlayBatch(actions), f.play.id, "0")
            val change = PlayBatches.change(tree, f.actor, command)
            assertNull(change.reason)
            tree = change.tree!!
            commands += command
            return true
        }
        val toolkit = TableToolkitController(f.actor, ::current, { true }, { action, request -> enqueue(listOf(action), request) },
            playBatch = ::enqueue, playTree = { tree })
        toolkit.selectionState.value = TableSelection(setOf(f.attackerViews.single()), TableMode.SELECT)
        val initial = current().board
        assertTrue(toolkit.perform(offerings(current()).single { it.name == "Deal combat damage" }.intent))
        assertEquals(1, commands.size)
        assertEquals(4, commands.single().playActions().size) // Both creature marks, life loss and lifelink.
        assertEquals("2", current().board.cards.single { it.id == f.attackerIds.single() }.damage)
        assertEquals("2", current().board.cards.single { it.id == f.blockerIds.single() }.damage)
        assertEquals("17", current().board.players.single { it.accountId == f.defender }.life)
        assertEquals("25", current().board.players.single { it.accountId == f.actor }.life)
        assertEquals(initial.combat, current().board.combat)
        assertEquals(TableSelection(), toolkit.selectionState.value)
        assertEquals(setOf(commands.single().requestId), tree.nodes.values.map { it.invocationId }.toSet())
        assertEquals("4", current().currentGroup!!.count)
        val undo = SelectiveUndo.change(tree, f.actor, commands.single().copy(requestId = f.id(500), action = GameAction.Undo))
        assertNull(undo.reason)
        tree = undo.tree!!
        assertEquals(initial, current().board)

        assertTrue(toolkit.perform(offerings(current()).single { it.name == "Close + next" }.intent))
        assertEquals(2, commands.size)
        // Rules v32: End of combat is recorded on the way; the close comes first so it all reads as one group.
        assertEquals(listOf(PlayAction.Combat(CombatAction.Close(f.id(90))),
            PlayAction.ChooseStep(TurnPosition.END_COMBAT, through = true), PlayAction.ChooseStep(TurnPosition.POSTCOMBAT_MAIN)),
            commands.last().playActions())
        assertEquals(1, tree.nodes.values.filter { it.invocationId == commands.last().requestId }
            .map { tree.stateAt(it.id)!!.currentGroup!!.id }.toSet().size)
        assertNull(current().board.combat)
        assertEquals(TurnPosition.POSTCOMBAT_MAIN, TurnPosition.of(current().board.turn))
        val undoClose = SelectiveUndo.change(tree, f.actor, commands.last().copy(requestId = f.id(501), action = GameAction.Undo))
        assertNull(undoClose.reason)
        assertEquals(initial, undoClose.tree!!.stateAt(undoClose.tree!!.currentNodeId)!!.board)
    }

    @Test fun projectionMenuUsesCapturedProjectionSelectionAndManualNotesAreFloating() {
        val f = CombatDamageFixture(attackers = listOf("*/2", "3/3"), blockers = listOf("1/2"))
        val submitted = mutableListOf<List<PlayAction>>()
        val toolkit = TableToolkitController(f.actor, { f.play }, { true }, { action, _ -> submitted += listOf(action); true },
            playBatch = { actions, _ -> submitted += actions; true })
        toolkit.openCard(TableScope(f.attackerViews.first(), f.allViews))
        val tools = combatDamageMenuTools(toolkit, f.play, toolkit.capture!!, true)
        assertEquals(listOf("Deal to target", "Deal to target or creatures"), tools.map { it.name })
        tools.last().click()
        assertEquals(1, submitted.size)
        assertEquals(setOf(f.blockerIds.single(), f.attackerIds.first()),
            submitted.single().filterIsInstance<PlayAction.AdjustDamage>().map { it.cardId }.toSet())
        assertEquals("Combatant: assign manually", toolkit.error)
        assertNull(toolkit.capture)
        val sourceBound = toolkit.bind(TableScope(f.attackerIds.last(), f.attackerIds))!!
        // The real cards deal through their own combat copies.
        assertTrue(combatDamageMenuTools(toolkit, f.play, sourceBound, true).all { it.enabled })
    }

    @Test fun firstStrikeQuickActionFiltersDealersButNotRecipientsAndThenTurnsOffMultiselect() {
        val f = CombatDamageFixture(blockers = listOf("2/2"), note = "First strike")
        val sent = mutableListOf<PlayAction>()
        val toolkit = TableToolkitController(f.actor, { f.play }, { true }, { action, _ -> sent += action; true },
            playBatch = { actions, _ -> sent += actions; true })
        toolkit.selectionState.value = TableSelection(f.allViews.toSet(), TableMode.SELECT)
        assertTrue(toolkit.perform(offerings(f.play).single { it.name == "First strike damage" }.intent))
        assertEquals(listOf<PlayAction>(PlayAction.AdjustDamage(f.blockerIds.single(), "5")), sent)
        assertEquals(TableSelection(), toolkit.selectionState.value)
        sent.clear()
        assertTrue(toolkit.perform(offerings(f.play).single { it.name == "Regular damage" }.intent))
        assertEquals(listOf<PlayAction>(PlayAction.AdjustDamage(f.attackerIds.single(), "2")), sent)
    }

    @Test fun staleAreaAndFailedSubmissionDoNotClearSelectionOrPartiallySend() {
        val f = CombatDamageFixture(blockers = listOf("2/2"))
        var calls = 0
        val toolkit = TableToolkitController(f.actor, { f.play }, { true }, { _, _ -> calls++; false },
            playBatch = { _, _ -> calls++; false })
        val selection = TableSelection(f.allViews.toSet(), TableMode.SELECT)
        toolkit.selectionState.value = selection
        val intent = offerings(f.play).first().intent as TableActionIntent.Combat
        assertFalse(toolkit.perform(intent.copy(areaId = f.id(999))))
        assertEquals(0, calls)
        assertFalse(toolkit.perform(intent))
        assertEquals(1, calls)
        assertEquals(selection, toolkit.selectionState.value)
    }
}
