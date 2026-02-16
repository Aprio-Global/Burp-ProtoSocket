package protosocket.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProtobufField data model.
 */
class ProtobufFieldTest {

    @Test
    @DisplayName("Constructor creates simple field without nested fields")
    void testConstructorSimple() {
        ProtobufField field = new ProtobufField(1, ProtobufField.VARINT, 42L);

        assertEquals(1, field.getFieldNumber());
        assertEquals(ProtobufField.VARINT, field.getWireType());
        assertEquals(42L, field.getValue());
        assertFalse(field.hasNestedFields());
        assertEquals(0, field.getNestedFields().size());
    }

    @Test
    @DisplayName("Constructor creates field with nested fields")
    void testConstructorWithNested() {
        List<ProtobufField> nested = new ArrayList<>();
        nested.add(new ProtobufField(1, ProtobufField.VARINT, 100L));
        nested.add(new ProtobufField(2, ProtobufField.LENGTH_DELIMITED, "test"));

        ProtobufField field = new ProtobufField(3, ProtobufField.LENGTH_DELIMITED, new byte[0], nested);

        assertEquals(3, field.getFieldNumber());
        assertEquals(ProtobufField.LENGTH_DELIMITED, field.getWireType());
        assertTrue(field.hasNestedFields());
        assertEquals(2, field.getNestedFields().size());
    }

    @Test
    @DisplayName("Field name can be set and retrieved")
    void testFieldName() {
        ProtobufField field = new ProtobufField(1, ProtobufField.VARINT, 42L);

        assertNull(field.getFieldName());
        field.setFieldName("userId");
        assertEquals("userId", field.getFieldName());
    }

    @Test
    @DisplayName("Field type can be set and retrieved")
    void testFieldType() {
        ProtobufField field = new ProtobufField(1, ProtobufField.VARINT, 42L);

        assertNull(field.getFieldType());
        assertFalse(field.hasSchemaTypeInfo());

        field.setFieldType("int32");
        assertEquals("int32", field.getFieldType());
        assertTrue(field.hasSchemaTypeInfo());
    }

    @Test
    @DisplayName("Message type information can be set and retrieved")
    void testMessageTypeInfo() {
        ProtobufField field = new ProtobufField(1, ProtobufField.LENGTH_DELIMITED, new byte[0]);

        assertFalse(field.isMessageType());
        assertNull(field.getMessageTypeName());

        field.setIsMessageType(true);
        field.setMessageTypeName("User");

        assertTrue(field.isMessageType());
        assertEquals("User", field.getMessageTypeName());
    }

    @Test
    @DisplayName("Wire type names are correctly mapped")
    void testGetWireTypeName() {
        assertEquals("VARINT", ProtobufField.getWireTypeName(ProtobufField.VARINT));
        assertEquals("FIXED64", ProtobufField.getWireTypeName(ProtobufField.FIXED64));
        assertEquals("LENGTH_DELIMITED", ProtobufField.getWireTypeName(ProtobufField.LENGTH_DELIMITED));
        assertEquals("START_GROUP", ProtobufField.getWireTypeName(ProtobufField.START_GROUP));
        assertEquals("END_GROUP", ProtobufField.getWireTypeName(ProtobufField.END_GROUP));
        assertEquals("FIXED32", ProtobufField.getWireTypeName(ProtobufField.FIXED32));
        assertEquals("UNKNOWN(99)", ProtobufField.getWireTypeName(99));
    }

    @Test
    @DisplayName("toString provides readable representation for simple field")
    void testToStringSimple() {
        ProtobufField field = new ProtobufField(1, ProtobufField.VARINT, 42L);
        String result = field.toString();

        assertTrue(result.contains("Field 1"));
        assertTrue(result.contains("VARINT"));
        assertTrue(result.contains("42"));
    }

    @Test
    @DisplayName("toString provides readable representation for nested field")
    void testToStringNested() {
        List<ProtobufField> nested = new ArrayList<>();
        nested.add(new ProtobufField(1, ProtobufField.VARINT, 100L));

        ProtobufField field = new ProtobufField(3, ProtobufField.LENGTH_DELIMITED, new byte[0], nested);
        String result = field.toString();

        assertTrue(result.contains("Field 3"));
        assertTrue(result.contains("LENGTH_DELIMITED"));
        assertTrue(result.contains("nested message"));
        assertTrue(result.contains("1 fields"));
    }
}
