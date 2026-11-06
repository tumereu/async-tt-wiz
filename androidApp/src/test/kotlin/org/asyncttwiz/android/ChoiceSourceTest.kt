package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import org.asyncttwiz.android.EngineGameFixture.Choices
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * STK-044 (owner 2026-11-05): every Automatum panel and target bar names its source by the card, not by text: the title
 * says only the choice and the source sits at the panel's right as a small card (an ability as its Stack ability token,
 * STK-046); holding it inspects it (TBL-104). Alice's decks are free invented cards whose casting, resolving or triggers
 * ask Automatum's choices ([EngineGameFixture.Choices]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class ChoiceSourceTest {
    @get:Rule val compose = createComposeRule()

    @After fun forget() = AutomatumBoards.reset()

    private fun kit(vararg cards: String) = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = cards.toList())))

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

    private fun bounds(node: SemanticsNodeInteraction): Rect = node.fetchSemanticsNode().boundsInRoot

    /** The source card ([CHOICE_SOURCE_TAG]) is [name], at the right of [panel] (its last few dp) and right of [title]. */
    private fun sourceAtTheRight(panel: String, title: String, name: String) {
        val source = compose.onNodeWithTag(CHOICE_SOURCE_TAG, useUnmergedTree = true)
        source.assert(hasContentDescription(name))
        val box = bounds(compose.onNodeWithContentDescription(panel))
        val card = bounds(source)
        val heading = bounds(compose.onNode(hasText(title) and isHeading()))
        assertTrue(box.right - card.right < 20f, "the source at the panel's right: $card in $box")
        assertTrue(card.left >= heading.right, "right of the title: $card, title $heading")
    }

    /** The source is drawn as an ability token with its [marker] ("Triggered ability", "Activated ability"), STK-046. */
    private fun sourceIsAnAbilityToken(marker: String) {
        compose.onNode(hasContentDescription(marker) and hasAnyAncestor(hasTestTag(CHOICE_SOURCE_TAG)), useUnmergedTree = true).assertExists()
    }

    /** Holding the source card shows Inspect with [image] while held and closes it on release (TBL-104). */
    private fun holdingTheSourceInspects(image: String) {
        val source = compose.onNodeWithTag(CHOICE_SOURCE_TAG, useUnmergedTree = true)
        compose.mainClock.autoAdvance = false
        source.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("Card inspection content").assertExists()
        compose.onNodeWithContentDescription("Card image: $image").assertExists()
        source.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Card inspection content").assertDoesNotExist()
    }

    // STK-023, STK-044: Alice casts Cinder Charm (a mode to choose): the cast panel's title says only what is done, "Cast a
    // spell", and the Charm is the small card at the panel's right; holding it inspects it
    @Test fun theCastPanelShowsTheCardAtItsRight() {
        val kit = kit(Choices.CHARM, "Mountain")
        kit.opening()
        assertTrue(kit.toolkit.cast.start(kit.atw(kit.hand(Choices.CHARM).first())), "the cast begins: ${kit.toolkit.error}")
        render(kit)
        compose.onNode(hasText("Cast a spell") and isHeading()).assertExists()
        sourceAtTheRight("Cast panel", "Cast a spell", Choices.CHARM)
        compose.onRoot().captureShot("$SCREENSHOTS/choice-source/cast-panel.png",
            "Alice cast Cinder Charm, which asks for a mode: the cast panel above her hand is titled only \"Cast a spell\", and the Charm sits at the panel's right as a small card she can hold to inspect.")
        holdingTheSourceInspects(Choices.CHARM)
    }

    // STK-047, STK-044: choosing the Charm's damage mode turns the panel into the target bar: "Choose any target", with
    // the Charm at its right
    @Test fun theTargetBarShowsTheSpellAtItsRight() {
        val kit = kit(Choices.CHARM, "Mountain")
        kit.opening()
        assertTrue(kit.toolkit.cast.start(kit.atw(kit.hand(Choices.CHARM).first())))
        render(kit)
        compose.onNodeWithText("Cinder Charm deals 1 damage to any target.").performClick()
        compose.waitForIdle()
        assertTrue(kit.toolkit.cast.targeting, "targets are picked on the table")
        compose.onNode(hasText("Choose any target") and isHeading()).assertExists()
        sourceAtTheRight("Choose targets", "Choose any target", Choices.CHARM)
        compose.onRoot().captureShot("$SCREENSHOTS/choice-source/target-bar.png",
            "Alice chose Cinder Charm's damage mode: the slim bar above her hand says only \"Choose any target\" beside its eye, and the Charm sits at the bar's right as a small card while the legal targets glow.")
        holdingTheSourceInspects(Choices.CHARM)
    }

    // STK-047, STK-044, STK-046: activating Spark Totem's ability ({T}: 1 damage to any target) asks for its target in the
    // target bar, its source the ability as its token (the Totem's art with the "A" chip), not the card
    @Test fun anActivationsTargetBarShowsItsAbilityToken() {
        val kit = kit(Choices.TOTEM, "Mountain")
        kit.opening()
        val totem = kit.atw(kit.hand(Choices.TOTEM).first())
        assertTrue(kit.toolkit.cast.start(totem), "the Totem is cast")
        kit.playOn("the Totem on the battlefield") { totem in kit.field(Choices.TOTEM).map(kit::atw) && kit.aliceMain() }
        kit.tap(kit.item(totem))
        assertTrue(kit.toolkit.cast.targeting, "one tap activates its one ability, targets picked on the table: ${kit.toolkit.error}")
        render(kit)
        sourceAtTheRight("Choose targets", "Choose any target", Choices.TOTEM)
        sourceIsAnAbilityToken("Activated ability")
        compose.onRoot().captureShot("$SCREENSHOTS/choice-source/target-bar-ability.png",
            "Alice tapped her Spark Totem to activate its ability: the bar above her hand says only \"Choose any target\", and at its right is the ability as a small dark token filled with the Totem's art and an \"A\" chip at its bottom, not the Totem card.")
        holdingTheSourceInspects(Choices.TOTEM)
    }

    /** Alice's Ember Divination resolving: her Scry 2. */
    private fun scry(kit: EngineTableKit): Decision.ChooseObjects {
        kit.opening()
        assertTrue(kit.toolkit.cast.start(kit.atw(kit.hand(Choices.DIVINATION).first())), "the Divination is cast")
        kit.playOn("Alice's scry") { kit.state().pending?.decision is Decision.ChooseObjects }
        return assertIs<Decision.ChooseObjects>(kit.state().pending?.decision)
    }

    // STK-030, STK-044: Alice's Ember Divination resolves: the open choice's title is only "Scry 2" and the Divination is
    // the small card at the panel's right; holding it inspects it
    @Test fun anOpenChoiceShowsItsSpellAtTheRight() {
        val kit = kit(Choices.DIVINATION, "Mountain")
        scry(kit)
        render(kit)
        compose.onNode(hasText("Scry 2") and isHeading()).assertExists()
        sourceAtTheRight("Automatum choice", "Scry 2", Choices.DIVINATION)
        compose.onRoot().captureShot("$SCREENSHOTS/choice-source/open-choice.png",
            "Alice's Ember Divination resolves and she scries 2: the panel above her hand is titled only \"Scry 2\", the Divination sits at its right as a small card, and the Top and Bottom areas hold the two cards.")
        holdingTheSourceInspects(Choices.DIVINATION)
    }

    // STK-044: in landscape the panel is at most 420 dp wide from the left edge; the source is at its right all the same
    @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    @Test fun anOpenChoiceShowsItsSpellAtTheRightInLandscape() {
        val kit = kit(Choices.DIVINATION, "Mountain")
        scry(kit)
        render(kit)
        sourceAtTheRight("Automatum choice", "Scry 2", Choices.DIVINATION)
        compose.onRoot().captureShot("$SCREENSHOTS/choice-source/open-choice-landscape.png",
            "In landscape Alice's Ember Divination resolves and she scries 2: the panel at the left above her hand is titled only \"Scry 2\" and the Divination sits at the panel's right as a small card.")
    }

    // STK-030, STK-044, STK-046: Alice's Spark Seer enters; its trigger's target is her open choice, in the target bar:
    // "Choose any target" with the trigger as its ability token (the Seer's art, the "T" chip) at the right
    @Test fun aTriggersTargetBarShowsItsAbilityToken() {
        val kit = kit(Choices.SEER, "Mountain")
        kit.opening()
        assertTrue(kit.toolkit.cast.start(kit.atw(kit.hand(Choices.SEER).first())), "the Seer is cast")
        kit.playOn("the Seer's trigger asking for its target") { kit.state().pending?.decision is Decision.Targets }
        render(kit)
        assertTrue(kit.toolkit.flows.targeting, "targets are picked on the table")
        sourceAtTheRight("Choose targets", "Choose any target", Choices.SEER)
        sourceIsAnAbilityToken("Triggered ability")
        compose.onRoot().captureShot("$SCREENSHOTS/choice-source/trigger-target-bar.png",
            "Alice's Spark Seer entered and its trigger asks for a target: the bar above her hand says only \"Choose any target\", and at its right is the trigger as a small dark ability token filled with the Seer's art and a \"T\" chip at its bottom.")
        holdingTheSourceInspects(Choices.SEER)
    }

    // STK-048, STK-018, STK-044: in Full control Alice casts two Cinder Charms, then Quench Spark: the lower Charm is a legal
    // target, so tapping the Stack opens it in place as its list, both Charms glowing as legal targets, while the target bar
    // stays above her hand with the Quench at its right; tapping the lower Charm picks it
    @Test fun theStackOpensAsItsListToPickATargetBelowTheTop() {
        val kit = kit(Choices.CHARM, Choices.CHARM, Choices.QUENCH)
        kit.opening()
        kit.alice.settings(PlaySettings(mode = ResponseMode.FullControl, alwaysAssumeResolves = true))
        kit.scenario.confirmStops[kit.f.a] = false
        repeat(2) {
            val s = kit.state()
            assertIs<GameDevice.Acted.Sent>(kit.alice.send(kit.scenario.cast(s, kit.me, Input.Cast(kit.me, kit.hand(Choices.CHARM).first()))))
        }
        assertEquals(2, kit.state().stack.size, "both Charms on the Stack, Alice holding priority")
        assertTrue(kit.toolkit.cast.start(kit.atw(kit.hand(Choices.QUENCH).first())), "the Quench is cast: ${kit.toolkit.error}")
        assertTrue(kit.toolkit.cast.targeting)
        render(kit)
        // The bar's source, held, inspects the Quench (with the list open, a touch outside it closes it, TBL-068)
        holdingTheSourceInspects(Choices.QUENCH)
        compose.runOnIdle { kit.tap(kit.scene().items.first { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }) }
        assertTrue(kit.toolkit.stackTargeting, "the Stack opens as its list to pick from")
        compose.waitForIdle()
        compose.onNodeWithText("Stack · 2").assertExists()
        val legal = compose.onAllNodes(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
            "Legal target"))
        legal.assertCountEquals(2)
        sourceAtTheRight("Choose targets", "Choose target spell", Choices.QUENCH)
        compose.onRoot().captureShot("$SCREENSHOTS/choice-source/stack-target-list.png",
            "Alice cast two Cinder Charms and then Quench Spark, and tapped the glowing Stack: it opened in place as its list \"Stack · 2\" with both Charms glowing violet as legal targets, while the bar above her hand still says \"Choose target spell\" with the Quench at its right.")
        // The lower Charm (the list's second row) is picked: the list closes and the Quench goes on the Stack targeting it
        val lower = kit.state().stack.first()
        compose.onNodeWithText("2 · ${Choices.CHARM}").performClick()
        compose.waitForIdle()
        kit.playOn("the Quench on the Stack", bobActs = false) { kit.state().stack.size == 3 }
        assertTrue(!kit.toolkit.stackTargeting, "the list closed")
        compose.onNodeWithText("Stack · 2").assertDoesNotExist()
        val quench = kit.state().stack.last()
        assertEquals(listOf<arcanum.engine.state.TargetRef>(arcanum.engine.state.TargetRef.Object(lower)),
            kit.state().stackItems.getValue(quench).targets.values.flatten(), "the Quench targets the lower Charm")
    }
}
