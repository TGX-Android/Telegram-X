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
 * File created on 25/02/2016 at 16:00
 */
package org.thunderdog.challegram.component.preview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.EmbeddedService;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.PopupLayout;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;

public class YouTubePreviewLayout extends PreviewLayout implements YouTubePlayerControls.Callback, FlingDetector.Callback {
  private static final float CLOSE_THRESHOLD = .5f;

  private FlingDetector gestureController;
  private int safetyOffset;

  private String videoId;
  private YouTubePlayerLayout player;
  private YouTubePlayerControls controls;
  private boolean previewSet;
  private boolean minimized;
  private float playerWidth, playerX;

  public static boolean ALLOW_FULLSCREEN = false;

  public YouTubePreviewLayout (Context context, ViewController<?> parent) {
    super(context, parent);

    if (ALLOW_FULLSCREEN) {
      UI.setFullscreenIfNeeded(this);
    }

    gestureController = new FlingDetector(context, this);
    safetyOffset = Screen.dp(20f);
  }

  @Override
  protected boolean buildLayout () {
    if (nativeEmbed.width == 0 || nativeEmbed.height == 0) {
      return false;
    }

    computeHeight(Screen.currentWidth());

    return true;
  }

  @Override
  protected int computeHeight (int currentWidth) {
    if (videoId == null || currentWidth == 0 || nativeEmbed.width == 0 || nativeEmbed.height == 0) {
      return footerHeight;
    }

    int playerWidth, playerHeight;
    final NavigationController navigation = UI.getNavigation();
    int navigationHeight = navigation != null ? navigation.getValue().getMeasuredHeight() : 0;
    int fullHeight = navigationHeight == 0 || inFullscreen ? Screen.currentActualHeight() : navigationHeight;

    if (minimized) {
      int minWidth = Screen.dp(YouTube.MIN_WIDTH_DP);
      int minHeight = Screen.dp(YouTube.MIN_HEIGHT_DP);

      float ratio = Math.max((float) minWidth / (float) nativeEmbed.width, (float) minHeight / (float) nativeEmbed.height);
      playerWidth = (int) ((float) nativeEmbed.width * ratio);
      playerHeight = (int) ((float) nativeEmbed.height * ratio);

      int paddingTop = HeaderView.getTopOffset() + Screen.dp(64f);

      if (paddingTop + playerHeight >= fullHeight) {
        paddingTop = (int) ((float) fullHeight * .5f - (float) playerHeight * .5f);
      }

      int targetY = playerHeight + footerHeight - fullHeight + paddingTop;

      if (controls != null) {
        controls.setNeedClip(true);
      }

      if (player != null) {
        player.setTranslationX((playerX = currentWidth - playerWidth - Screen.dp(8f)) + playerWidth * closeFactor);
        player.setTranslationY(targetY);
      }
    } else {
      playerWidth = currentWidth;
      playerHeight = (int) ((float) nativeEmbed.height * ((float) currentWidth / (float) nativeEmbed.width));

      if (inFullscreen || playerHeight + footerHeight >= fullHeight) {
        if (!inFullscreen && mayHintAboutFullscreen) {
          mayHintAboutFullscreen = false;
          UI.showToast(R.string.YoutubeRotateHint, Toast.LENGTH_SHORT);
          Settings.instance().markTutorialAsComplete(Settings.TUTORIAL_YOUTUBE_ROTATION);
        }
        playerHeight = fullHeight;
      } else {
        fullHeight = playerHeight + footerHeight;
      }

      if (player != null) {
        player.setTranslationX((playerX = 0f) + playerWidth * closeFactor);
        player.setTranslationY(0f);
      }

      if (controls != null) {
        controls.setNeedClip(false);
      }
    }

    mayHintAboutFullscreen = false;

    if (player != null) {
      player.setCurrentSize(playerWidth, playerHeight);
      ((LayoutParams) player.getLayoutParams()).bottomMargin = playerHeight == fullHeight ? 0 : footerHeight;
      if (!previewSet && currentWidth > 0 && playerHeight > 0) {
        previewSet = true;
        int size = Math.min(currentWidth, playerHeight);
        TdApi.PhotoSize photoSize = TD.findClosest(nativeEmbed.thumbnail, size, size);
        if (photoSize != null) {
          ImageFile file;
          file = new ImageFile(parent.tdlib(), photoSize.photo);
          file.setScaleType(ImageFile.FIT_CENTER);
          file.setSize(size);
          player.setPreview(file);
        }
      }
    }

    if (controls != null) {
      controls.setCurrentHeight(playerWidth, playerHeight, safetyOffset);
      setClipChildren(false);
      ((FrameLayoutFix.LayoutParams) controls.getLayoutParams()).topMargin = playerHeight + safetyOffset;
    }

    this.playerWidth = playerWidth;

    return Screen.currentActualHeight();
  }

  protected int getPreviewHeight () {
    return Screen.currentActualHeight();
  }

