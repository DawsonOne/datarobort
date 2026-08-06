package com.datarobort.core.mapper;

import com.datarobort.core.entity.Document;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface DocumentMapper {

    @Insert("""
            INSERT INTO document(kb_id, filename, file_type, file_size, plain_content, status, create_time)
            VALUES(#{kbId}, #{filename}, #{fileType}, #{fileSize}, #{plainContent}, #{status}, NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Document doc);

    @Update("""
            UPDATE document SET plain_content=#{plainContent}, status=#{status},
                error_msg=#{errorMsg} WHERE id=#{id}
            """)
    int updateContent(Document doc);

    @Delete("DELETE FROM document WHERE id=#{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT * FROM document WHERE id=#{id}")
    Document selectById(@Param("id") Long id);

    @Select("SELECT * FROM document WHERE kb_id=#{kbId} ORDER BY id DESC")
    List<Document> selectByKbId(@Param("kbId") Long kbId);

    @Delete("DELETE FROM document WHERE kb_id=#{kbId}")
    int deleteByKbId(@Param("kbId") Long kbId);
}
