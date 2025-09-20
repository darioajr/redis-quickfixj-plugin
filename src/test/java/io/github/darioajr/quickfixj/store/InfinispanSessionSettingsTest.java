package io.github.darioajr.quickfixj.store;

import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import quickfix.ConfigError;
import quickfix.SessionID;

class InfinispanSessionSettingsTest {

    private EmbeddedCacheManager cacheManager;
    private InfinispanSessionSettings sessionSettings;

    @BeforeEach
    void setUp() {
        GlobalConfiguration globalConfig = new GlobalConfigurationBuilder()
            .nonClusteredDefault()
            .build();
        
        cacheManager = new DefaultCacheManager(globalConfig);
        
        Configuration cacheConfig = new ConfigurationBuilder()
            .memory().maxCount(1000)
            .build();
            
        cacheManager.defineConfiguration("quickfixj-settings", cacheConfig);
        
        sessionSettings = new InfinispanSessionSettings(cacheManager);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (sessionSettings != null) {
            sessionSettings.close();
        }
        if (cacheManager != null) {
            cacheManager.close();
        }
    }

    @Test
    void testSetAndGetDefault() throws ConfigError {
        String key = "test.key";
        String value = "test.value";
        
        sessionSettings.setDefault(key, value);
        String retrievedValue = sessionSettings.getDefault(key);
        
        assertEquals(value, retrievedValue);
    }

    @Test
    void testSetAndGetSession() throws ConfigError {
        SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        String key = "session.test.key";
        String value = "session.test.value";
        
        sessionSettings.setSession(sessionID, key, value);
        String retrievedValue = sessionSettings.getSession(sessionID, key);
        
        assertEquals(value, retrievedValue);
    }

    @Test
    void testHasDefault() {
        String key = "existing.key";
        sessionSettings.setDefault(key, "some.value");
        
        assertTrue(sessionSettings.hasDefault(key));
        assertFalse(sessionSettings.hasDefault("non.existent.key"));
    }

    @Test
    void testHasSession() {
        SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        String key = "existing.session.key";
        
        sessionSettings.setSession(sessionID, key, "some.session.value");
        
        assertTrue(sessionSettings.hasSession(sessionID, key));
        assertFalse(sessionSettings.hasSession(sessionID, "non.existent.session.key"));
    }

    @Test
    void testSessionFallbackToDefault() throws ConfigError {
        SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        
        // Set only default value
        sessionSettings.setDefault("fallback.setting", "default.value");
        
        // Session should get default value if no session-specific value exists
        assertEquals("default.value", sessionSettings.getSession(sessionID, "fallback.setting"));
    }

    @Test
    void testSessionOverrideDefault() throws ConfigError {
        SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        
        // Set default value
        sessionSettings.setDefault("override.setting", "default.value");
        
        // Set session-specific value
        sessionSettings.setSession(sessionID, "override.setting", "session.value");
        
        // Session should get session-specific value, not default
        assertEquals("session.value", sessionSettings.getSession(sessionID, "override.setting"));
        assertEquals("default.value", sessionSettings.getDefault("override.setting"));
    }

    @Test
    void testMultipleSessions() throws ConfigError {
        SessionID sessionID1 = new SessionID("FIX.4.4", "SENDER1", "TARGET");
        SessionID sessionID2 = new SessionID("FIX.4.2", "SENDER2", "TARGET");
        SessionID sessionID3 = new SessionID("FIXT.1.1", "SENDER3", "TARGET");
        
        sessionSettings.setSession(sessionID1, "version", "4.4");
        sessionSettings.setSession(sessionID2, "version", "4.2");
        sessionSettings.setSession(sessionID3, "version", "T.1.1");
        
        assertEquals("4.4", sessionSettings.getSession(sessionID1, "version"));
        assertEquals("4.2", sessionSettings.getSession(sessionID2, "version"));
        assertEquals("T.1.1", sessionSettings.getSession(sessionID3, "version"));
    }

    @Test
    void testComplexSessionIDs() throws ConfigError {
        SessionID complexID1 = new SessionID("FIX.4.4", "SENDER", "TARGET", "QUALIFIER");
        SessionID complexID2 = new SessionID("FIX.4.4", "SENDER", "SUB", "TARGET", "QUALIFIER");
        
        sessionSettings.setSession(complexID1, "complex.key1", "complex.value1");
        sessionSettings.setSession(complexID2, "complex.key2", "complex.value2");
        
        assertEquals("complex.value1", sessionSettings.getSession(complexID1, "complex.key1"));
        assertEquals("complex.value2", sessionSettings.getSession(complexID2, "complex.key2"));
    }

