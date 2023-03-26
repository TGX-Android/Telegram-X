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
 * File created on 04/02/2018
 */
package org.thunderdog.challegram.telegram;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.collection.SparseArrayCompat;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.TDLib;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.SparseLongArray;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

@TargetApi(Build.VERSION_CODES.O)
public class TdlibNotificationChannelGroup {
  private final Tdlib tdlib;

  private static final String ACCOUNT_PREFIX = "account_";
  private static final String ACCOUNT_PREFIX_DEBUG = "debug_account_";
  private final long accountUserId;
  private final int globalVersion;
  private final String groupId;
  private final boolean isDebug;

  private final SparseArrayCompat<Object> commonChannels = new SparseArrayCompat<>(3);
  private final SparseLongArray versions = new SparseLongArray(3);

  private static final String PRIVATE_SUFFIX = "_private";
  private static final String GROUP_SUFFIX = "_group";
  private static final String CHANNEL_SUFFIX = "_channel";

  private static class ChannelEntry {
    public final long chatId;
    public @NonNull Object channel;
    public long version;

    public ChannelEntry (long chatId, @NonNull Object channel, long version) {
      this.chatId = chatId;
      this.channel = channel;
      this.version = version;
    }
  }

  private static final String CUSTOM_SUFFIX = "_chat_";
  private final LongSparseArray<ChannelEntry> customChannels = new LongSparseArray<>();

  private static String makePrefix (long accountUserId, int globalVersion) {
    return accountUserId + "_" + globalVersion;
  }

  public TdlibNotificationChannelGroup (Tdlib tdlib, long accountUserId, boolean isDebugAccount, @Nullable TdApi.User account) throws ChannelCreationFailureException {
    this.tdlib = tdlib;
    this.accountUserId = accountUserId;
    this.isDebug = isDebugAccount;
    this.globalVersion = tdlib.notifications().getChannelsGlobalVersion();
    this.groupId = makeGroupId(accountUserId, isDebugAccount);
    create(account);
  }

  public void create (@Nullable TdApi.User account) throws ChannelCreationFailureException {
    NotificationManager m = (NotificationManager) UI.getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
    if (m == null)
      throw new ChannelCreationFailureException("Notification service unavailable");
    try {
      m.createNotificationChannelGroup(new android.app.NotificationChannelGroup(this.groupId, Lang.getDebugString(TD.getUserName(accountUserId, account), isDebug)));
    } catch (Throwable t) {
      throw new ChannelCreationFailureException(t);
    }
    createCommonChannel(tdlib, m, tdlib.notifications().scopePrivate());
    createCommonChannel(tdlib, m, tdlib.notifications().scopeGroup());
    createCommonChannel(tdlib, m, tdlib.notifications().scopeChannel());
  }

  private Object createCommonChannel (Tdlib tdlib, NotificationManager m, TdApi.NotificationSettingsScope scope) throws ChannelCreationFailureException {
    int importance, vibrateMode, ledColor;
    String sound;

    LocalScopeNotificationSettings settings = tdlib.notifications().getLocalNotificationSettings(scope);
    long channelVersion = tdlib.notifications().getChannelVersion(scope, 0);

    importance = settings.getPriorityOrImportance();
    vibrateMode = settings.getVibrateMode();
    sound = settings.getSound();
    ledColor = settings.getLedColor();

    String title, description;
    switch (scope.getConstructor()) {
      case TdApi.NotificationSettingsScopePrivateChats.CONSTRUCTOR:
        title = Lang.getString(R.string.PrivateChatsMentions);
        description = Lang.getString(R.string.NotificationChannelCommonPrivate);
        break;
      case TdApi.NotificationSettingsScopeGroupChats.CONSTRUCTOR:
        title = Lang.getString(R.string.Groups);
        description = Lang.getString(R.string.NotificationChannelCommonGroups);
        break;
      case TdApi.NotificationSettingsScopeChannelChats.CONSTRUCTOR:
        title = Lang.getString(R.string.Channels);
        description = Lang.getString(R.string.NotificationChannelCommonChannels);
        break;
      default:
        throw new IllegalArgumentException("scope == " + scope);
    }

    android.app.NotificationChannel channel;

    channel = (android.app.NotificationChannel) newChannel(makeChannelId(accountUserId, globalVersion, scope, 0, channelVersion), title, groupId, importance, vibrateMode, sound, ledColor);
    channel.setDescription(description);
    try {
      m.createNotificationChannel(channel);
    } catch (Throwable t) {
      throw new ChannelCreationFailureException(t);
    }

    commonChannels.put(scope.getConstructor(), channel);
    versions.put(scope.getConstructor(), channelVersion);

    return channel;
  }

