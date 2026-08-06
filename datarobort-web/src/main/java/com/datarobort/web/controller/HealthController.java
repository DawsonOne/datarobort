package com.datarobort.web.controller;

import com.datarobort.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Liveness / readiness probe.
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public Mono<Result<Map<String, Object>>> health() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "UP");
        info.put("service", "datarobort");
        info.put("timestamp", Instant.now().toString());
        return Mono.just(Result.ok(info));
    }
}
