package protosocket.ui;

import burp.api.montoya.MontoyaApi;
import protosocket.core.ProtobufDecoder;
import protosocket.core.ProtobufField;
import protosocket.util.ProtobufFormatter;
import com.google.protobuf.Descriptors;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * UI panel for displaying and editing decoded protobuf messages.
 * Provides a JSON view with syntax highlighting.
 * Schema selection is required before decoding.
 */
public class DecodedMessagePanel extends JPanel {

    /**
     * Listener for schema selection events.
     */
    public interface SchemaSelectionListener {
        void onSchemaSelected(String messageType);
    }
    private final MontoyaApi api;
    private final ProtobufDecoder decoder;
    private final RSyntaxTextArea jsonEditor;
    private final JTextField searchField;
    private final JComboBox<String> rootMessageTypeComboBox;
    private List<ProtobufField> currentFields;
    private Descriptors.Descriptor currentDescriptor;
    private byte[] currentBinaryData; // Store raw data for re-decoding when schema selected
    private boolean modified = false;
    private boolean updating = false;
    private boolean updatingDropdown = false; // Flag to prevent listener trigger during programmatic updates
    private SchemaSelectionListener schemaSelectionListener;

    public DecodedMessagePanel(MontoyaApi api, ProtobufDecoder decoder) {
        this.api = api;
        this.decoder = decoder;

        setLayout(new BorderLayout());

        // Create toolbar panel with message type and search
        JPanel toolbarPanel = new JPanel();
        toolbarPanel.setLayout(new BoxLayout(toolbarPanel, BoxLayout.Y_AXIS));

        // Root message type panel
        JPanel messageTypePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        messageTypePanel.add(new JLabel("Root Message Type:"));

        rootMessageTypeComboBox = new JComboBox<>();
        rootMessageTypeComboBox.setPreferredSize(new Dimension(250, 25));
        rootMessageTypeComboBox.setEditable(false);
        rootMessageTypeComboBox.setToolTipText("Select message type to decode (schema required)");

        // Add listener for schema selection - triggers decoding
        rootMessageTypeComboBox.addActionListener(e -> {
            // Don't trigger listener during programmatic updates (auto-apply/cache restore)
            if (updatingDropdown) {
                return;
            }

            String selected = getRootMessageType();
            if (selected != null && !selected.isEmpty() && schemaSelectionListener != null) {
                schemaSelectionListener.onSchemaSelected(selected);
            }
        });

        messageTypePanel.add(rootMessageTypeComboBox);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.setToolTipText("Reload available message types from loaded schemas");
        refreshButton.addActionListener(e -> refreshMessageTypes());
        messageTypePanel.add(refreshButton);

        toolbarPanel.add(messageTypePanel);

        // Create search panel
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.add(new JLabel("Search:"));

        searchField = new JTextField(20);
        searchField.addActionListener(e -> findNext());
        searchPanel.add(searchField);

        JButton findNextButton = new JButton("Find Next");
        findNextButton.addActionListener(e -> findNext());
        searchPanel.add(findNextButton);

        JButton findPrevButton = new JButton("Find Previous");
        findPrevButton.addActionListener(e -> findPrevious());
        searchPanel.add(findPrevButton);

        toolbarPanel.add(searchPanel);

        add(toolbarPanel, BorderLayout.NORTH);

        // Create RSyntaxTextArea with JSON syntax highlighting
        jsonEditor = new RSyntaxTextArea();
        jsonEditor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
        jsonEditor.setCodeFoldingEnabled(true);
        jsonEditor.setAntiAliasingEnabled(true);
        jsonEditor.setEditable(true);
        jsonEditor.setTabSize(2);
        jsonEditor.setAutoIndentEnabled(true);
        jsonEditor.setLineWrap(false);  // CRITICAL: Disable line wrapping to show proper JSON structure
        jsonEditor.setWrapStyleWord(false);

        // Apply dark theme to match Burp Suite
        try {
            Theme theme = Theme.load(getClass().getResourceAsStream(
                "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"));
            theme.apply(jsonEditor);
        } catch (IOException | NullPointerException e) {
            // If theme loading fails, use default
            api.logging().logToError("Could not load dark theme: " + e.getMessage());
        }

        // Track modifications
        jsonEditor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { markModified(); }

            @Override
            public void removeUpdate(DocumentEvent e) { markModified(); }

            @Override
            public void changedUpdate(DocumentEvent e) { markModified(); }

            private void markModified() {
                if (!updating) {
                    modified = true;
                }
            }
        });

        // Use RTextScrollPane for better integration
        RTextScrollPane scrollPane = new RTextScrollPane(jsonEditor);
        scrollPane.setLineNumbersEnabled(true);
        scrollPane.setFoldIndicatorEnabled(true);

        add(scrollPane, BorderLayout.CENTER);

        // Add info panel at the bottom
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JLabel infoLabel = new JLabel("<html><i>Tip: Select a Root Message Type from the dropdown above to decode and edit the message. " +
            "Load schemas via ProtoSocket Settings tab if dropdown is empty.</i></html>");
        infoLabel.setFont(new Font("Dialog", Font.PLAIN, 10));
        infoPanel.add(infoLabel, BorderLayout.WEST);
        add(infoPanel, BorderLayout.SOUTH);

        // Populate initial dropdown
        refreshMessageTypes();
    }

    /**
     * Set decoded fields to display.
     */
    public void setDecodedFields(List<ProtobufField> fields, Descriptors.Descriptor descriptor) {
        this.currentFields = fields;
        this.currentDescriptor = descriptor;
        updateView();
        modified = false;
    }

    /**
     * Update the JSON view with current fields.
     */
    private void updateView() {
        if (currentFields == null || currentFields.isEmpty()) {
            jsonEditor.setText("No protobuf data");
            jsonEditor.setEditable(true);
            return;
        }

        // CRITICAL: Set editable BEFORE any operations that might fail
        // This ensures editor stays editable even if formatting throws exception
        api.logging().logToOutput("updateView: Setting editor editable=true (valid data)");
        jsonEditor.setEditable(true);

        try {
            updating = true;
            String json = ProtobufFormatter.toJson(currentFields);
            jsonEditor.setText(json);
            jsonEditor.setCaretPosition(0);
            api.logging().logToOutput("updateView: Successfully formatted " + currentFields.size() + " fields");
        } catch (Exception e) {
            jsonEditor.setText("Error formatting protobuf: " + e.getMessage());
            api.logging().logToError("updateView: Error formatting protobuf: " + e.getMessage());
            // Note: Editor remains editable even on error, allowing user to see/fix
        } finally {
            updating = false;
        }
    }

    /**
     * Get modified fields from the JSON view.
     */
    public List<ProtobufField> getModifiedFields() {
        return getModifiedFieldsWithDescriptor(currentDescriptor);
    }

    /**
     * Get modified fields from the JSON view with explicit descriptor.
     * This allows re-encoding with a different message type than was used for decoding.
     */
    public List<ProtobufField> getModifiedFieldsWithDescriptor(Descriptors.Descriptor descriptor) {
        if (!modified) {
            api.logging().logToOutput("No modifications detected, returning original fields");
            return currentFields;
        }

        try {
            String json = jsonEditor.getText();
            api.logging().logToOutput("--- Parsing Modified JSON ---");
            api.logging().logToOutput("Descriptor: " + (descriptor != null ? descriptor.getFullName() : "null (schemaless)"));
            api.logging().logToOutput("JSON length: " + json.length() + " chars");
            api.logging().logToOutput("JSON preview: " + (json.length() > 200 ? json.substring(0, 200) + "..." : json));

            List<ProtobufField> result = ProtobufFormatter.fromJson(json, descriptor);

            api.logging().logToOutput("✓ Parsed " + result.size() + " root-level fields from JSON");

            // Log summary of parsed fields
            for (ProtobufField field : result) {
                api.logging().logToOutput("  Field #" + field.getFieldNumber() +
                    " (wire_type=" + field.getWireType() +
                    ", hasNested=" + field.hasNestedFields() + ")");
            }

            return result;
        } catch (Exception e) {
            api.logging().logToError("✗ Error parsing modified JSON: " + e.getMessage());
            e.printStackTrace();
            // Return original fields if parsing fails
            return currentFields;
        }
    }

    /**
     * Check if the content has been modified.
     */
    public boolean isModified() {
        return modified;
    }

    /**
     * Set error message in the view.
     * Keep editor editable to prevent locking issues.
     */
    public void setError(String errorMessage) {
        api.logging().logToOutput("setError: Showing error (keeping editor editable): " + errorMessage);
        jsonEditor.setText("Error: " + errorMessage);
        // Keep editor editable to prevent lock-up issues
        // Users can still view and potentially edit the error message
        jsonEditor.setEditable(true);
    }

    /**
     * Clear the view.
     */
    public void clear() {
        jsonEditor.setText("");
        currentFields = null;
        currentDescriptor = null;
        modified = false;
    }

    /**
     * Get the root message type specified by the user.
     * Returns null if no type was selected.
     */
    public String getRootMessageType() {
        Object selected = rootMessageTypeComboBox.getSelectedItem();
        if (selected == null || selected.toString().startsWith("<")) {
            // Null or placeholder text like "<Select schema...>"
            return null;
        }
        return selected.toString();
    }

    /**
     * Set the root message type field without triggering the selection listener.
     * This is used when programmatically setting the schema (auto-apply or cache restore).
     */
    public void setRootMessageType(String messageType) {
        if (messageType != null && !messageType.isEmpty()) {
            try {
                updatingDropdown = true;
                rootMessageTypeComboBox.setSelectedItem(messageType);
            } finally {
                updatingDropdown = false;
            }
        }
    }

    /**
     * Refresh the dropdown with available message types from loaded schemas.
     * Filters to show only simple names (not fully qualified duplicates).
     */
    public void refreshMessageTypes() {
        if (decoder == null) {
            api.logging().logToError("Cannot refresh message types: decoder not available");
            return;
        }

        Set<String> allNames = decoder.getSchemaManager().getAllMessageTypeNames();

        // Filter to simple names only (no dots in name)
        Set<String> simpleNames = new TreeSet<>();  // TreeSet for alphabetical sorting
        for (String name : allNames) {
            if (!name.contains(".")) {
                simpleNames.add(name);
            }
        }

        // Clear and repopulate
        rootMessageTypeComboBox.removeAllItems();

        if (simpleNames.isEmpty()) {
            rootMessageTypeComboBox.addItem("<No schemas loaded>");
        } else {
            rootMessageTypeComboBox.addItem("<Select schema to decode>");
            for (String name : simpleNames) {
                rootMessageTypeComboBox.addItem(name);
            }
        }

        api.logging().logToOutput("Refreshed message types: " + simpleNames.size() + " types available");
    }

    /**
     * Set whether the view is editable.
     */
    public void setEditable(boolean editable) {
        api.logging().logToOutput("DecodedMessagePanel.setEditable(" + editable + ") called");
        jsonEditor.setEditable(editable);
    }

    /**
     * Set the schema selection listener.
     */
    public void setSchemaSelectionListener(SchemaSelectionListener listener) {
        this.schemaSelectionListener = listener;
    }

    /**
     * Show placeholder text prompting user to select a schema.
     * Called when a message is intercepted but not yet decoded.
     */
    public void showSchemaSelectionPrompt(byte[] binaryData) {
        this.currentBinaryData = binaryData;
        this.currentFields = null;
        this.currentDescriptor = null;
        this.modified = false;

        updating = true;
        try {
            if (decoder.getSchemaManager().hasSchemasLoaded()) {
                jsonEditor.setText("Select a message type from the dropdown above to decode this protobuf message.\n\n" +
                    "Binary data size: " + binaryData.length + " bytes");
            } else {
                jsonEditor.setText("No schemas loaded.\n\n" +
                    "Load .proto files via ProtoSocket Settings tab, then select a message type from the dropdown above.");
            }
            jsonEditor.setEditable(false);
        } finally {
            updating = false;
        }
    }

    /**
     * Find next occurrence of search text.
     */
    private void findNext() {
        String searchText = searchField.getText();
        if (searchText.isEmpty()) {
            return;
        }

        String content = jsonEditor.getText().toLowerCase();
        String search = searchText.toLowerCase();

        int start = jsonEditor.getCaretPosition();
        int index = content.indexOf(search, start);

        if (index == -1) {
            index = content.indexOf(search, 0);
        }

        if (index != -1) {
            jsonEditor.setCaretPosition(index);
            jsonEditor.select(index, index + searchText.length());
            searchField.setBackground(Color.WHITE);
        } else {
            searchField.setBackground(new Color(255, 200, 200));
        }
    }

    /**
     * Find previous occurrence of search text.
     */
    private void findPrevious() {
        String searchText = searchField.getText();
        if (searchText.isEmpty()) {
            return;
        }

        String content = jsonEditor.getText().toLowerCase();
        String search = searchText.toLowerCase();

        int start = Math.max(0, jsonEditor.getCaretPosition() - searchText.length() - 1);
        int index = content.lastIndexOf(search, start);

        if (index == -1) {
            index = content.lastIndexOf(search);
        }

        if (index != -1) {
            jsonEditor.setCaretPosition(index);
            jsonEditor.select(index, index + searchText.length());
            searchField.setBackground(Color.WHITE);
        } else {
            searchField.setBackground(new Color(255, 200, 200));
        }
    }

    /**
     * Cleanup resources when the panel is disposed.
     * This is called when the editor is closed or the extension is unloaded.
     */
    public void cleanup() {
        // Clear the editor content to release memory
        if (jsonEditor != null) {
            jsonEditor.setText("");
            jsonEditor.discardAllEdits();
        }

        // Clear references
        currentFields = null;
        currentDescriptor = null;
        currentBinaryData = null;
        schemaSelectionListener = null;

        // Reset flags
        modified = false;
        updating = false;
        updatingDropdown = false;

        api.logging().logToOutput("DecodedMessagePanel: Cleaned up resources");
    }
}
