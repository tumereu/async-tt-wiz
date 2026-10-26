package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Actual LobbyController -> GameSession -> GameplayReplica -> shared processor, not a local table. */
internal class BasicTableFixture {
    val data = GameplayFixture()
    fun id(n: Int) = data.id(n)
    val actor = data.actor
    val other = data.other
    val game = data.game
    val definition = data.definition.copy(faces = data.definition.faces.map { it.copy(corner = "2/3") })
    fun container(zone: PlayZone, owner: String = actor): String = id(200 + zone.ordinal + if (owner == other) 10 else 0)
    val root = data.initial.copy(definitions = listOf(SubmittedDefinition(DeckJson.digest(definition), definition)),
        board = data.initial.board.copy(cards = (40..43).map { n -> data.initial.board.cards.single().copy(
            id = id(n), definition = DeckJson.digest(definition), containerId = container(PlayZone.LIBRARY),
            corner = "7/8", placement = null, counters = if (n == 40) mapOf(CounterColor.BLUE to "3") else emptyMap(), note = "retained") },
            containers = listOf(actor, other).flatMap { owner -> PlayZone.entries.filter { it != PlayZone.STACK }.map { zone ->
                PlayContainer(container(zone, owner), zone, owner,
                    if (owner == actor && zone == PlayZone.LIBRARY) (40..43).map(::id) else emptyList())
            } } + PlayContainer(container(PlayZone.STACK), PlayZone.STACK)))
    class Worker : AbstractExecutorService() {
        val queue = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { queue.add(command) }
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst().run() }
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = false
    }
    class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
        override fun disconnect() = Unit
        override fun close() = Unit
    }
    val worker = Worker()
    val wire = Wire()
    val api = object : Lobbies {
        override suspend fun contacts(token: String, after: String?) = ContactList(emptyList())
        override suspend fun games(token: String, invitations: Boolean, after: String?) = GameList(if (invitations) emptyList() else listOf(game))
        override suspend fun head(token: String, id: String) = game
        override suspend fun search(token: String, query: String) = UserSearch(emptyList())
        override suspend fun add(token: String, input: AddContact): AccountIdentity = error("not used")
        override suspend fun create(token: String, input: CreateInvitation): GameSummary = error("not used")
        override fun close() = Unit
    }
    val lobby = LobbyController(api, { "A".repeat(43) }, worker, { it() }, gameConnection = wire, accountId = { actor })
    val basic = BasicTableController({ lobby.state.play }, { game.id in lobby.state.connectedGames }, lobby::gameplay)
    val play get() = requireNotNull(lobby.state.play)
    fun card(n: Int) = play.board.cards.single { it.id == id(n) }
    fun commands() = wire.frames.filterIsInstance<GameCommand>()
    fun message(message: GameServerMessage) = wire.listener.message(message)
    fun connect(initial: PlayState = root) {
        lobby.open(game.id); worker.drain(); wire.listener.opened(); message(GameWelcome())
        val stream = wire.frames.filterIsInstance<OpenGameStream>().last().streamId
        message(GameStreamReady(game.id, stream, "1")); message(data.head(initial))
        assertTrue(game.id in lobby.state.connectedGames)
    }
    fun move(n: Int, destination: TableDestination, top: Boolean = false): Boolean {
        val scope = TableScope(id(n), listOf(id(n)))
        return basic.handle(TableIntent.Move(scope, destination, BasicTableController.sources(play, scope.cardIds, top)))
    }
    fun legacyStack(n: Int): Boolean {
        val card = card(n)
        return lobby.gameplay(PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement),
            MoveDestination(container(PlayZone.STACK), MoveInsertion.TOP), GameplayRules.stackDigest(play)))
    }
    fun update(from: PlayState, command: GameCommand, base: Int, author: String = actor): GameRevision {
        val change = GameplayRules.change(from, author, command)
        assertEquals("accepted", change.outcome)
        return GameRevision(game.id, base.toString(), (base + 1).toString(), command.requestId, author, command.scope,
            game.copy(revision = (base + 1).toString()), play = change.state, mutation = change.mutation, group = change.state!!.currentGroup)
    }
    fun result(revision: GameRevision) = GameResult(revision.requestId, revision.scope, "accepted", ingressOrdinal = "1",
        commitRevision = revision.resultRevision, mutation = revision.mutation)
}

