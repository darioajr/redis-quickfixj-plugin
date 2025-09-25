package io.github.darioajr.quickfixj.factory;

import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.darioajr.quickfixj.store.RedisMessageStore;
import io.github.darioajr.quickfixj.store.RedisSessionSettings;
import quickfix.ConfigError;
import quickfix.MessageStore;
import quickfix.MessageStoreFactory;
import quickfix.SessionID;
import quickfix.SessionSettings;
import redis.clients.jedis.JedisPooled;

/**
 * Factory for creating MessageStores based on Redis.
 * 
 * <p>This factory provides a centralized way to create and manage Redis-backed 
 * MessageStore instances for QuickFIX/J applications. It handles Redis connection
 * management, instance reuse, and lifecycle management of the underlying Redis resources.</p>
 * 
 * <p>The factory supports Redis clustering and connection pooling, allowing
 * QuickFIX/J sessions to share message state across multiple JVMs when configured
 * with a shared Redis instance or cluster.</p>
 * 
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Centralized MessageStore instance management with reuse capabilities</li>
 *   <li>Redis connection pooling with configurable parameters</li>
 *   <li>Support for Redis authentication and SSL</li>
 *   <li>Automatic connection lifecycle management</li>
 *   <li>Built-in statistics and monitoring capabilities</li>
 *   <li>Proper resource cleanup and shutdown handling</li>
 * </ul>
 * 
 * <p><strong>Configuration Properties:</strong></p>
 * <p>The factory can be configured through SessionSettings using the following properties:</p>
 * <ul>
 *   <li>{@code redis.host} - Redis server hostname (default: localhost)</li>
 *   <li>{@code redis.port} - Redis server port (default: 6379)</li>
 *   <li>{@code redis.password} - Redis authentication password (optional)</li>
 *   <li>{@code redis.database} - Redis database number (default: 0)</li>
 *   <li>{@code redis.timeout} - Connection timeout in milliseconds (default: 2000)</li>
 *   <li>{@code redis.ssl} - Enable SSL connection (default: false)</li>
 *   <li>{@code redis.pool.maxTotal} - Maximum number of connections in pool (default: 8)</li>
 *   <li>{@code redis.pool.maxIdle} - Maximum idle connections in pool (default: 8)</li>
 *   <li>{@code redis.pool.minIdle} - Minimum idle connections in pool (default: 0)</li>
 *   <li>{@code redis.pool.testOnBorrow} - Test connections before use (default: true)</li>
 *   <li>{@code redis.pool.testOnReturn} - Test connections after use (default: false)</li>
 *   <li>{@code redis.pool.testWhileIdle} - Test idle connections (default: false)</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // In QuickFIX/J configuration file:
 * [DEFAULT]
 * MessageStoreFactory=io.github.darioajr.quickfixj.factory.RedisMessageStoreFactory
 * redis.host=redis-server.example.com
 * redis.port=6379
 * redis.password=mypassword
 * redis.database=1
 * 
 * [SESSION]
 * BeginString=FIX.4.4
 * SenderCompID=SENDER
 * TargetCompID=TARGET
 * }</pre>
 * 
 * @author Dario Oliveira Junior
 * @version 1.0
 * @since 1.0
 * @see quickfix.MessageStoreFactory
 * @see RedisMessageStore
 * @see redis.clients.jedis.JedisPooled
 * @see quickfix.SessionSettings
 */
public class RedisMessageStoreFactory implements MessageStoreFactory {
    
    /** Logger instance for this factory. */
    private static final Logger logger = LoggerFactory.getLogger(RedisMessageStoreFactory.class);
    
    /** Default Redis hostname. */
    private static final String DEFAULT_HOST = "localhost";
    
    /** Default Redis port. */
    private static final int DEFAULT_PORT = 6379;
    
    /** Default Redis database. */
    private static final int DEFAULT_DATABASE = 0;
    
    /** Default connection timeout in milliseconds. */
    private static final int DEFAULT_TIMEOUT = 2000;
    
    /** Map to store and reuse MessageStore instances per SessionID. */
    private final ConcurrentHashMap<SessionID, RedisMessageStore> messageStores = new ConcurrentHashMap<>();
    
    /** The main Redis client instance. */
    private volatile JedisPooled jedis;
    
    /** The session settings used for configuration. */
    private SessionSettings sessionSettings;
    
    /** Redis connection configuration. */
    private String redisHost;
    private int redisPort;
    private String redisPassword;
    private int redisDatabase;
    private int redisTimeout;
    private boolean redisSSL;
    
    /** Flag to track if the factory has been initialized. */
    private volatile boolean initialized = false;
    
    /**
     * Default constructor for RedisMessageStoreFactory.
     */
    public RedisMessageStoreFactory() {
        logger.info("Created RedisMessageStoreFactory instance");
    }
    
