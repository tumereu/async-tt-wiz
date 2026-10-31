package org.asyncttwiz.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class LibrarianWireTest {
    @Test fun snapshotsAndPickRequestsRoundTripAndOldRequestsDefaultToNoSpend() {
        val card = SubmittedDefinition("digest", CardDefinition("Cogwork Librarian", listOf(DefinitionFace("face", "Cogwork Librarian"))))
        val settings = DraftSettings.glimpse().copy(librarians = 10, librarianDefinition = card)
        assertEquals(settings, Json.decodeFromString<DraftSettings>(Json.encodeToString(settings)))
        val pick = PickPod("request", "attempt", "pack", listOf("a", "b"), listOf("c"), listOf("librarian"))
        assertEquals(pick, Json.decodeFromString<PickPod>(Json.encodeToString(pick)))
        val old = Json.decodeFromString<PickPod>("""{"requestId":"r","draftAttemptId":"a","packId":"p","keep":["c"]}""")
        assertTrue(old.librarians.isEmpty())
        assertEquals(0, Json.decodeFromString<DraftSettings>("{}").librarians)
        assertFalse(Json.decodeFromString<DraftCopy>("""{"id":"c","definition":"d"}""").librarian)
        val log = DraftPickLog("p", "pack", 0, listOf("a", "b"), librarians = listOf("l"))
        assertEquals(log, Json.decodeFromString<DraftPickLog>(Json.encodeToString(log)))
    }

    // DRF-051
    @Test fun librarianBoundsAndGridRestriction() {
        for (mode in listOf(DraftMode.BOOSTER, DraftMode.GLIMPSE)) {
            for (count in 0..10) assertTrue(DraftSettings(mode = mode, librarians = count).validate(2).isEmpty())
            for (count in listOf(-1, 11, Int.MAX_VALUE))
                assertContains(DraftSettings(mode = mode, librarians = count).validate(2), DraftSettingsIssue.LIBRARIANS)
        }
        assertContains(DraftSettings.grid().copy(librarians = 1).validate(2), DraftSettingsIssue.LIBRARIANS)
    }
}
