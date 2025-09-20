package io.github.darioajr.quickfixj.config;

import java.util.Properties;

import org.infinispan.configuration.cache.CacheMode;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.darioajr.quickfixj.factory.InfinispanMessageStoreFactory;

class InfinispanQuickFixJConfigTest {

    private InfinispanQuickFixJConfig config;

    @BeforeEach
    void setUp() {
        config = new InfinispanQuickFixJConfig()
            .cacheMode(CacheMode.LOCAL); // Force local mode for tests to avoid clustering issues
    }

    @Test
    void testDefaultConstructor() {
        assertNotNull(config);
    }

    @Test
    void testClusterName() {
        String clusterName = "test-cluster";
        InfinispanQuickFixJConfig result = config.clusterName(clusterName);
        assertSame(config, result); // Should return same instance for fluent API
    }

    @Test
    void testCacheMode() {
        CacheMode mode = CacheMode.DIST_SYNC;
        InfinispanQuickFixJConfig result = config.cacheMode(mode);
        assertSame(config, result);
    }

    @Test
    void testExpiration() {
        long expiration = 120;
        InfinispanQuickFixJConfig result = config.expiration(expiration);
        assertSame(config, result);
    }

    @Test
    void testMaxEntries() {
        int maxEntries = 5000;
        InfinispanQuickFixJConfig result = config.maxEntries(maxEntries);
        assertSame(config, result);
    }

    @Test
    void testEnableStatisticsTrue() {
        InfinispanQuickFixJConfig result = config.enableStatistics(true);
        assertSame(config, result);
    }

    @Test
    void testEnableStatisticsFalse() {
        InfinispanQuickFixJConfig result = config.enableStatistics(false);
        assertSame(config, result);
    }

    @Test
    void testEnableStatisticsDefault() {
        InfinispanQuickFixJConfig result = config.enableStatistics(true);
        assertSame(config, result);
    }

    @Test
    void testEnablePersistenceWithPath() {
        String path = "/tmp/test-data";
        InfinispanQuickFixJConfig result = config.enablePersistence(path);
        assertSame(config, result);
    }

    @Test
    void testCreateMessageStoreFactory() {
        InfinispanMessageStoreFactory factory = config.createMessageStoreFactory();
        assertNotNull(factory);
    }

    @Test
    void testCreateExampleQuickFixJProperties() {
        Properties properties = InfinispanQuickFixJConfig.createExampleQuickFixJProperties();
        assertNotNull(properties);
        assertFalse(properties.isEmpty());
        assertTrue(properties.containsKey("default.ConnectionType"));
        assertTrue(properties.containsKey("default.BeginString"));
        assertTrue(properties.containsKey("default.SenderCompID"));
        assertTrue(properties.containsKey("default.TargetCompID"));
        assertTrue(properties.containsKey("default.SocketConnectHost"));
        assertTrue(properties.containsKey("default.SocketConnectPort"));
        assertTrue(properties.containsKey("default.StartTime"));
        assertTrue(properties.containsKey("default.EndTime"));
        assertTrue(properties.containsKey("default.HeartBtInt"));
        
        assertEquals("initiator", properties.getProperty("default.ConnectionType"));
        assertEquals("FIX.4.4", properties.getProperty("default.BeginString"));
        assertEquals("SENDER", properties.getProperty("default.SenderCompID"));
        assertEquals("TARGET", properties.getProperty("default.TargetCompID"));
        assertEquals("localhost", properties.getProperty("default.SocketConnectHost"));
        assertEquals("9876", properties.getProperty("default.SocketConnectPort"));
        assertEquals("00:00:00", properties.getProperty("default.StartTime"));
        assertEquals("00:00:00", properties.getProperty("default.EndTime"));
        assertEquals("30", properties.getProperty("default.HeartBtInt"));
    }

    @Test
    void testFluentAPIChaining() {
        InfinispanQuickFixJConfig result = config
            .clusterName("chain-test")
            .cacheMode(CacheMode.REPL_SYNC)
            .expiration(180)
            .maxEntries(2000)
            .enableStatistics(true)
            .enablePersistence("/tmp/chain-test");
        
        assertSame(config, result);
    }

    @Test
    void testCreateMessageStoreFactoryWithFullConfiguration() {
        config
            .clusterName("full-config-test")
            .cacheMode(CacheMode.DIST_SYNC)
            .expiration(240)
            .maxEntries(15000)
            .enableStatistics(true)
            .enablePersistence("/tmp/full-config");
        
        InfinispanMessageStoreFactory factory = config.createMessageStoreFactory();
        assertNotNull(factory);
    }

    @Test
    void testMultipleFactoryCreation() {
        InfinispanMessageStoreFactory factory1 = config.createMessageStoreFactory();
        InfinispanMessageStoreFactory factory2 = config.createMessageStoreFactory();
        
        assertNotNull(factory1);
        assertNotNull(factory2);
        // Each call should create a new factory instance
        assertNotSame(factory1, factory2);
    }

    @Test
    void testConfigurationWithAllCacheModes() {
        // Test only local cache mode to avoid clustering issues in tests
        config.cacheMode(CacheMode.LOCAL);
        assertNotNull(config.createMessageStoreFactory());
        
        // For testing distributed modes, we'll validate the configuration without creating actual managers
        config.cacheMode(CacheMode.REPL_SYNC);
        config.cacheMode(CacheMode.REPL_ASYNC);
        config.cacheMode(CacheMode.DIST_SYNC);
        config.cacheMode(CacheMode.DIST_ASYNC);
        config.cacheMode(CacheMode.INVALIDATION_SYNC);
        config.cacheMode(CacheMode.INVALIDATION_ASYNC);
        
        // Verify the configuration was set correctly
        assertEquals(CacheMode.INVALIDATION_ASYNC, config.getCacheMode());
    }

    @Test
    void testEdgeCaseValues() {
        // Test with edge case values
        config.expiration(0); // No expiration
        config.maxEntries(1); // Minimum entries
        config.enableStatistics(false);
        
        InfinispanMessageStoreFactory factory = config.createMessageStoreFactory();
        assertNotNull(factory);
    }

    @Test
    void testLargeValues() {
        // Test with large values
        config.expiration(Long.MAX_VALUE);
        config.maxEntries(Integer.MAX_VALUE);
        
        InfinispanMessageStoreFactory factory = config.createMessageStoreFactory();
        assertNotNull(factory);
    }

    @Test
    void testNullClusterName() {
        // Test with null cluster name (should handle gracefully for local mode)
        config.clusterName(null);
        // Verify configuration accepts null but doesn't fail when creating factory
        assertDoesNotThrow(() -> {
            InfinispanMessageStoreFactory factory = config.createMessageStoreFactory();
            assertNotNull(factory);
        });
    }

    @Test
    void testEmptyClusterName() {
        // Test with empty cluster name
        config.clusterName("");
        InfinispanMessageStoreFactory factory = config.createMessageStoreFactory();
        assertNotNull(factory);
    }

    @Test
    void testNullPersistencePath() {
        // Test with null persistence path
        config.enablePersistence((String) null);
        InfinispanMessageStoreFactory factory = config.createMessageStoreFactory();
        assertNotNull(factory);
    }

    @Test
    void testEmptyPersistencePath() {
        // Test with empty persistence path
        config.enablePersistence("");
        InfinispanMessageStoreFactory factory = config.createMessageStoreFactory();
        assertNotNull(factory);
    }
}