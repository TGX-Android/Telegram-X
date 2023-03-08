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
 * File created on 07/03/2018
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Rect;
import android.location.Location;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.attach.MediaLocationPlaceView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessageLocation;
import org.thunderdog.challegram.helper.LocationHelper;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.LiveLocationManager;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibSender;
import org.thunderdog.challegram.telegram.TdlibThread;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CircleButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.Td;

public abstract class MapController<V extends View, T> extends ViewController<MapController.Args> implements View.OnClickListener, LocationHelper.LocationChangeListener, MoreDelegate, FactorAnimator.Target, Client.ResultHandler, MessageListener, Comparator<MapController.LocationPoint<T>>, LiveLocationManager.Listener, ListUpdateCallback {
  protected static final int MODE_DROPPED_PIN = 0;
  protected static final int MODE_LIVE_LOCATION = 1;

  public static class Args {
    protected final int mode;
    public double latitude, longitude;
    public String title, address;
    public @Nullable TdApi.Message message;
    public long messageExpiresAt;

    public ImageFile iconImage;

    public boolean navigateBackOnStop;
    public long locationOwnerChatId;
    public boolean isFaded;

    public long chatId, messageThreadId;

    public Args (double latitude, double longitude) {
      this.mode = MODE_DROPPED_PIN;
      this.latitude = latitude;
      this.longitude = longitude;
    }

    public Args (double latitude, double longitude, @Nullable TdApi.Message message) {
      this.mode = message != null && ((TdApi.MessageLocation) message.content).expiresIn == 0 ? MODE_DROPPED_PIN : MODE_LIVE_LOCATION;
      this.latitude = latitude;
      this.longitude = longitude;
      this.message = message;
      if (message != null) {
        messageExpiresAt = SystemClock.uptimeMillis() + (long) ((TdApi.MessageLocation) message.content).expiresIn * 1000l;
      }
    }

    public Args setNavigateBackOnStop (boolean navigate) {
      this.navigateBackOnStop = navigate;
      return this;
    }

    public Args setChatId (long chatId, long messageThreadId) {
      this.chatId = chatId;
      this.messageThreadId = messageThreadId;
      return this;
    }

    public Args setLocationOwnerChatId (long chatId) {
      this.locationOwnerChatId = chatId;
      return this;
    }

    public Args setIsFaded (boolean isFaded) {
      this.isFaded = isFaded;
      return this;
    }
  }

