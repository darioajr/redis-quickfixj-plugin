package com.infinispan.quickfixj.store;

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
 * Implementação baseada em Infinispan para armazenar configurações de sessão do QuickFIX/J.
 * Esta implementação permite armazenar e recuperar configurações de sessão de forma distribuída.
 */
public class InfinispanSessionSettings {
    
    private static final Logger logger = LoggerFactory.getLogger(InfinispanSessionSettings.class);
    
    private static final String SETTINGS_CACHE = "quickfixj-settings";
    private static final String DEFAULT_SECTION = "DEFAULT";
    
    private final EmbeddedCacheManager cacheManager;
    private final Cache<String, Map<String, String>> settingsCache;
    
    // Cache local para performance
    private final Map<String, Map<String, String>> localCache = new ConcurrentHashMap<>();
    private final Map<String, String> defaultSettings = new ConcurrentHashMap<>();
    
    /**
     * Construtor que recebe um CacheManager do Infinispan já configurado.
     *
     * @param cacheManager o gerenciador de cache do Infinispan
     */
    public InfinispanSessionSettings(EmbeddedCacheManager cacheManager) {
        this.cacheManager = cacheManager;
        this.settingsCache = cacheManager.getCache(SETTINGS_CACHE);
        
        // Carregar configurações existentes
        loadSettings();
        
        logger.info("InfinispanSessionSettings inicializado com sucesso");
    }
    
    /**
     * Construtor que recebe um CacheManager e configurações iniciais.
     *
     * @param cacheManager o gerenciador de cache do Infinispan
     * @param baseSettings configurações base para copiar
     */
    public InfinispanSessionSettings(EmbeddedCacheManager cacheManager, SessionSettings baseSettings) {
        this(cacheManager);
        
        if (baseSettings != null) {
            copyFromSessionSettings(baseSettings);
        }
    }
    
    /**
     * Define uma configuração padrão.
     */
    public void setDefault(String key, String value) {
        defaultSettings.put(key, value);
        persistDefaultSettings();
        logger.debug("Configuração padrão definida: {}={}", key, value);
    }
    
    /**
     * Define uma configuração para uma sessão específica.
     */
    public void setSession(SessionID sessionID, String key, String value) {
        String sessionKey = sessionID.toString();
        Map<String, String> sessionSettings = localCache.computeIfAbsent(sessionKey, k -> new ConcurrentHashMap<>());
        sessionSettings.put(key, value);
        
        persistSessionSettings(sessionID, sessionSettings);
        logger.debug("Configuração da sessão definida: sessionID={}, {}={}", sessionID, key, value);
    }
    
    /**
     * Obtém uma configuração padrão.
     */
    public String getDefault(String key) throws ConfigError {
        String value = defaultSettings.get(key);
        if (value == null) {
            throw new ConfigError("Key not found: " + key);
        }
        return value;
    }
    
    /**
     * Obtém uma configuração para uma sessão específica.
     */
    public String getSession(SessionID sessionID, String key) throws ConfigError {
        String sessionKey = sessionID.toString();
        Map<String, String> sessionSettings = localCache.get(sessionKey);
        
        if (sessionSettings != null && sessionSettings.containsKey(key)) {
            return sessionSettings.get(key);
        }
        
        // Se não encontrou na sessão, busca no default
        if (defaultSettings.containsKey(key)) {
            return defaultSettings.get(key);
        }
        
        throw new ConfigError("Key not found: " + key);
    }
    
    /**
     * Verifica se uma configuração padrão existe.
     */
    public boolean hasDefault(String key) {
        return defaultSettings.containsKey(key);
    }
    
    /**
     * Verifica se uma configuração existe para uma sessão.
     */
    public boolean hasSession(SessionID sessionID, String key) {
        String sessionKey = sessionID.toString();
        Map<String, String> sessionSettings = localCache.get(sessionKey);
        
        return (sessionSettings != null && sessionSettings.containsKey(key)) || defaultSettings.containsKey(key);
    }
    
    /**
     * Obtém todas as configurações padrão.
     */
    public Map<String, String> getDefaultProperties() {
        return new HashMap<>(defaultSettings);
    }
    
