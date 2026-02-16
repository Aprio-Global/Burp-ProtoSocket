package protosocket.schema;

import burp.api.montoya.MontoyaApi;
import com.google.protobuf.DescriptorProtos.*;
import com.google.protobuf.Descriptors.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses .proto files and builds FileDescriptors with import resolution.
 * Supports basic proto3 syntax including imports and nested messages.
 */
public class ProtoFileParser {
    private final MontoyaApi api;
    private final ProtoSchemaManager schemaManager;

    // Regex patterns for parsing
    private static final Pattern IMPORT_PATTERN = Pattern.compile("import\\s+\"([^\"]+)\";");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+([\\w.]+);");
    // Simple patterns to find start of definitions (body extraction uses brace matching)
    private static final Pattern MESSAGE_START_PATTERN = Pattern.compile("message\\s+(\\w+)\\s*\\{");
    private static final Pattern ENUM_START_PATTERN = Pattern.compile("enum\\s+(\\w+)\\s*\\{");
    private static final Pattern ENUM_VALUE_PATTERN = Pattern.compile("(\\w+)\\s*=\\s*(\\d+)");
    private static final Pattern FIELD_PATTERN = Pattern.compile("(repeated\\s+)?([\\w.]+)\\s+(\\w+)\\s*=\\s*(\\d+)");
    private static final Pattern ONEOF_PATTERN = Pattern.compile("oneof\\s+(\\w+)\\s*\\{([^}]+)\\}", Pattern.MULTILINE);

    public ProtoFileParser(MontoyaApi api, ProtoSchemaManager schemaManager) {
        this.api = api;
        this.schemaManager = schemaManager;
    }

    /**
     * Parse a .proto file and register it with the schema manager.
     * Automatically resolves and loads imports.
     */
    public FileDescriptor parseProtoFile(String filePath) throws IOException, DescriptorValidationException {
        return parseProtoFile(new File(filePath));
    }

    /**
     * Parse a .proto file and register it.
     */
    public FileDescriptor parseProtoFile(File protoFile) throws IOException, DescriptorValidationException {
        if (!protoFile.exists()) {
            throw new IOException("Proto file not found: " + protoFile.getAbsolutePath());
        }

        String content = new String(Files.readAllBytes(protoFile.toPath()));
        return parseProtoContent(content, protoFile.getName(), protoFile.getParent());
    }

    /**
     * Remove comments from proto file content.
     */
    private String stripComments(String content) {
        // Remove multi-line comments /* */
        content = content.replaceAll("/\\*.*?\\*/", "");

        // Remove single-line comments //
        content = content.replaceAll("//.*?(\r?\n|$)", "$1");

        return content;
    }

    /**
     * Parse proto file content.
     */
    private FileDescriptor parseProtoContent(String content, String fileName, String basePath)
            throws IOException, DescriptorValidationException {

        api.logging().logToOutput("Parsing proto file: " + fileName);

        // Strip comments before parsing
        content = stripComments(content);

        // Extract package name
        String packageName = extractPackage(content);

        // Extract and resolve imports
        List<FileDescriptor> dependencies = resolveImports(content, basePath);

        // Extract message and enum definitions
        FileDescriptorProto.Builder fileBuilder = FileDescriptorProto.newBuilder()
                .setName(fileName)
                .setSyntax("proto3");

        if (packageName != null) {
            fileBuilder.setPackage(packageName);
        }

        // Track enum names for type resolution
        Set<String> enumNames = new HashSet<>();

        // Parse all enum types first (using brace-aware extraction)
        List<EnumDef> enums = extractEnums(content);
        for (EnumDef enumDef : enums) {
            enumNames.add(enumDef.name);
            EnumDescriptorProto enumDescriptor = parseEnum(enumDef.name, enumDef.body);
            fileBuilder.addEnumType(enumDescriptor);
        }

        // Parse all message types (using brace-aware extraction)
        List<MessageDef> messages = extractMessages(content);
        for (MessageDef msgDef : messages) {
            DescriptorProto messageDescriptor = parseMessage(msgDef.name, msgDef.body, enumNames);
            fileBuilder.addMessageType(messageDescriptor);
        }

        // Build the file descriptor
        FileDescriptor fileDescriptor = FileDescriptor.buildFrom(
                fileBuilder.build(),
                dependencies.toArray(new FileDescriptor[0])
        );

        // Register with schema manager
        schemaManager.registerFileDescriptor(fileDescriptor);

        return fileDescriptor;
    }

