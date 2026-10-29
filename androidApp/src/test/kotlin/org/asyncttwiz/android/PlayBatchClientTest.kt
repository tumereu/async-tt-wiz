package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class PlayBatchClientTest {
    private fun toolkit(f: BasicTableFixture) = TableToolkitController(f.actor, { f.play }, { true },
        f.lobby::gameplay, playBatch = f.lobby::gameplayBatch, playTree = f.lobby::displayTree)

    @Test fun catalogTokensUseTypeAppropriateRowsStackUpToThreeAndKeepFlippableDesignations() {
        for (type in listOf("Token Creature — Elf", "Token Artifact — Treasure", "Token Land — Forest", "Card", "Dungeon", "")) {
            val f = BasicTableFixture()
            val definition = CardDefinition(if (type == "Card") "Day // Night" else "Token", listOf(
                DefinitionFace(f.id(901), "Front", listOf(f.id(902)), typeLine = type),
                DefinitionFace(f.id(902), "Back", listOf(f.id(901)), typeLine = type)), token = true, layout = "double_faced_token")
            val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
            f.connect()
            try {
                val ui = toolkit(f)
                val before = f.play.board
                repeat(4) { index ->
                    val count = f.commands().size
                    ui.tokens.catalog(); ui.tokens.create(submitted)
                    assertFalse(ui.tokens.showingCatalog, ui.tokens.error)
                    val command = f.commands().drop(count).single()
                    // Rules v30: the token is created straight in its row (no follow-up move); the second and third
                    // identical tokens join the first one's pile and the fourth finds it full and starts a new pile.
                    assertIs<PlayAction.CreateToken>(command.playActions().single())
                    val row = if ("Land" in type) 0 else 1
                    val tokens = f.play.board.cards.filter { it.token }
                    assertEquals(index + 1, tokens.size)
                    assertEquals(if (index < 3) setOf(BoardPosition(0, row)) else setOf(BoardPosition(0, row), BoardPosition(1, row)),
                        tokens.map { it.placement!!.position }.toSet())
                    assertEquals(if (index < 3) 1 else 2, tokens.map { it.placement }.distinct().size)
                    assertEquals(2, f.play.definitions.single { it.digest == submitted.digest }.definition.faces.size)
                }
                repeat(3) { assertTrue(f.lobby.historyControl(GameAction.Undo)) }
                assertEquals(1, f.play.board.cards.count { it.token })
                val first = f.play.board.cards.single { it.token }
                assertTrue(ui.submit(PlayAction.SetFace(first.id, f.id(902))))
                assertEquals(f.id(902), f.play.board.cards.single { it.token }.faceId)
                assertTrue(f.lobby.historyControl(GameAction.Undo))
                assertTrue(f.lobby.historyControl(GameAction.Undo))
                assertEquals(before, f.play.board)
            } finally { f.lobby.close(); f.worker.drain() }
        }
    }

    @Test fun shuffleIntoLibrarySendsMovesAndEveryOwnersShuffleAsOneUndoableRequest() {
        for (count in listOf(1, 2)) {
            val f = BasicTableFixture()
            val ids = (40 until 40 + count).map(f::id)
            val initial = f.root.copy(board = f.root.board.copy(
                cards = f.root.board.cards.map { card -> if (card.id !in ids) card else card.copy(
                    containerId = f.container(PlayZone.HAND), owner = if (card.id == f.id(41)) f.other else f.actor) },
                containers = f.root.board.containers.map { c -> c.copy(cards = when (c.id) {
                    f.container(PlayZone.HAND) -> ids
                    f.container(PlayZone.LIBRARY) -> c.cards - ids.toSet()
                    else -> c.cards
                }) }))
            f.connect(initial)
            try {
                val ui = toolkit(f)
                val captured = mutableListOf<TableIntent>()
                var dismissed = false
                basicMoveTools(f.play, emptyMap(), true, TableScope(ids.first(), ids),
                    BasicTableController.sources(f.play, ids), { captured += it; ui.handle(it) }, { dismissed = true })
                    .single { it.name == "Shuffle into library" }.click()
                assertTrue(dismissed)
                val intent = assertIs<TableIntent.Batch>(captured.single())
                val command = f.commands().single()
                assertEquals(intent.invocationId, command.requestId)
                val actions = assertIs<GameAction.PlayBatch>(command.action).actions
                assertEquals(ids.toSet(), actions.filterIsInstance<PlayAction.MoveCard>().map { it.cardId }.toSet())
                assertEquals(ids.map { if (it == f.id(41)) f.container(PlayZone.LIBRARY, f.other) else f.container(PlayZone.LIBRARY) }.toSet(),
                    actions.filterIsInstance<PlayAction.ShuffleLibrary>().map { it.containerId }.toSet())
                assertTrue(actions.take(count).all { it is PlayAction.MoveCard })
                assertTrue(f.lobby.displayTree()!!.nodes.values.all { it.invocationId == command.requestId })
                assertTrue(f.lobby.historyControl(GameAction.Undo))
                assertEquals(initial.board, f.play.board)
            } finally { f.lobby.close(); f.worker.drain() }
        }
    }

    @Test fun selectedPingAndUnpingFlattenIntoOneBatchAndUndoTogether() {
        val f = BasicTableFixture(); f.connect()
        try {
            assertTrue(f.lobby.gameplay(PlayAction.NextStep))
            val first = PingTarget(PingTargetKind.CARD, f.id(40))
            val second = PingTarget(PingTargetKind.CARD, f.id(41))
            val reference = EventReference(f.play.id, f.play.currentNodeId)
            assertTrue(f.lobby.annotate(PlayAction.Ping(reference, first)))
            val before = f.play
            val ui = toolkit(f)
            ui.openCard(TableScope(first.id!!, listOf(first.id!!, second.id!!)))
            val annotations = TableMenuAnnotations(EventReference(before.id, before.currentNodeId), f.lobby::annotateBatch)
            val start = f.commands().size
            cardPingTool(ui, before, ui.capture!!, true, annotations).click()
            val command = f.commands().drop(start).single()
            val actions = assertIs<GameAction.PlayBatch>(command.action).actions
            assertEquals(listOf(PlayAction.Unping(reference, first), PlayAction.Ping(annotations.reference, second)), actions)
            assertEquals(listOf(second), ActivePingProjection().records(f.play).map { it.target })
            assertTrue(f.lobby.historyControl(GameAction.Undo))
            assertEquals(before.annotations, f.play.annotations)
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun historicalTopAndBottomBattlefieldMovesDraftAtTheReseededContinuation() {
        for (selection in listOf(ContentsSelection.TOP, ContentsSelection.BOTTOM)) {
            val f = BasicTableFixture()
            val initial = f.root.copy(librarySecret = RandomV1.encodeSeed(ByteArray(32) { it.toByte() }))
            val replica = GameplayReplica()
            assertTrue(replica.root(f.data.head(initial)))
            assertTrue(replica.predict(f.data.command(PlayAction.NextStep), f.actor))
            val before = replica.displayTree!!
            val continuation = Continuation(initial.root.id, true)
            val probe = assertNotNull(HistoryContinuation.change(before, f.actor,
                f.data.command(PlayAction.NextStep).copy(requestId = f.id(810), continuation = continuation)).tree)
            val reseeded = probe.nodes.getValue(probe.currentNodeId).transition.delta!!.libraryReseeds
                .single { it.containerId == f.container(PlayZone.LIBRARY) }.to
            val expected = LibraryRules.slice(reseeded, selection, 2)
            val original = LibraryRules.slice(initial.board.containers.single { it.id == f.container(PlayZone.LIBRARY) }.cards, selection, 2)
            assertNotEquals(original, expected, "Fixture must change the captured edge identities")
            val commands = mutableListOf<GameCommand>()
            val ui = TableToolkitController(f.actor, { initial }, { true }, { _, _ -> error("Expected a batch") },
                playBatch = { actions, request ->
                    val command = f.data.command(actions.first()).copy(requestId = request,
                        action = GameAction.PlayBatch(actions), continuation = continuation)
                    commands += command
                    replica.predict(command, f.actor)
                }, playTree = { before }, continuation = { continuation })
            val library = ui.libraryController(f.container(PlayZone.LIBRARY), f.container(PlayZone.HAND))!!
            assertEquals(LibrarySubmission(2, 0), library.move(selection, 2,
                MoveDestination(f.container(PlayZone.BATTLEFIELD), MoveInsertion.EMPTY, BoardPosition(0, 0))))
            val command = commands.single()
            val moves = assertIs<GameAction.PlayBatch>(command.action).actions.map { assertIs<PlayAction.MoveCard>(it) }
            assertEquals(2, moves.size)
            assertTrue(moves.all { it.source.takeFromEdge })
            val accepted = assertNotNull(HistoryContinuation.change(before, f.actor, command).tree)
            assertEquals(accepted.stateAt(accepted.currentNodeId), replica.display)
            val battlefield = replica.display!!.board.containers.single { it.id == f.container(PlayZone.BATTLEFIELD) }
            assertEquals(expected.toSet(), battlefield.cards.toSet()) // Battlefield membership is unordered.
            assertEquals(listOf(BoardPosition(0, 0), BoardPosition(1, 0)), expected.map { id ->
                replica.display!!.board.cards.single { it.id == id }.placement!!.position })
        }
    }

    @Test fun failedSingleAndBatchValueEnqueuePreserveTheEditorUntilRetrySucceeds() {
        for (count in listOf(1, 2)) {
            val f = BasicTableFixture()
            f.connect(f.root.copy(board = f.root.board.copy(
                cards = f.root.board.cards.mapIndexed { index, card -> card.copy(containerId = f.container(PlayZone.BATTLEFIELD),
                    placement = PlayPlacement(BoardPosition(index, 0), f.id(800 + index))) },
                containers = f.root.board.containers.map { it.copy(cards =
                    if (it.id == f.container(PlayZone.BATTLEFIELD)) (40..43).map(f::id) else emptyList()) })))
            try {
                var accept = false
                val ui = TableToolkitController(f.actor, { f.play }, { true }, { action, group ->
                    accept && f.lobby.gameplay(action, group)
                }, playBatch = { actions, request -> accept && f.lobby.gameplayBatch(actions, request) },
                    playTree = f.lobby::displayTree)
                val ids = (40 until 40 + count).map(f::id)
                ui.manual.cards(f.play, ids.first(), ids)
                ui.manual.edit(ManualValue(ManualValueKind.NOTE), f.play, f.actor)
                val editor = ui.manual.editor!!
                editor.text = "keep my typed value"
                assertFalse(ui.completeValue())
                assertSame(editor, ui.manual.editor)
                assertEquals("keep my typed value", ui.manual.editor!!.text)
                assertTrue(f.commands().isEmpty())
                accept = true
                assertTrue(ui.completeValue())
                assertNull(ui.manual.editor)
                assertEquals(count, f.commands().single().playActions().size)
                assertTrue(f.play.board.cards.filter { it.id in ids }.all { it.note == editor.text })
            } finally { f.lobby.close(); f.worker.drain() }
        }
    }

    @Test fun drawNClampsToRemainingCardsAndRejectionWithdrawsTheWholePrediction() {
        val f = BasicTableFixture(); f.connect()
        try {
            val ui = toolkit(f)
            val library = ui.libraryController(f.container(PlayZone.LIBRARY), f.container(PlayZone.HAND))!!
            assertEquals(LibrarySubmission(4, 0), library.draw(20))
            val command = f.commands().single()
            assertEquals(4, assertIs<GameAction.PlayBatch>(command.action).actions.size)
            assertEquals(4, f.play.board.containers.single { it.zone == PlayZone.HAND && it.accountId == f.actor }.cards.size)
            assertEquals(f.root, f.lobby.state.acceptedPlay)
            f.message(GameResult(command.requestId, command.scope, "rejected", "movementConflict", "1"))
            assertEquals(f.root.board, f.play.board)
            assertNotNull(f.lobby.state.connectionMessage)
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun declareAndTapAndMultiCardMoveEachSendOneOriginal() {
        val f = BasicTableFixture(); f.connect()
        try {
            val ui = toolkit(f)
            val ids = listOf(f.id(40), f.id(41))
            assertTrue(ui.handle(TableIntent.Move(TableScope(ids.first(), ids),
                TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 0), BasicTableController.sources(f.play, ids))))
            assertEquals(1, f.commands().size)
            assertEquals(2, assertIs<GameAction.PlayBatch>(f.commands().single().action).actions.size)
            assertTrue(ui.declare(ids, true))
            val batch = assertIs<GameAction.PlayBatch>(f.commands().last().action)
            assertEquals(5, batch.actions.size)
            assertEquals(2, f.commands().size)
            val tree = f.lobby.displayTree()!!
            val nodes = f.commands().last().playMembers().map { tree.nodes.getValue(CommandIds.uuidV5(it.requestId, "board-node")) }
            assertEquals(nodes.dropLast(1).map { it.id }, nodes.drop(1).map { it.parentId })
            assertTrue(nodes.all { it.invocationId == f.commands().last().requestId })
            assertTrue(f.play.board.cards.filter { it.id in ids }.all { it.tapped })
            assertTrue(f.lobby.historyControl(GameAction.Undo))
            assertNull(f.play.board.combat)
            assertTrue(f.play.board.cards.filter { it.id in ids }.none { it.tapped })
        } finally { f.lobby.close(); f.worker.drain() }
    }

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
