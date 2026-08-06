package com.datarobort.core.mapper;

import com.datarobort.core.entity.SemanticModel;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface SemanticModelMapper {

    @Insert("""
            INSERT INTO semantic_model(ds_id, table_name, column_name, synonyms, vector_status,
                                       recall_enabled, create_time, update_time)
            VALUES(#{dsId}, #{tableName}, #{columnName}, #{synonyms}, #{vectorStatus},
                   #{recallEnabled}, NOW(), NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SemanticModel sm);

    @Update("""
            UPDATE semantic_model
            SET synonyms=#{synonyms}, vector_status=#{vectorStatus},
                recall_enabled=#{recallEnabled}, update_time=NOW()
            WHERE id=#{id}
            """)
    int updateById(SemanticModel sm);

    @Delete("DELETE FROM semantic_model WHERE id=#{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT * FROM semantic_model WHERE id=#{id}")
    SemanticModel selectById(@Param("id") Long id);

    @Select("SELECT * FROM semantic_model WHERE ds_id=#{dsId} ORDER BY table_name, column_name")
    List<SemanticModel> selectByDsId(@Param("dsId") Long dsId);

    @Select("SELECT * FROM semantic_model WHERE recall_enabled=1 ORDER BY id")
    List<SemanticModel> selectEnabled();
}
