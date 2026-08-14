package com.datarobort.web.service;

import com.datarobort.ai.vector.VectorStoreService;
import com.datarobort.ai.vector.VectorStoreService.VectorHit;
import com.datarobort.core.entity.BusinessKnowledge;
import com.datarobort.core.entity.KnowledgeBase;
import com.datarobort.core.entity.ModelConfig;
import com.datarobort.core.entity.SemanticModel;
import com.datarobort.core.mapper.BusinessKnowledgeMapper;
import com.datarobort.core.mapper.KnowledgeBaseMapper;
import com.datarobort.core.mapper.ModelConfigMapper;
import com.datarobort.core.mapper.SemanticModelMapper;
import com.datarobort.web.pipeline.EmbeddingPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * P5: recall merge logic — threshold filtering, dedup, agent scope (kbIds),
 * business/semantic switches.
 */
class RecallServiceTest {

    private VectorStoreService vectorStore;
    private KnowledgeBaseMapper kbMapper;
    private BusinessKnowledgeMapper bkMapper;
    private SemanticModelMapper smMapper;
    private EmbeddingPipeline pipeline;
    private ModelConfigService modelConfigService;
    private ModelConfigMapper modelConfigMapper;
    private RecallService service;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStoreService.class);
        kbMapper = mock(KnowledgeBaseMapper.class);
        bkMapper = mock(BusinessKnowledgeMapper.class);
        smMapper = mock(SemanticModelMapper.class);
        pipeline = mock(EmbeddingPipeline.class);
        modelConfigService = mock(ModelConfigService.class);
        modelConfigMapper = mock(ModelConfigMapper.class);
        service = new RecallService(vectorStore, kbMapper, bkMapper, smMapper,
                pipeline, modelConfigService, modelConfigMapper);

        when(vectorStore.embed(any(EmbeddingModel.class), anyString()))
                .thenReturn(new float[]{0.1f});
        when(modelConfigMapper.selectDefault(ModelConfig.TYPE_EMBEDDING))
                .thenReturn(new ModelConfig());
        when(modelConfigService.embeddingClient(any())).thenReturn(mock(EmbeddingModel.class));
    }

    private KnowledgeBase kb(Long id, String name, boolean enabled, int status) {
        KnowledgeBase k = new KnowledgeBase();
        k.setId(id);
        k.setName(name);
        k.setRecallEnabled(enabled);
        k.setStatus(status);
        return k;
    }

    private VectorHit hit(double score, String content) {
        VectorHit h = new VectorHit();
        h.setScore(score);
        h.setContent(content);
        return h;
    }

    @Test
    void thresholdFiltersLowScoreHits() {
        when(kbMapper.selectAll()).thenReturn(List.of(kb(1L, "KB1", true, 1)));
        when(vectorStore.indexExists("idx:kb:1")).thenReturn(true);
        when(vectorStore.search(eq("idx:kb:1"), any(), anyInt()))
                .thenReturn(List.of(hit(0.9, "good"), hit(0.1, "below-threshold")));

        List<RecallService.RecallItem> items = service.recall("q", 10, 0.3);
        assertEquals(1, items.size(), "below-threshold hit must be dropped");
        assertEquals("good", items.get(0).getContent());
    }

    @Test
    void disabledKb_skipped() {
        when(kbMapper.selectAll()).thenReturn(List.of(
                kb(1L, "ON", true, 1),
                kb(2L, "OFF-recall", false, 1),
                kb(3L, "OFF-status", true, 0)));
        when(vectorStore.indexExists(anyString())).thenReturn(true);
        when(vectorStore.search(anyString(), any(), anyInt()))
                .thenReturn(List.of(hit(0.9, "x")));

        List<RecallService.RecallItem> items = service.recall("q", 10, 0.3);
        // Only KB1 yields items
        assertTrue(items.stream().allMatch(i -> i.getSourceId().equals(1L)),
                "disabled KBs must not be searched");
    }

    @Test
    void agentScope_kbIdsRestricts() {
        when(kbMapper.selectAll()).thenReturn(List.of(kb(1L, "A", true, 1), kb(2L, "B", true, 1)));
        when(vectorStore.indexExists(anyString())).thenReturn(true);
        when(vectorStore.search(anyString(), any(), anyInt()))
                .thenReturn(List.of(hit(0.9, "x")));

        List<RecallService.RecallItem> items = service.recall("q", 10, 0.3, List.of(2L), null, null);
        assertTrue(items.stream().allMatch(i -> i.getSourceId().equals(2L)),
                "kbIds scope must restrict search to bound KBs only");
    }

    @Test
    void businessSwitch_disablesBusinessRecall() {
        when(kbMapper.selectAll()).thenReturn(List.of());
        BusinessKnowledge bk = new BusinessKnowledge();
        bk.setTerm("大客户");
        when(bkMapper.selectEnabled()).thenReturn(List.of(bk));
        when(vectorStore.indexExists("idx:business_knowledge")).thenReturn(true);
        when(vectorStore.search(eq("idx:business_knowledge"), any(), anyInt()))
                .thenReturn(List.of(hit(0.9, "大客户")));

        // default on
        List<RecallService.RecallItem> on = service.recall("q", 10, 0.3, null, null, null);
        assertTrue(on.stream().anyMatch(i -> i.getSourceType().equals("business")));

        // switch off
        List<RecallService.RecallItem> off = service.recall("q", 10, 0.3, null, false, null);
        assertTrue(off.stream().noneMatch(i -> i.getSourceType().equals("business")));
    }

    @Test
    void semanticSwitch_disablesSemanticRecall() {
        when(kbMapper.selectAll()).thenReturn(List.of());
        SemanticModel sm = new SemanticModel();
        sm.setTableName("orders");
        when(smMapper.selectEnabled()).thenReturn(List.of(sm));
        when(vectorStore.indexExists("idx:semantic_model")).thenReturn(true);
        when(vectorStore.search(eq("idx:semantic_model"), any(), anyInt()))
                .thenReturn(List.of(hit(0.9, "orders")));

        List<RecallService.RecallItem> off = service.recall("q", 10, 0.3, null, null, false);
        assertTrue(off.stream().noneMatch(i -> i.getSourceType().equals("semantic")));
    }

    @Test
    void duplicates_removedBySourceAndContent() {
        when(kbMapper.selectAll()).thenReturn(List.of(kb(1L, "KB1", true, 1), kb(2L, "KB2", true, 1)));
        when(vectorStore.indexExists(anyString())).thenReturn(true);
        // both KBs return the same content → dedup must keep one
        when(vectorStore.search(anyString(), any(), anyInt()))
                .thenReturn(List.of(hit(0.9, "same"), hit(0.8, "same"), hit(0.7, "other")));

        List<RecallService.RecallItem> items = service.recall("q", 10, 0.3);
        long sameCount = items.stream().filter(i -> i.getContent().equals("same")).count();
        assertEquals(1, sameCount, "identical (sourceType|content) must be deduplicated");
    }

    @Test
    void topKRespected() {
        when(kbMapper.selectAll()).thenReturn(List.of(kb(1L, "KB1", true, 1)));
        when(vectorStore.indexExists("idx:kb:1")).thenReturn(true);
        when(vectorStore.search(eq("idx:kb:1"), any(), anyInt()))
                .thenReturn(List.of(hit(0.9, "a"), hit(0.8, "b"), hit(0.7, "c")));

        List<RecallService.RecallItem> items = service.recall("q", 2, 0.3);
        assertEquals(2, items.size());
    }

    @Test
    void resultsSortedByScoreDesc() {
        when(kbMapper.selectAll()).thenReturn(List.of(kb(1L, "KB1", true, 1)));
        when(vectorStore.indexExists("idx:kb:1")).thenReturn(true);
        when(vectorStore.search(eq("idx:kb:1"), any(), anyInt()))
                .thenReturn(List.of(hit(0.5, "mid"), hit(0.9, "top"), hit(0.7, "low2")));

        List<RecallService.RecallItem> items = service.recall("q", 10, 0.3);
        double[] scores = items.stream().mapToDouble(RecallService.RecallItem::getScore).toArray();
        for (int i = 1; i < scores.length; i++) {
            assertTrue(scores[i - 1] >= scores[i], "must be sorted descending");
        }
    }

    @Test
    void noIndex_noItems() {
        when(kbMapper.selectAll()).thenReturn(List.of(kb(1L, "KB1", true, 1)));
        when(vectorStore.indexExists(anyString())).thenReturn(false);
        assertTrue(service.recall("q", 10, 0.3).isEmpty());
    }
}
