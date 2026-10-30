package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class CompleteCoreIntegrationTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"

    @Test fun explorerUsesMergedGroupsAndLastEventGlyphForManualTokenAndStackFamilies() {
        val definition = TokenCatalog.blank
        val root = PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))), listOf(PlayCard(id(9), definition.digest,
                id(7), id(10), definition.definition.faces.single().faceId,
                placement = PlayPlacement(BoardPosition(0, 0), id(15)))),
                listOf(PlayContainer(id(10), PlayZone.BATTLEFIELD, id(7), listOf(id(9))),
                    PlayContainer(id(11), PlayZone.STACK)), TurnState(id(7))),
            listOf(definition), emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))
        var tree = GameplayTree(root)
        var n = 100
        fun step(action: PlayAction): HistoryRange {
            val command = GameCommand(id(n++), GameScope(root.gameId, playId = root.id), GameAction.Play(action), id(80), "1")
            tree = assertNotNull(tree.play(id(7), command).tree)
            val range = HistoryGraphLayout.build(tree).ranges.last()
            val last = tree.nodes.getValue(tree.currentNodeId)
            val icon = last.grouping!!.icon
            assertEquals(last.id, range.representativeId)
            assertEquals(last.id, range.targetId)
            assertEquals(icon, range.icon)
            return range
        }
        val tap = step(PlayAction.SetTapped(id(9), true))
        val view = step(PlayAction.InspectFace(id(9), root.board.cards.single().faceId, id(10)))
        assertEquals(tap.id, view.id)
        assertEquals(HistoryIcon.VISIBILITY, view.icon)
        val creation = step(PlayAction.CloneToken(id(9)))
        val edit = step(PlayAction.SetNote(id(9), "customization"))
        assertEquals(creation.id, edit.id)
        assertNotEquals(creation.representativeId, edit.representativeId)
        assertEquals(HistoryIcon.STICKY_NOTE_2, edit.icon)
        val stack = step(PlayAction.Stack(StackAction.AddEffect(id(9), GameplayRules.stackDigest(tree.stateAt(tree.currentNodeId)!!))))
        assertEquals(HistoryIcon.LAYERS, stack.icon)
        assertNotEquals(edit.id, stack.id)
        assertEquals(tree.nodes.size + 1, HistoryGraphLayout.build(tree).ranges.sumOf { it.nodeIds.size })
    }
}
