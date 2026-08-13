package com.datarobort.web.service;

import com.datarobort.ai.graph.AgentState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Assembles analysis results into a self-contained HTML report file
 * (conclusions + embedded base64 chart images + data table + SQL) and
 * saves it under the reports directory. Returns the URL served by the
 * static resource handler (/reports/xxx.html).
 */
@Slf4j
@Service
public class ReportFileService {

    private static final int MAX_TABLE_ROWS = 20;

    @Value("${datarobort.report-dir:reports}")
    private String reportDirName;

    /**
     * Generate the HTML report file for the given state.
     *
     * @return URL like "/reports/report-xxx.html", or null on failure
     */
    public String generate(AgentState state) {
        try {
            Path reportDir = Paths.get(reportDirName);
            Files.createDirectories(reportDir);
            String filename = "report-" + System.currentTimeMillis() + "-"
                    + UUID.randomUUID().toString().substring(0, 8) + ".html";
            Path file = reportDir.resolve(filename);
            Files.writeString(file, buildHtml(state), StandardCharsets.UTF_8);
            log.info("report file saved: {}", file.toAbsolutePath());
            return "/reports/" + filename;
        } catch (Exception e) {
            log.error("failed to generate report file", e);
            return null;
        }
    }

    // ------------------------------------------------------------------
    // HTML assembly
    // ------------------------------------------------------------------

    private String buildHtml(AgentState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>DataRobort 分析报告</title>\n");
        sb.append("<style>\n")
          .append(":root{--ink:#1E293B;--sub:#64748B;--line:#E6E9F2;--indigo:#4F46E5}\n")
          .append("*{box-sizing:border-box}\n")
          .append("body{margin:0;background:#F5F7FB;color:var(--ink);font-family:-apple-system,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;line-height:1.75;font-size:14px}\n")
          .append(".wrap{max-width:960px;margin:0 auto;padding:32px 24px 64px}\n")
          .append("h1{font-size:26px;border-bottom:3px solid var(--indigo);padding-bottom:12px;margin:0 0 20px}\n")
          .append("h2{font-size:19px;margin:28px 0 10px}\n")
          .append("h3{font-size:16px;margin:20px 0 8px}\n")
          .append("h4{font-size:14px;margin:14px 0 6px}\n")
          .append(".meta{color:var(--sub);font-size:13px;margin:4px 0}\n")
          .append(".meta b{color:var(--ink)}\n")
          .append(".chart{margin:20px 0;padding:16px;background:#fff;border:1px solid var(--line);border-radius:12px}\n")
          .append(".chart img{max-width:100%;height:auto;display:block;margin:0 auto}\n")
          .append("table{border-collapse:collapse;width:100%;background:#fff;font-size:13px;margin:12px 0}\n")
          .append("th{background:#F8FAFC;text-align:left;padding:8px 10px;border-bottom:2px solid var(--line);white-space:nowrap}\n")
          .append("td{padding:7px 10px;border-bottom:1px solid var(--line);word-break:break-all}\n")
          .append("pre{background:#1E293B;color:#E2E8F0;padding:14px;border-radius:10px;overflow-x:auto;font-size:12.5px}\n")
          .append("code{background:#F1F5F9;padding:2px 5px;border-radius:4px;font-size:12.5px}\n")
          .append("blockquote{border-left:3px solid var(--line);margin:10px 0;padding:2px 14px;color:var(--sub)}\n")
          .append("</style>\n</head>\n<body>\n<div class=\"wrap\">\n");

        // Header
        sb.append("<h1>DataRobort 分析报告</h1>\n");
        sb.append("<div class=\"meta\"><b>问题</b>：").append(escapeHtml(state.getUserQuestion())).append("</div>\n");
        sb.append("<div class=\"meta\"><b>意图</b>：").append(escapeHtml(state.getIntent())).append("</div>\n");
        sb.append("<div class=\"meta\"><b>生成时间</b>：")
          .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
          .append("</div>\n");

        // Conclusions (markdown → html)
        if (state.getMarkdownReport() != null && !state.getMarkdownReport().isBlank()) {
            sb.append(markdownToHtml(state.getMarkdownReport()));
        }

        // Charts (embedded base64)
        if (state.getChartImages() != null && !state.getChartImages().isEmpty()) {
            sb.append("<h2>图表</h2>\n");
            for (AgentState.ChartImage img : state.getChartImages()) {
                sb.append("<div class=\"chart\">\n");
                if (img.getTitle() != null && !img.getTitle().isBlank()) {
                    sb.append("<h3>").append(escapeHtml(img.getTitle())).append("</h3>\n");
                }
                sb.append("<img src=\"data:image/png;base64,").append(img.getImageBase64()).append("\" alt=\"chart\">\n");
                sb.append("</div>\n");
            }
        }

        // Data table (first 20 rows)
        if (state.getQueryResult() != null && !state.getQueryResult().isEmpty()) {
            sb.append("<h2>数据明细（前 ").append(Math.min(MAX_TABLE_ROWS, state.getQueryResult().size()))
              .append(" 行，共 ").append(state.getQueryResult().size()).append(" 行）</h2>\n");
            sb.append(buildTable(state.getQueryResult()));
        }

        // SQL
        if (state.getGeneratedSql() != null && !state.getGeneratedSql().isBlank()) {
            sb.append("<h2>SQL 查询</h2>\n");
            sb.append("<pre>").append(escapeHtml(state.getGeneratedSql())).append("</pre>\n");
        }

        sb.append("</div>\n</body>\n</html>\n");
        return sb.toString();
    }

