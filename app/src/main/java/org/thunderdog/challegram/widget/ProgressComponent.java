package org.thunderdog.challegram.widget;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.TimerParticles;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.MultipleViewProvider;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.Destroyable;

/**
 * Date: 11/11/2016
 * Author: default
 */

public class ProgressComponent implements Runnable, FactorAnimator.Target, Destroyable, BaseActivity.SimpleStateListener {
  public static boolean USE_ROUNDED_CAP = true;

  private static final float MIN_DEGREES_BIG = USE_ROUNDED_CAP ? 8f : 32f;
  private static final float MIN_DEGREES = 25f;
  private static final float MIN_DEGREES_10DP = 10f;

  private static final long TOTAL_ANIMATION_TIME = 1600l;
  private static final long REPEAT_TIME = TOTAL_ANIMATION_TIME * 20;
  private static final long TIME_PER_SWEEP = 600l;
  private static final long TIME_BETWEEN_SWEEP = (TOTAL_ANIMATION_TIME - TIME_PER_SWEEP * 2) / 2;

  private long totalAnimationTime = TOTAL_ANIMATION_TIME;
  private long timeBetweenSweep = TIME_BETWEEN_SWEEP;
  private long repeatTime = REPEAT_TIME;
  private long timePerSweep = TIME_PER_SWEEP;

  private int radius;
  private final Rect drawingRect;
  private final RectF arcRect;

  private int color;
  private boolean colorForced;
  private float alpha = 1f;
  private final MultipleViewProvider currentViews;
  private ViewProvider viewProvider;
  private long startTime;
  private boolean monotonic;

  private boolean useLargerPaint;
  private float strokeWidth;

  private final BaseActivity context;
  private boolean uiResumed = true;

  private long timerStartMs = -1, timerEndMs = -1;

  public ProgressComponent (BaseActivity context, int radius) {
    this.radius = radius;
    this.drawingRect = new Rect();
    this.arcRect = new RectF();
    this.currentViews = new MultipleViewProvider();
    this.viewProvider = currentViews;
    this.context = context;
    /*context.addSimpleStateListener(this);
    this.uiResumed = context.getActivityState() == UI.STATE_RESUMED;*/
  }

  @Override
  public void onActivityStateChanged (BaseActivity activity, int newState, int prevState) {
    setUiResumed(newState == UI.STATE_RESUMED);
  }

  @Override
  public void performDestroy () {
    context.removeSimpleStateListener(this);
    setUiResumed(false);
  }

  public void setViewProvider (@Nullable ViewProvider viewProvider) {
    this.viewProvider = viewProvider != null ? viewProvider : currentViews;
  }

  public void setRadius (int radius) {
    if (this.radius != radius) {
      this.radius = radius;
      setBounds(drawingRect.left, drawingRect.top, drawingRect.right, drawingRect.bottom);
    }
  }

  public void setDurations (long totalTime, long timePerSweep) {
    this.totalAnimationTime = totalTime;
    this.timePerSweep = timePerSweep;
    this.timeBetweenSweep = (totalAnimationTime - timePerSweep * 2) / 2;
    setRepeatTime(totalAnimationTime * 10);
  }

  private boolean isPrecise;

  public void setIsPrecise () {
    this.isPrecise = true;
    this.repeatTime = 1800l;
  }

  public void setRepeatTime (long repeatTime) {
    this.repeatTime = repeatTime;
  }

  public void setSlowerDurations () {
    setDurations(1700l, 600);
  }

  public void setUseLargerPaint () {
    setUseLargerPaint(Screen.dp(2f));
  }

  public void setUseLargerPaint (float strokeWidth) {
    this.useLargerPaint = true;
    this.strokeWidth = strokeWidth;
  }

  public void setBounds (int left, int top, int right, int bottom) {
    drawingRect.set(left, top, right, bottom);

    int centerX = drawingRect.centerX();
    int centerY = drawingRect.centerY();
    arcRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
  }

  public void forceColor (int color) {
    if (this.color != color) {
      boolean prevIsActive = isActive();
      this.color = color;
      this.colorForced = true;
      scheduleInvalidateIfNeeded(false, prevIsActive);
    }
  }

