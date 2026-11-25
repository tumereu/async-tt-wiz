package org.asyncttwiz.android

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.android.EngineGameFixture.Choices
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.PlayZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ZON-138 to ZON-142 (owner, 2026-11-25): your hand as a fan in landscape, for the owner's gallery review: a still fan, one at its
 * limit (on narrow, 892 dp and wide screens), a large hand turned from its start and to its middle with its "+N"s, a raised card in
 * view and turned away, your cards from other zones after the divider, and a clip of turning it with a flick.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w892dp-h412dp-land-mdpi")
class LandscapeHandFanScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clear() { context.getSharedPreferences("card-appearance", Context.MODE_PRIVATE).edit().clear().commit() }
    @After fun forget() = AutomatumBoards.reset()

    private val preferences by lazy { CardAppearancePreferences(AppPlatform.current) }
    private fun handSize(size: Float) { if (size != 1f) { preferences.customizeAreas(true); preferences.setSize(size, CardArea.HAND) } }

    /** Alice's table with [hand] cards in her hand at [size], the fan laid out as [layout] says; the scene it draws. */
    private fun render(hand: Int, size: Float = 1f, layout: TableLayoutState = TableLayoutState(), width: Float = 892f,
        height: Float = 412f): TableScene {
        handSize(size)
        val play = LiveTableFixture.play(hand)
        val board = engineTableBoard(play, "a")
        val toolkit = TableToolkitController("a", { board }, { true }, { _, _ -> true })
        toolkit.layoutState.value = layout
        compose.setContent { MaterialTheme { EngineFixtureTable(play, "a") {
            TableSurface(LiveTableFixture.game, board, "a", true, Modifier.fillMaxSize(), connected = true,
                onIntent = toolkit::handle, toolkit = toolkit, runtime = TableRuntimeHooks(navigation = {}, chat = {}, history = {}))
        } } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        return TablePresentation.scene(LiveTableFixture.game, board, "a", width, height, true, layout, appearance = preferences.read())
    }

    private fun TableScene.hand() = items.single { it.kind == TableItemKind.HAND && it.owner == "a" }
    private fun TableScene.turn() = assertNotNull(viewports.single { it.id == hand().viewport }.turn)

    /** The most cards a still fan holds on a [width] x [height] screen at hand size [size]. */
    private fun capacity(size: Float, width: Float = 892f, height: Float = 412f) = (2..30).first { n ->
        TablePresentation.scene(LiveTableFixture.game, engineTableBoard(LiveTableFixture.play(n + 1), "a"), "a", width, height, true,
            appearance = CardAppearance(customizeAreas = true, areas = mapOf(CardArea.HAND to size)))
            .viewports.single { it.id == "a/HAND" }.turn!!.turns
    }

    // ZON-138: five cards at the default (1x) size make a still fan at the bottom of the bar, a fifth of each below the screen
    @Test fun aSmallHandIsAStillFan() {
        val scene = render(5)
        assertTrue(!scene.turn().turns && scene.hand().fanPlus.isEmpty())
        captureShot("$SCREENSHOTS/hand/landscape-fan-small.png",
            "Landscape table, Alice holding five cards at the default hand size: her hand is a still fan between her piles and the controls, its cards turned a little and overlapping with their bottom fifth below the screen, no white hand row behind them, the count 5 at its right end.")
    }

    // ZON-138: five 2.5x cards stand over the battlefield, which is laid out above them and seen beside them
    @Test fun largeCardsStandOverTheBattlefield() {
        val scene = render(5, size = 2.5f)
        assertTrue(scene.hand().bounds.y < 412f - TableControlsHeight)
        captureShot("$SCREENSHOTS/hand/landscape-fan-large-cards.png",
            "Landscape table with hand cards at 2.5x: Alice's five cards make a still fan standing over the battlefield above the bottom bar, the bottom fifth of each below the screen, the battlefield seen beside them.")
    }

    // ZON-139: at the limit a still fan of as many cards as fit with at least 20 dp of each in view (here at 2.5x)
    @Test fun aFullFanAtItsLimit() {
        val fits = capacity(2.5f)
        val scene = render(fits, size = 2.5f)
        assertTrue(!scene.turn().turns)
        captureShot("$SCREENSHOTS/hand/landscape-fan-full.png",
            "Landscape table with 2.5x hand cards: Alice holds $fits cards, as many as fit with at least 20 dp of each in view, so her fan is full but still turns no further: no +N at either end.")
    }

    // ZON-139: the limit on an 892 dp wide screen at the default size: about 10
    @Test fun theLimitAtTheDefaultSize() {
        val fits = capacity(1f)
        render(fits)
        captureShot("$SCREENSHOTS/hand/landscape-fan-limit-892.png",
            "Landscape table 892 dp wide at the default hand size: Alice's $fits cards, the most a still fan holds there with at least 20 dp of each in view.")
    }

    // ZON-139: the limit on a narrow phone (780 x 360 dp) at the default size
    @Test @Config(qualifiers = "w780dp-h360dp-land-mdpi")
    fun theLimitOnANarrowPhone() {
        val fits = capacity(1f, 780f, 360f)
        render(fits, width = 780f, height = 360f)
        captureShot("$SCREENSHOTS/hand/landscape-fan-limit-narrow.png",
            "Landscape table on a narrow phone (780 x 360 dp) at the default hand size: Alice's $fits cards, the most a still fan holds there with at least 20 dp of each in view (the controls take half the bar).")
    }

    // ZON-139: the limit on a wide phone (960 x 412 dp) at the default size
    @Test @Config(qualifiers = "w960dp-h412dp-land-mdpi")
    fun theLimitOnAWidePhone() {
        val fits = capacity(1f, 960f)
        render(fits, width = 960f)
        captureShot("$SCREENSHOTS/hand/landscape-fan-limit-wide.png",
            "Landscape table on a wide phone (960 x 412 dp) at the default hand size: Alice's $fits cards, the most a still fan holds there with at least 20 dp of each in view.")
    }

    // ZON-139, ZON-140: a hand of ten 2.5x cards at its start: the first ones in view, the rest turned away with a "+N" at the right
    @Test fun aLargeHandAtItsStart() {
        val scene = render(10, size = 2.5f)
        val plus = scene.hand().fanPlus.single()
        assertTrue(plus.first.x > scene.hand().bounds.center.x)
        captureShot("$SCREENSHOTS/hand/landscape-fan-turning-start.png",
            "Landscape table, Alice holding ten 2.5x cards: her fan shows the first ${scene.turn().slots} from its left end, the other ${plus.second} turned away below the screen, counted by a small +${plus.second} at the fan's right end; her hand count still says 10.")
    }

    // ZON-139, ZON-140: turned to the middle, with a "+N" at each end
    @Test fun aLargeHandTurnedToItsMiddle() {
        val pitch = HandFan.MIN_SLOT
        val scene = render(10, size = 2.5f, layout = TableLayoutState(scroll = mapOf("a/HAND" to 2 * pitch)))
        assertEquals(listOf(2, 10 - 2 - scene.turn().slots), scene.hand().fanPlus.sortedBy { it.first.x }.map { it.second })
        captureShot("$SCREENSHOTS/hand/landscape-fan-turning-middle.png",
            "Alice's ten-card fan turned two cards on with a sideways drag: her third card is now at the fan's left end, a +2 at the left end and a +${10 - 2 - scene.turn().slots} at the right end count the cards turned away below the screen.")
    }

    // ZON-141: a raised card in a turned fan pops out along its turn until whole in view
    @Test fun aRaisedCardInView() {
        val scene = render(10, size = 2.5f, layout = TableLayoutState(scroll = mapOf("a/HAND" to 2 * HandFan.MIN_SLOT), raised = "a/HAND/3"))
        assertTrue(scene.items.single { it.cardId == "a/HAND/3" }.raised)
        captureShot("$SCREENSHOTS/hand/landscape-fan-raised.png",
            "Alice tapped her fourth card in the turned fan: it pops out of the fan along its own turn until it is whole in view, drawn over the others.")
    }

    // ZON-141: a raised card turned away stays raised: popped out over the end it went past
    @Test fun aRaisedCardTurnedAway() {
        val scene = render(10, size = 2.5f, layout = TableLayoutState(scroll = mapOf("a/HAND" to 4 * HandFan.MIN_SLOT), raised = "a/HAND/1"))
        val card = scene.items.single { it.cardId == "a/HAND/1" }
        assertTrue(card.raised && card.bounds.bottom <= 412f)
        captureShot("$SCREENSHOTS/hand/landscape-fan-raised-turned-away.png",
            "Alice raised her second card and then turned the fan four cards on: the raised card stays raised, standing popped out over the fan's left end it went past, while +4 counts the cards turned away there.")
    }

    // ZON-138, ZON-139, ZON-079: her cards from other zones stay at the fan's right end after the divider while her hand turns
    @Test fun cardsFromOtherZonesAfterTheDivider() {
        handSize(2.5f)
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(Choices.FLARE, "Mountain"))))
        kit.opening()
        kit.playLand()
        val flare = kit.atw(kit.hand(Choices.FLARE).first())
        assertTrue(kit.toolkit.cast.start(flare), "the Flare is cast: ${kit.toolkit.error}")
        kit.playOn("the Flare in the graveyard") { kit.state().player(kit.me).graveyard.any { kit.atw(it) == flare } && kit.aliceMain() }
        compose.setContent { MaterialTheme { NoRipples { CompositionLocalProvider(LocalEngineRoutes provides kit.routes) {
            Box(Modifier.fillMaxSize()) {
                TableSurface(kit.f.game, kit.alice.play(), kit.f.a, true, Modifier.fillMaxSize(), connected = true,
                    onIntent = kit.toolkit::handle, toolkit = kit.toolkit,
                    runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { kit.routes.undo() }, controlsConnected = true))
            }
        } } } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        val play = kit.alice.play()
        val scene = TablePresentation.scene(kit.f.game, play, kit.f.a, 892f, 412f, true, computed = assertNotNull(play.values).computed,
            handRow = HandRow.cards(play, kit.f.a), appearance = preferences.read())
        assertEquals(PlayZone.GRAVEYARD, scene.items.single { it.id == "handRow/$flare" }.handZone)
        val turn = scene.viewports.single { it.id == scene.items.single { it.kind == TableItemKind.HAND && it.owner == kit.f.a }.viewport }.turn
        assertTrue(turn?.turns == true, "her hand turns: $turn")
        captureShot("$SCREENSHOTS/hand/landscape-fan-other-zones.png",
            "Alice's 2.5x hand turns (+N at its right end), while her Ember Flare castable from her graveyard by flashback stays at the fan's right end after the thin divider, with its Graveyard badge, outside the turning part.")
    }

    // ZON-126, ZON-001, ZON-138 (owner-reported 2026-11-25): the other player's hand is the small fan of card backs with its count
    // after their piles in their top bar, as in every other layout, while yours is the large fan
    @Test fun theOtherPlayersHandIsASmallFan() {
        val scene = render(8, size = 2.5f)
        val other = scene.items.single { it.kind == TableItemKind.HAND && it.owner == "b" }
        assertTrue(other.handFan && scene.hand().handFan && other.bounds.bottom <= TablePresentation.HEADER)
        captureShot("$SCREENSHOTS/hand/landscape-fan-opponent-row.png",
            "Landscape table with 2.5x hand cards, both players holding eight cards: Bob's hand is the small fan of card backs with its count 8 after his piles in the top bar, Alice's the large fan at the bottom.")
    }

    // ZON-139: the clip of turning a large hand with a flick: it carries on, slows down and rests on a whole card
    @Test fun clipOfTurningWithAFlick() {
        val scene = render(12, size = 2.5f)
        val start = assertNotNull(scene.items.single { it.cardId == "a/HAND/2" }.handSlot).center
        compose.mainClock.autoAdvance = false
        var down = false
        compose.captureClip("$SCREENSHOTS/clips/landscape-fan-flick.webp",
            "Alice flicks her twelve-card 2.5x fan leftwards: it turns with her finger, carries on a little after she lets go, slowing down, and comes to rest on a whole card, the +N at each end counting the cards turned away.",
            millis = 2_000L) { at ->
            val lead = CLIP_LEAD_MILLIS
            val steps = 4
            if (at == lead) { compose.onRoot().performTouchInput { down(Offset(start.x * density, start.y * density)) }; down = true }
            else if (down && at > lead && at <= lead + steps * CLIP_STEP_MILLIS) {
                val n = (at - lead) / CLIP_STEP_MILLIS
                compose.onRoot().performTouchInput { moveTo(Offset((start.x - 25f * n) * density, start.y * density)) }
                if (n.toInt() == steps) { compose.onRoot().performTouchInput { up() }; down = false }
            }
        }
    }
}
