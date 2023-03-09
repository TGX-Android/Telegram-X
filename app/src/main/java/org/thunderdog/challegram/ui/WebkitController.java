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
 * File created on 15/11/2016
 */
package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.widget.FrameLayoutFix;

public class WebkitController<T> extends ViewController<T> {
  private WebView webView;
  private DoubleHeaderView headerCell;

  public WebkitController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_webkit;
  }

  @SuppressLint("SetJavaScriptEnabled")
  @Override
  protected final View onCreateView (Context context) {
    headerCell = new DoubleHeaderView(context());
    headerCell.setThemedTextColor(this);
    headerCell.initWithMargin(Screen.dp(49f), true);

    FrameLayoutFix contentView = new FrameLayoutFix(context) {
      @Override
      public boolean onTouchEvent (MotionEvent event) {
        return true;
      }
    };
    ViewSupport.setThemedBackground(contentView, R.id.theme_color_filling, this);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    // FIXME android.webkit.WebViewFactory$MissingWebViewPackageException
    webView = new WebView(context);
    webView.getSettings().setJavaScriptEnabled(true);
    webView.getSettings().setDomStorageEnabled(true);
    webView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      // FIXME maybe better to remove?
      webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
      CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
    }

    if (hasSpecialProcessing()) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        webView.setWebViewClient(new WebViewClient() {
          @Override
          public void onPageFinished (WebView view, String url) {
            Uri uri;
            try {
              uri = Uri.parse(url);
            } catch (Throwable t) {
              uri = null;
            }
            if (uri == null || !processSpecial(uri))
              super.onPageFinished(view, url);
          }

          @Override
          public boolean shouldOverrideUrlLoading (WebView view, WebResourceRequest request) {
            return processSpecial(request.getUrl()) || super.shouldOverrideUrlLoading(view, request);
          }
        });
      } else {
        webView.setWebViewClient(new WebViewClient() {
          @Override
          public void onPageFinished (WebView view, String url) {
            Uri uri;
            try {
              uri = Uri.parse(url);
            } catch (Throwable t) {
              uri = null;
            }
            if (uri == null || !processSpecial(uri))
              super.onPageFinished(view, url);
          }

          @Override
          public boolean shouldOverrideUrlLoading (WebView view, String url) {
            Uri uri;
            try {
              uri = Uri.parse(url);
            } catch (Throwable t) {
              uri = null;
            }
            return (uri != null && processSpecial(uri)) || super.shouldOverrideUrlLoading(view, url);
          }
        });
      }
    } else {
      webView.setWebViewClient(new WebViewClient());
    }
    webView.setWebChromeClient(new WebChromeClient() {
      @Override
      public void onProgressChanged (WebView view, int newProgress) {
        onPageProgress((float) newProgress / 100f);
      }
    });
    onCreateWebView(headerCell, webView);

    contentView.addView(webView);

    return contentView;
  }

  @Override
  public View getViewForApplyingOffsets () {
    return webView;
  }

  protected void onCreateWebView (DoubleHeaderView headerCell, WebView webView) {
    if (getArguments() != null && getArguments() instanceof String) {
      headerCell.setSubtitle((String) getArguments());
      loadUrl((String) getArguments());
    }
  }

  protected final void loadUrl (String url) {
    webView.loadUrl(url);
  }

  protected void onPageProgress (float progress) {
    if (headerCell != null) {
      headerCell.animateProgress(progress);
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    webView.destroy();
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  protected boolean hasSpecialProcessing () {
    return false;
  }

  protected boolean processSpecial (Uri url) {
    return false; // override
  }
}
