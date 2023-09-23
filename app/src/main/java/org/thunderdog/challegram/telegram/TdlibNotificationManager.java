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
 * File created on 20/11/2016
 */
package org.thunderdog.challegram.telegram;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.Message;
import android.util.SparseIntArray;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.FileProvider;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.TDLib;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.BaseThread;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.helper.Recorder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.sync.SyncAdapter;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MainController;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Passcode;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.FileUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.leveldb.LevelDB;
import me.vkryl.td.ChatId;

public class TdlibNotificationManager implements UI.StateListener, Passcode.LockListener, CleanupStartupDelegate {
  public static final int ID_MUSIC = Integer.MAX_VALUE;
  public static final int ID_LOCATION = Integer.MAX_VALUE - 1;
  public static final int ID_ONGOING_CALL_NOTIFICATION = Integer.MAX_VALUE - 2;
  public static final int ID_INCOMING_CALL_NOTIFICATION = Integer.MAX_VALUE - 3;
  public static final int ID_PENDING_TASK = Integer.MAX_VALUE - 4;
  public static final int IDS_COUNT = 5;
  public static final int IDS_PER_ACCOUNT = (int) ((long) (Integer.MAX_VALUE - IDS_COUNT) / (long) TdlibAccount.ID_MAX) - 1;

  public static int calculateBaseNotificationId (Tdlib tdlib) {
    return 1 + IDS_PER_ACCOUNT * tdlib.id();
  }

  @Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
  private @interface NotificationThread { }

  private static final int PLAY_SOUND = 0;
  private static final int REBUILD_NOTIFICATION = 1;
  private static final int ENSURE_CHANNELS = 2;
  private static final int ON_UPDATE_NOTIFICATION_CHANNELS = 3;
  private static final int ON_UPDATE_ACTIVE_NOTIFICATIONS = 4;
  // private static final int ON_UPDATE_HAVE_PENDING_NOTIFICATIONS = 5;
  private static final int ON_UPDATE_NOTIFICATION_GROUP = 6;
  private static final int ON_UPDATE_NOTIFICATION = 7;
  private static final int ON_HIDE_ALL_NOTIFICATIONS = 8;
  private static final int ON_HIDE_NOTIFICATION = 9;
  private static final int ON_REBUILD_NOTIFICATIONS = 10;
  private static final int ON_REBUILD_NOTIFICATIONS_SPECIFIC = 11;
  private static final int CLEANUP_CHANNELS = 12;
  private static final int ON_CHAT_OPENED = 13;
  private static final int ON_DROP_NOTIFICATION_DATA = 14;
  private static final int RELEASE_REFERENCE = 15;
  private static final int RELEASE_REFERENCE_WITH_TASK = 16;
  private static final int ON_UPDATE_MY_USER_ID = 17;
  private static final int ON_UPDATE_MY_USER = 18;
  private static final int ON_REBUILD_NOTIFICATION_GROUP = 19;
  private static final int ON_RESTART = 20;
  private static final int REMOVE_NOTIFICATIONS = 21;

  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public static final String _NOTIFICATIONS_STACK_KEY = "notifications";

  private static final String _INAPP_VIBRATE_KEY = "inapp_vibrate";
  private static final String _INAPP_SOUNDS_KEY = "inapp_sounds";
  private static final String _INAPP_CHATSOUNDS_KEY = "inapp_chatSounds";

  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public static final String _CUSTOM_USER_SUFFIX = "user_";

  public static final String _CUSTOM_SOUND_KEY = "custom_sound_";
  public static final String _CUSTOM_SOUND_NAME_KEY = "custom_sound_name_";
  public static final String _CUSTOM_SOUND_PATH_KEY = "custom_sound_path_";
  public static final String _CUSTOM_VIBRATE_KEY = "custom_vibrate_";
  public static final String _CUSTOM_VIBRATE_ONLYSILENT_KEY = "custom_vibrate_onlysilent_";
  public static final String _CUSTOM_PRIORITY_KEY = "custom_priority_";
  public static final String _CUSTOM_IMPORTANCE_KEY = "custom_importance_";
  public static final String _CUSTOM_PRIORITY_OR_IMPORTANCE_KEY = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? _CUSTOM_IMPORTANCE_KEY : _CUSTOM_PRIORITY_KEY;

  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public static final String __CUSTOM_PINNED_NOTIFICATIONS_KEY = "custom_pinned_notifications_";

  private static final String _CUSTOM_LED_KEY = "custom_led_";

  private static final String _CUSTOM_CALL_RINGTONE_KEY = "custom_call_ringtone_";
  private static final String _CUSTOM_CALL_RINGTONE_NAME_KEY = "custom_call_ringtone_name_";
  private static final String _CUSTOM_CALL_RINGTONE_PATH_KEY = "custom_call_ringtone_path_";
  private static final String _CUSTOM_CALL_VIBRATE_KEY = "custom_call_vibrate_";
  private static final String _CUSTOM_CALL_VIBRATE_ONLYSILENT_KEY = "custom_call_vibrate_onlysilent_";

  private static final String _CHANNEL_VERSION_GLOBAL_KEY = "channels_version_global"; // resets all channels
  static final String KEY_PREFIX_CHANNEL_VERSION = "channels_version_"; // resets only default private chat settings
  private static final String _CHANNEL_VERSION_CUSTOM_KEY = "channels_version_custom_"; // resets only specific chat channel

  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public static final String __PRIVATE_MUTE_KEY = "private_mute";
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public static final String __PRIVATE_PREVIEW_KEY = "private_preview";

  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public static final String __GROUP_MUTE_KEY = "groups_mute";
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public static final String __GROUP_PREVIEW_KEY = "groups_preview";

  static final String KEY_SUFFIX_SOUND = "_sounds";
  static final String KEY_SUFFIX_SOUND_NAME = "_sounds_name";
  static final String KEY_SUFFIX_SOUND_PATH = "_sounds_path";
  static final String KEY_SUFFIX_VIBRATE = "_vibrate";
  static final String KEY_SUFFIX_VIBRATE_ONLYSILENT = "_vibrate_onlysilent";
  static final String KEY_SUFFIX_LED = "_led";
  static final String KEY_SUFFIX_CONTENT_PREVIEW = "_content_preview";
  private static final String KEY_SUFFIX_PRIORITY_KEY = "_priority";
  private static final String KEY_SUFFIX_IMPORTANCE_KEY = "_importance";

  private static final int DEFAULT_REPEAT_NOTIFICATIONS_TIME = 120;

  static final String KEY_SUFFIX_PRIORITY_OR_IMPORTANCE_KEY =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
      KEY_SUFFIX_IMPORTANCE_KEY :
      KEY_SUFFIX_PRIORITY_KEY;
  public static final int DEFAULT_PRIORITY_OR_IMPORTANCE =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
      NotificationManager.IMPORTANCE_HIGH :
      Notification.PRIORITY_HIGH;
  public static final int PRIORITY_OR_IMPORTANCE_UNSET = -100;

  /*private static final String _PRIVATE_SOUND_KEY = "private_sounds";
  private static final String _PRIVATE_SOUND_NAME_KEY = "private_sounds_name";
  private static final String _PRIVATE_VIBRATE_KEY = "private_vibrate";
  private static final String _PRIVATE_VIBRATE_ONLYSILENT_KEY = "private_vibrate_onlysilent";
  private static final String _PRIVATE_LED_KEY = "private_led";
  private static final String _PRIVATE_CONTENT_PREVIEW = "private_content_preview";
  private static final String _PRIVATE_PRIORITY_KEY = "private_priority";
  private static final String _PRIVATE_IMPORTANCE_KEY = "private_importance";*/

  /*private static final String _GROUP_SOUND_KEY = "groups_sounds";
  private static final String _GROUP_SOUND_NAME_KEY = "groups_sounds_name";
  private static final String _GROUP_VIBRATE_KEY = "groups_vibrate";
  private static final String _GROUP_VIBRATE_ONLYSILENT_KEY = "group_vibrate_onlysilent";
  private static final String _GROUP_LED_KEY = "group_led";
  private static final String _GROUP_CONTENT_PREVIEW = "group_content_preview";
  private static final String _GROUP_PRIORITY_KEY = "group_priority";
  private static final String _GROUP_IMPORTANCE_KEY = "group_importance";*/

  /*private static final String _CHANNEL_SOUND_KEY = "channels_sounds";
  private static final String _CHANNEL_SOUND_NAME_KEY = "channels_sounds_name";
  private static final String _CHANNEL_VIBRATE_KEY = "channels_vibrate";
  private static final String _CHANNEL_VIBRATE_ONLYSILENT_KEY = "channels_vibrate_onlysilent";
  private static final String _CHANNEL_LED_KEY = "channels_led";
  private static final String _CHANNEL_CONTENT_PREVIEW = "channels_content_preview";
  private static final String _CHANNEL_PRIORITY_KEY = "channels_priority";
  private static final String _CHANNEL_IMPORTANCE_KEY = "channels_importance";*/

  private static final String _CALL_RINGTONE_KEY = "voice_ringtone";
  private static final String _CALL_RINGTONE_NAME_KEY = "voice_ringtone_name";
  private static final String _CALL_RINGTONE_PATH_KEY = "voice_ringtone_path";
  private static final String _CALL_VIBRATE_KEY = "voice_vibrate";
  private static final String _CALL_VIBRATE_ONLYSILENT_KEY = "voice_vibrate_onlysilent";

  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public static final String __PINNED_MESSAGE_NOTIFICATION_KEY = "pinned_message_notification";

  private static final String _REPEAT_NOTIFICATION_MINUTES_KEY = "repeat_notification_minutes";

  public static final int VIBRATE_MODE_DEFAULT = 0;
  public static final int VIBRATE_MODE_SHORT = 1;
  public static final int VIBRATE_MODE_LONG = 2;
  public static final int VIBRATE_MODE_DISABLED = 3;

  public static final long[] VIBRATE_SHORT_PATTERN = {0, 100, 0, 100};
  public static final long[] VIBRATE_LONG_PATTERN = {0, 1000};

  public static final long[] VIBRATE_CALL_SHORT_PATTERN = {0, 140, 0, 140, 750};
  public static final long[] VIBRATE_CALL_LONG_PATTERN = {0, 1000, 1000};

  public static final int[] LED_COLORS =
    {0xffffffff, 0xff0000ff, 0xffff0000, 0xffff8e01, 0xffffff00, 0xff00ff00, 0xff00ffff, 0xffd274f9, 0xffff00ff};
  public static final int LED_COLOR_DEFAULT = LED_COLORS[1]; // Blue
  public static final int LED_COLOR_UNSET = 0;
  public static final int[] LED_COLORS_IDS = {
    ColorId.ledWhite,
    ColorId.ledBlue,
    ColorId.ledRed,
    ColorId.ledOrange,
    ColorId.ledYellow,
    ColorId.ledGreen,
    ColorId.ledCyan,
    ColorId.ledPurple,
    ColorId.ledPink
  };

  public static final int[] LED_COLORS_STRINGS = {
    R.string.LedWhite,
    R.string.LedBlue,
    R.string.LedRed,
    R.string.LedOrange,
    R.string.LedYellow,
    R.string.LedGreen,
    R.string.LedCyan,
    R.string.LedPurple,
    R.string.LedPink
  };

  private final Tdlib tdlib;
  private final TdlibNotificationHelper notification;
  private final NotificationQueue queue;

  @Nullable
  private AudioManager _audioManager;

  private AudioManager audioManager () {
    if (_audioManager == null) {
      try {
        _audioManager = (AudioManager) UI.getAppContext().getSystemService(Context.AUDIO_SERVICE);
      } catch (Throwable t) {
        Log.e(Log.TAG_FCM, "Context.AUDIO_SERVICE is not available", t);
      }
    }
    return _audioManager;
  }

  @Nullable
  private Boolean _inAppChatSounds;

