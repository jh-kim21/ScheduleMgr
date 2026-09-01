package com.projectflow.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * CORS for {@code /api/**}, configured per profile through
 * {@code project-flow.cors.allowed-origin-patterns} (comma separated).
 *
 * <p>In development the frontend is served by Vite and the requests reach the backend through its
 * proxy, so this looks like a same-origin setup — but it is not, as far as the CORS filter is
 * concerned. Browsers attach an {@code Origin} header to every request that is not a GET, even a
 * same-origin one, and the Vite proxy forwards that header untouched. A hard-coded port therefore
 * fails the moment Vite picks a different one (which it does automatically when the default is
 * taken), and it fails in a confusing way: GETs carry no {@code Origin}, so the screen loads
 * normally and only saving breaks, with a bare 403.
 *
 * <p>Hence the pattern list rather than a fixed origin, and hence the split: the desktop profile
 * allows any loopback port because that is what a developer's machine looks like, while the server
 * profile allows nothing unless an operator names the origins. Leaving a loopback wildcard in a
 * deployed service would be worse than making the deployment state its frontend origin.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final List<String> allowedOriginPatterns;

    /**
     * Parsed here rather than bound straight to a {@code List<String>}: an unset or empty property
     * would otherwise arrive as a list holding one empty string, which is not the same thing as
     * "no origins allowed".
     */
    public WebConfig(@Value("${project-flow.cors.allowed-origin-patterns:}") String patterns) {
        this.allowedOriginPatterns = Arrays.stream(patterns.split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .toList();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOriginPatterns.isEmpty()) {
            // No mapping, which is what "no cross-origin access" looks like: the preflight for a
            // write gets a 403 and a GET response carries no Access-Control-Allow-Origin, so a
            // browser can neither send the one nor read the other. Requests that arrive without an
            // Origin header — the Vite proxy's own, server-to-server calls, curl — are untouched,
            // because CORS is a browser rule and never was server-side authorisation.
            return;
        }
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
