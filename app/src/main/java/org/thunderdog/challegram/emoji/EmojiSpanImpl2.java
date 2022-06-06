/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
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
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.tool.Paints;

class EmojiSpanImpl2 extends ReplacementSpan implements EmojiSpan {
  public static EmojiSpan newSpan (CharSequence code, EmojiInfo info) {
    return new EmojiSpanImpl2(code, info);
  }

  private final CharSequence code;
  private final EmojiInfo info;

  private final Paint.FontMetricsInt mTmpFontMetrics = new Paint.FontMetricsInt();
  private int mSize = -1;

  private EmojiSpanImpl2 (CharSequence code, EmojiInfo info) {
    this.code = code;
    this.info = info;
  }

  @Override
  public CharSequence getEmojiCode () {
    return code;
  }

  @Override
  public int getRawSize (Paint paint) {
    if (mSize == -1)
      getSize(paint, code, 0, code.length(), null);
    return mSize;
  }

  @Override
  public int getSize(@NonNull final Paint paint, final CharSequence text, final int start,
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

  private boolean needInvalidate;

  @Override
  public boolean needRefresh () {
    return needInvalidate;
  }

  @Override
  public void draw (@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
    if (mSize == -1)
      getRawSize(paint);

    float centerX = x + mSize / 2f;
    float height = (bottom - top);
    float centerY = top + height / 2f;

    Rect rect = Paints.getRect();

    int reduce = Emoji.instance().getReduceSize();

    rect.left = (int) (centerX - mSize / 2f) + reduce / 2;
    rect.top = (int) (centerY - mSize / 2f) + reduce / 2;
    rect.right = rect.left + mSize - reduce / 2 - reduce % 2;
    rect.bottom = rect.top + mSize - reduce / 2 - reduce % 2;

    this.needInvalidate = !Emoji.instance().draw(canvas, info, rect);
  }
}
