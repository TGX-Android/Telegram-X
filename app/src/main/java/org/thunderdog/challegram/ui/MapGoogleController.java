package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.loader.ImageCache;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageLoader;
import org.thunderdog.challegram.loader.Watcher;
import org.thunderdog.challegram.loader.WatcherReference;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Letters;

import java.util.List;

import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.MessageId;

/**
 * Date: 3/8/18
 * Author: default
 */

public class MapGoogleController extends MapController<MapView, MapGoogleController.MarkerData> implements OnMapReadyCallback, GoogleMap.OnMyLocationChangeListener, GoogleMap.OnCameraMoveStartedListener, GoogleMap.OnMarkerClickListener {
  private static final float DEFAULT_ZOOM_LEVEL = 16.0f;
  private static final float CLICK_ZOOM_LEVEL = 17.0f;

  public MapGoogleController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  protected MapView createMapView (Context context, int marginBottom) {
    MapView mapView = new MapView(context);
    mapView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    mapView.setPadding(0, 0, 0, marginBottom);
    return mapView;
  }

  private static final float FINISHED_BROADCAST_ALPHA = .6f;

  public static class MarkerData implements Watcher, Destroyable {
    public final Tdlib tdlib;
    public Marker marker;

    private final WatcherReference reference = new WatcherReference(this);

    public Canvas canvas;
    public Bitmap bitmap;

    public MarkerData (Tdlib tdlib, GoogleMap googleMap, LocationPoint<MarkerData> point) {
      this.tdlib = tdlib;
      marker = googleMap.addMarker(newPoint(point));
      marker.setTag(point);
    }

    private MarkerOptions newPoint (LocationPoint<MarkerData> point) {
      LatLng latLng = new LatLng(point.latitude, point.longitude);
      MarkerOptions opts = new MarkerOptions();
      opts.position(latLng);
      Bitmap bitmap = null;
      if (point.isSelfLocation) {
        TdApi.User user = tdlib.myUser();
        int avatarColorId = tdlib.cache().userAvatarColorId(user);
        Letters letters = tdlib.cache().userLetters(user);
        TdApi.File avatar = user != null && user.profilePhoto != null ? user.profilePhoto.small : null;
        bitmap = newBitmap(this, avatarColorId, letters, avatar);
      } else if (point.isLiveLocation && point.message != null) {
        opts.zIndex(1f);
        this.isActive = ((TdApi.MessageLocation) point.message.content).expiresIn > 0;
        opts.alpha(isActive ? 1f : FINISHED_BROADCAST_ALPHA);
        bitmap = newBitmap(this, point.message);
      }
      if (bitmap != null) {
        opts.icon(BitmapDescriptorFactory.fromBitmap(bitmap));
        opts.anchor(0.5f, 0.907f);
      }
      return opts;
    }

    private @Nullable Bitmap newBitmap (MarkerData data, TdApi.Message message) {
      int avatarColorId;
      Letters letters;
      TdApi.File avatar;
      switch (message.sender.getConstructor()) {
        case TdApi.MessageSenderChat.CONSTRUCTOR: {
          TdApi.Chat chat = tdlib.chat(((TdApi.MessageSenderChat) message.sender).chatId);
          avatarColorId = tdlib.chatAvatarColorId(chat);
          letters = tdlib.chatLetters(chat);
          avatar = chat != null && chat.photo != null ? chat.photo.small : null;
          break;
        }
        case TdApi.MessageSenderUser.CONSTRUCTOR: {
          TdApi.User user = tdlib.cache().user(((TdApi.MessageSenderUser) message.sender).userId);
          avatarColorId = tdlib.cache().userAvatarColorId(user);
          letters = tdlib.cache().userLetters(user);
          avatar = user != null && user.profilePhoto != null ? user.profilePhoto.small : null;
          break;
        }
        default:
          throw new IllegalArgumentException(message.sender.toString());
      }
      return newBitmap(data, avatarColorId, letters, avatar);
    }

    private Drawable liveBackground;

    private static void drawAvatar (Canvas c, Bitmap bitmap) {
      BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
      Matrix matrix = new Matrix();
      float scale = Screen.dp(52) / (float) bitmap.getWidth();
      matrix.postTranslate(Screen.dp(5), Screen.dp(5));
      matrix.postScale(scale, scale);
      Paint roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      roundPaint.setShader(shader);
      shader.setLocalMatrix(matrix);
      RectF rect = Paints.getRectF();
      rect.set(Screen.dp(5), Screen.dp(5), Screen.dp(52 + 5), Screen.dp(52 + 5));
      c.drawRoundRect(rect, Screen.dp(26), Screen.dp(26), roundPaint);
    }

