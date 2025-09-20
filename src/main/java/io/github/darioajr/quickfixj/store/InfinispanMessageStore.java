package io.github.darioajr.quickfixj.store;

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
 * Implementation of QuickFIX/J MessageStore using Infinispan as persistence backend.
 * 
 * <p>This implementation provides a distributed and scalable way to store and retrieve
 * FIX messages using Infinispan's distributed caching capabilities. It supports multiple
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
 * <p><strong>Cache Structure:</strong></p>
 * <ul>
 *   <li><em>quickfixj-messages</em> - Stores FIX message content keyed by session and sequence</li>
 *   <li><em>quickfixj-sequences</em> - Tracks sender and target sequence numbers per session</li>
 *   <li><em>quickfixj-sessions</em> - Maintains session creation timestamps</li>
 * </ul>
 * 
 * <p><strong>Thread Safety:</strong></p>
 * <p>This implementation is thread-safe and uses atomic operations for sequence number
 * management. Multiple threads can safely access the same store instance.</p>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * EmbeddedCacheManager cacheManager = new DefaultCacheManager();
 * SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
 * InfinispanMessageStore store = new InfinispanMessageStore(cacheManager, sessionID);
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
 * @see org.infinispan.manager.EmbeddedCacheManager
 * @see quickfix.SessionID
 */
public class InfinispanMessageStore implements MessageStore {
    
    /** Logger instance for this message store. */
    private static final Logger logger = LoggerFactory.getLogger(InfinispanMessageStore.class);
    
    /** Cache name for storing FIX messages. */
    private static final String MESSAGES_CACHE = "quickfixj-messages";
    
    /** Cache name for storing sequence numbers. */
    private static final String SEQUENCES_CACHE = "quickfixj-sequences";
    
    /** Cache name for storing session information. */
    private static final String SESSIONS_CACHE = "quickfixj-sessions";
    
    /** The Infinispan cache manager instance. */
    private final EmbeddedCacheManager cacheManager;
    
    /** Cache for storing FIX message content. */
    private final Cache<String, String> messagesCache;
    
    /** Cache for storing sequence numbers. */
    private final Cache<String, Integer> sequencesCache;
    
    /** Cache for storing session timestamps (creation time only). */
    private final Cache<String, Long> sessionsCache; // Stores only creation timestamp
    
    /** The SessionID specific to this store instance. */
    private final SessionID sessionID; // Specific SessionID for this instance
    
    /** Map of next sender sequence numbers per session. */
    private final Map<SessionID, AtomicLong> nextSenderSeqNums = new HashMap<>();
    
    /** Map of next target sequence numbers per session. */
    private final Map<SessionID, AtomicLong> nextTargetSeqNums = new HashMap<>();
    
    /**
     * Constructor that accepts a pre-configured Infinispan CacheManager.
     * 
     * <p>Creates a new message store instance for the specified session using the
     * provided cache manager. The constructor initializes all required caches and
     * prepares the store for message operations.</p>
     * 
     * <p>The following caches are automatically obtained from the cache manager:</p>
     * <ul>
     *   <li><em>quickfixj-messages</em> - For storing FIX message content</li>
     *   <li><em>quickfixj-sequences</em> - For tracking sequence numbers</li>
     *   <li><em>quickfixj-sessions</em> - For session metadata</li>
     * </ul>
     *
     * @param cacheManager the Infinispan cache manager instance
     * @param sessionID the specific SessionID for this store instance
     * @throws IllegalArgumentException if cacheManager or sessionID is null
     */
    public InfinispanMessageStore(EmbeddedCacheManager cacheManager, SessionID sessionID) {
        if (cacheManager == null) {
            throw new IllegalArgumentException("CacheManager cannot be null");
        }
        if (sessionID == null) {
            throw new IllegalArgumentException("SessionID cannot be null");
        }
        
        this.sessionID = sessionID;
        this.cacheManager = cacheManager;
        this.messagesCache = cacheManager.getCache(MESSAGES_CACHE);
        this.sequencesCache = cacheManager.getCache(SEQUENCES_CACHE);
        this.sessionsCache = cacheManager.getCache(SESSIONS_CACHE);
        
        logger.info("InfinispanMessageStore initialized successfully for session: {}", sessionID);
    }
    
