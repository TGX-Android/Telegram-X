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
 */
package org.thunderdog.challegram.component.preview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.EmbeddedService;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.PopupLayout;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

@SuppressLint("ViewConstructor")
public class WebViewPreviewLayout extends PreviewLayout {
  private FrameLayout container, containerFullscreen;
  private WebView preview;
  private View previewProgress, customView;
  private int lastHeight, savedStatusBarColor;
  private WebChromeClient.CustomViewCallback customViewCallback;

  private final BoolAnimator progressVisibility = new BoolAnimator(0, (id, factor, fraction, callee) -> {
    previewProgress.setAlpha(MathUtils.clamp(factor));
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);

  public WebViewPreviewLayout (Context context, ViewController<?> parent) {
    super(context, parent);
    UI.setFullscreenIfNeeded(this);
  }

  @SuppressLint("SetJavaScriptEnabled")
  @Override
  public boolean setPreview (EmbeddedService nativeEmbed) {
    TdlibManager.instance().player().setPauseReason(TGPlayerController.PAUSE_REASON_OPEN_WEB_VIDEO, true);

    preview = new WebView(getContext());
    ViewSupport.setThemedBackground(preview, ColorId.placeholder);
    preview.getSettings().setDomStorageEnabled(true);
    preview.getSettings().setJavaScriptEnabled(true);
    preview.getSettings().setAllowContentAccess(true);
    preview.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      preview.getSettings().setMediaPlaybackRequiresUserGesture(false);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      preview.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
      CookieManager.getInstance().setAcceptThirdPartyCookies(preview, true);
    }

    preview.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP));
    preview.setWebViewClient(new WebViewClient() {
      @Override
      public void onPageFinished (WebView view, String url) {
        progressVisibility.setValue(false, true);
      }

      @Override
      public boolean shouldOverrideUrlLoading (WebView view, String url) {
        UI.openUrl(url);
        return true;
      }
    });

    preview.setWebChromeClient(new WebChromeClient() {
      @Override
      public void onHideCustomView () {
        if (customView == null) {
          return;
        }

        containerFullscreen.setVisibility(View.GONE);
        containerFullscreen.removeView(customView);
        customViewCallback.onCustomViewHidden();
        customView = null;
        customViewCallback = null;
        processFullscreen(false);
      }

      @Override
      public void onShowCustomView (View view, CustomViewCallback callback) {
        if (customView != null) {
          callback.onCustomViewHidden();
          return;
        }

        customView = view;
        containerFullscreen.setVisibility(View.VISIBLE);
        containerFullscreen.addView(customView, FrameLayoutFix.newParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        customViewCallback = callback;
        processFullscreen(true);
      }

      @Override
      public void onShowCustomView (View view, int requestedOrientation, CustomViewCallback callback) {
        onShowCustomView(view, callback);
      }
    });

    previewProgress = Views.simpleProgressView(getContext(), FrameLayoutFix.newParams(Screen.dp(48f), Screen.dp(48f), Gravity.CENTER));
    progressVisibility.setValue(true, false);

    containerFullscreen = new FrameLayout(getContext());
    containerFullscreen.setBackgroundColor(Color.BLACK);
    containerFullscreen.setVisibility(View.GONE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      containerFullscreen.setFitsSystemWindows(true);
    }

    container = new FrameLayout(getContext());
    container.addView(preview);
    container.addView(previewProgress);

    addView(container);
    addView(containerFullscreen);

    return super.setPreview(nativeEmbed);
  }

  private boolean inFullScreen() {
    return customView != null;
  }

  private int getWebViewHeight (int currentWidth) {
    if (nativeEmbed.type == EmbeddedService.TYPE_CUSTOM_EMBED && nativeEmbed.height != 1) {
      return nativeEmbed.height;
    }

    float scale = nativeEmbed.width / (float) currentWidth;
    return (int) Math.min(nativeEmbed.height / scale, Screen.getDisplayHeight() / 2f);
  }

  @Override
  protected int computeHeight (int currentWidth) {
    lastHeight = getWebViewHeight(currentWidth);
    container.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, lastHeight, Gravity.TOP));
    return inFullScreen() ? Screen.currentHeight() : (footerHeight + lastHeight);
  }

  @Override
  protected int getPreviewHeight () {
    return Screen.currentActualHeight();
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
  protected boolean onPrepareNextPreview (String pageUrl) {
    return false;
  }

  @Override
  protected void onDestroyPopupInternal () {
    preview.destroy();
  }

  @Override
  public void forceClose (boolean animated) {
    popupLayout.hideWindow(animated);
  }

  @Override
  public void onPopupDismissPrepare (PopupLayout popup) {
    TdlibManager.instance().player().setPauseReason(TGPlayerController.PAUSE_REASON_OPEN_WEB_VIDEO, false);
    if (inFullScreen()) {
      processFullscreen(false);
    }
  }

  @Override
  public void onPopupCompletelyShown (PopupLayout popup) {
    preview.loadUrl(nativeEmbed.embedUrl);
  }

  private void processFullscreen (boolean inFullscreen) {
    final BaseActivity context = UI.getContext(getContext());
    context.setScreenFlagEnabled(BaseActivity.SCREEN_FLAG_PLAYING_FULLSCREEN_WEB_VIDEO, inFullscreen);
    if (inFullscreen) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !Config.USE_FULLSCREEN_NAVIGATION) {
        savedStatusBarColor = context.getWindow().getStatusBarColor();
        context.getWindow().setStatusBarColor(0xff000000);
      }
      context.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
      context.setWindowDecorSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE, false);
    } else {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !Config.USE_FULLSCREEN_NAVIGATION) {
        context.getWindow().setStatusBarColor(savedStatusBarColor);
      }
      context.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
      context.setWindowDecorSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE, false);
    }
  }
}
