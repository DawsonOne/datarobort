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
 * Validates generated SQL through {@link SqlValidator} (syntax, SELECT-only,
 * no WITH / dangerous functions / table whitelist). If validation fails, asks
 * the LLM to fix it (up to 3 retries). The guard is enforced again at execution
 * time by SqlExecNode, so state tampering cannot bypass it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlGuardNode implements GraphNode {

    private static final int MAX_RETRY = 3;

    private final ModelConfigService modelConfigService;
    private final ModelConfigMapper modelConfigMapper;
    private final SqlValidator sqlValidator;

    @Override
    public AgentState execute(AgentState state) {
        if (state.getGeneratedSql() == null || state.getGeneratedSql().isBlank()) {
            state.addTrace("sql-guard", "done", 0, "no SQL to validate");
            return state;
        }

        String sql = state.getGeneratedSql();
        Long dsId = AgentDsResolver.firstDsId(state);

        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            long start = System.currentTimeMillis();
            String error = sqlValidator.validate(sql, dsId);
            if (error == null) {
                state.setGeneratedSql(sql);
                state.addTrace("sql-guard", "done", System.currentTimeMillis() - start, "SQL valid");
                return state;
            }
            state.setSqlError(error);
            state.setSqlRetryCount(attempt + 1);
            log.warn("SQL validation attempt {} failed: {}", attempt + 1, error);

            if (attempt < MAX_RETRY - 1) {
                sql = fixSql(sql, error);
                if (sql == null) {
                    state.addTrace("sql-guard", "failed", System.currentTimeMillis() - start, "cannot fix");
                    state.setFailed(true); state.setErrorMessage("SQL 校验失败: " + error);
                    return state;
                }
            }
        }

        state.addTrace("sql-guard", "failed", 0, "max retries exceeded: " + state.getSqlError());
        state.setFailed(true); state.setErrorMessage("SQL 校验失败(已重试" + MAX_RETRY + "次): " + state.getSqlError());
        return state;
    }

    /** Ask LLM to fix the SQL. Returns null if unfixable. */
    private String fixSql(String badSql, String error) {
        try {
            ChatClient client = defaultChatClient();
            String prompt = """
                    Fix the following SQL based on the error message. Reply with ONLY the corrected SQL, no explanation.

                    Bad SQL: %s
                    Error: %s

                    Fixed SQL:""".formatted(badSql, error);
            String fixed = client.prompt().user(prompt).call().content();
            if (fixed == null || fixed.isBlank()) return null;
            return fixed.replaceAll("```sql\\s*", "").replaceAll("```\\s*", "").trim();
        } catch (Exception e) {
            log.warn("SQL fix LLM call failed: {}", e.getMessage());
            return null;
        }
    }

    private ChatClient defaultChatClient() {
        ModelConfig mc = modelConfigMapper.selectDefault(ModelConfig.TYPE_CHAT);
        if (mc == null) throw new RuntimeException("未设置默认 Chat 模型");
        return modelConfigService.chatClient(mc);
    }
}
