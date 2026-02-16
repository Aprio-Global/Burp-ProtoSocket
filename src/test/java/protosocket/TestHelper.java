package protosocket;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.extension.Extension;
import burp.api.montoya.logging.Logging;
import protosocket.core.ProtobufField;
import com.google.protobuf.Descriptors.*;
import com.google.protobuf.DescriptorProtos.*;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test utilities for creating mocks and test data.
 */
public class TestHelper {

    /**
     * Create a mock MontoyaApi with logging that outputs to console.
     */
    public static MontoyaApi createMockApi() {
        MontoyaApi api = mock(MontoyaApi.class);
        Logging logging = mock(Logging.class);
        Extension extension = mock(Extension.class);

        when(api.logging()).thenReturn(logging);
        when(api.extension()).thenReturn(extension);

        // Print logs to console for debugging
        doAnswer(invocation -> {
            System.out.println("[TEST LOG] " + invocation.getArgument(0));
            return null;
        }).when(logging).logToOutput(anyString());

        doAnswer(invocation -> {
            System.err.println("[TEST ERROR] " + invocation.getArgument(0));
            return null;
        }).when(logging).logToError(anyString());

        return api;
    }

    /**
     * Create a test FileDescriptor programmatically.
     */
    public static FileDescriptor createTestFileDescriptor(String packageName, String fileName,
                                                          DescriptorProto... messages) throws Exception {
        FileDescriptorProto.Builder fileProtoBuilder = FileDescriptorProto.newBuilder()
                .setName(fileName)
                .setPackage(packageName);

        for (DescriptorProto message : messages) {
            fileProtoBuilder.addMessageType(message);
        }

        FileDescriptorProto fileProto = fileProtoBuilder.build();
        return FileDescriptor.buildFrom(fileProto, new FileDescriptor[0]);
    }

    /**
     * Create a test message descriptor.
     */
    public static DescriptorProto createTestMessageDescriptor(String messageName, FieldDescriptorProto... fields) {
        DescriptorProto.Builder builder = DescriptorProto.newBuilder()
                .setName(messageName);

        for (FieldDescriptorProto field : fields) {
            builder.addField(field);
        }

        return builder.build();
    }

    /**
     * Create a test field descriptor.
     */
    public static FieldDescriptorProto createTestFieldDescriptor(String name, int number,
                                                                  FieldDescriptorProto.Type type) {
        return FieldDescriptorProto.newBuilder()
                .setName(name)
                .setNumber(number)
                .setType(type)
                .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                .build();
    }

    /**
     * Create a test field descriptor for a repeated field.
     */
    public static FieldDescriptorProto createRepeatedFieldDescriptor(String name, int number,
                                                                      FieldDescriptorProto.Type type) {
        return FieldDescriptorProto.newBuilder()
                .setName(name)
                .setNumber(number)
                .setType(type)
                .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                .build();
    }

    /**
     * Create a test field descriptor for a message field.
     */
    public static FieldDescriptorProto createMessageFieldDescriptor(String name, int number, String typeName) {
        return FieldDescriptorProto.newBuilder()
                .setName(name)
                .setNumber(number)
                .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                .setTypeName(typeName)
                .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                .build();
    }

    /**
     * Convert byte array to hex string.
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Convert hex string to byte array.
     */
    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Create a temporary .proto file for testing.
     */
    public static Path createTempProtoFile(Path tempDir, String fileName, String content) throws IOException {
        Path protoFile = tempDir.resolve(fileName);
        Files.writeString(protoFile, content);
        return protoFile;
    }

    /**
     * Assert that two ProtobufField objects are equal (deep comparison).
     */
    public static void assertProtobufFieldEquals(ProtobufField expected, ProtobufField actual) {
        if (expected == null && actual == null) {
            return;
        }
        if (expected == null || actual == null) {
            throw new AssertionError("One field is null: expected=" + expected + ", actual=" + actual);
        }

        if (expected.getFieldNumber() != actual.getFieldNumber()) {
            throw new AssertionError("Field numbers differ: expected=" + expected.getFieldNumber()
                    + ", actual=" + actual.getFieldNumber());
        }

        if (expected.getWireType() != actual.getWireType()) {
            throw new AssertionError("Wire types differ: expected=" + expected.getWireType()
                    + ", actual=" + actual.getWireType());
        }

        // Compare values (handle byte arrays specially)
        Object expectedValue = expected.getValue();
        Object actualValue = actual.getValue();
        if (expectedValue instanceof byte[] && actualValue instanceof byte[]) {
            byte[] expectedBytes = (byte[]) expectedValue;
            byte[] actualBytes = (byte[]) actualValue;
            if (!java.util.Arrays.equals(expectedBytes, actualBytes)) {
                throw new AssertionError("Byte array values differ");
            }
        } else if (!java.util.Objects.equals(expectedValue, actualValue)) {
            throw new AssertionError("Values differ: expected=" + expectedValue + ", actual=" + actualValue);
        }

        // Compare nested fields
        if (expected.hasNestedFields() != actual.hasNestedFields()) {
            throw new AssertionError("Nested fields presence differs");
        }

        if (expected.hasNestedFields()) {
            List<ProtobufField> expectedNested = expected.getNestedFields();
            List<ProtobufField> actualNested = actual.getNestedFields();

            if (expectedNested.size() != actualNested.size()) {
                throw new AssertionError("Nested field count differs: expected=" + expectedNested.size()
                        + ", actual=" + actualNested.size());
            }

            for (int i = 0; i < expectedNested.size(); i++) {
                assertProtobufFieldEquals(expectedNested.get(i), actualNested.get(i));
            }
        }
    }

    /**
     * Create a simple protobuf message with varint field.
     * Format: field 1, varint (wire type 0), value 42
     * Encoding: 08 2a
     */
    public static byte[] createSimpleProtobufMessage() {
        return hexToBytes("082a"); // field 1, varint, value 42
    }

    /**
     * Create a protobuf message with a string field.
     * Format: field 2, string (wire type 2), value "test"
     * Encoding: 12 04 74657374
     */
    public static byte[] createStringProtobufMessage() {
        return hexToBytes("120474657374"); // field 2, length-delimited, "test"
    }

    /**
     * Create a protobuf message with nested message.
     * Format: field 3, nested message (wire type 2) containing field 1, varint, value 100
     * Encoding: 1a 02 08 64
     */
    public static byte[] createNestedProtobufMessage() {
        return hexToBytes("1a020864"); // field 3, nested message with field 1, varint 100
    }
}
