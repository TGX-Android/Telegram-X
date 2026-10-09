package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;

import java.util.List;

/** Pure UI policy for the pinned TDLib contract. The server remains authoritative. */
public final class ForumTopicPolicy {
  private ForumTopicPolicy () { }
  public static final int[] ICON_COLORS = {0x6FB9F0, 0xFFD67E, 0xCB86DB, 0x8EEE98, 0xFF93B2, 0xFB6F5F};

  public static boolean isMember (TdApi.ChatMemberStatus status) {
    return status instanceof TdApi.ChatMemberStatusMember || status instanceof TdApi.ChatMemberStatusAdministrator ||
      status instanceof TdApi.ChatMemberStatusCreator && ((TdApi.ChatMemberStatusCreator) status).isMember ||
      status instanceof TdApi.ChatMemberStatusRestricted && ((TdApi.ChatMemberStatusRestricted) status).isMember;
  }

  public static boolean canManage (TdApi.ChatMemberStatus status) {
    return status instanceof TdApi.ChatMemberStatusCreator && isMember(status) ||
      status instanceof TdApi.ChatMemberStatusAdministrator && ((TdApi.ChatMemberStatusAdministrator) status).rights.canManageTopics;
  }

  public static boolean canCreate (TdApi.ChatMemberStatus status, TdApi.ChatPermissions permissions) {
    if (!isMember(status)) return false;
    if (canManage(status)) return true;
    if (status instanceof TdApi.ChatMemberStatusRestricted) {
      return permissions != null && permissions.canCreateTopics && ((TdApi.ChatMemberStatusRestricted) status).permissions.canCreateTopics;
    }
    return permissions != null && permissions.canCreateTopics;
  }

  public static boolean canEdit (TdApi.ChatMemberStatus status, TdApi.ForumTopicInfo info) {
    return info != null && isMember(status) && (canManage(status) || info.isOutgoing);
  }

  public static boolean canDeleteAny (TdApi.ChatMemberStatus status) {
    return status instanceof TdApi.ChatMemberStatusCreator && isMember(status) ||
      status instanceof TdApi.ChatMemberStatusAdministrator && ((TdApi.ChatMemberStatusAdministrator) status).rights.canDeleteMessages;
  }

  public static boolean canOfferDelete (TdApi.ChatMemberStatus status, TdApi.ForumTopicInfo info) {
    return info != null && (canDeleteAny(status) || !info.isGeneral && isMember(status) && info.isOutgoing);
  }

  public static boolean canPinMessages (TdApi.ChatMemberStatus status, TdApi.ChatPermissions permissions) {
    if (!isMember(status)) return false;
    if (status instanceof TdApi.ChatMemberStatusCreator) return true;
    if (status instanceof TdApi.ChatMemberStatusAdministrator) return ((TdApi.ChatMemberStatusAdministrator) status).rights.canPinMessages;
    return permissions != null && permissions.canPinMessages && (!(status instanceof TdApi.ChatMemberStatusRestricted) || ((TdApi.ChatMemberStatusRestricted) status).permissions.canPinMessages);
  }

  public static boolean validName (String name) {
    return name != null && !name.trim().isEmpty() && name.codePointCount(0, name.length()) <= 128;
  }

  public static boolean validColor (int color) {
    for (int allowed : ICON_COLORS) if (color == allowed) return true;
    return false;
  }

  public static boolean allowedIcon (long id, boolean premium, TdApi.Sticker[] defaults) {
    if (id == 0 || premium) return true;
    if (defaults != null) for (TdApi.Sticker sticker : defaults) {
      if (sticker.fullType instanceof TdApi.StickerFullTypeCustomEmoji && ((TdApi.StickerFullTypeCustomEmoji) sticker.fullType).customEmojiId == id) return true;
    }
    return false;
  }

  /** Only a complete pinned prefix may be reordered; never submit a search result as the full order. */
  public static boolean completePinnedPrefix (List<TdApi.ForumTopic> topics, boolean endReached) {
    if (endReached) return true;
    for (TdApi.ForumTopic topic : topics) if (!topic.isPinned) return true;
    return false;
  }

  public static int[] movePin (List<TdApi.ForumTopic> topics, int topicId, int direction) {
    int count = 0, index = -1;
    for (TdApi.ForumTopic topic : topics) if (topic.isPinned) count++;
    int[] ids = new int[count];
    int i = 0;
    for (TdApi.ForumTopic topic : topics) if (topic.isPinned) {
      if (topic.info.forumTopicId == topicId) index = i;
      ids[i++] = topic.info.forumTopicId;
    }
    if (index < 0 || Math.abs(direction) != 1 || index + direction < 0 || index + direction >= count) return null;
    int tmp = ids[index]; ids[index] = ids[index + direction]; ids[index + direction] = tmp;
    return ids;
  }

  public static TdApi.ChatNotificationSettings notifications (TdApi.ChatNotificationSettings s, boolean inherit, int muteFor) {
    return new TdApi.ChatNotificationSettings(inherit, inherit ? 0 : muteFor,
      s.useDefaultSound, s.soundId, s.useDefaultShowPreview, s.showPreview,
      s.useDefaultMuteStories, s.muteStories, s.useDefaultStorySound, s.storySoundId,
      s.useDefaultShowStoryPoster, s.showStoryPoster,
      s.useDefaultDisablePinnedMessageNotifications, s.disablePinnedMessageNotifications,
      s.useDefaultDisableMentionNotifications, s.disableMentionNotifications);
  }
}