  @Override
  public boolean setPreview (EmbeddedService nativeEmbed) {
    videoId = YouTube.parseVideoId(nativeEmbed.viewUrl);
    if (!StringUtils.isEmpty(videoId)) {
      TdlibManager.instance().player().setPauseReason(TGPlayerController.PAUSE_REASON_OPEN_YOUTUBE, true);

      controls = new YouTubePlayerControls(getContext());
      controls.setCanMinimize(parent.tdlib().youtubePipEnabled());
      controls.setCallback(this);
      controls.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

      FrameLayoutFix.LayoutParams params;

      params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
      params.bottomMargin = footerHeight;

      player = new YouTubePlayerLayout(getContext());
      if (ALLOW_FULLSCREEN) {
        UI.setFullscreenIfNeeded(player);
      }
      player.setControls(controls);
      player.setParentLayout(this);
      player.setLayoutParams(params);
      addView(player);
      player.addView(controls);
    }
    return super.setPreview(nativeEmbed);
  }

  @Override
  public void onPopupCompletelyShown (PopupLayout popup) {
    if (videoId != null) {
      TdlibManager.instance().player().setPauseReason(TGPlayerController.PAUSE_REASON_OPEN_YOUTUBE, true);
      player.loadVideo(videoId);
    }
  }

  @Override
  protected boolean onPrepareNextPreview (String pageUrl) {
    String videoId = YouTube.parseVideoId(pageUrl);

    if (videoId != null && videoId.equals(this.videoId)) {
      onRequestExpand();
      return true;
    }

    return false;
  }

  private YouTubePlayerLayout playerToDestroy;

  @Override
  public void onPopupDismissPrepare (PopupLayout popup) {
    if (inFullscreen) {
      onRequestFullscreen(false);
    }
    TdlibManager.instance().player().setPauseReason(TGPlayerController.PAUSE_REASON_OPEN_YOUTUBE, false);
    if (player != null) {
      player.onPrepareDestroy();
      playerToDestroy = player;
      player = null;
    }
  }

  @Override
  public void onDestroyPopupInternal () {
    if (playerToDestroy != null) {
      playerToDestroy.onDestroy();
      playerToDestroy = null;
    }
  }

  @Override
  public void onPlayPause () {
    try {
      if (player != null && player.getPlayer() != null && player.isReady()) {
        if (player.getPlayer().isPlaying()) {
          player.getPlayer().pause();
        } else {
          player.getPlayer().play();
        }
      }
    } catch (Throwable t) {
      Log.w("YouTube onPlayPause", t);
    }
  }

  @Override
  public void onSeekTo (int timeInMillis) {
    if (player != null) {
      player.seekToMillis(timeInMillis);
    }
  }

