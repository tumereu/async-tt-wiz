package org.asyncttwiz.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.protocol.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatusBubbleChoicesTest {
    private val nodes = mutableMapOf<String, GameplayMutation>()
    private val lookup: (String) -> GameplayMutation? = { nodes[it] }

    /** A node after [parent]; [status] non-null makes it change player "b"'s status. */
    private fun node(id: String, parent: String, status: String? = null, player: String = "b"): String {
        val fields = if (status == null) listOf(GameplayFieldChange("c1", GameplayField.TAPPED, "false", "true"))
            else listOf(GameplayFieldChange(player, GameplayField.STATUS, "", status))
        nodes[id] = GameplayMutation(id, parent, "main", "r-$id", player, null,
            if (status == null) PlayAction.SetTapped("c1", true) else PlayAction.SetStatus(status), GameplayTransition(fields))
        return id
    }

    private fun hidden(cursor: String, status: String, hide: StatusBubbleHide) =
        StatusBubbleRules.hidden(lookup, cursor, "b", status, hide)

    @Test fun hideLastsThroughOtherEventsButANewStatusShowsAgain() {
        node("n1", "root", "Wait"); node("n2", "n1")
        val hide = StatusBubbleRules.hide(lookup, "n2", "b", "Wait")
        assertEquals("n1", hide.key)
        node("n3", "n2"); node("n4", "n3", status = "Ready", player = "a")
        assertTrue(hidden("n4", "Wait", hide))
        node("n5", "n4", "BRB")
        assertFalse(hidden("n5", "BRB", hide))
    }

    @Test fun sameTextSetAgainByANewEventShowsAgain() {
        node("n1", "root", "Wait"); node("n2", "n1")
        val hide = StatusBubbleRules.hide(lookup, "n2", "b", "Wait")
        node("n3", "n2", "BRB"); node("n4", "n3", "Wait")
        assertFalse(hidden("n4", "Wait", hide))
    }

    @Test fun undoKeepsTheHideUntilTheStatusEventItselfIsUndoneOrRedone() {
        node("n1", "root", "Wait"); node("n2", "n1")
        val hide = StatusBubbleRules.hide(lookup, "n2", "b", "Wait")
        // Undoing the unrelated hide-point event keeps the same status event in effect.
        assertTrue(hidden("n1", "Wait", hide))
        // Undoing the status event changes the status; redoing it is a new event.
        assertFalse(hidden("root", PlayerStatus.OPENING, hide))
        node("n1b", "root", "Wait")
        assertFalse(hidden("n1b", "Wait", hide))
    }

    @Test fun openingStatusWithoutAnyEventCanBeHidden() {
        val hide = StatusBubbleRules.hide(lookup, "root", "b", PlayerStatus.OPENING)
        node("n1", "root"); node("n2", "n1")
        assertTrue(hidden("n2", PlayerStatus.OPENING, hide))
        assertFalse(hidden("n2", "", hide))
    }

    @Test fun modesMuteToADotAndNothingForEmptyStatus() {
        val hide = StatusBubbleHide("Wait", "n1", "n1")
        assertEquals(StatusBubbleMode.BUBBLE, StatusBubbleRules.mode("Wait", StatusBubbleChoice(), hidden = false))
        assertEquals(StatusBubbleMode.DOT, StatusBubbleRules.mode("Wait", StatusBubbleChoice(muted = true), hidden = false))
        assertEquals(StatusBubbleMode.NONE, StatusBubbleRules.mode("Wait", StatusBubbleChoice(hidden = hide), hidden = true))
        assertEquals(StatusBubbleMode.NONE, StatusBubbleRules.mode("", StatusBubbleChoice(muted = true), hidden = false))
        assertEquals(StatusBubbleMode.NONE, StatusBubbleRules.mode("", StatusBubbleChoice(), hidden = false))
    }

    @Test fun dotSitsOnTheLifeSquareCornerOnTheBubbleSide() {
        val life = TableBounds(10f, 100f, 48f, 48f)
        assertEquals(life.right, StatusBubble.dot(life, below = true).center.x)
        assertEquals(life.bottom, StatusBubble.dot(life, below = true).center.y)
        assertEquals(life.y, StatusBubble.dot(life, below = false).center.y)
        assertEquals(StatusBubble.DOT, StatusBubble.dot(life, below = false).width)
    }

    @Test fun choicesSurviveNewStoresPerAccountGameAndPlayer() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("status-bubbles", Context.MODE_PRIVATE).edit().clear().commit()
        val hide = StatusBubbleHide("Wait", "n1", "n2")
        StatusBubbleStore(context, "alice", "game").write("b", StatusBubbleChoice(hidden = hide))
        StatusBubbleStore(context, "alice", "game").write("a", StatusBubbleChoice(muted = true))
        val reopened = StatusBubbleStore(context, "alice", "game")
        assertEquals(StatusBubbleChoice(hidden = hide), reopened.choice("b"))
        assertEquals(StatusBubbleChoice(muted = true), reopened.choice("a"))
        assertEquals(StatusBubbleChoice(), StatusBubbleStore(context, "bob", "game").choice("b"))
        assertEquals(StatusBubbleChoice(), StatusBubbleStore(context, "alice", "other").choice("b"))
        reopened.write("b", StatusBubbleChoice())
        assertEquals(StatusBubbleChoice(), StatusBubbleStore(context, "alice", "game").choice("b"))
    }

    @Test fun controllerTogglesAndModes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("status-bubbles", Context.MODE_PRIVATE).edit().clear().commit()
        val initial = TableFixture.play(library = 0)
        val play = initial.copy(board = initial.board.copy(players = initial.board.players.map {
            it.copy(status = if (it.accountId == "a") "" else "Wait")
        }))
        val bubbles = StatusBubbles(StatusBubbleStore(context, "a", play.gameId)) { null }
        assertEquals(mapOf("a" to StatusBubbleMode.NONE, "b" to StatusBubbleMode.BUBBLE), bubbles.modes(play))
        bubbles.hide(play, "b", hidden = true)
        assertEquals(StatusBubbleMode.NONE, bubbles.modes(play)["b"])
        bubbles.hide(play, "b", hidden = false)
        assertEquals(StatusBubbleMode.BUBBLE, bubbles.modes(play)["b"])
        bubbles.mute("b", true)
        assertTrue(bubbles.muted("b")); assertEquals(StatusBubbleMode.DOT, bubbles.modes(play)["b"])
        bubbles.mute("b", false)
        assertEquals(StatusBubbleMode.BUBBLE, bubbles.modes(play)["b"])
    }
}
