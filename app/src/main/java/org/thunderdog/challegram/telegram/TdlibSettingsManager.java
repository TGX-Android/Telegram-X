package org.thunderdog.challegram.telegram;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.collection.SparseArrayCompat;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ChatStyle;
import org.thunderdog.challegram.theme.TGBackground;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.LongSparseLongArray;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.core.unit.BitwiseUtils;
import me.vkryl.core.util.Blob;
import me.vkryl.leveldb.LevelDB;

/**
 * Date: 2/20/18
 * Author: default
 */

public class TdlibSettingsManager implements CleanupStartupDelegate {
  private final Tdlib tdlib;

  private static final String PLAIN_CHANNEL_KEY = "settings_channel_plain";
  private static final String CONFIG_KEY = "settings_app_config";
  private static final String PREFERENCES_KEY = "settings_user_preferences";

  private static final String THEME_CHAT_STYLE_KEY = "settings_chat_style";
  private static final String THEME_GLOBAL_THEME_KEY = "settings_global_theme";
  private static final String THEME_GLOBAL_THEME_DAYLIGHT_KEY = "settings_global_theme_daylight";
  private static final String THEME_GLOBAL_THEME_NIGHT_KEY = "settings_global_theme_night";

  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  public static final String __PEER_TO_PEER_KEY = "settings_peer_to_peer";

  private static final String DISMISS_MESSAGE_PREFIX = "dismiss_pinned_";
  private static final String DISMISS_REQUESTS_PREFIX = "dismiss_requests_";

  private static final String NOTIFICATION_GROUP_DATA_PREFIX = "notification_gdata_";
  private static final String NOTIFICATION_DATA_PREFIX = "notification_data_";
  public static final String CONVERSION_PREFIX = "pending_conversion_";

  public static final String DEVICE_TOKEN_KEY = "registered_device_token";
  public static final String DEVICE_UID_KEY = "registered_device_uid";
  public static final String DEVICE_OTHER_UID_KEY = "registered_device_uid_other";
  public static final String DEVICE_TDLIB_VERSION_KEY = "registered_device_tdlib";

  public static final String NOTIFICATION_ERROR_KEY = "notification_error";
  public static final String NOTIFICATION_VERSION_KEY = "notification_version";

  private static final String LOCAL_CHAT_ID_PREFIX = "local_chat_id_"; // remote -> local
  private static final String REMOTE_CHAT_ID_PREFIX = "remote_chat_id_"; // local -> remote
  private static final String LOCAL_CHAT_IDS_COUNT = "local_chat_ids";

  private @ThemeId Integer _globalTheme, _globalThemeDaylight, _globalThemeNight;

  @Nullable
  private @ChatStyle Integer _chatStyle;
  @Nullable
  private Boolean _forcePlainModeInChannels;

  @Nullable
  private Integer _notificationErrorCount;

  @Nullable
  private Long _userPreferences;

  private final SparseArrayCompat<TGBackground> wallpapers = new SparseArrayCompat<>();

  private final Object localChatIdsSync = new Object();
  private Long _localChatIdsCount;
  private final LongSparseLongArray remoteToLocalChatIds = new LongSparseLongArray();
  private final LongSparseLongArray localToRemoteChatIds = new LongSparseLongArray();

  public static String key (String key, int accountId) {
    return accountId != 0 ? accountId + "_" + key : key;
  }

  private static int readDefault (Settings prefs, String originalKey, int defaultValue, int accountId) {
    final String key = key(originalKey, accountId);
    if (accountId > 0) {
      return prefs.getInt(key, prefs.getInt(originalKey, defaultValue));
    } else {
      return prefs.getInt(key, defaultValue);
    }
  }

  TdlibSettingsManager (Tdlib tdlib) {
    this.tdlib = tdlib;

    load();

    tdlib.listeners().addCleanupListener(this);
  }

  private void load () {
    // FIXME
  }

  @Override
  public void onPerformStartup (boolean isAfterRestart) {
    if (isAfterRestart && tdlib.id() > 0) {
      load();
    }
  }

  @Override
  public void onPerformRestart () { }

  @Override
  public void onPerformUserCleanup () {
    Settings prefs = Settings.instance();

    SharedPreferences.Editor editor = prefs.edit();

    final int accountId = tdlib.id();

    Settings.instance().deleteWallpapers(tdlib, editor);
    wallpapers.clear();
    editor.remove(key(THEME_CHAT_STYLE_KEY, accountId));
    editor.remove(key(PLAIN_CHANNEL_KEY, accountId));
    editor.remove(key(PREFERENCES_KEY, accountId));
    editor.remove(key(THEME_GLOBAL_THEME_KEY, accountId));
    editor.remove(key(THEME_GLOBAL_THEME_NIGHT_KEY, accountId));
    editor.remove(key(THEME_GLOBAL_THEME_DAYLIGHT_KEY, accountId));
    editor.remove(key(LOCAL_CHAT_IDS_COUNT, accountId));
    // editor.remove(key(PEER_TO_PEER_KEY, accountId));
    Settings.instance().removeScrollPositions(accountId, editor);
    String dismissPrefix = key(DISMISS_MESSAGE_PREFIX, accountId);
    String dismissReqPrefix = key(DISMISS_REQUESTS_PREFIX, accountId);
    String notificationGroupDataPrefix = key(NOTIFICATION_GROUP_DATA_PREFIX, accountId);
    String notificationDataPrefix = key(NOTIFICATION_DATA_PREFIX, accountId);
    String conversionPrefix = key(CONVERSION_PREFIX, accountId);
    String localChatIdPrefix = key(LOCAL_CHAT_ID_PREFIX, accountId);
    String remoteChatIdPrefix = key(REMOTE_CHAT_ID_PREFIX, accountId);
    Settings.instance().removeByAnyPrefix(new String[] {
      dismissPrefix,
      dismissReqPrefix,
      notificationGroupDataPrefix,
      notificationDataPrefix,
      conversionPrefix,
      localChatIdPrefix,
      remoteChatIdPrefix
    }, editor);
    editor.apply();

    _globalTheme = _globalThemeDaylight = _globalThemeNight = null;
    _notificationErrorCount = null;
    _chatStyle = null;
    _forcePlainModeInChannels = null;
    _userPreferences = null;
    _localChatIdsCount = null;
    remoteToLocalChatIds.clear();
    localToRemoteChatIds.clear();

    unregisterDevice(tdlib.id());

    load();
  }