  private void setUiResumed (boolean isResumed) {
    if (this.uiResumed != isResumed) {
      boolean prevIsActive = isActive();
      this.uiResumed = isResumed;
      scheduleInvalidateIfNeeded(false, prevIsActive);
    }
  }

  public float getAlpha () {
    return alpha;
  }

  public void setAlpha (float alpha) {
    if (this.alpha != alpha) {
      boolean prevIsActive = isActive();
      this.alpha = alpha;
      scheduleInvalidateIfNeeded(false, prevIsActive);
    }
  }

  public void attachToView (@Nullable View view) {
    boolean prevIsActive = isActive();
    this.currentViews.attachToView(view);
    scheduleInvalidateIfNeeded(false, prevIsActive);
  }

  public void detachFromView (@Nullable View view) {
    this.currentViews.detachFromView(view);
  }

  private boolean isActive () {
    return viewProvider.hasAnyTargetToInvalidate() && (Color.alpha(color) > 0 || !colorForced) && uiResumed;
  }

  private boolean useStupidInvalidate;

  public void setUseStupidInvalidate () {
    this.useStupidInvalidate = true;
  }

  private int getColor () {
    return ColorUtils.alphaColor(alpha, colorForced ? color : Theme.progressColor());
  }

  public void restartAnimationIfNeeded () {
    scheduleInvalidateIfNeeded(false, true /*unknown, actually*/);
  }

  private boolean scheduled;

  private void scheduleInvalidateIfNeeded (boolean byMyself, boolean prevIsActive) {
    if (viewProvider.hasAnyTargetToInvalidate()) {
      if (isActive() && !prevIsActive) {
        startTime = SystemClock.uptimeMillis();
      }
      if (!byMyself) {
        invalidate();
      }
      if (Color.alpha(getColor()) > 0) {
        if (!byMyself || scheduled) {
          UI.getProgressHandler().removeCallbacks(this);
        }
        scheduled = true;
        UI.getProgressHandler().postDelayed(this, monotonic ? 3 : getFrameDelay());
      }
    }
  }

  private static long getFrameDelay () {
    return Math.max(8, ValueAnimator.getFrameDelay());
  }

  private void invalidate () {
    if (useStupidInvalidate) {
      viewProvider.invalidate();
    } else {
      viewProvider.invalidate(drawingRect);
    }
  }

  @Override
  public void run () {
    scheduled = false;
    invalidate();
  }

  // Progress utils

  private static final int PRECISE_ANIMATOR = 0;
  private float preciseFactor;
  private FactorAnimator preciseAnimator;
  private static final Interpolator DECELERATE_INTERPOLATOR = new DecelerateInterpolator(.72f);

