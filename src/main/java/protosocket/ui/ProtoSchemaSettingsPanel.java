package protosocket.ui;

import burp.api.montoya.MontoyaApi;
import protosocket.schema.ProtoFileParser;
import protosocket.schema.ProtoSchemaManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Settings panel for managing .proto file schemas.
 * Allows users to load .proto files and view loaded schemas.
 */
public class ProtoSchemaSettingsPanel extends JPanel {
    private final MontoyaApi api;
    private final ProtoSchemaManager schemaManager;
    private final ProtoFileParser parser;

    private JTextArea statusArea;
    private JButton loadFileButton;
    private JButton loadDirectoryButton;
    private JButton clearButton;
    private JLabel statsLabel;

    public ProtoSchemaSettingsPanel(MontoyaApi api, ProtoSchemaManager schemaManager, ProtoFileParser parser) {
        this.api = api;
        this.schemaManager = schemaManager;
        this.parser = parser;

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Create UI components
        createNorthPanel();
        createCenterPanel();
        createSouthPanel();

        updateStats();
    }

    private void createNorthPanel() {
        JPanel northPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JLabel titleLabel = new JLabel("Proto Schema Management");
        titleLabel.setFont(new Font("Dialog", Font.BOLD, 14));
        northPanel.add(titleLabel);

        add(northPanel, BorderLayout.NORTH);
    }

    private void createCenterPanel() {
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));

        // Buttons panel
        JPanel buttonsPanel = new JPanel(new GridLayout(3, 1, 5, 5));

        loadFileButton = new JButton("Load .proto File");
        loadFileButton.addActionListener(e -> loadProtoFile());
        buttonsPanel.add(loadFileButton);

        loadDirectoryButton = new JButton("Load .proto Directory");
        loadDirectoryButton.addActionListener(e -> loadProtoDirectory());
        buttonsPanel.add(loadDirectoryButton);

        clearButton = new JButton("Clear All Schemas");
        clearButton.addActionListener(e -> clearSchemas());
        buttonsPanel.add(clearButton);

        centerPanel.add(buttonsPanel, BorderLayout.NORTH);

        // Status area
        statusArea = new JTextArea(15, 50);
        statusArea.setEditable(false);
        statusArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        statusArea.setText("No .proto files loaded.\n\n" +
                          "Load .proto files to enable schema-aware decoding with field names.\n" +
                          "The parser supports imports - just load the main .proto file.");

        JScrollPane scrollPane = new JScrollPane(statusArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Status"));
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);
    }

    private void createSouthPanel() {
        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        statsLabel = new JLabel("Schemas loaded: 0 files, 0 message types");
        southPanel.add(statsLabel);

        add(southPanel, BorderLayout.SOUTH);
    }

    private void loadProtoFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().endsWith(".proto");
            }

            @Override
            public String getDescription() {
                return "Protocol Buffer Files (*.proto)";
            }
        });

        int result = fileChooser.showOpenDialog(getParentFrame());
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            loadFile(selectedFile);
        }
    }

    private void loadProtoDirectory() {
        JFileChooser dirChooser = new JFileChooser();
        dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int result = dirChooser.showOpenDialog(getParentFrame());
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedDir = dirChooser.getSelectedFile();
            loadDirectory(selectedDir);
        }
    }

    private void loadFile(File file) {
        statusArea.append("\n\nLoading: " + file.getName() + "...\n");

        try {
            parser.parseProtoFile(file);
            statusArea.append("✓ Successfully loaded " + file.getName() + "\n");
            updateStats();

            // Show loaded message types
            statusArea.append("\nAvailable message types:\n");
            for (String typeName : schemaManager.getAllMessageTypeNames()) {
                statusArea.append("  - " + typeName + "\n");
            }

        } catch (Exception e) {
            statusArea.append("✗ Error: " + e.getMessage() + "\n");
            api.logging().logToError("Failed to load proto file: " + e.getMessage());
        }

        // Scroll to bottom
        statusArea.setCaretPosition(statusArea.getDocument().getLength());
    }

    private void loadDirectory(File directory) {
        statusArea.append("\n\nLoading all .proto files from: " + directory.getName() + "...\n");

        try {
            int loaded = parser.loadProtoDirectory(directory.getAbsolutePath());
            statusArea.append("✓ Successfully loaded " + loaded + " .proto files\n");
            updateStats();

            // Show loaded message types
            statusArea.append("\nAvailable message types:\n");
            for (String typeName : schemaManager.getAllMessageTypeNames()) {
                statusArea.append("  - " + typeName + "\n");
            }

        } catch (Exception e) {
            statusArea.append("✗ Error: " + e.getMessage() + "\n");
            api.logging().logToError("Failed to load proto directory: " + e.getMessage());
        }

        // Scroll to bottom
        statusArea.setCaretPosition(statusArea.getDocument().getLength());
    }

    private void clearSchemas() {
        int confirm = JOptionPane.showConfirmDialog(
                getParentFrame(),
                "Clear all loaded schemas?",
                "Confirm Clear",
                JOptionPane.YES_NO_OPTION
        );

        if (confirm == JOptionPane.YES_OPTION) {
            schemaManager.clear();
            statusArea.setText("All schemas cleared.\n");
            updateStats();
        }
    }

    private void updateStats() {
        statsLabel.setText("Schemas: " + schemaManager.getStats());
    }

    private Frame getParentFrame() {
        return (Frame) SwingUtilities.getWindowAncestor(this);
    }
}
