package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class AttentionPresentationTest {
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
            assertFalse(tracker.observe(waiting, !reviewBefore, null))
            val change = GameplayRules.change(waiting, author, d.command(action))
            val next = requireNotNull(change.state)
            val arrived = tracker.observe(next, !reviewAfter, change.mutation)
            assertFalse(tracker.observe(next, !reviewAfter, change.mutation), "An endpoint must pulse only once")
            return arrived
        }
        assertTrue(arrival(d.other, PlayAction.PassAttention))
        assertFalse(arrival(d.actor, PlayAction.PassAttention))
        assertTrue(arrival(d.actor, PlayAction.NextStep))
        assertFalse(arrival(d.other, PlayAction.PassAttention, reviewBefore = true))
        assertFalse(arrival(d.other, PlayAction.PassAttention, reviewAfter = true))
    }

    @Test fun movePillIsIndependentOfTurnAndReviewDisablesPass() {
        val initial = TableFixture.play()
        for (holder in listOf(null, "a", "b")) for (viewer in listOf("a", "b")) {
            val play = initial.copy(board = initial.board.copy(attentionHolder = holder))
            val own = play.board.effectiveAttentionHolder == viewer
            for ((w, h, landscape) in listOf(Triple(412f, 892f, false), Triple(892f, 412f, true))) {
                for (reviewing in listOf(false, true)) {
                    val pill = attentionPresentation(TableFixture.game, play, viewer, reviewing, true)
                    assertEquals(if (own) "Your move" else if (viewer == "a") "Waiting · Bob" else "Waiting · Alice", pill.label)
                    assertEquals(if (own) OwnTableColor else OpponentTableColor, pill.color)
                    assertEquals(!reviewing, pill.enabled)
                    val scene = TablePresentation.scene(TableFixture.game, play, viewer, w, h, landscape, reviewing = reviewing)
                    assertEquals(play.board.effectiveAttentionHolder, scene.fields.single { it.holdsAttention }.owner)
                }
            }
            assertFalse(attentionPresentation(TableFixture.game, play, viewer, false, false).enabled)
        }
    }

    @Test fun historyPassNamesTheActualDestinationEvenWhenNonHolderTakesTheFlag() {
        val d = GameplayFixture()
        val changed = GameplayRules.change(d.initial, d.actor, d.command(PlayAction.PassAttention))
        val holder = changed.state!!.board.effectiveAttentionHolder
        val names = mapOf(d.actor to "Alice", d.other to "Bob")
        assertEquals("Alice passed the move to ${names.getValue(holder)}",
            historyText(changed.mutation, d.initial, d.actor, names).description)
    }

    @Test fun combatAdvanceQuickActionUsesTheSharedAttentionReset() {
        val d = GameplayFixture()
        var play = d.initial.copy(board = d.initial.board.copy(
            turn = d.initial.board.turn.copy(phase = "COMBAT", step = "END_COMBAT"), attentionHolder = d.other))
        val sent = mutableListOf<PlayAction>()
        val toolkit = TableToolkitController(d.actor, { play }, { true }, { action, _ ->
            sent += action
            val change = GameplayRules.change(play, d.actor, d.command(action))
            change.state?.also { play = it } != null
        })
        assertTrue(toolkit.perform(TableActionIntent.Combat(TableActionScope(play.gameId, play.id),
            CombatTool.ADVANCE_AND_CLOSE, null)))
        assertEquals(listOf<PlayAction>(PlayAction.NextStep), sent)
        assertEquals(TurnPosition.POSTCOMBAT_MAIN, TurnPosition.of(play.board.turn))
        assertEquals(d.actor, play.board.effectiveAttentionHolder)
    }
}
