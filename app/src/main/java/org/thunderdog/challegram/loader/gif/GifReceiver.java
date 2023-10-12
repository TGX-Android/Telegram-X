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
 * File created on 29/02/2016 at 23:15
 */
package org.thunderdog.challegram.loader.gif;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.thunderdog.challegram.N;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.ReceiverUpdateListener;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import java.lang.ref.WeakReference;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.MathUtils;

public class GifReceiver implements GifWatcher, Runnable, Receiver {
  private static final int STATE_LOADED = 0x01;

  private static @Nullable Handler __handler;

  static Handler getHandler () {
    if (__handler == null) {
      synchronized (GifReceiver.class) {
        if (__handler == null) {
          __handler = new Handler(Looper.getMainLooper());
        }
      }
    }
    return __handler;
  }

  private final @Nullable View view;
  private ReceiverUpdateListener updateListener;
  private final GifWatcherReference reference;
  private int state;
  private GifState gif;

  private GifFile file, cachedFile;
  private boolean isDetached;
  private float progress;

  private float alpha = 1f;

  private final RectF progressRect;
  private Matrix bitmapMatrix;
  private final Matrix shaderMatrix;
  private final RectF bitmapRect;
  private final RectF drawRegion, croppedDrawRegion, croppedClipRegion;

  private final int progressOffset, progressRadius;

  public GifReceiver (@Nullable View view) {
    this.progressOffset = Screen.dp(1f);
    this.progressRadius = Screen.dp(10f);

    this.view = view;
    this.reference = new GifWatcherReference(this);
    this.bitmapRect = new RectF();
    this.shaderMatrix = new Matrix();
    this.drawRegion = new RectF();
    this.progressRect = new RectF();
    this.croppedDrawRegion = new RectF();
    this.croppedClipRegion = new RectF();
  }

  /** @noinspection unchecked*/
  @Override
  public final GifReceiver setUpdateListener (ReceiverUpdateListener listener) {
    this.updateListener = listener;
    return this;
  }

  private float radius;

  @Override
  public void setRadius (float radius) {
    if (this.radius != radius) {
      this.radius = radius;
      layoutRect();
      invalidate();
    }
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
    if (U.setRect(drawRegion, left, top, right, bottom)) {
      layoutRect();
      invalidate();
      return true;
    }
    return false;
  }

  @Override
  public void forceBoundsLayout () {
    layoutRect();
  }

  @Override
  public int getLeft () {
    return (int) drawRegion.left;
  }

  @Override
  public int getTop () {
    return (int) drawRegion.top;
  }

  @Override
  public int getRight () {
    return (int) drawRegion.right;
  }

  @Override
  public int getBottom () {
    return (int) drawRegion.bottom;
  }

  @Override
  public int getTargetWidth () {
    if (gif != null) {
      int sourceWidth = (gif.isRotated() ? gif.height() : gif.width());
      int sourceHeight = (gif.isRotated() ? gif.width() : gif.height());

      float widthRatio = (float) getWidth() / (float) sourceWidth;
      float heightRatio = (float) getHeight() / (float) sourceHeight;

      float ratio;
      if (file != null) {
        switch (file.getScaleType()) {
          case GifFile.CENTER_CROP:
            ratio = Math.max(widthRatio, heightRatio);
            break;
          case GifFile.FIT_CENTER:
            ratio = Math.min(widthRatio, heightRatio);
            break;
          default:
            return getWidth();
        }
      } else {
        ratio = Math.min(widthRatio, heightRatio);
      }
      sourceWidth *= ratio;
      sourceHeight *= ratio;
      return sourceWidth;
    }
    return getWidth();
  }

  @Override
  public int getTargetHeight () {
    if (gif != null) {
      int sourceWidth = (gif.isRotated() ? gif.height() : gif.width());
      int sourceHeight = (gif.isRotated() ? gif.width() : gif.height());

      float widthRatio = (float) getWidth() / (float) sourceWidth;
      float heightRatio = (float) getHeight() / (float) sourceHeight;

      float ratio;
      if (file != null) {
        switch (file.getScaleType()) {
          case GifFile.CENTER_CROP:
            ratio = Math.max(widthRatio, heightRatio);
            break;
          case GifFile.FIT_CENTER:
            ratio = Math.min(widthRatio, heightRatio);
            break;
          default:
            return getWidth();
        }
      } else {
        ratio = Math.min(widthRatio, heightRatio);
      }
      sourceWidth *= ratio;
      sourceHeight *= ratio;
      return sourceHeight;
    }
    return 0;
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
          int availWidth = (int) drawRegion.width();
          int availHeight = (int) drawRegion.height();

          if (file != null) {
            sourceWidth = availWidth;
            sourceHeight = availHeight;
          }

          float ratio = Math.min((float) availWidth / (float) sourceWidth, (float) availHeight / (float) sourceHeight);
          sourceWidth *= ratio;
          sourceHeight *= ratio;

          int centerX = (int) drawRegion.centerX();
          int centerY = (int) drawRegion.centerY();

          return x >= centerX - sourceWidth / 2f && x <= centerX + sourceWidth / 2f && y >= centerY - sourceHeight / 2f && y <= centerY + sourceHeight / 2f;
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
    clearShaderPaint();
  }

