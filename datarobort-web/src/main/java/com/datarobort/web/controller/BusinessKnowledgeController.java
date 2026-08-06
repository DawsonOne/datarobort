package com.datarobort.web.controller;

import com.datarobort.common.result.Result;
import com.datarobort.core.entity.BusinessKnowledge;
import com.datarobort.web.service.BusinessKnowledgeService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@RestController
@RequestMapping("/api/business-knowledge")
public class BusinessKnowledgeController {

    private final BusinessKnowledgeService service;

    public BusinessKnowledgeController(BusinessKnowledgeService service) { this.service = service; }

    @GetMapping
    public Mono<Result<List<BusinessKnowledge>>> list() {
        return Mono.fromCallable(service::list).subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }

    @GetMapping("/{id}")
    public Mono<Result<BusinessKnowledge>> detail(@PathVariable Long id) {
        return Mono.fromCallable(() -> service.detail(id)).subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }

    @PostMapping
    public Mono<Result<BusinessKnowledge>> create(@RequestBody BusinessKnowledge bk) {
        return Mono.fromCallable(() -> service.create(bk)).subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }

    @PutMapping("/{id}")
    public Mono<Result<BusinessKnowledge>> update(@PathVariable Long id, @RequestBody BusinessKnowledge bk) {
        return Mono.fromCallable(() -> service.update(id, bk)).subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }

    @DeleteMapping("/{id}")
    public Mono<Result<Void>> delete(@PathVariable Long id) {
        return Mono.fromRunnable(() -> service.delete(id)).subscribeOn(Schedulers.boundedElastic()).then(Mono.just(Result.ok()));
    }
}
