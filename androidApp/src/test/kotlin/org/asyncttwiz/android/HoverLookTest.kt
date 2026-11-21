package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The hover look of controls and cards ([hoverLook]): shown only while a mouse is over them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class HoverLookTest {
    @get:Rule val compose = createComposeRule()

    private val white = Color.White.toArgb()

    private fun pixel(tag: String, x: Int? = null, y: Int? = null): Int {
        val image = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        return image.getPixel(x ?: (image.width / 2), y ?: (image.height / 2))
    }

    private fun mouseTo(tag: String) {
        compose.onNodeWithTag(tag).performMouseInput { moveTo(center) }
        compose.waitForIdle()
    }

    private fun mouseAway() {
        compose.onAllNodes(isRoot()).onFirst().performMouseInput { moveTo(Offset(400f, 800f)) }
        compose.waitForIdle()
    }

    // WEB-139: a control the mouse is over shows a faint layer of its ink (8 %), gone once the mouse leaves; a greyed one shows none
    @Test fun aControlTheMouseIsOverShowsAFaintLayerOfItsInk() {
        var enabled by mutableStateOf(true)
        compose.setContent {
            Column(Modifier.background(Color.White).padding(top = 8.dp)) {
                Box(Modifier.size(48.dp).testTag("control").background(Color.White).hoverLook(HoverLook.Tint(Color.Black), enabled))
            }
        }
        assertEquals(white, pixel("control"), "no layer before the mouse comes")
        mouseTo("control")
        val hovered = Color(pixel("control"))
        assertEquals(1 - HOVER_TINT_ALPHA, hovered.red, .02f, "8 % black over the white: $hovered")
        mouseAway()
        assertEquals(white, pixel("control"), "gone once the mouse left")
        enabled = false
        mouseTo("control")
        assertEquals(white, pixel("control"), "none on a greyed control")
    }

    // WEB-139: a finger on a control never shows the hover look, so the phone looks as before
    @Test fun aTouchNeverShowsIt() {
        compose.setContent {
            Box(Modifier.size(48.dp).testTag("control").background(Color.White).hoverLook(HoverLook.Tint(Color.Black)))
        }
        compose.onNodeWithTag("control").performTouchInput { down(center) }
        compose.waitForIdle()
        assertEquals(white, pixel("control"), "no layer under a finger")
        compose.onNodeWithTag("control").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(white, pixel("control"), "nor after it")
    }

    // WEB-139: the app's own buttons (here Undo's square, as the desktop action area draws it) show it in their ink
    @Test fun anActionSquareShowsItInItsInk() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.background(Color.White).padding(8.dp)) {
                    ActionSquare("Undo", "↶", true, Modifier.testTag("undo"), fill = Color.White, ink = MenuInk,
                        icon = { TableChromeIcon("undo") }) {}
                }
            }
        }
        // A corner, clear of the icon
        val before = pixel("undo", 2, 2)
        mouseTo("undo")
        val after = pixel("undo", 2, 2)
        assertNotEquals(before, after, "the square tinted under the mouse")
        assertTrue(Color(after).red < Color(before).red, "darker, in its dark ink: ${Color(before)} -> ${Color(after)}")
    }

    // WEB-140: a card the mouse is over shows a thin dark outline just inside its edge, its middle untouched
    @Test fun aCardTheMouseIsOverShowsAThinDarkOutline() {
        compose.setContent {
            Box(Modifier.background(Color.White).padding(8.dp)) {
                Box(Modifier.size(70.dp, 98.dp).testTag("card").background(Color.White).hoverLook(HoverLook.Outline()))
            }
        }
        val image = { compose.onNodeWithTag("card").captureToImage().asAndroidBitmap() }
        assertEquals(white, image().getPixel(1, 49), "no outline before the mouse comes")
        mouseTo("card")
        val hovered = image()
        assertTrue(Color(hovered.getPixel(1, 49)).red < .6f, "the outline at the left edge: ${Color(hovered.getPixel(1, 49))}")
        assertEquals(white, hovered.getPixel(35, 49), "the middle untouched")
        mouseAway()
        assertEquals(white, image().getPixel(1, 49), "gone once the mouse left")
    }
}
