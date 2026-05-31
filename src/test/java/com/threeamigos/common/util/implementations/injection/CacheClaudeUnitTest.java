package com.threeamigos.common.util.implementations.injection;

import com.threeamigos.common.util.implementations.collections.Cache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * These unit tests validate the functionality of the Cache class.
 * Generated with Claude 3.5
 */
@DisplayName( "Cache unit test")
class CacheUnitTest {

    @Test
    @DisplayName("Default constructor creates cache with default settings")
    void testDefaultConstructor() {
        Cache<String, String> cache = new Cache<>();
        assertNotNull(cache);
        assertEquals(0.0, cache.getCacheHitRate());
    }

    @Test
    @DisplayName("Parameterized constructor creates cache with custom settings")
    void testParameterizedConstructor() {
        Cache<String, String> cache = new Cache<>(100, 8, 0.6f);
        assertNotNull(cache);
        assertEquals(0.0, cache.getCacheHitRate());
    }

    @Test
    @DisplayName("computeIfAbsent returns computed value on cache miss")
    void testComputeIfAbsentCacheMiss() {
        Cache<String, String> cache = new Cache<>();
        String result = cache.computeIfAbsent("key1", () -> "value1");
        assertEquals("value1", result);
        assertEquals(0.0, cache.getCacheHitRate());
    }

    @Test
    @DisplayName("computeIfAbsent returns cached value on cache hit")
    void testComputeIfAbsentCacheHit() {
        Cache<String, String> cache = new Cache<>();
        cache.computeIfAbsent("key1", () -> "value1");
        String result = cache.computeIfAbsent("key1", () -> "value2");
        assertEquals("value1", result);
        assertEquals(0.5, cache.getCacheHitRate());
    }

    @Test
    @DisplayName("getCacheHitRate returns 0 when no operations")
    void testGetCacheHitRateNoOperations() {
        Cache<String, String> cache = new Cache<>();
        assertEquals(0.0, cache.getCacheHitRate());
    }

    @Test
    @DisplayName("getCacheHitRate calculates correctly with multiple hits and misses")
    void testGetCacheHitRateCalculation() {
        Cache<String, String> cache = new Cache<>();
        cache.computeIfAbsent("key1", () -> "value1"); // miss
        cache.computeIfAbsent("key2", () -> "value2"); // miss
        cache.computeIfAbsent("key1", () -> "value1"); // hit
        cache.computeIfAbsent("key2", () -> "value2"); // hit
        cache.computeIfAbsent("key1", () -> "value1"); // hit
        assertEquals(0.6, cache.getCacheHitRate(), 0.01);
    }

    @Test
    @DisplayName("Cache evicts eldest entry when max size exceeded")
    void testCacheEviction() {
        Cache<Integer, String> cache = new Cache<>(3, 3, 0.75f);
        cache.computeIfAbsent(1, () -> "value1");
        cache.computeIfAbsent(2, () -> "value2");
        cache.computeIfAbsent(3, () -> "value3");
        cache.computeIfAbsent(4, () -> "value4");

        // Key 1 should be evicted, so accessing it should be a miss
        AtomicInteger callCount = new AtomicInteger(0);
        cache.computeIfAbsent(1, () -> {
            callCount.incrementAndGet();
            return "value1-new";
        });
        assertEquals(1, callCount.get());
    }

    @Test
    @DisplayName("Cache handles null values")
    void testCacheWithNullValue() {
        Cache<String, String> cache = new Cache<>();
        String result = cache.computeIfAbsent("key1", () -> null);
        assertNull(result);
    }

    @Test
    @DisplayName("Cache is thread-safe")
    void testCacheThreadSafety() throws InterruptedException {
        Cache<Integer, Integer> cache = new Cache<>();
        int threadCount = 10;
        int operationsPerThread = 100;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        cache.computeIfAbsent(j % 10, () -> 0);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        // Verify that the cache hit rate is reasonable
        assertTrue(cache.getCacheHitRate() > 0.5);
    }

