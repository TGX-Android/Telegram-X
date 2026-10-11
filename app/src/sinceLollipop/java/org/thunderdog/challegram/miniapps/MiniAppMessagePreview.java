/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.WallpaperView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.MessagesController;

/** Uses the chat renderer for text, entities, media, captions and inline markup. No preview actions are executable. */
final class MiniAppMessagePreview extends FrameLayout {
  private final MessagesController renderer;
  private final MessageView messageView;
  private boolean destroyed;

  MiniAppMessagePreview (MiniAppController host, TdApi.Chat self, MiniAppInlinePreview preview) {
    super(host.context());
    renderer = new MessagesController(host.context(), host.tdlib());
    MessagesController.Arguments arguments = new MessagesController.Arguments(MessagesController.PREVIEW_MODE_WALLPAPER_OBJECT, null, self);
    // Wallpaper preview mode forces chat bubbles without changing the user's chat style or wallpaper settings.
    arguments.wallpaperObject = new TdApi.Background(0, false, Theme.getDarkFactor() > .5f, "", null,
      new TdApi.BackgroundTypeFill(new TdApi.BackgroundFillSolid(Theme.getColor(ColorId.background) & 0xffffff)));
    renderer.setArguments(arguments);
    // Initialize the ordinary renderer's metrics, wallpaper and receivers in its existing preview mode.
    renderer.getValue();
    WallpaperView wallpaper = renderer.wallpaper();
    wallpaper.initWithCustomWallpaper(host.tdlib().settings().getWallpaper(Theme.getWallpaperIdentifier()));
    if (wallpaper.getParent() instanceof ViewGroup) ((ViewGroup) wallpaper.getParent()).removeView(wallpaper);
    addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout chat = new LinearLayout(getContext()); chat.setOrientation(LinearLayout.VERTICAL);
    chat.setPadding(Screen.dp(4), Screen.dp(8), Screen.dp(4), Screen.dp(8));
    TextView caption = new TextView(getContext()); caption.setText(Lang.getString(R.string.MiniAppMessagePreview));
    caption.setTextSize(13); caption.setTextColor(Theme.textDecentColor()); caption.setGravity(Gravity.CENTER);
    chat.addView(caption, new LinearLayout.LayoutParams(-1, Screen.dp(28)));
    TdApi.Message message = TD.newFakeMessage(self.id, new TdApi.MessageSenderUser(host.tdlib().myUserId()), preview.content);
    message.date = (int) (System.currentTimeMillis() / 1000L);
    message.viaBotUserId = host.botId(); message.replyMarkup = preview.replyMarkup;
    TGMessage parsed = TGMessage.valueOf(renderer.getManager(), message, self, null, (TdApi.ChatAdministrator) null);
    messageView = new MessageView(getContext()); messageView.setManager(renderer.getManager());
    messageView.setParentOnMeasureDisabled(true); messageView.setUseReceivers(); messageView.setUseComplexReceiver();
    messageView.setMessage(parsed);
    chat.addView(messageView, new LinearLayout.LayoutParams(-1, -2));
    addView(chat, new FrameLayout.LayoutParams(-1, -2));
  }

  @Override public boolean onInterceptTouchEvent (MotionEvent event) { return true; }
  @Override public boolean onTouchEvent (MotionEvent event) { return true; }

  void destroy () {
    if (destroyed) return;
    destroyed = true; messageView.performDestroy(); renderer.destroy();
  }
}
