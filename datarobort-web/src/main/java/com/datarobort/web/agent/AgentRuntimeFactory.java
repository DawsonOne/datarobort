package com.datarobort.web.agent;

import com.datarobort.ai.graph.AgentState;
import com.datarobort.common.error.ErrorCode;
import com.datarobort.common.exception.BizException;
import com.datarobort.core.entity.Agent;
import com.datarobort.core.mapper.AgentDatasourceMapper;
import com.datarobort.core.mapper.AgentKnowledgeMapper;
import com.datarobort.core.mapper.AgentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Assembles the runtime context for one agent: bound datasources,
 * knowledge bases, recall switches and the custom system prompt are
 * written into the AgentState's agentConfig map, from where the graph
 * nodes pick them up. One agent = one pipeline configuration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRuntimeFactory {

    private final AgentMapper agentMapper;
    private final AgentDatasourceMapper agentDatasourceMapper;
    private final AgentKnowledgeMapper agentKnowledgeMapper;

    /**
     * Load the agent config and apply it to the state. No-op when agentId is null.
     *
     * @throws BizException when the agent does not exist
     */
    public void applyToState(AgentState state, Long agentId) {
        if (agentId == null) return;

        Agent agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND);
        }

        state.setAgentId(agentId);
        Map<String, Object> cfg = state.getAgentConfig();
        cfg.put("prompt", agent.getPrompt());
        cfg.put("datasourceIds", agentDatasourceMapper.selectDsIdsByAgent(agentId));
        cfg.put("kbIds", agentKnowledgeMapper.selectKbIdsByAgent(agentId));
        cfg.put("businessRecallEnabled", agent.getBusinessRecallEnabled());
        cfg.put("semanticRecallEnabled", agent.getSemanticRecallEnabled());
        log.debug("agent {} runtime assembled: {} datasources, {} kbs",
                agent.getName(), cfg.get("datasourceIds"), cfg.get("kbIds"));
    }
}
