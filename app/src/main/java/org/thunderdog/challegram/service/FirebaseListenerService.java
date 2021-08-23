package org.thunderdog.challegram.service;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.TDLib;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.BaseThread;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.sync.SyncTask;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.CancellableRunnable;

/**
 * Date: 19/11/2016
 * Author: default
 */

public class FirebaseListenerService extends FirebaseMessagingService {
  @Override
  public void onNewToken (@NonNull String newToken) {
    UI.initApp(getApplicationContext());
    TDLib.Tag.notifications("onNewToken %s, sending to all accounts", newToken);
    TdlibManager.instance().runWithWakeLock(manager -> {
      manager.setDeviceToken(newToken);
    });
  }

  @Override
  public void onDeletedMessages () {
    UI.initApp(getApplicationContext());
    TDLib.Tag.notifications("onDeletedMessages: performing sync for all accounts");
    TdlibManager.makeSync(getApplicationContext(), TdlibAccount.NO_ID, TdlibManager.SYNC_CAUSE_DELETED_MESSAGES, 0, !TdlibManager.inUiThread(), 0);
  }

  @Override
  public void onMessageReceived (@NonNull RemoteMessage remoteMessage) {
    final String payload = makePayload(remoteMessage);
    final long sentTime = remoteMessage.getSentTime();
    UI.initApp(getApplicationContext());
    final long pushId = Settings.instance().newPushId();

    // Trying to find accountId for the push
    TdApi.Object result = Client.execute(new TdApi.GetPushReceiverId(payload));
    final int accountId;
    if (result instanceof TdApi.PushReceiverId) {
      long pushReceiverId = ((TdApi.PushReceiverId) result).id;
      accountId = Settings.instance().findAccountByReceiverId(pushReceiverId);
      if (accountId != TdlibAccount.NO_ID) {
        TDLib.Tag.notifications(pushId, accountId, "Found account for receiverId: %d, payload: %s, sentTime: %d", pushReceiverId, payload, sentTime);
      } else {
        TDLib.Tag.notifications(pushId, accountId, "Couldn't find account for receiverId: %d. Sending to all accounts, payload: %s, sentTime: %d", pushReceiverId, payload, sentTime);
      }
    } else {
      accountId = TdlibAccount.NO_ID;
      if (StringUtils.isEmpty(payload) || payload.equals("{}") || payload.equals("{\"badge\":\"0\"}")) {
        TDLib.Tag.notifications(pushId, accountId, "Empty payload: %s, error: %s. Quitting task.", payload, TD.toErrorString(result));
        return;
      } else {
        TDLib.Tag.notifications(pushId, accountId, "Couldn't fetch receiverId: %s, payload: %s. Sending to all instances.", TD.toErrorString(result), payload);
      }
    }

    TdlibManager.instanceForAccountId(accountId).runWithWakeLock(manager -> processPush(manager, pushId, payload, accountId));
  }

  private boolean hasActiveNetwork () {
    ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo netInfo = cm.getActiveNetworkInfo();
    return netInfo != null && netInfo.isConnected();
  }

