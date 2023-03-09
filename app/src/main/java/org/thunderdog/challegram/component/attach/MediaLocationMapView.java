/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 21/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.ActivityPermissionResult;
import org.thunderdog.challegram.widget.CircleButton;
import org.thunderdog.challegram.widget.ShadowView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.CancellableRunnable;

public class MediaLocationMapView extends FrameLayoutFix implements OnMapReadyCallback, GoogleMap.OnMyLocationChangeListener, GoogleMap.OnCameraMoveListener, GoogleMap.OnCameraMoveStartedListener, GoogleMap.OnCameraIdleListener, GoogleMap.OnCameraMoveCanceledListener, View.OnClickListener, ActivityPermissionResult {
  public interface Callback {
    void onLocationUpdate (Location location, boolean custom, boolean gpsLocated, boolean preventRequest, boolean isSmallZoom);
    void onForcedLocationReset ();
  }

  private static final int FLAG_PAUSED = 0x01;
  private static final int FLAG_DESTROYED = 0x02;
  private static final int FLAG_SCHEDULE_INIT = 0x04;
  private static final int FLAG_CREATED = 0x08;
  private static final int FLAG_RESUMED = 0x10;

  // Data
  private Callback callback;
  private int flags;

  private boolean userMovingLocation;
  private boolean didFirstMove;
  private boolean ignoreMyLocation;
  private Location lastMyLocation;
  private Location currentLocation;

  // Children

  private MapView mapView;
  private ImageView pinView;
  private ImageView pinXView;
  private CircleButton myLocationButton;

  private @Nullable GoogleMap googleMap;

  public static int getMapHeight (boolean big) {
    int defaultSize = Screen.dp(150f);
    return big ? Math.max(Screen.smallestSide() - HeaderView.getSize(false) - Screen.dp(60f), defaultSize) : defaultSize;
  }

  public MediaLocationMapView (Context context) {
    super(context);
  }

  private MediaLocationPointView locationPointView;

  public void init (ViewController<?> themeProvider, MediaLocationPointView pointView, boolean big) {
    locationPointView = pointView;

    int mapHeight = getMapHeight(big);

    int mapPadding = 0; // Screen.dp(30f);
    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, mapHeight + mapPadding * 2);
    params.topMargin = -mapPadding;
    mapView = new MapView(getContext()) {
      @Override
      public boolean onInterceptTouchEvent (MotionEvent ev) {
        switch (ev.getAction()) {
          case MotionEvent.ACTION_DOWN: {
            onMapTouchDown();
            break;
          }
          case MotionEvent.ACTION_MOVE: {
            onMapTouchMove();
            break;
          }
          case MotionEvent.ACTION_UP: {
            onMapTouchUp();
            break;
          }
        }
        return super.onInterceptTouchEvent(ev);
      }
    };
    mapView.setLayoutParams(params);
    addView(mapView);

    pinXView = new ImageView(getContext());
    pinXView.setScaleType(ImageView.ScaleType.CENTER);
    pinXView.setImageResource(R.drawable.baseline_close_18);
    pinXView.setColorFilter(Theme.getColor(R.id.theme_color_icon, ThemeId.BLUE));
    pinXView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    addView(pinXView);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
    params.bottomMargin = Screen.dp(18f);
    pinView = new ImageView(getContext());
    pinView.setImageResource(R.drawable.ic_map_pin_44);
    pinView.setLayoutParams(params);
    pinView.setAlpha(0f);
    addView(pinView);
    updatePin();

    // My button

    int padding = Screen.dp(4f);
    params = FrameLayoutFix.newParams(Screen.dp(40f) + padding * 2, Screen.dp(40f) + padding * 2, Gravity.RIGHT | Gravity.BOTTOM);
    params.bottomMargin = Screen.dp(16f) - padding;
    params.rightMargin = Screen.dp(16f) - padding;