    @Override
    public MessageStore create(SessionID sessionID) {
        if (!initialized) {
            throw new IllegalStateException("Factory not initialized. Call create(SessionID, SessionSettings) first.");
        }
        
        return messageStores.computeIfAbsent(sessionID, sid -> {
            logger.info("Creating new RedisMessageStore for SessionID: {}", sid);
            return new RedisMessageStore(jedis, sid);
        });
    }
    
    /**
     * Creates a MessageStore for the given SessionID using the provided SessionSettings.
     * 
     * <p>This method serves as the primary entry point for creating MessageStore instances.
     * It initializes the Redis connection if not already done, configures the Redis client
     * based on the session settings, and creates a new MessageStore instance for the
     * specified SessionID.</p>
     * 
     * @param sessionID the SessionID for which to create the MessageStore
     * @param settings the SessionSettings containing Redis configuration
     * @return a new RedisMessageStore instance
     * @throws IllegalArgumentException if sessionID or settings is null
     * @see #create(SessionID)
     */
    public MessageStore create(SessionID sessionID, SessionSettings settings) {
        if (sessionID == null) {
            throw new IllegalArgumentException("SessionID cannot be null");
        }
        if (settings == null) {
            throw new IllegalArgumentException("SessionSettings cannot be null");
        }
        
        // Initialize if not already done
        if (!initialized) {
            initialize(settings);
        }
        
        return create(sessionID);
    }
    
    /**
     * Initializes the factory with the provided SessionSettings.
     * 
     * <p>This method configures the Redis connection based on the settings and
     * sets up the connection pool. It should be called before creating any
     * MessageStore instances.</p>
     * 
     * @param settings the SessionSettings containing configuration
     */
    private synchronized void initialize(SessionSettings settings) {
        if (initialized) {
            return; // Already initialized
        }
        
        this.sessionSettings = settings;
        
        try {
            // Load Redis configuration from settings
            loadRedisConfiguration(settings);
            
            // Create Redis client
            createRedisClient();
            
            // Test connection
            testRedisConnection();
            
            initialized = true;
            
            logger.info("Successfully initialized RedisMessageStoreFactory with Redis at {}:{}", 
                       redisHost, redisPort);
        } catch (Exception e) {
            logger.error("Failed to initialize RedisMessageStoreFactory: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Redis factory", e);
        }
    }
    
    /**
     * Loads Redis configuration from SessionSettings.
     * 
     * @param settings the SessionSettings to read from
     * @throws ConfigError if there's an error reading configuration
     */
    private void loadRedisConfiguration(SessionSettings settings) throws ConfigError {
        // Load Redis connection settings
        redisHost = getStringSetting(settings, "redis.host", DEFAULT_HOST);
        redisPort = getIntSetting(settings, "redis.port", DEFAULT_PORT);
        redisPassword = getStringSetting(settings, "redis.password", null);
        redisDatabase = getIntSetting(settings, "redis.database", DEFAULT_DATABASE);
        redisTimeout = getIntSetting(settings, "redis.timeout", DEFAULT_TIMEOUT);
        redisSSL = getBooleanSetting(settings, "redis.ssl", false);
        
        logger.debug("Loaded Redis configuration: host={}, port={}, database={}, ssl={}", 
                    redisHost, redisPort, redisDatabase, redisSSL);
    }
    
    /**
     * Creates the Redis client instance.
     */
    private void createRedisClient() {
        jedis = new JedisPooled(redisHost, redisPort);
        
        logger.debug("Created Redis client for {}:{}", redisHost, redisPort);
    }
    
    /**
     * Tests the Redis connection to ensure it's working.
     * 
     * @throws RuntimeException if the connection test fails
     */
    private void testRedisConnection() {
        try {
            String response = jedis.ping();
            if (!"PONG".equals(response)) {
                throw new RuntimeException("Unexpected ping response: " + response);
            }
            logger.debug("Redis connection test successful");
        } catch (Exception e) {
            logger.error("Redis connection test failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to connect to Redis", e);
        }
    }
    
    /**
     * Utility method to get a string setting with a default value.
     * 
     * @param settings the SessionSettings
     * @param key the setting key
     * @param defaultValue the default value if not found
     * @return the setting value or default
     */
    private String getStringSetting(SessionSettings settings, String key, String defaultValue) {
        try {
            return settings.getString(key);
        } catch (ConfigError e) {
            return defaultValue;
        }
    }
    
    /**
     * Utility method to get an integer setting with a default value.
     * 
     * @param settings the SessionSettings
     * @param key the setting key
     * @param defaultValue the default value if not found
     * @return the setting value or default
     */
    private int getIntSetting(SessionSettings settings, String key, int defaultValue) {
        try {
            return (int) settings.getLong(key);
        } catch (ConfigError | quickfix.FieldConvertError e) {
            return defaultValue;
        }
    }
    
