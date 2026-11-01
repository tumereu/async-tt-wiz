package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PowerToughnessStepIconTest {
    @get:Rule val compose = createComposeRule()
    private val steps = listOf("+1/+0", "−1/−0", "+0/+1", "+0/−1")

    private fun paths(step: PowerToughnessStep) = (0 until powerToughnessStepIcon(step).root.size)
        .map { powerToughnessStepIcon(step).root[it] as VectorPath }

    // TBL-083
    @Test fun untilEndOfTurnIconIsThePlainIconPlusAnHourglassInItsTopRightCorner() {
        for (power in -1..1) for (toughness in -1..1) {
            val plain = paths(PowerToughnessStep(power, toughness, false))
            val eot = paths(PowerToughnessStep(power, toughness, true))
            // The same symbols and slash at the same height...
            assertEquals(plain, eot.dropLast(1))
            // ...and one small hourglass clear of them: right of the slash's top (x ≤ 15) and above a "+" arm (y ≥ 7.5).
            val hourglass = eot.last()
            val half = hourglass.strokeLineWidth / 2
            val points = hourglass.pathData.flatMap { node -> when (node) {
                is PathNode.MoveTo -> listOf(node.x to node.y)
                is PathNode.LineTo -> listOf(node.x to node.y)
                else -> fail("Unexpected $node")
            } }
            assertTrue(points.all { (x, y) -> x - half > 16f && x + half <= 24f && y - half >= 0f && y + half < 6.5f }, "$points")
        }
    }

    // TBL-083
    @Test fun stepButtonSheet() {
        compose.setContent {
            // Three times the test screen's density, so the small hourglass can be checked by eye.
            CompositionLocalProvider(LocalDensity provides Density(3f, 1f)) {
                ScreenTheme {
                    Column(Modifier.testTag("capture").background(Color(0xffe4ece9)).padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (step in steps) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            InlinePopupCell(MenuControl("P/T $step") {}, label = step, owner = OwnTableColor)
                            InlinePopupCell(MenuControl("EOT P/T $step") {}, label = "EOT $step", owner = OwnTableColor)
                        }
                    }
                }
            }
        }
        val output = File("$SCREENSHOTS/pt-step-buttons.png")
        output.parentFile!!.mkdirs()
        compose.onNodeWithTag("capture").captureShot(output.path, "The More page's P/T step buttons drawn at three times " +
            "normal size: each single step on the left and its until-end-of-turn twin on the right, the same icon with a " +
            "small hourglass in its top-right corner.")
    }
}
