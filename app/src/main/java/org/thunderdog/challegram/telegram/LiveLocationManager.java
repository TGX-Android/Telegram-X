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
 * File created on 03/03/2018
 */
package org.thunderdog.challegram.telegram;

import android.content.Intent;
import android.location.Location;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.helper.LocationHelper;
import org.thunderdog.challegram.service.LiveLocationService;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.reference.ReferenceList;
import me.vkryl.td.ChatId;

public class LiveLocationManager implements LocationHelper.LocationChangeListener, UI.StateListener, ReferenceList.FullnessListener {
  private static class UiHandler extends Handler {
    private final LiveLocationManager context;

    public UiHandler (final LiveLocationManager context) {
      super(Looper.getMainLooper());
      this.context = context;
    }

    @Override
    public void handleMessage (Message msg) {
      context.handleUiMessage(msg);
    }
  }

  public interface UserLocationChangeListener {
    void onLiveLocationBroadcast (@Nullable TdApi.Location location, int heading);
  }

  public interface OutputDelegate extends UserLocationChangeListener {
    void onLiveLocationDataRequest (ArrayList<Tdlib> outTdlibs, ArrayList<ArrayList<TdApi.Message>> outMessages);
  }

  public interface Listener extends UserLocationChangeListener {
    void onLiveLocationsLoaded (ArrayList<Tdlib> tdlibs, ArrayList<ArrayList<TdApi.Message>> messages);
    void onLiveLocationsListChanged (Tdlib tdlib, @Nullable ArrayList<TdApi.Message> messages);
    void onLiveLocationMessageEdited (Tdlib tdlib, TdApi.Message message);
    void onLiveLocationErrorState (boolean inErrorState);
  }

  private static final long LOCATION_ACQUIRE_TIME = TimeUnit.SECONDS.toMillis(10);
  private static final long LOCATION_ACQUIRE_TIME_RETRY = TimeUnit.SECONDS.toMillis(20);
  private static final long BACKGROUND_UPDATE_TIME = TimeUnit.SECONDS.toMillis(60);
  private static final long FOREGROUND_UPDATE_TIME = TimeUnit.SECONDS.toMillis(10);
  private static final long BACKGROUND_RETRY_DELAY = TimeUnit.SECONDS.toMillis(10);
  private static final long FOREGROUND_RETRY_DELAY = TimeUnit.SECONDS.toMillis(2);

  // private final TdlibManager context;
  private final UiHandler handler;
  private final LocationHelper helper;
  private boolean isResumed;

  private boolean isActive;
  private final ReferenceList<OutputDelegate> delegates = new ReferenceList<>(false, true, this);
  private final ReferenceList<Listener> listeners = new ReferenceList<>(false);
  private final ReferenceList<UserLocationChangeListener> locationListeners = new ReferenceList<>(false);

  private TdApi.Location location;
  private int heading;
  private boolean hasError;

  public LiveLocationManager (TdlibManager context) {
    // this.context = context;
    this.handler = new UiHandler(this);
    this.helper = new LocationHelper(UI.getAppContext(), this, false, true);
    this.isResumed = UI.getUiState() == UI.STATE_RESUMED;
    UI.addStateListener(this);
  }

  public boolean hasResolvableError () {
    synchronized (this) {
      return hasError && isActive;
    }
  }

  public void resolveError (BaseActivity activity) {
    synchronized (this) {
      if (hasError && isActive) {
        performWorker(activity);
      }
    }
  }

  private void setHasError (boolean hasError) {
    if (this.hasError != hasError) {
      this.hasError = hasError;
      handler.sendMessage(Message.obtain(handler, ACTION_DISPATCH_ERROR_STATE, hasError ? 1 : 0, 0));
    }
  }