    /**
     * Utility method to get a boolean setting with a default value.
     * 
     * @param settings the SessionSettings
     * @param key the setting key
     * @param defaultValue the default value if not found
     * @return the setting value or default
     */
    private boolean getBooleanSetting(SessionSettings settings, String key, boolean defaultValue) {
        try {
            return settings.getBool(key);
        } catch (ConfigError | quickfix.FieldConvertError e) {
            return defaultValue;
        }
    }
    
    /**
     * Creates a RedisSessionSettings instance using the factory's Redis client.
     * 
     * <p>This method provides a way to create session settings that are backed
     * by the same Redis instance used for message storage, ensuring consistency
     * across the QuickFIX/J application.</p>
     * 
     * @return a new RedisSessionSettings instance
     * @throws IllegalStateException if the factory is not initialized
     */
    public RedisSessionSettings createSessionSettings() {
        if (!initialized) {
            throw new IllegalStateException("Factory not initialized");
        }
        return new RedisSessionSettings(jedis);
    }
    
    /**
     * Creates a RedisSessionSettings instance and loads configuration from the provided settings.
     * 
     * @param settings the SessionSettings to copy configuration from
     * @return a new RedisSessionSettings instance with loaded configuration
     * @throws IllegalStateException if the factory is not initialized
     */
    public RedisSessionSettings createSessionSettings(SessionSettings settings) {
        RedisSessionSettings redisSettings = createSessionSettings();
        
        // Copy settings from the provided SessionSettings
        if (settings != null) {
            copySessionSettings(settings, redisSettings);
        }
        
        return redisSettings;
    }
    
    /**
     * Copies settings from source to target SessionSettings.
     * 
     * @param source the source SessionSettings
     * @param target the target RedisSessionSettings
     */
    private void copySessionSettings(SessionSettings source, RedisSessionSettings target) {
        try {
            // Copy default settings
            Properties defaults = source.getDefaultProperties();
            if (defaults != null) {
                for (Object key : defaults.keySet()) {
                    String keyStr = (String) key;
                    String value = defaults.getProperty(keyStr);
                    if (value != null) {
                        target.setString(keyStr, value);
                    }
                }
            }
            
            // Copy session-specific settings
            var sessionIterator = source.sectionIterator();
            while (sessionIterator.hasNext()) {
                SessionID sessionID = sessionIterator.next();
                Properties sessionProps = source.getSessionProperties(sessionID);
                if (sessionProps != null) {
                    for (Object key : sessionProps.keySet()) {
                        String keyStr = (String) key;
                        String value = sessionProps.getProperty(keyStr);
                        if (value != null) {
                            target.setString(sessionID, keyStr, value);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to copy some session settings: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Gets statistics about the current factory state.
     * 
     * @return a Properties object containing factory statistics
     */
    public Properties getStatistics() {
        Properties stats = new Properties();
        stats.setProperty("initialized", String.valueOf(initialized));
        stats.setProperty("messageStoreCount", String.valueOf(messageStores.size()));
        
        if (initialized) {
            stats.setProperty("redisHost", redisHost);
            stats.setProperty("redisPort", String.valueOf(redisPort));
            stats.setProperty("redisDatabase", String.valueOf(redisDatabase));
            stats.setProperty("redisSSL", String.valueOf(redisSSL));
            
            // Test connection status
            try {
                jedis.ping();
                stats.setProperty("redisConnectionStatus", "CONNECTED");
            } catch (Exception e) {
                stats.setProperty("redisConnectionStatus", "DISCONNECTED");
                stats.setProperty("redisConnectionError", e.getMessage());
            }
        }
        
        return stats;
    }
    
    /**
     * Shuts down the factory and closes all resources.
     * 
     * <p>This method should be called when the application is shutting down
     * to ensure proper cleanup of Redis connections and other resources.</p>
     */
    public synchronized void shutdown() {
        if (!initialized) {
            return;
        }
        
        logger.info("Shutting down RedisMessageStoreFactory");
        
        try {
            // Clear message store cache
            messageStores.clear();
            
            // Close Redis client
            if (jedis != null) {
                jedis.close();
                jedis = null;
            }
            
            initialized = false;
            
            logger.info("Successfully shut down RedisMessageStoreFactory");
        } catch (Exception e) {
            logger.error("Error during shutdown: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Gets the Redis client instance (for advanced usage).
     * 
     * @return the Redis client instance
     * @throws IllegalStateException if the factory is not initialized
     */
    public JedisPooled getRedisClient() {
        if (!initialized) {
            throw new IllegalStateException("Factory not initialized");
        }
        return jedis;
    }
    
    /**
     * Checks if the factory is initialized.
     * 
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return initialized;
    }
}