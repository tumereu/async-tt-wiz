package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.*
import org.junit.Test

internal object TableToolFixture {
    fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    fun play(position: TurnPosition = TurnPosition.UNTAP, number: String = "1"): PlayState {
        val turnPlayer = id(7)
        val containers = listOf(PlayContainer(id(10), PlayZone.BATTLEFIELD, turnPlayer, listOf(id(20), id(21))),
            PlayContainer(id(11), PlayZone.LIBRARY, turnPlayer, listOf(id(22))),
            PlayContainer(id(12), PlayZone.HAND, turnPlayer, listOf(id(23), id(24))),
            PlayContainer(id(13), PlayZone.BATTLEFIELD, id(8)), PlayContainer(id(14), PlayZone.LIBRARY, id(8)),
            PlayContainer(id(15), PlayZone.HAND, id(8)))
        val cards = listOf(
            // A card originally owned by the OTHER player is still in the tracked player's field.
            PlayCard(id(20), "def", id(8), id(10), id(25), tapped = true, placement = PlayPlacement(BoardPosition(4, 2), id(30))),
            PlayCard(id(21), "def", id(7), id(10), id(25), placement = PlayPlacement(BoardPosition(2, 1), id(31))))
        return PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))), cards, containers,
                TurnState(turnPlayer, number, position.phase, position.step)), emptyList(), emptyList(),
            InitialStarter(StarterChoice(turnPlayer), turnPlayer))
    }
}

internal class RecordingRoutes : TableActionRoutes {
    val intents = mutableListOf<TableActionIntent>()
    override fun library(intent: TableActionIntent.Library) { intents += intent }
    override fun combat(intent: TableActionIntent.Combat) { intents += intent }
    override fun token(intent: TableActionIntent.Token) { intents += intent }
    override fun ending(intent: TableActionIntent.Ending) { intents += intent }
    override fun stack(intent: TableActionIntent.Stack) { intents += intent }
    override fun player(intent: TableActionIntent.Player) { intents += intent }
}

class QuickActionsTest {
    @Test fun nextStepLabelOnlySaysEndTurnWhenNextPassesToOtherPlayer() {
        val fixture = GameplayFixture()
        for (step in TurnPosition.entries) {
            val play = fixture.initial.copy(board = fixture.initial.board.copy(turn =
                TurnState(fixture.actor, "3", step.phase, step.step)))
            val result = GameplayRules.change(play, fixture.actor, fixture.command(PlayAction.NextStep))
            val passedTurn = requireNotNull(result.state).board.turn.accountId != fixture.actor
            assertEquals(if (passedTurn) "End turn" else stepLabel(TurnPosition.of(result.state!!.board.turn)!!), nextStepLabel(play.board.turn), step.name)
        }
    }

    private fun catalog(position: TurnPosition, number: String = "1", context: QuickContext = QuickContext()): List<QuickAction> {
        val play = TableToolFixture.play(position, number)
        val controller = TableActionController({ play }, { true }, { true }, RecordingRoutes())
        return QuickActions.contextual(play, context.copy(viewer = play.board.turn.accountId), controller)
    }

    @Test fun existingAttackersHideNoAttackAndOfferSelectingMoreButBlockersDoNot() {
        val initial = TableToolFixture.play(TurnPosition.DECLARE_ATTACKERS)
        for (attackers in listOf(false, true)) {
            val occurrence = CombatOccurrence(TableToolFixture.id(98), TableToolFixture.id(20))
            val area = CombatArea(TableToolFixture.id(95), listOf(CombatPair(TableToolFixture.id(96),
                attackers = if (attackers) listOf(occurrence) else emptyList(),
                blockers = if (attackers) emptyList() else listOf(occurrence))))
            val play = initial.copy(board = initial.board.copy(combat = area))
            val controller = TableActionController({ play }, { true }, { true }, RecordingRoutes())
            val actions = QuickActions.contextual(play, QuickContext(viewer = play.board.turn.accountId), controller)
            assertEquals(if (attackers) listOf("Select more attackers", QuickActions.ASSUME_NO_BLOCKS) else listOf("Select attackers", "No attack"), actions.map { it.name })
        }
    }

