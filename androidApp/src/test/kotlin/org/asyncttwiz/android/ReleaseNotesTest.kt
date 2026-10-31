package org.asyncttwiz.android

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReleaseNotesTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clear() { context.getSharedPreferences("release-notes", Context.MODE_PRIVATE).edit().clear().commit() }

    private val notes = listOf(
        ReleaseNote(ReleaseNotes.INITIAL_RELEASE, ReleaseNoteKind.ADD, "Initial release."),
        ReleaseNote("2026-10-27-temp-pile", ReleaseNoteKind.ADD, "A Temp pile."),
        ReleaseNote("2026-10-28-whats-new", ReleaseNoteKind.ADD, "What's new."),
        ReleaseNote("2026-10-27-faster", ReleaseNoteKind.MOD, "Faster."),
        ReleaseNote("2026-10-28-b-fix", ReleaseNoteKind.FIX, "Fix B."),
        ReleaseNote("2026-10-28-a-fix", ReleaseNoteKind.FIX, "Fix A."),
        ReleaseNote("2026-10-26-old-fix", ReleaseNoteKind.FIX, "Old fix."))

    @Test fun parsesPackagedJson() {
        val parsed = ReleaseNotes.parse("""[{"id":"2026-10-27-a","kind":"mod","text":"Changed é"},{"id":"2026-10-27-b","kind":"fix","text":"Fixed"}]""")
        assertEquals(listOf(ReleaseNote("2026-10-27-a", ReleaseNoteKind.MOD, "Changed é"),
            ReleaseNote("2026-10-27-b", ReleaseNoteKind.FIX, "Fixed")), parsed)
        assertFails { ReleaseNotes.parse("""[{"id":"x","kind":"new","text":"t"}]""") }
    }

    // LOB-087
    @Test fun groupsNewChangedFixedNewestFirst() {
        val grouped = ReleaseNotes.grouped(notes)
        assertEquals(listOf(ReleaseNoteKind.ADD, ReleaseNoteKind.MOD, ReleaseNoteKind.FIX), grouped.map { it.first })
        assertEquals(listOf("2026-10-28-whats-new", ReleaseNotes.INITIAL_RELEASE, "2026-10-27-temp-pile"), grouped[0].second.map { it.id })
        assertEquals(listOf("2026-10-28-a-fix", "2026-10-28-b-fix", "2026-10-26-old-fix"), grouped[2].second.map { it.id })
        assertEquals(listOf(ReleaseNoteKind.FIX), ReleaseNotes.grouped(notes.filter { it.kind == ReleaseNoteKind.FIX }).map { it.first })
    }

    // LOB-087
    @Test fun freshInstallSeesNothingUpdateSeesAllButInitialStoredRecordSeesOnlyNewer() {
        val fresh = ReleaseNotes.seenRecord(null, notes, freshInstall = true)
        assertEquals(emptyList(), ReleaseNotes.unseen(notes, fresh))
        val updated = ReleaseNotes.seenRecord(null, notes, freshInstall = false)
        assertEquals(notes.drop(1), ReleaseNotes.unseen(notes, updated))
        val stored = setOf(ReleaseNotes.INITIAL_RELEASE, "2026-10-27-temp-pile", "removed-note")
        assertEquals(stored, ReleaseNotes.seenRecord(stored, notes, freshInstall = false))
        assertEquals(notes.drop(2), ReleaseNotes.unseen(notes, ReleaseNotes.seenRecord(stored, notes, freshInstall = true)))
    }

    // LOB-088
    @Test fun dialogShowsAtOnceExceptOverAnOpenGameOrPod() {
        assertTrue(ReleaseNotes.showDialog(notes, pendingNotificationRoute = false, ActivityScreen.NONE))
        assertTrue(ReleaseNotes.showDialog(notes, false, ActivityScreen(podList = true)))
        assertFalse(ReleaseNotes.showDialog(emptyList(), false, ActivityScreen.NONE))
        assertFalse(ReleaseNotes.showDialog(notes, pendingNotificationRoute = true, ActivityScreen.NONE))
        assertFalse(ReleaseNotes.showDialog(notes, false, ActivityScreen(game = "g1")))
        assertFalse(ReleaseNotes.showDialog(notes, false, ActivityScreen(pod = "p1")))
    }

    @Test fun preferencesSettleTheRecordOnceAndMarkSeen() {
        val preferences = ReleaseNotePreferences(context)
        val first = preferences.unseen(notes)
        // The record is now stored, so later runs never re-decide fresh install vs update.
        assertEquals(first, ReleaseNotePreferences(context).unseen(notes))
        val later = notes + ReleaseNote("2026-10-29-later", ReleaseNoteKind.MOD, "Later.")
        assertEquals(first + later.last(), ReleaseNotePreferences(context).unseen(later))
        preferences.markSeen(later)
        assertEquals(emptyList(), ReleaseNotePreferences(context).unseen(later))
    }

    @Test fun packagedAssetHoldsTheRepositoryNotes() {
        val packaged = ReleaseNotes.parse(context.assets.open(ReleaseNotes.ASSET).use { it.readBytes().decodeToString() })
        assertTrue(packaged.any { it.id == ReleaseNotes.INITIAL_RELEASE && it.kind == ReleaseNoteKind.ADD })
        assertTrue(packaged.any { it.id == "2026-10-28-whats-new" })
    }

    @Test fun dialogShowsGroupsAndOkDismisses() {
        var dismissed = false
        compose.setContent { MaterialTheme { if (!dismissed) WhatsNewDialog(notes) { dismissed = true } } }
        compose.onNodeWithText("What's new").assertIsDisplayed()
        compose.onNodeWithText("New").assertIsDisplayed()
        compose.onNodeWithText("Changed").assertIsDisplayed()
        compose.onNodeWithText("Fixed").assertExists()
        compose.onNodeWithText("Fix A.").assertExists()
        compose.onNodeWithTag("whats-new-ok").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }
}
