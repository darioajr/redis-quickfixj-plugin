package io.github.darioajr.quickfixj.factory;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.darioajr.quickfixj.store.InfinispanMessageStore;
import io.github.darioajr.quickfixj.store.InfinispanSessionSettings;
import quickfix.MessageStore;
import quickfix.MessageStoreFactory;
import quickfix.SessionID;
import quickfix.SessionSettings;

/**
 * Factory for creating MessageStores based on Infinispan.
 * 
 * <p>This factory provides a centralized way to create and manage Infinispan-backed 
 * MessageStore instances for QuickFIX/J applications. It handles cache configuration,
 * instance reuse, and lifecycle management of the underlying Infinispan resources.</p>
 * 
 * <p>The factory supports both embedded and clustered Infinispan configurations, allowing
 * QuickFIX/J sessions to share message state across multiple JVMs when configured in
 * a distributed mode.</p>
 * 
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Centralized MessageStore instance management with reuse capabilities</li>
 *   <li>Configurable cache modes (LOCAL, REPLICATED, DISTRIBUTED)</li>
 *   <li>Automatic cache lifecycle management</li>
 *   <li>Support for custom Infinispan configuration files</li>
 *   <li>Built-in statistics and monitoring capabilities</li>
 *   <li>Proper resource cleanup and shutdown handling</li>
 * </ul>
 * 
 * <p><strong>Configuration Properties:</strong></p>
 * <p>The factory can be configured through SessionSettings using the following properties:</p>
 * <ul>
 *   <li>{@code infinispan.cache.mode} - Cache clustering mode (LOCAL, REPLICATED, DISTRIBUTED)</li>
 *   <li>{@code infinispan.cache.config.file} - Path to custom Infinispan configuration file</li>
 *   <li>{@code infinispan.cluster.name} - Name of the cluster for distributed caches</li>
 *   <li>{@code infinispan.cache.expiration.minutes} - Cache entry expiration time in minutes</li>
 *   <li>{@code infinispan.cache.max.entries} - Maximum number of entries per cache</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * SessionSettings settings = new SessionSettings("quickfixj.cfg");
 * InfinispanMessageStoreFactory factory = new InfinispanMessageStoreFactory();
 * factory.configure(settings);
 * 
 * // Factory is now ready to create MessageStore instances
 * MessageStore store = factory.create(sessionID);
 * }</pre>
 * 
 * <p><strong>Thread Safety:</strong></p>
 * <p>This factory is thread-safe and can be used concurrently to create MessageStore
 * instances for multiple QuickFIX/J sessions.</p>
 * 
 * @author Dario Oliveira Junior
 * @version 1.0
 * @since 1.0
 * @see InfinispanMessageStore
 * @see InfinispanSessionSettings
 * @see quickfix.MessageStoreFactory
 */
public class InfinispanMessageStoreFactory implements MessageStoreFactory {
    
    /** Logger instance for this factory. */
    private static final Logger logger = LoggerFactory.getLogger(InfinispanMessageStoreFactory.class);
    
    /** Default cluster name used for distributed cache configurations. */
    private static final String DEFAULT_CLUSTER_NAME = "quickfixj-cluster";
    
    /** Default cache mode used when not specified in configuration. */
    private static final String DEFAULT_CACHE_MODE = "LOCAL";
    
    /** Default cache entry expiration time in minutes (24 hours). */
    private static final long DEFAULT_EXPIRATION_MINUTES = 1440; // 24 hours
    
    /** Default maximum number of entries per cache. */
    private static final int DEFAULT_MAX_ENTRIES = 10000;
    
    /** Configuration property name for specifying the cluster name for distributed caches. */
    public static final String CLUSTER_NAME_PROPERTY = "InfinispanClusterName";
    
    /** Configuration property name for specifying the cache mode (LOCAL, REPLICATED, DISTRIBUTED). */
    public static final String CACHE_MODE_PROPERTY = "InfinispanCacheMode";
    
    /** Configuration property name for specifying cache entry expiration time in minutes. */
    public static final String EXPIRATION_MINUTES_PROPERTY = "InfinispanExpirationMinutes";
    
