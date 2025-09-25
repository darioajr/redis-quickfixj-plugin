package io.github.darioajr.quickfixj.store;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import quickfix.SessionID;
import redis.clients.jedis.JedisPooled;
import redis.embedded.RedisServer;

/**
 * Testes unitários para RedisMessageStore.
 */
class RedisMessageStoreTest {
    
    private RedisServer redisServer;
    private JedisPooled jedis;
    private RedisMessageStore messageStore;
    private SessionID sessionID;
    
    @BeforeEach
    void setUp() throws Exception {
        // Iniciar Redis embarcado para testes
        redisServer = new RedisServer(6380);
        redisServer.start();
        
        // Conectar ao Redis
        jedis = new JedisPooled("localhost", 6380);
        
        // Criar SessionID para testes
        sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        
        // Criar MessageStore
        messageStore = new RedisMessageStore(jedis, sessionID);
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
    void testMessageStoreCreation() {
        assertNotNull(messageStore);
        assertEquals(sessionID, messageStore.getSessionID());
        assertNotNull(messageStore.getJedis());
    }
    
    @Test
    void testStoreAndRetrieveMessage() throws Exception {
        String message = "8=FIX.4.4|9=40|35=0|49=SENDER|56=TARGET|34=1|52=20231201-10:00:00|10=000|";
        
        // Armazenar mensagem
        boolean result = messageStore.set(1, message);
        assertTrue(result);
        
        // Recuperar mensagem
        Collection<String> messages = new ArrayList<>();
        messageStore.get(1, 1, messages);
        
        assertEquals(1, messages.size());
        assertEquals(message, messages.iterator().next());
    }
    
    @Test
    void testStoreMultipleMessages() throws Exception {
        String message1 = "8=FIX.4.4|9=40|35=0|49=SENDER|56=TARGET|34=1|10=000|";
        String message2 = "8=FIX.4.4|9=40|35=0|49=SENDER|56=TARGET|34=2|10=000|";
        String message3 = "8=FIX.4.4|9=40|35=0|49=SENDER|56=TARGET|34=3|10=000|";
        
        // Armazenar mensagens
        messageStore.set(1, message1);
        messageStore.set(2, message2);
        messageStore.set(3, message3);
        
        // Recuperar mensagens
        Collection<String> messages = new ArrayList<>();
        messageStore.get(1, 3, messages);
        
        assertEquals(3, messages.size());
    }
    
    @Test
    void testSequenceNumbers() throws Exception {
        // Testar sequence numbers iniciais
        assertEquals(1, messageStore.getNextSenderMsgSeqNum());
        assertEquals(1, messageStore.getNextTargetMsgSeqNum());
        
        // Definir sequence numbers
        messageStore.setNextSenderMsgSeqNum(10);
        messageStore.setNextTargetMsgSeqNum(20);
        
        assertEquals(10, messageStore.getNextSenderMsgSeqNum());
        assertEquals(20, messageStore.getNextTargetMsgSeqNum());
        
        // Incrementar sequence numbers
        messageStore.incrNextSenderMsgSeqNum();
        messageStore.incrNextTargetMsgSeqNum();
        
        assertEquals(11, messageStore.getNextSenderMsgSeqNum());
        assertEquals(21, messageStore.getNextTargetMsgSeqNum());
    }
    
    @Test
    void testCreationTime() throws Exception {
        Date creationTime = messageStore.getCreationTime();
        assertNotNull(creationTime);
        
        // Verificar se a data está próxima do momento atual
        long now = System.currentTimeMillis();
        long diff = Math.abs(now - creationTime.getTime());
        assertTrue(diff < 5000); // Diferença menor que 5 segundos
    }
    
    @Test
    void testReset() throws Exception {
        String message = "8=FIX.4.4|9=40|35=0|49=SENDER|56=TARGET|34=1|10=000|";
        
        // Armazenar mensagem e definir sequence numbers
        messageStore.set(1, message);
        messageStore.setNextSenderMsgSeqNum(10);
        messageStore.setNextTargetMsgSeqNum(20);
        
        // Reset
        messageStore.reset();
        
        // Verificar se os dados foram resetados
        assertEquals(1, messageStore.getNextSenderMsgSeqNum());
        assertEquals(1, messageStore.getNextTargetMsgSeqNum());
        
        // Verificar se as mensagens foram removidas
        Collection<String> messages = new ArrayList<>();
        messageStore.get(1, 1, messages);
        assertEquals(0, messages.size());
    }
    
    @Test
    void testRefresh() throws Exception {
        // Definir sequence numbers
        messageStore.setNextSenderMsgSeqNum(5);
        messageStore.setNextTargetMsgSeqNum(10);
        
        // Simular mudança externa (outro cliente Redis)
        String seqKey = "quickfixj:sequences:" + sessionID.toString();
        jedis.hset(seqKey, "senderSeqNum", "15");
        jedis.hset(seqKey, "targetSeqNum", "25");
        
        // Refresh
        messageStore.refresh();
        
        // Verificar se os valores foram atualizados
        assertEquals(15, messageStore.getNextSenderMsgSeqNum());
        assertEquals(25, messageStore.getNextTargetMsgSeqNum());
    }
    
    @Test
    void testGetMessagesInRange() throws Exception {
        // Armazenar 10 mensagens
        for (int i = 1; i <= 10; i++) {
            String message = String.format("8=FIX.4.4|9=40|35=0|49=SENDER|56=TARGET|34=%d|10=000|", i);
            messageStore.set(i, message);
        }
        
        // Recuperar mensagens no intervalo 3-7
        Collection<String> messages = new ArrayList<>();
        messageStore.get(3, 7, messages);
        
        assertEquals(5, messages.size());
    }
    
    @Test
    void testGetMessagesWithGaps() throws Exception {
        // Armazenar mensagens com lacunas
        messageStore.set(1, "Message 1");
        messageStore.set(3, "Message 3");
        messageStore.set(5, "Message 5");
        
        // Recuperar mensagens no intervalo 1-5
        Collection<String> messages = new ArrayList<>();
        messageStore.get(1, 5, messages);
        
        // Deve retornar apenas as mensagens que existem
        assertEquals(3, messages.size());
    }
    
    @Test
    void testNullMessageHandling() throws Exception {
        // Tentar armazenar mensagem nula
        boolean result = messageStore.set(1, null);
        
        // Deve retornar false
        assertEquals(false, result);
    }
    
    @Test
    void testInvalidSequenceRange() throws Exception {
        Collection<String> messages = new ArrayList<>();
        
        // Tentar recuperar com início maior que fim
        messageStore.get(10, 5, messages);
        
        // Deve retornar lista vazia
        assertEquals(0, messages.size());
    }
    
    @Test
    void testMultipleSessions() throws Exception {
        SessionID sessionID2 = new SessionID("FIX.4.4", "SENDER2", "TARGET2");
        RedisMessageStore messageStore2 = new RedisMessageStore(jedis, sessionID2);
        
        // Armazenar mensagens em diferentes sessões
        messageStore.set(1, "Message from session 1");
        messageStore2.set(1, "Message from session 2");
        
        // Verificar isolamento entre sessões
        Collection<String> messages1 = new ArrayList<>();
        Collection<String> messages2 = new ArrayList<>();
        
        messageStore.get(1, 1, messages1);
        messageStore2.get(1, 1, messages2);
        
        assertEquals(1, messages1.size());
        assertEquals(1, messages2.size());
        assertEquals("Message from session 1", messages1.iterator().next());
        assertEquals("Message from session 2", messages2.iterator().next());
    }
    
    @Test
    void testSequenceNumberPersistence() throws Exception {
        // Definir sequence numbers
        messageStore.setNextSenderMsgSeqNum(100);
        messageStore.setNextTargetMsgSeqNum(200);
        
        // Criar novo MessageStore com a mesma sessão
        RedisMessageStore newMessageStore = new RedisMessageStore(jedis, sessionID);
        
        // Verificar se os sequence numbers persistiram
        assertEquals(100, newMessageStore.getNextSenderMsgSeqNum());
        assertEquals(200, newMessageStore.getNextTargetMsgSeqNum());
    }
}