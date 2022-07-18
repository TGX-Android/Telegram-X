package org.thunderdog.challegram.reactions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Property;
import android.view.View;
import android.view.animation.LinearInterpolator;

import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.SimplestCheckBox;

/**
 * Circular check box that doesn't require separate view to draw, wrapped into a separate view
 */
public class SimplestCheckboxView extends View {
  private SimplestCheckBox cb = SimplestCheckBox.newInstance(0f, null);
  private float factor = 0;
  private boolean isChecked;
  private Animator currentAnim;
  private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private String counter;

  private static Property<SimplestCheckboxView, Float> FACTOR = new Property<>(Float.class, "Fdfsafdsa") {
    @Override
    public Float get (SimplestCheckboxView object) {
      return object.factor;
    }

    @Override
    public void set (SimplestCheckboxView object, Float value) {
      object.factor = value;
      object.invalidate();
    }
  };

  public SimplestCheckboxView (Context context) {
    this(context, null);
  }

  public SimplestCheckboxView (Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public SimplestCheckboxView (Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(widthMeasureSpec) : SimplestCheckBox.size(),
      MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(heightMeasureSpec) : SimplestCheckBox.size());
  }

  @Override
  protected void onDraw (Canvas canvas) {
    int cbSize = Math.min(getWidth() - Screen.dp(2), Math.min(getHeight() - Screen.dp(2), SimplestCheckBox.size() - Screen.dp(4)));
    if (factor > 0f) {
      paint.setColor(Theme.fillingColor());
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(Screen.dp(1));
      canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, cbSize / 2f - paint.getStrokeWidth() / 2f, paint);
    }
    float scale = cbSize / (float) SimplestCheckBox.size();
    if (scale != 1f) {
      canvas.save();
      canvas.scale(scale, scale, getWidth() / 2f, getHeight() / 2f);
    }
    SimplestCheckBox.draw(canvas, getWidth() / 2, getHeight() / 2, factor, counter, cb, Theme.togglerActiveColor(), Theme.fillingColor(), false, 0f);
    if (scale != 1f)
      canvas.restore();
  }

  public void setCounter (String counter) {
    this.counter = counter;
    invalidate();
  }

  public void setChecked (boolean checked, boolean animated) {
    if (isChecked != checked) {
      isChecked = checked;
      if (currentAnim != null)
        currentAnim.cancel();

      if (animated) {
        currentAnim = ObjectAnimator.ofFloat(this, FACTOR, checked ? 1f : 0f);
        currentAnim.setDuration(165);
        currentAnim.setInterpolator(new LinearInterpolator());
        currentAnim.addListener(new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd (Animator animation) {
            currentAnim = null;
          }
        });
        currentAnim.start();
      } else {
        factor = checked ? 1f : 0f;
        invalidate();
      }
    }
  }
}
