package com.datarobort.core.mapper;

import com.datarobort.core.entity.Datasource;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DatasourceMapper {

    @Insert("""
            INSERT INTO datasource(name, type, jdbc_url, username, password, description,
                                   status, create_time, update_time)
            VALUES(#{name}, #{type}, #{jdbcUrl}, #{username}, #{password}, #{description},
                   #{status}, NOW(), NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Datasource ds);

    @Update("""
            UPDATE datasource
            SET name=#{name}, type=#{type}, jdbc_url=#{jdbcUrl}, username=#{username},
                password=#{password}, description=#{description}, status=#{status},
                update_time=NOW()
            WHERE id=#{id}
            """)
    int updateById(Datasource ds);

    @Delete("DELETE FROM datasource WHERE id=#{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT * FROM datasource WHERE id=#{id}")
    Datasource selectById(@Param("id") Long id);

    @Select("SELECT * FROM datasource ORDER BY id DESC")
    List<Datasource> selectAll();

    @Select("SELECT * FROM datasource WHERE name=#{name} LIMIT 1")
    Datasource selectByName(@Param("name") String name);
}
