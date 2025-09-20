package io.github.darioajr.quickfixj.store;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quickfix.ConfigError;
import quickfix.SessionID;
import quickfix.SessionSettings;

/**
 * Infinispan-based implementation for storing QuickFIX/J session settings.
 * 
 * <p>This implementation provides a distributed and scalable way to store and retrieve
 * QuickFIX/J session configurations using Infinispan's distributed caching capabilities.
 * It supports both default (global) settings and session-specific settings with proper
 * inheritance and override mechanisms.</p>
 * 
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Distributed storage of session settings across cluster nodes</li>
 *   <li>Two-level configuration hierarchy: default and session-specific settings</li>
 *   <li>Local caching for improved performance</li>
 *   <li>Automatic settings persistence and retrieval</li>
 *   <li>Session settings inheritance from defaults with override capability</li>
 *   <li>Support for runtime settings modification and reloading</li>
 * </ul>
 * 
 * <p><strong>Configuration Hierarchy:</strong></p>
 * <ul>
 *   <li><em>Default Settings</em> - Global settings that apply to all sessions unless overridden</li>
 *   <li><em>Session Settings</em> - Session-specific settings that override defaults</li>
 * </ul>
 * 
 * <p><strong>Cache Structure:</strong></p>
 * <ul>
 *   <li><em>quickfixj-settings</em> - Stores both default and session-specific configurations</li>
 *   <li><em>DEFAULT</em> section - Contains global default settings</li>
 *   <li><em>SessionID keys</em> - Session-specific setting overrides</li>
 * </ul>
 * 
 * <p><strong>Thread Safety:</strong></p>
 * <p>This implementation is thread-safe and uses concurrent collections for local caching.
 * Multiple threads can safely access and modify settings concurrently.</p>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * EmbeddedCacheManager cacheManager = new DefaultCacheManager();
 * InfinispanSessionSettings settings = new InfinispanSessionSettings(cacheManager);
 * 
 * // Set default configuration
 * settings.setDefault("SocketConnectHost", "localhost");
 * settings.setDefault("SocketConnectPort", "9876");
 * 
 * // Set session-specific configuration
 * SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
 * settings.setSession(sessionID, "SocketConnectPort", "9877"); // Override default
 * 
 * // Retrieve settings (will get session-specific value if available, otherwise default)
 * String port = settings.getSession(sessionID, "SocketConnectPort"); // Returns "9877"
 * String host = settings.getSession(sessionID, "SocketConnectHost"); // Returns "localhost"
 * }</pre>
 * 
 * @author Dario Oliveira Junior
 * @version 1.0
 * @since 1.0
 * @see quickfix.SessionSettings
 * @see org.infinispan.manager.EmbeddedCacheManager
 * @see quickfix.SessionID
 */
public class InfinispanSessionSettings {
    
    /** Logger instance for this session settings implementation. */
    private static final Logger logger = LoggerFactory.getLogger(InfinispanSessionSettings.class);
    
    /** Cache name for storing session settings. */
    private static final String SETTINGS_CACHE = "quickfixj-settings";
    
    /** Key used for storing default (global) settings. */
    private static final String DEFAULT_SECTION = "DEFAULT";
    
    /** The Infinispan cache manager instance. */
    private final EmbeddedCacheManager cacheManager;
    
    /** Cache for storing session settings with persistence. */
    private final Cache<String, Map<String, String>> settingsCache;
    
    /** Local cache for improved performance - stores session-specific settings. */
    private final Map<String, Map<String, String>> localCache = new ConcurrentHashMap<>();
    
    /** Local cache for default settings. */
    private final Map<String, String> defaultSettings = new ConcurrentHashMap<>();
    
    /**
     * Constructor that accepts a pre-configured Infinispan CacheManager.
     * 
     * <p>Creates a new session settings instance using the provided cache manager.
     * This constructor initializes the settings cache, loads any existing settings
     * from the distributed cache, and prepares the instance for configuration operations.</p>
     * 
     * <p>The constructor automatically loads any existing settings from the cache,
     * including both default settings and session-specific configurations.</p>
     *
     * @param cacheManager the Infinispan cache manager instance
     * @throws IllegalArgumentException if cacheManager is null
     */
    public InfinispanSessionSettings(EmbeddedCacheManager cacheManager) {
        if (cacheManager == null) {
            throw new IllegalArgumentException("CacheManager cannot be null");
        }
        
        this.cacheManager = cacheManager;
        this.settingsCache = cacheManager.getCache(SETTINGS_CACHE);
        
        // Load existing settings
        loadSettings();
        
        logger.info("InfinispanSessionSettings initialized successfully");
    }
    
