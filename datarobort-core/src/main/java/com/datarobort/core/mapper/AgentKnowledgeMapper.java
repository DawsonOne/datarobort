package com.datarobort.core.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface AgentKnowledgeMapper {

    @Insert("INSERT INTO agent_knowledge(agent_id, kb_id) VALUES(#{agentId}, #{kbId})")
    int insert(@Param("agentId") Long agentId, @Param("kbId") Long kbId);

    @Delete("DELETE FROM agent_knowledge WHERE agent_id=#{agentId}")
    int deleteByAgent(@Param("agentId") Long agentId);

    @Select("SELECT kb_id FROM agent_knowledge WHERE agent_id=#{agentId} ORDER BY id")
    List<Long> selectKbIdsByAgent(@Param("agentId") Long agentId);

    @Select("""
            SELECT k.name FROM knowledge_base k
            JOIN agent_knowledge ak ON k.id = ak.kb_id
            WHERE ak.agent_id = #{agentId} ORDER BY ak.id
            """)
    List<String> selectKbNamesByAgent(@Param("agentId") Long agentId);
}
