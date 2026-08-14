package com.datarobort.web.agent;

import com.datarobort.ai.graph.AgentState;
import com.datarobort.ai.graph.GraphNode;
import com.datarobort.core.entity.ModelConfig;
import com.datarobort.core.mapper.ModelConfigMapper;
import com.datarobort.sandbox.PythonSandboxClient;
import com.datarobort.web.service.ModelConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Generates Python analysis code via LLM and executes it in the Docker sandbox.
 *
 * <p>Data injection hardening (P5): the full query result is handed to the
 * sandbox as base64 (DATA_JSON_B64) instead of being embedded inside a
 * {@code '''...'''} literal, so DB cells containing quotes or backslashes can
 * never escape into the generated Python source. The LLM still sees a
 * truncated plain-text preview for context.
 */
@Slf4j
@Component
public class PythonNode implements GraphNode {

    /** Generated code longer than this is rejected (LLM runaway output). */
    private static final int MAX_CODE_CHARS = 20_000;

    private final ModelConfigService modelConfigService;
    private final ModelConfigMapper modelConfigMapper;
    private final PythonSandboxClient sandbox;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${datarobort.spike.sandbox-image:datarobort-sandbox:latest}")
    private String sandboxImage;

    @Value("${datarobort.spike.sandbox-timeout-seconds:60}")
    private long sandboxTimeoutSeconds;

    public PythonNode(ModelConfigService modelConfigService, ModelConfigMapper modelConfigMapper,
                      PythonSandboxClient sandbox) {
        this.modelConfigService = modelConfigService;
        this.modelConfigMapper = modelConfigMapper;
        this.sandbox = sandbox;
    }

    @Override
    public AgentState execute(AgentState state) {
        if (!state.isIntent("analyze", "report")) {
            state.addTrace("python", "done", 0, "skipped");
            return state;
        }
        if (state.getQueryResult() == null || state.getQueryResult().isEmpty()) {
            state.addTrace("python", "done", 0, "no data to analyze");
            return state;
        }

        long start = System.currentTimeMillis();
        try {
            String dataJson = objectMapper.writeValueAsString(state.getQueryResult());
            String code = generateCode(dataJson, state.getUserQuestion());
            if (code.length() > MAX_CODE_CHARS) {
                state.setPythonError("生成的代码过长（" + code.length() + " 字符），已拒绝执行");
                state.addTrace("python", "failed", System.currentTimeMillis() - start, "code too long");
                return state;
            }
            state.setPythonCode(code);

            PythonSandboxClient.SandboxResult result = sandbox.runPython(
                    sandboxImage, code, Duration.ofSeconds(sandboxTimeoutSeconds));

            long dur = System.currentTimeMillis() - start;
            if (result.timeout()) {
                state.setPythonError("执行超时");
                state.addTrace("python", "failed", dur, "timeout");
                state.setFailed(true); state.setErrorMessage("Python 分析超时");
            } else if (result.exitCode() != 0) {
                state.setPythonError(result.stderr());
                state.addTrace("python", "failed", dur, "exit=" + result.exitCode());
                // Non-fatal: continue with raw data
                state.setPythonResult(result.stdout());
            } else {
                state.setPythonResult(result.stdout());
                extractCharts(state, result.stdout());
                state.addTrace("python", "done", dur,
                        state.getChartImages().isEmpty() ? "ok" : "ok, " + state.getChartImages().size() + " charts");
                log.info("python done in {}ms, {} charts", dur, state.getChartImages().size());
            }
        } catch (Exception e) {
            log.error("Python node failed", e);
            state.addTrace("python", "failed", System.currentTimeMillis() - start, e.getMessage());
            // Non-fatal: continue to chart/report with raw data
        }
        return state;
    }

    /** Extract base64 chart images from the sandbox JSON output. */
    private void extractCharts(AgentState state, String stdout) {
        try {
            JsonNode root = objectMapper.readTree(stdout);
            JsonNode charts = root.get("charts");
            if (charts != null && charts.isArray()) {
                for (JsonNode c : charts) {
                    AgentState.ChartImage img = new AgentState.ChartImage();
                    img.setTitle(c.has("title") ? c.get("title").asText() : "");
                    String b64 = c.has("image") ? c.get("image").asText() : null;
                    if (b64 != null && !b64.isBlank()) {
                        img.setImageBase64(b64);
                        state.getChartImages().add(img);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("failed to extract charts from python output: {}", e.getMessage());
        }
    }

    private String generateCode(String dataJson, String question) {
        ChatClient client = defaultChatClient();
        // Base64 is a safe alphabet — DB content can never break out of the literal
        String dataB64 = Base64.getEncoder().encodeToString(dataJson.getBytes(StandardCharsets.UTF_8));
        String prompt = """
                Write Python 3 code to analyze the following data and answer the user's question.
                Print results as JSON using print(json.dumps(...)).

                DATA (JSON array, truncated preview for reference):
                %s

                USER QUESTION: %s

                REQUIREMENTS:
                - Use only standard library + pandas + matplotlib
                - Read data from DATA_JSON_B64 variable (already injected below, base64-encoded JSON)
                - Generate 1-3 charts with matplotlib that best answer the question
                  (bar/line/pie/scatter). For EACH chart, use the exact pattern:
                      import matplotlib
                      matplotlib.use('Agg')
                      import matplotlib.pyplot as plt
                      plt.figure(figsize=(8, 5))
                      # ... your plotting code ...
                      buf = io.BytesIO()
                      plt.savefig(buf, format='png', dpi=100, bbox_inches='tight')
                      chart_b64 = base64.b64encode(buf.getvalue()).decode('utf-8')
                      plt.close()
                - Use ENGLISH text in chart titles/axis labels (the sandbox has no Chinese fonts)
                - Output ONE valid JSON object via print(json.dumps(result)) with structure:
                  {"summary": "<analysis conclusion text>", "charts": [{"title": "<chart title>", "image": "<base64 png string>"}]}
                - Keep it under 150 lines
                - No file I/O, no network

                PYTHON CODE:
                ```python
                import json
                import pandas as pd
                import io
                import base64

                DATA_JSON_B64 = '%s'

                data = json.loads(base64.b64decode(DATA_JSON_B64).decode('utf-8'))
                df = pd.DataFrame(data)
                # Your analysis code below
                """.formatted(truncate(dataJson, 3000), question, dataB64);

        String resp = client.prompt().user(prompt).call().content();
        if (resp == null) return "print(json.dumps({'error': 'no code generated'}))";
        // Strip markdown code fences
        resp = resp.replaceAll("```python\\s*", "").replaceAll("```\\s*", "").trim();
        // Ensure required imports are present
        if (!resp.contains("import json")) resp = "import json\n" + resp;
        if (!resp.contains("import base64")) resp = "import base64\n" + resp;
        if (!resp.contains("import io")) resp = "import io\n" + resp;
        return resp;
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
