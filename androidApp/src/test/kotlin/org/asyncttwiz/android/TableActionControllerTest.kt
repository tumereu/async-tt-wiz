package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.*
import org.junit.Test

class TableActionControllerTest {
    private var play = TableToolFixture.play()
    private var connected = true
    private val sent = mutableListOf<PlayAction>()
    private val routes = RecordingRoutes()
    private var samples = 0
    private val controller = TableActionController({ play }, { connected }, { sent += it; true }, routes,
        sample = { bound -> samples++; bound - 1 })

    // TRN-044
    @Test fun freshDieAndCoinSampleExactlyOnceOnlyWhenSameGameConnected() {
        val scope = controller.scope()!!
        assertTrue(controller.rollDie(scope))
        assertEquals(listOf<PlayAction>(PlayAction.RollDie(6, 6)), sent)
        assertEquals(1, samples)
        assertTrue(controller.flipCoin(scope))
        assertEquals(PlayAction.FlipCoin(CoinFace.TAILS), sent.last())
        assertEquals(2, samples)
        assertTrue(controller.rollDie(scope, Int.MAX_VALUE))
        assertEquals(PlayAction.RollDie(Int.MAX_VALUE, Int.MAX_VALUE), sent.last())
        assertEquals(3, samples)
        assertFalse(controller.rollDie(scope, 0))
        connected = false
        assertFalse(controller.rollDie(scope))
        assertFalse(controller.flipCoin(scope))
        connected = true
        assertFalse(controller.rollDie(scope.copy(playId = TableToolFixture.id(99))))
        assertEquals(3, samples)
        // The ordinary sender stores this original input; transport/replay never re-enters sampling.
        val original = GameCommand(TableToolFixture.id(60), GameScope(scope.gameId, playId = scope.playId),
            GameAction.Play(sent.first()), TableToolFixture.id(61), "1")
        val accepted = GameplayRules.change(play, TableToolFixture.id(7), original)
        assertEquals(accepted, GameplayHistory.replay(play, accepted.mutation!!))
        assertEquals(GameWire.digest(original), GameWire.digest(original.copy(generation = "2")))
        assertEquals(3, samples)
    }

    @Test fun failedOriginalSubmissionDoesNotResampleOrRetryAutomatically() {
        var attempts = 0
        val rejecting = TableActionController({ play }, { true }, { attempts++; false }, routes,
            sample = { samples++; 0 })
        assertFalse(rejecting.rollDie(controller.scope()!!))
        assertEquals(1, samples)
        assertEquals(1, attempts)
    }

    @Test fun capturedUntapScopeIsRegionNotOriginalOwnerAndNeverRetargetsOnTurnChange() {
        val captured = controller.untap(play, true)!!
        assertEquals(listOf(TableToolFixture.id(21), TableToolFixture.id(20)), captured.cardIds)
        play = play.copy(board = play.board.copy(turn = TurnState(TableToolFixture.id(8), "2")))
        assertTrue(controller.perform(captured))
        assertEquals(listOf(PlayAction.SetTapped(TableToolFixture.id(21), false),
            PlayAction.SetTapped(TableToolFixture.id(20), false), PlayAction.NextStep), sent)
        // The relative tracking constituent advances the CURRENT turn, not a stale replacement.
        val command = GameCommand(TableToolFixture.id(60), GameScope(play.gameId, playId = play.id),
            GameAction.Play(sent.last()), TableToolFixture.id(61), "1")
        assertEquals(TurnPosition.UPKEEP, TurnPosition.of(GameplayRules.change(play, TableToolFixture.id(7), command).state!!.board.turn))
    }

    @Test fun drawCapturesTopAndBothContainersAndEmptyDrawStillAdvances() {
        val captured = controller.draw(play, true)!!
        play = play.copy(board = play.board.copy(turn = TurnState(TableToolFixture.id(8), "2")))
        controller.perform(captured)
        assertEquals(PlayAction.MoveCard(TableToolFixture.id(22), MoveSource(TableToolFixture.id(11), edge = MoveSourceEdge.TOP),
            MoveDestination(TableToolFixture.id(12), MoveInsertion.UNORDERED)), sent.first())
        assertEquals(PlayAction.NextStep, sent.last())
        sent.clear()
        val empty = controller.draw(play, true)!!
        assertNull(empty.cardId)
        controller.perform(empty)
        assertEquals(listOf<PlayAction>(PlayAction.NextStep), sent)
        sent.clear()
        controller.perform(empty.copy(advance = false))
        assertTrue(sent.isEmpty())
    }

