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
 * Exemplo completo de uso do plugin Infinispan-QuickFIX/J.
 * Demonstra como configurar e usar o Infinispan como backend de persistência para QuickFIX/J.
 */
public class InfinispanQuickFixJExample {
    
    private static final Logger logger = LoggerFactory.getLogger(InfinispanQuickFixJExample.class);
    
    public static void main(String[] args) {
        logger.info("Iniciando exemplo Infinispan-QuickFIX/J");
        
        try {
            // Exemplo 1: Configuração básica
            basicExample();
            
            // Exemplo 2: Configuração avançada
            advancedExample();
            
            // Exemplo 3: Configuração distribuída
            distributedExample();
            
        } catch (Exception e) {
            logger.error("Erro no exemplo", e);
        }
        
        logger.info("Exemplo concluído");
    }
    
    /**
     * Exemplo básico com configuração local.
     */
    private static void basicExample() throws Exception {
        logger.info("=== Exemplo Básico ===");
        
        // Criar configuração básica
        InfinispanQuickFixJConfig config = new InfinispanQuickFixJConfig()
            .clusterName("basic-example")
            .cacheMode(CacheMode.LOCAL)
            .expiration(60) // 1 hora
            .maxEntries(1000);
        
        // Criar factory
        InfinispanMessageStoreFactory factory = config.createMessageStoreFactory();
        
        // Criar SessionSettings usando Properties
        Properties properties = InfinispanQuickFixJConfig.createExampleQuickFixJProperties();
        properties.setProperty("default.MessageStoreFactory", factory.getClass().getName());
        
        SessionSettings settings = new SessionSettings(new ByteArrayInputStream(propertiesToString(properties).getBytes()));
        
        // Configurar factory
        factory.configure(settings);
        
        // Criar store para uma sessão específica
        SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        MessageStore store = factory.create(sessionID);
        
        // Demonstrar uso básico
        demonstrateStoreUsage(store, "Exemplo Básico");
        
        // Limpeza
        factory.close();
        
        logger.info("Exemplo básico concluído");
    }
    
    /**
     * Exemplo avançado com persistência em disco.
     */
    private static void advancedExample() throws Exception {
        logger.info("=== Exemplo Avançado ===");
        
        // Criar configuração avançada
        InfinispanQuickFixJConfig config = new InfinispanQuickFixJConfig()
            .clusterName("advanced-example")
            .cacheMode(CacheMode.LOCAL)
            .expiration(1440) // 24 horas
            .maxEntries(10000)
            .enableStatistics(true)
            .enablePersistence("./infinispan-data");
        
        // Criar factory
        InfinispanMessageStoreFactory factory = config.createMessageStoreFactory();
        
        // Configuração com SessionSettings
        Properties properties = InfinispanQuickFixJConfig.createExampleQuickFixJProperties();
        SessionSettings settings = new SessionSettings(new ByteArrayInputStream(propertiesToString(properties).getBytes()));
        
        factory.configure(settings);
        
        // Demonstrar múltiplas sessões
        SessionID session1 = new SessionID("FIX.4.4", "CLIENT1", "SERVER");
        SessionID session2 = new SessionID("FIX.4.4", "CLIENT2", "SERVER");
        
        MessageStore store1 = factory.create(session1);
        MessageStore store2 = factory.create(session2);
        
        demonstrateStoreUsage(store1, "Sessão 1");
        demonstrateStoreUsage(store2, "Sessão 2");
        
        // Mostrar estatísticas
        Properties stats = factory.getCacheStatistics();
        logger.info("Estatísticas do cache:");
        stats.forEach((key, value) -> logger.info("  {}: {}", key, value));
        
        factory.close();
        
        logger.info("Exemplo avançado concluído");
    }
    
