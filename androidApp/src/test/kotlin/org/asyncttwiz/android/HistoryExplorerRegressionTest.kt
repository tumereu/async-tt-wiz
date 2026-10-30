package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.concurrent.Executor
import kotlin.test.*

class HistoryExplorerRegressionTest {
    private val f = DownloadedTreeFixture
    private val a = f.a
    private var sequence = 1000
    private fun command(tree: GameplayTree, action: GameAction, continuation: String? = null) =
        GameCommand(f.id(sequence++), GameScope(tree.initial.gameId, playId = tree.initial.id), action, f.id(14), "1",
            continuation = continuation?.let { Continuation(it, true) })
    private fun change(tree: GameplayTree, action: GameAction, continuation: String? = null): GameplayTree {
        val result = HistoryContinuation.change(tree, a, command(tree, action, continuation))
        return assertNotNull(result.tree, "$action: ${result.reason}")
    }
    private fun play(tree: GameplayTree, action: PlayAction, continuation: String? = null) =
        change(tree, GameAction.Play(action), continuation)

    private fun openEveryBoundary(tree: GameplayTree) {
        for (hidden in listOf(false, true)) {
            val failures = mutableListOf<Throwable>()
            val explorer = HistoryExplorerController(Executor { it.run() }, { it() },
                { _, failure -> failures.add(failure) })
            try {
                explorer.update(tree.copy(), showHidden = hidden)
                assertNull(explorer.state.error, "Opening ${tree.currentNodeId}: ${failures.lastOrNull()}")
                for (id in tree.nodes.keys + tree.initial.root.id) {
                    explorer.select(id)
                    assertNull(explorer.state.error, "Selecting $id: ${tree.nodes[id]?.action}")
                    assertEquals(tree.stateAt(id), explorer.state.reviewed)
                    assertTrue(id in explorer.state.graph!!.positions)
                }
            } finally { explorer.close() }
        }
    }

    @Test fun firstTurnIncludingEmptyHistoryAndDrawsReordersRevealsAndTurnTracking() {
        var tree = f.root(PlayZone.LIBRARY).let { root ->
            val initial = root.initial
            val cards = (100..159).map { initial.board.cards.first().copy(id = f.id(it)) }
            GameplayTree(initial.copy(board = initial.board.copy(cards = cards + initial.board.cards.last(),
                containers = initial.board.containers.map { if (it.accountId == a) it.copy(cards = cards.map { c -> c.id }) else it } +
                    PlayContainer(f.id(20), PlayZone.HAND, a))))
        }
        openEveryBoundary(tree)
        val library = f.id(10); val hand = f.id(20)
        LibraryActions.draw(tree.initial.board, library, hand, 7).forEach { tree = play(tree, it) }
        openEveryBoundary(tree)
        val before = tree.stateAt(tree.currentNodeId)!!
        val members = before.board.containers.single { it.id == hand }.cards
        tree = play(tree, PlayAction.MoveCard(members.last(), MoveSource(hand),
            MoveDestination(hand, MoveInsertion.GAP, beforeId = members.first())))
        tree = play(tree, PlayAction.ShowContents(library, ContentsSelection.TOP, 2, reveal = true))
        tree = play(tree, PlayAction.NextStep)
        tree = play(tree, PlayAction.EndTurn)
        openEveryBoundary(tree)
    }

    @Test fun undoRedoSelectiveUndoAndContinuingFromEarlierBoundary() {
        var tree = play(f.root(), PlayAction.SetTapped(f.id(9), true))
        tree = change(tree, GameAction.Undo); openEveryBoundary(tree)
        tree = change(tree, GameAction.Redo); openEveryBoundary(tree)
        val fork = tree.currentNodeId
        tree = play(tree, PlayAction.AdjustLife(a, "1"))
        tree = play(tree, PlayAction.AdjustLife(a, "2"), fork)
        tree = change(tree, GameAction.Undo); openEveryBoundary(tree)
        tree = change(tree, GameAction.Redo); openEveryBoundary(tree)
        val remote = command(tree, GameAction.Play(PlayAction.AdjustLife(f.b, "1")))
        tree = assertNotNull(HistoryContinuation.change(tree, f.b, remote).tree)
        tree = change(tree, GameAction.Undo); openEveryBoundary(tree)
    }

