package org.thunderdog.challegram.telegram;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import me.vkryl.core.collection.LongList;
import me.vkryl.core.unit.BitwiseUtils;
import me.vkryl.core.util.FilteredIterator;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

/**
 * Date: 25/12/2018
 * Author: default
 */
public class TdlibNotificationGroup implements Iterable<TdlibNotification> {
  private static final int FLAG_HIDDEN_GLOBALLY = 1;
  private static final int FLAG_VISIBLE = 1 << 1;

  private final Tdlib tdlib;
  private final int id;
  private final TdApi.NotificationGroupType type;
  private final long chatId;

  private int totalCount;
  private final ArrayList<TdlibNotification> notifications;

  private int hiddenNotificationId, flags;

  public TdlibNotificationGroup (Tdlib tdlib, TdApi.NotificationGroup group) {
    this.tdlib = tdlib;
    this.id = group.id;
    this.type = group.type;
    this.chatId = group.chatId;
    this.totalCount = group.totalCount;
    this.notifications = new ArrayList<>(group.notifications.length);
    for (TdApi.Notification notification : group.notifications) {
      this.notifications.add(new TdlibNotification(tdlib, notification, this));
    }
    Collections.sort(this.notifications);
    restoreData();
  }

  public TdlibNotificationGroup (Tdlib tdlib, TdApi.UpdateNotificationGroup update) {
    this.tdlib = tdlib;
    this.id = update.notificationGroupId;
    this.type = update.type;
    this.chatId = update.chatId;
    this.totalCount = update.totalCount;
    int notificationCount = update.addedNotifications != null ? update.addedNotifications.length : 0;
    this.notifications = new ArrayList<>(notificationCount);
    if (notificationCount > 0) {
      for (TdApi.Notification notification : update.addedNotifications) {
        this.notifications.add(new TdlibNotification(tdlib, notification, this));
      }
    }
    Collections.sort(this.notifications);
    restoreData();
  }

  private void restoreData () {
    long data = tdlib.settings().getNotificationGroupData(this.id);
    this.hiddenNotificationId = BitwiseUtils.splitLongToFirstInt(data);
    this.flags = BitwiseUtils.splitLongToSecondInt(data);
  }

  public int getId () {
    return id;
  }

  public int getTotalCount () {
    return totalCount;
  }

  public int maxNotificationId () {
    return notifications.isEmpty() ? hiddenNotificationId : notifications.get(notifications.size() - 1).getId();
  }

  public long getChatId () {
    return chatId;
  }

  public long getMessageThreadId () {
    return 0;
  }

  public boolean isSelfChat () {
    return tdlib.isSelfChat(chatId);
  }

  public long[] getAllMessageIds () {
    LongList ids = new LongList(notifications.size());
    for (TdlibNotification notification : this) {
      long messageId = notification.findMessageId();
      if (messageId != 0)
        ids.append(messageId);
    }
    return ids.get();
  }

  public long findTargetMessageId () {
    if (!isMention())
      return 0;
    for (TdlibNotification notification : this) {
      long messageId = notification.findMessageId();
      if (messageId != 0)
        return messageId;
    }
    return 0;
  }

  public int firstNotificationId () {
    return !notifications.isEmpty() ? notifications.get(0).getId() : 0;
  }

  public long[] getAllUserIds () {
    Set<Long> userIds = new HashSet<>(notifications.size());
    for (TdlibNotification notification : this) {
      long chatId = notification.findSenderId();
      if (ChatId.isPrivate(chatId)) {
        userIds.add(ChatId.toUserId(chatId));
      }
    }
    if (!userIds.isEmpty()) {
      long[] result = new long[userIds.size()];
      int i = 0;
      for (Long userId : userIds) {
        result[i] = userId;
        i++;
      }
      return result;
    }
    return null;
  }

  public boolean isMention () {
    return type.getConstructor() == TdApi.NotificationGroupTypeMentions.CONSTRUCTOR;
  }

  public boolean isOnlyPinned () {
    boolean first = true;
    for (TdlibNotification notification : this) {
      if (!notification.isPinnedMessage()) {
        return false;
      } else if (first) {
        first = false;
      }
    }
    return !first;
  }

  public boolean isOnlyInitiallySilent () {
    boolean first = true;
    for (TdlibNotification notification : this) {
      if (!notification.isVisuallySilent()) {
        return false;
      } else if (first) {
        first = false;
      }
    }
    return !first;
  }

  public boolean isOnlyScheduled () {
    boolean first = true;
    for (TdlibNotification notification : this) {
      if (!notification.isScheduled()) {
        return false;
      } else if (first) {
        first = false;
      }
    }
    return !first;
  }

