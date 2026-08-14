package com.datarobort.web.service;

import com.datarobort.common.error.ErrorCode;
import com.datarobort.common.exception.BizException;
import com.datarobort.core.entity.Agent;
import com.datarobort.core.mapper.AgentDatasourceMapper;
import com.datarobort.core.mapper.AgentKnowledgeMapper;
import com.datarobort.core.mapper.AgentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Agent management: CRUD plus resource bindings (datasources, knowledge
 * bases) and publish/draft lifecycle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentMapper agentMapper;
    private final AgentDatasourceMapper agentDatasourceMapper;
    private final AgentKnowledgeMapper agentKnowledgeMapper;

    public List<Agent> list() {
        List<Agent> agents = agentMapper.selectAll();
        agents.forEach(this::fillBindings);
        return agents;
    }

    public Agent detail(Long id) {
        Agent agent = require(id);
        fillBindings(agent);
        return agent;
    }

    @Transactional
    public Agent create(Agent agent) {
        validate(agent);
        checkNameUnique(null, agent.getName());
        agent.setId(null);
        if (agent.getStatus() == null) agent.setStatus(Agent.STATUS_DRAFT);
        if (agent.getBusinessRecallEnabled() == null) agent.setBusinessRecallEnabled(true);
        if (agent.getSemanticRecallEnabled() == null) agent.setSemanticRecallEnabled(true);
        agentMapper.insert(agent);
        rebuildBindings(agent);
        return detail(agent.getId());
    }

    @Transactional
    public Agent update(Long id, Agent agent) {
        Agent existing = require(id);
        validate(agent);
        checkNameUnique(id, agent.getName());
        agent.setId(id);
        if (agent.getStatus() == null) agent.setStatus(existing.getStatus());
        if (agent.getBusinessRecallEnabled() == null) agent.setBusinessRecallEnabled(existing.getBusinessRecallEnabled());
        if (agent.getSemanticRecallEnabled() == null) agent.setSemanticRecallEnabled(existing.getSemanticRecallEnabled());
        agentMapper.updateById(agent);
        // Rebind only when the frontend sends binding arrays (not on partial updates)
        if (agent.getDatasourceIds() != null) {
            rebuildBindings(agent);
        }
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        agentDatasourceMapper.deleteByAgent(id);
        agentKnowledgeMapper.deleteByAgent(id);
        agentMapper.deleteById(id);
    }

    /** Publish (status=1) or take down (status=0) an agent. */
    @Transactional
    public Agent publish(Long id, Integer status) {
        Agent agent = require(id);
        if (status == null || (status != Agent.STATUS_PUBLISHED && status != Agent.STATUS_DRAFT)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 必须为 0 或 1");
        }
        agent.setStatus(status);
        agentMapper.updateById(agent);
        return detail(id);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void rebuildBindings(Agent agent) {
        agentDatasourceMapper.deleteByAgent(agent.getId());
        if (agent.getDatasourceIds() != null) {
            for (Long dsId : agent.getDatasourceIds()) {
                agentDatasourceMapper.insert(agent.getId(), dsId);
            }
        }
        agentKnowledgeMapper.deleteByAgent(agent.getId());
        if (agent.getKbIds() != null) {
            for (Long kbId : agent.getKbIds()) {
                agentKnowledgeMapper.insert(agent.getId(), kbId);
            }
        }
    }

    private void fillBindings(Agent agent) {
        agent.setDatasourceIds(agentDatasourceMapper.selectDsIdsByAgent(agent.getId()));
        agent.setKbIds(agentKnowledgeMapper.selectKbIdsByAgent(agent.getId()));
        agent.setDatasourceNames(agentDatasourceMapper.selectDsNamesByAgent(agent.getId()));
        agent.setKbNames(agentKnowledgeMapper.selectKbNamesByAgent(agent.getId()));
    }

    private Agent require(Long id) {
        Agent agent = agentMapper.selectById(id);
        if (agent == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND);
        }
        return agent;
    }

    private void validate(Agent agent) {
        if (agent == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "参数不能为空");
        }
        if (agent.getName() == null || agent.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "name 不能为空");
        }
    }

    private void checkNameUnique(Long id, String name) {
        Agent other = agentMapper.selectByName(name);
        if (other != null && !other.getId().equals(id)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "智能体名称已存在: " + name);
        }
    }
}
