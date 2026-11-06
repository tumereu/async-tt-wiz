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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasStateDescription
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
 * The combat band (owner playtest 2026-11-06): always between the battlefields, collapsed to the row of target chips while
 * attackers are declared with none staged, opened by staging the first attacker, its lanes under the chips; the creatures
 * that may attack glowing red-orange (more under a finger); the defender's blocks with their arrows; the attacker placing
 * blocks to assume on the lanes.
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

    /** The defending player's name, as their chip says it. */
    private fun bobName() = listOf(k.f.game.inviter, k.f.game.recipient).single { it.accountId == k.f.b }.username

    // CMB-001, CMB-013, CMB-028 (owner playtest 2026-11-06): while Alice declares attackers with none staged the band
    // stays collapsed at its chip row and shows a chip for Bob (crossed swords, his round avatar, his name), the active
    // target filled violet; the creatures that may attack glow red-orange; a tap on the chip keeps its target active and
    // stages nothing
    @Test fun theCollapsedBandShowsTheTargetChips() {
        val attacker = aliceDeclaring()
        render()
        val scene = k.scene()
        assertNull(scene.combatPanel, "collapsed")
        val band = assertNotNull(scene.combatBand)
        assertEquals(CombatStackPresentation.CHIP_ROW * scene.camera.zoom, band.height, .001f)
        val chip = scene.combatLabels.single()
        assertEquals(null, chip.cardId)
        assertEquals(bobName(), chip.label)
        assertTrue(band.y <= chip.chip.y && band.bottom >= chip.chip.bottom)
        assertTrue(attacker in k.toolkit.combat.attackable(k.alice.play(), k.f.a), "the Cub may attack")
        compose.onNodeWithTag("combat-target-chip").assert(hasContentDescription("Attack ${bobName()}"))
            .assert(hasStateDescription("active"))
        captureShot("$SCREENSHOTS/combat-band/chips.png",
            "Alice declares attackers with nothing staged: the band between the battlefields stays collapsed at its chip row and shows Bob's target chip (crossed swords, his avatar, his name) filled violet as the active target; her Ember Cub, which may attack, glows red-orange and the main button reads No attack.")
        compose.onNodeWithTag("combat-target-chip").performClick()
        compose.waitForIdle()
        assertEquals(null, k.toolkit.combat.activeTarget(k.alice.play()))
        assertEquals(null, k.alice.play().board.combat, "a chip stages nothing")
        assertNull(k.scene().combatPanel)
    }

    // CMB-013 (owner playtest 2026-11-06): holding a chip shows the target's full name over it and highlights the
    // player's life square while held
    @Test fun holdingAChipShowsTheFullNameAndHighlightsTheTarget() {
        aliceDeclaring()
        render()
        compose.onNodeWithTag("combat-target-chip").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription("Target: ${bobName()}").assertExists()
        captureShot("$SCREENSHOTS/combat-band/chip-held.png",
            "Alice holds Bob's target chip while declaring attackers: his full name shows over the chip and his life square is framed violet while she holds it.")
        compose.onNodeWithTag("combat-target-chip").performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Target: ${bobName()}").assertDoesNotExist()
    }

    // TBL-117, CMB-028 (owner playtest 2026-11-06): while a finger is down on a glowing card its glow grows a little and
    // brightens
    @Test fun aFingerOnAGlowingCardGrowsItsGlow() {
        val attacker = aliceDeclaring()
        render()
        val card = k.item(attacker)
        val table = compose.onNodeWithContentDescription("Shared table")
        // The table runs a frame clock while a finger is down: the test's clock is moved by hand meanwhile
        compose.mainClock.autoAdvance = false
        table.performTouchInput { down(androidx.compose.ui.geometry.Offset(card.bounds.center.x, card.bounds.center.y)) }
        compose.mainClock.advanceTimeBy(150)
        captureShot("$SCREENSHOTS/combat-band/pressed.png",
            "Alice's finger is down on her Ember Cub while she declares attackers: its red-orange glow has grown a little and brightened, so it feels pressable.")
        table.performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
    }

    // CMB-001, CMB-042 (owner playtest 2026-11-06): staging the first attacker opens the band by itself, its copy in the
    // lane under Bob's chip, exactly the battlefield cards' size, Bob's life under his chip
    @Test fun stagingTheFirstAttackerOpensTheBand() {
        val attacker = aliceDeclaring()
        assertNull(k.scene().combatPanel)
        k.tap(k.item(attacker))
        k.settle()
        val panel = assertNotNull(k.scene().combatPanel)
        val copy = k.scene().items.single { it.viewport == "combat" && it.sourceId == attacker }
        assertTrue(panel.contains(copy.bounds.center))
        val real = k.item(attacker)
        assertEquals(minOf(real.bounds.width, real.bounds.height), copy.bounds.width, .001f)
        assertEquals(maxOf(real.bounds.width, real.bounds.height), copy.bounds.height, .001f)
        val lane = k.scene().combatLabels.single()
        assertTrue(lane.chip.bottom < copy.bounds.y && lane.valueBounds!!.y >= lane.chip.bottom - .001f)
        render()
        compose.onNodeWithTag("main-button").assert(hasContentDescription("Attack (1)"))
        compose.onNodeWithContentDescription("${bobName()}: ${lane.value}").assertExists()
        captureShot("$SCREENSHOTS/combat-band/staged.png",
            "Alice tapped her ready Ember Cub: the band opened by itself between the battlefields, pushing them apart, with the Cub's copy at battlefield size in the lane under Bob's chip, his life under the chip, and the main button reading Attack (1).")
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
        // HIS-096 (owner, 2026-11-06): Bob's pause after her attack is assumed at once; his blockers are a wait with Assume
        // on the main button
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
