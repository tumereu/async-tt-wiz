package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.TargetRef
import org.asyncttwiz.android.EngineGameFixture.Choices
import org.asyncttwiz.protocol.ChatMessage
import org.asyncttwiz.protocol.PlayZone
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The desktop table (WEB-015, wireframes D1–D5) at 1440 × 900: the same game table as the phone's
 * arranged for a large window: the global top row with the phase track, each player's seat header a box floating over their battlefield,
 * the Stack at the battlefields' right edge beside the right dock with the docked chat, your hand centred in the bottom band, the action area at the
 * bottom right, and panels as a strip bottom-centre or a centred overlay over the dimmed table.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1440dp-h900dp-land-mdpi")
class DesktopTableScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @After fun forget() = AutomatumBoards.reset()

    private fun drain(f: EngineLobbyFixture) {
        repeat(4) { compose.runOnIdle { f.worker.drain() }; compose.waitForIdle() }
    }

    /** A lobby whose game chat (docked) holds a short exchange between Alice and Bob of [kit]'s game. */
    private fun chatLobby(kit: EngineTableKit): LobbyController {
        val lobby = ScreenFixtures().lobby
        val game = kit.f.game
        fun message(n: Int, author: String, text: String) = ChatMessage("m$n", game.id, game.playId!!, author, text, n.toString(), "date")
        lobby.seed(lobby.state.copy(selected = game.id, detail = game, chatLoaded = true, chat = ChatPresentation(messages =
            IndexedChatMessages().upsert(listOf(message(1, kit.f.b, "that last game was close"), message(2, kit.f.a, "rematch after this one?"))))))
        return lobby
    }

    /** Alice's table of [kit]'s game drawn as [EngineTableScreen] draws it in the desktop layout: the top row over the table
     * surface with the desktop frame, the dock holding the docked chat of [lobby] while [dockOpen] (read as state). */
    private fun render(kit: EngineTableKit, lobby: LobbyController = chatLobby(kit), dockOpen: () -> Boolean = { true },
        pictures: Boolean = false) {
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalAppLayout provides AppLayout.Desktop, LocalEngineRoutes provides kit.routes,
                        LocalDesktopFrame provides DesktopFrame(dockOpen())) {
                        val play = kit.alice.play().let { if (pictures) pictured(it) else it }
                        Column(Modifier.fillMaxSize().background(Color(0xfff3f5f5))) {
                            DesktopTopRow(kit.f.game, play, kit.f.a, kit.toolkit, reviewing = false, navigation = {}, returnCurrent = null,
                                changes = null, dockOpen = dockOpen(), toggleDock = {}, chat = null, chatUnread = 0)
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                TableSurface(kit.f.game, play, kit.f.a, true, Modifier.fillMaxSize(), connected = true,
                                    onIntent = kit.toolkit::handle, toolkit = kit.toolkit,
                                    runtime = TableRuntimeHooks(navigation = {}, undo = { kit.routes.undo() }, controlsConnected = true,
                                        waitingLine = kit.routes.waitingLine(play),
                                        desktopDock = { DesktopDock { m -> ChatPanel(lobby, kit.f.a, m, docked = true) } }))
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Alice's scene of her desktop table (the surface under the top row: 1440 × 844). */
    private fun scene(kit: EngineTableKit, pictures: Boolean = false, appearance: CardAppearance = DesktopCardAppearance,
        layout: TableLayoutState = kit.toolkit.layoutState.value): TableScene =
        kit.alice.play().let { if (pictures) pictured(it) else it }.let { play ->
            TablePresentation.scene(kit.f.game, play, kit.f.a, 1440f, 900f - DesktopTable.topRow(1440f), true, layout,
                appearance = appearance, landscapeHome = true, handRow = HandRow.cards(play, kit.f.a), desktop = DesktopFrame())
        }

    private val picturedBoards = java.util.IdentityHashMap<TableBoard, TableBoard>()
    /** TBL-139, TBL-146: [play] with every card face given a printing of the test pictures ([PlaceholderArtwork]), as real cards have:
     * the engine fixture's cards have none, so they keep the app's frame. */
    private fun pictured(play: TableBoard): TableBoard = picturedBoards.getOrPut(play) {
        play.copy(definitions = play.definitions.map { submitted -> submitted.copy(definition = submitted.definition.copy(
            faces = submitted.definition.faces.map { it.copy(scryfallId = it.scryfallId ?: placeholderPrinting(it.name)) })) })
    }

    /** The mouse moved over [item] of Alice's desktop table (its surface lies under the top row). */
    private fun hover(item: TableItem) {
        compose.onAllNodes(isRoot()).onFirst().performMouseInput {
            moveTo(androidx.compose.ui.geometry.Offset(item.bounds.center.x, item.bounds.center.y + DesktopTable.topRow(1440f)))
        }
        compose.waitForIdle()
    }

    /** The page's [name] key pressed ([down]) or released at [time] (ms). */
    private fun key(name: String, down: Boolean, time: Long) {
        compose.runOnIdle { DesktopKeyboard.dispatch(DesktopKey(name, down, time)) }
        compose.waitForIdle()
    }

    // WEB-024, WEB-025 (D4; owner playtest 2026-11-15: no pinned Inspect by a tap of Alt): Alice cast her Ember Cub this turn;
    // she holds Alt with the mouse over the Cub: Inspect shows it over the dimmed table with its changes beside it, without a
    // close button, and the view is paused; released it closes, and a quick tap of Alt pins nothing
    @Test fun holdingAltOverACardInspectsItWithItsChanges() {
        val kit = EngineTableKit()
        kit.opening()
        kit.playLand()
        val cub = kit.castCub()
        render(kit)
        val cubItem = scene(kit).items.single { it.cardId == cub && it.battlefieldCard }
        hover(cubItem)
        key("Alt", true, 10_000)
        compose.onNodeWithTag("card-inspection").assertExists()
        compose.onNodeWithContentDescription("Close inspection").assertDoesNotExist()
        compose.runOnIdle { assertTrue(kit.routes.viewPause.key, "the finger key pauses the view") }
        captureShot("$SCREENSHOTS/desktop/inspect-alt.png",
            "Alice cast her Ember Cub this turn; holding Alt with the mouse over it inspects it over the dimmed desktop table, its picture at the left with its changes (entered this turn) beside it, and no close button: it shows only while Alt is held.")
        key("Alt", false, 12_000)
        compose.onNodeWithTag("card-inspection").assertDoesNotExist()
        hover(cubItem.copy(bounds = cubItem.bounds.copy(x = cubItem.bounds.x + 2f)))
        key("Alt", true, 20_000)
        key("Alt", false, 20_100)
        compose.onNodeWithTag("card-inspection").assertDoesNotExist()
        compose.runOnIdle { assertFalse(kit.routes.viewPause.key) }
    }

    // WEB-024, WEB-085, TBL-111 (owner playtest 2026-11-15: "should be top down and scrollable, preferably even whilst held"): Alice has two
    // Mountains stacked on her battlefield; holding Alt over the stack inspects both, one under the other, and the mouse wheel
    // scrolls them while Alt is held
    @Test fun aMultiCardInspectListsTheCardsTopDownAndTheWheelScrollsIt() {
        val kit = EngineTableKit()
        kit.toReadyCub()
        kit.playLand()
        render(kit)
        val stack = scene(kit).items.filter { it.battlefieldCard && it.copies == 2 }.last()
        hover(stack)
        key("Alt", true, 10_000)
        compose.onNodeWithTag(INSPECT_LIST_TAG).assertExists()
        val line = compose.onNodeWithTag(INSPECT_SEPARATOR_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(line.width > line.height, "the cards one under another, a line across between them: $line")
        compose.onNodeWithTag(INSPECT_LIST_TAG).performMouseInput { scroll(3f) }
        compose.waitForIdle()
        val scrolled = compose.onNodeWithTag(INSPECT_SEPARATOR_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(scrolled.top < line.top - 50f, "the wheel scrolled them up while Alt is held: $line -> $scrolled")
        compose.onNodeWithTag(INSPECT_LIST_TAG).performMouseInput { scroll(-3f) }
        compose.waitForIdle()
        captureShot("$SCREENSHOTS/desktop/inspect-list.png",
            "Alice holds Alt with the mouse over her two stacked Mountains on the desktop table: Inspect lists both, one under the other with a line between them, the second peeking in at the bottom; the mouse wheel scrolls the list while Alt is held.")
        key("Alt", false, 12_000)
        compose.onNodeWithTag("card-inspection").assertDoesNotExist()
    }

    // WEB-031, WEB-088 (owner playtest 2026-11-15): the hold key keeps the response mode Control while held and puts back the mode Alice
    // had when it is let go; a mode changed meanwhile is kept
    @Test fun holdingTheHoldKeyKeepsControlModeUntilItIsLetGo() {
        val kit = EngineTableKit()
        kit.opening()
        val mode = { kit.alice.device.settings.mode }
        val before = mode()
        assertTrue(before != org.asyncttwiz.protocol.game.ResponseMode.FullControl, "Alice isn't in Control to begin with: $before")
        val was = assertNotNull(kit.routes.holdControl("Control"))
        kit.settle()
        assertEquals(org.asyncttwiz.protocol.game.ResponseMode.FullControl, mode(), "Control while held")
        kit.routes.releaseControl(was, "let go")
        kit.settle()
        assertEquals(before, mode(), "back to her mode")
        kit.routes.holdControl("Control")
        kit.settle()
        assertTrue(kit.routes.nextResponseMode(), "she changes the mode meanwhile")
        kit.settle()
        val changed = mode()
        kit.routes.releaseControl(was, "let go")
        kit.settle()
        assertEquals(changed, mode(), "a mode changed meanwhile is kept")
    }

    // WEB-027, ZON-080, ZON-111, WEB-026: with a Mountain and an Ember Shard out, the mouse rests on Alice's Ember Kit in hand: its
    // two chips show faded above it and its quick look beside it, nothing outlined for its free regular way; on the "Cast with
    // offspring" chip that chip turns solid and its one source is outlined in amber;
    // a click on the faded "Cast with offspring" chip casts it that way at once
    @Test fun hoveringAHandCardShowsItsChipsFadedWithTheirSources() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(Choices.KIT, Choices.SHARD, "Mountain"))))
        kit.opening()
        kit.playLand()
        val shard = kit.atw(kit.hand(Choices.SHARD).first())
        assertTrue(kit.toolkit.cast.start(shard), "the Shard is cast: ${kit.toolkit.error}")
        kit.playOn("the Shard on the battlefield") { shard in kit.field(Choices.SHARD).map(kit::atw) && kit.aliceMain() }
        val card = kit.atw(kit.hand(Choices.KIT).first())
        render(kit)
        hover(scene(kit).items.single { it.cardId == card && it.zone == PlayZone.HAND })
        compose.mainClock.advanceTimeBy(QUICK_LOOK_MILLIS + 50)
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(card, kit.toolkit.hand.hovered)
            assertEquals(null, kit.toolkit.hand.raised, "hovering raises nothing")
            assertEquals(emptySet(), kit.toolkit.hand.sources(kit.alice.play()), "the Kit's regular way is free: nothing outlined")
        }
        compose.onNodeWithTag(HOVER_CHIPS_TAG).assertExists()
        compose.onNodeWithTag(QUICK_LOOK_TAG).assertExists()
        // WEB-026, WEB-015, WEB-074 (owner 2026-11-16: the card stays in the fan): its quick look above it, clear of the fan and its chips
        val look = compose.onNodeWithTag(QUICK_LOOK_TAG).fetchSemanticsNode().boundsInRoot
        val chipRow = compose.onNodeWithTag(HOVER_CHIPS_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(look.bottom <= chipRow.top, "the quick look above the chips: $look, $chipRow")
        // The pointer moves up onto the "Cast with offspring" chip: it turns solid and its one source is outlined
        compose.onNodeWithContentDescription("Way Cast with offspring {R}").performMouseInput { moveTo(center) }
        compose.mainClock.advanceTimeBy(HOVER_CHIPS_KEEP_MILLIS + 50)
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(card, kit.toolkit.hand.hovered, "the chips stay while the pointer is on them")
            assertEquals(1, kit.toolkit.hand.hoverChip)
            assertEquals(1, kit.toolkit.hand.sources(kit.alice.play()).size, "the chip's source outlined")
        }
        captureShot("$SCREENSHOTS/desktop/hover-chips.png",
            "With a Mountain and an Ember Shard out, the mouse rested on Alice's Ember Kit in her hand on the desktop table, then moved up onto its chips: \"Cast with offspring\" turned solid while the free \"Cast\" stays faded, and the one source it would tap is outlined in amber.")
        compose.onNodeWithContentDescription("Way Cast with offspring {R}").performClick()
        compose.waitForIdle()
        kit.playOn("the Kit cast with offspring") { kit.state().stack.isNotEmpty() || card in kit.field(Choices.KIT).map(kit::atw) }
    }

    // WEB-026 (e2e GiftTargetPhraseDesktopTest: with the battlefields' column 0 right of the seat boxes, WEB-016, a hand card's
    // quick look lay over Alice's creature and took the mouse: her click on the creature under it was lost): the quick look has
    // nothing to press; a click on the card under it reaches the table as a tap on that card
    @Test fun aClickOnACardUnderTheQuickLookReachesTheTable() {
        val kit = EngineTableKit()
        kit.toReadyCub()
        render(kit)
        val shown = scene(kit)
        val cub = shown.items.single { it.battlefieldCard && it.owner == kit.f.a && it.value == "Ember Cub" }
        // The hand card whose quick look (above it, centred on it) covers the Cub's middle
        val card = shown.items.filter { it.zone == PlayZone.HAND && it.kind == TableItemKind.CARD && it.owner == kit.f.a }
            .minBy { kotlin.math.abs(it.bounds.center.x - cub.bounds.center.x) }
        hover(card)
        compose.mainClock.advanceTimeBy(QUICK_LOOK_MILLIS + 50)
        compose.waitForIdle()
        val look = compose.onNodeWithTag(QUICK_LOOK_TAG).fetchSemanticsNode().boundsInRoot
        val at = androidx.compose.ui.geometry.Offset(cub.bounds.center.x, cub.bounds.center.y + DesktopTable.topRow(1440f))
        assertTrue(look.contains(at), "the quick look over the Cub's middle: $look, Cub ${cub.bounds}")
        assertEquals(null, kit.toolkit.popupItem)
        compose.onAllNodes(isRoot()).onFirst().performMouseInput { moveTo(at); press(); release() }
        // A tap is a tap once no second one followed it
        compose.mainClock.advanceTimeBy(DOUBLE_TAP_MILLIS + 100)
        compose.waitForIdle()
        assertEquals(cub.id, kit.toolkit.popupItem?.id, "the click a tap on the Cub (its menu open)")
    }

    // WEB-015, WEB-073, WEB-074, WEB-103, TBL-139, TBL-146 (owner playtest 2026-11-15, as in MTG Arena; owner 2026-11-16): Alice's hand is a fan at the bottom of her
    // desktop table between her seat header and the action area, its cards partly below the window's bottom, drawn as their
    // printed pictures; the mouse on her Mountain only draws it over the others where it lies, its Play chip faded above it; a
    // click raises it (ZON-079): it pops out of the fan along its own turn, keeping it, whole in view, its Play chip above it
    @Test fun theHandIsAFanTheHoveredCardComesToTheFrontAndARaisedOnePopsOut() {
        val kit = EngineTableKit()
        kit.opening()
        render(kit, pictures = true)
        val cards = scene(kit, pictures = true).items.filter { it.zone == PlayZone.HAND && it.kind == TableItemKind.CARD && it.label.startsWith("Mountain") }
        val mountain = cards.firstOrNull { it.cardTransform!!.rotation != 0f } ?: cards.first()
        assertTrue(mountain.fullPicture, "the desktop hand's cards drawn as full pictures by default")
        val top = DesktopTable.topRow(1440f)
        fun shown() = compose.onAllNodes(hasContentDescription("Mountain, owner", substring = true)).fetchSemanticsNodes()
            .map { it.boundsInRoot }.filter { it.top > 900f - DesktopTable.FLOAT - 120f }.minBy { kotlin.math.abs(it.center.x - mountain.bounds.center.x) }
        captureShot("$SCREENSHOTS/desktop/hand-fan.png",
            "Alice's hand on the desktop table at the start of her turn is a fan of printed card pictures at the bottom between her seat header and the action area, its cards turned a little either way, overlapping and partly below the window, the whole fan in view at its sides.")
        hover(mountain.copy(bounds = mountain.handSlot!!))
        // Before its quick look (WEB-026) would cover the fan
        compose.mainClock.advanceTimeBy(QUICK_LOOK_MILLIS / 2)
        compose.waitForIdle()
        assertEquals(mountain.bounds.y + top, shown().top, 1f, "the hovered Mountain where it lay")
        captureShot("$SCREENSHOTS/desktop/hand-hover.png",
            "With the mouse on Alice's Mountain in her desktop table's hand fan, the Mountain is drawn over its neighbours where it lies, neither moved nor turned, its Play chip faded above it.")
        compose.onAllNodes(isRoot()).onFirst().performMouseInput {
            click(androidx.compose.ui.geometry.Offset(mountain.handSlot!!.center.x, mountain.handSlot!!.center.y + top))
        }
        compose.waitForIdle()
        // The mouse leaves for the empty battlefield: the Mountain stays out
        compose.onAllNodes(isRoot()).onFirst().performMouseInput { moveTo(androidx.compose.ui.geometry.Offset(600f, 400f)) }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(mountain.cardId, kit.toolkit.hand.raised, "the click raised the Mountain") }
        val raised = shown()
        assertTrue(raised.top < mountain.bounds.y + top - 40f && raised.bottom <= 900f - DesktopTable.FAN_LIFT + 1f,
            "popped out whole into view: $raised")
        captureShot("$SCREENSHOTS/desktop/hand-raised.png",
            "Alice clicked her Mountain in the desktop table's hand fan and moved the mouse away: it popped out of the fan along its own turn, keeping its angle, whole in view and over the others, its Play chip above it.")
    }

    // TBL-139, TBL-146, TBL-147 (owner 2026-11-16): with "Battlefield cards: full picture" on in the desktop layout's Settings, Alice's battlefield
    // shows its cards as their printed pictures with a thin border in her colour, while the Stack and her piles keep the app's
    // frame
    @Test fun battlefieldCardsAsFullPictures() {
        val preferences = CardAppearancePreferences(org.asyncttwiz.appui.AppPlatform.current, AppLayout.Desktop)
        preferences.fullPicture(hand = false, enabled = true)
        try {
            val kit = respondingKit()
            val appearance = preferences.read()
            assertTrue(appearance.battlefieldFullPicture && appearance.handFullPicture, "both on: $appearance")
            val items = scene(kit, pictures = true, appearance = appearance).items.filter { it.kind == TableItemKind.CARD && it.artwork != null }
            val field = items.filter { it.zone == PlayZone.BATTLEFIELD }
            assertTrue(field.isNotEmpty() && field.all { it.fullPicture && it.artwork?.size == "normal" }, "battlefield full pictures: $field")
            val stack = items.filter { it.zone == PlayZone.STACK }
            assertTrue(stack.isNotEmpty() && stack.none { it.fullPicture }, "the Stack keeps the app's frame: $stack")
            render(kit, pictures = true)
            compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle()
            captureShot("$SCREENSHOTS/desktop/full-picture-battlefield.png",
                "With \"Battlefield cards: full picture\" turned on in Settings, the battlefield cards on Alice's desktop table show their whole printed card pictures with a thin border in their owner's colour while she responds to Bob's spell, which keeps the app's frame on the Stack; her hand's cards are full pictures too, as by default on the desktop layout.")
        } finally {
            preferences.fullPicture(hand = false, enabled = false)
        }
    }

    // WEB-028: on the desktop layout a right-click on a control does its hold action and never its tap; a left click
    // taps it
    @Test fun aRightClickOnAControlDoesItsHold() {
        var taps = 0
        var holds = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalAppLayout provides AppLayout.Desktop) {
                    ActionSquare("End turn test", "", true, Modifier.testTag("square"), holdName = "End turn", onHold = { holds++ },
                        onClick = { taps++ })
                }
            }
        }
        compose.onNodeWithTag("square").performMouseInput {
            press(androidx.compose.ui.test.MouseButton.Secondary); release(androidx.compose.ui.test.MouseButton.Secondary)
        }
        compose.runOnIdle { assertEquals(1 to 0, holds to taps, "the right-click held it") }
        compose.onNodeWithTag("square").performMouseInput { click() }
        compose.runOnIdle { assertEquals(1 to 1, holds to taps, "the left click tapped it") }
    }

    // WEB-015, WEB-102, WEB-071, WEB-073, WEB-016, WEB-076, WEB-018, WEB-022, WEB-017, WEB-078, STK-007, TBL-012, TBL-121: the desktop table's arrangement: the battlefields
    // end at the dock and reach from the top row to the window's bottom, laid out above what floats over their bottom (owner
    // playtest 2026-11-15); the other player's seat header is a box floating at the top left (owner notes 2026-11-16) with their
    // hand as a small fan of backs; yours floats at the bottom left, each as tall as the action area at the bottom right, the
    // battlefields' column 0 just right of both in the opening view; your hand is a fan between yours and the action area, its
    // cards partly below the window's bottom, the part in view as tall as the action area; the Stack is the HUD at the
    // battlefields' right edge, left of the dock
    @Test fun theSceneLaysOutSeatHeadersHandAndBattlefieldsForTheDock() {
        val kit = EngineTableKit()
        kit.opening()
        val scene = scene(kit)
        val height = 900f - DesktopTable.topRow(1440f)
        val world = assertNotNull(scene.world)
        val laidOut = world.viewport
        assertTrue(laidOut.right <= 1440f - DesktopTable.DOCK, "the battlefields end at the dock: $laidOut")
        assertEquals(height, assertNotNull(world.clipViewport).bottom, .5f, "drawn down to the window's bottom")
        assertEquals(0f, world.clipViewport!!.y, "drawn up to the top row")
        assertTrue(laidOut.bottom <= height - DesktopTable.FLOAT, "laid out (fitted, TBL-012) above the floats: $laidOut")
        assertTrue(laidOut.y <= 4f, "the rows take the room up to the top row (no strip across the top): $laidOut")
        val bobLife = scene.items.single { it.kind == TableItemKind.LIFE && it.owner == kit.f.b }.bounds
        val aliceLife = scene.items.single { it.kind == TableItemKind.LIFE && it.owner == kit.f.a }.bounds
        val margin = (DesktopTable.SEAT - DesktopTable.LIFE) / 2
        assertEquals(DesktopTable.FLOAT - DesktopTable.SEAT + margin, bobLife.y, "Bob's life square at the bottom of his box at the top")
        assertEquals(DesktopTable.PLATE_PAD, bobLife.x, "his avatar and name over it")
        assertEquals(height - DesktopTable.SEAT + margin, aliceLife.y, "Alice's life square at the bottom of her seat header")
        assertEquals(DesktopTable.PLATE_PAD, aliceLife.x, "her avatar and name over it")
        assertEquals(DesktopTable.LIFE, aliceLife.height)
        val plate = assertNotNull(scene.desktopPlates[kit.f.a])
        assertEquals(TableBounds(0f, height - DesktopTable.FLOAT, DesktopTable.pilesRight(DesktopTable.PLATE_PAD, 3) + DesktopTable.PLATE_PAD,
            DesktopTable.FLOAT), plate, "her seat header floating at the bottom left, as tall as the action area")
        val bobsHandItem = scene.items.single { it.kind == TableItemKind.HAND && it.owner == kit.f.b }
        val bobsPlate = assertNotNull(scene.desktopPlates[kit.f.b])
        assertEquals(TableBounds(0f, 0f, bobsHandItem.bounds.right + DesktopTable.PLATE_PAD, DesktopTable.FLOAT), bobsPlate,
            "his seat header a box at the top left, as wide as its contents (his life square, piles and hand's fan), as tall as hers")
        assertEquals(3, scene.floats.size, "both seat headers and the action area float with backgrounds of their own")
        assertTrue(scene.floats.any { it.first == bobsPlate } && scene.floats.any { it.first == plate }, "the seat boxes: ${scene.floats}")
        assertTrue(scene.floats.any { it.first == TableBounds(1440f - DesktopTable.ACTIONS, height - DesktopTable.FLOAT, DesktopTable.ACTIONS,
            DesktopTable.FLOAT) }, "the action area: ${scene.floats}")
        // TBL-121: the opening view puts the battlefields' column 0 just right of both boxes
        val boxes = maxOf(plate.right, bobsPlate.right)
        assertTrue(scene.fields.all { kotlin.math.abs(it.originX - boxes) < .5f }, "column 0 right of the boxes at $boxes: ${scene.fields.map { it.originX }}")
        // WEB-016, WEB-076, WEB-022, WEB-084: the piles twice the landscape bar row's 32 dp, inside their seat header with their count bubbles
        val piles = scene.items.filter { it.kind == TableItemKind.PILE && it.zone in listOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE) }
        assertEquals(6, piles.size)
        assertTrue(piles.all { it.bounds.height == DesktopTable.PILE_HEIGHT }, "piles 64 tall: ${piles.map { it.bounds }}")
        val bobsPiles = piles.filter { it.bounds.y < DesktopTable.FLOAT }
        assertTrue(bobsPiles.size == 3 && bobsPiles.all { it.pileCountBubble(DesktopTable.PILE_BADGE).y >= DesktopTable.FLOAT - DesktopTable.SEAT &&
                it.bounds.bottom <= DesktopTable.FLOAT },
            "Bob's piles and their bubbles inside his seat header's row: ${bobsPiles.map { it.bounds }}")
        // WEB-022, WEB-084: battlefield cards 2× (before the opening zoom, TBL-121)
        assertEquals(2f * 28f, scene.cardWidth / scene.camera.zoom, .5f)
        // WEB-015, WEB-073, WEB-084 (owner playtest 2026-11-15): her hand a fan at 5.2×, centred between her seat header and the action area,
        // turned and overlapping, the part in view at rest as tall as the action area
        val hand = scene.items.filter { it.kind == TableItemKind.CARD && it.zone == PlayZone.HAND && it.owner == kit.f.a }
        assertEquals(7, hand.size)
        assertTrue(hand.all { it.cardTransform?.height == 208f }, "hand cards at 5.2×: ${hand.map { it.cardTransform }}")
        assertTrue(hand.first().cardTransform!!.rotation < 0f && hand.last().cardTransform!!.rotation > 0f, "turned as a fan")
        assertTrue(hand.zipWithNext().all { (a, b) -> b.handSlot!!.x < a.bounds.right }, "overlapping")
        val middle = (hand.minOf { it.handSlot!!.x } + hand.maxOf { it.handSlot!!.right }) / 2
        val room = (plate.right + 8f + 1440f - DesktopTable.ACTIONS - 8f - TablePresentation.FAN_COUNT) / 2
        assertEquals(room, middle, 1f, "centred between her seat header and the action area")
        val centre = hand[3]
        assertEquals(height - DesktopTable.FLOAT, centre.bounds.y, 1f, "the part in view as tall as the action area")
        assertTrue(hand.all { it.bounds.bottom > height }, "partly below the window's bottom")
        // Owner 2026-11-16 (the fan was cut at its left): every card shows whole at the fan's sides, its turned corners too
        assertEquals(hand.first().bounds.x, hand.first().visible.minOf { it.x }, .5f, "the leftmost card's turned corner in view")
        assertTrue(hand.first().bounds.x >= plate.right, "clear of her seat header: ${hand.first().bounds}")
        // The card the mouse is on is only drawn over the others (owner 2026-11-16), neither moved nor turned
        fun desktopScene(layout: TableLayoutState) = TablePresentation.scene(kit.f.game, kit.alice.play(), kit.f.a, 1440f, height, true,
            layout, appearance = DesktopCardAppearance, landscapeHome = true, handRow = HandRow.cards(kit.alice.play(), kit.f.a),
            desktop = DesktopFrame())
        fun handOf(shown: TableScene) = shown.items.filter { it.kind == TableItemKind.CARD && it.zone == PlayZone.HAND && it.owner == kit.f.a }
        val hoveredScene = desktopScene(kit.toolkit.layoutState.value.copy(hovered = centre.cardId))
        val hovered = handOf(hoveredScene)
        assertEquals(centre.cardId, hovered.last().cardId, "drawn over the others")
        assertEquals(centre.bounds, hovered.last().bounds, "not moved")
        assertEquals(centre.cardTransform, hovered.last().cardTransform, "not turned")
        // ZON-079 (owner 2026-11-16): a raised card pops out of the fan along its own turn, keeping it, until it is whole in view
        val turned = hand[1]
        val raisedScene = desktopScene(kit.toolkit.layoutState.value.copy(raised = turned.cardId, hovered = centre.cardId))
        val raisedHand = handOf(raisedScene)
        val raised = raisedHand.single { it.cardId == turned.cardId }
        assertTrue(raised.raised)
        assertEquals(centre.cardId, raisedHand.last().cardId, "the hovered card over the raised one")
        assertEquals(turned.cardId, raisedHand[raisedHand.lastIndex - 1].cardId, "the raised card over the rest")
        assertEquals(turned.cardTransform, raised.cardTransform, "still turned as in the fan")
        assertTrue(turned.cardTransform!!.rotation < 0f)
        assertEquals(height - DesktopTable.FAN_LIFT, raised.bounds.bottom, .5f, "whole in view")
        val dx = raised.bounds.center.x - turned.bounds.center.x
        val dy = raised.bounds.center.y - turned.bounds.center.y
        val radians = Math.toRadians(turned.cardTransform!!.rotation.toDouble())
        assertTrue(dy < -40f, "popped out upwards: $dy")
        assertEquals(kotlin.math.tan(radians).toFloat(), -dx / dy, .01f, "along its own turn")
        assertEquals(raised.bounds, raised.visible.single(), "whole, uncut")
        // The mouse finds each card by its own part of the fan, popped out or not, and a popped-out card above the fan too
        val neighbour = hand[2]
        assertEquals(neighbour.cardId, raisedScene.hit(TablePoint(neighbour.handSlot!!.center.x, height - 20f))?.cardId,
            "the card beside it is still found by its part of the fan")
        assertEquals(turned.cardId, raisedScene.hit(TablePoint(raised.bounds.center.x, height - DesktopTable.FLOAT - 30f))?.cardId,
            "the popped-out card above the fan")
        val bobsHand = scene.items.filter { it.kind == TableItemKind.CARD && it.zone == PlayZone.HAND && it.owner == kit.f.b }
        assertTrue(bobsHand.isNotEmpty() && bobsHand.all { it.bounds.y >= DesktopTable.FLOAT - DesktopTable.SEAT && it.bounds.bottom <= DesktopTable.FLOAT },
            "Bob's hand in his seat header's row")
        // WEB-016, WEB-076, ZON-001, ZON-126 (owner, desktop playtest 2026-11-14: "Opponent hand should be a fan like in mobile?"): Bob's hand is
        // the phone's small fan of card backs after his piles, its cards the piles' size times FAN_CARD, turned and overlapping,
        // its count at its right
        assertTrue(scene.items.single { it.kind == TableItemKind.HAND && it.owner == kit.f.b }.handFan, "Bob's hand drawn as a fan")
        val fanCard = DesktopTable.PILE_HEIGHT * TablePresentation.FAN_CARD
        assertTrue(bobsHand.all { it.cardTransform?.height == fanCard }, "fan cards ${fanCard} tall: ${bobsHand.map { it.cardTransform }}")
        assertTrue(bobsHand.first().cardTransform!!.rotation < 0f && bobsHand.last().cardTransform!!.rotation > 0f, "turned as a fan")
        assertTrue(bobsHand.zipWithNext().all { (a, b) -> b.bounds.x < a.bounds.right }, "overlapping: ${bobsHand.map { it.bounds }}")
        assertTrue(bobsHand.all { it.bounds.x > bobsPiles.maxOf { pile -> pile.bounds.right } }, "after his piles")
        val stackItem = scene.items.single { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }
        val stack = stackItem.bounds
        val region = assertNotNull(scene.stackRegion)
        assertTrue(region.right <= 1440f - DesktopTable.DOCK + .5f && region.right >= laidOut.right - 1f && stack.x > laidOut.center.x,
            "the Stack at the battlefields' right edge, left of the dock: $stack, $region, battlefields $laidOut")
        assertTrue(stackItem.stackBadge.x >= laidOut.x && stackItem.stackBadge.right <= 1440f - DesktopTable.DOCK,
            "its count bubble on the battlefields, nothing of it under the dock: ${stackItem.stackBadge}")
    }

    // WEB-015, WEB-016, WEB-104, WEB-017, WEB-078, WEB-018, WEB-020, WEB-083, TBL-117, STK-007: the table at Alice's first main phase: the top row with the
    // turn line, move pill and phase track (no step button), the seat headers with avatars and names, the empty Stack at the
    // battlefields' right edge beside the chat dock, the action area with Undo and the main button
    @Test fun theDesktopTableHasTopRowSeatHeadersDockAndActionArea() {
        val kit = EngineTableKit()
        kit.opening()
        render(kit)
        compose.onNodeWithTag("phase-track").assertExists()
        compose.onNodeWithContentDescription("Choose phase/step", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("desktop-dock").assertExists()
        compose.onNodeWithText("STACK · 0").assertDoesNotExist()
        val dock = compose.onNodeWithTag("desktop-dock").fetchSemanticsNode().boundsInRoot
        val stack = compose.onNodeWithContentDescription("Stack, 0 entries").fetchSemanticsNode().boundsInRoot
        assertTrue(stack.right <= dock.left, "the Stack on the field, left of the chat dock: $stack, dock $dock")
        compose.onNodeWithText("rematch after this one?").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close chat").assertDoesNotExist()
        compose.onNodeWithTag("action-area").assertExists()
        compose.onNodeWithContentDescription("Undo").assertExists()
        // WEB-018, WEB-079 (owner, desktop playtest 2026-11-14): Undo a real button a third of the bottom row wide, the main button the
        // other two thirds, lined up with no room between them but the quick actions' gap
        val area = compose.onNodeWithTag("action-area").fetchSemanticsNode().boundsInRoot
        val undo = compose.onNodeWithTag(DESKTOP_UNDO_TAG).fetchSemanticsNode().boundsInRoot
        val main = compose.onNodeWithTag("main-button").fetchSemanticsNode().boundsInRoot
        val pad = DesktopTable.ACTIONS_PADDING * DesktopTable.UI_SCALE
        val row = main.right - undo.left
        // a third of the row and two thirds, with the quick actions' gap between them
        val gap = org.asyncttwiz.android.DesktopTable.ACTION_GAP * org.asyncttwiz.android.DesktopTable.UI_SCALE
        assertEquals(area.width - 2 * pad, row, 2f, "the row spans the action area: $undo, $main in $area")
        assertEquals((row - 2 * gap) / 3, undo.width, 1.5f, "Undo a third: $undo of $row")
        assertEquals(row - (row - 2 * gap) / 3 - gap, main.width, 1.5f, "the main button two thirds: $main of $row")
        assertTrue(main.left - undo.right in 0f..(DesktopTable.ACTION_GAP * DesktopTable.UI_SCALE + 1f), "no room between them: $undo, $main")
        assertEquals(main.top, undo.top, 1f, "lined up")
        assertEquals(main.height, undo.height, 1f, "as tall")
        compose.onAllNodesWithText("Bob").onFirst().assertExists()
        compose.onNodeWithText("You").assertExists()
        captureShot("$SCREENSHOTS/desktop/table.png",
            "Alice's desktop table at her first main phase: the top row with her turn, the move pill and the phase track, Bob's seat header in a box at the top left with his hand as a small fan of card backs after his piles, as on the phone, and hers above her larger hand, where the Mountain she may play glows in cyan as on the phone, the empty Stack at the battlefields' right edge and the game chat docked beside it at the right, and at the bottom right the quick actions over a row of Undo, a real button a third of it wide, and the main button in the other two thirds, lined up.")
    }

    /** Bob casts Storm Spark at Alice's Ember Cub in his turn and she holds priority over it. */
    private fun respondingKit(): EngineTableKit {
        val kit = EngineTableKit()
        val cub = kit.toReadyCub()
        kit.bobCastsSpark { s -> TargetRef.Object(s.battlefield.first { (it as arcanum.engine.state.ObjectId.Card).card.value == cub }) }
        kit.scenario.confirmStops[kit.f.a] = false
        assertTrue(kit.toolkit.automatumStep(TableActionIntent.End(kit.scope)), "End turn")
        kit.playOn("Alice holding priority over Bob's Spark", rounds = 40) {
            val s = kit.state()
            s.stack.isNotEmpty() && s.priority.holder == kit.me && kit.routes.arrowShown() && s.stackItems[s.stack.last()]?.kind == StackItemKind.Spell
        }
        return kit
    }

    // WEB-017, WEB-078, WEB-016, WEB-077, STK-007, STK-057, STK-058 (D1): Bob casts Storm Spark at Alice's Ember Cub and she holds priority: the
    // Spark is on the Stack at the battlefields' right edge (never in the dock), its arrow to the Cub, the band at the
    // battlefields' top beside Bob's seat box says what it does, the main button reads Resolve
    @Test fun respondingShowsTheStackOnTheField() {
        render(respondingKit())
        compose.onNodeWithText("STACK · 1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Stack, 1 entries").assertExists()
        compose.onNodeWithTag("stack-line").assert(hasContentDescription("Bob cast Storm Spark · deals 3 damage to Ember Cub"))
        captureShot("$SCREENSHOTS/desktop/table-responding.png",
            "Bob cast Storm Spark at Alice's Ember Cub in his turn and she holds priority: the Spark is on the Stack at the battlefields' right edge with its arrow to the Cub, the band at the battlefields' top beside Bob's seat box says what it does, and the main button at the bottom right reads Resolve.")
    }

    // WEB-016, WEB-071, WEB-077, TBL-121, TBL-012, TRN-066, TRN-150, STK-057 (owner notes 2026-11-16: "An opponent's life, hand etc could sit in a similar box
    // as yours. Default camera pan could be so that these are on the left side of the 'pos 0' so that they don't obstruct cards
    // played"): with Bob's Mountains and Alice's Mountain and Ember Cub out, Bob's seat header is a box at the top left like
    // hers at the bottom left; the table opens with every battlefield card just right of both boxes, the rows reaching up beside
    // Bob's box, and fitted (F) it still shows them all right of the boxes; the band saying what Bob's Spark does lies at the
    // battlefields' top beside his box
    @Test fun theSeatBoxesCoverNoCardInTheOpeningViewOrTheFittedTable() {
        val kit = respondingKit()
        val opening = scene(kit)
        val fitted = scene(kit, layout = kit.toolkit.layoutState.value.copy(camera = assertNotNull(opening.world).fit()))
        for ((view, shown) in listOf("opening" to opening, "fitted" to fitted)) {
            val boxes = shown.desktopPlates.values
            assertEquals(2, boxes.size, "both seat boxes ($view)")
            val cards = shown.items.filter { it.battlefieldCard }
            assertTrue(cards.any { it.owner == kit.f.b } && cards.any { it.owner == kit.f.a }, "both players' cards out ($view): ${cards.map { it.label }}")
            assertTrue(cards.all { card -> boxes.none { it.intersection(card.bounds) != null } },
                "no card under a seat box ($view): ${cards.map { it.label.substringBefore(",") to it.bounds }}, boxes $boxes")
            val bobsTop = cards.filter { it.owner == kit.f.b }.minOf { it.bounds.y }
            assertTrue(bobsTop < DesktopTable.FLOAT, "Bob's rows reach up beside his box ($view): $bobsTop")
        }
        render(kit)
        val box = compose.onAllNodesWithTag(DESKTOP_SEAT_BOX_TAG).fetchSemanticsNodes().map { it.boundsInRoot }.minBy { it.top }
        val band = compose.onNodeWithTag("wait-band").fetchSemanticsNode().boundsInRoot
        assertEquals(box.right, band.left, 1f, "the band beside Bob's box: $band, box $box")
        assertEquals(box.top, band.top, 1f, "at the battlefields' top")
        captureShot("$SCREENSHOTS/desktop/opponent-box.png",
            "Bob cast Storm Spark at Alice's Ember Cub in his turn and she holds priority, the table as it opens: Bob's avatar, name, life, piles and hand's fan sit in a box at the top left like Alice's at the bottom left, the battlefields reach up to the top row beside it, every card lies just right of both boxes, and the band saying what the Spark does runs along the battlefields' top beside Bob's box.")
        compose.runOnIdle { assertNotNull(kit.toolkit.fitTable).invoke() }
        compose.waitForIdle()
        captureShot("$SCREENSHOTS/desktop/opponent-box-fitted.png",
            "The same table after Alice pressed F to fit it: the whole table zoomed to fit the room right of the two seat boxes, every card still clear of them.")
    }

    // WEB-017, WEB-078, STK-007 (owner playtest 2026-11-15: "push it to the side"): with the chat dock open the battlefields narrow and the
    // Stack holding Bob's Spark lies at their right edge, left of the dock, nothing covering it; with the dock folded away the
    // battlefields take its width and the Stack moves right with their edge
    @Test fun theStackMovesWithTheBattlefieldsEdgeBesideTheChatDock() {
        val open = androidx.compose.runtime.mutableStateOf(true)
        render(respondingKit(), dockOpen = { open.value })
        val dock = compose.onNodeWithTag("desktop-dock").fetchSemanticsNode().boundsInRoot
        val beside = compose.onNodeWithContentDescription("Stack, 1 entries").fetchSemanticsNode().boundsInRoot
        assertTrue(beside.right <= dock.left && beside.right > dock.left - 120f, "the Stack just left of the open chat dock: $beside, dock $dock")
        captureShot("$SCREENSHOTS/desktop/stack-beside-chat.png",
            "Bob cast Storm Spark at Alice's Ember Cub and she holds priority with the chat dock open at the right: the battlefields end at the dock and the Stack holding the Spark lies at their right edge, beside the chat and not under it.")
        compose.runOnIdle { open.value = false }
        compose.waitForIdle()
        compose.onNodeWithTag("desktop-dock").assertDoesNotExist()
        val folded = compose.onNodeWithContentDescription("Stack, 1 entries").fetchSemanticsNode().boundsInRoot
        assertEquals(beside.right + DesktopTable.DOCK, folded.right, 1f, "folded away, the Stack at the window's right edge: $folded")
    }

    // WEB-015, WEB-016, WEB-020, WEB-022: in a 1920 × 1080 window, with Alice's Mountain and Ember Cub out at her main phase,
    // the table at the desktop's default sizes: the top row, the seat headers and the action area drawn larger as at 1440
    @Config(qualifiers = "w1920dp-h1080dp-land-mdpi")
    @Test fun theDefaultSizesInAFullHdWindow() {
        val kit = EngineTableKit()
        kit.toReadyCub()
        render(kit)
        compose.onNodeWithTag("phase-track").assertIsDisplayed()
        compose.onNodeWithTag("action-area").assertIsDisplayed()
        captureShot("$SCREENSHOTS/desktop/table-1920.png",
            "Alice's desktop table in a 1920 × 1080 window at her main phase with her Mountain and Ember Cub out, at the desktop's default sizes: the top row, the seat headers with their piles, her hand and the action area drawn as at 1440 × 900, the battlefields taking the extra room with all six rows in view.")
    }

    // WEB-022, WEB-084, TBL-034, TBL-121: the owner's ask, battlefield cards twice the default (the Card size slider at 2×, on top of
    // the desktop's own 2×): in a 1440 × 900 window the six rows don't fit even at the opening's least zoom (0.65×), so the
    // table opens centred on the battlefields and Alice's land row with her Mountain lies below the view
    @Test fun battlefieldCardsAtTwiceTheDefault() {
        val preferences = CardAppearancePreferences(org.asyncttwiz.appui.AppPlatform.current, AppLayout.Desktop)
        // Only the battlefield's: the areas keep their own 1× sliders
        preferences.customizeAreas(true)
        preferences.setSize(2f)
        try {
            val kit = EngineTableKit()
            kit.toReadyCub()
            render(kit)
            captureShot("$SCREENSHOTS/desktop/table-battlefield-4x.png",
                "Alice turned on Customize per area and set the Card size slider to 2× on the desktop table (battlefield cards 4× the phone's, twice the default; her hand as before) at her main phase with her Mountain and Ember Cub out: in a 1440 × 900 window the six battlefield rows don't fit even at the opening's least zoom, so the view is centred on the battlefields and her land row with the Mountain lies below it.")
        } finally { preferences.setSize(1f); preferences.customizeAreas(false) }
    }

    // WEB-015, WEB-075, CMB-001, CMB-053, CMB-028 (D2): Alice declares attackers: the combat band opens between the battlefields with her staged
    // Cub, the main button reads Attack and the quick actions are the combat's
    @Test fun declaringAttackersKeepsTheCombatBand() {
        val kit = EngineTableKit()
        val cub = kit.toReadyCub()
        kit.toAttacks()
        kit.toolkit.combat.show(kit.alice.play(), AutomatumBoards.combat(kit.alice.play()))
        assertTrue(kit.toolkit.stage(kit.alice.play(), listOf(cub), null), "the Cub staged")
        kit.settle()
        kit.toolkit.combat.show(kit.alice.play(), AutomatumBoards.combat(kit.alice.play()))
        render(kit)
        assertTrue(scene(kit).combatPanel != null, "the band open")
        captureShot("$SCREENSHOTS/desktop/table-attackers.png",
            "Alice declares attackers on the desktop table: her Ember Cub is staged in the combat band's lane against Bob between the battlefields, the combat's quick actions sit over the main button, which reads Attack.")
    }

    // WEB-019, STK-047 (D3): picking targets needs the board: the target bar is a strip at the bottom centre of the
    // battlefields, on Alice's seat header, while the legal targets glow and nothing is dimmed
    @Test fun aDecisionThatNeedsTheBoardIsAStripBottomCentre() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(Choices.CHARM, "Mountain"))))
        kit.opening()
        assertTrue(kit.toolkit.cast.start(kit.atw(kit.hand(Choices.CHARM).first())), "the cast begins: ${kit.toolkit.error}")
        render(kit)
        compose.onNodeWithText("Cinder Charm deals 1 damage to any target.").performClick()
        compose.waitForIdle()
        assertTrue(kit.toolkit.cast.targeting, "targets are picked on the table")
        val bar = compose.onNodeWithContentDescription("Choose targets").fetchSemanticsNode().boundsInRoot
        val tableRight = 1440f - DesktopTable.DOCK
        assertTrue(kotlin.math.abs((bar.left + bar.right) / 2 - tableRight / 2) < 2f, "centred on the battlefields: $bar")
        assertTrue(bar.width <= DesktopTable.PROMPT_WIDTH + 1f, "at most the strip's width: $bar")
        val seatTop = 900f - DesktopTable.band(false)
        assertTrue(kotlin.math.abs(bar.bottom - seatTop) < 2f, "on the top of what floats at the bottom: $bar vs $seatTop")
        compose.onAllNodesWithTag("prompt-dim").assertCountEquals(0)
        captureShot("$SCREENSHOTS/desktop/prompt-strip.png",
            "Alice chose Cinder Charm's damage mode on the desktop table: \"Choose any target\" is a strip at the bottom centre of the battlefields, right on her seat header, while the legal targets glow on the undimmed table.")
    }

    // WEB-019, WEB-080, WEB-081, STK-023, STK-052 (D3b): a choice that doesn't need the board is a centred overlay over the battlefields with the
    // table and the dock dimmed; its go-on (Cast) is the main button in the undimmed action area
    @Test fun aChoiceThatDoesntNeedTheBoardIsACentredOverlay() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(Choices.CHARM, "Mountain"))))
        kit.opening()
        assertTrue(kit.toolkit.cast.start(kit.atw(kit.hand(Choices.CHARM).first())), "the cast begins: ${kit.toolkit.error}")
        render(kit)
        val panel = compose.onNodeWithContentDescription("Cast panel").fetchSemanticsNode().boundsInRoot
        val tableRight = 1440f - DesktopTable.DOCK
        assertTrue(kotlin.math.abs((panel.left + panel.right) / 2 - tableRight / 2) < 2f, "centred on the battlefields: $panel")
        assertEquals(DesktopTable.OVERLAY_WIDTH, panel.width, 1f)
        compose.onAllNodesWithTag("prompt-dim").assertCountEquals(3)
        // WEB-019, WEB-081 (owner playtest 2026-11-15): its go-on, greyed as the main button's until a mode is chosen, in its bottom right
        // corner too
        val goOn = compose.onNodeWithTag(PANEL_GO_ON_TAG).assert(hasContentDescription("Cast", substring = true))
            .fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("main-button").assert(hasContentDescription("Cast", substring = true))
        assertTrue(kotlin.math.abs(goOn.right - panel.right) < 24f && goOn.top >= panel.bottom - 1f && goOn.top < panel.bottom + 24f,
            "in the overlay's bottom right corner, under its content: $goOn, $panel")
        captureShot("$SCREENSHOTS/desktop/prompt-overlay.png",
            "Alice began casting Cinder Charm on the desktop table: its modes are a centred overlay over the battlefields with the table and the dock dimmed behind it, Cast greyed in its bottom right corner as on the main button, while the top row and the action area stay as they are.")
    }

    // WEB-019, WEB-080, WEB-081 (owner playtest 2026-11-15: the panels float in the centre, away from the main button): Alice casts Ember Burst
    // with a Mountain out: its X panel is a centred overlay with Cast, ready at X 0, in its bottom right corner as on the main
    // button; a click on it goes on as the main button does, to the target strip
    @Test fun aPanelHasItsGoOnInItsCorner() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(Choices.BURST, "Mountain"))))
        kit.opening()
        kit.playLand()
        assertTrue(kit.toolkit.cast.start(kit.atw(kit.hand(Choices.BURST).first())), "the cast begins: ${kit.toolkit.error}")
        render(kit)
        compose.onNodeWithTag(PANEL_GO_ON_TAG).assertIsEnabled().assert(hasContentDescription("Cast", substring = true))
        compose.runOnIdle { assertEquals(null, kit.toolkit.mainSlot.action?.refusal, "Cast can be done") }
        captureShot("$SCREENSHOTS/desktop/panel-go-on.png",
            "Alice began casting Ember Burst with a Mountain out on the desktop table: its X panel is the centred overlay, and Cast, ready at X 0, sits in its bottom right corner as well as on the main button at the bottom right.")
        compose.onNodeWithTag(PANEL_GO_ON_TAG).performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(kit.toolkit.cast.targeting, "the corner's Cast went on to the targets") }
    }

    /** Bob kept and Alice mulliganed twice: the Keep / Bottom overlay asks for one card for the bottom; the overlay's bounds,
     * its Bottom area's and its eye's, in the window. */
    private fun keepBottomOverlay(): Triple<androidx.compose.ui.geometry.Rect, androidx.compose.ui.geometry.Rect, androidx.compose.ui.geometry.Rect> {
        val kit = EngineTableKit()
        kit.bob.keep()
        repeat(2) { round ->
            val before = kit.state().player(kit.me).hand.toList()
            assertTrue(kit.toolkit.opening(TableActionIntent.OpeningMulligan(kit.scope)), "mulligan ${round + 1}")
            kit.playOn("Alice's hand after mulligan ${round + 1}") { kit.state().player(kit.me).hand.toList() != before }
        }
        kit.playOn("one card owed for the bottom") { AutomatumOpening.bottomOwed(kit.alice.play()) == 1 }
        render(kit)
        compose.waitForIdle()
        val panel = compose.onNodeWithContentDescription("Keep or bottom").fetchSemanticsNode().boundsInRoot
        val bottom = compose.onNodeWithContentDescription("Bottom area").fetchSemanticsNode().boundsInRoot
        val eye = compose.onAllNodes(hasContentDescription("mulligan panel", substring = true)).onFirst().fetchSemanticsNode().boundsInRoot
        return Triple(panel, bottom, eye)
    }

    /** WEB-019, WEB-082: [panel] whole in a [width] × [height] window, its Bottom area and eye whole inside it. */
    private fun assertWhole(width: Float, height: Float, panel: androidx.compose.ui.geometry.Rect,
        bottom: androidx.compose.ui.geometry.Rect, eye: androidx.compose.ui.geometry.Rect) {
        assertTrue(panel.top >= DesktopTable.topRow(width) && panel.bottom <= height && panel.right <= width - DesktopTable.DOCK,
            "the overlay in the window over the battlefields: $panel")
        assertTrue(bottom.top >= panel.top && bottom.bottom <= eye.top + 1f && eye.bottom <= panel.bottom + 1f,
            "the Bottom area whole above the eye, inside the overlay: $bottom, $eye in $panel")
    }

    // WEB-019, WEB-080, WEB-082 (coordinator, 2026-11-14: a desktop panel always fits its room; the Keep / Bottom overlay's Bottom area was cut
    // off at 1440 × 900): right after Alice's second mulligan the Keep / Bottom overlay is drawn only as large as fits, its
    // whole Bottom area and its eye in view
    @Test fun theKeepBottomOverlayFitsItsRoom() {
        val (panel, bottom, eye) = keepBottomOverlay()
        assertWhole(1440f, 900f, panel, bottom, eye)
        captureShot("$SCREENSHOTS/desktop/keep-bottom-overlay.png",
            "Bob kept and Alice mulliganed twice on the desktop table at 1440 × 900: the Keep / Bottom overlay is drawn as large as fits over the battlefields (less than 1.3×), its Keep area with her seven cards, its whole Bottom area and its eye in view, OK on the main button.")
    }

    // WEB-019, WEB-082: the same in a 1280 × 720 window: drawn smaller still, the overlay whole in its room with its eye (where even
    // 1× doesn't fit, its content scrolls as the phone's does)
    @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    @Test fun theKeepBottomOverlayFitsASmallWindow() {
        val (panel, _, eye) = keepBottomOverlay()
        assertTrue(panel.top >= DesktopTable.topRow(1280f) && panel.bottom <= 720f && eye.bottom <= panel.bottom + 1f,
            "the overlay and its eye in the window: $panel, $eye")
        captureShot("$SCREENSHOTS/desktop/keep-bottom-overlay-1280.png",
            "The same Keep / Bottom overlay in a 1280 × 720 window: drawn smaller to fit the room over the battlefields, whole with its Bottom area and its eye.")
    }

    // WEB-019, WEB-080, WEB-082: a cast panel with modes in a 1280 × 720 window: the centred overlay fits the room over the battlefields
    @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    @Test fun aCastPanelFitsASmallWindow() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(Choices.CHARM, "Mountain"))))
        kit.opening()
        assertTrue(kit.toolkit.cast.start(kit.atw(kit.hand(Choices.CHARM).first())), "the cast begins: ${kit.toolkit.error}")
        render(kit)
        compose.waitForIdle()
        val panel = compose.onNodeWithContentDescription("Cast panel").fetchSemanticsNode().boundsInRoot
        assertTrue(panel.top >= DesktopTable.topRow(1280f) && panel.bottom <= 720f, "the cast panel in the window: $panel")
        compose.onNodeWithText("You gain 2 life.").assertIsDisplayed()
        captureShot("$SCREENSHOTS/desktop/prompt-overlay-1280.png",
            "Alice began casting Cinder Charm on the desktop table in a 1280 × 720 window: its modes are the centred overlay, drawn as large as fits the room over the battlefields.")
    }

    // WEB-020, WEB-021, WEB-017 (D5), LOB-138: the real table screen in the desktop layout, on a game opened from the server:
    // the chat docked and read as it shows; Table actions → Timeline puts the strip under the hand and Previous / Auto / Next
    // in the action area, Return to current in the top row
    @Test fun theTableScreenInTheDesktopLayoutAndItsTimeline() {
        val f = EngineLobbyFixture()
        val incoming = ChatMessage("incoming", f.game.id, f.game.playId!!, f.other, "good luck!", "1", "date")
        try {
            f.connect(f.head(chat = listOf(incoming), otherKept = true))
            f.keep()
            compose.setContent {
                MaterialTheme { CompositionLocalProvider(LocalAppLayout provides AppLayout.Desktop) { TableScreen(f.lobby, f.me, {}, true) } }
            }
            drain(f)
            compose.onNodeWithText("good luck!").assertIsDisplayed()
            compose.runOnIdle { assertTrue(f.lobby.chatDocked, "the chat docked") }
            compose.onNodeWithTag("phase-track").assertExists()
            compose.onNodeWithContentDescription("Table actions").performClick()
            compose.onNodeWithText("Timeline").assertIsEnabled().performClick()
            drain(f)
            compose.onNodeWithContentDescription("Return to current").assertExists()
            compose.onNodeWithContentDescription("Playback settings").assertExists()
            val strip = compose.onNodeWithContentDescription("Playback settings").fetchSemanticsNode().boundsInRoot
            assertTrue(strip.right <= 1440f - DesktopTable.ACTIONS + 1f && strip.bottom >= 900f - TableTimelineHeight - 1f,
                "the strip along the bottom band, left of the action area: $strip")
            val next = compose.onNodeWithContentDescription("Previous").fetchSemanticsNode().boundsInRoot
            assertTrue(next.left >= 1440f - DesktopTable.ACTIONS, "Previous / Auto / Next in the action area: $next")
            captureShot("$SCREENSHOTS/desktop/table-timeline.png",
                "Alice opened Table actions → Timeline on the desktop table: the seat headers turn grey and the board dims, the timeline strip runs along the bottom band under her hand, Previous / Auto / Next take the action area, and Return to current sits in the top row.")
        } finally { f.close() }
    }
}
