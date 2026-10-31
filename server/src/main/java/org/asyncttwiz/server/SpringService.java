package org.asyncttwiz.server;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.web.server.Shutdown;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.DefaultLifecycleProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.asyncttwiz.server.accounts.AccountHttp;
import org.asyncttwiz.server.accounts.PasswordResetService;
import org.asyncttwiz.server.accounts.ResetMailWorker;
import org.asyncttwiz.server.accounts.SmtpResetMailer;
import org.asyncttwiz.server.decks.DeckHttp;
import org.asyncttwiz.server.lobby.LobbyHttp;
import org.asyncttwiz.server.game.GameHub;
import org.asyncttwiz.server.game.GameHttp;
import org.asyncttwiz.server.activity.ActivityDelivery;
import org.asyncttwiz.server.activity.ActivityHttp;
import org.asyncttwiz.server.diagnostics.CrashReportHttp;
import org.asyncttwiz.server.activity.ActivityPushAdapter;

/** Explicit Spring Boot servlet contexts; no auto-configured database, migrations, Actuator or APIs. */
public final class SpringService implements AutoCloseable {
    private final ServiceSettings settings;
    private final ServiceRuntime runtime;
    private final PasswordResetService resets;
    private final ResetMailWorker mail;
    private final GameHub games;
    private final ActivityDelivery activity;
    private ConfigurableApplicationContext health;
    private ConfigurableApplicationContext publicApi;
    private boolean closed;

    public SpringService(ServiceSettings settings) {
        this(settings, org.asyncttwiz.server.activity.FcmServiceAccount.fromEnvironment());
    }

    /** Optional explicit adapter injection for embedded callers/tests.
     * The ordinary service performs maintenance without any provider credentials. */
    public SpringService(ServiceSettings settings, ActivityPushAdapter activityPush) {
        this.settings = settings;
        runtime = new ServiceRuntime(settings.database());
        resets = settings.resetMail() == null ? null : new PasswordResetService(runtime, settings.resetMail());
        mail = resets == null ? null : new ResetMailWorker(runtime, resets, new SmtpResetMailer(settings.resetMail()));
        if (mail != null) runtime.attachWorker(() -> resets.validatePending(), mail::available);
        games = new GameHub(runtime);
        runtime.attachWorker(() -> games.validatePending(), games::available);
        activity = new ActivityDelivery(runtime, activityPush, java.time.Clock.systemUTC());
    }

    public synchronized void start() {
        if (closed || health != null) throw new IllegalStateException("Service already started or closed");
        try {
            health = context(settings.healthAddress(), settings.healthPort(), true);
            publicApi = context(settings.publicAddress(), settings.publicPort(), false);
            if (mail != null) mail.start();
            games.start();
            activity.start();
            runtime.start();
        } catch (RuntimeException failure) { close(); throw failure; }
    }

    /** The public listener's actual port once started: the configured port, or the ephemeral one chosen for 0. */
    public synchronized int publicPort() { return boundPort(publicApi); }

    /** The health listener's actual port once started: the configured port, or the ephemeral one chosen for 0. */
    public synchronized int healthPort() { return boundPort(health); }

    private int boundPort(ConfigurableApplicationContext context) {
        if (closed || !(context instanceof WebServerApplicationContext web)) throw new IllegalStateException("Service not running");
        return web.getWebServer().getPort();
    }

