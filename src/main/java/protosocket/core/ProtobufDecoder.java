package protosocket.core;

import burp.api.montoya.MontoyaApi;
import protosocket.schema.ProtoSchemaManager;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.UnknownFieldSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Decodes and encodes protobuf messages with schema-aware processing.
 * Uses UnknownFieldSet for dynamic parsing combined with schema descriptors
 * to provide accurate field names and type information.
 * Schema is required for all decoding operations.
 */
public class ProtobufDecoder {
    private final MontoyaApi api;
    private final ProtoSchemaManager schemaManager;

    public ProtobufDecoder(MontoyaApi api, ProtoSchemaManager schemaManager) {
        this.api = api;
        this.schemaManager = schemaManager;
    }

    /**
     * Get the schema manager.
     */
    public ProtoSchemaManager getSchemaManager() {
        return schemaManager;
    }

    /**
     * Decode binary protobuf data with a required schema descriptor.
     * Schema is always required for decoding operations.
     *
     * @param data Raw protobuf binary data
     * @param rootMessageType Message type name for logging (e.g., "BackMsg")
     * @param descriptor Schema descriptor for the root message (required, not null)
     * @return List of decoded fields
     * @throws InvalidProtocolBufferException if data is not valid protobuf
     * @throws IllegalArgumentException if descriptor is null
     */
    public List<ProtobufField> decode(byte[] data, String rootMessageType, com.google.protobuf.Descriptors.Descriptor descriptor)
            throws InvalidProtocolBufferException {

        if (descriptor == null) {
            throw new IllegalArgumentException("Schema descriptor is required for decoding");
        }
        if (data == null || data.length == 0) {
            return new ArrayList<>();
        }

        api.logging().logToOutput("Decoding with schema: " + descriptor.getFullName());

        UnknownFieldSet fieldSet = UnknownFieldSet.parseFrom(data);

        // Parse and apply schema field names in one pass
        List<ProtobufField> fields = parseUnknownFieldSet(fieldSet, descriptor);
        applySchemaFieldNames(fields, descriptor);

        return fields;
    }

    /**
     * Recursively parse an UnknownFieldSet into ProtobufField objects.
     *
     * @param fieldSet The UnknownFieldSet to parse
     * @param messageDescriptor Optional schema descriptor for this message (null if not available)
     */
    private List<ProtobufField> parseUnknownFieldSet(UnknownFieldSet fieldSet,
                                                     com.google.protobuf.Descriptors.Descriptor messageDescriptor) {
        List<ProtobufField> fields = new ArrayList<>();

        for (Map.Entry<Integer, UnknownFieldSet.Field> entry : fieldSet.asMap().entrySet()) {
            int fieldNumber = entry.getKey();
            UnknownFieldSet.Field field = entry.getValue();

            // Extract varint values
            for (long varint : field.getVarintList()) {
                fields.add(new ProtobufField(fieldNumber, ProtobufField.VARINT, varint));
            }

            // Extract fixed32 values
            for (int fixed32 : field.getFixed32List()) {
                fields.add(new ProtobufField(fieldNumber, ProtobufField.FIXED32, fixed32));
            }

            // Extract fixed64 values
            for (long fixed64 : field.getFixed64List()) {
                fields.add(new ProtobufField(fieldNumber, ProtobufField.FIXED64, fixed64));
            }

            // Extract length-delimited values (bytes/strings/embedded messages)
            for (ByteString bytes : field.getLengthDelimitedList()) {
                // Look up field descriptor for schema-aware nested message detection
                com.google.protobuf.Descriptors.FieldDescriptor fieldDescriptor = null;
                if (messageDescriptor != null) {
                    fieldDescriptor = messageDescriptor.findFieldByNumber(fieldNumber);
                }

                List<ProtobufField> nested = tryDecodeNested(bytes, fieldDescriptor);
                fields.add(new ProtobufField(fieldNumber, ProtobufField.LENGTH_DELIMITED, bytes, nested));
            }

            // Handle groups (deprecated but still supported)
            for (UnknownFieldSet group : field.getGroupList()) {
                List<ProtobufField> groupFields = parseUnknownFieldSet(group, null);
                fields.add(new ProtobufField(fieldNumber, ProtobufField.START_GROUP, group, groupFields));
            }
        }

        return fields;
    }

