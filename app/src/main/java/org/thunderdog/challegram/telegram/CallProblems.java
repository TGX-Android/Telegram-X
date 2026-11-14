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
 * File created on 06/11/2026
 */
package org.thunderdog.challegram.telegram;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.AppContext;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// What the system tells that keeps calls, above all incoming ones in background, from showing;
// guesses, e.g. a firmware that may kill apps, aren't problems
public final class CallProblems {
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    Type.NONE,
    Type.FULL_SCREEN_INTENT_DENIED,
    Type.BACKGROUND_RESTRICTED,
    Type.CHANNEL_SILENCED,
    Type.APP_CHANNEL_HIDDEN,
    Type.ONGOING_CHANNEL_OFF
  })
  public @interface Type {
    int
      NONE = 0,
      FULL_SCREEN_INTENT_DENIED = 1,
      BACKGROUND_RESTRICTED = 2,
      CHANNEL_SILENCED = 3,
      APP_CHANNEL_HIDDEN = 4,
      ONGOING_CHANNEL_OFF = 5;
  }

  private CallProblems () { }

  private static final int UNKNOWN = -1;
  private static int lastProblem = UNKNOWN;
  private static boolean isLastOngoingChannelOff;

  public static @Type int get (Context context) {
    return lastProblem != UNKNOWN ? lastProblem : refresh(context);
  }

  public static @Type int get (Tdlib tdlib) {
    @Type int problem = get(AppContext.get());
    if (problem == Type.NONE) {
      if (!tdlib.notifications().canCallChannelRing(0)) {
        return Type.CHANNEL_SILENCED;
      }
      if (isLastOngoingChannelOff) {
        return Type.ONGOING_CHANNEL_OFF;
      }
    }
    return problem;
  }

  public static @Type int refresh (Context context) {
    lastProblem = find(context);
    isLastOngoingChannelOff = isOngoingChannelOff(context);
    return lastProblem;
  }

  public static boolean isFullScreenIntentDenied (Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      NotificationManager notificationManager =
        context.getSystemService(NotificationManager.class);
      return notificationManager != null && !notificationManager.canUseFullScreenIntent();
    }
    return false;
  }

  private static @Type int find (Context context) {
    if (isFullScreenIntentDenied(context)) {
      return Type.FULL_SCREEN_INTENT_DENIED;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      ActivityManager activityManager = context.getSystemService(ActivityManager.class);
      if (activityManager != null && activityManager.isBackgroundRestricted()) {
        return Type.BACKGROUND_RESTRICTED;
      }
    }
    if (isAppChannelHidden(context)) {
      return Type.APP_CHANNEL_HIDDEN;
    }
    return Type.NONE;
  }

  public static boolean isOngoingChannelOff (Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager notificationManager =
        context.getSystemService(NotificationManager.class);
      android.app.NotificationChannel channel = notificationManager != null ?
        notificationManager.getNotificationChannel(U.CHANNEL_ID_ONGOING_CALLS) :
        null;
      return channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE;
    }
    return false;
  }

  public static boolean isAppChannelHidden (Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager notificationManager =
        context.getSystemService(NotificationManager.class);
      android.app.NotificationChannel channel = notificationManager != null ?
        notificationManager.getNotificationChannel(U.CHANNEL_ID_INCOMING_CALLS_IN_APP) :
        null;
      return channel != null && !TdlibNotificationChannelGroup.canShowCall(
        channel.getImportance(),
        channel.getLockscreenVisibility()
      );
    }
    return false;
  }

  public static @StringRes int getDescription (@Type int problem) {
    switch (problem) {
      case Type.FULL_SCREEN_INTENT_DENIED:
        return R.string.CallProblemFullScreen;
      case Type.BACKGROUND_RESTRICTED:
        return R.string.CallProblemBackground;
      case Type.CHANNEL_SILENCED:
        return R.string.CallProblemChannel;
      case Type.APP_CHANNEL_HIDDEN:
        return R.string.CallProblemAppChannel;
      case Type.ONGOING_CHANNEL_OFF:
        return R.string.CallProblemOngoingChannel;
      case Type.NONE:
      default:
        return 0;
    }
  }

  public static @StringRes int getAction (@Type int problem) {
    switch (problem) {
      case Type.FULL_SCREEN_INTENT_DENIED:
        return R.string.CallProblemFullScreenAction;
      case Type.BACKGROUND_RESTRICTED:
        return R.string.CallProblemBackgroundAction;
      case Type.CHANNEL_SILENCED:
      case Type.APP_CHANNEL_HIDDEN:
        return R.string.CallProblemChannelAction;
      case Type.ONGOING_CHANNEL_OFF:
        return R.string.CallProblemOngoingChannelAction;
      case Type.NONE:
      default:
        return 0;
    }
  }

  public static @StringRes int getGuide (@Type int problem) {
    switch (problem) {
      case Type.FULL_SCREEN_INTENT_DENIED:
        return R.string.CallProblemFullScreenGuide;
      case Type.BACKGROUND_RESTRICTED:
        return R.string.CallProblemBackgroundGuide;
      case Type.CHANNEL_SILENCED:
        return R.string.CallProblemChannelGuide;
      case Type.APP_CHANNEL_HIDDEN:
        return R.string.CallProblemAppChannelGuide;
      case Type.ONGOING_CHANNEL_OFF:
        return R.string.CallProblemOngoingChannelGuide;
      case Type.NONE:
      default:
        return 0;
    }
  }

  public static @StringRes int getGuideSteps (@Type int problem) {
    if (problem == Type.FULL_SCREEN_INTENT_DENIED) {
      return Device.IS_SAMSUNG ?
        R.string.CallProblemFullScreenStepsSamsung :
        R.string.CallProblemFullScreenSteps;
    }
    return 0;
  }

  public static void openSettings (Context context, @Type int problem) {
    // No screen of the system opens the battery settings of an app, they're on its page
    Uri uri = Uri.fromParts("package", context.getPackageName(), null);
    Intent intent;
    if (problem == Type.FULL_SCREEN_INTENT_DENIED &&
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, uri);
    } else if ((problem == Type.APP_CHANNEL_HIDDEN || problem == Type.ONGOING_CHANNEL_OFF) &&
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
      intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
      intent.putExtra(Settings.EXTRA_CHANNEL_ID, problem == Type.APP_CHANNEL_HIDDEN ?
        U.CHANNEL_ID_INCOMING_CALLS_IN_APP : U.CHANNEL_ID_ONGOING_CALLS);
    } else {
      intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri);
    }
    try {
      context.startActivity(intent);
    } catch (Throwable t) {
      Log.e("Unable to open the settings of a problem of calls", t);
      try {
        context.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri));
      } catch (Throwable fallback) {
        UI.showToast(R.string.NoAppToOpen, Toast.LENGTH_SHORT);
      }
    }
  }
}
