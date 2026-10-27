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
            assertEquals(if (attackers) listOf("Select more attackers") else listOf("Select attackers", "No attack"), actions.map { it.name })
        }
    }

    @Test fun completeTwelvePositionCatalogHasNoInferredOperations() {
        val expected = mapOf(
            TurnPosition.UNTAP to listOf("Untap all", "Untap all + next", "Untap, draw → main", "Mulligan N", "Mulligan −1"),
            TurnPosition.UPKEEP to listOf("Draw + go to main"),
            TurnPosition.DRAW to listOf("Draw one", "Draw one + next"),
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
        assertEquals(5, catalog(TurnPosition.UNTAP).size) // overflow, not dropped/clipped to three
        assertEquals(3, catalog(TurnPosition.UNTAP, "2").size)
        assertEquals(3, catalog(TurnPosition.UNTAP, "3").size) // not each player's first Untap
        assertEquals(7, (catalog(TurnPosition.UNTAP)[3].intent as TableActionIntent.Library).defaultCount)
        assertEquals(1, (catalog(TurnPosition.UNTAP)[4].intent as TableActionIntent.Library).defaultCount)
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
        assertEquals(6, catalog(TurnPosition.UNTAP, context = QuickContext(stackTarget =
            ExplicitStackTarget(TableToolFixture.id(90), StackTargetKind.EFFECT))).size)
    }

    @Test fun stepActionsAreOnlyOfferedToTheirPlayerButExplicitStackActionsRemainAvailable() {
        for (position in TurnPosition.entries) {
            val play = TableToolFixture.play(position)
            val controller = TableActionController({ play }, { true }, { true }, RecordingRoutes())
            val defender = QuickContext(viewer = TableToolFixture.id(8))
            val actions = QuickActions.contextual(play, defender, controller)
            assertEquals(when (position) {
                TurnPosition.UNTAP -> listOf("Mulligan N", "Mulligan −1")
                TurnPosition.DECLARE_BLOCKERS -> listOf("No blocks + next")
                else -> emptyList()
            }, actions.map { it.name })
            assertTrue(QuickActions.contextual(play, QuickContext(), controller).isEmpty())
            val target = ExplicitStackTarget(TableToolFixture.id(90), StackTargetKind.EFFECT)
            if (position != TurnPosition.UNTAP)
                assertIs<TableActionIntent.Stack>(QuickActions.contextual(play, defender.copy(stackTarget = target), controller).last().intent)
        }
    }

    @Test fun openingMulligansUseEachViewersOwnHandAndLibraryAndOnlyTheOpeningUntap() {
        val first = TableToolFixture.play()
        for (viewer in first.board.players.map { it.accountId }) {
            val play = first.copy(board = first.board.copy(attentionHolder = first.board.turn.accountId))
            val controller = TableActionController({ play }, { true }, { true }, RecordingRoutes())
            assertTrue(QuickActions.visible(play, viewer))
            assertEquals(viewer != play.board.turn.accountId, QuickActions.mulliganOnly(play, viewer))
            val actions = QuickActions.contextual(play, QuickContext(viewer = viewer,
                stackTarget = ExplicitStackTarget(TableToolFixture.id(90), StackTargetKind.EFFECT)), controller)
            val mulligans = actions.mapNotNull { it.intent as? TableActionIntent.Library }
            assertEquals(listOf(LibraryTool.MULLIGAN_N, LibraryTool.MULLIGAN_MINUS_ONE), mulligans.map { it.tool })
            for (intent in mulligans) {
                assertEquals(viewer, intent.targets.accountId)
                assertEquals(play.board.containers.single { it.zone == PlayZone.HAND && it.accountId == viewer }.cards, intent.handIds)
                assertEquals(play.board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == viewer }.id, intent.targets.libraryId)
            }
            if (viewer != play.board.turn.accountId) assertEquals(2, actions.size)
        }
        val other = TableToolFixture.id(8)
        for (play in listOf(TableToolFixture.play(number = "2"), TableToolFixture.play(TurnPosition.UPKEEP))) {
            assertFalse(QuickActions.visible(play, other))
            assertFalse(QuickActions.openingMulligan(play, other))
        }
        assertFalse(QuickActions.visible(first, null))
        assertFalse(QuickActions.visible(first, "spectator"))
    }

    @Test fun startTurnAndDrawToMainUseOrderedCapturedConstituents() {
        assertEquals("Untap, draw\n→ main", quickActionLabel("Untap, draw → main"))
        for ((position, name) in listOf(TurnPosition.UNTAP to "Untap, draw → main", TurnPosition.UPKEEP to "Draw + go to main")) {
            val play = TableToolFixture.play(position)
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
