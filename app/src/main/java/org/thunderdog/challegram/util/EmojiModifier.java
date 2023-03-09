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
 * File created on 02/05/2019
 */
package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.text.Layout;
import android.text.TextPaint;
import android.view.View;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.StringUtils;

public class EmojiModifier implements DrawModifier {
  private final Layout layout;

  public EmojiModifier (CharSequence text, TextPaint paint) {
    CharSequence emojiPreview = Emoji.instance().replaceEmoji(text);
    int end = StringUtils.indexOf(emojiPreview, "\n", 0);
    if (end == -1)
      end = emojiPreview.length();
    int previewWidth = (int) U.measureEmojiText(emojiPreview, 0, end, paint);
    this.layout = U.createLayout(emojiPreview, previewWidth, paint);
  }

  @Override
  public void afterDraw (View view, Canvas c) {
    c.save();
    c.translate(view.getMeasuredWidth() - Screen.dp(18f) - layout.getWidth(), view.getMeasuredHeight() / 2 - layout.getHeight() / 2);
    layout.draw(c);
    c.restore();
  }
}
