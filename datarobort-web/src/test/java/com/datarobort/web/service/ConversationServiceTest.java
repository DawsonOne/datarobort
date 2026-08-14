package com.datarobort.web.service;

import com.datarobort.core.entity.Message;
import com.datarobort.core.mapper.ConversationMapper;
import com.datarobort.core.mapper.MessageMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * P5: multi-turn context window — window size, 300-char line trim,
 * 4000-char total cap, ordering.
 */
class ConversationServiceTest {

    private final ConversationMapper convMapper = mock(ConversationMapper.class);
    private final MessageMapper msgMapper = mock(MessageMapper.class);
    private final ConversationService service = new ConversationService(convMapper, msgMapper);

    private Message msg(Long id, String role, String content) {
        Message m = new Message();
        m.setId(id);
        m.setRole(role);
        m.setContent(content);
        return m;
    }

    @Test
    void noConversationId_emptyContext() {
        assertEquals("", service.buildHistoryContext(null));
    }

    @Test
    void noHistory_emptyContext() {
        when(msgMapper.selectRecent(1L, 10)).thenReturn(List.of());
        assertEquals("", service.buildHistoryContext(1L));
    }

    @Test
    void history_chronologicalOrder() {
        // selectRecent returns DESC — the service must reverse to chronological
        when(msgMapper.selectRecent(1L, 10)).thenReturn(List.of(
                msg(3L, "assistant", "latest"),
                msg(2L, "user", "middle"),
                msg(1L, "user", "first")));
        String ctx = service.buildHistoryContext(1L);
        assertTrue(ctx.indexOf("first") < ctx.indexOf("middle"), "oldest first");
        assertTrue(ctx.indexOf("middle") < ctx.indexOf("latest"), "chronological order");
        assertTrue(ctx.startsWith("user: first"), "roles preserved: " + ctx);
    }

    @Test
    void window_capsAtTen_viaMapperQuery() {
        // The windowing itself happens in SQL (selectRecent LIMIT ?); the
        // service must ask for exactly CONTEXT_WINDOW messages.
        List<Message> desc = new java.util.ArrayList<>();
        for (long i = 10; i >= 1; i--) desc.add(msg(i, "user", "m" + i));
        when(msgMapper.selectRecent(1L, 10)).thenReturn(desc);
        String ctx = service.buildHistoryContext(1L);
        assertTrue(ctx.contains("m10") && ctx.contains("m1"), "all returned messages in context");
        verify(msgMapper).selectRecent(1L, 10);
        assertEquals("user: m1\nuser: m2", ctx.substring(0, 17));
    }

    @Test
    void longLine_trimmedTo300() {
        when(msgMapper.selectRecent(1L, 10)).thenReturn(List.of(msg(1L, "user", "x".repeat(500))));
        String ctx = service.buildHistoryContext(1L);
        String line = ctx.split("\n")[0];
        // "user: " prefix (6) + 300 chars + "..." (3) = 309
        assertEquals(309, line.length(), "300-char trim + prefix + ellipsis");
        assertTrue(line.endsWith("..."));
    }

    @Test
    void totalCap_4000Chars() {
        List<Message> desc = new java.util.ArrayList<>();
        for (long i = 10; i >= 1; i--) desc.add(msg(i, "user", "y".repeat(600)));
        when(msgMapper.selectRecent(1L, 10)).thenReturn(desc);
        String ctx = service.buildHistoryContext(1L);
        assertTrue(ctx.length() <= 4000, "context must be capped at 4000 chars: " + ctx.length());
    }

    @Test
    void newlines_replacedWithSpaces() {
        when(msgMapper.selectRecent(1L, 10)).thenReturn(List.of(msg(1L, "user", "line1\nline2\nline3")));
        String ctx = service.buildHistoryContext(1L);
        assertFalse(ctx.contains("\nline"), "embedded newlines flattened: " + ctx);
    }
}
