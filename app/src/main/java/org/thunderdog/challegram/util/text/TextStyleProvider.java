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
 * File created on 23/02/2017
 */
package org.thunderdog.challegram.util.text;

import android.text.TextPaint;

import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;

public class TextStyleProvider implements Settings.FontSizeChangeListener {
  private final TextPaint regularPaint;

  private float textSize;
  private boolean allowSp;

  public Fonts.TextPaintStorage storage;

  public TextStyleProvider (TextPaint regularPaint) {
    this.regularPaint = regularPaint;
  }

  public TextStyleProvider (Fonts.TextPaintStorage textPaintStorage) {
    this.regularPaint = null;
    this.storage = textPaintStorage;
  }

  public TextStyleProvider setAllowSp (boolean allowSp) {
    this.allowSp = allowSp;
    return this;
  }

  private static void checkPaintsSize (int oldTextSize, int textSize, TextPaint... paints) {
    for (TextPaint paint : paints) {
      if (paint != null && paint.getTextSize() != oldTextSize) {
        paint.setTextSize(textSize);
      }
    }
  }

  @Override
  public void onFontSizeChanged (float newSizeDp) {
    setTextSize(newSizeDp, true);
  }

  private float sizeDiff;

  public TextStyleProvider setTextSizeDiff (float diff) {
    this.sizeDiff = diff;
    return this;
  }

  public int convertUnit (float size) {
    if (allowSp && Settings.instance().needChatFontSizeScaling()) {
      return Screen.sp(size);
    } else {
      return Screen.dp(size);
    }
  }

  public TextStyleProvider setTextSize (float textSize) {
    return setTextSize(textSize, false);
  }

  private TextStyleProvider setTextSize (float textSize, boolean force) {
    textSize += sizeDiff;
    if (force || this.textSize != textSize) {
      float oldSize = this.textSize;
      this.textSize = textSize;
      if (oldSize != 0) {
        checkPaintsSize(convertUnit(oldSize), convertUnit(textSize), regularPaint);
      }
    }
    return this;
  }

  public Fonts.TextPaintStorage getTextPaintStorage () {
    return storage;
  }

  public final TextPaint preparePaint (TextPaint paint) {
    if (textSize != 0) {
      paint.setTextSize(convertUnit(textSize));
    }
    return paint;
  }

  public int getTextSizeInPixels () {
    return convertUnit(textSize);
  }

  public float getTextSizeInDp () {
    if (allowSp && Settings.instance().needChatFontSizeScaling()) {
      return (int) (Screen.sp(textSize) / Screen.density() - .5f);
    }
    return textSize;
  }

  public float getTextSize () {
    return textSize;
  }

  public TextPaint getTextPaint () {
    return preparePaint(storage != null ? storage.getRegularPaint() : this.regularPaint);
  }

  public TextPaint getMonospacePaint () {
    return preparePaint(storage != null ? storage.getMonospaceStorage().getRegularPaint() : regularPaint);
  }

  public TextPaint getUnderlinePaint () {
    return preparePaint(storage != null ? storage.getUnderlineStorage().getRegularPaint() : regularPaint);
  }

  public TextPaint getStrikethroughPaint () {
    return preparePaint(storage != null ? storage.getStrikeThroughStorage().getRegularPaint() : regularPaint);
  }

  public TextPaint getBoldPaint () {
    return preparePaint(storage != null ? storage.getBoldPaint() : regularPaint);
  }

  public TextPaint getFakeBoldPaint () {
    return preparePaint(storage != null ? storage.getFakeBoldPaint() : regularPaint);
  }

  public TextPaint getItalicPaint () {
    return preparePaint(storage != null ? storage.getItalicPaint() : regularPaint);
  }

  public TextPaint getBoldItalicPaint () {
    return preparePaint(storage != null ? storage.getBoldItalicPaint() : regularPaint);
  }
}
