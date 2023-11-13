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
 * File created on 04/03/2016 at 03:11
 */
package org.thunderdog.challegram.component.chat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.helper.Recorder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MessagesController;

import java.util.ArrayList;

import me.vkryl.core.lambda.CancellableRunnable;

public class RaiseHelper implements SensorEventListener {
  private static final boolean USE_WAKELOCK = true;

  public interface Listener {
    boolean enterRaiseMode ();
    boolean leaveRaiseMode ();
  }

  private static RaiseHelper instance;

  public static RaiseHelper instance () {
    if (instance == null) {
      instance = new RaiseHelper();
    }
    return instance;
  }

  private boolean raiseActive;
  private boolean raiseDisabled;

  private SensorManager manager;
  private Sensor proximity, accelerometer, magnetometer;
  private PowerManager.WakeLock wakeLock;
  private AudioManager audioManager;

  private final ArrayList<Listener> listeners;

  private RaiseHelper () {
    listeners = new ArrayList<>(2);
    try {
      manager = (SensorManager) UI.getContext().getSystemService(Context.SENSOR_SERVICE);
      if (manager == null) {
        raiseDisabled = true;
        Log.e("SensorManager is unavailable");
        return;
      }
    } catch (Throwable t) {
      raiseDisabled = true;
      Log.e("SensorManager error", t);
      return;
    }

    try {
      proximity = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
      if (proximity == null) {
        Log.w("Proximity sensor is unavailable");
        destroy();
        return;
      }
    } catch (Throwable t) {
      Log.w("Error accessing proximity sensor", t);
      destroy();
      return;
    }

    try {
      accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
      if (accelerometer == null) {
        Log.w("Accelerometer sensor is unavailable");
        destroy();
        return;
      }
    } catch (Throwable t) {
      Log.w("Error accessing accelerometer sensor", t);
      destroy();
      return;
    }

    try {
      magnetometer = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
      if (magnetometer == null) {
        Log.w("Magnetometer sensor is unavailable");
        destroy();
        return;
      }
    } catch (Throwable t) {
      Log.w("Error accessing magnetometer sensor", t);
      destroy();
      return;
    }

    if (USE_WAKELOCK) {
      try {
        PowerManager powerManager;
        powerManager = (PowerManager) UI.getContext().getSystemService(Context.POWER_SERVICE);

        // PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK
        wakeLock = powerManager.newWakeLock(0x00000020, "tgx:proximity");
      } catch (Throwable t) {
        Log.w("Cannot create wakeLock", t);
      }
    }
  }

  public boolean isUnavailable () {
    return raiseDisabled;
  }

  private void destroy () {
    raiseDisabled = true;

    proximity = null;
    accelerometer = null;
    magnetometer = null;
  }

  // Public interrupts

  public boolean inRaiseMode () {
    return !raiseDisabled && raiseActive && inRaiseMode;
  }

  public void register (Listener listener) {
    if (!raiseDisabled /*&& Settings.instance().needRaiseToSpeak()*/) {
      if (!raiseActive) {
        reset();
        addListener(listener);
        this.inRaiseMode = false;
        this.raiseActive = !raiseDisabled && registerInternal();
      } else {
        addListener(listener);
      }
    }
  }

  private void addListener (Listener listener) {
    synchronized (listeners) {
      if (!listeners.contains(listener)) {
        this.listeners.add(listener);
      }
    }
  }

  public void unregister (Listener listener) {
    if (raiseActive && !raiseDisabled) {
      final boolean result;
      synchronized (listeners) {
        result = this.listeners.remove(listener) && listeners.isEmpty();
      }
      if (result) {
        unregisterInternal();
        raiseActive = false;
      }
    }
  }

  // Private utils