  // Dismiss pinned messages

  public interface DismissMessageListener {
    void onPinnedMessageDismissed (long chatId, long messageId);
    void onPinnedMessageRestored (long chatId);
  }

  private final ReferenceList<DismissMessageListener> dismissMessageListeners = new ReferenceList<>();

  public void addPinnedMessageDismissListener (DismissMessageListener listener) {
    dismissMessageListeners.add(listener);
  }

  public void removePinnedMessageDismissListener (DismissMessageListener listener) {
    dismissMessageListeners.remove(listener);
  }

  public void dismissMessage (long chatId, long messageId) {
    Settings.instance().putLong(key(DISMISS_MESSAGE_PREFIX, tdlib.id()) + chatId, messageId);
    for (DismissMessageListener listener : dismissMessageListeners) {
      listener.onPinnedMessageDismissed(chatId, messageId);
    }
  }

  public void restorePinnedMessages (long chatId) {
    Settings.instance().remove(key(DISMISS_MESSAGE_PREFIX, tdlib.id()) + chatId);
    for (DismissMessageListener listener : dismissMessageListeners) {
      listener.onPinnedMessageRestored(chatId);
    }
  }

  public boolean isMessageDismissed (long chatId, long messageId) {
    return messageId != 0 && Settings.instance().getLong(key(DISMISS_MESSAGE_PREFIX, tdlib.id()) + chatId, 0) >= messageId;
  }

  public boolean hasDismissedMessages (long chatId) {
    return Settings.instance().getLong(key(DISMISS_MESSAGE_PREFIX, tdlib.id()) + chatId, 0) > 0;
  }

  // Dismiss join requests

  public interface DismissRequestsListener {
    void onJoinRequestsDismissed (long chatId);
    void onJoinRequestsRestore (long chatId);
  }

  private final ReferenceList<DismissRequestsListener> dismissRequestsListeners = new ReferenceList<>();

  public void addJoinRequestsDismissListener (DismissRequestsListener listener) {
    dismissRequestsListeners.add(listener);
  }

  public void removeJoinRequestsDismissListener (DismissRequestsListener listener) {
    dismissRequestsListeners.remove(listener);
  }

  public void dismissRequests (long chatId, TdApi.ChatJoinRequestsInfo pendingInfo) {
    Settings.instance().putLongArray(key(DISMISS_REQUESTS_PREFIX, tdlib.id()) + chatId, pendingInfo.userIds);
    for (DismissRequestsListener listener : dismissRequestsListeners) {
      listener.onJoinRequestsDismissed(chatId);
    }
  }

  public void restoreRequests (long chatId, boolean silent) {
    Settings.instance().remove(key(DISMISS_REQUESTS_PREFIX, tdlib.id()) + chatId);
    if (!silent) {
      for (DismissRequestsListener listener : dismissRequestsListeners) {
        listener.onJoinRequestsRestore(chatId);
      }
    }
  }

  public boolean isRequestsDismissed (long chatId, TdApi.ChatJoinRequestsInfo pendingInfo) {
    return pendingInfo == null || Arrays.equals(Settings.instance().getLongArray(key(DISMISS_REQUESTS_PREFIX, tdlib.id()) + chatId), pendingInfo.userIds);
  }

  public boolean forcePlainModeInChannels () {
    if (_forcePlainModeInChannels == null)
      _forcePlainModeInChannels = Settings.instance().getBoolean(key(PLAIN_CHANNEL_KEY, tdlib.id()), true);
    return _forcePlainModeInChannels;
  }

  private static String makeConversionKey (int accountId, String originalPath, String conversion) {
    return key(CONVERSION_PREFIX, accountId) + conversion + "_" + originalPath;
  }

  public boolean isKnownConversion (String originalPath, String conversion) {
    return Settings.instance().containsKey(makeConversionKey(tdlib.id(), originalPath, conversion));
  }

  public void rememberConversion (String originalPath, String conversion) {
    Settings.instance().putVoid(makeConversionKey(tdlib.id(), originalPath, conversion));
  }

  public void forgetConversion (String originalPath, String conversion) {
    Settings.instance().remove(makeConversionKey(tdlib.id(), originalPath, conversion));
  }

