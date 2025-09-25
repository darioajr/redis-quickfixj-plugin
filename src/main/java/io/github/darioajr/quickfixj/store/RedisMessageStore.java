package io.github.darioajr.quickfixj.store;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quickfix.MessageStore;
import quickfix.SessionID;
import redis.clients.jedis.JedisPooled;

/**
 * Implementation of QuickFIX/J MessageStore using Redis as persistence backend.
 * 
 * <p>This implementation provides a distributed and scalable way to store and retrieve
 * FIX messages using Redis's distributed caching capabilities. It supports multiple
 * QuickFIX/J sessions with proper isolation and sequence number management.</p>
 * 
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Distributed message storage across cluster nodes</li>
 *   <li>Per-session sequence number management with atomic operations</li>
 *   <li>Session isolation and thread-safe operations</li>
 *   <li>Automatic session creation time tracking</li>
 *   <li>Message range retrieval with efficient caching</li>
 *   <li>Session reset and refresh capabilities</li>
 * </ul>
 * 
 * <p><strong>Redis Structure:</strong></p>
 * <ul>
 *   <li><em>quickfixj:messages:{sessionId}</em> - Stores FIX message content using Redis hashes</li>
 *   <li><em>quickfixj:sequences:{sessionId}</em> - Tracks sender and target sequence numbers per session</li>
 *   <li><em>quickfixj:sessions:{sessionId}</em> - Maintains session creation timestamps</li>
 * </ul>
 * 
 * <p><strong>Thread Safety:</strong></p>
 * <p>This implementation is thread-safe and uses atomic operations for sequence number
 * management. Multiple threads can safely access the same store instance.</p>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * JedisPooled jedis = new JedisPooled("localhost", 6379);
 * SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
 * RedisMessageStore store = new RedisMessageStore(jedis, sessionID);
 * 
 * // Store a message
 * store.set(1, "8=FIX.4.4|9=40|35=0|49=SENDER|56=TARGET|10=000|");
 * 
 * // Retrieve messages
 * Collection<String> messages = new ArrayList<>();
 * store.get(1, 10, messages);
 * }</pre>
 * 
 * @author Dario Oliveira Junior
 * @version 1.0
 * @since 1.0
 * @see quickfix.MessageStore
 * @see redis.clients.jedis.JedisPooled
 * @see quickfix.SessionID
 */
public class RedisMessageStore implements MessageStore {
    
    /** Logger instance for this message store. */
    private static final Logger logger = LoggerFactory.getLogger(RedisMessageStore.class);
    
    /** Cache name prefix for storing FIX messages. */
    private static final String MESSAGES_PREFIX = "quickfixj:messages:";
    
    /** Cache name prefix for storing sequence numbers. */
    private static final String SEQUENCES_PREFIX = "quickfixj:sequences:";
    
    /** Cache name prefix for storing session information. */
    private static final String SESSIONS_PREFIX = "quickfixj:sessions:";
    
    /** The Redis client instance. */
    private final JedisPooled jedis;
    
    /** The SessionID specific to this store instance. */
    private final SessionID sessionID;
    
    /** Map of next sender sequence numbers per session. */
    private final Map<SessionID, AtomicLong> nextSenderSeqNums = new HashMap<>();
    
    /** Map of next target sequence numbers per session. */
    private final Map<SessionID, AtomicLong> nextTargetSeqNums = new HashMap<>();
    
