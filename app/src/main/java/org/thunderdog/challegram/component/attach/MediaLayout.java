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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;

import com.google.android.gms.maps.MapsInitializer;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.mediaview.AvatarPickerMode;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.navigation.ActivityResultHandler;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.BackListener;
import org.thunderdog.challegram.navigation.CounterHeaderView;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.RightId;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeChangeListener;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.CreatePollController;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.ui.SetSenderController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.HapticMenuHelper;
import org.thunderdog.challegram.util.Permissions;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.SendButton;
import org.thunderdog.challegram.widget.ShadowView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.DeviceUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;
import tgx.td.Td;
import tgx.td.TdConstants;

public class MediaLayout extends FrameLayoutFix implements
  MediaBottomBar.Callback, BaseActivity.PopupAnimatorOverride,
  View.OnClickListener, BaseActivity.ActivityListener, ActivityResultHandler, /*ActivityResultCancelHandler,*/ BackListener,
  PopupLayout.AnimatedPopupProvider, PopupLayout.DismissListener, FactorAnimator.Target, ThemeChangeListener, Lang.Listener, Destroyable, PopupLayout.TouchDownInterceptor {

  private interface MediaCallback { }

  public interface MediaGalleryCallback extends MediaCallback {
    void onSendVideo (ImageGalleryFile file, boolean isFirst);
    void onSendPhoto (ImageGalleryFile file, boolean isFirst);
  }

  public static final long REVEAL_DURATION = 220l;
  public static final long REVEAL_HIDE_DURATION = 285l;

  public static final int MODE_DEFAULT = 0;
  public static final int MODE_LOCATION = 1;
  public static final int MODE_GALLERY = 2;
  public static final int MODE_CUSTOM_POPUP = 3;
  public static final int MODE_AVATAR_PICKER = 4;
  public static final int MODE_CUSTOM_ADAPTER = 5;

  private int mode;
  private @AvatarPickerMode int avatarPickerMode = AvatarPickerMode.NONE;
  private @Nullable MediaCallback callback;

  // Data
  private boolean noMediaAccess;
  private @Nullable MessagesController target;

  // Children
  private MediaBottomBaseController<?>[] controllers;
  private @Nullable MediaBottomBar bottomBar;
  private @Nullable ShadowView shadowView;
  private MediaBottomBaseController<?> currentController;

  private View customBottomBar;

  private final ThemeListenerList themeListeners = new ThemeListenerList();

  private final ViewController<?> parent;

  public MediaLayout (ViewController<?> context) {
    super(context.context());
    this.parent = context;
  }

  public Tdlib tdlib () {
    return parent.tdlib();
  }

  public void initDefault (MessagesController target) {
    init(MODE_DEFAULT, target);
  }

  public int getMode () {
    return mode;
  }

  public int getAvatarPickerMode () {
    return avatarPickerMode;
  }

  private boolean singleMediaMode;

  public void setSingleMediaMode (boolean singleMediaMode) {
    this.singleMediaMode = singleMediaMode;
  }

  public boolean inSingleMediaMode () {
    return singleMediaMode || avatarPickerMode != AvatarPickerMode.NONE || mode == MODE_AVATAR_PICKER;
  }

  public void setAvatarPickerMode (@AvatarPickerMode int avatarPickerMode) {
    this.avatarPickerMode = avatarPickerMode;
  }

  private boolean rtl, needVote;

  private ItemsAdapter itemsAdapter;

  public void setItemsAdapter (ItemsAdapter itemsAdapter) {
    this.itemsAdapter = itemsAdapter;
  }

  public interface ItemsAdapter {
    MediaBottomBar.BarItem[] getBottomBarItems ();
    int getDefaultItemIndex ();
    MediaBottomBaseController<?> createControllerForIndex(int index);
    boolean needBottomBar ();
  }

  public void init (int mode, MessagesController target) {
    this.mode = mode;
    this.target = target;
    this.rtl = Lang.rtl();
    this.needVote = false;
    final MediaBottomBar.BarItem[] items;
    final int index;

    switch (mode) {
      case MODE_LOCATION: {
        items = new MediaBottomBar.BarItem[] {
          new MediaBottomBar.BarItem(R.drawable.baseline_location_on_24, R.string.Location, ColorId.attachLocation, Screen.dp(1f))
        };
        index = 0;
        break;
      }
      case MODE_AVATAR_PICKER:
      case MODE_GALLERY: {
        items = new MediaBottomBar.BarItem[] {
          new MediaBottomBar.BarItem(R.drawable.baseline_location_on_24, R.string.Gallery, ColorId.attachPhoto, Screen.dp(1f))
        };
        index = 0;
        break;
      }
      case MODE_CUSTOM_ADAPTER: {
        items = itemsAdapter.getBottomBarItems();
        index = itemsAdapter.getDefaultItemIndex();
        if (itemsAdapter.needBottomBar()) {
          mode = MODE_DEFAULT;
        }
        break;
      }
      default: {
        this.needVote = tdlib().canSendPolls(getTargetChatId());
        if (rtl) {
          items = new MediaBottomBar.BarItem[]{
            needVote ?
              new MediaBottomBar.BarItem(R.drawable.baseline_poll_24, R.string.CreatePoll, ColorId.attachInlineBot) :
              new MediaBottomBar.BarItem(R.drawable.deproko_baseline_bots_24, R.string.InlineBot, ColorId.attachInlineBot),
            new MediaBottomBar.BarItem(R.drawable.baseline_location_on_24, R.string.Location, ColorId.attachLocation, Screen.dp(1f)),
            new MediaBottomBar.BarItem(R.drawable.baseline_image_24, R.string.Gallery, ColorId.attachPhoto),
            new MediaBottomBar.BarItem(R.drawable.baseline_insert_drive_file_24, R.string.File, ColorId.attachFile),
            new MediaBottomBar.BarItem(R.drawable.baseline_person_24, R.string.AttachContact, ColorId.attachContact, Screen.dp(1f))
          };
        } else {
          items = new MediaBottomBar.BarItem[]{
            new MediaBottomBar.BarItem(R.drawable.baseline_person_24, R.string.AttachContact, ColorId.attachContact, Screen.dp(1f)),
            new MediaBottomBar.BarItem(R.drawable.baseline_insert_drive_file_24, R.string.File, ColorId.attachFile),
            new MediaBottomBar.BarItem(R.drawable.baseline_image_24, R.string.Gallery, ColorId.attachPhoto),
            new MediaBottomBar.BarItem(R.drawable.baseline_location_on_24, R.string.Location, ColorId.attachLocation, Screen.dp(1f)),
            needVote ?
              new MediaBottomBar.BarItem(R.drawable.baseline_poll_24, R.string.CreatePoll, ColorId.attachInlineBot) :
              new MediaBottomBar.BarItem(R.drawable.deproko_baseline_bots_24, R.string.InlineBot, ColorId.attachInlineBot)
          };
        }
        index = 2;
        mode = MODE_DEFAULT;
        break;
      }
    }

    controllers = new MediaBottomBaseController[items.length];

    if (mode == MODE_DEFAULT) {
      bottomBar = new MediaBottomBar(getContext());
      bottomBar.setItems(items, index);
      bottomBar.setCallback(this);
      shadowView = new ShadowView(getContext());
      shadowView.setSimpleTopShadow(true);
      themeListeners.addThemeInvalidateListener(bottomBar);
      FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(shadowView.getLayoutParams());
      params.bottomMargin = bottomBar.getLayoutParams().height;
      params.gravity = Gravity.BOTTOM;
      shadowView.setLayoutParams(params);
      themeListeners.addThemeInvalidateListener(shadowView);
    }

    currentController = getControllerForIndex(index);
    View controllerView = currentController.getValue();
    if (currentController != null) {
      setAllowSpoiler(currentController.allowSpoiler());
    }

    addView(controllerView);
    if (mode == MODE_DEFAULT) {
      addView(bottomBar);
      addView(shadowView);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && false) { // FIXME
      setElevation(Screen.dp(10f));
      setTranslationZ(Screen.dp(1f));
      setOutlineProvider(new android.view.ViewOutlineProvider() {
        @TargetApi(value = 21)
        @Override
        public void getOutline (View view, android.graphics.Outline outline) {
          int top = currentController.getCurrentHeight() + (int) currentController.getValue().getTranslationY();
          int bottom = getMeasuredHeight();

          int left = 0;
          int right = getMeasuredWidth();

          outline.setRect(left, top, right, bottom);
        }
      });
    }

    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    ThemeManager.instance().addThemeListener(this);
    Lang.addLanguageListener(this);
  }

  public void initCustom () {
    mode = MODE_CUSTOM_POPUP;
    controllers = new MediaBottomBaseController[1];
    currentController = getControllerForIndex(0);
    View controllerView = currentController.getValue();

    addView(controllerView);

    if (mode == MODE_DEFAULT) {
      setCustomBottomBar(currentController.createCustomBottomBar());
    }

    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    ThemeManager.instance().addThemeListener(this);
    Lang.addLanguageListener(this);
  }

  public void setCustomBottomBar (View bottomBar) {
    addView(customBottomBar = bottomBar);
    themeListeners.addThemeInvalidateListener(customBottomBar);
    customBottomBar.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    customBottomBar.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.EXACTLY);
  }

  public View getCustomBottomBar () {
    return customBottomBar;
  }

  @Override
  public void onLanguagePackEvent (int event, int arg1) {
    if (Lang.hasDirectionChanged(event, arg1)) {
      // TODO checkRtl();
    }
  }

  @Override
  public boolean needsTempUpdates () {
    return true;
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    themeListeners.onThemeColorsChanged(areTemp);
    if (headerView != null) {
      MediaBottomBaseController<?> c = getCurrentController();
      if (c != null) {
        headerView.resetColors(c, null);
      }
    }
  }

  public void setCallback (@NonNull MediaCallback callback) {
    this.callback = callback;
  }

  public boolean inSpecificMode () {
    return mode != MODE_DEFAULT;
  }

  public void onConfirmExit (boolean isExitingSelection) {
    if (isExitingSelection) {
      cancelMultiSelection();
    } else {
      hide(false);
    }
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    MediaBottomBaseController<?> c = getCurrentController();
    if (c.isAnimating()) {
      return true;
    }
    if (c.onBackPressed(fromTop)) {
      return true;
    }
    if (counterView != null && counterView.isEnabled()) {
      if (!c.showExitWarning(false)) {
        cancelMultiSelection();
      }
      return true;
    }
    if (c.isExpanded()) {
      c.collapseToStart();
      return true;
    }
    return c.showExitWarning(false);
  }

  public long getTargetChatId () {
    return target != null ? target.getChatId() : 0;
  }

  public long getTargetMessageThreadId () {
    return target != null ? target.getMessageThreadId() : 0;
  }

  public boolean areScheduledOnly () {
    return target != null && target.areScheduledOnly();
  }

  public MessagesController getTarget () {
    return target;
  }

  public int getCurrentContentWidth () {
    return bottomBar != null ? bottomBar.getCurrentBarWidth() : Screen.currentWidth();
  }

  public HeaderView getHeaderView () {
    if (headerView == null) {
      prepareHeader();
    }
    return headerView;
  }

  private MediaBottomBaseController<?> getControllerForIndex (int index) {
    MediaBottomBaseController<?> c = controllers[index];
    if (c == null) {
      c = createControllerForIndex(index);
      c.attachToThemeListeners(themeListeners);
      controllers[index] = c;
    }
    return c;
  }

  public MediaBottomBaseController<?> createControllerForIndex (int index) {
    switch (mode) {
      case MODE_LOCATION: {
        return new MediaBottomLocationController(this);
      }
      case MODE_AVATAR_PICKER:
      case MODE_GALLERY: {
        MediaBottomGalleryController c = new MediaBottomGalleryController(this);
        c.setArguments(new MediaBottomGalleryController.Arguments(mode == MODE_GALLERY || (mode == MODE_AVATAR_PICKER && avatarPickerMode == AvatarPickerMode.NONE)));
        return c;
      }
      case MODE_CUSTOM_ADAPTER: {
        return itemsAdapter.createControllerForIndex(index);
      }
    }
    if (rtl) {
      index = controllers.length - index - 1;
    }
    switch (index) {
      case 0: return new MediaBottomContactsController(this);
      case 1: return new MediaBottomFilesController(this);
      case 2: return new MediaBottomGalleryController(this);
      case 3: return new MediaBottomLocationController(this);
      case 4: return new MediaBottomInlineBotsController(this);
      default: throw new IllegalArgumentException("Unknown index passed: " + index);
    }
  }

  private MediaBottomBaseController<?> getCurrentController () {
    return bottomBar != null ? controllers[bottomBar.getCurrentIndex()] : controllers[0];
  }

  private boolean disallowGallerySystemPicker;

  public void setDisallowGallerySystemPicker (boolean disallowGallerySystemPicker) {
    this.disallowGallerySystemPicker = disallowGallerySystemPicker;
  }

  public boolean isDisallowGallerySystemPicker () {
    return disallowGallerySystemPicker;
  }

  // Setters

  public void setNoMediaAccess () {
    noMediaAccess = true;
  }

  // Interaction

  public void preload (Runnable after, long timeout) {
    getCurrentController().preload(after, timeout);
  }

  private PopupLayout popupLayout;

  public void show () {
    show(false);
  }

  public void show (boolean overlayStatusBar) {
    popupLayout = new PopupLayout(getContext());
    popupLayout.setTouchDownInterceptor(this);
    popupLayout.setActivityListener(this);
    popupLayout.setHideKeyboard();
    popupLayout.setDismissListener(this);
    popupLayout.setNeedRootInsets();
    popupLayout.setOverlayStatusBar(overlayStatusBar);
    popupLayout.init(true);
    popupLayout.showAnimatedPopupView(this, this);
  }

  @Override
  public boolean onBackgroundTouchDown (PopupLayout popupLayout, MotionEvent e) {
    MediaBottomBaseController<?> c = getCurrentController();
    return c != null && c.showExitWarning(false);
  }

  @Override
  public void prepareShowAnimation () {

  }

  private PopupLayout openingPopup;

  @Override
  public void launchShowAnimation (PopupLayout openingPopup) {
    this.openingPopup = openingPopup;
    createCustomShowAnimator().start();
  }

  private PopupLayout pendingPopup;

  @Override
  public boolean launchHideAnimation (PopupLayout popup, FactorAnimator ignored) {
    pendingPopup = popup;
    Animator animator = createCustomHideAnimator();
    if (animator != null) {
      animator.start();
      return true;
    } else {
      pendingPopup = null;
    }
    return false;
  }

  private boolean hideCircular;
  private boolean hidden;

  public void setForceHidden () {
    if (!hidden) {
      hidden = true;
      onCurrentColorChanged();
    }
  }

  public boolean isHidden () {
    return hidden;
  }

  public void forceHide () {
    if (!hidden) {
      hidden = true;
      popupLayout.hideWindow(false);
    }
  }

  public void hide (boolean multi) {
    if (hidden) {
      return;
    }
    setForceHidden();
    hideCircular = multi;

    popupLayout.hideWindow(true);
  }

  public boolean isVisible () {
    return popupLayout != null && !popupLayout.isWindowHidden();
  }

  // Callbacks

  @Override
  public void onActivityResult (int requestCode, int resultCode, Intent data) {
    if (requestCode == Intents.ACTIVITY_RESULT_MANAGE_STORAGE) {
      onActivityPermissionResult(Intents.ACTIVITY_RESULT_MANAGE_STORAGE, UI.getContext(getContext()).permissions().canManageStorage());
      return;
    }

    ViewController<?> c = getCurrentController();
    if (c instanceof ActivityResultHandler) {
      ((ActivityResultHandler) c).onActivityResult(requestCode, resultCode, data);
    }
  }

  @Override
  public void onActivityPause () {
    getCurrentController().onActivityPause();
  }

  @Override
  public void onActivityResume () {
    getCurrentController().onActivityResume();
  }

  @Override
  public void onActivityDestroy () {
    for (MediaBottomBaseController<?> controller : controllers) {
      if (controller != null) {
        controller.onActivityDestroy();
      }
    }
  }

  private boolean ignorePermissions;

  @Override
  public void onActivityPermissionResult (int code, boolean granted) {
    switch (code) {
      case BaseActivity.REQUEST_FINE_LOCATION: {
        if (granted) {
          MediaBottomLocationController c = (MediaBottomLocationController) getControllerForIndex(inSpecificMode() ? 0 : 3);
          c.onLocationPermissionOk();
        } else {
          ViewController<?> c = UI.getCurrentStackItem(getContext());
          if (c != null) {
            c.openMissingLocationPermissionAlert(false);
          }
        }
        break;
      }
      case BaseActivity.REQUEST_USE_CAMERA: {
        if (granted) {
          openCamera();
        } else {
          ViewController<?> c = UI.getCurrentStackItem(getContext());
          if (c != null) {
            c.openMissingCameraPermissionAlert();
          }
        }
        break;
      }
      case Intents.ACTIVITY_RESULT_MANAGE_STORAGE:
      case BaseActivity.REQUEST_READ_STORAGE: {
        if (requestedPermissionIndex == 1) {
          if (granted) {
            if (bottomBar != null) {
              bottomBar.setSelectedIndex(1);
            }
          } else {
            ViewController<?> c = UI.getCurrentStackItem(getContext());
            if (c != null) {
              c.openMissingStoragePermissionAlert();
            }
          }
        }
        break;
      }
    }
    requestedPermissionIndex = -1;
  }

  private MediaBottomBaseController<?> from;
  private View fromView, toView;
  private int fromHeight, toHeight;
  private float fromY;

  private int requestedPermissionIndex = -1;

  @Override
  public boolean onBottomPrepareSectionChange (int fromIndex, int toIndex, boolean ignorePermissionsRequest) {
    if (counterFactor != 0f || (counterAnimator != null && counterAnimator.isAnimating()) || getCurrentController().isAnimating()) {
      return false;
    }

    switch (toIndex) {
      case 3: {
        boolean googleMapsInstalled;
        try {
          MapsInitializer.initialize(getContext());
          googleMapsInstalled = DeviceUtils.isApplicationInstalled(getContext(), U.PACKAGE_GOOGLE_MAPS, false);
        } catch (Throwable t) {
          googleMapsInstalled = false;
        }
        if (!googleMapsInstalled) {
          ViewController<?> c = UI.getCurrentStackItem(getContext());
          if (c != null) {
            c.openMissingGoogleMapsAlert();
          } else {
            UI.showToast(R.string.NoGoogleMaps, Toast.LENGTH_LONG);
          }
          return false;
        }
        break;
      }
      case 1: {
        if (!ignorePermissionsRequest && UI.getContext(getContext()).permissions().requestReadExternalStorage(Permissions.ReadType.ALL, grantType -> {
          // ignore grantType
          if (bottomBar != null) {
            bottomBar.setSelectedIndex(toIndex, true);
          }
        })) {
          return false;
        }
        break;
      }
      case 4: {
        if (needVote) {
          if (target != null && target.isFocused()) {
            long chatId = getTargetChatId();
            if (chatId != 0) {
              CreatePollController c = new CreatePollController(target.context(), target.tdlib());
              c.setArguments(new CreatePollController.Args(chatId, target.getMessageThread(), target));
              parent.navigateTo(c);
              hide(false);
            }
          }
          return false;
        }
        break;
      }
    }

    setNeedSpoiler(false);

    MediaBottomBaseController<?> to;

    from = controllers[fromIndex];
    fromHeight = from.getCurrentHeight();
    fromView = from.getValue();
    fromY = fromView.getTranslationY();

    to = getControllerForIndex(toIndex);
    toView = to.getValue();
    toHeight = to.getStartHeight();
    toView.setTranslationY(toHeight);
    if (to.isPaused()) {
      to.onActivityResume();
    }

    addView(toView, 1);

    return true;
  }

  public interface MediaViewArgumentsEditor {
    MediaViewController.Args edit (MediaViewController.Args args);
  }

  private MediaViewArgumentsEditor mediaViewControllerArgumentsEditor;

  public void setMediaViewControllerArgumentsEditor (MediaViewArgumentsEditor mediaViewControllerArgumentsEditor) {
    this.mediaViewControllerArgumentsEditor = mediaViewControllerArgumentsEditor;
  }

  public MediaViewController.Args prepareMediaViewArguments (MediaViewController.Args args) {
    if (mediaViewControllerArgumentsEditor != null) {
      return mediaViewControllerArgumentsEditor.edit(args);
    }

    return args;
  }

  @Override
  public void onBottomSectionChanged (int index) {
    removeView(fromView);
    from.resetState();
    from.onActivityPause();
    currentController = controllers[index];
    controllers[index].onCompleteShow(false);
    onCurrentColorChanged();
    if (mode == MODE_DEFAULT && target != null) {
      target.setBroadcastAction(controllers[index].getBroadcastingAction());
    }
  }

  @Override
  public void onBottomFactorChanged (float factor) {
    fromView.setTranslationY(fromY + Math.round((float) fromHeight * factor));
    toView.setTranslationY(toHeight - Math.round(toHeight * factor));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      invalidateOutline();
    }
    onCurrentColorChanged();
  }

  @Override
  public void onBottomTopRequested (int currentIndex) {
    if (controllers[currentIndex] != null) {
      controllers[currentIndex].expandFully();
    }
  }

  private void onCurrentColorChanged () {
    if (Config.USE_CUSTOM_NAVIGATION_COLOR && bottomBar != null) {
      UI.getContext(getContext()).setCustomNavigationColor(bottomBar::getCurrentColor, isDestroyed ? 0f : bottomBarFactor * (1f - counterFactor), hidden || (popupLayout != null && popupLayout.isWindowHidden()) || isCounterVisible);
    }
  }

  // Animation

  private boolean bottomBarAnimating;
  private float bottomBarFactor = 1f;

  public void setBottomBarFactor (float factor) {
    if (bottomBarFactor != factor) {
      this.bottomBarFactor = factor;
      updateBarPosition();
    }
  }

  private int getBottomBarHeight () {
    return customBottomBar != null ? customBottomBar.getMeasuredHeight() : MediaBottomBar.getBarHeight();
  }

  public int getCurrentBottomBarHeight () {
    return (int) ((float) getBottomBarHeight() * Math.max(bottomBarFactor, counterFactor));
  }

  private void updateBarPosition () {
    int height = getBottomBarHeight();
    float factor = Math.max(bottomBarFactor, counterFactor);
    float y = height - (int) ((float) height * factor);
    if (!inSpecificMode() || mode == MODE_AVATAR_PICKER || mode == MODE_CUSTOM_ADAPTER) {
      if (bottomBar != null) {
        if (currentController != null) {
          currentController.onUpdateBottomBarFactor(bottomBarFactor, counterFactor, y);
        }
        bottomBar.setTranslationY(y);
        onCurrentColorChanged();
      }
      if (currentController != null && mode == MODE_AVATAR_PICKER) {
        currentController.onUpdateBottomBarFactor(bottomBarFactor, counterFactor, y);
      }
      if (customBottomBar != null) {
        customBottomBar.setTranslationY(y);
        onCurrentColorChanged();
      }
      if (shadowView != null) {
        shadowView.setTranslationY(y);
      }
    }
  }

  private ValueAnimator bottomBarAnimator;
  private boolean isCounterVisible;

  public void animateBottomFactor (float toFactor) {
    if (bottomBarAnimating) {
      bottomBarAnimating = false;
      if (bottomBarAnimator != null) {
        bottomBarAnimator.cancel();
        bottomBarAnimator = null;
      }
    }
    if (bottomBarFactor == toFactor) {
      getCurrentController().onBottomBarAnimationComplete();
      return;
    }
    bottomBarAnimating = true;
    final float fromFactor = bottomBarFactor;
    final float factorDiff = toFactor - fromFactor;
    bottomBarAnimator = AnimatorUtils.simpleValueAnimator();
    bottomBarAnimator.addUpdateListener(animation -> {
      if (bottomBarAnimating) {
        float factor = AnimatorUtils.getFraction(animation);
        setBottomBarFactor(fromFactor + factorDiff * factor);
        getCurrentController().onBottomBarAnimationFraction(factor);
      }
    });
    bottomBarAnimator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        bottomBarAnimating = false;
        getCurrentController().onBottomBarAnimationComplete();
      }
    });
    bottomBarAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    bottomBarAnimator.setDuration(REVEAL_DURATION);
    bottomBarAnimator.start();
  }

  public void showBottomBar () {
    animateBottomFactor(1f);
  }

  public void hideBottomBar () {
    animateBottomFactor(0f);
  }

  private boolean hideCollapsed;

  public void hideBottomBarAndDismiss () {
    hideCollapsed = true;
    hide(false);
  }

  private int currentStartHeight;

  private void prepareRevealAnimation () {
    currentStartHeight = currentController.getStartHeight();
    currentController.getValue().setTranslationY(currentStartHeight);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      invalidateOutline();
    }
    setBottomBarFactor(0f);
  }

  private void setRevealFactor (float factor) {
    if (popupLayout != null) {
      popupLayout.setRevealFactor(factor);
    }
    setBottomBarFactor(factor);
    currentController.getValue().setTranslationY(currentStartHeight - (int) ((float) currentStartHeight * factor));
  }

  public void onContentHeightChanged () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      invalidateOutline();
    }
  }

  // Popup animation

  @Override
  public boolean shouldOverrideHideAnimation () {
    return true;
  }

  @Override
  public Animator createCustomHideAnimator () {
    Animator.AnimatorListener endListener = new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        getCurrentController().onBottomBarAnimationComplete();
        if (pendingPopup != null) {
          pendingPopup.onCustomHideAnimationComplete();
        }
      }
    };
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && hideCircular) {
      int cx = getMeasuredWidth() - (int) ((float) sendButton.getMeasuredWidth() * .5f);
      int cy = getMeasuredHeight() - (int) ((float) sendButton.getMeasuredHeight() * .5f);
      if (headerFactor != 0f) {
        setContentVisible(true);
      }
      float startRadius = (float) Math.hypot(getMeasuredWidth(), getMeasuredHeight());
      Animator animator = android.view.ViewAnimationUtils.createCircularReveal(this, cx, cy, startRadius, 0f);
      /*animator.setInterpolator(new Interpolator() {
        @Override
        public float getInterpolation (float input) {
          float interpolation = Anim.DECELERATE_INTERPOLATOR.getInterpolation(input);
          if (pendingPopup != null) {
            pendingPopup.setRevealFactor(Utils.floatRange(1f - interpolation));
          }
          return interpolation;
        }
      });*/
      animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
      animator.setDuration(REVEAL_HIDE_DURATION);
      animator.addListener(endListener);
      if (pendingPopup != null) {
        pendingPopup.animateRevealFactor(0f);
      }
      return animator;
    }

    ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
    animator.setDuration(REVEAL_DURATION);
    final float fromFactor = bottomBarFactor;
    final float factorDiff = -bottomBarFactor;

    final float fromCounterFactor = counterFactor;
    final float counterFactorDiff = -counterFactor;

    final float fromHeadFactor = headerFactor;
    final float headFactorDiff = -headerFactor;
    animator.addUpdateListener(animation -> {
      float factor = AnimatorUtils.getFraction(animation);
      bottomBarFactor = fromFactor + factorDiff * factor;
      if (fromCounterFactor != 0f) {
        __setCounterFactor(fromCounterFactor + counterFactorDiff * factor);
      }
      updateBarPosition();
      setHeaderFactor(fromHeadFactor + headFactorDiff * factor);
      getCurrentController().onBottomBarAnimationFraction(factor);
      pendingPopup.setRevealFactor(1f - factor);
    });
    animator.addListener(endListener);
    animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    getCurrentController().onBottomBarAnimationStart(true);
    return animator;
  }

  private boolean ignoreHeaderStyles;

  @Override
  public void modifyBaseHideAnimator (final ValueAnimator animator) {
    if (hideCollapsed || headerFactor != 0f) {
      animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
      animator.setDuration(REVEAL_DURATION);
    } else {
      animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
      animator.setDuration(REVEAL_HIDE_DURATION);
      if (headerFactor != 0f && hideCircular) {
        ignoreHeaderStyles = true;
        animator.addUpdateListener(animation -> setHeaderFactor(AnimatorUtils.getFraction(animation)));
      }
    }
  }

  @Override
  public boolean shouldOverrideShowAnimation () {
    return true;
  }

  @Override
  public Animator createCustomShowAnimator () {
    ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
    animator.setDuration(REVEAL_DURATION);
    animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    animator.addUpdateListener(animation -> setRevealFactor(AnimatorUtils.getFraction(animation)));
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        if (openingPopup != null) {
          openingPopup.onCustomShowComplete();
        }
        getCurrentController().onCompleteShow(true);
        /*if (Views.HARDWARE_LAYER_ENABLED) {
          NavigationController c = UI.getNavigation();
          if (c != null) {
            Views.setLayerType(c.getWrap(), origLayerType1);
          }
          Views.setLayerType(this, origLayerType2);
        }*/
      }
    });
    prepareRevealAnimation();
    return animator;
  }

  @Override
  public void modifyBaseShowAnimator (ValueAnimator animator) {
    animator.setDuration(REVEAL_DURATION);
    animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
  }

  private ViewController.CameraOpenOptions cameraOpenOptions;

  public void hidePopupAndOpenCamera (ViewController.CameraOpenOptions params) {
    this.cameraOpenOptions = params;
    forceHide();
  }

  // Popup

  @Override
  public void onPopupDismiss (PopupLayout popup) {
    if (cameraOpenOptions != null) {
      if (parent != null && (parent instanceof MessagesController || mode == MODE_AVATAR_PICKER) && !parent.isDestroyed()) {
        parent.openInAppCamera(cameraOpenOptions);
      }
    }
    performDestroy();
  }

  private boolean isDestroyed;

  @Override
  public void performDestroy () {
    setContentVisible(true);
    isDestroyed = true;
    onCurrentColorChanged();
    for (ViewController<?> controller : controllers) {
      if (controller != null) {
        removeView(controller.getValue());
        if (!controller.isDestroyed())
          controller.destroy();
      }
    }
    if (showKeyboardOnHide && target != null) {
      target.showKeyboard();
    }
    if (sendButton != null) {
      sendButton.destroySlowModeCounterController();
    }
    ThemeManager.instance().removeThemeListener(this);
    Lang.removeLanguageListener(this);
    if (target != null) {
      target.setBroadcastAction(TdApi.ChatActionCancel.CONSTRUCTOR);
    }
  }

  // Callbacks

  private boolean showKeyboardOnHide;

  public void chooseInlineBot (TGUser user) {
    if (user.getUser() != null && target != null) {
      target.onUsernamePick(Td.primaryUsername(user.getUser()));
    }
    showKeyboardOnHide = true;
    hide(false);
  }

  public void chooseInlineBot (String username) {
    if (target != null) {
      target.onUsernamePick(username);
    }
    showKeyboardOnHide = true;
    hide(false);
  }

  public void pickDateOrProceed (TdlibUi.SimpleSendCallback sendCallback) {
    if (target != null && target.areScheduledOnly()) {
      tdlib().ui().showScheduleOptions(target, getTargetChatId(), false, sendCallback, null, null);
    } else {
      if (showSlowModeRestriction(sendButton)) {
        return;
      }
      sendCallback.onSendRequested(Td.newSendOptions(), false);
    }
  }

  public void sendContact (TGUser user) {
    pickDateOrProceed((sendOptions, disableMarkdown) -> {
      if (target != null) {
        target.sendContact(user.getUser(), true, sendOptions);
      }
      hide(false);
    });
  }

  public void sendContacts (LongSparseArray<TGUser> users, TdApi.MessageSendOptions options) {
    if (users == null || users.size() == 0)
      return;
    if (target != null) {
      int size = users.size();
      if (size > 0) {
        for (int i = 0; i < size; i++) {
          TGUser user = users.valueAt(i);
          target.sendContact(user.getUser(), i == 0, options);
        }
      }
    }
    hide(true);
  }

  public void sendFilesMixed (View view, List<String> files, ArrayList<MediaBottomFilesController.MusicEntry> musicFiles, TdApi.MessageSendOptions options, boolean isMultiSend) {
    if ((files == null || files.isEmpty()) && (musicFiles == null || musicFiles.isEmpty()))
      return;
    if (files != null && !files.isEmpty()) {
      if (target != null && target.showRestriction(view, RightId.SEND_DOCS)) {
        return;
      }
    }
    if (musicFiles != null && !musicFiles.isEmpty()) {
      if (target != null && target.showRestriction(view, RightId.SEND_AUDIO)) {
        return;
      }
    }
    boolean needGroupMedia;
    if (isMultiSend) {
      needGroupMedia = this.needGroupMedia;
      Settings.instance().setNeedGroupMedia(needGroupMedia);
    } else {
      needGroupMedia = !Settings.instance().rememberAlbumSetting() || Settings.instance().needGroupMedia();
    }
    if (target != null) {
      if (files != null) {
        target.sendFiles(view, files, needGroupMedia, true, options);
      }
      if (musicFiles != null) {
        target.sendMusic(view, musicFiles, needGroupMedia, true, options);
      }
    }
    hide(isMultiSend);
  }

  public void sendFile (View v, String file) {
    if (target != null && target.showRestriction(v, RightId.SEND_DOCS)) {
      return;
    }
    pickDateOrProceed((sendOptions, disableMarkdown) -> {
      if (target != null) {
        if (target.showRestriction(v, RightId.SEND_DOCS)) {
          return;
        }
        target.sendFiles(v, Collections.singletonList(file), needGroupMedia, true, sendOptions);
      }
      hide(false);
    });
  }

  public void sendMusic (View view, MediaBottomFilesController.MusicEntry musicFile) {
    if (target != null && target.showRestriction(view, RightId.SEND_AUDIO)) {
      return;
    }
    pickDateOrProceed((sendOptions, disableMarkdown) -> {
      if (target != null) {
        target.sendMusic(view, Collections.singletonList(musicFile), needGroupMedia, true, sendOptions);
      }
      hide(false);
    });
  }

  public void sendImage (ImageFile image, boolean isRemote) {
    pickDateOrProceed((sendOptions, disableMarkdown) -> {
      if (isRemote) {
        long queryId;
        String id;
        if (image instanceof MediaImageFile) {
          queryId = ((MediaImageFile) image).getQueryId();
          id = ((MediaImageFile) image).getResultId();
        } else {
          throw new IllegalArgumentException("image.getType() == " + image.getType());
        }
        if (target != null) {
          target.sendInlineQueryResult(queryId, id, true, false, sendOptions);
        }
      } else {
      /*if (target != null) {
        target.onSendPaths(new String[]{image.getFilePath()}, false);
      }*/
      }
      hide(false);
    });
  }

  public boolean sendPhotosOrVideos (View view, ArrayList<ImageFile> images, boolean areRemote, TdApi.MessageSendOptions options, boolean disableMarkdown, boolean asFiles, boolean disableAnimation) {
    if (mode == MODE_AVATAR_PICKER) {
      parent.context().forceCloseCamera();
    }

    if (images == null || images.isEmpty()) {
      return false;
    }
    // ArrayList<String> results = new ArrayList<>(images.size());
    if (areRemote) {
      if (target != null) {
        CharSequence text = target.tdlib().getInlineRestrictionText(target.getChat());
        if (target.showRestriction(view, text)) {
          return false;
        }
      }
      boolean first = true;
      for (ImageFile rawFile : images) {
        String resultId;
        long queryId;
        if (rawFile instanceof MediaImageFile) {
          MediaImageFile imageFile = (MediaImageFile) rawFile;
          queryId = imageFile.getQueryId();
          resultId = imageFile.getResultId();
        } else {
          continue;
        }
        if (target != null) {
          target.sendInlineQueryResult(queryId, resultId, first, false, options);
        }
        first = false;
      }
    } else {
      ArrayList<ImageGalleryFile> galleryFiles = new ArrayList<>(images.size());

      boolean first = true;

      if (target != null) {
        boolean havePhotos = false;
        boolean haveVideos = false;
        for (ImageFile rawFile : images) {
          if (!(rawFile instanceof ImageGalleryFile)) {
            throw new IllegalArgumentException("rawFile instanceof " + rawFile.getClass().getName());
          }
          ImageGalleryFile galleryFile = (ImageGalleryFile) rawFile;
          if (galleryFile.isVideo()) {
            haveVideos = true;
          } else {
            havePhotos = true;
          }
          if (havePhotos && haveVideos) {
            break;
          }
        }
        if (target.showPhotoVideoRestriction(view, havePhotos, haveVideos)) {
          return false;
        }
      }
      for (ImageFile rawFile : images) {
        ImageGalleryFile galleryFile = (ImageGalleryFile) rawFile;
        if (galleryFile.getFilePath() != null) {
          galleryFiles.add(galleryFile);
        }
        if (callback != null && callback instanceof MediaGalleryCallback) {
          if (galleryFile.isVideo()) {
            ((MediaGalleryCallback) callback).onSendVideo(galleryFile, first);
          } else {
            ((MediaGalleryCallback) callback).onSendPhoto(galleryFile, first);
          }
        }
        first = false;
      }

      if (target != null) {
        ImageGalleryFile[] result = new ImageGalleryFile[galleryFiles.size()];
        galleryFiles.toArray(result);
        Settings.instance().setNeedGroupMedia(needGroupMedia);
        boolean showCaptionAboveMedia = false;
        boolean hasSpoiler = allowSpoiler && needSpoiler;
        target.sendPhotosAndVideosCompressed(result, needGroupMedia, options, disableMarkdown, asFiles, showCaptionAboveMedia, hasSpoiler);
      }
    }

    if (disableAnimation) {
      forceHide();
    } else {
      hide(true);
    }
    return true;
  }

  public void sendVenue (MediaLocationData place) {
    pickDateOrProceed((sendOptions, disableMarkdown) -> {
      if (target != null) {
        target.send(place.convertToInputMessage(), true, sendOptions, null);
      }
      // TODO reveal hide animation
      hide(false);
    });
  }

  public void sendLocation (double latitude, double longitude, double accuracy, int heading, int livePeriod) {
    pickDateOrProceed((sendOptions, disableMarkdown) -> {
      if (target != null) {
        TdApi.Location location = new TdApi.Location(latitude, longitude, accuracy);
        if (inSpecificMode()) {
          target.sendPickedLocation(location, heading, sendOptions);
        } else {
          target.send(new TdApi.InputMessageLocation(location, livePeriod, heading, 0), true, sendOptions, null);
        }
      }
      hide(false);
      // TODO reveal hide animation
    });
  }

  public void openCamera () {
    hide(false);
    UI.openCameraDelayed(UI.getContext(getContext()));
  }

  public void openGallery (boolean sendAsFile) {
    hide(false);
    UI.openGalleryDelayed(UI.getContext(getContext()), sendAsFile);
  }

  private TooltipOverlayView.TooltipInfo tooltipInfo;

  @Override
  public void onClick (View v) {
    int viewId = v.getId();
    if (viewId == R.id.btn_send) {
      pickDateOrProceed((sendOptions, disableMarkdown) ->
        getCurrentController().onMultiSendPress(v, sendOptions, false)
      );
    } else if (viewId == R.id.btn_spoiler) {
      if (allowSpoiler) {
        setNeedSpoiler(!needSpoiler);
        if (needSpoiler) {
          tooltipInfo = UI.getContext(getContext()).tooltipManager().builder(v)
            .icon(R.drawable.baseline_whatshot_24)
            .offset(rect -> rect.offset(0, Screen.dp(12f))).show(tdlib(), R.string.MediaSpoilerHint).hideDelayed();
        } else {
          if (tooltipInfo != null) {
            tooltipInfo.hideNow();
            tooltipInfo = null;
          }
        }
      }
    } else if (viewId == R.id.btn_mosaic) {
      setNeedGroupMedia(!needGroupMedia, true);
    } else if (viewId == R.id.btn_close) {
      if (!getCurrentController().showExitWarning(true)) {
        cancelMultiSelection();
      }
    }
  }

  public void cancelMultiSelection () {
    animateCounterFactor(0f);
    getCurrentController().onCancelMultiSelection();
  }

  // Counter

  private CounterHeaderView counterView;
  private SendButton sendButton;
  private HapticMenuHelper sendMenu;
  private BackHeaderButton closeButton;
  private TextView counterHintView;
  private ImageView groupMediaView, hotMediaView;

  private float groupMediaFactor;
  private boolean needGroupMedia, needSpoiler;
  private FactorAnimator groupMediaAnimator;
  private static final int ANIMATOR_GROUP_MEDIA = 1;

  private void setGroupMediaFactor (float factor) {
    if (this.groupMediaFactor != factor) {
      this.groupMediaFactor = factor;
      groupMediaView.setColorFilter(ColorUtils.fromToArgb(Theme.getColor(ColorId.icon), Theme.getColor(ColorId.iconActive), factor));
    }
  }

  public final void setNeedGroupMedia (boolean needGroupMedia, boolean animated) {
    if (this.needGroupMedia != needGroupMedia) {
      this.needGroupMedia = needGroupMedia;
      Settings.instance().setNeedGroupMedia(needGroupMedia);
      themeListeners.removeThemeListenerByTarget(groupMediaView);
      themeListeners.addThemeFilterListener(groupMediaView, needGroupMedia ? ColorId.iconActive : ColorId.icon);
      checkCounterHintText();
      if (animated) {
        if (groupMediaAnimator == null) {
          groupMediaAnimator = new FactorAnimator(ANIMATOR_GROUP_MEDIA, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.groupMediaFactor);
        }
        groupMediaAnimator.animateTo(needGroupMedia ? 1f : 0f);
      } else {
        if (groupMediaAnimator != null) {
          groupMediaAnimator.forceFactor(0f);
        }
        setGroupMediaFactor(needGroupMedia ? 1f : 0f);
      }
    }
  }

  private void prepareCounter () {
    if (counterView == null && bottomBar != null) {
      FrameLayoutFix.LayoutParams params;
      params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(53f), Gravity.LEFT | Gravity.TOP);
      params.leftMargin = Screen.dp(56f + 18f);
      params.rightMargin = Screen.dp(56f);
      counterView = new CounterHeaderView(getContext());
      counterView.setFactorChangeListener(v -> checkCounterHint());
      counterView.initDefault(ColorId.text);
      themeListeners.addThemeInvalidateListener(counterView);
      counterView.setSuffix(Lang.plural(R.string.SelectedSuffix, 1), false);
      counterView.setLayoutParams(params);
      bottomBar.addView(counterView);

      needGroupMedia = !Settings.instance().rememberAlbumSetting() || Settings.instance().needGroupMedia();

      params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(53f), Gravity.LEFT | Gravity.TOP);
      params.leftMargin = Screen.dp(56f + 18f);
      params.rightMargin = Screen.dp(56f);
      params.topMargin = Screen.dp(28f);
      counterHintView = new NoScrollTextView(getContext());
      counterHintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
      counterHintView.setTextColor(Theme.textDecentColor());
      themeListeners.addThemeTextDecentColorListener(counterView);
      counterHintView.setTypeface(Fonts.getRobotoRegular());
      counterHintView.setEllipsize(TextUtils.TruncateAt.END);
      counterHintView.setSingleLine(true);
      counterHintView.setLayoutParams(params);
      counterHintView.setText(Lang.getString(needGroupMedia ? R.string.AsOneMessage : R.string.AsSeparateMessages));
      groupMediaFactor = needGroupMedia ? 1f : 0f;
      bottomBar.addView(counterHintView);

      sendButton = new SendButton(getContext(), R.drawable.deproko_baseline_send_24) {
        @Override
        public boolean onTouchEvent (MotionEvent e) {
          return isEnabled() && Views.isValid(this) && super.onTouchEvent(e);
        }
      };
      sendButton.setId(R.id.btn_send);
      sendButton.getSlowModeCounterController(tdlib()).setCurrentChat(getTargetChatId());
      themeListeners.addThemeFilterListener(sendButton, ColorId.chatSendButton);
      sendButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(55f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT));
      Views.setClickable(sendButton);
      sendButton.setOnClickListener(this);
      bottomBar.addView(sendButton);

      sendMenu = new HapticMenuHelper(list -> {
        final TdApi.Chat chat = getTargetChat();
        List<HapticMenuHelper.MenuItem> items = tdlib().ui().fillDefaultHapticMenu(getTargetChatId(), false, getCurrentController().canRemoveMarkdown(), true);
        if (items == null)
          items = new ArrayList<>();
        getCurrentController().addCustomItems(sendButton, items);
        if (chat != null && chat.messageSenderId != null) {
          items.add(0, MediaLayout.createHapticSenderItem(tdlib(), chat));
        }
        return !items.isEmpty() ? items : null;
      }, (menuItem, parentView, item) -> {
        final int menuItemId = menuItem.getId();
        if (menuItemId == R.id.btn_openSendersMenu) {
          openSetSenderPopup();
        } else if (menuItemId == R.id.btn_sendNoMarkdown) {
          pickDateOrProceed((sendOptions, disableMarkdown) ->
            getCurrentController().onMultiSendPress(sendButton, sendOptions, true)
          );
        } else if (menuItemId == R.id.btn_sendNoSound) {
          pickDateOrProceed((sendOptions, disableMarkdown) ->
            getCurrentController().onMultiSendPress(sendButton, sendOptions, false)
          );
        } else if (menuItemId == R.id.btn_sendOnceOnline) {
          getCurrentController().onMultiSendPress(sendButton, Td.newSendOptions(new TdApi.MessageSchedulingStateSendWhenOnline()), false);
        } else if (menuItemId == R.id.btn_sendScheduled) {
          if (target != null) {
            tdlib().ui().pickSchedulingState(target,
              schedule ->
                getCurrentController().onMultiSendPress(sendButton, Td.newSendOptions(schedule), false),
              getTargetChatId(), false, false, null, null
            );
          }
        }
        return true;
      }, themeListeners, null).attachToView(sendButton);

      params = FrameLayoutFix.newParams(Screen.dp(55f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT);
      params.rightMargin = Screen.dp(55f);
      hotMediaView = new ImageView(getContext()) {
        @Override
        public boolean onTouchEvent (MotionEvent e) {
          return isEnabled() && Views.isValid(this) && super.onTouchEvent(e);
        }
      };
      hotMediaView.setOnClickListener(this);
      hotMediaView.setId(R.id.btn_spoiler);
      hotMediaView.setScaleType(ImageView.ScaleType.CENTER);
      hotMediaView.setImageResource(R.drawable.baseline_whatshot_24);
      hotMediaView.setAlpha(allowSpoiler ? 1f : 0f);
      hotMediaView.setColorFilter(Theme.getColor(needSpoiler ? ColorId.iconActive : ColorId.icon));
      themeListeners.addThemeFilterListener(hotMediaView, needSpoiler ? ColorId.iconActive : ColorId.icon);
      hotMediaView.setLayoutParams(params);
      bottomBar.addView(hotMediaView);

      params = FrameLayoutFix.newParams(Screen.dp(55f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT);
      params.rightMargin = allowSpoiler ? Screen.dp(55f) + Screen.dp(48f) : Screen.dp(55f);
      groupMediaView = new ImageView(getContext()) {
        @Override
        public boolean onTouchEvent (MotionEvent e) {
          return isEnabled() && Views.isValid(this) && super.onTouchEvent(e);
        }
      };
      groupMediaView.setOnClickListener(this);
      groupMediaView.setId(R.id.btn_mosaic);
      groupMediaView.setScaleType(ImageView.ScaleType.CENTER);
      groupMediaView.setImageResource(R.drawable.deproko_baseline_mosaic_group_24);
      int colorId = needGroupMedia ? ColorId.iconActive : ColorId.icon;
      groupMediaView.setColorFilter(Theme.getColor(colorId));
      themeListeners.addThemeFilterListener(groupMediaView, colorId);
      groupMediaView.setLayoutParams(params);
      bottomBar.addView(groupMediaView);

      closeButton = new BackHeaderButton(getContext()) {
        @Override
        public boolean onTouchEvent (MotionEvent e) {
          return (e.getAction() != MotionEvent.ACTION_DOWN || isEnabled()) && super.onTouchEvent(e);
        }
      };
      closeButton.setId(R.id.btn_close);
      RippleSupport.setTransparentSelector(closeButton);
      closeButton.setButtonFactor(BackHeaderButton.TYPE_CLOSE);
      closeButton.setOnClickListener(this);
      closeButton.setColor(Theme.iconColor());
      themeListeners.addThemeColorListener(closeButton, ColorId.icon);
      closeButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT));
      bottomBar.addView(closeButton);

      counterView.setAlpha(0f);
      sendButton.setAlpha(0f);
      closeButton.setAlpha(0f);
      counterHintView.setAlpha(0f);
      groupMediaView.setAlpha(0f);
      hotMediaView.setAlpha(0f);

      setCounterEnabled(false);
    }
    // No need to reset, right?
    // setNeedSpoiler(false);
    setAllowSpoiler(getCurrentController().allowSpoiler());
    hotMediaView.setAlpha(counterFactor * (allowSpoiler ? 1f : 0f));
    if (counterView != null) {
      counterView.setTranslationY(0f);
      checkSuffix(false);
      // setNeedGroupMedia(TGSettingsManager.instance().needGroupMedia(), false);
    }
  }

  private boolean allowSpoiler;

  public boolean allowSpoiler () {
    return allowSpoiler;
  }

  public boolean needSpoiler () {
    return needSpoiler;
  }

  private void setAllowSpoiler (boolean allowSpoiler) {
    if (this.allowSpoiler != allowSpoiler) {
      this.allowSpoiler = allowSpoiler;
      if (allowSpoiler) {
        setNeedSpoiler(false);
      }
      if (hotMediaView != null) {
        hotMediaView.setAlpha(allowSpoiler ? counterFactor : 0f);
      }
      if (groupMediaView != null) {
        Views.setRightMargin(groupMediaView, allowSpoiler ? Screen.dp(55f) + Screen.dp(48f) : Screen.dp(55f));
      }
    }
  }

  public void setNeedSpoiler (boolean needSpoiler) {
    if (this.needSpoiler != needSpoiler) {
      this.needSpoiler = needSpoiler;
      if (hotMediaView != null) {
        hotMediaView.setColorFilter(Theme.getColor(needSpoiler ? ColorId.iconActive : ColorId.icon));
        themeListeners.removeThemeListenerByTarget(hotMediaView);
        themeListeners.addThemeFilterListener(hotMediaView, needSpoiler ? ColorId.iconActive : ColorId.icon);
      }
    }
  }

  public boolean showCaptionAboveMedia () {
    return false; // TODO
  }

  public void setShowCaptionAboveMedia (boolean showCaptionAboveMedia) {
    // TODO
  }

  private @Nullable TdApi.Chat getTargetChat () {
    MessagesController c = getTarget();
    TdApi.Chat chat = null;
    if (c != null) {
      chat = c.getChat();
    }
    return chat;
  }

  private void checkSuffix (boolean animate) {
    MediaBottomBaseController<?> c = getCurrentController();
    if (c instanceof MediaBottomGalleryController) {
      MediaBottomGalleryController gallery = (MediaBottomGalleryController) c;
      int photosCount = 0, videosCount = 0, otherCount = 0;
      ArrayList<ImageFile> imageFiles = gallery.getSelectedMediaItems(false);
      if (imageFiles != null && !imageFiles.isEmpty()) {
        for (ImageFile imageFile : imageFiles) {
          if (imageFile.isWebp() || !(imageFile instanceof ImageGalleryFile)) {
            otherCount++;
            break;
          }
          ImageGalleryFile galleryFile = (ImageGalleryFile) imageFile;
          if (galleryFile.isVideo()) {
            videosCount++;
            if (photosCount > 0) {
              break;
            }
          } else {
            photosCount++;
            if (videosCount > 0) {
              break;
            }
          }
        }
        if (otherCount > 0 || (photosCount > 0 && videosCount > 0)) {
          int count = (otherCount + photosCount + videosCount);
          counterView.setSuffix(Lang.plural(R.string.AttachMediasSuffix, count), animate);
        } else if (videosCount > 0) {
          counterView.setSuffix(Lang.plural(R.string.AttachVideosSuffix, videosCount), animate);
        } else {
          counterView.setSuffix(Lang.plural(R.string.AttachPhotosSuffix, photosCount), animate);
        }
        return;
      }
    }
    counterView.setSuffix(Lang.plural(R.string.SelectedSuffix, Math.max(1, counterView.getCounter())), animate);
  }

  private void setCounterEnabled (boolean enabled) {
    if (counterView != null && counterView.isEnabled() != enabled) {
      counterView.setEnabled(enabled);
      groupMediaView.setEnabled(enabled);
      sendButton.setEnabled(enabled);
      closeButton.setEnabled(enabled);
    }
  }

  private float counterFactor;
  private FactorAnimator counterAnimator;
  private boolean counterAnimateOnlyPosition;


  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_COUNTER: {
        setCounterFactor(factor);
        break;
      }
      case ANIMATOR_GROUP_MEDIA: {
        setGroupMediaFactor(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_COUNTER: {
        if (finalFactor == 0f && counterAnimateOnlyPosition) {
          setCounterFactorInternal(0f);
        }
        break;
      }
    }
  }

  private void checkCounterHintText () {
    if (counterView == null || counterHintView == null) {
      return;
    }
    if (needGroupMedia) {
      int count = (int) Math.ceil((float) counterView.getCounter() / (float) TdConstants.MAX_MESSAGE_GROUP_SIZE);
      if (count > 1) {
        counterHintView.setText(Lang.plural(R.string.AsXMessages, count));
      } else {
        counterHintView.setText(Lang.getString(R.string.AsOneMessage));
      }
    } else {
      counterHintView.setText(Lang.getString(R.string.AsSeparateMessages));
    }
  }

  private void checkCounterHint () {
    if (counterView != null) {
      float translateFactor = counterView.getMultipleFactor();
      if (translateFactor > 0f && (counterFactor == 0f || !getCurrentController().supportsMediaGrouping())) {
        translateFactor = 0f;
      }
      hotMediaView.setAlpha(counterFactor * (allowSpoiler ? 1f : 0f));
      float alpha = counterFactor * translateFactor;
      groupMediaView.setAlpha(alpha);
      counterHintView.setAlpha(alpha);
      counterView.setTranslationY(-Screen.dp(9f) * translateFactor);
    }
  }

  private void setCounterFactorInternal (float factor) {
    if (bottomBar != null) {
      bottomBar.setOverlayFactor(factor);
    }
    if (counterView != null) {
      counterView.setAlpha(factor);
      sendButton.setAlpha(factor);
      closeButton.setAlpha(factor);
      checkCounterHint();
    }
    setCounterEnabled(factor != 0f);
  }

  public float getCounterFactor () {
    return counterFactor;
  }

  private void __setCounterFactor (float factor) {
    if (this.counterFactor != factor) {
      this.counterFactor = factor;
      hotMediaView.setAlpha(factor * (allowSpoiler ? 1f : 0f));
      setAddExtraSpacing(factor == 1f);
    }
  }

  private boolean addExtraSpacing;

  private void setAddExtraSpacing (boolean add) {
    if (this.addExtraSpacing != add) {
      this.addExtraSpacing = add;
      if (currentController != null) {
        currentController.updateExtraSpacingDecoration();
      }
    }
  }

  private void setCounterFactor (float factor) {
    if (counterFactor != factor) {
      __setCounterFactor(factor);
      if (!counterAnimateOnlyPosition || bottomBarFactor != 0f) {
        setCounterFactorInternal(factor);
      }
      updateBarPosition();
    }
  }

  private static final int ANIMATOR_COUNTER = 0;

  private void animateCounterFactor (final float toFactor) {
    isCounterVisible = toFactor == 1f;
    onCurrentColorChanged();
    if (counterAnimator == null) {
      counterAnimator = new FactorAnimator(ANIMATOR_COUNTER, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 200l, this.counterFactor);
    } else {
      counterAnimator.cancel();
    }
    counterAnimateOnlyPosition = bottomBarFactor == 0f;
    if (bottomBar != null) {
      bottomBar.prepareOverlayAnimation();
    }
    if (counterAnimateOnlyPosition && toFactor == 1f) {
      setCounterFactorInternal(1f);
    }
    counterAnimator.animateTo(toFactor);
  }

  public void setCounter (int count) {
    if (inSingleMediaMode()) {
      return;
    }

    boolean init = counterFactor == 0f && count == 1;
    if (init) {
      prepareCounter();
    }
    animateCounterFactor(count > 0 ? 1f : 0f);
    if (counterView != null) {
      if (init) {
        counterView.initCounter(count, false);
        checkSuffix(false);
      } else if (count != 0) {
        counterView.setCounter(count);
      }
    }
    if (!init && count > 0) {
      checkSuffix(true);
      checkCounterHintText();
    }
  }

  public void clearCounter () {
    animateCounterFactor(0f);
    getCurrentController().onCancelMultiSelection();
  }

  // HeaderView

  private float headerFactor;
  private @Nullable HeaderView headerView;
  private int lastHeaderIndex = -1;

  public void prepareHeader () {
    int index = bottomBar != null ? bottomBar.getCurrentIndex() : 0;
    MediaBottomBaseController<?> c = getCurrentController();

    if (headerView == null) {
      headerView = new HeaderView(getContext()) {
        @Override
        public boolean onTouchEvent (MotionEvent e) {
          super.onTouchEvent(e);
          return true;
        }
      };
      headerView.initWithSingleController(getCurrentController(), true);
      headerView.setAlpha(0f);
      headerView.setTranslationY(-HeaderView.getSize(false) - headerView.getFilling().getExtraHeight());
      addView(headerView);
      lastHeaderIndex = index;
    }

    if (lastHeaderIndex != index) {
      headerView.setTitle(getCurrentController());
      lastHeaderIndex = index;
    }
  }

  public void setHeaderFactor (float factor) {
    if (this.headerFactor != factor) {
      this.headerFactor = factor;
      if (headerView != null && !ignoreHeaderStyles) {
        headerView.setAlpha(factor);
        int offsetY = HeaderView.getSize(false) + headerView.getFilling().getExtraHeight();
        headerView.setTranslationY(-offsetY + (int) ((float) offsetY * factor));
      }
    }
  }

  private boolean visible = true;

  public void setContentVisible (boolean visible) {
    if (this.visible != visible) {
      this.visible = visible;
      NavigationController c = UI.getNavigation();
      if (c != null) {
        c.getValue().setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
      }
    }
  }

  public @Nullable MessagesController parentMessageController () {
    return (parent instanceof MessagesController) ? (MessagesController) parent : null;
  }

  public boolean needCameraButton () {
    return mode == MODE_AVATAR_PICKER || (parent instanceof MessagesController) && !((MessagesController) parent).isCameraButtonVisibleOnAttachPanel();
  }

  public int getCameraButtonOffset () {
    return Screen.dp(60);
  }

  public static HapticMenuHelper.MenuItem createHapticSenderItem (Tdlib tdlib, @NonNull TdApi.Chat chat) {
    final long senderId = Td.getSenderId(chat.messageSenderId);

    if (senderId == chat.id) {
      return new HapticMenuHelper.MenuItem(R.id.btn_openSendersMenu, Lang.getString(R.string.SendAs), tdlib.getMessageSenderTitle(chat.messageSenderId), R.drawable.dot_baseline_acc_anon_24);
    } else if (senderId == tdlib.myUserId()) {
      return new HapticMenuHelper.MenuItem(R.id.btn_openSendersMenu, Lang.getString(R.string.SendAs), tdlib.getMessageSenderTitle(chat.messageSenderId), R.drawable.dot_baseline_acc_personal_24);
    } else {
      return new HapticMenuHelper.MenuItem(R.id.btn_openSendersMenu, Lang.getString(R.string.SendAs), tdlib.getMessageSenderTitle(chat.messageSenderId), 0, tdlib, chat.messageSenderId, false);
    }
  }

  private void openSetSenderPopup () {
    TdApi.Chat chat = getTargetChat();
    if (chat == null) return;

    tdlib().send(new TdApi.GetChatAvailableMessageSenders(getTargetChatId()), (result, error) -> {
      UI.post(() -> {
        if (result != null) {
          final SetSenderController c = new SetSenderController(getContext(), tdlib());
          c.setArguments(new SetSenderController.Args(chat, result.senders, chat.messageSenderId));
          c.setDelegate(this::setNewMessageSender);
          c.show();
        }
      });
    });
  }

  private void setNewMessageSender (TdApi.ChatMessageSender sender) {
    tdlib().send(new TdApi.SetChatMessageSender(getTargetChatId(), sender.sender), tdlib().typedOkHandler());
  }

  public boolean showSlowModeRestriction (View v) {
    CharSequence restriction = tdlib().getSlowModeRestrictionText(getTargetChatId());
    if (restriction != null) {
      parent.context().tooltipManager()
        .builder(v)
        .controller(parent)
        .show(parent.tdlib(), restriction).hideDelayed();
      return true;
    }

    return false;
  }


  /* * */

  private MediaBottomFilesController.Delegate filesControllerDelegate;

  public void setFilesControllerDelegate (MediaBottomFilesController.Delegate filesControllerDelegate) {
    this.filesControllerDelegate = filesControllerDelegate;
  }

  @NonNull
  public MediaBottomFilesController.Delegate getFilesControllerDelegate () {
    if (filesControllerDelegate == null) {
      filesControllerDelegate = new MediaBottomFilesController.Delegate() {
        @Override
        public boolean showRestriction (View view, int rightId) {
          return (target != null && target.showRestriction(view, rightId));
        }

        @Override
        public void onFilesSelected (ArrayList<InlineResult<?>> results, boolean needShowKeyboard) {
          if (target != null && target.isFocused()) {
            target.setFilesToAttach(results, needShowKeyboard);
            hide(false);
          }
        }
      };
    }

    return filesControllerDelegate;
  }
}
