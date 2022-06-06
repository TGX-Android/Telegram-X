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
 * File created on 29/02/2016 at 23:15
 */
package org.thunderdog.challegram.loader.gif;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.N;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.MathUtils;

public class GifReceiver implements GifWatcher, Runnable, Receiver {
  private static final int STATE_LOADED = 0x01;

  private static @Nullable GifHandler __handler;

  public static GifHandler getHandler () {
    if (__handler == null) {
      synchronized (GifReceiver.class) {
        if (__handler == null) {
          __handler = new GifHandler();
        }
      }
    }
    return __handler;
  }

  private View view;
  private GifWatcherReference reference;
  private int state;
  private GifState gif;

  private GifFile file, cachedFile;
  private boolean isDetached;
  private float progress;

  private int left, top, right, bottom;
  private float alpha = 1f;

  private RectF progressRect;
  private Matrix bitmapMatrix;
  private Rect bitmapRect;
  private Rect drawRegion;

  private final int progressOffset, progressRadius;

  public GifReceiver (View view) {
    this.progressOffset = Screen.dp(1f);
    this.progressRadius = Screen.dp(10f);

    this.view = view;
    this.reference = new GifWatcherReference(this);
    this.bitmapRect = new Rect();
    this.drawRegion = new Rect();
    this.progressRect = new RectF();
  }

  @Override
  public void setRadius (int radius) {
    // TODO
  }

  private Object tag;
  @Override
  public void setTag (Object tag) {
    this.tag = tag;
  }
  @Override
  public Object getTag () {
    return tag;
  }

  @Override
  public View getTargetView () {
    return view;
  }

  @Override
  public View findTargetView (GifFile file) {
    return this.file != null && this.file.getFileId() == file.getFileId() ? view : null;
  }

  // Getters/Setters

  public boolean setBounds (int left, int top, int right, int bottom) {
    if (this.left != left || this.top != top || this.right != right || this.bottom != bottom) {
      this.left = left;
      this.top = top;
      this.right = right;
      this.bottom = bottom;
      this.drawRegion.set(left, top, right, bottom);
      layoutRect();
      return true;
    }
    return false;
  }

  @Override
  public void forceBoundsLayout () {
    layoutRect();
  }

  public int getLeft () {
    return left;
  }

  public int getTop () {
    return top;
  }

  public int getRight () {
    return right;
  }

  public int getBottom () {
    return bottom;
  }

  public int centerX () {
    return (int) ((float) (left + right) * .5f);
  }

  public int centerY () {
    return (int) ((float) (bottom + top) * .5f);
  }

  @Override
  public int getWidth () {
    return getRight() - getLeft();
  }

  @Override
  public int getHeight () {
    return getBottom() - getTop();
  }

  @Override
  public int getTargetWidth () {
    return gif != null ? (gif.isRotated() ? gif.height() : gif.width()) : 0;
  }

  @Override
  public int getTargetHeight () {
    return gif != null ? (gif.isRotated() ? gif.width() : gif.height()) : 0;
  }

  // ImageReceiver

  private float savedAlpha;

  @Override
  public float getPaintAlpha () {
    return alpha;
  }

  @Override
  public void setPaintAlpha (float alpha) {
    this.savedAlpha = this.alpha;
    this.alpha = alpha;
  }

  @Override
  public void restorePaintAlpha () {
    this.alpha = this.savedAlpha;
    this.savedAlpha = 0f;
  }

  @Override
  public void setAlpha (float alpha) {
    if (this.alpha != alpha) {
      this.alpha = alpha;
      invalidate();
    }
  }

  @Override
  public float getAlpha () {
    return this.alpha;
  }

  @Override
  public void setAnimationDisabled (boolean disabled) {
    // TODO
  }