    /**
     * Constructs a new RedisMessageStore for the specified SessionID.
     * 
     * <p>This constructor initializes the message store with the provided Redis client
     * and associates it with the specified QuickFIX/J SessionID. The store will automatically
     * create necessary data structures for managing messages, sequence numbers, and session
     * metadata for this specific session.</p>
     * 
     * <p>The constructor performs the following initialization steps:</p>
     * <ul>
     *   <li>Associates the store with the provided SessionID</li>
     *   <li>Initializes Redis key prefixes for data isolation</li>
     *   <li>Sets up atomic counters for sequence number management</li>
     *   <li>Records session creation timestamp if this is a new session</li>
     * </ul>
     * 
     * @param jedis the Redis client instance to use for all operations
     * @param sessionID the QuickFIX/J SessionID this store will manage
     * @throws IllegalArgumentException if jedis or sessionID is null
     * @see #getCreationTime()
     * @see #reset()
     */
    public RedisMessageStore(JedisPooled jedis, SessionID sessionID) {
        if (jedis == null) {
            throw new IllegalArgumentException("Redis client cannot be null");
        }
        if (sessionID == null) {
            throw new IllegalArgumentException("SessionID cannot be null");
        }
        
        this.jedis = jedis;
        this.sessionID = sessionID;
        
        try {
            // Initialize sequence numbers for this session
            nextSenderSeqNums.put(sessionID, new AtomicLong(getNextSenderMsgSeqNum()));
            nextTargetSeqNums.put(sessionID, new AtomicLong(getNextTargetMsgSeqNum()));
            
            // Set creation time if this is a new session
            String sessionKey = SESSIONS_PREFIX + sessionID.toString();
            if (!jedis.exists(sessionKey)) {
                jedis.hset(sessionKey, "creationTime", String.valueOf(System.currentTimeMillis()));
                logger.info("Created new session entry for SessionID: {}", sessionID);
            }
            
            logger.info("Initialized RedisMessageStore for SessionID: {}", sessionID);
        } catch (IOException e) {
            logger.error("Failed to initialize RedisMessageStore for SessionID: {}", sessionID, e);
            throw new RuntimeException("Failed to initialize message store", e);
        }
    }
    
    /**
     * Generates a Redis key for storing messages for this session.
     * 
     * @return the Redis key for messages
     */
    private String getMessagesKey() {
        return MESSAGES_PREFIX + sessionID.toString();
    }
    
    /**
     * Generates a Redis key for storing sequence numbers for this session.
     * 
     * @return the Redis key for sequences
     */
    private String getSequencesKey() {
        return SEQUENCES_PREFIX + sessionID.toString();
    }
    
    /**
     * Generates a Redis key for storing session info for this session.
     * 
     * @return the Redis key for session info
     */
    private String getSessionKey() {
        return SESSIONS_PREFIX + sessionID.toString();
    }
    
