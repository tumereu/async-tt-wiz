package org.asyncttwiz.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.Map;
import org.asyncttwiz.server.database.DatabaseSettings;
import org.asyncttwiz.server.accounts.ResetMailSettings;

/** Explicit runtime inputs only; Spring's arbitrary environment/property binding is not enabled. */
public record ServiceSettings(DatabaseSettings database, URI publicBaseUrl, InetAddress publicAddress,
                              int publicPort, InetAddress healthAddress, int healthPort, ResetMailSettings resetMail) {
    // Embedded foundation checks may omit adapters; the actual serve command requires mail configuration.
    public ServiceSettings(DatabaseSettings database, URI base, InetAddress address, int port,
                           InetAddress healthAddress, int healthPort) {
        this(database, base, address, port, healthAddress, healthPort, null);
    }
    public static ServiceSettings fromEnvironment(Map<String, String> environment) throws IOException {
        var database = DatabaseSettings.fromEnvironment(environment);
        var base = URI.create(required(environment, "PUBLIC_BASE_URL"));
        if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme())) || base.getHost() == null
                || base.getRawUserInfo() != null || base.getRawQuery() != null || base.getRawFragment() != null
                || base.getPort() == 0 || base.getPort() > 65535) {
            throw new IllegalArgumentException("Invalid public base URL");
        }
        int port = port(environment, "SERVER_PORT", 8080);
        int healthPort = port(environment, "HEALTH_PORT", 8081);
        if (port == healthPort) throw new IllegalArgumentException("Public and health ports must differ");
        return new ServiceSettings(database, base,
                address(environment, "SERVER_ADDRESS", "0.0.0.0"), port,
                address(environment, "HEALTH_ADDRESS", "127.0.0.1"), healthPort,
                ResetMailSettings.fromEnvironment(environment));
    }

    private static InetAddress address(Map<String, String> values, String key, String fallback) throws IOException {
        String value = values.getOrDefault(key, fallback);
        if (value.isBlank()) throw new IllegalArgumentException("Invalid listener address");
        return InetAddress.getByName(value);
    }

    private static int port(Map<String, String> values, String name, int fallback) {
        String value = values.getOrDefault(name, Integer.toString(fallback));
        if (!value.matches("[0-9]{1,5}")) throw new IllegalArgumentException("Invalid listener port");
        int port = Integer.parseInt(value);
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid listener port");
        return port;
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing service configuration");
        return value;
    }

    @Override public String toString() { return "ServiceSettings[redacted]"; }
}
