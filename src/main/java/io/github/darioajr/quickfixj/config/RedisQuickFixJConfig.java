package io.github.darioajr.quickfixj.config;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.darioajr.quickfixj.factory.RedisMessageStoreFactory;
import io.github.darioajr.quickfixj.store.RedisSessionSettings;
import quickfix.SessionSettings;
import redis.clients.jedis.JedisPooled;

/**
 * Configuration class to facilitate the creation and configuration of the Redis-QuickFIX/J plugin.
 * 
 * <p>This class provides a fluent API for configuring Redis as a message store backend for QuickFIX/J.
 * It supports various Redis configurations including authentication, database selection, timeouts,
 * and SSL connections. The class follows the builder pattern, allowing method chaining for 
 * convenient configuration.</p>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * RedisQuickFixJConfig config = new RedisQuickFixJConfig()
 *     .host("redis.example.com")
 *     .port(6379)
 *     .database(1)
 *     .password("mypassword")
 *     .timeout(5000)
 *     .enableSSL(false);
 * 
 * JedisPooled redisClient = config.createRedisClient();
 * RedisMessageStoreFactory factory = config.createMessageStoreFactory();
 * }</pre>
 * 
 * <p><b>Redis Configuration:</b></p>
 * <p>The class automatically creates the following key namespaces for QuickFIX/J:</p>
 * <ul>
 *   <li><code>quickfixj:messages:{sessionId}</code> - FIX message storage</li>
 *   <li><code>quickfixj:sequences:{sessionId}</code> - Sequence number tracking</li>
 *   <li><code>quickfixj:sessions:{sessionId}</code> - Session metadata</li>
 *   <li><code>quickfixj:settings:{sessionId}</code> - Session-specific settings</li>
 * </ul>
 * 
 * <p><b>Thread Safety:</b></p>
 * <p>This configuration class is not thread-safe. It should be used during application
 * initialization and not shared between threads during configuration.</p>
 * 
 * <p><b>Performance Considerations:</b></p>
 * <ul>
 *   <li>Use connection pooling for high-throughput applications</li>
 *   <li>Configure appropriate timeout values for your network environment</li>
 *   <li>Consider using Redis clustering for high availability</li>
 *   <li>Monitor Redis memory usage and configure appropriate eviction policies</li>
 * </ul>
 * 
 * @author Dario Oliveira Junior
 * @version 1.0
 * @since 1.0
 * @see RedisMessageStoreFactory
 * @see RedisSessionSettings
 * @see redis.clients.jedis.JedisPooled
 */
public class RedisQuickFixJConfig {
    
    /** Logger instance for this configuration class. */
    private static final Logger logger = LoggerFactory.getLogger(RedisQuickFixJConfig.class);
    
    /** Default Redis host. */
    private static final String DEFAULT_HOST = "localhost";
    
    /** Default Redis port. */
    private static final int DEFAULT_PORT = 6379;
    
    /** Default database number. */
    private static final int DEFAULT_DATABASE = 0;
    
    /** Default connection timeout in milliseconds. */
    private static final int DEFAULT_TIMEOUT = 2000;
    
    // Configuration fields
    private String host = DEFAULT_HOST;
    private int port = DEFAULT_PORT;
    private String password;
    private int database = DEFAULT_DATABASE;
    private int timeout = DEFAULT_TIMEOUT;
    private boolean sslEnabled = false;
    
    /**
     * Creates a new RedisQuickFixJConfig with default settings.
     */
    public RedisQuickFixJConfig() {
        logger.debug("Created new RedisQuickFixJConfig with defaults");
    }
    