    /**
     * Extract package name from proto content.
     */
    private String extractPackage(String content) {
        Matcher matcher = PACKAGE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Resolve and load all imports.
     */
    private List<FileDescriptor> resolveImports(String content, String basePath) throws IOException, DescriptorValidationException {
        List<FileDescriptor> dependencies = new ArrayList<>();

        Matcher importMatcher = IMPORT_PATTERN.matcher(content);
        while (importMatcher.find()) {
            String importPath = importMatcher.group(1);

            // Check if already loaded
            FileDescriptor existing = schemaManager.getFileDescriptor(importPath);
            if (existing != null) {
                dependencies.add(existing);
                continue;
            }

            // Try to find the file in search paths
            File importFile = findImportFile(importPath, basePath);
            if (importFile != null && importFile.exists()) {
                FileDescriptor importedDescriptor = parseProtoFile(importFile);
                dependencies.add(importedDescriptor);
            } else {
                api.logging().logToError("Could not resolve import: " + importPath);
            }
        }

        return dependencies;
    }

    /**
     * Find an imported proto file using search paths.
     */
    private File findImportFile(String importPath, String basePath) {
        // Strategy 1: Try base path first (for relative imports)
        if (basePath != null) {
            File file = new File(basePath, importPath);
            if (file.exists()) {
                return file;
            }
        }

        // Strategy 2: Try search paths (for absolute imports from root)
        for (String searchPath : schemaManager.getSearchPaths()) {
            File file = new File(searchPath, importPath);
            if (file.exists()) {
                return file;
            }
        }

        // Strategy 3: Try using just the filename (files in same directory)
        // where the file is actually in the same directory as the importing file
        if (basePath != null) {
            String filename = new File(importPath).getName();
            File file = new File(basePath, filename);
            if (file.exists()) {
                api.logging().logToOutput("Resolved import via same-directory fallback: " + importPath + " -> " + filename);
                return file;
            }
        }

        return null;
    }

    /**
     * Extract matching braces content starting from an opening brace position.
     * Returns the content between braces (excluding the braces themselves).
     */
    private String extractBracedContent(String content, int startPos) {
        int braceCount = 0;
        int contentStart = -1;

        for (int i = startPos; i < content.length(); i++) {
            char c = content.charAt(i);

            if (c == '{') {
                if (braceCount == 0) {
                    contentStart = i + 1;
                }
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    return content.substring(contentStart, i);
                }
            }
        }

        return ""; // Unmatched braces
    }

    /**
     * Extract all message definitions with proper brace matching.
     */
    private List<MessageDef> extractMessages(String content) {
        List<MessageDef> messages = new ArrayList<>();
        Matcher matcher = MESSAGE_START_PATTERN.matcher(content);

        while (matcher.find()) {
            String name = matcher.group(1);
            int bracePos = matcher.end() - 1; // Position of '{'
            String body = extractBracedContent(content, bracePos);
            messages.add(new MessageDef(name, body, matcher.start(), matcher.end() + body.length()));
        }

        return messages;
    }

    /**
     * Extract all enum definitions with proper brace matching.
     */
    private List<EnumDef> extractEnums(String content) {
        List<EnumDef> enums = new ArrayList<>();
        Matcher matcher = ENUM_START_PATTERN.matcher(content);

        while (matcher.find()) {
            String name = matcher.group(1);
            int bracePos = matcher.end() - 1;
            String body = extractBracedContent(content, bracePos);
            enums.add(new EnumDef(name, body, matcher.start(), matcher.end() + body.length()));
        }

        return enums;
    }

