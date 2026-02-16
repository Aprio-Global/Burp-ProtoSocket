package protosocket.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.EditorMode;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpResponseEditor;
import burp.api.montoya.ui.editor.extension.HttpRequestEditorProvider;
import burp.api.montoya.ui.editor.extension.HttpResponseEditorProvider;
import protosocket.core.ProtobufDecoder;
import protosocket.core.WebSocketMessageStore;

/**
 * Provider that creates ProtobufMessageEditor instances for Burp's message viewer.
 * Registers both request and response editors.
 */
public class ProtobufEditorProvider implements HttpRequestEditorProvider, HttpResponseEditorProvider {
    private final MontoyaApi api;
    private final WebSocketMessageStore messageStore;
    private final ProtobufDecoder decoder;

    public ProtobufEditorProvider(MontoyaApi api, WebSocketMessageStore messageStore, ProtobufDecoder decoder) {
        this.api = api;
        this.messageStore = messageStore;
        this.decoder = decoder;
    }

    @Override
    public ExtensionProvidedHttpRequestEditor provideHttpRequestEditor(EditorCreationContext creationContext) {
        // Create a new editor instance for requests
        ProtobufMessageEditor editor = new ProtobufMessageEditor(api, messageStore, decoder, true);

        // Configure editability based on context (e.g., Proxy history is read-only, Repeater is editable)
        if (creationContext.editorMode() == EditorMode.READ_ONLY) {
            editor.getPanel().setEditable(false);
        }

        return editor;
    }

    @Override
    public ExtensionProvidedHttpResponseEditor provideHttpResponseEditor(EditorCreationContext creationContext) {
        // Create a new editor instance for responses
        ProtobufMessageEditor editor = new ProtobufMessageEditor(api, messageStore, decoder, false);

        // Configure editability based on context (e.g., Proxy history is read-only, Repeater is editable)
        if (creationContext.editorMode() == EditorMode.READ_ONLY) {
            editor.getPanel().setEditable(false);
        }

        return editor;
    }
}
