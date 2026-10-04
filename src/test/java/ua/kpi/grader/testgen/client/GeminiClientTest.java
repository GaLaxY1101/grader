package ua.kpi.grader.testgen.client;

import org.junit.jupiter.api.Test;
import ua.kpi.grader.testgen.config.TestGenProperties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiClientTest {

    @Test
    void constructor_failsFast_whenApiKeyIsBlank() {
        TestGenProperties properties = propertiesWithKey("");

        assertThatThrownBy(() -> new GeminiClient(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GEMINI_API_KEY");
    }

    @Test
    void constructor_failsFast_whenApiKeyIsNull() {
        TestGenProperties properties = propertiesWithKey(null);

        assertThatThrownBy(() -> new GeminiClient(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GEMINI_API_KEY");
    }

    @Test
    void constructor_succeeds_whenApiKeyIsPresent() {
        TestGenProperties properties = propertiesWithKey("AIzaSy-test-key");

        new GeminiClient(properties); // must not throw
    }

    private static TestGenProperties propertiesWithKey(String apiKey) {
        return new TestGenProperties(
                TestGenProperties.Provider.GEMINI,
                new TestGenProperties.Ollama("http://localhost:11434", "qwen2.5-coder:3b", 180, "30m", 8192),
                new TestGenProperties.Gemini("https://generativelanguage.googleapis.com",
                        apiKey, "gemini-2.0-flash", 60, 8192, 0),
                0.2, 3, 5, true, 10, true,
                new TestGenProperties.Sandbox("cpp:1", "py:1", 20, 5, "256m", "1"));
    }
}
