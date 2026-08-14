package com.datarobort.web.controller;

import com.datarobort.common.result.Result;
import com.datarobort.core.entity.Conversation;
import com.datarobort.core.entity.Message;
import com.datarobort.web.service.ConversationService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService service;

    public ConversationController(ConversationService service) { this.service = service; }

    @GetMapping
    public Mono<Result<List<Conversation>>> list(@RequestParam(required = false) Long agentId) {
        return Mono.fromCallable(() -> service.list(agentId)).subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }

    @GetMapping("/{id}")
    public Mono<Result<Conversation>> detail(@PathVariable Long id) {
        return Mono.fromCallable(() -> service.detail(id)).subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }

    @GetMapping("/{id}/messages")
    public Mono<Result<List<Message>>> messages(@PathVariable Long id) {
        return Mono.fromCallable(() -> service.messages(id)).subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }

    @PostMapping
    public Mono<Result<Conversation>> create(@RequestBody Map<String, Object> body) {
        Long agentId = toLong(body.get("agentId"));
        String title = body.get("title") != null ? String.valueOf(body.get("title")) : null;
        return Mono.fromCallable(() -> service.create(agentId, title))
                .subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }

    @PutMapping("/{id}")
    public Mono<Result<Conversation>> updateTitle(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Mono.fromCallable(() -> service.updateTitle(id, body.get("title")))
                .subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }

    @DeleteMapping("/{id}")
    public Mono<Result<Void>> delete(@PathVariable Long id) {
        return Mono.fromRunnable(() -> service.delete(id)).subscribeOn(Schedulers.boundedElastic()).then(Mono.just(Result.ok()));
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
