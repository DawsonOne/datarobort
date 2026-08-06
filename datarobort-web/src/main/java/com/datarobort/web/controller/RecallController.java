package com.datarobort.web.controller;

import com.datarobort.common.result.Result;
import com.datarobort.web.service.RecallService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recall")
public class RecallController {

    private final RecallService recallService;

    public RecallController(RecallService recallService) { this.recallService = recallService; }

    @PostMapping
    public Mono<Result<List<RecallService.RecallItem>>> recall(@RequestBody Map<String, Object> body) {
        String query = (String) body.get("query");
        int topK = body.get("topK") instanceof Number n ? n.intValue() : 10;
        double threshold = body.get("threshold") instanceof Number n ? n.doubleValue() : 0.3;
        return Mono.fromCallable(() -> recallService.recall(query, topK, threshold))
                .subscribeOn(Schedulers.boundedElastic()).map(Result::ok);
    }
}
