/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

/** Defensive endpoint. The manager rejects this platform before creating a session or WebView. */
public final class MiniAppController extends ViewController<Void> {
  public MiniAppController (Context context, Tdlib tdlib, MiniAppLaunchRequest request, Runnable onOpened) {
    super(context, tdlib);
  }

  @Override
  public int getId () { return R.id.controller_miniApp; }

  @Override
  protected View onCreateView (Context context) { return new View(context); }

  public long launchId () { return 0; }
  public boolean isMinimized () { return false; }
  public boolean canRestore (MiniAppLaunchRequest request) { return false; }
  public String botName () { return ""; }
  public android.graphics.Bitmap preview () { return null; }
  public void restore () { }
  public void requestClose () { close(); }

  public void open (boolean allowWriteAccess) {
    UI.showToast(R.string.MiniAppWebViewUnavailable, Toast.LENGTH_LONG);
    close();
  }

  public void close () { destroy(); }

  @Override
  public void destroy () {
    if (isDestroyed()) return;
    tdlib.miniApps().onClosed(this);
    super.destroy();
  }
}