    /** Configuration property name for specifying the maximum number of entries per cache. */
    public static final String MAX_ENTRIES_PROPERTY = "InfinispanMaxEntries";
    
    /** Configuration property name for specifying the path to a custom Infinispan configuration file. */
    public static final String CACHE_CONFIG_FILE_PROPERTY = "InfinispanConfigFile";
    
    /** The Infinispan cache manager instance. */
    private EmbeddedCacheManager cacheManager;
    
    /** Map of created MessageStore instances, keyed by SessionID for reuse. */
    private final ConcurrentHashMap<SessionID, InfinispanMessageStore> stores = new ConcurrentHashMap<>();
    
    /** The QuickFIX/J session settings. */
    private SessionSettings sessionSettings;
    
    /**
     * Default constructor.
     * 
     * <p>Creates a new factory instance. The factory must be configured using
     * {@link #configure(SessionSettings)} before it can create MessageStore instances.</p>
     */
    public InfinispanMessageStoreFactory() {
        // Empty constructor - initialization will be done in the configure method
    }
    
    /**
     * Constructor that accepts a pre-configured CacheManager.
     * 
     * <p>This constructor allows the factory to use an existing CacheManager instance,
     * useful for sharing cache configurations across multiple factories or when
     * custom Infinispan configuration is needed.</p>
     *
     * @param cacheManager the pre-configured Infinispan cache manager
     * @throws IllegalArgumentException if cacheManager is null
     */
    public InfinispanMessageStoreFactory(EmbeddedCacheManager cacheManager) {
        if (cacheManager == null) {
            throw new IllegalArgumentException("CacheManager cannot be null");
        }
        this.cacheManager = cacheManager;
        logger.info("InfinispanMessageStoreFactory initialized with existing CacheManager");
    }
    
    /**
     * Configures the factory with session settings.
     * 
     * <p>This method is automatically called by QuickFIX/J during initialization.
     * It sets up the Infinispan cache manager and configures all required caches
     * based on the provided session settings.</p>
     * 
     * <p>If a CacheManager was provided via constructor, this method will only
     * store the session settings without creating a new CacheManager.</p>
     *
     * @param settings the QuickFIX/J session settings containing Infinispan configuration
     * @throws RuntimeException if configuration fails
     */
    public void configure(SessionSettings settings) {
        this.sessionSettings = settings;
        
        if (cacheManager == null) {
            try {
                cacheManager = createCacheManager(settings);
                configureCaches();
                logger.info("InfinispanMessageStoreFactory configured successfully");
            } catch (Exception e) {
                logger.error("Error configuring InfinispanMessageStoreFactory", e);
                throw new RuntimeException("Failed to configure Infinispan", e);
            }
        }
    }
    
    /**
     * Creates a MessageStore instance for the specified session.
     * 
     * <p>This method implements the QuickFIX/J MessageStoreFactory interface.
     * It creates or reuses an InfinispanMessageStore instance for the given session ID.
     * Store instances are cached and reused for the same session ID to maintain
     * message sequence consistency.</p>
     * 
     * @param sessionID the QuickFIX/J session identifier
     * @return an InfinispanMessageStore instance for the session
     * @throws RuntimeException if store creation fails
     * @throws IllegalStateException if the factory is not configured
     */
    @Override
    public MessageStore create(SessionID sessionID) {
        if (cacheManager == null) {
            throw new IllegalStateException("Factory not configured. Call configure() first.");
        }
        
        try {
            InfinispanMessageStore store = stores.computeIfAbsent(sessionID, 
                sid -> new InfinispanMessageStore(cacheManager, sid));
            
            logger.debug("MessageStore created for session: {}", sessionID);
            return store;
        } catch (Exception e) {
            logger.error("Error creating MessageStore for session: {}", sessionID, e);
            throw new RuntimeException("Failed to create MessageStore", e);
        }
    }
    
