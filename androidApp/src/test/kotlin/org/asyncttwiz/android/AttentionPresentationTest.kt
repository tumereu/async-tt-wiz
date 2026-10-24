package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class AttentionPresentationTest {
    @Test fun pillAndBattlefieldTintAgreeInBothOrientationsAndReviewDisablesPass() {
        val initial = TableFixture.play()
        for (holder in listOf(null, "a", "b")) for (viewer in listOf("a", "b")) {
            val play = initial.copy(board = initial.board.copy(attentionHolder = holder))
            val own = play.board.effectiveAttentionHolder == viewer
            for ((w, h, landscape) in listOf(Triple(412f, 892f, false), Triple(892f, 412f, true))) {
                for (reviewing in listOf(false, true)) {
                    val pill = attentionPresentation(TableFixture.game, play, viewer, reviewing, true)
                    assertEquals(if (own) "Your move" else if (viewer == "a") "Bob's move" else "Alice's move", pill.label)
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