  private boolean inIdleMode () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
      return powerManager.isDeviceIdleMode();
    }
    return false;
  }

  private static BaseThread queue;
  private static BaseThread queue () {
    if (queue == null)
      queue = new BaseThread("FcmForegroundServiceTimer");
    return queue;
  }

  private static final int STATE_RUNNING = 0;
  private static final int STATE_VISIBLE = 1;
  private static final int STATE_FINISHED = 2;

  private final Object foregroundLock = new Object();

  private void processPush (final TdlibManager manager, final long pushId, final String payload, final int accountId) {
    // Checking current environment
    final boolean doze = inIdleMode();
    final boolean network = hasActiveNetwork();

    final long startTimeMs = SystemClock.uptimeMillis();

    final AtomicInteger state = new AtomicInteger(STATE_RUNNING);
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<CancellableRunnable> timeout = new AtomicReference<>();

    final boolean inRecoveryMode = manager.inRecoveryMode();
    final boolean shown;

    if (doze || !network || inRecoveryMode) {
      synchronized (foregroundLock) {
        TDLib.Tag.notifications(pushId, accountId, "Starting a foreground task because we may be operating in a constrained environment, doze: %b, network: %b, recovery: %b", doze, network, inRecoveryMode);
        state.set(STATE_VISIBLE);
        showForegroundNotification(manager, inRecoveryMode, pushId, accountId);
        latch.countDown();
        shown = true;
      }
    } else {
      shown = false;
    }

    manager.processPushOrSync(pushId, accountId, payload, () -> {
      TDLib.Tag.notifications(pushId, accountId, "processPushOrSync finished in %dms", SystemClock.uptimeMillis() - startTimeMs);
      synchronized (foregroundLock) {
        if (state.compareAndSet(STATE_VISIBLE, STATE_FINISHED)) {
          TDLib.Tag.notifications(pushId, accountId, "Stopping a foreground task");
          ForegroundService.stopForegroundTask(getApplicationContext(), pushId, accountId);
          SyncTask.cancel(accountId);
        } else {
          int currentState = state.get();
          TDLib.Tag.notifications(pushId, accountId, "Finishing without a foreground task, state: %s", currentState == STATE_RUNNING ? "running" : currentState == STATE_VISIBLE ? "visible" : currentState == STATE_FINISHED ? "finished" : Integer.toString(currentState));
          state.set(STATE_FINISHED);
          latch.countDown();
          if (shown) {
            CancellableRunnable act = timeout.get();
            if (act != null) {
              act.cancel();
              queue().cancel(act);
            }
          }
        }
      }
      TDLib.Tag.notifications(pushId, accountId, "Finished push processing task in %dms", SystemClock.uptimeMillis() - startTimeMs);
    });

    if (!shown) {
      synchronized (foregroundLock) {
        if (state.get() != STATE_FINISHED) {
          CancellableRunnable act = new CancellableRunnable() {
            @Override
            public void act () {
              synchronized (foregroundLock) {
                if (state.compareAndSet(STATE_RUNNING, STATE_VISIBLE)) {
                  TDLib.Tag.notifications(pushId, accountId, "Starting a foreground task because the job is running too long");
                  showForegroundNotification(manager, inRecoveryMode, pushId, accountId);
                  latch.countDown();
                }
              }
            }
          };
          timeout.set(act);
          queue().post(act, TimeUnit.SECONDS.toMillis(7));
        }
      }
    }

    try {
      latch.await();
    } catch (InterruptedException e) {
      TDLib.Tag.notifications(pushId, accountId, "Interrupted.");
    }

    synchronized (foregroundLock) {
      int currentState = state.get();
      TDLib.Tag.notifications(pushId, accountId, "Quitting processPush() with state: %s", currentState == STATE_RUNNING ? "running" : currentState == STATE_VISIBLE ? "visible" : currentState == STATE_FINISHED ? "finished" : Integer.toString(currentState));
      if (currentState != STATE_FINISHED) {
        SyncTask.schedule(pushId, accountId);
      }
    }
  }

  private void showForegroundNotification (TdlibManager manager, boolean inRecovery, long pushId, int accountId) {
    String text;
    if (accountId != TdlibAccount.NO_ID && manager.isMultiUser()) {
      text = Lang.getString(R.string.RetrievingText, manager.account(accountId).getLongName());
    } else {
      text = null;
    }
    ForegroundService.startForegroundTask(getApplicationContext(),
      Lang.getString(inRecovery ? R.string.RetrieveMessagesError : R.string.RetrievingMessages), text,
      U.getOtherNotificationChannel(),
      0,
      pushId,
      accountId
    );
  }

  // Utils

  private static void put (JSONObject obj, String key, Object value) {
    try {
      obj.put(key, wrap(value));
    } catch (JSONException e) {
      Log.e(Log.TAG_FCM, "Cannot set JSON value %s: %s", key, value);
    }
  }
  public static Object wrap (Object o) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      return JSONObject.wrap(o);
    } else {
      if (o == null) {
        return JSONObject.NULL;
      }
      if (o instanceof JSONArray || o instanceof JSONObject) {
        return o;
      }
      if (o.equals(JSONObject.NULL)) {
        return o;
      }
      try {
        if (o instanceof Collection) {
          return new JSONArray((Collection<?>) o);
        } else if (o.getClass().isArray()) {
          return new JSONArray(Arrays.asList((Object[]) o));
        }
        if (o instanceof Map) {
          return new JSONObject((Map<?, ?>) o);
        }
        if (o instanceof Boolean ||
          o instanceof Byte ||
          o instanceof Character ||
          o instanceof Double ||
          o instanceof Float ||
          o instanceof Integer ||
          o instanceof Long ||
          o instanceof Short ||
          o instanceof String) {
          return o;
        }
        if (o.getClass().getPackage().getName().startsWith("java.")) {
          return o.toString();
        }
      } catch (Exception ignored) {
      }
      return null;
    }
  }

  private static String makePayload (final RemoteMessage remoteMessage) {
    JSONObject json = new JSONObject();

    put(json, "google.sent_time", remoteMessage.getSentTime());

    RemoteMessage.Notification notification = remoteMessage.getNotification();
    if (notification != null) {
      put(json, "google.notification.sound", notification.getSound());
    } else {
      Intent intent = remoteMessage.toIntent();
      Bundle extras = intent.getExtras();
      if (extras != null) {
        if (extras.containsKey("gcm.n.sound2")) {
          put(json, "google.notification.sound", extras.getString("gcm.n.sound2"));
        } else if (extras.containsKey("gcm.n.sound")) {
          put(json, "google.notification.sound", extras.getString("gcm.n.sound"));
        }
      }
    }

    Map<String, String> data = remoteMessage.getData();
    if (data != null) {
      for (Map.Entry<String, String> entry : data.entrySet()) {
        put(json, entry.getKey(), entry.getValue());
      }
    }

    return json.toString();
  }
}
