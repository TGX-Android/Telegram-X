package org.thunderdog.challegram.video.old;

@Deprecated
public class Sample {
  private long offset = 0;
  private long size = 0;

  public Sample(long offset, long size) {
    this.offset = offset;
    this.size = size;
  }

  public long getOffset() {
    return offset;
  }

  public long getSize() {
    return size;
  }
}

