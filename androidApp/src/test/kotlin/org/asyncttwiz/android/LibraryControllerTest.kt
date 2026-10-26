package org.asyncttwiz.android

import kotlin.test.*
import org.junit.Test
import org.asyncttwiz.protocol.*

class LibraryControllerTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun initial(): PlayState = PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
        BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))), (20..25).map { n ->
            PlayCard(id(n), "definition", id(7), if (n < 23) id(10) else id(11), id(40)) },
            listOf(PlayContainer(id(10), PlayZone.LIBRARY, id(7), (20..22).map(::id)),
                PlayContainer(id(11), PlayZone.HAND, id(7), (23..25).map(::id))), TurnState(id(7))),
        emptyList(), emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))

    @Test fun mulliganSendsOneActionAndPredictsOnceWithoutResults() {
        var predicted = initial()
        val actions = mutableListOf<PlayAction>()
        val mutations = mutableListOf<GameplayMutation>()
        var seedCalls = 0
        val seed = RandomV1.encodeSeed(ByteArray(32))
        val controller = LibraryController(id(2), id(10), id(11), { predicted }, { true }, { action ->
            actions += action
            val command = GameCommand(id(100 + actions.size), GameScope(id(1), playId = id(2)), GameAction.Play(action), id(9), "1")
            val change = GameplayRules.change(predicted, id(7), command)
            assertNull(change.reason)
            predicted = change.state!!
            change.mutation?.let(mutations::add)
            true // Local admission only: no accepted server response has arrived.
        }, { seedCalls++; seed })
        val displayed = listOf(25, 23, 24).map(::id)
        assertEquals(LibrarySubmission(1, 0), controller.mulligan(displayed, minusOne = true))
        assertEquals(PlayAction.Mulligan(id(10), id(11), displayed, 2, seed), actions.single())
        assertEquals(RandomV1.shuffle(displayed + (20..22).map(::id), seed).take(2),
            predicted.board.containers.single { it.id == id(11) }.cards)
        assertEquals(1, seedCalls)
        assertEquals(1, mutations.size)
        var authoritative = initial()
        mutations.forEach { mutation ->
            val replay = GameplayHistory.replay(authoritative, mutation)
            assertNull(replay.reason)
            authoritative = replay.state!!
        }
        assertEquals(predicted, authoritative)
    }

    @Test fun freshShuffleSamplesOnceOfflineAndIncarnationFencesDoNotSampleOrSend() {
        var current = initial()
        var online = true
        var samples = 0
        val sent = mutableListOf<PlayAction>()
        val controller = LibraryController(id(2), id(10), id(11), { current }, { online }, { sent += it; true },
            { RandomV1.encodeSeed(ByteArray(32) { samples.toByte() }).also { samples++ } })
        assertEquals(LibrarySubmission(1, 0), controller.shuffle())
        val original = sent.single() as PlayAction.ShuffleLibrary
        // Retry ownership is the existing queue; retaining the action preserves the original seed.
        assertEquals(original.seed, (sent.single() as PlayAction.ShuffleLibrary).seed)
        controller.shuffle()
        assertNotEquals(original.seed, (sent.last() as PlayAction.ShuffleLibrary).seed)
        online = false
        controller.shuffle(); controller.show(); controller.draw()
        assertEquals(2, samples); assertEquals(2, sent.size)
        current = current.copy(id = id(99)); online = true
        controller.shuffle(); controller.draw()
        assertEquals(2, samples); assertEquals(2, sent.size)
    }

    @Test fun sevenCardMulliganPredictionBeforeAfterBenchmark() {
        val base = initial()
        val cards = (1000..1059).map { n -> PlayCard(id(n), "definition", id(7),
            if (n < 1053) id(10) else id(11), id(40)) }
        val root = base.copy(board = base.board.copy(cards = cards, containers = base.board.containers.map { container ->
            container.copy(cards = cards.filter { it.containerId == container.id }.map { it.id }) }))
        val hand = root.board.containers.single { it.id == id(11) }.cards
        val seed = RandomV1.encodeSeed(ByteArray(32))
        var last = root
        fun predict(combined: Boolean): Int {
            var state = root
            var requests = 0
            fun send(action: PlayAction) {
                val command = GameCommand(id(2000 + requests++), GameScope(root.gameId, playId = root.id),
                    GameAction.Play(action), id(9), "1")
                state = assertNotNull(GameplayRules.change(state, id(7), command).state)
            }
            if (combined) send(PlayAction.Mulligan(id(10), id(11), hand, 7, seed)) else {
                val plan = org.asyncttwiz.protocol.LibraryActions.mulligan(root.board, id(10), id(11), hand, seed)
                plan.prefix().forEach(::send)
                org.asyncttwiz.protocol.LibraryActions.draw(state.board, id(10), id(11), 7).forEach(::send)
            }
            last = state
            return requests
        }
        assertEquals(15, predict(false))
        val before = last.board
        assertEquals(1, predict(true))
        assertEquals(before, last.board)
        fun median(combined: Boolean): Double {
            repeat(3) { predict(combined) }
            return List(7) { val start = System.nanoTime(); predict(combined); (System.nanoTime() - start) / 1_000_000.0 }.sorted()[3]
        }
        println("Mulligan 7 prediction (60 cards): before=${median(false)} ms / 15 requests; after=${median(true)} ms / 1 request")
    }

    @Test fun closePanelBindsRestAndOneSeedAndDoesNotSampleOrSendOffline() {
        var online = true
        var samples = 0
        val seed = RandomV1.encodeSeed(ByteArray(32))
        val sent = mutableListOf<PlayAction>()
        val controller = LibraryController(id(2), id(10), id(11), { initial() }, { online }, { sent += it; true },
            seed = { samples++; seed })
        assertEquals(LibrarySubmission(1, 0), controller.dismiss(id(30), ContentsRest.BOTTOM_RANDOM))
        assertEquals(listOf<PlayAction>(PlayAction.DismissContents(id(30), ContentsRest.BOTTOM_RANDOM, seed)), sent)
        assertEquals(1, samples)
        online = false
        assertEquals(LibrarySubmission(0, 1), controller.dismiss(id(30)))
        assertEquals(1, samples); assertEquals(1, sent.size)
    }

    @Test fun bottomMoveAndDrawCaptureIdsAndLocalAdmissionRefusalIsNotAServerAck() {
        val state = initial()
        val sent = mutableListOf<PlayAction>()
        val controller = LibraryController(id(2), id(10), id(11), { state }, { true }, { sent += it; sent.size != 1 })
        assertEquals(LibrarySubmission(1, 1), controller.draw(2))
        assertEquals(listOf(id(20), id(21)), sent.map { (it as PlayAction.MoveCard).cardId })
        sent.clear()
        controller.move(ContentsSelection.BOTTOM, 2, MoveDestination(id(11), MoveInsertion.UNORDERED))
        assertEquals(listOf(id(21), id(22)), sent.map { (it as PlayAction.MoveCard).cardId })
    }

    @Test fun multiCardDrawAcrossAReseedUsesTheNewFrontierFromEitherEnd() {
        for (end in listOf(ContentsSelection.TOP, ContentsSelection.BOTTOM)) {
            val root = initial()
            var tree = GameplayTree(root)
            var n = 300
            fun command(action: PlayAction) = GameCommand(id(n++), GameScope(root.gameId, playId = root.id),
                GameAction.Play(action), id(9), "1")
            tree = assertNotNull(tree.play(id(8), command(PlayAction.AdjustLife(id(8), "1"))).tree)
            var armed = true
            val controller = LibraryController(root.id, id(10), id(11),
                { if (armed) root else tree.stateAt(tree.currentNodeId) }, { true }, { action ->
                    val input = command(action).copy(continuation = if (armed) Continuation(root.root.id, true) else null)
                    val change = tree.play(id(7), input)
                    tree = assertNotNull(change.tree, change.reason)
                    armed = false
                    true
                })
            assertEquals(LibrarySubmission(2, 0), controller.draw(2, end))
            val fork = tree.nodes.values.first { it.transition.delta!!.libraryReseeds.isNotEmpty() }
            val order = fork.transition.delta!!.libraryReseeds.single().to
            val hand = tree.stateAt(tree.currentNodeId)!!.board.containers.single { it.id == id(11) }
            assertEquals(root.board.containers.single { it.id == id(11) }.cards +
                if (end == ContentsSelection.TOP) order.take(2) else order.takeLast(2), hand.cards)
        }
    }
}
