package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
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
 * his table held there until he taps OK. Alice's own reveal opens nothing on her table.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class RevealPanelTest {
    @get:Rule val compose = createComposeRule()
    private val f = EngineGameFixture(aliceCards = listOf(EngineGameFixture.Choices.INSIGHT, "Mountain"))
    private val names = mapOf(f.a to "Alice", f.b to "Bob")

    @After fun forget() = AutomatumBoards.reset()

    private fun render(game: EngineGame, viewer: String) {
        val routes = EngineTableRoutes(viewer, { game }, {}, { game.play() }).apply { opponentName = names.getValue(if (viewer == f.a) f.b else f.a) }
        val toolkit = TableToolkitController(viewer, { game.play() }, { routes.canAct() }, { action, _ -> routes.local(listOf(action)) },
            playBatch = { actions, _ -> routes.local(actions) }, engine = routes)
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides routes) {
                        Box(Modifier.fillMaxSize()) {
                            TableSurface(f.game, game.play(), viewer, false, Modifier.fillMaxSize(), onIntent = toolkit::handle, toolkit = toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, waitingLine = routes.waitingLine(game.play())))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Alice at her first main phase (Bob kept and is away) casts her Ember Insight; it resolves on his assumed pass. */
    private fun aliceCastsInsight(): EngineGame {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot(otherKept = true)).apply { tick(1_000); keep(emptyList()); tick(2_000) }
        val me = PlayerId(f.a)
        val insight = game.device.model.engine.let { s ->
            s.player(me).hand.first { s.obj(it)?.definition?.faces?.first()?.name == EngineGameFixture.Choices.INSIGHT }
        }
        val copy = game.device.model.engine.deepCopy()
        val inputs = mutableListOf<Input>(Input.Cast(me, insight))
        var next: Input = inputs.first()
        while (true) {
            Engine.perform(copy, next).changes.forEach(copy::apply)
            val d = copy.pending?.decision ?: break
            if (d is Decision.Random || Engine.decider(copy, d.player) != me) break
            next = if (d is Decision.ManaPayment) Input.Answer(d.id, Answer.ManaPayment(d.suggestion))
                else Engine.options(copy, me).filterIsInstance<Input.Answer>().first { it.decision == d.id }
            inputs += next
        }
        assertIs<GameDevice.Acted.Sent>(game.send(inputs))
        game.tick(3_000)
        return game
    }

    // HIS-110, STK-044, TBL-104: Bob's playback stops right after Alice's reveal: the panel above his hand names her and the
    // Insight, shows the revealed card face up and the Insight at its right; the table stays there (the later events held)
    // until OK, after which playback goes on
    @Test fun theOtherPlayersRevealStopsPlaybackUntilOk() {
        val alice = aliceCastsInsight()
        assertNull(alice.shown?.reveal, "her own reveal opens nothing")
        alice.endTurn()
        alice.tick(5_000)
        val bob = EngineGame(f.game.id, f.b, f.base, f.behind(alice.device.session.pending, alice.device.model))
        bob.startPlayback()
        var guard = 0
        while (bob.shown?.status is GameDevice.Status.Playing && bob.shown?.reveal == null && guard++ < 200) bob.step()
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

    // HIS-110: Alice's own reveal opens no panel on her table
    @Test fun yourOwnRevealOpensNothing() {
        val alice = aliceCastsInsight()
        assertNull(alice.shown?.reveal)
        render(alice, f.a)
        compose.onNodeWithTag(REVEAL_OK_TAG).assertDoesNotExist()
    }
}
