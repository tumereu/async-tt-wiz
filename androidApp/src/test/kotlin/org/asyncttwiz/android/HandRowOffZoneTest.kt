package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ZON-079 (owner, 2026-11-12) on Alice's table: a card she may cast from her graveyard (Ember Flare, a free sorcery with
 * flashback {R}) shows at her hand row's right end after a thin divider with a Graveyard badge, while its pile still holds it;
 * it glows and casts as a hand card when she can cast it now, and is dull and shakes when she can't.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class HandRowOffZoneTest {
    @get:Rule val compose = createComposeRule()
    private val kit by lazy { EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(Choices.FLARE, "Mountain")))) }
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

    /** Alice casts an Ember Flare from her hand (free) and lets it resolve: it lies in her graveyard. Its id. */
    private fun flareInGraveyard(): String {
        val flare = kit.atw(kit.hand(Choices.FLARE).first())
        assertTrue(toolkit.cast.start(flare), "the Flare is cast: ${toolkit.error}")
        kit.playOn("the Flare in the graveyard") { kit.state().player(kit.me).graveyard.any { kit.atw(it) == flare } && kit.aliceMain() }
        return flare
    }

    private fun rowItem(card: String): TableItem = kit.scene().items.single { it.id == "handRow/$card" }

    // ZON-079: the flashback card in her graveyard is in her hand row, after her hand's cards and a divider, with the
    // Graveyard badge; the graveyard still holds it; with an untapped Mountain it glows (TBL-117) and two taps cast it from
    // there by its flashback cost, Autotap tapping the Mountain, as a hand card is cast
    @Test fun aFlashbackCardInTheGraveyardIsCastFromTheHandRow() {
        kit.opening()
        val mountain = kit.playLand()
        val flare = flareInGraveyard()
        val item = rowItem(flare)
        assertEquals(PlayZone.GRAVEYARD, item.handZone)
        val divider = assertNotNull(kit.scene().items.single { it.kind == TableItemKind.HAND && it.owner == kit.f.a }.handDivider, "a divider")
        assertTrue(divider < item.bounds.x, "the divider before it")
        assertEquals(PlayZone.HAND, item.zone)
        val hand = kit.scene().items.filter { it.kind == TableItemKind.CARD && it.zone == PlayZone.HAND && it.handZone == null && it.owner == kit.f.a }
        assertTrue(hand.isNotEmpty() && hand.all { it.bounds.right < item.bounds.x }, "at the row's right end")
        assertTrue(flare in kit.pile(PlayZone.GRAVEYARD).cards, "its pile still holds it")
        assertTrue(flare in toolkit.playable(kit.alice.play()), "it glows: castable now")
        render()
        compose.onNodeWithContentDescription("Ember Flare, owner Alice, untapped, from graveyard").assertExists()
        captureShot("$SCREENSHOTS/hand/hand-row-flashback.png",
            "Alice cast her Ember Flare and it went to her graveyard: with flashback it shows at the right end of her hand row after a thin divider, with a Graveyard badge, glowing because her untapped Mountain can pay its flashback cost.")
        kit.tap(rowItem(flare))
        assertEquals(flare, toolkit.hand.raised, "raised as a hand card")
        assertEquals(listOf("Flashback" to "{R}"), assertNotNull(toolkit.hand.chips).chips.map { it.name to it.cost })
        kit.tap(rowItem(flare))
        assertFalse(toolkit.cast.open, "nothing to ask: cast at once")
        kit.playOn("the Flare exiled") { kit.state().exile.any { kit.atw(it) == flare } && kit.aliceMain() }
        assertTrue(kit.alice.play().board.cards.single { it.id == mountain }.tapped, "Autotap tapped the Mountain")
        assertNull(kit.scene().items.firstOrNull { it.id == "handRow/$flare" }, "exiled: out of the hand row")
    }

    // ZON-079, TBL-128: without mana the flashback card still shows in her hand row, dull, and a tap only shakes it
    @Test fun withoutManaItShowsDullAndShakes() {
        kit.opening()
        val flare = flareInGraveyard()
        assertEquals(PlayZone.GRAVEYARD, rowItem(flare).handZone)
        assertFalse(flare in toolkit.playable(kit.alice.play()), "no glow: nothing pays its flashback cost")
        kit.tap(rowItem(flare))
        assertNull(toolkit.hand.raised, "not raised")
        assertEquals(flare, toolkit.shaking?.id, "it shakes")
    }

    // ZON-079: the other player's table shows nothing of Alice's flashback card in a hand row
    @Test fun theOtherPlayersRowHasNone() {
        kit.opening()
        val flare = flareInGraveyard()
        kit.settle()
        assertTrue(kit.scene(kit.f.b).items.none { it.id == "handRow/$flare" })
    }
}
