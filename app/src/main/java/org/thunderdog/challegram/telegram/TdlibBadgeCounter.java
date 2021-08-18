package org.thunderdog.challegram.telegram;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.td.ChatPosition;

/**
 * Date: 10/01/2019
 * Author: default
 */
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
