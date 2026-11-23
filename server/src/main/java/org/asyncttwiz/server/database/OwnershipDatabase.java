package org.asyncttwiz.server.database;

import java.sql.Connection;
import java.sql.SQLException;
import org.postgresql.ds.PGSimpleDataSource;
import com.zaxxer.hikari.HikariDataSource;

/** Server-internal, bounded connections, separate from the explicit migration interface. */
final class OwnershipDatabase implements AutoCloseable {
    private static final String ALIVE_BYPASS = "com.zaxxer.hikari.aliveBypassWindowMs";
    private final PGSimpleDataSource source = new PGSimpleDataSource();
    private final DatabaseSettings settings;
    private final HikariDataSource work;

    OwnershipDatabase(DatabaseSettings settings) {
        this.settings = settings;
        source.setURL(settings.url());
        source.setUser(settings.user());
        source.setPassword(settings.password());
        // Override URL values too: a lifecycle/health probe must not wait indefinitely.
        source.setConnectTimeout(3);
        source.setSocketTimeout(5);
        source.setTcpKeepAlive(true);
        source.setOptions("-c statement_timeout=5000 -c lock_timeout=2000 -c idle_in_transaction_session_timeout=10000");
        source.setApplicationName("async-tt-wiz-ownership");
        var workSource = new PGSimpleDataSource();
        workSource.setURL(settings.url());
        workSource.setUser(settings.user());
        workSource.setPassword(settings.password());
        workSource.setConnectTimeout(3);
        workSource.setSocketTimeout(15);
        workSource.setTcpKeepAlive(true);
        workSource.setOptions("-c statement_timeout=10000 -c lock_timeout=2000 -c idle_in_transaction_session_timeout=10000");
        workSource.setApplicationName("async-tt-wiz-work");
        // Lazy start: service-logback.xml must be active before pool/library diagnostics.
        // Hikari would also send a validation query when borrowing a connection idle for over 500 ms,
        // which on a quiet server is nearly every borrow; dead connections are handled at first use instead.
        if (System.getProperty(ALIVE_BYPASS) == null) System.setProperty(ALIVE_BYPASS, Long.toString(Long.MAX_VALUE / 2));
        var config = new HikariDataSource();
        config.setDataSource(workSource);
        config.setPoolName("writer-work");
        config.setMaximumPoolSize(8);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(5000);
        config.setValidationTimeout(2000);
        config.setIdleTimeout(60000);
        config.setKeepaliveTime(30000);
        config.setMaxLifetime(600000);
        // Ownership acquisition, not eager pool construction, controls availability.
        config.setInitializationFailTimeout(-1);
        work = config;
    }

    Connection open() throws SQLException { return source.getConnection(); }

    /** Not validated on borrow (that costs a round trip per transaction): a backend that died while idle
     * fails the first statement with a connection error, and WriterOwnership retries on a fresh one. */
    Connection openWork() throws SQLException { return work.getConnection(); }

    void discard(Connection connection) { work.evictConnection(connection); }

    /** After a dead connection: idle siblings likely died with it (a database restart), so replace them. */
    void discardIdle() {
        var pool = work.getHikariPoolMXBean();
        if (pool != null) pool.softEvictConnections();
    }

    @Override public void close() { work.close(); }

    /**
     * Refuses unless the stored history is exactly this build's (no migrate/baseline/repair here).
     * Flyway reports the history it reads as a validation result or its own exception; a JDBC failure
     * underneath means the database could not be read (connection reset or refused, timeout, lock wait,
     * cancel, shutdown, bad credentials) and is thrown as that SQLException, so the caller reports
     * unavailability with its cause instead of a verdict on the schema. Only an error about the statement
     * or its data (SQLSTATE class 22 or 42, what a damaged history table answers) means an unsupported schema.
     */
    void validateSchema() throws SQLException, SchemaRejected {
        try {
            var flyway = org.flywaydb.core.Flyway.configure()
                    .configuration(FoundationMigrations.flyway(settings).getConfiguration())
                    .dataSource(source).load();
            var validation = flyway.validateWithResult();
            if (!validation.validationSuccessful) throw new OwnershipFailure(OwnershipFailure.Reason.UNSUPPORTED_SCHEMA);
            var info = flyway.info();
            // validate alone is insufficient on an empty schema. No migrate/baseline/repair here.
            if (info.current() == null || info.pending().length != 0 || info.applied().length == 0) {
                throw new OwnershipFailure(OwnershipFailure.Reason.UNSUPPORTED_SCHEMA);
            }
        } catch (OwnershipFailure failure) {
            throw failure;
        } catch (RuntimeException failure) {
            var sql = sqlCause(failure);
            if (sql != null && !schemaAnswer(sql)) throw sql;
            // Flyway's exception text/causes can contain JDBC URLs and credentials: the caller renders
            // only the secret-free diagnostic of this cause, never the exception itself.
            throw new SchemaRejected(failure);
        }
    }

    private static SQLException sqlCause(Throwable failure) {
        for (int depth = 0; failure != null && depth < 8; depth++, failure = failure.getCause()) {
            if (failure instanceof SQLException sql) return sql;
        }
        return null;
    }

    private static boolean schemaAnswer(SQLException failure) {
        String state = failure.getSQLState();
        return state != null && (state.startsWith("22") || state.startsWith("42"));
    }

    /** An unsupported schema refused with a library failure, kept only for the caller's secret-free
     * diagnostic; never thrown past WriterOwnership. */
    static final class SchemaRejected extends Exception {
        SchemaRejected(Throwable cause) { super("Unsupported schema", cause, false, false); }
    }

    @Override public String toString() { return "OwnershipDatabase[redacted]"; }
}