  public void setForcePlainModeInChannels (boolean forcePlainMode) {
    this._forcePlainModeInChannels = forcePlainMode;
    Settings.instance().putBoolean(key(PLAIN_CHANNEL_KEY, tdlib.id()), forcePlainMode);
  }

  public void setApplicationConfig (String json) {
    String key = key(CONFIG_KEY, tdlib.id());
    if (StringUtils.isEmpty(json)) {
      Settings.instance().remove(key);
    } else {
      Settings.instance().putString(key, json);
    }
  }

  public String getApplicationConfig () {
    return Settings.instance().getString(key(CONFIG_KEY, tdlib.id()), null);
  }

  public void replaceGlobalTheme (TdlibSettingsManager otherSettings) {
    int theme = otherSettings.globalTheme();
    int daylightTheme = otherSettings.globalDaylightTheme();
    int nightTheme = otherSettings.globalNightTheme();
    final int accountId = tdlib.id();
    SharedPreferences.Editor editor = Settings.instance().edit();
    editor.putInt(key(THEME_GLOBAL_THEME_DAYLIGHT_KEY, accountId), this._globalThemeDaylight = daylightTheme);
    editor.putInt(key(THEME_GLOBAL_THEME_NIGHT_KEY, accountId), this._globalThemeNight = nightTheme);
    editor.putInt(key(THEME_GLOBAL_THEME_KEY, accountId), this._globalTheme = theme);
    editor.apply();
  }

  public static int getThemeId (Settings prefs, int accountId, boolean isDark) {
    return ThemeManager.restoreThemeId(prefs, readDefault(prefs, THEME_GLOBAL_THEME_DAYLIGHT_KEY, 0, accountId), true, isDark);
  }

  public void replaceThemeId (int oldThemeId, int newThemeId) {
    if (globalTheme() != oldThemeId && globalDaylightTheme() != oldThemeId && globalNightTheme() != oldThemeId)
      return;
    final int accountId = tdlib.id();
    SharedPreferences.Editor editor = Settings.instance().edit();
    if (globalDaylightTheme() == oldThemeId)
      editor.putInt(key(THEME_GLOBAL_THEME_DAYLIGHT_KEY, accountId), this._globalThemeDaylight = newThemeId);
    if (globalNightTheme() == oldThemeId)
      editor.putInt(key(THEME_GLOBAL_THEME_NIGHT_KEY, accountId), this._globalThemeNight = newThemeId);
    if (globalTheme() == oldThemeId)
      editor.putInt(key(THEME_GLOBAL_THEME_KEY, accountId), this._globalTheme = newThemeId);
    editor.apply();
  }

  public static void replaceThemeId (int accountId, int oldThemeId, int newThemeId) {
    Settings prefs = Settings.instance();
    int globalTheme = ThemeManager.restoreThemeId(readDefault(prefs, THEME_GLOBAL_THEME_KEY, 0, accountId), true);
    int globalThemeDaylight = ThemeManager.restoreThemeId(readDefault(prefs, THEME_GLOBAL_THEME_DAYLIGHT_KEY, 0, accountId), true, false);
    int globalThemeNight = ThemeManager.restoreThemeId(readDefault(prefs, THEME_GLOBAL_THEME_NIGHT_KEY, 0, accountId), true, true);

    if (globalTheme != oldThemeId && globalThemeDaylight != oldThemeId && globalThemeNight != oldThemeId)
      return;

    SharedPreferences.Editor editor = Settings.instance().edit();
    if (globalThemeDaylight == oldThemeId)
      editor.putInt(key(THEME_GLOBAL_THEME_DAYLIGHT_KEY, accountId), newThemeId);
    if (globalThemeNight == oldThemeId)
      editor.putInt(key(THEME_GLOBAL_THEME_NIGHT_KEY, accountId), newThemeId);
    if (globalTheme == oldThemeId)
      editor.putInt(key(THEME_GLOBAL_THEME_KEY, accountId), newThemeId);
    editor.apply();
  }

  public void fixThemeId (int themeId, boolean isDark, int parentThemeId) {
    final int accountId = tdlib.id();
    SharedPreferences.Editor editor = null;
    if (isDark && globalDaylightTheme() == themeId) {
      int dayThemeId = parentThemeId == 0 || Theme.isDarkTheme(parentThemeId) ? ThemeManager.DEFAULT_LIGHT_THEME : parentThemeId;
      editor = Settings.instance().edit();
      editor.putInt(key(THEME_GLOBAL_THEME_DAYLIGHT_KEY, accountId), ThemeManager.saveThemeId(_globalThemeDaylight = dayThemeId));
      editor.putInt(key(THEME_GLOBAL_THEME_NIGHT_KEY, accountId), ThemeManager.saveThemeId(_globalThemeNight = themeId));
    } else if (!isDark && globalNightTheme() == themeId) {
      int nightThemeId = parentThemeId == 0 || !Theme.isDarkTheme(parentThemeId) ? ThemeManager.DEFAULT_DARK_THEME : parentThemeId;
      editor = Settings.instance().edit();
      editor.putInt(key(THEME_GLOBAL_THEME_NIGHT_KEY, accountId), ThemeManager.saveThemeId(_globalThemeNight = nightThemeId));
      editor.putInt(key(THEME_GLOBAL_THEME_DAYLIGHT_KEY, accountId), ThemeManager.saveThemeId(_globalThemeDaylight = themeId));
    }
    if (editor != null)
      editor.apply();
  }

