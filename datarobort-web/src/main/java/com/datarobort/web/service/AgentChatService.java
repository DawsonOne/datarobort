package com.datarobort.web.service;

import com.datarobort.ai.graph.AgentState;
import com.datarobort.ai.graph.GraphExecutor;
import com.datarobort.ai.graph.GraphNode;
import com.datarobort.common.exception.BizException;
import com.datarobort.core.entity.Conversation;
import com.datarobort.core.entity.Message;
import com.datarobort.web.agent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared chat runtime: assembles the agent graph, prepares the state
 * (agent bindings + conversation history), runs the pipeline and
 * persists messages. Used by both the SSE chat endpoint and the MCP tools,
 * so the two entry points behave identically.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentChatService {

    private static final int MAX_SQL_RETRY = 3;

    private final IntentNode intentNode;
    private final RecallNode recallNode;
    private final SqlGenNode sqlGenNode;
    private final SqlGuardNode sqlGuardNode;
    private final SqlExecNode sqlExecNode;
    private final PythonNode pythonNode;
    private final ChartNode chartNode;
    private final ReportNode reportNode;
    private final GraphExecutor graphExecutor;
    private final AgentRuntimeFactory agentRuntimeFactory;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    /** The ordered node list that forms the analysis pipeline. */
    public List<Map.Entry<String, GraphNode>> buildGraph() {
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

    /**
     * Prepare the state for one user question: load the agent binding,
     * resolve the conversation (multi-turn history), and persist the
     * user message when a conversation is bound.
     */
    public AgentState prepareState(String question, Long agentId, Long conversationId) {
        AgentState state = new AgentState();
        state.setUserQuestion(question);

        // Resolve the agent from the conversation. detail() also validates
        // that the conversation exists. When the conversation is bound to an
        // agent, that binding is authoritative over the request's agentId.
        if (conversationId != null) {
            Conversation c = conversationService.detail(conversationId);
            if (c.getAgentId() != null) {
                agentId = c.getAgentId();
            }
        }
        agentRuntimeFactory.applyToState(state, agentId);
        state.setConversationId(conversationId);

        // Multi-turn context window
        String history = conversationService.buildHistoryContext(conversationId);
        if (!history.isEmpty()) {
            state.getAgentConfig().put("history", history);
        }

        // Persist the user message before running the pipeline
        if (conversationId != null) {
            try {
                Message user = new Message();
                user.setConversationId(conversationId);
                user.setRole(Message.ROLE_USER);
                user.setContent(question);
                conversationService.saveMessage(user);
            } catch (Exception e) {
                log.warn("failed to persist user message: {}", e.getMessage());
            }
        }
        return state;
    }

    /** Run the full pipeline synchronously. */
    public AgentState run(AgentState state) {
        return graphExecutor.execute(buildGraph(), state, null, MAX_SQL_RETRY);
    }

    /** Run the full pipeline with an SSE listener. */
    public AgentState run(AgentState state, GraphExecutor.GraphListener listener) {
        return graphExecutor.execute(buildGraph(), state, listener, MAX_SQL_RETRY);
    }

    /**
     * Persist the assistant message after the pipeline finishes.
     * Failures are logged and swallowed — persistence must never break the stream.
     */
    public void persistAssistantMessage(AgentState state) {
        if (state.getConversationId() == null) return;
        try {
            Message assistant = new Message();
            assistant.setConversationId(state.getConversationId());
            assistant.setRole(Message.ROLE_ASSISTANT);
            assistant.setContent(state.getMarkdownReport() != null
                    ? state.getMarkdownReport()
                    : (state.getErrorMessage() != null ? state.getErrorMessage() : ""));
            assistant.setSqlText(state.getGeneratedSql());
            assistant.setMarkdownReport(state.getMarkdownReport());
            assistant.setReportFileUrl(state.getReportFileUrl());
            assistant.setNodeTraces(objectMapper.writeValueAsString(state.getTraces()));
            conversationService.saveMessage(assistant);
        } catch (Exception e) {
            log.warn("failed to persist assistant message: {}", e.getMessage());
        }
    }

    /** Serialize the final state into the map sent in the SSE complete event. */
    public Map<String, Object> resultMap(AgentState fs) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("intent", fs.getIntent());
        r.put("sql", fs.getGeneratedSql());
        r.put("rowCount", fs.getRowCount());
        r.put("chartOption", fs.getChartOption());
        r.put("markdownReport", fs.getMarkdownReport());
        r.put("traces", fs.getTraces());
        r.put("failed", fs.isFailed());
        r.put("errorMessage", fs.getErrorMessage());
        r.put("reportFileUrl", fs.getReportFileUrl());
        r.put("conversationId", fs.getConversationId());
        r.put("agentId", fs.getAgentId());
        return r;
    }

    /** Extract a nullable Long from a JSON body value. */
    public Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw new BizException(com.datarobort.common.error.ErrorCode.PARAM_INVALID,
                    "非法 ID: " + v);
        }
    }
}
