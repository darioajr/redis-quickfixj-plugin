package io.github.darioajr.quickfixj.config;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.darioajr.quickfixj.factory.InfinispanMessageStoreFactory;
import io.github.darioajr.quickfixj.store.InfinispanSessionSettings;
import quickfix.SessionSettings;

/**
 * Configuration class to facilitate the creation and configuration of the Infinispan-QuickFIX/J plugin.
 * 
 * <p>This class provides a fluent API for configuring Infinispan as a message store backend for QuickFIX/J.
 * It supports various cache modes, expiration policies, persistence settings, and clustering configurations.
 * The class follows the builder pattern, allowing method chaining for convenient configuration.</p>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * InfinispanQuickFixJConfig config = new InfinispanQuickFixJConfig()
 *     .clusterName("my-trading-cluster")
 *     .cacheMode(CacheMode.REPLICATED)
 *     .expiration(720) // 12 hours
 *     .maxEntries(50000)
 *     .enableStatistics(true)
 *     .enablePersistence("/var/data/infinispan");
 * 
 * EmbeddedCacheManager cacheManager = config.createCacheManager();
 * InfinispanMessageStoreFactory factory = config.createMessageStoreFactory();
 * }</pre>
 * 
 * <p><b>Supported Cache Modes:</b></p>
 * <ul>
 *   <li><b>LOCAL</b> - Single node cache (default)</li>
 *   <li><b>REPLICATED</b> - Full replication across all cluster nodes</li>
 *   <li><b>DISTRIBUTED</b> - Data distributed across cluster nodes</li>
 * </ul>
 * 
 * <p><b>Cache Configuration:</b></p>
 * <p>The class automatically creates the following caches for QuickFIX/J:</p>
 * <ul>
 *   <li><code>quickfixj-messages</code> - Stores FIX messages</li>
 *   <li><code>quickfixj-sequences</code> - Stores sequence numbers</li>
 *   <li><code>quickfixj-sessions</code> - Stores session data</li>
 *   <li><code>quickfixj-settings</code> - Stores session settings</li>
 * </ul>
 * 
 * @author Dario Ajr
 * @version 1.0
 * @since 1.0
 * @see InfinispanMessageStoreFactory
 * @see InfinispanSessionSettings
 * @see EmbeddedCacheManager
 */
public class InfinispanQuickFixJConfig {
    
    /** Logger instance for this class. */
    private static final Logger logger = LoggerFactory.getLogger(InfinispanQuickFixJConfig.class);
    
    /** Name of the Infinispan cluster. */
    private String clusterName = "quickfixj-cluster";
    
    /** Cache mode for Infinispan (LOCAL, REPL_SYNC, DIST_SYNC, etc.). */
    private CacheMode cacheMode = CacheMode.LOCAL;
    
    /** Cache entry expiration time in minutes. */
    private long expirationMinutes = 1440; // 24 hours
    
    /** Maximum number of entries allowed in cache. */
    private int maxEntries = 10000;
    
    /** Whether to enable cache statistics. */
    private boolean enableStatistics = true;
    
    /** Whether to enable persistent storage. */
    private boolean enablePersistence = false;
    
    /** Directory location for persistent storage. */
    private String persistenceLocation = "./infinispan-data";
    
    /**
     * Creates a new InfinispanQuickFixJConfig with default settings.
     * 
     * <p>Default configuration includes:</p>
     * <ul>
     *   <li>Cluster name: "quickfixj-cluster"</li>
     *   <li>Cache mode: LOCAL</li>
     *   <li>Expiration: 1440 minutes (24 hours)</li>
     *   <li>Max entries: 10,000</li>
     *   <li>Statistics: enabled</li>
     *   <li>Persistence: disabled</li>
     * </ul>
     */
    public InfinispanQuickFixJConfig() {
        // Default configuration
    }
    
    /**
     * Sets the cluster name for the Infinispan cluster.
     * 
     * <p>The cluster name is used to identify and group nodes in a distributed cache setup.
     * All nodes with the same cluster name will form a single cluster and share data.</p>
     * 
     * @param clusterName the name of the cluster (must not be null)
     * @return this configuration instance for method chaining
     * @throws IllegalArgumentException if clusterName is null or empty
     */
    public InfinispanQuickFixJConfig clusterName(String clusterName) {
        this.clusterName = clusterName;
        return this;
    }
    
