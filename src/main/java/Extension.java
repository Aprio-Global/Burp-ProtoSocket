import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import protosocket.cache.MessageSchemaCache;
import protosocket.core.ProtobufDecoder;
import protosocket.core.WebSocketMessageStore;
import protosocket.handler.ProtoWebSocketCreationHandler;
import protosocket.schema.ProtoFileParser;
import protosocket.schema.ProtoSchemaManager;
import protosocket.ui.ProtoSchemaSettingsPanel;
import protosocket.ui.ProtobufWebSocketMessageEditorProvider;
import protosocket.util.ProtobufFileLogger;
import protosocket.util.ThreadManager;

public class Extension implements BurpExtension {
    private ThreadManager threadManager;

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("ProtoSocket");

        threadManager = new ThreadManager(api);
        ProtoSchemaManager schemaManager = new ProtoSchemaManager(api);
        ProtoFileParser protoParser = new ProtoFileParser(api, schemaManager);
        ProtobufDecoder decoder = new ProtobufDecoder(api, schemaManager);
        WebSocketMessageStore messageStore = new WebSocketMessageStore(api);
        ProtobufFileLogger fileLogger = new ProtobufFileLogger(api);

        // Create message schema cache for per-message schema persistence
        MessageSchemaCache messageSchemaCache = new MessageSchemaCache(api);

        // Wire the cache to the schema manager so it can be cleared on schema reload
        schemaManager.setMessageSchemaCache(messageSchemaCache);

        ProtoWebSocketCreationHandler wsHandler = new ProtoWebSocketCreationHandler(
                api,
                messageStore,
                decoder,
                threadManager
        );
        api.proxy().registerWebSocketCreationHandler(wsHandler);

        // Register custom WebSocket message editor (appears in Inspector pane)
        // Pass the cache so each editor instance can use it for per-message schema persistence
        ProtobufWebSocketMessageEditorProvider wsEditorProvider =
            new ProtobufWebSocketMessageEditorProvider(api, decoder, fileLogger, messageSchemaCache);
        api.userInterface().registerWebSocketMessageEditorProvider(wsEditorProvider);

        ProtoSchemaSettingsPanel settingsPanel = new ProtoSchemaSettingsPanel(api, schemaManager, protoParser);
        api.userInterface().registerSuiteTab("ProtoSocket Settings", settingsPanel);

        // Register unload handler for clean shutdown
        api.extension().registerUnloadingHandler(() -> {
            api.logging().logToOutput("Unloading ProtoSocket extension...");
            threadManager.shutdown();
            api.logging().logToOutput("ProtoSocket extension unloaded successfully");
        });

        // Log successful initialization
        api.logging().logToOutput("════════════════════════════════════════════════════════");
        api.logging().logToOutput("ProtoSocket - Schema-Required Mode");
        api.logging().logToOutput("════════════════════════════════════════════════════════");
        api.logging().logToOutput("");
        api.logging().logToOutput("IMPORTANT: Schemas are REQUIRED for all operations.");
        api.logging().logToOutput("");
        api.logging().logToOutput("To use this extension:");
        api.logging().logToOutput("  1. Go to 'ProtoSocket Settings' tab");
        api.logging().logToOutput("  2. Load your .proto files (supports imports)");
        api.logging().logToOutput("  3. Intercept protobuf traffic");
        api.logging().logToOutput("  4. Select schema from dropdown in Protobuf tab");
        api.logging().logToOutput("  5. Edit and forward messages");
        api.logging().logToOutput("");
        api.logging().logToOutput("Without schemas loaded, this extension will not function.");
        api.logging().logToOutput("════════════════════════════════════════════════════════");
    }
}