package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.engine.state.GameState
import arcanum.engine.state.StackItemKind
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
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-07 (Fractured Sanity: "is it the cast or the cycle effect?"): on Alice's table, what Bob's top
 * Stack entry is while she holds priority (STK-057), its target arrows (STK-058), the Stack list's choices (STK-018), an
 * ability's kind in Inspect (STK-050) and the cycling event text (HIS-091), on games played through the simulated server.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class StackClarityTest {
    @get:Rule val compose = createComposeRule()
    private val names = mapOf(TableStatesFixture.a to "Alice", TableStatesFixture.b to "Bob")

    @After fun forget() = AutomatumBoards.reset()

    private fun render(kit: EngineTableKit) {
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides kit.routes) {
                        Box(Modifier.fillMaxSize()) {
                            val play = kit.alice.play()
                            TableSurface(kit.f.game, play, kit.f.a, false, Modifier.fillMaxSize(), connected = true,
                                onIntent = kit.toolkit::handle, toolkit = kit.toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { kit.routes.undo() },
                                    controlsConnected = true, waitingLine = kit.routes.waitingLine(play)))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Alice ends her turn and plays on to Bob's top Stack entry of [kind], with her holding priority over it. */
    private fun toBobsEntry(kit: EngineTableKit, kind: (GameState) -> Boolean) {
        kit.scenario.confirmStops[kit.f.a] = false
        assertTrue(kit.toolkit.automatumStep(TableActionIntent.End(kit.scope)), "End turn")
        kit.playOn("Alice holding priority over Bob's entry", rounds = 40) {
            val s = kit.state()
            s.stack.isNotEmpty() && s.priority.holder == kit.me && kit.routes.arrowShown() && kind(s)
        }
    }

    // STK-057, STK-058, STK-018 (owner playtest 2026-11-07): Bob casts Storm Spark at Alice's Ember Cub; at her pause a line
    // says "Bob cast Storm Spark · deals 3 damage to Ember Cub" (the Cub's name in her colour, owner 2026-11-07) and an
    // arrow runs from the Stack to the Cub, highlighted in Bob's colour; the Stack list's row names the Cub in the Spark's text
    @Test fun anOpponentsTargetedSpellSaysWhatItDoesAndPointsAtItsTarget() {
        val kit = EngineTableKit()
        val cub = kit.toReadyCub()
        kit.bobCastsSpark { s -> TargetRef.Object(s.battlefield.first { (it as arcanum.engine.state.ObjectId.Card).card.value == cub }) }
        toBobsEntry(kit) { s -> s.stackItems[s.stack.last()]?.kind == StackItemKind.Spell }
        render(kit)
        compose.onNodeWithTag("stack-line").assert(hasContentDescription("Bob cast Storm Spark · deals 3 damage to Ember Cub"))
        compose.onNodeWithTag("stack-target-arrows", useUnmergedTree = true)
            .assert(hasContentDescription("Targets of the top Stack entry: Ember Cub"))
        captureShot("$SCREENSHOTS/stack/opponent-spell-line.png",
            "Bob cast Storm Spark at Alice's Ember Cub and she holds priority: a line over the battlefield says \"Bob cast Storm Spark · deals 3 damage to Ember Cub\" with the Cub's name in her green, and a red arrow runs from the Stack to the Cub, which has a soft red highlight.")
        compose.onNodeWithContentDescription("Stack, 1 entries").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Storm Spark deals 3 damage to Ember Cub.").assertExists()
        compose.onNodeWithText("Target: Ember Cub").assertDoesNotExist()
        captureShot("$SCREENSHOTS/stack/stack-list-choices.png",
            "Alice tapped the Stack holding Bob's Storm Spark: the list's row shows the Spark and, under its name, its text with Bob's target named in it, \"Storm Spark deals 3 damage to Ember Cub.\", the Cub's name in her green.")
    }

    // STK-057, STK-050, HIS-091 (owner playtest 2026-11-07, CR 702.29c): Bob cycles Shattered Reason; at Alice's pause the line
    // names the cycling trigger and what it does ("each opponent mills 4 cards"), not the spell; the Stack list names each
    // ability's kind and holding the trigger inspects it as "Cycling trigger"; the event reads "Bob cycled Shattered Reason"
    @Test fun anOpponentsCyclingTriggerIsNamedAsOne() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(bobCards = listOf(Choices.REASON, "Mountain"))))
        kit.opening()
        // A Mountain and a Storm Spark in hand: Alice can respond, so Bob's trigger is a point where she acts
        kit.playLand()
        kit.bobCycles(Choices.REASON)
        toBobsEntry(kit) { s -> s.stackItems[s.stack.last()]?.kind == StackItemKind.Triggered }
        assertTrue(kit.alice.timeline.events(kit.alice, names).any { it.description == "Bob cycled ${Choices.REASON}" },
            "${kit.alice.timeline.events(kit.alice, names).map { it.description }}")
        render(kit)
        compose.onNodeWithTag("stack-line").assert(hasContentDescription("${Choices.REASON}'s cycling trigger · each opponent mills 4 cards"))
        // No targets: no arrows
        compose.onNodeWithTag("stack-target-arrows", useUnmergedTree = true).assertDoesNotExist()
        captureShot("$SCREENSHOTS/stack/cycling-trigger-line.png",
            "Bob cycled Shattered Reason and Alice holds priority over its cycling trigger: the Stack shows the trigger's token and a line over the battlefield says \"Shattered Reason's cycling trigger · each opponent mills 4 cards\".")
        compose.onNodeWithContentDescription("Stack, 2 entries").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Cycling trigger").assertExists()
        compose.onNodeWithText("Cycling ability").assertExists()
        compose.mainClock.advanceTimeBy(400)
        compose.mainClock.autoAdvance = false
        val row = compose.onNodeWithText("Top · ${Choices.REASON}'s ability")
        row.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("Cycling trigger of ${Choices.REASON}").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(100)
        captureShot("$SCREENSHOTS/stack/cycling-trigger-inspect.png",
            "Alice holds Bob's Shattered Reason cycling trigger in the Stack list: Inspect's dark panel shows the card's art, the \"T\" chip with \"Cycling trigger\", the card's name and the trigger's text.")
        row.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(2, kit.state().stack.size)
    }
}
