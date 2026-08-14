package com.datarobort.core.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface AgentDatasourceMapper {

    @Insert("INSERT INTO agent_datasource(agent_id, ds_id) VALUES(#{agentId}, #{dsId})")
    int insert(@Param("agentId") Long agentId, @Param("dsId") Long dsId);

    @Delete("DELETE FROM agent_datasource WHERE agent_id=#{agentId}")
    int deleteByAgent(@Param("agentId") Long agentId);

    @Select("SELECT ds_id FROM agent_datasource WHERE agent_id=#{agentId} ORDER BY id")
    List<Long> selectDsIdsByAgent(@Param("agentId") Long agentId);

    @Select("""
            SELECT d.name FROM datasource d
            JOIN agent_datasource ad ON d.id = ad.ds_id
            WHERE ad.agent_id = #{agentId} ORDER BY ad.id
            """)
    List<String> selectDsNamesByAgent(@Param("agentId") Long agentId);
}
