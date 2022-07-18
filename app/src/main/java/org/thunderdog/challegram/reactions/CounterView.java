package org.thunderdog.challegram.reactions;

import android.content.Context;
import android.graphics.Canvas;
import android.view.Gravity;

import androidx.annotation.IdRes;

import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.widget.SparseDrawableView;

public class CounterView extends SparseDrawableView {
  public final Counter counter;
  private int iconColor;

  public CounterView (Context context, Counter.Builder counter, @IdRes int iconColor) {
    super(context);
    this.counter = counter.callback(this).build();
    this.iconColor = iconColor;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(Math.round(counter.getRealWidthWithoutAnimationBullshit()), MeasureSpec.getSize(heightMeasureSpec));
  }

  @Override
  protected void onDraw (Canvas canvas) {
    counter.draw(canvas, getWidth() / 2, getHeight() / 2, Gravity.CENTER_HORIZONTAL, 1f, this, iconColor);
  }

  public void setCount (int count, boolean animated) {
    counter.setCount(count, animated);
    requestLayout();
  }
}
