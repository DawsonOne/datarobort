package com.datarobort.web.controller;

import com.datarobort.common.result.Result;
import com.datarobort.core.entity.Document;
import com.datarobort.core.entity.KnowledgeBase;
import com.datarobort.web.service.KnowledgeBaseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService service;
    private final int maxUploadBytes;

    public KnowledgeBaseController(KnowledgeBaseService service,
                                   @Value("${datarobort.upload.max-size-mb:20}") int maxUploadMb) {
        this.service = service;
        this.maxUploadBytes = maxUploadMb * 1024 * 1024;
    }

    @GetMapping
    public Mono<Result<List<KnowledgeBase>>> list() {
        return Mono.fromCallable(service::list).subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }

    @GetMapping("/{id}")
    public Mono<Result<KnowledgeBase>> detail(@PathVariable Long id) {
        return Mono.fromCallable(() -> service.detail(id)).subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }

    @PostMapping
    public Mono<Result<KnowledgeBase>> create(@RequestBody KnowledgeBase kb) {
        return Mono.fromCallable(() -> service.create(kb)).subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }

    @PutMapping("/{id}")
    public Mono<Result<KnowledgeBase>> update(@PathVariable Long id, @RequestBody KnowledgeBase kb) {
        return Mono.fromCallable(() -> service.update(id, kb)).subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }

    @DeleteMapping("/{id}")
    public Mono<Result<Void>> delete(@PathVariable Long id) {
        return Mono.fromRunnable(() -> service.delete(id)).subscribeOn(Schedulers.boundedElastic()).then(Mono.just(Result.ok()));
    }

    @GetMapping("/{kbId}/documents")
    public Mono<Result<List<Document>>> listDocuments(@PathVariable Long kbId) {
        return Mono.fromCallable(() -> service.listDocuments(kbId)).subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }

    @PostMapping(value = "/{kbId}/documents", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Result<Document>> uploadDocument(@PathVariable Long kbId,
                                                  @RequestPart("file") FilePart filePart) {
        // DataBufferUtils.join(maxBytes) caps the payload while streaming and
        // throws DataBufferLimitException when a huge / unbounded file is sent —
        // otherwise the whole body would be buffered into heap memory (DoS).
        return DataBufferUtils.join(filePart.content(), maxUploadBytes)
                .onErrorMap(DataBufferLimitException.class,
                        e -> new IllegalArgumentException("文件超过大小限制（" + (maxUploadBytes / 1024 / 1024) + "MB）"))
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return Mono.fromCallable(() -> service.uploadDocument(kbId, filePart.filename(), bytes))
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .map(Result::ok);
    }

    @DeleteMapping("/{kbId}/documents/{docId}")
    public Mono<Result<Void>> deleteDocument(@PathVariable Long kbId, @PathVariable Long docId) {
        return Mono.fromRunnable(() -> service.deleteDocument(docId)).subscribeOn(Schedulers.boundedElastic()).then(Mono.just(Result.ok()));
    }
}
