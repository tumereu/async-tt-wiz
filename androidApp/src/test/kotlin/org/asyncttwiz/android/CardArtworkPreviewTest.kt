package org.asyncttwiz.android

import android.graphics.Bitmap
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import coil3.asImage
import coil3.memory.MemoryCache
import org.asyncttwiz.storage.ArtworkReference
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CardArtworkPreviewTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()

    @Test fun cachedNormalThumbnailIsVisibleOnFirstFrameWhileLargeArtIsUnavailable() {
        val context = RuntimeEnvironment.getApplication()
        CardArtworkService(context, temporary.newFolder()).use { service ->
            val normal = ArtworkReference(null, 0, "v1")
            val thumbnail = Bitmap.createBitmap(40, 56, Bitmap.Config.ARGB_8888).asImage()
            service.loader.memoryCache!![MemoryCache.Key(CardArtworkRequest(normal, 40, 56).key)] = MemoryCache.Value(thumbnail)
            compose.setContent {
                CardArtwork(normal.copy(size = "large"), "Artwork", 300, 420, service = service,
                    placeholder = { Text("Missing artwork") }, previewReference = normal)
            }
            compose.onNodeWithContentDescription("Artwork").assertIsDisplayed()
            compose.onNodeWithText("Missing artwork").assertDoesNotExist()
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithContentDescription("Artwork").assertIsDisplayed()
            compose.onNodeWithText("Missing artwork").assertDoesNotExist()
        }
    }
}
