package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.random.Random
import kotlin.test.*

/** Real gestures/toolkit -> production session queue/prediction -> authoritative shared tree rules. */
class StackProjectionJourneyTest {
    private class Journey(board: BoardState? = null, defaults: Boolean = true) : AutoCloseable {
        val f = BasicTableFixture()
        val initial = f.root.let { root ->
            val definition = f.definition.copy(faces = f.definition.faces.map { it.copy(rulesText = "Visible Stack rule") })
            val digest = DeckJson.digest(definition)
            val initialBoard = board ?: if (defaults) root.board.copy(cards = root.board.cards.map {
                it.copy(corner = "2/3", counters = emptyMap()) }) else root.board
            root.copy(definitions = listOf(SubmittedDefinition(digest, definition)),
                board = initialBoard.copy(cards = initialBoard.cards.map { it.copy(definition = digest) }))
        }
        var tree = GameplayTree(initial)
        var revision = 1
        var processed = 0
        val ui: TableToolkitController
        val gestures: TableGestureCoordinator
        init {
            f.connect(initial)
            assertEquals(initial.board, f.play.board)
            ui = TableToolkitController(f.actor, { f.play }, { true }, f.lobby::gameplay)
            gestures = TableGestureCoordinator({ intent -> when (intent) {
                is TableIntent.Selection -> ui.selectionState.value = intent.selection
                is TableIntent.Actions -> ui.openCard(gesturesCapture(intent), scene().sources)
                else -> ui.handle(intent)
            } })
        }
        private fun gesturesCapture(intent: TableIntent.Actions) = requireNotNull(intent.scope)
        fun scene(viewer: String = f.actor): TableScene {
            val base = TablePresentation.scene(f.game, f.play, viewer, 412f, 892f, false)
            return base.copy(stackBubbles = TablePresentation.stackBubbles(base, f.play, ui.stackBubbleEntry, 412f, 892f))
        }
        fun sync() {
            val prediction = f.play.board
            while (processed < f.commands().size) {
                val command = f.commands()[processed++]
                val before = tree
                val change = HistoryContinuation.change(before, f.actor, command)
                assertNotEquals("rejected", change.outcome, "${command.action}: ${change.reason}")
                f.message(GameReceipt(command.requestId, command.scope, processed.toString()))
                if (change.tree == null) {
                    assertEquals("noOp", change.outcome)
                    f.message(GameResult(command.requestId, command.scope, "noOp", ingressOrdinal = processed.toString()))
                } else {
                    tree = change.tree!!
                    val update = GameRevision(f.game.id, revision.toString(), (++revision).toString(), command.requestId,
                        f.actor, command.scope, f.game.copy(revision = revision.toString()), treeDelta = TreeSync.delta(before, tree))
                    val decoded = TreeSync.apply(TreeSync.generation(before), update.treeDelta!!).tree
                    assertEquals(tree.stateAt(tree.currentNodeId), decoded.stateAt(decoded.currentNodeId), "Cold delta replay: ${command.action}")
                    val result = GameResult(command.requestId, command.scope, "accepted", ingressOrdinal = processed.toString(),
                        commitRevision = revision.toString())
                    // Exercise both result-before-revision and revision-before-result queue retirement.
                    if (processed % 2 == 0) f.message(result)
                    f.message(update)
                    if (processed % 2 != 0) f.message(result)
                }
            }
            assertNull(ui.error)
            assertNull(f.lobby.state.error)
            assertNull(f.lobby.state.connectionMessage)
            assertTrue(f.game.id in f.lobby.state.connectedGames)
            assertFalse(f.game.id in f.lobby.state.pendingGames)
            val accepted = tree.stateAt(tree.currentNodeId)!!.board
            assertEquals(accepted, f.lobby.state.acceptedPlay!!.board, "Accepted endpoint after ${f.commands().lastOrNull()?.action}")
            assertEquals(accepted, f.play.board)
            assertEquals(prediction, f.play.board)
        }
        fun control(action: GameAction) {
            sync()
            assertTrue(f.lobby.historyControl(action))
            // Selective/disclosing controls may deliberately wait for the accepted tree.
            val command = f.commands().last()
            val before = tree
            val change = HistoryContinuation.change(before, f.actor, command)
            assertEquals("accepted", change.outcome, "${command.action}: ${change.reason}")
            tree = change.tree!!
            processed++
            val update = GameRevision(f.game.id, revision.toString(), (++revision).toString(), command.requestId,
                f.actor, command.scope, f.game.copy(revision = revision.toString()), treeDelta = TreeSync.delta(before, tree))
            f.message(update)
            f.message(GameResult(command.requestId, command.scope, "accepted", ingressOrdinal = processed.toString(),
                commitRevision = revision.toString()))
            assertNull(ui.error); assertNull(f.lobby.state.error); assertNull(f.lobby.state.connectionMessage)
            assertEquals(tree.stateAt(tree.currentNodeId)!!.board, f.play.board)
        }
        fun undoRedo() {
            val after = f.play.board
            control(GameAction.Undo); control(GameAction.Redo)
            assertEquals(after, f.play.board)
        }
        fun reversible(operation: () -> Unit) {
            val before = f.play.board
            val start = f.commands().size
            operation(); sync()
            val after = f.play.board
            val requests = f.commands().drop(start).map { it.requestId }.toSet()
            val mutations = tree.nodes.values.count { it.requestId in requests }
            repeat(mutations) { control(GameAction.Undo) }
            assertEquals(before, f.play.board, "Undo every individual request in the operation")
            repeat(mutations) { control(GameAction.Redo) }
            assertEquals(after, f.play.board, "Redo every individual request in the operation")
        }
        fun move(id: String, zone: PlayZone, owner: String = f.actor): Boolean {
            val target = f.container(zone, owner)
            val destination = if (zone == PlayZone.BATTLEFIELD) {
                val occupied = f.play.board.cards.filter { it.containerId == target }.mapNotNull { it.placement?.position }.toSet()
                TableDestination.Grid(target, generateSequence(0) { it + 1 }.first { BoardPosition(it, 1) !in occupied }, 1)
            } else TableDestination.Container(target, zone)
            return ui.handle(TableIntent.Move(TableScope(id, listOf(id)), destination, scene().sources))
        }
        fun ring(id: String, zone: PlayZone) {
            ui.openCard(TableScope(id, listOf(id)), scene().sources)
            val bound = ui.capture!!
            val tools = basicMoveTools(f.play, mapOf(f.actor to "A", f.other to "B"), true,
                TableScope(bound.contextViewId, bound.viewIds), bound.sources, ui::handle, ui::dismiss)
            val suffix = if (zone == PlayZone.LIBRARY) "library top" else zone.name.lowercase()
            tools.single { it.name == "Move to A $suffix" }.click()
        }
        fun drag(ids: List<String>, zone: PlayZone = PlayZone.STACK): TableDestination? {
            val current = scene()
            val id = ids.first()
            val source = current.items.firstOrNull { it.cardId == id }
                ?: current.items.first { it.kind == TableItemKind.PILE && id == it.cards.firstOrNull() && it.zone != PlayZone.STACK }
            val start = source.visible.map { it.center }.first { current.hit(it)?.id == source.id }
            ui.selectionState.value = TableSelection(ids = ids.toSet())
            gestures.update(current, ui.selectionState.value, true)
            val end = current.items.single { it.zone == zone && it.kind == TableItemKind.PILE }.bounds.center
            gestures.down(start, 0); gestures.move(end, 80)
            val preview = gestures.preview.destination
            gestures.up(end, 100)
            return preview
        }
        fun bubble(entry: String, zone: PlayZone) {
            ui.showStackBubbles(entry)
            val current = scene()
            if (current.stackBubbles == null) {
                // Entries below the three table peeks are reached through the real Contents route.
                ui.stackContents()
                assertTrue(entry in ui.publicContents()!!.cardIds)
                assertTrue(ui.handle(TableIntent.StackDestination(entry, zone)))
                return
            }
            gestures.update(current, TableSelection(), true)
            val bubble = current.stackBubbles.controls.single { it.id == "stackBubble/${zone.name.lowercase()}" }
            gestures.down(bubble.bounds.center, 500); gestures.up(bubble.bounds.center, 520)
        }
        override fun close() { f.lobby.close(); f.worker.drain() }
    }

