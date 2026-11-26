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
import androidx.compose.ui.test.junit4.v2.createComposeRule
import arcanum.engine.Engine
import arcanum.engine.Host
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
import org.asyncttwiz.appui.AppPlatform

/**
 * HIS-110, HIS-153, HIS-154 (owner playtest 2026-11-07): Alice casts an Ember Insight ("Reveal the top card of your library and put that
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

    private fun render(game: EngineGame, viewer: String, landscape: Boolean = false, pump: () -> Unit = {}) {
        val routes = EngineTableRoutes(viewer, { game }, pump, { game.play() }).apply { opponentName = names.getValue(if (viewer == f.a) f.b else f.a) }
        val toolkit = TableToolkitController(viewer, { game.play() }, { routes.canAct() }, { action, _ -> routes.local(listOf(action)) },
            playBatch = { actions, _ -> routes.local(actions) }, engine = routes)
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides routes) {
                        key(frame.intValue) {
                            Box(Modifier.fillMaxSize()) {
                                TableSurface(f.game, game.play(), viewer, landscape, Modifier.fillMaxSize(), onIntent = toolkit::handle, toolkit = toolkit,
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
            Engine.perform(copy, next, org.asyncttwiz.protocol.game.EnginePreferences.of(copy)).changes.forEach(copy::apply)
            val d = copy.pending?.decision ?: break
            if (d is Decision.Random || Host.decider(copy, d.player) != me) break
            next = if (d is Decision.ManaPayment) Input.Answer(d.id, Answer.ManaPayment(d.suggestion))
                else Engine.options(copy, me, org.asyncttwiz.protocol.game.EnginePreferences.of(copy)).filterIsInstance<Input.Answer>().first { it.decision == d.id }
            inputs += next
        }
        return inputs
    }

    /** Bob, catching up on Alice's turn in which she cast her Ember Insight, stopped at her reveal. */
    private fun bobAtReveal(): EngineGame {
        val alice = aliceCastsInsight()
        assertNotNull(alice.shown?.reveal, "her own panel")
        alice.dismissReveal(alice.shown!!.reveal!!, 0L)
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

    /** HIS-110, HIS-151, TRN-063, TRN-114 (owner playtest 2026-11-09): the reveal panel's OK, the main button while the panel is open. */
    private fun revealOk(): SemanticsNodeInteraction = compose.onNode(hasTestTag("main-button") and hasContentDescription("OK"))

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

    // HIS-110, HIS-150, HIS-151, HIS-153, STK-044, STK-178, TBL-104: Bob's playback stops right after Alice's reveal: the panel above his hand names her and the
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
            "Bob catching up on Alice's turn: her Ember Insight revealed her top card ($name), so his playback stopped right after it with the reveal panel above his hand, \"Alice revealed · Ember Insight\", the revealed card face up, the Insight at the right and the eye, with OK on his main button; the table waits there until he taps it.")
        repeat(3) { bob.step() }
        assertEquals(held, bob.device.session.localHead, "the table stays at the reveal")
        revealOk().performClick()
        compose.runOnIdle { assertNull(bob.shown?.reveal) }
        while (bob.shown?.status is GameDevice.Status.Playing && guard++ < 400) bob.step()
        assertTrue(bob.device.session.localHead != held, "playback went on after OK: ${bob.shown?.status} behind=${bob.device.session.behind} buffered=${bob.device.session.buffered.size}")
    }

    // HIS-110, HIS-151, TRN-063, TRN-114, STK-029 (owner playtest 2026-11-09): while the reveal panel is open the main button is its OK (Resolve
    // waits); a tap on it dismisses the reveal, and the main button is Resolve again and acts
    // STK-044 (owner, 2026-11-26): in landscape the reveal panel stands at the left on top of the bottom bar, nothing of the bar covered
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun theRevealPanelInLandscapeStandsOnTheBar() {
        val bob = bobAtReveal()
        assertNotNull(bob.shown?.reveal, "stopped at Alice's reveal")
        render(bob, f.b, landscape = true)
        val box = compose.onNodeWithContentDescription("Reveal").fetchSemanticsNode().boundsInRoot
        val barTop = 412f - TableControlsHeight
        assertTrue(kotlin.math.abs(box.bottom - barTop) < 1f && box.left < 1f && box.width <= 421f, "on the bar at the left: $box")
        captureShot("$SCREENSHOTS/landscape-panels/reveal.png",
            "In landscape Bob catching up on Alice's turn stopped at her Ember Insight's reveal: the reveal panel stands at the left on " +
                "top of the bottom bar, his life square, piles and hand in view under it.")
    }

    @Test fun theMainButtonIsTheRevealsOk() {
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
        CardAppearancePreferences(AppPlatform.current).apply { customizeAreas(true); setSize(2f, CardArea.HAND) }
        render(bob, g.b) { settle() }
        revealOk().assertExists()
        captureShot("$SCREENSHOTS/engine/reveal-panel-greyed.png",
            "Bob's turn: Alice answered his Ember Glint with her Ember Foresight, which revealed her top card; he let it resolve, so the reveal panel shows that card at his hand's card size (set to 2x) and his main button is the reveal's OK, Resolve waiting behind it.")
        revealOk().performClick()
        compose.runOnIdle { assertNull(bob.shown?.reveal) }
        refresh()
        compose.onNodeWithTag("main-button").assert(hasContentDescription("Resolve"))
        assertEquals(null, holdingMainButtonSays(REVEAL_BUSY), "no longer greyed")
        // The hold just made is Resolve's own (the Resolve chain): the tap is on the board it led to, as the table draws it (a tap
        // on the board before it would be one the game has moved past, not taken: problem report B7UZB5)
        refresh()
        val before = bob.shown?.version
        // TRN-063, TRN-117: a tap right after the OK would be ignored as the second tap of a double tap
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(MainButtonGuard.MILLIS))
        compose.onNodeWithTag("main-button").performClick()
        compose.runOnIdle { assertTrue(bob.shown?.version != before, "the main button acted after OK") }
    }

    // HIS-110, HIS-151, TBL-034 (owner, 2026-11-07): the revealed cards use the Hand card size (28 x 40 dp times it), not the Panels
    // size; the source's card stays at the Panels size
    @Test fun revealedCardsFollowTheHandCardSize() {
        val preferences = CardAppearancePreferences(AppPlatform.current)
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

    // HIS-114, HIS-157, HIS-110, STK-044, STK-178 (owner playtest 2026-11-09): Alice casts a Hedge Study and then a Hedge Recall returning the
    // Study from her graveyard to her hand; Bob's playback stops there with the reveal panel naming the move and showing only
    // the Study, the Recall at its right, until his OK. Alice's own move opens nothing of hers
    @Test fun theOtherPlayersCardLeavingTheirGraveyardForTheirHandShowsAsAReveal() {
        val g = EngineGameFixture(aliceCards = listOf(EngineGameFixture.Choices.STUDY, EngineGameFixture.Choices.RECALL))
        val alice = EngineGame(g.game.id, g.a, g.base, g.snapshot(otherKept = true)).apply { tick(1_000); keep(); tick(2_000) }
        assertIs<GameDevice.Acted.Sent>(alice.send(cast(alice, EngineGameFixture.Choices.STUDY)))
        alice.tick(3_000)
        val study = alice.device.model.engine.player(PlayerId(g.a)).graveyard.single()
        assertIs<GameDevice.Acted.Sent>(alice.send(cast(alice, EngineGameFixture.Choices.RECALL)))
        alice.tick(4_000)
        assertNull(alice.shown?.reveal, "her own move opens nothing of hers")
        assertTrue(study.let { (it as arcanum.engine.state.ObjectId.Card).card } in alice.device.model.engine.player(PlayerId(g.a)).hand
            .map { (it as arcanum.engine.state.ObjectId.Card).card }, "the Study back in her hand")
        alice.endTurn()
        alice.tick(5_000)
        val bob = EngineGame(g.game.id, g.b, g.base, g.behind(alice.device.session.pending, alice.device.model))
        bob.startPlayback()
        var guard = 0
        while (bob.shown?.status is GameDevice.Status.Playing && bob.shown?.reveal == null && guard++ < 200) bob.step()
        val panel = assertNotNull(bob.shown?.reveal, "stopped at her move")
        assertEquals(GameDevice.LeftPile(arcanum.api.Zone.Graveyard, arcanum.api.Zone.Hand, PlayerId(g.a)), panel.left)
        val held = bob.device.session.localHead
        render(bob, g.b)
        compose.onNodeWithText("Alice returned ${EngineGameFixture.Choices.STUDY} from their graveyard to their hand").assertExists()
        compose.onNode(hasContentDescription(EngineGameFixture.Choices.STUDY) and hasAnyAncestor(hasContentDescription("Reveal"))).assertExists()
        assertEquals(1, compose.onAllNodes(hasAnyAncestor(hasContentDescription("Reveal")) and hasContentDescription(EngineGameFixture.Choices.RECALL))
            .fetchSemanticsNodes().size, "the Recall only as the source")
        compose.onNodeWithTag(CHOICE_SOURCE_TAG).assertContentDescriptionEquals(EngineGameFixture.Choices.RECALL)
        captureShot("$SCREENSHOTS/engine/reveal-panel-pile.png",
            "Bob catching up on Alice's turn: her Hedge Recall returned her Hedge Study from her graveyard to her hand, so his playback stopped there with the reveal panel, \"Alice returned Hedge Study from their graveyard to their hand\", only the Study face up, the Recall at the right and OK on his main button.")
        repeat(3) { bob.step() }
        assertEquals(held, bob.device.session.localHead, "the table stays at the move")
        revealOk().performClick()
        compose.runOnIdle { assertNull(bob.shown?.reveal) }
    }

    // HIS-233, HIS-110, STK-044, STK-178 (owner 2026-11-19, N18): Alice's Hedge Delve exiles the top two cards of her library; Bob's
    // playback stops there with the reveal panel naming the exile and showing those two cards face up, the Delve at its right,
    // until his OK; her own phone shows her the same panel ("You exiled 2 cards from your library")
    @Test fun cardsExiledFromALibraryShowAsAReveal() {
        val g = EngineGameFixture(aliceCards = listOf(EngineGameFixture.Choices.DELVE, "Mountain"))
        val alice = EngineGame(g.game.id, g.a, g.base, g.snapshot(otherKept = true)).apply { tick(1_000); keep(); tick(2_000) }
        val top = alice.device.model.engine.player(PlayerId(g.a)).library.take(2).map { (it as arcanum.engine.state.ObjectId.Card).card }
        assertIs<GameDevice.Acted.Sent>(alice.send(cast(alice, EngineGameFixture.Choices.DELVE)))
        alice.tick(3_000)
        val own = assertNotNull(alice.shown?.reveal, "her own panel")
        assertEquals(GameDevice.LibraryExile(PlayerId(g.a)), own.exiled)
        assertEquals(top.toSet(), own.cards.toSet())
        alice.dismissReveal(own, 3_500)
        alice.tick(4_000)
        alice.endTurn()
        alice.tick(5_000)
        val bob = EngineGame(g.game.id, g.b, g.base, g.behind(alice.device.session.pending, alice.device.model))
        bob.startPlayback()
        var guard = 0
        while (bob.shown?.status is GameDevice.Status.Playing && bob.shown?.reveal == null && guard++ < 200) bob.step()
        val panel = assertNotNull(bob.shown?.reveal, "stopped at her exile")
        assertEquals(GameDevice.LibraryExile(PlayerId(g.a)), panel.exiled)
        render(bob, g.b)
        compose.onNodeWithText("Alice exiled 2 cards from their library").assertExists()
        compose.onNodeWithTag(CHOICE_SOURCE_TAG).assertContentDescriptionEquals(EngineGameFixture.Choices.DELVE)
        captureShot("$SCREENSHOTS/engine/reveal-panel-library-exile.png",
            "Bob catching up on Alice's turn: her Hedge Delve exiled the top two cards of her library, so his playback stopped there with the reveal panel, \"Alice exiled 2 cards from their library\", both cards face up, the Delve at the right and OK on his main button.")
        revealOk().performClick()
        compose.runOnIdle { assertNull(bob.shown?.reveal) }
    }

    /** Alice (Bob kept and is away) casts [spell] from a deck of it, Mountains, Ash Pups and Ember Divinations: it resolves on his
     * assumed pass up to her choice among the top four cards, open on her phone. */
    private fun aliceChoosesAmong(spell: String): Pair<EngineGameFixture, EngineGame> {
        val g = EngineGameFixture(aliceCards = listOf(spell, "Mountain", EngineGameFixture.Choices.PUP, EngineGameFixture.Choices.DIVINATION))
        val alice = EngineGame(g.game.id, g.a, g.base, g.snapshot(otherKept = true)).apply { tick(1_000); keep(); tick(2_000) }
        assertIs<GameDevice.Acted.Sent>(alice.send(cast(alice, spell)))
        alice.tick(3_000)
        val d = assertIs<Decision.ChooseObjects>(alice.device.model.engine.pending?.decision, "her choice among the cards")
        assertTrue(d.options.isNotEmpty() && d.others.isNotEmpty(), "a Pup to choose, other cards beside it: $d")
        return g to alice
    }

    private val inChoice = hasAnyAncestor(hasContentDescription("Automatum choice"))

    /** The name of the card of [alice]'s engine object [id]. */
    private fun nameOf(alice: EngineGame, id: arcanum.engine.state.ObjectId) =
        alice.device.model.engine.obj(id)!!.definition!!.faces.first().name

    // STK-224, STK-225, HIS-155, STK-190: Alice's Ember Unveiling reveals her top four cards: no reveal panel of hers opens, only the choice,
    // which shows the Ash Pup to choose and the other revealed cards under "Other cards", dimmed and not choosable
    @Test fun aRevealThenChooseIsTheChoosersOnePanel() {
        val (g, alice) = aliceChoosesAmong(EngineGameFixture.Choices.UNVEILING)
        assertNull(alice.shown?.reveal, "no reveal panel of hers")
        val d = alice.device.model.engine.pending?.decision as Decision.ChooseObjects
        render(alice, g.a)
        compose.onNode(hasText("Other cards") and isHeading(), useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Other cards, ${d.others.size}", useUnmergedTree = true).assertExists()
        compose.onAllNodes(hasContentDescription("Reveal")).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription(EngineGameFixture.Choices.PUP) and inChoice, useUnmergedTree = true).assertCountEquals(d.options.size)
        val other = d.others.map { nameOf(alice, it) }.first { it != EngineGameFixture.Choices.UNVEILING }
        compose.onAllNodes(hasContentDescription(other) and inChoice, useUnmergedTree = true).onFirst().performClick()
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasContentDescription("$other, chosen"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
            "an other card isn't chosen")
        compose.onRoot().captureShot("$SCREENSHOTS/engine/choice-among-revealed.png",
            "Alice cast her Ember Unveiling: it revealed her top four cards, and instead of a reveal panel first she gets one choice panel " +
                "showing all four: the Ash Pup to choose, and the cards she can't choose dimmed under \"Other cards\".")
    }

    // STK-225, HIS-110, HIS-150: Bob catching up on the same Unveiling still gets the reveal stop and panel with all four cards
    @Test fun theOtherPlayerStillGetsTheRevealOfAChoiceAmongRevealedCards() {
        val (g, alice) = aliceChoosesAmong(EngineGameFixture.Choices.UNVEILING)
        val bob = EngineGame(g.game.id, g.b, g.base, g.behind(alice.device.session.pending, alice.device.model))
        bob.startPlayback()
        var guard = 0
        while (bob.shown?.status is GameDevice.Status.Playing && bob.shown?.reveal == null && guard++ < 200) bob.step()
        val reveal = assertNotNull(bob.shown?.reveal, "stopped at her reveal")
        assertEquals(4, reveal.cards.size)
        render(bob, g.b)
        compose.onNodeWithText("Alice revealed · ${EngineGameFixture.Choices.UNVEILING}").assertExists()
        revealOk().assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/engine/choice-among-revealed-other.png",
            "Bob catching up on the same turn: Alice's Ember Unveiling revealed her top four cards, so his playback stopped there with the " +
                "reveal panel, \"Alice revealed · Ember Unveiling\", all four cards face up and OK on his main button; her choice among them is hers alone.")
    }

    /** STK-230: the text highlighted in the open panel, or null when nothing is. */
    private fun highlighted(): String? = compose.onAllNodes(hasContentDescription("Highlighted: ", substring = true))
        .fetchSemanticsNodes().singleOrNull()?.config?.get(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)
        ?.single()?.removePrefix("Highlighted: ")

    /** Bob catching up on [alice]'s events of [g], stopped at her first reveal. */
    private fun bobCatchesUp(g: EngineGameFixture, alice: EngineGame): EngineGame {
        val bob = EngineGame(g.game.id, g.b, g.base, g.behind(alice.device.session.pending, alice.device.model))
        bob.startPlayback()
        var guard = 0
        while (bob.shown?.status is GameDevice.Status.Playing && bob.shown?.reveal == null && guard++ < 200) bob.step()
        assertNotNull(bob.shown?.reveal, "stopped at her reveal")
        return bob
    }

    // HIS-252, STK-230, HIS-150: Bob's reveal panel of Alice's Ember Unveiling shows the spell's whole text under its title, the sentence
    // that revealed the cards shining as the target bar's phrase picked now does
    @Test fun theRevealPanelShowsItsSourcesTextWithTheRevealHighlighted() {
        val (g, alice) = aliceChoosesAmong(EngineGameFixture.Choices.UNVEILING)
        val bob = bobCatchesUp(g, alice)
        assertNotNull(bob.shown?.reveal?.item, "the Unveiling's Stack item with the reveal")
        render(bob, g.b)
        compose.onNodeWithText("Alice revealed · ${EngineGameFixture.Choices.UNVEILING}").assertExists()
        compose.onNodeWithContentDescription("Ability: ", substring = true).assertExists()
        assertEquals("Reveal the top four cards of your library.", highlighted())
        compose.onRoot().captureShot("$SCREENSHOTS/ability-highlight/reveal.png",
            "Bob catching up on Alice's turn: her Ember Unveiling revealed her top four cards, and the reveal panel shows the Unveiling's " +
                "whole text under \"Alice revealed · Ember Unveiling\" with \"Reveal the top four cards of your library.\" in violet on a " +
                "softly pulsing light violet, over the four cards.")
    }

    // HIS-252, STK-230, HIS-233: the panel of cards exiled from a library shows the Hedge Delve's whole text, its exiling sentence highlighted
    @Test fun aLibraryExilesPanelShowsItsSourcesTextWithTheExileHighlighted() {
        val g = EngineGameFixture(aliceCards = listOf(EngineGameFixture.Choices.DELVE, "Mountain"))
        val alice = EngineGame(g.game.id, g.a, g.base, g.snapshot(otherKept = true)).apply { tick(1_000); keep(); tick(2_000) }
        assertIs<GameDevice.Acted.Sent>(alice.send(cast(alice, EngineGameFixture.Choices.DELVE)))
        alice.tick(3_000)
        render(alice, g.a)
        compose.onNodeWithText("You exiled 2 cards from your library").assertExists()
        assertEquals("Exile the top two cards of your library.", highlighted())
        compose.onRoot().captureShot("$SCREENSHOTS/ability-highlight/library-exile.png",
            "Alice cast her Hedge Delve: her own panel \"You exiled 2 cards from your library\" shows the Delve's whole text with " +
                "\"Exile the top two cards of your library.\" highlighted in violet and the sentence after it plain, over the two cards.")
    }

    // HIS-252, STK-230, HIS-157: the Hedge Recall's whole text is the one sentence that moved the card: shown, nothing highlighted (no part
    // to tell apart)
    @Test fun aSourceWhoseWholeTextDidItShowsNothingHighlighted() {
        val g = EngineGameFixture(aliceCards = listOf(EngineGameFixture.Choices.STUDY, EngineGameFixture.Choices.RECALL))
        val alice = EngineGame(g.game.id, g.a, g.base, g.snapshot(otherKept = true)).apply { tick(1_000); keep(); tick(2_000) }
        assertIs<GameDevice.Acted.Sent>(alice.send(cast(alice, EngineGameFixture.Choices.STUDY)))
        alice.tick(3_000)
        assertIs<GameDevice.Acted.Sent>(alice.send(cast(alice, EngineGameFixture.Choices.RECALL)))
        alice.tick(4_000)
        alice.endTurn()
        alice.tick(5_000)
        val bob = bobCatchesUp(g, alice)
        render(bob, g.b)
        compose.onNodeWithContentDescription("Ability: Return target card from your graveyard to your hand.").assertExists()
        assertNull(highlighted())
        compose.onRoot().captureShot("$SCREENSHOTS/ability-highlight/whole-text-nothing.png",
            "Bob catching up on Alice's turn: her Hedge Recall returned her Hedge Study to her hand; the panel shows the Recall's text under " +
                "its title with nothing highlighted, because that one sentence is all of it and there is no part to tell apart.")
    }

    // STK-224, STK-225: Alice's Ember Glimpse looks at her top four cards privately: her choice shows the Ash Pup to reveal and the others
    // dimmed under "Other cards"; Bob, catching up, gets no panel and none of those cards
    @Test fun aPrivateLookThenChooseIsTheChoosersOnePanelAndShowsTheOtherPlayerNothing() {
        val (g, alice) = aliceChoosesAmong(EngineGameFixture.Choices.GLIMPSE)
        assertNull(alice.shown?.reveal, "no panel of hers")
        val d = alice.device.model.engine.pending?.decision as Decision.ChooseObjects
        render(alice, g.a)
        compose.onNode(hasText("Other cards") and isHeading(), useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Other cards, ${d.others.size}", useUnmergedTree = true).assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/engine/choice-among-looked.png",
            "Alice cast her Ember Glimpse: she looked at her top four cards, privately, and her choice panel shows all four: the Ash Pup she " +
                "may reveal and put into her hand, and the cards she can't choose dimmed under \"Other cards\".")
        val bob = EngineGame(g.game.id, g.b, g.base, g.behind(alice.device.session.pending, alice.device.model))
        bob.startPlayback()
        var guard = 0
        while (bob.shown?.status is GameDevice.Status.Playing && bob.shown?.reveal == null && guard++ < 200) bob.step()
        assertNull(bob.shown?.reveal, "nothing of the look shown to Bob")
        val seen = Host.visible(bob.device.model.engine, PlayerId(g.b))
        assertTrue((d.options + d.others).none { it in seen }, "none of the cards looked at visible to Bob")
    }

    // HIS-110, HIS-150, HIS-152, HIS-154 (owner, 2026-11-09): Alice's own reveal of a card she couldn't see opens her own panel: "You revealed · <source>",
    // the card, OK; her table's actions wait for her OK, and it closes only for her
    @Test fun yourOwnRevealShowsYouThePanelWithItsOk() {
        val alice = aliceCastsInsight()
        val reveal = assertNotNull(alice.shown?.reveal)
        assertEquals(PlayerId(f.a), reveal.by)
        render(alice, f.a)
        compose.onNodeWithText("You revealed · ${EngineGameFixture.Choices.INSIGHT}").assertExists()
        revealOk().assertExists()
        captureShot("$SCREENSHOTS/engine/reveal-panel-own.png", "Alice cast her own Ember Insight: it revealed her top card, so the " +
            "panel above her hand says \"You revealed\" with the card and the Insight, and her table waits for her own OK, her main button.")
        revealOk().performClick()
        refresh()
        revealOk().assertDoesNotExist()
    }
}
