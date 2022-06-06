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
 * File created on 09/07/2018
 */
package org.thunderdog.challegram.player;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.PowerManager;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

public class ProximityManager implements Settings.RaiseToSpeakListener, SensorEventListener, UI.StateListener {
  public interface Delegate {
    void onUpdateAttributes ();
  }

  private final TGPlayerController player;
  private final Delegate delegate;
  private @Nullable TdApi.Message playbackObject;

  public ProximityManager (TGPlayerController player, Delegate delegate) {
    this.player = player;
    this.delegate = delegate;
  }

  private boolean isPlayingVideo () {
    return playbackObject != null && playbackObject.content.getConstructor() == TdApi.MessageVideoNote.CONSTRUCTOR;
  }

  public void setPlaybackObject (@Nullable TdApi.Message playbackObject) {
    boolean hadObject = this.playbackObject != null;
    boolean hasObject = playbackObject != null;
    this.playbackObject = playbackObject;
    if (hadObject != hasObject) {
      if (hasObject) {
        Settings.instance().addRaiseToSpeakListener(this);
        uiPaused = UI.getUiState() != UI.STATE_RESUMED;
        UI.addStateListener(this);
        this.isVideo = isPlayingVideo();
        setEarpieceMode(Settings.instance().getEarpieceMode(isVideo));
      } else {
        Settings.instance().removeRaiseToSpeakListener(this);
        UI.removeStateListener(this);
        setEarpieceMode(Settings.EARPIECE_MODE_NEVER);
      }
      checkRaiseMode();
    } else if (hasObject) {
      this.isVideo = isPlayingVideo();
      int newEarpieceMode = Settings.instance().getEarpieceMode(isVideo);
      if (!inRaiseMode || newEarpieceMode != Settings.EARPIECE_MODE_NEVER) {
        setEarpieceMode(newEarpieceMode);
      }
    }
  }

  private boolean isVideo;

  @Override
  public void onEarpieceModeChanged (boolean isVideo, int newMode) {
    if (this.isVideo == isVideo) {
      setEarpieceMode(newMode);
    }
  }

  private @Settings.EarpieceMode int earpieceMode = Settings.EARPIECE_MODE_NEVER;

  private boolean setEarpieceMode (int mode) {
    if (this.earpieceMode != mode) {
      this.earpieceMode = mode;
      boolean needProximitySensor = false;
      boolean forceEarpiece = false;
      switch (mode) {
        case Settings.EARPIECE_MODE_ALWAYS:
          needProximitySensor = true;
          forceEarpiece = true;
          break;
        case Settings.EARPIECE_MODE_PROXIMITY:
          needProximitySensor = true;
          break;
        case Settings.EARPIECE_MODE_NEVER:
          break;
      }
      this.forceEarpiece = forceEarpiece;
      checkPlayingThroughEarpiece();
      this.needProximitySensor = needProximitySensor;
      checkProximitySensorEnabled();
      return true;
    }
    return false;
  }

  @Override
  public void onUiStateChanged (int newState) {
    boolean isPaused = newState != UI.STATE_RESUMED;
    if (this.uiPaused != isPaused) {
      this.uiPaused = isPaused;
      checkProximitySensorEnabled();
    }
  }

  private boolean needProximitySensor;
  private boolean proximitySensorEnabled;

  private boolean uiPaused;

  private void checkProximitySensorEnabled () {
    boolean proximitySensorEnabled = needProximitySensor && (!uiPaused || inRaiseMode) && !isWiredHeadsetOn;
    if (this.proximitySensorEnabled != proximitySensorEnabled) {
      if (proximitySensorEnabled) {
        if (!registerProximitySensor()) {
          return;
        }
      } else {
        if (!unregisterProximitySensor()) {
          return;
        }
      }
      this.proximitySensorEnabled = proximitySensorEnabled;
    }
  }

  private Sensor proximitySensor;
  private PowerManager.WakeLock proximityWakeLock;

  private boolean isWiredHeadsetOn;
  private BroadcastReceiver receiver = new BroadcastReceiver() {
    @Override
    public void onReceive (Context context, Intent intent) {
      checkWiredHeadset();
    }
  };

  private boolean registerProximitySensor () {
    SensorManager manager = (SensorManager) UI.getContext().getSystemService(Context.SENSOR_SERVICE);
    if (manager == null)
      return false;
    if (proximitySensor == null)
      proximitySensor = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
    if (proximitySensor == null)
      return false;
    if (proximityWakeLock == null) {
      PowerManager powerManager = (PowerManager) UI.getContext().getSystemService(Context.POWER_SERVICE);
      if (powerManager != null) {
        try {
          proximityWakeLock = powerManager.newWakeLock(0x00000020, "tgx:proximity");
        } catch (Throwable t) {
          Log.e("Unable to create proximity wake lock", t);
        }
      }
    }
    try {
      manager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
    } catch (Throwable t) {
      Log.e("Unable to register proximity sensor listener", t);
      return false;
    }

    AudioManager am = (AudioManager) UI.getContext().getSystemService(Context.AUDIO_SERVICE);
    if (am != null) {
      isWiredHeadsetOn = U.isWiredHeadsetOn(am);
      BluetoothAdapter btAdapter = am.isBluetoothScoAvailableOffCall() ? BluetoothAdapter.getDefaultAdapter() : null;

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
      try {
        UI.getAppContext().registerReceiver(receiver, filter);
      } catch (Throwable t) {
        Log.e("Unable to register headset broadcast receiver", t);
      }
    } else {
      isWiredHeadsetOn = false;
    }
    return true;
  }

