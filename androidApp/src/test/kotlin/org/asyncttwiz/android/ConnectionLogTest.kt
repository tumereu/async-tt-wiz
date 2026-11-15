package org.asyncttwiz.android

import org.junit.Test
import kotlin.test.assertEquals

class ConnectionLogTest {
    // Problem report VSVLSK: a socket opened again every second logs at most three lines of a kind a minute; the rest are
    // counted and said with the next line of the kind, and other kinds are logged as they come
    @Test fun repeatsOfAKindAreSummarisedAndOtherKindsStillLogged() {
        var now = 0L
        val lines = mutableListOf<String>()
        val log = ConnectionLog({ now }) { warn, line -> lines += (if (warn) "W " else "I ") + line }
        repeat(60) { log.info("opened", "Connected ($it)"); now += 1_000 }
        log.warn("fenced", "Fenced")
        log.info("opened", "Connected (later)")
        assertEquals(listOf("I Connected (0)", "I Connected (1)", "I Connected (2) (more like it this minute are only counted)",
            "W Fenced", "I Connected (later) (57 more like it before this, not logged)"), lines)
    }
}
