package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

internal class GameplayFixture {
    fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    val actor = id(1); val other = id(2)
    val root = PlayRoot(id(3), id(4))
    val definition = CardDefinition("Retained", listOf(DefinitionFace(id(11), "Retained face")))
    val initial = PlayState(id(5), id(6), id(7), id(8), root, root.id,
        BoardState(listOf(PlayPlayer(actor), PlayPlayer(other)),
            listOf(PlayCard(id(9), DeckJson.digest(definition), actor, id(10), id(11),
                placement = PlayPlacement(BoardPosition(0, 0), id(40)))),
            listOf(PlayContainer(id(10), PlayZone.BATTLEFIELD, actor, listOf(id(9)))), TurnState(actor)),
        listOf(SubmittedDefinition(DeckJson.digest(definition), definition)), emptyList(), InitialStarter(StarterChoice(actor), actor))
    val game = GameSummary(initial.gameId, "Replica", "2026-10-23T00:00:00Z", AccountIdentity(actor, "A"),
        AccountIdentity(other, "B"), initial.preparationRound, id(12), InvitationState.ACCEPTED,
        lifecycle = GameLifecycle.ACTIVE, revision = "1", playId = initial.id)
    var sequence = 100
    fun command(action: PlayAction) = GameCommand(id(sequence++), GameScope(game.id, playId = initial.id),
        GameAction.Play(action), id(13), "1")
    fun head(state: PlayState = initial, revision: String = "1") = GameHead(game.id, revision, game.copy(revision = revision), play = state)
    fun update(from: PlayState, command: GameCommand, base: String, result: String, author: String = actor): GameRevision {
        val next = requireNotNull(GameplayRules.change(from, author, command).state)
        return GameRevision(game.id, base, result, command.requestId, author, command.scope, game.copy(revision = result), play = next)
    }
    fun result(command: GameCommand, revision: String? = null, outcome: String = "accepted") =
        GameResult(command.requestId, command.scope, outcome, ingressOrdinal = "1", commitRevision = revision)
    fun snapshot(vararg updates: GameRevision): GameplaySnapshot {
        val start = GameRevision(game.id, "0", "1", initial.startRequestId, actor,
            GameScope(game.id, game.preparationRound), game, play = initial)
        val last = updates.lastOrNull()
        return GameplaySnapshot(head(last?.play ?: initial, last?.resultRevision ?: "1"), listOf(start) + updates)
    }
    fun life(state: PlayState?) = state!!.board.players.first().life
}

class GameplayReplicaTest {
    @Test fun concernBasedUndoRedoPredictionMatchesAuthoritativeEngineIncludingDisclosuresAndVacancy() {
        val f = GameplayFixture()
        val initial = f.initial.let { state -> state.copy(board = state.board.copy(
            cards = listOf(state.board.cards.single().copy(covered = false,
                placement = PlayPlacement(BoardPosition(0, 0), f.id(40))),
                state.board.cards.single().copy(id = f.id(41), containerId = f.id(42), placement = null)),
            containers = listOf(state.board.containers.single().copy(zone = PlayZone.BATTLEFIELD),
                PlayContainer(f.id(42), PlayZone.HAND, f.other, listOf(f.id(41)))))) }
        val replica = GameplayReplica()
        assertTrue(replica.root(f.head(initial)))
        var authoritative = GameplayTree(initial)
        val inspect = f.command(PlayAction.InspectFace(f.id(9), f.id(11), f.id(10)))
        val undo = f.command(PlayAction.PassAttention).copy(action = GameAction.Undo)
        val redo = f.command(PlayAction.PassAttention).copy(action = GameAction.Redo)
        for (command in listOf(inspect, undo, redo)) {
            assertTrue(replica.predict(command, f.actor))
            val change = HistoryContinuation.change(authoritative, f.actor, command)
            authoritative = assertNotNull(change.tree, change.reason)
            assertEquals(authoritative.nodes, replica.displayTree!!.nodes)
            assertEquals(authoritative.stateAt(authoritative.currentNodeId), replica.display)
        }
        assertTrue(authoritative.nodes.values.single().transition.disclosures.isNotEmpty())

        authoritative = GameplayTree(initial)
        val move = f.command(PlayAction.MoveCard(f.id(9), MoveSource(f.id(10), initial.board.cards.first().placement),
            MoveDestination(f.id(10), MoveInsertion.EMPTY, BoardPosition(1, 0))))
        authoritative = assertNotNull(HistoryContinuation.change(authoritative, f.actor, move).tree)
        val arrival = f.command(PlayAction.MoveCard(f.id(41), MoveSource(f.id(42)),
            MoveDestination(f.id(10), MoveInsertion.EMPTY, BoardPosition(0, 0))))
        authoritative = assertNotNull(HistoryContinuation.change(authoritative, f.other, arrival).tree)
        val collisionReplica = GameplayReplica()
        val endpoint = authoritative.stateAt(authoritative.currentNodeId)!!
        assertTrue(collisionReplica.published(f.head(endpoint, "3"), TreeSync.generation(authoritative), "3", emptyList(), emptyList()))
        val blockedUndo = f.command(PlayAction.PassAttention).copy(action = GameAction.Undo)
        assertEquals("replayBlocked", HistoryContinuation.change(authoritative, f.actor, blockedUndo).reason)
        assertFalse(collisionReplica.predict(blockedUndo, f.actor))
        assertEquals(endpoint, collisionReplica.display)
    }

