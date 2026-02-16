package protosocket.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpResponseEditor;
import protosocket.core.ProtobufDecoder;
import protosocket.core.ProtobufField;
import protosocket.core.WebSocketMessageStore;
import protosocket.util.ProtobufFormatter;
import com.google.protobuf.Descriptors;

import java.awt.*;
import java.util.List;

/**
 * Custom message editor that displays decoded protobuf in a "Protobuf" tab.
 * Implements both request and response editor interfaces.
 */
public class ProtobufMessageEditor implements ExtensionProvidedHttpRequestEditor,
                                               ExtensionProvidedHttpResponseEditor {
    private final MontoyaApi api;
    private final WebSocketMessageStore messageStore;
    private final ProtobufDecoder decoder;
    private final DecodedMessagePanel panel;

    private byte[] currentMessage;
    private boolean isRequest;
    private Descriptors.Descriptor currentDescriptor;

    public ProtobufMessageEditor(MontoyaApi api, WebSocketMessageStore messageStore, ProtobufDecoder decoder, boolean isRequest) {
        this.api = api;
        this.messageStore = messageStore;
        this.decoder = decoder;
        this.isRequest = isRequest;
        this.panel = new DecodedMessagePanel(api, decoder);

        // Set schema selection listener
        panel.setSchemaSelectionListener(this::onSchemaSelected);
    }

    /**
     * Get the decoded message panel (for configuring editability).
     */
    public DecodedMessagePanel getPanel() {
        return panel;
    }

    @Override
    public HttpRequest getRequest() {
        if (!isRequest) {
            return null;
        }

        // If user modified the protobuf, re-encode it
        if (panel.isModified()) {
            // Descriptor is required for encoding
            if (currentDescriptor == null) {
                api.logging().logToError("Cannot encode without schema. Returning original data.");
                return HttpRequest.httpRequest(ByteArray.byteArray(currentMessage));
            }

            try {
                api.logging().logToOutput("=== ENCODING HTTP REQUEST ===");
                api.logging().logToOutput("Using schema: " + currentDescriptor.getFullName());

                List<ProtobufField> modifiedFields = panel.getModifiedFieldsWithDescriptor(currentDescriptor);
                byte[] encoded = decoder.encode(modifiedFields);

                // Log the final binary output that will be sent
                api.logging().logToOutput("\n" + "=".repeat(70));
                api.logging().logToOutput("║ FINAL HTTP REQUEST BODY TO BE SENT");
                api.logging().logToOutput("=".repeat(70));
                api.logging().logToOutput("Size: " + encoded.length + " bytes");
                api.logging().logToOutput("Schema: " + currentDescriptor.getFullName());
                api.logging().logToOutput("");
                api.logging().logToOutput("--- Base64 ---");
                api.logging().logToOutput(java.util.Base64.getEncoder().encodeToString(encoded));
                api.logging().logToOutput("");
                api.logging().logToOutput("--- Hex Dump ---");
                api.logging().logToOutput(ProtobufFormatter.formatHexDump(encoded));
                api.logging().logToOutput("=".repeat(70) + "\n");

                return HttpRequest.httpRequest(ByteArray.byteArray(encoded));
            } catch (Exception e) {
                api.logging().logToError("Error re-encoding protobuf: " + e.getMessage());
                e.printStackTrace();
                // Return original if encoding fails
                return HttpRequest.httpRequest(ByteArray.byteArray(currentMessage));
            }
        }

        return HttpRequest.httpRequest(ByteArray.byteArray(currentMessage));
    }

    @Override
    public HttpResponse getResponse() {
        if (isRequest) {
            return null;
        }

        // If user modified the protobuf, re-encode it
        if (panel.isModified()) {
            // Descriptor is required for encoding
            if (currentDescriptor == null) {
                api.logging().logToError("Cannot encode without schema. Returning original data.");
                return HttpResponse.httpResponse(ByteArray.byteArray(currentMessage));
            }

            try {
                api.logging().logToOutput("=== ENCODING HTTP RESPONSE ===");
                api.logging().logToOutput("Using schema: " + currentDescriptor.getFullName());

                List<ProtobufField> modifiedFields = panel.getModifiedFieldsWithDescriptor(currentDescriptor);
                byte[] encoded = decoder.encode(modifiedFields);

                // Log the final binary output that will be sent
                api.logging().logToOutput("\n" + "=".repeat(70));
                api.logging().logToOutput("║ FINAL HTTP RESPONSE BODY TO BE SENT");
                api.logging().logToOutput("=".repeat(70));
                api.logging().logToOutput("Size: " + encoded.length + " bytes");
                api.logging().logToOutput("Schema: " + currentDescriptor.getFullName());
                api.logging().logToOutput("");
                api.logging().logToOutput("--- Base64 ---");
                api.logging().logToOutput(java.util.Base64.getEncoder().encodeToString(encoded));
                api.logging().logToOutput("");
                api.logging().logToOutput("--- Hex Dump ---");
                api.logging().logToOutput(ProtobufFormatter.formatHexDump(encoded));
                api.logging().logToOutput("=".repeat(70) + "\n");

                return HttpResponse.httpResponse(ByteArray.byteArray(encoded));
            } catch (Exception e) {
                api.logging().logToError("Error re-encoding protobuf: " + e.getMessage());
                e.printStackTrace();
                // Return original if encoding fails
                return HttpResponse.httpResponse(ByteArray.byteArray(currentMessage));
            }
        }

        return HttpResponse.httpResponse(ByteArray.byteArray(currentMessage));
    }

    @Override
    public void setRequestResponse(HttpRequestResponse requestResponse) {
        try {
            // Try to extract binary data from request or response
            byte[] data;
            if (isRequest && requestResponse.request() != null) {
                data = requestResponse.request().body().getBytes();
            } else if (!isRequest && requestResponse.response() != null) {
                data = requestResponse.response().body().getBytes();
            } else {
                panel.clear();
                return;
            }

            currentMessage = data;
            currentDescriptor = null; // Reset descriptor

            // Don't decode immediately - show schema selection prompt
            if (decoder.isValidProtobuf(data)) {
                panel.showSchemaSelectionPrompt(data);
            } else {
                panel.setError("Not valid protobuf data");
            }
        } catch (Exception e) {
            panel.setError("Error: " + e.getMessage());
            api.logging().logToError("Error in setRequestResponse: " + e.getMessage());
        }
    }

    /**
     * Called when user selects a schema from the dropdown.
     * Decodes the current binary data with the selected schema.
     */
    private void onSchemaSelected(String messageType) {
        if (currentMessage == null) {
            panel.setError("No data to decode");
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
            List<ProtobufField> decoded = decoder.decode(currentMessage, messageType, descriptor);

            // Store descriptor for encoding later
            currentDescriptor = descriptor;

            // Display decoded fields
            panel.setDecodedFields(decoded, descriptor);

        } catch (Exception e) {
            panel.setError("Failed to decode with schema " + messageType + ": " + e.getMessage());
            api.logging().logToError("Decode error: " + e.getMessage());
        }
    }

    @Override
    public boolean isEnabledFor(HttpRequestResponse requestResponse) {
        try {
            // Check if this contains valid protobuf data
            byte[] data;
            if (isRequest && requestResponse.request() != null) {
                data = requestResponse.request().body().getBytes();
            } else if (!isRequest && requestResponse.response() != null) {
                data = requestResponse.response().body().getBytes();
            } else {
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
    public boolean isModified() {
        return panel.isModified();
    }

    @Override
    public Selection selectedData() {
        // Return empty selection (text selection not implemented)
        return null;
    }
}
