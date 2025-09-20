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

/**
 * Complete example of using the Infinispan-QuickFIX/J plugin.
 * 
 * <p>This class demonstrates how to configure and use Infinispan as a persistence backend for QuickFIX/J.
 * It provides three different examples showcasing various configuration scenarios:</p>
 * 
 * <ol>
 *   <li><b>Basic Example</b> - Simple local cache configuration suitable for single-node applications</li>
 *   <li><b>Advanced Example</b> - Local cache with disk persistence and statistics enabled</li>
 *   <li><b>Distributed Example</b> - Distributed cache configuration for multi-node clustering</li>
 * </ol>
 * 
 * <p>Each example demonstrates:</p>
 * <ul>
 *   <li>Creating and configuring an {@link InfinispanQuickFixJConfig}</li>
 *   <li>Setting up an {@link InfinispanMessageStoreFactory}</li>
 *   <li>Configuring QuickFIX/J {@link SessionSettings}</li>
 *   <li>Creating and using {@link MessageStore} instances</li>
 *   <li>Performing common message store operations</li>
 * </ul>
 * 
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * // Run all examples
 * java InfinispanQuickFixJExample
 * }</pre>
 * 
 * @author Dario Ajr
 * @version 1.0
 * @since 1.0
 * @see InfinispanQuickFixJConfig
 * @see InfinispanMessageStoreFactory
 * @see MessageStore
 * @see SessionSettings
 */
public class InfinispanQuickFixJExample {
    
    private static final Logger logger = LoggerFactory.getLogger(InfinispanQuickFixJExample.class);
    
    /**
     * Main method that runs all Infinispan-QuickFIX/J examples.
     * 
     * <p>This method executes three different examples in sequence:</p>
     * <ol>
     *   <li>Basic configuration example</li>
     *   <li>Advanced configuration with persistence</li>
     *   <li>Distributed clustering example</li>
     * </ol>
     * 
     * <p>Each example is self-contained and demonstrates different aspects
     * of using Infinispan as a QuickFIX/J message store backend.</p>
     * 
     * @param args command-line arguments (not used)
     */
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
    
    /**
     * Demonstrates basic Infinispan-QuickFIX/J configuration with local caching.
     * 
     * <p>This example shows the simplest possible configuration using:</p>
     * <ul>
     *   <li>Local cache mode (single node)</li>
     *   <li>1-hour expiration time</li>
     *   <li>1000 maximum entries</li>
     *   <li>No persistence (in-memory only)</li>
     * </ul>
     * 
     * <p>The example creates a single message store and demonstrates basic
     * message storage and retrieval operations.</p>
     * 
     * @throws Exception if any error occurs during the example execution
     */
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
    
    /**
     * Demonstrates advanced Infinispan-QuickFIX/J configuration with disk persistence.
     * 
     * <p>This example shows a more sophisticated configuration featuring:</p>
     * <ul>
     *   <li>Local cache mode with disk persistence</li>
     *   <li>24-hour expiration time</li>
     *   <li>10,000 maximum entries</li>
     *   <li>Statistics collection enabled</li>
     *   <li>Persistent storage to disk</li>
     * </ul>
     * 
     * <p>The example demonstrates:</p>
     * <ul>
     *   <li>Creating multiple message stores for different sessions</li>
     *   <li>Retrieving and displaying cache statistics</li>
     *   <li>Persistent data storage across application restarts</li>
     * </ul>
     * 
     * @throws Exception if any error occurs during the example execution
     */
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
    
    /**
     * Demonstrates distributed Infinispan-QuickFIX/J configuration for clustering.
     * 
     * <p>This example shows how to configure Infinispan for distributed clustering:</p>
     * <ul>
     *   <li>Distributed synchronous cache mode</li>
     *   <li>48-hour expiration time</li>
     *   <li>50,000 maximum entries</li>
     *   <li>Statistics collection enabled</li>
     *   <li>Custom QuickFIX/J properties for acceptor configuration</li>
     * </ul>
     * 
     * <p>This configuration is suitable for high-availability scenarios where
     * multiple nodes need to share message store data across a cluster.</p>
     * 
     * <p><b>Note:</b> In a real distributed environment, you would typically
     * run this on multiple nodes with proper network configuration.</p>
     * 
     * @throws Exception if any error occurs during the example execution
     */
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
    
    /**
     * Demonstrates basic usage of a MessageStore instance.
     * 
     * <p>This method performs common MessageStore operations including:</p>
     * <ul>
     *   <li>Checking initial sequence numbers</li>
     *   <li>Storing sample FIX messages</li>
     *   <li>Retrieving stored messages</li>
     *   <li>Updating and incrementing sequence numbers</li>
     *   <li>Displaying session creation time</li>
     * </ul>
     * 
     * <p>The method uses sample FIX 4.4 messages to demonstrate the storage
     * and retrieval functionality.</p>
     * 
     * @param store the MessageStore instance to demonstrate
     * @param label a descriptive label for logging purposes
     * @throws Exception if any error occurs during the demonstration
     */
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
    
    /**
     * Converts Properties to String in the format expected by QuickFIX/J.
     * 
     * <p>This utility method transforms a Java Properties object into the INI-style
     * configuration format that QuickFIX/J expects. The conversion handles:</p>
     * 
     * <ul>
     *   <li><b>[DEFAULT]</b> section - Contains properties with "default." prefix</li>
     *   <li><b>[SESSION]</b> sections - Contains properties with "session." prefix</li>
     * </ul>
     * 
     * <p>Property keys are processed as follows:</p>
     * <ul>
     *   <li>Keys starting with "default." are placed in the [DEFAULT] section with the prefix removed</li>
     *   <li>Keys starting with "session." are parsed and placed in [SESSION] sections</li>
     * </ul>
     * 
     * <p><b>Example input:</b></p>
     * <pre>
     * default.ConnectionType=initiator
     * default.HeartBtInt=30
     * session.FIX.4.4:SENDER->TARGET.BeginString=FIX.4.4
     * </pre>
     * 
     * <p><b>Example output:</b></p>
     * <pre>
     * [DEFAULT]
     * ConnectionType=initiator
     * HeartBtInt=30
     * [SESSION]
     * BeginString=FIX.4.4
     * </pre>
     * 
     * @param properties the Properties object to convert
     * @return a String in QuickFIX/J configuration format
     */
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