class BasicTableControllerTest {
    @Test fun nonBattlefieldOrientationIsIgnoredWithoutShowingAnError() {
        val f = BasicTableFixture(); f.connect()
        try {
            assertTrue(f.move(40, TableDestination.Container(f.container(PlayZone.HAND), PlayZone.HAND)))
            val toolkit = TableToolkitController(f.actor, { f.play }, { true }, f.lobby::gameplay)
            val before = f.commands().size
            assertFalse(toolkit.handle(TableIntent.Rotate(TableScope(f.id(40), listOf(f.id(40))), true, toggle = true)))
            assertNull(toolkit.error)
            assertEquals(before, f.commands().size)
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun doubleTapUsesCurrentContextNotStaleDrawingOrFirstSelectedCardAndFailedQueueIsVisible() {
        val f = BasicTableFixture(); f.connect()
        try {
            val toolkit = TableToolkitController(f.actor, { f.play }, { true }, f.lobby::gameplay)
            for (n in 40..41) assertTrue(f.move(n, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), n - 40, 0)))
            assertTrue(f.lobby.gameplay(PlayAction.SetTapped(f.id(41), true)))
            val before = f.commands().size
            // Drawing still says untapped and selection order starts with a different card.
            assertTrue(toolkit.handle(TableIntent.Rotate(TableScope(f.id(41), listOf(f.id(40), f.id(41))), true, toggle = true)))
            assertFalse(f.card(40).tapped); assertFalse(f.card(41).tapped)
            assertTrue(f.commands().drop(before).all { ((it.action as GameAction.Play).action as PlayAction.SetTapped).tapped == false })
            val refused = TableToolkitController(f.actor, { f.play }, { true }, { _, _ -> false }, waitingForConnection = { true })
            assertFalse(refused.handle(TableIntent.Rotate(TableScope(f.id(41), listOf(f.id(41))), true, toggle = true)))
            assertNotNull(refused.error, "Waiting for connection must not hide a refused rotation")
        } finally { f.lobby.close(); f.worker.drain() }
    }
    @Test fun handMultiReorderKeepsAnAlreadyPlacedPrefixWithoutSendingANoop() {
        val f = BasicTableFixture(); f.connect()
        try {
            val hand = f.container(PlayZone.HAND)
            for (n in 40..43) assertTrue(f.move(n, TableDestination.Container(hand, PlayZone.HAND)))
            val before = f.commands().size
            val scope = TableScope(f.id(43), listOf(f.id(40), f.id(43)))
            assertTrue(f.basic.handle(TableIntent.Move(scope, TableDestination.Container(hand, PlayZone.HAND,
                handGap = IdentityGap(beforeId = f.id(41))), BasicTableController.sources(f.play, scope.cardIds))))
            assertEquals(listOf(40, 43, 41, 42).map(f::id), f.play.board.containers.single { it.id == hand }.cards)
            assertEquals(listOf(f.id(43)), f.commands().drop(before).map { ((it.action as GameAction.Play).action as PlayAction.MoveCard).cardId })
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun handReorderUsesCapturedGapAndNormalPrediction() {
        val f = BasicTableFixture(); f.connect()
        val hand = f.container(PlayZone.HAND)
        for (n in 40..43) assertTrue(f.move(n, TableDestination.Container(hand, PlayZone.HAND)))
        val before = f.commands().size
        assertTrue(f.move(43, TableDestination.Container(hand, PlayZone.HAND, handGap = IdentityGap(f.id(40), f.id(41)))))
        assertEquals(listOf(40, 43, 41, 42).map(f::id), f.play.board.containers.single { it.id == hand }.cards)
        val action = (f.commands().drop(before).single().action as GameAction.Play).action as PlayAction.MoveCard
        assertEquals(MoveInsertion.GAP, action.destination.insertion)
        assertEquals(f.id(40), action.destination.afterId)
        assertEquals(f.id(41), action.destination.beforeId)
    }

    @Test fun libraryDoubleTapDrawUsesNormalTopMoveAndNeverSubstitutesAStaleTop() {
        val f = BasicTableFixture(); f.connect()
        val draw = TableIntent.Draw(f.container(PlayZone.LIBRARY), f.container(PlayZone.HAND), f.id(40))
        assertTrue(f.basic.handle(draw))
        val action = (f.commands().single().action as GameAction.Play).action
        assertEquals(PlayAction.MoveCard(f.id(40), MoveSource(draw.libraryId, edge = MoveSourceEdge.TOP),
            MoveDestination(draw.handId, MoveInsertion.UNORDERED)), action)
        assertFalse(f.basic.handle(draw))
        assertEquals(1, f.commands().size)
    }

    @Test fun rotateSkipsStackMembersOfMixedSelection() {
        val f = BasicTableFixture(); f.connect()
        assertTrue(f.legacyStack(40))
        assertTrue(f.move(41, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 0)))
        val before = f.commands().size
        assertTrue(f.basic.handle(TableIntent.Rotate(TableScope(f.id(41), listOf(f.id(40), f.id(41))), true)))
        assertEquals(listOf(PlayAction.SetTapped(f.id(41), true)), f.commands().drop(before).map { (it.action as GameAction.Play).action })
        assertFalse(f.card(40).tapped)
    }
    @Test fun selectedCardsEnterExistingPileInSourceOrderImmediatelyWithOneInvocationAndOriginalOutcomes() {
        val f = BasicTableFixture(); f.connect()
        f.move(42, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 2, 1))
        val before = f.play
        val target = TableDestination.CardPile(f.id(42), BasicTableController.sources(before, listOf(f.id(42))).getValue(f.id(42)))
        val scope = TableScope(f.id(41), listOf(f.id(41), f.id(40)))
        assertTrue(f.basic.handle(TableIntent.Move(scope, target, BasicTableController.sources(before, scope.cardIds))))
        val commands = f.commands().drop(1)
        assertEquals(2, commands.size) // Both sent with no receipt/result/revision, not an ack barrier.
        val moves = commands.map { (it.action as GameAction.Play).action as PlayAction.MoveCard }
        assertEquals(listOf(f.id(40), f.id(41)), moves.map { it.cardId })
        assertEquals(listOf(null, f.id(40)), moves.map { it.destination.afterId })
        assertEquals(listOf(listOf(f.id(41)), emptyList()), moves.map { it.destination.pendingIds })
        assertTrue(moves.all { it.destination.insertion == MoveInsertion.PILE_GAP && it.destination.beforeId == f.id(42) && it.destination.targetCardId == f.id(42) })
        assertEquals(1, commands.map { it.invocationId }.distinct().size); assertNotNull(commands[0].invocationId)
        val endpoint = f.play.board
        assertEquals(listOf(f.id(40), f.id(41), f.id(42)), endpoint.containers.single { it.id == f.container(PlayZone.BATTLEFIELD) }.cards)
        assertTrue(endpoint.cards.filter { it.id in listOf(f.id(40), f.id(41), f.id(42)) }.all { it.placement == f.card(42).placement })
        assertEquals(f.root, f.lobby.state.acceptedPlay) // Prediction is not local authoritative success.
        val setup = f.update(f.root, f.commands()[0], 1)
        val first = f.update(before, commands[0], 2)
        val second = f.update(first.play!!, commands[1], 3)
        for (revision in listOf(setup, first, second)) {
            f.message(f.result(revision)); assertEquals(endpoint, f.play.board)
            f.message(revision); assertEquals(endpoint, f.play.board)
        }
        f.message(f.result(first)); f.message(first)
        assertEquals(endpoint, f.play.board); assertEquals(3, f.commands().size)
    }

