package com.projectflow.infrastructure.config;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

/**
 * Moves the desktop build to a free port when its usual one is taken.
 *
 * <p>Without this, a busy port is the worst possible failure for the installed app: Tomcat cannot
 * bind, the context fails, and the process dies. There is no window, so nothing at all appears to
 * happen — the user double-clicks the icon and gets silence. Another server on 8080 is not an
 * unusual situation on a developer's machine.
 *
 * <p>Moving the port is safe here because nothing links to a fixed address: the app opens the
 * browser itself, at whatever port actually got bound
 * ({@link DesktopBrowserLauncher} reads {@code local.server.port}).
 *
 * <p>Runs as an {@link EnvironmentPostProcessor} because the decision has to be made before the web
 * server binds — by the time a bean could look at it, the failure has already happened.
 *
 * <p>Only active for the packaged desktop app ({@code project-flow.desktop.enabled}). A hosted
 * deployment must <em>not</em> silently move its port: there the port is part of the address people
 * and reverse proxies use, and failing loudly is correct.
 */
public class DesktopPortFallback implements EnvironmentPostProcessor {

    private static final String ENABLED_PROPERTY = "project-flow.desktop.enabled";
    private static final int DEFAULT_PORT = 8080;

    private final Log log;

    public DesktopPortFallback(DeferredLogFactory logFactory) {
        // Logging is not started this early; a deferred log is replayed once it is.
        this.log = logFactory.getLog(DesktopPortFallback.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty(ENABLED_PROPERTY, Boolean.class, false)) {
            return;
        }

        int configured = environment.getProperty("server.port", Integer.class, DEFAULT_PORT);
        // 0 already means "any free port", so there is nothing to rescue.
        if (configured == 0 || isFree(configured)) {
            return;
        }

        Integer replacement = findFreePort();
        if (replacement == null) {
            log.warn("포트 " + configured + "이 사용 중이고 대체 포트도 찾지 못했습니다.");
            return;
        }

        log.warn("포트 " + configured + "이 사용 중이므로 " + replacement + "번으로 실행합니다.");
        environment.getPropertySources().addFirst(new MapPropertySource(
                "desktopPortFallback", Map.of("server.port", replacement)));
    }

    /**
     * Binds the port for real rather than guessing. There is a small race between this check and
     * Tomcat's own bind, but the alternative — finding out from a failed startup — is what this
     * class exists to avoid.
     */
    private boolean isFree(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private Integer findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            return null;
        }
    }
}
