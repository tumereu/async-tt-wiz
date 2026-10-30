package org.asyncttwiz.protocol

import kotlin.test.*

/** Real shared-processor seams, not category-only traces or native/server acceptance. */
class LibraryTurnHistoryIntegrationTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val seed = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })
    private fun initial() = PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
        BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))),
            (20..23).map { PlayCard(id(it), "definition", id(7), id(10), id(40), note = "keep") } +
                PlayCard(id(24), "definition", id(7), id(13), id(40), note = "keep",
                    placement = PlayPlacement(BoardPosition(0, 0), id(50))),
            listOf(PlayContainer(id(10), PlayZone.LIBRARY, id(7), (20..23).map(::id)),
                PlayContainer(id(11), PlayZone.HAND, id(7)), PlayContainer(id(12), PlayZone.LIBRARY, id(8)),
                PlayContainer(id(13), PlayZone.BATTLEFIELD, id(7), listOf(id(24)))),
            TurnState(id(7))), emptyList(), emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))
    private fun command(action: PlayAction, n: Int) = GameCommand(id(100 + n),
        GameScope(id(1), playId = id(2)), GameAction.Play(action), id(9), n.toString())

    @Test fun acceptedLibraryTurnRandomPrefixesKeepLiteralRepresentativesAndReplayTuples() {
        var current = initial()
        val mutations = mutableListOf<GameplayMutation>()
        val groups = mutableListOf<GameplayGroup>()
        fun unrelated(state: PlayState) = state.copy(board = state.board.copy(
            players = state.board.players.map { if (it.accountId == id(8)) it.copy(life = "unrelated") else it },
            cards = state.board.cards.map { if (it.id == id(24)) it.copy(note = "unrelated note") else it }))
        fun accept(action: PlayAction, boundary: Boolean, category: String, representative: Int,
            icon: String, author: String = id(7)): GameplayMutation {
            val before = current
            val result = GameplayRules.change(before, author, command(action, mutations.size + 1))
            assertEquals("accepted", result.outcome, action.toString())
            val mutation = result.mutation!!
            assertEquals(mutation, GameWire.json.decodeFromString<GameplayMutation>(GameWire.json.encodeToString(mutation)))
            current = result.state!!
            val group = current.currentGroup!!
            assertEquals(boundary, before.currentGroup?.id != group.id, "prefix ${mutations.size + 1}")
            assertEquals(category, group.category)
            mutations += mutation
            groups += group
            assertEquals(mutations[representative - 1].id, group.representative!!.nodeId)
            assertEquals(icon, group.representative.semantic.icon.wire)
            assertEquals(mutation.id, group.lastNodeId)
            assertEquals(result, GameplayHistory.replay(before, mutation))
            val replay = GameplayHistory.replay(unrelated(before), mutation)
            assertEquals("accepted", replay.outcome)
            assertEquals(unrelated(current), replay.state, "Replay must preserve unrelated fields and accepted displays")
            assertEquals(mutation.transition, replay.mutation!!.transition)
            assertEquals(mutation.grouping, replay.mutation.grouping)
            return mutation
        }
        accept(PlayAction.SetTapped(id(24), true), true, "tap", 1, "rotate_90_degrees_cw")
        val shuffle = accept(PlayAction.ShuffleLibrary(id(10), seed), false, "shuffle", 2, "shuffle")
        assertEquals(id(10), shuffle.grouping!!.key)
        accept(PlayAction.NextStep, false, "shuffle", 2, "shuffle")
        accept(PlayAction.ShuffleLibrary(id(10), seed), false, "shuffle", 2, "shuffle")
        val view = accept(PlayAction.ShowContents(id(10), ContentsSelection.TOP, 2), true, "view", 5, "pageview")
        val display = view.transition.contents!!.to!!
        assertEquals(current.board.containers.first().cards.take(2), display.cardIds)
        assertTrue(display.facesVisibleTo(id(7))); assertFalse(display.facesVisibleTo(id(8)))
        accept(PlayAction.DismissContents(display.id), true, "closure", 6, "close")
        assertTrue(current.board.contentsDisplays.isEmpty())
        // Rules v29: a panel close is its own history step; nothing joins it.
        val reveal = accept(PlayAction.ShowContents(id(10), ContentsSelection.BOTTOM, 2, reveal = true), true, "view", 7, "visibility")
        val revealed = reveal.transition.contents!!.to!!
        assertEquals(current.board.containers.first().cards.takeLast(2), revealed.cardIds)
        assertTrue(revealed.facesVisibleTo(id(8)))
        accept(PlayAction.DismissContents(revealed.id), true, "closure", 8, "close")
        val turn = accept(PlayAction.EndTurn, true, "leading", 9, "outlined_flag")
        assertEquals(HistorySignificance.TURN_TRANSITION, turn.grouping!!.significance)
        assertEquals(TurnState(id(8), "2"), current.board.turn)
        val die = accept(PlayAction.RollDie(6, 4), true, "random", 10, "casino")
        assertEquals(GameplayRandomResult(RandomTool.DIE, "4", "6"), die.transition.random)
        val coin = accept(PlayAction.FlipCoin(CoinFace.TAILS), false, "random", 10, "casino")
        assertEquals("monetization_on", coin.grouping!!.icon.wire)
        assertEquals(GameplayRandomResult(RandomTool.COIN, "TAILS"), coin.transition.random)
        accept(PlayAction.SetPlayerCounter(id(7), CounterColor.BLUE, "3"), false, "edit", 12, "exposure_plus_1")
        val draw = accept(LibraryActions.draw(current.board, id(10), id(11), 1).single(), true, "move", 13, "download")
        assertEquals("${id(10)}/${id(11)}", draw.grouping!!.key)
        accept(PlayAction.ChooseStep(TurnPosition.COMBAT_DAMAGE), false, "move", 13, "download")
        assertTrue(current.board.contentsDisplays.isEmpty())
        accept(PlayAction.ShuffleLibrary(id(10), seed), true, "shuffle", 15, "shuffle", id(8))
        assertEquals(listOf("1", "2", "3", "4", "1", "1", "1", "1", "1", "1", "2", "3", "1", "2", "1"), groups.map { it.count })
        assertEquals(mutations[0].id, groups[3].firstNodeId) // Earlier prefix boundaries did not migrate.
        val corrupted = shuffle.copy(grouping = shuffle.grouping.copy(icon = HistoryIcon.CASINO))
        val tapped = GameplayRules.change(initial(), id(7), command(mutations[0].action, 1)).state!!
        assertNotNull(GameplayHistory.replay(tapped, corrupted).state) // Presentation metadata is not concerned state.
    }

    @Test fun cleanupUsesAcceptedTurnTupleAndUnavailableAnnotationContextsFailClosed() {
        val initial = initial()
        val cleanup = initial.copy(board = initial.board.copy(turn = TurnState(id(7), phase = "ENDING", step = "CLEANUP")))
        val result = GameplayRules.change(cleanup, id(7), command(PlayAction.NextStep, 1))
        assertEquals("accepted", result.outcome)
        assertEquals(HistorySignificance.TURN_TRANSITION, result.mutation!!.grouping!!.significance)
        assertEquals("outlined_flag", result.mutation.grouping.icon.wire)
        assertEquals(result, GameplayHistory.replay(cleanup, result.mutation))
        val reference = EventReference(initial.id, initial.currentNodeId)
        for (action in listOf(PlayAction.EventComment(reference, "comment"),
                PlayAction.Ping(reference, PingTarget(PingTargetKind.CARD, id(20))))) {
            val unavailable = GameplayRules.change(initial, id(7), command(action, 2))
            assertEquals("missingReference", unavailable.reason)
            assertNull(unavailable.state); assertNull(unavailable.mutation)
        }
        val unsupported = PlayAction.MoveCard(id(20), MoveSource(id(10)),
            MoveDestination(id(11), MoveInsertion.EMPTY, position = BoardPosition(1, 2)))
        val rejected = GameplayRules.change(initial, id(7), command(unsupported, 3))
        assertEquals("invalidInput", rejected.reason) // EMPTY/position are Battlefield operands, not a Hand destination.
        assertNull(rejected.state); assertNull(rejected.mutation)
    }
}
