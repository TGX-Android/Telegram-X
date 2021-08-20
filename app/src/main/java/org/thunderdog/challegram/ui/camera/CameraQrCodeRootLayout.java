package org.thunderdog.challegram.ui.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

class CameraQrCodeRootLayout extends CameraRootLayout implements FactorAnimator.Target {
  private final static Interpolator ANIMATION_INTERPOLATOR = AnimatorUtils.OVERSHOOT_INTERPOLATOR;
  private final static long ANIMATION_DURATION = 250L;
  private final static long CONFIRMATION_DURATION = 750L;

  private final static int ANIMATOR_LOCATION_X = 0;
  private final static int ANIMATOR_LOCATION_Y = 1;
  private final static int ANIMATOR_SIZE = 2;
  private final static int ANIMATOR_STATUS = 3;

  private final int cornerSize = Screen.dp(20);
  private final Paint dimmerPaint = new Paint();
  private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path cornerPath = new Path();

  private final QrBoxLocation currentLocation = new QrBoxLocation();
  private final QrBoxLocation initialLocation = new QrBoxLocation();

  private int cameraViewWidth;
  private int cameraViewHeight;

  private final BoolAnimator qrFoundAnimator = new BoolAnimator(ANIMATOR_STATUS, this, ANIMATION_INTERPOLATOR, CONFIRMATION_DURATION, false);
  private FactorAnimator sizeChangeAnimator;
  private FactorAnimator locationChangeAnimatorX;
  private FactorAnimator locationChangeAnimatorY;

  private boolean qrMode;

  public CameraQrCodeRootLayout (@NonNull Context context) {
    super(context);
    dimmerPaint.setColor(0x7f000000);
    cornerPaint.setColor(Theme.getColor(R.id.theme_color_white));
    cornerPaint.setStyle(Paint.Style.STROKE);
    cornerPaint.setStrokeWidth(Screen.dp(2));
    cornerPaint.setStrokeJoin(Paint.Join.ROUND);
  }

  @Override
  public void setQrCorner (Rect boundingBox, int height, int width) {
    if (boundingBox == null) {
      qrFoundAnimator.setValue(true, false);
      return;
    }

    float scaleX = (float) cameraViewWidth / width;
    float scaleY = (float) cameraViewHeight / height;

    Rect qrBounds = new Rect(
            (int) (boundingBox.left * scaleX),
            (int) (boundingBox.top * scaleY),
            (int) (boundingBox.right * scaleX),
            (int) (boundingBox.bottom * scaleY)
    );

    //Log.d("box: %s | scaleX: %s | scaleY: %s | preview: (%s x %s) | view: (%s x %s) | camera: (%s x %s)", qrBounds.toString(), scaleX, scaleY, width, height, getWidth(), getHeight(), cameraViewWidth, cameraViewHeight);

    qrFoundAnimator.setValue(true, true);
    animateQrLocation(qrBounds.left, qrBounds.top, (qrBounds.right - qrBounds.left) + cornerSize);
  }

  @Override
  public void setQrMode (boolean qrMode) {
    this.qrMode = qrMode;
    currentLocation.copyFrom(initialLocation);
    invalidate();
  }

  @Override
  public void resetQrCorner () {
    qrFoundAnimator.setValue(false, false);
    animateQrLocation(initialLocation.x, initialLocation.y, initialLocation.size);
  }

  private void animateQrLocation (float x, float y, float size) {
    if (locationChangeAnimatorX == null) {
      locationChangeAnimatorX = createFactorAnimator(ANIMATOR_LOCATION_X, currentLocation.x);
    }

    if (locationChangeAnimatorY == null) {
      locationChangeAnimatorY = createFactorAnimator(ANIMATOR_LOCATION_Y, currentLocation.y);
    }

    if (sizeChangeAnimator == null) {
      sizeChangeAnimator = createFactorAnimator(ANIMATOR_SIZE, currentLocation.size);
    }

    locationChangeAnimatorX.animateTo(x);
    locationChangeAnimatorY.animateTo(y);
    sizeChangeAnimator.animateTo(size);
  }

  private FactorAnimator createFactorAnimator (int id, float initialValue) {
    return new FactorAnimator(id, this, ANIMATION_INTERPOLATOR, ANIMATION_DURATION, initialValue);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_LOCATION_X:
        currentLocation.x = factor;
        break;
      case ANIMATOR_LOCATION_Y:
        currentLocation.y = factor;
        break;
      case ANIMATOR_SIZE:
        currentLocation.size = factor;
        break;
    }

    invalidate();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (id == ANIMATOR_STATUS && finalFactor == 1f) {
      ((CameraController) controller).onQrCodeFoundAndWaited();
      qrFoundAnimator.setValue(false, false);
    }
  }

  @Override
  protected boolean drawChild (Canvas canvas, View child, long drawingTime) {
    boolean result = super.drawChild(canvas, child, drawingTime);
    if (child instanceof CameraLayout && qrMode) {
      // X, Y defines center point - where size defines distance between center point and corner
      float size, x, y;
      if (currentLocation.size == 0) {
        currentLocation.size = initialLocation.size = size = (int) (Math.min(child.getWidth(), child.getHeight()) / 1.5f);
        currentLocation.x = initialLocation.x = x = (getWidth() - size) / 2;
        currentLocation.y = initialLocation.y = y = (getHeight() - size) / 2;
        cameraViewWidth = child.getWidth();
        cameraViewHeight = child.getHeight();
      } else {
        size = currentLocation.size;
        x = currentLocation.x;
        y = currentLocation.y;
      }

      // draw surrounding boxes to create dimming around the box
      canvas.drawRect(0, 0, child.getMeasuredWidth(), y, dimmerPaint);
      canvas.drawRect(0, y, x, getHeight(), dimmerPaint);
      canvas.drawRect(x, y + size, x + size, getHeight(), dimmerPaint);
      canvas.drawRect(x + size, y, child.getMeasuredWidth(), getHeight(), dimmerPaint);

      // draw corners
      cornerPath.reset();
      cornerPath.moveTo(x, y + cornerSize);
      cornerPath.lineTo(x, y);
      cornerPath.lineTo(x + cornerSize, y);
      canvas.drawPath(cornerPath, cornerPaint);

      cornerPath.reset();
      cornerPath.moveTo(x + size, y + cornerSize);
      cornerPath.lineTo(x + size, y);
      cornerPath.lineTo(x + size - cornerSize, y);
      canvas.drawPath(cornerPath, cornerPaint);

      cornerPath.reset();
      cornerPath.moveTo(x, y + size - cornerSize);
      cornerPath.lineTo(x, y + size);
      cornerPath.lineTo(x + cornerSize, y + size);
      canvas.drawPath(cornerPath, cornerPaint);

      cornerPath.reset();
      cornerPath.moveTo(x + size, y + size - cornerSize);
      cornerPath.lineTo(x + size, y + size);
      cornerPath.lineTo(x + size - cornerSize, y + size);
      canvas.drawPath(cornerPath, cornerPaint);
    }

    return result;
  }

  private final static class QrBoxLocation {
    public float x;
    public float y;
    public float size;

    public void set(float x, float y, float size) {
      this.x = x;
      this.y = y;
      this.size = size;
    }

    public void copyFrom(QrBoxLocation other) {
      set(other.x, other.y, other.size);
    }
  }
}