    private void drawAvatar (final MarkerData data, Canvas c, @ThemeColorId int avatarColorId, Letters letters, TdApi.File avatar) {
      int cx = Screen.dp(62) / 2;
      int cy = Screen.dp(62f) / 2;
      int radius = Screen.dp(26f);

      final ImageFile imageFile;
      if (avatar != null) {
        imageFile = new ImageFile(tdlib, avatar);
        imageFile.setSwOnly(true);
        imageFile.setSize(ChatView.getDefaultAvatarCacheSize());
        synchronized (ImageCache.getReferenceCounters()) {
          Bitmap avatarBitmap = ImageCache.instance().getBitmap(imageFile);
          if (U.isValidBitmap(avatarBitmap)) {
            drawAvatar(c, avatarBitmap);
            return;
          }
        }
      } else {
        imageFile = null;
      }

      c.drawCircle(cx, cy, radius, Paints.fillingPaint(Theme.getColor(avatarColorId)));
      Paint paint = Paints.whiteMediumPaint(19f, letters.needFakeBold, false);
      float textWidth = Paints.measureLetters(letters, 19f);
      c.drawText(letters.text, cx - textWidth / 2, cy + Screen.dp(6.5f), paint);

      data.requestFile(imageFile);
    }

    private @Nullable Bitmap newBitmap (MarkerData data, @ThemeColorId int avatarColorId, Letters letters, TdApi.File avatar) {
      Bitmap result = null;
      boolean success = false;
      try {
        if (liveBackground == null) {
          liveBackground = UI.getResources().getDrawable(R.drawable.bg_livepin);
          liveBackground.setBounds(0, 0, Screen.dp(62f), Screen.dp(76f));
        }
        result = Bitmap.createBitmap(Screen.dp(62), Screen.dp(76), Bitmap.Config.ARGB_8888);
        result.eraseColor(0);
        Canvas c = new Canvas(result);
        liveBackground.draw(c);

        data.canvas = c;
        data.bitmap = result;

        drawAvatar(data, c, avatarColorId, letters, avatar);
        success = true;
      } catch (Throwable t) {
        Log.w(t);
      }
      if (!success && result != null) {
        try {
          result.recycle();
        } catch (Throwable ignored) { }
        result = null;
      }
      return result;
    }

    public void setPosition (LocationPoint<MarkerData> point) {
      marker.setPosition(new LatLng(point.latitude, point.longitude));
      setActive(point.message == null || ((TdApi.MessageLocation) point.message.content).expiresIn > 0);
    }

    private boolean isActive = true;

    public void setActive (boolean isActive) {
      if (this.isActive != isActive) {
        this.isActive = isActive;
        marker.setAlpha(isActive ? 1f : FINISHED_BROADCAST_ALPHA);
      }
    }

    public void remove () {
      marker.remove();
    }

    @Override
    public void performDestroy () {
      requestFile(null);
    }

    private ImageFile requestedFile;

    public void requestFile (ImageFile imageFile) {
      if (this.requestedFile == null && imageFile == null) {
        return;
      }
      if (this.requestedFile != null && imageFile != null && this.requestedFile.accountId() == imageFile.accountId() && this.requestedFile.getId() == imageFile.getId()) {
        return;
      }
      if (this.requestedFile != null) {
        ImageLoader.instance().removeWatcher(reference);
      }
      this.requestedFile = imageFile;
      if (imageFile != null) {
        ImageLoader.instance().requestFile(imageFile, reference);
      }
    }

    private boolean isRequested (ImageFile file) {
      return this.requestedFile != null && this.requestedFile.getId() == file.getId() && this.requestedFile.accountId() == file.accountId();
    }

    @Override
    public void imageLoaded (final ImageFile file, boolean successful, Bitmap bitmap) {
      if (successful && isRequested(file) && canvas != null && U.isValidBitmap((Bitmap) bitmap)) {
        drawAvatar(canvas, (Bitmap) bitmap);
        UI.post(() -> {
          if (isRequested(file)) {
            marker.setIcon(BitmapDescriptorFactory.fromBitmap(this.bitmap));
          }
        });
      }
    }