    @Test fun emptyUntapAndRejectedConstituentsDoNotPreventLaterRelativeAdvance() {
        val captured = controller.untap(play, true)!!
        val rejected = TableActionController({ play }, { true }, { sent += it; false }, routes)
        rejected.perform(captured)
        assertEquals(PlayAction.NextStep, sent.last())
        sent.clear()
        controller.perform(captured.copy(cardIds = emptyList()))
        assertEquals(listOf<PlayAction>(PlayAction.NextStep), sent)
    }

    @Test fun menuIntentsReachTypedCapabilityComponentsWithCapturedIdentities() {
        val scope = controller.scope()!!
        val entries = TableActionMenus.table(play, QuickContext(combatAreaId = TableToolFixture.id(95)), controller) +
            TableActionMenus.library(play, controller.targets(play)!!, controller) +
            TableActionMenus.declaration(scope, TableToolFixture.id(95), listOf(TableToolFixture.id(20))) +
            StackTargetKind.entries.flatMap { TableActionMenus.stack(scope, ExplicitStackTarget(TableToolFixture.id(96), it)) } +
            TableMenuAction("Player", TableActionIntent.Player(scope, TableToolFixture.id(8)))
        entries.forEach { controller.perform(it.intent) }
        val expected = entries.map { it.intent }.filter { it is TableActionIntent.Library || it is TableActionIntent.Combat ||
            it is TableActionIntent.Token || it is TableActionIntent.Ending || it is TableActionIntent.Stack || it is TableActionIntent.Player }
        assertEquals(expected, routes.intents)
        val mulligan = routes.intents.filterIsInstance<TableActionIntent.Library>().first { it.tool == LibraryTool.MULLIGAN_MINUS_ONE }
        assertEquals(listOf(TableToolFixture.id(23), TableToolFixture.id(24)), mulligan.handIds)
        assertEquals(1, mulligan.defaultCount)
        play = play.copy(id = TableToolFixture.id(99))
        val count = routes.intents.size
        assertFalse(controller.perform(mulligan))
        assertEquals(count, routes.intents.size)
    }

    @Test fun releasedDeltaUsesGenericLifeRelativeActionAndCurrentCounterApplicability() {
        val scope = controller.scope()!!
        val life = PlayerValueTarget(scope, TableToolFixture.id(8))
        val blue = life.copy(color = CounterColor.BLUE)
        assertTrue(controller.adjust(life, 3))
        assertEquals(PlayAction.AdjustLife(TableToolFixture.id(8), "3"), sent.last())
        assertTrue(controller.adjust(blue, -2))
        assertEquals(PlayAction.AdjustPlayerCounter(TableToolFixture.id(8), CounterColor.BLUE, "-2"), sent.last())
        assertFalse(controller.adjust(blue, 0))
        play = play.copy(board = play.board.copy(players = play.board.players.map {
            if (it.accountId == blue.accountId) it.copy(counters = mapOf(CounterColor.BLUE to "text")) else it }))
        assertFalse(controller.adjust(blue, 1))
        connected = false
        assertFalse(controller.adjust(life, 1))
        connected = true
        assertFalse(controller.adjust(life.copy(accountId = TableToolFixture.id(99)), 1))
        assertEquals(2, sent.size)
    }

    @Test fun lifePreviewThenConcurrentUpdateAppliesOneRelativeReleaseNotStaleSet() {
        val target = PlayerValueTarget(controller.scope()!!, TableToolFixture.id(7))
        val drag = PlayerDeltaDrag(target, 10f)
        drag.move(30f)
        assertTrue(sent.isEmpty())
        play = play.copy(board = play.board.copy(players = play.board.players.map {
            if (it.accountId == target.accountId) it.copy(life = "21") else it }))
        val release = drag.release()!!
        controller.adjust(release.target, release.delta)
        assertEquals(listOf<PlayAction>(PlayAction.AdjustLife(target.accountId, "-3")), sent)
        val command = GameCommand(TableToolFixture.id(60), GameScope(play.gameId, playId = play.id),
            GameAction.Play(sent.single()), TableToolFixture.id(61), "1")
        assertEquals("18", GameplayRules.change(play, TableToolFixture.id(8), command).state!!.board.players.first().life)
        assertNull(drag.release())
    }
}
