package org.asyncttwiz.server.database;

import java.sql.Connection;
import java.sql.SQLException;
import org.postgresql.ds.PGSimpleDataSource;
import com.zaxxer.hikari.HikariDataSource;

/** Server-internal, bounded connections, separate from the explicit migration interface. */
final class OwnershipDatabase implements AutoCloseable {
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

    Connection openWork() throws SQLException {
        // Validate even recently returned connections (Hikari normally skips these for 500ms).
        // A backend may have died between transactions without a client-side socket error yet.
        for (int attempt = 0; attempt < 2; attempt++) {
            var connection = work.getConnection();
            try {
                if (connection.isValid(2)) return connection;
            } catch (SQLException failure) {
                work.evictConnection(connection);
                throw failure;
            }
            work.evictConnection(connection);
        }
        throw new SQLException("Work connection validation failed", "08006");
    }

    void discard(Connection connection) { work.evictConnection(connection); }

    @Override public void close() { work.close(); }

    void validateSchema() {
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
            // Flyway's exception text/causes can contain JDBC URLs and credentials.
            throw new OwnershipFailure(OwnershipFailure.Reason.UNSUPPORTED_SCHEMA);
        }
    }

    @Override public String toString() { return "OwnershipDatabase[redacted]"; }
}
