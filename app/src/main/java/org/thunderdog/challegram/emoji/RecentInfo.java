/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 04/05/2019
 */
package org.thunderdog.challegram.emoji;

import me.vkryl.core.util.Blob;
import me.vkryl.core.util.BlobEntry;

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
