/**
 * File created on 21/05/15 at 18:26
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.core;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.SystemClock;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.reference.ReferenceUtils;

public class WatchDog {
  private final TdlibManager context;

  private final List<Reference<Context>> activities;

  private boolean isOnline, isUnavailable;
  private boolean systemDataSaverEnabled;

  /*private boolean hasWifi;
  private boolean hasMobile;
  private boolean isRoaming;*/

  private static final int CONNECTION_TYPE_NONE = -1;
  public static final int CONNECTION_TYPE_MOBILE = 0;
  public static final int CONNECTION_TYPE_WIFI = 1;
  public static final int CONNECTION_TYPE_ROAMING = 2;
  public static final int CONNECTION_TYPE_OTHER = 3;
  private int connectionType = CONNECTION_TYPE_NONE;

  public int getConnectionType () {
    return connectionType;
  }

  WatchDog (TdlibManager context) {
    this.context = context;
    this.activities = new ArrayList<>();
    isOnline = true;
  }

  // Impl

  public boolean isWifi () {
    return connectionType == CONNECTION_TYPE_WIFI;
  }

  public boolean isRoaming () {
    return connectionType == CONNECTION_TYPE_ROAMING;
  }

  public boolean isMobile () {
    return connectionType == CONNECTION_TYPE_MOBILE;
  }

  public boolean isSystemDataSaverEnabled () {
    return systemDataSaverEnabled;
  }

  public void wakeUp () {
    letsHelpDoge();
  }

  private void checkActivities () {
    ReferenceUtils.gcReferenceList(activities);
  }

  public void onActivityCreate (Context context) {
    wakeUp();
    if (ReferenceUtils.addReference(activities, context)) {
      checkActivities();
    }
  }

  public void onActivityDestroy (Context context) {
    if (ReferenceUtils.removeReference(activities, context)) {
      checkActivities();
    }
  }

  public boolean setIsOnline (boolean isOnline) {
    if (this.isOnline == isOnline) {
      return false;
    }
    if (Log.isEnabled(Log.TAG_NETWORK_STATE)) {
      Log.i(Log.TAG_NETWORK_STATE, "setIsOnline: %b -> %b", this.isOnline, isOnline);
    }
    this.isOnline = isOnline;
    checkConnectionType(false);
    return true;
  }

  private int lastReportedConnectionType = Integer.MIN_VALUE;
  private long lastReportedConnectionTimeMs;

  private void checkConnectionType (boolean force) {
    int reportConnectionType;
    boolean isOnline;
    if (Config.FAKE_BACKGROUND_CONNECTION_STATE && inBackground) {
      int connectionType = this.connectionType;
      if (connectionType == CONNECTION_TYPE_NONE) {
        connectionType = CONNECTION_TYPE_OTHER;
      }
      reportConnectionType = connectionType;
      isOnline = true;
    } else {
      isOnline = this.isOnline;
      int connectionType = this.connectionType;
      reportConnectionType = isOnline ? connectionType : CONNECTION_TYPE_NONE;
    }
    if (lastReportedConnectionType != reportConnectionType || force) {
      lastReportedConnectionTimeMs = SystemClock.elapsedRealtime();
      sendTGConnectionType(lastReportedConnectionType = reportConnectionType, isOnline);
    }
  }

  private void onNetworkRouteChanged () {
    if (isOnline) {
      checkConnectionType(true);
    }
  }

  public boolean isOnline () {
    return isOnline;
  }

  public boolean isOffline () {
    return !isOnline;
  }

  public boolean isWaitingForNetwork () {
    return connectionType == CONNECTION_TYPE_NONE;
  }

  public void resendNetworkStateIfNeeded () {
    if (inBackground) {
      if (lastReportedConnectionTimeMs == 0 || (SystemClock.elapsedRealtime() - lastReportedConnectionTimeMs >= 1000)) {
        context.onConnectionAwake();
      }
    }
  }

  public void letsHelpDoge () {
    letsHelpDoge(false);
  }

  private static int getConnectionType (NetworkInfo info) {
    if (info.isRoaming()) {
      return CONNECTION_TYPE_ROAMING;
    }
    switch (info.getType()) {
      case ConnectivityManager.TYPE_WIFI:
      case ConnectivityManager.TYPE_ETHERNET: {
        return CONNECTION_TYPE_WIFI;
      }
      case ConnectivityManager.TYPE_MOBILE:
      case ConnectivityManager.TYPE_BLUETOOTH:
      case ConnectivityManager.TYPE_VPN:
      default: {
        return CONNECTION_TYPE_MOBILE;
      }
    }
  }

  private CancellableRunnable backgroundStateChecker;
  private boolean inBackground = true;

  public void helpDogeIfInBackground () {
    if (inBackground) {
      letsHelpDoge(false);
    }
  }

  public boolean onBackgroundStateChanged (final boolean inBackground) {
    if (this.inBackground != inBackground) {
      this.inBackground = inBackground;

      Log.i(Log.TAG_NETWORK_STATE, "inBackground -> %b", inBackground);

      if (backgroundStateChecker != null) {
        backgroundStateChecker.cancel();
        backgroundStateChecker = null;
      }
      if (!inBackground) {
        backgroundStateChecker = new CancellableRunnable() {
          @Override
          public void act () {
            letsHelpDoge(false);
          }
        };
        backgroundStateChecker.removeOnCancel(UI.getAppHandler());
        UI.post(backgroundStateChecker, 300);
      }
      return true;
    }
    return false;
  }

  private NetworkInfo.State lastRouteState;
  private String lastRouteExtra;
  private int lastRouteType;
  private boolean lastRouteAvailable;
  private long lastNetworkHandle;

  private boolean hasRouteStateChanged (NetworkInfo networkInfo) {
    return lastRouteState != null && (!lastRouteState.equals(networkInfo.getState()) || !StringUtils.equalsOrBothEmpty(lastRouteExtra, networkInfo.getExtraInfo()) || lastRouteAvailable != networkInfo.isAvailable() || lastRouteType != networkInfo.getType());
  }

  private void saveConnectionRouteInfo (NetworkInfo networkInfo) {
    lastRouteState = networkInfo.getState();
    lastRouteExtra = networkInfo.getExtraInfo();
    lastRouteAvailable = networkInfo.isAvailable();
    lastRouteType = networkInfo.getType();
    if (Log.isEnabled(Log.TAG_NETWORK_STATE)) {
      Log.i(Log.TAG_NETWORK_STATE, "saveConnectionRouteInfo: %s", networkInfo);
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private boolean hasRouteChanged (ConnectivityManager manager, Object rawNetwork) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && manager != null && rawNetwork != null) {
      android.net.Network network = (android.net.Network) rawNetwork;

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        return network.getNetworkHandle() != lastNetworkHandle;
      }
    }
    return false;
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private void saveRoute (ConnectivityManager manager, Object rawNetwork) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && manager != null && rawNetwork != null) {
      android.net.Network network = (android.net.Network) rawNetwork;
      if (Log.isEnabled(Log.TAG_NETWORK_STATE)) {
        // try/catch for https://issuetracker.google.com/issues/175055271?pli=1
        try {
          android.net.NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
          Log.i(Log.TAG_NETWORK_STATE, "saveRoute, network: %s, capabilities: %s", network, capabilities);
        } catch (Throwable t) {
          Log.i(Log.TAG_NETWORK_STATE, "Unable to get network capabilities: %s", t, network);
        }
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        lastNetworkHandle = network.getNetworkHandle();
      }
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private static boolean filter (NetworkInfo info) {
    return info.isConnectedOrConnecting() && info.getType() != ConnectivityManager.TYPE_VPN;
  }

  public void letsHelpDoge (boolean dataSaverOnly) {
    ConnectivityManager manager = (ConnectivityManager) UI.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE);

    if (manager == null) {
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      int restrictBackgroundStatus;
      try {
        restrictBackgroundStatus = manager.getRestrictBackgroundStatus();
      } catch (SecurityException e) {
        Log.w("Unable to fetch background network status", e);
        restrictBackgroundStatus = ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED;
      }
      boolean dataSaverEnabled = manager.isActiveNetworkMetered() && restrictBackgroundStatus != ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED;
      setSystemDataSaverEnabled(dataSaverEnabled);
      if (dataSaverOnly) {
        return;
      }
    }

    NetworkInfo activeNetworkInfo = null; // manager.getActiveNetworkInfo();
    Object activeNetwork = null;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      android.net.Network foundActiveNetwork = manager.getActiveNetwork();
      if (foundActiveNetwork != null) {
        activeNetwork = foundActiveNetwork;
        activeNetworkInfo = manager.getNetworkInfo(foundActiveNetwork);
      }
    }/* else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { // commented out because of possible incorrect detection
      android.net.Network[] networks = manager.getAllNetworks();
      for (android.net.Network network : networks) {
        NetworkInfo info = manager.getNetworkInfo(network);
        if (info.isConnectedOrConnecting()) {
          activeNetwork = network;
          activeNetworkInfo = manager.getNetworkInfo(network);
        }
      }
    }*/ else {
      activeNetworkInfo = manager.getActiveNetworkInfo();
    }

    boolean isOnline = false;
    int connectionType = CONNECTION_TYPE_NONE;
    NetworkInfo chosenNetworkInfo = null;
    Object chosenNetwork = null;

    NetworkInfo availableNetworkInfo = null;
    Object availableNetwork = null;

    if (activeNetworkInfo != null && activeNetworkInfo.isAvailable() && activeNetworkInfo.getType() != ConnectivityManager.TYPE_VPN) {
      availableNetworkInfo = activeNetworkInfo;
      availableNetwork = activeNetwork;
    }

    if (activeNetworkInfo != null && filter(activeNetworkInfo)) {
      isOnline = true;
      chosenNetworkInfo = activeNetworkInfo;
      chosenNetwork = activeNetwork;
      connectionType = getConnectionType(activeNetworkInfo);
    } else {
      // Trying to find active network manually
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        android.net.Network[] networks = manager.getAllNetworks();
        for (android.net.Network network : networks) {
          NetworkInfo info = manager.getNetworkInfo(network);
          if (info != null) {
            if (filter(info)) {
              isOnline = true;
              int type = getConnectionType(info);
              chosenNetworkInfo = info;
              chosenNetwork = network;
              if (connectionType == CONNECTION_TYPE_NONE || type != CONNECTION_TYPE_WIFI) {
                connectionType = type;
              }
            } else if (availableNetworkInfo == null && info.isAvailable() && info.getType() != ConnectivityManager.TYPE_VPN) {
              availableNetworkInfo = info;
              availableNetwork = network;
            }
          }
        }
      } else {
        NetworkInfo[] networkInfos = manager.getAllNetworkInfo();
        for (NetworkInfo info : networkInfos) {
          if (info != null) {
            // TODO what happens if we are on VPN on pre-lollipop device?
            if (info.isConnectedOrConnecting()) {
              isOnline = true;
              int type = getConnectionType(info);
              chosenNetworkInfo = info;
              if (connectionType == CONNECTION_TYPE_NONE || type != CONNECTION_TYPE_WIFI) {
                connectionType = type;
              }
            } else if (availableNetworkInfo == null && info.isAvailable()) {
              availableNetworkInfo = info;
            }
          }
        }
      }
    }

    if (Settings.instance().forceDisableNetwork()) {
      chosenNetworkInfo = null;
      chosenNetwork = null;
      isOnline = false;
      connectionType = CONNECTION_TYPE_NONE;
    } else if (chosenNetworkInfo == null && availableNetworkInfo != null) {
      isOnline = true;
      chosenNetworkInfo = availableNetworkInfo;
      chosenNetwork = availableNetwork;
      connectionType = getConnectionType(availableNetworkInfo);
    }

    boolean isUnavailable = isUnavailable(chosenNetworkInfo);
    setUnavailable(isUnavailable);
    isOnline = isOnline && !isUnavailable;

    if (isOnline) {
      final int previousConnectionType = this.connectionType;
      setConnectionType(connectionType);
      if (previousConnectionType != CONNECTION_TYPE_NONE && (previousConnectionType != connectionType || hasRouteStateChanged(chosenNetworkInfo) || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && hasRouteChanged(manager, chosenNetwork)))) {
        onNetworkRouteChanged();
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        saveRoute(manager, chosenNetwork);
      }
      saveConnectionRouteInfo(chosenNetworkInfo);
    }
    if (!setIsOnline(isOnline)) {
      checkConnectionType(false);
    }
  }

  private void setUnavailable (boolean isUnavailable) {
    this.isUnavailable = isUnavailable;
  }

  private static boolean isUnavailable (NetworkInfo info) {
    if (info == null) {
      return true;
    }
    switch (info.getState()) {
      case DISCONNECTED:
      case DISCONNECTING:
      case SUSPENDED:
      // case UNKNOWN:
        return true;
    }
    return false;
  }

  private void setSystemDataSaverEnabled (boolean isEnabled) {
    if (connectionType == CONNECTION_TYPE_NONE) {
      this.systemDataSaverEnabled = isEnabled;
      return;
    }
    if (this.systemDataSaverEnabled != isEnabled) {
      this.systemDataSaverEnabled = isEnabled;
      context.onSystemDataSaverStateChanged(isEnabled);
    }
  }

  private void sendTGConnectionType (int type, boolean isOnline) {
    Log.w("sendTGConnectionType type:%d isOnline:%b inBackground:%b this.isOnline:%b", type, isOnline, inBackground, this.isOnline);
    TdApi.NetworkType networkType;
    if (isOnline) {
      switch (type) {
        case CONNECTION_TYPE_ROAMING: {
          networkType = new TdApi.NetworkTypeMobileRoaming();
          break;
        }
        case CONNECTION_TYPE_WIFI: {
          networkType = new TdApi.NetworkTypeWiFi();
          break;
        }
        case CONNECTION_TYPE_MOBILE: {
          networkType = new TdApi.NetworkTypeMobile();
          break;
        }
        default: {
          networkType = new TdApi.NetworkTypeOther();
          break;
        }
      }
    } else {
      networkType = new TdApi.NetworkTypeNone();
    }
    if (Log.isEnabled(Log.TAG_NETWORK_STATE)) {
      Log.i(Log.TAG_NETWORK_STATE, "setConnectionType, networkType: %s", networkType.getClass().getName());
    }
    context.setNetworkType(networkType);
  }

  public void checkNetworkAvailability () {
    if (connectionType == CONNECTION_TYPE_NONE) {
      letsHelpDoge();
    }
  }

  private void setConnectionType (int connectionType) {
    if (this.connectionType != connectionType) {
      boolean online = isOnline || (Config.FAKE_BACKGROUND_CONNECTION_STATE && inBackground);
      int reportType = online ? connectionType : CONNECTION_TYPE_NONE;
      if (online && reportType == CONNECTION_TYPE_NONE && inBackground) {
        reportType = CONNECTION_TYPE_OTHER;
      }
      lastReportedConnectionTimeMs = SystemClock.elapsedRealtime();
      sendTGConnectionType(lastReportedConnectionType = reportType, online);
    }
    if (this.connectionType == CONNECTION_TYPE_NONE) {
      this.connectionType = connectionType;
      return;
    }
    if (this.connectionType != connectionType) {
      int oldConnectionType = this.connectionType;
      this.connectionType = connectionType;
      context.onConnectionTypeChanged(oldConnectionType, connectionType);
    }
  }

  /*public synchronized void setDogeState (int state) {
    if (this.dogState != state) {
      int oldState = dogState;
      this.dogState = state;
      if (Config.USE_CUSTOM_CRASH_MANAGER) {
        if (state == STATE_CONNECTED) {
          CrashManager.instance().check();
        }
      }
      if (oldState == STATE_UPDATING || state == STATE_UPDATING) {
        TGNotificationManager.instance().onWatchDogUpdatingStateChanged(state == STATE_UPDATING);
      }
      if (isOnline && dogState == STATE_CONNECTED) {
        onSynchronized();
      }
    }
  }

  public int getDogeState () {
    return dogState;
  }

  public String translateDogeState () {
    switch (dogState) {
      case STATE_CONNECTED: {
        return UI.getString(R.string.Messages);
      }
      default: {
        return UI.getString(NetworkStatusBarView.getStringForState(dogState));
      }
    }
  }*/
}
