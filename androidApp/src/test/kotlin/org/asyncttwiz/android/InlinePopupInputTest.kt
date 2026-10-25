package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class InlinePopupInputTest {
    @get:Rule val compose = createComposeRule()

    /** Include the production host relay: standalone popup tests cannot catch missing targets. */
    private fun renderPopup(content: @Composable (() -> Unit) -> Unit): List<Offset> {
        val open = mutableStateOf(true)
        val downs = mutableListOf<Offset>()
        val relay = TableTapRelay({ _, _ -> false }, { p, _ -> downs += p }, { _, _ -> }, {})
        compose.setContent { MaterialTheme {
            CompositionLocalProvider(LocalTableTapRelay provides relay) {
                Box(Modifier.fillMaxSize().tableRingRelay(relay)) {
                    if (open.value) content { open.value = false }
                }
            }
        } }
        compose.mainClock.advanceTimeBy(600)
        return downs
    }

    @Test fun manageStackCopySubmitsAndKeepsPopupOpen() {
        val state = TableStatesFixture.combat()
        val id = CombatStackRules.stack(state.board).cards.first()
        val intents = mutableListOf<TableIntent>()
        val downs = renderPopup { dismiss ->
            ManageStackDialog(state, TableStatesFixture.a, true, dismiss, intents::add, {})
        }
        compose.onAllNodesWithContentDescription("Copy")[0].performTouchInput { click() }
        compose.onNodeWithContentDescription("Context action grid").assertExists()
        compose.runOnIdle {
            assertEquals(listOf<TableIntent>(TableIntent.Submit(StackMenuActions.copy(state, id), null)), intents)
            assertTrue(downs.isEmpty())
        }
    }

    @Test fun contentsRowOpensActionsAndSearchTakesFocusWithoutRelaying() {
        val state = TableStatesFixture.initial
        val viewer = TableStatesFixture.a
        val container = state.board.containers.single { it.accountId == viewer && it.zone == PlayZone.GRAVEYARD }
        val pile = PileContents(container.id, "Graveyard", container.cards)
        val entry = contentsEntries(state, pile, viewer).first()
        val actions = mutableListOf<String>()
        val downs = renderPopup { dismiss ->
            InlinePopupPanel(dismiss, "Contents", height = 560.dp, sheet = true) {
                ContentsScreen(state, pile, viewer, actions::add, {}, Modifier.height(480.dp))
            }
        }
        compose.onNode(hasText(entry.location) and hasText(entry.name)).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { assertEquals(listOf(entry.cardId), actions) }
        compose.onNode(hasSetTextAction()).performTouchInput { click() }
        compose.onNode(hasSetTextAction()).assertIsFocused().performTextInput("not a card name")
        compose.onNodeWithText("No cards").assertExists()
        compose.onNodeWithContentDescription("Context action grid").assertExists()
        compose.runOnIdle { assertTrue(downs.isEmpty()) }
    }

    @Test fun contentsScrollCanStartBetweenRowsWithoutDismissing() {
        val state = TableStatesFixture.initial
        val viewer = TableStatesFixture.a
        val container = state.board.containers.single { it.accountId == viewer && it.zone == PlayZone.BATTLEFIELD }
        val pile = PileContents(container.id, "Battlefield", container.cards, ordered = true)
        val downs = renderPopup { dismiss ->
            InlinePopupPanel(dismiss, "Contents", height = 260.dp, sheet = true) {
                ContentsScreen(state, pile, viewer, {}, {}, Modifier.height(180.dp))
            }
        }
        // Start in the list's padding, not on a card row. The whole scroll viewport is inside.
        val scrolls = compose.onAllNodes(hasScrollAction(), useUnmergedTree = true).fetchSemanticsNodes()
        val vertical = scrolls.last { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) &&
            it.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f }
        val bounds = vertical.boundsInRoot
        compose.onRoot().performTouchInput {
            swipe(Offset(bounds.left + 2f, bounds.bottom - 8f), Offset(bounds.left + 2f, bounds.top + 8f), 300)
        }
        compose.onNodeWithContentDescription("Context action grid").assertExists()
        compose.runOnIdle {
            assertTrue(vertical.config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
            assertTrue(downs.isEmpty())
        }
    }

    @Test fun emptyCellAndGapDismissAndRelayBothPressAndReleaseButControlSwipeChangesPage() {
        val open = mutableStateOf(true)
        val downs = mutableListOf<Offset>()
        val ups = mutableListOf<Offset>()
        var clicks = 0
        var pages = 0
        val relay = TableTapRelay({ _, _ -> false }, { p, _ -> downs += p }, { p, _ -> ups += p }, {})
        compose.setContent { MaterialTheme {
            CompositionLocalProvider(LocalTableTapRelay provides relay) {
                Box(Modifier.fillMaxSize().tableRingRelay(relay).semantics { contentDescription = "Host" }) {
                    if (open.value) TableInlinePopup("test", "card", null, true, OwnTableColor, { open.value = false },
                        width = 160.dp, placement = TableBounds(100f, 100f, 160f, 48f), switchPage = { pages += it }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            InlinePopupCell(MenuControl("First") { clicks++ })
                            Spacer(Modifier.size(48.dp))
                            InlinePopupCell(MenuControl("Last") { clicks++ })
                        }
                    }
                }
            }
        } }
        // Both coordinates are within the popup's rectangle, but neither belongs to a control.
        for (point in listOf(Offset(174f, 124f), Offset(149f, 124f))) {
            compose.runOnIdle { open.value = true }
            compose.mainClock.advanceTimeBy(600)
            compose.onNodeWithContentDescription("Host").performTouchInput { click(point) }
            compose.onNodeWithContentDescription("Context action grid").assertDoesNotExist()
            compose.runOnIdle { assertEquals(point, downs.last()); assertEquals(point, ups.last()); assertEquals(0, clicks) }
        }
        compose.runOnIdle { open.value = true }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("Last").performTouchInput {
            swipe(center, center - Offset(80f, 0f), 160)
        }
        compose.runOnIdle { assertEquals(1, pages); assertEquals(2, downs.size); assertEquals(0, clicks) }
        compose.onNodeWithContentDescription("Context action grid").assertExists()
    }
}
