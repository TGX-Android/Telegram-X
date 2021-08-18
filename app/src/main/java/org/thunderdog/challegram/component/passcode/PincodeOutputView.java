/**
 * File created on 08/08/15 at 21:33
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.component.passcode;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Passcode;

import me.vkryl.android.AnimatorUtils;

public class PincodeOutputView {
  private float centerX, centerY;

  private float startX;
  private float origX, diffX, xFactor;

  private float space;

  private final PincodeCircle[] circles;
  private int size;
  private int drawingSize;

  private View parentView;

  public PincodeOutputView () {
    circles = new PincodeCircle[Passcode.PINCODE_SIZE];
    space = Screen.dpf(21f);
  }

  public void setParentView (View view) {
    this.parentView = view;
  }

  public void append () {
    if (size < Passcode.PINCODE_SIZE && drawingSize < Passcode.PINCODE_SIZE) {
      if (circles[drawingSize] == null) {
        circles[drawingSize] = new PincodeCircle(this);
      }
      drawingSize++;
      size++;
      circles[drawingSize - 1].show();
      animate();
    }
  }

  public float getFactor () {
    return xFactor;
  }

  public void setFactor (float factor) {
    if (this.isAnimating && this.xFactor != factor) {
      this.xFactor = factor;
      this.startX = origX + diffX * factor;
      invalidate();
    }
  }

  public void remove () {
    if (size > 0) {
      circles[size - 1].hide();
      size--;
      animate();
    }
  }

  private ValueAnimator xAnimator;
  private boolean isAnimating;

  private void animate () {
    if (isAnimating) {
      isAnimating = false;
      float currentX = startX;
      xAnimator.cancel();
      startX = currentX;
    }

    origX = startX;
    diffX = getStartX() - startX;
    isAnimating = diffX != 0;

    if (isAnimating) {
      this.xFactor = 0f;
      xAnimator = AnimatorUtils.simpleValueAnimator();
      // this, "factor"
      xAnimator.addUpdateListener(animation -> setFactor(AnimatorUtils.getFraction(animation)));
      xAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
      xAnimator.setDuration(PincodeCircle.ANIMATION_DURATION);
      xAnimator.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd (Animator animation) {
          isAnimating = false;
        }
      });
      xAnimator.start();
    }
  }

  private boolean isCirclesAnimating;
  private float circlesFactor;

  public boolean isAnimating () {
    return isCirclesAnimating;
  }

  public float getCirclesFactor () {
    return circlesFactor;
  }

  public void setCirclesFactor (float factor) {
    if (this.circlesFactor != factor) {
      for (int i = 0; i < drawingSize; i++) {
        circles[i].radiusFactor = factor;
      }
      invalidate();
    }
  }

  public void clear () {
    size = 0;
    drawingSize = 0;
  }

  public boolean removeAll () {
    if (size == 0) return false;
    isCirclesAnimating = true;
    circlesFactor = 1f;
    for (int i = 0; i < size; i++) {
      circles[i].setAnimating(true);
    }
    ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
    // FIXME do with implements
    animator.addUpdateListener(animation -> setCirclesFactor(1f - AnimatorUtils.getFraction(animation)));
    animator.setDuration(PincodeCircle.ANIMATION_DURATION);
    animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        for (int i = 0; i < drawingSize; i++) {
          circles[i].setAnimating(false);
        }
        drawingSize = 0;
        isCirclesAnimating = false;
        layoutDots();
      }
    });
    animator.setStartDelay(20l);
    animator.start();
    size = 0;
    return true;
  }

  public void shiftLast () {
    if (drawingSize > 0) {
      drawingSize--;
    }
  }

  public void setRect (float left, float top, float right, float bottom) {
    this.centerX = left + (right - left) * .5f;
    this.centerY = top + (bottom - top) * .5f;
    layoutDots();
    invalidate();
  }

  private void layoutDots () {
    this.startX = getStartX();
  }

  private float getStartX () {
    return size < 2 ? centerX : centerX - (size - 1) * space * .5f;
  }

  public void invalidate () {
    if (this.parentView != null) {
      this.parentView.invalidate();
    }
  }

  public void draw (Canvas c) {
    float x = startX;
    for (int i = 0; i < drawingSize; i++) {
      circles[i].draw(c, x, centerY);
      x += space;
    }
  }

  private static class PincodeCircle {
    public static final long ANIMATION_DURATION = 180l;

    // private static Paint circlePaint;
    // private static float radius;

    public float radiusFactor;
    public boolean isAnimating;

    private PincodeOutputView parent;

    public PincodeCircle (PincodeOutputView parent) {
      this.parent = parent;
    }

    private ValueAnimator radiusAnimator;

    private void cancelAnimation () {
      if (isAnimating) {
        isAnimating = false;
        float currentValue = radiusFactor;
        if (radiusAnimator != null) {
          radiusAnimator.cancel();
        }
        radiusFactor = currentValue;
      }
    }

    public void show () {
      cancelAnimation();
      this.isAnimating = true;
      this.radiusFactor = 0f;

      radiusAnimator = AnimatorUtils.simpleValueAnimator();
      // FIXME do with implements
      radiusAnimator.addUpdateListener(animation -> setFactor(AnimatorUtils.getFraction(animation)));
      radiusAnimator.setDuration(ANIMATION_DURATION);
      radiusAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
      radiusAnimator.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd (Animator animation) {
          isAnimating = false;
        }
      });
      radiusAnimator.start();
    }

    public void hide () {
      cancelAnimation();
      this.isAnimating = true;
      this.radiusFactor = 1f;

      radiusAnimator = AnimatorUtils.simpleValueAnimator();
      // FIXME do with implements
      radiusAnimator.addUpdateListener(animation -> setFactor(1f - AnimatorUtils.getFraction(animation)));
      radiusAnimator.setDuration(ANIMATION_DURATION);
      radiusAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
      radiusAnimator.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd (Animator animation) {
          parent.shiftLast();
          isAnimating = false;
        }
      });
      radiusAnimator.start();
    }

    public void setAnimating (boolean animating) {
      if (animating) {
        if (isAnimating) {
          isAnimating = false;
          float currentValue = radiusFactor;
          radiusAnimator.cancel();
          radiusFactor = currentValue;
        }
        radiusAnimator = null;
        isAnimating = true;
      } else if (radiusAnimator == null) {
        isAnimating = false;
      }
    }

    public float getFactor () {
      return radiusFactor;
    }

    public void setFactor (float radiusFactor) {
      if (this.isAnimating && this.radiusFactor != radiusFactor) {
        this.radiusFactor = radiusFactor;
        parent.invalidate();
      }
    }

    public void draw (Canvas c, float x, float y) {
      // TODO: 09/08/15 Display digit
      int radius = Screen.dp(4.5f);
      Paint circlePaint = Paints.fillingPaint(Theme.getColor(R.id.theme_color_passcodeIcon));
      if (isAnimating) {
        c.drawCircle(x, y, radius * radiusFactor, circlePaint);
      } else {
        c.drawCircle(x, y, radius, circlePaint);
      }
    }
  }
}