    @Test
    @DisplayName("Supplier function is only called on cache miss")
    void testSupplierCalledOnlyOnMiss() {
        Cache<String, String> cache = new Cache<>();
        AtomicInteger callCount = new AtomicInteger(0);

        cache.computeIfAbsent("key1", () -> {
            callCount.incrementAndGet();
            return "value1";
        });

        cache.computeIfAbsent("key1", () -> {
            callCount.incrementAndGet();
            return "value2";
        });

        assertEquals(1, callCount.get());
    }

    @Test
    @DisplayName("Cache maintains LRU order")
    void testLRUOrder() {
        Cache<Integer, String> cache = new Cache<>(3, 3, 0.75f);

        // Fill cache to exactly max capacity (3 entries)
        cache.computeIfAbsent(1, () -> "value1");
        cache.computeIfAbsent(2, () -> "value2");
        cache.computeIfAbsent(3, () -> "value3");

        // Cache should hold exactly 3 entries (maxCacheSize)
        assertEquals(3, cache.size());

        // Access key 1 to make it recently used
        // LRU order is now: 2 (oldest), 3, 1 (most recent)
        cache.computeIfAbsent(1, () -> "value1");

        // Add key 4, which should evict key 2 (least recently used)
        cache.computeIfAbsent(4, () -> "value4");

        // Cache should still hold exactly 3 entries
        assertEquals(3, cache.size());

        // Keys 1, 3, and 4 should be cached (key 2 was evicted)
        AtomicInteger callCount = new AtomicInteger(0);
        cache.computeIfAbsent(1, () -> {
            callCount.incrementAndGet();
            return "value1-new";
        });
        cache.computeIfAbsent(3, () -> {
            callCount.incrementAndGet();
            return "value3-new";
        });
        cache.computeIfAbsent(4, () -> {
            callCount.incrementAndGet();
            return "value4-new";
        });
        assertEquals(0, callCount.get());

        // Key 2 should require recomputation (it was evicted)
        cache.computeIfAbsent(2, () -> {
            callCount.incrementAndGet();
            return "value2-new";
        });
        assertEquals(1, callCount.get());
    }

    @Test
    @DisplayName("Cache works with different key and value types")
    void testDifferentTypes() {
        Cache<Integer, Long> cache = new Cache<>();
        Long result = cache.computeIfAbsent(42, () -> 100L);
        assertEquals(100L, result);

        Long cachedResult = cache.computeIfAbsent(42, () -> 200L);
        assertEquals(100L, cachedResult);
    }

    @Test
    @DisplayName("Cache with maxSize 1 works correctly")
    void testCacheWithMaxSizeOne() {
        Cache<String, String> cache = new Cache<>(1, 1, 0.75f);
        cache.computeIfAbsent("key1", () -> "value1");
        cache.computeIfAbsent("key2", () -> "value2");

        // key1 should be evicted
        AtomicInteger callCount = new AtomicInteger(0);
        cache.computeIfAbsent("key1", () -> {
            callCount.incrementAndGet();
            return "value1-new";
        });
        assertEquals(1, callCount.get());
    }

