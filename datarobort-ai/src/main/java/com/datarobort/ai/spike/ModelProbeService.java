package com.datarobort.ai.spike;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Spike A: verifies connectivity with an OpenAI-compatible chat model
 * (Qwen via DashScope compatible mode, DeepSeek, or a self-hosted gateway)
 * through Spring AI's ChatClient.
 *
 * <p>The endpoint is switched purely by configuration
 * ({code spring.ai.openai.base-url / api-key / chat.options.model}),
 * proving the "one codebase, any OpenAI-compatible provider" design.
 */
@Service
public class ModelProbeService {

    private final ChatClient chatClient;

    public ModelProbeService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /** Blocking call, used for connectivity test. */
    public String chat(String message) {
        return chatClient.prompt().user(message).call().content();
    }

    /** Streaming call, the way production chat will work over SSE. */
    public Flux<String> chatStream(String message) {
        return chatClient.prompt().user(message).stream().content();
    }
}
