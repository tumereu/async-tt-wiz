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
            "Bob cast Storm Spark at Alice's Ember Cub and she holds priority: the band under his hand says \"Bob cast Storm Spark\" and, small under it, \"deals 3 damage to Ember Cub\" with the Cub's name in her green, and a red arrow runs from the Stack to the Cub, which has a soft red highlight.")
        compose.onNodeWithContentDescription("Stack, 1 entries").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Storm Spark deals 3 damage to Ember Cub.").assertExists()
        compose.onNodeWithText("Target: Ember Cub").assertDoesNotExist()
        captureShot("$SCREENSHOTS/stack/stack-list-choices.png",
            "Alice tapped the Stack holding Bob's Storm Spark: the list's row shows the Spark and, under its name, its text with Bob's target named in it, \"Storm Spark deals 3 damage to Ember Cub.\", the Cub's name in her green.")
    }

    // STK-057, STK-018 (test and screenshots only): Bob casts Cinder Charm choosing its second mode; at Alice's pause the line
    // says the chosen mode, not the card's whole text, and the Stack list's row shows it
    @Test fun anOpponentsModalSpellShowsItsChosenMode() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(bobCards = listOf(Choices.CHARM, "Mountain"))))
        kit.opening()
        // A Mountain and a Storm Spark in hand: Alice can respond, so Bob's spell is a point where she acts
        kit.playLand()
        kit.bobCasts(Choices.CHARM, free = true) { d -> if (d is arcanum.engine.decision.Decision.Modes) arcanum.engine.decision.Answer.Modes(listOf(1)) else null }
        toBobsEntry(kit) { s -> s.stackItems[s.stack.last()]?.kind == StackItemKind.Spell }
        render(kit)
        compose.onNodeWithTag("stack-line").assert(hasContentDescription("Bob cast ${Choices.CHARM} · you gain 2 life"))
        captureShot("$SCREENSHOTS/stack/opponent-mode-line.png",
            "Bob cast Cinder Charm choosing its second mode and Alice holds priority: the band under his hand says \"Bob cast Cinder Charm\" with \"you gain 2 life\" small under it, only the chosen mode (its own words: \"you\" is Bob).")
        compose.onNodeWithContentDescription("Stack, 1 entries").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Mode: You gain 2 life.", substring = true).assertExists()
        captureShot("$SCREENSHOTS/stack/opponent-mode-list.png",
            "Alice tapped the Stack holding Bob's Cinder Charm: its row shows the card's text and under it the chosen mode, \"Mode: You gain 2 life.\"")
    }

    // STK-057, STK-018 (test and screenshots only): Bob casts Ember Burst with X = 1 at Alice; at her pause the line fills X in
    // ("deals 1 damage to you") and the Stack list's row shows it with "X = 1"
    @Test fun anOpponentsXSpellShowsItsX() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(bobCards = listOf(Choices.BURST, "Mountain"))))
        kit.opening()
        kit.playLand()
        kit.bobCasts(Choices.BURST) { d -> when (d) {
            is arcanum.engine.decision.Decision.Announce -> arcanum.engine.decision.Answer.Number(1)
            is arcanum.engine.decision.Decision.Targets -> arcanum.engine.decision.Answer.Targets(listOf(TargetRef.Player(kit.me)))
            else -> null
        } }
        toBobsEntry(kit) { s -> s.stackItems[s.stack.last()]?.kind == StackItemKind.Spell }
        render(kit)
        compose.onNodeWithTag("stack-line").assert(hasContentDescription("Bob cast ${Choices.BURST} · deals 1 damage to you"))
        captureShot("$SCREENSHOTS/stack/opponent-x-line.png",
            "Bob cast Ember Burst with X = 1 at Alice and she holds priority: the band under his hand says \"Bob cast Ember Burst\" with \"deals 1 damage to you\" small under it, X filled in.")
        compose.onNodeWithContentDescription("Stack, 1 entries").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ember Burst deals 1 damage to you.").assertExists()
        compose.onNodeWithText("X = 1").assertExists()
        captureShot("$SCREENSHOTS/stack/opponent-x-list.png",
            "Alice tapped the Stack holding Bob's Ember Burst: its row shows \"Ember Burst deals 1 damage to you.\" with X filled in and \"X = 1\" under it.")
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
            "Bob cycled Shattered Reason and Alice holds priority over its cycling trigger: the Stack shows the trigger's token and the band under his hand says \"Shattered Reason's cycling trigger\" with \"each opponent mills 4 cards\" small under it.")
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
