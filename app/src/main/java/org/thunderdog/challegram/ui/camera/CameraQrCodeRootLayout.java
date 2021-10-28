package org.thunderdog.challegram.ui.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.MathUtils;

class CameraQrCodeRootLayout extends CameraRootLayout implements FactorAnimator.Target {
  private final static long ANIMATION_DURATION = 350L;
  private final static long RESET_DURATION = 150L;
  private final static long RESET_DURATION_LEGACY = 250L;
  private final static long CONFIRMATION_DURATION = 750L;

  private final static int ANIMATOR_GENERAL = 0;
  private final static int ANIMATOR_STATUS = 1;
  private final static int ANIMATOR_RESET = 2;
  private final static int ANIMATOR_QR_TEXT = 3;

  private final Paint dimmerPaint = new Paint();
  private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint dbgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  private final QrBoxLocation initialLocation = new QrBoxLocation();
  private final QrBoxLocation initialCurrentLocation = new QrBoxLocation();
  private final QrBoxLocation currentLocation = new QrBoxLocation();
  private QrBoxLocation futureLocation;

  private final Path cornerTLPath = new Path();
  private final Path cornerTRPath = new Path();
  private final Path cornerBLPath = new Path();
  private final Path cornerBRPath = new Path();

  private Rect guideLinePart1, guideLinePart2, guideLinePart3;
  private RectF dbgBox, dbgBox2;

  private final int cornerSize = Screen.dp(20);
  private int cameraViewWidth;
  private int cameraViewHeight;
  private int cameraScaledWidth;

  private final BoolAnimator qrFoundAnimator = new BoolAnimator(ANIMATOR_STATUS, this, AnimatorUtils.LINEAR_INTERPOLATOR, CONFIRMATION_DURATION, false);
  private final BoolAnimator qrParamsAnimator = new BoolAnimator(ANIMATOR_GENERAL, this, AnimatorUtils.OVERSHOOT_INTERPOLATOR, ANIMATION_DURATION, false);
  private final BoolAnimator resetAnimator = new BoolAnimator(ANIMATOR_RESET, this, AnimatorUtils.LINEAR_INTERPOLATOR, RESET_DURATION, false);
  private final BoolAnimator qrTextAnimator = new BoolAnimator(ANIMATOR_QR_TEXT, this, AnimatorUtils.OVERSHOOT_INTERPOLATOR, CONFIRMATION_DURATION, true);

  private Text qrTextTitle;
  private Text qrTextSubtitle;
  private Text qrTextDebug;
  private float qrTextAlpha = 1f;
  private float qrRotation = 0;

  private boolean qrMode, qrModeClosing, qrModeInvertedOrientation, qrModePortrait;
  private boolean qrDebugRegions;
  private int qrSubtitle;

  public CameraQrCodeRootLayout (@NonNull Context context) {
    super(context);
    dimmerPaint.setColor(0x7f000000);
    cornerPaint.setColor(Theme.getColor(R.id.theme_color_white));
    cornerPaint.setStyle(Paint.Style.STROKE);
    cornerPaint.setStrokeWidth(Screen.dp(2));
    cornerPaint.setStrokeJoin(Paint.Join.ROUND);
    dbgPaint.setColor(Color.RED);
    dbgPaint.setStyle(Paint.Style.STROKE);
    dbgPaint.setStrokeWidth(Screen.dp(2));
    dbgPaint.setStrokeJoin(Paint.Join.ROUND);
  }

  private void updateTexts (int width) {
    TextColorSet forceWhite = () -> Color.WHITE;
    qrTextTitle = new Text.Builder(Lang.getString(R.string.ScanQRFullTitle), width, Paints.robotoStyleProvider(31), forceWhite).allBold().addFlags(Text.FLAG_ALIGN_CENTER).singleLine().build();
    qrTextSubtitle = new Text.Builder(Lang.getString(qrSubtitle == 0 ? R.string.ScanQRFullSubtitle : qrSubtitle), width, Paints.robotoStyleProvider(16), forceWhite).addFlags(Text.FLAG_ALIGN_CENTER).maxLineCount(2).build();
  }

