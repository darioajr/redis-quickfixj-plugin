package io.github.darioajr.quickfixj.store;

import java.util.Iterator;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import quickfix.ConfigError;
import quickfix.SessionID;
import redis.clients.jedis.JedisPooled;
import redis.embedded.RedisServer;

/**
 * Testes unitários para RedisSessionSettings.
 */
class RedisSessionSettingsTest {
    
    private RedisServer redisServer;
    private JedisPooled jedis;
    private RedisSessionSettings sessionSettings;
    
    @BeforeEach
    void setUp() throws Exception {
        // Iniciar Redis embarcado para testes
        redisServer = new RedisServer(6381);
        redisServer.start();
        
        // Conectar ao Redis
        jedis = new JedisPooled("localhost", 6381);
        
        // Criar SessionSettings
        sessionSettings = new RedisSessionSettings(jedis);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (jedis != null) {
            // Limpar dados de teste
            jedis.flushDB();
            jedis.close();
        }
        
        if (redisServer != null) {
            redisServer.stop();
        }
    }
    
    @Test
    void testSessionSettingsCreation() {
        assertNotNull(sessionSettings);
        assertEquals(jedis, sessionSettings.getJedis());
        assertTrue(sessionSettings.isCacheEnabled());
    }
    
    @Test
    void testSetAndGetDefaultSettings() throws Exception {
        // Definir configurações padrão
        sessionSettings.setString("ConnectionType", "initiator");
        sessionSettings.setString("HeartBtInt", "30");
        
        // Recuperar configurações
        assertEquals("initiator", sessionSettings.getString("ConnectionType"));
        assertEquals("30", sessionSettings.getString("HeartBtInt"));
    }
    
    @Test
    void testSetAndGetSessionSettings() throws Exception {
        SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        
        // Definir configurações da sessão
        sessionSettings.setString(sessionID, "SocketConnectHost", "localhost");
        sessionSettings.setString(sessionID, "SocketConnectPort", "9876");
        
        // Recuperar configurações
        assertEquals("localhost", sessionSettings.getString(sessionID, "SocketConnectHost"));
        assertEquals("9876", sessionSettings.getString(sessionID, "SocketConnectPort"));
    }
    
    @Test
    void testSessionSettingsInheritance() throws Exception {
        SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        
        // Definir configuração padrão
        sessionSettings.setString("HeartBtInt", "30");
        
        // Definir configuração específica da sessão
        sessionSettings.setString(sessionID, "SocketConnectHost", "localhost");
        
        // A sessão deve herdar a configuração padrão
        assertEquals("30", sessionSettings.getString(sessionID, "HeartBtInt"));
        
        // E ter sua configuração específica
        assertEquals("localhost", sessionSettings.getString(sessionID, "SocketConnectHost"));
    }
    
    @Test
    void testSessionSettingsOverride() throws Exception {
        SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        
        // Definir configuração padrão
        sessionSettings.setString("HeartBtInt", "30");
        
        // Sobrescrever na sessão
        sessionSettings.setString(sessionID, "HeartBtInt", "60");
        
        // A configuração da sessão deve prevalecer
        assertEquals("60", sessionSettings.getString(sessionID, "HeartBtInt"));
        
        // A configuração padrão deve permanecer inalterada
        assertEquals("30", sessionSettings.getString("HeartBtInt"));
    }
    
    @Test
    void testMissingConfiguration() {
        SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        
        // Tentar acessar configuração inexistente
        assertThrows(ConfigError.class, () -> {
            sessionSettings.getString("NonExistentKey");
        });
        
        assertThrows(ConfigError.class, () -> {
            sessionSettings.getString(sessionID, "NonExistentKey");
        });
    }
    
    @Test
    void testSectionIterator() throws Exception {
        SessionID sessionID1 = new SessionID("FIX.4.4", "SENDER1", "TARGET1");
        SessionID sessionID2 = new SessionID("FIX.4.4", "SENDER2", "TARGET2");
        
        // Definir configurações para diferentes sessões
        sessionSettings.setString(sessionID1, "Key1", "Value1");
        sessionSettings.setString(sessionID2, "Key2", "Value2");
        
        // Iterar sobre as sessões
        Iterator<SessionID> iterator = sessionSettings.sectionIterator();
        
        int count = 0;
        while (iterator.hasNext()) {
            SessionID sessionID = iterator.next();
            assertNotNull(sessionID);
            count++;
        }
        
        assertEquals(2, count);
    }
    
    @Test
    void testCacheDisabled() throws Exception {
        // Criar SessionSettings com cache desabilitado
        RedisSessionSettings settings = new RedisSessionSettings(jedis, false);
        
        assertEquals(false, settings.isCacheEnabled());
        
        // Definir e recuperar configuração
        settings.setString("TestKey", "TestValue");
        assertEquals("TestValue", settings.getString("TestKey"));
    }
    
