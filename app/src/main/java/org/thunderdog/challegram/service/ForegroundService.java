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
 * File created on 24/03/2019
 */
package org.thunderdog.challegram.service;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.TDLib;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibNotificationManager;
import org.thunderdog.challegram.tool.Intents;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;

public class ForegroundService extends Service {
  private static final String EXTRA_TITLE      = "extra_title";
  private static final String EXTRA_TEXT      = "extra_subtitle";
  private static final String EXTRA_CHANNEL_ID = "extra_channel_id";
  private static final String EXTRA_ICON_RES   = "extra_icon_res";
  private static final String EXTRA_PUSH_ID    = "extra_push_id";
  private static final String EXTRA_ACCOUNT_ID = "extra_account_id";

  private static final String ACTION_START = "start";
  private static final String ACTION_STOP  = "stop";

  private final List<TaskInfo> tasks = new ArrayList<>();
  private CharSequence activeTitle;
  private CharSequence activeText;
  private String activeChannelId;
  private int    activeIconRes;

  @Override
  public void onCreate () { }

  @Override
  public int onStartCommand (Intent intent, int flags, int startId) {
    synchronized (ForegroundService.class) {
      if      (intent != null && ACTION_START.equals(intent.getAction())) handleStart(intent);
      else if (intent != null && ACTION_STOP.equals(intent.getAction()))  handleStop(intent);
      else                                                                throw new IllegalStateException("Action needs to be START or STOP.");

      return START_NOT_STICKY;
    }
  }

  private static class TaskInfo {
    public final CharSequence title, text;
    public final String channelId;
    public final int iconRes;
    public final long pushId;
    public final int accountId;

    public TaskInfo (CharSequence title, CharSequence text, String channelId, int iconRes, long pushId, int accountId) {
      this.title = title;
      this.text = text;
      this.channelId = channelId;
      this.iconRes = iconRes;
      this.pushId = pushId;
      this.accountId = accountId;
    }
  }

  private void handleStart (@NonNull Intent intent) {
    CharSequence title = intent.getCharSequenceExtra(EXTRA_TITLE);
    if (StringUtils.isEmpty(title)) {
      title = intent.getStringExtra(EXTRA_TITLE);
    }
    CharSequence text  = intent.getCharSequenceExtra(EXTRA_TEXT);
    String channelId   = intent.getStringExtra(EXTRA_CHANNEL_ID);
    int    iconRes     = intent.getIntExtra(EXTRA_ICON_RES, R.drawable.baseline_sync_white_24);
    long pushId        = intent.getLongExtra(EXTRA_PUSH_ID, 0);
    int accountId      = intent.getIntExtra(EXTRA_ACCOUNT_ID, TdlibAccount.NO_ID);
    TaskInfo info = new TaskInfo(title, text, channelId, iconRes, pushId, accountId);
    this.tasks.add(info);

    TDLib.Tag.notifications(pushId, accountId, "handleStart() Title: %s  ChannelId: %s  Text: %s", title, channelId, text);

    if (tasks.size() == 1) {
      TDLib.Tag.notifications(pushId, accountId, "First request. Title: %s  ChannelId: %s  Text: %s", title, channelId, text);
      activeTitle     = title;
      activeText      = text;
      activeChannelId = channelId;
      activeIconRes   = iconRes;
    }

    postObligatoryForegroundNotification(activeTitle, activeText, activeChannelId, activeIconRes);
  }