  @Override
  public void setQrCorner (RectF boundingBox, int height, int width, int rotation, boolean isLegacyZxing) {
    if (qrModeClosing) {
      return;
    }

    resetAnimator.setValue(false, false);
    resetAnimator.setDuration(isLegacyZxing ? RESET_DURATION_LEGACY : RESET_DURATION);

    if (boundingBox == null) {
      qrFoundAnimator.setValue(true, false);
      return;
    }

    float scaleX = (float) getWidth() / width;
    float scaleY = (float) getHeight() / height;
    //Log.e("sX %s sY %s max %s [view %s x %s, preview %s x %s] == src %s -> dst %s", scaleX, scaleY, scale, getHeight(), getWidth(), height, width, boundingBox, qrBounds);

    float nx = getWidth() / 2f + (boundingBox.left - width / 2f);
    float nx2 = getWidth() / 2f + (boundingBox.right - width / 2f);
    float ny = getHeight() / 2f + (boundingBox.top - height / 2f);
    float qrSize;
    if (Settings.instance().getCameraAspectRatioMode() == Settings.CAMERA_RATIO_FULL_SCREEN) {
      scaleX = (float) cameraScaledWidth / width;
      RectF bbx = new RectF(
              (boundingBox.left * scaleX),
              (boundingBox.top * scaleY),
              (boundingBox.right * scaleX),
              (boundingBox.bottom * scaleY)
      );
      ny = bbx.top;
      qrSize = Math.max(bbx.width(), bbx.height());
    } else {
      qrSize = (nx2 - nx);
    }

    if (qrDebugRegions) {
      qrTextDebug = new Text.Builder(Lang.formatString("camera = %s x %s, view = %s x %s, cameraView = %s x %s\naspect = %s, zxing = %s\nsx: %s, sy: %s\nX: %s, Y: %s, size: %s\nSource bounds: %s (width: %s)", null, height, width, getHeight(), getWidth(), cameraViewHeight, cameraViewWidth, Settings.instance().getCameraAspectRatioMode(), isLegacyZxing, scaleX, scaleY, nx, ny, qrSize, boundingBox, boundingBox.width()).toString(), getWidth(), Paints.robotoStyleProvider(14), () -> Color.RED).build();
      dbgBox = boundingBox;
      dbgBox2 = new RectF(
              (boundingBox.left * scaleX),
              (boundingBox.top * scaleY),
              (boundingBox.right * scaleX),
              (boundingBox.bottom * scaleY)
      );
    }

    animateQrLocation(nx, ny, qrSize);
    qrFoundAnimator.setValue(true, true);
    qrTextAnimator.setValue(false, true);
  }

  @Override
  public void setQrMode (boolean qrMode, boolean qrModeDebug) {
    this.qrMode = qrMode;
    qrDebugRegions = qrModeDebug || Settings.instance().needShowQrRegions();
    qrModeClosing = false;
    currentLocation.set(0, 0, 0);
    invalidate();
  }