    /**
     * Try to decode a ByteString as a nested protobuf message.
     * Schema information is always required and respected.
     *
     * @param bytes The bytes to potentially decode as a nested message
     * @param fieldDescriptor Schema descriptor for this field (required for nested messages)
     * @return List of decoded fields if this is a nested message, null otherwise
     */
    private List<ProtobufField> tryDecodeNested(ByteString bytes,
                                                com.google.protobuf.Descriptors.FieldDescriptor fieldDescriptor) {
        // Schema info is required
        if (fieldDescriptor == null) {
            return null;
        }

        // Only try to decode as nested if schema says it's a MESSAGE type
        if (fieldDescriptor.getType() != com.google.protobuf.Descriptors.FieldDescriptor.Type.MESSAGE) {
            // Schema says this is NOT a message (e.g., string, bytes, etc.)
            // Do not attempt nested decoding to avoid false positives
            return null;
        }

        // Schema says it's a message, try to decode with the message descriptor
        try {
            UnknownFieldSet nested = UnknownFieldSet.parseFrom(bytes);
            if (!nested.asMap().isEmpty()) {
                com.google.protobuf.Descriptors.Descriptor nestedDesc = fieldDescriptor.getMessageType();
                return parseUnknownFieldSet(nested, nestedDesc);
            }
        } catch (InvalidProtocolBufferException e) {
            api.logging().logToError("Failed to decode nested message for field #" +
                fieldDescriptor.getNumber() + " (" + fieldDescriptor.getName() + "): " + e.getMessage());
        }
        return null;
    }

    /**
     * Encode a list of ProtobufField objects back into binary protobuf data.
     *
     * @param fields List of fields to encode
     * @return Binary protobuf data
     * @throws IOException if encoding fails
     */
    public byte[] encode(List<ProtobufField> fields) throws IOException {
        api.logging().logToOutput("--- Encoding to Binary Protobuf ---");
        api.logging().logToOutput("Encoding " + fields.size() + " root-level fields");

        UnknownFieldSet.Builder builder = UnknownFieldSet.newBuilder();

        // Group fields by field number (protobuf can have multiple values for same field)
        for (ProtobufField field : fields) {
            api.logging().logToOutput("  Encoding field #" + field.getFieldNumber() +
                " (wire_type=" + field.getWireType() +
                ", hasNested=" + field.hasNestedFields() + ")");
            addFieldToBuilder(builder, field);
        }

        byte[] result = builder.build().toByteArray();
        api.logging().logToOutput("✓ Encoded to " + result.length + " bytes");

        // Log hex preview of encoded bytes
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < Math.min(result.length, 64); i++) {
            hex.append(String.format("%02x ", result[i]));
        }
        if (result.length > 64) hex.append("...");
        api.logging().logToOutput("Encoded bytes (first 64): " + hex.toString());

