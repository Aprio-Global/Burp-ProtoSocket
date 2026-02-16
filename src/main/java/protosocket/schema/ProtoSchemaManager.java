package protosocket.schema;

import burp.api.montoya.MontoyaApi;
import protosocket.cache.MessageSchemaCache;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages loaded .proto schemas and provides lookups for message types.
 * Handles multiple .proto files with import resolution.
 */
public class ProtoSchemaManager {
    private final MontoyaApi api;

    // Map of message type name to descriptor
    private final Map<String, Descriptor> messageDescriptors = new ConcurrentHashMap<>();

    // Map of file name to file descriptor
    private final Map<String, FileDescriptor> fileDescriptors = new ConcurrentHashMap<>();

    // Search paths for resolving imports
    private final List<String> searchPaths = new ArrayList<>();

    // Last selected schema for auto-apply feature
    private String lastSelectedSchema = null;

    // Message schema cache (injected after construction)
    private MessageSchemaCache messageSchemaCache;

    public ProtoSchemaManager(MontoyaApi api) {
        this.api = api;
    }

    /**
     * Set the message schema cache to be cleared when schemas are reloaded.
     * This ensures that cached message-to-schema mappings don't reference stale schemas.
     */
    public void setMessageSchemaCache(MessageSchemaCache cache) {
        this.messageSchemaCache = cache;
    }

    /**
     * Add a search path for resolving proto imports.
     */
    public void addSearchPath(String path) {
        if (!searchPaths.contains(path)) {
            searchPaths.add(path);
            api.logging().logToOutput("Added proto search path: " + path);
        }
    }

    /**
     * Get all search paths.
     */
    public List<String> getSearchPaths() {
        return new ArrayList<>(searchPaths);
    }

    /**
     * Register a FileDescriptor and all its message types.
     */
    public void registerFileDescriptor(FileDescriptor fileDescriptor) {
        String fileName = fileDescriptor.getName();
        fileDescriptors.put(fileName, fileDescriptor);

        // Register all message types from this file
        for (Descriptor messageType : fileDescriptor.getMessageTypes()) {
            registerMessageType(messageType);
        }

        api.logging().logToOutput("Registered proto file: " + fileName +
                                 " (" + fileDescriptor.getMessageTypes().size() + " message types)");
    }

    /**
     * Recursively register a message type and its nested types.
     */
    private void registerMessageType(Descriptor descriptor) {
        // Register by simple name
        messageDescriptors.put(descriptor.getName(), descriptor);

        // Register by fully qualified name
        messageDescriptors.put(descriptor.getFullName(), descriptor);

        // Register nested types
        for (Descriptor nestedType : descriptor.getNestedTypes()) {
            registerMessageType(nestedType);
        }
    }

    /**
     * Look up a message descriptor by name.
     * Supports both simple names and fully qualified names.
     */
    public Descriptor getMessageDescriptor(String messageName) {
        return messageDescriptors.get(messageName);
    }

    /**
     * Get a file descriptor by file name.
     */
    public FileDescriptor getFileDescriptor(String fileName) {
        return fileDescriptors.get(fileName);
    }

    /**
     * Check if any schemas are loaded.
     */
    public boolean hasSchemasLoaded() {
        return !messageDescriptors.isEmpty();
    }

    /**
     * Get all registered message type names.
     */
    public Set<String> getAllMessageTypeNames() {
        return new HashSet<>(messageDescriptors.keySet());
    }

    /**
     * Clear all loaded schemas.
     * Also clears the message schema cache to prevent stale schema references.
     */
    public void clear() {
        messageDescriptors.clear();
        fileDescriptors.clear();
        lastSelectedSchema = null;

        // Clear message-specific schema cache when schemas are reloaded
        if (messageSchemaCache != null) {
            messageSchemaCache.clear();
        }

        api.logging().logToOutput("Cleared all proto schemas and message cache");
    }

    /**
     * Get statistics about loaded schemas.
     */
    public String getStats() {
        return String.format("Loaded: %d files, %d message types",
                           fileDescriptors.size(),
                           messageDescriptors.size());
    }

    /**
     * Require that schemas are loaded, throw exception if not.
     * @throws IllegalStateException if no schemas are loaded
     */
    public void requireSchemasLoaded() {
        if (!hasSchemasLoaded()) {
            throw new IllegalStateException("No .proto schemas loaded. Load schemas via ProtoSocket Settings tab.");
        }
    }

    /**
     * Get the last selected schema name (for auto-apply feature).
     */
    public String getLastSelectedSchema() {
        return lastSelectedSchema;
    }

    /**
     * Set the last selected schema name (for auto-apply feature).
     */
    public void setLastSelectedSchema(String schemaName) {
        this.lastSelectedSchema = schemaName;
        api.logging().logToOutput("Remembered schema for auto-apply: " + schemaName);
    }
}
