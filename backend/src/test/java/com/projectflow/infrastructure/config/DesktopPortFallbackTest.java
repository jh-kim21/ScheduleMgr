package com.projectflow.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.mock.env.MockEnvironment;

import java.net.ServerSocket;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class DesktopPortFallbackTest {

    /** Logging is not started this early in real startup, so a no-op factory is enough here. */
    private static final DeferredLogFactory NO_OP_LOGS =
            (Supplier<org.apache.commons.logging.Log> supplier) -> new org.apache.commons.logging.impl.NoOpLog();

    private final DesktopPortFallback fallback = new DesktopPortFallback(NO_OP_LOGS);

    @Test
    @DisplayName("데스크톱 모드가 아니면 포트를 건드리지 않는다")
    void doesNothingWhenNotDesktop() throws Exception {
        try (ServerSocket taken = new ServerSocket(0)) {
            MockEnvironment environment = new MockEnvironment()
                    .withProperty("server.port", String.valueOf(taken.getLocalPort()));

            fallback.postProcessEnvironment(environment, null);

            // 호스팅 배포에서 포트가 조용히 바뀌면 주소가 어긋난다 — 크게 실패하는 것이 맞다.
            assertThat(environment.getPropertySources().contains("desktopPortFallback")).isFalse();
        }
    }

    @Test
    @DisplayName("포트가 비어 있으면 그대로 쓴다")
    void keepsFreePort() throws Exception {
        int free;
        try (ServerSocket probe = new ServerSocket(0)) {
            free = probe.getLocalPort();
        }
        MockEnvironment environment = new MockEnvironment()
                .withProperty("project-flow.desktop.enabled", "true")
                .withProperty("server.port", String.valueOf(free));

        fallback.postProcessEnvironment(environment, null);

        assertThat(environment.getPropertySources().contains("desktopPortFallback")).isFalse();
        assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(free);
    }

    @Test
    @DisplayName("포트가 사용 중이면 빈 포트로 바꾼다")
    void movesOffBusyPort() throws Exception {
        try (ServerSocket taken = new ServerSocket(0)) {
            int busy = taken.getLocalPort();
            MockEnvironment environment = new MockEnvironment()
                    .withProperty("project-flow.desktop.enabled", "true")
                    .withProperty("server.port", String.valueOf(busy));

            fallback.postProcessEnvironment(environment, null);

            Integer chosen = environment.getProperty("server.port", Integer.class);
            assertThat(environment.getPropertySources().contains("desktopPortFallback")).isTrue();
            assertThat(chosen).isNotNull().isNotEqualTo(busy).isPositive();
        }
    }

    @Test
    @DisplayName("포트 0은 이미 '빈 포트 아무거나'라 그대로 둔다")
    void leavesEphemeralPortAlone() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("project-flow.desktop.enabled", "true")
                .withProperty("server.port", "0");

        fallback.postProcessEnvironment(environment, null);

        assertThat(environment.getPropertySources().contains("desktopPortFallback")).isFalse();
    }
}
