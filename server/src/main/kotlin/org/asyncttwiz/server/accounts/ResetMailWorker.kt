package org.asyncttwiz.server.accounts

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.OwnershipFailure

class ResetMailWorker(private val runtime: ServiceRuntime, private val resets: PasswordResetService,
                      private val mailer: ResetMailer) : AutoCloseable {
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "reset-mail").apply { isDaemon = true }
    }
    fun start() {
        scheduler.scheduleWithFixedDelay({
            try { if (runtime.ready()) resets.deliverDue(mailer) }
            catch (_: OwnershipFailure) { /* Ordinary ownership recovery, no network replay in a DB transaction. */ }
            catch (_: Exception) { runtime.invalidateWorkerRecovery() }
        }, 0, 500, TimeUnit.MILLISECONDS)
    }
    fun available() = !scheduler.isShutdown
    override fun close() {
        scheduler.shutdownNow()
        try { scheduler.awaitTermination(5, TimeUnit.SECONDS) }
        catch (_: InterruptedException) { Thread.currentThread().interrupt() }
    }
}
