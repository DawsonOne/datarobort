package com.datarobort.core.mapper;

import com.datarobort.core.entity.Chunk;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ChunkMapper {

    @Insert("""
            INSERT INTO chunk(doc_id, kb_id, content, chunk_index, vector_id, vector_status, create_time)
            VALUES(#{docId}, #{kbId}, #{content}, #{chunkIndex}, #{vectorId}, #{vectorStatus}, NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Chunk c);

    @Update("UPDATE chunk SET vector_id=#{vectorId}, vector_status=#{vectorStatus} WHERE id=#{id}")
    int updateVector(Chunk c);

    @Delete("DELETE FROM chunk WHERE doc_id=#{docId}")
    int deleteByDocId(@Param("docId") Long docId);

    @Delete("DELETE FROM chunk WHERE kb_id=#{kbId}")
    int deleteByKbId(@Param("kbId") Long kbId);

    @Select("SELECT * FROM chunk WHERE doc_id=#{docId} ORDER BY chunk_index")
    List<Chunk> selectByDocId(@Param("docId") Long docId);

    @Select("SELECT * FROM chunk WHERE kb_id=#{kbId} ORDER BY id")
    List<Chunk> selectByKbId(@Param("kbId") Long kbId);

    @Select("SELECT * FROM chunk WHERE vector_status='pending' LIMIT #{limit}")
    List<Chunk> selectPending(@Param("limit") int limit);

    @Select("SELECT * FROM chunk WHERE vector_status='done' AND kb_id=#{kbId}")
    List<Chunk> selectDoneByKbId(@Param("kbId") Long kbId);
}