    @Override
    public void imageProgress (ImageFile file, float progress) { }
  }

  @Override
  protected boolean needBackgroundMapInitialization (@NonNull MapView mapView) {
    return true;
  }

  @Override
  protected void initializeMap (@NonNull MapView mapView, boolean inBackground) {
    try {
      mapView.onCreate(null);
      if (!inBackground) {
        mapView.getMapAsync(this);
      }
    } catch (Throwable ignored) { }
  }

  @Override
  protected void resumeMap (@NonNull MapView mapView) {
    try { mapView.onResume(); } catch (Throwable ignored) { }
  }

  @Override
  protected void pauseMap (@NonNull MapView mapView) {
    try { mapView.onPause(); } catch (Throwable ignored) { }
  }

  @Override
  protected void destroyMap (@NonNull MapView mapView) {
    try { mapView.onPause(); } catch (Throwable ignored) { }
    try { mapView.onDestroy(); } catch (Throwable ignored) { }
  }

  @Override
  protected boolean onBuildDirectionTo (@NonNull MapView mapView, double latitude, double longitude) {
    return false;
  }

  @SuppressWarnings("MissingPermission")
  @Override
  protected boolean displayMyLocation (@NonNull MapView mapView) {
    if (googleMap != null) {
      try {
        googleMap.setMyLocationEnabled(true);
        return true;
      } catch (Throwable ignored) { }
    }
    return false;
  }

  private GoogleMap googleMap;

  private MapStyleOptions darkMapTheme;
  private MapStyleOptions getDarkMapTheme () {
    if (darkMapTheme == null) {
      darkMapTheme = MapStyleOptions.loadRawResourceStyle(context, R.raw.maps_night);
    }
    return darkMapTheme;
  }

  @Override
  protected void onApplyMapType (int oldType, int newType) {
    if (googleMap != null) {
      if (oldType == Settings.MAP_TYPE_DARK) {
        try { googleMap.setMapStyle(null); } catch (Throwable ignored) { }
      } else if (newType == Settings.MAP_TYPE_DARK) {
        try { googleMap.setMapStyle(getDarkMapTheme()); } catch (Throwable ignored) { }
      }
      switch (newType) {
        case Settings.MAP_TYPE_DEFAULT:
          googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
          break;
        case Settings.MAP_TYPE_DARK:
          googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
          break;
        case Settings.MAP_TYPE_TERRAIN:
          googleMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
          break;
        case Settings.MAP_TYPE_HYBRID:
        case Settings.MAP_TYPE_SATELLITE:
          googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
          break;
      }
    }
  }

  @Override
  public void onMapReady (GoogleMap googleMap) {
    if (isDestroyed()) {
      return;
    }

    this.googleMap = googleMap;

    googleMap.getUiSettings().setMyLocationButtonEnabled(false);
    googleMap.getUiSettings().setZoomControlsEnabled(false);
    googleMap.getUiSettings().setCompassEnabled(false);
    googleMap.setOnMyLocationChangeListener(this);
    googleMap.setOnMarkerClickListener(this);

    googleMap.setOnCameraMoveStartedListener(this);

    if (U.checkLocationPermission(context)) {
      try { googleMap.setMyLocationEnabled(true); } catch (Throwable ignored) { }
    }

    switch (mapType()) {
      case Settings.MAP_TYPE_DEFAULT:
        break;
      case Settings.MAP_TYPE_DARK:
        try { googleMap.setMapStyle(getDarkMapTheme()); } catch (Throwable ignored) { }
        break;
      case Settings.MAP_TYPE_TERRAIN:
        googleMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
        break;
      case Settings.MAP_TYPE_SATELLITE:
      case Settings.MAP_TYPE_HYBRID:
        googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        break;
    }

    List<LocationPoint<MarkerData>> points = pointsOfInterest();
    for (LocationPoint<MarkerData> point : points) {
      if (point.data != null) {
        point.data.setPosition(point);
      } else {
        point.data = new MarkerData(tdlib, googleMap, point);
      }
    }
    boolean isSharing = isSharingLiveLocation();
    if (isSharing) {
      LocationPoint<MarkerData> point = myLocation(false);
      if (point != null) {
        if (point.data != null) {
          point.data.setPosition(point);
        } else {
          point.data = new MarkerData(tdlib, googleMap, point);
        }
      }
    }
    googleMap.moveCamera(buildCamera(mapView(), null, false, getArgumentsStrict().mode == MODE_DROPPED_PIN));

    mapView().onResume();

    executeScheduledAnimation();
  }

