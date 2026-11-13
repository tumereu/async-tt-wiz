package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The desktop table (docs/specs/web-client.md §7.2, wireframes D1–D5) at 1440 × 900: the same game table as the phone's
 * arranged for a large window: the global top row with the phase track, each player's seat header on their battlefield,
 * the right dock with the Stack list and the docked chat, your hand centred in the bottom band, the action area at the
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
     * surface with the desktop frame, the dock holding the Stack list and the docked chat of [lobby]. */
    private fun render(kit: EngineTableKit, lobby: LobbyController = chatLobby(kit)) {
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalAppLayout provides AppLayout.Desktop, LocalEngineRoutes provides kit.routes,
                        LocalDesktopFrame provides DesktopFrame()) {
                        val play = kit.alice.play()
                        Column(Modifier.fillMaxSize().background(Color(0xfff3f5f5))) {
                            DesktopTopRow(kit.f.game, play, kit.f.a, kit.toolkit, reviewing = false, navigation = {}, returnCurrent = null,
                                changes = null, dockOpen = true, toggleDock = {}, chat = null, chatUnread = 0)
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                TableSurface(kit.f.game, play, kit.f.a, true, Modifier.fillMaxSize(), connected = true,
                                    onIntent = kit.toolkit::handle, toolkit = kit.toolkit,
                                    runtime = TableRuntimeHooks(navigation = {}, undo = { kit.routes.undo() }, controlsConnected = true,
                                        waitingLine = kit.routes.waitingLine(play),
                                        desktopDock = { DesktopDock(play, kit.f.a, gameNames(kit.f.game)) { m -> ChatPanel(lobby, kit.f.a, m, docked = true) } }))
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Alice's scene of her desktop table (the surface under the top row: 1440 × 844). */
    private fun scene(kit: EngineTableKit): TableScene = kit.alice.play().let { play ->
        TablePresentation.scene(kit.f.game, play, kit.f.a, 1440f, 900f - DesktopTable.TOP_ROW, true, kit.toolkit.layoutState.value,
            appearance = DesktopCardAppearance, landscapeHome = true, handRow = HandRow.cards(play, kit.f.a), desktop = DesktopFrame())
    }

    // WEB-015, WEB-016, WEB-018, WEB-022: the desktop table's arrangement: the battlefields end at the dock, the other player's
    // seat header is at the top with their hand's small backs, yours lies right above the bottom band, your hand is centred
    // in the band left of the action area at the desktop's larger size, and the Stack isn't on the battlefields
    @Test fun theSceneLaysOutSeatHeadersHandAndBattlefieldsForTheDock() {
        val kit = EngineTableKit()
        kit.opening()
        val scene = scene(kit)
        val height = 900f - DesktopTable.TOP_ROW
        val band = DesktopTable.band(DesktopCardAppearance, false)
        val world = assertNotNull(scene.world).viewport
        assertTrue(world.right <= 1440f - DesktopTable.DOCK, "the battlefields end at the dock: $world")
        val bobLife = scene.items.single { it.kind == TableItemKind.LIFE && it.owner == kit.f.b }.bounds
        val aliceLife = scene.items.single { it.kind == TableItemKind.LIFE && it.owner == kit.f.a }.bounds
        assertEquals(0f, bobLife.y, "Bob's seat header at the top")
        assertEquals(height - band - DesktopTable.SEAT, aliceLife.y, "Alice's seat header right above the bottom band")
        assertEquals(DesktopTable.NAME, aliceLife.x, "after the avatar and name")
        assertTrue(world.y >= bobLife.bottom && world.bottom <= aliceLife.y, "the battlefields between the seat headers: $world")
        val hand = scene.items.filter { it.kind == TableItemKind.CARD && it.zone == PlayZone.HAND && it.owner == kit.f.a }
        assertEquals(7, hand.size)
        assertTrue(hand.all { it.bounds.height == 80f }, "hand cards at 2×")
        val middle = (hand.minOf { it.bounds.x } + hand.maxOf { it.bounds.right }) / 2
        val bandMiddle = (8f + 1440f - DesktopTable.ACTIONS - 8f) / 2
        assertTrue(kotlin.math.abs(middle - bandMiddle) < 16f, "centred in the band left of the action area: $middle vs $bandMiddle")
        assertTrue(hand.all { it.bounds.y >= height - band }, "in the bottom band")
        val bobsHand = scene.items.filter { it.kind == TableItemKind.CARD && it.zone == PlayZone.HAND && it.owner == kit.f.b }
        assertTrue(bobsHand.isNotEmpty() && bobsHand.all { it.bounds.bottom <= DesktopTable.SEAT }, "Bob's hand in his seat header")
        val stack = scene.items.single { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }.bounds
        assertTrue(stack.x >= 1440f - DesktopTable.DOCK, "the Stack under the dock, not on the battlefields: $stack")
    }

    // WEB-015, WEB-016, WEB-017, WEB-018, WEB-020: the table at Alice's first main phase: the top row with the turn line, move
    // pill and phase track (no step button), the seat headers with avatars and names, the dock with an empty Stack and the
    // chat, the action area with Undo and the main button
    @Test fun theDesktopTableHasTopRowSeatHeadersDockAndActionArea() {
        val kit = EngineTableKit()
        kit.opening()
        render(kit)
        compose.onNodeWithTag("phase-track").assertExists()
        compose.onNodeWithContentDescription("Choose phase/step", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("desktop-dock").assertExists()
        compose.onNodeWithText("STACK · 0").assertExists()
        compose.onNodeWithText("Empty").assertExists()
        compose.onNodeWithText("rematch after this one?").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close chat").assertDoesNotExist()
        compose.onNodeWithTag("action-area").assertExists()
        compose.onNodeWithContentDescription("Undo").assertExists()
        compose.onAllNodesWithText("Bob").onFirst().assertExists()
        compose.onNodeWithText("You").assertExists()
        captureShot("$SCREENSHOTS/desktop/table.png",
            "Alice's desktop table at her first main phase: the top row with her turn, the move pill and the phase track, Bob's seat header at the top and hers above her larger hand, the Stack (empty) and the game chat docked at the right, and the quick actions over Undo and the main button at the bottom right.")
    }

    // WEB-017, WEB-016, STK-057, STK-058 (D1): Bob casts Storm Spark at Alice's Ember Cub and she holds priority: the dock's
    // Stack list shows the Spark with its target named, the band under Bob's seat header says what it does, the main button
    // reads Resolve
    @Test fun respondingShowsTheStackInTheDock() {
        val kit = EngineTableKit()
        val cub = kit.toReadyCub()
        kit.bobCastsSpark { s -> TargetRef.Object(s.battlefield.first { (it as arcanum.engine.state.ObjectId.Card).card.value == cub }) }
        kit.scenario.confirmStops[kit.f.a] = false
        assertTrue(kit.toolkit.automatumStep(TableActionIntent.End(kit.scope)), "End turn")
        kit.playOn("Alice holding priority over Bob's Spark", rounds = 40) {
            val s = kit.state()
            s.stack.isNotEmpty() && s.priority.holder == kit.me && kit.routes.arrowShown() && s.stackItems[s.stack.last()]?.kind == StackItemKind.Spell
        }
        render(kit)
        compose.onNodeWithText("STACK · 1").assertExists()
        compose.onNodeWithText("Storm Spark deals 3 damage to Ember Cub.").assertExists()
        compose.onNodeWithTag("stack-line").assert(hasContentDescription("Bob cast Storm Spark · deals 3 damage to Ember Cub"))
        captureShot("$SCREENSHOTS/desktop/table-responding.png",
            "Bob cast Storm Spark at Alice's Ember Cub in his turn and she holds priority: the dock's Stack list shows the Spark with its target named in its text, the band under Bob's seat header says what it does, and the main button at the bottom right reads Resolve.")
    }

    // WEB-015, CMB-001, CMB-028 (D2): Alice declares attackers: the combat band opens between the battlefields with her staged
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
        val seatTop = 900f - scene(kit).let { DesktopTable.band(DesktopCardAppearance, false) } - DesktopTable.SEAT
        assertTrue(kotlin.math.abs(bar.bottom - seatTop) < 2f, "on Alice's seat header: $bar vs $seatTop")
        compose.onAllNodesWithTag("prompt-dim").assertCountEquals(0)
        captureShot("$SCREENSHOTS/desktop/prompt-strip.png",
            "Alice chose Cinder Charm's damage mode on the desktop table: \"Choose any target\" is a strip at the bottom centre of the battlefields, right on her seat header, while the legal targets glow on the undimmed table.")
    }

    // WEB-019, STK-023, STK-052 (D3b): a choice that doesn't need the board is a centred overlay over the battlefields with the
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
        compose.onAllNodesWithTag("prompt-dim").assertCountEquals(2)
        captureShot("$SCREENSHOTS/desktop/prompt-overlay.png",
            "Alice began casting Cinder Charm on the desktop table: its modes are a centred overlay over the battlefields with the table and the dock dimmed behind it, while the top row and the action area with the main button stay as they are.")
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