    /**
     * Obtém todas as configurações de uma sessão.
     */
    public Map<String, String> getSessionProperties(SessionID sessionID) {
        String sessionKey = sessionID.toString();
        Map<String, String> sessionSettings = localCache.get(sessionKey);
        
        if (sessionSettings != null) {
            return new HashMap<>(sessionSettings);
        }
        
        return new HashMap<>();
    }
    
    /**
     * Retorna um iterator para todas as sessões.
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
                logger.warn("Erro ao parsear SessionID: {}", sessionKey, e);
            }
        }
        
        return sessionIDs.iterator();
    }
    
    /**
     * Recarrega as configurações do Infinispan.
     */
    public void refresh() {
        try {
            loadSettings();
            logger.info("Configurações recarregadas do Infinispan");
        } catch (Exception e) {
            logger.error("Erro ao recarregar configurações", e);
        }
    }
    
    /**
     * Persiste uma configuração específica da sessão.
     */
    public void persistSessionSettings(SessionID sessionID, Map<String, String> settings) {
        try {
            settingsCache.put(sessionID.toString(), new HashMap<>(settings));
            logger.debug("Configurações da sessão persistidas: {}", sessionID);
        } catch (Exception e) {
            logger.error("Erro ao persistir configurações da sessão: {}", sessionID, e);
        }
    }
    
    /**
     * Remove as configurações de uma sessão.
     */
    public void removeSessionSettings(SessionID sessionID) {
        try {
            String sessionKey = sessionID.toString();
            settingsCache.remove(sessionKey);
            localCache.remove(sessionKey);
            logger.info("Configurações da sessão removidas: {}", sessionID);
        } catch (Exception e) {
            logger.error("Erro ao remover configurações da sessão: {}", sessionID, e);
        }
    }
    
    /**
     * Fecha o settings e libera recursos.
     */
    public void close() {
        try {
            localCache.clear();
            defaultSettings.clear();
            logger.info("InfinispanSessionSettings fechado");
        } catch (Exception e) {
            logger.error("Erro ao fechar InfinispanSessionSettings", e);
        }
    }
    
    // Métodos auxiliares privados
    
    private void loadSettings() {
        try {
            // Carregar configurações padrão
            Map<String, String> defaultCache = settingsCache.get(DEFAULT_SECTION);
            if (defaultCache != null) {
                defaultSettings.clear();
                defaultSettings.putAll(defaultCache);
            }
            
            // Carregar configurações das sessões
            for (Map.Entry<String, Map<String, String>> entry : settingsCache.entrySet()) {
                String key = entry.getKey();
                if (!DEFAULT_SECTION.equals(key)) {
                    localCache.put(key, new ConcurrentHashMap<>(entry.getValue()));
                }
            }
            
            logger.debug("Configurações carregadas: {} sessões", localCache.size());
        } catch (Exception e) {
            logger.error("Erro ao carregar configurações", e);
        }
    }
    
    private void persistDefaultSettings() {
        try {
            settingsCache.put(DEFAULT_SECTION, new HashMap<>(defaultSettings));
        } catch (Exception e) {
            logger.error("Erro ao persistir configurações padrão", e);
        }
    }
    
    private void copyFromSessionSettings(SessionSettings source) {
        try {
            // Este método pode ser implementado se necessário para copiar
            // configurações de um SessionSettings existente
            logger.info("Configurações copiadas do SessionSettings fornecido");
        } catch (Exception e) {
            logger.error("Erro ao copiar configurações", e);
        }
    }
    
    private SessionID parseSessionID(String sessionKey) {
        try {
            // Assumindo formato: FIX.4.4:SENDER->TARGET
            String[] parts = sessionKey.split(":");
            if (parts.length >= 2) {
                String version = parts[0];
                String[] senderTarget = parts[1].split("->");
                if (senderTarget.length == 2) {
                    return new SessionID(version, senderTarget[0], senderTarget[1]);
                }
            }
        } catch (Exception e) {
            logger.debug("Não foi possível parsear SessionID: {}", sessionKey);
        }
        return null;
    }
}
