package io.github.darioajr.quickfixj.factory;

import java.io.ByteArrayInputStream;
import java.util.Properties;

import org.infinispan.configuration.cache.CacheMode;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.darioajr.quickfixj.config.InfinispanQuickFixJConfig;
import io.github.darioajr.quickfixj.store.InfinispanSessionSettings;
import quickfix.MessageStore;
import quickfix.SessionID;
import quickfix.SessionSettings;

class InfinispanMessageStoreFactoryTest {

    private InfinispanMessageStoreFactory factory;
    private SessionSettings sessionSettings;

    @BeforeEach
    void setUp() throws Exception {
        InfinispanQuickFixJConfig config = new InfinispanQuickFixJConfig()
            .clusterName("test-cluster")
            .cacheMode(CacheMode.LOCAL)
            .expiration(60)
            .maxEntries(1000)
            .enableStatistics(true);
        
        factory = config.createMessageStoreFactory();
        
        Properties properties = new Properties();
        properties.setProperty("default.ConnectionType", "initiator");
        properties.setProperty("default.BeginString", "FIX.4.4");
        properties.setProperty("default.SenderCompID", "SENDER");
        properties.setProperty("default.TargetCompID", "TARGET");
        properties.setProperty("default.HeartBtInt", "30");
        
        String propertiesString = propertiesToString(properties);
        sessionSettings = new SessionSettings(new ByteArrayInputStream(propertiesString.getBytes()));
        
        factory.configure(sessionSettings);
    }

