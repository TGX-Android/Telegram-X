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
 * File created on 06/12/2016
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.webkit.WebView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.telegram.Tdlib;

public class TelegramFaqController extends WebkitController<Void> {
  public TelegramFaqController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  protected void onCreateWebView (DoubleHeaderView headerCell, WebView webView) {
    String url = Lang.getString(R.string.url_faq);
    headerCell.setTitle(R.string.TelegramFAQ);
    headerCell.setSubtitle(url);
    webView.loadUrl(url);
  }
}
