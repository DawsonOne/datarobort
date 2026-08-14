package com.datarobort.web.agent;

import com.datarobort.ai.graph.AgentState;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the business datasource(s) an agent is bound to from the runtime
 * agentConfig. Legacy default: when an agent binds no datasource the pipeline
 * falls back to datasource id=1 (the demo business database).
 */
public final class AgentDsResolver {

    private AgentDsResolver() {
    }

    /** First bound datasource id, or the legacy default (1L) when none is bound. */
    public static Long firstDsId(AgentState state) {
        List<Long> ids = dsIds(state);
        return ids.isEmpty() ? 1L : ids.get(0);
    }

    /** All bound datasource ids; falls back to [1L] when none is bound. */
    public static List<Long> dsIds(AgentState state) {
        Object v = state.getAgentConfig().get("datasourceIds");
        if (v instanceof List<?> list) {
            List<Long> ids = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Number n) {
                    ids.add(n.longValue());
                }
            }
            if (!ids.isEmpty()) {
                return ids;
            }
        }
        return List.of(1L);
    }
}
