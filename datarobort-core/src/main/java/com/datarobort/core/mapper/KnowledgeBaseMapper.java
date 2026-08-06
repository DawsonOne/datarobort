package com.datarobort.core.mapper;

import com.datarobort.core.entity.KnowledgeBase;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface KnowledgeBaseMapper {

    @Insert("""
            INSERT INTO knowledge_base(name, description, chunk_strategy, chunk_size, chunk_overlap,
                                       delimiter, embedding_model_id, recall_enabled, status, create_time, update_time)
            VALUES(#{name}, #{description}, #{chunkStrategy}, #{chunkSize}, #{chunkOverlap},
                   #{delimiter}, #{embeddingModelId}, #{recallEnabled}, #{status}, NOW(), NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(KnowledgeBase kb);

    @Update("""
            UPDATE knowledge_base
            SET name=#{name}, description=#{description}, chunk_strategy=#{chunkStrategy},
                chunk_size=#{chunkSize}, chunk_overlap=#{chunkOverlap}, delimiter=#{delimiter},
                embedding_model_id=#{embeddingModelId}, recall_enabled=#{recallEnabled},
                status=#{status}, update_time=NOW()
            WHERE id=#{id}
            """)
    int updateById(KnowledgeBase kb);

    @Delete("DELETE FROM knowledge_base WHERE id=#{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT * FROM knowledge_base WHERE id=#{id}")
    KnowledgeBase selectById(@Param("id") Long id);

    @Select("SELECT * FROM knowledge_base ORDER BY id DESC")
    List<KnowledgeBase> selectAll();

    @Select("SELECT * FROM knowledge_base WHERE name=#{name} LIMIT 1")
    KnowledgeBase selectByName(@Param("name") String name);
}