    /**
     * Exemplo de configuração distribuída.
     */
    private static void distributedExample() throws Exception {
        logger.info("=== Exemplo Distribuído ===");
        
        // Criar configuração distribuída
        InfinispanQuickFixJConfig config = new InfinispanQuickFixJConfig()
            .clusterName("distributed-cluster")
            .cacheMode(CacheMode.DIST_SYNC)
            .expiration(2880) // 48 horas
            .maxEntries(50000)
            .enableStatistics(true);
        
        // Criar factory
        InfinispanMessageStoreFactory factory = config.createMessageStoreFactory();
        
        // Configuração personalizada
        Properties properties = new Properties();
        properties.setProperty("default.ConnectionType", "acceptor");
        properties.setProperty("default.SocketAcceptPort", "9876");
        properties.setProperty("default.StartTime", "00:00:00");
        properties.setProperty("default.EndTime", "00:00:00");
        properties.setProperty("default.HeartBtInt", "30");
        properties.setProperty("default.MessageStoreFactory", factory.getClass().getName());
        
        // Configurações específicas do Infinispan
        properties.setProperty("default.InfinispanClusterName", "distributed-cluster");
        properties.setProperty("default.InfinispanCacheMode", "DIST_SYNC");
        properties.setProperty("default.InfinispanExpirationMinutes", "2880");
        properties.setProperty("default.InfinispanMaxEntries", "50000");
        
        SessionSettings settings = new SessionSettings(new ByteArrayInputStream(propertiesToString(properties).getBytes()));
        factory.configure(settings);
        
        // Demonstrar uso
        SessionID sessionID = new SessionID("FIX.4.4", "DISTRIBUTOR", "EXCHANGE");
        MessageStore store = factory.create(sessionID);
        
        demonstrateStoreUsage(store, "Exemplo Distribuído");
        
        factory.close();
        
        logger.info("Exemplo distribuído concluído");
    }
    
    /**
     * Demonstra o uso básico de um MessageStore.
     */
    private static void demonstrateStoreUsage(MessageStore store, String label) throws Exception {
        logger.info("--- Demonstrando {} ---", label);
        
        // Verificar sequências iniciais
        logger.info("Sequence numbers iniciais - Sender: {}, Target: {}", 
                   store.getNextSenderMsgSeqNum(), store.getNextTargetMsgSeqNum());
        
        // Armazenar algumas mensagens
        String message1 = "8=FIX.4.4|9=73|35=A|49=SENDER|56=TARGET|34=1|52=20231219-10:30:00|98=0|108=30|10=142|";
        String message2 = "8=FIX.4.4|9=55|35=0|49=SENDER|56=TARGET|34=2|52=20231219-10:30:01|10=143|";
        String message3 = "8=FIX.4.4|9=68|35=1|49=SENDER|56=TARGET|34=3|52=20231219-10:30:02|112=TESTID|10=144|";
        
        store.set(1, message1);
        store.set(2, message2);
        store.set(3, message3);
        
        logger.info("Armazenadas 3 mensagens");
        
        // Recuperar mensagens
        java.util.Collection<String> messages = new java.util.ArrayList<>();
        store.get(1, 3, messages);
        
        logger.info("Recuperadas {} mensagens:", messages.size());
        int count = 1;
        for (String msg : messages) {
            logger.info("  Mensagem {}: {}", count++, msg.substring(0, Math.min(50, msg.length())) + "...");
        }
        
        // Atualizar sequence numbers
        store.setNextSenderMsgSeqNum(10);
        store.setNextTargetMsgSeqNum(5);
        
        logger.info("Sequence numbers atualizados - Sender: {}, Target: {}", 
                   store.getNextSenderMsgSeqNum(), store.getNextTargetMsgSeqNum());
        
        // Incrementar sequence numbers
        store.incrNextSenderMsgSeqNum();
        store.incrNextTargetMsgSeqNum();
        
        logger.info("Sequence numbers incrementados - Sender: {}, Target: {}", 
                   store.getNextSenderMsgSeqNum(), store.getNextTargetMsgSeqNum());
        
        // Mostrar tempo de criação
        logger.info("Tempo de criação da sessão: {}", store.getCreationTime());
        
        logger.info("--- Fim da demonstração {} ---", label);
    }
    
    /**
     * Converte Properties para String no formato esperado pelo QuickFIX/J.
     */
    private static String propertiesToString(Properties properties) {
        StringBuilder sb = new StringBuilder();
        
        // Adicionar seção [DEFAULT]
        sb.append("[DEFAULT]\n");
        properties.forEach((key, value) -> {
            String keyStr = key.toString();
            if (keyStr.startsWith("default.")) {
                sb.append(keyStr.substring(8)).append("=").append(value).append("\n");
            }
        });
        
        // Adicionar seções de sessão
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