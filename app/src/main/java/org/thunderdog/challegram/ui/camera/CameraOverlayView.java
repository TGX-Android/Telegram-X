package org.thunderdog.challegram.ui.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

/**
 * Date: 9/21/17
 * Author: default
 */

public class CameraOverlayView extends View implements FactorAnimator.Target {
  private static final int FAKE_FLASH_COLOR = 0xfce8d2;
  private static final float MAX_FLASH_ALPHA = 1f;

  public CameraOverlayView (Context context) {
    super(context);
  }

  @Override
  protected void onDraw (Canvas c) {
    final int viewWidth = getMeasuredWidth();
    final int viewHeight = getMeasuredHeight();
    int alpha = (int) (255f * overlayFactor);
    if (alpha > 0) {
      if (preview == null || preview.isRecycled()) {
        c.drawColor(ColorUtils.color(alpha, 0));
      } else {
        Paint paint = Paints.getBitmapPaint();
        if (alpha != 255) {
          paint.setAlpha(alpha);
        }
        int rotation = MathUtils.modulo(previewRotation - UI.getContext(getContext()).getWindowRotationDegrees(), 360);
        if (rotation == 180)
          rotation = 0;
        int width, height;
        if (U.isRotated(rotation)) {
          width = preview.getHeight();
          height = preview.getWidth();
        } else {
          width = preview.getWidth();
          height = preview.getHeight();
        }
        float scale = Math.max((float) viewWidth / (float) width, (float) viewHeight / (float) height);
        final boolean save = scale != 1f || rotation != 0;
        if (save) {
          c.save();
          if (scale != 1f)
            c.scale(scale, scale, viewWidth / 2f, viewHeight / 2f);
          if (rotation != 0)
            c.rotate(rotation, viewWidth / 2f, viewHeight / 2f);
        }
        c.drawBitmap(preview, viewWidth / 2f - preview.getWidth() / 2f, viewHeight / 2f - preview.getHeight() / 2f, paint);
        if (save) {
          c.restore();
        }
        if (alpha != 255) {
          paint.setAlpha(255);
        }
      }
    }
    float gridAlphaFactor = isGridVisible != null ? isGridVisible.getFloatValue() : 0f;
    if (gridAlphaFactor > 0f) {
      int horizontalStep = viewWidth / 3;
      int verticalStep = viewHeight / 3;
      int cx = 0, cy = 0;
      for (int i = 0; i < 2; i++) {
        cx += horizontalStep; cy += verticalStep;
        c.drawLine(cx, 0, cx, viewHeight, Paints.getProgressPaint(0x4affffff, Screen.dp(1f)));
        c.drawLine(0, cy, viewWidth, cy, Paints.getProgressPaint(0x4affffff, Screen.dp(1f)));
      }
    }
    alpha = (int) (255f * flashFactor * MAX_FLASH_ALPHA);
    if (alpha > 0) {
      c.drawColor(ColorUtils.color(alpha, FAKE_FLASH_COLOR));
    }
  }

  // Visibility

  private static final int ANIMATOR_VISIBILITY = 0;

  private boolean overlayVisible = true;
  private float overlayFactor = 1f;

  private FactorAnimator visibilityAnimator;

  private boolean needFastAnimations;

  public void setNeedFastAnimations (boolean needFastAnimations) {
    if (this.needFastAnimations != needFastAnimations) {
      this.needFastAnimations = needFastAnimations;
      if (needFastAnimations) {
        if (U.isValidBitmap(takenPreview) && takenPreview.getPixel(0, 0) != 0) {
          this.preview = takenPreview;
          this.previewRotation = takenPreviewRotation;
        }
        invalidate();
      }
    }
  }

  private Runnable onDone;

