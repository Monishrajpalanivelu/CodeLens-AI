package com.codelens.ai.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Custom EmbeddingModel implementation calling the Gemini REST API directly
 * (not Vertex AI — no GCP service account needed, just an API key).
 *
 * Implementing Spring AI's EmbeddingModel interface means PgVectorStore,
 * and any future search code, can use this through the standard interface
 * without knowing it's Gemini under the hood. Swapping providers later
 * means writing a new class, not touching business logic.
 */
@Service
@Slf4j
public class GeminiEmbeddingService implements EmbeddingModel {

    private final RestClient restClient;
    private final String model;

    // Gemini's text-embedding-004 has two task types that change how the
    // vector space is optimized. Using the wrong one degrades search recall.
    private enum TaskType {
        RETRIEVAL_DOCUMENT, // used when indexing code into the vector store
        RETRIEVAL_QUERY     // used when embedding a user's search query
    }

    public GeminiEmbeddingService(
            @Value("${gemini.api.key}") String apiKey,
            @Value("${gemini.api.base-url}") String baseUrl,
            @Value("${gemini.api.model}") String model) {
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
    }

    /**
     * Called automatically by PgVectorStore when indexing Documents.
     * Always uses RETRIEVAL_DOCUMENT task type.
     */
    public static class GeminiClientException extends RuntimeException {
        public GeminiClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @Override
    @Retryable(
            retryFor = RuntimeException.class,
            noRetryFor = GeminiClientException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> inputs = request.getInstructions();

        for (int i = 0; i < inputs.size(); i++) {
            float[] vector = embedInternal(inputs.get(i), TaskType.RETRIEVAL_DOCUMENT);
            embeddings.add(new Embedding(vector, i));
        }
        return new EmbeddingResponse(embeddings);
    }

    /**
     * Use this for search queries — NOT call(). RETRIEVAL_QUERY optimizes
     * the vector space for query-time matching against indexed documents.
     */
    @Retryable(
            retryFor = RuntimeException.class,
            noRetryFor = GeminiClientException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public float[] embedQuery(String query) {
        return embedInternal(query, TaskType.RETRIEVAL_QUERY);
    }

    @Override
    public float[] embed(Document document) {
        return embedInternal(document.getText(), TaskType.RETRIEVAL_DOCUMENT);
    }

    private float[] embedInternal(String text, TaskType taskType) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "content", Map.of("parts", List.of(Map.of("text", text))),
                    "taskType", taskType.name(),
                    "outputDimensionality", 768
            );

            GeminiEmbedResponse response = restClient.post()
                    .uri("/" + model + ":embedContent")
                    .body(requestBody)
                    .retrieve()
                    .body(GeminiEmbedResponse.class);

            if (response == null || response.embedding() == null) {
                throw new RuntimeException("Empty embedding response from Gemini");
            }
            return response.embedding().values();

        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.warn("Gemini embedding call failed with status {}: {}", e.getStatusCode(), e.getMessage());
            if (e.getStatusCode() != null && e.getStatusCode().is4xxClientError()) {
                throw new GeminiClientException("Gemini embedding client error (e.g. token limits/auth): " + e.getMessage(), e);
            }
            throw new RuntimeException("Gemini embedding failed", e);
        } catch (Exception e) {
            log.warn("Gemini embedding call failed: {}", e.getMessage());
            throw new RuntimeException("Gemini embedding failed", e);
        }
    }

    // --- Response DTOs matching Gemini's JSON shape ---
    private record GeminiEmbedResponse(GeminiEmbeddingValues embedding) {}
    private record GeminiEmbeddingValues(float[] values) {}
}