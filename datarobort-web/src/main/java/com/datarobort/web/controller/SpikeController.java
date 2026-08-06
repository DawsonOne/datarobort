package com.datarobort.web.controller;

import com.datarobort.ai.spike.RedisVectorSpikeService;
import com.datarobort.common.result.Result;
import com.datarobort.sandbox.SandboxSpikeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * On-demand Spike triggers (P0 technical verification).
 */
@RestController
@RequestMapping("/spike")
public class SpikeController {

    private final RedisVectorSpikeService vectorSpike;
    private final SandboxSpikeService sandboxSpike;

    public SpikeController(RedisVectorSpikeService vectorSpike, SandboxSpikeService sandboxSpike) {
        this.vectorSpike = vectorSpike;
        this.sandboxSpike = sandboxSpike;
    }

    /** Spike B: Redis 8 vector search verification. */
    @PostMapping("/redis-vector/run")
    public Mono<Result<Map<String, Object>>> runVectorSpike() {
        return Mono.fromCallable(vectorSpike::run)
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::ok);
    }

    /** Spike C: Docker Python sandbox verification. */
    @PostMapping("/sandbox/run")
    public Mono<Result<Map<String, Object>>> runSandboxSpike() {
        return Mono.fromCallable(sandboxSpike::run)
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::ok);
    }
}
