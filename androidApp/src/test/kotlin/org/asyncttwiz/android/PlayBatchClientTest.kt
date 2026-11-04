package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class PlayBatchClientTest {
    private fun toolkit(f: BasicTableFixture) = TableToolkitController(f.actor, { TableBoard.of(f.play) }, { true },
        f.lobby::gameplay, playBatch = f.lobby::gameplayBatch, playTree = f.lobby::displayTree)








    // SYS-009
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
