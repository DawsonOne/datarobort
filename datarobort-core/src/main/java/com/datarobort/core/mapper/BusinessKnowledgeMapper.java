package com.datarobort.core.mapper;

import com.datarobort.core.entity.BusinessKnowledge;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface BusinessKnowledgeMapper {

    @Insert("""
            INSERT INTO business_knowledge(term, synonyms, vector_status, recall_enabled, create_time, update_time)
            VALUES(#{term}, #{synonyms}, #{vectorStatus}, #{recallEnabled}, NOW(), NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(BusinessKnowledge bk);

    @Update("""
            UPDATE business_knowledge
            SET term=#{term}, synonyms=#{synonyms}, vector_status=#{vectorStatus},
                recall_enabled=#{recallEnabled}, update_time=NOW()
            WHERE id=#{id}
            """)
    int updateById(BusinessKnowledge bk);

    @Delete("DELETE FROM business_knowledge WHERE id=#{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT * FROM business_knowledge WHERE id=#{id}")
    BusinessKnowledge selectById(@Param("id") Long id);

    @Select("SELECT * FROM business_knowledge ORDER BY id DESC")
    List<BusinessKnowledge> selectAll();

    @Select("SELECT * FROM business_knowledge WHERE recall_enabled=1 ORDER BY id")
    List<BusinessKnowledge> selectEnabled();
}
