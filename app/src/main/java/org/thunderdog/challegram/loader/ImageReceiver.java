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
 * File created on 06/05/2015 at 12:31
 */
package org.thunderdog.challegram.loader;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;

import androidx.annotation.FloatRange;
import androidx.annotation.Nullable;

import org.drinkmore.Tracer;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.mediaview.crop.CropState;
import org.thunderdog.challegram.mediaview.paint.PaintState;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;

public class ImageReceiver implements Watcher, ValueAnimator.AnimatorUpdateListener, Receiver, ImageFile.CropStateChangeListener {
  private static boolean ANIMATION_ENABLED;

  private static ImageHandler handler;

  private ImageFile file, cachedFile;
  private final WatcherReference reference;

  private final @Nullable View view;
  private ReceiverUpdateListener updateListener;
  private Bitmap bitmap;
  private float alpha = 1f, progress;

  private boolean isDetached, needProgress, animationDisabled;
  private float radius;

  private int left, top, right, bottom;

  private final Rect drawRegion, bitmapRect;

  private final Paint bitmapPaint;
  private Matrix bitmapMatrix;

  private Paint roundPaint; // rounded corners
  private Paint repeatPaint; // repeat mode
  private BitmapShader bitmapShader;
  private RectF roundRect;
  private RectF bitmapRectF;
  private Matrix shaderMatrix;

  private CropState displayCrop;
  private Rect croppedRect;
  private float cropApplyFactor = 1.0f;

  private OnCompleteListener completeListener;

  public ImageReceiver (View view, int radius) {
    if (handler == null) {
      handler = new ImageHandler();
      float density = UI.getResources().getDisplayMetrics().density;
      ANIMATION_ENABLED = density >= 2.0f; //(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && density >= 2f) || (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP && density > 2f);
    }
    this.bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    this.view = view;
    this.reference = new WatcherReference(this);
    this.drawRegion = new Rect();
    this.bitmapRect = new Rect();

    if (radius != 0) {
      setRadius(radius);
    }
  }

  @Override
  public void setUpdateListener (ReceiverUpdateListener listener) {
    this.updateListener = listener;
  }

  @Override
  public void invalidate () {
    if (view != null) {
      /*if (drawRegion.isEmpty()) {
        view.invalidate();
      }*/
      view.invalidate(drawRegion.left, drawRegion.top, drawRegion.right, drawRegion.bottom);
    }
    if (updateListener != null) {
      updateListener.onRequestInvalidate(this);
    }
  }

