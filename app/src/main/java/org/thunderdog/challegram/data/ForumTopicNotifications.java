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

import org.drinkless.tdlib.TdApi;

/** Topic-only edits: copy the latest snapshot and preserve every unrelated TDLib field. */
@me.vkryl.annotation.MadeByLLM(value = "gpt-6-astra", id = "forum-topics", ref = "PR #1095")
public final class ForumTopicNotifications {
  private ForumTopicNotifications () {
  }

  public enum Field {
    PREVIEW, PINNED, MENTIONS
  }

  public static TdApi.ChatNotificationSettings copy (TdApi.ChatNotificationSettings value) {
    return new TdApi.ChatNotificationSettings(
      value.useDefaultMuteFor,
      value.muteFor,
      value.useDefaultSound,
      value.soundId,
      value.useDefaultShowPreview,
      value.showPreview,
      value.useDefaultMuteStories,
      value.muteStories,
      value.useDefaultStorySound,
      value.storySoundId,
      value.useDefaultShowStoryPoster,
      value.showStoryPoster,
      value.useDefaultDisablePinnedMessageNotifications,
      value.disablePinnedMessageNotifications,
      value.useDefaultDisableMentionNotifications,
      value.disableMentionNotifications
    );
  }

  public static TdApi.ChatNotificationSettings mute (
    TdApi.ChatNotificationSettings value,
    boolean inherit,
    int seconds
  ) {
    return ForumTopicPolicy.notifications(value, inherit, seconds);
  }

  public static TdApi.ChatNotificationSettings toggleMute (
    TdApi.ChatNotificationSettings value,
    boolean groupMuted
  ) {
    // Inherited group mute must become an explicit zero, not "use default".
    return mute(value, false, ForumPresentation.isMuted(value, groupMuted) ? 0 : Integer.MAX_VALUE);
  }

  public static TdApi.ChatNotificationSettings sound (
    TdApi.ChatNotificationSettings value,
    boolean inherit,
    long soundId
  ) {
    TdApi.ChatNotificationSettings result = copy(value);
    result.useDefaultSound = inherit;
    result.soundId = soundId;
    return result;
  }

  public static TdApi.ChatNotificationSettings field (
    TdApi.ChatNotificationSettings value,
    Field field,
    boolean inherit,
    boolean enabled
  ) {
    TdApi.ChatNotificationSettings result = copy(value);
    switch (field) {
      case PREVIEW: result.useDefaultShowPreview = inherit; result.showPreview = enabled; break;
      case PINNED:
        result.useDefaultDisablePinnedMessageNotifications =
          inherit; result.disablePinnedMessageNotifications = !enabled; break;
      case MENTIONS:
        result.useDefaultDisableMentionNotifications = inherit; result.disableMentionNotifications =
          !enabled; break;
    }
    return result;
  }
}