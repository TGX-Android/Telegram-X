package org.thunderdog.challegram.emoji;

import me.vkryl.core.util.Blob;
import me.vkryl.core.util.BlobEntry;

/**
 * Date: 2019-05-04
 * Author: default
 */
public class RecentInfo implements BlobEntry {
  public int useCount;
  public int lastUseTime;

  public RecentInfo () { }

  public RecentInfo (int useCount, int lastUseTime) {
    this.useCount = useCount;
    this.lastUseTime = lastUseTime;
  }

  @Override
  public int estimatedBinarySize () {
    return Blob.sizeOf(useCount) + Blob.sizeOf(lastUseTime);
  }

  @Override
  public void saveTo (Blob blob) {
    blob.writeVarint(useCount);
    blob.writeVarint(lastUseTime);
  }

  @Override
  public void restoreFrom (Blob blob) {
    useCount = blob.readVarint();
    lastUseTime = blob.readVarint();
  }
}
