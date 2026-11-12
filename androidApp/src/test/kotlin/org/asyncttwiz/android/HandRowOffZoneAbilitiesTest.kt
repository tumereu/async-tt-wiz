package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import arcanum.engine.Engine
import arcanum.engine.input.Input
import org.asyncttwiz.android.EngineGameFixture.Choices
import org.asyncttwiz.protocol.PlayZone
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ZON-079, ZON-080, TBL-117, TBL-128 (owner, 2026-11-12) on Alice's table: a card with an ability she may use from her graveyard
 * (unearth; "{R}: Return this card from your graveyard to your hand") is in her hand row as a card she may cast is, and behaves
 * as a hand card: it glows while one of its uses can be done now, a tap raises it with its chips (one per cast way and ability,
 * greyed when not usable now) and never shakes while it glows.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class HandRowOffZoneAbilitiesTest {
    @get:Rule val compose = createComposeRule()
    private val kit by lazy {
        EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(Choices.GHOUL, Choices.REPRISE, "Mountain"))))
    }
    private val toolkit get() = kit.toolkit

    @After fun forget() = AutomatumBoards.reset()

    private fun render() {
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides kit.routes) {
                        Box(Modifier.fillMaxSize()) {
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

    private fun rowItem(card: String): TableItem = kit.scene().items.single { it.id == "handRow/$card" }

    private fun graveyard(): List<String> = kit.state().player(kit.me).graveyard.map(kit::atw)

    // ZON-079, CR 702.84a: Alice casts her free Ember Ghoul and sacrifices it; in her graveyard it is in her hand row, glowing
    // with her untapped Mountain (TBL-117); a tap raises it with its one chip, "Unearth {R}", and the chip unearths it
    @Test fun anUnearthCardIsUsedFromTheHandRow() {
        kit.opening()
        kit.playLand()
        val ghoul = kit.atw(kit.hand(Choices.GHOUL).first())
        assertTrue(toolkit.cast.start(ghoul), "the Ghoul is cast: ${toolkit.error}")
        kit.playOn("the Ghoul on the battlefield") { ghoul in kit.field(Choices.GHOUL).map(kit::atw) && kit.aliceMain() }
        val sacrifice = Engine.options(kit.state(), kit.me).filterIsInstance<Input.Activate>().single { kit.atw(it.source) == ghoul }
        assertTrue(toolkit.cast.begin(ghoul, sacrifice), "the Ghoul is sacrificed: ${toolkit.error}")
        kit.playOn("the Ghoul in the graveyard") { ghoul in graveyard() && kit.aliceMain() }
        assertEquals(PlayZone.GRAVEYARD, rowItem(ghoul).handZone)
        assertTrue(ghoul in toolkit.playable(kit.alice.play()), "it glows: unearth can be used now")
        kit.tap(rowItem(ghoul))
        assertEquals(ghoul, toolkit.hand.raised, "raised as a hand card")
        assertNull(toolkit.shaking, "no shake")
        assertEquals(listOf(Triple("Unearth", "{R}", true)), assertNotNull(toolkit.hand.chips).chips.map { Triple(it.name, it.cost, it.affordable) })
        assertTrue(toolkit.hand.chip(0), "the Unearth chip")
        kit.playOn("the Ghoul unearthed") { ghoul in kit.field(Choices.GHOUL).map(kit::atw) && kit.aliceMain() }
        assertNull(kit.scene().items.firstOrNull { it.id == "handRow/$ghoul" }, "on the battlefield: out of the hand row")
    }

    // ZON-079, ZON-080, TBL-128, CR 702.34a: Alice's free Ember Reprise in her graveyard, one Mountain untapped: its flashback
    // {R}{R} can't be paid but its "{R}: Return this card from your graveyard to your hand" can; it glows, a tap raises it
    // without a shake, with a greyed Flashback chip and a live ability chip, which returns it to her hand
    @Test fun aCardItCantBeCastButWhoseAbilityCanBeUsedGlowsAndRaises() {
        kit.opening()
        val mountain = kit.playLand()
        val reprise = kit.atw(kit.hand(Choices.REPRISE).first())
        assertTrue(toolkit.cast.start(reprise), "the Reprise is cast: ${toolkit.error}")
        kit.playOn("the Reprise in the graveyard") { reprise in graveyard() && kit.aliceMain() }
        assertEquals(PlayZone.GRAVEYARD, rowItem(reprise).handZone)
        assertTrue(reprise in toolkit.playable(kit.alice.play()), "it glows: its ability can be used now")
        kit.tap(rowItem(reprise))
        assertEquals(reprise, toolkit.hand.raised, "raised as a hand card")
        assertNull(toolkit.shaking, "no shake")
        assertEquals(listOf(Triple("Flashback", "{R}{R}", false), Triple("Activate", "{R}", true)),
            assertNotNull(toolkit.hand.chips).chips.map { Triple(it.name, it.cost, it.affordable) })
        render()
        compose.onNodeWithContentDescription("Ember Reprise, owner Alice, untapped, from graveyard").assertExists()
        captureShot("$SCREENSHOTS/hand/hand-row-ability-chips.png",
            "Alice's Ember Reprise lies in her graveyard with one Mountain untapped: tapped in her hand row it is raised with a greyed Flashback {R}{R} chip and a live chip for its {R} ability that returns it to her hand.")
        assertTrue(toolkit.hand.chip(1), "the ability's chip")
        kit.playOn("the Reprise back in her hand") { kit.hand(Choices.REPRISE).any { kit.atw(it) == reprise } && kit.aliceMain() }
        assertTrue(kit.alice.play().board.cards.single { it.id == mountain }.tapped, "Autotap tapped the Mountain")
    }

    // ZON-079, TBL-128: with no Mountain the Ghoul in her graveyard is still in her hand row, dull, and a tap only shakes it
    @Test fun withNothingUsableNowItShowsDullAndShakes() {
        kit.opening()
        val ghoul = kit.atw(kit.hand(Choices.GHOUL).first())
        assertTrue(toolkit.cast.start(ghoul), "the Ghoul is cast: ${toolkit.error}")
        kit.playOn("the Ghoul on the battlefield") { ghoul in kit.field(Choices.GHOUL).map(kit::atw) && kit.aliceMain() }
        val sacrifice = Engine.options(kit.state(), kit.me).filterIsInstance<Input.Activate>().single { kit.atw(it.source) == ghoul }
        assertTrue(toolkit.cast.begin(ghoul, sacrifice), "the Ghoul is sacrificed: ${toolkit.error}")
        kit.playOn("the Ghoul in the graveyard") { ghoul in graveyard() && kit.aliceMain() }
        assertEquals(PlayZone.GRAVEYARD, rowItem(ghoul).handZone)
        assertTrue(ghoul !in toolkit.playable(kit.alice.play()), "no glow: nothing pays its unearth")
        kit.tap(rowItem(ghoul))
        assertNull(toolkit.hand.raised, "not raised")
        assertEquals(ghoul, toolkit.shaking?.id, "it shakes")
    }
}
