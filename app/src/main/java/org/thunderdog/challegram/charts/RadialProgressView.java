package org.thunderdog.challegram.charts;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

public class RadialProgressView extends View {

  private long lastUpdateTime;
  private float radOffset;
  private float currentCircleLength;
  private boolean risingCircleLength;
  private float currentProgressTime;
  private RectF cicleRect = new RectF();
  private boolean useSelfAlpha;

  private int progressColor;

  private DecelerateInterpolator decelerateInterpolator;
  private AccelerateInterpolator accelerateInterpolator;
  private Paint progressPaint;
  private static final float rotationTime = 2000;
  private static final float risingTime = 500;
  private int size;

  public RadialProgressView(Context context) {
    super(context);

    size = Screen.dp(40);

    progressColor = Theme.progressColor();
    decelerateInterpolator = new DecelerateInterpolator();
    accelerateInterpolator = new AccelerateInterpolator();
    progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    progressPaint.setStyle(Paint.Style.STROKE);
    progressPaint.setStrokeCap(Paint.Cap.ROUND);
    progressPaint.setStrokeWidth(Screen.dp(3));
    progressPaint.setColor(progressColor);
  }

  public void setUseSelfAlpha(boolean value) {
    useSelfAlpha = value;
  }

  @Override
  public void setAlpha(float alpha) {
    super.setAlpha(alpha);
    if (useSelfAlpha) {
      Drawable background = getBackground();
      int a = (int) (alpha * 255);
      if (background != null) {
        background.setAlpha(a);
      }
      progressPaint.setAlpha(a);
    }
  }

  private void updateAnimation() {
    long newTime = System.currentTimeMillis();
    long dt = newTime - lastUpdateTime;
    if (dt > 17) {
      dt = 17;
    }
    lastUpdateTime = newTime;

    radOffset += 360 * dt / rotationTime;
    int count = (int) (radOffset / 360);
    radOffset -= count * 360;

    currentProgressTime += dt;
    if (currentProgressTime >= risingTime) {
      currentProgressTime = risingTime;
    }
    if (risingCircleLength) {
      currentCircleLength = 4 + 266 * accelerateInterpolator.getInterpolation(currentProgressTime / risingTime);
    } else {
      currentCircleLength = 4 - 270 * (1.0f - decelerateInterpolator.getInterpolation(currentProgressTime / risingTime));
    }
    if (currentProgressTime == risingTime) {
      if (risingCircleLength) {
        radOffset += 270;
        currentCircleLength = -266;
      }
      risingCircleLength = !risingCircleLength;
      currentProgressTime = 0;
    }
    invalidate();
  }

  public void setSize(int value) {
    size = value;
    invalidate();
  }

  public void setStrokeWidth(float value) {
    progressPaint.setStrokeWidth(Screen.dp(value));
  }

  public void setProgressColor(int color) {
    progressColor = color;
    progressPaint.setColor(progressColor);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    int x = (getMeasuredWidth() - size) / 2;
    int y = (getMeasuredHeight() - size) / 2;
    cicleRect.set(x, y, x + size, y + size);
    canvas.drawArc(cicleRect, radOffset, currentCircleLength, false, progressPaint);
    updateAnimation();
  }
}
