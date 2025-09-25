package io.github.darioajr.quickfixj.examples;

import java.io.ByteArrayInputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.darioajr.quickfixj.config.RedisQuickFixJConfig;
import io.github.darioajr.quickfixj.factory.RedisMessageStoreFactory;
import quickfix.MessageStore;
import quickfix.SessionID;
import quickfix.SessionSettings;

public class RedisQuickFixJExample {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisQuickFixJExample.class);
    
    public static void main(String[] args) {
        logger.info("Starting Redis-QuickFIX/J example");
        
        try {
            // Example 1: Basic configuration
            basicExample();
            
            // Example 2: Advanced configuration
            advancedExample();
            
            // Example 3: Distributed configuration
            distributedExample();
            
        } catch (Exception e) {
            logger.error("Error in example", e);
        }
        
        logger.info("Example completed");
    }
    
    private static void basicExample() throws Exception {
        logger.info("=== Basic Example ===");
        
        // Create basic configuration
        RedisQuickFixJConfig config = new RedisQuickFixJConfig()
            .host("localhost")
            .port(6379)
            .database(0)
            .timeout(5000);
        
        // Create factory
        RedisMessageStoreFactory factory = config.createMessageStoreFactory();
        
        // Create session settings
        SessionSettings settings = config.createQuickFixJSessionSettings();
        
        // Create a session ID
        SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        
        // Create message store
        MessageStore messageStore = factory.create(sessionID, settings);
        
        // Test message storage
        String testMessage = "8=FIX.4.4|9=40|35=0|49=SENDER|56=TARGET|34=1|52=20231201-10:00:00|10=000|";
        messageStore.set(1, testMessage);
        
        logger.info("Stored message with sequence 1");
        logger.info("Next sender sequence: {}", messageStore.getNextSenderMsgSeqNum());
        
        // Retrieve message
        java.util.Collection<String> messages = new java.util.ArrayList<>();
        messageStore.get(1, 1, messages);
        
        logger.info("Retrieved {} messages", messages.size());
        for (String message : messages) {
            logger.info("Message: {}", message);
        }
        
        logger.info("Basic example completed successfully");
    }
    
    private static void advancedExample() throws Exception {
        logger.info("=== Advanced Example ===");
        
        // Create advanced configuration with authentication
        RedisQuickFixJConfig config = new RedisQuickFixJConfig()
            .host("redis.example.com")
            .port(6380)
            .password("mypassword")
            .database(1)
            .timeout(10000)
            .enableSSL(false);
        
        // Validate configuration
        config.validate();
        
        // Get configuration as properties
        Properties props = config.toProperties();
        logger.info("Configuration properties: {}", props);
        
        // Create configuration from properties
        RedisQuickFixJConfig configFromProps = RedisQuickFixJConfig.fromProperties(props);
        logger.info("Recreated config: {}", configFromProps);
        
        logger.info("Advanced example completed successfully");
    }
    
    private static void distributedExample() throws Exception {
        logger.info("=== Distributed Example ===");
        
        // Configuration for distributed setup
        String configString = """
            [DEFAULT]
            ConnectionType=acceptor
            StartTime=00:00:00
            EndTime=00:00:00
            HeartBtInt=30
            ValidateUserDefinedFields=Y
            MessageStoreFactory=io.github.darioajr.quickfixj.factory.RedisMessageStoreFactory
            redis.host=redis-cluster.example.com
            redis.port=6379
            redis.database=2
            redis.timeout=5000
            
            [SESSION]
            BeginString=FIX.4.4
            SenderCompID=EXCHANGE
            TargetCompID=CLIENT1
            SocketAcceptPort=9876
            DataDictionary=FIX44.xml
            
            [SESSION]
            BeginString=FIX.4.4
            SenderCompID=EXCHANGE
            TargetCompID=CLIENT2
            SocketAcceptPort=9877
            DataDictionary=FIX44.xml
            """;
        
        // Create SessionSettings from string
        SessionSettings settings = new SessionSettings(new ByteArrayInputStream(configString.getBytes()));
        
        // Create Redis configuration
        RedisQuickFixJConfig redisConfig = new RedisQuickFixJConfig()
            .host("redis-cluster.example.com")
            .port(6379)
            .database(2);
        
        // Create factory
        RedisMessageStoreFactory factory = redisConfig.createMessageStoreFactory();
        
        // Create session settings with Redis backend
        var redisSessionSettings = redisConfig.createSessionSettings(settings);
        
        logger.info("Created distributed configuration with {} sessions", 
                   countSessions(redisSessionSettings));
        
        // Test multiple sessions
        SessionID session1 = new SessionID("FIX.4.4", "EXCHANGE", "CLIENT1");
        SessionID session2 = new SessionID("FIX.4.4", "EXCHANGE", "CLIENT2");
        
        MessageStore store1 = factory.create(session1, settings);
        MessageStore store2 = factory.create(session2, settings);
        
        // Store messages in different sessions
        store1.set(1, "8=FIX.4.4|9=40|35=0|49=EXCHANGE|56=CLIENT1|34=1|10=000|");
        store2.set(1, "8=FIX.4.4|9=40|35=0|49=EXCHANGE|56=CLIENT2|34=1|10=000|");
        
        logger.info("Session 1 next sequence: {}", store1.getNextSenderMsgSeqNum());
        logger.info("Session 2 next sequence: {}", store2.getNextSenderMsgSeqNum());
        
        logger.info("Distributed example completed successfully");
    }
    
    private static int countSessions(SessionSettings settings) {
        int count = 0;
        var iterator = settings.sectionIterator();
        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }
        return count;
    }
    
    /**
     * Example showing performance testing with Redis message store.
     */
    private static void performanceExample() throws Exception {
        logger.info("=== Performance Example ===");
        
        RedisQuickFixJConfig config = new RedisQuickFixJConfig()
            .host("localhost")
            .port(6379)
            .database(0)
            .timeout(5000);
        
        RedisMessageStoreFactory factory = config.createMessageStoreFactory();
        SessionSettings settings = config.createQuickFixJSessionSettings();
        SessionID sessionID = new SessionID("FIX.4.4", "PERF_SENDER", "PERF_TARGET");
        MessageStore messageStore = factory.create(sessionID, settings);
        
        // Performance test - store 1000 messages
        long startTime = System.currentTimeMillis();
        String baseMessage = "8=FIX.4.4|9=100|35=D|49=PERF_SENDER|56=PERF_TARGET|52=20231201-10:00:00|11=ORDER_%d|21=1|55=SYMBOL|54=1|60=20231201-10:00:00|38=100|40=2|44=100.50|10=000|";
        
        for (int i = 1; i <= 1000; i++) {
            String message = String.format(baseMessage, i);
            messageStore.set(i, message);
            if (i % 100 == 0) {
                logger.info("Stored {} messages", i);
            }
        }
        
        long endTime = System.currentTimeMillis();
        logger.info("Stored 1000 messages in {}ms", (endTime - startTime));
        
        // Performance test - retrieve messages
        startTime = System.currentTimeMillis();
        java.util.Collection<String> messages = new java.util.ArrayList<>();
        messageStore.get(1, 1000, messages);
        endTime = System.currentTimeMillis();
        
        logger.info("Retrieved {} messages in {}ms", messages.size(), (endTime - startTime));
        
        logger.info("Performance example completed successfully");
    }
    
    /**
     * Example showing session management with Redis.
     */
    private static void sessionManagementExample() throws Exception {
        logger.info("=== Session Management Example ===");
        
        RedisQuickFixJConfig config = new RedisQuickFixJConfig()
            .host("localhost")
            .port(6379)
            .database(1);
        
        var redisSettings = config.createSessionSettings();
        
        // Set default settings
        redisSettings.setString("ConnectionType", "initiator");
        redisSettings.setString("StartTime", "00:00:00");
        redisSettings.setString("EndTime", "00:00:00");
        redisSettings.setString("HeartBtInt", "30");
        
        // Create multiple sessions with specific settings
        SessionID session1 = new SessionID("FIX.4.4", "CLIENT", "BROKER1");
        SessionID session2 = new SessionID("FIX.4.4", "CLIENT", "BROKER2");
        
        redisSettings.setString(session1, "SocketConnectHost", "broker1.example.com");
        redisSettings.setString(session1, "SocketConnectPort", "7001");
        redisSettings.setString(session2, "SocketConnectHost", "broker2.example.com");
        redisSettings.setString(session2, "SocketConnectPort", "7002");
        
        // Retrieve settings
        logger.info("Session 1 host: {}", redisSettings.getString(session1, "SocketConnectHost"));
        logger.info("Session 2 host: {}", redisSettings.getString(session2, "SocketConnectHost"));
        logger.info("Default HeartBtInt: {}", redisSettings.getString("HeartBtInt"));
        
        // Get cache statistics
        var stats = redisSettings.getCacheStatistics();
        logger.info("Cache statistics: {}", stats);
        
        logger.info("Session management example completed successfully");
    }
}