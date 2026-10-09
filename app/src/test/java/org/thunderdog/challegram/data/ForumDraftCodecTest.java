package org.thunderdog.challegram.data;

import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ForumDraftCodecTest {
  @Test public void roundTripsAllDraftPrimitiveTypes () throws Exception {
    Map<String, Object> fields = new HashMap<>();
    fields.put("text", "Synthetic draft: Привет 📨\nSecond line");
    fields.put("reply", Long.MAX_VALUE);
    fields.put("offset", 15);
    fields.put("disabled", true);
    fields.put("url", null);
    assertEquals(fields, ForumDraftCodec.decode(ForumDraftCodec.encode(fields)));
  }

  @Test public void explicitClearHasAStoredRepresentation () throws Exception {
    byte[] clear = ForumDraftCodec.encode(new HashMap<>());
    assertNotNull(clear);
    assertTrue(ForumDraftCodec.decode(clear).isEmpty());
  }

  @Test public void encodingIsDeterministicForAcknowledgements () {
    Map<String, Object> a = new LinkedHashMap<>(), b = new LinkedHashMap<>();
    a.put("text", "A"); a.put("reply", 1L);
    b.put("reply", 1L); b.put("text", "A");
    assertArrayEquals(ForumDraftCodec.encode(a), ForumDraftCodec.encode(b));
    b.put("text", "B");
    assertFalse(java.util.Arrays.equals(ForumDraftCodec.encode(a), ForumDraftCodec.encode(b)));
  }

  @Test public void longTextIsNotLimitedByWriteUtf () throws Exception {
    Map<String, Object> fields = new HashMap<>();
    fields.put("text", new String(new char[100000]).replace('\0', 'x'));
    assertEquals(fields, ForumDraftCodec.decode(ForumDraftCodec.encode(fields)));
  }

  @Test(expected = IOException.class) public void truncatedDraftIsRejected () throws Exception { ForumDraftCodec.decode(new byte[] {0, 0, 0}); }
  @Test(expected = IOException.class) public void unknownVersionIsRejected () throws Exception { ForumDraftCodec.decode(new byte[] {0, 0, 0, 2, 0, 0, 0, 0}); }
  @Test(expected = IOException.class) public void negativeCountIsRejected () throws Exception { ForumDraftCodec.decode(new byte[] {0, 0, 0, 1, -1, -1, -1, -1}); }
  @Test(expected = IOException.class) public void trailingDataIsRejected () throws Exception { ForumDraftCodec.decode(new byte[] {0, 0, 0, 1, 0, 0, 0, 0, 1}); }
  @Test(expected = IOException.class) public void invalidLengthIsRejected () throws Exception { ForumDraftCodec.decode(new byte[] {0, 0, 0, 1, 0, 0, 0, 1, -1, -1, -1, -1, 0}); }
}
