package io.github.darioajr.quickfixj.infinispan.config;

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

import io.github.darioajr.quickfixj.infinispan.factory.InfinispanMessageStoreFactory;
import io.github.darioajr.quickfixj.infinispan.store.InfinispanSessionSettings;

import quickfix.SessionSettings;

/**
 * Classe de configuração para facilitar a criação e configuração do plugin Infinispan-QuickFIX/J.
 * Fornece métodos convenientes para configurar o Infinispan com configurações adequadas para QuickFIX/J.
 */
public class InfinispanQuickFixJConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(InfinispanQuickFixJConfig.class);
    
    private String clusterName = "quickfixj-cluster";
    private CacheMode cacheMode = CacheMode.LOCAL;
    private long expirationMinutes = 1440; // 24 horas
    private int maxEntries = 10000;
    private boolean enableStatistics = true;
    private boolean enablePersistence = false;
    private String persistenceLocation = "./infinispan-data";
    
    /**
     * Construtor padrão com configurações básicas.
     */
    public InfinispanQuickFixJConfig() {
        // Configuração padrão
    }
    
    /**
     * Define o nome do cluster.
     */
    public InfinispanQuickFixJConfig clusterName(String clusterName) {
        this.clusterName = clusterName;
        return this;
    }
    
    /**
     * Define o modo de cache (LOCAL, REPLICATED, DISTRIBUTED).
     */
    public InfinispanQuickFixJConfig cacheMode(CacheMode cacheMode) {
        this.cacheMode = cacheMode;
        return this;
    }
    
    /**
     * Define o tempo de expiração das entradas em minutos.
     */
    public InfinispanQuickFixJConfig expiration(long minutes) {
        this.expirationMinutes = minutes;
        return this;
    }
    
    /**
     * Define o número máximo de entradas no cache.
     */
    public InfinispanQuickFixJConfig maxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
        return this;
    }
    
    /**
     * Habilita ou desabilita estatísticas.
     */
    public InfinispanQuickFixJConfig enableStatistics(boolean enable) {
        this.enableStatistics = enable;
        return this;
    }
    
    /**
     * Habilita persistência em disco.
     */
    public InfinispanQuickFixJConfig enablePersistence(String location) {
        this.enablePersistence = true;
        this.persistenceLocation = location;
        return this;
    }
    
    /**
     * Cria um EmbeddedCacheManager configurado.
     */
    public EmbeddedCacheManager createCacheManager() {
        try {
            GlobalConfiguration globalConfig = new GlobalConfigurationBuilder()
                .clusteredDefault()
                .transport().clusterName(clusterName)
                .build();
            
            EmbeddedCacheManager cacheManager = new DefaultCacheManager(globalConfig);
            
            // Configurar caches
            Configuration cacheConfig = createCacheConfiguration();
            cacheManager.defineConfiguration("quickfixj-messages", cacheConfig);
            cacheManager.defineConfiguration("quickfixj-sequences", cacheConfig);
            cacheManager.defineConfiguration("quickfixj-sessions", cacheConfig);
            cacheManager.defineConfiguration("quickfixj-settings", cacheConfig);
            
            logger.info("CacheManager criado com sucesso - Cluster: {}, Mode: {}", clusterName, cacheMode);
            return cacheManager;
            
        } catch (Exception e) {
            logger.error("Erro ao criar CacheManager", e);
            throw new RuntimeException("Falha ao criar CacheManager", e);
        }
    }
    
    /**
     * Cria uma MessageStoreFactory configurada.
     */
    public InfinispanMessageStoreFactory createMessageStoreFactory() {
        EmbeddedCacheManager cacheManager = createCacheManager();
        return new InfinispanMessageStoreFactory(cacheManager);
    }
    
    /**
     * Cria um SessionSettings baseado em Infinispan.
     */
    public InfinispanSessionSettings createSessionSettings(SessionSettings baseSettings) {
        EmbeddedCacheManager cacheManager = createCacheManager();
        return new InfinispanSessionSettings(cacheManager, baseSettings);
    }
    
    /**
     * Cria configurações de exemplo para QuickFIX/J.
     */
    public static Properties createExampleQuickFixJProperties() {
        Properties properties = new Properties();
        
        // Configurações padrão
        properties.setProperty("default.ConnectionType", "initiator");
        properties.setProperty("default.StartTime", "00:00:00");
        properties.setProperty("default.EndTime", "00:00:00");
        properties.setProperty("default.HeartBtInt", "30");
        properties.setProperty("default.SocketConnectPort", "9876");
        properties.setProperty("default.SocketConnectHost", "localhost");
        properties.setProperty("default.ReconnectInterval", "5");
        properties.setProperty("default.FileStorePath", "store");
        
        // Usar o InfinispanMessageStoreFactory
        properties.setProperty("default.MessageStoreFactory", InfinispanMessageStoreFactory.class.getName());
        
        // Configurações específicas do Infinispan
        properties.setProperty("default.InfinispanClusterName", "quickfixj-cluster");
        properties.setProperty("default.InfinispanCacheMode", "LOCAL");
        properties.setProperty("default.InfinispanExpirationMinutes", "1440");
        properties.setProperty("default.InfinispanMaxEntries", "10000");
        
        // Sessão de exemplo
        properties.setProperty("session.FIX.4.4:SENDER->TARGET.BeginString", "FIX.4.4");
        properties.setProperty("session.FIX.4.4:SENDER->TARGET.SenderCompID", "SENDER");
        properties.setProperty("session.FIX.4.4:SENDER->TARGET.TargetCompID", "TARGET");
        
        logger.info("Configurações de exemplo criadas");
        
        return properties;
    }
    
    /**
     * Converte a configuração para Properties.
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
     * Cria uma configuração de cache.
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
                .addSingleFileStore()
                .location(persistenceLocation)
                .async().enable();
        }
        
        return builder.build();
    }
    
    // Getters
    public String getClusterName() { return clusterName; }
    public CacheMode getCacheMode() { return cacheMode; }
    public long getExpirationMinutes() { return expirationMinutes; }
    public int getMaxEntries() { return maxEntries; }
    public boolean isEnableStatistics() { return enableStatistics; }
    public boolean isEnablePersistence() { return enablePersistence; }
    public String getPersistenceLocation() { return persistenceLocation; }
}