package org.asyncttwiz.server.database;

/** This explicit command is not service startup. No clean, repair or baseline modes. */
public final class MigrationCommand {
    private MigrationCommand() {}

    public static void main(String[] args) {
        if (args.length != 1 || !args[0].equals("migrate")) {
            System.err.println("Usage: server migrate");
            System.exit(2);
        }
        try {
            int count = FoundationMigrations.migrate(DatabaseSettings.fromEnvironment(System.getenv()));
            System.out.println("Migration successful; applied=" + count);
        } catch (Exception failure) {
            // JDBC/Flyway errors can contain URLs, SQL and mounted secret paths.
            System.err.println("Migration failed; no destructive fallback attempted. Check configuration and schema compatibility.");
            System.exit(1);
        }
    }
}