  @Override
  protected void onPointOfInterestFocusStateChanged (LocationPoint<MarkerData> point, boolean isFocused) {
    if (point.data != null) {
      point.data.marker.setZIndex(isFocused ? 10f : point.isLiveLocation && point.message != null ? 1f : 0f);
    }
  }

  @Override
  protected void onPointOfInterestAdded (LocationPoint<MarkerData> point, int toIndex) {
    if (googleMap != null) {
      if (point.data != null) {
        point.data.setPosition(point);
      } else {
        point.data = new MarkerData(tdlib, googleMap, point);
      }
    }
  }

  @Override
  protected void onPointOfInterestRemoved (LocationPoint<MarkerData> point, int fromIndex) {
    if (point.data != null) {
      point.data.remove();
      point.data = null;
    }
  }

  @Override
  protected void onPointOfInterestCoordinatesChanged (LocationPoint<MarkerData> point, int index) {
    if (point.data != null) {
      point.data.setPosition(point);
    }
  }

  @Override
  protected void onPointOfInterestActiveStateMightChanged (LocationPoint<MarkerData> point, boolean isActive) {
    if (point.data != null) {
      point.data.setActive(isActive);
    }
  }

  private CameraUpdate buildCamera (MapView mapView, @Nullable LocationPoint<MarkerData> specificPoint, boolean needBearing, boolean onlyFocus) {
    LocationPoint<MarkerData> singlePoint = specificPoint;
    List<LocationPoint<MarkerData>> pointOfInterests = pointsOfInterest();
    LocationPoint<MarkerData> myLocation = myLocation(true);

    if (singlePoint == null) {
      int totalCount = pointOfInterests.size();
      if (myLocation != null) {
        totalCount++;
      }
      if (totalCount == 1) {
        singlePoint = myLocation != null ? myLocation : pointOfInterests.get(0);
      }
    }

    if (singlePoint != null) {
      CameraPosition.Builder b = new CameraPosition.Builder();
      b.target(new LatLng(singlePoint.latitude, singlePoint.longitude));
      float zoom = DEFAULT_ZOOM_LEVEL;
      if (specificPoint != null) {
        zoom = Math.max(googleMap.getCameraPosition().zoom, CLICK_ZOOM_LEVEL);
      }
      b.zoom(zoom);
      if (needBearing) {
        b.bearing(singlePoint.bearing);
        b.tilt(45f);
      }
      return CameraUpdateFactory.newCameraPosition(b.build());
    }

    LatLngBounds.Builder b = new LatLngBounds.Builder();
    if (myLocation != null) {
      b.include(new LatLng(myLocation.latitude, myLocation.longitude));
    }
    if (onlyFocus) {
      if (hasFocusPoint()) {
        LocationPoint<MarkerData> point = pointOfInterests.get(0);
        b.include(new LatLng(point.latitude, point.longitude));
      }
    } else {
      for (LocationPoint<MarkerData> point : pointOfInterests) {
        b.include(new LatLng(point.latitude, point.longitude));
      }
    }

    LatLngBounds tmpBounds = b.build();
    LatLng center = tmpBounds.getCenter();
    int bound = 111;
    LatLng northEast = move(center, bound, bound);
    LatLng southWest = move(center, -bound, -bound);
    b.include(southWest);
    b.include(northEast);

    LatLngBounds bounds = b.build();

    int mapWidth = mapView.getMeasuredWidth();
    int mapHeight = mapView.getMeasuredHeight();

    if (mapWidth == 0 || mapHeight == 0) {
      mapWidth = context.getContentView().getMeasuredWidth();
      mapHeight = context.getContentView().getMeasuredHeight();
    }

    if (mapWidth == 0 || mapHeight == 0) {
      mapWidth = Screen.currentWidth();
      mapHeight = Screen.currentHeight();
    }

    return CameraUpdateFactory.newLatLngBounds(bounds, mapWidth, mapHeight, Screen.dp(82f));
  }

  private static final double EARTH_RADIUS = 6366198;
  /**
   * Create a new LatLng which lies toNorth meters north and toEast meters
   * east of startLL
   */
  private static LatLng move(LatLng startLL, double toNorth, double toEast) {
    double lonDiff = meterToLongitude(toEast, startLL.latitude);
    double latDiff = meterToLatitude(toNorth);
    return new LatLng(startLL.latitude + latDiff, startLL.longitude
      + lonDiff);
  }

