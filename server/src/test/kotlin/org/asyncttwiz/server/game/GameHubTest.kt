package org.asyncttwiz.server.game

import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.Future
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.DatabaseSettings
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GameHubTest {
    @Test fun `a dead scheduled task makes the still running executor unavailable`() {
        val settings = DatabaseSettings("jdbc:postgresql://127.0.0.1:1/unused", "unused", "unused")
        ServiceRuntime(settings).use { runtime ->
            val executor = ScheduledThreadPoolExecutor(1)
            GameHub(runtime, executor).use { hub ->
                hub.start()
                assertTrue(hub.available())
                // Cancelling models the same terminal future state as an uncaught task failure.
                (executor.queue.first() as Future<*>).cancel(false)
                assertFalse(executor.isShutdown)
                assertFalse(hub.available())
            }
        }
    }
}
