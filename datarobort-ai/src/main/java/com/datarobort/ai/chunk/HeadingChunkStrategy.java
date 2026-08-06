package com.datarobort.ai.chunk;

import com.datarobort.core.entity.KnowledgeBase;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits text by Markdown headings (lines starting with #). Each heading +
 * its content forms a chunk. Falls back to fixed-size when no headings found.
 */
@Component
public class HeadingChunkStrategy implements ChunkStrategy {

    @Override
    public String key() { return "heading"; }

    @Override
    public List<String> chunk(String text, KnowledgeBase kb) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) return chunks;
        String[] lines = text.split("\n");
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (line.trim().startsWith("#") && !current.isEmpty()) {
                chunks.add(current.toString().trim());
                current.setLength(0);
            }
            if (!current.isEmpty()) current.append('\n');
            current.append(line);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }
        // Fallback if no headings found
        if (chunks.isEmpty() || (chunks.size() == 1 && !text.trim().startsWith("#"))) {
            return new FixedSizeChunkStrategy().chunk(text, kb);
        }
        return chunks;
    }
}