    @Test fun completeTwelvePositionCatalogHasNoInferredOperations() {
        val expected = mapOf(
            TurnPosition.UNTAP to listOf("Untap all", "Untap all + next", "Go to main"),
            TurnPosition.UPKEEP to listOf("Go to main"),
            TurnPosition.DRAW to listOf("Go to main"),
            TurnPosition.PRECOMBAT_MAIN to listOf("Select attackers", "Skip combat"),
            TurnPosition.DECLARE_ATTACKERS to listOf("Select attackers", "No attack"),
            TurnPosition.COMBAT_DAMAGE to listOf("Deal combat damage", "Close + next"),
            TurnPosition.END_COMBAT to listOf("Advance + close combat"),
            TurnPosition.POSTCOMBAT_MAIN to listOf("End turn"),
            TurnPosition.END_STEP to listOf("End turn"))
        for (position in TurnPosition.entries) {
            assertEquals(expected[position] ?: emptyList(), catalog(position).map { it.name }, position.name)
            assertTrue(catalog(position, context = QuickContext(playable = false)).isEmpty())
        }
        assertEquals(3, QuickActions.contextualPositions)
        assertEquals(3, catalog(TurnPosition.UNTAP).size)
        assertEquals(3, catalog(TurnPosition.UNTAP, "2").size)
        assertEquals(3, catalog(TurnPosition.UNTAP, "3").size) // not each player's first Untap
        assertEquals(listOf("Untap all", "Untap all + next", "Untap, draw → main"), catalog(TurnPosition.UNTAP, "2").map { it.name })
        assertEquals(listOf("Draw + go to main"), catalog(TurnPosition.UPKEEP, "2").map { it.name })
        assertEquals(listOf("Draw one", "Draw one + next"), catalog(TurnPosition.DRAW, "2").map { it.name })
    }

    @Test fun declarationsRequireCapturedSelectionOnlyAtDeclareAttackers() {
        val selection = listOf(TableToolFixture.id(20), TableToolFixture.id(21), TableToolFixture.id(20))
        val context = QuickContext(selectMode = true, selectedSourceIds = selection)
        for (position in TurnPosition.entries) {
            val declarations = catalog(position, context = context).filter { (it.intent as? TableActionIntent.Combat)?.tool in
                setOf(CombatTool.DECLARE, CombatTool.DECLARE_AND_TAP) }
            assertEquals(if (position == TurnPosition.DECLARE_ATTACKERS) 2 else 0, declarations.size)
            for (action in declarations) assertEquals(selection.distinct(), (action.intent as TableActionIntent.Combat).sourceIds)
        }
        assertEquals(listOf("Select attackers", "No attack"), catalog(TurnPosition.DECLARE_ATTACKERS).map { it.name })
        assertEquals(2, catalog(TurnPosition.DECLARE_ATTACKERS, context = context.copy(selectMode = false)).size)
        assertEquals(listOf("Select attackers", "No attack"), catalog(TurnPosition.DECLARE_ATTACKERS,
            context = context.copy(selectedSourceIds = emptyList())).map { it.name })
    }

    @Test fun everyExplicitStackKindAppendsAfterPhaseActionsAtEveryPosition() {
        for (position in TurnPosition.entries) for (kind in StackTargetKind.entries) {
            val target = ExplicitStackTarget(TableToolFixture.id(90), kind)
            val plain = catalog(position)
            val actions = catalog(position, context = QuickContext(stackTarget = target))
            assertEquals(plain, actions.dropLast(1))
            assertEquals(TableActionIntent.Stack(TableActionScope(TableToolFixture.id(1), TableToolFixture.id(2)), target), actions.last().intent)
        }
        assertEquals(4, catalog(TurnPosition.UNTAP, context = QuickContext(stackTarget =
            ExplicitStackTarget(TableToolFixture.id(90), StackTargetKind.EFFECT))).size)
    }

