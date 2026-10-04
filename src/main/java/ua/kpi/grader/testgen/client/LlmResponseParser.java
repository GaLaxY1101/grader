package ua.kpi.grader.testgen.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses an LLM chat answer into the code fragment the self-repair loop evaluates.
 * Provider-agnostic: Ollama and Gemini both return free-form text that may wrap
 * the test file in a Markdown fenced block.
 */
public final class LlmResponseParser {

    /** First fenced block: ```lang\n ... ```; the language tag is optional. */
    private static final Pattern FENCED_BLOCK =
            Pattern.compile("```[\\w+#.-]*[ \\t]*\\r?\\n(.*?)```", Pattern.DOTALL);

    private LlmResponseParser() {
    }

    /**
     * Extracts the code from an LLM answer: the content of the first fenced code block
     * (with or without a language tag), or the whole content trimmed if there is no fence.
     *
     * @param content raw LLM answer
     * @return extracted code; empty string for {@code null} input
     */
    public static String extractCode(String content) {
        if (content == null) {
            return "";
        }
        Matcher matcher = FENCED_BLOCK.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).strip() + "\n";
        }
        return content.strip();
    }
}
