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
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.DefaultLifecycleProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.asyncttwiz.server.accounts.AccountHttp;

/** Explicit Spring Boot servlet contexts; no auto-configured database, migrations, Actuator or APIs. */
public final class SpringService implements AutoCloseable {
    private final ServiceSettings settings;
    private final ServiceRuntime runtime;
    private ConfigurableApplicationContext health;
    private ConfigurableApplicationContext publicApi;
    private boolean closed;

    public SpringService(ServiceSettings settings) {
        this.settings = settings;
        runtime = new ServiceRuntime(settings.database());
    }

    public synchronized void start() {
        if (closed || health != null) throw new IllegalStateException("Service already started or closed");
        try {
            health = context(settings.healthAddress(), settings.healthPort(), true);
            publicApi = context(settings.publicAddress(), settings.publicPort(), false);
            runtime.start();
        } catch (RuntimeException failure) { close(); throw failure; }
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
                    new Routes(runtime, isHealth), "/"));
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
        Routes(ServiceRuntime runtime, boolean health) {
            this.runtime = runtime;
            this.health = health;
            accounts = health ? null : new AccountHttp(runtime);
        }

        @Override protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
            if (!health && accounts.handle(request, response)) return;
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
        if (publicApi != null) publicApi.close();
        if (health != null) health.close();
    }
}
