package org.asyncttwiz.protocol.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * I-D54 phase 2: a history tree with checkpoints gives the state at any event as the state reached directly, from the
 * nearest checkpoint (never from the root: owner rule, games reach 10k+ events), and moves a state at hand between
 * branches by reverting and applying.
 */
class GameHistoryTest {
    private class Tree(seed: Long, every: Int) {
        val games = EngineGames(seed)
        val store = InMemoryHistory("root", games.codec.encodeModel(games.model()))
        val history = GameHistory(store, games.codec, every)
        /** The state reached directly at every event. */
        val direct = mutableMapOf<String, GameModel>()

        /** Plays [events] events from [from] (whose state is [model]), storing them with their checkpoints. */
        fun grow(model: GameModel, from: String, events: Int, prefix: String): List<String> =
            games.play(model, from, events, prefix).map { (event, state) ->
                val depth = store.depthOfParent(event) + 1
                store.add(event, history.checkpointFor(depth, state))
                direct[event.id] = state
                event.id
            }
    }

    // SYS-026: a state comes from the nearest checkpoint and the events after it, never from the start
    @Test
    fun theStateAtAnyEventFromItsNearestCheckpointIsTheStateReachedDirectly() {
        val tree = Tree(11, every = 16)
        val main = tree.grow(tree.games.model(), "root", 320, "m")
        // Branches off the main line at three points, each with its own later events
        val branches = listOf(40, 130, 250).filter { it < main.size }.flatMapIndexed { i, at ->
            val from = main[at]
            tree.games.rnd.nextInt() // the branch differs from the main line by the random choices that follow
            tree.grow(tree.direct.getValue(from).copy(), from, 60, "b$i.")
        }
        val all = main + branches
        assertTrue(main.size >= 200 && branches.size >= 100, "${main.size} main events, ${branches.size} on branches")
        assertTrue(tree.store.checkpointCount < 2 + all.size / 16 + 3, "${tree.store.checkpointCount} checkpoints")
        for (id in all) {
            val stepsBefore = tree.history.steps
            val state = tree.history.stateAt(id)
            assertTrue(state.sameAs(tree.direct.getValue(id)), "state at $id")
            assertTrue(tree.history.steps - stepsBefore < 16, "$id: at most every − 1 events applied")
        }
    }

    @Test
    fun aStateAtHandMovesBetweenBranchesExactly() {
        val tree = Tree(12, every = 32)
        val main = tree.grow(tree.games.model(), "root", 200, "m")
        val branch = tree.grow(tree.direct.getValue(main[120]).copy(), main[120], 40, "b.")
        val all = main + branch
        val rnd = Random(7)
        var at = all.random(rnd)
        var model = tree.history.stateAt(at)
        repeat(150) {
            // Mostly nearby moves (undo, redo, a branch's sibling), sometimes far ones
            val to = if (rnd.nextInt(4) == 0) all.random(rnd) else all[(all.indexOf(at) + rnd.nextInt(-6, 7)).coerceIn(all.indices)]
            val before = tree.history.steps
            model = tree.history.move(model, at, to)
            assertTrue(model.sameAs(tree.direct.getValue(to)), "moved from $at to $to")
            assertTrue(tree.history.steps - before <= 2 * 32, "bounded work from $at to $to")
            at = to
        }
    }

    @Test
    fun aFirstEventHasTheRootAsParentAndDepthOne() {
        val tree = Tree(13, every = 4)
        val ids = tree.grow(tree.games.model(), "root", 9, "m")
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9), ids.map { tree.history.depthOf(it) })
        assertEquals(0, tree.history.depthOf("root"))
        // Checkpoints at depth 4 and 8, besides the root's
        assertEquals(3, tree.store.checkpointCount)
        assertTrue(tree.history.stateAt("root").sameAs(tree.games.model()))
    }
}
