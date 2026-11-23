package org.asyncttwiz.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TableNotesTest {
    // SYS-078: notes on the table show one at a time; a note told while another one shows waits for it and shows next,
    // never replacing it at once (the same text twice in a row: once)
    @Test fun aNoteToldWhileAnotherShowsWaitsForIt() {
        val routes = EngineTableRoutes("a", { null }, {}, { null })
        routes.tell("That wasn't taken: the game had moved on.")
        routes.tell("Bob took a mulligan · your turn stands")
        routes.tell("Bob took a mulligan · your turn stands")
        assertEquals("That wasn't taken: the game had moved on.", routes.note)
        routes.noteDone()
        assertEquals("Bob took a mulligan · your turn stands", routes.note)
        routes.noteDone()
        assertNull(routes.note)
    }
}
