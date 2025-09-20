package io.github.darioajr.quickfixj.examples;

import java.io.ByteArrayInputStream;
import java.util.Properties;

import org.infinispan.configuration.cache.CacheMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.darioajr.quickfixj.config.InfinispanQuickFixJConfig;
import io.github.darioajr.quickfixj.factory.InfinispanMessageStoreFactory;
import quickfix.MessageStore;
import quickfix.SessionID;
import quickfix.SessionSettings;

public class InfinispanQuickFixJExample {
    
    private static final Logger logger = LoggerFactory.getLogger(InfinispanQuickFixJExample.class);
    
    public static void main(String[] args) {
        logger.info("Starting Infinispan-QuickFIX/J example");
        
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
        InfinispanQuickFixJConfig config = new InfinispanQuickFixJConfig()
            .clusterName("basic-example")
            .cacheMode(CacheMode.LOCAL)
            .expiration(60) // 1 hour
            .maxEntries(1000);
        
        // Create factory
        InfinispanMessageStoreFactory factory = config.createMessageStoreFactory();
        
        // Create SessionSettings using Properties
        Properties properties = InfinispanQuickFixJConfig.createExampleQuickFixJProperties();
        properties.setProperty("default.MessageStoreFactory", factory.getClass().getName());
        
        SessionSettings settings = new SessionSettings(new ByteArrayInputStream(propertiesToString(properties).getBytes()));
        
        // Configure factory
        factory.configure(settings);
        
        // Create store for a specific session
        SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        MessageStore store = factory.create(sessionID);
        
        // Demonstrate basic usage
        demonstrateStoreUsage(store, "Basic Example");
        
        // Cleanup
        factory.close();
        
        logger.info("Basic example completed");
    }
    
    private static void advancedExample() throws Exception {
        logger.info("=== Advanced Example ===");
        
        // Create advanced configuration
        InfinispanQuickFixJConfig config = new InfinispanQuickFixJConfig()
            .clusterName("advanced-example")
            .cacheMode(CacheMode.LOCAL)
            .expiration(1440) // 24 hours
            .maxEntries(10000)
            .enableStatistics(true)
            .enablePersistence("./infinispan-data");
        
        // Create factory
        InfinispanMessageStoreFactory factory = config.createMessageStoreFactory();
        
        // Configuration with SessionSettings
        Properties properties = InfinispanQuickFixJConfig.createExampleQuickFixJProperties();
        SessionSettings settings = new SessionSettings(new ByteArrayInputStream(propertiesToString(properties).getBytes()));
        
        factory.configure(settings);
        
        // Demonstrate multiple sessions
        SessionID session1 = new SessionID("FIX.4.4", "CLIENT1", "SERVER");
        SessionID session2 = new SessionID("FIX.4.4", "CLIENT2", "SERVER");
        
        MessageStore store1 = factory.create(session1);
        MessageStore store2 = factory.create(session2);
        
        demonstrateStoreUsage(store1, "Session 1");
        demonstrateStoreUsage(store2, "Session 2");
        
        // Show statistics
        Properties stats = factory.getCacheStatistics();
        logger.info("Cache statistics:");
        stats.forEach((key, value) -> logger.info("  {}: {}", key, value));
        
        factory.close();
        
        logger.info("Advanced example completed");
    }
    
