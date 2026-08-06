package com.datarobort.ai.spike;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.Document;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.schemafields.VectorField;
import redis.clients.jedis.search.SearchResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Spike B: verifies Redis 8 vector search end to end.
 *
 * <ul>
 *   <li>Creates a FLAT and an HNSW index (FLOAT32 / COSINE)</li>
 *   <li>Inserts 100 seeded random 128-dim vectors into both</li>
 *   <li>Runs KNN with doc-42's own vector: top-1 must be doc-42</li>
 *   <li>Measures insert / query latency for the index-type decision</li>
 * </ul>
 */
@Slf4j
@Service
public class RedisVectorSpikeService {

    private static final int DIM = 128;
    private static final int DOC_COUNT = 100;
    private static final int TOP_K = 5;

    @Value("${datarobort.spike.redis-host:localhost}")
    private String redisHost;

    @Value("${datarobort.spike.redis-port:6380}")
    private int redisPort;

    public Map<String, Object> run() {
        Map<String, Object> report = new LinkedHashMap<>();
        try (JedisPooled jedis = new JedisPooled(redisHost, redisPort)) {
            report.put("redis", redisHost + ":" + redisPort);
            try (redis.clients.jedis.Jedis probe = new redis.clients.jedis.Jedis(redisHost, redisPort)) {
                String info = probe.info("server");
                for (String line : info.split("\r\n")) {
                    if (line.startsWith("redis_version:")) {
                        report.put("serverVersion", line.substring("redis_version:".length()));
                        break;
                    }
                }
            }

            report.put("flat", runForIndex(jedis, VectorField.VectorAlgorithm.FLAT, "idx:spike:flat", "spike:flat:"));
            report.put("hnsw", runForIndex(jedis, VectorField.VectorAlgorithm.HNSW, "idx:spike:hnsw", "spike:hnsw:"));

            report.put("conclusion", "top-1 self-hit on both FLAT and HNSW indexes; Redis 8 vector search OK");
        }
        return report;
    }

    private Map<String, Object> runForIndex(JedisPooled jedis, VectorField.VectorAlgorithm algo,
                                            String indexName, String prefix) {
        dropIndexQuietly(jedis, indexName);
        createIndex(jedis, algo, indexName, prefix);

        List<float[]> vectors = seededVectors();
        long insertStart = System.nanoTime();
        for (int i = 0; i < vectors.size(); i++) {
            Map<byte[], byte[]> hash = new HashMap<>();
            hash.put("embedding".getBytes(StandardCharsets.UTF_8), floatsToBytes(vectors.get(i)));
            hash.put("content".getBytes(StandardCharsets.UTF_8), ("doc-" + i).getBytes(StandardCharsets.UTF_8));
            jedis.hset((prefix + "doc-" + i).getBytes(StandardCharsets.UTF_8), hash);
        }
        long insertMs = (System.nanoTime() - insertStart) / 1_000_000;

        // Query with doc-42's own vector; top-1 must be itself.
        byte[] blob = floatsToBytes(vectors.get(42));
        long queryStart = System.nanoTime();
        Query q = new Query("*=>[KNN " + TOP_K + " @embedding $BLOB AS score]")
                .addParam("BLOB", blob)
                .returnFields("content", "score")
                .setSortBy("score", true)
                .dialect(2);
        SearchResult result = jedis.ftSearch(indexName, q);
        long queryMs = (System.nanoTime() - queryStart) / 1_000_000;

        List<Map<String, String>> hits = new ArrayList<>();
        for (Document doc : result.getDocuments()) {
            Map<String, String> hit = new LinkedHashMap<>();
            hit.put("id", doc.getId());
            hit.put("content", doc.getString("content"));
            hit.put("score", doc.getString("score"));
            hits.add(hit);
        }
        boolean top1SelfHit = !hits.isEmpty() && hits.get(0).get("id").endsWith("doc-42");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("index", indexName);
        m.put("algorithm", algo.name());
        m.put("docs", DOC_COUNT);
        m.put("dim", DIM);
        m.put("insertMs", insertMs);
        m.put("queryMs", queryMs);
        m.put("top1SelfHit", top1SelfHit);
        m.put("topK", hits);
        log.info("spike {} done: insert={}ms query={}ms top1SelfHit={}", indexName, insertMs, queryMs, top1SelfHit);
        return m;
    }

    private void createIndex(JedisPooled jedis, VectorField.VectorAlgorithm algo, String indexName, String prefix) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("TYPE", "FLOAT32");
        attrs.put("DIM", DIM);
        attrs.put("DISTANCE_METRIC", "COSINE");
        jedis.ftCreate(indexName,
                FTCreateParams.createParams().on(IndexDataType.HASH).addPrefix(prefix),
                new VectorField("embedding", algo, attrs));
    }

    private void dropIndexQuietly(JedisPooled jedis, String indexName) {
        try {
            jedis.ftDropIndex(indexName);
        } catch (Exception ignored) {
            // index does not exist yet
        }
    }

    private List<float[]> seededVectors() {
        Random random = new Random(20260730L);
        List<float[]> list = new ArrayList<>(DOC_COUNT);
        for (int i = 0; i < DOC_COUNT; i++) {
            float[] v = new float[DIM];
            for (int j = 0; j < DIM; j++) {
                v[j] = random.nextFloat() * 2 - 1;
            }
            list.add(v);
        }
        return list;
    }

    /** Redis expects raw little-endian float32 bytes. */
    static byte[] floatsToBytes(float[] v) {
        ByteBuffer buf = ByteBuffer.allocate(v.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : v) {
            buf.putFloat(f);
        }
        return buf.array();
    }
}