  public void invalidateFully () {
    if (view != null) {
      view.invalidate();
    }
    if (updateListener != null) {
      updateListener.onRequestInvalidate(this);
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

  public void prepareToBeCropped () {
    if (displayCrop == null) {
      displayCrop = new CropState();
      croppedRect = new Rect();
    }
  }

  @Override
  public View getTargetView () {
    return view;
  }

  private boolean isTargetRotated () {
    if (file != null) {
      int rotation = file.getRotation();
      if (displayCrop != null) {
        rotation += displayCrop.getRotateBy();
      }
      return U.isRotated(rotation);
    }
    return false;
  }

  @Override
  public int getTargetWidth () {
    int sourceWidth, sourceHeight;
    if (U.isValidBitmap(bitmap)) {
      if (isTargetRotated()) {
        sourceHeight = bitmapRect.width();
        sourceWidth = bitmapRect.height();
      } else {
        sourceWidth = bitmapRect.width();
        sourceHeight = bitmapRect.height();
      }
    } else if (file instanceof ImageGalleryFile) {
      sourceWidth = ((ImageGalleryFile) file).getWidth();
      sourceHeight = ((ImageGalleryFile) file).getHeight();
    } else {
      sourceWidth = 0;
      sourceHeight = 0;
    }
    if (sourceWidth == 0 || sourceHeight == 0) {
      return 0;
    }
    sourceWidth = getCroppedWidth(sourceWidth);
    sourceHeight = getCroppedHeight(sourceHeight);
    float widthRatio = (float) getWidth() / (float) sourceWidth;
    float heightRatio = (float) getHeight() / (float) sourceHeight;
    float ratio;
    if (file != null) {
      switch (file.getScaleType()) {
        case ImageFile.CENTER_CROP:
          ratio = Math.max(widthRatio, heightRatio);
          break;
        case ImageFile.FIT_CENTER:
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

  @Override
  public int getTargetHeight () {
    int sourceWidth, sourceHeight;
    if (U.isValidBitmap(bitmap)) {
      if (isTargetRotated()) {
        sourceHeight = bitmapRect.width();
        sourceWidth = bitmapRect.height();
      } else {
        sourceWidth = bitmapRect.width();
        sourceHeight = bitmapRect.height();
      }
    } else if (file instanceof ImageGalleryFile) {
      sourceWidth = ((ImageGalleryFile) file).getWidth();
      sourceHeight = ((ImageGalleryFile) file).getHeight();
    } else {
      sourceWidth = 0;
      sourceHeight = 0;
    }
    if (sourceWidth == 0 || sourceHeight == 0) {
      return 0;
    }
    sourceWidth = getCroppedWidth(sourceWidth);
    sourceHeight = getCroppedHeight(sourceHeight);
    float widthRatio = (float) getWidth() / (float) sourceWidth;
    float heightRatio = (float) getHeight() / (float) sourceHeight;
    float ratio;
    if (file != null) {
      switch (file.getScaleType()) {
        case ImageFile.CENTER_CROP:
          ratio = Math.max(widthRatio, heightRatio);
          break;
        case ImageFile.FIT_CENTER:
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

  private boolean hasColorFilter;
  private int colorFilter;

  @Override
  public void setColorFilter (int colorFilter) {
    if (!this.hasColorFilter || this.colorFilter != colorFilter) {
      this.hasColorFilter = true;
      this.colorFilter = colorFilter;

      PorterDuffColorFilter duffColorFilter = new PorterDuffColorFilter(colorFilter, PorterDuff.Mode.SRC_IN);
      this.bitmapPaint.setColorFilter(duffColorFilter);
      if (repeatPaint != null) {
        this.repeatPaint.setColorFilter(duffColorFilter);
      }

      invalidate();
    }
  }

  @Override
  public void disableColorFilter () {
    if (this.hasColorFilter) {
      this.hasColorFilter = false;
      this.bitmapPaint.setColorFilter(null);
      if (repeatPaint != null) {
        this.repeatPaint.setColorFilter(null);
      }
      invalidate();
    }
  }

  public void setAnimationDisabled (boolean animationDisabled) {
    if (ANIMATION_ENABLED && this.animationDisabled != animationDisabled) {
      this.animationDisabled = animationDisabled;

      if (animationDisabled) {
        this.alpha = 1f;
      } else if (U.isValidBitmap(bitmap)) {
        this.alpha = 1f;
      } else {
        this.alpha = 0f;
      }
      if (savedAlpha == 0) {
        if (roundPaint != null) {
          roundPaint.setAlpha((int) (255f * alpha));
        }
        if (repeatPaint != null) {
          repeatPaint.setAlpha((int) (255f * alpha));
        }
        bitmapPaint.setAlpha((int) (255f * alpha));
      }

      invalidate();
    }
  }

  public void setCompleteListener (OnCompleteListener listener) {
    this.completeListener = listener;
  }

  // Call this always before setBounds

  public void copyBounds (ImageReceiver other) {
    setBounds(other.left, other.top, other.right, other.bottom);
    setRadius(other.radius);
  }

  @Override
  public void setRadius (float radius) {
    if (this.radius != radius) {
      this.radius = radius;

      if (roundPaint == null) {
        roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        roundPaint.setAlpha(bitmapPaint.getAlpha());
        shaderMatrix = new Matrix();
        bitmapRectF = new RectF();
        roundRect = new RectF();
      }

      roundRect.set(left, top, right, bottom);

      if (U.isValidBitmap(bitmap)) {
        if (radius > 0) {
          boolean needShader = bitmapShader == null;
          if (needShader) {
            bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
          }
          layoutRound();
          if (needShader) {
            roundPaint.setShader(bitmapShader);
          }
        } else {
          layoutRect();
        }
      }
    }
  }

  @Override
  public boolean setBounds (int left, int top, int right, int bottom) {
    if (this.left != left || this.top != top || this.right != right || this.bottom != bottom) {
      this.left = left;
      this.top = top;
      this.right = right;
      this.bottom = bottom;
      if (radius > 0) {
        layoutRound();
      } else {
        layoutRect();
      }
      return true;
    }
    return false;
  }

  @Override
  public void forceBoundsLayout () {
    if (radius > 0) {
      layoutRound();
    } else {
      layoutRect();
    }
  }

  @Override
  public void onCropStateChanged (ImageFile file, CropState newCropState) {
    if (!displayCrop.compare(newCropState)) {
      displayCrop.set(newCropState);
      forceBoundsLayout();
      invalidateFully();
    }
  }

  public float getRadius () {
    return radius;
  }

  private void layoutRound () {
    this.drawRegion.set(left, top, right, bottom);
    if (U.isValidBitmap(bitmap)) {
      roundRect.set(drawRegion);
      shaderMatrix.reset();
      bitmapRectF.set(bitmapRect.left, bitmapRect.top, bitmapRect.right, bitmapRect.bottom);

      if (file != null) {
        float ratio = -1f;
        switch (file.getScaleType()) {
          case ImageFile.CENTER_CROP:{
            ratio = Math.max(roundRect.width() / (float) bitmapRect.width(), roundRect.height() / (float) bitmapRect.height());
            break;
          }
          case ImageFile.FIT_CENTER: {
            ratio = Math.min(roundRect.width() / (float) bitmapRect.width(), roundRect.height() / (float) bitmapRect.height());
            break;
          }
        }
        if (ratio != -1f) {
          int resultWidth = (int) (bitmapRect.width() * ratio);
          int resultHeight = (int) (bitmapRect.height() * ratio);
          float cx = roundRect.centerX();
          float cy = roundRect.centerY();
          roundRect.set(
            cx - resultWidth / 2f,
            cy - resultHeight / 2f,
            cx + resultWidth / 2f,
           cy + resultHeight / 2f
          );
        }
      }
      shaderMatrix.setRectToRect(bitmapRectF, roundRect, Matrix.ScaleToFit.FILL);

      if (bitmapShader != null) {
        bitmapShader.setLocalMatrix(shaderMatrix);
      }
    }
  }

  private int getCroppedWidth (int sourceWidth) {
    return sourceWidth;
  }

  private int getCroppedHeight (int sourceHeight) {
    return sourceHeight;
  }

  private int getVisualRotation () {
    if (file != null) {
      int rotation = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && file instanceof ImageGalleryFile && ((ImageGalleryFile) file).needThumb() ? 0 : file.getVisualRotation();
      if (displayCrop != null) {
        rotation = MathUtils.modulo(rotation + displayCrop.getRotateBy(), 360);
      }
      return rotation;
    }
    return 0;
  }

  private boolean layoutMatrix (Matrix matrix) {
    if (file == null || !U.isValidBitmap(bitmap)) {
      return false;
    }
    switch (file.getScaleType()) {
      case ImageFile.CENTER_CROP: {
        matrix.reset();

        int deltaWidth = bitmapRect.width();
        int deltaHeight = bitmapRect.height();

        int viewWidth = drawRegion.width();
        int viewHeight = drawRegion.height();

        float scale;
        float dx = 0, dy = 0;

        if (deltaWidth * viewHeight > viewWidth * deltaHeight) {
          scale = (float) viewHeight / (float) deltaHeight;
          dx = (viewWidth - deltaWidth * scale) * 0.5f;
        } else {
          scale = (float) viewWidth / (float) deltaWidth;
          dy = (viewHeight - deltaHeight * scale) * 0.5f;
        }

        matrix.setScale(scale, scale);
        matrix.postTranslate((int) (dx + 0.5f), (int) (dy + 0.5f));

        return true;
      }
      case ImageFile.FIT_CENTER: {
        matrix.reset();

        int deltaWidth, deltaHeight;

        boolean rotated = U.isRotated(getVisualRotation());
        if (rotated) {
          deltaWidth = bitmapRect.height();
          deltaHeight = bitmapRect.width();
        } else {
          deltaWidth = bitmapRect.width();
          deltaHeight = bitmapRect.height();
        }

        int viewWidth = drawRegion.width();
        int viewHeight = drawRegion.height();

        float scale = Math.min((float) viewWidth / (float) deltaWidth, (float) viewHeight / (float) deltaHeight);

        int futureWidth = (int) ((float) deltaWidth * scale);
        int futureHeight = (int) ((float) deltaHeight * scale);

        float dx, dy;

        if (rotated) {
          dx = (viewWidth - futureHeight) / 2f;
          dy = (viewHeight - futureWidth) / 2f;
        } else {
          dx = (viewWidth - futureWidth) / 2f;
          dy = (viewHeight - futureHeight) / 2f;
        }

        matrix.setScale(scale, scale);
        matrix.postTranslate((int) (dx + 0.5f), (int) (dy + 0.5f));

        return true;
      }
    }
    return false;
  }

  public void setCropApplyFactor (float factor) {
    if (this.cropApplyFactor != factor) {
      this.cropApplyFactor = factor;
      forceBoundsLayout();
      invalidate();
    }
  }

  private void layoutRect () {
    this.drawRegion.set(left, top, right, bottom);

    if (U.isValidBitmap(bitmap)) {
      if (displayCrop != null && (!displayCrop.isRegionEmpty() || displayCrop.getDegreesAroundCenter() != 0f)) {

        // First, normalize region area
        double left = displayCrop.getLeft();
        double right = displayCrop.getRight();
        double top = displayCrop.getTop();
        double bottom = displayCrop.getBottom();

        int sourceWidth = bitmap.getWidth();
        int sourceHeight = bitmap.getHeight();

        int rotateArea = -getVisualRotation();
        while (rotateArea != 0) {
          if (left != 0.0 || top != 0.0 || right != 1.0 || bottom != 1.0) {
            double prevLeft = left;
            double prevTop = top;
            double prevRight = right;
            double prevBottom = bottom;

            if (rotateArea < 0) {
              bottom = 1.0 - prevLeft;
              right = prevBottom;
              top = 1.0 - prevRight;
              left = prevTop;
              rotateArea += 90;
            } else {
              bottom = prevRight;
              left = 1.0 - prevBottom;
              top = prevLeft;
              right = 1.0 - prevTop;
              rotateArea -= 90;
            }
          } else {
            rotateArea += 90 * -Math.signum(rotateArea);
          }
        }

        // Then, scale if needed
        float degrees = displayCrop.getDegreesAroundCenter();
        if (degrees != 0f) {
          double rad = Math.toRadians(degrees);
          float sin = (float) Math.abs(Math.sin(rad));
          float cos = (float) Math.abs(Math.cos(rad));

          float W = sourceWidth * cos + sourceHeight * sin;
          float H = sourceWidth * sin + sourceHeight * cos;

          float rotateScale = Math.max(W / sourceWidth, H / sourceHeight);

          /*
          sourceWidth *= rotateScale;
          sourceHeight *= rotateScale;*/

          // sourceWidth *= rotateScale;
          // sourceHeight *= rotateScale;
        }

        int bitmapLeft, bitmapTop, bitmapRight, bitmapBottom;

        bitmapLeft = (int) Math.ceil(left * (double) sourceWidth);
        bitmapRight = (int) Math.floor(right * (double) sourceWidth);
        bitmapTop = (int) Math.ceil(top * (double) sourceHeight);
        bitmapBottom = (int) Math.floor(bottom * (double) sourceHeight);

        croppedRect.set(bitmapLeft, bitmapTop, bitmapRight, bitmapBottom);

        if (cropApplyFactor != 1f) {
          bitmapLeft = (int) Math.ceil((left * cropApplyFactor) * (double) sourceWidth);
          bitmapRight = (int) Math.floor((1.0 + (right - 1.0) * cropApplyFactor) * (double) sourceWidth);
          bitmapTop = (int) Math.ceil((top * cropApplyFactor) * (double) sourceHeight);
          bitmapBottom = (int) Math.floor((1.0 + (bottom - 1.0) * cropApplyFactor) * (double) sourceHeight);
        }
        bitmapRect.set(bitmapLeft, bitmapTop, bitmapRight, bitmapBottom);
      } else {
        bitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        if (croppedRect != null) {
          croppedRect.set(bitmapRect);
        }
      }
    } else {
      return;
    }

    if (file != null) {
      if (bitmapMatrix == null) {
        bitmapMatrix = new Matrix();
      }
      layoutMatrix(bitmapMatrix);
    }
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

  public void setAlpha (@FloatRange(from = 0.0, to = 1.0) float alpha) {
    if (ANIMATION_ENABLED && !animationDisabled && this.alpha != alpha) {
      this.alpha = alpha;
      if (savedAlpha == 0) {
        if (roundPaint != null) {
          roundPaint.setAlpha((int) (255f * alpha));
        }
        if (repeatPaint != null) {
          repeatPaint.setAlpha((int) (255f * alpha));
        }
        bitmapPaint.setAlpha((int) (255f * alpha));
      }
      invalidate();
    }
  }

  public float getAlpha () {
    return this.alpha;
  }

  public float getDisplayAlpha () {
    return isLoaded() ? getAlpha() : 0f;
  }

  public void forceAlpha (float alpha) {
    if (ANIMATION_ENABLED && !animationDisabled) {
      if (animator != null) {
        animator.cancel();
      }
      this.alpha = alpha;
      if (roundPaint != null) {
        roundPaint.setAlpha((int) (255f * alpha));
      }
      if (repeatPaint != null) {
        repeatPaint.setAlpha((int) (255f * alpha));
      }
      bitmapPaint.setAlpha((int) (255f * alpha));
    }
  }

  public void setProgress (float progress) {
    if (this.progress != progress) {
      this.progress = progress;
      invalidate();
    }
  }

  public void setNeedProgress () {
    this.needProgress = true;
  }

  public void requestFile (ImageFile file) {
    if (isDetached) {
      cachedFile = file;
      return;
    }

    /*final int accountId1 = this.file == null ? TdlibAccount.NO_ID : this.file.accountId();
    final int accountId2 = file == null ? TdlibAccount.NO_ID : file.accountId();

    final int fileType1 = this.file == null ? 0 : this.file.getType();
    final int fileType2 = file == null ? 0 : file.getType();

    final String persistentId1 = this.file instanceof ImageFileRemote ? this.file.getRemoteId() : this.file instanceof ImageApicFile ? this.file.toString() : null;
    final String persistentId2 = file instanceof ImageFileRemote ? file.getRemoteId() : file instanceof ImageApicFile ? file.toString() : null;

    final int fileId1 = this.file != null && persistentId1 == null ? this.file.getId() : 0;
    final int fileId2 = file != null && persistentId2 == null ? file.getId() : 0;*/

    boolean sameFiles = sameFiles(this.file, file);

    if (!sameFiles || (this.bitmap != null && this.bitmap.isRecycled())) {
      if (this.file != null) {
        ImageLoader.instance().removeWatcher(reference);
      }

      if (file != null) {
        Bitmap bitmap = ImageStrictCache.instance().get(file);
        if (!U.isValidBitmap(bitmap)) {
          bitmap = ImageCache.instance().getBitmap(file);
        }

        if (!U.isValidBitmap(bitmap)) {
          boolean changed = alpha != 0f;
          forceAlpha(0f);
          if (!setBundle(file, file.suppressEmptyBundle() && U.isValidBitmap(this.bitmap) ? this.bitmap : null, true) && changed) {
            invalidate();
          }

          if (!file.isCacheOnly()) {
            ImageLoader.instance().requestFile(file, reference);
          }
        } else {
          boolean changed = alpha != 1f;
          forceAlpha(1f);
          if (!setBundle(file, bitmap, true) && changed) {
            invalidate();
          }
          dispatchCompleted();
        }
      } else {
        setBundle(null, null, true);
      }
    } else if (this.file != file) {
      setBundle(file, this.bitmap, true);
      if (U.isValidBitmap(this.bitmap)) {
        if (radius > 0) {
          layoutRound();
        } else {
          layoutRect();
        }
        invalidate();
      }
    }
  }

  void setBundleOrIgnore (ImageFile file, Bitmap bitmap) {
    ImageFile currentFile = this.file;
    if (compareToFile(currentFile, file)) {
      dispatchCompleted();
      setBundle(currentFile, bitmap, true);
    }
  }

  private void setBitmap (Bitmap bitmap) {
    if (this.bitmap != bitmap) {
      this.bitmap = bitmap;
      if (bitmapShader != null) {
        bitmapShader = null;
        if (roundPaint != null) {
          roundPaint.setShader(null);
        }
        if (repeatPaint != null) {
          repeatPaint.setShader(null);
        }
      }
    }
    if (U.isValidBitmap(bitmap)) {
      bitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
    } else {
      bitmapRect.set(0, 0, 1, 1);
    }
  }

  // returns @boolean invalidated

  private static boolean sameFiles (ImageFile file1, ImageFile file2) {
    return (file1 == file2) || ((file1 != null ? file1.getType() : 0) == (file2 != null ? file2.getType() : 0) && StringUtils.equalsOrBothEmpty(file1 != null ? file1.toString() : null, file2 != null ? file2.toString() : null));
  }

  public boolean setBundle (ImageFile file, Bitmap bitmap, boolean local) {
    if (this.file == null && file == null) {
      return false;
    }

    /*final int fileType1 = this.file == null ? 0 : this.file.getType();
    final int fileType2 = file == null ? 0 : file.getType();

    final String persistentId1 = this.file != null && this.file instanceof ImageFileRemote ? this.file.getRemoteId() : null;
    final String persistentId2 = file != null && file instanceof ImageFileRemote ? file.getRemoteId() : null;

    final int fileId1 = this.file != null && persistentId1 == null ? this.file.getId() : 0;
    final int fileId2 = file != null && persistentId2 == null ? file.getId() : 0;*/

    // final boolean sameFiles = fileType1 == fileType2 && fileId1 == 0 && fileId2 == 0 && Strings.compare(persistentId1, persistentId2);
    final boolean sameFiles = sameFiles(this.file, file);

    final boolean needRefs1 = this.file == null || this.file.needReferences();
    final boolean needRefs2 = file == null || file.needReferences();

    boolean bitmapChanged = false;

    if (sameFiles) {
      final Bitmap oldBitmap = this.bitmap;
      final ImageFile oldFile = this.file;

      if (oldBitmap != bitmap) {
        synchronized (ImageCache.getReferenceCounters()) {
          if (needRefs1 && oldBitmap != null) {
            ImageCache.instance().removeReference(oldFile, oldBitmap);
          }
          if (needRefs2 && bitmap != null) {
            ImageCache.instance().addReference(file, bitmap);
          }
          setBitmap(bitmap);
          bitmapChanged = true;
        }
      }

      if (oldFile != file) {
        this.file = file;
      }
    } else {
      ImageFile oldFile = this.file;
      Bitmap oldBitmap = this.bitmap;

      synchronized (ImageCache.getReferenceCounters()) {
        this.file = file;
        setBitmap(bitmap);

        if (displayCrop != null) {
          if (oldFile != null) {
            oldFile.removeCropStateListener(this);
          }
          if (file != null) {
            displayCrop.set(file.getCropState());
            file.addCropStateListener(this);
          } else {
            displayCrop.set(null);
          }
        }

        if (needRefs1 && oldFile != null) {
          ImageCache.instance().removeReference(oldFile, oldBitmap);
        }
        if (needRefs2 && file != null && bitmap != null) {
          ImageCache.instance().addReference(file, bitmap);
        }
      }

      if (oldBitmap != bitmap) {
        bitmapChanged = true;
      }
    }

    if (U.isValidBitmap(bitmap)) {
      if (radius > 0) {
        if (bitmapChanged) {
          bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
          roundPaint.setShader(bitmapShader);
          layoutRound();
        } else {
          layoutRound();
          bitmapChanged = true;
        }
      } else if (file.getScaleType() == ImageFile.CENTER_REPEAT) {
        if (bitmapChanged) {
          if (repeatPaint == null) {
            repeatPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
            repeatPaint.setAlpha(bitmapPaint.getAlpha());
            repeatPaint.setColorFilter(bitmapPaint.getColorFilter());
          }

          bitmapShader = new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
          repeatPaint.setShader(bitmapShader);
          layoutRect();
        } else {
          layoutRect();
          bitmapChanged = true;
        }
      } else {
        layoutRect();
      }
    }

    if (bitmapChanged) {
      if (local) {
        if (ANIMATION_ENABLED && !animationDisabled && U.isValidBitmap(bitmap) && alpha == 0f) {
          animate();
        } else {
          invalidate();
        }
      } else {
        if (ANIMATION_ENABLED && !animationDisabled && U.isValidBitmap(bitmap) && alpha == 0f) {
          postAnimate();
        } else {
          postInvalidate();
        }
      }
    }

    return bitmapChanged;
  }

  // Listeners

  public boolean isLoaded () {
    return U.isValidBitmap(bitmap);
  }

  public boolean needPlaceholder () {
    return !isLoaded() || (alpha != 1f && ANIMATION_ENABLED && !animationDisabled);
  }

  @Override
  public void drawPlaceholder (Canvas c) {
    drawPlaceholderRounded(c, radius);
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

  // Current state

  public ImageFile getCurrentFile () {
    return isDetached ? cachedFile : file;
  }

  @Override
  public boolean isEmpty () {
    return getCurrentFile() == null;
  }

  public Bitmap getCurrentBitmap () {
    return bitmap;
  }

  // Remotes

  private static boolean compareToFile (ImageFile file1, ImageFile file2) {
    if (file1 == null || file2 == null) {
      return false;
    }
    if (file1 == file2) {
      return true;
    }

    final int fileType1 = file1 == null ? 0 : file1.getType();
    final int fileType2 = file2 == null ? 0 : file2.getType();

    final String persistentId1 = file1 != null && file1 instanceof ImageFileRemote ?  file1.getRemoteId() : null;
    final String persistentId2 = file2 != null && file2 instanceof ImageFileRemote ? file2.getRemoteId() : null;

    final int fileId1 = file1 != null && persistentId1 == null ? file1.getId() : 0;
    final int fileId2 = file2 != null && persistentId2 == null ? file2.getId() : 0;

    return fileType1 == fileType2 && fileId1 == fileId2 && StringUtils.equalsOrBothEmpty(persistentId1, persistentId2);
  }

  @Override
  public void imageLoaded (ImageFile file, boolean successful, Bitmap bitmap) {
    ImageFile currentFile = this.file;
    if (compareToFile(currentFile, file)) {
      if (successful) {
        handler.display(this, currentFile, (Bitmap) bitmap);
      } else {
        setBundle(currentFile, null, false);
      }
    }
  }

  @Override
  public void imageProgress (ImageFile file, float progress) {
    ImageFile currentFile = this.file;
    if (needProgress && compareToFile(currentFile, file)) {
      setProgress(progress);
    }
  }

  private void dispatchCompleted () {
    if (completeListener != null) {
      completeListener.onComplete(this, file);
    }
  }

  // Drawings

  private ImageAnimator animator;
  private ValueAnimator objAnimator;

  public void animate () {
    if (ANIMATION_ENABLED && !animationDisabled) {
      if (animator != null) {
        animator.cancel();
      }
      if (objAnimator != null) {
        objAnimator.cancel();
      }

      setAlpha(0f);

      if (this.file != null && this.file instanceof ImageMp3File) {
        if (objAnimator == null) {
          objAnimator = AnimatorUtils.simpleValueAnimator();
          objAnimator.addUpdateListener(this);
          objAnimator.setDuration(120l);
          objAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
        } else {
          objAnimator.setFloatValues(0f, 1f);
        }
        objAnimator.start();
      } else {
        if (animator == null) {
          if (this.file != null) {
            animator = new ImageAnimator(this, this.file instanceof ImageGalleryFile);
          } else {
            animator = new ImageAnimator(this);
          }
        }
        animator.start();
      }
    }
  }

  @Override
  public void onAnimationUpdate (ValueAnimator animation) {
    setAlpha(AnimatorUtils.getFraction(animation));
  }

  public void postAnimate () {
    if (ANIMATION_ENABLED && !animationDisabled) {
      handler.animate(this);
    } else {
      handler.invalidate(this);
    }
  }

  public void postInvalidate () {
    handler.invalidate(this);
  }

  private int savedAlpha;

  @Override
  public float getPaintAlpha () {
    return (float) (repeatPaint != null ? repeatPaint.getAlpha() : roundPaint != null ? roundPaint.getAlpha() : bitmapPaint.getAlpha()) / 255f;
  }

  @Override
  public void setPaintAlpha (float factor) {
    int bitmapAlpha = bitmapPaint.getAlpha();
    savedAlpha = Color.rgb(repeatPaint != null ? repeatPaint.getAlpha() : bitmapAlpha, roundPaint != null ? roundPaint.getAlpha() : bitmapAlpha, bitmapAlpha);
    final int alpha = (int) (255f * MathUtils.clamp(factor));
    if (roundPaint != null)
      roundPaint.setAlpha(alpha);
    if (repeatPaint != null)
      repeatPaint.setAlpha(alpha);
    bitmapPaint.setAlpha(alpha);
  }

  @Override
  public void restorePaintAlpha () {
    if (repeatPaint != null)
      repeatPaint.setAlpha(Color.red(savedAlpha));
    if (roundPaint != null)
      roundPaint.setAlpha(Color.green(savedAlpha));
    bitmapPaint.setAlpha(Color.blue(savedAlpha));
    savedAlpha = 0;
  }

  @Override
  public boolean isInsideContent (float x, float y, int emptyWidth, int emptyHeight) {
    if (file != null || (emptyWidth != 0 && emptyHeight != 0)) {
      int sourceWidth, sourceHeight;
      if (file == null) {
        sourceWidth = emptyWidth;
        sourceHeight = emptyHeight;
      } else {
        if (U.isValidBitmap(bitmap)) {
          if (isTargetRotated()) {
            sourceWidth = getCroppedWidth(bitmapRect.height());
            sourceHeight = getCroppedHeight(bitmapRect.width());
          } else {
            sourceWidth = getCroppedWidth(bitmapRect.width());
            sourceHeight = getCroppedHeight(bitmapRect.height());
          }
        } else if (file instanceof ImageGalleryFile) {
          sourceWidth = getCroppedWidth(((ImageGalleryFile) file).getWidth());
          sourceHeight = getCroppedHeight(((ImageGalleryFile) file).getHeight());
        } else {
          return false;
        }
      }
      int scaleType = file != null ? file.getScaleType() : ImageFile.FIT_CENTER;
      switch (scaleType) {
        case ImageFile.FIT_CENTER: {
          int availWidth = right - left;
          int availHeight = bottom - top;

          float ratio = Math.min((float) availWidth / (float) sourceWidth, (float) availHeight / (float) sourceHeight);
          sourceWidth *= ratio;
          sourceHeight *= ratio;

          int centerX = (left + right) / 2;
          int centerY = (top + bottom) / 2;

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

  @Override
  public void draw (Canvas c) {
    if (U.isValidBitmap(bitmap)) {
      final int rotation = getVisualRotation();
      if (radius != 0) {
        if (rotation != 0) {
          c.save();
          c.rotate(rotation, left + (right - left) / 2f, top + (bottom - top) / 2f);
        }
        drawRoundRect(c, roundRect, radius, radius, roundPaint);
        if (rotation != 0) {
          c.restore();
        }
      } else if (file.getScaleType() == ImageFile.CENTER_REPEAT) {
        c.save();
        c.drawRect(left, top, right, bottom, repeatPaint);
        c.restore();
      } else {
        PaintState paintState = file.getPaintState();
        float scaleType = file.getScaleType();
        if (scaleType == ImageFile.CENTER_CROP || scaleType == ImageFile.FIT_CENTER) {
          // c.drawRect(left, top, right, bottom, Paints.fillingPaint(0xaa00ff00));
          boolean hasCrop = displayCrop != null;
          float degrees = 0f;
          if (hasCrop) {
            degrees = displayCrop.getDegreesAroundCenter();
            hasCrop = degrees != 0f || !displayCrop.isRegionEmpty();
          }

          c.save();
          c.clipRect(left, top, right, bottom);
          if (left != 0 || top != 0) {
            c.translate(left, top);
          }
          if (rotation != 0) {
            c.rotate(rotation, (right - left) / 2f, (bottom - top) / 2f);
          }

          if (hasCrop) {
            c.concat(bitmapMatrix);
            Rect rect = Paints.getRect();
            if (cropApplyFactor < 1f || degrees != 0f || paintState != null) {
              int left = croppedRect.left - bitmapRect.left;
              int top = croppedRect.top - bitmapRect.top;
              c.clipRect(left, top, left + croppedRect.width(), top + croppedRect.height());
            }
            rect.set(0, 0, bitmapRect.width(), bitmapRect.height());
            if (degrees != 0f) {
              c.translate(-bitmapRect.left, -bitmapRect.top);

              float w = bitmap.getWidth();
              float h = bitmap.getHeight();
              double rad = Math.toRadians(degrees);
              float sin = (float) Math.abs(Math.sin(rad));
              float cos = (float) Math.abs(Math.cos(rad));

              // W = w·|cos φ| + h·|sin φ|
              // H = w·|sin φ| + h·|cos φ|

              float W = w * cos + h * sin;
              float H = w * sin + h * cos;

              float scale = Math.max(W / w, H / h);
              float cx = w / 2;
              float cy = h / 2;
              c.rotate(degrees, cx, cy);
              c.scale(scale, scale, cx, cy);

              drawBitmap(c, bitmap, 0, 0, bitmapPaint);
              if (paintState != null) {
                paintState.draw(c, 0, 0, bitmap.getWidth(), bitmap.getHeight());
              }
            } else {
              drawBitmap(c, bitmap, bitmapRect, rect, bitmapPaint);
              if (paintState != null) {
                c.clipRect(rect);
                DrawAlgorithms.drawPainting(c, bitmap, bitmapRect, rect, paintState);
              }
            }
          } else {
            c.concat(bitmapMatrix);
            drawBitmap(c, bitmap, 0, 0, bitmapPaint);
            if (paintState != null) {
              c.clipRect(0, 0, bitmap.getWidth(), bitmap.getHeight());
              paintState.draw(c, 0, 0, bitmap.getWidth(), bitmap.getHeight());
            }
          }

          c.restore();
        } else {
          drawBitmap(c, bitmap, bitmapRect, drawRegion, bitmapPaint);
          if (paintState != null) {
            c.save();
            c.clipRect(drawRegion);
            DrawAlgorithms.drawPainting(c, bitmap, bitmapRect, drawRegion, paintState);
            c.restore();
          }
        }
      }
    }
  }

  public interface OnCompleteListener {
    void onComplete (ImageReceiver receiver, ImageFile imageFile);
  }

  private static void drawBitmap (Canvas c, Bitmap bitmap, float left, float top, Paint paint) {
    try {
      c.drawBitmap(bitmap, left, top, paint);
    } catch (Throwable t) {
      Log.e(Log.TAG_IMAGE_LOADER, "Unable to draw bitmap", t);
      Tracer.onOtherError(t);
    }
  }

  private static void drawBitmap (Canvas c, Bitmap bitmap, Rect rect, Rect drawRegion, Paint paint) {
    try {
      c.drawBitmap(bitmap, rect, drawRegion, paint);
    } catch (Throwable t) {
      Log.e(Log.TAG_IMAGE_LOADER, "Unable to draw bitmap", t);
      Tracer.onOtherError(t);
    }
  }

  private static void drawRoundRect (Canvas c, RectF rect, float rx, float ry, Paint paint) {
    try {
      c.drawRoundRect(rect, rx, ry, paint);
    } catch (Throwable t) {
      Log.e(Log.TAG_IMAGE_LOADER, "Unable to draw bitmap", t);
      Tracer.onOtherError(t);
    }
  }
}
