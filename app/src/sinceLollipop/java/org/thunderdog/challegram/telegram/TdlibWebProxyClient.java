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
 * File created on 03/11/2026
 */
package org.thunderdog.challegram.telegram;

import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.RequiresApi;

import java.io.ByteArrayInputStream;
import java.util.Collections;

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class TdlibWebProxyClient extends WebViewClient {
  interface Callback {
    boolean isAllowed (Uri uri);
    boolean isExpectedPage (String url);
    void onLoadFailed (String reason);
    void onTerminated ();
  }

  protected final Callback callback;

  TdlibWebProxyClient (Callback callback) {
    this.callback = callback;
  }

  static WebResourceResponse blockedResponse () {
    return new WebResourceResponse(
      "text/plain",
      "UTF-8",
      403,
      "Forbidden",
      Collections.emptyMap(),
      new ByteArrayInputStream(new byte[0])
    );
  }

  @Override
  public boolean shouldOverrideUrlLoading (WebView view, String url) {
    callback.onLoadFailed("Relay navigation blocked");
    return true;
  }

  // The String overload stops being called on API 24, this one exists only since API 24,
  // but its parameter type is available since API 21.
  @Override
  public boolean shouldOverrideUrlLoading (WebView view, WebResourceRequest request) {
    if (request.isForMainFrame()) {
      callback.onLoadFailed("Relay navigation blocked");
    }
    return true;
  }

  @Override
  public WebResourceResponse shouldInterceptRequest (WebView view, WebResourceRequest request) {
    return callback.isAllowed(request.getUrl()) ? null : blockedResponse();
  }

  @Override
  public void onPageStarted (WebView view, String url, Bitmap favicon) {
    if (!callback.isExpectedPage(url)) {
      callback.onLoadFailed("Relay navigation blocked");
    }
  }

  @Override
  public void onReceivedSslError (WebView view, SslErrorHandler ssl, SslError error) {
    ssl.cancel();
    callback.onLoadFailed("Relay TLS error");
  }

  @Override
  public void onReceivedError (WebView view, int code, String description, String url) {
    callback.onLoadFailed("Relay network error");
  }

  @Override
  public void onReceivedHttpError (
    WebView view,
    WebResourceRequest request,
    WebResourceResponse response
  ) {
    if (request.isForMainFrame()) {
      callback.onLoadFailed("Relay HTTP error");
    }
  }
}
