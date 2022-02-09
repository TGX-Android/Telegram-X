package me.vkryl.android.animator;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.FloatRange;
import androidx.annotation.Nullable;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.core.BitwiseUtils;

/**
 * Date: 26/10/2016
 * Author: default
 */

public class FactorAnimator {
  public interface Target {
    void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee);
    default void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }
  }

  public interface StartHelper {
    void startAnimatorOnLayout (FactorAnimator animator, float toFactor);
  }

  private final int id;
  private final Target target;
  private Interpolator interpolator;
  private long duration, startDelay;

  private int intValue;
  private long longValue;
  private Object objValue;

  private float factor, toFactor;
  private boolean isAnimating;
  private boolean isBlocked;
  private ValueAnimator animator;

  private Runnable startRunnable;

  public FactorAnimator (int id, Target target, Interpolator interpolator, long duration) {
    if (target == null) {
      throw new IllegalArgumentException();
    }
    this.id = id;
    this.target = target;
    this.interpolator = interpolator;
    this.duration = duration;
  }

  public FactorAnimator (int id, Target target, Interpolator interpolator, long duration, float currentFactor) {
    if (target == null) {
      throw new IllegalArgumentException();
    }
    this.id = id;
    this.target = target;
    this.interpolator = interpolator;
    this.duration = duration;
    this.factor = currentFactor;
  }

  public void setLongValue (long longValue) {
    this.longValue = longValue;
  }

  public long getLongValue () {
    return longValue;
  }

  public void setIntValue (int intValue) {
    this.intValue = intValue;
  }

  public void setIntFlag (int flag, boolean enabled) {
    this.intValue = BitwiseUtils.setFlag(intValue, flag, enabled);
  }

  public boolean getIntFlag (int flag) {
    return (intValue & flag) != 0;
  }

  public int getIntValue () {
    return intValue;
  }

  public void setObjValue (Object obj) {
    this.objValue = obj;
  }

  public Object getObjValue () {
    return objValue;
  }

  public void cancel () {
    if (isAnimating) {
      if (Looper.myLooper() != Looper.getMainLooper())
        throw new AssertionError();
      setAnimating(false);
      if (animator != null) {
        animator.cancel();
        animator = null;
      }
    }
  }

  public void setStartRunnable (Runnable runnable) {
    this.startRunnable = runnable;
  }

  public void setDuration (long duration) {
    this.duration = duration;
  }

  public long getDuration () {
    return duration;
  }

  public void setStartDelay (long delay) {
    this.startDelay = delay;
  }

  public void setInterpolator (Interpolator interpolator) {
    this.interpolator = interpolator;
  }

  public void setIsBlocked (boolean isBlocked) {
    this.isBlocked = isBlocked;
  }

  public void animateTo (float toFactor) {
    animateTo(toFactor, null);
  }

  private void invokeStartRunnable () {
    if (startRunnable != null) {
      startRunnable.run();
    }
  }

  private void setAnimating (boolean isAnimating) {
    if (this.isAnimating != isAnimating) {
      this.isAnimating = isAnimating;
    }
  }

  public void animateTo (float toFactor, @Nullable View view) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      throw new AssertionError();
    }

    if (isAnimating) {
      cancel();
    }

    if (factor == toFactor) {
      invokeStartRunnable();
      target.onFactorChangeFinished(id, factor, this);
      return;
    }

    if (isBlocked) {
      this.factor = toFactor;
      target.onFactorChanged(id, factor, 1f, this);
      target.onFactorChangeFinished(id, factor, this);
      invokeStartRunnable();
      return;
    }

    setAnimating(true);

    final float fromFactor = factor;
    final float factorDiff = toFactor - fromFactor;

    if (duration <= 0) {
      setFactor(toFactor, 1f);
      target.onFactorChangeFinished(id, toFactor, this);
      setAnimating(false);
      return;
    }

    this.toFactor = toFactor;

    animator = AnimatorUtils.simpleValueAnimator();
    animator.setDuration(duration);
    animator.setInterpolator(interpolator);
    animator.addUpdateListener(animation -> {
      if (isAnimating) {
        float fraction = AnimatorUtils.getFraction(animation);
        setFactor(fromFactor + factorDiff * fraction, fraction);
      }
    });
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationStart (Animator animation) {
        invokeStartRunnable();
      }

      @Override
      public void onAnimationEnd (Animator animation) {
        if (isAnimating) {
          setFactor(fromFactor + factorDiff, 1f);
          setAnimating(false);
          target.onFactorChangeFinished(id, factor, FactorAnimator.this);
        }
      }
    });
    if (startDelay != 0) {
      animator.setStartDelay(startDelay);
    }

    try {
      if (view != null) {
        AnimatorUtils.startAnimator(view, animator);
      } else {
        animator.start();
      }
    } catch (Throwable t) {
      Log.e("tgx", "Cannot start animation", t);
      forceFactor(toFactor);
    }
  }

  public float getToFactor () {
    return isAnimating ? toFactor : factor;
  }

  @FloatRange(from = 0.0, to = 1.0)
  public float getFactor () {
    return factor;
  }

  public boolean isAnimating () {
    return isAnimating;
  }

  private void setFactor (float factor, float fraction) {
    if (this.factor != factor) {
      this.factor = factor;
      target.onFactorChanged(id, factor, fraction, this);
    }
  }

  public void forceFactor (float factor) {
    forceFactor(factor, false);
  }

  public void forceFactor (float factor, boolean callTarget) {
    cancel();
    if (this.factor != factor && callTarget) {
      this.factor = factor;
      target.onFactorChangeFinished(id, factor, this);
    } else {
      this.factor = factor;
    }
  }
}