  private boolean unregisterProximitySensor () {
    SensorManager manager = (SensorManager) UI.getContext().getSystemService(Context.SENSOR_SERVICE);
    if (manager == null)
      return false;
    if (proximitySensor == null)
      return false;
    setWakeLockHeld(false);
    try {
      manager.unregisterListener(this, proximitySensor);
    } catch (Throwable t) {
      Log.e("Unable to unregister proximity sensor listener", t);
      return false;
    }
    try {
      UI.getAppContext().unregisterReceiver(receiver);
    } catch (Throwable t) {
      Log.e("Unable to unregister receiver");
    }
    return true;
  }

  private boolean forceEarpiece, isNearToProximitySensor, inRaiseMode;
  private boolean playingThroughEarpiece;

  private void checkPlayingThroughEarpiece () {
    boolean playingThroughEarpiece = playbackObject != null && (forceEarpiece || isNearToProximitySensor);
    if (this.playingThroughEarpiece != playingThroughEarpiece) {
      this.playingThroughEarpiece = playingThroughEarpiece;
      AudioManager am = (AudioManager) UI.getAppContext().getSystemService(Context.AUDIO_SERVICE);
      if (am != null) {
        if (playingThroughEarpiece) {
          am.setSpeakerphoneOn(false);
          am.setMode(AudioManager.MODE_IN_COMMUNICATION);
        } else {
          am.setMode(AudioManager.MODE_NORMAL);
        }
      }
      delegate.onUpdateAttributes();
    }
  }

  public boolean needPlayThroughEarpiece () {
    return playingThroughEarpiece;
  }

  @Override
  public void onSensorChanged (SensorEvent e) {
    if (e.sensor == null)
      return;
    if (e.sensor == proximitySensor) {
      float value = e.values[0];
      boolean isNearToSensor = playbackObject != null && value < 5.0f && value < proximitySensor.getMaximumRange();
      if (this.isNearToProximitySensor != isNearToSensor) {
        this.isNearToProximitySensor = isNearToSensor;
        checkRaiseMode();
      }
    }
  }

  private void checkWiredHeadset () {
    AudioManager am = (AudioManager) UI.getContext().getSystemService(Context.AUDIO_SERVICE);
    boolean hasHeadset = am != null && U.isWiredHeadsetOn(am);
    if (this.isWiredHeadsetOn != hasHeadset) {
      this.isWiredHeadsetOn = hasHeadset;
      checkRaiseMode();
      checkProximitySensorEnabled();
    }
  }

  private void checkRaiseMode () {
    boolean inRaiseMode = playbackObject != null && isNearToProximitySensor && !isWiredHeadsetOn;
    if (this.inRaiseMode != inRaiseMode) {
      this.inRaiseMode = inRaiseMode;

      BaseActivity context = UI.getUiContext(); // FIXME replace with listeners list
      if (context != null)
        context.setOrientationLockFlagEnabled(BaseActivity.ORIENTATION_FLAG_PROXIMITY, inRaiseMode);
      if (inRaiseMode) {
        checkPlayingThroughEarpiece();
        player.playIfPaused(playbackObject);
      } else {
        player.pauseWithReason(TGPlayerController.PAUSE_REASON_PROXIMITY);
        if (!setEarpieceMode(Settings.instance().getEarpieceMode(isVideo))) {
          checkPlayingThroughEarpiece();
        }
      }

      setWakeLockHeld(inRaiseMode);
    }
  }

  @Override
  public void onAccuracyChanged (Sensor sensor, int accuracy) { }

  private boolean wakeLockHeld;

  @SuppressWarnings("WakelockTimeout")
  private void setWakeLockHeld (boolean wakeLockHeld) {
    if (proximityWakeLock == null) {
      return;
    }
    if (this.wakeLockHeld != wakeLockHeld) {
      boolean ok = false;
      try {
        if (wakeLockHeld) {
          proximityWakeLock.acquire();
        } else {
          proximityWakeLock.release();
        }
        ok = true;
      } catch (Throwable t) {
        Log.e("Unable to acquire/release wake lock, wakeLockHeld:%b", t, wakeLockHeld);
      }
      if (ok) {
        this.wakeLockHeld = wakeLockHeld;
      }
    }
  }

  public void modifyExoPlayer (ExoPlayer exoPlayer, @C.AudioContentType int contentType) {
    if (needPlayThroughEarpiece()) {
      exoPlayer.setAudioAttributes(new AudioAttributes.Builder().setContentType(C.CONTENT_TYPE_SPEECH).setUsage(C.USAGE_VOICE_COMMUNICATION).build(), false);
    } else {
      exoPlayer.setAudioAttributes(new AudioAttributes.Builder().setContentType(contentType).setUsage(C.USAGE_MEDIA).build(), false);
    }
  }
}
