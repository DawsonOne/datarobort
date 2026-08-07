package com.datarobort.web.service;

import com.datarobort.ai.vector.VectorStoreService;
import com.datarobort.common.error.ErrorCode;
import com.datarobort.common.exception.BizException;
import com.datarobort.core.entity.BusinessKnowledge;
import com.datarobort.core.entity.KnowledgeBase;
import com.datarobort.core.entity.ModelConfig;
import com.datarobort.core.entity.SemanticModel;
import com.datarobort.core.mapper.BusinessKnowledgeMapper;
import com.datarobort.core.mapper.KnowledgeBaseMapper;
import com.datarobort.core.mapper.ModelConfigMapper;
import com.datarobort.core.mapper.SemanticModelMapper;
import com.datarobort.web.pipeline.EmbeddingPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Unified recall service: merges results from knowledge base (chunks),
 * business knowledge, and semantic model indexes. Applies recall switch
 * filtering, similarity threshold, dedup, and rerank.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecallService {

    private static final double DEFAULT_THRESHOLD = 0.3;
    private static final int DEFAULT_TOP_K = 10;

    private final VectorStoreService vectorStore;
    private final KnowledgeBaseMapper kbMapper;
    private final BusinessKnowledgeMapper bkMapper;
    private final SemanticModelMapper smMapper;
    private final EmbeddingPipeline pipeline;
    private final ModelConfigService modelConfigService;
    private final ModelConfigMapper modelConfigMapper;

    /**
     * Three-way recall for a query string.
     * Returns a single ranked list of RecallItem across all enabled sources.
     */
    public List<RecallItem> recall(String query, int topK, double threshold) {
        List<RecallItem> all = new ArrayList<>();

        // 1. Knowledge base chunks (each KB uses its own index)
        List<KnowledgeBase> kbs = kbMapper.selectAll();
        for (KnowledgeBase kb : kbs) {
            if (kb.getRecallEnabled() == null || !kb.getRecallEnabled() || kb.getStatus() == null || kb.getStatus() != 1)
                continue;
            try {
                String idx = EmbeddingPipeline.indexName(kb.getId());
                if (!vectorStore.indexExists(idx)) continue;
                float[] qvec = pipeline.embedQuery(query, kb);
                List<VectorStoreService.VectorHit> hits = vectorStore.search(idx, qvec, topK);
                for (VectorStoreService.VectorHit hit : hits) {
                    if (hit.getScore() < threshold) continue;
                    RecallItem item = new RecallItem();
                    item.setSourceType("knowledge");
                    item.setSourceId(kb.getId());
                    item.setSourceTitle("KB:" + kb.getName());
                    item.setContent(hit.getContent());
                    item.setScore(hit.getScore());
                    all.add(item);
                }
            } catch (Exception e) {
                log.warn("recall from kb {} failed: {}", kb.getId(), e.getMessage());
            }
        }

        // 2. Business knowledge
        List<BusinessKnowledge> bks = bkMapper.selectEnabled();
        if (!bks.isEmpty() && vectorStore.indexExists("idx:business_knowledge")) {
            try {
                String queryText = query;
                float[] qvec = vectorStore.embed(defaultEmbeddingModel(), queryText);
                List<VectorStoreService.VectorHit> hits = vectorStore.search("idx:business_knowledge", qvec, topK);
                for (VectorStoreService.VectorHit hit : hits) {
                    if (hit.getScore() < threshold) continue;
                    RecallItem item = new RecallItem();
                    item.setSourceType("business");
                    item.setSourceTitle(hit.getTerm());
                    // Use 'term' as content to guarantee unique dedup keys (synonyms may be empty)
                    item.setContent(hit.getTerm() != null ? hit.getTerm() : "");
                    item.setScore(hit.getScore());
                    all.add(item);
                }
            } catch (Exception e) {
                log.warn("recall from business_knowledge failed: {}", e.getMessage());
            }
        }

        // 3. Semantic model
        List<SemanticModel> sms = smMapper.selectEnabled();
        if (!sms.isEmpty() && vectorStore.indexExists("idx:semantic_model")) {
            try {
                float[] qvec = vectorStore.embed(defaultEmbeddingModel(), query);
                List<VectorStoreService.VectorHit> hits = vectorStore.search("idx:semantic_model", qvec, topK);
                for (VectorStoreService.VectorHit hit : hits) {
                    if (hit.getScore() < threshold) continue;
                    RecallItem item = new RecallItem();
                    item.setSourceType("semantic");
                    String tbl = hit.getTableName() != null ? hit.getTableName() : "";
                    String col = hit.getColumnName() != null ? "." + hit.getColumnName() : "";
                    item.setSourceTitle(tbl + col + " → " + (hit.getSynonyms() != null ? hit.getSynonyms() : ""));
                    item.setContent(tbl + col);
                    item.setScore(hit.getScore());
                    all.add(item);
                }
            } catch (Exception e) {
                log.warn("recall from semantic_model failed: {}", e.getMessage());
            }
        }

        // Dedup & rerank
        all = deduplicate(all);
        all.sort(Comparator.comparingDouble(RecallItem::getScore).reversed());
        if (all.size() > topK) all = all.subList(0, topK);

        log.debug("recall for '{}' returned {} items", query, all.size());
        return all;
    }

    public List<RecallItem> recall(String query) {
        return recall(query, DEFAULT_TOP_K, DEFAULT_THRESHOLD);
    }

    private List<RecallItem> deduplicate(List<RecallItem> items) {
        Set<String> seen = new HashSet<>();
        List<RecallItem> deduped = new ArrayList<>();
        for (RecallItem item : items) {
            String key = item.getSourceType() + "|" + item.getContent();
            if (seen.add(key)) deduped.add(item);
        }
        return deduped;
    }

    private EmbeddingModel defaultEmbeddingModel() {
        ModelConfig mc = modelConfigMapper.selectDefault(ModelConfig.TYPE_EMBEDDING);
        if (mc == null) throw new BizException(ErrorCode.PARAM_INVALID, "未设置默认 Embedding 模型");
        return modelConfigService.embeddingClient(mc);
    }

    // --- inner type ---

    @lombok.Data
    public static class RecallItem {
        private String sourceType;   // knowledge | business | semantic
        private Long sourceId;
        private String sourceTitle;
        private String content;
        private double score;
    }
}
