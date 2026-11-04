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
import arcanum.engine.input.Input
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The table of a game played on engine state as the player sees it (R4b's replacements of the old model's Automatum
 * screen tests): Alice's [EngineGame] on the simulated server ([EngineTableKit]) drawn by [TableSurface] with its routes
 * and toolkit, as [EngineTableScreen] draws it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class EngineTableScreenTest {
    @get:Rule val compose = createComposeRule()
    private val kit = EngineTableKit()
    private val f = kit.f
    private val toolkit = kit.toolkit
    /** Bumped to draw the game's newest board. */
    private var version by mutableIntStateOf(0)

    @After fun forget() = AutomatumBoards.reset()

    private fun render(game: EngineGame = kit.alice, viewer: String = f.a, routes: EngineTableRoutes = kit.routes,
        toolkit: TableToolkitController = kit.toolkit) {
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides routes) {
                        Box(Modifier.fillMaxSize()) {
                            version
                            val play = game.play()
                            TableSurface(f.game, play, viewer, false, Modifier.fillMaxSize(), connected = true,
                                onIntent = toolkit::handle, toolkit = toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { routes.undo() },
                                    controlsConnected = true, waitingLine = routes.waitingLine(play)))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun redraw() { compose.runOnIdle { version++ }; compose.waitForIdle() }

    // STK-044, STK-023: the target bar of a Storm Spark cast is the table's full width with its bottom on the top of
    // Alice's hand row, white; its eye hides it and shows it again (only the eye stays). STK-025: nothing reaches Bob
    // while the cast is open; the step arrow refuses with the card being cast shaking; Cancel drops it without a trace
    @Test fun theTargetBarSitsAboveTheHandWithItsEye() {
        kit.opening()
        kit.playLand()
        val spark = kit.atw(kit.hand("Storm Spark").first())
        val tip = kit.alice.device.session.tip
        render()
        compose.runOnIdle { assertTrue(toolkit.cast.start(spark), "${toolkit.error}") }
        compose.waitForIdle()
        assertTrue(toolkit.cast.targeting)
        val bar = compose.onNodeWithContentDescription("Choose targets").assertExists().fetchSemanticsNode().boundsInRoot
        val hand = kit.scene().items.first { it.kind == TableItemKind.HAND && it.owner == f.a }.bounds
        val density = compose.density.density
        assertEquals(hand.y, bar.bottom / density, 1f, "its bottom on the top of the hand row")
        assertEquals(0f, bar.left, 1f); assertEquals(412f, bar.right / density, 1f)
        compose.onNodeWithText("Storm Spark", substring = true).assertExists()
        compose.onNodeWithContentDescription("Hide bar").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Choose targets").assertDoesNotExist()
        compose.onNodeWithContentDescription("Cancel cast").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show bar").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Choose targets").assertExists()
        compose.onNodeWithText("Hold").assertDoesNotExist()
        // STK-025: the cast is Alice's alone; another action is refused without a message, the card being cast shaking
        compose.runOnIdle {
            assertTrue(kit.alice.take(kit.scenario.sim.now).none { it is GameAction.Submit }, "nothing went out")
            assertTrue(!toolkit.automatumStep(TableActionIntent.Next(kit.scope)))
            assertNull(toolkit.error)
            assertEquals(spark, toolkit.shaking?.id)
            assertEquals(CastController.BUSY, stepBlocked(kit.alice.play(), toolkit.cast.open, null), "the step arrow is greyed and says why")
        }
        compose.onNodeWithContentDescription("Cancel cast").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Choose targets").assertDoesNotExist()
        kit.settle()
        assertEquals(tip, kit.alice.device.session.tip, "Cancel left no trace")
        assertTrue(spark in kit.hand("Storm Spark").map(kit::atw))
    }

    // TBL-120, TBL-122: Alice's mana pool shows as a pill of mana symbols on her battlefield while it isn't empty
    @Test fun theManaPoolShowsAsAPill() {
        kit.opening()
        val mountain = kit.playLand()
        render()
        compose.onNodeWithContentDescription("Mana pool", substring = true).assertDoesNotExist()
        kit.tap(kit.item(mountain))
        kit.settle()
        redraw()
        compose.onNodeWithContentDescription("Mana pool: 1 {R}").assertExists()
    }

    // TBL-119, TRN-010: no virtual-tabletop controls: Table actions hold the timeline and the result (no dice, coins,
    // tokens or combat areas); card menus keep Inspect, Other side and the combat draft
    @Test fun noVirtualTabletopControls() {
        assertEquals(listOf("inspect", "otherSide", "Attack →"), CardRingPages.page(CardMenuLayout.CARD).actions)
        assertEquals(listOf("inspect", "Attack →", "Remove from combat"), CardRingPages.page(CardMenuLayout.COMBAT).actions)
        assertEquals(listOf("inspect", "otherSide"), CardRingPages.page(CardMenuLayout.PANEL).actions)
        kit.opening()
        render()
        compose.onNodeWithContentDescription("Table actions").performClick()
        compose.waitForIdle()
        for (entry in listOf("Timeline", "Suggest game end", "Concede")) compose.onNodeWithText(entry).assertExists()
        for (entry in listOf("Roll die…", "Flip coin", "Create token", "Create combat area"))
            compose.onNodeWithText(entry).assertDoesNotExist()
    }

    // SYS-003, TRN-059: a change of Alice's own settings goes out at once: no Send button, no confirmation
    @Test fun aSettingGoesOutAtOnce() {
        kit.opening()
        kit.alice.take(kit.scenario.sim.now)
        render()
        compose.onNodeWithContentDescription("Always assume resolves").performClick()
        compose.waitForIdle()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText("Send").assertDoesNotExist()
        val sent = kit.alice.take(kit.scenario.sim.now).filterIsInstance<GameAction.EngineSettings>()
        assertTrue(sent.single().settings.alwaysAssumeResolves, "$sent")
    }

    // STK-037, STK-029, TRN-063: with Bob's Storm Spark on the Stack the table is Alice's to act on: Resolve under the
    // Stack, no time limit and no Hold; Resolve lets it resolve
    @Test fun atAStopResolveSitsUnderTheStack() {
        kit.opening()
        // A Mountain and a Storm Spark in hand: Alice can respond, so the Spark is a point where she acts
        kit.playLand()
        kit.bobCastsSpark()
        kit.scenario.confirmStops[f.a] = false
        assertTrue(toolkit.automatumStep(TableActionIntent.End(kit.scope)), "End turn")
        kit.playOn("Alice with Bob's Storm Spark on the Stack") {
            kit.state().stack.isNotEmpty() && kit.state().priority.holder == kit.me && kit.routes.arrowShown()
        }
        render()
        compose.onNodeWithTag("stack-resolve").assertExists()
        compose.onNodeWithText("Hold").assertDoesNotExist()
        assertEquals(AutomatumStep.Resolve, AutomatumStep.of(kit.alice.play()))
        compose.onNodeWithTag("stack-resolve").performClick()
        compose.waitForIdle()
        kit.playOn("the Spark resolving") { kit.state().stack.isEmpty() }
        assertTrue(kit.scenario.cards(kit.state(), kit.other, kit.state().player(kit.other).graveyard, "Storm Spark").isNotEmpty(), "it resolved")
    }

    // CMB-035, CMB-028: a staged attacker's real card carries a violet "→ <target>" chip in the declare attackers step
    @Test fun aStagedAttackerCarriesItsTargetChip() {
        val cub = kit.toReadyCub()
        kit.toAttacks()
        kit.tap(kit.item(cub))
        kit.settle()
        render()
        val chips = compose.onAllNodesWithContentDescription("→ Bob").fetchSemanticsNodes()
        assertTrue(chips.isNotEmpty(), "the staged Cub's chip")
        val card = kit.item(cub).bounds
        for (chip in chips) assertTrue(chip.boundsInRoot.width / compose.density.density <= card.width + .5f,
            "no wider than the card")
    }
}
