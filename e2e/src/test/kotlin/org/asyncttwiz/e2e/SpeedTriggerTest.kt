package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import arcanum.engine.state.PlayerStatus
import arcanum.engine.view.AbilityOrigin
import org.asyncttwiz.android.StackAbilities
import org.asyncttwiz.android.StackAbilityShown
import org.asyncttwiz.protocol.CombatStackRules
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Owner notes 2026-11-14 evening: speed's increase (start your engines!, CR 702.179d) read "Ability's ability" on the Stack and in
 * the texts, a stand-in name. It is a triggered ability with no source (it uses the Stack, CR 113.3c, 603.3): it is named after its
 * rule, "Speed", with the rule's text. Alice (Control, so she holds priority over her own trigger) has an Ember Racer out and burns
 * Bob; Bob plays along.
 */
class SpeedTriggerTest : E2e() {
    private val speedText = "Whenever one or more opponents lose life during your turn, if your speed is less than 4, your speed increases by 1. " +
        "This ability triggers only once each turn."

    private fun E2ePlayer.speed(): Int = state.player(alice.me).statuses.filterIsInstance<PlayerStatus.Speed>().singleOrNull()?.value ?: 0

    private fun E2ePlayer.speedOnTheStack(): Boolean = state.let { s -> s.stack.any { s.stackItems[it]?.origin == AbilityOrigin.Rule("speed") } }

    // STK-022, CR 702.179d, 603.3: the speed increase goes on the Stack as an entry of its own named "Speed" with the rule's text
    // (never a stand-in), and raises the speed as it resolves
    @Test fun theSpeedIncreaseIsNamedSpeedOnTheStack() {
        startGame(TestDeck.racers(), TestDeck.sparks(), deal = { a, _ -> listOf("Mountain", "Ember Racer", "Storm Spark").all { it in hand(a) } })
        bothOpen()
        bothKeep(bobAway = false)
        // Control from her main phase on (her table's response mode, TRN-059)
        alice.game!!.settings(alice.shown.settings.copy(mode = ResponseMode.FullControl))
        waitUntil("Alice in Control") { alice.shown.settings.mode == ResponseMode.FullControl && alice.inOwnMain() }
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Racer", this)
        waitUntil("Alice holding priority over her Racer", along = listOf(bob)) { alice.state.stack.isNotEmpty() && alice.stepArrow() == "Resolve" }
        alice.tap("Resolve")
        waitUntil("the Racer on the battlefield, speed 1", along = listOf(bob)) {
            alice.field("Ember Racer").isNotEmpty() && alice.state.stack.isEmpty() && alice.inOwnMain() && alice.speed() == 1
        }
        alice.playFromHand("Storm Spark", this)
        waitUntil("Alice's target bar") { alice.shows(hasText("Choose any target") and isHeading()) }
        alice.node(hasContentDescription("${bob.name}, life 20", substring = true)).performClick()
        waitUntil("Alice holding priority over her Spark", along = listOf(bob)) { alice.state.stack.isNotEmpty() && alice.stepArrow() == "Resolve" }
        alice.tap("Resolve")
        waitUntil("the speed increase on the Stack, Alice holding priority", along = listOf(bob)) {
            alice.state.player(bob.me).life == 17 && alice.speedOnTheStack() && alice.stepArrow() == "Resolve" && alice.current()
        }
        val play = alice.shown.board
        val entry = assertNotNull(CombatStackRules.stack(play.board).cards.firstOrNull())
        assertEquals(StackAbilityShown(triggered = true, source = "Speed", text = speedText), StackAbilities.of(play, entry, alice.id))
        alice.node(hasContentDescription("Stack, 1 entries")).performClick()
        waitUntil("the Stack list naming it") { alice.showsText("Top · Speed's ability") }
        alice.tap("Close")
        waitUntil("the Stack list closed") { !alice.showsText("Top · Speed's ability") && alice.stepArrow() == "Resolve" && alice.current() }
        alice.tap("Resolve")
        waitUntil("speed 2", along = listOf(bob)) { alice.speed() == 2 && alice.state.stack.isEmpty() }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}
