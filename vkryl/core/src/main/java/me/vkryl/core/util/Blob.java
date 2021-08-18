/**
 * File created on 05/04/15 at 08:47
 * Copyright Vyacheslav Krylov, 2014
 */
package me.vkryl.core.util;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.zip.CRC32;

import me.vkryl.core.StringUtils;

public final class Blob {

  private int index;
  private byte[] data;

  private CRC32 crc32;

  public Blob () { }

  public Blob (int size) {
    data = new byte[size];
  }

  public Blob (byte[] source) {
    this.data = source;
  }

  public void reset (int size) {
    index = 0;
    data = new byte[size];
  }

  public void reset (byte[] source) {
    index = 0;
    data = source;
  }

  public void reset (Blob source) {
    index = 0;
    data = source.data;
  }

  public void destroy () {
    index = 0;
    data = null;
  }

  public void seekToStart () {
    index = 0;
  }

  public void trim () {
    if (index != data.length) {
      data = Arrays.copyOf(data, index);
    }
  }

  public byte[] toByteArray () {
    trim();
    return data;
  }

  public int size () {
    return data != null ? data.length : 0;
  }

  public int remaining () {
    return size() - index;
  }

  /* Public utils */

  public void ensureCapacity (int more) {
    if (data == null) {
      data = new byte[more];
    } else if (index + more > data.length) {
      data = Arrays.copyOf(data, index + more);
    }
  }

  public void checkCapacity (int more) {
    if (data == null) {
      throw new RuntimeException("Blob.data == null");
    } else if (index + more > data.length) {
      throw new RuntimeException("Blob.data.length < index + more");
    }
  }

  /* Private utils */

  private void arrange (int capacity) {
    if (data == null) {
      data = new byte[capacity];
    } else if (index + capacity > data.length) {
      data = Arrays.copyOf(data, index + capacity);
    }
  }

  /* Input methods */

  public void writeByte (byte i) {
    arrange(1);
    data[index] = i;
    index++;
  }

  public boolean writeBoolean (boolean i) {
    writeByte((byte) (i ? 1 : 0));
    return i;
  }

  public void writeRaw (byte[] i) {
    arrange(i.length);
    for (byte j : i) {
      data[index] = j;
      index++;
    }
  }

  public void writeRaw (byte[] i, int x) {
    arrange(x);
    for (int j = 0; j < x; j++) {
      data[index] = i[j];
      index++;
    }
  }

  public void writeInt16 (int i) {
    arrange(2);
    data[index] = (byte) ((i >> 8) & 0xff);
    index++;
    data[index] = (byte) (i & 0xff);
    index++;
  }

  public void writeInt (int i) {
    arrange(4);
    writeInt(data, index, i);
    index += 4;
  }

  public static void writeInt (byte[] data, int index, int i) {
    data[index] = (byte) ((i >> 24) & 0xff);
    index++;
    data[index] = (byte) ((i >> 16) & 0xff);
    index++;
    data[index] = (byte) ((i >> 8) & 0xff);
    index++;
    data[index] = (byte) (i & 0xff);
    index++;
  }

  public static void writeFloat (byte[] data, int index, float i) {
    writeInt(data, index, Float.floatToIntBits(i));
  }

  public void writeFloat (float i) {
    writeInt(Float.floatToIntBits(i));
  }

  public static void writeVarint (RandomAccessFile file, int i) throws IOException {
    while ((i & 0xffffff80) != 0l) {
      file.write(((i & 0x7f) | 0x80));
      i >>>= 7;
    }
    file.write((i & 0x7f));
  }

  public void writeVarint (int i) {
    arrange(sizeOf(i));
    while ((i & 0xffffff80) != 0l) {
      data[index] = (byte) ((i & 0x7f) | 0x80);
      index++;
      i >>>= 7;
    }
    data[index] = (byte) (i & 0x7f);
    index++;
  }