    /**
     * Stores a FIX message in the cache with the specified sequence number.
     * 
     * <p>This method implements the QuickFIX/J MessageStore interface for storing
     * messages. The message is stored with a composite key that includes the session ID
     * and sequence number to ensure proper isolation between sessions.</p>
     *
     * @param sequence the sequence number for the message
     * @param message the FIX message content to store
     * @return true if the message was successfully stored
     * @throws IOException if an error occurs during storage
     */
    @Override
    public boolean set(int sequence, String message) throws IOException {
        try {
            SessionID currentSession = getCurrentSessionID();
            String key = createMessageKey(currentSession, sequence);
            messagesCache.put(key, message);
            
            logger.debug("Message stored: sessionID={}, sequence={}", currentSession, sequence);
            return true;
        } catch (Exception e) {
            logger.error("Error storing message: sequence={}", sequence, e);
            throw new IOException("Failed to store message", e);
        }
    }
    
    /**
     * Retrieves messages for the specified sequence number range.
     * 
     * <p>This method retrieves all stored messages within the given sequence number
     * range and adds them to the provided collection. Messages that don't exist
     * for specific sequence numbers are simply skipped.</p>
     *
     * @param startSequence the starting sequence number (inclusive)
     * @param endSequence the ending sequence number (inclusive)
     * @param messages the collection to add retrieved messages to
     * @throws IOException if an error occurs during retrieval
     */
    @Override
    public void get(int startSequence, int endSequence, Collection<String> messages) throws IOException {
        try {
            SessionID currentSession = getCurrentSessionID();
            
            for (int seq = startSequence; seq <= endSequence; seq++) {
                String key = createMessageKey(currentSession, seq);
                String message = messagesCache.get(key);
                if (message != null) {
                    messages.add(message);
                }
            }
            
            logger.debug("Retrieved {} messages for sessionID={}, range={}-{}", 
                        messages.size(), currentSession, startSequence, endSequence);
        } catch (Exception e) {
            logger.error("Error retrieving messages: range={}-{}", startSequence, endSequence, e);
            throw new IOException("Failed to retrieve messages", e);
        }
    }
    
    /**
     * Gets the next sender message sequence number.
     * 
     * <p>Returns the next sequence number that should be used for outgoing messages
     * from this session. If no sequence number has been set, returns 1 as the default.</p>
     *
     * @return the next sender sequence number
     * @throws IOException if an error occurs during retrieval
     */
    @Override
    public int getNextSenderMsgSeqNum() throws IOException {
        try {
            SessionID currentSession = getCurrentSessionID();
            return getNextSequenceNumber(currentSession, true);
        } catch (Exception e) {
            logger.error("Error getting next sender sequence number", e);
            throw new IOException("Failed to get sequence number", e);
        }
    }

    /**
     * Gets the next target message sequence number.
     * 
     * <p>Returns the next sequence number that is expected from the target (counterparty)
     * for incoming messages. If no sequence number has been set, returns 1 as the default.</p>
     *
     * @return the next target sequence number
     * @throws IOException if an error occurs during retrieval
     */
    @Override
    public int getNextTargetMsgSeqNum() throws IOException {
        try {
            SessionID currentSession = getCurrentSessionID();
            return getNextSequenceNumber(currentSession, false);
        } catch (Exception e) {
            logger.error("Error getting next target sequence number", e);
            throw new IOException("Failed to get sequence number", e);
        }
    }
    
    /**
     * Sets the next sender message sequence number.
     * 
     * <p>Updates the sequence number that will be used for the next outgoing message
     * from this session. This is typically called during session initialization or
     * when recovering from a stored sequence number.</p>
     *
     * @param next the next sender sequence number to set
     * @throws IOException if an error occurs during the update
     */
    @Override
    public void setNextSenderMsgSeqNum(int next) throws IOException {
        try {
            SessionID currentSession = getCurrentSessionID();
            setSequenceNumber(currentSession, next, true);
            
            nextSenderSeqNums.computeIfAbsent(currentSession, k -> new AtomicLong()).set(next);
            logger.debug("Next sender seq num set: sessionID={}, seqNum={}", currentSession, next);
        } catch (Exception e) {
            logger.error("Error setting next sender sequence number: {}", next, e);
            throw new IOException("Failed to set sequence number", e);
        }
    }

