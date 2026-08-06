package com.datarobort.ai.chunk;

import com.datarobort.core.entity.KnowledgeBase;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed-size sliding window chunking with overlap.
 */
@Component
public class FixedSizeChunkStrategy implements ChunkStrategy {

    @Override
    public String key() { return "fixed"; }

    @Override
    public List<String> chunk(String text, KnowledgeBase kb) {
        int size = kb.getChunkSize() != null ? kb.getChunkSize() : 500;
        int overlap = kb.getChunkOverlap() != null ? kb.getChunkOverlap() : 50;
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) return chunks;
        int step = Math.max(size - overlap, 1);
        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(start + size, text.length());
            chunks.add(text.substring(start, end));
        }
        return chunks;
    }
}
