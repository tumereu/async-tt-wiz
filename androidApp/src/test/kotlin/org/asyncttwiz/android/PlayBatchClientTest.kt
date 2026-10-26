package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class PlayBatchClientTest {
    private fun toolkit(f: BasicTableFixture) = TableToolkitController(f.actor, { f.play }, { true },
        f.lobby::gameplay, playBatch = f.lobby::gameplayBatch)

    @Test fun drawNClampsToRemainingCardsAndRejectionWithdrawsTheWholePrediction() {
        val f = BasicTableFixture(); f.connect()
        try {
            val ui = toolkit(f)
            val library = ui.libraryController(f.container(PlayZone.LIBRARY), f.container(PlayZone.HAND))!!
            assertEquals(LibrarySubmission(4, 0), library.draw(20))
            val command = f.commands().single()
            assertEquals(4, assertIs<GameAction.PlayBatch>(command.action).actions.size)
            assertEquals(4, f.play.board.containers.single { it.zone == PlayZone.HAND && it.accountId == f.actor }.cards.size)
            assertEquals(f.root, f.lobby.state.acceptedPlay)
            f.message(GameResult(command.requestId, command.scope, "rejected", "movementConflict", "1"))
            assertEquals(f.root.board, f.play.board)
            assertNotNull(f.lobby.state.connectionMessage)
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun declareAndTapAndMultiCardMoveEachSendOneOriginal() {
        val f = BasicTableFixture(); f.connect()
        try {
            val ui = toolkit(f)
            val ids = listOf(f.id(40), f.id(41))
            assertTrue(ui.handle(TableIntent.Move(TableScope(ids.first(), ids),
                TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 0), BasicTableController.sources(f.play, ids))))
            assertEquals(1, f.commands().size)
            assertEquals(2, assertIs<GameAction.PlayBatch>(f.commands().single().action).actions.size)
            assertTrue(ui.declare(ids, true))
            val batch = assertIs<GameAction.PlayBatch>(f.commands().last().action)
            assertEquals(5, batch.actions.size)
            assertEquals(2, f.commands().size)
            val tree = f.lobby.displayTree()!!
            val nodes = f.commands().last().playMembers().map { tree.nodes.getValue(CommandIds.uuidV5(it.requestId, "board-node")) }
            assertEquals(nodes.dropLast(1).map { it.id }, nodes.drop(1).map { it.parentId })
            assertTrue(nodes.all { it.invocationId == f.commands().last().requestId })
            assertTrue(f.play.board.cards.filter { it.id in ids }.all { it.tapped })
            assertTrue(f.lobby.historyControl(GameAction.Undo))
            assertNull(f.play.board.combat)
            assertTrue(f.play.board.cards.filter { it.id in ids }.none { it.tapped })
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun predictionAndConfirmationAnimateTheSameIndividualEventsOnlyOnce() {
        val f = BasicTableFixture()
        val changes = mutableListOf<TableChangeBatch>()
        val replica = GameplayReplica(changes::add)
        assertTrue(replica.root(f.data.head(f.root)))
        val actions = LibraryActions.draw(f.root.board, f.container(PlayZone.LIBRARY), f.container(PlayZone.HAND), 3)
        val command = f.data.command(actions.first()).copy(action = GameAction.PlayBatch(actions))
        assertTrue(replica.predict(command, f.actor))
        assertEquals(listOf(command.requestId), replica.pendingIds)
        assertEquals(3, changes.sumOf { it.events.size })
        val initial = GameplayTree(f.root)
        val accepted = assertNotNull(HistoryContinuation.change(initial, f.actor, command).tree)
        val revision = GameRevision(f.game.id, "1", "2", command.requestId, f.actor, command.scope,
            f.game.copy(revision = "2"), treeDelta = TreeSync.delta(initial, accepted))
        assertTrue(replica.apply(revision))
        assertTrue(replica.pendingIds.isEmpty())
        assertEquals(3, changes.sumOf { it.events.size })
        assertEquals(accepted.stateAt(accepted.currentNodeId), replica.display)
    }
}