  private void handleStop (@NonNull Intent intent) {
    long pushId      = intent.getLongExtra(EXTRA_PUSH_ID, 0);
    int accountId    = intent.getIntExtra(EXTRA_ACCOUNT_ID, TdlibAccount.NO_ID);
    TDLib.Tag.notifications(pushId, accountId, "handleStop()");

    TaskInfo lastTask;
    if (tasks.isEmpty()) {
      TDLib.Tag.notifications(pushId, accountId, "Bug: handleStop() without handleStart()");
      lastTask = null;
    } else {
      lastTask = tasks.remove(tasks.size() - 1);
    }

    CharSequence title, text;
    String channelId;
    @DrawableRes int iconRes;

    if (!StringUtils.isEmpty(activeChannelId)) {
      title = activeTitle;
      text = activeText;
      channelId = activeChannelId;
      iconRes = activeIconRes;
    } else if (lastTask != null) {
      title = lastTask.title;
      text = lastTask.text;
      channelId = lastTask.channelId;
      iconRes = lastTask.iconRes;
    } else {
      title = Lang.getString(R.string.RetrievingMessages);
      text = null;
      channelId = U.getOtherNotificationChannel();
      iconRes = 0;
    }

    if (StringUtils.isEmpty(channelId))
      throw new IllegalStateException();
    postObligatoryForegroundNotification(title, text, channelId, iconRes);

    if (tasks.isEmpty()) {
      TDLib.Tag.notifications(pushId, accountId, "Last request. Ending foreground service.");
      stopForeground(true);
      stopSelf();

      activeTitle = null;
      activeText = null;
      activeChannelId = null;
      activeIconRes = 0;
    }
  }

  private void postObligatoryForegroundNotification (CharSequence title, CharSequence text, String channelId, @DrawableRes int iconRes) {
    if (!tasks.isEmpty()) {
      TaskInfo info = tasks.get(tasks.size() - 1);
      title = info.title;
      channelId = info.channelId;
      iconRes = info.iconRes;
      text = info.text;
    }
    NotificationCompat.Builder b = new NotificationCompat.Builder(this, channelId)
      .setSmallIcon(iconRes)
      .setContentTitle(title)
      .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), Intents.mutabilityFlags(false)));
    if (!StringUtils.isEmpty(text)) {
      b.setContentText(text);
    }
    U.startForeground(this, TdlibNotificationManager.ID_PENDING_TASK, b.build());
  }

  @Nullable
  @Override
  public IBinder onBind (Intent intent) {
    return null;
  }

  public static boolean startForegroundTask (@NonNull Context context, @NonNull CharSequence task, @Nullable CharSequence text, @NonNull String channelId, @DrawableRes int iconRes, long pushId, int accountId) {
    if (StringUtils.isEmpty(channelId))
      throw new IllegalArgumentException(channelId);
    Intent intent = new Intent(context, ForegroundService.class);
    intent.setAction(ACTION_START);
    intent.putExtra(EXTRA_TITLE, task);
    if (!StringUtils.isEmpty(text))
      intent.putExtra(EXTRA_TEXT, text);
    intent.putExtra(EXTRA_CHANNEL_ID, channelId);
    if (iconRes != 0)
      intent.putExtra(EXTRA_ICON_RES, iconRes);
    intent.putExtra(EXTRA_PUSH_ID, pushId);
    intent.putExtra(EXTRA_ACCOUNT_ID, accountId);
    return startForegroundService(context, intent, pushId, accountId);
  }

  public static boolean stopForegroundTask (@NonNull Context context, long pushId, int accountId) {
    Intent intent = new Intent(context, ForegroundService.class);
    intent.setAction(ACTION_STOP);
    intent.putExtra(EXTRA_PUSH_ID, pushId);
    intent.putExtra(EXTRA_ACCOUNT_ID, accountId);
    return startForegroundService(context, intent, pushId, accountId);
  }

  public static boolean startForegroundService (Context context, Intent intent, long pushId, int accountId) {
    try {
      ContextCompat.startForegroundService(context, intent);
      TDLib.Tag.notifications(pushId, accountId, "startForegroundService(%s) executed successfully (SDK %d)", intent.getAction(), Build.VERSION.SDK_INT);
      return true;
    } catch (Throwable t) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // caution: do not import to avoid crashes on pre-S
        if (t instanceof android.app.ForegroundServiceStartNotAllowedException) {
          TDLib.Tag.notifications(pushId, accountId, "startForegroundService(%s) failed due to system settings restrictions (SDK %d):\n%s", intent.getAction(), Build.VERSION.SDK_INT, Log.toString(t));
          return false;
        }
      }
      TDLib.Tag.notifications(pushId, accountId, "startForegroundService(%s) failed due to error (SDK %d):\n%s", intent.getAction(), Build.VERSION.SDK_INT, Log.toString(t));
      return false;
    }
  }
}