  @Override
  public boolean isInsideContent (float x, float y, int emptyWidth, int emptyHeight) {
    if (file != null || (emptyWidth != 0 && emptyHeight != 0)) {
      int sourceWidth = emptyWidth;
      int sourceHeight = emptyHeight;
      int scaleType = file != null ? file.getScaleType() : ImageFile.FIT_CENTER;
      switch (scaleType) {
        case ImageFile.FIT_CENTER: {
          int availWidth = right - left;
          int availHeight = bottom - top;

          if (file != null) {
            sourceWidth = availWidth;
            sourceHeight = availHeight;
          }

          float ratio = Math.min((float) availWidth / (float) sourceWidth, (float) availHeight / (float) sourceHeight);
          sourceWidth *= ratio;
          sourceHeight *= ratio;

          int centerX = (left + right) / 2;
          int centerY = (top + bottom) / 2;

          return x >= centerX - sourceWidth / 2 && x <= centerX + sourceWidth / 2 && y >= centerY - sourceHeight / 2 && y <= centerY + sourceHeight / 2;
        }
        case ImageFile.CENTER_CROP:
        default: {
          break;
        }
      }
    }
    return false;
  }


  // Interacts

  public GifFile getCurrentFile () {
    return isDetached ? cachedFile : file;
  }

  @Override
  public boolean isEmpty () {
    return getCurrentFile() == null;
  }

  public void requestFile (GifFile file) {
    if (isDetached) {
      cachedFile = file;
      return;
    }

    int fileId1 = this.file == null ? 0 : this.file.getFileId();
    int fileId2 = file == null ? 0 : file.getFileId();

    if (fileId1 != fileId2) {
      if (fileId1 != 0) {
        GifBridge.instance().removeWatcher(reference);
      }
      this.file = file;
      this.state = file == null || file instanceof GifFileRemote || !TD.isFileLoaded(file.getFile()) ? 0 : STATE_LOADED;
      this.gif = null;
      if (fileId2 != 0) {
        if (state != STATE_LOADED) {
          startRotation();
        }
        GifBridge.instance().requestFile(file, reference);
      } else {
        invalidate();
      }
    }
  }

  public boolean needPlaceholder () {
    return state != STATE_LOADED || gif == null || !gif.hasBitmap();
  }

  @Override
  public void attach () {
    if (isDetached) {
      isDetached = false;
      if (this.cachedFile != null) {
        requestFile(this.cachedFile);
        this.cachedFile = null;
      }
    }
  }

  @Override
  public void detach () {
    if (!isDetached) {
      isDetached = true;
      if (this.file != null) {
        this.cachedFile = this.file;
        isDetached = false;
        requestFile(null);
        isDetached = true;
      }
    }
  }

  @Override
  public void clear () {
    requestFile(null);
  }

  @Override
  public void destroy () {
    clear();
  }

  // Loader stuff

  @Override
  public void gifLoaded (GifFile file, GifState gif) {
    getHandler().onLoad(this, file, gif);
  }

  @Override
  public void gifProgress (GifFile file, float progress) {
    getHandler().onProgress(this, file, progress);
  }

  public void onLoad (GifFile file, GifState gif) {
    int fileId = this.file == null ? 0 : this.file.getFileId();
    if (file.getFileId() == fileId) {
      this.gif = gif;
      this.state = STATE_LOADED;
      layoutGif();
      invalidate();
    }
  }

  @Override
  public void gifFrameChanged (GifFile file) {
    int fileId = this.file == null ? 0 : this.file.getFileId();
    if (file.getFileId() == fileId) {
      invalidate();
    }
  }

  // Rotation and sweep animation stuff

  private static final float START_ANGLE = 10f;
  private static final float END_ANGLE = 350f;
  private static final long CHANGE_PROGRESS_DURATION = 140l;
  private static final long ROTATION_DURATION = 2400l;

  public void onProgress (GifFile file, float progress) {
    int fileId = this.file == null ? 0 : this.file.getFileId();
    if (file.getFileId() == fileId) {
      float previousProgress = this.progress;
      this.progress = progress;
      startSweepAnimation(previousProgress);
    }
  }

  private float rotation;
  private long rotationAnimationStart;

