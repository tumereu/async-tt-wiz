package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.api.CounterKind
import arcanum.engine.decision.Decision
import arcanum.engine.state.ObjectId
import org.asyncttwiz.android.EngineGameFixture.Choices
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
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-06 (Bushy Bodyguard): a creature with offspring and "When this creature enters, you may forage. If
 * you do, put two +1/+1 counters on it." cast with offspring, every step on Alice's table: ordering the two enter
 * triggers, the Stack's inspect of each ability (STK-050), the token copy's own trigger, the forage question with its
 * sentence and cost, forage's choice of way, the cards to exile, and declining the original's forage. Engine side:
 * arcanum's BlbOffspringForageWalkthroughTest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class OffspringForageWalkthroughTest {
    @get:Rule val compose = createComposeRule()
    private val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(Choices.SENTRY, Choices.PIE, Choices.STUDY, "Mountain"))))
    private val toolkit = kit.toolkit
    private var version by mutableIntStateOf(0)

    private val forage = "When this creature enters, you may forage. If you do, put two +1/+1 counters on it."
    private val offspring = "Offspring {R} (You may pay an additional {R} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)"

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

    private fun refresh() { kit.settle(); version++; compose.waitForIdle() }

    private fun pending(): Decision? = kit.state().pending?.decision

    /** Alice lets the game go on: her stop's own action (STK-036, Control) or a pass. */
    private fun go() {
        (kit.alice.device.status as? GameDevice.Status.Stopped)?.let { kit.alice.act(it.assumed); return }
        kit.alice.pass()
    }

    /** Alice's turns pass (End turn) until [name] is in her hand, back in her Main 1. */
    private fun toHand(name: String) {
        repeat(8) {
            if (kit.hand(name).isNotEmpty()) return
            val turn = kit.state().turn.number
            assertTrue(toolkit.automatumStep(TableActionIntent.End(kit.scope)), "End turn")
            kit.playOn("Alice's next Main 1", rounds = 40) {
                if (kit.state().priority.holder == kit.me && pending() == null && kit.state().turn.active != kit.me) go()
                kit.aliceMain() && kit.state().turn.number > turn
            }
        }
        assertTrue(kit.hand(name).isNotEmpty(), "$name in Alice's hand: ${kit.state().player(kit.me).hand}")
    }

    /** Alice casts the free [name] and it resolves. */
    private fun castFree(name: String) {
        toHand(name)
        val card = kit.atw(kit.hand(name).first())
        assertTrue(toolkit.cast.start(card), "$name is cast: ${toolkit.error}")
        kit.playOn("$name resolving") {
            if (kit.state().stack.isNotEmpty() && kit.state().priority.holder == kit.me && pending() == null) go()
            kit.state().stack.isEmpty() && kit.aliceMain()
        }
    }

    /** Holding the Stack list's row [row] shows the ability's inspect tile with [text]; captured as [shot]. */
    private fun holdInStackList(row: String, text: String, shot: String, caption: String) {
        compose.onNode(hasContentDescription("Stack, ", substring = true) and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(400)
        compose.mainClock.autoAdvance = false
        val node = compose.onNodeWithText(row)
        node.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("Triggered ability of ${Choices.SENTRY}").assertIsDisplayed()
        compose.onNode(hasText(text, substring = true) and hasAnyAncestor(hasContentDescription("Triggered ability of ${Choices.SENTRY}")))
            .assertExists()
        compose.mainClock.advanceTimeBy(100)
        captureShot(shot, caption)
        node.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Card inspection content").assertDoesNotExist()
        compose.onAllNodes(hasText("Close") or hasContentDescription("Close")).onFirst().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(row).assertDoesNotExist()
    }

    // STK-030, STK-044, STK-050, CR 702.175a, CR 603.3b, CR 603.4, CR 701.61a, CR 118.12 (owner playtest 2026-11-06):
    // Alice casts Hollowroot Sentry with offspring; both enter triggers are hers to order, each named by its sentence (the
    // offspring trigger by its keyword's printed line); while the forage trigger asks, holding the offspring trigger on the
    // Stack shows its line; the forage question shows its sentence and its cost without holding; forage asks which way and
    // which cards; the token copy's own forage trigger shows its text on the Stack and declining it puts nothing on it
    @Test fun anOffspringAndForageCreatureStepByStep() {
        kit.opening()
        // Control: Automatum asks her the order of her triggers rather than putting them on in the order offered
        kit.alice.settings(kit.alice.shown!!.settings.copy(mode = org.asyncttwiz.protocol.game.ResponseMode.FullControl))
        kit.scenario.confirmStops[kit.f.a] = false
        toHand("Mountain")
        kit.playLand()
        castFree(Choices.PIE)
        // Four cards in her graveyard, so forage can exile and has cards to choose among
        kit.playOn("four cards in Alice's graveyard", rounds = 60) {
            val s = kit.state()
            if (s.player(kit.me).graveyard.size < 4 && kit.aliceMain()) castFree(Choices.STUDY)
            s.player(kit.me).graveyard.size >= 4 && kit.aliceMain()
        }
        toHand(Choices.SENTRY)
        if (kit.field("Mountain").none { kit.state().obj(it)?.tapped == false }) toHand("Mountain").also { kit.playLand() }
        val sentry = kit.atw(kit.hand(Choices.SENTRY).first())
        // Offspring is the cast's extra (ZON-082), paid with the Mountain
        assertTrue(toolkit.cast.start(sentry, extras = listOf(1)), "the Sentry is cast with offspring: ${toolkit.error}")
        untilDecision<Decision.OrderTriggers>("Alice ordering the Sentry's two enter triggers")
        render()

        // 1. Ordering the triggers (CR 603.3b): each is the Sentry's card with its own line, the offspring one its keyword's
        compose.onNode(hasText("Order triggers") and isHeading()).assertExists()
        compose.onNodeWithText(offspring, substring = true).assertExists()
        compose.onNodeWithText(forage).assertExists()
        captureShot("$SCREENSHOTS/questions/offspring-order-triggers.png",
            "Alice's Hollowroot Sentry, cast with offspring, has entered: the panel above her hand is titled \"Order triggers\" and lists the Sentry twice as rows numbered 1 and 2, each with a drag handle, one with its offspring line and one with its forage line, the top one resolving first.")
        // STK-030 (owner gallery review, 2026-11-06): a drag-to-reorder list; forage moves to the top (it resolves first),
        // offspring below it (first on the Stack, resolves last)
        val forageRow = compose.onNodeWithContentDescription("Trigger: $forage")
        forageRow.fetchSemanticsNode().config.getOrElse(androidx.compose.ui.semantics.SemanticsActions.CustomActions) { emptyList() }
            ?.firstOrNull { it.label == "Move up" }?.let { up -> compose.runOnIdle { up.action() } }
        compose.waitForIdle()
        assertTrue(compose.onNodeWithContentDescription("Trigger: $forage").fetchSemanticsNode().boundsInRoot.top <
            compose.onNodeWithContentDescription("Trigger: $offspring", substring = true).fetchSemanticsNode().boundsInRoot.top, "forage on top")
        compose.onNodeWithText("OK").performClick()
        refresh()

        // 2. The original's forage resolves first: the question shows its sentence and its cost (CR 118.12)
        untilDecision<Decision.May>("the original's forage question")
        assertEquals(1, kit.state().stack.size - 1, "the offspring trigger waits under the resolving forage trigger")
        compose.onNode(hasText("You may pay") and isHeading()).assertExists()
        compose.onNodeWithContentDescription("Question: You may forage. If you do, put two +1/+1 counters on it.").assertExists()
        compose.onNodeWithContentDescription("Cost: Forage").assertExists()
        captureShot("$SCREENSHOTS/questions/may-forage.png",
            "The Sentry's forage trigger resolves: the panel above Alice's hand is titled \"You may pay\", says \"You may forage. If you do, put two +1/+1 counters on it.\" with a \"Cost\" chip reading Forage, the trigger's token at its right, and No and Yes at its bottom.")
        // Meanwhile holding the offspring trigger on the Stack shows its keyword's line (STK-050)
        holdInStackList("2 · ${Choices.SENTRY}'s ability", "Offspring {R}", "$SCREENSHOTS/questions/offspring-stack-inspect.png",
            "While the forage question is open, Alice holds the Sentry's offspring trigger waiting under it in the Stack list: Inspect shows the Sentry's art, \"Triggered ability\" and its offspring line with the reminder text.")
        compose.onNodeWithText("Yes").performClick()
        refresh()

        // 3. Which way to forage, saying what foraging is (CR 701.61a)
        val way = untilDecision<Decision.ChooseOption>("which way to forage")
        assertEquals(listOf("exile", "sacrifice"), way.options)
        compose.onNodeWithContentDescription("Question: $forage").assertExists()
        compose.onNodeWithText("To forage, exile three cards from your graveyard or sacrifice a Food.").assertExists()
        captureShot("$SCREENSHOTS/questions/forage-way.png",
            "After Yes, the panel asks \"Choose one\" under the trigger's sentence, saying \"To forage, exile three cards from your graveyard or sacrifice a Food.\", with Exile and Sacrifice buttons.")
        compose.onNodeWithText("Exile").performClick()
        refresh()

        // 4. The three cards to exile among the four
        val cards = untilDecision<Decision.ChooseObjects>("the cards to exile")
        assertEquals(3, cards.min)
        assertEquals(4, cards.options.size)
        compose.onNode(hasText("Exile") and isHeading()).assertExists()
        compose.onNodeWithContentDescription("Question: $forage").assertExists()
        val inPanel = hasAnyAncestor(hasContentDescription("Automatum choice"))
        // The first three of the graveyard's cards, by name: a picked card reads "<name>, chosen"
        val names = cards.options.take(3).map { kit.state().obj(it)?.definition?.faces?.first()?.name.orEmpty() }
        for (name in names) compose.onAllNodes(hasContentDescription(name) and inPanel, useUnmergedTree = true).onFirst().performClick()
        compose.waitForIdle()
        captureShot("$SCREENSHOTS/questions/forage-exile.png",
            "Exiling is chosen: the panel titled \"Exile\" under the trigger's sentence asks for three of the four Hedge Studies in Alice's graveyard, three of them picked.")
        compose.onNodeWithText("OK").performClick()
        refresh()
        val original = kit.state().battlefield.single { (it as? ObjectId.Card)?.card?.value == sentry }
        assertEquals(2, kit.state().obj(original)?.counters(CounterKind.PowerToughness(1, 1)), "the Sentry has its two counters")
        assertEquals(3, kit.state().zone(arcanum.api.Zone.Exile, kit.me).size, "three cards exiled")

        // 5. The offspring trigger resolves: a 1/1 token copy (without the counters) enters and only its own forage ability
        // triggers (CR 707.2, CR 603.4); while it asks, holding it on the Stack shows its sentence
        untilDecision<Decision.May>("the token's forage question")
        val token = kit.state().battlefield.single { kit.state().obj(it)?.token == true }
        assertEquals(token, kit.state().stackItems[kit.state().stack.last()]?.source, "the token's own trigger")
        assertEquals(0, kit.state().obj(token)?.counters(CounterKind.PowerToughness(1, 1)))
        holdInStackList("Top · ${Choices.SENTRY}'s ability", "you may forage", "$SCREENSHOTS/questions/token-forage-stack-inspect.png",
            "The offspring trigger made a 1/1 token copy of the Sentry and only the token's forage ability triggered: while its question is open, holding it on the Stack shows its forage sentence.")

        // 6. Declined: nothing paid, nothing put on the token
        compose.onNodeWithText("No").performClick()
        refresh()
        kit.playOn("the Stack empty") { kit.state().stack.isEmpty() && pending() == null }
        assertEquals(0, kit.state().obj(token)?.counters(CounterKind.PowerToughness(1, 1)), "declined: no counters")
        assertTrue(kit.field(Choices.PIE).isNotEmpty(), "the Food is still there")
    }

    /** Plays on (Alice passing when she holds priority with nothing asked) until Alice's open decision is a [T]. */
    private inline fun <reified T : Decision> untilDecision(what: String): T {
        kit.playOn(what, rounds = 40) {
            if (kit.state().stack.isNotEmpty() && kit.state().priority.holder == kit.me && pending() == null) go()
            pending() is T
        }
        refresh()
        return assertIs<T>(pending())
    }
}
