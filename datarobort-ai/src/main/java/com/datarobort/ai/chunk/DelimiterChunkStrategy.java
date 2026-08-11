package com.datarobort.ai.chunk;

import com.datarobort.core.entity.KnowledgeBase;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Splits text by a custom delimiter string.
 */
@Component
public class DelimiterChunkStrategy implements ChunkStrategy {

    @Override
    public String key() { return "delimiter"; }

    @Override
    public List<String> chunk(String text, KnowledgeBase kb) {
        if (text == null || text.isEmpty()) return List.of();
        String delim = kb.getDelimiter();
        if (delim == null || delim.isEmpty()) {
            // fallback to fixed
            return new FixedSizeChunkStrategy().chunk(text, kb);
        }
        return Arrays.stream(text.split(delim, -1))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
