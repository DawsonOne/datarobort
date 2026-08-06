package com.datarobort.core.mapper;

import com.datarobort.core.entity.DsColumn;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DsColumnMapper {

    @Insert("""
            INSERT INTO ds_column(table_id, column_name, data_type, column_comment, is_primary, nullable)
            VALUES(#{tableId}, #{columnName}, #{dataType}, #{columnComment}, #{isPrimary}, #{nullable})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DsColumn c);

    @Delete("""
            DELETE FROM ds_column WHERE table_id IN
            (SELECT id FROM ds_table WHERE ds_id=#{dsId})
            """)
    int deleteByDsId(@Param("dsId") Long dsId);

    @Select("""
            SELECT c.* FROM ds_column c
            JOIN ds_table t ON c.table_id = t.id
            WHERE t.ds_id=#{dsId}
            ORDER BY c.table_id, c.id
            """)
    List<DsColumn> selectByDsId(@Param("dsId") Long dsId);

    @Select("SELECT * FROM ds_column WHERE table_id=#{tableId} ORDER BY id")
    List<DsColumn> selectByTableId(@Param("tableId") Long tableId);
}
