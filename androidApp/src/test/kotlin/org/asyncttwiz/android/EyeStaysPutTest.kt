package org.asyncttwiz.android

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import arcanum.engine.decision.Decision
import org.asyncttwiz.android.EngineGameFixture.Choices
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * STK-176, STK-044 (owner-reported 2026-11-26: "the eye has begun moving again; its position should be fixed"): every Automatum
 * panel's eye sits at one spot of the screen, at the left of its bottom row: the same for every panel kind (the cast panel, a target
 * bar, a choice, the costs panel), while the panel's content changes (a cast panel becoming its target bar) and while it is hidden
 * (only the eye stays) and shown again, in portrait and in landscape.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class EyeStaysPutTest {
    @get:Rule val compose = createComposeRule()

    @After fun forget() {
        AutomatumBoards.reset()
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("card-appearance", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun kit(vararg cards: String) = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = cards.toList())))

    private fun render(kit: EngineTableKit, landscape: Boolean) {
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides kit.routes) {
                        Box(Modifier.fillMaxSize()) {
                            TableSurface(kit.f.game, kit.alice.play(), kit.f.a, landscape, Modifier.fillMaxSize(), connected = true,
                                onIntent = kit.toolkit::handle, toolkit = kit.toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { kit.routes.undo() }, controlsConnected = true))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** The eye's centre (dp; mdpi), shown or hidden. */
    private fun eye(): Offset = compose.onAllNodes(hasContentDescription("Hide ", substring = true) or hasContentDescription("Show ", substring = true),
        useUnmergedTree = true).fetchSemanticsNodes().filter { n ->
            n.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d: String ->
                d.startsWith("Hide ") || d.startsWith("Show ") } == true && n.boundsInRoot.width in 30f..50f
        }.single().boundsInRoot.center

    /** Where every eye must be: 24 dp from the left, 26 dp above the panels' bottom (the hand row's top; in landscape the bar's). */
    private fun spot(kit: EngineTableKit, landscape: Boolean): Offset {
        val bottom = if (landscape) 412f - TableControlsHeight
            else kit.scene().items.first { it.kind == TableItemKind.HAND && it.owner == kit.f.a }.bounds.y
        return Offset(24f, bottom - 26f)
    }

    private fun assertAt(expected: Offset, what: String) {
        val at = eye()
        assertTrue(kotlin.math.abs(at.x - expected.x) < 1f && kotlin.math.abs(at.y - expected.y) < 1f, "$what: the eye at $at, not $expected")
    }

    /** Hides the panel with its eye, checks the eye stayed, and shows it again. */
    private fun hideAndShow(expected: Offset, what: String) {
        compose.onNode(hasContentDescription("Hide ", substring = true) and hasClickAction(), useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        assertAt(expected, "$what hidden")
        compose.onNode(hasContentDescription("Show ", substring = true) and hasClickAction(), useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        assertAt(expected, "$what shown again")
    }

    private fun castAndTargets(landscape: Boolean) {
        val kit = kit(Choices.CHARM, "Mountain")
        kit.opening()
        assertTrue(kit.toolkit.cast.start(kit.atw(kit.hand(Choices.CHARM).first())), "the cast begins: ${kit.toolkit.error}")
        render(kit, landscape)
        val spot = spot(kit, landscape)
        assertAt(spot, "the cast panel")
        hideAndShow(spot, "the cast panel")
        compose.onNodeWithText("Cinder Charm deals 1 damage to any target.").performClick()
        compose.waitForIdle()
        assertTrue(kit.toolkit.cast.targeting, "targets are picked on the table")
        assertAt(spot, "the target bar")
        hideAndShow(spot, "the target bar")
    }

    // STK-176, STK-044, STK-047: the cast panel and the target bar it becomes
    @Test fun theCastPanelsAndTargetBarsEyeStaysPut() = castAndTargets(landscape = false)

    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun inLandscapeTheCastPanelsAndTargetBarsEyeStaysPut() = castAndTargets(landscape = true)

    private fun choice(landscape: Boolean) {
        val kit = kit(Choices.MUSING, "Mountain")
        kit.opening()
        assertTrue(kit.toolkit.cast.start(kit.atw(kit.hand(Choices.MUSING).first())), "the cast begins: ${kit.toolkit.error}")
        kit.playOn("Alice's you may", rounds = 40) {
            val d = kit.state().pending?.decision
            d is Decision.May && d.player == kit.me && kit.alice.device.status == GameDevice.Status.YourMove
        }
        render(kit, landscape)
        val spot = spot(kit, landscape)
        assertAt(spot, "the choice")
        hideAndShow(spot, "the choice")
    }

    // STK-176, STK-030: a choice while something resolves
    @Test fun aChoicesEyeStaysPut() = choice(landscape = false)

    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun inLandscapeAChoicesEyeStaysPut() = choice(landscape = true)

    private fun costs(landscape: Boolean) {
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
        render(kit, landscape)
        val spot = spot(kit, landscape)
        assertAt(spot, "the costs panel")
        hideAndShow(spot, "the costs panel")
    }

    // STK-176, CMB-098: the costs panel of a declaration
    @Test fun theCostsPanelsEyeStaysPut() = costs(landscape = false)

    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun inLandscapeTheCostsPanelsEyeStaysPut() = costs(landscape = true)
}
