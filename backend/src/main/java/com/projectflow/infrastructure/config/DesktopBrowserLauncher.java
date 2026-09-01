package com.projectflow.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Opens the app in the default browser once the server is up.
 *
 * <p>The desktop build is a Spring Boot server with the built frontend inside it, so launching the
 * installed application would otherwise start a process with nothing on screen — it would look
 * broken. This is the piece that makes the installer produce something usable.
 *
 * <p>Off unless {@code project-flow.desktop.enabled} is set, which the desktop launchers turn on
 * through {@code -D}. Defaulting to off keeps {@code ./gradlew bootRun} from opening a tab on every
 * restart during development. The same flag drives {@link DesktopPortFallback}.
 *
 * <p>The platform's own opener command is used rather than {@code java.awt.Desktop}: Spring Boot
 * runs headless by default, and {@code Desktop} throws there. A shell-out also keeps AWT out of a
 * process that has no other use for it.
 */
@Component
public class DesktopBrowserLauncher {

    private static final Logger log = LoggerFactory.getLogger(DesktopBrowserLauncher.class);

    private final boolean enabled;
    private final String host;

    public DesktopBrowserLauncher(
            @Value("${project-flow.desktop.enabled:false}") boolean enabled,
            @Value("${project-flow.desktop.host:localhost}") String host) {
        this.enabled = enabled;
        this.host = host;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void openBrowser(ApplicationReadyEvent event) {
        if (!enabled) {
            return;
        }

        String url = "http://%s:%d".formatted(host, resolvePort(event));
        List<String> command = openerFor(url);
        if (command == null) {
            log.info("이 플랫폼에서는 브라우저를 자동으로 열 수 없습니다. 직접 접속하세요: {}", url);
            return;
        }

        try {
            new ProcessBuilder(command).start();
            log.info("브라우저를 열었습니다: {}", url);
        } catch (IOException e) {
            // Not being able to open a browser must not take the server down — the user can still
            // navigate there by hand, so the URL is the useful part of this message.
            log.warn("브라우저를 열지 못했습니다. 직접 접속하세요: {} ({})", url, e.getMessage());
        }
    }

    /** The port actually bound, which may differ from the configured one when 0 was requested. */
    private int resolvePort(ApplicationReadyEvent event) {
        String port = event.getApplicationContext().getEnvironment().getProperty("local.server.port");
        if (port == null) {
            port = event.getApplicationContext().getEnvironment().getProperty("server.port", "8080");
        }
        return Integer.parseInt(port);
    }

    private List<String> openerFor(String url) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            return List.of("open", url);
        }
        if (os.contains("win")) {
            return List.of("rundll32", "url.dll,FileProtocolHandler", url);
        }
        if (os.contains("nux") || os.contains("nix")) {
            return List.of("xdg-open", url);
        }
        return null;
    }
}
