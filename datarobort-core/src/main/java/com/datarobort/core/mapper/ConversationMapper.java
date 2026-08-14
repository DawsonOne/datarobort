package com.datarobort.core.mapper;

import com.datarobort.core.entity.Conversation;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ConversationMapper {

    @Insert("INSERT INTO conversation(agent_id, title, create_time, update_time) VALUES(#{agentId}, #{title}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Conversation conversation);

    @Update("UPDATE conversation SET title=#{title}, update_time=NOW() WHERE id=#{id}")
    int updateTitle(@Param("id") Long id, @Param("title") String title);

    @Update("UPDATE conversation SET update_time=NOW() WHERE id=#{id}")
    int touch(@Param("id") Long id);

    @Delete("DELETE FROM conversation WHERE id=#{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT * FROM conversation WHERE id=#{id}")
    Conversation selectById(@Param("id") Long id);

    @Select("SELECT * FROM conversation ORDER BY update_time DESC, id DESC LIMIT 100")
    List<Conversation> selectAll();

    @Select("SELECT * FROM conversation WHERE agent_id=#{agentId} ORDER BY update_time DESC, id DESC LIMIT 100")
    List<Conversation> selectByAgent(@Param("agentId") Long agentId);
}
