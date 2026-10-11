/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.json.JSONObject;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.util.Permissions;
import me.vkryl.core.lambda.RunnableInt;
import me.vkryl.core.lambda.RunnableBool;
import static org.thunderdog.challegram.miniapps.MiniAppTheme.object;

/** Bot consent and Android permission are independent. No background location subscription. */
final class MiniAppLocation {
  private final MiniAppController host;
  private final SharedPreferences preferences;
  private final LocationManager manager;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private LocationListener listener;
  private Runnable timeout;
  private MiniAppDocument.Request pending;
  private int generation;

  MiniAppLocation (MiniAppController host) {
    this.host = host;
    preferences = host.context().getSharedPreferences("miniapp-location-" + host.storageScope(), Context.MODE_PRIVATE);
    manager = (LocationManager) host.context().getSystemService(Context.LOCATION_SERVICE);
  }
  boolean available () { return manager != null && host.context().getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION); }
  private boolean permission (String name) {
    return androidx.core.content.ContextCompat.checkSelfPermission(host.context(), name) == PackageManager.PERMISSION_GRANTED;
  }
  private boolean appPermission () { return permission(Manifest.permission.ACCESS_COARSE_LOCATION) || permission(Manifest.permission.ACCESS_FINE_LOCATION); }
  boolean granted () { return preferences.getBoolean("granted", false) && appPermission(); }
  JSONObject info () {
    JSONObject result = object("available", available());
    if (available()) {
      boolean requested = preferences.getBoolean("requested", false);
      try { result.put("access_requested", requested); if (requested) result.put("access_granted", granted()); } catch (Exception ignored) { }
    }
    return result;
  }
  void setGranted (boolean value) {
    preferences.edit().putBoolean("requested", true).putBoolean("granted", value).apply();
    if (!value) cancel();
  }
  void request (MiniAppDocument.Request origin) {
    if (pending != null) return;
    if (!available()) { host.reply(origin, "location_requested", object("available", false)); return; }
    pending = origin;
    consent(origin, "", allowed -> {
      if (pending != origin) return;
      if (!allowed) finish(null); else acquire();
    });
  }

  /** HTML geolocation has a separate, exact-origin grant; never retained by Chromium globally. */
  void consent (MiniAppDocument.Request origin, String webOrigin, RunnableBool done) {
    String prefix = webOrigin.isEmpty() ? "" : "html:" + webOrigin + ":";
    int requestGeneration = generation;
    RunnableBool deliver = allowed -> {
      if (requestGeneration == generation && host.current(origin)) done.runWithBool(allowed);
      else done.runWithBool(false);
    };
    Runnable askAndroid = () -> {
      RunnableInt result = ignored -> {
        boolean ok = requestGeneration == generation && host.current(origin) && appPermission();
        if (ok) preferences.edit().putBoolean(prefix + "requested", true).putBoolean(prefix + "granted", true).apply();
        deliver.runWithBool(ok);
      };
      if (!host.context().permissions().requestPermissions(result, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) result.runWithInt(Permissions.GrantResult.ALL);
    };
    if (preferences.getBoolean(prefix + "granted", false)) { askAndroid.run(); return; }
    if (preferences.getBoolean(prefix + "requested", false)) { deliver.runWithBool(false); return; }
    String message = Lang.getString(R.string.MiniAppLocationRequest, host.botName());
    if (!webOrigin.isEmpty()) message += "\n\n" + webOrigin;
    host.nativePrompt(origin, "", message, R.string.Allow, MiniAppPermissionView.location(host), askAndroid, () -> {
      preferences.edit().putBoolean(prefix + "requested", true).putBoolean(prefix + "granted", false).apply();
      deliver.runWithBool(false);
    });
  }

  @android.annotation.SuppressLint("MissingPermission")
  private void acquire () {
    if (pending == null || !host.current(pending) || !appPermission()) { finish(null); return; }
    try {
      java.util.List<String> providers = manager.getProviders(true);
      Location best = null;
      for (String provider : providers) {
        if (!permission(Manifest.permission.ACCESS_FINE_LOCATION) && LocationManager.GPS_PROVIDER.equals(provider)) continue;
        Location location = manager.getLastKnownLocation(provider);
        if (location != null && SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos() < 120_000_000_000L &&
            (best == null || location.getElapsedRealtimeNanos() > best.getElapsedRealtimeNanos())) best = location;
      }
      if (best != null) { finish(best); return; }
      listener = new LocationListener() {
        @Override public void onLocationChanged (Location location) { finish(location); }
        @Override public void onStatusChanged (String provider, int status, Bundle extras) { }
        @Override public void onProviderEnabled (String provider) { }
        @Override public void onProviderDisabled (String provider) { }
      };
      boolean registered = false;
      for (String provider : providers) {
        if (LocationManager.PASSIVE_PROVIDER.equals(provider) || !permission(Manifest.permission.ACCESS_FINE_LOCATION) && LocationManager.GPS_PROVIDER.equals(provider)) continue;
        manager.requestLocationUpdates(provider, 0, 0, listener, Looper.getMainLooper()); registered = true;
      }
      if (!registered) { finish(null); return; }
      timeout = () -> finish(null); handler.postDelayed(timeout, 15000);
    } catch (RuntimeException error) { finish(null); }
  }
  private void finish (Location location) {
    if (timeout != null) { handler.removeCallbacks(timeout); timeout = null; }
    if (listener != null) { try { manager.removeUpdates(listener); } catch (RuntimeException ignored) { } listener = null; }
    MiniAppDocument.Request origin = pending; pending = null;
    if (origin != null) host.reply(origin, "location_requested", locationObject(location));
  }
  void cancel () { generation++; finish(null); }
  void pause () { if (listener != null) finish(null); }
  static JSONObject locationObject (Location location) {
    if (location == null) return object("available", false);
    return object("available", true, "latitude", location.getLatitude(), "longitude", location.getLongitude(),
      "altitude", location.hasAltitude() ? location.getAltitude() : JSONObject.NULL,
      "course", location.hasBearing() ? location.getBearing() : JSONObject.NULL,
      "speed", location.hasSpeed() ? location.getSpeed() : JSONObject.NULL,
      "horizontal_accuracy", location.hasAccuracy() ? location.getAccuracy() : JSONObject.NULL,
      "vertical_accuracy", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy() ? location.getVerticalAccuracyMeters() : JSONObject.NULL,
      "course_accuracy", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasBearingAccuracy() ? location.getBearingAccuracyDegrees() : JSONObject.NULL,
      "speed_accuracy", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasSpeedAccuracy() ? location.getSpeedAccuracyMetersPerSecond() : JSONObject.NULL);
  }
}
