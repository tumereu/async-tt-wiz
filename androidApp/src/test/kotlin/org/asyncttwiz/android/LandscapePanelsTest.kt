package org.asyncttwiz.android

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import arcanum.engine.decision.Decision
import org.asyncttwiz.android.EngineGameFixture.Choices
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * STK-044 (owner, 2026-11-26): in landscape every Automatum panel (the cast panel, a target bar, a choice, the costs panel; the reveal
 * panel in [RevealPanelTest]) stands at the left, at most 420 dp wide, its bottom on the top of the bottom bar, so nothing of the bar
 * (life, piles, the hand's fan) is covered; large hand cards standing over the battlefield (TBL-238) are drawn in front of it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w892dp-h412dp-land-mdpi")
class LandscapePanelsTest {
    @get:Rule val compose = createComposeRule()

    @After fun forget() {
        AutomatumBoards.reset()
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("card-appearance", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun kit(vararg cards: String) = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = cards.toList())))

    private fun render(kit: EngineTableKit) {
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides kit.routes) {
                        Box(Modifier.fillMaxSize()) {
                            TableSurface(kit.f.game, kit.alice.play(), kit.f.a, true, Modifier.fillMaxSize(), connected = true,
                                onIntent = kit.toolkit::handle, toolkit = kit.toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { kit.routes.undo() }, controlsConnected = true))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** The panel described as [panel] stands on the bottom bar, at the left, at most 420 dp wide ([narrow]; the cast panel keeps the
     * table's whole width). */
    private fun onTheBar(panel: String, narrow: Boolean = true) {
        val box = compose.onNodeWithContentDescription(panel).fetchSemanticsNode().boundsInRoot
        val barTop = 412f - TableControlsHeight
        assertTrue(kotlin.math.abs(box.bottom - barTop) < 1f, "$panel's bottom on the bar's top ($barTop): $box")
        assertTrue(box.left < 1f && (!narrow || box.width <= 421f), "$panel at the left, at most 420 dp wide: $box")
    }

    // STK-044, STK-023: the cast panel of Cinder Charm (a mode to choose)
    @Test fun theCastPanelStandsOnTheBar() {
        val kit = kit(Choices.CHARM, "Mountain")
        kit.opening()
        assertTrue(kit.toolkit.cast.start(kit.atw(kit.hand(Choices.CHARM).first())), "the cast begins: ${kit.toolkit.error}")
        render(kit)
        onTheBar("Cast panel", narrow = false)
        compose.onRoot().captureShot("$SCREENSHOTS/landscape-panels/cast-panel.png",
            "In landscape Alice cast Cinder Charm, which asks for a mode: the cast panel stands at the left on top of the bottom bar, " +
                "her life, piles and the hand's fan all in view under it.")
    }

    // STK-044, STK-047: the target bar of the Charm's damage mode
    @Test fun theTargetBarStandsOnTheBar() {
        val kit = kit(Choices.CHARM, "Mountain")
        kit.opening()
        assertTrue(kit.toolkit.cast.start(kit.atw(kit.hand(Choices.CHARM).first())))
        render(kit)
        compose.onNodeWithText("Cinder Charm deals 1 damage to any target.").performClick()
        compose.waitForIdle()
        assertTrue(kit.toolkit.cast.targeting, "targets are picked on the table")
        onTheBar("Choose targets")
        compose.onRoot().captureShot("$SCREENSHOTS/landscape-panels/target-bar.png",
            "In landscape Alice chose Cinder Charm's damage mode: the target bar \"Choose any target\" stands at the left on top of " +
                "the bottom bar, her life square and piles in view under it.")
    }

    /** Alice casts her free Ember Musing and plays on until its "you may draw" is hers to answer. */
    private fun musingMay(): EngineTableKit {
        val kit = kit(Choices.MUSING, "Mountain")
        kit.opening()
        assertTrue(kit.toolkit.cast.start(kit.atw(kit.hand(Choices.MUSING).first())), "the cast begins: ${kit.toolkit.error}")
        kit.playOn("Alice's you may", rounds = 40) {
            val d = kit.state().pending?.decision
            d is Decision.May && d.player == kit.me && kit.alice.device.status == GameDevice.Status.YourMove
        }
        return kit
    }

    // STK-044, STK-181: a choice: the Musing's "you may draw a card"
    @Test fun aChoiceStandsOnTheBar() {
        val kit = musingMay()
        render(kit)
        onTheBar("Automatum choice")
        compose.onRoot().captureShot("$SCREENSHOTS/landscape-panels/choice.png",
            "In landscape Alice's Ember Musing asks whether she draws a card: the choice panel stands at the left on top of the " +
                "bottom bar, nothing of the bar covered.")
    }

    // STK-044, TBL-238 (owner 2026-11-26): with hand cards 2.5 times the size, standing over the battlefield, they are drawn in front
    // of the panel, which still stands on the bar
    @Test fun largeHandCardsStandInFrontOfThePanel() {
        val preferences = CardAppearancePreferences(AppPlatform.current)
        preferences.customizeAreas(true)
        preferences.setSize(2.5f, CardArea.HAND)
        val kit = musingMay()
        render(kit)
        onTheBar("Automatum choice")
        compose.onRoot().captureShot("$SCREENSHOTS/landscape-panels/large-hand-cards.png",
            "In landscape with hand cards 2.5 times the size, Alice's Ember Musing asks whether she draws a card: the choice panel " +
                "stands at the left on top of the bottom bar, and her large hand cards standing over the battlefield are drawn in " +
                "front of it, dimmed as the hand is while a choice is open.")
    }

    // STK-044, CMB-098: the costs panel of an Ember Charger's exert
    @Test fun theCostsPanelStandsOnTheBar() {
        val kit = kit(Choices.CHARGER, "Mountain")
        kit.opening()
        val card = kit.atw(kit.hand(Choices.CHARGER).first())
        assertTrue(kit.toolkit.cast.start(card), "the Charger is cast: ${kit.toolkit.error}")
        kit.playOn("the Charger on the battlefield") { card in kit.field(Choices.CHARGER).map(kit::atw) && kit.aliceMain() }
        kit.toAttacks()
        kit.toolkit.combat.show(kit.alice.play(), AutomatumBoards.combat(kit.alice.play()))
        kit.tap(kit.item(card))
        kit.settle()
        kit.toolkit.combat.show(kit.alice.play(), AutomatumBoards.combat(kit.alice.play()))
        assertTrue(kit.toolkit.automatumStep(TableActionIntent.Next(TableActionScope(kit.f.game.id, kit.alice.play().playId))), "the main button")
        assertTrue(kit.toolkit.combat.costPanelOpen(kit.alice.play()), "the costs panel")
        render(kit)
        onTheBar(COSTS_PANEL)
        compose.onNode(hasText("Declare attackers") and isHeading(), useUnmergedTree = true).assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/landscape-panels/costs-panel.png",
            "In landscape Alice staged her Ember Charger and pressed Attack (1): the costs panel \"Declare attackers\" stands at the " +
                "left on top of the bottom bar, the combat band in view beside it.")
    }
}
