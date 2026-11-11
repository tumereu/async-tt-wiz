package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.api.TurnStep
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The life an attacker's lifelink gains on the combat band (owner playtest 2026-11-11): Alice attacks Bob with an Ember
 * Leech (2/2, lifelink, haste) cast in her first turn, he doesn't block, and the damage's hold shows his total on his chip
 * and her gain as the life-gain heart on the band's edge by her battlefield.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CombatLifeGainTest {
    @get:Rule val compose = createComposeRule()
    private val k = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(EngineGameFixture.Choices.LEECH, "Mountain"))))

    @After fun forget() = AutomatumBoards.reset()

    private fun show(viewer: String, board: TableBoard) {
        val routes = if (viewer == k.f.a) k.routes else k.bobRoutes
        val toolkit = if (viewer == k.f.a) k.toolkit else k.bobToolkit
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides routes) {
                        Box(Modifier.fillMaxSize()) {
                            TableSurface(k.f.game, board, viewer, false, Modifier.fillMaxSize(), connected = true,
                                onIntent = toolkit::handle, toolkit = toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { routes.undo() }, controlsConnected = true,
                                    waitingLine = routes.waitingLine(board)))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    // CMB-050 (owner playtest 2026-11-11; CR 510.1, CR 702.15b): the attacker's lifelink gain shows on the band's edge facing
    // her battlefield with the life-gain heart, and as "+2" at her life square; Bob's chip shows his total; nothing per lane
    @Test fun anAttackersLifelinkGainShowsOnTheBand() {
        k.opening()
        val leech = k.atw(k.hand(EngineGameFixture.Choices.LEECH).first())
        assertTrue(k.toolkit.cast.start(leech), "the cast begins: ${k.toolkit.error}")
        k.playOn("the Leech on the battlefield") { leech in k.field(EngineGameFixture.Choices.LEECH).map(k::atw) && k.aliceMain() }
        k.toAttacks()
        k.toolkit.combat.show(k.alice.play(), AutomatumBoards.combat(k.alice.play()))
        assertTrue(k.toolkit.stage(k.alice.play(), listOf(leech), null), "the Leech staged")
        k.settle()
        assertTrue(k.toolkit.automatumStep(TableActionIntent.Next(k.scope)), "Attack (1)")
        // Her own attack runs on to the damage on her phone (Bob's no blocks and passes assumed): its steps hold the damage's
        // hold board (HIS-113)
        val aliceDealt = k.alice.transition?.steps.orEmpty().lastOrNull { it.damageDealt }
        k.playOn("the damage on both phones") { k.state().player(k.other).life == 18 && k.bob.device.model.engine.player(k.other).life == 18 }
        assertEquals(22, k.state().player(k.me).life, "the Leech's lifelink")
        val hold = assertNotNull(aliceDealt ?: k.scenario.shown[k.f.a].orEmpty().flatMap { it.steps }.lastOrNull { it.damageDealt },
            "the damage's hold board on Alice's phone")
        val scene = TablePresentation.scene(k.f.game, hold, k.f.a, 412f, 892f, false)
        assertEquals(mapOf<String?, Int>(null to 2), scene.combatDealt?.targets)
        assertEquals(mapOf(k.f.a to 2), scene.combatDealt?.gained)
        // Her battlefield is the lower one: the band's lower edge
        assertEquals(scene.fields.last().gridBounds.y, assertNotNull(scene.combatGainEdge), .5f)
        show(k.f.a, hold)
        compose.onAllNodes(hasContentDescription("Gained 2 life with the combat damage, on the band"), useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodes(hasContentDescription("Gained 2 life with the combat damage"), useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodes(hasContentDescription("took 2 combat damage", substring = true), useUnmergedTree = true).assertCountEquals(1)
        captureShot("$SCREENSHOTS/combat-band/lifelink-gain-alice.png",
            "Alice's phone, the damage's hold after her Ember Leech (lifelink) attacked Bob unblocked: his chip carries a red splash with 2, the green heart with +2 on the band's edge by her battlefield shows the life she gained, and her life square has a green +2.")
        // Mirrored for Bob (the same board from his seat): her battlefield is the upper one
        val bob = TablePresentation.scene(k.f.game, hold, k.f.b, 412f, 892f, false)
        assertEquals(bob.fields.first().gridBounds.bottom, assertNotNull(bob.combatGainEdge, "Bob's band open on the damage"), .5f)
    }
}
