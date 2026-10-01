package ua.kpi.grader.testgen.client;

/**
 * One message of an LLM chat conversation.
 *
 * @param role    {@code system}, {@code user} or {@code assistant}
 * @param content message text
 */
public record ChatMessage(String role, String content) {

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }
}
