/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.AvatarView;

/** Telegram's user → bot permission illustration: 50dp avatars separated by a 36dp arrow. */
final class MiniAppPermissionView extends FrameLayout {
  private final AvatarView user, bot;
  private MiniAppPermissionView (MiniAppController host, int badge) {
    super(host.context());
    setMinimumHeight(Screen.dp(100));
    setBackgroundColor(Theme.getColor(ColorId.fillingPositive));
    user = new AvatarView(host.context()); user.setUser(host.tdlib(), host.tdlib().myUserId(), false);
    bot = new AvatarView(host.context()); bot.setUser(host.tdlib(), host.botId(), false);
    LayoutParams a = new LayoutParams(Screen.dp(50), Screen.dp(50), Gravity.CENTER); a.rightMargin = Screen.dp(86); addView(user, a);
    LayoutParams b = new LayoutParams(Screen.dp(50), Screen.dp(50), Gravity.CENTER); b.leftMargin = Screen.dp(86); addView(bot, b);
    ImageView arrow = new ImageView(host.context()); arrow.setImageResource(R.drawable.round_keyboard_arrow_right_24); arrow.setColorFilter(android.graphics.Color.WHITE);
    addView(arrow, new LayoutParams(Screen.dp(24), Screen.dp(24), Gravity.CENTER));
    ImageView symbol = new ImageView(host.context()); symbol.setImageResource(badge); symbol.setColorFilter(android.graphics.Color.WHITE);
    LayoutParams s = new LayoutParams(Screen.dp(24), Screen.dp(24), Gravity.CENTER); s.rightMargin = Screen.dp(48); s.topMargin = Screen.dp(36); addView(symbol, s);
  }
  static MiniAppPermissionView location (MiniAppController host) { return new MiniAppPermissionView(host, R.drawable.baseline_location_on_24); }
  @Override protected void onDetachedFromWindow () { super.onDetachedFromWindow(); user.performDestroy(); bot.performDestroy(); }
}