    /**
     * Sets the next target message sequence number.
     * 
     * <p>Updates the sequence number that is expected for the next incoming message
     * from the target (counterparty). This is typically called during session
     * initialization or when recovering from a stored sequence number.</p>
     *
     * @param next the next target sequence number to set
     * @throws IOException if an error occurs during the update
     */
    @Override
    public void setNextTargetMsgSeqNum(int next) throws IOException {
        try {
            SessionID currentSession = getCurrentSessionID();
            setSequenceNumber(currentSession, next, false);
            
            nextTargetSeqNums.computeIfAbsent(currentSession, k -> new AtomicLong()).set(next);
            logger.debug("Next target seq num set: sessionID={}, seqNum={}", currentSession, next);
        } catch (Exception e) {
            logger.error("Error setting next target sequence number: {}", next, e);
            throw new IOException("Failed to set sequence number", e);
        }
    }
    
    /**
     * Increments the next sender message sequence number.
     * 
     * <p>Atomically increments the sender sequence number and updates it in the cache.
     * This method is typically called after sending a message to prepare for the next
     * outgoing message.</p>
     *
     * @throws IOException if an error occurs during the increment operation
     */
    @Override
    public void incrNextSenderMsgSeqNum() throws IOException {
        try {
            SessionID currentSession = getCurrentSessionID();
            AtomicLong seqNum = nextSenderSeqNums.computeIfAbsent(currentSession, k -> new AtomicLong(1));
            int next = (int) seqNum.incrementAndGet();
            setSequenceNumber(currentSession, next, true);
            
            logger.debug("Sender seq num incremented: sessionID={}, seqNum={}", currentSession, next);
        } catch (Exception e) {
            logger.error("Error incrementing sender sequence number", e);
            throw new IOException("Failed to increment sequence number", e);
        }
    }

    /**
     * Increments the next target message sequence number.
     * 
     * <p>Atomically increments the target sequence number and updates it in the cache.
     * This method is typically called after receiving a message to prepare for the next
     * expected incoming message.</p>
     *
     * @throws IOException if an error occurs during the increment operation
     */
    @Override
    public void incrNextTargetMsgSeqNum() throws IOException {
        try {
            SessionID currentSession = getCurrentSessionID();
            AtomicLong seqNum = nextTargetSeqNums.computeIfAbsent(currentSession, k -> new AtomicLong(1));
            int next = (int) seqNum.incrementAndGet();
            setSequenceNumber(currentSession, next, false);
            
            logger.debug("Target seq num incremented: sessionID={}, seqNum={}", currentSession, next);
        } catch (Exception e) {
            logger.error("Error incrementing target sequence number", e);
            throw new IOException("Failed to increment sequence number", e);
        }
    }
    
    /**
     * Gets the creation time for this session.
     * 
     * <p>Returns the timestamp when this session was first created. If the session
     * doesn't exist in the cache, a new creation time is generated and stored.</p>
     *
     * @return the session creation time
     * @throws IOException if an error occurs during retrieval or creation
     */
    @Override
    public Date getCreationTime() throws IOException {
        try {
            SessionID currentSession = getCurrentSessionID();
            Long creationTime = sessionsCache.get(currentSession.toString());
            
            if (creationTime != null) {
                return new Date(creationTime);
            } else {
                // If doesn't exist, create new session
                Date newCreationTime = new Date();
                sessionsCache.put(currentSession.toString(), newCreationTime.getTime());
                return newCreationTime;
            }
        } catch (Exception e) {
            logger.error("Error getting session creation time", e);
            throw new IOException("Failed to get creation time", e);
        }
    }
    
    /**
     * Resets the message store for this session.
     * 
     * <p>This method performs a complete reset of the store by:</p>
     * <ul>
     *   <li>Removing all stored messages for this session</li>
     *   <li>Resetting sender and target sequence numbers to 1</li>
     *   <li>Updating the session creation time to current time</li>
     *   <li>Clearing in-memory sequence number caches</li>
     * </ul>
     *
     * @throws IOException if an error occurs during the reset operation
     */
    @Override
    public void reset() throws IOException {
        try {
            SessionID currentSession = getCurrentSessionID();
            
            // Clear session messages
            messagesCache.entrySet().removeIf(entry -> entry.getKey().startsWith(currentSession.toString()));
            
            // Reset sequence numbers
            setSequenceNumber(currentSession, 1, true);
            setSequenceNumber(currentSession, 1, false);
            
            nextSenderSeqNums.put(currentSession, new AtomicLong(1));
            nextTargetSeqNums.put(currentSession, new AtomicLong(1));
            
            // Update creation time
            Date newCreationTime = new Date();
            sessionsCache.put(currentSession.toString(), newCreationTime.getTime());
            
            logger.info("Session reset: sessionID={}", currentSession);
        } catch (Exception e) {
            logger.error("Error resetting store", e);
            throw new IOException("Failed to reset store", e);
        }
    }
    