  public void writeVarLong (long i) {
    arrange(sizeOf(i));
    i = encodeZigZag64(i);
    while (true) {
      if ((i & ~0x7FL) == 0) {
        data[index++] = (byte) i;
        return;
      } else {
        data[index++] = (byte) (((int) i & 0x7F) | 0x80);
        i >>>= 7;
      }
    }
  }

  public void writeLong (long i) {
    arrange(8);
    writeLong(data, index, i);
    index += 8;
  }

  public static void writeLong (byte[] data, int index, long i) {
    data[index] = (byte) ((i >>> 56) & 0xff);
    index++;
    data[index] = (byte) ((i >>> 48) & 0xff);
    index++;
    data[index] = (byte) ((i >>> 40) & 0xff);
    index++;
    data[index] = (byte) ((i >>> 32) & 0xff);
    index++;
    data[index] = (byte) ((i >>> 24) & 0xff);
    index++;
    data[index] = (byte) ((i >>> 16) & 0xff);
    index++;
    data[index] = (byte) ((i >>> 8) & 0xff);
    index++;
    data[index] = (byte) (i & 0xff);
    index++;
  }

  public static void writeDouble (byte[] data, int index, double i) {
    writeLong(data, index, Double.doubleToLongBits(i));
  }

  public void writeDouble (double i) {
    writeLong(Double.doubleToLongBits(i));
  }

  public void writeByteArray (byte[] i) {
    arrange(sizeOf(i.length) + i.length);
    writeVarint(i.length);
    for (byte j : i) {
      data[index] = j;
      index++;
    }
  }

  public void writeIntArray (int[] i) {
    arrange(sizeOf(i.length) + i.length * 4);
    writeVarint(i.length);
    for (int j : i) {
      writeInt(j);
    }
  }

  public void writeLongArray (long[] i) {
    arrange(sizeOf(i.length) + i.length * 8);
    writeVarint(i.length);
    for (long j : i) {
      writeLong(j);
    }
  }

  public void writeString (String i) {
    byte[] rawString;
    rawString = i.getBytes(StringUtils.UTF_8);
    writeByteArray(rawString);
  }

  /* Output methods */

  public static byte readByte (RandomAccessFile file) throws IOException {
    return (byte) file.read();
  }

  public byte readByte () {
    checkCapacity(1);
    return data[index++];
  }

  public boolean readBoolean () {
    return readByte() == 1;
  }

  public byte[] readRaw () {
    if (index == 0)
      return data;
    checkCapacity(data.length - index);
    byte[] i = Arrays.copyOfRange(data, index, data.length);
    index = data.length;
    return i;
  }

  public byte[] readRaw (int size) {
    checkCapacity(size);
    byte[] i = Arrays.copyOfRange(data, index, index + size);
    index += size;
    return i;
  }

  public int readInt16 () {
    checkCapacity(2);
    return (((0xff & data[index++]) << 8) | (0xff & data[index++]));
  }

  public int readInt () {
    checkCapacity(4);
    return ((0xff & data[index++]) << 24) | ((0xff & data[index++]) << 16) |
      ((0xff & data[index++]) << 8) | (0xff & data[index++]);
  }

  public float readFloat () {
    return Float.intBitsToFloat(readInt());
  }

  public static int readVarint (RandomAccessFile file) throws IOException {
    int value = 0;
    int i = 0;
    int b;
    while (((b = file.read()) & 0x80) != 0) {
      value |= (b & 0x7F) << i;
      i += 7;
      if (i > 35)
        throw new RuntimeException("varint read failed");
    }
    return value | (b << i);
  }

  public int readVarint () {
    int value = 0;
    int i = 0;
    int b;
    while (((b = data[index++]) & 0x80) != 0) {
      value |= (b & 0x7F) << i;
      i += 7;
      if (i > 35)
        throw new RuntimeException("varint read failed");
    }
    return value | (b << i);
  }

  public long readVarLong () {
    int shift = 0;
    long result = 0;
    while (shift < 64) {
      byte b = data[index++];
      result |= (long) (b & 0x7F) << shift;
      if ((b & 0x80) == 0)
        return decodeZigZag64(result);
      shift += 7;
    }
    throw new RuntimeException("varlong read failed");
  }