  @Nullable
  private Integer _channelGlobalVersion;
  private final LocalScopeNotificationSettings privateSettings, groupSettings, channelSettings;
  private @Nullable TdApi.ScopeNotificationSettings settingsForPrivateChats, settingsForGroupChats, settingsForChannelChats;

  private Integer _callVibrate;
  private Boolean _callVibrateOnlyIfSilent;
  private boolean callRingtoneLoaded, callRingtoneNameLoaded, callRingtonePathLoaded;
  private @Nullable String _callRingtone, _callRingtoneName, _callRingtonePath;

  @Nullable
  private Integer _repeatNotificationMinutes;

  public static String key (String key, int accountId) {
    return accountId != 0 ? "account" + accountId + "_" + key : key;
  }

  public static class NotificationQueue extends BaseThread {
    private final TdlibManager context;

    public NotificationQueue (String name, TdlibManager context) {
      super(name);
      this.context = context;
    }

    public void init () {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        sendMessage(Message.obtain(getHandler(), CLEANUP_CHANNELS), 0);
      }
    }

    @Override
    protected void process (Message msg) {
      switch (msg.what) {
        case CLEANUP_CHANNELS: {
          TdlibNotificationChannelGroup.cleanupChannelGroups(context);
          break;
        }
        case PLAY_SOUND: {
          ((TdlibNotificationManager) msg.obj).playSound(msg.arg1, msg.arg2);
          break;
        }
        case REBUILD_NOTIFICATION: {
          ((TdlibNotificationManager) msg.obj).rebuildNotification();
          break;
        }
        case ENSURE_CHANNELS: {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Tdlib tdlib = ((TdlibNotificationManager) msg.obj).tdlib;
            TdlibNotificationChannelGroup.cleanupChannelGroups(context);
            try {
              tdlib.notifications().createChannels();
            } catch (TdlibNotificationChannelGroup.ChannelCreationFailureException e) {
              TDLib.Tag.notifications("Unable to create notification channels:\n%s", Log.toString(e));
              tdlib.settings().trackNotificationChannelProblem(e, 0);
            }
            TdlibNotificationChannelGroup.cleanupChannels(tdlib);
          }
          break;
        }
        case ON_UPDATE_NOTIFICATION_CHANNELS: {
          ((TdlibNotificationManager) msg.obj).onUpdateNotificationChannels(BitwiseUtils.mergeLong(msg.arg1, msg.arg2));
          break;
        }
        case ON_UPDATE_ACTIVE_NOTIFICATIONS: {
          Object[] obj = (Object[]) msg.obj;
          ((TdlibNotificationManager) obj[0]).processActiveNotifications((TdApi.UpdateActiveNotifications) obj[1]);
          obj[0] = obj[1] = null;
          break;
        }
        /*case ON_UPDATE_HAVE_PENDING_NOTIFICATIONS: {
          Object[] obj = (Object[]) msg.obj;
          ((TdlibNotificationManager) obj[0]).processHavePendingNotifications((TdApi.UpdateHavePendingNotifications) obj[1]);
          obj[0] = obj[1] = null;
          break;
        }*/
        case ON_UPDATE_NOTIFICATION_GROUP: {
          Object[] obj = (Object[]) msg.obj;
          ((TdlibNotificationManager) obj[0]).processNotificationGroup((TdApi.UpdateNotificationGroup) obj[1]);
          obj[0] = obj[1] = null;
          break;
        }
        case ON_UPDATE_NOTIFICATION: {
          Object[] obj = (Object[]) msg.obj;
          ((TdlibNotificationManager) obj[0]).processNotification((TdApi.UpdateNotification) obj[1]);
          obj[0] = obj[1] = null;
          break;
        }
        case ON_HIDE_ALL_NOTIFICATIONS: {
          ((TdlibNotificationManager) msg.obj).onHideAllImpl(msg.arg1);
          break;
        }
        case ON_HIDE_NOTIFICATION: {
          Object[] obj = (Object[]) msg.obj;
          ((TdlibNotificationManager) obj[0]).onHideImpl((TdlibNotificationExtras) obj[1]);
          obj[0] = obj[1] = null;
          break;
        }
        case ON_REBUILD_NOTIFICATIONS: {
          Object[] obj = (Object[]) msg.obj;
          ((TdlibNotificationManager) obj[0]).rebuildNotificationsImpl((TdApi.NotificationSettingsScope) obj[1]);
          obj[0] = obj[1] = null;
          break;
        }
        case ON_REBUILD_NOTIFICATIONS_SPECIFIC: {
          ((TdlibNotificationManager) msg.obj).rebuildNotificationsImpl(BitwiseUtils.mergeLong(msg.arg1, msg.arg2));
          break;
        }
        case ON_CHAT_OPENED: {
          ((TdlibNotificationManager) msg.obj).onChatOpenedImpl(BitwiseUtils.mergeLong(msg.arg1, msg.arg2));
          break;
        }
        case ON_DROP_NOTIFICATION_DATA: {
          ((TdlibNotificationManager) msg.obj).onDropNotificationDataImpl(msg.arg1 == 1);
          break;
        }
        case RELEASE_REFERENCE: {
          ((TdlibNotificationManager) msg.obj).tdlib.decrementNotificationReferenceCount();
          break;
        }
        case RELEASE_REFERENCE_WITH_TASK: {
          Object[] data = (Object[]) msg.obj;
          ((Runnable) data[1]).run();
          ((TdlibNotificationManager) data[0]).tdlib.decrementNotificationReferenceCount();
          data[0] = data[1] = null;
          break;
        }
        case ON_UPDATE_MY_USER_ID: {
          ((TdlibNotificationManager) msg.obj).onUpdateMyUserIdImpl(BitwiseUtils.mergeLong(msg.arg1, msg.arg2));
          break;
        }
        case ON_UPDATE_MY_USER: {
          Object[] data = (Object[]) msg.obj;
          ((TdlibNotificationManager) data[0]).onUpdateMyUserImpl((TdApi.User) data[1]);
          data[0] = data[1] = null;
          break;
        }
        case ON_REBUILD_NOTIFICATION_GROUP: {
          ((TdlibNotificationManager) msg.obj).rebuildNotificationGroupImpl(msg.arg1);
          break;
        }
        case ON_RESTART: {
          ((TdlibNotificationManager) msg.obj).onRestartImpl();
          break;
        }
        case REMOVE_NOTIFICATIONS: {
          Object[] data = (Object[]) msg.obj;
          ((TdlibNotificationManager) data[0]).removeNotificationGroupImpl((TdlibNotificationExtras) data[1]);
          data[0] = data[1] = null;
          break;
        }
      }
    }
  }

  private String key (String key) {
    return key(key, tdlib.id());
  }

  TdlibNotificationManager (Tdlib tdlib, NotificationQueue queue) {
    this.tdlib = tdlib;
    this.queue = queue;
    tdlib.listeners().addCleanupListener(this);
    this.notification = new TdlibNotificationHelper(this, tdlib);

    final int accountId = tdlib.id();


    // Sounds
    this.sounds = new SparseIntArray();
    this.loadedSounds = new SparseIntArray();

    // In-app stuff
    privateSettings = new LocalScopeNotificationSettings(accountId, new TdApi.NotificationSettingsScopePrivateChats());
    groupSettings = new LocalScopeNotificationSettings(accountId, new TdApi.NotificationSettingsScopeGroupChats());
    channelSettings = new LocalScopeNotificationSettings(accountId, new TdApi.NotificationSettingsScopeChannelChats());

    // Queue

    UI.addStateListener(this);

    Passcode.instance().addLockListener(this);

    // FIXME?
    // tdlib.context().global().addAccountListener(this);
  }

  /**
   * Called from {@link org.thunderdog.challegram.service.FirebaseListenerService} when push processing takes too long.
   * */
  public void notifyPushProcessingTakesTooLong () {
    notification.abortCancelableOperations();
  }

  public Tdlib tdlib () {
    return tdlib;
  }

  // Settings

  public boolean areInAppChatSoundsEnabled () {
    if (_inAppChatSounds == null) {
      _inAppChatSounds = Settings.instance().getBoolean(key(_INAPP_CHATSOUNDS_KEY, tdlib.id()), true);
    }
    return _inAppChatSounds;
  }

  public void toggleInAppChatSoundsEnabled () {
    this._inAppChatSounds = !areInAppChatSoundsEnabled();
    Settings.instance().putBoolean(key(_INAPP_CHATSOUNDS_KEY), _inAppChatSounds);
  }

  // Properties

  public boolean hasLocalNotificationProblem () {
    return areNotificationsBlockedGlobally() || areNotificationsBlocked(scopePrivate()) ||
      areNotificationsBlocked(scopeGroup()) || areNotificationsBlocked(scopeChannel()) ||
      !hasFirebase() ||
      tdlib.notifications().getNotificationBlockStatus() == TdlibNotificationManager.Status.ACCOUNT_NOT_SELECTED;
  }

  public boolean areNotificationsBlockedGlobally () {
    switch (getNotificationBlockStatus()) {
      case Status.BLOCKED_ALL:
      case Status.MISSING_PERMISSION:
      case Status.BLOCKED_CATEGORY:
      case Status.DISABLED_APP_SYNC:
      case Status.DISABLED_SYNC:
      case Status.FIREBASE_MISSING:
      case Status.FIREBASE_ERROR:

      case Status.INTERNAL_ERROR:
        return true;

      case Status.ACCOUNT_NOT_SELECTED:
      case Status.NOT_BLOCKED:
        return false;
    }
    return false;
  }

  public boolean needSyncAlert () {
    return isSyncDisabledGlobally() && !hasFirebase();
  }

  private boolean hasFirebase () {
    return U.isGooglePlayServicesAvailable(UI.getAppContext());
  }

  private boolean isSyncDisabledGlobally () {
    return !SyncAdapter.isSyncEnabledGlobally();
  }

  private boolean isSyncDisabledForApp () {
    return !SyncAdapter.isSyncEnabled(UI.getContext());
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    Status.NOT_BLOCKED,
    Status.BLOCKED_CATEGORY,
    Status.BLOCKED_ALL,
    Status.DISABLED_SYNC,
    Status.DISABLED_APP_SYNC,
    Status.FIREBASE_MISSING,
    Status.INTERNAL_ERROR,
    Status.ACCOUNT_NOT_SELECTED,
    Status.FIREBASE_ERROR,
    Status.MISSING_PERMISSION
  })
  public @interface Status {
    int
      NOT_BLOCKED = 0,
      BLOCKED_CATEGORY = 1,
      BLOCKED_ALL = 2,
      DISABLED_SYNC = 3,
      DISABLED_APP_SYNC = 4,
      FIREBASE_MISSING = 5,
      INTERNAL_ERROR = 6,
      ACCOUNT_NOT_SELECTED = 7,
      FIREBASE_ERROR = 8,
      MISSING_PERMISSION = 9;
  }

  public @Status
  int getNotificationBlockStatus () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(UI.getAppContext(), android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        return Status.MISSING_PERMISSION;
      }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      long selfUserId = tdlib.myUserId();
      if (selfUserId != 0) {
        android.app.NotificationChannelGroup group = (android.app.NotificationChannelGroup) getSystemChannelGroup();
        if (group != null && group.isBlocked()) {
          return Status.BLOCKED_CATEGORY;
        }
      }
    }
    if (!NotificationManagerCompat.from(UI.getAppContext()).areNotificationsEnabled()) {
      return Status.BLOCKED_ALL;
    }
    boolean hasFirebase = hasFirebase();
    if (!hasFirebase) {
      // Sync matters only when Firebase unavailable
      if (isSyncDisabledGlobally())
        return Status.DISABLED_SYNC;
      if (isSyncDisabledForApp())
        return Status.DISABLED_APP_SYNC;
      return Status.FIREBASE_MISSING;
    }
    if (tdlib.settings().hasNotificationProblems())
      return Status.INTERNAL_ERROR;
    if (!tdlib.account().forceEnableNotifications() && Settings.instance().checkNotificationFlag(Settings.NOTIFICATION_FLAG_ONLY_SELECTED_ACCOUNTS))
      return Status.ACCOUNT_NOT_SELECTED;
    if (tdlib.context().getTokenState() == TdlibManager.TokenState.ERROR)
      return Status.FIREBASE_ERROR;
    return Status.NOT_BLOCKED;
  }

  public boolean areNotificationsBlocked (TdApi.NotificationSettingsScope scope) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      return getDefaultPriorityOrImportance(scope) == android.app.NotificationManager.IMPORTANCE_NONE;
    }
    return false;
  }

  public boolean areNotificationsBlocked (long chatId, boolean allowGlobal) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      int importance = getCustomPriorityOrImportance(chatId, PRIORITY_OR_IMPORTANCE_UNSET);
      return importance == android.app.NotificationManager.IMPORTANCE_NONE || (importance == PRIORITY_OR_IMPORTANCE_UNSET && areNotificationsBlocked(scope(chatId)));
    }
    return false;
  }

  /**
   * @param chatId chat identifier
   * @return true, when at least one notification setting is not affected by default value
   */
  public boolean hasCustomChatSettings (long chatId) {
    if (chatId == 0)
      return false;
    if (hasCustomPriorityOrImportance(chatId) ||
      hasCustomLedColor(chatId))
      return true;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      android.app.NotificationChannel channel = (android.app.NotificationChannel) getSystemChannel(null, chatId);
      if (channel != null)
        return true;
      int importance = getEffectivePriorityOrImportance(chatId);
      if (importance < android.app.NotificationManager.IMPORTANCE_DEFAULT)
        return false;
    }
    return hasCustomVibrateMode(chatId) || hasCustomSound(chatId);
  }

  // Property: Vibrate Mode & Silence

  /**
   * @param scope scope
   * @return default effective vibrate mode for specified scope
   */
  public int getDefaultVibrateMode (TdApi.NotificationSettingsScope scope) {
    LocalScopeNotificationSettings settings = getLocalNotificationSettings(scope);
    return getChannelVibrateMode(scope, 0, settings.getVibrateMode());
  }

  public boolean isDefaultVibrateModeEnabled (TdApi.NotificationSettingsScope scope) {
    return getDefaultVibrateMode(scope) != TdlibNotificationManager.VIBRATE_MODE_DISABLED;
  }

  /**
   * @param scope scope
   * @return default effective vibrate mode silence option
   */
  public boolean getDefaultVibrateOnlyIfSilent (TdApi.NotificationSettingsScope scope) {
    if (Config.VIBRATE_ONLY_IF_SILENT_AVAILABLE) {
      LocalScopeNotificationSettings settings = getLocalNotificationSettings(scope);
      return settings.getVibrateOnlyIfSilent();
    }
    return false;
  }

  /**
   * @param scope scope
   * @param vibrateMode desired vibrate mode value
   * @param onlyIfSilent desired vibrate mode silence value
   */
  public boolean setDefaultVibrateMode (TdApi.NotificationSettingsScope scope, int vibrateMode, boolean onlyIfSilent) {
    if (getDefaultVibrateMode(scope) != vibrateMode || (Config.VIBRATE_ONLY_IF_SILENT_AVAILABLE && getDefaultVibrateOnlyIfSilent(scope) != onlyIfSilent)) {
      LocalScopeNotificationSettings settings = getLocalNotificationSettings(scope);
      settings.setVibrateMode(vibrateMode, onlyIfSilent);
      LevelDB editor = Settings.instance().edit();
      if (vibrateMode != VIBRATE_MODE_DEFAULT) {
        editor.putInt(settings.suffix(KEY_SUFFIX_VIBRATE), vibrateMode);
      } else {
        editor.remove(settings.suffix(KEY_SUFFIX_VIBRATE));
      }
      if (Config.VIBRATE_ONLY_IF_SILENT_AVAILABLE) {
        if (onlyIfSilent) {
          editor.putBoolean(settings.suffix(KEY_SUFFIX_VIBRATE_ONLYSILENT), onlyIfSilent);
        } else {
          editor.remove(settings.suffix(KEY_SUFFIX_VIBRATE_ONLYSILENT));
        }
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        incrementChannelVersion(scope, 0, editor);
      } else {
        editor.apply();
      }
      return true;
    }
    return false;
  }

  /**
   * @param chatId chat identifier
   * @return vibrate mode value for specific chat. {@link #VIBRATE_MODE_DEFAULT} means that value should be obtained from the surrounding scope.
   */
  public int getCustomVibrateMode (long chatId, int defaultVibrateMode) {
    return getChannelVibrateMode(null, chatId, Settings.instance().getInt(key(_CUSTOM_VIBRATE_KEY + chatId), defaultVibrateMode));
  }

  /**
   * @param chatId chat identifier
   * @return true, when vibrate mode is not affected by the default value
   */
  public boolean hasCustomVibrateMode (long chatId) {
    return getCustomVibrateMode(chatId, VIBRATE_MODE_DEFAULT) != VIBRATE_MODE_DEFAULT;
  }

  public boolean isVibrateModeEnabled (long chatId) {
    int vibrateMode = getCustomVibrateMode(chatId, VIBRATE_MODE_DEFAULT);
    if (vibrateMode == VIBRATE_MODE_DEFAULT)
      vibrateMode = getDefaultVibrateMode(scope(chatId));
    return vibrateMode != VIBRATE_MODE_DISABLED;
  }

  /**
   * @param chatId Chat identifier
   * @return stored vibrate silence value, or false by default
   */
  public boolean getCustomVibrateOnlyIfSilent (long chatId) {
    return Config.VIBRATE_ONLY_IF_SILENT_AVAILABLE && Settings.instance().getBoolean(key(_CUSTOM_VIBRATE_ONLYSILENT_KEY + chatId), false);
  }

  /**
   * @param chatId chat identifier
   * @param vibrateMode desired vibrate mode or {@link #VIBRATE_MODE_DEFAULT} to use default value
   * @param onlyIfSilent true, if vibration should be used only when sounds are disabled
   */
  public void setCustomVibrateMode (long chatId, int vibrateMode, boolean onlyIfSilent) {
    int oldVibrateMode = getCustomVibrateMode(chatId, VIBRATE_MODE_DEFAULT);
    final LevelDB editor = Settings.instance().edit();
    if (vibrateMode == VIBRATE_MODE_DEFAULT) {
      editor.remove(key(_CUSTOM_VIBRATE_KEY + chatId));
      if (Config.VIBRATE_ONLY_IF_SILENT_AVAILABLE) {
        editor.remove(key(_CUSTOM_VIBRATE_ONLYSILENT_KEY + chatId));
      }
    } else {
      editor.putInt(key(_CUSTOM_VIBRATE_KEY + chatId), vibrateMode);
      if (Config.VIBRATE_ONLY_IF_SILENT_AVAILABLE) {
        editor.putBoolean(key(_CUSTOM_VIBRATE_ONLYSILENT_KEY + chatId), onlyIfSilent);
      }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && oldVibrateMode != vibrateMode) {
      incrementChannelVersion(null, chatId, editor);
    } else {
      editor.apply();
    }
  }

  /**
   * @param chatId chat identifier
   * @return currently effective vibrate mode for the specified chat to be applied to new notifications.
   */
  public int getEffectiveVibrateMode (long chatId) {
    TdApi.NotificationSettingsScope scope = scope(chatId);
    int vibrateMode = getCustomVibrateMode(chatId, VIBRATE_MODE_DEFAULT);
    boolean silentOnly;
    if (vibrateMode != VIBRATE_MODE_DEFAULT) {
      silentOnly = Config.VIBRATE_ONLY_IF_SILENT_AVAILABLE && Settings.instance().getBoolean(key(_CUSTOM_VIBRATE_ONLYSILENT_KEY + chatId), getDefaultVibrateOnlyIfSilent(scope));
    } else {
      vibrateMode = getDefaultVibrateMode(scope);
      silentOnly = Config.VIBRATE_ONLY_IF_SILENT_AVAILABLE && getDefaultVibrateOnlyIfSilent(scope);
    }
    if (Config.VIBRATE_ONLY_IF_SILENT_AVAILABLE) {
      if (silentOnly && audioManager() != null) {
        try {
          int mode = audioManager().getRingerMode();
          if (mode != AudioManager.RINGER_MODE_SILENT && mode != AudioManager.RINGER_MODE_VIBRATE) {
            vibrateMode = VIBRATE_MODE_DISABLED;
          }
        } catch (Throwable t) {
          Log.e(Log.TAG_FCM, "Cannot get ringer mode", t);
        }
      }
    }
    return vibrateMode;
  }

  // Property: Priority or Importance

  /**
   * @param scope scope
   * @return default effective priority or importance for specified scope
   */
  public int getDefaultPriorityOrImportance (TdApi.NotificationSettingsScope scope) {
    LocalScopeNotificationSettings settings = getLocalNotificationSettings(scope);
    return getChannelPriorityOrImportance(scope, 0, settings.getPriorityOrImportance());
  }

  /**
   * @param scope scope
   * @param priorityOrImportance desired default priority or importance value
   * @return true, if value changed
   */
  public boolean setDefaultPriorityOrImportance (TdApi.NotificationSettingsScope scope, int priorityOrImportance) {
    if (getDefaultPriorityOrImportance(scope) != priorityOrImportance) {
      LocalScopeNotificationSettings settings = getLocalNotificationSettings(scope);
      settings.setPriorityOrImportance(priorityOrImportance);
      LevelDB editor = Settings.instance().edit();
      if (priorityOrImportance != DEFAULT_PRIORITY_OR_IMPORTANCE) {
        editor.putInt(settings.suffix(KEY_SUFFIX_PRIORITY_OR_IMPORTANCE_KEY), priorityOrImportance);
      } else {
        editor.remove(settings.suffix(KEY_SUFFIX_PRIORITY_OR_IMPORTANCE_KEY));
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        incrementChannelVersion(scope, 0, editor);
      } else {
        editor.apply();
      }
      return true;
    }
    return false;
  }

  /**
   * @param chatId chat identifier
   * @return true, when vibrate mode is not affected by the default value
   */
  public boolean hasCustomPriorityOrImportance (long chatId) {
    return getCustomPriorityOrImportance(chatId, PRIORITY_OR_IMPORTANCE_UNSET) != PRIORITY_OR_IMPORTANCE_UNSET;
  }

  /**
   * @param chatId chat identifier
   * @return current priority or importance value. {@link #DEFAULT_PRIORITY_OR_IMPORTANCE} means value should be obtained from the surrounding scope.
   */
  public int getCustomPriorityOrImportance (long chatId, int defaultPriorityOrImportance) {
    return getChannelPriorityOrImportance(null, chatId, Settings.instance().getInt(key(_CUSTOM_PRIORITY_OR_IMPORTANCE_KEY + chatId), defaultPriorityOrImportance));
  }

  /**
   * @param chatId chat identifier
   * @param priorityOrImportance desired priority or importance value, or {@link #PRIORITY_OR_IMPORTANCE_UNSET} to use default value
   */
  public void setCustomPriorityOrImportance (long chatId, int priorityOrImportance) {
    int oldPriorityOrImportance = getCustomPriorityOrImportance(chatId, PRIORITY_OR_IMPORTANCE_UNSET);
    final LevelDB editor = Settings.instance().edit();
    if (priorityOrImportance == PRIORITY_OR_IMPORTANCE_UNSET) {
      editor.remove(key(_CUSTOM_PRIORITY_OR_IMPORTANCE_KEY + chatId));
    } else {
      editor.putInt(key(_CUSTOM_PRIORITY_OR_IMPORTANCE_KEY + chatId), priorityOrImportance);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && oldPriorityOrImportance != priorityOrImportance) {
      incrementChannelVersion(null, chatId, editor);
    } else {
      editor.apply();
    }
  }

  /**
   * @param chatId chat identifier
   * @return currently effective priority or importance for the specified chat to be applied to new notification.
   */
  public int getEffectivePriorityOrImportance (long chatId) {
    int priorityOrImportance = getCustomPriorityOrImportance(chatId, PRIORITY_OR_IMPORTANCE_UNSET);
    if (priorityOrImportance != PRIORITY_OR_IMPORTANCE_UNSET) {
      return priorityOrImportance;
    } else {
      return getDefaultPriorityOrImportance(scope(chatId));
    }
  }

  // Property: LED Color

  public int getDefaultLedColor (TdApi.NotificationSettingsScope scope) {
    LocalScopeNotificationSettings settings = getLocalNotificationSettings(scope);
    return getChannelLedColor(scope, 0, settings.getLedColor());
  }

  public boolean setDefaultLedColor (TdApi.NotificationSettingsScope scope, int ledColor) {
    if (getDefaultLedColor(scope) != ledColor) {
      LocalScopeNotificationSettings settings = getLocalNotificationSettings(scope);
      settings.setLedColor(ledColor);
      LevelDB editor = Settings.instance().edit();
      if (ledColor != LED_COLOR_DEFAULT) {
        editor.putInt(settings.suffix(KEY_SUFFIX_LED), ledColor);
      } else {
        editor.remove(settings.suffix(KEY_SUFFIX_LED));
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        incrementChannelVersion(scope, 0, editor);
      } else {
        editor.apply();
      }
      return true;
    }
    return false;
  }

  public boolean hasCustomLedColor (long chatId) {
    return getCustomLedColor(chatId, LED_COLOR_UNSET) != LED_COLOR_UNSET;
  }

  public int getCustomLedColor (long chatId, int defaultLedColor) {
    return getChannelLedColor(null, chatId, Settings.instance().getInt(key(_CUSTOM_LED_KEY + chatId), defaultLedColor));
  }

  public void setCustomLedColor (long chatId, int ledColor) {
    int oldLedColor = getCustomLedColor(chatId, LED_COLOR_UNSET);
    LevelDB editor = Settings.instance().edit();
    if (ledColor == LED_COLOR_UNSET) {
      editor.remove(key(_CUSTOM_LED_KEY + chatId));
    } else {
      editor.putInt(key(_CUSTOM_LED_KEY + chatId), ledColor);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ledColor != oldLedColor) {
      incrementChannelVersion(null, chatId, editor);
    } else {
      editor.apply();
    }
  }

  public int getEffectiveLedColor (long chatId) {
    int ledColor = getCustomLedColor(chatId, LED_COLOR_UNSET);
    if (ledColor != LED_COLOR_UNSET) {
      return ledColor;
    } else {
      return getDefaultLedColor(scope(chatId));
    }
  }

  // Property: Sound & Sound Name

  public static boolean compareSounds (@Nullable String a, @Nullable String b) {
    return a == null == (b == null) && StringUtils.equalsOrBothEmpty(a, b);
  }

  public static String fixSoundUri (String soundUri) {
    if (StringUtils.isEmpty(soundUri))
      return soundUri;
    try {
      Uri uri = fixSoundUri(Uri.parse(soundUri), false, null);
      return uri == null ? null : uri.toString();
    } catch (Throwable ignored) { }
    return soundUri;
  }

  public static Uri fixSoundUri (@NonNull Uri uri, boolean allowCopy, @Nullable String forceFileName) {
    if (uri.equals(android.provider.Settings.System.DEFAULT_RINGTONE_URI) || uri.equals(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI))
      return null;
    if ("content".equals(uri.getScheme())) {
      if (Config.FILE_PROVIDER_AUTHORITY.equals(uri.getAuthority()) && uri.getPath().matches("^/ringtones/(?!\\.\\.)[^/]+$")) {
        return uri;
      }
      String filePath = U.tryResolveFilePath(uri);
      boolean fileAccessible = false;
      if (filePath != null && !filePath.equals(uri.toString())) {
        try {
          File file = new File(filePath);
          if (file.exists() && file.length() > 0) {
            fileAccessible = true;
          }
        } catch (Throwable ignored) {}
      }
      if (allowCopy) {
        File outputFile;

        if (!StringUtils.isEmpty(forceFileName)) {
          outputFile = new File(U.getRingtonesDir(), forceFileName);
          if (outputFile.exists() && !outputFile.delete())
            return null;
        } else {
          String name = U.getRingtoneName(uri, "other.ogg");
          String fileExtension = U.getExtension(name);
          String fileName = fileExtension != null ? name.substring(0, name.length() - fileExtension.length() - 1) : name;
          outputFile = U.newFile(U.getRingtonesDir(), fileName, fileExtension);
        }

        if ((fileAccessible && FileUtils.copy(new File(filePath), outputFile)) || U.copyFile(UI.getAppContext(), uri, outputFile)) {
          return FileProvider.getUriForFile(UI.getAppContext(), Config.FILE_PROVIDER_AUTHORITY, outputFile);
        }
      }
      return null;
    }
    return uri;
  }

  public boolean isDefaultSoundEnabled (TdApi.NotificationSettingsScope scope) {
    return !"".equals(getDefaultSound(scope));
  }

  public String getDefaultSound (TdApi.NotificationSettingsScope scope) {
    return getChannelSound(scope, 0, getSavedDefaultSound(scope));
  }

  public String getSavedDefaultSound (TdApi.NotificationSettingsScope scope) {
    return fixSoundUri(getLocalNotificationSettings(scope).getSound());
  }

  public String getDefaultSoundName (TdApi.NotificationSettingsScope scope) {
    LocalScopeNotificationSettings settings = getLocalNotificationSettings(scope);
    return getChannelSoundName(settings.getSound(), settings.getSoundName(), getDefaultSound(scope));
  }

  public String getDefaultSoundPath (TdApi.NotificationSettingsScope scope) {
    LocalScopeNotificationSettings settings = getLocalNotificationSettings(scope);
    return settings.getSoundPath();
  }

  public boolean setDefaultSound (TdApi.NotificationSettingsScope scope, @Nullable String newSound, @Nullable String newSoundName, @Nullable String newSoundPath) {
    newSound = fixSoundUri(newSound);
    LocalScopeNotificationSettings settings = getLocalNotificationSettings(scope);
    if (!compareSounds(settings.getSound(), newSound) || !StringUtils.equalsOrBothEmpty(settings.getSoundName(), newSoundName) || !StringUtils.equalsOrBothEmpty(settings.getSoundPath(), newSoundPath)) {
      settings.setSound(newSound, newSoundName, newSoundPath);
      LevelDB editor = Settings.instance().edit();
      if (newSound != null)
        editor.putString(settings.suffix(KEY_SUFFIX_SOUND), newSound);
      else
        editor.remove(settings.suffix(KEY_SUFFIX_SOUND));
      if (!StringUtils.isEmpty(newSound) && newSoundName != null)
        editor.putString(settings.suffix(KEY_SUFFIX_SOUND_NAME), newSoundName);
      else
        editor.remove(settings.suffix(KEY_SUFFIX_SOUND_NAME));
      if (!StringUtils.isEmpty(newSound) && newSoundPath != null)
        editor.putString(settings.suffix(KEY_SUFFIX_SOUND_PATH), newSoundPath);
      else
        editor.remove(settings.suffix(KEY_SUFFIX_SOUND_PATH));
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        incrementChannelVersion(scope, 0, editor);
      } else {
        editor.apply();
      }
      return true;
    }
    return false;
  }

  public String getCustomSound (long chatId, String defaultSound) {
    return getChannelSound(null, chatId, getSavedCustomSound(chatId, defaultSound));
  }

  public String getSavedCustomSound (long chatId, String defaultSound) {
    return fixSoundUri(Settings.instance().getString(key(_CUSTOM_SOUND_KEY + chatId), defaultSound));
  }

  public boolean hasCustomSound (long chatId) {
    return getCustomSound(chatId, null) != null;
  }

  public boolean isSoundEnabled (long chatId) {
    String sound = getCustomSound(chatId, null);
    return sound != null ? !"".equals(sound) : isDefaultSoundEnabled(scope(chatId));
  }

  public String getCustomSoundName (long chatId) {
    String actualSound = getCustomSound(chatId, null);
    if (StringUtils.isEmpty(actualSound)) {
      return actualSound;
    }
    String sound = Settings.instance().getString(key(_CUSTOM_SOUND_KEY + chatId), null);
    String soundName = Settings.instance().getString(key(_CUSTOM_SOUND_NAME_KEY + chatId), null);
    return getChannelSoundName(sound, soundName, actualSound);
  }

  public String getCustomSoundPath (long chatId) {
    String actualSound = getCustomSound(chatId, null);
    if (StringUtils.isEmpty(actualSound)) {
      return actualSound;
    }
    return Settings.instance().getString(key(_CUSTOM_SOUND_PATH_KEY + chatId), null);
  }

  public void setCustomSound (long chatId, @Nullable String customSound, @Nullable String customSoundName, @Nullable String customSoundPath) {
    customSound = fixSoundUri(customSound);
    String oldSound = getCustomSound(chatId, null);
    LevelDB editor = Settings.instance().edit();
    if (customSound == null) {
      editor.remove(key(_CUSTOM_SOUND_KEY + chatId));
    } else {
      editor.putString(key(_CUSTOM_SOUND_KEY + chatId), customSound);
    }
    if (StringUtils.isEmpty(customSoundName)) {
      editor.remove(key(_CUSTOM_SOUND_NAME_KEY + chatId));
    } else {
      editor.putString(key(_CUSTOM_SOUND_NAME_KEY + chatId), customSoundName);
    }
    if (StringUtils.isEmpty(customSoundPath)) {
      editor.remove(key(_CUSTOM_SOUND_PATH_KEY + chatId));
    } else {
      editor.putString(key(_CUSTOM_SOUND_PATH_KEY + chatId), customSoundPath);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !compareSounds(customSound, oldSound)) {
      incrementChannelVersion(null, chatId, editor);
    } else {
      editor.apply();
    }
  }

  public @Nullable String getEffectiveSound (long chatId) {
    String sound = getCustomSound(chatId, null);
    if (sound == null) {
      LocalScopeNotificationSettings settings = getLocalNotificationSettings(chatId);
      sound = settings.getSound();
    }
    return sound;
  }

  // Private

  public void setCustomCallVibrate (long chatId, int mode, boolean vibrateOnlyIfSilent) {
    if (mode == VIBRATE_MODE_DEFAULT) {
      Settings.instance().edit().remove(key(_CUSTOM_CALL_VIBRATE_KEY + chatId)).remove(key(_CUSTOM_CALL_VIBRATE_ONLYSILENT_KEY + chatId)).apply();
    } else if (Config.VIBRATE_ONLY_IF_SILENT_AVAILABLE) {
      Settings.instance().edit().putInt(key(_CUSTOM_CALL_VIBRATE_KEY + chatId), mode).putBoolean(key(_CUSTOM_CALL_VIBRATE_ONLYSILENT_KEY + chatId), vibrateOnlyIfSilent).apply();
    } else {
      Settings.instance().putInt(key(_CUSTOM_CALL_VIBRATE_KEY + chatId), mode);
    }
  }

  public int getCustomCallVibrateModeForChat (long chatId) {
    return Settings.instance().getInt(key(_CUSTOM_CALL_VIBRATE_KEY + chatId), VIBRATE_MODE_DEFAULT);
  }

  public boolean getCustomCallVibrateOnlyIfSilentForChat (long chatId) {
    return Config.VIBRATE_ONLY_IF_SILENT_AVAILABLE && Settings.instance().getBoolean(key(_CUSTOM_CALL_VIBRATE_ONLYSILENT_KEY + chatId), false);
  }

  // Channel utils

  private TdlibNotificationChannelGroup channelGroupCache;

  public void createChannels () throws TdlibNotificationChannelGroup.ChannelCreationFailureException {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      long accountUserId = tdlib.myUserId(true);
      if (accountUserId != 0) {
        getChannelCache().create(tdlib.myUser());
      }
    }
  }

  public TdlibNotificationChannelGroup getChannelCache () throws TdlibNotificationChannelGroup.ChannelCreationFailureException {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      long accountUserId = tdlib.myUserId(true);
      TdApi.User account = myUser();
      if (accountUserId == 0) {
        if (channelGroupCache != null)
          return channelGroupCache;
        throw new IllegalStateException("Cannot retrieve accountUserId, required by channelGroup, authorizationStatus: " + tdlib.authorizationStatus());
      }
      if (channelGroupCache == null || channelGroupCache.getAccountUserId() != accountUserId) {
        channelGroupCache = new TdlibNotificationChannelGroup(tdlib, accountUserId, tdlib.account().isDebug(), account);
      }
      return channelGroupCache;
    }
    return null;
  }

  public Object getSystemChannel (TdlibNotificationGroup group) throws TdlibNotificationChannelGroup.ChannelCreationFailureException {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      return getChannelCache().getChannel(group, false);
    }
    return null;
  }

  public boolean resetChannelCache (long accountUserId) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && channelGroupCache != null && channelGroupCache.getAccountUserId() == accountUserId) {
      channelGroupCache = null;
      return true;
    }
    return false;
  }

  public int getChannelsGlobalVersion () {
    if (_channelGlobalVersion == null)
      _channelGlobalVersion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? Settings.instance().getInt(key(_CHANNEL_VERSION_GLOBAL_KEY, tdlib.id()), 0) : 0;
    return _channelGlobalVersion;
  }

  @TargetApi(Build.VERSION_CODES.O)
  public long getChannelVersion (TdApi.NotificationSettingsScope scope, long customChatId) {
    if (customChatId != 0) {
      return Settings.instance().getLong(key(_CHANNEL_VERSION_CUSTOM_KEY + customChatId), 0);
    } else {
      return getLocalNotificationSettings(scope).getChannelVersion();
    }
  }

  @TargetApi(Build.VERSION_CODES.O)
  private void incrementChannelVersion (@Nullable TdApi.NotificationSettingsScope scope, long chatId, LevelDB editor) {
    long selfUserId = tdlib.myUserId();
    LocalScopeNotificationSettings settings = chatId != 0 ? null : getLocalNotificationSettings(scope);
    String key = chatId != 0 ? key(_CHANNEL_VERSION_CUSTOM_KEY + chatId) : settings.prefix(KEY_PREFIX_CHANNEL_VERSION);
    long oldVersion = chatId != 0 ? Settings.instance().getLong(key, 0) : settings.getChannelVersion();
    long newVersion = oldVersion == Long.MAX_VALUE ? Long.MIN_VALUE : oldVersion + 1;
    editor.putLong(key, newVersion);
    if (chatId == 0) {
      settings.setChannelVersion(newVersion);
    }
    editor.apply();
    try {
      TdlibNotificationChannelGroup.updateChannelSettings(tdlib, selfUserId, tdlib.account().isDebug(), getChannelsGlobalVersion(), scope, chatId, newVersion);
    } catch (TdlibNotificationChannelGroup.ChannelCreationFailureException e) {
      TDLib.Tag.notifications("Unable to increment notification channel version for chat %d:\n%s", chatId, Log.toString(e));
      tdlib.settings().trackNotificationChannelProblem(e, chatId);
    }
    onUpdateNotificationChannels(selfUserId);
    if (chatId != 0) {
      tdlib.listeners().updateNotificationChannel(chatId);
    } else {
      tdlib.listeners().updateNotificationChannel(scope);
    }
  }

  @TargetApi(Build.VERSION_CODES.O)
  public String getSystemChannelId (TdApi.NotificationSettingsScope scope, long customChatId) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      long accountId = tdlib.myUserId();
      if (accountId != 0) {
        long version = getChannelVersion(scope, customChatId);
        return TdlibNotificationChannelGroup.makeChannelId(accountId, getChannelsGlobalVersion(), scope, customChatId, version);
      }
    }
    return null;
  }

  @TargetApi(Build.VERSION_CODES.O)
  @Nullable
  public Object getSystemChannelGroup () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      long selfUserId = tdlib.myUserId();
      if (selfUserId == 0)
        return null;
      NotificationManager m = (NotificationManager) UI.getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
      String groupId = TdlibNotificationChannelGroup.makeGroupId(selfUserId, tdlib.account().isDebug());
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return m.getNotificationChannelGroup(groupId);
      } else {
        List<android.app.NotificationChannelGroup> groups = m.getNotificationChannelGroups();
        for (android.app.NotificationChannelGroup group : groups) {
          if (groupId.equals(group.getId())) {
            return group;
          }
        }
      }
    }
    return null;
  }

  @TargetApi(Build.VERSION_CODES.O)
  public Object getSystemChannel (TdApi.NotificationSettingsScope scope, long customChatId) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager m = (NotificationManager) UI.getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
      if (m != null) {
        String channelId = getSystemChannelId(scope, customChatId);
        if (channelId != null) {
          return m.getNotificationChannel(channelId);
        }
      }
    }
    return null;
  }

  private String getChannelSoundName (@Nullable String sound, @Nullable String soundName, @Nullable String actualSound) {
    if (StringUtils.isEmpty(actualSound)) {
      return actualSound;
    } else if (compareSounds(actualSound, sound)) {
      return U.getRingtoneName(sound, soundName);
    } else {
      return U.getRingtoneName(actualSound, Lang.getString(R.string.RingtoneCustom));
    }
  }

  private @Nullable String getChannelSound (TdApi.NotificationSettingsScope scope, long customChatId, @Nullable String defaultSound) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      android.app.NotificationChannel channel = (android.app.NotificationChannel) getSystemChannel(scope, customChatId);
      if (channel != null) {
        Uri sound = channel.getSound();
        AudioAttributes audioAttributes = channel.getAudioAttributes();
        if (sound == null) {
          return audioAttributes == null ? "" : null;
        } else if (sound.equals(Uri.EMPTY)) {
          return "";
        } else {
          String soundString = sound.toString();
          try {
            Uri defaultRingtoneUri;
            defaultRingtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (defaultRingtoneUri != null && StringUtils.equalsOrBothEmpty(defaultRingtoneUri.toString(), soundString)) {
              return null;
            }
            defaultRingtoneUri = RingtoneManager.getActualDefaultRingtoneUri(UI.getAppContext(), RingtoneManager.TYPE_NOTIFICATION);
            if (defaultRingtoneUri != null && StringUtils.equalsOrBothEmpty(defaultRingtoneUri.toString(), soundString)) {
              return null;
            }
          } catch (Throwable ignored) { }

          return sound.toString();
        }
      }
    }
    return defaultSound;
  }

  private int getChannelLedColor (TdApi.NotificationSettingsScope scope, long customChatId, int defaultLedColor) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      android.app.NotificationChannel channel = (android.app.NotificationChannel) getSystemChannel(scope, customChatId);
      if (channel != null) {
        return channel.shouldShowLights() ? channel.getLightColor() : 0;
      }
    }
    return defaultLedColor;
  }

  private int getChannelPriorityOrImportance (TdApi.NotificationSettingsScope scope, long customChatId, int defaultPriorityOrImportance) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      android.app.NotificationChannel channel = (android.app.NotificationChannel) getSystemChannel(scope, customChatId);
      if (channel != null) {
        return channel.getImportance();
      }
    }
    return defaultPriorityOrImportance;
  }

  private int getChannelVibrateMode (TdApi.NotificationSettingsScope scope, long customChatId, int defaultVibrateMode) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      android.app.NotificationChannel channel = (android.app.NotificationChannel) getSystemChannel(scope, customChatId);
      if (channel != null) {
        if (!channel.shouldVibrate()) {
          return VIBRATE_MODE_DISABLED;
        }
        long[] vibrationPattern = channel.getVibrationPattern();
        if (vibrationPattern != null) {
          if (Arrays.equals(vibrationPattern, VIBRATE_SHORT_PATTERN)) {
            return VIBRATE_MODE_SHORT;
          } else if (Arrays.equals(vibrationPattern, VIBRATE_LONG_PATTERN)) {
            return VIBRATE_MODE_LONG;
          }
        }
        return VIBRATE_MODE_DEFAULT;
      }
    }
    return defaultVibrateMode;
  }

  // Utils

  private static boolean needUpdateDefaults (TdApi.ScopeNotificationSettings settings) {
    return settings == null || (!settings.showPreview || settings.muteFor != 0 || settings.disableMentionNotifications || settings.disablePinnedMessageNotifications);
  }

  private static void resetToDefault (TdApi.ScopeNotificationSettings settings) {
    settings.showPreview = true;
    settings.muteFor = 0;
    settings.disablePinnedMessageNotifications = false;
    settings.disableMentionNotifications = false;
  }

  private static TdApi.ScopeNotificationSettings newDefaults () {
    return new TdApi.ScopeNotificationSettings(
      0, 0, true,
      true, false, 0, true,
      false, false
    );
  }

  // Ringtone

  public @Nullable String getCustomCallRingtone (long chatId) {
    return Settings.instance().getString(key(_CUSTOM_CALL_RINGTONE_KEY + chatId), null);
  }

  public @Nullable String getCustomCallRingtoneName (long chatId) {
    return Settings.instance().getString(key(_CUSTOM_CALL_RINGTONE_NAME_KEY + chatId), null);
  }

  public @Nullable String getCustomCallRingtonePath (long chatId) {
    return Settings.instance().getString(key(_CUSTOM_CALL_RINGTONE_PATH_KEY + chatId), null);
  }

  public void setCustomCallRingtone (long chatId, @Nullable String customRingtone, @Nullable String customRingtoneName, @Nullable String customRingtonePath) {
    if (customRingtone == null) {
      Settings.instance().edit()
        .remove(key(_CUSTOM_CALL_RINGTONE_KEY + chatId))
        .remove(key(_CUSTOM_CALL_RINGTONE_NAME_KEY + chatId))
        .remove(key(_CUSTOM_CALL_RINGTONE_PATH_KEY + chatId))
        .apply();
    } else {
      LevelDB.Editor editor = Settings.instance().edit();
      editor.putString(key(_CUSTOM_CALL_RINGTONE_KEY + chatId), customRingtone);
      if (StringUtils.isEmpty(customRingtoneName)) {
        editor.remove(key(_CUSTOM_CALL_RINGTONE_NAME_KEY + chatId));
      } else {
        editor.putString(key(_CUSTOM_CALL_RINGTONE_NAME_KEY + chatId), customRingtoneName);
      }
      if (StringUtils.isEmpty(customRingtonePath)) {
        editor.remove(key(_CUSTOM_CALL_RINGTONE_PATH_KEY + chatId));
      } else {
        editor.putString(key(_CUSTOM_CALL_RINGTONE_PATH_KEY + chatId), customRingtonePath);
      }
      editor.apply();
    }
  }

  public String getCallRingtone (long chatId) {
    String sound = getCustomCallRingtone(chatId);
    if (sound == null) {
      sound = getCallRingtone();
    }
    if (StringUtils.isEmpty(sound)) {
      sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE).toString();
    }
    return sound;
  }

  public boolean shouldVibrateBecauseOfMute () {
    if (audioManager() != null) {
      try {
        return audioManager().getRingerMode() == AudioManager.RINGER_MODE_VIBRATE;
      } catch (Throwable t) {
        Log.w(t);
      }
    }
    return true;
  }

  public int getCallVibrateMode (long chatId) {
    int vibrateMode = getCustomCallVibrateModeForChat(chatId);
    boolean silentOnly;

    if (vibrateMode != VIBRATE_MODE_DEFAULT) {
      silentOnly = getCustomCallVibrateOnlyIfSilentForChat(chatId);
    } else {
      vibrateMode = getCallVibrateMode();
      silentOnly = getCallVibrateOnlyIfSilent();
    }

    if (silentOnly && audioManager() != null) {
      try {
        int mode = audioManager().getRingerMode();
        if (mode != AudioManager.RINGER_MODE_SILENT && mode != AudioManager.RINGER_MODE_VIBRATE) {
          vibrateMode = VIBRATE_MODE_DISABLED;
        }
      } catch (Throwable t) {
        Log.e(Log.TAG_FCM, "Cannot get ringer mode", t);
      }
    }

    return vibrateMode;
  }

  public @Nullable String getCallRingtone () {
    if (!callRingtoneLoaded) {
      _callRingtone = Settings.instance().getString(key(_CALL_RINGTONE_KEY, tdlib.id()), null);
      callRingtoneLoaded = true;
    }
    return _callRingtone;
  }

  public @Nullable String getCallRingtoneName () {
    if (!callRingtoneNameLoaded) {
      _callRingtoneName = Settings.instance().getString(key(_CALL_RINGTONE_NAME_KEY, tdlib.id()), null);
      callRingtoneNameLoaded = true;
    }
    return _callRingtoneName;
  }

  public @Nullable String getCallRingtonePath () {
    if (!callRingtonePathLoaded) {
      _callRingtonePath = Settings.instance().getString(key(_CALL_RINGTONE_PATH_KEY, tdlib.id()), null);
      callRingtonePathLoaded = true;
    }
    return _callRingtonePath;
  }

  public boolean setCallRingtone (@Nullable String voiceRingtone, @Nullable String voiceRingtoneName, @Nullable String voiceRingtonePath) {
    if (!StringUtils.equalsOrBothEmpty(voiceRingtone, getCallRingtone()) ||
        !StringUtils.equalsOrBothEmpty(voiceRingtoneName, getCallRingtoneName()) ||
        !StringUtils.equalsOrBothEmpty(voiceRingtonePath, getCallRingtonePath())) {
      this._callRingtone = voiceRingtone; this.callRingtoneLoaded = true;
      this._callRingtoneName = voiceRingtoneName; this.callRingtoneNameLoaded = true;
      this._callRingtonePath = voiceRingtonePath; this.callRingtonePathLoaded = true;
      SharedPreferences.Editor editor = Settings.instance().edit();
      if (voiceRingtone == null) {
        editor.remove(key(_CALL_RINGTONE_KEY));
        editor.remove(key(_CALL_RINGTONE_NAME_KEY));
        editor.remove(key(_CALL_RINGTONE_PATH_KEY));
      } else {
        editor.putString(key(_CALL_RINGTONE_KEY), voiceRingtone);
        if (StringUtils.isEmpty(voiceRingtoneName)) {
          editor.remove(key(_CALL_RINGTONE_NAME_KEY));
        } else {
          editor.putString(key(_CALL_RINGTONE_NAME_KEY), voiceRingtoneName);
        }
        if (StringUtils.isEmpty(voiceRingtonePath)) {
          editor.remove(key(_CALL_RINGTONE_PATH_KEY));
        } else {
          editor.putString(key(_CALL_RINGTONE_PATH_KEY), voiceRingtonePath);
        }
      }
      editor.apply();
      return true;
    }
    return false;
  }

  public int getCallVibrateMode () {
    if (_callVibrate == null)
      _callVibrate = Settings.instance().getInt(key(_CALL_VIBRATE_KEY, tdlib.id()), VIBRATE_MODE_DEFAULT);
    return _callVibrate;
  }

  public boolean getCallVibrateOnlyIfSilent () {
    if (_callVibrateOnlyIfSilent == null)
      _callVibrateOnlyIfSilent = Config.VIBRATE_ONLY_IF_SILENT_AVAILABLE && Settings.instance().getBoolean(key(_CALL_VIBRATE_ONLYSILENT_KEY, tdlib.id()), false);
    return _callVibrateOnlyIfSilent;
  }

  public boolean setCallVibrate (int vibrateMode, boolean onlySilent) {
    if (getCallVibrateMode() != vibrateMode || getCallVibrateOnlyIfSilent() != onlySilent) {
      this._callVibrate = vibrateMode;
      this._callVibrateOnlyIfSilent = onlySilent;
      Settings.instance().edit().putInt(key(_CALL_VIBRATE_KEY), vibrateMode).putBoolean(key(_CALL_VIBRATE_ONLYSILENT_KEY), onlySilent).apply();
      return true;
    }
    return false;
  }

  // Reset

  public void resetNotificationSettings (boolean onlyLocal) {
    final int accountId = tdlib.id();
    LevelDB editor = Settings.instance().edit();
    editor
      .remove(key(_INAPP_VIBRATE_KEY, accountId))
      .remove(key(_INAPP_SOUNDS_KEY, accountId))
      .remove(key(_INAPP_CHATSOUNDS_KEY, accountId))

      .remove(key(_CALL_RINGTONE_KEY, accountId))
      .remove(key(_CALL_RINGTONE_NAME_KEY, accountId))
      .remove(key(_CALL_RINGTONE_PATH_KEY, accountId))
      .remove(key(_CALL_VIBRATE_KEY, accountId))
      .remove(key(_CALL_VIBRATE_ONLYSILENT_KEY, accountId));

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      int channelGlobalVersion = getChannelsGlobalVersion();
      editor.putInt(key(_CHANNEL_VERSION_GLOBAL_KEY, accountId), _channelGlobalVersion = (channelGlobalVersion == Integer.MAX_VALUE ? Integer.MIN_VALUE : ++channelGlobalVersion));
    }

    String customSoundKey = key(_CUSTOM_SOUND_KEY, accountId);
    String customSoundNameKey = key(_CUSTOM_SOUND_NAME_KEY, accountId);
    String customSoundPathKey = key(_CUSTOM_SOUND_PATH_KEY, accountId);
    String customLedKey = key(_CUSTOM_LED_KEY, accountId);
    String customVibrateKey = key(_CUSTOM_VIBRATE_KEY, accountId);
    String customVibrateOnlySilentKey = key(_CUSTOM_VIBRATE_ONLYSILENT_KEY, accountId);
    String customCallRingtoneKey = key(_CUSTOM_CALL_RINGTONE_KEY, accountId);
    String customCallRingtoneNameKey = key(_CUSTOM_CALL_RINGTONE_NAME_KEY, accountId);
    String customCallVibrateKey = key(_CUSTOM_CALL_VIBRATE_KEY, accountId);
    String customCallVibrateOnlySilentKey = key(_CUSTOM_CALL_VIBRATE_ONLYSILENT_KEY, accountId);
    String customCallPriorityKey = key(_CUSTOM_PRIORITY_OR_IMPORTANCE_KEY, accountId);
    String channelVersionCustomKey = key(_CHANNEL_VERSION_CUSTOM_KEY, accountId);
    Settings.instance().removeByAnyPrefix(new String[] {
      customSoundKey, customSoundNameKey, customSoundPathKey, customLedKey, customVibrateKey, customVibrateOnlySilentKey,
      customCallRingtoneKey, customCallRingtoneNameKey, customCallVibrateKey, customCallVibrateOnlySilentKey, customCallPriorityKey,
      channelVersionCustomKey
    }, editor);

    editor.apply();


    _inAppChatSounds = null;

    privateSettings.resetToDefault(editor);
    groupSettings.resetToDefault(editor);
    channelSettings.resetToDefault(editor);

    _callVibrate = null;
    _callVibrateOnlyIfSilent = null;
    _callRingtoneName = null; _callRingtone = null;
    callRingtoneLoaded = false; callRingtoneNameLoaded = false;

    _repeatNotificationMinutes = null;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      long selfUserId = tdlib.myUserId();
      TdApi.User account = tdlib.myUser();
      if (selfUserId != 0) {
        TdlibNotificationChannelGroup.deleteChannels(tdlib, selfUserId, tdlib.account().isDebug(), account, !onlyLocal);
      }
    }

    if (!onlyLocal) {
      if (needUpdateDefaults(settingsForPrivateChats)) {
        if (settingsForPrivateChats != null) {
          resetToDefault(settingsForPrivateChats);
        } else {
          settingsForPrivateChats = newDefaults();
        }
        tdlib.client().send(new TdApi.SetScopeNotificationSettings(new TdApi.NotificationSettingsScopePrivateChats(), settingsForPrivateChats), tdlib.okHandler());
      }
      if (needUpdateDefaults(settingsForGroupChats)) {
        if (settingsForGroupChats != null) {
          resetToDefault(settingsForGroupChats);
        } else {
          settingsForGroupChats = newDefaults();
        }
        tdlib.client().send(new TdApi.SetScopeNotificationSettings(new TdApi.NotificationSettingsScopeGroupChats(), settingsForGroupChats), tdlib.okHandler());
      }
      if (needUpdateDefaults(settingsForChannelChats)) {
        if (settingsForChannelChats != null) {
          resetToDefault(settingsForChannelChats);
        } else {
          settingsForChannelChats = newDefaults();
        }
        tdlib.client().send(new TdApi.SetScopeNotificationSettings(new TdApi.NotificationSettingsScopeChannelChats(), settingsForChannelChats), tdlib.okHandler());
      }

      boolean updated;
      updated = Settings.instance().setNeedSplitNotificationCategories(true);
      updated = Settings.instance().setNeedHideSecretChats(false) || updated;
      if (Settings.instance().resetBadge()) {
        tdlib.context().resetBadge();
      }
      if (updated) {
        tdlib.context().onUpdateAllNotifications();
      }
    }

    rebuildNotification();
  }

  // Show Preview

  public boolean defaultShowPreview (TdApi.NotificationSettingsScope scope) {
    TdApi.ScopeNotificationSettings settings = getScopeNotificationSettings(scope);
    return settings != null && settings.showPreview;
  }

  public boolean defaultShowPreview (long chatId) {
    return !ChatId.isSecret(chatId) && defaultShowPreview(scope(chatId));
  }

  public void setDefaultShowPreview (TdApi.NotificationSettingsScope scope, boolean showPreview) {
    TdApi.ScopeNotificationSettings settings = getScopeNotificationSettings(scope);
    if (settings != null) {
      settings.showPreview = showPreview;
      tdlib.setScopeNotificationSettings(scope, settings);
    }
  }

  public void toggleDefaultShowPreview (TdApi.NotificationSettingsScope scope) {
    setDefaultShowPreview(scope, !defaultShowPreview(scope));
  }

  public boolean isShowPreviewEnabled (long chatId, boolean isMention) {
    TdApi.ChatNotificationSettings settings = tdlib.chatSettings(chatId);
    if (settings == null || settings.useDefaultShowPreview) {
      if (ChatId.isSecret(chatId)) {
        return false;
      } else if (isMention) {
        return settingsForPrivateChats != null && settingsForPrivateChats.showPreview;
      } else {
        return defaultShowPreview(chatId);
      }
    } else {
      return settings.showPreview;
    }
  }

  public void setShowPreviewEnabled (long chatId, boolean showPreview) {
    TdApi.ChatNotificationSettings settings = tdlib.chatSettings(chatId);
    boolean defaultShowPreview = defaultShowPreview(chatId);
    if (settings != null) {
      settings.useDefaultShowPreview = (showPreview == defaultShowPreview);
      settings.showPreview = showPreview;
      tdlib.setChatNotificationSettings(chatId, settings);
    }
  }

  public void toggleShowPreview (long chatId) {
    setShowPreviewEnabled(chatId, !isShowPreviewEnabled(chatId, false));
  }

  // Show Preview (Content)

  public void toggleContentPreview (TdApi.NotificationSettingsScope scope) {
    LocalScopeNotificationSettings settings = getLocalNotificationSettings(scope);
    boolean needContentPreview = !settings.needContentPreview();
    settings.setNeedContentPreview(needContentPreview);
    Settings.instance().putBoolean(settings.suffix(KEY_SUFFIX_CONTENT_PREVIEW), needContentPreview);
  }

  public boolean isContentPreviewEnabled (TdApi.NotificationSettingsScope scope) {
    return Config.NEED_NOTIFICATION_CONTENT_PREVIEW && getLocalNotificationSettings(scope).needContentPreview();
  }

  public boolean needContentPreview (long chatId, boolean isMention) {
    if (!Config.NEED_NOTIFICATION_CONTENT_PREVIEW || !isShowPreviewEnabled(chatId, isMention)) {
      return false;
    }
    if (ChatId.isUserChat(chatId) || isMention) {
      return privateSettings.needContentPreview() && settingsForPrivateChats != null && settingsForPrivateChats.showPreview;
    } else if (tdlib.isChannel(chatId)) {
      return channelSettings.needContentPreview() && settingsForChannelChats != null && settingsForChannelChats.showPreview;
    } else {
      return groupSettings.needContentPreview() && settingsForGroupChats != null && settingsForGroupChats.showPreview;
    }
  }

  // Pinned Messages

  public boolean defaultDisablePinnedMessages (TdApi.NotificationSettingsScope scope) {
    TdApi.ScopeNotificationSettings settings = getScopeNotificationSettings(scope);
    return settings != null && settings.disablePinnedMessageNotifications;
  }

  public boolean defaultDisablePinnedMessages (long chatId) {
    TdApi.ScopeNotificationSettings settings = getScopeNotificationSettings(chatId);
    return settings != null && settings.disablePinnedMessageNotifications;
  }

  public void setDefaultDisablePinnedMessages (TdApi.NotificationSettingsScope scope, boolean disabled) {
    TdApi.ScopeNotificationSettings settings = getScopeNotificationSettings(scope);
    if (settings != null) {
      settings.disablePinnedMessageNotifications = disabled;
      tdlib.setScopeNotificationSettings(scope, settings);
    }
  }

  public boolean arePinnedMessagesDisabled (long chatId) {
    TdApi.ChatNotificationSettings settings = tdlib.chatSettings(chatId);
    if (settings == null || settings.useDefaultDisablePinnedMessageNotifications) {
      return defaultDisablePinnedMessages(chatId);
    } else {
      return settings.disablePinnedMessageNotifications;
    }
  }

  public void setDisablePinnedMessages (long chatId, boolean disable) {
    TdApi.ChatNotificationSettings settings = tdlib.chatSettings(chatId);
    boolean defaultDisablePinnedMessages = defaultDisablePinnedMessages(chatId);
    if (settings != null) {
      settings.useDefaultDisablePinnedMessageNotifications = (disable == defaultDisablePinnedMessages);
      settings.disablePinnedMessageNotifications = disable;
      tdlib.setChatNotificationSettings(chatId, settings);
    }
  }

  // Mentions

  public boolean defaultDisableMentions (TdApi.NotificationSettingsScope scope) {
    TdApi.ScopeNotificationSettings settings = getScopeNotificationSettings(scope);
    return settings != null && settings.disableMentionNotifications;
  }

  public boolean defaultDisableMentions (long chatId) {
    TdApi.ScopeNotificationSettings settings = getScopeNotificationSettings(chatId);
    return settings != null && settings.disableMentionNotifications;
  }

  public void setDefaultDisableMentions (TdApi.NotificationSettingsScope scope, boolean disabled) {
    TdApi.ScopeNotificationSettings settings = getScopeNotificationSettings(scope);
    if (settings != null) {
      settings.disableMentionNotifications = disabled;
      tdlib.setScopeNotificationSettings(scope, settings);
    }
  }

  public boolean areMentionsDisabled (long chatId) {
    TdApi.ChatNotificationSettings settings = tdlib.chatSettings(chatId);
    if (settings == null || settings.useDefaultDisableMentionNotifications) {
      return defaultDisableMentions(chatId);
    } else {
      return settings.disableMentionNotifications;
    }
  }

  public void setMentionsDisabled (long chatId, boolean disable) {
    TdApi.ChatNotificationSettings settings = tdlib.chatSettings(chatId);
    boolean defaultDisablePinnedMessages = defaultDisableMentions(chatId);
    if (settings != null) {
      settings.useDefaultDisableMentionNotifications = (disable == defaultDisablePinnedMessages);
      settings.disableMentionNotifications = disable;
      tdlib.setChatNotificationSettings(chatId, settings);
    }
  }

  // Repeat notifications

  public int getRepeatNotificationMinutes () {
    if (_repeatNotificationMinutes == null)
      _repeatNotificationMinutes = Settings.instance().getInt(key(_REPEAT_NOTIFICATION_MINUTES_KEY, tdlib.id()), DEFAULT_REPEAT_NOTIFICATIONS_TIME);
    return _repeatNotificationMinutes;
  }

  public boolean setRepeatNotificationMinuted (int minutes) {
    if (getRepeatNotificationMinutes() != minutes) {
      this._repeatNotificationMinutes = minutes;
      Settings.instance().putInt(key(_REPEAT_NOTIFICATION_MINUTES_KEY), minutes);
      if (minutes == 0) {
        cancelPendingNotificationRepeat();
      } else {
        scheduleNotificationRepeat();
      }
      return true;
    }
    return false;
  }

  // Update handlers

  public TdApi.ScopeNotificationSettings getScopeNotificationSettings (long chatId) {
    return getScopeNotificationSettings(scope(chatId));
  }

  public TdApi.ScopeNotificationSettings getScopeNotificationSettings (TdApi.Chat chat) {
    return getScopeNotificationSettings(scope(chat));
  }

  public TdApi.ScopeNotificationSettings getScopeNotificationSettings (TdApi.NotificationSettingsScope scope) {
    switch (scope.getConstructor()) {
      case TdApi.NotificationSettingsScopePrivateChats.CONSTRUCTOR:
        return settingsForPrivateChats;
      case TdApi.NotificationSettingsScopeGroupChats.CONSTRUCTOR:
        return settingsForGroupChats;
      case TdApi.NotificationSettingsScopeChannelChats.CONSTRUCTOR:
        return settingsForChannelChats;
    }
    throw new RuntimeException();
  }

  public @NonNull TdApi.NotificationSettingsScope scope (long chatId) {
    switch (ChatId.getType(chatId)) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR:
        return scopePrivate();
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
        return scopeGroup();
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR:
        return tdlib.isSupergroup(chatId) ? scopeGroup() : scopeChannel();
    }
    throw new RuntimeException();
  }

  public @NonNull TdApi.NotificationSettingsScope scope (TdApi.Chat chat) {
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR:
        return scopePrivate();
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
        return scopeGroup();
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR:
        return TD.isSupergroup(chat.type) ? scopeGroup() : scopeChannel();
    }
    throw new RuntimeException();
  }

  public @NonNull TdApi.NotificationSettingsScope scopePrivate () {
    return privateSettings.scope;
  }

  public @NonNull TdApi.NotificationSettingsScope scopeGroup () {
    return groupSettings.scope;
  }

  public @NonNull TdApi.NotificationSettingsScope scopeChannel () {
    return channelSettings.scope;
  }

  public LocalScopeNotificationSettings getLocalNotificationSettings (long chatId) {
    switch (ChatId.getType(chatId)) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR:
        return privateSettings;
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
        return groupSettings;
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR:
        return tdlib.isSupergroup(chatId) ? groupSettings : channelSettings;
    }
    throw new RuntimeException("chatId == " + chatId);
  }

  public LocalScopeNotificationSettings getLocalNotificationSettings (TdApi.NotificationSettingsScope scope) {
    switch (scope.getConstructor()) {
      case TdApi.NotificationSettingsScopePrivateChats.CONSTRUCTOR:
        return privateSettings;
      case TdApi.NotificationSettingsScopeGroupChats.CONSTRUCTOR:
        return groupSettings;
      case TdApi.NotificationSettingsScopeChannelChats.CONSTRUCTOR:
        return channelSettings;
    }
    throw new RuntimeException();
  }

  // Schedule

  private void scheduleNotificationRepeat () {
    // TODO
  }

  private void cancelPendingNotificationRepeat () {
    // TODO
  }

  // In-App Sounds

  private void playOutgoingSound () {
    if (areInAppChatSoundsEnabled()) {
      playSound(R.raw.sound_out, 100);
    }
  }

  private void playIncomingSound () {
    if (areInAppChatSoundsEnabled()) {
      playSound(R.raw.sound_in, 500);
    }
  }

  private long nextCanPlaySoundTime;
  private SoundPool soundPool;
  private static final int MAX_STREAM_COUNT = 3;
  private final SparseIntArray loadedSounds;
  private final SparseIntArray sounds;

  private void playSound (@RawRes int soundResource, int delayAfter) {
    if (audioManager() == null || Recorder.instance().isRecording()) {
      return;
    }
    if (Thread.currentThread() != queue) {
      queue.sendMessage(Message.obtain(queue.getHandler(), PLAY_SOUND, soundResource, delayAfter, this), 0);
      return;
    }
    long ms = System.currentTimeMillis();

    if (nextCanPlaySoundTime != 0 && nextCanPlaySoundTime > ms) {
      return;
    }

    try {
      if (soundPool == null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          android.media.AudioAttributes attributes = new android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
          soundPool = new SoundPool.Builder().setMaxStreams(MAX_STREAM_COUNT).setAudioAttributes(attributes).build();
        } else {
          //noinspection deprecation
          soundPool = new SoundPool(3, AudioManager.STREAM_SYSTEM, 0);
        }
        soundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
          if (status == 0) {
            soundPool.play(sampleId, 1f, 1f, 1, 0, 1f);
          }
        });
      }
      int soundID = sounds.get(soundResource);
      if (soundID == 0 && loadedSounds.get(soundResource) != 1) {
        loadedSounds.put(soundResource, 1);
        sounds.put(soundResource, soundID = soundPool.load(UI.getAppContext(), soundResource, 1));
      }
      if (soundID != 0) {
        soundPool.play(soundID, 1f, 1f, 1, 0, 1f);
        nextCanPlaySoundTime = ms + delayAfter;
      } else {
        nextCanPlaySoundTime = ms + delayAfter + 30; // Giving 30ms for loading
      }
    } catch (Throwable t) {
      Log.e(Log.TAG_FCM, "Unable to play raw sound", t);
    }
  }

  public boolean needVibrateWhenRinging () {
    if (shouldVibrateBecauseOfMute()) {
      return true;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return android.provider.Settings.System.getInt(UI.getContext().getContentResolver(),
        android.provider.Settings.System.VIBRATE_WHEN_RINGING, 0) != 0;
    } else {
      return true;
    }
  }

  // ===== NEW NOTIFICATIONS =====

  // External events

  @Override
  public void onPerformStartup (boolean isAfterRestart) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      sendLockedMessage(Message.obtain(queue.getHandler(), ENSURE_CHANNELS, this), null);
    }
  }

  @Override
  public void onPerformUserCleanup () {
    resetNotificationSettings(true);
  }

  @Override
  public void onPerformRestart () {
    queue.sendMessage(Message.obtain(queue.getHandler(), ON_RESTART, this), 0);
  }

  @Override
  public void onPasscodeLocked (boolean isLocked) {
    rebuildNotification();
  }

  @Override
  public void onUiStateChanged (int newState) {
    if (Passcode.instance().isEnabled() && Passcode.instance().isLocked()) {
      rebuildNotification();
    }
  }

  private void sendLockedMessage (Message message, @Nullable Runnable after) {
    tdlib.incrementNotificationReferenceCount();
    queue.sendMessage(message, 0);
    releaseTdlibReference(after);
  }

  @AnyThread
  @TargetApi(Build.VERSION_CODES.TIRAMISU)
  public void onNotificationPermissionGranted () {
    rebuildNotification();
  }

  @AnyThread
  public void onUpdateNotificationChannels (long accountUserId) {
    if (Thread.currentThread() != queue) {
      sendLockedMessage(Message.obtain(queue.getHandler(), ON_UPDATE_NOTIFICATION_CHANNELS, BitwiseUtils.splitLongToFirstInt(accountUserId), BitwiseUtils.splitLongToSecondInt(accountUserId), this), null);
    } else {
      resetNotificationGroupImpl(accountUserId);
    }
  }

  // Private

  private static final long NOTIFICATION_LIMIT = 800l;
  private long lastNotificationTimeMs, lastNotificationActualTimeMs;

  private static boolean areNotificationSoundsForbidden () {
    // TODO check for pending call?
    return Recorder.instance().isRecording();
  }

  /**
   * @return true, when notification should show with sound
   */
  public boolean allowNotificationSound (long chatId) {
    if (areNotificationSoundsForbidden())
      return false;
    long now = System.currentTimeMillis();
    if (now - lastNotificationTimeMs >= NOTIFICATION_LIMIT || now - lastNotificationActualTimeMs >= 5000) {
      lastNotificationTimeMs = lastNotificationActualTimeMs = now;
      return true;
    } else {
      lastNotificationTimeMs = now;
      return false;
    }
  }

  /**
   * Updates notification because of some external event:
   * - Passcode Lock / Unlock
   * - Channel version change
   * */
  @AnyThread
  private void rebuildNotification () {
    if (Thread.currentThread() != queue) {
      sendLockedMessage(Message.obtain(queue.getHandler(), REBUILD_NOTIFICATION, this), null);
    } else {
      rebuildNotificationImpl();
    }
  }

  // TDLib updates

  @TdlibThread
  void onUpdateNewMessage (TdApi.UpdateNewMessage update) {
    if (update.message.isOutgoing || update.message.sendingState != null)
      return;
    ViewController<?> c = null;
    try {
      c = UI.getCurrentStackItem();
    } catch (IndexOutOfBoundsException ignored) { }
    if (c instanceof MessagesController && c.isSameTdlib(tdlib)) {
      long activeChatId = ((MessagesController) c).getActiveChatId();
      if (activeChatId != 0 && update.message.chatId == activeChatId && tdlib.chatNotificationsEnabled(activeChatId)) {
        TdApi.ChatMemberStatus status = tdlib.chatStatus(update.message.chatId);
        if (status == null || TD.isMember(status)) {
          playIncomingSound();
        }
      }
    }
  }

  @TdlibThread
  void onUpdateMessageSendSucceeded (TdApi.UpdateMessageSendSucceeded update) {
    TdApi.Message sentMessage = update.message;
    ViewController<?> c = null;
    try {
      c = UI.getCurrentStackItem();
    } catch (IndexOutOfBoundsException ignored) { }
    if (((c instanceof MessagesController && ((MessagesController) c).compareChat(sentMessage.chatId)) || (c instanceof MainController)) && !c.isPaused()) {
      switch (sentMessage.content.getConstructor()) {
        case TdApi.MessageScreenshotTaken.CONSTRUCTOR:
        case TdApi.MessageChatSetMessageAutoDeleteTime.CONSTRUCTOR: {
          break;
        }
        default: {
          playOutgoingSound();
          break;
        }
      }
    }
  }

  @TdlibThread
  void onUpdateNotificationSettings (TdApi.UpdateChatNotificationSettings update, long chatId, TdApi.ChatNotificationSettings oldNotificationSettings) {
    boolean defaultShowPreview = defaultShowPreview(chatId);
    boolean hadPreview = oldNotificationSettings.useDefaultShowPreview ? defaultShowPreview : oldNotificationSettings.showPreview;
    boolean hasPreview = update.notificationSettings.useDefaultShowPreview ? defaultShowPreview : update.notificationSettings.showPreview;
    if (hadPreview != hasPreview) {
      onUpdateNotifications(update.chatId);
    }
  }

  @TdlibThread
  void onUpdateNotificationSettings (TdApi.UpdateScopeNotificationSettings update) {
    TdApi.NotificationSettingsScope scope = update.scope;
    TdApi.ScopeNotificationSettings settings = update.notificationSettings;
    TdApi.ScopeNotificationSettings existingSettings = getScopeNotificationSettings(scope);
    boolean previewToggled = existingSettings != null && existingSettings.showPreview != settings.showPreview;
    switch (scope.getConstructor()) {
      case TdApi.NotificationSettingsScopePrivateChats.CONSTRUCTOR:
        settingsForPrivateChats = settings;
        break;
      case TdApi.NotificationSettingsScopeGroupChats.CONSTRUCTOR:
        settingsForGroupChats = settings;
        break;
      case TdApi.NotificationSettingsScopeChannelChats.CONSTRUCTOR:
        settingsForChannelChats = settings;
        break;
      default:
        throw new RuntimeException();
    }
    if (previewToggled) {
      onUpdateNotifications(scope);
    }
  }

  // TDLib Notifications API

  @TdlibThread
  void releaseTdlibReference (@Nullable Runnable after) {
    if (after != null) {
      queue.sendMessage(Message.obtain(queue.getHandler(), RELEASE_REFERENCE_WITH_TASK, new Object[] {this, after}), 0);
    } else {
      queue.sendMessage(Message.obtain(queue.getHandler(), RELEASE_REFERENCE, this), 0);
    }
  }

  @TdlibThread
  void onUpdateActiveNotifications (TdApi.UpdateActiveNotifications update, @Nullable Runnable after) {
    sendLockedMessage(Message.obtain(queue.getHandler(), ON_UPDATE_ACTIVE_NOTIFICATIONS, new Object[] {this, update}), after);
  }

  @TdlibThread
  void onUpdateNotificationGroup (TdApi.UpdateNotificationGroup update) {
    sendLockedMessage(Message.obtain(queue.getHandler(), ON_UPDATE_NOTIFICATION_GROUP, new Object[] {this, update}), null);
  }

  @TdlibThread
  void onUpdateNotification (TdApi.UpdateNotification update) {
    sendLockedMessage(Message.obtain(queue.getHandler(), ON_UPDATE_NOTIFICATION, new Object[] {this, update}), null);
  }

  @TdlibThread
  void onUpdateMyUserId (long myUserId) {
    sendLockedMessage(Message.obtain(queue.getHandler(), ON_UPDATE_MY_USER_ID, BitwiseUtils.splitLongToFirstInt(myUserId), BitwiseUtils.splitLongToSecondInt(myUserId), this), null);
  }

  @TdlibThread
  void onUpdateMyUser (@Nullable TdApi.User user) {
    sendLockedMessage(Message.obtain(queue.getHandler(), ON_UPDATE_MY_USER, new Object[] {this, user}), null);
  }

  @AnyThread
  public void onHideAll (int category) {
    queue.sendMessage(Message.obtain(queue.getHandler(), ON_HIDE_ALL_NOTIFICATIONS, category, 0, this), 0);
  }

  @AnyThread
  public void removeNotificationGroup (TdlibNotificationExtras extras) {
    queue.sendMessage(Message.obtain(queue.getHandler(), REMOVE_NOTIFICATIONS, new Object[] {this, extras}), 0);
  }

  @AnyThread
  public boolean isUnknownGroup (int groupId) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      return notification.isUnknownGroup(groupId);
    } else {
      return false;
    }
  }

  @AnyThread
  public void onHide (TdlibNotificationExtras extras) {
    sendLockedMessage(Message.obtain(queue.getHandler(), ON_HIDE_NOTIFICATION, new Object[] {this, extras}), null);
  }

  @AnyThread
  public void onChatOpened (long chatId) {
    if (ChatId.isSecret(chatId)) {
      sendLockedMessage(Message.obtain(queue.getHandler(), ON_CHAT_OPENED, BitwiseUtils.splitLongToFirstInt(chatId), BitwiseUtils.splitLongToSecondInt(chatId), this), null);
    }
  }

  @AnyThread
  public void onUpdateNotifications (TdApi.NotificationSettingsScope scope) {
    sendLockedMessage(Message.obtain(queue.getHandler(), ON_REBUILD_NOTIFICATIONS, new Object[] {this, scope}), null);
  }

  @AnyThread
  public void onUpdateNotifications (long chatId) {
    sendLockedMessage(Message.obtain(queue.getHandler(), ON_REBUILD_NOTIFICATIONS_SPECIFIC, BitwiseUtils.splitLongToFirstInt(chatId), BitwiseUtils.splitLongToSecondInt(chatId), this), null);
  }

  @AnyThread
  public void onDropNotificationData (boolean hideAll) {
    queue.sendMessage(Message.obtain(queue.getHandler(), ON_DROP_NOTIFICATION_DATA, hideAll ? 1 : 0, 0, this), 0);
  }

  // Impl

  /*private boolean havePendingNotifications;

  @NotificationThread
  private void processHavePendingNotifications (TdApi.UpdateHavePendingNotifications update) {
    if (this.havePendingNotifications != update.havePendingNotifications) {
      if (update.havePendingNotifications) {
        if (!tdlib.context().addWakeLockReference()) {
          return;
        }
      } else {
        if (!tdlib.context().removeWakeLockReference()) {
          return;
        }
      }
      this.havePendingNotifications = update.havePendingNotifications;
    }
  }*/

  private long myUserId;
  private @Nullable TdApi.User myUser;

  @NotificationThread
  public long myUserId () {
    return myUserId;
  }

  @NotificationThread
  public boolean isSelfUserId (long userId) {
    return userId != 0 && userId == myUserId;
  }

  @NotificationThread
  @Nullable
  public TdApi.User myUser () {
    return myUser;
  }

  @NotificationThread
  private void onUpdateMyUserIdImpl (long userId) {
    this.myUserId = userId;
    if (userId == 0)
      myUser = null;
  }

  @NotificationThread
  private void onUpdateMyUserImpl (TdApi.User user) {
    this.myUser = user;
  }

  @NotificationThread
  private void resetNotificationGroupImpl (long accountUserId) {
    notification.onNotificationChannelGroupReset(accountUserId);
  }

  @NotificationThread
  private void rebuildNotificationImpl () {
    notification.rebuild();
  }

  @NotificationThread
  private void rebuildNotificationGroupImpl (int groupId) {
    notification.rebuildGroup(groupId);
  }

  @NotificationThread
  private void rebuildNotificationsImpl (TdApi.NotificationSettingsScope scope) {
    notification.rebuild(scope);
  }

  @NotificationThread
  private void rebuildNotificationsImpl (long specificChatId) {
    notification.rebuildChat(specificChatId);
  }

  @NotificationThread
  private void onChatOpenedImpl (long chatId) {
    notification.onChatOpened(chatId);
  }

  @NotificationThread
  private void processActiveNotifications (TdApi.UpdateActiveNotifications update) {
    notification.restoreState(update);
  }

  @NotificationThread
  private void processNotificationGroup (TdApi.UpdateNotificationGroup update) {
    notification.updateGroup(update);
  }

  @NotificationThread
  private void processNotification (TdApi.UpdateNotification update) {
    notification.editNotification(update);
  }

  @NotificationThread
  private void onHideAllImpl (int category) {
    notification.onHideAll(category);
  }

  @NotificationThread
  private void onHideImpl (TdlibNotificationExtras extras) {
    notification.onHide(extras);
  }

  @NotificationThread
  private void onDropNotificationDataImpl (boolean hideAll) {
    notification.onDropNotificationData(hideAll);
  }

  @NotificationThread
  private void onRestartImpl () {
    notification.onTdlibRestart();
  }

  @NotificationThread
  private void removeNotificationGroupImpl (TdlibNotificationExtras extras) {
    notification.removeNotificationGroup(extras);
  }
}
