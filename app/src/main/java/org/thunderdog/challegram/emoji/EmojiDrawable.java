package org.thunderdog.challegram.emoji;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.tool.Screen;

/**
 * Date: 2019-05-04
 * Author: default
 */
public class EmojiDrawable extends Drawable {
  private final EmojiInfo info;

  public EmojiDrawable (EmojiInfo info) {
    this.info = info;
  }

  @Override
  public void setAlpha (int alpha) { }

  @Override
  public void setColorFilter (ColorFilter colorFilter) { }

  @Override
  public int getOpacity () {
    return PixelFormat.TRANSPARENT;
  }

  @Override
  public void draw (@NonNull Canvas c) {
    Emoji.instance().draw(c, info, getBounds());
  }

  @Override
  public int getIntrinsicWidth () {
    return Screen.dp(24f);
  }

  @Override
  public int getIntrinsicHeight () {
    return Screen.dp(24f);
  }
}
