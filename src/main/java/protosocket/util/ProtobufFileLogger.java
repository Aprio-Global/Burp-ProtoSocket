package protosocket.util;

import burp.api.montoya.MontoyaApi;
import protosocket.core.ProtobufField;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Logs protobuf WebSocket traffic to file with hex dump, JSON, and metadata.
 * Creates log files in: ~/.burp-protosocket/logs/
 */
public class ProtobufFileLogger {
    private final Path logDir;
    private final MontoyaApi api;
    private final DateTimeFormatter timestampFormat;
    private final DateTimeFormatter dateFormat;
    private boolean enabled = true;

    public ProtobufFileLogger(MontoyaApi api) {
        this.api = api;

        // Create log directory in user home
        String userHome = System.getProperty("user.home");
        this.logDir = Paths.get(userHome, ".burp-protosocket", "logs");

        try {
            Files.createDirectories(logDir);
            api.logging().logToOutput("ProtoSocket file logging enabled: " + logDir);
        } catch (IOException e) {
            api.logging().logToError("Failed to create log directory: " + e.getMessage());
            enabled = false;
        }

        this.timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());
        this.dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault());
    }

    /**
     * Log a modified WebSocket message to file.
     * Creates one log file per day: protosocket-YYYY-MM-DD.log
     *
     * @param originalBinary Original unmodified binary data
     * @param modifiedBinary Modified binary data to be sent
     * @param modifiedFields Decoded protobuf fields
     * @param direction Message direction (e.g., "CLIENT_TO_SERVER")
     * @param rootMessageType Root message type name (may be null)
     */
    public void logModifiedMessage(
            byte[] originalBinary,
            byte[] modifiedBinary,
            List<ProtobufField> modifiedFields,
            String direction,
            String rootMessageType) {

        if (!enabled) return;

        try {
            // Create daily log file
            String date = dateFormat.format(Instant.now());
            Path logFile = logDir.resolve("protosocket-" + date + ".log");

            // Append to log file
            try (BufferedWriter writer = Files.newBufferedWriter(logFile,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

                String timestamp = timestampFormat.format(Instant.now());

                writer.write("=".repeat(80));
                writer.newLine();
                writer.write("MODIFIED WEBSOCKET MESSAGE");
                writer.newLine();
                writer.write("=".repeat(80));
                writer.newLine();
                writer.write("Timestamp: " + timestamp);
                writer.newLine();
                writer.write("Direction: " + direction);
                writer.newLine();
                writer.write("Root Message Type: " + (rootMessageType != null ? rootMessageType : "auto-detect"));
                writer.newLine();
                writer.write("Original Size: " + originalBinary.length + " bytes");
                writer.newLine();
                writer.write("Modified Size: " + modifiedBinary.length + " bytes");
                writer.newLine();
                writer.newLine();

                // Base64 encoded (for copy/paste)
                writer.write("--- BASE64 (Modified) ---");
                writer.newLine();
                writer.write(java.util.Base64.getEncoder().encodeToString(modifiedBinary));
                writer.newLine();
                writer.newLine();

                // Hex dump of modified binary
                writer.write("--- HEX DUMP (Modified) ---");
                writer.newLine();
                writer.write(ProtobufFormatter.formatHexDump(modifiedBinary));
                writer.newLine();
                writer.newLine();

                // Decoded JSON
                writer.write("--- DECODED JSON ---");
                writer.newLine();
                writer.write(ProtobufFormatter.toJson(modifiedFields));
                writer.newLine();
                writer.newLine();

                // Original hex dump (for comparison)
                writer.write("--- HEX DUMP (Original) ---");
                writer.newLine();
                writer.write(ProtobufFormatter.formatHexDump(originalBinary));
                writer.newLine();
                writer.newLine();

                writer.flush();

                api.logging().logToOutput("Logged modified message to: " + logFile);
            }
        } catch (IOException e) {
            api.logging().logToError("Failed to write to log file: " + e.getMessage());
        }
    }

    /**
     * Get the log directory path.
     */
    public Path getLogDirectory() {
        return logDir;
    }

    /**
     * Check if file logging is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }
}
