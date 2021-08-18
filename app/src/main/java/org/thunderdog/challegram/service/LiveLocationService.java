package org.thunderdog.challegram.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.LiveLocationManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibNotificationManager;
import org.thunderdog.challegram.tool.Intents;

import java.util.ArrayList;

/**
 * Date: 3/5/18
 * Author: default
 */

public class LiveLocationService extends Service implements LiveLocationManager.Listener {
  @Nullable
  @Override
  public IBinder onBind (Intent intent) {
    return null;
  }

  @Override
  public void onLiveLocationBroadcast (@Nullable TdApi.Location location, int heading) { }

  @Override
  public void onLiveLocationMessageEdited (Tdlib tdlib, TdApi.Message message) { }

  private ArrayList<Tdlib> tdlibs;
  private ArrayList<ArrayList<TdApi.Message>> messages;
  private boolean showingNotification;

  @Override
  public int onStartCommand (Intent intent, int flags, int startId) {
    TdlibManager.instance().liveLocation().addListener(this);
    return START_STICKY;
  }

  @Override
  public void onDestroy () {
    super.onDestroy();
    U.stopForeground(this, true, TdlibNotificationManager.ID_LOCATION);
    TdlibManager.instance().liveLocation().removeListener(this);
  }

  private boolean hasError;

  @Override
  public void onLiveLocationErrorState (boolean inErrorState) {
    if (this.hasError != inErrorState) {
      this.hasError = inErrorState;
      updateNotification();
    }
  }

  @Override
  public void onLiveLocationsLoaded (ArrayList<Tdlib> tdlibs, ArrayList<ArrayList<TdApi.Message>> messages) {
    this.tdlibs = tdlibs;
    this.messages = messages;
    updateNotification();
  }

  @Override
  public void onLiveLocationsListChanged (Tdlib tdlib, @Nullable ArrayList<TdApi.Message> messages) {
    if (this.tdlibs == null) {
      return;
    }
    int i = tdlibs.indexOf(tdlib);
    if (messages == null) {
      if (i == -1) {
        return;
      }
      this.tdlibs.remove(i);
      this.messages.remove(i);
    } else if (i != -1) {
      this.messages.set(i, messages);
    } else {
      this.tdlibs.add(tdlib);
      this.messages.add(messages);
    }
    updateNotification();
  }

  private void updateNotification () {
    if (this.tdlibs == null || this.tdlibs.isEmpty()) {
      if (showingNotification) {
        stopForeground(true);
        showingNotification = false;
      }
      return;
    }
    if (!showingNotification) {
      U.startForeground(this, TdlibNotificationManager.ID_LOCATION, buildNotification());
      return;
    }
    try {
      NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      if (manager != null) {
        manager.notify(TdlibNotificationManager.ID_LOCATION, buildNotification());
      }
    } catch (Throwable ignored) { }
  }

  private Notification buildNotification () {
    Notification.Builder b;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      b = new Notification.Builder(this, Intents.newSimpleChannel(Intents.CHANNEL_ID_LOCATION, R.string.AttachLiveLocation));
    } else {
      b = new Notification.Builder(this);
    }

    PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, Intents.valueOfLocation(hasError), PendingIntent.FLAG_UPDATE_CURRENT);
    b.setContentIntent(pendingIntent);

    PendingIntent pauseIntent = PendingIntent.getBroadcast(this, 100, Intents.valueOfLocationReceiver(Intents.ACTION_LOCATION_STOP), PendingIntent.FLAG_UPDATE_CURRENT);

    // PendingIntent pIntent = PendingIntent.getActivity(this, (int) SystemClock.uptimeMillis(), new Intent(this, MainActivity.class), 0);

    b.addAction(R.drawable.baseline_stop_24_white, Lang.getString(R.string.StopLiveLocation), pauseIntent);
    b.setSmallIcon(R.drawable.baseline_location_on_24_white);

    b.setContentTitle(Lang.getString(R.string.AttachLiveLocation));
    String text;
    if (hasError) {
      text = Lang.getString(R.string.LiveLocationError);
    } else {
      text = Lang.getString(R.string.AttachLiveLocation) + " " + TdlibManager.instance().liveLocation().buildSubtext(tdlibs, messages, 0);
    }
    b.setContentText(text);
    b.setOngoing(true);

    return b.build();
  }
}
