package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import arcanum.engine.decision.Decision
import org.asyncttwiz.android.EngineGameFixture.Choices
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Owner playtest, 2026-11-09, on Alice's table: a source with several ways to pay pays the sensible one when tapped and carries
 * its corner chip, which opens its ways (STK-024); Cast is the main button (STK-052, TRN-063); a payment while something
 * resolves is the slim bar, its sources tapped on the table and Pay the main button (STK-049).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PayOnTableTest {
    @get:Rule val compose = createComposeRule()
    private var deck = listOf(Choices.FORK, Choices.BLUE_NOTE, "Mountain")
    private val kit by lazy { EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = deck))) }
    private val toolkit get() = kit.toolkit
    private var version by mutableIntStateOf(0)

    @After fun forget() = AutomatumBoards.reset()

    private fun render() {
        // HIS-112: no plays shown (Alice's own triggers would hold her table a moment each)
        HistoryPlaybackPreferences(androidx.test.core.app.ApplicationProvider.getApplicationContext(), kit.f.a).showPlaysMillis = 0
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides kit.routes) {
                        Box(Modifier.fillMaxSize()) {
                            version
                            TableSurface(kit.f.game, kit.alice.play(), kit.f.a, false, Modifier.fillMaxSize(), connected = true,
                                onIntent = toolkit::handle, toolkit = toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { kit.routes.undo() }, controlsConnected = true))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun refresh() { version++; compose.waitForIdle() }

    private fun pending(): Decision? = kit.state().pending?.decision

    // STK-024, STK-052, TRN-063 (owner playtest 2026-11-09), CR 601.2h: with Autotap off, a tap on the Ember Fork ({R} or {U}) for Blue
    // Note ({U}) takes {U}; its corner chip says so and opens its ways; Cast is the main button, the panel has none
    @Test fun aSourceOfTwoColoursPaysTheSensibleWayAndItsChipOpensItsWays() {
        kit.opening()
        val fork = kit.atw(kit.hand(Choices.FORK).first())
        assertTrue(toolkit.cast.start(fork), "the Fork is cast: ${toolkit.error}")
        kit.playOn("the Fork on the battlefield") { fork in kit.field(Choices.FORK).map(kit::atw) && kit.aliceMain() }
        toolkit.cast.autotap = false
        val note = kit.atw(kit.hand(Choices.BLUE_NOTE).first())
        render()
        assertTrue(toolkit.cast.start(note), "Blue Note's panel: ${toolkit.error}")
        // In portrait the panel covers the bottom of her battlefield: she pans the table up to see her sources above it
        toolkit.layoutState.value = toolkit.layoutState.value.copy(camera = TableCamera(y = 150f))
        refresh()
        compose.onNode(hasTestTag("main-button") and hasContentDescription("Cast")).assertIsNotEnabled()
        kit.tap(kit.item(fork))
        refresh()
        assertEquals(setOf(fork), toolkit.cast.paySources())
        assertEquals(arcanum.api.Color.Blue, (toolkit.cast.paidWith(fork) as arcanum.engine.decision.ManaUse.Ability).color)
        compose.onNode(hasTestTag("main-button") and hasContentDescription("Cast")).assertIsEnabled()
        compose.onNode(hasContentDescription("Pays {U}: ", substring = true)).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Pay with {R}").assertExists()
        captureShot("$SCREENSHOTS/cast-panel/source-ways.png",
            "Alice cast Blue Note ({U}) with Autotap off, panned the table up and tapped her Ember Fork ({R} or {U}): it pays {U}, the way that pays the cost, shown on its amber corner chip; a tap on the chip opened its ways, {R} and {U} (marked), and Cast is on the main button.")
        compose.onNodeWithContentDescription("Pay with {R}").performClick()
        compose.waitForIdle()
        assertEquals(arcanum.api.Color.Red, (toolkit.cast.paidWith(fork) as arcanum.engine.decision.ManaUse.Ability).color)
        compose.onNode(hasTestTag("main-button") and hasContentDescription("Cast")).assertIsNotEnabled()
    }

    // STK-049, TRN-063 (owner playtest 2026-11-09), CR 118.12: Ember Tollkeeper's "you may pay {1}" is the slim bar "Pay {1}" with Don't
    // pay, Autotap's Mountain outlined on the table, and Pay the main button
    @Test fun aPaymentWhileResolvingIsTheSlimBarWithPayOnTheMainButton() {
        deck = listOf(Choices.TOLLKEEPER, "Mountain")
        kit.opening()
        kit.playLand()
        val keeper = kit.atw(kit.hand(Choices.TOLLKEEPER).first())
        render()
        assertTrue(toolkit.cast.start(keeper), "the Tollkeeper is cast: ${toolkit.error}")
        kit.playOn("the Tollkeeper's question", rounds = 40) {
            if (kit.state().stack.isNotEmpty() && kit.state().priority.holder == kit.me && pending() == null) {
                (kit.alice.device.status as? GameDevice.Status.Stopped)?.let { kit.alice.act(it.assumed) } ?: kit.alice.pass()
            }
            pending() is Decision.May
        }
        kit.settle()
        refresh()
        assertIs<Decision.May>(pending())
        compose.onNodeWithContentDescription("Pay on the table").assertExists()
        compose.onNodeWithContentDescription("Automatum choice").assertDoesNotExist()
        compose.onNode(hasTestTag("main-button") and hasContentDescription("Pay")).assertIsEnabled()
        val mountain = kit.atw(kit.field("Mountain").first())
        assertEquals(setOf(mountain), toolkit.paySources())
        captureShot("$SCREENSHOTS/questions/pay-on-table.png",
            "Alice cast her Ember Tollkeeper, whose trigger asks \"you may pay {1}\": a slim bar \"Pay {1}\" over the sentence with \"Paid\" beside it and Don't pay, the Mountain Autotap chose outlined in amber on the table with its {R} corner chip, and Pay on the main button.")
    }
}
