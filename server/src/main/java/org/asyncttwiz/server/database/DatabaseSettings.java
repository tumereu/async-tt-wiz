package org.asyncttwiz.server.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.postgresql.Driver;

/** File-backed credentials; neither diagnostics nor toString disclose connection inputs. */
public final class DatabaseSettings {
    private final String url;
    private final String user;
    private final String password;

    public DatabaseSettings(String url, String user, String password) {
        Properties parsed = url == null ? null : Driver.parseURL(url, null);
        if (parsed == null || !url.startsWith("jdbc:postgresql://")
                || parsed.containsKey("user") || parsed.containsKey("password")) {
            throw new IllegalArgumentException("DB_URL must be a PostgreSQL network URL without credentials");
        }
        if (user == null || user.isBlank() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Database credentials are required");
        }
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public static DatabaseSettings fromEnvironment(Map<String, String> environment) throws IOException {
        String secretPath = required(environment, "DB_PASSWORD_FILE");
        // A final LF/CRLF from mounted text-secret files is not part of the password.
        String password = Files.readString(Path.of(secretPath)).replaceFirst("\\r?\\n$", "");
        return new DatabaseSettings(required(environment, "DB_URL"), required(environment, "DB_USER"), password);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    public String url() { return url; }
    public String user() { return user; }
    public String password() { return password; }

    @Override public String toString() { return "DatabaseSettings[redacted]"; }
}
