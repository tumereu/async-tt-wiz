package org.asyncttwiz.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.Executor
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EventTextUiTest {
    @get:Rule val compose = createComposeRule()
    private val f = DownloadedTreeFixture

    @Test fun textOverlaysTheTopRowWithoutTakingTapsAndFadesOnItsOwn() {
        val tree = f.tap(f.root())
        val feed = EventTextFeed(Executor { it.run() }, { it() })
        var taps = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(48.dp).testTag("row").clickable { taps++ })
                    EventTextOverlay(feed, pauseMillis = 500)
                }
            }
        }
        compose.runOnIdle {
            feed.displayed(tree.currentNodeId, tree.stateAt(tree.currentNodeId), HistoryMode.PLAYBACK, tree.nodes, f.a, emptyMap()) { _, _ -> true }
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("event-text").assertIsDisplayed().assertContentDescriptionEquals("A card tapped")
        compose.onNodeWithTag("event-text").performClick()
        compose.mainClock.advanceTimeBy(300)
        assertEquals(1, taps, "the row beneath still takes the tap")
        compose.mainClock.advanceTimeBy(EVENT_TEXT_MIN_MILLIS)
        assertNull(feed.chip)
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("event-text").assertDoesNotExist()
    }

    @Test fun textAppearsAndIsReplacedAtOnceAndOnlyItsDisappearanceFades() {
        var value by androidx.compose.runtime.mutableStateOf<String?>(null)
        compose.mainClock.autoAdvance = false
        compose.setContent { InstantShowFadeOut(value) { androidx.compose.material3.Text(it, Modifier.testTag("shown")) } }
        compose.runOnIdle { value = "one"; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("shown").assertTextEquals("one")
        compose.runOnIdle { value = "two"; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithTag("shown").assertCountEquals(1) // No cross-fade of old and new.
        compose.onNodeWithTag("shown").assertTextEquals("two")
        compose.runOnIdle { value = null; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(EVENT_TEXT_FADE_OUT_MILLIS / 2L)
        compose.onNodeWithTag("shown").assertTextEquals("two") // Still fading out.
        compose.mainClock.advanceTimeBy(EVENT_TEXT_FADE_OUT_MILLIS.toLong())
        compose.onNodeWithTag("shown").assertDoesNotExist()
    }

    @Test fun settingsOffersEventTextOnByDefault() {
        compose.setContent { MaterialTheme { SettingsContent(null, accountId = "event-text-ui", logout = {}) } }
        compose.onNodeWithTag("event-text-setting").performScrollTo().assertIsOn().performClick().assertIsOff()
    }
}
