package org.asyncttwiz.server.database;

import java.sql.Connection;
import java.sql.SQLException;
import org.postgresql.ds.PGSimpleDataSource;

/** Server-internal, bounded connections, separate from the explicit migration interface. */
final class OwnershipDatabase {
    private final PGSimpleDataSource source = new PGSimpleDataSource();
    private final DatabaseSettings settings;

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
    }

    Connection open() throws SQLException { return source.getConnection(); }

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
