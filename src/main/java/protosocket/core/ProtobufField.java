package protosocket.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a decoded protobuf field with field number, wire type, and value.
 */
public class ProtobufField {
    // Wire type constants from protobuf specification
    public static final int VARINT = 0;
    public static final int FIXED64 = 1;
    public static final int LENGTH_DELIMITED = 2;
    public static final int START_GROUP = 3;  // Deprecated
    public static final int END_GROUP = 4;    // Deprecated
    public static final int FIXED32 = 5;

    private final int fieldNumber;
    private final int wireType;
    private final Object value;
    private final List<ProtobufField> nestedFields;
    private String fieldName; // Optional field name from schema
    private String fieldType; // Optional field type from schema ("int32", "string", "message", etc.)
    private boolean isMessage; // true if this field is TYPE_MESSAGE
    private String messageTypeName; // For nested messages

    /**
     * Constructor for simple fields without nested messages.
     */
    public ProtobufField(int fieldNumber, int wireType, Object value) {
        this(fieldNumber, wireType, value, null);
    }

    /**
     * Constructor for fields that may contain nested messages.
     */
    public ProtobufField(int fieldNumber, int wireType, Object value, List<ProtobufField> nestedFields) {
        this.fieldNumber = fieldNumber;
        this.wireType = wireType;
        this.value = value;
        this.nestedFields = nestedFields != null ? nestedFields : new ArrayList<>();
        this.fieldName = null;
    }

    public int getFieldNumber() {
        return fieldNumber;
    }

    public int getWireType() {
        return wireType;
    }

    public Object getValue() {
        return value;
    }

    public List<ProtobufField> getNestedFields() {
        return nestedFields;
    }

    public boolean hasNestedFields() {
        return nestedFields != null && !nestedFields.isEmpty();
    }

    /**
     * Get the field name from schema (if available).
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * Set the field name from schema.
     */
    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    /**
     * Get the field type from schema (if available).
     */
    public String getFieldType() {
        return fieldType;
    }

    /**
     * Set the field type from schema.
     */
    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    /**
     * Check if this field is a message type.
     */
    public boolean isMessageType() {
        return isMessage;
    }

    /**
     * Set whether this field is a message type.
     */
    public void setIsMessageType(boolean isMessage) {
        this.isMessage = isMessage;
    }

    /**
     * Get the message type name (for nested messages).
     */
    public String getMessageTypeName() {
        return messageTypeName;
    }

    /**
     * Set the message type name.
     */
    public void setMessageTypeName(String messageTypeName) {
        this.messageTypeName = messageTypeName;
    }

    /**
     * Check if schema type information is available.
     */
    public boolean hasSchemaTypeInfo() {
        return fieldType != null;
    }

    /**
     * Get wire type name as string for display.
     */
    public static String getWireTypeName(int wireType) {
        switch (wireType) {
            case VARINT:
                return "VARINT";
            case FIXED64:
                return "FIXED64";
            case LENGTH_DELIMITED:
                return "LENGTH_DELIMITED";
            case START_GROUP:
                return "START_GROUP";
            case END_GROUP:
                return "END_GROUP";
            case FIXED32:
                return "FIXED32";
            default:
                return "UNKNOWN(" + wireType + ")";
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Field ").append(fieldNumber).append(" [").append(getWireTypeName(wireType)).append("]: ");
        if (hasNestedFields()) {
            sb.append("nested message with ").append(nestedFields.size()).append(" fields");
        } else {
            sb.append(value);
        }
        return sb.toString();
    }
}
