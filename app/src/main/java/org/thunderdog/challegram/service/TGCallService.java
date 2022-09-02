/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
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
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
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
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.receiver.VoIPMediaButtonReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibNotificationManager;
import org.thunderdog.challegram.telegram.TdlibNotificationUtils;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.SoundPoolMap;
import org.thunderdog.challegram.voip.VoIPController;
import org.thunderdog.challegram.voip.gui.CallSettings;
import org.thunderdog.challegram.voip.gui.VoIPFeedbackActivity;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import me.vkryl.core.StringUtils;
import me.vkryl.td.ChatId;

public class TGCallService extends Service implements
  TdlibCache.CallStateChangeListener,
  AudioManager.OnAudioFocusChangeListener,
  SensorEventListener,
  VoIPController.ConnectionStateListener, UI.StateListener {
  @Override
  public IBinder onBind (Intent intent) {
    return null;
  }

  private @Nullable Tdlib tdlib;
  private @Nullable TdApi.Call call;
  private @Nullable String callChannelId;
  private TdApi.User user;

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
    if (controller != null) {
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
    return controller != null ? controller.getCallDuration() : -1;
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
  private @Nullable
  VoIPController controller;
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

  public void updateOutputGainControlState(){
    AudioManager am=(AudioManager) getSystemService(AUDIO_SERVICE);
    if (controller != null) {
      controller.setAudioOutputGainControlEnabled(hasEarpiece() && am != null && !am.isSpeakerphoneOn() && !am.isBluetoothScoOn() && !isHeadsetPlugged);
      controller.setEchoCancellationStrength(isHeadsetPlugged || (hasEarpiece() && am != null && !am.isSpeakerphoneOn() && !am.isBluetoothScoOn() && !isHeadsetPlugged) ? 0 : 1);
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

    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
    boolean success = false;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER) != null) {
      int outFramesPerBuffer = StringUtils.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
      if (outFramesPerBuffer != 0) {
        VoIPController.setNativeBufferSize(outFramesPerBuffer);
        success = true;
      }
    }
    if (!success) {
      VoIPController.setNativeBufferSize(AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) / 2);
    }
  }

  private void initCall (Tdlib tdlib, TdApi.Call call) {
    if (controller != null) {
      throw new IllegalStateException();
    }
    Log.v(Log.TAG_VOIP, "TGCallService.onCreate");
    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
    try {
      controller = new VoIPController();
      controller.setConnectionStateListener(this);
      controller.setConfig(tdlib.callPacketTimeoutMs(), tdlib.callConnectTimeoutMs(), tdlib.files().getVoipDataSavingOption(), call.id);

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
    if (isBtHeadsetConnected && !soundPoolMap.isProbablyPlaying()) {
      am.stopBluetoothSco();
      Log.d(Log.TAG_VOIP, "AudioManager.stopBluetoothSco (in onDestroy)");
      am.setSpeakerphoneOn(false);
      Log.d(Log.TAG_VOIP, "AudioManager.setSpeakerphoneOn(false) (in onDestroy)");
    }
    try {
      if (!soundPoolMap.isProbablyPlaying()) {
        am.setMode(AudioManager.MODE_NORMAL);
        Log.d(Log.TAG_VOIP, "AudioManager.setMode(AudioManager.MODE_NORMAL) (in onDestroy)");
      } else {
        final int amChangeCounterFinal = amChangeCounter;
        UI.post(() -> {
          if (amChangeCounterFinal == amChangeCounter) {
            try {
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
    if (call != null && call.state.getConstructor() == TdApi.CallStateDiscarded.CONSTRUCTOR && isInitiated) {
      updateStats();
      if (!sentDebugLog && ((TdApi.CallStateDiscarded) call.state).needDebugInformation && !StringUtils.isEmpty(lastDebugLog)) {
        sentDebugLog = true;
        tdlib.client().send(new TdApi.SendCallDebugInformation(call.id, lastDebugLog), tdlib.okHandler());
      }
      if (!sentRating && (((TdApi.CallStateDiscarded) call.state).needRating || (BuildConfig.DEBUG && logViewed))) {
        sentRating = true;
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

  @Override
  public void onCallUpgradeRequestReceived () {

  }

  @Override
  public void onGroupCallKeyReceived (byte[] key) {

  }

  @Override
  public void onGroupCallKeySent () {

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

  @Override
  public void onCallSettingsChanged (int callId, CallSettings settings) {
    if (controller != null) {
      controller.setMicMute(settings != null && settings.isMicMuted());
    }
    setAudioMode(settings != null ? settings.getSpeakerMode() : CallSettings.SPEAKER_MODE_NONE);
  }

  // Implementation

  private void acceptIncomingCall () {
    if (call != null) {
      tdlib.context().calls().acceptCall(this, tdlib, call.id);
      if (UI.getUiState() != UI.STATE_RESUMED) {
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
    return controller != null && isInitiated ? controller.getPreferredRelayID() : 0;
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
      if (newState != UI.STATE_RESUMED && needShowIncomingNotification) {
        needShowIncomingNotification = false;
        showIncomingNotification();
      } else if (newState == UI.STATE_RESUMED) {
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
    boolean needNotification = call != null && (call.isOutgoing || call.state.getConstructor() == TdApi.CallStateExchangingKeys.CONSTRUCTOR || call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR) && !TD.isFinished(call) && UI.getUiState() != UI.STATE_RESUMED;

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
      m.createNotificationChannel(channel);
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
    Bitmap bitmap = TdlibNotificationUtils.buildLargeIcon(tdlib, user.profilePhoto != null ? user.profilePhoto.small : null, TD.getAvatarColorId(user, tdlib.myUserId()), TD.getLetters(user), false, true);
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

    if (UI.getUiState() == UI.STATE_RESUMED) {
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
      m.createNotificationChannel(channel);
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
        ((SpannableString) endTitle).setSpan(new ForegroundColorSpan(Theme.getColor(R.id.theme_color_circleButtonNegative)), 0, endTitle.length(), 0);
      }
      builder.addAction(R.drawable.round_call_end_24_white, endTitle, PendingIntent.getBroadcast(this, 0, endIntent, PendingIntent.FLAG_ONE_SHOT | Intents.mutabilityFlags(false)));
      Intent answerIntent = new Intent();
      Intents.secureIntent(answerIntent, false);
      answerIntent.setAction(Intents.ACTION_ANSWER_CALL);
      CharSequence answerTitle = Lang.getString(R.string.AnswerCall);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        answerTitle = new SpannableString(answerTitle);
        ((SpannableString) answerTitle).setSpan(new ForegroundColorSpan(Theme.getColor(R.id.theme_color_circleButtonPositive)), 0, answerTitle.length(), 0);
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
    Bitmap bitmap = user != null ? TdlibNotificationUtils.buildLargeIcon(tdlib, user.profilePhoto != null ? user.profilePhoto.small : null, TD.getAvatarColorId(user, tdlib.myUserId()), TD.getLetters(user), false, true) : null;
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
      if (UI.getUiState() != UI.STATE_RESUMED) {
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
      switch (callSound) {
        case R.raw.voip_end:
        case R.raw.voip_fail:
          soundPoolMap.playUnique(callSound, 1, 1, 0, 0, 1);
          break;
        case R.raw.voip_busy:
          soundPoolMap.playUnique(callSound, 1, 1, 0, 2, 1);
          break;
        case R.raw.voip_connecting:
        case R.raw.voip_ringback:
        default:
          soundPoolMap.playUnique(callSound, 1, 1, 0, call.state.getConstructor() == TdApi.CallStateExchangingKeys.CONSTRUCTOR ? 0 : -1, 1);
          break;
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

  private void updateNetworkType (boolean force) {
    ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
    NetworkInfo info = cm.getActiveNetworkInfo();
    lastNetInfo = info;
    int type = VoIPController.NET_TYPE_UNKNOWN;
    if (info != null) {
      switch (info.getType()) {
        case ConnectivityManager.TYPE_MOBILE:
          switch (info.getSubtype()) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
              type = VoIPController.NET_TYPE_GPRS;
              break;
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
              type = VoIPController.NET_TYPE_EDGE;
              break;
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
              type = VoIPController.NET_TYPE_3G;
              break;
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
              type = VoIPController.NET_TYPE_HSPA;
              break;
            case TelephonyManager.NETWORK_TYPE_LTE:
              type = VoIPController.NET_TYPE_LTE;
              break;
            default:
              type = VoIPController.NET_TYPE_OTHER_MOBILE;
              break;
          }
          break;
        case ConnectivityManager.TYPE_WIFI:
          type = VoIPController.NET_TYPE_WIFI;
          break;
        case ConnectivityManager.TYPE_ETHERNET:
          type = VoIPController.NET_TYPE_ETHERNET;
          break;
      }
    }
    if (controller != null && (force || controller.getNetworkType() != type)) {
      controller.setNetworkType(type);
    }
  }

  private VoIPController.Stats stats = new VoIPController.Stats(), prevStats = new VoIPController.Stats();
  private long prevDuration;

  private void updateStats () {
    if (controller == null) {
      return;
    }
    controller.getStats(stats);

    long newDuration = getCallDuration();

    long wifiSentDiff = stats.bytesSentWifi - prevStats.bytesSentWifi;
    long wifiReceivedDiff = stats.bytesRecvdWifi - prevStats.bytesRecvdWifi;
    long mobileSentDiff = stats.bytesSentMobile - prevStats.bytesSentMobile;
    long mobileReceivedDiff = stats.bytesRecvdMobile - prevStats.bytesRecvdMobile;
    double durationDiff = (double) Math.max(0, newDuration - prevDuration) / 1000d;

    VoIPController.Stats tmp = stats;
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

  private boolean isInitiated;
  private String lastDebugLog;

  private void checkInitiated () {
    if (isInitiated || TD.isFinished(call)) {
      if (TD.isFinished(call)) {
        if (controller != null) {
          lastDebugLog = controller.getDebugLog();
          controller.release();
          controller = null;
        }
        cleanupChannels((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE));
        U.stopForeground(this, true, TdlibNotificationManager.ID_ONGOING_CALL_NOTIFICATION, TdlibNotificationManager.ID_INCOMING_CALL_NOTIFICATION);
        incomingNotification = ongoingCallNotification = null;
        stopSelf();
      }
      return;
    }

    if (call == null || call.state.getConstructor() != TdApi.CallStateReady.CONSTRUCTOR || isInitiated || controller == null) {
      return;
    }

    TdApi.CallStateReady state = (TdApi.CallStateReady) call.state;

    controller.setEncryptionKey(state.encryptionKey, call.isOutgoing);
    try {
      controller.setRemoteEndpoints(state.servers, state.protocol.udpP2p && state.allowP2p, Settings.instance().forceTcpInCalls(), state.protocol.maxLayer);
    } catch (IllegalArgumentException e) {
      hangUp();
      return;
    }
    int proxyId = Settings.instance().getEffectiveCallsProxyId();
    if (proxyId != Settings.PROXY_ID_NONE) {
      Settings.Proxy proxy = Settings.instance().getProxyConfig(proxyId);
      if (proxy != null && proxy.canUseForCalls()) {
        switch (proxy.type.getConstructor()) {
          case TdApi.ProxyTypeSocks5.CONSTRUCTOR: {
            TdApi.ProxyTypeSocks5 socks5 = (TdApi.ProxyTypeSocks5) proxy.type;
            controller.setProxy(proxy.server, proxy.port, socks5.username, socks5.password);
            break;
          }
          default: {
            Log.e("Unsupported proxy type for calls: %s", proxy.type);
            break;
          }
        }
      }
    }
    controller.start();
    updateNetworkType(false);
    controller.connect();

    isInitiated = true;
  }

  @Override
  public void onConnectionStateChanged (int newState) {
    try {
      if (tdlib == null || call == null) {
        return;
      }
      switch (newState) {
        case VoIPController.STATE_ESTABLISHED: {
          tdlib.dispatchCallStateChanged(call.id, newState);
          break;
        }
        case VoIPController.STATE_FAILED: {
          tdlib.context().calls().hangUp(tdlib, call.id, true, getConnectionId());
          break;
        }
      }
    } catch (Throwable t) {
      Log.e(Log.TAG_VOIP, "Error", t);
    }
  }

  @Override
  public void onSignalBarCountChanged (int newCount) {
    try {
      if (tdlib != null && call != null) {
        tdlib.dispatchCallBarsCount(call.id, newCount);
      }
    } catch (Throwable t) {
      Log.e(Log.TAG_VOIP, "Error", t);
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

  public static String getLog () {
    TGCallService service = currentInstance();
    if (service != null && service.controller != null) {
      return service.controller.getDebugString();
    } else {
      return "instance not found";
    }
  }
}
