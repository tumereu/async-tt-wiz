package org.asyncttwiz.server.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Closeable single-service ownership boundary. The service lifecycle calls refresh periodically;
 * token() probes existing ownership without reacquiring. Neither method is a cached ready flag.
 * Future mutating repositories must enter through transaction before taking game/stream locks.
 */
public final class WriterOwnership implements AutoCloseable {
    // Stable two-int advisory namespace, shared by all service instances using this database.
    private static final int LOCK_NAMESPACE = 0x41545457;
    private static final int LOCK_ID = 1;
    private static final String EPOCH = "SELECT epoch FROM public.service_writer WHERE singleton = true";
    private final OwnershipDatabase database;
    private final String diagnosticId = java.util.UUID.randomUUID().toString();
    private Connection lockConnection;
    private Token current;
    private boolean closed;

    /** Opaque instance-bound capability, not a caller-supplied epoch or durable client token. */
    public static final class Token {
        private final long epoch;
        private Token(long epoch) { this.epoch = epoch; }
        public long epoch() { return epoch; }
        @Override public String toString() { return "WriterToken[epoch=" + epoch + "]"; }
    }

    @FunctionalInterface
    public interface Transaction<T> {
        /** Local database work only; do not commit/rollback, change autocommit or retain connection. */
        T execute(Connection connection) throws SQLException;
    }

    public WriterOwnership(DatabaseSettings settings) { database = new OwnershipDatabase(settings); }

    /** Acquire/recover, validating history and reloading the implemented singleton before availability. */
    public synchronized Optional<Token> refresh() {
        if (closed) throw new OwnershipFailure(OwnershipFailure.Reason.CLOSED);
        if (probe()) return Optional.of(current);
        try {
            database.validateSchema();
            lockConnection = database.open();
            try (var statement = lockConnection.createStatement();
                 var rows = statement.executeQuery("SELECT pg_try_advisory_lock(" + LOCK_NAMESPACE + ", " + LOCK_ID + ")")) {
                if (!rows.next() || !rows.getBoolean(1)) {
                    release();
                    return Optional.empty();
                }
            }
            lockConnection.setAutoCommit(false);
            long next;
            // Exclusive row lock waits for old transactions' shared locks, even after lock-session loss.
            try (var statement = lockConnection.createStatement(); var rows = statement.executeQuery(EPOCH + " FOR UPDATE")) {
                if (!rows.next()) throw new OwnershipFailure(OwnershipFailure.Reason.UNSUPPORTED_SCHEMA);
                next = Math.addExact(rows.getLong(1), 1);
            }
            try (var statement = lockConnection.prepareStatement("UPDATE public.service_writer SET epoch = ? WHERE singleton = true")) {
                statement.setLong(1, next);
                if (statement.executeUpdate() != 1) throw new OwnershipFailure(OwnershipFailure.Reason.UNSUPPORTED_SCHEMA);
            }
            lockConnection.commit();
            lockConnection.setAutoCommit(true);
            // The singleton is the only durable runtime state implemented in this child.
            // Reload after commit; future inbox/outbox workers must join recovery before readiness.
            current = new Token(next);
            if (!probe()) throw new OwnershipFailure(OwnershipFailure.Reason.DATABASE_UNAVAILABLE);
            diagnostic("acquired", null, next);
            return Optional.of(current);
        } catch (OwnershipFailure failure) {
            release();
            diagnostic("refresh", failure, -1);
            throw failure;
        } catch (SQLException failure) {
            release();
            diagnostic("refresh", failure, -1);
            throw new OwnershipFailure(OwnershipFailure.Reason.DATABASE_UNAVAILABLE);
        } catch (RuntimeException failure) {
            release();
            diagnostic("refresh", failure, -1);
            throw new OwnershipFailure(OwnershipFailure.Reason.UNSUPPORTED_SCHEMA);
        }
    }

    /** Empty immediately after observed database/lock loss; never returns an unprobed cached token. */
    public synchronized Optional<Token> token() {
        return !closed && probe() ? Optional.of(current) : Optional.empty();
    }

    private boolean probe() {
        if (current == null || lockConnection == null) return false;
        try (var statement = lockConnection.createStatement();
             var rows = statement.executeQuery("SELECT epoch, EXISTS (SELECT 1 FROM pg_locks WHERE locktype = 'advisory' "
                     + "AND pid = pg_backend_pid() AND classid = " + LOCK_NAMESPACE + "::oid AND objid = " + LOCK_ID
                     + "::oid AND objsubid = 2 AND granted) FROM public.service_writer WHERE singleton = true")) {
            if (rows.next() && rows.getLong(1) == current.epoch && rows.getBoolean(2)) return true;
        } catch (SQLException failure) {
            // Invalidates before any recovery attempt; raw exceptions never cross this boundary.
            long epoch = current.epoch;
            release();
            diagnostic("probe", failure, epoch);
            return false;
        }
        long epoch = current.epoch;
        release();
        diagnostic("probe-fence-mismatch", null, epoch);
        return false;
    }