    @Test fun stackProjectionsTextMarksCombatAndRetainedRevealForks() {
        openEveryBoundary(TableStatesFixture.history())
        val initial = f.root().initial
        val definition = initial.definitions.single().definition.let { it.copy(faces = it.faces.map { face ->
            face.copy(rulesText = "First ability\n\nSecond ability") }) }
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        var tree = GameplayTree(initial.copy(definitions = listOf(submitted), board = initial.board.copy(
            cards = initial.board.cards.map { it.copy(definition = submitted.digest,
                covered = false, placement = if (it.owner == a) PlayPlacement(BoardPosition(0, 0), f.id(30)) else null) },
            containers = initial.board.containers.map { if (it.accountId == a) it.copy(zone = PlayZone.BATTLEFIELD) else it } +
                PlayContainer(f.id(20), PlayZone.STACK))))
        val card = f.id(9)
        fun append(action: PlayAction) {
            tree = play(tree, action)
            openEveryBoundary(tree)
        }
        append(PlayAction.Combat(CombatAction.Open))
        val combat = tree.stateAt(tree.currentNodeId)!!.board.combat!!.id
        append(PlayAction.Combat(CombatAction.Declare(combat, card)))
        append(PlayAction.Combat(CombatAction.Close(combat)))
        append(PlayAction.Stack(StackAction.AddEffect(card, CombatStackRules.stackDigest(tree.stateAt(tree.currentNodeId)!!))))
        val effect = tree.stateAt(tree.currentNodeId)!!.board.stackEffects.single().id
        append(PlayAction.Stack(StackAction.MarkText(effect, 0)))
        append(PlayAction.Stack(StackAction.RemoveEffect(effect)))
    }

    @Test fun versionOneRetainedMovementWithoutOptionalGroupingCanBeReconstructedAndRendered() {
        val initial = f.root(PlayZone.LIBRARY).initial.let { it.copy(board = it.board.copy(containers = it.board.containers +
            PlayContainer(f.id(20), PlayZone.HAND, a))) }
        val tree = play(GameplayTree(initial), LibraryActions.draw(initial.board, f.id(10), f.id(20), 1).single())
        val legacy = tree.copy(nodes = tree.nodes.mapValues { (_, node) -> node.copy(grouping = null) })
        openEveryBoundary(legacy)
        val history = TableHistoryController(Executor { it.run() }, { it() }, { _, _ -> false }, { _, _, _ -> false })
        try {
            history.update(legacy, "legacy")
            val events = HistoryTimelineLayout.events(history.playback!!.tree, legacy.currentNodeId)
            assertEquals(HistoryIcon.DOWNLOAD, events.last().icon)
        } finally { history.close() }
    }

    @Test fun downloadedHistoryWithUndoRedoAndForkOpensCold() {
        var tree = play(f.root(), PlayAction.SetTapped(f.id(9), true))
        tree = play(tree, PlayAction.AdjustLife(a, "1"), tree.initial.root.id)
        tree = change(tree, GameAction.Undo)
        val transfer = f.transfer(TreeSync.generation(tree), chunkSize = 4096)
        val download = TreeDownload(TreeStoreFixture(), Thread("not-the-test-worker"))
        val staged = assertNotNull(download.stage(transfer.plan) { transfer.compressed[it].inputStream() })
        val published = download.publish(staged, emptyList(), 10) as TreePublication.Published
        openEveryBoundary(published.value.generation.tree)
        openEveryBoundary(change(published.value.generation.tree, GameAction.Redo))
    }

    @Test fun longGameWithAlternatingAuthorsAndTurnChangesOpensCold() {
        var tree = f.root()
        repeat(2100) { n ->
            val action = if (n % 20 == 0) PlayAction.EndTurn else PlayAction.AdjustLife(a, "1")
            val actor = if (n % 2 == 0) a else f.b
            tree = assertNotNull(tree.play(actor, command(tree, GameAction.Play(action))).tree)
        }
        val explorer = HistoryExplorerController(Executor { it.run() }, { it() })
        try {
            explorer.update(tree.copy(), showHidden = false)
            assertNull(explorer.state.error)
            assertEquals(tree.stateAt(tree.currentNodeId), explorer.state.reviewed)
            assertEquals(2101, explorer.state.graph!!.positions.size)
        } finally { explorer.close() }
    }
}
