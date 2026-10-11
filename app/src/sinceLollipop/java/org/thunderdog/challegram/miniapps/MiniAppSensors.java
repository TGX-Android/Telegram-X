/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Axis conventions and throttling follow Telegram Android's BotSensors (DrKLO/Telegram, GPL-2.0-or-later).
 */
package org.thunderdog.challegram.miniapps;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Arrays;

/** Main-thread, per-document subscriptions. No listener or postponed sample survives pause/stop. */
final class MiniAppSensors {
  interface Callback { void onEvent (String event, JSONObject data); }
  private final SensorManager manager;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Stream[] streams = new Stream[3];
  private boolean paused;

  MiniAppSensors (Context context) { manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE); }

  boolean start (int type, long interval, boolean absolute, Callback callback) {
    if (manager == null) return false;
    Stream existing = streams[type];
    if (existing != null && (type != 2 || existing.absolute == absolute)) return true;
    stop(type);
    Stream stream = new Stream(type, Math.max(20, Math.min(1000, interval)), absolute, callback);
    if (!stream.available() || (!paused && !stream.register())) { stream.unregister(); return false; }
    streams[type] = stream;
    return true;
  }

  boolean stop (int type) {
    if (manager == null) return false;
    if (streams[type] != null) { streams[type].unregister(); streams[type] = null; }
    return true;
  }

  void stopAll () { for (int type = 0; type < streams.length; type++) stop(type); }
  void pause () {
    if (paused) return;
    paused = true;
    for (Stream stream : streams) if (stream != null) stream.unregister();
  }
  void resume () {
    if (!paused) return;
    paused = false;
    for (int type = 0; type < streams.length; type++) {
      Stream stream = streams[type];
      if (stream != null && !stream.register()) {
        stream.unregister(); streams[type] = null;
        stream.callback.onEvent(stream.prefix + "_failed", MiniAppTheme.object("error", "UNSUPPORTED"));
      }
    }
  }

  private final class Stream implements SensorEventListener {
    final int type;
    final long interval;
    final boolean absolute;
    final Callback callback;
    final Sensor first, second;
    final String prefix;
    final Runnable postponed = this::post;
    float[] values, gravity, magnetic;
    final float[] captured = new float[3];
    long last;
    boolean registered;

    Stream (int type, long interval, boolean absolute, Callback callback) {
      this.type = type; this.interval = interval; this.absolute = absolute; this.callback = callback;
      prefix = type == 0 ? "accelerometer" : type == 1 ? "gyroscope" : "device_orientation";
      first = manager.getDefaultSensor(type == 0 || type == 2 && absolute ? Sensor.TYPE_ACCELEROMETER : type == 1 ? Sensor.TYPE_GYROSCOPE : Sensor.TYPE_GAME_ROTATION_VECTOR);
      second = type == 2 && absolute ? manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) : null;
    }
    boolean available () { return first != null && (type != 2 || !absolute || second != null); }
    boolean register () {
      int delay = interval >= 160 ? SensorManager.SENSOR_DELAY_NORMAL : interval >= 60 ? SensorManager.SENSOR_DELAY_UI : SensorManager.SENSOR_DELAY_GAME;
      try {
        registered = manager.registerListener(this, first, delay, handler);
        if (second != null) registered &= manager.registerListener(this, second, delay, handler);
      } catch (RuntimeException e) { registered = false; }
      return registered;
    }
    void unregister () {
      manager.unregisterListener(this);
      handler.removeCallbacks(postponed);
      registered = false; last = 0;
      values = gravity = magnetic = null;
      Arrays.fill(captured, 0);
    }
    @Override public void onSensorChanged (SensorEvent event) {
      if (paused || !registered || streams[type] != this) return;
      // Android recycles the values array after returning from this callback.
      if (type == 1) {
        for (int i = 0; i < 3; i++) captured[i] += event.values[i];
      } else if (type == 2 && absolute) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) gravity = event.values.clone();
        else magnetic = event.values.clone();
      } else values = event.values.clone();
      handler.removeCallbacks(postponed);
      long delay = interval - (SystemClock.uptimeMillis() - last);
      if (delay > 0) handler.postDelayed(postponed, delay); else post();
    }
    private void post () {
      if (paused || !registered || streams[type] != this) return;
      JSONObject data = new JSONObject();
      try {
        if (type == 0) {
          if (values == null) return;
          data.put("x", -values[0]).put("y", -values[1]).put("z", -values[2]);
        } else if (type == 1) {
          data.put("x", captured[0]).put("y", captured[1]).put("z", captured[2]);
          Arrays.fill(captured, 0);
        } else {
          float[] matrix = new float[9];
          if (absolute) {
            if (gravity == null || magnetic == null || !SensorManager.getRotationMatrix(matrix, null, gravity, magnetic)) return;
          } else {
            if (values == null) return;
            SensorManager.getRotationMatrixFromVector(matrix, values.length > 4 ? Arrays.copyOf(values, 4) : values);
          }
          float[] angles = SensorManager.getOrientation(matrix, new float[3]);
          data.put("absolute", absolute).put("alpha", -angles[0]).put("beta", -angles[1]).put("gamma", angles[2]);
        }
        last = SystemClock.uptimeMillis();
        callback.onEvent(prefix + "_changed", data);
      } catch (JSONException | IllegalArgumentException ignored) { }
    }
    @Override public void onAccuracyChanged (Sensor sensor, int accuracy) { }
  }
}