    myLocationButton = new CircleButton(getContext()) {
      @Override
      public boolean onTouchEvent (MotionEvent event) {
        return (event.getAction() != MotionEvent.ACTION_DOWN || (getAlpha() != 0f && !myLocationButtonAnimating)) && super.onTouchEvent(event);
      }
    };
    themeProvider.addThemeInvalidateListener(myLocationButton);
    myLocationButton.init(R.drawable.baseline_gps_fixed_24, 40f, 4f, R.id.theme_color_circleButtonOverlay, R.id.theme_color_circleButtonOverlayIcon);
    myLocationButton.setId(R.id.btn_gps);
    myLocationButton.setAlpha(0f);
    myLocationButton.setOnClickListener(this);
    myLocationButton.setLayoutParams(params);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && getContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      myLocationButton.setAlpha(1f);
    } else {
      checkLocationSettings(false, false);
    }
    addView(myLocationButton);

    // Shadow

    ShadowView shadowView = new ShadowView(getContext());
    shadowView.setSimpleTopShadow(true);
    params = FrameLayoutFix.newParams(shadowView.getLayoutParams());
    params.gravity = Gravity.BOTTOM;
    shadowView.setLayoutParams(params);
    themeProvider.addThemeInvalidateListener(shadowView);
    addView(shadowView);

    // Global

    setBackgroundColor(Theme.placeholderColor());
    themeProvider.addThemeBackgroundColorListener(this, R.id.theme_color_placeholder);
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, mapHeight, Gravity.TOP));

    // Google Maps initialization

    Background.instance().post(() -> {
      try {
        mapView.onCreate(null);
      } catch (Throwable ignored) {
        // initialized google shit
      }
      UI.post(() -> initMap());
    });
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_gps) {
      checkLocationSettings(true, false);
    }
  }

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  // Set custom position

  private boolean userForcedLocation;

  public void setMarkerPosition (double lat, double lng) {
    Location location = new Location("network");
    location.setLatitude(lat);
    location.setLongitude(lng);

    userForcedLocation = true;

    setIgnoreMyLocation(true);
    positionMarker(location, defaultZoomLevel());
  }

  private void clearForcedLocation () {
    if (userForcedLocation) {
      userForcedLocation = false;
      if (callback != null) {
        callback.onForcedLocationReset();
      }
    }
  }

  // Animations

  private double touchLatitude, touchLongitude;

  private void onMapTouchDown () {
    getParent().getParent().requestDisallowInterceptTouchEvent(true);
    if (googleMap != null) {
      LatLng latLng = googleMap.getCameraPosition().target;
      touchLatitude = latLng.latitude;
      touchLongitude = latLng.longitude;
    }
  }

  private void onMapTouchMove () {
    if (!userMovingLocation) {
      if (googleMap == null) {
        return;
      }
      LatLng latLng = googleMap.getCameraPosition().target;
      if (latLng.latitude == touchLatitude && latLng.longitude == touchLongitude) {
        return;
      }
      setUserMovingLocation(true);
      setIgnoreMyLocation(true);
    }
  }

  private boolean cameraMoving;

  private void onMapTouchUp () {
    saveLastLocation();
    getParent().getParent().requestDisallowInterceptTouchEvent(false);
    if (userMovingLocation) {
      if (!cameraMoving) {
        setUserMovingLocation(false);
      }
    }
  }

  private float pinFactor;
  private boolean animatingPin;
  private ValueAnimator pinAnimator;

  private void animatePinFactor (float toFactor) {
    if (pinView == null) {
      this.pinFactor = toFactor;
      return;
    }
    if (animatingPin) {
      animatingPin = false;
      if (pinAnimator != null) {
        pinAnimator.cancel();
        pinAnimator = null;
      }
    }
    if (this.pinFactor == toFactor) {
      return;
    }

    animatingPin = true;

    final float fromFactor = this.pinFactor;
    final float factorDiff = toFactor - fromFactor;

    pinAnimator = AnimatorUtils.simpleValueAnimator();
    pinAnimator.setDuration(120l);
    pinAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    pinAnimator.addUpdateListener(animation -> setPinFactor(fromFactor + factorDiff * AnimatorUtils.getFraction(animation)));
    pinAnimator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        animatingPin = false;
        MediaLocationMapView.this.pinAnimator = null;
      }
    });
    pinAnimator.start();
  }

  private void setPinFactor (float factor) {
    if (this.pinFactor != factor && animatingPin) {
      this.pinFactor = factor;
      updatePin();
    }
  }

  private void updatePin () {
    pinView.setTranslationY((float) -Screen.dp(10f) * pinFactor);
    pinXView.setAlpha(pinFactor);
  }

  // Callbacks

  public void onPauseMap () { // called when activity paused or bottom section changed
    if ((flags & FLAG_PAUSED) == 0) {
      flags |= FLAG_PAUSED;
      if ((flags & FLAG_CREATED) != 0) {
        pauseMap();
      }
    }
  }

  private void pauseMap () {
    try {
      mapView.onPause();
    } catch (Throwable ignored) { }
    flags &= ~FLAG_RESUMED;
  }

  private void resumeMap () {
    if ((flags & FLAG_RESUMED) == 0) {
      flags |= FLAG_RESUMED;
      try {
        mapView.onResume();
      } catch (Throwable ignored) { }
    }
  }

  public void onResumeMap () {
    if ((flags & FLAG_PAUSED) != 0) {
      flags &= ~FLAG_PAUSED;
      if ((flags & FLAG_SCHEDULE_INIT) != 0) {
        flags &= ~FLAG_SCHEDULE_INIT;
        initMap();
      } else if ((flags & FLAG_CREATED) != 0) {
        resumeMap();
      }
    }
  }

  public void onDestroyMap () {
    if ((flags & FLAG_DESTROYED) == 0) {
      flags |= FLAG_DESTROYED;
      try {
        mapView.onDestroy();
      } catch (Throwable ignored) { }
      if (googleClient != null) {
        try {
          googleClient.disconnect();
        } catch (Throwable ignored) { }
        googleClient = null;
      }
    }
  }

  // internal

  private void initMap () {
    if ((flags & FLAG_DESTROYED) != 0) {
      return;
    }
    if ((flags & FLAG_PAUSED) != 0) {
      flags |= FLAG_SCHEDULE_INIT;
      return;
    }
    flags |= FLAG_CREATED;
    try {
      mapView.onCreate(null);
      mapView.getMapAsync(this);
    } catch (Throwable ignored) { }
  }

  // Callbacks

  private boolean checkLocationPermission () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return getContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    return true;
  }

  @Override
  public void onMapReady (GoogleMap googleMap) {
    this.googleMap = googleMap;
    pinView.setAlpha(1f);

    try {
      if (checkLocationPermission()) {
        googleMap.setMyLocationEnabled(true);
      }
    } catch (Throwable t) {
      Log.e("No access to Google Play Services", t);
    }

    googleMap.getUiSettings().setMyLocationButtonEnabled(false);
    googleMap.getUiSettings().setZoomControlsEnabled(false);
    googleMap.getUiSettings().setCompassEnabled(false);
    googleMap.setOnMyLocationChangeListener(this);
    googleMap.setOnCameraMoveListener(this);
    googleMap.setOnCameraMoveStartedListener(this);
    googleMap.setOnCameraIdleListener(this);
    googleMap.setOnCameraMoveCanceledListener(this);

    if (currentLocation == null) {
      Location currentLocation = MediaLocationFinder.instance().getLastKnownLocation();
      if (currentLocation != null) {
        positionMarker(currentLocation);
      } else {
        double latitude = 45.924197260584734;
        double longitude = 6.870443522930145;
        float zoomLevel = googleMap.getMinZoomLevel();
        Settings.LastLocation location = Settings.instance().getViewedLocation();
        if (location != null) {
          latitude = location.latitude;
          longitude = location.longitude;
          zoomLevel = location.zoomOrAccuracy;
        }
        positionMarker(latitude, longitude, zoomLevel);
      }
    } else {
      positionMarker(currentLocation);
    }

    resumeMap();
  }

  private void saveLastLocation () {
    if (googleMap != null) {
      if (currentLocation == null) {
        currentLocation = new Location("network");
        currentLocation.setLatitude(googleMap.getCameraPosition().target.latitude);
        currentLocation.setLongitude(googleMap.getCameraPosition().target.longitude);
      }
      if (currentLocation != null) {
        Settings.instance().setViewedLocation(currentLocation.getLatitude(), currentLocation.getLongitude(), googleMap.getCameraPosition().zoom);
      }
    }
  }

  private float defaultZoomLevel () {
    return googleMap == null ? -1 : userForcedLocation ? googleMap.getMaxZoomLevel() - 3 : googleMap.getMaxZoomLevel() - 5;
  }

  private void positionMarker (Location location) {
    positionMarker(location, defaultZoomLevel());
  }

  private boolean isSmallZoom (float zoomLevel) {
    return googleMap == null || zoomLevel < googleMap.getMaxZoomLevel() - 10;
  }

  private void positionMarker (Location location, float zoomLevel) {
    positionMarkerInternal(location, zoomLevel);
    setShowMyLocationButton(userForcedLocation);
    if (callback != null) {
      callback.onLocationUpdate(location, userForcedLocation, lastMyLocation != null, userMovingLocation || userForcedLocation, false);
    }
  }

  // buttons

  private boolean myLocationButtonShowing;
  private boolean myLocationButtonAnimating;
  private ValueAnimator myLocationButtonAnimator;
  private float myLocationButtonFactor;

  private void setShowMyLocationButton (boolean show) {
    show = show || locationResolutionRequired;
    if (myLocationButtonShowing != show) {
      myLocationButtonShowing = show;
      animateMyLocationButtonFactor(show ? 1f : 0f);
    }
  }

  private void animateMyLocationButtonFactor (float toFactor) {
    if (myLocationButtonAnimating) {
      myLocationButtonAnimating = false;
      if (myLocationButtonAnimator != null) {
        myLocationButtonAnimator.cancel();
        myLocationButtonAnimator = null;
      }
    }
    if (this.myLocationButtonFactor == toFactor) {
      return;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && !isAttachedToWindow()) {
      this.myLocationButtonFactor = toFactor;
      myLocationButton.setAlpha(toFactor);
      return;
    }
    myLocationButtonAnimating = true;

    final float fromFactor = this.myLocationButtonFactor;
    final float factorDiff = toFactor - fromFactor;

    myLocationButtonAnimator = AnimatorUtils.simpleValueAnimator();
    myLocationButtonAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    myLocationButtonAnimator.setDuration(150l);
    myLocationButtonAnimator.addUpdateListener(animation -> setMyLocationButtonFactor(fromFactor + factorDiff * AnimatorUtils.getFraction(animation)));
    myLocationButtonAnimator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        myLocationButtonAnimating = false;
        myLocationButtonAnimator = null;
      }
    });
    myLocationButtonAnimator.start();
  }

  private void setMyLocationButtonFactor (float factor) {
    if (this.myLocationButtonFactor != factor && myLocationButtonAnimating) {
      this.myLocationButtonFactor = factor;
      myLocationButton.setAlpha(factor);
    }
  }

  private void focusOnMyLocation () {
    setIgnoreMyLocation(false, true);
  }

  private GoogleApiClient googleClient;
  private boolean noGoogleApiClient;
  private boolean locationResolutionRequired;

  public void onResolutionComplete (boolean isOk) {
    locationPointView.setShowProgress(isOk);
    if (isOk) {
      locationResolutionRequired = false;
      checkLocationSettings(true, false);
    }
  }

  @Override
  public void onPermissionResult (int code, String[] permissions, int[] grantResults, int grantCount) {
    if (permissions.length == grantCount) {
      checkLocationSettings(true, false);
    } else if (!UI.getContext(UI.getContext()).permissions().shouldShowAccessLocationRationale()) {
      Intents.openPermissionSettings();
    }
  }

  public void onLocationPermissionOk () {
    checkLocationSettings(true, false);
  }

  public void checkLocationSettings (final boolean requestedByUser, final boolean disablePrompts) {
    if (UI.getContext(getContext()).checkLocationPermissions(false) != PackageManager.PERMISSION_GRANTED) {
      locationPointView.setShowProgress(false);
      if (requestedByUser && !disablePrompts) {
        ((BaseActivity) getContext()).requestLocationPermission(false, false, this);
      } else {
        setShowMyLocationButton(true);
      }
      return;
    }

    if (googleMap != null) {
      googleMap.setMyLocationEnabled(true);
    }

    if (noGoogleApiClient) {
      locationPointView.setShowProgress(false);
      if (requestedByUser) {
        focusOnMyLocation();
      }
      return;
    }

    try {
      if (googleClient == null) {
        GoogleApiClient.Builder b = new GoogleApiClient.Builder(getContext());
        b.addApi(LocationServices.API);
        b.addOnConnectionFailedListener(connectionResult -> {
          if (!noGoogleApiClient) {
            noGoogleApiClient = true;
            checkLocationSettings(false, false);
          }
        });
        googleClient = b.build();
        googleClient.connect();
      }

      LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
          .addLocationRequest(LocationRequest.create())
          .setAlwaysShow(true);

      final LocationSettingsRequest request = builder.build();
      final PendingResult<LocationSettingsResult> result =
          LocationServices.SettingsApi.checkLocationSettings(googleClient, request);

      result.setResultCallback(result1 -> {
        final Status status = result1.getStatus();
        //final LocationSettingsStates state = result.getLocationSettingsStates();
        switch (status.getStatusCode()) {
          case LocationSettingsStatusCodes.RESOLUTION_REQUIRED: {
            if (!requestedByUser || disablePrompts) {
              setShowMyLocationButton(true);
              locationResolutionRequired = true;
              break;
            }

            try {
              status.startResolutionForResult(((BaseActivity) getContext()), Intents.ACTIVITY_RESULT_RESOLUTION);
            } catch (Throwable t) {
              // Ignore the error.
            }
            break;
          }
          case LocationSettingsStatusCodes.SUCCESS: {
            if (requestedByUser) {
              if (!result1.getLocationSettingsStates().isLocationUsable()) {
                locationPointView.setShowProgress(false);
              }
              focusOnMyLocation();
            }
            break;
          }
          case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
          default: {
            locationPointView.setShowProgress(false);
            if (requestedByUser) {
              focusOnMyLocation();
            }
            break;
          }
        }
      });
    } catch (Throwable t) {
      Log.w("Error", t);
      noGoogleApiClient = true;
      checkLocationSettings(requestedByUser, disablePrompts);
    }

    // GoogleApiClient client = new GoogleApiClient.Builder(getContext()).addApiIfAvailable(Drive).build();
  }

  // positioning

  private void positionMarker (double lat, double lng, float zoomLevel) {
    Location location = new Location("network");
    location.setLatitude(lat);
    location.setLongitude(lng);
    positionMarker(location, zoomLevel);
  }

  private void positionMarkerInternal (Location location, float zoomLevel) {
    if (location == null) {
      return;
    }

    currentLocation = location;

    if (userMovingLocation || googleMap == null) {
      return;
    }

    LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

    if (didFirstMove) {
      CameraUpdate position = userForcedLocation || !ignoreMyLocation ? CameraUpdateFactory.newLatLngZoom(latLng, zoomLevel) : CameraUpdateFactory.newLatLng(latLng);
      googleMap.animateCamera(position);
    } else {
      didFirstMove = true;
      CameraUpdate position = CameraUpdateFactory.newLatLngZoom(latLng, zoomLevel);
      googleMap.moveCamera(position);
    }
  }

  public Location getCurrentLocation () {
    return currentLocation;
  }

  private void setUserMovingLocation (boolean moving) {
    if (this.userMovingLocation != moving) {
      this.userMovingLocation = moving;
      animatePinFactor(moving ? 1f : 0f);
      if (!userMovingLocation) {
        postLocationUpdateDelayed();
        saveLastLocation();
      } else {
        clearForcedLocation();
        cancelLocationUpdate();
      }
    }
  }

  private CancellableRunnable updateTask;

  private void postLocationUpdateDelayed () {
    cancelLocationUpdate();
    updateTask = new CancellableRunnable() {
      @Override
      public void act () {
        if (ignoreMyLocation) {
          setShowMyLocationButton(true);
          if (callback != null) {
            callback.onLocationUpdate(currentLocation, true, lastMyLocation != null, userMovingLocation || userForcedLocation, googleMap == null || isSmallZoom(googleMap.getCameraPosition().zoom));
          }
        }
      }
    };
    postDelayed(updateTask, 400l);
  }

  private void cancelLocationUpdate () {
    if (updateTask != null) {
      updateTask.cancel();
      updateTask = null;
    }
  }

  private void setIgnoreMyLocation (boolean ignoreMyLocation) {
    setIgnoreMyLocation(ignoreMyLocation, false);
  }

  private void setIgnoreMyLocation (boolean ignoreMyLocation, boolean force) {
    if (this.ignoreMyLocation != ignoreMyLocation || force) {
      this.ignoreMyLocation = ignoreMyLocation;
      if (!ignoreMyLocation && lastMyLocation != null) {
        cancelLocationUpdate();
        clearForcedLocation();
        positionMarker(lastMyLocation);
        saveLastLocation();
      }
    }
  }

  @Override
  public void onMyLocationChange (Location location) {
    lastMyLocation = location;
    if (location != null) {
      Settings.instance().saveLastKnownLocation(location.getLatitude(), location.getLongitude(), location.getAccuracy());
    }
    if (!ignoreMyLocation) {
      setShowMyLocationButton(false);
      positionMarker(location);
      saveLastLocation();
    }
  }

  @Override
  public void onCameraMove () {
    if (userMovingLocation && googleMap != null) {
      LatLng latLng = googleMap.getCameraPosition().target;
      Location location = new Location("network");
      location.setLatitude(latLng.latitude);
      location.setLongitude(latLng.longitude);
      currentLocation = location;
      setShowMyLocationButton(true);
      if (callback != null) {
        callback.onLocationUpdate(location, true, lastMyLocation != null, userMovingLocation || userForcedLocation, true);
      }
    }
  }

  private void setCameraMoving (boolean moving) {
    if (this.cameraMoving != moving) {
      this.cameraMoving = moving;
      if (!cameraMoving) {
        if (userMovingLocation) {
          setUserMovingLocation(false);
        } else {
          saveLastLocation();
        }
      }
    }
  }

  @Override
  public void onCameraIdle () {
    setCameraMoving(false);
    saveLastLocation();
  }

  @Override
  public void onCameraMoveCanceled () {
    setCameraMoving(false);
  }

  private boolean ignoredFirstMove;

  @Override
  public void onCameraMoveStarted (int i) {
    if (ignoredFirstMove) {
      setCameraMoving(true);
    } else {
      ignoredFirstMove = true;
    }
  }
}
