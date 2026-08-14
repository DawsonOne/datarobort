package com.datarobort.web.service;

import com.datarobort.ai.graph.AgentState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P5: HTML report generation — XSS escaping (user question, SQL, cell
 * values), markdown rendering, table/chart sections, file naming.
 */
class ReportFileServiceTest {

    @TempDir
    Path tempDir;

    private ReportFileService service;

    @BeforeEach
    void setUp() {
        service = new ReportFileService();
        ReflectionTestUtils.setField(service, "reportDirName", tempDir.toString());
    }

    private AgentState state() {
        AgentState s = new AgentState();
        s.setUserQuestion("按月份统计销售趋势");
        s.setIntent("analyze");
        s.setGeneratedSql("SELECT MONTH(create_time) m, SUM(amount) a FROM orders GROUP BY m");
        s.setMarkdownReport("# 结论\n\n本月**增长**良好，见 `SQL`。\n\n> 备注\n\n- 列表项");
        s.setQueryResult(List.of(
                Map.of("month", 1, "amount", 1000),
                Map.of("month", 2, "amount", 2000)));
        AgentState.ChartImage img = new AgentState.ChartImage();
        img.setTitle("月销售趋势");
        img.setImageBase64("iVBORw0KGgo=");
        s.getChartImages().add(img);
        return s;
    }

    @Test
    void generatesHtmlFile_withCoreSections() throws Exception {
        String url = service.generate(state());
        assertNotNull(url);
        assertTrue(url.matches("/reports/report-\\d+-[0-9a-f]{8}\\.html"), "URL format: " + url);

        String html = Files.readString(tempDir.resolve(url.substring("/reports/".length())));
        assertTrue(html.contains("DataRobort 分析报告"));
        assertTrue(html.contains("<h2>图表</h2>"));
        assertTrue(html.contains("<img src=\"data:image/png;base64,iVBORw0KGgo=\""));
        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("<th>month</th>"));
        assertTrue(html.contains("<h2>SQL 查询</h2>"));
        assertTrue(html.contains("SELECT MONTH(create_time)"));
    }

    @Test
    void markdownRendered_toHtml() throws Exception {
        String url = service.generate(state());
        String html = Files.readString(tempDir.resolve(url.substring("/reports/".length())));
        assertTrue(html.contains("<h2>结论</h2>"), "h1 → h2");
        assertTrue(html.contains("<strong>增长</strong>"), "bold");
        assertTrue(html.contains("<code>SQL</code>"), "inline code");
        assertTrue(html.contains("<blockquote>备注</blockquote>"), "blockquote");
        // raw markdown syntax must not leak
        assertFalse(html.contains("# 结论"));
    }

    @Test
    void xssInUserQuestion_escaped() throws Exception {
        AgentState s = state();
        s.setUserQuestion("<script>alert(1)</script>订单");
        String html = readHtml(s);
        assertFalse(html.contains("<script>"), "script tag must be escaped");
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
    }

    @Test
    void xssInSqlAndCells_escaped() throws Exception {
        AgentState s = state();
        s.setGeneratedSql("SELECT '<img src=x onerror=alert(1)>' FROM orders");
        s.setQueryResult(List.of(Map.of("name", "<b>bold</b>&<i>ital</i>")));
        String html = readHtml(s);
        assertFalse(html.contains("<img src=x onerror=alert(1)>"));
        assertFalse(html.contains("<b>bold</b>"), "cell HTML must be escaped");
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"));
        assertTrue(html.contains("&lt;b&gt;bold&lt;/b&gt;&amp;&lt;i&gt;ital&lt;/i&gt;"));
    }

    @Test
    void tableRows_cappedAt20() throws Exception {
        AgentState s = state();
        java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) rows.add(Map.of("n", i));
        s.setQueryResult(rows);
        String html = readHtml(s);
        assertTrue(html.contains("共 50 行"));
        assertTrue(html.contains("前 20 行"));
        assertTrue(html.split("<tr>").length - 1 <= 21, "header + at most 20 data rows");
    }

    @Test
    void chartTitle_escaped() throws Exception {
        AgentState s = state();
        s.getChartImages().clear();
        AgentState.ChartImage img = new AgentState.ChartImage();
        img.setTitle("<h1>t</h1>");
        img.setImageBase64("abc");
        s.getChartImages().add(img);
        String html = readHtml(s);
        assertFalse(html.contains("<h1>t</h1>"));
        assertTrue(html.contains("&lt;h1&gt;t&lt;/h1&gt;"));
    }

    @Test
    void failure_returnsNull() {
        AgentState s = state();
        ReflectionTestUtils.setField(service, "reportDirName", "Z:/impossible/dir/\0");
        assertNull(service.generate(s), "write failure must not throw, returns null");
    }

    private String readHtml(AgentState s) throws Exception {
        String url = service.generate(s);
        return Files.readString(tempDir.resolve(url.substring("/reports/".length())));
    }
}
