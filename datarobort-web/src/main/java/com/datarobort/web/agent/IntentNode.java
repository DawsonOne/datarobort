package com.datarobort.web.agent;

import com.datarobort.ai.graph.AgentState;
import com.datarobort.ai.graph.GraphNode;
import com.datarobort.core.entity.ModelConfig;
import com.datarobort.core.mapper.ModelConfigMapper;
import com.datarobort.web.service.ModelConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Classifies user intent: chat | query | analyze | report.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentNode implements GraphNode {

    private final ModelConfigService modelConfigService;
    private final ModelConfigMapper modelConfigMapper;

    private static final String PROMPT = """
            Classify the user's intent into exactly one category. Reply with ONLY the category word, nothing else.

            Categories:
            - chat: casual conversation, greetings, general questions NOT about data
            - query: asking for specific numbers, counts, sums, or simple data lookups (Text-to-SQL)
            - analyze: asking for deep analysis, trends, statistics, or comparisons that require Python
            - report: asking for a formatted report with charts, visualizations, or multi-section output

            User input: %s
            Intent:""";

    @Override
    public AgentState execute(AgentState state) {
        String question = state.getUserQuestion();
        if (question == null || question.isBlank()) {
            state.addTrace("intent", "done", 0, "empty question → chat");
            state.setIntent("chat"); state.setIntentConfidence(1.0);
            return state;
        }

        long start = System.currentTimeMillis();
        try {
            ChatClient client = defaultChatClient();
            String reply = client.prompt().user(String.format(PROMPT, question)).call().content();
            String intent = reply != null ? reply.strip().toLowerCase() : "chat";
            // Normalize
            if (!intent.equals("query") && !intent.equals("analyze") && !intent.equals("report")) {
                intent = "chat";
            }
            state.setIntent(intent);
            state.setIntentConfidence(0.9);
            long dur = System.currentTimeMillis() - start;
            state.addTrace("intent", "done", dur, "intent=" + intent);
            log.info("intent: {} ({}ms)", intent, dur);
        } catch (Exception e) {
            log.warn("intent classification failed, defaulting to chat: {}", e.getMessage());
            state.setIntent("chat");
            state.setIntentConfidence(0.5);
            state.addTrace("intent", "done", System.currentTimeMillis() - start, "fallback to chat: " + e.getMessage());
        }
        return state;
    }

    private ChatClient defaultChatClient() {
        ModelConfig mc = modelConfigMapper.selectDefault(ModelConfig.TYPE_CHAT);
        if (mc == null) throw new RuntimeException("未设置默认 Chat 模型");
        return modelConfigService.chatClient(mc);
    }
}
