package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.Executor
import javax.imageio.ImageIO
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class TableStatesScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture
    private val combatTargetsState = f.combat().let { play ->
        val target = f.source(f.b, "2/3")
        val original = play.definitions.single { it.digest == target.definition }
        val definition = original.definition.copy(faces = original.definition.faces.map {
            it.copy(name = "Jace Beleren", typeLine = "Planeswalker — Jace") })
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        play.copy(definitions = play.definitions + submitted, board = play.board.copy(
            cards = play.board.cards.map { if (it.id == target.id) it.copy(definition = submitted.digest) else it },
            combat = play.board.combat!!.let { area -> area.copy(pairs = area.pairs.mapIndexed { index, pair ->
                if (index == 0) pair.copy(attackTarget = target.id) else pair }) }))
    }
    private val graveyardState = f.initial.let { play ->
        val graveyard = f.id("a/GRAVEYARD")
        val river = play.board.cards.single { it.containerId == f.id("a/HAND") && it.corner == "1/2" }
        play.copy(board = play.board.copy(
            cards = play.board.cards.map { if (it.id == river.id) it.copy(containerId = graveyard, covered = false) else it },
            containers = play.board.containers.map { container -> when (container.id) {
                graveyard -> container.copy(cards = listOf(river.id) + container.cards)
                river.containerId -> container.copy(cards = container.cards - river.id)
                else -> container
            } }))
    }
    /** ZON-091, ZON-092: River Adept suspended in your exile with 3 time counters, beside a card without counters. */
    private val exileState = f.initial.let { play ->
        val exile = f.id("a/EXILE")
        val hand = play.board.cards.filter { it.containerId == f.id("a/HAND") }
        val river = hand.single { it.corner == "1/2" }
        val other = hand.first { it.id != river.id }
        val moved = listOf(river.id, other.id)
        play.copy(board = play.board.copy(
            cards = play.board.cards.map { when (it.id) {
                river.id -> it.copy(containerId = exile, covered = false, typedCounters = listOf(TypedCounter("time", 3, 1)))
                other.id -> it.copy(containerId = exile, covered = false)
                else -> it
            } },
            containers = play.board.containers.map { container -> when (container.id) {
                exile -> container.copy(cards = moved + container.cards)
                f.id("a/HAND") -> container.copy(cards = container.cards - moved.toSet())
                else -> container
            } }))
    }

    /** TBL-021, TBL-135 (owner, 2026-11-11: counters as visuals): Bob's Ash Oracle planeswalker with 4 loyalty counters, his
     * creature with a -1/-1 counter and 3 oil counters, Alice's with two +1/+1 counters, a shield and a stun counter. */
    private val countersState = f.initial.let { play ->
        val sentinel = f.source(f.a, "1/3")
        val blocker = f.source(f.b, "2/3")
        val walker = play.board.cards.first { it.containerId == f.id("b/BATTLEFIELD") && it.id != blocker.id }
        val original = play.definitions.single { it.digest == walker.definition }
        val definition = original.definition.copy(faces = original.definition.faces.map {
            it.copy(name = "Ash Oracle", typeLine = "Legendary Planeswalker — Chandra", rulesText = "[+1]: You gain 1 life.",
                power = "", toughness = "", loyalty = "3", corner = "3") })
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        play.copy(definitions = play.definitions + submitted, board = play.board.copy(cards = play.board.cards.map { when (it.id) {
            sentinel.id -> it.copy(typedCounters = listOf(TypedCounter("+1/+1", 2, 1), TypedCounter("shield", 1, 2), TypedCounter("stun", 1, 3)))
            blocker.id -> it.copy(typedCounters = listOf(TypedCounter("-1/-1", 1, 4), TypedCounter("oil", 3, 5)))
            walker.id -> it.copy(definition = submitted.digest, corner = "3", typedCounters = listOf(TypedCounter("loyalty", 4, 6)))
            else -> it
        } }))
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun render(kind: String, landscape: Boolean): List<PlayAction> {
        val sent = mutableListOf<PlayAction>()
        val tokenState = f.combat().let { state ->
            val actual = state.board.containers.single { it.zone == PlayZone.STACK }.cards.first { id -> state.board.cards.any { it.id == id } }
            val tokenIds = listOf(f.source(f.a, "1/3").id, f.source(f.b, "2/3").id,
                state.board.containers.single { it.accountId == f.a && it.zone == PlayZone.HAND }.cards.first()) +
                if (kind == "tokens-stack-card") listOf(actual) else emptyList()
            state.copy(board = state.board.copy(cards = state.board.cards.map { it.copy(token = it.id in tokenIds) },
                containers = state.board.containers.map { if (kind == "tokens-stack-card" && it.zone == PlayZone.STACK)
                    it.copy(cards = it.cards.reversed()) else it },
                stackEffects = state.board.stackEffects.map { it.copy(sourceId = f.source(f.a, "1/3").id) }))
        }
        val drawn = if (kind == "combat-targets") combatTargetsState
            else if (kind.startsWith("tokens")) tokenState
            else if (kind == "graveyard-contents") graveyardState
            else if (kind.startsWith("exile-counters")) exileState
            else if (kind.startsWith("counters")) countersState else f.combat()
        val current = { drawn }
        // The engine table's board and routes (R4b follow-up): what the app draws of this board
        val toolkit = TableToolkitController(f.a, { engineTableBoard(current(), f.a) }, { true }, { action, _ -> sent += action; true })
        if (kind == "tokens-small") toolkit.layoutState.value = TableLayoutState(TableCamera(zoom = TablePresentation.MIN_ZOOM))
        // Zoomed in on Alice's creature with counters, centred on it
        if (kind == "counters-close") TablePresentation.scene(f.game, engineTableBoard(current(), f.a), f.a, 412f, 892f, landscape).let { scene ->
            val card = scene.items.single { it.sourceId == f.source(f.a, "1/3").id && it.zone == PlayZone.BATTLEFIELD }.bounds.center
            val port = assertNotNull(scene.world).viewport.center
            toolkit.layoutState.value = TableLayoutState(TableCamera(card.x - port.x, card.y - port.y, zoom = 3f))
        }
        compose.setContent {
            MaterialTheme {
                if (kind in listOf("placeholder", "unavailable")) TableUnavailable(f.game.gameName, landscape,
                    loading = kind == "placeholder", navigation = {}, retry = {})
                else {
                    EngineFixtureTable(current(), f.a) {
                        Box(Modifier.fillMaxSize()) {
                            TableSurface(f.game, engineTableBoard(current(), f.a), f.a, landscape, Modifier.fillMaxSize(), connected = true,
                                onIntent = toolkit::handle, toolkit = toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, history = {}, undo = { true }, controlsConnected = true))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        when (kind) {
            "tokens-contents" -> compose.onNodeWithContentDescription("Stack, 2 entries").performClick()
            "tokens-inspector" -> compose.runOnIdle { toolkit.peek(f.source(f.a, "1/3").id) }
            "stack-contents" -> {
                compose.onNodeWithContentDescription("Stack, 2 entries").performTouchInput { doubleClick() }
                compose.runOnIdle { assertEquals("manageStack", toolkit.panel) }
                compose.onNodeWithText("Stack · 2").assertExists()
            }
            "graveyard-contents" -> {
                // ZON-035: a double-tap on a pile leaves its search panel open: the first tap opened it, the second keeps it.
                compose.onNodeWithContentDescription("Alice graveyard, 3 cards").performTouchInput { doubleClick() }
                compose.mainClock.advanceTimeBy(500)
                compose.onNodeWithContentDescription("Your Graveyard panel, 3 cards").assertIsDisplayed()
                compose.onNode(isDialog()).assertDoesNotExist()
                compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
                val scene = TablePresentation.scene(f.game, engineTableBoard(current(), f.a), f.a, 412f, 892f, landscape, toolkit.layoutState.value)
                val panel = scene.cardPanels.single()
                assertEquals(PlayZone.GRAVEYARD, panel.localZone)
                assertEquals(0f, panel.bounds.x)
                // Headers and names are canvas-rendered; verify the geometry used to draw them.
                assertEquals(listOf("Creature (2)", "Land (1)"), panel.typeHeaders.map { it.title })
                assertEquals(listOf("Meadow Sentinel", "River Adept", "Forest"), panel.rows.map { it.entry.name })
                for (name in listOf("Meadow Sentinel", "River Adept", "Forest"))
                    compose.onNodeWithContentDescription("Graveyard panel: $name").assertIsDisplayed()
            }
            "exile-counters" -> {
                compose.onNodeWithContentDescription("Alice exile, 2 cards, cards with counters").performTouchInput { doubleClick() }
                compose.mainClock.advanceTimeBy(500)
                compose.onNodeWithContentDescription("Your Exile panel, 2 cards").assertIsDisplayed()
                compose.onNodeWithContentDescription("Exile panel: River Adept, time counter ×3").assertIsDisplayed()
                val scene = TablePresentation.scene(f.game, engineTableBoard(current(), f.a), f.a, 412f, 892f, landscape, toolkit.layoutState.value)
                // TBL-135 (owner, 2026-11-11): a bubble on its art, not words on a chip
                assertEquals(listOf(3), scene.items.single { it.label == "Exile panel: River Adept, time counter ×3" }.counterBubbles.map { it.count })
            }
            "counters" -> {
                val board = engineTableBoard(current(), f.a)
                val scene = TablePresentation.scene(f.game, board, f.a, 412f, 892f, landscape, toolkit.layoutState.value,
                    computed = assertNotNull(board.values).computed)
                val oracle = scene.items.single { it.zone == PlayZone.BATTLEFIELD && it.label.startsWith("Ash Oracle,") }
                assertEquals("4", oracle.corner, "its loyalty in the corner")
                assertEquals(emptyList(), oracle.counterBubbles, "no loyalty bubble beside its corner")
                assertTrue(oracle.label.endsWith(", loyalty counter ×4"), oracle.label)
                assertEquals(listOf("2", "1", "1"), scene.items.single { it.sourceId == f.source(f.a, "1/3").id && it.zone == PlayZone.BATTLEFIELD }
                    .counterBubbles.map { CounterBubbles.look(it).text })
                // TBL-135 (owner, 2026-11-12): a counter without a designed colour is a plain bubble with only its number
                assertEquals(listOf("1", "3"), scene.items.single { it.sourceId == f.source(f.b, "2/3").id && it.zone == PlayZone.BATTLEFIELD }
                    .counterBubbles.map { CounterBubbles.look(it).text })
            }
            "exile-counters-pile" -> {
                compose.onNodeWithContentDescription("Alice exile, 2 cards, cards with counters").assertIsDisplayed()
                val scene = TablePresentation.scene(f.game, engineTableBoard(current(), f.a), f.a, 412f, 892f, landscape, toolkit.layoutState.value)
                assertEquals(listOf(true, false), listOf(f.a, f.b).map { owner ->
                    scene.items.single { it.zone == PlayZone.EXILE && it.kind == TableItemKind.PILE && it.owner == owner }.countersInside })
            }
        }
        compose.waitForIdle()
        assertTrue(sent.isEmpty())
        val file = File("$SCREENSHOTS/states-$kind-${if (landscape) "landscape" else "portrait"}.png")
        file.parentFile!!.mkdirs()
        val combat = "in combat at declare attackers"
        val shown = when (kind) {
            "combat" -> "$combat, one attacker blocked; the Stack holds an effect and a card"
            "combat-targets" -> "$combat, the first attack aimed at the opponent's Jace Beleren planeswalker, in its own lane"
            "tokens" -> "$combat, where your attacker, the opponent's blocker and one hand card are tokens"
            "tokens-small" -> "$combat with several tokens, zoomed out as far as possible"
            "tokens-contents" -> "$combat with several tokens, after tapping the two-entry Stack"
            "tokens-inspector" -> "$combat with several tokens, holding your token attacker: Inspect shows only its card image, with normal card corners"
            "tokens-stack-card" -> "$combat with several tokens, a token card on top of the Stack"
            "stack-contents" -> "$combat, after double-tapping the Stack: Manage Stack lists its two entries"
            "graveyard-contents" -> "after double-tapping your three-card graveyard: its panel lists the cards grouped by type"
            "exile-counters" -> "after double-tapping your exile: its panel shows the suspended River Adept with a white bubble \"3T\" (three time counters) on its art"
            "counters" -> "with counters on the battlefield: Bob's Ash Oracle planeswalker shows its 4 loyalty in its corner, his creature a near-black -1/-1 bubble and a plain white 3 bubble for 3 oil counters, Alice's creature a green 2 (+1/+1), a sky-blue 1 (shield) and a yellow 1 (stun) on its art"
            "counters-close" -> "with counters on the battlefield, zoomed in on Alice's creature: a green 2 for its +1/+1 counters, a sky-blue 1 (shield) and a yellow 1 (stun) on its art, its corner 3/5; beside it Bob's creature's near-black -1/-1 bubble and a plain white 3 for 3 oil counters"
            "exile-counters-pile" -> "with a suspended card (3 time counters) in your exile: the exile pile carries a small violet counter marker at its top-right corner"
            "placeholder" -> "placeholder showing Loading table… with a spinner"
            "unavailable" -> "placeholder showing Table unavailable with a Reconnect button"
            else -> error("No caption for $kind")
        }
        captureShot(file.path, "${if (landscape) "Landscape" else "Portrait"} table $shown.")
        val image = ImageIO.read(file)
        assertEquals(if (landscape) 892 else 412, image.width)
        assertEquals(if (landscape) 412 else 892, image.height)
        if (kind.startsWith("combat")) {
            compose.onNodeWithContentDescription("Table actions").assertIsDisplayed().assertIsEnabled()
            val scene = TablePresentation.scene(f.game, engineTableBoard(current(), f.a), f.a, image.width.toFloat(), image.height.toFloat(), landscape,
                landscapeHome = true)
            if (kind == "combat") assertRenderedLandsBands(image, scene, f.a, turnPlayer = current().board.turn.accountId)
            // CMB-042: the title size of the band's cards (never narrower than COMBAT_MIN_CARD at zoom 1), zooming with the band
            val zoom = scene.camera.zoom
            assertTrue(scene.combatLabels.all { it.fontSize in 10.5f * zoom..11.5f * zoom })
            val cards = scene.items.filter { it.projection && it.viewport == "combat" }
            assertTrue(cards.isNotEmpty())
            assertTrue(scene.combatLabels.all { label -> cards.all { label.bounds.bottom < it.bounds.y } })
            if (kind == "combat-targets") assertEquals(1, scene.combatLaneDividers.size)
            // Card-slot dividers start below the lane headers; lane dividers cross the header row too.
            assertTrue(scene.combatDividers.all { (from, _) -> scene.combatLabels.all { from.y >= it.bounds.bottom - .01f } })
            assertTrue(scene.combatLaneDividers.all { (from, _) -> scene.combatLabels.all { from.y <= it.bounds.y + .01f } })
        }
        return sent
    }

    // CMB-008, CMB-013, CMB-014, STK-008, STK-018, STK-021
    @Test fun combatPortrait() { render("combat", false) }
    @Test fun combatCardTargetPortrait() { render("combat-targets", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun combatCardTargetLandscape() { render("combat-targets", true) }
    @Test fun manageStackReflectsLiveMembershipAndClosesWhenEmpty() {
        var state by mutableStateOf(f.combat())
        val ui = TableToolkitController(f.a, { tableBoard(state) }, { true }, { _, _ -> error("Browsing is local") })
        ui.stackContents()
        compose.setContent { MaterialTheme {
            TableToolkitScreen(ui, tableBoard(state), f.game, false)
        } }
        compose.onNodeWithText("Stack · 2").assertIsDisplayed()
        compose.runOnIdle {
            state = state.copy(board = state.board.copy(stackEffects = emptyList(), containers = state.board.containers.map {
                if (it.zone == PlayZone.STACK) it.copy(cards = it.cards - f.id("effect")) else it }))
        }
        compose.onNodeWithText("Stack · 1").assertIsDisplayed()
        compose.runOnIdle { state = f.initial }
        compose.onNodeWithText("Stack · 1").assertDoesNotExist()
        compose.runOnIdle { assertNull(ui.panel) }
    }
    // STK-021
    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun manageStackPeekAttachesAboveTheManageDialogAndReleasesBackToIt() {
        val state = f.combat()
        compose.setContent { MaterialTheme {
            ManageStackDialog(tableBoard(state), f.a, {})
        } }
        compose.onNodeWithText("Stack · 2").assertIsDisplayed()
        val dialog = android.view.inspector.WindowInspector.getGlobalWindowViews().last()
        val row = compose.onNodeWithText("Top ·", substring = true)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(350)
        row.performTouchInput { advanceEventTime(350); down(center) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("Card inspection content").assertIsDisplayed()
        val popup = android.view.inspector.WindowInspector.getGlobalWindowViews().last()
        assertEquals(dialog.windowToken, (popup.layoutParams as android.view.WindowManager.LayoutParams).token,
            "The inspection popup must belong to the manage dialog, not the activity underneath it")
        val file = File("$SCREENSHOTS/manage-stack-inspection-portrait.png")
        file.parentFile!!.mkdirs()
        captureShot(file.path, "Manage Stack dialog in combat with two entries, holding the top row: " +
            "its card peek opens above the dialog.")
        row.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Card inspection content").assertDoesNotExist()
        compose.onNodeWithText("Stack · 2").assertIsDisplayed()
    }
    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun tokensOnBattlefieldHandStackAndCombatPortrait() { render("tokens", false) }
    @Test fun tokensAtSmallestCardSizePortrait() { render("tokens-small", false) }
    @Test fun tokenContentsPreviewPortrait() { render("tokens-contents", false) }
    @Test fun tokenInspectorPreviewPortrait() { render("tokens-inspector", false) }
    @Test fun realTokenOnStackPortrait() { render("tokens-stack-card", false) }
    // CMB-008, STK-018, ZON-035, ZON-058
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun combatLandscape() { render("combat", true) }
    @Test fun stackPortrait() { render("stack-contents", false) }
    @Test fun graveyardBrowserPortrait() { render("graveyard-contents", false) }
    // ZON-091: counters on a card in exile show on it in the exile view
    @Test fun exileCountersPortrait() { render("exile-counters", false) }
    // TBL-021, TBL-135 (owner, 2026-11-11): a planeswalker's loyalty in its corner, other counters as bubbles on the art
    @Test fun countersPortrait() { render("counters", false) }
    @Test fun countersCloseUpPortrait() { render("counters-close", false) }
    // ZON-092: an exile pile holding a card with counters is marked
    @Test fun exileCountersPilePortrait() { render("exile-counters-pile", false) }
    // TBL-104
    @Test fun graveyardHoldInspectsWithoutOpeningActionsOrLogging() {
        val sent = render("graveyard-contents", false)
        compose.mainClock.autoAdvance = false
        val row = compose.onNodeWithContentDescription("Graveyard panel: Meadow Sentinel")
        compose.mainClock.advanceTimeBy(350)
        row.performTouchInput { advanceEventTime(350); down(center) }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.onNodeWithContentDescription("Card inspection content").assertIsDisplayed()
        compose.onNodeWithContentDescription("Card image: Meadow Sentinel").assertExists()
        compose.onNodeWithText("Creature — Soldier", substring = false).assertDoesNotExist()
        assertTrue(sent.isEmpty())
        row.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Card inspection content").assertDoesNotExist()
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.onNodeWithContentDescription("Your Graveyard panel, 3 cards").assertIsDisplayed()
        assertTrue(sent.isEmpty())
    }
    @Test fun graveyardDoubleTapDoesNotRotateOrSubmit() {
        val sent = render("graveyard-contents", false)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(350)
        // Real frames compose the immediately opened card menu before the second tap; that tap is
        // then relayed back to the table's coordinator, which recognizes the double tap.
        // Keep idle synchronization from auto-advancing past the recognition window between taps.
        val row = compose.onNodeWithContentDescription("Graveyard panel: Meadow Sentinel")
        val center = row.fetchSemanticsNode().boundsInRoot.center
        row.performTouchInput { advanceEventTime(350); click() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(120)
        // ZON-058: Automatum offers nothing for it, so its menu would be empty and doesn't open
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.onRoot().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(400)
        compose.mainClock.autoAdvance = true
        assertTrue(sent.isEmpty())
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.onNodeWithContentDescription("Your Graveyard panel, 3 cards").assertIsDisplayed()
    }
    // HIS-005, old-model timeline branching, old-model armed continuation, old-model game tree, STK-018
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun stackLandscape() { render("stack-contents", true) }
    @Test fun loadingPortrait() { render("placeholder", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun loadingLandscape() { render("placeholder", true) }
    @Test fun unavailablePortrait() { render("unavailable", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun unavailableLandscape() { render("unavailable", true) }


    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun opponentLifeOpensOnlyLifeInLandscape() {
        val sent = render("combat", true)
        compose.onNodeWithContentDescription("Bob, life", substring = true).performClick()
        compose.onNodeWithContentDescription("Life", substring = false).assertExists()
        compose.onNodeWithContentDescription("Set life").assertDoesNotExist()
        compose.onNodeWithContentDescription("Set red counter").assertDoesNotExist()
        assertTrue(sent.isEmpty())
    }




}
