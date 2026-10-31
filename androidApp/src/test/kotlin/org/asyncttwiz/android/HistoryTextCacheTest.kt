package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.concurrent.Executor
import kotlin.test.*

/** Older events keep their board-aware texts: each is made when the client holds its before-board. */
class HistoryTextCacheTest {
    private val f = DownloadedTreeFixture
    private fun id(n: Int) = f.id(n)
    private val cardNames = listOf("Shock", "Opt", "Ponder", "Brainstorm")

    private class Harness : AutoCloseable {
        val work = ArrayDeque<Runnable>()
        val posts = ArrayDeque<() -> Unit>()
        val reviews = ArrayDeque<(DownloadedHistoryState) -> Unit>()
        val history = TableHistoryController(Executor { work.addLast(it) }, { posts.addLast(it) }, { _, complete ->
            reviews.addLast(complete); true
        }, { _, _, _ -> error("Review must not submit gameplay") })
        fun drain() { while (work.isNotEmpty() || posts.isNotEmpty()) { while (work.isNotEmpty()) work.removeFirst().run(); while (posts.isNotEmpty()) posts.removeFirst().invoke() } }
        override fun close() = history.close()
    }

    /** bob's Library holds four named cards (alice cannot see them); alice and bob each have a hand. */
    private fun root(): GameplayTree {
        val base = f.root(PlayZone.LIBRARY).initial
        val definitions = cardNames.mapIndexed { n, name -> CardDefinition(name, listOf(DefinitionFace(id(600 + n), name))) }
        val cards = definitions.mapIndexed { n, definition -> PlayCard(id(610 + n), DeckJson.digest(definition), f.b, id(13), id(600 + n)) }
        return GameplayTree(base.copy(definitions = base.definitions + definitions.map { SubmittedDefinition(DeckJson.digest(it), it) },
            board = base.board.copy(cards = base.board.cards.filter { it.containerId != id(13) } + cards,
                containers = base.board.containers.map { if (it.id == id(13)) it.copy(cards = cards.map(PlayCard::id)) else it } +
                    listOf(PlayContainer(id(20), PlayZone.HAND, f.a), PlayContainer(id(21), PlayZone.HAND, f.b)))))
    }
    private fun play(tree: GameplayTree, n: Int, action: PlayAction, actor: String = f.b) = assertNotNull(tree.play(actor,
        GameCommand(id(n), GameScope(tree.initial.gameId, playId = tree.initial.id), GameAction.Play(action), id(14), "1")).tree)
    private fun draw(tree: GameplayTree, card: Int, n: Int) = play(tree, n, PlayAction.MoveCard(id(610 + card),
        MoveSource(id(13), edge = MoveSourceEdge.TOP), MoveDestination(id(21), MoveInsertion.UNORDERED)))
    private fun control(tree: GameplayTree, action: GameAction, n: Int, actor: String = f.b) = assertNotNull(SelectiveUndo.change(tree, actor,
        GameCommand(id(n), GameScope(tree.initial.gameId, playId = tree.initial.id), action, id(14), "1")).tree)
    private fun game(tree: GameplayTree) = GameSummary(tree.initial.gameId, "Table", "2026-10-29T00:00:00Z",
        AccountIdentity(f.a, "alice"), AccountIdentity(f.b, "bob"), tree.initial.preparationRound, id(90), InvitationState.ACCEPTED)
    private fun texts(h: Harness, ids: List<String>) = ids.map { h.history.playback!!.tree.texts.getValue(it).description }

    // HIS-069
    @Test fun liveEventsKeepTheirMoveTextsAfterTheyAreNoLongerTheLatest() {
        for (viewer in listOf(f.a, f.b)) Harness().use { h ->
            var tree = root()
            val draws = mutableListOf<String>()
            h.history.update(tree, "v0", game(tree), viewer); h.drain()
            cardNames.indices.forEach { n ->
                tree = draw(tree, n, 700 + n); draws += tree.currentNodeId
                h.history.update(tree, "v${n + 1}", game(tree), viewer); h.drain()
            }
            tree = play(tree, 720, PlayAction.SetLife(f.a, "17"))
            h.history.update(tree, "v9", game(tree), viewer); h.drain()
            val expected = if (viewer == f.b) cardNames.map { "Drew $it" } else List(4) { "Drew a card" }
            assertEquals(expected, texts(h, draws), "viewer=$viewer")
            // The timeline strip reads the same texts.
            val strip = HistoryTimelineLayout.events(h.history.playback!!.tree, tree.currentNodeId)
            assertEquals(expected, strip.drop(1).take(4).map { it.description })
            // A fresh table generation without these boards would only say "A card moved".
            assertEquals("A card moved", TimelineTexts(tree.copy(), viewer, mapOf(f.a to "alice", f.b to "bob"))[draws.first()]?.description)
        }
    }

    @Test fun undoAndRedoKeepEarlierTextsAndRenameTheRedoneEvent() {
        Harness().use { h ->
            var tree = root()
            val draws = mutableListOf<String>()
            cardNames.indices.forEach { n ->
                tree = draw(tree, n, 700 + n); draws += tree.currentNodeId
                h.history.update(tree, "v$n", game(tree), f.b); h.drain()
            }
            val undone = control(tree, GameAction.Undo, 900)
            h.history.update(undone, "undo", game(tree), f.b); h.drain()
            assertEquals(cardNames.take(3).map { "Drew $it" }, texts(h, draws.take(3)))
            val redone = control(undone, GameAction.Redo, 901)
            assertEquals(draws.last(), redone.currentNodeId)
            h.history.update(redone, "redo", game(tree), f.b); h.drain()
            assertEquals(cardNames.map { "Drew $it" }, texts(h, draws))
        }
    }