  public void setOverlayVisible (boolean isVisible, boolean animated, Runnable onDone) {
    if (this.overlayVisible != isVisible) {
      this.overlayVisible = isVisible;
      final float toFactor = isVisible ? 1f : 0f;
      if (animated) {
        this.onDone = onDone;
        if (visibilityAnimator == null) {
          visibilityAnimator = new FactorAnimator(ANIMATOR_VISIBILITY, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 140l, this.overlayFactor);
        }
        visibilityAnimator.setDuration((needFastAnimations && preview == null) || isVisible ? 180l : 290l);
        visibilityAnimator.animateTo(toFactor);
      } else {
        this.onDone = null;
        if (visibilityAnimator != null) {
          visibilityAnimator.forceFactor(toFactor);
        }
        this.overlayFactor = toFactor;
        if (onDone != null) {
          onDone.run();
        }
      }
    }
  }

  private void forceOverlayFactor (float factor) {
    if (visibilityAnimator != null) {
      visibilityAnimator.forceFactor(factor);
    }
    setOverlayFactor(factor);
  }

  private Bitmap takenPreview;
  private int takenPreviewRotation;

  public void resetOverlayState () {
    this.overlayVisible = true;
    if (U.isValidBitmap(preview)) {
      takenPreview = preview;
      takenPreviewRotation = previewRotation;
    } else {
      takenPreview = null;
    }
    this.preview = null;
    forceOverlayFactor(1f);
    invalidate();
  }

  private void setOverlayFactor (float factor) {
    if (this.overlayFactor != factor) {
      this.overlayFactor = factor;
      invalidate();
    }
  }

  // Preview (fake bitmap)

  private Bitmap preview;
  private int previewRotation;

  public void setPreview (Bitmap bitmap, int rotation) {
    this.preview = bitmap;
    this.previewRotation = rotation;
    invalidate();
  }

  // Flash

  private static final int ANIMATOR_FLASH = 1;

  private FlashListener flashListener;

  public interface FlashListener {
    void onFlashPerformed ();
    void onFlashFinished ();
  }

  public void setFlashListener (FlashListener listener) {
    this.flashListener = listener;
  }

  private float flashFactor;

  private FactorAnimator flashAnimator;
  private static final long FLASH_DURATION_IN = 280l;
  private static final long FLASH_DURATION_OUT = 120l;

  public void performFakeFlash () {
    if (flashAnimator == null) {
      flashAnimator = new FactorAnimator(ANIMATOR_FLASH, this, AnimatorUtils.DECELERATE_INTERPOLATOR, FLASH_DURATION_IN, this.flashFactor);
    } else {
      flashAnimator.setDuration(FLASH_DURATION_IN);
      flashAnimator.setStartDelay(0);
    }
    flashAnimator.animateTo(1f);
  }

  public void releaseFakeFlash (boolean delayed) {
    if (flashAnimator != null) {
      flashAnimator.setStartDelay(delayed ? 1000 : 0);
      flashAnimator.setDuration(FLASH_DURATION_OUT);
      flashAnimator.animateTo(0f);
    }
  }

  private void setFlashFactor (float factor) {
    if (this.flashFactor != factor) {
      this.flashFactor = factor;
      invalidate();
    }
  }

  // Grid

  private static final int ANIMATOR_GRID = 2;
  private BoolAnimator isGridVisible;

  public void setGridVisible (boolean isVisible, boolean animated) {
    if (isGridVisible == null)
      isGridVisible = new BoolAnimator(ANIMATOR_GRID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 120l);
    isGridVisible.setValue(isVisible, animated);
  }

  // Listener

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_VISIBILITY:
        setOverlayFactor(factor);
        break;
      case ANIMATOR_FLASH:
        setFlashFactor(factor);
        break;
      case ANIMATOR_GRID:
        invalidate();
        break;
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_FLASH: {
        if (flashListener != null) {
          if (finalFactor == 1f) {
            flashListener.onFlashPerformed();
          } else if (finalFactor == 0f) {
            flashListener.onFlashFinished();
          }
        } else if (finalFactor == 1f) {
          releaseFakeFlash(true);
        }
        break;
      }
      case ANIMATOR_VISIBILITY: {
        if (onDone != null) {
          onDone.run();
          onDone = null;
        }
        break;
      }
    }
  }


}