    @Test
    @DisplayName("Constructor throws exception for invalid maxCacheSize")
    void testConstructorInvalidMaxCacheSize() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> new Cache<String, String>(0, 16, 0.75f));
        assertTrue(exception.getMessage().contains("maxCacheSize must be positive"));

        exception = assertThrows(IllegalArgumentException.class, () -> new Cache<String, String>(-1, 16, 0.75f));
        assertTrue(exception.getMessage().contains("maxCacheSize must be positive"));
    }

    @Test
    @DisplayName("Constructor throws exception for invalid initialCapacity")
    void testConstructorInvalidInitialCapacity() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> new Cache<String, String>(100, 0, 0.75f));
        assertTrue(exception.getMessage().contains("initialCapacity must be positive"));

        exception = assertThrows(IllegalArgumentException.class, () -> new Cache<String, String>(100, -5, 0.75f));
        assertTrue(exception.getMessage().contains("initialCapacity must be positive"));
    }

    @Test
    @DisplayName("Constructor throws exception for invalid loadFactor")
    void testConstructorInvalidLoadFactor() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> new Cache<String, String>(100, 16, 0.0f));
        assertTrue(exception.getMessage().contains("loadFactor must be in (0, 1)"));

        exception = assertThrows(IllegalArgumentException.class, () -> new Cache<String, String>(100, 16, 1.0f));
        assertTrue(exception.getMessage().contains("loadFactor must be in (0, 1)"));

        exception = assertThrows(IllegalArgumentException.class, () -> new Cache<String, String>(100, 16, -0.5f));
        assertTrue(exception.getMessage().contains("loadFactor must be in (0, 1)"));

        exception = assertThrows(IllegalArgumentException.class, () -> new Cache<String, String>(100, 16, 1.5f));
        assertTrue(exception.getMessage().contains("loadFactor must be in (0, 1)"));
    }

    @Test
    @DisplayName("computeIfAbsent throws NullPointerException for null key")
    void testComputeIfAbsentNullKey() {
        Cache<String, String> cache = new Cache<>();
        Exception exception = assertThrows(NullPointerException.class, () -> cache.computeIfAbsent(null, () -> "value"));
        assertTrue(exception.getMessage().contains("key cannot be null"));
    }

    @Test
    @DisplayName("computeIfAbsent throws NullPointerException for null supplier")
    void testComputeIfAbsentNullSupplier() {
        Cache<String, String> cache = new Cache<>();
        Exception exception = assertThrows(NullPointerException.class, () -> cache.computeIfAbsent("key", null));
        assertTrue(exception.getMessage().contains("supplierFunction cannot be null"));
    }

    @Test
    @DisplayName("Null values are properly cached and retrieved")
    void testNullValueCaching() {
        Cache<String, String> cache = new Cache<>();
        AtomicInteger callCount = new AtomicInteger(0);

        // First access should compute null
        String result1 = cache.computeIfAbsent("key1", () -> {
            callCount.incrementAndGet();
            return null;
        });
        assertNull(result1);
        assertEquals(1, callCount.get());
        assertEquals(1, cache.getMissCount());
        assertEquals(0, cache.getHitCount());

        // Second access should retrieve cached null (not recompute)
        String result2 = cache.computeIfAbsent("key1", () -> {
            callCount.incrementAndGet();
            return null;
        });
        assertNull(result2);
        assertEquals(1, callCount.get()); // Should still be 1, not 2
        assertEquals(1, cache.getMissCount());
        assertEquals(1, cache.getHitCount());
    }

    @Test
    @DisplayName("getHitCount returns correct count")
    void testGetHitCount() {
        Cache<String, String> cache = new Cache<>();
        assertEquals(0, cache.getHitCount());

        cache.computeIfAbsent("key1", () -> "value1"); // miss
        assertEquals(0, cache.getHitCount());

        cache.computeIfAbsent("key1", () -> "value1"); // hit
        assertEquals(1, cache.getHitCount());

        cache.computeIfAbsent("key1", () -> "value1"); // hit
        assertEquals(2, cache.getHitCount());
    }

    @Test
    @DisplayName("getMissCount returns correct count")
    void testGetMissCount() {
        Cache<String, String> cache = new Cache<>();
        assertEquals(0, cache.getMissCount());

        cache.computeIfAbsent("key1", () -> "value1"); // miss
        assertEquals(1, cache.getMissCount());

        cache.computeIfAbsent("key2", () -> "value2"); // miss
        assertEquals(2, cache.getMissCount());

        cache.computeIfAbsent("key1", () -> "value1"); // hit
        assertEquals(2, cache.getMissCount()); // Should still be 2
    }

    @Test
    @DisplayName("size returns correct cache size")
    void testSize() {
        Cache<String, String> cache = new Cache<>();
        assertEquals(0, cache.size());

        cache.computeIfAbsent("key1", () -> "value1");
        assertEquals(1, cache.size());

        cache.computeIfAbsent("key2", () -> "value2");
        assertEquals(2, cache.size());

        cache.computeIfAbsent("key1", () -> "value1"); // hit, no size change
        assertEquals(2, cache.size());
    }

    @Test
    @DisplayName("clear removes all entries but preserves statistics")
    void testClear() {
        Cache<String, String> cache = new Cache<>();
        cache.computeIfAbsent("key1", () -> "value1");
        cache.computeIfAbsent("key2", () -> "value2");
        cache.computeIfAbsent("key1", () -> "value1"); // hit

        assertEquals(2, cache.size());
        assertEquals(1, cache.getHitCount());
        assertEquals(2, cache.getMissCount());

        cache.clear();

        assertEquals(0, cache.size());
        // Statistics should be preserved
        assertEquals(1, cache.getHitCount());
        assertEquals(2, cache.getMissCount());
    }

    @Test
    @DisplayName("invalidate removes specific entry")
    void testInvalidate() {
        Cache<String, String> cache = new Cache<>();
        cache.computeIfAbsent("key1", () -> "value1");
        cache.computeIfAbsent("key2", () -> "value2");

        assertEquals(2, cache.size());

        cache.invalidate("key1");

        assertEquals(1, cache.size());

        // Accessing key1 should now be a miss
        AtomicInteger callCount = new AtomicInteger(0);
        cache.computeIfAbsent("key1", () -> {
            callCount.incrementAndGet();
            return "value1-new";
        });
        assertEquals(1, callCount.get());
    }

    @Test
    @DisplayName("invalidateAll with predicate removes matching entries")
    void testInvalidateAll() {
        Cache<Integer, String> cache = new Cache<>();
        cache.computeIfAbsent(1, () -> "value1");
        cache.computeIfAbsent(2, () -> "value2");
        cache.computeIfAbsent(3, () -> "value3");
        cache.computeIfAbsent(4, () -> "value4");

        assertEquals(4, cache.size());

        // Remove all even keys
        cache.invalidateAll(key -> key % 2 == 0);

        assertEquals(2, cache.size());

        // Keys 1 and 3 should still be cached
        AtomicInteger callCount = new AtomicInteger(0);
        cache.computeIfAbsent(1, () -> {
            callCount.incrementAndGet();
            return "value1-new";
        });
        cache.computeIfAbsent(3, () -> {
            callCount.incrementAndGet();
            return "value3-new";
        });
        assertEquals(0, callCount.get());

        // Keys 2 and 4 should require recomputation
        cache.computeIfAbsent(2, () -> {
            callCount.incrementAndGet();
            return "value2-new";
        });
        cache.computeIfAbsent(4, () -> {
            callCount.incrementAndGet();
            return "value4-new";
        });
        assertEquals(2, callCount.get());
    }

    @Test
    @DisplayName("Supplier exception propagates correctly")
    void testSupplierException() {
        Cache<String, String> cache = new Cache<>();

        Exception exception = assertThrows(RuntimeException.class, () -> cache.computeIfAbsent("key1", () -> {
            throw new RuntimeException("Computation failed");
        }));

        assertEquals("Computation failed", exception.getMessage());
        // Miss should still be recorded
        assertEquals(1, cache.getMissCount());
        assertEquals(0, cache.size()); // Nothing should be cached
    }

    @Test
    @DisplayName("Double-checked locking prevents duplicate computation")
    void testDoubleCheckedLocking() throws InterruptedException {
        Cache<String, String> cache = new Cache<>();
        AtomicInteger computationCount = new AtomicInteger(0);
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // All threads try to compute the same key simultaneously
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    cache.computeIfAbsent("sameKey", () -> {
                        computationCount.incrementAndGet();
                        // Simulate expensive computation
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return "value";
                    });
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        // Computation should happen exactly once
        assertEquals(1, computationCount.get());
        assertEquals(1, cache.getMissCount());
        assertEquals(9, cache.getHitCount()); // 9 hits from the other threads
    }

    @Test
    @DisplayName("Cache handles boundary at exact max size")
    void testCacheBoundaryAtMaxSize() {
        Cache<Integer, String> cache = new Cache<>(3, 3, 0.75f);

        // Fill cache to exactly max size
        cache.computeIfAbsent(1, () -> "value1");
        assertEquals(1, cache.size());

        cache.computeIfAbsent(2, () -> "value2");
        assertEquals(2, cache.size());

        cache.computeIfAbsent(3, () -> "value3");
        // Cache holds exactly maxCacheSize (3) entries
        assertEquals(3, cache.size());

        // All three keys should be cached
        AtomicInteger callCount = new AtomicInteger(0);
        cache.computeIfAbsent(1, () -> {
            callCount.incrementAndGet();
            return "value1-new";
        });
        cache.computeIfAbsent(2, () -> {
            callCount.incrementAndGet();
            return "value2-new";
        });
        cache.computeIfAbsent(3, () -> {
            callCount.incrementAndGet();
            return "value3-new";
        });
        // No recomputation should have occurred - all were cache hits
        assertEquals(0, callCount.get());

        // Adding a 4th entry should evict the least recently used (key 1)
        cache.computeIfAbsent(4, () -> "value4");
        assertEquals(3, cache.size());

        // Key 1 should now be evicted
        cache.computeIfAbsent(1, () -> {
            callCount.incrementAndGet();
            return "value1-new";
        });
        assertEquals(1, callCount.get());

        // Keys 3 and 4 should still be cached (2 was evicted when we added 1)
        cache.computeIfAbsent(3, () -> {
            callCount.incrementAndGet();
            return "value3-new";
        });
        cache.computeIfAbsent(4, () -> {
            callCount.incrementAndGet();
            return "value4-new";
        });
        assertEquals(1, callCount.get());
    }

    @Test
    @DisplayName("Cache hit rate is correct with only hits")
    void testHitRateAllHits() {
        Cache<String, String> cache = new Cache<>();
        cache.computeIfAbsent("key1", () -> "value1"); // miss

        for (int i = 0; i < 99; i++) {
            cache.computeIfAbsent("key1", () -> "value1"); // hit
        }

        assertEquals(0.99, cache.getCacheHitRate(), 0.001);
    }

    @Test
    @DisplayName("Cache hit rate is correct with only misses")
    void testHitRateAllMisses() {
        Cache<String, String> cache = new Cache<>();

        for (int i = 0; i < 10; i++) {
            final int val = i;
            cache.computeIfAbsent("key" + val, () -> "value" + val); // all misses
        }

        assertEquals(0.0, cache.getCacheHitRate(), 0.001);
    }

    @Test
    @DisplayName("invalidate non-existent key does not throw exception")
    void testInvalidateNonExistentKey() {
        Cache<String, String> cache = new Cache<>();
        cache.computeIfAbsent("key1", () -> "value1");

        assertDoesNotThrow(() -> cache.invalidate("nonExistent"));
        assertEquals(1, cache.size());
    }

    @Test
    @DisplayName("invalidateAll with no matches leaves cache unchanged")
    void testInvalidateAllNoMatches() {
        Cache<Integer, String> cache = new Cache<>();
        cache.computeIfAbsent(1, () -> "value1");
        cache.computeIfAbsent(2, () -> "value2");

        cache.invalidateAll(key -> key > 10);

        assertEquals(2, cache.size());
    }

    @Test
    @DisplayName("invalidateAll with all matches clears entire cache")
    void testInvalidateAllMatches() {
        Cache<Integer, String> cache = new Cache<>();
        cache.computeIfAbsent(1, () -> "value1");
        cache.computeIfAbsent(2, () -> "value2");
        cache.computeIfAbsent(3, () -> "value3");

        cache.invalidateAll(key -> true);

        assertEquals(0, cache.size());
    }
}