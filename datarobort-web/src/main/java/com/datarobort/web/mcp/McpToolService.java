package com.datarobort.web.mcp;

import com.datarobort.ai.graph.AgentState;
import com.datarobort.web.service.AgentChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP tools exposed to external clients (Claude Desktop etc.).
 * Each tool runs the full agent pipeline and returns the Markdown report.
 * Tools may optionally be scoped to a published agent via agentId.
 */
@Slf4j
@Service
public class McpToolService {

    private static final int MAX_QUESTION_LENGTH = 2000;

    private final AgentChatService chatService;

    public McpToolService(AgentChatService chatService) {
        this.chatService = chatService;
    }

    @Tool(name = "data_query", description = """
            用自然语言查询业务数据库。输入数据分析问题，系统自动完成意图识别、
            知识召回、SQL 生成与校验、查询执行，并返回数据分析报告。
            示例："2024年电子产品类目月度销售额是多少？"
            """)
    public String dataQuery(@ToolParam(description = "自然语言数据分析问题", required = true) String question) {
        return runPipeline("data_query", question, null);
    }

    @Tool(name = "python_analyze", description = """
            对业务数据进行深度分析（趋势、统计、对比），系统在隔离的 Docker 沙箱中
            执行 Python 分析代码并生成图表，返回分析结论报告。
            示例："分析各品类2024年到2026年的销售趋势"
            """)
    public String pythonAnalyze(@ToolParam(description = "需要深度分析的自然语言问题", required = true) String question) {
        return runPipeline("python_analyze", question, null);
    }

    @Tool(name = "report_generate", description = """
            生成完整的分析报告，包含数据洞察、图表（matplotlib 渲染）和 SQL，
            报告保存为 HTML 文件。适用于需要正式输出的场景。
            示例："生成2025年度销售分析报告"
            """)
    public String reportGenerate(@ToolParam(description = "报告主题或分析问题", required = true) String question) {
        return runPipeline("report_generate", question, null);
    }

    private String runPipeline(String tool, String question, Long agentId) {
        if (question == null || question.isBlank()) {
            return "[DataRobort] 错误: question 不能为空";
        }
        if (question.length() > MAX_QUESTION_LENGTH) {
            return "[DataRobort] 错误: 问题长度超过 " + MAX_QUESTION_LENGTH + " 字符";
        }
        long start = System.currentTimeMillis();
        log.info("MCP tool {} invoked: {}", tool, question);
        try {
            AgentState state = chatService.prepareState(question, agentId, null);
            AgentState fs = chatService.run(state);
            long elapsed = System.currentTimeMillis() - start;
            log.info("MCP tool {} finished in {}ms, failed={}", tool, elapsed, fs.isFailed());

            if (fs.isFailed()) {
                return "[DataRobort] 分析失败: " + (fs.getErrorMessage() != null ? fs.getErrorMessage() : "未知错误");
            }
            StringBuilder sb = new StringBuilder();
            if (fs.getMarkdownReport() != null && !fs.getMarkdownReport().isBlank()) {
                sb.append(fs.getMarkdownReport());
            } else {
                sb.append("(无分析报告)");
            }
            if (fs.getReportFileUrl() != null) {
                sb.append("\n\n完整报告文件: ").append(fs.getReportFileUrl());
            }
            sb.append("\n\n(耗时 ").append(elapsed).append("ms)");
            return sb.toString();
        } catch (Exception e) {
            log.error("MCP tool {} failed", tool, e);
            return "[DataRobort] 执行异常: " + e.getMessage();
        }
    }
}
