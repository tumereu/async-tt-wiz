package org.asyncttwiz.android

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Render production vectors with Android's native renderer, including launcher layer overscan. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class LogoScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun wordmark() {
        compose.setContent {
            ScreenTheme {
                Box(Modifier.testTag("capture").background(Color.White).padding(16.dp)) { WizardLogo() }
            }
        }
        compose.onNodeWithContentDescription("Wizard hat").assertIsDisplayed()
        compose.onNodeWithText("ATW").assertIsDisplayed()
        capture("logo-wordmark")
    }

    @Test fun markAt48dp() {
        compose.setContent {
            Image(painterResource(R.drawable.atw_logo), "Wizard hat", Modifier.size(48.dp).testTag("capture"))
        }
        capture("logo-48dp", 48)
    }

    @Test fun launcherSquare() = launcher("square")
    @Test fun launcherCircle() = launcher("circle")
    @Test fun launcherSquircle() = launcher("squircle")
    @Test fun launcherThemed() = launcher("themed")

    @Test @Config(sdk = [29]) fun manifestIconsOnMinimumSdk() {
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals(R.mipmap.ic_launcher, info.icon)
        assertTrue(context.getDrawable(info.icon) is AdaptiveIconDrawable)
        assertTrue(context.getDrawable(R.mipmap.ic_launcher_round) is AdaptiveIconDrawable)
    }

    @Test fun foregroundFitsCircularSafeZone() {
        val bitmap = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
        val foreground = requireNotNull(context.getDrawable(R.drawable.ic_launcher_foreground))
        foreground.setBounds(0, 0, 108, 108)
        foreground.draw(AndroidCanvas(bitmap))
        var painted = 0
        for (y in 0 until 108) for (x in 0 until 108) {
            if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 0) {
                painted++
                val dx = x + .5f - 54
                val dy = y + .5f - 54
                assertTrue(dx * dx + dy * dy <= 33 * 33, "Artwork outside 66dp safe circle at $x,$y")
            }
        }
        assertTrue(painted > 1000, "Foreground must not be empty")
    }

    private fun launcher(mask: String) {
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val themed = mask == "themed"
        val foreground = if (themed) assertNotNull(icon.monochrome).mutate().apply {
            setTint(android.graphics.Color.rgb(36, 76, 70))
        } else icon.foreground
        val shape = when (mask) {
            "circle", "themed" -> CircleShape
            "squircle" -> GenericShape { size, _ ->
                val w = size.width
                val h = size.height
                moveTo(w / 2, 0f)
                cubicTo(w * .94f, 0f, w, h * .06f, w, h / 2)
                cubicTo(w, h * .94f, w * .94f, h, w / 2, h)
                cubicTo(w * .06f, h, 0f, h * .94f, 0f, h / 2)
                cubicTo(0f, h * .06f, w * .06f, 0f, w / 2, 0f)
                close()
            }
            else -> RectangleShape
        }
        compose.setContent {
            Canvas(Modifier.size(192.dp).testTag("capture").clip(shape)) {
                drawIntoCanvas { canvas ->
                    val side = size.width.toInt()
                    icon.background.setBounds(0, 0, side, side)
                    icon.background.draw(canvas.nativeCanvas)
                    // AdaptiveIconDrawable expands its 108dp layers around the visible 72dp viewport.
                    foreground.setBounds(-side / 4, -side / 4, side * 5 / 4, side * 5 / 4)
                    foreground.draw(canvas.nativeCanvas)
                }
            }
        }
        capture("launcher-$mask", 192)
    }

    private fun capture(name: String, side: Int? = null) {
        val output = File("$SCREENSHOTS/logo/$name.png")
        output.parentFile!!.mkdirs()
        compose.onNodeWithTag("capture").captureRoboImage(output.path)
        val image = ImageIO.read(output)
        assertTrue(image.width > 0 && image.height > 0)
        if (side != null) {
            assertEquals(side, image.width)
            assertEquals(side, image.height)
        }
    }
}