  private boolean registerInternal () {
    try {
      manager.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL);
      manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
      if (magnetometer != null) {
        manager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
      }
    } catch (Throwable t) {
      Log.w("Cannot register receiver", t);
    }
    return true;
  }

  private void unregisterInternal () {
    reset();
    processRaiseMode();
    try {
      manager.unregisterListener(this);
    } catch (Throwable t) {
      Log.w("Cannot unregister sensor event listener", t);
    }
    if (USE_WAKELOCK) {
      if (wakeLock != null) {
        try {
          if (wakeLock.isHeld()) {
            wakeLock.release();
          }
        } catch (Throwable t) {
          Log.w("Cannot release wakeLock", t);
        }
      }
    }
  }

  @SuppressWarnings ("deprecation")
  private boolean isWiredHeadsetOn () {
    if (audioManager == null) {
      try {
        audioManager = (AudioManager) UI.getContext().getSystemService(Context.AUDIO_SERVICE);
        return audioManager.isWiredHeadsetOn();
      } catch (Throwable t) {
        Log.w("AudioManager is unavailable", t);
      }
    } else {
      try {
        return audioManager.isWiredHeadsetOn();
      } catch (Throwable t) {
        Log.w("No isWiredHeadsetOn method", t);
      }
    }
    return false;
  }

  // Current state

  private boolean isNearToSensor;
  private boolean isInGoodAngle;

  private void reset () {
    isNearToSensor = false;
    hasAccelerometerData = false;
    hasMagnetometerData = false;
  }

  // Sensor event listener

  @Override
  public void onSensorChanged (SensorEvent e) {
    if (proximity != null && e.sensor == proximity) {
      processProximityEvent(e);
    } else if (accelerometer != null && e.sensor == accelerometer) {
      processAccelerometerEvent(e);
    } else if (magnetometer != null && e.sensor == magnetometer) {
      processMagnetometerEvent(e);
    } else {
      Log.v("Unknown event");
    }
  }

  @Override
  public void onAccuracyChanged (Sensor sensor, int accuracy) { }


  // Proximity

  private boolean isNearToSensor (float value) {
    return value < 5.0f && value != proximity.getMaximumRange();
  }

  private void processProximityEvent (SensorEvent e) {
    final boolean newValue = isNearToSensor(e.values[0]);
    if (newValue != isNearToSensor) {
      isNearToSensor = newValue;
      processRaiseMode();
    }
  }

  // Accelerometer

  private boolean hasAccelerometerData;
  private float[] accelerometerData = new float[3];

  private void processAccelerometerEvent (SensorEvent e) {
    System.arraycopy(e.values, 0, accelerometerData, 0, e.values.length);
    hasAccelerometerData = true;
    if (hasMagnetometerData) {
      processOrientation();
    }
  }

  // Magnetometer

  private boolean hasMagnetometerData;
  private final float[] magnetometerData = new float[3];

  private void processMagnetometerEvent (SensorEvent e) {
    System.arraycopy(e.values, 0, magnetometerData, 0, e.values.length);
    hasMagnetometerData = true;
    if (hasAccelerometerData) {
      processOrientation();
    }
  }

  // Orientation computation

  private final float[] R = new float[9];
  private final float[] orientation = new float[3];

  private void processOrientation () {
    SensorManager.getRotationMatrix(R, null, accelerometerData, magnetometerData);
    SensorManager.getOrientation(R, orientation);
    boolean newValue = isInGoodAngle(orientation);
    if (newValue && !isInGoodAngle) { // Set only once to prevent unexpected leaving raise mode
      isInGoodAngle = true;
      processRaiseMode();
    } else if (!newValue && !isNearToSensor) {
      isInGoodAngle = false;
    }
  }

  /*private static long lastLogTime;
  private static boolean debug () {
    long time = System.currentTimeMillis();
    if (lastLogTime == 0 || time - lastLogTime > 2000) {
      lastLogTime = time;
      return true;
    }
    return false;
  }*/

  private static final int PITCH_THRESHOLD = 75;
  private static final int PITCH_MIN_THRESHOLD = 15;
  private static final int ROLL_THRESHOLD = 30;
  private static final int ROLL_MIN_THRESHOLD = 10;

  private static final float delta = 180.0f / (float) Math.PI;
  private static boolean isInGoodAngle (float[] data) {
    // final int yaw = (int) (data[0] * delta); // yaw
    final int pitch = (int) (data[1] * delta); // pitch
    final int roll = (int) (data[2] * delta); // roll

    /*if (debug()) {
      // boolean yawCheck = true;
      boolean pitchCheck = Math.abs(90 + pitch) < PITCH_THRESHOLD;
      boolean rollCheck = Math.abs(90 - Math.abs(roll)) < ROLL_THRESHOLD;

      if (rollCheck && pitchCheck) {
        Logger.v("pitch: %d(%s) roll: %d(%s)", pitch, pitchCheck ? "+" : "-", roll, rollCheck ? "+" : "-");
      } else {
        Logger.w("pitch: %d(%s) roll: %d(%s)", pitch, pitchCheck ? "+" : "-", roll, rollCheck ? "+" : "-");
      }
    }*/

    final int pitchCheck = Math.abs(90 + pitch);
    final int rollCheck = Math.abs(90 - Math.abs(roll));

    return pitchCheck > PITCH_MIN_THRESHOLD && pitchCheck < PITCH_THRESHOLD && rollCheck > ROLL_MIN_THRESHOLD && rollCheck < ROLL_THRESHOLD;
  }

  // Raise mode (full)

  private boolean futureRaise;
  private CancellableRunnable scheduledRaise;

  private void setRaiseDelayed (boolean raise) {
    if (futureRaise != raise) {
      futureRaise = raise;
      if (scheduledRaise != null) {
        scheduledRaise.cancel();
        scheduledRaise = null;
      }
      if (raise) {
        scheduledRaise = new CancellableRunnable() {
          @Override
          public void act () {
            if (isPending() && futureRaise && scheduledRaise != null) {
              raise(true);
            }
          }
        };
        UI.post(scheduledRaise, 1000l);
      } else {
        raise(false);
      }
    }
  }

  private void processRaiseMode () {
    boolean inRaise = isInGoodAngle && (/*inRaiseMode ||*/ (isNearToSensor && !Recorder.instance().isRecording() && hasPermissions()));
    if (inRaise) {
      BaseActivity context = UI.getUiContext();
      inRaise = !TdlibManager.instance().calls().hasActiveCall();
      if (inRaise && !this.inRaiseMode && (context != null && context.getActivityState() == UI.State.RESUMED && !context.isActivityBusyWithSomething())) {
        ViewController<?> c = context.navigation().getCurrentStackItem();
        inRaise = c instanceof MessagesController && ((MessagesController) c).isEmojiInputEmpty();
      }
    }
    setRaiseDelayed(inRaise);
  }

  private boolean hasPermissions () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      Context context = UI.getContext();
      return context != null && context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }
    return true;
  }

  private boolean inRaiseMode;
  private long lastInRaiseMode;

  private void raise (final boolean newValue) {
    if (newValue == inRaiseMode) {
      return;
    }

    if (newValue) {
      if (lastInRaiseMode != 0 && SystemClock.uptimeMillis() - lastInRaiseMode < 1000l) {
        return;
      }
      final BaseActivity context = UI.getUiContext();
      if (context == null || context.getRecordAudioVideoController().isOpen() || context.isPasscodeShowing()) {
        return;
      }
      try {
        if (isWiredHeadsetOn()) {
          return;
        }
      } catch (Throwable t) {
        Log.w("Cannot check wired headset", t);
      }
    }

    inRaiseMode = newValue;
    if (newValue) {
      if (!enterRaiseMode()) {
        inRaiseMode = false;
        return;
      }
    } else {
      if (!leaveRaiseMode()) {
        inRaiseMode = true;
        return;
      }
    }

    if (USE_WAKELOCK) {
      if (wakeLock != null) {
        try {
          if (newValue && !wakeLock.isHeld()) {
            wakeLock.acquire();
          } else if (!newValue && wakeLock.isHeld()) {
            wakeLock.release();
          }
        } catch (Throwable t) {
          Log.v("Cannot work with wakeLock", t);
        }
      }
    }

    if (newValue) {
      lastInRaiseMode = SystemClock.uptimeMillis();
    }
  }

  private boolean enterRaiseMode () {
    boolean result = true;
    synchronized (listeners) {
      for (int i = 0; i < listeners.size(); i++) {
        if (!listeners.get(i).enterRaiseMode()) {
          result = false;
        }
      }
    }
    return result;
  }

  private boolean leaveRaiseMode () {
    boolean result = true;
    synchronized (listeners) {
      for (int i = 0; i < listeners.size(); i++) {
        if (!listeners.get(i).leaveRaiseMode()) {
          result = false;
        }
      }
    }
    return result;
  }
}