  @Override
  public void setQrModeSubtitle (int subtitleRes) {
    this.qrSubtitle = subtitleRes;
    if (qrTextTitle != null) {
      updateTexts(qrTextTitle.getMaxWidth());
    }
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
    } else if (id == ANIMATOR_QR_TEXT) {
      qrTextAlpha = MathUtils.clamp(factor);
      invalidate();
    } else if (id == ANIMATOR_STATUS && factor > 0.25f && !qrTextAnimator.isAnimating()) {
      //qrTextAnimator.setValue(false, true);
    }
  }

  @Override
  public void onCameraClosed () {
    qrModeClosing = true;
    qrFoundAnimator.setValue(false, false);
    qrParamsAnimator.setValue(false, false);
    qrTextAnimator.setValue(true, true);
    currentLocation.set(initialLocation.x, initialLocation.y, initialLocation.size);
    updateBoundingBoxPaths();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (id == ANIMATOR_STATUS && finalFactor == 1f) {
      if (!Settings.instance().needDisableQrProcessing()) {
        ((CameraController) controller).onQrCodeFoundAndWaited();
        qrFoundAnimator.setValue(false, false);
      }
    } else if (id == ANIMATOR_RESET && finalFactor == 1f) {
      qrFoundAnimator.setValue(false, false);
      qrTextAnimator.setValue(true, true);
      animateQrLocation(initialLocation.x, initialLocation.y, initialLocation.size);
    }
  }

  private int getDisplayRotation () {
    return controller.context().getWindowRotationDegrees();
  }

  @Override
  protected boolean drawChild (Canvas canvas, View child, long drawingTime) {
    boolean result = super.drawChild(canvas, child, drawingTime);

    if (child instanceof CameraLayout && qrMode) {
      // X, Y defines center point - where size defines distance between center point and corner
      Rect activeGuideLinePart1, activeGuideLinePart3;

      float size, x, y;
      if (guideLinePart1 == null || guideLinePart2 == null || guideLinePart3 == null || currentLocation.size == 0) {
        qrModeInvertedOrientation = getDisplayRotation() == 90;
        qrModePortrait = getDisplayRotation() == 0;

        int buttonRegion = Screen.dp(80f + 56f);

        switch (getDisplayRotation()) {
          case 90: {
            guideLinePart3 = new Rect(canvas.getWidth() - buttonRegion, 0, canvas.getWidth(), canvas.getHeight());
            guideLinePart1 = new Rect(0, 0, ((canvas.getWidth() - guideLinePart3.width()) / 2) - Screen.getStatusBarHeight(), canvas.getHeight());
            guideLinePart2 = new Rect(guideLinePart1.right, 0, guideLinePart3.left, canvas.getHeight());
            break;
          }

          case 270: {
            guideLinePart1 = new Rect(0, 0, buttonRegion, canvas.getHeight());
            guideLinePart2 = new Rect(guideLinePart1.right, 0, (int) (guideLinePart1.right + ((canvas.getWidth() - guideLinePart1.right) / 2f)) + Screen.getStatusBarHeight(), canvas.getHeight());
            guideLinePart3 = new Rect(guideLinePart2.right, 0, canvas.getWidth(), canvas.getHeight());
            break;
          }

          default: {
            guideLinePart1 = new Rect(0, canvas.getHeight() - buttonRegion, canvas.getWidth(), canvas.getHeight());
            guideLinePart3 = new Rect(0, 0, canvas.getWidth(), (canvas.getHeight() - guideLinePart1.height()) / 2);
            guideLinePart2 = new Rect(0, guideLinePart3.bottom, canvas.getWidth(), guideLinePart1.top);
            break;
          }
        }

        if (qrModePortrait) {
          initialLocation.size = size = (int) (Math.min(child.getWidth(), child.getHeight()) / 1.5f);
          initialLocation.x = x = (getWidth() - size) / 2;
          initialLocation.y = y = (getHeight() - size) / 2;
          updateTexts((int) (guideLinePart3.width() / 1.5f));
        } else {
          initialLocation.size = size = guideLinePart2.width() / 1.5f;
          initialLocation.x = x = guideLinePart2.left + (size / 4);
          initialLocation.y = y = guideLinePart2.top + (size / 4);
          updateTexts((qrModeInvertedOrientation ? guideLinePart1 : guideLinePart3).width());
        }

        currentLocation.copyFrom(initialLocation);
        View possibleCameraView = ((CameraLayout) child).getChildAt(0);
        if (possibleCameraView instanceof CameraTextureView) {
          cameraScaledWidth = ((CameraTextureView) possibleCameraView).scaledImageWidth;
        }
        updateBoundingBoxPaths();
      } else {
        size = currentLocation.size;
        x = currentLocation.x;
        y = currentLocation.y;
      }

      if (qrModeInvertedOrientation) {
        activeGuideLinePart1 = guideLinePart3;
        activeGuideLinePart3 = guideLinePart1;
      } else {
        activeGuideLinePart1 = guideLinePart1;
        activeGuideLinePart3 = guideLinePart3;
      }

      // draw surrounding boxes to create dimming around the box
      canvas.drawRect(0, 0, canvas.getWidth(), y, dimmerPaint);
      canvas.drawRect(0, y, x, getHeight(), dimmerPaint);
      canvas.drawRect(x, y + size, x + size, getHeight(), dimmerPaint);
      canvas.drawRect(x + size, y, canvas.getWidth(), getHeight(), dimmerPaint);

      // draw corners
      canvas.drawPath(cornerTLPath, cornerPaint);
      canvas.drawPath(cornerTRPath, cornerPaint);
      canvas.drawPath(cornerBLPath, cornerPaint);
      canvas.drawPath(cornerBRPath, cornerPaint);

      if (qrTextTitle != null && qrTextSubtitle != null) {
        int titleTextSize = Screen.dp(31);
        int vertPadding = Screen.dp(6);
        int yBaseline = (qrModePortrait) ? (int) (initialLocation.y - titleTextSize * 4) : activeGuideLinePart3.centerY();
        canvas.save();
        canvas.rotate(qrRotation, activeGuideLinePart3.centerX(), yBaseline);
        qrTextTitle.draw(canvas, activeGuideLinePart3.left, activeGuideLinePart3.right, 0, yBaseline - titleTextSize - vertPadding, null, qrTextAlpha);
        qrTextSubtitle.draw(canvas, activeGuideLinePart3.left, activeGuideLinePart3.right, 0, yBaseline + vertPadding, null, qrTextAlpha);
        //if (qrDebugRegions) {
        //  dbgPaint.setColor(Color.MAGENTA);
        //  canvas.drawRect(activeGuideLinePart3.left, yBaseline - titleTextSize - vertPadding, activeGuideLinePart3.right, yBaseline + qrTextSubtitle.getHeight() + vertPadding, dbgPaint);
        //  dbgPaint.setColor(Theme.getColor(R.id.theme_color_textNegative));
        //}
        canvas.restore();
      }

      if (qrDebugRegions) {
        //canvas.drawRect(activeGuideLinePart1, dbgPaint);
        //canvas.drawRect(guideLinePart2, dbgPaint);
        //canvas.drawRect(activeGuideLinePart3, dbgPaint);
        if (qrTextDebug != null) {
          qrTextDebug.draw(canvas, 0, Screen.getStatusBarHeight());
        }

        if (dbgBox != null) {
          dbgPaint.setColor(Color.GREEN);
          canvas.drawRect(dbgBox, dbgPaint);
          dbgPaint.setColor(Color.BLUE);
          canvas.drawRect(dbgBox2, dbgPaint);
          dbgPaint.setColor(Color.RED);
        }
      }
    }

    return result;
  }

  @Override
  public void setComponentRotation (float rotation) {
    //Log.e("rotate %s", rotation);
    qrRotation = rotation;
    invalidate();
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
