package org.thunderdog.challegram.util.text;

import android.text.TextPaint;

import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;

/**
 * Date: 23/02/2017
 * Author: default
 */

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