  private void startRotation () {
    progress = 0f;
    sweepAnimationStart = 0l;
    sweep = sweepFactor = sweepDiff = sweepStart = 0f;
    rotationAnimationStart = System.currentTimeMillis();
    view.removeCallbacks(this);
    view.postDelayed(this, 16l);
  }

  @Override
  public void run () {
    invalidateProgress();
    if (state != STATE_LOADED && file != null) {
      view.postDelayed(this, 16l);
    }
  }

  private void drawProgress (Canvas c, float startAngle, float sweepAngle) {
    Paint progressPaint = Paints.getProgressPaint(0xffffffff, Screen.dp(2f));
    if (rotation == 0f) {
      c.drawArc(progressRect, startAngle, Math.max(sweepAngle, 12f), false, progressPaint);
    } else {
      c.drawArc(progressRect, startAngle + (360f * rotation), Math.max(sweepAngle, 12f), false, progressPaint);
    }
  }

  private void onRotateFrame () {
    long ms = 0l;

    if (rotationAnimationStart == 0l) {
      rotation = 0f;
      rotationAnimationStart = System.currentTimeMillis();
    } else {
      ms = System.currentTimeMillis();
      rotation = (float) (ms - rotationAnimationStart) / (float) ROTATION_DURATION;
    }

    if (rotation >= 1f) {
      rotationAnimationStart = ms;
      rotation = rotation - 1f;
    }
  }

  private float sweep, sweepStart, sweepDiff, sweepFactor;
  private long sweepAnimationStart;

  private void startSweepAnimation (float fromProgress) {
    sweepAnimationStart = System.currentTimeMillis();

    if (sweep == 0f) {
      sweep = fromProgress;
      sweepStart = fromProgress;
    } else {
      sweepStart = sweep;
    }

    sweepFactor = 0f;
    sweepDiff = progress - sweepStart;

    invalidateProgress();
  }

  private void onSweepFrame () {
    float input = (float) (System.currentTimeMillis() - sweepAnimationStart) / (float) CHANGE_PROGRESS_DURATION;

    if (input <= 0f) {
      sweepFactor = 0f;
    } else if (input >= 1f) {
      sweepFactor = 1f;
    } else {
      sweepFactor = N.iimg(input);
    }

    sweep = sweepStart + sweepDiff * sweepFactor;
  }

  private void invalidateProgress () {
    view.invalidate((int) progressRect.left - progressOffset, (int) progressRect.top - progressOffset, (int) progressRect.right + progressOffset, (int) progressRect.bottom + progressOffset);
  }

  @Override
  public void invalidate () {
    view.invalidate(left, top, right, bottom);
  }

  // Private stuff

  private int cx, cy;

  private void layoutRect () {
    cx = (int) ((float) (left + right) * .5f);
    cy = (int) ((float) (top + bottom) * .5f);

    progressRect.left = cx - progressRadius + progressOffset;
    progressRect.right = cx + progressRadius - progressOffset;
    progressRect.top = cy - progressRadius + progressOffset;
    progressRect.bottom = cy + progressRadius - progressOffset;

    layoutGif();
  }

