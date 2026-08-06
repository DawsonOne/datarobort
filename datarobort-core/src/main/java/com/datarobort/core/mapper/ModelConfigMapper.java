package com.datarobort.core.mapper;

import com.datarobort.core.entity.ModelConfig;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ModelConfigMapper {

    @Insert("""
            INSERT INTO model_config(name, type, provider, base_url, api_key, model_name,
                                     dimension, is_default, params, status, create_time, update_time)
            VALUES(#{name}, #{type}, #{provider}, #{baseUrl}, #{apiKey}, #{modelName},
                   #{dimension}, #{isDefault}, #{params}, #{status}, NOW(), NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ModelConfig m);

    @Update("""
            UPDATE model_config
            SET name=#{name}, type=#{type}, provider=#{provider}, base_url=#{baseUrl},
                api_key=#{apiKey}, model_name=#{modelName}, dimension=#{dimension},
                is_default=#{isDefault}, params=#{params}, status=#{status}, update_time=NOW()
            WHERE id=#{id}
            """)
    int updateById(ModelConfig m);

    @Delete("DELETE FROM model_config WHERE id=#{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT * FROM model_config WHERE id=#{id}")
    ModelConfig selectById(@Param("id") Long id);

    @Select("""
            SELECT * FROM model_config
            WHERE (#{type} IS NULL OR type=#{type})
            ORDER BY is_default DESC, id DESC
            """)
    List<ModelConfig> selectList(@Param("type") String type);

    @Update("UPDATE model_config SET is_default=0 WHERE type=#{type}")
    int clearDefault(@Param("type") String type);

    @Update("UPDATE model_config SET is_default=1, update_time=NOW() WHERE id=#{id}")
    int setDefault(@Param("id") Long id);

    @Select("SELECT * FROM model_config WHERE type=#{type} AND is_default=1 AND status=1 LIMIT 1")
    ModelConfig selectDefault(@Param("type") String type);

    @Select("SELECT * FROM model_config WHERE name=#{name} LIMIT 1")
    ModelConfig selectByName(@Param("name") String name);
}
