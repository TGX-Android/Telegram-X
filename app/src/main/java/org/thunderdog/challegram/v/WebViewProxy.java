package org.thunderdog.challegram.v;

import android.webkit.JavascriptInterface;

import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.GameController;
import org.thunderdog.challegram.ui.ShareController;

/**
 * Date: 26/01/2017
 * Author: default
 */
@SuppressWarnings("unused")
public final class WebViewProxy {
  private final GameController context;

  public WebViewProxy (GameController context) {
    this.context = context;
  }

  @JavascriptInterface
  public final void postEvent (final String eventName, final String eventData) {
    UI.post(() -> {
      if (context.getArguments() != null && context.getArgumentsStrict().message != null) {
        GameController.Args args = context.getArgumentsStrict();

        if ("share_game".equals(eventName)) {
          ShareController c = new ShareController(context.context(), context.tdlib());
          c.setArguments(new ShareController.Args(args.game, args.userId, args.message, false));
          c.show();
        } else if ("share_score".equals(eventName)) {
          ShareController c = new ShareController(context.context(), context.tdlib());
          c.setArguments(new ShareController.Args(args.game, args.userId, args.message, true));
          c.show();
        }
      }
    });
  }
}
