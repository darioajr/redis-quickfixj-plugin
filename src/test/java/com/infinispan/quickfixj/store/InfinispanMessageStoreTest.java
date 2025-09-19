package com.infinispan.quickfixj.store;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import quickfix.SessionID;

/**
 * Testes unitários para InfinispanMessageStore.
 */
class InfinispanMessageStoreTest {
    
    private EmbeddedCacheManager cacheManager;
    private InfinispanMessageStore messageStore;
    private SessionID sessionID;
    
    @BeforeEach
    void setUp() {
        // Configurar cache manager para testes
        GlobalConfiguration globalConfig = new GlobalConfigurationBuilder()
            .clusteredDefault()
            .transport().clusterName("test-cluster")
            .build();
        
        cacheManager = new DefaultCacheManager(globalConfig);
        
        // Configurar caches
        Configuration cacheConfig = new ConfigurationBuilder()
            .clustering().cacheMode(CacheMode.LOCAL)
            .memory().maxCount(1000)
            .build();
            
        cacheManager.defineConfiguration("quickfixj-messages", cacheConfig);
        cacheManager.defineConfiguration("quickfixj-sequences", cacheConfig);
        cacheManager.defineConfiguration("quickfixj-sessions", cacheConfig);
        
        sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        messageStore = new InfinispanMessageStore(cacheManager, sessionID);
    }
    
    @AfterEach
    void tearDown() {
        if (messageStore != null) {
            messageStore.close();
        }
        if (cacheManager != null) {
            cacheManager.stop();
        }
    }
    
    @Test
    void testSetAndGetMessage() throws Exception {
        // Arrange
        String testMessage = "8=FIX.4.4|9=49|35=A|49=SENDER|56=TARGET|34=1|52=20231219-10:30:00|98=0|108=30|10=142|";
        
        // Act
        boolean result = messageStore.set(1, testMessage);
        
        Collection<String> messages = new ArrayList<>();
        messageStore.get(1, 1, messages);
        
        // Assert
        assertTrue(result);
        assertEquals(1, messages.size());
        assertTrue(messages.contains(testMessage));
    }
    
    @Test
    void testGetMultipleMessages() throws Exception {
        // Arrange
        String message1 = "8=FIX.4.4|9=49|35=A|49=SENDER|56=TARGET|34=1|10=142|";
        String message2 = "8=FIX.4.4|9=49|35=0|49=SENDER|56=TARGET|34=2|10=143|";
        String message3 = "8=FIX.4.4|9=49|35=1|49=SENDER|56=TARGET|34=3|10=144|";
        
        // Act
        messageStore.set(1, message1);
        messageStore.set(2, message2);
        messageStore.set(3, message3);
        
        Collection<String> messages = new ArrayList<>();
        messageStore.get(1, 3, messages);
        
        // Assert
        assertEquals(3, messages.size());
        assertTrue(messages.contains(message1));
        assertTrue(messages.contains(message2));
        assertTrue(messages.contains(message3));
    }
    
    @Test
    void testSequenceNumbers() throws Exception {
        // Act & Assert - Initial values
        assertEquals(1, messageStore.getNextSenderMsgSeqNum());
        assertEquals(1, messageStore.getNextTargetMsgSeqNum());
        
        // Set specific values
        messageStore.setNextSenderMsgSeqNum(10);
        messageStore.setNextTargetMsgSeqNum(20);
        
        assertEquals(10, messageStore.getNextSenderMsgSeqNum());
        assertEquals(20, messageStore.getNextTargetMsgSeqNum());
        
        // Increment values
        messageStore.incrNextSenderMsgSeqNum();
        messageStore.incrNextTargetMsgSeqNum();
        
        assertEquals(11, messageStore.getNextSenderMsgSeqNum());
        assertEquals(21, messageStore.getNextTargetMsgSeqNum());
    }
    
    @Test
    void testCreationTime() throws Exception {
        // Act
        Date creationTime = messageStore.getCreationTime();
        
        // Assert
        assertNotNull(creationTime);
        assertTrue(creationTime.getTime() <= System.currentTimeMillis());
    }
    
    @Test
    void testReset() throws Exception {
        // Arrange
        String testMessage = "8=FIX.4.4|9=49|35=A|49=SENDER|56=TARGET|34=1|10=142|";
        messageStore.set(1, testMessage);
        messageStore.setNextSenderMsgSeqNum(10);
        messageStore.setNextTargetMsgSeqNum(20);
        
        // Act
        messageStore.reset();
        
        // Assert
        assertEquals(1, messageStore.getNextSenderMsgSeqNum());
        assertEquals(1, messageStore.getNextTargetMsgSeqNum());
        
        Collection<String> messages = new ArrayList<>();
        messageStore.get(1, 1, messages);
        assertTrue(messages.isEmpty());
    }
    
    @Test
    void testRefresh() throws Exception {
        // Arrange
        messageStore.setNextSenderMsgSeqNum(100);
        messageStore.setNextTargetMsgSeqNum(200);
        
        // Act
        messageStore.refresh();
        
        // Assert - Values should be preserved after refresh
        assertEquals(100, messageStore.getNextSenderMsgSeqNum());
        assertEquals(200, messageStore.getNextTargetMsgSeqNum());
    }
    
    @Test
    void testConcurrentAccess() throws Exception {
        // Test that multiple threads can access the store safely
        int numThreads = 5;
        int messagesPerThread = 10;
        
        Thread[] threads = new Thread[numThreads];
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < messagesPerThread; j++) {
                        int seqNum = threadId * messagesPerThread + j + 1;
                        String message = "Thread" + threadId + "Message" + j;
                        messageStore.set(seqNum, message);
                    }
                } catch (Exception e) {
                    fail("Thread " + threadId + " failed: " + e.getMessage());
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for completion
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify all messages were stored
        Collection<String> allMessages = new ArrayList<>();
        messageStore.get(1, numThreads * messagesPerThread, allMessages);
        assertEquals(numThreads * messagesPerThread, allMessages.size());
    }
}
