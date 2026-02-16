package protosocket.cache;

import burp.api.montoya.MontoyaApi;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thread-safe cache that maps WebSocket message payloads to their selected protobuf schemas.
 * This allows each message to remember which schema was applied to it, enabling independent
 * schema selections for different messages (e.g., in Repeater history vs send sections).
 *
 * Uses LRU eviction to prevent unbounded memory growth.
 */
public class MessageSchemaCache {
    private static final int MAX_CACHE_SIZE = 1000;

    private final MontoyaApi api;
    private final Map<Integer, String> cache;

    public MessageSchemaCache(MontoyaApi api) {
        this.api = api;

        // Create LRU cache with access-order iteration
        this.cache = new LinkedHashMap<Integer, String>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
                boolean shouldRemove = size() > MAX_CACHE_SIZE;
                if (shouldRemove) {
                    api.logging().logToOutput("MessageSchemaCache: Evicting oldest entry (cache full)");
                }
                return shouldRemove;
            }
        };
    }

    /**
     * Retrieves the cached schema name for a given message payload.
     *
     * @param payload The WebSocket message payload
     * @return The schema name previously selected for this message, or null if not cached
     */
    public synchronized String getSchemaForMessage(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return null;
        }

        int hash = computeHash(payload);
        String schemaName = cache.get(hash);

        if (schemaName != null) {
            api.logging().logToOutput("MessageSchemaCache: Cache hit for message (hash=" + hash + ", schema=" + schemaName + ")");
        }

        return schemaName;
    }

    /**
     * Stores the schema selection for a given message payload.
     *
     * @param payload The WebSocket message payload
     * @param schemaName The schema name selected for this message
     */
    public synchronized void setSchemaForMessage(byte[] payload, String schemaName) {
        if (payload == null || payload.length == 0 || schemaName == null || schemaName.isEmpty()) {
            return;
        }

        int hash = computeHash(payload);
        cache.put(hash, schemaName);

        api.logging().logToOutput("MessageSchemaCache: Cached schema for message (hash=" + hash + ", schema=" + schemaName + ", cache size=" + cache.size() + ")");
    }

    /**
     * Clears all cached schema selections.
     * Should be called when proto schemas are reloaded to prevent stale selections.
     */
    public synchronized void clear() {
        int size = cache.size();
        cache.clear();
        api.logging().logToOutput("MessageSchemaCache: Cleared " + size + " cached entries");
    }

    /**
     * Returns the current cache size.
     *
     * @return Number of cached message-schema mappings
     */
    public synchronized int size() {
        return cache.size();
    }

    /**
     * Computes a hash for the message payload.
     * Uses Arrays.hashCode() for fast hashing (acceptable collision risk for UI preference caching).
     *
     * @param payload The message payload
     * @return Hash code for the payload
     */
    private int computeHash(byte[] payload) {
        return Arrays.hashCode(payload);
    }
}
