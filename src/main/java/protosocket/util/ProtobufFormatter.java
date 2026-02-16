package protosocket.util;

import protosocket.core.ProtobufField;
import com.google.protobuf.ByteString;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Formats ProtobufField objects to/from JSON for display and editing.
 */
public class ProtobufFormatter {

    /**
     * Convert a list of ProtobufField objects to a formatted JSON string.
     *
     * @param fields List of decoded protobuf fields
     * @return Pretty-printed JSON string
     */
    public static String toJson(List<ProtobufField> fields) {
        JSONObject root = fieldsToJsonObject(fields);
        return root.toString(2); // Pretty print with 2-space indent
    }

    /**
     * Convert a list of fields to a JSONObject, handling repeated fields as arrays.
     * This is used for both top-level and nested message formatting.
     */
    private static JSONObject fieldsToJsonObject(List<ProtobufField> fields) {
        JSONObject obj = new JSONObject();

        for (ProtobufField field : fields) {
            // Use field name from schema if available, otherwise use field_N format
            String key = field.getFieldName() != null ?
                field.getFieldName() : "field_" + field.getFieldNumber();

            // If the field already exists, handle multiple values for the same field
            if (obj.has(key)) {
                Object existing = obj.get(key);
                JSONArray array;
                if (existing instanceof JSONArray) {
                    array = (JSONArray) existing;
                } else {
                    // Convert single value to array
                    array = new JSONArray();
                    array.put(existing);
                    obj.put(key, array);
                }
                array.put(formatFieldValue(field));
            } else {
                obj.put(key, formatFieldValue(field));
            }
        }

        return obj;
    }

    /**
     * Format a single field's value using schema information.
     * Schema is always required.
     */
    private static Object formatFieldValue(ProtobufField field) {
        return formatWithSchema(field);
    }

    /**
     * Check if the schema field type is compatible with the actual wire type.
     */
    private static boolean isWireTypeCompatible(String fieldType, int wireType) {
        if (fieldType == null) {
            return false;
        }

        switch (fieldType) {
            case "int32":
            case "int64":
            case "uint32":
            case "uint64":
            case "sint32":
            case "sint64":
            case "bool":
            case "enum":
                // Accept both VARINT (normal encoding) and LENGTH_DELIMITED (packed repeated encoding)
                return wireType == ProtobufField.VARINT || wireType == ProtobufField.LENGTH_DELIMITED;

            case "fixed32":
            case "sfixed32":
            case "float":
                // Accept both FIXED32 (normal encoding) and LENGTH_DELIMITED (packed repeated encoding)
                return wireType == ProtobufField.FIXED32 || wireType == ProtobufField.LENGTH_DELIMITED;

            case "fixed64":
            case "sfixed64":
            case "double":
                // Accept both FIXED64 (normal encoding) and LENGTH_DELIMITED (packed repeated encoding)
                return wireType == ProtobufField.FIXED64 || wireType == ProtobufField.LENGTH_DELIMITED;

            case "string":
            case "bytes":
            case "message":
                return wireType == ProtobufField.LENGTH_DELIMITED;

            default:
                return false;
        }
    }