  public void setProgress (float progress, boolean animated) {
    progress = Math.min(1f, Math.max(0f, progress));
    if (animated && isActive() && (preciseFactor != progress || preciseAnimator != null)) {
      if (preciseAnimator == null) {
        preciseAnimator = new FactorAnimator(PRECISE_ANIMATOR, this, DECELERATE_INTERPOLATOR, 180l, preciseFactor); // 400
      }
      preciseAnimator.animateTo(progress);
    } else {
      if (preciseAnimator != null) {
        preciseAnimator.cancel();
        preciseAnimator.forceFactor(progress);
      }
      this.preciseFactor = progress;
      invalidate();
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case PRECISE_ANIMATOR: {
        setPreciseFactor(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  private void setPreciseFactor (float factor) {
    if (this.preciseFactor != factor) {
      this.preciseFactor = factor;
      // invalidate();
      // No invalidate since it's scheduled?
    }
  }

  public void setMonotonic (boolean isMonotonic) {
    this.monotonic = isMonotonic;
  }

  private boolean noStartDelay;

  public void setNoStartDelay (boolean noStartDelay) {
    this.noStartDelay = noStartDelay;
  }

  public void setTimer (long fromDate, long toDate) {
    long date = System.currentTimeMillis();
    long toDiff = toDate - date;
    long fromDiff = fromDate - date;

    long now = SystemClock.uptimeMillis();

    this.timerStartMs = now + fromDiff;
    this.timerEndMs = now + toDiff;
  }

  private boolean isTimer () {
    return timerStartMs != -1 && timerEndMs != -1;
  }

  public long getTimerRemainingTimeMillis () {
    return (long) ((float) (timerEndMs - timerStartMs) * getTimerValue());
  }

  private float getTimerValue () {
    long now = SystemClock.uptimeMillis();
    if (now <= timerStartMs) {
      return 1f;
    } else if (now >= timerEndMs) {
      return 0f;
    } else {
      now -= timerStartMs;
      return 1f - ((float) now / (float) (timerEndMs - timerStartMs));
    }
  }

  // Drawing

  private TimerParticles timerParticles;

  public void draw (Canvas c) {
    if (alpha <= 0f)
      return;

    final long timeMs = (monotonic ? SystemClock.uptimeMillis() : SystemClock.uptimeMillis() - startTime) + (noStartDelay ? timeBetweenSweep + timePerSweep + timeBetweenSweep : 0);
    final float startAngle, sweepAngle;
    final boolean isTimer = isTimer();

    if (isTimer) {
      sweepAngle = 360f * getTimerValue();
      startAngle = 270f - sweepAngle;
    } else if (isPrecise) {
      startAngle = (360f * ((float) (timeMs % repeatTime) / (float) repeatTime)) % 360f;
      sweepAngle = MIN_DEGREES_10DP + (360f - MIN_DEGREES_10DP) * preciseFactor;
    } else {
      final long time = timeMs % totalAnimationTime;

      final long timeStart1 = timeBetweenSweep;
      final long timeStart2 = timePerSweep + timeBetweenSweep + timeBetweenSweep;

      final float factor1 = time < timeStart1 ? 0f : time > timeStart1 + timePerSweep ? 1f : AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation((float) (time - timeStart1) / (float) timePerSweep);
      final float factor2 = time < timeStart2 ? 0f : time > timeStart2 + timePerSweep ? 1f : AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation((float) (time - timeStart2) / (float) timePerSweep);

      startAngle = (270f * factor2 + (360f + 90f) * ((float) time / (float) totalAnimationTime) + (360f * ((float) (timeMs % repeatTime) / (float) repeatTime))) % 360f;
      sweepAngle = (radius >= Screen.dp(18f) ? MIN_DEGREES_BIG : radius >= Screen.dp(10f) ? MIN_DEGREES_10DP : MIN_DEGREES) + factor1 * 270f * (1f - factor2);
    }

    final int color = ColorUtils.alphaColor(alpha, colorForced ? this.color : Theme.progressColor());
    c.drawArc(arcRect, startAngle, sweepAngle, false, useLargerPaint ? Paints.getProgressPaint(color, strokeWidth) : Paints.getProgressOuterPaint(color));

    if (isTimer) {
      float finalAngle = MathUtils.modulo(startAngle, 360);
      double rad = Math.toRadians(finalAngle);
      double sin = Math.sin(rad);
      double cos = Math.cos(rad);

      float cx = (float) (arcRect.centerX() + arcRect.width() / 2f * cos);
      float cy = (float) (arcRect.centerY() + arcRect.height() / 2f * sin);

      c.drawCircle(cx, cy, strokeWidth / 2f, Paints.fillingPaint(color));
      if (timerParticles == null) {
        timerParticles = new TimerParticles();
      }
      timerParticles.draw(c, color, strokeWidth, arcRect, 360f - sweepAngle, alpha);
    }

    if (!scheduled && isActive()) {
      scheduleInvalidateIfNeeded(true, true);
    }
  }

  public static ProgressComponent simpleInstance (View view, float radiusDp, int left, int top, int width, int height) {
    ProgressComponent progress = new ProgressComponent(UI.getContext(view.getContext()), Screen.dp(radiusDp));
    progress.setUseLargerPaint();
    progress.setSlowerDurations();
    progress.forceColor(0xffffffff);
    progress.setAlpha(0f);
    progress.setBounds(left, top, left + width, top + height);
    progress.attachToView(view);
    return progress;
  }
}
