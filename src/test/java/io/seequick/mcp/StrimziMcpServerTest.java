package io.seequick.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrimziMcpServerTest {

    private PrintStream originalOut;
    private ByteArrayOutputStream captured;

    @BeforeEach
    void captureStdout() {
        originalOut = System.out;
        captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
    }

    @Test
    void withSuppressedStdoutShouldPreventOutputFromLeakingToStdout() {
        StrimziMcpServer.withSuppressedStdout(() -> {
            System.out.println("this should be suppressed");
            return null;
        });

        assertThat(captured.toString()).isEmpty();
    }

    @Test
    void withSuppressedStdoutShouldRestoreStdoutAfterAction() {
        StrimziMcpServer.withSuppressedStdout(() -> "ignored");

        // Writing after the call should reach our capture stream (stdout is restored)
        System.out.print("restored");
        assertThat(captured.toString()).isEqualTo("restored");
    }

    @Test
    void withSuppressedStdoutShouldRestoreStdoutEvenIfActionThrows() {
        assertThatThrownBy(() ->
                StrimziMcpServer.withSuppressedStdout(() -> {
                    throw new RuntimeException("boom");
                })
        ).isInstanceOf(RuntimeException.class);

        System.out.print("restored");
        assertThat(captured.toString()).isEqualTo("restored");
    }

    @Test
    void withSuppressedStdoutShouldReturnActionResult() {
        String result = StrimziMcpServer.withSuppressedStdout(() -> "expected-version");

        assertThat(result).isEqualTo("expected-version");
    }
}