  public static void fixThemeId (int accountId, int themeId, boolean isDark, int parentThemeId) {
    Settings prefs = Settings.instance();
    int globalThemeDaylight = ThemeManager.restoreThemeId(readDefault(prefs, THEME_GLOBAL_THEME_DAYLIGHT_KEY, 0, accountId), true, false);
    int globalThemeNight = ThemeManager.restoreThemeId(readDefault(prefs, THEME_GLOBAL_THEME_NIGHT_KEY, 0, accountId), true, true);

    SharedPreferences.Editor editor = null;
    if (isDark && globalThemeDaylight == themeId) {
      int dayThemeId = parentThemeId == 0 || Theme.isDarkTheme(parentThemeId) ? ThemeManager.DEFAULT_LIGHT_THEME : parentThemeId;
      editor = Settings.instance().edit();
      editor.putInt(key(THEME_GLOBAL_THEME_DAYLIGHT_KEY, accountId), ThemeManager.saveThemeId(globalThemeDaylight = dayThemeId));
      editor.putInt(key(THEME_GLOBAL_THEME_NIGHT_KEY, accountId), ThemeManager.saveThemeId(globalThemeNight = themeId));
    } else if (!isDark && globalThemeNight == themeId) {
      int nightThemeId = parentThemeId == 0 || !Theme.isDarkTheme(parentThemeId) ? ThemeManager.DEFAULT_DARK_THEME : parentThemeId;
      editor = Settings.instance().edit();
      editor.putInt(key(THEME_GLOBAL_THEME_NIGHT_KEY, accountId), ThemeManager.saveThemeId(globalThemeNight = nightThemeId));
      editor.putInt(key(THEME_GLOBAL_THEME_DAYLIGHT_KEY, accountId), ThemeManager.saveThemeId(globalThemeDaylight = themeId));
    }
    if (editor != null)
      editor.apply();
  }

  public void setGlobalTheme (@ThemeId int theme) {
    int oldTheme = globalTheme();
    if (oldTheme != theme) {
      final int accountId = tdlib.id();
      SharedPreferences.Editor editor = Settings.instance().edit();
      if (Theme.isDarkTheme(theme)) {
        editor.putInt(key(THEME_GLOBAL_THEME_NIGHT_KEY, accountId), ThemeManager.saveThemeId(this._globalThemeNight = theme));
      } else if (Theme.isDarkTheme(oldTheme)) {
        editor.putInt(key(THEME_GLOBAL_THEME_NIGHT_KEY, accountId), ThemeManager.saveThemeId(this._globalThemeNight = oldTheme));
      }
      if (!Theme.isDarkTheme(theme)) {
        editor.putInt(key(THEME_GLOBAL_THEME_DAYLIGHT_KEY, accountId), ThemeManager.saveThemeId(this._globalThemeDaylight = theme));
      } else if (!Theme.isDarkTheme(oldTheme)) {
        editor.putInt(key(THEME_GLOBAL_THEME_DAYLIGHT_KEY, accountId), ThemeManager.saveThemeId(this._globalThemeDaylight = oldTheme));
      }
      editor.putInt(key(THEME_GLOBAL_THEME_KEY, accountId), ThemeManager.saveThemeId(this._globalTheme = theme));
      editor.apply();

      tdlib.wallpaper().onThemeSwitched(oldTheme, theme);
    }
  }

  public int globalTheme () {
    if (_globalTheme == null) {
      int globalTheme = ThemeManager.restoreThemeId(readDefault(Settings.instance(), THEME_GLOBAL_THEME_KEY, 0, tdlib.id()), true);
      if (Settings.instance().getNightMode() == Settings.NIGHT_MODE_SCHEDULED) {
        int desiredGlobalTheme = Settings.instance().inNightSchedule() ? globalDaylightTheme() : globalNightTheme();
        if (globalTheme != desiredGlobalTheme) {
          globalTheme = desiredGlobalTheme;
          Settings.instance().putInt(key(THEME_GLOBAL_THEME_KEY, tdlib.id()), globalTheme);
        }
      }
      _globalTheme = globalTheme;
    }
    return _globalTheme;
  }

  public int globalDaylightTheme () {
    if (_globalThemeDaylight == null)
      _globalThemeDaylight = ThemeManager.restoreThemeId(readDefault(Settings.instance(), THEME_GLOBAL_THEME_DAYLIGHT_KEY, 0, tdlib.id()), true, false);
    return _globalThemeDaylight;
  }

  public int globalNightTheme () {
    if (_globalThemeNight == null)
      _globalThemeNight = ThemeManager.restoreThemeId(readDefault(Settings.instance(), THEME_GLOBAL_THEME_NIGHT_KEY, 0, tdlib.id()), true, true);
    return _globalThemeNight;
  }

  public static void deleteWallpaper (int accountId, int usageIdentifier) {
    SharedPreferences.Editor editor = Settings.instance().edit();
    Settings.instance().deleteWallpaper(accountId, editor, usageIdentifier);
    editor.apply();
  }

  public void deleteWallpaper (int usageIdentifier) {
    wallpapers.remove(usageIdentifier);
    deleteWallpaper(tdlib.id(), usageIdentifier);
  }

