package org.asyncttwiz.server;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.asyncttwiz.server.database.DatabaseSettings;
import org.asyncttwiz.server.database.OwnershipFailure;
import org.asyncttwiz.server.database.WriterOwnership;

/** All future command handlers must use this admission/fenced transaction boundary. */
public final class ServiceRuntime implements AutoCloseable {
    private final WriterOwnership ownership;
    private final ScheduledExecutorService recovery = Executors.newSingleThreadScheduledExecutor(r -> {
        var thread = new Thread(r, "writer-recovery");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final ReentrantReadWriteLock admission = new ReentrantReadWriteLock();

    public ServiceRuntime(DatabaseSettings settings) { ownership = new WriterOwnership(settings); }

    public void start() {
        recovery.scheduleWithFixedDelay(() -> {
            if (stopping.get()) return;
            try { ownership.refresh(); }
            catch (OwnershipFailure ignored) { /* Safe unavailable state; next cycle revalidates/reloads. */ }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    public boolean ready() {
        // Future implemented inbox/outbox/mail/push workers must join this predicate after reload.
        return !stopping.get() && ownership.token().isPresent() && !stopping.get();
    }

    public <T> T transaction(WriterOwnership.Transaction<T> work) {
        if (stopping.get()) throw new OwnershipFailure(OwnershipFailure.Reason.NOT_OWNER);
        admission.readLock().lock();
        try {
            if (stopping.get()) throw new OwnershipFailure(OwnershipFailure.Reason.NOT_OWNER);
            var token = ownership.token().orElseThrow(() -> new OwnershipFailure(OwnershipFailure.Reason.NOT_OWNER));
            return ownership.transaction(token, work);
        } finally { admission.readLock().unlock(); }
    }

    /** Stop admission first, then allow bounded local transactions to finish before releasing ownership. */
    @Override public void close() {
        if (!stopping.compareAndSet(false, true)) return;
        recovery.shutdownNow();
        boolean drained = false;
        try {
            drained = admission.writeLock().tryLock(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        finally {
            ownership.close();
            if (drained) admission.writeLock().unlock();
        }
    }

    @Override public String toString() { return "ServiceRuntime[redacted]"; }
}