    /**
     * Format field value using schema type information for clean output.
     */
    private static Object formatWithSchema(ProtobufField field) {
        String fieldType = field.getFieldType();
        int wireType = field.getWireType();

        // SAFETY CHECK: Verify wire type matches schema type expectation
        if (!isWireTypeCompatible(fieldType, wireType)) {
            // Schema mismatch detected - show clean error instead of verbose dump
            return formatSchemaMismatch(field, fieldType, wireType);
        }

        // PRIORITY CHECK: If we have successfully decoded nested fields, prioritize showing them
        // This handles cases where schema says "string" or "bytes" but the data
        // is actually a nested protobuf message that was successfully decoded
        if (field.hasNestedFields()) {
            return fieldsToJsonObject(field.getNestedFields());
        }

        // PACKED REPEATED FIELD CHECK: If wire type is LENGTH_DELIMITED for primitive types,
        // this is a packed repeated field - unpack the values into an array
        if (wireType == ProtobufField.LENGTH_DELIMITED) {
            switch (fieldType) {
                case "int32":
                case "int64":
                case "uint32":
                case "uint64":
                case "sint32":
                case "sint64":
                case "bool":
                case "enum":
                    return unpackVarintArray(field, fieldType);

                case "fixed32":
                case "sfixed32":
                case "float":
                    return unpackFixed32Array(field, fieldType);

                case "fixed64":
                case "sfixed64":
                case "double":
                    return unpackFixed64Array(field, fieldType);
            }
        }

        // Type-safe value extraction with validation
        switch (fieldType) {
            case "int32":
            case "int64":
            case "uint32":
            case "uint64":
                if (!(field.getValue() instanceof Number)) {
                    return null;
                }
                return ((Number) field.getValue()).longValue();

            case "sint32":
            case "sint64":
                if (!(field.getValue() instanceof Number)) {
                    return null;
                }
                long varint = ((Number) field.getValue()).longValue();
                return (varint >>> 1) ^ -(varint & 1);

            case "bool":
                if (!(field.getValue() instanceof Number)) {
                    return null;
                }
                return ((Number) field.getValue()).longValue() != 0;

            case "fixed32":
            case "sfixed32":
                if (!(field.getValue() instanceof Number)) {
                    return null;
                }
                return ((Number) field.getValue()).intValue();

            case "fixed64":
            case "sfixed64":
                if (!(field.getValue() instanceof Number)) {
                    return null;
                }
                return ((Number) field.getValue()).longValue();

            case "float":
                if (!(field.getValue() instanceof Number)) {
                    return null;
                }
                int bits = ((Number) field.getValue()).intValue();
                return Float.intBitsToFloat(bits);

            case "double":
                if (!(field.getValue() instanceof Number)) {
                    return null;
                }
                long longBits = ((Number) field.getValue()).longValue();
                return Double.longBitsToDouble(longBits);

            case "string":
                if (!(field.getValue() instanceof ByteString)) {
                    return null;
                }
                ByteString bytes = (ByteString) field.getValue();
                return bytes.toStringUtf8();

            case "bytes":
                if (!(field.getValue() instanceof ByteString)) {
                    return null;
                }
                ByteString byteString = (ByteString) field.getValue();
                return java.util.Base64.getEncoder().encodeToString(byteString.toByteArray());

            case "message":
                // Recursively format nested message with repeated field support
                if (field.hasNestedFields()) {
                    return fieldsToJsonObject(field.getNestedFields());
                }
                return null;

            case "enum":
                if (!(field.getValue() instanceof Number)) {
                    return null;
                }
                return ((Number) field.getValue()).intValue();

            default:
                // Unknown type
                return null;
        }
    }

    // Format field when schema type doesn't match wire type - shows clean error
    private static Object formatSchemaMismatch(ProtobufField field, String expectedType, int actualWireType) {
        // For nested messages that were successfully decoded, show them anyway
        if (field.hasNestedFields()) {
            return fieldsToJsonObject(field.getNestedFields());
        }

        // Otherwise show a simple error
        JSONObject error = new JSONObject();
        error.put("_error", "Schema mismatch");
        error.put("_expected_type", expectedType);
        error.put("_actual_wire_type", ProtobufField.getWireTypeName(actualWireType));

        // Include the raw value for reference
        switch (actualWireType) {
            case ProtobufField.VARINT:
                error.put("_raw_value", ((Number) field.getValue()).longValue());
                break;
            case ProtobufField.LENGTH_DELIMITED:
                if (field.getValue() instanceof ByteString) {
                    ByteString bs = (ByteString) field.getValue();
                    error.put("_raw_bytes", java.util.Base64.getEncoder().encodeToString(bs.toByteArray()));
                }
                break;
            case ProtobufField.FIXED32:
            case ProtobufField.FIXED64:
                error.put("_raw_value", ((Number) field.getValue()).longValue());
                break;
        }

        return error;
    }


    /**
     * Check if a string is printable (contains mostly printable characters).
     */
    private static boolean isPrintableString(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }

        int printableCount = 0;
        for (char c : str.toCharArray()) {
            if (c >= 32 && c < 127) {
                printableCount++;
            } else if (Character.isWhitespace(c)) {
                printableCount++;
            }
        }

