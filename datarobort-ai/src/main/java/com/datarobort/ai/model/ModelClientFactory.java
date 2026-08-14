package com.datarobort.ai.model;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds and caches Spring AI clients per model_config row.
 *
 * <p>Every client is constructed from the database config (base-url /
 * api-key / model-name), so any OpenAI-compatible provider (Qwen, DeepSeek,
 * self-hosted vLLM) works through the same code path. Cache entries are
 * evicted when the config changes.
 */
@Slf4j
@Component
public class ModelClientFactory {

    private static final String DIMENSION_PROBE_TEXT = "dimension-probe";

    private final Map<Long, ChatClient> chatCache = new ConcurrentHashMap<>();
    private final Map<Long, EmbeddingModel> embeddingCache = new ConcurrentHashMap<>();

    public ChatClient chatClient(Long id, String baseUrl, String apiKey, String modelName) {
        return chatCache.computeIfAbsent(id, k -> {
            OpenAiApi api = openAiApi(baseUrl, apiKey);
            // temperature=0：SQL 生成/意图识别需要确定性输出（评测复现稳定）
            OpenAiChatModel model = OpenAiChatModel.builder()
                    .openAiApi(api)
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(modelName)
                            .temperature(0.0)
                            .build())
                    .build();
            return ChatClient.builder(model).build();
        });
    }

    public EmbeddingModel embeddingModel(Long id, String baseUrl, String apiKey, String modelName) {
        return embeddingCache.computeIfAbsent(id, k -> new OpenAiEmbeddingModel(
                openAiApi(baseUrl, apiKey),
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(modelName).build()));
    }

    /**
     * Probes the output dimension of an embedding model with one real call.
     * Cached by {@code (baseUrl, apiKey, modelName)} to avoid leaking HTTP
     * clients on repeated create/update operations.
     *
     * @return vector length
     */
    public int probeEmbeddingDimension(String baseUrl, String apiKey, String modelName) {
        String cacheKey = baseUrl + "|" + (apiKey == null ? "" : apiKey) + "|" + modelName;
        EmbeddingModel model = probeCache.computeIfAbsent(cacheKey, k -> new OpenAiEmbeddingModel(
                openAiApi(baseUrl, apiKey),
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(modelName).build()));
        return model.embed(DIMENSION_PROBE_TEXT).length;
    }
    private final Map<String, EmbeddingModel> probeCache = new ConcurrentHashMap<>();

    /** Evicts cached clients for a model (after update / delete). */
    public void evict(Long id) {
        chatCache.remove(id);
        embeddingCache.remove(id);
        // Probe cache is bounded (N embedding configs). Clearing on any eviction keeps it fresh.
        probeCache.clear();
    }

    // Shared RestClient builder with 2-min timeout for reasoning models (Qwen 3.7 etc.)
    private static final RestClient.Builder REST_CLIENT_BUILDER;
    static {
        var httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        REST_CLIENT_BUILDER = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient));
    }

    private OpenAiApi openAiApi(String baseUrl, String apiKey) {
        // Spring AI's OpenAiApi appends /v1/chat/completions (or /v1/embeddings)
        // internally. Strip a trailing /v1 so we never produce /v1/v1/… paths.
        String normalized = baseUrl;
        if (normalized != null) {
            normalized = normalized.replaceAll("/v1/?$", "");
        }
        return OpenAiApi.builder()
                .baseUrl(normalized)
                .apiKey(apiKey == null ? "sk-none" : apiKey)
                .restClientBuilder(REST_CLIENT_BUILDER)
                .build();
    }
}