    /** Shared epoch lock lasts through callback and commit; independent transactions stay concurrent. */
    public <T> T transaction(Token token, Transaction<T> work) {
        synchronized (this) {
            // A database restart can leave the same stored epoch but destroy the lock session.
            // Do not admit using only an in-memory token, even before a periodic probe runs.
            if (closed || token == null || token != current || !probe()) {
                throw new OwnershipFailure(OwnershipFailure.Reason.NOT_OWNER);
            }
        }
        String phase = "open";
        try (var connection = database.open()) {
            phase = "begin";
            connection.setAutoCommit(false);
            try {
                phase = "fence";
                lockEpoch(connection, token.epoch);
                synchronized (this) {
                    if (closed || current != token) throw new OwnershipFailure(OwnershipFailure.Reason.NOT_OWNER);
                }
                phase = "callback";
                T result = work.execute(connection);
                phase = "commit";
                connection.commit();
                phase = "close";
                return result;
            } catch (Exception failure) {
                try { connection.rollback(); } catch (SQLException ignored) { }
                throw failure;
            }
        } catch (Exception failure) {
            recheckOwnership(token);
            diagnostic(phase, failure, token.epoch);
            // Even a driver/callback/close error's suppressed exceptions must not leak inputs.
            if (failure instanceof OwnershipFailure safe) throw new OwnershipFailure(safe.reason());
            throw new OwnershipFailure(OwnershipFailure.Reason.TRANSACTION_FAILED);
        }
    }

    /** Internal transaction entry, also exercised directly to prove the fence after a delayed probe. */
    static void lockEpoch(Connection connection, long expectedEpoch) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(EPOCH + " FOR SHARE")) {
            if (!rows.next() || rows.getLong(1) != expectedEpoch) {
                throw new OwnershipFailure(OwnershipFailure.Reason.NOT_OWNER);
            }
        }
    }

    private synchronized void recheckOwnership(Token token) {
        // Work connections do not hold the advisory lock. An open/SQL/callback/commit failure
        // says nothing about the dedicated lock session: releasing it here would let one
        // transient work-connection failure fence every other worker and force a new epoch.
        // Probe the actual ownership boundary instead; real session/epoch loss still releases.
        // A delayed failure from an old transaction must not disturb a recovered owner.
        if (current == token) probe();
    }

    /** Internal correlation only: unrelated to any account, request, token or database input. */
    public String diagnosticId() { return diagnosticId; }

    /** Fixed categories only, including wrapped JDBC/codec failures; never render a Throwable. */
    public static String failureCategory(Throwable failure) {
        if (failure == null) return "NONE";
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
            if (cause instanceof OwnershipFailure safe) return "OWNERSHIP_" + safe.reason().name();
            if (cause instanceof java.sql.SQLTimeoutException) return "SQL_TIMEOUT";
            if (cause instanceof SQLException sql) {
                String state = sql.getSQLState();
                if (state == null) return "SQL_OTHER";
                return switch (state) {
                    case "40P01" -> "SQL_DEADLOCK";
                    case "55P03" -> "SQL_LOCK_UNAVAILABLE";
                    case "57014" -> "SQL_CANCELLED";
                    default -> state.startsWith("08") ? "SQL_CONNECTION" : "SQL_OTHER";
                };
            }
            if (cause instanceof kotlinx.serialization.SerializationException) return "CODEC";
        }
        if (failure instanceof ClassCastException) return "TYPE_MISMATCH";
        if (failure instanceof IllegalArgumentException) return "INVALID_ARGUMENT";
        if (failure instanceof IllegalStateException) return "INVALID_STATE";
        if (failure instanceof java.io.IOException) return "IO";
        return "OTHER";
    }

    private void diagnostic(String phase, Throwable failure, long epoch) {
        // Dedicated secret-free stderr event: service root/library logging deliberately stays off.
        System.err.println("Writer availability: owner=" + diagnosticId + " thread=" + Thread.currentThread().threadId()
                + " epoch=" + epoch + " phase=" + phase + " category=" + failureCategory(failure));
    }

    private void release() {
        current = null;
        var connection = lockConnection;
        lockConnection = null;
        if (connection != null) {
            try { connection.close(); } catch (SQLException ignored) { }
        }
    }

    /** Terminal, idempotent; restart uses a fresh manager and epoch. Does not await application callbacks. */
    @Override public synchronized void close() {
        closed = true;
        release();
    }

    @Override public String toString() { return "WriterOwnership[redacted]"; }
}
