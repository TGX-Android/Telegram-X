package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

/**
 * Date: 3/23/18
 * Author: default
 */

public class TimerView extends NoScrollTextView implements FactorAnimator.Target, Handler.Callback {
  private final Handler handler = new Handler(this);
  private final BoolAnimator isVisible = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  public TimerView (Context context) {
    super(context);
    setTypeface(Fonts.getRobotoMedium());
    setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    setGravity(Gravity.CENTER);
    setPadding(0, 0, 0, Screen.dp(1f));
    setAlpha(0f);
  }

  @Override
  public boolean handleMessage (Message msg) {
    switch (msg.what) {
      case 0: {
        if (invalidateScheduled) {
          invalidateScheduled = false;
          invalidate();
        }
        break;
      }
      case 1: {
        if (textUpdateScheduled) {
          textUpdateScheduled = false;
          updateText();
        }
        break;
      }
    }
    return true;
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    setAlpha(factor);
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  private int livePeriod;
  private long aliveExpiresAt;

  public interface ActiveStateChangeListener {
    void onActiveStateChanged (TimerView v, boolean isActive);
  }

  private ActiveStateChangeListener listener;

  public void setListener (ActiveStateChangeListener listener) {
    this.listener = listener;
  }

  public boolean isTimerVisible () {
    return isVisible.getValue();
  }

  private void setIsActive (boolean isActive, boolean animated) {
    boolean nowIsVisible = this.isVisible.getValue();
    this.isVisible.setValue(isActive, animated);
    if (nowIsVisible != isActive) {
      if (listener != null) {
        listener.onActiveStateChanged(this, isActive);
      }
    }
  }

  public void setLivePeriod (int livePeriod, long expiresAt) {
    setIsActive(expiresAt > 0, false);
    this.livePeriod = livePeriod;
    this.aliveExpiresAt = expiresAt;

    if (invalidateScheduled || textUpdateScheduled) {
      invalidateScheduled = false;
      textUpdateScheduled = false;
      handler.removeCallbacksAndMessages(null);
    }

    updateText();
    invalidate();
  }

  private void updateText () {
    if (aliveExpiresAt == 0) {
      return;
    }
    long now = SystemClock.uptimeMillis();
    long millisRemaining = aliveExpiresAt - now;
    if (millisRemaining <= 0) {
      aliveExpiresAt = 0;
      setIsActive(false, false);
      return;
    }

    long timeTillNextTimerUpdate;
    String res;
    if (millisRemaining <= 60000) {
      int seconds = (int) (millisRemaining / 1000l);
      timeTillNextTimerUpdate = 1000; // - millisRemaining % 1000;
      res = Integer.toString(seconds);
    } else if (millisRemaining < 60000 * 60) {
      int minutes = (int) ((double) (millisRemaining / 1000l) / 60.0);
      timeTillNextTimerUpdate = 60000 - millisRemaining % 60000;
      res = Integer.toString(minutes);
    } else {
      int hours = (int) Math.ceil((double) (millisRemaining / 1000l / 60l) / 60.0);
      timeTillNextTimerUpdate = (60000 * 60) - millisRemaining % (60000 * 60);
      res = hours + "h";
    }

    setText(res);
    if (!textUpdateScheduled) {
      textUpdateScheduled = true;
      handler.sendMessageDelayed(Message.obtain(handler, 1), timeTillNextTimerUpdate);
    }
  }

  private boolean invalidateScheduled, textUpdateScheduled;

  @Override
  protected void onDraw (Canvas c) {
    if (livePeriod == 0) {
      super.onDraw(c);
      return;
    }

    final int centerX = getMeasuredWidth() / 2;
    final int centerY = getMeasuredHeight() / 2;

    int timerRadius = Screen.dp(12f);
    int color = Theme.progressColor();

    long millisRemaining = aliveExpiresAt - SystemClock.uptimeMillis();
    float doneFactor = MathUtils.clamp((float) ((double) millisRemaining / (double) (livePeriod * 1000l)));
    int strokeSize = Screen.dp(1.5f);

    int degrees = (int) (360f * doneFactor);
    if (degrees == 360) {
      c.drawCircle(centerX, centerY, timerRadius, Paints.getProgressPaint(color, strokeSize));
    } else {
      c.drawCircle(centerX, centerY, timerRadius, Paints.getProgressPaint(ColorUtils.alphaColor(.25f, color), strokeSize));
      RectF rectF = Paints.getRectF();
      rectF.set(centerX - timerRadius, centerY - timerRadius, centerX + timerRadius, centerY + timerRadius);
      c.drawArc(rectF, MathUtils.modulo((-90 + (360 - degrees)), 360), degrees, false, Paints.getProgressPaint(color, strokeSize));
    }

    if (!invalidateScheduled) {
      long delay = U.calculateDelayForDiameter(timerRadius * 2, (long) livePeriod * 1000l);
      invalidateScheduled = true;
      handler.sendMessageDelayed(Message.obtain(handler, 0), delay);
    }

    super.onDraw(c);
  }
}
