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
 * File created on 19/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.helper.LocationHelper;
import org.thunderdog.challegram.navigation.ActivityResultHandler;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.util.CancellableResultHandler;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.ShadowView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.td.ChatId;

public class MediaBottomLocationController extends MediaBottomBaseController<Void> implements View.OnClickListener, MediaLocationMapView.Callback, MediaLocationFinder.Callback, ActivityResultHandler, Menu, FactorAnimator.Target {
  public MediaBottomLocationController (MediaLayout context) {
    super(context, R.string.Location);
  }

  @Override
  public int getId () {
    return R.id.controller_media_location;
  }

  @Override
  protected int getMenuId () {
    return mediaLayout.inSpecificMode() ? 0 : R.id.menu_search;
  }

  @Override
  public int getBroadcastingAction () {
    return TdApi.ChatActionChoosingLocation.CONSTRUCTOR;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_search) {
      header.addSearchButton(menu, this);
    } else if (id == R.id.menu_clear) {
      header.addClearButton(menu, this);
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_search) {
      if (firstLoaded && currentLocation != null) {
        mediaLayout.getHeaderView().openSearchMode();
        headerView = mediaLayout.getHeaderView();
        hideMap();
      }
    } else if (id == R.id.menu_btn_clear) {
      clearSearchInput();
    }
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    return (mapAnimator != null && mapAnimator.isAnimating()) || super.onBackPressed(fromTop);
  }

  @Override
  protected int getSearchHint () {
    return R.string.SearchForPlaces;
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  // private MediaLocationAdapter adapter;
  private SettingsAdapter settingsAdapter;

  private FrameLayoutFix mapWrap;
  private MediaLocationMapView mapView;
  private MediaLocationPointView locationView;
  private TextView textView, dataView;

  @Override
  protected void onRecyclerTopUpdate (float top) {
    super.onRecyclerTopUpdate(top);
    mapWrap.setTranslationY(top);
  }

  private ListItem paddingItem, liveItem;
  private int totalHeaderSize;

  @Override
  protected View onCreateView (Context context) {
    final int mapHeight = MediaLocationMapView.getMapHeight(mediaLayout.inSpecificMode());
    final int barHeight = SettingHolder.measureHeightForType(ListItem.TYPE_ATTACH_LOCATION);
    final int marginOffset = Screen.dp(4f);
    final int totalHeaderSize = this.totalHeaderSize = mapHeight + barHeight + marginOffset;

    // Map wrap

    FrameLayoutFix.LayoutParams params;

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, totalHeaderSize + ShadowView.simpleBottomShadowHeight(), Gravity.TOP);
    params.topMargin = HeaderView.getSize(false);

    mapWrap = new FrameLayoutFix(context) {
      @Override
      public boolean onTouchEvent (MotionEvent event) {
        boolean res = super.onTouchEvent(event);
        return event.getAction() == MotionEvent.ACTION_DOWN || res;
      }
    };
    mapWrap.setLayoutParams(params);

    // Recycler

    buildContentView(false);
    recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, MOVE_MAP_DURATION));
    LinearLayoutManager manager = new LinearLayoutManager(context, RecyclerView.VERTICAL, false);

    settingsAdapter = new SettingsAdapter(this) {
      @Override
      protected void setPlace (ListItem item, int position, MediaLocationPlaceView view, boolean isUpdate) {
        final int itemId = item.getId();
        if (itemId == R.id.btn_shareLiveLocation) {
          view.setDefaultLiveLocation(true);
          view.setInProgress(sendingLiveLocation, isUpdate);
          view.setEnabled(!sendingLiveLocation);
        } else if (itemId == R.id.place) {
          view.setInProgress(false, false);
          MediaLocationData place = (MediaLocationData) item.getData();
          boolean isChecked = selectedPlace != null && selectedPlace.equals(place);
          if (isUpdate) {
            view.setChecked(isChecked, true);
          } else {
            view.setLocation(place, isChecked);
          }
        }
      }
    };
    ArrayList<ListItem> items = new ArrayList<>();
    items.add(paddingItem = new ListItem(ListItem.TYPE_PADDING).setHeight(totalHeaderSize));
    if (!mediaLayout.inSpecificMode() && !ChatId.isSecret(mediaLayout.getTargetChatId()) && !mediaLayout.areScheduledOnly()) {
      items.add(liveItem = new ListItem(ListItem.TYPE_ATTACH_LOCATION, R.id.btn_shareLiveLocation));
    }
    items.add(new ListItem(ListItem.TYPE_HEADER, R.id.btn_places, 0, R.string.PullToSeePlaces).setTextColorId(ColorId.textLight));
    settingsAdapter.setItems(items, false);

    // adapter = new MediaLocationAdapter(context, manager, totalHeaderSize, this, this);
    setLayoutManager(manager);
    setAdapter(settingsAdapter);

    // Bar

    final int buttonPadding = Screen.dp(12f);

    int leftWidth = Screen.dp(20f) + Screen.dp(20f) * 2 + buttonPadding;
    params = FrameLayoutFix.newParams(leftWidth, barHeight, Gravity.LEFT | Gravity.TOP);
    params.topMargin = mapHeight + marginOffset;

    locationView = new MediaLocationPointView(context);
    locationView.setPadding(Screen.dp(20f), 0, buttonPadding, 0);
    locationView.setLayoutParams(params);
    Views.setClickable(locationView);

    // Map

    mapView = new MediaLocationMapView(context);
    mapView.init(this, locationView, mediaLayout.inSpecificMode());
    mapView.setCallback(this);
    mapWrap.addView(mapView);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, barHeight + marginOffset);
    params.topMargin = mapHeight;
    View view = new View(context);
    view.setId(R.id.btn_send);
    RippleSupport.setSimpleWhiteBackground(view, this);
    view.setLayoutParams(params);
    view.setOnClickListener(this);
    Views.setClickable(view);
    mapWrap.addView(view);

    mapWrap.addView(locationView);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, barHeight, Gravity.LEFT | Gravity.TOP);
    params.topMargin = mapHeight + marginOffset;
    params.leftMargin = leftWidth;

    FrameLayoutFix textWrap = new FrameLayoutFix(context);
    textWrap.setPadding(Screen.dp(80f) - leftWidth, 0, 0, 0);
    textWrap.setLayoutParams(params);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
    // params.bottomMargin = Screen.dp(10f);
    params.rightMargin = Screen.dp(12f);

    textView = new NoScrollTextView(context);
    textView.setTypeface(Fonts.getRobotoMedium());
    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    textView.setTextColor(Theme.textAccentColor());
    addThemeTextAccentColorListener(textView);
    textView.setSingleLine(true);
    textView.setGravity(Gravity.CENTER_VERTICAL);
    textView.setEllipsize(TextUtils.TruncateAt.END);
    textView.setText(Lang.getString(R.string.Locating));
    textView.setLayoutParams(params);
    textWrap.addView(textView);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
    params.topMargin = Screen.dp(10f);
    params.rightMargin = Screen.dp(12f);

    dataView = new NoScrollTextView(context);
    dataView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    dataView.setTextColor(Theme.textDecentColor());
    addThemeTextDecentColorListener(ColorId.textLight);
    dataView.setTypeface(Fonts.getRobotoRegular());
    dataView.setEllipsize(TextUtils.TruncateAt.END);
    dataView.setSingleLine(true);
    dataView.setLayoutParams(params);
    dataView.setAlpha(0f);
    textWrap.addView(dataView);

    mapWrap.addView(textWrap);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ShadowView.simpleBottomShadowHeight(), Gravity.TOP);
    params.topMargin = mapHeight + barHeight + marginOffset;

    ShadowView shadowView = new ShadowView(context);
    shadowView.setSimpleBottomTransparentShadow(true);
    shadowView.setLayoutParams(params);
    shadowView.setAlpha(0f);
    addThemeInvalidateListener(shadowView);
    mapWrap.addView(shadowView);
    settingsAdapter.addBoundShadow(recyclerView, shadowView, Screen.dp(10f));

    contentView.addView(mapWrap);

    return contentView;
  }

  @Override
  public void onRecyclerFirstMovement () {
    this.allowLocationRequests = true;
    if (currentLocation != null) {
      searchPlaces(currentLocation, false);
    }
  }

  // Callbacks

  @Override
  public void onActivityResult (int requestCode, int resultCode, Intent data) {
    if (requestCode == Intents.ACTIVITY_RESULT_RESOLUTION) {
      mapView.onResolutionComplete(resultCode == Activity.RESULT_OK);
    }
  }

  @Override
  protected void onCompleteShow (boolean isPopup) {
    super.onCompleteShow(isPopup);

    if (mapView != null) {
      mapView.checkLocationSettings(true, false);
    }
  }

  public void onLocationPermissionOk () {
    mapView.onLocationPermissionOk();
  }

  @Override
  protected boolean canExpandHeight () {
    return !mediaLayout.inSpecificMode(); // FIXME?
  }

  private boolean sendingLiveLocation;

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.place) {
      ListItem item = (ListItem) v.getTag();
      MediaLocationData data = (MediaLocationData) item.getData();
      selectPlace(data, true);
    } else if (viewId == R.id.btn_send) {
      if (selectedPlace != null) {
        mediaLayout.sendVenue(selectedPlace);
      } else {
        Location location = mapView.getCurrentLocation();
        if (location != null) {
          mediaLayout.sendLocation(location.getLatitude(), location.getLongitude(), location.getAccuracy(), U.getHeading(location), 0);
        }
      }
    } else if (viewId == R.id.btn_shareLiveLocation) {
      if (!sendingLiveLocation) {
        /*if (lastKnownLocation != null && U.timeSinceGenerationMs(lastKnownLocation) <= 150000l *//*2.5 minutes*//*) {
            openLiveLocationAlert();
            return;
          }*/
        sendingLiveLocation = true;
        settingsAdapter.updateValuedSettingById(R.id.btn_shareLiveLocation);
        LocationHelper.requestLocation(context, 15000l, true, true, (errorCode, location) -> {
          if (isDestroyed()) {
            return;
          }
          sendingLiveLocation = false;
          settingsAdapter.updateValuedSettingById(R.id.btn_shareLiveLocation);
          switch (errorCode) {
            case LocationHelper.ERROR_CODE_PERMISSION: {
              Intents.openPermissionSettings();
              break;
            }
            case LocationHelper.ERROR_CODE_RESOLUTION: {
              ViewController<?> c = context.navigation().getCurrentStackItem();
              if (c != null) {
                c.openMissingLocationPermissionAlert(true);
              }
              break;
            }
            case LocationHelper.ERROR_CODE_NONE: {
              if (location != null) {
                if (lastKnownLocation == null) {
                  lastKnownLocation = U.newFakeLocation();
                }
                lastKnownLocation.set(location);
              }
              openLiveLocationAlert();
              break;
            }
            case LocationHelper.ERROR_CODE_PERMISSION_CANCEL: {
              break;
            }
            default: {
              UI.showToast(R.string.DetectLocationError, Toast.LENGTH_SHORT);
              break;
            }
          }
        });
      }
    }
  }

  private void openLiveLocationAlert () {
    if (lastKnownLocation != null) {
      long chatId = mediaLayout.getTargetChatId();
      openLiveLocationAlert(chatId, arg1 -> mediaLayout.sendLocation(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude(), lastKnownLocation.getAccuracy(), U.getHeading(lastKnownLocation), arg1));
    }
  }

  private Location currentLocation;
  private Location lastLocation;
  private CancellableResultHandler lastLocationCall;

  private static final int ANIMATOR_DATA = 1;
  private BoolAnimator dataAnimator = new BoolAnimator(ANIMATOR_DATA, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  private void setLocationData (String locationData) {
    dataAnimator.setValue(!StringUtils.isEmpty(locationData), true);
    if (!StringUtils.isEmpty(locationData)) {
      dataView.setText(locationData);
    }
  }

  private boolean allowLocationRequests;

  private Location lastKnownLocation;

  @Override
  public void onLocationUpdate (Location location, boolean custom, boolean gpsLocated, boolean preventRequests, boolean isSmallZoom) {
    if (gpsLocated) {
      locationView.setShowProgress(false);
      if (!custom) {
        lastKnownLocation = location;
      }
    }
    locationView.setIsCustom(custom);
    if (custom) {
      if (selectedPlace == null) {
        textView.setText(Lang.getString(R.string.SendCurrentLocation));
        locationView.setIsPlace(false);
        setLocationData(MathUtils.roundDouble(location.getLatitude()) + ", " + MathUtils.roundDouble(location.getLongitude()));
      }
    } else {
      textView.setText(Lang.getString(R.string.SendCurrentLocation));
      locationView.setIsPlace(false);
      setLocationData(location.getAccuracy() > 0 ? Lang.plural(R.string.AccurateToMeters, (int) location.getAccuracy()) : null);
    }
    currentLocation = location;
    if (mediaLayout.inSpecificMode()) {
      lastLocation = currentLocation;
    } else if (allowLocationRequests) {
      if (!preventRequests && (lastLocation == null || lastLocation.distanceTo(location) >= 200) || !hasPlaces()) {
        searchPlaces(location, isSmallZoom);
      }
    }
  }

  private void searchPlaces (Location location, boolean isSmallZoom) {
    if (lastLocationCall != null) {
      lastLocationCall.cancel();
      lastLocationCall = null;
    }
    if (isSmallZoom) {
      setPlaces(null, showingPlacesFound);
      setPlacesHeader(0);
    } else {
      setPlacesHeader(R.string.LoadingPlaces);
      lastLocation = location;
      lastLocationCall = MediaLocationFinder.findNearbyPlaces(tdlib, mediaLayout.getTargetChatId(), location, null, this);
    }
  }

  @Override
  public void onForcedLocationReset () {
    selectPlace(null, true);
  }

  private boolean firstLoaded;
  private List<MediaLocationData> places;

  @Override
  public void onNearbyPlacesLoaded (CancellableResultHandler call, Location location, long queryId, List<MediaLocationData> result, @Nullable String nextOffset) {
    if (this.lastLocationCall == call) {
      this.lastLocationCall = null;
    }
    if (isDestroyed() || this.lastLocation == null || this.lastLocation != location || this.lastLocation.getLongitude() != location.getLongitude() || this.lastLocation.getLatitude() != location.getLatitude()) {
      return;
    }
    this.places = result;
    if (!mapHidden) {
      showNearbyPlaces();
    }
    setPlacesHeader(places != null && !places.isEmpty() ? (showingFoundPlaces ? R.string.FoundPlaces : R.string.NearbyPlaces) : R.string.NoPlacesFound);
    firstLoaded = true;
    /*if (!firstLoaded && result != null && !result.isEmpty()) {
      firstLoaded = true;
      expandStartHeight(settingsAdapter);
    }*/
  }

  private void setPlacesHeader (int headerRes) {
    setPlacesHeader(headerRes != 0 ? Lang.getString(headerRes) : "");
  }

  private void setPlacesHeader (CharSequence str) {
    int i = settingsAdapter.indexOfViewById(R.id.btn_places);
    if (i != -1) {
      if (settingsAdapter.getItems().get(i).setStringIfChanged(str)) {
        settingsAdapter.notifyItemChanged(i);
      }
    }
  }

  @Override
  public void onNearbyPlacesErrorLoading (CancellableResultHandler call, Location location, TdApi.Error error) {
    if (this.lastLocationCall == call) {
      this.lastLocationCall = null;
    }
    if (isDestroyed() || this.lastLocation == null || this.lastLocation != location || this.lastLocation.getLongitude() != location.getLongitude() || this.lastLocation.getLatitude() != location.getLatitude()) {
      return;
    }
    this.places = null;
    if (!mapHidden) {
      showNearbyPlaces();
    }
    if (error == null) {
      setPlacesHeader(R.string.PlaceSearchError);
    } else {
      setPlacesHeader(Lang.getString(R.string.PlaceSearchError) + ": " + TD.toErrorString(error));
    }
  }

  // State

  @Override
  public void onActivityResume () {
    super.onActivityResume();
    mapView.onResumeMap();
  }

  @Override
  public void onActivityPause () {
    super.onActivityPause();
    mapView.onPauseMap();
  }

  @Override
  public void destroy () {
    super.destroy();
    mapView.onDestroyMap();
  }

  /*@Override
  protected int getMaxStartHeightLimit () {
    return MediaBottomBar.getBarHeight() + ;
  }*/

  /*@Override
  protected int getInitialContentHeight () {
    return Screen.dp(160f) + MediaLocationMapView.getMapHeight(mediaLayout.inLocationMode());
  }*/

  @Override
  protected int getInitialContentHeight () {
    return getMapWrapHeight() + (mediaLayout.inSpecificMode() || ChatId.isSecret(mediaLayout.getTargetChatId()) || mediaLayout.areScheduledOnly() ? 0 : SettingHolder.measureHeightForType(ListItem.TYPE_ATTACH_LOCATION)) + SettingHolder.measureHeightForType(ListItem.TYPE_HEADER) + Screen.dp(10f);
  }

  private boolean mapHidden;

  private void hideMap () {
    setMapHidden(true);
  }

  private void showMap () {
    setMapHidden(false);
  }

  private void setMapHidden (boolean hidden) {
    if (this.mapHidden != hidden) {
      this.mapHidden = hidden;
      if (true) {
        return;
      }

      useTranslation = hasPlaces();
      if (hidden) {
        selectPlace(null, true);
      } else {
        if (useTranslation) {
          setHidePadding(false);
        } else {
          showNearbyPlaces();
        }
        mapView.onResumeMap();
        mapWrap.setVisibility(View.VISIBLE);
      }

      animateMapFactor(hidden ? 0f : 1f);
    }
  }

  private List<MediaLocationData> showingPlaces;
  private boolean showingPlacesFound;

  public void setPlaces (List<MediaLocationData> places, boolean areFound) {
    List<MediaLocationData> showingPlaces = this.showingPlaces;
    boolean oldPlacesFound = this.showingPlacesFound;
    int oldItemCount = showingPlaces != null && !showingPlaces.isEmpty() ? showingPlaces.size() : 0;
    int newItemCount = places != null && !places.isEmpty() ? places.size() : 0;
    this.showingPlaces = places;
    this.showingPlacesFound = areFound;
    if (oldItemCount == 0 && newItemCount == 0) {
      return;
    }
    if (liveItem != null) {
      if (!oldPlacesFound && areFound) {
        settingsAdapter.removeItemById(R.id.btn_shareLiveLocation);
      } else if (oldPlacesFound && !areFound) {
        settingsAdapter.addItem(1, liveItem);
      }
    }
    final int headerItemCount = areFound || liveItem == null ? 2 : 3;
    if (newItemCount == 0) {
      settingsAdapter.removeRange(headerItemCount, oldItemCount);
      if (areFound) {
        setPlacesHeader(R.string.NoPlacesFound);
      }
      selectPlace(null, true);
      return;
    }
    setPlacesHeader(areFound ? R.string.FoundPlaces : R.string.NearbyPlaces);
    for (int i = headerItemCount + oldItemCount - 1; i >= headerItemCount; i--) {
      settingsAdapter.getItems().remove(i);
    }
    for (MediaLocationData location : places) {
      settingsAdapter.getItems().add(new ListItem(ListItem.TYPE_ATTACH_LOCATION, R.id.place).setData(location));
    }
    if (areFound) {
      selectPlace(places.get(0), false);
    }
    if (oldItemCount == 0) {
      settingsAdapter.notifyItemRangeInserted(headerItemCount, newItemCount);
    } else {
      settingsAdapter.notifyItemRangeChanged(headerItemCount, Math.min(oldItemCount, newItemCount));
      if (oldItemCount < newItemCount) {
        settingsAdapter.notifyItemRangeInserted(headerItemCount + oldItemCount, newItemCount - oldItemCount);
      } else if (oldItemCount > newItemCount) {
        settingsAdapter.notifyItemRangeRemoved(headerItemCount + newItemCount, oldItemCount - newItemCount);
      }
    }
    ((LinearLayoutManager) getLayoutManager()).scrollToPositionWithOffset(0, 0);
  }

  private void setHidePadding (boolean hidePadding) {
    ListItem item = settingsAdapter.getItems().get(0);
    int desiredHeight = hidePadding ? Screen.dp(4f) : totalHeaderSize;
    if (item.getHeight() != desiredHeight) {
      LinearLayoutManager manager = (LinearLayoutManager) getLayoutManager();
      int savedPosition = manager.findFirstVisibleItemPosition();
      View view = manager.findViewByPosition(savedPosition);
      int savedOffset = view != null ? view.getTop() : 0;
      item.setHeight(desiredHeight);
      settingsAdapter.notifyItemChanged(0);
      if (savedPosition != -1) {
        manager.scrollToPositionWithOffset(savedPosition, savedOffset);
      }
    }
  }

  private boolean hasPlaces () {
    return places != null && !places.isEmpty();
  }

  private MediaLocationData selectedPlace;
  private void selectPlace (MediaLocationData place, boolean notify) {
    /*if (mapHidden) {
      if (place != null) {
        mediaLayout.sendVenue(place);
      }
      return;
    }*/
    if ((selectedPlace != null && selectedPlace.equals(place) && notify)) {
      return;
    }
    MediaLocationData oldLocation = selectedPlace;
    selectedPlace = place;
    if (notify) {
      if (place != null) {
        settingsAdapter.updateValuedSettingByData(place);
      }
      if (oldLocation != null) {
        settingsAdapter.updateValuedSettingByData(oldLocation);
      }
    }

    if (place != null) {
      textView.setText(place.getTitle());
      locationView.setIsPlace(true);
      setLocationData(place.getAddress());
      mapView.setMarkerPosition(place.getLatitude(), place.getLongitude());
    }
  }

  private static final int ANIMATOR_MAP = 0;
  private FactorAnimator mapAnimator;

  private int getMapWrapHeight () {
    int mapHeight = MediaLocationMapView.getMapHeight(mediaLayout.inSpecificMode());
    int barHeight = SettingHolder.measureHeightForType(ListItem.TYPE_ATTACH_LOCATION);
    return mapHeight + barHeight;
  }

  private int mapHeight;
  private int mapLastScrollY;
  private int mapStartOffset;
  private boolean useTranslation;

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_MAP: {
        if (useTranslation) {
          int offset = (int) ((float) mapHeight * (1f - factor));
          mapWrap.setTranslationY(-offset);

          int scrollY = offset - mapStartOffset; // (int) ((float) mapHeight * fraction) * (mapHidden ? 1 : -1);
          recyclerView.scrollBy(0, scrollY - mapLastScrollY);
          mapLastScrollY = scrollY;
        } else {
          mapWrap.setAlpha(factor);
        }

        break;
      }
      case ANIMATOR_DATA: {
        float ty = Screen.dp(10f);
        textView.setTranslationY(-ty * factor);
        dataView.setTranslationY(ty * (1f - factor));
        dataView.setAlpha(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_MAP: {
        if (finalFactor == 0f) {
          mapWrap.setVisibility(View.GONE);
          mapView.onPauseMap();
          setHidePadding(true);
        } else if (finalFactor == 1f && showingPlacesFound) {
          showNearbyPlaces();
        }
        break;
      }
    }
  }

  private static final long MOVE_MAP_DURATION = 160l;

  private void animateMapFactor (float factor) {
    if (mapAnimator == null) {
      mapAnimator = new FactorAnimator(ANIMATOR_MAP, this, AnimatorUtils.DECELERATE_INTERPOLATOR, MOVE_MAP_DURATION, 1f);
    }
    mapHeight = getMapWrapHeight();
    mapStartOffset = (int) ((float) mapHeight * (1f - mapAnimator.getFactor()));
    //noinspection ResourceType
    mapWrap.setTranslationY(useTranslation ? -mapStartOffset : 0);
    mapWrap.setAlpha(useTranslation ? 1f : mapAnimator.getFactor());
    mapLastScrollY = 0;
    mapAnimator.animateTo(factor);
  }

  @Override
  protected void onSearchInputChanged (String query) {
    searchPlaces(query.trim().toLowerCase());
  }

  @Override
  protected void onLeaveSearchMode () {
    lastQuery = "";
    showMap();
  }

  private String lastQuery = "";
  private CancellableRunnable lastSearchTask;
  private CancellableResultHandler lastSearchCall;

  private boolean showingFoundPlaces;

  private void showNearbyPlaces () {
    showingFoundPlaces = false;
    setPlaces(places, false);
  }

  private void showFoundPlaces (List<MediaLocationData> places) {
    showingFoundPlaces = true;
    setPlaces(places, true);
  }

  private void searchPlaces (final String q) {
    if (lastQuery.equals(q)) {
      return;
    }
    lastQuery = q;
    if (lastSearchTask != null) {
      lastSearchTask.cancel();
    }
    if (lastSearchCall != null) {
      lastSearchCall.cancel();
      lastSearchCall = null;
    }
    if (q.isEmpty()) {
      if (showingFoundPlaces) {
        selectPlace(null, true);
        ((LinearLayoutManager) getLayoutManager()).scrollToPositionWithOffset(0, 0);
        mapView.checkLocationSettings(true, true);
        showNearbyPlaces();
      }
      return;
    }
    setPlacesHeader(R.string.LoadingPlaces);
    lastSearchTask = new CancellableRunnable() {
      @Override
      public void act () {
        if (lastQuery.equals(q) && mapHidden) {
          searchInternal(q);
        }
      }
    };
    UI.post(lastSearchTask, 350);
  }

  private void searchInternal (final String q) {
    lastSearchCall = MediaLocationFinder.findNearbyPlaces(tdlib, mediaLayout.getTargetChatId(), lastLocation, q, new MediaLocationFinder.Callback() {
      @Override
      public void onNearbyPlacesLoaded (CancellableResultHandler call, Location location, long queryId, List<MediaLocationData> result, @Nullable String nextOffset) {
        if (lastQuery.equals(q) && mapHidden) {
          showFoundPlaces(result);
        }
      }

      @Override
      public void onNearbyPlacesErrorLoading (CancellableResultHandler call, Location location, TdApi.Error error) {
        if (lastQuery.equals(q) && mapHidden) {
          showFoundPlaces(null);
        }
      }
    });
  }
}