  public void setWallpaper (@NonNull TGBackground wallpaper, boolean force, int usageIdentifier) {
    TGBackground currentWallpaper = getWallpaper(usageIdentifier);
    if ((currentWallpaper == null && wallpaper != null) || (force && !TGBackground.compare(currentWallpaper, wallpaper, false))) {
      ThemeManager.instance().notifyChatWallpaperChanged(tdlib, wallpaper, 0, usageIdentifier);
      if (wallpaper != null) {
        wallpaper.save(usageIdentifier);
        wallpaper.load(tdlib);
      } else {
        SharedPreferences.Editor editor = Settings.instance().edit();
        Settings.instance().deleteWallpaper(tdlib, editor, usageIdentifier);
        editor.apply();
      }
      wallpapers.put(usageIdentifier, wallpaper);
    }
  }

  public void setChatStyle (@ChatStyle int chatStyle) {
    if (chatStyle() != chatStyle) {
      this._chatStyle = chatStyle;
      Settings.instance().putInt(key(THEME_CHAT_STYLE_KEY, tdlib.id()), chatStyle);
      if (chatStyle == ThemeManager.CHAT_STYLE_BUBBLES) {
        tdlib.wallpaper().ensureWallpaperAvailability();
      }
      ThemeManager.instance().notifyChatStyleChanged(tdlib, chatStyle);
    }
  }

  public void toggleChatStyle () {
    setChatStyle(chatStyle() == ThemeManager.CHAT_STYLE_BUBBLES ? ThemeManager.CHAT_STYLE_MODERN : ThemeManager.CHAT_STYLE_BUBBLES);
  }

  @ChatStyle
  public int chatStyle () {
    if (_chatStyle == null) {
      final int accountId = tdlib.id();
      SharedPreferences prefs = Settings.instance().pmc();

      int chatStyle;
      final String keyChatStyle = key(THEME_CHAT_STYLE_KEY, accountId);
      chatStyle = prefs.getInt(keyChatStyle, ThemeManager.CHAT_STYLE_UNKNOWN);
      if (chatStyle == ThemeManager.CHAT_STYLE_UNKNOWN && accountId > 0) {
        chatStyle = prefs.getInt(THEME_CHAT_STYLE_KEY, ThemeManager.CHAT_STYLE_UNKNOWN);
      }
      if (chatStyle != ThemeManager.CHAT_STYLE_UNKNOWN) {
        chatStyle = ThemeManager.restoreChatStyle(chatStyle);
      } else {
        int defaultStyle = ThemeManager.CHAT_STYLE_BUBBLES;
        try {
          String language = Locale.getDefault().getLanguage();
          if (!StringUtils.isEmpty(language)) {
            if (language.equals(new Locale("ja").getLanguage()) ||
              language.equals(new Locale("ko").getLanguage()) ||
              language.equals(new Locale("zh").getLanguage())) {
              defaultStyle = ThemeManager.CHAT_STYLE_MODERN;
            }
          }
        } catch (Throwable ignored) { }
        prefs.edit().putInt(keyChatStyle, defaultStyle).apply();
        chatStyle = defaultStyle;
      }
      _chatStyle = chatStyle;
    }
    return _chatStyle;
  }

  public @Nullable TGBackground getWallpaper (int usageIdentifier) {
    return getWallpaper(usageIdentifier, false);
  }

  public @Nullable TGBackground getWallpaper (int usageIdentifier, boolean allowEmpty) {
    int i = wallpapers.indexOfKey(usageIdentifier);
    TGBackground wallpaper = i >= 0 ? wallpapers.valueAt(i) : null;
    if (i < 0) {
      wallpaper = TGBackground.restore(tdlib, usageIdentifier);
      wallpapers.put(usageIdentifier, wallpaper);
    }
    return wallpaper != null && (allowEmpty || !wallpaper.isEmpty()) ? wallpaper : null;
  }

  public boolean useBubbles () {
    return chatStyle() == ThemeManager.CHAT_STYLE_BUBBLES;
  }

 /* public int peerToPeerOption () {
    return Settings.instance().getInt(key(PEER_TO_PEER_KEY, tdlib.id()), TD.TYPE_EVERYBODY);
  }

  public void setPeerToPeerOption (int option) {
    if (option == TD.TYPE_EVERYBODY) {
      Settings.instance().remove(key(PEER_TO_PEER_KEY, tdlib.id()));
    } else {
      Settings.instance().putInt(key(PEER_TO_PEER_KEY, tdlib.id()), option);
    }
  }*/

  public void setNotificationGroupData (int groupId, int hiddenNotificationId, int flags) {
    String key = key(NOTIFICATION_GROUP_DATA_PREFIX + groupId, tdlib.id());
    if (hiddenNotificationId == 0 && flags == 0) {
      Settings.instance().remove(key);
    } else {
      long data = BitwiseUtils.mergeLong(hiddenNotificationId, flags);
      Settings.instance().putLong(key, data);
    }
  }

  public long getNotificationGroupData (int groupId) {
    String key = key(NOTIFICATION_GROUP_DATA_PREFIX + groupId, tdlib.id());
    return Settings.instance().getLong(key, 0);
  }

