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
 */
package org.thunderdog.challegram.data;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

/** Width policy shared by standalone forum topics and forum tabs; widths are local pixels. */
@me.vkryl.annotation.MadeByLLM(value = "gpt-6-astra", id = "forum-topics", ref = "PR #1095")
public final class ForumMessageLayout {
  private ForumMessageLayout () {
  }

  public static boolean useWideContent (
    boolean forumTabs,
    @Nullable TdApi.MessageTopic openTopic,
    boolean eventLog,
    boolean threadHeader
  ) {
    // Use the open history's typed identity, not a message's topic or the group's type.
    // Comments, saved-message topics and ordinary chats keep their existing geometry.
    return (forumTabs || ForumHistory.isForum(openTopic)) && !eventLog && !threadHeader;
  }

  public static int bubbleGutterDp (boolean forumContent, boolean threadHeader) {
    // History width already excludes any panel. Standalone topics have no panel to reserve.
    return forumContent ? 0 : threadHeader ? 8 : 56;
  }

  public static int captionMaxWidth (
    boolean forumContent,
    boolean bubbles,
    int contentWidth,
    int mediaWidth,
    int captionPadding
  ) {
    if (!bubbles) {
      return contentWidth;
    }
    return Math.max(1, (forumContent ? contentWidth : mediaWidth) - captionPadding);
  }

  public static int mediaContentWidth (
    boolean forumContent,
    boolean bubbles,
    int mediaWidth,
    int captionWidth,
    int captionPadding
  ) {
    return Math.max(mediaWidth, captionWidth + (forumContent && bubbles ? captionPadding : 0));
  }

  public static int expandedMediaWidth (
    boolean fillCaption,
    int mediaWidth,
    int captionWidth,
    int captionPadding,
    int availableWidth
  ) {
    return fillCaption ?
      Math.max(mediaWidth, Math.min(availableWidth, captionWidth + captionPadding)) : mediaWidth;
  }

  public static int headerAvatarLeft (boolean rtl, int left, int right, int avatarSize) {
    return rtl ? right - avatarSize : left;
  }

  public static int headerAuthorLeft (
    boolean rtl,
    int left,
    int right,
    int avatarSize,
    int gap,
    int authorWidth
  ) {
    return rtl ? right - avatarSize - gap - authorWidth : left + avatarSize + gap;
  }

  public static int headerTrailingLeft (boolean rtl, int left, int right, int trailingWidth) {
    return rtl ? left : right - trailingWidth;
  }
}