    @Test fun samePileReorderUsesPendingExclusionAndAlreadyPlacedPrefixSendsNoRequest() {
        val seed = BasicTableFixture()
        val pile = PlayPlacement(BoardPosition(0, 0), seed.id(900))
        fun initial(order: List<Int>): PlayState = seed.root.copy(board = seed.root.board.copy(
            cards = seed.root.board.cards.map { if (it.id in order.map(seed::id)) it.copy(containerId = seed.container(PlayZone.BATTLEFIELD), placement = pile) else it },
            containers = seed.root.board.containers.map { when (it.id) {
                seed.container(PlayZone.BATTLEFIELD) -> it.copy(cards = order.map(seed::id))
                seed.container(PlayZone.LIBRARY) -> it.copy(cards = it.cards.filter { id -> id !in order.map(seed::id) })
                else -> it
            } }))
        for ((order, sent) in listOf(listOf(40, 41, 42) to 0, listOf(42, 40, 41) to 2, listOf(40, 42, 41) to 1)) {
            val f = BasicTableFixture(); f.connect(initial(order))
            val scope = TableScope(f.id(40), listOf(f.id(40), f.id(41)))
            val target = TableDestination.CardPile(f.id(42), BasicTableController.sources(f.play, listOf(f.id(42))).getValue(f.id(42)))
            assertEquals(sent > 0, f.basic.handle(TableIntent.Move(scope, target, BasicTableController.sources(f.play, scope.cardIds))))
            assertEquals(sent, f.commands().size)
            assertEquals(listOf(f.id(40), f.id(41), f.id(42)), f.play.board.containers.single { it.id == f.container(PlayZone.BATTLEFIELD) }.cards)
            if (sent == 1) {
                val only = (f.commands().single().action as GameAction.Play).action as PlayAction.MoveCard
                assertEquals(f.id(41), only.cardId); assertEquals(f.id(40), only.destination.afterId)
            }
        }
        // Ordinary member drag within a full pile still brings only that member to the top.
        val f = BasicTableFixture(); f.connect(initial(listOf(40, 41, 42)))
        assertTrue(f.move(41, TableDestination.CardPile(f.id(40), BasicTableController.sources(f.play, listOf(f.id(40))).getValue(f.id(40)))))
        assertEquals(listOf(f.id(41), f.id(40), f.id(42)), f.play.board.containers.single { it.id == f.container(PlayZone.BATTLEFIELD) }.cards)
    }

