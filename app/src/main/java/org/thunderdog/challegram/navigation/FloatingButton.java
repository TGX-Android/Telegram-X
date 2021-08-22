/**
 * File created on 17/08/15 at 02:49
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.navigation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.SimpleShapeDrawable;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.Destroyable;

public class FloatingButton extends View implements Destroyable, Screen.StatusBarHeightChangeListener {
  private float center;
  private float heightDiff;

  private boolean isShowing;

  private static final float MIN_SCALE = .4f;

  public FloatingButton (Context context) {
    super(context);

    heightDiff = -Size.getMaximumHeaderSizeDifference();
    RippleSupport.setCircleBackground(this, 56f, 4f, R.id.theme_color_headerButton, null);

    int padding = Screen.dp(4f);
    int size = Screen.dp(56f);
    center = (float) size * .5f + padding;

    FrameLayoutFix.LayoutParams params;

    params = FrameLayoutFix.newParams(size + padding * 2, size + padding * 2, Gravity.TOP | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT));
    params.topMargin = HeaderView.getBigSize(true) - Screen.dp(30f) - padding;
    params.rightMargin = params.leftMargin = Screen.dp(16f) - padding;

    setLayoutParams(params);
    Screen.addStatusBarHeightListener(this);

    isShowing = true;

    setTranslationY(heightDiff);
    setAlpha(0f);
    setScaleX(MIN_SCALE);
    setScaleY(MIN_SCALE);
  }

  @Override
  public void onStatusBarHeightChanged (int newHeight) {
    Views.setTopMargin(this, HeaderView.getBigSize(true) - Screen.dp(30f) - Screen.dp(4f));
  }

  @Override
  public void performDestroy () {
    Screen.removeStatusBarHeightListener(this);
  }

  private Drawable leftIcon, rightIcon;
  private int leftIconRes, rightIconRes;
  private float leftIconTop, rightIconTop;
  private Drawable icon;
  private int iconRes;
  private float iconTop;

  public void setIcons (int leftIcon, int rightIcon) {
    if (leftIcon != 0 && rightIcon != 0 && leftIcon != rightIcon) {
      this.icon = null;
      this.iconTop = 0;
      this.iconRes = 0;

      this.leftIcon = Drawables.get(getResources(), leftIcon);
      this.leftIconRes = leftIcon;
      this.leftIconTop = getIconTop(leftIcon, this.leftIcon);

      this.rightIcon = Drawables.get(getResources(), rightIcon);
      this.rightIconRes = rightIcon;
      this.rightIconTop = getIconTop(rightIcon, this.rightIcon);

      setFactor(0f);

      return;
    }

    this.leftIcon = null;
    this.leftIconRes = 0;
    this.rightIcon = null;
    this.rightIconRes = 0;

    if (leftIcon != 0) {
      this.icon = Drawables.get(getResources(), leftIcon);
      this.iconRes = leftIcon;
      this.iconTop = getIconTop(leftIcon, icon);
    } else {
      this.icon = Drawables.get(getResources(), rightIcon);
      this.iconRes = rightIcon;
      this.iconTop = getIconTop(rightIcon, icon);
    }

    invalidate();
  }

  private float factor;

  public void setFactor (float factor) { // Switch factor. 0f - showing left icon, 1f - right
    if (this.factor != factor && leftIcon != null && rightIcon != null) {
      this.factor = factor;
      invalidate();
    }
  }

  private float heightFactor = -1f, scaleFactor = -1f;
  private float fromHeightFactor;
  private float toHeightFactor;
  private boolean translateFully;

  private Animator.AnimatorListener hideListener;

  private boolean touchEnabled;

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return touchEnabled && super.onTouchEvent(event);
  }

  public void setTouchEnabled (boolean enabled) {
    if (touchEnabled != enabled) {
      touchEnabled = enabled;
      setEnabled(enabled);
      if (Views.HARDWARE_LAYER_ENABLED) {
        Views.setLayerType(this, enabled ? (SimpleShapeDrawable.USE_SOFTWARE_SHADOW ? LAYER_TYPE_SOFTWARE : LAYER_TYPE_HARDWARE) : LAYER_TYPE_NONE);
      }
    }
  }

  private static final long OVERSHOOT_DURATION = 380l;

  public void initHeightFactor (float heightFactor, float heightFactor2, float scaleFactor, boolean animate, boolean translation) {
    fromHeightFactor = heightFactor;
    toHeightFactor = heightFactor2;
    translateFully = translation;
    if (toHeightFactor == fromHeightFactor) {
      setTranslationY((1f - heightFactor) * heightDiff);
    } else {
      setHeightFactor(heightFactor, scaleFactor, animate);
    }
  }

  @SuppressWarnings ("SuspiciousNameCombination")
  public void setHeightFactor (float heightFactor, float scaleFactor, boolean animate) {
    if (this.heightFactor != heightFactor || this.scaleFactor != scaleFactor) {
      this.heightFactor = heightFactor;
      this.scaleFactor = scaleFactor;
      if (translateFully) {
        setTranslationY((1f - heightFactor) * heightDiff - Size.getHeaderPortraitSize() * fromHeightFactor * (1f - heightFactor));
      } else if (animate || fromHeightFactor != toHeightFactor) {
        setTranslationY((1f - heightFactor) * heightDiff);
      }
      if (animate) {
        if (heightFactor == 0f) {
          if (isShowing) {
            isShowing = false;
            Views.animate(this, 0f, 0f, 0f, OVERSHOOT_DURATION, AnimatorUtils.ANTICIPATE_OVERSHOOT_INTERPOLATOR, getHideListener());
          }
        } else {
          if (!isShowing) {
            isShowing = true;
            setTouchEnabled(true);
            Views.animate(this, 1f, 1f, 1f, OVERSHOOT_DURATION, AnimatorUtils.ANTICIPATE_OVERSHOOT_INTERPOLATOR, null);
          }
        }
      } else {
        /*float origH = heightFactor;

        if (fromHeightFactor != toHeightFactor) {
          if (fromHeightFactor != 0f) {
            heightFactor = Math.min(heightFactor / fromHeightFactor, 1f);
          } else if (toHeightFactor != 0f) {
            heightFactor = heightFactor / toHeightFactor;
          }
        }

        if (origH == heightFactor) {
          Logger.v("%f", heightFactor);
        } else {
          Logger.w("%f %f", origH, heightFactor);
        }*/

        float realAlpha = MathUtils.clamp(scaleFactor);
        setAlpha(realAlpha);
        float scale = MIN_SCALE + (1f - MIN_SCALE) * scaleFactor;
        setScaleX(scale);
        setScaleY(scale);
        setTouchEnabled(isShowing = realAlpha != 0f);
      }
    }
  }

  private float getIconTop (int resource, Drawable icon) {
    if (resource == 0 || icon == null) {
      return 0;
    }
    switch (resource) {
      case R.drawable.baseline_chat_bubble_24: {
        return Screen.dp(17f) + Screen.dp(4f);
      }
      /*case R.drawable.ic_camera_gray: {
        return Screen.dp(19f) + Screen.dp(4f);
      }*/
      default: {
        return center - icon.getMinimumHeight() / 2;
      }
    }
  }

  private static void draw (Canvas c, int res, Drawable d, float cx, float top, Paint paint, float alpha) {
    if (d != null) {
      boolean needReverse = Drawables.needMirror(res);
      if (needReverse) {
        c.save();
        c.scale(-1f, 1f, cx, top + d.getMinimumHeight() / 2);
      }
      int savedAlpha = paint.getAlpha();
      paint.setAlpha((int) (255f * alpha));
      Drawables.draw(c, d, cx - d.getMinimumWidth() / 2, top, paint);
      paint.setAlpha(savedAlpha);
      if (needReverse) {
        c.restore();
      }
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (icon != null) {
      draw(c, iconRes, icon, center, iconTop, Paints.getHeaderFloatIconPorterDuffPaint(), 1f);
    } else if (leftIcon != null || rightIcon != null) {
      Paint paint = Paints.getHeaderFloatIconPorterDuffPaint();
      if (leftIcon != null)
        draw(c, leftIconRes, leftIcon, center, leftIconTop, paint, 1f - MathUtils.clamp(factor));
      if (rightIcon != null)
        draw(c, rightIconRes, rightIcon, center, rightIconTop, paint, MathUtils.clamp(factor));
    }
  }

  private Animator.AnimatorListener getHideListener () {
    if (hideListener == null) {
      hideListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd (Animator animation) {
          if (!isShowing) {
            setTouchEnabled(false);
          }
        }
      };
    }
    return hideListener;
  }

  // show/hide for EditHeaderView

  private float showFactor;

  public float getShowFactor () {
    return showFactor;
  }

  public void setShowFactor (float factor) {
    if (this.showFactor != factor) {
      this.showFactor = factor;
      setAlpha(MathUtils.clamp(factor));
      final float scale = MIN_SCALE + (1f - MIN_SCALE) * factor;
      setScaleX(scale);
      setScaleY(scale);
    }
  }

  public void updatePosition (int height) {
    float heightFactor = (float) (height - Size.getHeaderPortraitSize()) / (float) Size.getMaximumHeaderSizeDifference();
    setTranslationY((1f - heightFactor) * heightDiff);
  }

  public void show (ViewController<?> c) {
    int icon = c.getFloatingButtonId();
    int height = c.getHeaderHeight();
    float heightFactor = (float) (height - Size.getHeaderPortraitSize()) / (float) Size.getMaximumHeaderSizeDifference();

    isShowing = true;

    setTouchEnabled(true);
    setIcons(0, icon);
    setTranslationY((1f - heightFactor) * heightDiff);

    showFactor = 0f;

    ValueAnimator obj;
    obj = AnimatorUtils.simpleValueAnimator();
    obj.addUpdateListener(animation -> setShowFactor(AnimatorUtils.getFraction(animation)));
    obj.setInterpolator(AnimatorUtils.ANTICIPATE_OVERSHOOT_INTERPOLATOR);
    obj.setDuration(OVERSHOOT_DURATION);

    obj.start();
  }

  public void hide () {
    if (isShowing) {
      isShowing = false;

      showFactor = 1f;

      ValueAnimator obj;

      obj = AnimatorUtils.simpleValueAnimator();
      obj.addUpdateListener(animation -> setShowFactor(1f - AnimatorUtils.getFraction(animation)));
      obj.setInterpolator(AnimatorUtils.ANTICIPATE_OVERSHOOT_INTERPOLATOR);
      obj.setDuration(OVERSHOOT_DURATION);
      obj.addListener(getHideListener());
      obj.start();
    }
  }
}
