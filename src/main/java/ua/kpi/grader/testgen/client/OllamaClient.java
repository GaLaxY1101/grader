package ua.kpi.grader.testgen.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ua.kpi.grader.testgen.config.TestGenProperties;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal client for the Ollama chat API ({@code POST /api/chat}, non-streaming).
 */
@Slf4j
@Component
public class OllamaClient {

    /** First fenced block: ```lang\n ... ```; the language tag is optional. */
    private static final Pattern FENCED_BLOCK =
            Pattern.compile("```[\\w+#.-]*[ \\t]*\\r?\\n(.*?)```", Pattern.DOTALL);

    private final RestClient restClient;
    private final TestGenProperties.Ollama properties;

    public OllamaClient(TestGenProperties testGenProperties) {
        this.properties = testGenProperties.ollama();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Sends a chat conversation to the configured model and returns the assistant reply.
     *
     * @param messages    conversation so far (system, user and assistant turns)
     * @param temperature sampling temperature
     * @param seed        sampling seed for reproducibility; null lets the server choose
     * @return assistant reply with token counts and duration
     * @throws LlmUnavailableException if Ollama is unreachable, times out or returns an error
     */
    public LlmResponse chat(List<ChatMessage> messages, double temperature, Integer seed) {
        return chat(properties.model(), messages, temperature, seed);
    }

    /**
     * Same as {@link #chat(List, double, Integer)} but with an explicit model tag.
     *
     * @param model model tag overriding the configured default
     * @throws LlmUnavailableException if Ollama is unreachable, times out or returns an error
     */
    public LlmResponse chat(String model, List<ChatMessage> messages, double temperature, Integer seed) {
        ChatRequest request = new ChatRequest(model, messages, false, properties.keepAlive(),
                new Options(temperature, seed, properties.numCtx() > 0 ? properties.numCtx() : null));
        long start = System.nanoTime();
        ChatResponse response;
        try {
            response = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (ResourceAccessException e) {
            throw new LlmUnavailableException(
                    "LLM server not reachable at " + properties.baseUrl() + ": " + e.getMessage(), e);
        } catch (RestClientResponseException e) {
            throw new LlmUnavailableException(
                    "LLM server returned " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(), e);
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        if (response == null || response.message() == null) {
            throw new LlmUnavailableException("LLM server returned an empty response", null);
        }
        log.debug("Ollama {} answered in {} ms (prompt {} tok, completion {} tok)",
                model, durationMs, response.promptEvalCount(), response.evalCount());
        return new LlmResponse(response.message().content(), response.model(),
                response.promptEvalCount(), response.evalCount(), durationMs);
    }

    /**
     * Extracts the code from an LLM answer: the content of the first fenced code block
     * (with or without a language tag), or the whole content trimmed if there is no fence.
     *
     * @param content raw LLM answer
     * @return extracted code; empty string for null input
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ChatRequest(
            String model,
            List<ChatMessage> messages,
            boolean stream,
            @JsonProperty("keep_alive") String keepAlive,
            Options options
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Options(
            double temperature,
            Integer seed,
            @JsonProperty("num_ctx") Integer numCtx
    ) {}

    private record ChatResponse(
            String model,
            ChatMessage message,
            @JsonProperty("prompt_eval_count") Integer promptEvalCount,
            @JsonProperty("eval_count") Integer evalCount
    ) {}
}