  private static long decodeZigZag64(long n) {
    return (n >>> 1) ^ -(n & 1);
  }

  private static long encodeZigZag64(long n) {
    return (n << 1) ^ (n >> 63);
  }

  public long readLong() {
    checkCapacity(8);
    long value = 0;
    for (int i = 0; i < 8; i++) {
      value = (value << 8) + (data[index++] & 0xff);
    }
    return value;
  }

  public static long readLong (byte[] data, int index) {
    if (data.length - index < 8)
      throw new IllegalArgumentException();
    long value = 0;
    for (int i = 0; i < 8; i++) {
      value = (value << 8) + (data[index++] & 0xff);
    }
    return value;
  }

  public static double readDouble (byte[] data, int index) {
    return Double.longBitsToDouble(readLong(data, index));
  }

  public static int readInt (byte[] data, int index) {
    if (data.length - index < 4)
      throw new IllegalArgumentException();
    return ((0xff & data[index++]) << 24) | ((0xff & data[index++]) << 16) |
      ((0xff & data[index++]) << 8) | (0xff & data[index++]);
  }

  public static float readFloat (byte[] data, int index) {
    return Float.intBitsToFloat(readInt(data, index));
  }

  public double readDouble() {
    return Double.longBitsToDouble(readLong());
  }

  public static byte[] readByteArray (RandomAccessFile file) throws IOException {
    int length = readVarint(file);
    byte[] i = new byte[length];
    file.read(i, 0, i.length);
    return i;
  }

  /**
   * Skips byte array without allocating any buffer
   */
  public void skipByteArray () {
    int length = readVarint();
    checkCapacity(length);
    index += length;
  }

  public byte[] readByteArray() {
    int length = readVarint();
    checkCapacity(length);
    byte[] i = new byte[length];
    for (int j = 0; j < i.length; j++)
      i[j] = data[index++];
    return i;
  }

  public int[] readIntArray () {
    int length = readVarint();
    checkCapacity(4 * length);
    int[] i = new int[length];
    checkCapacity(4 * i.length);
    for (int j = 0; j < i.length; j++)
      i[j] = readInt();
    return i;
  }

  public long[] readLongArray() {
    int length = readVarint();
    checkCapacity(8 * length);
    long[] i = new long[length];
    checkCapacity(8 * i.length);
    for (int j = 0; j < i.length; j++)
      i[j] = readLong();
    return i;
  }

  public static String readString (RandomAccessFile r) throws IOException {
    try {
      return new String(readByteArray(r), StringUtils.UTF_8);
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException();
    }
  }

  public String readString () {
    return new String(readByteArray(), StringUtils.UTF_8);
  }

  /**
   * Skips string without allocating buffer
   */
  public void skipString () {
    skipByteArray();
  }

  public long crc32 () {
    if (crc32 == null) {
      crc32 = new CRC32();
    } else {
      crc32.reset();
    }
    crc32.update(data);
    return crc32.getValue();
  }

  /* Public utils */

  public static int sizeOf (long i) {
    i = encodeZigZag64(i);
    int size = 0;
    while (true) {
      if ((i & ~0x7FL) == 0) {
        return ++size;
      } else {
        size++;
        i >>>= 7;
      }
    }
  }

  public static int sizeOf (int i) {
    int size = 1;
    while ((i & 0xffffff80) != 0l) {
      size++;
      i >>>= 7;
    }
    return size;
  }

  public static int sizeOf (byte[] v) {
    return sizeOf(v.length) + v.length;
  }

  public static int sizeOf (String str, boolean allowEmpty) {
    if (StringUtils.isEmpty(str)) {
      return allowEmpty ? Blob.sizeOf(0) : 0;
    } else {
      return Blob.sizeOf(str.length()) + str.length(); // TODO calculate better
    }
  }

  public static int sizeOf (int[] v) {
    return sizeOf(v.length) + 4 * v.length;
  }

  public static int sizeOf (long[] v) {
    return sizeOf(v.length) + 8 * v.length;
  }
}