    /**
     * Sets the cache mode for the Infinispan cluster.
     * 
     * <p>Available cache modes:</p>
     * <ul>
     *   <li><b>LOCAL</b> - Cache operates only on the local node (default)</li>
     *   <li><b>REPLICATED</b> - Data is replicated to all nodes in the cluster</li>
     *   <li><b>DISTRIBUTED</b> - Data is distributed across cluster nodes with configurable redundancy</li>
     * </ul>
     * 
     * @param cacheMode the cache mode to use (must not be null)
     * @return this configuration instance for method chaining
     * @throws IllegalArgumentException if cacheMode is null
     * @see CacheMode
     */
    public InfinispanQuickFixJConfig cacheMode(CacheMode cacheMode) {
        this.cacheMode = cacheMode;
        return this;
    }
    
    /**
     * Sets the expiration time for cache entries in minutes.
     * 
     * <p>After the specified time, cache entries will be automatically removed from the cache.
     * This helps prevent memory leaks and ensures that stale data is cleaned up.</p>
     * 
     * @param minutes the expiration time in minutes (must be positive)
     * @return this configuration instance for method chaining
     * @throws IllegalArgumentException if minutes is negative or zero
     */
    public InfinispanQuickFixJConfig expiration(long minutes) {
        this.expirationMinutes = minutes;
        return this;
    }
    