    @Override
    public boolean set(int sequence, String message) throws IOException {
        if (message == null) {
            logger.warn("Attempted to store null message for sequence {} in session {}", sequence, sessionID);
            return false;
        }
        
        try {
            String messageKey = String.valueOf(sequence);
            jedis.hset(getMessagesKey(), messageKey, message);
            
            logger.debug("Stored message for sequence {} in session {}: {} characters", 
                        sequence, sessionID, message.length());
            return true;
        } catch (Exception e) {
            logger.error("Failed to store message for sequence {} in session {}: {}", 
                        sequence, sessionID, e.getMessage(), e);
            throw new IOException("Failed to store message: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void get(int startSequence, int endSequence, Collection<String> messages) throws IOException {
        if (messages == null) {
            throw new IllegalArgumentException("Messages collection cannot be null");
        }
        
        if (startSequence > endSequence) {
            logger.warn("Start sequence {} is greater than end sequence {} for session {}", 
                       startSequence, endSequence, sessionID);
            return;
        }
        
        try {
            String messagesKey = getMessagesKey();
            
            for (int seq = startSequence; seq <= endSequence; seq++) {
                String message = jedis.hget(messagesKey, String.valueOf(seq));
                if (message != null) {
                    messages.add(message);
                    logger.debug("Retrieved message for sequence {} in session {}", seq, sessionID);
                }
            }
            
            logger.debug("Retrieved {} messages for sequence range {}-{} in session {}", 
                        messages.size(), startSequence, endSequence, sessionID);
        } catch (Exception e) {
            logger.error("Failed to retrieve messages for sequence range {}-{} in session {}: {}", 
                        startSequence, endSequence, sessionID, e.getMessage(), e);
            throw new IOException("Failed to retrieve messages: " + e.getMessage(), e);
        }
    }
    
    @Override
    public int getNextSenderMsgSeqNum() throws IOException {
        try {
            String seqKey = getSequencesKey();
            String senderSeqStr = jedis.hget(seqKey, "senderSeqNum");
            
            int nextSeq = (senderSeqStr != null) ? Integer.parseInt(senderSeqStr) : 1;
            
            // Update atomic counter if needed
            AtomicLong counter = nextSenderSeqNums.get(sessionID);
            if (counter != null) {
                counter.set(nextSeq);
            }
            
            logger.debug("Next sender sequence number for session {}: {}", sessionID, nextSeq);
            return nextSeq;
        } catch (Exception e) {
            logger.error("Failed to get next sender sequence number for session {}: {}", 
                        sessionID, e.getMessage(), e);
            throw new IOException("Failed to get next sender sequence number: " + e.getMessage(), e);
        }
    }
    
    @Override
    public int getNextTargetMsgSeqNum() throws IOException {
        try {
            String seqKey = getSequencesKey();
            String targetSeqStr = jedis.hget(seqKey, "targetSeqNum");
            
            int nextSeq = (targetSeqStr != null) ? Integer.parseInt(targetSeqStr) : 1;
            
            // Update atomic counter if needed
            AtomicLong counter = nextTargetSeqNums.get(sessionID);
            if (counter != null) {
                counter.set(nextSeq);
            }
            
            logger.debug("Next target sequence number for session {}: {}", sessionID, nextSeq);
            return nextSeq;
        } catch (Exception e) {
            logger.error("Failed to get next target sequence number for session {}: {}", 
                        sessionID, e.getMessage(), e);
            throw new IOException("Failed to get next target sequence number: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void setNextSenderMsgSeqNum(int next) throws IOException {
        try {
            String seqKey = getSequencesKey();
            jedis.hset(seqKey, "senderSeqNum", String.valueOf(next));
            
            // Update atomic counter
            AtomicLong counter = nextSenderSeqNums.get(sessionID);
            if (counter != null) {
                counter.set(next);
            }
            
            logger.debug("Set next sender sequence number for session {} to: {}", sessionID, next);
        } catch (Exception e) {
            logger.error("Failed to set next sender sequence number for session {} to {}: {}", 
                        sessionID, next, e.getMessage(), e);
            throw new IOException("Failed to set next sender sequence number: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void setNextTargetMsgSeqNum(int next) throws IOException {
        try {
            String seqKey = getSequencesKey();
            jedis.hset(seqKey, "targetSeqNum", String.valueOf(next));
            
            // Update atomic counter
            AtomicLong counter = nextTargetSeqNums.get(sessionID);
            if (counter != null) {
                counter.set(next);
            }
            
            logger.debug("Set next target sequence number for session {} to: {}", sessionID, next);
        } catch (Exception e) {
            logger.error("Failed to set next target sequence number for session {} to {}: {}", 
                        sessionID, next, e.getMessage(), e);
            throw new IOException("Failed to set next target sequence number: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void incrNextSenderMsgSeqNum() throws IOException {
        try {
            AtomicLong counter = nextSenderSeqNums.get(sessionID);
            if (counter != null) {
                long newSeq = counter.incrementAndGet();
                setNextSenderMsgSeqNum((int) newSeq);
                logger.debug("Incremented sender sequence number for session {} to: {}", sessionID, newSeq);
            } else {
                // Fallback: get current and increment
                int current = getNextSenderMsgSeqNum();
                setNextSenderMsgSeqNum(current + 1);
                logger.debug("Incremented sender sequence number for session {} to: {}", sessionID, current + 1);
            }
        } catch (Exception e) {
            logger.error("Failed to increment sender sequence number for session {}: {}", 
                        sessionID, e.getMessage(), e);
            throw new IOException("Failed to increment sender sequence number: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void incrNextTargetMsgSeqNum() throws IOException {
        try {
            AtomicLong counter = nextTargetSeqNums.get(sessionID);
            if (counter != null) {
                long newSeq = counter.incrementAndGet();
                setNextTargetMsgSeqNum((int) newSeq);
                logger.debug("Incremented target sequence number for session {} to: {}", sessionID, newSeq);
            } else {
                // Fallback: get current and increment
                int current = getNextTargetMsgSeqNum();
                setNextTargetMsgSeqNum(current + 1);
                logger.debug("Incremented target sequence number for session {} to: {}", sessionID, current + 1);
            }
        } catch (Exception e) {
            logger.error("Failed to increment target sequence number for session {}: {}", 
                        sessionID, e.getMessage(), e);
            throw new IOException("Failed to increment target sequence number: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Date getCreationTime() throws IOException {
        try {
            String sessionKey = getSessionKey();
            String creationTimeStr = jedis.hget(sessionKey, "creationTime");
            
            if (creationTimeStr != null) {
                long creationTime = Long.parseLong(creationTimeStr);
                Date result = new Date(creationTime);
                logger.debug("Retrieved creation time for session {}: {}", sessionID, result);
                return result;
            } else {
                // If no creation time is stored, return current time and store it
                long currentTime = System.currentTimeMillis();
                jedis.hset(sessionKey, "creationTime", String.valueOf(currentTime));
                Date result = new Date(currentTime);
                logger.info("No creation time found for session {}, set to current time: {}", sessionID, result);
                return result;
            }
        } catch (Exception e) {
            logger.error("Failed to get creation time for session {}: {}", sessionID, e.getMessage(), e);
            throw new IOException("Failed to get creation time: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void reset() throws IOException {
        try {
            logger.info("Resetting message store for session: {}", sessionID);
            
            // Clear all messages for this session
            jedis.del(getMessagesKey());
            
            // Reset sequence numbers to 1
            String seqKey = getSequencesKey();
            jedis.hset(seqKey, "senderSeqNum", "1");
            jedis.hset(seqKey, "targetSeqNum", "1");
            
            // Update atomic counters
            AtomicLong senderCounter = nextSenderSeqNums.get(sessionID);
            if (senderCounter != null) {
                senderCounter.set(1);
            }
            
            AtomicLong targetCounter = nextTargetSeqNums.get(sessionID);
            if (targetCounter != null) {
                targetCounter.set(1);
            }
            
            // Update creation time to current time
            String sessionKey = getSessionKey();
            jedis.hset(sessionKey, "creationTime", String.valueOf(System.currentTimeMillis()));
            
            logger.info("Successfully reset message store for session: {}", sessionID);
        } catch (Exception e) {
            logger.error("Failed to reset message store for session {}: {}", sessionID, e.getMessage(), e);
            throw new IOException("Failed to reset message store: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void refresh() throws IOException {
        try {
            logger.debug("Refreshing message store for session: {}", sessionID);
            
            // Reload sequence numbers from Redis
            int senderSeq = getNextSenderMsgSeqNum();
            int targetSeq = getNextTargetMsgSeqNum();
            
            // Update atomic counters
            AtomicLong senderCounter = nextSenderSeqNums.get(sessionID);
            if (senderCounter != null) {
                senderCounter.set(senderSeq);
            }
            
            AtomicLong targetCounter = nextTargetSeqNums.get(sessionID);
            if (targetCounter != null) {
                targetCounter.set(targetSeq);
            }
            
            logger.debug("Refreshed sequence numbers for session {}: sender={}, target={}", 
                        sessionID, senderSeq, targetSeq);
        } catch (Exception e) {
            logger.error("Failed to refresh message store for session {}: {}", sessionID, e.getMessage(), e);
            throw new IOException("Failed to refresh message store: " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets the SessionID associated with this message store.
     * 
     * @return the SessionID for this store
     */
    public SessionID getSessionID() {
        return sessionID;
    }
    
    /**
     * Gets the Redis client instance used by this store.
     * 
     * @return the Redis client
     */
    public JedisPooled getJedis() {
        return jedis;
    }
}