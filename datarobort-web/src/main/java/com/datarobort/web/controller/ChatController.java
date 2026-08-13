package com.datarobort.web.controller;

import com.datarobort.ai.graph.AgentState;
import com.datarobort.ai.graph.GraphExecutor;
import com.datarobort.ai.graph.GraphNode;
import com.datarobort.web.agent.*;
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
import java.util.*;

/**
 * Main chat API with SSE streaming.
 * Assembles the agent graph and runs it on each user message.
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final IntentNode intentNode;
    private final RecallNode recallNode;
    private final SqlGenNode sqlGenNode;
    private final SqlGuardNode sqlGuardNode;
    private final SqlExecNode sqlExecNode;
    private final PythonNode pythonNode;
    private final ChartNode chartNode;
    private final ReportNode reportNode;
    private final GraphExecutor graphExecutor;
    private final ObjectMapper objectMapper;

    public ChatController(IntentNode intentNode, RecallNode recallNode,
                          SqlGenNode sqlGenNode, SqlGuardNode sqlGuardNode,
                          SqlExecNode sqlExecNode, PythonNode pythonNode,
                          ChartNode chartNode, ReportNode reportNode,
                          GraphExecutor graphExecutor, ObjectMapper objectMapper) {
        this.intentNode = intentNode;
        this.recallNode = recallNode;
        this.sqlGenNode = sqlGenNode;
        this.sqlGuardNode = sqlGuardNode;
        this.sqlExecNode = sqlExecNode;
        this.pythonNode = pythonNode;
        this.chartNode = chartNode;
        this.reportNode = reportNode;
        this.graphExecutor = graphExecutor;
        this.objectMapper = objectMapper;
    }

    /** Plain JSON response — no SSE parsing needed on the frontend. */
    @PostMapping
    public Mono<Map<String, Object>> chat(@RequestBody Map<String, String> body) {
        String question = body.getOrDefault("question", "");
        if (question.isBlank()) {
            return Mono.just(Map.of("error", "question is required"));
        }
        AgentState state = new AgentState();
        state.setUserQuestion(question);
        List<Map.Entry<String, GraphNode>> nodes = buildGraph(state);
        return Mono.fromCallable(() -> {
            AgentState fs = graphExecutor.execute(nodes, state, null, 3);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("intent", fs.getIntent());
            result.put("sql", fs.getGeneratedSql());
            result.put("rowCount", fs.getRowCount());
            result.put("chartOption", fs.getChartOption());
            result.put("markdownReport", fs.getMarkdownReport());
            result.put("traces", fs.getTraces());
            result.put("failed", fs.isFailed());
            result.put("errorMessage", fs.getErrorMessage());
            return result;
        });
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody Map<String, String> body,
                                                     ServerHttpResponse response) {
        // Prevent proxy/CDN buffering of the SSE stream
        response.getHeaders().set("X-Accel-Buffering", "no");
        response.getHeaders().set("Cache-Control", "no-cache, no-transform");

        String question = body.getOrDefault("question", "");
        if (question.isBlank()) {
            return Flux.just(buildEvent("error", "{\"error\":\"question is required\"}"));
        }

        AgentState state = new AgentState();
        state.setUserQuestion(question);
        List<Map.Entry<String, GraphNode>> nodes = buildGraph(state);

        return Flux.<ServerSentEvent<String>>create(sink -> {
            // Signal that the SSE connection is established
            sink.next(buildEvent("connected", "{\"status\":\"ok\"}"));

            Thread graphThread = new Thread(() -> {
                try {
                    graphExecutor.execute(nodes, state, new GraphExecutor.GraphListener() {
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
                                Map<String, Object> r = new LinkedHashMap<>();
                                r.put("intent", fs.getIntent()); r.put("sql", fs.getGeneratedSql());
                                r.put("rowCount", fs.getRowCount()); r.put("chartOption", fs.getChartOption());
                                r.put("markdownReport", fs.getMarkdownReport()); r.put("traces", fs.getTraces());
                                r.put("failed", fs.isFailed()); r.put("errorMessage", fs.getErrorMessage());
                                r.put("reportFileUrl", fs.getReportFileUrl());
                                sink.next(buildEvent("complete", objectMapper.writeValueAsString(r)));
                            } catch (Exception e) {
                                log.error("failed to serialize complete event", e);
                                sink.next(buildEvent("error", "{\"error\":\"" + esc(e.getMessage()) + "\"}"));
                            }
                            sink.complete();
                        }
                    }, 3);
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

    private List<Map.Entry<String, GraphNode>> buildGraph(AgentState state) {
        List<Map.Entry<String, GraphNode>> nodes = new ArrayList<>();
        nodes.add(Map.entry("intent", intentNode));
        nodes.add(Map.entry("recall", recallNode));
        nodes.add(Map.entry("sql-gen", sqlGenNode));
        nodes.add(Map.entry("sql-guard", sqlGuardNode));
        nodes.add(Map.entry("sql-exec", sqlExecNode));
        nodes.add(Map.entry("python", pythonNode));
        nodes.add(Map.entry("chart", chartNode));
        nodes.add(Map.entry("report", reportNode));
        return nodes;
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