    @AfterEach
    void tearDown() {
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    void testCreate() {
        SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        MessageStore store = factory.create(sessionID);
        assertNotNull(store);
    }

    @Test
    void testCreateMultipleSessions() {
        SessionID sessionID1 = new SessionID("FIX.4.4", "SENDER1", "TARGET");
        SessionID sessionID2 = new SessionID("FIX.4.4", "SENDER2", "TARGET");
        
        MessageStore store1 = factory.create(sessionID1);
        MessageStore store2 = factory.create(sessionID2);
        
        assertNotNull(store1);
        assertNotNull(store2);
        assertNotSame(store1, store2);
    }

    @Test
    void testCreateSessionSettings() {
        InfinispanSessionSettings settings = factory.createSessionSettings();
        assertNotNull(settings);
    }

    @Test
    void testCreateSessionSettingsMultiple() {
        InfinispanSessionSettings settings1 = factory.createSessionSettings();
        InfinispanSessionSettings settings2 = factory.createSessionSettings();
        
        assertNotNull(settings1);
        assertNotNull(settings2);
        assertNotSame(settings1, settings2);
    }

    @Test
    void testGetCacheStatistics() {
        Properties stats = factory.getCacheStatistics();
        assertNotNull(stats);
    }

    @Test
    void testGetCacheStatisticsContainsData() {
        // Create some stores to generate cache data
        SessionID sessionID1 = new SessionID("FIX.4.4", "SENDER1", "TARGET");
        SessionID sessionID2 = new SessionID("FIX.4.4", "SENDER2", "TARGET");
        
        factory.create(sessionID1);
        factory.create(sessionID2);
        factory.createSessionSettings();
        factory.createSessionSettings();
        
        Properties stats = factory.getCacheStatistics();
        assertNotNull(stats);
        assertFalse(stats.isEmpty());
    }

    @Test
    void testConfigureWithDifferentSettings() throws Exception {
        Properties differentProperties = new Properties();
        differentProperties.setProperty("default.ConnectionType", "acceptor");
        differentProperties.setProperty("default.BeginString", "FIX.4.2");
        differentProperties.setProperty("default.SenderCompID", "DIFFERENT_SENDER");
        differentProperties.setProperty("default.TargetCompID", "DIFFERENT_TARGET");
        differentProperties.setProperty("default.HeartBtInt", "60");
        
        String propertiesString = propertiesToString(differentProperties);
        SessionSettings differentSettings = new SessionSettings(new ByteArrayInputStream(propertiesString.getBytes()));
        
        factory.configure(differentSettings);
        
        SessionID sessionID = new SessionID("FIX.4.2", "DIFFERENT_SENDER", "DIFFERENT_TARGET");
        MessageStore store = factory.create(sessionID);
        assertNotNull(store);
    }

    @Test
    void testConfigureMultipleTimes() throws Exception {
        // Configure once
        Properties properties1 = new Properties();
        properties1.setProperty("default.ConnectionType", "initiator");
        properties1.setProperty("default.BeginString", "FIX.4.4");
        
        String propertiesString1 = propertiesToString(properties1);
        SessionSettings settings1 = new SessionSettings(new ByteArrayInputStream(propertiesString1.getBytes()));
        factory.configure(settings1);
        
        // Configure again with different settings
        Properties properties2 = new Properties();
        properties2.setProperty("default.ConnectionType", "acceptor");
        properties2.setProperty("default.BeginString", "FIX.4.2");
        
        String propertiesString2 = propertiesToString(properties2);
        SessionSettings settings2 = new SessionSettings(new ByteArrayInputStream(propertiesString2.getBytes()));
        factory.configure(settings2);
        
        SessionID sessionID = new SessionID("FIX.4.2", "SENDER", "TARGET");
        MessageStore store = factory.create(sessionID);
        assertNotNull(store);
    }

    @Test
    void testCreateWithDifferentBeginStrings() {
        SessionID sessionID42 = new SessionID("FIX.4.2", "SENDER", "TARGET");
        SessionID sessionID44 = new SessionID("FIX.4.4", "SENDER", "TARGET");
        SessionID sessionID50 = new SessionID("FIXT.1.1", "SENDER", "TARGET");
        
        MessageStore store42 = factory.create(sessionID42);
        MessageStore store44 = factory.create(sessionID44);
        MessageStore store50 = factory.create(sessionID50);
        
        assertNotNull(store42);
        assertNotNull(store44);
        assertNotNull(store50);
    }

    @Test
    void testCreateWithComplexSessionIDs() {
        SessionID complexSessionID1 = new SessionID("FIX.4.4", "SENDER", "TARGET", "QUALIFIER");
        SessionID complexSessionID2 = new SessionID("FIX.4.4", "SENDER", "SUB", "TARGET", "QUALIFIER");
        
        MessageStore store1 = factory.create(complexSessionID1);
        MessageStore store2 = factory.create(complexSessionID2);
        
        assertNotNull(store1);
        assertNotNull(store2);
    }

    @Test
    void testFactoryCloseTwice() {
        // Should not throw exception when closed multiple times
        factory.close();
        factory.close(); // Second close should be safe
    }

    @Test
    void testCreateAfterClose() {
        factory.close();
        
        // Creating after close should throw RuntimeException due to terminated cache manager
        SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            factory.create(sessionID);
        });
        assertTrue(exception.getMessage().contains("Failed to create MessageStore"));
    }

    @Test
    void testGetCacheStatisticsAfterClose() {
        factory.close();
        
        // Should still be able to get statistics after close
        Properties stats = factory.getCacheStatistics();
        assertNotNull(stats);
    }

    @Test
    void testFactoryWithoutConfiguration() {
        InfinispanQuickFixJConfig config = new InfinispanQuickFixJConfig();
        InfinispanMessageStoreFactory unconfiguredFactory = config.createMessageStoreFactory();
        
        // Should work even without explicit configuration
        SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        MessageStore store = unconfiguredFactory.create(sessionID);
        assertNotNull(store);
        
        unconfiguredFactory.close();
    }

    @Test
    void testFactoryWithPersistenceEnabled() {
        InfinispanQuickFixJConfig configWithPersistence = new InfinispanQuickFixJConfig()
            .enablePersistence("./test-data");
        
        InfinispanMessageStoreFactory factoryWithPersistence = configWithPersistence.createMessageStoreFactory();
        
        SessionID sessionID = new SessionID("FIX.4.4", "PERSIST_SENDER", "PERSIST_TARGET");
        MessageStore store = factoryWithPersistence.create(sessionID);
        assertNotNull(store);
        
        factoryWithPersistence.close();
    }

    @Test
    void testFactoryWithDistributedCacheMode() {
        InfinispanQuickFixJConfig distributedConfig = new InfinispanQuickFixJConfig()
            .cacheMode(CacheMode.DIST_SYNC)
            .clusterName("distributed-test");
        
        InfinispanMessageStoreFactory distributedFactory = distributedConfig.createMessageStoreFactory();
        
        SessionID sessionID = new SessionID("FIX.4.4", "DIST_SENDER", "DIST_TARGET");
        MessageStore store = distributedFactory.create(sessionID);
        assertNotNull(store);
        
        distributedFactory.close();
    }

    private String propertiesToString(Properties properties) {
        StringBuilder sb = new StringBuilder();
        sb.append("[DEFAULT]\n");
        properties.forEach((key, value) -> {
            String keyStr = key.toString();
            if (keyStr.startsWith("default.")) {
                sb.append(keyStr.substring(8)).append("=").append(value).append("\n");
            } else {
                sb.append(keyStr).append("=").append(value).append("\n");
            }
        });
        return sb.toString();
    }
}