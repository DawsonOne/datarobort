package com.datarobort.web.controller;

import com.datarobort.common.result.Result;
import com.datarobort.core.entity.SemanticModel;
import com.datarobort.web.service.SemanticModelService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@RestController
@RequestMapping("/api/semantic-models")
public class SemanticModelController {

    private final SemanticModelService service;

    public SemanticModelController(SemanticModelService service) { this.service = service; }

    @GetMapping
    public Mono<Result<List<SemanticModel>>> list(@RequestParam(required = false) Long dsId) {
        return Mono.fromCallable(() -> dsId != null ? service.listByDsId(dsId) : java.util.Collections.<SemanticModel>emptyList())
                .subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }

    @GetMapping("/{id}")
    public Mono<Result<SemanticModel>> detail(@PathVariable Long id) {
        return Mono.fromCallable(() -> service.detail(id)).subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }

    @PostMapping
    public Mono<Result<SemanticModel>> create(@RequestBody SemanticModel sm) {
        return Mono.fromCallable(() -> service.create(sm)).subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }

    @PutMapping("/{id}")
    public Mono<Result<SemanticModel>> update(@PathVariable Long id, @RequestBody SemanticModel sm) {
        return Mono.fromCallable(() -> service.update(id, sm)).subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }

    @DeleteMapping("/{id}")
    public Mono<Result<Void>> delete(@PathVariable Long id) {
        return Mono.fromRunnable(() -> service.delete(id)).subscribeOn(Schedulers.boundedElastic()).then(Mono.just(Result.ok()));
    }
}