  public long singleSenderId () {
    TdlibNotification prevNotification = null;
    for (TdlibNotification notification : this) {
      if (prevNotification != null && !prevNotification.isSameSender(notification))
        return 0;
      prevNotification = notification;
    }
    return prevNotification != null ? prevNotification.findSenderId() : 0;
  }

  public static final int CATEGORY_DEFAULT = 0;
  public static final int CATEGORY_PRIVATE = 1;
  public static final int CATEGORY_GROUPS = 2;
  public static final int CATEGORY_CHANNELS = 3;
  public static final int CATEGORY_SECRET = 4;

  public static final int MAX_CATEGORY = CATEGORY_SECRET;

  public int getCategory () {
    if (Settings.instance().needSplitNotificationCategories()) {
      switch (ChatId.getType(chatId)) {
        case TdApi.ChatTypeSecret.CONSTRUCTOR:
          return CATEGORY_SECRET; // Settings.instance().needHideSecretChats() ? CATEGORY_SECRET : CATEGORY_PRIVATE;
        case TdApi.ChatTypePrivate.CONSTRUCTOR:
          return CATEGORY_PRIVATE;
        case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
          return isMention() ? CATEGORY_PRIVATE : CATEGORY_GROUPS;
        case TdApi.ChatTypeSupergroup.CONSTRUCTOR:
          return isMention() ? CATEGORY_PRIVATE : tdlib.isChannelFast(chatId) ? CATEGORY_CHANNELS : CATEGORY_GROUPS;
      }
      throw new IllegalStateException("Unknown chatId: " + chatId);
    }
    if (ChatId.isSecret(chatId) && Settings.instance().needHideSecretChats()) {
      return CATEGORY_SECRET;
    }
    return CATEGORY_DEFAULT;
  }

  public boolean matchesCategory (int category) {
    return category == CATEGORY_DEFAULT || getCategory() == category;
  }

  public List<TdlibNotification> notifications () {
    return notifications;
  }

  @NonNull
  @Override
  public Iterator<TdlibNotification> iterator () {
    return new FilteredIterator<>(notifications, notification -> !notification.isHidden());
  }

  public TdlibNotification lastNotification () {
    return notifications.get(notifications.size() - 1);
  }

  public boolean isEmpty () {
    return notifications.isEmpty();
  }

  public int visualSize () {
    int count = 0;
    for (TdlibNotification ignored : this) {
      count++;
    }
    return count;
  }

  public TdlibNotification removeNotification (int notificationId) {
    int i = 0;
    for (TdlibNotification notification : notifications) {
      if (notification.getId() == notificationId) {
        return notifications.remove(i);
      }
      i++;
    }
    return null;
  }

  public int updateGroup (TdApi.UpdateNotificationGroup update, @Nullable List<TdlibNotification> addedNotifications, List<TdlibNotification> removedNotifications) {
    int changeCount = 0;
    if (this.totalCount != update.totalCount) {
      this.totalCount = update.totalCount;
      changeCount++;
    }
    if (update.removedNotificationIds != null && update.removedNotificationIds.length > 0) {
      for (int i = update.removedNotificationIds.length - 1; i >= 0; i--) {
        TdlibNotification removedNotification = removeNotification(update.removedNotificationIds[i]);
        if (removedNotification != null) {
          if (removedNotifications != null)
            removedNotifications.add(removedNotification);
          changeCount++;
        }
      }
    }
    if (update.addedNotifications != null && update.addedNotifications.length > 0) {
      boolean needHideReplacements = isHidden();
      this.notifications.ensureCapacity(this.notifications.size() + update.addedNotifications.length);
      for (TdApi.Notification notification : update.addedNotifications) {
        TdlibNotification addedNotification = new TdlibNotification(tdlib, notification, this);
        this.notifications.add(addedNotification);
        if (addedNotifications != null)
          addedNotifications.add(addedNotification);
        boolean replacement = false;
        if (removedNotifications != null && notification.type.getConstructor() == TdApi.NotificationTypeNewMessage.CONSTRUCTOR) {
          TdApi.Message message = ((TdApi.NotificationTypeNewMessage) notification.type).message;
          for (TdlibNotification removedNotification : removedNotifications) {
            if (removedNotification.getNotificationContent().getConstructor() == TdApi.NotificationTypeNewPushMessage.CONSTRUCTOR) {
              TdApi.NotificationTypeNewPushMessage pushMessage = (TdApi.NotificationTypeNewPushMessage) removedNotification.getNotificationContent();
              if (removedNotification.getChatId() == message.chatId && message.id == pushMessage.messageId) {
                replacement = true;
                if (needHideReplacements) {
                  increaseHiddenNotificationId(addedNotification.getId());
                } else {
                  boolean changed = !Td.equalsTo(pushMessage.sender, message.sender); // FIXME TDLib?
                  if (!changed) {
                    TdApi.FormattedText text = Td.textOrCaption(message.content); // TODO: server should include entities in push content
                    if (!Td.isEmpty(text) && text.entities != null && text.entities.length > 0) {
                      for (TdApi.TextEntity entity : text.entities) {
                        if (TD.isVisual(entity.type, false)) {
                          changed = true;
                          break;
                        }
                      }
                    }
                  }
                  if (changed) {
                    changeCount++;
                  }
                }
                break;
              }
            }
          }
        }
        if (replacement) {
          changeCount--;
        } else {
          changeCount++;
          needHideReplacements = false;
        }
      }
      Collections.sort(this.notifications);
    }
    return changeCount;
  }