    /**
     * Helper class to store message definition.
     */
    private static class MessageDef {
        String name;
        String body;
        int start;
        int end;

        MessageDef(String name, String body, int start, int end) {
            this.name = name;
            this.body = body;
            this.start = start;
            this.end = end;
        }
    }

    /**
     * Helper class to store enum definition.
     */
    private static class EnumDef {
        String name;
        String body;
        int start;
        int end;

        EnumDef(String name, String body, int start, int end) {
            this.name = name;
            this.body = body;
            this.start = start;
            this.end = end;
        }
    }

    /**
     * Remove oneof blocks from message body to avoid duplicate field parsing.
     */
    private String removeOneofBlocks(String messageBody) {
        Matcher oneofMatcher = ONEOF_PATTERN.matcher(messageBody);
        return oneofMatcher.replaceAll("");
    }

    /**
     * Remove nested message and enum definitions from message body.
     */
    private String removeNestedDefinitions(String messageBody) {
        // Remove all message definitions
        List<MessageDef> messages = extractMessages(messageBody);
        List<EnumDef> enums = extractEnums(messageBody);

        // Sort by start position in reverse order to remove from end to start
        List<int[]> ranges = new ArrayList<>();
        for (MessageDef msg : messages) {
            ranges.add(new int[]{msg.start, msg.end});
        }
        for (EnumDef en : enums) {
            ranges.add(new int[]{en.start, en.end});
        }
        ranges.sort((a, b) -> Integer.compare(b[0], a[0]));

        StringBuilder result = new StringBuilder(messageBody);
        for (int[] range : ranges) {
            result.delete(range[0], Math.min(range[1], result.length()));
        }

        return result.toString();
    }

    /**
     * Parse an enum definition.
     */
    private EnumDescriptorProto parseEnum(String enumName, String enumBody) {
        EnumDescriptorProto.Builder enumBuilder = EnumDescriptorProto.newBuilder()
                .setName(enumName);

        Matcher valueMatcher = ENUM_VALUE_PATTERN.matcher(enumBody);
        while (valueMatcher.find()) {
            String valueName = valueMatcher.group(1);
            int valueNumber = Integer.parseInt(valueMatcher.group(2));

            EnumValueDescriptorProto.Builder valueBuilder = EnumValueDescriptorProto.newBuilder()
                    .setName(valueName)
                    .setNumber(valueNumber);

            enumBuilder.addValue(valueBuilder.build());
        }

        return enumBuilder.build();
    }