  public String buildSubtext (Tdlib tdlib, ArrayList<TdApi.Message> messages, long specificChatId, boolean isTitle, TdApi.Location myLocation) {
    if (tdlib == null) {
      return null;
    }
    String subtext = null;
    if (specificChatId != 0) {
      TdApi.Message outputMessage = tdlib.cache().findOutputLiveLocationMessage(specificChatId);
      if (outputMessage == null) {
        return null;
      }

      if (!isTitle && messages.size() == 2) {
        TdApi.Message msg = messages.get(0);
        if (msg.isOutgoing) {
          msg = messages.get(1);
        }
        TdApi.Location l1 = myLocation != null ? myLocation : ((TdApi.MessageLocation) outputMessage.content).location;
        TdApi.Location l2 = ((TdApi.MessageLocation) msg.content).location;
        float distance = U.distanceBetween(l1.latitude, l1.longitude, l2.latitude, l2.longitude);
        return Lang.lowercase(Lang.shortDistanceToPerson(distance));
      }

      boolean userChat = ChatId.isUserChat(specificChatId);

      if (userChat) {
        if (isTitle) {
          return "- " + Lang.getString(R.string.FromYou);
        }
      } else {
        if (isTitle) {
          return "- " + Lang.getString(R.string.FromYou);
        } else if (messages != null && !messages.isEmpty()) {
          List<String> names = new ArrayList<>();
          names.add(Lang.getString(R.string.FromYou));
          for (TdApi.Message msg : messages) {
            if (!msg.isOutgoing) {
              names.add(tdlib.senderName(msg, true, false));
              if (names.size() >= 3) {
                break;
              }
            }
          }
          if (names.size() <= 1) {
            return null;
          }
          if (names.size() >= 3) {
            return "- " + Lang.getString(R.string.SharingYouAndOtherName, Lang.plural(R.string.xMembers, names.size() - 1));
          }
          return "- " + TextUtils.join(Lang.getConcatSeparator(), names);
        } else {
          return null;
        }
      }
      return null;
    } else if (messages == null || messages.isEmpty()) {
      return null;
    } else if (messages.size() == 1) {
      TdApi.Message singleMessage = messages.get(0);
      if (ChatId.isUserChat(singleMessage.chatId)) {
        TdApi.User user = tdlib.chatUser(singleMessage.chatId);
        if (user != null) {
          subtext = Lang.getString(R.string.SharingWithX, user.firstName);
        }
      } else {
        subtext = Lang.getString(R.string.AttachLiveLocationIsSharing, tdlib.chatTitle(singleMessage.chatId));
      }
    } else {
      subtext = Lang.plural(R.string.SharingWithXChats, messages.size());
    }
    return subtext;
  }

  public String buildSubtext (ArrayList<Tdlib> tdlibs, ArrayList<ArrayList<TdApi.Message>> messages, long specificChatId) {
    int sharingChatsCount = 0;
    for (ArrayList<TdApi.Message> chunk : messages) {
      sharingChatsCount += chunk.size();
    }

    String subtext = null;
    if (tdlibs.size() == 1) {
      subtext = buildSubtext(tdlibs.get(0), messages.get(0), specificChatId, true, null);
    }

    if (subtext == null) {
      subtext = Lang.plural(R.string.SharingWithXChats, sharingChatsCount);
    }
    return subtext;
  }

  public void addDelegate (OutputDelegate delegate) {
    delegates.add(delegate);
  }

  public void removeDelegate (OutputDelegate delegate) {
    delegates.remove(delegate);
  }

  public static class LocationData {
    public final TdApi.Location location;
    public final int heading;

    public LocationData (TdApi.Location location, int heading) {
      this.location = location;
      this.heading = heading;
    }
  }

  public @Nullable LocationData addLocationListener (UserLocationChangeListener listener) {
    locationListeners.add(listener);
    TdApi.Location location;
    int heading;
    synchronized (this) {
      if (isActive) {
        location = this.location;
        heading = this.heading;
      } else {
        location = null;
        heading = 0;
      }
    }
    return location != null ? new LocationData(location, heading) : null;
  }

  public void removeLocationListener (UserLocationChangeListener listener) {
    locationListeners.remove(listener);
  }

  public void finishBroadcast () {
    synchronized (this) {
      lastBroadcastTime = 0;
      handler.sendMessage(Message.obtain(handler, ACTION_BROADCAST_LOCATION, 0, isActive ? 1 : 0, null));
    }
  }

  private void dispatchLocation (TdApi.Location location, int heading) {
    handler.sendMessage(Message.obtain(handler, ACTION_DISPATCH_LOCATION_CHANGED, heading, 0, location));
  }

  private CancellationSignal serviceLaunchCancellationSignal;

