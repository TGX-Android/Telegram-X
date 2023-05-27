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
 * File created on 02/03/2016 at 13:32
 */
package org.thunderdog.challegram.component.chat;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;

public class Waveform {
  public static final int MODE_BITMAP = 0;
  public static final int MODE_RECT = 1;

  private static final int PLACEHOLDER_SIZE = 50;

  private static float minimumHeight;
  private static float maxHeightDiff;
  private static int width, spacing, radius;
  private Paint paint;
  private static RectF rect;

  private float expandFactor = 1f;
  private byte[] data;
  private Chunk[] chunks;
  private int maxSample;
  private int currentWidth;
  private Bitmap bitmap;

  private int mode;
  private boolean isOutBubble;

  public Waveform (byte[] data, int mode, boolean isOutBubble) {
    if (minimumHeight == 0) {
      minimumHeight = Screen.dpf(1.5f);
      maxHeightDiff = Screen.dpf(7f);
      width = Screen.dp(3f);
      spacing = Screen.dp(1f);
      radius = Screen.dp(1f);
    }
    if (rect == null) {
      rect = new RectF();
    }
    paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    paint.setStyle(Paint.Style.FILL);

    this.mode = mode;
    this.isOutBubble = isOutBubble;

    if (data == null || data.length == 0) {
      this.data = new byte[PLACEHOLDER_SIZE];
      return;
    }

    this.data = data;
    calculateMaxSample();
  }

  public int getWidth () {
    return currentWidth;
  }

  public int getMaxSample () {
    return maxSample;
  }

  public void setData (byte[] data) {
    if (data == null) {
      this.data = new byte[PLACEHOLDER_SIZE];
      this.maxSample = 0;
    } else {
      this.data = data;
      calculateMaxSample();
    }
    expandFactor = 0f;
    if (lastTotalWidth != 0) {
      layout(lastTotalWidth, true);
    }
  }

  private void calculateMaxSample () {
    maxSample = 0;
    for (byte b : data) {
      int i = b < 0 ? -b : b;
      if (i > maxSample) {
        maxSample = i;
      }
    }
  }

  public float getExpand () {
    return expandFactor;
  }

  public void setExpand (float expand) {
    this.expandFactor = expand;
  }

  private int lastTotalWidth;

  public void layout (int totalWidth) {
    layout(totalWidth, false);
  }

  private void layout (int totalWidth, boolean force) {
    if (totalWidth <= 0) {
      return;
    }

    int numSamples = (int) ((float) totalWidth / (width + spacing));

    if (chunks != null && chunks.length == numSamples && !force) {
      return;
    }

    lastTotalWidth = totalWidth;
    currentWidth = numSamples * (width + spacing) - spacing;

    int available;
    if (chunks == null) {
      available = 0;
      chunks = new Chunk[numSamples];
    } else {
      available = Math.min(chunks.length, numSamples);
      Chunk[] newChunks = new Chunk[numSamples];
      System.arraycopy(chunks, 0, newChunks, 0, available);
      chunks = newChunks;
    }

    int currentHeight = (int) (minimumHeight + maxHeightDiff) * 2 + Screen.dp(10f);

    Canvas inactiveCanvas;
    if (mode == MODE_BITMAP) {
      if (bitmap == null || bitmap.getWidth() != currentWidth || bitmap.getHeight() != currentHeight) {
        if (bitmap != null) {
          bitmap.recycle();
          bitmap = null;
        }
        bitmap = Bitmap.createBitmap(currentWidth, currentHeight, Bitmap.Config.ARGB_8888);
        inactiveCanvas = new Canvas(bitmap);
      } else {
        bitmap.eraseColor(0);
        inactiveCanvas = new Canvas(bitmap);
      }
    } else {
      inactiveCanvas = null;
    }

    int[] adjustedSamples = new int[numSamples];
    scale(data, adjustedSamples);
    if (mode == MODE_BITMAP) {
      paint.setColor(Theme.getColor(isOutBubble ? ColorId.bubbleOut_waveformInactive : ColorId.waveformInactive));
    }
    int cx = 0;
    int centerY = (int) ((float) currentHeight * .5f);
    for (int i = 0; i < numSamples; i++) {
      int peakSample = adjustedSamples[i];
      float heightDiff = maxSample == 0 ? 0f : maxHeightDiff * ((float) (peakSample) / (float) (maxSample));
      Chunk chunk;
      if (i >= available) {
        chunks[i] = chunk = new Chunk(heightDiff);
      } else {
        chunk = chunks[i];
        chunk.heightDiff = heightDiff;
      }
      if (inactiveCanvas != null) {
        chunk.draw(inactiveCanvas, cx, centerY, paint);
        cx += width + spacing;
      }
    }

    U.recycle(inactiveCanvas);
  }

