package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Decision
import arcanum.engine.input.Block
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Automatum's choices on a game played on engine state (R4b's replacements of the old model's choice screen tests):
 * Alice's discard to hand size in her cleanup step on the simulated server ([EngineTableKit]), drawn by [TableSurface]
 * as [EngineTableScreen] draws it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class EngineChoiceScreenTest {
    @get:Rule val compose = createComposeRule()
    private val kit = EngineTableKit()
    private val f = kit.f
    private val toolkit = kit.toolkit
    private var version by mutableIntStateOf(0)

    @After fun forget() = AutomatumBoards.reset()

    private fun render() {
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides kit.routes) {
                        Box(Modifier.fillMaxSize()) {
                            version
                            TableSurface(f.game, kit.alice.play(), f.a, false, Modifier.fillMaxSize(), connected = true,
                                onIntent = toolkit::handle, toolkit = toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { kit.routes.undo() }, controlsConnected = true))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Alice keeps seven and plays nothing; in her second turn she has eight cards at her cleanup step: discard one. */
    private fun toDiscard(): Decision.ChooseObjects {
        kit.opening()
        assertTrue(toolkit.automatumStep(TableActionIntent.End(kit.scope)), "End turn")
        kit.playOn("Alice's second Main 1") { kit.aliceMain() && kit.state().turn.number >= 3 }
        assertEquals(8, kit.state().player(kit.me).hand.size)
        assertTrue(toolkit.automatumStep(TableActionIntent.End(kit.scope)), "End turn")
        kit.playOn("Alice's discard") { kit.state().pending?.decision is Decision.ChooseObjects }
        val decision = assertIs<Decision.ChooseObjects>(kit.state().pending?.decision)
        assertEquals(kit.me, decision.player)
        return decision
    }

    // STK-043, CR 514.1: discarding is one screen of two labelled areas with a count, Hand and Discard; the cards start in
    // Hand, a tap moves one to Discard, OK is enabled once exactly the number asked is there and sends the choice
    @Test fun aDiscardIsAChoiceBetweenHandAndDiscard() {
        toDiscard()
        render()
        compose.onNodeWithText("Hand · 8").assertExists()
        compose.onNodeWithText("Discard · 0").assertExists()
        compose.onNodeWithText("OK").assertIsNotEnabled()
        val area = compose.onNodeWithContentDescription("Hand area").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(area.left + 40f, area.center.y)) }
        compose.waitForIdle()
        compose.onNodeWithText("Hand · 7").assertExists()
        compose.onNodeWithText("Discard · 1").assertExists()
        compose.onNodeWithText("OK").assertIsEnabled().performClick()
        compose.waitForIdle()
        kit.settle()
        assertEquals(7, kit.state().player(kit.me).hand.size, "the discard went out in one step")
        assertEquals(1, kit.state().player(kit.me).graveyard.size)
    }

    // STK-032: while Automatum's choice is open, casting is refused ("Automatum is waiting for a choice. Answer it first."
    // on the deciding player's device; on the other player's, whose table waits for the choice, without a message);
    // looking and holding to inspect still work
    @Test fun whileAChoiceIsOpenCastingIsRefused() {
        toDiscard()
        val card = kit.atw(kit.hand("Storm Spark").first())
        assertFalse(toolkit.cast.start(card))
        assertEquals(SharedFlowController.BUSY, toolkit.error)
        toolkit.inspect(card)
        assertNotNull(toolkit.inspection, "Inspect still works")
        kit.settle()
        assertTrue(kit.bob.play().flowOpen, "Bob's table shows the open choice")
        val bobs = kit.bob.device.model.engine.let { s -> s.player(kit.other).hand.first() as ObjectId.Card }.card.value
        assertIs<GameDevice.Status.Waiting>(kit.bob.device.status)
        assertFalse(kit.bobToolkit.cast.start(bobs), "refused on Bob's device too")
        assertFalse(kit.bobToolkit.cast.open)
    }

    // STK-033, CMB-033, CR 510.1c: a division of combat damage Automatum asks for is its open choice, made in the division
    // panel: a row per creature that may get damage, filled in with Automatum's suggestion (lethal damage to each in
    // turn); OK deals the combat damage. Alice's Ember Cub (2/1) is blocked by three of Bob's (with two, lethal damage to
    // each is the only in-order result: made without asking, CMB-033)
    @Test fun aDivisionOfCombatDamageIsMadeInItsPanel() {
        val other = kit.other
        kit.scenario.confirmStops[f.b] = false
        // Bob: a Mountain and an Ember Cub each turn
        kit.bobMain = { s ->
            val land = kit.scenario.cards(s, other, s.player(other).hand, "Mountain").firstOrNull()
            val cub = kit.scenario.cards(s, other, s.player(other).hand, "Ember Cub").firstOrNull()
            when {
                (s.turn.landsPlayed[other] ?: 0) == 0 && land != null -> { kit.bob.send(listOf(Input.PlayLand(other, land))); true }
                cub != null && kit.scenario.cards(s, other, s.battlefield, "Mountain").any { s.obj(it)?.tapped == false } -> {
                    kit.bob.send(kit.scenario.cast(s, other, Input.Cast(other, cub))); true
                }
                else -> false
            }
        }
        val attacker = kit.toReadyCub()
        fun bobsCubs() = kit.scenario.cards(kit.state(), other, kit.state().battlefield, "Ember Cub")
        kit.playOn("Bob with three Cubs, Alice in her Main 1", rounds = 80) {
            if (kit.aliceMain() && bobsCubs().size < 3) kit.toolkit.automatumStep(TableActionIntent.End(kit.scope))
            // Alice lets Bob's Cubs resolve and passes in his turn
            if (kit.alice.device.status == GameDevice.Status.YourMove && kit.state().turn.active == other &&
                kit.state().pending == null && kit.state().priority.holder == kit.me) kit.alice.pass()
            kit.aliceMain() && bobsCubs().size >= 3
        }
        kit.toAttacks()
        kit.tap(kit.item(attacker))
        kit.settle()
        assertTrue(toolkit.automatumStep(TableActionIntent.Next(kit.scope)), "Attack (1)")
        fun bobBlocks() = Engine.options(kit.bob.device.model.engine, other).any { it is Input.DeclareBlockers }
        kit.playOn("Bob declaring blockers", bobActs = false) {
            // Bob lets the attack go on to his blocks
            if (kit.bob.device.status == GameDevice.Status.YourMove && !bobBlocks() &&
                kit.bob.device.model.engine.priority.holder == other) kit.bob.pass()
            kit.bob.device.status == GameDevice.Status.YourMove && bobBlocks()
        }
        val alicesCub = kit.state().battlefield.first { (it as ObjectId.Card).card.value == attacker }
        kit.bob.send(listOf(Input.DeclareBlockers(other, bobsCubs().take(3).map { Block(it, alicesCub) })))
        kit.playOn("Alice dividing her Cub's damage") {
            if (kit.alice.device.status == GameDevice.Status.YourMove && kit.state().pending == null &&
                kit.state().priority.holder == kit.me) toolkit.automatumStep(TableActionIntent.Next(kit.scope))
            kit.state().pending?.decision is Decision.Divide
        }
        val division = assertIs<Decision.Divide>(kit.state().pending?.decision)
        assertEquals(kit.me, division.player)
        assertEquals(2, division.amount)
        render()
        compose.onNodeWithContentDescription("Division of combat damage").assertExists()
        assertEquals(2, compose.onAllNodesWithContentDescription("Ember Cub: 1").fetchSemanticsNodes().size,
            "a row per blocker, lethal damage to each in turn")
        assertEquals(1, compose.onAllNodesWithContentDescription("Ember Cub: 0").fetchSemanticsNodes().size)
        compose.onNodeWithText("OK").assertIsEnabled().performClick()
        compose.waitForIdle()
        kit.playOn("the combat damage") { kit.state().turn.step != TurnStep.CombatDamage || kit.state().pending == null }
        kit.settle()
        assertEquals(2, kit.scenario.cards(kit.state(), other, kit.state().player(other).graveyard, "Ember Cub").size,
            "two blockers were dealt lethal damage")
    }
}
