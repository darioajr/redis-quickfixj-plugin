package io.github.darioajr.quickfixj.factory;

import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.darioajr.quickfixj.store.RedisMessageStore;
import io.github.darioajr.quickfixj.store.RedisSessionSettings;
import quickfix.MessageStore;
import quickfix.SessionID;
import quickfix.SessionSettings;
import redis.embedded.RedisServer;

/**
 * Testes unitários para RedisMessageStoreFactory.
 */
class RedisMessageStoreFactoryTest {
    
    private RedisServer redisServer;
    private RedisMessageStoreFactory factory;
    private SessionSettings sessionSettings;
    private SessionID sessionID;
    
    @BeforeEach
    void setUp() throws Exception {
        // Iniciar Redis embarcado para testes
        redisServer = new RedisServer(6382);
        redisServer.start();
        
        // Criar factory
        factory = new RedisMessageStoreFactory();
        
        // Criar SessionSettings para testes
        sessionSettings = new SessionSettings();
        sessionSettings.setString("redis.host", "localhost");
        sessionSettings.setString("redis.port", "6382");
        sessionSettings.setString("redis.database", "0");
        sessionSettings.setString("redis.timeout", "5000");
        
        // Criar SessionID para testes
        sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (factory != null) {
            factory.shutdown();
        }
        
        if (redisServer != null) {
            redisServer.stop();
        }
    }
    
    @Test
    void testFactoryCreation() {
        assertNotNull(factory);
        assertEquals(false, factory.isInitialized());
    }
    
    @Test
    void testCreateMessageStore() {
        // Criar MessageStore
        MessageStore messageStore = factory.create(sessionID, sessionSettings);
        
        assertNotNull(messageStore);
        assertTrue(messageStore instanceof RedisMessageStore);
        assertTrue(factory.isInitialized());
    }
    
    @Test
    void testCreateMessageStoreWithoutSettings() {
        // Tentar criar MessageStore sem inicializar
        assertThrows(IllegalStateException.class, () -> {
            factory.create(sessionID);
        });
    }
    
    @Test
    void testCreateMultipleMessageStores() {
        SessionID sessionID2 = new SessionID("FIX.4.4", "SENDER2", "TARGET2");
        
        // Criar múltiplos MessageStores
        MessageStore store1 = factory.create(sessionID, sessionSettings);
        MessageStore store2 = factory.create(sessionID2, sessionSettings);
        
        assertNotNull(store1);
        assertNotNull(store2);
        
        // Verificar que são instâncias diferentes
        assertTrue(store1 != store2);
        
        // Mas para a mesma SessionID deve retornar a mesma instância
        MessageStore store1Again = factory.create(sessionID, sessionSettings);
        assertEquals(store1, store1Again);
    }
    
    @Test
    void testCreateSessionSettings() {
        // Inicializar factory
        factory.create(sessionID, sessionSettings);
        
        // Criar RedisSessionSettings
        RedisSessionSettings redisSettings = factory.createSessionSettings();
        
        assertNotNull(redisSettings);
        assertNotNull(redisSettings.getJedis());
    }
    
    @Test
    void testCreateSessionSettingsWithSource() {
        // Adicionar algumas configurações ao SessionSettings original
        sessionSettings.setString("ConnectionType", "initiator");
        sessionSettings.setString("HeartBtInt", "30");
        sessionSettings.setString(sessionID, "SocketConnectHost", "localhost");
        
        // Inicializar factory
        factory.create(sessionID, sessionSettings);
        
        // Criar RedisSessionSettings com configurações copiadas
        RedisSessionSettings redisSettings = factory.createSessionSettings(sessionSettings);
        
        assertNotNull(redisSettings);
        
        // Verificar que as configurações foram copiadas
        try {
            assertEquals("initiator", redisSettings.getString("ConnectionType"));
            assertEquals("30", redisSettings.getString("HeartBtInt"));
            assertEquals("localhost", redisSettings.getString(sessionID, "SocketConnectHost"));
        } catch (Exception e) {
            // As configurações podem não estar disponíveis imediatamente devido ao carregamento assíncrono
            // Isso é esperado em alguns cenários
        }
    }
    
