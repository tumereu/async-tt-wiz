package org.asyncttwiz.android

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.protocol.AccountJson
import org.asyncttwiz.protocol.CrashReport
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiagnosticsTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val store get() = ReportChoiceStore(context)

    @Before fun unasked() { store.choice = ReportChoice.UNASKED }

    // SYS-030, LOB-090, LOB-104
    @Test fun testBuildsAlwaysReportAndProductionOnlyAfterTheDeviceOptedIn() {
        for (choice in ReportChoice.entries) assertTrue(Diagnostics.enabledFor(alwaysOn = true, choice))
        assertFalse(Diagnostics.enabledFor(alwaysOn = false, ReportChoice.UNASKED))
        assertFalse(Diagnostics.enabledFor(alwaysOn = false, ReportChoice.OFF))
        assertTrue(Diagnostics.enabledFor(alwaysOn = false, ReportChoice.ON))
        // These unit tests run the debug build, which keeps today's always-on reports.
        assertTrue(Diagnostics.alwaysOn)
        assertTrue(Diagnostics.enabled)
    }

    // LOB-104
    @Test fun theChoiceIsStoredOnTheDeviceAndStartsUnasked() {
        assertEquals(ReportChoice.UNASKED, store.choice)
        store.choice = ReportChoice.ON
        assertEquals(ReportChoice.ON, ReportChoiceStore(context).choice)
        store.choice = ReportChoice.OFF
        assertEquals(ReportChoice.OFF, ReportChoiceStore(context).choice)
    }

    // LOB-104
    @Test fun productionAsksOnceAfterSignInOnTheMenuOrGamesListOnly() {
        fun ask(alwaysOn: Boolean = false, choice: ReportChoice = ReportChoice.UNASKED, signedIn: Boolean = true,
            destination: ShellDestination = ShellDestination.MENU, game: String? = null, blocked: Boolean = false) =
            ReportPrompt.shouldPrompt(alwaysOn, choice, signedIn, destination, game, blocked)
        assertTrue(ask())
        assertTrue(ask(destination = ShellDestination.GAMES))
        assertFalse(ask(destination = ShellDestination.GAMES, game = "game"), "never over an open game")
        for (other in listOf(ShellDestination.PODS, ShellDestination.DECKS, ShellDestination.CONTACTS, ShellDestination.SETTINGS))
            assertFalse(ask(destination = other), other.name)
        assertFalse(ask(alwaysOn = true), "test builds never ask")
        assertFalse(ask(choice = ReportChoice.ON))
        assertFalse(ask(choice = ReportChoice.OFF))
        assertFalse(ask(signedIn = false))
        assertFalse(ask(blocked = true), "waits for What's new, card records and notification prompts")
    }

    // LOB-104
    @Test fun thePromptAnswersYesOrNoAndClosingItMeansNo() {
        val answers = mutableListOf<Boolean>()
        compose.setContent { ScreenTheme { ReportsPromptDialog { answers += it } } }
        compose.onNodeWithText("Send error reports?").assertIsDisplayed()
        compose.onNodeWithText(REPORTS_PROMPT_TEXT).assertIsDisplayed()
        assertTrue("no passwords or email addresses" in REPORTS_PROMPT_TEXT)
        compose.onNodeWithTag("reports-prompt-yes").performClick()
        compose.onNodeWithTag("reports-prompt-no").performClick()
        compose.runOnIdle { assertEquals(listOf(true, false), answers) }
    }

    // LOB-104
    @Test fun productionSettingsSwitchStartsOffAndSavesTheChoice() {
        compose.setContent { ScreenTheme { ReportsSetting(alwaysOn = false) } }
        val row = compose.onNodeWithTag("reports-setting")
        compose.onNodeWithText("Send error reports").assertIsDisplayed()
        row.assertIsOff().assertIsEnabled().performClick()
        row.assertIsOn()
        assertEquals(ReportChoice.ON, store.choice)
        row.performClick().assertIsOff()
        assertEquals(ReportChoice.OFF, store.choice)
    }

    // LOB-090, LOB-104
    @Test fun testBuildSettingsSwitchIsAlwaysOn() {
        compose.setContent { ScreenTheme { ReportsSetting(alwaysOn = true) } }
        compose.onNodeWithTag("reports-setting").assertIsOn().assertIsNotEnabled()
        compose.onNodeWithText("Always on in test builds").assertIsDisplayed()
    }

    // LOB-104
    @Test fun turningReportsOffDeletesEveryUnsentReport() {
        val directory = temporary.newFolder()
        val reports = CrashReportStore(directory)
        val report = CrashReport("2026-10-31T12:00:00Z", "release", "device", "trace")
        repeat(2) { reports.write(report) }
        reports.bufferLatency(report.copy(kind = "latency"))
        reports.bufferJank(report.copy(kind = "jank"))
        reports.clear()
        assertTrue(reports.pending().isEmpty())
        assertNull(reports.bufferedLatency())
        assertNull(reports.bufferedJank())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    // LOB-090, LOB-104
    @Test fun aCrashWhileReportsAreOffIsNotSavedButAndroidStillHandlesIt() {
        val reports = CrashReportStore(temporary.newFolder())
        var on = false
        val handled = mutableListOf<Throwable>()
        val handler = CrashExceptionHandler(reports, { _, failure -> handled += failure }, "release", "device",
            enabled = { on }, now = { Instant.parse("2026-10-31T12:00:00Z") })
        val failure = IllegalStateException("boom for player@example.com")
        handler.uncaughtException(Thread.currentThread(), failure)
        assertTrue(reports.pending().isEmpty())
        on = true
        handler.uncaughtException(Thread.currentThread(), failure)
        assertEquals(1, reports.pending().size)
        val body = AccountJson.format.decodeFromString<CrashReport>(reports.pending().single().readText()).body
        assertTrue("boom for [email]" in body, body)
        assertFalse("player@example.com" in body)
        assertEquals(listOf<Throwable>(failure, failure), handled)
    }
}
