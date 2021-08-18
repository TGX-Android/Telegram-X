/**
 * File created on 08/08/15 at 12:18
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.component.passcode;

import android.animation.ValueAnimator;
import android.graphics.Canvas;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.AnimatorUtils;

public class PatternDot {
  public static final int RADIUS = 6;
  public static final int RADIUS_BIG = 10;

  private float x, y;
  private boolean selected;
  private PasscodeView parentView;

  private float radius, radiusDiff;
  private float factor;

  public PatternDot () {
    this.radius = Screen.dpf(RADIUS);
    this.radiusDiff = Screen.dpf(RADIUS_BIG) - this.radius;
  }

  public PatternDot (PasscodeView parentView) {
    this();
    this.parentView = parentView;
  }

  public void setXY (float x, float y) {
    this.x = x;
    this.y = y;
  }

  public void setSelected (boolean selected) {
    if (this.selected != selected) {
      this.selected = selected;
      if (selected) {
        if (parentView.getCallback().isPasscodeVisible()) {
          this.factor = 0f;
          ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
          // FIXME do with implements
          animator.addUpdateListener(animation -> setFactor(AnimatorUtils.getFraction(animation)));
          animator.setInterpolator(AnimatorUtils.ACCELERATE_DECELERATE_INTERPOLATOR);
          animator.setDuration(180l);
          animator.start();
        }
        UI.hapticVibrate(parentView, false);
      }
    }
  }

  public float getFactor () {
    return factor;
  }

  public void setFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      if (parentView != null) {
        parentView.invalidate();
      }
    }
  }

  public float getX () {
    return x;
  }

  public float getY () {
    return y;
  }

  public boolean isMatching (float x, float y, float offset) {
    return !selected && x >= this.x - offset && x <= this.x + offset && y >= this.y - offset && y <= this.y + offset;
  }

  private float getRadius () {
    return radius + radiusDiff * (factor > .5f ? (1f - factor) / .5f : factor / .5f);
  }

  public void draw (Canvas c) {
    c.drawCircle(x, y, getRadius(), Paints.fillingPaint(Theme.getColor(R.id.theme_color_passcodeIcon)));
  }
}
