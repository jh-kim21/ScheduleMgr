package com.projectflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

@SpringBootApplication
public class ProjectFlowApplication {

    /** Set by the desktop launchers via {@code -D}; see DesktopBrowserLauncher. */
    private static final String DESKTOP_PROPERTY = "project-flow.desktop.enabled";

    public static void main(String[] args) {
        try {
            SpringApplication.run(ProjectFlowApplication.class, args);
        } catch (RuntimeException | Error e) {
            // The desktop build has no console and no window, so a failed start is otherwise
            // completely silent — the user double-clicks the icon and nothing happens. The stack
            // trace still goes to the log; this only makes the failure visible.
            if (Boolean.getBoolean(DESKTOP_PROPERTY)) {
                showStartupFailure(e);
            }
            throw e;
        }
    }

    private static void showStartupFailure(Throwable failure) {
        String message = """
                ProjectFlow를 시작할 수 없습니다.

                %s

                이미 실행 중인 ProjectFlow가 있는지 확인해 보세요.
                문제가 계속되면 이 메시지를 그대로 알려주시면 도움이 됩니다.""".formatted(rootCauseMessage(failure));

        try {
            // Spring Boot keeps an explicitly set headless value, so the launchers pass
            // -Djava.awt.headless=false. If something else forced headless on, the dialog cannot
            // open — swallow that rather than replacing the real failure with a dialog failure.
            SwingUtilities.invokeAndWait(() -> JOptionPane.showMessageDialog(
                    null, message, "ProjectFlow 실행 실패", JOptionPane.ERROR_MESSAGE));
        } catch (Exception | Error ignored) {
            // The original exception is rethrown by the caller and is the useful one.
        }
    }

    private static String rootCauseMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message != null && !message.isBlank()
                ? message
                : cause.getClass().getSimpleName();
    }
}
