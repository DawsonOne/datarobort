package com.datarobort.web.service;

import com.datarobort.common.error.ErrorCode;
import com.datarobort.common.exception.BizException;
import com.datarobort.core.entity.Document;
import com.datarobort.core.entity.KnowledgeBase;
import com.datarobort.core.mapper.*;
import com.datarobort.web.pipeline.EmbeddingPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper kbMapper;
    private final DocumentMapper docMapper;
    private final ChunkMapper chunkMapper;
    private final EmbeddingPipeline pipeline;
    private static final Tika tika = new Tika();

    public List<KnowledgeBase> list() { return kbMapper.selectAll(); }

    public KnowledgeBase detail(Long id) { return require(id); }

    @Transactional
    public KnowledgeBase create(KnowledgeBase kb) {
        checkNameUnique(null, kb.getName());
        kb.setId(null);
        if (kb.getChunkStrategy() == null) kb.setChunkStrategy("fixed");
        if (kb.getChunkSize() == null) kb.setChunkSize(500);
        if (kb.getChunkOverlap() == null) kb.setChunkOverlap(50);
        if (kb.getRecallEnabled() == null) kb.setRecallEnabled(true);
        if (kb.getStatus() == null) kb.setStatus(1);
        kbMapper.insert(kb);
        return kb;
    }

    @Transactional
    public KnowledgeBase update(Long id, KnowledgeBase kb) {
        require(id);
        checkNameUnique(id, kb.getName());
        kb.setId(id);
        kbMapper.updateById(kb);
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        // Clean up: chunks → documents → knowledge_base
        chunkMapper.deleteByKbId(id);
        docMapper.deleteByKbId(id);
        kbMapper.deleteById(id);
    }

    // --- Document management ---

    public List<Document> listDocuments(Long kbId) { return docMapper.selectByKbId(kbId); }

    @Transactional
    public Document uploadDocument(Long kbId, MultipartFile file) {
        KnowledgeBase kb = require(kbId);
        Document doc = new Document();
        doc.setKbId(kbId);
        doc.setFilename(file.getOriginalFilename());
        doc.setFileSize(file.getSize());
        doc.setStatus(Document.STATUS_PARSING);

        // Detect file type
        String detected = tika.detect(file.getOriginalFilename());
        doc.setFileType(mapFileType(detected));

        // Parse text with Apache Tika
        try (InputStream in = file.getInputStream()) {
            String text = tika.parseToString(in);
            doc.setPlainContent(text);
        } catch (Exception e) {
            doc.setStatus(Document.STATUS_FAILED);
            doc.setErrorMsg("文件解析失败: " + e.getMessage());
            docMapper.insert(doc);
            throw new BizException(ErrorCode.DOC_PARSE_FAILED, e.getMessage());
        }
        docMapper.insert(doc);

        // Trigger async embedding pipeline
        pipeline.process(doc, kb);
        return doc;
    }

    @Transactional
    public void deleteDocument(Long docId) {
        Document doc = docMapper.selectById(docId);
        if (doc == null) throw new BizException(ErrorCode.NOT_FOUND);
        chunkMapper.deleteByDocId(docId);
        docMapper.deleteById(docId);
    }

    // --- helpers ---

    private KnowledgeBase require(Long id) {
        KnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null) throw new BizException(ErrorCode.KB_NOT_FOUND);
        return kb;
    }

    private void checkNameUnique(Long id, String name) {
        KnowledgeBase other = kbMapper.selectByName(name);
        if (other != null && !other.getId().equals(id))
            throw new BizException(ErrorCode.PARAM_INVALID, "知识库名称已存在: " + name);
    }

    private String mapFileType(String mime) {
        if (mime == null) return "txt";
        if (mime.contains("pdf")) return "pdf";
        if (mime.contains("word") || mime.contains("docx")) return "docx";
        if (mime.contains("markdown") || mime.contains("x-markdown")) return "md";
        return "txt";
    }
}
