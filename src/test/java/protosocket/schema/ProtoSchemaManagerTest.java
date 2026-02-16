package protosocket.schema;

import burp.api.montoya.MontoyaApi;
import protosocket.TestHelper;
import com.google.protobuf.Descriptors.*;
import com.google.protobuf.DescriptorProtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProtoSchemaManager.
 */
class ProtoSchemaManagerTest {

    private MontoyaApi mockApi;
    private ProtoSchemaManager manager;

    @BeforeEach
    void setUp() {
        mockApi = TestHelper.createMockApi();
        manager = new ProtoSchemaManager(mockApi);
    }

    @Test
    @DisplayName("Initially no schemas are loaded")
    void testInitiallyEmpty() {
        assertFalse(manager.hasSchemasLoaded());
        assertTrue(manager.getAllMessageTypeNames().isEmpty());
    }

    @Test
    @DisplayName("Can register FileDescriptor and retrieve by name")
    void testRegisterAndRetrieveFileDescriptor() throws Exception {
        DescriptorProto messageProto = TestHelper.createTestMessageDescriptor("TestMessage",
                TestHelper.createTestFieldDescriptor("id", 1, FieldDescriptorProto.Type.TYPE_INT32));

        FileDescriptor fileDescriptor = TestHelper.createTestFileDescriptor(
                "com.test", "test.proto", messageProto);

        manager.registerFileDescriptor(fileDescriptor);

        assertTrue(manager.hasSchemasLoaded());
        FileDescriptor retrieved = manager.getFileDescriptor("test.proto");
        assertNotNull(retrieved);
        assertEquals("test.proto", retrieved.getName());
    }

    @Test
    @DisplayName("Can retrieve message descriptor by simple name")
    void testGetMessageDescriptorBySimpleName() throws Exception {
        DescriptorProto messageProto = TestHelper.createTestMessageDescriptor("User",
                TestHelper.createTestFieldDescriptor("id", 1, FieldDescriptorProto.Type.TYPE_INT32),
                TestHelper.createTestFieldDescriptor("name", 2, FieldDescriptorProto.Type.TYPE_STRING));

        FileDescriptor fileDescriptor = TestHelper.createTestFileDescriptor(
                "com.example", "user.proto", messageProto);

        manager.registerFileDescriptor(fileDescriptor);

        Descriptor descriptor = manager.getMessageDescriptor("User");
        assertNotNull(descriptor);
        assertEquals("User", descriptor.getName());
    }

    @Test
    @DisplayName("Can retrieve message descriptor by fully qualified name")
    void testGetMessageDescriptorByFullName() throws Exception {
        DescriptorProto messageProto = TestHelper.createTestMessageDescriptor("User",
                TestHelper.createTestFieldDescriptor("id", 1, FieldDescriptorProto.Type.TYPE_INT32));

        FileDescriptor fileDescriptor = TestHelper.createTestFileDescriptor(
                "com.example", "user.proto", messageProto);

        manager.registerFileDescriptor(fileDescriptor);

        Descriptor descriptor = manager.getMessageDescriptor("com.example.User");
        assertNotNull(descriptor);
        assertEquals("com.example.User", descriptor.getFullName());
    }

    @Test
    @DisplayName("Returns null for unknown message type")
    void testGetMessageDescriptorNotFound() {
        Descriptor descriptor = manager.getMessageDescriptor("UnknownMessage");
        assertNull(descriptor);
    }

    @Test
    @DisplayName("Get all message type names returns all registered types")
    void testGetAllMessageTypeNames() throws Exception {
        DescriptorProto message1 = TestHelper.createTestMessageDescriptor("User");
        DescriptorProto message2 = TestHelper.createTestMessageDescriptor("Post");

        FileDescriptor fileDescriptor = TestHelper.createTestFileDescriptor(
                "com.example", "messages.proto", message1, message2);

        manager.registerFileDescriptor(fileDescriptor);

        Set<String> allNames = manager.getAllMessageTypeNames();

        // Should contain both simple names and fully qualified names
        assertTrue(allNames.contains("User"));
        assertTrue(allNames.contains("Post"));
        assertTrue(allNames.contains("com.example.User"));
        assertTrue(allNames.contains("com.example.Post"));
    }

    @Test
    @DisplayName("Clear removes all schemas")
    void testClear() throws Exception {
        DescriptorProto messageProto = TestHelper.createTestMessageDescriptor("User");
        FileDescriptor fileDescriptor = TestHelper.createTestFileDescriptor(
                "com.example", "user.proto", messageProto);

        manager.registerFileDescriptor(fileDescriptor);
        assertTrue(manager.hasSchemasLoaded());

        manager.clear();

        assertFalse(manager.hasSchemasLoaded());
        assertTrue(manager.getAllMessageTypeNames().isEmpty());
        assertNull(manager.getLastSelectedSchema());
    }

    @Test
    @DisplayName("Search paths can be added and retrieved")
    void testSearchPathManagement() {
        assertTrue(manager.getSearchPaths().isEmpty());

        manager.addSearchPath("/path/to/protos");
        manager.addSearchPath("/another/path");

        assertEquals(2, manager.getSearchPaths().size());
        assertTrue(manager.getSearchPaths().contains("/path/to/protos"));
        assertTrue(manager.getSearchPaths().contains("/another/path"));

        // Adding duplicate should not increase size
        manager.addSearchPath("/path/to/protos");
        assertEquals(2, manager.getSearchPaths().size());
    }

    @Test
    @DisplayName("RequireSchemasLoaded throws when no schemas")
    void testRequireSchemasLoadedThrowsWhenEmpty() {
        assertThrows(IllegalStateException.class, () -> manager.requireSchemasLoaded());
    }

    @Test
    @DisplayName("RequireSchemasLoaded succeeds when schemas loaded")
    void testRequireSchemasLoadedSucceeds() throws Exception {
        DescriptorProto messageProto = TestHelper.createTestMessageDescriptor("User");
        FileDescriptor fileDescriptor = TestHelper.createTestFileDescriptor(
                "com.example", "user.proto", messageProto);

        manager.registerFileDescriptor(fileDescriptor);

        assertDoesNotThrow(() -> manager.requireSchemasLoaded());
    }

    @Test
    @DisplayName("Last selected schema can be tracked")
    void testLastSelectedSchemaTracking() {
        assertNull(manager.getLastSelectedSchema());

        manager.setLastSelectedSchema("User");
        assertEquals("User", manager.getLastSelectedSchema());

        manager.setLastSelectedSchema("Post");
        assertEquals("Post", manager.getLastSelectedSchema());

        manager.clear();
        assertNull(manager.getLastSelectedSchema());
    }

    @Test
    @DisplayName("Get stats returns correct counts")
    void testGetStats() throws Exception {
        String initialStats = manager.getStats();
        assertTrue(initialStats.contains("0 files"));
        assertTrue(initialStats.contains("0 message types"));

        DescriptorProto message1 = TestHelper.createTestMessageDescriptor("User");
        DescriptorProto message2 = TestHelper.createTestMessageDescriptor("Post");

        FileDescriptor fileDescriptor = TestHelper.createTestFileDescriptor(
                "com.example", "messages.proto", message1, message2);

        manager.registerFileDescriptor(fileDescriptor);

        String stats = manager.getStats();
        assertTrue(stats.contains("1 files"));
        assertTrue(stats.contains("message types"));
    }
}