    @Test fun stepActionsAreOnlyOfferedToTheirPlayerButExplicitStackActionsRemainAvailable() {
        for (position in TurnPosition.entries) {
            val play = TableToolFixture.play(position)
            val controller = TableActionController({ play }, { true }, { true }, RecordingRoutes())
            val defender = QuickContext(viewer = TableToolFixture.id(8))
            val actions = QuickActions.contextual(play, defender, controller)
            assertEquals(when (position) {
                TurnPosition.DECLARE_BLOCKERS -> listOf("No blocks + next")
                else -> emptyList()
            }, actions.map { it.name })
            assertTrue(QuickActions.contextual(play, QuickContext(), controller).isEmpty())
            val target = ExplicitStackTarget(TableToolFixture.id(90), StackTargetKind.EFFECT)
            assertIs<TableActionIntent.Stack>(QuickActions.contextual(play, defender.copy(stackTarget = target), controller).last().intent)
        }
    }

    private fun withStatus(play: PlayState, vararg statuses: Pair<String, String>) = play.copy(board = play.board.copy(
        players = play.board.players.map { player -> statuses.firstOrNull { it.first == player.accountId }
            ?.let { player.copy(status = it.second) } ?: player }))

    @Test fun openingMulligansUseEachViewersOwnHandAndLibraryWhileTheirStatusIsTheOpeningStatus() {
        val first = TableToolFixture.play()
        val mulliganing = first.board.players.map { it.accountId to PlayerStatus.OPENING }.toTypedArray()
        for (viewer in first.board.players.map { it.accountId }) {
            val play = withStatus(first.copy(board = first.board.copy(attentionHolder = first.board.turn.accountId)), *mulliganing)
            val controller = TableActionController({ play }, { true }, { true }, RecordingRoutes())
            assertTrue(QuickActions.visible(play, viewer))
            assertEquals(viewer != play.board.turn.accountId, QuickActions.mulliganOnly(play, viewer))
            val actions = QuickActions.contextual(play, QuickContext(viewer = viewer,
                stackTarget = ExplicitStackTarget(TableToolFixture.id(90), StackTargetKind.EFFECT)), controller)
            val mulligans = actions.mapNotNull { it.intent as? TableActionIntent.Library }
            assertEquals(listOf(LibraryTool.MULLIGAN_N, LibraryTool.MULLIGAN_MINUS_ONE), mulligans.map { it.tool })
            val hand = play.board.containers.single { it.zone == PlayZone.HAND && it.accountId == viewer }.cards
            assertEquals(listOf(7, (hand.size - 1).coerceAtLeast(0)), mulligans.map { it.defaultCount })
            for (intent in mulligans) {
                assertEquals(viewer, intent.targets.accountId)
                assertEquals(play.board.containers.single { it.zone == PlayZone.HAND && it.accountId == viewer }.cards, intent.handIds)
                assertEquals(play.board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == viewer }.id, intent.targets.libraryId)
            }
            // Turn-1 shortcuts are hidden for the starting player too; the opponent sees only Mull/Ready.
            assertEquals(listOf("Mull N", "Mull −1", "Ready"), actions.take(3).map { it.name })
            if (viewer != play.board.turn.accountId) assertEquals(3, actions.size)
            else assertEquals(4, actions.size) // + the explicit Stack target
        }
        // Mulliganing does not depend on the step: it lasts until the status changes.
        val other = TableToolFixture.id(8)
        val upkeep = withStatus(TableToolFixture.play(TurnPosition.UPKEEP), other to PlayerStatus.OPENING)
        assertTrue(QuickActions.visible(upkeep, other)); assertTrue(QuickActions.mulliganOnly(upkeep, other))
        for (play in listOf(TableToolFixture.play(number = "2"), TableToolFixture.play(TurnPosition.UPKEEP), TableToolFixture.play())) {
            assertFalse(QuickActions.visible(play, other))
            assertFalse(QuickActions.openingMulligan(play, other))
        }
        assertFalse(QuickActions.visible(withStatus(first, *mulliganing), null))
        assertFalse(QuickActions.visible(withStatus(first, *mulliganing), "spectator"))
    }

    @Test fun startingPlayersTurnOneShortcutsWaitWhileEitherPlayerHasExactlyTheOpeningStatus() {
        val starter = TableToolFixture.id(7); val other = TableToolFixture.id(8)
        val normal = listOf("Untap all", "Untap all + next", "Go to main")
        fun names(play: PlayState, viewer: String = starter) = QuickActions.contextual(play, QuickContext(viewer = viewer),
            TableActionController({ play }, { true }, { true }, RecordingRoutes())).map { it.name }
        val base = TableToolFixture.play()
        assertEquals(listOf("Mull N", "Mull −1", "Ready"), names(withStatus(base, starter to PlayerStatus.OPENING, other to PlayerStatus.OPENING)))
        // The starter is done, the opponent is still mulliganing: no shortcuts yet, and the opponent still sees Mull/Ready.
        val waiting = withStatus(base, starter to "", other to PlayerStatus.OPENING)
        assertEquals(emptyList(), names(waiting))
        assertEquals(listOf("Mull N", "Mull −1", "Ready"), names(waiting, other))
        assertEquals(emptyList(), names(withStatus(TableToolFixture.play(TurnPosition.UPKEEP), other to PlayerStatus.OPENING)))
        // Any other status counts as done: cleared, a preset, or edited opening text.
        for (status in listOf("", "Ready", "Wait", "Mulliganing…", "mulliganing", "Mulligan to 6")) {
            assertEquals(normal, names(withStatus(base, starter to status, other to status)), status)
            assertFalse(QuickActions.openingMulligan(withStatus(base, other to status), other))
        }
        // From turn 2 an unchanged opening status no longer hides the turn player's shortcuts.
        assertEquals(listOf("Untap all", "Untap all + next", "Untap, draw → main"),
            names(withStatus(TableToolFixture.play(number = "2"), other to PlayerStatus.OPENING)))
    }

    @Test fun readyClearsTheViewersOwnStatus() {
        val other = TableToolFixture.id(8)
        val play = withStatus(TableToolFixture.play(), other to PlayerStatus.OPENING)
        val sent = mutableListOf<PlayAction>()
        val controller = TableActionController({ play }, { true }, { sent += it; true }, RecordingRoutes())
        val ready = QuickActions.contextual(play, QuickContext(viewer = other), controller).single { it.name == "Ready" }
        assertTrue(controller.perform(ready.intent))
        assertEquals(listOf<PlayAction>(PlayAction.SetStatus("")), sent)
        val change = GameplayRules.change(play, other, GameCommand(TableToolFixture.id(40),
            GameScope(play.gameId, playId = play.id), GameAction.Play(sent.single()), TableToolFixture.id(41), "1"))
        assertEquals("", change.state!!.board.players.single { it.accountId == other }.status)
        assertFalse(QuickActions.openingMulligan(change.state!!, other))
    }

    @Test fun startTurnAndDrawToMainUseOrderedCapturedConstituents() {
        assertEquals("Untap, draw\n→ main", quickActionLabel("Untap, draw → main"))
        for ((position, name) in listOf(TurnPosition.UNTAP to "Untap, draw → main", TurnPosition.UPKEEP to "Draw + go to main")) {
            val play = TableToolFixture.play(position, "3")
            val sent = mutableListOf<PlayAction>()
            val controller = TableActionController({ play }, { true }, { sent += it; true }, RecordingRoutes())
            val action = QuickActions.contextual(play, QuickContext(viewer = play.board.turn.accountId), controller).single { it.name == name }
            assertTrue(controller.perform(action.intent))
            if (position == TurnPosition.UNTAP) assertEquals(listOf(TableToolFixture.id(21), TableToolFixture.id(20)),
                sent.filterIsInstance<PlayAction.SetTapped>().map { it.cardId })
            assertEquals(TableToolFixture.id(22), sent.filterIsInstance<PlayAction.MoveCard>().single().cardId)
            assertEquals(PlayAction.ChooseStep(TurnPosition.PRECOMBAT_MAIN), sent.last())
            assertTrue(sent.none { it == PlayAction.NextStep })
        }
        assertTrue(CombatOfferings.advanceAndClose(TableToolFixture.play(TurnPosition.END_COMBAT).board.turn))
    }

    @Test fun startingPlayersFirstTurnGoesToMainWithoutDrawing() {
        for (position in listOf(TurnPosition.UNTAP, TurnPosition.UPKEEP, TurnPosition.DRAW)) {
            val play = TableToolFixture.play(position)
            val sent = mutableListOf<PlayAction>()
            val controller = TableActionController({ play }, { true }, { sent += it; true }, RecordingRoutes())
            val actions = QuickActions.contextual(play, QuickContext(viewer = play.board.turn.accountId), controller)
            assertTrue(actions.none { "raw" in it.name }, position.name)
            assertTrue(controller.perform(actions.single { it.name == "Go to main" }.intent))
            assertEquals(listOf<PlayAction>(PlayAction.ChooseStep(TurnPosition.PRECOMBAT_MAIN)), sent)
        }
        // The other player's first turn is turn 2 and draws as usual.
        assertTrue(catalog(TurnPosition.DRAW, "2").none { it.name == "Go to main" })
    }

    @Test fun allUnderlyingRoutesRemainOutsideTheirQuickWindows() {
        val play = TableToolFixture.play(TurnPosition.CLEANUP, "9")
        val controller = TableActionController({ play }, { true }, { true }, RecordingRoutes())
        val table = TableActionMenus.table(play, QuickContext(), controller)
        assertEquals(listOf("Create combat area",
            "Create token", "Suggest game end"), table.map { it.name })
        val area = CombatArea(TableToolFixture.id(95))
        val open = TableActionMenus.table(play.copy(board = play.board.copy(combat = area)), QuickContext(), controller)
        assertEquals(listOf("Close combat area"), open.filter { it.intent is TableActionIntent.Combat }.map { it.name })
        assertEquals(area.id, (open.single { it.intent is TableActionIntent.Combat }.intent as TableActionIntent.Combat).areaId)
        val library = TableActionMenus.library(play, controller.targets(play)!!, controller)
        assertEquals(LibraryTool.entries.toSet(), library.map { (it.intent as TableActionIntent.Library).tool }.toSet())
        assertEquals(setOf(CombatTool.DECLARE, CombatTool.DECLARE_AND_TAP),
            TableActionMenus.declaration(controller.scope()!!, null, listOf(TableToolFixture.id(20))).map {
                (it.intent as? TableActionIntent.Combat)?.tool }.filterNotNull().toSet())
        assertEquals(listOf(true, false), TableActionMenus.declaration(controller.scope()!!, null, listOf(TableToolFixture.id(20)))
            .mapNotNull { (it.intent as? TableActionIntent.Orient)?.tapped })
        for (kind in StackTargetKind.entries) assertEquals(1,
            TableActionMenus.stack(controller.scope()!!, ExplicitStackTarget(TableToolFixture.id(91), kind)).size)
        val pending = TableActionMenus.table(play, QuickContext(), controller, TableToolFixture.id(92), true)
        assertEquals(listOf(EndingTool.ACCEPT), pending.mapNotNull { (it.intent as? TableActionIntent.Ending)?.tool })
        val ownPending = TableActionMenus.table(play, QuickContext(), controller, TableToolFixture.id(92), false)
        assertTrue(ownPending.none { it.intent is TableActionIntent.Ending })
    }

    @Test fun tapHoldTooltipAndCancellationAreMutuallyExclusive() {
        val tap = ActionPress(true)
        assertEquals(PressAction.TAP, tap.release())
        assertNull(tap.release())
        val hold = ActionPress(true)
        assertEquals(PressAction.HOLD, hold.hold())
        assertNull(hold.release()) // no Next after End
        assertNull(hold.hold())
        val tooltip = ActionPress(false)
        assertEquals(PressAction.TOOLTIP, tooltip.hold())
        assertNull(tooltip.release())
        val cancelled = ActionPress(true)
        cancelled.cancel()
        assertNull(cancelled.hold())
        assertNull(cancelled.release())
    }

    @Test fun deltaPreviewReversalZeroCancelAndOneReleaseRetainOriginalTarget() {
        val target = PlayerValueTarget(TableActionScope(TableToolFixture.id(1), TableToolFixture.id(2)), TableToolFixture.id(7), CounterColor.BLUE)
        val drag = PlayerDeltaDrag(target, 12f)
        drag.move(30f)
        assertEquals(-2L, drag.delta)
        drag.move(-50f)
        assertEquals(1L, drag.delta)
        assertEquals(PlayerDeltaRelease(target, 1), drag.release())
        assertNull(drag.release())
        val zero = PlayerDeltaDrag(target, 10f)
        zero.move(30f); zero.move(-30f)
        assertNull(zero.release())
        val cancel = PlayerDeltaDrag(target, 10f)
        cancel.move(30f); cancel.cancel()
        assertNull(cancel.release())
    }
}