    @Test
    void testGetNonExistentDefault() {
        // Should throw ConfigError for non-existent default settings
        assertThrows(ConfigError.class, () -> sessionSettings.getDefault("non.existent"));
    }

    @Test
    void testGetNonExistentSession() {
        SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        
        // Should throw ConfigError for non-existent session settings
        assertThrows(ConfigError.class, () -> sessionSettings.getSession(sessionID, "non.existent"));
    }

    @Test
    void testNullArguments() {
        // Test null arguments should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> sessionSettings.setDefault(null, "value"));
        assertThrows(IllegalArgumentException.class, () -> sessionSettings.setDefault("key", null));
        assertThrows(IllegalArgumentException.class, () -> sessionSettings.getDefault(null));
        assertThrows(IllegalArgumentException.class, () -> sessionSettings.hasDefault(null));
        
        SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        assertThrows(IllegalArgumentException.class, () -> sessionSettings.setSession(null, "key", "value"));
        assertThrows(IllegalArgumentException.class, () -> sessionSettings.setSession(sessionID, null, "value"));
        assertThrows(IllegalArgumentException.class, () -> sessionSettings.setSession(sessionID, "key", null));
        assertThrows(IllegalArgumentException.class, () -> sessionSettings.getSession(null, "key"));
        assertThrows(IllegalArgumentException.class, () -> sessionSettings.getSession(sessionID, null));
        assertThrows(IllegalArgumentException.class, () -> sessionSettings.hasSession(null, "key"));
        assertThrows(IllegalArgumentException.class, () -> sessionSettings.hasSession(sessionID, null));
    }

    @Test
    void testConstructorWithNullCacheManager() {
        assertThrows(IllegalArgumentException.class, () -> new InfinispanSessionSettings(null));
    }

    @Test
    void testCloseOperation() {
        // Should not throw exception when closed
        sessionSettings.close();
        
        // Should be safe to close multiple times
        sessionSettings.close();
    }

    @Test
    void testHasSessionFallbackToDefault() {
        SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        
        // Set only default value
        sessionSettings.setDefault("fallback.has.setting", "default.value");
        
        // Session should return true for hasSession since it falls back to default
        assertTrue(sessionSettings.hasSession(sessionID, "fallback.has.setting"));
    }

    @Test
    void testManySessionsAndSettings() throws ConfigError {
        // Test with many sessions and settings to ensure performance and correctness
        for (int i = 0; i < 100; i++) {
            SessionID sessionID = new SessionID("FIX.4.4", "SENDER" + i, "TARGET");
            sessionSettings.setSession(sessionID, "setting" + i, "value" + i);
        }
        
        // Verify all settings can be retrieved correctly
        for (int i = 0; i < 100; i++) {
            SessionID sessionID = new SessionID("FIX.4.4", "SENDER" + i, "TARGET");
            assertEquals("value" + i, sessionSettings.getSession(sessionID, "setting" + i));
        }
    }

    @Test
    void testDefaultOverrideWithManySettings() throws ConfigError {
        sessionSettings.setDefault("common.setting", "default.value");
        
        for (int i = 0; i < 50; i++) {
            SessionID sessionID = new SessionID("FIX.4.4", "SENDER" + i, "TARGET");
            if (i % 2 == 0) {
                sessionSettings.setSession(sessionID, "common.setting", "override.value" + i);
            }
        }
        
        // Verify correct values are returned
        for (int i = 0; i < 50; i++) {
            SessionID sessionID = new SessionID("FIX.4.4", "SENDER" + i, "TARGET");
            String expected = (i % 2 == 0) ? "override.value" + i : "default.value";
            assertEquals(expected, sessionSettings.getSession(sessionID, "common.setting"));
        }
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        final int numThreads = 10;
        final int operationsPerThread = 100;
        Thread[] threads = new Thread[numThreads];
        
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    SessionID sessionID = new SessionID("FIX.4.4", "THREAD" + threadId, "TARGET" + i);
                    sessionSettings.setSession(sessionID, "concurrent.key" + i, "value" + threadId + "_" + i);
                    
                    try {
                        String value = sessionSettings.getSession(sessionID, "concurrent.key" + i);
                        assertEquals("value" + threadId + "_" + i, value);
                    } catch (ConfigError e) {
                        fail("Unexpected ConfigError: " + e.getMessage());
                    }
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
    }
}