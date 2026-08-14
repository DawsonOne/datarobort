package com.datarobort.web.controller;

import com.datarobort.common.result.Result;
import com.datarobort.core.entity.Agent;
import com.datarobort.web.service.AgentService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentService service;

    public AgentController(AgentService service) { this.service = service; }

    @GetMapping
    public Mono<Result<List<Agent>>> list() {
        return Mono.fromCallable(service::list).subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }

    @GetMapping("/{id}")
    public Mono<Result<Agent>> detail(@PathVariable Long id) {
        return Mono.fromCallable(() -> service.detail(id)).subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }

    @PostMapping
    public Mono<Result<Agent>> create(@RequestBody Agent agent) {
        return Mono.fromCallable(() -> service.create(agent)).subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }

    @PutMapping("/{id}")
    public Mono<Result<Agent>> update(@PathVariable Long id, @RequestBody Agent agent) {
        return Mono.fromCallable(() -> service.update(id, agent)).subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }

    @DeleteMapping("/{id}")
    public Mono<Result<Void>> delete(@PathVariable Long id) {
        return Mono.fromRunnable(() -> service.delete(id)).subscribeOn(Schedulers.boundedElastic()).then(Mono.just(Result.ok()));
    }

    /** Body: {"status": 1} publish, {"status": 0} draft. */
    @PutMapping("/{id}/publish")
    public Mono<Result<Agent>> publish(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        return Mono.fromCallable(() -> service.publish(id, body.get("status")))
                .subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }
}
