/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 22/09/2017
 */
package org.thunderdog.challegram.ui.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

public class CameraButton extends View implements FactorAnimator.Target, Runnable {
  private CameraController parent;
  private CameraBlurView blurView;

  private Bitmap cameraIcon, stopIcon, closeIcon;

  public CameraButton (Context context) {
    super(context);
    Views.setClickable(this);
    RippleSupport.setCircleBackground(this, 56f, 4f, R.id.theme_color_white, null);
    setLayerType(LAYER_TYPE_HARDWARE, null);

    int padding = Screen.dp(4f);
    setLayoutParams(FrameLayoutFix.newParams(Screen.dp(56f) + padding * 2, Screen.dp(56f) + padding * 2));
    setPadding(padding, padding, padding, padding);

    // cameraIcon = new EraseDrawable(R.drawable.baseline_camera_alt_24, R.id.theme_color_white);
    // stopIcon = new EraseDrawable(R.drawable.baseline_stop_24, R.id.theme_color_white);
    cameraIcon = Drawables.toBitmap(Drawables.get(R.drawable.baseline_camera_alt_24));
    closeIcon = Drawables.toBitmap(Drawables.get(R.drawable.baseline_close_24));
    stopIcon = Drawables.toBitmap(Drawables.get(R.drawable.baseline_stop_24));
  }

  public void setParent (CameraController parent) {
    this.parent = parent;
  }

  public void setBlurView (CameraBlurView blurView) {
    this.blurView = blurView;
  }

  private boolean isActive;

  private void dropActive (@Nullable MotionEvent e) {
    if (isActive) {
      isActive = false;
      if (e != null) {
        super.onTouchEvent(MotionEvent.obtain(e.getDownTime(), e.getEventTime(), MotionEvent.ACTION_UP, e.getX(), e.getY(), e.getMetaState()));
      } else {
        long eTime = SystemClock.uptimeMillis();
        super.onTouchEvent(MotionEvent.obtain(eTime, eTime, MotionEvent.ACTION_UP, x, y, 0));
      }
      cancelScheduledVideoRecord();
    }
  }

  private void scheduleVideoRecord () {
    postDelayed(this, ViewConfiguration.getLongPressTimeout());
  }

  private void cancelScheduledVideoRecord () {
    removeCallbacks(this);
  }

  private boolean inVideoCapture;

  public boolean finishVideoCapture (boolean needPhoto) {
    cancelScheduledVideoRecord();
    if (inVideoCapture) {
      inVideoCapture = false;
      needPhoto = needPhoto && listener != null && !isInRecordMode;
      if (listener != null) {
        listener.onFinishVideoCapture(this);
      } else {
        blurView.performSuccessHint();
        setInRecordMode(false);
      }
      if (!needPhoto)
        return true;
    }
    return needPhoto && takePhoto();
  }

  public boolean takePhoto () {
    if (!inVideoCapture) {
      blurView.performSuccessHint();
      if (listener != null) {
        listener.onTakePicture(this);
      }
      return true;
    }
    return false;
  }

  public void takeVideo () {
    if (listener != null) {
      inVideoCapture = listener.onStartVideoCapture(this);
    } else {
      inVideoCapture = true;
      setInRecordMode(true);
    }
  }

  private boolean isInRecordMode;
  private float recordFactor;
  private FactorAnimator recordAnimator;
  private static final int ANIMATOR_RECORD = 0;

  public void setInRecordMode (boolean inRecordMode) {
    if (this.isInRecordMode != inRecordMode && (inVideoCapture || !inRecordMode)) {
      this.isInRecordMode = inRecordMode;
      if (recordAnimator == null) {
        recordAnimator = new FactorAnimator(ANIMATOR_RECORD, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.recordFactor);
      }
      recordAnimator.animateTo(inRecordMode ? 1f : 0f);
      if (inRecordMode) {
        TdlibManager.instance().player().pauseWithReason(TGPlayerController.PAUSE_REASON_RECORD_VIDEO);
        dropActive(null);
      } else if (listener != null) {
        blurView.performSuccessHint();
      }
    }
  }

  private void setRecordFactor (float factor) {
    if (this.recordFactor != factor) {
      this.recordFactor = factor;
      blurView.setExpandFactor(factor);
      float scale = .85f + .15f * (1f - factor);
      setScaleX(scale);
      setScaleY(scale);
      parent.setVideoFactor(factor);
      invalidate();
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_RECORD:
        setRecordFactor(factor);
        break;
    }
  }

