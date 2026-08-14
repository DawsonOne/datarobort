package com.datarobort.core.mapper;

import com.datarobort.core.entity.Message;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface MessageMapper {

    @Insert("""
            INSERT INTO message(conversation_id, role, content, sql_text, markdown_report, report_file_url,
                                node_traces, create_time)
            VALUES(#{conversationId}, #{role}, #{content}, #{sqlText}, #{markdownReport}, #{reportFileUrl},
                   #{nodeTraces}, NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Message message);

    @Delete("DELETE FROM message WHERE conversation_id=#{conversationId}")
    int deleteByConversation(@Param("conversationId") Long conversationId);

    @Select("SELECT * FROM message WHERE conversation_id=#{conversationId} ORDER BY id ASC")
    List<Message> selectByConversation(@Param("conversationId") Long conversationId);

    @Select("SELECT * FROM message WHERE conversation_id=#{conversationId} ORDER BY id DESC LIMIT #{limit}")
    List<Message> selectRecent(@Param("conversationId") Long conversationId, @Param("limit") int limit);
}
