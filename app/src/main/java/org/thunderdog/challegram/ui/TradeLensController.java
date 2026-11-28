/*
 * This file is a part of TradeLens Connect, an unofficial Telegram client derived from Telegram X
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
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tradelens.TLConsent;

/**
 * The TradeLens screen: hosts the TradeLens Connect mini app inside the client.
 *
 * Telegram signs the launch data (initData) through TDLib's openWebApp, exactly as official Telegram does,
 * so the TradeLens server can verify the user's Telegram identity. This client does not run Telegram Mini Apps
 * in general; it only opens this one, from the TradeLens bot, and only allows navigation within the TradeLens site.
 */
public class TradeLensController extends WebkitController<Void> {
  private static final String BOT_USERNAME = "TradeLensagent";
  private static final String SITE_HOST = "tradelens360ai.live";
  private static final String MINIAPP_URL = "https://" + SITE_HOST + "/tg/";

  private long launchId;
  private boolean opened;
  private String launchUrl = MINIAPP_URL;

  /** True only for the TradeLens Mini App's own address (https, the TradeLens site, under /tg/). */
  public static boolean isMiniAppUrl (String url) {
    if (url == null) {
      return false;
    }
    try {
      Uri uri = Uri.parse(url);
      return "https".equals(uri.getScheme()) && SITE_HOST.equals(uri.getHost()) && uri.getUserInfo() == null
        && uri.getPath() != null && uri.getPath().startsWith("/tg/");
    } catch (Throwable t) {
      return false;
    }
  }

  /** Opens the Mini App at this address (for example with ?s=<signal id>, from an alert's button). Ignored unless it is the Mini App's own address. */
  public void setLaunchUrl (String url) {
    if (isMiniAppUrl(url)) {
      launchUrl = url;
    }
  }

  public TradeLensController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  protected void onCreateWebView (DoubleHeaderView headerCell, WebView webView) {
    headerCell.setTitle(R.string.TradeLens);
    webView.getSettings().setAllowFileAccess(false);
    webView.getSettings().setAllowContentAccess(false);
    webView.addJavascriptInterface(new Bridge(), "TelegramWebviewProxy");
    if (TLConsent.accepted()) {
      openMiniApp();
    } else {
      TLConsent.ask(this, this::openMiniApp);
    }
  }

  private void openMiniApp () {
    if (opened) {
      return;
    }
    opened = true;
    tdlib.send(new TdApi.SearchPublicChat(BOT_USERNAME), (chat, error) -> {
      if (chat == null || chat.type.getConstructor() != TdApi.ChatTypePrivate.CONSTRUCTOR) {
        failed();
        return;
      }
      long botUserId = ((TdApi.ChatTypePrivate) chat.type).userId;
      TdApi.WebAppOpenParameters parameters = new TdApi.WebAppOpenParameters(null, "TradeLensConnect", new TdApi.WebAppOpenModeFullSize());
      tdlib.send(new TdApi.OpenWebApp(chat.id, botUserId, launchUrl, null, null, parameters), (info, openError) -> {
        if (info == null || info.url == null || info.url.url == null) {
          failed();
          return;
        }
        launchId = info.launchId;
        final String url = info.url.url;
        UI.post(() -> {
          if (!isDestroyed()) {
            loadUrl(url);
          }
        });
      });
    });
  }

  private void failed () {
    opened = false;
    UI.post(() -> {
      if (!isDestroyed()) {
        UI.showToast(R.string.TradeLensUnavailable, android.widget.Toast.LENGTH_LONG);
      }
    });
  }

  @Override
  public void destroy () {
    super.destroy();
    if (launchId != 0) {
      tdlib.send(new TdApi.CloseWebApp(launchId), tdlib.typedOkHandler());
      launchId = 0;
    }
  }

  @Override
  protected boolean hasSpecialProcessing () {
    return true;
  }

  /** Returns true when the navigation is handled here (blocked in the web view). Only the TradeLens site stays inside the app. */
  @Override
  protected boolean processSpecial (Uri url) {
    if (url == null) {
      return true;
    }
    if ("https".equals(url.getScheme()) && SITE_HOST.equals(url.getHost())) {
      return false;
    }
    if ("about".equals(url.getScheme())) {
      return false;
    }
    if ("https".equals(url.getScheme())) {
      UI.openUrl(url.toString());
    }
    return true;
  }

  /** The minimum of Telegram's web view bridge that the TradeLens mini app needs. */
  private final class Bridge {
    @JavascriptInterface
    public void postEvent (String eventType, String eventData) {
      if ("web_app_close".equals(eventType)) {
        UI.post(() -> {
          if (!isDestroyed()) {
            navigateBack();
          }
        });
      }
    }
  }
}