  private Object getChannel (TdApi.NotificationSettingsScope scope) {
    Object channel = commonChannels.get(scope.getConstructor());
    if (channel == null)
      throw new IllegalStateException("scope == " + scope);
    return channel;
  }

  private long getChannelVersion (TdApi.NotificationSettingsScope scope) {
    int i = versions.indexOfKey(scope.getConstructor());
    if (i < 0)
      throw new IllegalStateException("scope == " + scope);
    return versions.valueAt(i);
  }

  @NonNull
  private Object getCustomChannel (long chatId) throws ChannelCreationFailureException {
    if (chatId == 0)
      throw new IllegalArgumentException();
    android.app.NotificationChannel channel;
    ChannelEntry entry = customChannels.get(chatId);
    if (entry == null) {
      long channelVersion = tdlib.notifications().getChannelVersion(null, chatId);
      channel = (android.app.NotificationChannel) makeCustomChannel(tdlib, accountUserId, isDebug, globalVersion, chatId, channelVersion);
      entry = new ChannelEntry(chatId, channel, channelVersion);
      this.customChannels.put(chatId, entry);
    } else {
      channel = (android.app.NotificationChannel) ensureChannelVersion(tdlib, null, entry);
    }
    return channel;
  }

  @NonNull
  private Object ensureChannelVersion (Tdlib tdlib, TdApi.NotificationSettingsScope scope, @Nullable ChannelEntry customEntry) throws ChannelCreationFailureException {
    final long newVersion = tdlib.notifications().getChannelVersion(scope, customEntry != null ? customEntry.chatId : 0);
    final long oldVersion;
    android.app.NotificationChannel channel;
    if (customEntry != null) {
      oldVersion = customEntry.version;
      channel = (android.app.NotificationChannel) customEntry.channel;
    } else {
      oldVersion = getChannelVersion(scope);
      channel = (android.app.NotificationChannel) getChannel(scope);
    }
    NotificationManager m = (NotificationManager) UI.getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
    if (m == null)
      throw new ChannelCreationFailureException("Notification service unavailable");
    if (oldVersion != newVersion) {
      if (customEntry != null) {
        channel = (android.app.NotificationChannel) makeCustomChannel(tdlib, accountUserId, isDebug, globalVersion, customEntry.chatId, newVersion);
        customEntry.channel = channel;
        customEntry.version = newVersion;
      } else {
        channel = (android.app.NotificationChannel) createCommonChannel(tdlib, m, scope);
      }
    }
    return channel;
  }

  // API

  public long getAccountUserId () {
    return accountUserId;
  }

  @TargetApi(Build.VERSION_CODES.O)
  public Object getChannel (TdlibNotificationGroup group, boolean allowDisabled) throws ChannelCreationFailureException {
    return getChannel(group.getChatId(), group.isMention(), group.singleSenderId(), allowDisabled);
  }

  @TargetApi(Build.VERSION_CODES.O)
  private Object getChannel (long chatId, boolean areMentions, long singleSenderId, boolean allowDisabled) throws ChannelCreationFailureException {
    android.app.NotificationChannel channel = (android.app.NotificationChannel) getChannelImpl(chatId, areMentions, singleSenderId);
    if (channel == null) {
      throw new IllegalStateException("Could not create channel, chatId:" + chatId + ", areMentions:" + areMentions + ", singleSenderId:" + singleSenderId);
    }
    if (allowDisabled || channel.getImportance() != NotificationManager.IMPORTANCE_NONE) {
      return channel;
    }
    return null;
  }

