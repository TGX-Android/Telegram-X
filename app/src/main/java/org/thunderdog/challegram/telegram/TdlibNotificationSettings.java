package org.thunderdog.challegram.telegram;

import android.app.Notification;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * Date: 25/12/2018
 * Author: default
 */
public class TdlibNotificationSettings {
  private final int vibrateMode, ledColor, priority;
  @Nullable
  private final String soundPath; // null means default, empty means none, otherwise custom

  public TdlibNotificationSettings (Tdlib tdlib, long settingsChatId, TdlibNotificationGroup group) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      // Unused because of notification channels
      this.vibrateMode = this.ledColor = this.priority = 0;
      this.soundPath = null;
      return;
    }

    this.vibrateMode = tdlib.notifications().getEffectiveVibrateMode(settingsChatId);
    this.soundPath = tdlib.notifications().getEffectiveSound(settingsChatId);
    this.ledColor = tdlib.notifications().getEffectiveLedColor(settingsChatId);
    this.priority = tdlib.notifications().getEffectivePriorityOrImportance(settingsChatId);

    /*int vibrateMode;
    String soundPath;
    int ledColor;
    int priority;

    priority = tdlib.notifications().getPriority(settingsChatId, TD.isUserChat(settingsChatId));

    if (group.isMention()) {
      vibrateMode = tdlib.notifications().getCustomVibrateModeForChatId(settingsChatId);
      soundPath = tdlib.notifications().getCustomSoundForChatId(settingsChatId);
      ledColor = tdlib.notifications().getLedColor(settingsChatId);
      if (vibrateMode == TdlibNotificationManager.VIBRATE_MODE_DEFAULT) {
        vibrateMode = tdlib.notifications().getPrivateVibrateMode();
      }
      if (Strings.isEmpty(soundPath)) {
        soundPath = tdlib.notifications().getPrivateSound();
      }
    } else {
      vibrateMode = tdlib.notifications().getVibrateMode(settingsChatId);
      soundPath = tdlib.notifications().getSound(settingsChatId);
      ledColor = tdlib.notifications().getLedColor(settingsChatId);
      if (group.isMention()) {
        int singleSenderUserId = group.singleAuthorUserId();
        if (singleSenderUserId != 0) {
          priority = tdlib.notifications().getPriority(TD.userIdToChatId(singleSenderUserId), true);
        }
      }
    }

    this.vibrateMode = vibrateMode;
    this.soundPath = soundPath;
    this.ledColor = ledColor;
    this.priority = priority;*/
  }

  public static void applyStatic (NotificationCompat.Builder b, Tdlib tdlib, TdlibNotificationGroup group, boolean isSummary) {
    // Log.e("notification applyStatic isSummary:%b", isSummary);
    long settingsChatId = group.getChatId();
    if (group.isMention()) {
      long senderChatId = group.lastNotification().findSenderId();
      if (senderChatId != 0) {
        settingsChatId = senderChatId;
      }
    }
    int priority = tdlib.notifications().getEffectivePriorityOrImportance(settingsChatId);
    b.setPriority(priority);
  }

  public void apply (NotificationCompat.Builder b, boolean isSummary) {
    b.setPriority(priority);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      // Unused because of notification channels
      return;
    }

    int defaults = 0;

    if (ledColor != 0) {
      b.setLights(ledColor, 1000, 1000);
    }

    switch (vibrateMode) {
      case TdlibNotificationManager.VIBRATE_MODE_DEFAULT: {
        defaults |= Notification.DEFAULT_VIBRATE;
        break;
      }
      case TdlibNotificationManager.VIBRATE_MODE_SHORT: {
        b.setVibrate(TdlibNotificationManager.VIBRATE_SHORT_PATTERN);
        break;
      }
      case TdlibNotificationManager.VIBRATE_MODE_LONG: {
        b.setVibrate(TdlibNotificationManager.VIBRATE_LONG_PATTERN);
        break;
      }
      case TdlibNotificationManager.VIBRATE_MODE_DISABLED: {
        break;
      }
    }

    // Log.e("notification apply isCommon: %b, soundPath: %s", isSummary, soundPath == null ? "null" : soundPath.isEmpty() ? "empty" : soundPath);
    if (soundPath == null) {
      defaults |= Notification.DEFAULT_SOUND;
    } else if (!soundPath.isEmpty()) {
      /*
      * if (!Strings.isEmpty(soundPath) && soundPath.startsWith("file://")) {
        UI.getAppContext().grantUriPermission("com.android.systemui", Uri.parse(soundPath), Intent.FLAG_GRANT_READ_URI_PERMISSION);
      }
      * */
      /*Uri uri = null;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && soundPath.startsWith("file://")) {
        uri = U.contentUriFromFile(new File(soundPath.substring("file://".length())));
      }*/
      b.setSound(Uri.parse(soundPath));
    }

    if (defaults != 0) {
      b.setDefaults(defaults);
    }
  }
}
