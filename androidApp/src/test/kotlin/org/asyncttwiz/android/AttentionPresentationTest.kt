package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class AttentionPresentationTest {
    // HIS-008, TRN-013
    @Test fun timelineBarsAreNeutralRegardlessOfTurnViewerOrAttention() {
        val initial = TableFixture.play().board
        for (turn in listOf("a", "b")) for (viewer in listOf("a", "b")) for (holder in listOf(null, "a", "b")) {
            val board = initial.copy(turn = initial.turn.copy(accountId = turn), attentionHolder = holder)
            assertEquals(TimelineBarColor, tableBarColor(board, viewer, reviewing = true))
            assertEquals(battlefieldColor(board.effectiveAttentionHolder, viewer, board.effectiveAttentionHolder, false), tableBarColor(board, viewer))
            assertNotEquals(TimelineBarColor, tableBarColor(board, viewer))
        }
    }

    // TRN-005, TRN-012
    @Test fun turnTextAndFieldColorsFollowDisplayedTurnNotTheMove() {
        val initial = TableFixture.play()
        for (turnPlayer in listOf("a", "b")) for (viewer in listOf("a", "b")) {
            val turn = initial.board.turn.copy(accountId = turnPlayer, number = "42")
            assertEquals(if (turnPlayer == viewer) "Your turn · 42" else
                "${if (turnPlayer == "a") "Alice" else "Bob"}'s turn · 42", turnHeader(TableFixture.game, turn, viewer))
            for (lands in listOf(false, true)) {
                assertEquals(androidx.compose.ui.graphics.Color(if (lands) 0xffdfe2e2 else 0xffeceeee),
                    battlefieldColor(if (turnPlayer == "a") "b" else "a", viewer, turnPlayer, lands))
                assertEquals(androidx.compose.ui.graphics.Color(if (turnPlayer == viewer) {
                    if (lands) 0xffc4dcd0 else 0xffe2efe6
                } else if (lands) 0xffe9d1d3 else 0xfff4e8ea), battlefieldColor(turnPlayer, viewer, turnPlayer, lands))
            }
        }
    }

    @Test fun moveArrivalPulsesOnlyForLiveRemoteOrAutomaticStepArrivals() {
        val d = GameplayFixture()
        val waiting = d.initial.copy(board = d.initial.board.copy(attentionHolder = d.other))
        fun arrival(author: String, action: PlayAction, reviewBefore: Boolean = false, reviewAfter: Boolean = false): Boolean {
            val tracker = MoveArrivalTracker(d.actor)
            assertFalse(tracker.observe(tableBoard(waiting), !reviewBefore, null))
            val change = GameplayRules.change(waiting, author, d.command(action))
            val next = requireNotNull(change.state)
            val arrived = tracker.observe(tableBoard(next), !reviewAfter, change.mutation)
            assertFalse(tracker.observe(tableBoard(next), !reviewAfter, change.mutation), "An endpoint must pulse only once")
            return arrived
        }
        assertTrue(arrival(d.other, PlayAction.PassAttention))
        assertFalse(arrival(d.actor, PlayAction.PassAttention))
        assertTrue(arrival(d.actor, PlayAction.NextStep))
        assertFalse(arrival(d.other, PlayAction.PassAttention, reviewBefore = true))
        assertFalse(arrival(d.other, PlayAction.PassAttention, reviewAfter = true))
    }

    // TRN-006
    @Test fun movePillIsIndependentOfTurnAndReviewDisablesPass() {
        val initial = TableFixture.play()
        for (holder in listOf(null, "a", "b")) for (viewer in listOf("a", "b")) {
            val play = initial.copy(board = initial.board.copy(attentionHolder = holder))
            val own = play.board.effectiveAttentionHolder == viewer
            for ((w, h, landscape) in listOf(Triple(412f, 892f, false), Triple(892f, 412f, true))) {
                for (reviewing in listOf(false, true)) {
                    val pill = attentionPresentation(TableFixture.game, tableBoard(play), viewer, reviewing, true)
                    assertEquals(if (own) "Your move" else if (viewer == "a") "Waiting · Bob" else "Waiting · Alice", pill.label)
                    assertEquals(if (own) OwnTableColor else OpponentTableColor, pill.color)
                    assertEquals(!reviewing, pill.enabled)
                    val scene = TablePresentation.scene(TableFixture.game, tableBoard(play), viewer, w, h, landscape, reviewing = reviewing)
                    assertEquals(play.board.effectiveAttentionHolder, scene.fields.single { it.holdsAttention }.owner)
                }
            }
            assertFalse(attentionPresentation(TableFixture.game, tableBoard(play), viewer, false, false).enabled)
        }
    }

    // TRN-058 (I-D57): on engine state the move pill, the bar colours and the field that holds the move show whom the
    // game really waits for (the board's move), not who acts on the board shown: a decision stop of the viewer's in
    // playback while the other player plays on is the other player's move
    @Test fun theMovePillAndBarsFollowWhomTheGameReallyWaitsFor() {
        val initial = TableFixture.play()
        for (viewer in listOf("a", "b")) {
            val other = if (viewer == "a") "b" else "a"
            val play = initial.copy(board = initial.board.copy(attentionHolder = viewer, move = other))
            val pill = attentionPresentation(TableFixture.game, tableBoard(play), viewer, false, true)
            assertEquals(if (viewer == "a") "Waiting · Bob" else "Waiting · Alice", pill.label)
            assertFalse(pill.own)
            assertEquals(battlefieldColor(other, viewer, other, false), tableBarColor(play.board, viewer))
            val scene = TablePresentation.scene(TableFixture.game, tableBoard(play), viewer, 412f, 892f, false)
            assertEquals(other, scene.fields.single { it.holdsAttention }.owner)
            // The game waits for the viewer: their move
            val own = initial.copy(board = initial.board.copy(attentionHolder = other, move = viewer))
            assertEquals("Your move", attentionPresentation(TableFixture.game, tableBoard(own), viewer, false, true).label)
            assertEquals(battlefieldColor(viewer, viewer, viewer, false), tableBarColor(own.board, viewer))
        }
    }

    // TRN-006, HIS-094 (smoke 2026-11-04): in the catch-up pause the board is an earlier point: the pill reads "Catching up"
    // in the move's colours (TRN-058: the move itself stays whom the game waits for)
    @Test fun theCatchUpPauseReadsCatchingUpInTheMovesColours() {
        val initial = TableFixture.play()
        for (move in listOf("a", "b")) {
            val play = initial.copy(board = initial.board.copy(attentionHolder = move, move = move))
            val pill = attentionPresentation(TableFixture.game, tableBoard(play), "a", false, true, catchingUp = true)
            assertEquals("Catching up", pill.label)
            assertEquals(move == "a", pill.own)
        }
    }


}
