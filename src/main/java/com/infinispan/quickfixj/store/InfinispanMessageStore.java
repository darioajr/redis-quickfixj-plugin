package com.infinispan.quickfixj.store;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quickfix.MessageStore;
import quickfix.SessionID;

/**
 * Implementação do MessageStore do QuickFIX/J usando Infinispan como backend de persistência.
 * Esta implementação permite armazenar e recuperar mensagens FIX de forma distribuída e escalável.
 */
public class InfinispanMessageStore implements MessageStore {
    
    private static final Logger logger = LoggerFactory.getLogger(InfinispanMessageStore.class);
    
    private static final String MESSAGES_CACHE = "quickfixj-messages";
    private static final String SEQUENCES_CACHE = "quickfixj-sequences";
    private static final String SESSIONS_CACHE = "quickfixj-sessions";
    
    private final EmbeddedCacheManager cacheManager;
    private final Cache<String, String> messagesCache;
    private final Cache<String, Integer> sequencesCache;
    private final Cache<String, Long> sessionsCache; // Armazena apenas timestamp de criação
    private final SessionID sessionID; // SessionID específico para esta instância
    
    private final Map<SessionID, AtomicLong> nextSenderSeqNums = new HashMap<>();
    private final Map<SessionID, AtomicLong> nextTargetSeqNums = new HashMap<>();
    
    /**
     * Construtor que recebe um CacheManager do Infinispan já configurado.
     *
     * @param cacheManager o gerenciador de cache do Infinispan
     * @param sessionID o SessionID específico para esta instância do store
     */
    public InfinispanMessageStore(EmbeddedCacheManager cacheManager, SessionID sessionID) {
        this.sessionID = sessionID;
        this.cacheManager = cacheManager;
        this.messagesCache = cacheManager.getCache(MESSAGES_CACHE);
        this.sequencesCache = cacheManager.getCache(SEQUENCES_CACHE);
        this.sessionsCache = cacheManager.getCache(SESSIONS_CACHE);
        
        logger.info("InfinispanMessageStore inicializado com sucesso");
    }
    
    @Override
    public boolean set(int sequence, String message) throws IOException {
        try {
            SessionID sessionID = getCurrentSessionID();
            String key = createMessageKey(sessionID, sequence);
            messagesCache.put(key, message);
            
            logger.debug("Mensagem armazenada: sessionID={}, sequence={}", sessionID, sequence);
            return true;
        } catch (Exception e) {
            logger.error("Erro ao armazenar mensagem: sequence={}", sequence, e);
            throw new IOException("Falha ao armazenar mensagem", e);
        }
    }
    
    @Override
    public void get(int startSequence, int endSequence, Collection<String> messages) throws IOException {
        try {
            SessionID sessionID = getCurrentSessionID();
            
            for (int seq = startSequence; seq <= endSequence; seq++) {
                String key = createMessageKey(sessionID, seq);
                String message = messagesCache.get(key);
                if (message != null) {
                    messages.add(message);
                }
            }
            
            logger.debug("Recuperadas {} mensagens para sessionID={}, range={}-{}", 
                        messages.size(), sessionID, startSequence, endSequence);
        } catch (Exception e) {
            logger.error("Erro ao recuperar mensagens: range={}-{}", startSequence, endSequence, e);
            throw new IOException("Falha ao recuperar mensagens", e);
        }
    }
    
    @Override
    public int getNextSenderMsgSeqNum() throws IOException {
        try {
            SessionID sessionID = getCurrentSessionID();
            return getNextSequenceNumber(sessionID, true);
        } catch (Exception e) {
            logger.error("Erro ao obter próximo número de sequência do sender", e);
            throw new IOException("Falha ao obter sequence number", e);
        }
    }
    
    @Override
    public int getNextTargetMsgSeqNum() throws IOException {
        try {
            SessionID sessionID = getCurrentSessionID();
            return getNextSequenceNumber(sessionID, false);
        } catch (Exception e) {
            logger.error("Erro ao obter próximo número de sequência do target", e);
            throw new IOException("Falha ao obter sequence number", e);
        }
    }
    
    @Override
    public void setNextSenderMsgSeqNum(int next) throws IOException {
        try {
            SessionID sessionID = getCurrentSessionID();
            setSequenceNumber(sessionID, next, true);
            
            nextSenderSeqNums.computeIfAbsent(sessionID, k -> new AtomicLong()).set(next);
            logger.debug("Próximo sender seq num definido: sessionID={}, seqNum={}", sessionID, next);
        } catch (Exception e) {
            logger.error("Erro ao definir próximo número de sequência do sender: {}", next, e);
            throw new IOException("Falha ao definir sequence number", e);
        }
    }
    
    @Override
    public void setNextTargetMsgSeqNum(int next) throws IOException {
        try {
            SessionID sessionID = getCurrentSessionID();
            setSequenceNumber(sessionID, next, false);
            
            nextTargetSeqNums.computeIfAbsent(sessionID, k -> new AtomicLong()).set(next);
            logger.debug("Próximo target seq num definido: sessionID={}, seqNum={}", sessionID, next);
        } catch (Exception e) {
            logger.error("Erro ao definir próximo número de sequência do target: {}", next, e);
            throw new IOException("Falha ao definir sequence number", e);
        }
    }
    