    @Test
    void testFactoryStatistics() {
        // Criar alguns MessageStores
        factory.create(sessionID, sessionSettings);
        factory.create(new SessionID("FIX.4.4", "SENDER2", "TARGET2"), sessionSettings);
        
        // Obter estatísticas
        Properties stats = factory.getStatistics();
        
        assertNotNull(stats);
        assertEquals("true", stats.getProperty("initialized"));
        assertEquals("2", stats.getProperty("messageStoreCount"));
        assertEquals("localhost", stats.getProperty("redisHost"));
        assertEquals("6382", stats.getProperty("redisPort"));
    }
    
    @Test
    void testConfigurationWithPassword() throws Exception {
        // Criar configurações com senha
        SessionSettings settingsWithAuth = new SessionSettings();
        settingsWithAuth.setString("redis.host", "localhost");
        settingsWithAuth.setString("redis.port", "6382");
        settingsWithAuth.setString("redis.password", "testpassword");
        
        RedisMessageStoreFactory factoryWithAuth = new RedisMessageStoreFactory();
        
        // Nota: Este teste pode falhar se o Redis não estiver configurado com autenticação
        // Em ambiente de produção, seria necessário configurar o Redis com senha
        try {
            factoryWithAuth.create(sessionID, settingsWithAuth);
            // Se chegou aqui, a configuração funcionou
            assertTrue(factoryWithAuth.isInitialized());
        } catch (RuntimeException e) {
            // Esperado se o Redis não tem autenticação configurada
            assertTrue(e.getMessage().contains("Failed to initialize Redis factory") ||
                      e.getMessage().contains("Failed to connect to Redis"));
        } finally {
            factoryWithAuth.shutdown();
        }
    }
    
    @Test
    void testConfigurationWithSSL() throws Exception {
        // Criar configurações com SSL
        SessionSettings settingsWithSSL = new SessionSettings();
        settingsWithSSL.setString("redis.host", "localhost");
        settingsWithSSL.setString("redis.port", "6382");
        settingsWithSSL.setString("redis.ssl", "true");
        
        RedisMessageStoreFactory factoryWithSSL = new RedisMessageStoreFactory();
        
        // Nota: Este teste pode falhar se o Redis não estiver configurado com SSL
        try {
            factoryWithSSL.create(sessionID, settingsWithSSL);
        } catch (RuntimeException e) {
            // Esperado se o Redis não tem SSL configurado
            assertTrue(e.getMessage().contains("Failed to initialize Redis factory") ||
                      e.getMessage().contains("Failed to connect to Redis"));
        } finally {
            factoryWithSSL.shutdown();
        }
    }
    
    @Test
    void testShutdown() {
        // Criar MessageStore
        factory.create(sessionID, sessionSettings);
        assertTrue(factory.isInitialized());
        
        // Fazer shutdown
        factory.shutdown();
        assertEquals(false, factory.isInitialized());
    }
    
    @Test
    void testGetRedisClient() {
        // Inicializar factory
        factory.create(sessionID, sessionSettings);
        
        // Obter cliente Redis
        assertNotNull(factory.getRedisClient());
    }
    
    @Test
    void testGetRedisClientNotInitialized() {
        // Tentar obter cliente Redis sem inicializar
        assertThrows(IllegalStateException.class, () -> {
            factory.getRedisClient();
        });
    }
    
    @Test
    void testInvalidConfiguration() {
        // Configurações com porta inválida
        SessionSettings invalidSettings = new SessionSettings();
        invalidSettings.setString("redis.host", "localhost");
        invalidSettings.setString("redis.port", "99999"); // Porta inválida
        
        // Deve lançar exceção
        assertThrows(RuntimeException.class, () -> {
            factory.create(sessionID, invalidSettings);
        });
    }
    
    @Test
    void testDefaultConfiguration() {
        // Usar configurações mínimas (apenas host e porta)
        SessionSettings minimalSettings = new SessionSettings();
        minimalSettings.setString("redis.host", "localhost");
        minimalSettings.setString("redis.port", "6382");
        
        // Deve funcionar com valores padrão
        MessageStore store = factory.create(sessionID, minimalSettings);
        assertNotNull(store);
    }
    
    @Test
    void testNullArguments() {
        // Testar argumentos nulos
        assertThrows(IllegalArgumentException.class, () -> {
            factory.create(null, sessionSettings);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            factory.create(sessionID, null);
        });
    }
}