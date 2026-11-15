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
 * File created on 06/09/2017
 */
package org.thunderdog.challegram.service;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.os.Vibrator;
import android.telephony.TelephonyManager;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.receiver.VoIPMediaButtonReceiver;
import org.thunderdog.challegram.telegram.CallManager;
import org.thunderdog.challegram.telegram.PrivateCallListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibNotificationManager;
import org.thunderdog.challegram.telegram.TdlibNotificationUtils;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.CallController;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.unsorted.Passcode;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.SoundPoolMap;
import org.thunderdog.challegram.voip.ConnectionStateListener;
import org.thunderdog.challegram.voip.NetworkStats;
import org.thunderdog.challegram.voip.Socks5Proxy;
import org.thunderdog.challegram.voip.VoIP;
import org.thunderdog.challegram.voip.VoIPInstance;
import org.thunderdog.challegram.voip.annotation.CallNetworkType;
import org.thunderdog.challegram.voip.annotation.CallState;
import org.thunderdog.challegram.voip.gui.CallSettings;
import org.thunderdog.challegram.voip.gui.VoIPFeedbackActivity;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Executors;

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Filter;
import me.vkryl.core.lambda.RunnableBool;
import tgx.td.ChatId;

public class TGCallService extends Service implements
  TdlibCache.CallStateChangeListener,
  AudioManager.OnAudioFocusChangeListener,
  SensorEventListener, UI.StateListener, Passcode.LockListener {
  @Override
  public IBinder onBind (Intent intent) {
    return null;
  }

  private @Nullable Tdlib tdlib;
  private @Nullable TdApi.Call call;
  private TdApi.User user;

  private boolean callInitialized;

  @Override
  public int onStartCommand (Intent intent, int flags, int startId) {
    if (Log.isEnabled(Log.TAG_VOIP)) {
      Log.i(Log.TAG_VOIP, "TGCallService.onStartCommand received, intent: %s", intent);
    }
    startForegroundAtOnce();
    final int oldCallId = callId();
    int accountId, callId;
    if (intent != null) {
      accountId = intent.getIntExtra("account_id", TdlibAccount.NO_ID);
      callId = intent.getIntExtra("call_id", 0);
    } else {
      accountId = TdlibAccount.NO_ID;
      callId = 0;
    }
    if (accountId == TdlibAccount.NO_ID || callId == 0) {
      setCallId(null, 0);
    } else {
      setCallId(TdlibManager.getTdlib(accountId), callId);
    }
    if (call == null || user == null) {
      Log.w(Log.TAG_VOIP, "TGCallService.onStartCommand: failed because call or other party not found, call: %s, user: %s", call, user);
      U.stopForeground(this, true, TdlibNotificationManager.ID_FOREGROUND_CALL_START);
      isForegroundPlaceholderShown = false;
      stopSelf();
      return START_NOT_STICKY;
    }
    if (callInitialized) {
      if (oldCallId != 0 && oldCallId != callId)
        throw new IllegalStateException();
    } else {
      initCall(tdlib, call);
    }
    updateCall(call);
    return START_NOT_STICKY;
  }

  private boolean isForegroundPlaceholderShown;
  private Notification foregroundPlaceholder;

  private void startForegroundAtOnce () {
    // Android 8+ needs startForeground in time even for a call that is gone. Under its own id, as
    // System UI opens a full-screen intent only for a notification that is added, not updated
    if (incomingNotification != null || ongoingCallNotification != null) {
      return;
    }
    Notification.Builder builder;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      builder = new Notification.Builder(this, U.getOtherNotificationChannel());
    } else {
      builder = new Notification.Builder(this);
    }
    builder
      .setContentTitle(Lang.getString(R.string.VoipBranding))
      .setSmallIcon(CALL_ICON_RES)
      .setContentIntent(PendingIntent.getActivity(UI.getContext(),
        Intents.REQUEST_CODE_CALL_SCREEN, Intents.valueOfCallInApp(false),
        PendingIntent.FLAG_ONE_SHOT | Intents.mutabilityFlags(false)));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      builder.setCategory(Notification.CATEGORY_CALL);
    }
    Notification notification;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      notification = builder.build();
    } else {
      notification = builder.getNotification();
    }
    try {
      U.startForeground(this, TdlibNotificationManager.ID_FOREGROUND_CALL_START, notification,
        false);
      foregroundPlaceholder = notification;
      isForegroundPlaceholderShown = true;
    } catch (Throwable t) {
      Log.e(Log.TAG_VOIP, "Unable to start the service of calls in foreground", t);
    }
  }

  public int callId () {
    return call != null ? call.id : 0;
  }

  public int callTdlib () {
    return tdlib != null ? tdlib.id() : TdlibAccount.NO_ID;
  }

  public long getCallDuration () {
    return tgcalls != null ? tgcalls.getCallDuration() : VoIPInstance.DURATION_UNKNOWN;
  }

  private void setCallId (Tdlib tdlib, int callId) {
    if (this.tdlib != tdlib || callId != callId()) {
      if (this.call != null) {
        this.tdlib.cache().unsubscribeFromCallUpdates(call.id, this);
        UI.removeStateListener(this);
      }
      this.tdlib = tdlib;
      setCall(tdlib != null ? tdlib.cache().getCall(callId) : null);
      this.callBarsCount = -1;
      this.user = call != null ? tdlib.cache().user(call.userId) : null;
      if (call != null) {
        tdlib.cache().subscribeToCallUpdates(call.id, this);
        UI.addStateListener(this);
      }
      /*if (controller != null) {
        controller.setCallId(callId);
      }*/
    }
  }

  private SoundPoolMap soundPoolMap;
  private @Nullable VoIPInstance tgcalls;
  private @Nullable PrivateCallListener callListener;
  private PowerManager.WakeLock cpuWakelock;
  private BluetoothAdapter btAdapter;

  private boolean isProximityNear, isHeadsetPlugged;

  private final BroadcastReceiver receiver = new BroadcastReceiver() {
    @Override
    public void onReceive (Context context, Intent intent) {
      final String action = intent.getAction();

      if (ACTION_HEADSET_PLUG.equals(action)) {
        isHeadsetPlugged = intent.getIntExtra("state", 0) == 1;
        if (isHeadsetPlugged && proximityWakelock != null && proximityWakelock.isHeld()) {
          proximityWakelock.release();
        }
        isProximityNear = false;
        updateOutputGainControlState();
        return;
      }

      if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
        updateNetworkType(true);
        return;
      }

      if (BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
        updateBluetoothHeadsetState(intent.getIntExtra(BluetoothProfile.EXTRA_STATE, 0) == BluetoothProfile.STATE_CONNECTED);
        return;
      }

      if (AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED.equals(action)) {
        notifyAudioSettingsChanged();
        return;
      }

      if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
          hangUp();
        }
        return;
      }

      if (Intent.ACTION_SCREEN_OFF.equals(action) || Intent.ACTION_SCREEN_ON.equals(action) ||
        Intent.ACTION_USER_PRESENT.equals(action)) {
        boolean isPhoneLocked = Passcode.instance().isLockedForIncomingCalls() && isPhoneLocked();
        if (incomingNotification != null && incomingNotificationPhoneLocked != isPhoneLocked) {
          showIncomingNotification(true);
        }
        return;
      }

      if (Intents.ACTION_DECLINE_CALL.equals(action)) {
        declineIncomingCall();
        return;
      }

      if (Intents.ACTION_END_CALL.equals(action)) {
        hangUp();
        return;
      }

      if (Intents.ACTION_ANSWER_CALL.equals(action)) {
        if (Passcode.instance().isLockedForIncomingCalls()) {
          // E.g. "Answer" of a notification from before the lock on a watch: it asks to unlock
          if (incomingNotification != null && !incomingNotificationLocked) {
            showIncomingNotification(true);
          }
          return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
          if (incomingNotification != null) {
            U.startForeground(TGCallService.this,
              TdlibNotificationManager.ID_FOREGROUND_INCOMING_CALL_NOTIFICATION,
              incomingNotification, true);
          } else if (isForegroundPlaceholderShown) {
            U.startForeground(TGCallService.this, TdlibNotificationManager.ID_FOREGROUND_CALL_START,
              foregroundPlaceholder, true);
          }
        }
        acceptIncomingCall();
        return;
      }
    }
  };

  public static final String ACTION_HEADSET_PLUG;

  static {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      ACTION_HEADSET_PLUG = AudioManager.ACTION_HEADSET_PLUG;
    } else {
      ACTION_HEADSET_PLUG = Intent.ACTION_HEADSET_PLUG;
    }
  }

  private static volatile WeakReference<TGCallService> reference;

  private boolean audioGainControlEnabled;
  private int echoCancellationStrength;

  public void updateOutputGainControlState () {
    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
    boolean var;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      android.media.AudioDeviceInfo deviceInfo = am.getCommunicationDevice();
      var = deviceInfo == null || deviceInfo.getType() == android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE;
    } else {
      var = hasEarpiece() && am != null && !am.isSpeakerphoneOn() && !am.isBluetoothScoOn() && !isHeadsetPlugged;
    }
    this.audioGainControlEnabled = var;
    this.echoCancellationStrength = isHeadsetPlugged || var ? 0 : 1;
    if (tgcalls != null) {
      tgcalls.setAudioOutputGainControlEnabled(audioGainControlEnabled);
      tgcalls.setEchoCancellationStrength(echoCancellationStrength);
    }
  }

  public boolean compareCall (Tdlib tdlib, int callId) {
    return call != null && callTdlib() == tdlib.id() && callId == call.id;
  }

  @Override
  public void onCreate () {
    super.onCreate();
    AppContext.init(getApplicationContext());
    reference = new WeakReference<>(this);

    soundPoolMap = new SoundPoolMap(AudioManager.STREAM_VOICE_CALL);
    soundPoolMap.prepare(R.raw.voip_connecting, R.raw.voip_ringback, R.raw.voip_fail, R.raw.voip_end, R.raw.voip_busy);

    VoIP.initialize(this);
    Passcode.instance().addLockListener(this);
  }

  @Override
  public void onPasscodeLocked (boolean isLocked) {
    boolean isLockedForCalls = Passcode.instance().isLockedForIncomingCalls();
    if (incomingNotification != null && incomingNotificationLocked != isLockedForCalls) {
      showIncomingNotification(true);
    }
  }

  private Object communicationDeviceChangedListener;

  private void initCall (Tdlib tdlib, TdApi.Call call) {
    if (callInitialized) {
      throw new IllegalStateException();
    }
    Log.v(Log.TAG_VOIP, "TGCallService.onCreate");
    callInitialized = true;
    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
    try {
      if (cpuWakelock == null) {
        cpuWakelock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tgx:voip");
        cpuWakelock.acquire();
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

      } else {
        btAdapter = am.isBluetoothScoAvailableOffCall() ? BluetoothAdapter.getDefaultAdapter() : null;
      }

      IntentFilter filter = new IntentFilter();
      filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        filter.addAction(AudioManager.ACTION_HEADSET_PLUG);
      } else {
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
      }
      if (btAdapter != null) {
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
      }
      filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
      filter.addAction(Intent.ACTION_SCREEN_OFF);
      filter.addAction(Intent.ACTION_SCREEN_ON);
      filter.addAction(Intent.ACTION_USER_PRESENT);
      /*filter.addAction(Intents.ACTION_END_CALL);
      filter.addAction(Intents.ACTION_DECLINE_CALL);
      filter.addAction(Intents.ACTION_ANSWER_CALL);*/
      registerReceiver(receiver, filter);

      am.registerMediaButtonEventReceiver(new ComponentName(this, VoIPMediaButtonReceiver.class));

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (communicationDeviceChangedListener == null) {
          communicationDeviceChangedListener = (AudioManager.OnCommunicationDeviceChangedListener) device -> UI.post(this::notifyAudioSettingsChanged);
        }
        am.addOnCommunicationDeviceChangedListener(Executors.newSingleThreadExecutor(), (AudioManager.OnCommunicationDeviceChangedListener) communicationDeviceChangedListener);
        notifyAudioSettingsChanged();
      } else {
        if (btAdapter != null && btAdapter.isEnabled()) {
          //noinspection MissingPermission
          int headsetState = btAdapter.getProfileConnectionState(BluetoothProfile.HEADSET);
          updateBluetoothHeadsetState(headsetState == BluetoothProfile.STATE_CONNECTED);
          if (headsetState == BluetoothProfile.STATE_CONNECTED) {
            am.setBluetoothScoOn(true);
          }
          notifyAudioSettingsChanged();
        }
      }
    } catch (Throwable t) {
      Log.e(Log.TAG_VOIP, "Error initializing call", t);
    }
  }

  private boolean isDestroyed;
  private static int amChangeCounter;

  private void releaseAudioFocus () {
    if (cpuWakelock == null) {
      return;
    }
    cpuWakelock.release();
    final AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
    boolean isBtHeadsetConnected = this.isBtHeadsetConnected;
    RunnableBool disconnectBt = Build.VERSION.SDK_INT < Build.VERSION_CODES.S && isBtHeadsetConnected ? (delayed) -> {
      am.stopBluetoothSco();
      Log.d(Log.TAG_VOIP, "AudioManager.stopBluetoothSco (in onDestroy), delayed: %b", delayed);
      am.setSpeakerphoneOn(false);
      Log.d(Log.TAG_VOIP, "AudioManager.setSpeakerphoneOn(false) (in onDestroy), delayed: %b", delayed);
    } : null;
    try {
      if (!soundPoolMap.isProbablyPlaying()) {
        if (disconnectBt != null) {
          disconnectBt.runWithBool(false);
        }
        am.setMode(AudioManager.MODE_NORMAL);
        Log.d(Log.TAG_VOIP, "AudioManager.setMode(AudioManager.MODE_NORMAL) (in onDestroy)");
      } else {
        final int amChangeCounterFinal = amChangeCounter;
        UI.post(() -> {
          if (amChangeCounterFinal == amChangeCounter) {
            try {
              if (disconnectBt != null) {
                disconnectBt.runWithBool(true);
              }
              Log.d(Log.TAG_VOIP, "AudioManager.setMode(AudioManager.MODE_NORMAL) (in onDestroy, delayed)");
              am.setMode(AudioManager.MODE_NORMAL);
            } catch (Throwable ignored) { }
          }
        }, 5000);
      }
    } catch (Throwable ignored) { }
    if (haveAudioFocus) {
      am.abandonAudioFocus(this);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      if (communicationDeviceChangedListener != null) {
        am.removeOnCommunicationDeviceChangedListener((AudioManager.OnCommunicationDeviceChangedListener) communicationDeviceChangedListener);
      }
    }
    am.unregisterMediaButtonEventReceiver(new ComponentName(this, VoIPMediaButtonReceiver.class));
    if (haveAudioFocus) {
      am.abandonAudioFocus(this);
    }
  }

  private void setCall (TdApi.Call call) {
    this.call = call;
  }

  @Override
  public void onDestroy () {
    this.isDestroyed = true;
    Log.v(Log.TAG_VOIP, "TGCallService.onDestroy");
    Passcode.instance().removeLockListener(this);
    UI.cancel(microphoneCheck);
    if (call != null) {
      tdlib.cache().unsubscribeFromCallUpdates(call.id, this);
    }
    UI.removeStateListener(this);

    setCall(null);
    updateCurrentState();
    try {
      unregisterReceiver(receiver);
    } catch (Throwable t) {
      Log.w(Log.TAG_VOIP, "Cannot unregister receiver", t);
    }

    if (reference != null && reference.get() == this) {
      reference = null;
    }

    super.onDestroy();

    releaseAudioFocus();

    if (!soundPoolMap.isProbablyPlaying()) {
      soundPoolMap.release();
    }

    setCallId(null, 0);
  }

  // Call state updates

  @Override
  public void onCallUpdated (final TdApi.Call call) {
    if (!isDestroyed) {
      updateCall(call);
    }
  }

  private void updateCall (TdApi.Call call) {
    setCall(call);
    updateCurrentState();
  }

  private boolean sentDebugLog;
  private boolean sentRating;

  private void updateCurrentState () {
    if (call != null && call.state.getConstructor() == TdApi.CallStateDiscarded.CONSTRUCTOR) {
      updateStats();
      if (!sentDebugLog && ((TdApi.CallStateDiscarded) call.state).needDebugInformation && !StringUtils.isEmpty(lastDebugLog)) {
        sentDebugLog = true;
        tdlib.send(new TdApi.SendCallDebugInformation(new TdApi.InputCallDiscarded(call.id), lastDebugLog.toString()), tdlib.typedOkHandler());
      }
      boolean needRating = ((TdApi.CallStateDiscarded) call.state).needRating;
      if (!sentRating && TD.canRateCall(call) && needRating) {
        sentRating = true;
        // TODO new feedback pop-up
        startRatingActivity();
      }
    }
    configureDeviceForCall();
    updateCurrentSound();
    showNotification();
    updateStats();
    checkInitiated();
  }

  private void startRatingActivity () {
    if (tdlib != null && call != null) {
      try {
        PendingIntent.getActivity(TGCallService.this, Intents.REQUEST_CODE_CALL_RATING,
          new Intent(TGCallService.this, VoIPFeedbackActivity.class)
          .setAction("RATE_CALL_" + call.id)
          .putExtra("account_id", tdlib.id())
          .putExtra("call_id", call.id)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP), Intents.mutabilityFlags(false)).send();
      } catch (Throwable t) {
        Log.e(Log.TAG_VOIP, "Error starting rate activity", t);
      }
    }
  }

  @Override
  public void onCallStateChanged (int callId, int newState) {
    if (newState == CallState.ESTABLISHED && call != null && call.id == callId &&
      ongoingCallNotification != null) {
      ongoingCallNotification = null;
      showNotification();
    }
  }

  private int callBarsCount = -1;

  public int getCallBarsCount () {
    return callBarsCount;
  }

  @Override
  public void onCallBarsCountChanged (int callId, int barsCount) {
    if (this.call != null && this.call.id == callId) {
      this.callBarsCount = barsCount;
    }
  }

  private int lastAudioMode;

  private void setAudioMode (int mode) {
    Log.d(Log.TAG_VOIP, "setAudioMode: %s", mode == CallSettings.SPEAKER_MODE_BLUETOOTH ? "SPEAKER_MODE_BLUETOOTH" : mode == CallSettings.SPEAKER_MODE_EARPIECE ? "SPEAKER_MODE_NONE" : mode == CallSettings.SPEAKER_MODE_SPEAKER_DEFAULT ? "SPEAKER_MODE_SPEAKER_DEFAULT" : Integer.toString(mode));
    lastAudioMode = mode;
    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      android.media.AudioDeviceInfo selectedAudioDevice = null;
      Filter<android.media.AudioDeviceInfo> filter = null;
      switch (mode) {
        case CallSettings.SPEAKER_MODE_EARPIECE: {
          filter = device -> switch (device.getType()) {
            case android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE ->
              true;
            default ->
              false;
          };
          break;
        }

        case CallSettings.SPEAKER_MODE_BLUETOOTH: {
          filter = device -> switch (device.getType()) {
            case
              android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
              android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
              android.media.AudioDeviceInfo.TYPE_BLE_HEADSET/*,
              android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER,
              android.media.AudioDeviceInfo.TYPE_BLE_BROADCAST*/ ->
              true;
            default ->
              false;
          };
          break;
        }

        case CallSettings.SPEAKER_MODE_SPEAKER: {
          filter = device -> switch (device.getType()) {
            case
              android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
              android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
              android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER ->
              true;
            default ->
              false;
          };
          break;
        }

        case CallSettings.SPEAKER_MODE_SPEAKER_DEFAULT: {
          if (hasEarpiece()) {
            filter = device -> switch (device.getType()) {
              case
                android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
                android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER ->
                true;
              default ->
                false;
            };
          } else {
            filter = device -> switch (device.getType()) {
              case
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                android.media.AudioDeviceInfo.TYPE_BLE_HEADSET/*,
              android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER,
              android.media.AudioDeviceInfo.TYPE_BLE_BROADCAST*/ ->
                true;
              default ->
                false;
            };
          }
          break;
        }
        default:
          throw new UnsupportedOperationException();
      }
      if (filter != null) {
        List<android.media.AudioDeviceInfo> devices = am.getAvailableCommunicationDevices();
        for (android.media.AudioDeviceInfo device : devices) {
          if (filter.accept(device)) {
            selectedAudioDevice = device;
            break;
          }
        }
      }
      if (selectedAudioDevice != null) {
        am.setCommunicationDevice(selectedAudioDevice);
      } else {
        am.clearCommunicationDevice();
      }
    } else {
      switch (mode) {
        case CallSettings.SPEAKER_MODE_BLUETOOTH: {
          am.setBluetoothScoOn(true);
          am.setSpeakerphoneOn(false);
          break;
        }
        case CallSettings.SPEAKER_MODE_EARPIECE: {
          am.setBluetoothScoOn(false);
          am.setSpeakerphoneOn(false);
          break;
        }
        case CallSettings.SPEAKER_MODE_SPEAKER: {
          am.setBluetoothScoOn(false);
          am.setSpeakerphoneOn(true);
          break;
        }
        case CallSettings.SPEAKER_MODE_SPEAKER_DEFAULT: {
          if (hasEarpiece()) {
            am.setSpeakerphoneOn(true);
          } else {
            am.setBluetoothScoOn(true);
          }
          break;
        }
      }
    }
  }

  private CallSettings postponedCallSettings;

  @Override
  public void onCallSettingsChanged (int callId, CallSettings settings) {
    this.postponedCallSettings = settings;
    if (tgcalls != null) {
      tgcalls.setMicDisabled(settings != null && settings.isMicMuted());
    }
    setAudioMode(settings != null ? settings.getSpeakerMode() : CallSettings.SPEAKER_MODE_EARPIECE);
  }

  // Implementation

  private void acceptIncomingCall () {
    if (call != null) {
      tdlib.context().calls().acceptCall(this, tdlib, call.id);
      if (UI.getUiState() != UI.State.RESUMED) {
        bringCallToFront();
      }
    }
  }

  private void declineIncomingCall () {
    if (call != null && !call.isOutgoing &&
      call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR) {
      tdlib.context().calls().hangUp(tdlib, call.id, false, 0);
    } else {
      cancelIncomingNotification();
    }
  }

  public long getConnectionId () {
    return tgcalls != null ? tgcalls.getConnectionId() : 0;
  }

  private void hangUp () {
    if (call != null) {
      tdlib.context().calls().hangUp(tdlib, call.id, false, getConnectionId());
    }
  }

  @Override
  public void onUiStateChanged (int newState) {
    updateCurrentState();
    if (ongoingCallNotification != null) {
      if (newState == UI.State.RESUMED) {
        // Visible now, so the microphone for the rest of the call: Android 11–13 check it again
        // at a start of the service, Android 12+ also at startForeground
        Intent intent = new Intent(this, TGCallService.class);
        intent.putExtra(Intents.KEY_ACCOUNT_ID, tdlib.id());
        intent.putExtra(Intents.KEY_CALL_ID, call.id);
        startService(intent);
        U.startForeground(this, TdlibNotificationManager.ID_FOREGROUND_ONGOING_CALL_NOTIFICATION,
          ongoingCallNotification, true);
      }
      scheduleMicrophoneCheck(1000);
    }
    updateIncomingNotification();
  }

  public static void checkIncomingNotification () {
    TGCallService service = currentInstance();
    if (service != null) {
      service.updateIncomingNotification();
    }
  }

  private void updateIncomingNotification () {
    boolean isPendingIncoming = call != null && !call.isOutgoing &&
      call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR;
    if (!isPendingIncoming || isNotificationRinging || incomingNotification == null) {
      return;
    }
    boolean isCallScreenShown = isCallScreenShown();
    if (isCallScreenShown == isIncomingNotificationQuiet) {
      return;
    }
    isIncomingNotificationQuiet = isCallScreenShown;
    if (isCallScreenShown) {
      showIncomingNotification(true);
    } else {
      // Added again, as an update doesn't open its full-screen intent
      U.stopForeground(this, true,
        TdlibNotificationManager.ID_FOREGROUND_INCOMING_CALL_NOTIFICATION);
      incomingNotification = null;
      showIncomingNotification();
    }
  }

  private boolean isCallScreenShown () {
    BaseActivity activity = UI.getUiContext();
    if (call == null || activity == null || (activity.getActivityState() != UI.State.RESUMED &&
      !activity.isRequestingMicForCall())) {
      return false;
    }
    if (activity.isPasscodeShowing()) {
      CallManager calls = tdlib.context().calls();
      return Passcode.instance().requireForIncomingCalls() &&
        calls.getCurrentCallAccountId() == tdlib.id() && calls.getCurrentCallId() == call.id;
    }
    ViewController<?> c = activity.navigation().getCurrentStackItem();
    return c instanceof CallController && !c.isDestroyed() && c.tdlibId() == callTdlib() &&
      ((CallController) c).compareUserId(call.userId);
  }

  // Configuration

  private static final int PROXIMITY_SCREEN_OFF_WAKE_LOCK = 32;

  private PowerManager.WakeLock proximityWakelock;
  private boolean haveAudioFocus;
  private boolean isConfigured;

  private void configureDeviceForCall () {
    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);

    if (TD.isActive(call) && !isConfigured) {
      isConfigured = true;

      Log.i(Log.TAG_VOIP, "Configuring device for call...");

      am.setMode(AudioManager.MODE_IN_COMMUNICATION);
      amChangeCounter++;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

      } else {
        am.setSpeakerphoneOn(false);
      }
      am.requestAudioFocus(this, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);
      updateOutputGainControlState();

      SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
      Sensor proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
      try {
        if (proximity != null) {
          proximityWakelock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(PROXIMITY_SCREEN_OFF_WAKE_LOCK, "tgx:voip-proximity");
          sm.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL);
        }
      } catch (Throwable t) {
        Log.e(Log.TAG_VOIP, "Error initializing proximity sensor", t);
      }
    } else if (!TD.isActive(call) && isConfigured) {
      isConfigured = false;

      Log.i(Log.TAG_VOIP, "Unconfiguring device from call...");

      am.setMode(AudioManager.MODE_NORMAL);
      // Audio focus changed in the onDestroy

      SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
      Sensor proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
      if (proximity != null) {
        sm.unregisterListener(this);
      }
      if (proximityWakelock != null && proximityWakelock.isHeld()) {
        proximityWakelock.release();
      }
    }
  }

  @Override
  public void onAudioFocusChange (int focusChange) {
    haveAudioFocus = focusChange == AudioManager.AUDIOFOCUS_GAIN;
    Log.i(Log.TAG_VOIP, "onAudioFocusChange, focusChange: %d, haveAudioFocus: %b", focusChange, haveAudioFocus);
  }

  @Override
  @SuppressWarnings("NewApi")
  public void onSensorChanged (SensorEvent event) {
    if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
      AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
      if (isHeadsetPlugged || am.isSpeakerphoneOn() || (isBtHeadsetConnected && am.isBluetoothScoOn())) {
        return;
      }
      boolean newIsNear = event.values[0] < Math.min(event.sensor.getMaximumRange(), 3);
      if (newIsNear != isProximityNear) {
        if (Log.isEnabled(Log.TAG_VOIP)) {
          Log.v(Log.TAG_VOIP, "Proximity state changed, isNear: %b", newIsNear);
        }
        isProximityNear = newIsNear;
        try {
          if (isProximityNear) {
            proximityWakelock.acquire();
          } else {
            proximityWakelock.release(1); // this is non-public API before L
          }
        } catch (Throwable t) {
          Log.e(Log.TAG_VOIP, "Failed to acquire/release proximity wakelock, isNear: %b", t, newIsNear);
        }
      }
    }
  }

  @Override
  public void onAccuracyChanged (Sensor sensor, int accuracy) {

  }

  // Notification

  private Notification ongoingCallNotification;

  private static final @DrawableRes int CALL_ICON_RES = R.drawable.baseline_phone_24_white;

  private void showNotification () {
    boolean needNotification = call != null && (call.isOutgoing || call.state.getConstructor() == TdApi.CallStateExchangingKeys.CONSTRUCTOR || call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR) && !TD.isFinished(call);

    if (needNotification == (ongoingCallNotification != null)) {
      return;
    }

    if (!needNotification) {
      stopForegroundOfCall();
      incomingNotification = ongoingCallNotification = null;
      return;
    }



    Notification.Builder builder;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      builder = new Notification.Builder(this, U.getOngoingCallsNotificationChannel());
    } else {
      builder = new Notification.Builder(this);
    }

    int titleRes = call.isOutgoing ? R.string.CallOngoingOutgoing : R.string.CallOngoingIncoming;
    boolean isCallStyle = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    String microphoneOff = isMicrophoneOff ? Lang.getString(R.string.VoipMicrophoneOff) : null;
    if (isCallStyle) {
      builder
        .setContentTitle(TD.getUserName(user))
        .setContentText(microphoneOff != null ? microphoneOff : Lang.getString(titleRes));
    } else {
      builder
        .setContentTitle(Lang.getString(titleRes))
        .setContentText(microphoneOff != null ? microphoneOff : TD.getUserName(user));
    }
    builder
      .setSmallIcon(CALL_ICON_RES)
      .setContentIntent(newCallScreenIntent());
    if (tdlib.context().isMultiUser()) {
      String shortName = tdlib.accountShortName();
      if (shortName != null) {
        builder.setSubText(shortName);
      }
    }
    PendingIntent endCall = null;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      Intent endIntent = new Intent();
      Intents.secureIntent(endIntent, false);
      endIntent.setAction(Intents.ACTION_END_CALL);
      endCall = PendingIntent.getBroadcast(this, Intents.REQUEST_CODE_CALL_END, endIntent,
        Intents.mutabilityFlags(false));
      if (!isCallStyle) {
        builder.addAction(R.drawable.round_call_end_24_white, Lang.getString(R.string.VoipEndCall),
          endCall);
      }
      builder.setPriority(Notification.PRIORITY_MAX);
    }
    long duration = getCallDuration();
    boolean isConnected = duration != VoIPInstance.DURATION_UNKNOWN;
    if (isConnected) {
      builder.setWhen(System.currentTimeMillis() - duration);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        builder.setUsesChronometer(true);
      }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      builder.setShowWhen(isConnected);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      builder.setColor(tdlib.accountColor());
    }
    Bitmap bitmap = TdlibNotificationUtils.buildLargeIcon(tdlib, ChatId.fromUserId(user.id),
      user.profilePhoto != null ? user.profilePhoto.small : null,
      tdlib.cache().userAccentColor(user), TD.getLetters(user), false, true);
    if (bitmap != null) {
      builder.setLargeIcon(bitmap);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      android.app.Person caller = TdlibNotificationUtils.newCallPerson(tdlib, user, false, bitmap);
      builder.setStyle(Notification.CallStyle.forOngoingCall(caller, endCall)
        .setIsVideo(call.isVideo));
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      ongoingCallNotification = builder.build();
    } else {
      ongoingCallNotification = builder.getNotification();
    }
    U.startForeground(this, TdlibNotificationManager.ID_FOREGROUND_ONGOING_CALL_NOTIFICATION, ongoingCallNotification, true);
    cancelForegroundPlaceholder();
    cancelIncomingNotification();
    // The capabilities of the process follow the service a moment later
    scheduleMicrophoneCheck(1000);
  }

  private boolean isMicrophoneOff;
  private final Runnable microphoneCheck = this::checkMicrophone;

  private void scheduleMicrophoneCheck (long delayMs) {
    UI.cancel(microphoneCheck);
    UI.post(microphoneCheck, delayMs);
  }

  private void checkMicrophone () {
    // Android 11+ denies the microphone to a call answered in background, as does its toggle of
    // Android 12+ at any time; access lasts 5 s after the app leaves the screen, so every 5 s
    if (ongoingCallNotification == null) {
      return;
    }
    boolean isOff = false;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      android.app.AppOpsManager appOps =
        (android.app.AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
      isOff = appOps != null && appOps.unsafeCheckOpNoThrow(
        android.app.AppOpsManager.OPSTR_RECORD_AUDIO, Process.myUid(), getPackageName()
      ) == android.app.AppOpsManager.MODE_IGNORED;
    }
    if (isOff != isMicrophoneOff) {
      isMicrophoneOff = isOff;
      ongoingCallNotification = null;
      showNotification();
    } else {
      scheduleMicrophoneCheck(5000);
    }
  }

  private void cancelForegroundPlaceholder () {
    if (isForegroundPlaceholderShown) {
      NotificationManager manager =
        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      manager.cancel(TdlibNotificationManager.ID_FOREGROUND_CALL_START);
      isForegroundPlaceholderShown = false;
    }
  }

  private void cancelIncomingNotification () {
    if (canCancelIncomingNotification()) {
      NotificationManager manager =
        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      manager.cancel(TdlibNotificationManager.ID_FOREGROUND_INCOMING_CALL_NOTIFICATION);
    }
  }

  private boolean canCancelIncomingNotification () {
    // Under the same id CallManager may show another call, which rings without its service
    return call != null &&
      !tdlib.context().calls().isAnotherCallShownWithoutService(tdlib, call.id);
  }

  private void stopForegroundOfCall () {
    if (canCancelIncomingNotification()) {
      U.stopForeground(this, true,
        TdlibNotificationManager.ID_FOREGROUND_ONGOING_CALL_NOTIFICATION,
        TdlibNotificationManager.ID_FOREGROUND_INCOMING_CALL_NOTIFICATION);
    } else {
      U.stopForeground(this, true,
        TdlibNotificationManager.ID_FOREGROUND_ONGOING_CALL_NOTIFICATION);
    }
  }

  // Sound

  private boolean isRinging;

  private void setIsRinging (boolean isRinging) {
    if (this.isRinging != isRinging) {
      this.isRinging = isRinging;
      if (isRinging) {
        startRinging();
      } else {
        stopRinging();
      }
    }
  }

  private MediaPlayer ringtonePlayer;
  private Vibrator vibrator;
  private @Nullable String ringChannelId;
  private boolean isAppRinging, isNotificationRinging, isShownByCallManager;
  private Notification incomingNotification;
  private boolean isIncomingNotificationQuiet;
  private boolean incomingNotificationLocked, incomingNotificationPhoneLocked;

  private boolean isPhoneLocked () {
    KeyguardManager keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
    return keyguard != null && keyguard.isKeyguardLocked();
  }

  private boolean showIncomingNotification () {
    return showIncomingNotification(false);
  }

  private boolean showIncomingNotification (boolean replace) {
    boolean needNotification = call != null && !call.isOutgoing && call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR;

    if (!needNotification && incomingNotification == null) {
      return false;
    }
    if (isShownByCallManager) {
      // An update of the notification of CallManager would stop its ringtone, and One UI would
      // show it as a heads-up over the screen of the call
      return true;
    }

    needNotification = needNotification && NotificationManagerCompat.from(this).areNotificationsEnabled();

    boolean isReplacing = replace && needNotification && incomingNotification != null;
    if (!isReplacing && needNotification == (incomingNotification != null)) {
      return needNotification;
    }

    /*if (!needNotification) {
      stopForeground(true);
      incomingNotification = ongoingCallNotification = null;
      return false;
    }*/

    Log.i("Showing incoming notification");

    Notification.Builder builder;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      String channelId;
      if (isIncomingNotificationQuiet) {
        channelId = U.getOngoingCallsNotificationChannel();
      } else if (isNotificationRinging) {
        channelId = ringChannelId;
      } else {
        channelId = U.getIncomingCallsInAppNotificationChannel();
      }
      builder = new Notification.Builder(this, channelId);
      // An update that only alerts once stops the system's insistent ringtone, e.g. on screen off
      builder.setOnlyAlertOnce(!isNotificationRinging);
    } else {
      builder = new Notification.Builder(this);
    }

    // A locked phone asks for its PIN before an action that opens an activity, so there only a
    // tap on the notification opens the call, over the lock screen
    boolean isLocked = Passcode.instance().isLockedForIncomingCalls();
    boolean isPhoneLocked = isLocked && isPhoneLocked();
    incomingNotificationLocked = isLocked;
    incomingNotificationPhoneLocked = isPhoneLocked;
    boolean isCallStyle = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    String lockedText = isPhoneLocked ? Lang.getString(R.string.CallTapToSeeCaller) :
      isLocked ? Lang.getString(R.string.CallUnlockToAnswer) : null;
    if (isCallStyle) {
      builder.setContentText(
        lockedText != null ? lockedText : Lang.getString(R.string.CallBrandingIncoming)
      );
    } else {
      builder
        .setContentTitle(Lang.getString(
          isLocked ? R.string.IncomingCall : R.string.CallBrandingIncoming
        ))
        .setContentText(lockedText != null ? lockedText : TD.getUserName(user));
    }
    builder
      .setSmallIcon(CALL_ICON_RES)
      .setContentIntent(newCallScreenIntent());
    if (tdlib.context().isMultiUser()) {
      String shortName = tdlib.accountShortName();
      if (shortName != null) {
        builder.setSubText(shortName);
      }
    }
    PendingIntent declineAction = null, answerAction = null;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      Intent endIntent = new Intent();
      Intents.secureIntent(endIntent, false);
      endIntent.setAction(Intents.ACTION_DECLINE_CALL);
      if (tdlib != null && call != null) {
        endIntent.putExtra(Intents.KEY_ACCOUNT_ID, tdlib.id());
        endIntent.putExtra(Intents.KEY_CALL_ID, call.id);
      }
      CharSequence endTitle = Lang.getString(R.string.DeclineCall);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        endTitle = new SpannableString(endTitle);
        ((SpannableString) endTitle).setSpan(new ForegroundColorSpan(Theme.getColor(ColorId.circleButtonNegative)), 0, endTitle.length(), 0);
      }
      declineAction = PendingIntent.getBroadcast(this, Intents.REQUEST_CODE_CALL_DECLINE, endIntent,
        PendingIntent.FLAG_ONE_SHOT | Intents.mutabilityFlags(false));
      if (!isCallStyle) {
        builder.addAction(R.drawable.round_call_end_24_white, endTitle, declineAction);
      }
      Intent answerIntent = new Intent();
      Intents.secureIntent(answerIntent, false);
      answerIntent.setAction(Intents.ACTION_ANSWER_CALL);
      CharSequence answerTitle =
        Lang.getString(isLocked ? R.string.CallUnlock : R.string.AnswerCall);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        answerTitle = new SpannableString(answerTitle);
        ((SpannableString) answerTitle).setSpan(new ForegroundColorSpan(Theme.getColor(ColorId.circleButtonPositive)), 0, answerTitle.length(), 0);
      }
      boolean hasMicrophone = ContextCompat.checkSelfPermission(this,
        Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
      if (isLocked) {
        answerAction = newCallScreenIntent();
      } else if (!hasMicrophone) {
        // Only an activity asks for the microphone. Not one-shot: the notification isn't posted
        // again while it rings
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | Intents.mutabilityFlags(false);
        answerAction = PendingIntent.getActivity(UI.getContext(), Intents.REQUEST_CODE_CALL_ANSWER,
          Intents.valueOfAnswerCall(tdlib.id(), call.id), flags);
      } else {
        answerAction = PendingIntent.getBroadcast(this, Intents.REQUEST_CODE_CALL_ANSWER,
          answerIntent, PendingIntent.FLAG_ONE_SHOT | Intents.mutabilityFlags(false));
      }
      if (!isCallStyle && !isPhoneLocked) {
        builder.addAction(R.drawable.round_call_24_white, answerTitle, answerAction);
      }
      builder.setPriority(
        isIncomingNotificationQuiet ? Notification.PRIORITY_LOW : Notification.PRIORITY_MAX
      );
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      builder.setShowWhen(false);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      builder.setColor(tdlib.accountColor());
      builder.setVibrate(new long[0]);
      builder.setCategory(Notification.CATEGORY_CALL);
      if (user != null) {
        TdlibNotificationUtils.addCallPerson(builder, tdlib, user, isLocked);
      }
      if (!isIncomingNotificationQuiet) {
        Intent screen = Intents.valueOfCall(tdlib.id(), call.id);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | Intents.mutabilityFlags(false);
        builder.setFullScreenIntent(PendingIntent.getActivity(this,
          Intents.REQUEST_CODE_CALL_FULL_SCREEN, screen, flags), true);
      }
    }
    Bitmap bitmap = user != null && !isLocked ?
      TdlibNotificationUtils.buildLargeIcon(tdlib, ChatId.fromUserId(user.id),
      user.profilePhoto != null ? user.profilePhoto.small : null,
      tdlib.cache().userAccentColor(user), TD.getLetters(user), false, true) : null;
    if (bitmap != null) {
      builder.setLargeIcon(bitmap);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      // Only this style rings at the volume of calls, others at that of notifications whatever
      // their channel says (restrict_audio_attributes_call of AOSP)
      android.app.Person caller =
        TdlibNotificationUtils.newCallPerson(tdlib, user, isLocked, bitmap);
      // Also its title, for watches that show only the title and the text
      builder
        .setContentTitle(caller.getName())
        .setStyle(Notification.CallStyle.forIncomingCall(caller, declineAction, answerAction)
          .setIsVideo(call != null && call.isVideo));
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      incomingNotification = builder.build();
    } else {
      incomingNotification = builder.getNotification();
    }
    if (isNotificationRinging) {
      incomingNotification.flags |= Notification.FLAG_INSISTENT;
    }
    U.startForeground(this, TdlibNotificationManager.ID_FOREGROUND_INCOMING_CALL_NOTIFICATION, incomingNotification, false);
    cancelForegroundPlaceholder();
    return true;
  }

  private void startRinging () {
    Log.i(Log.TAG_VOIP, "startRinging");
    TdlibManager.instance().player().pauseWithReason(TGPlayerController.PAUSE_REASON_TELEGRAM_CALL);
    isAppRinging = true;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      ringChannelId = tdlib.notifications().ensureCallChannel(ChatId.fromUserId(user.id));
      CallManager calls = tdlib.context().calls();
      if (calls.isShownWithoutService(tdlib, call.id)) {
        isAppRinging = false;
        isShownByCallManager = true;
      } else if (isDoNotDisturbOn()) {
        // Only Android knows whether Do not disturb lets the caller through, e.g. a repeat caller
        isAppRinging = false;
        isNotificationRinging = canRingOnChannel();
      } else if (isPlayerMutedInBackground() && canRingOnChannel()) {
        // Without the channel the app rings itself: Android 17 mutes its sound, not its vibration
        isAppRinging = false;
        isNotificationRinging = true;
      }
    }
    isIncomingNotificationQuiet = !isNotificationRinging && isCallScreenShown();
    if (!showIncomingNotification()) {
      Log.v(Log.TAG_VOIP, "Starting incall activity for incoming call");
      if (UI.getUiState() != UI.State.RESUMED) {
        bringCallToFront();
      }
    }
    if (isAppRinging) {
      startAppRinging();
    }
  }

  private boolean isPlayerMutedInBackground () {
    // Android 17 mutes a service started while no screen of the app is shown: on a phone in use
    // the full-screen intent is only a heads-up, a locked or sleeping one shows the call at once
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN ||
      UI.getUiState() == UI.State.RESUMED || isPhoneLocked()) {
      return false;
    }
    PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
    return power == null || power.isInteractive();
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private boolean canRingOnChannel () {
    return ringChannelId != null && tdlib.notifications().canShowOnCallChannel(ringChannelId);
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private boolean isDoNotDisturbOn () {
    NotificationManager m = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    int filter = m.getCurrentInterruptionFilter();
    return filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
      filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
  }

  private void startAppRinging () {
    long chatId = ChatId.fromUserId(user.id);
    int ringerMode = tdlib.notifications().getRingerMode();
    Uri ringtone = null;
    long[] vibratePattern = null;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ringChannelId != null) {
      NotificationManager m = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      android.app.NotificationChannel channel = m.getNotificationChannel(ringChannelId);
      if (channel != null && channel.getImportance() >= NotificationManager.IMPORTANCE_DEFAULT) {
        Uri sound = channel.getSound();
        ringtone = Uri.EMPTY.equals(sound) ? null : sound;
        if (channel.shouldVibrate()) {
          vibratePattern = channel.getVibrationPattern();
          if (vibratePattern == null) {
            // As AOSP vibrates a channel without a pattern, e.g. one of an earlier version
            vibratePattern = TdlibNotificationManager.getDefaultVibrationPattern();
          }
        } else if (ringtone != null && ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
          vibratePattern = TdlibNotificationManager.getFallbackVibrationPattern();
        }
      }
    } else {
      String sound = tdlib.notifications().getCallRingtone(chatId);
      ringtone = sound.isEmpty() ? null : Uri.parse(sound);
      vibratePattern = tdlib.notifications().getCallVibratePattern(chatId);
    }

    // As a call of the system: no vibration in silent mode, whatever the vibration of calls says
    if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
      vibratePattern = null;
    }
    if (ringtone != null && ringerMode == AudioManager.RINGER_MODE_NORMAL) {
      Uri telegramCall = TdlibNotificationManager.getTelegramCallRingtone();
      playRingtone(ringtone, !ringtone.equals(telegramCall), vibratePattern);
    } else if (vibratePattern != null) {
      startVibration(vibratePattern);
    }
  }

  private void startVibration (long[] pattern) {
    vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
    if (vibrator != null) {
      vibrateAsRingtone(vibrator, pattern);
    }
  }

  @SuppressWarnings("deprecation")
  private static void vibrateAsRingtone (Vibrator vibrator, long[] pattern) {
    // As the vibration of a ringtone: one of an unknown usage takes the intensity of touches,
    // which One UI may have off; the versions before 13 have only the deprecated forms of it
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0),
        android.os.VibrationAttributes.createForUsage(
          android.os.VibrationAttributes.USAGE_RINGTONE));
      return;
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      vibrator.vibrate(pattern, 0);
      return;
    }
    android.media.AudioAttributes attributes = new android.media.AudioAttributes.Builder()
      .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
      .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
      .build();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0), attributes);
    } else {
      vibrator.vibrate(pattern, 0, attributes);
    }
  }

  @SuppressWarnings("deprecation")
  private void playRingtone (Uri ringtone, boolean fallBackToDefault,
    @Nullable long[] vibratePattern) {
    if (vibratePattern != null) {
      startVibration(vibratePattern);
    }
    ringtonePlayer = new MediaPlayer();
    ringtonePlayer.setOnPreparedListener(MediaPlayer::start);
    ringtonePlayer.setLooping(true);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // Unlike STREAM_RING, these attributes keep the player from taking over the vibration
      ringtonePlayer.setAudioAttributes(new android.media.AudioAttributes.Builder()
        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setHapticChannelsMuted(true)
        .build());
    } else {
      ringtonePlayer.setAudioStreamType(AudioManager.STREAM_RING);
    }
    if (fallBackToDefault) {
      ringtonePlayer.setOnErrorListener((mediaPlayer, what, extra) -> {
        if (ringtonePlayer == mediaPlayer) {
          mediaPlayer.release();
          playRingtone(TdlibNotificationManager.getTelegramCallRingtone(), false,
            vibratePattern);
        }
        return true;
      });
    }
    try {
      ringtonePlayer.setDataSource(this, ringtone);
      ringtonePlayer.prepareAsync();
    } catch (Throwable t) {
      Log.e(Log.TAG_VOIP, "Failed to start ringing", t);
      if (ringtonePlayer != null) {
        ringtonePlayer.release();
        ringtonePlayer = null;
      }
      if (fallBackToDefault) {
        playRingtone(TdlibNotificationManager.getTelegramCallRingtone(), false, vibratePattern);
      }
    }
  }

  private PendingIntent newCallScreenIntent () {
    // Extras don't make another pending intent, so an earlier call's are replaced. Not one-shot:
    // a notification that rings isn't posted again once the screen is left
    Intent intent = Intents.valueOfCall(tdlib.id(), call.id);
    int flags = PendingIntent.FLAG_UPDATE_CURRENT | Intents.mutabilityFlags(false);
    return PendingIntent.getActivity(UI.getContext(), Intents.REQUEST_CODE_CALL_SCREEN, intent,
      flags);
  }

  private void bringCallToFront () {
    /*try {
      PendingIntent.getActivity(UI.getContext(), 0, Intents.valueOfCall(), PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT).send();
    } catch (Exception x) {
      Log.e(Log.TAG_VOIP, "Error starting incall activity", x);
    }*/
  }

  public boolean silenceAppRinging () {
    if (!isAppRinging) {
      return false;
    }
    isAppRinging = false;
    if (ringtonePlayer != null) {
      ringtonePlayer.release();
      ringtonePlayer = null;
    }
    if (vibrator != null) {
      vibrator.cancel();
      vibrator = null;
    }
    return true;
  }

  private void stopRinging () {
    stopForegroundOfCall();
    incomingNotification = ongoingCallNotification = null;
    isAppRinging = isNotificationRinging = isShownByCallManager = false;
    if (ringtonePlayer != null) {
      ringtonePlayer.stop();
      ringtonePlayer.release();
      ringtonePlayer = null;
    }
    if (vibrator != null) {
      vibrator.cancel();
      vibrator = null;
    }
    if (ringChannelId != null) {
      ringChannelId = null;
      tdlib.notifications().deleteStaleChannels();
    }
  }

  private void updateCurrentSound () {
    int callSound;

    if (call != null && call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR && !call.isOutgoing) {
      callSound = 0;
      setIsRinging(true);
    } else {
      callSound = call != null ? TD.getCallStateSound(call) : 0;
      setIsRinging(false);
    }
    if (callSound != 0) {
      if (callSound == R.raw.voip_end || callSound == R.raw.voip_fail) {
        soundPoolMap.playUnique(callSound, 1, 1, 0, 0, 1);
      } else if (callSound == R.raw.voip_busy) {
        soundPoolMap.playUnique(callSound, 1, 1, 0, 2, 1);
      } else {
        soundPoolMap.playUnique(callSound, 1, 1, 0, call.state.getConstructor() == TdApi.CallStateExchangingKeys.CONSTRUCTOR ? 0 : -1, 1);
      }
    } else {
      soundPoolMap.stopLastSound();
    }
  }

  // Audio

  public void onMediaButtonEvent (KeyEvent event) {
    // TODO
  }

  public boolean isBluetoothHeadsetConnected () {
    return isBtHeadsetConnected;
  }

  private Boolean mHasEarpiece = null;

  public boolean hasEarpiece () {
    if (((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getPhoneType() != TelephonyManager.PHONE_TYPE_NONE)
      return true;
    if (mHasEarpiece != null) {
      return mHasEarpiece;
    }

    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      for (android.media.AudioDeviceInfo deviceInfo : am.getAvailableCommunicationDevices()) {
        if (deviceInfo.getType() == android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
          mHasEarpiece = true;
          return true;
        }
      }
    }

    // not calculated yet, do it now
    try {
      Method method = AudioManager.class.getMethod("getDevicesForStream", Integer.TYPE);
      Field field = AudioManager.class.getField("DEVICE_OUT_EARPIECE");
      int earpieceFlag = field.getInt(null);
      int bitmaskResult = (int) method.invoke(am, AudioManager.STREAM_VOICE_CALL);

      // check if masked by the earpiece flag
      if ((bitmaskResult & earpieceFlag) == earpieceFlag) {
        mHasEarpiece = Boolean.TRUE;
      } else {
        mHasEarpiece = Boolean.FALSE;
      }
    } catch (Throwable error) {
      Log.e(Log.TAG_VOIP, "Error while checking earpiece! ", error);
      mHasEarpiece = Boolean.TRUE;
    }

    return mHasEarpiece;
  }

  private boolean isBtHeadsetConnected;

  private void updateBluetoothHeadsetState (boolean isConnected) {
    if (this.isBtHeadsetConnected != isConnected) {
      this.isBtHeadsetConnected = isConnected;
      AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

      } else {
        if (isConnected) {
          Log.d(Log.TAG_VOIP, "AudioManager.startBluetoothSco()");
          am.startBluetoothSco();
        } else {
          Log.d(Log.TAG_VOIP, "AudioManager.stopBluetoothSco()");
          am.stopBluetoothSco();
        }
      }
      notifyAudioSettingsChanged();
    }
  }

  private CallSettings getCallSettings () {
    if (call != null) {
      CallSettings settings = tdlib.cache().getCallSettings(call.id);
      if (settings == null) {
        settings = new CallSettings(tdlib, call.id);
      }
      return settings;
    }
    return null;
  }

  private void notifyAudioSettingsChanged () {
    Log.d(Log.TAG_VOIP, "notifyAudioSettingsChanged");

    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
    int mode;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      List<android.media.AudioDeviceInfo> audioDevices = am.getAvailableCommunicationDevices();
      isBtHeadsetConnected = false;
      for (android.media.AudioDeviceInfo audioDevice : audioDevices) {
        switch (audioDevice.getType()) {
          case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
          case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
          case android.media.AudioDeviceInfo.TYPE_BLE_HEADSET:
            isBtHeadsetConnected = true;
            break;
        }
      }
      android.media.AudioDeviceInfo deviceInfo = am.getCommunicationDevice();
      mode = CallSettings.SPEAKER_MODE_EARPIECE;
      if (deviceInfo != null) {
        switch (deviceInfo.getType()) {
          case android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER:
          case android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
          case android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE: {
            mode = CallSettings.SPEAKER_MODE_SPEAKER;
            isHeadsetPlugged = false;
            break;
          }
          case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
          case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
          case android.media.AudioDeviceInfo.TYPE_BLE_HEADSET: {
            mode = CallSettings.SPEAKER_MODE_BLUETOOTH;
            isHeadsetPlugged = true;
            break;
          }
          case android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE: {
            mode = CallSettings.SPEAKER_MODE_EARPIECE;
            isHeadsetPlugged = false;
            break;
          }
        }
      }
    } else {
      mode = isBluetoothHeadsetConnected() && am.isBluetoothScoOn() ? CallSettings.SPEAKER_MODE_BLUETOOTH : am.isSpeakerphoneOn() ? CallSettings.SPEAKER_MODE_SPEAKER : CallSettings.SPEAKER_MODE_EARPIECE;
    }
    if (this.lastAudioMode != mode) {
      CallSettings settings = getCallSettings();
      if (settings != null) {
        this.lastAudioMode = mode;
        settings.setSpeakerMode(mode);
      }
    }
  }

  // Network type

  private NetworkInfo lastNetInfo;
  private @CallNetworkType int lastNetworkType = CallNetworkType.UNKNOWN;

  private void updateNetworkType (boolean dispatchToTgCalls) {
    ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
    NetworkInfo info = cm.getActiveNetworkInfo();
    lastNetInfo = info;
    @CallNetworkType int type = lastNetworkType;
    if (info != null) {
      switch (info.getType()) {
        case ConnectivityManager.TYPE_MOBILE:
          switch (info.getSubtype()) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
              type = CallNetworkType.MOBILE_GPRS;
              break;
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
              type = CallNetworkType.MOBILE_EDGE;
              break;
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
              type = CallNetworkType.MOBILE_3G;
              break;
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
              type = CallNetworkType.MOBILE_HSPA;
              break;
            case TelephonyManager.NETWORK_TYPE_LTE:
              type = CallNetworkType.MOBILE_LTE;
              break;
            default:
              type = CallNetworkType.OTHER_MOBILE;
              break;
          }
          break;
        case ConnectivityManager.TYPE_WIFI:
          type = CallNetworkType.WIFI;
          break;
        case ConnectivityManager.TYPE_ETHERNET:
          type = CallNetworkType.ETHERNET;
          break;
      }
    }
    this.lastNetworkType = type;
    if (dispatchToTgCalls && tgcalls != null) {
      tgcalls.setNetworkType(type);
    }
  }

  private NetworkStats stats = new NetworkStats(), prevStats = new NetworkStats();
  private long prevDuration;

  private void updateStats () {
    if (tgcalls == null) {
      return;
    }
    tgcalls.getNetworkStats(stats);

    long newDuration = getCallDuration();
    if (newDuration == VoIPInstance.DURATION_UNKNOWN) {
      newDuration = 0;
    }

    long wifiSentDiff = stats.bytesSentWifi - prevStats.bytesSentWifi;
    long wifiReceivedDiff = stats.bytesRecvdWifi - prevStats.bytesRecvdWifi;
    long mobileSentDiff = stats.bytesSentMobile - prevStats.bytesSentMobile;
    long mobileReceivedDiff = stats.bytesRecvdMobile - prevStats.bytesRecvdMobile;
    double durationDiff = (double) Math.max(0, newDuration - prevDuration) / 1000d;

    NetworkStats tmp = stats;
    stats = prevStats;
    prevStats = tmp;
    prevDuration = newDuration;

    if (wifiSentDiff > 0 || wifiReceivedDiff > 0 || durationDiff > 0) {
      tdlib.send(new TdApi.AddNetworkStatistics(new TdApi.NetworkStatisticsEntryCall(new TdApi.NetworkTypeWiFi(), wifiSentDiff, wifiReceivedDiff, durationDiff)), tdlib.typedOkHandler());
    }

    if (mobileSentDiff > 0 || mobileReceivedDiff > 0 || durationDiff > 0) {
      TdApi.NetworkType type = lastNetInfo != null && lastNetInfo.isRoaming() ? new TdApi.NetworkTypeMobileRoaming() : new TdApi.NetworkTypeMobile();
      tdlib.send(new TdApi.AddNetworkStatistics(new TdApi.NetworkStatisticsEntryCall(type, mobileSentDiff, mobileReceivedDiff, durationDiff)), tdlib.typedOkHandler());
    }
  }

  // VoIP

  private CharSequence lastDebugLog;

  private void releaseTgCalls (@Nullable Tdlib tdlib, @Nullable TdApi.Call call) {
    if (tgcalls != null) {
      if (call == null) {
        call = tgcalls.getCall();
      }
      if (tdlib == null) {
        tdlib = tgcalls.tdlib();
      }
      lastDebugLog = tgcalls.collectDebugLog();
      tgcalls.performDestroy();
      tgcalls = null;
    }
    if (callListener != null && tdlib != null && call != null) {
      tdlib.listeners().unsubscribeFromCallUpdates(call.id, callListener);
      callListener = null;
    }
    callInitialized = false; // FIXME?
  }

  private boolean isInitiated () {
    return tgcalls != null;
  }

  private void checkInitiated () {
    if (isInitiated() || TD.isFinished(call)) {
      if (TD.isFinished(call)) {
        releaseTgCalls(tdlib, call);
        stopForegroundOfCall();
        incomingNotification = ongoingCallNotification = null;
        stopSelf();
      }
      return;
    }

    if (call == null || call.state.getConstructor() != TdApi.CallStateReady.CONSTRUCTOR || !callInitialized || tdlib == null) {
      return;
    }

    updateOutputGainControlState();
    updateNetworkType(false);
    Socks5Proxy callProxy = null;
    int proxyId = Settings.instance().getEffectiveCallsProxyId();
    if (proxyId != Settings.PROXY_ID_NONE) {
      Settings.Proxy proxy = Settings.instance().getProxyConfig(proxyId);
      if (proxy != null && proxy.proxy != null && proxy.canUseForCalls()) {
        callProxy = new Socks5Proxy(proxy.proxy);
      }
    }
    boolean isMicDisabled = postponedCallSettings != null && postponedCallSettings.isMicMuted();
    boolean forceTcp = Settings.instance().forceTcpInCalls();

    Tdlib tdlib = this.tdlib;
    TdApi.Call call = this.call;
    TdApi.CallStateReady state = (TdApi.CallStateReady) call.state;

    ConnectionStateListener stateListener = new ConnectionStateListener() {
      @Override
      public void onConnectionStateChanged (VoIPInstance context, @CallState int newState) {
        if (newState == CallState.ESTABLISHED) {
          tdlib.dispatchCallStateChanged(call.id, newState);
        } else if (newState == CallState.FAILED) {
          long connectionId = context.getConnectionId();
          tdlib.context().calls().hangUp(tdlib, call.id, true, connectionId);
        }
      }

      @Override
      public void onSignalBarCountChanged (int newCount) {
        tdlib.dispatchCallBarsCount(call.id, newCount);
      }

      @Override
      public void onSignallingDataEmitted (byte[] data) {
        tdlib.client().send(new TdApi.SendCallSignalingData(call.id, data), tdlib.silentHandler());
      }
    };

    VoIPInstance tgcallsTemp;
    try {
      tgcallsTemp = VoIP.instantiateAndConnect(
        tdlib,
        call,
        state,
        stateListener,
        forceTcp,
        callProxy,
        lastNetworkType,
        audioGainControlEnabled,
        echoCancellationStrength,
        isMicDisabled
      );
    } catch (Throwable t) {
      tgcallsTemp = null;
    }
    final VoIPInstance tgcalls = tgcallsTemp;

    if (tgcalls != null) {
      this.callListener = new PrivateCallListener() {
        @Override
        public void onNewCallSignalingDataArrived (int callId, byte[] data) {
          tgcalls.handleIncomingSignalingData(data);
        }
      };
      tdlib.listeners().subscribeToCallUpdates(call.id, callListener);
      this.tgcalls = tgcalls;
    } else {
      hangUp();
    }
  }

  // Destroy

  public void processBroadcast (Context context, Intent intent) {
    receiver.onReceive(context, intent);
  }

  public static TGCallService currentInstance () {
    return reference != null ? reference.get() : null;
  }

  private boolean logViewed;

  public static void markLogViewed () {
    TGCallService service = currentInstance();
    if (service != null) {
      service.logViewed = true;
    }
  }

  @NonNull
  public CharSequence getLibraryNameAndVersion () {
    return tgcalls != null ?
      tgcalls.getLibraryName() + " " + tgcalls.getLibraryVersion() :
      "unknown";
  }

  @NonNull
  public CharSequence getDebugString () {
    if (tgcalls != null) {
      CharSequence log = tgcalls.collectDebugLog();
      if (log != null) {
        return log;
      }
    }
    return "";
  }
}
