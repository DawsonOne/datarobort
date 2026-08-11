package com.datarobort.web.service;

import com.datarobort.ai.vector.VectorStoreService;
import com.datarobort.common.error.ErrorCode;
import com.datarobort.common.exception.BizException;
import com.datarobort.core.entity.BusinessKnowledge;
import com.datarobort.core.entity.ModelConfig;
import com.datarobort.core.mapper.BusinessKnowledgeMapper;
import com.datarobort.core.mapper.ModelConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessKnowledgeService {

    private static final String INDEX_NAME = "idx:business_knowledge";
    private static final String PREFIX = "bk:";

    private final BusinessKnowledgeMapper mapper;
    private final ModelConfigMapper modelConfigMapper;
    private final VectorStoreService vectorStore;
    private final ModelConfigService modelConfigService;

    public List<BusinessKnowledge> list() { return mapper.selectAll(); }

    public BusinessKnowledge detail(Long id) { return require(id); }

    @Transactional
    public BusinessKnowledge create(BusinessKnowledge bk) {
        bk.setId(null);
        if (bk.getRecallEnabled() == null) bk.setRecallEnabled(true);
        bk.setVectorStatus(BusinessKnowledge.VEC_PENDING);
        mapper.insert(bk);
        vectorize(bk);
        return bk;
    }

    @Transactional
    public BusinessKnowledge update(Long id, BusinessKnowledge bk) {
        require(id);
        bk.setId(id);
        bk.setVectorStatus(BusinessKnowledge.VEC_PENDING);
        mapper.updateById(bk);
        vectorize(bk);
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        mapper.deleteById(id);
        vectorStore.delete(PREFIX + id);
    }

    // Runs synchronously within the calling transaction (not @Async).
    // If embedding is slow, consider extracting to a separate async service.
    private void vectorize(BusinessKnowledge bk) {
        try {
            ModelConfig mc = modelConfigMapper.selectDefault(ModelConfig.TYPE_EMBEDDING);
            if (mc == null) throw new BizException(ErrorCode.PARAM_INVALID, "未设置默认 Embedding 模型");
            if (mc.getDimension() == null) throw new BizException(ErrorCode.EMBEDDING_DIM_MISMATCH, "Embedding 模型维度未知");
            EmbeddingModel embModel = modelConfigService.embeddingClient(mc);
            vectorStore.createIndex(INDEX_NAME, PREFIX, mc.getDimension());
            String text = buildVectorText(bk);
            float[] vec = vectorStore.embed(embModel, text);
            vectorStore.insert(PREFIX + bk.getId(), vec, Map.of("term", bk.getTerm(), "synonyms", bk.getSynonyms() == null ? "" : bk.getSynonyms()));
            bk.setVectorStatus(BusinessKnowledge.VEC_DONE);
            mapper.updateById(bk);
        } catch (Exception e) {
            log.error("business knowledge {} vectorize failed: {}", bk.getId(), e.getMessage());
            bk.setVectorStatus(BusinessKnowledge.VEC_FAILED);
            mapper.updateById(bk);
        }
    }

    private String buildVectorText(BusinessKnowledge bk) {
        StringBuilder sb = new StringBuilder(bk.getTerm());
        if (bk.getSynonyms() != null) sb.append(" ").append(bk.getSynonyms());
        return sb.toString();
    }

    private BusinessKnowledge require(Long id) {
        BusinessKnowledge bk = mapper.selectById(id);
        if (bk == null) throw new BizException(ErrorCode.NOT_FOUND);
        return bk;
    }
}
