package arcanum.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which card records setSupport checks of a supported set. Invented cards. */
class SetSupportTest {
    private fun record(uuid: String, vararg flags: String) = com.google.gson.JsonObject().apply {
        addProperty("name", "Ploom Card $uuid")
        addProperty("uuid", uuid)
        flags.forEach { addProperty(it, true) }
    }

    @Test fun aPaperSetSkipsItsOnlineOnlyCards() {
        val records = listOf(record("1"), record("2", "isOnlineOnly"), record("3", "isRebalanced"))
        assertFalse(onlineSet(records))
        assertEquals(listOf("Ploom Card 1"), setCards(records, keepOnline = onlineSet(records)).map { it.name })
    }

    // A set only online (Through the Omenpaths: paper cards under other names, on Arena only) is listed on purpose: its
    // cards are checked, still without Un-cards or rebalanced ones
    @Test fun anOnlineOnlySetChecksItsCards() {
        val records = listOf(record("1", "isOnlineOnly"), record("2", "isOnlineOnly"), record("3", "isOnlineOnly", "isRebalanced"))
        assertTrue(onlineSet(records))
        assertEquals(listOf("Ploom Card 1", "Ploom Card 2"), setCards(records, keepOnline = onlineSet(records)).map { it.name })
        assertFalse(onlineSet(emptyList()))
    }
}