    @Test
    void testCacheToggle() throws Exception {
        // Definir configuração com cache habilitado
        sessionSettings.setString("TestKey", "TestValue");
        
        // Desabilitar cache
        sessionSettings.setCacheEnabled(false);
        assertEquals(false, sessionSettings.isCacheEnabled());
        
        // Ainda deve conseguir recuperar configuração
        assertEquals("TestValue", sessionSettings.getString("TestKey"));
        
        // Reabilitar cache
        sessionSettings.setCacheEnabled(true);
        assertEquals(true, sessionSettings.isCacheEnabled());
    }
    
    @Test
    void testRemoveSession() throws Exception {
        SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        
        // Definir configurações da sessão
        sessionSettings.setString(sessionID, "Key1", "Value1");
        sessionSettings.setString(sessionID, "Key2", "Value2");
        
        // Verificar que as configurações existem
        assertEquals("Value1", sessionSettings.getString(sessionID, "Key1"));
        
        // Remover sessão
        sessionSettings.removeSession(sessionID);
        
        // Verificar que as configurações foram removidas
        assertThrows(ConfigError.class, () -> {
            sessionSettings.getString(sessionID, "Key1");
        });
    }
    
    @Test
    void testClearAll() throws Exception {
        SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        
        // Definir configurações padrão e da sessão
        sessionSettings.setString("DefaultKey", "DefaultValue");
        sessionSettings.setString(sessionID, "SessionKey", "SessionValue");
        
        // Limpar todas as configurações
        sessionSettings.clearAll();
        
        // Verificar que todas as configurações foram removidas
        assertThrows(ConfigError.class, () -> {
            sessionSettings.getString("DefaultKey");
        });
        
        assertThrows(ConfigError.class, () -> {
            sessionSettings.getString(sessionID, "SessionKey");
        });
    }
    
    @Test
    void testReload() throws Exception {
        SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        
        // Definir configurações
        sessionSettings.setString("DefaultKey", "DefaultValue");
        sessionSettings.setString(sessionID, "SessionKey", "SessionValue");
        
        // Simular mudança externa no Redis
        jedis.hset("quickfixj:settings:DEFAULT", "ExternalKey", "ExternalValue");
        
        // Recarregar configurações
        sessionSettings.reload();
        
        // Verificar que a nova configuração está disponível
        assertEquals("ExternalValue", sessionSettings.getString("ExternalKey"));
        
        // Verificar que as configurações existentes ainda estão lá
        assertEquals("DefaultValue", sessionSettings.getString("DefaultKey"));
    }
    
    @Test
    void testCacheStatistics() throws Exception {
        SessionID sessionID1 = new SessionID("FIX.4.4", "SENDER1", "TARGET1");
        SessionID sessionID2 = new SessionID("FIX.4.4", "SENDER2", "TARGET2");
        
        // Definir várias configurações
        sessionSettings.setString("DefaultKey1", "Value1");
        sessionSettings.setString("DefaultKey2", "Value2");
        sessionSettings.setString(sessionID1, "SessionKey1", "Value1");
        sessionSettings.setString(sessionID2, "SessionKey2", "Value2");
        
        // Obter estatísticas do cache
        Map<String, Object> stats = sessionSettings.getCacheStatistics();
        
        assertNotNull(stats);
        assertEquals(true, stats.get("cacheEnabled"));
        assertTrue((Integer) stats.get("defaultCacheSize") >= 2);
        assertTrue((Integer) stats.get("sessionCacheCount") >= 2);
        assertTrue((Integer) stats.get("totalSessionIDs") >= 2);
    }
    
    @Test
    void testPersistence() throws Exception {
        SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        
        // Definir configurações
        sessionSettings.setString("DefaultKey", "DefaultValue");
        sessionSettings.setString(sessionID, "SessionKey", "SessionValue");
        
        // Criar novo RedisSessionSettings com a mesma instância Redis
        RedisSessionSettings newSessionSettings = new RedisSessionSettings(jedis);
        
        // Verificar que as configurações persistiram
        assertEquals("DefaultValue", newSessionSettings.getString("DefaultKey"));
        assertEquals("SessionValue", newSessionSettings.getString(sessionID, "SessionKey"));
    }
    
    @Test
    void testMultipleSessionsIsolation() throws Exception {
        SessionID sessionID1 = new SessionID("FIX.4.4", "SENDER1", "TARGET1");
        SessionID sessionID2 = new SessionID("FIX.4.4", "SENDER2", "TARGET2");
        
        // Definir configurações diferentes para cada sessão
        sessionSettings.setString(sessionID1, "SameKey", "Value1");
        sessionSettings.setString(sessionID2, "SameKey", "Value2");
        
        // Verificar isolamento
        assertEquals("Value1", sessionSettings.getString(sessionID1, "SameKey"));
        assertEquals("Value2", sessionSettings.getString(sessionID2, "SameKey"));
    }
    
    @Test
    void testNullSessionHandling() {
        // Remover sessão nula não deve causar erro
        sessionSettings.removeSession(null);
    }
}