package org.asyncttwiz.server.database;

import org.flywaydb.core.Flyway;

/** One forward-only history for the explicit runner and disposable code generation. */
public final class FoundationMigrations {
    private FoundationMigrations() {}

    public static Flyway flyway(DatabaseSettings settings) {
        return Flyway.configure()
                .dataSource(settings.url(), settings.user(), settings.password())
                .locations("classpath:db/migration")
                .defaultSchema("public")
                .schemas("public")
                .createSchemas(false)
                .cleanDisabled(true)
                .baselineOnMigrate(false)
                .outOfOrder(false)
                .validateMigrationNaming(true)
                .validateOnMigrate(true)
                // Flyway otherwise ignores future migrations by default.
                .ignoreMigrationPatterns(new String[0])
                .executeInTransaction(true)
                .connectRetries(0)
                // No raw JDBC URL or exception details in runner logs, even on failure.
                .loggers(new String[0])
                .load();
    }

    public static int migrate(DatabaseSettings settings) {
        return flyway(settings).migrate().migrationsExecuted;
    }
}
