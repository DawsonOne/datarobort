package com.datarobort.ai.vector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.*;
import redis.clients.jedis.search.schemafields.VectorField;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Redis 8 vector store backed by RediSearch.
 * Each knowledge type lives in its own index:
 * {@code idx:knowledge_chunk}, {@code idx:business_knowledge}, {@code idx:semantic_model}.
 */
@Slf4j
@Service
public class VectorStoreService {

    private static final int M = 16;
    private static final int EF_CONSTRUCTION = 200;

    @Value("${datarobort.spike.redis-host:localhost}")
    private String redisHost;

    @Value("${datarobort.spike.redis-port:6380}")
    private int redisPort;

    private volatile JedisPooled jedis;

    private JedisPooled jedis() {
        if (jedis == null) {
            synchronized (this) {
                if (jedis == null) {
                    jedis = new JedisPooled(redisHost, redisPort);
                }
            }
        }
        return jedis;
    }

    /** Create or recreate a vector index for the given dimension. */
    public void createIndex(String indexName, String prefix, int dimension) {
        dropIndexQuietly(indexName);
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("TYPE", "FLOAT32");
        attrs.put("DIM", dimension);
        attrs.put("DISTANCE_METRIC", "COSINE");
        attrs.put("M", M);
        attrs.put("EF_CONSTRUCTION", EF_CONSTRUCTION);
        jedis().ftCreate(indexName,
                FTCreateParams.createParams().on(IndexDataType.HASH).addPrefix(prefix),
                new VectorField("embedding", VectorField.VectorAlgorithm.HNSW, attrs));
        log.info("created vector index {} with dim={}", indexName, dimension);
    }

    /** Insert a vector with metadata into the index. */
    public void insert(String key, float[] vector, Map<String, String> metadata) {
        Map<byte[], byte[]> hash = new HashMap<>();
        hash.put("embedding".getBytes(StandardCharsets.UTF_8), floatsToBytes(vector));
        for (Map.Entry<String, String> e : metadata.entrySet()) {
            if (e.getValue() != null) {
                hash.put(e.getKey().getBytes(StandardCharsets.UTF_8),
                        e.getValue().getBytes(StandardCharsets.UTF_8));
            }
        }
        jedis().hset(key.getBytes(StandardCharsets.UTF_8), hash);
    }

    /** Batch insert vectors. */
    public void insertBatch(List<VectorEntry> entries) {
        for (VectorEntry e : entries) {
            insert(e.key, e.vector, e.metadata);
        }
    }

    /** KNN search returning top-K results with scores. */
    public List<VectorHit> search(String indexName, float[] queryVector, int topK) {
        byte[] blob = floatsToBytes(queryVector);
        Query q = new Query("*=>[KNN " + topK + " @embedding $BLOB AS score]")
                .addParam("BLOB", blob)
                .returnFields("content", "term", "synonyms", "score")
                .setSortBy("score", true)
                .dialect(2);
        SearchResult result = jedis().ftSearch(indexName, q);
        List<VectorHit> hits = new ArrayList<>();
        for (redis.clients.jedis.search.Document doc : result.getDocuments()) {
            VectorHit hit = new VectorHit();
            hit.setId(doc.getId());
            hit.setScore(parseScore(doc.getString("score")));
            hit.setContent(doc.getString("content"));
            hit.setTerm(doc.getString("term"));
            hit.setSynonyms(doc.getString("synonyms"));
            hits.add(hit);
        }
        return hits;
    }

    /** Delete a single key. */
    public void delete(String key) {
        jedis().del(key);
    }

    /** Delete all keys matching a prefix. Slow for large datasets; use with caution. */
    public void deleteByPrefix(String prefix) {
        var keys = jedis().keys(prefix + "*");
        if (!keys.isEmpty()) {
            jedis().del(keys.toArray(new String[0]));
        }
    }

    /** Embed a single text and return the float vector. */
    public float[] embed(EmbeddingModel model, String text) {
        return model.embed(text);
    }

    /** Embed a batch of texts. */
    public List<float[]> embedBatch(EmbeddingModel model, List<String> texts) {
        return model.embed(texts);
    }

    public void dropIndexQuietly(String indexName) {
        try {
            jedis().ftDropIndex(indexName);
        } catch (Exception ignored) {
        }
    }

    public boolean indexExists(String indexName) {
        try {
            jedis().ftInfo(indexName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // --- helpers ---

    static byte[] floatsToBytes(float[] v) {
        ByteBuffer buf = ByteBuffer.allocate(v.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : v) buf.putFloat(f);
        return buf.array();
    }

    private double parseScore(String s) {
        if (s == null) return 0;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
    }

    // --- inner types ---

    @lombok.Data
    public static class VectorEntry {
        private String key;
        private float[] vector;
        private Map<String, String> metadata;
    }

    @lombok.Data
    public static class VectorHit {
        private String id;
        private double score;
        private String content;
        private String term;
        private String synonyms;
    }
}
