package org.asyncttwiz.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import coil3.asImage
import coil3.memory.MemoryCache
import com.github.takahirom.roborazzi.captureRoboImage
import org.asyncttwiz.protocol.*
import org.asyncttwiz.protocol.PodGridView as GridViewData
import org.asyncttwiz.storage.ArtworkReference
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Grid cards with (fake, card-proportioned) artwork: selection tint/dim, picker borders and discards. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h1400dp-port-xhdpi")
class PodGridScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()
    private val alice = AccountIdentity("alice", "Alice")
    private val bob = AccountIdentity("bob", "Bob")
    private val cards = List(9) { DraftCopy("copy-$it", "definition-$it") }
    private val definitions = cards.associate {
        it.definition to CardDefinition("Card ${it.id.last()}", listOf(DefinitionFace("face", "Card ${it.id.last()}",
            scryfallId = "print-${it.id.last()}")))
    }
    private val palette = listOf(0xff8a3b2e, 0xff2e6b8a, 0xff3f7d3a, 0xff7a5a2a, 0xff5b3f8a, 0xff2a7a72, 0xff8a2e5e, 0xff6b6b2e, 0xff3b4f8a)
        .map { it.toInt() }
    private val service by lazy { CardArtworkService(RuntimeEnvironment.getApplication(), temporary.newFolder()) }

    @After fun uninstall() { installArtwork(null); service.close() }

    private fun installArtwork(value: CardArtworkService?) {
        val provider = PublicArtwork::class.java.getDeclaredField("provider").apply { isAccessible = true }.get(PublicArtwork)
        provider.javaClass.getDeclaredField("service").apply { isAccessible = true }.set(provider, value)
    }

    /** Bob took row 1 of the live grid; the finished grid below holds picks and discards. */
    private fun view(): GridViewData {
        val live = GridPublicBoard("grid-0", 1, cards.mapIndexed { index, copy -> copy.takeIf { index >= 3 } }, alice.accountId,
            slots = cards.mapIndexed { index, copy -> GridPublicSlot(copy, "bob".takeIf { index < 3 }) })
        val done = GridPublicBoard("done", 2, List(9) { null }, null, finished = true, slots = cards.mapIndexed { index, copy ->
            GridPublicSlot(copy, when (index) { 1, 4, 7 -> "bob"; 6, 8 -> "alice"; else -> null }, refill = index in 6..8)
        })
        return GridViewData(GridPublicView(1, 9, false, listOf("alice", "bob"), listOf(live), emptyList(), finishedGrids = listOf(done)),
            GridPrivateView(alice.accountId, emptyList(), live.id))
    }

    private fun cardBitmap(width: Int, height: Int, color: Int): Bitmap {
        // Decoded card art keeps the printed 488×680 proportions inside the requested box.
        val w = minOf(width, (height * 488f / 680f).toInt()).coerceAtLeast(1)
        val h = minOf(height, (width * 680f / 488f).toInt()).coerceAtLeast(1)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(0xff111111.toInt())
            val paint = Paint().apply { this.color = color }
            val inset = w * .05f
            canvas.drawRect(inset, inset, w - inset, h - inset, paint)
            // Printed layout: name bar with name and mana cost on top, art box below it.
            paint.color = 0xffd8d0b8.toInt()
            canvas.drawRect(inset * 1.4f, h * .045f, w - inset * 1.4f, h * .105f, paint)
            paint.color = 0xff222222.toInt()
            canvas.drawRect(inset * 2, h * .065f, w * .55f, h * .085f, paint)
            repeat(2) { canvas.drawCircle(w - inset * 2.6f - it * w * .07f, h * .075f, w * .028f, paint) }
            paint.color = 0xffe8dcc0.toInt()
            canvas.drawRect(inset * 1.4f, h * .115f, w - inset * 1.4f, h * .55f, paint)
        }
    }

    @Test fun selectedRowAndColumnWithPickedAndDiscardedCards() {
        var generation by mutableIntStateOf(0)
        installArtwork(service)
        compose.setContent { ScreenTheme {
            key(generation) { PodGridView(view(), 18, definitions, listOf(alice, bob), false, { _, _, _ -> }) }
        } }
        // Seed decoded artwork at each slot's exact decode box, then remount so every card loads it at once.
        for (grid in listOf("grid-0", "done")) cards.forEachIndexed { index, copy ->
            val size = compose.onNodeWithTag("pod-grid-$grid-slot-$index").fetchSemanticsNode().size
            val reference = ArtworkReference("print-$index", 0, copy.definition, "normal", null, null)
            service.loader.memoryCache!![MemoryCache.Key(CardArtworkRequest(reference, size.width, size.height).key)] =
                MemoryCache.Value(cardBitmap(size.width, size.height, palette[index]).asImage())
        }
        compose.runOnIdle { generation++ }
        compose.waitUntil(5_000) {
            compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image)).fetchSemanticsNodes().size == 18
        }
        compose.mainClock.advanceTimeBy(500)
        val directory = File("build/outputs/roborazzi").also { it.mkdirs() }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(directory, "pod-grid-none.png").path)
        // The frame hugs the art: right inside the 1dp border at the top and bottom edge is card, not slot background.
        val slotFile = File(directory, "pod-grid-slot.png")
        compose.onNodeWithTag("pod-grid-grid-0-slot-4").captureRoboImage(slotFile.path)
        val image = ImageIO.read(slotFile)
        val border = (1 * RuntimeEnvironment.getApplication().resources.displayMetrics.density).toInt()
        listOf(border + 1, image.height - border - 2).forEach { y ->
            assertEquals(0xff111111.toInt(), image.getRGB(image.width / 2, y), "pixel at y=$y of ${image.height}")
        }
        // The picker badge sits top-left, below the name bar (mana cost and name stay visible).
        val slot = compose.onNodeWithTag("pod-grid-grid-0-slot-1").fetchSemanticsNode().boundsInRoot
        val badge = compose.onNodeWithTag("pod-grid-grid-0-picker-1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(badge.left - slot.left < slot.width / 4 && badge.top - slot.top >= slot.height * .11f &&
            badge.bottom - slot.top < slot.height * .3f, "badge $badge in slot $slot")
        compose.onNodeWithContentDescription("Select row 3").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(directory, "pod-grid-row.png").path)
        compose.onNodeWithContentDescription("Select column 2").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(directory, "pod-grid-column.png").path)
    }
}
