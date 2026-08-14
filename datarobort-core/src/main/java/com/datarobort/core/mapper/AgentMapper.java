package com.datarobort.core.mapper;

import com.datarobort.core.entity.Agent;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface AgentMapper {

    @Insert("""
            INSERT INTO agent(name, avatar, prompt, status, business_recall_enabled, semantic_recall_enabled,
                              create_time, update_time)
            VALUES(#{name}, #{avatar}, #{prompt}, #{status}, #{businessRecallEnabled}, #{semanticRecallEnabled},
                   NOW(), NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Agent agent);

    @Update("""
            UPDATE agent
            SET name=#{name}, avatar=#{avatar}, prompt=#{prompt}, status=#{status},
                business_recall_enabled=#{businessRecallEnabled},
                semantic_recall_enabled=#{semanticRecallEnabled},
                update_time=NOW()
            WHERE id=#{id}
            """)
    int updateById(Agent agent);

    @Delete("DELETE FROM agent WHERE id=#{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT * FROM agent WHERE id=#{id}")
    Agent selectById(@Param("id") Long id);

    @Select("SELECT * FROM agent ORDER BY id DESC")
    List<Agent> selectAll();

    @Select("SELECT * FROM agent WHERE name=#{name} LIMIT 1")
    Agent selectByName(@Param("name") String name);
}
