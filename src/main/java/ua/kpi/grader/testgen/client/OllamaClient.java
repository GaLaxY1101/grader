package ua.kpi.grader.testgen.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

/**
 * Minimal client for the Ollama chat API ({@code POST /api/chat}, non-streaming).
 * Registered only when {@code testgen.provider = ollama}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "testgen.provider", havingValue = "ollama")
public class OllamaClient implements LlmClient {

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
     * {@inheritDoc}
     *
     * @throws LlmUnavailableException if Ollama is unreachable, times out or returns an error
     */
    @Override
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
