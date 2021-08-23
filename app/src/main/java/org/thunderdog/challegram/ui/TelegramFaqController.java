package org.thunderdog.challegram.ui;

import android.content.Context;
import android.webkit.WebView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.telegram.Tdlib;

/**
 * Date: 06/12/2016
 * Author: default
 */

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
