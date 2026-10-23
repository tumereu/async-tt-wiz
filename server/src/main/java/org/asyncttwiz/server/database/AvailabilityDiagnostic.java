package org.asyncttwiz.server.database;

import java.sql.SQLException;
import java.util.Set;

/** Transport evidence only. JDBC/jOOQ messages can contain SQL, bind values, URLs and roles.
 * Emit known input-free transport messages verbatim, never arbitrary exception text/stack traces. */
public final class AvailabilityDiagnostic {
    private AvailabilityDiagnostic() { }
    private static final Set<String> SAFE_MESSAGES = Set.of(
            "The connection attempt failed.", "An I/O error occurred while sending to the backend.",
            "This connection has been closed.", "Connection reset", "Connection reset by peer",
            "Connection refused", "Read timed out", "Socket closed", "Broken pipe",
            "Connect timed out", "FATAL: terminating connection due to administrator command",
            "Work connection validation failed");

    public static String detail(Throwable failure) {
        var result = new StringBuilder();
        for (int depth = 0; failure != null && depth < 8; depth++, failure = failure.getCause()) {
            String state = failure instanceof SQLException sql ? sql.getSQLState() : null;
            result.append(" cause").append(depth).append("={class=").append(failure.getClass().getName())
                    .append(" sqlState=").append(state != null && state.matches("[0-9A-Z]{5}") ? state : "NONE")
                    .append(" message=\"").append(SAFE_MESSAGES.contains(failure.getMessage() == null ? "" : failure.getMessage())
                            ? failure.getMessage() : "[redacted]").append("\"}");
        }
        return result.toString();
    }
}