  public MapController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_map;
  }

  @Override
  protected boolean usePopupMode () {
    return true;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return !hasFocused;
  }

  @Override
  public boolean canSlideBackFrom (NavigationController navigationController, float x, float y) {
    return y <= HeaderView.getSize(true);
  }

  @Override
  protected long getStartDelay (boolean forward) {
    return 260l;
  }

  // MAP

  protected final boolean hasFocusPoint () {
    switch (getArgumentsStrict().mode) {
      case MODE_DROPPED_PIN:
        return true;
    }
    return false;
  }

  private FrameLayoutFix contentView;
  private CustomRecyclerView recyclerView;
  private SettingsAdapter adapter;
  private V mapView;

  private CircleButton myLocationButton, directionButton, tileButton, backCircle;

  protected static class LocationPoint<D> {
    protected double latitude, longitude;
    protected float accuracy, bearing;
    protected int heading;

    protected @Nullable TdApi.Message message;
    protected long expiresAt = 0;
    protected boolean isLiveLocation;
    protected boolean isSelfLocation;
    protected boolean isFocusPoint;

    protected final MapController<?,D> context;

    protected D data;

    public LocationPoint (MapController<?,D> context, double latitude, double longitude) {
      this.context = context;
      this.latitude = latitude;
      this.longitude = longitude;
    }

    public LocationPoint<D> setSourceMessage (@Nullable TdApi.Message liveLocation, boolean isLiveLocation) {
      this.message = liveLocation;
      this.isLiveLocation = isLiveLocation;
      this.expiresAt = liveLocation != null ? SystemClock.uptimeMillis() + (long) ((TdApi.MessageLocation) liveLocation.content).expiresIn * 1000l : 0;
      return this;
    }
  }

  protected abstract V createMapView (Context context, int marginBottom);
  protected abstract boolean needBackgroundMapInitialization (@NonNull V mapView);
  protected abstract void initializeMap (@NonNull V mapView, boolean inBackground);
  protected abstract void resumeMap (@NonNull V mapView);
  protected abstract void pauseMap (@NonNull V mapView);
  protected abstract void destroyMap (@NonNull V mapView);
  protected abstract boolean displayMyLocation (@NonNull V mapView);
  protected abstract boolean onPositionRequested (@NonNull V mapView, @Nullable LocationPoint<T> point, boolean animated, boolean needBearing, boolean onlyFocus);
  protected abstract void onApplyMapType (int oldType, int newType);
  protected abstract boolean onBuildDirectionTo (@NonNull V mapView, double latitude, double longitude);
  protected abstract boolean onStartPeriodicBearingUpdates (@NonNull V mapView);
  protected abstract void onFinishPeriodicBearingUpdates (@NonNull V mapView);
  protected abstract boolean wouldRememberMapType (int newMapType);
  protected abstract int[] getAvailableMapTypes ();

  protected final @NonNull V mapView () {
    if (mapView == null) {
      throw new IllegalStateException();
    }
    return mapView;
  }

  protected final int mapType () {
    return mapType != Settings.MAP_TYPE_UNSET ? mapType : Theme.isDark() ? Settings.MAP_TYPE_DARK : Settings.MAP_TYPE_DEFAULT;
  }

  protected final @Nullable LocationPoint<T> myLocation (boolean allowCached) {
    if (myLocation != null) {
      return myLocation;
    }
    if (isLoadingLocation && allowCached) {
      Location location = LocationHelper.getLastKnownLocation(context, true);
      if (location != null) {
        LocationPoint<T> point = new LocationPoint<>(this, location.getLatitude(), location.getLongitude());
        point.isSelfLocation = true;
        return point;
      }
    }
    return null;
  }

  @Override
  protected int getHeaderIconColorId () {
    return R.id.theme_color_headerButtonIcon;
  }

  @Override
  protected View onCreateView (Context context) {
    contentView = new FrameLayoutFix(context);
    ViewSupport.setThemedBackground(contentView, R.id.theme_color_filling, this);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    int bottomMargin = SettingHolder.measureHeightForType(ListItem.TYPE_ATTACH_LOCATION_BIG);

    mapType = Settings.instance().getMapType();
    mapView = createMapView(context, bottomMargin);
    if (mapView == null) {
      throw new IllegalStateException();
    }
    contentView.addView(mapView);

    directionButton = new CircleButton(context);
    directionButton.setBottomText(Lang.getString(R.string.DirectionGo).toUpperCase());
    directionButton.setId(R.id.btn_direction);
    directionButton.setOnClickListener(this);
    directionButton.init(R.drawable.baseline_directions_24, 56f, 4f, R.id.theme_color_circleButtonRegular, R.id.theme_color_circleButtonRegularIcon);
    int padding = Screen.dp(4f);
    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(Screen.dp(56f) + padding * 2, Screen.dp(56f) + padding * 2, Gravity.RIGHT | Gravity.BOTTOM);
    params.rightMargin = Screen.dp(16f) - padding;
    params.bottomMargin = params.rightMargin + bottomMargin;
    directionButton.setLayoutParams(params);
    addThemeInvalidateListener(directionButton);
    contentView.addView(directionButton);

    myLocationButton = new CircleButton(context);
    myLocationButton.setId(R.id.btn_gps);
    myLocationButton.setOnClickListener(this);
    myLocationButton.init(R.drawable.baseline_gps_fixed_24, 56f, 4f, R.id.theme_color_filling, R.id.theme_color_icon);
    params = FrameLayoutFix.newParams(Screen.dp(56f) + padding * 2, Screen.dp(56f) + padding * 2, Gravity.RIGHT | Gravity.BOTTOM);
    params.rightMargin = Screen.dp(16f) - padding;
    params.bottomMargin = params.rightMargin + bottomMargin;
    if (directionButton != null) {
      params.bottomMargin += Screen.dp(16f) + Screen.dp(56f);
    }
    myLocationButton.setLayoutParams(params);
    addThemeInvalidateListener(myLocationButton);
    contentView.addView(myLocationButton);

    tileButton = new CircleButton(context);
    tileButton.setId(R.id.btn_layer);
    tileButton.setOnClickListener(this);
    tileButton.init(R.drawable.baseline_layers_24, 36f, 4f, R.id.theme_color_filling, R.id.theme_color_icon);
    params = FrameLayoutFix.newParams(Screen.dp(36f) + padding * 2, Screen.dp(36f) + padding * 2, Gravity.RIGHT | Gravity.TOP);
    params.rightMargin = Screen.dp(10f) - padding;
    params.topMargin = HeaderView.getTopOffset() + params.rightMargin;
    tileButton.setLayoutParams(params);
    tileButton.setAlpha(0f);
    addThemeInvalidateListener(tileButton);
    contentView.addView(tileButton);

    backCircle = new CircleButton(context);
    backCircle.setEnabled(false);
    backCircle.setAlpha(0f);
    backCircle.init(0, 36f, 4f, R.id.theme_color_filling, R.id.theme_color_icon);
    params = FrameLayoutFix.newParams(Screen.dp(36f) + padding * 2, Screen.dp(36f) + padding * 2, Gravity.LEFT | Gravity.TOP);
    params.leftMargin = Screen.dp(10f) - padding;
    params.topMargin = HeaderView.getTopOffset() + params.leftMargin;
    backCircle.setLayoutParams(params);
    addThemeInvalidateListener(backCircle);
    contentView.addView(backCircle);

    recyclerView = (CustomRecyclerView) Views.inflate(context(), R.layout.recycler_custom, contentView);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
        checkMapPosition();
      }
    });
    ViewSupport.setHigherElevation(recyclerView);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    recyclerView.setVerticalScrollBarEnabled(false);
    recyclerView.addItemDecoration(new SettingsAdapter.BackgroundDecoration(R.id.theme_color_background) {
      @Override
      public void getItemOffsets (Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position > 0) {
          outRect.top = 0;
        } else if (position == 0) {
          outRect.top = (parent.getMeasuredHeight())
            - SettingHolder.measureHeightForType(ListItem.TYPE_SHADOW_TOP)
            - SettingHolder.measureHeightForType(ListItem.TYPE_ATTACH_LOCATION_BIG);
        }
      }

      @Override
      protected boolean needsBackground (ListItem item) {
        return super.needsBackground(item) && !(item.getViewType() == ListItem.TYPE_SHADOW_TOP && item.getBoolValue());
      }
    });
    addThemeInvalidateListener(recyclerView);
    recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l));
    recyclerView.setMeasureListener((v, oldWidth, oldHeight, newWidth, newHeight) -> {
      if (oldHeight > 0 && oldHeight != newHeight) {
        v.invalidateItemDecorations();
      }
      checkMapPosition();
    });
    recyclerView.setTouchInterceptor((v, x, y) -> {
      LinearLayoutManager m = (LinearLayoutManager) v.getLayoutManager();
      int i = m.findFirstVisibleItemPosition();
      if (i == 0) {
        View top = m.findViewByPosition(0);
        return top == null || !(top.getTop() + top.getMeasuredHeight() >= y);
      }
      return true;
    });
    recyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    recyclerView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setPlace (ListItem item, int position, final MediaLocationPlaceView view, boolean isUpdate) {
        switch (item.getId()) {
          case R.id.liveLocation: {
            LocationPoint<T> point = (LocationPoint<T>) item.getData();
            setLocationPlaceView(view, point.message, point.expiresAt, false);
            break;
          }
          case R.id.liveLocationSelf: {
            final TdApi.Message msg = tdlib.cache().findOutputLiveLocationMessage(getArgumentsStrict().chatId);
            if (msg != null) {
              TGMessageLocation.TimeResult result = TGMessageLocation.buildLiveLocationSubtitle(tdlib, Math.max(msg.date, msg.editDate));
              TdApi.MessageLocation location = ((TdApi.MessageLocation) msg.content);
              long expiresIn = Math.max(0, (long) (msg.date + location.livePeriod) * 1000l - System.currentTimeMillis());
              view.setLiveLocation(Lang.getString(R.string.StopSharingLiveLocation), result.text, true, true, location.expiresIn == 0, location.livePeriod, expiresIn > 0 ? SystemClock.uptimeMillis() + expiresIn : 0);
              view.setInProgress(inShareProgress, isUpdate);
              if (result.nextLiveLocationUpdateTime != -1) {
                view.scheduleSubtitleUpdater(new Runnable() {
                  @Override
                  public void run () {
                    if (view.getSubtitleUpdater() != this) {
                      return;
                    }
                    TGMessageLocation.TimeResult result = TGMessageLocation.buildLiveLocationSubtitle(tdlib, Math.max(msg.date, msg.editDate));
                    view.updateSubtitle(result.text);
                    if (result.nextLiveLocationUpdateTime != -1) {
                      view.scheduleSubtitleUpdater(this, SystemClock.uptimeMillis() - result.nextLiveLocationUpdateTime);
                    }
                  }
                }, SystemClock.uptimeMillis() - result.nextLiveLocationUpdateTime);
              }
            } else {
              view.setDefaultLiveLocation(false);
              view.setInProgress(inShareProgress, isUpdate);
            }
            break;
          }
          case R.id.place: {
            Args args = getArgumentsStrict();
            switch (args.mode) {
              case MODE_DROPPED_PIN: {
                if (args.message != null) {
                  setLocationPlaceView(view, args.message, args.messageExpiresAt, true);
                  return;
                }
                String subtitle;
                if (myLocation != null) {
                  StringBuilder b = new StringBuilder();
                  b.append(Lang.shortDistance(U.distanceBetween(args.latitude, args.longitude, myLocation.latitude, myLocation.longitude)));
                  if (!StringUtils.isEmpty(args.address)) {
                    b.append(Strings.DOT_SEPARATOR);
                    b.append(args.address);
                  }
                  subtitle = b.toString();
                } else if (isLoadingLocation) {
                  subtitle = Lang.getString(R.string.CalculatingDistance);
                } else {
                  subtitle = !StringUtils.isEmpty(args.address) ? args.address : Lang.beautifyCoordinates(args.latitude, args.longitude);
                }
                String title = args.locationOwnerChatId != 0 ? tdlib.chatTitle(args.locationOwnerChatId) : !StringUtils.isEmpty(args.title) ? args.title : Lang.getString(R.string.DroppedPin);
                view.setLocation(title, subtitle, R.id.theme_color_file, null, false, 0, 0);
                if (args.locationOwnerChatId != 0) {
                  ImageFile avatarFile = tdlib.chatAvatar(args.locationOwnerChatId);
                  if (avatarFile != null) {
                    view.setRoundedLocationImage(avatarFile);
                  } else {
                    view.setPlaceholder(tdlib.chatPlaceholderMetadata(args.locationOwnerChatId, false));
                  }
                } else {
                  view.setLocationImage(args.iconImage);
                }
                view.setIsFaded(args.isFaded);
                break;
              }
              case MODE_LIVE_LOCATION: {
                LocationPoint<T> point = (LocationPoint<T>) item.getData();
                setLocationPlaceView(view, point.message, point.expiresAt, true);
                break;
              }
            }
            break;
          }
        }
      }
    };

    Args args = getArgumentsStrict();

    pointsOfInterest = new ArrayList<>();

    List<ListItem> items = new ArrayList<>();

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP).setBoolValue(true));
    switch (args.mode) {
      case MODE_DROPPED_PIN: {
        LocationPoint<T> point = new LocationPoint<>(this, args.latitude, args.longitude);
        point.setSourceMessage(args.message, args.message != null);
        point.isFocusPoint = true;
        pointsOfInterest.add(point);
        // hasChangedFocus = args.message != null;

        items.add(new ListItem(ListItem.TYPE_ATTACH_LOCATION_BIG, R.id.place));
        break;
      }
      case MODE_LIVE_LOCATION: {
        // hasChangedFocus = true;

        TdApi.Message sharingMessage = tdlib.cache().findOutputLiveLocationMessage(args.chatId);
        if (sharingMessage != null || tdlib.canSendBasicMessage(args.chatId)) {
          items.add(newShareLiveLocationCell());
        }
        if (sharingMessage != null) {
          TdApi.Location location = ((TdApi.MessageLocation) sharingMessage.content).location;
          myLocation = new LocationPoint<>(this, location.latitude, location.longitude);
          myLocation.isSelfLocation = true;
        }
        if (args.message != null && !tdlib.isSelfSender(args.message)) {
          LocationPoint<T> point = new LocationPoint<>(this, args.latitude, args.longitude);
          point.setSourceMessage(args.message, true);
          pointsOfInterest.add(point);

          items.add(newLiveLocationCell(point));
        }
        break;
      }
    }
    adapter.setItems(items, false);

    recyclerView.setAdapter(adapter);
    contentView.addView(recyclerView);

    if (args.mode == MODE_LIVE_LOCATION && args.message != null) {
      ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(0, -bottomMargin);
    }

    checkDirectionHidden();
    isLoadingLocation = context().permissions().canAccessLocation();

    if (needBackgroundMapInitialization(mapView)) {
      runOnBackgroundThread(() -> {
        initializeMap(mapView, true);
        runOnUiThread(() -> {
          if (!isDestroyed()) {
            initMap();
          }
        });
      });
    } else {
      initializeMap(mapView, false);
    }

    if (args.chatId != 0) {
      tdlib.listeners().subscribeToMessageUpdates(args.chatId, this);
      tdlib.context().liveLocation().addListener(this);

      tdlib.client().send(new TdApi.SearchChatRecentLocationMessages(args.chatId, 100), this);
    }

    return contentView;
  }

  private void checkMapPosition () {
    int mapWidth = mapView.getMeasuredWidth();
    int mapHeight = mapView.getMeasuredHeight() - HeaderView.getTopOffset();
    float translationY = 0f;
    if (mapHeight > mapWidth) {
      LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
      int firstPosition = manager.findFirstVisibleItemPosition();
      if (firstPosition > 0) {
        translationY = mapWidth - mapHeight;
      } else if (firstPosition == 0) {
        View view = manager.findViewByPosition(0);
        if (view != null) {
          translationY = Math.max(mapWidth - mapHeight, manager.getDecoratedTop(view));
        }
      }
    }
    setMapTranslationY(translationY);
  }

  private static ListItem newShareLiveLocationCell () {
    return new ListItem(ListItem.TYPE_ATTACH_LOCATION_BIG, R.id.liveLocationSelf);
  }

  private float mapTranslationY;
  private void setMapTranslationY (float y) {
    if (mapTranslationY != y) {
      mapTranslationY = y;
      mapView.setTranslationY(y / 2);
      if (directionButton != null) {
        directionButton.setTranslationY(y);
      }
      myLocationButton.setTranslationY(calculateMyLocationTranslate());
    }
  }

  private float calculateMyLocationTranslate () {
    float y = mapTranslationY;
    if (directionHideAnimator != null) {
      y += directionHideAnimator.getFloatValue() * (float) (Screen.dp(16f) + Screen.dp(56f));
    }
    return y;
  }

  private static final int ANIMATOR_DIRECTION_HIDE = 1;
  private BoolAnimator directionHideAnimator;

  private void checkDirectionHidden () {
    boolean isHidden = getArgumentsStrict().mode == MODE_LIVE_LOCATION;
    if (isHidden) {
      isHidden = focusMode != FOCUS_MODE_TARGET || focusTarget == null;
    }
    boolean nowHidden = directionHideAnimator != null && directionHideAnimator.getValue();
    if (nowHidden != isHidden) {
      if (directionHideAnimator == null) {
        directionHideAnimator = new BoolAnimator(ANIMATOR_DIRECTION_HIDE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180);
      }
      directionHideAnimator.setValue(isHidden, hasFocused);
    }
  }

  private void setLocationPlaceView (final MediaLocationPlaceView view, final TdApi.Message msg, long expiresAt, boolean isBase) {
    TdlibSender sender = new TdlibSender(tdlib, msg.chatId, msg.senderId);
    view.setRoundedLocationImage(sender.getAvatar());

    TGMessageLocation.TimeResult result = buildLocationSubtitle(msg, isBase);
    TdApi.MessageLocation location = (TdApi.MessageLocation) msg.content;
    view.setLocation(sender.getName(), result.text, sender.getAvatarColorId(), sender.getLetters(), expiresAt == 0 || SystemClock.uptimeMillis() >= expiresAt, location.livePeriod, expiresAt);
    if (result.nextLiveLocationUpdateTime != -1) {
      view.scheduleSubtitleUpdater(new Runnable() {
        @Override
        public void run () {
          if (view.getSubtitleUpdater() != this) {
            return;
          }
          TGMessageLocation.TimeResult result = buildLocationSubtitle(msg, isBase);
          view.updateSubtitle(result.text);
          if (result.nextLiveLocationUpdateTime != -1) {
            view.scheduleSubtitleUpdater(this, SystemClock.uptimeMillis() - result.nextLiveLocationUpdateTime);
          }
        }
      }, SystemClock.uptimeMillis() - result.nextLiveLocationUpdateTime);
    }
  }

  private TGMessageLocation.TimeResult buildLocationSubtitle (TdApi.Message msg, boolean isBase) {
    TdApi.MessageLocation messageLocation = (TdApi.MessageLocation) msg.content;
    TdApi.Location location = messageLocation.location;
    LocationPoint<T> targetPoint;
    targetPoint = myLocation;
    if (!isBase && hasFocusPoint()) {
      targetPoint = pointsOfInterest.get(0);
    }

    long nextUpdateTimeMs = -1;

    StringBuilder b = new StringBuilder();

    /*if (((TdApi.MessageLocation) msg.content).expiresIn > 0) {

    } else {
      String ago = Lang.getAgo(Math.max(msg.date, msg.editDate), System.currentTimeMillis(), false, true, true);
      b.append(ago);
    }*/

    TGMessageLocation.TimeResult result = TGMessageLocation.buildLiveLocationSubtitle(tdlib, Math.max(msg.date, msg.editDate));
    b.append(result.text);
    nextUpdateTimeMs = result.nextLiveLocationUpdateTime;

    if (targetPoint != null) {
      if (b.length() > 0)
        b.append(Strings.DOT_SEPARATOR);
      b.append(Lang.shortDistance(U.distanceBetween(location.latitude, location.longitude, targetPoint.latitude, targetPoint.longitude)));
    } else if (isLoadingLocation) {
      if (b.length() > 0)
        b.append(Strings.DOT_SEPARATOR);
      b.append(Lang.getString(R.string.Calculating));
    }
    return new TGMessageLocation.TimeResult(b.toString(), nextUpdateTimeMs);
  }

  @Override
  protected void applyCustomHeaderAnimations (float factor) {
    tileButton.setAlpha(factor);
    backCircle.setAlpha(factor);
    backCircle.setTranslationY(-HeaderView.getSize(true) * (1f - factor));
  }

  @Override
  public void destroy () {
    super.destroy();
    Args args = getArgumentsStrict();
    if (args.chatId != 0) {
      tdlib.listeners().unsubscribeFromMessageUpdates(args.chatId, this);
      tdlib.context().liveLocation().removeLocationListener(this);
    }
    for (LocationPoint<?> point : pointsOfInterest) {
      if (point.data instanceof Destroyable) {
        ((Destroyable) point.data).performDestroy();
      }
    }
    setInCompass(false);
    if (mapView != null) {
      destroyMap(mapView);
      mapView = null;
    }
    if (locationHelper != null) {
      locationHelper.destroy();
    }
    Views.destroyRecyclerView(recyclerView);
  }

  @Override
  public void onActivityResume () {
    super.onActivityResume();
    if (needInitialization) {
      needInitialization = false;
      initMap();
    } else if (mapView != null) {
      resumeMap(mapView);
    }
  }

  @Override
  public void onActivityPause () {
    super.onActivityPause();
    if (mapView != null) {
      pauseMap(mapView);
    }
  }

  private boolean needInitialization;

  private void initMap () {
    if (isDestroyed()) {
      return;
    }
    if (isPaused()) {
      needInitialization = true;
      return;
    }
    initializeMap(mapView, false);
    checkIsSharing(false);
  }

  private LocationHelper locationHelper;
  private boolean hasFocused;

  @Override
  public void onFocus () {
    super.onFocus();
    this.hasFocused = true;
    if (locationHelper == null) {
      locationHelper = new LocationHelper(context, this, true, false);
    }
    setLoadingLocation(true);
    locationHelper.checkLocationPermission(REQUEST_DEFAULT, null);
  }

  @Override
  public void onBlur () {
    super.onBlur();
    if (locationHelper != null) {
      locationHelper.cancel();
    }
  }

  private static final String REQUEST_DEFAULT = "";
  private static final String REQUEST_FOCUS_DEFAULT = "focus_target";
  private static final String REQUEST_FOCUS_SELF = "focus_self";
  private static final String REQUEST_SHARE_LIVE = "share_live";

  @Override
  public void onLocationResult (LocationHelper context, @NonNull String arg, @Nullable Location location) {
    if (isDestroyed()) {
      return;
    }
    myLocationButton.setInProgress(false);
    if (location == null) {
      if (context().permissions().canAccessLocation() && mapView != null) {
        setLoadingLocation(true);
        boolean willReceiveLocation = displayMyLocation(mapView);
        if (!willReceiveLocation) {
          locationHelper.receiveLocation(arg, null, 1000, true);
        }
      }
      return;
    }
    if (mapView != null) {
      displayMyLocation(mapView);
    }
    switch (arg) {
      case REQUEST_FOCUS_DEFAULT:
        setMyLocation(location);
        setFocusMode(FOCUS_MODE_DEFAULT, null);
        break;
      case REQUEST_FOCUS_SELF:
        if (focusMode == FOCUS_MODE_SELF) {
          if (!setInCompass(!inCompass) && !inCompass) {
            setFocusMode(FOCUS_MODE_DEFAULT, null);
          }
        } else {
          setFocusMode(FOCUS_MODE_SELF, null);
        }
        break;
      case REQUEST_SHARE_LIVE:
        inShareProgress = false;
        adapter.updateValuedSettingById(R.id.liveLocationSelf);
        openLiveLocationAlert(getArgumentsStrict().chatId, arg1 -> {
          if (myLocation != null) {
            Args args = getArgumentsStrict();
            inShareProgress = true;
            adapter.updateValuedSettingById(R.id.liveLocationSelf);
            tdlib.sendMessage(args.chatId, args.messageThreadId, 0, Td.newSendOptions(tdlib.chatDefaultDisableNotifications(args.chatId)), new TdApi.InputMessageLocation(new TdApi.Location(myLocation.latitude, myLocation.longitude, myLocation.accuracy), arg1, myLocation.heading, 0));
          }
        });
        break;
    }
  }

  private static final int ANIMATOR_MY = 0;
  private BoolAnimator myAnimator;
  private void setHasMyLocation (boolean hasMyLocation) {
    if (hasMyLocation != (myAnimator != null && myAnimator.getValue())) {
      if (myAnimator == null) {
        myAnimator = new BoolAnimator(ANIMATOR_MY, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
      }
      myAnimator.setValue(hasMyLocation, true);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_MY: {
        if (factor == 0f) {
          myLocationButton.setIconColorId(R.id.theme_color_icon);
        } else if (factor == 1f) {
          myLocationButton.setIconColorId(R.id.theme_color_iconActive);
        } else {
          myLocationButton.setCustomIconColor(ColorUtils.fromToArgb(Theme.iconColor(), Theme.getColor(R.id.theme_color_iconActive), factor));
        }
        break;
      }
      case ANIMATOR_DIRECTION_HIDE:
        directionButton.setAlpha(1f - factor);
        float scale = .6f + .4f * (1f - factor);
        directionButton.setScaleX(scale);
        directionButton.setScaleY(scale);
        myLocationButton.setTranslationY(calculateMyLocationTranslate());
        break;
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  private boolean inCompass;
  private boolean setInCompass (boolean inCompass) {
    if (this.inCompass != inCompass) {
      if (inCompass && !onStartPeriodicBearingUpdates(mapView)) {
        return false;
      }
      this.inCompass = inCompass;
      myLocationButton.replaceIcon(inCompass ? R.drawable.baseline_explore_24 : R.drawable.baseline_gps_fixed_24);
      if (!inCompass || (myLocation != null && myLocation.heading != 0)) {
        updateCameraAutomatically();
      }
      if (!inCompass) {
        onFinishPeriodicBearingUpdates(mapView);
      }
      return true;
    }
    return false;
  }

  @Override
  public void onLocationRequestFailed (LocationHelper context, int errorCode, final @NonNull String arg, Location savedLocation) {
    if (isDestroyed()) {
      return;
    }
    myLocationButton.setInProgress(false);
    setLoadingLocation(false);
    switch (arg) {
      case REQUEST_DEFAULT: {
        if (myLocation == null) {
          setFocusMode(FOCUS_MODE_TARGET, pointsOfInterest.get(0));
        }
        break;
      }
      case REQUEST_FOCUS_DEFAULT: {
        if (myLocation == null && pointsOfInterest.size() > 0) {
          if (focusMode == FOCUS_MODE_TARGET && focusTarget == pointsOfInterest.get(0)) {
            return;
          }
          setFocusMode(FOCUS_MODE_DEFAULT, null);
        }
        break;
      }
      case REQUEST_SHARE_LIVE: {
        inShareProgress = false;
        adapter.updateValuedSettingById(R.id.liveLocationSelf);
        break;
      }
    }
  }

  private boolean isLoadingLocation;
  private LocationPoint<T> myLocation;

  protected final void setMyLocation (@NonNull Location location) {
    double latitude = location.getLatitude();
    double longitude = location.getLongitude();
    float bearing = location.getBearing();
    int heading = U.getHeading(location);
    if (myLocation == null || myLocation.latitude != latitude || myLocation.longitude != longitude) {
      if (myLocation == null) {
        myLocation = new LocationPoint<>(this, latitude, longitude);
        myLocation.isSelfLocation = true;
        if (isSharingLiveLocation) {
          onPointOfInterestAdded(myLocation, -1);
          onLivePersonListChanged(true, false);
        }
      } else {
        myLocation.latitude = latitude;
        myLocation.longitude = longitude;
      }
      myLocation.heading = heading;
      myLocation.bearing = bearing;
      isLoadingLocation = false;
      notifyDistanceChanged();
    } else if (myLocation.heading != heading) {
      myLocation.heading = heading;
      myLocation.bearing = bearing;
      if (inCompass) {
        updateCameraAutomatically();
      }
    }
  }

  private void setLoadingLocation (boolean isLoading) {
    if (this.isLoadingLocation != isLoading) {
      this.isLoadingLocation = isLoading;
      adapter.updateValuedSettingById(R.id.place);
      adapter.updateAllValuedSettingsById(R.id.liveLocation);
    }
  }

  private void notifyDistanceChanged () {
    adapter.updateValuedSettingById(R.id.place);
    updateCameraAutomatically();

    resortList();

    if (isSharingLiveLocation && myLocation != null) {
      onPointOfInterestCoordinatesChanged(myLocation, -1);
    }
  }

  private void resortList () {
    if (tempPointsOfInterest == null) {
      tempPointsOfInterest = new ArrayList<>(pointsOfInterest);
    } else {
      tempPointsOfInterest.clear();
      tempPointsOfInterest.addAll(pointsOfInterest);
    }
    Collections.sort(tempPointsOfInterest, this);
    boolean foundChanges = false;
    int i = 0;
    for (LocationPoint<T> point : pointsOfInterest) {
      if (point != tempPointsOfInterest.get(i)) {
        foundChanges = true;
      }
      i++;
    }
    adapter.updateAllValuedSettingsById(R.id.liveLocation);
    if (!foundChanges) {
      return;
    }
    DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
      @Override
      public int getOldListSize () {
        return pointsOfInterest.size();
      }

      @Override
      public int getNewListSize () {
        return tempPointsOfInterest.size();
      }

      @Override
      public boolean areItemsTheSame (int oldItemPosition, int newItemPosition) {
        return pointsOfInterest.get(oldItemPosition) == tempPointsOfInterest.get(newItemPosition);
      }

      @Override
      public boolean areContentsTheSame (int oldItemPosition, int newItemPosition) {
        return true; // areItemsTheSame(oldItemPosition, newItemPosition);
      }
    });
    diffResult.dispatchUpdatesTo(this);
    pointsOfInterest.clear();
    pointsOfInterest.addAll(tempPointsOfInterest);
  }

  @Override
  public void onInserted (int position, int count) {
    Log.i("onInserted %d", position);
    int startIndex = position + liveLocationsStartIndex();
    if (hasFocusPoint()) {
      startIndex--;
    }
    for (int i = 0; i < count; i++) {
      adapter.addItem(startIndex + i, newLiveLocationCell(pointsOfInterest.get(position + i)));
    }
  }

  @Override
  public void onRemoved (int position, int count) {
    Log.i("onRemoved %d %d", position, count);
    if (hasFocusPoint())
      position--;
    int offset = liveLocationsStartIndex();
    if (count == 1)
      adapter.removeItem(offset + position);
    else
      adapter.removeRange(offset + position, count);
  }

  @Override
  public void onMoved (int fromPosition, int toPosition) {
    int offset = liveLocationsStartIndex();
    if (hasFocusPoint()) {
      offset--;
    }
    Log.i("moveItem %d %d", fromPosition, toPosition);
    adapter.moveItem(offset + fromPosition, offset + toPosition);
  }

  @Override
  public void onChanged (int position, int count, Object payload) {
    Log.i("onChanged %d", position);
    if (hasFocusPoint())
      position--;
    int startIndex = position + liveLocationsStartIndex();
    for (int i = 0; i < count; i++) {
      adapter.notifyItemChanged(startIndex + i);
      // adapter.updateValuedSettingByPosition(startIndex + i);
    }
  }

  private void updateCameraAutomatically () {
    switch (focusMode) {
      case FOCUS_MODE_DEFAULT:
        onPositionRequested(mapView, null, hasFocused, false, false);// !hasChangedFocus
        break;
      case FOCUS_MODE_SELF:
        if (myLocation != null) {
          onPositionRequested(mapView, myLocation, hasFocused, inCompass, false);
        }
        break;
    }
  }

  private int focusMode;
  private @Nullable LocationPoint<T> focusTarget;
  // private boolean hasChangedFocus;

  private static final int FOCUS_MODE_DEFAULT = 0;
  private static final int FOCUS_MODE_SELF = 1;
  private static final int FOCUS_MODE_TARGET = 2;
  private static final int FOCUS_MODE_CUSTOM = 3;

  private void setFocusMode (int focusMode, @Nullable LocationPoint<T> focusTarget) {
    if (this.focusMode != focusMode || (focusMode == FOCUS_MODE_TARGET && this.focusTarget != focusTarget)) {
      LocationPoint<T> oldFocusTarget = this.focusTarget != null ? this.focusTarget : this.focusMode == FOCUS_MODE_SELF ? myLocation : null;
      LocationPoint<T> newFocusTarget = focusTarget != null ? focusTarget : focusMode == FOCUS_MODE_SELF ? myLocation : null;
      if (oldFocusTarget != newFocusTarget) {
        if (oldFocusTarget != null) {
          onPointOfInterestFocusStateChanged(oldFocusTarget, false);
        }
        if (newFocusTarget != null) {
          onPointOfInterestFocusStateChanged(newFocusTarget, true);
        }
      }

      this.focusMode = focusMode;
      // this.hasChangedFocus = true;
      this.focusTarget = focusTarget;
      checkDirectionHidden();
      LocationPoint<T> point = null;
      switch (focusMode) {
        case FOCUS_MODE_TARGET:
          point = focusTarget;
          break;
        case FOCUS_MODE_SELF:
          point = myLocation;
          break;
      }
      setInCompass(false);
      setHasMyLocation(focusMode == FOCUS_MODE_SELF);
      if (onPositionRequested(mapView, point, hasFocused, false, false)) {
        if (focusMode == FOCUS_MODE_DEFAULT || focusMode == FOCUS_MODE_TARGET) {
          bringMapToFront();
        }
      }
    }
  }

  private void bringMapToFront () {
    if (mapView == null) {
      return;
    }

    int viewWidth = recyclerView.getMeasuredWidth();
    int viewHeight = recyclerView.getMeasuredHeight();

    if (viewWidth == 0 || viewHeight == 0) {
      return;
    }

    LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
    int firstVisiblePosition = manager.findFirstVisibleItemPosition();
    if (firstVisiblePosition == RecyclerView.NO_POSITION) {
      return;
    }

    int decorationTop = viewHeight
      - SettingHolder.measureHeightForType(ListItem.TYPE_SHADOW_TOP)
      - SettingHolder.measureHeightForType(ListItem.TYPE_ATTACH_LOCATION_BIG);

    int scrollTop = adapter.measureScrollTop(firstVisiblePosition);
    View view = manager.findViewByPosition(firstVisiblePosition);
    if (view != null) {
      scrollTop -= manager.getDecoratedTop(view);
    }
    if (firstVisiblePosition > 0) {
      scrollTop += decorationTop;
    }

    int maxScrollY = viewWidth >= viewHeight ? 0 : viewWidth + HeaderView.getTopOffset();

    if (scrollTop > maxScrollY) {
      recyclerView.stopScroll();
      recyclerView.smoothScrollBy(0, maxScrollY - scrollTop);
    }
  }

  private void bringItemsToFront (boolean addedLiveLocations) {
    if (mapView == null) {
      return;
    }
    int liveLocationCount = getLiveLocationCount();
    if (liveLocationCount == 0) {
      return;
    }

    LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
    int firstVisibleItemPosition = manager.findFirstVisibleItemPosition();

    if (firstVisibleItemPosition > 0) {
      return;
    }

    View firstView = manager.findViewByPosition(0);
    int scrollTop = firstView != null ? manager.getDecoratedTop(firstView) : 0;

    int desiredOffset = SettingHolder.measureHeightForType(ListItem.TYPE_ATTACH_LOCATION_BIG);
    if (liveLocationCount > 1) {
      desiredOffset += desiredOffset / 2;
    }
    if (hasFocusPoint()) {
      if (liveLocationCount == 1) {
        desiredOffset += desiredOffset / 2;
      }
      desiredOffset +=
        SettingHolder.measureHeightForType(ListItem.TYPE_SHADOW_BOTTOM) +
        SettingHolder.measureHeightForType(ListItem.TYPE_SHADOW_TOP) +
        SettingHolder.measureHeightForType(ListItem.TYPE_HEADER);
    }

    if (firstView == null || Math.min(recyclerView.getMeasuredWidth(), recyclerView.getMeasuredHeight()) == 0) {
      manager.scrollToPositionWithOffset(0, -desiredOffset);
    } else if (scrollTop > -desiredOffset) {
      recyclerView.stopScroll();
      recyclerView.smoothScrollBy(0, desiredOffset + scrollTop);
    }
  }

  protected final void onUserMovedCamera () {
    setFocusMode(FOCUS_MODE_CUSTOM, null);
  }

  @Override
  public void onThemeChanged (ThemeDelegate oldTheme, ThemeDelegate newTheme) {
    super.onThemeChanged(oldTheme, newTheme);
    if (mapType == Settings.MAP_TYPE_UNSET) {
      boolean needDark;
      if (oldTheme.isDark() != (needDark = newTheme.isDark())) {
        int oldType, newType;
        if (needDark) {
          oldType = Settings.MAP_TYPE_DEFAULT;
          newType = Settings.MAP_TYPE_DARK;
        } else {
          oldType = Settings.MAP_TYPE_DARK;
          newType = Settings.MAP_TYPE_DEFAULT;
        }
        onApplyMapType(oldType, newType);
      }
    }
  }

  private int mapType;

  private void setMapType (int mapType) {
    if (this.mapType != mapType) {
      int oldMapType = mapType();
      this.mapType = mapType;
      if (mapType == Settings.MAP_TYPE_UNSET || wouldRememberMapType(mapType)) {
        Settings.instance().setMapType(mapType);
      }
      int newMapType = mapType();
      if (oldMapType != newMapType) {
        onApplyMapType(oldMapType, newMapType);
      }
    }
  }

  // Other

  private boolean inShareProgress;

  @Override
  public void onClick (View v) {
    Args args = getArgumentsStrict();
    switch (v.getId()) {
      case R.id.liveLocation: {
        LocationPoint<T> point = (LocationPoint<T>) ((ListItem) v.getTag()).getData();
        if (focusMode == FOCUS_MODE_TARGET && focusTarget == point) {
          setFocusMode(FOCUS_MODE_DEFAULT, null);
        } else if (point != null) {
          setFocusMode(FOCUS_MODE_TARGET, point);
        }
        if (BuildConfig.DEBUG) {
          adapter.updateValuedSettingByPosition(adapter.indexOfViewByData(point));
        }
        break;
      }
      case R.id.liveLocationSelf: {
        if (inShareProgress)
          return;
        TdApi.Message msg = tdlib.cache().findOutputLiveLocationMessage(args.chatId);
        this.inShareProgress = true;
        adapter.updateValuedSettingById(R.id.liveLocationSelf);
        if (msg != null) {
          tdlib.client().send(new TdApi.EditMessageLiveLocation(msg.chatId, msg.id, null, null, 0, 0), tdlib.okHandler());
        } else {
          locationHelper.receiveLocation(REQUEST_SHARE_LIVE, null, 10000, true);
        }
        break;
      }
      case R.id.place: {
        /*if (!hasChangedFocus) {
          hasChangedFocus = true;
          if (mapView != null) {
            updateCameraAutomatically();
          }
          return;
        }*/
        if (focusMode != FOCUS_MODE_DEFAULT && focusMode != FOCUS_MODE_SELF && !(focusMode == FOCUS_MODE_TARGET && hasFocusPoint() && focusTarget != pointsOfInterest.get(0))) {
          if (myLocation == null) {
            if (focusMode == FOCUS_MODE_TARGET) {
              setLoadingLocation(true);
              locationHelper.receiveLocation(REQUEST_FOCUS_DEFAULT, null, -1, true);
            } else {
              setFocusMode(FOCUS_MODE_TARGET, pointsOfInterest.get(0));
            }
          } else {
            setFocusMode(FOCUS_MODE_DEFAULT, null);
          }
        } else if (hasFocusPoint()) {
          setFocusMode(FOCUS_MODE_TARGET, pointsOfInterest.get(0));
        }
        break;
      }
      case R.id.btn_direction: {
        if (!onBuildDirectionTo(mapView, args.latitude, args.longitude)) {
          Intents.openDirections(args.latitude, args.longitude, args.title, args.address);
        }
        break;
      }
      case R.id.btn_gps: {
        if (focusMode == FOCUS_MODE_SELF) {
          myLocationButton.setInProgress(false);
          locationHelper.cancel();
          setFocusMode(FOCUS_MODE_DEFAULT, null);
        } else {
          myLocationButton.setInProgress(true);
          locationHelper.receiveLocation(REQUEST_FOCUS_SELF, null, -1, true);
        }
        break;
      }
      case R.id.btn_layer: {
        IntList ids = new IntList(4);
        StringList strings = new StringList(4);
        int[] mapTypes = getAvailableMapTypes();
        for (int mapType : mapTypes) {
          int id, string;
          switch (mapType) {
            case Settings.MAP_TYPE_DEFAULT:
              id = R.id.btn_layerTypeMapDefault;
              string = R.string.LayerMapDefault;
              break;
            case Settings.MAP_TYPE_DARK:
              id = R.id.btn_layerTypeMapDark;
              string = R.string.LayerMapDark;
              break;
            case Settings.MAP_TYPE_SATELLITE:
              id = R.id.btn_layerTypeMapSatellite;
              string = R.string.LayerMapSatellite;
              break;
            case Settings.MAP_TYPE_TERRAIN:
              id = R.id.btn_layerTypeMapTerrain;
              string = R.string.LayerMapTerrain;
              break;
            case Settings.MAP_TYPE_HYBRID:
              id = R.id.btn_layerTypeMapHybrid;
              string = R.string.LayerMapHybrid;
              break;
            default:
              throw new IllegalArgumentException();
          }
          ids.append(id);
          strings.append(string);
        }
        if (!ids.isEmpty()) {
          showMore(ids.get(), strings.get(), 0);
        }
        break;
      }
    }
  }

  @Override
  public void onMoreItemPressed (int id) {
    int newMapType;
    switch (id) {
      case R.id.btn_layerTypeMapDefault: newMapType = Settings.MAP_TYPE_DEFAULT; break;
      case R.id.btn_layerTypeMapDark: newMapType = Settings.MAP_TYPE_DARK; break;
      case R.id.btn_layerTypeMapSatellite: newMapType = Settings.MAP_TYPE_SATELLITE; break;
      case R.id.btn_layerTypeMapTerrain: newMapType = Settings.MAP_TYPE_TERRAIN; break;
      case R.id.btn_layerTypeMapHybrid: newMapType = Settings.MAP_TYPE_HYBRID; break;
      default: {
        return;
      }
    }
    if (mapType() != newMapType && ((newMapType == Settings.MAP_TYPE_DEFAULT && !Theme.isDark()) || (newMapType == Settings.MAP_TYPE_DARK && Theme.isDark()))) {
      newMapType = Settings.MAP_TYPE_UNSET;
    }
    setMapType(newMapType);
  }

  // Points

  protected final @NonNull List<LocationPoint<T>> pointsOfInterest () {
    if (pointsOfInterest == null) {
      throw new IllegalStateException();
    }
    return pointsOfInterest;
  }

  protected void onPointOfInterestMoved (LocationPoint<T> point, int fromIndex, int toIndex) {
    // override
  }
  protected void onPointOfInterestAdded (LocationPoint<T> point, int toIndex) {
    // override
  }
  protected void onPointOfInterestRemoved (LocationPoint<T> point, int fromIndex) {
    // override
  }
  protected void onPointOfInterestCoordinatesChanged (LocationPoint<T> point, int index) {
    // override
  }
  protected void onPointOfInterestActiveStateMightChanged (LocationPoint<T> point, boolean isActive) {
    // override
  }
  protected void onPointOfInterestFocusStateChanged (LocationPoint<T> point, boolean isFocused) {
    // override
  }

  // Live location output listener

  @Override
  public void onLiveLocationBroadcast (@Nullable TdApi.Location location, int heading) { }

  @Override
  public void onLiveLocationErrorState (boolean inErrorState) { }

  @Override
  public void onLiveLocationsLoaded (ArrayList<Tdlib> tdlibs, ArrayList<ArrayList<TdApi.Message>> messages) {
    if (!isDestroyed()) {
      checkIsSharing(true);
    }
  }

  @Override
  public void onLiveLocationsListChanged (Tdlib tdlib, @Nullable ArrayList<TdApi.Message> messages) {
    if (!isDestroyed()) {
      checkIsSharing(true);
    }
  }

  @Override
  public void onLiveLocationMessageEdited (Tdlib tdlib, TdApi.Message message) {
    if (isSharingLiveLocation && this.tdlib.id() == tdlib.id() && getArgumentsStrict().chatId == message.chatId) {
      adapter.updateValuedSettingById(R.id.liveLocationSelf);
    }
  }

  private boolean isSharingLiveLocation;

  protected final boolean isSharingLiveLocation () {
    return isSharingLiveLocation;
  }

  private void checkIsSharing (boolean byUserRequest) {
    TdApi.Message sharingMessage = tdlib.cache().findOutputLiveLocationMessage(getArgumentsStrict().chatId);
    boolean isSharing = sharingMessage != null;
    if (this.isSharingLiveLocation != isSharing) {
      this.isSharingLiveLocation = isSharing;
      inShareProgress = false;
      if (myLocation != null) {
        if (isSharing) {
          Log.i("adding my location, because started sharing");
          onPointOfInterestAdded(myLocation, -1);
        } else {
          onPointOfInterestRemoved(myLocation, -1);
        }
        onLivePersonListChanged(byUserRequest, true);
      }
      adapter.updateValuedSettingById(R.id.liveLocationSelf);
      if (!isSharing && getArgumentsStrict().navigateBackOnStop) {
        navigateBack();
      }
    }
  }

  private void onLivePersonListChanged (boolean force, boolean resetFocusChange) {
    if (focusMode != FOCUS_MODE_DEFAULT) {
      setFocusMode(FOCUS_MODE_DEFAULT, null);
    } else if (force || getArgumentsStrict().mode != MODE_DROPPED_PIN) {
      /*if (resetFocusChange) {
        this.hasChangedFocus = true;
      }*/
      if (mapView != null) {
        updateCameraAutomatically();
      }
    }
  }

  // Live Locations cells

  private ListItem newLiveLocationCell (LocationPoint<T> point) {
    return new ListItem(ListItem.TYPE_ATTACH_LOCATION_BIG, R.id.liveLocation).setData(point);
  }

  // Live Locations list

  private List<LocationPoint<T>> pointsOfInterest;
  private @Nullable List<LocationPoint<T>> tempPointsOfInterest;

  private int indexOfLiveLocation (long messageId) {
    int i = 0;
    for (LocationPoint<T> point : pointsOfInterest) {
      if (point.message != null && point.message.id == messageId) {
        return i;
      }
      i++;
    }
    return -1;
  }

  private int indexOfLiveLocationBySender (TdApi.MessageSender sender, long messageId) {
    int i = 0;
    int bestGuess = -1;
    for (LocationPoint<T> point : pointsOfInterest) {
      if (point.message != null) {
        if (point.message.id == messageId) {
          return i;
        }
        if (Td.equalsTo(point.message.senderId, sender) && !(i == 0 && hasFocusPoint())) {
          bestGuess = i;
        }
      }
      i++;
    }
    return bestGuess;
  }

  private int dataToAdapterPosition (int position) {
    if (hasFocusPoint()) {
      position--;
    }
    return liveLocationsStartIndex() + position;
  }

  private int getLiveLocationCount () {
    int size = pointsOfInterest.size();
    if (hasFocusPoint()) {
      size--;
    }
    return size;
  }

  private int liveLocationsStartIndex () {
    int size = adapter.getItems().size();
    int liveLocationCount = pointsOfInterest.size();
    if (hasFocusPoint()) {
      liveLocationCount--;
    }
    return size - liveLocationCount/* - 1*/;
  }

  private void displayLiveLocations (@NonNull ArrayList<LocationPoint<T>> points) {
    int pointsStartCount = pointsOfInterest.size();
    pointsOfInterest.addAll(points);

    final List<ListItem> items = adapter.getItems();
    final int cellCount = items.size();

    // int splitInsertIndex = -1;
    // LocationPoint<T> ignoreAdd = null;
    int headerItemCount = 0;

    if (getArgumentsStrict().mode == MODE_DROPPED_PIN) {
      headerItemCount = 4;
      ArrayUtils.ensureCapacity(items, cellCount + points.size() + headerItemCount);
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.LiveLocations));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      if (tdlib.canSendBasicMessage(getArgumentsStrict().chatId)) {
        items.add(newShareLiveLocationCell());
      }
    } else {
      ArrayUtils.ensureCapacity(items, cellCount + points.size());
      /*LocationPoint<T> oldPoint = pointsStartCount == 1 ? pointsOfInterest.get(0) : null;
      if (oldPoint != null && oldPoint.isLiveLocation) {
        Collections.sort(pointsOfInterest, this);
        int newIndex = pointsOfInterest.indexOf(oldPoint);
        if (newIndex > 0) {
          splitInsertIndex = newIndex;
          ignoreAdd = pointsOfInterest.get(0);
          items.remove(cellCount - 1);

        }
      }*/
    }

    int i = 0;
    for (LocationPoint<T> point : points) {
      items.add(newLiveLocationCell(point));
      onPointOfInterestAdded(point, pointsStartCount + i);
      i++;
    }

    adapter.notifyItemRangeInserted(cellCount, points.size() + headerItemCount);
    resortList();

    onLivePersonListChanged(false, true);
    bringItemsToFront(true);
  }

  private void checkLiveLocationCoordinates (LocationPoint<T> point, int dataPosition) {
    if (point.message == null) {
      return;
    }
    TdApi.MessageLocation messageLocation = (TdApi.MessageLocation) point.message.content;
    TdApi.Location location = messageLocation.location;
    point.expiresAt = SystemClock.uptimeMillis() + (long) messageLocation.expiresIn * 1000l;
    if (location.latitude != point.latitude || location.longitude != point.longitude) {
      point.latitude = location.latitude;
      point.longitude = location.longitude;
      onPointOfInterestCoordinatesChanged(point, dataPosition);
      if (focusMode == FOCUS_MODE_TARGET && focusTarget == point) {
        onPositionRequested(mapView, point, hasFocused, false, false);
      } else {
        updateCameraAutomatically();
      }
    } else {
      onPointOfInterestActiveStateMightChanged(point, ((TdApi.MessageLocation) point.message.content).expiresIn > 0);
    }
  }

  private void checkLiveLocationPosition (LocationPoint<T> point, int currentPosition) {
    checkLiveLocationCoordinates(point, currentPosition);
    resortList();
    /*int adapterPosition = dataToAdapterPosition(currentPosition);
    if (pointsOfInterest.size() == 1) {
      adapter.updateValuedSettingByPosition(adapterPosition);
      return;
    }
    if (pointsOfInterest.remove(currentPosition) != point)
      throw new IllegalArgumentException();
    int bestPosition = Collections.binarySearch(pointsOfInterest, point, this);
    int newPosition = (-bestPosition) - 1;
    if (bestPosition >= 0 || newPosition == currentPosition) { // impossible condition, do nothing
      pointsOfInterest.add(currentPosition, point);
      adapter.updateValuedSettingByPosition(adapterPosition);
    } else {
      pointsOfInterest.add(newPosition, point);
      onPointOfInterestMoved(point, currentPosition, newPosition);
      int newAdapterPosition = dataToAdapterPosition(newPosition);
      adapter.moveItem(adapterPosition, newAdapterPosition);
      adapter.updateValuedSettingByPosition(newAdapterPosition);
    }*/
  }

  private void addLiveLocation (@NonNull TdApi.Message message) {
    int i = indexOfLiveLocationBySender(message.senderId, message.id);
    if (i != -1) {
      LocationPoint<T> point = pointsOfInterest.get(i);
      point.setSourceMessage(message, true);
      checkLiveLocationPosition(point, i);
      return;
    }

    int cellStartIndex = liveLocationsStartIndex();

    TdApi.Location location = ((TdApi.MessageLocation) message.content).location;
    LocationPoint<T> point = new LocationPoint<>(this, location.latitude, location.longitude);
    point.setSourceMessage(message, true);

    int bestPosition = Collections.binarySearch(pointsOfInterest, point, this);
    if (bestPosition >= 0) { // impossible condition, do nothing
      return;
    }

    List<ListItem> items = adapter.getItems();

    boolean needHeader = getLiveLocationCount() == 0 && getArgumentsStrict().mode == MODE_DROPPED_PIN;

    int newPosition = (-bestPosition) - 1;
    pointsOfInterest.add(newPosition, point);
    onPointOfInterestAdded(point, newPosition);

    Args args = getArgumentsStrict();

    if (!needHeader) {
      items.add(newLiveLocationCell(point));
      adapter.notifyItemInserted(cellStartIndex + newPosition - (hasFocusPoint() ? 1 : 0));
    } else {
      int startSize = items.size();
      int headerItemCount = 3;
      boolean hasWritePermission = tdlib.canSendBasicMessage(args.chatId);
      if (hasWritePermission) {
        headerItemCount++;
      }
      ArrayUtils.ensureCapacity(items, items.size() + headerItemCount);
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.LiveLocations));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      if (hasWritePermission) {
        items.add(newShareLiveLocationCell());
      }
      items.add(newLiveLocationCell(point));
      adapter.notifyItemRangeInserted(startSize, headerItemCount + 1);
    }

    onLivePersonListChanged(true, true);
    bringItemsToFront(true);
  }

  private void removeLiveLocationMessage (LocationPoint<T> point, int currentPosition) {
    int startCellCount = adapter.getItems().size();
    int fromAdapterPosition = dataToAdapterPosition(currentPosition);

    pointsOfInterest.remove(currentPosition);
    onPointOfInterestRemoved(point, currentPosition);

    boolean needRemoveHeader =  getLiveLocationCount() == 0 && getArgumentsStrict().mode == MODE_DROPPED_PIN;

    if (needRemoveHeader) {
      int removeItemCount = 1 + 3;
      if (adapter.getItems().get(startCellCount - 2).getId() == R.id.liveLocationSelf) {
        removeItemCount++;
      }
      adapter.removeRange(startCellCount - removeItemCount, removeItemCount);
    } else {
      adapter.removeItem(fromAdapterPosition);
    }

    onLivePersonListChanged(true, true);
  }

  private void updateLiveLocationMessage (long messageId, @NonNull TdApi.MessageLocation location) {
    int currentPosition = indexOfLiveLocation(messageId);
    if (currentPosition != -1) {
      LocationPoint<T> point = pointsOfInterest.get(currentPosition);
      point.message.content = location;
      if (location.expiresIn == 0 && hasFocusPoint() && getArgumentsStrict().message != null) {
        removeLiveLocationMessage(point, currentPosition);
      } else {
        checkLiveLocationPosition(point, currentPosition);
      }
    }
  }

  private void removeLiveLocations (long[] messageIds) {
    final int size = pointsOfInterest.size();
    int removedCount = 0;
    for (int i = size - 1; i >= 0; i--) {
      LocationPoint<T> point = pointsOfInterest.get(i);
      if (point.message != null && ArrayUtils.indexOf(messageIds, point.message.id) != -1) {
        removeLiveLocationMessage(point, i);
        if (++removedCount == messageIds.length) {
          break;
        }
      }
    }
  }

  private void updateLiveLocationDate (long messageId, int editDate) {
    int i = indexOfLiveLocation(messageId);
    if (i != -1) {
      LocationPoint<T> point = pointsOfInterest.get(i);
      point.message.editDate = editDate;
      // doing nothing, cell will move in updateLiveLocationMessage
    }
  }

  // Live locations list

  @Override
  public void onResult (TdApi.Object object) {
    if (isDestroyed()) {
      return;
    }
    switch (object.getConstructor()) {
      case TdApi.Messages.CONSTRUCTOR: {
        TdApi.Message[] messages = ((TdApi.Messages) object).messages;
        if (messages.length > 0) {
          final ArrayList<LocationPoint<T>> list = new ArrayList<>(messages.length);
          for (TdApi.Message message : messages) {
            if (message.content.getConstructor() != TdApi.MessageLocation.CONSTRUCTOR) {
              continue;
            }
            if (message.isOutgoing || tdlib.isSelfSender(message)) {
              continue;
            }
            LocationPoint<T> ignorePoint = pointsOfInterest.isEmpty() ? null : pointsOfInterest.get(0);
            TdApi.Message ignoreMessage = ignorePoint != null ? ignorePoint.message : null;
            TdApi.MessageLocation location = (TdApi.MessageLocation) message.content;
            if (location.livePeriod > 0 && (ignoreMessage == null || ignoreMessage.id != message.id) && (location.expiresIn > 0 || ignoreMessage == null || getArgumentsStrict().mode == MODE_LIVE_LOCATION)) {
              list.add(new LocationPoint<>(this, location.location.latitude, location.location.longitude).setSourceMessage(message, true));
            }
          }
          if (list.isEmpty()) {
            return;
          }
          Collections.sort(list, this);
          runOnUiThread(() -> {
            if (!isDestroyed()) {
              displayLiveLocations(list);
            }
          });
        }
        break;
      }
      case TdApi.Error.CONSTRUCTOR: {
        Log.w("Error: %s", TD.toErrorString(object));
        break;
      }
    }
  }

  @TdlibThread
  private void addMessageIfNeeded (final TdApi.Message message) {
    if (isDestroyed()) {
      return;
    }
    if (message.content.getConstructor() != TdApi.MessageLocation.CONSTRUCTOR) {
      return;
    }
    if (message.schedulingState != null || tdlib.isSelfSender(message)) {
      return;
    }
    if (((TdApi.MessageLocation) message.content).livePeriod <= 0) {
      return;
    }
    executeOnUiThread(() -> {
      if (!isDestroyed()) {
        addLiveLocation(message);
      }
    });
  }

  private void updateMessageIfNeeded (final long chatId, final long messageId, final TdApi.MessageLocation location) {
    if (isDestroyed()) {
      return;
    }
    if (location.livePeriod <= 0) {
      return;
    }
    executeOnUiThread(() -> {
      if (!isDestroyed() && getArgumentsStrict().chatId == chatId) {
        updateLiveLocationMessage(messageId, location);
      }
    });
  }

  @Override
  public void onNewMessage (final TdApi.Message message) {
    addMessageIfNeeded(message);
  }

  @Override
  public void onMessageSendAcknowledged (long chatId, long messageId) { }

  @Override
  public void onMessageSendSucceeded (TdApi.Message message, long oldMessageId) {
    addMessageIfNeeded(message);
  }

  @Override
  public void onMessageContentChanged (long chatId, long messageId, TdApi.MessageContent newContent) {
    if (newContent.getConstructor() == TdApi.MessageLocation.CONSTRUCTOR) {
      updateMessageIfNeeded(chatId, messageId, (TdApi.MessageLocation) newContent);
    }
  }

  @Override
  public void onMessageSendFailed (TdApi.Message message, long oldMessageId, int errorCode, String errorMessage) { }

  @Override
  public int compare (LocationPoint<T> o1, LocationPoint<T> o2) {
    boolean f1 = o1.isFocusPoint;
    boolean f2 = o2.isFocusPoint;
    if (f1 != f2) {
      return f1 ? -1 : 1;
    }

    boolean n1 = o1.message == null;
    boolean n2 = o2.message == null;
    if (n1 != n2) {
      return n1 ? -1 : 1;
    }

    boolean needCompareDistance;
    double compareToLatitude, compareToLongitude;
    if (needCompareDistance = (hasFocusPoint() && getArgumentsStrict().message == null)) {
      Args args = getArgumentsStrict();
      compareToLatitude = args.latitude;
      compareToLongitude = args.longitude;
    } else if (needCompareDistance = myLocation != null) {
      compareToLatitude = myLocation.latitude;
      compareToLongitude = myLocation.longitude;
    } else {
      compareToLatitude = compareToLongitude = 0;
    }

    if (o1.message == null) {
      float d1 = U.distanceBetween(compareToLatitude, compareToLongitude, o1.latitude, o1.longitude);
      float d2 = U.distanceBetween(compareToLatitude, compareToLongitude, o2.latitude, o2.longitude);
      if (d1 != d2) {
        return (d1 < d2) ? -1 : 1;
      }
      return 0;
    }

    boolean s1 = o1.isSelfLocation;
    boolean s2 = o2.isSelfLocation;
    if (s1 != s2) {
      return s1 ? -1 : 1;
    }

    long now = SystemClock.uptimeMillis();
    boolean e1 = o1.expiresAt < now;
    boolean e2 = o2.expiresAt < now;

    if (e1 != e2) {
      return e1 ? 1 : -1;
    }

    TdApi.Message m1 = o1.message;
    TdApi.Message m2 = o2.message;

    TdApi.MessageLocation l1 = (TdApi.MessageLocation) m1.content;
    TdApi.MessageLocation l2 = (TdApi.MessageLocation) m2.content;

    if (needCompareDistance) {
      float d1 = U.distanceBetween(compareToLatitude, compareToLongitude, l1.location.latitude, l1.location.longitude);
      float d2 = U.distanceBetween(compareToLatitude, compareToLongitude, l2.location.latitude, l2.location.longitude);
      if (d1 != d2) {
        return (d1 < d2) ? -1 : 1;
      }
    }

    int t1 = Math.max(m1.date, m1.editDate);
    int t2 = Math.max(m2.date, m2.editDate);

    if (t1 != t2) {
      return Integer.compare(t1, t2);
    }

    long sender1 = Td.getSenderId(m1);
    long sender2 = Td.getSenderId(m2);
    if (sender1 != sender2) {
      return Long.compare(sender1, sender2);
    }

    return Long.compare(m1.id, m2.id);
  }

  @Override
  public void onMessagesDeleted (final long chatId, final long[] messageIds) {
    if (!isDestroyed()) {
      runOnUiThread(() -> {
        if (!isDestroyed() && getArgumentsStrict().chatId == chatId) {
          removeLiveLocations(messageIds);
        }
      });
    }
  }

  @Override
  public void onMessageEdited (final long chatId, final long messageId, final int editDate, @Nullable TdApi.ReplyMarkup replyMarkup) {
    if (!isDestroyed()) {
      runOnUiThread(() -> {
        if (!isDestroyed() && getArgumentsStrict().chatId == chatId) {
          updateLiveLocationDate(messageId, editDate);
        }
      });
    }
  }
}
