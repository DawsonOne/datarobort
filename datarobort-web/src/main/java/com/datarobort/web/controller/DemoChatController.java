package com.datarobort.web.controller;

import com.datarobort.ai.spike.ModelProbeService;
import com.datarobort.common.result.Result;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Spike A demo endpoint: streaming chat against the configured
 * OpenAI-compatible model.
 *
 * <pre>curl -N "http://localhost:8080/demo/chat?q=你好"</pre>
 */
@RestController
@RequestMapping("/demo")
public class DemoChatController {

    private final ModelProbeService probeService;

    public DemoChatController(ModelProbeService probeService) {
        this.probeService = probeService;
    }

    /** SSE streaming chat. */
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam String q) {
        return probeService.chatStream(q);
    }

    /** Blocking connectivity probe, wrapped in the unified Result. */
    @GetMapping("/ping")
    public Mono<Result<String>> ping() {
        return Mono.fromCallable(() -> probeService.chat("reply with the single word: pong"))
                .map(Result::ok);
    }
}