    @Test fun resultFirstDeltaFirstDuplicateAndNoOpPublishOnlyNetEndpoints() {
        val f = GameplayFixture(); val replica = GameplayReplica(); assertTrue(replica.root(f.head()))
        val commands = listOf("5", "2", "-1").map { f.command(PlayAction.AdjustLife(f.actor, it)) }
        commands.forEach { assertTrue(replica.predict(it, f.actor)) }
        val target = replica.display!!.board
        assertEquals("26", f.life(replica.display)); assertEquals("20", f.life(replica.accepted))
        replica.result(f.result(commands[0], "2")); assertEquals(target, replica.display!!.board)
        val first = f.update(f.initial, commands[0], "1", "2")
        assertTrue(replica.apply(first)); assertEquals(target, replica.display!!.board)
        val second = f.update(first.play!!, commands[1], "2", "3")
        assertTrue(replica.apply(second)); assertEquals(target, replica.display!!.board)
        replica.result(f.result(commands[1], "3")); replica.result(f.result(commands[1], "3"))
        assertTrue(replica.apply(first)); assertEquals("3", replica.revision); assertEquals(target, replica.display!!.board)
        replica.result(f.result(commands[2], outcome = "noOp"))
        assertEquals("27", f.life(replica.display)); assertTrue(replica.pendingIds.isEmpty())
        assertEquals(CommandIds.uuidV5(commands[1].requestId, "board-node"), replica.accepted!!.currentNodeId)
    }
    @Test fun failedPredictionsStayWithdrawnWhileIndependentOriginalAndLateAcceptedWorkSurvive() {
        val f = GameplayFixture(); val replica = GameplayReplica(); replica.root(f.head())
        val first = f.command(PlayAction.AdjustLife(f.actor, "1"))
        val second = f.command(PlayAction.AdjustLife(f.actor, "2"))
        val independent = f.command(PlayAction.SetTapped(f.id(9), true))
        listOf(first, second, independent).forEach { assertTrue(replica.predict(it, f.actor)) }
        val remote = f.update(f.initial, f.command(PlayAction.SetLife(f.actor, "lost")), "1", "2", f.other)
        assertTrue(replica.apply(remote)); assertEquals("lost", f.life(replica.display))
        assertTrue(replica.display!!.board.cards.single().tapped); assertEquals(3, replica.pendingIds.size)
        val restore = f.update(remote.play!!, f.command(PlayAction.SetLife(f.actor, "20")), "2", "3", f.other)
        assertTrue(replica.apply(restore)); assertEquals("20", f.life(replica.display))
        val late = f.update(restore.play!!, first, "3", "4")
        assertTrue(replica.apply(late)); assertEquals("21", f.life(replica.display))
        assertTrue(replica.display!!.board.cards.single().tapped)
        replica.result(f.result(second, outcome = "rejected")); assertEquals("21", f.life(replica.display))
        assertEquals(listOf(independent.requestId), replica.pendingIds)
    }
    @Test fun stagedBaselineCatchesUpBeforeAtomicSwitchAndNeverRewindsPublishedAcceptedState() {
        val f = GameplayFixture(); val replica = GameplayReplica(); replica.root(f.head())
        val own = f.command(PlayAction.AdjustLife(f.actor, "5")); replica.predict(own, f.actor)
        val first = f.update(f.initial, own, "1", "2")
        val second = f.update(first.play!!, f.command(PlayAction.SetTapped(f.id(9), true)), "2", "3", f.other)
        replica.apply(first); replica.apply(second)
        val published = replica.display
        assertFalse(replica.baseline(GameplayBaseline(f.snapshot(), emptyList())))
        assertEquals(published, replica.display); assertEquals("3", replica.revision)
        assertTrue(replica.baseline(GameplayBaseline(f.snapshot(), emptyList()), listOf(second, first, first)))
        assertEquals(published, replica.display); assertEquals(3, replica.retainedRecords.size)
        val invalid = f.snapshot(first).let { it.copy(records = it.records.drop(1)) }
        assertFailsWith<IllegalArgumentException> { replica.baseline(GameplayBaseline(invalid, emptyList())) }
        assertEquals(published, replica.display)
    }
    @Test fun baselineCrossingUnresolvedOriginalRequiresCorrelationAndResultAloneDoesNotInstallState() {
        val f = GameplayFixture(); val replica = GameplayReplica(); replica.root(f.head())
        val own = f.command(PlayAction.AdjustLife(f.actor, "5")); replica.predict(own, f.actor)
        assertFalse(replica.baseline(GameplayBaseline(f.snapshot(), emptyList())))
        assertFalse(replica.baseline(GameplayBaseline(f.snapshot(), listOf(GameRequestStatus(own.requestId, "unknown")))))
        assertTrue(replica.baseline(GameplayBaseline(f.snapshot(), listOf(GameRequestStatus(own.requestId, "pending")))))
        assertEquals("25", f.life(replica.display)); assertEquals("20", f.life(replica.accepted))
        val first = f.update(f.initial, own, "1", "2")
        assertTrue(replica.baseline(GameplayBaseline(f.snapshot(first), listOf(GameRequestStatus(own.requestId, "final", f.result(own, "2"))))))
        assertEquals("25", f.life(replica.display)); assertEquals("25", f.life(replica.accepted)); assertTrue(replica.pendingIds.isEmpty())
    }
    @Test fun nonBoardRecordsAdvanceSameNodeRevisionAndPreservePredictionInIncrementalAndBaselinePaths() {
        val f = GameplayFixture(); val replica = GameplayReplica(); replica.root(f.head())
        val own = f.command(PlayAction.AdjustLife(f.actor, "1")); replica.predict(own, f.actor)
        val record = GameRevision(f.game.id, "1", "2", f.id(700), f.other,
            GameScope(f.game.id, playId = f.initial.id), f.game.copy(revision = "2"),
            chatMessage = ChatMessage(f.id(700), f.game.id, f.initial.id, f.other, "not a board record", "2", "date"))
        assertTrue(replica.apply(record)); assertEquals("2", replica.revision)
        assertNull(replica.retainedRecords.single().chatMessage)
        assertEquals(f.root.id, replica.accepted!!.currentNodeId); assertEquals("21", f.life(replica.display))
        val snapshot = f.snapshot().let { it.copy(head = f.head(revision = "2"), records = it.records + record) }
        assertTrue(replica.baseline(GameplayBaseline(snapshot, listOf(GameRequestStatus(own.requestId, "pending")))))
        assertEquals("21", f.life(replica.display)); assertEquals(2, replica.retainedRecords.size)
        assertTrue(replica.retainedRecords.all { it.chatMessage == null })
    }
}