  @Override
  public void destroy () {
    clear();
  }

  // Loader stuff

  @AnyThread
  @Override
  public void gifLoaded (GifFile file, GifState gif) {
    getHandler().post(() -> {
      onLoad(file, gif);
    });
  }

  @AnyThread
  @Override
  public void gifProgress (GifFile file, float progress) {
    getHandler().post(() -> {
      onProgress(file, progress);
    });
  }

  @UiThread
  public void onLoad (GifFile file, GifState gif) {
    int fileId = this.file == null ? 0 : this.file.getFileId();
    if (file.getFileId() == fileId) {
      this.gif = gif;
      this.state = STATE_LOADED;
      layoutGif();
      invalidate();
    }
  }

  @UiThread
  @Override
  public void gifFrameChanged (GifFile file, boolean isRestart) {
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

  @UiThread
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
    rotationAnimationStart = SystemClock.uptimeMillis();
    if (view != null) {
      view.removeCallbacks(this);
      view.postDelayed(this, 16l);
    }
  }

  @Override
  public void run () {
    invalidateProgress();
    if (state != STATE_LOADED && file != null && view != null) {
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
      rotationAnimationStart = SystemClock.uptimeMillis();
    } else {
      ms = SystemClock.uptimeMillis();
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
    sweepAnimationStart = SystemClock.uptimeMillis();

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
    float input = (float) (SystemClock.uptimeMillis() - sweepAnimationStart) / (float) CHANGE_PROGRESS_DURATION;

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
    if (view != null) {
      view.invalidate((int) progressRect.left - progressOffset, (int) progressRect.top - progressOffset, (int) progressRect.right + progressOffset, (int) progressRect.bottom + progressOffset);
    }
    if (updateListener != null) {
      updateListener.onRequestInvalidate(this);
    }
  }

  @Override
  public void invalidate () {
    if (view != null) {
      view.invalidate(getLeft(), getTop(), getRight(), getBottom());
    }
    if (updateListener != null) {
      updateListener.onRequestInvalidate(this);
    }
  }

  // Private stuff

  private int cx, cy;

  private void layoutRect () {
    cx = (int) drawRegion.centerX();
    cy = (int) drawRegion.centerY();

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

    shaderMatrix.reset();
    int scaleType = file.getScaleType();
    if (scaleType != 0) {
      switch (scaleType) {
        case GifFile.FIT_CENTER:
        case GifFile.CENTER_CROP: {
          float bitmapWidth = bitmapRect.width();
          float bitmapHeight = bitmapRect.height();

          float targetWidth = drawRegion.width();
          float targetHeight = drawRegion.height();

          float centerX = drawRegion.centerX();
          float centerY = drawRegion.centerY();

          float scale = scaleType == GifFile.FIT_CENTER ?
            Math.min(targetWidth / bitmapWidth, targetHeight / bitmapHeight) :
            Math.max(targetWidth / bitmapWidth, targetHeight / bitmapHeight);

          bitmapWidth *= scale;
          bitmapHeight *= scale;
          croppedDrawRegion.set(
            centerX - bitmapWidth / 2f,
            centerY - bitmapHeight / 2f,
            centerX + bitmapWidth / 2f,
            centerY + bitmapHeight / 2f
          );
          shaderMatrix.setRectToRect(bitmapRect, croppedDrawRegion, Matrix.ScaleToFit.FILL);
          croppedClipRegion.set(
            centerX - Math.min(targetWidth, bitmapWidth) / 2f,
            centerY - Math.min(targetHeight, bitmapHeight) / 2f,
            centerX + Math.min(targetWidth, bitmapWidth) / 2f,
            centerY + Math.min(targetHeight, bitmapHeight) / 2f
          );

          break;
        }
        default:
          throw new UnsupportedOperationException(Integer.toString(scaleType));
      }
    } else {
      croppedDrawRegion.set(drawRegion);
      croppedClipRegion.set(drawRegion);
      shaderMatrix.setRectToRect(bitmapRect, drawRegion, Matrix.ScaleToFit.FILL);
    }
    if (lastShader != null) {
      lastShader.setLocalMatrix(shaderMatrix);
    }

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

          int viewWidth = getWidth();
          int viewHeight = getHeight();

          float scale = Math.min((float) viewWidth / (float) deltaWidth, (float) viewHeight / (float) deltaHeight);

          int futureWidth = (int) ((float) deltaWidth * scale);
          int futureHeight = (int) ((float) deltaHeight * scale);

          float dx, dy;
          if (gif.isRotated()) {
            dx = (viewWidth - futureHeight) / 2f;
            dy = (viewHeight - futureWidth) / 2f;
          } else {
            dx = (viewWidth - futureWidth) / 2f;
            dy = (viewHeight - futureHeight) / 2f;
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

          int viewWidth = getWidth();
          int viewHeight = getHeight();

          float scale = Math.max((float) viewWidth / (float) deltaWidth, (float) viewHeight / (float) deltaHeight);

          int futureWidth = (int) ((float) deltaWidth * scale);
          int futureHeight = (int) ((float) deltaHeight * scale);

          float dx, dy;
          if (gif.isRotated()) {
            dx = (viewWidth - futureHeight) / 2f;
            dy = (viewHeight - futureWidth) / 2f;
          } else {
            dx = (viewWidth - futureWidth) / 2f;
            dy = (viewHeight - futureHeight) / 2f;
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

  private static final int DRAW_BATCH_STARTED = 1;
  private static final int DRAW_BATCH_DRAWN = 1 << 1;

  private int drawBatchFlags;

  public void beginDrawBatch () {
    // Use when drawing the same GifReceiver multiple times on one canvas
    drawBatchFlags = DRAW_BATCH_STARTED;
  }

  public void finishDrawBatch () {
    int flags = drawBatchFlags;
    if (gif != null && BitwiseUtils.hasFlag(flags, DRAW_BATCH_STARTED) && BitwiseUtils.hasFlag(flags, DRAW_BATCH_DRAWN)) {
      synchronized (gif.getBusyList()) {
        if (gif.hasBitmap()) {
          gif.getDrawFrame(true);
        }
      }
    }
    this.drawBatchFlags = 0;
  }

  public void draw (Canvas c) {
    if (file == null) {
      return;
    }

    if (gif != null) {
      boolean isFirstFrame = false;
      synchronized (gif.getBusyList()) {
        if (gif.hasBitmap()) {
          final boolean inBatch = BitwiseUtils.hasFlag(drawBatchFlags, DRAW_BATCH_STARTED);
          if (!inBatch || !BitwiseUtils.hasFlag(drawBatchFlags, DRAW_BATCH_DRAWN)) {
            gif.applyNext();
            if (inBatch) {
              drawBatchFlags |= DRAW_BATCH_DRAWN;
            }
          }
          final int alpha = (int) (255f * MathUtils.clamp(this.alpha));
          Paint bitmapPaint = Paints.getBitmapPaint();
          int restoreAlpha = bitmapPaint.getAlpha();
          if (alpha != restoreAlpha) {
            bitmapPaint.setAlpha(alpha);
          }
          int scaleType = file.getScaleType();
          GifState.Frame frame = gif.getDrawFrame(!inBatch);
          if (radius != 0) {
            boolean clip = scaleType != 0;
            int restoreToCount;
            if (clip) {
              restoreToCount = Views.save(c);
              c.clipRect(croppedClipRegion);
            } else {
              restoreToCount = -1;
            }
            c.drawRoundRect(croppedClipRegion, radius, radius, shaderPaint(frame.bitmap, bitmapPaint.getAlpha()));
            if (clip) {
              Views.restore(c, restoreToCount);
            }
          } else if (scaleType != 0) {
            c.save();
            c.clipRect(drawRegion);

            if (drawRegion.left != 0 || drawRegion.top != 0) {
              c.translate(drawRegion.left, drawRegion.top);
            }

            int rotation = gif.getRotation();
            if (rotation != 0) {
              int width = getWidth();
              int height = getHeight();
              /*if (scaleType == GifFile.CENTER_CROP) {
                float scale = (float) width / (float) height;
                c.scale(scale, scale, width / 2, height / 2);
              }*/
              c.rotate(rotation, width / 2f, height / 2f);
            }

            c.concat(bitmapMatrix);
            c.drawBitmap(frame.bitmap, 0f, 0f, bitmapPaint);

            c.restore();
          } else {
            Rect rect = Paints.getRect();
            rect.set((int) bitmapRect.left, (int) bitmapRect.top, (int) bitmapRect.right, (int) bitmapRect.bottom);
            c.drawBitmap(frame.bitmap, rect, drawRegion, bitmapPaint);
          }
          if (alpha != restoreAlpha) {
            bitmapPaint.setAlpha(restoreAlpha);
          }
          isFirstFrame = frame.no == 0;
        }
      }
      if (isFirstFrame) {
        GifActor.onGifRestarted(file);
      }
    }
  }


  private Paint shaderPaint;
  private WeakReference<Bitmap> lastShaderBitmapReference;
  private BitmapShader lastShader;

  private Paint shaderPaint (Bitmap bitmap, int alpha) {
    if (shaderPaint == null) {
      shaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    }
    Bitmap lastShaderBitmap = lastShaderBitmapReference != null ? lastShaderBitmapReference.get() : null;
    if (lastShaderBitmap != bitmap) {
      lastShaderBitmapReference = new WeakReference<>(bitmap);
      BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
      shader.setLocalMatrix(shaderMatrix);
      shaderPaint.setShader(lastShader = shader);
    }
    shaderPaint.setAlpha(alpha);
    return shaderPaint;
  }

  private void clearShaderPaint () {
    lastShaderBitmapReference = null;
    shaderPaint = null;
    lastShader = null;
  }
}