  public void setNotificationData (int notificationId, int flags) {
    String key = key(NOTIFICATION_DATA_PREFIX + notificationId, tdlib.id());
    if (flags != 0) {
      Settings.instance().putInt(key, flags);
    } else {
      Settings.instance().remove(key);
    }
  }

  public void deleteHiddenNotificationIds () {
    LevelDB db = Settings.instance().pmc();
    db.removeByPrefix(key(NOTIFICATION_DATA_PREFIX, tdlib.id()));
    db.removeByPrefix(key(NOTIFICATION_GROUP_DATA_PREFIX, tdlib.id()));
    db.flush();
  }

  public int getNotificationData (int notificationId) {
    String key = key(NOTIFICATION_DATA_PREFIX + notificationId, tdlib.id());
    return Settings.instance().getInt(key, 0);
  }

  private static long getRegisteredDeviceUserId (int accountId) {
    return Settings.instance().getLong(key(DEVICE_UID_KEY, accountId), 0);
  }

  private static int getRegisteredDeviceTdlibVersion (int accountId) {
    return Settings.instance().getInt(key(DEVICE_TDLIB_VERSION_KEY, accountId), 0);
  }

  private static String getRegisteredDeviceToken (int accountId) {
    return Settings.instance().getString(key(DEVICE_TOKEN_KEY, accountId), null);
  }

  private static long[] getRegisteredDeviceOtherUserIds (int accountId) {
    return Settings.instance().pmc().getLongArray(key(DEVICE_OTHER_UID_KEY, accountId));
  }

  public static void setRegisteredDevice (int accountId, long userId, String token, @Nullable long[] otherUserIds) {
    if (StringUtils.isEmpty(token)) {
      unregisterDevice(accountId);
    } else {
      LevelDB pmc = Settings.instance().edit();
      pmc.putString(key(DEVICE_TOKEN_KEY, accountId), token);
      pmc.putLong(key(DEVICE_UID_KEY, accountId), userId);
      pmc.putInt(key(DEVICE_TDLIB_VERSION_KEY, accountId), BuildConfig.TDLIB_VERSION);
      if (otherUserIds != null && otherUserIds.length > 0) {
        pmc.putLongArray(key(DEVICE_OTHER_UID_KEY, accountId), otherUserIds);
      } else {
        pmc.remove(key(DEVICE_OTHER_UID_KEY, accountId));
      }
      pmc.apply();
    }
  }

  public static boolean checkRegisteredDeviceToken (int accountId, long userId, String token, long[] otherUserIds, boolean skipOtherUserIdsCheck) {
    return
      getRegisteredDeviceTdlibVersion(accountId) == BuildConfig.TDLIB_VERSION &&
      getRegisteredDeviceUserId(accountId) == userId &&
      StringUtils.equalsOrBothEmpty(getRegisteredDeviceToken(accountId), token) &&
      (skipOtherUserIdsCheck || Arrays.equals(getRegisteredDeviceOtherUserIds(accountId), otherUserIds != null && otherUserIds.length > 0 ? otherUserIds : null));
  }

  public static void unregisterDevice (int accountId) {
    Settings.instance().edit()
      .remove(key(DEVICE_TOKEN_KEY, accountId))
      .remove(key(DEVICE_UID_KEY, accountId))
      .remove(key(DEVICE_OTHER_UID_KEY, accountId))
      .remove(key(DEVICE_TDLIB_VERSION_KEY, accountId))
      .apply();
  }

  private long nextLocalChatId () {
    final String key = key(LOCAL_CHAT_IDS_COUNT, tdlib.id());
    if (_localChatIdsCount == null) {
      _localChatIdsCount = Settings.instance().getLong(key, 0);
    }
    final long nextLocalChatId = ++_localChatIdsCount;
    Settings.instance().putLong(key, nextLocalChatId);
    return nextLocalChatId;
  }

  public long getLocalChatId (long remoteChatId) {
    synchronized (localChatIdsSync) {
      long localChatId = remoteToLocalChatIds.get(remoteChatId);
      if (localChatId == 0) {
        final String key = key(LOCAL_CHAT_ID_PREFIX, tdlib.id()) + remoteChatId;
        localChatId = Settings.instance().getLong(key, 0);
        if (localChatId == 0) {
          localChatId = nextLocalChatId();
          Settings.instance().putLong(key, localChatId);
          Settings.instance().putLong(key(REMOTE_CHAT_ID_PREFIX, tdlib.id()) + localChatId, remoteChatId);
        }
        remoteToLocalChatIds.put(remoteChatId, localChatId);
        localToRemoteChatIds.put(localChatId, remoteChatId);
      }
      return localChatId;
    }
  }

  public long getRemoteChatId (long localChatId) {
    synchronized (localChatIdsSync) {
      long remoteChatId = localToRemoteChatIds.get(localChatId);
      if (remoteChatId == 0) {
        remoteChatId = Settings.instance().getLong(key(REMOTE_CHAT_ID_PREFIX, tdlib.id()) + localChatId, 0);
        if (remoteChatId != 0) {
          localToRemoteChatIds.put(localChatId, remoteChatId);
          remoteToLocalChatIds.put(remoteChatId, localChatId);
        }
      }
      return remoteChatId;
    }
  }

  public static long getRemoteChatId (int accountId, long localChatId) {
    return Settings.instance().getLong(key(REMOTE_CHAT_ID_PREFIX, accountId) + localChatId, 0);
  }

