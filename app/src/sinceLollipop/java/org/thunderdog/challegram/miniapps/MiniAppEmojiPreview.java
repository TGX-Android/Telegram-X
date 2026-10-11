/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.emoji.CustomEmojiSpanImpl;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.AvatarView;
import org.thunderdog.challegram.widget.CustomEmojiTextView;

/** The current user's name/avatar pill and emoji badge used by SetupEmojiStatusSheet. */
final class MiniAppEmojiPreview extends FrameLayout {
  private final AvatarView avatar;
  private final CustomEmojiTextView emoji;
  private final MiniAppController host;
  private final boolean highlight;
  private long[] statuses;
  private int statusIndex;
  private final Runnable rotate = new Runnable() {
    @Override public void run () {
      if (!isAttachedToWindow() || statuses == null || statuses.length == 0) return;
      setEmoji(statuses[statusIndex++ % statuses.length]); postDelayed(this, 2500);
    }
  };

  MiniAppEmojiPreview (MiniAppController host, long emojiId) {
    super(host.context()); this.host = host; highlight = emojiId != 0;
    setMinimumHeight(Screen.dp(100)); setBackgroundColor(Theme.getColor(ColorId.fillingPositive));
    LinearLayout pill = new LinearLayout(host.context()); pill.setGravity(Gravity.CENTER_VERTICAL);
    pill.setBackground(background(16));
    addView(pill, new LayoutParams(-2, Screen.dp(32), Gravity.CENTER));
    avatar = new AvatarView(host.context()); avatar.setUser(host.tdlib(), host.tdlib().myUserId(), false);
    pill.addView(avatar, new LinearLayout.LayoutParams(Screen.dp(32), Screen.dp(32)));
    TextView name = new TextView(host.context()); name.setText(host.tdlib().cache().userName(host.tdlib().myUserId()));
    name.setTextSize(14); name.setTextColor(Theme.getColor(ColorId.text)); name.setMaxWidth(Screen.dp(190)); name.setSingleLine();
    name.setEllipsize(android.text.TextUtils.TruncateAt.END); name.setPadding(Screen.dp(4), 0, Screen.dp(6), 0);
    pill.addView(name, new LinearLayout.LayoutParams(-2, -2));
    FrameLayout badge = new FrameLayout(host.context());
    LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(Screen.dp(highlight ? 48 : 28), Screen.dp(32)); slot.rightMargin = Screen.dp(7); pill.addView(badge, slot);
    pill.setClipChildren(false); badge.setClipChildren(false);
    if (highlight) {
      android.view.View circle = new android.view.View(host.context()); circle.setBackground(background(24)); circle.setElevation(Screen.dp(2));
      badge.addView(circle, new LayoutParams(Screen.dp(48), Screen.dp(48), Gravity.CENTER));
    }
    emoji = new CustomEmojiTextView(host.context(), host.tdlib()); emoji.setGravity(Gravity.CENTER); emoji.setTextSize(highlight ? 32 : 24);
    emoji.setElevation(Screen.dp(3)); badge.addView(emoji, new LayoutParams(Screen.dp(highlight ? 40 : 28), Screen.dp(40), Gravity.CENTER));
    if (highlight) setEmoji(emojiId);
  }
  private GradientDrawable background (int radius) {
    GradientDrawable shape = new GradientDrawable(); shape.setColor(Theme.getColor(ColorId.filling)); shape.setCornerRadius(Screen.dp(radius)); return shape;
  }
  private void setEmoji (long id) {
    SpannableString text = new SpannableString("*");
    text.setSpan(new CustomEmojiSpanImpl(null, emoji, host.tdlib(), id), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); emoji.setText(text);
  }
  @Override protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    if (!highlight) host.tdlib().send(new TdApi.GetDefaultEmojiStatuses(), (result, error) -> host.tdlib().ui().post(() -> {
      if (error == null && isAttachedToWindow()) { statuses = result.customEmojiIds; rotate.run(); }
    }));
  }
  @Override protected void onDetachedFromWindow () { removeCallbacks(rotate); avatar.performDestroy(); emoji.performDestroy(); super.onDetachedFromWindow(); }
}
