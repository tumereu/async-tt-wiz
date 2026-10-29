package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.concurrent.Executor
import kotlin.test.*

class EventTextTest {
    private val f = DownloadedTreeFixture
    private val names = mapOf(f.a to "alice", f.b to "bob")
    private fun id(n: Int) = f.id(n)

    /** bob draws Shock, Opt, Ponder, Brainstorm (one group); alice sets her life; bob sets his life. */
    private class Game(val tree: GameplayTree, val draws: List<String>, val aliceLife: String, val bobLife: String)
    private fun game(): Game {
        val base = f.root(PlayZone.LIBRARY).initial
        val cardNames = listOf("Shock", "Opt", "Ponder", "Brainstorm")
        val definitions = cardNames.mapIndexed { n, name -> CardDefinition(name, listOf(DefinitionFace(id(600 + n), name))) }
        val cards = definitions.mapIndexed { n, definition -> PlayCard(id(610 + n), DeckJson.digest(definition), f.b, id(13), id(600 + n)) }
        val initial = base.copy(definitions = base.definitions + definitions.map { SubmittedDefinition(DeckJson.digest(it), it) },
            board = base.board.copy(cards = base.board.cards.filter { it.containerId != id(13) } + cards,
                containers = base.board.containers.map { if (it.id == id(13)) it.copy(cards = cards.map(PlayCard::id)) else it } +
                    listOf(PlayContainer(id(20), PlayZone.HAND, f.a), PlayContainer(id(21), PlayZone.HAND, f.b))))
        var tree = GameplayTree(initial)
        fun play(actor: String, request: Int, action: PlayAction): String {
            tree = assertNotNull(tree.play(actor, GameCommand(id(request), GameScope(initial.gameId, playId = initial.id),
                GameAction.Play(action), id(14), "1")).tree)
            return tree.currentNodeId
        }
        val draws = cards.mapIndexed { n, card -> play(f.b, 700 + n, PlayAction.MoveCard(card.id,
            MoveSource(id(13), edge = MoveSourceEdge.TOP), MoveDestination(id(21), MoveInsertion.UNORDERED))) }
        val alice = play(f.a, 720, PlayAction.SetLife(f.a, "17"))
        val bob = play(f.b, 721, PlayAction.SetLife(f.b, "18"))
        return Game(tree, draws, alice, bob)
    }

    private class Feed {
        val work = ArrayDeque<Runnable>()
        val posts = ArrayDeque<() -> Unit>()
        var now = 1_000L
        val feed = EventTextFeed(Executor { work.addLast(it) }, { posts.addLast(it) }, { now })
        fun drain() { while (work.isNotEmpty() || posts.isNotEmpty()) { work.removeFirstOrNull()?.run(); posts.removeFirstOrNull()?.invoke() } }
    }
    private fun Feed.show(game: Game, cursor: String, mode: HistoryMode = HistoryMode.PLAYBACK, viewer: String = f.a) =
        feed.displayed(cursor, game.tree.stateAt(cursor), mode, game.tree.nodes, viewer, names) { ancestor, endpoint ->
            generateSequence(endpoint) { game.tree.nodes[it]?.parentId }.any { it == ancestor }
        }

    @Test fun groupTextComesFromTheShownBoardAndNeverNamesUnseenCards() {
        val game = game()
        val shown = game.tree.stateAt(game.draws.last())!!
        assertEquals("4 cards: Library → Hand", eventGroupText(game.tree.nodes, shown, f.a, names)?.description)
        assertEquals("Shock, Opt, Ponder, Brainstorm: Library → Hand", eventGroupText(game.tree.nodes, shown, f.b, names)?.description)
        // A board inside the group tells only the events it includes.
        assertEquals("Shock, Opt: Library → Hand",
            eventGroupText(game.tree.nodes, game.tree.stateAt(game.draws[1])!!, f.b, names)?.description)
        assertEquals("Life 20 → 17", eventGroupText(game.tree.nodes, game.tree.stateAt(game.aliceLife)!!, f.b, names)?.description)
        assertNull(eventGroupText(game.tree.nodes, game.tree.initial, f.a, names), "the initial state has no event")
        // Too long a group reads as its last event, never as a miscounted group.
        assertEquals("A card: Library → Hand", eventGroupText(game.tree.nodes, shown, f.a, names, limit = 2)?.description)
    }

    @Test fun timelineShowsEveryGroupIncludingOwnAndMovingBackwardsShowsTheGroupNowShown() {
        val game = game()
        Feed().run {
            show(game, game.aliceLife); drain()
            assertEquals("Life 20 → 17", feed.chip?.text, "your own group is shown in timeline mode")
            show(game, game.bobLife); drain()
            assertEquals("Life 20 → 18", feed.chip?.text)
            show(game, game.draws.last())
            assertNull(feed.chip, "the previous text goes at once, never shown over another board")
            drain()
            assertEquals("4 cards: Library → Hand", feed.chip?.text)
            show(game, game.tree.initial.root.id); drain()
            assertNull(feed.chip)
        }
    }

