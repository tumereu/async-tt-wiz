package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.engine.state.TargetRef
import org.asyncttwiz.android.EngineGameFixture.Choices
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D1 (owner playtest 2026-11-07): a cost or choice whose every candidate is a battlefield permanent is picked on the table
 * as targets are (STK-047): the slim bar with the question, the candidates glowing, a group of identical ones opening on a
 * tap; a choice among cards elsewhere (a discard from the hand) keeps its panel.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class TablePickTest {
    @get:Rule val compose = createComposeRule()

    @After fun forget() = AutomatumBoards.reset()

    private fun render(kit: EngineTableKit) {
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
    }

    /** Alice casts her (free) [name] from her hand and lets it resolve. */
    private fun EngineTableKit.castFree(name: String) {
        val card = atw(hand(name).first())
        assertTrue(toolkit.cast.start(card), "$name is cast: ${toolkit.error}")
        playOn("$name on the battlefield") { card in field(name).map(::atw) && aliceMain() }
    }

    // D1, STK-047, STK-044, STK-023: Alice taps her Ember Altar ("{T}, Sacrifice another creature: …"): the creature
    // to sacrifice is picked on the table, not in a panel: the slim bar says "Sacrifice" over the ability's text, the Altar at
    // its right, her two Ash Pups glow violet (the Altar doesn't); a tap on a Pup sacrifices it and the ability goes on the
    // Stack
    @Test fun aSacrificeCostIsPickedOnTheTable() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(Choices.ALTAR, Choices.PUP, Choices.PUP))))
        kit.opening()
        kit.castFree(Choices.ALTAR)
        kit.castFree(Choices.PUP)
        kit.castFree(Choices.PUP)
        val altar = kit.atw(kit.field(Choices.ALTAR).single())
        val pups = kit.field(Choices.PUP).map(kit::atw)
        assertEquals(2, pups.size)
        kit.tap(kit.item(altar))
        val cast = kit.toolkit.cast
        assertTrue(cast.targeting, "the sacrifice is picked on the table: ${kit.toolkit.error}")
        val pick = assertIs<TablePick.Objects>(cast.pick)
        assertEquals("Sacrifice", pick.title)
        assertEquals(pups.toSet(), cast.glow(kit.alice.play()).intersect(pups.toSet() + altar), "the Pups glow, the Altar doesn't")
        render(kit)
        compose.onNode(hasText("Sacrifice") and isHeading()).assertExists()
        compose.onNodeWithContentDescription("Cast panel").assertDoesNotExist()
        compose.onRoot().captureShot("$SCREENSHOTS/table-pick/sacrifice-bar.png",
            "Alice tapped her Ember Altar, whose cost sacrifices another creature: the slim bar above her hand says \"Sacrifice\" over " +
                "the ability's text with the ability at its right, and her two Ash Pups glow " +
                "violet on the table to be picked.")
        // A tap on a Pup picks it: the cost's one creature, so the ability goes on at once
        kit.tap(kit.item(pups[0]))
        assertTrue(!cast.targeting, "the pick answered the cost: ${kit.toolkit.error}")
        kit.playOn("the Pup sacrificed, the Altar's ability resolved") {
            kit.field(Choices.PUP).size == 1 && kit.state().stack.isEmpty() && kit.aliceMain()
        }
        assertEquals(22, kit.state().player(kit.me).life.toInt(), "the ability resolved: Alice gained 2")
    }

    // D1, STK-030, STK-047: an engine choice of battlefield permanents is a table pick, its candidates as targets and its
    // answer the chosen cards; a choice of cards in the hand (a discard) keeps its panel
    @Test fun onlyAChoiceAmongBattlefieldPermanentsIsPickedOnTheTable() {
        val kit = EngineTableKit()
        val discard = kit.toAliceDiscard()
        assertNull(TablePick.of(discard, kit.state()), "a discard from the hand keeps its panel")
        render(kit)
        assertTrue(!kit.toolkit.flows.targeting, "the discard is a panel")
        compose.onNodeWithContentDescription("Automatum choice").assertExists()
        // A plain pick among the same hand cards still keeps its panel: only battlefield candidates go to the table
        assertNull(TablePick.of(discard.copy(min = 1, max = 1, text = "Choose to exile a card"), kit.state()))
    }

    // D1, STK-047: a table pick of permanents answers with the chosen cards, its candidates being their objects as targets
    @Test fun aPickOfPermanentsAnswersWithTheChosenCards() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(Choices.PUP))))
        kit.opening()
        kit.castFree(Choices.PUP)
        kit.castFree(Choices.PUP)
        val pups = kit.field(Choices.PUP)
        val choice = arcanum.engine.decision.Decision.ChooseObjects(arcanum.engine.decision.DecisionId("d"), kit.me, null, pups, 1, 1,
            "Choose to sacrifice a creature")
        val pick = assertIs<TablePick.Objects>(TablePick.of(choice, kit.state()))
        assertEquals("Sacrifice a creature", pick.title)
        assertEquals(pups.map { TargetRef.Object(it) }, pick.options)
        assertEquals(arcanum.engine.decision.Answer.Objects(listOf(pups[1])), pick.answer(listOf(TargetRef.Object(pups[1]))))
        // Too long a sentence for a title says how many to pick
        assertEquals("Choose one", assertIs<TablePick.Objects>(TablePick.of(choice.copy(text =
            "Each opponent sacrifices a creature of their choice."), kit.state())).title)
    }
}
