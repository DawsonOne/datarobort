package com.datarobort.web.service;

import com.datarobort.ai.vector.VectorStoreService;
import com.datarobort.common.error.ErrorCode;
import com.datarobort.common.exception.BizException;
import com.datarobort.core.entity.ModelConfig;
import com.datarobort.core.entity.SemanticModel;
import com.datarobort.core.mapper.ModelConfigMapper;
import com.datarobort.core.mapper.SemanticModelMapper;
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
public class SemanticModelService {

    private static final String INDEX_NAME = "idx:semantic_model";
    private static final String PREFIX = "sm:";

    private final SemanticModelMapper mapper;
    private final ModelConfigMapper modelConfigMapper;
    private final VectorStoreService vectorStore;
    private final ModelConfigService modelConfigService;

    public List<SemanticModel> listByDsId(Long dsId) { return mapper.selectByDsId(dsId); }

    public SemanticModel detail(Long id) { return require(id); }

    @Transactional
    public SemanticModel create(SemanticModel sm) {
        sm.setId(null);
        if (sm.getRecallEnabled() == null) sm.setRecallEnabled(true);
        sm.setVectorStatus(SemanticModel.VEC_PENDING);
        mapper.insert(sm);
        vectorizeAsync(sm);
        return sm;
    }

    @Transactional
    public SemanticModel update(Long id, SemanticModel sm) {
        require(id);
        sm.setId(id);
        sm.setVectorStatus(SemanticModel.VEC_PENDING);
        mapper.updateById(sm);
        vectorizeAsync(sm);
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        SemanticModel sm = require(id);
        mapper.deleteById(id);
        vectorStore.delete(PREFIX + id);
    }

    private void vectorizeAsync(SemanticModel sm) {
        try {
            EmbeddingModel embModel = defaultEmbeddingModel();
            ensureIndex(embModel);
            String text = buildVectorText(sm);
            float[] vec = vectorStore.embed(embModel, text);
            vectorStore.insert(PREFIX + sm.getId(), vec,
                    Map.of("table_name", sm.getTableName(),
                           "column_name", sm.getColumnName() == null ? "" : sm.getColumnName(),
                           "synonyms", sm.getSynonyms() == null ? "" : sm.getSynonyms()));
            sm.setVectorStatus(SemanticModel.VEC_DONE);
            mapper.updateById(sm);
        } catch (Exception e) {
            log.error("semantic model {} vectorize failed: {}", sm.getId(), e.getMessage());
            sm.setVectorStatus(SemanticModel.VEC_FAILED);
            mapper.updateById(sm);
        }
    }

    private String buildVectorText(SemanticModel sm) {
        StringBuilder sb = new StringBuilder(sm.getTableName());
        if (sm.getColumnName() != null) sb.append(".").append(sm.getColumnName());
        if (sm.getSynonyms() != null) sb.append(" ").append(sm.getSynonyms());
        return sb.toString();
    }

    private EmbeddingModel defaultEmbeddingModel() {
        ModelConfig mc = modelConfigMapper.selectDefault(ModelConfig.TYPE_EMBEDDING);
        if (mc == null) throw new BizException(ErrorCode.PARAM_INVALID, "未设置默认 Embedding 模型");
        if (mc.getDimension() == null) throw new BizException(ErrorCode.EMBEDDING_DIM_MISMATCH, "Embedding 模型维度未知");
        return modelConfigService.embeddingClient(mc);
    }

    private void ensureIndex(EmbeddingModel embModel) {
        if (!vectorStore.indexExists(INDEX_NAME)) {
            ModelConfig mc = modelConfigMapper.selectDefault(ModelConfig.TYPE_EMBEDDING);
            vectorStore.createIndex(INDEX_NAME, PREFIX, mc.getDimension());
        }
    }

    private SemanticModel require(Long id) {
        SemanticModel sm = mapper.selectById(id);
        if (sm == null) throw new BizException(ErrorCode.NOT_FOUND);
        return sm;
    }
}