    private ConfigurableApplicationContext context(InetAddress address, int port, boolean isHealth) {
        var application = new SpringApplication(ListenerConfiguration.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        application.setAddCommandLineProperties(false);
        var environment = new StandardEnvironment();
        // Runtime uses the explicit typed inputs, not arbitrary environment/system/application files.
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("fixed-service-settings", Map.of(
                "logging.config", "classpath:service-logback.xml",
                "spring.config.location", "optional:classpath:/service-fixed-config/",
                "spring.main.banner-mode", "off")));
        application.setEnvironment(environment);
        application.addInitializers(context -> {
            var factory = new TomcatServletWebServerFactory();
            factory.setAddress(address);
            factory.setPort(port);
            factory.setShutdown(Shutdown.GRACEFUL);
            if (!isHealth) factory.addContextCustomizers(tomcat -> tomcat.addServletContainerInitializer(
                    new org.apache.tomcat.websocket.server.WsSci(), java.util.Set.of()));
            factory.addConnectorCustomizers(connector -> {
                connector.setProperty("maxThreads", isHealth ? "8" : "32");
                connector.setProperty("minSpareThreads", "2");
                connector.setProperty("connectionTimeout", "5000");
                connector.setProperty("maxConnections", "128");
                connector.setProperty("acceptCount", "32");
                connector.setXpoweredBy(false);
            });
            context.getBeanFactory().registerSingleton("servletWebServerFactory", factory);
            context.getBeanFactory().registerSingleton("serviceServlet", new ServletRegistrationBean<>(
                    new Routes(runtime, isHealth, resets, settings, games), "/"));
        });
        return application.run();
    }

    @Configuration(proxyBeanMethods = false)
    public static class ListenerConfiguration {
        @Bean public DefaultLifecycleProcessor lifecycleProcessor() {
            var processor = new DefaultLifecycleProcessor();
            processor.setTimeoutPerShutdownPhase(5000);
            return processor;
        }
    }

    private static final class Routes extends HttpServlet {
        private final ServiceRuntime runtime;
        private final boolean health;
        private final AccountHttp accounts;
        private final DeckHttp decks;
        private final org.asyncttwiz.server.pods.PodHttp pods;
        private final LobbyHttp lobby;
        private final GameHttp games;
        private final ActivityHttp activity;
        private final CrashReportHttp crashReports;
        private final org.asyncttwiz.server.releases.AppReleaseHttp appReleases;
        private final org.asyncttwiz.server.diagnostics.ProblemReportHttp problemReports;
        Routes(ServiceRuntime runtime, boolean health, PasswordResetService resets, ServiceSettings settings, GameHub hub) {
            this.runtime = runtime;
            this.health = health;
            accounts = health ? null : new AccountHttp(runtime, resets, settings.resetMail());
            decks = health ? null : new DeckHttp(runtime);
            pods = health ? null : new org.asyncttwiz.server.pods.PodHttp(runtime);
            lobby = health ? null : new LobbyHttp(runtime);
            games = health ? null : new GameHttp(runtime, hub);
            activity = health ? null : new ActivityHttp(runtime, hub::presenceChanged);
            crashReports = health ? null : new CrashReportHttp(runtime, java.time.Clock.systemUTC());
            appReleases = health ? null : new org.asyncttwiz.server.releases.AppReleaseHttp();
            problemReports = health ? null : new org.asyncttwiz.server.diagnostics.ProblemReportHttp(runtime);
        }

        @Override protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
            if (!health && appReleases.handle(request, response)) return;
            if (!health && problemReports.handle(request, response)) return;
            if (!health && accounts.handle(request, response)) return;
            if (!health && decks.handle(request, response)) return;
            if (!health && pods.handle(request, response)) return;
            if (!health && games.handle(request, response)) return;
            if (!health && lobby.handle(request, response)) return;
            if (!health && activity.handle(request, response)) return;
            if (!health && crashReports.handle(request, response)) return;
            int status = 404;
            String body = "Not found\n";
            if (health && (request.getMethod().equals("GET") || request.getMethod().equals("HEAD"))) {
                if (request.getRequestURI().equals("/health/live")) { status = 200; body = "OK\n"; }
                else if (request.getRequestURI().equals("/health/ready")) {
                    status = runtime.ready() ? 200 : 503;
                    body = status == 200 ? "OK\n" : "Unavailable\n";
                }
            }
            // Future handlers also use runtime.transaction(), not a cached HTTP readiness value.
            // Unknown paths/methods never echo request inputs.
            response.setStatus(status);
            response.setContentType("text/plain;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            if (!request.getMethod().equals("HEAD")) response.getWriter().write(body);
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        runtime.close();
        activity.close();
        games.close();
        if (mail != null) mail.close();
        if (publicApi != null) publicApi.close();
        if (health != null) health.close();
    }
}
