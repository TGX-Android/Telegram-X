package org.thunderdog.challegram.ui.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.MathUtils;

class CameraQrCodeRootLayout extends CameraRootLayout implements FactorAnimator.Target {
  private final static long ANIMATION_DURATION = 350L;
  private final static long RESET_DURATION = 150L;
  private final static long CONFIRMATION_DURATION = 750L;

  private final static int ANIMATOR_GENERAL = 0;
  private final static int ANIMATOR_STATUS = 3;
  private final static int ANIMATOR_RESET = 4;

  private final Paint dimmerPaint = new Paint();
  private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  private final QrBoxLocation initialLocation = new QrBoxLocation();
  private final QrBoxLocation initialCurrentLocation = new QrBoxLocation();
  private final QrBoxLocation currentLocation = new QrBoxLocation();
  private QrBoxLocation futureLocation;

  private final Path cornerTLPath = new Path();
  private final Path cornerTRPath = new Path();
  private final Path cornerBLPath = new Path();
  private final Path cornerBRPath = new Path();

  private final int cornerSize = Screen.dp(20);
  private int cameraViewWidth;
  private int cameraViewHeight;

  private final BoolAnimator qrFoundAnimator = new BoolAnimator(ANIMATOR_STATUS, this, AnimatorUtils.LINEAR_INTERPOLATOR, CONFIRMATION_DURATION, false);
  private final BoolAnimator resetAnimator = new BoolAnimator(ANIMATOR_RESET, this, AnimatorUtils.LINEAR_INTERPOLATOR, RESET_DURATION, false);
  private final BoolAnimator qrParamsAnimator = new BoolAnimator(ANIMATOR_GENERAL, this, AnimatorUtils.OVERSHOOT_INTERPOLATOR, ANIMATION_DURATION, false);

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

    float scaleX = (float) getWidth() / width;
    float scaleY = (float) getHeight() / height;

    Rect qrBounds = new Rect(
      (int) (boundingBox.left * scaleX),
      (int) (boundingBox.top * scaleY),
      (int) (boundingBox.right * scaleX),
      (int) (boundingBox.bottom * scaleY)
    );

    if (Settings.instance().getCameraAspectRatioMode() == Settings.CAMERA_RATIO_4_3) {
      int topPadding = (getHeight() - cameraViewHeight) / 2;
      scaleY = (float) cameraViewHeight / height;
      qrBounds.top = (int) (topPadding + (boundingBox.top * scaleY));
    }

    //Log.d("box: %s | scaleX: %s | scaleY: %s | preview: (%s x %s) | view: (%s x %s) | camera: (%s x %s)", qrBounds.toString(), scaleX, scaleY, width, height, getWidth(), getHeight(), cameraViewWidth, cameraViewHeight);

    animateQrLocation(qrBounds.left, qrBounds.top, (qrBounds.right - qrBounds.left) + cornerSize);
    resetAnimator.setValue(false, false);
    qrFoundAnimator.setValue(true, true);
  }

  @Override
  public void setQrMode (boolean qrMode) {
    this.qrMode = qrMode;
    currentLocation.copyFrom(initialLocation);
    invalidate();
  }

  @Override
  public void resetQrCorner () {
    resetAnimator.setValue(true, true);
  }

  private void animateQrLocation (float x, float y, float size) {
    initialCurrentLocation.copyFrom(currentLocation);
    futureLocation = new QrBoxLocation(x, y, size);
    qrParamsAnimator.setValue(false, false);
    qrParamsAnimator.setValue(true, true);
  }

  private void updateBoundingBoxPaths () {
    float x = currentLocation.x;
    float y = currentLocation.y;
    float size = currentLocation.size;

    cornerTLPath.reset();
    cornerTLPath.moveTo(x, y + cornerSize);
    cornerTLPath.lineTo(x, y);
    cornerTLPath.lineTo(x + cornerSize, y);

    cornerTRPath.reset();
    cornerTRPath.moveTo(x + size, y + cornerSize);
    cornerTRPath.lineTo(x + size, y);
    cornerTRPath.lineTo(x + size - cornerSize, y);

    cornerBLPath.reset();
    cornerBLPath.moveTo(x, y + size - cornerSize);
    cornerBLPath.lineTo(x, y + size);
    cornerBLPath.lineTo(x + cornerSize, y + size);

    cornerBRPath.reset();
    cornerBRPath.moveTo(x + size, y + size - cornerSize);
    cornerBRPath.lineTo(x + size, y + size);
    cornerBRPath.lineTo(x + size - cornerSize, y + size);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == ANIMATOR_GENERAL) {
      currentLocation.x = MathUtils.fromTo(initialCurrentLocation.x, futureLocation.x, factor);
      currentLocation.y = MathUtils.fromTo(initialCurrentLocation.y, futureLocation.y, factor);
      currentLocation.size = MathUtils.fromTo(initialCurrentLocation.size, futureLocation.size, factor);
      updateBoundingBoxPaths();
      invalidate();
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (id == ANIMATOR_STATUS && finalFactor == 1f) {
      ((CameraController) controller).onQrCodeFoundAndWaited();
      qrFoundAnimator.setValue(false, false);
      animateQrLocation(initialLocation.x, initialLocation.y, initialLocation.size);
    } else if (id == ANIMATOR_RESET && finalFactor == 1f) {
      qrFoundAnimator.setValue(false, false);
      animateQrLocation(initialLocation.x, initialLocation.y, initialLocation.size);
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
        updateBoundingBoxPaths();
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
      canvas.drawPath(cornerTLPath, cornerPaint);
      canvas.drawPath(cornerTRPath, cornerPaint);
      canvas.drawPath(cornerBLPath, cornerPaint);
      canvas.drawPath(cornerBRPath, cornerPaint);
    }

    return result;
  }

  private final static class QrBoxLocation {
    public float x;
    public float y;
    public float size;

    public QrBoxLocation () {

    }

    public QrBoxLocation (float x, float y, float size) {
      this.x = x;
      this.y = y;
      this.size = size;
    }

    public void set (float x, float y, float size) {
      this.x = x;
      this.y = y;
      this.size = size;
    }

    public void copyFrom (QrBoxLocation other) {
      set(other.x, other.y, other.size);
    }
  }
}