    @Test fun dragEverySourceAndBubbleBothDirectionsWithUndoRedoAndServerPredictionAgreement() {
        for (source in listOf(PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.HAND))
            for (target in listOf(PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD)) Journey().use { j ->
                assertTrue(j.move(j.f.id(40), source)); j.sync()
                val sourceLocation = j.f.card(40).containerId
                j.reversible { assertNotNull(j.drag(listOf(j.f.id(40)))) }
                val entry = j.f.play.board.stackEffects.single().id
                assertEquals(sourceLocation, j.f.card(40).containerId)
                assertEquals(entry, j.ui.stackBubbleEntry)
                j.reversible { j.bubble(entry, target) }
                assertTrue(j.f.play.board.stackEffects.isEmpty())
                assertEquals(j.f.container(target), j.f.card(40).containerId)
            }
    }

    @Test fun ringMovesOnEntriesAndSourcesKeepLiveProjectionAndNeverSurfaceNoopResetErrors() {
        for (source in listOf(PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.HAND))
            for (target in listOf(PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.HAND, PlayZone.LIBRARY))
                for (onEntry in listOf(false, true)) Journey().use { j ->
                    assertTrue(j.move(j.f.id(40), source)); j.sync()
                    j.reversible { assertTrue(j.move(j.f.id(40), PlayZone.STACK)) }
                    val entry = j.f.play.board.stackEffects.single().id
                    j.reversible { j.ring(if (onEntry) entry else j.f.id(40), target) }
                    assertEquals(j.f.container(target), j.f.card(40).containerId)
                    assertEquals(j.f.card(40), CombatStackRules.liveSource(j.f.play.board, entry))
                    if (target == PlayZone.LIBRARY) {
                        assertTrue(j.scene(j.f.other).items.single { it.cardId == entry }.covered)
                        assertTrue(stackTextOptions(j.f.play, entry, j.f.other).isEmpty())
                        assertFalse(contentsEntries(j.f.play, PileContents(j.f.container(PlayZone.STACK), "Stack", listOf(entry), true),
                            j.f.other).single().faceVisible)
                    }
                    val bound = j.ui.bind(TableScope(entry, listOf(entry)))!!
                    j.reversible { assertTrue(j.ui.removeOccurrences(bound)) }
                }
    }

