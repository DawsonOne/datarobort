package com.datarobort.web.controller;

import com.datarobort.common.result.Result;
import com.datarobort.core.entity.Datasource;
import com.datarobort.web.service.DatasourceService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * Datasource management APIs.
 */
@RestController
@RequestMapping("/api/datasources")
public class DatasourceController {

    private final DatasourceService service;

    public DatasourceController(DatasourceService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<Result<List<Datasource>>> list() {
        return Mono.fromCallable(() -> service.list())
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::ok);
    }

    @GetMapping("/{id}")
    public Mono<Result<Datasource>> detail(@PathVariable Long id) {
        return Mono.fromCallable(() -> service.detail(id))
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::ok);
    }

    @PostMapping
    public Mono<Result<Datasource>> create(@RequestBody Datasource d) {
        return Mono.fromCallable(() -> service.create(d))
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::ok);
    }

    @PutMapping("/{id}")
    public Mono<Result<Datasource>> update(@PathVariable Long id, @RequestBody Datasource d) {
        return Mono.fromCallable(() -> service.update(id, d))
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::ok);
    }

    @DeleteMapping("/{id}")
    public Mono<Result<Void>> delete(@PathVariable Long id) {
        return Mono.fromRunnable(() -> service.delete(id))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.just(Result.ok()));
    }

    /** Connection test. */
    @PostMapping("/{id}/test")
    public Mono<Result<Map<String, Object>>> test(@PathVariable Long id) {
        return Mono.fromCallable(() -> service.test(id))
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::ok);
    }

    /** Re-crawl table/column metadata. */
    @PostMapping("/{id}/refresh-schema")
    public Mono<Result<Map<String, Object>>> refreshSchema(@PathVariable Long id) {
        return Mono.fromCallable(() -> service.refreshSchema(id))
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::ok);
    }

    /** Schema tree (tables with columns). */
    @GetMapping("/{id}/schema")
    public Mono<Result<Object>> schema(@PathVariable Long id) {
        return Mono.fromCallable(() -> service.schemaTree(id))
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::ok);
    }
}
