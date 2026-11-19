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
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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

/** STK-024, STK-162 (owner notes 2026-11-14): mana from a source whose cost uses other cards is produced on the board, never chosen by name. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class BoardManaTest {
    @get:Rule val compose = createComposeRule()
    /** A test's own table kit ([kit] otherwise). */
    private var kitOverride: EngineTableKit? = null
    private val defaultKit by lazy { EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(Choices.ELDERS, Choices.PUP, Choices.BLUE_NOTE)))) }
    private val kit get() = kitOverride ?: defaultKit
    private val toolkit get() = kit.toolkit
    private var version by mutableIntStateOf(0)

    @After fun forget() = AutomatumBoards.reset()

    private fun render() {
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

    // STK-214, STK-211, STK-191, CR 601.2h (owner 2026-11-19): Alice casts a Blue Note ({U}) with Autotap off, her Ash Urn ("{T}, Exile a card from your
    // graveyard: Add one mana of any color.") out and two cards in her graveyard: a tap on the Urn opens the panel's choice of the card it
    // exiles; she picks the second card and OK pays with it; the payment line names that card with Change
    @Test fun thePayerChoosesTheCardAManaAbilitysCostExiles() {
        val k = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(Choices.URN, Choices.STUDY, Choices.BLUE_NOTE))))
        kitOverride = k
        k.opening()
        val urnCard = k.atw(k.hand(Choices.URN).first())
        assertTrue(k.toolkit.cast.start(urnCard), "cast the Urn: ${k.toolkit.error}")
        k.playOn("the Urn on the battlefield") { urnCard in k.field(Choices.URN).map(k::atw) && k.aliceMain() }
        repeat(2) { n ->
            val study = k.atw(k.hand(Choices.STUDY).first())
            assertTrue(k.toolkit.cast.start(study), "cast Study ${n + 1}: ${k.toolkit.error}")
            k.playOn("Study ${n + 1} in the graveyard") { k.state().player(arcanum.engine.state.PlayerId(k.f.a)).graveyard.size == n + 1 && k.aliceMain() }
        }
        val graveyard = k.state().player(arcanum.engine.state.PlayerId(k.f.a)).graveyard
        assertEquals(2, graveyard.size)
        val note = k.atw(k.hand(Choices.BLUE_NOTE).first())
        k.toolkit.cast.autotap = false
        render()
        k.tap(k.item(note, PlayZone.HAND))
        k.tap(k.item(note, PlayZone.HAND))
        assertTrue(k.toolkit.cast.open, "the cast panel: ${k.toolkit.error}")
        refresh()
        val urn = k.atw(k.field(Choices.URN).first())
        k.tap(k.item(urn))
        refresh()
        val pick = assertNotNull(k.toolkit.cast.costPick, "the Urn's cost asks which card")
        assertEquals(graveyard.toSet(), pick.choice.options.toSet())
        compose.onNodeWithContentDescription("Cards for ${Choices.URN}'s cost", substring = true).assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/cast-panel/mana-cost-pick.png",
            "Alice opened Blue Note's cast panel with Autotap off and tapped her Ash Urn (\"{T}, Exile a card from your graveyard: Add one " +
                "mana of any color.\"): the panel asks which card of her graveyard it exiles, her two Hedge Studies drawn as cards, with OK and Cancel.")
        assertTrue(k.toolkit.cast.pickCostObjects(listOf(graveyard.last())))
        k.playOn("the payment worked out") { !k.toolkit.cast.busy }
        refresh()
        assertNull(k.toolkit.cast.costPick)
        assertTrue(k.toolkit.cast.paid, "the Urn pays: ${k.toolkit.cast.mana}")
        val used = k.toolkit.cast.mana.filterIsInstance<arcanum.engine.decision.ManaUse.Ability>().single()
        assertEquals(listOf<arcanum.engine.decision.Answer>(arcanum.engine.decision.Answer.Objects(listOf(graveyard.last()))),
            used.answers.filterIsInstance<arcanum.engine.decision.Answer.Objects>())
        compose.onNodeWithContentDescription("Change the cards ${Choices.URN}: ${Choices.STUDY} uses").assertExists()
    }

    // STK-024, STK-162, STK-163 (owner notes 2026-11-14): with no payment chosen for her (Autotap off), Alice's Blue Note ({U}) with her Hearth Elders
    // ("Tap two untapped creatures you control: Add one mana of any color.") and Ash Pups out: the cast panel offers no chip naming
    // the Elders or the creatures its cost taps; it says "Still owed {U}: produce the mana on the board."; a tap on the Elders on the
    // table pays it and Cast is enabled
    @Test fun aSourceTappingOtherCreaturesIsProducedOnTheBoard() {
        kit.opening()
        for (name in listOf(Choices.ELDERS, Choices.PUP, Choices.PUP)) {
            val card = kit.atw(kit.hand(name).first())
            assertTrue(toolkit.cast.start(card), "cast $name: ${toolkit.error}")
            kit.playOn("$name on the battlefield") { card in kit.field(name).map(kit::atw) && kit.aliceMain() }
        }
        val note = kit.atw(kit.hand(Choices.BLUE_NOTE).first())
        toolkit.cast.autotap = false
        render()
        kit.tap(kit.item(note, PlayZone.HAND))
        kit.tap(kit.item(note, PlayZone.HAND))
        assertTrue(toolkit.cast.open, "the cast panel: ${toolkit.error}")
        refresh()
        compose.onNodeWithContentDescription("Not paid: Still owed {U}: produce the mana on the board.").assertExists()
        compose.onNodeWithContentDescription("Mana source ", substring = true).assertDoesNotExist()
        compose.onNodeWithText("${Choices.ELDERS}:", substring = true).assertDoesNotExist()
        compose.onRoot().captureShot("$SCREENSHOTS/cast-panel/payment-on-the-board.png",
            "Alice opened Blue Note's cast panel with Autotap off and her Hearth Elders (\"Tap two untapped creatures you control: Add " +
                "one mana of any color.\") and Ash Pups out: no chip names them, the panel says only \"Still owed {U}: produce the mana " +
                "on the board.\" in red, and Cast is greyed.")
        val elders = kit.atw(kit.field(Choices.ELDERS).first())
        kit.tap(kit.item(elders))
        refresh()
        assertTrue(toolkit.cast.paid, "the Elders pay")
        assertEquals(setOf(elders), toolkit.cast.paySources())
        compose.onNodeWithContentDescription("Not paid", substring = true).assertDoesNotExist()
        compose.onNode(hasText("Cast") and hasClickAction()).assertIsEnabled()
    }
}