    @Test fun fastScrubShowsOnlyTheFinalPositionsText() {
        val game = game()
        Feed().run {
            show(game, game.bobLife); show(game, game.aliceLife); show(game, game.draws.last())
            assertEquals(1, work.size, "superseded texts are never computed")
            drain()
            assertEquals("4 cards: Library → Hand", feed.chip?.text)
            // A text computed for a board already left behind is dropped.
            show(game, game.aliceLife); work.removeFirst().run()
            show(game, game.bobLife)
            posts.removeFirst().invoke()
            assertNull(feed.chip)
            drain()
            assertEquals("Life 20 → 18", feed.chip?.text)
        }
    }

    @Test fun liveShowsOnlyAnOpponentsNewGroup() {
        val game = game()
        Feed().run {
            show(game, game.draws.last(), HistoryMode.LIVE); drain()
            assertNull(feed.chip, "opening the table tells nothing new")
            show(game, game.aliceLife, HistoryMode.LIVE); drain()
            assertNull(feed.chip, "not your own action")
            show(game, game.bobLife, HistoryMode.LIVE); drain()
            assertEquals("Life 20 → 18", feed.chip?.text)
            show(game, game.aliceLife, HistoryMode.LIVE); drain()
            assertNull(feed.chip, "a head moving back (undo) is not a new group")
        }
        Feed().run {
            show(game, game.aliceLife, HistoryMode.PLAYBACK); drain()
            feed.displayed(game.aliceLife, game.tree.stateAt(game.aliceLife), HistoryMode.LIVE, game.tree.nodes, f.a, names) { _, _ -> true }
            assertEquals("Life 20 → 17", feed.chip?.text, "reaching live at the same board keeps its text")
            show(game, game.bobLife, HistoryMode.PLAYBACK); drain()
            show(game, game.draws.last(), HistoryMode.LIVE); drain()
            assertNull(feed.chip, "returning to live is not an opponent's new group")
        }
    }

    @Test fun textFadesOnItsOwnClockWhilePlaybackIsPaused() {
        val game = game()
        val harness = Feed()
        val playback = HistoryPlayback(HistoryTree.ingest(game.tree, "v1"))
        playback.scrub(game.aliceLife); playback.pause()
        harness.run {
            show(game, game.aliceLife); drain()
            assertEquals(EVENT_TEXT_MIN_MILLIS, feed.remainingMillis(500))
            now += 1_999
            assertEquals(1, feed.remainingMillis(500))
            now += 1
            assertEquals(0, feed.remainingMillis(500), "due while paused")
            assertFalse(playback.position.playing)
            val first = feed.chip!!.serial
            // A longer playback delay keeps it up as long; an old timer never removes a newer text.
            show(game, game.bobLife); drain()
            feed.expire(first)
            assertNotNull(feed.chip)
            assertEquals(3_000, feed.remainingMillis(3_000))
            feed.expire(feed.chip!!.serial)
            assertNull(feed.chip)
        }
    }

    @Test fun settingOffShowsNothing() {
        val game = game()
        Feed().run {
            show(game, game.aliceLife); drain()
            assertNotNull(feed.chip)
            feed.enabled = false
            assertNull(feed.chip)
            show(game, game.bobLife)
            assertTrue(work.isEmpty())
            drain(); assertNull(feed.chip)
            feed.enabled = true
            show(game, game.draws.last()); drain()
            assertEquals("4 cards: Library → Hand", feed.chip?.text)
        }
    }

    @Test fun tableHistoryShowsTheScrubbedBoardsGroupText() {
        val game = game()
        val work = ArrayDeque<Runnable>(); val posts = ArrayDeque<() -> Unit>()
        val reviews = ArrayDeque<Pair<String?, (DownloadedHistoryState) -> Unit>>()
        val history = TableHistoryController(Executor { work.addLast(it) }, { posts.addLast(it) }, { node, complete ->
            reviews.addLast(node to complete); true
        }, { _, _, _ -> error("Review must not submit gameplay") })
        fun drain() { while (work.isNotEmpty() || posts.isNotEmpty()) { work.removeFirstOrNull()?.run(); posts.removeFirstOrNull()?.invoke() } }
        history.use {
            val summary = GameSummary(game.tree.initial.gameId, "game", "now", AccountIdentity(f.a, "alice"),
                AccountIdentity(f.b, "bob"), game.tree.initial.preparationRound, f.id(500), InvitationState.ACCEPTED)
            history.update(game.tree, "v1", summary, f.a); drain()
            history.select(game.draws.last()); history.displayPosition()
            val (node, complete) = reviews.removeFirst()
            complete(DownloadedHistoryState(1L, node, state = game.tree.stateAt(node!!)))
            drain()
            assertEquals("4 cards: Library → Hand", history.eventText.chip?.text)
        }
        assertNull(history.eventText.chip)
    }
}
