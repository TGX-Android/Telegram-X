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
import android.os.Vibrator;
import android.telephony.TelephonyManager;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.receiver.VoIPMediaButtonReceiver;
import org.thunderdog.challegram.telegram.PrivateCallListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibNotificationChannelGroup;
import org.thunderdog.challegram.telegram.TdlibNotificationManager;
import org.thunderdog.challegram.telegram.TdlibNotificationUtils;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;
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

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableBool;
import tgx.td.ChatId;

public class TGCallService extends Service implements
  TdlibCache.CallStateChangeListener,
  AudioManager.OnAudioFocusChangeListener,
  SensorEventListener, UI.StateListener {
  @Override
  public IBinder onBind (Intent intent) {
    return null;
  }

  private @Nullable Tdlib tdlib;
  private @Nullable TdApi.Call call;
  private @Nullable String callChannelId;
  private TdApi.User user;

  private boolean callInitialized;

  @Override
  public int onStartCommand (Intent intent, int flags, int startId) {
    if (Log.isEnabled(Log.TAG_VOIP)) {
      Log.i(Log.TAG_VOIP, "TGCallService.onStartCommand received, intent: %s", intent);
    }
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

      if (Intents.ACTION_DECLINE_CALL.equals(action)) {
        declineIncomingCall();
        return;
      }

      if (Intents.ACTION_END_CALL.equals(action)) {
        hangUp();
        return;
      }

      if (Intents.ACTION_ANSWER_CALL.equals(action)) {
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
    this.audioGainControlEnabled = hasEarpiece() && am != null && !am.isSpeakerphoneOn() && !am.isBluetoothScoOn() && !isHeadsetPlugged;
    this.echoCancellationStrength = isHeadsetPlugged || (hasEarpiece() && am != null && !am.isSpeakerphoneOn() && !am.isBluetoothScoOn() && !isHeadsetPlugged) ? 0 : 1;
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
    UI.initApp(getApplicationContext());
    reference = new WeakReference<>(this);

    soundPoolMap = new SoundPoolMap(AudioManager.STREAM_VOICE_CALL);
    soundPoolMap.prepare(R.raw.voip_connecting, R.raw.voip_ringback, R.raw.voip_fail, R.raw.voip_end, R.raw.voip_busy);

    VoIP.initialize(this);
  }

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

      btAdapter = am.isBluetoothScoAvailableOffCall() ? BluetoothAdapter.getDefaultAdapter() : null;

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
      /*filter.addAction(Intents.ACTION_END_CALL);
      filter.addAction(Intents.ACTION_DECLINE_CALL);
      filter.addAction(Intents.ACTION_ANSWER_CALL);*/
      registerReceiver(receiver, filter);

      am.registerMediaButtonEventReceiver(new ComponentName(this, VoIPMediaButtonReceiver.class));

      if (btAdapter != null && btAdapter.isEnabled()) {
        //noinspection MissingPermission
        int headsetState = btAdapter.getProfileConnectionState(BluetoothProfile.HEADSET);
        updateBluetoothHeadsetState(headsetState == BluetoothProfile.STATE_CONNECTED);
        if (headsetState == BluetoothProfile.STATE_CONNECTED) {
          am.setBluetoothScoOn(true);
        }
        notifyAudioSettingsChanged();
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
    RunnableBool disconnectBt = isBtHeadsetConnected ? (delayed) -> {
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
    am.unregisterMediaButtonEventReceiver(new ComponentName(this, VoIPMediaButtonReceiver.class));
    if (haveAudioFocus) {
      am.abandonAudioFocus(this);
    }
  }

  private void setCall (TdApi.Call call) {
    boolean sameCall = this.call != null && call != null && this.call.id == call.id;
    this.call = call;
    this.callChannelId = sameCall && this.callChannelId != null ? this.callChannelId : this.call != null ? "call_" + this.call.id + "_" + System.currentTimeMillis() : null;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      cleanupChannels((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE));
    }
  }

  @Override
  public void onDestroy () {
    this.isDestroyed = true;
    Log.v(Log.TAG_VOIP, "TGCallService.onDestroy");

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
        tdlib.client().send(new TdApi.SendCallDebugInformation(call.id, lastDebugLog.toString()), tdlib.okHandler());
      }
      if (!sentRating && (((TdApi.CallStateDiscarded) call.state).needRating || BuildConfig.EXPERIMENTAL)) {
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
        PendingIntent.getActivity(TGCallService.this, 0, new Intent(TGCallService.this, VoIPFeedbackActivity.class)
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
  public void onCallStateChanged (int callId, int newState) { }

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
    Log.d(Log.TAG_VOIP, "setAudioMode: %s", mode == CallSettings.SPEAKER_MODE_BLUETOOTH ? "SPEAKER_MODE_BLUETOOTH" : mode == CallSettings.SPEAKER_MODE_NONE ? "SPEAKER_MODE_NONE" : mode == CallSettings.SPEAKER_MODE_SPEAKER_DEFAULT ? "SPEAKER_MODE_SPEAKER_DEFAULT" : Integer.toString(mode));
    lastAudioMode = mode;
    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
    switch (mode) {
      case CallSettings.SPEAKER_MODE_BLUETOOTH: {
        am.setBluetoothScoOn(true);
        am.setSpeakerphoneOn(false);
        break;
      }
      case CallSettings.SPEAKER_MODE_NONE: {
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

  private CallSettings postponedCallSettings;

  @Override
  public void onCallSettingsChanged (int callId, CallSettings settings) {
    this.postponedCallSettings = settings;
    if (tgcalls != null) {
      tgcalls.setMicDisabled(settings != null && settings.isMicMuted());
    }
    setAudioMode(settings != null ? settings.getSpeakerMode() : CallSettings.SPEAKER_MODE_NONE);
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
    if (call != null) {
      tdlib.context().calls().hangUp(tdlib, call.id, false, 0);
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
    boolean isPendingIncoming = call != null && !call.isOutgoing && call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR;
    if (isPendingIncoming) {
      if (newState != UI.State.RESUMED && needShowIncomingNotification) {
        needShowIncomingNotification = false;
        showIncomingNotification();
      } else if (newState == UI.State.RESUMED) {
        needShowIncomingNotification = true;
        cleanupChannels((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE));
        U.stopForeground(this, true, TdlibNotificationManager.ID_INCOMING_CALL_NOTIFICATION);
        incomingNotification = null;
      }
    }
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
      am.setSpeakerphoneOn(false);
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
      cleanupChannels((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE));
      U.stopForeground(this, true, TdlibNotificationManager.ID_ONGOING_CALL_NOTIFICATION, TdlibNotificationManager.ID_INCOMING_CALL_NOTIFICATION);
      incomingNotification = ongoingCallNotification = null;
      return;
    }



    Notification.Builder builder;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager m = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      cleanupChannels(m);
      // final String channelId = "call_" + call.id + "_" + System.currentTimeMillis();
      android.app.NotificationChannel channel = new android.app.NotificationChannel(callChannelId, Lang.getString(R.string.NotificationChannelOutgoingCall), NotificationManager.IMPORTANCE_LOW);
      channel.enableVibration(false);
      channel.enableLights(false);
      channel.setSound(null, null);
      try {
        m.createNotificationChannel(channel);
      } catch (Throwable t) {
        Log.v("Unable to create notification channel for call", new TdlibNotificationChannelGroup.ChannelCreationFailureException(t));
      }
      builder = new Notification.Builder(this, callChannelId);
    } else {
      builder = new Notification.Builder(this);
    }

    builder
      .setContentTitle(Lang.getString(R.string.OutgoingCall))
      .setContentText(TD.getUserName(user))
      .setSmallIcon(CALL_ICON_RES)
      .setContentIntent(PendingIntent.getActivity(UI.getContext(), 0, Intents.valueOfCall(), PendingIntent.FLAG_ONE_SHOT | Intents.mutabilityFlags(false)));
    if (tdlib.context().isMultiUser()) {
      String shortName = tdlib.accountShortName();
      if (shortName != null) {
        builder.setSubText(shortName);
      }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      Intent endIntent = new Intent();
      Intents.secureIntent(endIntent, false);
      endIntent.setAction(Intents.ACTION_END_CALL);
      builder.addAction(R.drawable.round_call_end_24_white, Lang.getString(R.string.VoipEndCall), PendingIntent.getBroadcast(this, 0, endIntent, Intents.mutabilityFlags(false)));
      builder.setPriority(Notification.PRIORITY_MAX);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      builder.setShowWhen(false);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      builder.setColor(tdlib.accountColor());
    }
    Bitmap bitmap = TdlibNotificationUtils.buildLargeIcon(tdlib, user.profilePhoto != null ? user.profilePhoto.small : null, tdlib.cache().userAccentColor(user), TD.getLetters(user), false, true);
    if (bitmap != null) {
      builder.setLargeIcon(bitmap);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      ongoingCallNotification = builder.build();
    } else {
      ongoingCallNotification = builder.getNotification();
    }
    U.startForeground(this, TdlibNotificationManager.ID_ONGOING_CALL_NOTIFICATION, ongoingCallNotification);
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
  private Notification incomingNotification;

  private boolean needShowIncomingNotification;

  private boolean showIncomingNotification () {
    boolean needNotification = call != null && !call.isOutgoing && call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR;

    if (!needNotification && incomingNotification == null) {
      return false;
    }

    needNotification = needNotification && NotificationManagerCompat.from(this).areNotificationsEnabled();

    if (needNotification == (incomingNotification != null)) {
      return needNotification;
    }

    if (UI.getUiState() == UI.State.RESUMED) {
      needShowIncomingNotification = true;
      Log.i("No need to show incoming notification right now, but may in future.");
      return true;
    }

    /*if (!needNotification) {
      stopForeground(true);
      incomingNotification = ongoingCallNotification = null;
      return false;
    }*/

    Log.i("Showing incoming notification");

    Notification.Builder builder;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager m = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      cleanupChannels(m);
      // final String channelId = "call_" + call.id + "_" + System.currentTimeMillis();
      android.app.NotificationChannel channel = new android.app.NotificationChannel(callChannelId, Lang.getString(R.string.NotificationChannelCall), NotificationManager.IMPORTANCE_HIGH);
      channel.enableVibration(false);
      channel.enableLights(false);
      channel.setSound(null, null);
      try {
        m.createNotificationChannel(channel);
      } catch (Throwable t) {
        Log.v("Unable to create notification channel for call", new TdlibNotificationChannelGroup.ChannelCreationFailureException(t));
      }
      builder = new Notification.Builder(this, callChannelId);
    } else {
      builder = new Notification.Builder(this);
    }

    builder
      .setContentTitle(Lang.getString(R.string.CallBrandingIncoming))
      .setContentText(TD.getUserName(user))
      .setSmallIcon(CALL_ICON_RES)
      .setContentIntent(PendingIntent.getActivity(UI.getContext(), 0, Intents.valueOfCall(), PendingIntent.FLAG_ONE_SHOT | Intents.mutabilityFlags(false)));
    if (tdlib.context().isMultiUser()) {
      String shortName = tdlib.accountShortName();
      if (shortName != null) {
        builder.setSubText(shortName);
      }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      Intent endIntent = new Intent();
      Intents.secureIntent(endIntent, false);
      endIntent.setAction(Intents.ACTION_DECLINE_CALL);
      CharSequence endTitle = Lang.getString(R.string.DeclineCall);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        endTitle = new SpannableString(endTitle);
        ((SpannableString) endTitle).setSpan(new ForegroundColorSpan(Theme.getColor(ColorId.circleButtonNegative)), 0, endTitle.length(), 0);
      }
      builder.addAction(R.drawable.round_call_end_24_white, endTitle, PendingIntent.getBroadcast(this, 0, endIntent, PendingIntent.FLAG_ONE_SHOT | Intents.mutabilityFlags(false)));
      Intent answerIntent = new Intent();
      Intents.secureIntent(answerIntent, false);
      answerIntent.setAction(Intents.ACTION_ANSWER_CALL);
      CharSequence answerTitle = Lang.getString(R.string.AnswerCall);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        answerTitle = new SpannableString(answerTitle);
        ((SpannableString) answerTitle).setSpan(new ForegroundColorSpan(Theme.getColor(ColorId.circleButtonPositive)), 0, answerTitle.length(), 0);
      }
      builder.addAction(R.drawable.round_call_24_white, answerTitle, PendingIntent.getBroadcast(this, 0, answerIntent, PendingIntent.FLAG_ONE_SHOT | Intents.mutabilityFlags(false)));
      builder.setPriority(Notification.PRIORITY_MAX);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      builder.setShowWhen(false);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      builder.setColor(tdlib.accountColor());
      builder.setVibrate(new long[0]);
      builder.setCategory(Notification.CATEGORY_CALL);
      builder.setFullScreenIntent(PendingIntent.getActivity(this, PendingIntent.FLAG_ONE_SHOT, Intents.valueOfCall(), Intents.mutabilityFlags(false)), true);
    }
    Bitmap bitmap = user != null ? TdlibNotificationUtils.buildLargeIcon(tdlib, user.profilePhoto != null ? user.profilePhoto.small : null, tdlib.cache().userAccentColor(user), TD.getLetters(user), false, true) : null;
    if (bitmap != null) {
      builder.setLargeIcon(bitmap);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      incomingNotification = builder.build();
    } else {
      incomingNotification = builder.getNotification();
    }
    U.startForeground(this, TdlibNotificationManager.ID_INCOMING_CALL_NOTIFICATION, incomingNotification);
    return true;
  }

  private void startRinging () {
    Log.i(Log.TAG_VOIP, "startRinging");
    TdlibManager.instance().player().pauseWithReason(TGPlayerController.PAUSE_REASON_TELEGRAM_CALL);
    ringtonePlayer = new MediaPlayer();
    ringtonePlayer.setOnPreparedListener(mediaPlayer -> ringtonePlayer.start());
    ringtonePlayer.setLooping(true);
    ringtonePlayer.setAudioStreamType(AudioManager.STREAM_RING);
    try {
      String notificationUri = tdlib.notifications().getCallRingtone(ChatId.fromUserId(user.id));
      ringtonePlayer.setDataSource(this, Uri.parse(notificationUri));
      ringtonePlayer.prepareAsync();
    } catch (Throwable t) {
      Log.e(Log.TAG_VOIP, "Failed to start ringing", t);
      if (ringtonePlayer != null) {
        ringtonePlayer.release();
        ringtonePlayer = null;
      }
    }

    int vibrateMode = tdlib.notifications().getCallVibrateMode(ChatId.fromUserId(user.id));
    if (vibrateMode != TdlibNotificationManager.VIBRATE_MODE_DISABLED) {
      vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
      if (vibrator != null) {
        switch (vibrateMode) {
          case TdlibNotificationManager.VIBRATE_MODE_SHORT:
            vibrator.vibrate(TdlibNotificationManager.VIBRATE_CALL_SHORT_PATTERN, 0);
            break;
          case TdlibNotificationManager.VIBRATE_MODE_LONG:
            vibrator.vibrate(TdlibNotificationManager.VIBRATE_CALL_LONG_PATTERN, 0);
            break;
          default:
            if (tdlib.notifications().needVibrateWhenRinging()) {
              vibrator.vibrate(TdlibNotificationManager.VIBRATE_CALL_LONG_PATTERN, 0);
            }
            break;
        }
      }
    }

    if (!showIncomingNotification()) {
      Log.v(Log.TAG_VOIP, "Starting incall activity for incoming call");
      if (UI.getUiState() != UI.State.RESUMED) {
        bringCallToFront();
      }
    }
  }

  private void bringCallToFront () {
    /*try {
      PendingIntent.getActivity(UI.getContext(), 0, Intents.valueOfCall(), PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT).send();
    } catch (Exception x) {
      Log.e(Log.TAG_VOIP, "Error starting incall activity", x);
    }*/
  }

  private void cleanupChannels (NotificationManager m) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && m != null) {
      List<android.app.NotificationChannel> channels = m.getNotificationChannels();
      for (android.app.NotificationChannel channel : channels) {
        String id = channel.getId();
        if (id.startsWith("call_") && !(callChannelId != null && callChannelId.equals(id))) {
          try {
            m.deleteNotificationChannel(channel.getId());
          } catch (Throwable t) {
            Log.e("Unable to delete notification channel", t);
          }
        }
      }
    }
  }

  private void stopRinging () {
    cleanupChannels((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE));
    U.stopForeground(this, true, TdlibNotificationManager.ID_ONGOING_CALL_NOTIFICATION, TdlibNotificationManager.ID_INCOMING_CALL_NOTIFICATION);
    incomingNotification = ongoingCallNotification = null;
    if (ringtonePlayer != null) {
      ringtonePlayer.stop();
      ringtonePlayer.release();
      ringtonePlayer = null;
    }
    if (vibrator != null) {
      vibrator.cancel();
      vibrator = null;
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

    // not calculated yet, do it now
    try {
      AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
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
      if (isConnected) {
        Log.d(Log.TAG_VOIP, "AudioManager.startBluetoothSco()");
        am.startBluetoothSco();
      } else {
        Log.d(Log.TAG_VOIP, "AudioManager.stopBluetoothSco()");
        am.stopBluetoothSco();
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
    int mode = isBluetoothHeadsetConnected() && am.isBluetoothScoOn() ? CallSettings.SPEAKER_MODE_BLUETOOTH : am.isSpeakerphoneOn() ? CallSettings.SPEAKER_MODE_SPEAKER : CallSettings.SPEAKER_MODE_NONE;
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
      tdlib.client().send(new TdApi.AddNetworkStatistics(new TdApi.NetworkStatisticsEntryCall(new TdApi.NetworkTypeWiFi(), wifiSentDiff, wifiReceivedDiff, durationDiff)), tdlib.okHandler());
    }

    if (mobileSentDiff > 0 || mobileReceivedDiff > 0 || durationDiff > 0) {
      TdApi.NetworkType type = lastNetInfo != null && lastNetInfo.isRoaming() ? new TdApi.NetworkTypeMobileRoaming() : new TdApi.NetworkTypeMobile();
      tdlib.client().send(new TdApi.AddNetworkStatistics(new TdApi.NetworkStatisticsEntryCall(type, mobileSentDiff, mobileReceivedDiff, durationDiff)), tdlib.okHandler());
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
        cleanupChannels((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE));
        U.stopForeground(this, true, TdlibNotificationManager.ID_ONGOING_CALL_NOTIFICATION, TdlibNotificationManager.ID_INCOMING_CALL_NOTIFICATION);
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