  @Override
  public void onRequestMinimize () {
    if (player == null || player.getPlayer() == null || !player.isReady() || controls == null) {
      return;
    }
    final BaseActivity context = ((BaseActivity) getContext());
    if (inFullscreen) {
      processMinimizeFullscreen();
    }
    context.pretendYouDontKnowThisWindow(popupLayout);
    popupLayout.setHideBackground(true);
    popupLayout.setDisableCancelOnTouchDown(true);
    setFooterVisibility(View.GONE);
    controls.setMinimized(minimized = true);
    computeHeight(getMeasuredWidth());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      player.setElevation(Screen.dp(3f));
      player.setTranslationZ(Screen.dp(1f));
      player.setOutlineProvider(new android.view.ViewOutlineProvider() {
        @TargetApi (value = 21)
        @Override
        public void getOutline (View view, android.graphics.Outline outline) {
          outline.setRect(0, 0, player.getMeasuredWidth(), player.getMeasuredHeight());
        }
      });
    }
    player.requestLayout();
  }

  @Override
  public void onRequestExpand () {
    if (controls == null || isAnimating) {
      return;
    }
    final BaseActivity context = UI.getContext(getContext());
    context.letsRememberAboutThisWindow(popupLayout);
    popupLayout.setHideBackground(false);
    popupLayout.setDisableCancelOnTouchDown(false);
    if (inFullscreen) {
      processFullscreen(true);
    }
    setFooterVisibility(View.VISIBLE);
    controls.setMinimized(minimized = false);
    computeHeight(getMeasuredWidth());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      player.setElevation(0);
      player.setTranslationZ(0);
      player.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
    }
    player.requestLayout();
  }

  // Close stuff

  private float closeFactor;
  private float closeStartX;
  private boolean isAnimating, isClosing, closeSet;

  public float getCloseFactor () {
    return closeFactor;
  }

  public void setCloseFactor (float factor) {
    if (this.closeFactor != factor) {
      this.closeFactor = factor;
      if (player != null) {
        player.setTranslationX(playerX + playerWidth * factor);
        player.setAlpha(1f - Math.abs(factor));
      }
    }
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    if (isClosing || isAnimating) {
      return true;
    } else {
      gestureController.onTouchEvent(ev);
      return false;
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (!isClosing) {
      return super.onTouchEvent(e);
    }
    super.onTouchEvent(e);
    gestureController.onTouchEvent(e);
    switch (e.getAction()) {
      case MotionEvent.ACTION_MOVE: {
        float x = e.getX();
        if (!closeSet) {
          closeSet = true;
          closeStartX = x;
        }
        setCloseFactor((x - closeStartX) / playerWidth);
        break;
      }
      case MotionEvent.ACTION_UP: {
        applyClose(closeFactor >= CLOSE_THRESHOLD ? 1f : closeFactor <= -CLOSE_THRESHOLD ? -1f : 0f, 0f);
        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        applyClose(0f, 0f);
        break;
      }
    }
    return true;
  }

  @Override
  public void onStartClose () {
    isClosing = true;
    closeSet = false;
    requestPause();
    player.showThumb();
  }

  @Override
  public boolean onFling (float velocityX, float velocityY) {
    if (isClosing && !isAnimating) {
      float abs = Math.abs(velocityX);
      if (abs > Math.abs(velocityY) && abs > Screen.dp(350, 1f)) {
        applyClose(velocityX > 0 ? 1f : -1f, abs);
        return true;
      }
    }
    return false;
  }

  @Override
  public void forceClose (boolean animated) {
    if (animated && minimized) {
      onStartClose();
      applyClose(1f, 0);
    } else {
      popupLayout.hideWindow(animated);
    }
  }

  private void applyClose (final float to, float velocity) {
    if (isAnimating || !isClosing) {
      return;
    }

    isAnimating = true;
    isClosing = false;

    final float startFactor = getCloseFactor();
    final float diffFactor = to - startFactor;
    ValueAnimator obj = AnimatorUtils.simpleValueAnimator();
    obj.addUpdateListener(animation -> setCloseFactor(startFactor + diffFactor * AnimatorUtils.getFraction(animation)));
    obj.setDuration(velocity == 0f ? 200l : to == 1f ? 160l : 120l); // TODO calculate duration
    obj.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    obj.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        if (to != 0f) {
          forceClose(false);
        } else {
          player.hideThumb();
          requestContinue();
        }
        isAnimating = false;
      }
    });
    obj.start();
  }

  // Stop/restart

  @Override
  public void onStopped () {
    if (player != null) {
      player.showThumb();
    }
  }

  @Override
  public void onRestarted () {
    if (player != null) {
      player.hideThumb();
    }
  }


  // Pause

  private boolean pauseRequested;

  private void requestPause () {
    if (!pauseRequested && player != null && player.getPlayer() != null && player.getPlayer().isPlaying()) {
      pauseRequested = true;
      player.getPlayer().pause();
    }
  }

  @Override
  public boolean shouldIgnorePlayPause () {
    return isClosing && pauseRequested;
  }

  private void requestContinue () {
    if (pauseRequested) {
      pauseRequested = false;
      if (player != null && player.getPlayer() != null) {
        player.getPlayer().play();
      }
    }
  }

  // Fullscreen stuff


  @Override
  public void onShowHideControls (boolean show) {
    final BaseActivity context = UI.getContext(getContext());
    if (inFullscreen && context != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
        context.setWindowDecorSystemUiVisibility(show ? View.SYSTEM_UI_FLAG_VISIBLE : View.SYSTEM_UI_FLAG_LOW_PROFILE, false);
      }
    }
  }

  @Override
  public void onRequestFullscreen (boolean inFullscreen) {
    if (player == null || player.getPlayer() == null || !player.isReady()) {
      return;
    }

    controls.setFullscreen(inFullscreen);
    this.inFullscreen = inFullscreen;
    if (!inFullscreen) {
      mayHintAboutFullscreen = Settings.instance().needTutorial(Settings.TUTORIAL_YOUTUBE_ROTATION);
    }
    processFullscreen(inFullscreen);
    post(() -> requestLayout());
  }

  private static final boolean RESET_FULLSCREEN_STATE = false;

  private void processMinimizeFullscreen () {
    if (RESET_FULLSCREEN_STATE) {
      controls.setFullscreen(false);
      this.inFullscreen = false;
    }
    processFullscreen(false);
  }

  private boolean mayHintAboutFullscreen;
  private boolean inFullscreen;
  private int savedStatusBarColor;

  private void processFullscreen (boolean inFullscreen) {
    final BaseActivity context = UI.getContext(getContext());
    // ViewAnimationUtils.createCircularReveal()
    if (inFullscreen) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !Config.USE_FULLSCREEN_NAVIGATION) {
        savedStatusBarColor = context.getWindow().getStatusBarColor();
        context.getWindow().setStatusBarColor(0xff000000);
      }
      context.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
        context.setWindowDecorSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE, false);
      }
      // context.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    } else {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !Config.USE_FULLSCREEN_NAVIGATION) {
        context.getWindow().setStatusBarColor(savedStatusBarColor);
      }
      context.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
        context.setWindowDecorSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE, false);
      }
      // context.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN); //, ~WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }
  }
}