    /**
     * Parse a message definition.
     */
    private DescriptorProto parseMessage(String messageName, String messageBody, Set<String> enumNames) {
        DescriptorProto.Builder messageBuilder = DescriptorProto.newBuilder()
                .setName(messageName);

        // Track nested message and enum names for type resolution
        Set<String> nestedMessageNames = new HashSet<>();
        Set<String> nestedEnumNames = new HashSet<>(enumNames);

        // Parse nested enums first (using brace-aware extraction)
        List<EnumDef> nestedEnums = extractEnums(messageBody);
        for (EnumDef enumDef : nestedEnums) {
            nestedEnumNames.add(enumDef.name);
            EnumDescriptorProto enumDescriptor = parseEnum(enumDef.name, enumDef.body);
            messageBuilder.addEnumType(enumDescriptor);
        }

        // Parse nested messages (using brace-aware extraction)
        List<MessageDef> nestedMessages = extractMessages(messageBody);
        for (MessageDef msgDef : nestedMessages) {
            nestedMessageNames.add(msgDef.name);
            DescriptorProto nestedDescriptor = parseMessage(msgDef.name, msgDef.body, nestedEnumNames);
            messageBuilder.addNestedType(nestedDescriptor);
        }

        // Remove nested message and enum definitions from body before parsing fields
        String bodyWithoutNested = removeNestedDefinitions(messageBody);

        int oneofIndex = 0;
        Map<String, Integer> oneofNameToIndex = new HashMap<>();

        // Combine nested names with file-level enums for type resolution
        Set<String> allEnumNames = new HashSet<>(nestedEnumNames);
        Set<String> allTypeNames = new HashSet<>(nestedMessageNames);
        allTypeNames.addAll(allEnumNames);

        // FIRST: Parse oneof blocks
        Matcher oneofMatcher = ONEOF_PATTERN.matcher(bodyWithoutNested);
        while (oneofMatcher.find()) {
            String oneofName = oneofMatcher.group(1);
            String oneofBody = oneofMatcher.group(2);

            // Register oneof group
            OneofDescriptorProto.Builder oneofBuilder =
                OneofDescriptorProto.newBuilder().setName(oneofName);
            messageBuilder.addOneofDecl(oneofBuilder.build());
            oneofNameToIndex.put(oneofName, oneofIndex);

            // Parse fields inside oneof
            Matcher fieldMatcher = FIELD_PATTERN.matcher(oneofBody);
            while (fieldMatcher.find()) {
                boolean isRepeated = fieldMatcher.group(1) != null;
                String fieldType = fieldMatcher.group(2);
                String fieldName = fieldMatcher.group(3);
                int fieldNumber = Integer.parseInt(fieldMatcher.group(4));

                FieldDescriptorProto.Builder fieldBuilder = FieldDescriptorProto.newBuilder()
                        .setName(fieldName)
                        .setNumber(fieldNumber)
                        .setOneofIndex(oneofIndex);  // Mark field as part of oneof

                // Set label (repeated or optional)
                if (isRepeated) {
                    fieldBuilder.setLabel(FieldDescriptorProto.Label.LABEL_REPEATED);
                } else {
                    fieldBuilder.setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL);
                }

                // Set field type
                FieldDescriptorProto.Type type = mapProtoType(fieldType);
                if (type != null) {
                    fieldBuilder.setType(type);
                } else if (isEnumType(fieldType, allEnumNames)) {
                    // It's an enum type
                    fieldBuilder.setType(FieldDescriptorProto.Type.TYPE_ENUM);
                    fieldBuilder.setTypeName(fieldType);
                } else {
                    // Assume it's a message type
                    fieldBuilder.setType(FieldDescriptorProto.Type.TYPE_MESSAGE);
                    fieldBuilder.setTypeName(fieldType);
                }

                messageBuilder.addField(fieldBuilder.build());
            }

            oneofIndex++;
        }

        // SECOND: Parse regular fields (excluding oneof content)
        String bodyWithoutOneofs = removeOneofBlocks(bodyWithoutNested);
        Matcher fieldMatcher = FIELD_PATTERN.matcher(bodyWithoutOneofs);
        while (fieldMatcher.find()) {
            boolean isRepeated = fieldMatcher.group(1) != null;
            String fieldType = fieldMatcher.group(2);
            String fieldName = fieldMatcher.group(3);
            int fieldNumber = Integer.parseInt(fieldMatcher.group(4));

            FieldDescriptorProto.Builder fieldBuilder = FieldDescriptorProto.newBuilder()
                    .setName(fieldName)
                    .setNumber(fieldNumber);

            // Set label (repeated or optional)
            if (isRepeated) {
                fieldBuilder.setLabel(FieldDescriptorProto.Label.LABEL_REPEATED);
            } else {
                fieldBuilder.setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL);
            }