        return result;
    }

    /**
     * Add a single field to the UnknownFieldSet builder.
     */
    private void addFieldToBuilder(UnknownFieldSet.Builder builder, ProtobufField field) throws IOException {
        int fieldNumber = field.getFieldNumber();
        int wireType = field.getWireType();
        Object value = field.getValue();

        UnknownFieldSet.Field.Builder fieldBuilder = UnknownFieldSet.Field.newBuilder();

        switch (wireType) {
            case ProtobufField.VARINT:
                if (value instanceof Number) {
                    fieldBuilder.addVarint(((Number) value).longValue());
                }
                break;

            case ProtobufField.FIXED32:
                if (value instanceof Number) {
                    fieldBuilder.addFixed32(((Number) value).intValue());
                }
                break;

            case ProtobufField.FIXED64:
                if (value instanceof Number) {
                    fieldBuilder.addFixed64(((Number) value).longValue());
                }
                break;

            case ProtobufField.LENGTH_DELIMITED:
                // If it has nested fields, encode them recursively
                if (field.hasNestedFields()) {
                    api.logging().logToOutput("    ↳ Recursively encoding nested message with " +
                        field.getNestedFields().size() + " fields");
                    byte[] nestedBytes = encode(field.getNestedFields());
                    api.logging().logToOutput("    ↳ Nested message encoded to " + nestedBytes.length + " bytes");
                    fieldBuilder.addLengthDelimited(ByteString.copyFrom(nestedBytes));
                } else if (value instanceof ByteString) {
                    api.logging().logToOutput("    ↳ Adding ByteString (" + ((ByteString)value).size() + " bytes)");
                    fieldBuilder.addLengthDelimited((ByteString) value);
                } else if (value instanceof byte[]) {
                    api.logging().logToOutput("    ↳ Adding byte[] (" + ((byte[])value).length + " bytes)");
                    fieldBuilder.addLengthDelimited(ByteString.copyFrom((byte[]) value));
                } else if (value instanceof String) {
                    api.logging().logToOutput("    ↳ Adding String (" + ((String)value).length() + " chars)");
                    fieldBuilder.addLengthDelimited(ByteString.copyFromUtf8((String) value));
                }
                break;

            case ProtobufField.START_GROUP:
                // Handle groups (deprecated)
                if (field.hasNestedFields()) {
                    UnknownFieldSet.Builder groupBuilder = UnknownFieldSet.newBuilder();
                    for (ProtobufField nestedField : field.getNestedFields()) {
                        addFieldToBuilder(groupBuilder, nestedField);
                    }
                    fieldBuilder.addGroup(groupBuilder.build());
                }
                break;

            default:
                api.logging().logToError("Unknown wire type: " + wireType);
        }

        builder.mergeField(fieldNumber, fieldBuilder.build());
    }

    /**
     * Apply field names from schema to decoded fields.
     * Schema descriptor is always required (passed from decode() method).
     * Recursively processes ALL nested fields with their proper context.
     */
    private void applySchemaFieldNames(List<ProtobufField> fields, com.google.protobuf.Descriptors.Descriptor descriptor) {
        if (fields == null || fields.isEmpty() || descriptor == null) {
            return;
        }

        api.logging().logToOutput("Applying schema field names from descriptor: " + descriptor.getFullName());

        if (descriptor != null) {
            // Apply field names AND types for this level
            for (ProtobufField field : fields) {
                com.google.protobuf.Descriptors.FieldDescriptor fieldDescriptor =
                    descriptor.findFieldByNumber(field.getFieldNumber());

                if (fieldDescriptor != null) {
                    // Set field name
                    field.setFieldName(fieldDescriptor.getName());

                    // Set field type information
                    String protoType = fieldDescriptor.getType().toString().toLowerCase();
                    protoType = protoType.replace("type_", ""); // "TYPE_INT32" -> "int32"
                    field.setFieldType(protoType);

                    // Set message type information
                    if (fieldDescriptor.getType() == com.google.protobuf.Descriptors.FieldDescriptor.Type.MESSAGE) {
                        field.setIsMessageType(true);
                        field.setMessageTypeName(fieldDescriptor.getMessageType().getFullName());
                    } else {
                        field.setIsMessageType(false);
                    }
                }
            }
        }

        // Recurse into nested fields with CONTEXT from this level
        for (ProtobufField field : fields) {
            if (field.hasNestedFields()) {
                String fieldName = field.getFieldName() != null ?
                    field.getFieldName() : "field_" + field.getFieldNumber();

                // CONTEXT-AWARE: Determine child descriptor from parent
                com.google.protobuf.Descriptors.Descriptor childDescriptor = null;

                if (descriptor != null) {
                    com.google.protobuf.Descriptors.FieldDescriptor fieldDescriptor = descriptor.findFieldByNumber(field.getFieldNumber());
                    if (fieldDescriptor != null &&
                        fieldDescriptor.getType() == com.google.protobuf.Descriptors.FieldDescriptor.Type.MESSAGE) {
                        // Use the explicit message type from schema
                        childDescriptor = fieldDescriptor.getMessageType();
                        api.logging().logToOutput("  Recursing into " + fieldName +
                                                 " with explicit type: " + childDescriptor.getFullName());
                    } else {
                        api.logging().logToOutput("  Recursing into " + fieldName + " (no type context)");
                    }
                } else {
                    api.logging().logToOutput("  Recursing into " + fieldName + " (no parent context)");
                }

                applySchemaFieldNames(field.getNestedFields(), childDescriptor);
            }
        }
    }

    /**
     * Check if binary data appears to be valid protobuf.
     * This is a simple heuristic check.
     */
    public boolean isValidProtobuf(byte[] data) {
        if (data == null || data.length == 0) {
            return false;
        }

        try {
            UnknownFieldSet fieldSet = UnknownFieldSet.parseFrom(data);
            // Consider it valid if we can parse it and it has at least one field
            return !fieldSet.asMap().isEmpty();
        } catch (InvalidProtocolBufferException e) {
            return false;
        }
    }
}
