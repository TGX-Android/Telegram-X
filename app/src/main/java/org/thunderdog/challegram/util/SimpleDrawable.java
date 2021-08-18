package org.thunderdog.challegram.util;

import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

/**
 * Date: 2019-12-21
 * Author: default
 */
public abstract class SimpleDrawable extends Drawable {
  @Override
  public void setAlpha (int alpha) {

  }

  @Override
  public void setColorFilter (@Nullable ColorFilter colorFilter) {

  }

  @Override
  public int getOpacity () {
    return PixelFormat.UNKNOWN;
  }
}
