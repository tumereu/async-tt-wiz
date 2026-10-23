package org.asyncttwiz.protocol

import kotlinx.serialization.json.JsonObject
import kotlin.test.*

class R1ConsolidationTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"

    @Test fun endingSuggestionRetainsManualTuplesThroughFlatTreeDownloadAndReplay() {
        val actor = id(1); val other = id(2); val root = PlayRoot(id(3), id(4))
        val initial = PlayState(id(5), id(6), id(7), id(8), root, root.id,
            BoardState(listOf(PlayPlayer(actor), PlayPlayer(other)), emptyList(), emptyList(), TurnState(actor)),
            emptyList(), emptyList(), InitialStarter(StarterChoice(actor), actor))
        var tree = GameplayTree(initial)
        val actions = listOf(PlayAction.SetLife(actor, "\u0085 999999999999999999999999999999 \u00a0"),
            PlayAction.AdjustLife(actor, "1"), PlayAction.SuggestResult(SuggestedResult.DRAW))
        actions.forEachIndexed { index, action ->
            val command = GameCommand(id(100 + index), GameScope(initial.gameId, playId = initial.id),
                GameAction.Play(action), id(9), "1")
            tree = assertNotNull(tree.play(actor, command).tree)
        }
        val accepted = assertNotNull(tree.stateAt(tree.currentNodeId))
        assertEquals("1000000000000000000000000000000", accepted.board.players.first().life)
        assertEquals(SuggestedResult.DRAW, accepted.board.resultSuggestion!!.outcome)
        assertEquals(HistoryCategory.S.key, accepted.currentGroup!!.category)
        assertEquals("1", accepted.currentGroup!!.count)
        assertEquals(HistorySignificance.ENDING_SUGGESTION, accepted.currentGroup!!.representative!!.semantic.significance)
        val generation = DownloadedTree(tree, mapOf(root.branchId to JsonObject(emptyMap())))
        val records = TreeSync.records(generation).toList()
        assertEquals(3, records.count { it.kind == "node" })
        val downloaded = TreeSync.decode(records.asSequence(), initial.gameId, initial.id)
        assertEquals(accepted, downloaded.tree.stateAt(downloaded.tree.currentNodeId))
        assertEquals(initial, downloaded.tree.stateAt(root.id))
        assertEquals(tree.nodes, downloaded.tree.nodes)
    }
}
