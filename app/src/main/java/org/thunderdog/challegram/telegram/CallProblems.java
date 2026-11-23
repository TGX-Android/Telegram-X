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
import android.util.SparseBooleanArray;
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.StringRes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.core.BaseThread;
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
    Type.ONGOING_CHANNEL_OFF,
    Type.NOTIFICATIONS_BLOCKED,
    Type.PUSH_UNAVAILABLE,
    Type.CATEGORY_BLOCKED,
    Type.DISPLAY_FAILED
  })
  public @interface Type {
    int
      NONE = 0,
      FULL_SCREEN_INTENT_DENIED = 1,
      BACKGROUND_RESTRICTED = 2,
      CHANNEL_SILENCED = 3,
      APP_CHANNEL_HIDDEN = 4,
      ONGOING_CHANNEL_OFF = 5,
      NOTIFICATIONS_BLOCKED = 6,
      PUSH_UNAVAILABLE = 7,
      CATEGORY_BLOCKED = 8,
      DISPLAY_FAILED = 9;
  }

  private CallProblems () { }

  private static final int UNKNOWN = -1;
  // What the system answered last, read and written on the main thread
  private static int lastProblem = UNKNOWN;
  private static boolean isLastAppChannelHidden, isLastOngoingChannelOff;
  private static final SparseBooleanArray silencedChannels = new SparseBooleanArray();
  private static final SparseBooleanArray pendingRefreshes = new SparseBooleanArray();
  private static BaseThread refreshThread;

  private static BaseThread refreshThread () {
    synchronized (pendingRefreshes) {
      if (refreshThread == null) {
        refreshThread = new BaseThread("CallProblems");
      }
      return refreshThread;
    }
  }

  public static @Type int get (Tdlib tdlib) {
    if (!BuildConfig.CALLS_AVAILABLE) {
      return Type.NONE;
    }
    if (lastProblem == UNKNOWN || silencedChannels.indexOfKey(tdlib.id()) < 0) {
      refresh(tdlib);
    }
    if (lastProblem != UNKNOWN && lastProblem != Type.NONE) {
      return lastProblem;
    }
    return getChannelProblem(tdlib);
  }

  public static @Type int getOfSettings (Tdlib tdlib) {
    if (!BuildConfig.CALLS_AVAILABLE) {
      return Type.NONE;
    }
    switch (tdlib.notifications().getCallBlockStatus()) {
      case TdlibNotificationManager.Status.MISSING_PERMISSION:
      case TdlibNotificationManager.Status.BLOCKED_ALL:
        return Type.NOTIFICATIONS_BLOCKED;
      case TdlibNotificationManager.Status.BLOCKED_CATEGORY:
        return Type.CATEGORY_BLOCKED;
      case TdlibNotificationManager.Status.BACKGROUND_RESTRICTED:
        return Type.BACKGROUND_RESTRICTED;
      case TdlibNotificationManager.Status.DISABLED_SYNC:
      case TdlibNotificationManager.Status.DISABLED_APP_SYNC:
      case TdlibNotificationManager.Status.PUSH_SERVICE_MISSING:
      case TdlibNotificationManager.Status.PUSH_SERVICE_ERROR:
        return Type.PUSH_UNAVAILABLE;
      case TdlibNotificationManager.Status.INTERNAL_ERROR:
      case TdlibNotificationManager.Status.ACCOUNT_NOT_SELECTED:
      case TdlibNotificationManager.Status.NOT_BLOCKED:
        break;
    }
    if (tdlib.settings().hasCallProblems()) {
      return Type.DISPLAY_FAILED;
    }
    return get(tdlib);
  }

  public static boolean isOffForDevice (Tdlib tdlib) {
    TdApi.Session session = tdlib.currentSession();
    return session != null && !session.canAcceptCalls;
  }

  public static boolean isOfNotifications (@Type int problem) {
    return problem == Type.NOTIFICATIONS_BLOCKED || problem == Type.CATEGORY_BLOCKED ||
      problem == Type.PUSH_UNAVAILABLE;
  }

  private static @Type int getChannelProblem (Tdlib tdlib) {
    if (isLastAppChannelHidden) {
      return Type.APP_CHANNEL_HIDDEN;
    }
    if (silencedChannels.get(tdlib.id())) {
      return Type.CHANNEL_SILENCED;
    }
    if (isLastOngoingChannelOff) {
      return Type.ONGOING_CHANNEL_OFF;
    }
    return Type.NONE;
  }

  @AnyThread
  public static void refresh (Tdlib tdlib) {
    int accountId = tdlib.id();
    synchronized (pendingRefreshes) {
      if (pendingRefreshes.get(accountId)) {
        return;
      }
      pendingRefreshes.put(accountId, true);
    }
    // Not the queue of notifications, which may wait for their media for seconds
    refreshThread().post(() -> {
      synchronized (pendingRefreshes) {
        pendingRefreshes.delete(accountId);
      }
      Context context = AppContext.get();
      @Type int problem = find(context);
      boolean isAppChannelHidden = isAppChannelHidden(context);
      boolean isOngoingChannelOff = isOngoingChannelOff(context);
      boolean isChannelSilenced = !tdlib.notifications().canCallChannelRing(0);
      UI.post(() -> {
        boolean changed = problem != (lastProblem != UNKNOWN ? lastProblem : Type.NONE) ||
          isAppChannelHidden != isLastAppChannelHidden ||
          isOngoingChannelOff != isLastOngoingChannelOff ||
          isChannelSilenced != silencedChannels.get(accountId);
        lastProblem = problem;
        isLastAppChannelHidden = isAppChannelHidden;
        isLastOngoingChannelOff = isOngoingChannelOff;
        silencedChannels.put(accountId, isChannelSilenced);
        if (changed) {
          tdlib.context().global().notifyCallProblemsChanged();
        }
      });
    }, 0);
  }

  public static boolean isFullScreenIntentDenied (Tdlib tdlib) {
    return get(tdlib) == Type.FULL_SCREEN_INTENT_DENIED;
  }

  private static boolean askFullScreenIntentDenied (Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      NotificationManager notificationManager =
        context.getSystemService(NotificationManager.class);
      return notificationManager != null && !notificationManager.canUseFullScreenIntent();
    }
    return false;
  }

  public static boolean isBackgroundRestricted (Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      ActivityManager activityManager = context.getSystemService(ActivityManager.class);
      return activityManager != null && activityManager.isBackgroundRestricted();
    }
    return false;
  }

  private static @Type int find (Context context) {
    if (askFullScreenIntentDenied(context)) {
      return Type.FULL_SCREEN_INTENT_DENIED;
    }
    if (isBackgroundRestricted(context)) {
      return Type.BACKGROUND_RESTRICTED;
    }
    return Type.NONE;
  }

  private static boolean isOngoingChannelOff (Context context) {
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

  private static boolean isAppChannelHidden (Context context) {
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
      case Type.NOTIFICATIONS_BLOCKED:
        return R.string.CallProblemBlocked;
      case Type.CATEGORY_BLOCKED:
        return R.string.CallProblemCategory;
      case Type.PUSH_UNAVAILABLE:
        return R.string.CallProblemPush;
      case Type.CHANNEL_SILENCED:
        return R.string.CallProblemChannel;
      case Type.APP_CHANNEL_HIDDEN:
        return R.string.CallProblemAppChannel;
      case Type.ONGOING_CHANNEL_OFF:
        return R.string.CallProblemOngoingChannel;
      case Type.DISPLAY_FAILED:
        return R.string.CallProblemFailed;
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
        return R.string.CallProblemChannelAction;
      case Type.APP_CHANNEL_HIDDEN:
        return R.string.CallProblemAppChannelAction;
      case Type.ONGOING_CHANNEL_OFF:
        return R.string.CallProblemOngoingChannelAction;
      case Type.DISPLAY_FAILED:
        return R.string.ShareNotificationError;
      case Type.NOTIFICATIONS_BLOCKED:
      case Type.CATEGORY_BLOCKED:
      case Type.PUSH_UNAVAILABLE:
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
      case Type.DISPLAY_FAILED:
        return R.string.CallProblemFailedGuide;
      case Type.NOTIFICATIONS_BLOCKED:
      case Type.CATEGORY_BLOCKED:
      case Type.PUSH_UNAVAILABLE:
      case Type.NONE:
      default:
        return 0;
    }
  }

  public static @StringRes int getNotificationsGuide (Tdlib tdlib) {
    @TdlibNotificationManager.Status int status = tdlib.notifications().getCallBlockStatus();
    switch (status) {
      case TdlibNotificationManager.Status.MISSING_PERMISSION:
        return R.string.CallProblemPermissionGuide;
      case TdlibNotificationManager.Status.BLOCKED_ALL:
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ?
          R.string.CallProblemBlockedAllGuide :
          R.string.CallProblemBlockedAppGuide;
      case TdlibNotificationManager.Status.BLOCKED_CATEGORY:
        return R.string.CallProblemBlockedCategoryGuide;
      case TdlibNotificationManager.Status.DISABLED_SYNC:
        return R.string.CallProblemSyncGlobalOffGuide;
      case TdlibNotificationManager.Status.DISABLED_APP_SYNC:
        return R.string.CallProblemSyncAppOffGuide;
      case TdlibNotificationManager.Status.PUSH_SERVICE_MISSING:
        return R.string.CallProblemFirebaseUnavailableGuide;
      case TdlibNotificationManager.Status.PUSH_SERVICE_ERROR:
        return R.string.CallProblemFirebaseErrorGuide;
      case TdlibNotificationManager.Status.BACKGROUND_RESTRICTED:
      case TdlibNotificationManager.Status.INTERNAL_ERROR:
      case TdlibNotificationManager.Status.ACCOUNT_NOT_SELECTED:
      case TdlibNotificationManager.Status.NOT_BLOCKED:
        break;
    }
    return 0;
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
