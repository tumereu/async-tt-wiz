package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
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
 * HIS-110 (owner playtest 2026-11-07): Alice casts an Ember Insight ("Reveal the top card of your library and put that
 * card into your hand.") while Bob is away; Bob's catch-up playback stops at the reveal with its panel above his hand,
 * his table held there until he taps OK. Alice's own reveal opens her own panel (owner, 2026-11-09), her OK her own.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class RevealPanelTest {
    @get:Rule val compose = createComposeRule()
    private val f = EngineGameFixture(aliceCards = listOf(EngineGameFixture.Choices.INSIGHT, "Mountain"))
    private val names = mapOf(f.a to "Alice", f.b to "Bob")

    @After fun forget() {
        AutomatumBoards.reset()
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("card-appearance", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** Bumped to compose the table again on the game's newest board ([refresh]). */
    private val frame = mutableIntStateOf(0)
    private fun refresh() { compose.runOnIdle { frame.intValue++ }; compose.waitForIdle() }

    private fun render(game: EngineGame, viewer: String, pump: () -> Unit = {}) {
        val routes = EngineTableRoutes(viewer, { game }, pump, { game.play() }).apply { opponentName = names.getValue(if (viewer == f.a) f.b else f.a) }
        val toolkit = TableToolkitController(viewer, { game.play() }, { routes.canAct() }, { action, _ -> routes.local(listOf(action)) },
            playBatch = { actions, _ -> routes.local(actions) }, engine = routes)
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides routes) {
                        key(frame.intValue) {
                            Box(Modifier.fillMaxSize()) {
                                TableSurface(f.game, game.play(), viewer, false, Modifier.fillMaxSize(), onIntent = toolkit::handle, toolkit = toolkit,
                                    runtime = TableRuntimeHooks(navigation = {}, chat = {}, waitingLine = routes.waitingLine(game.play())))
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Alice at her first main phase (Bob kept and is away) casts her Ember Insight; it resolves on his assumed pass. */
    private fun aliceCastsInsight(): EngineGame {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot(otherKept = true)).apply { tick(1_000); keep(); tick(2_000) }
        assertIs<GameDevice.Acted.Sent>(game.send(cast(game, EngineGameFixture.Choices.INSIGHT)))
        game.tick(3_000)
        return game
    }

    /** [game]'s player's cast of the hand card [name] with their own answers, as the cast panel makes it. */
    private fun cast(game: EngineGame, name: String): List<Input> {
        val me = PlayerId(game.me)
        val card = game.device.model.engine.let { s -> s.player(me).hand.first { s.obj(it)?.definition?.faces?.first()?.name == name } }
        val copy = game.device.model.engine.deepCopy()
        val inputs = mutableListOf<Input>(Input.Cast(me, card))
        var next: Input = inputs.first()
        while (true) {
            Engine.perform(copy, next).changes.forEach(copy::apply)
            val d = copy.pending?.decision ?: break
            if (d is Decision.Random || Engine.decider(copy, d.player) != me) break
            next = if (d is Decision.ManaPayment) Input.Answer(d.id, Answer.ManaPayment(d.suggestion))
                else Engine.options(copy, me).filterIsInstance<Input.Answer>().first { it.decision == d.id }
            inputs += next
        }
        return inputs
    }

    /** Bob, catching up on Alice's turn in which she cast her Ember Insight, stopped at her reveal. */
    private fun bobAtReveal(): EngineGame {
        val alice = aliceCastsInsight()
        assertNotNull(alice.shown?.reveal, "her own panel")
        alice.dismissReveal()
        alice.endTurn()
        alice.tick(5_000)
        val bob = EngineGame(f.game.id, f.b, f.base, f.behind(alice.device.session.pending, alice.device.model))
        bob.startPlayback()
        var guard = 0
        while (bob.shown?.status is GameDevice.Status.Playing && bob.shown?.reveal == null && guard++ < 200) bob.step()
        return bob
    }

    /** The main button shown now: the step arrow, or the wait's Assume while the other player must act. */
    private fun mainButton(): SemanticsNodeInteraction =
        compose.onNode(hasTestTag("main-button") or hasTestTag("engine-wait-action"))

    /** What holding the main button says, or null when it says none of [reasons]. */
    private fun holdingMainButtonSays(vararg reasons: String): String? {
        mainButton().performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val said = reasons.firstOrNull { compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
        mainButton().performTouchInput { up() }
        compose.waitForIdle()
        return said
    }

    // HIS-110, STK-044, TBL-104: Bob's playback stops right after Alice's reveal: the panel above his hand names her and the
    // Insight, shows the revealed card face up and the Insight at its right; the table stays there (the later events held)
    // until OK, after which playback goes on
    @Test fun theOtherPlayersRevealStopsPlaybackUntilOk() {
        val bob = bobAtReveal()
        var guard = 0
        val reveal = assertNotNull(bob.shown?.reveal, "stopped at Alice's reveal")
        assertEquals(PlayerId(f.a), reveal.by)
        assertTrue(bob.device.session.behind, "her later events wait")
        val held = bob.device.session.localHead
        render(bob, f.b)
        compose.onNodeWithText("Alice revealed · ${EngineGameFixture.Choices.INSIGHT}").assertExists()
        val name = bob.play().board.cards.first { it.id == reveal.cards.single().value }.let { c ->
            bob.play().definitions.first { it.digest == c.definition }.definition.faces.first().name
        }
        compose.onNode(hasContentDescription(name) and hasAnyAncestor(hasContentDescription("Reveal"))).assertExists()
        compose.onNodeWithTag(CHOICE_SOURCE_TAG).assertContentDescriptionEquals(EngineGameFixture.Choices.INSIGHT)
        captureShot("$SCREENSHOTS/engine/reveal-panel.png",
            "Bob catching up on Alice's turn: her Ember Insight revealed her top card ($name), so his playback stopped right after it with the reveal panel above his hand, \"Alice revealed · Ember Insight\", the revealed card face up, the Insight at the right, the eye and OK; the table waits there until he taps OK.")
        repeat(3) { bob.step() }
        assertEquals(held, bob.device.session.localHead, "the table stays at the reveal")
        compose.onNodeWithTag(REVEAL_OK_TAG).performClick()
        compose.runOnIdle { assertNull(bob.shown?.reveal) }
        while (bob.shown?.status is GameDevice.Status.Playing && guard++ < 400) bob.step()
        assertTrue(bob.device.session.localHead != held, "playback went on after OK: ${bob.shown?.status} behind=${bob.device.session.behind} buffered=${bob.device.session.buffered.size}")
    }

    // HIS-110, TRN-063, STK-029 (owner, 2026-11-07): while the reveal panel is open the main button is greyed: a tap does
    // nothing (it shakes) and holding it says why; after OK it acts again
    @Test fun theMainButtonIsGreyedUntilOk() {
        // Bob starts (Control: he holds priority wherever he gets it) with free Ember Glints, Alice holds free Ember Foresights
        val g = EngineGameFixture(startingPlayer = f.b, aliceCards = listOf(EngineGameFixture.Choices.FORESIGHT),
            bobCards = listOf(EngineGameFixture.Choices.GLINT))
        val sim = EngineAppSim(g)
        val alice = sim.load(g.a)
        val bob = sim.load(g.b)
        fun settle() = repeat(20) {
            for (p in listOf(g.a, g.b)) { sim.deliver(p); sim.game(p).playToStop(); sim.game(p).tick(sim.now); sim.flush(p) }
        }
        assertIs<GameDevice.Acted.Sent>(alice.keep())
        settle()
        assertIs<GameDevice.Acted.Sent>(bob.keep())
        settle()
        assertEquals(GameDevice.Status.YourMove, bob.shown?.status, "Bob in his first main phase")
        bob.settings(org.asyncttwiz.protocol.game.PlaySettings(mode = org.asyncttwiz.protocol.game.ResponseMode.FullControl))
        // Bob casts a Glint and passes; Alice responds with her Foresight, and Bob lets it resolve: her reveal, after which
        // he holds priority again with his Glint on the Stack
        assertIs<GameDevice.Acted.Sent>(bob.send(cast(bob, EngineGameFixture.Choices.GLINT)))
        settle()
        assertEquals(PlayerId(g.b), bob.device.model.engine.priority?.holder, "in Control he keeps priority after his cast")
        bob.pass()
        settle()
        assertEquals(GameDevice.Status.YourMove, alice.shown?.status, "Alice may respond")
        assertIs<GameDevice.Acted.Sent>(alice.send(cast(alice, EngineGameFixture.Choices.FORESIGHT)))
        settle()
        assertNull(bob.shown?.reveal)
        assertEquals(2, bob.shown?.engine?.stack?.size, "her Foresight on his Glint")
        bob.pass()
        settle()
        assertNotNull(bob.shown?.reveal, "Alice's reveal open on Bob's table")
        assertEquals(1, bob.shown?.engine?.stack?.size, "his Glint waits on the Stack")
        // The hand card size at 2x for the shot: the revealed card drawn at it (owner, 2026-11-07)
        CardAppearancePreferences(ApplicationProvider.getApplicationContext()).apply { customizeAreas(true); setSize(2f, CardArea.HAND) }
        render(bob, g.b) { settle() }
        compose.onNodeWithTag("main-button").assert(hasContentDescription("Resolve"))
        val version = bob.shown?.version
        compose.onNodeWithTag("main-button").performClick()
        compose.runOnIdle {
            assertEquals(version, bob.shown?.version, "a tap moves nothing")
            assertNotNull(bob.shown?.reveal, "the panel stays open")
        }
        compose.onNodeWithTag("main-button").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithText(REVEAL_BUSY).assertExists()
        captureShot("$SCREENSHOTS/engine/reveal-panel-greyed.png",
            "Bob's turn: Alice answered his Ember Glint with her Ember Foresight, which revealed her top card; he let it resolve, so the reveal panel shows that card at his hand's card size (set to 2x) and his main button, Resolve, is greyed; held, it says to dismiss the reveal first.")
        compose.onNodeWithTag("main-button").performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithTag(REVEAL_OK_TAG).performClick()
        compose.runOnIdle { assertNull(bob.shown?.reveal) }
        refresh()
        compose.onNodeWithTag(REVEAL_OK_TAG).assertDoesNotExist()
        compose.onNodeWithTag("main-button").assertExists()
        assertEquals(null, holdingMainButtonSays(REVEAL_BUSY), "no longer greyed")
        val before = bob.shown?.version
        compose.onNodeWithTag("main-button").performClick()
        compose.runOnIdle { assertTrue(bob.shown?.version != before, "the main button acted after OK") }
    }

    // HIS-110, TBL-034 (owner, 2026-11-07): the revealed cards use the Hand card size (28 x 40 dp times it), not the Panels
    // size; the source's card stays at the Panels size
    @Test fun revealedCardsFollowTheHandCardSize() {
        val preferences = CardAppearancePreferences(ApplicationProvider.getApplicationContext())
        preferences.customizeAreas(true)
        preferences.setSize(2f, CardArea.HAND)
        preferences.setSize(1f, CardArea.PANELS)
        val bob = bobAtReveal()
        val reveal = assertNotNull(bob.shown?.reveal)
        render(bob, f.b)
        val name = bob.play().board.cards.first { it.id == reveal.cards.single().value }.let { c ->
            bob.play().definitions.first { it.digest == c.definition }.definition.faces.first().name
        }
        val card = compose.onNode(hasContentDescription(name) and hasAnyAncestor(hasContentDescription("Reveal")))
        card.assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(80.dp)
        compose.onNodeWithTag(CHOICE_SOURCE_TAG).assertWidthIsEqualTo(28.dp)
        compose.runOnIdle { preferences.setSize(1.5f, CardArea.HAND) }
        compose.waitForIdle()
        card.assertWidthIsEqualTo(42.dp).assertHeightIsEqualTo(60.dp)
    }

    // HIS-110 (owner, 2026-11-09): Alice's own reveal of a card she couldn't see opens her own panel: "You revealed · <source>",
    // the card, OK; her table's actions wait for her OK, and it closes only for her
    @Test fun yourOwnRevealShowsYouThePanelWithItsOk() {
        val alice = aliceCastsInsight()
        val reveal = assertNotNull(alice.shown?.reveal)
        assertEquals(PlayerId(f.a), reveal.by)
        render(alice, f.a)
        compose.onNodeWithText("You revealed · ${EngineGameFixture.Choices.INSIGHT}").assertExists()
        compose.onNodeWithTag(REVEAL_OK_TAG).assertExists()
        captureShot("$SCREENSHOTS/engine/reveal-panel-own.png", "Alice cast her own Ember Insight: it revealed her top card, so the " +
            "panel above her hand says \"You revealed\" with the card and the Insight, and her table waits for her own OK.")
        compose.onNodeWithTag(REVEAL_OK_TAG).performClick()
        refresh()
        compose.onNodeWithTag(REVEAL_OK_TAG).assertDoesNotExist()
    }
}
