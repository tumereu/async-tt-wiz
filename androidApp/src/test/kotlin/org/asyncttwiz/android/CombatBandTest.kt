package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import arcanum.engine.Engine
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
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The combat band (owner, 2026-11-05): the thin strip that pulses while attackers are declared with none staged, opened by
 * a tap or by staging the first attacker; the band between the battlefields holding the lanes; the creatures that may
 * attack glowing red-orange; the defender's blocks with their arrows; the attacker placing blocks to assume on the lanes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CombatBandTest {
    @get:Rule val compose = createComposeRule()
    private val k = EngineTableKit()

    @After fun forget() = AutomatumBoards.reset()

    /** [viewer]'s table as [EngineTableScreen] draws it (the blocks placed to assume on the attacker's lanes). */
    private fun render(viewer: String = k.f.a, landscape: Boolean = false) {
        val game = if (viewer == k.f.a) k.alice else k.bob
        val routes = if (viewer == k.f.a) k.routes else k.bobRoutes
        val toolkit = if (viewer == k.f.a) k.toolkit else k.bobToolkit
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides routes) {
                        Box(Modifier.fillMaxSize()) {
                            val play = game.play().let { b -> routes.assuming()?.board(b) ?: b }
                            TableSurface(k.f.game, play, viewer, landscape, Modifier.fillMaxSize(), connected = true,
                                onIntent = toolkit::handle, toolkit = toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { routes.undo() }, controlsConnected = true,
                                    waitingLine = routes.waitingLine(play)))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Bob's Main 1: a Mountain, then an Ember Cub, so he has a creature to block with. */
    private fun bobCastsACub() {
        k.bobMain = { s ->
            val land = k.scenario.cards(s, k.other, s.player(k.other).hand, "Mountain").firstOrNull()
            val cub = k.scenario.cards(s, k.other, s.player(k.other).hand, "Ember Cub").firstOrNull()
            when {
                (s.turn.landsPlayed[k.other] ?: 0) == 0 && land != null -> { k.bob.send(listOf(Input.PlayLand(k.other, land))); true }
                cub != null && k.scenario.cards(s, k.other, s.battlefield, "Ember Cub").isEmpty() &&
                    k.scenario.cards(s, k.other, s.battlefield, "Mountain").any { s.obj(it)?.tapped == false } -> {
                    k.bob.send(k.scenario.cast(s, k.other, Input.Cast(k.other, cub))); true
                }
                else -> false
            }
        }
    }

    /** Alice declaring attackers with a ready Cub (Bob has a Cub too); the combat worked out. Returns her Cub. */
    private fun aliceDeclaring(): String {
        bobCastsACub()
        val attacker = k.toReadyCub()
        k.bobMain = null
        k.toAttacks()
        k.toolkit.combat.show(k.alice.play(), AutomatumBoards.combat(k.alice.play()))
        return attacker
    }

    // CMB-001, CMB-028 (owner, 2026-11-05): with no attacker staged the band is a thin strip that pulses; the creatures
    // that may attack glow red-orange; a tap on the strip opens the band with its empty lanes, pushing the battlefields
    // apart
    @Test fun theStripPulsesUntilATapOpensTheBand() {
        val attacker = aliceDeclaring()
        render()
        assertNull(k.scene().combatPanel)
        val strip = assertNotNull(k.scene().combatStrip)
        assertTrue(attacker in k.toolkit.combat.attackable(k.alice.play(), k.f.a), "the Cub may attack")
        captureShot("$SCREENSHOTS/combat-band/strip.png",
            "Alice declares attackers with nothing staged: the band between the battlefields is a thin red-orange strip that pulses, and her Ember Cub, which may attack, glows red-orange; the main button reads No attack.")
        val fieldBefore = k.scene().fields.last().gridBounds.y
        compose.onNodeWithTag("combat-strip").assert(hasContentDescription("Open the combat area")).performClick()
        compose.waitForIdle()
        val panel = assertNotNull(k.scene().combatPanel, "the band opened")
        assertNull(k.scene().combatStrip)
        assertTrue(k.scene().fields.last().gridBounds.y > fieldBefore + strip.height, "her battlefield pushed down")
        assertTrue(panel.bottom <= k.scene().fields.last().gridBounds.y)
        assertEquals(null, k.alice.play().board.combat, "opening the band stages nothing")
        captureShot("$SCREENSHOTS/combat-band/attack-glow.png",
            "Alice tapped the strip open before staging anything: the band between the battlefields shows the empty lane aimed at Bob, and her Ember Cub, which may attack, glows red-orange from behind.")
        // A tap on the Cub stages it in the open band
        k.tap(k.item(attacker))
        k.settle()
        assertEquals(1, k.alice.play().board.combat?.pairs?.size)
    }

    // CMB-001 (owner, 2026-11-05): staging the first attacker opens the band at once, holding its copy in the lane
    @Test fun stagingTheFirstAttackerOpensTheBand() {
        val attacker = aliceDeclaring()
        assertNotNull(k.scene().combatStrip)
        k.tap(k.item(attacker))
        k.settle()
        assertNull(k.scene().combatStrip)
        val panel = assertNotNull(k.scene().combatPanel)
        val copy = k.scene().items.single { it.viewport == "combat" && it.sourceId == attacker }
        assertTrue(panel.contains(copy.bounds.center))
        // CMB-042: the band's cards are no smaller than the battlefield's at the same zoom
        assertTrue(copy.bounds.width >= k.item(attacker).bounds.width)
        render()
        compose.onNodeWithTag("main-button").assert(hasContentDescription("Attack (1)"))
        captureShot("$SCREENSHOTS/combat-band/staged.png",
            "Alice tapped her ready Ember Cub: the band opened between the battlefields, pushing them apart, with the Cub's copy in the lane aimed at Bob and the main button reading Attack (1).")
    }

    // CMB-001, CMB-007 (owner, 2026-11-05): the open band in landscape
    @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    @Test fun theOpenBandInLandscape() {
        val attacker = aliceDeclaring()
        k.tap(k.item(attacker))
        k.settle()
        render(landscape = true)
        captureShot("$SCREENSHOTS/combat-band/staged-landscape.png",
            "In landscape Alice tapped her ready Ember Cub: the band between the battlefields holds its copy in the lane aimed at Bob, zoomed with the battlefields, and the main button reads Attack (1).")
    }

    // CMB-031, CMB-034 (owner, 2026-11-05): Bob arranges his blocks on the band's lanes: his Cub blocks Alice's attacker,
    // an arrow from blocker to attacker
    @Test fun theDefenderArrangesBlocksOnTheLanes() {
        val attacker = aliceDeclaring()
        val blocker = k.atw(k.field("Ember Cub", k.other).single())
        k.tap(k.item(attacker))
        k.settle()
        assertTrue(k.toolkit.automatumStep(TableActionIntent.Next(k.scope)), "Attack (1)")
        k.playOn("Bob declaring blockers", bobActs = false) {
            Engine.options(k.bob.device.model.engine, k.other).any { it is Input.DeclareBlockers }
        }
        k.bobToolkit.combat.show(k.bob.play(), AutomatumBoards.combat(k.bob.play()))
        k.tap(k.item(blocker, viewer = k.f.b), k.bobToolkit, k.f.b)
        k.settle(bobActs = false)
        k.tap(k.scene(k.f.b).items.first { it.viewport == "combat" && it.sourceId == attacker }, k.bobToolkit, k.f.b)
        k.settle(bobActs = false)
        assertEquals(listOf(blocker to attacker), k.bob.device.model.table.blockDraft.orEmpty().map { k.atw(it.blocker) to k.atw(it.attacker) })
        render(k.f.b)
        captureShot("$SCREENSHOTS/combat-band/blocks.png",
            "Bob declares blockers: in the band between the battlefields, mirrored for him, his Ember Cub's copy blocks Alice's attacking Cub with an arrow from blocker to attacker, the damage preview on both, and the main button reads Block (1).")
    }

    // CMB-040, CMB-049 (owner, 2026-11-05): Bob is away when Alice attacks; while she waits for his blocks her table is at
    // once in assume mode on the lanes: she places his Cub as a blocker by taps, as he would, and the main button assumes
    // exactly that ("Assume no blockers" before, "Assume blockers" after); nothing is assumed before she presses it
    @Test fun theAttackerPlacesBlocksToAssumeOnTheLanes() {
        val attacker = aliceDeclaring()
        val blocker = k.atw(k.field("Ember Cub", k.other).single())
        k.scenario.online[k.f.b] = false
        k.alice.tick(k.scenario.sim.now + 120_000)
        k.tap(k.item(attacker))
        k.settle()
        assertTrue(k.toolkit.automatumStep(TableActionIntent.Next(k.scope)), "Attack (1)")
        k.settle()
        k.alice.tick(k.scenario.sim.now + 121_000)
        // Bob's pause after her attack, then his blockers: a wait with Assume on the main button
        if ((k.alice.device.status as? GameDevice.Status.Waiting)?.offer == GameDevice.Offer.AssumeResolves) {
            k.alice.assume(); k.alice.tick(k.scenario.sim.now + 122_000)
        }
        val waiting = assertIs<GameDevice.Status.Waiting>(k.alice.device.status)
        assertEquals(GameDevice.Offer.AssumeBlockers, waiting.offer)
        k.alice.notes.clear()
        val placed = assertNotNull(k.routes.assuming(), "assume mode at once")
        render()
        compose.onNodeWithTag("engine-wait-action").assert(hasContentDescription("Assume no blockers"))
        k.toolkit.combat.show(placed.board(k.alice.play()), AutomatumBoards.combat(placed.board(k.alice.play())))
        k.tap(k.item(blocker))
        assertEquals(blocker, k.toolkit.combat.selected, "Bob's Cub is the blocker to place")
        k.tap(k.item(attacker))
        assertEquals(mapOf(blocker to placed.blocks.getValue(blocker)), placed.blocks)
        assertTrue(k.alice.device.session.pending.none { it.assumed?.input is Input.DeclareBlockers }, "nothing assumed yet")
        compose.waitForIdle()
        compose.onNodeWithTag("engine-wait-action").assert(hasContentDescription("Assume blockers"))
        compose.onNodeWithContentDescription("Clear").assertExists()
        captureShot("$SCREENSHOTS/combat-band/assume.png",
            "Bob is away when Alice attacks with her Ember Cub: waiting for his blocks, her table is in assume mode on the band's lanes; she tapped his Cub and then her attacker, so his Cub's copy blocks it with an arrow, Clear is the quick action and the main button reads Assume blockers.")
        val expected = listOf(Block(k.state().battlefield.single { (it as? ObjectId.Card)?.card?.value == blocker },
            k.state().battlefield.single { (it as? ObjectId.Card)?.card?.value == attacker }))
        compose.onNodeWithTag("engine-wait-action").performClick()
        compose.runOnIdle {
            val assumed = k.alice.device.session.pending.firstNotNullOf { it.assumed?.input as? Input.DeclareBlockers }
            assertEquals(expected, assumed.blocks)
        }
    }
}
