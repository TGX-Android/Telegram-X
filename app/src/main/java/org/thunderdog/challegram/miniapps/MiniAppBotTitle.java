/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

/** Inline verification mark and emoji rendering for Mini App headers and tab cards. */
public final class MiniAppBotTitle {
  private MiniAppBotTitle () { }

  public static CharSequence text (Tdlib tdlib, long botId, int badgeDp) {
    CharSequence name = Emoji.instance().replaceEmoji(tdlib.cache().userName(botId));
    TdApi.User user = tdlib.cache().user(botId);
    if (user == null || user.verificationStatus == null || !user.verificationStatus.isVerified) return name;
    SpannableStringBuilder title = new SpannableStringBuilder(name).append(" \uFFFC");
    final int size = Screen.dp(badgeDp);
    final Drawable badge = Drawables.get(UI.getResources(), R.drawable.deproko_baseline_verify_chat_24).mutate();
    badge.setBounds(0, 0, size, size);
    title.setSpan(new ReplacementSpan() {
      @Override public int getSize (Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) { return size; }
      @Override public void draw (Canvas canvas, CharSequence text, int start, int end, float x, int top, int baseline, int bottom, Paint paint) {
        badge.setColorFilter(Theme.getColor(ColorId.fillingPositive), PorterDuff.Mode.SRC_IN);
        canvas.save(); canvas.translate(x, baseline + (paint.ascent() + paint.descent() - size) / 2f);
        badge.draw(canvas); canvas.restore();
      }
    }, title.length() - 1, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return title;
  }
}
