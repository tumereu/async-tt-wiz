package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class CompleteCoreIntegrationTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"

    @Test fun explorerUsesMergedPrefixRepresentativeAndLiteralGlyphForManualTokenAndStackFamilies() {
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
            val representative = tree.stateAt(tree.currentNodeId)!!.currentGroup!!.representative!!
            assertEquals(representative.nodeId, range.representativeId)
            assertEquals(representative.semantic.icon, range.glyph.icon)
            assertEquals(historyIconGlyph(representative.semantic.icon), range.glyph.symbol)
            return range
        }
        val tap = step(PlayAction.SetTapped(id(9), true))
        val view = step(PlayAction.InspectFace(id(9), root.board.cards.single().faceId, id(10)))
        assertEquals(tap.id, view.id)
        assertEquals(HistoryGlyph.INSPECT, view.glyph)
        val creation = step(PlayAction.CloneToken(id(9)))
        val edit = step(PlayAction.SetNote(id(9), "customization"))
        assertEquals(creation.representativeId, edit.representativeId)
        assertEquals(HistoryGlyph.CLONE, edit.glyph)
        val stack = step(PlayAction.Stack(StackAction.AddEffect(id(9), GameplayRules.stackDigest(tree.stateAt(tree.currentNodeId)!!))))
        assertEquals(HistoryGlyph.STACK, stack.glyph)
        assertNotEquals(edit.id, stack.id)
        assertEquals(tree.nodes.size + 1, HistoryGraphLayout.build(tree).ranges.sumOf { it.nodeIds.size })
    }
}