    // HIS-069
    @Test fun playbackAndReviewGiveOlderEventsTheirTextsWithoutRebuildingFromTheStart() {
        // The draws are far behind the live end: only their own boards can tell what moved.
        var tree = root()
        val draws = cardNames.indices.map { n -> tree = draw(tree, n, 700 + n); tree.currentNodeId }
        repeat(HEAD_TEXT_WINDOW + 20) { n -> tree = play(tree, 1000 + n, PlayAction.SetLife(f.a, "${n + 1}"), f.a) }
        val session = tree.materializedCopy()
        Harness().use { h ->
            h.history.update(tree, "v1", game(tree), f.a); h.drain()
            val shown = h.history.playback!!.tree
            assertEquals(List(4) { "A card moved" }, texts(h, draws))
            assertEquals("Life ${HEAD_TEXT_WINDOW + 19} → ${HEAD_TEXT_WINDOW + 20}", shown.texts.getValue(tree.currentNodeId).description)
            fun review(node: String) {
                h.history.displayPosition()
                h.reviews.removeFirst()(DownloadedHistoryState(1L, node, state = session.stateAt(node)))
                h.drain()
            }
            h.history.select(draws[1]); review(draws[1])
            // The reviewed draw, the one before it (stepped back) and those after it (stepped forward).
            assertEquals(List(4) { "Drew a card" }, texts(h, draws))
            val source = assertNotNull(shown.source)
            assertEquals(0, source.work.rootMaterializations)
            assertEquals(0, source.work.replayedNodes)
            // Stepping on keeps every text; nothing older reverts to the generic wording.
            h.history.playback!!.next(); review(h.history.playback!!.position.cursorId)
            h.history.playback!!.next(); review(h.history.playback!!.position.cursorId)
            assertEquals(List(4) { "Drew a card" }, texts(h, draws))
            // A newer live event keeps the stored texts as well.
            val next = play(tree, 5000, PlayAction.SetLife(f.b, "3"))
            h.history.update(next, "v2", game(next), f.a); h.drain()
            assertEquals(List(4) { "Drew a card" }, texts(h, draws))
        }
    }

    @Test fun textBuilderUsesOnlyTheGivenBoardAndHidesUnseenCards() {
        var tree = root()
        val draws = cardNames.indices.map { n -> tree = draw(tree, n, 700 + n); tree.currentNodeId }
        val shownAt = tree.materializedCopy().stateAt(draws[2])!!
        // A cold copy has no boards at all: any board request would have to rebuild from the start.
        val cold = tree.copy()
        val names = mapOf(f.a to "alice", f.b to "bob")
        for (viewer in listOf(f.a, f.b)) {
            val made = historyTextsNear(cold, shownAt, HistoryTextCache(viewer, names), back = 8, forward = listOf(draws[3]), ahead = 8)
            assertEquals(draws.toSet(), made.keys)
            val subjects = if (viewer == f.b) cardNames else List(4) { "a card" }
            assertEquals(subjects.map { "Drew $it" }, draws.map { made.getValue(it).text.description })
        }
        assertEquals(0, cold.work.rootMaterializations)
        assertEquals(0, cold.work.replayedNodes)
        assertNull(cold.preparedState(draws[2]))
        // The walk back is bounded and stops at texts already made.
        val one = historyTextsNear(cold, shownAt, HistoryTextCache(f.b, names), back = 1)
        assertEquals(setOf(draws[2], draws[3]), one.keys)
        val early = tree.materializedCopy().stateAt(draws[1])!!
        val known = HistoryTextCache(f.b, names).with(historyTextsNear(cold, early, HistoryTextCache(f.b, names), back = 1))
        assertEquals(setOf(draws[3]), historyTextsNear(cold, tree.preparedState(tree.currentNodeId)!!, known, back = 8).keys)
    }

    @Test fun storedTextsDropForRewrittenEventsAndStayBounded() {
        var tree = root()
        val draws = cardNames.indices.map { n -> tree = draw(tree, n, 700 + n); tree.currentNodeId }
        val names = mapOf(f.a to "alice", f.b to "bob")
        val made = historyTextsNear(tree, tree.preparedState(tree.currentNodeId)!!, HistoryTextCache(f.b, names), back = 8)
        val cache = HistoryTextCache(f.b, names).with(made)
        assertEquals("Drew Shock", cache.text(tree, draws[0])?.description)
        // The same event id with a different record (e.g. a prediction the server answered differently).
        val node = tree.nodes.getValue(draws[0])
        val rewritten = tree.copy(nodes = tree.nodes + (node.id to node.copy(invocationId = id(999))))
        assertNull(cache.text(rewritten, draws[0]))
        assertNull(cache.text(tree.copy(nodes = tree.nodes - draws[0]), draws[0]))
        assertSame(cache, cache.forContext(f.b, names))
        assertEquals(0, cache.forContext(f.a, names).size)
        val bounded = HistoryTextCache(f.b, names, limit = 2).with(made)
        assertEquals(2, bounded.size)
        // The texts made first leave first.
        assertNull(bounded.text(tree, draws[3]))
        assertEquals("Drew Shock", bounded.text(tree, draws[0])?.description)
    }
}
