package org.asyncttwiz.server.game

import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.DatabaseSettings

object GameplayMaintenance {
    /** Run with the service stopped. The ordinary writer lease fences competing execution. */
    @JvmStatic fun run() {
        ServiceRuntime(DatabaseSettings.fromEnvironment(System.getenv())).use { runtime ->
            runtime.start()
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15)
            while (!runtime.ready()) {
                check(System.nanoTime() < deadline) { "Gameplay maintenance could not acquire writer ownership" }
                Thread.sleep(20)
            }
            GameService(runtime).checkpointGameplay()
        }
    }
}