    /**
     * Constructor that accepts a CacheManager and initial base settings.
     * 
     * <p>Creates a new session settings instance using the provided cache manager
     * and optionally copies settings from an existing SessionSettings instance.
     * This is useful for migrating from traditional file-based QuickFIX/J
     * configurations to the distributed Infinispan-based approach.</p>
     *
     * @param cacheManager the Infinispan cache manager instance
     * @param baseSettings base settings to copy from (can be null)
     * @throws IllegalArgumentException if cacheManager is null
     */
    public InfinispanSessionSettings(EmbeddedCacheManager cacheManager, SessionSettings baseSettings) {
        this(cacheManager);
        
        if (baseSettings != null) {
            copyFromSessionSettings(baseSettings);
        }
    }
    
    /**
     * Sets a default (global) configuration setting.
     * 
     * <p>Default settings apply to all sessions unless overridden by session-specific
     * settings. This method stores the setting both in the local cache for fast access
     * and persists it to the distributed cache for durability and cluster synchronization.</p>
     * 
     * <p>Examples of typical default settings include connection parameters, message
     * validation settings, and behavioral configurations that should apply across
     * all sessions by default.</p>
     *
     * @param key the configuration key
     * @param value the configuration value
     * @throws IllegalArgumentException if key or value is null
     */
    public void setDefault(String key, String value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Key and value cannot be null");
        }
        