  @TargetApi(Build.VERSION_CODES.O)
  @NonNull
  private Object getChannelImpl (long chatId, boolean areMentions, long singleAuthorChatId) throws ChannelCreationFailureException {
    // android.app.NotificationChannel channel;
    if (areMentions) {
      if (tdlib.notifications().hasCustomChatSettings(singleAuthorChatId)) {
        return getCustomChannel(singleAuthorChatId);
      } else {
        return ensureChannelVersion(tdlib, tdlib.isChannel(chatId) ? tdlib.notifications().scopeChannel() : tdlib.notifications().scopePrivate(), null);
      }
    } else if (tdlib.notifications().hasCustomChatSettings(chatId)) {
      return getCustomChannel(chatId);
    } else {
      return ensureChannelVersion(tdlib, tdlib.notifications().scope(chatId), null);
    }
  }

  // Utils

  public static String makeGroupId (long accountUserId, boolean isDebug) {
    return (isDebug ? ACCOUNT_PREFIX_DEBUG : ACCOUNT_PREFIX) + accountUserId;
  }

  private static Object newChannel (String channelId, String channelName, String groupId, int importance, int vibrateMode, String sound, int ledColor) {
    android.app.NotificationChannel channel = new android.app.NotificationChannel(channelId, channelName, importance);
    channel.setGroup(groupId);
    switch (vibrateMode) {
      case TdlibNotificationManager.VIBRATE_MODE_SHORT:
        channel.setVibrationPattern(TdlibNotificationManager.VIBRATE_SHORT_PATTERN);
        break;
      case TdlibNotificationManager.VIBRATE_MODE_LONG:
        channel.setVibrationPattern(TdlibNotificationManager.VIBRATE_LONG_PATTERN);
        break;
      case TdlibNotificationManager.VIBRATE_MODE_DEFAULT:
        channel.enableVibration(true);
        break;
    }
    if (sound != null) {
      channel.setSound(sound.isEmpty() ? null : Uri.parse(sound), null);
    }
    if (ledColor != 0) {
      channel.enableLights(true);
      channel.setLightColor(ledColor);
    }
    channel.setShowBadge(true);
    return channel;
  }

  private static Object makeCustomChannel (Tdlib tdlib, long accountUserId, boolean isDebug, int globalVersion, long chatId, long channelVersion) {
    int priority = tdlib.notifications().getEffectivePriorityOrImportance(chatId);
    int vibrateMode = tdlib.notifications().getEffectiveVibrateMode(chatId);
    int ledColor = tdlib.notifications().getEffectiveLedColor(chatId);
    String sound = tdlib.notifications().getEffectiveSound(chatId);
    android.app.NotificationChannel channel = (android.app.NotificationChannel) newChannel(makeChannelId(accountUserId, globalVersion, tdlib.notifications().scope(chatId), chatId, channelVersion), Lang.getString(R.string.NotificationChannelCustom, tdlib.chatTitle(chatId)), makeGroupId(accountUserId, isDebug), priority, vibrateMode, sound, ledColor);
    setChannelDescription(tdlib, channel, chatId);
    return channel;
  }

