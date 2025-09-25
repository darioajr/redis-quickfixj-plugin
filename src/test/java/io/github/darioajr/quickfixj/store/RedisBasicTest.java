package io.github.darioajr.quickfixj.store;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import redis.clients.jedis.JedisPooled;
import redis.embedded.RedisServer;

/**
 * Basic tests for Redis connectivity without complex inheritance.
 */
class RedisBasicTest {
    
    private RedisServer redisServer;
    private JedisPooled jedis;
    private int redisPort;

    @BeforeEach
    void setUp() throws Exception {
        // Start embedded Redis server on a random port
        redisPort = 6370; // Use a different port to avoid conflicts
        
        try {
            redisServer = RedisServer.builder()
                    .port(redisPort)
                    .setting("maxmemory 128M")
                    .build();
            redisServer.start();
            
            // Create Redis client
            jedis = new JedisPooled("localhost", redisPort);
            
            // Test connection
            jedis.ping();
            
        } catch (Exception e) {
            System.err.println("Failed to start Redis server: " + e.getMessage());
            throw e;
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (jedis != null) {
            jedis.close();
        }
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testBasicRedisOperations() {
        // Test basic Redis operations
        jedis.set("test:key1", "value1");
        String value = jedis.get("test:key1");
        assertEquals("value1", value);

        // Test hash operations (used by RedisSessionSettings)
        jedis.hset("test:hash", "field1", "value1");
        String hashValue = jedis.hget("test:hash", "field1");
        assertEquals("value1", hashValue);

        // Test deletion
        jedis.del("test:key1");
        assertNull(jedis.get("test:key1"));

        jedis.del("test:hash");
        assertNull(jedis.hget("test:hash", "field1"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testRedisSessionSettingsBasicOperations() {
        RedisSessionSettings settings = new RedisSessionSettings(jedis);
        
        // Test basic set/get without recursion
        settings.setString("TestKey", "TestValue");
        
        // This should not cause infinite recursion
        try {
            String value = settings.getString("TestKey");
            assertEquals("TestValue", value);
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }
}