    @Test fun legacyRealStackCardRingGraveyardReplaysAndQueuesNoopConveniences() = Journey().use { j ->
        assertTrue(j.move(j.f.id(40), PlayZone.BATTLEFIELD)); j.sync()
        val card = j.f.card(40)
        // Deliberate old-client wire request: production UI may no longer construct this path.
        assertTrue(j.f.lobby.gameplay(PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement),
            MoveDestination(j.f.container(PlayZone.STACK), MoveInsertion.TOP), GameplayRules.stackDigest(j.f.play))))
        j.sync(); j.undoRedo()
        j.ring(card.id, PlayZone.GRAVEYARD); j.sync(); j.undoRedo()
        assertEquals(j.f.container(PlayZone.GRAVEYARD), j.f.card(40).containerId)
    }

    @Test fun ownerReportedMoveThenRingGraveyardWorksBeforeAnyAcknowledgementForOldAndNewPaths() {
        for (legacy in listOf(false, true)) for (defaults in listOf(false, true)) Journey(defaults = defaults).use { j ->
            assertTrue(j.move(j.f.id(40), PlayZone.BATTLEFIELD)); j.sync()
            if (legacy) assertTrue(j.f.legacyStack(40)) else assertNotNull(j.drag(listOf(j.f.id(40))))
            val entry = if (legacy) j.f.id(40) else j.f.play.board.stackEffects.single().id
            j.ring(entry, PlayZone.GRAVEYARD)
            assertNull(j.ui.error)
            j.sync(); j.undoRedo()
            assertEquals(j.f.container(PlayZone.GRAVEYARD), j.f.card(40).containerId)
        }
    }

    @Test fun pendingStackRingMovesAndTerminalUndoRedoShareOnePredictedQueue() {
        for (legacy in listOf(false, true)) Journey().use { j ->
            assertTrue(j.move(j.f.id(40), PlayZone.BATTLEFIELD)); j.sync()
            if (legacy) assertTrue(j.f.legacyStack(40)) else assertNotNull(j.drag(listOf(j.f.id(40))))
            val entry = if (legacy) j.f.id(40) else j.f.play.board.stackEffects.single().id
            j.ring(entry, PlayZone.GRAVEYARD)
            val graveyard = j.f.play.board
            assertTrue(j.f.lobby.historyControl(GameAction.Undo))
            assertEquals(j.f.container(if (legacy) PlayZone.STACK else PlayZone.BATTLEFIELD), j.f.card(40).containerId)
            assertTrue(j.f.lobby.historyControl(GameAction.Redo))
            assertEquals(graveyard, j.f.play.board)
            // Move again before any result/revision, using the predecessor predicted by Redo.
            j.ring(j.f.id(40), PlayZone.HAND)
            j.sync()
            assertEquals(j.f.container(PlayZone.HAND), j.f.card(40).containerId)
        }
    }

    @Test fun mixedSourceMultiDragProjectsEachCardAndEveryBubbleRequestCanBeUndoneAndRedone() = Journey(defaults = false).use { j ->
        val zones = listOf(PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.HAND)
        zones.forEachIndexed { index, zone -> assertTrue(j.move(j.f.id(40 + index), zone)) }
        j.sync()
        j.reversible { assertNotNull(j.drag((40..43).map(j.f::id))) }
        assertEquals((40..43).map(j.f::id).toSet(), j.f.play.board.stackEffects.map { it.sourceId }.toSet())
        j.f.play.board.stackEffects.toList().forEachIndexed { index, effect ->
            j.reversible { j.bubble(effect.id, if (index % 2 == 0) PlayZone.BATTLEFIELD else PlayZone.GRAVEYARD) }
        }
        assertTrue(j.f.play.board.stackEffects.isEmpty())
    }

    @Test fun battlefieldBubbleReturnsASourceOnTheOtherPlayersFieldToItsOwner() = Journey().use { j ->
        assertTrue(j.move(j.f.id(40), PlayZone.BATTLEFIELD, j.f.other)); j.sync()
        j.reversible { assertTrue(j.move(j.f.id(40), PlayZone.STACK)) }
        val entry = j.f.play.board.stackEffects.single().id
        j.reversible { j.bubble(entry, PlayZone.BATTLEFIELD) }
        assertEquals(j.f.container(PlayZone.BATTLEFIELD), j.f.card(40).containerId)
    }

    @Test fun accessibilityAndDirectSubmitUseProjectionsAndRingOffersExactlyOneStackEntry() = Journey().use { j ->
        assertTrue(j.move(j.f.id(40), PlayZone.HAND)); j.sync()
        val source = j.f.card(40)
        val tools = basicMoveTools(j.f.play, emptyMap(), true, TableScope(source.id, listOf(source.id)),
            j.scene().sources, j.ui::handle, {})
        assertEquals(1, tools.count { it.name.contains("Stack") })
        tools.single { it.name == "Move to Stack" }.click()
        val rawMove = PlayAction.MoveCard(source.id, MoveSource(source.containerId),
            MoveDestination(j.f.container(PlayZone.STACK), MoveInsertion.TOP))
        assertTrue(j.ui.submit(rawMove))
        val current = j.scene()
        j.gestures.update(current, TableSelection(), true)
        j.gestures.accessibleMove(current.items.single { it.cardId == source.id },
            TableDestination.Container(j.f.container(PlayZone.STACK), PlayZone.STACK))
        assertEquals(3, j.f.play.board.stackEffects.size)
        j.sync(); j.undoRedo()
    }

    @Test fun loggedContentsMoveControlsProjectPublicCardsButNeverLibraryCards() {
        for (zone in listOf(PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.HAND)) Journey().use { j ->
            assertTrue(j.move(j.f.id(40), zone)); j.sync()
            val controller = j.ui.libraryController(j.f.container(zone), j.f.container(PlayZone.HAND))!!
            val target = libraryDestinations(j.f.play.board, sourceIds = listOf(j.f.id(40))).single {
                it.second.containerId == j.f.container(PlayZone.STACK) }.second
            j.reversible { assertEquals(LibrarySubmission(1, 0), controller.moveCards(listOf(j.f.id(40)), target)) }
            assertEquals(j.f.container(zone), j.f.card(40).containerId)
            assertEquals(j.f.play.board.stackEffects.single().id, j.ui.stackBubbleEntry)
        }
    }

    @Test fun multiSelectionLibraryRejectionAndOpponentHandRevealLifetime() = Journey().use { j ->
        val library = j.scene().items.single { it.zone == PlayZone.LIBRARY && it.owner == j.f.actor }
        val before = j.f.commands().size
        assertNull(j.drag(listOf(j.f.id(40))))
        assertFalse(j.move(j.f.id(40), PlayZone.STACK))
        val controller = j.ui.libraryController(library.id, j.f.container(PlayZone.HAND))!!
        assertEquals(LibrarySubmission(0, 1), controller.move(ContentsSelection.TOP, 1,
            MoveDestination(j.f.container(PlayZone.STACK), MoveInsertion.TOP)))
        assertEquals(LibrarySubmission(0, 1), controller.moveCards(listOf(j.f.id(40)),
            MoveDestination(j.f.container(PlayZone.STACK), MoveInsertion.TOP)))
        assertTrue(libraryDestinations(j.f.play.board, sourceIds = listOf(j.f.id(40))).none {
            it.second.containerId == j.f.container(PlayZone.STACK) })
        assertEquals(before, j.f.commands().size)
        assertTrue(libraryDestinations(j.f.play.board).none { it.second.containerId == j.f.container(PlayZone.STACK) })
        assertTrue(j.move(j.f.id(40), PlayZone.HAND)); assertTrue(j.move(j.f.id(41), PlayZone.HAND))
        assertTrue(j.move(j.f.id(42), PlayZone.HAND)); j.sync()
        assertTrue(j.scene(j.f.other).items.single { it.cardId == j.f.id(40) }.covered)
        j.reversible { assertNotNull(j.drag(listOf(j.f.id(40), j.f.id(41)))) }
        assertEquals(2, j.f.play.board.stackEffects.size)
        val opponent = TableToolkitController(j.f.other, { j.f.play }, { true }, { _, _ -> error("Local inspection only") })
        assertTrue(j.scene(j.f.other).items.single { it.cardId == j.f.id(42) }.covered)
        opponent.inspect(j.f.id(42)); assertTrue(opponent.inspection!!.faceIds.isEmpty())
        for (effect in j.f.play.board.stackEffects) {
            assertFalse(j.scene(j.f.other).items.single { it.cardId == effect.sourceId }.covered)
            assertFalse(j.scene(j.f.other).items.single { it.cardId == effect.id }.covered)
            assertTrue(stackTextOptions(j.f.play, effect.id, j.f.other).isNotEmpty())
            opponent.inspect(effect.id); assertTrue(opponent.inspection!!.faceIds.isNotEmpty())
            opponent.inspect(effect.sourceId); assertTrue(opponent.inspection!!.faceIds.isNotEmpty())
        }
        // Multiple effects on one source: only removal of the last one hides it again.
        assertTrue(j.ui.addEffects(listOf(j.f.id(40)))); j.sync()
        val effects = j.f.play.board.stackEffects.filter { it.sourceId == j.f.id(40) }
        for ((index, effect) in effects.withIndex()) {
            assertTrue(j.ui.removeOccurrences(j.ui.bind(TableScope(effect.id, listOf(effect.id)))!!)); j.sync(); j.undoRedo()
            assertEquals(index == effects.lastIndex, j.scene(j.f.other).items.single { it.cardId == j.f.id(40) }.covered)
        }
        opponent.inspect(j.f.id(40)); assertTrue(opponent.inspection!!.faceIds.isEmpty())
    }

    @Test fun seededUserOperationWalksStayAcceptedAndPredictionConverges() {
        for (seed in listOf(128, 2026, 7301, 99173)) {
            val random = Random(seed)
            var board: BoardState? = null
            // Short retained-history windows keep serialization/cold Undo replay bounded. The board
            // and RNG continue across windows; every window uses the real production session.
            repeat(10) { window -> Journey(board).use { j ->
                repeat(80) { local ->
                    val step = window * 80 + local
                    try {
                        val card = j.f.play.board.cards.random(random)
                        val effects = j.f.play.board.stackEffects
                        when (random.nextInt(12)) {
                            0 -> if (CombatStackRules.canProject(j.f.play.board, card)) j.move(card.id, PlayZone.STACK)
                            1 -> if (effects.isNotEmpty()) j.bubble(effects.random(random).id,
                                if (random.nextBoolean()) PlayZone.BATTLEFIELD else PlayZone.GRAVEYARD)
                            2 -> if (effects.isNotEmpty()) {
                                val effect = effects.random(random)
                                j.ring(if (random.nextBoolean()) effect.id else effect.sourceId,
                                    listOf(PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.HAND, PlayZone.LIBRARY).random(random))
                            }
                            3 -> if (effects.isNotEmpty()) {
                                val id = effects.random(random).id
                                j.ui.removeOccurrences(j.ui.bind(TableScope(id, listOf(id)))!!)
                            }
                            4 -> {
                                val piles = basicPileDestinations(j.f.play, TableScope(card.id, listOf(card.id)))
                                if (piles.isNotEmpty()) j.ui.handle(TableIntent.Move(TableScope(card.id, listOf(card.id)),
                                    piles.random(random), j.scene().sources))
                            }
                            5 -> j.ui.handle(TableIntent.Rotate(TableScope(card.id, listOf(card.id)), !card.tapped))
                            6 -> assertTrue(j.ui.submit(PlayAction.NextStep))
                            7 -> if (CombatStackRules.canProject(j.f.play.board, card) && j.scene().items.any {
                                    (it.cardId == card.id || card.id == it.cards.firstOrNull()) &&
                                        it.visible.any { bounds -> bounds.contains(it.bounds.center) } }) j.drag(listOf(card.id))
                            10, 11 -> {
                                j.sync()
                                val action = if (random.nextBoolean()) GameAction.Undo else GameAction.Redo
                                val probe = j.f.data.command(PlayAction.NextStep).copy(action = action)
                                if (HistoryContinuation.change(j.tree, j.f.actor, probe).outcome == "accepted") j.control(action)
                            }
                            else -> j.move(card.id, PlayZone.entries.filter { it != PlayZone.STACK }.random(random))
                        }
                        // Keep short bursts unacknowledged so later gestures/rings capture the
                        // production queue's prediction, not just the last accepted server board.
                        if (random.nextInt(4) == 0) j.sync()
                        if (random.nextInt(8) == 0 && j.tree.nodes.isNotEmpty()) {
                            j.sync()
                            // Do not submit controls that the real UI would disable for disclosure.
                            val probe = j.f.data.command(PlayAction.NextStep).copy(action = GameAction.Undo)
                            if (HistoryContinuation.change(j.tree, j.f.actor, probe).outcome == "accepted") j.undoRedo()
                        }
                    } catch (failure: Throwable) { throw AssertionError("seed=$seed step=$step", failure) }
                }
                // A real incarnation root contains cards, not occurrence IDs. Finish the window
                // with ordinary remove-effect operations before using its board as the next root.
                j.f.play.board.stackEffects.toList().forEach { effect ->
                    assertTrue(j.ui.removeOccurrences(j.ui.bind(TableScope(effect.id, listOf(effect.id)))!!))
                }
                j.sync()
                board = j.f.play.board
            } }
        }
    }
}
