package protosocket.core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.websocket.Direction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe storage for WebSocket messages and their decoded protobuf data.
 * Implements LRU eviction to handle large projects.
 */
public class WebSocketMessageStore {
    private static final int MAX_STORED_MESSAGES = 10000;

    private final MontoyaApi api;
    private final Map<String, StoredMessage> messages;
    private final Map<String, List<ProtobufField>> decodedData;

    public WebSocketMessageStore(MontoyaApi api) {
        this.api = api;
        // Use ConcurrentHashMap for thread safety
        this.messages = new ConcurrentHashMap<>();
        this.decodedData = new ConcurrentHashMap<>();
    }

    /**
     * Store a binary WebSocket message and return its unique ID.
     */
    public String storeMessage(byte[] payload, Direction direction) {
        evictOldestIfNeeded();

        String id = generateMessageId();
        StoredMessage msg = new StoredMessage(payload, direction, System.currentTimeMillis());
        messages.put(id, msg);

        return id;
    }

    /**
     * Store decoded protobuf data for a message.
     */
    public void storeDecodedData(String messageId, List<ProtobufField> fields) {
        if (messages.containsKey(messageId)) {
            decodedData.put(messageId, fields);
        }
    }

    /**
     * Retrieve decoded data for a message.
     */
    public List<ProtobufField> getDecodedData(String messageId) {
        return decodedData.get(messageId);
    }

    /**
     * Get stored message by ID.
     */
    public StoredMessage getMessage(String messageId) {
        return messages.get(messageId);
    }

    /**
     * Remove decoded data (for example, if user modified it).
     */
    public void removeDecodedData(String messageId) {
        decodedData.remove(messageId);
    }

    /**
     * Check if message has decoded data.
     */
    public boolean hasDecodedData(String messageId) {
        return decodedData.containsKey(messageId);
    }

    /**
     * Evict oldest messages if we're at capacity.
     */
    private void evictOldestIfNeeded() {
        if (messages.size() >= MAX_STORED_MESSAGES) {
            // Find oldest message based on timestamp
            String oldestId = null;
            long oldestTimestamp = Long.MAX_VALUE;

            for (Map.Entry<String, StoredMessage> entry : messages.entrySet()) {
                if (entry.getValue().timestamp < oldestTimestamp) {
                    oldestTimestamp = entry.getValue().timestamp;
                    oldestId = entry.getKey();
                }
            }

            if (oldestId != null) {
                messages.remove(oldestId);
                decodedData.remove(oldestId);
                api.logging().logToOutput("Evicted old message: " + oldestId + " (size limit reached)");
            }
        }
    }

    /**
     * Generate unique message ID.
     */
    private String generateMessageId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Get current number of stored messages.
     */
    public int getMessageCount() {
        return messages.size();
    }

    /**
     * Clear all stored messages and decoded data.
     */
    public void clear() {
        messages.clear();
        decodedData.clear();
        api.logging().logToOutput("Cleared all stored messages");
    }

    /**
     * Inner class representing a stored WebSocket message.
     */
    public static class StoredMessage {
        public final byte[] payload;
        public final Direction direction;
        public final long timestamp;

        public StoredMessage(byte[] payload, Direction direction, long timestamp) {
            this.payload = payload;
            this.direction = direction;
            this.timestamp = timestamp;
        }
    }
}