  /*@Override
  public void draw (Canvas c) {
    if (recordFactor != 0f) {
      int viewWidth = getMeasuredWidth();
      int viewHeight = getMeasuredHeight();
      c.save();
      float scale = .8f + (1f - recordFactor) * .2f;
      c.scale(scale, scale, viewWidth / 2, viewHeight / 2);
    }
    super.draw(c);
    if (recordFactor != 0f) {
      c.restore();
    }
  }*/

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  private float x, y;
  private boolean isZooming;

  private float actualZoom;

  public void setActualZoom (float zoom) {
    this.actualZoom = zoom;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    final int viewWidth = getMeasuredWidth();
    final int viewHeight = getMeasuredHeight();

    x = e.getX();
    y = e.getY();

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        isZooming = false;
        super.onTouchEvent(e);
        if (!isInRecordMode) {
          scheduleVideoRecord();
        }
        isActive = true;
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (isActive) {
          if (x < 0 || y < 0 || x > viewWidth || y > viewHeight) {
            dropActive(e);
          } else {
            super.onTouchEvent(e);
          }
        }

        final float zoomFactor = MathUtils.clamp(-y / Screen.dpf(150f));
        float minZoom = parent.getManager().getMinZoom();
        float maxZoom = parent.getManager().getMaxZoom();

        if (!isZooming && y < 0 && (minZoom + (maxZoom - minZoom) * zoomFactor) >= actualZoom) {
          isZooming = true;
        }

        if (isZooming) {
          parent.getManager().onRequestZoom(minZoom + (maxZoom - minZoom) * zoomFactor);
        }

        break;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        if (isActive) {
          super.onTouchEvent(e);
        }
        finishVideoCapture(!isInRecordMode && e.getAction() == MotionEvent.ACTION_UP && isActive);
        isActive = false;
        break;

      default: {
        if (isActive) {
          super.onTouchEvent(e);
        }
        break;
      }
    }

    return true;
  }

  @Override
  public void run () {
    if (isActive) {
      takeVideo();
    }
  }

  private float componentRotation;
  public void setComponentRotation (float componentRotation) {
    if (this.componentRotation != componentRotation) {
      this.componentRotation = componentRotation;
      setRotation(componentRotation);
      isZooming = false;
    }
  }

  private static void drawBitmap (Canvas c, Bitmap d, int cx, int cy, float alpha) {
    if (alpha == 0f)
      return;
    cx -= d.getWidth() / 2;
    cy -= d.getHeight() / 2;
    Paint paint = Paints.getXorPaint();
    paint.setAlpha((int) (255f * alpha));
    c.drawBitmap(d, cx, cy, paint);
    // d.draw(c, cx, cy, alpha);
  }

  @Override
  protected void onDraw (Canvas c) {
    int viewWidth = getMeasuredWidth();
    int viewHeight = getMeasuredHeight();

    int cx = viewWidth / 2;
    int cy = viewHeight / 2;

    /*if (componentRotation != 0f) {
      c.save();
      c.rotate(componentRotation, cx, cy);
    }*/

    if (parent.isInQrScanMode()) {
      drawBitmap(c, closeIcon, cx, cy, 1f - recordFactor / .5f);
      return;
    }

    if (recordFactor > .3f) {
      float alpha = ((recordFactor - .3f) / .7f);
      drawBitmap(c, stopIcon, cx, cy, alpha);
    }
    if (recordFactor <= .5f) {
      float alpha = 1f - recordFactor / .5f;
      drawBitmap(c, cameraIcon, cx, cy, alpha);
    }
    /*if (componentRotation != 0) {
      c.restore();
    }*/
  }

  // API

  public interface RecordListener {
    /**
     * Called when user requests to take photo shot.
     *
     * This method can be called instantly after {@link #onFinishVideoCapture(CameraButton)}
     * in cases when {@link CameraButton#setInRecordMode(boolean true)} has not been called.
     * */
    void onTakePicture (CameraButton v);

    /**
     * Called when user requests to start video capture.
     *
     * If method returns true, {@link #onFinishVideoCapture(CameraButton)} will be called later.
     * */
    boolean onStartVideoCapture (CameraButton v);

    /**
     * Called when user requested to stop video capture or in case when activity has been paused.
     * */
    void onFinishVideoCapture (CameraButton v);
  }

  @Nullable
  private RecordListener listener;

  public void setRecordListener (RecordListener listener) {
    this.listener = listener;
  }


}