  @Override
  public void onFullnessStateChanged (ReferenceList<?> list, boolean isFull) {
    synchronized (this) {
      if (this.isActive != isFull) {
        this.isActive = isFull;
        if (serviceLaunchCancellationSignal != null) {
          serviceLaunchCancellationSignal.cancel();
          serviceLaunchCancellationSignal = null;
        }
        Intent serviceIntent = new Intent(UI.getAppContext(), LiveLocationService.class);
        if (isFull) {
          serviceLaunchCancellationSignal = new CancellationSignal();
          UI.startService(serviceIntent, true, true, serviceLaunchCancellationSignal);
          if (location == null) {
            performWorker(null);
          } else {
            rescheduleLocationWorker();
          }
          dispatchLocation(location, heading);
        } else {
          UI.getAppContext().stopService(serviceIntent);
          cancelLocationWorker();
          dispatchLocation(null, 0);
        }
      }
    }
  }

  private static final long UPDATE_THRESHOLD = TimeUnit.SECONDS.toMillis(15);
  private long lastBroadcastTime;

  @Override
  public void onLocationResult (LocationHelper context, @NonNull String arg, Location location) {
    synchronized (this) {
      double latitude = location.getLatitude();
      double longitude = location.getLongitude();
      float accuracy = location.getAccuracy();
      int heading = U.getHeading(location);
      long time = SystemClock.uptimeMillis();
      if (this.location == null || this.hasError ||
          this.location.latitude != latitude || this.location.longitude != longitude || this.location.horizontalAccuracy != accuracy ||
          this.heading != heading ||
          lastBroadcastTime == 0 || time - lastBroadcastTime >= UPDATE_THRESHOLD) {
        this.location = new TdApi.Location(latitude, longitude, accuracy);
        this.heading = heading;
        this.lastBroadcastTime = time;
        setHasError(false);
        Log.v("Broadcasting live location");
        handler.sendMessage(Message.obtain(handler, ACTION_BROADCAST_LOCATION, heading, isActive ? 1 : 0, this.location));
      } else {
        Log.v("Ignoring live location, because it is not changed");
      }
    }
  }

  @Override
  public void onLocationRequestFailed (LocationHelper context, int errorCode, @NonNull String arg, Location savedLocation) {
    synchronized (this) {
      setHasError(true);
      rescheduleLocationWorker();
    }
  }

  // Public

  public void addListener (Listener listener) {
    listeners.add(listener);
    ArrayList<Tdlib> tdlibs;
    ArrayList<ArrayList<TdApi.Message>> messages;
    synchronized (this) {
      if (!isActive) {
        return;
      }
      tdlibs = new ArrayList<>();
      messages = new ArrayList<>();
    }
    for (OutputDelegate delegate : delegates) {
      delegate.onLiveLocationDataRequest(tdlibs, messages);
    }
    listener.onLiveLocationsLoaded(tdlibs, messages);
  }

  public void removeListener (Listener listener) {
    listeners.remove(listener);
  }

  void notifyOutputListChanged (Tdlib tdlib, @Nullable ArrayList<TdApi.Message> messages) {
    if (messages != null) {
      handler.sendMessage(Message.obtain(handler, ACTION_DISPATCH_LIST_CHANGED, new Object[] {tdlib, messages}));
    } else {
      handler.sendMessage(Message.obtain(handler, ACTION_DISPATCH_LIST_CLEARED, tdlib));
    }
  }

  void notifyOutputMessageEdited (Tdlib tdlib, TdApi.Message message) {
    handler.sendMessage(Message.obtain(handler, ACTION_DISPATCH_MESSAGE_CHANGED, new Object[] {tdlib, message}));
  }

  @Override
  public void onUiStateChanged (int newState) {
    synchronized (this) {
      boolean isResumed = newState == UI.STATE_RESUMED;
      if (this.isResumed != isResumed) {
        this.isResumed = isResumed;
        rescheduleLocationWorker(); // changes timeout
      }
    }
  }

  // UI handler

  private static final int ACTION_PERFORM_WORKER = 0;
  private static final int ACTION_BROADCAST_LOCATION = 1;
  private static final int ACTION_DISPATCH_LIST_CHANGED = 2;
  private static final int ACTION_DISPATCH_LIST_CLEARED = 3;
  private static final int ACTION_DISPATCH_LOCATION_CHANGED = 4;
  private static final int ACTION_DISPATCH_MESSAGE_CHANGED = 5;
  private static final int ACTION_DISPATCH_ERROR_STATE = 6;
  private static final int ACTION_REQUEST_LOCATION_UPDATE = 7;