  private static void setChannelDescription (Tdlib tdlib, Object channelObj, long chatId) {
    android.app.NotificationChannel channel = (android.app.NotificationChannel) channelObj;
    switch (ChatId.getType(chatId)) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR: {
        String userName = tdlib.cache().userName(ChatId.toUserId(chatId));
        channel.setDescription(Lang.getString(R.string.NotificationChannelUser, userName));
        break;
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
        channel.setDescription(Lang.getString(R.string.NotificationChannelGroupChat, tdlib.chatTitle(chatId)));
        break;
      case TdApi.ChatTypeSecret.CONSTRUCTOR:
        channel.setDescription(Lang.getString(R.string.NotificationChannelSecretChat, tdlib.chatTitle(chatId)));
        break;
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR:
        TdApi.Supergroup supergroup = tdlib.chatToSupergroup(chatId);
        if (supergroup == null) {
          channel.setDescription(Lang.getString(R.string.NotificationChannelGroupChat, tdlib.chatTitle(chatId)));
        } else if (!Td.hasUsername(supergroup)) {
          channel.setDescription(Lang.getString(supergroup.isChannel ? R.string.NotificationChannelChannelChat : R.string.NotificationChannelGroupChat, tdlib.chatTitle(chatId)));
        } else {
          channel.setDescription(Lang.getString(supergroup.isChannel ? R.string.NotificationChannelChannelChatPublic : R.string.NotificationChannelGroupChatPublic, tdlib.chatTitle(chatId), tdlib.tMeHost() + Td.primaryUsername(supergroup)));
        }
        break;
    }
  }

  @TargetApi(Build.VERSION_CODES.O)
  public static int importanceToPriority (int importance) {
    switch (importance) {
      case NotificationManager.IMPORTANCE_MAX:
        return Notification.PRIORITY_MAX;
      case NotificationManager.IMPORTANCE_HIGH:
        return Notification.PRIORITY_HIGH;
      case NotificationManager.IMPORTANCE_DEFAULT:
        return Notification.PRIORITY_DEFAULT;
      case NotificationManager.IMPORTANCE_LOW:
        return Notification.PRIORITY_LOW;
      case NotificationManager.IMPORTANCE_MIN:
        return Notification.PRIORITY_MIN;
    }
    return Notification.PRIORITY_DEFAULT;
  }

  @TargetApi(Build.VERSION_CODES.O)
  public static int priorityToImportance (int priority) {
    switch (priority) {
      case Notification.PRIORITY_MAX:
        return NotificationManager.IMPORTANCE_MAX;
      case Notification.PRIORITY_HIGH:
        return NotificationManager.IMPORTANCE_HIGH;
      case Notification.PRIORITY_DEFAULT:
        return NotificationManager.IMPORTANCE_DEFAULT;
      case Notification.PRIORITY_LOW:
        return NotificationManager.IMPORTANCE_LOW;
      case Notification.PRIORITY_MIN:
        return NotificationManager.IMPORTANCE_MIN;
    }
    return NotificationManager.IMPORTANCE_NONE;
  }

  // Static API

  private static String getSuffix (TdApi.NotificationSettingsScope scope) {
    switch (scope.getConstructor()) {
      case TdApi.NotificationSettingsScopePrivateChats.CONSTRUCTOR:
        return PRIVATE_SUFFIX;
      case TdApi.NotificationSettingsScopeGroupChats.CONSTRUCTOR:
        return GROUP_SUFFIX;
      case TdApi.NotificationSettingsScopeChannelChats.CONSTRUCTOR:
        return CHANNEL_SUFFIX;
    }
    throw new RuntimeException();
  }

  public static String makeChannelId (long accountUserId, int globalVersion, TdApi.NotificationSettingsScope scope, long customChatId, long channelVersion) {
    StringBuilder b = new StringBuilder(makePrefix(accountUserId, globalVersion));
    b.append(customChatId != 0 ? CUSTOM_SUFFIX : getSuffix(scope));
    if (customChatId != 0) {
      b.append(customChatId);
    }
    if (channelVersion != 0) {
      b.append('_');
      b.append(channelVersion);
    }
    return b.toString();
  }

  public static void updateGroup (TdApi.User user) {
    NotificationManager m = (NotificationManager) UI.getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
    if (m != null) {
      android.app.NotificationChannelGroup group;
      group = new android.app.NotificationChannelGroup("account_" + user.id, TD.getUserName(user));
      m.createNotificationChannelGroup(group);
    }
  }

