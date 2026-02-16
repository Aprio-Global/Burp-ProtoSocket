package protosocket.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.contextmenu.WebSocketMessage;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedWebSocketMessageEditor;
import protosocket.cache.MessageSchemaCache;
import protosocket.core.ProtobufDecoder;
import protosocket.core.ProtobufField;
import protosocket.util.ProtobufFileLogger;
import protosocket.util.ProtobufFormatter;
import com.google.protobuf.Descriptors;

import java.awt.*;
import java.util.List;

/**
 * Custom WebSocket message editor for the Inspector pane.
 * Displays decoded protobuf in a "Protobuf" tab when viewing WebSocket messages.
 */
public class ProtobufWebSocketMessageEditor implements ExtensionProvidedWebSocketMessageEditor {
    private final MontoyaApi api;
    private final ProtobufDecoder decoder;
    private final DecodedMessagePanel panel;
    private final ProtobufFileLogger fileLogger;
    private final MessageSchemaCache schemaCache;
    private WebSocketMessage currentMessage;
    private byte[] currentBinaryData;
    private Descriptors.Descriptor currentDescriptor;

    public ProtobufWebSocketMessageEditor(MontoyaApi api, ProtobufDecoder decoder,
                                         ProtobufFileLogger fileLogger,
                                         MessageSchemaCache schemaCache) {
        this.api = api;
        this.decoder = decoder;
        this.fileLogger = fileLogger;
        this.schemaCache = schemaCache;
        this.panel = new DecodedMessagePanel(api, decoder);

        // Set schema selection listener to handle decode on selection
        panel.setSchemaSelectionListener(this::onSchemaSelected);
    }

    /**
     * Get the decoded message panel (for configuring editability).
     */
    public DecodedMessagePanel getPanel() {
        return panel;
    }

    @Override
    public ByteArray getMessage() {
        // If user modified the protobuf, re-encode it
        if (panel.isModified()) {
            // Descriptor is required for encoding
            if (currentDescriptor == null) {
                api.logging().logToError("Cannot encode without schema. Returning original binary data.");
                return ByteArray.byteArray(currentBinaryData);
            }

            try {
                api.logging().logToOutput("=== ENCODING WEBSOCKET MESSAGE ===");
                api.logging().logToOutput("Using schema: " + currentDescriptor.getFullName());

                // Pass the descriptor to the panel for schema-aware encoding
                List<ProtobufField> modifiedFields = panel.getModifiedFieldsWithDescriptor(currentDescriptor);
                byte[] encoded = decoder.encode(modifiedFields);

                // Log the final binary output that will be sent
                api.logging().logToOutput("\n" + "=".repeat(70));
                api.logging().logToOutput("║ FINAL WEBSOCKET MESSAGE TO BE SENT TO SERVER");
                api.logging().logToOutput("=".repeat(70));
                api.logging().logToOutput("Message ID: WS-" + System.currentTimeMillis());
                api.logging().logToOutput("Size: " + encoded.length + " bytes");
                api.logging().logToOutput("Modified: YES (user edited in Protobuf tab)");
                api.logging().logToOutput("Schema: " + currentDescriptor.getFullName());
                api.logging().logToOutput("");
                api.logging().logToOutput("--- Base64 (for copy/paste) ---");
                api.logging().logToOutput(java.util.Base64.getEncoder().encodeToString(encoded));
                api.logging().logToOutput("");
                api.logging().logToOutput("--- Hex Dump ---");
                if (encoded.length > 2048) {
                    // For large messages, show first 1KB only
                    byte[] preview = java.util.Arrays.copyOf(encoded, 1024);
                    api.logging().logToOutput(ProtobufFormatter.formatHexDump(preview));
                    api.logging().logToOutput("... (" + (encoded.length - 1024) + " more bytes, total " + encoded.length + " bytes)");
                } else {
                    api.logging().logToOutput(ProtobufFormatter.formatHexDump(encoded));
                }
                api.logging().logToOutput("");
                api.logging().logToOutput("--- Decoded JSON (from modified data) ---");
                try {
                    List<ProtobufField> decodedBack = decoder.decode(encoded, currentDescriptor.getName(), currentDescriptor);
                    String jsonVerify = ProtobufFormatter.toJson(decodedBack);
                    api.logging().logToOutput(jsonVerify);
                } catch (Exception ex) {
                    api.logging().logToError("Could not decode encoded data back to JSON: " + ex.getMessage());
                }
                api.logging().logToOutput("=".repeat(70) + "\n");

                // Log to file for persistent debugging
                if (fileLogger != null && fileLogger.isEnabled()) {
                    fileLogger.logModifiedMessage(
                        currentBinaryData,  // original
                        encoded,            // modified
                        modifiedFields,
                        currentMessage.direction().toString(),
                        currentDescriptor.getName()
                    );
                }

                return ByteArray.byteArray(encoded);
            } catch (Exception e) {
                api.logging().logToError("Error re-encoding protobuf: " + e.getMessage());
                e.printStackTrace();
                // Return original if encoding fails
                return ByteArray.byteArray(currentBinaryData);
            }
        }

        return ByteArray.byteArray(currentBinaryData);
    }