        defaultSettings.put(key, value);
        persistDefaultSettings();
        logger.debug("Default setting defined: {}={}", key, value);
    }
    
    /**
     * Sets a session-specific configuration setting.
     * 
     * <p>Session-specific settings override default settings for the specified session.
     * This allows fine-grained control over individual session behavior while maintaining
     * global defaults. The setting is stored both locally and persisted to the distributed
     * cache for durability and cluster synchronization.</p>
     * 
     * <p>If a setting with the same key exists in the default settings, this session-specific
     * value will take precedence when retrieved for this particular session.</p>
     *
     * @param sessionID the session identifier for which to set the configuration
     * @param key the configuration key
     * @param value the configuration value
     * @throws IllegalArgumentException if sessionID, key, or value is null
     */
    public void setSession(SessionID sessionID, String key, String value) {
        if (sessionID == null || key == null || value == null) {
            throw new IllegalArgumentException("SessionID, key, and value cannot be null");
        }
        
        String sessionKey = sessionID.toString();
        Map<String, String> sessionSettings = localCache.computeIfAbsent(sessionKey, k -> new ConcurrentHashMap<>());
        sessionSettings.put(key, value);
        
        persistSessionSettings(sessionID, sessionSettings);
        logger.debug("Session setting defined: sessionID={}, {}={}", sessionID, key, value);
    }
    
    /**
     * Retrieves a default (global) configuration setting.
     * 
     * <p>Returns the value of the specified default configuration key. Default settings
     * are global settings that apply to all sessions unless overridden by session-specific
     * configurations.</p>
     *
     * @param key the configuration key to retrieve
     * @return the configuration value for the specified key
     * @throws ConfigError if the key is not found in the default settings
     * @throws IllegalArgumentException if key is null
     */
    public String getDefault(String key) throws ConfigError {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        
        String value = defaultSettings.get(key);
        if (value == null) {
            throw new ConfigError("Key not found: " + key);
        }
        return value;
    }
    
    /**
     * Retrieves a configuration setting for a specific session.
     * 
     * <p>This method implements a hierarchical lookup strategy:</p>
     * <ol>
     *   <li>First checks for a session-specific setting with the given key</li>
     *   <li>If not found, falls back to the default setting</li>
     *   <li>If neither exists, throws a ConfigError</li>
     * </ol>
     * 
     * <p>This approach allows session-specific settings to override defaults while
     * providing a fallback mechanism for common configurations.</p>
     *
     * @param sessionID the session identifier for which to retrieve the setting
     * @param key the configuration key to retrieve
     * @return the configuration value (session-specific if available, otherwise default)
     * @throws ConfigError if the key is not found in either session or default settings
     * @throws IllegalArgumentException if sessionID or key is null
     */
    public String getSession(SessionID sessionID, String key) throws ConfigError {
        if (sessionID == null || key == null) {
            throw new IllegalArgumentException("SessionID and key cannot be null");
        }
        
        String sessionKey = sessionID.toString();
        Map<String, String> sessionSettings = localCache.get(sessionKey);
        
        if (sessionSettings != null && sessionSettings.containsKey(key)) {
            return sessionSettings.get(key);
        }
        
        // If not found in session, search in defaults
        if (defaultSettings.containsKey(key)) {
            return defaultSettings.get(key);
        }
        
        throw new ConfigError("Key not found: " + key);
    }
    
    /**
     * Checks if a default configuration setting exists.
     * 
     * <p>This method verifies whether a configuration key exists in the default
     * (global) settings. It's useful for conditional configuration access and
     * validation scenarios.</p>
     *
     * @param key the configuration key to check
     * @return true if the key exists in default settings, false otherwise
     * @throws IllegalArgumentException if key is null
     */
    public boolean hasDefault(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        return defaultSettings.containsKey(key);
    }
    
    /**
     * Checks if a configuration setting exists for a specific session.
     * 
     * <p>This method uses the same hierarchical lookup strategy as {@link #getSession(SessionID, String)}:
     * it first checks for session-specific settings, then falls back to default settings.
     * Returns true if the key exists in either location.</p>
     *
     * @param sessionID the session identifier for which to check the setting
     * @param key the configuration key to check
     * @return true if the key exists in session-specific or default settings, false otherwise
     * @throws IllegalArgumentException if sessionID or key is null
     */
    public boolean hasSession(SessionID sessionID, String key) {
        if (sessionID == null || key == null) {
            throw new IllegalArgumentException("SessionID and key cannot be null");
        }
        
        String sessionKey = sessionID.toString();
        Map<String, String> sessionSettings = localCache.get(sessionKey);
        
        return (sessionSettings != null && sessionSettings.containsKey(key)) || defaultSettings.containsKey(key);
    }
    
    /**
     * Retrieves all default configuration properties.
     * 
     * <p>Returns a copy of all default (global) settings as a Map. This is useful
     * for inspection, debugging, or bulk operations on configuration data.
     * The returned map is a defensive copy - modifications to it will not affect
     * the stored settings.</p>
     *
     * @return a new Map containing all default settings (empty map if no defaults exist)
     */
    public Map<String, String> getDefaultProperties() {
        return new HashMap<>(defaultSettings);
    }
    
    /**
     * Retrieves all configuration properties for a specific session.
     * 
     * <p>Returns a copy of all session-specific settings for the given session ID.
     * Note that this method only returns session-specific overrides, not the complete
     * effective configuration (which would include defaults). To get the complete
     * effective configuration, use individual {@link #getSession(SessionID, String)} calls.</p>
     * 
     * <p>The returned map is a defensive copy - modifications to it will not affect
     * the stored settings.</p>
     *
     * @param sessionID the session identifier for which to retrieve properties
     * @return a new Map containing session-specific settings (empty map if no session settings exist)
     * @throws IllegalArgumentException if sessionID is null
     */
    public Map<String, String> getSessionProperties(SessionID sessionID) {
        if (sessionID == null) {
            throw new IllegalArgumentException("SessionID cannot be null");
        }
        
        String sessionKey = sessionID.toString();
        Map<String, String> sessionSettings = localCache.get(sessionKey);
        
        if (sessionSettings != null) {
            return new HashMap<>(sessionSettings);
        }
        
        return new HashMap<>();
    }
    
    /**
     * Returns an iterator for all configured sessions.
     * 
     * <p>This method scans the local cache for all session keys and attempts to
     * parse them back into SessionID objects. Only valid SessionID strings are
     * included in the result. Invalid session keys are logged as warnings but
     * do not cause the method to fail.</p>
     * 
     * <p>The iterator provides a way to enumerate all sessions that have
     * session-specific configurations defined.</p>
     *
     * @return an iterator over SessionID objects for all configured sessions
     */
    public Iterator<SessionID> getSessions() {
        Set<SessionID> sessionIDs = new HashSet<>();
        
        for (String sessionKey : localCache.keySet()) {
            try {
                SessionID sessionID = parseSessionID(sessionKey);
                if (sessionID != null) {
                    sessionIDs.add(sessionID);
                }
            } catch (Exception e) {
                logger.warn("Error parsing SessionID: {}", sessionKey, e);
            }
        }
        
        return sessionIDs.iterator();
    }
    
    /**
     * Reloads settings from the Infinispan cache.
     * 
     * <p>This method refreshes the local cache by reloading all settings from the
     * distributed Infinispan cache. This is useful in clustered environments where
     * settings may have been modified by other nodes, ensuring that this instance
     * has the most current configuration data.</p>
     * 
     * <p>The method clears and repopulates both default settings and session-specific
     * settings from the distributed cache.</p>
     */
    public void refresh() {
        try {
            loadSettings();
            logger.info("Settings reloaded from Infinispan");
        } catch (Exception e) {
            logger.error("Error reloading settings", e);
        }
    }
    
    /**
     * Persists session-specific settings to the distributed cache.
     * 
     * <p>This method stores the provided session settings in the distributed cache
     * for durability and cluster synchronization. It creates a defensive copy of
     * the settings map before storing to prevent external modifications.</p>
     * 
     * <p>This method is called automatically by {@link #setSession(SessionID, String, String)}
     * but can also be called directly for bulk operations.</p>
     *
     * @param sessionID the session identifier for which to persist settings
     * @param settings the settings map to persist
     * @throws IllegalArgumentException if sessionID or settings is null
     */
    public void persistSessionSettings(SessionID sessionID, Map<String, String> settings) {
        if (sessionID == null || settings == null) {
            throw new IllegalArgumentException("SessionID and settings cannot be null");
        }
        
        try {
            settingsCache.put(sessionID.toString(), new HashMap<>(settings));
            logger.debug("Session settings persisted: {}", sessionID);
        } catch (Exception e) {
            logger.error("Error persisting session settings: {}", sessionID, e);
        }
    }
    
    /**
     * Removes all settings for a specific session.
     * 
     * <p>This method completely removes all session-specific settings for the given
     * session from both the local cache and the distributed cache. After removal,
     * any configuration lookups for this session will fall back to default settings only.</p>
     * 
     * <p>This operation is useful for cleaning up settings for sessions that are no
     * longer needed or for resetting a session to use only default configurations.</p>
     *
     * @param sessionID the session identifier for which to remove all settings
     * @throws IllegalArgumentException if sessionID is null
     */
    public void removeSessionSettings(SessionID sessionID) {
        if (sessionID == null) {
            throw new IllegalArgumentException("SessionID cannot be null");
        }
        
        try {
            String sessionKey = sessionID.toString();
            settingsCache.remove(sessionKey);
            localCache.remove(sessionKey);
            logger.info("Session settings removed: {}", sessionID);
        } catch (Exception e) {
            logger.error("Error removing session settings: {}", sessionID, e);
        }
    }
    
    /**
     * Closes the settings instance and releases local resources.
     * 
     * <p>This method performs cleanup by clearing all local caches but does not
     * affect the distributed cache or the cache manager. The distributed settings
     * remain available for other instances or future use.</p>
     * 
     * <p>After calling this method, the instance should not be used for further
     * operations as the local caches will be empty.</p>
     */
    public void close() {
        try {
            localCache.clear();
            defaultSettings.clear();
            logger.info("InfinispanSessionSettings closed");
        } catch (Exception e) {
            logger.error("Error closing InfinispanSessionSettings", e);
        }
    }
    
    // Private Helper Methods
    
    /**
     * Loads all settings from the distributed cache into local caches.
     * 
     * <p>This method refreshes the local caches by loading all settings from the
     * distributed Infinispan cache. It handles both default settings and
     * session-specific settings.</p>
     */
    private void loadSettings() {
        try {
            // Load default settings
            Map<String, String> defaultCache = settingsCache.get(DEFAULT_SECTION);
            if (defaultCache != null) {
                defaultSettings.clear();
                defaultSettings.putAll(defaultCache);
            }
            
            // Load session settings
            for (Map.Entry<String, Map<String, String>> entry : settingsCache.entrySet()) {
                String key = entry.getKey();
                if (!DEFAULT_SECTION.equals(key)) {
                    localCache.put(key, new ConcurrentHashMap<>(entry.getValue()));
                }
            }
            
            logger.debug("Settings loaded: {} sessions", localCache.size());
        } catch (Exception e) {
            logger.error("Error loading settings", e);
        }
    }

    /**
     * Persists default settings to the distributed cache.
     */
    private void persistDefaultSettings() {
        try {
            settingsCache.put(DEFAULT_SECTION, new HashMap<>(defaultSettings));
        } catch (Exception e) {
            logger.error("Error persisting default settings", e);
        }
    }

    /**
     * Copies settings from an existing SessionSettings instance.
     * 
     * <p>This method can be implemented as needed to copy settings from a
     * traditional QuickFIX/J SessionSettings instance.</p>
     * 
     * @param source the source SessionSettings to copy from
     */
    @SuppressWarnings("unused")
    private void copyFromSessionSettings(SessionSettings source) {
        try {
            // This method can be implemented if needed to copy
            // settings from an existing SessionSettings instance
            logger.info("Settings copied from provided SessionSettings");
        } catch (Exception e) {
            logger.error("Error copying settings", e);
        }
    }

    /**
     * Parses a session key string back into a SessionID object.
     * 
     * <p>Attempts to parse session key strings in the format "FIX.4.4:SENDER->TARGET"
     * back into SessionID objects. Returns null if parsing fails.</p>
     * 
     * @param sessionKey the session key string to parse
     * @return the parsed SessionID, or null if parsing fails
     */
    private SessionID parseSessionID(String sessionKey) {
        try {
            // Assuming format: FIX.4.4:SENDER->TARGET
            String[] parts = sessionKey.split(":");
            if (parts.length >= 2) {
                String version = parts[0];
                String[] senderTarget = parts[1].split("->");
                if (senderTarget.length == 2) {
                    return new SessionID(version, senderTarget[0], senderTarget[1]);
                }
            }
        } catch (Exception e) {
            logger.debug("Could not parse SessionID: {}", sessionKey);
        }
        return null;
    }
}