  @TargetApi(Build.VERSION_CODES.O)
  public static void cleanupChannels (Tdlib tdlib) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager m = (NotificationManager) UI.getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
      if (m == null)
        return;
      long accountUserId = tdlib.myUserId(true);
      if (accountUserId == 0)
        return;
      List<android.app.NotificationChannel> channels = m.getNotificationChannels();
      if (channels != null && !channels.isEmpty()) {
        String prefix = makePrefix(accountUserId, tdlib.notifications().getChannelsGlobalVersion());
        String groupPrefix = tdlib.account().isDebug() ? ACCOUNT_PREFIX_DEBUG : ACCOUNT_PREFIX;
        List<String> removedChannels = null;
        for (int i = channels.size() - 1; i >= 0; i--) {
          android.app.NotificationChannel channel = channels.get(i);
          String groupId = channel.getGroup();
          if (StringUtils.isEmpty(groupId) || !groupId.startsWith(groupPrefix))
            continue;
          int userId = StringUtils.parseInt(groupId.substring(groupPrefix.length()));
          if (userId != accountUserId)
            continue;
          String id = channel.getId();
          boolean ok = false;
          if (!StringUtils.isEmpty(id) && id.startsWith(prefix)) {
            String data = id.substring(prefix.length());
            if (data.startsWith(PRIVATE_SUFFIX)) {
              int versionIndex = data.indexOf('_', PRIVATE_SUFFIX.length());
              long version = versionIndex != -1 ? StringUtils.parseInt(data.substring(versionIndex + 1)) : 0;
              long currentVersion = tdlib.notifications().getChannelVersion(tdlib.notifications().scopePrivate(), 0);
              ok = version == currentVersion;
            } else if (data.startsWith(GROUP_SUFFIX)) {
              int versionIndex = data.indexOf('_', GROUP_SUFFIX.length());
              long version = versionIndex != -1 ? StringUtils.parseInt(data.substring(versionIndex + 1)) : 0;
              long currentVersion = tdlib.notifications().getChannelVersion(tdlib.notifications().scopeGroup(), 0);
              ok = version == currentVersion;
            } else if (data.startsWith(CHANNEL_SUFFIX)) {
              int versionIndex = data.indexOf('_', CHANNEL_SUFFIX.length());
              long version = versionIndex != -1 ? StringUtils.parseInt(data.substring(versionIndex + 1)) : 0;
              long currentVersion = tdlib.notifications().getChannelVersion(tdlib.notifications().scopeChannel(), 0);
              ok = version == currentVersion;
            } else if (data.startsWith(CUSTOM_SUFFIX)) {
              int versionIndex = data.indexOf('_', CUSTOM_SUFFIX.length());
              long version = versionIndex != -1 ? StringUtils.parseInt(data.substring(versionIndex + 1)) : 0;
              long customChatId = StringUtils.parseLong(data.substring(CUSTOM_SUFFIX.length(), versionIndex != -1 ? versionIndex : data.length()));
              long currentVersion = tdlib.notifications().getChannelVersion(null, customChatId);
              ok = version == currentVersion;
            }
          }
          if (!ok) {
            if (removedChannels == null)
              removedChannels = new ArrayList<>();
            removedChannels.add(id);
            m.deleteNotificationChannel(id);
          }
        }
        if (removedChannels != null) {
          Log.e(Log.TAG_FCM, "Removed deprecated channels: %s", Strings.join(", ", removedChannels));
        }
      }
    }
  }

  @TargetApi(Build.VERSION_CODES.O)
  public static void cleanupChannelGroups (TdlibManager context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager m = (NotificationManager) UI.getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
      if (m == null)
        return;
      List<android.app.NotificationChannelGroup> groups = m.getNotificationChannelGroups();
      if (groups != null && !groups.isEmpty()) {

        for (int j = 0; j < 2; j++) {
          boolean isDebug = j == 1;
          long[] userIds = context.availableUserIds(isDebug);
          String prefix = isDebug ? ACCOUNT_PREFIX_DEBUG : ACCOUNT_PREFIX;
          for (int i = groups.size() - 1; i >= 0; i--) {
            android.app.NotificationChannelGroup group = groups.get(i);
            String groupId = group.getId();
            if (!StringUtils.isEmpty(groupId) && groupId.startsWith(prefix)) {
              int userId = StringUtils.parseInt(groupId.substring(prefix.length()));
              if (userId == 0 || Arrays.binarySearch(userIds, userId) < 0) {
                m.deleteNotificationChannelGroup(groupId);
              }
            }
          }
        }
      }
    }
  }

  public static void updateChannelSettings (Tdlib tdlib, long accountUserId, boolean isDebug, int globalVersion, TdApi.NotificationSettingsScope scope, long customChatId, long channelVersion) throws ChannelCreationFailureException {
    if (accountUserId == 0) {
      return;
    }
    NotificationManager m = (NotificationManager) UI.getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
    if (m != null) {
      if (customChatId != 0) {
        TdApi.Chat chat = tdlib.chat(customChatId);
        if (chat != null) {
          android.app.NotificationChannel channel = (android.app.NotificationChannel) makeCustomChannel(tdlib, accountUserId, isDebug, globalVersion, chat.id, channelVersion);
          if (tdlib.notifications().hasCustomChatSettings(customChatId)) {
            try {
              m.createNotificationChannel(channel);
            } catch (Throwable t) {
              throw new ChannelCreationFailureException(t);
            }
          }
        }
      } else {
        tdlib.notifications().createChannels();
      }
      while (channelVersion > 0) {
        m.deleteNotificationChannel(makeChannelId(accountUserId, globalVersion, scope, customChatId, --channelVersion));
      }
    }
  }

  public static void updateChat (Tdlib tdlib, long accountUserId, TdApi.Chat chat) throws ChannelCreationFailureException {
    if (accountUserId == 0) {
      return;
    }
    NotificationManager m = (NotificationManager) UI.getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
    if (m != null) {
      long channelVersion = tdlib.notifications().getChannelVersion(null, chat.id);
      String channelId = makeChannelId(accountUserId, tdlib.notifications().getChannelsGlobalVersion(), null, chat.id, channelVersion);
      android.app.NotificationChannel channel = m.getNotificationChannel(channelId);
      if (channel != null) {
        channel.setName(tdlib.chatTitle(chat));
        setChannelDescription(tdlib, channel, chat.id);
        try {
          m.createNotificationChannel(channel);
        } catch (Throwable t) {
          throw new ChannelCreationFailureException(t);
        }
      }
    }
  }

  public static void deleteChannels (Tdlib tdlib, long accountUserId, boolean isDebug, @Nullable TdApi.User account, boolean recreate) {
    if (account == null) {
      return;
    }
    NotificationManager m = (NotificationManager) UI.getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
    if (m != null) {
      List<android.app.NotificationChannel> channels = m.getNotificationChannels();
      final String groupId = makeGroupId(accountUserId, isDebug);
      if (channels != null && !channels.isEmpty()) {
        for (android.app.NotificationChannel channel : channels) {
          if (StringUtils.equalsOrBothEmpty(channel.getGroup(), groupId)) {
            m.deleteNotificationChannel(channel.getId());
          }
        }
      }
      List<android.app.NotificationChannelGroup> groups = m.getNotificationChannelGroups();
      if (groups != null && !groups.isEmpty()) {
        for (int i = groups.size() - 1; i >= 0; i--) {
          android.app.NotificationChannelGroup group = groups.get(i);
          if (StringUtils.equalsOrBothEmpty(group.getId(), groupId)) {
            m.deleteNotificationChannelGroup(groupId);
          }
        }
      }
      if (recreate) {
        try {
          tdlib.notifications().createChannels();
        } catch (ChannelCreationFailureException e) {
          TDLib.Tag.notifications("Unable to recreate notification channels:\n%s", Log.toString(e));
          tdlib.settings().trackNotificationChannelProblem(e, 0);
        }
      }
      tdlib.notifications().onUpdateNotificationChannels(accountUserId);
    }
  }

  public static class ChannelCreationFailureException extends IOException {
    public ChannelCreationFailureException (String message) {
      super(message);
    }

    public ChannelCreationFailureException (Throwable cause) {
      super(cause);
    }
  }
}
