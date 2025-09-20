package io.github.darioajr.quickfixj.infinispan.factory;

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

import io.github.darioajr.quickfixj.infinispan.store.InfinispanMessageStore;
import io.github.darioajr.quickfixj.infinispan.store.InfinispanSessionSettings;
import quickfix.MessageStore;
import quickfix.MessageStoreFactory;
import quickfix.SessionID;
import quickfix.SessionSettings;

/**
 * Factory para criar MessageStores baseados em Infinispan.
 * Esta factory configura e gerencia o cache do Infinispan para persistência das mensagens QuickFIX/J.
 */
public class InfinispanMessageStoreFactory implements MessageStoreFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(InfinispanMessageStoreFactory.class);
    
    // Configurações padrão
    private static final String DEFAULT_CLUSTER_NAME = "quickfixj-cluster";
    private static final String DEFAULT_CACHE_MODE = "LOCAL";
    private static final long DEFAULT_EXPIRATION_MINUTES = 1440; // 24 horas
    private static final int DEFAULT_MAX_ENTRIES = 10000;
    
    // Propriedades configuráveis
    public static final String CLUSTER_NAME_PROPERTY = "InfinispanClusterName";
    public static final String CACHE_MODE_PROPERTY = "InfinispanCacheMode";
    public static final String EXPIRATION_MINUTES_PROPERTY = "InfinispanExpirationMinutes";
    public static final String MAX_ENTRIES_PROPERTY = "InfinispanMaxEntries";
    public static final String CACHE_CONFIG_FILE_PROPERTY = "InfinispanConfigFile";
    
    private EmbeddedCacheManager cacheManager;
    private final ConcurrentHashMap<SessionID, InfinispanMessageStore> stores = new ConcurrentHashMap<>();
    private SessionSettings sessionSettings;
    
    /**
     * Construtor padrão.
     */
    public InfinispanMessageStoreFactory() {
        // Construtor vazio - inicialização será feita no método configure
    }
    
    /**
     * Construtor que recebe um CacheManager já configurado.
     *
     * @param cacheManager o gerenciador de cache do Infinispan pré-configurado
     */
    public InfinispanMessageStoreFactory(EmbeddedCacheManager cacheManager) {
        this.cacheManager = cacheManager;
        logger.info("InfinispanMessageStoreFactory inicializada com CacheManager existente");
    }
    
    /**
     * Configura a factory com as configurações da sessão.
     * Este método é chamado automaticamente pelo QuickFIX/J.
     */
    public void configure(SessionSettings settings) {
        this.sessionSettings = settings;
        
        if (cacheManager == null) {
            try {
                cacheManager = createCacheManager(settings);
                configureCaches();
                logger.info("InfinispanMessageStoreFactory configurada com sucesso");
            } catch (Exception e) {
                logger.error("Erro ao configurar InfinispanMessageStoreFactory", e);
                throw new RuntimeException("Falha na configuração do Infinispan", e);
            }
        }
    }
    
    @Override
    public MessageStore create(SessionID sessionID) {
        try {
            InfinispanMessageStore store = stores.computeIfAbsent(sessionID, 
                sid -> new InfinispanMessageStore(cacheManager, sid));
            
            logger.debug("MessageStore criado para sessão: {}", sessionID);
            return store;
        } catch (Exception e) {
            logger.error("Erro ao criar MessageStore para sessão: {}", sessionID, e);
            throw new RuntimeException("Falha ao criar MessageStore", e);
        }
    }
    
    /**
     * Cria um SessionSettings baseado em Infinispan.
     *
     * @return InfinispanSessionSettings configurado
     */
    public InfinispanSessionSettings createSessionSettings() {
        if (cacheManager == null) {
            throw new IllegalStateException("Factory não foi configurada. Chame configure() primeiro.");
        }
        
        return new InfinispanSessionSettings(cacheManager, sessionSettings);
    }
    
    /**
     * Fecha a factory e libera todos os recursos.
     */
    public void close() {
        try {
            // Fechar todos os stores
            stores.values().forEach(InfinispanMessageStore::close);
            stores.clear();
            
            // Fechar o cache manager
            if (cacheManager != null && cacheManager.getStatus().allowInvocations()) {
                cacheManager.stop();
            }
            
            logger.info("InfinispanMessageStoreFactory fechada");
        } catch (Exception e) {
            logger.error("Erro ao fechar InfinispanMessageStoreFactory", e);
        }
    }
    
    /**
     * Retorna o CacheManager sendo usado.
     *
     * @return o EmbeddedCacheManager
     */
    public EmbeddedCacheManager getCacheManager() {
        return cacheManager;
    }
    
    /**
     * Retorna estatísticas dos caches.
     *
     * @return mapa com estatísticas de cada cache
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
                
                stats.setProperty("cluster.members", String.valueOf(cacheManager.getMembers().size()));
                stats.setProperty("cache.status", cacheManager.getStatus().toString());
                
            } catch (Exception e) {
                logger.warn("Erro ao obter estatísticas dos caches", e);
            }
        }
        
        return stats;
    }
    
    // Métodos privados
    
    private EmbeddedCacheManager createCacheManager(SessionSettings settings) {
        try {
            // Verificar se há arquivo de configuração customizado
            String configFile = getConfigProperty(settings, CACHE_CONFIG_FILE_PROPERTY, null);
            if (configFile != null) {
                logger.info("Carregando configuração do Infinispan de: {}", configFile);
                try {
                    return new DefaultCacheManager(configFile);
                } catch (java.io.IOException e) {
                    logger.error("Erro ao carregar arquivo de configuração: {}", configFile, e);
                    throw new RuntimeException("Falha ao carregar configuração", e);
                }
            }
            
            // Criar configuração programaticamente
            String clusterName = getConfigProperty(settings, CLUSTER_NAME_PROPERTY, DEFAULT_CLUSTER_NAME);
            
            GlobalConfiguration globalConfig = new GlobalConfigurationBuilder()
                .clusteredDefault()
                .transport().clusterName(clusterName)
                .build();
            
            return new DefaultCacheManager(globalConfig);
            
        } catch (RuntimeException e) {
            logger.error("Erro ao criar CacheManager", e);
            throw e;
        }
    }
    
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
        
        // Definir configurações para todos os caches
        cacheManager.defineConfiguration("quickfixj-messages", cacheConfig);
        cacheManager.defineConfiguration("quickfixj-sequences", cacheConfig);
        cacheManager.defineConfiguration("quickfixj-sessions", cacheConfig);
        cacheManager.defineConfiguration("quickfixj-settings", cacheConfig);
        
        // Inicializar os caches
        cacheManager.getCache("quickfixj-messages");
        cacheManager.getCache("quickfixj-sequences");
        cacheManager.getCache("quickfixj-sessions");
        cacheManager.getCache("quickfixj-settings");
        
        logger.info("Caches configurados - Mode: {}, Expiration: {} min, MaxEntries: {}", 
                   cacheMode, expirationMinutes, maxEntries);
    }
    
    private String getConfigProperty(SessionSettings settings, String property, String defaultValue) {
        try {
            if (settings != null) {
                return settings.getString(property);
            }
        } catch (quickfix.ConfigError e) {
            logger.debug("Propriedade não encontrada: {}", property);
        }
        return defaultValue;
    }
}