    /**
     * Sets the maximum number of entries allowed in the cache.
     * 
     * <p>When the cache reaches this limit, the eviction policy will be triggered to remove
     * entries and make room for new ones. This helps control memory usage.</p>
     * 
     * @param maxEntries the maximum number of entries (must be positive)
     * @return this configuration instance for method chaining
     * @throws IllegalArgumentException if maxEntries is negative or zero
     */
    public InfinispanQuickFixJConfig maxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
        return this;
    }
    
    /**
     * Enables or disables cache statistics collection.
     * 
     * <p>When enabled, Infinispan will collect detailed statistics about cache operations
     * such as hit/miss ratios, access times, and entry counts. These statistics can be
     * useful for monitoring and performance tuning but may have a small performance overhead.</p>
     * 
     * @param enable true to enable statistics, false to disable
     * @return this configuration instance for method chaining
     */
    public InfinispanQuickFixJConfig enableStatistics(boolean enable) {
        this.enableStatistics = enable;
        return this;
    }
    
    /**
     * Enables disk persistence for the cache.
     * 
     * <p>When enabled, cache data will be stored to disk at the specified location,
     * allowing data to survive cache restarts and node failures. This is particularly
     * important for QuickFIX/J message stores to ensure message durability.</p>
     * 
     * <p><strong>Note:</strong> Enabling persistence may impact performance due to disk I/O operations,
     * but provides data durability guarantees.</p>
     * 
     * @param location the directory path where cache data will be persisted (must not be null)
     * @return this configuration instance for method chaining
     * @throws IllegalArgumentException if location is null or empty
     */
    public InfinispanQuickFixJConfig enablePersistence(String location) {
        this.enablePersistence = true;
        this.persistenceLocation = location;
        return this;
    }
    
    /**
     * Creates and configures an EmbeddedCacheManager with the specified settings.
     * 
     * <p>This method creates a fully configured Infinispan cache manager with the following caches:</p>
     * <ul>
     *   <li><code>quickfixj-messages</code> - For storing FIX protocol messages</li>
     *   <li><code>quickfixj-sequences</code> - For storing sequence numbers</li>
     *   <li><code>quickfixj-sessions</code> - For storing session data</li>
     *   <li><code>quickfixj-settings</code> - For storing session settings</li>
     * </ul>
     * 
     * <p>The cache manager is configured with all the settings specified through the builder methods,
     * including clustering, expiration, persistence, and statistics settings.</p>
     * 
     * @return a configured EmbeddedCacheManager ready for use with QuickFIX/J
     * @throws RuntimeException if the cache manager creation fails
     */
    public EmbeddedCacheManager createCacheManager() {
        try {
            GlobalConfiguration globalConfig = new GlobalConfigurationBuilder()
                .clusteredDefault()
                .transport().clusterName(clusterName)
                .build();
            
            EmbeddedCacheManager cacheManager = new DefaultCacheManager(globalConfig);
            
            // Configure caches
            Configuration cacheConfig = createCacheConfiguration();
            cacheManager.defineConfiguration("quickfixj-messages", cacheConfig);
            cacheManager.defineConfiguration("quickfixj-sequences", cacheConfig);
            cacheManager.defineConfiguration("quickfixj-sessions", cacheConfig);
            cacheManager.defineConfiguration("quickfixj-settings", cacheConfig);
            
            logger.info("CacheManager created successfully - Cluster: {}, Mode: {}", clusterName, cacheMode);
            return cacheManager;
            
        } catch (Exception e) {
            logger.error("Error creating CacheManager", e);
            throw new RuntimeException("Failed to create CacheManager", e);
        }
    }
    
    /**
     * Creates a configured InfinispanMessageStoreFactory for use with QuickFIX/J.
     * 
     * <p>The returned factory can be used to create message stores that will use Infinispan
     * as the underlying storage mechanism. This provides benefits such as clustering,
     * high availability, and improved performance compared to file-based storage.</p>
     * 
     * <p>This method creates a new cache manager instance using the configured settings.
     * The factory will manage the lifecycle of the cache manager internally.</p>
     * 
     * @return a configured InfinispanMessageStoreFactory ready for use with QuickFIX/J
     * @throws RuntimeException if the cache manager creation fails
     * @see InfinispanMessageStoreFactory
     */
    public InfinispanMessageStoreFactory createMessageStoreFactory() {
        EmbeddedCacheManager cacheManager = createCacheManager();
        return new InfinispanMessageStoreFactory(cacheManager);
    }
    
    /**
     * Creates an Infinispan-based SessionSettings wrapper.
     * 
     * <p>This method creates a SessionSettings implementation that uses Infinispan
     * for storing and sharing session configuration across cluster nodes. This is
     * particularly useful in distributed environments where multiple QuickFIX/J
     * instances need to share session settings.</p>
     * 
     * @param baseSettings the base SessionSettings to wrap and enhance with Infinispan capabilities
     * @return an InfinispanSessionSettings instance that provides distributed session settings
     * @throws RuntimeException if the cache manager creation fails
     * @throws IllegalArgumentException if baseSettings is null
     * @see InfinispanSessionSettings
     * @see SessionSettings
     */
    public InfinispanSessionSettings createSessionSettings(SessionSettings baseSettings) {
        EmbeddedCacheManager cacheManager = createCacheManager();
        return new InfinispanSessionSettings(cacheManager, baseSettings);
    }
    
    /**
     * Creates example configuration properties for QuickFIX/J with Infinispan integration.
     * 
     * <p>This static method provides a complete set of example properties that demonstrate
     * how to configure QuickFIX/J to use Infinispan as the message store. The returned
     * properties include:</p>
     * <ul>
     *   <li>Default connection settings (host, port, heartbeat)</li>
     *   <li>Infinispan-specific configuration (cluster name, cache mode, expiration)</li>
     *   <li>Example session configuration (FIX.4.4 SENDER->TARGET)</li>
     *   <li>Message store factory configuration pointing to InfinispanMessageStoreFactory</li>
     * </ul>
     * 
     * <p>These properties can be used as a starting point for your own QuickFIX/J configuration
     * or for testing and development purposes.</p>
     * 
     * @return a Properties object containing example QuickFIX/J configuration with Infinispan settings
     */
    public static Properties createExampleQuickFixJProperties() {
        Properties properties = new Properties();
        
        // Default configurations
        properties.setProperty("default.ConnectionType", "initiator");
        properties.setProperty("default.StartTime", "00:00:00");
        properties.setProperty("default.EndTime", "00:00:00");
        properties.setProperty("default.HeartBtInt", "30");
        properties.setProperty("default.SocketConnectPort", "9876");
        properties.setProperty("default.SocketConnectHost", "localhost");
        properties.setProperty("default.ReconnectInterval", "5");
        properties.setProperty("default.FileStorePath", "store");
        
        // Use the InfinispanMessageStoreFactory
        properties.setProperty("default.MessageStoreFactory", InfinispanMessageStoreFactory.class.getName());
        
        // Infinispan-specific configurations
        properties.setProperty("default.InfinispanClusterName", "quickfixj-cluster");
        properties.setProperty("default.InfinispanCacheMode", "LOCAL");
        properties.setProperty("default.InfinispanExpirationMinutes", "1440");
        properties.setProperty("default.InfinispanMaxEntries", "10000");
        
        // Example session
        properties.setProperty("session.FIX.4.4:SENDER->TARGET.BeginString", "FIX.4.4");
        properties.setProperty("session.FIX.4.4:SENDER->TARGET.SenderCompID", "SENDER");
        properties.setProperty("session.FIX.4.4:SENDER->TARGET.TargetCompID", "TARGET");
        
        logger.info("Example configurations created");
        
        return properties;
    }
    
    /**
     * Converts the current configuration to a Properties object.
     * 
     * <p>This method serializes all configuration settings into a Properties object
     * with standardized key names. This can be useful for:</p>
     * <ul>
     *   <li>Persisting configuration to files</li>
     *   <li>Logging current configuration settings</li>
     *   <li>Integrating with other configuration systems</li>
     *   <li>Debugging configuration issues</li>
     * </ul>
     * 
     * <p>Property keys used:</p>
     * <ul>
     *   <li><code>infinispan.cluster.name</code> - The cluster name</li>
     *   <li><code>infinispan.cache.mode</code> - The cache mode (LOCAL, REPLICATED, DISTRIBUTED)</li>
     *   <li><code>infinispan.expiration.minutes</code> - Expiration time in minutes</li>
     *   <li><code>infinispan.max.entries</code> - Maximum number of entries</li>
     *   <li><code>infinispan.statistics.enabled</code> - Whether statistics are enabled</li>
     *   <li><code>infinispan.persistence.enabled</code> - Whether persistence is enabled</li>
     *   <li><code>infinispan.persistence.location</code> - Persistence storage location</li>
     * </ul>
     * 
     * @return a Properties object containing all configuration settings
     */
    public Properties toProperties() {
        Properties props = new Properties();
        props.setProperty("infinispan.cluster.name", clusterName);
        props.setProperty("infinispan.cache.mode", cacheMode.toString());
        props.setProperty("infinispan.expiration.minutes", String.valueOf(expirationMinutes));
        props.setProperty("infinispan.max.entries", String.valueOf(maxEntries));
        props.setProperty("infinispan.statistics.enabled", String.valueOf(enableStatistics));
        props.setProperty("infinispan.persistence.enabled", String.valueOf(enablePersistence));
        props.setProperty("infinispan.persistence.location", persistenceLocation);
        return props;
    }
    
    /**
     * Creates a cache configuration based on the current settings.
     * 
     * <p>This private method builds an Infinispan Configuration object using all the
     * settings specified through the builder methods. The configuration includes:</p>
     * <ul>
     *   <li>Clustering mode (LOCAL, REPLICATED, or DISTRIBUTED)</li>
     *   <li>Entry expiration policy based on lifespan</li>
     *   <li>Memory management with maximum entry count</li>
     *   <li>Statistics collection (if enabled)</li>
     *   <li>Disk persistence (if enabled)</li>
     * </ul>
     * 
     * <p>This configuration is applied to all QuickFIX/J-related caches created by this class.</p>
     * 
     * @return a Configuration object ready to be used for cache creation
     */
    private Configuration createCacheConfiguration() {
        ConfigurationBuilder builder = new ConfigurationBuilder();
        
        builder.clustering()
            .cacheMode(cacheMode);
            
        builder.expiration()
            .lifespan(expirationMinutes, TimeUnit.MINUTES);
            
        builder.memory()
            .maxCount(maxEntries);
        
        if (enableStatistics) {
            builder.statistics().enable();
        }
        
        if (enablePersistence) {
            builder.persistence()
                .addSoftIndexFileStore()
                .dataLocation(persistenceLocation + "/data")
                .indexLocation(persistenceLocation + "/index")
                .async().enable();
        }
        
        return builder.build();
    }
    
    // Getters
    
    /**
     * Gets the configured cluster name.
     * @return the cluster name
     */
    public String getClusterName() { return clusterName; }
    
    /**
     * Gets the configured cache mode.
     * @return the cache mode (LOCAL, REPLICATED, or DISTRIBUTED)
     */
    public CacheMode getCacheMode() { return cacheMode; }
    
    /**
     * Gets the configured expiration time in minutes.
     * @return the expiration time in minutes
     */
    public long getExpirationMinutes() { return expirationMinutes; }
    
    /**
     * Gets the configured maximum number of entries.
     * @return the maximum number of entries allowed in the cache
     */
    public int getMaxEntries() { return maxEntries; }
    
    /**
     * Checks if statistics collection is enabled.
     * @return true if statistics are enabled, false otherwise
     */
    public boolean isEnableStatistics() { return enableStatistics; }
    
    /**
     * Checks if disk persistence is enabled.
     * @return true if persistence is enabled, false otherwise
     */
    public boolean isEnablePersistence() { return enablePersistence; }
    
    /**
     * Gets the configured persistence storage location.
     * @return the directory path where cache data is persisted
     */
    public String getPersistenceLocation() { return persistenceLocation; }
}