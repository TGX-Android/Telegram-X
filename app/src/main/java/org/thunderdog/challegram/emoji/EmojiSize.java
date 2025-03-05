package org.thunderdog.challegram.emoji;

import android.graphics.Paint;

public class EmojiSize {
  private final Paint.FontMetricsInt mTmpFontMetrics = new Paint.FontMetricsInt();
  private int mSize = -1;

  public boolean isInitialized () {
    return mSize != -1;
  }

  public void initialize (Paint paint, Paint.FontMetricsInt fm, boolean optional) {
    if (optional && isInitialized()) {
      return;
    }

    paint.getFontMetricsInt(mTmpFontMetrics);
    mSize = Math.abs(mTmpFontMetrics.descent - mTmpFontMetrics.ascent);

    if (fm != null) {
      fm.ascent = mTmpFontMetrics.ascent;
      fm.descent = mTmpFontMetrics.descent;
      fm.top = mTmpFontMetrics.top;
      fm.bottom = mTmpFontMetrics.bottom;
    }
  }

  public int getSize () {
    return mSize;
  }
}
