package protosocket.handler;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.proxy.websocket.*;
import protosocket.core.ProtobufDecoder;
import protosocket.core.ProtobufField;
import protosocket.core.WebSocketMessageStore;
import protosocket.util.ProtobufFormatter;
import protosocket.util.ThreadManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles individual WebSocket messages, intercepting binary frames to decode protobuf.
 */
public class ProtoMessageHandler implements ProxyMessageHandler {
    private final MontoyaApi api;
    private final WebSocketMessageStore messageStore;
    private final ProtobufDecoder decoder;
    private final ThreadManager threadManager;

    // Track messages that have been modified by the user (for re-encoding)
    private final Map<String, List<ProtobufField>> modifiedMessages = new ConcurrentHashMap<>();

    public ProtoMessageHandler(
            MontoyaApi api,
            WebSocketMessageStore messageStore,
            ProtobufDecoder decoder,
            ThreadManager threadManager) {
        this.api = api;
        this.messageStore = messageStore;
        this.decoder = decoder;
        this.threadManager = threadManager;
    }

    @Override
    public BinaryMessageReceivedAction handleBinaryMessageReceived(InterceptedBinaryMessage message) {
        byte[] payload = message.payload().getBytes();

        // Store the message
        String messageId = messageStore.storeMessage(payload, message.direction());

        // Validate if message is protobuf
        threadManager.submit(() -> {
            try {
                if (decoder.isValidProtobuf(payload)) {
                    api.logging().logToOutput("Protobuf message detected [" + message.direction() + "] - " +
                        "Select schema in Protobuf tab to decode");
                } else {
                    api.logging().logToOutput("Binary message is not valid protobuf (skipping)");
                }
            } catch (Exception e) {
                api.logging().logToError("Failed to validate protobuf: " + e.getMessage());
            }
        });

        // Pass through the original message (no modification by default)
        return BinaryMessageReceivedAction.continueWith(message);
    }

    @Override
    public BinaryMessageToBeSentAction handleBinaryMessageToBeSent(InterceptedBinaryMessage message) {
        byte[] payload = message.payload().getBytes();

        // Store the message
        String messageId = messageStore.storeMessage(payload, message.direction());

        // Check if user has modified this message
        // (This will be implemented when we add the UI editing capability)

        // Validate if message is protobuf
        threadManager.submit(() -> {
            try {
                if (decoder.isValidProtobuf(payload)) {
                    api.logging().logToOutput("Protobuf message detected [" + message.direction() + "] - " +
                        "Select schema in Protobuf tab to decode");
                }
            } catch (Exception e) {
                api.logging().logToError("Failed to validate protobuf: " + e.getMessage());
            }
        });

        // Pass through the original message
        return BinaryMessageToBeSentAction.continueWith(message);
    }

    @Override
    public TextMessageReceivedAction handleTextMessageReceived(InterceptedTextMessage message) {
        // Pass through text messages (we only handle binary protobuf)
        return TextMessageReceivedAction.continueWith(message);
    }

    @Override
    public TextMessageToBeSentAction handleTextMessageToBeSent(InterceptedTextMessage message) {
        // Pass through text messages
        return TextMessageToBeSentAction.continueWith(message);
    }

    @Override
    public void onClose() {
        // Cleanup when WebSocket closes
        api.logging().logToOutput("WebSocket closed");
    }

    /**
     * Mark a message as modified with new field data.
     * This will be used by the UI to trigger re-encoding.
     */
    public void markMessageModified(String messageId, List<ProtobufField> modifiedFields) {
        modifiedMessages.put(messageId, modifiedFields);
    }

    /**
     * Check if a message has been modified.
     */
    public boolean isMessageModified(String messageId) {
        return modifiedMessages.containsKey(messageId);
    }

    /**
     * Get modified fields for a message.
     */
    public List<ProtobufField> getModifiedFields(String messageId) {
        return modifiedMessages.get(messageId);
    }
}
