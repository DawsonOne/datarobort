package com.datarobort.sandbox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P5: bounded stdout/stderr sink — a malicious sandbox printing forever must
 * not exhaust host heap.
 */
class BoundedOutputCollectorTest {

    @Test
    void smallInput_keptWhole() {
        BoundedOutputCollector c = new BoundedOutputCollector(100);
        c.append("hello");
        c.append(" world");
        assertEquals("hello world", c.content());
        assertFalse(c.isTruncated());
    }

    @Test
    void oversizeSingleChunk_truncatedWithMarker() {
        BoundedOutputCollector c = new BoundedOutputCollector(10);
        c.append("0123456789ABCDEF");
        assertEquals(10, c.content().length() - "[output truncated]".length() - 1,
                "content = 10 chars + marker line");
        assertTrue(c.content().contains("[output truncated]"));
        assertTrue(c.isTruncated());
    }

    @Test
    void overflowAcrossChunks_truncatedOnce() {
        BoundedOutputCollector c = new BoundedOutputCollector(12);
        c.append("12345");   // 5
        c.append("67890");   // 10
        c.append("abc");     // room left: 2 → append "ab", truncate
        assertEquals("1234567890ab\n[output truncated]", c.content());
        // further appends are ignored
        c.append("MORE");
        assertEquals("1234567890ab\n[output truncated]", c.content());
    }

    @Test
    void exactlyFits_noTruncation() {
        BoundedOutputCollector c = new BoundedOutputCollector(5);
        c.append("12345");
        assertEquals("12345", c.content());
        assertFalse(c.isTruncated());
    }

    @Test
    void nullAppend_ignored() {
        BoundedOutputCollector c = new BoundedOutputCollector(10);
        c.append(null);
        assertEquals("", c.content());
    }

    @Test
    void emptyMaxChars_neverGrows() {
        BoundedOutputCollector c = new BoundedOutputCollector(0);
        c.append("x");
        assertTrue(c.content().contains("[output truncated]"));
        assertTrue(c.isTruncated());
    }
}