    @Override
    public void setMessage(WebSocketMessage message) {
        this.currentMessage = message;

        // CRITICAL: Always reset editable state when new message arrives
        // This prevents the editor from getting stuck in non-editable state
        // if the previous message had an error or decoding failure
        panel.setEditable(true);

        try {
            // Get binary payload from WebSocket message
            ByteArray payload = message.payload();
            byte[] data = payload.getBytes();

            if (data == null || data.length == 0) {
                panel.clear();
                return;
            }

            currentBinaryData = data;
            currentDescriptor = null; // Reset descriptor

            // Don't decode immediately - show schema selection prompt
            // User must select schema from dropdown to decode
            if (decoder.isValidProtobuf(data)) {
                panel.showSchemaSelectionPrompt(data);

                // Priority 1: Check if this specific message has a cached schema selection
                String cachedSchema = schemaCache.getSchemaForMessage(data);
                if (cachedSchema != null && decoder.getSchemaManager().getMessageDescriptor(cachedSchema) != null) {
                    api.logging().logToOutput("Restoring cached schema for this message: " + cachedSchema);
                    onSchemaSelected(cachedSchema);
                    return; // Early return - we've restored the message-specific schema
                }

                // Priority 2: Fall back to auto-apply using the global last selected schema
                String lastSchema = decoder.getSchemaManager().getLastSelectedSchema();
                if (lastSchema != null && !lastSchema.isEmpty()) {
                    // Verify schema still exists
                    if (decoder.getSchemaManager().getMessageDescriptor(lastSchema) != null) {
                        api.logging().logToOutput("Auto-applying last selected schema: " + lastSchema);
                        onSchemaSelected(lastSchema);
                    } else {
                        api.logging().logToOutput("Last selected schema '" + lastSchema + "' no longer available");
                    }
                }
            } else {
                panel.setError("Not valid protobuf data");
            }
        } catch (Exception e) {
            panel.setError("Error: " + e.getMessage());
            api.logging().logToError("Error in setMessage: " + e.getMessage());
        }
    }

    /**
     * Called when user selects a schema from the dropdown.
     * Decodes the current binary data with the selected schema.
     */
    private void onSchemaSelected(String messageType) {
        if (currentBinaryData == null) {
            panel.setError("No binary data to decode");
            return;
        }

        try {
            api.logging().logToOutput("Schema selected: " + messageType);

            // Look up descriptor
            Descriptors.Descriptor descriptor = decoder.getSchemaManager().getMessageDescriptor(messageType);
            if (descriptor == null) {
                panel.setError("Schema not found: " + messageType);
                return;
            }

            // Decode with schema
            List<ProtobufField> decoded = decoder.decode(currentBinaryData, messageType, descriptor);

            // Store descriptor for encoding later
            currentDescriptor = descriptor;

            // Display decoded fields
            panel.setDecodedFields(decoded, descriptor);

            // Update the dropdown UI to show the selected schema
            // This is important for auto-apply and cache restore scenarios
            panel.setRootMessageType(messageType);

            // Cache this schema selection for this specific message
            schemaCache.setSchemaForMessage(currentBinaryData, messageType);

            // Also remember this schema globally for auto-apply to new messages
            decoder.getSchemaManager().setLastSelectedSchema(messageType);

        } catch (Exception e) {
            panel.setError("Failed to decode with schema " + messageType + ": " + e.getMessage());
            api.logging().logToError("Decode error: " + e.getMessage());
        }
    }

    @Override
    public boolean isEnabledFor(WebSocketMessage message) {
        try {
            ByteArray payload = message.payload();
            byte[] data = payload.getBytes();

            if (data == null || data.length == 0) {
                return false;
            }

            // Only enable if data is valid protobuf
            return decoder.isValidProtobuf(data);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String caption() {
        return "Protobuf";
    }

    @Override
    public Component uiComponent() {
        return panel;
    }

    @Override
    public Selection selectedData() {
        // Return null for now (text selection not implemented)
        return null;
    }

    @Override
    public boolean isModified() {
        return panel.isModified();
    }

    /**
     * Cleanup resources when the editor is disposed.
     * Called during extension unload or when the editor is closed.
     */
    public void cleanup() {
        if (panel != null) {
            panel.cleanup();
        }
    }
}
