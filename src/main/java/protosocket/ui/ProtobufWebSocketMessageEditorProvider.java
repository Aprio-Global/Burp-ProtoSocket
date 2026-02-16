package protosocket.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedWebSocketMessageEditor;
import burp.api.montoya.ui.editor.extension.WebSocketMessageEditorProvider;
import protosocket.cache.MessageSchemaCache;
import protosocket.core.ProtobufDecoder;
import protosocket.util.ProtobufFileLogger;

/**
 * Provider that creates ProtobufWebSocketMessageEditor instances for the Inspector pane.
 * This makes the "Protobuf" tab appear when viewing WebSocket messages in Proxy.
 * Creates separate editor instances for each context (history vs send) to maintain independent state.
 */
public class ProtobufWebSocketMessageEditorProvider implements WebSocketMessageEditorProvider {
    private final MontoyaApi api;
    private final ProtobufDecoder decoder;
    private final ProtobufFileLogger fileLogger;
    private final MessageSchemaCache schemaCache;

    public ProtobufWebSocketMessageEditorProvider(MontoyaApi api, ProtobufDecoder decoder,
                                                 ProtobufFileLogger fileLogger,
                                                 MessageSchemaCache schemaCache) {
        this.api = api;
        this.decoder = decoder;
        this.fileLogger = fileLogger;
        this.schemaCache = schemaCache;
    }

    @Override
    public ExtensionProvidedWebSocketMessageEditor provideMessageEditor(EditorCreationContext creationContext) {
        // Create a new editor instance for each context (history vs send) to maintain independent state
        // Each instance will use the shared MessageSchemaCache to remember per-message schema selections
        // NOTE: Always keep WebSocket editors editable - Burp's EditorMode doesn't properly handle
        // WebSocket intercept scenarios where editing is needed
        ProtobufWebSocketMessageEditor editor = new ProtobufWebSocketMessageEditor(api, decoder, fileLogger, schemaCache);
        editor.getPanel().setEditable(true);

        api.logging().logToOutput("Created new ProtobufWebSocketMessageEditor instance for context: " +
                creationContext.editorMode());

        return editor;
    }
}
