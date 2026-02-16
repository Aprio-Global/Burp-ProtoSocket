# ProtoSocket

A Burp Suite extension for decoding and re-encoding Protocol Buffers (protobuf) inside WebSocket traffic.

## Features

- **Schema-Aware Protobuf Decoding**: Decode protobuf messages using .proto schema files
- **Required Schemas**: .proto files are required for all decoding and encoding operations (with import support)
- **WebSocket Interception**: Automatically intercepts binary WebSocket frames
- **Custom Editor Tab**: View decoded protobuf in a dedicated "Protobuf" tab in the Inspector pane
- **Text Search**: Search through decoded protobuf with find next/previous functionality
- **Message Editing**: Edit decoded protobuf as JSON and automatically re-encode with schema validation
- **Clean JSON Output**: Shows field values with proper types and field names from schema
- **Nested Messages**: Automatically decodes nested protobuf messages using schema type information
- **Background Processing**: Decoding happens in background threads to maintain Burp responsiveness

## Installation

1. Build the extension:
   ```bash
   ./gradlew jar
   ```

2. Load in Burp Suite:
   - Go to **Extensions** → **Installed** → **Add**
   - Select `build/libs/ProtoSocket.jar`
   - Click **Next**

3. Verify installation:
   - Check the **Extension output** tab for the message:
     ```
     ==================================================
     ProtoSocket extension loaded successfully!
     Monitoring WebSocket traffic for protobuf messages
     ==================================================
     ```

## Usage

### IMPORTANT: Load Schemas First

**Schemas are required before you can use this extension:**

1. Go to the **ProtoSocket Settings** tab in Burp's main UI
2. Click **"Load .proto File"** to load a single .proto file
3. Or click **"Load .proto Directory"** to load all .proto files from a folder
4. The parser automatically resolves `import` statements
5. View loaded message types in the status area

### Viewing and Decoding Protobuf Messages

1. Capture WebSocket traffic with binary protobuf messages using Burp Proxy
2. In the **Proxy** → **WebSocket history** tab, select a message
3. Look for the **Protobuf** tab in the message viewer
4. **Select a message type from the dropdown** to decode the message
5. View the decoded protobuf as formatted JSON with field names

### Editing and Re-encoding Messages

1. After selecting a schema and decoding, the JSON editor becomes editable
2. Modify field values as needed (field names match your .proto schema)
3. The extension automatically re-encodes the protobuf when you send/forward the message
4. All encoding uses the selected schema for type-safe re-encoding

### Understanding the JSON Format

Decoded protobuf messages are displayed as clean JSON using your schema:

```json
{
  "user_id": 42,
  "username": "Alice",
  "is_active": true,
  "profile": {
    "email": "alice@example.com",
    "age": 25
  }
}
```

Field names and types are determined by your .proto schema, making messages easy to read and edit.

## Wire Types

The extension supports the following protobuf wire types:

| Wire Type | Numeric Value | Typical Usage |
|-----------|---------------|---------------|
| VARINT | 0 | int32, int64, uint32, uint64, sint32, sint64, bool, enum |
| FIXED64 | 1 | fixed64, sfixed64, double |
| LENGTH_DELIMITED | 2 | string, bytes, embedded messages, packed repeated fields |
| START_GROUP | 3 | Group start (deprecated) |
| END_GROUP | 4 | Group end (deprecated) |
| FIXED32 | 5 | fixed32, sfixed32, float |

## Architecture

The extension is structured into the following packages:

- **core**: Core protobuf decoding/encoding logic and message storage
  - `ProtobufDecoder.java` - Decode/encode using Google's UnknownFieldSet
  - `ProtobufField.java` - Data model for decoded fields
  - `WebSocketMessageStore.java` - Thread-safe message storage with LRU eviction

- **handler**: WebSocket interception handlers
  - `ProtoWebSocketCreationHandler.java` - Registers handlers on new WebSockets
  - `ProtoMessageHandler.java` - Intercepts and processes binary messages

- **ui**: User interface components
  - `ProtobufMessageEditor.java` - Custom "Protobuf" tab implementation
  - `ProtobufEditorProvider.java` - Provides editor instances to Burp
  - `DecodedMessagePanel.java` - Swing panel with JSON editor

- **util**: Utility classes
  - `ProtobufFormatter.java` - Convert protobuf fields to/from JSON
  - `ThreadManager.java` - Background thread pool management

## Dependencies

All dependencies are bundled in the JAR:

- Google Protobuf Java 3.25.1
- org.json 20231013
- Burp Montoya API 2025.10 (provided by Burp)

## Troubleshooting

### "Protobuf" tab doesn't appear

- Ensure the WebSocket message contains valid protobuf binary data
- Check the extension output for decoding errors
- Verify that schemas are loaded in ProtoSocket Settings tab

### Cannot decode messages

- Load .proto schema files via ProtoSocket Settings tab
- Select the correct message type from the dropdown in the Protobuf tab
- Check that your .proto files match the protobuf version used by the application

### Extension not loading

- Verify you're using Burp Suite Professional or Community (2023.10+)
- Check for Java version compatibility (requires Java 21)

### Server rejects re-encoded messages

- Ensure you selected the correct schema type for the message
- Check Extension output tab for encoding errors
- Verify your .proto schema matches the server's expectation

## Limitations

- **Binary frames only**: Text WebSocket messages are not processed
- **Schema required**: Cannot decode protobuf without loading .proto files
- **Manual schema selection**: User must select the correct message type from dropdown for each message

## License

This extension is provided as-is for security testing purposes.

## Contributing

Contributions are welcome! Please ensure:
- Code follows existing patterns
- BApp Store requirements are met
- All dependencies are bundled
- Background threads are used for slow operations

## Credits

Built with:
- [Burp Suite Montoya API](https://portswigger.github.io/burp-extensions-montoya-api/)
- [Google Protocol Buffers](https://protobuf.dev/)
- [org.json](https://github.com/stleary/JSON-java)