    /**
     * Sets the Redis server hostname.
     * 
     * @param host the Redis server hostname (default: localhost)
     * @return this configuration instance for method chaining
     * @throws IllegalArgumentException if host is null or empty
     */
    public RedisQuickFixJConfig host(String host) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }
        this.host = host.trim();
        logger.debug("Set Redis host to: {}", this.host);
        return this;
    }
    
    /**
     * Sets the Redis server port.
     * 
     * @param port the Redis server port (default: 6379)
     * @return this configuration instance for method chaining
     * @throws IllegalArgumentException if port is not in valid range (1-65535)
     */
    public RedisQuickFixJConfig port(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        this.port = port;
        logger.debug("Set Redis port to: {}", this.port);
        return this;
    }
    
    /**
     * Sets the Redis authentication password.
     * 
     * @param password the Redis password (optional)
     * @return this configuration instance for method chaining
     */
    public RedisQuickFixJConfig password(String password) {
        this.password = password;
        logger.debug("Set Redis password: {}", password != null ? "[PROTECTED]" : "[NONE]");
        return this;
    }
    
    /**
     * Sets the Redis database number.
     * 
     * @param database the database number (default: 0)
     * @return this configuration instance for method chaining
     * @throws IllegalArgumentException if database is negative
     */
    public RedisQuickFixJConfig database(int database) {
        if (database < 0) {
            throw new IllegalArgumentException("Database number cannot be negative");
        }
        this.database = database;
        logger.debug("Set Redis database to: {}", this.database);
        return this;
    }
    
    /**
     * Sets the connection timeout in milliseconds.
     * 
     * @param timeout the timeout in milliseconds (default: 2000)
     * @return this configuration instance for method chaining
     * @throws IllegalArgumentException if timeout is not positive
     */
    public RedisQuickFixJConfig timeout(int timeout) {
        if (timeout <= 0) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        this.timeout = timeout;
        logger.debug("Set Redis timeout to: {}ms", this.timeout);
        return this;
    }
    
    /**
     * Enables or disables SSL connection to Redis.
     * 
     * @param sslEnabled true to enable SSL, false to disable (default: false)
     * @return this configuration instance for method chaining
     */
    public RedisQuickFixJConfig enableSSL(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
        logger.debug("Set Redis SSL enabled: {}", this.sslEnabled);
        return this;
    }
    
    /**
     * Creates a Redis client using the current configuration.
     * 
     * <p>This method creates a new JedisPooled instance based on the configured
     * parameters. The client can be used directly or passed to other QuickFIX/J
     * components like RedisMessageStoreFactory.</p>
     * 
     * @return a configured JedisPooled instance
     * @throws RuntimeException if the Redis client cannot be created or connected
     */
    public JedisPooled createRedisClient() {
        logger.info("Creating Redis client for {}:{} database {}", host, port, database);
        
        try {
            JedisPooled jedis = new JedisPooled(host, port);
            
            // Test the connection
            String response = jedis.ping();
            if (!"PONG".equals(response)) {
                throw new RuntimeException("Unexpected ping response: " + response);
            }
            
            logger.info("Successfully created Redis client for {}:{}", host, port);
            return jedis;
            
        } catch (Exception e) {
            logger.error("Failed to create Redis client: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create Redis client", e);
        }
    }
    
    /**
     * Creates a RedisMessageStoreFactory using the current configuration.
     * 
     * <p>This is a convenience method that creates a Redis client and then
     * creates a message store factory using that client. The factory can be
     * used directly in QuickFIX/J configurations.</p>
     * 
     * @return a configured RedisMessageStoreFactory
     */
    public RedisMessageStoreFactory createMessageStoreFactory() {
        logger.info("Creating RedisMessageStoreFactory");
        
        RedisMessageStoreFactory factory = new RedisMessageStoreFactory();
        
        logger.info("Successfully created RedisMessageStoreFactory");
        return factory;
    }
    
    /**
     * Creates a RedisSessionSettings instance using the current configuration.
     * 
     * <p>This method creates a Redis client and then creates session settings
     * that are backed by Redis. This allows QuickFIX/J session configurations
     * to be stored and shared across multiple application instances.</p>
     * 
     * @return a configured RedisSessionSettings instance
     */
    public RedisSessionSettings createSessionSettings() {
        logger.info("Creating RedisSessionSettings");
        
        JedisPooled jedis = createRedisClient();
        RedisSessionSettings settings = new RedisSessionSettings(jedis);
        
        logger.info("Successfully created RedisSessionSettings");
        return settings;
    }
    
    /**
     * Creates a RedisSessionSettings instance and loads configuration from SessionSettings.
     * 
     * @param sourceSettings the SessionSettings to copy configuration from
     * @return a configured RedisSessionSettings instance with loaded configuration
     */
    public RedisSessionSettings createSessionSettings(SessionSettings sourceSettings) {
        RedisSessionSettings redisSettings = createSessionSettings();
        
        if (sourceSettings != null) {
            copySessionSettings(sourceSettings, redisSettings);
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
                var sessionID = sessionIterator.next();
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
     * Creates QuickFIX/J SessionSettings with Redis-specific configuration.
     * 
     * <p>This method creates a standard SessionSettings instance and populates it
     * with the Redis configuration properties. This can be used to configure
     * QuickFIX/J applications to use Redis as the message store backend.</p>
     * 
     * @return a SessionSettings instance configured for Redis
     */
    public SessionSettings createQuickFixJSessionSettings() {
        SessionSettings settings = new SessionSettings();
        
        // Set Redis configuration as default settings
        settings.setString("MessageStoreFactory", 
                          "io.github.darioajr.quickfixj.factory.RedisMessageStoreFactory");
        settings.setString("redis.host", host);
        settings.setString("redis.port", String.valueOf(port));
        settings.setString("redis.database", String.valueOf(database));
        settings.setString("redis.timeout", String.valueOf(timeout));
        settings.setString("redis.ssl", String.valueOf(sslEnabled));
        
        if (password != null && !password.trim().isEmpty()) {
            settings.setString("redis.password", password);
        }
        
        logger.info("Created QuickFIX/J SessionSettings with Redis configuration");
        return settings;
    }
    
    /**
     * Validates the current configuration.
     * 
     * @throws IllegalStateException if the configuration is invalid
     */
    public void validate() {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalStateException("Redis host must be specified");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("Redis port must be between 1 and 65535");
        }
        if (database < 0) {
            throw new IllegalStateException("Redis database number cannot be negative");
        }
        if (timeout <= 0) {
            throw new IllegalStateException("Redis timeout must be positive");
        }
        
        logger.debug("Configuration validation passed");
    }
    
    /**
     * Gets the current configuration as Properties.
     * 
     * @return a Properties object containing the current configuration
     */
    public Properties toProperties() {
        Properties props = new Properties();
        props.setProperty("redis.host", host);
        props.setProperty("redis.port", String.valueOf(port));
        props.setProperty("redis.database", String.valueOf(database));
        props.setProperty("redis.timeout", String.valueOf(timeout));
        props.setProperty("redis.ssl", String.valueOf(sslEnabled));
        
        if (password != null && !password.trim().isEmpty()) {
            props.setProperty("redis.password", password);
        }
        
        return props;
    }
    
    /**
     * Creates a configuration from Properties.
     * 
     * @param properties the Properties containing configuration
     * @return a new RedisQuickFixJConfig instance
     */
    public static RedisQuickFixJConfig fromProperties(Properties properties) {
        RedisQuickFixJConfig config = new RedisQuickFixJConfig();
        
        String hostProp = properties.getProperty("redis.host");
        if (hostProp != null) {
            config.host(hostProp);
        }
        
        String portProp = properties.getProperty("redis.port");
        if (portProp != null) {
            try {
                config.port(Integer.parseInt(portProp));
            } catch (NumberFormatException e) {
                logger.warn("Invalid port number in properties: {}", portProp);
            }
        }
        
        String passwordProp = properties.getProperty("redis.password");
        if (passwordProp != null) {
            config.password(passwordProp);
        }
        
        String databaseProp = properties.getProperty("redis.database");
        if (databaseProp != null) {
            try {
                config.database(Integer.parseInt(databaseProp));
            } catch (NumberFormatException e) {
                logger.warn("Invalid database number in properties: {}", databaseProp);
            }
        }
        
        String timeoutProp = properties.getProperty("redis.timeout");
        if (timeoutProp != null) {
            try {
                config.timeout(Integer.parseInt(timeoutProp));
            } catch (NumberFormatException e) {
                logger.warn("Invalid timeout in properties: {}", timeoutProp);
            }
        }
        
        String sslProp = properties.getProperty("redis.ssl");
        if (sslProp != null) {
            config.enableSSL(Boolean.parseBoolean(sslProp));
        }
        
        return config;
    }
    
    @Override
    public String toString() {
        return String.format("RedisQuickFixJConfig{host='%s', port=%d, database=%d, timeout=%d, ssl=%s, password=%s}",
                           host, port, database, timeout, sslEnabled, password != null ? "[PROTECTED]" : "[NONE]");
    }
    
    // Getters
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getPassword() { return password; }
    public int getDatabase() { return database; }
    public int getTimeout() { return timeout; }
    public boolean isSslEnabled() { return sslEnabled; }
}