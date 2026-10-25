package org.asyncttwiz.android

import android.app.Application
import android.os.Looper
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.storage.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CatalogControlsTest {
    @get:Rule val compose = createComposeRule()
    private val viewModels = ViewModelStore()
    @After fun clear() { viewModels.clear() }

    @Test fun finishingShowsIndeterminateProgressEvenIfPreviousTotalIsPresent() {
        val progress = CatalogProgress(CatalogPhase.FINISHING, 450, 450)
        compose.setContent { ScreenTheme { CatalogProgressBar(progress) } }
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(1)
        assertEquals("Finishing up — verifying catalog…", progress.label())
    }

    @Test fun manualFullRefreshShowsSizeAndRequiresConfirmationBeforeBulkDownload() {
        val controller = CatalogViewModel(ApplicationProvider.getApplicationContext<Application>())
        viewModels.put("catalog", controller)
        val downloads = AtomicInteger()
        val source = object : CatalogSource {
            override fun metadata(): CatalogMetadata {
                assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
                return CatalogMetadata("2026-10-25", "5.2.2")
            }
            override fun size() = 12_000_000L
            override fun checksum() = "0".repeat(64)
            override fun download(output: OutputStream, progress: (Long) -> Unit) {
                assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
                downloads.incrementAndGet()
                throw IOException("Synthetic transfer failure; never use the network")
            }
        }
        CatalogViewModel::class.java.getDeclaredField("source").apply { isAccessible = true }.set(controller, source)
        val worker = CatalogViewModel::class.java.getDeclaredField("worker").apply { isAccessible = true }
            .get(controller) as ExecutorService
        fun waitForWorker() {
            worker.submit {}.get(10, TimeUnit.SECONDS)
            shadowOf(Looper.getMainLooper()).idle()
        }
        controller.requestFullRefresh()
        waitForWorker()
        assertTrue(controller.state.fullRefresh!!.full)
        assertEquals(0, downloads.get())
        compose.setContent { ScreenTheme { CatalogControls(controller) } }
        compose.onNodeWithText("Refresh full catalog?").assertIsDisplayed()
        compose.onNodeWithText("Download size: 12 MB.").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertNull(controller.state.fullRefresh)
        assertEquals(0, downloads.get())
        compose.runOnIdle { controller.requestFullRefresh() }
        waitForWorker()
        compose.onNodeWithText("Refresh full catalog").performClick()
        waitForWorker()
        assertEquals(1, downloads.get())
        assertNull(controller.state.fullRefresh)
        assertTrue(controller.state.error)
        assertFalse(controller.state.busy)
    }
}