    /**
     * Creates an Infinispan-based SessionSettings instance.
     * 
     * <p>This method creates a specialized SessionSettings implementation that
     * integrates with Infinispan for storing and retrieving session configuration
     * data in a distributed manner.</p>
     *
     * @return configured InfinispanSessionSettings instance
     * @throws IllegalStateException if the factory is not configured
     */
    public InfinispanSessionSettings createSessionSettings() {
        if (cacheManager == null) {
            throw new IllegalStateException("Factory not configured. Call configure() first.");
        }
        
        return new InfinispanSessionSettings(cacheManager, sessionSettings);
    }
    
    /**
     * Closes the factory and releases all resources.
     * 
     * <p>This method performs a graceful shutdown of the factory by:</p>
     * <ul>
     *   <li>Closing all created MessageStore instances</li>
     *   <li>Clearing the store cache</li>
     *   <li>Stopping the Infinispan cache manager</li>
     * </ul>
     * 
     * <p>After calling this method, the factory cannot be used to create new
     * MessageStore instances without reconfiguring it.</p>
     */
    public void close() {
        try {
            // Close all stores
            stores.values().forEach(InfinispanMessageStore::close);
            stores.clear();
            
            // Close the cache manager
            if (cacheManager != null && cacheManager.getStatus().allowInvocations()) {
                cacheManager.stop();
            }
            
            logger.info("InfinispanMessageStoreFactory closed");
        } catch (Exception e) {
            logger.error("Error closing InfinispanMessageStoreFactory", e);
        }
    }
    
    /**
     * Returns the CacheManager being used by this factory.
     * 
     * <p>This method provides access to the underlying Infinispan cache manager,
     * allowing for advanced cache operations and monitoring.</p>
     *
     * @return the EmbeddedCacheManager instance, or null if not configured
     */
    public EmbeddedCacheManager getCacheManager() {
        return cacheManager;
    }
    
    /**
     * Returns statistics for all caches managed by this factory.
     * 
     * <p>This method collects and returns runtime statistics for all QuickFIX/J
     * related caches including:</p>
     * <ul>
     *   <li>Cache sizes (number of entries in each cache)</li>
     *   <li>Cluster membership information</li>
     *   <li>Cache manager status</li>
     * </ul>
     *
     * @return Properties object containing cache statistics with keys like
     *         "messages.size", "sequences.size", "sessions.size", "settings.size",
     *         "cluster.members", and "cache.status"
     */
    public Properties getCacheStatistics() {
        Properties stats = new Properties();
        
        if (cacheManager != null) {
            try {
                Cache<?, ?> messagesCache = cacheManager.getCache("quickfixj-messages");
                Cache<?, ?> sequencesCache = cacheManager.getCache("quickfixj-sequences");
                Cache<?, ?> sessionsCache = cacheManager.getCache("quickfixj-sessions");
                Cache<?, ?> settingsCache = cacheManager.getCache("quickfixj-settings");
                
                stats.setProperty("messages.size", String.valueOf(messagesCache.size()));
                stats.setProperty("sequences.size", String.valueOf(sequencesCache.size()));
                stats.setProperty("sessions.size", String.valueOf(sessionsCache.size()));
                stats.setProperty("settings.size", String.valueOf(settingsCache.size()));
                
                // Handle cluster members safely for local mode
                List<?> members = cacheManager.getMembers();
                stats.setProperty("cluster.members", String.valueOf(members != null ? members.size() : 1));
                stats.setProperty("cache.status", cacheManager.getStatus().toString());
                
            } catch (Exception e) {
                logger.warn("Error obtaining cache statistics", e);
            }
        }
        
        return stats;
    }
    
    // Private Methods
    
