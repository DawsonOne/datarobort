package com.datarobort.web.service;

import com.datarobort.common.error.ErrorCode;
import com.datarobort.common.exception.BizException;
import com.datarobort.core.entity.Conversation;
import com.datarobort.core.entity.Message;
import com.datarobort.core.mapper.ConversationMapper;
import com.datarobort.core.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Conversation & message management: session CRUD, message persistence,
 * and multi-turn context window assembly for the LLM prompts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    /** How many recent messages feed the multi-turn context window. */
    private static final int CONTEXT_WINDOW = 10;
    private static final int MAX_HISTORY_CHARS = 4000;

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    @Transactional
    public Conversation create(Long agentId, String title) {
        Conversation c = new Conversation();
        c.setAgentId(agentId);
        c.setTitle(title == null || title.isBlank() ? "新对话" : truncate(title, 50));
        conversationMapper.insert(c);
        return c;
    }

    public List<Conversation> list(Long agentId) {
        if (agentId != null) {
            return conversationMapper.selectByAgent(agentId);
        }
        return conversationMapper.selectAll();
    }

    public Conversation detail(Long id) {
        return require(id);
    }

    public List<Message> messages(Long conversationId) {
        require(conversationId);
        return messageMapper.selectByConversation(conversationId);
    }

    @Transactional
    public Conversation updateTitle(Long id, String title) {
        require(id);
        if (title == null || title.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "title 不能为空");
        }
        conversationMapper.updateTitle(id, truncate(title, 50));
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        messageMapper.deleteByConversation(id);
        conversationMapper.deleteById(id);
    }

    /** Persist one message and touch the conversation timestamp. */
    @Transactional
    public Message saveMessage(Message message) {
        message.setId(null);
        messageMapper.insert(message);
        conversationMapper.touch(message.getConversationId());
        return message;
    }

    /**
     * Build the multi-turn context window: the last N messages formatted as
     * "role: content" lines. Returns empty string when there is no history.
     */
    public String buildHistoryContext(Long conversationId) {
        if (conversationId == null) return "";
        List<Message> recent = messageMapper.selectRecent(conversationId, CONTEXT_WINDOW);
        if (recent.isEmpty()) return "";
        // selectRecent returns DESC order; reverse for chronological order
        List<Message> ordered = new ArrayList<>(recent);
        java.util.Collections.reverse(ordered);

        StringBuilder sb = new StringBuilder();
        for (Message m : ordered) {
            String content = m.getContent();
            if (content == null || content.isBlank()) continue;
            content = content.replace('\n', ' ').trim();
            if (content.length() > 300) content = content.substring(0, 300) + "...";
            sb.append(m.getRole()).append(": ").append(content).append("\n");
            if (sb.length() > MAX_HISTORY_CHARS) break;
        }
        return sb.toString();
    }

    private Conversation require(Long id) {
        Conversation c = conversationMapper.selectById(id);
        if (c == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在: " + id);
        }
        return c;
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
