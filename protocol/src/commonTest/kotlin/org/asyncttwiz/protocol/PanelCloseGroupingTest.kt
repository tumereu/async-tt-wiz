package org.asyncttwiz.protocol

import kotlin.test.*

/** Rules v29: closing a Search/Look/Reveal panel, with its rest moves and shuffle, is its own history step. */
class PanelCloseGroupingTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val a = id(7)
    private val b = id(8)
    private val seed = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })
    private var request = 100
    private fun root(): PlayState {
        val definition = CardDefinition("Wizard", listOf(DefinitionFace(id(40), "Wizard", corner = "2/2")))
        val cards = (20..24).map { PlayCard(id(it), "definition", a, id(10), id(40)) }
        return PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(a), PlayPlayer(b)), cards,
                listOf(PlayContainer(id(10), PlayZone.LIBRARY, a, cards.map { it.id }), PlayContainer(id(11), PlayZone.HAND, a),
                    PlayContainer(id(12), PlayZone.GRAVEYARD, a), PlayContainer(id(13), PlayZone.EXILE, a),
                    PlayContainer(id(14), PlayZone.BATTLEFIELD, a)), TurnState(a)),
            listOf(SubmittedDefinition("definition", definition)), emptyList(), InitialStarter(StarterChoice(a), a))
    }
    private fun change(state: PlayState, action: PlayAction, actor: String = a) = GameplayRules.change(state, actor,
        GameCommand(id(request++), GameScope(state.gameId, playId = state.id), GameAction.Play(action), id(90), "1"))
    private fun step(state: PlayState, action: PlayAction, actor: String = a): PlayState {
        val result = change(state, action, actor)
        assertEquals("accepted", result.outcome, "$action: ${result.reason}")
        return assertNotNull(result.state)
    }
    private fun group(state: PlayState) = assertNotNull(state.currentGroup)
    /** The close event is a complete group of its own, and the next event does not join it. */
    private fun assertOwnStep(before: PlayState, closed: PlayState, actor: String) {
        assertNotEquals(group(before).id, group(closed).id)
        assertEquals(closed.currentNodeId, group(closed).firstNodeId)
        assertEquals(closed.currentNodeId, group(closed).lastNodeId)
        assertEquals(actor, group(closed).author)
        assertEquals(HistorySignificance.VIEW_REVEAL_DISMISSAL, group(closed).representative!!.semantic.significance)
        val next = step(closed, PlayAction.AdjustLife(actor, "1"), actor)
        assertNotEquals(group(closed).id, group(next).id)
        assertNotEquals(group(closed).id, group(step(closed, PlayAction.PassAttention, if (actor == a) b else a)).id)
    }

    @Test fun searchSessionIsOneGroupAndTheCloseWithItsShuffleIsTheNext() {
        val opened = step(root(), PlayAction.ShowContents(id(10), ContentsSelection.ALL))
        val panel = opened.board.contentsDisplays.single()
        val drawn = step(opened, PlayAction.MoveCard(id(20), MoveSource(id(10)), MoveDestination(id(11), MoveInsertion.UNORDERED)))
        val played = step(drawn, PlayAction.MoveCard(id(21), MoveSource(id(10)),
            MoveDestination(id(14), MoveInsertion.EMPTY, BoardPosition(0, 0))))
        val tapped = step(played, PlayAction.SetTapped(id(21), true))
        assertEquals(1, listOf(opened, drawn, played, tapped).map { group(it).id }.distinct().size)
        assertEquals("4", group(tapped).count)
        val result = change(tapped, PlayAction.DismissContents(panel.id, seed = seed))
        val closed = assertNotNull(result.state)
        // The rest goes back and is shuffled inside the close event itself.
        val order = assertNotNull(result.mutation!!.transition.libraryOrder)
        assertEquals(RandomV1.shuffle(tapped.board.containers.first().cards, seed), order.to)
        assertEquals(order.to, closed.board.containers.first().cards)
        assertOwnStep(tapped, closed, a)
    }

    @Test fun lookSessionWithARevealGroupsAsBeforeAndTheCloseWithItsRestMovesIsItsOwnStep() {
        val opened = step(root(), PlayAction.ShowContents(id(10), ContentsSelection.TOP, 3))
        val panel = opened.board.contentsDisplays.single()
        val revealed = step(opened, PlayAction.RevealContentsCard(panel.id, panel.cardIds.first()))
        val taken = step(revealed, PlayAction.MoveCard(panel.cardIds.first(), MoveSource(id(10)), MoveDestination(id(11), MoveInsertion.UNORDERED)))
        val result = change(taken, PlayAction.DismissContents(panel.id, ContentsRest.GRAVEYARD, seed))
        val closed = assertNotNull(result.state)
        assertEquals(panel.cardIds.drop(1).toSet(), closed.board.containers.single { it.id == id(12) }.cards.toSet())
        assertEquals(2, result.mutation!!.transition.disclosures.size)
        assertOwnStep(taken, closed, a)
        // A preceding tap group no longer takes in the close either.
        val reveal = step(root(), PlayAction.ShowContents(id(10), ContentsSelection.TOP, 2, reveal = true))
        val tap = step(step(reveal, PlayAction.MoveCard(id(20), MoveSource(id(10)),
            MoveDestination(id(14), MoveInsertion.EMPTY, BoardPosition(0, 0)))), PlayAction.SetTapped(id(20), true))
        assertEquals(HistoryCategory.T.key, group(tap).category)
        assertOwnStep(tap, step(tap, PlayAction.DismissContents(reveal.board.contentsDisplays.single().id, ContentsRest.BOTTOM, seed)), a)
    }

    @Test fun theOpponentClosingAnyPanelIsItsOwnStep() {
        for ((selection, reveal) in listOf(ContentsSelection.ALL to false, ContentsSelection.TOP to false, ContentsSelection.TOP to true)) {
            val opened = step(root(), PlayAction.ShowContents(id(10), selection, 2, reveal))
            val closed = step(opened, PlayAction.DismissContents(opened.board.contentsDisplays.single().id, ContentsRest.BOTTOM, seed), b)
            assertTrue(closed.board.contentsDisplays.isEmpty())
            assertOwnStep(opened, closed, b)
        }
    }

    @Test fun closesUnderOlderRulesGroupExactlyAsBefore() {
        val display = ContentsDisplay(id(30), a, id(10), ContentsSelection.ALL, 1, false, listOf(id(20)), panelVersion = 3)
        val look = display.copy(selection = ContentsSelection.TOP)
        fun close(panel: ContentsDisplay, version: Int) = HistoryGrouping.classify(PlayAction.DismissContents(panel.id),
            emptyList(), GameplayTransition(contents = ContentsDisplayChange(from = panel), rulesVersion = version))
        val open = HistoryGrouping.classify(PlayAction.ShowContents(id(10), ContentsSelection.ALL), emptyList(),
            GameplayTransition(contents = ContentsDisplayChange(to = display), rulesVersion = 28))
        val session = HistoryGrouping.append(null, id(50), id(60), a, open)
        val tap = HistoryGrouping.append(null, id(50), id(60), a,
            HistoryGrouping.classify(PlayAction.SetTapped(id(20), true), emptyList(), GameplayTransition(rulesVersion = 28)))
        val old = close(display, 28)
        assertFalse(old.isolated)
        assertFalse("isolated" in GameWire.json.encodeToString(HistorySemantic.serializer(), old))
        assertEquals(display.id, old.panelSession)
        // v28: the author's Search close joins its session; a Look close joins a preceding tap group.
        assertEquals(session.id, HistoryGrouping.append(session, id(51), id(61), a, old).id)
        assertEquals(tap.id, HistoryGrouping.append(tap, id(51), id(61), a, close(look, 28)).id)
        // v29: neither does.
        val new = close(display, 29)
        assertTrue(new.isolated)
        assertEquals(old.copy(isolated = true), new)
        assertNotEquals(session.id, HistoryGrouping.append(session, id(51), id(61), a, new).id)
        assertNotEquals(tap.id, HistoryGrouping.append(tap, id(51), id(61), a, close(look, 29)).id)
    }
}
