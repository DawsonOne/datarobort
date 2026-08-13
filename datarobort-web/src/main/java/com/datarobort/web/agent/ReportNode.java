package com.datarobort.web.agent;

import com.datarobort.ai.graph.AgentState;
import com.datarobort.ai.graph.GraphNode;
import com.datarobort.core.entity.ModelConfig;
import com.datarobort.core.mapper.ModelConfigMapper;
import com.datarobort.web.service.ModelConfigService;
import com.datarobort.web.service.ReportFileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Assembles the final Markdown report from all previous node outputs,
 * and generates the HTML report file (with embedded charts).
 */
@Slf4j
@Component
public class ReportNode implements GraphNode {

    private final ModelConfigService modelConfigService;
    private final ModelConfigMapper modelConfigMapper;
    private final ReportFileService reportFileService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReportNode(ModelConfigService modelConfigService, ModelConfigMapper modelConfigMapper,
                      ReportFileService reportFileService) {
        this.modelConfigService = modelConfigService;
        this.modelConfigMapper = modelConfigMapper;
        this.reportFileService = reportFileService;
    }

    @Override
    public AgentState execute(AgentState state) {
        long start = System.currentTimeMillis();
        try {
            StringBuilder report = new StringBuilder();
            report.append("# 数据分析报告\n\n");
            report.append("**问题**: ").append(state.getUserQuestion()).append("\n\n");

            // Intent
            report.append("**识别意图**: ").append(state.getIntent()).append("\n\n");

            // SQL
            if (state.getGeneratedSql() != null) {
                report.append("## SQL 查询\n");
                report.append("```sql\n").append(state.getGeneratedSql()).append("\n```\n\n");
                report.append("返回 **").append(state.getRowCount()).append("** 行数据\n\n");
            }

            // Data summary via LLM
            if (state.getQueryResult() != null && !state.getQueryResult().isEmpty()) {
                String summary = generateSummary(state);
                report.append(summary).append("\n\n");
            }

            // Python
            if (state.getPythonResult() != null) {
                report.append("## Python 深度分析\n");
                report.append("```json\n").append(truncate(state.getPythonResult(), 2000)).append("\n```\n\n");
            }
            if (state.getPythonError() != null) {
                report.append("> ⚠️ Python 执行异常: ").append(state.getPythonError()).append("\n\n");
            }

            // Chart note
            if (!state.getChartImages().isEmpty()) {
                report.append("## 图表\n*已生成 ").append(state.getChartImages().size())
                     .append(" 张图表，见报告文件*\n\n");
            } else if (state.getChartOption() != null) {
                report.append("## 图表\n*(图表在前端渲染)*\n\n");
            }

            state.setMarkdownReport(report.toString());
            state.addTrace("report", "done", System.currentTimeMillis() - start, "ok");
        } catch (Exception e) {
            log.warn("report generation failed: {}", e.getMessage());
            state.addTrace("report", "done", System.currentTimeMillis() - start, "partial: " + e.getMessage());
        }

        // Generate the self-contained HTML report file (conclusions + charts + data table)
        // only for data-driven intents that actually produced query results
        if (state.isIntent("query", "analyze", "report")
                && state.getQueryResult() != null && !state.getQueryResult().isEmpty()) {
            try {
                state.setReportFileUrl(reportFileService.generate(state));
            } catch (Exception e) {
                log.warn("report file generation failed: {}", e.getMessage());
            }
        }
        return state;
    }

    private String generateSummary(AgentState state) {
        try {
            ChatClient client = defaultChatClient();
            String dataJson = objectMapper.writeValueAsString(
                    state.getQueryResult().size() > 20
                            ? state.getQueryResult().subList(0, 20)
                            : state.getQueryResult());
            String prompt = """
                    根据以下数据，用 3-5 句话中文总结关键发现。直接回复，不要 markdown 格式。

                    问题: %s
                    数据: %s

                    总结:""".formatted(state.getUserQuestion(), dataJson);

            String summary = client.prompt().user(prompt).call().content();
            return "## 数据洞察\n\n" + (summary != null ? summary : "*(无法生成摘要)*");
        } catch (Exception e) {
            return "## 数据洞察\n\n*(LLM 摘要生成失败)*";
        }
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
