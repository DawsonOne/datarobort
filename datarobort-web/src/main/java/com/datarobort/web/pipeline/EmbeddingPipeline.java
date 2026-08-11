package com.datarobort.web.pipeline;

import com.datarobort.ai.vector.VectorStoreService;
import com.datarobort.common.error.ErrorCode;
import com.datarobort.common.exception.BizException;
import com.datarobort.core.entity.Chunk;
import com.datarobort.core.entity.Document;
import com.datarobort.core.entity.KnowledgeBase;
import com.datarobort.core.entity.ModelConfig;
import com.datarobort.core.mapper.ChunkMapper;
import com.datarobort.core.mapper.DocumentMapper;
import com.datarobort.core.mapper.ModelConfigMapper;
import com.datarobort.web.service.ModelConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class EmbeddingPipeline {

    private final VectorStoreService vectorStore;
    private final ChunkMapper chunkMapper;
    private final DocumentMapper documentMapper;
    private final ModelConfigService modelConfigService;
    private final ModelConfigMapper modelConfigMapper;
    private final ChunkStrategyRegistry strategyRegistry;

    public EmbeddingPipeline(VectorStoreService vectorStore,
                             ChunkMapper chunkMapper,
                             DocumentMapper documentMapper,
                             ModelConfigService modelConfigService,
                             ModelConfigMapper modelConfigMapper,
                             ChunkStrategyRegistry strategyRegistry) {
        this.vectorStore = vectorStore;
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.modelConfigService = modelConfigService;
        this.modelConfigMapper = modelConfigMapper;
        this.strategyRegistry = strategyRegistry;
    }

    @Async
    public void process(Document doc, KnowledgeBase kb) {
        try {
            List<String> texts = strategyRegistry.get(kb.getChunkStrategy()).chunk(doc.getPlainContent(), kb);
            if (texts.isEmpty()) { markDocFailed(doc, "分块结果为空"); return; }

            List<Chunk> chunks = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                Chunk c = new Chunk();
                c.setDocId(doc.getId()); c.setKbId(kb.getId());
                c.setContent(texts.get(i)); c.setChunkIndex(i);
                c.setVectorStatus(Chunk.VEC_PENDING);
                chunkMapper.insert(c);
                chunks.add(c);
            }

            EmbeddingModel embModel = getEmbeddingModel(kb);
            String indexName = EmbeddingPipeline.indexName(kb.getId());
            String prefix = EmbeddingPipeline.prefix(kb.getId());
            if (!vectorStore.indexExists(indexName)) {
                int dim = kbEmbeddingDimension(kb);
                vectorStore.createIndex(indexName, prefix, dim);
            }

            int done = 0;
            for (Chunk c : chunks) {
                try {
                    float[] vec = vectorStore.embed(embModel, c.getContent());
                    String key = prefix + doc.getId() + "-" + c.getChunkIndex();
                    Map<String, String> meta = new LinkedHashMap<>();
                    meta.put("content", c.getContent());
                    meta.put("doc_id", String.valueOf(doc.getId()));
                    meta.put("kb_id", String.valueOf(kb.getId()));
                    vectorStore.insert(key, vec, meta);
                    c.setVectorId(key); c.setVectorStatus(Chunk.VEC_DONE);
                    chunkMapper.updateVector(c); done++;
                } catch (Exception e) {
                    log.error("chunk {}/{} embed failed: {}", c.getChunkIndex(), doc.getId(), e.getMessage());
                    c.setVectorStatus(Chunk.VEC_FAILED);
                    chunkMapper.updateVector(c);
                }
            }
            doc.setStatus(Document.STATUS_PARSED);
            documentMapper.updateContent(doc);
            log.info("doc {} processed: {}/{} chunks vectorized", doc.getId(), done, chunks.size());
        } catch (Exception e) {
            log.error("pipeline failed doc {}: {}", doc.getId(), e.getMessage(), e);
            markDocFailed(doc, e.getMessage());
        }
    }

    public float[] embedQuery(String text, KnowledgeBase kb) {
        return vectorStore.embed(getEmbeddingModel(kb), text);
    }

    private EmbeddingModel getEmbeddingModel(KnowledgeBase kb) {
        if (kb.getEmbeddingModelId() == null)
            throw new BizException(ErrorCode.PARAM_INVALID, "知识库未绑定 Embedding 模型");
        // Use mapper directly — modelConfigService.detail() masks the API key!
        ModelConfig mc = modelConfigMapper.selectById(kb.getEmbeddingModelId());
        if (mc == null) throw new BizException(ErrorCode.MODEL_NOT_FOUND);
        if (!ModelConfig.TYPE_EMBEDDING.equals(mc.getType()))
            throw new BizException(ErrorCode.PARAM_INVALID, "知识库绑定的模型不是 Embedding 类型");
        return modelConfigService.embeddingClient(mc);
    }

    private int kbEmbeddingDimension(KnowledgeBase kb) {
        ModelConfig mc = modelConfigMapper.selectById(kb.getEmbeddingModelId());
        if (mc == null || mc.getDimension() == null)
            throw new BizException(ErrorCode.EMBEDDING_DIM_MISMATCH, "Embedding 模型维度未知，请先在模型管理页测试连通");
        return mc.getDimension();
    }

    private void markDocFailed(Document doc, String msg) {
        doc.setStatus(Document.STATUS_FAILED);
        doc.setErrorMsg(msg != null && msg.length() > 500 ? msg.substring(0, 500) : msg);
        documentMapper.updateContent(doc);
    }

    public static String indexName(Long kbId) { return "idx:kb:" + kbId; }
    public static String prefix(Long kbId) { return "kb:" + kbId + ":"; }
}
