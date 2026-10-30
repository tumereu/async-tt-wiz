package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.asyncttwiz.protocol.HistoryIcon
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertTrue

/** A sheet of every card-move history icon (rules v32: by target) with a sample text, for review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class MoveIconsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val rows = listOf(
        HistoryIcon.DOWNLOAD to "To hand · Drew 2 cards / Returned Opt to hand",
        HistoryIcon.SPACE_DASHBOARD to "To battlefield · Put Opt onto the battlefield",
        HistoryIcon.LAYERS to "To stack · Cast Opt",
        HistoryIcon.DELETE_SWEEP to "To graveyard · Discarded Opt",
        HistoryIcon.BLOCK to "To exile · Exiled Grizzly Bears",
        HistoryIcon.LIBRARY_BOOKS to "To library · Put a card on top of library",
        HistoryIcon.INBOX to "To Temp pile · Put a card into Temp 2",
        HistoryIcon.OPEN_WITH to "Same place · Grizzly Bears moved on the battlefield",
        HistoryIcon.LAYERS_CLEAR to "Pulled from the Stack (kept)",
        HistoryIcon.DELETE to "Token removed (kept)")

    @Test fun moveIconSheet() {
        compose.setContent {
            ScreenTheme {
                Column(Modifier.testTag("capture").background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((icon, label) in rows) Row(verticalAlignment = Alignment.CenterVertically) {
                        HistoryEventIcon(icon)
                        Text(icon.wire, Modifier.padding(start = 12.dp).width(150.dp))
                        Text(label)
                    }
                }
            }
        }
        val output = File("build/outputs/roborazzi/history/move-icons.png")
        output.parentFile!!.mkdirs()
        compose.onNodeWithTag("capture").captureRoboImage(output.path)
        val image = ImageIO.read(output)
        assertTrue(image.width > 0 && image.height > 0)
    }
}
