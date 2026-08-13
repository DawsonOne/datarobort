package com.datarobort.web.agent;

import com.datarobort.ai.graph.AgentState;
import com.datarobort.ai.graph.GraphNode;
import com.datarobort.core.entity.ModelConfig;
import com.datarobort.core.mapper.ModelConfigMapper;
import com.datarobort.web.service.ModelConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Generates an ECharts option JSON based on query results and analysis.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChartNode implements GraphNode {

    private final ModelConfigService modelConfigService;
    private final ModelConfigMapper modelConfigMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AgentState execute(AgentState state) {
        if (!state.isIntent("analyze", "report")) {
            state.addTrace("chart", "done", 0, "skipped");
            return state;
        }

        long start = System.currentTimeMillis();
        try {
            StringBuilder context = new StringBuilder();
            context.append("用户问题: ").append(state.getUserQuestion()).append("\n");
            if (state.getQueryResult() != null) {
                context.append("数据(前10行): ")
                        .append(truncate(objectMapper.writeValueAsString(
                                state.getQueryResult().size() > 10
                                        ? state.getQueryResult().subList(0, 10)
                                        : state.getQueryResult()), 3000));
            }
            if (state.getPythonResult() != null) {
                context.append("\n分析结果: ").append(state.getPythonResult());
            }

            ChatClient client = defaultChatClient();
            String prompt = """
                    Generate an ECharts option JSON for the following context.
                    Choose an appropriate chart type (bar, line, pie, scatter).
                    Reply with ONLY valid JSON, no explanation, no markdown.

                    %s

                    ECharts option (JSON only):""".formatted(context.toString());

            String json = client.prompt().user(prompt).call().content();
            if (json != null) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
                // Basic validation: must start with {
                if (json.startsWith("{")) {
                    state.setChartOption(json);
                    state.addTrace("chart", "done", System.currentTimeMillis() - start, "ok");
                    return state;
                }
            }
            state.addTrace("chart", "done", System.currentTimeMillis() - start, "no chart generated");
        } catch (Exception e) {
            log.warn("chart generation failed: {}", e.getMessage());
            state.addTrace("chart", "done", System.currentTimeMillis() - start, "failed: " + e.getMessage());
        }
        return state;
    }

    private ChatClient defaultChatClient() {
        ModelConfig mc = modelConfigMapper.selectDefault(ModelConfig.TYPE_CHAT);
        if (mc == null) throw new RuntimeException("未设置默认 Chat 模型");
        return modelConfigService.chatClient(mc);
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
