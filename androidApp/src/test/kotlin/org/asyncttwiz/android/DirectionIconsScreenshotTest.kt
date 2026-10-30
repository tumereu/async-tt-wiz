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

/** A sheet of every rules v34 direction / step / closing history icon with a sample text, for review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class DirectionIconsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val rows = listOf(
        HistoryIcon.FAVORITE to "Life gained · Life 20 → 23",
        HistoryIcon.HEART_BROKEN to "Life lost · Life 20 → 17",
        HistoryIcon.EXPOSURE_PLUS_1 to "Card counter added",
        HistoryIcon.EXPOSURE_NEG_1 to "Card counter removed",
        HistoryIcon.EXPOSURE_ZERO to "Card counters cleared",
        HistoryIcon.ARROW_CIRCLE_UP to "Player counter up · Poison 1 → 2",
        HistoryIcon.ARROW_CIRCLE_DOWN to "Player counter down · Poison 2 → 1",
        HistoryIcon.TRENDING_UP to "P/T up · 2/2 → 3/3 (also until end of turn)",
        HistoryIcon.TRENDING_DOWN to "P/T down · 2/2 → 1/1",
        HistoryIcon.LOCAL_FIRE_DEPARTMENT to "Damage dealt · 0 → 3",
        HistoryIcon.HEALING to "Damage removed · 3 → 0",
        HistoryIcon.ARROW_FORWARD to "Next step · Upkeep → Draw",
        HistoryIcon.SKIP_NEXT to "Next turn · End turn",
        HistoryIcon.OUTLINED_FLAG to "Pass the move (kept)",
        HistoryIcon.VISIBILITY_OFF to "Close a view",
        HistoryIcon.CLOSE to "Decline / withdraw result (kept)",
        HistoryIcon.CHECK to "Close combat",
        HistoryIcon.LAYERS_CLEAR to "Remove from the Stack (kept)",
        HistoryIcon.GPS_FIXED to "Choose attack target")

    @Test fun directionIconSheet() {
        compose.setContent {
            ScreenTheme {
                Column(Modifier.testTag("capture").background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((icon, label) in rows) Row(verticalAlignment = Alignment.CenterVertically) {
                        HistoryEventIcon(icon)
                        Text(icon.wire, Modifier.padding(start = 12.dp).width(190.dp))
                        Text(label)
                    }
                }
            }
        }
        val output = File("$SCREENSHOTS/history/direction-icons.png")
        output.parentFile!!.mkdirs()
        compose.onNodeWithTag("capture").captureRoboImage(output.path)
        val image = ImageIO.read(output)
        assertTrue(image.width > 0 && image.height > 0)
    }
}
