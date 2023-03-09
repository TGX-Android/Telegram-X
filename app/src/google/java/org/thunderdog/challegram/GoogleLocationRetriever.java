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
 * File created on 22/10/2022
 */
package org.thunderdog.challegram;

import android.location.Location;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.LocationRetriever;

import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.RunnableBool;

public class GoogleLocationRetriever extends LocationRetriever {
  private GoogleApiClient googleClient;
  private boolean isUnavailable;

  public GoogleLocationRetriever (BaseActivity context) {
    super(context);

    GoogleApiClient.Builder b = new GoogleApiClient.Builder(context);
    b.addApi(LocationServices.API);
    b.addOnConnectionFailedListener(connectionResult -> {
      isUnavailable = true;
      onLocationFetchFailed();
    });
    googleClient = b.build();
    googleClient.connect();
  }

  @Override
  public void performDestroy () {
    if (googleClient != null) {
      try {
        googleClient.disconnect();
      } catch (Throwable t) {
        Log.w("GoogleApiClient.disconnect() failed", t);
      }
      googleClient = null;
    }
  }

  @Override
  public void checkPermissions (RunnableBool callback) {
    try {
      final LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
        .addLocationRequest(LocationRequest.create())
        .setAlwaysShow(true);
      final LocationSettingsRequest request = builder.build();
      final PendingResult<LocationSettingsResult> result =
        LocationServices.SettingsApi.checkLocationSettings(googleClient, request);

      result.setResultCallback(result1 -> {
        final Status status = result1.getStatus();
        switch (status.getStatusCode()) {
          case LocationSettingsStatusCodes.RESOLUTION_REQUIRED: {
            try {
              activity.putActivityResultHandler(Intents.ACTIVITY_RESULT_GOOGLE_LOCATION_REQUEST, (requestCode, resultCode, data) -> {
                // TODO check if permission request rejected?
                callback.runWithBool(true);
              });
              status.startResolutionForResult(activity, Intents.ACTIVITY_RESULT_GOOGLE_LOCATION_REQUEST);
            } catch (Throwable t) {
              callback.runWithBool(false);
            }
            break;
          }
          case LocationSettingsStatusCodes.SUCCESS:
          case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
          default: {
            callback.runWithBool(true);
            break;
          }
        }
      });
    } catch (Throwable t) {
      Log.w("GoogleLocationRetriever.retrieveLocation() failed", t);
      callback.runWithBool(false);
    }
  }

  @Override
  protected void retrieveLocation () {
    if (isUnavailable) {
      onLocationFetchFailed();
      return;
    }
    checkPermissions((havePermissions) -> {
      if (havePermissions) {
        retrieveLocationImpl();
      } else {
        onLocationFetchFailed();
      }
    });
  }

  private static final long LOCATION_MAX_WAIT_TIME = 3000L;
  private static final boolean USE_LAST_KNOWN_LOCATION = false;

  private void retrieveLocationImpl () {
    final CancellableRunnable[] timeout = new CancellableRunnable[1];
    final boolean[] sent = new boolean[1];
    final LocationListener listener = location -> {
      timeout[0].cancel();
      if (!sent[0]) {
        sent[0] = true;
        onLocationRetrieved(location);
      }
    };
    timeout[0] = new CancellableRunnable() {
      @Override
      public void act () {
        if (!sent[0]) {
          sent[0] = true;
          try {
            LocationServices.FusedLocationApi.removeLocationUpdates(googleClient, listener);
          } catch (Throwable t) {
            Log.w("Error removeLocationUpdates", t);
          }
          Location location = null;
          try {
            location = LocationServices.FusedLocationApi.getLastLocation(googleClient);
          } catch (SecurityException ignored) { }
          catch (Throwable t) {
            Log.w("getLastLocation error", t);
          }
          if (location != null) {
            onLocationRetrieved(location);
          } else {
            onLocationFetchFailed();
          }
        }
      }
    };
    UI.post(timeout[0], LOCATION_MAX_WAIT_TIME);
    try {
      LocationRequest request = LocationRequest.create().setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY).setExpirationDuration(LOCATION_MAX_WAIT_TIME).setNumUpdates(1).setMaxWaitTime(5000L);
      LocationServices.FusedLocationApi.requestLocationUpdates(googleClient, request, listener);
    } catch (SecurityException ignored) {
      sent[0] = true;
      onLocationFetchFailed();
    } catch (Throwable t) {
      Log.w("requestLocationUpdates error", t);
      sent[0] = true;
      onLocationFetchFailed();
    }
  }
}
