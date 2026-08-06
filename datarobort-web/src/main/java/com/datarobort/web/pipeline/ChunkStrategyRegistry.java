package com.datarobort.web.pipeline;

import com.datarobort.ai.chunk.ChunkStrategy;
import com.datarobort.common.error.ErrorCode;
import com.datarobort.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ChunkStrategyRegistry {

    private final Map<String, ChunkStrategy> map;

    public ChunkStrategyRegistry(List<ChunkStrategy> strategies) {
        this.map = strategies.stream()
                .collect(Collectors.toMap(ChunkStrategy::key, Function.identity()));
    }

    public ChunkStrategy get(String key) {
        ChunkStrategy s = map.get(key == null ? "fixed" : key);
        if (s == null) throw new BizException(ErrorCode.PARAM_INVALID, "不支持的分块策略: " + key);
        return s;
    }
}