    @Override
    public void incrNextSenderMsgSeqNum() throws IOException {
        try {
            SessionID sessionID = getCurrentSessionID();
            AtomicLong seqNum = nextSenderSeqNums.computeIfAbsent(sessionID, k -> new AtomicLong(1));
            int next = (int) seqNum.incrementAndGet();
            setSequenceNumber(sessionID, next, true);
            
            logger.debug("Sender seq num incrementado: sessionID={}, seqNum={}", sessionID, next);
        } catch (Exception e) {
            logger.error("Erro ao incrementar número de sequência do sender", e);
            throw new IOException("Falha ao incrementar sequence number", e);
        }
    }
    
    @Override
    public void incrNextTargetMsgSeqNum() throws IOException {
        try {
            SessionID sessionID = getCurrentSessionID();
            AtomicLong seqNum = nextTargetSeqNums.computeIfAbsent(sessionID, k -> new AtomicLong(1));
            int next = (int) seqNum.incrementAndGet();
            setSequenceNumber(sessionID, next, false);
            
            logger.debug("Target seq num incrementado: sessionID={}, seqNum={}", sessionID, next);
        } catch (Exception e) {
            logger.error("Erro ao incrementar número de sequência do target", e);
            throw new IOException("Falha ao incrementar sequence number", e);
        }
    }
    
    @Override
    public Date getCreationTime() throws IOException {
        try {
            SessionID sessionID = getCurrentSessionID();
            Long creationTime = sessionsCache.get(sessionID.toString());
            
            if (creationTime != null) {
                return new Date(creationTime);
            } else {
                // Se não existe, criar nova sessão
                Date newCreationTime = new Date();
                sessionsCache.put(sessionID.toString(), newCreationTime.getTime());
                return newCreationTime;
            }
        } catch (Exception e) {
            logger.error("Erro ao obter tempo de criação da sessão", e);
            throw new IOException("Falha ao obter creation time", e);
        }
    }
    
    @Override
    public void reset() throws IOException {
        try {
            SessionID sessionID = getCurrentSessionID();
            
            // Limpar mensagens da sessão
            messagesCache.entrySet().removeIf(entry -> entry.getKey().startsWith(sessionID.toString()));
            
            // Reset sequence numbers
            setSequenceNumber(sessionID, 1, true);
            setSequenceNumber(sessionID, 1, false);
            
            nextSenderSeqNums.put(sessionID, new AtomicLong(1));
            nextTargetSeqNums.put(sessionID, new AtomicLong(1));
            
            // Atualizar tempo de criação
            Date newCreationTime = new Date();
            sessionsCache.put(sessionID.toString(), newCreationTime.getTime());
            
            logger.info("Sessão resetada: sessionID={}", sessionID);
        } catch (Exception e) {
            logger.error("Erro ao resetar store", e);
            throw new IOException("Falha ao resetar store", e);
        }
    }
    
    @Override
    public void refresh() throws IOException {
        try {
            SessionID sessionID = getCurrentSessionID();
            
            // Recarregar sequence numbers do cache
            Integer senderSeq = sequencesCache.get(createSenderSeqKey(sessionID));
            Integer targetSeq = sequencesCache.get(createTargetSeqKey(sessionID));
            
            if (senderSeq != null) {
                nextSenderSeqNums.put(sessionID, new AtomicLong(senderSeq));
            }
            if (targetSeq != null) {
                nextTargetSeqNums.put(sessionID, new AtomicLong(targetSeq));
            }
            
            logger.debug("Store refreshed para sessionID={}", sessionID);
        } catch (Exception e) {
            logger.error("Erro ao fazer refresh do store", e);
            throw new IOException("Falha ao fazer refresh", e);
        }
    }
    
    /**
     * Fecha o store e libera recursos.
     */
    public void close() {
        try {
            if (cacheManager != null && cacheManager.getStatus().allowInvocations()) {
                cacheManager.stop();
            }
            logger.info("InfinispanMessageStore fechado");
        } catch (Exception e) {
            logger.error("Erro ao fechar InfinispanMessageStore", e);
        }
    }
    
    // Métodos auxiliares privados
    
    private SessionID getCurrentSessionID() {
        // Retorna o SessionID específico desta instância
        return this.sessionID;
    }
    
    private String createMessageKey(SessionID sessionID, int sequence) {
        return sessionID.toString() + ":msg:" + sequence;
    }
    
    private String createSenderSeqKey(SessionID sessionID) {
        return sessionID.toString() + ":sender:seq";
    }
    
    private String createTargetSeqKey(SessionID sessionID) {
        return sessionID.toString() + ":target:seq";
    }
    
    private int getNextSequenceNumber(SessionID sessionID, boolean isSender) {
        String key = isSender ? createSenderSeqKey(sessionID) : createTargetSeqKey(sessionID);
        Integer seqNum = sequencesCache.get(key);
        return seqNum != null ? seqNum : 1;
    }
    
    private void setSequenceNumber(SessionID sessionID, int seqNum, boolean isSender) {
        String key = isSender ? createSenderSeqKey(sessionID) : createTargetSeqKey(sessionID);
        sequencesCache.put(key, seqNum);
    }
}