    /**
     * Creates a new EmbeddedCacheManager based on session settings.
     * 
     * <p>This method attempts to create a cache manager by first checking for
     * a custom configuration file specified in the settings. If no custom file
     * is found, it creates a programmatic configuration with default clustering
     * settings.</p>
     *
     * @param settings the session settings containing Infinispan configuration
     * @return a configured EmbeddedCacheManager instance
     * @throws RuntimeException if cache manager creation fails
     */
    private EmbeddedCacheManager createCacheManager(SessionSettings settings) {
        try {
            // Check for custom configuration file
            String configFile = getConfigProperty(settings, CACHE_CONFIG_FILE_PROPERTY, null);
            if (configFile != null) {
                logger.info("Loading Infinispan configuration from: {}", configFile);
                try {
                    return new DefaultCacheManager(configFile);
                } catch (java.io.IOException e) {
                    logger.error("Error loading configuration file: {}", configFile, e);
                    throw new RuntimeException("Failed to load configuration", e);
                }
            }
            
            // Create configuration programmatically
            String clusterName = getConfigProperty(settings, CLUSTER_NAME_PROPERTY, DEFAULT_CLUSTER_NAME);
            
            GlobalConfiguration globalConfig = new GlobalConfigurationBuilder()
                .clusteredDefault()
                .transport().clusterName(clusterName)
                .build();
            
            return new DefaultCacheManager(globalConfig);
            
        } catch (RuntimeException e) {
            logger.error("Error creating CacheManager", e);
            throw e;
        }
    }
    
    /**
     * Configures all QuickFIX/J related caches with the specified settings.
     * 
     * <p>This method creates cache configurations based on session settings and
     * defines the following caches:</p>
     * <ul>
     *   <li>quickfixj-messages - for storing FIX messages</li>
     *   <li>quickfixj-sequences - for message sequence numbers</li>
     *   <li>quickfixj-sessions - for session state information</li>
     *   <li>quickfixj-settings - for session configuration data</li>
     * </ul>
     */
    private void configureCaches() {
        String cacheMode = getConfigProperty(sessionSettings, CACHE_MODE_PROPERTY, DEFAULT_CACHE_MODE);
        long expirationMinutes = Long.parseLong(
            getConfigProperty(sessionSettings, EXPIRATION_MINUTES_PROPERTY, String.valueOf(DEFAULT_EXPIRATION_MINUTES)));
        int maxEntries = Integer.parseInt(
            getConfigProperty(sessionSettings, MAX_ENTRIES_PROPERTY, String.valueOf(DEFAULT_MAX_ENTRIES)));
        
        Configuration cacheConfig = new ConfigurationBuilder()
            .clustering()
                .cacheMode(CacheMode.valueOf(cacheMode.toUpperCase()))
            .expiration()
                .lifespan(expirationMinutes, TimeUnit.MINUTES)
            .memory()
                .maxCount(maxEntries)
            .statistics()
                .enable()
            .build();
        
        // Define configurations for all caches
        cacheManager.defineConfiguration("quickfixj-messages", cacheConfig);
        cacheManager.defineConfiguration("quickfixj-sequences", cacheConfig);
        cacheManager.defineConfiguration("quickfixj-sessions", cacheConfig);
        cacheManager.defineConfiguration("quickfixj-settings", cacheConfig);
        
        // Initialize the caches
        cacheManager.getCache("quickfixj-messages");
        cacheManager.getCache("quickfixj-sequences");
        cacheManager.getCache("quickfixj-sessions");
        cacheManager.getCache("quickfixj-settings");
        
        logger.info("Caches configured - Mode: {}, Expiration: {} min, MaxEntries: {}", 
                   cacheMode, expirationMinutes, maxEntries);
    }
    
    /**
     * Retrieves a configuration property from session settings with fallback to default value.
     * 
     * <p>This utility method safely retrieves configuration properties from QuickFIX/J
     * session settings, handling potential ConfigError exceptions and providing default
     * values when properties are not found.</p>
     *
     * @param settings the session settings to query
     * @param property the property name to retrieve
     * @param defaultValue the default value to return if property is not found
     * @return the property value from settings, or defaultValue if not found or null settings
     */
    private String getConfigProperty(SessionSettings settings, String property, String defaultValue) {
        try {
            if (settings != null) {
                return settings.getString(property);
            }
        } catch (quickfix.ConfigError e) {
            logger.debug("Property not found: {}", property);
        }
        return defaultValue;
    }
}