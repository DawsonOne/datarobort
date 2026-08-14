package com.datarobort.web.agent;

import com.datarobort.ai.graph.AgentState;
import com.datarobort.ai.graph.GraphNode;
import com.datarobort.web.service.RecallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls RecallService to retrieve knowledge base chunks, business terms,
 * and semantic model mappings for the user's question.
 * The recall scope (knowledge bases + switches) follows the agent binding.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecallNode implements GraphNode {

    private final RecallService recallService;

    @Override
    public AgentState execute(AgentState state) {
        if (!state.isIntent("query", "analyze", "report")) {
            state.addTrace("recall", "done", 0, "skipped (intent=" + state.getIntent() + ")");
            return state;
        }

        long start = System.currentTimeMillis();
        try {
            Map<String, Object> cfg = state.getAgentConfig();
            List<Long> kbIds = toLongList(cfg.get("kbIds"));
            Boolean businessEnabled = (Boolean) cfg.get("businessRecallEnabled");
            Boolean semanticEnabled = (Boolean) cfg.get("semanticRecallEnabled");

            List<RecallService.RecallItem> items =
                    recallService.recall(state.getUserQuestion(), 5, 0.2, kbIds, businessEnabled, semanticEnabled);
            StringBuilder sb = new StringBuilder();
            for (RecallService.RecallItem item : items) {
                sb.append("[").append(item.getSourceType()).append("] ");
                sb.append(item.getSourceTitle() != null ? item.getSourceTitle() : "");
                if (item.getContent() != null) sb.append(" → ").append(item.getContent());
                sb.append("\n");
            }
            String ctx = sb.toString();
            state.setRecallContext(ctx.isEmpty() ? "(无相关知识召回)" : ctx);
            long dur = System.currentTimeMillis() - start;
            state.addTrace("recall", "done", dur, "recalled " + items.size() + " items");
            log.info("recall: {} items in {}ms", items.size(), dur);
        } catch (Exception e) {
            log.warn("recall failed, continuing without knowledge: {}", e.getMessage());
            state.setRecallContext("(知识召回失败，继续执行)");
            state.addTrace("recall", "done", System.currentTimeMillis() - start, "failed: " + e.getMessage());
        }
        return state;
    }

    private List<Long> toLongList(Object v) {
        // absent key → null (no scope, search all enabled KBs);
        // present key → the list itself, possibly empty (agent binds no KBs)
        if (!(v instanceof List<?> list)) return null;
        List<Long> ids = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Number n) ids.add(n.longValue());
        }
        return ids;
    }
}