  private int indexOfNotification (int notificationId) {
    TdlibNotification stubNotification = new TdlibNotification(notificationId);
    int i = Collections.binarySearch(notifications, stubNotification);
    return i >= 0 ? i : -1;
  }

  @Nullable
  public TdlibNotification updateNotification (TdApi.Notification notification) {
    int i = indexOfNotification(notification.id);
    if (i >= 0) {
      TdlibNotification result = new TdlibNotification(tdlib, notification, this);
      notifications.set(i, result);
      return result;
    }
    return null;
  }

  // Client-specific

  private void setNotificationData (int hiddenNotificationId, int flags) {
    if (this.hiddenNotificationId != hiddenNotificationId || this.flags != flags) {
      this.hiddenNotificationId = hiddenNotificationId;
      this.flags = flags;
      tdlib.settings().setNotificationGroupData(this.id, hiddenNotificationId, flags);
    }
  }

  private void increaseHiddenNotificationId (int hiddenNotificationId) {
    if (this.hiddenNotificationId < hiddenNotificationId) {
      setNotificationData(hiddenNotificationId, this.flags);
    }
  }

  public boolean dropNotificationData () {
    if (this.hiddenNotificationId != 0 || this.flags != 0) {
      this.hiddenNotificationId = 0;
      this.flags = 0;
      // TODO call TDLib method
      return true;
    }
    return false;
  }

  public static final int HIDE_REASON_DEFAULT = 0;
  public static final int HIDE_REASON_GLOBAL = 1;
  public static final int HIDE_REASON_DISABLED_CHANNEL = 2;
  public static final int HIDE_REASON_SECURITY_ERROR = 3;
  public static final int HIDE_REASON_DATA_DROP = 4;
  public static final int HIDE_REASON_DISPLAY_ERROR = 5;
  public static final int HIDE_REASON_BUILD_ERROR = 6;
  public static final int HIDE_REASON_RESTRICTED = 7;

  public void markAsHidden (int reason) {
    int flags = this.flags & ~FLAG_VISIBLE;
    if (reason == HIDE_REASON_GLOBAL)
      flags |= FLAG_HIDDEN_GLOBALLY;
    int maxNotificationId = maxNotificationId();
    setNotificationData(maxNotificationId, flags);
    if (reason != HIDE_REASON_DISABLED_CHANNEL && needRemoveDismissedMessages()) {
      tdlib.client().send(new TdApi.RemoveNotificationGroup(id, maxNotificationId), tdlib.silentHandler());
    }
  }

  public boolean isHidden () {
    return (flags & FLAG_VISIBLE) == 0 && isHidden(maxNotificationId());
  }

  public boolean isHidden (int notificationId) {
    return hiddenNotificationId != 0 && hiddenNotificationId >= notificationId;
  }

  public void markAsVisible () {
    // Called when group becomes displayed
    setNotificationData(this.hiddenNotificationId, (this.flags & ~FLAG_HIDDEN_GLOBALLY) | FLAG_VISIBLE);
  }

  public boolean needRemoveDismissedMessages () {
    int notificationFlag;
    if (isMention()) {
      notificationFlag = Settings.NOTIFICATION_FLAG_INCLUDE_PRIVATE;
    } else {
      switch (ChatId.getType(getChatId())) {
        case TdApi.ChatTypePrivate.CONSTRUCTOR:
        case TdApi.ChatTypeSecret.CONSTRUCTOR:
          notificationFlag = Settings.NOTIFICATION_FLAG_INCLUDE_PRIVATE;
          break;
        case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
          notificationFlag = Settings.NOTIFICATION_FLAG_INCLUDE_GROUPS;
          break;
        case TdApi.ChatTypeSupergroup.CONSTRUCTOR:
          notificationFlag = tdlib.isChannel(getChatId()) ? Settings.NOTIFICATION_FLAG_INCLUDE_CHANNELS : Settings.NOTIFICATION_FLAG_INCLUDE_GROUPS;
          break;
        default:
          return true;
      }
    }
    return !Settings.instance().checkNotificationFlag(notificationFlag);
  }
}
