package org.thunderdog.challegram.util;

public final class LongPair {
  public final long value1;
  public final long value2;

  public LongPair (long value1, long value2) {
    this.value1 = value1;
    this.value2 = value2;
  }

  public static LongPair of (long value1, long value2) {
    return new LongPair(value1, value2);
  }

  @Override
  public boolean equals (Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LongPair other = (LongPair) o;
    return value1 == other.value1 && value2 == other.value2;
  }

  @Override
  public int hashCode () {
    int result = (int) (value1 ^ (value1 >>> 32));
    result = 31 * result + (int) (value2 ^ (value2 >>> 32));
    return result;
  }
}