    @Test fun prefixConflictWithdrawsOnlyItsPredictionAndNeverResendsOrSubstitutesDestination() {
        val f = BasicTableFixture(); f.connect()
        f.move(42, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 2, 1))
        val setup = f.update(f.root, f.commands().single(), 1); f.message(setup)
        val before = f.play
        val scope = TableScope(f.id(40), listOf(f.id(40), f.id(41)))
        val target = TableDestination.CardPile(f.id(42), BasicTableController.sources(before, listOf(f.id(42))).getValue(f.id(42)))
        assertTrue(f.basic.handle(TableIntent.Move(scope, target, BasicTableController.sources(before, scope.cardIds))))
        val commands = f.commands().drop(1)
        val first = f.update(before, commands[0], 2)
        f.message(first)
        val remote = f.data.command(PlayAction.MoveCard(f.id(40), MoveSource(f.container(PlayZone.BATTLEFIELD), f.card(40).placement),
            MoveDestination(f.container(PlayZone.HAND), MoveInsertion.UNORDERED)))
        val interleaved = f.update(first.play!!, remote, 3, f.other); f.message(interleaved)
        assertEquals(f.container(PlayZone.LIBRARY), f.card(41).containerId)
        assertEquals(f.container(PlayZone.HAND), f.card(40).containerId)
        f.message(GameResult(commands[1].requestId, commands[1].scope, "rejected", "movementConflict", "4"))
        f.message(f.result(first)); f.message(interleaved)
        assertEquals(interleaved.play!!.board, f.play.board)
        assertEquals(commands, f.commands().drop(1))
        val count = f.commands().size
        val staleSources = BasicTableController.sources(before, scope.cardIds)
        val departedTarget = f.data.command(PlayAction.MoveCard(f.id(42), MoveSource(f.container(PlayZone.BATTLEFIELD), f.card(42).placement),
            MoveDestination(f.container(PlayZone.EXILE), MoveInsertion.UNORDERED)))
        f.message(f.update(interleaved.play!!, departedTarget, 4, f.other))
        assertFalse(f.basic.handle(TableIntent.Move(scope, target, staleSources)))
        assertEquals(count, f.commands().size)
    }

    @Test fun normalControllerQueuesIndependentAndSameCardMovesBeforeAnyAckAndNeverRewindsOnOldResults() {
        val f = BasicTableFixture(); f.connect()
        assertTrue(f.move(40, TableDestination.Container(f.container(PlayZone.HAND), PlayZone.HAND), top = true))
        assertTrue(f.move(41, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 2, 1), top = true))
        assertTrue(f.move(40, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 0)))
        assertTrue(f.basic.handle(TableIntent.Rotate(TableScope(f.id(40), listOf(f.id(40))), true)))
        assertTrue(f.basic.handle(TableIntent.PlayerDelta(f.other, null, -3)))
        val commands = f.commands(); assertEquals(5, commands.size)
        val target = f.play.board
        assertEquals(f.root, f.lobby.state.acceptedPlay)
        var accepted = f.root
        val revisions = commands.mapIndexed { index, command -> f.update(accepted, command, index + 1).also { accepted = it.play!! } }
        assertEquals(target, accepted.board)
        // Result-first, revision-first, duplicate and old deliveries all keep the same visual endpoint.
        f.message(f.result(revisions[0])); assertEquals(target, f.play.board)
        for ((index, revision) in revisions.withIndex()) {
            f.message(revision); assertEquals(target, f.play.board)
            if (index > 0) f.message(f.result(revision))
            f.message(revisions[0]); assertEquals(target, f.play.board)
        }
        f.message(f.result(revisions[0])); assertEquals(target, f.play.board)
        assertFalse(f.game.id in f.lobby.state.pendingGames)
        assertEquals(commands, f.commands())
        assertEquals(BoardPosition(0, 0), f.card(40).placement!!.position)
        assertTrue(f.card(40).tapped); assertEquals("", f.card(40).note)
    }

    @Test fun defaultsAreOrdinaryOrderedConstituentsWithOneInvocationAndUnorderedDestinations() {
        val f = BasicTableFixture(); f.connect()
        assertTrue(f.move(40, TableDestination.Container(f.container(PlayZone.GRAVEYARD), PlayZone.GRAVEYARD), true))
        val commands = f.commands()
        assertEquals(listOf(PlayAction.MoveCard::class),
            commands.map { (it.action as GameAction.Play).action::class })
        assertEquals(1, commands.map { it.invocationId }.distinct().size); assertNotNull(commands[0].invocationId)
        val move = (commands[0].action as GameAction.Play).action as PlayAction.MoveCard
        assertEquals(MoveInsertion.UNORDERED, move.destination.insertion); assertNull(move.destination.beforeId)
        assertEquals("7/8", f.card(40).corner); assertEquals(mapOf(CounterColor.BLUE to "3"), f.card(40).counters)
        assertEquals("", f.card(40).note)
        assertTrue(f.move(40, TableDestination.Container(f.container(PlayZone.EXILE), PlayZone.EXILE)))
        assertEquals(f.container(PlayZone.EXILE), f.card(40).containerId)
        assertTrue(f.move(40, TableDestination.Container(f.container(PlayZone.LIBRARY), PlayZone.LIBRARY)))
        assertEquals(f.id(40), f.play.board.containers.single { it.id == f.container(PlayZone.LIBRARY) }.cards.first())
    }

    @Test fun capturedSelectionPreservesSourceOrderAtLibraryTopBottomAndUsesPrefixGapsNotReversedSubmissions() {
        for (bottom in listOf(false, true)) {
            val f = BasicTableFixture(); f.connect()
            val scope = TableScope(f.id(42), listOf(f.id(42), f.id(40))) // UI order is not Library order.
            val intent = TableIntent.Move(scope, TableDestination.Container(f.container(PlayZone.LIBRARY), PlayZone.LIBRARY, bottom),
                BasicTableController.sources(f.play, scope.cardIds))
            assertTrue(f.basic.handle(intent))
            val moves = f.commands().mapNotNull { ((it.action as GameAction.Play).action as? PlayAction.MoveCard) }
            // An already-top UNKNOWN card still needs a node to explicitly fix its position.
            assertEquals(listOf(f.id(40), f.id(42)), moves.map { it.cardId })
            assertEquals(MoveInsertion.GAP, moves.last().destination.insertion)
            assertEquals(f.id(40), moves.last().destination.afterId)
            val order = f.play.board.containers.single { it.id == f.container(PlayZone.LIBRARY) }.cards
            assertEquals(if (bottom) listOf(41, 43, 40, 42) else listOf(40, 42, 41, 43), order.map { it.takeLast(2).toInt() })
            val library = f.play.board.containers.single { it.id == f.container(PlayZone.LIBRARY) }
            assertEquals(listOf(f.id(40), f.id(42)), if (bottom) library.knownBottom else library.knownTop)
        }
    }

    @Test fun capturedSourceAndDestinationDoNotFollowSyncAndStackUsesSemanticPredictedPredecessor() {
        val f = BasicTableFixture(); f.connect()
        val scope = TableScope(f.id(40), listOf(f.id(40)))
        val original = TableIntent.Move(scope, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 0),
            BasicTableController.sources(f.play, scope.cardIds, true))
        val remote = f.data.command(PlayAction.MoveCard(f.id(40), MoveSource(f.container(PlayZone.LIBRARY)),
            MoveDestination(f.container(PlayZone.HAND), MoveInsertion.UNORDERED)))
        f.message(f.update(f.root, remote, 1, f.other))
        assertFalse(f.basic.handle(original)); assertTrue(f.commands().isEmpty())
        assertTrue(f.move(40, TableDestination.Container(f.container(PlayZone.STACK), PlayZone.STACK)))
        val predecessor = f.play
        assertTrue(f.move(41, TableDestination.Container(f.container(PlayZone.HAND), PlayZone.HAND), true))
        assertTrue(f.move(41, TableDestination.Container(f.container(PlayZone.STACK), PlayZone.STACK)))
        val moves = f.commands().mapNotNull { ((it.action as GameAction.Play).action as? PlayAction.Stack)?.action as? StackAction.AddEffect }
        assertEquals(GameplayRules.stackDigest(predecessor), moves.last().expectedStackDigest)
        assertNotEquals(moves.first().expectedStackDigest, moves.last().expectedStackDigest)
    }

    @Test fun remoteConflictWithdrawsOnlyFailedPredictionsAndRetainsIndependentLifeWithoutDuplicateDispatch() {
        val f = BasicTableFixture(); f.connect()
        f.move(40, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 0), true)
        f.basic.handle(TableIntent.PlayerDelta(f.actor, null, 2))
        val commands = f.commands()
        val remote = f.data.command(PlayAction.MoveCard(f.id(40), MoveSource(f.container(PlayZone.LIBRARY)),
            MoveDestination(f.container(PlayZone.EXILE), MoveInsertion.UNORDERED)))
        val revision = f.update(f.root, remote, 1, f.other); f.message(revision)
        assertEquals(f.container(PlayZone.EXILE), f.card(40).containerId)
        assertEquals("22", f.play.board.players.single { it.accountId == f.actor }.life)
        f.message(GameResult(commands[0].requestId, commands[0].scope, "rejected", "movementConflict", "1"))
        val life = f.update(revision.play!!, commands[1], 2); val endpoint = f.play.board
        f.message(f.result(life)); f.message(life); f.message(revision); f.message(life)
        assertEquals(endpoint, f.play.board); assertEquals(commands, f.commands())
        f.wire.listener.disconnected(); assertFalse(f.basic.handle(TableIntent.PlayerDelta(f.actor, null, 1)))
        assertEquals(commands, f.commands()); assertEquals(endpoint, f.play.board)
    }

    @Test fun realGridPileSingleMemberMovesPreservePlacementsAndCapacityRejectsWholeSelection() {
        val f = BasicTableFixture(); f.connect()
        f.move(40, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 2, 1), true)
        val target = TableDestination.CardPile(f.id(40), BasicTableController.sources(f.play, listOf(f.id(40))).getValue(f.id(40)))
        f.move(41, target, true); f.move(42, target, true)
        assertEquals(f.card(40).placement, f.card(42).placement)
        val placements = TablePresentation.logicalPlacements(f.play)
        assertEquals(2f, placements.getValue(f.id(40)).column)
        assertEquals(0, placements.getValue(f.id(42)).pileIndex)
        val before = f.commands()
        assertFalse(f.move(43, target, true)); assertEquals(before, f.commands())
        val scope = TableScope(f.id(41), listOf(f.id(41), f.id(43)))
        assertFalse(f.basic.handle(TableIntent.Move(scope, target, BasicTableController.sources(f.play, scope.cardIds))))
        assertEquals(before, f.commands())
        assertTrue(f.move(41, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 4, 0)))
        assertNotEquals(f.card(40).placement, f.card(41).placement)
    }
}