            // Set field type
            FieldDescriptorProto.Type type = mapProtoType(fieldType);
            if (type != null) {
                fieldBuilder.setType(type);
            } else if (isEnumType(fieldType, allEnumNames)) {
                // It's an enum type
                fieldBuilder.setType(FieldDescriptorProto.Type.TYPE_ENUM);
                fieldBuilder.setTypeName(fieldType);
            } else {
                // Assume it's a message type
                fieldBuilder.setType(FieldDescriptorProto.Type.TYPE_MESSAGE);
                fieldBuilder.setTypeName(fieldType);
            }

            messageBuilder.addField(fieldBuilder.build());
        }

        return messageBuilder.build();
    }

    /**
     * Check if a type is an enum type.
     * Handles both simple names ("GapSize") and qualified names ("com.example.GapSize").
     */
    private boolean isEnumType(String fieldType, Set<String> enumNames) {
        // Check exact match first
        if (enumNames.contains(fieldType)) {
            return true;
        }

        // Check if it's a qualified name and the simple name matches
        if (fieldType.contains(".")) {
            String simpleName = fieldType.substring(fieldType.lastIndexOf('.') + 1);
            if (enumNames.contains(simpleName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Map proto type string to FieldDescriptorProto.Type.
     */
    private FieldDescriptorProto.Type mapProtoType(String protoType) {
        switch (protoType.toLowerCase()) {
            case "double": return FieldDescriptorProto.Type.TYPE_DOUBLE;
            case "float": return FieldDescriptorProto.Type.TYPE_FLOAT;
            case "int32": return FieldDescriptorProto.Type.TYPE_INT32;
            case "int64": return FieldDescriptorProto.Type.TYPE_INT64;
            case "uint32": return FieldDescriptorProto.Type.TYPE_UINT32;
            case "uint64": return FieldDescriptorProto.Type.TYPE_UINT64;
            case "sint32": return FieldDescriptorProto.Type.TYPE_SINT32;
            case "sint64": return FieldDescriptorProto.Type.TYPE_SINT64;
            case "fixed32": return FieldDescriptorProto.Type.TYPE_FIXED32;
            case "fixed64": return FieldDescriptorProto.Type.TYPE_FIXED64;
            case "sfixed32": return FieldDescriptorProto.Type.TYPE_SFIXED32;
            case "sfixed64": return FieldDescriptorProto.Type.TYPE_SFIXED64;
            case "bool": return FieldDescriptorProto.Type.TYPE_BOOL;
            case "string": return FieldDescriptorProto.Type.TYPE_STRING;
            case "bytes": return FieldDescriptorProto.Type.TYPE_BYTES;
            default: return null; // Custom message type
        }
    }

    /**
     * Load all .proto files from a directory recursively.
     */
    public int loadProtoDirectory(String directoryPath) {
        int loaded = 0;
        try {
            Path dir = Paths.get(directoryPath);
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                api.logging().logToError("Not a valid directory: " + directoryPath);
                return 0;
            }

            // Add this directory to search paths
            schemaManager.addSearchPath(directoryPath);

            // Find all .proto files
            List<File> protoFiles = findProtoFiles(dir.toFile());
            api.logging().logToOutput("Found " + protoFiles.size() + " .proto files in " + directoryPath);

            // Parse each file
            for (File protoFile : protoFiles) {
                try {
                    parseProtoFile(protoFile);
                    loaded++;
                } catch (Exception e) {
                    api.logging().logToError("Failed to parse " + protoFile.getName() + ": " + e.getMessage());
                }
            }

            api.logging().logToOutput("Successfully loaded " + loaded + " proto files");

        } catch (Exception e) {
            api.logging().logToError("Error loading proto directory: " + e.getMessage());
        }

        return loaded;
    }

    /**
     * Recursively find all .proto files in a directory.
     */
    private List<File> findProtoFiles(File directory) {
        List<File> protoFiles = new ArrayList<>();

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    protoFiles.addAll(findProtoFiles(file));
                } else if (file.getName().endsWith(".proto")) {
                    protoFiles.add(file);
                }
            }
        }

        return protoFiles;
    }
}
