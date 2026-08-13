package com.datarobort.web.agent;

import com.datarobort.ai.graph.AgentState;
import com.datarobort.ai.graph.GraphNode;
import com.datarobort.web.service.RecallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Calls RecallService to retrieve knowledge base chunks, business terms,
 * and semantic model mappings for the user's question.
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
            List<RecallService.RecallItem> items = recallService.recall(state.getUserQuestion(), 5, 0.2);
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
}
