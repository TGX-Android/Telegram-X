/**
 * File created on 25/04/15 at 09:15
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.support;

import android.view.animation.Animation;
import android.view.animation.Transformation;

public class ViewTranslator extends Animation
{
  private int mFromXType = ABSOLUTE;
  private int mToXType = ABSOLUTE;

  private int mFromYType = ABSOLUTE;
  private int mToYType = ABSOLUTE;

  private float mFromXValue = 0.0f;
  private float mToXValue = 0.0f;

  private float mFromYValue = 0.0f;
  private float mToYValue = 0.0f;

  private float mFromXDelta;
  private float mToXDelta;
  private float mFromYDelta;
  private float mToYDelta;

  /**
   * Constructor to use when building a TranslateAnimation from code
   *
   * @param fromXDelta Change in X coordinate to apply at the start of the
   *        animation
   * @param toXDelta Change in X coordinate to apply at the end of the
   *        animation
   * @param fromYDelta Change in Y coordinate to apply at the start of the
   *        animation
   * @param toYDelta Change in Y coordinate to apply at the end of the
   *        animation
   */
  public ViewTranslator (float fromXDelta, float toXDelta, float fromYDelta, float toYDelta) {
    mFromXValue = fromXDelta;
    mToXValue = toXDelta;
    mFromYValue = fromYDelta;
    mToYValue = toYDelta;

    mFromXType = ABSOLUTE;
    mToXType = ABSOLUTE;
    mFromYType = ABSOLUTE;
    mToYType = ABSOLUTE;

    setFillAfter(true);
    setDuration(0);
  }

  public void updateValues(float fromXDelta, float toXDelta, float fromYDelta, float toYDelta) {
    mFromXValue = fromXDelta;
    mToXValue = toXDelta;
    mFromYValue = fromYDelta;
    mToYValue = toYDelta;

    mFromXType = ABSOLUTE;
    mToXType = ABSOLUTE;
    mFromYType = ABSOLUTE;
    mToYType = ABSOLUTE;
  }

  @Override
  protected void applyTransformation(float interpolatedTime, Transformation t) {
    float dx = mFromXDelta;
    float dy = mFromYDelta;
    if (mFromXDelta != mToXDelta) {
      dx = mFromXDelta + ((mToXDelta - mFromXDelta) * interpolatedTime);
    }
    if (mFromYDelta != mToYDelta) {
      dy = mFromYDelta + ((mToYDelta - mFromYDelta) * interpolatedTime);
    }
    t.getMatrix().setTranslate(dx, dy);
  }

  @Override
  public void initialize(int width, int height, int parentWidth, int parentHeight) {
    super.initialize(width, height, parentWidth, parentHeight);
    mFromXDelta = resolveSize(mFromXType, mFromXValue, width, parentWidth);
    mToXDelta = resolveSize(mToXType, mToXValue, width, parentWidth);
    mFromYDelta = resolveSize(mFromYType, mFromYValue, height, parentHeight);
    mToYDelta = resolveSize(mToYType, mToYValue, height, parentHeight);
  }
}