    private String buildTable(List<Map<String, Object>> rows) {
        int max = Math.min(rows.size(), MAX_TABLE_ROWS);
        Set<String> cols = new LinkedHashSet<>();
        for (int i = 0; i < max; i++) {
            cols.addAll(rows.get(i).keySet());
        }
        StringBuilder sb = new StringBuilder("<table><thead><tr>");
        for (String c : cols) {
            sb.append("<th>").append(escapeHtml(c)).append("</th>");
        }
        sb.append("</tr></thead><tbody>\n");
        for (int i = 0; i < max; i++) {
            sb.append("<tr>");
            for (String c : cols) {
                Object v = rows.get(i).get(c);
                sb.append("<td>").append(v == null ? "" : escapeHtml(String.valueOf(v))).append("</td>");
            }
            sb.append("</tr>\n");
        }
        sb.append("</tbody></table>\n");
        return sb.toString();
    }

    /** Minimal markdown → HTML converter (headers, bold/italic, code, blockquote). */
    private String markdownToHtml(String md) {
        if (md == null || md.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        for (String block : md.split("\n\n")) {
            String b = block.trim();
            if (b.isEmpty()) continue;
            String html = escapeHtml(b);
            // Code fences first (their content contains no markdown)
            html = html.replaceAll("```\\w*\\s*([\\s\\S]*?)```", "<pre><code>$1</code></pre>");
            // Headers
            html = html.replaceAll("(?m)^### (.+)$", "<h4>$1</h4>");
            html = html.replaceAll("(?m)^## (.+)$", "<h3>$1</h3>");
            html = html.replaceAll("(?m)^# (.+)$", "<h2>$1</h2>");
            // Bold / italic (after escaping, * stays as-is)
            html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
            html = html.replaceAll("\\*(.+?)\\*", "<em>$1</em>");
            // Blockquote ("> " was escaped to "&gt; ")
            html = html.replaceAll("(?m)^&gt; (.+)$", "<blockquote>$1</blockquote>");
            // Inline code
            html = html.replaceAll("`([^`]+)`", "<code>$1</code>");
            // Line breaks within the block
            html = html.replace("\n", "<br>\n");
            out.append(html).append("\n");
        }
        return out.toString();
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
