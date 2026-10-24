package org.asyncttwiz.server.game

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class GameHandlerTimingTest {
    @Test fun `slow handler includes queue wait and failures without message payloads`() {
        var now = 0L
        val logged = mutableListOf<List<Any>>()
        val timing = GameHandlerTiming({ now }) { game, handler, queue, handling, outcome ->
            logged.add(listOf(game, handler, queue, handling, outcome))
        }
        val slow = timing.queued("game", "ResumeGameStream")
        now = 800_000_000
        slow { now += 200_000_000 }
        assertEquals(listOf("game", "ResumeGameStream", 800.0, 200.0, "ok"), logged.single())
        timing.queued("game", "SubscribeGame").invoke { now += 999_000_000 }
        assertEquals(1, logged.size)
        assertThrows(IllegalStateException::class.java) {
            timing.queued("game", "SubscribeGame").invoke { now += 1_000_000_000; error("private message") }
        }
        assertEquals("IllegalStateException", logged.last().last())
    }
}