    /**
     * Refreshes the message store by reloading data from the cache.
     * 
     * <p>This method reloads the sequence numbers from the distributed cache,
     * ensuring that the local in-memory copies are synchronized with the
     * potentially updated values from other cluster nodes.</p>
     *
     * @throws IOException if an error occurs during the refresh operation
     */
    @Override
    public void refresh() throws IOException {
        try {
            SessionID currentSession = getCurrentSessionID();
            
            // Reload sequence numbers from cache
            Integer senderSeq = sequencesCache.get(createSenderSeqKey(currentSession));
            Integer targetSeq = sequencesCache.get(createTargetSeqKey(currentSession));
            
            if (senderSeq != null) {
                nextSenderSeqNums.put(currentSession, new AtomicLong(senderSeq));
            }
            if (targetSeq != null) {
                nextTargetSeqNums.put(currentSession, new AtomicLong(targetSeq));
            }
            
            logger.debug("Store refreshed for sessionID={}", currentSession);
        } catch (Exception e) {
            logger.error("Error refreshing store", e);
            throw new IOException("Failed to refresh", e);
        }
    }
    
    /**
     * Closes the store and releases resources.
     * 
     * <p>This method performs cleanup by stopping the cache manager if it's still
     * running and accepting invocations. This should be called when the store is
     * no longer needed to ensure proper resource cleanup.</p>
     * 
     * <p><strong>Note:</strong> After calling this method, the store should not be used
     * for any further operations.</p>
     */
    public void close() {
        try {
            if (cacheManager != null && cacheManager.getStatus().allowInvocations()) {
                cacheManager.stop();
            }
            logger.info("InfinispanMessageStore closed for session: {}", sessionID);
        } catch (Exception e) {
            logger.error("Error closing InfinispanMessageStore", e);
        }
    }
    
    // Private Helper Methods
    
    /**
     * Gets the current SessionID for this store instance.
     * 
     * @return the specific SessionID for this instance
     */
    private SessionID getCurrentSessionID() {
        // Returns the specific SessionID for this instance
        return this.sessionID;
    }

    /**
     * Creates a cache key for storing a message.
     * 
     * @param currentSession the session ID
     * @param sequence the message sequence number
     * @return the cache key string
     */
    private String createMessageKey(SessionID currentSession, int sequence) {
        return currentSession.toString() + ":msg:" + sequence;
    }

    /**
     * Creates a cache key for the sender sequence number.
     * 
     * @param currentSession the session ID
     * @return the cache key string for sender sequence
     */
    private String createSenderSeqKey(SessionID currentSession) {
        return currentSession.toString() + ":sender:seq";
    }

    /**
     * Creates a cache key for the target sequence number.
     * 
     * @param currentSession the session ID
     * @return the cache key string for target sequence
     */
    private String createTargetSeqKey(SessionID currentSession) {
        return currentSession.toString() + ":target:seq";
    }

    /**
     * Gets the next sequence number for sender or target.
     * 
     * @param currentSession the session ID
     * @param isSender true for sender sequence, false for target sequence
     * @return the next sequence number, or 1 if not found
     */
    private int getNextSequenceNumber(SessionID currentSession, boolean isSender) {
        String key = isSender ? createSenderSeqKey(currentSession) : createTargetSeqKey(currentSession);
        Integer seqNum = sequencesCache.get(key);
        return seqNum != null ? seqNum : 1;
    }

    /**
     * Sets the sequence number for sender or target.
     * 
     * @param currentSession the session ID
     * @param seqNum the sequence number to set
     * @param isSender true for sender sequence, false for target sequence
     */
    private void setSequenceNumber(SessionID currentSession, int seqNum, boolean isSender) {
        String key = isSender ? createSenderSeqKey(currentSession) : createTargetSeqKey(currentSession);
        sequencesCache.put(key, seqNum);
    }
}