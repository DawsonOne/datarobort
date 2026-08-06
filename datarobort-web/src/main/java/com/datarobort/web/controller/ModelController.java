package com.datarobort.web.controller;

import com.datarobort.common.result.Result;
import com.datarobort.core.entity.ModelConfig;
import com.datarobort.web.service.ModelConfigService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * Model configuration APIs.
 */
@RestController
@RequestMapping("/api/models")
public class ModelController {

    private final ModelConfigService service;

    public ModelController(ModelConfigService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<Result<List<ModelConfig>>> list(@RequestParam(required = false) String type) {
        return Mono.fromCallable(() -> service.list(type))
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::ok);
    }

    @GetMapping("/{id}")
    public Mono<Result<ModelConfig>> detail(@PathVariable Long id) {
        return Mono.fromCallable(() -> service.detail(id))
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::ok);
    }

    @PostMapping
    public Mono<Result<ModelConfig>> create(@RequestBody ModelConfig m) {
        return Mono.fromCallable(() -> service.create(m))
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::ok);
    }

    @PutMapping("/{id}")
    public Mono<Result<ModelConfig>> update(@PathVariable Long id, @RequestBody ModelConfig m) {
        return Mono.fromCallable(() -> service.update(id, m))
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::ok);
    }

    @DeleteMapping("/{id}")
    public Mono<Result<Void>> delete(@PathVariable Long id) {
        return Mono.fromRunnable(() -> service.delete(id))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.just(Result.ok()));
    }

    @PostMapping("/{id}/default")
    public Mono<Result<ModelConfig>> setDefault(@PathVariable Long id) {
        return Mono.fromCallable(() -> service.setDefault(id))
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::ok);
    }

    /** Connectivity test: one real call to the provider. */
    @PostMapping("/{id}/test")
    public Mono<Result<Map<String, Object>>> test(@PathVariable Long id) {
        return Mono.fromCallable(() -> service.test(id))
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::ok);
    }
}
