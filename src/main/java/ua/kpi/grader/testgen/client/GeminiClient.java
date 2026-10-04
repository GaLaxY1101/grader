package ua.kpi.grader.testgen.client;

import com.fasterxml.jackson.annotation.JsonInclude;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Client for the Google Gemini chat API
 * ({@code POST /v1beta/models/{model}:generateContent}, non-streaming).
 * Registered when {@code testgen.provider = gemini}.
 *
 * <p>Maps the generic {@link ChatMessage} list to Gemini's shape:
 * <ul>
 *   <li>a leading {@code system} message becomes {@code systemInstruction};
 *   <li>{@code user} and {@code assistant} messages become {@code contents} with
 *       roles {@code user} and {@code model} respectively.
 * </ul>
 *
 * <p>Gemini has no native {@code seed} parameter; the self-repair loop still
 * drives sampling diversity via the temperature stagnation heuristic.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "testgen.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiClient implements LlmClient {

    private final RestClient restClient;
    private final TestGenProperties.Gemini properties;

    public GeminiClient(TestGenProperties testGenProperties) {
        this.properties = testGenProperties.gemini();
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY is required when testgen.provider = gemini");
        }
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

    @Override
    public LlmResponse chat(String model, List<ChatMessage> messages, double temperature, Integer seed) {
        if (seed != null) {
            log.debug("Gemini has no native seed parameter; ignoring seed={} for model {}", seed, model);
        }
        GenerateRequest request = buildRequest(messages, temperature);
        long start = System.nanoTime();
        GenerateResponse response;
        try {
            response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent?key={key}", model, properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(GenerateResponse.class);
        } catch (ResourceAccessException e) {
            throw new LlmUnavailableException(
                    "Gemini API not reachable at " + properties.baseUrl() + ": " + e.getMessage(), e);
        } catch (RestClientResponseException e) {
            throw new LlmUnavailableException(
                    "Gemini API returned " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(), e);
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new LlmUnavailableException("Gemini API returned no candidates", null);
        }
        Candidate candidate = response.candidates().getFirst();
        String finishReason = candidate.finishReason();
        if (finishReason != null && !"STOP".equals(finishReason) && !"MAX_TOKENS".equals(finishReason)) {
            throw new LlmUnavailableException(
                    "Gemini stopped with finishReason=" + finishReason, null);
        }
        String content = extractText(candidate);
        if (content == null) {
            throw new LlmUnavailableException("Gemini API returned an empty candidate", null);
        }
        Integer promptTokens = response.usageMetadata() != null
                ? response.usageMetadata().promptTokenCount() : null;
        Integer completionTokens = response.usageMetadata() != null
                ? response.usageMetadata().candidatesTokenCount() : null;
        log.debug("Gemini {} answered in {} ms (prompt {} tok, completion {} tok, finish {})",
                model, durationMs, promptTokens, completionTokens, finishReason);
        return new LlmResponse(content, model, promptTokens, completionTokens, durationMs);
    }

    private GenerateRequest buildRequest(List<ChatMessage> messages, double temperature) {
        Content systemInstruction = null;
        List<Content> contents = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            Part part = new Part(message.content());
            if ("system".equals(message.role())) {
                systemInstruction = new Content(null, List.of(part));
            } else {
                contents.add(new Content(mapRole(message.role()), List.of(part)));
            }
        }
        ThinkingConfig thinkingConfig = properties.thinkingBudget() >= 0
                ? new ThinkingConfig(properties.thinkingBudget()) : null;
        GenerationConfig generationConfig = new GenerationConfig(
                temperature, properties.maxOutputTokens(), thinkingConfig);
        return new GenerateRequest(systemInstruction, contents, generationConfig);
    }

    private static String mapRole(String role) {
        return "assistant".equals(role) ? "model" : "user";
    }

    private static String extractText(Candidate candidate) {
        if (candidate.content() == null || candidate.content().parts() == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Part part : candidate.content().parts()) {
            if (part.text() != null) {
                sb.append(part.text());
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record GenerateRequest(
            Content systemInstruction,
            List<Content> contents,
            GenerationConfig generationConfig
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Content(String role, List<Part> parts) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Part(String text) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record GenerationConfig(
            Double temperature,
            Integer maxOutputTokens,
            ThinkingConfig thinkingConfig
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ThinkingConfig(Integer thinkingBudget) {}

    private record GenerateResponse(
            List<Candidate> candidates,
            UsageMetadata usageMetadata
    ) {}

    private record Candidate(Content content, String finishReason) {}

    private record UsageMetadata(
            Integer promptTokenCount,
            Integer candidatesTokenCount,
            Integer totalTokenCount
    ) {}
}
