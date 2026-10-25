package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
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