  // Preferences

  public static final long PREFERENCE_HIDE_ARCHIVE = 1;
  public static final long PREFERENCE_MUTE_NON_CONTACTS = 1 << 1;

  public boolean needHideArchive () {
    return getUserPreference(PREFERENCE_HIDE_ARCHIVE);
  }

  public boolean needMuteNonContacts () {
    return getUserPreference(PREFERENCE_MUTE_NON_CONTACTS);
  }

  public void toggleUserPreference (long key) {
    boolean value = getUserPreference(key);
    setUserPreference(key, !value);
  }

  private long getUserPreferences () {
    if (_userPreferences == null)
      _userPreferences = Settings.instance().getLong(key(PREFERENCES_KEY, tdlib.id()), 0);
    return _userPreferences;
  }

  private boolean getUserPreference (long key) {
    return BitwiseUtils.getFlag(getUserPreferences(), key);
  }

  public boolean setUserPreference (long key, boolean value) {
    long preferences = getUserPreferences();
    long newPreferences = BitwiseUtils.setFlag(preferences, key, value);
    if (preferences != newPreferences) {
      Settings.instance().putLong(key(PREFERENCES_KEY, tdlib.id()), _userPreferences = newPreferences);
      notifyPreferenceChanged(key, value);
      return true;
    }
    return false;
  }

  public interface PreferenceChangeListener {
    void onPreferenceChanged (Tdlib tdlib, long key, boolean value);
  }

  private ReferenceList<PreferenceChangeListener> preferenceListeners;

  public void addUserPreferenceChangeListener (PreferenceChangeListener listener) {
    if (preferenceListeners == null)
      preferenceListeners = new ReferenceList<>();
    preferenceListeners.add(listener);
  }

  public void removeUserPreferenceChangeListener (PreferenceChangeListener listener) {
    if (preferenceListeners != null) {
      preferenceListeners.remove(listener);
    }
  }

  private void notifyPreferenceChanged (long key, boolean value) {
    for (PreferenceChangeListener listener : preferenceListeners) {
      listener.onPreferenceChanged(tdlib, key, value);
    }
  }

  public int getNotificationProblemCount () {
    if (_notificationErrorCount == null) {
      int versionCode = Settings.instance().getInt(key(NOTIFICATION_VERSION_KEY, tdlib.id()), BuildConfig.VERSION_CODE);
      if (versionCode != BuildConfig.VERSION_CODE) {
        LevelDB editor = Settings.instance().edit();
        editor.removeByPrefix(key(NOTIFICATION_ERROR_KEY, tdlib.id()));
        editor.remove(key(NOTIFICATION_VERSION_KEY, tdlib.id()));
        editor.apply();
      }
      _notificationErrorCount = Settings.instance().getInt(key(NOTIFICATION_ERROR_KEY, tdlib.id()), 0);
    }
    return _notificationErrorCount;
  }

  public interface NotificationProblemListener {
    void onNotificationProblemsAvailabilityChanged (Tdlib tdlib, boolean available);
  }

  private ReferenceList<NotificationProblemListener> notificationProblemListeners;

  public void addNotificationProblemAvailabilityChangeListener (NotificationProblemListener listener) {
    if (notificationProblemListeners == null) {
      notificationProblemListeners = new ReferenceList<>(true);
    }
    notificationProblemListeners.add(listener);
  }

  public void removeNotificationProblemAvailabilityChangeListener (NotificationProblemListener listener) {
    if (notificationProblemListeners != null) {
      notificationProblemListeners.remove(listener);
    }
  }

  public synchronized void trackNotificationProblem (Throwable t, boolean isDisplayError, long chatId) {
    final int id = getNotificationProblemCount() + 1;
    final boolean isFirst = id == 1;

    final byte flags = (byte) (
      (isDisplayError ? 1 : 0)
    );
    Blob b = new Blob(1 + Log.blobSize(t));
    b.writeByte(flags);
    Log.toBlob(t, b);
    final byte[] value = b.toByteArray();

    final String prefix = key(NOTIFICATION_ERROR_KEY, tdlib.id());
    final String existingKey = Settings.instance().pmc().findByValue(prefix, value);

    LevelDB editor = Settings.instance().edit();
    if (isFirst) {
      editor.putInt(key(NOTIFICATION_VERSION_KEY, tdlib.id()), BuildConfig.VERSION_CODE);
    }
    editor.putInt(prefix, id);
    if (existingKey != null && existingKey.endsWith("_data")) {
      final String existingPrefix = existingKey.substring(0, existingKey.length() - "data".length());
      String countKey = existingPrefix + "count";
      String timeKey = existingPrefix + "time";
      String chatKey = existingPrefix + "chat";
      editor.putInt(countKey, editor.getInt(countKey, 1) + 1);
      if (chatId != 0) {
        editor.putLong(chatKey, chatId);
      } else {
        editor.remove(chatKey);
      }
      editor.putLong(timeKey, System.currentTimeMillis());
    } else {
      String dataKey = prefix + "_" + id + "_data";
      String timeKey = prefix + "_" + id + "_time";
      String chatKey = prefix + "_" + id + "_chat";

      editor.putByteArray(dataKey, value);
      if (chatId != 0) {
        editor.putLong(chatKey, chatId);
      } else {
        editor.remove(chatKey);
      }
      editor.putLong(timeKey, System.currentTimeMillis());
    }
    editor.apply();

    _notificationErrorCount = id;
    if (isFirst && notificationProblemListeners != null) {
      for (NotificationProblemListener listener : notificationProblemListeners) {
        listener.onNotificationProblemsAvailabilityChanged(tdlib, true);
      }
    }
  }

