package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

/** The same production adapter mounted by TableScreen, feeding LobbyController/GameSession. */
class TableToolkitWiringTest {
    @Test fun resetColoredCountersIsOneAtomicBatchPreservesCornerAndUndoRestoresEveryColor() {
        val f = BasicTableFixture()
        val id = f.id(40)
        val root = f.root.copy(board = f.root.board.copy(cards = f.root.board.cards.map { card ->
            if (card.id != id) card else card.copy(containerId = f.container(PlayZone.BATTLEFIELD), covered = false,
                placement = PlayPlacement(BoardPosition(0, 0), f.id(600)), corner = "8/9", eotCorner = "+1/+0",
                counters = CounterColor.entries.associateWith { if (it == CounterColor.GRAY) "0" else "marked" })
        }, containers = f.root.board.containers.map { container -> when (container.id) {
            f.container(PlayZone.LIBRARY) -> container.copy(cards = container.cards - id)
            f.container(PlayZone.BATTLEFIELD) -> container.copy(cards = listOf(id))
            else -> container
        } }))
        f.connect(root)
        try {
            val ui = toolkit(f)
            ui.openCard(TableScope(id, listOf(id)))
            val actions = resetCardCounters(f.play, ui.manual.scope!!)
            assertEquals(CounterColor.entries.map { PlayAction.ClearCardCounter(id, it) }, actions)
            ui.submitAll(actions)
            val command = f.commands().single()
            assertEquals(actions, assertIs<GameAction.PlayBatch>(command.action).actions)
            val changed = f.play.board.cards.single { it.id == id }
            assertTrue(changed.counters.isEmpty())
            assertEquals("8/9", changed.corner)
            assertEquals("+1/+0", changed.eotCorner)
            val server = assertNotNull(HistoryContinuation.change(GameplayTree(root), f.actor, command).tree)
            assertEquals(f.play.board, server.stateAt(server.currentNodeId)!!.board)
            assertTrue(f.lobby.historyControl(GameAction.Undo))
            assertEquals(root.board, f.play.board)
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun drawUndoSearchCloseVariesTheNextDrawThroughTheSessionAndMatchesServer() {
        for (keepTop in listOf(false, true)) {
            val f = BasicTableFixture(); f.connect(); val ui = toolkit(f)
            var server = GameplayTree(f.root)
            var processed = 0
            fun verifyServer() {
                for (command in f.commands().drop(processed)) {
                    val change = HistoryContinuation.change(server, f.actor, command)
                    server = assertNotNull(change.tree, change.reason)
                    processed++
                }
                assertEquals(server.stateAt(server.currentNodeId)!!.board, f.play.board)
            }
            try {
                val library = f.container(PlayZone.LIBRARY)
                val hand = f.container(PlayZone.HAND)
                if (keepTop) assertTrue(ui.submit(PlayAction.SetKeepTopRevealed(library, true)))
                val drawn = mutableSetOf<String>()
                repeat(20) {
                    ui.openLibrary(library)
                    assertEquals(1, ui.library!!.controller.draw().submitted)
                    drawn += f.play.board.containers.single { it.id == hand }.cards.single()
                    assertTrue(f.lobby.historyControl(GameAction.Undo))
                    ui.openLibrary(library)
                    ui.showLibraryContents(ContentsSelection.ALL, 1, false)
                    ui.closeCardPanel(f.play.board.contentsDisplays.single())
                    assertTrue(f.play.board.contentsDisplays.isEmpty())
                    verifyServer()
                }
                assertTrue(drawn.size > 1, "Search close pins draws with keepTop=$keepTop")
            } finally { f.lobby.close(); f.worker.drain() }
        }
    }

    @Test fun closingALibraryRevealStillAsksWhereTheRestGoes() {
        val f = BasicTableFixture(); f.connect(); val ui = toolkit(f)
        try {
            ui.openLibrary(f.container(PlayZone.LIBRARY))
            ui.showLibraryContents(ContentsSelection.TOP, 2, true)
            val display = f.play.board.contentsDisplays.single()
            assertTrue(display.reveal)
            ui.closeCardPanel(display)
            assertEquals("panelRest/${display.id}", ui.panel)
            assertEquals(listOf(display.id), f.play.board.contentsDisplays.map { it.id })
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun opponentsLibraryOpensTheSameToolsAndActionsKeepOwnerZonesAndActingPanelAuthor() {
        val f = BasicTableFixture()
        val root = f.root.copy(board = f.root.board.copy(
            cards = f.root.board.cards.map { it.copy(owner = f.other, containerId = f.container(PlayZone.LIBRARY, f.other)) },
            containers = f.root.board.containers.map { container -> container.copy(cards =
                if (container.id == f.container(PlayZone.LIBRARY, f.other)) f.root.board.cards.map { it.id } else emptyList()) }))
        f.connect(root)
        val ui = toolkit(f)
        try {
            val library = f.container(PlayZone.LIBRARY, f.other)
            val hand = f.container(PlayZone.HAND, f.other)
            val graveyard = f.container(PlayZone.GRAVEYARD, f.other)
            fun open(): LibraryController {
                ui.openLibrary(library)
                assertEquals("library", ui.panel)
                return assertNotNull(ui.library).controller.also { assertEquals(hand, it.handId) }
            }
            assertEquals(1, open().draw().submitted)
            assertEquals(hand, f.card(40).containerId)
            assertEquals(1, open().move(ContentsSelection.TOP, 1, MoveDestination(graveyard, MoveInsertion.UNORDERED)).submitted)
            assertEquals(graveyard, f.card(41).containerId)
            for (selection in ContentsSelection.entries) for (reveal in listOf(false, true)) {
                open()
                ui.showLibraryContents(selection, 1, reveal)
                val display = f.play.board.contentsDisplays.single()
                assertEquals(f.actor, display.author)
                assertEquals(library, display.containerId)
                ui.resolveCardPanel(display, ContentsRest.BOTTOM)
                assertTrue(f.play.board.contentsDisplays.isEmpty())
                assertTrue(f.play.board.containers.single { it.id == library }.cards.all { id ->
                    f.play.board.cards.single { it.id == id }.owner == f.other })
            }
            assertEquals(1, open().shuffle().submitted)
            val controller = open()
            val displayed = ui.library!!.handIds
            val mulligan = controller.mulligan(displayed, count = 2)
            assertEquals(0, mulligan.refused)
            assertEquals(2, f.play.board.containers.single { it.id == hand }.cards.size)
        } finally { f.lobby.close(); f.worker.drain() }
    }

    private fun quick(ui: TableToolkitController, name: String): Boolean {
        val play = ui.current()!!
        return ui.perform(QuickActions.contextual(play,
            toolkitQuickContext(play, ui.selectionState.value, null, ui.viewer), ui.table).single { it.name == name }.intent)
    }

    @Test fun turnShortcutsShareOneInvocationAndUndoTheWholeReproducibleDrawShortcut() {
        for ((position, name) in listOf(TurnPosition.UNTAP to "Untap, draw → main", TurnPosition.UPKEEP to "Draw + go to main")) {
            val f = BasicTableFixture(); f.connect(); val ui = toolkit(f)
            try {
                assertTrue(f.move(40, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 0), true))
                assertTrue(ui.submit(PlayAction.SetTapped(f.id(40), true)))
                assertTrue(ui.submit(PlayAction.ChooseStep(position)))
                val count = f.commands().size
                assertTrue(quick(ui, name))
                val commands = f.commands().drop(count)
                assertEquals(1, commands.map { it.invocationId }.distinct().size)
                assertNotNull(commands.first().invocationId)
                assertEquals(TurnPosition.PRECOMBAT_MAIN, TurnPosition.of(f.play.board.turn))
                assertEquals(f.container(PlayZone.HAND), f.card(41).containerId)
                assertEquals(position != TurnPosition.UNTAP, f.card(40).tapped)
                val before = f.play.board
                // Return to the exact pre-shortcut boundary, including its draw and untap.
                assertTrue(f.lobby.historyControl(GameAction.Undo))
                assertEquals(position, TurnPosition.of(f.play.board.turn))
                assertEquals(f.container(PlayZone.LIBRARY), f.card(41).containerId)
                assertTrue(f.card(40).tapped)
                assertTrue(f.lobby.historyControl(GameAction.Redo))
                assertEquals(before, f.play.board)
            } finally { f.lobby.close(); f.worker.drain() }
        }
    }

    @Test fun attackShortcutsOwnOnlyAutomaticModeAndDeclarationsClearSelectionInOneInvocation() {
        for (tap in listOf(false, true)) {
            val f = BasicTableFixture(); f.connect(); val ui = toolkit(f)
            try {
                for (id in 40..41) assertTrue(f.move(id, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), id - 40, 0), true))
                val scope = ui.table.scope()!!
                assertTrue(ui.perform(TableActionIntent.Choose(scope, TurnPosition.PRECOMBAT_MAIN)))
                assertTrue(quick(ui, "Select attackers"))
                assertEquals(TurnPosition.DECLARE_ATTACKERS, TurnPosition.of(f.play.board.turn))
                assertEquals(TableMode.SELECT, ui.selectionState.value.mode)
                assertTrue(ui.selectionState.value.attackSelection)
                assertTrue(ui.perform(TableActionIntent.Next(scope)))
                assertEquals(TableMode.NORMAL, ui.selectionState.value.mode)
                assertTrue(ui.perform(TableActionIntent.Choose(scope, TurnPosition.DECLARE_ATTACKERS)))
                assertTrue(quick(ui, "Select attackers"))
                ui.selectionState.value = ui.selectionState.value.copy(ids = setOf(f.id(40), f.id(41)))
                val before = f.play.board
                val count = f.commands().size
                assertTrue(quick(ui, if (tap) "Declare + tap" else "Declare without tapping"))
                assertEquals(TableSelection(), ui.selectionState.value)
                assertEquals(2, CombatStackRules.occurrences(f.play.board.combat).size)
                assertEquals(tap, f.card(40).tapped)
                assertEquals(1, f.commands().drop(count).map { it.invocationId }.distinct().size)
                assertTrue(f.lobby.historyControl(GameAction.Undo))
                assertEquals(before, f.play.board)

                // A manually chosen Select survives a shortcut and subsequent step change.
                ui.selectionState.value = TableSelection(mode = TableMode.SELECT)
                assertTrue(quick(ui, "Select attackers"))
                assertFalse(ui.selectionState.value.attackSelection)
                assertTrue(ui.perform(TableActionIntent.Next(scope)))
                assertEquals(TableMode.SELECT, ui.selectionState.value.mode)
            } finally { f.lobby.close(); f.worker.drain() }
        }
    }

    @Test fun noAttackClosesOnlyEmptyCombatAndGroupsWithProgression() {
        val f = BasicTableFixture(); f.connect(); val ui = toolkit(f)
        try {
            val scope = ui.table.scope()!!
            assertTrue(ui.submit(PlayAction.ChooseStep(TurnPosition.DECLARE_ATTACKERS)))
            assertTrue(ui.submit(PlayAction.Combat(CombatAction.Open)))
            assertTrue(quick(ui, "Select attackers"))
            val before = f.play.board
            val count = f.commands().size
            assertTrue(quick(ui, "No attack"))
            assertEquals(TurnPosition.POSTCOMBAT_MAIN, TurnPosition.of(f.play.board.turn))
            assertNull(f.play.board.combat)
            assertEquals(TableMode.NORMAL, ui.selectionState.value.mode)
            assertEquals(1, f.commands().drop(count).map { it.invocationId }.distinct().size)
            assertTrue(f.lobby.historyControl(GameAction.Undo))
            assertEquals(before, f.play.board)
            ui.selectionState.value = TableSelection(mode = TableMode.SELECT, attackSelection = true)
            assertTrue(ui.submit(PlayAction.NextStep)) // Sync/other controls, rather than a quick action.
            ui.refreshAttackSelection()
            assertEquals(TableMode.NORMAL, ui.selectionState.value.mode)
            assertTrue(ui.perform(TableActionIntent.Choose(scope, TurnPosition.PRECOMBAT_MAIN)))
            assertTrue(quick(ui, "Skip combat"))
            assertEquals(TurnPosition.END_STEP, TurnPosition.of(f.play.board.turn))
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun selectedDoubleTapUsesOneInvocationAndOneUndoThroughProductionAdapter() {
        val f = BasicTableFixture(); f.connect(); val ui = toolkit(f)
        try {
            for (id in 40..41) assertTrue(f.move(id, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), id - 40, 0), true))
            val before = f.play.board
            val scene = TablePresentation.scene(f.game, f.play, f.actor, 412f, 892f, false)
            val selected = TableSelection(setOf(f.id(40), f.id(41)), TableMode.SELECT)
            val gestures = TableGestureCoordinator({ intent ->
                if (intent is TableIntent.Selection) ui.selectionState.value = intent.selection else ui.handle(intent)
            })
            gestures.update(scene, selected, true)
            val point = scene.items.single { it.cardId == f.id(40) }.visible.first().center
            val count = f.commands().size
            gestures.down(point, 0); gestures.up(point, 20)
            gestures.update(scene, ui.selectionState.value, true)
            gestures.down(point, 100); gestures.up(point, 120)
            assertEquals(TableSelection(), ui.selectionState.value)
            assertTrue(f.card(40).tapped); assertTrue(f.card(41).tapped)
            assertEquals(1, f.commands().drop(count).map { it.invocationId }.distinct().size)
            assertTrue(f.lobby.historyControl(GameAction.Undo))
            assertEquals(before, f.play.board)
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun multiDragKeepsThePressedCardAsItsGridAnchorAndMatchesLandingOutlines() {
        val f = BasicTableFixture(); f.connect()
        try {
            val field = f.container(PlayZone.BATTLEFIELD)
            assertTrue(f.move(40, TableDestination.Grid(field, 0, 1)))
            assertTrue(f.move(41, TableDestination.Grid(field, 1, 1)))
            val ui = toolkit(f)
            val scope = TableScope(f.id(41), listOf(f.id(40), f.id(41)))
            val destination = TableDestination.Grid(field, 4, 1)
            val scene = TablePresentation.scene(f.game, f.play, f.actor, 412f, 892f, false)
            val landing = tableDragLanding(scene, TableGesturePreview(scope = scope, destination = destination))
            assertEquals(2, landing.outlines.size)
            assertTrue(ui.handle(TableIntent.Move(scope, destination, BasicTableController.sources(f.play, scope.cardIds))))
            assertEquals(BoardPosition(3, 1), f.card(40).placement!!.position)
            assertEquals(BoardPosition(4, 1), f.card(41).placement!!.position)
            val after = TablePresentation.scene(f.game, f.play, f.actor, 412f, 892f, false)
            assertEquals(landing.outlines, scope.cardIds.map { id -> after.items.single { it.cardId == id }.bounds })
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun stackRotateDoesNotResolveToItsLiveSourceButCombatRotateStillDoes() {
        val f = TableStatesFixture
        val play = f.combat()
        val sent = mutableListOf<PlayAction>()
        val ui = TableToolkitController(f.a, { play }, { true }, { action, _ -> sent += action; true })
        val stackIds = CombatStackRules.stack(play.board).cards
        for (id in stackIds) assertFalse(ui.handle(TableIntent.Rotate(TableScope(id, listOf(id)), true)))
        assertTrue(sent.isEmpty())
        val combat = f.id("attacker1")
        assertTrue(ui.handle(TableIntent.Rotate(TableScope(combat, stackIds + combat), true)))
        assertEquals(listOf<PlayAction>(PlayAction.SetTapped(f.source(f.a, "1/3").id, true)), sent)
    }
    @Test fun publicPileTapsRouteToLiveContentsWithoutViewRevealOrDismissRequests() {
        var play = TableStatesFixture.combat()
        val sent = mutableListOf<PlayAction>()
        val ui = TableToolkitController(TableStatesFixture.a, { play }, { true }, { action, _ -> sent += action; true })
        val scene = TablePresentation.scene(TableStatesFixture.game, play, ui.viewer, 412f, 892f, false)
        val piles = scene.items.filter { it.kind == TableItemKind.PILE &&
            it.zone in setOf(PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.STACK) }
        assertEquals(5, piles.size) // Both participants' Graveyard/Exile plus the shared Stack.
        for (item in piles) {
            ui.open(TableIntent.Actions(item.id, null), item, TableSelection())
            if (item.zone == PlayZone.STACK) {
                assertEquals("manageStack", ui.panel)
                assertNull(ui.publicContents())
            } else {
                val contents = assertNotNull(ui.publicContents())
                assertEquals(item.id, contents.containerId)
                val container = play.board.containers.single { it.id == item.id }
                assertEquals(container.cards, contents.cardIds)
                assertFalse(contents.ordered)
            }
            assertNull(ui.library)
            ui.closeLocalPile()
            assertNull(ui.publicContents())
        }
        val graveyard = play.board.containers.first { it.zone == PlayZone.GRAVEYARD }
        ui.openPublicPile(graveyard.id)
        play = play.copy(board = play.board.copy(containers = play.board.containers.map {
            if (it.id == graveyard.id) it.copy(cards = emptyList()) else it
        }))
        assertTrue(ui.publicContents()!!.cardIds.isEmpty()) // Live membership, not a fixed disclosure tray.
        assertTrue(sent.isEmpty())
        assertTrue(play.board.contentsDisplays.isEmpty())
    }

    @Test fun publicBrowsingAndHoldRemainLocalOfflineAndCannotBypassLibraryView() {
        val play = TableStatesFixture.initial
        val ui = TableToolkitController(TableStatesFixture.a, { play }, { false }, { _, _ -> error("No browsing requests") })
        val pile = play.board.containers.first { it.zone == PlayZone.GRAVEYARD }
        ui.openPublicPile(pile.id)
        val route = assertNotNull(ui.publicContents())
        ui.openPublicPile(play.board.containers.first { it.zone == PlayZone.LIBRARY }.id)
        assertEquals(route, ui.publicContents())
        ui.inspect(route.cardIds.first())
        assertNotNull(ui.inspection); assertFalse(ui.inspection!!.explicit)
        ui.closeInspection(); ui.dismiss()
    }

    @Test fun publicBrowserCardActionsBindSpecificSourceAndUseExistingMoveDestinations() {
        for (sourceZone in listOf(PlayZone.GRAVEYARD, PlayZone.EXILE)) {
            for (zone in listOf(PlayZone.HAND, PlayZone.BATTLEFIELD, PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE)) {
                for (bottom in if (zone == PlayZone.LIBRARY) listOf(false, true) else listOf(false)) {
                    val f = BasicTableFixture(); f.connect()
                    try {
                        assertTrue(f.move(40, TableDestination.Container(f.container(sourceZone), sourceZone), true))
                        val ui = toolkit(f)
                        ui.openPublicPile(f.container(sourceZone))
                        val contents = assertNotNull(ui.publicContents())
                        ui.openCard(TableSelection().scope(f.id(40), contents.cardIds))
                        val bound = assertNotNull(ui.capture)
                        assertEquals(listOf(f.id(40)), bound.sourceIds)
                        // Even an old/captured other-player zone resolves back to the card's owner.
                        val targetId = f.container(zone, if (zone == sourceZone) f.other else f.actor)
                        val target = if (zone == PlayZone.BATTLEFIELD) TableDestination.Grid(targetId, 0, 0)
                            else TableDestination.Container(targetId, zone, bottom)
                        assertTrue(ui.handle(TableIntent.Move(TableScope(bound.contextViewId, bound.sourceIds), target, bound.sources)),
                            "$sourceZone → $zone (bottom=$bottom)")
                        assertEquals(f.container(zone), f.card(40).containerId)
                        if (zone == PlayZone.LIBRARY) {
                            val ids = f.play.board.containers.single { it.id == f.container(zone) }.cards
                            assertEquals(f.id(40), if (bottom) ids.last() else ids.first())
                        }
                        assertTrue(f.actions().none { it is PlayAction.ShowContents || it is PlayAction.DismissContents })
                    } finally { f.lobby.close(); f.worker.drain() }
                }
            }
        }
    }

    @Test fun staleLibraryCallbackAfterTableDisappearsDoesNotConstructOrSubmit() {
        val f = BasicTableFixture(); f.connect()
        var current: PlayState? = f.play
        val ui = TableToolkitController(f.actor, { current }, { true }, { _, _ -> error("No stale submission") })
        try {
            val intent = assertNotNull(ui.table.library(f.play, LibraryTool.MULLIGAN_MINUS_ONE))
            val library = assertNotNull(ui.libraryController(f.container(PlayZone.LIBRARY), f.container(PlayZone.HAND)))
            current = null
            assertNull(ui.libraryController(f.container(PlayZone.LIBRARY), f.container(PlayZone.HAND)))
            ui.library(intent)
            assertEquals(LibrarySubmission(0, 1), library.draw())
            assertTrue(f.commands().isEmpty())
        } finally { f.lobby.close(); f.worker.drain() }
    }
    @Test fun productionInspectorUsesAcceptedTreeProofNotReceiptOrResultAndClosedLifetimeCannotReopen() {
        val f = BasicTableFixture(); f.connect()
        val ui = TableToolkitController(f.actor, { f.lobby.state.play }, { true }, f.lobby::gameplay, playBatch = f.lobby::gameplayBatch,
            acceptedInspection = f.lobby::inspectFace)
        ui.reviewContext("live")
        var tree = GameplayTree(f.root)
        fun accepted(command: GameCommand, revision: Int) {
            val next = HistoryContinuation.change(tree, f.actor, command).tree!!
            f.message(GameRevision(f.game.id, revision.toString(), (revision + 1).toString(), command.requestId,
                f.actor, command.scope, f.game.copy(revision = (revision + 1).toString()), treeDelta = TreeSync.delta(tree, next)))
            tree = next
        }
        try {
            val face = f.root.board.cards.first().faceId
            assertTrue(ui.submit(PlayAction.InspectFace(f.id(40), face, f.container(PlayZone.LIBRARY))))
            val original = f.commands().last()
            assertTrue(ui.inspection!!.provisional)
            f.message(GameReceipt(original.requestId, original.scope, "1"))
            assertTrue(ui.inspection!!.provisional)
            f.message(GameResult(original.requestId, original.scope, "accepted", ingressOrdinal = "1", commitRevision = "2"))
            assertTrue(ui.inspection!!.provisional)
            accepted(original, 1)
            assertFalse(ui.inspection!!.provisional)
            assertTrue(ui.inspection!!.explicit)
            assertTrue(ui.submit(PlayAction.InspectFace(f.id(41), face, f.container(PlayZone.LIBRARY))))
            val closed = f.commands().last(); ui.reviewContext("history/root"); accepted(closed, 2)
            assertNull(ui.inspection)
            assertTrue(ui.submit(PlayAction.InspectFace(f.id(42), face, f.container(PlayZone.LIBRARY))))
            val rejected = f.commands().last()
            f.message(GameResult(rejected.requestId, rejected.scope, "rejected", "inapplicable", "3"))
            assertNull(ui.inspection); assertNotNull(ui.error)
        } finally { ui.closeInspection(); f.lobby.close(); f.worker.drain() }
    }

    @Test fun actualNormalHistoryArmFlowsThroughLibraryMoveDefaultsAndEverySubsequentOriginalWithoutAckBarrier() {
        val f = BasicTableFixture(); f.connect()
        try {
            val prior = f.data.command(PlayAction.AdjustLife(f.other, "1"))
            val root = GameplayTree(f.root)
            val accepted = HistoryContinuation.change(root, f.other, prior).tree!!
            f.message(GameRevision(f.game.id, "1", "2", prior.requestId, f.other, prior.scope,
                f.game.copy(revision = "2"), treeDelta = TreeSync.delta(root, accepted)))
            val personal = f.lobby.history
            personal.update(f.lobby.retainedTree()!!, "2"); f.worker.drain()
            personal.select(root.initial.root.id); personal.displayPosition()
            val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
            while (personal.view == null) { assertTrue(System.nanoTime() < end); Thread.sleep(5) }
            assertTrue(personal.arm(root.initial.root.id)); assertTrue(f.commands().isEmpty())
            val ui = TableToolkitController(f.actor, {
                if (personal.provisional) f.lobby.state.play else personal.view?.state
            }, { true }, f.lobby::gameplay, playBatch = f.lobby::gameplayBatch)
            val library = assertNotNull(ui.libraryController(f.container(PlayZone.LIBRARY), f.container(PlayZone.HAND)))
            assertEquals(LibrarySubmission(2, 0), library.move(ContentsSelection.TOP, 2,
                MoveDestination(f.container(PlayZone.GRAVEYARD), MoveInsertion.UNORDERED)))
            val originals = f.commands()
            assertEquals(1, originals.size)
            assertEquals(2, assertIs<GameAction.PlayBatch>(originals.single().action).actions.size)
            assertEquals(Continuation(root.initial.root.id, true), originals.first().continuation)
            assertNull(originals.single().playMembers()[1].continuation)
            assertEquals(f.root.board, f.lobby.state.acceptedPlay!!.board.copy(players = f.root.board.players))
            assertTrue(f.lobby.historyControl(GameAction.Undo))
            val undo = f.commands().last(); assertEquals(GameAction.Undo, undo.action); assertNull(undo.continuation)
            assertTrue(f.lobby.historyControl(GameAction.Redo))
            assertEquals(GameAction.Redo, f.commands().last().action)
        } finally { f.lobby.close(); f.worker.drain() }
    }

    private fun toolkit(f: BasicTableFixture) = TableToolkitController(f.actor, { f.lobby.state.play },
        { f.game.id in f.lobby.state.connectedGames }, f.lobby::gameplay, playBatch = f.lobby::gameplayBatch)
    private fun BasicTableFixture.actions() = commands().flatMap { it.playActions() }

    @Test fun mountedManualCaptureReadsCurrentValuesAndQueuesManyRequestsBeforeAnyResult() {
        val f = BasicTableFixture()
        val ids = listOf(f.id(40), f.id(41))
        val initial = f.root.copy(board = f.root.board.copy(cards = f.root.board.cards.map { card ->
            if (card.id !in ids) card else card.copy(containerId = f.container(PlayZone.BATTLEFIELD), covered = false,
                placement = PlayPlacement(BoardPosition(ids.indexOf(card.id), 0), f.id(600 + ids.indexOf(card.id))))
        }, containers = f.root.board.containers.map { container -> when (container.id) {
            f.container(PlayZone.LIBRARY) -> container.copy(cards = container.cards - ids.toSet())
            f.container(PlayZone.BATTLEFIELD) -> container.copy(cards = ids)
            else -> container
        } }))
        f.connect(initial); val ui = toolkit(f)
        ui.openCard(TableScope(f.id(40), listOf(f.id(40), f.id(41))))
        val scope = ui.manual.scope!!
        ui.manual.edit(ManualValue(ManualValueKind.CORNER), f.play, f.actor)
        ui.manual.editor!!.text = "9/11"
        assertTrue(ui.completeValue())
        assertEquals(listOf<PlayAction>(PlayAction.SetCorner(f.id(40), "9/11"), PlayAction.SetCorner(f.id(41), "9/11")), f.actions())
        assertEquals(1, f.commands().map { it.invocationId }.distinct().size)
        assertEquals("9/11", scope.value(f.play, ManualValue(ManualValueKind.CORNER), f.actor))
        assertEquals(initial, f.lobby.state.acceptedPlay)
        assertEquals(scope, ui.manual.scope); assertNull(ui.manual.editor)
        assertTrue(ui.submit(PlayAction.SetNote(f.id(40), " a\n b ")))
        assertTrue(ui.submit(PlayAction.SetOwner(f.id(40), f.other)))
        assertEquals("a\n b", f.card(40).note); assertEquals(f.other, f.card(40).owner)
        val endpoint = f.play.board
        var accepted = initial
        val revisions = f.commands().mapIndexed { index, command -> f.update(accepted, command, index + 1).also { accepted = it.play!! } }
        for (revision in revisions) { f.message(f.result(revision)); f.message(revision); assertEquals(endpoint, f.play.board) }
        f.message(revisions.first()); assertEquals(endpoint, f.play.board)
    }

    @Test fun everyLibraryMoveUsesExplicitDefaultsAndOneInvocationWithoutWaitingForResults() {
        val f = BasicTableFixture(); f.connect(); val ui = toolkit(f)
        val library = assertNotNull(ui.libraryController(f.container(PlayZone.LIBRARY), f.container(PlayZone.HAND)))
        assertEquals(LibrarySubmission(2, 0), library.move(ContentsSelection.TOP, 2,
            MoveDestination(f.container(PlayZone.GRAVEYARD), MoveInsertion.UNORDERED)))
        val actions = f.actions()
        assertEquals(listOf(PlayAction.MoveCard::class, PlayAction.MoveCard::class), actions.map { it::class })
        assertEquals(listOf(f.id(40), f.id(41)), actions.filterIsInstance<PlayAction.MoveCard>().map { it.cardId })
        assertEquals(1, f.commands().map { it.invocationId }.distinct().size)
        assertEquals(f.root.board.cards.single { it.id == f.id(40) }.corner, f.card(40).corner)
        assertEquals(f.root.board.cards.single { it.id == f.id(40) }.counters, f.card(40).counters)
        assertEquals("", f.card(40).note); assertEquals(f.root, f.lobby.state.acceptedPlay)
    }

    @Test fun bottomSliceSubsetAndBothMulligansKeepCapturedOrderAndInvocation() {
        for (minusOne in listOf(false, true)) {
            val f = BasicTableFixture(); f.connect(); val ui = toolkit(f)
            val library = assertNotNull(ui.libraryController(f.container(PlayZone.LIBRARY), f.container(PlayZone.HAND)))
            library.draw(3)
            val start = f.commands().size
            val displayed = listOf(42, 40, 41).map(f::id)
            val result = library.mulligan(displayed, count = 1, minusOne = minusOne)
            assertEquals(0, result.refused)
            val commands = f.commands().drop(start)
            val actions = commands.map { (it.action as GameAction.Play).action }
            val action = actions.single() as PlayAction.Mulligan
            assertEquals(displayed, action.displayedHand)
            assertEquals(if (minusOne) 2 else 1, action.drawCount)
            assertEquals(1, commands.map { it.invocationId }.distinct().size)
            assertEquals(if (minusOne) 2 else 1, f.play.board.containers.single { it.id == f.container(PlayZone.HAND) }.cards.size)
        }
    }

    @Test fun localRefusalQueuesNoMembersAndNeverRefillsOrResetsUnmovedCard() {
        for (top in listOf(false, true)) {
        val f = BasicTableFixture(); f.connect()
        val attempted = mutableListOf<Pair<PlayAction, String?>>()
        val ui = TableToolkitController(f.actor, { f.play }, { true }, { action, invocation ->
            attempted += action to invocation
            if (action is PlayAction.MoveCard && action.cardId == f.id(40)) false else f.lobby.gameplay(action, invocation)
        }, playBatch = { actions, request -> attempted += actions.map { it to request }; false })
        val library = assertNotNull(ui.libraryController(f.container(PlayZone.LIBRARY), f.container(PlayZone.HAND)))
        val destination = MoveDestination(f.container(PlayZone.EXILE), MoveInsertion.UNORDERED)
        val result = if (top) library.move(ContentsSelection.TOP, 2, destination)
            else library.moveCards(listOf(f.id(40), f.id(41)), destination)
        assertEquals(LibrarySubmission(0, 2), result)
        assertEquals(listOf(f.id(40), f.id(41)), attempted.mapNotNull { (it.first as? PlayAction.MoveCard)?.cardId })
        assertTrue(attempted.none { it.first == PlayAction.ResetCorner(f.id(40)) })
        assertEquals(f.container(PlayZone.LIBRARY), f.card(40).containerId)
        assertEquals(f.container(PlayZone.LIBRARY), f.card(41).containerId)
        assertTrue(f.commands().isEmpty())
        assertEquals(1, attempted.map { it.second }.distinct().size)
        assertNotNull(ui.error)
        }
    }

    @Test fun quickDrawCaptureDoesNotReplaceTopAfterOpponentMoveAndStillAdvancesRelatively() {
        val f = BasicTableFixture(); f.connect()
        val attempted = mutableListOf<Pair<PlayAction, String?>>()
        val ui = TableToolkitController(f.actor, { f.play }, { true }, { action, invocation ->
            attempted += action to invocation; f.lobby.gameplay(action, invocation)
        })
        val intent = ui.table.draw(f.play, true)!!
        val remote = f.data.command(PlayAction.MoveCard(f.id(40), MoveSource(f.container(PlayZone.LIBRARY)),
            MoveDestination(f.container(PlayZone.EXILE), MoveInsertion.UNORDERED)))
        f.message(f.update(f.root, remote, 1, f.other))
        assertTrue(ui.perform(intent))
        assertEquals(listOf<PlayAction>(PlayAction.NextStep), attempted.map { it.first })
        assertEquals(listOf<PlayAction>(PlayAction.NextStep), f.actions()) // Known-stale prediction is refused, not replaced.
        assertEquals(f.container(PlayZone.LIBRARY), f.card(41).containerId)
        assertEquals("UPKEEP", f.play.board.turn.step)
        assertEquals(1, f.commands().map { it.invocationId }.distinct().size)
        assertEquals(1, attempted.map { it.second }.distinct().size)
    }

    @Test fun realDeclarationEffectsAndSourceEditingNormalizeAliasesWithoutMergingOccurrences() {
        val f = BasicTableFixture(); f.connect(); val ui = toolkit(f)
        assertTrue(f.move(40, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 0), true))
        val start = f.commands().size
        assertTrue(ui.declare(listOf(f.id(40)), true))
        assertEquals(1, f.commands().drop(start).size)
        assertEquals(3, f.commands().last().playActions().size)
        assertEquals(1, f.commands().drop(start).map { it.invocationId }.distinct().size)
        assertTrue(f.card(40).tapped)
        assertTrue(ui.addEffects(listOf(f.id(40)))); assertTrue(ui.addEffects(listOf(f.id(40))))
        val effects = f.play.board.stackEffects.map { it.id }
        val occurrence = CombatStackRules.occurrences(f.play.board.combat).single().id
        ui.openCard(TableScope(effects.first(), listOf(effects.first(), f.id(40), effects.last(), occurrence)))
        assertEquals(listOf(f.id(40)), ui.manual.scope!!.targetIds)
        assertEquals(4, ui.capture!!.viewIds.size)
        assertTrue(ui.submit(PlayAction.AdjustCorner(f.id(40), CornerPart.BOTH, 1)))
        assertEquals("8/9", CombatStackRules.inspection(f.play.board, effects.last())!!.corner)
        ui.removeOccurrences(ui.capture!!.copy(viewIds = listOf(effects.first())))
        assertEquals(listOf(effects.last()), f.play.board.stackEffects.map { it.id })
        assertEquals(f.container(PlayZone.BATTLEFIELD), f.card(40).containerId)
    }

    @Test fun actualCombatPlacementUsesRealSessionDerivedOccurrenceIdsAndBoundSlots() {
        val f = BasicTableFixture(); f.connect(); val ui = toolkit(f)
        for (n in 40..41) f.move(n, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), n - 40, 0), true)
        ui.submit(PlayAction.Combat(CombatAction.Open))
        val bound = ui.bind(TableScope(f.id(40), listOf(f.id(41), f.id(40))))!!
        val area = f.play.board.combat!!.id
        val start = f.commands().size
        assertTrue(ui.place(bound, CombatDestination(area, CombatRole.BLOCKER)))
        val pair = f.play.board.combat!!.pairs.single()
        assertEquals(listOf(f.id(40), f.id(41)), pair.blockers.map { it.sourceId })
        assertTrue(pair.attackers.isEmpty())
        assertEquals(1, f.commands().drop(start).map { it.invocationId }.distinct().size)
        assertEquals(f.id(40), bound.contextViewId)
        assertEquals(1, f.commands().drop(start).size)
        assertEquals(2, f.commands().last().playActions().size)
        assertFalse(f.card(40).tapped)
    }

    @Test fun sharedPanelMembershipLocalHoldLoggedInspectionAndDisconnectedGatingAreDifferent() {
        val f = BasicTableFixture(); f.connect(); val ui = toolkit(f)
        ui.openLibrary(f.container(PlayZone.LIBRARY))
        assertEquals("library", ui.panel)
        assertTrue(f.play.board.contentsDisplays.isEmpty())
        ui.showLibraryContents(ContentsSelection.ALL, 1, false)
        val display = f.play.board.contentsDisplays.single()
        assertNull(ui.panel) // The board panel is not a personal dialog route.
        assertEquals(display.id, TablePresentation.scene(f.game, f.play, f.actor, 412f, 892f, false).cardPanels.single().display.id)
        val before = f.commands().size
        ui.inspect(f.id(40)); assertEquals(before, f.commands().size)
        assertFalse(ui.inspection!!.explicit)
        assertTrue(ui.submit(PlayAction.InspectFace(f.id(40), f.card(40).faceId, f.card(40).containerId)))
        assertTrue(ui.inspection!!.provisional); assertEquals(f.root, f.lobby.state.acceptedPlay)
        ui.libraryController(f.container(PlayZone.LIBRARY), f.container(PlayZone.HAND))!!.draw()
        assertEquals(display.cardIds - f.id(40), f.play.board.contentsDisplays.single().cardIds)
        assertEquals(f.container(PlayZone.HAND), f.card(40).containerId)
        val sent = f.commands().size
        f.wire.listener.disconnected()
        assertFalse(ui.submit(PlayAction.SetLife(f.actor, "50")))
        assertFalse(ui.perform(TableActionIntent.End(TableActionScope(f.game.id, f.play.id))))
        assertEquals(sent, f.commands().size)
    }

    @Test fun sharedPanelsSurvivePersonalMenusAndPreventAnotherPanelUntilResolved() {
        val f = BasicTableFixture(); f.connect(); val ui = toolkit(f)
        for (reveal in listOf(false, true)) {
            ui.openLibrary(f.container(PlayZone.LIBRARY))
            assertEquals("library", ui.panel)
            ui.showLibraryContents(ContentsSelection.TOP, 1, reveal)
            val display = f.play.board.contentsDisplays.last()
            assertNull(ui.panel)
            assertEquals(reveal, display.reveal)
            ui.dismiss()
            assertNull(ui.panel)
            ui.openLibrary(f.container(PlayZone.LIBRARY))
            assertEquals("library", ui.panel)
            assertEquals(display, f.play.board.contentsDisplays.last())
            ui.showLibraryContents(ContentsSelection.TOP, 1, !reveal)
            assertEquals(listOf(display), f.play.board.contentsDisplays)
            ui.resolveCardPanel(display)
            assertTrue(f.play.board.contentsDisplays.isEmpty())
        }
    }

    @Test fun capturedFieldReleaseSurvivesReflowButHoldAndCancelNeverAlsoTap() {
        val f = BasicTableFixture(); f.connect(); val ui = toolkit(f)
        val scene = TablePresentation.scene(f.game, f.play, f.actor, 480f, 900f, false)
        val life = scene.items.single { it.id == "life/${f.actor}" }
        val events = mutableListOf<TableIntent>()
        val gesture = TableGestureCoordinator({ events += it; ui.handle(it) })
        gesture.update(scene, TableSelection(), true)
        gesture.down(life.bounds.center, 0); gesture.move(life.bounds.center.copy(y = life.bounds.center.y - 48), 20)
        assertTrue(f.commands().isEmpty())
        gesture.update(scene.copy(items = scene.items.map { it.copy(bounds = it.bounds.copy(x = it.bounds.x + 20)) }), TableSelection(), true)
        gesture.up(life.bounds.center.copy(y = life.bounds.center.y - 48), 30)
        assertEquals(listOf<PlayAction>(PlayAction.AdjustLife(f.actor, "-1")), f.actions())
        val press = ActionPress(true)
        assertEquals(PressAction.HOLD, press.hold()); assertNull(press.release())
        val tooltip = ActionPress(false)
        assertEquals(PressAction.TOOLTIP, tooltip.hold()); assertNull(tooltip.release())
        val cancelled = ActionPress(true); cancelled.cancel(); assertNull(cancelled.release())
        assertEquals(1, f.commands().size)
    }

    @Test fun rotationHolderRetainsEditorSelectionAndCameraButExitOrIdentityChangeClearsPersonalInput() {
        val f = BasicTableFixture(); f.connect()
        val holder = TableToolkitViewModel()
        val key = listOf(f.game.id, f.play.id, f.actor)
        val ui = holder.obtain(f.lobby, key) { toolkit(f) }
        ui.openCard(TableScope(f.id(40), listOf(f.id(40))))
        ui.manual.edit(ManualValue(ManualValueKind.NOTE), f.play, f.actor)
        ui.manual.editor!!.text = "unsubmitted retained draft"
        ui.layoutState.value = TableLayoutState(TableCamera(84f, 42f, 1.2f))
        ui.selectionState.value = TableSelection(setOf(f.id(40)))
        val rotated = holder.obtain(f.lobby, key) { error("Rotation must not create a replacement holder") }
        assertSame(ui, rotated); assertEquals("unsubmitted retained draft", rotated.manual.editor!!.text)
        assertEquals(1.2f, rotated.layoutState.value.camera.zoom)
        assertEquals(setOf(f.id(40)), rotated.selectionState.value.ids)
        val next = holder.obtain(f.lobby, key + "new-play") { toolkit(f) }
        assertNotSame(ui, next); assertNull(ui.manual.scope); assertNull(ui.manual.editor)
        assertTrue(f.commands().isEmpty())
        holder.release(next)
    }

    @Test fun realTokenLinkAndDisappearanceKeepEffectLastLiveReadOnlyStateWithoutReplacement() {
        val f = BasicTableFixture(); f.connect(); val ui = toolkit(f)
        assertTrue(ui.submit(PlayAction.CreateToken(TokenCatalog.blank)))
        val token = f.play.board.cards.single { it.token }.id
        assertTrue(ui.submit(PlayAction.SetCorner(token, "5/2")))
        assertTrue(ui.submit(PlayAction.SetNote(token, "last live note")))
        assertTrue(ui.addEffects(listOf(token)))
        val effect = f.play.board.stackEffects.single().id
        val otherCard = f.play.board.cards.first { !it.token }.id
        ui.openCard(TableScope(token, listOf(token, otherCard)))
        assertTrue(ui.tokens.linkSelected())
        assertEquals(1, f.play.board.links.size)
        ui.batch { ui.tokens.removeTokens() }
        assertTrue(f.play.board.cards.none { it.id == token }); assertTrue(f.play.board.links.isEmpty())
        val frozen = f.play.board.stackEffects.single().frozen!!
        assertEquals("5/2", frozen.corner); assertEquals("last live note", frozen.note)
        val before = f.commands().size
        ui.openCard(TableScope(effect, listOf(effect)))
        assertEquals("occurrence", ui.panel); assertNull(ui.manual.scope)
        ui.inspect(effect)
        assertEquals(token, ui.inspection!!.snapshot!!.id)
        assertEquals(before, f.commands().size)
        ui.removeOccurrences(ui.capture!!)
        assertTrue(f.play.board.stackEffects.isEmpty()); assertTrue(f.play.board.cards.none { it.id == token })
    }
}