        // Consider it printable if at least 80% of characters are printable
        return (double) printableCount / str.length() >= 0.8;
    }


    /**
     * Parse JSON string back to a list of ProtobufField objects with schema support.
     * Schema descriptor is required for all operations.
     *
     * @param json JSON string representation of fields
     * @param descriptor Root message descriptor for resolving named fields (required, not null)
     * @return List of ProtobufField objects
     * @throws JSONException if JSON is invalid
     * @throws IllegalArgumentException if descriptor is null
     */
    public static List<ProtobufField> fromJson(String json, com.google.protobuf.Descriptors.Descriptor descriptor) throws JSONException {
        if (descriptor == null) {
            throw new IllegalArgumentException("Schema descriptor is required for parsing JSON");
        }

        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Log start of parsing
        System.out.println("[ProtobufFormatter] Starting fromJson with descriptor: " + descriptor.getFullName());

        List<ProtobufField> fields = new ArrayList<>();
        JSONObject root = new JSONObject(json);

        Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            String key = keys.next();

            // Determine field number and descriptor
            int fieldNumber = -1;
            com.google.protobuf.Descriptors.FieldDescriptor fieldDescriptor = null;

            // Schema-based lookup (descriptor is always non-null now)
            fieldDescriptor = descriptor.findFieldByName(key);
            if (fieldDescriptor != null) {
                fieldNumber = fieldDescriptor.getNumber();
                // Log successful schema resolution
                System.out.println("[ProtobufFormatter]   ✓ Field '" + key + "' → #" + fieldNumber +
                    " (type=" + fieldDescriptor.getType() + ")");
            } else {
                // Log schema lookup failure
                System.out.println("[ProtobufFormatter]   ✗ Field '" + key + "' not found in descriptor " +
                    descriptor.getName());
            }

            // Fall back to field_N format
            if (fieldNumber == -1 && key.startsWith("field_")) {
                try {
                    fieldNumber = Integer.parseInt(key.substring(6));
                    // Log fallback
                    System.out.println("[ProtobufFormatter]   → Field '" + key + "' → #" + fieldNumber +
                        " (field_N format)");
                } catch (NumberFormatException e) {
                    System.out.println("[ProtobufFormatter]   ✗ Invalid field_N format: " + key);
                    continue; // Skip invalid field names
                }
            }

            // Skip if we couldn't resolve field number
            if (fieldNumber == -1) {
                System.out.println("[ProtobufFormatter]   ⊗ Skipping field '" + key + "' (no field number)");
                continue;
            }

            Object value = root.get(key);

            // Parse field value with schema context
            List<ProtobufField> parsedFields = parseJsonValue(fieldNumber, value, fieldDescriptor);
            fields.addAll(parsedFields);
        }

        System.out.println("[ProtobufFormatter] Completed fromJson: " + fields.size() + " fields parsed");
        return fields;
    }


    /**
     * Parse a single field from verbose JSON format (old format with wire_type).
     */
    private static ProtobufField parseFieldFromVerboseJson(int fieldNumber, JSONObject fieldObj,
                                                           com.google.protobuf.Descriptors.FieldDescriptor fieldDescriptor) {
        String wireTypeName = fieldObj.getString("wire_type");
        int wireType = parseWireType(wireTypeName);

        switch (wireType) {
            case ProtobufField.VARINT:
                long varint = fieldObj.optLong("value", 0);
                return new ProtobufField(fieldNumber, wireType, varint);

            case ProtobufField.FIXED32:
                int fixed32 = fieldObj.optInt("value", 0);
                return new ProtobufField(fieldNumber, wireType, fixed32);

            case ProtobufField.FIXED64:
                long fixed64 = fieldObj.optLong("value", 0);
                return new ProtobufField(fieldNumber, wireType, fixed64);

            case ProtobufField.LENGTH_DELIMITED:
                List<ProtobufField> nested = null;

                // PRIORITY 1: Check for nested message first
                if (fieldObj.has("as_message") && !fieldObj.isNull("as_message")) {
                    JSONObject nestedObj = fieldObj.getJSONObject("as_message");

                    // Get child descriptor if available
                    com.google.protobuf.Descriptors.Descriptor childDescriptor = null;
                    if (fieldDescriptor != null &&
                        fieldDescriptor.getType() == com.google.protobuf.Descriptors.FieldDescriptor.Type.MESSAGE) {
                        childDescriptor = fieldDescriptor.getMessageType();
                    }

                    // Use schema-aware parser
                    nested = fromJsonObject(nestedObj, childDescriptor);

                    if (nested != null && !nested.isEmpty()) {
                        // Successfully parsed nested message, use it
                        return new ProtobufField(fieldNumber, wireType, null, nested);
                    }
                }

                // PRIORITY 2: Try string/base64/hex only if no nested message
                ByteString bytes;
                if (fieldObj.has("as_string") && !fieldObj.isNull("as_string")) {
                    bytes = ByteString.copyFromUtf8(fieldObj.getString("as_string"));
                } else if (fieldObj.has("as_base64") &&
                           !fieldObj.isNull("as_base64") &&
                           !fieldObj.getString("as_base64").isEmpty()) {
                    byte[] decoded = java.util.Base64.getDecoder().decode(fieldObj.getString("as_base64"));
                    bytes = ByteString.copyFrom(decoded);
                } else if (fieldObj.has("as_hex") &&
                           !fieldObj.isNull("as_hex") &&
                           !fieldObj.getString("as_hex").isEmpty()) {
                    // FIXED: Properly decode hex string to bytes instead of treating it as UTF-8
                    bytes = ByteString.copyFrom(hexToBytes(fieldObj.getString("as_hex")));
                } else {
                    bytes = ByteString.EMPTY;
                }

                return new ProtobufField(fieldNumber, wireType, bytes, nested);

            default:
                return null;
        }
    }

    /**
     * Parse wire type name to integer constant.
     */
    private static int parseWireType(String wireTypeName) {
        switch (wireTypeName.toUpperCase()) {
            case "VARINT":
                return ProtobufField.VARINT;
            case "FIXED64":
                return ProtobufField.FIXED64;
            case "LENGTH_DELIMITED":
                return ProtobufField.LENGTH_DELIMITED;
            case "START_GROUP":
                return ProtobufField.START_GROUP;
            case "END_GROUP":
                return ProtobufField.END_GROUP;
            case "FIXED32":
                return ProtobufField.FIXED32;
            default:
                return -1;
        }
    }

    /**
     * Parse a JSON value into ProtobufField(s).
     * Handles primitives, objects, arrays, and verbose format.
     *
     * @param fieldNumber Field number
     * @param value JSON value (primitive, object, or array)
     * @param fieldDescriptor Schema descriptor for this field (null if not available)
     * @return List of ProtobufField objects (multiple for repeated fields)
     */
    private static List<ProtobufField> parseJsonValue(
            int fieldNumber,
            Object value,
            com.google.protobuf.Descriptors.FieldDescriptor fieldDescriptor) throws JSONException {

        List<ProtobufField> fields = new ArrayList<>();

        // Handle JSONArray (repeated field)
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                Object item = array.get(i);
                fields.addAll(parseJsonValue(fieldNumber, item, fieldDescriptor));
            }
            return fields;
        }

        // Handle JSONObject
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;

            // Check for verbose format (has "wire_type" key)
            if (obj.has("wire_type")) {
                fields.add(parseFieldFromVerboseJson(fieldNumber, obj, fieldDescriptor));
                return fields;
            }

            // Check for schema mismatch error format
            if (obj.has("_error") && obj.getString("_error").equals("Schema mismatch")) {
                System.out.println("[ProtobufFormatter]   ! Schema mismatch field - reconstructing from raw data");

                // Get the actual wire type
                String wireTypeName = obj.optString("_actual_wire_type", "");
                int wireType = parseWireType(wireTypeName);

                // Reconstruct field from raw value based on wire type
                switch (wireType) {
                    case ProtobufField.VARINT:
                    case ProtobufField.FIXED32:
                    case ProtobufField.FIXED64:
                        if (obj.has("_raw_value")) {
                            long rawValue = obj.getLong("_raw_value");
                            fields.add(new ProtobufField(fieldNumber, wireType, rawValue));
                        }
                        break;

                    case ProtobufField.LENGTH_DELIMITED:
                        if (obj.has("_raw_bytes")) {
                            String base64 = obj.getString("_raw_bytes");
                            byte[] decodedBytes = java.util.Base64.getDecoder().decode(base64);
                            ByteString bytes = ByteString.copyFrom(decodedBytes);
                            fields.add(new ProtobufField(fieldNumber, wireType, bytes, null));
                        }
                        break;
                }
                return fields;
            }

            // Clean format - nested message
            com.google.protobuf.Descriptors.Descriptor childDescriptor = null;
            if (fieldDescriptor != null &&
                    fieldDescriptor.getType() == com.google.protobuf.Descriptors.FieldDescriptor.Type.MESSAGE) {
                childDescriptor = fieldDescriptor.getMessageType();
            }

            List<ProtobufField> nested = fromJsonObject(obj, childDescriptor);
            ProtobufField field = new ProtobufField(
                    fieldNumber,
                    ProtobufField.LENGTH_DELIMITED,
                    null,
                    nested
            );
            fields.add(field);
            return fields;
        }

        // Handle primitive values
        fields.add(parsePrimitiveValue(fieldNumber, value, fieldDescriptor));
        return fields;
    }

    /**
     * Recursively parse a JSON object into ProtobufFields.
     * Used for nested messages. Schema descriptor is required.
     */
    private static List<ProtobufField> fromJsonObject(
            JSONObject obj,
            com.google.protobuf.Descriptors.Descriptor descriptor) throws JSONException {

        List<ProtobufField> fields = new ArrayList<>();

        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = obj.get(key);

            // Resolve field number from schema
            int fieldNumber = -1;
            com.google.protobuf.Descriptors.FieldDescriptor fieldDescriptor = null;

            // Try schema-based lookup if descriptor is available
            if (descriptor != null) {
                fieldDescriptor = descriptor.findFieldByName(key);
                if (fieldDescriptor != null) {
                    fieldNumber = fieldDescriptor.getNumber();
                }
            }

            // Fall back to field_N format if schema lookup failed or no descriptor
            if (fieldNumber == -1 && key.startsWith("field_")) {
                try {
                    fieldNumber = Integer.parseInt(key.substring(6));
                } catch (NumberFormatException e) {
                    continue;
                }
            }

            if (fieldNumber == -1) {
                System.out.println("[ProtobufFormatter]   ⊗ Skipping field '" + key + "' (no field number, descriptor=" + (descriptor != null ? descriptor.getName() : "null") + ")");
                continue;
            }

            // Parse value recursively
            fields.addAll(parseJsonValue(fieldNumber, value, fieldDescriptor));
        }

        return fields;
    }

    /**
     * Parse a primitive JSON value into a ProtobufField.
     * Uses schema type information when available.
     */
    private static ProtobufField parsePrimitiveValue(
            int fieldNumber,
            Object value,
            com.google.protobuf.Descriptors.FieldDescriptor fieldDescriptor) throws JSONException {

        // Use schema type information if available
        if (fieldDescriptor != null) {
            com.google.protobuf.Descriptors.FieldDescriptor.Type type = fieldDescriptor.getType();

            // Log schema-aware type conversion
            System.out.println("[ProtobufFormatter]     Converting field #" + fieldNumber +
                " using schema type " + type + " (value=" + value + ")");

            switch (type) {
                case BOOL:
                    long boolValue = (value instanceof Boolean && (Boolean) value) ? 1L : 0L;
                    System.out.println("[ProtobufFormatter]       → BOOL: " + value + " → VARINT(" + boolValue + ")");
                    return new ProtobufField(fieldNumber, ProtobufField.VARINT, boolValue);

                case INT32:
                case INT64:
                case UINT32:
                case UINT64:
                case ENUM:
                    long varintValue = ((Number) value).longValue();
                    System.out.println("[ProtobufFormatter]       → " + type + ": " + value + " → VARINT(" + varintValue + ")");
                    return new ProtobufField(fieldNumber, ProtobufField.VARINT, varintValue);

                case SINT32:
                case SINT64:
                    long signedValue = ((Number) value).longValue();
                    // Apply ZigZag encoding: (n << 1) ^ (n >> 63)
                    long zigzagValue = (signedValue << 1) ^ (signedValue >> 63);
                    System.out.println("[ProtobufFormatter]       → " + type + ": " + value + " → ZIGZAG → VARINT(" + zigzagValue + ")");
                    return new ProtobufField(fieldNumber, ProtobufField.VARINT, zigzagValue);

                case FIXED32:
                case SFIXED32:
                    int fixed32Value = ((Number) value).intValue();
                    System.out.println("[ProtobufFormatter]       → " + type + ": " + value + " → FIXED32(" + fixed32Value + ")");
                    return new ProtobufField(fieldNumber, ProtobufField.FIXED32, fixed32Value);

                case FLOAT:
                    float floatValue = ((Number) value).floatValue();
                    int floatBits = Float.floatToRawIntBits(floatValue);
                    System.out.println("[ProtobufFormatter]       → FLOAT: " + value + " → FIXED32(bits=" + floatBits + ")");
                    return new ProtobufField(fieldNumber, ProtobufField.FIXED32, floatBits);

                case FIXED64:
                case SFIXED64:
                    long fixed64Value = ((Number) value).longValue();
                    System.out.println("[ProtobufFormatter]       → " + type + ": " + value + " → FIXED64(" + fixed64Value + ")");
                    return new ProtobufField(fieldNumber, ProtobufField.FIXED64, fixed64Value);

                case DOUBLE:
                    double doubleValue = ((Number) value).doubleValue();
                    long doubleBits = Double.doubleToRawLongBits(doubleValue);
                    System.out.println("[ProtobufFormatter]       → DOUBLE: " + value + " → FIXED64(bits=" + doubleBits + ")");
                    return new ProtobufField(fieldNumber, ProtobufField.FIXED64, doubleBits);

                case STRING:
                    String stringValue = value.toString();
                    System.out.println("[ProtobufFormatter]       → STRING: '" +
                        (stringValue.length() > 50 ? stringValue.substring(0, 50) + "..." : stringValue) +
                        "' → LENGTH_DELIMITED");
                    ByteString stringBytes = ByteString.copyFromUtf8(stringValue);
                    return new ProtobufField(fieldNumber, ProtobufField.LENGTH_DELIMITED, stringBytes, null);

                case BYTES:
                    String base64Value = value.toString();
                    byte[] decodedBytes = java.util.Base64.getDecoder().decode(base64Value);
                    ByteString bytes = ByteString.copyFrom(decodedBytes);
                    System.out.println("[ProtobufFormatter]       → BYTES: base64 decoded to " +
                        decodedBytes.length + " bytes → LENGTH_DELIMITED");
                    return new ProtobufField(fieldNumber, ProtobufField.LENGTH_DELIMITED, bytes, null);

                default:
                    System.out.println("[ProtobufFormatter]       ⚠ Unknown schema type");
                    throw new JSONException("Unknown schema type: " + type);
            }
        }

        // Should never reach here since descriptor is always required
        throw new JSONException("Schema descriptor is required but was not used");
    }


    /**
     * Convert byte array to hex string representation.
     */
    private static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }

        StringBuilder hexString = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b & 0xFF));
        }
        return hexString.toString();
    }

    /**
     * Convert hex string to byte array.
     * Accepts formats: "48656c6c6f" or "0x48656c6c6f"
     *
     * @param hex Hex string to convert
     * @return byte array
     * @throws IllegalArgumentException if hex string has odd length or invalid characters
     */
    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) {
            return new byte[0];
        }

        // Remove "0x" prefix if present
        if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }

        // Remove any whitespace
        hex = hex.replaceAll("\\s+", "");

        int len = hex.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length");
        }

        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int highNibble = Character.digit(hex.charAt(i), 16);
            int lowNibble = Character.digit(hex.charAt(i + 1), 16);

            if (highNibble == -1 || lowNibble == -1) {
                throw new IllegalArgumentException("Invalid hex character in string: " + hex);
            }

            data[i / 2] = (byte) ((highNibble << 4) + lowNibble);
        }
        return data;
    }

    /**
     * Format binary data as a hex dump with ASCII representation.
     * Format: offset hex_bytes  ASCII
     * Example:
     *   0000: 08 01 12 0b 48 65 6c 6c  6f 20 57 6f 72 6c 64 1a |....Hello World.|
     *   0010: 04 74 65 73 74                                    |.test|
     *
     * @param data Binary data to format
     * @param bytesPerLine Number of bytes per line (typically 16)
     * @return Formatted hex dump string
     */
    public static String formatHexDump(byte[] data, int bytesPerLine) {
        if (data == null || data.length == 0) {
            return "(empty)";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i += bytesPerLine) {
            // Offset
            sb.append(String.format("%04x: ", i));

            // Hex bytes (with midpoint separator)
            for (int j = 0; j < bytesPerLine; j++) {
                if (i + j < data.length) {
                    sb.append(String.format("%02x ", data[i + j]));
                } else {
                    sb.append("   ");
                }
                if (j == 7) sb.append(" "); // Visual separator at 8 bytes
            }

            // ASCII representation
            sb.append(" |");
            for (int j = 0; j < bytesPerLine && i + j < data.length; j++) {
                byte b = data[i + j];
                sb.append((b >= 32 && b < 127) ? (char)b : '.');
            }
            sb.append("|");

            if (i + bytesPerLine < data.length) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Convenience method with default 16 bytes per line.
     */
    public static String formatHexDump(byte[] data) {
        return formatHexDump(data, 16);
    }

    /**
     * Unpack a packed repeated varint field (int32, uint32, sint32, sint64, bool, enum).
     */
    private static Object unpackVarintArray(ProtobufField field, String fieldType) {
        if (!(field.getValue() instanceof com.google.protobuf.ByteString)) {
            return null;
        }

        com.google.protobuf.ByteString bytes = (com.google.protobuf.ByteString) field.getValue();
        byte[] data = bytes.toByteArray();
        List<Object> values = new ArrayList<>();

        int pos = 0;
        while (pos < data.length) {
            // Decode varint
            long value = 0;
            int shift = 0;
            while (pos < data.length) {
                byte b = data[pos++];
                value |= (long)(b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    break;
                }
                shift += 7;
            }

            // Apply type-specific transformation
            switch (fieldType) {
                case "sint32":
                case "sint64":
                    // ZigZag decode
                    values.add((value >>> 1) ^ -(value & 1));
                    break;
                case "bool":
                    values.add(value != 0);
                    break;
                default:
                    values.add(value);
            }
        }

        return values;
    }

    /**
     * Unpack a packed repeated fixed32 field (fixed32, sfixed32, float).
     */
    private static Object unpackFixed32Array(ProtobufField field, String fieldType) {
        if (!(field.getValue() instanceof com.google.protobuf.ByteString)) {
            return null;
        }

        com.google.protobuf.ByteString bytes = (com.google.protobuf.ByteString) field.getValue();
        byte[] data = bytes.toByteArray();
        List<Object> values = new ArrayList<>();

        for (int pos = 0; pos + 3 < data.length; pos += 4) {
            int value = (data[pos] & 0xFF) |
                       ((data[pos + 1] & 0xFF) << 8) |
                       ((data[pos + 2] & 0xFF) << 16) |
                       ((data[pos + 3] & 0xFF) << 24);

            if ("float".equals(fieldType)) {
                values.add(Float.intBitsToFloat(value));
            } else {
                values.add(value);
            }
        }

        return values;
    }

    /**
     * Unpack a packed repeated fixed64 field (fixed64, sfixed64, double).
     */
    private static Object unpackFixed64Array(ProtobufField field, String fieldType) {
        if (!(field.getValue() instanceof com.google.protobuf.ByteString)) {
            return null;
        }

        com.google.protobuf.ByteString bytes = (com.google.protobuf.ByteString) field.getValue();
        byte[] data = bytes.toByteArray();
        List<Object> values = new ArrayList<>();

        for (int pos = 0; pos + 7 < data.length; pos += 8) {
            long value = (long)(data[pos] & 0xFF) |
                        ((long)(data[pos + 1] & 0xFF) << 8) |
                        ((long)(data[pos + 2] & 0xFF) << 16) |
                        ((long)(data[pos + 3] & 0xFF) << 24) |
                        ((long)(data[pos + 4] & 0xFF) << 32) |
                        ((long)(data[pos + 5] & 0xFF) << 40) |
                        ((long)(data[pos + 6] & 0xFF) << 48) |
                        ((long)(data[pos + 7] & 0xFF) << 56);

            if ("double".equals(fieldType)) {
                values.add(Double.longBitsToDouble(value));
            } else {
                values.add(value);
            }
        }

        return values;
    }
}
