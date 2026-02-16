package protosocket.handler;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.proxy.websocket.ProxyWebSocket;
import burp.api.montoya.proxy.websocket.ProxyWebSocketCreation;
import burp.api.montoya.proxy.websocket.ProxyWebSocketCreationHandler;
import protosocket.core.ProtobufDecoder;
import protosocket.core.WebSocketMessageStore;
import protosocket.util.ThreadManager;

/**
 * Handler invoked when a WebSocket connection is created in Burp Proxy.
 * Registers a message handler for each WebSocket to intercept binary protobuf messages.
 */
public class ProtoWebSocketCreationHandler implements ProxyWebSocketCreationHandler {
    private final MontoyaApi api;
    private final WebSocketMessageStore messageStore;
    private final ProtobufDecoder decoder;
    private final ThreadManager threadManager;

    public ProtoWebSocketCreationHandler(
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
    public void handleWebSocketCreation(ProxyWebSocketCreation proxyWebSocketCreation) {
        ProxyWebSocket proxyWebSocket = proxyWebSocketCreation.proxyWebSocket();

        // Log WebSocket creation
        api.logging().logToOutput("WebSocket created: " + proxyWebSocketCreation.upgradeRequest().url());

        // Register message handler for this WebSocket
        ProtoMessageHandler messageHandler = new ProtoMessageHandler(
                api,
                messageStore,
                decoder,
                threadManager
        );

        proxyWebSocket.registerProxyMessageHandler(messageHandler);
    }
}
