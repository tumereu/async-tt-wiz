package org.asyncttwiz.server;

import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.LogManager;
import org.asyncttwiz.server.database.MigrationCommand;

/** Same distribution exposes explicit migration and service commands, never automatic migration. */
public final class ServiceCommand {
    private ServiceCommand() {}

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("migrate")) { MigrationCommand.main(args); return; }
        if (args.length != 1 || !args[0].equals("serve")) {
            System.err.println("Usage: server serve|migrate");
            System.exit(2);
        }
        // Library startup/SQL exceptions may include raw URLs/credentials. Only fixed messages below
        // are emitted; logging configuration cannot be overridden through SPRING_* or CLI properties.
        LogManager.getLogManager().reset();
        java.util.logging.Logger.getLogger("").setLevel(Level.OFF);
        try {
            var settings = ServiceSettings.fromEnvironment(System.getenv());
            var service = new SpringService(settings);
            var stopped = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                service.close();
                stopped.countDown();
            }, "service-shutdown"));
            service.start();
            System.out.println("Service started; health is separate; readiness requires database writer ownership.");
            stopped.await();
        } catch (Exception failure) {
            System.err.println("Service failed; check configuration, listener availability and schema compatibility.");
            System.exit(1);
        }
    }
}