  public void forgetNotificationProblems () {
    if (getNotificationProblemCount() > 0) {
      _notificationErrorCount = 0;
      Settings.instance().removeByPrefix(key(NOTIFICATION_ERROR_KEY, tdlib.id()), null);
      if (notificationProblemListeners != null) {
        for (NotificationProblemListener listener : notificationProblemListeners) {
          listener.onNotificationProblemsAvailabilityChanged(tdlib, false);
        }
      }
    }
  }

  private static class NotificationError {
    public final long id;

    public int eventCount = 1;
    public long lastEventTime;
    public long chatId;

    public int flags;
    public Log.ThrowableInfo info;

    public NotificationError (long id) {
      this.id = id;
    }

    public boolean isDisplayError () {
      return BitwiseUtils.getFlag(flags, 1);
    }

    public long getChatId () {
      return chatId;
    }
  }

  public long getLastNotificationProblematicChat () {
    final int totalCount = getNotificationProblemCount();
    if (totalCount == 0)
      return 0;

    final String prefix = key(NOTIFICATION_ERROR_KEY + "_", tdlib.id());
    long maxTime = 0;
    long reportId = 0;
    long resultChatId = 0;

    long chatId = 0;
    long chatIdReportId = 0;
    for (LevelDB.Entry entry : Settings.instance().pmc().find(prefix)) {
      String key = entry.key();
      int i = key.indexOf('_', prefix.length());
      if (i == -1)
        continue;

      long id = StringUtils.parseLong(key.substring(prefix.length(), i));
      String suffix = key.substring(i + 1);

      switch (suffix) {
        case "time": {
          long time = entry.asLong();
          if (time > maxTime) {
            maxTime = time;
            reportId = id;
            resultChatId = chatIdReportId == id ? chatId : 0;
          }
          break;
        }
        case "chat": {
          chatId = entry.asLong();
          chatIdReportId = id;
          if (id == reportId) {
            resultChatId = chatId;
          }
          break;
        }
      }
    }
    return resultChatId;
  }

  public String buildNotificationReport () {
    final int totalCount = getNotificationProblemCount();
    if (totalCount == 0)
      return null;

    final LongSparseArray<NotificationError> errors = new LongSparseArray<>();
    NotificationError error = null;
    int errorCount = 0;

    final String prefix = key(NOTIFICATION_ERROR_KEY + "_", tdlib.id());
    for (LevelDB.Entry entry : Settings.instance().pmc().find(prefix)) {
      String key = entry.key();
      int i = key.indexOf('_', prefix.length());
      if (i == -1)
        continue;

      long id = StringUtils.parseLong(key.substring(prefix.length(), i));
      if (error == null || error.id != id) {
        error = errors.get(id);
        if (error == null) {
          errors.put(id, error = new NotificationError(id));
        }
      }

      String suffix = key.substring(i + 1);

      try {
        switch (suffix) {
          case "data": {
            byte[] data = entry.asByteArray();
            Blob blob = new Blob(data);
            error.flags = blob.readByte();
            error.info = Log.throwableFromBlob(blob);
            errorCount++;
            break;
          }
          case "time":
            error.lastEventTime = Math.max(error.lastEventTime, entry.asLong());
            break;
          case "count":
            error.eventCount = Math.max(1, entry.asInt());
            break;
          case "chat":
            error.chatId = entry.asLong();
            break;
        }
      } catch (Throwable t) {
        Log.e("Unable to parse part of a notification error", t);
      }
    }

    if (errors.isEmpty() || errorCount == 0) {
      forgetNotificationProblems();
      return null;
    }

    StringBuilder b = new StringBuilder(U.getUsefulMetadata(tdlib)).append("\n")
      .append("Total: ").append(totalCount).append("\n")
      .append("Now: ").append(Lang.getTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS));
    for (int i = 0; i < errors.size(); i++) {
      error = errors.valueAt(i);
      if (error.info == null)
        continue;
      b.append("\n\n");
      if (error.eventCount < totalCount) {
        b.append("Count: ").append(error.eventCount).append("\n");
      }
      if (error.lastEventTime != 0) {
        b.append("Date: ").append(Lang.getTimestamp(error.lastEventTime, TimeUnit.MILLISECONDS)).append("\n");
      }
      b.append("Step: ").append(error.isDisplayError() ? "display" : "build").append("\n");
      if (error.chatId != 0) {
        TdApi.Chat chat = tdlib.chatSync(error.chatId, 500);
        if (chat != null) {
          String username = tdlib.chatUsername(chat.id);
          b.append("chat: ").append(error.chatId).append(", title: ").append(tdlib.chatTitle(chat));
          if (!StringUtils.isEmpty(username)) {
            b.append("username: @").append(username);
          }
          b.append("\n");
        } else {
          b.append("chat: ").append(error.chatId).append("\n");
        }
      }
      b.append(error.info);
    }
    return b.toString();
  }

  public boolean hasNotificationProblems () {
    return getNotificationProblemCount() > 0;
  }
}
