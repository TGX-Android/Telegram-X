/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 10/01/2019
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.td.ChatPosition;

public class TdlibBadgeCounter {
  private int count;
  private boolean isMuted;

  TdlibBadgeCounter () { }

  TdlibBadgeCounter (TdlibCounter counter, @Nullable TdlibCounter archiveCounter) {
    reset(counter, archiveCounter);
  }

  boolean reset (Tdlib tdlib) {
    return reset(tdlib.getCounter(ChatPosition.CHAT_LIST_MAIN), tdlib.getCounter(ChatPosition.CHAT_LIST_ARCHIVE));
  }

  boolean reset (TdlibAccount account) {
    return reset(account.getCounter(ChatPosition.CHAT_LIST_MAIN), account.getCounter(ChatPosition.CHAT_LIST_ARCHIVE));
  }

  void add (TdlibBadgeCounter counter) {
    this.count += counter.count;
    if (!counter.isMuted)
      this.isMuted = false;
  }

  boolean reset (TdlibCounter counter, @Nullable TdlibCounter archiveCounter) {
    int badgeFlags = Settings.instance().getBadgeFlags();
    boolean needArchive = (badgeFlags & Settings.BADGE_FLAG_ARCHIVED) != 0 && archiveCounter != null;
    int mutedCount, unmutedCount;
    if ((badgeFlags & Settings.BADGE_FLAG_MESSAGES) != 0) {
      mutedCount = counter.messageCount;
      unmutedCount = counter.messageUnmutedCount;
      if (needArchive) {
        mutedCount += archiveCounter.messageCount;
        unmutedCount += archiveCounter.messageUnmutedCount;
      }
    } else {
      mutedCount = counter.chatCount;
      unmutedCount = counter.chatUnmutedCount;
      if (needArchive) {
        mutedCount += archiveCounter.chatCount;
        unmutedCount += archiveCounter.chatUnmutedCount;
      }
    }
    mutedCount = Math.max(0, mutedCount);
    unmutedCount = Math.max(0, unmutedCount);

    int count;
    boolean isMuted;
    if ((badgeFlags & Settings.BADGE_FLAG_MUTED) != 0) {
      count = mutedCount;
      isMuted = unmutedCount == 0;
    } else {
      count = unmutedCount;
      isMuted = false;
    }

    boolean changed = this.count != count || this.isMuted != isMuted;

    this.count = count;
    this.isMuted = isMuted;

    return changed;
  }

  public int getCount () {
    return count;
  }

  public boolean isMuted () {
    return isMuted;
  }
}
