package com.datarobort.web.service;

import com.datarobort.ai.model.ModelClientFactory;
import com.datarobort.common.crypto.AesCryptoUtil;
import com.datarobort.common.error.ErrorCode;
import com.datarobort.common.exception.BizException;
import com.datarobort.core.entity.ModelConfig;
import com.datarobort.core.mapper.ModelConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Model configuration management: CRUD with api-key encryption & masking,
 * connectivity tests, embedding dimension probing, default-model election.
 */
@Slf4j
@Service
public class ModelConfigService {

    private static final String MASK = "******";

    private final ModelConfigMapper mapper;
    private final ModelClientFactory clientFactory;

    @Value("${datarobort.crypto-key:datarobort-dev-key}")
    private String cryptoKey;

    public ModelConfigService(ModelConfigMapper mapper, ModelClientFactory clientFactory) {
        this.mapper = mapper;
        this.clientFactory = clientFactory;
    }

    public List<ModelConfig> list(String type) {
        List<ModelConfig> list = mapper.selectList(type);
        list.forEach(m -> m.setApiKey(m.getApiKey() == null ? null : MASK));
        return list;
    }

    public ModelConfig detail(Long id) {
        ModelConfig m = require(id);
        m.setApiKey(m.getApiKey() == null ? null : MASK);
        return m;
    }

    @Transactional
    public ModelConfig create(ModelConfig m) {
        validate(m);
        checkNameUnique(null, m.getName());
        m.setId(null);
        if (m.getStatus() == null) {
            m.setStatus(1);
        }
        if (m.getApiKey() != null && !MASK.equals(m.getApiKey())) {
            m.setApiKey(AesCryptoUtil.encrypt(m.getApiKey(), cryptoKey));
        }
        if (Boolean.TRUE.equals(m.getIsDefault())) {
            mapper.clearDefault(m.getType());
        } else if (m.getIsDefault() == null) {
            // Only auto-elect as default when the user didn't express a preference
            // AND no record of this type exists yet.
            m.setIsDefault(mapper.selectList(m.getType()).isEmpty());
        }
        probeDimensionIfEmbedding(m);
        mapper.insert(m);
        return detail(m.getId());
    }

    @Transactional
    public ModelConfig update(Long id, ModelConfig m) {
        ModelConfig existing = require(id);
        validate(m);
        checkNameUnique(id, m.getName());
        m.setId(id);
        // keep old key when the frontend sends back the mask
        if (m.getApiKey() == null || MASK.equals(m.getApiKey())) {
            m.setApiKey(existing.getApiKey());
        } else {
            m.setApiKey(AesCryptoUtil.encrypt(m.getApiKey(), cryptoKey));
        }
        if (m.getIsDefault() == null) {
            m.setIsDefault(Boolean.TRUE.equals(existing.getIsDefault()));
        }
        if (m.getStatus() == null) {
            m.setStatus(existing.getStatus());
        }
        if (Boolean.TRUE.equals(m.getIsDefault()) && !Boolean.TRUE.equals(existing.getIsDefault())) {
            mapper.clearDefault(m.getType());
        }
        probeDimensionIfEmbedding(m);
        mapper.updateById(m);
        clientFactory.evict(id);
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        mapper.deleteById(id);
        clientFactory.evict(id);
    }

    @Transactional
    public ModelConfig setDefault(Long id) {
        ModelConfig m = require(id);
        mapper.clearDefault(m.getType());
        mapper.setDefault(id);
        return detail(id);
    }

    /**
     * Connectivity test: one real call against the provider.
     * Chat -> short completion; embedding -> dimension probe.
     */
    public Map<String, Object> test(Long id) {
        ModelConfig m = require(id);
        String plainKey = decrypt(m.getApiKey());
        long start = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            if (ModelConfig.TYPE_EMBEDDING.equals(m.getType())) {
                int dim = clientFactory.probeEmbeddingDimension(m.getBaseUrl(), plainKey, m.getModelName());
                result.put("success", true);
                result.put("message", "连接成功，向量维度=" + dim);
            } else {
                ChatClient client = clientFactory.chatClient(m.getId(), m.getBaseUrl(), plainKey, m.getModelName());
                String reply = client.prompt().user("reply with the single word: pong").call().content();
                result.put("success", true);
                result.put("message", "连接成功，模型回复: " + (reply == null ? "" : reply.strip()));
            }
        } catch (Exception e) {
            log.warn("model {} test failed: {}", id, e.getMessage());
            result.put("success", false);
            result.put("message", "连接失败: " + rootMessage(e));
        }
        result.put("latencyMs", System.currentTimeMillis() - start);
        return result;
    }

    /** Decrypts the api key; used internally by AI services. */
    public String decryptApiKey(ModelConfig m) {
        return decrypt(m.getApiKey());
    }

    /** Embedding client for the runtime (never exposes the key). */
    public EmbeddingModel embeddingClient(ModelConfig m) {
        return clientFactory.embeddingModel(m.getId(), m.getBaseUrl(), decrypt(m.getApiKey()), m.getModelName());
    }

    /** Chat client for the runtime (never exposes the key). */
    public ChatClient chatClient(ModelConfig m) {
        return clientFactory.chatClient(m.getId(), m.getBaseUrl(), decrypt(m.getApiKey()), m.getModelName());
    }

    private void probeDimensionIfEmbedding(ModelConfig m) {
        if (!ModelConfig.TYPE_EMBEDDING.equals(m.getType())) {
            m.setDimension(null);
            return;
        }
        try {
            m.setDimension(clientFactory.probeEmbeddingDimension(
                    m.getBaseUrl(), decrypt(m.getApiKey()), m.getModelName()));
        } catch (Exception e) {
            log.warn("embedding dimension probe failed for {}: {}", m.getModelName(), e.getMessage());
            m.setDimension(null);
        }
    }

    private String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty() || MASK.equals(encrypted)) {
            return encrypted;
        }
        return AesCryptoUtil.decrypt(encrypted, cryptoKey);
    }

    private ModelConfig require(Long id) {
        ModelConfig m = mapper.selectById(id);
        if (m == null) {
            throw new BizException(ErrorCode.MODEL_NOT_FOUND);
        }
        return m;
    }

    private void validate(ModelConfig m) {
        if (m == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "参数不能为空");
        }
        if (m.getName() == null || m.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "name 不能为空");
        }
        if (m.getType() == null ||
                (!ModelConfig.TYPE_CHAT.equals(m.getType()) && !ModelConfig.TYPE_EMBEDDING.equals(m.getType()))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "type 必须为 chat 或 embedding");
        }
        if (m.getBaseUrl() == null || m.getBaseUrl().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "baseUrl 不能为空");
        }
        if (m.getModelName() == null || m.getModelName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "modelName 不能为空");
        }
    }

    private void checkNameUnique(Long id, String name) {
        ModelConfig other = mapper.selectByName(name);
        if (other != null && !other.getId().equals(id)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模型名称已存在: " + name);
        }
    }

    private String rootMessage(Throwable e) {
        Throwable t = e;
        java.util.Set<Throwable> seen = new java.util.HashSet<>();
        while (t.getCause() != null && seen.add(t.getCause())) {
            t = t.getCause();
        }
        return t.getMessage() == null ? e.getMessage() : t.getMessage();
    }
}
