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
 * File created on 08/11/2016
 */
package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.text.BoringLayout;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.tool.Screen;

// Single-line emoji text with ellipsize
public class EmojiString {
  private final Layout layout;

  private final String text;
  private final TextPaint textPaint;
  private final int maxWidth;

  private float preferredMinWidth;

  @Override
  public boolean equals (@Nullable Object obj) {
    return obj instanceof EmojiString && ((EmojiString) obj).text.equals(this.text);
  }

  @Override
  public int hashCode() {
    return text.hashCode();
  }

  public EmojiString (String text, int maxWidth, TextPaint textPaint) {
    this.text = text;
    this.textPaint = textPaint;
    this.maxWidth = maxWidth;
    boolean ellipsized = false;
    CharSequence emojiText = Emoji.instance().replaceEmoji(text);
    // CharSequence emojiText;
    if (maxWidth <= 0) {
      // emojiText = TextUtils.ellipsize(sourceEmojiText, textPaint, maxWidth * 2, TextUtils.TruncateAt.END);
      // ellipsized = emojiText.length() < sourceEmojiText.length() || !Strings.compare(emojiText, sourceEmojiText);
      maxWidth = Screen.widestSide();
    }
    BoringLayout.Metrics metrics = BoringLayout.isBoring(emojiText, textPaint);
    if (metrics != null && metrics.width <= maxWidth) {
      layout = new BoringLayout(emojiText, textPaint, maxWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0f, metrics, false);
    } else {
      StaticLayout layout = new StaticLayout(emojiText, 0, emojiText.length(), textPaint, maxWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0, false);
      int lineCount = layout.getLineCount();
      if (lineCount > 2) {
        float totalWidth = Math.max(layout.getLineWidth(0), layout.getLineWidth(1)) - U.measureText("…", textPaint);
        int newEnd = layout.getLineEnd(1);
        layout = new StaticLayout(emojiText, 0, newEnd, textPaint, maxWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0, false, TextUtils.TruncateAt.END, (int) totalWidth);
      }
      if (lineCount > 1) {
        int i = 0;
        int len = emojiText.length();
        while (i < len && emojiText.charAt(i) != '\n') {
          i++;
        }
        if (i > 0) {
          preferredMinWidth = U.measureEmojiText(emojiText, 0, i, textPaint);
        }
      }
      this.layout = layout;
    }

    /*boolean needMinWidth = ellipsized;
    final int lineCount = layout.getLineCount();
    if (needMinWidth && lineCount > 1) {
      float maxLineWidth = 0;
      for (int i = 0; i < lineCount; i++) {
        float lineWidth = layout.getLineWidth(i);
        maxLineWidth = Math.max(maxLineWidth, lineWidth);
      }
      if (maxLineWidth < maxWidth) {
        int end = layout.getLineEnd(0) - 1;
        if (end >= 0 && end < text.length()) {
          char c = text.charAt(end);
          int type = Character.getType(c);
          if (type == Character.SPACE_SEPARATOR || type == Character.LINE_SEPARATOR) {
            needMinWidth = false;
          }
        }
      }
    }*/

    if (ellipsized) {
      int i;
      int start = 0;
      do {
        i = text.indexOf('\n', start);
        float textWidth;
        if (i == -1) {
          textWidth = U.measureEmojiText(emojiText, start, emojiText.length(), textPaint);
        } else {
          textWidth = U.measureEmojiText(emojiText, start, i, textPaint);
          start = i + 1;
        }
        preferredMinWidth = Math.max(preferredMinWidth, textWidth);
      } while (i != -1);
    }
  }

  public float getPreferredMinWidth () {
    return preferredMinWidth;
  }

  public int getWidth () {
    return layout.getWidth();
  }

  public int getMaxLineWidth () {
    int lineCount = layout.getLineCount();
    float max = 0.0f;
    for (int i = 0; i < lineCount; i++) {
      max = Math.max(max, layout.getLineWidth(i));
    }
    return (int) max;
  }

  public int getTextWidth () {
    return layout.getLineCount() > 0 ? (int) layout.getLineWidth(0) : 0;
  }

  public String getText () {
    return text;
  }

  public int getMaxWidth () {
    return maxWidth;
  }

  public void draw (Canvas c, int x, int y, int color, boolean center) {
    int lineCount = layout.getLineCount();
    if (center && lineCount > 1) {
      int top = layout.getLineBottom(0);
      int bottom = layout.getLineBottom(1);
      y -= (bottom - top) / 2;
    }
    c.save();
    c.translate(x, y);
    if (lineCount > 2) {
      c.clipRect(Math.min(layout.getLineLeft(0), layout.getLineLeft(1)), layout.getLineTop(0), Math.max(layout.getLineRight(0), layout.getLineRight(1)), layout.getLineBottom(1));
    }
    textPaint.setColor(color);
    layout.draw(c);
    c.restore();
  }
}
