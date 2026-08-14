package com.datarobort.web.controller;

import com.datarobort.ai.graph.AgentState;
import com.datarobort.web.service.AgentChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Main chat API with SSE streaming.
 * Request body: {question, agentId?, conversationId?}
 * When agentId is present the pipeline runs with the agent's bound
 * datasources / knowledge / prompt; when conversationId is present the
 * multi-turn context is injected and messages are persisted.
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AgentChatService chatService;
    private final ObjectMapper objectMapper;

    public ChatController(AgentChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    /** Plain JSON response — no SSE parsing needed on the frontend. */
    @PostMapping
    public Mono<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        String question = (String) body.getOrDefault("question", "");
        if (question.isBlank()) {
            return Mono.just(Map.of("error", "question is required"));
        }
        Long agentId = chatService.toLong(body.get("agentId"));
        Long conversationId = chatService.toLong(body.get("conversationId"));
        return Mono.fromCallable(() -> {
            AgentState state = chatService.prepareState(question, agentId, conversationId);
            AgentState fs = chatService.run(state);
            chatService.persistAssistantMessage(fs);
            return chatService.resultMap(fs);
        });
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody Map<String, Object> body,
                                                     ServerHttpResponse response) {
        // Prevent proxy/CDN buffering of the SSE stream
        response.getHeaders().set("X-Accel-Buffering", "no");
        response.getHeaders().set("Cache-Control", "no-cache, no-transform");

        String question = (String) body.getOrDefault("question", "");
        if (question.isBlank()) {
            return Flux.just(buildEvent("error", "{\"error\":\"question is required\"}"));
        }

        // State preparation (agent binding + history) happens here so that
        // invalid agentId / conversationId errors surface as an SSE error event.
        final AgentState state;
        try {
            Long agentId = chatService.toLong(body.get("agentId"));
            Long conversationId = chatService.toLong(body.get("conversationId"));
            state = chatService.prepareState(question, agentId, conversationId);
        } catch (Exception e) {
            log.warn("prepareState failed: {}", e.getMessage());
            return Flux.just(buildEvent("error", "{\"error\":\"" + esc(e.getMessage()) + "\"}"));
        }

        return Flux.<ServerSentEvent<String>>create(sink -> {
            // Signal that the SSE connection is established
            sink.next(buildEvent("connected", "{\"status\":\"ok\"}"));

            Thread graphThread = new Thread(() -> {
                try {
                    chatService.run(state, new com.datarobort.ai.graph.GraphExecutor.GraphListener() {
                        @Override public void onNodeStart(String name) {
                            sink.next(buildEvent("node-start", "{\"node\":\"" + name + "\"}"));
                        }
                        @Override public void onNodeDone(String name, long dur, String msg) {
                            sink.next(buildEvent("node-done",
                                    "{\"node\":\"" + name + "\",\"durationMs\":" + dur + ",\"message\":\"" + esc(msg) + "\"}"));
                        }
                        @Override public void onNodeFailed(String name, String err) {
                            sink.next(buildEvent("node-failed",
                                    "{\"node\":\"" + name + "\",\"error\":\"" + esc(err) + "\"}"));
                        }
                        @Override public void onComplete(AgentState fs) {
                            try {
                                chatService.persistAssistantMessage(fs);
                                sink.next(buildEvent("complete", objectMapper.writeValueAsString(chatService.resultMap(fs))));
                            } catch (Exception e) {
                                log.error("failed to serialize complete event", e);
                                sink.next(buildEvent("error", "{\"error\":\"" + esc(e.getMessage()) + "\"}"));
                            }
                            sink.complete();
                        }
                    });
                } catch (Exception e) {
                    log.error("graph execution failed", e);
                    sink.next(buildEvent("error", "{\"error\":\"" + esc(e.getMessage()) + "\"}"));
                    sink.complete();
                }
            });
            graphThread.setDaemon(true);
            graphThread.setName("graph-exec-" + System.currentTimeMillis());
            graphThread.start();

            sink.onCancel(() -> {
                log.info("SSE stream cancelled by client");
                if (graphThread.isAlive()) {
                    graphThread.interrupt();
                }
            });
        }, FluxSink.OverflowStrategy.BUFFER)
        .timeout(Duration.ofMinutes(5));
    }

    private ServerSentEvent<String> buildEvent(String event, String data) {
        return ServerSentEvent.<String>builder()
                .event(event)
                .data(data)
                .build();
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
