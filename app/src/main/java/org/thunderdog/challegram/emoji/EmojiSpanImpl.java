/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 04/05/2019
 */
package org.thunderdog.challegram.emoji;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Spannable;
import android.text.style.BackgroundColorSpan;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Paints;

import me.vkryl.core.ColorUtils;

class EmojiSpanImpl extends ReplacementSpan implements EmojiSpan {
  public static EmojiSpan newSpan (@Nullable EmojiInfo info) {
    return new EmojiSpanImpl(info);
  }

  public static EmojiSpan newCustomEmojiSpan (
    @Nullable EmojiInfo info,
    CustomEmojiSurfaceProvider surfaceProvider,
    Tdlib tdlib, long customEmojiId) {
    return new CustomEmojiSpanImpl(info, surfaceProvider, tdlib, customEmojiId);
  }

  protected final @Nullable EmojiInfo info;

  private final Paint.FontMetricsInt mTmpFontMetrics = new Paint.FontMetricsInt();
  protected int mSize = -1;

  protected EmojiSpanImpl (@Nullable EmojiInfo info) {
    this.info = info;
  }

  @Override
  public final EmojiSpan toBuiltInEmojiSpan () {
    return info != null ? newSpan(info) : null;
  }

  public boolean isCustomEmoji () {
    return false;
  }

  @Override
  public final int getRawSize (Paint paint) {
    if (mSize == -1) {
      getSize(paint, null, 0, 0, null);
    }
    return mSize;
  }

  @Override
  public final int getSize (@NonNull final Paint paint, final CharSequence text, final int start,
                      final int end, final Paint.FontMetricsInt fm) {
    paint.getFontMetricsInt(mTmpFontMetrics);
    mSize = Math.abs(mTmpFontMetrics.descent - mTmpFontMetrics.ascent);

    if (fm != null) {
      fm.ascent = mTmpFontMetrics.ascent;
      fm.descent = mTmpFontMetrics.descent;
      fm.top = mTmpFontMetrics.top;
      fm.bottom = mTmpFontMetrics.bottom;
    }

    return mSize;
  }

  protected final int getEmojiSize (Paint paint) {
    if (mSize == -1) {
      getRawSize(paint);
    }
    return mSize;
  }

  protected boolean needInvalidate;

  @Override
  public final boolean needRefresh () {
    return needInvalidate;
  }

  @Override
  public final void draw (@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
    int emojiSize = getEmojiSize(paint);

    float centerX = x + emojiSize / 2f;
    float height = (bottom - top);
    float centerY = top + height / 2f;

    if (text instanceof Spannable) {
      Spannable spannable = (Spannable) text;
      BackgroundColorSpan[] backgroundColorSpans = spannable.getSpans(start, end, BackgroundColorSpan.class);
      if (backgroundColorSpans != null && backgroundColorSpans.length > 0) {
        int blendedColor = 0;
        for (BackgroundColorSpan backgroundColorSpan : backgroundColorSpans) {
          int backgroundColor = backgroundColorSpan.getBackgroundColor();
          if (blendedColor == 0) {
            blendedColor = backgroundColor;
          } else {
            blendedColor = ColorUtils.compositeColor(blendedColor, backgroundColor);
          }
        }
        if (Color.alpha(blendedColor) != 0) {
          canvas.drawRect(x, top, x + emojiSize, bottom, Paints.fillingPaint(blendedColor));
        }
      }
    }

    drawEmoji(canvas, centerX, centerY, emojiSize);
  }

  protected void drawEmoji (Canvas canvas, float centerX, float centerY, int emojiSize) {
    Rect rect = Paints.getRect();

    int reduce = Emoji.instance().getReduceSize();

    rect.left = (int) (centerX - emojiSize / 2f) + reduce / 2;
    rect.top = (int) (centerY - emojiSize / 2f) + reduce / 2;
    rect.right = rect.left + emojiSize - reduce / 2 - reduce % 2;
    rect.bottom = rect.top + emojiSize - reduce / 2 - reduce % 2;

    this.needInvalidate = !Emoji.instance().draw(canvas, info, rect);
  }
}
