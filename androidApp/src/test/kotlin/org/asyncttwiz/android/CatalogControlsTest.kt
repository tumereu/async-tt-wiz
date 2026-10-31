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
import java.util.concurrent.CountDownLatch
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

    // LOB-091
    @Test fun firstLaunchWaitsForDownloadConsentThenLeavesLoginAndRegistrationUsable() {
        val downloads = AtomicInteger()
        val controller = initialCatalog(downloads)
        controller.foreground()
        waitForWorker(controller)
        assertTrue(controller.needsInitialDownloadConsent)
        assertEquals(0, downloads.get())
        val fixtures = ScreenFixtures()
        fixtures.auth.seed(AuthState(restoring = false))
        compose.setContent { AccountEntry(fixtures.auth, fixtures.reset, controller) }
        compose.onNodeWithText("Download size: 12 MB.").assertIsDisplayed()
        compose.onAllNodes(hasClickAction() and hasAnyAncestor(isDialog())).assertCountEquals(2)
        compose.onNodeWithText("Not now").assertDoesNotExist()
        compose.onNodeWithText("Download").performClick()
        waitForWorker(controller)
        assertEquals(1, downloads.get())
        assertFalse(controller.needsInitialDownloadConsent)
        assertTrue(controller.state.error)
        compose.onNodeWithText("Retry download").assertIsDisplayed()
        compose.onNodeWithText("Exit").assertDoesNotExist()
        compose.onNodeWithTag("email").performTextInput("player@example.test")
        compose.onNodeWithText("Create account").performScrollTo().performClick()
        compose.onNodeWithTag("username").performTextInput("Wizard")
        assertEquals("Wizard", fixtures.auth.state.username)
    }

    @Test fun firstLaunchFailedMetadataCheckShowsConsentWithoutPrematureErrorOrRetry() {
        val downloads = AtomicInteger()
        val controller = initialCatalog(downloads) { throw IOException("Metadata unavailable") }
        controller.foreground()
        waitForWorker(controller)
        assertTrue(controller.needsInitialDownloadConsent)
        assertFalse(controller.state.error)
        assertNull(controller.state.status)
        assertEquals(0, downloads.get())
        val fixtures = ScreenFixtures()
        fixtures.auth.seed(AuthState(restoring = false))
        compose.setContent { AccountEntry(fixtures.auth, fixtures.reset, controller) }
        compose.onNodeWithText("Download").assertIsDisplayed()
        compose.onNodeWithText("Exit").assertIsDisplayed()
        compose.onNodeWithText("Retry download", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Card records could not be updated", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Download").performClick()
        waitForWorker(controller)
        assertTrue(controller.state.error)
        compose.onNodeWithText("Retry download").assertIsDisplayed()
    }

    // LOB-091
    @Test fun exitFinishesActivityAndMissingCatalogPromptsAgainOnNextLaunch() {
        val downloads = AtomicInteger()
        val first = initialCatalog(downloads)
        first.foreground()
        waitForWorker(first)
        val activity = org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup()
        try {
            val fixtures = ScreenFixtures()
            fixtures.auth.seed(AuthState(restoring = false))
            compose.setContent { AccountEntry(fixtures.auth, fixtures.reset, first, exit = activity.get()::finish) }
            compose.onNodeWithText("Exit").performClick()
            assertTrue(activity.get().isFinishing)
            val next = initialCatalog(downloads)
            next.foreground()
            waitForWorker(next)
            assertTrue(next.needsInitialDownloadConsent)
            assertEquals(0, downloads.get())
            next.seed(next.state.copy(installed = CatalogMetadata("2026-10-25", "5.2.2")))
            assertFalse(next.needsInitialDownloadConsent)
        } finally { activity.pause().stop().destroy() }
    }

    @Test fun consentDuringMetadataCheckIsNotLostAndDoesNotWaitToShowPrompt() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val downloads = AtomicInteger()
        val controller = initialCatalog(downloads) {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
        }
        try {
            controller.foreground()
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(controller.state.busy)
            assertTrue(controller.needsInitialDownloadConsent)
            assertEquals(0, downloads.get())
            controller.consent()
            assertFalse(controller.needsInitialDownloadConsent)
        } finally { release.countDown() }
        waitForWorker(controller)
        assertEquals(1, downloads.get())
    }

    private fun initialCatalog(downloads: AtomicInteger, beforeSetList: () -> Unit = {}): CatalogViewModel {
        val controller = CatalogViewModel(ApplicationProvider.getApplicationContext<Application>())
        viewModels.put("catalog-${System.identityHashCode(controller)}", controller)
        val source = object : CatalogSource {
            override fun metadata() = CatalogMetadata("2026-10-25", "5.2.2")
            override fun setList(): CatalogSetList {
                beforeSetList()
                return CatalogSetList(metadata(), emptyList())
            }
            override fun size() = 12_000_000L
            override fun checksum() = "0".repeat(64)
            override fun download(output: OutputStream, progress: (Long) -> Unit) {
                downloads.incrementAndGet()
                throw IOException("Synthetic transfer failure; never use the network")
            }
        }
        CatalogViewModel::class.java.getDeclaredField("source").apply { isAccessible = true }.set(controller, source)
        return controller
    }

    private fun waitForWorker(controller: CatalogViewModel) {
        val worker = CatalogViewModel::class.java.getDeclaredField("worker").apply { isAccessible = true }
            .get(controller) as ExecutorService
        worker.submit {}.get(10, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
    }

    // LOB-093
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
