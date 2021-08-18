package org.thunderdog.challegram.component.chat;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

/**
 * Date: 04/04/2017
 * Author: default
 */

public class EncryptionKeyDrawable extends Drawable {
  private static final int[] colors = {
    0xffffffff,
    0xffd5e6f3,
    0xff2d5775,
    0xff2f99c9
  };

  public EncryptionKeyDrawable (byte[] data) {
    this.data = data;
  }

  private byte[] data;

  private int getBits (int bitOffset) {
    return (data[bitOffset / 8] >> (bitOffset % 8)) & 0x3;
  }

  public void setData (byte[] data) {
    this.data = data;
    invalidateSelf();
  }

  /*public void setColors (int[] value) {
    if (colors.length != 4) {
      throw new IllegalArgumentException("colors must have length of 4");
    }
    colors = value;
    invalidateSelf();
  }*/

  @Override
  public void draw (@NonNull Canvas c) {
    if (data == null) {
      return;
    }

    if (data.length == 16) {
      int bitPointer = 0;
      float rectSize = (float) Math.floor(Math.min(getBounds().width(), getBounds().height()) / 8.0f);
      float xOffset = Math.max(0, (getBounds().width() - rectSize * 8) / 2);
      float yOffset = Math.max(0, (getBounds().height() - rectSize * 8) / 2);
      for (int iy = 0; iy < 8; iy++) {
        for (int ix = 0; ix < 8; ix++) {
          int byteValue = getBits(bitPointer);
          bitPointer += 2;
          int colorIndex = Math.abs(byteValue) % colors.length;
          c.drawRect(xOffset + ix * rectSize, iy * rectSize + yOffset, xOffset + ix * rectSize + rectSize, iy * rectSize + rectSize + yOffset, Paints.fillingPaint(colors[colorIndex]));
        }
      }
    } else {
      int bitPointer = 0;
      float rectSize = (float) Math.floor(Math.min(getBounds().width(), getBounds().height()) / 12.0f);
      float xOffset = Math.max(0, (getBounds().width() - rectSize * 12) / 2);
      float yOffset = Math.max(0, (getBounds().height() - rectSize * 12) / 2);
      for (int iy = 0; iy < 12; iy++) {
        for (int ix = 0; ix < 12; ix++) {
          int byteValue = getBits(bitPointer);
          int colorIndex = Math.abs(byteValue) % colors.length;
          c.drawRect(xOffset + ix * rectSize, iy * rectSize + yOffset, xOffset + ix * rectSize + rectSize, iy * rectSize + rectSize + yOffset, Paints.fillingPaint(colors[colorIndex]));
          bitPointer += 2;
        }
      }
    }
  }

  @Override
  public void setAlpha (int alpha) { }

  @Override
  public void setColorFilter (ColorFilter cf) { }

  @Override
  public int getOpacity () {
    return PixelFormat.UNKNOWN;
  }

  @Override
  public int getIntrinsicWidth () {
    return Screen.dp(32f);
  }

  @Override
  public int getIntrinsicHeight () {
    return Screen.dp(32f);
  }
}