  private void handleUiMessage (Message msg) {
    switch (msg.what) {
      case ACTION_PERFORM_WORKER: {
        synchronized (this) {
          workerScheduled = false;
          performWorker(null);
        }
        break;
      }
      case ACTION_BROADCAST_LOCATION: {
        TdApi.Location location = (TdApi.Location) msg.obj;
        int heading = msg.arg1;
        boolean wasActive = msg.arg2 == 1;
        for (OutputDelegate delegate : delegates) {
          delegate.onLiveLocationBroadcast(location, heading);
        }
        for (Listener listener : listeners) {
          listener.onLiveLocationBroadcast(location, heading);
        }
        if (wasActive) {
          for (UserLocationChangeListener listener : locationListeners) {
            listener.onLiveLocationBroadcast(location, heading);
          }
        }
        break;
      }
      case ACTION_DISPATCH_LIST_CHANGED: {
        Object[] data = (Object[]) msg.obj;
        for (Listener listener : listeners) {
          //noinspection unchecked
          listener.onLiveLocationsListChanged((Tdlib) data[0], (ArrayList<TdApi.Message>) data[1]);
        }
        data[0] = null;
        data[1] = null;
        break;
      }
      case ACTION_DISPATCH_LIST_CLEARED: {
        for (Listener listener : listeners) {
          listener.onLiveLocationsListChanged((Tdlib) msg.obj, null);
        }
        break;
      }
      case ACTION_DISPATCH_LOCATION_CHANGED: {
        TdApi.Location location = (TdApi.Location) msg.obj;
        int heading = msg.arg1;
        for (UserLocationChangeListener listener : locationListeners) {
          listener.onLiveLocationBroadcast(location, heading);
        }
        break;
      }
      case ACTION_DISPATCH_MESSAGE_CHANGED: {
        Object[] data = (Object[]) msg.obj;
        for (Listener listener : listeners) {
          listener.onLiveLocationMessageEdited((Tdlib) data[0], (TdApi.Message) data[1]);
        }
        data[0] = null;
        data[1] = null;
        break;
      }
      case ACTION_DISPATCH_ERROR_STATE: {
        boolean hasError = msg.arg1 == 1;
        for (Listener listener : listeners) {
          listener.onLiveLocationErrorState(hasError);
        }
        break;
      }
      case ACTION_REQUEST_LOCATION_UPDATE: {
        synchronized (this) {
          cancelLocationWorker();
          performWorker(null);
        }
        break;
      }
    }
  }

  // Internal (no synchronization)

  private boolean workerScheduled;
  private long workerExecuteTime;

  private void rescheduleLocationWorker () {
    cancelLocationWorker();
    if (!isActive)
      return;
    workerScheduled = true;
    long delay = (hasError ? (isResumed ? FOREGROUND_RETRY_DELAY : BACKGROUND_RETRY_DELAY) : isResumed ? FOREGROUND_UPDATE_TIME : BACKGROUND_UPDATE_TIME);
    if (workerExecuteTime != 0) {
      delay = (workerExecuteTime + delay) - SystemClock.elapsedRealtime();
    }
    if (delay > 0) {
      Log.v("Scheduling live location worker in %dms", delay);
      handler.sendMessageDelayed(Message.obtain(handler, ACTION_PERFORM_WORKER), delay);
    } else {
      performWorker(null);
    }
  }

  private void cancelLocationWorker () {
    if (workerScheduled) {
      workerScheduled = false;
      handler.removeMessages(ACTION_PERFORM_WORKER);
    }
  }

  private void performWorker (BaseActivity activity) {
    if (!isActive) {
      return;
    }
    Log.v("Performing live location worker");
    cancelLocationWorker();
    workerExecuteTime = SystemClock.elapsedRealtime();
    helper.receiveLocation("", activity, hasError ? LOCATION_ACQUIRE_TIME_RETRY : LOCATION_ACQUIRE_TIME, activity != null);
    rescheduleLocationWorker();
  }

  public void requestUpdate () {
    handler.sendMessage(Message.obtain(handler, ACTION_REQUEST_LOCATION_UPDATE));
  }
}
