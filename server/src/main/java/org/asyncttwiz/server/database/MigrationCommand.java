package org.asyncttwiz.server.database;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.exception.FlywayValidateException;

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
            System.err.println("Migration failed; no destructive fallback attempted. Check configuration and schema compatibility.");
            System.err.println(diagnostic(failure));
            System.exit(1);
        }
    }

    /**
     * Why a migration failed, without its inputs: JDBC/Flyway messages can contain URLs, SQL and mounted
     * secret paths, so this shows only Flyway's error code (e.g. DB_CONNECTION, VALIDATE_ERROR), a validate
     * failure's report, and for each cause its class, SQL state and the known input-free transport messages
     * ({@link AvailabilityDiagnostic}).
     */
    static String diagnostic(Throwable failure) {
        String code = "NONE";
        String validation = "";
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
            if (code.equals("NONE") && cause instanceof FlywayException flyway && flyway.getErrorCode() instanceof Enum<?> named) {
                code = named.name();
            }
            if (validation.isEmpty() && cause instanceof FlywayValidateException invalid && invalid.getMessage() != null) {
                validation = " validation=\"" + validationSummary(invalid.getMessage()) + "\"";
            }
        }
        return java.time.Instant.now() + " Migration failure: flywayError=" + code + validation + AvailabilityDiagnostic.detail(failure);
    }

    /** Flyway's validate report names only migration versions, descriptions, scripts and checksums (no URL, SQL
     * or credentials); it says which migration disagrees, which the error code alone does not. One line, bounded. */
    private static String validationSummary(String message) {
        String report = message.replaceFirst("(?s)\\s*Need more flexibility with validation rules\\?.*$", "")
                .replaceAll("\\s*\\R\\s*", " | ").replace('"', '\'');
        return report.length() <= 1000 ? report : report.substring(0, 1000) + "...";
    }
}
