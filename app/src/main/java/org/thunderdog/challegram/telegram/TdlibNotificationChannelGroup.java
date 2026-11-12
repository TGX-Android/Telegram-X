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

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.collection.LongSparseArray;
import androidx.collection.SparseArrayCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.TDLib;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.unsorted.AppContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.SparseLongArray;
import tgx.td.ChatId;
import tgx.td.Td;

@RequiresApi(Build.VERSION_CODES.O)
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

  private static final String CALLS_SUFFIX = "_calls";
  private static final String CALLS_USER_SUFFIX = "_user_";
  private static final String LEGACY_CALL_PREFIX = "call_";

  private static String makePrefix (long accountUserId, int globalVersion) {
    return accountUserId + "_" + globalVersion;
  }

  @RequiresApi(Build.VERSION_CODES.O)
  public static long[] findUserCallChatIds (long accountUserId, int globalVersion) {
    NotificationManager m =
      (NotificationManager) AppContext.get().getSystemService(Context.NOTIFICATION_SERVICE);
    if (m == null) {
      return new long[0];
    }
    String prefix = makePrefix(accountUserId, globalVersion) + CALLS_SUFFIX + CALLS_USER_SUFFIX;
    Set<Long> chatIds = new HashSet<>();
    for (android.app.NotificationChannel channel : m.getNotificationChannels()) {
      String id = channel.getId();
      if (id.startsWith(prefix)) {
        int end = id.indexOf('_', prefix.length());
        long chatId = StringUtils.parseLong(
          end == -1 ? id.substring(prefix.length()) : id.substring(prefix.length(), end)
        );
        if (chatId != 0) {
          chatIds.add(chatId);
        }
      }
    }
    long[] result = new long[chatIds.size()];
    int i = 0;
    for (long chatId : chatIds) {
      result[i++] = chatId;
    }
    return result;
  }

  public TdlibNotificationChannelGroup (Tdlib tdlib, long accountUserId, boolean isDebugAccount, int globalVersion, @Nullable TdApi.User account) throws ChannelCreationFailureException {
    this.tdlib = tdlib;
    this.accountUserId = accountUserId;
    this.isDebug = isDebugAccount;
    this.globalVersion = globalVersion;
    this.groupId = makeGroupId(accountUserId, isDebugAccount);
    create(account);
  }

  public boolean compareTo (long accountUserId, boolean isDebug, long globalVersion) {
    return this.accountUserId == accountUserId && this.isDebug == isDebug && this.globalVersion == globalVersion;
  }

  private static String getChannelGroupName (long userId, TdApi.User user, boolean isDebug) {
    String name = Lang.getDebugString(TD.getUserName(userId, user), isDebug);
    if (StringUtils.isEmpty(name)) {
      name = "#" + userId;
    }
    return name;
  }

  public void create (@Nullable TdApi.User account) throws ChannelCreationFailureException {
    NotificationManager m = (NotificationManager) AppContext.get().getSystemService(Context.NOTIFICATION_SERVICE);
    if (m == null)
      throw new ChannelCreationFailureException("Notification service unavailable");
    try {
      final String groupName = getChannelGroupName(accountUserId, account, isDebug);
      android.app.NotificationChannelGroup group;
      group = new android.app.NotificationChannelGroup(this.groupId, groupName);
      m.createNotificationChannelGroup(group);
    } catch (Throwable t) {
      throw new ChannelCreationFailureException(t);
    }
    createCommonChannel(tdlib, m, tdlib.notifications().scopePrivate());
    createCommonChannel(tdlib, m, tdlib.notifications().scopeGroup());
    createCommonChannel(tdlib, m, tdlib.notifications().scopeChannel());
    long callChannelVersion = tdlib.notifications().getCallChannelVersion(0);
    Object callChannel =
      makeCallChannel(tdlib, accountUserId, isDebug, globalVersion, 0, callChannelVersion);
    try {
      createChannel(m, callChannel);
    } catch (ChannelCreationFailureException e) {
      // Channels of messages stay usable: without its channel a call rings by the app
      TDLib.Tag.notifications("Unable to create call channel:\n%s", Log.toString(e));
      tdlib.settings().trackNotificationChannelProblem(e, 0);
    }
  }

  private static void createChannel (NotificationManager m, Object channel)
    throws ChannelCreationFailureException {
    try {
      m.createNotificationChannel((android.app.NotificationChannel) channel);
    } catch (Throwable t) {
      throw new ChannelCreationFailureException(t);
    }
  }

  private static void deleteChannel (NotificationManager m, String channelId) {
    try {
      m.deleteNotificationChannel(channelId);
    } catch (Throwable t) {
      TDLib.Tag.notifications("Unable to delete channel %s:\n%s", channelId, Log.toString(t));
    }
  }

  private static void deleteChannelGroup (NotificationManager m, String groupId) {
    try {
      m.deleteNotificationChannelGroup(groupId);
    } catch (Throwable t) {
      TDLib.Tag.notifications("Unable to delete channel group %s:\n%s", groupId, Log.toString(t));
    }
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
    NotificationManager m = (NotificationManager) AppContext.get().getSystemService(Context.NOTIFICATION_SERVICE);
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

  @RequiresApi(Build.VERSION_CODES.O)
  public Object getChannel (TdlibNotificationGroup group, boolean allowDisabled) throws ChannelCreationFailureException {
    return getChannel(group.getChatId(), group.isMention(), group.singleSenderId(), allowDisabled);
  }

  @RequiresApi(Build.VERSION_CODES.O)
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

  @RequiresApi(Build.VERSION_CODES.O)
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

  private static Object newCallChannel (
    String channelId,
    String channelName,
    String groupId,
    int vibrateMode,
    @Nullable String ringtone
  ) {
    android.app.NotificationChannel channel = new android.app.NotificationChannel(
      channelId,
      channelName,
      NotificationManager.IMPORTANCE_HIGH
    );
    channel.setGroup(groupId);
    // Set also without vibration, for one turned on in the system; before enableVibration, as a
    // pattern turns vibration on
    channel.setVibrationPattern(TdlibNotificationManager.VIBRATE_CALL_LONG_PATTERN);
    channel.enableVibration(vibrateMode != TdlibNotificationManager.VIBRATE_MODE_DISABLED);
    if (ringtone != null && ringtone.isEmpty()) {
      channel.setSound(null, null);
    } else {
      Uri sound = ringtone != null ?
        Uri.parse(ringtone) :
        TdlibNotificationManager.getTelegramCallRingtone();
      channel.setSound(sound, new android.media.AudioAttributes.Builder()
        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build());
    }
    return channel;
  }

  private static Object makeCallChannel (
    Tdlib tdlib,
    long accountUserId,
    boolean isDebug,
    int globalVersion,
    long userChatId,
    long channelVersion
  ) {
    String channelId = makeCallChannelId(accountUserId, globalVersion, userChatId, channelVersion);
    String groupId = makeGroupId(accountUserId, isDebug);
    TdlibNotificationManager notifications = tdlib.notifications();
    if (userChatId == 0) {
      Object channel = newCallChannel(
        channelId,
        Lang.getString(R.string.NotificationChannelIncomingCalls),
        groupId,
        notifications.getSavedCallVibrateMode(),
        notifications.getSavedCallRingtone()
      );
      setCallChannelDescription(tdlib, channel, 0);
      return channel;
    }
    String customRingtone = notifications.getCustomCallRingtone(userChatId);
    int customVibrateMode = notifications.getCustomCallVibrateModeForChat(userChatId);
    boolean inheritRingtone = customRingtone == null;
    boolean inheritVibration = customVibrateMode == TdlibNotificationManager.VIBRATE_MODE_DEFAULT;
    Object channel = newCallChannel(
      channelId,
      getCallChannelName(tdlib.chatTitle(userChatId)),
      groupId,
      inheritVibration ? notifications.getCallVibrateMode() : customVibrateMode,
      inheritRingtone ? notifications.getCallRingtone() : customRingtone
    );
    setCallChannelDescription(tdlib, channel, userChatId);
    Object accountChannel = notifications.getSystemCallChannel(0);
    if (accountChannel != null) {
      inheritCallChannel(channel, accountChannel, inheritRingtone, inheritVibration);
    }
    return channel;
  }

  private static void inheritCallChannel (
    Object channelObj,
    Object parentChannelObj,
    boolean inheritRingtone,
    boolean inheritVibration
  ) {
    android.app.NotificationChannel channel = (android.app.NotificationChannel) channelObj;
    android.app.NotificationChannel parent = (android.app.NotificationChannel) parentChannelObj;
    channel.setImportance(parent.getImportance());
    channel.enableLights(parent.shouldShowLights());
    channel.setLightColor(parent.getLightColor());
    channel.setLockscreenVisibility(parent.getLockscreenVisibility());
    channel.setShowBadge(parent.canShowBadge());
    channel.setBypassDnd(parent.canBypassDnd());
    if (inheritRingtone) {
      channel.setSound(parent.getSound(), parent.getAudioAttributes());
    }
    if (inheritVibration) {
      long[] vibrationPattern = parent.getVibrationPattern();
      if (vibrationPattern != null) {
        channel.setVibrationPattern(vibrationPattern);
      }
      channel.enableVibration(parent.shouldVibrate());
    }
    if (channel.shouldVibrate() && channel.getVibrationPattern() == null) {
      channel.setVibrationPattern(TdlibNotificationManager.VIBRATE_CALL_LONG_PATTERN);
    }
  }

  private static String getCallChannelName (String chatTitle) {
    return Lang.getString(R.string.NotificationChannelCustomCalls, chatTitle);
  }

  private static void setCallChannelDescription (Tdlib tdlib, Object channelObj, long userChatId) {
    android.app.NotificationChannel channel = (android.app.NotificationChannel) channelObj;
    if (userChatId != 0) {
      String userName = tdlib.cache().userName(ChatId.toUserId(userChatId));
      channel.setDescription(Lang.getString(R.string.NotificationChannelUserCalls, userName));
    } else {
      channel.setDescription(Lang.getString(R.string.NotificationChannelCommonCalls));
    }
  }

  public static boolean canRing (int importance) {
    return importance >= NotificationManager.IMPORTANCE_HIGH;
  }

  public static boolean canShowCall (int importance, int lockscreenVisibility) {
    return importance >= NotificationManager.IMPORTANCE_HIGH &&
      lockscreenVisibility != Notification.VISIBILITY_SECRET;
  }

  public static int getCallVibrateMode (boolean shouldVibrate, @Nullable long[] vibrationPattern) {
    if (!shouldVibrate) {
      return TdlibNotificationManager.VIBRATE_MODE_DISABLED;
    }
    if (Arrays.equals(vibrationPattern, TdlibNotificationManager.VIBRATE_CALL_SHORT_PATTERN)) {
      return TdlibNotificationManager.VIBRATE_MODE_SHORT;
    }
    // The pattern of "Default" and of a long one saved before
    return TdlibNotificationManager.VIBRATE_MODE_DEFAULT;
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

  @RequiresApi(Build.VERSION_CODES.O)
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

  @RequiresApi(Build.VERSION_CODES.O)
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

  public static String makeCallChannelId (
    long accountUserId,
    int globalVersion,
    long userChatId,
    long channelVersion
  ) {
    StringBuilder b = new StringBuilder(makePrefix(accountUserId, globalVersion));
    b.append(CALLS_SUFFIX);
    if (userChatId != 0) {
      b.append(CALLS_USER_SUFFIX);
      b.append(userChatId);
    }
    if (channelVersion != 0) {
      b.append('_');
      b.append(channelVersion);
    }
    return b.toString();
  }

  static boolean isCallChannelId (String data) {
    return data.startsWith(CALLS_SUFFIX);
  }

  static long parseCallChannelUserChatId (String data) {
    int start = CALLS_SUFFIX.length() + CALLS_USER_SUFFIX.length();
    if (!data.startsWith(CALLS_USER_SUFFIX, CALLS_SUFFIX.length())) {
      return 0;
    }
    int end = data.indexOf('_', start);
    return StringUtils.parseLong(data.substring(start, end != -1 ? end : data.length()));
  }

  static long parseCallChannelVersion (String data) {
    int versionIndex = data.startsWith(CALLS_USER_SUFFIX, CALLS_SUFFIX.length()) ?
      data.indexOf('_', CALLS_SUFFIX.length() + CALLS_USER_SUFFIX.length()) :
      data.indexOf('_', CALLS_SUFFIX.length());
    return versionIndex != -1 ? StringUtils.parseLong(data.substring(versionIndex + 1)) : 0;
  }

  public static void updateGroup (@NonNull TdApi.User user, boolean isDebug) {
    NotificationManager m = (NotificationManager) AppContext.get().getSystemService(Context.NOTIFICATION_SERVICE);
    if (m != null) {
      final String groupId = makeGroupId(user.id, isDebug);
      final String groupName = getChannelGroupName(user.id, user, isDebug);
      android.app.NotificationChannelGroup group;
      group = new android.app.NotificationChannelGroup(groupId, groupName);
      m.createNotificationChannelGroup(group);
    }
  }

  @RequiresApi(Build.VERSION_CODES.O)
  public static void cleanupChannels (Tdlib tdlib) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager m = (NotificationManager) AppContext.get().getSystemService(Context.NOTIFICATION_SERVICE);
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
          long userId = StringUtils.parseLong(groupId.substring(groupPrefix.length()));
          if (userId != accountUserId)
            continue;
          String id = channel.getId();
          boolean ok = false;
          if (!StringUtils.isEmpty(id) && id.startsWith(prefix)) {
            String data = id.substring(prefix.length());
            if (isCallChannelId(data)) {
              long userChatId = parseCallChannelUserChatId(data);
              long currentVersion = tdlib.notifications().getCallChannelVersion(userChatId);
              ok = parseCallChannelVersion(data) == currentVersion;
            } else if (data.startsWith(PRIVATE_SUFFIX)) {
              int versionIndex = data.indexOf('_', PRIVATE_SUFFIX.length());
              long version = versionIndex != -1 ? StringUtils.parseLong(data.substring(versionIndex + 1)) : 0;
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
            deleteChannel(m, id);
          }
        }
        if (removedChannels != null) {
          TDLib.Tag.notifications( "Removed deprecated channels: %s", Strings.join(", ", removedChannels));
        }
      }
    }
  }

  public static void deleteLegacyCallChannels () {
    NotificationManager m =
      (NotificationManager) AppContext.get().getSystemService(Context.NOTIFICATION_SERVICE);
    if (m == null) {
      return;
    }
    for (android.app.NotificationChannel channel : m.getNotificationChannels()) {
      String channelId = channel.getId();
      if (channelId.startsWith(LEGACY_CALL_PREFIX) && StringUtils.isEmpty(channel.getGroup())) {
        deleteChannel(m, channelId);
      }
    }
  }

  @RequiresApi(Build.VERSION_CODES.O)
  public static void cleanupChannelGroups (TdlibManager context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager m = (NotificationManager) AppContext.get().getSystemService(Context.NOTIFICATION_SERVICE);
      if (m == null)
        return;
      List<android.app.NotificationChannelGroup> groups = m.getNotificationChannelGroups();
      if (groups != null && !groups.isEmpty()) {

        for (int j = 0; j < 2; j++) {
          boolean isDebug = j == 1;
          Set<Long> userIds = context.availableUserIdsSet(isDebug);
          String prefix = isDebug ? ACCOUNT_PREFIX_DEBUG : ACCOUNT_PREFIX;
          for (int i = groups.size() - 1; i >= 0; i--) {
            android.app.NotificationChannelGroup group = groups.get(i);
            String groupId = group.getId();
            if (!StringUtils.isEmpty(groupId) && groupId.startsWith(prefix)) {
              long userId = StringUtils.parseLong(groupId.substring(prefix.length()));
              if (userId == 0 || !userIds.contains(userId)) {
                deleteChannelGroup(m, groupId);
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
    NotificationManager m = (NotificationManager) AppContext.get().getSystemService(Context.NOTIFICATION_SERVICE);
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

  public static String ensureCallChannel (
    Tdlib tdlib,
    long accountUserId,
    boolean isDebug,
    int globalVersion,
    long userChatId,
    long channelVersion
  ) throws ChannelCreationFailureException {
    NotificationManager m =
      (NotificationManager) AppContext.get().getSystemService(Context.NOTIFICATION_SERVICE);
    if (m == null) {
      throw new ChannelCreationFailureException("Notification service unavailable");
    }
    String channelId = makeCallChannelId(accountUserId, globalVersion, userChatId, channelVersion);
    if (m.getNotificationChannel(channelId) == null) {
      TdApi.User account = tdlib.myUser();
      if (account != null) {
        updateGroup(account, isDebug);
      }
      Object channel =
        makeCallChannel(tdlib, accountUserId, isDebug, globalVersion, userChatId, channelVersion);
      createChannel(m, channel);
    }
    return channelId;
  }

  public static long[] updateCallChannelSettings (
    Tdlib tdlib,
    long accountUserId,
    boolean isDebug,
    int globalVersion,
    long userChatId,
    long channelVersion,
    boolean isRingtoneChanged
  ) throws ChannelCreationFailureException {
    long[] followingChatIds = new long[0];
    if (accountUserId == 0) {
      return followingChatIds;
    }
    NotificationManager m =
      (NotificationManager) AppContext.get().getSystemService(Context.NOTIFICATION_SERVICE);
    if (m != null) {
      Set<String> channelIds = new HashSet<>();
      for (android.app.NotificationChannel channel : m.getNotificationChannels()) {
        channelIds.add(channel.getId());
      }
      Set<String> ringingChannelIds = getActiveChannelIds(m);
      if (userChatId == 0 || tdlib.notifications().hasCustomCallSettings(userChatId)) {
        Object channel =
          makeCallChannel(tdlib, accountUserId, isDebug, globalVersion, userChatId, channelVersion);
        long previousVersion =
          channelVersion == Long.MIN_VALUE ? Long.MAX_VALUE : channelVersion - 1;
        String previousChannelId =
          makeCallChannelId(accountUserId, globalVersion, userChatId, previousVersion);
        Object previousChannel = m.getNotificationChannel(previousChannelId);
        if (previousChannel != null) {
          inheritCallChannel(channel, previousChannel, !isRingtoneChanged, isRingtoneChanged);
        }
        createChannel(m, channel);
        if (userChatId == 0 && previousChannel != null) {
          followingChatIds = followAccountCallChannel(tdlib, m, channelIds, ringingChannelIds,
            accountUserId, isDebug, globalVersion, channel, previousChannel, isRingtoneChanged);
        }
      }
      deleteOldCallChannels(m, channelIds, ringingChannelIds, accountUserId, globalVersion,
        userChatId, channelVersion);
    }
    return followingChatIds;
  }

  private static long[] followAccountCallChannel (
    Tdlib tdlib,
    NotificationManager m,
    Set<String> channelIds,
    Set<String> ringingChannelIds,
    long accountUserId,
    boolean isDebug,
    int globalVersion,
    Object accountChannelObj,
    Object previousChannelObj,
    boolean isRingtoneChanged
  ) throws ChannelCreationFailureException {
    // A channel of a user keeps what the account had when it was made: what was changed neither
    // in the app nor in the system follows the account
    android.app.NotificationChannel account = (android.app.NotificationChannel) accountChannelObj;
    android.app.NotificationChannel previous = (android.app.NotificationChannel) previousChannelObj;
    TdlibNotificationManager notifications = tdlib.notifications();
    long[] userChatIds = findUserCallChatIds(accountUserId, globalVersion);
    int count = 0;
    for (long userChatId : userChatIds) {
      String userChannelId = makeCallChannelId(accountUserId, globalVersion, userChatId,
        notifications.getCallChannelVersion(userChatId));
      android.app.NotificationChannel userChannel = m.getNotificationChannel(userChannelId);
      if (userChannel == null) {
        continue;
      }
      boolean isInherited = isRingtoneChanged ?
        notifications.getSavedCustomCallRingtone(userChatId) == null &&
          TdlibNotificationManager.isSameCallSound(userChannel.getSound(), previous.getSound()) :
        notifications.getSavedCustomCallVibrateMode(userChatId) ==
          TdlibNotificationManager.VIBRATE_MODE_DEFAULT &&
          getCallVibrateMode(userChannel.shouldVibrate(), userChannel.getVibrationPattern()) ==
            getCallVibrateMode(previous.shouldVibrate(), previous.getVibrationPattern());
      if (!isInherited) {
        continue;
      }
      long channelVersion = notifications.incrementCallChannelVersion(userChatId);
      android.app.NotificationChannel channel = (android.app.NotificationChannel)
        makeCallChannel(tdlib, accountUserId, isDebug, globalVersion, userChatId, channelVersion);
      inheritCallChannel(channel, userChannel, true, true);
      // Its name as it was, also while the chat isn't loaded
      channel.setName(userChannel.getName());
      channel.setDescription(userChannel.getDescription());
      if (isRingtoneChanged) {
        channel.setSound(account.getSound(), account.getAudioAttributes());
      } else {
        channel.setVibrationPattern(account.getVibrationPattern());
        channel.enableVibration(account.shouldVibrate());
      }
      createChannel(m, channel);
      deleteOldCallChannels(m, channelIds, ringingChannelIds, accountUserId, globalVersion,
        userChatId, channelVersion);
      userChatIds[count++] = userChatId;
    }
    return Arrays.copyOf(userChatIds, count);
  }

  private static void deleteOldCallChannels (
    NotificationManager m,
    Set<String> channelIds,
    Set<String> ringingChannelIds,
    long accountUserId,
    int globalVersion,
    long userChatId,
    long channelVersion
  ) {
    while (channelVersion > 0) {
      String channelId =
        makeCallChannelId(accountUserId, globalVersion, userChatId, --channelVersion);
      if (channelIds.contains(channelId) && !ringingChannelIds.contains(channelId)) {
        deleteChannel(m, channelId);
      }
    }
  }

  @RequiresApi(Build.VERSION_CODES.O)
  public static void resetCallChannels (
    Tdlib tdlib,
    long accountUserId,
    boolean isDebug,
    int globalVersion,
    long channelVersion
  ) throws ChannelCreationFailureException {
    if (accountUserId == 0) {
      return;
    }
    NotificationManager m =
      (NotificationManager) AppContext.get().getSystemService(Context.NOTIFICATION_SERVICE);
    if (m == null) {
      return;
    }
    String channelId = makeCallChannelId(accountUserId, globalVersion, 0, channelVersion);
    String prefix = makePrefix(accountUserId, globalVersion);
    Set<String> ringingChannelIds = getActiveChannelIds(m);
    for (android.app.NotificationChannel channel : m.getNotificationChannels()) {
      String id = channel.getId();
      if (!id.startsWith(prefix + CALLS_SUFFIX) || id.equals(channelId)) {
        continue;
      }
      if (!ringingChannelIds.contains(id)) {
        deleteChannel(m, id);
        continue;
      }
      // One that rings now goes with its call, as a stale version
      long userChatId = parseCallChannelUserChatId(id.substring(prefix.length()));
      if (userChatId != 0) {
        tdlib.notifications().incrementCallChannelVersion(userChatId);
      }
    }
    createChannel(m,
      makeCallChannel(tdlib, accountUserId, isDebug, globalVersion, 0, channelVersion));
  }

  private static Set<String> getActiveChannelIds (NotificationManager m) {
    Set<String> channelIds = new HashSet<>();
    try {
      for (android.service.notification.StatusBarNotification notification :
        m.getActiveNotifications()) {
        channelIds.add(notification.getNotification().getChannelId());
      }
    } catch (Throwable t) {
      TDLib.Tag.notifications("Unable to get active notifications:\n%s", Log.toString(t));
    }
    return channelIds;
  }

  public static void updateChat (Tdlib tdlib, long accountUserId, TdApi.Chat chat) throws ChannelCreationFailureException {
    if (accountUserId == 0) {
      return;
    }
    NotificationManager m = (NotificationManager) AppContext.get().getSystemService(Context.NOTIFICATION_SERVICE);
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
      if (ChatId.isPrivate(chat.id)) {
        Object callChannel = tdlib.notifications().getSystemCallChannel(chat.id);
        if (callChannel != null) {
          ((android.app.NotificationChannel) callChannel).setName(
            getCallChannelName(tdlib.chatTitle(chat))
          );
          setCallChannelDescription(tdlib, callChannel, chat.id);
          createChannel(m, callChannel);
        }
      }
    }
  }

  public static void deleteChannels (Tdlib tdlib, long accountUserId, boolean isDebug, @Nullable TdApi.User account, boolean recreate) {
    if (account == null) {
      return;
    }
    NotificationManager m = (NotificationManager) AppContext.get().getSystemService(Context.NOTIFICATION_SERVICE);
    if (m != null) {
      List<android.app.NotificationChannel> channels = m.getNotificationChannels();
      final String groupId = makeGroupId(accountUserId, isDebug);
      if (channels != null && !channels.isEmpty()) {
        for (int i = channels.size() - 1; i >= 0; i--) {
          android.app.NotificationChannel channel = channels.get(i);
          String channelGroupId = channel.getGroup();
          String channelId = channel.getId();
          if (StringUtils.equalsOrBothEmpty(channelGroupId, groupId)) {
            deleteChannel(m, channelId);
          }
        }
      }
      List<android.app.NotificationChannelGroup> groups = m.getNotificationChannelGroups();
      if (groups != null && !groups.isEmpty()) {
        for (int i = groups.size() - 1; i >= 0; i--) {
          android.app.NotificationChannelGroup group = groups.get(i);
          if (StringUtils.equalsOrBothEmpty(group.getId(), groupId)) {
            deleteChannelGroup(m, groupId);
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