  private static double meterToLongitude(double meterToEast, double latitude) {
    double latArc = Math.toRadians(latitude);
    double radius = Math.cos(latArc) * EARTH_RADIUS;
    double rad = meterToEast / radius;
    return Math.toDegrees(rad);
  }


  private static double meterToLatitude(double meterToNorth) {
    double rad = meterToNorth / EARTH_RADIUS;
    return Math.toDegrees(rad);
  }

  @Override
  protected boolean onPositionRequested (@NonNull MapView mapView, @Nullable LocationPoint<MarkerData> point, boolean animated, boolean needBearing, boolean onlyFocus) {
    if (googleMap != null) {
      CameraUpdate cameraUpdate = buildCamera(mapView, point, needBearing, onlyFocus);
      if (animated) {
        googleMap.animateCamera(cameraUpdate);
        return true;
      } else {
        googleMap.moveCamera(cameraUpdate);
      }
    }
    return false;
  }

  @Override
  public void onMyLocationChange (Location location) {
    Settings.instance().saveLastKnownLocation(location.getLatitude(), location.getLongitude(), location.getAccuracy());
    setMyLocation(location);
  }

  @Override
  public boolean onMarkerClick (Marker marker) {
    LocationPoint<MarkerData> point = (LocationPoint<MarkerData>) marker.getTag();
    if (point != null) {
      long chatId = 0;
      long messageId = 0;
      if (point.message != null) {
        chatId = point.message.chatId;
        messageId = point.message.id;
      } else if (point.isSelfLocation) {
        chatId = getArgumentsStrict().chatId;
        TdApi.Message outputMessage = tdlib.cache().findOutputLiveLocationMessage(chatId);
        if (outputMessage != null) {
          messageId = outputMessage.id;
        }
      }
      if (chatId != 0 && messageId != 0) {
        tdlib.ui().openChat(this, chatId, new TdlibUi.ChatOpenParameters().highlightMessage(new MessageId(chatId, messageId)).ensureHighlightAvailable());
      }
    }
    return true;
  }

  @Override
  public void onCameraMoveStarted (int reason) {
    if (reason == REASON_GESTURE) {
      onUserMovedCamera();
    }
  }

  @Override
  protected boolean wouldRememberMapType (int newMapType) {
    switch (newMapType) {
      case Settings.MAP_TYPE_HYBRID:
      case Settings.MAP_TYPE_SATELLITE:
        return false;
    }
    return true;
  }

  @Override
  protected int[] getAvailableMapTypes () {
    return new int[] {
      Settings.MAP_TYPE_DEFAULT,
      Settings.MAP_TYPE_DARK,
      Settings.MAP_TYPE_SATELLITE,
      Settings.MAP_TYPE_TERRAIN
    };
  }

  /*private boolean hasBearing;
  private float bearing;
  private FusedLocationProviderClient bearingClient;
  private LocationCallback bearingCallback;*/

  @Override
  protected boolean onStartPeriodicBearingUpdates (@NonNull MapView mapView) {
    return false;
    /*if (!U.checkLocationPermission(context)) {
    }
    if (bearingCallback == null) {
      bearingCallback = new com.google.android.gms.location.LocationCallback() {
        @Override
        public void onLocationResult (LocationResult locationResult) {
          super.onLocationResult(locationResult);
          Location location = locationResult.getLastLocation();
          hasBearing = true;
          bearing = location.getBearing();
          location.
          setMyLocation(location);
        }
      };
    }
    LocationRequest request = LocationRequest.create()
      .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
      .setInterval(2000)
      .setFastestInterval(500);
    bearingClient = LocationServices.getFusedLocationProviderClient((Context) context);
    try {
      bearingClient.requestLocationUpdates(request, bearingCallback, Looper.getMainLooper());
      return true;
    } catch (Throwable ignored) {
      bearingClient = null;
    }
    return false;*/
  }

  @Override
  protected void onFinishPeriodicBearingUpdates (@NonNull MapView mapView) {
    /*if (bearingClient != null) {
      try { bearingClient.removeLocationUpdates(bearingCallback); } catch (Throwable ignored) { }
      bearingClient = null;
    }
    hasBearing = false;
    bearing = 0f;*/
  }
}