  private void layoutGif () {
    if (gif == null || file == null) {
      return;
    }

    if (bitmapMatrix != null) {
      bitmapMatrix.reset();
    }

    bitmapRect.right = gif.width();
    bitmapRect.bottom = gif.height();

    if (file != null) {
      switch (file.getScaleType()) {
        case GifFile.FIT_CENTER: {
          if (bitmapMatrix == null) {
            bitmapMatrix = new Matrix();
          }

          int deltaWidth, deltaHeight;

          if (gif.isRotated()) {
            deltaWidth = gif.height();
            deltaHeight = gif.width();
          } else {
            deltaWidth = gif.width();
            deltaHeight = gif.height();
          }

          int viewWidth = drawRegion.width();
          int viewHeight = drawRegion.height();

          float scale = Math.min((float) viewWidth / (float) deltaWidth, (float) viewHeight / (float) deltaHeight);

          int futureWidth = (int) ((float) deltaWidth * scale);
          int futureHeight = (int) ((float) deltaHeight * scale);

          float dx, dy;
          if (gif.isRotated()) {
            dx = (viewWidth - futureHeight) / 2;
            dy = (viewHeight - futureWidth) / 2;
          } else {
            dx = (viewWidth - futureWidth) / 2;
            dy = (viewHeight - futureHeight) / 2;
          }

          bitmapMatrix.setScale(scale, scale);
          bitmapMatrix.postTranslate((int) (dx + 0.5f), (int) (dy + 0.5f));

          break;
        }
        case GifFile.CENTER_CROP: {
          if (bitmapMatrix == null) {
            bitmapMatrix = new Matrix();
          }

          final int deltaWidth, deltaHeight;
          if (gif.isRotated()) {
            deltaWidth = gif.height();
            deltaHeight = gif.width();
          } else {
            deltaWidth = gif.width();
            deltaHeight = gif.height();
          }

          int viewWidth = drawRegion.width();
          int viewHeight = drawRegion.height();

          float scale = Math.max((float) viewWidth / (float) deltaWidth, (float) viewHeight / (float) deltaHeight);

          int futureWidth = (int) ((float) deltaWidth * scale);
          int futureHeight = (int) ((float) deltaHeight * scale);

          float dx, dy;
          if (gif.isRotated()) {
            dx = (viewWidth - futureHeight) / 2;
            dy = (viewHeight - futureWidth) / 2;
          } else {
            dx = (viewWidth - futureWidth) / 2;
            dy = (viewHeight - futureHeight) / 2;
          }

          bitmapMatrix.setScale(scale, scale);
          bitmapMatrix.postTranslate((int) (dx), (int) (dy));

          break;
        }
      }
    }
  }

  // Drawing

  @Override
  public void drawPlaceholder (Canvas c) {
    if (state != STATE_LOADED) {
      c.drawCircle(cx, cy, progressRadius, Paints.getPlaceholderPaint());

      onRotateFrame();

      if (sweepAnimationStart != 0l) {
        onSweepFrame();

        if (sweepFactor == 1f) {
          sweepAnimationStart = 0l;
          sweep = 0f;
          drawProgress(c, -100f, START_ANGLE + progress * END_ANGLE);
        } else {
          drawProgress(c, -100f, START_ANGLE + sweep * END_ANGLE);
        }
      } else {
        drawProgress(c, -100f, START_ANGLE + progress * END_ANGLE);
      }
    }
  }

  public void draw (Canvas c) {
    if (file == null) {
      return;
    }

    if (gif != null) {
      synchronized (gif.getBusyList()) {
        if (gif.hasBitmap()) {
          gif.applyNext();
          final int alpha = (int) (255f * MathUtils.clamp(this.alpha));
          Paint bitmapPaint = Paints.getBitmapPaint();
          int restoreAlpha = bitmapPaint.getAlpha();
          if (alpha != restoreAlpha) {
            bitmapPaint.setAlpha(alpha);
          }
          int scaleType = file.getScaleType();
          if (scaleType != 0) {
            c.save();
            c.clipRect(left, top, right, bottom);

            if (left != 0 || top != 0) {
              c.translate(left, top);
            }

            int rotation = gif.getRotation();
            if (rotation != 0) {
              int width = right - left;
              int height = bottom - top;
              /*if (scaleType == GifFile.CENTER_CROP) {
                float scale = (float) width / (float) height;
                c.scale(scale, scale, width / 2, height / 2);
              }*/
              c.rotate(rotation, width / 2, height / 2);
            }

            c.concat(bitmapMatrix);
            c.drawBitmap(gif.getBitmap(true), 0f, 0f, bitmapPaint);

            c.restore();
          } else {
            c.drawBitmap(gif.getBitmap(true), bitmapRect, drawRegion, bitmapPaint);
          }
          if (alpha != restoreAlpha) {
            bitmapPaint.setAlpha(restoreAlpha);
          }
        }
      }
    }
  }
}
