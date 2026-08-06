package com.datarobort.core.mapper;

import com.datarobort.core.entity.DsTable;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DsTableMapper {

    @Insert("INSERT INTO ds_table(ds_id, table_name, table_comment) VALUES(#{dsId}, #{tableName}, #{tableComment})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DsTable t);

    @Delete("DELETE FROM ds_table WHERE ds_id=#{dsId}")
    int deleteByDsId(@Param("dsId") Long dsId);

    @Select("SELECT * FROM ds_table WHERE ds_id=#{dsId} ORDER BY table_name")
    List<DsTable> selectByDsId(@Param("dsId") Long dsId);

    @Select("SELECT * FROM ds_table WHERE id=#{id}")
    DsTable selectById(@Param("id") Long id);
}
