package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.asyncttwiz.android.EngineGameFixture.Choices
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TBL-137 (owner notes 2026-11-14): an uncrewed Vehicle shows "Crew N" in its bottom text, before its P/T, where it fits. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class VehicleCrewTest {
    @get:Rule val compose = createComposeRule()

    @After fun forget() = AutomatumBoards.reset()

    // TBL-137 (owner notes 2026-11-14), CR 301.7a, 208.3: Alice's Ember Cart (crew 2, 3/3) on the battlefield, not crewed: its
    // bottom text reads "Crew 2" before its printed 3/3; her Ash Pup, a creature, shows its P/T alone
    @Test fun anUncrewedVehicleShowsItsCrewBeforeItsPowerAndToughness() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(Choices.CART, Choices.PUP))))
        kit.opening()
        for (name in listOf(Choices.CART, Choices.PUP)) {
            val card = kit.atw(kit.hand(name).first())
            assertTrue(kit.toolkit.cast.start(card), "$name is cast: ${kit.toolkit.error}")
            kit.playOn("$name on the battlefield") { card in kit.field(name).map(kit::atw) && kit.aliceMain() }
        }
        // The table's scene as drawn, with Automatum's values
        val play = kit.alice.play()
        val scene = TablePresentation.scene(kit.f.game, play, kit.f.a, 412f, 892f, false, kit.toolkit.layoutState.value,
            handRow = HandRow.cards(play, kit.f.a), computed = AutomatumBoards.compute(play, kit.f.a))
        fun item(name: String) = scene.items.first { it.kind == TableItemKind.CARD && it.sourceId == kit.atw(kit.field(name).single()) &&
            it.zone == org.asyncttwiz.protocol.PlayZone.BATTLEFIELD && it.viewport != "combat" && !it.projection }
        assertEquals("Crew 2", item(Choices.CART).crew)
        assertEquals("3/3", item(Choices.CART).corner, "the P/T it has once crewed")
        assertNull(item(Choices.PUP).crew, "a creature has no crew text")
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides kit.routes) {
                        Box(Modifier.fillMaxSize()) {
                            TableSurface(kit.f.game, kit.alice.play(), kit.f.a, false, Modifier.fillMaxSize(), connected = true,
                                onIntent = kit.toolkit::handle, toolkit = kit.toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { kit.routes.undo() }, controlsConnected = true))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        captureShot("$SCREENSHOTS/battlefield/vehicle-crew.png",
            "Alice cast her Ember Cart (a Vehicle with crew 2, 3/3) and an Ash Pup: the uncrewed Cart's bottom text reads \"Crew 2\" " +
                "on a short line just above the 3/3 it has once crewed, while the Pup shows its 1/1 alone.")
    }
}