    private static void distributedExample() throws Exception {
        logger.info("=== Distributed Example ===");
        
        // Create distributed configuration
        InfinispanQuickFixJConfig config = new InfinispanQuickFixJConfig()
            .clusterName("distributed-cluster")
            .cacheMode(CacheMode.DIST_SYNC)
            .expiration(2880) // 48 hours
            .maxEntries(50000)
            .enableStatistics(true);
        
        // Create factory
        InfinispanMessageStoreFactory factory = config.createMessageStoreFactory();
        
        // Custom configuration
        Properties properties = new Properties();
        properties.setProperty("default.ConnectionType", "acceptor");
        properties.setProperty("default.SocketAcceptPort", "9876");
        properties.setProperty("default.StartTime", "00:00:00");
        properties.setProperty("default.EndTime", "00:00:00");
        properties.setProperty("default.HeartBtInt", "30");
        properties.setProperty("default.MessageStoreFactory", factory.getClass().getName());
        
        // Infinispan-specific configurations
        properties.setProperty("default.InfinispanClusterName", "distributed-cluster");
        properties.setProperty("default.InfinispanCacheMode", "DIST_SYNC");
        properties.setProperty("default.InfinispanExpirationMinutes", "2880");
        properties.setProperty("default.InfinispanMaxEntries", "50000");
        
        SessionSettings settings = new SessionSettings(new ByteArrayInputStream(propertiesToString(properties).getBytes()));
        factory.configure(settings);
        
        // Demonstrate usage
        SessionID sessionID = new SessionID("FIX.4.4", "DISTRIBUTOR", "EXCHANGE");
        MessageStore store = factory.create(sessionID);
        
        demonstrateStoreUsage(store, "Distributed Example");
        
        factory.close();
        
        logger.info("Distributed example completed");
    }
    
    private static void demonstrateStoreUsage(MessageStore store, String label) throws Exception {
        logger.info("--- Demonstrating {} ---", label);
        
        // Check initial sequences
        logger.info("Initial sequence numbers - Sender: {}, Target: {}", 
                   store.getNextSenderMsgSeqNum(), store.getNextTargetMsgSeqNum());
        
        // Store some messages
        String message1 = "8=FIX.4.4|9=73|35=A|49=SENDER|56=TARGET|34=1|52=20231219-10:30:00|98=0|108=30|10=142|";
        String message2 = "8=FIX.4.4|9=55|35=0|49=SENDER|56=TARGET|34=2|52=20231219-10:30:01|10=143|";
        String message3 = "8=FIX.4.4|9=68|35=1|49=SENDER|56=TARGET|34=3|52=20231219-10:30:02|112=TESTID|10=144|";
        
        store.set(1, message1);
        store.set(2, message2);
        store.set(3, message3);
        
        logger.info("Stored 3 messages");
        
        // Retrieve messages
        java.util.Collection<String> messages = new java.util.ArrayList<>();
        store.get(1, 3, messages);
        
        logger.info("Retrieved {} messages:", messages.size());
        int count = 1;
        for (String msg : messages) {
            logger.info("  Message {}: {}", count++, msg.substring(0, Math.min(50, msg.length())) + "...");
        }
        
        // Update sequence numbers
        store.setNextSenderMsgSeqNum(10);
        store.setNextTargetMsgSeqNum(5);
        
        logger.info("Updated sequence numbers - Sender: {}, Target: {}", 
                   store.getNextSenderMsgSeqNum(), store.getNextTargetMsgSeqNum());
        
        // Increment sequence numbers
        store.incrNextSenderMsgSeqNum();
        store.incrNextTargetMsgSeqNum();
        
        logger.info("Incremented sequence numbers - Sender: {}, Target: {}", 
                   store.getNextSenderMsgSeqNum(), store.getNextTargetMsgSeqNum());
        
        // Show creation time
        logger.info("Session creation time: {}", store.getCreationTime());
        
        logger.info("--- End of {} demonstration ---", label);
    }
    
    private static String propertiesToString(Properties properties) {
        StringBuilder sb = new StringBuilder();
        
        // Add [DEFAULT] section
        sb.append("[DEFAULT]\n");
        properties.forEach((key, value) -> {
            String keyStr = key.toString();
            if (keyStr.startsWith("default.")) {
                sb.append(keyStr.substring(8)).append("=").append(value).append("\n");
            }
        });
        
        // Add session sections
        properties.forEach((key, value) -> {
            String keyStr = key.toString();
            if (keyStr.startsWith("session.")) {
                String[] parts = keyStr.split("\\.", 3);
                if (parts.length == 3) {
                    sb.append("[SESSION]\n");
                    sb.append(parts[2]).append("=").append(value).append("\n");
                }
            }
        });
        
        return sb.toString();
    }
}