package org.thunderdog.challegram.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/** Versioned, platform-independent storage for the primitive fields of TdBundle drafts. */
public final class ForumDraftCodec {
  private static final int VERSION = 1;
  private static final int MAX_BYTES = 16 * 1024 * 1024;
  private ForumDraftCodec () { }

  public static byte[] encode (Map<String, Object> fields) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bytes);
      out.writeInt(VERSION);
      out.writeInt(fields.size());
      for (Map.Entry<String, Object> field : new TreeMap<>(fields).entrySet()) {
        writeString(out, field.getKey());
        Object value = field.getValue();
        if (value == null) out.writeByte(0);
        else if (value instanceof String) { out.writeByte(1); writeString(out, (String) value); }
        else if (value instanceof Integer) { out.writeByte(2); out.writeInt((Integer) value); }
        else if (value instanceof Long) { out.writeByte(3); out.writeLong((Long) value); }
        else if (value instanceof Boolean) { out.writeByte(4); out.writeBoolean((Boolean) value); }
        else throw new IllegalArgumentException("Unsupported draft field type");
      }
      return bytes.toByteArray();
    } catch (IOException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  public static Map<String, Object> decode (byte[] bytes) throws IOException {
    if (bytes.length > MAX_BYTES) throw new IOException("Draft too large");
    DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
    if (in.readInt() != VERSION) throw new IOException("Unknown draft version");
    int count = in.readInt();
    if (count < 0 || count > bytes.length / 5) throw new IOException("Invalid field count");
    Map<String, Object> fields = new TreeMap<>();
    for (int i = 0; i < count; i++) {
      String key = readString(in);
      Object value;
      switch (in.readByte()) {
        case 0: value = null; break;
        case 1: value = readString(in); break;
        case 2: value = in.readInt(); break;
        case 3: value = in.readLong(); break;
        case 4: value = in.readBoolean(); break;
        default: throw new IOException("Unknown draft field type");
      }
      if (fields.containsKey(key)) throw new IOException("Duplicate draft field");
      fields.put(key, value);
    }
    if (in.available() != 0) throw new IOException("Trailing draft data");
    return fields;
  }

  private static void writeString (DataOutputStream out, String value) throws IOException {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    out.writeInt(bytes.length);
    out.write(bytes);
  }

  private static String readString (DataInputStream in) throws IOException {
    int length = in.readInt();
    if (length < 0 || length > in.available()) throw new IOException("Invalid draft field length");
    byte[] bytes = new byte[length];
    in.readFully(bytes);
    return new String(bytes, StandardCharsets.UTF_8);
  }
}