  private static void scale (byte[] data, int[] output) {
    for (int i = 0; i < data.length; i++) {
      int index = i * output.length / data.length;
      int sample = data[i];
      if (sample < 0) {
        sample = -sample;
      }
      if (output[index] < sample) {
        output[index] = sample;
      }
    }
  }

  public void destroy () {
    if (bitmap != null && !bitmap.isRecycled()) {
      bitmap.recycle();
    }
  }

  public void draw (Canvas c, float progress, int startX, int centerY) {
    switch (mode) {
      case MODE_BITMAP: {
        if (chunks == null || bitmap == null || bitmap.isRecycled()) {
          break;
        }
        int color = Theme.getColor(isOutBubble ? ColorId.bubbleOut_waveformInactive : ColorId.waveformInactive);
        if (paint.getColor() != color) {
          paint.setColorFilter(Paints.createColorFilter(color));
          paint.setColor(color);
        }
        int topY = centerY - (int) ((float) bitmap.getHeight() * .5f);
        if (progress == 0f) {
          c.drawBitmap(bitmap, startX, topY, paint);
          break;
        }
        if (progress == 1f) {
          int colorId = isOutBubble ? ColorId.bubbleOut_waveformActive : ColorId.waveformActive;
          c.drawBitmap(bitmap, startX, topY, PorterDuffPaint.get(colorId));
          break;
        }
        float endX = progress * (float) currentWidth;
        c.save();
        c.clipRect(startX, topY, startX + endX, topY + bitmap.getHeight());
        int colorId = isOutBubble ? ColorId.bubbleOut_waveformActive : ColorId.waveformActive;
        c.drawBitmap(bitmap, startX, topY, PorterDuffPaint.get(colorId));
        c.restore();
        c.save();
        c.clipRect(startX + endX, topY, startX + bitmap.getWidth(), topY + bitmap.getHeight());
        c.drawBitmap(bitmap, startX, topY, paint);
        c.restore();
        break;
      }
      case MODE_RECT: {
        if (chunks == null) {
          break;
        }
        int cx = startX;
        if (progress == 0f || progress == 1f) {
          paint.setColor(Theme.getColor(progress == 0f ? (isOutBubble ? ColorId.bubbleOut_waveformInactive : ColorId.waveformInactive) : (isOutBubble ? ColorId.bubbleOut_waveformActive : ColorId.waveformActive)));
          for (Chunk chunk : chunks) {
            chunk.draw(c, cx, centerY, expandFactor, paint);
            cx += width + spacing;
          }
          break;
        }
        int bound = maxHeight() * 2;
        int topY = centerY - bound;
        int bottomY = centerY + bound;
        float endX = startX + progress * (float) currentWidth;
        c.save();
        c.clipRect(startX, topY, endX, bottomY);
        paint.setColor(Theme.getColor(isOutBubble ? ColorId.bubbleOut_waveformActive : ColorId.waveformActive));
        int i = 0;
        for (Chunk chunk : chunks) {
          chunk.draw(c, cx, centerY, expandFactor, paint);
          cx += width + spacing;
          if (cx > endX) {
            cx -= width + spacing;
            break;
          }
          i++;
        }
        c.restore();
        c.save();
        c.clipRect(endX - 1, topY, startX + currentWidth, bottomY);
        paint.setColor(Theme.getColor(isOutBubble ? ColorId.bubbleOut_waveformInactive : ColorId.waveformInactive));
        for (; i < chunks.length; i++) {
          Chunk chunk = chunks[i];
          chunk.draw(c, cx, centerY, expandFactor, paint);
          cx += width + spacing;
        }
        c.restore();
        break;
      }
    }
  }

  public int getHeight () {
    return bitmap == null ? maxHeight() : bitmap.getHeight();
  }

  public static int maxHeight () {
    return (int) ((minimumHeight + maxHeightDiff) * 2f);
  }

  private static class Chunk {
    public float heightDiff;

    public Chunk (float heightDiff) {
      this.heightDiff = heightDiff;
    }

    public void draw (Canvas c, int startX, float centerY, Paint paint) {
      float height = minimumHeight + heightDiff;
      rect.left = startX;
      rect.top = centerY - height;
      rect.bottom = centerY + height;
      rect.right = startX + width;
      c.drawRoundRect(rect, radius, radius, paint);
    }

    public void draw (Canvas c, int startX, float centerY, float expandFactor, Paint paint) {
      float height = minimumHeight + heightDiff * (expandFactor < 0f ? 0f : expandFactor);
      rect.left = startX;
      rect.top = centerY - height;
      rect.bottom = centerY + height;
      rect.right = startX + width;
      c.drawRoundRect(rect, radius, radius, paint);
    }
  }
}
