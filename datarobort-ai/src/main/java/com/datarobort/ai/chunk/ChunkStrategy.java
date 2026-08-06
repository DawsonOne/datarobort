package com.datarobort.ai.chunk;

import com.datarobort.core.entity.KnowledgeBase;
import java.util.List;

/**
 * Splits document plain text into chunks according to a strategy.
 */
public interface ChunkStrategy {

    /** Strategy key matching {@link KnowledgeBase#getChunkStrategy()}. */
    String key();

    /**
     * Split the full text into chunks.
     *
     * @param text full document text
     * @param kb   knowledge base configuration (carries chunk_size, chunk_overlap, delimiter)
     * @return ordered list of chunk texts
     */
    List<String> chunk(String text, KnowledgeBase kb);
}
