package com.datarobort.ai.graph;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;

/**
 * Simple sequential graph executor with conditional routing and retry loops.
 * Pushes node lifecycle events to an optional listener (used for SSE streaming).
 */
@Slf4j
@Component
public class GraphExecutor {

    public interface GraphListener {
        void onNodeStart(String nodeName);
        void onNodeDone(String nodeName, long durationMs, String message);
        void onNodeFailed(String nodeName, String error);
        void onComplete(AgentState state);
    }

    /**
     * Run the graph for the given state.
     *
     * @param nodes       ordered list of (nodeName, node) pairs
     * @param state       initial state
     * @param listener    optional SSE callback (may be null)
     * @param maxSqlRetry maximum retries for SQL fix loop (node retries handled inside SqlGuardNode)
     * @return final state
     */
    public AgentState execute(List<Map.Entry<String, GraphNode>> nodes,
                               AgentState state,
                               GraphListener listener,
                               int maxSqlRetry) {
        for (Map.Entry<String, GraphNode> entry : nodes) {
            if (state.isFailed()) break;

            String name = entry.getKey();
            GraphNode node = entry.getValue();

            if (listener != null) listener.onNodeStart(name);
            long start = System.currentTimeMillis();
            try {
                state = node.execute(state);
                long dur = System.currentTimeMillis() - start;
                if (state.isFailed()) {
                    if (listener != null) listener.onNodeFailed(name, state.getErrorMessage());
                    log.warn("node {} failed: {}", name, state.getErrorMessage());
                } else {
                    AgentState.NodeTrace last = !state.getTraces().isEmpty()
                            ? state.getTraces().get(state.getTraces().size() - 1) : null;
                    String msg = last != null ? last.getMessage() : "ok";
                    if (listener != null) listener.onNodeDone(name, dur, msg);
                }
            } catch (Exception e) {
                log.error("node {} threw exception", name, e);
                state.setFailed(true);
                state.setErrorMessage(name + ": " + e.getMessage());
                if (listener != null) listener.onNodeFailed(name, e.getMessage());
            }
        }
        if (listener != null) listener.onComplete(state);
        return state;
    }
}
