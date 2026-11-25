package org.asyncttwiz.android

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*
import org.asyncttwiz.appui.AppPlatform

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReleaseNotesTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clear() { context.getSharedPreferences("release-notes", Context.MODE_PRIVATE).edit().clear().commit() }

    private fun change(importance: Int) = ReleaseNoteKind.Change(importance)
    private val notes = listOf(
        ReleaseNote(ReleaseNotes.INITIAL_RELEASE, change(2), "Initial release."),
        ReleaseNote("2026-10-27-temp-pile", change(1), "A Temp pile."),
        ReleaseNote("2026-10-28-whats-new", change(1), "What's new."),
        ReleaseNote("2026-10-27-faster", change(3), "Faster."),
        ReleaseNote("2026-10-28-b-fix", change(2), "Fix B."),
        ReleaseNote("2026-10-28-a-fix", change(2), "Fix A."),
        ReleaseNote("2026-10-26-old-fix", change(2), "Old fix."))

    @Test fun parsesPackagedJson() {
        val parsed = ReleaseNotes.parse("""[{"id":"2026-10-27-a","kind":"1","text":"Changed é"},{"id":"2026-10-27-b","kind":"3","text":"Fixed"}]""")
        assertEquals(listOf(ReleaseNote("2026-10-27-a", change(1), "Changed é"),
            ReleaseNote("2026-10-27-b", change(3), "Fixed")), parsed)
        assertFails { ReleaseNotes.parse("""[{"id":"x","kind":"fix","text":"t"}]""") }
        assertFails { ReleaseNotes.parse("""[{"id":"x","kind":"4","text":"t"}]""") }
    }

    // LOB-087 (owner 2026-11-25): one Changes group, the most important first, then newest first
    @Test fun changesAreOneGroupMostImportantFirst() {
        val grouped = ReleaseNotes.grouped(notes)
        assertEquals(listOf("Changes"), grouped.map { it.first })
        assertEquals(listOf("2026-10-28-whats-new", "2026-10-27-temp-pile", "2026-10-28-a-fix", "2026-10-28-b-fix",
            ReleaseNotes.INITIAL_RELEASE, "2026-10-26-old-fix", "2026-10-27-faster"), grouped.single().second.map { it.id })
        assertEquals(emptyList(), ReleaseNotes.grouped(emptyList()))
    }

    // LOB-087 (owner 2026-11-10): newly supported sets come first under New sets; no set notes, no such heading
    @Test fun newSetsComeFirstAndOnlyWhenThereAreAny() {
        val withSets = notes + ReleaseNote("2026-10-28-set-foundations", ReleaseNoteKind.Set, "Foundations")
        assertEquals(listOf("New sets", "Changes"), ReleaseNotes.grouped(withSets).map { it.first })
        assertEquals(listOf("2026-10-28-set-foundations"), ReleaseNotes.grouped(withSets).first().second.map { it.id })
        assertEquals(ReleaseNoteKind.Set, ReleaseNotes.parse("""[{"id":"2026-10-28-set-x","kind":"set","text":"X"}]""").single().kind)
        assertFalse("New sets" in ReleaseNotes.grouped(notes).map { it.first })
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
        val preferences = ReleaseNotePreferences(AppPlatform.current)
        val first = preferences.unseen(notes)
        // The record is now stored, so later runs never re-decide fresh install vs update.
        assertEquals(first, ReleaseNotePreferences(AppPlatform.current).unseen(notes))
        val later = notes + ReleaseNote("2026-10-29-later", change(2), "Later.")
        assertEquals(first + later.last(), ReleaseNotePreferences(AppPlatform.current).unseen(later))
        preferences.markSeen(later)
        assertEquals(emptyList(), ReleaseNotePreferences(AppPlatform.current).unseen(later))
    }

    @Test fun packagedAssetHoldsTheRepositoryNotes() {
        val packaged = ReleaseNotes.parse(context.assets.open(ReleaseNotes.ASSET).use { it.readBytes().decodeToString() })
        assertTrue(packaged.any { it.id == ReleaseNotes.INITIAL_RELEASE && it.kind == ReleaseNoteKind.Change(2) })
        assertTrue(packaged.any { it.id == "2026-10-28-whats-new" })
    }

    @Test fun dialogShowsGroupsAndOkDismisses() {
        var dismissed = false
        compose.setContent { MaterialTheme { if (!dismissed) WhatsNewDialog(notes) { dismissed = true } } }
        compose.onNodeWithText("What's new").assertIsDisplayed()
        compose.onNodeWithText("Changes").assertIsDisplayed()
        compose.onNodeWithText("New").assertDoesNotExist()
        compose.onNodeWithText("Fixed").assertDoesNotExist()
        compose.onNodeWithText("Fix A.").assertExists()
        compose.onNodeWithTag("whats-new-ok").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }
}
